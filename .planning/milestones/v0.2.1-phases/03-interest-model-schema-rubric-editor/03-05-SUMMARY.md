---
phase: 03-interest-model-schema-rubric-editor
plan: 05
subsystem: ui
tags: [react, tanstack-query, typescript, vitest, interests]

requires:
  - phase: 03-interest-model-schema-rubric-editor
    provides: "03-01 V6 schema with a required topic name; 03-03 profile/topic REST shapes; 03-04 /status and /preview contract (coded against, fetch mocked)"
provides:
  - "src/api/interest.ts: interest types and interestApi for all eight /api/interest endpoints"
  - "src/hooks/useInterest.ts: useInterestStatus (staleTime 0), useInterestProfile, useSaveInterestProfile (meta.inlineError)"
  - "InterestsDialog with profile editor, D-11 guidance, counter, status notices, cold-start focus and unsaved-changes guard"
  - "Settings 'Edit interests…' entry; MainLayout interestsOpen state"
  - "createQueryClient() with the meta.inlineError toast opt-out"
  - "ApiError (status + ProblemDetail title) thrown by api/client.ts"
affects: [03-06, 03-07, 05-priority-view]

actuals:
  tokens: 9566
  tasks: 3
  commits: 3
plan_head_before: e7adeba8710e9c44a6893a19798881c55d81f7c3

tech-stack:
  added: []
  patterns:
    - "Dialog body mounts queries only while open; the editor child seeds local state once from loaded data (refetches never clobber typed text)"
    - "Mutations that report errors inline set meta: { inlineError: true } and the global MutationCache skips them"
    - "Component tests exercise the real api/hook layers by routing a mocked globalThis.fetch by method and URL"

key-files:
  created:
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/queryClient.ts
    - src/main/frontend/src/queryClient.test.ts
  modified:
    - src/main/frontend/src/components/SettingsDialog.tsx
    - src/main/frontend/src/components/SettingsDialog.test.tsx
    - src/main/frontend/src/App.tsx
    - src/main/frontend/src/App.css
    - src/main/frontend/src/api/client.ts
    - src/main/frontend/src/api/client.test.ts

key-decisions:
  - "03-05: Interest types live in src/api/interest.ts (following api/integrations.ts), not types/index.ts"
  - "03-05: Phase 5 reuses MainLayout's interestsOpen state for the Priority cold-start call to action"
  - "03-05: The profile editor reports dirtiness to the dialog body via onDirtyChange; 03-06 feeds the dirty topic count into describeUnsaved(profileDirty, dirtyTopics)"
  - "03-05: useSaveInterestProfile invalidates ['interest','status'] without awaiting it, so 'Saving…' ends as soon as the PUT returns"

patterns-established:
  - "Inline-error mutation: useMutation({ ..., meta: { inlineError: true } }) plus an inline .dialog-error with the UI-SPEC copy"
  - "ApiError: branch on error.status / error.title (ProblemDetail) instead of parsing messages"

requirements-completed: [INT-01, INT-06]

