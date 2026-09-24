---
phase: 04-scoring-pipeline-backfill-sweep
verified: 2026-09-24T02:40:00Z
status: human_needed
score: 87/88 must-haves verified (5/5 roadmap success criteria; 82/83 plan truths; 1 backstop truth abstained as insufficient_spec)
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-01-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-01-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-02-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-02-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-03-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-03-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-04-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-04-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-05-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-05-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-06-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-06-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-07-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-07-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-08-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-08-SUMMARY.md
  - CLAUDE.md
  - src/main/frontend/src/App.css
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java
  - src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java
  - src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
  - src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java
  - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
  - src/main/java/org/bartram/myfeeder/service/FeedPollingService.java
  - src/main/java/org/bartram/myfeeder/service/InterestRescoreService.java
  - src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/RescoreCount.java
  - src/main/java/org/bartram/myfeeder/service/ScoringFailure.java
  - src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
  - src/main/resources/application.yaml
  - src/test/resources/application.yaml
covered_digest: "v1:sha256:06fef466e183c90294fa9ff312de7555fac353de7c205d46e35359c42cf0d700"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Live-key end-to-end (plan 04-05 human-check). Set MYFEEDER_TYPESAFE_API_KEY, run ./gradlew bootTestRun, and save a profile or at least one topic. (1) Within about 3 minutes of startup, GET /api/interest/status shows eligibleUnscored falling toward 0, and article_score rows appear with status SCORED, model jev-1.13.0 and a request id. (2) A feed refresh that brings new articles produces rows for them without waiting for the sweep. (3) Interests -> Re-score unread shows a count, confirming resets it, and the 'N waiting to be scored' line drains back to 0. (4) Poll logs show no new feed errors while scoring runs."
    expected: "Backlog drains through the sweep; new arrivals are scored via the ArticlesIngestedEvent hand-off; Re-score resets and re-drains; feeds keep errorCount 0"
    why_human: "Needs the live TypeSafe API (billed, never called by the verifier) and a running app. The Spring event multicast from pollFeed to InterestScoringListener is only exercised with a hand-wired publisher in ScoringIsolationTest, not inside a running context"
  - test: "Footer layout and theme (plan 04-07). Open Interests in each of the 6 themes. Check that the note and the 'Re-score unread' button share one row, and that the inline confirmation, the 'Counting articles…' line, the 'Re-scoring started' line, the 'N waiting to be scored' line and the 409 error text are readable, with the disabled-button tooltips showing."
    expected: "The row does not overlap or clip; text contrast works in all 6 themes; the confirmation looks like the existing close-guard row"
    why_human: "Visual layout and theme contrast are not asserted by any test. The CSS uses theme variables only, but appearance needs a human look"
  - test: "Flagged prohibition (04-07, test-tier, partially enforced): 'MUST NOT send a reset ... when the dialog holds unsaved edits'. Open Interests with no unsaved edits and click 'Re-score unread'. While the confirmation is showing, type into the profile textarea or a topic field, then click 'Re-score'."
    expected: "Decide whether this is acceptable. Currently the POST is sent: RescoreFooter hides the 'Re-score unread' button while confirming, and RescoreConfirm never re-checks `dirty`. So edits typed after the confirmation opens do not block the reset, and the reset re-judges against the saved rubric, not the edited one"
    why_human: "D-04's literal wording (the button is disabled while dirty) is met and tested by rescoreDisabledWhileEditsAreUnsaved. The broader prohibition is not enforced for edits made after the confirmation opens. A human must decide whether to accept this or close it (for example unmount the confirmation or disable 'Re-score' when dirty becomes true)"
  - test: "Backstop truth (04-02, verification: backstop): two concurrent writes for the same article cannot create two rows or an FK error, because ON CONFLICT on the article_score primary key serializes them"
    expected: "Accept on Postgres ON CONFLICT semantics plus the single-thread executor and in-flight set, or request a concurrent-writer test"
    why_human: "Non-inferable (backstop) truth. No concurrent-writer test exists; only sequential idempotency is tested (scoredIsWriteOnce, skippedIsTerminalAndFirstReasonWins). Presence and wiring never qualify under the backstop rule"
