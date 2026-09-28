---
phase: 05-blend-priority-view
plan: 01
subsystem: api
tags: [postgres, spring-data-jdbc, jdbcclient, keyset-pagination, interest-ranking]

requires:
  - phase: 03-interest-profile-topics
    provides: V6 interest schema (interest_topic, article_score, article_topic_score)
  - phase: 04-scoring-pipeline
    provides: stored Jev outputs (SCORED rows with profile_score/profile_max_level, per-topic noul)
provides:
  - InterestScoreQueries blend CTE (learned, eff, contrib, blended) and Priority keyset page queries
  - PriorityService.page(Long, int) with 404 on a missing cursor
  - GET /api/articles/priority returning PaginatedResponse<Article> {items, nextCursor}
  - Article.interestScore (@Transient Integer, null when unscored)
  - myfeeder.interest.blend.profile-points (MyfeederProperties.Interest.Blend.profilePoints = 100)
  - SPA forward for /priority
affects: [05-02, 05-03, 05-04, 05-05, 05-06, 05-07, phase-06-feedback]

actuals:
  tokens: 10622
  tasks: 3
  commits: 3
plan_head_before: 9903eadfe6d0023d7e267a0f9a63acee42cae15d

tech-stack:
  added: []
  patterns:
    - "Single blend CTE built by blendCte(scope) with compile-time scope constants; every request value is a named parameter"
    - "Keyset page over a keyed CTE, cursor row resolved in the same statement and not unread-scoped (R4)"
    - "Two statements (first page / after cursor) instead of a nullable cursor parameter"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/service/PriorityService.java
    - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
    - src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java
    - src/test/java/org/bartram/myfeeder/config/SpaForwardControllerTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/model/Article.java
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
    - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
    - src/main/java/org/bartram/myfeeder/config/SpaForwardController.java
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java

key-decisions:
  - "05-01: InterestScoreQueries is the single source of truth for Priority sort and badge; raw_n is numeric ROUND(..., 6), badge = CASE-guarded LEAST(100, GREATEST(0, ROUND(raw_n)))::int, sort key = COALESCE(raw_n::float8, '-Infinity')"
  - "05-01: A Priority cursor that names no article is a 404 (NotFoundException), not a 400, so the client can restart from page 1"
  - "05-01: No new index and no V7 migration; the page query uses idx_article_read"

patterns-established:
  - "InterestScoreQueries scope constants: 05-03 adds IDS_SCOPE, ARTICLE_SCOPE, TOTAL on the same blendCte"
  - "Priority tests seed article_score rows before article_topic_score rows (FK) and bind timestamps as java.sql.Timestamp"

requirements-completed: [PRIO-01, PRIO-02, PRIO-03, PRIO-06]

coverage:
  - id: D1
    description: "GET /api/articles/priority ranks unread scored articles by the blended score, then unscored ones by date, pages across the boundary with no duplicate, and 404s a missing cursor"
    requirement: PRIO-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#rankedWalkCrossesIntoUnscoredWithNoDuplicates"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#missingCursorIs404"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#fullOrderIsScoreThenDateThenId"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every unread article without a SCORED row (no row, FAILED, SKIPPED, outside the window, undated) sorts after all scored rows, by date then id"
    requirement: PRIO-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#fullOrderIsScoreThenDateThenId"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#readArticlesAreExcluded"
        status: pass
    human_judgment: false
  - id: D3
    description: "interestScore is null for unscored rows, clamped to 0..100 and rounded half away from zero for scored rows"
    requirement: PRIO-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#unscoredRowsHaveNullInterestScore"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#displayIsClampedAndRoundsHalfAwayFromZero"
        status: pass
    human_judgment: false
  - id: D4
    description: "Keyset paging in pages of 3 and 5 equals the unpaged order (tie group split across pages, scored/unscored boundary inside a page); cursor marked read between pages still continues; limit clamped to [1, 100]"
    requirement: PRIO-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#pagedWalksMatchTheUnpagedOrder"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#cursorReadBetweenPagesStillContinues"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#priorityClampsLimit"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#priorityTrimsLookAheadRowAndSetsNextCursor"
        status: pass
    human_judgment: false
  - id: D5
    description: "Weight changes reorder from stored outputs alone (Criterion 5); topic added after scoring contributes 0 and deleted topic cascades away (R5)"
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#weightChangeReordersWithoutRescoring"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#topicAddedAfterScoringContributesNothing"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#deletedTopicContributionCascadesAway"
        status: pass
    human_judgment: false
  - id: D6
    description: "Browser reload on /priority serves index.html; myfeeder.interest.blend.profile-points bound and mirrored in test yaml"
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/SpaForwardControllerTest.java#priorityReloadForwardsToIndex"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java#devOverlayResolvesEveryMainKeyToMainsValue"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-09-25
status: complete
---

