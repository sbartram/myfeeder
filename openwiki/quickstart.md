---
type: Quickstart
title: myfeeder OpenWiki Quickstart
description: Entry point for the myfeeder codebase wiki — a Spring Boot 4 / React feed reader (Feedly-style RSS/Atom/JSON Feed aggregator) that now adds Jev-powered interest scoring/ranking, engagement capture, topic gap discovery, and Raindrop.io export. Explains what the app does, how the repo is organized, and links to architecture, workflows, domain model, integrations, operations, and testing docs.
tags: [quickstart, myfeeder, spring-boot, feed-reader, interest-scoring, jev, engagement, raindrop]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-08T14:57:22.664Z
sources:
  - id: openwiki-source-ef7629065e06131773b2ed05
    resource: repo://.claude/skills/interest-calibration/SKILL.md
  - id: openwiki-source-345c2e9224a6f02470bff299
    resource: repo://.claude/skills/release/SKILL.md
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-8122e6795ddabaf3e36c6167
    resource: repo://src/main/resources/db/migration/V6__interest_scoring.sql
  - id: openwiki-source-1bf13317d5c3962a6e27f90d
    resource: repo://src/main/resources/db/migration/V7__engagement.sql
generated: { by: "openwiki/0.9.0", at: "2026-11-08T14:57:22.664Z" }
---

# myfeeder Quickstart

**myfeeder** is a self-hosted feed reader modeled on Feedly: it subscribes to RSS/Atom/JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and lets a single user read, star, organize (folders/boards), and export saved articles to Raindrop.io. See `docs/initial-design.md` for the original product brief.

Since the last wiki baseline, myfeeder grew a second major subsystem on top of the feed reader: **Jev (TypeSafe)-powered interest scoring and ranking**. Every eligible unread article is judged against the user's interest profile/topics, blended with learned weights from thumbs feedback and captured engagement, and surfaced in a dedicated **Priority** view; the system also discovers **topic gaps** from engagement the user's topics don't cover yet.

- **Backend**: Spring Boot 4.0.8, Java 25, Spring Data JDBC (not JPA) + Flyway, Redis cache, Resilience4j (aspect-ordered breaker-then-retry), Spring AI (Anthropic chat starter present but unused by any endpoint), and `spring-ai-starter-typesafe` (Jev) with an app-owned `TypeSafeClient` bean.
- **Frontend**: React 19 + TypeScript SPA (`src/main/frontend/`), TanStack Query, Zustand, Vite; built and embedded into the Spring Boot jar's static resources. Adds a **Priority** route/view and an **Interests** dialog (profile, topics, learned weights, suggested topics) alongside the existing feed/folder/board UI.
- **Persistence**: PostgreSQL via Testcontainers (dev/test) or Docker Compose, with Flyway migrations `V1`–`V7`; Redis for `@Cacheable` (Raindrop collections).
- **Single-user deployment**: no auth/multi-tenancy — see `docs/initial-design.md` and the "single-user deployment" note in `FeedPollingScheduler`.

## Where to start

| If you want to... | Go to |
|---|---|
| Understand the runtime shape (packages, layers, frontend build, Priority view) | [Architecture Overview](architecture/overview.md) |
| Understand how feeds get polled, backoff, retention, and reader-view extraction work | [Feed Lifecycle Workflow](workflows/feed-lifecycle.md) |
| Understand the Jev-powered scoring pipeline, learned-weight blend, Priority ranking, and gap discovery | [Interest Scoring, Ranking & Gap Discovery](workflows/interest-scoring.md) |
| Understand how opens/saves are captured and how the UI reacts after them | [Engagement Capture & Post-Engagement Reactions](workflows/engagement-capture.md) |
| Understand the data model (Feed/Article/Folder/Board plus the interest/engagement schema) and API rules | [Domain Concepts](domain/concepts.md) |
| Understand the Jev (TypeSafe) client, its resilience config, and exception mapping | [Jev (TypeSafe) Scoring Integration](integrations/jev.md) |
| Understand the Raindrop.io export integration and its resilience pattern | [Raindrop Integration](integrations/raindrop.md) |
| Build, run, deploy, debug production issues, or tune/throttle interest scoring | [Operations Runbook](operations/runbook.md) |
| Know what tests exist (including scoring/engagement proofs) and how to run them | [Testing Guide](testing/guide.md) |

## Repository map (top level)

