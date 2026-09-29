# myfeeder

## What This Is

myfeeder is a self-hosted, single-user feed reader (Spring Boot 4 + React) running on a homelab k3s cluster. It subscribes to RSS, Atom and JSON Feed sources, polls them on a schedule, and presents articles in a three-panel reader with folders, boards, reader view, and Raindrop.io forwarding. Since v0.2.1 it also has **interest ranking**: every new article is judged once by TypeSafe's Jev judgment model against the reader's written interest profile and weighted topic rubric, and a Priority view surfaces the articles that matter most, with an explainable 0–100 score that thumbs up/down feedback tunes without extra Jev calls.

## Core Value

Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

## Current State

**Shipped:** v0.2.1 Interest Ranking (2026-09-29), in production on k3s (Helm revision 19). The milestone went out as three releases: v0.1.24 (dependency upgrade), v0.2.0 (interest ranking with a live TypeSafe key) and v0.2.1 (calibrated tiers 70/22). Archive: `.planning/milestones/v0.2.1-ROADMAP.md`, `.planning/MILESTONES.md`.

**Codebase:** ~5.4k lines of main Java, ~11.6k of test Java and ~12.5k of TypeScript/TSX. Spring Boot 4.0.8, Spring AI 2.0.1, Spring Cloud 2025.1.3, spring-ai-starter-typesafe 0.1.0 (jev-1.13.0), Flyway through V6.

## Next Milestone Goals

Not yet defined. Start with `/gsd-new-milestone`. Candidates carried forward from v0.2.1:

- Frontend ESLint cleanup (7 `react-hooks` errors; `npm run build` doesn't run eslint)
- InterestsDialog help text still says "primarily about" (should match the shipped v2 "substantially about" wording)
- Raindrop resilience tuning (`retry-exceptions`, the inert backoff multiplier)
- Open review advisories: 05 WR-01 (two-statement breakdown read), 05 WR-03 (topic edits don't invalidate article caches), 07 WR-05 / IN-10 (replay drift-guard hardening), IN-01 (70/40 tier fallback flash)
- Previously out of scope and now unblocked: CONCERNS.md fixes (JSON Feed dates, SSRF DNS rebinding), sort-by-interest on other lists, react-router v7 (clears the two accepted advisories)

## Requirements

### Validated

<!-- Inferred from the existing codebase (.planning/codebase/, mapped 2026-09-22 at 5aa00cc). -->

- ✓ Subscribe to RSS/Atom/JSON Feed URLs with SSRF guard and 10 MiB body cap — existing
- ✓ Scheduled per-feed polling with conditional GET (ETag/If-Modified-Since) and exponential error backoff — existing
- ✓ Article dedup by GUID on upsert; chronological sort by COALESCE(published_at, fetched_at) with cursor pagination — existing
- ✓ Read / starred state, mark-read, unread counts — existing
- ✓ Folders (with drag-and-drop) and boards (curated article collections) — existing
- ✓ Reader view via Readability4J extraction, cached in DB — existing
- ✓ OPML import/export (XXE-protected) — existing
- ✓ Save articles to Raindrop.io behind Resilience4j circuit breaker + retry — existing
- ✓ Retention cleanup job — existing
- ✓ Vim-style keyboard shortcuts, 6 themes, persisted preferences — existing
- ✓ Helm/k3s deployment with release pipeline (axion tags, Dockerfile image) — existing
- ✓ Dependencies upgraded (patch/GA line): Spring Boot 4.0.8, Spring AI BOM 2.0.1, Spring Cloud 2025.1.3, frontend in-major bumps; 162 backend + 47 frontend tests green; released and deployed as v0.1.24 — v0.2.1 (Phase 1)
- ✓ TypeSafe Jev integrated via `spring-ai-starter-typesafe` 0.1.0, optional like Raindrop (keyless startup), `JevApiClient.judge()` behind Resilience4j `jev` breaker/retry, optional Helm secret; live smoke returned `jev-1.13.0` — v0.2.1 (Phase 2)
- ✓ Interest profile (≤2,000 chars) and topic rubric (≤25 topics, weights −50..+50) editor with negation warning, not-configured/cold-start notices and one-call topic preview against the open article; V6 interest schema; question wording calibrated (v2 "substantially about") — v0.2.1 (Phase 3)
- ✓ Each newly ingested article judged once by Jev off the polling thread (bounded jev-score queue), raw profile `Score` + per-topic `Noul` stored per article; a 50-per-2-min sweep backfills the unread backlog, retries FAILED rows up to 3 attempts and pauses while the breaker is open; manual "Re-score unread"; `/api/interest/status` counts — v0.2.1 (Phase 4)
- ✓ Blended 0–100 interest score computed at query time from stored raw Jev outputs (one CTE is the single source of sort and badge), so weight edits re-rank on refresh with no Jev calls — v0.2.1 (Phase 5)
- ✓ Priority view at `/priority` (feed tree + `g p`): unread scored articles by score, ties by date, then "Not yet scored" by date; keyset paging with an opaque served-tuple cursor that survives mid-walk score changes (G-05-7); frozen order while triaging; status banner — v0.2.1 (Phase 5)
- ✓ Tier-colored interest badge in every article list and the reading pane, with matched-topic chips and an exact "Why N?" breakdown that sums to the badge — v0.2.1 (Phase 5)
- ✓ Thumbs up/down (buttons + `u`/`d`, Shift+D narrow picker) stores one reversible `article_feedback` row; the learned adjustment (capped ±20, sign-clamped, within ±50) is derived in SQL on every read, so the badge, Why row and Priority order update with no Jev call and no write to topic weights; effect toast, no-match "Create topic from article" draft, learned line per topic in Interests — v0.2.1 (Phase 6)
- ✓ Interest ranking live in production (v0.2.0 with a real TypeSafe key, v0.2.1 calibrated): launch backfill drained 183 legacy articles in 6m54s with 0 FAILED rows and the breaker CLOSED; blend constants tuned by a read-only prod replay (profile-points 100, tiers 70/22, learn-rate 2, learned-cap 20) and served to the badge via `/status` `tiers`; Jev retry/breaker log lines; CLAUDE.md documents the Jev config, throttle levers and tuning procedure — v0.2.1 (Phase 7)

### Active

(none; defined by the next milestone's `/gsd-new-milestone`, see Next Milestone Goals)

### Out of Scope

- Automatic re-scoring when the profile text or topic list changes — a manual, user-triggered "Re-score unread" covers this; weight changes still apply instantly via query-time blend
- Full article content or on-demand Readability extraction as Jev input — title + summary (falling back to stripped, truncated content) is enough
- Using liked/disliked articles as in-context examples — feedback adjusts topic weights instead
- Sort-by-interest on every article list and hiding/dimming low-interest articles — Priority view + badge only for this milestone
- CONCERNS.md fixes (JSON Feed dates, SSRF DNS rebinding) and FeedPanel refactor — milestone kept focused on Jev ranking
- Jev advisors (self-refine, guardrails), RAG reranking, tool index — not relevant to feed ranking
- Multi-user profiles — single-user app
- Spring Boot 4.1 / react-router 7 / frontend major upgrades — Spring Cloud has no GA line for Boot 4.1 yet; router major is unrelated churn

## Context

- Brownfield: codebase mapped in `.planning/codebase/` (STACK, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, INTEGRATIONS, CONCERNS).
- Reference: Spring blog "Spring AI TypeSafe: structured judgment" (2026-09-21). Jev is a judgment API, not a chat model: `TypeSafeClient.systemOne(state, Map<String, Question>)` with `Noul` (yes/no probability in [0,1]), `Choice`, and `Score` (continuous value over an ordered rubric, per-level probabilities, confidence). ~300ms per call, no streaming, one call per document. Configured via `spring.ai.typesafe.api-key` / `TYPESAFE_API_KEY`; auto-configures a `TypeSafeClient` bean.
- Jev state must be string/object/array/null — bare numbers/booleans produce 422s. Option/instruction text quality strongly affects confidence.
- Polling path: `FeedPollingService` upserts articles; scoring should hook after insert (only for genuinely new articles), ideally asynchronously so polling latency is unaffected.
- Existing precedent for optional external integrations: Raindrop (`RaindropApiClientImpl` with `@CircuitBreaker` + `@Retry` on the API-client bean, business validation in the service, `ignore-exceptions` for not-configured).
- Spring AI with Anthropic is already a dependency (chat only); BOM-managed versions for Spring AI — the TypeSafe community starter is not in the Spring AI BOM and needs an explicit version.

## Constraints

- **Tech stack**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA), Flyway migrations (next is V7), Jackson 3.x (`tools.jackson.*`), React 19 + TanStack Query + Zustand — follow existing conventions in CLAUDE.md
- **Build**: Gradle Kotlin DSL only (never Maven), even though the reference article shows Maven coordinates
- **Resilience**: Jev calls wrapped with `@CircuitBreaker` + `@Retry` on a dedicated API-client bean (`JevApiClientImpl`), following the Raindrop pattern; since Phase 4 the breaker is the outer aspect (aspect orders 1/2), so it records one outcome per article, not per attempt
- **Compatibility**: Spring AI TypeSafe 0.1.0 (built against Boot 4.0.7) runs on Boot 4.0.8; it is not in the Spring AI BOM, so its version is explicit
- **Performance**: Ingest must not block on Jev; polling latency and failure behavior unchanged when Jev is slow or down
- **Cost**: one Jev call per new article (plus one-time backlog backfill); no re-scoring loops
- **Deployment**: new secret `MYFEEDER_TYPESAFE_API_KEY` threaded through `deploy.sh` and Helm chart as optional
- **Pagination**: Priority view uses keyset pagination with the same `{items, nextCursor}` JSON shape as `PaginatedResponse`, via a sibling `PriorityPage` record whose cursor is an opaque encoding of the served `(sort_score, sort_date, id)` tuple (Phase 5, G-05-7); every other paginated endpoint keeps its `Long` id cursor

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Judge at ingest (once per new article) | Predictable cost, instant ranking | ✓ Good — ArticlesIngestedEvent hand-off scores new arrivals before the sweep (Phase 4 UAT) |
| Interest signals = written profile + weighted topic rubric + thumbs feedback | User wants explicit control plus lightweight feedback | ✓ Good — all three signals feed one blend CTE (Phase 6) |
| Weighted blend: profile `Score` + Σ(topic `Noul` × weight), single `systemOne` call | One call per article covers all signals; negative weights push articles down | ✓ Good — one `judge()` per article; negative weights bury articles in Priority (Phases 4–5) |
| Store raw Jev outputs; blend at query time | Thumbs-driven weight nudges re-rank instantly with zero extra Jev calls | ✓ Good — one blend CTE drives sort and badge; weight edits re-rank on refresh with no Jev calls (Phase 5) |
| Thumbs feedback adjusts topic weights (not in-context examples) | Deterministic and explainable | ✓ Good — derived (not stored) learned adjustment keeps votes reversible and base weights untouched; toast and Interests show exact before/after (Phase 6) |
| Jev input = title + summary + feed name | Cheap, always available at ingest, no extra fetches | ✓ Good — calibration 0.859 high-over-low agreement on this input (Phase 3) |
| Optional integration, degrade gracefully + background backfill | Ingest must never fail because of Jev | ✓ Good — ScoringIsolationTest; live 30-article backlog drained with 0 feed errors (Phase 4) |
| Profile/topic edits apply to new articles, plus a manual "Re-score unread" button | Research: ~$0.10/1k articles; only way edits reach existing unread | ✓ Good — count and delete share one scope; scores discarded if the rubric changes mid-call (Phase 4) |
| Score in points on a 0–100 scale: 100×profile + Σ hinge(noul)×weight; weights −50..+50; learned ±20 derived from stored votes, no sign flip | One coherent, explainable model (research R1/R2/R6) | ✓ Good — "Why N?" rows sum exactly to the badge; tuned only at query time (Phases 5–7) |
| Eligibility window: unread, published within 14 days, newest first | Prevents subscribe/OPML/startup floods | ✓ Good — one ELIGIBLE predicate drives sweep, status and Re-score (Phase 4) |
| Summary falls back to stripped/truncated content | Many Atom feeds have content but no summary | ✓ Good — `ArticleStateBuilder` falls back to stripped content, truncated at 1,500 chars (Phase 4) |
| NULL-GUID articles skipped by scorer; parser fix is a separate task | Existing re-insert bug would cause re-scoring | ✓ Good — NULL-GUID articles get a terminal SKIPPED row; the parser fix is still open |
| App-owned TypeSafeClient bean; Resilience4j as the single retry layer | Starter crashes on a blank key; avoid 9× stacked retries | ✓ Good — keyless startup + SDK max-retries 0 tested (Phase 2) |
| Priority view = unread by blended score, unscored after by date | Useful even while backfill is in progress | ✓ Good — shipped with a "Not yet scored" separator and paging across the boundary (Phase 5 UAT) |
| Priority cursor = opaque served `(sort_score, sort_date, id)` tuple, not the article id (Phase 5, G-05-7) | An id cursor re-read the live score and silently skipped rows when it dropped mid-walk | ✓ Good — drop/rise exact-sequence tests; residual: an unserved row whose score rises past the boundary is still skipped (05-REVIEW WR-05, Phase 6 votes should set the "Ranking changed" hint) |
| One-time backfill of unread backlog at launch | Priority view useful immediately | ✓ Good — 183 legacy articles drained in 6m54s, 0 FAILED, breaker CLOSED (Phase 7) |
| Upgrade deps (patch/GA line) as the first phase, before Jev work | TypeSafe starter built against Boot 4.0.7; project was on 4.0.3 + Spring AI milestone M2 | ✓ Good — shipped v0.1.24 (Phase 1), soak clean |
| Pin RestClient transport to Reactor Netty via explicit `reactor-netty-http` (D-01) | Spring AI 2.0.1 dropped it transitively; Boot would silently fall back to the JDK client | ✓ Good — guarded by HttpClientConfigurationTest |
| Bind outbound timeouts under `spring.http.clients.*` (D-02) | Old singular keys were silently unbound, so the 5s/30s timeouts never applied | ✓ Good — stalled feeds now time out (Phase 1) |
| Topic question wording v2 ("substantially about `topic`") (Phase 3 calibration) | v1 "primarily about" under-fired: obvious matches stayed far below noul 0.5 | ✓ Good — v2 doubles obvious-match nouls; under-firing threshold tuning deferred to Phase 4/5 |
| Jev breaker wraps retry (aspect orders 1/2), 30s shared timeout, auto OPEN→HALF_OPEN (Phase 4) | Breaker must count articles, not attempts; profile+topics calls exceed 5s | ✓ Good — closes 02-REVIEW WR-01..03 |
| bootTestRun activates a `dev` profile overlay for live settings; suite stays offline (Phase 4, G-04-1) | Test application.yaml shadows main under bootTestRun | ✓ Good — live-key UAT re-run passed; DevProfileConfigTest guards drift and activation |
| Accept react-router v6 advisories (GHSA-wrjc-x8rr-h8h6, GHSA-337j-9hxr-rhxg) | Fix needs v7 major; no SSR, internal-only navigation targets | ⚠️ Revisit — when a v7 migration is scheduled |
| Badge tier thresholds are server config served on `/status` (D-13), tuned only via committed yaml (D-14) | One source for the badge colours; no Helm/env drift | ✓ Good — prod serves 70/22; frontend falls back to 70/40 until status loads (brief first-paint flash, IN-01) (Phase 7) |
| Calibrate by read-only replay of the verbatim blend SQL against prod, drift-guarded by a test (Phase 7) | Tune without re-scoring or writing prod | ✓ Good — replay matched the app 5/5; only tiers.neutral moved 40→22, so Priority order was unchanged; guard hardened in 07-10/07-11 (WR-05/IN-10 advisories remain) |
| Keep a single retry layer (Resilience4j) and concurrency 1 for launch backfill (D-07) | Two retry layers multiply 429s | ✓ Good — 183-article backfill drained with 0 retries, 0 FAILED, breaker CLOSED; no D-08 throttle needed (Phase 7) |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-29 after v0.2.1 milestone*
