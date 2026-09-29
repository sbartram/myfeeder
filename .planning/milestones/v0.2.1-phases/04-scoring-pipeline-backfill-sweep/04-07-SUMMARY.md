---
phase: 04-scoring-pipeline-backfill-sweep
plan: 07
subsystem: ui
tags: [react, tanstack-query, vitest, interest-ranking, rescore, claude-md]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-06 GET /api/interest/status appends eligibleUnscored and failed"
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-08 GET /api/interest/rescore {count, windowDays} and POST /api/interest/rescore (409 when unconfigured or cold start)"
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-01 blob-from-HEAD CLAUDE.md staging steps"
provides:
  - "InterestStatus.eligibleUnscored/failed, RescoreCount, interestApi.getRescoreCount/rescore"
  - "useRescoreCount (never cached), useRescoreUnread (inlineError, refreshes status), conditional 15s status polling"
  - "RescoreFooter / RescoreConfirm in the Interests dialog footer with disabled reasons and the 'N waiting to be scored' line"
  - "CLAUDE.md Interest Ranking lines for the status counts and GET|POST /rescore"
affects: [05-priority-view]

actuals:
  tokens: 4904
  tasks: 3
  commits: 4
plan_head_before: 1e69db382082901a3817fd008372d4ada6fa307a

tech-stack:
  added: []
  patterns:
    - "A confirmation that must show fresh server data mounts only while open and uses a query with staleTime 0 and gcTime 0; it renders a loading line while isFetching, so a cached number is never confirmed"
    - "A disabled button carries its reason in title, first match wins (the '+ Add topic' pattern)"
    - "Status polling uses refetchInterval as a function of the last data, so it runs only while there is something to wait for and only while an observer (the open dialog) exists"

key-files:
  created: []
  modified:
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/App.css
    - CLAUDE.md

key-decisions:
  - "Disabled-reason order: not configured, then unsaved edits, then cold start. A missing or failed status never disables Re-score; the server's 409 then shows inline"
  - "RescoreConfirm owns both useRescoreCount and useRescoreUnread; while the reset is pending, its Cancel and Re-score buttons are disabled and Re-score reads 'Re-scoring…'. The footer's 'Re-score unread' button is not rendered while the confirmation is open, and that is the only time a reset can be pending"
  - "The status query polls every 15s only while eligibleUnscored > 0. An absent field counts as 0, so the existing 'exactly 2 status calls' test still holds"
  - "The confirmation row reuses 'dialog-actions interests-confirm' (the close guard's classes), so it gets the same flex layout and theme without new CSS"

patterns-established:
  - "Fresh-count confirmation: mount-scoped query (gcTime 0) + 'Counting…' while isFetching"

requirements-completed: [INT-05, JEV-05]

coverage:
  - id: D1
    description: "Re-score unread in the Interests dialog footer: an inline, themed confirmation with the server's fresh count (plural, singular and zero), exactly one POST on confirm, status refetched, Cancel sends nothing, and a 409 shows inline without a toast"
    requirement: INT-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#rescoreConfirmShowsTheServerCountAndPosts"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#cancelSendsNoReset"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#singleArticleCopyIsSingular"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#zeroCountOffersNothingToReset"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#reopeningFetchesAFreshCount"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#resetFailureShowsInlineErrorNotToast"
        status: pass
    human_judgment: false
  - id: D2
    description: "Re-score unread is disabled with a specific title when there are unsaved edits (D-04), when Jev is unconfigured, or in cold start"
    requirement: INT-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#rescoreDisabledWhileEditsAreUnsaved"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#rescoreDisabledWithReasonWhenNotConfiguredOrColdStart"
        status: pass
    human_judgment: false
  - id: D3
    description: "'N articles waiting to be scored' shows while configured, not cold start and eligibleUnscored > 0; status polls every 15s only then"
    requirement: JEV-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#waitingLineShowsEligibleUnscored"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#statusPollsOnlyWhileArticlesAreWaiting"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#coldStartFocusesTheTextareaAndClearsAfterSave"
        status: pass
    human_judgment: false
  - id: D4
    description: "Footer layout and theming: the note and button in one row, the confirmation and waiting line readable in all six themes"
    verification: []
    human_judgment: true
    rationale: "Visual layout and theme contrast are not asserted by any test; CSS uses theme variables only, but appearance needs a human look"
  - id: D5
    description: "CLAUDE.md Interest Ranking section names the appended status fields and GET|POST /rescore, in a CLAUDE.md-only commit that leaves the user's uncommitted hunks unchanged"
    verification:
      - kind: other
        ref: "git show HEAD:CLAUDE.md contains 'GET|POST /rescore' and 'eligibleUnscored, failed'; git show --name-only HEAD == CLAUDE.md (at cfcc1a4)"
        status: pass
      - kind: other
        ref: "diff of claude-md-user-hunks.07.before.diff vs .07.after.diff (index lines ignored) is empty"
        status: pass
    human_judgment: false

