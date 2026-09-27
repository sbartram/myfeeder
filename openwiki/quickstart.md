---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4.0.8 / React 19 feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) with reader-view content extraction and Raindrop.io export. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader, reader-view]
verified:
  - by: openwiki/0.6.0
    at: 2026-09-27T13:47:46.861Z
sources:
  - id: openwiki-source-a3c425b9b584dec963d8c626
    resource: repo://.planning/phases/01-dependency-upgrade/01-01-SUMMARY.md
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-92b6e647240c8425fe97dcaa
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedFetcher.java
  - id: openwiki-source-0ca4313738bd1faeedf7586c
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java
  - id: openwiki-source-51200a041d438c053e6983d3
    resource: repo://src/main/resources/db/migration/V5__article_extracted_content.sql
generated: { by: "openwiki/0.6.0", at: "2026-09-27T13:47:46.861Z" }
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and lets a single user read, star, organize (folders/boards), and export saved articles to Raindrop.io. It also offers a **reader view**: when an article's own feed content is thin or unstyled, myfeeder fetches the article's original page and extracts readable content for display in the app's own theme. See `docs/initial-design.md` for the original product brief.

- **Backend**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j (via Spring Cloud 2025.1.3), Spring AI 2.0.1 (Anthropic, chat-only, currently unused by any endpoint).
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources.
- **Reader view / content extraction**: `ArticleExtractionService` fetches an article's original page through `FeedFetcher` (reusing its SSRF guard and size cap), decodes it with jsoup, and extracts readable content with Readability4J; the result is cached on the `article.extracted_content` column (Flyway `V5`).
- **Outbound-fetch security**: `FeedUrlValidator` guards every caller-influenced fetch (subscribed feed URLs and reader-view page fetches) against SSRF — it requires http/https and rejects hosts resolving to loopback, link-local, RFC1918/site-local, any-local, or multicast addresses.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose; Redis for `@Cacheable` (Raindrop collections).
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, frontend build, SSRF posture) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, retention, and reader-view extraction work | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand the data model (Feed/Article/Folder/Board) and API rules | [Domain Concepts](domain/concepts.md) |
| Understand the Raindrop.io export integration and its resilience pattern | [Raindrop Integration](integrations/raindrop.md) |
| Build, run, deploy, or debug production issues | [Operations Runbook](operations/runbook.md) |
| Know what tests exist and how to run them | [Testing Guide](testing/guide.md) |

## Repository map (top level)

- `src/main/java/org/bartram/myfeeder/` — Spring Boot backend (see [Architecture Overview](architecture/overview.md) for package breakdown), including `service/FeedUrlValidator.java` (SSRF guard) and `service/ArticleExtractionService.java` (reader-view extraction via jsoup + Readability4J)
- `src/main/frontend/` — React SPA source; `npm run build` outputs to `src/main/resources/static/`
- `src/main/resources/db/migration/` — Flyway migrations `V1`–`V5`, including `V5__article_extracted_content.sql` for the reader-view content cache (see [Domain Concepts](domain/concepts.md))
- `src/test/java/...` — JUnit/Mockito/Testcontainers backend tests; `src/main/frontend/src/**/*.test.ts(x)` — Vitest frontend tests (see [Testing Guide](testing/guide.md))
- `helm/myfeeder/`, `Dockerfile`, `compose.yaml`, `deploy.sh` — deployment tooling (see [Operations Runbook](operations/runbook.md))
- `docs/initial-design.md`, `docs/backlog.md`, `docs/plans/2026-03-15-backend-implementation.md` — original product brief, live TODO/bug list, and the historical backend design plan that the codebase was built from commit-by-commit
- `CLAUDE.md` — the most detailed, actively-maintained engineering reference in this repo (build commands, gotchas, conventions); this wiki synthesizes and cross-links it rather than duplicating it wholesale. Treat `CLAUDE.md` as the fastest-changing source of truth for day-to-day gotchas.

## How this codebase evolved (git history highlights)

The project started from `docs/plans/2026-03-15-backend-implementation.md`, a full backend design spec, and was implemented commit-by-commit in dependency order: config → models → migrations → repositories → parser → services (Feed, Article, Polling, Raindrop, Retention) → controllers → scheduler. After the initial backend, feature work layered on: Raindrop.io collection picker, folders/boards, OPML import, per-user UI preferences (themes, font size, keyboard shortcuts), and a `design-review-fixes` branch (merged `d93dcca`) that refactored request DTOs from raw `Map` bodies to typed records, decoupled feed scheduling via Spring application events, and fixed pagination/backoff bugs. More recently, the reader-view feature added `ArticleExtractionService`, the `FeedUrlValidator` SSRF guard for all caller-influenced outbound fetches, and the `V5` migration caching extracted content. The most recent notable work is a Spring Boot dependency upgrade (4.0.3 → **4.0.8**, Spring AI to **2.0.1**, Spring Cloud to **2025.1.3**), which pinned the outbound `RestClient` transport to Reactor Netty via an explicit `reactor-netty-http` dependency and fixed previously-inert outbound HTTP timeouts by renaming `spring.http.client.*` to the non-deprecated `spring.http.clients.*` keys (see `.planning/phases/01-dependency-upgrade/01-01-SUMMARY.md`). Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — `docs/backlog.md` lists this as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key`, but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up.
- **Known open bugs** — `docs/backlog.md` "Bugs" section (charset mojibake on feeds without a charset header; `j`/`k` navigation list mismatch on Starred/Folder views) — tracked in backlog.md, not duplicated here since it changes frequently.
