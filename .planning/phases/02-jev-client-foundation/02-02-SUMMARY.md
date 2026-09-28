---
phase: 02-jev-client-foundation
plan: 02
subsystem: api
tags: [typesafe, jev, spring-ai, restclient, mockrestserviceserver, junit5]

# Dependency graph
requires:
  - phase: 02-jev-client-foundation (plan 02-01)
    provides: App-owned TypeSafeClient bean (TypeSafeConfig), TypeSafeProperties binding, jev-1.13.0 pin, "configured" predicate StringUtils.hasText(apiKey)
provides:
  - JevApiClient.judge(Map<String, ?> state, Map<String, ? extends Question> questions), a generic pass-through (D-01, D-03)
  - JevJudgment(model, requestId, nouls, scores, inputTokens, outputTokens) + JevScore(value, maxLevel, confidence), SDK-free (D-02)
  - JevNotConfiguredException (FQN org.bartram.myfeeder.integration.JevNotConfiguredException) for 02-03's resilience4j ignore lists
  - JevApiClientImpl @Component (non-final, public judge) ready for 02-03's @CircuitBreaker/@Retry
  - Opt-in JevLiveSmokeTest (JEV_LIVE_SMOKE=true) for the SC2 human check
affects: [02-03 Jev resilience, Phase 3 topic preview + question builders, Phase 4 scorer + score storage, Phase 5 blend]

# Actuals (#2632): chars/4 over the realized diff (29,944 diff chars)
actuals:
  tokens: 7486
  tasks: 3
  commits: 4
plan_head_before: df9af1674fb657379e6abc22d030590585163dba

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wire tests build TypeSafeClient directly on a MockRestServiceServer-bound builder (never via TypeSafeConfig, whose Reactor factory would replace the mock)"
    - "Raw-body RequestMatcher (MockClientHttpRequest.getBodyAsString) to pin JSON key order; JSON-equality matchers ignore order"
    - "Parameterized test display name starts with {displayName} so JUnit XML reports stay greppable by method name"
    - "Live external-API tests are gated at method level on a dedicated opt-in env var, never on the credential"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/integration/JevNotConfiguredException.java
    - src/main/java/org/bartram/myfeeder/integration/JevJudgment.java
    - src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
    - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
    - src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java
    - src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java
  modified: []

key-decisions:
  - "JevJudgment.model comes from SystemOneResponse.model(), proven with a jev-1.13.0-canary response against the jev-1.13.0 default"
  - "judge() order: requireConfigured -> Assert.notNull(state)/notEmpty(questions) -> LinkedHashMap copy minus nulls -> SDK call (401/403 WARN status+requestId, rethrow) -> per-question noul/score/answer accessor check -> JevJudgment.from"
  - "Answer checks use the SDK accessors (response.noul/score/answer), so missing answers throw TypeSafeMissingAnswerException and wrong-kind answers throw TypeSafeAnswerTypeException"

patterns-established:
  - "Jev client boundary: callers pass SDK Question types in, get only the app-owned JevJudgment out"
  - "Key-free logging: 401/403 WARN carries HTTP status + requestId only, never e.getMessage()/e.body()"

requirements-completed: [JEV-01, JEV-03]

