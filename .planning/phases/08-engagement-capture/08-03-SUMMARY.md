---
phase: 08-engagement-capture
plan: 03
subsystem: api
tags: [spring-data-jdbc, engagement, testcontainers, mockito, postgres, raindrop, boards]

# Dependency graph
requires:
  - phase: 08-engagement-capture (plan 01)
    provides: "V7 article_engagement / topic_suggestion_dismissal, EngagementKind, ArticleEngagementStore.recordQuietly/kinds, the by-id engagement field, EngagementApiIntegrationTest harness"
provides:
  - "STAR captured in ArticleService.updateState on an unstarred-to-starred change only (CAPT-02)"
  - "BOARD captured in BoardService.addArticle after the save-or-already-present branch, so re-adds credit and a thrown add records nothing (CAPT-03, D-09 as amended)"
  - "RAINDROP captured in RaindropService right after createBookmark returns; validation failures, client failures and an open breaker record nothing (CAPT-04)"
  - "Proofs: stickiness across unstar/board removal/board delete, feed-delete cascade (204), unchanged badge and breakdown with Jev uncalled (CAPT-05, CAPT-07, SC-5)"
  - "V7EngagementMigrationTest (16 tests) pinning the V7 shape, CHECKs, PKs, cascades, CREATE-TABLE-only file, keyword-safe names and the ranking-SQL guard"
  - "CLAUDE.md documents V7 and the engagement capture rules"
affects: [08-04, 08-05, phase-09-engagement-ranking, phase-11-topic-suggestions]

# Actuals (#2632)
actuals:
  tokens: 12190
  tasks: 3
  commits: 5
plan_head_before: 2220bb57afc4ff26e595714ff793d89c7804124b
plan_head_after: 1863b4022639f1b6b089cd4d50b6061722847fac

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Save-path capture is one line after the user's write: engagementStore.recordQuietly(id, KIND), with no transaction boundary on the owning service"
    - "Static source guard in a test (rankingSqlDoesNotReadTheV7TablesYet) that a later phase must deliberately remove or invert"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/service/ArticleService.java
    - src/main/java/org/bartram/myfeeder/service/BoardService.java
    - src/main/java/org/bartram/myfeeder/integration/RaindropService.java
    - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
    - src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java
    - src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
    - CLAUDE.md

key-decisions:
  - "BOARD is recorded after the save-or-already-present branch (D-09 as amended 2026-09-29, user-approved): re-adds credit, a thrown board_article save records nothing"
  - "RAINDROP capture lives in RaindropService, not RaindropApiClientImpl, so it sits outside the breaker/retry bean and only runs once createBookmark has returned"
  - "No transaction boundary added to ArticleService, BoardService or RaindropService (D-07); the pre-existing lost-update race on updateState stays out of scope"

patterns-established:
  - "One capture point per save kind, in the service that owns the save, so every client entry point (star button and s; board picker, Read Later and b; Raindrop button and v) is covered without client changes"

requirements-completed: [CAPT-02, CAPT-03, CAPT-04, CAPT-05, CAPT-07]

