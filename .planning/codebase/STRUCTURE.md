---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
# Codebase Structure

**Analysis Date:** 2026-09-22

## Directory Layout

```
/Users/scottb/orca/workspaces/myfeeder/main/
├── build.gradle.kts              # Gradle build config (Java 25, Spring Boot 4.0.3, axion-release)
├── settings.gradle.kts           # Gradle project settings
├── Dockerfile                     # Multi-stage build: JRE runtime with embedded jar
├── docker-compose.yaml           # Postgres + Redis for local dev
├── compose.yaml                  # Alias for docker-compose.yaml
├── helm/                          # Helm chart for k8s deployment (values.yaml, templates/)
├── src/
│   ├── main/
│   │   ├── java/org/bartram/myfeeder/
│   │   │   ├── MyfeederApplication.java       # Spring Boot entry point
│   │   │   ├── config/                        # Configuration & customization
│   │   │   │   ├── MyfeederProperties.java    # @ConfigurationProperties for app config
│   │   │   │   ├── RestClientConfig.java      # User-Agent customizer for RestClient
│   │   │   │   └── SpaForwardController.java  # SPA routing (forwards unmatched paths to index.html)
│   │   │   ├── controller/                    # REST API endpoints
│   │   │   │   ├── FeedController.java        # GET/POST/PUT/DELETE /api/feeds
│   │   │   │   ├── ArticleController.java     # GET /api/articles (paginated), PATCH state
│   │   │   │   ├── FolderController.java      # Folder CRUD
│   │   │   │   ├── BoardController.java       # Board/collection CRUD
│   │   │   │   ├── IntegrationConfigController.java  # Integration settings
│   │   │   │   ├── OpmlController.java        # OPML export/import
│   │   │   │   ├── VersionController.java     # GET /api/version (build info)
│   │   │   │   ├── GlobalExceptionHandler.java # Exception → HTTP status mapping
│   │   │   │   ├── PaginatedResponse.java     # Generic pagination response
│   │   │   │   └── *Request.java              # DTO records (SubscribeRequest, MarkReadRequest, etc.)
│   │   │   ├── service/                       # Business logic
│   │   │   │   ├── FeedService.java           # Feed subscribe/update/delete; publishes events
│   │   │   │   ├── FeedPollingService.java    # Poll logic, deduplication, article save
│   │   │   │   ├── ArticleService.java        # Article retrieval, filtering, state updates
│   │   │   │   ├── FolderService.java         # Folder management
│   │   │   │   ├── BoardService.java          # Board management
│   │   │   │   ├── FeedFetcher.java           # HTTP fetch with SSRF guard, ETag, size cap
│   │   │   │   ├── FeedUrlValidator.java      # SSRF protection: validates scheme, IP ranges
│   │   │   │   ├── ArticleExtractionService.java # Reader view: fetch HTML, extract content
│   │   │   │   ├── RetentionService.java      # @Scheduled cron job: clear old content
│   │   │   │   ├── OpmlService.java           # Parse OPML with XXE protection
│   │   │   │   ├── OpmlImportService.java     # Bulk import from OPML; publishes FeedSavedEvent
│   │   │   │   ├── FetchResult.java           # Record: HTTP response bytes + metadata
│   │   │   │   ├── ExtractedContent.java      # Record: extracted HTML + cached flag
│   │   │   │   ├── NotFoundException.java     # Domain exception (404)
│   │   │   │   ├── FeedFetchException.java    # Domain exception (422)
│   │   │   │   └── OpmlImportResult.java      # Result of OPML import (success/error counts)
│   │   │   ├── repository/                    # Spring Data JDBC repositories
│   │   │   │   ├── FeedRepository.java        # Feed CRUD + custom @Query methods
│   │   │   │   ├── ArticleRepository.java     # Article CRUD + pagination, mark-read, content clear
│   │   │   │   ├── FolderRepository.java      # Folder CRUD
│   │   │   │   ├── BoardRepository.java       # Board CRUD
│   │   │   │   ├── BoardArticleRepository.java # Board article join CRUD
│   │   │   │   └── IntegrationConfigRepository.java # Integration config CRUD
│   │   │   ├── scheduler/                     # Background task scheduling
│   │   │   │   └── FeedPollingScheduler.java  # Event-driven scheduler; registers/cancels polling tasks
│   │   │   ├── integration/                   # External service integrations
│   │   │   │   ├── RaindropService.java       # Business logic: save to Raindrop
│   │   │   │   ├── RaindropApiClient.java     # Interface for Raindrop API calls
│   │   │   │   ├── RaindropApiClientImpl.java  # Implementation with @CircuitBreaker + @Retry
│   │   │   │   ├── RaindropConfig.java        # POJO: Raindrop config (API token, collection ID)
│   │   │   │   ├── RaindropCollection.java    # Record: Raindrop collection info
│   │   │   │   └── RaindropNotConfiguredException.java # Exception when Raindrop token missing
│   │   │   ├── event/                         # Domain events
│   │   │   │   ├── FeedSavedEvent.java        # Published when feed created/updated
│   │   │   │   └── FeedDeletedEvent.java      # Published when feed deleted
│   │   │   ├── model/                         # Domain entities (Spring Data JDBC @Table)
│   │   │   │   ├── Feed.java                  # Feed entity: title, URL, poll interval, error tracking
│   │   │   │   ├── Article.java               # Article entity: content, read/starred flags
│   │   │   │   ├── Folder.java                # Folder entity: organize feeds
│   │   │   │   ├── Board.java                 # Board entity: curated collections
│   │   │   │   ├── BoardArticle.java          # Join table: board ↔ article
│   │   │   │   ├── IntegrationConfig.java     # Integration settings: type, config JSON, enabled
│   │   │   │   ├── FeedType.java              # Enum: RSS, ATOM, JSON_FEED
│   │   │   │   ├── IntegrationType.java       # Enum: RAINDROP, etc.
│   │   │   │   └── UnreadCount.java           # Record: feed ID + unread count (query result)
│   │   │   └── parser/                        # Feed parsing
│   │   │       ├── FeedParser.java            # Main parser: ROME + Jackson for RSS/Atom/JSON
│   │   │       ├── ParsedFeed.java            # Record: parsed feed metadata
│   │   │       ├── ParsedArticle.java         # Record: parsed article data
│   │   │       ├── FeedParseException.java    # Exception: feed is not valid (422)
│   │   │       ├── OpmlFeed.java              # Record: parsed OPML feed entry
│   │   │       └── OpmlParseException.java    # Exception: OPML is malformed (400)
│   │   ├── resources/
│   │   │   ├── application.yaml               # Spring Boot config: logging, database, Raindrop, Resilience4j
│   │   │   ├── db/migration/                  # Flyway SQL migrations
│   │   │   │   ├── V1__initial_schema.sql     # Create feed, article, integration_config tables
│   │   │   │   ├── V2__folders_boards_and_feed_folder.sql # Add folders, boards, feed.folder_id
│   │   │   │   ├── V3__article_image_url.sql  # Add article.image_url column
│   │   │   │   ├── V4__strip_raindrop_api_token.sql # Remove embedded token field (use config table)
│   │   │   │   └── V5__article_extracted_content.sql # Add article.extracted_content column
│   │   │   ├── static/                        # (Generated) React SPA bundle (Vite build output)
│   │   │   │   └── index.html, assets/, etc.
│   │   │   └── templates/                     # (None; SPA-only, no server-side rendering)
│   │   └── frontend/                          # React + TypeScript frontend
│   │       ├── package.json                   # npm dependencies: React 19, Vite, TanStack Query, Zustand
│   │       ├── vite.config.ts                 # Vite config: dev server on :5173, /api proxy to :8080
│   │       ├── tsconfig.json                  # TypeScript config with path aliases
│   │       ├── index.html                     # SPA entry point (React root in <div id="root">)
│   │       ├── src/
│   │       │   ├── main.tsx                   # React app initialization, Vite app load
│   │       │   ├── App.tsx                    # Root component: AppShell wrapper
│   │       │   ├── App.css                    # Global styles
│   │       │   ├── api/                       # HTTP client layer
│   │       │   │   ├── client.ts              # Base apiGet, apiPost, apiPatch wrappers
│   │       │   │   ├── feeds.ts               # Feed API endpoints
│   │       │   │   ├── articles.ts            # Article API endpoints
│   │       │   │   ├── folders.ts             # Folder API endpoints
│   │       │   │   ├── boards.ts              # Board API endpoints
│   │       │   │   ├── integrations.ts        # Integration settings API
│   │       │   │   └── opml.ts                # OPML import/export API
│   │       │   ├── hooks/                     # TanStack Query hooks + custom hooks
│   │       │   │   ├── useFeeds.ts            # Query hook: list/create/update/delete feeds
│   │       │   │   ├── useArticles.ts         # Query hook: paginated articles, filters
│   │       │   │   ├── useFolders.ts          # Query hook: folder CRUD
│   │       │   │   ├── useBoards.ts           # Query hook: board CRUD
│   │       │   │   ├── useOpml.ts             # Query hook: OPML import/export
│   │       │   │   ├── useTheme.ts            # Custom hook: load/switch themes from preferencesStore
│   │       │   │   ├── useVersion.ts          # Query hook: app version/build info
│   │       │   │   ├── useKeyboardShortcuts.ts # Custom hook: vim-style navigation (j/k/n/p/m/s/o/b/v/r)
│   │       │   │   ├── useMarkAllReadInFeed.ts # Custom hook: bulk mark read
│   │       │   │   └── useUnreadFeedNavigation.ts # Custom hook: jump to next unread
│   │       │   ├── stores/                    # Zustand state management
│   │       │   │   ├── uiStore.ts             # UI state: panel widths, selected feed/article, focus
│   │       │   │   └── preferencesStore.ts    # User prefs: theme, persisted in localStorage
│   │       │   ├── components/                # React components
│   │       │   │   ├── AppShell.tsx           # Three-panel layout with resizable dividers
│   │       │   │   ├── FeedPanel.tsx          # Left panel: feed tree, folder tree
│   │       │   │   ├── ArticleList.tsx        # Center panel: article list with pagination
│   │       │   │   ├── ReadingPane.tsx        # Right panel: article detail, reader view toggle
│   │       │   │   ├── AddFeedDialog.tsx      # Modal: subscribe to feed
│   │       │   │   ├── SettingsDialog.tsx     # Modal: preferences, integrations
│   │       │   │   ├── BoardManager.tsx       # Modal: board management
│   │       │   │   ├── MarkOlderReadDialog.tsx # Modal: mark older articles read
│   │       │   │   ├── ShortcutOverlay.tsx    # Modal: keyboard shortcuts reference
│   │       │   │   ├── Toast.tsx              # Toast notifications
│   │       │   │   ├── EmptyState.tsx         # Placeholder when no feeds/articles
│   │       │   │   └── *.test.tsx             # Component tests (Vitest)
│   │       │   ├── types/                     # TypeScript type definitions
│   │       │   │   ├── index.ts               # Main types: Feed, Article, Folder, Board, etc.
│   │       │   │   └── api.ts                 # API response types
│   │       │   ├── utils/                     # Utility functions
│   │       │   │   ├── sanitize.ts            # DOMPurify sanitization for HTML
│   │       │   │   └── format.ts              # Date/time formatting
│   │       │   ├── themes.ts                  # Theme definitions: 6 themes (light/dark variants)
│   │       │   ├── assets/                    # Static assets (icons, images)
│   │       │   └── test/                      # Test utilities and mocks
│   │       └── public/                        # Public static files (copied to build output)
│   └── test/
│       ├── java/org/bartram/myfeeder/         # Backend tests
│       │   ├── MyfeederApplicationTests.java  # Integration test: full app startup
│       │   ├── controller/                    # Controller tests (@WebMvcTest, MockMvc)
│       │   │   ├── FeedControllerTests.java
│       │   │   ├── ArticleControllerTests.java
│       │   │   └── *ControllerTests.java
│       │   ├── service/                       # Service tests (unit, mocks)
│       │   │   ├── FeedServiceTests.java
│       │   │   ├── ArticleServiceTests.java
│       │   │   ├── FeedUrlValidatorTests.java
│       │   │   └── *ServiceTests.java
│       │   ├── repository/                    # Repository tests (@DataJdbcTest, Testcontainers)
│       │   ├── parser/                        # Parser tests (ROME, Jackson, OPML)
│       │   ├── scheduler/                     # Scheduler tests
│       │   ├── integration/                   # Raindrop integration tests
│       │   └── TestcontainersConfiguration.java # Postgres + Redis containers for tests
│       ├── resources/
│       │   ├── application.yaml               # Test config: dummy API keys, in-memory settings
│       │   ├── feeds/                         # Sample RSS/Atom/JSON feed files for parser tests
│       │   ├── opml/                          # Sample OPML files for import tests
│       │   └── pages/                         # Sample HTML pages for content extraction tests
│       └── frontend/ (within frontend/ dir)   # Frontend tests
│           └── src/test/ and *.test.tsx files
├── openwiki/                                  # OpenWiki documentation (auto-generated from code)
│   ├── architecture/                          # Architecture docs
│   ├── domain/                                # Domain concepts
│   ├── integrations/                          # Integration guides
│   ├── operations/                            # Ops/deployment docs
│   ├── testing/                               # Testing patterns
│   └── workflows/                             # Developer workflows
├── docs/                                      # Additional documentation
│   ├── plans/                                 # Planning/phase docs (from /gsd-* agents)
│   └── superpowers/                           # Development workflow docs
├── .github/workflows/                         # GitHub Actions CI/CD
├── .serena/                                   # Serena project memory
└── .planning/codebase/                        # GSD mapping documents
    ├── ARCHITECTURE.md                        # This file's companion
    ├── CONVENTIONS.md (future)
    ├── STRUCTURE.md                           # This file
    ├── TESTING.md (future)
    ├── STACK.md (future)
    ├── INTEGRATIONS.md (future)
    └── CONCERNS.md (future)
```

