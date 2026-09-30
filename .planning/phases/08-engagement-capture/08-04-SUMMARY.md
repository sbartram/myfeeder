---
phase: 08-engagement-capture
plan: 04
subsystem: ui
tags: [react, tanstack-query, vitest, engagement, forget]

requires:
  - phase: 08-engagement-capture
    provides: "08-01 serves DELETE /api/articles/{id}/engagement (204) and engagement on GET by id; 08-02 built useEngagement.ts, EngagementKind and optional Article.engagement"
provides:
  - "articlesApi.forgetEngagement(id): DELETE /api/articles/{id}/engagement"
  - "useForgetEngagement(): DELETE mutation that refreshes only ['article', id] (exact)"
  - "ScoreRow renders when scored or engaged, with an 'Engaged: <kinds> · Forget' line (aria-label 'Forget engagement')"
  - "useSaveToRaindrop, useAddArticleToBoard and useReadLater refresh the exact by-id article on success"
affects: [08-03 CLAUDE.md text, 08-05 release, phase 9 learned engagement]

actuals:
  tokens: 5535
  tasks: 2
  commits: 3
plan_head_before: 2220bb57afc4ff26e595714ff793d89c7804124b
plan_head_after: 32d8d3d77623371e7c8c42e41fd2aa2e330525c7

tech-stack:
  added: []
  patterns:
    - "Engagement reactions (open, save, board add, Read Later, Forget) invalidate only { queryKey: ['article', id], exact: true }; never 'priority' or 'articles'"
    - "ScoreRow's render guard is 'scored or engaged'; each half renders independently"

key-files:
  created:
    - src/main/frontend/src/components/ScoreRow.test.tsx
    - src/main/frontend/src/hooks/engagementRefresh.test.ts
  modified:
    - src/main/frontend/src/components/ScoreRow.tsx
    - src/main/frontend/src/hooks/useEngagement.ts
    - src/main/frontend/src/api/articles.ts
    - src/main/frontend/src/App.css
    - src/main/frontend/src/components/ReadingPane.test.tsx
    - src/main/frontend/src/hooks/useArticles.ts
    - src/main/frontend/src/hooks/useBoards.ts

key-decisions:
  - "Forget failure uses the global MutationCache error toast (no meta.inlineError); success shows no toast (D-03 rules out confirm and undo, not an error report)"
  - "useAddArticleToBoard still returns the boardArticles invalidation promise so the mutation keeps awaiting that refetch; the new by-id invalidation is fire-and-forget"
  - "useUpdateArticleState is unchanged: its prefix ['article', id] invalidation already refreshes the Engaged line after a star"

patterns-established:
  - "Engaged labels come from the fixed ENGAGEMENT_LABEL map and render as React text (no HTML), in server enum order"

requirements-completed: [CAPT-06]

