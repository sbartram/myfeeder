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
- **AI**: Spring AI with Anthropic Claude (chat) and Vertex AI (embeddings)
- **Resilience**: Resilience4j circuit breaker via Spring Cloud
- **HTTP Client**: Spring RestClient for outbound calls
- **Monitoring**: Spring Boot Actuator
- **Feed Parsing**: ROME 2.1.0 for RSS/Atom, Jackson 3.x for JSON Feed

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

## Key Conventions

- Base package: `org.bartram.myfeeder`
- Uses Spring Data JDBC (not JPA) -- entities use `@Table`/`@Id` annotations from `org.springframework.data.annotation`, not `jakarta.persistence`
- Gradle Kotlin DSL for build configuration
- BOM-managed versions for Spring AI and Spring Cloud (do not specify versions on individual dependencies)
- Resilience4j: Use `@CircuitBreaker(name = "...")` (outer) + `@Retry(name = "...")` (inner) annotations on external service calls. Config in `application.yaml` under `resilience4j.circuitbreaker.instances` and `resilience4j.retry.instances`
- Spring Data JDBC does not support derived query methods like JPA — use `@Query` annotation for custom queries

## Spring Boot 4 / Jackson 3.x Notes

- Jackson 3.x package is `tools.jackson.databind`, NOT `com.fasterxml.jackson.databind`. All `ObjectMapper`, `JsonNode` imports must use `tools.jackson.databind.*`.
- Spring Boot auto-configures `tools.jackson.databind.ObjectMapper` as a bean (not the old `com.fasterxml` one).
- Test annotations `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest` are in `org.springframework.boot.*.test.autoconfigure` packages (e.g., `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`).
- Test starter dependencies follow the pattern `spring-boot-starter-<module>-test` (e.g., `spring-boot-starter-webmvc-test`).

## Gotchas

- **Docker required**: Must be running for both `./gradlew test` (Testcontainers) and `./gradlew bootRun` (Docker Compose)
- **Zustand persist + new preferences**: Adding a new field to `preferencesStore` with a default value only applies to fresh installs. Existing users with a `myfeeder-prefs` localStorage key get `undefined` for the new field (Zustand merges stored state over defaults). Use a `merge` function or version migration if the default must apply to everyone.
- **Spring Data JDBC ≠ JPA**: No lazy loading, no derived query methods, no `@Entity` — use `@Table`/`@Id` from `org.springframework.data.annotation` and `@Query` for custom queries
- **Jackson 3.x imports**: Must use `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`
- **FeedPollingScheduler coupling**: Creating/updating a feed must register it with the scheduler — `FeedService` handles this, so don't bypass it with direct repository calls

## Test Patterns

- **Unit tests**: Mockito with `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for services
- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean` for dependencies
- **Repository tests**: `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)` for real Postgres
- **Parser tests**: Plain unit tests with sample feed files in `src/test/resources/feeds/`
- **Integration test**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` verifying all beans wire correctly
- Test `application.yaml` must include `myfeeder.*` properties and dummy Spring AI keys (`spring.ai.anthropic.api-key`, `spring.ai.vertex.ai.embedding.*`)
