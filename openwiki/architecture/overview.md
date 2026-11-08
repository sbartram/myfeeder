---
type: Architecture Overview
title: myfeeder Architecture Overview
description: Explains the runtime architecture of myfeeder — Spring Boot 4 backend package layout (including interest scoring), Spring Data JDBC persistence, the resilience4j aspect-ordering convention, React 19 SPA frontend structure (including the Priority view and Interests dialog), and how backend and frontend fit together in one deployable jar.
resource: src/main/java/org/bartram/myfeeder
tags: [architecture, spring-boot, react, backend, frontend, interest-scoring, resilience4j]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-08T14:57:22.664Z
sources:
  - id: openwiki-source-0386155cb694082a3e8ad543
    resource: repo://src/main/frontend/src/App.tsx
  - id: openwiki-source-5a4b8a5a0a0ff7abed2ae511
    resource: repo://src/main/frontend/src/components/InterestsDialog.tsx
  - id: openwiki-source-b851d2e2d6e546d3a8b83692
    resource: repo://src/main/frontend/src/components/PriorityBanner.tsx
  - id: openwiki-source-d56d2d8714fc1dd717c3eb7d
    resource: repo://src/main/frontend/src/hooks/engagementReaction.ts
  - id: openwiki-source-2b59042c287fbfa60bd5ed68
    resource: repo://src/main/frontend/src/hooks/useInterest.ts
  - id: openwiki-source-dff2d10e92f2b5124ace0f9e
    resource: repo://src/main/frontend/src/hooks/usePriorityArticles.ts
  - id: openwiki-source-29e9fa5f6bc0b5fa74b710a7
    resource: repo://src/main/frontend/src/utils/interest.ts
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
  - id: openwiki-source-e8441d910270910ebcaa60e2
    resource: repo://src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
  - id: openwiki-source-bfd9e6b64553ce72d0555fce
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - id: openwiki-source-cef6cf34b0ee266bb62a613f
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java
  - id: openwiki-source-de3e8d1e24d1b39e66e69043
    resource: repo://src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java
  - id: openwiki-source-4805f4a78501939aafe493fa
    resource: repo://src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
generated: { by: "openwiki/0.9.0", at: "2026-11-08T14:57:22.664Z" }
---

# Architecture Overview

## Backend package structure

Base package: `org.bartram.myfeeder`. Entry point: `MyfeederApplication` (`@EnableScheduling`, `@ConfigurationPropertiesScan`).

```
config/       MyfeederProperties (myfeeder.* config), RestClientConfig (User-Agent customizer), SpaForwardController,
              InterestScoringConfig (dedicated scoring executor), TypeSafeConfig (owns the TypeSafeClient bean),
              JevEventLogging (resilience4j event → log bridge for the "jev" breaker/retry)
model/        Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount,
              InterestProfile, InterestTopic
repository/   Spring Data JDBC repositories (one per model), @Query-based custom queries, plus
              ArticleScoreStore / ArticleFeedbackStore / InterestScoreQueries for interest scoring and feedback
parser/       FeedParser (ROME for RSS/Atom + Jackson for JSON Feed), ParsedFeed/ParsedArticle records, OpmlFeed/OpmlParseException
service/      FeedService, ArticleService, FeedPollingService, FeedFetcher, FolderService, BoardService, RetentionService,
              OpmlService, OpmlImportService — plus interest scoring: InterestService, InterestPreviewService,
              InterestRescoreService, InterestStatusService, InterestQuestions, ArticleScoringService,
              ArticleFeedbackService, ArticleStateBuilder, ScoringQueue, PriorityService, TopicSuggestionService,
              InterestScoringListener
integration/  RaindropService, RaindropApiClient(Impl), RaindropConfig — see integrations/raindrop.md
              JevApiClient(Impl), JevJudgment, JevNotConfiguredException — see integrations/jev.md
event/        FeedSavedEvent, FeedDeletedEvent, ArticlesIngestedEvent — after-commit scheduling/scoring signals
controller/   REST controllers + typed request records + PaginatedResponse + GlobalExceptionHandler, plus
              InterestController, InterestPreviewController, InterestRescoreController, InterestStatusController,
              TopicSuggestionController and the PriorityPage cursor-paging record (served from ArticleController)
scheduler/    FeedPollingScheduler — dynamic per-feed polling with backoff
```

This is a classic layered architecture (controller → service → repository), with **two cross-cutting mechanisms** worth understanding before changing anything:

1. The **event-driven feed scheduler** (see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)) and the **single fetch path** through `FeedFetcher` (used by both subscribe and poll — never issue a raw `RestClient` call to fetch feed content).
2. The **dedicated interest-scoring executor**, described below (see [Interest Scoring Workflow](../workflows/interest-scoring.md) for the end-to-end pipeline).

