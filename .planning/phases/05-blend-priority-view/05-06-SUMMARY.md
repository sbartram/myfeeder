---
phase: 05-blend-priority-view
plan: 06
subsystem: ui
tags: [react, tanstack-query, zustand, keyboard, priority]
status: complete

requires:
  - phase: 05-02
    provides: "usePriorityArticles, PRIORITY_KEY, dedupeById, refreshPriority, PriorityList"
  - phase: 05-04
    provides: "PriorityBanner, g p route wiring, useInterestStatus polling on /priority"
provides:
  - "patchPriorityArticle(qc, id, { read?, starred? }): in-place Priority row patch (setQueriesData, no new cache entry)"
  - "usePriorityArticles().fetchNextNewId(): loads the next page and resolves to its first unseen row id"
  - "usePriorityStore (session-only): rankingChanged, baselineUnscored, setRankingChanged, setBaselineUnscored, resetHint"
  - "useKeyboardShortcuts callbacks: isPriority, onPriorityNextPage, onPriorityRefresh"
  - "MainLayout: keyboard list is priority.rows on /priority; leaving /priority removes the Priority cache"
  - "Ranking changed hint on the refresh button (CSS .priority-refresh.hint)"
affects: [05-07, phase-06-feedback]

actuals:
  tokens: 10495
  tasks: 3
  commits: 4
plan_head_before: 37275513367d02ce1452ce90e72778f5ff8e192a

tech-stack:
  added: []
  patterns:
    - "Priority cache is patched in place (setQueriesData), never invalidated or refetched; re-rank only via resetQueries (refresh / r) or removeQueries on leave"
    - "Session-only UI state lives in a non-persisted Zustand store (priorityStore), not in the persisted, widely mocked uiStore"

key-files:
  created:
    - src/main/frontend/src/stores/priorityStore.ts
    - src/main/frontend/src/hooks/usePriorityArticles.test.ts
  modified:
    - src/main/frontend/src/hooks/usePriorityArticles.ts
    - src/main/frontend/src/hooks/useArticles.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/PriorityList.tsx
    - src/main/frontend/src/components/PriorityList.test.tsx
    - src/main/frontend/src/components/ShortcutOverlay.tsx
    - src/main/frontend/src/App.tsx
    - src/main/frontend/src/App.css

key-decisions:
  - "05-06: Read/star patch the ['priority'] cache in place via patchPriorityArticle (request read/starred only, never the PATCH response), so rows keep index and listed score; nothing invalidates or refetches the Priority query"
  - "05-06: usePriorityStore (non-persisted Zustand) holds rankingChanged + baselineUnscored; 05-07 adds whyOpen/toggleWhy there"
  - "05-06: The hint lights on interest mutations (5 onSuccess handlers) or status.eligibleUnscored below the page-1 baseline; accepted as a soft signal (Open Question 1); refresh and re-entry clear it"
  - "05-06: On /priority, j past the last row calls onPriorityNextPage (fetchNextNewId) and selects the first new row; r calls onPriorityRefresh; Shift+A has an explicit !isPriority guard"

patterns-established:
  - "Keyboard route awareness via callbacks (isPriority + route-specific actions) rather than a virtual selectedFeedId"
  - "Hint button keeps one DOM element and swaps class/label/aria-label so focus is not lost"

requirements-completed: [PRIO-05, PRIO-06, PRIO-07]

