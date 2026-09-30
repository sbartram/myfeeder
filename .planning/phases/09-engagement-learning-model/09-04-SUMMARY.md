---
phase: 09-engagement-learning-model
plan: 04
subsystem: testing
tags: [postgres, testcontainers, data-jdbc-test, learned-model, engagement, v0.2.1-parity]

# Dependency graph
requires:
  - phase: 09-engagement-learning-model
    provides: "09-01 extended LEARNED_CTE (engaged / eng_learned / eng with zero floor), Blend.Engagement properties and the engagement bindings"
provides:
  - "InterestScoreQueriesEngagementTest: 15 real-Postgres proofs of LRN-01/02/03, D-08, additive caps, the rising badge and read-only idempotency"
  - "src/test/resources/interest/v021-unread-blend.sql: the v0.2.1 unread blend frozen byte for byte from tag v0.2.1 (replay line 30)"
  - "InterestScoreQueriesZeroEngagementTest: 5 D-05/LRN-05 proofs that cap 0, absent rows, and cap 0 with negative weights reproduce v0.2.1 raws, badges, eff2 w and Priority order exactly"
affects: [09-05, 09-06, 12]

# Actuals (#2632)
actuals:
  tokens: 7440
  tasks: 2
  commits: 2
plan_head_before: 562ceadb7d1f9ac1877f2a32332ab0def43c16f5
plan_head_after: 904e58029cbed12e0fed7e8160554ec07510ac16

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Parity proof against frozen SQL: copy the old query text from an immutable git tag into a test resource and compare the new query's results with it, rather than with the new code's own text"
    - "Tests build InterestScoreQueries with their own MyfeederProperties (D-04), so a yaml tuning never moves these proofs"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java
    - src/test/resources/interest/v021-unread-blend.sql
  modified: []

key-decisions:
  - "The frozen file is line 30 of scripts/interest-calibration-replay.sql at tag v0.2.1, written by git show + sed, never typed; it is also byte-identical to the pre-Phase-9 commit 8f062f4"
  - "The frozen side binds only learnRate (2.0, double), learnedCap (20, int) and profilePoints (100, int), with the same Java types the app binds, so type coercion cannot differ between the two sides"
  - "Only effective(), badges, raws, Priority ids and whole-map equality are asserted, never learned()/learnedRaw() or 09-03's split components"

patterns-established:
  - "Mutation check for proof-only tests: temporarily break the production SQL, confirm a named test fails on an assertion, then restore the file with git checkout -- <file>"

requirements-completed: [LRN-01, LRN-02, LRN-03, LRN-05]

coverage:
  - id: D1
    description: "An engaged SCORED article lifts the topics it matched once, at its strongest kind (open 20.45, save 20.9, two saves 21.8, four kinds on one article 20.9)"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java#anOpenAddsOpenWeightTimesHinge, aSaveCountsMoreThanAnOpen, everyKindOnOneArticleCountsOnceAsASave, eachEngagedArticleCountsOnce"
        status: pass
    human_judgment: false
  - id: D2
    description: "Only SCORED articles count; an engaged article with no topic scores adds nothing; read articles 400 days old still count (D-08)"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java#onlyScoredArticlesCount, anEngagedArticleWithoutTopicScoresAddsNothing, readAndOldArticlesStillCount"
        status: pass
    human_judgment: false
  - id: D3
    description: "Any vote (up, down, narrowed with or without picks) replaces the article's engagement on every topic; removing the vote, or forgetting the engagement, restores the weight map exactly"
    requirement: LRN-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java#anyVoteReplacesTheArticlesEngagementOnEveryTopic, removingTheVoteRestoresTheEngagementExactly, forgettingEngagementRestoresThePreEngagementWeights"
        status: pass
    human_judgment: false
  - id: D4
    description: "A negative-base topic is never nudged, while a zero-base topic reaches 0.9"
    requirement: LRN-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java#negativeBaseIsNeverNudged, zeroBaseQualifies"
        status: pass
    human_judgment: false
  - id: D5
    description: "Thumbs and engagement add within their own caps (38); saving one article raises another matching article's badge 68 to 69; two reads are equal and write nothing"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java#thumbsAndEngagementAddWithinTheirOwnCaps, savingOneArticleRaisesAnotherMatchingArticlesBadge, readingTwiceWritesNothing"
        status: pass
    human_judgment: false
  - id: D6
    description: "Engagement at zero or absent reproduces the frozen v0.2.1 SQL exactly (raws, badges, bit-identical eff2 w, Priority order including ties), also at cap 0 with negative weights; non-vacuous at cap 8; switching the cap back restores exactly"
    requirement: LRN-05
    verification:
      - kind: other
        ref: "git show v0.2.1:scripts/interest-calibration-replay.sql > build/v021-replay.sql; sed -n '30p' build/v021-replay.sql | cmp - src/test/resources/interest/v021-unread-blend.sql"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java (5 tests)"
        status: pass
      - kind: integration
        ref: "./gradlew test -x npmBuild -x npmInstall (633 tests, 0 failures, 2 pre-existing skips)"
        status: pass
    human_judgment: false

