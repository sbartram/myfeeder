---
phase: 10-explainable-engagement-in-the-ui
verified: 2026-09-30T21:05:00Z
status: human_needed
score: 14/14 must-haves verified
covered_files:
  - .planning/phases/10-explainable-engagement-in-the-ui/10-01-PLAN.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-01-SUMMARY.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-02-PLAN.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-02-SUMMARY.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-03-PLAN.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-03-SUMMARY.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-04-PLAN.md
  - .planning/phases/10-explainable-engagement-in-the-ui/10-04-SUMMARY.md
  - CLAUDE.md
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/PriorityList.test.tsx
  - src/main/frontend/src/components/ScoreRow.test.tsx
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/components/TopicRow.tsx
  - src/main/frontend/src/components/WhyBreakdown.test.tsx
  - src/main/frontend/src/components/WhyBreakdown.tsx
  - src/main/frontend/src/hooks/engagementReaction.test.ts
  - src/main/frontend/src/hooks/engagementReaction.ts
  - src/main/frontend/src/hooks/engagementRefresh.test.ts
  - src/main/frontend/src/hooks/useArticles.ts
  - src/main/frontend/src/hooks/useBoards.ts
  - src/main/frontend/src/hooks/useEngagement.test.ts
  - src/main/frontend/src/hooks/useEngagement.ts
  - src/main/frontend/src/hooks/useFeedback.test.ts
  - src/main/frontend/src/hooks/useFeedback.ts
  - src/main/frontend/src/hooks/usePriorityArticles.test.ts
  - src/main/frontend/src/types/index.ts
  - src/main/frontend/src/utils/feedback.test.ts
  - src/main/frontend/src/utils/feedback.ts
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
  - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
  - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
  - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
covered_digest: "v2:sha256:887457a7813e3d0fa1757289b4864c351cad238ba33a21669b69f03c6aa97395"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Manual UAT (harvested from 10-04 Task 2 <human-check>). Run ./gradlew bootTestRun (export MYFEEDER_TYPESAFE_API_KEY for live scoring) and cd src/main/frontend && npm run dev. On /priority pick a scored article: (1) press o; (2) star it, add it to a board with b, save to Raindrop if configured, then Forget; also repeat an open with no score change; (3) press the hint; (4) vote thumbs-up on an engaged article, then remove the vote; (5) open Interests."
    expected: "(1) The tab opens at once, the refresh button reads '↻ Ranking changed — refresh', the row keeps its place, its badge and 'Why N?' update. (2) Each action behaves the same; a repeat open with no score change lights nothing. (3) The list re-ranks; the row's position, its badge in Priority and the feed list, the reading-pane badge and 'Why N?' all agree. (4) The toast reads '(replaces engagement)', and removing the vote reads '(engagement restored)'. (5) A topic's line reads 'Learned … (votes …, engaged …) · Effective weight …'."
    why_human: "Real browser, real router, real Jev-scored data and visual in-place behavior; the component tests use mocked fetch and a MemoryRouter."
  - test: "Resolve judgment-tier prohibition (10-03): 'MUST NOT print a weight or part the client computed; every number shown is a server field, only rounded for display'."
    expected: "Confirm or reject. Non-authoritative LLM verdict: SATISFIED. WhyBreakdown.TopicLabel prints row.thumbsWeight / row.engagementWeight / row.baseWeight / row.weight via formatSigned/formatDelta only; TopicRow.LearnedLine prints thumbsLearned / engagementLearned / learned / effectiveWeight; the toast prints after − before, a delta of two server values that predates Phase 10. No part is derived from another."
    why_human: "Judgment-tier prohibition; per ADR-550 D4 an interactive verify requires explicit human resolution."
  - test: "Decide on code-review WR-01 (engagement refetch vs a thumbs vote on the same article). Reproduce: on /priority press s then u within one GET round trip (throttle the network in devtools to widen the window)."
    expected: "Either accept the race as a known edge, or fix it before shipping (cancel ['article', id] in press()/narrow() and ignore a GET a vote overtook, per 10-REVIEW.md). If the GET resolves after the vote's onSuccess, the reading pane can show the vote unpressed and the Priority row can be patched back to the pre-vote score until the next refresh."
    why_human: "Timing-dependent interleaving; no test exercises 'vote resolves before the reaction's GET'. It does not re-sort Priority and a refresh corrects it, so SC-2/SC-3 hold on the normal path, but it is a user-visible stale state."
  - test: "Decide on code-review WR-02: narrowed thumbs-down on an engaged article, e.g. narrowed to Rust on an article also matching Go."
    expected: "Toast currently reads '👎 Narrowed · Rust −2.7 (replaces engagement) · Go −0.2' — Go's drop (its engagement share leaving) has no note, and removing the vote shows 'Go +0.2' with no '(engagement restored)'. Accept as D-11 written (pinned by narrowedDownVoteOnAnEngagedArticleMarksOnlyThePickedTopic) or change the marker rule/wording."
    why_human: "UX judgment about wording; numbers are correct and match 'Why N?', so SC-4 holds numerically."
  - test: "Decide on code-review WR-04: a topic whose votes and engagement cancel (thumbsLearned −1.5, engagementLearned +1.5)."
    expected: "Interests currently reads 'No learned adjustment yet · Effective weight +20' while 'Why N?' shows '(+20 −1.5 votes +1.5 engaged)'. Accept or fix (take the 'No learned adjustment yet' branch only when both parts round to zero)."
    why_human: "Rare coincidence case; contradicts the goal's 'see how much came from votes and how much from engagement' on the Interests surface only."
  - test: "Decide on board-list badge freshness (verifier finding): star or open an article that sits on a board, refresh Priority, then open that board within 30 s of its last load."
    expected: "BoardArticleList renders InterestBadge from ['boardArticles', boardId], which invalidateAfterLearnedChange and refreshPriority never invalidate, so the board list can show the pre-engagement badge for up to the 30 s staleTime. Accept (the vote path has had the same gap since v0.2.1 and D-07 mirrored it) or add ['boardArticles'] to invalidateAfterLearnedChange."
    why_human: "SC-3 says 'its badge in every article list'; the gap is bounded by the 30 s staleTime and mirrors an accepted pre-existing refresh set, so it needs a scope decision rather than being a clear failure."
