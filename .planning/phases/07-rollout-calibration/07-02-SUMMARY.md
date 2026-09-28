---
phase: 07-rollout-calibration
plan: 02
subsystem: ui
tags: [react, tanstack-query, react-context, interest-badge, vitest]

requires:
  - phase: 07-rollout-calibration
    provides: "07-01: /api/interest/status serves tiers {high, neutral} as server config"
provides:
  - "TierThresholds type and optional InterestStatus.tiers field"
  - "DEFAULT_TIERS (70/40), TierContext and tierOf(score, tiers) in utils/interest.ts"
  - "useInterestTiers(): one non-polling status observer returning served tiers or the fallback"
  - "MainLayout provides the served tiers to every InterestBadge via TierContext.Provider"
affects: [07-09 tier tuning, InterestBadge, PriorityList, ArticleList, BoardArticleList, ScoreRow]

actuals:
  tokens: 3525
  tasks: 2
  commits: 3
plan_head_before: 75ea22c41487559cc6e82f5a00167ea8bcc4d368

tech-stack:
  added: []
  patterns:
    - "Server-served display config reaches leaf components through a React context with a safe default, so components rendered without a QueryClient still work"
    - "A second observer on a shared query key with staleTime Infinity + select never adds polling to the key"

key-files:
  created:
    - src/main/frontend/src/hooks/useInterest.test.tsx
  modified:
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/utils/interest.ts
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/InterestBadge.tsx
    - src/main/frontend/src/App.tsx
    - src/main/frontend/src/components/InterestBadge.test.tsx
    - src/main/frontend/src/utils/interest.test.ts

key-decisions:
  - "InterestBadge reads TierContext (default DEFAULT_TIERS) instead of calling useQuery, because ArticleList/BoardArticleList tests render without a QueryClientProvider"
  - "useInterestTiers shares the ['interest','status'] key with staleTime Infinity, select s.tiers and no refetchInterval; useInterestStatus polling is unchanged"
  - "DEFAULT_TIERS is the only 70/40 literal in the frontend tier code; tierOf compares against the passed thresholds only"

patterns-established:
  - "TierContext pattern: MainLayout is the single provider of served tier thresholds"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "A served tier pair colors a badge end to end (status JSON -> useInterestTiers -> TierContext -> InterestBadge class), with the 70/40 fallback before load"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useInterest.test.tsx#servedTiersColorTheBadge"
        status: pass
    human_judgment: false
  - id: D2
    description: "Fallback to 70/40 when status has no tiers field or the status call fails"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useInterest.test.tsx#fallsBackTo70And40WithoutTiers"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useInterest.test.tsx#fallsBackWhenStatusFails"
        status: pass
    human_judgment: false
  - id: D3
    description: "The tiers observer fetches /status once and never refetches for a second observer"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useInterest.test.tsx#fetchesStatusOnceAndNeverRefetches"
        status: pass
    human_judgment: false
  - id: D4
    description: "tierOf and InterestBadge are inclusive at the low end for the default and custom pairs; existing 70/40 badge table unchanged"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#tierOf"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestBadge.test.tsx#usesTheProvidedTiers"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestBadge.test.tsx#tiersAreInclusiveAtTheLowEnd"
        status: pass
    human_judgment: false
  - id: D5
    description: "MainLayout provides the served tiers to every list in the running app"
    requirement: OPS-02
    verification:
      - kind: other
        ref: "grep 'TierContext.Provider value={tiers}' and 'useInterestTiers()' in App.tsx; npx tsc -b && npm test (322 tests pass)"
        status: pass
    human_judgment: true
    rationale: "No test renders App/MainLayout; seeing badges recolor after a server tier change in the running app needs a human or the 07-09 tuning run"

duration: 2min
completed: 2026-09-27
status: complete
---

# Phase 07 Plan 02: Served Badge Tier Thresholds Summary

**Interest badges now classify scores with the `tiers` pair served on `/api/interest/status`, delivered through a root `useInterestTiers` observer and `TierContext`, with 70/40 as the fallback before status loads or when it fails**

## Performance

- **Duration:** 2 min
- **Started:** 2026-09-27T20:22:12Z
- **Completed:** 2026-09-27T20:24:36Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- `TierThresholds` type, optional `InterestStatus.tiers`, `DEFAULT_TIERS`, `TierContext` and `tierOf(score, tiers)` with no numeric literal in `tierOf`
- `useInterestTiers()`: shares the status cache key with `staleTime: Infinity`, `select: (s) => s.tiers`, no polling, and falls back to `DEFAULT_TIERS`
- `InterestBadge` reads `TierContext`. `MainLayout` wraps AppShell, the dialogs and toasts in `TierContext.Provider value={tiers}`
- An end-to-end test proves that a mocked `/status` with tiers {50, 20} turns a 55 badge from neutral to high. Boundary tables cover the default pair and custom pairs

## Task Commits

1. **Task 1 (tracer): Served tier pair colors a badge through status, hook, context and badge** - `9b34bb6` (feat)
2. **Task 2: MainLayout provider plus boundary tables** - `7247549` (test), `2535352` (feat)

## Files Created/Modified
- `src/main/frontend/src/api/interest.ts` - `TierThresholds` and optional `tiers` field
- `src/main/frontend/src/utils/interest.ts` - `DEFAULT_TIERS`, `TierContext`, threshold-driven `tierOf`
- `src/main/frontend/src/hooks/useInterest.ts` - `useInterestTiers` (`useInterestStatus` untouched)
- `src/main/frontend/src/components/InterestBadge.tsx` - reads `TierContext` before the unscored early return
- `src/main/frontend/src/App.tsx` - MainLayout provides the served tiers
- `src/main/frontend/src/hooks/useInterest.test.tsx` - four hook/path tests (new)
- `src/main/frontend/src/components/InterestBadge.test.tsx` - `usesTheProvidedTiers`
- `src/main/frontend/src/utils/interest.test.ts` - `describe('tierOf')` with three tests

## Decisions Made
- The badge reads a context with a default value instead of calling `useQuery`, so list tests without a `QueryClientProvider` keep working and each badge creates no query observer.
- The `useInterest.test.tsx` harness renders several badges under one provider rather than one harness per score. It asserts the same cases (55 fallback then high, 20 neutral, 19 low).
- Settle checks in the fallback tests read `QueryClient.getQueryState` status, because the hook returns only the thresholds.

## Deviations from Plan

None - plan executed exactly as written. See the TDD note below.

## TDD Gate Compliance

Task 2 (`tdd="true"`) has a RED commit `7247549` and a GREEN commit `2535352`. No refactor was needed. The new `tierOf` and `usesTheProvidedTiers` unit tests already passed at RED. This was an expected early GREEN, not a wrong test: the Task 1 tracer, as the plan designed it, had already shipped `tierOf(score, tiers)` and `TierContext`. The one behavior that was still missing at RED was the MainLayout provider. Its wiring check (`grep TierContext.Provider value={tiers}` / `useInterestTiers()` in App.tsx) failed with exit 1 before the feat commit and passed after it. No test renders `App`, as the plan's planning facts state.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Tuning the tiers in 07-09 is a server config change only. No frontend constant has to be edited.
- The full Vitest suite passes (29 files, 322 tests), and `npx tsc -b` is clean.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-27*

## Self-Check: PASSED
