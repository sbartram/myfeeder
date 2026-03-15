# MyFeeder Backend Design

Single-user feed reader backend built with Spring Boot 4.0.3 and Java 21. Supports RSS, Atom, and JSON Feed formats with per-feed configurable polling, article state tracking, and Raindrop.io integration.

## Data Model

All entities use Spring Data JDBC (`@Table`/`@Id` from `org.springframework.data.annotation`).

### Feed

| Field | Type | Notes |
|-------|------|-------|
| `id` | Long | Auto-generated |
| `url` | String | Feed URL |
| `title` | String | From feed metadata |
| `description` | String | Nullable |
| `siteUrl` | String | Link to the website |
| `feedType` | Enum | RSS, ATOM, JSON_FEED |
| `pollIntervalMinutes` | int | Default 15 |
| `lastPolledAt` | Instant | Nullable |
| `lastSuccessfulPollAt` | Instant | Nullable |
| `errorCount` | int | Consecutive failures |
| `lastError` | String | Nullable |
| `etag` | String | Nullable, for conditional GET |
| `lastModifiedHeader` | String | Nullable, for conditional GET |
| `createdAt` | Instant | |

### Article

| Field | Type | Notes |
|-------|------|-------|
| `id` | Long | Auto-generated |
| `feedId` | Long | FK to Feed |
| `guid` | String | Unique per feed, used for dedup |
| `title` | String | |
| `url` | String | Link to original article |
| `author` | String | Nullable |
| `content` | String | Nullable, full HTML, cleared after retention window |
| `summary` | String | Nullable, short excerpt, kept permanently |
| `publishedAt` | Instant | Nullable, falls back to `fetchedAt` if feed omits it |
| `fetchedAt` | Instant | |
| `read` | boolean | Default false |
| `starred` | boolean | Default false |

Deduplication: unique constraint on `(feedId, guid)`.

### IntegrationConfig

| Field | Type | Notes |
|-------|------|-------|
| `id` | Long | Auto-generated |
| `type` | Enum | RAINDROP (extensible), unique constraint |
| `config` | String | JSON blob (API token, collection ID, etc.) |
| `enabled` | boolean | |

## Architecture

Layered monolith: Controller -> Service -> Repository.

### Package Structure

```
org.bartram.myfeeder
├── controller/       # REST endpoints
├── service/          # Business logic
├── repository/       # Spring Data JDBC repositories
├── model/            # Entity classes
├── parser/           # Feed parsing (RSS, Atom, JSON Feed)
├── integration/      # External integrations (Raindrop)
├── scheduler/        # Feed polling scheduler
└── config/           # Spring configuration classes
```

### Components

- **FeedController** — CRUD for feed subscriptions, manual poll trigger
- **ArticleController** — List articles (paginated, filterable), state changes, bulk mark-read, save to Raindrop
- **IntegrationConfigController** — CRUD for integration settings
- **FeedService** — Feed subscription management, validates feed URLs by fetching and parsing before saving
- **ArticleService** — Article queries, state changes, delegates Raindrop saves
- **FeedPollingService** — Fetches and parses a single feed, deduplicates articles, stores new ones
- **FeedParser** — Parses raw feed content into normalized `ParsedFeed`/`ParsedArticle`. Uses ROME for RSS/Atom, Jackson for JSON Feed
- **RaindropService** — Calls Raindrop.io API to save bookmarks. Uses RestClient with Resilience4j circuit breaker
- **RetentionService** — Scheduled daily job that nullifies `content` on articles older than the retention window
- **FeedPollingScheduler** — Manages dynamic scheduled tasks per feed based on `pollIntervalMinutes`

### Caching (Redis)

- Cache parsed feed metadata to avoid re-parsing
- Cache article lists for short durations to reduce DB load on repeated UI refreshes
- Caching is gracefully degradable — if Redis is unavailable, the app operates without caching

## REST API

### Feed Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/feeds` | List all subscribed feeds |
| `POST` | `/api/feeds` | Subscribe to a feed (validates URL, fetches title) |
| `GET` | `/api/feeds/{id}` | Get feed details + stats |
| `PUT` | `/api/feeds/{id}` | Update feed settings (poll interval, title override) |
| `DELETE` | `/api/feeds/{id}` | Unsubscribe (deletes feed + its articles) |
| `POST` | `/api/feeds/{id}/poll` | Manually trigger a poll |