coverage:
  - id: D1
    description: "Starring an unstarred article over HTTP stores one STAR row after the save; unstar, re-star, starring a starred article, a read-only PATCH and bulk mark-read store nothing new"
    requirement: CAPT-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#starringAnUnstarredArticleRecordsStarAfterTheSave, starringAnAlreadyStarredArticleRecordsNothing, unstarringRecordsNothing, aReadOnlyUpdateRecordsNothing, updateStateOnAMissingArticleRecordsNothing, bulkMarkReadRecordsNothing"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#starringRecordsStarAndShowsOnTheArticle, unstarKeepsStarAndRestarKeepsOneRow, readOnlyPatchRecordsNothing, starringAStarredArticleRecordsNothing, bulkMarkReadRecordsNothing"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every board add records one BOARD row per article across boards; a re-add (including a pre-V7 board membership) credits; a failed save records nothing; remove and board delete touch no engagement"
    requirement: CAPT-03
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java#addingRecordsBoardAfterTheSave, reAddingAnArticleAlreadyOnTheBoardStillRecordsBoard, aFailedSaveRecordsNothing, removingAndDeletingTouchNoEngagement"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#boardAddRecordsOneBoardRowAcrossTwoBoards, reAddingAnArticleAlreadyOnABoardEarnsBoard"
        status: pass
    human_judgment: false
  - id: D3
    description: "RAINDROP is recorded only after createBookmark returns; not configured, disabled, no collection, a client failure/open breaker and RaindropNotConfiguredException record nothing; repeated saves capture quietly each time"
    requirement: CAPT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java#shouldDelegateToClientWhenConfigured, shouldThrowWhenConfigMissing, shouldThrowWhenConfigDisabled, shouldThrowWhenNoCollectionSelected, aFailedOrBlockedBookmarkRecordsNothing, aRaindropNotConfiguredFailureRecordsNothing, aRepeatedSaveCapturesEachTimeQuietly"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java#recordIsIdempotent (one row per kind)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Engagement is sticky across unstar, board removal and board delete; DELETE /api/feeds/{id} returns 204 and cascades the engagement of that feed's articles"
    requirement: CAPT-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#engagementIsStickyAcrossUnstarBoardRemovalAndBoardDelete, deletingTheFeedRemovesEngagementAndSucceeds"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java#deletingABoardOrItsArticleKeepsEngagement, deletingTheFeedCascadesEngagement, deletingTheArticleCascadesEngagementAndDismissal"
        status: pass
    human_judgment: false
  - id: D5
    description: "Open, star and board add leave the badge and the whole interestBreakdown unchanged, the topic weight unchanged and Jev uncalled; the ranking SQL and the replay SQL mention neither V7 table"
    requirement: CAPT-07
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#engagementLeavesTheRankingUnchanged (plus the @AfterEach Jev-never-called check on every test)"
        status: pass
      - kind: other
        ref: "src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java#rankingSqlDoesNotReadTheV7TablesYet"
        status: pass
    human_judgment: false
  - id: D6
    description: "V7 shape pinned: exact column lists, kind/reason CHECKs, PKs, FK for dismissals, topic delete keeps a dismissal, CREATE TABLE only (no backfill, no index), names pass the replay write-keyword pattern"
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java (16 tests)"
        status: pass
    human_judgment: false
  - id: D7
    description: "CLAUDE.md lists V7, drops the V6-only migration clause, and documents the capture rules, the two routes and the new classes/hooks"
    verification:
      - kind: other
        ref: "grep checks: V7__engagement.sql, ArticleEngagementStore, EngagementKind, engagement/open, useOpenOriginal, useForgetEngagement present; 'later milestone phases add no migrations' absent"
        status: pass
    human_judgment: false

# Metrics
duration: 9min
completed: 2026-09-30
status: complete
---

# Phase 8 Plan 03: Save-Path Engagement Capture Summary

**STAR, BOARD and RAINDROP are now recorded server-side, each by a single best-effort `recordQuietly` line placed after the user's write in the service that owns that save. STAR is recorded only when an article goes from unstarred to starred. BOARD is recorded on every board add, re-adds included. RAINDROP is recorded only after the bookmark was created. Tests show that engagement is sticky, that deleting a feed cascades it, and that the ranking does not change. The V7 schema is pinned by a 16-test migration test.**

## Performance

- **Duration:** about 9 min
- **Started:** 2026-09-30T02:47:15Z
- **Completed:** 2026-09-30T02:55:50Z
- **Tasks:** 3
- **Files modified:** 9 (1 created, 8 modified)

## Accomplishments

- **CAPT-02:** `ArticleService.updateState` computes `newlyStarred = Boolean.TRUE.equals(starred) && !article.isStarred()` before it mutates the article. It records STAR after `articleRepository.save`. A read-only PATCH (auto-mark-read), an unstar, a re-star and bulk mark-read record nothing. `markRead` is unchanged.
- **CAPT-03:** `BoardService.addArticle` saves the `board_article` row only when the article is not already on the board. After that branch it always records BOARD. Read Later, the board picker and `b` all reach this one method. A save that throws (an unknown board id fails the FK, for example) propagates and records nothing.
- **CAPT-04:** `RaindropService.saveToRaindrop` records RAINDROP on the line right after `createBookmark` and before `log.info`. Validation throws before that line, and the client's fallback always throws, so a failed or blocked save never reaches it. `RaindropApiClient` and `RaindropApiClientImpl` are unchanged.
- **CAPT-05 and CAPT-07:** integration tests show two things. First, open, star and board add, followed by unstar, board removal and board delete, still leave `[OPEN_ORIGINAL, STAR, BOARD]`. Second, `DELETE /api/feeds/{id}` returns 204 and cascades the engagement rows.
- **SC-5:** open, star and board add leave `interestScore`, the whole `interestBreakdown` and the topic weight unchanged, and Jev is never called. A static guard shows that `InterestScoreQueries.java` and `scripts/interest-calibration-replay.sql` never mention either V7 table. Its comment names Phase 9 (LRN-01, CAL-01) as the place that removes or inverts the guard.
- Full backend suite is green: 599 tests, 0 failures, 2 skipped (the pre-existing gated Jev live and spike tests). This includes `DevProfileConfigTest` (4) and `InterestCalibrationReplaySqlTest` (12).

## Task Commits