Persistence uses **Spring Data JDBC, not JPA**: entities use `@Table`/`@Id` from `org.springframework.data.annotation`, there is no lazy loading, and custom queries require `@Query` (no derived query methods). See [Domain Concepts](../domain/concepts.md) for the schema.

### The interest-scoring executor

Newly ingested articles must be scored off the request/polling thread without an unbounded backlog building up. `InterestScoringConfig` registers a single-threaded, bounded-queue `ThreadPoolTaskExecutor` under the bean name `InterestScoringConfig.EXECUTOR` (`"interestScoringExecutor"`):

- Core and max pool size are both `myfeeder.interest.concurrency` (default 1); queue capacity is `myfeeder.interest.queue-capacity` (default 1000).
- The bean is registered with `defaultCandidate = false` so it never satisfies Boot's `@ConditionalOnMissingBean(Executor.class)` and silently replace the `applicationTaskExecutor`; consumers inject it explicitly with `@Qualifier(InterestScoringConfig.EXECUTOR)`.
- The default **abort** rejection policy is kept deliberately: when the queue is full, `ThreadPoolTaskExecutor` throws `TaskRejectedException` rather than discarding the task. `ScoringQueue` catches that exception and releases the article id from its in-flight tracking set, so the periodic sweep can re-enqueue the article later instead of losing it forever (a discarding policy would leave the id marked in-flight permanently).
- No `@Async` annotation is used anywhere; work is handed to the executor explicitly by `ScoringQueue.submit`/`submitIngested`, called from `InterestScoringListener` (an `@TransactionalEventListener` on `ArticlesIngestedEvent`, `AFTER_COMMIT`) and from the periodic sweep.
- `ScoringQueue` tracks in-flight article ids in a `ConcurrentHashMap`-backed set so an id already queued or running is never submitted twice, and never blocks or throws back to its caller on a full queue.

This gives interest scoring the same event-driven-plus-background-worker shape as feed polling, but as an entirely separate mechanism with its own executor, queue, and retry-on-reject semantics — the two must not be confused when reasoning about concurrency or backpressure.

### Resilience4j convention: breaker + retry live on the API-client bean, never the service

Both outbound integrations — Raindrop (`RaindropApiClientImpl`) and Jev/TypeSafe (`JevApiClientImpl`) — follow the same Resilience4j placement rule:

- `@CircuitBreaker` and `@Retry` are placed on methods of a dedicated API-client bean (`RaindropApiClientImpl.listCollections`/`createBookmark`, `JevApiClientImpl.judge`), **never** on the service that calls it (`RaindropService`, `ArticleScoringService`). Spring AOP proxies only intercept calls that arrive from *outside* the bean; a method on the service calling another method on the same service (self-invocation) would bypass the proxy and silently skip both aspects. Putting the annotations on a separate client bean guarantees every call crosses the proxy boundary.
- Aspect order is fixed project-wide in `application.yaml`: `resilience4j.circuitbreaker.circuit-breaker-aspect-order: 1` and `resilience4j.retry.retry-aspect-order: 2`. Lower order wraps outer, so the circuit breaker is always the **outer** aspect: it wraps the retry and therefore records exactly one outcome per logical call (success/failure/short-circuit), rather than one outcome per retry attempt.
- Each client instance configures its own named Resilience4j instance (`raindrop`, `jev`) with its own `ignore-exceptions` list so that expected/benign exceptions (e.g. `RaindropNotConfiguredException`, `JevNotConfiguredException`, validation-shaped TypeSafe exceptions) never count as circuit-breaker failures.
- `JevEventLogging` is a small `@Configuration` that subscribes to the `jev` `CircuitBreakerRegistry`/`RetryRegistry` event publishers purely to log state transitions and exhausted retries (fixed text, never exception messages or API keys) — it does not reconfigure the aspects.

When adding a new outbound integration, mirror this shape: a dedicated `*ApiClient`/`*ApiClientImpl` pair carrying the Resilience4j annotations, called by a service that contains no resilience annotations of its own.

## Configuration

