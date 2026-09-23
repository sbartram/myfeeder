---
phase: 02-jev-client-foundation
verified: 2026-09-23T03:45:00Z
status: passed
score: 3/4 roadmap success criteria verified (SC2 live call pending human); plan truths all verified except 2 backstop truths (insufficient_spec)
covered_files:

  - .planning/REQUIREMENTS.md
  - .planning/phases/02-jev-client-foundation/02-01-PLAN.md
  - .planning/phases/02-jev-client-foundation/02-01-SUMMARY.md
  - .planning/phases/02-jev-client-foundation/02-02-PLAN.md
  - .planning/phases/02-jev-client-foundation/02-02-SUMMARY.md
  - .planning/phases/02-jev-client-foundation/02-03-PLAN.md
  - .planning/phases/02-jev-client-foundation/02-03-SUMMARY.md
  - .planning/phases/02-jev-client-foundation/02-04-PLAN.md
  - .planning/phases/02-jev-client-foundation/02-04-SUMMARY.md
  - build.gradle.kts
  - deploy.sh
  - helm/myfeeder/templates/app-deployment.yaml
  - helm/myfeeder/templates/app-secret.yaml
  - helm/myfeeder/values.yaml
  - src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/integration/JevJudgment.java
  - src/main/java/org/bartram/myfeeder/integration/JevNotConfiguredException.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - src/test/resources/application.yaml

covered_digest: "v1:sha256:eba6df247ca4af23f4b3d9d67af506f1304beaf3ed684101e0e563e84805d48a"
behavior_unverified: 0
overrides_applied: 0
deferred:

  - truth: "JEV-03 second half: the model id is stored with every score"
    addressed_in: "Phase 3 (V6 article_score schema) and Phase 4"
    evidence: "Phase 4 SC1: 'each eligible one ... gets exactly one stored judgment: profile score and confidence, a noul per topic, the model id, and the profile/topic versions'. Phase 2 SC2 only requires the client to expose the model id so it can be stored; JevJudgment.model() does that."
  - truth: "ROADMAP Phase 3 note: 'Phase 2 lays down /api/interest/status with the configured flag and breaker state'"
    addressed_in: "Phase 4"
    evidence: "Phase 4 SC4: 'GET /api/interest/status reports whether Jev is configured, the circuit-breaker state ...'. 02-CONTEXT.md explicitly scopes it out of Phase 2 ('GET /api/interest/status (JEV-05, Phase 4)'). Not a Phase 2 SC or requirement. The Phase 3 note is stale and INT-06 (Phase 3) will need at least the configured flag there."
human_verification:

  - test: "SC2 live smoke: JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=<your key> ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info"
    expected: "1 test run, 0 skipped, 0 failures; the output line 'Jev live smoke: model=jev-1.13.0 requestId=<non-empty> ...' appears; profile score maxLevel 4, noul t1 in [0,1]"
    why_human: "Needs the user's real, billed TypeSafe key. The verifier must not run it. The offline canary test only proves the model is read from the response body, not that the live API returns jev-1.13.0."
  - test: "Backstop truth (02-02): concurrent judge() calls share no mutable request state"
    expected: "Accept on inspection: JevApiClientImpl has only final fields (TypeSafeClient, TypeSafeProperties), copies state into a local LinkedHashMap per call; SDK TypeSafeClient thread-safety is asserted by the SDK, not tested here"
    why_human: "verification: backstop (non-inferable). No concurrent test exists; presence and wiring never qualify. Phase 4's executor is the first concurrent caller."
  - test: "Backstop truth (02-04): during a key-only rollout the RollingUpdate keeps the old pod serving until the new pod is ready"
    expected: "On the first keyed deploy (Phase 7), kubectl -n myfeeder rollout status shows a rolling replacement with no gap in readiness"
    why_human: "verification: backstop; requires a real cluster rollout, which this phase deliberately did not perform"
  - test: "Judgment-tier prohibition review (non-authoritative LLM verdicts, see report): user's uncommitted CLAUDE.md/.claude/CLAUDE.md/.planning/config.json edits untouched; no real helm/kubectl run against the cluster during 02-04"
    expected: "Confirm. Verifier evidence: git status still shows ' M' for all three files; no phase commit touches them. The no-cluster claim cannot be checked from the repo."
    why_human: "unverified-prohibition — human review recommended (judgment tier)"
---

# Phase 2: Jev Client Foundation Verification Report