---

# Phase 4: Scoring Pipeline & Backfill Sweep Verification Report

**Phase Goal:** Every eligible article, including the existing unread backlog, is judged once by Jev in the background, and feed polling is never slowed or failed by it.
**Verified:** 2026-09-24T02:40:00Z
**Status:** human_needed
**Re-verification:** No. This is the initial verification.

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Shortly after a poll brings in new articles, each eligible one (unread, within 14 days, has a GUID) gets exactly one stored judgment: profile score and confidence, a noul per topic, the model id and the profile/topic versions. The newest articles are scored first. | ✓ VERIFIED | The ingest chain works. `FeedPollingService.publishIngested` publishes `ArticlesIngestedEvent` with the inserted ids only, after the success bookkeeping (FeedPollingService.java:62-86). `InterestScoringListener` (`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`) calls `ScoringQueue.submitIngested`, which filters through `ArticleScoreStore.filterNeedingScoring` (newest first). The executor then calls `ArticleScoringService.score` on `jev-score-`. The scorer snapshots versions, calls `jevApiClient.judge` once, and `writeScored` inserts the parent row first and then the topic rows in one `@Transactional`. Tests: `ArticleScoringFlowTest.scoresAnEligibleArticleOnceAndStoresRawOutputs` (two `score()` calls give exactly one `judge()`, and the Postgres row has score 3.2/4/0.81, version 2, the model, the request id and per-topic nouls/versions); `ArticleScoreStoreTest.selectsEligibleUnscoredNewestFirst`, `excludesReadAndOutOfWindowArticles` (strict `>` at the cutoff); `ScoringIsolationTest.ingestedEventReachesTheScorerOnTheScoringThread`; `FeedPollingServiceTest` (publishes only the new ids). All pass. |
| 2 | When Jev is slow (30s), failing or unconfigured, poll duration and feed error counts do not change, and nothing is scored while the profile is empty and there are no topics. | ✓ VERIFIED | `ScoringIsolationTest` uses the real 1-thread executor, queue and listener: `pollFinishesWhileScoringIsBlocked` (scorer blocked 30s, poll returns in under 5s, errorCount 0, lastError null), `scorerFailureNeverReachesTheFeed`, `unconfiguredJevEnqueuesNothing`, `enqueueFailureNeverFailsThePoll`. The listener catches every `RuntimeException`, and `publishIngested` has its own try that sits after the errorCount reset. Cold start: `ArticleScoringFlowTest.coldStartJudgesNothing`, `ArticleScoringServiceTest.coldStartTouchesNothing`, `InterestScoringSweepTest.skipsInColdStart`. All pass. |
| 3 | The sweep scores the eligible unscored backlog on launch, after a late key, after an outage and after the profile is first written, without re-judging scored articles. Permanent failures stop at 3 attempts; transient failures and an open circuit use no attempt; GUID-less articles are skipped. | ✓ VERIFIED | `InterestScoringSweep.sweep()` is `@Scheduled(fixedDelayString=${myfeeder.interest.sweep-delay}, initialDelayString=...)`. It gates on isConfigured, breaker OPEN/FORCED_OPEN and isColdStart, then calls `findNeedingScoring(cutoff, min(room, 50))` and `scoringQueue.submit`. Late key and profile first written: the gates are re-evaluated on every run. Outage: D-17 `automatic-transition-from-open-to-half-open-enabled: true` in both YAML files (`JevResilienceTest.openBreakerMovesToHalfOpenWithoutACall`), and `runsWhenTheBreakerIsHalfOpen`. No re-judging: NEEDS_SCORING excludes SCORED, SKIPPED and exhausted FAILED, and SCORED is write-once in SQL (`ON CONFLICT ... WHERE article_score.status = 'FAILED'`, tested by `scoredIsWriteOnce`). Attempts: `failedAttemptsCountUpToExhaustion`, `permanentFailuresRecordAFixedTextAttempt`, `transientFailuresWriteNothing`. `ScoringFailure.isTransient` covers 429, 5xx, connection/timeout, CallNotPermitted, NotConfigured and 401/403. GUID: `blankGuidIsSkippedWithoutACall`. The context test `sweepIsScheduledWithConfiguredDelays` proves PT2M binds. All pass. |
| 4 | `GET /api/interest/status` reports configured, the breaker state, and the eligible-unscored and failed counts. | ✓ VERIFIED | `InterestStatus(boolean configured, String breakerState, boolean coldStart, long eligibleUnscored, long failed)`: the three existing components are unchanged and the counts are appended. `InterestStatusService.status()` reads both counts from one `store.counts(cutoff)` call (a single SQL statement with `COUNT(*) FILTER`). `InterestStatusController` serves `/status`. Tests: `InterestApiIntegrationTest.statusCountsEligibleUnscoredAndExhaustedFailures` (full stack, no key), `ArticleScoreStoreTest.countsSplitRetryingFromExhausted`, `countsIgnoreReadAndOutOfWindow`, `InterestStatusServiceTest`. All pass. |
| 5 | User can trigger "Re-score unread", sees how many articles will be re-judged before confirming, and the in-window unread articles are then re-scored by the sweep. | ✓ VERIFIED (visual check is a human item) | Backend: `GET/POST /api/interest/rescore` (`InterestRescoreController` calls `InterestRescoreService`). `countRescoreScope` and `deleteRescoreScope` share `RESCORE_SCOPE` (ELIGIBLE, SCORED or FAILED). The D-18 guard throws `IllegalStateException`, which maps to 409. `rescoreCountEqualsRowsDeleted` proves the count equals the rows deleted, and that `findNeedingScoring` then returns the reset articles, so the sweep re-drains them. `InterestRescoreApiIntegrationTest.rescoreCountIsServedAndKeylessResetIs409` passes. Frontend: `RescoreFooter`/`RescoreConfirm` in InterestsDialog.tsx (inline confirmation, server count, zero case, disabled reasons, waiting line). Vitest: `rescoreConfirmShowsTheServerCountAndPosts`, `rescoreDisabledWhileEditsAreUnsaved`, `rescoreDisabledWithReasonWhenNotConfiguredOrColdStart`, `waitingLineShowsEligibleUnscored`, `statusPollsOnlyWhileArticlesAreWaiting`. All 118 frontend tests pass, and `npx tsc -b` exits 0. |

