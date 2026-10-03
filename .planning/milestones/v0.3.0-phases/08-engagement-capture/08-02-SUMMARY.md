---
phase: 08-engagement-capture
plan: 02
subsystem: ui
tags: [react, tanstack-query, vitest, engagement, window-open]

requires:
  - phase: 08-engagement-capture
    provides: "08-01 serves PUT /api/articles/{id}/engagement/open (204); this plan's tests mock fetch, so either order is correct"
provides:
  - "useOpenOriginal(): the single Open Original path (toolbar button, reader-view fallback button, o shortcut)"
  - "articlesApi.recordOpen(id): bodyless PUT /api/articles/{id}/engagement/open"
  - "apiPut<T>(path, body?): JSON headers and body only when a body is given"
  - "EngagementKind union and optional Article.engagement (client contract rendered by 08-04)"
affects: [08-04 forget control, 08-05 release, phase 9 learned engagement]

actuals:
  tokens: 4770
  tasks: 2
  commits: 3
plan_head_before: 52c830c62a2258f855deb6e0646b92bc72d1b7c4
plan_head_after: 7e34634a9b8172dc463681bdb79c005274a43883

tech-stack:
  added: []
  patterns:
    - "Fire-and-forget report: window.open first, then Promise.resolve().then(api).then(exact invalidate).catch(() => {}); no useMutation, so the global MutationCache toast never fires"

key-files:
  created:
    - src/main/frontend/src/hooks/useEngagement.ts
    - src/main/frontend/src/hooks/useEngagement.test.ts
  modified:
    - src/main/frontend/src/api/client.ts
    - src/main/frontend/src/api/articles.ts
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/components/ReadingPane.tsx
    - src/main/frontend/src/components/ReadingPane.test.tsx
    - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts

key-decisions:
  - "A blank or whitespace article.url neither opens a tab nor records (research A3)"
  - "apiPut sends the JSON header and body when body !== undefined (not truthiness), so an explicit falsy body is still serialized"

patterns-established:
  - "Engagement reports refresh only ['article', id] with exact: true, never ['priority'], ['articles'] or ['article', id, 'extracted']"

requirements-completed: [CAPT-01, CAPT-07]

coverage:
  - id: D1
    description: "useOpenOriginal opens the tab synchronously before a bodyless PUT, invalidates only the exact by-id query on success, swallows network/404/500 errors without a toast, sends one PUT per open and skips a blank URL"
    requirement: CAPT-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useEngagement.test.ts#useOpenOriginal (6 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Toolbar Open Original and the reader-view extraction-error fallback both call the helper"
    requirement: CAPT-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#toolbarOpenOriginalGoesThroughTheHelper, extractionErrorFallbackGoesThroughTheHelper"
        status: pass
    human_judgment: false
  - id: D3
    description: "The o shortcut opens first, then calls recordOpen; it stays silent when recording fails and does nothing without a selection"
    requirement: CAPT-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#'o' opens the selected article's URL..., 'o' still opens and stays silent..., 'o' does nothing without a selected article"
        status: pass
    human_judgment: false
  - id: D4
    description: "In-body link clicks and Copy Link never call the helper or the API"
    requirement: CAPT-07
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#inBodyLinkOpensButIsNotReported, copyLinkIsNotReported"
        status: pass
    human_judgment: false
  - id: D5
    description: "Live check with the backend stopped: the o key opens a tab and no error toast appears (the button half was approved at the tracer checkpoint)"
    requirement: CAPT-01
    verification: []
    human_judgment: true
    rationale: "Real-browser popup and user-activation behavior, which jsdom cannot show; deferred to end-of-phase UAT by the coordinator"

duration: 37min
completed: 2026-09-30
status: complete
---

# Phase 8 Plan 02: Open Original Engagement Capture (client) Summary

**One `useOpenOriginal` helper now serves the toolbar button, the reader-view fallback and `o`. It opens the tab synchronously with `noopener`, then sends a fire-and-forget bodyless `PUT /api/articles/{id}/engagement/open` that swallows every error, and a successful report refreshes only `['article', id]`.**

## Performance

- **Duration:** 37 min, including the wait at the tracer checkpoint
- **Started:** 2026-09-30T02:01:54Z
- **Completed:** 2026-09-30T02:39:14Z
- **Tasks:** 2
- **Files modified:** 9 (2 created, 7 modified)

## Accomplishments
- `useOpenOriginal` runs in this order:
  - A blank URL does nothing.
  - Otherwise `window.open(url, '_blank', 'noopener')` runs first.
  - Then a `Promise.resolve()` chain runs `recordOpen`, an exact invalidation of `['article', id]`, and a catch that swallows every error.
  - There is no `useMutation` and no client dedupe.