coverage:
  - id: D1
    description: "Profile editor loads the saved profile, shows the four D-11 tips, placeholder, 2,000 maxLength and live counter, and saves via PUT /api/interest/profile with Saved / Unsaved changes states"
    requirement: INT-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#loadsProfileAndSavesItThroughTheApi"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#showsTipsPlaceholderAndCounter"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#counterAtLimitShowsLimitReached"
        status: pass
    human_judgment: false
  - id: D2
    description: "Load and save failures show the UI-SPEC inline copy, keep the typed text and are never toasted"
    requirement: INT-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#saveFailureKeepsTextAndShowsInlineCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#loadFailureShowsRetryCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#saveErrorIsNotToasted"
        status: pass
    human_judgment: false
  - id: D3
    description: "Not configured, paused and cold-start notices follow /api/interest/status in fixed order; editing and saving work without a key or status; cold start focuses the textarea and clears after save via status refetch"
    requirement: INT-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#notConfiguredNoticeShownAndProfileStillSaves"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#noticesStackInFixedOrder"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#coldStartFocusesTheTextareaAndClearsAfterSave"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#statusFailureShowsNoNoticeAndEditingWorks"
        status: pass
    human_judgment: false
  - id: D4
    description: "Close and overlay clicks with an unsaved profile show the Discard unsaved changes confirm bar; a clean dialog closes at once"
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#closeWhileDirtyAsksFirst"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#overlayClickUsesTheSameGuard"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#closeWhenCleanClosesImmediately"
        status: pass
    human_judgment: false
  - id: D5
    description: "Settings 'Edit interests…' closes Settings and opens the Interests dialog; inline-error mutations skip the global toast while others still toast; ApiError carries status and title"
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/SettingsDialog.test.tsx#editInterestsButtonCallsOnOpenInterests"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/queryClient.test.ts#inlineErrorMutationsAreNotToasted"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/queryClient.test.ts#mutationErrorsAreToastedByDefault"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/api/client.test.ts#errorsCarryStatusAndProblemTitle"
        status: pass
    human_judgment: false
  - id: D6
    description: "Dialog visual layout across themes: 720px shell with fixed header/footer and scrolling body, notice styling, 0-3 notices wrapping without horizontal scroll at a 360px viewport (UI E6 backstop), and the Settings → Interests hand-off in the running app"
    verification: []
    human_judgment: true
    rationale: "Visual layout, theme rendering and the 360px wrap backstop are not asserted by jsdom tests; App.tsx wiring is type-checked but not rendered in a test"

duration: 6min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 05: Interests Dialog Profile Editor Summary

**Interests dialog opened from Settings, with a D-11 guided profile editor (2,000-char counter, per-item Save), server-driven not-configured/paused/cold-start notices, an unsaved-changes close guard, and the shared interestApi, query hooks, ApiError and inline-error toast opt-out for plans 03-06 and 03-07**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-23T18:04:48Z
- **Completed:** 2026-09-23T18:10:33Z
- **Tasks:** 3
- **Files modified:** 12 (6 created, 6 modified)

## Accomplishments
- `interestApi` covers all eight `/api/interest` endpoints with their types. Plans 03-06 and 03-07 only need to add hooks.
- The Interests dialog loads the profile once per open, seeds the editor a single time (refetches never overwrite typed text), and saves through `PUT /api/interest/profile`. It shows "Saving…", "Saved" and "Unsaved changes", and the counter reads "2,000 / 2,000 · limit reached" at the limit.
- Notices come only from `/api/interest/status`, in the order not configured → paused → cold start. The status query has `staleTime: 0`, the textarea gets focus once on cold start, and a profile save invalidates the status so the cold-start notice clears from the server's answer (D-05).
- Closing the dialog (Close button or overlay click) with an unsaved profile shows the inline "Discard unsaved changes?" bar. Esc is not bound.
- Settings → "Edit interests…" closes Settings and opens the Interests dialog. `createQueryClient()` skips the global toast for mutations marked `meta.inlineError`, and `ApiError` carries the HTTP status and ProblemDetail title.

## Task Commits

1. **Task 1 (tracer): Interests dialog profile slice through interestApi** - `71a2207` (feat)
2. **Task 2: Settings entry point and inline-error toast opt-out** - `dd75f9e` (feat)
3. **Task 3: Notices, cold-start focus, unsaved guard, ApiError, styles** - `d902baa` (feat)

The tracer gate ran in interactive `end-of-phase` mode with an automated-only `<verify>`: the verify commands were re-run, passed, and expansion continued with no checkpoint.