# Metrics
duration: 5min
completed: 2026-09-30
status: complete
---

# Phase 9 Plan 04: Engagement Learning Proofs Summary

**Twenty real-Postgres tests prove the engagement part of the learned model. Fifteen cover LRN-01/02/03: one MAX strength per article, SCORED only, vote override with exact restore, the negative-base skip, additive caps and the rising badge. Five compare against the v0.2.1 unread blend, frozen byte for byte from the `v0.2.1` tag: at cap 0, with no rows, and at cap 0 with negative weights, every raw, badge, `eff2.w` and the Priority order match exactly.**

## Performance

- **Duration:** about 5 min
- **Started:** 2026-09-30T16:26:40Z
- **Completed:** 2026-09-30T16:31:32Z
- **Tasks:** 2
- **Files modified:** 3 (all created)

## Accomplishments

- **LRN-01 values.** With learnRate 2, open 0.25, save 0.5 and cap 8, rust (base 20, noul 0.95) goes to:
  - 20.45 after one open
  - 20.9 after one save, and also when one article carries all four kinds
  - 21.8 after saves on two articles

  FAILED, SKIPPED and unscored engaged articles add nothing. A read article published 400 days ago still counts (D-08).
- **LRN-02, vote override.** One saved article matches rust and go. An up vote moves them to 21.8 / 11.8, and a down vote to 18.2 / 8.2. A narrowed up vote that picks rust gives 21.8 / 10.0, and a narrowed vote with no picks gives 20.0 / 10.0. Deleting the vote, or deleting the engagement rows, restores the `topicWeights` map exactly.
- **LRN-03, sign rule.** Politics (base -10) stays -10 after three saves. Zero (base 0) reaches 0.9.
- **Caps are additive.** Go reaches 38: base 10, thumbs 24 capped at 20, engagement 10 capped at 8.
- **SC-1 badge.** Saving a different rust article raises an unengaged article's badge from 68 to 69.
- **Idempotency.** Two `allTopicWeights()` reads are equal. They leave the `article_engagement` row count, the sum of `interest_topic.weight` and the `article_score` row count unchanged.
- **D-05 / LRN-05.** The frozen file is replay line 30 at `v0.2.1`, and it passes the plan's `cmp` check. The comparisons against the frozen SQL:
  - Raws: `compareTo` per id, over the same key set.
  - Badges and Priority ids: `isEqualTo`. The Priority ids include a1/a3, which tie on raw and date.
  - `eff2.w`: exact `Double` equality.
  - `engagementIsActiveAtCapEight` shows the comparisons are not vacuous.

## Task Commits

1. **Task 1 (tracer): engagement behaviors against real Postgres:** `0877325` (test).
   - Tracer gate: in interactive `end-of-phase` mode with an automated-only verify, the verify was re-run and passed (15/15). The run continued with no checkpoint.
2. **Task 2 (TDD): zero rule against frozen v0.2.1 SQL:** `904e580` (test).

