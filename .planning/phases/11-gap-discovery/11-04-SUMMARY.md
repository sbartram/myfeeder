---
phase: 11-gap-discovery
plan: 04
subsystem: ui
tags: [react, tanstack-query, typescript, vitest, interests, gap-discovery]

requires:
  - phase: 11-gap-discovery
    provides: "11-02: POST /api/interest/topics accepts sourceArticleId and writes TOPIC_CREATED in the createTopic transaction"
  - phase: 11-gap-discovery
    provides: "11-03: SuggestedTopics section, useTopicSuggestions, useDismissSuggestion, suggestion test helpers"
provides:
  - "Create topic on a suggestion: a prefilled +20 draft (title cut to 500) carrying sourceArticleId, appended to the open dialog"
  - "Draft added state (no actions) while the draft is unsaved; hidden once saved, active again once discarded"
  - "At 25 topic rows Create topic is disabled with 'You have 25 topics, the maximum.'; Dismiss still works"
  - "TopicInput / TopicRowState / TopicDraft gain sourceArticleId; only TopicRow's create path sends it"
  - "The reading-pane Create topic from article draft carries sourceArticleId (one draft path)"
  - "CLAUDE.md: useInterest suggestion hooks and the Gap discovery UI bullets"
affects: [11-verification, 12-calibration, interests-dialog]

actuals:
  tokens: 9352
  tasks: 3
  commits: 4
plan_head_before: 52f0f94572aaee94a52cf5691825bb697f13a88d
plan_head_after: b88a8cb63c365c440b03d8c15cb90f1767b7ac42

tech-stack:
  added: []
  patterns:
    - "A row's sourceArticleId drives the suggestion's state: on an unsaved row it reads Draft added, on a saved row it hides the suggestion before the refetch lands"
    - "Optional request fields are added only when defined, so existing exact-body assertions stay byte-identical"

key-files:
  created: []
  modified:
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/components/TopicRow.tsx
    - src/main/frontend/src/components/TopicRow.test.tsx
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/App.css
    - src/main/frontend/src/components/FeedbackNotice.tsx
    - src/main/frontend/src/components/FeedbackNotice.test.tsx
    - src/main/frontend/src/components/ReadingPane.test.tsx
    - CLAUDE.md

key-decisions:
  - "A5 kept as planned: a drafted suggestion shows only 'Draft added', with neither Create topic nor Dismiss; the user discards the draft to dismiss instead"
  - "The drafted and active suggestion rows are two <li> branches sharing one badge-and-text fragment, so the drafted branch carries a literal aria-disabled=\"true\""
  - "atMax stays rows.length >= 25, so unsaved drafts count, matching + Add topic"

patterns-established:
  - "Draft sources: a TopicDraft/TopicRowState sourceArticleId survives edits (spread) and markSaved, and is sent once, on create"

requirements-completed: [GAP-02, GAP-05]