1. **Task 1 (tracer): STAR capture.** `a6c956c` (feat). Tracer gate: interactive run with `end-of-phase` mode and an automated-only verify. After the commit the verify was re-run and passed (BUILD SUCCESSFUL), so expansion continued.
2. **Task 2 (TDD): BOARD and RAINDROP capture**
   - RED: `400816d` (test). 6 targeted failures: 2 integration `AssertionFailedError`s, 2 `WantedButNotInvoked` and 2 `VerificationInOrderFailure`. `check tdd-red-evidence` returned RED_EVIDENCE_OK for BoardServiceTest, RaindropServiceTest and EngagementApiIntegrationTest.
   - GREEN: `bc0b59b` (feat). BoardServiceTest 10/10, RaindropServiceTest 8/8, EngagementApiIntegrationTest 18/18.
   - REFACTOR: none needed.
3. **Task 3: proofs, V7 pin and docs**
   - `7d30646` (test): 3 integration tests and V7EngagementMigrationTest (16).
   - `1863b40` (docs): CLAUDE.md.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/service/ArticleService.java`: STAR capture on an unstarred-to-starred change
- `src/main/java/org/bartram/myfeeder/service/BoardService.java`: new `engagementStore` field; `addArticle` restructured so BOARD is recorded after the save-or-exists branch
- `src/main/java/org/bartram/myfeeder/integration/RaindropService.java`: new `engagementStore` field; RAINDROP recorded after `createBookmark`
- `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`: 6 new STAR tests
- `src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java`: `@Mock ArticleEngagementStore` and 4 new tests
- `src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java`: `@Mock ArticleEngagementStore`; `articleAt` now sets id 42. The delegation test is extended with an InOrder check, the three validation tests check that the store is never touched, and there are 3 new tests
- `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java`: 10 new tests (5 STAR, 2 board, 3 proofs), the `patchState`, `createBoard`, `addToBoard` and scoring helpers, and topic cleanup in `@BeforeEach`
- `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java`: 16 schema and static-guard tests
- `CLAUDE.md`: Flyway list, Interest Ranking schema bullet, Package Structure, the Key Behaviors "Engagement capture (v0.3.0)" bullet, and the Frontend hooks list

## Decisions Made

- I followed the plan's recorded interpretations: BOARD is recorded after the save-or-exists branch (D-09 as amended and user-approved), RAINDROP capture sits in the service and not in the breaker bean, and no transaction boundary was added anywhere.
- `namesPassTheReplayWriteKeywordCheck` checks 14 names: the 4 kinds, the 2 reasons, the 2 table names and 6 column names read from `information_schema`. It also asserts that count, so a renamed or added column cannot slip through.

## Deviations from Plan

None. The plan executed as written.

Minor note: 08-01 wrote a Javadoc on the `insertEngagement` test helper saying STAR/BOARD capture "only arrives with the save paths (08-03)". I reworded it to "bypassing the save paths that capture STAR and BOARD", because this plan ships those save paths.

## TDD Gate Compliance

- Task 2: RED `400816d` (`test(08-03)`) comes before GREEN `bc0b59b` (`feat(08-03)`), and the RED evidence is RED_EVIDENCE_OK for all three target classes. No REFACTOR was needed.
- Task 3 (`tdd="true"`) is a proof task. Its tests describe behavior that was already delivered by 08-01 (the V7 schema, cascades, Forget) and by Tasks 1 and 2 (capture). Every test passed on its first run, so no GREEN commit follows `7d30646`. That is the expected outcome for characterization tests, not a missing feature. To show the tests are not vacuous, I temporarily disabled the STAR capture line in `ArticleService`. Five of the new integration tests then failed, including the stickiness, feed-cascade and ranking proofs. The file was then restored from the commit.

## Issues Encountered

- The worktree sandbox refuses compound shell commands that it cannot prove stay inside the worktree. Verification greps therefore ran as small scripts in the session scratchpad. The #3968 commit-ledger base is the dispatch base `2220bb5`, and `git rev-list --count 2220bb5..HEAD` measured 5 commits before this SUMMARY.
- REQUIREMENTS.md was not touched. The orchestrator owns shared tracking writes after the wave.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 08-04 (frontend) can rely on these server behaviors. After a board add or a Raindrop save, the by-id `engagement` now includes BOARD or RAINDROP. After a star it includes STAR (`useUpdateArticleState` already invalidates `['article', id]`).
- 08-05 (release gate) can treat V7 as fully pinned by `V7EngagementMigrationTest`.
- Phase 9 (LRN-01, CAL-01) must remove or invert `V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` when engagement enters the learned CTE and the replay.

---
*Phase: 08-engagement-capture*
*Completed: 2026-09-30*
