---
phase: 09-engagement-learning-model
plan: 03
subsystem: database
tags: [postgres, cte, numeric, property-test, testcontainers, jackson, breakdown]

# Dependency graph
requires:
  - phase: 09-engagement-learning-model
    provides: "09-01: eff2.w_thumbs / eng_raw / eng and contrib.w_thumbs in LEARNED_CTE, engagement constants bound in learnedSql"
provides:
  - "TopicWeight appended components thumbsLearned, engagementRaw, engagementLearned, thumbsEffective; learned = thumbs capped + engagement capped (D-15)"
  - "TOPIC_WEIGHTS_SELECT columns thumbs_learned, eng_raw, eng, w_thumbs; learned computed as ROUND(learned,6) + ROUND(eng,6)"
  - "TopicContribution appended components thumbsWeight, engagementWeight; breakdown columns learned_w / thumbs_w / eng_w as numeric subtraction of 6-decimal rounded values"
  - "InterestBreakdown.Row appended thumbsWeight, engagementWeight (NON_NULL; PROFILE rows omit them); Row.topic gains two trailing double parameters"
  - "InterestLearnedGridTest: 1,456-cell real-Postgres exact-split property grid with a dyadic BigDecimal oracle"
affects: [09-04, 09-05, 09-06, 10, 12]

# Actuals (#2632)
actuals:
  tokens: 9526
  tasks: 2
  commits: 3
plan_head_before: 562ceadb7d1f9ac1877f2a32332ab0def43c16f5
plan_head_after: 3ebc2ef0ad6ea8da237be44760c7d5ef04f07df0

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dyadic grid oracle: learnRate 32 with noul 0.5 + k/64 (votes) and 0.5 + e/32 (saves) makes every learned value exact in float8, so a BigDecimal oracle compares with compareTo == 0"
    - "Split parts are SQL numeric subtraction of already-rounded 6-decimal values, never rounding of float differences"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
    - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
    - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
    - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java

key-decisions:
  - "Task 2's RED commit adds the Row shape with ScoreBreakdowns passing 0, 0, so RED is an assertion failure (RED_EVIDENCE_OK) instead of a compile failure (INVALID_RED), mirroring 09-01's declared-constant RED"
  - "The grid seeds with JdbcTemplate.batchUpdate and reads ids back by name/guid; the three grid tests run in about 5 s including seeding"
  - "breakdownSplitEqualsTopicWeightSplit samples 30 cells (bases -45, -5, 0, 10, 45 x thumbs -24, -5, 24 x engagement 0, 16) and asserts that the sample itself hits every clamp branch"

patterns-established:
  - "Grid branch coverage is asserted, not assumed: thumbs cap, engagement cap, sign clamp for both signs, +50 and -50 range, and exact +50 / -50 / 0 are each counted > 0"

requirements-completed: [LRN-03, LRN-04]

