---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4 / React feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) with Raindrop.io export and Jev-powered interest ranking. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader, interest-ranking, jev]
verified:
  - by: openwiki/0.7.0
    at: 2026-10-04T13:52:44.431Z
sources:
  - id: openwiki-source-ecc9d3feae0bae0f4276a593
    resource: repo://.planning/ROADMAP.md
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-8122e6795ddabaf3e36c6167
    resource: repo://src/main/resources/db/migration/V6__interest_scoring.sql
  - id: openwiki-source-1bf13317d5c3962a6e27f90d
    resource: repo://src/main/resources/db/migration/V7__engagement.sql
generated: { by: "openwiki/0.7.0", at: "2026-10-04T13:52:44.431Z" }
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and lets a single user read, star, organize (folders/boards), and export saved articles to Raindrop.io. Since v0.2.1/v0.3.0 it also ranks unread articles by learned interest, using an AI judge (Jev) scored against a user-defined rubric and adjusted by engagement and thumbs feedback. See `docs/initial-design.md` for the original product brief.

- **Backend**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j, Spring AI (Anthropic, chat-only, declared on the classpath but **unused** by any endpoint). The actual AI-scoring integration in production use is the Jev/TypeSafe client (`org.springaicommunity:spring-ai-starter-typesafe`), owned and wired by the app itself (not Spring AI's auto-configuration) — see [Jev Integration](integrations/jev.md).
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose; Redis for `@Cacheable` (Raindrop collections).
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, frontend build) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, and retention works | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand how articles get AI-scored, ranked, and re-ranked by engagement/feedback | [Interest Ranking & Engagement Learning Workflow](workflows/interest-scoring.md) |
| Understand the data model (Feed/Article/Folder/Board plus the interest/engagement schema) and API rules | [Domain Concepts](domain/concepts.md) |
| Understand the Jev/TypeSafe AI-judging integration behind interest scoring | [Jev Integration](integrations/jev.md) |
| Understand the Raindrop.io export integration and its resilience pattern | [Raindrop Integration](integrations/raindrop.md) |
| Build, run, deploy, or debug production issues (including Jev throttling/calibration) | [Operations Runbook](operations/runbook.md) |
| Know what tests exist and how to run them | [Testing Guide](testing/guide.md) |

## Repository map (top level)

- `src/main/java/org/bartram/myfeeder/` — Spring Boot backend (see [Architecture Overview](architecture/overview.md) for package breakdown); notably `service/`, `integration/`, `controller/`, and `config/` now carry parallel feed/article code and interest/Jev code side by side:
  - `config/` — `MyfeederProperties` (self-validates engagement constants), `RestClientConfig`, `SpaForwardController`, `TypeSafeConfig` (app-owned `TypeSafeClient` bean, jev retry interval), `InterestScoringConfig` (jev-score executor), `JevEventLogging`
  - `service/` — original feed/article/folder/board/retention/OPML services plus the interest package: `InterestService`, `InterestStatusService`, `InterestPreviewService`, `InterestRescoreService`, `ArticleScoringService`, `ScoringQueue`, `PriorityService`, `ArticleFeedbackService`, `TopicSuggestionService`, and related support types
  - `integration/` — `RaindropService`/`RaindropApiClient(Impl)` and `JevApiClient`/`JevApiClientImpl` (mirrored client-split pattern, each with its own Resilience4j `@CircuitBreaker`/`@Retry`)
  - `controller/` — feed/article/folder/board/integration/OPML/version controllers plus `Interest`, `InterestStatus`, `InterestPreview`, `InterestRescore`, `TopicSuggestion` controllers
- `src/main/frontend/` — React SPA source; `npm run build` outputs to `src/main/resources/static/`
- `src/main/resources/db/migration/` — Flyway migrations `V1`–`V7`: `V1`–`V5` cover feeds/articles/folders/boards/extracted content; `V6__interest_scoring.sql` and `V7__engagement.sql` add the interest-ranking and engagement-learning schema (see [Domain Concepts](domain/concepts.md))
- `src/test/java/...` — JUnit/Mockito/Testcontainers backend tests; `src/main/frontend/src/**/*.test.ts(x)` — Vitest frontend tests (see [Testing Guide](testing/guide.md))
- `helm/myfeeder/`, `Dockerfile`, `compose.yaml`, `deploy.sh` — deployment tooling (see [Operations Runbook](operations/runbook.md))
- `docs/initial-design.md`, `docs/backlog.md`, `docs/plans/2026-03-15-backend-implementation.md` — original product brief, live TODO/bug list, and the historical backend design plan that the codebase was built from commit-by-commit
- `.planning/ROADMAP.md` — milestone tracker; see "Recent evolution" below
- `CLAUDE.md` — the most detailed, actively-maintained engineering reference in this repo (build commands, gotchas, conventions, including the full Interest Ranking rules); this wiki synthesizes and cross-links it rather than duplicating it wholesale. Treat `CLAUDE.md` as the fastest-changing source of truth for day-to-day gotchas.

## Recent evolution: Interest Ranking and Engagement Learning

The two most recent major milestones in `.planning/ROADMAP.md` both shipped on top of the original feed-reader base and are the biggest change to the app since its initial build:

- **v0.2.1 Interest Ranking** (Phases 1–7, shipped 2026-09-29) — added the Jev/TypeSafe AI-judging client, an interest profile/topic rubric editor, a scoring pipeline that judges new articles against the rubric, a blended Priority view, and thumbs-up/down feedback that adjusts learned topic weights.
- **v0.3.0 Engagement Learning** (Phases 8–12, shipped 2026-10-02) — added engagement capture (opens, stars, board adds, Raindrop saves), a second learned term derived from engagement in the ranking query, explainable "Why N?" breakdowns in the UI, and gap discovery (topic suggestions for rubric blind spots).

These are documented end-to-end in [Interest Ranking & Engagement Learning Workflow](workflows/interest-scoring.md) and [Jev Integration](integrations/jev.md); the underlying schema is in [Domain Concepts](domain/concepts.md).

## How this codebase evolved (git history highlights)

The project started from `docs/plans/2026-03-15-backend-implementation.md`, a full backend design spec, and was implemented commit-by-commit in dependency order: config → models → migrations → repositories → parser → services (Feed, Article, Polling, Raindrop, Retention) → controllers → scheduler. After the initial backend, feature work layered on: Raindrop.io collection picker, folders/boards, OPML import, per-user UI preferences (themes, font size, keyboard shortcuts), and a `design-review-fixes` branch (merged `d93dcca`) that refactored request DTOs from raw `Map` bodies to typed records, decoupled feed scheduling via Spring application events, and fixed pagination/backoff bugs. Most recently, the v0.2.1 and v0.3.0 milestones added the full interest-ranking and engagement-learning system described above. Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — `docs/backlog.md` lists this as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key` (via `MYFEEDER_ANTHROPIC_API_KEY` at deploy time), but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up. The AI integration that *is* wired up and in production use is Jev scoring via the TypeSafe client — see [Jev Integration](integrations/jev.md).
- **Known open bugs** — `docs/backlog.md` "Bugs" section (`j`/`k` navigation list mismatch on Starred/Folder views, plus assorted reader/feed-rendering issues) — tracked in backlog.md, not duplicated here since it changes frequently.