---

# Phase 10: Explainable Engagement in the UI Verification Report

**Phase Goal:** The user can see how much of each topic's weight came from votes and how much from engagement, and engaging with an article updates what they see without reshuffling the Priority list they are triaging.
**Verified:** 2026-09-30T21:05:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1: "Why N?" shows each matched topic's effective weight as base + votes + engagement, and point rows still sum exactly to the badge | ✓ VERIFIED | `WhyBreakdown.tsx` TopicLabel reads `row.thumbsWeight ?? 0` / `row.engagementWeight ?? 0`, label `(+base ±votes votes ±engaged engaged)`, zero-rounded parts omitted, tooltip lists every part; rows/points/total logic untouched. Server sends the fields (`ScoreBreakdowns.java:78`, `InterestBreakdown.Row`). `WhyBreakdown.test.tsx` (bothPartsNamedInline, rowsStillSumToTheBadge …) passed in my run; server point-sum = display asserted in `engagedArticleAgreesEverywhereAfterRefresh` (passed) |
| 2 | SC-2: open, star, board add/Read Later, Raindrop save and Forget on Priority never re-sort; hint appears; badge and "Why N?" update in place; refresh applies the new order | ✓ VERIFIED | `engagementReaction.ts` afterEngagement: `qc.query({staleTime:0, retry:false})` by id, then `patchPriorityArticle` + `setRankingChanged(true)` only when the score differs; `['priority']` never invalidated. Wired in `useEngagement.ts` (open, Forget), `useArticles.ts` (star when `starred === true`, Raindrop), `useBoards.ts` (board add, Read Later); every entry point (ReadingPane buttons, `o`/`s`/`v` shortcuts, `b`→BoardManager, ScoreRow Forget) goes through these hooks. Tests passed in my run: `openingLightsTheHintAndPatchesTheBadgeWithoutReordering` (titles order unchanged, badge 95, one Priority GET), `starOnPriorityPatchesTheScoreAndLightsTheHint`, `onPriorityEachSavePatchesTheRowWhenTheScoreChanges` (3 saves), `forgetOnPriorityPatchesTheRowAndLightsTheHint`, `neverInvalidatesResetsOrRefetchesPriority`, `noSaveTouchesPriority`. Refresh = existing `refreshPriority` (reset + by-id invalidation). See WR-01 warning |
| 3 | SC-3: after a refresh, an engaged article's Priority position, badges and "Why N?" agree | ✓ VERIFIED | `PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh` (ran, passed): 68 → 69 after star PATCH → 68 after Forget DELETE across by-id, breakdown display, breakdown point sum, feed list and full Priority walk; engagementWeight 0.9 / 0.0; peer/engaged order flips and flips back. See board-list freshness warning |
| 4 | SC-4: a thumbs vote on an engaged article shows an effect toast whose before/after account for the replaced engagement, matching "Why N?" | ✓ VERIFIED | `FeedbackApiIntegrationTest.voteEffectBeforeIncludesTheEngagementItReplaces` (ran, passed): PUT 20.9 → 21.8 engagementReplaced true; DELETE 21.8 → 20.9 true. The engaged "Why N?" weight for the same fixture is 20.9 (`EngagementApiIntegrationTest.whyBreakdownCarriesTheEngagementPart` / `engagementRaisesTheRanking…`, passed). Toast wording `👍 Rust +0.9 (replaces engagement)` / `Vote removed · Rust −0.9 (engagement restored)` pinned in `feedback.test.ts` and hook-level `useFeedback.test.ts` (passed) |
| 5 | Equal refetched score (repeat open, unscored, already-voted) patches nothing and lights nothing | ✓ VERIFIED | `anEqualScoreChangesNothing`, `aRepeatOpenWithTheSameScoreLightsNothing` (passed) |
| 6 | Unloaded row / off Priority patches nothing; off Priority refreshes other by-id articles but not `['article', n, 'extracted']` | ✓ VERIFIED | `anUnloadedRowChangesNothing`, `offPriorityPatchesNothingAndRefreshesOtherOpenArticles`, `onPriorityLeavesOtherOpenArticlesAlone` (passed) |
| 7 | Open stays fire-and-forget; failed PUT runs no reaction; failures never toast | ✓ VERIFIED | `useEngagement.ts` window.open precedes `recordOpen`, trailing `.catch(()=>{})`; tests `opensFirstThenSendsABodylessPut`, `aFailedRefetchIsSilent`, `aFailedRefetchIsSilentAndNeverRejects` (passed) |
| 8 | Unstar, read toggles, board removal and failed saves run no reaction | ✓ VERIFIED | `useRemoveArticleFromBoard` unchanged; star gated on `starred === true`; `unstarAndReadDoNotReact`, `boardRemovalRunsNoReaction`, `aFailedSaveRunsNoReaction` (passed) |
| 9 | Pending vote on the same article skips the Priority compare; vote hook shares the refresh set unchanged | ✓ VERIFIED | `isMutating({mutationKey:['feedback'], …}) > 0` guard; `useFeedback.ts` calls `invalidateAfterLearnedChange(qc, v.id, v.onPriority)`; `aPendingVoteOnTheSameArticleSkipsThePatch` and all `useFeedback.test.ts` cases passed |
| 10 | TopicEffect.engagementReplaced appended and computed from before/after (D-11, WR-03) | ✓ VERIFIED | `FeedbackResult.java:32-34` last component `boolean engagementReplaced`; `ArticleFeedbackService.java:119-123` `differs(engagementLearned) && differs(learnedRaw)`; ArticleFeedbackServiceTest (25 tests) passed |
| 11 | LearnedLimit precedence LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE; shared `engagementAtCap` rule (D-10, WR-01) | ✓ VERIFIED | `LearnedLimit.java` order confirmed; enum constant order unchanged; `bindingRangeOutranksTheEngagementCap` (ran, passed) |
| 12 | TopicLearned.engagementAtCap on GET /api/interest/topics/learned; Interests line reads `Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5`, "at max" for capped engagement (D-14, D-15) | ✓ VERIFIED | `TopicLearned.java` last component; `ArticleFeedbackService.java:83-85`; `TopicRow.tsx` LearnedLine reads `learned.engagementAtCap ?? limit === 'ENGAGEMENT_CAP'`; `TopicRow.test.tsx` passed. See WR-04 warning |
| 13 | Toast: binding limit note wins; ENGAGEMENT_CAP never worded and never lists a topic alone (D-12, D-13) | ✓ VERIFIED | `feedback.ts` effectNote default branch + filter excludes ENGAGEMENT_CAP; `engagementCapIsNeverWorded`, `engagementCapAloneIsNotListed`, `aBindingLimitWinsOverTheReplacedNote` (passed) |
| 14 | priorityPageAfter Javadoc states the skip limit; CLAUDE.md/STATE.md/09 dispositions updated (D-09, IN-04) | ✓ VERIFIED | `InterestScoreQueries.java:187` "is not served until the user refreshes…"; CLAUDE.md contains afterEngagement, invalidateAfterLearnedChange, the D-10 order, engagementReplaced, engagementAtCap, "(replaces engagement)", and no longer "until Phase 10 words it"; 09-REVIEW-DISPOSITION WR-01/WR-03/IN-01/IN-04 = fixed; STATE.md line 92 updated |

