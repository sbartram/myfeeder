<!-- GSD:project-start source:PROJECT.md -->

## Project

**myfeeder**

myfeeder is a self-hosted, single-user feed reader (Spring Boot 4 + React) running on a homelab k3s cluster. It subscribes to RSS, Atom and JSON Feed sources, polls them on a schedule, and presents articles in a three-panel reader with folders, boards, reader view, and Raindrop.io forwarding. This milestone adds **interest ranking**: every new article is judged by TypeSafe's Jev judgment model against the reader's written interest profile and weighted topic rubric, so the articles that matter most surface first in a Priority view.

**Core Value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

### Constraints

- **Tech stack**: Spring Boot 4.0.3, Java 25, Spring Data JDBC (not JPA), Flyway migrations (next is V6), Jackson 3.x (`tools.jackson.*`), React 19 + TanStack Query + Zustand — follow existing conventions in CLAUDE.md
- **Build**: Gradle Kotlin DSL only (never Maven), even though the reference article shows Maven coordinates
- **Resilience**: Jev calls wrapped with `@CircuitBreaker` (outer) + `@Retry` (inner) on a dedicated API-client bean, following the Raindrop pattern
- **Compatibility**: Spring AI TypeSafe 0.1.0 (built against Boot 4.0.7) — upgrade to Boot 4.0.8 first, then verify the starter resolves and starts
- **Performance**: Ingest must not block on Jev; polling latency and failure behavior unchanged when Jev is slow or down
- **Cost**: one Jev call per new article (plus one-time backlog backfill); no re-scoring loops
- **Deployment**: new secret `MYFEEDER_TYPESAFE_API_KEY` threaded through `deploy.sh` and Helm chart as optional
- **Pagination**: Priority view must use cursor pagination compatible with `PaginatedResponse` (score-based composite cursor)

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Java 25 - Backend server, domain models, services, feed parsing
- TypeScript 5.9 - Frontend (React application)
- SQL (PostgreSQL) - Database schemas and migrations
- YAML - Configuration files
- Bash - Build scripts and deployment automation
- JavaScript - Node.js build tooling (npm, Vite)

## Runtime

- JDK 25 (eclipse-temurin for development)
- JRE 25 (eclipse-temurin:25 for production via Docker)
- Node.js (v18+) - Frontend build and development
- Gradle 8.x (Kotlin DSL) - Backend dependency management
- npm - Frontend package management

## Frameworks

- Spring Boot 4.0.3 - Backend framework
- Spring MVC (servlet stack) - Web layer
- Spring Data JDBC - Database abstraction (not JPA)
- Spring Cache - Cache abstraction
- Spring RestClient - HTTP client
- Spring Actuator - Monitoring and health checks
- Spring Scheduling - Scheduled tasks (@EnableScheduling)
- Flyway 10.x - Database schema migrations (`spring-boot-starter-flyway`)
- PostgreSQL JDBC driver - Database connectivity
- Spring Cloud Resilience4j 2025.1.0 - Circuit breaker pattern
- ROME 2.1.0 - RSS/Atom feed parsing
- ROME Modules 2.1.0 - Media RSS, iTunes, GeoRSS extensions
- Jackson 3.x - JSON feed parsing and serialization
- Readability4J 1.0.8 - Article extraction and readability analysis
- Spring AI 2.0.0-M2 - LLM abstraction
- spring-ai-starter-model-anthropic - Anthropic Claude integration (dependency available)
- React 19.2.4 - UI library
- React Router v6.30.3 - Client-side routing
- TanStack Query (React Query) 5.90.21 - Server state management
- Zustand 5.0.11 - Client state management
- Vite 8.0.0 - Frontend build tool
- TypeScript 5.9.3 - Type-safe JavaScript
- DOMPurify 3.3.3 - HTML sanitization
- JUnit Platform / JUnit 5 - Test runner (testcontainers-junit-jupiter)
- Mockito - Mocking framework
- Testcontainers 1.x - Docker-based test infrastructure
- Vitest 4.1.0 - Frontend test runner
- React Testing Library 16.3.2 - React component testing
- JSDOM 29.x - DOM simulation for tests
- Gradle 8.x (Kotlin DSL) - Build orchestration
- npm - Node package management
- axion-release 1.21.1 - Semantic versioning and release tagging
- Lombok - Boilerplate reduction (@Data, @RequiredArgsConstructor, @Slf4j)
- Spring Boot Configuration Processor - `@ConfigurationProperties` support
- ESLint 9.39.4 - JavaScript/TypeScript linting
- @typescript-eslint - TypeScript ESLint support
- eslint-plugin-react-hooks - React hooks linting
- eslint-plugin-react-refresh - React refresh validation
- TypeScript compiler (tsc -b) - Type checking before build
- @vitejs/plugin-react 6.0.0 - React support for Vite
- Vite builds to `src/main/resources/static/` (embedded in JAR)

