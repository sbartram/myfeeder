---
phase: 08-engagement-capture
plan: 01
subsystem: api
tags: [postgres, flyway, spring-data-jdbc, jdbcclient, engagement, testcontainers]

# Dependency graph
requires:
  - phase: 06 (v0.2.1 interest ranking, archived)
    provides: "V6 schema (article_feedback), ArticleFeedbackStore JdbcClient store pattern, the by-id Article payload with feedback"
provides:
  - "V7__engagement.sql: article_engagement (PK article_id+kind, CHECK on four kinds, FK cascade) and topic_suggestion_dismissal (Phase 11)"
  - "EngagementKind enum OPEN_ORIGINAL, STAR, BOARD, RAINDROP (declaration order = API/display order)"
  - "ArticleEngagementStore: record (idempotent), recordQuietly (never throws, class-name-only WARN), kinds, deleteAll"
  - "PUT /api/articles/{id}/engagement/open (bodyless, 204, 404 unknown, 400 non-numeric)"
  - "DELETE /api/articles/{id}/engagement (204, 404 unknown; star/board/vote untouched, no tombstone)"
  - "GET /api/articles/{id} carries engagement: string[] ([] when none); list items omit it"
affects: [08-02, 08-03, 08-04, 08-05, phase-09-engagement-ranking, phase-11-topic-suggestions]

# Actuals (#2632)
actuals:
  tokens: 10340
  tasks: 2
  commits: 3
plan_head_before: 52c830c62a2258f855deb6e0646b92bc72d1b7c4
plan_head_after: d86e5d5c61b0046f95a82582d8d6fe1300a89959

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Engagement store with no transaction boundary: each statement autocommits so a failed engagement insert cannot abort the caller's save (D-07)"
    - "Best-effort capture lives in the store (recordQuietly), so save-path services stay one-liners"
    - "Bodyless PUT/DELETE engagement routes delegate to ArticleService, keeping ArticleController's constructor unchanged"

key-files:
  created:
    - src/main/resources/db/migration/V7__engagement.sql
    - src/main/java/org/bartram/myfeeder/model/EngagementKind.java
    - src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java
    - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/model/Article.java
    - src/main/java/org/bartram/myfeeder/service/ArticleService.java
    - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
    - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java

key-decisions:
  - "recordOpen uses the throwing record, not recordQuietly: a database failure surfaces as a 5xx (ignored by the fire-and-forget client) instead of a 204 that would claim a row was stored"
  - "No extra index on article_engagement: the PK (article_id, kind) serves by-article lookups, Forget, the cascade and Phase 9 grouping"
  - "V7 stays editable until the v0.3.0 release applies it to prod; 08-05 Task 2 gates that one-way door"

patterns-established:
  - "Engagement kinds are returned in enum declaration order (sorted in Java, not SQL)"
  - "Forget deletes engagement rows only; it is not an undo of the save and leaves no tombstone"

requirements-completed: [CAPT-01, CAPT-06]

coverage:
  - id: D1
    description: "V7 migration creates article_engagement and topic_suggestion_dismissal with the decided PK, CHECKs and FK cascades, and nothing else"
    requirement: CAPT-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java (Flyway applies V7 at context load)"
        status: pass
      - kind: other
        ref: "grep checks: PRIMARY KEY (article_id, kind), both CHECK lists, 2x ON DELETE CASCADE in V7__engagement.sql"
        status: pass
    human_judgment: false
  - id: D2
    description: "PUT /api/articles/{id}/engagement/open stores one OPEN_ORIGINAL row idempotently (204), 404 for unknown id, 400 for non-numeric id, never calls Jev"
    requirement: CAPT-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#openReturns204AndStoresOneOpenOriginalRow, repeatedOpenKeepsOneRowAndItsFirstCreatedAt, openOnAnUnknownArticleIs404AndStoresNothing, openWithANonNumericIdIs400"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#recordOpenStoresOpenOriginal, recordOpenOnAMissingArticleThrowsNotFoundAndStoresNothing"
        status: pass
    human_judgment: false
  - id: D3
    description: "GET /api/articles/{id} carries engagement ([] when none, kinds in enum order); list items carry no engagement key"
    requirement: CAPT-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#byIdCarriesEngagement, listItemsCarryNoEngagement"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#findByIdWithBreakdownCarriesEngagement, findByIdWithBreakdownWithoutEngagementHasAnEmptyList"
        status: pass
    human_judgment: false
  - id: D4
    description: "DELETE /api/articles/{id}/engagement removes every engagement row (204), 204 without engagement, 404 unknown, leaves star/board/vote intact, no tombstone"
    requirement: CAPT-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#forgetDeletesEveryKindAndReturns204, forgetLeavesStarBoardAndVoteAlone, openAfterForgetRecordsAgain, forgetWithoutEngagementIs204, forgetOnAnUnknownArticleIs404"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#forgetEngagementDeletesTheRows, forgetEngagementOnAMissingArticleThrowsNotFound"
        status: pass
    human_judgment: false
  - id: D5
    description: "ArticleEngagementStore contract: idempotent record, enum-order kinds, delete count, recordQuietly swallows an FK failure and WARNs with the simple class name only"
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java (5 tests)"
        status: pass
    human_judgment: false

# Metrics
duration: 6min
completed: 2026-09-30
status: complete
---

# Phase 8 Plan 01: Engagement Capture Server Slice Summary

**V7 `article_engagement` + `topic_suggestion_dismissal` schema, an idempotent JdbcClient `ArticleEngagementStore` with no transaction boundary, a bodyless `PUT /api/articles/{id}/engagement/open`, a `DELETE /api/articles/{id}/engagement` Forget route, and an appended `engagement` array on the by-id article. All of it is proven against Testcontainers Postgres, and none of it calls Jev.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-30T02:01:10Z
- **Completed:** 2026-09-30T02:07:20Z
- **Tasks:** 2
- **Files modified:** 9 (5 created, 4 modified)

