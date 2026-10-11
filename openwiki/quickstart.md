---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4 / React feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) that layers Jev-powered interest/priority scoring, engagement capture, and topic-gap suggestions on top of Raindrop.io export. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader, interest-scoring, jev]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-11T14:50:52.613Z
sources:
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-2c150224c03b91cd2e1fdd33
    resource: repo://docs/backlog.md
  - id: openwiki-source-b581f72a458dff736caee61c
    resource: repo://docs/initial-design.md
  - id: openwiki-source-8122e6795ddabaf3e36c6167
    resource: repo://src/main/resources/db/migration/V6__interest_scoring.sql
  - id: openwiki-source-1bf13317d5c3962a6e27f90d
    resource: repo://src/main/resources/db/migration/V7__engagement.sql
generated: { by: "openwiki/0.7.2", at: "2026-10-11T14:50:52.613Z" }
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and lets a single user read, star, organize (folders/boards), and export saved articles to Raindrop.io. Beyond polling and export, it now also *ranks* articles by inferred interest: a **Jev** judge (via the `spring-ai-starter-typesafe` client) scores unread articles against user-defined topics, reader actions (opens/stars/board adds/Raindrop saves) are captured as **engagement** that feeds back into learned topic weights, and a **topic gap-discovery** feature suggests new topics from engaged-but-uncovered articles. See `docs/initial-design.md` for the original product brief (predates scoring/engagement) and `docs/backlog.md` for the live TODO/bug list.

- **Backend**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j (circuit breaker + retry via Spring Cloud), Spring AI (Anthropic, chat-only, currently unused by any endpoint), and `org.springaicommunity:spring-ai-starter-typesafe` for Jev interest scoring (app owns the `TypeSafeClient` bean; version pinned via `typesafeVersion` in `build.gradle.kts` since it is not in the Spring AI BOM).
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose; Redis for `@Cacheable` (Raindrop collections). Schema evolves through Flyway migrations `V1`–`V7`, the latest two (`V6`, `V7`) adding the interest-scoring and engagement tables.
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, scoring executor, frontend build) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, and retention works | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand how articles get ranked by inferred interest (the Jev pipeline) | [Interest Scoring Workflow](workflows/interest-scoring.md) |
| Understand how reader actions become learned weights and topic suggestions | [Engagement, Learning & Suggestions](workflows/engagement-and-suggestions.md) |
| Understand the data model (Feed/Article/Folder/Board + interest/engagement tables) and API rules | [Domain Concepts](domain/concepts.md) |
| Understand the Jev (TypeSafe) scoring integration, its resilience config, and throttle levers | [Jev Scoring Integration](integrations/jev-scoring.md) |
| Understand the Raindrop.io export integration and its resilience pattern | [Raindrop Integration](integrations/raindrop.md) |
| Build, run, deploy, or debug production issues (incl. Jev/TypeSafe secrets and calibration tooling) | [Operations Runbook](operations/runbook.md) |
| Know what tests exist and how to run them, including offline-from-Jev test config | [Testing Guide](testing/guide.md) |

## Repository map (top level)

