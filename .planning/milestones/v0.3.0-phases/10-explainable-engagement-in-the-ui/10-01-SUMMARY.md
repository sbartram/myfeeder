---
phase: 10-explainable-engagement-in-the-ui
plan: 01
subsystem: ui
tags: [react, tanstack-query, priority, engagement, vitest]

requires:
  - phase: 08-engagement-capture
    provides: open/star/board/Raindrop/Forget engagement capture endpoints and hooks
  - phase: 06 (v0.2.1)
    provides: patchPriorityArticle, priorityStore rankingChanged, the vote's in-place Priority reaction
provides:
  - hooks/engagementReaction.ts with invalidateAfterLearnedChange and the never-rejecting afterEngagement
  - in-place Priority reaction (patch + "Ranking changed") after open, star, board add, Read Later, Raindrop save and Forget
  - one refresh set shared by votes and engagement (learned, articles, other by-id articles off Priority)
affects: [10-02, 10-03, 10-04, 11-topic-suggestions]

actuals:
  tokens: 14300
  tasks: 3
  commits: 5
plan_head_before: f03d84dd993d2db8a96c15675f4a5d18c865fc34
plan_head_after: cf97e5a67c5f339ba327781bcf4ce24ff884a04d

tech-stack:
  added: []
  patterns:
    - "Post-action reaction as a plain never-rejecting function (not a hook), voided from a block-body onSuccess"
    - "By-id refresh via qc.query({ staleTime: 0, retry: false }) instead of invalidation, so the caller can compare the fresh score"
    - "Priority detection with useMatch('/priority') inside each engagement hook, as the vote does"

key-files:
  created:
    - src/main/frontend/src/hooks/engagementReaction.ts
    - src/main/frontend/src/hooks/engagementReaction.test.ts
  modified:
    - src/main/frontend/src/hooks/useEngagement.ts
    - src/main/frontend/src/hooks/useArticles.ts
    - src/main/frontend/src/hooks/useBoards.ts
    - src/main/frontend/src/hooks/useFeedback.ts
    - src/main/frontend/src/hooks/useEngagement.test.ts
    - src/main/frontend/src/hooks/engagementRefresh.test.ts
    - src/main/frontend/src/hooks/usePriorityArticles.test.ts
    - src/main/frontend/src/components/ScoreRow.test.tsx
    - src/main/frontend/src/components/PriorityList.test.tsx

key-decisions:
  - "afterEngagement's by-id qc.query passes retry: false as well as staleTime: 0: createQueryClient's default retry: 1 would otherwise apply, so a failed refetch would issue a second GET after ~1s (T-10-01 one GET per action)"
  - "The Pitfall 4 guard (A5) skips only the compare/patch while a ['feedback'] mutation for the same id is pending; the refetch and invalidations still run"
  - "The vote hook shares only invalidateAfterLearnedChange; its unconditional patch, hint and toast are unchanged (Open Question 3)"

patterns-established:
  - "Engagement reaction: every D-05 action calls void afterEngagement(qc, id, onPriority) only on success; ['priority'] is changed only through patchPriorityArticle"

requirements-completed: [LRN-06]

