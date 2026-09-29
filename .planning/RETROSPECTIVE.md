# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

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

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.2.1 | ~532 backend `@Test` (from 162) + 320 frontend | — | — |

### Top Lessons (Verified Across Milestones)

1. (Needs a second milestone to cross-validate)