coverage:
  - id: D1
    description: "Mark read / star (m, s, reading-pane buttons, auto-mark-read) patch the Priority row in place: same index, dimmed, listed score kept, no refetch"
    requirement: PRIO-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#markReadPatchesTheRowInPlace, starPatchesTheRowInPlace, twoMutationsOnTheSameRowBothApply, patchKeepsTheListedScore, patchWithoutPriorityCacheCreatesNothing"
        status: pass
      - kind: automated_ui
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#markReadKeepsTheRowInPlaceDimmed"
        status: pass
    human_judgment: false
  - id: D2
    description: "The Priority query is not refetched by ['articles'] invalidation, window focus, reconnect or remount"
    requirement: PRIO-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#articlesInvalidationDoesNotRefetchPriority, focusAndReconnectDoNotRefetch, remountDoesNotRefetch"
        status: pass
    human_judgment: false
  - id: D3
    description: "Paging continues after the cursor row is read; duplicates dropped with the patched occurrence kept"
    requirement: PRIO-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#cursorRowReadBeforeLoadMoreContinues"
        status: pass
    human_judgment: false
  - id: D4
    description: "On /priority j/k walk ranked rows incl. read ones, j on the last row loads and selects the next page's first new row, Shift+A is a no-op, r re-ranks; other lists unchanged"
    requirement: PRIO-07
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#on Priority (7 tests)"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#fetchNextNewIdSelectsTheFirstUnseenRow, fetchNextNewIdWithoutANextPageReturnsUndefined"
        status: pass
    human_judgment: false
  - id: D5
    description: "Leaving /priority removes the Priority cache so re-entry fetches page 1 fresh"
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#leavingAndReenteringFetchesPageOneFresh"
        status: pass
    human_judgment: false
  - id: D6
    description: "Ranking changed hint lights after interest edits / a lower waiting count, never re-sorts, clears on refresh and re-entry"
    verification:
      - kind: automated_ui
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#hintLightsWhenFewerArticlesAreWaiting, noHintWhenMoreArticlesAreWaiting, interestSaveLightsTheHint, refreshClearsTheHint, reenteringClearsTheHint"
        status: pass
    human_judgment: false
  - id: D7
    description: "End-to-end feel in the running app: rows stay put and dimmed across m, auto-mark-read, star, window refocus and a 60s wait; topic save lights the hint; r re-ranks; reload on /priority works; hint label fits at minimum panel width (E8)"
    verification: []
    human_judgment: true
    rationale: "Task 3 <human-check> and the E8 long-text backstop need the real app (bootTestRun + Vite) and visual judgment of layout at minimum width"

duration: 6min
completed: 2026-09-25
---

# Phase 05 Plan 06: Frozen Priority Triage, Ranked Keyboard Walk and Ranking-Changed Hint Summary

**Read/star patch Priority rows in place via `setQueriesData` (no refetch ever re-ranks), the keyboard walks the ranked rows with `j` paging past the last one, and a session-only `usePriorityStore` lights "↻ Ranking changed — refresh" after interest edits or new scores.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-25T20:13:50Z
- **Completed:** 2026-09-25T20:19:51Z
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments
- `patchPriorityArticle` wired into `useUpdateArticleState.onSuccess`: m, s, reading-pane buttons and auto-mark-read dim the row in place; the listed `interestScore` is kept
- Cache-policy proofs: no Priority refetch on `['articles']` invalidation, focus, reconnect or remount; paging continues after the cursor row is read
- Keyboard on `/priority`: `j`/`k` over `priority.rows` (read rows included), `j` on the last row pages via `fetchNextNewId` (D-11), `r` re-ranks, `Shift+A` guarded; overlay reads "Refresh current feed / ranking"
- `MainLayout` removes the Priority cache on leave so re-entry starts from page 1 (D-08)
- "Ranking changed" hint: set by 5 interest mutations and by a status count below the page-1 baseline; cleared by refresh and re-entry; accent color

## Task Commits

1. **Task 1 (tracer): in-place Priority patch** - `f461510` (feat)
2. **Task 2: keyboard walk, j paging, Shift+A guard, r refresh, leave-time removal** - `174baaf` (feat)
3. **Task 3: Ranking changed hint** - `4f9a534` (test, RED), `ed4ca97` (feat, GREEN)

## Contracts for plan 05-07

- `usePriorityStore` shape (`src/main/frontend/src/stores/priorityStore.ts`): `{ rankingChanged: boolean; baselineUnscored: number | null; setRankingChanged(v: boolean); setBaselineUnscored(v: number | null); resetHint() }`. Plain `create`, no middleware. 05-07 adds `whyOpen` / `toggleWhy` here; tests reset it with `usePriorityStore.setState({ rankingChanged: false, baselineUnscored: null })` in `beforeEach`.
- Keyboard callbacks (`useKeyboardShortcuts` second arg): `isPriority?: boolean`, `onPriorityNextPage?: () => Promise<number | undefined>`, `onPriorityRefresh?: () => void`, alongside the existing `onOpenBoard`, `onShowShortcuts`.
- `usePriorityArticles(enabled)` returns the infinite query plus `rows` and `fetchNextNewId`.