**Phase Goal:** The app can make resilient Jev judgment calls on a pinned model when a key is configured, and runs exactly as before when no key is set
**Verified:** 2026-09-23T03:45:00Z
**Status:** human_needed
**Re-verification:** No (initial verification)

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | The app starts and serves feeds normally whether the TypeSafe API key is absent, blank or set, and a context test covers each case (the app owns the `TypeSafeClient` bean) | ✓ VERIFIED | `TypeSafeConfig.typeSafeClient` is app-owned, uses the Supplier `apiKey(...)` overload. `TypeSafeConfigTest` loads the starter's `TypeSafeAutoConfiguration` and covers absent / blank / whitespace / set key (each: context started, exactly 1 `TypeSafeClient`). Negative control `starterAloneFailsOnBlankKey` proves the blank-key test would catch removal of the app-owned bean. Starter jar inspected: only `typeSafeClient` and `typeSafeEndpoints` beans, both `@ConditionalOnMissingBean`. Full app context: I ran the full suite myself, and `MyfeederApplicationTests.contextLoads` passes keyless and logs the D-10 INFO line exactly once. No feed code references any Jev type (grep: Jev/TypeSafe symbols only in the 5 new files). |
| 2 | With a live key, a smoke call returns a judgment from `jev-1.13.0`, and the client exposes the response's model id so it can be stored with each score | ? UNCERTAIN (human) | Exposure half VERIFIED: `JevJudgment.from` maps `response.model()`; `judgeReturnsResponseModelNotConfiguredDefault` asserts a `jev-1.13.0-canary` response yields `model() == "jev-1.13.0-canary"` while the wire request carries `model: jev-1.13.0`. The pin is bound from main YAML (`mainYamlPinsModelTimeoutAndDisablesSdkRetries`). Live half NOT RUN: `JevLiveSmokeTest` is gated on `JEV_LIVE_SMOKE=true` and was skipped (correctly) in my run. |
| 3 | Sustained transient failures (429/5xx/timeout) are retried only by Resilience4j (SDK retries are off) and open the circuit breaker; per-article 400/422 errors are neither retried nor able to open the breaker | ✓ VERIFIED | `@CircuitBreaker(name="jev")` + `@Retry(name="jev")` on `JevApiClientImpl.judge`, no fallback. `jevClientIsAnAopProxy` asserts `AopUtils.isAopProxy`. SDK `retry.max-retries: 0` in main YAML (asserted). JevResilienceTest uses the production YAML against a real socket stub: 5xx = exactly 3 hits then typed `TypeSafeInternalServerException` (which also proves no SDK retry stacking); 429 honored `retry-after-ms` and retried; read timeout surfaced as `TypeSafeApiConnectionException` and hit 3 times; sustained 5xx opens the breaker (10th recorded failure) and the open breaker short-circuits with no HTTP request. 400 and 422: 1 hit each, 0 failed/0 successful calls recorded, breaker CLOSED. SDK bytecode checked: every status >= 500 maps to `TypeSafeInternalServerException` (529 to its subclass `TypeSafeOverloadedException`), and `TypeSafeApiTimeoutException` extends `TypeSafeApiConnectionException`, so the retry list covers all 5xx and timeouts. 429 and timeouts are recorded by the breaker because neither is, or extends, an ignored type (hierarchy verified via javap), and default recording is proven by the 5xx test. |
| 4 | `deploy.sh` and the Helm chart treat `MYFEEDER_TYPESAFE_API_KEY` as optional: a deploy without it succeeds with a warning, and changing only the key rolls the pod | ✓ VERIFIED | I ran these myself: `helm lint` passes; a keyless `helm template` renders `myfeeder-typesafe-api-key: ""` plus the `MYFEEDER_TYPESAFE_API_KEY` secretKeyRef env; `checksum/secret` appears exactly once, on the app pod template; key-A vs key-B gives a different checksum; a tag-only change keeps the same checksum. `deploy.sh` with a fake `helm` on PATH: a keyless run exits 0, prints the TypeSafe warning and forwards `secrets.typesafeApiKey=`; a keyed run exits 0, forwards the key, prints no TypeSafe warning and never echoes the key. `bash -n` passes. |

**Score:** 3/4 roadmap SCs verified. SC2 is waiting on the human live smoke. 0 truths are present but behavior-unverified.

### Plan must-have truths (merged, 02-01..02-04)

