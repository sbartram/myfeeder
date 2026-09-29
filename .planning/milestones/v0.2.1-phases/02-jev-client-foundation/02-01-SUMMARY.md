---
phase: 02-jev-client-foundation
plan: 01
subsystem: infra
tags: [typesafe, jev, spring-ai, reactor-netty, resilience4j, aspectj, spring-boot]

# Dependency graph
requires:
  - phase: 01-dependency-upgrade
    provides: Boot 4.0.8, Reactor Netty RestClient transport (D-01), spring.http.clients timeouts (D-02), User-Agent RestClientCustomizer
provides:
  - App-owned TypeSafeClient bean (TypeSafeConfig.typeSafeClient). The context starts with the key absent, blank, whitespace or set
  - Jev-only Reactor Netty transport (connect 5s, read = spring.ai.typesafe.timeout) on a clone() of the auto-configured RestClient.Builder
  - spring.ai.typesafe pins in main YAML (optional key via MYFEEDER_TYPESAFE_API_KEY, model jev-1.13.0, timeout 5s, SDK retries 0) and a keyless test-YAML mirror with base-url http://127.0.0.1:9
  - spring-boot-starter-aspectj on the classpath, so Resilience4j @CircuitBreaker/@Retry aspects are active app-wide
  - Single "Jev configured" predicate, StringUtils.hasText(TypeSafeProperties.getApiKey()), for 02-02 and the Phase 3/4 status endpoint to reuse
affects: [02-02 JevApiClientImpl, 02-03 Jev resilience, 02-04 helm/deploy key wiring, Phase 3/4 interest status endpoint, Raindrop]

# Actuals (#2632): chars/4 over the realized diff (15,073 diff chars + ~2,600 todo chars)
actuals:
  tokens: 4400
  tasks: 2
  commits: 2
plan_head_before: 8aaa8c203476265f8ea6ea28bea7982ba5cf1913

# Tech tracking
tech-stack:
  added:
    - org.springaicommunity:spring-ai-starter-typesafe:0.1.0 (plus transitive typesafe-java-sdk:0.1.0)
    - org.springframework.boot:spring-boot-starter-aspectj (Boot BOM 4.0.8, brings org.aspectj:aspectjweaver 1.9.25.1)
  patterns:
    - "App-owned bean overrides a starter's @ConditionalOnMissingBean bean when the starter's own factory asserts on optional config"
    - "Per-client transport: clone() the injected RestClient.Builder, then set a dedicated ClientHttpRequestFactoryBuilder.reactor() factory"
    - "Context-runner test loads the starter auto-config plus a starter-alone negative control, so the test would catch removal of the override"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
    - src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java
    - .planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md
  modified:
    - build.gradle.kts
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml

key-decisions:
  - "TypeSafeClient is app-owned (Pattern A); the key reaches the SDK only through the Supplier overload, so a blank key is legal at build time"
  - "Jev transport is its own Reactor Netty factory (connect 5s / read 5s) on a cloned builder; the shared builder and the global spring.http.clients 5s/30s settings are unchanged"
  - "spring-boot-starter-aspectj landed in its own commit (0e85811), which makes a revert a single commit; Raindrop resilience is now live and was user-accepted on 2026-09-22"

patterns-established:
  - "Jev configured <=> StringUtils.hasText(spring.ai.typesafe.api-key); whitespace counts as not configured"
  - "Keyless startup logs exactly one fixed-text INFO line; never the key, its length or its prefix"

requirements-completed: [JEV-01, JEV-02, JEV-03]

