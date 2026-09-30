---
gsd_state_version: "1.0"
milestone: v0.3.0
milestone_name: Engagement Learning
current_phase: 8
current_phase_name: Engagement Capture
status: executing
stopped_at: Phase 8 context gathered
last_updated: "2026-09-30T00:00:33.696Z"
last_activity: 2026-09-29
last_activity_desc: v0.3.0 roadmap created (5 phases, 22/22 requirements mapped)
state_head: c6f0dc9fbf2d89de375d34ab4a4810f84f0e890b
progress:
  total_phases: 5
  completed_phases: 7
  total_plans: 5
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-29)

**Core value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.
**Current focus:** Phase 8 — Engagement Capture (v0.3.0 Engagement Learning)

## Current Position

Phase: 8 (Engagement Capture) — READY TO EXECUTE
Plan: — (not planned yet)
Status: Ready to execute
Last activity: 2026-09-29 — v0.3.0 roadmap created (5 phases, 22/22 requirements mapped)

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 51 (all in v0.2.1, Phases 1–7)
- Average duration: -
- Total execution time: -

**By Phase (v0.3.0):**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion. v0.2.1 per-plan metrics (Phases 1–7) are in this file's git history at `18269f8`.*

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

### Pending Todos

- [2026-09-23] [backend] Tune Raindrop resilience and fix CLAUDE.md AspectJ note — [todo file](.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md)

### Blockers/Concerns

- [Phase 8] Release version RESOLVED at roadmap approval (2026-09-29): capture (V7) ships as v0.3.0 (`incrementMinor`), the calibrated build (Phase 12) as v0.3.1 — the v0.2.0→v0.2.1 pattern
- [Phase 9] LRN-05's startup rules collide at zero (0 ≤ 0 trips "save > open"), so validation must accept the disabled setting; highest-risk SQL of the milestone, plan test-first with `--research-phase`
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

Last session: 2026-09-29T20:09:49.966Z
Stopped at: Phase 8 context gathered
Resume file: .planning/phases/08-engagement-capture/08-CONTEXT.md

## Operator Next Steps

- Discuss the first v0.3.0 phase with /gsd-discuss-phase 8
