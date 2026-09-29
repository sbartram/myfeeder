---
phase: 05-blend-priority-view
plan: 03
subsystem: api
tags: [postgres, jdbcclient, spring-data-jdbc, largest-remainder, interest-ranking, jackson]

requires:
  - phase: 05-blend-priority-view
    provides: "05-01 InterestScoreQueries blend CTE (blendCte, INTEREST_SCORE, contrib/blended), Article.interestScore, the InterestScoreQueriesTest fixture and PriorityApiIntegrationTest"
provides:
  - "InterestScoreQueries.displayScores(Collection<Long>): id-scoped badge map (read articles included, unscored ids absent, empty ids run no SQL)"
  - "InterestScoreQueries.breakdownInputs(long): Optional<BreakdownInputs> with nested records BreakdownInputs and TopicContribution; constants IDS_SCOPE, ARTICLE_SCOPE, TOTAL"
  - "service.ScoreBreakdowns: build, apportion (largest remainder), levelIndex"
  - "model.InterestBreakdown with nested Row (KIND_PROFILE, KIND_TOPIC, profile(...), topic(...)) and NonMatchingTopic"
  - "Article.interestBreakdown (@Transient, omitted from JSON when null)"
  - "ArticleService.findByIdWithBreakdown(Long), used only by GET /api/articles/{id}"
  - "interestScore enrichment on GET /api/articles pages, GET /api/boards/{id}/articles pages and PATCH /api/articles/{id}"
affects: [05-05, 05-07, phase-06-feedback]

actuals:
  tokens: 14838
  tasks: 3
  commits: 5
plan_head_before: fd9b4a0b31f6c82da99d204c992434689081b861

tech-stack:
  added: []
  patterns:
    - "Id-scoped enrichment: services fetch rows as before, then one displayScores(ids) query sets the transient badge without reordering"
    - "Breakdown total and badge come from SQL; Java only apportions integer rows (floor + largest remainder) to the SQL total"
    - "TDD RED against compile-only placeholders so RED fails on assertions, validated with check tdd-red-evidence"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
    - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
    - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/service/ArticleService.java
    - src/main/java/org/bartram/myfeeder/service/BoardService.java
    - src/main/java/org/bartram/myfeeder/model/Article.java
    - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
    - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
    - src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java

key-decisions:
  - "05-03: interestScore is on every article response (lists, boards, PATCH, GET by id, Priority), id-scoped so read articles keep their badge; enrichment never reorders a list"
  - "05-03: GET /api/articles/{id} alone carries interestBreakdown {raw, total, display, rows, nonMatching}; total is SQL ROUND(raw) unclamped, rows sum exactly to it via largest remainder; Raindrop keeps findById"
  - "05-03: Breakdown row order is |points| desc, then |exact| desc, profile first, topic name A-Z ignoring case, topic id; a weight-0 matched topic is a 0-point row; noul 0.5 (hinge 0) is nonMatching"

patterns-established:
  - "Any new article-returning path enriches through displayScores(ids) (one bounded query per page, empty pages skip it)"
  - "Breakdown JSON: Row discriminated by kind (PROFILE | TOPIC) with null fields omitted"

requirements-completed: [PRIO-03, PRIO-04]