coverage:
  - id: D1
    description: "Create topic adds a prefilled +20 draft (trimmed title, empty focused name, Unsaved) to the open dialog and sends nothing"
    requirement: GAP-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#createTopicAddsAPlus20DraftToTheOpenDialog"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#aLongTitleIsCutTo500"
        status: pass
    human_judgment: false
  - id: D2
    description: "Saving the draft POSTs {name, description, weight, sourceArticleId}; the suggestion disappears at once and stays gone after the refetch"
    requirement: GAP-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#savingTheDraftSendsTheSourceAndRemovesTheSuggestion"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#aSavedDraftHidesTheSuggestionBeforeTheRefetch"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#aDraftWithASourceSendsItOnCreate"
        status: pass
    human_judgment: false
  - id: D3
    description: "Draft added state with no actions while unsaved; discarding reactivates; one click adds one row; drafts append in click order and keep their place"
    requirement: GAP-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#aDraftedSuggestionReadsDraftAddedAndCannotBeAddedTwice, discardingTheDraftReactivatesTheSuggestion, draftsAppendInClickOrderAndKeepTheirPlace"
        status: pass
    human_judgment: false
  - id: D4
    description: "At 25 topics Create topic is disabled with 'You have 25 topics, the maximum.' and Dismiss still works; + Add topic unchanged"
    requirement: GAP-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#at25CreateTopicIsDisabledWithTheTooltipAndDismissStillWorks, addTopicDisabledAt25WithTitle"
        status: pass
    human_judgment: false
  - id: D5
    description: "A PUT update never sends sourceArticleId; + Add topic POST body has no sourceArticleId key"
    requirement: GAP-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#aSavedRowEditNeverSendsTheSource, savedRowEditSendsPut"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#addsADraftTopicAndSavesItInPlace"
        status: pass
    human_judgment: false
  - id: D6
    description: "Creating and saving a topic from a suggestion never posts Preview"
    requirement: GAP-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#creatingNeverPostsPreview"
        status: pass
    human_judgment: false
  - id: D7
    description: "The reading-pane Create topic from article draft carries sourceArticleId through the seeded row into its POST"
    requirement: GAP-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackNotice.test.tsx#createTopicPassesTheDraft"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#noticeSitsBetweenToolbarAndContent"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#seededDraftSendsItsSourceOnSave"
        status: pass
    human_judgment: false
  - id: D8
    description: "End-to-end in the running app (engage, suggestion listed, Create topic, save, gone after reload) and the drafted row's look in all 6 themes"
    verification: []
    human_judgment: true
    rationale: "VALIDATION manual-only rows: the real backend round trip after a page reload and the visual fit of the drafted state per theme are not asserted by any automated test"

duration: 6min
completed: 2026-10-01
status: complete
---

# Phase 11 Plan 04: Create Topic From Suggestion Summary

**Create topic on a suggested topic now appends a +20 draft to the open Interests dialog. The draft uses the article title (cut to 500 characters) and carries `sourceArticleId`. While the draft is unsaved the suggestion reads "Draft added". Saving POSTs the source, and the suggestion disappears at once and stays gone after the refetch. At 25 topics only Dismiss stays usable, and the reading-pane draft sends its source too.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-10-01T22:55:27Z
- **Completed:** 2026-10-01T23:00:59Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments
- `addDraft(draft?)` appends a blank or prefilled draft to the open dialog (D-15). "+ Add topic" calls `() => addDraft()`, so React's click event never becomes the draft (Pitfall 5).
- `TopicInput`, `TopicRowState` and `TopicDraft` gain an optional `sourceArticleId`. Only `TopicRow`'s create path adds it, and only when it is defined. Existing POST and PUT body assertions pass unchanged (Pitfall 6).
- `markSaved` keeps the row's `sourceArticleId`. A saved draft's suggestion is therefore hidden before the suggestions refetch lands, even against a stale server list (Pitfall 4).
- A suggestion whose draft is unsaved renders `li.interests-suggestion.drafted[aria-disabled=true]` with "Draft added" and no buttons. Discarding the draft brings both actions back (D-16, A5).
- At 25 topic rows (drafts count), Create topic is disabled with the title "You have 25 topics, the maximum.", and Dismiss still works (D-18).
- The reading-pane `FeedbackNotice` draft and the seeded draft row carry `sourceArticleId`, so both Create topic paths mark the article handled (D-14).
- CLAUDE.md documents the hooks, the section, Create topic and "Draft added", the at-max rule, the reading-pane source, freshness and the proofs.
- 12 new tests. Frontend suite: 413/413, `npx tsc -b` clean. `./gradlew build` passes: 707 backend tests, 0 failures, 2 pre-existing skips.

## Task Commits

1. **Task 1 (tracer): Create topic → +20 draft → save with source → suggestion gone:** `4591381` (feat). After the commit the tracer gate re-ran its `<verify>` (tsc, 97 tests, grep criteria) before any expansion.
2. **Task 2: Draft added and the at-max state:** RED `66d004e` (test), then GREEN `c0247fa` (feat)
3. **Task 3: Reading-pane source, CLAUDE.md, phase gate:** `b88a8cb` (feat)