## Key Dependencies

- Spring Boot 4.0.3 - Entire application framework
- PostgreSQL - Primary persistent store
- Redis - Distributed cache layer
- ROME 2.1.0 - Feed subscription and parsing
- Resilience4j - External service call protection (Raindrop API)
- Flyway - Schema versioning and migrations
- Spring Data JDBC - Type-safe database queries without ORM overhead
- Jackson 3.x - JSON serialization (tools.jackson.databind.*)
- Lombok - Reduced boilerplate in entities and services
- Spring RestClient - Internal HTTP client for feed fetches and Raindrop API
- React 19 - Core UI framework
- TanStack Query 5.90 - Synchronizing server state with UI
- Zustand - Lightweight client-side stores (selections, preferences)
- React Router v6 - Three-panel navigation (feed tree / list / reader)
- Vite - Fast incremental builds and dev server

## Configuration

- Spring Boot profiles: `application.yaml` (defaults) + profile-specific overrides
- Configuration properties: `MyfeederProperties` (prefix `myfeeder.*`) scanned via `@ConfigurationPropertiesScan`
- External 12-factor secrets: Environment variables only (no `.env` files in production)
- Spring HTTP Client timeouts: `spring.http.client.{connect-timeout: 5s, read-timeout: 30s}`
- Local dev (.envrc): Sources from `$HOME/.config/secrets.env` and exports `MYFEEDER_RAINDROP_API_TOKEN`
- `build.gradle.kts` - Single source for backend versioning (via axion-release)
- `src/main/frontend/vite.config.ts` - Frontend build config (outputs to `src/main/resources/static/`)
- Spring Boot BuildInfo annotation generates `build-info.properties` (app version) at compile-time
- Polling interval configuration: `myfeeder.polling.{defaultIntervalMinutes, maxIntervalMinutes, backoffThreshold}`
- Retention policy: `myfeeder.retention.{fullContentDays, cleanup-cron}`
- Circuit breaker settings: `resilience4j.circuitbreaker.instances.raindrop.*` (failure threshold 50%, wait 30s open state)
- Retry strategy: `resilience4j.retry.instances.raindrop.*` (max 3 attempts, exponential backoff)

## Platform Requirements

- Java 25 (Gradle toolchain auto-configured via `JavaLanguageVersion.of(25)`)
- Docker - Running Testcontainers (tests), Docker Compose (local dev)
- Node.js 18+ - Frontend build and development
- macOS or Linux (Gradle build tested on macOS with Homebrew npm)
- Docker daemon (Testcontainers spawns Postgres + Redis containers)
- DOCKER_HOST environment variable (set to Docker Desktop socket on macOS by default)
- Docker - Image runtime (Dockerfile uses eclipse-temurin:25-jre)
- Kubernetes - Deployment via Helm chart (k3s cluster, namespace `myfeeder`)
- PostgreSQL 12+ - External managed database (`pg.bartram.org`)
- Redis 7+ - Distributed cache (deployed via Helm sub-chart or external)
- 512 MiB request / 2 GiB limit memory per pod
- k3s Kubernetes cluster
- Helm 3.x - Chart templating
- Docker Registry - `registry.bartram.org/bartram/myfeeder`
- MetalLB LoadBalancer (IP: 192.168.44.204)

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Naming Patterns

