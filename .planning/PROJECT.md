# myfeeder

## What This Is

myfeeder is a self-hosted, single-user feed reader (Spring Boot 4 + React) running on a homelab k3s cluster. It subscribes to RSS, Atom and JSON Feed sources, polls them on a schedule, and presents articles in a three-panel reader with folders, boards, reader view, and Raindrop.io forwarding. This milestone adds **interest ranking**: every new article is judged by TypeSafe's Jev judgment model against the reader's written interest profile and weighted topic rubric, so the articles that matter most surface first in a Priority view.

## Core Value

Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

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

### Active

- [ ] Integrate TypeSafe Jev via Spring AI community starter (`org.springaicommunity:spring-ai-starter-typesafe` 0.1.0), optional like Raindrop — app runs normally without an API key
- [ ] Reader can write and edit a free-text interest profile
- [ ] Reader can manage a topic rubric: add/remove topics, each with a description and a (possibly negative) weight
- [ ] Each newly ingested article is judged once by Jev (title + summary + feed name as state): a profile-interest `Score` plus a `Noul` per topic, in a single `systemOne` call
- [ ] Raw Jev outputs are stored per article; the blended interest score (profile score blended with Σ topic match × weight) is computed at query time
- [ ] Thumbs up/down on an article nudges the weights of the topics that article matched; ranking updates immediately with no new Jev calls
- [ ] "Priority" virtual feed in the feed tree: unread articles ordered by blended score desc (ties by date), unscored articles after, by date
- [ ] Interest score badge on articles in the article list and reading pane
- [ ] Graceful degradation: Jev failure/missing key/open circuit never fails ingest; unscored articles are backfilled by a background job
- [ ] One-time backfill that scores the existing unread backlog when the feature ships

### Out of Scope

- Re-scoring existing articles when the profile text or topic list changes — user chose new-articles-only; weight changes still apply instantly via query-time blend
- Using full article content or on-demand Readability extraction as Jev input — title + summary + feed is cheaper and available at ingest
- Using liked/disliked articles as in-context examples — feedback adjusts topic weights instead
- Sort-by-interest on every article list and hiding/dimming low-interest articles — Priority view + badge only for this milestone
- CONCERNS.md fixes (JSON Feed dates, SSRF DNS rebinding) and FeedPanel refactor — milestone kept focused on Jev ranking
- Jev advisors (self-refine, guardrails), RAG reranking, tool index — not relevant to feed ranking
- Multi-user profiles — single-user app

## Context

- Brownfield: codebase mapped in `.planning/codebase/` (STACK, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, INTEGRATIONS, CONCERNS).
- Reference: Spring blog "Spring AI TypeSafe: structured judgment" (2026-09-21). Jev is a judgment API, not a chat model: `TypeSafeClient.systemOne(state, Map<String, Question>)` with `Noul` (yes/no probability in [0,1]), `Choice`, and `Score` (continuous value over an ordered rubric, per-level probabilities, confidence). ~300ms per call, no streaming, one call per document. Configured via `spring.ai.typesafe.api-key` / `TYPESAFE_API_KEY`; auto-configures a `TypeSafeClient` bean.
- Jev state must be string/object/array/null — bare numbers/booleans produce 422s. Option/instruction text quality strongly affects confidence.
- Polling path: `FeedPollingService` upserts articles; scoring should hook after insert (only for genuinely new articles), ideally asynchronously so polling latency is unaffected.
- Existing precedent for optional external integrations: Raindrop (`RaindropApiClientImpl` with `@CircuitBreaker` + `@Retry` on the API-client bean, business validation in the service, `ignore-exceptions` for not-configured).
- Spring AI with Anthropic is already a dependency (chat only); BOM-managed versions for Spring AI — the TypeSafe community starter is not in the Spring AI BOM and needs an explicit version.

## Constraints

- **Tech stack**: Spring Boot 4.0.3, Java 25, Spring Data JDBC (not JPA), Flyway migrations (next is V6), Jackson 3.x (`tools.jackson.*`), React 19 + TanStack Query + Zustand — follow existing conventions in CLAUDE.md
- **Build**: Gradle Kotlin DSL only (never Maven), even though the reference article shows Maven coordinates
- **Resilience**: Jev calls wrapped with `@CircuitBreaker` (outer) + `@Retry` (inner) on a dedicated API-client bean, following the Raindrop pattern
- **Compatibility**: Spring AI TypeSafe 0.1.0 must work with the project's Spring AI / Spring Boot 4 versions — verify early
- **Performance**: Ingest must not block on Jev; polling latency and failure behavior unchanged when Jev is slow or down
- **Cost**: one Jev call per new article (plus one-time backlog backfill); no re-scoring loops
- **Deployment**: new secret `MYFEEDER_TYPESAFE_API_KEY` threaded through `deploy.sh` and Helm chart as optional
- **Pagination**: Priority view must use cursor pagination compatible with `PaginatedResponse` (score-based composite cursor)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Judge at ingest (once per new article) | Predictable cost, instant ranking | — Pending |
| Interest signals = written profile + weighted topic rubric + thumbs feedback | User wants explicit control plus lightweight feedback | — Pending |
| Weighted blend: profile `Score` + Σ(topic `Noul` × weight), single `systemOne` call | One call per article covers all signals; negative weights push articles down | — Pending |
| Store raw Jev outputs; blend at query time | Thumbs-driven weight nudges re-rank instantly with zero extra Jev calls | — Pending |
| Thumbs feedback adjusts topic weights (not in-context examples) | Deterministic and explainable | — Pending |
| Jev input = title + summary + feed name | Cheap, always available at ingest, no extra fetches | — Pending |
| Optional integration, degrade gracefully + background backfill | Ingest must never fail because of Jev | — Pending |
| Profile/topic edits apply to new articles only | Avoid re-scoring cost | — Pending |
| Priority view = unread by blended score, unscored after by date | Useful even while backfill is in progress | — Pending |
| One-time backfill of unread backlog at launch | Priority view useful immediately | — Pending |

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
*Last updated: 2026-09-22 after initialization*
