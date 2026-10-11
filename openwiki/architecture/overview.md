---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4 backend package layout (feed/board core plus interest-scoring and engagement additions), Spring Data JDBC persistence, HTTP layer conventions (GlobalExceptionHandler, RestClient/timeout config, Resilience4j), the scoring executor, and the React SPA frontend build/embed.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend, resilience4j, jackson3]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-11T14:50:52.613Z
sources:
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-0386155cb694082a3e8ad543
    resource: repo://src/main/frontend/src/App.tsx
  - id: openwiki-source-d6d102291b1964678af254e9
    resource: repo://src/main/frontend/src/components/ReadingPane.tsx
  - id: openwiki-source-d56d2d8714fc1dd717c3eb7d
    resource: repo://src/main/frontend/src/hooks/engagementReaction.ts
  - id: openwiki-source-a71f0f447f6fdcad4d39e4c1
    resource: repo://src/main/frontend/src/hooks/useEngagement.ts
  - id: openwiki-source-2b59042c287fbfa60bd5ed68
    resource: repo://src/main/frontend/src/hooks/useInterest.ts
  - id: openwiki-source-488238bd6a4828fc54072530
    resource: repo://src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - id: openwiki-source-2837caa12b7e70d7e094eb88
    resource: repo://src/main/java/org/bartram/myfeeder/config/RestClientConfig.java
  - id: openwiki-source-068b4c54848ec0b7c44aa265
    resource: repo://src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-e8441d910270910ebcaa60e2
    resource: repo://src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
  - id: openwiki-source-bfd9e6b64553ce72d0555fce
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - id: openwiki-source-cef6cf34b0ee266bb62a613f
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java
generated: { by: "openwiki/0.7.2", at: "2026-10-11T14:50:52.613Z" }
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`).

```
config/       MyfeederProperties (myfeeder.* config incl. interest/engagement validation), RestClientConfig (User-Agent customizer),
              SpaForwardController, TypeSafeConfig (app-owned TypeSafeClient bean, jev retry interval),
              InterestScoringConfig (jev-score executor bean), JevEventLogging (jev retry/breaker log lines)
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount,
              InterestProfile, InterestTopic, InterestBreakdown, ArticleFeedback, EngagementKind, SuggestionDismissalReason
