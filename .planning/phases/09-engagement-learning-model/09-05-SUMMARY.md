---
phase: 09-engagement-learning-model
plan: 05
subsystem: api
tags: [spring-mvc, learned-model, engagement, testcontainers, tdd]

# Dependency graph
requires:
  - phase: 09-engagement-learning-model
    provides: "09-03: TopicWeight.thumbsLearned / engagementRaw / engagementLearned, with learned = thumbs capped + engagement capped (D-15)"
  - phase: 09-engagement-learning-model
    provides: "09-02: frontend LearnedLimit and optional thumbsLearned / engagementLearned fields"
provides:
  - "LearnedLimit.ENGAGEMENT_CAP (appended last) and LearnedLimit.of(TopicWeight, learnedCap, engagementCap) with the D-11 precedence"
  - "TopicLearned appended thumbsLearned, engagementLearned on GET /api/interest/topics/learned"
  - "FeedbackResult.TopicEffect appended thumbsLearned, engagementLearned on every vote effect"
  - "HTTP proof that a vote's before includes the engagement the vote replaces (D-12)"
affects: [09-06, 10, 12]

# Actuals (#2632)
actuals:
  tokens: 5614
  tasks: 3
  commits: 5
plan_head_before: c24533ca2b541d660f626786c9ed97b9224a3884
plan_head_after: 9e99cb40e2860ea29a556e02eb0437fb07459866

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Appended record components for new JSON keys: existing keys keep their names and meaning, so the current UI keeps working (D-10)"
    - "The RED commit adds the record shape with the service passing 0, 0, so RED is an assertion failure (RED_EVIDENCE_OK) rather than a compile failure (same approach as 09-01 and 09-03)"

key-files:
  created: []
  modified:
    - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
    - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
    - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
    - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
    - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java

key-decisions:
  - "LearnedLimit.of keeps the three-argument form only; the two-argument form is removed so every caller must pass the engagement cap"
  - "Task 1 is a tracer: its RED (compile failure on the new signature and constant) was observed and not committed; Tasks 2 and 3 committed assertion-failure REDs verified with check tdd-red-evidence"

patterns-established:
  - "Engagement is seeded in FeedbackApiIntegrationTest with a direct INSERT INTO article_engagement helper, and every topic keeps the feedback-it- prefix"

requirements-completed: [LRN-02, LRN-04]

coverage:
  - id: D1
    description: "ENGAGEMENT_CAP follows the D-11 precedence: LEARNED_CAP first, then ENGAGEMENT_CAP (cap > 0, raw >= cap, exactly 8.0 counts and 7.999999 does not), then SIGN_CLAMP / WEIGHT_RANGE judged on base + thumbs + engagement; cap 0 and a negative base never report it"
    requirement: LRN-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java#learnedCapOutranksEngagementCap, engagementAtExactlyItsCapIsEngagementCap, engagementCapOutranksClampAndRange, capZeroNeverReportsEngagementCap, engagementIsPartOfTheSum, negativeBaseNeverReportsEngagementCap"
        status: pass
    human_judgment: false
  - id: D2
    description: "Over HTTP a base-20 topic matched at noul 1.0 by 8 starred scored articles serves learned 8.0, effectiveWeight 28.0, limit ENGAGEMENT_CAP, thumbsLearned 0.0, engagementLearned 8.0, and interest_topic.weight stays 20"
    requirement: LRN-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#learnedEndpointReportsTheEngagementCap"
        status: pass
    human_judgment: false
  - id: D3
    description: "GET /api/interest/topics/learned serves thumbsLearned and engagementLearned after the existing keys: one up-voted and one saved article at noul 0.95 give learned 2.7 = 1.8 + 0.9, effective 22.7, NONE; zero-engagement values are unchanged"
    requirement: LRN-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#learnedEndpointSplitsVotesAndEngagement"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#learnedEndpointMatchesTheVoteEffects"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java#learnedTopicsAreServed"
        status: pass
    human_judgment: false
  - id: D4
    description: "A vote on a starred article reports a before that includes the engagement it replaces: PUT 20.9 -> 21.8 (learned 1.8 = thumbs 1.8 + engagement 0.0), DELETE 21.8 -> 20.9 (learned 0.9 = thumbs 0.0 + engagement 0.9); Jev is never called and the topic weight stays 20"
    requirement: LRN-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#voteEffectBeforeIncludesTheEngagementItReplaces"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#putFeedbackReturnsTheResult"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java#effectsCarryBeforeAndAfterForEveryMatchedTopic"
        status: pass
    human_judgment: false

# Metrics
duration: 6min
completed: 2026-09-30
status: complete
---

# Phase 9 Plan 05: Engagement Cap Limit and Learned Split Summary

**`LearnedLimit` gains `ENGAGEMENT_CAP` with the D-11 precedence, judged on base + thumbs + engagement. `GET /api/interest/topics/learned` and every vote effect now carry `thumbsLearned` and `engagementLearned` next to the combined `learned`. An HTTP test shows that a vote's "before" includes the engagement the vote replaces (20.9 → 21.8 and back).**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-30T16:36:01Z
- **Completed:** 2026-09-30T16:42:01Z
- **Tasks:** 3
- **Files modified:** 8 (0 created, 8 modified)

## Accomplishments