duration: 7min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 07: Re-score Unread Footer Summary

**The Interests dialog footer has a "Re-score unread" action. It opens an inline confirmation that always fetches a fresh server count, and it sends one POST. The button's title explains why it is disabled when it is. A "N articles waiting to be scored" line polls /status every 15 seconds while the sweep drains. CLAUDE.md now documents the status counts and the rescore route.**

## Performance

- **Duration:** about 7 min
- **Started:** 2026-09-24T02:05:09Z
- **Completed:** 2026-09-24T02:12Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- INT-05: the "Re-score unread" button sits next to the note "Profile and topic changes apply to newly arriving articles." (D-01). Clicking it opens an inline, themed confirmation (D-02). The confirmation shows "Counting articles…" until a fresh `GET /api/interest/rescore` returns, then offers one `POST`. After the POST, it shows the started line and refetches status.
- D-04 and the D-18 mirror: the button is disabled with a reason when Jev is unconfigured, when the profile or a topic has unsaved edits, or in cold start.
- JEV-05 (UI): the dialog shows "N articles waiting to be scored" and polls status every 15 seconds, but only while articles are waiting and the dialog is open.
- CLAUDE.md's Interest Ranking section documents `{configured, breakerState, coldStart, eligibleUnscored, failed}` and `GET|POST /rescore`.

## Final copy strings

- Button: "Re-score unread". While a reset is pending, the confirm button reads "Re-scoring…"
- Loading: "Counting articles…"
- Confirm: "Re-judge {n} unread articles from the last {d} days? Existing scores are replaced as they're re-scored." ("1 unread article" when n is 1), with the buttons "Cancel" and "Re-score"
- Zero: "Nothing to re-judge: no scored unread articles from the last {d} days." with only "OK"
- Success: "Re-scoring started for {n} articles." ("1 article")
- Reset error: "Couldn't start the re-score: {message}"
- Count error: "Couldn't count the articles to re-judge: {message}." with "Cancel"
- Disabled titles, first match wins: "Scoring isn't set up yet: no TypeSafe API key is configured.", then "Save your changes first", then "Write a profile or add a topic first"
- Waiting line: "{N} articles waiting to be scored" ("1 article waiting to be scored")

## Task Commits

1. **Task 1 (tracer): re-score unread from the dialog footer:** `6616f0e` (feat). The six tests were written first and failed because the button was missing. Tracer gate: an interactive run in `end-of-phase` mode with an automated-only verify, so the verify was re-run (it passed) and execution continued without a checkpoint.
2. **Task 2: disabled reasons and the waiting line:** `6d1c907` (test, RED) and `5a77ead` (feat, GREEN)
3. **Task 3: CLAUDE.md route and status-count lines:** `cfcc1a4` (docs, CLAUDE.md only)

**Plan metadata:** the docs commit that adds this SUMMARY and REQUIREMENTS.md

## TDD Gate Compliance

- RED `6d1c907`: all four target tests failed on assertions for the planned behavior. `rescoreDisabledWhileEditsAreUnsaved` failed with "element is not disabled". `rescoreDisabledWithReasonWhenNotConfiguredOrColdStart` failed because the title attribute was missing. `waitingLineShowsEligibleUnscored` and `statusPollsOnlyWhileArticlesAreWaiting` failed because the "N articles waiting to be scored" line was not found. The other 38 tests passed. `gsd-tools check tdd-red-evidence` returned RED_EVIDENCE_OK for each record (`$HOME/.cache/myfeeder-phase04/red-evidence.04-07.*.json`, vitest verbose output rendered as TAP).
- GREEN `5a77ead`: 42/42 dialog tests pass and the full suite passes (118 tests in 17 files).
- REFACTOR: none needed.

