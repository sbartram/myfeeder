# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**myfeeder** is a Spring Boot 4.0.3 feed aggregator/reader application using Java 21. It subscribes to RSS, Atom, and JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and can forward saved articles to Raindrop.io.

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

- **Framework**: Spring Boot 4.0.3 with Spring MVC (servlet stack)
- **Language**: Java 21, Lombok for boilerplate reduction
- **Database**: PostgreSQL via Spring Data JDBC (not JPA), Flyway migrations
- **Caching**: Redis via Spring Cache abstraction
- **AI**: Spring AI with Anthropic Claude (chat only)
- **Resilience**: Resilience4j circuit breaker via Spring Cloud
- **HTTP Client**: Spring RestClient for outbound calls
- **Monitoring**: Spring Boot Actuator
- **Feed Parsing**: ROME 2.1.0 for RSS/Atom, Jackson 3.x for JSON Feed
- **Feed extensions**: Media RSS / iTunes / GeoRSS namespaces require `com.rometools:rome-modules:2.1.0` (separate artifact from `rome`); accessed via `entry.getModule(MediaEntryModule.URI)` etc.

## Package Structure

```
org.bartram.myfeeder
├── config/           MyfeederProperties, SpaForwardController
├── model/            Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount
├── repository/       Feed/Article/Folder/Board/BoardArticle/IntegrationConfig repositories
├── parser/           FeedParser (ROME + Jackson), ParsedFeed, ParsedArticle, FeedParseException, OpmlFeed, OpmlParseException
├── service/          FeedService, ArticleService, FeedPollingService, FolderService, BoardService, RetentionService, OpmlService, OpmlImportService, OpmlImportResult
├── integration/      RaindropService (with Resilience4j @CircuitBreaker + @Retry), RaindropConfig
├── controller/       Feed/Article/Folder/Board/IntegrationConfig/Opml controllers + PaginatedResponse + GlobalExceptionHandler + request DTOs (SubscribeRequest, MarkReadRequest, ArticleStateRequest)
├── scheduler/        FeedPollingScheduler (dynamic per-feed scheduling with backoff)
└── MyfeederApplication.java (@EnableScheduling, @ConfigurationPropertiesScan)
```

## Key Behaviors

- **FeedService** registers feeds with `FeedPollingScheduler` on create/update — don't forget this coupling
- **FeedPollingService** deduplicates articles by GUID on upsert
- **FeedPollingScheduler** uses `ApplicationReadyEvent` to register all feeds at startup; supports exponential backoff on errors
- **Article sort order**: Articles are sorted by `COALESCE(published_at, fetched_at)` not by `id`. Batch-fetched articles get sequential IDs but varied publication dates, so `ORDER BY id` does not produce chronological order. Cursor pagination uses composite `(published_at, id)` comparison — the cursor is still a single article ID, but the service looks up the cursor article's date for the SQL comparison.
- **ReadingPane fetches by ID**: The reading pane uses `useArticle(id)` to fetch the selected article directly (`GET /api/articles/{id}`), not by searching through the paginated list query. This avoids filter/sort mismatches between the article list and reading pane.
- **RetentionService** is a `@Scheduled` cron job — config under `myfeeder.retention.*`
- **OpmlService** has XXE protection enabled — maintain this when modifying XML parsing
- **OpmlImportService** registers new feeds with scheduler post-commit (not inline)
- **API endpoints**: `/api/feeds`, `/api/articles`, `/api/integrations`, `/api/opml`, `/api/boards`, `/api/folders`

## Frontend

- **Location**: `src/main/frontend/` (React + TypeScript, built with Vite)
- **Tech Stack**: React 19, TypeScript, TanStack Query, Zustand, React Router v6, DOMPurify
- **Layout**: Three-panel (feed tree / article list / reading pane) with resizable dividers
- **Build**: `npm run build` outputs to `src/main/resources/static/`; Gradle `npmBuild` task wires this into `./gradlew build`
- **Dev workflow**: `./gradlew bootTestRun` (backend) + `cd src/main/frontend && npm run dev` (Vite on :5173, proxies `/api` to :8080)
- **Tests**: Vitest + React Testing Library; run with `cd src/main/frontend && npm test`
- **Type-check**: use `npx tsc -b` from `src/main/frontend/` — plain `tsc --noEmit` returns success even with errors because the root `tsconfig.json` has `files: []` and uses project references
- **Key conventions**:
  - API client in `src/api/` — thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml)
  - TanStack Query hooks in `src/hooks/` — one file per domain (useArticles, useFeeds, useFolders, useBoards, useOpml)
  - Zustand stores in `src/stores/` — `uiStore` (selection, panel state), `preferencesStore` (localStorage-persisted settings)
  - Components in `src/components/` — AppShell, FeedPanel, ArticleList, ReadingPane, BoardArticleList, BoardManager, SettingsDialog, ShortcutOverlay, Toast, dialogs
  - Keyboard shortcuts: vim-style (j/k/n/p/m/s/o/b/v/r), g-chords, managed by `useKeyboardShortcuts` hook
  - Theme system: 6 themes (3 dark, 3 light) defined in `src/themes.ts`, applied via `useTheme` hook, persisted in `preferencesStore`

