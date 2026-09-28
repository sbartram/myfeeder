---
phase: 04-scoring-pipeline-backfill-sweep
plan: 04
subsystem: scoring
tags: [spring-events, thread-pool, executor, resilience, polling, mockito, tdd]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-02 ArticleScoreStore.filterNeedingScoring and MyfeederProperties.Interest (concurrency, queueCapacity, eligibilityCutoff)"
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-03 ArticleScoringService.score(long)"
provides:
  - "ArticlesIngestedEvent(feedId, articleIds), published by FeedPollingService after the poll's success bookkeeping with only the inserted ids"
  - "InterestScoringListener: never-throwing AFTER_COMMIT/fallbackExecution hand-off gated on JevApiClient.isConfigured()"
  - "InterestScoringConfig.interestScoringExecutor: defaultCandidate = false, core == max == concurrency (1), queue 1000, prefix jev-score-, abort policy"
  - "ScoringQueue: submitIngested (shared-predicate filter, newest first), submit (in-flight dedup, TaskRejectedException release), remainingCapacity, isInFlight"
affects: [04-05, 04-07]

actuals:
  tokens: 9350
  tasks: 3
  commits: 4
plan_head_before: 84461bda9ec213d85ac89ae39899dd6c5efe2f8a

tech-stack:
  added: []
  patterns:
    - "Named executor bean with defaultCandidate = false, injected through an explicit constructor carrying @Qualifier (no lombok.config)"
    - "Poll-thread hand-off: publish after bookkeeping in its own try; listener only enqueues and catches RuntimeException"
    - "In-flight id set released in finally and on TaskRejectedException, so dropped work is always re-selectable by the sweep"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java
    - src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
    - src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
    - src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java
    - src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java
    - src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/service/FeedPollingService.java
    - src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java
    - src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java

key-decisions:
  - "The listener gates only on isConfigured(); cold start stays in the scorer and sweep so the polling thread never runs the cold-start queries"
  - "Success criterion 2's slow Jev is simulated at the scorer boundary (scorer blocked on a latch for up to 30 s) with the real executor, queue, listener and polling service"
  - "Task 3's tests are characterization tests of the Task 1 ScoringQueue; they passed first run, so a mutation check (removing both in-flight releases) was used to prove they bite (4 of 5 fail)"

patterns-established:
  - "Scoring hand-off logs carry counts, ids and exception simple class names only"

requirements-completed: [SCOR-01, SCOR-02, SCOR-04]

coverage:
  - id: D1
    description: "Ingested ids are filtered through the shared predicate (14-day cutoff) and scored newest first on jev-score- threads via the real bounded executor"
    requirement: SCOR-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java#ingestedEventReachesTheScorerOnTheScoringThread"
        status: pass
    human_judgment: false
  - id: D2
    description: "FeedPollingService publishes one ArticlesIngestedEvent with only the inserted ids after the feed save; nothing when nothing is new or on 304; a failing publisher is not a poll error"
    requirement: SCOR-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java#publishesOneEventWithOnlyTheNewIds"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java#publishesNothingWhenNothingIsNew"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java#publisherFailureIsNotAPollError"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java#shouldSaveOnlyNewArticles"
        status: pass
    human_judgment: false
  - id: D3
    description: "Success criterion 2: poll finishes in under 5 s with errorCount 0 while the scorer blocks up to 30 s, throws, is unconfigured, or the enqueue SQL throws"
    requirement: SCOR-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java#pollFinishesWhileScoringIsBlocked"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java#scorerFailureNeverReachesTheFeed"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java#unconfiguredJevEnqueuesNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java#enqueueFailureNeverFailsThePoll"
        status: pass
    human_judgment: false
  - id: D4
    description: "Queue overflow drops safely (id released, one WARN), duplicates ignored, scorer exceptions contained, remainingCapacity correct for pool and inline executors"
    requirement: SCOR-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java#rejectedArticleIsReleasedForTheSweep"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java#duplicateSubmitWhileQueuedIsIgnored"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java#remainingCapacityTracksTheQueue"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java#scorerExceptionIsContainedAndReleasesTheId"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java#inlineExecutorRunsImmediatelyAndReportsUnboundedRoom"
        status: pass
    human_judgment: false
  - id: D5
    description: "Boot's applicationTaskExecutor survives; interestScoringExecutor is core 1 / max 1 / capacity 1000 / jev-score- in the full context"
    requirement: SCOR-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java#contextLoads"
        status: pass
    human_judgment: false