# Phase 5 Plan 01: Blend CTE and Priority API Summary

**`GET /api/articles/priority` is served by a single Postgres blend CTE: numeric raw score = 100 x profile_score/max + sum of hinge(noul) x weight. Results are ordered by score, then date, then id, and paged with a keyset over a `-Infinity` float8 sort key. The badge is null-guarded, and a missing cursor returns 404.**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-25T19:32:08Z
- **Completed:** 2026-09-25T19:38:06Z
- **Tasks:** 3
- **Files modified:** 13 (6 created, 7 modified)

## Accomplishments

- `InterestScoreQueries` holds the single blend CTE (`learned` returns 0 until Phase 6, then `eff`, `contrib` and `blended`) and the two keyset page statements.
- `PriorityService` and `GET /api/articles/priority` return `{items, nextCursor}` with `limit` clamped to [1, 100]. A missing cursor is a 404.
- `Article.interestScore` (Spring Data `@Transient`) is always in the JSON: null when the article is unscored, otherwise 0..100.
- A fixed 5-topic, 15-article real-Postgres fixture proves the exact order, ties, paged walks, cursor-read continuation, rounding, clamping, weight changes and the R5 cases.
- A browser reload on `/priority` forwards to `index.html`. `myfeeder.interest.blend.profile-points: 100` is listed in both yaml files.

## Contract for later plans

**`InterestScoreQueries`** (`org.bartram.myfeeder.repository`, `@Repository`, constructor-injected `JdbcClient jdbc`, `MyfeederProperties properties`). Package-private `static final String` constants, where alias `a` = article, `b` = blended and `k` = keyed:
- `UNREAD_SCOPE` = `a."read" = false`
- `UNREAD_OR_CURSOR_SCOPE` = `a."read" = false OR a.id = :cursorId`
- `INTEREST_SCORE` = `CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END`
- `SORT_SCORE` = `COALESCE(b.raw_n::float8, '-Infinity'::float8)`
- `SORT_DATE` = `COALESCE(a.published_at, a.fetched_at)`
- `ARTICLE_COLUMNS` = `a.id, a.feed_id, a.guid, a.title, a.url, a.author, a.content, a.summary, a.image_url, a.published_at, a.fetched_at, a."read", a.starred`
- `KEYED_ORDER` = `ORDER BY k.sort_score DESC, k.sort_date DESC, k.id DESC`
- `private static String blendCte(String scope)`, `private static String keyed(String scope)`, `private static Article mapArticle(ResultSet)` (sets `interestScore` from the `interest_score` column), `private static Instant toInstant(Timestamp)`
- `public List<Article> priorityFirstPage(int limit)`
- `public List<Article> priorityPageAfter(long cursorId, int limit)`
- Bound parameters: `:profilePoints` (from `properties.getInterest().getBlend().getProfilePoints()`), `:limit`, `:cursorId`

**`PriorityService.page(Long cursor, int limit)`**: with a null cursor it calls `priorityFirstPage`. A cursor for which `!articleRepository.existsById(cursor)` throws `NotFoundException("Article not found: <id>")`. Otherwise it calls `priorityPageAfter`.

**HTTP**: `GET /api/articles/priority?limit=50[&before=<articleId>]` returns `{"items": [Article...], "nextCursor": <id>|null}`. Every item carries `interestScore` (int 0..100, or `null` when unscored). A `before` that names no article returns 404 (a ProblemDetail).

