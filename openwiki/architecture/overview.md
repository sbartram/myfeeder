---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4 backend package layout (feed polling plus interest scoring), Spring Data JDBC persistence, React 19 SPA frontend build/embed process, and how backend and frontend fit together in one deployable jar.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend, interest-scoring]
verified:
  - by: openwiki/0.7.0
    at: 2026-10-04T13:52:44.431Z
sources:
  - id: openwiki-source-0386155cb694082a3e8ad543
    resource: repo://src/main/frontend/src/App.tsx
  - id: openwiki-source-d6d102291b1964678af254e9
    resource: repo://src/main/frontend/src/components/ReadingPane.tsx
  - id: openwiki-source-488238bd6a4828fc54072530
    resource: repo://src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - id: openwiki-source-0a50732f461f501fbe68e757
    resource: repo://src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - id: openwiki-source-a71602e15d81cc6dfe06d6ec
    resource: repo://src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - id: openwiki-source-068b4c54848ec0b7c44aa265
    resource: repo://src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - id: openwiki-source-cdc24fc3ca0c47ee6972a535
    resource: repo://src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-016143adecf7d2b7f9c2d654
    resource: repo://src/main/java/org/bartram/myfeeder/controller/InterestController.java
  - id: openwiki-source-9ff61ecb2e4e04e1866b4b69
    resource: repo://src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java
  - id: openwiki-source-bfd9e6b64553ce72d0555fce
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - id: openwiki-source-e10d497b6eb67a010f44960a
    resource: repo://src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-2a86193d3842f7c072ad71de
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java
  - id: openwiki-source-54ee9600bde3e6d1f8016bed
    resource: repo://src/main/java/org/bartram/myfeeder/service/InterestService.java
generated: { by: "openwiki/0.7.0", at: "2026-10-04T13:52:44.431Z" }
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`).

```
config/       MyfeederProperties (myfeeder.* config, implements Validator), RestClientConfig (User-Agent customizer),
              SpaForwardController, TypeSafeConfig (app-owned TypeSafeClient bean, jev retry interval),
              InterestScoringConfig (jev-score executor), JevEventLogging (jev retry/breaker log lines)
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount,
              InterestProfile, InterestTopic, InterestBreakdown, ArticleFeedback, EngagementKind, SuggestionDismissalReason
repository/   Spring Data JDBC repositories (one per model) plus ArticleScoreStore, ArticleFeedbackStore,
              ArticleEngagementStore, InterestScoreQueries (blend/learned CTEs), TopicSuggestionStore
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records, OpmlFeed/OpmlParseException
service/      FeedService, ArticleService, FeedPollingService, FeedFetcher, FolderService, BoardService, RetentionService,
              OpmlService, OpmlImportService, ArticleExtractionService (reader view); interest/scoring family:
              InterestService, InterestStatusService, InterestPreviewService, InterestRescoreService,
              ArticleScoringService, ScoringQueue, ArticleFeedbackService, TopicSuggestionService, PriorityService
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig; JevApiClient/JevApiClientImpl, JevJudgment,
              JevNotConfiguredException — see integrations/raindrop.md and integrations/jev.md
event/        FeedSavedEvent, FeedDeletedEvent (after-commit scheduling signals), ArticlesIngestedEvent (new article ids for interest scoring)
controller/   REST controllers (Feed/Article/Folder/Board/IntegrationConfig/Opml/Version plus Interest*, TopicSuggestion)
              + typed request records + PaginatedResponse/PriorityPage + GlobalExceptionHandler