### Articles

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/articles` | List articles (paginated, filterable) |
| `GET` | `/api/articles/{id}` | Get single article with full content |
| `PATCH` | `/api/articles/{id}` | Update state (read/unread, starred) |
| `POST` | `/api/articles/{id}/raindrop` | Save article to Raindrop.io |
| `POST` | `/api/articles/mark-read` | Bulk mark read |

**Bulk mark-read request body:** `{ "articleIds": [Long] }` or `{ "feedId": Long }`. Exactly one field must be provided. `articleIds` marks specific articles; `feedId` marks all articles for that feed.

**Article list query params:** `feedId`, `read`, `starred`, `since`, `page`, `size`, `sort` (default: `publishedAt,desc`, page size 20).

### Integration Config

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/integrations` | List configured integrations |
| `PUT` | `/api/integrations/raindrop` | Upsert Raindrop config |
| `DELETE` | `/api/integrations/raindrop` | Remove Raindrop config |

## Feed Polling & Parsing

### Poll Flow

1. Fetch raw content via RestClient with `If-Modified-Since`/`ETag` headers (conditional GET)
2. If 304 Not Modified, update `lastPolledAt` and return
3. Detect feed type from content (XML -> RSS or Atom by root element, JSON -> JSON Feed)
4. Parse into normalized `ParsedFeed`/`ParsedArticle`
5. Deduplicate: filter out articles whose `guid` already exists for this feed
6. Store new articles with full content
7. Update feed metadata (`lastPolledAt`, `lastSuccessfulPollAt`, reset `errorCount`)
8. Store returned `ETag`/`Last-Modified` headers on the Feed entity

### Scheduling

- On startup, register a `ScheduledFuture` per feed based on its `pollIntervalMinutes`
- Re-register when feeds are added, updated, or deleted via the API

### Error Handling

- On failure: increment `errorCount`, store `lastError`
- After `backoff-threshold` consecutive failures: effective interval = `min(pollIntervalMinutes * 2^(errorCount / backoffThreshold), maxIntervalMinutes)`. The stored `pollIntervalMinutes` is never modified; backoff is computed at scheduling time.
- Successful poll: reset `errorCount`, re-schedule at the stored `pollIntervalMinutes`
- Resilience4j circuit breaker wraps HTTP fetch

### Parsing

- ROME library for RSS and Atom
- Jackson for JSON Feed (simple JSON structure)
- Feed type auto-detected from content

### Retention Cleanup

- `RetentionService` runs daily (configurable cron)
- Nullifies `content` on articles where `fetchedAt` is older than the retention window
- `summary` is always retained

## Raindrop.io Integration

### Configuration

Stored in `IntegrationConfig` with type `RAINDROP`. Config JSON contains `apiToken` and `collectionId`.

### Save Flow

1. `POST /api/articles/{id}/raindrop` triggers save
2. `ArticleService` loads article, delegates to `RaindropService`
3. `RaindropService` loads config from `IntegrationConfigRepository`
4. Calls `POST https://api.raindrop.io/rest/v1/raindrop` with article link, title, and configured collection
5. Returns success/failure to caller

### Error Handling

- Resilience4j circuit breaker around Raindrop API calls
- Missing/disabled config returns 400
- Raindrop API errors propagated to caller

## Application Configuration

```yaml
spring:
  application:
    name: myfeeder

myfeeder:
  polling:
    default-interval-minutes: 15
    max-interval-minutes: 1440
    backoff-threshold: 5
  retention:
    full-content-days: 30
    cleanup-cron: "0 0 3 * * *"
  raindrop:
    api-base-url: https://api.raindrop.io/rest/v1
```

- Feed-specific settings (poll interval) in database on `Feed` entity
- Global defaults in `application.yaml` via `@ConfigurationProperties` (`MyfeederProperties`)
- Raindrop API token and collection managed via API, stored in `IntegrationConfig`
- `api-base-url` in config for test overrideability

## Dependencies

Existing (already in `build.gradle.kts`):
- `spring-boot-starter-webmvc` — REST controllers
- `spring-boot-starter-data-jdbc` — persistence
- `spring-boot-starter-data-redis` + `spring-boot-starter-cache` — caching
- `spring-boot-starter-restclient` — outbound HTTP (feed fetching, Raindrop API)
- `spring-cloud-starter-circuitbreaker-resilience4j` — circuit breaker
- `spring-boot-starter-actuator` — monitoring
- `postgresql` — database driver
- `lombok` — boilerplate reduction
- Testcontainers — test infrastructure

To add:
- `com.rometools:rome:2.1.0` — RSS/Atom feed parsing
- `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` — schema migration

### Schema Migration

Flyway manages database schema. Migration scripts go in `src/main/resources/db/migration/` using the naming convention `V{n}__{description}.sql`.
