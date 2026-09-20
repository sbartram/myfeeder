---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4 backend package layout, Spring Data JDBC persistence, outbound-fetch/SSRF and reader-view extraction mechanisms, HTTP-layer conventions, and the React 19 SPA build/embed process.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend, security]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-20T12:59:40.431Z
sources:
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-d6d102291b1964678af254e9
    resource: repo://src/main/frontend/src/components/ReadingPane.tsx
  - id: openwiki-source-cdc24fc3ca0c47ee6972a535
    resource: repo://src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-92b6e647240c8425fe97dcaa
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedFetcher.java
  - id: openwiki-source-0ca4313738bd1faeedf7586c
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
generated: { by: "openwiki/0.5.2", at: "2026-09-20T12:59:40.431Z" }
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`).

```
config/       MyfeederProperties (myfeeder.* config), RestClientConfig (User-Agent customizer), SpaForwardController
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount
repository/   Spring Data JDBC repositories (one per model), @Query-based custom queries
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records, OpmlFeed/OpmlParseException
service/      FeedService, ArticleService, FeedPollingService, FeedFetcher, FeedUrlValidator, ArticleExtractionService,
              FolderService, BoardService, RetentionService, OpmlService, OpmlImportService
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig — see integrations/raindrop.md
event/        FeedSavedEvent, FeedDeletedEvent — after-commit scheduling signals
controller/   REST controllers + typed request records + PaginatedResponse + GlobalExceptionHandler
scheduler/    FeedPollingScheduler — dynamic per-feed polling with backoff
```

This is a classic layered architecture (controller → service → repository), with three cross-cutting mechanisms worth understanding before changing anything: the **event-driven scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)), the **single fetch path** through `FeedFetcher` (used by subscribe, poll, and reader-view extraction — never issue a raw `RestClient` call to fetch feed or article content), and the **SSRF guard** that `FeedFetcher` applies via `FeedUrlValidator` to every one of those fetches.

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

## Cross-cutting mechanisms

### Outbound fetch path and SSRF guard

`FeedFetcher` is the single place that issues outbound HTTP requests for feed documents *and* for reader-view article pages. Every call — feed subscribe, feed polling, and `ArticleExtractionService` extraction — goes through it, so callers automatically inherit:

- **Conditional requests**: `fetch(url, etag, lastModified)` sends `If-None-Match`/`If-Modified-Since` and returns `FetchResult.notModified304()` on a 304; any 4xx/5xx raises `FeedFetchException` (→ 422).
- **A 10 MiB body cap** (`DEFAULT_MAX_FEED_BYTES`): the response is read with `readNBytes(maxFeedBytes + 1)`, and exceeding the cap raises `FeedFetchException` (→ 422) instead of letting an unbounded body exhaust heap.
- **The `myfeeder/<version>` User-Agent** applied via `RestClientConfig`'s `RestClientCustomizer` on the auto-configured `RestClient.Builder` (some CDNs rate-limit the default JDK HttpClient User-Agent).
- **An SSRF guard**, `FeedUrlValidator`, invoked as the first step of every `fetch(...)` call. It parses the URL, requires an `http`/`https` scheme, resolves the host, and rejects the request (`IllegalArgumentException` → 400) if any resolved address is loopback, link-local, RFC 1918 site-local, any-local, multicast, carrier-grade NAT (`100.64.0.0/10`), the IPv4 limited-broadcast address, or an IPv6 unique-local (`fc00::/7`) address. This stops a subscribed feed URL or an article's outbound link from steering the server at internal targets (the database, in-cluster services, a cloud metadata endpoint). Two limitations are accepted for the current single-user LAN deployment rather than closed: **DNS rebinding/TOCTOU** — the validator resolves and checks the host once, but `RestClient` re-resolves when it actually connects, so a host whose DNS answer changes between the two lookups can pass validation and still reach a private address (closing this would require pinning the connection to the validated IP); and **redirects are not re-validated per hop**, so an allowed URL can redirect to a blocked one. Both should be revisited before this application faces untrusted networks.

Because the guard and the size cap live inside `FeedFetcher`, any new code path that needs to fetch a feed or a web page must call `FeedFetcher` rather than constructing its own `RestClient` call — doing otherwise silently loses both protections.

### Reader-view extraction

`ArticleExtractionService` implements the reader-view feature: given an article ID, it fetches the article's original page through `FeedFetcher` (inheriting the SSRF guard, 10 MiB cap, and User-Agent), decodes it (header charset if declared, else jsoup's BOM/meta-tag detection), and extracts the readable content with Readability4J. The extracted HTML is cached in `article.extracted_content` on first request (subsequent calls serve the cached copy) and is aged out together with `content` by `RetentionService`. Failure modes: `NotFoundException` (no such article, 404), `IllegalArgumentException` (article has no URL, 400), `FeedFetchException` (page fetch failed, 422 — including the SSRF/size-cap rejections raised inside `FeedFetcher`), and `FeedParseException` (Readability4J found no usable content, 422). See [Reader View Workflow](../workflows/reader-view.md) for the end-to-end request flow and the frontend's auto-enable/sanitization behavior.

## Configuration

`MyfeederProperties` (prefix `myfeeder`) binds three groups from `application.yaml`:
- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`, which also expires cached `extracted_content`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)

