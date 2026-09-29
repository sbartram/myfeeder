---
phase: 06-thumbs-feedback
plan: 01
subsystem: api
tags: [spring-boot, jdbcclient, postgres, cte, interest-ranking, thumbs-feedback, testcontainers]

requires:
  - phase: 03-interest-schema
    provides: V6 article_feedback and article_feedback_topic tables (topics_narrowed, 03 D-02)
  - phase: 05-priority-view
    provides: blend CTE in InterestScoreQueries (sort, badge, breakdown) with the stub learned CTE
provides:
  - PUT /api/articles/{id}/feedback (JSON-only) and DELETE /api/articles/{id}/feedback returning FeedbackResult {article, scored, effects[]}
  - LEARNED_CTE deriving each topic's capped learned adjustment and sign-clamped effective weight from article_feedback
  - InterestScoreQueries.topicWeights / matchedTopicIds / isScored and the TopicWeight record
  - ArticleFeedbackStore (upsert/delete of votes and picks), ArticleFeedbackService (vote/clear), LearnedLimit enum
  - myfeeder.interest.blend.learn-rate (2) and learned-cap (20) config keys
affects: [06-02, 06-03, 06-04, 06-05, 06-06, 06-07, 07-tuning]

actuals:
  tokens: 16500
  tasks: 3
  commits: 5
plan_head_before: 9d2d91d0bad0e455d4773cd1470a3686fadcb32f

tech-stack:
  added: []
  patterns:
    - "Derived learned model: votes are rows; the adjustment is recomputed in SQL on every read, so undo is exact"
    - "blendSql/learnedSql helpers bind every blend constant in one place"
    - "Before/write/after in one @Transactional service method reports server-computed effects"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/repository/ArticleFeedbackStore.java
    - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
    - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
    - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
    - src/main/java/org/bartram/myfeeder/controller/FeedbackRequest.java
    - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
    - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java

key-decisions:
  - "ArticleFeedbackService.applyAndReport takes Function<List<Long>, Runnable>: it hands the matched topics to the caller, which validates picks and returns the write, so a bad pick is refused before any weight read or write while the before/write/after sequence stays in one method"
  - "Effects skip a matched topic missing from either weight read (a topic deleted mid-request) instead of failing the vote"
  - "Task 3's RED commit carried a compile scaffold (LearnedLimit with of() returning NONE, limit wired into TopicEffect) so RED failed on assertions, not on compilation"

patterns-established:
  - "Vote endpoints: PUT is JSON-only (415 for text/form), DELETE needs no content-type guard"
  - "Fixed-text IllegalArgumentException messages for vote validation, never echoing input"

requirements-completed: [FDBK-01, FDBK-02, FDBK-03, FDBK-04, FDBK-05, FDBK-06]

coverage:
  - id: D1
    description: "PUT /api/articles/{id}/feedback stores one vote row and re-blends every badge reading the matched topics at once, with no Jev call and no base-weight write"
    requirement: FDBK-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#upVoteRaisesMatchedTopicsAndTheBadge"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#badgeReflectsLearnedWeight"
        status: pass
    human_judgment: false
  - id: D2
    description: "Learned model holds the +/-20 cap, the sign clamp, the +/-50 range, a zero base moving both ways, narrowing and unscored votes counting nothing"
    requirement: FDBK-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#learnedIsCappedAtTwenty, positiveBaseNeverGoesBelowZero, negativeBaseNeverGoesAboveZero, effectiveWeightStaysWithinFifty, zeroBaseMovesBothWays, narrowedVoteMovesOnlyPickedTopics, narrowedVoteWithNoPicksMovesNothing, voteOnUnscoredArticleCountsNothing, learnedFollowsOneUpVote"
        status: pass
    human_judgment: false
  - id: D3
    description: "DELETE restores exactly; up, down, up equals a single up; repeated PUT and DELETE without a vote change nothing"
    requirement: FDBK-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#upDownUpEqualsASingleUp, deleteRestoresExactly, repeatedPutChangesNothing, deleteWithoutAVoteIsANoOp"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#deletingTheVoteRestoresExactly"
        status: pass
    human_judgment: false
  - id: D4
    description: "Response reports per-topic before/after effective weights, base, learned and limit (NONE/LEARNED_CAP/SIGN_CLAMP/WEIGHT_RANGE)"
    requirement: FDBK-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java#effectsCarryBeforeAndAfterForEveryMatchedTopic, learnedAtExactlyTheCapIsLearnedCap, sumCrossingZeroIsSignClamp, sumExactlyZeroIsNotSignClamp, sumBeyondFiftyIsWeightRange, sumExactlyFiftyIsNone"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#upVoteEffectNamesItsLimit"
        status: pass
    human_judgment: false
  - id: D5
    description: "A vote on an unscored article reports scored false; on a scored article with no matched topic reports scored true with no effects; both are stored"
    requirement: FDBK-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#unscoredVoteIsStoredAndReportsNotScored, scoredNoMatchVoteIsStoredWithNoEffects"
        status: pass
    human_judgment: false
  - id: D6
    description: "Narrowed thumbs-down moves only picked topics; a flip to up clears narrowing; bad votes/picks are fixed-text 400s before any write, missing article 404, non-JSON PUT 415"
    requirement: FDBK-06
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java#rejectsAVoteOtherThanPlusOrMinusOne, rejectsAnEmptyTopicList, rejectsMoreTopicIdsThanTopicsCanExist, missingArticleIsNotFound, rejectsATopicTheArticleDidNotMatch, duplicatePicksAreStoredOnce"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#putFeedbackRequiresJson, putFeedbackBadRequestIs400, putFeedbackMissingArticleIs404"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#narrowedDownVoteMovesOnlyPickedTopics, flipToUpClearsNarrowing, pickingAnUnmatchedTopicIs400AndWritesNothing, voteLeavesReadAndStarredAlone"
        status: pass
    human_judgment: false