repository/   Spring Data JDBC repositories (one per model) + ArticleScoreStore, ArticleFeedbackStore, ArticleEngagementStore,
              InterestScoreQueries (blend/learned CTEs), TopicSuggestionStore
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records, OpmlFeed/OpmlParseException
service/      FeedService, ArticleService, FeedPollingService, FeedFetcher, FolderService, BoardService, RetentionService,
              OpmlService, OpmlImportService, ArticleExtractionService; interest/*: InterestService, InterestStatusService,
              InterestPreviewService, InterestRescoreService, ArticleScoringService, ScoringQueue, InterestScoringListener,
              PriorityService, ArticleFeedbackService, TopicSuggestionService
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig — see integrations/raindrop.md;
              JevApiClient(Impl), JevJudgment, JevNotConfiguredException — see integrations/jev-scoring.md
event/        FeedSavedEvent, FeedDeletedEvent (after-commit scheduling signals), ArticlesIngestedEvent (scoring trigger)
controller/   Feed/Article/Folder/Board/IntegrationConfig/Opml/Version controllers, Interest/InterestStatus/InterestPreview/
              InterestRescore/TopicSuggestion controllers, typed request records, PaginatedResponse, PriorityPage,
              GlobalExceptionHandler
scheduler/    FeedPollingScheduler (dynamic per-feed polling with backoff), InterestScoringSweep (jev scoring sweep)
```

This is a classic layered architecture (controller → service → repository), with cross-cutting mechanisms worth understanding before changing anything: the **event-driven scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)), the **single fetch path** through `FeedFetcher` (used by both subscribe and poll — never issue a raw `RestClient` call to fetch feed content), and the **interest-scoring pipeline** (`ArticlesIngestedEvent` → `ScoringQueue` → `interestScoringExecutor` → `JevApiClient`, plus the periodic `InterestScoringSweep`) detailed in [Interest Scoring Workflow](../workflows/interest-scoring.md). Engagement capture and topic-gap discovery are detailed in [Engagement and Suggestions Workflow](../workflows/engagement-and-suggestions.md).

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

## Configuration

`MyfeederProperties` (prefix `myfeeder`, implements `Validator` so invalid combinations fail startup with fixed-text codes) binds several groups from `application.yaml`:
- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)
- `myfeeder.interest.*` — `concurrency`, `queue-capacity`, `sweep-delay`, `sweep-initial-delay`, `sweep-batch-size`, `window-days`, `blend.tiers.*`, `blend.engagement.open-weight`/`save-weight`/`cap`, `suggestions.near-miss` — validated as `ENGAGEMENT_INVALID`/`SUGGESTIONS_INVALID` on bad values; drives [Interest Scoring Workflow](../workflows/interest-scoring.md) and [Engagement and Suggestions Workflow](../workflows/engagement-and-suggestions.md)
- `spring.ai.typesafe.*` — `api-key` (env `MYFEEDER_TYPESAFE_API_KEY`), `base-url`, `model`, `timeout`, `retry.max-retries: 0` — bound by the TypeSafe starter's `TypeSafeProperties`; see [Jev Scoring Integration](../integrations/jev-scoring.md)

## HTTP layer conventions

### GlobalExceptionHandler mapping

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain and integration exceptions to `ProblemDetail` responses. Services should throw the specific exception type rather than have controllers catch/translate:

| Exception | Status | Notes |
|---|---|---|
| `NotFoundException` | 404 | missing path entity |
| `IllegalArgumentException` | 400 | generic bad request |
| `OpmlParseException` | 400 | XML/OPML parse failure |
| `IllegalStateException` | 409 | configuration/conflict error |
| `FeedParseException` | 422 | feed body could not be parsed |
| `FeedFetchException` | 422 | remote feed server returned an HTTP error |
| `RaindropNotConfiguredException` | 503 | no Raindrop API token configured |
| `JevNotConfiguredException` | 503 | no TypeSafe/Jev API key configured |
| `CallNotPermittedException` (Resilience4j) | 503 | the `jev` circuit breaker is OPEN |
| `TypeSafeBadRequestException` / `TypeSafeUnprocessableEntityException` | 422 | Jev rejected the request |
| other `TypeSafeException` | 503 | 429, 5xx, 401/403, timeouts — any other Jev request failure |

The Jev handlers always return fixed, app-owned text (e.g. "Jev not configured", "Jev is temporarily unavailable", "Jev rejected the request (HTTP …)", "The Jev request failed. Try again later.") and never forward a TypeSafe exception's message or response body, because TypeSafe error content can echo request details. See [Jev Scoring Integration](../integrations/jev-scoring.md) for the full retry/breaker behavior behind these mappings.

### Other conventions

- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`, `TopicRequest`, `FeedbackRequest`, `RescoreRequest`) rather than raw `Map` bodies, for compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); the `/api/interest/priority` list uses the related `PriorityPage` record, whose opaque cursor encodes the served `(score, date, id)` sort tuple rather than a plain article id — see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules.
- API surface: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`, `/api/interest` (profile, topics, learned, status, preview, suggestions, rescore, priority).

### RestClient and outbound timeout configuration

- `RestClientConfig` registers a `RestClientCustomizer` bean (`org.springframework.boot.restclient.RestClientCustomizer` — Boot 4 moved this out of `org.springframework.boot.web.client`) that adds a `myfeeder/<version> (+https://github.com/bartram/myfeeder)` User-Agent header to every auto-configured `RestClient.Builder` injection, covering feed fetches and the Raindrop client.
- Outbound connect/read timeouts and redirect policy for the auto-configured `RestClient` are set through `spring.http.clients.connect-timeout` / `read-timeout` / `redirects` (the metadata lives in `spring-boot-http-client-*.jar`, not `-autoconfigure`); the singular `spring.http.client.*` keys are deprecated and silently unbound in Boot 4. Current values are 5s/30s in `application.yaml`, guarded by `HttpClientConfigurationTest`.
- `TypeSafeConfig` owns the `TypeSafeClient` bean used for Jev scoring instead of letting the starter's `@ConditionalOnMissingBean` create it — a blank API key must never crash startup. It builds a Jev-only Reactor Netty request factory (connect 5s, read = `spring.ai.typesafe.timeout`) on a `clone()` of the auto-configured `RestClient.Builder`, which keeps the shared User-Agent customizer without changing any other bean's transport; `baseUrl` is set explicitly so the SDK never falls back to its own `TYPESAFE_*` env vars.

### Resilience4j convention (Raindrop and Jev clients)

Both outbound integrations follow the same pattern: `@CircuitBreaker(name = "...")` + `@Retry(name = "...")` sit on the **API-client bean** (`RaindropApiClientImpl`, `JevApiClientImpl`), not on the service layer, so business validation (not-configured/disabled checks in `RaindropService`/`JevApiClientImpl.isConfigured`) runs outside the breaker and only the real HTTP/SDK call is wrapped. Self-invocation bypasses the AOP proxy, so the annotated methods must live on a bean the service calls into, never one it calls from within itself. The annotations only take effect because `spring-boot-starter-aspectj` is on the classpath; without it, `@CircuitBreaker`/`@Retry` are silently inert. Execution order is set globally via `resilience4j.circuitbreaker.circuit-breaker-aspect-order: 1` and `resilience4j.retry.retry-aspect-order: 2` (lower = outer), so the breaker wraps the retry and records one outcome per logical call rather than one per retry attempt — Resilience4j's own defaults would place the retry outermost. Each client's "not configured" exception (`RaindropNotConfiguredException`, `JevNotConfiguredException`) is listed under `ignore-exceptions` for both its circuit breaker and retry instances, so a missing token/key never opens the breaker or burns a retry attempt. `RaindropApiClientImpl` additionally uses a `fallbackMethod` that re-throws `RaindropNotConfiguredException` as-is and wraps everything else as `IllegalStateException` (409); `JevApiClientImpl` has no fallback — typed TypeSafe exceptions and `CallNotPermittedException` propagate unchanged to `GlobalExceptionHandler`. See [Jev Scoring Integration](../integrations/jev-scoring.md) for the `jev` instance's specific retry/breaker tuning (3 attempts, `Retry-After` honoring, COUNT_BASED window of 20).

## Scoring executor

`InterestScoringConfig` defines the `interestScoringExecutor` bean (`ThreadPoolTaskExecutor`, threads named `jev-score-`), registered with `defaultCandidate = false` so it never satisfies Spring Boot's `@ConditionalOnMissingBean(Executor.class)` and silently replace the `applicationTaskExecutor`. Core and max pool size both equal `myfeeder.interest.concurrency` (1 by default); the bounded queue (`queue-capacity`, 1000) keeps the default abort rejection policy on purpose — `ScoringQueue` catches the resulting `TaskRejectedException` and releases the article id so the periodic sweep can retry it, whereas a discarding policy would leave the id marked in-flight forever. Consumers inject the bean with `@Qualifier(InterestScoringConfig.EXECUTOR)`; there is no `@Async` anywhere in the scoring path — work is handed to the executor explicitly. Full pipeline mechanics (sweep cadence, eligibility window, score discard-on-stale-rubric) are covered in [Interest Scoring Workflow](../workflows/interest-scoring.md).

## Frontend

`src/main/frontend/` is a React 19 + TypeScript SPA built with Vite.

- **Layout**: three resizable panels — feed tree (`FeedPanel`), article list (`ArticleList`/`BoardArticleList`/`PriorityList`), reading pane (`ReadingPane`), composed in `App.tsx` under `AppShell`. `App.tsx` also mounts route components (`FeedArticles`, `FolderArticles`, `StarredArticles`, `AllArticles`, `BoardArticles`, `PriorityList` on `/priority`) that each derive TanStack Query filters from Zustand preferences (sort order, hide-read).
- **Data layer**: `src/api/*.ts` are thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, version, interest); `src/hooks/*.ts` wrap them in TanStack Query hooks.
- **State**: Zustand stores in `src/stores/` — `uiStore` (selection/focus/panel state), `preferencesStore` (localStorage-persisted settings: theme, font sizes, sort order, hide-read) and `priorityStore` (the "Ranking changed" hint flag for the Priority view). `preferencesStore` uses Zustand's `persist`; adding a field with a default does **not** retroactively apply to existing users — needs a `merge` function or version migration.
- **Interest and engagement hooks** (`src/hooks/useInterest.ts`, `useEngagement.ts`, `engagementReaction.ts`, `useFeedback.ts`, `usePriorityArticles.ts`):
  - `useInterest.ts` exposes `useInterestStatus` (polls `/api/interest/status` every 15s only while articles are configured, past cold start, and waiting to be scored), `useInterestTiers` (reads the same status query's `tiers` field with `staleTime: Infinity`, falling back to `DEFAULT_TIERS` until loaded), profile/topic CRUD hooks, `useLearnedTopics`, and the topic-suggestion hooks `useTopicSuggestions`/`useDismissSuggestion`.
  - `useEngagement.ts` provides `useOpenOriginal` (opens the article's original URL via `window.open`, then fires a bodyless `PUT /api/articles/{id}/engagement/open` whose errors are swallowed) and `useForgetEngagement` (`DELETE /api/articles/{id}/engagement`). Both call `afterEngagement` on success.
  - `engagementReaction.ts` is the shared post-engagement/post-vote reaction, not a hook: `afterEngagement(qc, id, onPriority)` refetches the article by id with `staleTime: 0`, invalidates `['interest', 'learned']`, `['interest', 'suggestions']` and `['articles']` (and, off the Priority view, other open by-id articles) via `invalidateAfterLearnedChange`, and — only while on `/priority` — patches that row's `interestScore` in place and sets the "Ranking changed" flag if the refetched score differs; `['priority']` itself is never invalidated or refetched by this reaction, so the list being triaged never re-sorts mid-session.
  - `TierContext` (`src/utils/interest.ts`), populated from `useInterestTiers()` in `App.tsx`'s `MainLayout`, supplies the high/neutral score thresholds that `InterestBadge` and related components use to render score tiers consistently across the article list, reading pane and Priority view.
- **PriorityPage/`PriorityList`**: the `/priority` route renders `PriorityList`, backed by `usePriorityArticles`, which fetches and caches pages of the backend's `PriorityPage` (score-ranked) response under the `PRIORITY_KEY` query key; leaving `/priority` drops that cache so re-entry always fetches page 1 fresh.
- **ReadingPane reader-view toggle**: `GET /api/articles/{id}/extracted-content` fetches the article's original page through `FeedFetcher` (keeping its SSRF guard and size cap), extracts readable content with Readability4J server-side, and caches it on the article row. `ReadingPane` auto-enables reader view when a feed item has no content/summary, and otherwise offers a manual "📖 Reader View" / "📖 Feed View" toggle button; extracted HTML is sanitized with DOMPurify (`FORBID_TAGS`/`FORBID_ATTR: ['style']`) so publisher styling never overrides the app theme.
- **Keyboard shortcuts**: `useKeyboardShortcuts` hook implements vim-style navigation (`j`/`k`/`n`/`p`/`m`/`s`/`o`/`b`/`v`/`r`) plus `g`-prefixed chords and `+`/`-` font-size adjustment on the focused panel; it is Priority-aware (`isPriority`, `onPriorityNextPage`, `onPriorityRefresh`) when the Priority view is active.
- **Themes**: 6 themes (3 dark/3 light) in `src/themes.ts`, applied via `useTheme`, persisted in `preferencesStore`.
- **Build/embed**: `npm run build` outputs to `src/main/resources/static/`; the Gradle `npmBuild` task wires this into `./gradlew build` so the SPA ships inside the Spring Boot jar. `SpaForwardController` (in `config/`) forwards non-API routes to the SPA's `index.html` for client-side routing. See [Operations Runbook](../operations/runbook.md) for the full build/deploy sequence — the frontend must be rebuilt with `clean` before every image build or a stale bundle ships.

Engagement capture semantics (what gets recorded, when it's sticky, and how it feeds the learned-weight CTEs) and the full topic-gap-discovery suggestion lifecycle are detailed in [Engagement and Suggestions Workflow](../workflows/engagement-and-suggestions.md), not duplicated here.

## Spring Boot 4 / Jackson 3.x notes

These package moves affect how backend code in this repository is read and written:

- Jackson 3.x **databind** moved to `tools.jackson.databind.*` (`ObjectMapper`, `JsonNode`, `JsonMapper`). Spring Boot auto-configures `tools.jackson.databind.ObjectMapper` as a bean.
- Jackson 3.x **annotations** did *not* move — `@JsonProperty`, `@JsonIgnoreProperties`, `@JsonIgnore`, `@JsonCreator` etc. remain in `com.fasterxml.jackson.annotation.*` (jackson-annotations 2.x is a transitive dependency of jackson-databind 3.x). There is no `tools.jackson.annotation` package.
- Test annotations `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest` live under `org.springframework.boot.*.test.autoconfigure` packages (e.g. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`), and test starter dependencies follow the pattern `spring-boot-starter-<module>-test`.
- `RestClientCustomizer` is in `org.springframework.boot.restclient` (Boot 4 moved it out of `org.springframework.boot.web.client`); Spring Boot auto-applies any `RestClientCustomizer` bean to every auto-configured `RestClient.Builder` injection.
- Outbound `RestClient` timeouts/redirects use the plural `spring.http.clients.*` keys (see HTTP layer conventions above); the older singular `spring.http.client.*` keys are deprecated and silently not bound.
- `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7 — use `HttpStatus.valueOf(422)`.

## Infrastructure dependencies

- **PostgreSQL**: primary datastore, Flyway-migrated (`src/main/resources/db/migration/`), external at `pg.bartram.org` in production (not deployed by the Helm chart — see [Operations Runbook](../operations/runbook.md)).
- **Redis**: backs `@Cacheable` (currently only `RaindropService.listCollections`) via Spring Cache abstraction.
- **Docker**: required locally for both `./gradlew test` (Testcontainers-backed repository/integration tests) and `./gradlew bootRun` (Compose-managed Postgres/Redis). `./gradlew bootTestRun` runs the app itself against Testcontainers-managed services with no external Compose needed.
