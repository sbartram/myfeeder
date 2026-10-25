---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4 backend package layout (feed pipeline plus the interest-scoring/Jev subsystem), Spring Data JDBC persistence, React 19 SPA frontend build/embed process, HTTP-layer conventions, and infrastructure dependencies.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend, interest-scoring, jev]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-25T13:07:33.493Z
sources:
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-488238bd6a4828fc54072530
    resource: repo://src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - id: openwiki-source-0a50732f461f501fbe68e757
    resource: repo://src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - id: openwiki-source-a71602e15d81cc6dfe06d6ec
    resource: repo://src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - id: openwiki-source-2837caa12b7e70d7e094eb88
    resource: repo://src/main/java/org/bartram/myfeeder/config/RestClientConfig.java
  - id: openwiki-source-4546c2eb2f7a02188085d297
    resource: repo://src/main/java/org/bartram/myfeeder/config/SpaForwardController.java
  - id: openwiki-source-068b4c54848ec0b7c44aa265
    resource: repo://src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-016143adecf7d2b7f9c2d654
    resource: repo://src/main/java/org/bartram/myfeeder/controller/InterestController.java
  - id: openwiki-source-c4dd975523696711a0431829
    resource: repo://src/main/java/org/bartram/myfeeder/controller/InterestPreviewController.java
  - id: openwiki-source-8981c71714735d00f2c6d6eb
    resource: repo://src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java
  - id: openwiki-source-ae4c1267ffc866277d667caf
    resource: repo://src/main/java/org/bartram/myfeeder/controller/InterestStatusController.java
  - id: openwiki-source-9ff61ecb2e4e04e1866b4b69
    resource: repo://src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java
  - id: openwiki-source-c3ff2e449c60f14eac2534e2
    resource: repo://src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java
  - id: openwiki-source-1ccecc8d0cb9715b566fdff9
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - id: openwiki-source-9f0c7cf4f85988b7eeb61cf4
    resource: repo://src/main/java/org/bartram/myfeeder/MyfeederApplication.java
  - id: openwiki-source-b73fb67f4958acd1b9891c47
    resource: repo://src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java
  - id: openwiki-source-f645bebddc8435267a067d4f
    resource: repo://src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java
  - id: openwiki-source-e10d497b6eb67a010f44960a
    resource: repo://src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
  - id: openwiki-source-54ee9600bde3e6d1f8016bed
    resource: repo://src/main/java/org/bartram/myfeeder/service/InterestService.java
generated: { by: "openwiki/0.7.2", at: "2026-10-25T13:07:33.493Z" }
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`).

```
config/       MyfeederProperties (self-validating engagement/suggestion constants, myfeeder.* config), RestClientConfig
              (User-Agent customizer), SpaForwardController, TypeSafeConfig (app-owned TypeSafeClient, jev retry
              interval), InterestScoringConfig (jev-score executor), JevEventLogging (jev retry/breaker log lines)
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount,
              InterestProfile, InterestTopic, InterestBreakdown, ArticleFeedback, EngagementKind,
              SuggestionDismissalReason
repository/   Spring Data JDBC repositories (one per core model) plus the interest-scoring stores: ArticleScoreStore,
              ArticleFeedbackStore, ArticleEngagementStore, InterestScoreQueries (blend/learned CTEs), and
              TopicSuggestionStore (gap-discovery candidates/dismissals)
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records,
              OpmlFeed/OpmlParseException
service/      Feed pipeline: FeedService, ArticleService, FeedPollingService, FeedFetcher, FolderService,
              BoardService, RetentionService, OpmlService, OpmlImportService, ArticleExtractionService; interest/jev/
              engagement: InterestService, InterestStatusService, InterestPreviewService, InterestRescoreService,
              ArticleScoringService, ScoringQueue, InterestScoringListener, ArticleFeedbackService,
              TopicSuggestionService, PriorityService, ScoreBreakdowns, and related support types
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig — see integrations/raindrop.md; JevApiClient
              (Impl), JevJudgment, JevNotConfiguredException — see integrations/jev-typesafe.md