coverage:
  - id: D1
    description: "Spring context starts with the TypeSafe key absent, blank, whitespace or set, with exactly one app-owned TypeSafeClient and the starter auto-config loaded; the starter-alone negative control fails with 'No API key configured'"
    requirement: JEV-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#absentKeyStartsWithAppOwnedClient, blankKeyStartsDespiteStarterAutoConfiguration, whitespaceKeyIsTreatedAsNotConfigured, setKeyStartsWithPinnedModel, starterAloneFailsOnBlankKey"
        status: pass
    human_judgment: false
  - id: D2
    description: "Keyless startup logs one fixed INFO line (D-10) and a set key never appears in captured output"
    requirement: JEV-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#keylessStartupLogsOneInfoLine, configuredKeyNeverAppearsInOutput"
        status: pass
    human_judgment: false
  - id: D3
    description: "Main YAML binds model jev-1.13.0, timeout 5s, SDK max-retries 0 and the default base-url https://api.typesafe.ai; test YAML mirrors the pins, has no key and uses a local base-url"
    requirement: JEV-03
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#mainYamlPinsModelTimeoutAndDisablesSdkRetries, testYamlMirrorsMainTypeSafePinsWithoutKey"
        status: pass
    human_judgment: false
  - id: D4
    description: "spring-boot-starter-aspectj is on the runtime classpath (JEV-02 prerequisite); exactly two org.springaicommunity artifacts at 0.1.0; spring-web 7.0.9 and jackson-databind 3.1.5 not downgraded"
    requirement: JEV-02
    verification:
      - kind: other
        ref: "./gradlew -q dependencies --configuration runtimeClasspath > ~/.cache/myfeeder-phase02/runtime-deps.txt + plan Task 2 verify #2 grep chain"
        status: pass
    human_judgment: false
  - id: D5
    description: "Full Docker-backed backend suite passes after both dependencies (171 tests, 0 failures/errors), including MyfeederApplicationTests (full app context starts keyless) and the Raindrop tests"
    requirement: JEV-01
    verification:
      - kind: integration
        ref: "DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew cleanTest test -x npmBuild -x npmInstall"
        status: pass
    human_judgment: false

# Metrics
duration: 4min
completed: 2026-09-23
status: complete
---

# Phase 2 Plan 01: Jev Client Foundation Summary

**App-owned TypeSafeClient (spring-ai-starter-typesafe 0.1.0) that starts keyless, on a Jev-only Reactor Netty transport with jev-1.13.0 pinned and SDK retries off, plus spring-boot-starter-aspectj so Resilience4j annotations take effect app-wide**

## Performance

- **Duration:** 4 min
- **Started:** 2026-09-23T03:04:56Z
- **Completed:** 2026-09-23T03:08:40Z
- **Tasks:** 2
- **Files modified:** 6 (5 in the task commits, plus 1 todo file in the docs commit)

## Accomplishments

- `TypeSafeConfig` owns the `TypeSafeClient` bean. The starter's `@ConditionalOnMissingBean` backs off, so its blank-key `Assert.state` never runs. The context starts with the key absent, blank, whitespace or set, and holds exactly one client in every case.
- The Jev transport is a dedicated Reactor Netty factory (`ClientHttpRequestFactoryBuilder.reactor()`, connect 5s, read = `spring.ai.typesafe.timeout`). It is set on a `clone()` of the auto-configured builder, so the User-Agent customizer is kept and the shared builder is untouched.
- The main YAML pins `model: jev-1.13.0`, `timeout: 5s` and `retry.max-retries: 0`, with the key optional via `${MYFEEDER_TYPESAFE_API_KEY:}`. The test YAML is a keyless mirror with `base-url: http://127.0.0.1:9`.
- `TypeSafeConfigTest` has nine Docker-free tests, including a starter-alone negative control and a leak check with `sk-test-LEAKCHECK`.
- `spring-boot-starter-aspectj` is in its own commit. The resolved classpath shows `aspectjweaver 1.9.25.1`, exactly two TypeSafe artifacts at 0.1.0, and no Spring/Jackson downgrade (snapshot in `~/.cache/myfeeder-phase02/runtime-deps.txt`).
- The full backend suite passes: 171 tests (162 before this plan + 9 new), 0 failures or errors. `MyfeederApplicationTests` logs the keyless INFO line once, which shows the whole app context starts without a key.

## Task Commits

1. **Task 1 (tracer): keyless-safe app-owned TypeSafeClient.** `02852db` (feat). The tracer gate re-ran its automated verify (interactive run, `end-of-phase`, automated-only verify): it passed, so the plan continued to Task 2.
2. **Task 2: activate Resilience4j aspects app-wide.** `0e85811` (build). build.gradle.kts only.

**Plan metadata:** see the `docs(02-01)` commit that follows (SUMMARY, STATE, ROADMAP, todo).

## Files Created/Modified

