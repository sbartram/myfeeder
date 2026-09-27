---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4.0.8 backend package layout, Spring Data JDBC persistence, the outbound-fetch security posture (FeedUrlValidator SSRF guard, 10 MiB size cap), React 19 SPA build/embed, and Spring Boot 4/Jackson 3.x platform notes an agent must know before touching HTTP or JSON code.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend, security]
verified:
  - by: openwiki/0.6.0
    at: 2026-09-27T13:47:46.861Z
sources:
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-2837caa12b7e70d7e094eb88
    resource: repo://src/main/java/org/bartram/myfeeder/config/RestClientConfig.java
  - id: openwiki-source-cdc24fc3ca0c47ee6972a535
    resource: repo://src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-cef6cf34b0ee266bb62a613f
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java
  - id: openwiki-source-729f2600137cfa7e7eacc635
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropConfig.java
  - id: openwiki-source-3fc6a75fee36d9e5cebafb69
    resource: repo://src/main/java/org/bartram/myfeeder/parser/FeedParser.java
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-92b6e647240c8425fe97dcaa
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedFetcher.java
  - id: openwiki-source-0ca4313738bd1faeedf7586c
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
  - id: openwiki-source-c058c81a0fd47b404bea49ab
    resource: repo://src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java
  - id: openwiki-source-d15f43d25658ba6b84b0fd0e
    resource: repo://src/test/java/org/bartram/myfeeder/controller/FeedControllerTest.java
  - id: openwiki-source-5c7a1a26c395dd7ec6e128d1
    resource: repo://src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java
