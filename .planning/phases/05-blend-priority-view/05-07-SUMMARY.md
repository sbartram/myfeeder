---
phase: 05-blend-priority-view
plan: 07
subsystem: ui
tags: [react, zustand, keyboard, interest-ranking, breakdown]
status: complete

requires:
  - phase: 05-03
    provides: "interestBreakdown {raw,total,display,rows,nonMatching} on GET /api/articles/{id}; rows apportioned to sum exactly to total"
  - phase: 05-02
    provides: "InterestBadge, formatSigned, tierOf"
  - phase: 05-06
    provides: "usePriorityStore (session-only Zustand store)"
provides:
  - "TS types ProfileBreakdownRow, TopicBreakdownRow, BreakdownRow, NonMatchingTopic, InterestBreakdown; Article.interestBreakdown"
  - "usePriorityStore.whyOpen / toggleWhy (session-only, shared by the Why button and i)"
  - "ScoreRow({ article }): badge + matched-topic chips + Why toggle between the title and .article-meta"
  - "WhyBreakdown({ breakdown, articleId }): server rows, total line equal to the badge (capped/floored wording), non-matching footer"
  - "utils/interest.ts PROFILE_LEVEL_LABELS"
  - "Keyboard i (Toggle score breakdown) and its overlay entry after v"
  - "CSS .score-row, .topic-chip, .chip-sep, .why-toggle, .why-breakdown, .why-label, .why-points, .why-total, .why-muted, .why-empty, .why-more"
affects: [phase-06-feedback]

actuals:
  tokens: 8167
  tasks: 3
  commits: 5
plan_head_before: 4fe901a16282d12bc4b784bfedb55848d252ad0f

tech-stack:
  added: []
  patterns:
    - "Explanation UI renders server numbers only: points and totals come from the payload; the client formats presentation percentages (round(noul x 100), round(hinge x 100)) and signs"
    - "Cross-article UI toggles live in the session-only usePriorityStore; per-article disclosure state is local useState reset by useEffect on articleId"

key-files:
  created:
    - src/main/frontend/src/components/ScoreRow.tsx
    - src/main/frontend/src/components/WhyBreakdown.tsx
    - src/main/frontend/src/components/WhyBreakdown.test.tsx
  modified:
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/stores/priorityStore.ts
    - src/main/frontend/src/components/ReadingPane.tsx
    - src/main/frontend/src/components/ReadingPane.test.tsx
    - src/main/frontend/src/App.css
    - src/main/frontend/src/utils/interest.ts
    - src/main/frontend/src/utils/interest.test.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
    - src/main/frontend/src/components/ShortcutOverlay.tsx

key-decisions:
  - "05-07: The reading pane explains a score only from GET /api/articles/{id}: ScoreRow shows chips for TOPIC rows with weight != 0 in server order, and WhyBreakdown prints server points plus a total line whose value always equals the badge; the client never recomputes points"
  - "05-07: whyOpen/toggleWhy live in usePriorityStore (session-only), shared by the Why button and the i shortcut; the non-matching footer is local state that collapses on every article change"

patterns-established:
  - "Score explanation components are text-only React nodes (no raw HTML), which covers T-05-22"

requirements-completed: [PRIO-03, PRIO-04, PRIO-07]

