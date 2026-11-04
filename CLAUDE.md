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

- Stack and versions: see `build.gradle.kts` and `src/main/frontend/package.json`. Persistence is Spring Data JDBC (not JPA); the Spring AI Anthropic starter is on the classpath but unused by application code.
- **Jev (interest scoring)**: `org.springaicommunity:spring-ai-starter-typesafe` (explicit version 0.1.0 via `typesafeVersion` in `build.gradle.kts`; it is not in the Spring AI BOM); the app owns the `TypeSafeClient` bean (see Jev Scoring and Resilience)
- **Feed extensions**: Media RSS / iTunes / GeoRSS namespaces require `com.rometools:rome-modules:2.1.0` (separate artifact from `rome`); accessed via `entry.getModule(MediaEntryModule.URI)` etc.

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
  - Opens (both ↗ Open Original buttons and `o`) go through `useOpenOriginal` in `hooks/useEngagement.ts`: `window.open` first, then a fire-and-forget bodyless `PUT /api/articles/{id}/engagement/open` (204, idempotent, 404 for an unknown id) whose errors are swallowed. A successful PUT runs the post-engagement reaction (see below).
  - Saves are captured server-side after the user's write, through `ArticleEngagementStore.recordQuietly` (one `ON CONFLICT DO NOTHING` statement; a failure is a WARN with the ids and the exception class only):
    - STAR in `ArticleService.updateState`, on an unstarred→starred change only
    - BOARD in `BoardService.addArticle`, after the save-or-already-present branch, so re-adds credit
    - RAINDROP in `RaindropService`, right after `createBookmark` returns, outside the breaker bean
  - These three services must stay free of any transaction boundary, so a failed insert can never abort the user's save.
  - Engagement is sticky: unstar, board removal and board delete keep it; an article or feed delete cascades it.
  - Forget is `DELETE /api/articles/{id}/engagement` (204, no tombstone), reached from the reading pane's `Engaged: … · Forget` line in `ScoreRow` (shown when the article is scored or engaged; `useForgetEngagement`).
  - Post-engagement reaction (Phase 10): open, star (a `starred: true` update only), board add (including Read Later and `b`), Raindrop save and Forget each call `afterEngagement(qc, id, onPriority)` from `hooks/engagementReaction.ts` after the action succeeds.
    - It refetches `['article', id]` with `qc.query({ ..., staleTime: 0, retry: false })` and invalidates `['interest', 'learned']`, `['articles']` and, off Priority, the other open by-id articles (never `['article', n, 'extracted']`). `invalidateAfterLearnedChange` is that refresh set, shared with the vote hook.
    - On `/priority` (`useMatch('/priority')`) it patches the row's `interestScore` through `patchPriorityArticle` and sets `rankingChanged` only when the refetched score differs from the loaded row's.
    - `['priority']` is never invalidated, reset or refetched, so the list being triaged never re-sorts. The reaction never rejects; a failed refetch is silent.
    - Unstar, board removal and read toggles do not react.
    - An unloaded Priority row whose score rises above the cursor can be skipped until refresh (D-09, documented on `InterestScoreQueries.priorityPageAfter`); the "Ranking changed" hint prompts that refresh. `PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh` proves that after a refresh the position, badges and "Why N?" agree (SC-3).
  - `GET /api/articles/{id}` carries `engagement` (`[]` when none); list responses omit it.
  - Nothing else records engagement: in-body links, Copy Link, selection, reader view, auto-mark-read, bulk mark-read, OPML import and polling.
  - Since Phase 9 the learned CTE reads `article_engagement` (see Interest Ranking → Engagement learning). `V7EngagementMigrationTest.rankingSqlReadsEngagementButNotDismissals` guards that the ranking SQL never reads `topic_suggestion_dismissal`.
