---
phase: 07-rollout-calibration
plan: 03
subsystem: integration
tags: [resilience4j, logging, jev, typesafe, circuit-breaker, retry, observability]

requires:
  - phase: 02
    provides: "jev @CircuitBreaker/@Retry instances on JevApiClientImpl.judge, fixed-text log rule (Phase 2 D-06), TypeSafeConfig.jevRetryInterval honoring retry-after-ms"
provides:
  - "JevEventLogging: INFO `Jev retry attempt <n> after <Class> (waiting <ms> ms)`, WARN `Jev retries exhausted after <n> attempts: <Class>`, WARN `Jev circuit breaker <FROM>_TO_<TO> (failure rate <x>%, slow-call rate <y>%)`"
  - "OutputCapture proofs of all three lines through the real AOP proxy and socket, with no key or body leak"
affects: [07-07 launch watch (greps these prefixes), 07-BACKFILL evidence, OPS-01]

actuals:
  tokens: 1997
  tasks: 2
  commits: 2
plan_head_before: 41dc7fb5726acef56f8ed33c4950c1b2149f786d

tech-stack:
  added: []
  patterns:
    - "Resilience4j event-publisher subscription in a @Configuration constructor (registry get-or-create by name, never reconfigures)"
    - "Log StateTransition.name(), never the enum with {} (toString() is prose)"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  modified:
    - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java

key-decisions:
  - "JevEventLogging is a public @Configuration(proxyBeanMethods = false) class that only subscribes to the jev retry/breaker event publishers; no Actuator or Micrometer metric (D-06/D-15)"
  - "Non-retryable per-article errors (RetryOnIgnoredErrorEvent) are deliberately not logged"

patterns-established:
  - "Jev event lines carry only getClass().getSimpleName() and numbers, never getMessage(), a body or the key"

requirements-completed: [OPS-01]

coverage:
  - id: D1
    description: "A 429 absorbed by the jev retry logs `Jev retry attempt 1 after TypeSafeRateLimitException (waiting 700 ms)` with no key or body text"
    requirement: OPS-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#rateLimitRetryIsLoggedWithoutLeaking"
        status: pass
    human_judgment: false
  - id: D2
    description: "A jev call that uses all 3 attempts logs two retry lines and one WARN exhausted line, with no key or body text"
    requirement: OPS-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#exhaustedRetriesAreLogged"
        status: pass
    human_judgment: false
  - id: D3
    description: "Every jev breaker state transition logs a WARN line in the `<FROM>_TO_<TO>` form, never the prose toString()"
    requirement: OPS-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#breakerTransitionsAreLogged"
        status: pass
    human_judgment: false
  - id: D4
    description: "Per-article 400/422 errors log no retry or exhausted line"
    requirement: OPS-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#perArticleErrorsLogNoRetryLine"
        status: pass
    human_judgment: false
  - id: D5
    description: "Retry and breaker behavior are unchanged with the logger registered, and the full application context boots with it"
    requirement: OPS-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java (all 20 cases)"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java (3 cases, Testcontainers)"
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-09-27
status: complete
---

# Phase 07 Plan 03: Jev Event Logging Summary

**Every jev retry, exhausted retry and breaker transition now produces one fixed-text log line: INFO for a retry, WARN for an exhausted retry or a transition. The lines carry only exception class names and numbers. The 07-07 launch watch can grep them to show that the breaker stayed CLOSED and that 429s were absorbed.**

## Performance

- **Duration:** about 3 min (171 s measured)
- **Started:** 2026-09-27T20:26:43Z
- **Completed:** 2026-09-27T20:29:34Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- `config/JevEventLogging` (public, `@Configuration(proxyBeanMethods = false)`) gets the `jev` instances by name from `CircuitBreakerRegistry` and `RetryRegistry`. These are the same instances the annotation aspects use. It subscribes to `onRetry`, `onError` and `onStateTransition` and logs these exact format strings:
  - INFO `Jev retry attempt {} after {} (waiting {} ms)`
  - WARN `Jev retries exhausted after {} attempts: {}`
  - WARN `Jev circuit breaker {} (failure rate {}%, slow-call rate {}%)`, using `getStateTransition().name()`
- A stubbed 429 with `retry-after-ms: 700`, sent through the real proxy, logs `Jev retry attempt 1 after TypeSafeRateLimitException (waiting 700 ms)`.
- Three 500s log retry attempts 1 and 2 and then `Jev retries exhausted after 3 attempts: TypeSafeInternalServerException`.
- Forced transitions log `CLOSED_TO_OPEN`, `OPEN_TO_HALF_OPEN` and `HALF_OPEN_TO_CLOSED`, never `State transition from`.
- A 400 logs neither a retry line nor an exhausted line.
- No logged line contains `LEAKCHECK` (the fake key) or `stubbed failure` (the stub error body).
- Every existing JevResilienceTest case passes with the logger registered in the shared runner (20/20). `MyfeederApplicationTests` boots the full context with it (3/3).

## Task Commits

1. **Task 1 (tracer): the 429 retry line through the proxied client.** `ba62410` (feat)
2. **Task 2: exhausted, transition and silent-error proofs plus the app-context boot.** `79019da` (test)

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/config/JevEventLogging.java`: the jev retry and breaker event logger (D-15)
- `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java`: the runner registers `JevEventLogging.class`, plus four new OutputCapture tests (`rateLimitRetryIsLoggedWithoutLeaking`, `exhaustedRetriesAreLogged`, `breakerTransitionsAreLogged`, `perArticleErrorsLogNoRetryLine`)

## Decisions Made

- Followed the plan as written. The Javadoc records D-15, the fixed-text rule (Phase 2 D-06) and why `name()` is logged instead of `toString()`.

## TDD Gate Compliance

Task 2 is `tdd="true"` but changes only the test file: the behavior it covers shipped in the Task 1 tracer, as the plan intended. Its tests could therefore not go RED against HEAD, and the commit order is `feat(07-03)` (Task 1) before `test(07-03)` (Task 2). To prove the tests are not vacuous, I ran a mutation check before committing:

- **The mutation:** `JevEventLogging` temporarily logged the transition with `toString()` and demoted the exhausted line to DEBUG.
- **The result:** exactly `breakerTransitionsAreLogged` and `exhaustedRetriesAreLogged` failed on their assertions (20 run, 2 failed).
- **The evidence check:** `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (`target_test_failed`) for both target tests. The TAP was converted from the run's JUnit XML.
- **Cleanup:** the mutation was reverted with a single-file `git checkout` before GREEN. `src/main` was clean when the GREEN run started.

## Deviations from Plan

None. The plan was executed exactly as written.

## Issues Encountered

None.

## Known Stubs

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- The 07-07 watch script can grep the three prefixes: `Jev retry attempt`, `Jev retries exhausted` and `Jev circuit breaker`.
- Metrics read `-1.0%` until the breaker's minimum call count (10) is reached, so the watch should not read a `-1.0` rate as a failure.

## Self-Check: PASSED

- FOUND: `src/main/java/org/bartram/myfeeder/config/JevEventLogging.java`
- FOUND: `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java`
- FOUND: `ba62410`, `79019da`
- Plan verification (`JevResilienceTest` + `MyfeederApplicationTests`) passed: 20/20 and 3/3, with zero failures and errors.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-27*
