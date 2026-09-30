# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**myfeeder** is a Spring Boot 4.0.8 feed aggregator/reader application using Java 25. It subscribes to RSS, Atom, and JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and can forward saved articles to Raindrop.io.

## Build & Run Commands

```bash
# Build (includes frontend via Gradle npmBuild task)
./gradlew build

# Run backend tests (requires Docker for Testcontainers)
./gradlew test

# Run frontend tests
cd src/main/frontend && npm test

# Run a single test class
./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"

# Run app with Testcontainers-managed services (no external Docker Compose needed)
./gradlew bootTestRun

# Run app with Docker Compose services
./gradlew bootRun

# Frontend dev server (proxies /api to :8080)
cd src/main/frontend && npm run dev
```

## Architecture

- **Framework**: Spring Boot 4.0.8 with Spring MVC (servlet stack)
- **Language**: Java 25, Lombok for boilerplate reduction
- **Database**: PostgreSQL via Spring Data JDBC (not JPA), Flyway migrations
- **Caching**: Redis via Spring Cache abstraction
- **AI**: Spring AI Anthropic starter on the classpath (not yet used by any application code)
- **Jev (interest scoring)**: `org.springaicommunity:spring-ai-starter-typesafe` (explicit version 0.1.0 via `typesafeVersion` in `build.gradle.kts`; it is not in the Spring AI BOM); the app owns the `TypeSafeClient` bean (see Jev Scoring and Resilience)
- **Resilience**: Resilience4j circuit breaker via Spring Cloud
- **HTTP Client**: Spring RestClient for outbound calls
- **Monitoring**: Spring Boot Actuator
- **Feed Parsing**: ROME 2.1.0 for RSS/Atom, Jackson 3.x for JSON Feed
- **Feed extensions**: Media RSS / iTunes / GeoRSS namespaces require `com.rometools:rome-modules:2.1.0` (separate artifact from `rome`); accessed via `entry.getModule(MediaEntryModule.URI)` etc.

## Package Structure

```
org.bartram.myfeeder
├── config/           MyfeederProperties (self-validates the engagement constants), RestClientConfig (User-Agent customizer), SpaForwardController, TypeSafeConfig (app-owned TypeSafeClient, jev retry interval), InterestScoringConfig (jev-score executor), JevEventLogging (jev retry/breaker log lines)
├── model/            Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount, InterestProfile, InterestTopic, InterestBreakdown, ArticleFeedback, EngagementKind
├── repository/       Feed/Article/Folder/Board/BoardArticle/IntegrationConfig/InterestProfile/InterestTopic repositories, ArticleScoreStore, ArticleFeedbackStore, ArticleEngagementStore, InterestScoreQueries (blend/learned CTEs: Priority sort, badge, breakdown)
├── parser/           FeedParser (ROME + Jackson), ParsedFeed, ParsedArticle, FeedParseException, OpmlFeed, OpmlParseException
├── service/          FeedService, ArticleService, FeedPollingService, FolderService, BoardService, RetentionService, OpmlService, OpmlImportService, OpmlImportResult, FeedFetcher, FetchResult, FeedUrlValidator, ArticleExtractionService, ExtractedContent, NotFoundException, FeedFetchException; interest: InterestService, InterestStatusService, InterestStatus, TierThresholds, InterestPreviewService, InterestRescoreService, RescoreCount, ArticleScoringService, ScoringQueue, ScoringFailure, InterestScoringListener, InterestQuestions, ArticleStateBuilder, PriorityService, ScoreBreakdowns, ArticleFeedbackService, FeedbackResult, LearnedLimit, TopicLearned, TopicPreviewResponse
├── integration/      RaindropService, RaindropApiClient/RaindropApiClientImpl (with Resilience4j @CircuitBreaker + @Retry), RaindropConfig, RaindropCollection, RaindropNotConfiguredException, JevApiClient/JevApiClientImpl (@CircuitBreaker(name = "jev") + @Retry(name = "jev")), JevJudgment, JevNotConfiguredException
├── event/            FeedSavedEvent, FeedDeletedEvent (after-commit feed scheduling events), ArticlesIngestedEvent (new article ids for interest scoring)
├── controller/       Feed/Article/Folder/Board/IntegrationConfig/Opml/Version/Interest/InterestStatus/InterestPreview/InterestRescore controllers + PaginatedResponse + PriorityPage + GlobalExceptionHandler + request DTOs (SubscribeRequest, MarkReadRequest, ArticleStateRequest, FeedUpdateRequest, ProfileUpdateRequest, TopicRequest, TopicPreviewRequest, FeedbackRequest, RescoreRequest + board/folder request records)
├── scheduler/        FeedPollingScheduler (dynamic per-feed scheduling with backoff), InterestScoringSweep (jev scoring sweep)
└── MyfeederApplication.java (@EnableScheduling, @ConfigurationPropertiesScan)
```

## Key Behaviors

