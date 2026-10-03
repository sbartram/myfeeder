# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v0.3.0 — Engagement Learning

**Shipped:** 2026-10-02
**Phases:** 5 | **Plans:** 24 (57 tasks) | **Commits:** 233 over 4 days

### What Was Built
- Engagement capture (V7): opens and saves (star, board, Raindrop) recorded as sticky, forgettable rows outside any transaction; released first as v0.3.0 with ranking unchanged
- Engagement as a capped, zero-floored, thumbs-overridable implicit up-vote in the learned CTE, with an exact base + thumbs + engagement split and cap 0 bit-identical to v0.2.1
- Explainable engagement in "Why N?", the vote toast and Interests, plus one shared post-engagement reaction that never re-sorts an open Priority list
- Gap discovery: "Suggested topics" from engaged, unmatched articles, create-from-draft that atomically marks the suggestion handled, and permanent dismissal
- Calibration replay extended (engaged, dormant, floor, simulated backfill) and the calibrated build released as v0.3.1

### What Worked
- Shipping capture first with ranking provably unchanged let prod collect engagement while Phases 9–11 were built
- Test-first SQL for the highest-risk change: a 1,456-cell real-Postgres grid with an exact oracle and a "cap 0 equals SQL frozen from the v0.2.1 tag" check made the learned CTE rewrite safe
- Applying v0.2.1's lesson: the replay drift guard got bypass tests up front (12-01/12-02), so no gap-closure plans were needed this time
- Reusing one reaction (`afterEngagement`) and one draft path (`addDraft` with `sourceArticleId`) kept UI behavior consistent across six entry points
- Code-review findings were carried forward and fixed in the next phase (09 WR-01/03 in Phase 10, 09 WR-02 in Phase 12)

### What Was Inefficient
- The calibration phase ran only days after capture shipped, so prod data stayed below the D-02 floor (13/30 articles, 0/3 topics) and the outcome was the D-03 fallback; the roadmap's own note asked for 2–4 weeks of engagement
- Phases 8–11 again closed with stale verification digests, and again no `/gsd-audit-milestone` was run before close
- The Phase 10 deferred ESLint item was never closed, so it surfaced at milestone close

### Patterns Established
- Best-effort side writes (`recordQuietly`) live in services with no transaction boundary, so they can never abort the user's action
- Every derived-model change ships with an exact split proof plus a "disabled equals previous release" proof against frozen SQL
- Self-validating `@ConfigurationProperties` (`Validator`) with fixed-text startup refusals for tuning constants
- Ranking SQL never reads UI-state tables (the dismissal table), guarded by a test
- Release-then-learn: ship new signal capture as its own release before any model consumes it

### Key Lessons
1. Schedule calibration by data volume, not by phase order: gate the calibration phase on the data floor, not on the calendar.
2. For multi-phase milestones that rework shared files, run `/gsd-audit-milestone` once at the end instead of accepting stale-digest overrides twice.
3. Exact-oracle grid tests and frozen-SQL equivalence are worth their cost for any query-time model change; they caught no regressions only because they existed before the change.

### Cost Observations
- Model mix: not tracked
- Sessions: not tracked (4 calendar days)
- Notable: zero Jev spend for the whole milestone; every engagement effect is computed at query time

---

## Milestone: v0.2.1 — Interest Ranking

**Shipped:** 2026-09-29
**Phases:** 7 | **Plans:** 51 (135 tasks) | **Commits:** 397 over 8 days

### What Was Built
- Dependency upgrade (Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3) released first as v0.1.24, which also fixed HTTP timeouts that had been silently unbound
- A keyless-safe, app-owned TypeSafe Jev client behind one Resilience4j retry layer, wrapped by the `jev` breaker
- Interests dialog: profile, weighted topic rubric and one-call topic preview, with question wording calibrated live
- A background scoring pipeline (ingest hand-off, bounded 1-thread queue, 2-minute backfill sweep) that never touches feed polling
- A query-time blend CTE behind the Priority view, tier badges, an exact "Why N?" breakdown, and reversible thumbs feedback derived in SQL
- Production rollout (v0.2.0) with the backlog drained cleanly, then tiers calibrated by a read-only prod replay (v0.2.1)

### What Worked
- Doing the dependency upgrade as its own released phase first: the starter's Boot 4.0.7 baseline problem was solved before any Jev code existed
- Storing raw Jev outputs and blending at query time: weight edits, votes and tier tuning all shipped with zero re-scoring and zero extra Jev spend
- One shared predicate or builder per concern (`ELIGIBLE`, `InterestQuestions`/`ArticleStateBuilder`, one blend CTE) kept preview, scorer, sweep, status and Re-score consistent
- A gated live calibration spike (03-08) before building the scorer caught the under-firing v1 wording and the 5s timeout early
- Human UAT at phase ends found real defects the automated suite could not (G-04-1 bootTestRun config shadowing, G-05-7 cursor skip)

### What Was Inefficient
- The same scoring and blend files were edited across phases 3–7, so verification digests for phases 1–6 went stale and the milestone closed as an override
- The deferred-items and debug-session files were never flipped to resolved when later plans fixed them, so 5 of the 8 open audit items at close were already done
- Phase 7 needed two extra gap-closure plans (07-10, 07-11) to harden a test-only drift guard, and code review still leaves advisories (WR-05, IN-10)
- No `/gsd-audit-milestone` run before close

### Patterns Established
- The API-client bean carries `@CircuitBreaker` + `@Retry` with the breaker as the outer aspect (orders 1/2); business validation stays in the service
- A single retry layer: SDK retries off, Retry-After honored and capped
- Tuning constants live in committed yaml and are served to the frontend (`/status` `tiers`), never set through Helm/env
- Read-only calibration replays of verbatim production SQL, pinned by a drift-guard test
- `dev` profile overlay for `bootTestRun`, while the test suite stays offline, both sides enforced by tests

### Key Lessons
1. When a plan fixes an item tracked elsewhere (deferred-items, debug session, todo), close that artifact in the same commit, or milestone close inherits phantom open items.
2. Expect verification staleness when later phases rework shared files; schedule one final cross-phase verification or a `/gsd-audit-milestone` instead of re-verifying each phase.
3. Run a gated live spike against a billed external model before building the pipeline around it; cheap calibration found two blocking problems.
4. A test-only guard (the replay drift guard) deserves adversarial "bypass" tests up front, which would have saved two gap-closure plans.

### Cost Observations
- Model mix: not tracked
- Sessions: not tracked (8 calendar days)
- Notable: Jev spend stayed bounded by design: one call per article, a 183-article backfill with 0 retries, and all tuning done at query time

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v0.2.1 | — | 7 | First GSD-managed milestone: phase UAT, security verification and code-review gap closures |
| v0.3.0 | — | 5 | Release-then-learn (capture shipped first); test-first SQL with exact-oracle grids; review findings fixed in the following phase |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.2.1 | ~532 backend `@Test` (from 162) + 320 frontend | — | — |
| v0.3.0 | ~717 backend `@Test` + 408 frontend (723 backend tests run at the Phase 12 gate) | — | — |

### Top Lessons (Verified Across Milestones)

1. Verification digests go stale when later phases rework shared files (v0.2.1, v0.3.0); plan one end-of-milestone audit instead of per-phase re-verification.
2. Close tracked artifacts (deferred items, debug sessions) in the same commit that fixes them, or they resurface at milestone close (v0.2.1, v0.3.0).
3. Test-only guards and derived SQL models need adversarial or exact-oracle tests written before the change (v0.2.1 learned it the hard way; v0.3.0 applied it with no gap closures).
