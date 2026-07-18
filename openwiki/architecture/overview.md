---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4 backend package layout, Spring Data JDBC persistence, React 19 SPA frontend build/embed process, and how backend and frontend fit together in one deployable jar.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend]
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`).

```
config/       MyfeederProperties (myfeeder.* config), RestClientConfig (User-Agent customizer), SpaForwardController
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount
repository/   Spring Data JDBC repositories (one per model), @Query-based custom queries
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records, OpmlFeed/OpmlParseException
service/      FeedService, ArticleService, FeedPollingService, FeedFetcher, FolderService, BoardService, RetentionService, OpmlService, OpmlImportService
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig — see integrations/raindrop.md
event/        FeedSavedEvent, FeedDeletedEvent — after-commit scheduling signals
controller/   REST controllers + typed request records + PaginatedResponse + GlobalExceptionHandler
scheduler/    FeedPollingScheduler — dynamic per-feed polling with backoff
```

This is a classic layered architecture (controller → service → repository), with two cross-cutting mechanisms worth understanding before changing anything: the **event-driven scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)) and the **single fetch path** through `FeedFetcher` (used by both subscribe and poll — never issue a raw `RestClient` call to fetch feed content).

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

## Configuration

`MyfeederProperties` (prefix `myfeeder`) binds three groups from `application.yaml`:
- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)

## HTTP layer conventions

- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` responses: `NotFoundException`→404, `IllegalArgumentException`→400, `OpmlParseException`→400, `IllegalStateException`→409, `FeedParseException`/`FeedFetchException`→422, `RaindropNotConfiguredException`→503. Services should throw the specific exception type rather than have controllers catch/translate.
- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`) rather than raw `Map` bodies — this was a deliberate refactor (commit `5136a3e`) to get compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules that make this correct.
- API surface: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`.

## Frontend

`src/main/frontend/` is a React 19 + TypeScript SPA built with Vite.

- **Layout**: three resizable panels — feed tree (`FeedPanel`), article list (`ArticleList`/`BoardArticleList`), reading pane (`ReadingPane`), composed in `App.tsx` under `AppShell`.
- **Data layer**: `src/api/*.ts` are thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, version); `src/hooks/*.ts` wrap them in TanStack Query hooks (`useArticles`, `useFeeds`, `useFolders`, `useBoards`, `useOpml`). `App.tsx` composes route components (`FeedArticles`, `FolderArticles`, `StarredArticles`, `AllArticles`, `BoardArticles`) that each derive TanStack Query filters from Zustand preferences (sort order, hide-read).
- **State**: Zustand stores in `src/stores/` — `uiStore` (selection/focus/panel state) and `preferencesStore` (localStorage-persisted settings: theme, font sizes, sort order, hide-read). `preferencesStore` uses Zustand's `persist`; adding a field with a default does **not** retroactively apply to existing users (see Testing/Gotchas) — needs a `merge` function or version migration.
- **Keyboard shortcuts**: `useKeyboardShortcuts` hook implements vim-style navigation (`j`/`k`/`n`/`p`/`m`/`s`/`o`/`b`/`v`/`r`) plus `g`-prefixed chords (`ga`, `gs`, `gb`) and `+`/`-` font-size adjustment on the focused panel. It resolves the "current article" via `useArticle(selectedArticleId)` (direct fetch by ID) so single-article actions work even on views (Starred/Folder) whose visible list is queried differently from the list this hook receives — a known partial fix (see backlog bug on `j`/`k` navigation).
- **Themes**: 6 themes (3 dark/3 light) in `src/themes.ts`, applied via `useTheme`, persisted in `preferencesStore`.
- **Build/embed**: `npm run build` outputs to `src/main/resources/static/`; the Gradle `npmBuild` task wires this into `./gradlew build` so the SPA ships inside the Spring Boot jar. `SpaForwardController` (in `config/`) forwards non-API routes to the SPA's `index.html` for client-side routing. See [Operations Runbook](../operations/runbook.md) for the full build/deploy sequence — the frontend must be rebuilt with `clean` before every image build or a stale bundle ships.

## Infrastructure dependencies

- **PostgreSQL**: primary datastore, Flyway-migrated (`src/main/resources/db/migration/`), external at `pg.bartram.org` in production (not deployed by the Helm chart — see [Operations Runbook](../operations/runbook.md)).
- **Redis**: backs `@Cacheable` (currently only `RaindropService.listCollections`) via Spring Cache abstraction.
- **Docker**: required locally for both `./gradlew test` (Testcontainers-backed repository/integration tests) and `./gradlew bootRun` (Compose-managed Postgres/Redis). `./gradlew bootTestRun` runs the app itself against Testcontainers-managed services with no external Compose needed.