- `src/main/java/org/bartram/myfeeder/` — Spring Boot backend: `config/`, `model/`, `repository/`, `parser/`, `service/`, `integration/`, `event/`, `controller/`, `scheduler/` (see [Architecture Overview](architecture/overview.md) for the package breakdown, including the interest-scoring and engagement additions)
- `src/main/frontend/` — React SPA source; `npm run build` outputs to `src/main/resources/static/`
- `src/main/resources/db/migration/` — Flyway migrations `V1`–`V7`: core schema, folders/boards, article image/extracted-content, Raindrop token hardening, interest scoring (`V6`), and engagement/topic-suggestion-dismissal (`V7`) (see [Domain Concepts](domain/concepts.md))
- `src/test/java/...` — JUnit/Mockito/Testcontainers backend tests; `src/main/frontend/src/**/*.test.ts(x)` — Vitest frontend tests (see [Testing Guide](testing/guide.md))
- `helm/myfeeder/`, `Dockerfile`, `compose.yaml`, `deploy.sh` — deployment tooling (see [Operations Runbook](operations/runbook.md))
- `scripts/interest-calibration-replay.sh` — read-only SQL replay used to tune interest/engagement blend constants without a release (see [Interest Scoring Workflow](workflows/interest-scoring.md) and [Operations Runbook](operations/runbook.md))
- `docs/initial-design.md`, `docs/backlog.md` — the original product brief and the live TODO/bug list; both are historical/process references rather than current architecture docs. `.planning/` (PROJECT.md, ROADMAP.md, MILESTONES.md, phase notes under `milestones/`) is the historical phase-by-phase design and delivery record for features such as interest scoring, engagement, and topic suggestions — useful for *why* a design decision was made, not for current behavior.
- `CLAUDE.md` — the most detailed, actively-maintained engineering reference in this repo (build commands, gotchas, conventions, and the full Interest Ranking / Jev Scoring and Resilience sections); this wiki synthesizes and cross-links it rather than duplicating it wholesale. Treat `CLAUDE.md` as the fastest-changing source of truth for day-to-day gotchas.
- `AGENTS.md` — defines how coding agents (and this OpenWiki) are expected to use the generated `openwiki/` index: retrieve just-in-time rather than preloading, and never hand-edit generated OpenWiki pages — update source/docs and let OpenWiki regenerate instead.

## What's new beyond feed polling + Raindrop export

The original brief was "poll feeds, read/organize articles, export to Raindrop.io." The app has since grown a second major subsystem — interest-based prioritization — built in three layers:

1. **Interest scoring** (`workflows/interest-scoring.md`, `integrations/jev-scoring.md`): a per-user interest profile and a small set of weighted topics are judged against each eligible unread article by calling Jev (`JevApiClient`) through a dedicated scoring executor and sweep; each article gets an `ArticleScore` and per-topic breakdown.
2. **Engagement capture** (`workflows/engagement-and-suggestions.md`): opening, starring, boarding, or Raindrop-saving an article records an `ArticleEngagement` row; this blends into a learned adjustment on top of each topic's base weight (`InterestScoreQueries`'s learned/engagement CTEs), and thumbs feedback (`ArticleFeedback`) lets the user directly correct topic weights.
3. **Topic gap discovery** (`workflows/engagement-and-suggestions.md`): articles the reader engaged with but that no topic scores well against are surfaced as topic suggestions, which the user can dismiss or turn into a new topic.

All three layers are additive to the original feed/article/folder/board/Raindrop model; the base feed-lifecycle and Raindrop workflows are unchanged in shape.

## How this codebase evolved (git history highlights)

The project started from a full backend design spec and was implemented commit-by-commit in dependency order: config → models → migrations → repositories → parser → services (Feed, Article, Polling, Raindrop, Retention) → controllers → scheduler. Feature work then layered on: Raindrop.io collection picker, folders/boards, OPML import, per-user UI preferences (themes, font size, keyboard shortcuts), a `design-review-fixes` refactor (typed request DTOs, event-driven feed scheduling, pagination/backoff fixes), and — most recently — the v0.2.x/v0.3.x interest-scoring and engagement milestones: Jev-backed article scoring and priority ranking, reader engagement capture, learned topic weights blending thumbs feedback and engagement, and gap-discovery topic suggestions. Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change. `.planning/` holds the detailed phase-by-phase design notes and calibration decisions behind the interest-scoring/engagement work, for readers who need the rationale rather than just the current behavior.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — `docs/backlog.md` lists this as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key`, but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up. (This is unrelated to the Jev/TypeSafe integration, which is a separate Spring AI Community starter used only for interest scoring.)
- **Known open bugs** — `docs/backlog.md` "Bugs" section (e.g. `j`/`k` navigation list mismatch on Starred/Folder views) — tracked in backlog.md, not duplicated here since it changes frequently.
- **Engagement/interest calibration is still marked "revisit"** — per `CLAUDE.md`'s Interest Ranking section, the current engagement blend constants (open-weight/save-weight/cap) were kept on thin data via a documented fallback rule rather than fully tuned; see [Engagement, Learning & Suggestions](workflows/engagement-and-suggestions.md) and the Operations Runbook for the calibration replay tool.