event/        FeedSavedEvent, FeedDeletedEvent — after-commit scheduling signals; ArticlesIngestedEvent — new
              article ids for interest scoring
controller/   REST controllers + typed request records + PaginatedResponse + GlobalExceptionHandler, including
              InterestController, InterestStatusController, InterestPreviewController, InterestRescoreController,
              TopicSuggestionController
scheduler/    FeedPollingScheduler — dynamic per-feed polling with backoff; InterestScoringSweep — periodic jev
              scoring sweep
```

This is a classic layered architecture (controller → service → repository), with two cross-cutting mechanisms worth understanding before changing anything: the **event-driven scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)) and the **single fetch path** through `FeedFetcher` (used by both subscribe and poll — never issue a raw `RestClient` call to fetch feed content).

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

The `service/` and `integration/` packages have grown a second, equally large subsystem on top of the original feed pipeline: interest scoring (a Jev-judged relevance score per article, blended with thumbs/engagement learning) and its outbound client, Jev/TypeSafe. That subsystem is covered in depth in its own pages rather than duplicated here — see the next section.

## Interest ranking / Jev subsystem

The interest-scoring feature (profile/topic rubric, Jev-judged scores, thumbs/engagement learning, gap discovery, the scoring sweep and its Resilience4j breaker) is a substantial subsystem layered on top of the base feed architecture. It owns its own tables (`V6__interest_scoring.sql`, `V7__engagement.sql`), its own executor and scheduler (`InterestScoringConfig`/`InterestScoringSweep`), and its own outbound client (`JevApiClient` over the Jev/TypeSafe SDK). Rather than restate that detail here, see:

- [Interest Scoring (domain concepts)](../domain/interest-scoring.md) — the scoring model, blend/learned formulas, tiers, and schema
- [Interest Ranking Workflow](../workflows/interest-ranking.md) — end-to-end control flow: ingest → sweep → score → blend → display → feedback/engagement learning
- [Jev / TypeSafe Integration](../integrations/jev-typesafe.md) — the `TypeSafeClient` bean, retry/circuit-breaker configuration, and failure handling

## Configuration

`MyfeederProperties` (prefix `myfeeder`) binds configuration groups from `application.yaml` and implements `Validator` so Boot's binder self-validates it on startup:
- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)
- `myfeeder.interest.*` — window/concurrency/sweep sizing, plus the nested `blend` (profile points, learn rate, learned cap, tier thresholds, engagement weights/cap) and `suggestions` (gap-discovery near-miss threshold) groups; `MyfeederProperties.validate` refuses startup with fixed text (`ENGAGEMENT_INVALID`, `SUGGESTIONS_INVALID`) if these constants are out of range — see [Interest Scoring](../domain/interest-scoring.md) for the semantics of each constant

## HTTP layer conventions

- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` responses: `NotFoundException`→404, `IllegalArgumentException`→400, `OpmlParseException`→400, `IllegalStateException`→409, `FeedParseException`/`FeedFetchException`→422, `RaindropNotConfiguredException`→503. The same handler also maps the Jev/interest-scoring exceptions: `JevNotConfiguredException`→503 ("Jev not configured"), Resilience4j's `CallNotPermittedException`→503 ("Jev unavailable", the `jev` breaker is open), `TypeSafeBadRequestException`/`TypeSafeUnprocessableEntityException`→422 ("Jev rejected the request"), and any other `TypeSafeException`→503 ("Jev request failed"). Every Jev-related `ProblemDetail` detail is fixed app text and never echoes a TypeSafe exception's message or body, since that content can reflect request input. Services should throw the specific exception type rather than have controllers catch/translate.
- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`, `TopicRequest`, `FeedbackRequest`, `RescoreRequest`) rather than raw `Map` bodies — this was a deliberate refactor (commit `5136a3e`) to get compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules that make this correct.
- API surface: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`, `/api/interest` (profile, topics, status, preview, rescore, and topic suggestions — served by `InterestController`, `InterestStatusController`, `InterestPreviewController`, `InterestRescoreController`, `TopicSuggestionController`; see [Interest Ranking Workflow](../workflows/interest-ranking.md) for the routes).

