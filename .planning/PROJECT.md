# myfeeder

## What This Is

myfeeder is a self-hosted, single-user feed reader (Spring Boot 4 + React) running on a homelab k3s cluster. It subscribes to RSS, Atom and JSON Feed sources, polls them on a schedule, and presents articles in a three-panel reader with folders, boards, reader view, and Raindrop.io forwarding. Since v0.2.1 it also has **interest ranking**: every new article is judged once by TypeSafe's Jev judgment model against the reader's written interest profile and weighted topic rubric, and a Priority view surfaces the articles that matter most, with an explainable 0–100 score that thumbs up/down feedback tunes without extra Jev calls. Since v0.3.0 the ranking also **learns from engagement**: opening an article's original link or saving it (star, board, Raindrop) is a small, capped, thumbs-overridable implicit up-vote derived at query time, explained separately from votes in "Why N?", forgettable per article, and engaged articles that no topic covers are offered as topic suggestions.

## Core Value

Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

## Current State

**Shipped:** v0.3.0 Engagement Learning (2026-10-02), in production on k3s as v0.3.1 (Helm revision 21; startup and 10-minute soak clean). The milestone went out as two releases: v0.3.0 (engagement capture, V7 schema, ranking unchanged; Helm revision 20) and v0.3.1 (engagement learning model, explainable engagement UI, gap discovery, calibrated constants 0.25 / 0.5 / 8). Archives: `.planning/milestones/v0.3.0-ROADMAP.md`, `.planning/milestones/v0.2.1-ROADMAP.md`, `.planning/MILESTONES.md`.

**Codebase:** ~6.0k lines of main Java, ~16.2k of test Java and ~15.0k of TypeScript/TSX. Spring Boot 4.0.8, Spring AI 2.0.1, Spring Cloud 2025.1.3, spring-ai-starter-typesafe 0.1.0 (jev-1.13.0), Flyway through V7.

## Next Milestone Goals

Not defined yet; start with `/gsd-new-milestone`. Candidates carried out of v0.3.0:

- Re-run the engagement calibration replay once prod reaches the D-02 floor (≥ 30 counted engaged articles, ≥ 3 non-negative topics with engagement; 13/30 and 0/3 at close) and retune 0.25 / 0.5 / 8 if a candidate beats the defaults
- ENG-F5: a real one-time backfill of existing stars and boards as engagement (recommended by the Phase 12 replay, not scheduled)
- Deferred engagement features: reading-pane engagement status line (ENG-F1), "matched no topic" notice after engaging (ENG-F3), feed affinity
- Cleanup: frontend ESLint errors (incl. WhyBreakdown `set-state-in-effect`), InterestsDialog "primarily about" copy, Raindrop retry/backoff tuning, open review advisories

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

- ✓ Engagement capture: opening the original link (`o`/Open Original, fire-and-forget, tab always opens) and saving (star, board, Raindrop) record sticky, idempotent `article_engagement` rows (V7); a reading-pane "Engaged: … · Forget" line deletes them; ranking unchanged — v0.3.0 (Phase 8, released as v0.3.0)
- ✓ Engagement nudges topic weights as a fractional, capped, thumbs-overridable implicit up-vote, derived at query time in `LEARNED_CTE` with no Jev calls (open 0.25 / save 0.5 / cap 8, cap 0 = exactly v0.2.1); the backend split fields and `ENGAGEMENT_CAP` limit are served; replay and drift guard extended (CAL-01) — v0.3.0 (Phase 9, released in v0.3.1)
- ✓ "Why N?", the vote toast and Interests show engagement-learned points separately from thumbs-learned points (server split fields only); open, star, board add, Raindrop and Forget refetch the article and light "Ranking changed" without re-sorting an open Priority list; vote effects flag replaced engagement and LearnedLimit reports binding clamps before the engagement cap — v0.3.0 (Phase 10, released in v0.3.1)
- ✓ Engaged articles that matched no topic are suggested as new topics: Interests "Suggested topics" lists engaged, SCORED, unvoted, undismissed articles whose best noul is below the 0.35 near-miss; Create topic prefills a +20 draft whose save atomically marks the article handled, Dismiss is permanent; Jev is never called — v0.3.0 (Phase 11, released in v0.3.1)