- Class names: PascalCase (e.g., `Feed`, `FeedService`, `FeedRepository`, `FeedController`)
- Package structure: `org.bartram.myfeeder.<domain>` (e.g., `org.bartram.myfeeder.service`, `org.bartram.myfeeder.controller`, `org.bartram.myfeeder.repository`)
- Test classes: append `Test` suffix to the class under test (e.g., `FeedServiceTest` tests `FeedService`)
- Method names: camelCase (e.g., `subscribe`, `findAll`, `delete`, `updateFeed`, `listCollections`)
- Field names: camelCase (e.g., `feedId`, `pollIntervalMinutes`, `lastPolledAt`, `apiToken`)
- Test method names: "should" prefix with descriptive name (e.g., `shouldReturnAllFeeds`, `shouldDeleteFeed`, `shouldRejectNonPositivePollInterval`)
- Private helper methods: camelCase with clear intent (e.g., `requireConfigured`, `raiseIfBad`)
- Component names: PascalCase (e.g., `FeedPanel`, `ArticleList`, `ReadingPane`, `SortableFolder`)
- Hook names: camelCase with `use` prefix (e.g., `useFeeds`, `useArticles`, `useFolders`, `useTheme`, `useKeyboardShortcuts`)
- Store names: camelCase with `Store` suffix (e.g., `uiStore`, `preferencesStore`)
- Function names: camelCase (e.g., `formatPublishedDate`, `renderWithRouter`, `formatBylineDate`)
- Test names: descriptive statement style (e.g., "should X when Y", "shows date and time when the article is less than 24 hours old", "opens dropdown menu and shows 'Mark older than…' option")
- File names: camelCase for functions/utilities (e.g., `dates.ts`), PascalCase for components (e.g., `FeedPanel.tsx`)
- Type files: lowercase plural (e.g., `types/index.ts`)
- API modules: lowercase plural (e.g., `api/feeds.ts`, `api/articles.ts`)

## Code Style

- No explicit formatter configured (relies on IDE defaults)
- Constructor injection preferred via Lombok's `@RequiredArgsConstructor`
- Imports organized: standard library, then third-party packages, then project imports
- ESLint for linting: `.eslintrc.js` enables `@eslint/js`, TypeScript ESLint, React Hooks, and React Refresh plugins
- No Prettier configuration found; ESLint rules control formatting
- Strict TypeScript: `strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`
- Target: ES2023 with React 19 JSX
- Java: Gradle checks run via `./gradlew check`; no explicit linter rules in build config
- TypeScript/React: `npm run lint` runs ESLint; see `src/main/frontend/eslint.config.js` for rules

## Import Organization

## Error Handling

- Custom exception types map to specific HTTP status codes via centralized `GlobalExceptionHandler`
- Use `@RestControllerAdvice` to handle exceptions uniformly across controllers
- Return RFC 7807 `ProblemDetail` responses with title and detail message
- `NotFoundException` → HTTP 404 (resource not found)
- `IllegalArgumentException` → HTTP 400 (Bad Request; validation errors)
- `FeedParseException` → HTTP 422 (Unprocessable Entity; feed parsing failed)
- `FeedFetchException` → HTTP 422 (remote HTTP error or network issue)
- `OpmlParseException` → HTTP 400 (OPML parsing failed)
- `IllegalStateException` → HTTP 409 (Configuration error, e.g., integration disabled)
- `RaindropNotConfiguredException` → HTTP 503 (Service Unavailable; integration not configured)
- Fetch errors are caught and rethrown with descriptive messages via `raiseIfBad()` helper in `src/api/client.ts`
- ProblemDetail responses are parsed for the `detail`, `title`, or `message` field; falls back to raw response text
- Errors propagate to UI via React Query's `isError` state

## Logging

- Use Lombok's `@Slf4j` annotation to inject logger
- Log at INFO level for key events (successful operations, config changes)
- Log at WARN level for recoverable errors or degraded behavior
- Log at ERROR level for exceptions and failed operations
- Resilience4j handles circuit breaker logging via its own mechanisms
- No centralized logging framework; `console.log` / `console.error` used for debugging in development
- Errors are typically handled via React Query's `onError` callbacks

## Comments

- Javadoc for public methods and classes
- Single-line comments for non-obvious logic
- No verbose inline comments; code should be self-documenting
- JSDoc for exported functions and public APIs
- Inline comments sparingly; prefer clear function names
- Comments for business logic that isn't obvious from code

## Function Design

- Constructor injection via `@RequiredArgsConstructor` from Lombok; no setter injection
- Keep methods focused on a single responsibility
- Use Optional for nullable returns (e.g., `Optional<Feed> findById(Long id)`)
- Avoid null returns; throw exceptions or use Optional
- Hooks return object structures with explicit property names
- Use destructuring in components to extract only needed values
- Helper functions take explicit parameters, not options objects (unless many optional params)

## Module Design