**Score:** 5/5 roadmap truths verified. Plan must-haves: 82/83 verified. One `verification: backstop` truth (04-02 concurrency) abstains as insufficient_spec and goes to human review. No truth is present-but-behavior-unverified.

### Plan Must-Have Truths (summary by plan)

| Plan | Truths | Result | Notes |
|------|--------|--------|-------|
| 04-01 Jev hardening | 11 | 11 VERIFIED | Aspect orders are 1/2 in both YAML files (`breakerAspectWrapsRetryAspect`). 10 calls give 30 hits, 10 failures and OPEN, and the 11th call throws CallNotPermitted (`breakerOpensAtMinimumCallsAndShortCircuits`). 500,500,200 gives 0 failures and 1 success. Timeout 30s and slow-call 15s in both files. IllegalArgumentException is ignored and Choice is rejected with 0 hits (`callerInputErrorsAreNeitherSentNorRecorded`). `testYamlMirrorsMainJevInstances` passes. Raindrop tests pass. CLAUDE.md HEAD has the aspect-order convention. The todo is narrowed. (The SUMMARY swaps the Raindrop test counts: it says 5+3, but the actual counts are RaindropApiClientImplTest 3 and RaindropServiceTest 5. Info only.) |
| 04-02 ArticleScoreStore | 14 | 13 VERIFIED, 1 insufficient_spec | A single `ELIGIBLE` constant composes every query. Tiebreak is `id DESC`. The boundary is strict `>`. Missing-article writes are no-ops (`INSERT ... SELECT FROM article`). A deleted topic is left out. Defaults 14/1/1000/50/PT2M/PT1M. The concurrency backstop truth has no concurrent test (see human items). |
| 04-03 Scorer | 15 | 15 VERIFIED | `scorerHoldsNoTransactionAndNoBreakerHandle`. `isValid` rejects non-finite or out-of-range values. `writeErrorAfterBilledSuccessRecordsAFailedAttempt`. `missingLegendFallsBackToProfileMaxLevel`. The D-13 truncate keeps at least `max / 2` and never splits a surrogate pair (13 `ArticleStateBuilderTest` tests). |
| 04-04 Hand-off | 10 | 10 VERIFIED | `defaultCandidate = false`, and `applicationTaskExecutor` survives (`contextLoads`). A `TaskRejectedException` releases the in-flight id (`rejectedArticleIsReleasedForTheSweep`). `@Qualifier` is on an explicit constructor. |
| 04-05 Sweep | 10 | 10 VERIFIED | 8 `InterestScoringSweepTest` tests plus the schedule proof. Main YAML initial delay is PT1M and test YAML is PT1H. `spring.task.scheduling.pool.size` is not set, and virtual threads are not enabled. |
| 04-06 Status counts | 6 | 6 VERIFIED | No `lastError` field. The counts come from one statement. |
| 04-07 Dialog | 12 | 12 VERIFIED | The note is kept and the button sits beside it. The confirmation is inline. The zero case shows OK only. "Counting articles…" shows on every open (gcTime 0 plus `isFetching`). Disabled reasons and the waiting line work. Polling is conditional. `tsc -b` is clean. CLAUDE.md routes are updated. |
| 04-08 Re-score API | 5 | 5 VERIFIED | The GET is unguarded and the POST is guarded (409). The count uses the shared scope. |

