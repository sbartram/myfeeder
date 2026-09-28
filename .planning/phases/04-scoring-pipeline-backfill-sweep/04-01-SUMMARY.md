---
phase: 04-scoring-pipeline-backfill-sweep
plan: 01
subsystem: integration
tags: [resilience4j, circuit-breaker, retry, typesafe, jev, aspect-order]

requires:
  - phase: 02-jev-client-foundation
    provides: JevApiClientImpl behind @CircuitBreaker/@Retry("jev"), JevResilienceTest socket-stub harness
  - phase: 03-interest-model-schema-rubric-editor
    provides: calibration latency figures (about 2.6s average, more than 5s cold)
provides:
  - Breaker-outer aspect order (circuit-breaker-aspect-order 1, retry-aspect-order 2), so the jev breaker counts logical calls
  - 30s shared TypeSafe timeout, 15s jev slow-call threshold, automatic OPEN to HALF_OPEN on jev
  - IllegalArgumentException ignored by the jev breaker; non-Noul/Score questions rejected before the HTTP call
  - CLAUDE.md Resilience4j convention describing the property-driven order
affects: [04-03 scorer, 04-05 sweep, 04-06 status counts, phase 5 paused state]

actuals:
  tokens: 5750
  tasks: 3
  commits: 5
plan_head_before: 6476f53edf4b2b3a057fa84538168904cc191a88

tech-stack:
  added: []
  patterns:
    - "Resilience4j aspect order set by properties outside instances; the breaker wraps the retry"
    - "Caller input errors (IllegalArgumentException) sit in the breaker ignore list and outside the retry allow-list"

key-files:
  created: []
  modified:
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
    - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
    - src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java
    - CLAUDE.md
    - .planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md

key-decisions:
  - "Aspect order is global: Raindrop now also has the breaker outside the retry (same 3 HTTP attempts, one breaker outcome per save, fail-fast when open)"
  - "The mirror test compares the two aspect-order keys by exact name, since they sit outside instances.jev"
  - "The answer-check loop no longer has a generic answer() branch; non-Noul questions are Score by construction after the D-15 check"

patterns-established:
  - "Evidence files for hunk-only CLAUDE.md commits live under $HOME/.cache/myfeeder-phase04/"

requirements-completed: [SCOR-05, SCOR-07]

coverage:
  - id: D1
    description: "The jev breaker wraps the retry: one logical judge() call records one breaker outcome (10 failing calls = 30 hits, 10 failures, OPEN; retried success = 1 success)"
    requirement: SCOR-07
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#breakerOpensAtMinimumCallsAndShortCircuits"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#retriedCallThatSucceedsRecordsOneSuccess"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#breakerAspectWrapsRetryAspect"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#testYamlMirrorsMainJevInstances"
        status: pass
    human_judgment: false
  - id: D2
    description: "An OPEN jev breaker moves to HALF_OPEN on its own after wait-duration-in-open-state (D-17)"
    requirement: SCOR-05
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#openBreakerMovesToHalfOpenWithoutACall"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#mainYamlJevInstancesBindAsSpecified"
        status: pass
    human_judgment: false
  - id: D3
    description: "Null state, empty questions and Choice questions throw IllegalArgumentException with 0 HTTP hits and nothing recorded by the breaker (D-14, D-15)"
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java#callerInputErrorsAreNeitherSentNorRecorded"
        status: pass
    human_judgment: false
  - id: D4
    description: "Shared TypeSafe timeout 30s and jev slow-call threshold 15s, mirrored in the test YAML (D-05, D-06)"
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#mainYamlPinsModelTimeoutAndDisablesSdkRetries"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java#testYamlMirrorsMainTypeSafePinsWithoutKey"
        status: pass
    human_judgment: false
  - id: D5
    description: "The preview spinner can now run up to about 93s in the worst case (3 attempts x 30s plus backoff)"
    verification: []
    human_judgment: true
    rationale: "UX tolerance of a long preview spinner is a human judgment; no test asserts a frontend ceiling"
  - id: D6
    description: "CLAUDE.md Resilience4j bullet names both aspect-order keys, the one-outcome-per-call behavior and the aspectj requirement; user hunks untouched"
    verification:
      - kind: other
        ref: "git show HEAD~1:CLAUDE.md phrase checks + before/after hunk snapshot diff (empty)"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 01: Jev Resilience Hardening Summary