## Directory Purposes

**Backend (Java):**

- **config/**: Application configuration, customizers, SPA routing
  - `MyfeederProperties`: loads `myfeeder.*` config from `application.yaml`
  - `RestClientConfig`: customizes all `RestClient` instances with User-Agent
  - `SpaForwardController`: forwards unmatched URL paths to `index.html` for React Router

- **controller/**: REST API endpoints, request/response DTOs, error handling
  - Controllers bind HTTP requests to service calls
  - `GlobalExceptionHandler` centralizes exception → HTTP status mapping
  - `PaginatedResponse` encapsulates cursor + items for cursor-based pagination

- **service/**: Business logic, orchestration, external integrations
  - `FeedService`: feed lifecycle (subscribe, update, delete), publishes domain events
  - `FeedPollingService`: polls remote feeds, deduplicates articles, saves to DB
  - `ArticleService`: article retrieval, filtering, pagination, state updates
  - `FeedFetcher`: SSRF-guarded HTTP client for feed content
  - `FeedUrlValidator`: SSRF protection logic (URL scheme, IP range validation)
  - `RetentionService`: scheduled cleanup job (@Scheduled cron)
  - Integration services: `RaindropService` + `RaindropApiClientImpl`
  - Parsing/import: `FeedParser`, `OpmlService`, `OpmlImportService`

- **repository/**: Spring Data JDBC data access
  - One interface per aggregate (Feed, Article, Folder, Board, IntegrationConfig)
  - Custom `@Query` methods for complex filtering and pagination
  - `@Modifying` queries for bulk updates

- **scheduler/**: Background task coordination
  - `FeedPollingScheduler`: listens for domain events, registers/cancels polling tasks dynamically
  - Implements exponential backoff: `computeEffectiveInterval()` scales interval by error count

- **integration/**: External service clients
  - Raindrop.io API: client interface, implementation with Resilience4j circuit breaker + retry, config models
  - Future: other integrations follow same pattern

- **event/**: Domain events for scheduler coordination
  - `FeedSavedEvent`: published by `FeedService` when feed created/updated
  - `FeedDeletedEvent`: published by `FeedService` when feed deleted
  - Listened by `FeedPollingScheduler` with `@TransactionalEventListener(AFTER_COMMIT)`

- **model/**: Entity classes (@Table), enums, value objects
  - Entities use Spring Data JDBC (not JPA): `@Id`, `@Table` annotations
  - Enums: `FeedType` (RSS/ATOM/JSON_FEED), `IntegrationType` (RAINDROP)
  - Records: `ParsedFeed`, `ParsedArticle`, `FetchResult`, etc.

- **parser/**: Feed content parsing
  - `FeedParser`: ROME for RSS/Atom, Jackson for JSON Feed, handles charset detection
  - `OpmlService`: OPML XML parsing with XXE protection
  - Exceptions: `FeedParseException` (422), `OpmlParseException` (400)

**Database (Flyway SQL):**

- **db/migration/**: Version-controlled schema definitions
  - V1: Initial schema (feed, article, integration_config tables)
  - V2: Folders + boards feature
  - V3–5: Column additions (image_url, extracted_content)
  - Each numbered file runs once in order; re-running requires manual intervention

**Frontend (React/TypeScript):**

- **api/**: HTTP client layer
  - Base client (`client.ts`) with `apiGet`, `apiPost`, `apiPatch` wrappers
  - Domain-specific modules: `feeds.ts`, `articles.ts`, `folders.ts`, `boards.ts`, `integrations.ts`, `opml.ts`
  - Each module exports a namespace of endpoints (e.g., `articlesApi.list()`, `articlesApi.getById()`)

- **hooks/**: TanStack Query hooks + custom React hooks
  - Query hooks: `useFeeds`, `useArticles`, `useFolders`, `useBoards`, `useOpml`
  - Custom hooks: `useTheme`, `useKeyboardShortcuts`, `useVersion`, `useMarkAllReadInFeed`
  - Query keys follow pattern: `['feeds']`, `['articles', filters]` for cache isolation

- **stores/**: Zustand state management
  - `uiStore`: ephemeral UI state (panel widths, selected feed/article, keyboard focus)
  - `preferencesStore`: persistent user preferences (theme, settings) → localStorage `myfeeder-prefs`

- **components/**: React components
  - Layout: `AppShell` (three-panel layout with resizable dividers), `FeedPanel`, `ArticleList`, `ReadingPane`
  - Dialogs/modals: `AddFeedDialog`, `SettingsDialog`, `BoardManager`, `MarkOlderReadDialog`, `ShortcutOverlay`
  - Utilities: `Toast` (notifications), `EmptyState` (placeholders)
  - All use TypeScript + React Hooks
  - Tests colocated: `*.test.tsx` files

- **types/**: TypeScript type definitions
  - Main `index.ts`: Feed, Article, Folder, Board, ArticleFilters, PaginatedArticles, etc.
  - `api.ts`: API-specific types (responses, enums)

- **utils/**: Utility functions
  - `sanitize.ts`: DOMPurify for extracted HTML (strip styles to avoid dark pages)
  - `format.ts`: date/time formatting, human-readable text

- **themes.ts**: Theme definitions (light/dark palette variants)

- **test/**: Test utilities, mock factories, custom matchers

## Key File Locations

**Entry Points:**

- Backend: `src/main/java/org/bartram/myfeeder/MyfeederApplication.java` (Spring Boot main)
- Frontend: `src/main/frontend/src/main.tsx` (React root render)
- Frontend component tree: `src/main/frontend/src/App.tsx` (root component)

**Configuration:**

- Spring Boot: `src/main/resources/application.yaml` (logging, database, Raindrop, Resilience4j config)
- Frontend: `src/main/frontend/vite.config.ts` (dev server, build config)
- Build: `build.gradle.kts` (Gradle, Spring Boot, frontend npm integration)
- Kubernetes: `helm/myfeeder/values.yaml` (cluster deployment)

**Core Logic:**

- Feed polling: `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java` + `service/FeedPollingService.java`
- Feed fetching: `src/main/java/org/bartram/myfeeder/service/FeedFetcher.java` (HTTP + SSRF guard)
- Article pagination: `src/main/java/org/bartram/myfeeder/service/ArticleService.java` (cursor logic)
- Repository queries: `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java` (@Query SQL)

**Testing:**

- Backend integration tests: `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java`
- Parser tests: `src/test/java/org/bartram/myfeeder/parser/`
- Repository tests: `src/test/java/org/bartram/myfeeder/repository/`
- Controller tests: `src/test/java/org/bartram/myfeeder/controller/`
- Test containers: `src/test/java/org/bartram/myfeeder/TestcontainersConfiguration.java`
- Frontend tests: `src/main/frontend/src/**/*.test.tsx`
- Test data: `src/test/resources/feeds/`, `src/test/resources/opml/`, `src/test/resources/pages/`

**Migrations:**

- Schema: `src/main/resources/db/migration/V*.sql` (Flyway)

## Naming Conventions

**Files:**

- **Java files:** `PascalCase.java` (e.g., `FeedService.java`, `SubscribeRequest.java`)
- **Test files:** `*Tests.java` (not `*Test.java`) for JUnit test classes
- **React files:** `PascalCase.tsx` for components (e.g., `FeedPanel.tsx`), `camelCase.ts` for utilities
- **SQL migration files:** `V{number}__{description}.sql` (e.g., `V1__initial_schema.sql`)

**Directories:**

- **Package structure:** `org.bartram.myfeeder.<layer>` (config, controller, service, repository, etc.)
- **Frontend:** `src/` → `api/`, `hooks/`, `stores/`, `components/`, `types/`, `utils/`, `test/`, `assets/`, `themes/`

## Where to Add New Code

**New REST Endpoint (e.g., feed statistics):**

1. Create DTO in `controller/` (e.g., `FeedStatsRequest.java`)
2. Add method to existing controller or create new controller in `controller/` (e.g., `StatsController.java`)
3. Call service methods from `service/` layer
4. Add service method in `service/` (e.g., `FeedService.getStats()`)
5. If querying DB: add `@Query` method to repository (e.g., `FeedRepository.getStats()`)

**New External Integration (e.g., Mastodon sharing):**

1. Create integration module: `integration/MastodonService.java`, `MastodonApiClient.java`
2. Add integration type enum: add `MASTODON` to `IntegrationType.java`
3. Create integration config model in `model/` or inline in service
4. Add `@CircuitBreaker + @Retry` annotations to API client methods
5. Update `application.yaml` with Resilience4j config
6. Add endpoint in `IntegrationConfigController.java` to store/retrieve config
7. Call from `ArticleController` or add new controller method

**New Background Task (e.g., daily digest):**

1. Create service class in `service/` (e.g., `DigestService.java`)
2. Add `@Scheduled` method or publish domain event for `FeedPollingScheduler`-style coordination
3. If publishing event: create event class in `event/` (e.g., `DigestGeneratedEvent.java`)
4. Add config in `application.yaml` (cron expression, properties)
5. Add @EnableScheduling already on `MyfeederApplication`

**New Frontend Component:**

1. Create `.tsx` file in `components/` (PascalCase)
2. Create `.test.tsx` colocated test file
3. Import and use in parent component or route
4. If querying API: use hook from `hooks/` (or create new hook)
5. If managing state: use `uiStore` or `preferencesStore` from `stores/`

**New Database Table:**

1. Create Flyway migration in `src/main/resources/db/migration/` (V{next}__description.sql)
2. Create entity class in `model/` with `@Table` and `@Id` annotations
3. Create repository in `repository/` extending `ListCrudRepository<Entity, Long>`
4. Create service in `service/` using repository

**Utilities/Helpers:**

- Reusable backend logic: `service/` folder (marked as `@Component` or `@Service` if Spring-managed, or plain class)
- Reusable frontend logic: `utils/` folder (plain functions/classes)

## Special Directories

**Frontend build output:**

- `src/main/resources/static/` — Generated by `npm run build` (Vite)
- Committed: No (ignored in `.gitignore`)
- Generated by: `gradle npmBuild` task, runs during `./gradlew build`
- Content: `index.html`, `assets/`, `favicon.ico`

**Test containers and fixtures:**

- `src/test/java/org/bartram/myfeeder/TestcontainersConfiguration.java` — Postgres + Redis @Bean factories
- `src/test/resources/feeds/` — Sample RSS/Atom/JSON files for parser tests
- `src/test/resources/opml/` — Sample OPML files for import tests
- `src/test/resources/pages/` — Sample HTML pages for content extraction tests

**Generated Gradle:**

- `build/` directory — build outputs (class files, jars)
- `.gradle/` directory — Gradle cache
- Neither committed; ignored in `.gitignore`

**OpenWiki:**

- `openwiki/` — Auto-generated documentation (do not hand-edit; regenerated by GitHub Actions)
- Source: inline markdown in Java classes, `openwiki/` YAML metadata
- Output: `.md` files in `openwiki/architecture/`, `openwiki/domain/`, etc.

---

*Structure analysis: 2026-09-22*