- `org.bartram.myfeeder.model`: Domain entities with `@Table` and `@Id` (Spring Data JDBC, not JPA)
- `org.bartram.myfeeder.repository`: Spring Data repositories with `@Query` annotations for custom queries
- `org.bartram.myfeeder.service`: Business logic, orchestration, event publishing
- `org.bartram.myfeeder.controller`: HTTP endpoints, DTOs, request/response mapping
- `org.bartram.myfeeder.parser`: Feed parsing logic (ROME + Jackson)
- `org.bartram.myfeeder.integration`: External service clients (Raindrop.io)
- `org.bartram.myfeeder.scheduler`: Scheduled operations (feed polling, retention)
- `org.bartram.myfeeder.config`: Spring configuration, properties beans, customizers
- `org.bartram.myfeeder.event`: Event classes for pub/sub
- `src/api/`: HTTP client functions organized by domain (e.g., `feedsApi`, `articlesApi`)
- `src/hooks/`: React hooks for queries and mutations, one file per domain
- `src/stores/`: Zustand stores for global state (UI state, preferences)
- `src/components/`: React components with co-located tests (e.g., `ArticleList.tsx` + `ArticleList.test.tsx`)
- `src/utils/`: Helper functions with co-located tests (e.g., `dates.ts` + `dates.test.ts`)
- `src/types/`: TypeScript type definitions

## DTOs & Data Classes

- **Records** for immutable DTOs: `public record FeedUpdateRequest(String title, Integer pollIntervalMinutes) {}`
- **Lombok @Data** for mutable DTOs with more fields: `@Data public class MarkReadRequest { ... }`
- **Private Records** for internal/nested data: used in API clients for Jackson mapping
- **Spring Data JDBC @Table** for persistent entities: `@Table("feed")`, `@Id` for primary key
- **Interfaces** for component props and state shapes
- **Type aliases** for unions and specific types
- **Type imports** using `type` keyword to avoid circular dependencies

## Resilience & External Calls

- Place `@CircuitBreaker` (outer) and `@Retry` (inner) on the **API client bean**, not the service
- Business validation (config checks) runs in the service **before** calling the client
- Fallback methods re-throw specific exceptions (e.g., `RaindropNotConfiguredException`) before wrapping others
- List exception types in `ignore-exceptions` in both circuit breaker and retry configs

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## System Overview

```text

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

- **Request-driven:** Controllers delegate to services; services use repositories for data access
- **Event-driven scheduling:** Feed mutations (save/delete) publish domain events; scheduler listeners react asynchronously post-commit
- **Conditional polling:** HTTP ETag/Last-Modified requests reduce bandwidth; article deduplication by GUID prevents duplicates
- **Pagination:** Cursor-based (article ID + publish date) with limit+1 look-ahead to detect "more pages"
- **External resilience:** Raindrop API calls wrapped in Resilience4j circuit breaker + retry; feed fetch failures trigger exponential backoff
- **Security:** SSRF guard on feed URLs; XXE protection on OPML parsing; DOMPurify on extracted HTML

## Layers

- Purpose: HTTP request/response binding, validation, pagination assembly
- Location: `src/main/java/org/bartram/myfeeder/controller/`
- Contains: Controllers (Feed/Article/Folder/Board), request DTOs, PaginatedResponse, GlobalExceptionHandler
- Depends on: Service layer
- Used by: React frontend (HTTP client)
- Purpose: Feed management, article processing, polling coordination, external integrations
- Location: `src/main/java/org/bartram/myfeeder/service/` + `org/bartram/myfeeder/integration/`
- Contains: Services, FeedFetcher, parsers, URL validator
- Depends on: Model, Repository, external HTTP clients
- Used by: Controllers, Scheduler
- Purpose: Database queries via Spring Data JDBC
- Location: `src/main/java/org/bartram/myfeeder/repository/`
- Contains: `FeedRepository`, `ArticleRepository`, `FolderRepository`, `BoardRepository`, `IntegrationConfigRepository`
- Depends on: Model entities, PostgreSQL
- Used by: Services
- Purpose: Autonomous polling per feed with exponential backoff
- Location: `src/main/java/org/bartram/myfeeder/scheduler/`
- Contains: `FeedPollingScheduler` (event-driven), `RetentionService` (cron-scheduled)
- Depends on: Service layer, domain events
- Used by: Spring framework (ApplicationReadyEvent, @TransactionalEventListener)
- Purpose: Three-panel UI (feeds / article list / reading pane), keyboard navigation, theme management
- Location: `src/main/frontend/src/`
- Contains: Components, TanStack Query hooks, Zustand stores, API client
- Depends on: REST API
- Used by: Browser

## Data Flow

### Primary Request Path: Subscribe to Feed

### Polling with Exponential Backoff

### Article Pagination with Cursor

### Raindrop Save with Resilience

### State Management: Feed, Articles, UI

- `Feed`: persisted in DB, mutable (title, pollInterval, errorCount, etag, lastPolledAt)
- `Article`: persisted in DB, mostly immutable except `read` and `starred` booleans
- `Folder`, `Board`, `BoardArticle`: organizational data in DB
- `uiStore`: panel widths, keyboard focus, selected feed/article (ephemeral, lost on refresh)
- `preferencesStore`: theme, persisted settings (localStorage `myfeeder-prefs`)
- Feeds list, articles list, unread counts (cached in query client, auto-refetch on mutation)

## Key Abstractions

- Purpose: Distinguish RSS, Atom, JSON Feed feed sources
- Examples: `FeedType.RSS`, `FeedType.ATOM`, `FeedType.JSON_FEED`
- Pattern: Enum stored in DB `feed.feed_type`, set by parser
- Purpose: Represent feed and article data parsed from remote sources
- Examples: `src/main/java/org/bartram/myfeeder/parser/ParsedFeed.java`
- Pattern: Immutable records; parser returns these, service layer maps to entity models
- Purpose: Carry HTTP response bytes, content-type header, ETag, Last-Modified, 304 flag
- Examples: returned by `FeedFetcher.fetch()`
- Pattern: Decouples HTTP mechanics from parsing; parser consumes raw bytes + header
- Purpose: Store encrypted/sensitive config for external services (Raindrop token, API base URL)
- Examples: `IntegrationType.RAINDROP` enum; config JSON in DB
- Pattern: Service loads config, validates enabled state, rethrows config errors as `IllegalStateException` (409 Conflict)
- Purpose: Publish domain events when feed is created/updated or deleted
- Examples: `src/main/java/org/bartram/myfeeder/event/`
- Pattern: Published by `FeedService`, listened by `FeedPollingScheduler` with `@TransactionalEventListener(AFTER_COMMIT)`

## Entry Points

- Location: `src/main/java/org/bartram/myfeeder/MyfeederApplication.java`
- Triggers: `java -jar myfeeder.jar` (Spring Boot bootstrap)
- Responsibilities:
- Location: `src/main/frontend/src/main.tsx`
- Triggers: Browser page load (index.html serves from Spring's static resource handler)
- Responsibilities: React root render, theme setup, TanStack Query provider
- `GET /api/feeds` — list subscribed feeds
- `POST /api/feeds` — subscribe to new feed
- `GET /api/articles` — paginated article list
- `PATCH /api/articles/{id}` — update article read/starred state
- `POST /api/articles/{id}/raindrop` — save to Raindrop
- `GET /api/folders` — list folders
- `GET /api/boards` — list boards
- `POST /api/opml/import` — bulk import feeds from OPML
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

```java

