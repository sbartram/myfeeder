---
phase: 05-blend-priority-view
plan: 02
subsystem: ui
tags: [react, tanstack-query, infinite-query, vitest, interest-ranking]

requires:
  - phase: 05-blend-priority-view
    provides: "05-01 GET /api/articles/priority {items, nextCursor} with interestScore (null when unscored), 404 on a missing cursor"
provides:
  - articlesApi.priority(limit = 50, before?)
  - "TS Article.interestScore?: number | null"
  - "hooks/usePriorityArticles.ts: PRIORITY_KEY, dedupeById, usePriorityArticles(enabled = true), refreshPriority(qc)"
  - PriorityList component and the /priority route
  - InterestBadge component; utils/interest.ts Tier and tierOf
  - EmptyState optional detail prop
  - "CSS .article-item-head, .interest-badge (+ .tier-high/.tier-neutral/.tier-low), .interest-badge-slot, .priority-separator, .empty-state-detail; class priority-refresh on the refresh button"
affects: [05-04, 05-05, 05-06, 05-07]

actuals:
  tokens: 6813
  tasks: 3
  commits: 5
plan_head_before: 514c50643b8d7dda8f937cdd422616c711e244c5

tech-stack:
  added: []
  patterns:
    - "Frozen-order infinite query: key outside ['articles'], infinite staleTime, no focus/reconnect refetch; refresh = resetQueries (page 1 only)"
    - "State slot under the filter input instead of early returns, so the toolbar renders in every state"
    - "Fetch-stub route table in tests with unknown routes failing the test in afterEach"

key-files:
  created:
    - src/main/frontend/src/hooks/usePriorityArticles.ts
    - src/main/frontend/src/components/PriorityList.tsx
    - src/main/frontend/src/components/PriorityList.test.tsx
    - src/main/frontend/src/components/InterestBadge.tsx
    - src/main/frontend/src/components/InterestBadge.test.tsx
  modified:
    - src/main/frontend/src/api/articles.ts
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/App.tsx
    - src/main/frontend/src/utils/interest.ts
    - src/main/frontend/src/components/EmptyState.tsx
    - src/main/frontend/src/App.css

key-decisions:
  - "05-02: The Priority empty/loading/error/no-match copy renders in a slot below the filter input, never as an early return, so the toolbar and refresh button are present in every state"
  - "05-02: An EmptyState with a detail line switches to a column layout via .empty-state:has(.empty-state-detail); existing empty states keep their row layout"

patterns-established:
  - "Priority rows: .article-item-head wraps InterestBadge (or an aria-hidden .interest-badge-slot) and .article-item-title; 05-05 reuses this for other lists"
  - "Callers re-rank Priority only through refreshPriority(qc); nothing invalidates PRIORITY_KEY"

requirements-completed: [PRIO-01, PRIO-02, PRIO-03, PRIO-06]

coverage:
  - id: D1
    description: "/priority renders the server's ranked rows in server order; Load more requests before=<nextCursor> and rows are deduplicated by id"
    requirement: PRIO-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#rendersServerOrderAndPagesWithTheCursor"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#clickingARowSelectsIt"
        status: pass
    human_judgment: false
  - id: D2
    description: "The Not yet scored separator renders once before the first displayed unscored row, first when nothing is scored, absent when nothing unscored is displayed (including under a filter)"
    requirement: PRIO-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#separatorPrecedesTheFirstUnscoredRow"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#separatorFirstWhenNothingIsScored"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#noSeparatorWhenEverythingIsScored"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#separatorAppearsOnceTheFirstUnscoredRowLoads"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#filterHidesTheSeparatorWithoutUnscoredMatches"
        status: pass
    human_judgment: false
  - id: D3
    description: "InterestBadge shows the server score with inclusive tiers (0-39 low, 40-69 neutral, 70-100 high), nothing for null/undefined, 0 for a scored 0; unscored rows keep an aria-hidden slot; read rows keep their badge"
    requirement: PRIO-03
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestBadge.test.tsx#tiersAreInclusiveAtTheLowEnd"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestBadge.test.tsx#rendersNothingForUnscored"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestBadge.test.tsx#scoredZeroIsShown"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestBadge.test.tsx#labelsTheScore"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#unscoredRowsKeepAnEmptySlot"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#readRowKeepsItsBadge"
        status: pass
    human_judgment: false
  - id: D4
    description: "Loading, caught-up (with detail), first-page error and no-match states with the toolbar always present; Refresh ranking fetches only page 1, keeps the selection, shows Refreshing and recovers from failure; Load more failure relabels and retries; a 404 cursor restarts from page 1"
    requirement: PRIO-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#showsLoadingCopyWhileTheFirstPageIsPending"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#showsCaughtUpWithDetailWhenEmpty"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#showsErrorCopyWhenTheFirstPageFails"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#showsNoMatchesForTheFilter"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#refreshFetchesOnlyPageOne"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#refreshKeepsTheSelection"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#refreshButtonShowsRefreshingWhileInFlight"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#failedRefreshReturnsTheButtonToIdle"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#loadMoreFailureRelabelsAndRetries"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#missingCursorRestartsFromPageOne"
        status: pass
    human_judgment: false
  - id: D5
    description: "Visual fit: badge pill size and tier colors across the 6 themes, a long title wrapping beside the fixed-width badge column (E1/long-text backstop), separator strip, and the two-line caught-up state"
    verification: []
    human_judgment: true
    rationale: "jsdom does not lay out CSS; pill dimensions, theme contrast and wrapping need a human look in a real browser"