`MyfeederProperties` (prefix `myfeeder`) binds configuration groups from `application.yaml`:
- `myfeeder.polling.*` — `default-interval-minutes`, `max-interval-minutes`, `backoff-threshold` (drives [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md))
- `myfeeder.retention.*` — `full-content-days`, `cleanup-cron` (drives `RetentionService`)
- `myfeeder.raindrop.*` — `api-base-url`, `api-token` (env `MYFEEDER_RAINDROP_API_TOKEN`) — see [Raindrop Integration](../integrations/raindrop.md)
- `myfeeder.interest.*` — interest-scoring configuration, validated at startup via `MyfeederProperties implements Validator`:
  - `window-days` (default 14) — the eligibility window; only articles published/fetched within this many days of "now" are scored (`eligibilityCutoff()`).
  - `concurrency` (default 1) and `queue-capacity` (default 1000) — size the `interestScoringExecutor` described above.
  - `sweep-batch-size`, `sweep-delay`, `sweep-initial-delay` — pace the periodic re-sweep that re-submits eligible unscored/failed articles.
  - `blend.*` — `profile-points` (100), `learn-rate` (2), `learned-cap` (20); `blend.engagement.*` — `open-weight`/`save-weight`/`cap` (engagement-based learning, startup-validated: cap `0` disables it, otherwise `0 <= open-weight < save-weight < 1` and `0 < cap < learned-cap`); `blend.tiers.*` — `high`/`neutral` badge thresholds served by `/api/interest/status`.
  - `suggestions.near-miss` (default 0.35, startup-validated to `(0, 0.5]`) — the gap-discovery near-miss threshold used by `TopicSuggestionService`.
- `spring.ai.typesafe.*` (bound to `TypeSafeProperties`) — `api-key` (env `MYFEEDER_TYPESAFE_API_KEY`, optional: blank disables Jev calls but never fails startup), `model` (pinned), `timeout`, `retry.max-retries: 0` (Resilience4j's `jev` instance is the only retry layer; the SDK's own retry is disabled). `TypeSafeConfig` defines the `TypeSafeClient` bean itself (rather than letting the TypeSafe starter auto-configure it) specifically so a blank API key disables scoring instead of crashing the pod, and gives the client its own Reactor Netty transport/timeouts cloned from the shared `RestClient.Builder`.

See [Interest Scoring Workflow](../workflows/interest-scoring.md) and [Jev Integration](../integrations/jev.md) for how these values drive the scoring pipeline end to end.

## HTTP layer conventions

- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` responses: `NotFoundException`→404, `IllegalArgumentException`→400, `OpmlParseException`→400, `IllegalStateException`→409, `FeedParseException`/`FeedFetchException`→422, `RaindropNotConfiguredException`→503. Services should throw the specific exception type rather than have controllers catch/translate.
- Request bodies are typed Java records per endpoint (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MarkReadRequest`, `TopicRequest`, `FeedbackRequest`) rather than raw `Map` bodies — this was a deliberate refactor (commit `5136a3e`) to get compile-time binding checks.
- List endpoints return `PaginatedResponse<T>` (`{items, nextCursor}`); see [Domain Concepts](../domain/concepts.md) for the cursor/sort rules that make this correct. The Priority list (`GET /api/articles/priority`) uses a distinct `PriorityPage` record with the same `{items, nextCursor}` shape, but its cursor is an opaque base64url-encoded `(score, date, id)` tuple — the exact sort key the client was served — rather than a plain id, because the underlying ranking can change between pages.
- API surface: `/api/feeds`, `/api/articles` (including `/api/articles/priority`), `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`, `/api/interest` (profile, topics, learned weights, preview, rescore, status, suggestions).

## Frontend

`src/main/frontend/` is a React 19 + TypeScript SPA built with Vite.