coverage:
  - id: D1
    description: "judge() returns a JevJudgment whose model is the response's model id (canary differs from the pinned default); request is POST /v1/systemone with bearer auth and model jev-1.13.0"
    requirement: JEV-03
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java#judgeReturnsResponseModelNotConfiguredDefault"
        status: pass
    human_judgment: false
  - id: D2
    description: "Keyless judge() throws JevNotConfiguredException with zero HTTP requests; null state / empty questions rejected with IllegalArgumentException before any HTTP; full app context still starts keyless"
    requirement: JEV-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java#judgeThrowsNotConfiguredWithoutHttpCall, judgeRejectsNullStateAndEmptyQuestionsBeforeHttp"
        status: pass
      - kind: integration
        ref: "DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew cleanTest test -x npmBuild -x npmInstall (MyfeederApplicationTests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-04 state contract: ordered JSON object, nulls dropped, caller map untouched, empty state sent as {}"
    requirement: JEV-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java#judgeSendsOrderedObjectStateWithoutNulls, judgeDoesNotMutateCallerState, judgeSendsEmptyStateAsObject"
        status: pass
    human_judgment: false
  - id: D4
    description: "Answer mapping: response order kept, exact doubles, unmodifiable maps, null usage/requestId, maxLevel -1 without legend, missing and wrong-kind answers throw typed SDK exceptions"
    requirement: JEV-03
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java#judgeKeepsAnswerOrderAndExactValues, judgeHandlesMissingUsageRequestIdAndLegend, judgeThrowsMissingAnswerWhenQuestionUnanswered, judgeThrowsAnswerTypeWhenKindMismatches"
        status: pass
    human_judgment: false
  - id: D5
    description: "HTTP 400/401/403/422/429/500/529 surface as the SDK's typed exceptions (429 retryAfterMs 700); 401 WARN has status + requestId and no key/body text"
    requirement: JEV-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java#httpErrorsSurfaceAsTypedExceptions (7 cases), rejectedKeyLogsWarnWithRequestIdOnly"
        status: pass
    human_judgment: false
  - id: D6
    description: "Live smoke (SC2): a real Jev call through TypeSafeConfig + JevApiClientImpl + main application.yaml returns model jev-1.13.0"
    requirement: JEV-03
    verification:
      - kind: unit
        ref: "env -u JEV_LIVE_SMOKE ./gradlew cleanTest test --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' (reported skipped)"
        status: pass
    human_judgment: true
    rationale: "Agents have no TypeSafe key and the call is billed to the user. Only the gate (skipped by default) is automated; the real call is the end-of-phase human check: JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=... ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info (expect PASSED, printed model jev-1.13.0, request id and tokens, no key in output)"

# Metrics
duration: 5min
completed: 2026-09-23
status: complete
---

# Phase 2 Plan 02: Jev Client Summary

**Generic `JevApiClient.judge(state, questions)` over the app-owned TypeSafeClient, returning an SDK-free `JevJudgment` whose model id comes from the response, with D-04 state cleaning, per-question answer checks, typed SDK errors, a key-free 401/403 WARN, and an opt-in live smoke test**

## Performance

- **Duration:** 5 min
- **Started:** 2026-09-23T03:13:46Z
- **Completed:** 2026-09-23T03:19:00Z
- **Tasks:** 3
- **Files modified:** 6 (all created)

## Accomplishments

- `JevApiClient` has one method, `JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions)`. Callers build SDK `Noul`/`Score` questions themselves (D-01, D-03).
- `JevJudgment` (+ nested `JevScore`) is the app-owned result. `model` is `response.model()`: a `jev-1.13.0-canary` response against the `jev-1.13.0` default yields `"jev-1.13.0-canary"` (JEV-03/SC2). Both maps are unmodifiable LinkedHashMap copies in response order, with values carried exactly as the SDK parsed them.
- `JevApiClientImpl` (`@Component`, non-final, public `judge`) checks the key first (zero HTTP when keyless), rejects a null state or empty questions with `IllegalArgumentException`, drops null state values in order without touching the caller's map, and checks every requested answer through `response.noul/score/answer`.
- 401/403 log `TypeSafe rejected the API key (status {}, requestId {})` and rethrow. The test uses a 401 body that echoes `sk-test-LEAKCHECK` and finds no `LEAKCHECK` in the output. Every other SDK exception propagates unchanged (D-08).
- `JevApiClientImplTest` has 18 offline test executions (11 methods, one parameterized over 7 statuses). `JevLiveSmokeTest` is skipped by default.
- The full Docker-backed suite passes: 190 tests (171 before + 19 new), 1 skipped (the live smoke), 0 failures or errors. `MyfeederApplicationTests` still starts keyless with the new `@Component` wired in.

## Task Commits

1. **Task 1 (tracer): judge() returns JevJudgment with the response model.** `b2949c7` (feat). Tracer gate: interactive run, `end-of-phase`, automated-only verify. Verify was re-run and passed, so the plan continued to Task 2.
2. **Task 2 (tdd): state/answer contract and typed errors.** RED `3d7fe29` (test), GREEN `e4608ab` (feat). No refactor commit was needed.
3. **Task 3: opt-in live smoke test.** `9a3951f` (test)

**Plan metadata:** the `docs(02-02)` commit that follows (SUMMARY, STATE, ROADMAP, REQUIREMENTS).

## TDD Gate Compliance (Task 2)

- **RED (`3d7fe29`):** 5 of the 18 test executions failed against the Task 1 implementation, each on an assertion for the planned behavior:
  - the body still carried `"summary":null`
  - a null state caused an NPE instead of an `IllegalArgumentException`
  - a missing answer did not throw
  - a wrong-kind answer did not throw
  - the WARN output was empty

  There were no compile, load or discovery errors. The other cases (ordering, exact values, typed HTTP errors) already held after Task 1 and serve as regression pins.
