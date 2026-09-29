---
phase: 02-jev-client-foundation
plan: 03
subsystem: integration
tags: [resilience4j, circuit-breaker, retry, aspectj, typesafe, jev, reactor-netty]

requires:
  - phase: 02-jev-client-foundation (02-01)
    provides: App-owned TypeSafeClient on a Jev-only Reactor Netty transport, spring-boot-starter-aspectj, SDK max-retries 0
  - phase: 02-jev-client-foundation (02-02)
    provides: JevApiClient/JevApiClientImpl.judge with typed exceptions and the key-free 401/403 WARN
provides:
  - "@CircuitBreaker(name = \"jev\") + @Retry(name = \"jev\") on JevApiClientImpl.judge (no fallback, D-08)"
  - "resilience4j.circuitbreaker.instances.jev (D-09) and resilience4j.retry.instances.jev in main and test application.yaml"
  - "TypeSafeConfig.jevRetryInterval RetryConfigCustomizer (Retry-After aware, capped at MAX_RETRY_AFTER_MS = 10s, D-07)"
  - "JevResilienceTest: 12 AOP-proxy + real-socket tests pinning retry/breaker classification"
affects: [phase-04-scoring-worker, jev-status-endpoint, phase-07-launch-backfill]

actuals:
  tokens: 9601
  tasks: 3
  commits: 4
plan_head_before: 936895d63d4dca418100fe293ca95d31e523111b

tech-stack:
  added: []
  patterns:
    - "Resilience annotations on the API-client bean, no fallback, so typed SDK exceptions and CallNotPermittedException propagate unchanged"
    - "RetryConfigCustomizer bean overrides the YAML wait-duration interval; base read via Binder (not @Value Duration)"
    - "Docker-free resilience tests: ApplicationContextRunner + AopAutoConfiguration + resilience4j springboot3 auto-configs + JDK HttpServer stub, main YAML loaded from disk with addLast"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
    - src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java

key-decisions:
  - "No fallbackMethod on judge: typed SDK exceptions and CallNotPermittedException reach callers unwrapped (D-08); proven by isExactlyInstanceOf through the proxy"
  - "Retry is the outer aspect, so the jev breaker records every attempt: 3 calls x 3 5xx attempts = 9 failures (CLOSED), the 10th failure opens it"
  - "For Phase 4: the breaker is the shared 'jev' instance in CircuitBreakerRegistry (status endpoint can read it); CallNotPermittedException means open; timeouts arrive as TypeSafeApiConnectionException (never TypeSafeApiTimeoutException) with io.netty ReadTimeoutException in the cause chain"

patterns-established:
  - "Every resilience test asserts behavior through the real AOP proxy (AopUtils.isAopProxy) against a real socket, never a mocked factory"
  - "Test YAML resilience blocks must mirror main key-for-key; a test (testYamlMirrorsMainJevInstances) enforces it"

requirements-completed: [JEV-02]

coverage:
  - id: D1
    description: "judge() runs through a Resilience4j AOP proxy; a sustained 5xx is attempted 3 times and surfaces as the unwrapped TypeSafeInternalServerException"
    requirement: JEV-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#jevClientIsAnAopProxy"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#serverErrorIsAttemptedThreeTimesThenPropagatesTyped"
        status: pass
    human_judgment: false
  - id: D2
    description: "429 Retry-After honored inside the single retry layer, clamped to [0, 10000] ms, else 1s/2s exponential backoff (D-07)"
    requirement: JEV-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#retryIntervalHonorsRetryAfterUpToCap"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#retryIntervalFallsBackToExponentialBackoff"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#rateLimitWaitsForRetryAfterThenSucceeds"
        status: pass
    human_judgment: false
  - id: D3
    description: "Failure classification: 400/422 not retried or recorded; 401/403 recorded, not retried, WARN key-free; not-configured touches nothing; timeouts retried as TypeSafeApiConnectionException"
    requirement: JEV-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#badRequestAndUnprocessableAreNotRetriedOrRecorded"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#rejectedKeyIsNotRetriedButRecorded"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#notConfiguredIsNeitherRetriedNorRecorded"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#readTimeoutSurfacesAsConnectionExceptionAndIsRetried"
        status: pass
    human_judgment: false
  - id: D4
    description: "Breaker opens at exactly the 10th recorded failure (D-09) and then short-circuits with no HTTP request; CallNotPermittedException not retried"
    requirement: JEV-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#breakerOpensAtMinimumCallsAndShortCircuits"
        status: pass
    human_judgment: false
  - id: D5
    description: "Production jev instances bind as specified, test YAML mirrors main, headers (User-Agent, Bearer) survive the builder clone, retries resend identical bodies"
    requirement: JEV-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#mainYamlJevInstancesBindAsSpecified"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#testYamlMirrorsMainJevInstances"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#outboundRequestCarriesUserAgentAndBearer"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#retriedAttemptsSendIdenticalBodies"
        status: pass
    human_judgment: false
  - id: D6
    description: "Full Docker-backed backend suite green with the proxied client in the whole app context; JevLiveSmokeTest skipped"
    verification:
      - kind: integration
        ref: "DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew cleanTest test -x npmBuild -x npmInstall (204 tests, 1 skipped, 0 failures)"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-09-23
