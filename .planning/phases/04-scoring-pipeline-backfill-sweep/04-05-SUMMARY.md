---
phase: 04-scoring-pipeline-backfill-sweep
plan: 05
subsystem: scoring
tags: [scheduling, resilience4j, circuit-breaker, backfill, spring-scheduled, mockito]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-01 automatic OPEN->HALF_OPEN on the jev breaker (D-17)"
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-02 ArticleScoreStore.findNeedingScoring and MyfeederProperties.Interest"
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-04 ScoringQueue.remainingCapacity() and submit(List<Long>)"
provides:
  - "InterestScoringSweep.sweep(): the only drain loop (backfill, outage recovery, late key, first scoring after the profile, Re-score drain)"
  - "myfeeder.interest.* YAML block in main (sweep-initial-delay PT1M) and test (PT1H)"
  - "Context proof that the sweep is a FixedDelayTask at PT2M after the configured initial delay"
affects: [04-verification, 05-priority-view]

actuals:
  tokens: 3557
  tasks: 2
  commits: 3
plan_head_before: 00e9dd5728bdc439d46a6bca6a512bdd9e39517c

tech-stack:
  added: []
  patterns:
    - "Scheduled job on the shared scheduler thread only selects and enqueues; the whole body sits in try/catch(RuntimeException) and logs the exception class name only"
    - "Gate order: configured, breaker not OPEN/FORCED_OPEN, not cold start, free queue room; each gate returns before any query"
    - "Schedule assertions via ScheduledTaskHolder beans, matching ScheduledMethodRunnable (or the wrapped task's toString)"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
    - src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java
  modified:
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java
    - src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java

key-decisions:
  - "The @Scheduled placeholders have no inline defaults; both YAMLs define the keys, and the context test proves they bind"
  - "The schedule test collects tasks from every ScheduledTaskHolder bean, so it does not depend on a single holder being autowirable"

patterns-established:
  - "Background jobs that touch Jev read the breaker state through CircuitBreakerRegistry and never pre-check it in the scorer (Pitfall 5)"

requirements-completed: [SCOR-04, SCOR-05, SCOR-06]

coverage:
  - id: D1
    description: "The sweep skips when Jev is unconfigured, in cold start, or while the jev breaker is OPEN or FORCED_OPEN; it runs in HALF_OPEN"
    requirement: SCOR-06
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#skipsWhenJevIsNotConfigured"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#skipsInColdStart"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#skipsWhileTheBreakerIsOpenOrForcedOpen"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#runsWhenTheBreakerIsHalfOpen"
        status: pass
    human_judgment: false
  - id: D2
    description: "Each run enqueues min(free room, 50) eligible ids newest first within the 14-day window; a full queue runs no query"
    requirement: SCOR-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#enqueuesAtMostTheBatchCapNewestFirst"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#enqueuesAtMostTheFreeRoom"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#fullQueueRunsNoQuery"
        status: pass
    human_judgment: false
  - id: D3
    description: "A sweep failure is contained and never reaches the shared scheduler thread"
    requirement: SCOR-05
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java#failuresAreContained"
        status: pass
    human_judgment: false
  - id: D4
    description: "The running context schedules exactly one InterestScoringSweep as a FixedDelayTask with a 2-minute interval and the test YAML's 1-hour initial delay"
    requirement: SCOR-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java#sweepIsScheduledWithConfiguredDelays"
        status: pass
    human_judgment: false
  - id: D5
    description: "Live scoring end to end with a real TypeSafe key: backfill drains eligibleUnscored, new articles score without waiting for the sweep, Re-score resets and re-drains, polling unaffected"
    requirement: SCOR-05
    verification: []
    human_judgment: true
    rationale: "Needs a live TypeSafe key and billed Jev calls; human_verify_mode is end-of-phase, so this is queued for the phase UAT and was not run by the executor"

duration: 5min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 05: Scoring Sweep Summary

**`InterestScoringSweep` runs every 2 minutes (after a 1-minute initial delay, 1 hour in tests). Each run selects up to min(free queue room, 50) eligible unscored article ids, newest first within the 14-day window, and hands them to `ScoringQueue`. It skips while Jev is unconfigured, in cold start, or while the jev breaker is OPEN or FORCED_OPEN, runs in HALF_OPEN, never calls Jev on the scheduler thread, and contains its own failures.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-09-24T02:14:40Z
- **Completed:** 2026-09-24T02:19:48Z
- **Tasks:** 2
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- One sweep job covers the launch backfill, outage recovery, a key added later, first scoring after the profile is written and the Re-score drain (SCOR-05). No separate kick is needed.
- The gates follow D-10 and SCOR-06: configured, breaker not OPEN or FORCED_OPEN, not cold start, free queue room. Each gate returns before any SQL runs. HALF_OPEN runs, so with 04-01's automatic transition, scoring resumes by itself about 60 s after an outage opens the breaker.
- The batch cap follows D-16: `min(remainingCapacity(), sweep-batch-size = 50)`. It uses `eligibilityCutoff()`, the same predicate as the enqueue filter, the counts and Re-score (SCOR-04).
- Both YAMLs now carry `myfeeder.interest` (window-days 14, concurrency 1, queue-capacity 1000, sweep-batch-size 50, sweep-delay PT2M, sweep-initial-delay PT1M in main and PT1H in test). `spring.task.*` is untouched.