## Infrastructure

- `compose.yaml` defines Postgres and Redis for local dev (`bootRun`)
- `TestcontainersConfiguration` provides Postgres and Redis containers for tests and `bootTestRun`
- Docker must be running for both tests and local development
- Flyway migrations: `V1__initial_schema.sql` (feeds, articles, integration_configs), `V2__folders_boards_and_feed_folder.sql` (folders, boards, board_articles, feed.folder_id)

## Deployment

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys app + Redis; Postgres is external at `pg.bartram.org`
- **Build image**: `./gradlew clean bootBuildImage --imageName=registry.bartram.org/bartram/myfeeder:<version> -x test` (use `clean` so the latest frontend bundle is included; version comes from axion-release git tags)
- **Push image**: `docker push registry.bartram.org/bartram/myfeeder:<version>` (build does NOT push)
- **Cut release tag**: `./gradlew release` (creates tag locally via axion-release, pushes via git CLI — see Gotchas)
- **Deploy**: `./deploy.sh [version]` (requires `MYFEEDER_PG_PASSWORD` and `MYFEEDER_ANTHROPIC_API_KEY` env vars; no arg → axion computes the *next* snapshot version, which won't match a built image, so for chart-only redeploys against a release tag pass it explicitly: `./deploy.sh 0.1.2`)


## Key Conventions

- Base package: `org.bartram.myfeeder`
- Uses Spring Data JDBC (not JPA) -- entities use `@Table`/`@Id` annotations from `org.springframework.data.annotation`, not `jakarta.persistence`
- Gradle Kotlin DSL for build configuration
- BOM-managed versions for Spring AI and Spring Cloud (do not specify versions on individual dependencies)
- Resilience4j: Use `@CircuitBreaker(name = "...")` (outer) + `@Retry(name = "...")` (inner) annotations on external service calls. Config in `application.yaml` under `resilience4j.circuitbreaker.instances` and `resilience4j.retry.instances`
- **Resilience4j fallback re-throw pattern**: a `@CircuitBreaker` fallback wraps everything as a 5xx by default — to preserve specific HTTP status mappings (e.g. 400 for "no collection selected", 503 for "not configured"), the fallback method must `instanceof`-check and rethrow those exception types before wrapping anything else
- **GlobalExceptionHandler mappings**: `IllegalArgumentException` → 400 (Bad Request), `IllegalStateException` → 409 (Configuration error), `RaindropNotConfiguredException` → 503 (Raindrop not configured). Throw the right type from services and the controller layer doesn't need try/catch
- Spring Data JDBC does not support derived query methods like JPA — use `@Query` annotation for custom queries

## Spring Boot 4 / Jackson 3.x Notes

- Jackson 3.x **databind** moved to `tools.jackson.databind.*` (`ObjectMapper`, `JsonNode`, `JsonMapper`). Spring Boot auto-configures `tools.jackson.databind.ObjectMapper` as a bean.
- Jackson 3.x **annotations** did NOT move — `@JsonProperty`, `@JsonIgnoreProperties`, `@JsonIgnore`, `@JsonCreator` etc. are still in `com.fasterxml.jackson.annotation.*` (jackson-annotations 2.x is a transitive dep of jackson-databind 3.x). There is no `tools.jackson.annotation` package.
- Test annotations `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest` are in `org.springframework.boot.*.test.autoconfigure` packages (e.g., `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`).
- Test starter dependencies follow the pattern `spring-boot-starter-<module>-test` (e.g., `spring-boot-starter-webmvc-test`).
- `RestClientCustomizer` is in `org.springframework.boot.restclient` (Boot 4 moved it out of `org.springframework.boot.web.client`). Spring Boot auto-applies any `RestClientCustomizer` bean to the auto-configured `RestClient.Builder` injected into services.
- `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7. Use `HttpStatus.valueOf(422)` (or `UNPROCESSABLE_CONTENT` once it's exposed).

## Gotchas

- **Docker required**: Must be running for both `./gradlew test` (Testcontainers) and `./gradlew bootRun` (Docker Compose)
- **Zustand persist + new preferences**: Adding a new field to `preferencesStore` with a default value only applies to fresh installs. Existing users with a `myfeeder-prefs` localStorage key get `undefined` for the new field (Zustand merges stored state over defaults). Use a `merge` function or version migration if the default must apply to everyone.
- **Spring Data JDBC ≠ JPA**: No lazy loading, no derived query methods, no `@Entity` — use `@Table`/`@Id` from `org.springframework.data.annotation` and `@Query` for custom queries
- **Jackson 3.x imports**: Must use `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`
- **FeedPollingScheduler coupling**: Creating/updating a feed must register it with the scheduler — `FeedService` handles this, so don't bypass it with direct repository calls
- **Paketo MaxDirectMemorySize**: Buildpack hardcodes `-XX:MaxDirectMemorySize=10M` regardless of container memory limit. Netty (used by Lettuce/Redis) needs more. Override via `JDK_JAVA_OPTIONS` env var (processed after `JAVA_TOOL_OPTIONS`, so last-value-wins). Do NOT use `_JAVA_OPTIONS` or `JAVA_TOOL_OPTIONS` — they won't override the buildpack's value.
- **Frontend not in image**: `bootBuildImage -x npmBuild` reuses `build/resources/main/static/` from the last `processResources` run. After frontend-only changes, run `./gradlew clean bootBuildImage` or `./gradlew processResources` first to ensure the new bundle is packaged.
- **SNAPSHOT tags + pullPolicy**: `imagePullPolicy: IfNotPresent` causes k8s to reuse stale images when the same SNAPSHOT tag is pushed. Use `Always` during development; `IfNotPresent` is only safe with immutable release tags.
- **Gradle terminal escapes in scripts**: `./gradlew currentVersion -q` outputs terminal control sequences. In shell scripts, pipe through `grep 'Project version'` before parsing to avoid contaminating variables.
- **`./gradlew release` push uses git CLI**: axion-release's bundled jgit can't read OpenSSH-format keys. `release` and `pushRelease` in `build.gradle.kts` clear their built-in push action and finalize via a `gitPushRelease` Exec task. Don't replace this with raw axion auth config.
- **Helm probes have a startupProbe**: `startupProbe` (5s × 60 = up to 5 min) gates `livenessProbe` and `readinessProbe`. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to liveness — startupProbe is the gate.
- **Clipboard API requires HTTPS**: The app is served over HTTP (`192.168.44.204`), so `navigator.clipboard` is unavailable. Use `document.execCommand('copy')` fallback for clipboard operations.
- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`**: the `BuildProperties` bean is absent in controller-slice tests. Either inject with `@Autowired(required = false)` and handle `null`, or `@Import` a test config that exposes a `BuildProperties` bean from a `Properties` literal. Same caveat applies to other `info.*` / actuator auto-configured beans.
- **Outbound `RestClient` User-Agent**: the JDK HttpClient default is `Java-http-client/<version>`, which Vercel and other CDNs rate-limit by returning HTTP 200 with body `{"data":"too many requests"}`. `config/RestClientConfig.java` registers a `RestClientCustomizer` that sets `myfeeder/<version>` on every outbound `RestClient.Builder` — don't bypass it by constructing a raw `RestClient.builder()` without going through the auto-configured bean.
- **`FeedParser.parse` rejects empty results**: if a response yields no title and no articles, it throws `FeedParseException` (mapped to 422 by `GlobalExceptionHandler`). This catches "200 OK with garbage" responses that would otherwise save a NULL-title feed and violate `feed.title NOT NULL`.

## Test Patterns

- **Unit tests**: Mockito with `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for services
- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean` for dependencies
- **Repository tests**: `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)` for real Postgres
- **Parser tests**: Plain unit tests with sample feed files in `src/test/resources/feeds/`
- **Integration test**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` verifying all beans wire correctly
- **Migration tests**: Flyway runs at `@DataJdbcTest` startup, so tests can't insert a legacy-shape row and "re-run" the migration. Pattern: insert a legacy-shape row via `JdbcTemplate.update(...)`, then re-execute the migration SQL manually against it, then assert the post-migration shape. Example: `V4StripRaindropApiTokenMigrationTest`
- Test `application.yaml` must include `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key`
- **Frontend `vi.mock` of `preferencesStore` is full-replacement** (e.g. `SettingsDialog.test.tsx`) — adding a new export to `preferencesStore` requires updating every mock that consumes it, otherwise tests fail with `No "X" export is defined on the mock`. Prefer `vi.mock(..., async (importOriginal) => ({ ...await importOriginal(), usePreferences: ... }))` for new tests.