coverage:
  - id: D1
    description: "Reading pane score row between the title and .article-meta: tier badge, sign-colored matched-topic chips (trailing U+2212 on negatives, none for weight 0, full name in title), and a Why toggle with aria-expanded/aria-controls; nothing for unscored articles"
    requirement: PRIO-03
    verification:
      - kind: automated_ui
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#scoreRowShowsBadgeChipsAndWhy, scoreRowSitsBetweenTitleAndMeta, unscoredArticleHasNoScoreRow, scoredWithoutMatchedTopicsShowsBadgeAndWhyOnly, chipCarriesItsFullName"
        status: pass
    human_judgment: false
  - id: D2
    description: "Why toggle state is one session boolean (usePriorityStore.whyOpen) that survives switching articles; the open breakdown renders after .score-row and before .article-meta"
    requirement: PRIO-04
    verification:
      - kind: automated_ui
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#whyToggleFlipsTheSessionState, whyOpenShowsTheBreakdown"
        status: pass
    human_judgment: false
  - id: D3
    description: "Why breakdown: profile row with level label, topic rows 'name  counts% × signedWeight' with the math in title, server points with explicit sign (never recomputed), capped/floored/Score total line equal to the badge, empty-state line, non-matching footer (singular/plural/omitted) that collapses on article change"
    requirement: PRIO-04
    verification:
      - kind: automated_ui
        ref: "src/main/frontend/src/components/WhyBreakdown.test.tsx#rendersRowsWithSignedServerPoints, topicRowTitleShowsTheMath, weightZeroRowShowsUnsignedZero, cappedTotalLine, flooredTotalLine, nothingContributed, profileRowAbsentWithoutProfile, footerSingularPluralAndOmitted, footerTogglesAndShowsMatch, footerCollapsesOnArticleChange, rendersServerPointsWithoutRecomputing"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#levelLabels"
        status: pass
    human_judgment: false
  - id: D4
    description: "i toggles the breakdown for a scored selected article in every view; no-op when unscored, nothing selected, or typing in an input; overlay lists i after v"
    requirement: PRIO-07
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#iTogglesTheBreakdownForAScoredArticle, iDoesNothingForAnUnscoredArticle, iDoesNothingWithNothingSelected, iIsIgnoredWhileTyping"
        status: pass
    human_judgment: false
  - id: D5
    description: "Visual layout in the real app: chips wrap without horizontal scroll in a narrow pane (E5/overflow), long breakdown labels wrap without overlapping points (E6/long-text), and the end-to-end flow (badge, chips, i opens Why N?, stays open across j, the footer collapses on article change)"
    requirement: PRIO-04
    verification: []
    human_judgment: true
    rationale: "Backstop truths are CSS layout (flex-wrap, grid 1fr/auto) that jsdom cannot measure; Task 3's <human-check> asks for a bootTestRun + npm run dev walkthrough at end of phase"

duration: 5min
completed: 2026-09-25
---

# Phase 5 Plan 07: Reading-Pane Score Row and Why Breakdown Summary

**For a scored article, the reading pane now shows three things under the title: the tier badge, sign-colored chips for the topics that moved the score, and a "Why N?" toggle. The toggle, or the `i` key, opens an exact breakdown. It prints the server's profile and topic points, and its last line always equals the badge: `Score N`, `Total N → capped at 100`, or `Total −N → floored at 0`. Non-matching topics sit behind a footer.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-09-25T20:22:22Z
- **Completed:** 2026-09-25T20:28:04Z
- **Tasks:** 3
- **Files modified:** 13 (3 created, 10 modified)

## Accomplishments

- `ScoreRow` renders between `.article-title` and `.article-meta`, and only when `interestScore != null`. It shows:
  - the badge
  - one plain-text chip per matched topic with `weight != 0`, in the server's order, `.weight-positive` / `.weight-negative`, with a trailing `−` on negatives
  - `' · '` separators
  - a `Why N? ▸/▾` button carrying `aria-expanded` and `aria-controls="why-breakdown"`
- `WhyBreakdown` renders the server's rows as a two-column grid:
  - The profile row reads `Profile match ({label})`. A topic row reads `name  counts% × ±weight`, and its `title` shows the full math.
  - Points are the server's integers with an explicit sign, never recomputed.
  - The total line equals the badge. With no rows it shows `No profile or topic contributed to this score.`
  - The `Show n non-matching topic(s) ▸` footer uses singular or plural and is omitted when there are none. It collapses whenever `articleId` changes.
- `whyOpen` / `toggleWhy` in the session-only `usePriorityStore` are shared by the Why button and the new `i` shortcut. The shortcut works in every view and is ignored while typing. The overlay lists `i` as `Toggle score breakdown` directly after `v`.
- `PROFILE_LEVEL_LABELS` = None, In passing, Partly, Mainly, Core interest (index = server `levelIndex`).

## Task Commits

1. **Task 1 (tracer): badge, chips and Why toggle in the reading pane**: `ec75893` (feat). The tracer gate re-ran the verify and it passed.
2. **Task 2: exact Why breakdown**: `074e21e` (test, RED), then `7aab6c9` (feat, GREEN)
3. **Task 3: `i` toggles the breakdown**: `cc53170` (test, RED), then `ef37eb6` (feat, GREEN)

## Files Created/Modified