scheduler/    FeedPollingScheduler (dynamic per-feed polling with backoff), InterestScoringSweep (jev scoring sweep)
```

This is a classic layered architecture (controller → service → repository), now hosting **two** major subsystems side by side: feed polling/ingestion and interest scoring. Three cross-cutting mechanisms are worth understanding before changing anything: the **event-driven scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)), the **single fetch path** through `FeedFetcher` (used by subscribe, poll, *and* article extraction — never issue a raw `RestClient` call to fetch remote content), and the **Resilience4j breaker+retry placement** on API-client beans, which now exists as two independent pairs (`raindrop` and `jev`; see below).

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

## Interest scoring subsystem

Alongside feed polling, myfeeder runs a second major subsystem that scores unread articles against a user-defined interest profile and topics, using an LLM-backed judging service ("Jev") to produce a per-article/per-topic relevance signal. At the architecture level this subsystem adds:

- An **app-owned `TypeSafeClient` bean** (`config/TypeSafeConfig`), defined explicitly so the Spring AI TypeSafe starter's auto-configured bean (which would crash startup on a blank API key) backs off; a missing/blank `MYFEEDER_TYPESAFE_API_KEY` only disables scoring.
- A **`jev` Resilience4j circuit-breaker + retry pair** (`integration/JevApiClient`/`JevApiClientImpl`), configured and operated independently of the existing `raindrop` pair — same ordering convention (breaker outer, retry inner) but its own thresholds, ignored exceptions, and log lines (`config/JevEventLogging`).
- A **bounded scoring executor and queue**: a dedicated `interestScoringExecutor` thread pool (`config/InterestScoringConfig`) sized by `myfeeder.interest.concurrency`, backed by `service/ScoringQueue`, which accepts both sweep-selected and newly-ingested article ids.
- A **periodic sweep** (`scheduler/InterestScoringSweep`) that is the only drain loop for "needs scoring" work — it covers startup backfill, recovery after an outage, and re-scoring after profile/topic changes, skipping while Jev is unconfigured, the breaker is open, or the app is in cold start.

This overview only summarizes placement and responsibility; the scoring pipeline, blend/learned weighting, engagement learning, gap discovery ("Suggested topics"), and Priority ranking are documented in [Interest Scoring Workflow](../workflows/interest-scoring.md). Jev's API contract, configuration, and resilience tuning live in [Jev Integration](../integrations/jev.md).

## Reader view / article extraction

`GET /api/articles/{id}/extracted-content` is a cross-cutting capability that lets any article open in a readable "reader view" regardless of what content the source feed provided:

- `controller/ArticleController` exposes the endpoint; `service/ArticleExtractionService` loads the `Article`, fetches its original page **through `FeedFetcher`** (reusing the same SSRF guard, size cap, and User-Agent customizer used for feed polling — never a raw `RestClient` call), and extracts readable content with Readability4J.
- The extracted HTML is cached on `article.extracted_content` on first request and served from the cache afterwards; it ages out together with `content` via `RetentionService`.
- On the frontend, `ReadingPane` auto-enables reader view when the selected article has no feed-provided `content`/`summary`, and also offers a manual "📖 Reader View" / "📖 Feed View" toggle per article (`useExtractedArticle` in `src/hooks/useArticles.ts`). Extracted HTML is sanitized with DOMPurify using `FORBID_TAGS`/`FORBID_ATTR: ['style']` (stricter than the normal feed-content sanitization) so publisher styling — e.g. dark page backgrounds — is replaced by the app's own theme.

## Configuration

`MyfeederProperties` (prefix `myfeeder`, implements Spring's `Validator` so Boot's binder validates it on every bind) binds four groups from `application.yaml`:

- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)
- `myfeeder.interest.*` — interest scoring configuration, validated at startup (fixed-text rejection, never echoing a bound value):
  - `concurrency`, `queue-capacity` — scoring executor/queue sizing
  - `sweep-delay`, `sweep-initial-delay`, `sweep-batch-size` — `InterestScoringSweep` cadence and per-run cap
  - `window-days` — the scoring eligibility window
  - `blend.tiers.high`/`neutral` — badge thresholds served on `/api/interest/status`
  - `blend.engagement.open-weight`/`save-weight`/`cap` — engagement-learning constants (cap `0` disables engagement learning; otherwise `0 <= open-weight < save-weight < 1` and `0 < cap < learned-cap`)
  - `suggestions.near-miss` — the gap-discovery ("Suggested topics") near-miss threshold (`0 < near-miss <= 0.5`)

  See [Interest Scoring Workflow](../workflows/interest-scoring.md) and [Jev Integration](../integrations/jev.md) for how these constants are used and tuned.

## HTTP layer conventions

- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` responses:
  - `NotFoundException`→404, `IllegalArgumentException`→400, `OpmlParseException`→400, `IllegalStateException`→409, `FeedParseException`/`FeedFetchException`→422, `RaindropNotConfiguredException`→503
  - Jev-specific mappings: `JevNotConfiguredException`→503, `CallNotPermittedException`→503 (the `jev` breaker is open), `TypeSafeBadRequestException`/`TypeSafeUnprocessableEntityException`→422, any other `TypeSafeException`→503. Jev error details are fixed app text and never echo a TypeSafe message or response body (TypeSafe messages/bodies can carry request content).
  - Services should throw the specific exception type rather than have controllers catch/translate.
- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`, `TopicRequest`, `RescoreRequest`) rather than raw `Map` bodies — this was a deliberate refactor to get compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules that make this correct.
- API surface: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`, `/api/interest`.

## Frontend

`src/main/frontend/` is a React 19 + TypeScript SPA built with Vite.

- **Layout**: three resizable panels for day-to-day reading — feed tree (`FeedPanel`), article list (`ArticleList`/`BoardArticleList`), reading pane (`ReadingPane`) — composed in `App.tsx` under `AppShell`. A fourth major UI surface, the **Priority view** (`PriorityList`, routed at `/priority`) and its companion **Interests dialog** (`InterestsDialog`, opened from Settings or from the reading pane's "Create topic from article" action), sits alongside these three: it replaces the article list with a ranked triage queue and surfaces interest-scoring configuration (profile, topics, tiers, Suggested topics, Re-score). See [Interest Scoring Workflow](../workflows/interest-scoring.md) for its behavior.
- **Data layer**: `src/api/*.ts` are thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, version, interest); `src/hooks/*.ts` wrap them in TanStack Query hooks (`useArticles`, `useFeeds`, `useFolders`, `useBoards`, `useOpml`, `useInterest`, `useFeedback`, `usePriorityArticles`, `useEngagement`). `App.tsx` composes route components (`FeedArticles`, `FolderArticles`, `StarredArticles`, `AllArticles`, `BoardArticles`, and the `/priority` route) that each derive TanStack Query filters from Zustand preferences (sort order, hide-read).
- **State**: Zustand stores in `src/stores/` — `uiStore` (selection/focus/panel state), `preferencesStore` (localStorage-persisted settings: theme, font sizes, sort order, hide-read), plus `priorityStore` and `feedbackStore` supporting the Priority view and thumbs feedback. `preferencesStore` uses Zustand's `persist`; adding a field with a default does **not** retroactively apply to existing users (see Testing/Gotchas) — needs a `merge` function or version migration.
- **Keyboard shortcuts**: `useKeyboardShortcuts` hook implements vim-style navigation (`j`/`k`/`n`/`p`/`m`/`s`/`o`/`b`/`v`/`r`) plus `g`-prefixed chords and `+`/`-` font-size adjustment on the focused panel. It resolves the "current article" via `useArticle(selectedArticleId)` (direct fetch by ID) so single-article actions work even on views (Starred/Folder/Priority) whose visible list is keyed differently from the list this hook receives.
- **Themes**: 6 themes (3 dark/3 light) in `src/themes.ts`, applied via `useTheme`, persisted in `preferencesStore`.
- **Build/embed**: `npm run build` outputs to `src/main/resources/static/`; the Gradle `npmBuild` task wires this into `./gradlew build` so the SPA ships inside the Spring Boot jar. `SpaForwardController` (in `config/`) forwards non-API routes to the SPA's `index.html` for client-side routing. See [Operations Runbook](../operations/runbook.md) for the full build/deploy sequence — the frontend must be rebuilt with `clean` before every image build or a stale bundle ships.

## Infrastructure dependencies

- **PostgreSQL**: primary datastore, Flyway-migrated (`src/main/resources/db/migration/`), external at `pg.bartram.org` in production (not deployed by the Helm chart — see [Operations Runbook](../operations/runbook.md)).
- **Redis**: backs `@Cacheable` (currently only `RaindropService.listCollections`) via Spring Cache abstraction.
- **Docker**: required locally for both `./gradlew test` (Testcontainers-backed repository/integration tests) and `./gradlew bootRun` (Compose-managed Postgres/Redis). `./gradlew bootTestRun` runs the app itself against Testcontainers-managed services with no external Compose needed.