## Files Created/Modified
- `src/main/frontend/src/api/interest.ts`: `TopicInput.sourceArticleId?`
- `src/main/frontend/src/components/TopicRow.tsx`: `TopicRowState.sourceArticleId?`; the create branch sends it when defined
- `src/main/frontend/src/components/InterestsDialog.tsx`: `TopicDraft.sourceArticleId?`, `addDraft(draft?)`, `markSaved` keeping the source, the seeded row copying it, and `SuggestedTopics({ rows, atMax, onCreate })` with Create topic, Draft added and the D-18 state
- `src/main/frontend/src/App.css`: `.interests-suggestion.drafted`, `.interests-suggestion-state` (theme variables only)
- `src/main/frontend/src/components/FeedbackNotice.tsx`: the draft adds `sourceArticleId: article.id`
- Tests: `InterestsDialog.test.tsx` (10 new), `TopicRow.test.tsx` (2 new), `FeedbackNotice.test.tsx` and `ReadingPane.test.tsx` (expectations gain `sourceArticleId: 1`)
- `CLAUDE.md`: the useInterest hooks entry and the UI bullets under "Gap discovery (v0.3.0, Phase 11)"

## Decisions Made
- A5 is kept as the plan flagged it: a drafted suggestion offers no Dismiss. To dismiss it instead, the user discards the draft first.
- The drafted and active rows are two `<li>` branches that share one badge-and-text fragment. The drafted branch therefore carries a literal `aria-disabled="true"`, as the acceptance grep requires, without repeating the markup.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong Discard label in the plan's test spec**
- **Found during:** Task 2 (GREEN run)
- **Issue:** The plan said to click `Discard draft: new topic` in `discardingTheDraftReactivatesTheSuggestion`. `TopicRow.topicLabel` falls back to the description when the name is blank, so a suggestion draft's button is `Discard draft: A history of the B-tree`. "new topic" only labels a fully blank draft.
- **Fix:** The test clicks the description-based label, with a comment. The implementation is unchanged.
- **Files modified:** src/main/frontend/src/components/InterestsDialog.test.tsx
- **Verification:** The test passes and asserts no request is sent.
- **Committed in:** `c0247fa` (the Task 2 GREEN commit)

---

**Total deviations:** 1 auto-fixed (1 test-spec bug)
**Impact on plan:** None on behavior. The test now uses the label the UI actually renders.

## TDD Gate Compliance

- Task 2: RED `66d004e`. Three target tests failed on behavior assertions: `aDraftedSuggestionReadsDraftAddedAndCannotBeAddedTwice` (`toHaveClass("drafted")`), `discardingTheDraftReactivatesTheSuggestion` (no "Draft added") and `at25CreateTopicIsDisabledWithTheTooltipAndDismissStillWorks` (`toBeDisabled`). The other three already passed when written: `aSavedDraftHidesTheSuggestionBeforeTheRefetch`, `aLongTitleIsCutTo500` and `draftsAppendInClickOrderAndKeepTheirPlace`. The behavior they pin was delivered by Task 1's `markSaved` and `addDraft` changes, as the plan's structure implies. GREEN `c0247fa` made all 74 InterestsDialog tests pass.
- `workflow.tdd_mode` is not enabled, so no formal `check tdd-red-evidence` record was produced.
- No REFACTOR commit was needed.

## Issues Encountered
- The sandbox refused git commands that write under `.git/worktrees/...` (the plan ledger and spawn sentinel). The plan base `52f0f94` and the measured commit count (`git rev-list --count 52f0f94..HEAD` = 4) are recorded in the frontmatter instead.

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- Phase 11's four plans are complete. GAP-01..GAP-05 are covered by the backend (11-01, 11-02) and the UI (11-03, 11-04).
- Optional manual check from VALIDATION:
  1. Run `./gradlew bootTestRun` and `npm run dev`.
  2. Engage an article that no topic matches, then confirm it is listed under Suggested topics.
  3. Click Create topic, name the topic and Save. The suggestion should disappear and stay gone after a reload.
  4. Look at the drafted row in all 6 themes.

---
*Phase: 11-gap-discovery*
*Completed: 2026-10-01*