## Outbound HTTP clients and the Jev transport clone

The auto-configured `RestClient.Builder` uses Reactor Netty (`ReactorClientHttpRequestFactory`, pinned via an explicit `reactor-netty-http` dependency) and carries a shared `myfeeder/<version>` User-Agent customizer (`RestClientConfig`), applied through the Boot 4 `RestClientCustomizer` mechanism. Services such as `FeedFetcher` and `RaindropApiClientImpl` inject this shared builder directly.

The Jev client is the one exception: `config/TypeSafeConfig` builds its own `TypeSafeClient` bean on a **`clone()`** of that same auto-configured `RestClient.Builder`, with its own Reactor Netty request factory carrying Jev-specific timeouts (5s connect, `spring.ai.typesafe.timeout` read, 30s by default). Cloning (rather than reusing) the builder keeps the shared User-Agent customizer and observation instrumentation while giving Jev calls an independent transport, so neither the timeout tuning nor outages on one client's socket pool affect the other's (feed fetches, Raindrop). **Any new outbound client added to this codebase should decide explicitly whether it shares the common builder (most should) or needs its own cloned/configured transport like Jev** (only when it needs different timeouts, base URL handling, or isolation from the shared transport).

`config/TypeSafeConfig` also defines the `TypeSafeClient` bean itself rather than letting the TypeSafe starter's `@ConditionalOnMissingBean` create one — the starter's own bean would refuse startup on a blank API key, whereas myfeeder's bean only logs and disables Jev calls. See [Jev / TypeSafe Integration](../integrations/jev-typesafe.md) for the retry/circuit-breaker details built around this client.

## Spring Boot 4.0.8 / Jackson 3.x platform notes

The backend runs Spring Boot 4.0.8 with Jackson 3.x, which moved or renamed several packages/keys that affect code anywhere in the tree (not just one feature):