## Files Created/Modified
- `src/main/frontend/src/stores/priorityStore.ts` - session-only Priority UI state (hint + baseline)
- `src/main/frontend/src/hooks/usePriorityArticles.ts` - `patchPriorityArticle`, `fetchNextNewId`, hint reset in `refreshPriority`
- `src/main/frontend/src/hooks/usePriorityArticles.test.ts` - cache-policy, paging and re-entry proofs (12 tests)
- `src/main/frontend/src/hooks/useArticles.ts` - state mutation patches the Priority row
- `src/main/frontend/src/hooks/useKeyboardShortcuts.ts` (+ test) - Priority `j`, `r`, `Shift+A` branches (7 new tests)
- `src/main/frontend/src/hooks/useInterest.ts` - 5 `setRankingChanged(true)` calls; `useInterestStatus` doc comment
- `src/main/frontend/src/components/PriorityList.tsx` (+ test) - baseline capture, hint effect, hint label (6 new tests)
- `src/main/frontend/src/components/ShortcutOverlay.tsx` - `r` label
- `src/main/frontend/src/App.tsx` - `isPriority`, `priority`, leave-time `removeQueries`, keyboard callbacks
- `src/main/frontend/src/App.css` - `.priority-refresh.hint { color: var(--accent); }`

## Decisions Made
- The patch copies only defined `read`/`starred` keys from the request (an explicit field pick, not a spread of `variables.state`), so no other field can ever enter a row.
- The hint effect reads the store's current baseline via `getState()` rather than the render snapshot, so the mount-time `resetHint()` cannot race with a stale baseline from a previous visit.
- The refresh button stays one DOM element; class, label and `aria-label` switch with the hint (focus is preserved). While refreshing the hint styling is suppressed and the label is "↻ Refreshing…".

## Deviations from Plan

### Process notes

**1. [Process] Task 2 RED/GREEN committed together**
- **Found during:** Task 2
- **Issue:** Task 2 (`tdd="true"`) was committed as one `feat` commit; the RED run (6 failing tests on the new behaviour, 24 passing) was verified before implementation but not committed separately. Task 3 used separate `test` / `feat` commits.
- **Impact:** None on behaviour; the plan is `type: execute` and `workflow.tdd_mode` is off, so no TDD gate applies.

**2. [Process] Task 3 RED commit includes the unwired store**
- **Found during:** Task 3
- **Issue:** The tests import `usePriorityStore`; without the module the file fails to load (an invalid RED). The ~25-line store container was committed with the RED tests so the 5 tests failed on assertions for the planned behaviour; nothing used it until the GREEN commit.

**3. [Rule 1 - Criterion] Reworded a JSDoc line**
- **Found during:** Task 1 acceptance
- **Issue:** `grep -c setQueriesData usePriorityArticles.ts` printed 2 (JSDoc mention); criterion requires 1.
- **Fix:** Reworded the comment; count is 1. Committed in `f461510`.

Some guard tests (`articlesInvalidationDoesNotRefetchPriority`, `focusAndReconnectDoNotRefetch`, `remountDoesNotRefetch`, `jWalksPriorityRowsIncludingReadOnes`, `keysDoNothingWithNoPriorityRows`, `jOnTheLastRowOutsidePriorityStaysPut`, `leavingAndReenteringFetchesPageOneFresh`) passed at RED because 05-02's query options and the existing j/k already provide that behaviour; they are kept as regression proofs.

---

**Total deviations:** 1 auto-fixed (Rule 1), 2 process notes
**Impact on plan:** No scope change.

## Issues Encountered
None.

## Verification
- `cd src/main/frontend && npx tsc -b` exits 0; `npm test`: 22 files, 195 tests passed
- Tracer gate (Task 1): interactive, end-of-phase, automated-only verify; re-run passed before expansion
- All task acceptance greps pass (setQueriesData 1; patch call 1; no Priority invalidate/refetch 0/0; App.tsx keyboard list and removeQueries 1/1; `!callbacks.isPriority` 1; `onPriorityRefresh` 2; overlay label 1; `setRankingChanged(true)` 5; no zustand middleware 0; hint labels 1/1; accent rule 1; `refetchInterval` 1)
- ESLint clean on all changed files

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 05-07 can extend `usePriorityStore` and add the `i` shortcut next to the Priority keyboard branches.
- PRIO-07 is shared with 05-07; its checkbox stays open until 05-07's SUMMARY exists.
- End-of-phase human check (D7): run the Task 3 `<human-check>` and the E8 minimum-width label backstop.

## Self-Check: PASSED
- FOUND: priorityStore.ts, usePriorityArticles.test.ts, usePriorityArticles.ts, App.tsx
- FOUND commits: f461510, 174baaf, 4f9a534, ed4ca97

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*