- ✓ Engagement weights and cap calibrated by the read-only prod replay (engaged, dormant, floor and simulated-backfill sections; drift guard and read-only/privacy tests extended); D-03 fallback on thin data kept open 0.25 / save 0.5 / cap 8, marked revisit; shipped as v0.3.1 — v0.3.0 (Phase 12)

### Active

(none — define the next milestone's requirements with `/gsd-new-milestone`; candidates are listed under Next Milestone Goals)

### Out of Scope

- Automatic re-scoring when the profile text or topic list changes — a manual, user-triggered "Re-score unread" covers this; weight changes still apply instantly via query-time blend
- Full article content or on-demand Readability extraction as Jev input — title + summary (falling back to stripped, truncated content) is enough
- Using liked/disliked articles as in-context examples — feedback adjusts topic weights instead
- Sort-by-interest on every article list and hiding/dimming low-interest articles — Priority view + badge only for this milestone
- CONCERNS.md fixes (JSON Feed dates, SSRF DNS rebinding) and FeedPanel refactor — milestone kept focused on Jev ranking
- Jev advisors (self-refine, guardrails), RAG reranking, tool index — not relevant to feed ranking
- Multi-user profiles — single-user app
- Negative signal from skipped (not opened) articles — skipping is usually lack of time, not disinterest (v0.3.0 decision: positive-only)
- Dwell time, selection or reader view as engagement — j/k skimming and auto-enabled reader view make them noisy (v0.3.0)
- Feed affinity (per-feed bonus from open rate) — deferred; topic nudge + gap discovery first (v0.3.0)
- Writing learned points into `interest_topic.weight` — the derived model keeps every learned point reversible (v0.3.0)
- LLM-drafted topic suggestions — contradicts "no extra Jev calls"; the user writes the topic (v0.3.0)
- Separate weights per save kind, counting repeat opens/saves, a "pause learning" toggle — needless tuning surface or inflation; cap 0 disables learning (v0.3.0)
- Spring Boot 4.1 / react-router 7 / frontend major upgrades — Spring Cloud has no GA line for Boot 4.1 yet; router major is unrelated churn

## Context

- Brownfield: codebase mapped in `.planning/codebase/` (STACK, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, INTEGRATIONS, CONCERNS).
- Reference: Spring blog "Spring AI TypeSafe: structured judgment" (2026-09-21). Jev is a judgment API, not a chat model: `TypeSafeClient.systemOne(state, Map<String, Question>)` with `Noul` (yes/no probability in [0,1]), `Choice`, and `Score` (continuous value over an ordered rubric, per-level probabilities, confidence). ~300ms per call, no streaming, one call per document. Configured via `spring.ai.typesafe.api-key` / `TYPESAFE_API_KEY`; auto-configures a `TypeSafeClient` bean.
- Jev state must be string/object/array/null — bare numbers/booleans produce 422s. Option/instruction text quality strongly affects confidence.
- Polling path: `FeedPollingService` upserts articles; scoring should hook after insert (only for genuinely new articles), ideally asynchronously so polling latency is unaffected.
- Existing precedent for optional external integrations: Raindrop (`RaindropApiClientImpl` with `@CircuitBreaker` + `@Retry` on the API-client bean, business validation in the service, `ignore-exceptions` for not-configured).
- Spring AI with Anthropic is already a dependency (chat only); BOM-managed versions for Spring AI — the TypeSafe community starter is not in the Spring AI BOM and needs an explicit version.

## Constraints

- **Tech stack**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA), Flyway migrations (next is V8), Jackson 3.x (`tools.jackson.*`), React 19 + TanStack Query + Zustand — follow existing conventions in CLAUDE.md
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
| Record engagement outside any transaction via `recordQuietly` (Phase 8 D-07) | A failed engagement insert must never abort the user's star/board/Raindrop save | ✓ Good — shipped in v0.3.0; residual: a process stop right after `createBookmark` loses that RAINDROP row (D-08, accepted in 08-UAT) |
| Ship capture (V7) as v0.3.0 with ranking unchanged, before the learning model (Phase 8) | Prod accumulates engagement before calibration | ✓ Good — V7 applied to prod, Priority scores and Why unchanged, smoke PASS (Phase 8) |
| Engagement = fractional vote through `learnRate`, own cap below thumbs cap, zero floor, one sign/±50 clamp on base + thumbs + engagement (Phase 9 D-06/D-09) | Explicit thumbs must dominate; engagement must never lower a weight or lift a negative-base topic | ✓ Good — exact split proven on a 1,456-cell grid; cap 0 bit-identical to frozen v0.2.1 SQL |
| Self-validating `MyfeederProperties` (cap 0 disables; else 0 ≤ open < save < 1 and 0 < cap < learned-cap), yaml-only tuning (Phase 9 D-02/D-03) | Bad constants must refuse startup, not silently mis-rank | ✓ Good — fixed-text `ENGAGEMENT_INVALID`; no Helm/env keys (09-UAT) |
| No release in Phase 9 (D-14); latency guarded at 10 × baseline + 250 ms with ~20k rows (D-17) | Uncalibrated weights stay off prod until Phase 12 | ✓ Good — no index or migration needed |
| One shared post-engagement reaction (`afterEngagement`): by-id refetch, patch the Priority row only on a changed score, never invalidate `['priority']` (Phase 10 D-06/D-07) | Engaging while triaging must not re-sort the list | ✓ Good — SC-3 agreement test proves refresh makes position, badges and "Why N?" agree; UAT 6/6 |
| LearnedLimit precedence LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE; toast never words ENGAGEMENT_CAP (Phase 10 D-10..D-13) | The note must name the limit that actually binds | ✓ Good — pinned by unit and HTTP tests |
| Accept review edges WR-01 (star-then-vote GET race), WR-02 (narrowed unpicked topic has no note), WR-04 (cancelling parts read "No learned adjustment yet") and board-list badge staleness ≤30s (Phase 10 UAT) | Rare, self-healing on refresh, and consistent with v0.2.1 vote behavior | ⚠️ Revisit — not re-checked at the v0.3.1 release; still accepted |
| Gap-discovery window = first recording per engagement kind (`article_engagement` keeps the first `created_at` per (article, kind)); repeating the same kind does not refresh it (Phase 11 WR-01, UAT) | No migration this phase (Phase 8 D-13); docs corrected to match the code | ✓ Accepted — a `last_engaged_at` column is the route if "latest engagement" is ever wanted |
| Keep engagement constants 0.25 / 0.5 / 8 via the D-03 fallback (Phase 12) | Prod data below the D-02 floor (13 of 30 counted engaged articles, 0 of 3 topics with engagement); every grid candidate equalled cap 0 | ⚠️ Revisit — re-run the replay once the floor is met |
| ENG-F4 keep engaged-but-unscored ineligible; ENG-F5 recommend a real stars/boards backfill (Phase 12 D-09/D-10) | 1 dormant of 16 engaged (< 25%); the simulated backfill stays within the nudge rule | — Backfill not scheduled; no measured benefit yet |
| Suggestion writes (dismiss, topic-from-suggestion) are one `ON CONFLICT DO NOTHING` insert; the topic-create write shares `createTopic`'s transaction (Phase 11) | A topic and its handled mark must commit or roll back together | ✓ Good — `aFailedDismissalInsertRollsBackTheTopic`; UAT 3/3 |

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
*Last updated: 2026-10-02 after v0.3.0 milestone*
