---
phase: 09-engagement-learning-model
verified: 2026-09-30T17:04:00Z
status: passed
score: 60/62 must-haves verified (5/5 roadmap success criteria; 55/57 plan truths, 2 backstop truths routed to human)
covered_files:
  - .planning/phases/09-engagement-learning-model/09-01-PLAN.md
  - .planning/phases/09-engagement-learning-model/09-01-SUMMARY.md
  - .planning/phases/09-engagement-learning-model/09-02-PLAN.md
  - .planning/phases/09-engagement-learning-model/09-02-SUMMARY.md
  - .planning/phases/09-engagement-learning-model/09-03-PLAN.md
  - .planning/phases/09-engagement-learning-model/09-03-SUMMARY.md
  - .planning/phases/09-engagement-learning-model/09-04-PLAN.md
  - .planning/phases/09-engagement-learning-model/09-04-SUMMARY.md
  - .planning/phases/09-engagement-learning-model/09-05-PLAN.md
  - .planning/phases/09-engagement-learning-model/09-05-SUMMARY.md
  - .planning/phases/09-engagement-learning-model/09-06-PLAN.md
  - .planning/phases/09-engagement-learning-model/09-06-SUMMARY.md
  - CLAUDE.md
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/types/index.ts
  - src/main/frontend/src/utils/feedback.test.ts
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
  - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
  - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
  - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
  - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java
  - src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
  - src/test/resources/application.yaml
  - src/test/resources/interest/v021-unread-blend.sql

covered_digest: "v2:sha256:2ca954b2a6c2b3a9225fe1dc97f75bddb64b9f90406d384cd678f66370bf44d8"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Backstop truth (09-01, LRN-05 concurrency): confirm that nothing reloads myfeeder.interest.blend.engagement.* at runtime"
    expected: "Validation runs once in the binder at context start; no runtime path rebinds MyfeederProperties, so no request can see a constant change between the statements of one read"
    why_human: "verification: backstop (non-inferable). Tests prove refused configs fail context start, but no test proves the absence of a runtime reload. Note: spring-cloud-context 5.0.3 is on the runtime classpath (via the circuit-breaker starter), so ConfigurationPropertiesRebinder exists; no trigger is exposed (no management.endpoints exposure configured, so web exposure is Boot's default health only). InterestScoreQueries reads the properties on every call, so a rebind would apply mid-flight between the two breakdown statements."
  - test: "Backstop truth (09-04, LRN-01 concurrency): accept that every learned-model read is a single read-only statement under one READ COMMITTED snapshot"
    expected: "topicWeights/allTopicWeights/priority/badge reads are one statement each; breakdownInputs stays two statements (the race already recorded as 06-REVIEW WR-03)"
    why_human: "verification: backstop. Confirmed by code inspection (one jdbc.sql per read; breakdown uses two), but no concurrency test exercises a read racing an engagement insert or vote."
  - test: "Flagged prohibition (09-01, test tier): 'MUST NOT tune the app's engagement constants through Helm --set or environment overrides'"
    expected: "helm/myfeeder and deploy.sh carry no engagement key, and the operator agrees not to set MYFEEDER_INTEREST_BLEND_ENGAGEMENT_* out of band"
    why_human: "unverified-prohibition, human review recommended. grep of helm/ and deploy.sh finds no engagement key today, but no test enforces the prohibition, and Spring relaxed binding still accepts an env override."
---

# Phase 9: Engagement Learning Model Verification Report

**Phase Goal:** Engagement on scored articles nudges the topics they matched as a small, capped, thumbs-overridable implicit up-vote, derived at query time with no Jev calls and no writes to topic weights, and the calibration replay still reproduces the app exactly
**Verified:** 2026-09-30T17:04:00Z
**Status:** human_needed
**Re-verification:** No, initial verification

## Goal Achievement

The goal is achieved in code. Every roadmap success criterion is backed by a passing behavioral test that I ran myself on this branch. The status is `human_needed` only because the protocol requires it: two plan truths are tagged `verification: backstop`, and one test-tier prohibition has no wired test. None of these is a defect I observed.