## Files Created/Modified

- `src/main/frontend/src/api/interest.ts`: the `InterestStatus` counts, `RescoreCount`, `getRescoreCount` and `rescore`
- `src/main/frontend/src/hooks/useInterest.ts`: `useRescoreCount` and `useRescoreUnread`, plus the conditional `refetchInterval` on `useInterestStatus`
- `src/main/frontend/src/components/InterestsDialog.tsx`: `rescoreBlockedReason`, `RescoreFooter` and `RescoreConfirm`
- `src/main/frontend/src/components/InterestsDialog.test.tsx`: 10 new tests; the `status()` helper and the default status body gain `eligibleUnscored` and `failed`; new helpers `rescorePosts`, `rescoreGets` and `rescoreCount`
- `src/main/frontend/src/App.css`: `.interests-rescore` (the footer row), `.interests-rescore .interests-note` (removes the note's top margin inside the row) and `.interests-waiting`. They use theme variables only.
- `CLAUDE.md`: the Interest Ranking section's InterestStatusService and Routes bullets

## Decisions Made

- The disabled-reason order and the confirmation's ownership of the mutation are listed in key-decisions above.
- Cancel is also disabled while the reset is pending. Otherwise, unmounting the confirmation mid-request would drop the `mutate` success callback, and the "started" line would never appear.

## CLAUDE.md hunk comparison

The user's uncommitted hunks were at working-tree lines 40, 55-56, 99 and 105. This plan's edits were lines 147 and 149, and both edits kept the line count unchanged. The plan's edits were applied to a copy of `git show HEAD:CLAUDE.md`, and that copy's blob was staged with `git update-index --cacheinfo`. Commit `cfcc1a4` touches only CLAUDE.md, with 2 lines changed. With `index` lines ignored, `claude-md-user-hunks.07.before.diff` and `.07.after.diff` are identical (the diff printed nothing). The files `.envrc`, `.claude/CLAUDE.md` and `.planning/config.json` are byte-identical to their state before the plan (shasum), and all of them are still uncommitted.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Task 1's `RescoreFooter` took no props until Task 2**
- **Found during:** Task 1
- **Issue:** The plan's Task 1 call site passes `status` and `dirty`, but only Task 2 uses them. `tsc -b` rejects unused destructured props (TS6198).
- **Fix:** In commit `6616f0e`, `RescoreFooter()` takes no props and the call site is `<RescoreFooter />`. Task 2 (`5a77ead`) adds `{ status, dirty }` and the planned call site `<RescoreFooter status={status.data} dirty={profileDirty || dirtyTopics > 0} />`.
- **Verification:** `npx tsc -b` passes after each task.

**2. [Rule 3 - Blocking] The full `npm run lint` exits non-zero because of pre-existing errors**
- **Found during:** Task 2 verify
- **Issue:** `eslint .` reports the same 6 errors that phase 03's `deferred-items.md` already records. They are in `ArticleList.tsx`, `BoardArticleList.tsx`, `ReadingPane.tsx`, `SettingsDialog.tsx` and `Toast.tsx`, and this plan touches none of those files.
- **Fix:** None; this is out of scope. Every file this plan touched passes eslint with no errors.
- **Verification:** `npx eslint src/components/InterestsDialog.tsx src/hooks/useInterest.ts src/api/interest.ts src/components/InterestsDialog.test.tsx` exits 0.

---

**Total deviations:** 2 (1 intermediate-state adjustment, 1 pre-existing lint failure left in scope of phase 03's deferred items)
**Impact on plan:** None on the delivered behavior. The final code matches the plan's interfaces.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- Phase 5's Priority view can reuse `useRescoreCount` / `useRescoreUnread`, or add a second entry point (D-01).
- The status poll depends on the open dialog. If Phase 5 adds another observer of `['interest', 'status']`, that observer will poll too while `eligibleUnscored > 0`.

## Self-Check: PASSED

- All six modified files are present on disk.
- Commits `6616f0e`, `6d1c907`, `5a77ead` and `cfcc1a4` exist.
- Every acceptance criterion from Tasks 1-3 was re-run and passes. The one exception is the full-repo eslint, which fails on the pre-existing errors described in the deviations.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*
