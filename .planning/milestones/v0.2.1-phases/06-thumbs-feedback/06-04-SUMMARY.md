---
phase: 06-thumbs-feedback
plan: 04
subsystem: ui
tags: [react, typescript, zustand, tanstack-query, vitest, keyboard-shortcuts, interest-ranking, thumbs-feedback]

requires:
  - phase: 06-thumbs-feedback
    provides: "06-03 useVoteFeedback { press, narrow }, matchedTopics, formatVoteToast leads, FeedbackBar and its fetch-stub harness, .feedback-group"
  - phase: 06-thumbs-feedback
    provides: "06-02 Article.feedback {vote, narrowed, topics} on GET /api/articles/{id}"
provides:
  - "NarrowPicker({ article, onApply, onClose }): the thumbs-down topic picker popover (D-14, D-15)"
  - "FeedbackBar narrow control (.toolbar-btn.narrow-toggle) with the narrowed label, picker mount, close rules and focus return (D-13, D-16, D-17)"
  - "useFeedbackStore { narrowOpen, setNarrowOpen }: session-only, shared by Shift+D and the pane"
  - "utils/feedback.ts: canNarrow, narrowedPicks, narrowLabel"
  - "Keyboard: u / d vote with the toggle rule, Shift+D opens the picker; overlay rows u / d and Shift+D"
  - "CSS: .narrow-toggle.narrowed, .narrow-toggle-name, .narrow-picker and its parts"
affects: [06-05, 06-07]

actuals:
  tokens: 12685
  tasks: 3
  commits: 5
plan_head_before: d73b743b112a77c139ddaef0ce87decdada88c9b

tech-stack:
  added: []
  patterns:
    - "Popover key isolation: the popover's React onKeyDown calls preventDefault + stopPropagation for its own keys, so document-level shortcut listeners never see them (React root delegation)"
    - "Session-only zustand flag bridges a keyboard hook in MainLayout and a component in the reading pane"
    - "Focus return on close: remember the previous open state in a ref and, on true -> false, focus the trigger or a fallback element"

key-files:
  created:
    - src/main/frontend/src/components/NarrowPicker.tsx
    - src/main/frontend/src/components/NarrowPicker.test.tsx
    - src/main/frontend/src/stores/feedbackStore.ts
  modified:
    - src/main/frontend/src/components/FeedbackBar.tsx
    - src/main/frontend/src/components/FeedbackBar.test.tsx
    - src/main/frontend/src/utils/feedback.ts
    - src/main/frontend/src/App.css
    - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
    - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
    - src/main/frontend/src/components/ShortcutOverlay.tsx

key-decisions:
  - "Enter on a focused Cancel or Apply button keeps its native click; only Enter elsewhere in the picker applies. Every Enter still stops propagation, so Tab-to-Cancel then Enter cancels instead of applying"
  - "Shift+D also ignores Cmd/Ctrl/Alt, like u and d, so a modified Shift+D never opens the picker"
  - "The outside-mousedown scope falls back to the picker root when it is rendered outside a .feedback-group (standalone use in tests)"

patterns-established:
  - "Picker tests: pure-props render with vi.fn() callbacks, plus a renderInGroup helper that wraps the picker in .feedback-group next to a narrow control"
  - "FeedbackBar harness reads the selected id from uiStore, so tests can switch articles, and can add a focusable .reading-content"

requirements-completed: [FDBK-01, FDBK-06]

