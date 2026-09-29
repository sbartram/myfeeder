---
phase: 06-thumbs-feedback
plan: 07
subsystem: ui
tags: [react, typescript, vitest, zustand, interest-ranking, thumbs-feedback, accessibility]

requires:
  - phase: 06-thumbs-feedback
    provides: "06-03 formatVoteToast leads, formatDelta, useVoteFeedback onSuccess toast, FeedbackBar fetch-stub harness, apiDeleteJson"
  - phase: 06-thumbs-feedback
    provides: "06-04 canNarrow / narrowedPicks / narrowLabel in utils/feedback.ts and the narrow-control FeedbackBar tests"
provides:
  - "formatVoteToast: unscored copy (D-03), no-topics-matched copy (D-18, D-19), bare 'Vote removed', 'Effect under 0.1 points', three-entry cap with ' · +N more' (D-09), LEARNED_CAP / SIGN_CLAMP / WEIGHT_RANGE notes and '(now …)' for negative-base topics (D-07)"
  - "Toast container role=\"status\" (polite announcement)"
  - "utils/feedback.test.ts: effect-toast rule table plus nextVote / formatDelta / matchedTopics units"
  - "apiDeleteJson unit tests (parsed 200 body, ApiError with status on 404)"
affects: [06-verification, 06-uat]

actuals:
  tokens: 3800
  tasks: 2
  commits: 3
plan_head_before: 21814dc9175dbf0411f65c371af7e04e0d98443e

tech-stack:
  added: []
  patterns:
    - "Toast copy is a pure formatter over the server's FeedbackResult: only after - before, learned and after are printed, rounded; no weight, cap or clamp is recomputed client-side"
    - "Effect-list rule: keep rounded-non-zero or limited topics, stable sort by |change| DESC, show 3 then '+N more'"

key-files:
  created:
    - src/main/frontend/src/utils/feedback.test.ts
  modified:
    - src/main/frontend/src/utils/feedback.ts
    - src/main/frontend/src/components/Toast.tsx
    - src/main/frontend/src/api/client.test.ts
    - src/main/frontend/src/components/FeedbackBar.test.tsx

key-decisions:
  - "The capped example follows the UI-SPEC list rule (sort by absolute change), so it prints '👍 Go +0.2 · Rust +0.0 (learned at max +20)', not the spec's example order"
  - "Limit notes take precedence over '(now …)': a negative-base topic that hit SIGN_CLAMP or WEIGHT_RANGE shows the limit note only"
  - "Sorting uses the raw server change; the keep/omit test uses the one-decimal rounding that formatDelta prints, so a kept topic never prints +0.0 without a limit note"

patterns-established:
  - "utils/feedback.test.ts builds TopicEffect rows with an effect(name, before, after, {baseWeight, learned, limit}) helper whose topicIds follow array order, so server order is the input order"

requirements-completed: [FDBK-04, FDBK-05]

coverage:
  - id: D1
    description: "A vote on an unscored article toasts '{👍|👎} Saved — counts once this article is scored'; a scored article with no matched topic toasts '{👍|👎} Saved · No topics matched'; removing either toasts 'Vote removed'"
    requirement: FDBK-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#unscoredCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#noMatchCopy"
        status: pass
      - kind: integration
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx#unscoredVoteToastSaysItCountsLater"
        status: pass
      - kind: integration
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx#noMatchVoteToastSaysNoTopicsMatched"
        status: pass
    human_judgment: false
  - id: D2
    description: "The effect toast lists at most three topics by absolute change (ties in server order) then ' · +N more', with the right lead for up / down / removed / narrowed / all-matched, and a flip shows the full swing"
    requirement: FDBK-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#upVoteListsTopThreeThenMore"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#downFlipShowsTheFullSwing"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#removedShowsTheNetChange"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#narrowingLeads"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#tiesKeepServerOrder"
        status: pass
    human_judgment: false
  - id: D3
    description: "Zero changes are explained by limit notes (learned at max/min, can't cross 0, weight at max/min), negative-base topics show '(now …)', unlimited zero changes are omitted, and all-rounded-away votes say 'Effect under 0.1 points'"
    requirement: FDBK-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#cappedTopicShowsZeroWithItsNote"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#signClampAndRangeNotes"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#zeroChangeWithoutLimitIsOmitted"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#underTenthCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#formatDeltaHasOneDecimalAndNeverMinusZero"
        status: pass
    human_judgment: false
  - id: D4
    description: "apiDeleteJson returns the parsed JSON body of a 200 and raises ApiError with the status on a 404"
    verification:
      - kind: unit
        ref: "src/main/frontend/src/api/client.test.ts#apiDeleteJsonParsesTheBody"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/api/client.test.ts#apiDeleteJsonRaisesApiError"
        status: pass
    human_judgment: false
  - id: D5
    description: "The toast container has role=\"status\" so the effect text is announced politely, and long toasts (three 40-character names with limit notes) wrap inside the 360px toast without clipping"
    verification:
      - kind: other
        ref: "grep -c 'role=\"status\"' src/main/frontend/src/components/Toast.tsx -> 1"
        status: pass
    human_judgment: true
    rationale: "Screen-reader announcement and visual wrapping of long toasts inside the 360px max-width are not asserted by any test (UI E5/long-text is a declared backstop); a human should see a long toast in the running app"

duration: 4min
completed: 2026-09-27
status: complete
---

# Phase 06 Plan 07: Vote Effect Toast Rules Summary

