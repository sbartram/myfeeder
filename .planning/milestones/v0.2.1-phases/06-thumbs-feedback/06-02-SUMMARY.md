---
phase: 06-thumbs-feedback
plan: 02
subsystem: api
tags: [spring-boot, jdbcclient, postgres, cte, interest-ranking, thumbs-feedback, jackson]

requires:
  - phase: 06-thumbs-feedback
    provides: "06-01 LEARNED_CTE (learned -> eff -> eff2), blendCte contrib joining eff2, ArticleFeedbackStore, ArticleFeedbackService responses via findByIdWithBreakdown"
provides:
  - "ArticleFeedback record {vote, narrowed, topics[{topicId, name}]} on GET /api/articles/{id} and the PUT/DELETE feedback responses (absent when there is no vote; never on lists)"
  - "ArticleFeedbackStore.find(long) : Optional<ArticleFeedback>"
  - "InterestBreakdown.Row.baseWeight / learnedWeight on TOPIC rows (applied learned part, base + learned = weight)"
  - "contrib CTE columns base and learned_applied; TopicContribution.baseWeight / learnedWeight"
affects: [06-03, 06-04, 06-05, 06-06, 06-07]

actuals:
  tokens: 7256
  tasks: 2
  commits: 3
plan_head_before: 1dab06c67883614e93e833fec6cbfe5235a9e0cb

tech-stack:
  added: []
  patterns:
    - "Transient response-only fields on Article (@Transient + @JsonInclude(NON_NULL)) set only by findByIdWithBreakdown, so list endpoints never carry them"
    - "Learned values shown to the user are projections of the one eff2 CTE, never stored"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/model/ArticleFeedback.java
  modified:
    - src/main/java/org/bartram/myfeeder/model/Article.java
    - src/main/java/org/bartram/myfeeder/repository/ArticleFeedbackStore.java
    - src/main/java/org/bartram/myfeeder/service/ArticleService.java
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
    - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
    - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java

key-decisions:
  - "breakdownInputs now returns the effective weight rounded to 6 decimals (ROUND(c.w::numeric, 6)), like base_w and learned_w, so base + learned equals weight exactly; exact, sort and badge are unchanged"
  - "ArticleFeedbackStore.find reads picks only when topics_narrowed is true; an un-narrowed vote returns an empty topics list"

patterns-established:
  - "Vote state rides on findByIdWithBreakdown only: GET /api/articles/{id} and the PUT/DELETE feedback responses"

requirements-completed: [FDBK-01, FDBK-04, FDBK-06]

coverage:
  - id: D1
    description: "GET /api/articles/{id} and the PUT response carry feedback {vote, narrowed, topics[{topicId, name}]}; an un-narrowed vote has an empty topics list"
    requirement: FDBK-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#getArticleCarriesTheVoteState, unNarrowedVoteHasNoPicks"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java#findByIdWithBreakdownCarriesTheVote, findByIdWithBreakdownWithoutAVoteHasNoFeedback"
        status: pass
    human_judgment: false
  - id: D2
    description: "After DELETE, the DELETE response article and GET omit the feedback key; list items never carry it (D-02)"
    requirement: FDBK-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#removedVoteHasNoFeedbackKey, listItemsOmitFeedback"
        status: pass
    human_judgment: false
  - id: D3
    description: "Why TOPIC rows carry baseWeight and the applied learnedWeight (effective minus base, after the cap, the sign clamp and the +/-50 range); PROFILE rows omit both; rows still sum exactly to total"
    requirement: FDBK-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#breakdownInputsCarryBaseAndAppliedLearned, breakdownLearnedIsTheAppliedPart, breakdownInputsMatchTheBadge, oneNumberEverywhere"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java#topicRowsCarryBaseAndLearnedWeight, rowsStillSumToTotalWithLearnedWeights"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#getArticleSerializesBaseAndLearnedWeight"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-09-27
status: complete
---

# Phase 6 Plan 02: Vote State and Learned Weight on Article Responses Summary

**`GET /api/articles/{id}` and the PUT/DELETE feedback responses now carry the stored vote as `feedback {vote, narrowed, topics[{topicId, name}]}`. List responses never carry it. Each "Why N?" TOPIC row now carries `baseWeight` and `learnedWeight`. `learnedWeight` is the part of the learned adjustment that actually applied, taken from the same eff2 CTE, so the two add up to `weight` and the rows still sum exactly to the badge.**

## Performance

- **Duration:** about 5 min
- **Started:** 2026-09-27T03:32:43Z
- **Completed:** 2026-09-27T03:38Z
- **Tasks:** 2
- **Files modified:** 12 (1 created, 11 modified)

## Accomplishments

- New `ArticleFeedback` record. `Article.feedback` is `@Transient` and `@JsonInclude(NON_NULL)`, and only `findByIdWithBreakdown` sets it, for both scored and unscored articles. So it appears on the single-article GET and on both feedback responses, and never on list, Priority or board responses (D-02).
- `ArticleFeedbackStore.find` is read-only and uses named parameters only. It reads `vote, topics_narrowed`. For a narrowed vote it also reads the picks and their names from `interest_topic`, in topic id order.
- The `contrib` CTE now selects `e.base` and `e.w - e.base AS learned_applied`. `breakdownInputs` rounds `w`, `base_w` and `learned_w` to 6 decimals. `TopicContribution` and `InterestBreakdown.Row` each gain two trailing fields, `baseWeight` and `learnedWeight`. No existing JSON key changed.
- For topic Small (base 5) with 12 down-votes, learned is capped at -20 but the applied part is -5, because the weight is clamped at 0. The row therefore shows `learnedWeight` -5.0, not -20.