**Score:** 14/14 truths verified (0 present, behavior-unverified)

### Scope reconciliation (ENG-F6)

ROADMAP Phase 10 notes list "the Interests learned line split into votes and engagement (ENG-F6)" as deferred. 10-CONTEXT.md D-14 (a locked decision) pulls the basic split into this phase and keeps ENG-F6's contributing-article counts and richer layout deferred; REQUIREMENTS.md ENG-F6 still lists those counts as future work. Plan 10-03 implements exactly the D-14 subset. This is authorized scope, not creep. Suggest updating the ROADMAP note to say "ENG-F6 (narrowed: article counts and richer layout)" when the milestone is closed.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/frontend/src/hooks/engagementReaction.ts` | invalidateAfterLearnedChange + never-rejecting afterEngagement | ✓ VERIFIED | Substantive (82 lines), imported by useEngagement, useArticles, useBoards, useFeedback |
| `src/main/frontend/src/hooks/useEngagement.ts` | open + Forget wired, useMatch('/priority') | ✓ VERIFIED | Both hooks detect Priority and call afterEngagement |
| `src/main/frontend/src/hooks/useArticles.ts` | star (true only) + Raindrop wired | ✓ VERIFIED | |
| `src/main/frontend/src/hooks/useBoards.ts` | board add + Read Later wired | ✓ VERIFIED | Removal untouched |
| `src/main/java/.../FeedbackResult.java` | engagementReplaced | ✓ VERIFIED | |
| `src/main/java/.../ArticleFeedbackService.java` | SIX_DECIMAL_TOLERANCE, differs, engagementAtCap | ✓ VERIFIED | See WR-03 (tolerance value) |
| `src/main/java/.../LearnedLimit.java` | D-10 order + engagementAtCap | ✓ VERIFIED | |
| `src/main/java/.../TopicLearned.java` | engagementAtCap | ✓ VERIFIED | |
| `src/main/frontend/src/utils/feedback.ts` | replaced/restored notes, D-12/D-13 filter | ✓ VERIFIED | |
| `src/main/frontend/src/components/WhyBreakdown.tsx` | votes/engaged parts, tooltip | ✓ VERIFIED | |
| `src/main/frontend/src/components/TopicRow.tsx` | LearnedLine split + at max | ✓ VERIFIED | |
| `src/main/frontend/src/types/index.ts`, `api/interest.ts` | optional wire booleans | ✓ VERIFIED | |
| `src/test/java/.../PriorityApiIntegrationTest.java` | SC-3 proof | ✓ VERIFIED | Ran and passed |
| `CLAUDE.md` | Phase 10 behavior documented | ✓ VERIFIED | |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| useEngagement.ts | engagementReaction.ts | `afterEngagement(qc, article.id, onPriority)` / `afterEngagement(qc, id, onPriority)` | WIRED |
| engagementReaction.ts | usePriorityArticles.ts | `patchPriorityArticle(qc, id, { interestScore: score })` | WIRED |
| engagementReaction.ts | api/articles.ts | `articlesApi.getById(id)` in `qc.query` | WIRED |
| useFeedback.ts | engagementReaction.ts | `invalidateAfterLearnedChange(qc, v.id, v.onPriority)` | WIRED |
| ArticleFeedbackService | FeedbackResult | `differs(b.engagementLearned(), a.engagementLearned())` | WIRED |
| ArticleFeedbackService | LearnedLimit | `LearnedLimit.engagementAtCap(w, engagementCap)` | WIRED |
| feedback.ts | types TopicEffect | `e.engagementReplaced` | WIRED |
| WhyBreakdown.tsx | types TopicBreakdownRow | `row.engagementWeight` | WIRED |
| TopicRow.tsx | api/interest TopicLearned | `learned.engagementAtCap` | WIRED |
| UI entry points | engagement hooks | ReadingPane (open/star/Raindrop/Read Later/BoardManager), useKeyboardShortcuts (`o`, `s`, `v`, `b`→BoardManager), ScoreRow (Forget) | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| WhyBreakdown TopicLabel | thumbsWeight / engagementWeight | GET /api/articles/{id} → ScoreBreakdowns → InterestScoreQueries learned CTE | Yes (SC-3 test asserts 0.9) | ✓ FLOWING |
| PriorityList badge | interestScore | Priority page cache patched from fresh GET /api/articles/{id} | Yes | ✓ FLOWING |
| Vote toast | effects[].before/after/engagementReplaced | PUT/DELETE /feedback → topicWeights before/after | Yes (HTTP test) | ✓ FLOWING |
| TopicRow LearnedLine | thumbsLearned / engagementLearned / engagementAtCap | GET /api/interest/topics/learned → allTopicWeights | Yes (HTTP tests) | ✓ FLOWING |
| BoardArticleList badge | interestScore | ['boardArticles', id] — not in the engagement/vote refresh set | Refreshes only on staleTime/board add | ⚠️ see warning |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Reaction, Priority component, Why N?, toast, Interests line, save refresh, vote hook | `npx vitest run` on 7 phase test files | 7 files, 131 tests passed | ✓ PASS |
| Star / Forget / open hooks on Priority | `npx vitest run usePriorityArticles.test.ts ScoreRow.test.tsx useEngagement.test.ts` | 3 files, 29 tests passed | ✓ PASS |
| SC-3, SC-4, D-10, Why-N engagement part, service unit tests | `./gradlew test -x npmBuild -x npmInstall --tests …engagedArticleAgreesEverywhereAfterRefresh --tests …voteEffectBeforeIncludesTheEngagementItReplaces --tests …bindingRangeOutranksTheEngagementCap --tests …whyBreakdownCarriesTheEngagementPart --tests …ArticleFeedbackServiceTest` | BUILD SUCCESSFUL; 29 tests, 0 failures | ✓ PASS |
| Full suites | Orchestrator evidence (not re-run): backend 657 tests 0 failures, frontend tsc -b clean + 383/383 | accepted | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED (no probes declared; not a migration/tooling phase).

