---
phase: 05-blend-priority-view
plan: 04
subsystem: ui
tags: [react, react-router, tanstack-query, vitest, interest-ranking, keyboard-shortcuts]

requires:
  - phase: 05-blend-priority-view
    provides: "05-02 PriorityList (toolbar, filter input, list slot), the /priority route and utils/interest.ts"
  - phase: 03-interest-profile-topics
    provides: "useInterestStatus() with 15s conditional polling; MainLayout interestsOpen state (03-05)"
provides:
  - "Feed-tree Priority smart view (first, no icon, no count), active only on /priority via useMatch('/priority')"
  - "g then p chord: setSelectedFeed(null) + navigate('/priority'); shortcut overlay line 'g then p' / 'Go to Priority'"
  - "components/PriorityBanner.tsx: PriorityBanner({ onSetUpInterests? }) with D-14 precedence"
  - "utils/interest.ts: OPEN_BREAKER_STATES (readonly string[]) and articles(count) with en-US thousands separators"
  - "PriorityList prop onSetUpInterests?: () => void, wired in App.tsx to setInterestsOpen(true)"
  - "CSS .priority-banner, .priority-banner.cold-start, .priority-banner strong"
affects: [05-05, 05-06, 05-07]

actuals:
  tokens: 6498
  tasks: 3
  commits: 5
plan_head_before: 25312d4e9c49b9514553bbf2464851ddf9c34070

tech-stack:
  added: []
  patterns:
    - "Route-derived active state for smart views (useMatch) instead of store-derived state"
    - "Status banner renders between toolbar and filter, outside the list slot, so it never replaces rows"
    - "One wrapper element per banner with precedence-selected content (single role=status)"

key-files:
  created:
    - src/main/frontend/src/components/PriorityBanner.tsx
    - src/main/frontend/src/components/PriorityBanner.test.tsx
  modified:
    - src/main/frontend/src/components/FeedPanel.tsx
    - src/main/frontend/src/components/FeedPanel.test.tsx
    - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
    - src/main/frontend/src/components/ShortcutOverlay.tsx
    - src/main/frontend/src/components/PriorityList.tsx
    - src/main/frontend/src/components/PriorityList.test.tsx
    - src/main/frontend/src/utils/interest.ts
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/App.tsx
    - src/main/frontend/src/App.css

key-decisions:
  - "05-04: The Priority entry's active state comes from the route (useMatch('/priority')), and All Articles is active only when no feed/folder is selected AND the route is not /priority"
  - "05-04: articles(count) now formats with en-US thousands separators for both the Priority banner and the Interests dialog (identical output below 1,000)"

patterns-established:
  - "Status banners in list panels sit between the toolbar and the filter input, outside the state slot"
  - "Shared interest-status helpers (OPEN_BREAKER_STATES, articles) live only in utils/interest.ts"

requirements-completed: [PRIO-01, PRIO-07, PRIO-08]

coverage:
  - id: D1
    description: "Priority is the first feed-tree smart view (text only, no count), always rendered, active only on /priority; All Articles is not active there; clicking it clears feed/folder and navigates to /priority"
    requirement: PRIO-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedPanel.test.tsx#priorityIsTheFirstSmartViewWithoutACount"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/FeedPanel.test.tsx#priorityIsActiveOnlyOnItsRoute"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/FeedPanel.test.tsx#clickingPriorityNavigates"
        status: pass
    human_judgment: false
  - id: D2
    description: "g then p clears the selected feed and opens /priority without triggering the previous-unread-feed jump; plain p is unchanged; the overlay lists g then p before g then a"
    requirement: PRIO-07
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#gThenPOpensPriority"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#gThenPDoesNotJumpToThePreviousUnreadFeed"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#plainPStillJumpsToThePreviousUnreadFeed"
        status: pass
      - kind: other
        ref: "grep -n 'g then' src/main/frontend/src/components/ShortcutOverlay.tsx | head -1 (shows the g then p line)"
        status: pass
    human_judgment: false
  - id: D3
    description: "PriorityBanner shows exactly one status by D-14 precedence (not configured > cold start > paused OPEN/FORCED_OPEN > N waiting > none) with exact copy, thousands separators, no failed count, role=status; nothing while loading/failed; it sits between toolbar and filter and never replaces the list"
    requirement: PRIO-08
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityBanner.test.tsx (11 tests: rendersNothingWithoutStatus, notConfiguredWinsOverEverything, coldStartWinsOverPaused, coldStartButtonCallsTheCallback, pausedShowsTheWaitingCount, pausedWithNothingWaiting, halfOpenIsNotPaused, waitingUsesThousandsSeparators, noBannerWhenEverythingIsScored, failedCountIsNeverShown, bannerIsAStatusRegion)"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#bannerSitsAboveTheListAndNeverReplacesIt"
        status: pass
    human_judgment: false
  - id: D4
    description: "In cold start, Set up interests on /priority opens the Interests dialog through MainLayout's interestsOpen state"
    requirement: PRIO-08
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx#coldStartButtonCallsOnSetUpInterests"
        status: pass
      - kind: other
        ref: "grep -c 'onSetUpInterests={() => setInterestsOpen(true)}' src/main/frontend/src/App.tsx prints 1"
        status: pass
    human_judgment: false
  - id: D5
    description: "Banner visual fit: themed strip across the 6 themes, accent left border in cold start, text and button wrapping with no horizontal scroll at the minimum list-panel width (E2/overflow backstop)"
    verification: []
    human_judgment: true
    rationale: "jsdom does not lay out CSS; strip appearance, theme contrast and wrapping at narrow widths need a human look in a real browser"