- All three Open Original entry points share the helper (D-10). In-body links and Copy Link are proven unreported (CAPT-07).
- `apiPut` takes an optional body. Existing callers (`setFeedback`, `boardsApi.update`) still send JSON with Content-Type.
- The client contract is in place for 08-04: `EngagementKind` and the optional `Article.engagement`.

## Task Commits

1. **Task 1 (tracer): Open Original opens first, then reports via a bodyless PUT:** `1eb8d18` (feat)
2. **Task 2: `o` goes through the helper; in-body links and Copy Link are unreported:**
   - RED: `8262853` (test)
   - GREEN: `7e34634` (feat)

No REFACTOR commit was needed.

## Tracer Checkpoint

The tracer feedback gate stopped after Task 1. The run was interactive, the mode was `end-of-phase`, and the tracer carried a `<human-check>`. **The user approved the checkpoint ("approved").** The `o`-key half of the tracer human-check (backend stopped, press `o`, the tab opens, no toast) is **deferred to end-of-phase UAT**, as coverage item D5 records.

## TDD Gate Compliance (Task 2)

- **RED:** `8262853`. The extended `'o'` test and the "stays silent" test failed on `expected "vi.fn()" to be called with arguments: [ 7 ]`, because `o` still called `window.open` directly. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` / `target_test_failed` on the Vitest JUnit output.
  - Checker quirk: its `name="..."` regex matches inside `classname="..."`, so every JUnit case reads as the file name.
  - Keyed on `targetFile`, the checker classified this as `fixture_or_load_failure`. The record was therefore keyed on the test class, without `targetFile`.
  - The XML shows the two named `'o'` test cases failing on assertions, which is a real RED.
- The CAPT-07 negatives and the "no selection" test passed at RED. That is expected: they guard behavior that already existed and must not regress.
- **GREEN:** `7e34634`. The three files passed 56 tests, and the full suite passed 334.

## Files Created/Modified
- `src/main/frontend/src/hooks/useEngagement.ts`: `useOpenOriginal`, with a header comment giving the open, report and silent-failure rules.
- `src/main/frontend/src/hooks/useEngagement.test.ts`: six fetch-level tests covering order, bodyless PUT, exact invalidation, network, 404 and 500 silence, one PUT per open, and blank URL.
- `src/main/frontend/src/api/client.ts`: `apiPut(path, body?)`.
- `src/main/frontend/src/api/articles.ts`: `recordOpen(id)`.
- `src/main/frontend/src/types/index.ts`: `EngagementKind` and `Article.engagement?`.
- `src/main/frontend/src/components/ReadingPane.tsx`: `handleOpenOriginal` calls `openOriginal(article)`. `handleContentClick` and `handleCopyLink` are unchanged.
- `src/main/frontend/src/components/ReadingPane.test.tsx`: mocks `../hooks/useEngagement`, and adds two helper tests and two CAPT-07 negatives.
- `src/main/frontend/src/hooks/useKeyboardShortcuts.ts`: `case 'o'` calls `openOriginal(currentArticle)`, and `openOriginal` is in the deps.
- `src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts`: `recordOpen` added to the full-replacement articles mock; the `'o'` test is extended and two tests are new.

## Decisions Made
- A blank URL returns early with no tab and no record, per research A3 as the plan specified.
- `apiPut` gates the header and body on `body !== undefined` rather than `apiPost`'s truthiness check. The only intended bodyless call is `recordOpen`, and a falsy but explicit body still serializes.

## Deviations from Plan

None. The plan was executed as written.

## Issues Encountered
- The `check tdd-red-evidence` JUnit name-parsing quirk described under TDD Gate Compliance. It is a tool issue, not a plan issue.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- 08-04 can render `article.engagement` and add a Forget mutation to `hooks/useEngagement.ts`.
- Until 08-01 merges, the PUT returns 404 in a live run, and the client swallows it silently by design.
- The `o`-key live check is pending in end-of-phase UAT.

---
*Phase: 08-engagement-capture*
*Completed: 2026-09-30*

## Self-Check: PASSED
- Created files exist: `useEngagement.ts`, `useEngagement.test.ts`.
- Commits exist: `1eb8d18`, `8262853`, `7e34634`.
- `npx tsc -b` exits 0, and `npx vitest run` passes 30 files and 334 tests.
- The acceptance greps pass: `openOriginal(currentArticle)` is in `useKeyboardShortcuts.ts`, `openOriginal(article)` is in `ReadingPane.tsx`, `openOriginal` is in the deps, and `'noopener'`, `exact: true` and `articlesApi.recordOpen` are in `useEngagement.ts`.