duration: 7min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 04: Scoring Hand-off Summary

**Each poll now publishes the ids of the articles it inserted, after its success bookkeeping. A listener that never throws filters those ids through the shared eligibility predicate and hands them, newest first, to a dedicated 1-thread `jev-score-` executor with a 1000-slot queue. That executor is registered with `defaultCandidate = false`, so Boot keeps its `applicationTaskExecutor`. A full queue, a failing or slow scorer, an unconfigured Jev, a failing enqueue query or a failing publisher can never slow a poll or change `errorCount`.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-09-24T01:55:22Z
- **Completed:** 2026-09-24T02:02:06Z
- **Tasks:** 3
- **Files modified:** 9 (6 created, 3 modified)

## Accomplishments
- The event, listener, executor config and queue carry an ingested article from the poll to the scorer on a `jev-score-` thread (SCOR-01 hand-off).
- `FeedPollingService` publishes only the inserted ids, after `feedRepository.save(feed)`, inside its own try. `pollFeed` still has no transaction.
- Success criterion 2 (SCOR-02) is proven with the real executor. With the scorer blocked for up to 30 s, `pollFeed` returns in under 5 s, and `errorCount` stays 0 when the scorer throws, when Jev is unconfigured, when the enqueue SQL throws and when publishing throws.
- The enqueue-time filter reuses `ArticleScoreStore.filterNeedingScoring` (SCOR-04), and ids are submitted in its newest-first order.

## Contract for 04-05 (exact signatures)

`org.bartram.myfeeder.service.ScoringQueue` (`@Component`, explicit constructor `ScoringQueue(@Qualifier(InterestScoringConfig.EXECUTOR) TaskExecutor, ArticleScoringService, ArticleScoreStore, MyfeederProperties)`)
- `public void submitIngested(List<Long> ids)`: empty list does nothing; otherwise `submit(store.filterNeedingScoring(ids, cutoff))`
- `public int submit(List<Long> ids)`: submits in the given order and returns how many were newly accepted. Ids already queued or running are skipped. A rejected id is released from the in-flight set and counted in a single WARN.
- `public int remainingCapacity()`: `queueCapacity - queueSize` for a `ThreadPoolTaskExecutor`, `Integer.MAX_VALUE` otherwise
- package-private `boolean isInFlight(long id)`

`org.bartram.myfeeder.config.InterestScoringConfig`
- `public static final String EXECUTOR = "interestScoringExecutor"`
- `@Bean(name = EXECUTOR, defaultCandidate = false) public ThreadPoolTaskExecutor interestScoringExecutor(MyfeederProperties)`

`org.bartram.myfeeder.event.ArticlesIngestedEvent(Long feedId, List<Long> articleIds)`

`org.bartram.myfeeder.service.InterestScoringListener.onArticlesIngested(ArticlesIngestedEvent)` (`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`)

ScoringQueue fixes forced by Task 3's tests: **none**. All five passed against the Task 1 implementation unchanged.

## Task Commits