coverage:
  - id: D1
    description: "GET /api/articles and GET /api/boards/{id}/articles pages and PATCH /api/articles/{id} carry interestScore from the blend CTE (read articles included, null when unscored) without changing order"
    requirement: PRIO-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#everyArticleResponseCarriesInterestScore"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#findFilteredSetsInterestScoreWithoutReordering"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#updateStateReturnsTheSavedArticleWithItsScore"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java#findArticlesSetsInterestScore"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#displayScoresAreIdScopedIncludingReadArticles"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#displayScoresOfNoIdsIsEmpty"
        status: pass
    human_judgment: false
  - id: D2
    description: "Pure largest-remainder apportionment: rows always sum exactly to the SQL total (mock, capped, floored, half ties, negatives), with deterministic ordering, non-matching list and profile level index"
    requirement: PRIO-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#rowsSumToTotalAcrossCases"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#uiMockRowsAddUpToTheBadge"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#cappedTotalGoesToTheLargestRemainder"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#negativeRemaindersApportionExactly"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#levelIndexScalesToTheRubric"
        status: pass
    human_judgment: false
  - id: D3
    description: "GET /api/articles/{id} returns interestBreakdown whose display equals interestScore and whose rows sum to total; unscored article has null score and no breakdown key; lists omit the breakdown"
    requirement: PRIO-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#articleByIdCarriesAnExactBreakdown"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#getArticleSerializesTheBreakdown"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#listItemsOmitTheBreakdown"
        status: pass
    human_judgment: false
  - id: D4
    description: "One number everywhere: breakdown display, displayScores badge and Priority interestScore agree for every scored fixture article; SQL total unclamped (133/100, -30/0, 29/29); R5 later topic not listed; FAILED/SKIPPED/no row give no breakdown"
    requirement: PRIO-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#oneNumberEverywhere"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#breakdownInputsMatchTheBadge"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#breakdownInputsForCappedAndFlooredArticles"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#topicAddedAfterScoringIsNotListed"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#breakdownInputsEmptyForUnscored"
        status: pass
    human_judgment: false

duration: 8min
completed: 2026-09-25
status: complete
---

# Phase 5 Plan 03: Badge Enrichment and Exact Breakdown Summary

**Every article response now carries `interestScore` from the 05-01 blend CTE. That covers list pages, board pages, PATCH, GET by id and Priority, and read articles keep their badge. `GET /api/articles/{id}` also returns an `interestBreakdown`. Its integer rows are apportioned by largest remainder so they sum exactly to the SQL `ROUND(raw)` total, and its `display` field equals the badge.**

## Performance

- **Duration:** about 8 min
- **Started:** 2026-09-25T19:49:26Z
- **Completed:** 2026-09-25T19:58:06Z
- **Tasks:** 3
- **Files modified:** 13 (3 created, 10 modified)

## Accomplishments

- `InterestScoreQueries.displayScores(ids)` uses `blendCte(IDS_SCOPE)`, so it is scoped to ids rather than to unread articles. `ArticleService.findFiltered` (all four paths), `ArticleService.updateState` and `BoardService.findArticles` (both paths) call it to set the badge, and they never reorder a list.
- `InterestScoreQueries.breakdownInputs(id)` runs two statements on `blendCte(ARTICLE_SCOPE)`:
  - a header with the numeric raw, the SQL total, the clamped badge and the profile inputs;
  - the judged topics, each with its effective weight, hinge and exact points to 6 decimals.
- `ScoreBreakdowns` is pure Java with no Spring and no I/O:
  - It floors each row and gives the remaining points to the largest remainders, ties by display order.
  - It builds the profile row, which is shown even at 0 points.
  - It builds a row for each matched topic, and routes topics with hinge 0 to `nonMatching`.
  - `levelIndex` scales the profile score to the 0..4 rubric.
- `GET /api/articles/{id}` goes through `ArticleService.findByIdWithBreakdown`. The Raindrop save path keeps `findById`, unchanged.

## interestBreakdown JSON contract (for plan 05-07)

Only `GET /api/articles/{id}` returns `interestBreakdown`. List, board, Priority and PATCH responses omit the key, and so does an unscored article.

- **`raw`**: the numeric blend, 6 decimals.
- **`total`**: SQL `ROUND(raw)`, not clamped. It can be above 100 or below 0.
- **`display`**: the clamped badge. It is the same number as `interestScore`.
- **`rows`**: the points in these rows sum exactly to `total`.
  - Order: absolute points descending, then absolute exact descending, the profile row first, then topic name A-Z ignoring case, then topic id.
  - Each row is discriminated by `kind`. Null fields are omitted.
  - A `PROFILE` row has `kind`, `levelIndex` (0..4), `exact` and `points`.
  - A `TOPIC` row has `kind`, `topicId`, `name`, `noul`, `hinge`, `weight` (the effective weight: base plus learned, where learned is 0 until Phase 6), `exact` and `points`.