- **Layout**: three resizable panels — feed tree (`FeedPanel`), article list (`ArticleList`/`BoardArticleList`, or `PriorityList` on the `/priority` route), reading pane (`ReadingPane`), composed in `App.tsx` under `AppShell`.
- **Data layer**: `src/api/*.ts` are thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, interest, version); `src/hooks/*.ts` wrap them in TanStack Query hooks (`useArticles`, `useFeeds`, `useFolders`, `useBoards`, `useOpml`, plus the interest-scoring hooks below). `App.tsx` composes route components (`FeedArticles`, `FolderArticles`, `StarredArticles`, `AllArticles`, `BoardArticles`) and the `/priority` route that each derive TanStack Query filters from Zustand preferences (sort order, hide-read).
- **State**: Zustand stores in `src/stores/` — `uiStore` (selection/focus/panel state), `preferencesStore` (localStorage-persisted settings: theme, font sizes, sort order, hide-read), `priorityStore` (Priority list's "ranking changed" hint and related transient state), and `feedbackStore`. `preferencesStore` uses Zustand's `persist`; adding a field with a default does **not** retroactively apply to existing users (see Testing/Gotchas) — needs a `merge` function or version migration.
- **Keyboard shortcuts**: `useKeyboardShortcuts` hook implements vim-style navigation (`j`/`k`/`n`/`p`/`m`/`s`/`o`/`b`/`v`/`r`) plus `g`-prefixed chords (`ga`, `gs`, `gb`) and `+`/`-` font-size adjustment on the focused panel, with Priority-specific handling (`isPriority`, `onPriorityNextPage`, `onPriorityRefresh`) when the hook is driven by the Priority list. It resolves the "current article" via `useArticle(selectedArticleId)` (direct fetch by ID) so single-article actions work even on views whose visible list is queried differently from the list this hook receives — a known partial fix (see backlog bug on `j`/`k` navigation).
- **Themes**: 6 themes (3 dark/3 light) in `src/themes.ts`, applied via `useTheme`, persisted in `preferencesStore`.
- **Priority view**: `PriorityList` renders the ranked-unread-articles route (`/priority`), backed by `usePriorityArticles` (a TanStack `useInfiniteQuery` with `staleTime: Infinity` and focus/reconnect refetch disabled, so only an explicit refresh re-ranks the list and ordinary reads/votes patch rows in place via `patchPriorityArticle` instead of invalidating). `PriorityBanner` renders at most one status line above the list (not configured / cold start / scoring paused / N waiting), sourced from `useInterestStatus` with no extra polling of its own. `refreshPriority` (in `usePriorityArticles.ts`) resets the query to page 1, scrolls to top, and clears the "ranking changed" hint.
- **Interests dialog**: `InterestsDialog` is the profile/topic management surface opened from Settings, from the Priority banner's "Set up interests" action, or via a one-shot "create topic from article" draft from the reading pane. It composes `TopicRow` (one topic's name/description/weight editor plus live preview), `ScoreRow` (an article's score summary with a "forget engagement" action), `WhyBreakdown` (the per-topic score contribution breakdown, using `PROFILE_LEVEL_LABELS` and `formatSigned`), `FeedbackBar` (the thumbs-vote control, with `NarrowPicker` for limiting a 👎 to specific matched topics), and `InterestBadge` (the shared 0–100 tier-colored score pill used across `ArticleList`, `BoardArticleList`, `PriorityList`, and `ScoreRow`).
- **Interest-scoring hooks**: `useInterest.ts` exposes `useInterestStatus` (polls every 15s only while articles are configured, not cold-start, and have a positive `eligibleUnscored` count), `useInterestTiers` (reads the same cached status query's `tiers` field without ever polling, falling back to `DEFAULT_TIERS`), `useInterestProfile`/`useSaveInterestProfile`, `useInterestTopics`/`useCreateInterestTopic`/update/delete, and `useLearnedTopics` (each topic's learned adjustment, kept under its own query key so it never disturbs unsaved row edits). `usePriorityArticles.ts` owns the Priority list's infinite query, row deduplication (`dedupeById`), in-place patching (`patchPriorityArticle`), and the `refreshPriority` re-rank action. `useFeedback.ts` (`useVoteFeedback`) serializes thumbs votes through one mutation scope (`FEEDBACK_SCOPE`) so presses apply in order. `useEngagement.ts` (`useOpenOriginal`, `useForgetEngagement`) records "open original"/"forget" engagement, firing the original URL open synchronously (to preserve user-gesture popup permissions) before recording the engagement. `engagementReaction.ts` centralizes the shared post-engagement/vote cache invalidation (`invalidateAfterLearnedChange`, `afterEngagement`): it refreshes learned weights, suggestions, and article lists, and patches (never refetches) the Priority row so the ranked list is never silently reshuffled by a background action.
- **Tier thresholds**: `TierContext` (in `src/utils/interest.ts`) is a React context carrying the server's badge-tier thresholds (`{high, neutral}`, served by `/api/interest/status` and read via `useInterestTiers`); `App.tsx`'s `MainLayout` provides the live value, and any `InterestBadge` rendered outside that provider (e.g. in isolated tests) falls back to `DEFAULT_TIERS` (`{high: 70, neutral: 40}`).
- **Build/embed**: `npm run build` outputs to `src/main/resources/static/`; the Gradle `npmBuild` task wires this into `./gradlew build` so the SPA ships inside the Spring Boot jar. `SpaForwardController` (in `config/`) forwards non-API routes to the SPA's `index.html` for client-side routing. See [Operations Runbook](../operations/runbook.md) for the full build/deploy sequence — the frontend must be rebuilt with `clean` before every image build or a stale bundle ships.

## Infrastructure dependencies

- **PostgreSQL**: primary datastore, Flyway-migrated (`src/main/resources/db/migration/`), external at `pg.bartram.org` in production (not deployed by the Helm chart — see [Operations Runbook](../operations/runbook.md)).
- **Redis**: backs `@Cacheable` (currently only `RaindropService.listCollections`) via Spring Cache abstraction.
- **TypeSafe/Jev**: an external AI judging service accessed through `TypeSafeClient` (configured by `TypeSafeConfig`), gated end-to-end by `myfeeder.ai.typesafe.api-key` being present; absence disables scoring without affecting startup or any other feature. See [Jev Integration](../integrations/jev.md).
- **Docker**: required locally for both `./gradlew test` (Testcontainers-backed repository/integration tests) and `./gradlew bootRun` (Compose-managed Postgres/Redis). `./gradlew bootTestRun` runs the app itself against Testcontainers-managed services with no external Compose needed.