duration: 5min
completed: 2026-09-25
status: complete
---

# Phase 5 Plan 04: Priority Entry, g p and Status Banner Summary

**Priority is now the first feed-tree smart view (highlighted only on `/priority`) and reachable with `g p`. The Priority panel shows at most one status line under the toolbar, picked in order: not configured, cold start (with a Set up interests button that opens the Interests dialog), scoring paused, or N waiting. The line reuses the existing 15s `useInterestStatus()` polling and never touches the list.**

## Performance

- **Duration:** about 5 min
- **Started:** 2026-09-25T20:00:49Z
- **Completed:** 2026-09-25T20:05:49Z
- **Tasks:** 3
- **Files modified:** 13 (2 created, 11 modified)

## Accomplishments

- **Feed-tree entry (PRIO-01, D-12, D-13):** `Priority` is the first `.smart-view`, text only with no count. It reads no status data, so it always renders. Its active class comes from `useMatch('/priority')`, and All Articles is no longer active on `/priority`. Clicking it clears the feed and folder selection and navigates to `/priority`.
- **`g p` (PRIO-07):** a new `case 'p'` in the g-chord switch calls `setSelectedFeed(null)` (which also clears the folder and article) and navigates to `/priority`. The chord branch returns before the plain-key switch, so `p` alone still jumps to the previous unread feed. The overlay lists `g then p` / `Go to Priority` directly before `g then a`.
- **Status banner (PRIO-08, D-14, D-17):** `PriorityBanner` renders one `<div class="priority-banner" role="status">` with the exact UI-SPEC copy and a bold lead phrase. HALF_OPEN does not count as paused. Counts use `eligibleUnscored` only, formatted as `1 article` / `1,204 articles`. The component never reads `failed` and adds no `refetchInterval`.
- **Placement:** the banner sits between the toolbar and the filter input, outside the state slot, so the rows, empty states and error states always render below it.
- **Cold-start CTA:** `PriorityList` takes `onSetUpInterests`, and `App.tsx` passes `() => setInterestsOpen(true)`, reusing MainLayout's dialog state (03-05).
- **Shared helpers:** `OPEN_BREAKER_STATES` and `articles()` now have one definition, in `utils/interest.ts`. `InterestsDialog` imports both.
- **Styling:** `.priority-banner` is a full-width strip built only from theme variables. It wraps (`flex-wrap`, `overflow-wrap: anywhere`) and has no border radius. `.cold-start` switches the left border to `var(--accent)`.

## Task Commits

1. **Task 1 (tracer): Priority reachable from the feed tree and with g p**: `ab6f255` (feat)
2. **Task 2: one status banner chosen by precedence**:
   - RED: `2368d02` (test)
   - GREEN: `0267a44` (feat)
3. **Task 3: cold-start CTA and banner styling**:
   - RED: `0cd772b` (test)
   - GREEN: `2e57070` (feat)

## Files Created/Modified

- `src/main/frontend/src/components/PriorityBanner.tsx`: the one-line status banner with D-14 precedence
- `src/main/frontend/src/components/PriorityBanner.test.tsx`: 11 precedence, copy and count tests
- `src/main/frontend/src/components/FeedPanel.tsx`: the Priority smart view, `handlePriorityClick`, and the route-aware All Articles active rule
- `src/main/frontend/src/components/FeedPanel.test.tsx`: `renderPanelAt` with a location probe; 3 Priority entry tests. The `setSelectedFeed` and `setSelectedFolder` store mocks are now hoisted so the tests can assert on them.
- `src/main/frontend/src/hooks/useKeyboardShortcuts.ts`: the g-chord `case 'p'`
- `src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts`: `priority: vi.fn()` in the articles API mock; 3 g-p / plain-p tests
- `src/main/frontend/src/components/ShortcutOverlay.tsx`: the `g then p` entry
- `src/main/frontend/src/components/PriorityList.tsx`: renders `PriorityBanner` and takes the `onSetUpInterests` prop
- `src/main/frontend/src/components/PriorityList.test.tsx`: routes `GET /api/interest/status` in the shared setup; placement and CTA tests
- `src/main/frontend/src/utils/interest.ts`: `OPEN_BREAKER_STATES` and `articles()`
- `src/main/frontend/src/components/InterestsDialog.tsx`: its local copies removed; imports both helpers from `utils/interest.ts`
- `src/main/frontend/src/App.tsx`: `/priority` route passes `onSetUpInterests={() => setInterestsOpen(true)}`
- `src/main/frontend/src/App.css`: `.priority-banner` rules