duration: 6min
completed: 2026-09-25
status: complete
---

# Phase 5 Plan 02: Priority List Summary

**`/priority` now shows the server's ranked unread list with tier badges and a "Not yet scored" separator. Load more pages the list with the cursor and drops duplicate rows. The list has loading, empty and error states, and ↻ Refresh ranking re-ranks it by resetting the `['priority']` infinite query, which fetches only page 1. The query never refetches on its own because its staleTime is infinite and focus and reconnect refetch are off.**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-25T19:40:36Z
- **Completed:** 2026-09-25T19:46:23Z
- **Tasks:** 3
- **Files modified:** 11 (5 created, 6 modified)

## Accomplishments

- Opening `/priority` shows the rows in the exact order the server returned them. Load more requests `?limit=50&before=<nextCursor>`, and a row that comes back on the next page is shown only once (the first occurrence is kept).
- The cache policy follows research Pattern 5 from the first commit:
  - The query key `['priority']` is outside the `['articles']` prefix, so mark-read and star invalidations never touch it.
  - Its staleTime is infinite, and it does not refetch on window focus or reconnect.
- Each row shows a tier-colored 0-100 badge (0-39 low, 40-69 neutral, 70-100 high). An unscored row shows no badge and never the text 0. Instead it keeps an aria-hidden empty slot of the same width, so titles stay aligned.
- The "Not yet scored" separator appears once, right before the first displayed unscored row.
- The loading, caught-up, first-page error and no-match messages appear below the filter input. The toolbar and the refresh button stay visible in every state. There is no "Mark all read" button (D-16).
- Paging failures:
  - If Load more fails, the button reads "Couldn't load more. Try again", stays clickable, and the loaded rows stay put.
  - If the cursor article is gone (HTTP 404), the list restarts from page 1.

## Contract for later plans (05-04, 05-05, 05-06, 05-07)

**`hooks/usePriorityArticles.ts` exports:**
- `PRIORITY_KEY = ['priority'] as const`
- `dedupeById(pages: PaginatedArticles[] | undefined): Article[]`: keeps the first occurrence of each id.
- `usePriorityArticles(enabled = true)`: returns the `useInfiniteQuery` result spread together with `rows`, the deduped rows in server order.
  - Options: `staleTime: Infinity`, `refetchOnWindowFocus: false`, `refetchOnReconnect: false`.
  - This file does not import from `useArticles.ts`.
- `refreshPriority(qc: QueryClient): Promise<void>`:
  1. Scrolls `.article-list .article-items` to the top.
  2. Awaits `qc.resetQueries({ queryKey: PRIORITY_KEY })`.
  3. Awaits the invalidation of the `['article', id]` queries (predicate: `queryKey[0] === 'article'` and length 2).

**Other exports:**
- `articlesApi.priority(limit = 50, before?)`
- TS `Article.interestScore?: number | null`
- `PriorityList()`
- `InterestBadge({ score })`
- `utils/interest.ts`: `Tier`, `tierOf(score)`
- `EmptyState`: new optional `detail` prop

**CSS classes added to `App.css`:**
- `.article-item-head`, plus `.article-item-head .article-item-title { min-width: 0 }`
- `.interest-badge`, `.interest-badge.tier-high`, `.interest-badge.tier-neutral`, `.interest-badge.tier-low`
- `.interest-badge-slot`, and `.article-item .interest-badge, .article-item .interest-badge-slot { font-size: 0.85em }`
- `.article-item.read .interest-badge { opacity: 0.6 }`
- `.priority-separator`
- `.empty-state:has(.empty-state-detail)` and `.empty-state-detail`
- The refresh button uses the class `toolbar-btn priority-refresh`.

## Task Commits

1. **Task 1 (tracer): /priority list with cursor paging**: `0f4fb83` (feat)
2. **Task 2: tier badges, empty slot and the separator**:
   - RED: `b5c6d1d` (test)
   - GREEN: `6c2b58d` (feat)
3. **Task 3: states, refresh and paging failures**:
   - RED: `e03eec5` (test)
   - GREEN: `cb860d8` (feat)

## Files Created/Modified

- `src/main/frontend/src/hooks/usePriorityArticles.ts`: the frozen-order Priority infinite query, `dedupeById` and `refreshPriority`
- `src/main/frontend/src/components/PriorityList.tsx`: the `/priority` panel
- `src/main/frontend/src/components/InterestBadge.tsx`: the tier-colored score pill
- `src/main/frontend/src/components/PriorityList.test.tsx`: 21 route-to-fetch tests
- `src/main/frontend/src/components/InterestBadge.test.tsx`: 4 badge tests
- `src/main/frontend/src/api/articles.ts`: `priority(limit, before?)`
- `src/main/frontend/src/types/index.ts`: `interestScore?: number | null`
- `src/main/frontend/src/App.tsx`: the `/priority` route, placed before the catch-all
- `src/main/frontend/src/utils/interest.ts`: `Tier`, `tierOf`
- `src/main/frontend/src/components/EmptyState.tsx`: the `detail` prop
- `src/main/frontend/src/App.css`: the badge, slot, separator and empty-state detail rules (theme variables only)

