---
phase: 03-interest-model-schema-rubric-editor
plan: 06
subsystem: ui
tags: [react, tanstack-query, typescript, vitest, interests, topics]

requires:
  - phase: 03-interest-model-schema-rubric-editor
    provides: "03-01 required topic name; 03-03 topic REST shapes and limits; 03-05 interestApi, useInterest.ts, InterestsDialog shell, describeUnsaved, meta.inlineError, ApiError"
provides:
  - "src/components/TopicRow.tsx: TopicRowState type, WeightControl, TopicRow (per-row create/update/delete mutations)"
  - "src/utils/interest.ts: formatSigned, isNegated, hinge, formatPreviewText, parseWeight, isTopicDirty, WEIGHT_MIN/WEIGHT_MAX"
  - "src/hooks/useInterest.ts: useInterestTopics, useCreateInterestTopic, useUpdateInterestTopic, useDeleteInterestTopic; query key ['interest','topics']"
  - "InterestsDialog Topics section: rows seeded once per open, drafts appended, 25 cap, dirty-topic count fed into the close guard"
affects: [03-07, 03-08, 05-priority-view]

actuals:
  tokens: 13273
  tasks: 3
  commits: 4
plan_head_before: c2a3b286c63f10b6cd275c97f8f189bc527937a9

tech-stack:
  added: []
  patterns:
    - "Controlled rows: the dialog owns TopicRowState[]; each TopicRow reports onChange/onSaved/onDiscard/onDeleted and owns its own mutations, so pending state and errors stay per row"
    - "Rows are seeded once per open and never re-derived from the topics query; saves patch the cache with setQueryData and only create/delete invalidate status (Pitfall 7)"
    - "Debounce tests drive fireEvent.change with vi fake timers; userEvent + fake timers hangs in Testing Library's async wrapper under Vitest"

key-files:
  created:
    - src/main/frontend/src/components/TopicRow.tsx
    - src/main/frontend/src/components/TopicRow.test.tsx
    - src/main/frontend/src/utils/interest.ts
    - src/main/frontend/src/utils/interest.test.ts
  modified:
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/App.css

key-decisions:
  - "03-06: TopicRowState = {key, id, name, description, weightText, weight, saved}; key is t-<id> or d-<n> and never changes when a draft is saved"
  - "03-06: Each TopicRow owns useCreate/useUpdate/useDeleteInterestTopic; 03-07 adds its preview mutation the same way, inside TopicRow"
  - "03-06: Row dirtiness is isTopicDirty (utils/interest.ts): drafts always, saved rows when name/description differ or parseWeight(weightText) differs from the saved weight; the close guard counts drafts only when name or description has text"
  - "03-06: onSaved replaces the row's fields and baseline with the server response (trimmed values), keeping key and position"

patterns-established:
  - "Row button aria-labels name the topic via topicLabel(row): name, else description cut to 40 chars + …, else 'new topic'"
  - "Row-scoped errors render as one .dialog-error per message under line 2; mutations carry meta.inlineError so nothing is toasted"

requirements-completed: [INT-02, INT-03]

coverage:
  - id: D1
    description: "Add a draft (weight +20, name focused), save it via POST so it becomes a saved row in place; saved topics load in id order; slider and number stay synced with the signed, sign-classed label; Discard draft sends nothing"
    requirement: INT-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#addsADraftTopicAndSavesItInPlace"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#listsSavedTopicsInIdOrder"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#sliderAndNumberStaySynced"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#discardDraftSendsNoRequest"
        status: pass
    human_judgment: false
  - id: D2
    description: "Row validation (invalid weights keep the typed text and disable Save; blank name/description disable Save with blur copy), PUT edits, inline two-step delete, row-scoped save/delete failure copy and topic-named aria-labels"
    requirement: INT-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#weightOutOfRangeShowsErrorAndDisablesSave"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#blankFieldsDisableSaveWithBlurMessages"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#savedRowEditSendsPut"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#deleteConfirmsInlineThenDeletes"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#saveAndDeleteFailuresShowInlineCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#ariaLabelsNameTheTopic"
        status: pass
    human_judgment: false
  - id: D3
    description: "Debounced (400 ms) negation warning in role=status that never blocks Save or rewrites text, clearing on the next non-matching check; isNegated word list with smart-quote normalization"
    requirement: INT-03
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#negationWarningAppearsAfterDebounceAndNeverBlocks"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#negationWarningClears"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#isNegatedMatchesTheWordList"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#isNegatedNormalizesSmartQuotes"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#isNegatedIgnoresPositiveText"
        status: pass
    human_judgment: false
  - id: D4
    description: "Pure preview math for 03-07: formatSigned, hinge (R6) and formatPreviewText match the UI-SPEC strings"
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#formatSignedFormatsSignsAndDigits"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#hingeFollowsR6"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/interest.test.ts#formatPreviewTextMatchesTheUiSpec"
        status: pass
    human_judgment: false
  - id: D5
    description: "Mixed per-row saves keep other rows' unsaved edits; 25 cap with the maximum title; close guard counts dirty topics (blank drafts never block); delete removes the row and refreshes status; topics stay editable without a TypeSafe key (D-06)"
    requirement: INT-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#savingOneRowKeepsAnotherRowsUnsavedEdits"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#addTopicDisabledAt25WithTitle"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#closeGuardCountsDirtyTopics"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#blankDraftDoesNotBlockClose"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#deleteRemovesTheRowAndRefreshesStatus"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#topicsStayEditableWithoutAKey"
        status: pass
    human_judgment: false
  - id: D6
    description: "Topic row visuals across themes: bordered rows with the accent dirty rule, sign-colored slider and label, scale hint, the <600px line-2 wrap, 25 rows scrolling inside the 85vh body, and a 500-character description staying on one line inside its input (UI E4 long-text backstop)"
    verification: []
    human_judgment: true
    rationale: "Layout, theme colors, media-query wrapping and input overflow are not asserted by jsdom tests"