**The jev circuit breaker now wraps the retry, so it counts articles instead of attempts. The shared timeout is 30s and the slow-call threshold 15s. An OPEN breaker moves to HALF_OPEN on its own. Caller bugs and Choice questions are rejected before billing and are never recorded by the breaker.**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-24T01:19:15Z
- **Completed:** 2026-09-24T01:24:42Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- D-07: `resilience4j.circuitbreaker.circuit-breaker-aspect-order: 1` and `resilience4j.retry.retry-aspect-order: 2` are set in both YAML files. In the running context `CircuitBreakerAspect.getOrder()` is 1 and `RetryAspect.getOrder()` is 2. Ten failing calls make 30 HTTP hits, record 10 failures and open the breaker. The 11th call throws `CallNotPermittedException` without an HTTP hit. A call answered 500, 500, 200 records 0 failures and 1 success.
- D-05/D-06: `spring.ai.typesafe.timeout` is 30s (one shared `TypeSafeClient`), and the jev `slow-call-duration-threshold` is 15s. Both are mirrored in the test YAML.
- D-17: `automatic-transition-from-open-to-half-open-enabled: true` on jev. A forced-OPEN breaker reaches HALF_OPEN after 200ms with no call made.
- D-14/D-15: `java.lang.IllegalArgumentException` is in the jev breaker's `ignore-exceptions`, and the retry allow-list already leaves it out. `JevApiClientImpl.judge` rejects any question that is not `Noul` or `Score` ("Unsupported question type for '<name>'") before `client.systemOne(...)`.
- The mirror test now also compares the two aspect-order keys, which sit outside `instances.jev` (research Pitfall 8).
- CLAUDE.md's Resilience4j convention describes the property-driven order in a commit that carries only this plan's line. The Raindrop todo is narrowed, and its leftover doc items point at Phase 7 OPS-03.

## Task Commits

1. **Task 1 (tracer): the breaker wraps the retry (D-07)** - `5e69d6c` (fix)
2. **Task 2: timeout, slow-call, auto half-open, caller-error isolation** - `c904a71` (test, RED) + `cf607fd` (feat, GREEN)
3. **Task 3: CLAUDE.md convention + narrowed todo** - `40d4120` (docs, CLAUDE.md only) + `9b4b766` (docs, todo only)

The tracer feedback gate re-ran Task 1's automated verify after its commit. It passed, so the plan expanded to Tasks 2 and 3 (interactive, `end-of-phase`, automated-only verify).

## Files Created/Modified
- `src/main/resources/application.yaml` - Aspect orders, 30s timeout, 15s slow-call, auto transition, IAE ignore (with D-05/D-07/D-17 comments)
- `src/test/resources/application.yaml` - Exact mirror of every changed key
- `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java` - D-15 question-type check before the HTTP call; the answer-check loop is collapsed to noul/score
- `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` - Rewrote the breaker-count test. Added `retriedCallThatSucceedsRecordsOneSuccess`, `breakerAspectWrapsRetryAspect`, `callerInputErrorsAreNeitherSentNorRecorded` and `openBreakerMovesToHalfOpenWithoutACall`. Extended the bind test and the mirror filter.
- `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java` - Timeout expectation 5s to 30s
- `CLAUDE.md` - Resilience4j Key Conventions bullet (one line, in place)
- `.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` - Covers only the Raindrop tuning now

## Raindrop side effects of the global aspect order (research Pitfall 9)
The aspect order is global, so Raindrop's `@CircuitBreaker(name = "raindrop", fallbackMethod = ...)` + `@Retry(name = "raindrop")` also nests the breaker outside the retry now:
- Raindrop still makes up to **3 HTTP attempts per call**. The retry now retries the raw `RestClient` exception instead of the fallback's `IllegalStateException`.
- The breaker now records **one outcome per call**, so it opens after 5 failed saves (min 5 calls) instead of about 2.
- When the breaker is open, the call **returns the fallback immediately**. Before, the fallback's rejection was retried 3 times with 1s waits.
- `RaindropNotConfiguredException` behaves the same as before: both instances ignore it and the fallback rethrows it.
- `RaindropApiClientImplTest` (5 tests) and `RaindropServiceTest` (3 tests) pass unchanged. They build the client without the AOP proxy.