### Prohibitions (all test-tier)

| Plan | Prohibition | Disposition |
|------|-------------|-------------|
| 04-01 / 04-07 | Don't stage the user's uncommitted CLAUDE.md hunks, .envrc, and similar files | Enforced. `git status` still shows CLAUDE.md, .envrc, .claude/CLAUDE.md and .planning/config.json modified and uncommitted. HEAD's CLAUDE.md carries only the plan lines. |
| 04-02 / 04-08 | No read/starred change and no article delete from scoring SQL or Re-score | Enforced. The store touches only article_score and article_topic_score. `rescoreLeavesSkippedReadAndOutOfWindowRows` asserts the article count and the read flag are unchanged. |
| 04-03 | No unbounded billed calls | Enforced. MAX_ATTEMPTS 3; the "write failed" path records an attempt. The transient-timeout loop is a documented D-19 consequence (review IN-04). |
| 04-03 / 04-04 | No message, body, text or key in logs or last_error | Enforced. `ScoringFailure.describe` gives the class name, HTTP status and request id only. Listener and queue logs carry counts, ids and class names. |
| 04-04 / 04-05 | No Jev call on the poll, scheduler or HTTP thread; no feed errorCount impact | Enforced. `ScoringIsolationTest` asserts `jev-score-` threads; the sweep only selects and submits. |
| 04-06 | Status components not renamed; no lastError | Enforced. |
| 04-08 | No delete when unconfigured or in cold start; no delete outside the counted scope | Enforced (tests for the 409 and the shared scope). |
| 04-07 | MUST NOT send a reset against an unfreshened count or while the dialog holds unsaved edits | **FLAGGED: partially enforced.** The fresh count is enforced. Unsaved edits made *after* the confirmation opens are not guarded: `RescoreConfirm` never sees `dirty`. Listed under human verification. |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `repository/ArticleScoreStore.java` | ✓ VERIFIED | ELIGIBLE/NEEDS_SCORING/RESCORE_SCOPE; upserts on `ON CONFLICT (article_id)`; used by the scorer, queue, sweep, status and rescore |
| `service/ArticleScoringService.java` | ✓ VERIFIED | Uses `InterestQuestions.forRubric` and `ArticleStateBuilder.build`; one `judge()` call; classification |
| `service/ScoringFailure.java` | ✓ VERIFIED | `isTransient` and `describe` |
| `service/ArticleStateBuilder.java` | ✓ VERIFIED | `i >= max / 2` loop bound |
| `event/ArticlesIngestedEvent.java` | ✓ VERIFIED | record(feedId, articleIds) |
| `service/InterestScoringListener.java` | ✓ VERIFIED | `fallbackExecution = true`; catches everything |
| `config/InterestScoringConfig.java` | ✓ VERIFIED | `defaultCandidate = false`; core = max = concurrency |
| `service/ScoringQueue.java` | ✓ VERIFIED | In-flight set, TaskRejectedException handling, `remainingCapacity` |
| `scheduler/InterestScoringSweep.java` | ✓ VERIFIED | `fixedDelayString`; gates; batch cap |
| `service/InterestStatus(.java/Service)` | ✓ VERIFIED | `long eligibleUnscored, long failed`; `store.counts(` |
| `service/InterestRescoreService.java`, `controller/InterestRescoreController.java`, `service/RescoreCount.java` | ✓ VERIFIED | GET and POST `/rescore`; D-18 guard |
| `integration/JevApiClientImpl.java` | ✓ VERIFIED | "Unsupported question type" check before the HTTP call |
| `application.yaml` (main + test) | ✓ VERIFIED | Aspect orders, 30s timeout, 15s slow-call, auto half-open, IAE ignored, `myfeeder.interest` block |
| Frontend `api/interest.ts`, `hooks/useInterest.ts`, `InterestsDialog.tsx`, `App.css` | ✓ VERIFIED | `getRescoreCount`/`rescore`, `useRescoreCount`/`useRescoreUnread`, `RescoreFooter`, `.interests-rescore`/`.interests-waiting` |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| FeedPollingService | InterestScoringListener | `publishEvent(new ArticlesIngestedEvent(...))` then `@TransactionalEventListener(fallbackExecution = true)` | ✓ WIRED (runtime multicast inside a live context is in the E2E human item) |
| InterestScoringListener | ScoringQueue | `submitIngested` then `filterNeedingScoring` | ✓ WIRED |
| ScoringQueue | interestScoringExecutor | `@Qualifier(InterestScoringConfig.EXECUTOR)` | ✓ WIRED (context loads) |
| ScoringQueue | ArticleScoringService | `executor.execute(() -> run(id))` then `scorer.score(id)` | ✓ WIRED |
| ArticleScoringService | JevApiClient / InterestService / ArticleScoreStore | `jevApiClient.judge`, `interestService.isColdStart()`, `store.loadCandidate/write*` | ✓ WIRED |
| InterestScoringSweep | ScoringQueue / ArticleScoreStore / YAML | `remainingCapacity`, `submit`, `findNeedingScoring`, `${myfeeder.interest.sweep-delay}` | ✓ WIRED |
| InterestStatusService | ArticleScoreStore | `store.counts(eligibilityCutoff())` | ✓ WIRED |
| InterestRescoreService | ArticleScoreStore / GlobalExceptionHandler | `countRescoreScope`/`deleteRescoreScope`; `IllegalStateException` to 409 | ✓ WIRED |
| InterestsDialog | useInterest hooks | `useRescoreCount()` / `useRescoreUnread()` | ✓ WIRED |
| useInterest | api/interest.ts, `/status` | `interestApi.getRescoreCount/rescore`; `invalidateQueries(['interest','status'])`; `refetchInterval` | ✓ WIRED |
| application.yaml | JevApiClientImpl | aspect orders | ✓ WIRED (`breakerAspectWrapsRetryAspect` loads the main YAML) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| `/api/interest/status` counts | eligibleUnscored, failed | `ArticleScoreStore.counts` SQL over article LEFT JOIN article_score | Yes (keyless integration test seeds rows and asserts deltas) | ✓ FLOWING |
| Re-score confirmation | count, windowDays | `GET /rescore` then `countRescoreScope` SQL | Yes | ✓ FLOWING |
| "N waiting to be scored" | status.eligibleUnscored | `useInterestStatus` then `/status` | Yes | ✓ FLOWING |
| article_score rows | Jev outputs | `JevJudgment` from the SDK response then `writeScored` | Yes (flow test against Postgres, with the Jev client mocked) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend suite (run once; live tests gated off; no key in env) | `./gradlew test` | 384 tests, 0 failures, 0 errors, 2 skipped (JevLiveSmokeTest and InterestCalibrationSpikeTest, env-gated). Fresh result files dated 2026-09-24T02:31–02:32Z | ✓ PASS |
| Phase test classes | results XML | ScoringIsolationTest 5/5, ArticleScoringFlowTest 3/3, ArticleScoringServiceTest 14/14, ArticleScoreStoreTest 18/18, InterestScoringSweepTest 8/8, JevResilienceTest 16/16, ScoringQueueTest 5/5, InterestApiIntegrationTest 8/8, InterestRescoreApiIntegrationTest 1/1, InterestRescoreServiceTest 5/5, InterestRescoreControllerTest 3/3, InterestStatusServiceTest 5/5, ArticleStateBuilderTest 13/13, FeedPollingServiceTest 6/6, MyfeederApplicationTests 2/2, Raindrop 3/3 + 5/5 | ✓ PASS |
| Frontend suite | `npx vitest run` | 17 files, 118 tests passed | ✓ PASS |
| Type-check | `npx tsc -b` | exit 0 | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh` scripts, and none exist under `scripts/*/tests/`.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| SCOR-01 | 04-03, 04-04 | One Jev call per new article; profile Score plus a Noul per topic; feed/title/summary input with content fallback | ✓ SATISFIED | SC1 evidence; `ArticleStateBuilder` content fallback |
| SCOR-02 | 04-04 | Scoring never blocks or fails polling | ✓ SATISFIED | SC2 evidence |
| SCOR-03 | 04-02, 04-03 | Raw outputs stored write-once in separate tables | ✓ SATISFIED | `writeScored`, `scoredIsWriteOnce`, flow test |
| SCOR-04 | 04-02, 04-04, 04-05 | Unread and within 14 days (configurable); newest first | ✓ SATISFIED | ELIGIBLE, NEWEST_FIRST, `window-days` |
| SCOR-05 | 04-01, 04-05 | Background sweep: backfill, outage, late key, first profile | ✓ SATISFIED | SC3 evidence |
| SCOR-06 | 04-03, 04-05 | Nothing scored in cold start | ✓ SATISFIED | Gates in the scorer, sweep and rescore |
| SCOR-07 | 04-01, 04-02, 04-03 | Permanent failures retried at most 3 times; transient failures use no attempt | ✓ SATISFIED | MAX_ATTEMPTS, ScoringFailure, tests |
| SCOR-08 | 04-02, 04-03 | GUID-less articles skipped | ✓ SATISFIED | `blankGuidIsSkippedWithoutACall`; guid is NOT NULL in the schema (premise correction noted in CONTEXT) |
| INT-05 | 04-02, 04-07, 04-08 | Re-score unread with count, then re-score | ✓ SATISFIED | SC5 evidence |
| JEV-05 | 04-02, 04-06, 04-07 | Status reports configured, breaker and counts | ✓ SATISFIED | SC4 evidence |

Every phase ID appears in at least one plan's `requirements`. No orphaned requirements: REQUIREMENTS.md maps exactly SCOR-01..08, INT-05 and JEV-05 to Phase 4.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all 41 phase-modified files) | - | TBD/FIXME/XXX | none found | - |
| InterestsDialog.tsx | 25 | `PROFILE_PLACEHOLDER` | ℹ️ Info | Textarea placeholder copy, not a stub |
| InterestsDialog.tsx | 201-243, 254-310 | Confirmation does not re-check `dirty` after it opens | ⚠️ Warning | Flagged prohibition (human item 3) |
| ArticleScoringService.java | 65-96 | Rubric snapshot before a long call; write-once after (review WR-01) | ⚠️ Warning | An article in flight during a Re-score can keep a score from the replaced rubric. Narrow race; `profile_version` records it |
| InterestRescoreController.java | 23-26 | Bodyless destructive POST can be sent as a CORS simple request (review WR-02) | ⚠️ Warning | Cross-site trigger could force re-billing on this no-auth app |
| useInterest.ts | 20 | Status poll runs while unconfigured, in cold start or with the breaker OPEN (review WR-03) | ⚠️ Warning | 15s polling that never drains while the dialog is open. The server cost is one COUNT query |
| MyfeederProperties.java / InterestScoringSweep.java | 43-44 / 41-42 | Dead Java defaults; placeholders have no fallback (review IN-01) | ℹ️ Info | Startup fails only if the YAML keys are removed |

None of these block a success criterion. The code review (04-REVIEW.md) found 0 critical issues; its 3 warnings and 9 info items are advisory and restated above.

### Human Verification Required

### 1. Live-key end-to-end (04-05)

**Test:** Set `MYFEEDER_TYPESAFE_API_KEY`, run `./gradlew bootTestRun`, and save a profile or topic. Watch `/api/interest/status` and `article_score`. Refresh a feed, run Re-score unread, and check the poll logs.
**Expected:** eligibleUnscored drains; SCORED rows carry jev-1.13.0 and a request id; new arrivals are scored without waiting for the sweep; Re-score resets and re-drains; no new feed errors.
**Why human:** Needs the live, billed API and a running app. This also confirms the in-context event multicast.

### 2. Footer layout and theme (04-07)

**Test:** Open Interests in all 6 themes. Exercise the Re-score button, the confirmation, the zero case, the waiting line, the disabled tooltips and the 409 error.
**Expected:** One-row footer and readable contrast everywhere.
**Why human:** Visual only.

### 3. Flagged prohibition: unsaved edits after the confirmation opens (04-07)

**Test:** Open the Re-score confirmation, then edit the profile or a topic without saving, then click Re-score.
**Expected:** Decide whether this should be blocked. Currently the POST is sent.
**Why human:** A partially enforced test-tier prohibition needs a human accept or fix decision.

### 4. Backstop concurrency truth (04-02)

**Test:** Decide whether Postgres `ON CONFLICT` on the PK, plus the 1-thread executor and in-flight set, is enough, or whether a concurrent-writer test is needed.
**Expected:** Accept, or add the test.
**Why human:** A non-inferable truth with no behavioral test.

### Gaps Summary

No blocking gaps. The phase goal is achieved in code:
- Ingest publishes the new ids to a never-throwing listener and a dedicated 1-thread bounded executor.
- The scorer makes one Jev call and writes once to article_score/article_topic_score.
- A 2-minute sweep drains the backlog behind the configured, cold-start and breaker gates.
- Status exposes the counts, and Re-score resets the shared scope for the sweep to re-drain.

All 5 roadmap success criteria are supported by passing tests: 384 backend and 118 frontend.

The status is `human_needed`, not `passed`, for four reasons:
1. The planned live-key E2E check is queued for UAT.
2. The planned footer visual and theme check is queued for UAT.
3. One test-tier prohibition (no reset with unsaved edits) is only partially enforced.
4. One backstop truth has no concurrent test.

The three review warnings (WR-01 Re-score race, WR-02 cross-site bodyless POST, WR-03 poll in states that cannot drain) are worth a `/gsd-quick` or `/gsd-code-review 04 --fix` pass. They are not goal blockers.

---

_Verified: 2026-09-24T02:40:00Z_
_Verifier: Claude (gsd-verifier)_
