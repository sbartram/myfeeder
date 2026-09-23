---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
# External Integrations

**Analysis Date:** 2026-09-22

## APIs & External Services

**Bookmark Aggregation:**

- Raindrop.io - Save subscribed articles to a personal bookmarking collection
  - SDK/Client: Custom `RaindropApiClient` (interface) + `RaindropApiClientImpl` (implementation)
  - Endpoint: `https://api.raindrop.io/rest/v1`
  - Auth: Bearer token via HTTP Authorization header
  - Environment variable: `MYFEEDER_RAINDROP_API_TOKEN` (Helm secret `secrets.raindropApiToken`)
  - Resilience: `@CircuitBreaker(name = "raindrop")` + `@Retry(name = "raindrop")`
    - Failure threshold: 50% (5 failures in 10 calls trigger open state)
    - Open state wait: 30 seconds, then half-open with 3 permitted calls
    - Max retries: 3 with exponential backoff (1s base, 2x multiplier)
  - Exceptions ignored by breaker: `RaindropNotConfiguredException` (no token configured)
  - Implementation location: `src/main/java/org/bartram/myfeeder/integration/`

**Feed Sources (Inbound):**

- RSS/Atom feeds - Subscribed feed polling via ROME 2.1.0
  - HTTP client: Spring RestClient (outbound via `FeedFetcher`)
  - Feed parser: ROME core + rome-modules for Media RSS, iTunes, GeoRSS namespaces
  - JSON feeds: Jackson 3.x (tools.jackson.databind.*)
  - Resilience: SSRF validation (loopback/link-local/RFC1918 rejected), max 10 MiB per feed
  - Polling: Dynamic per-feed scheduling with exponential backoff
  - Polling trigger: Event-driven via `FeedSavedEvent` / `FeedDeletedEvent`
  - Default interval: 15 minutes (configurable via `myfeeder.polling.defaultIntervalMinutes`)
  - Max interval: 1440 minutes / 24 hours (backoff ceiling)
  - Backoff threshold: Triggers after 5 consecutive errors (configurable)

## Data Storage

**Databases:**

- PostgreSQL 12+ (external managed at `pg.bartram.org:5432`)
  - Connection: Spring Data JDBC (not JPA)
  - Database: `myfeeder`
  - Username: `myfeeder`
  - Password: Environment variable `MYFEEDER_PG_PASSWORD` (Helm secret)
  - Client: `org.postgresql:postgresql` JDBC driver
  - Schema management: Flyway (migrations in `src/main/resources/db/migration/V*.sql`)
  - Current version: V5 (article extracted content storage)

**Caching:**

- Redis 7+ (Docker Compose local, Helm chart deployment)
  - Spring Cache abstraction (`@Cacheable`, `@CacheEvict`)
  - Use case: Raindrop collection list caching (expires naturally on no updates)
  - Connection: Spring Data Redis (auto-configured via `spring-boot-starter-data-redis`)
  - Local dev: `docker compose up redis` (port 6379)
  - Testing: Testcontainers spawns ephemeral Redis container

**File Storage:**

- Local filesystem only (no S3/cloud storage)
- Article full text and extracted content stored in PostgreSQL `article.content` and `article.extracted_content` columns
- Retention: Full content retained for `myfeeder.retention.fullContentDays` (default: 30 days), then stripped to summary only

## Authentication & Identity

**Auth Provider:**

- Custom (none)
- Raindrop.io bearer token stored in `IntegrationConfig` table (encrypted config JSON)
  - Not stored in `article` or `feed` tables (see `V4__strip_raindrop_api_token.sql` migration)
- No user login or application-level auth (single-user feed reader)

**Session:**

- Stateless (no server-side sessions needed)

## Monitoring & Observability

**Error Tracking:**

- Spring Boot Actuator - Health endpoints
  - `/actuator/health` - Liveness/readiness probes
  - Configured in Helm chart under `app.probes.{startup,readiness,liveness}`
- No external error tracking service (no Sentry, Datadog, etc.)

**Logs:**

- Spring Boot logging (SLF4J + Logback)
- Log level: `INFO` by default (configurable via `logging.level.*` in application.yaml)
- Slf4j `@Slf4j` annotation on all services and controllers
- Structured logging: None (plain text or JSON if configured via logback.xml)