coverage:
  - id: D1
    description: "A 👎 on 2 or more matched topics (or a narrowed 👎) shows Narrow… after 👎 Down; Apply re-saves the 👎 for a strict subset (topicIds), un-narrows when all are checked, closes without a request when unchanged, and never sends an empty set"
    requirement: FDBK-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx#narrowControlShowsForADownVoteOnSeveralTopics, applyingASubsetNarrowsTheVote"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/NarrowPicker.test.tsx#applyingASubsetCallsOnApplyWithIds, applyingAllMatchedUnNarrows, unchangedSetClosesWithoutApplying, applyIsDisabledWithTheHintAtZero"
        status: pass
    human_judgment: false
  - id: D2
    description: "Narrowed label: '{name} only', '{a}, {b} only', '{k} of {n} topics' or 'No topics', with the full label in title and aria-label 'Penalizing {label}. Change topics'; clicking re-opens with the picks checked; 👎 Down again removes the vote; flipping to 👍 clears the narrowing"
    requirement: FDBK-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx#narrowedLabels, clickingTheNarrowedLabelReopensThePicker, pressingDownAgainRemovesTheWholeVote, flippingToUpClearsNarrowing"
        status: pass
    human_judgment: false
  - id: D3
    description: "Picker contents: matched topics in breakdown order with key hints 1-9 (blank from 10), sign-colored names with a trailing − on negatives, '{n}% match', initial checks (picks ∩ matched, or all), and the empty-list message with Apply hidden"
    requirement: FDBK-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/NarrowPicker.test.tsx#listsMatchedTopicsInBreakdownOrder, startsWithEveryMatchedTopicCheckedWhenNotNarrowed, startsWithThePicksWhenNarrowed, optionsBeyondNineHaveNoKeyHint, emptyListShowsTheMessageAndHidesApply"
        status: pass
    human_judgment: false
  - id: D4
    description: "The picker is keyboard-driven (1-9, Enter, Esc) and its keys never reach the global shortcut handler; focus goes to the first checkbox on open; it closes on outside mousedown, a different article or the vote leaving 👎, and focus returns to the control or .reading-content; a rejected narrowing reverts the label and shows the narrowing error toast"
    requirement: FDBK-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/NarrowPicker.test.tsx#focusMovesToTheFirstCheckboxOnOpen, numberKeysToggleOptions, enterAppliesWhenSomethingIsChecked, enterDoesNothingWithNoneChecked, escapeCancels, pickerKeysNeverReachTheDocument, outsideMouseDownCloses, insideMouseDownKeepsItOpen"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx#pickerClosesWhenTheArticleChanges, pickerClosesWhenTheVoteIsNoLongerDown, focusReturnsToTheNarrowControl, rejectedNarrowingRevertsTheLabel"
        status: pass
    human_judgment: false
  - id: D5
    description: "u and d vote on the loaded by-id article with the toggle rule in every view; Cmd/Ctrl/Alt variants and keys typed in inputs do nothing; votes never mark read or change the selection; Shift+D opens the picker only when narrowable and never votes; the overlay lists both"
    requirement: FDBK-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts#uVotesUpOnTheSelectedArticle, dVotesDownAndDAgainRemoves, modifiedVotingKeysAreIgnored, votingKeysWaitForTheArticle, votingKeysLeaveReadAndSelectionAlone, shiftDOpensThePickerWhenNarrowable, shiftDDoesNothingOtherwise, votingKeysIgnoredInInputs"
        status: pass
      - kind: other
        ref: "grep -c \"Thumbs up / down (press again to remove)\" src/main/frontend/src/components/ShortcutOverlay.tsx = 1"
        status: pass
    human_judgment: false
  - id: D6
    description: "Visual: the picker sits under 👎 Down over the article, long names truncate at 12em in the narrowed label, a 40-character name wraps inside the picker without overlapping the match %, and the toolbar wraps in a narrow pane"
    requirement: FDBK-06
    verification: []
    human_judgment: true
    rationale: "Layout and truncation are visual (Task 2 human-check): with ./gradlew bootTestRun and npm run dev, open a scored article matching 3+ topics, press d then Shift+D, press 2 then Enter, re-open and press Esc (article stays selected), then narrow the reading pane"

duration: 7min
completed: 2026-09-27
status: complete
---

# Phase 6 Plan 4: Narrow a Thumbs-Down and Keyboard Voting Summary

**A "Narrow…" control after 👎 Down opens a keyboard-driven topic picker that re-saves the down-vote for the chosen topics only, the pane then reads "👎 Politics only", and `u` / `d` / Shift+D vote and narrow from the keyboard without ever touching read state or the selection.**

## Performance

- **Duration:** about 7 min
- **Started:** 2026-09-27T03:39:44Z
- **Completed:** 2026-09-27T03:46:51Z
- **Tasks:** 3
- **Files modified:** 10 (3 created, 7 modified)

## Accomplishments