## Decisions Made

- The state messages render in a slot rather than as early returns, so the toolbar and refresh button stay visible in every state (UI-SPEC E1).
- The 404 restart runs in a `useEffect` keyed on `[isFetchNextPageError, error, qc]`, as the plan specified. After the reset the query returns to its initial state, so the effect does not fire again.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The empty-state detail line would have rendered beside the heading instead of under it**
- **Found during:** Task 3 (GREEN)
- **Issue:** `.empty-state` is a row flexbox, so the new `<p className="empty-state-detail">` would have sat to the right of "All caught up!". UI-SPEC puts it on a second line under the heading.
- **Fix:** Added `.empty-state:has(.empty-state-detail) { flex-direction: column; }`. Only empty states that have a detail line change, so every existing empty state keeps its current layout.
- **Files modified:** `src/main/frontend/src/App.css`
- **Verification:** Full frontend suite green; `npx tsc -b` exits 0. The visual result is listed under D5 (human judgment).
- **Committed in:** `cb860d8`

**2. [Rule 3 - Blocking] Reworded a doc comment so an acceptance grep counts 1**
- **Found during:** Task 1
- **Issue:** The doc comment also contained `staleTime: Infinity`, so `grep -c 'staleTime: Infinity'` printed 2.
- **Fix:** The comment now reads "An infinite staleTime". The code is unchanged.
- **Committed in:** `0f4fb83`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking).
**Impact on plan:** Both are small and in scope. There is no scope creep.

## TDD Gate Compliance

Tasks 2 and 3 each have a RED `test(05-02)` commit before their GREEN `feat(05-02)` commit (`b5c6d1d` then `6c2b58d`, and `e03eec5` then `cb860d8`). Task 1 is the tracer and was committed as `feat`.

- **Task 2 RED:** 9 target tests failed on assertions and 4 passed. The 4 passes were:
  - the 2 Task 1 tests;
  - 2 negative cases that the stubs already satisfy: no badge for null, and no separator when every row is scored.
- **Task 3 RED:** 10 target tests failed and 13 passed. The 13 passes were the earlier tests.
- **Signature-only stub:** In the Task 2 RED commit, `InterestBadge.tsx` was a stub that returned `null`. Without it the test module would not load, and the checker would classify that as `fixture_or_load_failure`, not a valid RED.
- **RED evidence:** Both RED runs returned `RED_EVIDENCE_OK` (`target_test_failed`) from `check tdd-red-evidence`.
  - Task 2 target: `separatorPrecedesTheFirstUnscoredRow`. Task 3 target: `refreshFetchesOnlyPageOne`.
  - The checker parses node:test TAP. Vitest's `tap-flat` reporter prints the `ok` / `not ok` lines but no `# tests/# pass/# fail` summary lines. So I appended those three lines to the captured output, with counts taken from the real `ok` / `not ok` lines, before I ran the check.

## Issues Encountered

None.

## Verification

- `cd src/main/frontend && npm test`: 19 files, 142 tests, all passing.
- `npx tsc -b`: exits 0.
- Every acceptance grep passes:
  - These each print 1: the `PRIORITY_KEY` line, `staleTime: Infinity`, `refetchOnWindowFocus: false`, `refetchOnReconnect: false`, the `/articles/priority?` URL, `path="/priority"`, `interestScore?: number | null`, `export function tierOf`, `role="separator"`, `resetQueries({ queryKey: PRIORITY_KEY })`, `Refreshing…`, `Couldn't load more. Try again`, `status === 404` and `detail?: string`.
  - These each print 0: `from './useArticles'`, `Mark all read`, and hex literals in the new CSS.
  - `Refresh ranking` prints 2.
  - `var(--accent)` appears in `.tier-high`.
- T-05-06: `PriorityList` and `InterestBadge` contain no `dangerouslySetInnerHTML`.
- T-05-07: the error messages are fixed text. `showsErrorCopyWhenTheFirstPageFails` checks that the server's `detail` ("boom") is never rendered.

## Known Stubs

None. The Task 2 RED stub of `InterestBadge` was replaced in `6c2b58d`.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 05-04 can add the feed-tree entry, `g p`, and `PriorityBanner` between the toolbar and the filter input.
- 05-05 can reuse `InterestBadge`, `.article-item-head` and `.interest-badge-slot` in the other lists.
- 05-06 can import `PRIORITY_KEY` and `refreshPriority` from `usePriorityArticles.ts` (this file does not import from `useArticles.ts`, so there is no cycle).

## Self-Check: PASSED

- All 5 created files exist on disk.
- Commits `0f4fb83`, `b5c6d1d`, `6c2b58d`, `e03eec5` and `cb860d8` are present in `git log`.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*