coverage:
  - id: D1
    description: "The score row shows 'Engaged: <kinds> · Forget' after the badge row for a scored article and alone for an unscored one; no line and no Forget with engagement [] or absent; an unscored article without engagement renders nothing"
    requirement: CAPT-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/ScoreRow.test.tsx#scoredAndEngagedShowsTheLabelAfterWhy, allFourKindsReadInOrder, unscoredButEngagedShowsOnlyTheEngagementLine, noEngagementShowsNoLine"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#unscoredButEngagedShowsTheForgetLineInThePane plus the unmodified score-row tests"
        status: pass
    human_judgment: false
  - id: D2
    description: "Forget sends DELETE /api/articles/{id}/engagement at once, refreshes only ['article', id] (exact) with no toast; a failed Forget shows one error toast and keeps the line"
    requirement: CAPT-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/ScoreRow.test.tsx#forgetDeletesAndRefreshesOnlyThisArticle, aFailedForgetShowsTheErrorToast"
        status: pass
    human_judgment: false
  - id: D3
    description: "Raindrop save, board add and Read Later refresh exactly ['article', id] on success, never 'priority' or 'articles'; a failed save refreshes nothing"
    requirement: CAPT-06
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/engagementRefresh.test.ts (5 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Live look and feel of the Engaged line and Forget in the running app"
    verification:
      - kind: manual_procedural
        ref: "tracer checkpoint: bootTestRun + Vite, open original, star, Forget"
        status: pass
    human_judgment: true
    rationale: "Visual placement and muted styling need a human; the user approved it at the tracer checkpoint"

duration: 16min
completed: 2026-09-30
status: complete
---

# Phase 8 Plan 04: Forget Engagement Control Summary

**The reading pane's score row now shows a muted "Engaged: opened, starred · Forget" line whenever the open article has engagement, including unscored articles. Forget sends `DELETE /api/articles/{id}/engagement` at once and refreshes only that article by exact id. Raindrop, board and Read Later saves also refresh that exact article, so the line updates immediately. Nothing touches Priority or the article lists.**

## Performance

- **Duration:** about 16 min of execution (02:47:59Z to 03:03:53Z), not counting the wait at the tracer checkpoint
- **Started:** 2026-09-30T02:47:59Z
- **Completed:** 2026-09-30T03:03:53Z
- **Tasks:** 2
- **Files modified:** 9 (2 created, 7 modified)

## Accomplishments
- `ScoreRow` renders when the article is scored or engaged. The badge, chips and "Why N?" render only when it is scored. The Engaged line renders whenever `engagement` is non-empty: after a `·` separator when scored, alone when not. The labels are opened, starred, on a board and saved to Raindrop, in server order, joined by `, `.
- The Forget button (`aria-label="Forget engagement"`, `toolbar-btn why-toggle`) is disabled while the DELETE is pending. It has no confirm, no undo and no keyboard shortcut (D-03, D-04). `useKeyboardShortcuts` is untouched.
- `useForgetEngagement` invalidates `{ queryKey: ['article', id], exact: true }` on success. A failure shows the standard error toast.
- `useSaveToRaindrop`, `useAddArticleToBoard` and `useReadLater` add the same exact by-id invalidation and keep their existing reactions.

## Task Commits

1. **Task 1 (tracer): Engaged line with Forget in the score row:** `6cfb34e` (feat)
2. **Task 2: exact by-id refresh after saves (TDD)**
   - RED: `9b063c1` (test)
   - GREEN: `32d8d3d` (feat)
   - No refactor was needed.

## Tracer Checkpoint

The tracer feedback gate stopped after Task 1. The run was interactive, the mode was `end-of-phase`, and the tracer carried a `<human-check>`. For the check, the executor started `./gradlew bootTestRun` and the Vite dev server from this worktree and seeded the Hacker News front-page feed. An API smoke test passed: PUT open returned 204 and GET showed `["OPEN_ORIGINAL"]`; DELETE returned 204 and GET showed `[]`. **The user approved the checkpoint ("approved").** Both servers were then stopped (PIDs killed, ports 8080 and 5173 free, no Testcontainers containers left).

## TDD Gate Compliance (Task 2)

- **RED:** `9b063c1`. `raindropSaveRefreshesOnlyTheByIdArticle`, `boardAddRefreshesTheBoardAndTheByIdArticle`, `readLaterRefreshesTheByIdArticle` and `noSaveInvalidatesPriority` failed on the missing `{ queryKey: ['article', 7], exact: true }` invalidation. `aFailedSaveDoesNotRefresh` passed, as expected for a guard that asserts something is absent. `check tdd-red-evidence` returned `RED_EVIDENCE_OK` / `target_test_failed`.
  - Checker quirk, the same as in 08-02: its JUnit parser reads `classname=` as the test name. The record was therefore keyed on the test class, `src/hooks/engagementRefresh.test.ts`.
- **GREEN:** `32d8d3d`. All 5 tests pass.
- **REFACTOR:** none.

## Files Created/Modified
- `src/main/frontend/src/components/ScoreRow.tsx`: the widened guard, `ENGAGEMENT_LABEL`, the Engaged line and the Forget button
- `src/main/frontend/src/components/ScoreRow.test.tsx`: 6 component tests (label, order, visibility, DELETE, exact invalidation, error toast)
- `src/main/frontend/src/hooks/useEngagement.ts`: `useForgetEngagement`
- `src/main/frontend/src/api/articles.ts`: `forgetEngagement`
- `src/main/frontend/src/App.css`: `.engagement-label`
- `src/main/frontend/src/components/ReadingPane.test.tsx`: the mock exports `useForgetEngagement`, plus the unscored-engaged pane test
- `src/main/frontend/src/hooks/useArticles.ts`, `useBoards.ts`: the exact by-id invalidation in three save hooks
- `src/main/frontend/src/hooks/engagementRefresh.test.ts`: 5 hook tests

## Decisions Made
- A failed Forget shows the global error toast; a successful Forget shows none. This is the plan's recorded interpretation of D-03.
- `useAddArticleToBoard` still returns the `boardArticles` invalidation promise, so its callers see the same settle timing. The by-id invalidation is `void`.
- `ScoreRow` uses `const score = article.interestScore; const scored = score != null`, so TypeScript narrows without a non-null assertion.

## Deviations from Plan

None. The plan was executed as written.

## Issues Encountered
- The worktree sandbox refused the compound git command that writes the plan-head ledger under `.git/`, so `plan_head_before` was recorded here from the verified base SHA.
- `check tdd-red-evidence` has the JUnit name-parsing quirk described above. It is a tool issue, not a plan issue.

## Verification
- `npx tsc -b` exits 0.
- The full `npx vitest run` passes: 32 files, 346 tests.
- Plan checks: `aria-label="Forget engagement"` is in ScoreRow.tsx, and `useForgetEngagement` is exported from useEngagement.ts. Neither save-hook file gains a `'priority'` key, and `useUpdateArticleState` is not in the diff.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- CAPT-06 is covered on the client. 08-03's CLAUDE.md text can name `useForgetEngagement` and the ScoreRow engagement line as built here.
- 08-05 (release) can proceed once 08-03 merges.

---
*Phase: 08-engagement-capture*
*Completed: 2026-09-30*
