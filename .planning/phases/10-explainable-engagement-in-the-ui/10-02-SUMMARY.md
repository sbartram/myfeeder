---
phase: 10-explainable-engagement-in-the-ui
plan: 02
subsystem: api
tags: [spring-boot, interest-ranking, engagement, learned-limit, feedback]

requires:
  - phase: 09-engagement-learning-model
    provides: TopicWeight split fields (learnedRaw, thumbsLearned, engagementRaw, engagementLearned), LearnedLimit.ENGAGEMENT_CAP, vote "before" including replaced engagement (D-12)
provides:
  - "FeedbackResult.TopicEffect.engagementReplaced on every vote effect (D-11, WR-03)"
  - "LearnedLimit.of precedence LEARNED_CAP -> SIGN_CLAMP -> WEIGHT_RANGE -> ENGAGEMENT_CAP -> NONE (D-10, WR-01)"
  - "LearnedLimit.engagementAtCap(TopicWeight, double), the single at-cap rule"
  - "TopicLearned.engagementAtCap on GET /api/interest/topics/learned (D-15)"
affects: [10-03 vote toast and Interests learned line, 10-04 CLAUDE.md LearnedLimit order text]

actuals:
  tokens: 6726     # chars/4 over the realized diff (26,905 chars, 9 files)
  tasks: 3
  commits: 3
plan_head_before: f03d84dd993d2db8a96c15675f4a5d18c865fc34
plan_head_after: b62df170f7931ac3c7d7c4dfa8501bd065117adc

tech-stack:
  added: []
  patterns:
    - "Before/after comparisons of 6-decimal float8 topic-weight values use a 5e-7 tolerance (SIX_DECIMAL_TOLERANCE, differs())"
    - "New JSON booleans are appended as the last record component (append-never-rename)"

key-files:
  created: []
  modified:
    - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
    - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
    - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
    - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
    - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
    - .planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md

key-decisions:
  - "D-11 marker is a boolean engagementReplaced on TopicEffect, not a new LearnedLimit value (A1): LearnedLimit is shared with TopicLearned, which has no before/after"
  - "The thumbs part changing is judged on learnedRaw (uncapped), so a vote landing on an already-capped thumbs sum still counts; LEARNED_CAP's note wins there (D-12)"
  - "A narrowed down vote leaves engagementReplaced false on unpicked topics even though their engagement share goes (Open Question 2, pinned by a test)"
  - "TopicLearned gets engagementAtCap (A4) so the Interests line can say 'at max' even when a binding clamp or range is the reported limit"

patterns-established:
  - "One shared predicate (LearnedLimit.engagementAtCap) feeds both the limit and the response flag, so they never disagree"

requirements-completed: [EXPL-01]

coverage:
  - id: D1
    description: "Vote effects carry engagementReplaced: true when the vote replaced (or its removal restored) the article's engagement share, false for unpicked narrowed topics, flips, unengaged articles and 6-decimal noise"
    requirement: EXPL-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java#anUpVoteOnAnEngagedArticleMarksTheReplacedEngagement, removingTheVoteMarksTheRestoredEngagement, narrowedVoteLeavesUnpickedTopicsUnmarked, sixDecimalNoiseIsNotAReplacement"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#voteEffectBeforeIncludesTheEngagementItReplaces, narrowedDownVoteOnAnEngagedArticleMarksOnlyThePickedTopic, flipOnAnEngagedArticleReplacesNothingMore"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#putFeedbackReturnsTheResult"
        status: pass
    human_judgment: false
  - id: D2
    description: "LearnedLimit.of reports a binding clamp or the +/-50 range before the engagement cap (D-10)"
    requirement: EXPL-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java#clampAndRangeOutrankEngagementCap, engagementAtCapIgnoresPrecedence"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#bindingRangeOutranksTheEngagementCap, learnedEndpointReportsTheEngagementCap"
        status: pass
    human_judgment: false
  - id: D3
    description: "GET /api/interest/topics/learned carries engagementAtCap from the shared rule, whatever limit is reported"
    requirement: EXPL-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java#learnedTopicsAreServed"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#learnedEndpointReportsTheEngagementCap, bindingRangeOutranksTheEngagementCap, learnedEndpointSplitsVotesAndEngagement, learnedEndpointMatchesTheVoteEffects"
        status: pass
      - kind: other
        ref: "./gradlew test -x npmBuild -x npmInstall (656 tests, 0 failures, 2 pre-existing opt-in skips)"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-09-30
status: complete
---

# Phase 10 Plan 02: Explainable vote effects and limit precedence Summary