All plan truths were checked against code and tests. Every non-backstop truth is VERIFIED, including: D-04 ordered/null-dropped state, D-06 key-free WARN, D-07 Retry-After cap boundaries (unit plus a live 700 ms stub), D-08 typed propagation, D-09 breaker binding values, idempotent retry bodies, the User-Agent and Bearer headers on the Jev transport, and the test-YAML mirrors of the jev blocks and typesafe pins. Two truths are tagged `verification: backstop` and are recorded as `insufficient_spec`, routed to human verification:

- 02-02 "concurrent judge() calls share no mutable request state". Inspection supports it (final fields only, per-call local copy), but there is no concurrent test.
- 02-04 "RollingUpdate keeps the old pod serving during a key-only rollout". This needs a cluster.

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Model id **stored** with every score (JEV-03 second half) | Phase 3/4 | Phase 4 SC1 lists "the model id" in the stored judgment |
| 2 | `/api/interest/status` with configured flag and breaker state (ROADMAP Phase 3 note says Phase 2 lays it down) | Phase 4 | Phase 4 SC4; 02-CONTEXT scopes it out of Phase 2 |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `build.gradle.kts` | TypeSafe starter 0.1.0 + spring-boot-starter-aspectj | ✓ VERIFIED | Both present; aspectj has no version (BOM) and landed in its own commit 0e85811 |
| `config/TypeSafeConfig.java` | App-owned client, keyless INFO, Jev-only Reactor Netty, Retry-After customizer | ✓ VERIFIED | Substantive (85 lines); `clone()` of the builder; `ClientHttpRequestFactoryBuilder.reactor()`; `RetryConfigCustomizer.of("jev", ...)` |
| `integration/JevApiClient.java` | Generic `judge(state, questions)` contract | ✓ VERIFIED | Single method, SDK `Question` types |
| `integration/JevApiClientImpl.java` | Guard, D-04 cleaning, answer checks, D-06 WARN, R4j annotations | ✓ VERIFIED | Wired: constructor-injected with the app-owned `TypeSafeClient`; `@Component` |
| `integration/JevJudgment.java` | SDK-free record with the response model | ✓ VERIFIED | `response.model()`; unmodifiable ordered maps |
| `integration/JevNotConfiguredException.java` | Not-configured signal | ✓ VERIFIED | In the breaker and retry ignore lists |
| `src/main/resources/application.yaml` | typesafe pins + jev resilience4j instances | ✓ VERIFIED | Raindrop blocks unchanged (no +/- raindrop lines in the phase diff) |
| `src/test/resources/application.yaml` | Keyless mirror, local base-url | ✓ VERIFIED | Mirror asserted by `testYamlMirrorsMainTypeSafePinsWithoutKey` / `testYamlMirrorsMainJevInstances` |
| Helm values/secret/deployment | Optional secret, env, checksum annotation | ✓ VERIFIED | Rendered and diffed (see SC4) |
| `deploy.sh` | Optional key + warning, forwarded | ✓ VERIFIED | Fake-helm dry run |
| Tests (TypeSafeConfigTest 11, JevApiClientImplTest 18, JevResilienceTest 12, JevLiveSmokeTest 1 gated) | Behavioral coverage | ✓ VERIFIED | All green in my full run |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| application.yaml `spring.ai.typesafe.*` | TypeSafeProperties | `@EnableConfigurationProperties(TypeSafeProperties.class)` on TypeSafeConfig | WIRED | The gsd tool reported the pattern missing only because it searches from/to files; the annotation is in TypeSafeConfig.java (read). Bound values are asserted from the main YAML. |
| TypeSafeConfig | RestClientConfig (User-Agent) | cloned auto-configured builder | WIRED | `outboundRequestCarriesUserAgentAndBearer` asserts `myfeeder/9.9.9-test` on the wire |
| JevApiClientImpl | TypeSafeConfig bean | constructor injection | WIRED | |
| JevJudgment | `SystemOneResponse.model()` | `from()` | WIRED | |
| `name = "jev"` annotations | resilience4j jev instances | R4j registry | WIRED | `mainYamlJevInstancesBindAsSpecified` |
| RetryConfigCustomizer | retry.instances.jev | `RetryConfigCustomizer.of("jev")` | WIRED | 700 ms Retry-After stub test with a 10 ms base |
| spring-boot-starter-aspectj | JevApiClientImpl proxy | AOP auto-config | WIRED | `AopUtils.isAopProxy` true |
| deploy.sh | values `secrets.typesafeApiKey` | `--set` | WIRED | Fake-helm argv captured |
| app-deployment.yaml | app-secret.yaml | secretKeyRef + `include ... \| sha256sum` | WIRED | Rendered |
| Pod env `MYFEEDER_TYPESAFE_API_KEY` | application.yaml | `${MYFEEDER_TYPESAFE_API_KEY:}` | WIRED | The gsd tool rejected a non-file `from`; verified manually (both ends present) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend suite (run once, keyless, JEV_LIVE_SMOKE unset) | `DOCKER_HOST=... ./gradlew cleanTest test -x npmBuild -x npmInstall` | BUILD SUCCESSFUL; 35 suites, 204 tests, 0 failures, 0 errors, 1 skipped (JevLiveSmokeTest) | ✓ PASS |
| Full app context starts keyless | `MyfeederApplicationTests.contextLoads` (from the same run) | pass; D-10 INFO line present once | ✓ PASS |
| Helm lint / keyless render / checksum roll | `helm lint`, `helm template` x4 | lint OK; empty key rendered; checksum differs on key-only and is stable on tag-only; 1 occurrence | ✓ PASS |
| deploy.sh optional key | fake `helm` on PATH, `env -i` | keyless exit 0 + warning + `secrets.typesafeApiKey=`; keyed exit 0, key not echoed | ✓ PASS |
| Live Jev call returns jev-1.13.0 | JevLiveSmokeTest | not run (billed, needs user key) | ? SKIP → human |

