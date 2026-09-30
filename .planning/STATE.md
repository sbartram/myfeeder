---
gsd_state_version: "1.0"
milestone: v0.3.0
milestone_name: Engagement Learning
current_phase: 10
current_phase_name: Explainable Engagement in the UI
status: executing
stopped_at: Phase 10 context gathered
last_updated: "2026-09-30T20:37:12.320Z"
last_activity: 2026-09-30
last_activity_desc: Phase 10 execution started
state_head: ba755e1d4c86b35b48a4b5ca1f45c795cc44e1d9
progress:
  total_phases: 5
  completed_phases: 9
  total_plans: 15
  completed_plans: 13
  percent: 87
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-30)

**Core value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.
**Current focus:** Phase 10 — Explainable Engagement in the UI

## Current Position

Phase: 10 (Explainable Engagement in the UI) — EXECUTING
Plan: 1 of 4
Status: Executing Phase 10
Last activity: 2026-09-30 — Phase 10 execution started

Progress: [█████████░] 87% (v0.3.0: 2 of 5 phases)

## Performance Metrics

**Velocity:**

- Total plans completed: 11 (51 in v0.2.1 Phases 1–7, 5 in Phase 8)
- Average duration: -
- Total execution time: -

**By Phase (v0.3.0):**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 08 | 5 | - | - |
| 09 | 6 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion. v0.2.1 per-plan metrics (Phases 1–7) are in this file's git history at `18269f8`.*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 08 P05 | 29min | 3 tasks | 2 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Settled for v0.3.0 before roadmapping (do not reopen):

- [v0.3.0]: Engagement skips topics with a negative base weight
- [v0.3.0]: Engagement has its own additive cap, below the thumbs cap (20); thumbs take their share first
- [v0.3.0]: Engagement is sticky (unstar, board removal and board delete keep it); a thumbs vote overrides it, and a minimal reading-pane "Forget engagement" control deletes the rows (no tombstone)
- [v0.3.0]: No V7 backfill of existing stars or boards; the Phase 12 replay simulates one
- [v0.3.0]: Open endpoint is a bodyless, idempotent `PUT /api/articles/{id}/engagement/open` returning 204, kind `OPEN_ORIGINAL`
- [v0.3.0]: Engaged-but-unscored articles stay ineligible for scoring; dormant engagement is measured by the calibration replay, with no status field
- [v0.3.0]: Gap discovery = "Suggested topics" list + near-miss filter + dismissal; all V7 schema (engagement and dismissal tables) ships in one migration in Phase 8
- [v0.3.0 roadmap]: Capture ships and releases first with ranking unchanged, so prod accumulates engagement before calibration; CAL-01 (replay + drift guard regeneration) lands with the learned-CTE change in Phase 9; Phase 11 depends only on Phase 8
- [Phase 08]: 08-05: user answered 'approve b' at the D-13 checkpoint; v0.3.0 released (tag at db5acd7, image sha256:7a785af7, Helm rev 20, rollback rev 19), V7 applied to prod, ranking and why UNCHANGED, smoke PASS
- [Phase 09]: engagement learning merged on branch gsd/phase-09-engagement-learning-model with no release (D-14); UAT 3/3, Nyquist compliant, SECURITY 16/16 closed

### Pending Todos

- [2026-09-23] [backend] Tune Raindrop resilience and fix CLAUDE.md AspectJ note — [todo file](.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md)

### Blockers/Concerns