- **`nonMatching`**: `[{topicId, name, noul}]` for topics judged at hinge 0. Ordered by noul descending, then name.

Example: the `articleByIdCarriesAnExactBreakdown` article, with profile 2.56 of 4 and topics a +20 (noul 0.93), b -30 (noul 0.60) and c +10 (noul 0.20):

```json
{
  "id": 123,
  "interestScore": 75,
  "interestBreakdown": {
    "raw": 75.200000,
    "total": 75,
    "display": 75,
    "rows": [
      { "kind": "PROFILE", "levelIndex": 3, "exact": 64.000000, "points": 64 },
      { "kind": "TOPIC", "topicId": 41, "name": "priority-it-topic-a", "noul": 0.93, "hinge": 0.8600000000000001, "weight": 20.0, "exact": 17.200000, "points": 17 },
      { "kind": "TOPIC", "topicId": 42, "name": "priority-it-topic-b", "noul": 0.6, "hinge": 0.19999999999999996, "weight": -30.0, "exact": -6.000000, "points": -6 }
    ],
    "nonMatching": [ { "topicId": 43, "name": "priority-it-topic-c", "noul": 0.2 } ]
  }
}
```

`hinge` is a float8 value from SQL, so the client should round it for display (`countsPct = round(hinge x 100)`). `exact` is numeric with 6 decimals.

Here is how the client prints the capped and floored last lines:

| Article | `total` | `display` | Last line |
|---------|---------|-----------|-----------|
| Capped | 133 | 100 | Total 133, capped at 100 |
| Floored | -30 | 0 | Total -30, floored at 0 |

## Task Commits

1. **Task 1 (tracer): interestScore on list, board and PATCH responses**: `ad7c9e1` (feat)
2. **Task 2: exact apportionment**: `72c4c27` (test, RED), then `43e319a` (feat, GREEN)
3. **Task 3: breakdown on GET /api/articles/{id}**: `ad1fcc2` (test, RED), then `7c89ea7` (feat, GREEN)

## Files Created/Modified

- `repository/InterestScoreQueries.java`: adds `IDS_SCOPE`, `ARTICLE_SCOPE`, `TOTAL`, the `BreakdownInputs` and `TopicContribution` records, `displayScores` and `breakdownInputs`.
- `service/ScoreBreakdowns.java` (new): `build`, `apportion` and `levelIndex`.
- `model/InterestBreakdown.java` (new): the payload records.
- `model/Article.java`: adds `@Transient @JsonInclude(NON_NULL) InterestBreakdown interestBreakdown`.
- `service/ArticleService.java`: adds `withScores` and `findByIdWithBreakdown`.
- `service/BoardService.java`: adds `withScores`.
- `controller/ArticleController.java`: `getArticle` now calls `findByIdWithBreakdown`.
- Tests:
  - `ScoreBreakdownsTest` is new, with 14 cases.
  - `InterestScoreQueriesTest` gains 8 methods.
  - `ArticleControllerTest` gains 2 methods, and `shouldGetArticleById` now stubs `findByIdWithBreakdown`.
  - `PriorityApiIntegrationTest` gains 2 methods, cleans up its board, and cleans up topics with `LIKE 'priority-it-topic%'`.
  - `ArticleServiceTest` gains 2 methods and `BoardServiceTest` gains 1. Both have a new `@Mock InterestScoreQueries`.

## Decisions Made

- The `breakdownInputs` binding uses the same `int` `profilePoints` as every other statement, so the breakdown's SQL input is the badge's input.
- `displayScores` collects rows with a `RowCallbackHandler` into a `HashMap`. Null values cannot occur, because only SCORED rows are in `blended`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Each breakdown statement calls `blendCte(ARTICLE_SCOPE)` itself, with no shared prefix variable**
- **Found during:** Task 3 acceptance gate.
- **Issue:** My first version shared one `cte` prefix variable, so `grep -c 'ARTICLE_SCOPE'` printed 2. The criterion requires at least 3 (the definition plus both statements).
- **Fix:** I inlined `blendCte(ARTICLE_SCOPE)` into both statements. The SQL is unchanged.
- **Files modified:** `InterestScoreQueries.java`
- **Verification:** The grep now prints 3, and I re-ran the Task 3 verify, which passed.
- **Committed in:** `7c89ea7`

