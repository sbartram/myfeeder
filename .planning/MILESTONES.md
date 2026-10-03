# Milestones

## v0.3.0 Engagement Learning (Shipped: 2026-10-02)

**Delivered:** Opening an article's original link or saving it (star, board, Raindrop) teaches the interest ranking as a small, capped, thumbs-overridable implicit up-vote derived at query time, with no extra Jev calls and every learned point explainable ("Why N?" splits votes from engagement) and reversible (Forget); engaged articles no topic covers become topic suggestions.

**Phases completed:** 5 phases (8–12), 24 plans, 57 tasks
**Releases:** v0.3.0 (engagement capture, V7 schema, ranking unchanged; Helm rev 20), v0.3.1 (learning model, explainability, gap discovery, calibrated constants; Helm rev 21)
**Timeline:** 4 days (2026-09-29 → 2026-10-02)
**Git range:** `8fd4145` (docs: start milestone v0.3.0) → `13fd203` (phase 12 transition); 233 commits, 41 `feat(`
**Code:** +3,421 / −279 lines across 60 files in `src/main` (218 files, +31,285 / −1,967 repo-wide incl. tests and planning); now ~6.0k main Java, ~16.2k test Java, ~15.0k TS/TSX

**Key accomplishments:**
- Engagement capture (V7 `article_engagement` + `topic_suggestion_dismissal`): opens via one fire-and-forget `useOpenOriginal` helper, STAR/BOARD/RAINDROP recorded server-side by `recordQuietly` outside any transaction; sticky, cascading on delete, forgettable from the reading pane; released as v0.3.0 with Priority order, badges and "Why N?" identical to v0.2.1
- Engagement learning in `LEARNED_CTE`: one MAX strength per article, SCORED only, thumbs override with exact restore, negative-base skip, own additive cap with a zero floor; exact base + thumbs + engagement split proven on a 1,456-cell real-Postgres grid, and cap 0 bit-identical to SQL frozen from the `v0.2.1` tag
- Self-validating yaml constants (`ENGAGEMENT_INVALID` refuses startup) and a latency budget proven with ~20k engagement rows (no index needed)
- Explainable engagement in the UI: "Why N?", the vote toast and the Interests learned line show votes and engagement separately from server split fields; one never-rejecting `afterEngagement` reaction updates badges in place and lights "Ranking changed" without re-sorting an open Priority list (SC-3 agreement test)
- Gap discovery: "Suggested topics" in Interests lists engaged, SCORED, unvoted articles below a 0.35 near-miss; Create topic adds a +20 draft whose save marks the article handled in the same transaction; Dismiss is permanent; Jev never called
- Calibration replay extended (six-field candidates, engaged/dormant/floor/backfill-simulation sections, drift and privacy guards); prod data was below the D-02 floor, so the D-03 fallback kept 0.25 / 0.5 / 8 (marked revisit) and shipped them as v0.3.1 with a clean startup and soak

**Closeout type:** override_closeout
**Known verification overrides:** 1 newly acknowledged, 8 carried forward from a prior close (see STATE.md Deferred Items). Phases 8–11 were closed with `stale` verification digests: their VERIFICATION.md files say `passed`, but later phases changed covered files. Phase 12's passed verification and full regression gate (723 backend tests + frontend suite) cover the final code. No milestone audit was run (user chose to proceed without `/gsd-audit-milestone`).

**Carried into the next milestone:** WhyBreakdown `set-state-in-effect` ESLint error (plus the 7 pre-existing ESLint errors), engagement constants revisit once the D-02 floor is met (`$HOME/.cache/myfeeder-phase12/sc3.sh`), ENG-F5 backfill recommended but unscheduled, open review advisories (08 WR-01, 09 IN-02/IN-03, 11 WR-02, 12 warnings), Raindrop retry/backoff tuning todo.

**Git tag:** `v0.3.0` and `v0.3.1` already existed as axion release tags (created by `./gradlew release`), so no new tag was created at close.

---

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