coverage:
  - id: D1
    description: "Across 1,456 grid topics every effective weight, thumbs-only effective weight, learned, thumbsLearned, engagementRaw and engagementLearned equals the exact one-clamp BigDecimal oracle; engagement applied is never negative and never moves a negative-base topic; every clamp branch is hit"
    requirement: LRN-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java#everyCellMatchesTheExactOracle"
        status: pass
    human_judgment: false
  - id: D2
    description: "The five CONTEXT/research worked cells split exactly as decided, through both topicWeights and breakdownInputs"
    requirement: LRN-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java#workedCellsSplitAsDecided"
        status: pass
    human_judgment: false
  - id: D3
    description: "On 30 sampled breakdown rows base + thumbs + engagement = weight and learned = thumbs + engagement exactly, and the parts equal the TopicWeight split"
    requirement: LRN-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java#breakdownSplitEqualsTopicWeightSplit"
        status: pass
    human_judgment: false
  - id: D4
    description: "v0.2.1 values unchanged with no engagement rows: InterestScoreQueriesTest (learnedWeight 1.72, 1.0, 0.4, 0.0, -5.0), ArticleFeedbackServiceTest, ScoreBreakdownsTest and the replay drift guard pass with no expected value changed"
    requirement: LRN-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java (36 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java (14 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Why N? JSON: TOPIC rows carry thumbsWeight and engagementWeight, PROFILE rows omit them; over HTTP a starred scored article shows base 20.0, learned 0.9, thumbs 0.0, engagement 0.9, weight 20.9"
    requirement: LRN-03
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#topicRowsCarryTheSplit"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#getArticleSerializesBaseAndLearnedWeight"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#whyBreakdownCarriesTheEngagementPart"
        status: pass
    human_judgment: false

# Metrics
duration: 6min
completed: 2026-09-30
status: complete
---

# Phase 9 Plan 03: Exact Thumbs/Engagement Split Summary

**A 1,456-cell real-Postgres grid with a dyadic BigDecimal oracle proves that base + thumbs + engagement = effective exactly at every clamp branch. `TopicWeight`, `TopicContribution` and the "Why N?" `Row` now expose the thumbs and engagement parts. Each part is computed in SQL by subtracting 6-decimal rounded values.**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-30T16:26:54Z
- **Completed:** 2026-09-30T16:32:42Z
- **Tasks:** 2
- **Files modified:** 8 (1 created, 7 modified)

## Accomplishments

- `InterestLearnedGridTest` has 3 tests over 14 bases x 13 thumbs values x 8 engagement values (1,456 topics, about 2,600 articles). Each topic is checked against the one-clamp oracle `clamp(base + clamp(t, +/-20) + (base < 0 ? 0 : min(e, 8)))`.
  - The oracle checks both the thumbs-only and the full effective weight on every cell, with exact `BigDecimal` compares.
  - Engagement applied is never negative, and a negative base never moves.
  - `learned = thumbsLearned + engagementLearned` holds on every cell.
  - Each clamp branch is counted and must be hit at least once: both caps, the sign clamp for both signs, the +50 and -50 range, and exact +50, -50 and 0.
- The five worked cells split exactly as decided in CONTEXT/research, for example (10, -20, 8) → 0, 0, -10, 0 and (0, -5, 8) → -5, 3, -5, 8.
- `TOPIC_WEIGHTS_SELECT` returns `thumbs_learned`, `eng_raw`, `eng` and `w_thumbs`. `learned` is now thumbs capped + engagement capped (D-15). With no engagement, every existing field keeps its v0.2.1 value.
- In the breakdown topic select:
  - `learned_w = ROUND(w,6) - ROUND(base,6)`
  - `thumbs_w = ROUND(w_thumbs,6) - ROUND(base,6)`
  - `eng_w = ROUND(w,6) - ROUND(w_thumbs,6)`
  - The existing learnedWeight expectations (1.72, 1.0, 0.4, 0.0, -5.0) are unchanged.
- "Why N?" TOPIC rows carry `thumbsWeight` and `engagementWeight` end to end over HTTP. PROFILE rows omit both (`NON_NULL`).

## Task Commits

1. **Task 1 (tracer): the grid proves the exact split; topic weights and breakdown expose it:** `5cc2d2e` (feat).
   - RED was observed and not committed, per the plan: the grid failed to compile only on the new accessors.
   - Tracer gate (interactive, end-of-phase, automated-only verify): the verify was re-run green before expansion.
2. **Task 2 (TDD): "Why N?" JSON carries the split:**
   - RED `1b4e59e` (test)
   - GREEN `3ebc2ef` (feat)
   - No refactor was needed.

**Plan metadata:** committed with this SUMMARY (docs)

## TDD Gate Compliance

- **Task 1 (tracer).** The RED state was run: `compileTestJava` failed only with `cannot find symbol` on `thumbsLearned` / `engagementRaw` / `engagementLearned` / `thumbsEffective` / `thumbsWeight` / `engagementWeight`. It was not committed, per the plan ("Do not commit the red state").
- **Task 2.**
  - RED `1b4e59e`: `topicRowsCarryTheSplit()` failed with `expected: 1.8 but was: 0.0`, and `whyBreakdownCarriesTheEngagementPart()` with `expected: 0.9 but was: 0.0`. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target `topicRowsCarryTheSplit()`).
  - GREEN `3ebc2ef`: 17 + 30 + 22 tests pass. The full suite has 618 tests, 0 failures and the 2 existing live-Jev skips.

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java` (new): the grid, the oracle, the worked cells and the breakdown sample.
- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java`: appended record components and javadocs, the split columns in `TOPIC_WEIGHTS_SELECT` and the breakdown select, and the mappers. `LEARNED_CTE` and `blendCte` are untouched.
- `src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java`: `Row.thumbsWeight` and `Row.engagementWeight`; the `Row.topic` trailing parameters; the javadoc.
- `src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java`: passes `t.thumbsWeight(), t.engagementWeight()`.
- `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java`: 5 `TopicWeight` sites appended. No expected value changed.
- `src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java`: new 10-arg `topic(...)` helper (the 8-arg one delegates to it with `learnedWeight, 0`) and `topicRowsCarryTheSplit`.
- `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java`: 4 `Row.topic` calls appended, plus split-key assertions on the TOPIC row and absence assertions on the PROFILE row.
- `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java`: `whyBreakdownCarriesTheEngagementPart` (topic `engagement-it-split`).

## Decisions Made

- **Task 2 RED shape.** The RED commit adds the `Row` shape, with `ScoreBreakdowns` passing `0, 0`. RED is then an assertion failure, which `check tdd-red-evidence` accepts, rather than a compile failure, which it classes as INVALID_RED. This is the same approach 09-01 took with its declared constant.
- **Breakdown sample.** It has 30 cells in which every cell has an article, and the test asserts that the sample itself covers every clamp branch.

## Deviations from Plan

None: the plan was executed as written.

- The plan's interface notes say `ArticleControllerTest` has "5 `Row.topic(` calls". The file has 4 (lines 158-160 and 188); all 4 were updated.
- The plan-commit ledger could not be written: the sandbox refused the command that writes into the git dir. The base commit `562ceadb` is recorded as `plan_head_before` instead, as 09-01 did.

## Issues Encountered

None.

## Known Stubs

None. The Task 2 RED placeholder (`0, 0`) was replaced in the GREEN commit.

## User Setup Required

None: no external service configuration required.

## Next Phase Readiness

- 09-05 can build `TopicLearned` / `TopicEffect` / `LearnedLimit` on `TopicWeight.learned` (now combined, D-15) and on the appended `thumbsLearned` / `engagementRaw` / `engagementLearned` / `thumbsEffective`.
- Phase 10 only renders `Row.thumbsWeight` / `Row.engagementWeight`; the frontend types do not have them yet.
- No release (D-14).

---
*Phase: 09-engagement-learning-model*
*Completed: 2026-09-30*

## Self-Check: PASSED

- Files exist: InterestLearnedGridTest.java, 09-03-SUMMARY.md
- Commits exist: 5cc2d2e, 1b4e59e, 3ebc2ef
- Full backend suite green (618 tests, 0 failures, 2 existing live-Jev skips); all Task 1 and Task 2 acceptance greps pass