**The vote toast now covers every UI-SPEC case from the server's numbers: votes on unscored articles, votes with no matched topic, changes that round away, a three-topic cap with "+N more", and limit or "(now …)" notes. The toast container is also announced politely.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-09-27T04:04:41Z
- **Completed:** 2026-09-27T04:08:14Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- An unscored article's vote toasts "👍 Saved — counts once this article is scored" (or 👎). A scored article with no matched topic toasts "👍 Saved · No topics matched". Removing either toasts "Vote removed" (D-03, D-18, D-19, FDBK-05).
- The effect toast keeps topics with a non-zero rounded change or a limit. It sorts them by absolute change (ties keep server order) and shows three, then " · +N more" (D-09).
- Zero changes carry their reason: "(learned at max +20)", "(learned at min −20)", "(can't cross 0)", "(weight at max +50)" or "(weight at min −50)". Disliked topics show "(now −28.8)" (D-07, research OQ2).
- When every change rounds away, the toast reads "{lead} · Effect under 0.1 points".
- `.toast-container` has `role="status"`. `apiDeleteJson` now has unit tests.

## Task Commits

1. **Task 1 (tracer): unscored and no-match toast copy**: `2fbc705` (feat; tests and implementation together, since the task is a tracer and not a TDD task). The tracer gate re-ran `<verify>` and it passed, so Task 2 went ahead.
2. **Task 2 RED: failing tests for the list, notes and under-0.1 copy**: `85a861f` (test)
3. **Task 2 GREEN: list cap, notes, under-0.1 copy, role="status"**: `03d47be` (feat)

No REFACTOR commit; the GREEN code needed no cleanup.

## Files Created/Modified
- `src/main/frontend/src/utils/feedback.ts`: `SAVED_LEADS`, `MAX_LISTED`, `effectNote`, and the completed `formatVoteToast`. It now imports `formatSigned` from `./interest`.
- `src/main/frontend/src/utils/feedback.test.ts` (new): the effect-toast rule table plus the `formatDelta`, `nextVote` and `matchedTopics` units.
- `src/main/frontend/src/components/FeedbackBar.test.tsx`: two new cases, `unscoredVoteToastSaysItCountsLater` and `noMatchVoteToastSaysNoTopicsMatched`.
- `src/main/frontend/src/components/Toast.tsx`: `role="status"` on `.toast-container`.
- `src/main/frontend/src/api/client.test.ts`: two new cases, `apiDeleteJsonParsesTheBody` and `apiDeleteJsonRaisesApiError`.

## Decisions Made
- The capped case follows the list rule (sort by absolute change), so it prints "👍 Go +0.2 · Rust +0.0 (learned at max +20)". The planner resolved it this way.
- Limit notes take precedence over "(now …)". The tests pin this with a negative-base topic at SIGN_CLAMP and one at WEIGHT_RANGE.
- The keep test uses the same one-decimal rounding that `formatDelta` prints, so a topic with no limit never prints "+0.0".

## Deviations from Plan

None. The plan ran as written.

## TDD Gate Compliance

- The RED commit (`85a861f`) comes before the GREEN commit (`03d47be`) for Task 2.
- RED failed as intended. The four target tests (`upVoteListsTopThreeThenMore`, `cappedTopicShowsZeroWithItsNote`, `signClampAndRangeNotes`, `underTenthCopy`) failed on their string assertions. For example, the first expected "… Politics +1.2 (now −28.8) · +2 more" and got "… Politics +1.2 · Go +0.2 · Zig +0.1".
- `gsd_run check tdd-red-evidence` returned `INVALID_RED (zero_tests_discovered)` for the Vitest TAP run. That checker reads node:test summary lines (`# tests N`, `# fail N`), which Vitest's `tap-flat` reporter does not print. The TAP output did list the target test under `not ok 6 - src/utils/feedback.test.ts > formatVoteToast > upVoteListsTopThreeThenMore`. The evidence was checked by hand from that output, and no summary lines were made up. This plan is `type: execute`, so the plan-level TDD gate does not strictly apply.
- Some Task 2 tests passed during RED, which was expected because they cover behavior that plans 06-03 and 06-04 already shipped: `formatDelta`, `nextVote`, `matchedTopics`, `downFlipShowsTheFullSwing`, `removedShowsTheNetChange`, `narrowingLeads`, `zeroChangeWithoutLimitIsOmitted`, `tiesKeepServerOrder`, and both `apiDeleteJson` cases.

## Issues Encountered
None.

## User Setup Required

None. No external service needs configuring.

## Next Phase Readiness
- This is the last plan of phase 06. FDBK-04 and FDBK-05 can be marked complete.
- Backstop for the verifier/UAT: open a long toast in the running app (three 40-character names with limit notes) and check that it wraps inside the 360px toast. `.toast` has `max-width: 360px` and no `white-space` override, so the text should wrap normally.
- Note: `ToastContainer` returns `null` when there are no toasts, so the `role="status"` region mounts together with its first message. Some screen readers announce a live region reliably only if it already existed before the content changed. The plan limited this change to the one attribute, so the container was not made always-mounted.

## Execution Context
The executor read all six workflow files: execute-plan.md, templates/summary.md, references/checkpoints.md, references/tdd.md, references/worktree-path-safety.md and references/executor-examples.md.

## Self-Check: PASSED
- All five key files are on disk.
- Commits `2fbc705`, `85a861f` and `03d47be` are in `git log`.
- `npx tsc -b` exits 0, and `npm test` passes 28 files / 314 tests.
- The acceptance greps give: "counts once this article is scored" 1, "No topics matched" 1, "Effect under 0.1 points" 2, "learned at max" 1, `role="status"` 1.
- `git status` shows CLAUDE.md, .claude/CLAUDE.md, .envrc and .planning/config.json still modified and unstaged.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*