duration: 8min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 06: Topic Rubric Editor Summary

**Weighted topic rubric in the Interests dialog: up to 25 per-row-saved topics, each with a name, a description and a synced −50..+50 slider and number weight. Descriptions that look negated get a 400 ms debounced, advice-only warning. Deletes use an inline two-step confirm. The pure preview math that 03-07 renders is also in place.**

## Performance

- **Duration:** about 8 min
- **Started:** 2026-09-23T18:23:28Z
- **Completed:** 2026-09-23T18:31:46Z
- **Tasks:** 3
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments
- The Topics section waits for both the profile and topics queries, then seeds its rows once (`t-<id>`, in id order). "+ Add topic" appends a `d-<n>` draft at +20 and focuses its name input. Save POSTs the draft, and the draft becomes a saved row in place, keeping its key and position.
- `TopicRow` owns its create, update and delete mutations, so "Saving…", "Deleting…" and the save/delete failure copy stay inside that row. Saved rows PUT, and delete goes through the inline confirm strip ("Keep topic" / "Confirm delete topic: {topic}").
- An invalid weight (out of range, not a whole number, or empty) keeps the typed text, shows "Weight must be a whole number from −50 to +50." and disables Save. Blank name and description fields disable Save, and show their message when blurred empty after typing.
- The negation warning (role=status) appears 400 ms after the last change. It never blocks Save and never rewrites the text.
- The dialog counts dirty topics for the close guard ("the profile and 2 topics", "1 topic"). Blank drafts never block closing. "+ Add topic" is disabled at 25 rows with the maximum-topics title. Saving one row leaves another row's unsaved edits untouched.
- `formatSigned`, `hinge` and `formatPreviewText` produce the UI-SPEC preview strings, so 03-07 only has to render them.

## For plan 03-07 (preview)
- `TopicRowState = { key: string; id: number | null; name: string; description: string; weightText: string; weight: number; saved: { name; description; weight } | null }`. Preview should send the row's current `description` and `weight`, plus `topicId = row.id` (null for drafts).
- Add the preview mutation inside `TopicRow` in the same per-row way as `useCreateInterestTopic`/`useUpdateInterestTopic`/`useDeleteInterestTopic` (`meta: { inlineError: true }`). Put the `Preview topic` button first in `.interests-row-actions`, labeled `Preview topic: ${topicLabel(row)}` (the helper is module-private in `TopicRow.tsx`). Render the result slot after the error slot under line 2.
- `formatPreviewText(noul, weight)` and `hinge(noul)` are in `utils/interest.ts`. Recomputing the result on a weight change is just a matter of calling it again with the stored `noul`.

## Task Commits

1. **Task 1 (tracer): topic rows with weight control, add and save in place** - `fa55074` (feat)
2. **Task 2 (TDD): row validation, negation warning, edit and inline delete**
   - RED: `c7a7ed5` (test)
   - GREEN: `5a65477` (feat)
3. **Task 3: 25 cap, dirty-topic close guard, row styles** - `4b90cfa` (feat)

The tracer gate ran in interactive `end-of-phase` mode with an automated-only `<verify>`. The verify commands were re-run, passed (21 dialog tests, `tsc -b` clean), and expansion continued with no checkpoint.

