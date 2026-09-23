---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
# Technology Stack

**Analysis Date:** 2026-09-22

## Languages

**Primary:**

- Java 25 - Backend server, domain models, services, feed parsing
- TypeScript 5.9 - Frontend (React application)
- SQL (PostgreSQL) - Database schemas and migrations
- YAML - Configuration files

**Secondary:**

- Bash - Build scripts and deployment automation
- JavaScript - Node.js build tooling (npm, Vite)

## Runtime

**Environment:**

- JDK 25 (eclipse-temurin for development)
- JRE 25 (eclipse-temurin:25 for production via Docker)
- Node.js (v18+) - Frontend build and development

**Package Manager:**

- Gradle 8.x (Kotlin DSL) - Backend dependency management
  - Lockfile: Not used (Maven Central repositories)
- npm - Frontend package management
  - Lockfile: `src/main/frontend/package-lock.json` (present)

## Frameworks

**Core:**

- Spring Boot 4.0.8 - Backend framework
- Spring MVC (servlet stack) - Web layer
- Spring Data JDBC - Database abstraction (not JPA)
- Spring Cache - Cache abstraction
- Spring RestClient - HTTP client (Reactor Netty transport via reactor-netty-http, D-01)
- Spring Actuator - Monitoring and health checks
- Spring Scheduling - Scheduled tasks (@EnableScheduling)

**Data & Persistence:**

- Flyway 10.x - Database schema migrations (`spring-boot-starter-flyway`)
- PostgreSQL JDBC driver - Database connectivity

**Resilience:**

- Spring Cloud Resilience4j 2025.1.3 - Circuit breaker pattern
  - `@CircuitBreaker` for Raindrop API calls
  - `@Retry` for transient failure handling

**Feed Parsing:**

- ROME 2.1.0 - RSS/Atom feed parsing
- ROME Modules 2.1.0 - Media RSS, iTunes, GeoRSS extensions
- Jackson 3.x - JSON feed parsing and serialization

**Content Extraction:**

- Readability4J 1.0.8 - Article extraction and readability analysis

**AI/ML (included, not yet active):**

- Spring AI 2.0.1 - LLM abstraction
- spring-ai-starter-model-anthropic - Anthropic Claude integration (dependency available)

**Frontend:**

- React 19.3.0 - UI library
- React Router v6.30.6 - Client-side routing
- TanStack Query (React Query) 5.103.2 - Server state management
- Zustand 5.0.15 - Client state management
- Vite 8.3.0 - Frontend build tool
- TypeScript 5.9.3 - Type-safe JavaScript
- DOMPurify 3.4.15 - HTML sanitization

**Testing:**

- JUnit Platform / JUnit 5 - Test runner (testcontainers-junit-jupiter)
- Mockito - Mocking framework
- Testcontainers 1.x - Docker-based test infrastructure
  - PostgreSQL containers for integration tests
  - Redis containers for cache testing
- Vitest 4.1.11 - Frontend test runner
- React Testing Library 16.3.3 - React component testing
- JSDOM 29.x - DOM simulation for tests

**Build & Dev Tools:**

- Gradle 8.x (Kotlin DSL) - Build orchestration
- npm - Node package management
- axion-release 1.21.1 - Semantic versioning and release tagging
- Lombok - Boilerplate reduction (@Data, @RequiredArgsConstructor, @Slf4j)
- Spring Boot Configuration Processor - `@ConfigurationProperties` support

**Linting & Formatting:**

- ESLint 9.39.5 - JavaScript/TypeScript linting
- @typescript-eslint - TypeScript ESLint support
- eslint-plugin-react-hooks - React hooks linting
- eslint-plugin-react-refresh - React refresh validation

**Frontend Build Process:**

- TypeScript compiler (tsc -b) - Type checking before build
- @vitejs/plugin-react 6.1.1 - React support for Vite
- Vite builds to `src/main/resources/static/` (embedded in JAR)