- **Feed scheduling is event-driven**: `FeedService`/`OpmlImportService` publish `FeedSavedEvent`/`FeedDeletedEvent`; `FeedPollingScheduler` listens with `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. New feed-mutating code paths just publish the event — never call the scheduler directly.
- **FeedPollingService** deduplicates articles by GUID on upsert
- **FeedFetcher is the single feed-fetch path**: both subscribe and polling go through it (conditional ETag/If-Modified-Since requests, HTTP errors → `FeedFetchException` → 422). It returns the raw body bytes plus the Content-Type header; charset resolution happens in `FeedParser` (an explicit header charset wins, else BOM/XML-prolog detection via ROME's `XmlReader`, else UTF-8). It also validates the URL via `FeedUrlValidator` (SSRF guard: http/https only; rejects loopback/link-local/RFC1918/any-local/multicast → 400) and bounds the body read to `DEFAULT_MAX_FEED_BYTES` (10 MiB → 422). Don't fetch feed content with a raw `RestClient` — both protections would be lost.
- **FeedPollingScheduler** uses `ApplicationReadyEvent` to register all feeds at startup; after every poll it re-reads the feed's error state and replaces the task when the effective interval changed, so exponential backoff engages (and clears) while the app runs
- **Article sort order**: Articles are sorted by `COALESCE(published_at, fetched_at)` not by `id`. Batch-fetched articles get sequential IDs but varied publication dates, so `ORDER BY id` does not produce chronological order. Cursor pagination uses composite `(published_at, id)` comparison — the cursor is still a single article ID, but the service looks up the cursor article's date for the SQL comparison. Paginated endpoints return `PaginatedResponse` (`{items, nextCursor}` — field is `items`, not `articles`); the limit+1 trimming lives in `PaginatedResponse.of(...)`, so controllers fetch `limit + 1` and delegate.
- **ReadingPane fetches by ID**: The reading pane uses `useArticle(id)` to fetch the selected article directly (`GET /api/articles/{id}`), not by searching through the paginated list query. This avoids filter/sort mismatches between the article list and reading pane.
- **Reader view (extraction)**: `GET /api/articles/{id}/extracted-content` fetches the article's original page through `FeedFetcher` (keeping the SSRF guard/size cap), extracts readable content with Readability4J (`ArticleExtractionService`), and caches it in `article.extracted_content` (aged out together with `content` by RetentionService). The ReadingPane auto-enables reader view when a feed item has no content/summary and offers a manual "📖 Reader View" toggle; extracted HTML is sanitized with DOMPurify `FORBID_TAGS/FORBID_ATTR: ['style']` so publisher styling (dark pages) is replaced by the app theme.
- **RetentionService** is a `@Scheduled` cron job — config under `myfeeder.retention.*`
- **OpmlService** has XXE protection enabled — maintain this when modifying XML parsing
- **OpmlImportService** publishes `FeedSavedEvent` per new feed; the scheduler's `@TransactionalEventListener(AFTER_COMMIT)` registers them post-commit (no manual `TransactionSynchronization`)
- **Engagement capture (v0.3.0)**:
  - Opens (both ↗ Open Original buttons and `o`) go through `useOpenOriginal` in `hooks/useEngagement.ts`: `window.open` first, then a fire-and-forget bodyless `PUT /api/articles/{id}/engagement/open` (204, idempotent, 404 for an unknown id) whose errors are swallowed.
  - Saves are captured server-side after the user's write, through `ArticleEngagementStore.recordQuietly` (one `ON CONFLICT DO NOTHING` statement; a failure is a WARN with the ids and the exception class only):
    - STAR in `ArticleService.updateState`, on an unstarred→starred change only
    - BOARD in `BoardService.addArticle`, after the save-or-already-present branch, so re-adds credit
    - RAINDROP in `RaindropService`, right after `createBookmark` returns, outside the breaker bean
  - These three services must stay free of any transaction boundary, so a failed insert can never abort the user's save.
  - Engagement is sticky: unstar, board removal and board delete keep it; an article or feed delete cascades it.
  - Forget is `DELETE /api/articles/{id}/engagement` (204, no tombstone), reached from the reading pane's `Engaged: … · Forget` line in `ScoreRow` (shown when the article is scored or engaged; `useForgetEngagement`).
  - `GET /api/articles/{id}` carries `engagement` (`[]` when none); list responses omit it.
  - Nothing else records engagement: in-body links, Copy Link, selection, reader view, auto-mark-read, bulk mark-read, OPML import and polling.
  - Since Phase 9 the learned CTE reads `article_engagement` (see Interest Ranking → Engagement learning). `V7EngagementMigrationTest.rankingSqlReadsEngagementButNotDismissals` guards that the ranking SQL never reads `topic_suggestion_dismissal`.
- **API endpoints**: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`, `/api/interest`

## Frontend