generated: { by: "openwiki/0.6.0", at: "2026-09-27T13:47:46.861Z" }
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`). Built on Spring Boot 4.0.8, Spring AI 2.0.1, and Spring Cloud 2025.1.3 (see `build.gradle.kts`); Java toolchain is 25.

```
config/       MyfeederProperties (myfeeder.* config), RestClientConfig (User-Agent customizer), SpaForwardController
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount
repository/   Spring Data JDBC repositories (one per model), @Query-based custom queries
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records, OpmlFeed/OpmlParseException
service/      FeedService, ArticleService, ArticleExtractionService, FeedPollingService, FeedFetcher, FeedUrlValidator, FolderService, BoardService, RetentionService, OpmlService, OpmlImportService
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig — see integrations/raindrop.md
event/        FeedSavedEvent, FeedDeletedEvent — after-commit scheduling signals
controller/   REST controllers + typed request records + PaginatedResponse + GlobalExceptionHandler
scheduler/    FeedPollingScheduler — dynamic per-feed polling with backoff
```

This is a classic layered architecture (controller → service → repository), with cross-cutting mechanisms worth understanding before changing anything:

- **Event-driven scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)).
- **Single fetch path** through `FeedFetcher` — used by feed subscribe/poll (`FeedService`, `FeedPollingService`) *and* by [`ArticleExtractionService`](#reader-view-articleextractionservice)'s reader view. Never issue a raw `RestClient` call to fetch remote content; going through `FeedFetcher` is what guarantees the SSRF guard, the size cap, and the `myfeeder` User-Agent are applied uniformly.
- **Outbound-fetch security posture** — see the dedicated section below.

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

## Configuration

`MyfeederProperties` (prefix `myfeeder`) binds three groups from `application.yaml`:
- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)

`spring.http.clients.connect-timeout` / `read-timeout` bound the auto-configured outbound `RestClient` used for every remote call (feed fetches, reader-view extraction, Raindrop) — see the platform notes below for why these keys, not the older singular form, are the ones that matter on this stack.

## Outbound-fetch security posture

Every outbound fetch of caller-influenced content (a subscribed feed URL, or an article's original page for reader view) goes through `FeedFetcher`, which enforces two protections before any bytes leave the JVM's control:

- **`FeedUrlValidator` (SSRF guard)** — called at the top of `FeedFetcher.fetch(...)`. It requires an `http`/`https` scheme, resolves the host, and rejects any resolved address that is loopback, link-local, RFC1918 site-local, carrier-grade NAT (`100.64.0.0/10`, RFC 6598 — not covered by `isSiteLocalAddress`), IPv6 unique-local (`fc00::/7`), any-local, or multicast. A rejection throws `IllegalArgumentException`, which `GlobalExceptionHandler` maps to HTTP 400. This stops a subscribed feed (or an article URL) from steering the server at internal targets such as the database, in-cluster services, or a cloud metadata endpoint.
  - **Known limitation — DNS rebinding / TOCTOU**: the validator resolves the host and checks the IP, but `RestClient` resolves the host again when it actually connects. A host whose DNS answer changes between those two lookups can pass validation yet connect to a private address; closing this gap would require pinning the connection to the validated IP (custom resolver/socket factory), which is not implemented. Redirects are also not re-validated per hop. Both limitations are accepted for the current single-user LAN deployment and should be revisited before this ever faces untrusted networks or multi-tenant use.
- **`FeedFetcher.DEFAULT_MAX_FEED_BYTES` (10 MiB cap)** — the response body is read with a bounded `readNBytes(maxFeedBytes + 1)`; exceeding the cap throws `FeedFetchException` (mapped to HTTP 422) rather than buffering an unbounded/streaming body that could exhaust pod heap.

Because both protections live inside `FeedFetcher` rather than in each caller, any new feature that needs to dereference a caller-supplied or remote-controlled URL should route through `FeedFetcher` (as `ArticleExtractionService` does) instead of building its own `RestClient` call.

## HTTP layer conventions

- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` responses: `NotFoundException`→404, `IllegalArgumentException`→400, `OpmlParseException`→400, `IllegalStateException`→409, `FeedParseException`/`FeedFetchException`→422, `RaindropNotConfiguredException`→503. Services should throw the specific exception type rather than have controllers catch/translate.
- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`) rather than raw `Map` bodies — this was a deliberate refactor (commit `5136a3e`) to get compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules that make this correct.
- API surface: `/api/feeds`, `/api/articles` (including `/api/articles/{id}/extracted-content`, served by `ArticleExtractionService`), `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`.

### Reader view: ArticleExtractionService

`ArticleExtractionService` (in `service/`) implements the reader view: given an article ID, it fetches the article's original page and extracts its readable content with Readability4J, so pages with poor styling or dark themes can be rendered inside the app's own reading pane. It deliberately reuses `FeedFetcher` for the page fetch rather than issuing its own `RestClient` call, so the fetch inherits the SSRF guard, the 10 MiB size cap, and the `myfeeder` User-Agent for free. Extracted content is cached on the `Article` row after first extraction (`articleRepository.saveExtractedContent`), so repeat requests skip the fetch/parse. Failure modes: `NotFoundException` (404) if the article doesn't exist, `IllegalArgumentException` (400) if the article has no URL, `FeedFetchException` (422) if the page fetch fails, and `FeedParseException` (422) if no readable content can be extracted.

## Spring Boot 4 / Jackson 3.x platform notes

Spring Boot 4 pulls in Jackson 3.x, which moved package roots in ways that matter when touching HTTP or JSON code in this repo:

- **Databind moved, annotations didn't**: `ObjectMapper`, `JsonNode`, `JsonMapper` and the rest of databind now live under `tools.jackson.databind.*` (see `FeedParser`, `RaindropService`, `IntegrationConfigController`), while the annotation package stayed at `com.fasterxml.jackson.annotation.*` (see `RaindropConfig`'s `@JsonIgnoreProperties`, and the `@JsonProperty` usages in `RaindropApiClientImpl`). Do not assume a single package rename — check which half of Jackson a class belongs to before importing.
- **Test slice annotations moved**: `@WebMvcTest` and equivalents now live under `org.springframework.boot.<slice>.test.autoconfigure.*` (e.g. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`), not the old consolidated `org.springframework.boot.test.autoconfigure.web.servlet` package. `JacksonAutoConfiguration` similarly moved to `org.springframework.boot.jackson.autoconfigure`.
- **`RestClientCustomizer` moved** to `org.springframework.boot.restclient` (see `RestClientConfig`).
- **Outbound RestClient timeouts** are configured via `spring.http.clients.connect-timeout` / `spring.http.clients.read-timeout` (plural `clients`), *not* the deprecated singular `spring.http.client.*` keys from Boot 3. This repo sets them in `application.yaml` and guards the binding — plus the resulting transport choice — with `HttpClientConfigurationTest`, which asserts both that `HttpClientSettings` picks up the configured durations and that the auto-configured `ClientHttpRequestFactory` is still `ReactorClientHttpRequestFactory`.
- **`reactor-netty-http` is now an explicit dependency** (`build.gradle.kts`) because Spring AI 2.0.1 no longer pulls it in transitively the way an earlier milestone did. Without this explicit dependency, Boot's auto-configuration would silently fall back to the JDK `HttpClient` transport for every outbound `RestClient` call (feed fetches, reader-view extraction, Raindrop) — a behavior change easy to miss since both transports satisfy the same interface. `HttpClientConfigurationTest.outboundTransportIsReactorNetty()` exists specifically to catch a regression here.

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
