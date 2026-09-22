---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
<!-- refreshed: 2026-09-22 -->

# Architecture

**Analysis Date:** 2026-09-22

## System Overview

myfeeder is a Spring Boot feed aggregator with a React frontend. It polls RSS/Atom/JSON feeds, stores articles in PostgreSQL, and forwards selections to Raindrop.io. The architecture uses a three-tier design: REST API controllers, service layer, and data access layer, with an event-driven scheduler for background polling.

```text
┌─────────────────────────────────────────────────────────────────┐
│               React Frontend (Vite + TypeScript)                │
│               Three-panel layout: Feeds / List / Reading Pane   │
│               `src/main/frontend/src/`                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP/JSON
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    REST API Layer (Spring MVC)                   │
│  FeedController / ArticleController / FolderController / etc.    │
│  `src/main/java/org/bartram/myfeeder/controller/`               │
├──────────────────────────┬──────────────────────────────────────┤
│ GlobalExceptionHandler   │ PaginatedResponse                     │
│ Status mapping           │ Cursor-based pagination               │
└──────────────────────────┴──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Service Layer (Business Logic)                │
│  FeedService / ArticleService / FeedPollingService / etc.        │
│  `src/main/java/org/bartram/myfeeder/service/`                  │
│                                                                   │
│  Key services:                                                   │
│  • FeedService — subscribe, update, delete                       │
│  • FeedPollingService — poll feed, deduplicate articles          │
│  • ArticleService — filter, paginate, update state              │
│  • FolderService — organize feeds                                │
│  • BoardService — curate article collections                     │
│  • RaindropService — integration with Raindrop.io               │
│  • ArticleExtractionService — reader view content extraction     │
│  • RetentionService — scheduled cleanup                          │
│  • OpmlService / OpmlImportService — OPML import/export         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
    ┌────────────────┐ ┌────────────┐ ┌─────────────┐
    │  Spring Data   │ │ FeedFetcher│ │ Resilience4j│
    │    JDBC        │ │            │ │ (Raindrop)  │
    │  Repositories  │ │ • SSRF     │ │             │
    │                │ │   guard    │ │ • Circuit   │
    │                │ │ • ETag     │ │   breaker   │
    │                │ │ • 10 MiB   │ │ • Retry     │
    │                │ │   cap      │ │             │
    └────────┬───────┘ └────────────┘ └─────────────┘
             │
             ▼
    ┌─────────────────────┐
    │  PostgreSQL (JDBC)  │
    │                     │
    │  • feed table       │
    │  • article table    │
    │  • folder table     │
    │  • board table      │
    │  • integration      │
    │  • Redis cache      │
    └─────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Feed Controller | REST endpoints for feed CRUD, subscribe, poll, folder moves | `controller/FeedController.java` |
| Article Controller | REST endpoints for article listing, filtering, state updates, Raindrop save | `controller/ArticleController.java` |
| Folder Controller | REST endpoints for folder management | `controller/FolderController.java` |
| Board Controller | REST endpoints for board/collection management | `controller/BoardController.java` |
| Global Exception Handler | Maps domain exceptions to HTTP status codes and problem details | `controller/GlobalExceptionHandler.java` |
| Feed Service | Business logic for feed subscription, updates, deletion; publishes events | `service/FeedService.java` |
| Feed Polling Service | Polls feeds, parses responses, deduplicates articles | `service/FeedPollingService.java` |
| Feed Polling Scheduler | Event-driven task scheduler; registers/cancels polling tasks per feed | `scheduler/FeedPollingScheduler.java` |
| Article Service | Retrieves, filters, paginates articles; updates read/starred state | `service/ArticleService.java` |
| Feed Fetcher | Conditional HTTP fetch (ETag/Last-Modified), SSRF guard, size cap | `service/FeedFetcher.java` |
| Feed URL Validator | SSRF protection: validates scheme, rejects private IP ranges | `service/FeedUrlValidator.java` |
| Feed Parser | Parses RSS/Atom/JSON feed bodies; handles charset detection | `parser/FeedParser.java` |
| Raindrop Service | Integration with Raindrop.io; caches collections | `integration/RaindropService.java` |
| Raindrop API Client | HTTP client with circuit breaker and retry for Raindrop API | `integration/RaindropApiClientImpl.java` |
| Article Extraction Service | Fetches article HTML, extracts readable content, caches in DB | `service/ArticleExtractionService.java` |
| Retention Service | Scheduled job to clear old content and enforce retention policy | `service/RetentionService.java` |
| OPML Service | Parses OPML files (with XXE protection) | `parser/OpmlService.java` |
| OPML Import Service | Imports OPML feeds; publishes FeedSavedEvent per new feed | `service/OpmlImportService.java` |

## Pattern Overview

**Overall:** Layered MVC with event-driven background scheduling and external resilience patterns

**Key Characteristics:**

- **Request-driven:** Controllers delegate to services; services use repositories for data access
- **Event-driven scheduling:** Feed mutations (save/delete) publish domain events; scheduler listeners react asynchronously post-commit
- **Conditional polling:** HTTP ETag/Last-Modified requests reduce bandwidth; article deduplication by GUID prevents duplicates
- **Pagination:** Cursor-based (article ID + publish date) with limit+1 look-ahead to detect "more pages"
- **External resilience:** Raindrop API calls wrapped in Resilience4j circuit breaker + retry; feed fetch failures trigger exponential backoff
- **Security:** SSRF guard on feed URLs; XXE protection on OPML parsing; DOMPurify on extracted HTML

## Layers

**REST API Layer (Controllers):**

- Purpose: HTTP request/response binding, validation, pagination assembly
- Location: `src/main/java/org/bartram/myfeeder/controller/`
- Contains: Controllers (Feed/Article/Folder/Board), request DTOs, PaginatedResponse, GlobalExceptionHandler
- Depends on: Service layer
- Used by: React frontend (HTTP client)

**Service Layer (Business Logic):**

- Purpose: Feed management, article processing, polling coordination, external integrations
- Location: `src/main/java/org/bartram/myfeeder/service/` + `org/bartram/myfeeder/integration/`
- Contains: Services, FeedFetcher, parsers, URL validator
- Depends on: Model, Repository, external HTTP clients
- Used by: Controllers, Scheduler

**Data Access Layer (Repositories):**

- Purpose: Database queries via Spring Data JDBC
- Location: `src/main/java/org/bartram/myfeeder/repository/`
- Contains: `FeedRepository`, `ArticleRepository`, `FolderRepository`, `BoardRepository`, `IntegrationConfigRepository`
- Depends on: Model entities, PostgreSQL
- Used by: Services

**Scheduler Layer (Background Tasks):**

- Purpose: Autonomous polling per feed with exponential backoff
- Location: `src/main/java/org/bartram/myfeeder/scheduler/`
- Contains: `FeedPollingScheduler` (event-driven), `RetentionService` (cron-scheduled)
- Depends on: Service layer, domain events
- Used by: Spring framework (ApplicationReadyEvent, @TransactionalEventListener)

**Frontend Layer (React):**

- Purpose: Three-panel UI (feeds / article list / reading pane), keyboard navigation, theme management
- Location: `src/main/frontend/src/`
- Contains: Components, TanStack Query hooks, Zustand stores, API client
- Depends on: REST API
- Used by: Browser

## Data Flow

### Primary Request Path: Subscribe to Feed

1. User enters feed URL in frontend (`AddFeedDialog.tsx`)
2. Frontend POSTs `/api/feeds` with `SubscribeRequest` (`FeedController.subscribe()`)
3. `FeedService.subscribe()`:
   - Calls `FeedFetcher.fetch(url)` (validates URL via `FeedUrlValidator`, sends GET, bounds response to 10 MiB)
   - Calls `FeedParser.parse(bytes, contentType)` (detects charset, parses RSS/Atom/JSON)
   - Creates new `Feed` entity with title, description, poll interval
   - Saves to DB via `FeedRepository.save()`
   - Publishes `FeedSavedEvent(feed)`
4. `FeedPollingScheduler.onFeedSaved(event)` (triggered post-commit):
   - Calls `registerFeed(feed)`
   - Computes effective poll interval (no backoff yet)
   - Schedules `pollAndAdjust(feedId)` task via `TaskScheduler`
5. First poll executes:
   - `FeedPollingService.pollFeed(feedId)` fetches, parses, saves new articles
   - Articles deduplicated by `(feed_id, guid)` uniqueness constraint
   - Feed's `lastSuccessfulPollAt` and `errorCount` reset
6. User sees feed in feed tree, articles appear in list

### Polling with Exponential Backoff

1. `FeedPollingScheduler.pollAndAdjust(feedId)` runs at fixed interval
2. `FeedPollingService.pollFeed()` called:
   - On success: `errorCount = 0`, `lastSuccessfulPollAt = now`, articles saved
   - On exception (HTTP error, parse error, etc.): `errorCount++`, `lastError = msg`, feed saved
3. Back in `pollAndAdjust()`: re-reads feed from DB, computes `desired = computeEffectiveInterval(feed)`:
   - If `errorCount >= backoffThreshold` (5): backoff = `poll_interval * 2^(errorCount/5)`, capped at 1440 min
   - Otherwise: backoff = `poll_interval` (default 15 min)
4. If interval changed: cancel old task, schedule new task at new interval starting 1 interval in the future

### Article Pagination with Cursor

1. Frontend fetches articles via `/api/articles?feedId=X&limit=50&before=cursorId&sort=desc`
2. `ArticleController.listArticles()`:
   - Clamps limit to [1, 100]
   - Fetches `limit + 1` rows to detect "more pages"
   - Calls `ArticleService.findFiltered(feedId, read, starred, cursor, limit+1, ascending)`
3. `ArticleService.findFiltered()`:
   - If cursor provided: looks up cursor article's `publishedAt` (or `fetchedAt`), calls repo's `findFilteredBefore()` which compares `(date, id)` tuples
   - Otherwise: calls `findFiltered()` (no cursor)
   - Repository executes SQL with `ORDER BY COALESCE(published_at, fetched_at), id`
4. `PaginatedResponse.of()`:
   - If fetched > limit: sets `nextCursor = last_item.id`, trims to limit
   - Otherwise: `nextCursor = null` (end of results)
5. Frontend receives `{items: [...], nextCursor: 123}`, passes `before=123` on next fetch

### Raindrop Save with Resilience

1. User clicks "Save to Raindrop" button on article
2. Frontend POSTs `/api/articles/{id}/raindrop`
3. `ArticleController.saveToRaindrop()`:
   - Looks up article
   - Calls `RaindropService.saveToRaindrop(article)`
4. `RaindropService.saveToRaindrop()`:
   - Loads config from `IntegrationConfigRepository`
   - Validates enabled state, collection ID
   - Calls `RaindropApiClient.createBookmark(collectionId, url, title)` (throws if not configured/disabled)
5. `RaindropApiClientImpl.createBookmark()` has `@CircuitBreaker + @Retry`:
   - First attempt: POST to Raindrop API
   - On 5xx/timeout: Retry up to 3x with exponential backoff
   - On repeated failure: Circuit breaker opens, subsequent calls immediately fail
   - On `RaindropNotConfiguredException`: immediately rethrow (no retry/breaker)
6. On success: article saved to Raindrop collection
7. On failure: `GlobalExceptionHandler` maps exception to HTTP 503 or 409 depending on type

### State Management: Feed, Articles, UI

**Backend State:**

- `Feed`: persisted in DB, mutable (title, pollInterval, errorCount, etag, lastPolledAt)
- `Article`: persisted in DB, mostly immutable except `read` and `starred` booleans
- `Folder`, `Board`, `BoardArticle`: organizational data in DB

**Frontend State (Zustand stores):**

- `uiStore`: panel widths, keyboard focus, selected feed/article (ephemeral, lost on refresh)
- `preferencesStore`: theme, persisted settings (localStorage `myfeeder-prefs`)

**Frontend Async State (TanStack Query):**

- Feeds list, articles list, unread counts (cached in query client, auto-refetch on mutation)

## Key Abstractions

**FeedType (Enum):**

- Purpose: Distinguish RSS, Atom, JSON Feed feed sources
- Examples: `FeedType.RSS`, `FeedType.ATOM`, `FeedType.JSON_FEED`
- Pattern: Enum stored in DB `feed.feed_type`, set by parser

**ParsedFeed / ParsedArticle (Records):**

- Purpose: Represent feed and article data parsed from remote sources
- Examples: `src/main/java/org/bartram/myfeeder/parser/ParsedFeed.java`
- Pattern: Immutable records; parser returns these, service layer maps to entity models

**FetchResult (Record):**

- Purpose: Carry HTTP response bytes, content-type header, ETag, Last-Modified, 304 flag
- Examples: returned by `FeedFetcher.fetch()`
- Pattern: Decouples HTTP mechanics from parsing; parser consumes raw bytes + header

**IntegrationConfig / IntegrationType:**

- Purpose: Store encrypted/sensitive config for external services (Raindrop token, API base URL)
- Examples: `IntegrationType.RAINDROP` enum; config JSON in DB
- Pattern: Service loads config, validates enabled state, rethrows config errors as `IllegalStateException` (409 Conflict)

**FeedSavedEvent / FeedDeletedEvent:**

- Purpose: Publish domain events when feed is created/updated or deleted
- Examples: `src/main/java/org/bartram/myfeeder/event/`
- Pattern: Published by `FeedService`, listened by `FeedPollingScheduler` with `@TransactionalEventListener(AFTER_COMMIT)`

## Entry Points

**Backend Entry Point:**

- Location: `src/main/java/org/bartram/myfeeder/MyfeederApplication.java`
- Triggers: `java -jar myfeeder.jar` (Spring Boot bootstrap)
- Responsibilities:
  - `@SpringBootApplication`: activates component scan, auto-config
  - `@ConfigurationPropertiesScan`: loads `MyfeederProperties` from `application.yaml`
  - `@EnableScheduling`: enables `@Scheduled` methods (RetentionService)
  - Spring starts all beans, including `FeedPollingScheduler`

**Frontend Entry Point:**

- Location: `src/main/frontend/src/main.tsx`
- Triggers: Browser page load (index.html serves from Spring's static resource handler)
- Responsibilities: React root render, theme setup, TanStack Query provider

**REST API Entry Points (Controllers):**

- `GET /api/feeds` — list subscribed feeds
- `POST /api/feeds` — subscribe to new feed
- `GET /api/articles` — paginated article list
- `PATCH /api/articles/{id}` — update article read/starred state
- `POST /api/articles/{id}/raindrop` — save to Raindrop
- `GET /api/folders` — list folders
- `GET /api/boards` — list boards
- `POST /api/opml/import` — bulk import feeds from OPML

**Background Task Entry Points (Scheduler):**

- `ApplicationReadyEvent` listener: `FeedPollingScheduler.onStartup()` — register all feeds on boot
- `@TransactionalEventListener`: `FeedPollingScheduler.onFeedSaved/Deleted()` — react to domain events
- `@Scheduled` cron: `RetentionService.cleanup()` — clean old content nightly
- `TaskScheduler.scheduleAtFixedRate()`: polling tasks (one per feed, self-adjusting)

## Architectural Constraints

- **Single-threaded event loop:** REST requests are servlet-based (blocking), background tasks run on Spring's `TaskScheduler` thread pool. No async/reactive stack.
- **Global state (FeedPollingScheduler):** `scheduledTasks` map is a `ConcurrentHashMap` holding `ScheduledFuture` references; mutations (register/cancel) are thread-safe but **races with polling**: e.g., concurrent `registerFeed` and `pollAndAdjust` can both try to reschedule (worst case: redundant polling for one cycle).
- **No lazy loading (Spring Data JDBC):** Entity associations are not lazy; if a service needs related data, it must fetch explicitly. Article does not auto-load its Feed; Board does not auto-load its BoardArticles.
- **Circular imports:** None detected; packages are cleanly layered (controller → service → repository/integration/scheduler).
- **RestClient customization:** `RestClientConfig` registers a `RestClientCustomizer` bean that sets User-Agent and applies to all `RestClient.Builder`-derived clients (feed fetches, Raindrop API). Bypassing the auto-configured bean (using `RestClient.builder()` directly) loses the User-Agent customizer.
- **Spring Data JDBC queries:** No derived query methods (JPA-style); all custom queries use `@Query` annotations. Modifying queries use `@Modifying`. See `ArticleRepository` for patterns.

## Anti-Patterns

### Calling Scheduler Methods Directly

**What happens:** New code calls `FeedPollingScheduler.registerFeed()` directly instead of publishing `FeedSavedEvent`.

**Why it's wrong:** The event is the contract; callers must publish it. Scheduler methods are defensive but should not be called from services. If you call `registerFeed()` inside a transaction and the service throws after, the polling task registers but the feed is rolled back.

**Do this instead:** In `FeedService`, publish the event after saving:

```java
Feed saved = feedRepository.save(feed);
eventPublisher.publishEvent(new FeedSavedEvent(saved));
```

The scheduler's `@TransactionalEventListener(AFTER_COMMIT)` handles registration post-commit.

### Fetching Feed Content with Raw RestClient

**What happens:** Code uses `RestClient.builder().baseUrl(...).build()` and GETs a feed URL directly, bypassing `FeedFetcher`.

**Why it's wrong:** Loses SSRF guard (`FeedUrlValidator`), loses size cap (10 MiB), loses conditional request headers (ETag/Last-Modified). Unsafe in production.

**Do this instead:** Inject `FeedFetcher` and call `fetch(url)` or `fetch(url, etag, lastModified)`. It enforces both protections.

### Mutating Entities After Finding

**What happens:** Code calls `feedRepository.findById()`, mutates the entity in memory, and does not call `save()`.

**Why it's wrong:** Spring Data JDBC does not provide lazy-write semantics; only explicit `save()` persists changes. In-memory mutations are lost.

**Do this instead:** Always call `save()` after mutations:

```java
Feed feed = feedRepository.findById(id).orElseThrow(...);
feed.setTitle(newTitle);
feedRepository.save(feed);  // required
```

## Error Handling

**Strategy:** Checked exceptions are rare; domain exceptions inherit from unchecked `RuntimeException`. Services throw domain-specific exceptions (`NotFoundException`, `IllegalArgumentException`, `IllegalStateException`). `GlobalExceptionHandler` catches and maps to HTTP status codes.

**Patterns:**

| Exception | HTTP Status | Mapped by | Meaning |
|-----------|-------------|-----------|---------|
| `NotFoundException` | 404 | `GlobalExceptionHandler.handleNotFound()` | Path entity not found (feed, article, folder) |
| `IllegalArgumentException` | 400 | `GlobalExceptionHandler.handleIllegalArgument()` | Invalid request (bad URL, negative limit) |
| `FeedParseException` | 422 | `GlobalExceptionHandler.handleFeedParseException()` | Feed body is not valid RSS/Atom/JSON |
| `FeedFetchException` | 422 | `GlobalExceptionHandler.handleFeedFetchException()` | HTTP error or size limit exceeded fetching feed |
| `OpmlParseException` | 400 | `GlobalExceptionHandler.handleOpmlParseException()` | OPML XML is malformed or invalid |
| `IllegalStateException` | 409 | `GlobalExceptionHandler.handleIllegalState()` | Config error (Raindrop not configured, collection not selected) |
| `RaindropNotConfiguredException` | 503 | `GlobalExceptionHandler.handleRaindropNotConfigured()` | Raindrop token missing or invalid |

**SSRF Validation Exception:** `FeedUrlValidator.validate()` throws `IllegalArgumentException` with message "Feed URL resolves to a non-public address" — mapped to 400 (Bad Request) by `GlobalExceptionHandler.handleIllegalArgument()`.

## Cross-Cutting Concerns

**Logging:** 

- `@Slf4j` (Lombok) on services and scheduler; logs polling success/failure, feed errors, feed deduplication counts
- Warnings logged for feed fetch failures; info for successful polls
- No request-level access logs; Spring Boot Actuator endpoints available for ops

**Validation:**

- URL validation in `FeedFetcher` / `FeedUrlValidator` (SSRF guard, scheme check)
- Poll interval validation in `FeedService.update()` (>= 1 minute)
- Article count validation in `ArticleService.markRead()` (days > 0)
- Raindrop collection ID required in `RaindropService.saveToRaindrop()`

**Authentication & Authorization:**

- No user authentication layer; single-user application (assumes running on LAN behind a router)
- No role-based access control
- All endpoints public

---

*Architecture analysis: 2026-09-22*