- **API endpoints**: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`, `/api/interest`

## Frontend

Frontend conventions and gotchas live in `src/main/frontend/CLAUDE.md` (loaded when working under that directory). Type-check with `npx tsc -b` from `src/main/frontend/`: plain `tsc --noEmit` passes even with errors.

## Infrastructure

- `compose.yaml` defines Postgres and Redis for local dev (`bootRun`)
- `TestcontainersConfiguration` provides Postgres and Redis containers for tests and `bootTestRun`
- Docker must be running for both tests and local development

## Deployment

Use the `release` skill (`.claude/skills/release/SKILL.md`) to cut, build, push and deploy a release; it carries the pipeline order and the deploy gotchas. Never move or overwrite a release tag: `./gradlew release` already creates `vX.Y.Z`, so `/gsd-complete-milestone` must not add a second tag for a milestone named after a release.

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

- **Schema**: `V6__interest_scoring.sql` creates all six interest tables (`interest_profile` singleton row 1, `interest_topic`, `article_score`, `article_topic_score`, `article_feedback`, `article_feedback_topic`). V7 (v0.3.0) adds `article_engagement` (Phase 8 capture, read by the learned CTE since Phase 9) and `topic_suggestion_dismissal` (Phase 11 gap discovery; read and written only by `TopicSuggestionStore`), and carries the whole v0.3.0 schema
- **InterestService** owns the profile/topic limits (service constants, fixed-text 400s) and the version rules; `isColdStart()` is the single cold-start predicate (blank profile AND zero topics) — callers never reimplement it
- **InterestQuestions** and **ArticleStateBuilder** are pure static builders shared by the preview and the scorer, so the preview judges exactly what scoring sends
- **InterestStatusService**/`InterestStatus` serve `{configured, breakerState, coldStart, eligibleUnscored, failed, tiers}` (breaker state read from `CircuitBreakerRegistry.circuitBreaker("jev")`; `tiers` = `{high, neutral}` from `myfeeder.interest.blend.tiers.*`, D-13); both counts come from one query over eligible articles (unread, inside the window): `eligibleUnscored` = no score row or a FAILED row under 3 attempts, `failed` = FAILED with all 3 attempts used; later fields are appended, existing ones are never renamed
- **InterestPreviewService**/`TopicPreviewResponse` judge one description against one article with one `JevApiClient.judge` call and return `{noul, model}`; validation runs before `judge()`, there is no transaction and no service retry, and the preview persists nothing
- **Routes** under `/api/interest`: `GET|PUT /profile`, `GET|POST /topics`, `GET /topics/learned`, `PUT|DELETE /topics/{id}`, `GET /status`, `POST /preview`, `GET /suggestions`, `PUT /suggestions/{articleId}/dismissal` (bodyless, 204, idempotent, 404 for an unknown article), `GET|POST /rescore` (the POST requires the JSON body `{"confirm": true}` — a bodyless/form/text POST gets 415 and a missing or false `confirm` gets 400, which blocks cross-site triggering; it is 409 when Jev is unconfigured or in cold start). `POST /topics` accepts an optional `sourceArticleId` that marks that article's suggestion handled in the same transaction as the topic insert; `PUT /topics/{id}` ignores it
- **Scores are discarded if the rubric changed mid-call**: after `judge()` returns, `ArticleScoringService.score` re-reads the profile version and each topic's version; if any changed, it stores nothing and records no attempt, so the sweep re-scores the article. Changing only a topic's name or weight doesn't count. Keep this check when changing the scoring write path.
- **Status polling**: `useInterestStatus` (`hooks/useInterest.ts`) polls `/status` every 15s only while `configured && !coldStart && eligibleUnscored > 0` (the same condition that shows the "N waiting" line); it keeps polling while the breaker is open because the breaker recovers on its own.
- **Thumbs feedback**: `PUT /api/articles/{id}/feedback` (JSON-only, `{vote: 1|-1, topicIds?}`) and `DELETE` store or remove one `article_feedback` row (plus `article_feedback_topic` picks when narrowed) through `ArticleFeedbackService`, which returns `{article, scored, effects[]}` with each matched topic's effective weight before and after the write. The learned adjustment is derived in the `learned`/`engaged`/`eng_learned`/`eff`/`eff2` CTEs of `InterestScoreQueries` (for votes: learn-rate x sum of vote x hinge, capped at +/-learned-cap, sign-clamped, within +/-50): a vote never writes a topic's weight and never calls Jev. `GET /api/articles/{id}` carries `feedback`; `GET /api/interest/topics/learned` serves base/learned/effective per topic, plus `thumbsLearned`, `engagementLearned` and `engagementAtCap`. Each effect also carries `engagementReplaced`: true when the write changed both the topic's capped engagement part and its uncapped thumbs part (`learnedRaw`), with a 5e-7 tolerance, and false on the unpicked topics of a narrowed 👎. The toast reads it as `(replaces engagement)`, or `(engagement restored)` for a removal. Learned values shift when a Re-score or a topic/feed delete removes the nouls a vote relied on (derived model).
- **Engagement learning (v0.3.0, Phase 9)**: engagement is a second derived term in `LEARNED_CTE`, a small capped up-vote computed at query time.
  - Four rules: thumbs first (w_thumbs is settled before engagement), a separate additive cap below learned-cap, negative-base topics skipped, and SCORED articles only.
  - `engaged` collapses `article_engagement` to one MAX strength per article (OPEN_ORIGINAL → open-weight; STAR, BOARD and RAINDROP → save-weight). It drops any article with an `article_feedback` row, so a vote replaces that article's engagement on every topic, and deleting the vote restores it.
  - `eng_learned` counts SCORED articles only, with no age or read window. Engaged-but-unscored articles stay ineligible for scoring (`ArticleScoreStore` does not look at engagement).
  - eng_raw = learn-rate x sum(strength x hinge), and eng = GREATEST(0, LEAST(cap, eng_raw)). The zero floor means engagement never lowers a weight. Both are 0 for a topic with a negative base.
  - `w_thumbs` is the v0.2.1 weight (clamp of base + thumbs). `w` applies the sign clamp and +/-50 once, to base + thumbs + eng, so a clamped down-vote must be paid back before engagement shows.
  - The split is computed in SQL by subtracting 6-decimal rounded values:
    - "Why N?" rows and `TopicContribution` carry `thumbsWeight` + `engagementWeight` = `learnedWeight` (= weight − base).
    - `TopicWeight`, `TopicLearned` and `TopicEffect` keep `learned` = `thumbsLearned` + `engagementLearned` (both capped, before the clamp).
  - `LearnedLimit.of(w, learnedCap, engagementCap)` returns the first match of LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE (both on base + thumbs + engagement) → ENGAGEMENT_CAP (only when cap > 0) → NONE (Phase 10 D-10). `LearnedLimit.engagementAtCap` is the single rule behind both ENGAGEMENT_CAP and `TopicLearned.engagementAtCap`, so the flag can be true while a binding clamp or range is the reported limit.
  - UI wording (Phase 10):
    - The vote toast never words ENGAGEMENT_CAP, and ENGAGEMENT_CAP alone never lists a topic. A binding limit's note wins over the replaced-engagement note.
    - The Interests line reads `Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5`, with `engaged … at max` when `engagementAtCap` (and `at max` / `at min` inside the votes part at the learned cap).
    - "Why N?" topic rows read `(+10 +2.0 votes +1.5 engaged)`: each part is the server's split field rounded on its own, a part that rounds to 0.0 is omitted, the tooltip lists every part, and the rows carry no limit notes.
  - Constants: `myfeeder.interest.blend.engagement.open-weight` / `save-weight` / `cap` ship as 0.25 / 0.5 / 8 in main `application.yaml`.
    - Calibrated in Phase 12 (revisit): the D-03 fallback kept the D-01 values on thin data (13 counted engaged articles of the 30 the floor asks for, 0 topics with engagement); see `.planning/phases/12-calibration-release/12-CALIBRATION.md`.
    - The test yaml keeps the D-01 values because `FeedbackApiIntegrationTest` and `PriorityApiIntegrationTest` assume them. A calibrated value that differs goes in `src/test/resources/application-dev.yaml` (`DevProfileConfigTest`); none differs today, so the dev overlay carries no engagement keys.
    - `MyfeederProperties implements Validator` refuses startup with the fixed text `ENGAGEMENT_INVALID` unless cap is 0 (disabled, whatever the weights) or 0 <= open < save < 1 and 0 < cap < learned-cap.
    - The constants apply at query time: no Jev call, no weight write, no re-score.
  - Proofs:
    - `InterestLearnedGridTest`: the exact split on a 1,456-cell grid.
    - `InterestScoreQueriesEngagementTest`.
    - `InterestScoreQueriesZeroEngagementTest`: cap 0 equals the frozen v0.2.1 blend in `src/test/resources/interest/v021-unread-blend.sql`.
    - `InterestScoreQueriesLatencyTest`: each query stays within 10 x baseline + 250 ms with about 20k engagement rows.
  - **Engagement tuning (v0.3.1, Phase 12)**: use the `interest-calibration` skill (`.claude/skills/interest-calibration/SKILL.md`). Outcome (2026-10-02): the D-03 fallback kept 0.25 / 0.5 / 8, marked revisit (see `.planning/phases/12-calibration-release/12-CALIBRATION.md`). Approved values go in main `application.yaml` (plus `application-dev.yaml` when they differ from the test yaml), never in Helm `--set` or env (D-14).
- **Gap discovery (v0.3.0, Phase 11)**: `GET /api/interest/suggestions` lists engaged articles that no topic covers, as `{items: [{articleId, title, feedTitle, interestScore}], total}`.
  - Predicate (D-08, `TopicSuggestionStore.CANDIDATES`): an engagement of any kind whose first recording (per kind) falls in the last 30 days (`TopicSuggestionService.WINDOW_DAYS`); `article_engagement` keeps the first `created_at` per (article, kind), so repeating an engagement of the same kind does not refresh the window (WR-01, accepted); a SCORED row; no vote; no dismissal row; and a best noul over all topic rows (either weight sign; no rows counts as 0) below `myfeeder.interest.suggestions.near-miss`.
  - Constant: `myfeeder.interest.suggestions.near-miss` is 0.35, an identical literal in main and test yaml, with the dev overlay untouched. `MyfeederProperties` refuses startup with the fixed text `SUGGESTIONS_INVALID` unless 0 < near-miss <= 0.5. It applies at query time, so a change needs no re-score.
  - Order: badge ascending, from `InterestScoreQueries.displayScores` in a second step (the ranking SQL is untouched and never reads the dismissal table), then latest engagement newest first, then id descending. At most 10 items (`MAX_SUGGESTIONS`) plus `total`; a candidate whose badge vanished between the two steps is dropped and not counted.
  - Lifecycle: Dismiss (`PUT /suggestions/{articleId}/dismissal`) writes DISMISSED; a topic saved with a `sourceArticleId` writes TOPIC_CREATED inside `InterestService.createTopic`'s transaction, so the two commit or roll back together, and a cap or validation 400 writes nothing. `TopicSuggestionStore.handle` is one `INSERT ... SELECT ... ON CONFLICT (article_id) DO NOTHING`: it keeps the first reason, and a stale article id inserts nothing and never blocks the save. Deleting the topic keeps the row (Phase 8 D-14), and there is no un-dismiss, so a handled article never returns, even after a later engagement.
  - Jev is never called: not to list, dismiss or create a topic from a suggestion.
  - Proofs: `TopicSuggestionStoreTest`, `TopicSuggestionServiceTest`, `TopicSuggestionApiIntegrationTest` (including `aFailedDismissalInsertRollsBackTheTopic` for atomicity and `neverCallsJev` after every test).
  - UI: the Interests dialog's "Suggested topics" section sits after Topics and before the Re-score footer, and is hidden when the list is empty, loading or failed. The heading reads `Suggested topics (N of M)` only when the list is capped. Each row shows the badge, the title as plain text, the feed name, Create topic and Dismiss.
  - Create topic calls `addDraft({ description: title.trim().slice(0, 500), weight: 20, sourceArticleId })` in the open dialog and saves nothing. While that draft is unsaved the row reads "Draft added" with no actions; saving hides it (a saved row keeps its `sourceArticleId`, so it stays hidden before the refetch), and discarding the draft makes it active again.
  - At 25 topic rows (drafts count), Create topic is disabled with the tooltip "You have 25 topics, the maximum." and Dismiss still works.
  - The reading-pane "Create topic from article" draft also carries `sourceArticleId`, so there is one draft path. `TopicRow` sends `sourceArticleId` only on create, never on a PUT.
  - Freshness: `['interest', 'suggestions']` uses staleTime 0, so it refetches on every open. It is invalidated by `invalidateAfterLearnedChange` (votes and every engagement reaction), topic create and delete, Re-score and Dismiss.
  - UI proofs: the `Suggested topics` describe in `InterestsDialog.test.tsx`, plus `useInterest.test.tsx` and `engagementReaction.test.ts`.

## Jev Scoring and Resilience

- **App-owned client bean**: `config/TypeSafeConfig` defines the `TypeSafeClient` bean, so the starter's `@ConditionalOnMissingBean` backs off and a blank key never crashes startup; it only logs `TypeSafe Jev not configured; interest scoring disabled`. The key is read only through a Supplier and is never logged. `baseUrl` is set explicitly, so the SDK never falls back to its `TYPESAFE_*` env vars. The bean gets a Jev-only Reactor Netty request factory on a `clone()` of the auto-configured `RestClient.Builder` (connect 5s, read = `spring.ai.typesafe.timeout`, 30s), which keeps the User-Agent customizer without changing any other bean's transport. Never build a second `TypeSafeClient` or reuse its transport for feeds.
- **Single retry layer**: `spring.ai.typesafe.retry.max-retries: 0`. The Resilience4j `jev` retry is the only retry: 3 attempts, waiting 1s then 2s via `TypeSafeConfig.jevRetryInterval`, except that a 429's `retry-after-ms` is honored, clamped to 0..10s (`MAX_RETRY_AFTER_MS`). It retries only 429, 5xx/overloaded and connection or timeout failures (`retry-exceptions`), never 400/422/401/403. The `jev` breaker is the outer aspect (order 1 vs 2): COUNT_BASED window 20, minimum 10 calls, 50% failure rate, slow calls over 15s at 50%, OPEN for 60s, then an automatic HALF_OPEN. 401/403 are recorded (not in `ignore-exceptions`), so a bad key opens the breaker. Never re-enable SDK retries: two layers multiply 429s.
- **Jev event log lines**: `config/JevEventLogging` (D-15) subscribes to the `jev` retry and breaker and logs `Jev retry attempt N after <Exception> (waiting M ms)` (INFO), `Jev retries exhausted after N attempts: <Exception>` (WARN) and `Jev circuit breaker <FROM>_TO_<TO> (failure rate x%, slow-call rate y%)` (WARN), with simple class names and numbers only, never a message, body or key. Grep prod with `kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '`.
- **Scoring executor**: bean `interestScoringExecutor` (`InterestScoringConfig.EXECUTOR`, threads `jev-score-`). Core = max = `myfeeder.interest.concurrency` (1), `queue-capacity` 1000, and the default abort policy is kept on purpose: `ScoringQueue` catches `TaskRejectedException` and releases the id so the sweep can retry it. `defaultCandidate = false` keeps Boot's `applicationTaskExecutor` alive. Inject it with `@Qualifier(InterestScoringConfig.EXECUTOR)`; there is no `@Async`.
- **Sweep**: `InterestScoringSweep` runs with `fixedDelay` = `myfeeder.interest.sweep-delay` (PT2M) after `sweep-initial-delay` (PT1M). Each run enqueues min(free queue room, `sweep-batch-size` 50) eligible articles, newest first. It skips when Jev is unconfigured, the `jev` breaker is OPEN/FORCED_OPEN, or in cold start; HALF_OPEN runs, so scoring resumes on its own. New articles are also enqueued on ingest (`InterestScoringListener` → `ScoringQueue.submitIngested`), which the batch size does not cap.
- **Eligibility window**: `read = false AND COALESCE(published_at, fetched_at) > now − window-days` (14), with no score row or a FAILED row under 3 attempts (`ArticleScoreStore.ELIGIBLE` / `NEEDS_SCORING` / `MAX_ATTEMPTS`). Transient failures (429, 5xx, connection, open breaker, missing key, 401/403) write no row and use no attempt (`ScoringFailure.isTransient`), so a 429 never creates a FAILED row; other failures count, bounding an article at 3 billed attempts.
- **Throttle levers**: if 429s or breaker trips become a problem (D-08), lower `myfeeder.interest.sweep-batch-size` and/or lengthen `sweep-delay` in `application.yaml`. The emergency lever without a release is `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20` (restarts the pod). Once the value is in yaml, remove the override with `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-`, because a Helm upgrade's three-way merge can keep an out-of-band env var. For an emergency stop, redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset. Keep `concurrency: 1` (D-07) and never add the SDK retry layer back.
- **Tier thresholds and tuning**: `myfeeder.interest.blend.tiers.high|neutral` (`MyfeederProperties.Interest.Blend.Tiers` → `TierThresholds`) is served as `tiers` on `/api/interest/status`. The frontend reads it through `useInterestTiers` → `TierContext` (provided in `App.tsx`) → `InterestBadge`, falling back to `DEFAULT_TIERS` 70/40 until status loads. Every blend constant is applied at query time, so changing one needs no Jev re-score. To tune, use the `interest-calibration` skill; keep the test yaml at profile-points 100 / tiers 70 / 40 (`InterestScoreQueriesTest` fixtures assume them). Never tune through Helm `--set` or env overrides (D-14).

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
- **Spring Data JDBC ≠ JPA**: No lazy loading, no derived query methods, no `@Entity` — use `@Id` from `org.springframework.data.annotation`, `@Table` from `org.springframework.data.relational.core.mapping`, and `@Query` for custom queries
- **Jackson 3.x imports**: Must use `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`
- **FeedPollingScheduler is event-driven**: feed mutations publish `FeedSavedEvent`/`FeedDeletedEvent`; the scheduler (re-)registers or cancels via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. New feed-mutating paths publish the event — never call `registerFeed`/`cancelFeed` directly.
- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`**: the `BuildProperties` bean is absent in controller-slice tests. Either inject with `@Autowired(required = false)` and handle `null`, or `@Import` a test config that exposes a `BuildProperties` bean from a `Properties` literal. Same caveat applies to other `info.*` / actuator auto-configured beans.
- **Outbound `RestClient` transport and User-Agent**: the auto-configured `RestClient` transport is Reactor Netty (`ReactorClientHttpRequestFactory`), pinned by the explicit `reactor-netty-http` dependency in `build.gradle.kts` (Spring AI 2.0.1 no longer brings it in transitively; without it Boot silently falls back to the JDK client) and guarded by `HttpClientConfigurationTest`. Without myfeeder's customizer, requests carry the HTTP library's default User-Agent, which Vercel and other CDNs rate-limit by returning HTTP 200 with body `{"data":"too many requests"}`. `config/RestClientConfig.java` registers a `RestClientCustomizer` that sets `myfeeder/<version>` on every outbound `RestClient.Builder` — don't bypass it by constructing a raw `RestClient.builder()` without going through the auto-configured bean.
- **`FeedParser.parse` rejects empty results**: if a response yields no title and no articles, it throws `FeedParseException` (mapped to 422 by `GlobalExceptionHandler`). This catches "200 OK with garbage" responses that would otherwise save a NULL-title feed and violate `feed.title NOT NULL`.

## Test Patterns

- **Unit tests**: Mockito with `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for services
- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean` for dependencies
- **Repository tests**: `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)` for real Postgres
- **Parser tests**: Plain unit tests with sample feed files in `src/test/resources/feeds/`
- **Integration test**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` verifying all beans wire correctly
- **Migration tests**: Flyway runs at `@DataJdbcTest` startup, so tests can't insert a legacy-shape row and "re-run" the migration. Pattern: insert a legacy-shape row via `JdbcTemplate.update(...)`, then re-execute the migration SQL manually against it, then assert the post-migration shape. Example: `V4StripRaindropApiTokenMigrationTest`
- Test `application.yaml` must include `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key`

## Homelab

Shared infra facts (registry, pg, k3s nodes, LB IPs, deploy conventions): @../HOMELAB.md

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->


<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:46cd31e7 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/core-concepts/sync-concepts.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   bd dolt push
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->