### Prohibitions

| Plan | Prohibition | Tier | Disposition |
|------|-------------|------|-------------|
| 10-01 | MUST NOT re-sort/refetch/invalidate/reset Priority on engagement | test | Enforced: neverInvalidatesResetsOrRefetchesPriority, noSaveTouchesPriority, PriorityList one-GET assertion |
| 10-01 | MUST NOT delay the original page open | test | Enforced: opensFirstThenSendsABodylessPut |
| 10-01 | MUST NOT claim ranking changed when score did not change | test | Enforced: anEqualScoreChangesNothing, aRepeatOpenWithTheSameScoreLightsNothing |
| 10-01 | MUST NOT toast on background refresh failure | test | Enforced: aFailedRefetchIsSilent(AndNeverRejects) |
| 10-02 | MUST NOT report smaller-than-nominal effect without replaced/restored note | test | Enforced: voteEffectBeforeIncludesTheEngagementItReplaces, service unit tests (see WR-02 for unpicked topics) |
| 10-02 | MUST NOT name engagement cap when clamp/range binds | test | Enforced: clampAndRangeOutrankEngagementCap, bindingRangeOutranksTheEngagementCap |
| 10-03 | MUST NOT present engagement weight as votes | test | Enforced: WhyBreakdown/TopicRow/feedback string tests |
| 10-03 | MUST NOT print a client-computed weight | judgment | Flagged for human resolution; LLM verdict (non-authoritative): satisfied |
| 10-03 | Toast MUST NOT mention the engagement cap | test | Enforced: engagementCapIsNeverWorded |
| 10-04 | MUST NOT claim Priority paging never skips | test | Enforced: Javadoc grep (old claim absent, "until the user refreshes" present) |
| 10-04 | MUST NOT let badge/Why N?/position disagree after refresh | test | Enforced: engagedArticleAgreesEverywhereAfterRefresh |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| LRN-06 | 10-01, 10-04 | Priority order, badge and "Why N?" reflect engagement consistently; engagement never re-sorts an open Priority list, it sets the hint | ✓ SATISFIED | Truths 2, 3, 5-9 |
| EXPL-01 | 10-02, 10-03 | "Why N?" shows each topic's effective weight as base + votes + engagement; rows sum exactly to the badge | ✓ SATISFIED | Truths 1, 4, 10-13 |