- `LearnedLimit` gains `ENGAGEMENT_CAP` as its last constant. `of(w, learnedCap, engagementCap)` returns the first rule that matches:
  1. LEARNED_CAP on `|learnedRaw|`
  2. ENGAGEMENT_CAP when `engagementCap > 0 && engagementRaw >= engagementCap`
  3. SIGN_CLAMP / WEIGHT_RANGE on `base + learned`, where learned is thumbs + engagement
  4. NONE

  Six unit tests cover the boundary and precedence cases, and one HTTP test reaches the cap with 8 starred articles.
- `ArticleFeedbackService` reads `getEngagement().getCap()` in both `learnedTopics()` and `applyAndReport`.
- `TopicLearned` and `FeedbackResult.TopicEffect` add `thumbsLearned` and `engagementLearned` after `limit`. `learned` stays the combined capped value (D-15). Existing JSON keys are unchanged, and the frontend types from 09-02 already declare both keys as optional.
- The vote effect still reads both `before` and `after` through `queries.topicWeights` inside the write's transaction, so D-12 holds without any added query. `voteEffectBeforeIncludesTheEngagementItReplaces` asserts the exact values both ways.

## Task Commits

1. **Task 1 (tracer): ENGAGEMENT_CAP with the D-11 precedence:** `b66d250` (feat)
   - RED was observed (compile failure on the three-argument `of` and the new constant) and was not committed.
   - Tracer gate: the run was interactive and end-of-phase, and the verify was automated only. The verify and its greps were re-run green before expansion.
2. **Task 2 (TDD): learned endpoint split:**
   - RED `288e13d` (test)
   - GREEN `9f1d4d2` (feat)
3. **Task 3 (TDD): vote effect split and the D-12 proof:**
   - RED `3d5647b` (test)
   - GREEN `9e99cb4` (feat)

No refactor commits were needed.

**Plan metadata:** committed with this SUMMARY (docs)

## TDD Gate Compliance

- **Task 2.** RED `288e13d`: `learnedEndpointSplitsVotesAndEngagement()` failed with `expected: 1.8 but was: 0.0`. `check tdd-red-evidence` returned `RED_EVIDENCE_OK` (`target_test_failed`). GREEN `9f1d4d2`: 20 + 12 + 20 tests pass.
- **Task 3.** RED `3d5647b`: `voteEffectBeforeIncludesTheEngagementItReplaces()` failed on `thumbsLearned` (`expected: 1.8 but was: 0.0`). Its before/after assertions (20.9 → 21.8) already passed, which confirms that D-12 holds by construction. `check tdd-red-evidence` returned `RED_EVIDENCE_OK`. GREEN `9e99cb4`: all four classes pass.
- **Full backend suite** (`./gradlew test -x npmBuild -x npmInstall`, with no `SPRING_PROFILES_ACTIVE` or `SPRING_AI_TYPESAFE_*` set): 647 tests, 0 failures, 0 errors, and the 2 existing live-Jev skips.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/service/LearnedLimit.java`: `ENGAGEMENT_CAP`, the three-argument `of`, and a javadoc listing the steps in order.
- `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java`: reads the engagement cap and builds `TopicLearned` / `TopicEffect` with the split parts.
- `src/main/java/org/bartram/myfeeder/service/TopicLearned.java`: appends `thumbsLearned` and `engagementLearned`, with javadoc.
- `src/main/java/org/bartram/myfeeder/service/FeedbackResult.java`: appends `thumbsLearned` and `engagementLearned` to `TopicEffect`, with javadoc (D-12).
- `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java`:
  - a 5-argument `weight(...)` helper
  - the 10 existing `of` calls now pass `, 20, 8`
  - six new precedence tests
  - two `TopicEffect` expectations appended
- `src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java`:
  - helpers `insertEngagement`, `assertLearnedParts` and `assertEffectParts`
  - three new tests
  - split checks added to `learnedEndpointMatchesTheVoteEffects`
- `src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java`: 7-argument `TopicLearned` and two new jsonPath checks.
- `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java`: 9-argument `TopicEffect` and two new jsonPath checks.

## Decisions Made

- The two-argument `LearnedLimit.of` is removed, not kept as an overload. This way no caller can silently skip the engagement check.
- The RED commits for Tasks 2 and 3 include the record shape, with the service passing `0, 0`. That makes RED an assertion failure, which `check tdd-red-evidence` accepts, instead of a compile failure (INVALID_RED). The GREEN commits replace the placeholders.

## Deviations from Plan

None: the plan was executed as written.

- The plan-commit ledger was not written into the git dir, because this worktree sandbox refuses git-dir writes, as it did for earlier plans. The base commit `c24533c` is recorded as `plan_head_before` instead. The 5 commits were counted with `git log c24533c..HEAD`.

## Issues Encountered

None.

## Known Stubs

None. The `0, 0` RED placeholders were replaced in GREEN commits `9f1d4d2` and `9e99cb4`.

## User Setup Required

None: no external service configuration required.

## Next Phase Readiness

- Phase 10 only has to render `thumbsLearned` / `engagementLearned` and word `ENGAGEMENT_CAP` in the toast note and the learned line (D-11). The backend already produces all three.
- No release (D-14).

---
*Phase: 09-engagement-learning-model*
*Completed: 2026-09-30*

## Self-Check: PASSED

- The 4 modified main sources and 4 modified tests exist, and the plan-level acceptance greps pass.
- Commits exist: b66d250, 288e13d, 9f1d4d2, 3d5647b, 9e99cb4.
- The full backend suite is green: 647 tests, 0 failures.