1. **Task 1 (tracer): ingested ids reach the scorer on the jev-score thread.** `ba188d1` (feat). I re-ran the tracer gate's automated verify end to end, it passed, and expansion went ahead.
2. **Task 2: poll publishes only inserted ids; scoring cannot slow or fail a poll.** RED `b0ccfa6` (test), GREEN `3087291` (feat)
3. **Task 3: queue overflow, dedup, Boot executor survival.** `434e859` (test)

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java`: event record
- `src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java`: the scoring executor bean
- `src/main/java/org/bartram/myfeeder/service/ScoringQueue.java`: in-flight dedup, rejection release, capacity
- `src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java`: after-commit hand-off that never throws
- `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java`: collects the inserted ids, adds `ApplicationEventPublisher` and `publishIngested`
- `src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java`: 5 tests covering success criterion 2 with the real executor
- `src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java`: 5 queue tests against real 1-thread pools
- `src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java`: publisher mock, id-assigning `save` stub, 3 new tests
- `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java`: checks that `applicationTaskExecutor` still exists and pins the scoring executor's shape

## Decisions Made
- I followed the planner's recorded decisions: the listener gates only on `isConfigured()`, and the slow-Jev case is simulated at the scorer boundary.
- `ScoringIsolationTest` also asserts that the blocked scorer really started on a `jev-score-` thread, and the failure tests wait for the scorer to run before checking the feed. Without that, the isolation tests would pass vacuously if nothing were ever published.

## Deviations from Plan

### TDD mechanics (no production-scope change)

**1. Task 2's RED commit adds the `ApplicationEventPublisher` field to FeedPollingService**
- **Found during:** Task 2 RED
- **Issue:** `ScoringIsolationTest` builds `FeedPollingService` with the new 5-argument constructor. Without the field, the RED run fails to compile, which counts as INVALID_RED under tdd.md (#3770).
- **Fix:** The `test(04-04)` commit adds only the field. It is unused until GREEN. This is the same signature-stub approach 04-02 used.
- **Verification:** `gsd-tools check tdd-red-evidence` returned RED_EVIDENCE_OK (6 of 11 failed on assertions; target `publishesOneEventWithOnlyTheNewIds`).
- **Committed in:** b0ccfa6

**2. Task 3 has no GREEN commit: its tests passed on the first run**
- **Found during:** Task 3
- **Issue:** Task 3 is marked `tdd="true"`, but the plan says its tests pin behavior Task 1 already built. They were green immediately, which tdd.md calls an unexpected GREEN.
- **Investigation:** The behavior already existed by design (from the Task 1 tracer), so the tests are characterization tests. To show they are not vacuous, I temporarily removed both `inFlight.remove(id)` calls in ScoringQueue. Four of the five ScoringQueueTest methods then failed. I reverted the file with `git checkout -- ScoringQueue.java`, and it was never committed.
- **Committed in:** 434e859 (test only)

---

**Total deviations:** 2 (both TDD mechanics). **Impact on plan:** none on the contract or production scope.

## TDD Gate Compliance

| Task | RED | GREEN | REFACTOR | Evidence |
|------|-----|-------|----------|----------|
| 2 | b0ccfa6 | 3087291 | none needed | RED_EVIDENCE_OK (6/11 failed on assertions, target publishesOneEventWithOnlyTheNewIds) |
| 3 | 434e859 | n/a (characterization) | none | Unexpected GREEN, by design; mutation check fails 4/5 |

## Verification

- Task 1 verify: ScoringIsolationTest ran green, and the report parse passed. All acceptance greps pass: `defaultCandidate = false` 1, `setMaxPoolSize` 1, `setCorePoolSize` 1, `DiscardPolicy` 0, qualifier 1, `public ScoringQueue(` 1, `fallbackExecution = true` 1, no `@EnableAsync`.
- Task 2 verify: FeedPollingServiceTest (6), ScoringIsolationTest (5) and FeedPollingSchedulerTest (8) all pass, and all eleven named tests are present. `publishEvent(new ArticlesIngestedEvent` appears 1 time and `@Transactional` 0 times. `publishIngested(` (line 65) comes after `feedRepository.save(feed);` (line 62).
- Task 3 verify: ScoringQueueTest (5) and MyfeederApplicationTests.contextLoads pass. The `applicationTaskExecutor` and `jev-score-` greps each print 1.
- Full backend suite `DOCKER_HOST=... ./gradlew test -x npmBuild -x npmInstall`: 375 tests, 0 failures, 0 errors, 2 skipped (the skips predate this plan).

## Issues Encountered
None

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- 04-05 (sweep) can use `ScoringQueue.remainingCapacity()` and `submit(...)` as-is. The `myfeeder.interest.*` YAML block is still 04-05's job. The executor's defaults currently come from `MyfeederProperties.Interest`.
- Requirements: `requirements.ready-ids` reported SCOR-01 and SCOR-02 ready, and both are now marked complete. SCOR-04 is still blocked by a sibling plan that also declares it and has not run yet (04-05).
- Not covered (as the plan's edge assumptions say): a database so slow that the enqueue filter query itself delays the poll.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED

All 6 created files exist; commits ba188d1, b0ccfa6, 3087291, 434e859 and 847f253 are present.