No orphaned requirements: REQUIREMENTS.md maps only LRN-06 and EXPL-01 to Phase 10, and both are claimed. (Traceability rows at REQUIREMENTS.md:91-92 still read "Pending"; update at milestone/phase close.)

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (32 phase-changed files) | - | TBD/FIXME/XXX, TODO/HACK/PLACEHOLDER | - | None found |
| `hooks/engagementReaction.ts` | 58-76 | Forced by-id refetch not cancelled by a following vote (review WR-01) | ⚠️ Warning | Stale by-id/Priority row after a quick engage-then-vote; corrected by refresh |
| `service/ArticleFeedbackService.java` | 35 | `SIX_DECIMAL_TOLERANCE = 5e-7` below one 6th-decimal unit (review WR-03) | ℹ️ Info | Rare spurious "(replaces engagement)"; not an SC failure |
| `components/TopicRow.tsx` | ~256 | Zero-branch keyed on combined learned (review WR-04) | ⚠️ Warning | Split hidden when votes and engagement cancel |
| `hooks/engagementReaction.ts` | 13-21 | Refresh set omits `['boardArticles']` | ⚠️ Warning | Board list badge stale up to 30 s |

Pre-existing, out of scope: ESLint `react-hooks/set-state-in-effect` in `WhyBreakdown.tsx:19` (recorded in deferred-items.md, predates the phase).

