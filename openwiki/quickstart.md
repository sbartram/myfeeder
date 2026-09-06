---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4 / React feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) with Raindrop.io export and reader-view content extraction. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader]
verified:
  - by: openwiki/0.5.0
    at: 2026-09-06T12:04:59.431Z
sources:
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-2c150224c03b91cd2e1fdd33
    resource: repo://docs/backlog.md
  - id: openwiki-source-b581f72a458dff736caee61c
    resource: repo://docs/initial-design.md
generated: { by: "openwiki/0.5.0", at: "2026-09-06T12:04:59.431Z" }
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and lets a single user read, star, organize (folders/boards), and export saved articles to Raindrop.io. It can also extract a clean "reader view" of an article's original page on demand. See `docs/initial-design.md` for the original product brief.

- **Backend**: Spring Boot 4.0.3, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j, Spring AI (Anthropic, chat-only, currently unused by any endpoint).
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose; Redis for `@Cacheable` (Raindrop collections).
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, frontend build, outbound-fetch hardening) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, and retention works | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand how a full-text "reader view" is fetched, extracted, cached, and sanitized | [Reader View / Content Extraction Workflow](workflows/reader-view-extraction.md) |
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
- `docs/initial-design.md`, `docs/backlog.md` — original product brief and the live TODO/bug list
- `CLAUDE.md` — the most detailed, actively-maintained engineering reference in this repo (build commands, gotchas, conventions); this wiki synthesizes and cross-links it rather than duplicating it wholesale. Treat `CLAUDE.md` as the fastest-changing source of truth for day-to-day gotchas.

## How this codebase evolved (recent highlights)

The backend was originally implemented commit-by-commit in dependency order (config → models → migrations → repositories → parser → services → controllers → scheduler). Feature work since then has layered on: Raindrop.io collection picker, folders/boards, OPML import, per-user UI preferences (themes, font size, keyboard shortcuts), a refactor decoupling feed scheduling via Spring application events (`FeedSavedEvent`/`FeedDeletedEvent`), and, most recently, **reader-view content extraction**: `GET /api/articles/{id}/extracted-content` fetches an article's original page through the same hardened `FeedFetcher` used for polling, extracts readable content with Readability4J (`ArticleExtractionService`), and caches the result in the new `article.extracted_content` column so it is aged out alongside `content` by `RetentionService`. The `ReadingPane` auto-enables this view when a feed item has no content/summary and otherwise offers a manual toggle; extracted HTML is sanitized with DOMPurify before rendering. Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — `docs/backlog.md` lists this as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key`, but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up.
- **Known open bugs and gaps** — `docs/backlog.md` "Bugs"/reader-related sections (e.g. `j`/`k` navigation list mismatch on Starred/Folder views; some article pages not extracting cleanly, such as missing images or unsuppressed embedded social links) — tracked in backlog.md, not duplicated here since it changes frequently.