`spring.http.client.connect-timeout`/`read-timeout` (5s/30s) bound every outbound `RestClient` call made through `FeedFetcher`, including reader-view page fetches.

## HTTP layer conventions

- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` responses: `NotFoundException`→404, `IllegalArgumentException`→400 (also the status for `FeedUrlValidator` SSRF rejections), `OpmlParseException`→400, `IllegalStateException`→409, `FeedParseException`/`FeedFetchException`→422, `RaindropNotConfiguredException`→503. Services should throw the specific exception type rather than have controllers catch/translate.
- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`) rather than raw `Map` bodies — this was a deliberate refactor (commit `5136a3e`) to get compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules that make this correct.
- API surface: `/api/feeds`, `/api/articles` (including `GET /api/articles/{id}/extracted-content` for reader view), `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`.

## Frontend

`src/main/frontend/` is a React 19 + TypeScript SPA built with Vite.

- **Layout**: three resizable panels — feed tree (`FeedPanel`), article list (`ArticleList`/`BoardArticleList`), reading pane (`ReadingPane`), composed in `App.tsx` under `AppShell`.
- **Data layer**: `src/api/*.ts` are thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, version); `src/hooks/*.ts` wrap them in TanStack Query hooks (`useArticles`, `useFeeds`, `useFolders`, `useBoards`, `useOpml`). `App.tsx` composes route components (`FeedArticles`, `FolderArticles`, `StarredArticles`, `AllArticles`, `BoardArticles`) that each derive TanStack Query filters from Zustand preferences (sort order, hide-read).
- **State**: Zustand stores in `src/stores/` — `uiStore` (selection/focus/panel state) and `preferencesStore` (localStorage-persisted settings: theme, font sizes, sort order, hide-read). `preferencesStore` uses Zustand's `persist`; adding a field with a default does **not** retroactively apply to existing users (see Testing/Gotchas) — needs a `merge` function or version migration.
- **Reading pane and reader view**: `ReadingPane` fetches the selected article directly via `useArticle(id)` (`GET /api/articles/{id}`), not by scanning the paginated list query. It renders in one of two modes: normal feed content (`article.content`/`article.summary`) or reader view, which calls `useExtractedArticle(id, enabled)` against `GET /api/articles/{id}/extracted-content`. Reader view auto-enables when the selected article has neither `content` nor `summary`, and can otherwise be toggled manually per article via the "📖 Reader View"/"📖 Feed View" button (the manual choice resets to auto when the selection changes). Both content sources are sanitized with DOMPurify before being rendered via `dangerouslySetInnerHTML`; the reader-view path additionally passes `FORBID_TAGS: ['style']` and `FORBID_ATTR: ['style']` so publisher inline styling (e.g. dark page backgrounds) is stripped and the app's own theme applies instead. See [Reader View Workflow](../workflows/reader-view.md) for the full request/render sequence.
- **Keyboard shortcuts**: `useKeyboardShortcuts` hook implements vim-style navigation (`j`/`k`/`n`/`p`/`m`/`s`/`o`/`b`/`v`/`r`) plus `g`-prefixed chords (`ga`, `gs`, `gb`) and `+`/`-` font-size adjustment on the focused panel. It resolves the "current article" via `useArticle(selectedArticleId)` (direct fetch by ID) so single-article actions work even on views (Starred/Folder) whose visible list is queried differently from the list this hook receives — a known partial fix (see backlog bug on `j`/`k` navigation).
- **Themes**: 6 themes (3 dark/3 light) in `src/themes.ts`, applied via `useTheme`, persisted in `preferencesStore`.
- **Build/embed**: `npm run build` outputs to `src/main/resources/static/`; the Gradle `npmBuild` task wires this into `./gradlew build` so the SPA ships inside the Spring Boot jar. `SpaForwardController` (in `config/`) forwards non-API routes to the SPA's `index.html` for client-side routing. See [Operations Runbook](../operations/runbook.md) for the full build/deploy sequence — the frontend must be rebuilt with `clean` before every image build or a stale bundle ships.

## Infrastructure dependencies

- **PostgreSQL**: primary datastore, Flyway-migrated (`src/main/resources/db/migration/`), external at `pg.bartram.org` in production (not deployed by the Helm chart — see [Operations Runbook](../operations/runbook.md)).
- **Redis**: backs `@Cacheable` (currently only `RaindropService.listCollections`) via Spring Cache abstraction.
- **Docker**: required locally for both `./gradlew test` (Testcontainers-backed repository/integration tests) and `./gradlew bootRun` (Compose-managed Postgres/Redis). `./gradlew bootTestRun` runs the app itself against Testcontainers-managed services with no external Compose needed.