- `NarrowPicker` is a non-modal `role="dialog"` popover inside `.feedback-group`. It lists the matched topics in breakdown order, each with a checkbox, a 1-9 key hint, a sign-colored name (trailing `−` on negatives, `--text-secondary` at weight 0) and `{n}% match`. It opens with the current picks checked, or every matched topic when the vote is not narrowed. Apply sends a strict subset as `topicIds`, un-narrows (null) when all are checked, just closes when the set is unchanged, and is disabled with the hint at zero. With no matched topics left, the empty message replaces the list and Apply is hidden.
- `FeedbackBar` shows the narrow control directly after 👎 Down when `canNarrow` holds. Its label is `Narrow…`, `{name} only`, `{a}, {b} only`, `{k} of {n} topics` or `No topics`. Names truncate at 12em, and the full label goes in `title` and `aria-label`. The picker closes on Apply, Cancel, Esc, outside mousedown, an article change or the vote leaving 👎. Focus then returns to the control, or to `.reading-content` when the control is gone.
- Picker keys `1`-`9`, Enter and Esc stop propagation, so Esc no longer clears the selected article and Enter no longer moves focus to the pane (Pitfall 5).
- `u` / `d` call `press(fetchedArticle, ±1)` once the by-id article has loaded. Cmd/Ctrl/Alt variants are ignored, so Cmd+D still bookmarks (Pitfall 6). Shift+D sets `narrowOpen` only when `canNarrow(fetchedArticle)` and never votes. The overlay lists `u / d` and `Shift+D` right after `s`.
- `useFeedbackStore` is a plain session-only zustand store (no middleware), kept out of the widely mocked `uiStore`.

## Task Commits

1. **Task 1 (tracer): Narrow… opens the picker and Apply re-saves the vote for the chosen topics**: `73c7cb8` (feat). The tracer's automated verify was re-run and passed before expansion (interactive run, end-of-phase mode, automated-only verify).
2. **Task 2 (TDD): the picker is keyboard-driven, never leaks keys, and closes cleanly**: RED `fff5e63` (test), GREEN `428447b` (feat)
3. **Task 3 (TDD): u and d vote, Shift+D opens the picker, and the overlay lists them**: RED `949b1d5` (test), GREEN `3f379dd` (feat)

No refactor commits were needed.

## Files Created/Modified

- `src/main/frontend/src/components/NarrowPicker.tsx` (new): the picker popover, its keys, focus-on-open and outside-mousedown close
- `src/main/frontend/src/components/NarrowPicker.test.tsx` (new): 17 pure-props tests
- `src/main/frontend/src/stores/feedbackStore.ts` (new): `useFeedbackStore { narrowOpen, setNarrowOpen }`
- `src/main/frontend/src/utils/feedback.ts`: `canNarrow`, `narrowedPicks`, `narrowLabel`
- `src/main/frontend/src/components/FeedbackBar.tsx`: the narrow control, the narrowed label, the picker mount, close rules and focus return
- `src/main/frontend/src/components/FeedbackBar.test.tsx`: harness reads the selected id from `uiStore` and can add `.reading-content`; 10 new tests
- `src/main/frontend/src/App.css`: `.narrow-toggle.narrowed`, `.narrow-toggle-name`, `.narrow-picker`, `-title`, `-list`, `.narrow-option` (+ hover), `.narrow-key`, `.narrow-match`, `.narrow-name`, `.narrow-name-zero`, `.narrow-hint`, `.narrow-empty`, `.narrow-picker-footer`. Colors come only from theme variables; the one literal is the UI-SPEC shadow `rgba(0, 0, 0, 0.3)`
- `src/main/frontend/src/hooks/useKeyboardShortcuts.ts`: `case 'u'`, `case 'd'`, `case 'D'`; `fetchedArticle` and `press` added to the `useCallback` deps
- `src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts`: `setFeedback` / `clearFeedback` added to the full-replacement mock, a modifier-aware `press(key, init)`, store resets, and 8 new tests
- `src/main/frontend/src/components/ShortcutOverlay.tsx`: the `u / d` and `Shift+D` rows

## Decisions Made

- Enter on a focused Cancel or Apply button keeps its native click. Only Enter elsewhere (a checkbox or the dialog) applies. Every Enter stops propagation. Without this, Tab to Cancel then Enter would have applied.
- Shift+D ignores Cmd/Ctrl/Alt, like `u` and `d`.
- The outside-mousedown check falls back to the picker root when there is no enclosing `.feedback-group`.

