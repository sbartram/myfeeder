---
status: diagnosed
trigger: "UAT G-04-1: With MYFEEDER_TYPESAFE_API_KEY set and ./gradlew bootTestRun, Jev reports 'not configured' and the backlog sweep never drains; works only after SPRING_AI_TYPESAFE_API_KEY / SPRING_AI_TYPESAFE_BASE_URL / MYFEEDER_INTEREST_SWEEP_INITIAL_DELAY overrides"
created: 2026-09-24T21:10:25Z
updated: 2026-09-24T21:40:00Z
goal: find_root_cause_only
symptoms_prefilled: true
---

## Current Focus

hypothesis: CONFIRMED. Under bootTestRun, src/test/resources/application.yaml shadows src/main/resources/application.yaml (same classpath resource name, test resources first on the classpath). Spring Boot loads only the first `classpath:/application.yaml`, so every main-only setting is lost. For Jev this means three separate failures: the key placeholder is gone, the base-url points at 127.0.0.1:9, and the sweep's first run is an hour after startup.
bug_class: Bohrbug (deterministic config resolution; reproduces 100% with a classpath probe)
known_pattern_candidate: none (no knowledge-base.md exists)
test: done. A Spring environment probe on the real bootTestRun classpath, with and without build/resources/test, and with a trial dev-profile overlay.
expecting: n/a
next_action: return ROOT CAUSE FOUND to the orchestrator (find_root_cause_only; no fix applied)

reasoning_checkpoint:
  hypothesis: "bootTestRun runs with the test classpath (build/classes/java/test, build/resources/test, then main), so ClassLoader.getResource('application.yaml') returns the test yaml and Spring Boot's optional:classpath:/ location loads only that file. The test yaml has no spring.ai.typesafe.api-key (so MYFEEDER_TYPESAFE_API_KEY never binds and TypeSafeConfig logs 'not configured'), sets base-url http://127.0.0.1:9 and sweep-initial-delay PT1H. The documented live-key procedure therefore cannot work."
  confirming_evidence:
    - "Gradle bootTestRun.classpath order printed: build/classes/java/test, build/resources/test, build/classes/java/main, build/resources/main (+220 jars)"
    - "Probe on that exact classpath with MYFEEDER_TYPESAFE_API_KEY=probe-value: only one config source 'class path resource [application.yaml]' = build/resources/test/application.yaml; api-key=null, base-url=http://127.0.0.1:9, sweep-initial-delay=PT1H, spring.http.clients.*=null, spring.application.name=null"
    - "Same probe with build/resources/test removed: api-key bound (len 11), base-url unset (starter default https://api.typesafe.ai), PT1M, 5s/30s, name=myfeeder"
    - "The user's env overrides (SPRING_AI_TYPESAFE_API_KEY, SPRING_AI_TYPESAFE_BASE_URL, MYFEEDER_INTEREST_SWEEP_INITIAL_DELAY) are exactly the three lost settings; with them the live run drained 30 to 0 with SCORED rows"
  falsification_test: "If the probe on the bootTestRun classpath had shown a 'build/resources/main/application.yaml' property source, or api-key bound from MYFEEDER_TYPESAFE_API_KEY, the shadowing hypothesis would be wrong. It showed neither."
  fix_rationale: "n/a (diagnose only). A fix must make the dev-run entry point load live settings while tests keep the offline defaults."
  blind_spots: "Did not run a full bootTestRun (needs Docker plus the billed key). The probe runs Spring's real ConfigData pipeline on the real classpath but stops at environment preparation, so it does not exercise bean creation. The user's live run already covers that end to end. Phase 3 UAT test 2 (live preview with 'MYFEEDER_TYPESAFE_API_KEY exported before bootTestRun') is recorded as a pass, but under the same shadowing it cannot have bound the key. Either it was run with some other setup, or it was a false pass. Not investigated further."
  candidate_causes:
    - "config: test application.yaml shadows main under bootTestRun (CONFIRMED)"
    - "code: TypeSafeConfig / JevApiClient.isConfigured() mis-reading a present key (ELIMINATED: TypeSafeProperties.getApiKey() is null because no property source defines it; with the key in the env under its Spring name, the same code works)"
    - "environment: key not exported to the Gradle-forked JVM (ELIMINATED: SPRING_AI_TYPESAFE_API_KEY exported the same way did reach it; MYFEEDER_RAINDROP_API_TOKEN also reaches the probe)"
    - "process/docs: the 04-05 human-check (and 03-07 step 5) prescribe bootTestRun for a live-key check, although research had already recorded that the test yaml shadows main"
  and_gate: "yes. Scoring under bootTestRun needs all three settings: (1) the key bound, (2) a real base-url, (3) a sweep delay short enough to drain within about 3 minutes. The test yaml defeats each one separately. Overriding only the key would give connection-refused transients against 127.0.0.1:9. Overriding key and base-url would still leave the backlog waiting an hour (only new arrivals would be scored). One shared cause, the shadowing, produces all three, and the procedure document that chose bootTestRun is a co-contributor."

