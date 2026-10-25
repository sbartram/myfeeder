---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4 / React feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) that scores every article's personal interest via a Jev (TypeSafe) rubric, learns from thumbs feedback and engagement, surfaces topic-suggestion gaps, and exports saved articles to Raindrop.io. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader, interest-scoring, jev]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-25T13:07:33.493Z
sources:
  - id: openwiki-source-d6639519234042199a7ed3b0
    resource: repo://.planning/MILESTONES.md
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
generated: { by: "openwiki/0.7.2", at: "2026-10-25T13:07:33.493Z" }
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, and stores articles in PostgreSQL for a single user to read, star, and organize (folders/boards). Past v0.2.x/v0.3.x it is no longer just a plain reader: every ingested article is judged against a personal interest profile and weighted topic rubric by an external **Jev (TypeSafe)** scoring service, ranked in a dedicated Priority view, tuned by thumbs feedback and by implicit engagement (opens, stars, board adds, Raindrop saves), and mined for topic-suggestion gaps — engaged articles no topic currently covers. Saved articles can still be exported to Raindrop.io. See `docs/initial-design.md` for the original product brief (written before interest scoring existed).

- **Backend**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j, Spring AI (Anthropic chat starter present but unused by any endpoint; `spring-ai-starter-typesafe` powers Jev scoring).
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose; Redis for `@Cacheable` (Raindrop collections).
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.
- **Two pipelines**: feed ingestion (subscribe → poll → dedup/store → optional reader-view extraction) and interest ranking (score via Jev → blend with learned thumbs/engagement weights at query time → Priority view and topic-suggestion gap discovery).

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, frontend build) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, retention, and reader-view extraction work | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand the content data model (Feed/Article/Folder/Board) and API rules | [Domain Concepts](domain/concepts.md) |
| Understand the interest-ranking data model (profile, topics, scores, feedback, engagement, suggestions) | [Interest Scoring Domain Model](domain/interest-scoring.md) |
| Understand end-to-end interest ranking: Jev scoring, learned weights, engagement, gap discovery | [Interest Ranking Workflow](workflows/interest-ranking.md) |
| Understand the Raindrop.io export integration and its resilience pattern | [Raindrop Integration](integrations/raindrop.md) |
| Understand the Jev (TypeSafe) client, single-retry-layer design, and degrade path | [Jev (TypeSafe) Scoring Integration](integrations/jev-typesafe.md) |
| Build, run, deploy, debug production issues, or tune/throttle interest scoring | [Operations Runbook](operations/runbook.md) |
| Know what tests exist and how to run them per subsystem | [Testing Guide](testing/guide.md) |

## Repository map (top level)

- `src/main/java/org/bartram/myfeeder/` — Spring Boot backend; `config/`, `model/`, `repository/`, `parser/`, `service/`, `integration/`, `event/`, `controller/`, `scheduler/` packages now include interest-scoring, engagement, and Jev/Raindrop code alongside the original feed/article/folder/board code (see [Architecture Overview](architecture/overview.md) for the full breakdown)
- `src/main/frontend/` — React SPA source; `npm run build` outputs to `src/main/resources/static/`
- `src/main/resources/db/migration/` — Flyway migrations `V1`–`V7`: `V1`–`V5` build the core content schema, `V6__interest_scoring.sql` adds the interest-ranking tables, `V7__engagement.sql` adds engagement capture and topic-suggestion dismissals (see [Domain Concepts](domain/concepts.md) and [Interest Scoring Domain Model](domain/interest-scoring.md))
- `src/test/java/...` — JUnit/Mockito/Testcontainers backend tests; `src/main/frontend/src/**/*.test.ts(x)` — Vitest frontend tests (see [Testing Guide](testing/guide.md))
- `helm/myfeeder/`, `Dockerfile`, `compose.yaml`, `deploy.sh` — deployment tooling (see [Operations Runbook](operations/runbook.md))
- `docs/initial-design.md`, `docs/backlog.md` — original product brief and live TODO/bug list
- `.planning/` — GSD-style planning history: `MILESTONES.md` and `ROADMAP.md` summarize shipped work, `milestones/v0.2.1-phases/` and `milestones/v0.3.0-phases/` hold the per-phase design/verification notes behind the Jev/interest-scoring and engagement/suggestions rollouts referenced below
- `CLAUDE.md` and `AGENTS.md` — two different agent-facing references, kept separate on purpose (see below)

