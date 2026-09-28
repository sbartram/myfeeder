---
phase: 03-interest-model-schema-rubric-editor
plan: 07
subsystem: ui
tags: [react, tanstack-query, zustand, typescript, vitest, interests, jev, preview]

requires:
  - phase: 03-interest-model-schema-rubric-editor
    provides: "03-04 POST /api/interest/preview {noul, model} with fixed-text ProblemDetails; 03-05 interestApi.preview, useInterestStatus, ApiError(status, title), meta.inlineError; 03-06 TopicRow/TopicRowState, topicLabel, hinge/formatSigned/formatPreviewText"
provides:
  - "src/hooks/useInterest.ts: usePreviewTopic (inlineError, no retries, no invalidation)"
  - "src/components/TopicRow.tsx: PreviewBlockKind/PreviewBlock types; optional articleId/previewBlock props; Preview topic button; TopicPreviewResult (live D-13 math, stale, failure copy); previewDisabledReason; previewErrorMessage"
  - "src/components/InterestsDialog.tsx: computePreviewBlock (D-14 order) and the 'Previewing against' / 'Preview unavailable' target line"
  - "App.css: .interests-preview-target/-title/-result(.stale, :empty)/-points/-pending/-nomatch"
affects: [03-08, 04-scoring, 05-priority-view]

actuals:
  tokens: 7672
  tasks: 2
  commits: 2
plan_head_before: 09a8be80e3aba78584aaec8d8caa43d3891aee25

tech-stack:
  added: []
  patterns:
    - "Section-level availability block computed once in the dialog and passed to every row; rows add only row-specific reasons (blank description)"
    - "Preview results store {noul, description, articleId}; math is rendered from noul and the row's current weight, staleness is derived by comparing the stored inputs to the current ones"
    - "Always-mounted aria-live slot hidden with :empty so it takes no row gap while idle"

key-files:
  created: []
  modified:
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/TopicRow.tsx
    - src/main/frontend/src/components/TopicRow.test.tsx
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/App.css

key-decisions:
  - "03-07: PreviewBlock is computed once per dialog (not configured, breaker OPEN/FORCED_OPEN, no article, status pending, status unknown) and passed to every TopicRow; the row adds the blank-description reason between no-article and status-pending"
  - "03-07: Status data wins over a failed refetch: status-unknown applies only when the status query has no data and has failed; status-pending when it has no data yet"
  - "03-07: The preview result slot is always mounted (aria-live polite) and hidden via .interests-preview-result:empty; a failure replaces the result in the slot until the next click"
  - "03-07: TopicRow falls back to the no-article reason when articleId is null and no block is passed, so a standalone row can never send articleId null"

patterns-established:
  - "Billed calls: mutate only from a click handler, TanStack default of no mutation retries, no invalidation; failedPreviewIsNeverRetried runs on the app's createQueryClient()"

requirements-completed: [INT-04, INT-06]

coverage:
  - id: D1
    description: "Preview topic sends one POST /api/interest/preview with the row's trimmed current description, the selected article id and the topic id (null for drafts), and renders the D-13 math line"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#previewSendsTheRowAndShowsTheMath"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#draftPreviewSendsNullTopicId"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#noMatchShowsMutedCopy"
        status: pass
    human_judgment: false
  - id: D2
    description: "In flight: 'Previewing…' (disabled) and 'Asking Jev…' in the aria-live slot, while other rows' Preview buttons stay enabled"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#inFlightShowsPreviewingAndAskingJev"
        status: pass
    human_judgment: false
  - id: D3
    description: "Weight-only changes recompute the points from the stored noul with no new request; description or article changes dim the result (stale class) and append the stale line"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#weightChangeRecomputesWithoutANewCall"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#descriptionOrArticleChangeMarksTheResultStale"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#articleChangeMarksExistingResultsStale"
        status: pass
    human_judgment: false
  - id: D4
    description: "Preview disabled reasons in the D-14 order (not configured, breaker, no article, blank description, status pending, status unknown) in the button title, and once on the target line as 'Preview unavailable: …'; editor still works when status fails (E1 partial)"
    requirement: INT-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#previewDisabledReasonsFollowTheSpecOrder"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#previewEnabledHasNoTitle"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#previewTargetLineShowsUnavailableReasons"
        status: pass
    human_judgment: false
  - id: D5
    description: "Target line 'Previewing against: {title}' with the full title in its title attribute, or 'loading article…' while the article loads"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#previewTargetLineShowsTheOpenArticleTitle"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#previewTargetLineShowsLoadingWhileTheArticleLoads"
        status: pass
    human_judgment: false
  - id: D6
    description: "Failures show the status-specific 'Preview failed: …' copy in .dialog-error inside the slot (including a breaker 503 while the dialog still showed CLOSED), are never retried, and a preview triggers no other request nor any uiStore write"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#previewFailuresShowStatusSpecificCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#failedPreviewIsNeverRetried"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#previewTriggersNoOtherRequests"
        status: pass
    human_judgment: false
  - id: D7
    description: "End-of-phase visual check: preview strip, stale dimming, ellipsis on the target line and math-line wrapping in narrow rows across all six themes; optional live preview with a real key (plan human-check steps 1-6)"
    verification: []
    human_judgment: true
    rationale: "Layout, theme contrast, ellipsis and wrapping (UI E5 overflow backstop, E6, E7) are not asserted in jsdom, and a live preview needs the user's billed TypeSafe key"