```

### Fetching Feed Content with Raw RestClient

### Mutating Entities After Finding

```java

```

## Error Handling

| Exception | HTTP Status | Mapped by | Meaning |
|-----------|-------------|-----------|---------|
| `NotFoundException` | 404 | `GlobalExceptionHandler.handleNotFound()` | Path entity not found (feed, article, folder) |
| `IllegalArgumentException` | 400 | `GlobalExceptionHandler.handleIllegalArgument()` | Invalid request (bad URL, negative limit) |
| `FeedParseException` | 422 | `GlobalExceptionHandler.handleFeedParseException()` | Feed body is not valid RSS/Atom/JSON |
| `FeedFetchException` | 422 | `GlobalExceptionHandler.handleFeedFetchException()` | HTTP error or size limit exceeded fetching feed |
| `OpmlParseException` | 400 | `GlobalExceptionHandler.handleOpmlParseException()` | OPML XML is malformed or invalid |
| `IllegalStateException` | 409 | `GlobalExceptionHandler.handleIllegalState()` | Config error (Raindrop not configured, collection not selected) |
| `RaindropNotConfiguredException` | 503 | `GlobalExceptionHandler.handleRaindropNotConfigured()` | Raindrop token missing or invalid |

## Cross-Cutting Concerns

- `@Slf4j` (Lombok) on services and scheduler; logs polling success/failure, feed errors, feed deduplication counts
- Warnings logged for feed fetch failures; info for successful polls
- No request-level access logs; Spring Boot Actuator endpoints available for ops
- URL validation in `FeedFetcher` / `FeedUrlValidator` (SSRF guard, scheme check)
- Poll interval validation in `FeedService.update()` (>= 1 minute)
- Article count validation in `ArticleService.markRead()` (days > 0)
- Raindrop collection ID required in `RaindropService.saveToRaindrop()`
- No user authentication layer; single-user application (assumes running on LAN behind a router)
- No role-based access control
- All endpoints public

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