## Symptoms

expected: With MYFEEDER_TYPESAFE_API_KEY set and ./gradlew bootTestRun, Jev is configured; backlog drains to 0 with SCORED rows (model + request id) within ~3 min; new arrivals scored without waiting for the sweep; Re-score resets and re-drains; no new feed errors.
actual: Log shows "TypeSafeConfig : TypeSafe Jev not configured; interest scoring disabled"; UI shows "30 articles waiting to be scored" and nothing happens. After restarting with SPRING_AI_TYPESAFE_API_KEY, SPRING_AI_TYPESAFE_BASE_URL=https://api.typesafe.ai and MYFEEDER_INTEREST_SWEEP_INITIAL_DELAY=PT1M, the unscored count went 30 -> 0 with no errors (and no logs at all).
errors: none besides the "not configured" info line
reproduction: UAT test 1 of phase 04 (documented live-key E2E procedure): export MYFEEDER_TYPESAFE_API_KEY, ./gradlew bootTestRun
started: discovered during UAT (phase 04)

## Eliminated

- hypothesis: TypeSafeConfig/JevApiClient mis-detect a present key (code bug in isConfigured / hasText)
  evidence: The probe shows spring.ai.typesafe.api-key=null in the environment under bootTestRun, so TypeSafeProperties.getApiKey() is really empty. With SPRING_AI_TYPESAFE_API_KEY the same code reports configured and scores (user live run).
  timestamp: 2026-09-24T21:25:00Z

- hypothesis: The env var does not reach the forked bootTestRun JVM
  evidence: The user's SPRING_AI_* overrides did take effect in the same launch path. Relaxed binding maps MYFEEDER_TYPESAFE_API_KEY to myfeeder.typesafe.api-key, a property nothing binds. The variable is present, but only a spring.ai.typesafe.api-key placeholder, which exists only in main yaml, would map it.
  timestamp: 2026-09-24T21:25:00Z

- hypothesis: "No logs at all" during a successful drain is a second defect
  evidence: ArticleScoringService/ScoringQueue/InterestScoringListener log only on failure (debug transient, info failed, warn queue-full/store failure). The success path is silent by design. This is not part of G-04-1; at most an observability nice-to-have.
  timestamp: 2026-09-24T21:30:00Z

## Evidence

- timestamp: 2026-09-24T21:12:00Z
  checked: .planning/debug/knowledge-base.md
  found: does not exist
  implication: no known-pattern candidate