### Observable Truths: Roadmap Success Criteria

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC-1 | Opening or saving a scored article raises the effective weight of each non-negative topic it matched. Matching articles' scores rise on refresh, with no Jev call and no stored-weight change. Each article counts once at its strongest kind (save > open), and open + star + boards + Raindrop counts once as a save. | ✓ VERIFIED | `InterestScoreQueries.LEARNED_CTE`: `engaged` takes MAX(strength) per article before the topic join (OPEN_ORIGINAL→open weight, else save weight); `eng_learned` joins `status = 'SCORED'` only. Tests (run): `EngagementApiIntegrationTest.engagementRaisesTheRankingWithoutWritingWeightsOrCallingJev` (HTTP: badge 68→69, weight 20.0→20.9, `interest_topic.weight` stays 20, `verify(jevApiClient, never()).judge`); `InterestScoreQueriesEngagementTest.everyKindOnOneArticleCountsOnceAsASave`, `aSaveCountsMoreThanAnOpen`, `eachEngagedArticleCountsOnce`, `onlyScoredArticlesCount`, `savingOneArticleRaisesAnotherMatchingArticlesBadge`, `readingTwiceWritesNothing`. |
| SC-2 | A thumbs vote on an engaged article replaces its engagement on every topic, and removing the vote restores it. Engagement on an unscored article or a negative-base topic adds nothing. | ✓ VERIFIED | `engaged` has `WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id)`; `eff` zeroes `eng_raw`/`eng` when `t.weight < 0`. Tests: `anyVoteReplacesTheArticlesEngagementOnEveryTopic` (up, down, narrowed with a pick, narrowed with no picks), `removingTheVoteRestoresTheEngagementExactly`, `negativeBaseIsNeverNudged`, `zeroBaseQualifies`, `onlyScoredArticlesCount`; `FeedbackApiIntegrationTest.voteEffectBeforeIncludesTheEngagementItReplaces`. |
| SC-3 | Engagement points have their own cap (below 20) and add on top of the thumbs points. Every effective weight stays inside the sign clamp and ±50, and base + thumbs + engagement = effective exactly across a grid. | ✓ VERIFIED | `eff2` applies one sign clamp and one ±50 bound to `base + learned + eng`, and keeps the v0.2.1 expression as `w_thumbs` (compared against `git show v0.2.1`). The split is computed by subtracting 6-decimal rounded values in SQL. `InterestLearnedGridTest` (3 tests, 1,456 cells, BigDecimal oracle, every clamp branch hit) passed; `thumbsAndEngagementAddWithinTheirOwnCaps` (38.0). |
| SC-4 | Open weight, save weight and cap come from committed yaml. With engagement at zero, scores and Priority order match v0.2.1 exactly. The app refuses to start when save ≤ open or cap ≥ thumbs cap. | ✓ VERIFIED | Main and test `application.yaml` both set `engagement: open-weight 0.25 / save-weight 0.5 / cap 8`. `MyfeederProperties implements Validator` with rule `cap == 0 \|\| (0 <= open < save < 1 && 0 < cap < learnedCap)`. `MyfeederPropertiesValidationTest` (12 tests, including the shipped main yaml, NaN handling and the fixed refusal text) passed. `InterestScoreQueriesZeroEngagementTest` (5 tests) runs `v021-unread-blend.sql`, which I confirmed is byte-identical (`cmp`) to line 30 of the replay at tag `v0.2.1`; raws, badges, eff2 w and Priority ids are equal at cap 0, and also with negative weights at cap 0. |
| SC-5 | The replay is regenerated and reproduces the app with engagement present. The drift guard fails on any divergence. The extended CTE stays within a measured latency budget at about 20k engagement rows. | ✓ VERIFIED | The 5 `WITH learned AS` replay lines contain the extended CTE, and the driver passes `-v engagementOpenWeight/engagementSaveWeight/engagementCap`. `InterestCalibrationReplaySqlTest` (14 tests) passed, including `driverPassesEveryBlendParameter` (exactly the 6 bind names) and `driftInAnySingleBlendCopyFails` (unread ×3, window, learned). `InterestScoreQueriesLatencyTest` passed in my run: `LATENCY priority baseline=29.7 extended=45.5 bound=547.3` with ~20k rows. The replay is never executed against a database in tests; reproduction rests on byte-equal text plus identical parameter names and values. That is the established Phase 7 contract. |

### Plan Must-Have Truths (merged)