## Deviations from Plan

### Auto-fixed Issues

**1. [TDD] Harness timing in one RED test fixed before the RED commit**
- **Found during:** Task 2 RED
- **Issue:** `rejectedNarrowingRevertsTheLabel` first asserted the intent label synchronously after the Apply click. TanStack notifies observers on a later tick, so it failed on the harness, not the behavior (INVALID_RED for that test).
- **Fix:** The PUT is now held on a deferred promise, the intent label is awaited with `waitFor`, and then the 400 is resolved.
- **Files modified:** `src/main/frontend/src/components/FeedbackBar.test.tsx`
- **Committed in:** `fff5e63` (RED)

---

**Total deviations:** 1 (a TDD harness correction). **Impact on plan:** none on scope or behavior.

## TDD Gate Compliance

- **Task 2:** RED `fff5e63` precedes GREEN `428447b`. Evidence came from vitest `--reporter=tap-flat` with a `# tests 32 / # pass 23 / # fail 9` footer, computed from that run's own `ok`/`not ok` lines (vitest TAP has no node-style summary, as in 06-03). Target `pickerKeysNeverReachTheDocument` failed on `expected [ '1', 'Enter', 'Escape' ] to deeply equal []`. `check tdd-red-evidence` returned RED_EVIDENCE_OK. Seven of the 13 Task 2 behaviors already passed in RED, because the Task 1 tracer or plan 06-03 ships them: Enter with nothing checked, the zero hint, the empty list, blank hints from option 10, inside mousedown, and the rejected-narrowing revert (the hook from 06-03). They stay as regression guards.
- **Task 3:** RED `949b1d5` precedes GREEN `3f379dd`. The TAP footer read `# tests 30 / # pass 26 / # fail 4`. Target `uVotesUpOnTheSelectedArticle` failed on `expected "vi.fn()" to be called with arguments: [ 1, 1, null ]`, and the verdict was RED_EVIDENCE_OK. The four "does nothing" guards (modified keys, loading article, Shift+D otherwise, inputs) passed in RED because they assert absence of behavior.
- Task 1 is a tracer (not `tdd="true"`), so its tests and code were committed together in `73c7cb8`.

## Issues Encountered

None.

## Verification

- `npx tsc -b`: exit 0. `npx eslint` on every changed file: clean.
- `npm test` (full frontend suite): 26 files, 272 tests, all passing.
- `npx vitest run src/components/NarrowPicker.test.tsx src/components/FeedbackBar.test.tsx src/components/ReadingPane.test.tsx`: 45 tests passing.
- Acceptance greps all pass. Task 1: `Penalize which topics?` 1, `role="dialog"` 1, `export function canNarrow` 1, `zustand/middleware` 0, `^\.narrow-picker ` 1. Task 2: `stopPropagation` 2, `addEventListener('mousedown'` 1, `reading-content` 1. Task 3: `case 'u':` 1, `case 'd':` 1, `case 'D':` 1, the modifier check 2, and the overlay row 1.
- Key links: `press(fetchedArticle` and `setNarrowOpen(true)` in `useKeyboardShortcuts.ts`; `narrow(article` in `FeedbackBar.tsx`.
- T-06-16: no `dangerouslySetInnerHTML` in `NarrowPicker.tsx` or `FeedbackBar.tsx`; topic names render as React text.
- `git status` shows `.envrc`, `CLAUDE.md`, `.claude/CLAUDE.md` and `.planning/config.json` still modified and unstaged.
- Executor workflow files read in full before starting: `execute-plan.md`, `templates/summary.md`, `references/checkpoints.md`, `references/tdd.md`, `references/worktree-path-safety.md`, `references/executor-examples.md`.

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness

- Plans 06-05 and 06-07 can build on `useFeedbackStore`, `canNarrow`, `narrowLabel` and the picker.
- The visual check (D6: picker placement, 12em truncation, long-name wrap, toolbar wrap) is left for end-of-phase UAT.
- No blockers.

## Self-Check: PASSED

- All 3 created files are present on disk.
- Commits 73c7cb8, fff5e63, 428447b, 949b1d5 and 3f379dd are present in git log.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*