**Vote effects now flag a replaced or restored engagement share (`engagementReplaced`), `LearnedLimit.of` puts the clamp and ±50 range ahead of the engagement cap, and learned topics carry `engagementAtCap` from one shared rule.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-30T20:26:48Z
- **Completed:** 2026-09-30T20:33:01Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments
- `FeedbackResult.TopicEffect` gets a new last field, `engagementReplaced`. `ArticleFeedbackService.applyAndReport` sets it when both `engagementLearned` and `learnedRaw` change by more than 5e-7 between the before and after reads. It adds no query and doesn't change the transaction. Over HTTP, a starred base-20 article reports 20.9 → 21.8 `true` on PUT and 21.8 → 20.9 `true` on DELETE.
- `LearnedLimit.of` now checks in this order: LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE. The order of the enum constants themselves is unchanged. A base-45 topic whose engagement is at its cap now reports WEIGHT_RANGE (learned 8.0, effective 50.0). A base-20 topic in the same situation still reports ENGAGEMENT_CAP.
- `TopicLearned` gets a new last field, `engagementAtCap`, filled from `LearnedLimit.engagementAtCap`. `of()` uses the same rule, so the limit and the flag can't disagree.
- Phase 9 review findings WR-01 and WR-03 are set to `fixed` in `09-REVIEW-DISPOSITION.md`. The `open:` count went from 7 to 5.

## Task Commits

1. **Task 1 (tracer): engagementReplaced end to end** - `d62dc0a` (feat)
2. **Task 2: D-10 precedence and the shared at-cap rule** - `8b85239` (feat)
3. **Task 3: engagementAtCap on learned topics** - `b62df17` (feat)

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/service/FeedbackResult.java`: new `boolean engagementReplaced` component, plus Javadoc covering D-11, D-12 and the narrowed-vote case.
- `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java`: adds `SIX_DECIMAL_TOLERANCE` and `differs()`, computes the flag in `applyAndReport`, and passes `engagementAtCap` in `learnedTopics`.
- `src/main/java/org/bartram/myfeeder/service/LearnedLimit.java`: new D-10 order, `engagementAtCap()`, and a rewritten class Javadoc.
- `src/main/java/org/bartram/myfeeder/service/TopicLearned.java`: new `boolean engagementAtCap` component. Its Javadoc no longer says "Phase 10 can split" and now describes the flag.
- `src/test/java/.../ArticleFeedbackServiceTest.java`: four engagementReplaced tests. `clampAndRangeOutrankEngagementCap` replaces `engagementCapOutranksClampAndRange`, and `engagementAtCapIgnoresPrecedence` is new.
- `src/test/java/.../FeedbackApiIntegrationTest.java`: new `narrowedDownVoteOnAnEngagedArticleMarksOnlyThePickedTopic`, `flipOnAnEngagedArticleReplacesNothingMore` and `bindingRangeOutranksTheEngagementCap`, new helpers `assertReplaced` and `assertEngagementAtCap`, and flag assertions added to existing tests.
- `src/test/java/.../ArticleControllerTest.java`, `InterestControllerTest.java`: constructors updated to the new signatures, and the JSON checks now cover the new keys.
- `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md`: WR-01 and WR-03 set to `fixed`, and `open:` lowered from 7 to 5.

## Decisions Made
- These follow the plan's recorded interpretations A1, A4 and Open Question 2 (see key-decisions). I made no new decisions.
- `narrowedVoteLeavesUnpickedTopicsUnmarked` has both topics in one call: Rust is picked and flagged `true`, Go is not picked and flagged `false`. The plan's sketch had Go alone. With both, the unit test pins the picked-versus-unpicked contrast directly, the same way the HTTP test does.

## Deviations from Plan

None. The plan ran as written.

## TDD Gate Compliance

This is a `type: execute` plan, and its actions say to commit code, tests and disposition together. So each task has a single `feat(10-02)` commit, with no separate `test(10-02)` commit before it.
- Task 2: I ran the red step first, and it was a valid RED. `clampAndRangeOutrankEngagementCap` failed with "expected: WEIGHT_RANGE but was: ENGAGEMENT_CAP", and `bindingRangeOutranksTheEngagementCap` failed the same way.
- Task 3: the red step couldn't fail on an assertion, because `InterestControllerTest` uses the new 8-argument `TopicLearned` constructor and the test sources don't compile until it exists. The equivalent evidence is that the four integration assertions compare `engagementAtCap` against booleans, and before the change that key was missing (null) from the response.

## Issues Encountered
None.

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- Plan 10-03 (vote toast and Interests learned line) can now read `effects[].engagementReplaced` and `topics/learned[].engagementAtCap`. The frontend `LearnedLimit` union is unchanged.
- Plan 10-04 still has to rewrite CLAUDE.md's sentence on the LearnedLimit order to the D-10 order. This plan deliberately left CLAUDE.md alone.

## Self-Check: PASSED

---
*Phase: 10-explainable-engagement-in-the-ui*
*Completed: 2026-09-30*