- **Jackson 3.x databind moved**: `ObjectMapper`, `JsonNode`, `JsonMapper` etc. now live in `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`. Spring Boot auto-configures a `tools.jackson.databind.ObjectMapper` bean. Jackson **annotations** (`@JsonProperty`, `@JsonIgnore`, `@JsonCreator`, …) did **not** move and remain in `com.fasterxml.jackson.annotation.*` — there is no `tools.jackson.annotation` package.
- **`RestClientCustomizer` moved**: it is now in `org.springframework.boot.restclient` (out of `org.springframework.boot.web.client` in Boot 3). `RestClientConfig` (above) depends on this package. Spring Boot auto-applies any `RestClientCustomizer` bean to the auto-configured `RestClient.Builder` that services inject.
- **Outbound timeout/redirect keys renamed**: connect/read timeouts and redirect behavior for the auto-configured `RestClient` are bound from `spring.http.clients.connect-timeout` / `read-timeout` / `redirects` (plural `clients`, metadata in `spring-boot-http-client-*.jar`). The singular `spring.http.client.*` keys were deprecated in Boot 4.0.0 and are silently not bound — any code or configuration still using the singular form has no effect. These settings apply globally to every `RestClient` built off the shared builder (feed fetches, Raindrop); the Jev client's cloned builder also inherits them before its own timeout override is applied. Current values are 5s/30s in `application.yaml`, guarded by `HttpClientConfigurationTest`.
- Test slice annotations (`@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest`) live under `org.springframework.boot.*.test.autoconfigure` packages, and test starter dependencies follow `spring-boot-starter-<module>-test`.
- `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7; the codebase uses `HttpStatus.valueOf(422)` instead.

## Frontend

`src/main/frontend/` is a React 19 + TypeScript SPA built with Vite.

- **Layout**: three resizable panels — feed tree (`FeedPanel`), article list (`ArticleList`/`BoardArticleList`), reading pane (`ReadingPane`), composed in `App.tsx` under `AppShell`.
- **Data layer**: `src/api/*.ts` are thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, version, interest); `src/hooks/*.ts` wrap them in TanStack Query hooks (`useArticles`, `useFeeds`, `useFolders`, `useBoards`, `useOpml`, `useInterest`, `useFeedback`, `usePriorityArticles`, `useEngagement`). `App.tsx` composes route components (`FeedArticles`, `FolderArticles`, `StarredArticles`, `AllArticles`, `BoardArticles`) that each derive TanStack Query filters from Zustand preferences (sort order, hide-read).
- **State**: Zustand stores in `src/stores/` — `uiStore` (selection/focus/panel state) and `preferencesStore` (localStorage-persisted settings: theme, font sizes, sort order, hide-read). `preferencesStore` uses Zustand's `persist`; adding a field with a default does **not** retroactively apply to existing users (see Testing/Gotchas) — needs a `merge` function or version migration.
- **Keyboard shortcuts**: `useKeyboardShortcuts` hook implements vim-style navigation (`j`/`k`/`n`/`p`/`m`/`s`/`o`/`b`/`v`/`r`) plus `g`-prefixed chords (`ga`, `gs`, `gb`) and `+`/`-` font-size adjustment on the focused panel. It resolves the "current article" via `useArticle(selectedArticleId)` (direct fetch by ID) so single-article actions work even on views (Starred/Folder) whose visible list is queried differently from the list this hook receives — a known partial fix (see backlog bug on `j`/`k` navigation).
- **Themes**: 6 themes (3 dark/3 light) in `src/themes.ts`, applied via `useTheme`, persisted in `preferencesStore`.
- **Interest ranking UI**: the Priority view, interests dialog, score badges, and thumbs feedback are a separate layer over this same data/state architecture — see [Interest Ranking Workflow](../workflows/interest-ranking.md) rather than duplicating that detail here.
- **Build/embed**: `npm run build` outputs to `src/main/resources/static/`; the Gradle `npmBuild` task wires this into `./gradlew build` so the SPA ships inside the Spring Boot jar. `SpaForwardController` (in `config/`) forwards non-API routes to the SPA's `index.html` for client-side routing. See [Operations Runbook](../operations/runbook.md) for the full build/deploy sequence — the frontend must be rebuilt with `clean` before every image build or a stale bundle ships.

## Infrastructure dependencies

- **PostgreSQL**: primary datastore, Flyway-migrated (`src/main/resources/db/migration/`), external at `pg.bartram.org` in production (not deployed by the Helm chart — see [Operations Runbook](../operations/runbook.md)). Migrations include the core schema plus `V6__interest_scoring.sql` and `V7__engagement.sql` for the interest-ranking tables — see [Interest Scoring](../domain/interest-scoring.md).
- **Redis**: backs `@Cacheable` (currently only `RaindropService.listCollections`) via Spring Cache abstraction.
- **Docker**: required locally for both `./gradlew test` (Testcontainers-backed repository/integration tests) and `./gradlew bootRun` (Compose-managed Postgres/Redis). `./gradlew bootTestRun` runs the app itself against Testcontainers-managed services with no external Compose needed.
- **Jev/TypeSafe**: an external HTTP API (`org.springaicommunity:spring-ai-starter-typesafe`) used only for interest scoring; optional at runtime (a blank `MYFEEDER_TYPESAFE_API_KEY` disables scoring without blocking startup) and protected by a dedicated Resilience4j circuit breaker/retry pair — see [Jev / TypeSafe Integration](../integrations/jev-typesafe.md).