| Plan | Truths | Status | Notes |
|------|--------|--------|-------|
| 09-01 | 13 | 12 ✓, 1 backstop → human | LEARNED_CTE starts with `WITH learned AS`; the `learned` CTE is textually identical to v0.2.1; bindings in `learnedSql`; the V7 guard is inverted to `rankingSqlReadsEngagementButNotDismissals` (passed); the dev overlay is untouched (`DevProfileConfigTest` passed); driver rejects bad ENGAGEMENT_* with exit 2 (test passed). The concurrency truth is `backstop`. |
| 09-02 | 5 | 5 ✓ | The `LearnedLimit` union includes `'ENGAGEMENT_CAP'`; optional split fields are on TopicEffect, TopicBreakdownRow and TopicLearned; `npx tsc -b` is clean; `feedback.test.ts` + `TopicRow.test.tsx` 40/40; the full vitest run is 348/348. |
| 09-03 | 8 | 8 ✓ | Grid oracle, worked cells, clamp-branch coverage, exact BigDecimal split; `TopicWeight`/`TopicContribution`/`InterestBreakdown.Row` fields are appended; `whyBreakdownCarriesTheEngagementPart` (HTTP: 20.0 / 0.9 / 0.0 / 0.9 / 20.9, PROFILE row omits the split keys). |
| 09-04 | 14 | 13 ✓, 1 backstop → human | All behavior proofs above; 400-day-old read article counts (`readAndOldArticlesStillCount`); cap 8→0→8 restores exactly (`switchingTheCapBackRestoresExactly`). |
| 09-05 | 8 | 8 ✓ | `LearnedLimit.of(w, learnedCap, engagementCap)` precedence; `ArticleFeedbackServiceTest` (20) and `FeedbackApiIntegrationTest` (21, incl. `learnedEndpointReportsTheEngagementCap`, `learnedEndpointSplitsVotesAndEngagement`) passed. |
| 09-06 | 9 | 9 ✓ | Latency test and record present; `git diff main..HEAD` of `src/main/resources/db/migration` and `ArticleScoreStore.java` is empty; `git tag --contains HEAD` is empty (no release, D-14); CLAUDE.md documents the model and names the inverted guard; the stale "does not read engagement until Phase 9" claim is gone; the `ArticleEngagementStore` javadoc is updated; full suite green (below). |

**Score:** 60/62 must-haves verified (0 present-but-behavior-unverified; 2 backstop truths are `insufficient_spec` and routed to human).

### Prohibitions

| Prohibition | Tier | Disposition |
|-------------|------|-------------|
| No Jev call, no weight/score write because of engagement | test | ✓ enforced (`engagementRaisesTheRanking…` `never().judge`, weight stays 20; `readingTwiceWritesNothing`) |
| No tuning via Helm `--set` / env overrides | test | ⚠️ UNVERIFIED, flagged: no wired test. grep of `helm/` and `deploy.sh` finds no engagement key |
| Client never computes learned/split/effective weights | test | ✓ Only type files changed in non-test frontend code; `effectNote`/`formatVoteToast`/`LearnedLine` fall-through is pinned in tests |
| Engagement never lifts a negative-base topic or lowers any weight | test | ✓ grid (`engagement applied >= 0` on every cell), `negativeBaseIsNeverNudged` |
| A vote overrides engagement; removal restores it | test | ✓ `anyVoteReplaces…`, `removingTheVoteRestores…` |
| Zero floor with cap 0 and negative weights | test | ✓ `capZeroWithNegativeWeightsEqualsV021` |
| Vote effect `before` includes the replaced engagement | test | ✓ `voteEffectBeforeIncludesTheEngagementItReplaces` |
| No release/tag/image/deploy in Phase 9 | test | ✓ Observed directly: no tag contains HEAD, no helm/deploy/Dockerfile diff |
| No contrib materialization hint, index or migration | test | ✓ Observed directly: migration dir unchanged; `NOT MATERIALIZED` is absent |
| Engaged-but-unscored stays ineligible for scoring | test | ✓ Observed directly: `ArticleScoreStore.java` unchanged against main |

### Required Artifacts