- `src/main/java/org/bartram/myfeeder/` — Spring Boot backend: `config/`, `controller/`, `event/`, `integration/`, `model/`, `parser/`, `repository/`, `scheduler/`, `service/` (see [Architecture Overview](architecture/overview.md) for the package breakdown, now including interest-scoring config/controllers/services)
- `src/main/frontend/` — React SPA source; `npm run build` outputs to `src/main/resources/static/`
- `src/main/resources/db/migration/` — Flyway migrations `V1`–`V7` (see [Domain Concepts](domain/concepts.md)); `V6` adds the interest-scoring schema and `V7` adds engagement capture + gap discovery
- `src/test/java/...` — JUnit/Mockito/Testcontainers backend tests; `src/main/frontend/src/**/*.test.ts(x)` — Vitest frontend tests (see [Testing Guide](testing/guide.md))
- `helm/myfeeder/`, `Dockerfile`, `compose.yaml`, `deploy.sh` — deployment tooling (see [Operations Runbook](operations/runbook.md))
- `docs/initial-design.md`, `docs/plans/` — original product brief and historical implementation plans; `.planning/` holds the phase-by-phase design notes for the interest-scoring/engagement/calibration arc (e.g. `.planning/phases/12-calibration-release/12-CALIBRATION.md`)
- `.claude/skills/release/SKILL.md` — the release pipeline (axion tag → bootJar → docker build/push → `deploy.sh` → rollout check)
- `.claude/skills/interest-calibration/SKILL.md` — how to tune interest-ranking constants (tier thresholds, profile points, engagement open/save weight and cap) with the read-only calibration replay
- `CLAUDE.md` — the most detailed, actively-maintained engineering reference in this repo (build commands, gotchas, conventions, and the full Interest Ranking/Jev Scoring/engagement behavior notes); this wiki synthesizes and cross-links it rather than duplicating it wholesale. Treat `CLAUDE.md` as the fastest-changing source of truth for day-to-day gotchas — check it before relying on any specific constant, threshold, or endpoint shape described in this wiki.

## How this codebase evolved (git history highlights)

The project started from a full backend design spec and was implemented commit-by-commit in dependency order: config → models → migrations → repositories → parser → services (Feed, Article, Polling, Raindrop, Retention) → controllers → scheduler. Early feature work layered on Raindrop.io's collection picker, folders/boards, OPML import, and per-user UI preferences (themes, font size, keyboard shortcuts), plus a `design-review-fixes` refactor that decoupled feed scheduling via Spring application events.

The most recent evolution (v0.2.x–v0.3.x) added a second major subsystem on top of the feed reader, in a deliberate arc:

1. **Interest scoring & ranking** (v0.2.x) — `V6__interest_scoring.sql` adds the profile/topic/score schema; a Jev (TypeSafe)-backed `ArticleScoringService` judges eligible unread articles on a dedicated executor/sweep; thumbs feedback and a learned-weight blend refine topic weights; a Priority view ranks articles by score.
2. **Engagement capture** (v0.3.0) — `V7__engagement.sql` adds `article_engagement`; opens and saves (star/board/Raindrop) are captured quietly alongside the user's real action, feed a second "engagement" term into the learned-weight blend, and drive a shared post-engagement UI reaction.
3. **Gap discovery** (v0.3.0, Phase 11) — `topic_suggestion_dismissal` plus `TopicSuggestionStore`/`TopicSuggestionService` surface engaged-but-uncovered articles as suggested topics in the Interests dialog.
4. **Calibration** (v0.3.1, Phase 12) — the `interest-calibration` skill and a read-only replay script tune tier thresholds and engagement weight/cap constants against real data before they're committed to `application.yaml`.

Recent commits favor targeted refactors with an explanatory commit message and a matching `CLAUDE.md` update — when changing a cross-cutting behavior, update `CLAUDE.md`'s relevant bullet in the same change.

## Backlog

- **Frontend component/theme deep-dive** — `src/main/frontend/src/components/*`, `themes.ts`, `useKeyboardShortcuts.ts` — deferred; [Architecture Overview](architecture/overview.md) covers structure and conventions at a summary level, not every component's props/behavior.
- **Dropbox / Google Drive export** — mentioned in the original product brief (`docs/initial-design.md`) as a planned integration; not implemented in source, so not documented as a working feature.
- **Spring AI / Anthropic chat** — declared as a dependency (`spring-ai-starter-model-anthropic`) in `build.gradle.kts` and requires `spring.ai.anthropic.api-key`, but no controller/service currently exposes a chat feature; flagged here so a future agent doesn't assume it's wired up. (Jev/TypeSafe is a separate Spring AI community starter and is fully wired up — see [Jev (TypeSafe) Scoring Integration](integrations/jev.md).)
- **Engagement weight/cap calibration revisit** — the v0.3.1 calibration run kept the v0.2.1 defaults (0.25 / 0.5 / 8) on thin data and flagged them "revisit"; see the calibration note under `.planning/phases/12-calibration-release/` and the `interest-calibration` skill before changing these constants.