- **Location**: `src/main/frontend/` (React + TypeScript, built with Vite)
- **Tech Stack**: React 19, TypeScript, TanStack Query, Zustand, React Router v6, DOMPurify
- **Layout**: Three-panel (feed tree / article list / reading pane) with resizable dividers
- **Build**: `npm run build` outputs to `src/main/resources/static/`; Gradle `npmBuild` task wires this into `./gradlew build`
- **Dev workflow**: `./gradlew bootTestRun` (backend) + `cd src/main/frontend && npm run dev` (Vite on :5173, proxies `/api` to :8080). `TestMyfeederApplication` activates the `dev` profile so live settings load (see the `bootTestRun` gotcha); export `MYFEEDER_TYPESAFE_API_KEY` first to enable Jev scoring
- **Tests**: Vitest + React Testing Library; run with `cd src/main/frontend && npm test`
- **Type-check**: use `npx tsc -b` from `src/main/frontend/` — plain `tsc --noEmit` returns success even with errors because the root `tsconfig.json` has `files: []` and uses project references
- **Key conventions**:
  - API client in `src/api/` — thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, interest)
  - TanStack Query hooks in `src/hooks/` — one file per domain (useArticles, useFeeds, useFolders, useBoards, useOpml, useInterest (interest status, tiers and rubric), useFeedback, usePriorityArticles, useEngagement (`useOpenOriginal`, `useForgetEngagement`))
  - Zustand stores in `src/stores/` — `uiStore` (selection, panel state), `preferencesStore` (localStorage-persisted settings)
  - Components in `src/components/` — AppShell, FeedPanel, ArticleList, ReadingPane, BoardArticleList, BoardManager, SettingsDialog, ShortcutOverlay, Toast, dialogs
  - Keyboard shortcuts: vim-style (j/k/n/p/m/s/o/b/v/r), g-chords, managed by `useKeyboardShortcuts` hook
  - Theme system: 6 themes (3 dark, 3 light) defined in `src/themes.ts`, applied via `useTheme` hook, persisted in `preferencesStore`

## Infrastructure

- `compose.yaml` defines Postgres and Redis for local dev (`bootRun`)
- `TestcontainersConfiguration` provides Postgres and Redis containers for tests and `bootTestRun`
- Docker must be running for both tests and local development
- Flyway migrations: `V1__initial_schema.sql` (feeds, articles, integration_configs), `V2__folders_boards_and_feed_folder.sql` (folders, boards, board_articles, feed.folder_id), `V3__article_image_url.sql`, `V4__strip_raindrop_api_token.sql`, `V5__article_extracted_content.sql`, `V6__interest_scoring.sql` (interest_profile, interest_topic, article_score, article_topic_score, article_feedback, article_feedback_topic), `V7__engagement.sql` (article_engagement, topic_suggestion_dismissal)

## Deployment

### Cut a release (full pipeline)

"Cut a release / create and deploy a new release" means this sequence, in this order (details on each step in the bullets below):

```bash
./gradlew release                       # 1. cut + push the release tag (axion) — BEFORE building, so the jar gets the release version
./gradlew clean bootJar                 # 2. build the jar (clean forces fresh frontend embed)
VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')
docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:$VERSION .
docker push registry.bartram.org/bartram/myfeeder:$VERSION
./deploy.sh $VERSION                    # needs MYFEEDER_PG_PASSWORD + MYFEEDER_ANTHROPIC_API_KEY (MYFEEDER_RAINDROP_API_TOKEN and MYFEEDER_TYPESAFE_API_KEY optional)
kubectl -n myfeeder rollout status deploy/myfeeder
kubectl -n myfeeder logs deploy/myfeeder --tail=20   # verify clean startup
```

For a minor release (new schema, dependency or view, as for 0.2.0) cut the tag with `./gradlew release -Prelease.versionIncrementer=incrementMinor`; the default increments the patch.