- **GREEN (`e4608ab`):** all 18 pass.
- **REFACTOR:** none.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/integration/JevNotConfiguredException.java`: the not-configured signal, which names `MYFEEDER_TYPESAFE_API_KEY`
- `src/main/java/org/bartram/myfeeder/integration/JevJudgment.java`: the result record, `JevScore`, and `from(SystemOneResponse)`
- `src/main/java/org/bartram/myfeeder/integration/JevApiClient.java`: the generic contract, with Javadoc on nulls, typed exceptions and timeouts
- `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java`: the client, covering the guard, argument checks, D-04, the D-06 WARN and answer checks
- `src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java`: offline MockRestServiceServer tests
- `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java`: the gated live SC2 test

## Decisions Made

- Followed the plan as specified. For the gated smoke test, I checked its production wiring offline with a throwaway test before committing, and deleted that test afterwards. The throwaway used a fake key via the YAML placeholder and a base-url of `http://127.0.0.1:9`. It confirmed one `TypeSafeProperties` bean, that the `JevApiClient` bean resolves, and that the call reaches the transport (`TypeSafeApiConnectionException`). Per orchestrator instructions, JEV_LIVE_SMOKE=true was never set.

## Notes for Phase 4 (scorer)

- Classify timeouts on the base `TypeSafeApiConnectionException`. Reactor Netty timeouts never produce `TypeSafeApiTimeoutException`.
- Per D-06, treat 401/403 (`TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException`) and `CallNotPermittedException` (breaker open, from 02-03) as transient. They should not burn a scoring attempt.
- `JevScore.maxLevel` can be -1 (no legend). Don't normalize by it blindly.
- Storing `JevJudgment.model()` with every score is Phase 4's must-have. It is the storage half of JEV-03, SCOR-03.
- `requestId`, `inputTokens` and `outputTokens` may be null.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Parameterized test display name made greppable**
- **Found during:** Task 2 (GREEN verify)
- **Issue:** JUnit's default parameterized display name is `[n] args`, so the JUnit XML had no `name="httpErrorsSurfaceAsTypedExceptions...` entry. The plan's verify grep (at least 7 matches) could not pass.
- **Fix:** `@ParameterizedTest(name = "{displayName} [{index}] status {0}")`
- **Files modified:** src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java
- **Verification:** the report shows 7 `httpErrorsSurfaceAsTypedExceptions(...)` cases, and verify 2 exits 0
- **Committed in:** e4608ab

---

**Total deviations:** 1 auto-fixed (1 blocking, test-only)
**Impact on plan:** None on production code; needed for the plan's own verification to be satisfiable.

## Issues Encountered

None. The TruffleHog pre-commit hook set the user's unstaged edits (`CLAUDE.md`, `.claude/CLAUDE.md`, `.planning/config.json`) aside and restored them on every commit. They remain unstaged, and nothing untracked was staged.

## Estimate Calibration

The plan estimated 70,000 tokens (confidence: low). The realized diff is about 7,500 tokens (chars/4). Again the plan text carried most of the design, so the estimate was roughly 9x too high.

## User Setup Required

Optional, for the end-of-phase SC2 human check only. Export `MYFEEDER_TYPESAFE_API_KEY` in one shell and run:
`JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=... ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info`
Expected result: PASSED (not skipped), printed model `jev-1.13.0`, a request id and token counts, and no key anywhere in the output.

## Next Phase Readiness

- 02-03 can add `@CircuitBreaker(name = "jev")` + `@Retry(name = "jev")` to `JevApiClientImpl.judge` without a signature change. `JevNotConfiguredException` has the exact FQN those ignore lists expect.
- Phase 3 can call `judge()` as-is for the topic preview. The question builders belong to Phase 3.

## Known Stubs

None.

---
*Phase: 02-jev-client-foundation*
*Completed: 2026-09-23*

## Self-Check: PASSED

- FOUND: all 6 created files (4 main, 2 test)
- FOUND: commits b2949c7, 3d7fe29, e4608ab, 9a3951f
- Task 1, 2 and 3 acceptance greps all passed. Every task verify block exited 0.
- Plan verification: the Docker-free `Jev*` + `config.*` slice is green, with JevLiveSmokeTest skipped. The full Docker-backed suite passed: 190 tests, 1 skipped, 0 failures or errors.