**InterestScoreQueriesTest fixture** (for 05-03, which adds tests to the same class). Topics are held in the fields `rust` (+20), `webAssembly` (+14), `politics` (-30), `zero` (0) and `gardening` (+10). The article fields are:
- `a1`, `a2`, `a3`: tied raw 82.2 (a1 and a3 published now-1h, a3 inserted after a1; a2 published now-2h)
- `a4`: 133.32, badge 100
- `a5`: -30, badge 0
- `a6`: 28.5, badge 29
- `a7`: 10 (NULL profile)
- `a8`: 75 (topic Zero only)
- `r1`: read, SCORED
- `u1`: no score row, now-30m
- `u2`: FAILED
- `u3`: SKIPPED
- `u4`: now-20d
- `u5`: undated, fetched now-3h
- `r2`: read, unscored

The full order is `[a4, a3, a1, a2, a8, a6, a7, a5, u1, u3, u5, u2, u4]`. Helpers: `seedFixture()`, `insertArticle`, `insertTopic(name, weight)`, `insertScored(id, Double, Integer)`, `insertStatus(id, status)`, `insertTopicScore(id, topicId, noul)`, `ids(...)`, `scores(...)`, `walk(n)`, `markRead(id)`.

## Task Commits

1. **Task 1 (tracer): GET /api/articles/priority end to end**: `57e0cb9` (feat)
2. **Task 2: SQL proven exact on a fixed fixture, plus service and controller rules**: `ac47c65` (test)
3. **Task 3: /priority SPA forward and the myfeeder.interest.blend yaml key**: `45e1877` (feat)

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java`: the blend CTE and the Priority page queries
- `src/main/java/org/bartram/myfeeder/service/PriorityService.java`: cursor existence check (404) and delegation
- `src/main/java/org/bartram/myfeeder/controller/ArticleController.java`: the `/priority` handler
- `src/main/java/org/bartram/myfeeder/model/Article.java`: `@Transient Integer interestScore`
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`: `Interest.Blend.profilePoints = 100`
- `src/main/java/org/bartram/myfeeder/config/SpaForwardController.java`: `/priority` forward
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml`: `myfeeder.interest.blend.profile-points: 100`
- Tests: `PriorityApiIntegrationTest`, `InterestScoreQueriesTest`, `PriorityServiceTest`, `SpaForwardControllerTest`, plus 5 new methods in `ArticleControllerTest`

## Decisions Made

- I followed the plan as specified. All SQL fragments are exactly the ones in RESEARCH Pattern 1 and Pattern 2.

## Deviations from Plan

None. The plan was executed exactly as written.

## TDD Gate Compliance

Task 2 carries `tdd="true"`, but the plan is `type: execute`, and Task 1 (the tracer) had already built the behavior by design. The plan tells Task 2 to "write the tests above first, then fix any defect they expose". So the new tests passed on their first run: an expected green, not a gate violation.

To make sure the tests are not vacuous, I temporarily changed `INTEREST_SCORE` to `LEAST(100, GREATEST(0, ROUND(b.raw_n::float8)))::int`, which drops the null guard and uses half-even float rounding. With that change, `unscoredRowsHaveNullInterestScore` and `displayIsClampedAndRoundsHalfAwayFromZero` failed (10 run, 2 failed). I then restored the file with `git checkout -- <file>`, so the change was never committed.

Commit sequence: `feat` (tracer), then `test` (proof), then `feat` (config). There is no separate RED commit ahead of the tracer's `feat`.

## Issues Encountered

None.

## Verification

- Full backend suite (`./gradlew test -x npmBuild -x npmInstall`): 416 tests, 0 failures, 0 errors, 2 skipped. The skips are the pre-existing gated `JevLiveSmokeTest` and `InterestCalibrationSpikeTest`.
- Acceptance greps all pass:
  - `learned AS` appears exactly once.
  - `CASE WHEN b.raw_n IS NULL THEN NULL`, `'-Infinity'::float8` and `::numeric, 6)` are present.
  - There is no `a.*` select, no DML and no import from the integration package.
  - There is no V7 migration, and `application-dev.yaml` is untouched.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 05-02 (frontend Priority list) can call `GET /api/articles/priority` with the contract above.
- 05-03 extends `InterestScoreQueries` with `IDS_SCOPE`, `ARTICLE_SCOPE` and `TOTAL` on the same `blendCte`, and adds tests to `InterestScoreQueriesTest`, reusing the fixture.

## Self-Check: PASSED

- All 6 created files exist on disk.
- Commits `57e0cb9`, `ac47c65` and `45e1877` are present in `git log`.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*