coverage:
  - id: D1
    description: "Shared afterEngagement: by-id refetch with staleTime 0, D-07 refresh set, Priority patch + hint only when the score changed, silent on failure, skips the patch while a vote on the id is pending"
    requirement: LRN-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/engagementReaction.test.ts (11 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Open (Open Original / o) on Priority patches the badge in place and lights the hint without reordering or refetching Priority; a repeat open with the same score lights nothing; window.open stays first and failures are silent"
    requirement: LRN-06
    verification:
      - kind: automated_ui
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#openingLightsTheHintAndPatchesTheBadgeWithoutReordering, #aRepeatOpenWithTheSameScoreLightsNothing"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useEngagement.test.ts (7 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Star (starred true only) and Forget engagement run the reaction; unstar and read toggles do not; a failed Forget toasts and makes no GET"
    requirement: LRN-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#starOnPriorityPatchesTheScoreAndLightsTheHint, #unstarAndReadDoNotReact"
        status: pass
      - kind: automated_ui
        ref: "src/main/frontend/src/components/ScoreRow.test.tsx#forgetRefetchesTheArticleAndRefreshesLearnedAndLists, #forgetOnPriorityPatchesTheRowAndLightsTheHint, #aFailedForgetShowsTheErrorToast"
        status: pass
    human_judgment: false
  - id: D4
    description: "Raindrop save, board add and Read Later run the reaction and keep their toasts/board invalidations; board removal and failed saves run none; no save touches ['priority']; the vote shares the refresh set unchanged"
    requirement: LRN-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/engagementRefresh.test.ts (9 tests)"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts + src/main/frontend/src/components/FeedbackBar.test.tsx (unmodified)"
        status: pass
    human_judgment: false
  - id: D5
    description: "On the running app, engaging an article on /priority visibly patches its badge and switches the refresh button to '↻ Ranking changed — refresh' without the list jumping"
    verification: []
    human_judgment: true
    rationale: "Real-browser feel of the in-place update (no scroll jump, badge change noticed) is not asserted by jsdom tests; it belongs to the end-of-phase UAT"

duration: 7min
completed: 2026-09-30
status: complete
---

# Phase 10 Plan 01: Post-engagement reaction on Priority Summary

**One never-rejecting `afterEngagement(qc, id, onPriority)` refetches the engaged article by id (staleTime 0), refreshes the vote's learned/list set, and on Priority patches only that row's badge and lights "Ranking changed" when the score actually moved; open, star, board add, Read Later, Raindrop save and Forget all use it.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-09-30T20:27:25Z
- **Completed:** 2026-09-30T20:34:40Z
- **Tasks:** 3
- **Files modified:** 11 (2 created, 9 modified)

## Accomplishments

- `hooks/engagementReaction.ts`: `invalidateAfterLearnedChange` (the vote's refresh set, verbatim) and `afterEngagement`, which never touches `['priority']` except through `patchPriorityArticle`.
- Open (`useOpenOriginal`) keeps `window.open` first and synchronous; the reaction runs only after the PUT succeeds, and every failure stays silent.
- Star reacts only on `starred: true`; Forget, Raindrop, board add and Read Later react on success; unstar, read toggles, board removal and failed saves do not.
- `useVoteFeedback` now uses the shared refresh set with identical behavior (useFeedback/FeedbackBar tests untouched and green).
- Phase 8's "by-id only" refresh tests are rewritten to the D-07 set, with the SC-2 "never priority" anchor kept in every engagement test file.

## Task Commits

1. **Task 1 (tracer): open on Priority patches the badge and lights the hint** - `78c9f8d` (feat). Tracer gate: verify re-run green, then expanded.
2. **Task 2: star and Forget react** - `5781745` (test, RED: 3 target tests failing on assertions), `7429fe9` (feat, GREEN)
3. **Task 3: Raindrop, board add, Read Later; vote shares the refresh set** - `4db19e3` (test, RED: 7 target tests failing on assertions), `cf97e5a` (feat, GREEN)

## Files Created/Modified

- `src/main/frontend/src/hooks/engagementReaction.ts` - the shared reaction and refresh set
- `src/main/frontend/src/hooks/engagementReaction.test.ts` - the 11 unit tests named in the plan
- `src/main/frontend/src/hooks/useEngagement.ts` - open and Forget wired, `useMatch('/priority')` in both
- `src/main/frontend/src/hooks/useArticles.ts` - star (starred true only) and Raindrop wired
- `src/main/frontend/src/hooks/useBoards.ts` - board add and Read Later wired; removal unchanged
- `src/main/frontend/src/hooks/useFeedback.ts` - vote uses `invalidateAfterLearnedChange`
- `src/main/frontend/src/hooks/useEngagement.test.ts` - MemoryRouter wrapper, method/URL routing, D-07 assertions, `aFailedRefetchIsSilent`
- `src/main/frontend/src/hooks/engagementRefresh.test.ts` - the 7 named tests (9 cases with the it.each)
- `src/main/frontend/src/hooks/usePriorityArticles.test.ts` - MemoryRouter at /priority, getById mock, two new cases
- `src/main/frontend/src/components/ScoreRow.test.tsx` - MemoryRouter wrapper, GET routing, Forget cases
- `src/main/frontend/src/components/PriorityList.test.tsx` - `OpenOriginalHarness` and two open cases

## Decisions Made

- `retry: false` on the by-id `qc.query` (see Deviations).
- Test helpers: the refresh tests assert "never priority" over `invalidateQueries` and `resetQueries` call keys plus `isInvalidated`, not "refetchQueries never called", because `invalidateQueries` calls `refetchQueries` internally with the same filters.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The by-id refetch would retry once on failure**
- **Found during:** Task 1
- **Issue:** The plan's interface notes say `qc.query` has retry off by default. In TanStack 5.103.2 `query()` only sets `retry: false` when the merged options leave it undefined, and `createQueryClient()` sets `queries.retry: 1`, so a failed refetch would issue a second GET about 1s later. That contradicts T-10-01 (one GET per action) and the "failed refetch is silent and quick" expectation.
- **Fix:** pass `retry: false` alongside `staleTime: 0` in `afterEngagement`.
- **Files modified:** src/main/frontend/src/hooks/engagementReaction.ts
- **Verification:** `aFailedRefetchIsSilentAndNeverRejects` asserts exactly one `getById` call.
- **Committed in:** 78c9f8d

**2. [Test adjustment] `neverInvalidatesResetsOrRefetchesPriority` does not assert "refetchQueries never called"**
- **Found during:** Task 1
- **Issue:** `invalidateQueries` calls `refetchQueries` internally with predicate filters, so a "never called" assertion is wrong about the library, not about the code.
- **Fix:** the test checks that no invalidate/reset/refetch call carries a `'priority'` first key, that `resetQueries` is never called, that `['priority']` is never invalidated and that `articlesApi.priority` is never called, as the plan's behavior block specifies.
- **Committed in:** 78c9f8d

---

**Total deviations:** 2 (1 Rule 1 fix, 1 test-assertion correction)
**Impact on plan:** Both keep the plan's intent; no scope change.

## Issues Encountered

- Task 2 and Task 3 are `tdd="true"`, but on the first pass Task 2's hooks were edited before its tests. To keep the RED/GREEN evidence honest, the two hook files went back to HEAD, the new tests ran and failed on their target assertions (3 failures: expected 82 to be 90, no GET, hint false), the tests were committed as RED, and then the implementation was restored and committed as GREEN. Task 3 went tests-first (7 target failures before the implementation).

## TDD Gate Compliance

- Task 2: RED `5781745` → GREEN `7429fe9`. `unstarAndReadDoNotReact` is a guard test and passes in both states by design.
- Task 3: RED `4db19e3` → GREEN `cf97e5a`. `aFailedSaveRunsNoReaction` and `boardRemovalRunsNoReaction` are guard tests and pass in both states by design.
- No REFACTOR commits were needed.

## Verification

- `cd src/main/frontend && npx tsc -b` exits 0.
- `npx vitest run`: 33 files, 369 tests pass.
- `grep -q "staleTime: 0" .../engagementReaction.ts && grep -q "invalidateAfterLearnedChange" .../useFeedback.ts` passes, and so do every task's grep checks.
- Mutation check: turning off the pending-vote guard, or restoring a 30s staleTime, makes `aPendingVoteOnTheSameArticleSkipsThePatch` and `aFreshCachedArticleIsStillRefetched` fail.
- `ReadingPane.test.tsx`, `useFeedback.test.ts` and `FeedbackBar.test.tsx` are unmodified.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The reaction is in place for 10-03/10-04 wording work and for reuse by Phase 11's suggestions.
- CLAUDE.md's Engagement capture bullet still describes the Phase 8 by-id refresh for opens; the phase's docs plan should reword it to the D-07 reaction.

---
*Phase: 10-explainable-engagement-in-the-ui*
*Completed: 2026-09-30*

## Self-Check: PASSED