## Files Created/Modified
- `src/main/frontend/src/api/interest.ts` - Interest types and `interestApi` (8 endpoints)
- `src/main/frontend/src/hooks/useInterest.ts` - Status, profile and save-profile hooks
- `src/main/frontend/src/components/InterestsDialog.tsx` - Dialog shell, `InterestNotices`, `ProfileEditor`, `describeUnsaved`, close guard
- `src/main/frontend/src/components/InterestsDialog.test.tsx` - 17 tests through the real api/hook layers with fetch mocked
- `src/main/frontend/src/queryClient.ts` / `queryClient.test.ts` - `createQueryClient()` and the toast opt-out tests
- `src/main/frontend/src/components/SettingsDialog.tsx` / `.test.tsx` - Interests section and optional `onOpenInterests` prop
- `src/main/frontend/src/App.tsx` - Uses `createQueryClient()`, owns `interestsOpen`, renders `InterestsDialog`
- `src/main/frontend/src/App.css` - `interests-*` rules (theme variables only)
- `src/main/frontend/src/api/client.ts` / `client.test.ts` - `ApiError` plus two tests; the five existing tests are unchanged

## Decisions Made
- The interest types live in `src/api/interest.ts` (following `api/integrations.ts`), not in `types/index.ts`.
- Phase 5 reuses `MainLayout`'s `interestsOpen` for the Priority cold-start call to action.
- `ProfileEditor` reports dirtiness up to the dialog body through `onDirtyChange`. `describeUnsaved(profileDirty, dirtyTopics)` is module-private in `InterestsDialog.tsx`, and 03-06 passes the dirty topic count (0 for now).
- The save hook does not await the status invalidation, so "Saving…" ends when the PUT returns and the notice updates once the refetch lands.

## Deviations from Plan

### Additions beyond the plan's test list
- Added `showsPausedNoticeForForcedOpenBreaker` and `doesNotAutofocusWithoutColdStart` to `InterestsDialog.test.tsx`, which covers the FORCED_OPEN branch and the "no autofocus otherwise" rule. The file has 17 tests rather than 15.
- Added small helper classes the plan didn't list (`.interests-loading`, `.interests-save-state`, `.interests-actions`, `.interests-confirm-actions`) and `overflow-wrap: anywhere` on notices, all using theme variables, so the UI-SPEC typography, the 8px actions margin and the notice wrapping are in CSS rather than inline styles.

**Total deviations:** 0 auto-fixed (Rules 1-3). The two additions above are small and in scope.
**Impact on plan:** None. All acceptance criteria pass as written.

## Issues Encountered
- The TruffleHog pre-commit hook failed twice with a transient updater error (`fork/exec /opt/homebrew/bin/trufflehog: no such file or directory`). The binary exists, the scan found 0 secrets, and the same commits passed on retry. This is logged in `deferred-items.md`.
- `npx eslint .` reports 6 errors, all in files from before this plan (ArticleList, BoardArticleList, ReadingPane, the SettingsDialog Raindrop effect, Toast). The files this plan added lint clean. This is also logged in `deferred-items.md`. The plan's verification (`tsc -b` + the full vitest suite) does not run eslint.

## Verification
- `(cd src/main/frontend && npx tsc -b)`: exit 0
- `npm --prefix src/main/frontend test`: 15 files, 69 tests passed
- All the acceptance-criteria greps for Tasks 1-3 printed their expected values: no `dangerouslySetInnerHTML`, no `'Escape'`, no `preferencesStore` in InterestsDialog, and no hex literals in the `interests-*` CSS.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- 03-06 (topic rows) can add topic hooks to `useInterest.ts`, set `meta: { inlineError: true }`, render inside `.interests-body` after the profile section, and pass its dirty-topic count to `describeUnsaved`.
- 03-07 (preview) can branch on `ApiError.status`/`title` and read `useInterestStatus()` to decide when preview is disabled.
- Visual/theme check of the dialog and the Settings → Interests hand-off is queued for end-of-phase UAT (coverage D6).

## Self-Check: PASSED
- All six created files exist, and commits 71a2207, dd75f9e and d902baa are in history.

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*
