# Milestones

## v0.2.1 Interest Ranking (Shipped: 2026-09-29)

**Delivered:** Every new article is judged once by TypeSafe Jev against a written interest profile and a weighted topic rubric, and a Priority view ranks unread articles by an explainable 0–100 score that thumbs feedback tunes with no extra Jev calls.

**Phases completed:** 7 phases (1–7), 51 plans, 135 tasks
**Releases:** v0.1.24 (dependency upgrade), v0.2.0 (interest ranking live), v0.2.1 (calibrated tiers)
**Timeline:** 8 days (2026-09-22 → 2026-09-29)
**Git range:** `7fbbf28` (docs: initialize project) → `0d17bd2` (phase 07 complete); 397 commits, 85 `feat(`
**Code:** +12,854 / −739 lines across 116 files in `src/main`; now ~5.4k main Java, ~11.6k test Java, ~12.5k TS/TSX

**Key accomplishments:**
- Upgraded to Spring Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3 plus in-major frontend bumps, pinned Reactor Netty and fixed the silently-unbound HTTP timeouts; shipped as v0.1.24
- Keyless-safe, app-owned `TypeSafeClient` with a single Resilience4j retry layer wrapped by the `jev` circuit breaker (Retry-After aware, 30s timeout, auto HALF_OPEN) and an optional Helm secret
- Interests dialog with a profile editor, a weighted (−50..+50) topic rubric and a one-call topic preview; question wording calibrated live (v2 "substantially about") on the V6 interest schema
- Background scoring that never touches polling: ingest hand-off to a bounded 1-thread `jev-score` queue, a 50-per-2-min backfill sweep under one eligibility predicate, capped FAILED retries and a manual "Re-score unread"
- Query-time blend CTE drives the Priority view (opaque served-tuple keyset cursor), tier badges in every list and an exact "Why N?" breakdown; reversible thumbs up/down re-weights topics via a derived, capped learned adjustment
- Production rollout: the 183-article backlog drained in 6m54s with 0 FAILED and the breaker CLOSED; tiers tuned to 70/22 by a read-only, drift-guarded replay of the blend SQL

**Closeout type:** override_closeout
**Known verification overrides:** 8 newly acknowledged, 0 carried forward from a prior close (see STATE.md Deferred Items). Phases 1–6 were also closed with `stale` verification digests: their VERIFICATION.md files say `passed`, but later phases changed covered files. Phase 7's passed verification covers the final code.

**Carried into the next milestone:** frontend ESLint errors (7), InterestsDialog "primarily about" help copy, Raindrop retry/backoff tuning todo; 07 review advisories WR-05 / IN-10 and deferred 05 WR-01 / WR-03.

**Git tag:** `v0.2.1` already existed as the axion release tag (created by `./gradlew release`), so no new tag was created at close.

---
