---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4 / React feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) with Raindrop.io export. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader]
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and lets a single user read, star, organize (folders/boards), and export saved articles to Raindrop.io. See `docs/initial-design.md` for the original product brief.

- **Backend**: Spring Boot 4.0.3, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j, Spring AI (Anthropic, chat-only, currently unused by any endpoint).
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose; Redis for `@Cacheable` (Raindrop collections).
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, frontend build) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, and retention works | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand the data model (Feed/Article/Folder/Board) and API rules | [Domain Concepts](domain/concepts.md) |
| Understand the Raindrop.io export integration and its resilience pattern | [Raindrop Integration](integrations/raindrop.md) |
| Build, run, deploy, or debug production issues | [Operations Runbook](operations/runbook.md) |
| Know what tests exist and how to run them | [Testing Guide](testing/guide.md) |

## Repository map (top level)

- `src/main/java/org/bartram/myfeeder/` — Spring Boot backend (see [Architecture Overview](architecture/overview.md) for package breakdown)
- `src/main/frontend/` — React SPA source; `npm run build` outputs to `src/main/resources/static/`
- `src/main/resources/db/migration/` — Flyway migrations `V1`–`V4` (see [Domain Concepts](domain/concepts.md))
- `src/test/java/...` — JUnit/Mockito/Testcontainers backend tests; `src/main/frontend/src/**/*.test.ts(x)` — Vitest frontend tests (see [Testing Guide](testing/guide.md))
- `helm/myfeeder/`, `Dockerfile`, `compose.yaml`, `deploy.sh` — deployment tooling (see [Operations Runbook](operations/runbook.md))
- `docs/initial-design.md`, `docs/backlog.md`, `docs/plans/2026-03-15-backend-implementation.md` — original product brief, live TODO/bug list, and the historical backend design plan that the codebase was built from commit-by-commit
- `CLAUDE.md` — the most detailed, actively-maintained engineering reference in this repo (build commands, gotchas, conventions); this wiki synthesizes and cross-links it rather than duplicating it wholesale. Treat `CLAUDE.md` as the fastest-changing source of truth for day-to-day gotchas.

## How this codebase evolved (git history highlights)

The project started from `docs/plans/2026-03-15-backend-implementation.md`, a full backend design spec, and was implemented commit-by-commit in dependency order: config → models → migrations → repositories → parser → services (Feed, Article, Polling, Raindrop, Retention) → controllers → scheduler. After the initial backend, feature work layered on: Raindrop.io collection picker, folders/boards, OPML import, per-user UI preferences (themes, font size, keyboard shortcuts), and a `design-review-fixes` branch (merged `d93dcca`) that refactored request DTOs from raw `Map` bodies to typed records, decoupled feed scheduling via Spring application events, and fixed pagination/backoff bugs. Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — `docs/backlog.md` lists this as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key`, but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up.
- **Known open bugs** — `docs/backlog.md` "Bugs" section (charset mojibake on feeds without a charset header; `j`/`k` navigation list mismatch on Starred/Folder views) — tracked in backlog.md, not duplicated here since it changes frequently.