## Task Commits

1. **Task 1 (tracer): sweep drains eligible unscored articles into the queue.** `a78fa81` (feat). Tracer gate: interactive, end-of-phase, automated-only verify. I re-ran the verify and it passed, so expansion went ahead with no checkpoint.
2. **Task 2: context proves the schedule.** `6dc5274` (test)
3. **Deviation fix: race in an existing 04-04 test.** `26354e6` (fix)

**Plan metadata:** committed with this SUMMARY (docs)

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java`: the drain loop, its gates and its batch cap
- `src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java`: 8 Mockito tests with a real `CircuitBreakerRegistry.ofDefaults()`
- `src/main/resources/application.yaml`: `myfeeder.interest` defaults (sweep-initial-delay PT1M)
- `src/test/resources/application.yaml`: the same block, with sweep-initial-delay PT1H (research Pitfall 12)
- `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java`: `sweepIsScheduledWithConfiguredDelays`
- `src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java`: race fix (see Deviations)

## Decisions Made
- The `@Scheduled` placeholders have no inline `:PT2M` defaults (the plan's action text). The YAML is the single source, and the context test fails if the keys stop binding.
- The schedule test flattens tasks from every `ScheduledTaskHolder` bean instead of autowiring one, which avoids ambiguity if more than one holder exists. It matches `ScheduledMethodRunnable#getMethod()`, and falls back to the task's `toString()` if the runnable is wrapped.
- To show the sweep tests are not vacuous, I temporarily removed the breaker gate and the cold-start gate and replaced the batch cap with `room`. Four of the eight tests failed. I restored the file from a scratch backup; the mutation was never committed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Race in `ScoringQueueTest.remainingCapacityTracksTheQueue` (from 04-04)**
- **Found during:** Task 2 (full backend suite)
- **Issue:** `blockWorkerOn(1L)` stubs the scorer, but the test could end before the `jev-score` worker invoked that stub. Under full-suite load, Mockito's strict stubs then threw `UnnecessaryStubbingException`. It failed in 2 of 2 full-suite runs and passed in 5 of 5 isolated runs. The capacity assertion itself was always right: with core size 1, id 1 is the worker's first task and never sits in the queue.
- **Fix:** Added `verify(scorer, timeout(5000)).score(1L)` after `submit`, before the capacity assertion. Test-only; no production change.
- **Files modified:** src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java
- **Verification:** Two consecutive full `cleanTest test` runs were green.
- **Committed in:** 26354e6

---

**Total deviations:** 1 auto-fixed (1 blocking). **Impact on plan:** a test-only fix to a sibling plan's flaky test, needed to meet this plan's green-suite verification. No scope creep.

## Verification

- Task 1: `InterestScoringSweepTest` ran 8 tests with 0 failures, and all eight named tests are in the report. Acceptance greps: `myfeeder.interest.sweep-delay` 1; non-comment `judge(` 0; `sweep-batch-size: 50` 1 in each YAML; `sweep-initial-delay: PT1H` (test) 1; `sweep-initial-delay: PT1M` (main) 1; `pool.size|virtual` in main YAML 0.
- Task 2: the `MyfeederApplicationTests` report contains `contextLoads` and `sweepIsScheduledWithConfiguredDelays`, with no failures. `grep -c sweepIsScheduledWithConfiguredDelays` prints 1.
- Full backend suite (`DOCKER_HOST=... ./gradlew cleanTest test -x npmBuild -x npmInstall`): 384 tests, 0 failures, 0 errors, 2 skipped (the skips predate this phase). Green twice in a row after the race fix.
- Frontend: `npx tsc -b` exited 0, and vitest ran 17 files and 118 tests, all passing.

## Queued for End-of-Phase UAT (human_verify_mode: end-of-phase)

The live-key check was **not run** by the executor. It needs a real TypeSafe key and billed calls. With `MYFEEDER_TYPESAFE_API_KEY` set, `./gradlew bootTestRun`, and a profile or at least one topic saved:
1. Within about 3 minutes of startup, `GET /api/interest/status` shows `eligibleUnscored` falling toward 0, and `article_score` rows appear with status SCORED, model `jev-1.13.0` and a request id.
2. A feed refresh that brings new articles produces rows for them without waiting for the sweep.
3. Interests → Re-score unread shows a count. Confirming resets it, and the "N waiting to be scored" line drains back to 0.
4. Poll logs show no new feed errors while scoring runs.

## Issues Encountered
None beyond the flaky test above.

## User Setup Required
None for this plan's automated scope. The UAT check above needs a live `MYFEEDER_TYPESAFE_API_KEY`.

## Next Phase Readiness
- All eight phase-04 plans now have summaries. Phase verification and the live-key UAT come next.
- `requirements.ready-ids` reported SCOR-04, SCOR-05 and SCOR-06 as ready (3/3), and all three are now marked complete.
- Edge assumptions carried from the plan: overlapping sweeps cannot double-score (in-flight set plus write-once SQL). In HALF_OPEN, up to 50 ids are enqueued but only 3 probe calls are admitted, and the rest fail fast as transient. A restart loses the in-memory queue, and the first sweep re-selects everything from the database.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED

InterestScoringSweep.java and InterestScoringSweepTest.java exist; commits a78fa81, 6dc5274 and 26354e6 are present on sbartram/main.
