---
phase: 05-blend-priority-view
plan: 05
subsystem: ui
tags: [react, typescript, vitest, interest-badge, article-list, board]

# Dependency graph
requires:
  - phase: 05-02
    provides: InterestBadge component, TS Article.interestScore, CSS .article-item-head / .interest-badge-slot / read-row badge dimming
  - phase: 05-03
    provides: interestScore on every GET /api/articles and GET /api/boards/{id}/articles item
provides:
  - Interest badges in All Articles, Feed, Folder and Starred rows (ArticleList)
  - Interest badges in Board rows (BoardArticleList)
  - reserveSlot memo in both lists (empty slot only when some loaded row is scored)
  - BoardArticleList.test.tsx; mutable mockItems in ArticleList.test.tsx
affects: [05-06, 05-07, 06-feedback]

# Actuals (#2632)
actuals:
  tokens: 2902
  tasks: 2
  commits: 4
plan_head_before: fb9614ed6a29f52806035bf00b15691a6fff3c6f

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Non-Priority list rows: .article-item-head wraps badge-or-slot and the unchanged title; slot only when reserveSlot"
    - "List tests mock the data hook with a module-level let mockItems read lazily by the mock function"

key-files:
  created:
    - src/main/frontend/src/components/BoardArticleList.test.tsx
  modified:
    - src/main/frontend/src/components/ArticleList.tsx
    - src/main/frontend/src/components/ArticleList.test.tsx
    - src/main/frontend/src/components/BoardArticleList.tsx

key-decisions:
  - "05-05: Outside Priority the empty badge slot is reserved only when at least one loaded row is scored (reserveSlot over allArticles, before the search filter), so lists with no scores render exactly as before"
  - "05-05: Lists render the badge only (no chips, no Why toggle) and never reorder by score; sorting by interest stays Priority-only"

patterns-established:
  - "Badge slot rule: interestScore != null -> InterestBadge; else reserveSlot && aria-hidden .interest-badge-slot"

requirements-completed: [PRIO-03]

coverage:
  - id: D1
    description: "All Articles, Feed, Folder and Starred rows show a tiered interest badge before the title for scored articles, read rows included, in query order"
    requirement: PRIO-03
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/ArticleList.test.tsx#scoredRowsShowTheirBadge"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ArticleList.test.tsx#readRowKeepsItsBadge"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ArticleList.test.tsx#rowsKeepTheirQueryOrder"
        status: pass
    human_judgment: false
  - id: D2
    description: "Unscored rows show no badge and never 0; the empty slot appears only when some loaded row in the list is scored"
    requirement: PRIO-03
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/ArticleList.test.tsx#unscoredRowReservesTheSlotWhenAnyRowIsScored"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ArticleList.test.tsx#noSlotWhenNothingIsScored"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ArticleList.test.tsx#noZeroForUnscored"
        status: pass
    human_judgment: false
  - id: D3
    description: "Board rows show the same badges and slot rule, read rows included"
    requirement: PRIO-03
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/BoardArticleList.test.tsx#boardRowsShowBadges"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/BoardArticleList.test.tsx#boardWithoutScoresHasNoSlot"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/BoardArticleList.test.tsx#boardReadRowKeepsItsBadge"
        status: pass
    human_judgment: false
  - id: D4
    description: "Badges look right in the running app (alignment of titles with and without the slot, dimmed badge on read rows, list font sizes)"
    requirement: PRIO-03
    verification: []
    human_judgment: true
    rationale: "Visual alignment and dimming across themes and list font sizes are not asserted by jsdom tests"

# Metrics
duration: 3min
completed: 2026-09-25
status: complete
---

# Phase 05 Plan 05: Interest Badges in Every List Summary

**ArticleList (All, Feed, Folder, Starred) and BoardArticleList rows now show the tiered 0-100 InterestBadge before the title, with an aria-hidden empty slot on unscored rows only when some loaded row is scored**

## Performance

- **Duration:** 3 min
- **Started:** 2026-09-25T20:08:56Z
- **Completed:** 2026-09-25T20:11:49Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- All Articles, Feed, Folder and Starred rows show each scored article's badge in place, read rows included (dimmed by the 05-02 CSS)
- Unscored rows show no badge and never the text 0; lists with no scored rows render exactly as before (no slot, no leading gap)
- Board rows follow the same badge and slot rule
- Row order is untouched: both lists render in query order, and the preserved selected-row copy keeps its badge because it spreads the article including `interestScore`

## Task Commits

1. **Task 1 (tracer): ArticleList badges** - `815f54a` (test, RED), `f99c08d` (feat, GREEN)
2. **Task 2: BoardArticleList badges** - `69eaf1f` (test, RED), `5d7d5b8` (feat, GREEN)

No refactor commits; the implementation needed no cleanup.

## Files Created/Modified
- `src/main/frontend/src/components/ArticleList.tsx` - `reserveSlot` memo; `.article-item-head` with `InterestBadge` or the reserved slot
- `src/main/frontend/src/components/ArticleList.test.tsx` - `useArticles` mock reads a mutable `mockItems`; six badge behaviors added, existing split-button tests unchanged
- `src/main/frontend/src/components/BoardArticleList.tsx` - the same change as ArticleList, no inline font size
- `src/main/frontend/src/components/BoardArticleList.test.tsx` - new; partial `useBoards` mock via `importOriginal`, real `useUIStore` reset in `beforeEach`

## Decisions Made
- `reserveSlot` is computed over `allArticles` (all loaded rows), not the search-filtered rows, matching the plan and the UI-SPEC "at least one loaded row" wording.
- Otherwise followed the plan as written.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- `gsd-tools check tdd-red-evidence` parses node:test-style TAP summary lines (`# tests/# pass/# fail`), which Vitest's `tap-flat` reporter does not print. The RED records were built from the real `--reporter=tap-flat` output with those three lines appended, counted mechanically from its `ok`/`not ok` lines, and the target test named by its full reported name. Both RED runs then returned `RED_EVIDENCE_OK` (target tests `scoredRowsShowTheirBadge` and `boardRowsShowBadges` failed on their badge assertions). This affects only the evidence tooling, not the code.

## TDD Gate Compliance

| Task | RED | GREEN | REFACTOR |
|------|-----|-------|----------|
| 1 | `815f54a` test(05-05) - 4 badge tests failed, the 2 absence guards and 3 existing tests passed | `f99c08d` feat(05-05) - 9/9 pass | not needed |
| 2 | `69eaf1f` test(05-05) - 2 badge tests failed, the no-slot guard passed | `5d7d5b8` feat(05-05) - 12/12 pass | not needed |

## Verification
- `npx vitest run src/components/BoardArticleList.test.tsx src/components/ArticleList.test.tsx`: 12 passed
- `npx tsc -b`: exit 0, no `error TS`
- `npm test`: 21 files, 170 tests passed
- Acceptance greps: ArticleList `<InterestBadge` 1, `reserveSlot` 2, `article-item-head` 1, sort pattern 0; BoardArticleList `<InterestBadge` 1, `reserveSlot` 2, `style=` 0

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Ready for 05-06 (Priority refresh, hint and keyboard) and 05-07 (reading-pane score row); no shared files with this plan.
- The visual check of badges in the running app (D4) belongs in end-of-phase UAT.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*

## Self-Check: PASSED
- FOUND: src/main/frontend/src/components/ArticleList.tsx, ArticleList.test.tsx, BoardArticleList.tsx, BoardArticleList.test.tsx
- FOUND commits: 815f54a, f99c08d, 69eaf1f, 5d7d5b8