## Decisions Made

- `PriorityBanner` chooses its content by precedence and returns a single wrapper element. The four per-state wrappers in the first GREEN draft were collapsed into one before the commit, so the file has exactly one `role="status"` (acceptance grep) and the class switch (`cold-start`) lives in one place.
- The Interests dialog's waiting counts now also show thousands separators. Output below 1,000 is unchanged, and its existing tests (e.g. `312 articles waiting to be scored`) still pass.

## Deviations from Plan

None. The plan was executed as written.

## TDD Gate Compliance

Tasks 2 and 3 each have a RED `test(05-04)` commit before their GREEN `feat(05-04)` commit (`2368d02` then `0267a44`, and `0cd772b` then `2e57070`). Task 1 is the tracer and was committed as `feat`.

- **Task 2 RED:** 10 target tests failed on assertions and 21 passed. The 21 passes were the 19 existing PriorityList tests and 2 negative banner cases that the stub already satisfies (`rendersNothingWithoutStatus`, `noBannerWhenEverythingIsScored`). `PriorityBanner.tsx` was a signature-only stub returning `null` in the RED commit, so the test module loaded. It was replaced in `0267a44`.
- **Task 3 RED:** 1 target test failed (`Unable to find role="button" and name "Set up interests"`) and 20 passed. The RED commit changed only the test file. The test passes the new `onSetUpInterests` prop, which `PriorityList` did not accept yet, so `npx tsc -b` would have reported that one prop error at `0cd772b`. Vitest does not type-check, and the GREEN commit `2e57070` adds the prop, so `tsc -b` exits 0 at HEAD.
- **RED evidence:** both RED runs returned `RED_EVIDENCE_OK` (`target_test_failed`) from `check tdd-red-evidence`. The targets were `PriorityBanner > notConfiguredWinsOverEverything` and `PriorityList > coldStartButtonCallsOnSetUpInterests`, each passed as the full TAP name. As in 05-02, the `# tests/# pass/# fail` lines were appended to the vitest `tap-flat` output, with counts taken from the real `ok` / `not ok` lines.

## Tracer Gate

The run was interactive with `human_verify_mode: end-of-phase`, and Task 1's `<verify>` is automated-only. So its verify was re-run: both Task 1 test files passed (19 tests) and `npx tsc -b` exited 0. Execution then continued to Tasks 2 and 3 with no checkpoint.

## Issues Encountered

None.

## Verification

- `cd src/main/frontend && npm test`: 20 files, 161 tests, all passing.
- `npx tsc -b`: exits 0.
- `npx eslint` on every touched file: exits 0.
- Acceptance greps:
  - Each prints 1:
    - `useMatch('/priority')` in FeedPanel.tsx
    - `navigate('/priority')` in FeedPanel.tsx
    - `navigate('/priority')` in useKeyboardShortcuts.ts
    - `'g then p'` in ShortcutOverlay.tsx
    - `priority: vi.fn()` in useKeyboardShortcuts.test.ts
    - `export const OPEN_BREAKER_STATES` in utils/interest.ts
    - `role="status"` in PriorityBanner.tsx
    - `onSetUpInterests={() => setInterestsOpen(true)}` in App.tsx
    - `var(--accent)` in `.priority-banner.cold-start`
  - Each prints 0:
    - `OPEN_BREAKER_STATES = ` in InterestsDialog.tsx
    - `\.failed` in PriorityBanner.tsx
    - `refetchInterval` in PriorityBanner.tsx
    - hex literals in the `.priority-banner` rules
    - `border-radius` in `.priority-banner`
  - The first `g then` line in the overlay is `g then p`.
  - `function articles(` appears only in `utils/interest.ts`.
- Threat model:
  - T-05-14: the banner renders only fixed copy and one number as React text. `breakerState` is compared, never displayed.
  - T-05-15: `failed` is never read (`failedCountIsNeverShown` plus the grep).
  - T-05-16: no new polling interval.
- Prohibitions:
  - The Priority entry has no status dependency (it reads no status data).
  - The banner never replaces the list (`bannerSitsAboveTheListAndNeverReplacesIt`).
  - Every commit staged its files by explicit path. The user's uncommitted `.claude/CLAUDE.md`, `.envrc`, `.planning/config.json` and `CLAUDE.md` changes remain unstaged.

## Known Stubs

None. The Task 2 RED stub of `PriorityBanner` was replaced in `0267a44`.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 05-05 can add badges to `ArticleList` and `BoardArticleList`. This plan did not change those files.
- 05-06 can extend the g-chord and keyboard callbacks. The UI-SPEC's `i` overlay line and the `r` relabel remain for 05-06 and 05-07.
- The visual fit of the banner (D5) is queued for the end-of-phase human check.

## Self-Check: PASSED

- Both created files exist on disk: `PriorityBanner.tsx` and `PriorityBanner.test.tsx`.
- Commits `ab6f255`, `2368d02`, `0267a44`, `0cd772b` and `2e57070` are present in `git log`. `git rev-list --count 25312d4..HEAD` returns 5.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*