`gsd-tools verify.artifacts` passes for all six plans (7/7, 4/4, 4/4, 3/3, 4/4, 3/3). I also read the core artifacts in full or by diff: `InterestScoreQueries.java`, `MyfeederProperties.java`, `LearnedLimit.java`, `ArticleFeedbackService.java`, `TopicLearned.java`, `FeedbackResult.java`, `ScoreBreakdowns.java`, `InterestBreakdown.java`, both replay scripts, both yaml files and the frontend types. They are substantive, not stubs.

### Key Link Verification

`gsd-tools verify.key-links` reports all links WIRED for every plan (4/4, 1/1, 3/3, 2/2, 2/2, 2/2). I traced the critical links by hand:
- yaml → `MyfeederProperties.Interest.Blend.Engagement` (proven by `shippedMainYamlStarts` loading main yaml from disk) → `InterestScoreQueries.learnedSql` binds `engagementOpenWeight/SaveWeight/Cap` → used by every learned read (`topicWeights`, `allTopicWeights`) and every blend read (`blendSql` calls `learnedSql`).
- `ArticleFeedbackService` → `LearnedLimit.of(w, cap, engagementCap)` in both `learnedTopics` and `applyAndReport`, with split fields from `TopicWeight`.
- `ScoreBreakdowns` → `Row.topic(..., t.thumbsWeight(), t.engagementWeight())` → HTTP JSON (proven by `whyBreakdownCarriesTheEngagementPart`).
- Replay `.sh` `-v` names = JdbcClient param names (`driverPassesEveryBlendParameter`).

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Badge / Priority / "Why N?" | `raw_n`, `w`, `w_thumbs` | `article_engagement` + `article_score` + `article_topic_score` + `interest_topic` via LEARNED_CTE/blendCte | Yes (HTTP test sees 68→69 after real PUT/PATCH/board calls) | ✓ FLOWING |
| `/api/interest/topics/learned` | learned, thumbsLearned, engagementLearned, limit | `allTopicWeights()` | Yes (`learnedEndpointReportsTheEngagementCap`) | ✓ FLOWING |
| Vote effects | before/after/split | `topicWeights()` before and after the write | Yes (`voteEffectBeforeIncludes…`) | ✓ FLOWING |

### Behavioral Spot-Checks (run by the verifier)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase 9 backend classes (14 classes) | `./gradlew test --tests …` (no SPRING_PROFILES_ACTIVE / SPRING_AI_TYPESAFE_* in env) | 208 tests, 0 failures; test task executed, not up-to-date | ✓ PASS |
| Full backend suite (once) | `./gradlew test` | 648 tests, 0 failures, 0 errors, 2 skipped (live-Jev) | ✓ PASS |
| Frontend type-check | `npx tsc -b` | clean | ✓ PASS |
| Frontend tests | `npx vitest run` | 32 files, 348 passed | ✓ PASS |
| Frozen v0.2.1 SQL | `git show v0.2.1:scripts/interest-calibration-replay.sql \| sed -n 30p \| cmp - src/test/resources/interest/v021-unread-blend.sql` | identical | ✓ PASS |
| Learned CTE unchanged from v0.2.1 | compared `git show v0.2.1:…InterestScoreQueries.java` | `learned` CTE text identical; v0.2.1 `w` survives verbatim as `w_thumbs` | ✓ PASS |
| Latency guard | `InterestScoreQueriesLatencyTest` output | priority 29.7→45.5 ms (bound 547.3) | ✓ PASS |

### Probe Execution

No `scripts/*/tests/probe-*.sh` exist and no plan declares a probe. SKIPPED.

### Requirements Coverage

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| LRN-01 | 09-01, 09-04, 09-06 | ✓ SATISFIED | SC-1 evidence |
| LRN-02 | 09-04, 09-05 | ✓ SATISFIED | SC-2 evidence; D-12 vote effect |
| LRN-03 | 09-03, 09-04 | ✓ SATISFIED in code, but ⚠️ **tracking not updated** | `eff` zeroes a negative base; grid + `negativeBaseIsNeverNudged` + `zeroBaseQualifies` pass. `.planning/REQUIREMENTS.md` still shows `- [ ] **LRN-03**` and `LRN-03 \| Phase 9 \| Pending`, while LRN-01/02/04/05 and CAL-01 were flipped. |
| LRN-04 | 09-02, 09-03, 09-05 | ✓ SATISFIED | SC-3 evidence |
| LRN-05 | 09-01, 09-04, 09-06 | ✓ SATISFIED | SC-4 evidence |
| CAL-01 | 09-01, 09-06 | ✓ SATISFIED | SC-5 evidence; the CTE edit, replay regeneration and guard inversions landed together per 09-01 |