- `components/ScoreRow.tsx` (new): badge, chips, Why toggle
- `components/WhyBreakdown.tsx` (new): the breakdown panel
- `components/WhyBreakdown.test.tsx` (new): 11 tests
- `types/index.ts`: breakdown types and `Article.interestBreakdown`
- `stores/priorityStore.ts`: `whyOpen`, `toggleWhy`
- `components/ReadingPane.tsx`: `<ScoreRow>` after the title; `<WhyBreakdown>` when scored, open and a breakdown is present
- `components/ReadingPane.test.tsx`: 7 new tests; resets `whyOpen` in `beforeEach`
- `utils/interest.ts`, `utils/interest.test.ts`: `PROFILE_LEVEL_LABELS` plus its test
- `hooks/useKeyboardShortcuts.ts`, `hooks/useKeyboardShortcuts.test.ts`: `case 'i'` plus 4 tests
- `components/ShortcutOverlay.tsx`: the `i` entry
- `App.css`: the score row and breakdown rules. They use theme variables only, with em sizes inside the reading pane.

## Decisions Made

- Chips mark only topics that moved the score. A matched topic with weight 0 appears as a breakdown row (`Zero  80% × 0` → `0`) but gets no chip (research Open Question 3).
- The Why breakdown is embedded in `GET /api/articles/{id}`. It has no separate fetch, so it has no loading or error UI of its own (E6 loading/error do not apply).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Non-matching rows also carry the label and points column classes**
- **Found during:** Task 2 (GREEN)
- **Issue:** The plan gives the non-matching cells only `why-muted`. That would leave a long non-matching name without `overflow-wrap`, and its `N% match · 0` value would be left-aligned instead of in the right-aligned points column.
- **Fix:** The name cell is `why-label why-muted` and the value cell is `why-points why-muted`. `.why-muted` is declared later, so the text stays muted.
- **Files modified:** `src/main/frontend/src/components/WhyBreakdown.tsx`
- **Verification:** The WhyBreakdown tests still pass (30/30 across the Task 2 verify set), and `npx tsc -b` exits 0.
- **Committed in:** `7aab6c9`

---

**Total deviations:** 1 auto-fixed (Rule 1). **Impact:** a presentation-only fix inside the plan's own CSS vocabulary, with no scope change.

## TDD Gate Compliance

Tasks 2 and 3 (`tdd="true"`) each have a RED `test(05-07)` commit before their GREEN `feat(05-07)` commit. No REFACTOR commits were needed.

- **Task 2 RED (`074e21e`):** The commit ships compile-only placeholders so that RED fails on assertions, not on module load (the 05-03 precedent): a `WhyBreakdown` that returns null and an empty `PROFILE_LEVEL_LABELS`. 12 target tests failed and 18 existing ones passed. The target test was `rendersServerPointsWithoutRecomputing`, and `check tdd-red-evidence` returned `RED_EVIDENCE_OK` (vitest JSON transcribed to TAP).
- **Task 3 RED (`cc53170`):** `iTogglesTheBreakdownForAScoredArticle` failed on `expected false to be true`, and `check tdd-red-evidence` returned `RED_EVIDENCE_OK`. The three no-op guard tests (unscored, nothing selected, typing) passed in RED by construction, because they assert that nothing happens. They pin the guards once `case 'i'` exists.

## Verification

- `cd src/main/frontend && npm test`: 23 files, 218/218 tests passed. `npx tsc -b` exits 0.
- `DOCKER_HOST=… ./gradlew build`: BUILD SUCCESSFUL (backend suite plus the embedded frontend build), with no TypeSafe or profile env vars in the shell.
- All acceptance greps for Tasks 1 to 3 printed their expected values. That includes 0 hex colors in the new CSS and 0 `dangerouslySetInnerHTML` in the phase's new components.

## Issues Encountered

None.

## Known Stubs

None.

## User Setup Required

None: no external service configuration required.

## Next Phase Readiness

- Phase 05's seven plans are complete.
- Task 3's human-check is queued for end-of-phase UAT: with `./gradlew bootTestRun` and `npm run dev` running and a scored article selected, check the badge and chips, press `i`, and confirm the last line equals the badge. Then press `j` and confirm the breakdown stays open. Finally, open the non-matching footer, change the article, and confirm it collapses.
- One visual item to look at during UAT: the rule above the total line is a `border-top` on both total cells (per the plan's CSS), so it may show a 16px break at the column gap.
- Phase 6 (feedback) can add thumbs buttons beside `ScoreRow`, since the score row and breakdown make no assumptions about feedback.

## Self-Check: PASSED

- The created files exist: `ScoreRow.tsx`, `WhyBreakdown.tsx`, `WhyBreakdown.test.tsx`.
- The commits exist on `sbartram/main`: `ec75893`, `074e21e`, `7aab6c9`, `cc53170`, `ef37eb6`. That is 5 commits since `plan_head_before` `4fe901a`.
- The acceptance criteria and the plan-level verification were re-run after the last commit, and they passed.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-25*