Ordering matters: `release` before `bootJar` (else the jar is stamped `-SNAPSHOT`); always pass `$VERSION` to `deploy.sh` explicitly (no arg → axion computes the *next* snapshot, which won't match any pushed image).

### Reference

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys app + Redis; Postgres is external at `pg.bartram.org`
- **Build image** (Dockerfile, not buildpacks — see Gotchas): first build the jar on the host with `./gradlew clean bootJar` (compiles + embeds the frontend via `npmBuild`→`processResources`, stamps the axion-release version into build-info), then `docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:<version> .` (the `Dockerfile` only packages `build/libs/*.jar` into a JRE runtime; `--provenance=false` keeps the pushed artifact a plain single-platform image instead of a buildkit attestation index). Use `clean` so the latest frontend bundle is included; `<version>` comes from `./gradlew currentVersion -q`.
- **Push image**: `docker push registry.bartram.org/bartram/myfeeder:<version>` (build does NOT push)
- **Cut release tag**: `./gradlew release` (creates tag locally via axion-release, pushes via git CLI — see Gotchas)
- **GSD milestone tags vs release tags**: `./gradlew release` already creates `vX.Y.Z`, so a milestone named after the release it shipped in (e.g. v0.2.1) must not get a second tag from `/gsd-complete-milestone` (its tag step skips an existing tag; never move or overwrite a release tag)
- **Deploy**: `./deploy.sh [version]` (requires `MYFEEDER_PG_PASSWORD` and `MYFEEDER_ANTHROPIC_API_KEY` env vars; `MYFEEDER_RAINDROP_API_TOKEN` and `MYFEEDER_TYPESAFE_API_KEY` are optional — a blank TypeSafe key disables interest scoring and the app still starts; every key or token is passed through `helm --set`, so it must not contain `,` or `\`; no arg → axion computes the *next* snapshot version, which won't match a built image, so for chart-only redeploys against a release tag pass it explicitly: `./deploy.sh 0.1.2`)


## Key Conventions

- Base package: `org.bartram.myfeeder`
- Uses Spring Data JDBC (not JPA) -- entities use `@Id` from `org.springframework.data.annotation` and `@Table` from `org.springframework.data.relational.core.mapping`, not `jakarta.persistence`
- Gradle Kotlin DSL for build configuration
- BOM-managed versions for Spring AI and Spring Cloud (do not specify versions on individual dependencies)
- Resilience4j: put `@CircuitBreaker(name = "...")` + `@Retry(name = "...")` on external service calls; they only take effect because `spring-boot-starter-aspectj` is on the classpath. The execution order comes from `resilience4j.circuitbreaker.circuit-breaker-aspect-order: 1` and `resilience4j.retry.retry-aspect-order: 2` (lower = outer; both keys sit outside `instances` and apply to every breaker/retry), so the breaker wraps the retry and records one outcome per logical call, not one per attempt; without them Resilience4j's defaults put the retry outermost. Put them on the **API-client bean** (e.g. `RaindropApiClientImpl`), not on the service — so business validation (not-configured/disabled/no-collection checks in `RaindropService`) runs outside the breaker and only the real HTTP call is wrapped. (Self-invocation bypasses the AOP proxy, so the annotated methods must live on a separate bean that the service calls.) Config in `application.yaml` under `resilience4j.circuitbreaker.instances` and `resilience4j.retry.instances`
- **Resilience4j fallback re-throw pattern**: a `@CircuitBreaker` fallback wraps everything as a 5xx (`IllegalStateException`→409) by default — the client's fallback `instanceof`-checks and rethrows `RaindropNotConfiguredException` (503) before wrapping everything else. Business-rule exceptions (400 "no collection", 409 "disabled") never reach the fallback because that validation happens in `RaindropService` before the client call. Also list `RaindropNotConfiguredException` under `ignore-exceptions` in both the circuit-breaker and retry instances so a missing token never opens the breaker or burns retries
- **GlobalExceptionHandler mappings**: `NotFoundException` → 404 (missing path entity), `IllegalArgumentException` → 400 (Bad Request), `OpmlParseException` → 400, `IllegalStateException` → 409 (Configuration error), `FeedParseException` → 422, `FeedFetchException` → 422 (remote returned an HTTP error), `RaindropNotConfiguredException` → 503, `JevNotConfiguredException` → 503 ("Jev not configured"), `CallNotPermittedException` → 503 ("Jev unavailable", the jev breaker is open), `TypeSafeBadRequestException`/`TypeSafeUnprocessableEntityException` → 422 ("Jev rejected the request"), any other `TypeSafeException` → 503 ("Jev request failed"); the Jev details are fixed text and never echo a TypeSafe message or body. Throw the right type from services and the controller layer doesn't need try/catch
- Spring Data JDBC does not support derived query methods like JPA — use `@Query` annotation for custom queries

## Interest Ranking

- **Schema**: `V6__interest_scoring.sql` creates all six interest tables (`interest_profile` singleton row 1, `interest_topic`, `article_score`, `article_topic_score`, `article_feedback`, `article_feedback_topic`). V7 (v0.3.0) adds `article_engagement` (Phase 8 capture, read by the learned CTE since Phase 9) and `topic_suggestion_dismissal` (used by Phase 11), and carries the whole v0.3.0 schema
- **InterestService** owns the profile/topic limits (service constants, fixed-text 400s) and the version rules; `isColdStart()` is the single cold-start predicate (blank profile AND zero topics) — callers never reimplement it
- **InterestQuestions** and **ArticleStateBuilder** are pure static builders shared by the preview and the scorer, so the preview judges exactly what scoring sends
- **InterestStatusService**/`InterestStatus` serve `{configured, breakerState, coldStart, eligibleUnscored, failed, tiers}` (breaker state read from `CircuitBreakerRegistry.circuitBreaker("jev")`; `tiers` = `{high, neutral}` from `myfeeder.interest.blend.tiers.*`, D-13); both counts come from one query over eligible articles (unread, inside the window): `eligibleUnscored` = no score row or a FAILED row under 3 attempts, `failed` = FAILED with all 3 attempts used; later fields are appended, existing ones are never renamed
- **InterestPreviewService**/`TopicPreviewResponse` judge one description against one article with one `JevApiClient.judge` call and return `{noul, model}`; validation runs before `judge()`, there is no transaction and no service retry, and the preview persists nothing
- **Routes** under `/api/interest`: `GET|PUT /profile`, `GET|POST /topics`, `GET /topics/learned`, `PUT|DELETE /topics/{id}`, `GET /status`, `POST /preview`, `GET|POST /rescore` (the POST requires the JSON body `{"confirm": true}` — a bodyless/form/text POST gets 415 and a missing or false `confirm` gets 400, which blocks cross-site triggering; it is 409 when Jev is unconfigured or in cold start)
- **Scores are discarded if the rubric changed mid-call**: after `judge()` returns, `ArticleScoringService.score` re-reads the profile version and each topic's version; if any changed, it stores nothing and records no attempt, so the sweep re-scores the article. Changing only a topic's name or weight doesn't count. Keep this check when changing the scoring write path.
- **Status polling**: `useInterestStatus` (`hooks/useInterest.ts`) polls `/status` every 15s only while `configured && !coldStart && eligibleUnscored > 0` (the same condition that shows the "N waiting" line); it keeps polling while the breaker is open because the breaker recovers on its own.
- **Thumbs feedback**: `PUT /api/articles/{id}/feedback` (JSON-only, `{vote: 1|-1, topicIds?}`) and `DELETE` store or remove one `article_feedback` row (plus `article_feedback_topic` picks when narrowed) through `ArticleFeedbackService`, which returns `{article, scored, effects[]}` with each matched topic's effective weight before and after the write. The learned adjustment is derived in the `learned`/`engaged`/`eng_learned`/`eff`/`eff2` CTEs of `InterestScoreQueries` (for votes: learn-rate x sum of vote x hinge, capped at +/-learned-cap, sign-clamped, within +/-50): a vote never writes a topic's weight and never calls Jev. `GET /api/articles/{id}` carries `feedback`; `GET /api/interest/topics/learned` serves base/learned/effective per topic, plus `thumbsLearned` and `engagementLearned`. Learned values shift when a Re-score or a topic/feed delete removes the nouls a vote relied on (derived model).
- **Engagement learning (v0.3.0, Phase 9)**: engagement is a second derived term in `LEARNED_CTE`, a small capped up-vote computed at query time.
  - `engaged` collapses `article_engagement` to one MAX strength per article (OPEN_ORIGINAL → open-weight; STAR, BOARD and RAINDROP → save-weight). It drops any article with an `article_feedback` row, so a vote replaces that article's engagement on every topic, and deleting the vote restores it.
  - `eng_learned` counts SCORED articles only, with no age or read window. Engaged-but-unscored articles stay ineligible for scoring (`ArticleScoreStore` does not look at engagement).
  - eng_raw = learn-rate x sum(strength x hinge), and eng = GREATEST(0, LEAST(cap, eng_raw)). The zero floor means engagement never lowers a weight. Both are 0 for a topic with a negative base.
  - `w_thumbs` is the v0.2.1 weight (clamp of base + thumbs). `w` applies the sign clamp and +/-50 once, to base + thumbs + eng, so a clamped down-vote must be paid back before engagement shows.
  - The split is computed in SQL by subtracting 6-decimal rounded values:
    - "Why N?" rows and `TopicContribution` carry `thumbsWeight` + `engagementWeight` = `learnedWeight` (= weight − base).
    - `TopicWeight`, `TopicLearned` and `TopicEffect` keep `learned` = `thumbsLearned` + `engagementLearned` (both capped, before the clamp).
  - `LearnedLimit.of(w, learnedCap, engagementCap)` returns the first match of LEARNED_CAP → ENGAGEMENT_CAP (only when cap > 0) → SIGN_CLAMP/WEIGHT_RANGE on base + thumbs + engagement → NONE. The UI falls through to its default wording for ENGAGEMENT_CAP until Phase 10 words it.
  - Constants: `myfeeder.interest.blend.engagement.open-weight` / `save-weight` / `cap` are 0.25 / 0.5 / 8 (D-01), identical literals in main and test yaml, with the dev overlay untouched.
    - `MyfeederProperties implements Validator` refuses startup with the fixed text `ENGAGEMENT_INVALID` unless cap is 0 (disabled, whatever the weights) or 0 <= open < save < 1 and 0 < cap < learned-cap.
    - The constants apply at query time: no Jev call, no weight write, no re-score.
  - Proofs:
    - `InterestLearnedGridTest`: the exact split on a 1,456-cell grid.
    - `InterestScoreQueriesEngagementTest`.
    - `InterestScoreQueriesZeroEngagementTest`: cap 0 equals the frozen v0.2.1 blend in `src/test/resources/interest/v021-unread-blend.sql`.
    - `InterestScoreQueriesLatencyTest`: each query stays within 10 x baseline + 250 ms with about 20k engagement rows.

## Jev Scoring and Resilience

- **App-owned client bean**: `config/TypeSafeConfig` defines the `TypeSafeClient` bean, so the starter's `@ConditionalOnMissingBean` backs off and a blank key never crashes startup; it only logs `TypeSafe Jev not configured; interest scoring disabled`. The key is read only through a Supplier and is never logged. `baseUrl` is set explicitly, so the SDK never falls back to its `TYPESAFE_*` env vars. The bean gets a Jev-only Reactor Netty request factory on a `clone()` of the auto-configured `RestClient.Builder` (connect 5s, read = `spring.ai.typesafe.timeout`, 30s), which keeps the User-Agent customizer without changing any other bean's transport. Never build a second `TypeSafeClient` or reuse its transport for feeds.
- **Single retry layer**: `spring.ai.typesafe.retry.max-retries: 0`. The Resilience4j `jev` retry is the only retry: 3 attempts, waiting 1s then 2s via `TypeSafeConfig.jevRetryInterval`, except that a 429's `retry-after-ms` is honored, clamped to 0..10s (`MAX_RETRY_AFTER_MS`). It retries only 429, 5xx/overloaded and connection or timeout failures (`retry-exceptions`), never 400/422/401/403. The `jev` breaker is the outer aspect (order 1 vs 2): COUNT_BASED window 20, minimum 10 calls, 50% failure rate, slow calls over 15s at 50%, OPEN for 60s, then an automatic HALF_OPEN. 401/403 are recorded (not in `ignore-exceptions`), so a bad key opens the breaker. Never re-enable SDK retries: two layers multiply 429s.
- **Jev event log lines**: `config/JevEventLogging` (D-15) subscribes to the `jev` retry and breaker and logs `Jev retry attempt N after <Exception> (waiting M ms)` (INFO), `Jev retries exhausted after N attempts: <Exception>` (WARN) and `Jev circuit breaker <FROM>_TO_<TO> (failure rate x%, slow-call rate y%)` (WARN), with simple class names and numbers only, never a message, body or key. Grep prod with `kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '`.
- **Scoring executor**: bean `interestScoringExecutor` (`InterestScoringConfig.EXECUTOR`, threads `jev-score-`). Core = max = `myfeeder.interest.concurrency` (1), `queue-capacity` 1000, and the default abort policy is kept on purpose: `ScoringQueue` catches `TaskRejectedException` and releases the id so the sweep can retry it. `defaultCandidate = false` keeps Boot's `applicationTaskExecutor` alive. Inject it with `@Qualifier(InterestScoringConfig.EXECUTOR)`; there is no `@Async`.
- **Sweep**: `InterestScoringSweep` runs with `fixedDelay` = `myfeeder.interest.sweep-delay` (PT2M) after `sweep-initial-delay` (PT1M). Each run enqueues min(free queue room, `sweep-batch-size` 50) eligible articles, newest first. It skips when Jev is unconfigured, the `jev` breaker is OPEN/FORCED_OPEN, or in cold start; HALF_OPEN runs, so scoring resumes on its own. New articles are also enqueued on ingest (`InterestScoringListener` → `ScoringQueue.submitIngested`), which the batch size does not cap.
- **Eligibility window**: `read = false AND COALESCE(published_at, fetched_at) > now − window-days` (14), with no score row or a FAILED row under 3 attempts (`ArticleScoreStore.ELIGIBLE` / `NEEDS_SCORING` / `MAX_ATTEMPTS`). Transient failures (429, 5xx, connection, open breaker, missing key, 401/403) write no row and use no attempt (`ScoringFailure.isTransient`), so a 429 never creates a FAILED row; other failures count, bounding an article at 3 billed attempts.
- **Throttle levers**: if 429s or breaker trips become a problem (D-08), lower `myfeeder.interest.sweep-batch-size` and/or lengthen `sweep-delay` in `application.yaml`. The emergency lever without a release is `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20` (restarts the pod). Once the value is in yaml, remove the override with `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-`, because a Helm upgrade's three-way merge can keep an out-of-band env var. For an emergency stop, redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset. Keep `concurrency: 1` (D-07) and never add the SDK retry layer back.
- **Tier thresholds and tuning**: `myfeeder.interest.blend.tiers.high|neutral` (`MyfeederProperties.Interest.Blend.Tiers` → `TierThresholds`) is served as `tiers` on `/api/interest/status`. The frontend reads it through `useInterestTiers` → `TierContext` (provided in `App.tsx`) → `InterestBadge`, falling back to `DEFAULT_TIERS` 70/40 until status loads. Every blend constant is applied at query time, so changing one needs no Jev re-score. To tune, run `scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL ...` (a read-only psql replay of the verbatim `InterestScoreQueries` blend, drift-guarded by `InterestCalibrationReplaySqlTest`; needs `MYFEEDER_PG_PASSWORD` and the LAN) and record the result in `.planning/phases/07-rollout-calibration/07-CALIBRATION.md`. Put tuned values in main `application.yaml` and `src/test/resources/application-dev.yaml`, and keep the test yaml at profile-points 100 / tiers 70 / 40, because `InterestScoreQueriesTest` fixtures assume them. The replay also reads `ENGAGEMENT_OPEN_WEIGHT`, `ENGAGEMENT_SAVE_WEIGHT` and `ENGAGEMENT_CAP` (defaults 0.25 / 0.5 / 8, validated as numbers before connecting). Its psql `-v` names equal the JdbcClient parameters (guarded by `driverPassesEveryBlendParameter`), and its learned section reports `eng_raw`, `eng` and `eng_at_cap`. The engagement values are identical literals in main and test yaml: a Phase 12 tuning changes both, or puts the tuned value in `application-dev.yaml` (`DevProfileConfigTest` enforces this). Never tune through Helm `--set` or env overrides (D-14).

## Spring Boot 4 / Jackson 3.x Notes

- Jackson 3.x **databind** moved to `tools.jackson.databind.*` (`ObjectMapper`, `JsonNode`, `JsonMapper`). Spring Boot auto-configures `tools.jackson.databind.ObjectMapper` as a bean.
- Jackson 3.x **annotations** did NOT move — `@JsonProperty`, `@JsonIgnoreProperties`, `@JsonIgnore`, `@JsonCreator` etc. are still in `com.fasterxml.jackson.annotation.*` (jackson-annotations 2.x is a transitive dep of jackson-databind 3.x). There is no `tools.jackson.annotation` package.
- Test annotations `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest` are in `org.springframework.boot.*.test.autoconfigure` packages (e.g., `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`).
- Test starter dependencies follow the pattern `spring-boot-starter-<module>-test` (e.g., `spring-boot-starter-webmvc-test`).
- `RestClientCustomizer` is in `org.springframework.boot.restclient` (Boot 4 moved it out of `org.springframework.boot.web.client`). Spring Boot auto-applies any `RestClientCustomizer` bean to the auto-configured `RestClient.Builder` injected into services.
- Outbound HTTP timeouts/redirects for the auto-configured `RestClient` are set via `spring.http.clients.connect-timeout` / `read-timeout` / `redirects` (metadata lives in `spring-boot-http-client-*.jar`, not `-autoconfigure`). The singular `spring.http.client.*` keys were deprecated in Boot 4.0.0 and are silently not bound, so the old claim that 5s/30s applied was false until the 4.0.8 upgrade renamed them. The settings apply globally to every `RestClient` (feed fetches + Raindrop). Currently 5s/30s in `application.yaml`, guarded by `HttpClientConfigurationTest`.
- `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7. Use `HttpStatus.valueOf(422)` (or `UNPROCESSABLE_CONTENT` once it's exposed).

## Gotchas

- **Docker required**: Must be running for both `./gradlew test` (Testcontainers) and `./gradlew bootRun` (Docker Compose)
- **`bootTestRun` loads the test `application.yaml`, not main**: it runs on the test classpath, where `src/test/resources/application.yaml` shadows `src/main/resources/application.yaml` (Spring Boot loads only the first `classpath:/application.yaml`). The test yaml is offline by design: no TypeSafe key placeholder, Jev base-url `http://127.0.0.1:9`, first sweep after `PT1H`. `TestMyfeederApplication` therefore activates the `dev` profile, and `src/test/resources/application-dev.yaml` restores main's key placeholder, the real Jev base-url, the `PT1M` first sweep, the 5s/30s HTTP timeouts, the app name and the Raindrop token placeholder. The test yaml plus that overlay must resolve every main `application.yaml` key to main's value, and no test may activate `dev`; `DevProfileConfigTest` enforces both. Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test` (e.g. via `.envrc`): either would make the suite call the billed Jev API. `./gradlew bootRun` (Docker Compose) loads main `application.yaml` directly.
- **Zustand persist + new preferences**: Adding a new field to `preferencesStore` with a default value only applies to fresh installs. Existing users with a `myfeeder-prefs` localStorage key get `undefined` for the new field (Zustand merges stored state over defaults). Use a `merge` function or version migration if the default must apply to everyone.
- **Spring Data JDBC ≠ JPA**: No lazy loading, no derived query methods, no `@Entity` — use `@Id` from `org.springframework.data.annotation`, `@Table` from `org.springframework.data.relational.core.mapping`, and `@Query` for custom queries
- **Jackson 3.x imports**: Must use `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`
- **FeedPollingScheduler is event-driven**: feed mutations publish `FeedSavedEvent`/`FeedDeletedEvent`; the scheduler (re-)registers or cancels via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. New feed-mutating paths publish the event — never call `registerFeed`/`cancelFeed` directly.
- **MaxDirectMemorySize (historical, Paketo-only)**: the old Paketo buildpack hardcoded `-XX:MaxDirectMemorySize=10M`, which starved Netty (Lettuce/Redis); the Helm chart overrides it via the `JDK_JAVA_OPTIONS` env var. The Dockerfile (`eclipse-temurin:25-jre`) has no such cap — direct memory defaults are container-aware — so the override is no longer required, but the chart still sets `JDK_JAVA_OPTIONS` and the JVM honors it. Tune JVM flags via `JDK_JAVA_OPTIONS` (auto-read by the `java -jar` entrypoint), not `_JAVA_OPTIONS`.
- **Frontend not in image**: the image is `docker build`-ed from `build/libs/*.jar`, so the frontend must already be embedded in that jar. Always build with `./gradlew clean bootJar` before `docker build` — `clean` forces `npmBuild`→`processResources` to repackage the current SPA bundle. A stale jar in `build/libs/` will ship an old frontend.
- **SNAPSHOT tags + pullPolicy**: `imagePullPolicy: IfNotPresent` causes k8s to reuse stale images when the same SNAPSHOT tag is pushed. Use `Always` during development; `IfNotPresent` is only safe with immutable release tags.
- **Gradle terminal escapes in scripts**: `./gradlew currentVersion -q` outputs terminal control sequences. In shell scripts, pipe through `grep 'Project version'` before parsing to avoid contaminating variables.
- **`./gradlew release` push uses git CLI**: axion-release's bundled jgit can't read OpenSSH-format keys. `release` and `pushRelease` in `build.gradle.kts` clear their built-in push action and finalize via a `gitPushRelease` Exec task. Don't replace this with raw axion auth config.
- **`./gradlew release` tag push can fail on a stray git-lfs pre-push hook**: a global `git lfs install` leaves a `pre-push` hook in `.git/hooks/` that runs `git lfs pre-push` even though this repo has **no** LFS content. It intermittently fails the push with "Remote origin does not support the Git LFS locking API" / "Unable to verify locks", blocking `gitPushRelease` (and plain `git push`). Fix (persistent, safe — no LFS data here): `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false`. Already set in this clone; re-apply after a fresh re-clone.
- **Helm probes have a startupProbe**: `startupProbe` (5s × 60 = up to 5 min) gates `livenessProbe` and `readinessProbe`. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to liveness — startupProbe is the gate.
- **Clipboard API requires HTTPS**: The app is served over HTTP (`192.168.44.204`), so `navigator.clipboard` is unavailable. Use `document.execCommand('copy')` fallback for clipboard operations.
- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`**: the `BuildProperties` bean is absent in controller-slice tests. Either inject with `@Autowired(required = false)` and handle `null`, or `@Import` a test config that exposes a `BuildProperties` bean from a `Properties` literal. Same caveat applies to other `info.*` / actuator auto-configured beans.
- **Outbound `RestClient` transport and User-Agent**: the auto-configured `RestClient` transport is Reactor Netty (`ReactorClientHttpRequestFactory`), pinned by the explicit `reactor-netty-http` dependency in `build.gradle.kts` (Spring AI 2.0.1 no longer brings it in transitively; without it Boot silently falls back to the JDK client) and guarded by `HttpClientConfigurationTest`. Without myfeeder's customizer, requests carry the HTTP library's default User-Agent, which Vercel and other CDNs rate-limit by returning HTTP 200 with body `{"data":"too many requests"}`. `config/RestClientConfig.java` registers a `RestClientCustomizer` that sets `myfeeder/<version>` on every outbound `RestClient.Builder` — don't bypass it by constructing a raw `RestClient.builder()` without going through the auto-configured bean.
- **`FeedParser.parse` rejects empty results**: if a response yields no title and no articles, it throws `FeedParseException` (mapped to 422 by `GlobalExceptionHandler`). This catches "200 OK with garbage" responses that would otherwise save a NULL-title feed and violate `feed.title NOT NULL`.
- **`pg.bartram.org` is split-horizon DNS**: only the LAN resolver (`192.168.44.6`/pi1) has the `192.168.44.206` record; public resolvers (1.1.1.1, Cloudflare) return no answer. The k3s nodes must use `192.168.44.6` as a resolver (configured in `k3s-ansible` via the `lan_dns_servers` var on the `prereq` role) and a CoreDNS forward block in the `coredns-custom` ConfigMap routes `bartram.org` queries there. cert-manager bypasses cluster DNS entirely via `--dns01-recursive-nameservers-only` so ACME challenges still resolve public records on Cloudflare.
- **Docker 29's containerd image store corrupts buildpack image exports** (pods fail to start with containerd `failed to pull and unpack … wrong diff id "sha256:…" calculated on extraction "sha256:…"`): this is why the build moved off `bootBuildImage` to a `Dockerfile` (see Deployment). Root cause: Docker 29's `dockerd` defaults to the **containerd-backed image store** (`docker info` shows `Storage Driver: overlayfs` + `driver-type io.containerd.snapshotter.v1`), and that store **mis-derives diffIDs when *exporting* certain images** — a known moby bug class ([moby/moby#47150](https://github.com/moby/moby/issues/47150), `docker save` produces wrong diffIDs). For a Paketo `bootBuildImage` image the exported **manifest** listed the wrong blob (the 3 KB `os-release` layer) in the base-layer slots while the config's `rootfs.diff_ids` stayed correct; strict consumers (k3s containerd) re-verify each layer against its diffID and refuse the image. Key scoping facts: it is **not** universal — plain `docker build` images (e.g. the `budget` project on the same engine) export fine; it only bites the **export → strict-pull** path, so the image still **runs locally** (`docker run` uses the daemon's internal store and never re-derives diffIDs) and survives a Docker-only `push`/`pull` (moby's puller is lenient). It reproduces via both `docker push` and `bootBuildImage publish=true`. Fixes, in order of preference: (1) **build via `Dockerfile`** (adopted — immune regardless of image store); (2) set `{"features":{"containerd-snapshotter":false}}` in the docker daemon config to revert to the `overlay2` graphdriver store, if Docker still allows opting out; (3) build with Docker ≤ 28. Emergency stopgap (used once for `0.1.16`): `skopeo copy docker://<image> dir:/tmp/img`, recover the correct base-layer bytes from the run image (`docker save <run-image>`, gunzip layers whose uncompressed sha256 matches the config `diff_ids`), patch the bad manifest slots' `digest`+`size`, re-verify every layer, then `skopeo copy dir:/tmp/img docker://<image>`.

## Test Patterns

- **Unit tests**: Mockito with `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for services
- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean` for dependencies
- **Repository tests**: `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)` for real Postgres
- **Parser tests**: Plain unit tests with sample feed files in `src/test/resources/feeds/`
- **Integration test**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` verifying all beans wire correctly
- **Migration tests**: Flyway runs at `@DataJdbcTest` startup, so tests can't insert a legacy-shape row and "re-run" the migration. Pattern: insert a legacy-shape row via `JdbcTemplate.update(...)`, then re-execute the migration SQL manually against it, then assert the post-migration shape. Example: `V4StripRaindropApiTokenMigrationTest`
- Test `application.yaml` must include `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key`
- **Frontend `vi.mock` of `preferencesStore` is full-replacement** (e.g. `SettingsDialog.test.tsx`) — adding a new export to `preferencesStore` requires updating every mock that consumes it, otherwise tests fail with `No "X" export is defined on the mock`. Prefer `vi.mock(..., async (importOriginal) => ({ ...await importOriginal(), usePreferences: ... }))` for new tests.

## Homelab

Shared infra facts (registry, pg, k3s nodes, LB IPs, deploy conventions): @../HOMELAB.md

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