**Plan metadata:** committed with this SUMMARY (docs).

## TDD Gate Compliance

Task 2 carries `tdd="true"` but is test-only (the files are the resource and the test class). The behavior it proves was implemented by 09-01, and this plan may not edit `InterestScoreQueries.java`. So there is no RED-to-GREEN transition and no `feat(09-04)` commit.

- **First run:** all 5 tests passed. This is the fail-fast rule's "unexpected GREEN" case. It is explained because the feature already exists; the test is not wrong.
- **Mutation check (not committed).** The zero floor was removed from `LEARNED_CTE` (`GREATEST(0, LEAST(:engagementCap, ...))` became `(LEAST(...))`). `capZeroWithNegativeWeightsEqualsV021` then failed on its target assertion: `[raw of 48: 120.678000 vs 136.671600] expected: 0 but was: -1`. The source was restored with `git checkout -- src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java`, and `git status` was clean afterwards.
- **Task 1** is a tracer, not TDD. Its 15 tests prove the behavior 09-01 shipped, and they passed on the first run.

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java`: 15 tests. It builds its own `InterestScoreQueries` (2 / 20 / 100 / 0.25 / 0.5 / 8) over topics rust 20, go 10, zero 0 and politics -10.
- `src/test/resources/interest/v021-unread-blend.sql`: one line, the v0.2.1 `blendCte(UNREAD_SCOPE)`, written by `git show v0.2.1:... | sed -n '30p'`.
- `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java`: 5 tests.
  - It uses the InterestScoreQueriesTest fixture shape: eight SCORED unread articles, r1 read, u1 unscored and u2 FAILED.
  - It adds an up vote on a4 and a narrowed down vote on a6 that picks WebAssembly.
  - Engagement: STAR and OPEN on a1, OPEN on a2, BOARD on a5 (Politics), RAINDROP on the up-voted a4, STAR on the FAILED u2, and OPEN on the unscored u1.

## Decisions Made

- The frozen side binds the three v0.2.1 parameters with the Java types the app binds (double learnRate, int learnedCap, int profilePoints), so the comparison cannot hide a coercion difference.
- The plan's `insertMatchingArticles` helper became `matching(topicId, noul)`, with a per-test guid counter. Calling it several times for one topic then cannot collide on the feed's guid.

## Deviations from Plan

None in behavior or scope.

- **[Environment] Plan commit ledger not written.** The worktree sandbox refused the write to `$(git rev-parse --git-dir)/gsd-plan-head-before-09-04`, as 09-01 had predicted. The base `562ceadb7d1f9ac1877f2a32332ab0def43c16f5` is recorded above as `plan_head_before`, and `commits: 2` was measured with `git rev-list --count 562cead..HEAD`.

**Total deviations:** 0 code deviations.
**Impact on plan:** none.

## Issues Encountered

None.

## Observations for the verifier

- **Bit-identity has one theoretical exception: the sign of zero.** `x + 0.0 == x` in IEEE except for `-0.0 + 0.0 = +0.0`. Take a zero-base topic whose only votes are down votes on articles where that topic's hinge is 0 (noul <= 0.5). Its v0.2.1 `w` would be `-0.0`, and the new `w` would be `+0.0`. The two are numerically equal and no raw, badge or order can differ, but Java `Double.equals` would tell them apart. This fixture does not produce that case, and no test asserts it.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- The proofs are ready to guard 09-03 (the split columns), 09-05 and Phase 12 calibration. They assert only effective weights, badges, raws and order, so 09-03's split does not affect them.
- 09-03 runs in parallel in another worktree and was not touched. After the merge, the whole suite should be re-run once.

---
*Phase: 09-engagement-learning-model*
*Completed: 2026-09-30*

## Self-Check: PASSED

- Files exist: InterestScoreQueriesEngagementTest.java, InterestScoreQueriesZeroEngagementTest.java, interest/v021-unread-blend.sql
- Commits exist: 0877325, 904e580
- Full backend suite green (633 tests, 0 failures, 2 pre-existing skips); cmp check passes