- timestamp: 2026-09-24T21:14:00Z
  checked: src/main/resources/application.yaml vs src/test/resources/application.yaml
  found: main-only keys are spring.application.name, spring.http.clients.connect-timeout 5s and read-timeout 30s, spring.ai.typesafe.api-key ${MYFEEDER_TYPESAFE_API_KEY:}, and myfeeder.raindrop.api-token ${MYFEEDER_RAINDROP_API_TOKEN:}. The test yaml differs on spring.ai.typesafe.base-url (http://127.0.0.1:9, where main uses the starter default) and myfeeder.interest.sweep-initial-delay (PT1H vs PT1M). Test-only keys are spring.ai.anthropic.api-key, datasource.hikari timeouts and flyway connect-retries. The resilience4j blocks are identical once comments are stripped.
  implication: bootTestRun would lose the key placeholder, real base-url, PT1M delay, HTTP timeouts and app name

- timestamp: 2026-09-24T21:16:00Z
  checked: Gradle init script printing tasks.bootTestRun.classpath (non-jar entries in order)
  found: build/classes/java/test, build/resources/test, build/classes/java/main, build/resources/main, then 220 jars
  implication: test resources precede main resources for bootTestRun

- timestamp: 2026-09-24T21:18:00Z
  checked: TypeSafeProperties in spring-ai-starter-typesafe-0.1.0.jar (javap + configuration metadata)
  found: baseUrl defaults to "https://api.typesafe.ai", model to "jev-latest"; api-key has no default
  implication: main yaml relies on the starter default base-url, and the test yaml's 127.0.0.1:9 replaces it outright

- timestamp: 2026-09-24T21:22:00Z
  checked: Spring Boot environment probe (SpringApplication, WebApplicationType.NONE, ApplicationEnvironmentPreparedEvent dump) on the exact bootTestRun classpath, MYFEEDER_TYPESAFE_API_KEY=probe-value
  found: getResources('application.yaml') returns both test and main copies, but the only config property source is "class path resource [application.yaml]" = build/resources/test/application.yaml. Values are api-key=null, base-url=http://127.0.0.1:9, sweep-initial-delay=PT1H, http.clients.*=null, application.name=null, anthropic.api-key=test-dummy-key.
  implication: CONFIRMS the shadowing. Spring Boot's classpath: location loads the first match only, never both.

- timestamp: 2026-09-24T21:22:30Z
  checked: Same probe with build/resources/test removed from the classpath (bootRun-equivalent config)
  found: api-key bound (len 11), base-url unset (so starter default https://api.typesafe.ai), PT1M, 5s/30s, application.name=myfeeder
  implication: The main yaml alone would satisfy the UAT procedure. The only difference is the shadowing test yaml.

- timestamp: 2026-09-24T21:23:00Z
  checked: myfeeder.raindrop.api-token under bootTestRun
  found: it still resolves from MYFEEDER_RAINDROP_API_TOKEN even though the test yaml has no placeholder. Relaxed binding maps the env var name directly to myfeeder.raindrop.api-token.
  implication: Raindrop is NOT affected, and only by the coincidence that its env var name matches its property path. MYFEEDER_TYPESAFE_API_KEY maps to myfeeder.typesafe.api-key, which nothing reads, so Jev is affected.

- timestamp: 2026-09-24T21:26:00Z
  checked: git log of both yamls
  found: The test yaml has shadowed main since the start (987ce4a, 2026-03). The typesafe base-url 127.0.0.1:9 and the omitted key date from 02852db (02-01, 2026-09-22). sweep-initial-delay PT1H is from a78fa81 (04-05). spring.http.clients timeouts were added to main only (fce474b, 01-01).
  implication: Live Jev has never worked under bootTestRun since Phase 2. The HTTP timeouts have never applied under bootTestRun either (feed fetches in local dev run with transport defaults).

- timestamp: 2026-09-24T21:28:00Z
  checked: planning docs (research PITFALLS/SUMMARY, 02-CONTEXT, 04-RESEARCH/CONTEXT, 04-05-PLAN, 04-VERIFICATION, 03-07-PLAN, 03-VERIFICATION)
  found: The shadowing was known and deliberately handled for tests. The mirror rule is "test YAML must not reference any real key env var. Pin baseUrl in tests" (02-CONTEXT), and PT1H exists for research Pitfall 12. Yet 04-05's human-check and 03-07 step 5 / 03-VERIFICATION prescribe "export MYFEEDER_TYPESAFE_API_KEY, ./gradlew bootTestRun".
  implication: The procedure contradicts the test-yaml design. bootTestRun inherits the test-safety config by construction.

- timestamp: 2026-09-24T21:29:00Z
  checked: guard tests that constrain any fix
  found: TypeSafeConfigTest.testYamlMirrorsMainTypeSafePinsWithoutKey asserts the test yaml has no spring.ai.typesafe.api-key, a base-url starting http://127.0.0.1, and no MYFEEDER_TYPESAFE_API_KEY reference. JevResilienceTest.testYamlMirrorsMainJevInstances requires identical jev blocks. HttpClientConfigurationTest, TypeSafeConfigTest.mainYamlPins..., JevLiveSmokeTest and InterestCalibrationSpikeTest load main yaml from disk because of the shadowing.
  implication: A fix that renames or restructures the test yaml must rework these guards. A fix that adds a dev-only overlay leaves them intact.

- timestamp: 2026-09-24T21:31:00Z
  checked: .envrc working-tree diff (values redacted)
  found: an uncommitted line exports MYFEEDER_TYPESAFE_API_KEY. direnv shells now carry the real key into every ./gradlew test.
  implication: Tests are safe today only because the test yaml never maps that variable. Any fix that lets tests load main application.yaml (for example renaming the test yaml to application-test.yaml) would bind the real key in tests unless the overlay explicitly blanks it. Exporting SPRING_AI_TYPESAFE_API_KEY/BASE_URL in .envrc (the "document env overrides" route) would override the test yaml for the whole suite, because systemEnvironment outranks application.yaml.

- timestamp: 2026-09-24T21:34:00Z
  checked: trial overlay (scratchpad only, repo untouched). An application-dev.yaml with api-key ${MYFEEDER_TYPESAFE_API_KEY:}, base-url https://api.typesafe.ai, sweep-initial-delay PT1M, http.clients 5s/30s and application.name, on the bootTestRun classpath.
  found: With spring.profiles.active=dev every live value resolves (key bound, real base-url, PT1M, 5s/30s, name). With no profile (what @SpringBootTest sees) the values are unchanged from today: key null, 127.0.0.1:9, PT1H.
  implication: A profile overlay activated only by the dev entry point fixes bootTestRun and leaves test isolation byte-for-byte unchanged. SpringApplication.Augmented.withAdditionalProfiles(String...) exists in Boot 4.0.8 (javap), so TestMyfeederApplication can activate it and the fix also works when launched from an IDE.

- timestamp: 2026-09-24T21:38:00Z
  checked: Gradle bootRun.classpath (same init-script technique)
  found: only build/classes/java/main and build/resources/main (+172 jars); no test resources
  implication: ./gradlew bootRun (Docker Compose services) already loads the real main yaml. The live-key check works there today with no code change, and bootTestRun is the only launcher affected.

## Resolution

root_cause: "./gradlew bootTestRun launches TestMyfeederApplication on the test runtime classpath, where build/resources/test comes before build/resources/main. src/test/resources/application.yaml therefore shadows src/main/resources/application.yaml completely, because Spring Boot loads only the first classpath:/application.yaml. The test yaml is deliberately offline: it has no spring.ai.typesafe.api-key placeholder, base-url is http://127.0.0.1:9, and sweep-initial-delay is PT1H. So MYFEEDER_TYPESAFE_API_KEY is never mapped (relaxed binding sends it to the unused myfeeder.typesafe.api-key), TypeSafeConfig logs 'not configured', and even a bound key would hit a refused port with the first sweep an hour out. Co-contributor: the 04-05 human-check (and 03-07/03-VERIFICATION) prescribe bootTestRun for live-key checks, contradicting the known test-yaml shadowing. Collateral loss under bootTestRun: spring.http.clients connect/read timeouts (5s/30s) and spring.application.name. Raindrop's token survives only because its env var name happens to equal its property path."
fix: ""
verification: ""
files_changed: []