status: complete
---

# Phase 2 Plan 03: Jev Resilience Summary

**Jev calls now have one retry layer and a circuit breaker. `@CircuitBreaker("jev")` and `@Retry("jev")` sit on `JevApiClientImpl.judge`, the jev breaker uses the D-09 settings, a Retry-After-aware interval caps waits at 10s, and 12 tests run through the real AOP proxy against a socket stub.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-23T03:21:10Z
- **Completed:** 2026-09-23T03:27:29Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- `judge` runs through a Resilience4j CGLIB proxy with no fallback, so typed SDK exceptions and `CallNotPermittedException` come through unchanged (D-08).
- The `jev` breaker (COUNT_BASED, window 20, min calls 10, 50%, 60s open, 3s slow-call threshold) and the `jev` retry (3 attempts; retries only 429, 5xx and connection errors) are in both YAML files. The Raindrop blocks are untouched.
- The `TypeSafeConfig.jevRetryInterval` customizer waits `min(retryAfterMs, 10s)`, never less than 0, when a 429 carries a hint. Otherwise it waits the base, then twice the base (1s, then 2s in production).
- Tests prove that 400/422 are neither retried nor recorded, 401/403 are recorded but not retried (and their WARN never contains the key), and the not-configured case touches nothing. The breaker opens on exactly the 10th recorded failure and then makes no HTTP call. A Reactor Netty read timeout arrives as `TypeSafeApiConnectionException` and is retried.

## Task Commits

1. **Task 1 (tracer): 5xx retried through the AOP proxy, typed** - `6c55eab` (feat)
2. **Task 2: 429 Retry-After honored (D-07), TDD** - RED `c754c83` (test), GREEN `a3baf0a` (feat)
3. **Task 3: classification, D-09 binding, YAML mirror, full suite** - `8800851` (test)

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java` - adds the `@CircuitBreaker`/`@Retry` "jev" annotations on `judge`
- `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java` - adds `MAX_RETRY_AFTER_MS` and the `jevRetryInterval` RetryConfigCustomizer bean
- `src/main/resources/application.yaml` - adds the jev breaker and retry instances
- `src/test/resources/application.yaml` - an exact copy of the jev instances
- `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java` - two exact-value tests of the interval function
- `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` - 12 proxy and socket-stub tests with a nested `StubServer`

## Decisions Made
- `IntervalBiFunction<Object>` is declared as a typed local variable because `RetryConfigCustomizer.of` hands over a raw `RetryConfig.Builder`, so a bare lambda would not compile.
- Notes for Phase 4:
  - Read breaker state from `CircuitBreakerRegistry.circuitBreaker("jev")`.
  - `CallNotPermittedException` means the breaker is open.
  - Classify timeouts on the base `TypeSafeApiConnectionException`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] A no-op `jevRetryInterval` skeleton in the RED commit**
- **Found during:** Task 2 (TDD RED)
- **Issue:** Without the bean method, the new TypeSafeConfigTest unit tests fail to compile. That is INVALID_RED under the TDD gate: a compile failure is not an assertion failure.
- **Fix:** The RED commit adds `MAX_RETRY_AFTER_MS` and a `jevRetryInterval` that returns `RetryConfigCustomizer.of("jev", builder -> { })`. All three target tests then failed on assertions: 500 ms was the default where 700/1000 was expected, and 55 ms elapsed where at least 650 was expected. `check tdd-red-evidence` returned RED_EVIDENCE_OK for each. GREEN replaced the skeleton.
- **Files modified:** src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
- **Committed in:** c754c83 (RED), a3baf0a (GREEN)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** None. The final code matches the plan exactly.

## TDD Gate Compliance
Task 2 has RED `test(02-03)` c754c83, followed by GREEN `feat(02-03)` a3baf0a. No refactor commit was needed: a tidy-up was folded in before the GREEN commit.

## Issues Encountered
None.

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- JEV-02 / SC3 are met. The Phase 4 worker can rely on the exception types and breaker semantics listed above.
- The live SC2 smoke test (`JEV_LIVE_SMOKE=true`) is still a human check at the end of the phase.

---
*Phase: 02-jev-client-foundation*
*Completed: 2026-09-23*

## Self-Check: PASSED
- All 6 key files exist; commits 6c55eab, c754c83, a3baf0a, 8800851 exist.
- The Jev and config tests are green: JevResilienceTest 12/12, TypeSafeConfigTest 11/11, JevApiClientImplTest 18/18. The full Docker suite passed: 204 tests, 1 skipped (JevLiveSmokeTest), 0 failures.