duration: 6min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 07: Topic Preview Summary

**Each topic row now has a "Preview topic" button. One click sends one billed Jev call that judges the row's draft description against the article open in the reading pane, and the row shows the hinge × weight math ("Match 82% → counts 64% × +20 = +12.8 pts"). Changing the weight recomputes the points locally. Changing the description or the article marks the result stale. The button explains why it is disabled, in the D-14 order. Failures show status-specific copy and are never retried.**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-09-23T18:34:56Z
- **Completed:** 2026-09-23T18:40:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- `usePreviewTopic` is a plain `useMutation` with `meta.inlineError`, no retries and no invalidation. Each `TopicRow` owns its own instance, so the pending state stays in that row.
- The Preview button sits first in the row actions and is labeled `Preview topic: {topic}`. It sends `{articleId, description: trimmed, topicId}` and keeps `{noul, description, articleId}` from the last success.
- `TopicPreviewResult` is an always-mounted `aria-live="polite"` strip. It shows "Asking Jev…", then the math from the stored noul and the row's current weight (the points span uses the sign color), or the muted "No match (contributes 0)" line. A failure shows `.dialog-error` copy. A stale result gets opacity 0.5 plus the stale line.
- `InterestsDialog` reads `selectedArticleId` from the store without ever writing it, and loads the title with `useArticle`. It computes one `PreviewBlock` in the D-14 order and renders either "Previewing against: {title}" (full title in `title`, ellipsis) or "Preview unavailable: {reason}".
- `previewErrorMessage` maps a 503 titled "Jev not configured", then 503/429/5xx, then 400/422 (with detail), then everything else to the UI-SPEC copy.

## Task Commits

1. **Task 1 (tracer): preview against the open article with the scoring math** - `2c515cf` (feat)
2. **Task 2: disabled reasons, stale results, failure copy and styles** - `52fd7a9` (feat)

Tracer gate: the run was interactive in `end-of-phase` mode, and Task 1's `<verify>` was automated-only. The verify commands were re-run and passed (38 tests, `tsc -b` clean), so expansion continued with no checkpoint.

## Files Created/Modified
- `src/main/frontend/src/hooks/useInterest.ts` - `usePreviewTopic`
- `src/main/frontend/src/components/TopicRow.tsx` - Preview button, `PreviewBlock` types, `previewDisabledReason`, `previewErrorMessage`, `TopicPreviewResult`
- `src/main/frontend/src/components/InterestsDialog.tsx` - `computePreviewBlock`, the preview target line, and `articleId`/`previewBlock` passed to each row
- `src/main/frontend/src/components/TopicRow.test.tsx` - 10 preview tests; the fetch mock now awaits async handlers
- `src/main/frontend/src/components/InterestsDialog.test.tsx` - 5 preview tests; the `useUIStore` selection is reset after each test
- `src/main/frontend/src/App.css` - preview styles (theme variables only)

## Decisions Made
- A single section-level block is passed to every row. The row adds only the blank-description reason, placed between no-article and status-pending, per the UI-SPEC order.
- "status-unknown" applies only when the status query has no data and has failed. If a refetch fails while earlier data exists, the earlier data is used.
- The result slot is always mounted so screen readers announce updates. `:empty { display: none }` keeps it from adding a row gap while idle.
- If a TopicRow gets `articleId` null and no block, it falls back to the no-article reason. That way it can never POST with a null article id.

## Deviations from Plan

**1. [Rule 3 - Blocking] The TopicRow test fetch mock awaited nothing**
- **Found during:** Task 1
- **Issue:** The deferred-response test needs async route handlers, but the 03-06 mock passed the handler's return value straight to `jsonResponse`. That was a type error and broke at runtime.
- **Fix:** The Handler type now allows `Reply | Promise<Reply>`, and the mock awaits it, the same way the InterestsDialog test mock does.
- **Files modified:** src/main/frontend/src/components/TopicRow.test.tsx
- **Committed in:** 2c515cf

**2. [Additive] Two tests beyond the plan's list**
- `previewEnabledHasNoTitle` (TopicRow) and `previewTargetLineShowsLoadingWhileTheArticleLoads` (InterestsDialog). They cover the enabled state and the "loading article…" copy, which the must-haves require but the listed tests didn't assert.

**Total deviations:** 1 auto-fixed (blocking) plus 2 additive tests. **Impact:** none on scope.

## Issues Encountered
- To confirm the stale tests aren't vacuous, the article-change comparison was removed temporarily. Both stale tests failed, and the file was restored before committing.
- If `GET /api/articles/{id}` fails, the target line keeps reading "Previewing against: loading article…". The UI-SPEC has no copy for this state, and Preview still works (the server returns a 404, which is shown with the "other" failure copy).

## User Setup Required
None. A live preview optionally needs `MYFEEDER_TYPESAFE_API_KEY` (end-of-phase human check step 5).

## Next Phase Readiness
- INT-04 and the Preview half of INT-06 are done in the UI. 03-08 (calibration) is next.
- The plan's `<human-check>` (profile persistence, weight validation, negation warning, no-key notice and disabled Preview, optional live preview, all six themes at under 600px) is queued for end-of-phase UAT.

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*

## Self-Check: PASSED

All modified files exist; commits 2c515cf, 52fd7a9 and e1f6267 are in history; full frontend suite 108/108 and `tsc -b` clean.