## Accomplishments

- V7 migration with both v0.3.0 tables, exactly as D-13/D-14 decided: `article_engagement` has PK `(article_id, kind)`, a CHECK on the four kinds and an FK cascade. `topic_suggestion_dismissal` has `article_id` as PK with an FK cascade, a `reason` CHECK and no topic column. There is no backfill and no extra index.
- `ArticleEngagementStore` provides these methods:
  - `record`: `INSERT ... ON CONFLICT (article_id, kind) DO NOTHING`.
  - `recordQuietly`: never throws; logs `Engagement <KIND> not recorded for article <id>: <SimpleName>` at WARN.
  - `kinds`: returns the kinds in enum order.
  - `deleteAll`: returns the row count.
- CAPT-01 server side: a bodyless, idempotent open route (204), 404 for an unknown id, and 400 for a non-numeric id. It only ever writes `OPEN_ORIGINAL`.
- CAPT-06 server side: the by-id `engagement` field (D-05) and Forget, which deletes engagement rows only. Star, board membership and the thumbs vote stay, and no tombstone is kept.
- The full backend suite is green: 560 tests, 0 failures, 2 skipped (the pre-existing gated Jev live and spike tests). That includes `InterestCalibrationReplaySqlTest` and `DevProfileConfigTest`.

## Task Commits

1. **Task 1 (tracer): open stored once and shown on the by-id article**: `57afeb6` (feat)
2. **Task 2 (TDD): Forget plus the store contract**
   - RED: `1f709f2` (test). 4 Forget tests failed with `Status expected:<204> but was:<404>`. `check tdd-red-evidence` returned RED_EVIDENCE_OK.
   - GREEN: `d86e5d5` (feat). 11 integration, 5 store, 22 service and 30 controller tests pass.
   - REFACTOR: none needed.

## Files Created/Modified

- `src/main/resources/db/migration/V7__engagement.sql`: both v0.3.0 tables
- `src/main/java/org/bartram/myfeeder/model/EngagementKind.java`: enum OPEN_ORIGINAL, STAR, BOARD, RAINDROP
- `src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java`: JdbcClient store, no transactions
- `src/main/java/org/bartram/myfeeder/model/Article.java`: appended `@Transient @JsonInclude(NON_NULL) List<EngagementKind> engagement`
- `src/main/java/org/bartram/myfeeder/service/ArticleService.java`: store field, by-id `setEngagement`, `recordOpen` and `forgetEngagement`
- `src/main/java/org/bartram/myfeeder/controller/ArticleController.java`: the two 204 routes (constructor unchanged)
- `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`: `@Mock ArticleEngagementStore` (Pitfall 6) plus 6 tests
- `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java`: 11 end-to-end tests, with an `@AfterEach` that checks Jev is never called
- `src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java`: 5 store-contract tests

## Decisions Made

- These follow the plan's recorded interpretations: `recordOpen` uses the throwing `record`, there is no extra index, and V7 stays editable until the v0.3.0 prod deploy (08-05 Task 2).
- The RED evidence targets the test class (`EngagementApiIntegrationTest`), because the checker matches Surefire failures by class and JUnit names carry a `()` suffix.

## Deviations from Plan

None. The plan executed as written.

TDD notes (not deviations):
- In the RED run, `forgetOnAnUnknownArticleIs404` passed before any implementation existed. With no DELETE mapping, Spring's static-resource fallback returns 404 anyway. The other four Forget tests failed on their status assertions, which gave a valid RED. After GREEN, the 404 comes from `NotFoundException` in `forgetEngagement`, and the unit test `forgetEngagementOnAMissingArticleThrowsNotFound` pins it.
- `ArticleEngagementStoreTest` also passed in RED, because Task 1 had already built the store. That test pins the existing store's contract; the Forget behavior was the RED target.
- The two `ArticleServiceTest` Forget tests went into the GREEN commit. They call `forgetEngagement`, and the test source set would not compile before that method existed, which would have made the RED invalid.

## Issues Encountered

- The worktree sandbox refuses shell writes under `.git/`. So the #3968 commit-ledger base was kept in the session scratchpad, not in `.git/gsd-plan-head-before-08-01`. `plan_head_before` is the dispatch base `52c830c`, and the commit count was measured with `git rev-list --count 52c830c..HEAD` = 3.

## TDD Gate Compliance

- RED `test(08-01)`: 1f709f2
- GREEN `feat(08-01)`: d86e5d5, which comes after RED
- REFACTOR: not needed

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 08-03 (save-path capture) can call `engagementStore.recordQuietly(id, STAR|BOARD|RAINDROP)` from `ArticleService.updateState`, `BoardService.addArticle` and `RaindropService.saveToRaindrop`. `BoardServiceTest` and `RaindropServiceTest` will need `@Mock ArticleEngagementStore` (Pitfall 6).
- 08-02 and 08-04 (frontend) can rely on `PUT /api/articles/{id}/engagement/open` returning 204, `DELETE /api/articles/{id}/engagement` returning 204, and `Article.engagement` on the by-id response.
- Nothing in `InterestScoreQueries` reads the new tables, so the ranking is untouched.

---
*Phase: 08-engagement-capture*
*Completed: 2026-09-30*

## Self-Check: PASSED

- All 5 created files exist on disk; all 4 modified files carry the changes (verified by the diff against 52c830c).
- Commits 57afeb6, 1f709f2 and d86e5d5 exist on the worktree branch.
- Plan verification: full `./gradlew test -x npmBuild -x npmInstall` is green (560 tests, 0 failures), and V6 is absent from the plan diff.