No orphaned requirements. REQUIREMENTS.md maps exactly LRN-01..05 and CAL-01 to Phase 9, and every one is claimed by a plan. LRN-06 is mapped to Phase 10.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all changed non-planning files) | — | TBD/FIXME/XXX/TODO/HACK | none found | — |
| `service/LearnedLimit.java` | `of(...)` | ENGAGEMENT_CAP checked before SIGN_CLAMP/WEIGHT_RANGE (review WR-01) | ⚠️ Warning | Follows D-11 as planned, but once a topic reaches the engagement cap, its "can't cross 0" / "+50 limit" notes are hidden for good (engagement never decays). No roadmap SC is violated. Phase 10 SC-4 covers only part of this. |
| `scripts/interest-calibration-replay.sh` | 36-41 | Accepts engagement constants the app refuses to start with (review WR-02) | ⚠️ Warning | Phase 12 calibration could record a candidate that fails startup |
| `service/ArticleFeedbackService.java` | 101-113 | A vote that replaces engagement reports a smaller change with `limit NONE` (review WR-03) | ⚠️ Warning | The toast explains nothing. Phase 10 SC-4 ("toast accounts for the engagement the vote replaced") is the natural home. |
| `repository/InterestScoreQueries.java` | engaged CTE | `ELSE save` for any non-open kind (IN-02) | ℹ️ Info | A future engagement kind would silently count as a save. The V7 CHECK constraint prevents that today. |

The review dispositions in `09-REVIEW-DISPOSITION.md` are all still `open` (7/7). None blocks the Phase 9 goal. The user should mark each one fixed, deferred (Phase 10 or 12) or skipped.

### Human Verification Required

1. **Backstop: no runtime reload of the engagement constants (09-01, LRN-05 concurrency)**
   - **Test:** Confirm there is no runtime rebind path for `MyfeederProperties`, or accept the latent one.
   - **Expected:** Constants change only at context start, after validation.
   - **Why human:** A backstop truth can't be proven by presence. Also, `spring-cloud-context` 5.0.3 is on the runtime classpath, so `ConfigurationPropertiesRebinder` exists. No trigger is exposed today (Boot's default web exposure is health only). `InterestScoreQueries` reads the properties on every call, so a rebind would apply between statements.

2. **Backstop: single-snapshot reads (09-04, LRN-01 concurrency)**
   - **Test:** Accept, by code inspection, that each learned read is one statement.
   - **Expected:** A read racing an engagement insert or vote sees the state before or after it. The two-statement breakdown race (06-REVIEW WR-03) is unchanged.
   - **Why human:** It is tagged backstop, and no concurrency test exists.

3. **Flagged prohibition: no Helm/env tuning of engagement constants**
   - **Test:** Confirm the operating rule and, optionally, add a guard test.
   - **Expected:** `helm/myfeeder` and `deploy.sh` stay free of engagement keys (true today), and no `MYFEEDER_INTEREST_BLEND_ENGAGEMENT_*` override is set.
   - **Why human:** It is a test-tier prohibition with no wired enforcement, so it is unverified, not green.

### Gaps Summary

No blocking gaps. The phase goal is achieved:
- Engagement is derived at query time in `LEARNED_CTE`. It is collapsed to one strength per article, overridden by any vote, limited to SCORED articles, capped with a zero floor and skipped for negative bases, and added under one clamp.
- It makes no Jev calls and no writes.
- At zero it reproduces v0.2.1 exactly.
- The replay and drift guard move in lockstep with the CTE.

I ran every claim listed above myself; none rests on SUMMARY.md alone.

Non-blocking follow-ups for the orchestrator or user:
- Flip LRN-03 to complete in `.planning/REQUIREMENTS.md` (checkbox and traceability row). It was missed while its five siblings were updated.
- Record dispositions for review WR-01, WR-02 and WR-03. WR-02 (replay driver mirroring the startup rule) is cheap and belongs before Phase 12 calibration. WR-01 and WR-03 are UI-explanation concerns that fit Phase 10.

---

_Verified: 2026-09-30T17:04:00Z_
_Verifier: Claude (gsd-verifier)_