### Human Verification Required

1. **Manual UAT (from 10-04 Task 2 human-check)** — run bootTestRun + vite dev server; on /priority exercise o, star, b, Raindrop, Forget, repeat open, the hint, a vote on an engaged article and Interests. Expected: see frontmatter. Why human: real browser/router/data and visual in-place behavior.
2. **Judgment prohibition: no client-computed weight** — confirm the non-authoritative "satisfied" verdict.
3. **WR-01 race decision** — accept or fix the engage-then-vote refetch race before shipping.
4. **WR-02 wording decision** — accept the silent "Go −0.2" on unpicked topics of a narrowed 👎 (D-11 as written) or change it.
5. **WR-04 decision** — accept or fix "No learned adjustment yet" when votes and engagement cancel.
6. **Board-list badge freshness** — accept the 30 s stale window (mirrors the vote path) or add `['boardArticles']` to `invalidateAfterLearnedChange`.

### Gaps Summary

No must-have failed. All four roadmap success criteria and all plan truths are backed by code that is present, wired and exercised by passing tests (re-run here for the key frontend files and the SC-3/SC-4/D-10 backend tests). The phase goal is achieved on the normal interaction path.

The status is human_needed, not passed, because (a) the planner deferred a manual UAT to end of phase, (b) one judgment-tier prohibition needs explicit human resolution, and (c) four edge-case defects need a ship/fix decision. None of them re-sorts Priority or breaks the post-refresh agreement SC-3 proves, but they can show stale or unexplained numbers in narrow cases: WR-01 (engage-then-vote race), WR-02 (unexplained drop on unpicked narrowed topics), WR-04 (Interests split hidden when parts cancel), and board-list badges outside the refresh set. WR-01 is the most material; it is the one to fix if any are fixed before ship.

---

_Verified: 2026-09-30T21:05:00Z_
_Verifier: Claude (gsd-verifier)_