**Metrics:**

- Spring Boot Actuator metrics endpoints available (`/actuator/metrics`)
- No external metrics ingestion (no Prometheus scrape config provided)

## CI/CD & Deployment

**Hosting:**

- Kubernetes (k3s on homelab)
- Namespace: `myfeeder`
- Container registry: `registry.bartram.org/bartram/myfeeder` (private homelab registry)

**Image Building:**

- Docker image built locally on host: `./gradlew clean bootJar && docker build -t registry.bartram.org/bartram/myfeeder:<version> .`
- Dockerfile location: `Dockerfile` (hand-written, not Paketo buildpacks — see CLAUDE.md Gotchas)
- Base image: `eclipse-temurin:25-jre` (slim JRE runtime)
- Frontend embedded: Gradle `npmBuild` task compiles React frontend and packages into `build/libs/*.jar`
- JAR packaged: Single boot JAR with frontend SPA at `/static/**`

**Deployment:**

- Helm 3.x chart: `helm/myfeeder/`
- Deploy script: `./deploy.sh <version>` (requires `MYFEEDER_PG_PASSWORD` + `MYFEEDER_ANTHROPIC_API_KEY` env vars)
- Image pull policy: `Always` (for SNAPSHOT tags during dev), `IfNotPresent` (for release tags)
- Rollout verification: `kubectl -n myfeeder rollout status deploy/myfeeder`

**Versioning:**

- Semantic versioning via axion-release 1.21.1
- Release flow: `./gradlew release` (creates and pushes tag), then `./gradlew clean bootJar` (embeds version), then `docker build` and `docker push`, then `./deploy.sh <version>`
- Version stamped into JAR by Spring Boot BuildInfo (available via `/actuator/info` endpoint)

## Environment Configuration

**Required env vars (production / Helm):**

- `MYFEEDER_PG_PASSWORD` - PostgreSQL password for `myfeeder` user
- `MYFEEDER_ANTHROPIC_API_KEY` - Anthropic Claude API key (for future AI features)
- `JDK_JAVA_OPTIONS` - JVM flags (set by Helm chart; no hardcoded DirectMemorySize cap needed for Dockerfile images)

**Optional env vars:**

- `MYFEEDER_RAINDROP_API_TOKEN` - Raindrop.io API token (can be empty; integration disabled if not set)
- `DOCKER_HOST` - Docker socket override for Testcontainers (macOS default: `unix:///Users/scottb/.rd/docker.sock`)
- `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` - Override Testcontainers socket detection (test only)

**Secrets location:**

- Helm: `helm/myfeeder/values.yaml` under `secrets.*` (injected as env vars into pod)
- Local dev (.envrc): `$HOME/.config/secrets.env` (sourced by direnv)
- Test: Hardcoded dummy values in `src/test/resources/application.yaml`

**Secrets NOT in code:**

- No `.env` files in repository (gitignored)
- No hardcoded API tokens or passwords in source

## Webhooks & Callbacks

**Incoming:**

- None (poll-based feed fetching only)

**Outgoing:**

- Raindrop.io API calls (async within request → response cycle, not webhook-driven)
- Future: Spring AI Claude integration (currently available as dependency but not activated)

## Development Workflow

**Local Services (Docker Compose):**

- `src/main/frontend/.docker` - No Docker setup; uses `npm run dev` directly
- `compose.yaml` - Postgres + Redis for `./gradlew bootRun` (development mode)
- Auto-wired via `spring-boot-docker-compose` (no manual startup needed if Docker running)

**Frontend Dev Server:**

- `cd src/main/frontend && npm run dev` - Vite dev server on port 5173
- Proxy: `/api/**` → `http://localhost:8080` (backend on port 8080)
- Hot reload: React Fast Refresh via `@vitejs/plugin-react`

**Testing:**

- Backend: `./gradlew test` (spawns Testcontainers for Postgres + Redis, no Docker Compose needed)
- Frontend: `cd src/main/frontend && npm test` (Vitest + JSDOM, no external services)
- Integration: `./gradlew bootTestRun` (backend running + Testcontainers)

---

*Integration audit: 2026-09-22*