### CLAUDE.md vs AGENTS.md

Both files exist in this repo and serve different audiences — read the one that matches your question rather than expecting either to duplicate the other:

- **`CLAUDE.md`** is the fastest-changing, most detailed day-to-day engineering reference: build/run commands, the full package structure, feature-level behavior notes (feed scheduling, engagement capture, interest ranking, Jev resilience), Spring Boot 4/Jackson 3 migration notes, deployment steps, and an accumulated Gotchas list. Read it when you need an authoritative, current answer about *how this codebase works or is operated* — this wiki synthesizes and cross-links it rather than duplicating it wholesale.
- **`AGENTS.md`** is the OpenWiki/Beads/CodeGraph *usage contract* for coding agents working in this repo: when to use OpenWiki retrieval vs. reading `openwiki/quickstart.md` directly, when to reach for CodeGraph before grep, and the Beads issue-tracking workflow/session-close protocol. Read it when you need to know *how to use the repo's agent tooling*, not what the application does.

## How this codebase evolved (git history highlights)

The project started from a full backend design spec (`docs/plans/2026-03-15-backend-implementation.md`, historical) and was implemented commit-by-commit in dependency order: config → models → migrations → repositories → parser → services (Feed, Article, Polling, Raindrop, Retention) → controllers → scheduler. After the initial backend, feature work layered on the original feed-reader surface: Raindrop.io collection picker, folders/boards, OPML import, per-user UI preferences (themes, font size, keyboard shortcuts), and a `design-review-fixes` branch that refactored request DTOs from raw `Map` bodies to typed records, decoupled feed scheduling via Spring application events, and fixed pagination/backoff bugs.

Two further GSD-tracked milestones (see `.planning/MILESTONES.md`, `.planning/ROADMAP.md`) then rebuilt the app around interest scoring:

- **v0.2.x — Interest Ranking** (`.planning/milestones/v0.2.1-phases/`, phases 01–07; shipped as v0.1.24 → v0.2.0 → v0.2.1): upgraded to Spring Boot 4.0.8/Spring AI 2.0.1/Spring Cloud 2025.1.3; introduced the app-owned `TypeSafeClient` Jev bean behind a single Resilience4j retry layer and circuit breaker; added the V6 interest schema (profile, topics, scores, feedback) and Interests dialog with a weighted topic rubric; built the background scoring queue and sweep; added the query-time blend CTE powering the Priority view, tier badges, and "Why N?" breakdowns; and closed with a production rollout and tier calibration.
- **v0.3.x — Engagement Learning** (`.planning/milestones/v0.3.0-phases/`, phases 08–12; shipped as v0.3.0 → v0.3.1): added engagement capture (V7 schema — opens, stars, board adds, Raindrop saves recorded outside any transaction); folded engagement into the learned-weight blend as a small, capped, thumbs-overridable implicit up-vote; made the thumbs/engagement split explainable end-to-end in the UI; added topic-suggestion gap discovery (engaged-but-uncovered articles surfaced for a new topic or dismissal); and closed with a calibration replay that validated (and, on thin data, deliberately kept) the shipped engagement constants.

Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — `docs/backlog.md` lists this as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key`, but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up. (This is unrelated to the Jev/TypeSafe integration, which is a separate Spring AI community starter used solely for interest scoring — see [Jev (TypeSafe) Scoring Integration](integrations/jev-typesafe.md).)
- **Engagement constants revisit** — `application.yaml`'s open/save-weight and cap (0.25 / 0.5 / 8) were kept on thin production data by the v0.3.1 calibration's documented fallback rather than tuned from a full sample; see `.planning/milestones/v0.3.0-phases/12-calibration-release/` and [Operations Runbook](operations/runbook.md) for the replay/retune procedure.
- **Known open bugs** — `docs/backlog.md` "Bugs" section (charset mojibake on feeds without a charset header; `j`/`k` navigation list mismatch on Starred/Folder views) — tracked in backlog.md, not duplicated here since it changes frequently.