duration: 10min (continuation); prior session time not recorded
completed: 2026-09-27
status: complete
---

# Phase 6 Plan 01: Thumbs Vote API with Derived Learned Weights Summary

**PUT/DELETE `/api/articles/{id}/feedback` store one vote row each. A SQL learned CTE derives every topic's capped (+/-20), sign-clamped, +/-50-bounded effective weight from those rows. The response reports per-topic before/after weights and the limit that applied, so undo is exact and Jev is never called.**

## Performance

- **Duration:** about 10 min for this continuation (2026-09-27T03:02Z to 03:12Z). The prior executor wrote and verified Task 1 before its checkpoint; that session's time was not recorded.
- **Started:** prior session (Task 1 code), resumed 2026-09-27T03:02Z
- **Completed:** 2026-09-27T03:12Z
- **Tasks:** 3
- **Files modified:** 14

## Accomplishments

- `LEARNED_CTE` replaces the stub. It computes learned = clamp(learnRate x SUM(vote x hinge), +/-learnedCap) over votes on SCORED articles and honors `topics_narrowed` picks. It then applies the sign clamp and the +/-50 range. Sort, badge and breakdown all read it through one `blendSql` helper, so exactly one `profilePoints` bind remains.
- PUT (JSON-only, 415 otherwise) and DELETE endpoints return `FeedbackResult {article, scored, effects[]}`. Each effect's before/after effective weight is read inside the write's transaction.
- Validation runs before any write, with fixed-text 400s:
  - vote must be 1 or -1
  - topicIds, when present, must be non-empty
  - topicIds can hold at most 25 entries
  - each topicId must be a topic this article matched
  - duplicate picks are stored once
- A missing article returns 404.
- `LearnedLimit` names the limit on each effect, checked in this order: LEARNED_CAP (at or beyond the cap), then SIGN_CLAMP (the sum crosses zero), then WEIGHT_RANGE (beyond +/-50), else NONE.
- `learn-rate: 2` and `learned-cap: 20` are mirrored into both application.yaml files, so DevProfileConfigTest stays green.

## Task Commits

1. **Task 1 (tracer): up-vote through PUT raises matched topic weight and badge** - `7c6e920` (feat)
2. **Task 2: learned-model bounds; removing/flipping a vote undoes exactly**
   - RED `042fd7c` (test)
   - GREEN `c7d40b3` (feat)
3. **Task 3: bad votes refused before write; narrowing validated; effects name their limit**
   - RED `808fb6e` (test)
   - GREEN `f87f527` (feat)

## Files Created/Modified

- `repository/InterestScoreQueries.java`: LEARNED_CTE, TopicWeight, topicWeights, matchedTopicIds, isScored, learnedSql/blendSql
- `repository/ArticleFeedbackStore.java`: vote upsert (`ON CONFLICT (article_id)`), pick replace, delete
- `service/ArticleFeedbackService.java`: vote/clear with validation and a single before/write/after report method
- `service/FeedbackResult.java`: the `{article, scored, effects}` contract with nested TopicEffect (limit last)
- `service/LearnedLimit.java`: the limit enum and its boundary rules
- `controller/FeedbackRequest.java`: request body (boxed vote, nullable topicIds)
- `controller/ArticleController.java`: `setFeedback` (PUT) and `clearFeedback` (DELETE)
- `config/MyfeederProperties.java`: `Blend.learnRate` and `Blend.learnedCap`
- `src/main/resources/application.yaml` and `src/test/resources/application.yaml`: the two blend keys
- Tests:
  - `FeedbackApiIntegrationTest` (12 API cases)
  - `ArticleFeedbackServiceTest` (14 unit cases)
  - `InterestScoreQueriesTest` (+12 learned-model cases)
  - `ArticleControllerTest` (+5 feedback cases)