## Task Commits

1. **Task 1 (tracer): GET and the vote responses carry the stored vote and its picks**: `ee9f996` (feat)
2. **Task 2: Why topic rows carry base weight and the applied learned part**
   - RED `8857aee` (test)
   - GREEN `918890b` (feat)

## Files Created/Modified

- `model/ArticleFeedback.java` (new): the vote-state record with nested `Topic(topicId, name)`
- `model/Article.java`: the transient `feedback` field
- `repository/ArticleFeedbackStore.java`: `find(long)`
- `service/ArticleService.java`: the `articleFeedbackStore` dependency; `findByIdWithBreakdown` sets `feedback`
- `repository/InterestScoreQueries.java`: the `contrib` columns `base` and `learned_applied`; the new `TopicContribution` fields; the rounded weight mapping
- `model/InterestBreakdown.java`: `Row.baseWeight` and `Row.learnedWeight`; `topic(...)` takes the two new values
- `service/ScoreBreakdowns.java`: passes `t.baseWeight(), t.learnedWeight()` to `Row.topic`
- Tests: `ArticleServiceTest` (+2), `FeedbackApiIntegrationTest` (+4), `ScoreBreakdownsTest` (+2 and a helper overload), `ArticleControllerTest` (+1, three existing `Row.topic` calls updated), `InterestScoreQueriesTest` (+2)

## Decisions Made

- The effective `w` in `breakdownInputs` is now rounded to 6 decimals, the same as `base_w` and `learned_w`, as the plan specified. Because the base is an integer, `ROUND(w) = base + ROUND(w - base)` holds exactly.
- The picks query runs only for a narrowed vote.

## Deviations from Plan

### Process notes

**1. [TDD] Task 2's RED commit included a compile scaffold.** The new tests call `baseWeight()` and `learnedWeight()` on `TopicContribution` and `Row`. Without those record fields the tests would not compile, which counts as INVALID_RED. So `8857aee` also adds the fields with their values stubbed to 0: `ScoreBreakdowns` passed `0, 0` and `breakdownInputs` mapped `0, 0`. This is the same approach as 06-01 Task 3. The RED run then failed on assertions (4 tests), and GREEN `918890b` supplied the real values. Because `ArticleControllerTest#getArticleSerializesBaseAndLearnedWeight` builds its `Row` directly, the scaffold alone made it pass in RED.

---

**Total deviations:** 1 process note. **Impact on plan:** none on scope or behavior.

## TDD Gate Compliance

- Task 2: RED `8857aee` is followed by GREEN `918890b`. No refactor commit, since nothing needed cleanup.
- RED evidence: the target `breakdownInputsCarryBaseAndAppliedLearned` failed with "[baseWeight of Rust] expected: 20.0 but was: 0.0". The failing tests were `breakdownInputsCarryBaseAndAppliedLearned`, `breakdownLearnedIsTheAppliedPart`, `topicRowsCarryBaseAndLearnedWeight` and `rowsStillSumToTotalWithLearnedWeights`, all on assertions. `check tdd-red-evidence` returned RED_EVIDENCE_OK. Gradle emits no TAP, so the JUnit XML was translated to TAP for the check.
- Task 1 is a tracer, not `tdd="true"`, so its test and code are in one commit, `ee9f996`. Tracer gate: the run was interactive with `end-of-phase` and an automated-only `<verify>`. The verify was re-run and passed, so expansion continued.

## Issues Encountered

None. The Testcontainers SSL flake did not occur.

## Verification

- Full backend suite `./gradlew test -x npmBuild -x npmInstall`: 514 tests, 0 failures, 0 errors. The 2 skips are the existing live-API tests.
- Both tasks' `<verify>` commands pass, and every `<acceptance_criteria>` grep returns the expected count (1/1/1; 3/1/1).
- Prohibition check: no `UPDATE`/`INSERT` on `interest_topic` in `ArticleFeedbackStore`, `InterestScoreQueries` or `ArticleService`.
- `git status` still shows the user's `.envrc`, `CLAUDE.md`, `.claude/CLAUDE.md` and `.planning/config.json` as modified and unstaged.
- The six execution_context files were read at the start (execute-plan.md, summary.md, checkpoints.md, tdd.md, worktree-path-safety.md, executor-examples.md). The project-root pin guard ran before the first write and before every commit.

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness

- The JSON contract for the frontend plans 06-03 to 06-05 is ready. `article.feedback` is `{vote: 1 | -1, narrowed, topics: [{topicId, name}]}` and is absent when there is no vote. `interestBreakdown.rows[i].baseWeight` and `.learnedWeight` appear on TOPIC rows only. Plan 06-06 documents both in CLAUDE.md.
- No blockers.

## Self-Check: PASSED

- `src/main/java/org/bartram/myfeeder/model/ArticleFeedback.java` exists.
- Commits ee9f996, 8857aee and 918890b are in git log.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*