- [Phase 8] 08-REVIEW.md WR-01 open: `recordQuietly` swallows only `DataAccessException`, so a non-DB failure after `createBookmark` could surface an error and invite a duplicate-bookmark retry; IN-01..IN-06 open (non-blocking)
- [Phase 8] 08-UI-REVIEW.md 19/24: Forget styled like the "Why N?" toggle (no danger hover), no "Forgetting…" pending label, Open Original buttons stay enabled for URL-less articles
- [Phase 9] 09-REVIEW-DISPOSITION.md: WR-01, WR-03, IN-01 and IN-04 fixed in Phase 10; WR-02 (replay accepts out-of-range engagement values) stays open for Phase 12, and IN-02 and IN-03 stay open; all non-blocking
- [Phase 12] Waits on several weeks of prod engagement collected after the Phase 8 release
- [Phase 2] 02-REVIEW WR-04 (Raindrop createBookmark POST retried) and WR-05 (helm --set secret mangling) still deferred; WR-01..03 closed in Phase 4
- [Phase 5] 05-REVIEW.md WR-04 closed by quick 260926-hhz (decoded cursor date bounded to years 1..9999 → 404); WR-05 (an unserved row whose score rises past the fixed cursor boundary is skipped — mitigated in Phase 6: votes set the Ranking-changed hint); WR-01/WR-03 user-deferred
- [Phase 3] 03-REVIEW.md WR-01..WR-05 (dialog save race, keyboard shortcuts behind modal, error copy, failed article load) still open
- [Phase 4] 04-VERIFICATION advisories: suite offline-ness relies on shell hygiene (strip SPRING_AI_TYPESAFE_*/SPRING_PROFILES_ACTIVE in the Gradle Test task); dev-overlay drift check is one-directional
- [Phase 4] Preview spinner can run ~93s worst case (3×30s + backoff); consider a UI ceiling
- [Phase 6] 06-REVIEW.md WR-01..WR-04 open (optimistic vote-state race, narrowed-vote toast credits untouched topics, breakdown rows torn by a concurrent vote under READ COMMITTED, Re-score leaves learned cache stale); non-blocking
- [Phase 6] 06-UI-REVIEW.md 21/24: picker can overflow a narrow pane (positioned against viewport), vote-error toasts announced politely not as alert, aria-controls points at an absent element when picker is closed
- [Phase 7] 07-REVIEW-DISPOSITION.md: 12 findings open and non-blocking (e.g. WR-01 breaker log prints "-1.0%" rates on OPEN_TO_HALF_OPEN/HALF_OPEN_TO_CLOSED; IN-01 70/40 first-paint badge flash; WR-05/IN-10 drift-guard advisories needing non-standard SQL spellings)
- [Phase 7] trufflehog pre-push stage is configured but only the pre-commit hook is installed in this clone (`pre-commit install --hook-type pre-push`)

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| debug_sessions | boottestrun-live-jev-unconfigured | diagnosed (fixed by 04-09 dev-profile overlay) | 2026-09-29 | v0.2.1 |
| uat_gaps | 05/05-UAT.md (G-05-7) | diagnosed (fixed by 05-08 served-tuple cursor) | 2026-09-29 | v0.2.1 |
| todos | 2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md | (presence-only) — still open: Raindrop retry-exceptions/backoff | 2026-09-29 | v0.2.1 |
| deferred_items | 03/deferred-items.md: Pre-existing frontend ESLint errors | acknowledged — still open (7 errors now) | 2026-09-29 | v0.2.1 |
| deferred_items | 03/deferred-items.md: TruffleHog hook updater error | acknowledged — fixed (`--no-update` in hook) | 2026-09-29 | v0.2.1 |
| deferred_items | 03/deferred-items.md: InterestsDialog help text says "primarily about" | acknowledged — still open (InterestsDialog.tsx:32) | 2026-09-29 | v0.2.1 |
| deferred_items | 03/deferred-items.md: Jev timeout 5s too short | acknowledged — fixed (30s, 04-01) | 2026-09-29 | v0.2.1 |
| deferred_items | 03/deferred-items.md: CR-01 ArticleStateBuilder.truncate | acknowledged — fixed (max/2 bound, 04-03) | 2026-09-29 | v0.2.1 |

## Session Continuity

Last session: 2026-09-30T18:33:16.130Z
Stopped at: Phase 10 context gathered
Resume file: .planning/phases/10-explainable-engagement-in-the-ui/10-CONTEXT.md

## Operator Next Steps

- Merge `gsd/phase-09-engagement-learning-model` to main with `--no-ff` (no tag/release per D-14)
- Discuss Phase 10 with /gsd-discuss-phase 10