## Files Created/Modified
- `src/main/frontend/src/components/TopicRow.tsx` - `TopicRowState`, `WeightControl`, `TopicRow`
- `src/main/frontend/src/components/TopicRow.test.tsx` - 8 row behavior tests (controlled harness, fetch mocked)
- `src/main/frontend/src/utils/interest.ts` - `formatSigned`, `isNegated`, `hinge`, `formatPreviewText`, `parseWeight`, `isTopicDirty`, `WEIGHT_MIN`/`WEIGHT_MAX`
- `src/main/frontend/src/utils/interest.test.ts` - 6 helper tests
- `src/main/frontend/src/hooks/useInterest.ts` - Topics query and the create/update/delete topic mutations
- `src/main/frontend/src/components/InterestsDialog.tsx` - `TopicsSection` (seed once, drafts, 25 cap, dirty count), both queries gate the editor
- `src/main/frontend/src/components/InterestsDialog.test.tsx` - 10 new tests (27 total)
- `src/main/frontend/src/App.css` - Topic row, weight control, warning and narrow-viewport rules (theme variables only)

## Decisions Made
- `parseWeight` and `isTopicDirty` live in `utils/interest.ts`, because both `TopicRow` and the dialog's close guard need them. The dirty check compares the parsed weight, so typing "020" over a saved 20 isn't counted as a change, and an invalid weight always counts as dirty.
- `onSaved` replaces the row's fields with the server response as well as its baseline. The server trims values, so a trailing space the user typed doesn't leave the row dirty after a successful save.
- The scale hint sits under the slider only, inside `.interests-weight-slider`, so "+50 boost" lines up with the end of the slider and not with the sign label.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Debounce tests use fireEvent with fake timers instead of userEvent**
- **Found during:** Task 2 (RED)
- **Issue:** The plan's suggested `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })` hung both negation tests for 5 s. Testing Library's async wrapper waits on a `setTimeout` that Vitest's fake clock freezes, because it only detects Jest fake timers.
- **Fix:** A `typeSlowly` helper fires `change` one character at a time, 100 ms apart, under `vi.useFakeTimers()`. The tests then assert no warning at 399 ms after the last keystroke and the warning at 400 ms.
- **Files modified:** `src/main/frontend/src/components/TopicRow.test.tsx`
- **Verification:** Both tests failed on the missing warning in RED and pass in GREEN.
- **Committed in:** `c7a7ed5`

### Small additions beyond the plan
- Four helper CSS classes the plan didn't list: `.interests-weight-slider`, `.interests-topic-description` (`flex: 1; min-width: 0`, which keeps a 500-character description from widening the row), `.interests-add-topic` (8px top margin) and `.interests-delete-confirm`. All use theme variables only.
- The RED evidence check (`check tdd-red-evidence`) parses node-test TAP summary lines, which Vitest's `tap-flat` reporter doesn't emit. The `# tests 14 / # pass 2 / # fail 12` counts from the same run were appended before the check. The verdict was `RED_EVIDENCE_OK` for both target tests.

**Total deviations:** 1 auto-fixed (1 blocking, test-only), plus the small additions above.
**Impact on plan:** None. All acceptance criteria pass as written.

## TDD Gate Compliance
- RED `c7a7ed5` (test(03-06)): 12 of 14 tests failed on assertions for the planned behavior. The other 2 (`formatSigned` from Task 1, and `isNegatedIgnoresPositiveText` against the stub) passed as expected.
- GREEN `5a65477` (feat(03-06)): 14 of 14 pass.
- REFACTOR: not needed.

## Issues Encountered
- The pre-commit TruffleHog hook passed on every commit. Each commit stashed and restored the user's unrelated unstaged edits, and those files were never staged.

## Verification
- `(cd src/main/frontend && npx tsc -b)`: exit 0
- `npm --prefix src/main/frontend test`: 17 files, 93 tests passed
- `npx eslint` on every file this plan touched: clean
- The acceptance greps for Tasks 1-3 printed their expected values: no `dangerouslySetInnerHTML` in TopicRow, `Confirm delete topic` ×1, `This looks negated` ×1, `role="status"` ×1, the maximum-topics title ×1, `max-width: 600px` and `.interests-topic-row.dirty` present, and no hex literals in the `interests-*`/`weight-*` CSS.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- 03-07 can add the preview button and result to `TopicRow` (see "For plan 03-07" above).
- A visual/theme check of the topic rows, the narrow-viewport wrap and long-description overflow is queued for end-of-phase UAT (coverage D6).

## Self-Check: PASSED
- All four created files exist, and commits fa55074, c7a7ed5, 5a65477 and 4b90cfa are in history.

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*