## Decisions Made

- `applyAndReport(articleId, Function<List<Long>, Runnable>)` gives the caller the matched set before any weight read, so pick validation needs no second lookup and happens before any read or write.
- An effect is skipped when its topic is missing from either weight read (deleted mid-request) instead of failing the vote.

## Deviations from Plan

### Process deviations

**1. Execution-context files were read by the prior executor.** The six execution_context files were read at the start of the first executor session. This continuation re-read all six.

**2. [Process] The project-root pin guard first ran after the first few Writes.** In the prior session the guard ran after the first Writes rather than before them. It passed, since the cwd was the pinned root. In this continuation it ran before every commit, as a saved copy of the verbatim guard.

**3. [Rule 3 - Blocking] TruffleHog pre-commit hook blocked the first commit.** The hook's self-updater failed with "cannot move binary". The user reinstalled trufflehog via Homebrew (`/opt/homebrew/bin/trufflehog`, v3.97.9). On resume the Task 1 commit ran with hooks on and TruffleHog passed; every later commit also passed. No hook was bypassed.

**4. [TDD] Task 3 RED commit included a compile scaffold.** The tests reference `LearnedLimit` and `TopicEffect.limit`, which did not exist. A test-only RED would have failed to compile (INVALID_RED). So `808fb6e` also adds the enum with `of()` stubbed to `NONE` and wires `limit` through `FeedbackResult` and the service. The RED run then failed on 10 assertions (RED_EVIDENCE_OK), and GREEN `f87f527` implemented the rules.

---

**Total deviations:** 4 (1 blocking tool issue resolved by the user, 3 process/TDD notes). **Impact on plan:** none on scope or behavior.

## TDD Gate Compliance

- Task 2: RED `042fd7c` is followed by GREEN `c7d40b3`. RED evidence: target `deleteRestoresExactly` failed with "Status expected:<200> but was:<405>", verdict RED_EVIDENCE_OK. Some tests already passed in RED because they cover the CTE and PUT that the Task 1 tracer built: the 12 learned-model tests in InterestScoreQueriesTest, plus `upDownUpEqualsASingleUp` and `repeatedPutChangesNothing`. The plan expected this ("the delete endpoint is missing, so the integration tests fail").
- Task 3: RED `808fb6e` is followed by GREEN `f87f527`. RED evidence: target `rejectsAVoteOtherThanPlusOrMinusOne` failed on an assertion; 10 tests failed in total, all on assertions; verdict RED_EVIDENCE_OK. The tests that passed in RED rely on existing behavior: the 404 lookup, scored/unscored reporting, the stub's NONE boundaries, the controller routes, and storing narrowed picks.
- RED evidence was recorded by translating the JUnit XML to TAP for `check tdd-red-evidence`, since Gradle emits no TAP.
- Task 1 is a tracer (not tdd="true"). Its test and code were committed together in `7c6e920`.

## Issues Encountered

- The TruffleHog updater failure is covered in Deviations #3.

## Verification

- Full backend suite `./gradlew test -x npmBuild -x npmInstall`: 503 tests, 0 failures, 0 errors. The 2 skips are the pre-existing live-API tests `JevLiveSmokeTest` and `InterestCalibrationSpikeTest`.
- All per-task `<verify>` commands and `<acceptance_criteria>` pass.
- There is no V7 migration.
- No `update interest_topic` appears in the vote path.
- `ArticleFeedbackService` has no integration import.
- `git status` shows the user's `.envrc`, `CLAUDE.md`, `.claude/CLAUDE.md` and `.planning/config.json` still modified and unstaged.

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness

- The `FeedbackResult` / `TopicEffect` JSON contract is fixed for the frontend plans 06-03, 06-04, 06-05 and 06-07. Plans 06-02 and 06-06 can extend `InterestScoreQueries` through `LEARNED_CTE` and `learnedSql`.
- No blockers.

## Self-Check: PASSED

- All 7 created files are present on disk.
- Commits 7c6e920, 042fd7c, c7d40b3, 808fb6e and f87f527 are present in git log.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*