**Total deviations:** 1, auto-fixed (Rule 3). **Impact:** a cosmetic source change with no behavior change.

## TDD Gate Compliance

Tasks 2 and 3 (`tdd="true"`) each have a RED `test(05-03)` commit before their GREEN `feat(05-03)` commit.

For each RED commit, I first added compile-only placeholders so the RED run failed on assertions, not on compilation:
- **Task 2:** the data records, plus a `ScoreBreakdowns` that returns empty values.
- **Task 3:** `breakdownInputs` returning `Optional.empty()` and `findByIdWithBreakdown` returning `Optional.empty()`.

RED evidence, validated with `check tdd-red-evidence` (the JUnit XML was transcribed to TAP):
- **Task 2:** the target was `uiMockRowsAddUpToTheBadge`, and 14 of 14 tests failed on assertions. Verdict: `RED_EVIDENCE_OK`.
- **Task 3:** the target was `getArticleSerializesTheBreakdown`, which failed with "Status expected:<200> but was:<404>". 8 tests failed in total. Verdict: `RED_EVIDENCE_OK`.

Some Task 3 tests passed during RED, as expected, because Tasks 1 and 2 had already built that behavior:
- `displayScores*` and `listItemsOmitTheBreakdown`.
- `breakdownInputsEmptyForUnscored`, which passes trivially against the placeholder.

Task 1 is the tracer (`type="tracer"`). It was a single `feat` commit, and its automated verify was re-run and passed before expansion. The mode was interactive and end-of-phase, so no checkpoint was needed.

## Issues Encountered

None.

## Verification

- **Full backend suite** (`./gradlew test -x npmBuild -x npmInstall`, with `DOCKER_HOST` set): 445 tests, 0 failures, 0 errors, 2 skipped. The skips are the pre-existing gated `JevLiveSmokeTest` and `InterestCalibrationSpikeTest`.
- **Acceptance greps**, all passing:

  | Check | Result |
  |-------|--------|
  | `IDS_SCOPE` | 2 |
  | `withScores(` in `ArticleService` / `BoardService` | 6 / 3 |
  | `@Mock private InterestScoreQueries interestScoreQueries` in each service test | 1 / 1 |
  | `RoundingMode.FLOOR` | 1 |
  | `Math.round(in.` or `RoundingMode.HALF` outside comments | 0 |
  | Spring imports in `ScoreBreakdowns` | 0 |
  | `NON_NULL` in `Article` | 1 |
  | `ARTICLE_SCOPE` | 3 |
  | `blendCte(` | 7 |
  | `findByIdWithBreakdown` in the controller | 1 |
  | `articleService.findById(id)` in the controller | 1 (the Raindrop handler) |
  | V7 migrations | 0 |

- **Prohibitions:** `InterestScoreQueries` and `ScoreBreakdowns` import nothing from `integration`, so nothing calls Jev. `InterestScoreQueries` has no DML and no `a.*` select. Every commit named its files explicitly, and the user's uncommitted changes stayed unstaged.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- **05-05** (list badges) can read `interestScore` straight from `/api/articles` and board pages.
- **05-07** (`ScoreRow` / `WhyBreakdown`) renders the contract above. It needs these TS types: `InterestBreakdown`, `BreakdownRow` (a union discriminated by `kind`) and `NonMatchingTopic`.
- **Phase 6:** the `weight` field on a TOPIC row already carries base plus learned, so replacing the `learned` CTE changes the breakdown with no API change.

## Self-Check: PASSED

- The 3 created files exist on disk.
- Commits `ad7c9e1`, `72c4c27`, `43e319a`, `ad1fcc2` and `7c89ea7` are present in `git log`.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*