### Probe Execution

No probes are declared by the phase, and `scripts/*/tests/probe-*.sh` does not apply. SKIPPED.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| JEV-01 | 02-01, 02-02 | App starts/runs with key absent, blank or set | ✓ SATISFIED | SC1 evidence |
| JEV-02 | 02-01, 02-03 | Dedicated client bean with @CircuitBreaker + @Retry; R4j the only retry layer; 400/422 don't open the breaker | ✓ SATISFIED | SC3 evidence |
| JEV-03 | 02-01, 02-02 | Model pinned jev-1.13.0; model id stored with every score | ✓ SATISFIED (pin + exposure) / storage deferred | Pin bound and sent on the wire; storage is Phase 4 SC1. Live confirmation is waiting on the SC2 human check. |
| JEV-04 | 02-04 | Optional key in deploy.sh and Helm; key-only change rolls the pod | ✓ SATISFIED | SC4 evidence |

No orphaned requirements. REQUIREMENTS.md maps exactly JEV-01..04 to Phase 2, and the plans claim all four.

### Prohibitions

| Prohibition | Tier | Verdict |
|-------------|------|---------|
| Startup/polling/endpoints never depend on the key (02-01) | test | VERIFIED: TypeSafeConfigTest + keyless MyfeederApplicationTests |
| Default test run never calls the live API (02-02) | test | VERIFIED: method-level `@EnabledIfEnvironmentVariable(JEV_LIVE_SMOKE=true)`; skipped in my run |
| Key/response body never in logs or JevJudgment (02-02) | test | VERIFIED: `configuredKeyNeverAppearsInOutput`, `rejectedKeyLogsWarnWithRequestIdOnly`, `rejectedKeyIsNotRetriedButRecorded` (no LEAKCHECK); JevJudgment has no body field |
| No stacked retry layers; no retry of 400/401/403/422/not-configured/open-circuit (02-03) | test | VERIFIED: hit counts + retry predicate assertions |
| No wrapping of Jev failures (02-03) | test | VERIFIED: `isExactlyInstanceOf` on every typed path; no fallbackMethod |
| Deploy/pod never fails on a missing key (02-04) | test | VERIFIED: fake-helm keyless exit 0; chart has no `required`/`fail` |
| Raindrop resilience config/code unchanged (02-01, 02-03) | judgment | LLM verdict (non-authoritative): upheld. No Raindrop lines in the phase diff; RaindropApiClientImpl untouched |
| No weakening of existing tests (02-01) | judgment | LLM verdict: upheld. The phase diff adds only new test files; no existing test was modified |
| User's uncommitted edits untouched (all) | judgment | LLM verdict: upheld. `git status` still shows ` M` on all three files. Flagged for human review |
| No real helm/kubectl against the cluster (02-04) | judgment | Unverifiable from the repo. Flagged: unverified-prohibition, human review recommended |