## UAT note: worst-case call time with a 30s timeout (research Pitfall 10)
One logical `judge()` call can take about **93s**: 3 attempts × 30s read timeout, plus 1s + 2s backoff, plus a 5s connect timeout per attempt if connects hang. The preview shares this timeout (D-05), so **the Interests dialog preview spinner can run that long**. Check during UAT whether the spinner needs a ceiling. With the breaker outer, a call that uses up all its timeouts counts as one failure, so 10 such articles open the breaker. The 15s slow-call threshold covers the whole logical call, including retry waits.

## CLAUDE.md hunk comparison
`claude-md-user-hunks.01.before.diff` and `claude-md-user-hunks.01.after.diff` (under `$HOME/.cache/myfeeder-phase04/`) are identical apart from `index` lines. The user's uncommitted hunks at working-tree lines 40, 55-56, 99 and 105 are unchanged and still uncommitted. Commit `40d4120` contains only line 137, staged via `git hash-object -w` of `CLAUDE.01.staged.md` (HEAD plus this line) and `git update-index --cacheinfo`. `.envrc`, `.claude/CLAUDE.md` and `.planning/config.json` have the same checksums as before the plan and were never staged.

## 02-REVIEW closure
- **WR-01** (caller input errors recorded as breaker failures): closed by the D-14 ignore entry and `callerInputErrorsAreNeitherSentNorRecorded`.
- **WR-02** (Choice questions billed and then dropped): closed by the D-15 type check before the HTTP call.
- **WR-03** (breaker thresholds count retry attempts): closed by the D-07 aspect orders and the rewritten breaker test.
- **WR-04** (Raindrop `createBookmark` POST retried) and **WR-05** (`helm --set` secret mangling) stay deferred.

## TDD Gate Compliance (Task 2)
- RED `c904a71`: four target tests failed on assertions for the planned behavior. `mainYamlJevInstancesBindAsSpecified` expected 15S but got 3S. `mainYamlPinsModelTimeoutAndDisablesSdkRetries` expected 30S but got 5S. `openBreakerMovesToHalfOpenWithoutACall` expected HALF_OPEN but got OPEN. `callerInputErrorsAreNeitherSentNorRecorded` got `TypeSafeMissingAnswerException` instead of IAE, because the Choice question was billed. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` for each (records in `$HOME/.cache/myfeeder-phase04/red-evidence.04-01.*.json`; Gradle JUnit XML was rendered as TAP for the checker).
- GREEN `cf607fd`: all named tests pass, and JevApiClientImplTest stays green (18 tests).
- REFACTOR: none needed.

## Decisions Made
- The aspect-order keys are compared by exact property name in `jevProperties(...)`. A prefix filter would also have pulled in the Raindrop instances.
- The unreachable `response.answer(name)` branch is removed, as the plan directed. After the D-15 check every question is Noul or Score.

## Deviations from Plan

None. The plan was executed as written. Task 2's test files went into a separate RED commit before the GREEN commit, which the TDD discipline requires. The plan's "commit the five files" is therefore split across `c904a71` and `cf607fd`.

## Issues Encountered
- The repo's pre-commit hook (TruffleHog via pre-commit) stashes unstaged files into its own patch cache and restores them around each commit. After every commit, the user's files were checked: `.envrc`, `.claude/CLAUDE.md` and `.planning/config.json` had the same checksums as before, and the CLAUDE.md hunk snapshot was unchanged.
- `REQUIREMENTS.md` was not changed. `requirements.ready-ids` reported 0/2 ready, because SCOR-05 and SCOR-07 are also declared by sibling plans in this phase that have no SUMMARY yet.

## Verification
- Plan verification: `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.*' --tests 'org.bartram.myfeeder.config.*'` passed.
- Full backend suite: `./gradlew test -x npmBuild -x npmInstall` ran 311 tests with 0 failures and 0 errors. The 2 skips are the pre-existing live-key tests (`InterestCalibrationSpikeTest`, `JevLiveSmokeTest`).

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
Wave 2 plans (scorer, sweep, status counts) can rely on these semantics: one breaker outcome per article, a breaker that recovers on its own, and caller errors that never poison the breaker.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED

All 7 modified files exist; commits 5e69d6c, c904a71, cf607fd, 40d4120 and 9b4b766 are present on sbartram/main.