- `build.gradle.kts`: `extra["typesafeVersion"] = "0.1.0"`, the TypeSafe starter, and `spring-boot-starter-aspectj` (no version, BOM-managed)
- `src/main/resources/application.yaml`: `spring.ai.typesafe` block (optional key, model pin, 5s timeout, SDK retries 0). The raindrop and resilience4j blocks are byte-identical.
- `src/test/resources/application.yaml`: keyless `spring.ai.typesafe` mirror with the local base-url
- `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java`: app-owned client bean, D-10 INFO line, Jev-only transport, `JEV_CONNECT_TIMEOUT`
- `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java`: the nine context and YAML tests
- `.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`: the tracked follow-up

## Decisions Made

- Followed the plan as specified. Key choices were already made: Pattern A (app-owned bean), D-05 (Reactor Netty only, cloned builder), D-10 (fixed-text keyless INFO line), and a separate AspectJ commit.
- The test YAML's comment doesn't name the production key variable, so the "no mention of the production key variable" rule holds for comments as well as values.

## Raindrop behavior change (user-visible)

Adding `spring-boot-starter-aspectj` activates Raindrop's `@CircuitBreaker`/`@Retry`. Before this, they were inert because AspectJ was absent, so Resilience4j never registered its aspects. Raindrop's config and code were **not** changed. From now on:

- **Retries:** Raindrop calls retry up to 3 attempts, 1s apart (fixed, because `exponential-backoff-multiplier` has no effect without `enable-exponential-backoff`), on **any** exception except `RaindropNotConfiguredException`. `retry.instances.raindrop` has no `retry-exceptions`, so 4xx responses are retried too.
- **Breaker:** the `raindrop` breaker is live: COUNT_BASED window 10, minimum 5 calls, 50% failure rate, 30s open. Retry is the outer aspect, so every attempt counts toward the breaker.
- **Errors:** failures now go through the fallback and surface as `IllegalStateException` → **409 "Raindrop.io is currently unavailable"**, not the raw error. A missing token still returns 503 (`RaindropNotConfiguredException` is rethrown and ignored by both instances).

The user accepted this on **2026-09-22** (RESEARCH Open Question 1, RESOLVED: "Accept as planned"). All existing Raindrop tests (`RaindropApiClientImplTest`, `RaindropServiceTest`) pass unchanged.

**Follow-up quick task (tracked as a pending todo):** `.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` covers two items:
1. Raindrop tuning: `retry-exceptions` (don't retry 4xx), and either `enable-exponential-backoff: true` or removing the inert multiplier.
2. CLAUDE.md corrections:
   - the Resilience4j annotations need `spring-boot-starter-aspectj`
   - Retry is the outer aspect, so every attempt counts toward the breaker
   - new classes (`config/TypeSafeConfig`, Jev integration classes) belong in Package Structure
   - `MYFEEDER_TYPESAFE_API_KEY` is an optional deploy.sh variable

CLAUDE.md was not edited in this plan. The user's uncommitted edits to it are untouched and unstaged.

## Deviations from Plan

None. The plan was executed exactly as written.

## Issues Encountered

None. The pre-commit hook (TruffleHog) set the user's unstaged edits aside as a patch file and restored them on both commits. `git status` still shows ` M` for `CLAUDE.md`, `.claude/CLAUDE.md` and `.planning/config.json`.

## Estimate Calibration

The plan estimated 65,000 tokens (confidence: low). The realized diff is about 4,400 tokens (chars/4). The estimate was far too high: the plan text already held nearly all of the code, so implementation was mostly transcription.

## User Setup Required

None. The key stays optional; Helm and deploy.sh wiring comes in plan 02-04.

## Next Phase Readiness

- 02-02 can inject `TypeSafeClient` and reuse `StringUtils.hasText(TypeSafeProperties.getApiKey())` in `JevApiClientImpl.requireConfigured()`.
- 02-03 can rely on active Resilience4j aspects. It should still assert `AopUtils.isAopProxy` in its tests.
- Requirements JEV-01/02/03 are shared with sibling plans. `requirements.ready-ids` reported 0/3 ready, so they are not yet marked complete in REQUIREMENTS.md. They are marked when the last declaring plan finishes.

---
*Phase: 02-jev-client-foundation*
*Completed: 2026-09-23*

## Self-Check: PASSED

- FOUND: src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
- FOUND: src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java
- FOUND: .planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md
- FOUND: ~/.cache/myfeeder-phase02/runtime-deps.txt
- FOUND: commit 02852db, commit 0e85811
- Task 1 acceptance greps all passed; Task 2 verify 1-4 all passed; plan verification (`EnableAsync` 0, `spring.threads.virtual` 0) passed