### Anti-Patterns Found

No TBD/FIXME/XXX/TODO/HACK/placeholder markers in any phase-modified file. No stubs.

Code-review warnings (02-REVIEW.md), assessed against the success criteria:

| Finding | Severity here | Undermines an SC? |
|---------|---------------|-------------------|
| WR-01: `IllegalArgumentException` (null state / empty questions) and client-side serialization `TypeSafeException` run inside the proxy and are recorded as breaker failures | ⚠️ Warning | No. SC3 covers API 400/422, which are correctly ignored. But a repeated Phase 4 caller bug could open the breaker. Fix before Phase 4 (ignore IAE, or validate outside the proxy). Not retried, since it is not in retry-exceptions. |
| WR-03: Retry is the outer aspect, so D-09 thresholds count attempts, not logical calls (4 failing articles open the breaker) | ⚠️ Warning | No. SC3 requires that sustained transients open the breaker, and they do (faster than D-09 intended). The test asserts this ordering deliberately. Phase 4 should decide consciously whether to keep attempt-level counting or restate D-09. |
| WR-02: Choice questions accepted but their answers dropped from JevJudgment | ⚠️ Warning | No. Phase 3/4 use only Noul/Score. Cheap to reject now because D-02 marks JevJudgment as costly to reshape. |
| WR-04: AspectJ activates Raindrop `@Retry` on the non-idempotent `createBookmark` POST, so a read timeout or 5xx can duplicate bookmarks | ⚠️ Warning | No Phase 2 SC. But this user-visible risk goes beyond what the user accepted on 2026-09-22 (the pending todo does not mention duplicates). Add it to the Raindrop follow-up todo. |
| WR-05: `helm --set` strips backslashes / rejects commas in secret values; the key is visible on helm argv | ⚠️ Warning | No for SC4 as stated (optional + roll both work). It is a latent failure mode for a key containing `,` or `\`, and it matches the pre-existing pattern for the other secrets. |
| IN-02: The live smoke test runs without the R4j aspects, so one transient 429/5xx fails the billed run | ℹ️ Info | Relevant to the SC2 human check. If it fails with 429/5xx, simply re-run it. |
| IN-06: A blank `model` override silently unpins to `jev-latest` via the SDK's env-var fallback | ℹ️ Info | The pin holds as configured. The live smoke asserts the response model. |

### Human Verification Required

#### 1. SC2 live smoke (required to close the phase)

**Test:** `JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=<key> ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info`
**Expected:** 1 test executed (not skipped), passing; output contains `Jev live smoke: model=jev-1.13.0 requestId=...`
**Why human:** A real, billed API key is required. If one transient 429/5xx fails it (IN-02: no R4j aspects in this runner), re-run it.

#### 2. Backstop: concurrent judge() safety (02-02)

**Test:** Accept on inspection, or add a concurrent stub test in Phase 4.
**Expected:** No shared mutable request state.
**Why human:** A non-inferable backstop truth; presence and wiring do not qualify.

#### 3. Backstop: key-only rolling update keeps serving (02-04)

**Test:** Observe `kubectl rollout status` on the first keyed deploy (Phase 7).
**Expected:** A rolling replacement with no readiness gap.
**Why human:** Needs the cluster.

#### 4. Judgment-tier prohibitions

**Test:** Confirm that no real helm/kubectl was run against k3s during 02-04, and that the uncommitted CLAUDE.md/config edits are intact.
**Expected:** Confirmed.
**Why human:** unverified-prohibition, human review recommended.

### Gaps Summary

No blocking gaps. The codebase achieves the phase goal for everything that can be checked offline:

- The app-owned client starts in every key state, including the full app context keyless.
- The model pin is bound and sent on the wire.
- The response model id is exposed.
- Resilience4j is the only retry layer, proven through the real AOP proxy against a socket stub with the production YAML.
- 400/422 neither retry nor record.
- The deploy path is keyless-safe, and a key-only change rolls the pod.

The one open item is SC2's live call, which by design needs the user's billed key. Five review warnings do not break any success criterion. Two of them should be decided before Phase 4 builds on this client: WR-01 (caller errors counted by the breaker) and WR-03 (attempt-level breaker counting). WR-04 (duplicate Raindrop bookmarks from the newly active retry) should be added to the existing Raindrop follow-up todo.

---

_Verified: 2026-09-23T03:45:00Z_
_Verifier: Claude (gsd-verifier)_