## Key Dependencies

**Critical (Backend):**

- Spring Boot 4.0.8 - Entire application framework
- PostgreSQL - Primary persistent store
- Redis - Distributed cache layer
- ROME 2.1.0 - Feed subscription and parsing
- Resilience4j - External service call protection (Raindrop API)
- Flyway - Schema versioning and migrations

**Infrastructure:**

- Spring Data JDBC - Type-safe database queries without ORM overhead
- Jackson 3.x - JSON serialization (tools.jackson.databind.*)
- Lombok - Reduced boilerplate in entities and services
- Spring RestClient - Internal HTTP client for feed fetches and Raindrop API

**Frontend:**

- React 19 - Core UI framework
- TanStack Query 5.103 - Synchronizing server state with UI
- Zustand - Lightweight client-side stores (selections, preferences)
- React Router v6 - Three-panel navigation (feed tree / list / reader)
- Vite - Fast incremental builds and dev server

## Configuration

**Environment:**

- Spring Boot profiles: `application.yaml` (defaults) + profile-specific overrides
- Configuration properties: `MyfeederProperties` (prefix `myfeeder.*`) scanned via `@ConfigurationPropertiesScan`
- External 12-factor secrets: Environment variables only (no `.env` files in production)
  - `MYFEEDER_RAINDROP_API_TOKEN` - Raindrop.io API token (optional)
  - `MYFEEDER_ANTHROPIC_API_KEY` - Anthropic Claude API (Helm chart secret)
  - `MYFEEDER_PG_PASSWORD` - PostgreSQL password (Helm chart secret)
- Spring HTTP Client timeouts: `spring.http.clients.{connect-timeout: 5s, read-timeout: 30s}`
- Local dev (.envrc): Sources from `$HOME/.config/secrets.env` and exports `MYFEEDER_RAINDROP_API_TOKEN`

**Build:**

- `build.gradle.kts` - Single source for backend versioning (via axion-release)
- `src/main/frontend/vite.config.ts` - Frontend build config (outputs to `src/main/resources/static/`)
- Spring Boot BuildInfo annotation generates `build-info.properties` (app version) at compile-time

**Feature Flags:**

- Polling interval configuration: `myfeeder.polling.{defaultIntervalMinutes, maxIntervalMinutes, backoffThreshold}`
- Retention policy: `myfeeder.retention.{fullContentDays, cleanup-cron}`
- Circuit breaker settings: `resilience4j.circuitbreaker.instances.raindrop.*` (failure threshold 50%, wait 30s open state)
- Retry strategy: `resilience4j.retry.instances.raindrop.*` (max 3 attempts, exponential backoff)

## Platform Requirements

**Development:**

- Java 25 (Gradle toolchain auto-configured via `JavaLanguageVersion.of(25)`)
- Docker - Running Testcontainers (tests), Docker Compose (local dev)
- Node.js 18+ - Frontend build and development
- macOS or Linux (Gradle build tested on macOS with Homebrew npm)

**Test Environment:**

- Docker daemon (Testcontainers spawns Postgres + Redis containers)
- DOCKER_HOST environment variable (set to Docker Desktop socket on macOS by default)

**Production:**

- Docker - Image runtime (Dockerfile uses eclipse-temurin:25-jre)
- Kubernetes - Deployment via Helm chart (k3s cluster, namespace `myfeeder`)
- PostgreSQL 12+ - External managed database (`pg.bartram.org`)
- Redis 7+ - Distributed cache (deployed via Helm sub-chart or external)
- 512 MiB request / 2 GiB limit memory per pod

**Deployment Target:**

- k3s Kubernetes cluster
- Helm 3.x - Chart templating
- Docker Registry - `registry.bartram.org/bartram/myfeeder`
- MetalLB LoadBalancer (IP: 192.168.44.204)

---

*Stack analysis: 2026-09-22*
