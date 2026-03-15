# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**myfeeder** is a Spring Boot 4.0.3 feed aggregator/reader application using Java 21. It subscribes to RSS, Atom, and JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and can forward saved articles to Raindrop.io.

## Build & Run Commands

```bash
# Build
./gradlew build

# Run tests (requires Docker for Testcontainers)
./gradlew test

# Run a single test class
./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"

# Run a single test method
./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests.contextLoads"

# Run app with Testcontainers-managed services (no external Docker Compose needed)
./gradlew bootTestRun

# Run app with Docker Compose services
./gradlew bootRun
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
├── config/           MyfeederProperties (@ConfigurationProperties)
├── model/            Feed, Article, IntegrationConfig entities; FeedType, IntegrationType enums
├── repository/       FeedRepository, ArticleRepository, IntegrationConfigRepository (Spring Data JDBC)
├── parser/           FeedParser (ROME + Jackson), ParsedFeed, ParsedArticle, FeedParseException
├── service/          FeedService, ArticleService, FeedPollingService, RetentionService
├── integration/      RaindropService, RaindropConfig
├── controller/       FeedController, ArticleController, IntegrationConfigController + request DTOs
├── scheduler/        FeedPollingScheduler (dynamic per-feed scheduling with backoff)
└── MyfeederApplication.java (@EnableScheduling, @ConfigurationPropertiesScan)
```

## Key Components

- **FeedParser**: Detects feed type (RSS/Atom/JSON Feed) and parses using ROME for XML formats, Jackson for JSON Feed. Returns `ParsedFeed`/`ParsedArticle` records.
- **FeedService**: CRUD for feed subscriptions. On create/update, registers feed with `FeedPollingScheduler`.
- **FeedPollingService**: Fetches feed content via RestClient, parses, and upserts new articles (deduplication by GUID).
- **FeedPollingScheduler**: Uses Spring `TaskScheduler` for dynamic per-feed polling. Registers on `ApplicationReadyEvent`. Supports exponential backoff on repeated errors.
- **RetentionService**: `@Scheduled` cron job to clear old article content (configurable via `myfeeder.retention.*`).
- **RaindropService**: Saves articles to Raindrop.io bookmarking service via their REST API.
- **Controllers**: REST endpoints for feeds (`/api/feeds`), articles (`/api/articles`), and integration config (`/api/integrations`).

## Infrastructure

- `compose.yaml` defines Postgres and Redis for local dev (`bootRun`)
- `TestcontainersConfiguration` provides Postgres and Redis containers for tests and `bootTestRun`
- Docker must be running for both tests and local development
- Flyway migration: `V1__initial_schema.sql` creates feeds, articles, and integration_configs tables

## Key Conventions

- Base package: `org.bartram.myfeeder`
- Uses Spring Data JDBC (not JPA) -- entities use `@Table`/`@Id` annotations from `org.springframework.data.annotation`, not `jakarta.persistence`
- Gradle Kotlin DSL for build configuration
- BOM-managed versions for Spring AI and Spring Cloud (do not specify versions on individual dependencies)

## Spring Boot 4 / Jackson 3.x Notes

- Jackson 3.x package is `tools.jackson.databind`, NOT `com.fasterxml.jackson.databind`. All `ObjectMapper`, `JsonNode` imports must use `tools.jackson.databind.*`.
- Spring Boot auto-configures `tools.jackson.databind.ObjectMapper` as a bean (not the old `com.fasterxml` one).
- Test annotations `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest` are in `org.springframework.boot.*.test.autoconfigure` packages (e.g., `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`).
- Test starter dependencies follow the pattern `spring-boot-starter-<module>-test` (e.g., `spring-boot-starter-webmvc-test`).

## Test Patterns

- **Unit tests**: Mockito with `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for services
- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockBean` for dependencies
- **Repository tests**: `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)` for real Postgres
- **Parser tests**: Plain unit tests with sample feed files in `src/test/resources/feeds/`
- **Integration test**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` verifying all beans wire correctly
- Test `application.yaml` must include `myfeeder.*` properties and dummy Spring AI keys (`spring.ai.anthropic.api-key`, `spring.ai.vertex.ai.embedding.*`)
