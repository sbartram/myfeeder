# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**myfeeder** is a Spring Boot 4.0.3 feed aggregator/reader application using Java 25. It subscribes to RSS, Atom, and JSON Feed sources, polls them on a schedule, stores articles in PostgreSQL, and can forward saved articles to Raindrop.io.

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
- **Language**: Java 25, Lombok for boilerplate reduction
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
├── config/           MyfeederProperties, RestClientConfig (User-Agent customizer), SpaForwardController
├── model/            Feed, FeedType, Article, Folder, Board, BoardArticle, IntegrationConfig, IntegrationType, UnreadCount
├── repository/       Feed/Article/Folder/Board/BoardArticle/IntegrationConfig repositories
├── parser/           FeedParser (ROME + Jackson), ParsedFeed, ParsedArticle, FeedParseException, OpmlFeed, OpmlParseException
├── service/          FeedService, ArticleService, FeedPollingService, FolderService, BoardService, RetentionService, OpmlService, OpmlImportService, OpmlImportResult, FeedFetcher, FetchResult, NotFoundException, FeedFetchException
├── integration/      RaindropService, RaindropApiClientImpl (with Resilience4j @CircuitBreaker + @Retry), RaindropConfig
├── event/            FeedSavedEvent, FeedDeletedEvent (after-commit feed scheduling events)
├── controller/       Feed/Article/Folder/Board/IntegrationConfig/Opml/Version controllers + PaginatedResponse + GlobalExceptionHandler + request DTOs (SubscribeRequest, MarkReadRequest, ArticleStateRequest, FeedUpdateRequest + board/folder request records)
├── scheduler/        FeedPollingScheduler (dynamic per-feed scheduling with backoff)
└── MyfeederApplication.java (@EnableScheduling, @ConfigurationPropertiesScan)
```

## Key Behaviors

- **Feed scheduling is event-driven**: `FeedService`/`OpmlImportService` publish `FeedSavedEvent`/`FeedDeletedEvent`; `FeedPollingScheduler` listens with `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. New feed-mutating code paths just publish the event — never call the scheduler directly.
- **FeedPollingService** deduplicates articles by GUID on upsert
- **FeedFetcher is the single feed-fetch path**: both subscribe and polling go through it (conditional ETag/If-Modified-Since requests, charset-correct decoding from the response Content-Type, HTTP errors → `FeedFetchException` → 422). It also validates the URL via `FeedUrlValidator` (SSRF guard: http/https only; rejects loopback/link-local/RFC1918/any-local/multicast → 400) and bounds the body read to `DEFAULT_MAX_FEED_BYTES` (10 MiB → 422). Don't fetch feed content with a raw `RestClient` — both protections would be lost.
- **FeedPollingScheduler** uses `ApplicationReadyEvent` to register all feeds at startup; after every poll it re-reads the feed's error state and replaces the task when the effective interval changed, so exponential backoff engages (and clears) while the app runs
- **Article sort order**: Articles are sorted by `COALESCE(published_at, fetched_at)` not by `id`. Batch-fetched articles get sequential IDs but varied publication dates, so `ORDER BY id` does not produce chronological order. Cursor pagination uses composite `(published_at, id)` comparison — the cursor is still a single article ID, but the service looks up the cursor article's date for the SQL comparison. Paginated endpoints return `PaginatedResponse` (`{items, nextCursor}` — field is `items`, not `articles`); the limit+1 trimming lives in `PaginatedResponse.of(...)`, so controllers fetch `limit + 1` and delegate.
- **ReadingPane fetches by ID**: The reading pane uses `useArticle(id)` to fetch the selected article directly (`GET /api/articles/{id}`), not by searching through the paginated list query. This avoids filter/sort mismatches between the article list and reading pane.
- **RetentionService** is a `@Scheduled` cron job — config under `myfeeder.retention.*`
- **OpmlService** has XXE protection enabled — maintain this when modifying XML parsing
- **OpmlImportService** publishes `FeedSavedEvent` per new feed; the scheduler's `@TransactionalEventListener(AFTER_COMMIT)` registers them post-commit (no manual `TransactionSynchronization`)
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
- Flyway migrations: `V1__initial_schema.sql` (feeds, articles, integration_configs), `V2__folders_boards_and_feed_folder.sql` (folders, boards, board_articles, feed.folder_id), `V3__article_image_url.sql`, `V4__strip_raindrop_api_token.sql`

## Deployment

### Cut a release (full pipeline)

"Cut a release / create and deploy a new release" means this sequence, in this order (run 3× for 0.1.16–0.1.18; details on each step in the bullets below):

```bash
./gradlew release                       # 1. cut + push the release tag (axion) — BEFORE building, so the jar gets the release version
./gradlew clean bootJar                 # 2. build the jar (clean forces fresh frontend embed)
VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')
docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:$VERSION .
docker push registry.bartram.org/bartram/myfeeder:$VERSION
./deploy.sh $VERSION                    # needs MYFEEDER_PG_PASSWORD + MYFEEDER_ANTHROPIC_API_KEY (MYFEEDER_RAINDROP_API_TOKEN optional)
kubectl -n myfeeder rollout status deploy/myfeeder
kubectl -n myfeeder logs deploy/myfeeder --tail=20   # verify clean startup
```

Ordering matters: `release` before `bootJar` (else the jar is stamped `-SNAPSHOT`); always pass `$VERSION` to `deploy.sh` explicitly (no arg → axion computes the *next* snapshot, which won't match any pushed image).

### Reference

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys app + Redis; Postgres is external at `pg.bartram.org`
- **Build image** (Dockerfile, not buildpacks — see Gotchas): first build the jar on the host with `./gradlew clean bootJar` (compiles + embeds the frontend via `npmBuild`→`processResources`, stamps the axion-release version into build-info), then `docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:<version> .` (the `Dockerfile` only packages `build/libs/*.jar` into a JRE runtime; `--provenance=false` keeps the pushed artifact a plain single-platform image instead of a buildkit attestation index). Use `clean` so the latest frontend bundle is included; `<version>` comes from `./gradlew currentVersion -q`.
- **Push image**: `docker push registry.bartram.org/bartram/myfeeder:<version>` (build does NOT push)
- **Cut release tag**: `./gradlew release` (creates tag locally via axion-release, pushes via git CLI — see Gotchas)
- **Deploy**: `./deploy.sh [version]` (requires `MYFEEDER_PG_PASSWORD` and `MYFEEDER_ANTHROPIC_API_KEY` env vars; no arg → axion computes the *next* snapshot version, which won't match a built image, so for chart-only redeploys against a release tag pass it explicitly: `./deploy.sh 0.1.2`)


## Key Conventions

- Base package: `org.bartram.myfeeder`
- Uses Spring Data JDBC (not JPA) -- entities use `@Table`/`@Id` annotations from `org.springframework.data.annotation`, not `jakarta.persistence`
- Gradle Kotlin DSL for build configuration
- BOM-managed versions for Spring AI and Spring Cloud (do not specify versions on individual dependencies)
- Resilience4j: Use `@CircuitBreaker(name = "...")` (outer) + `@Retry(name = "...")` (inner) annotations on external service calls. Put them on the **API-client bean** (e.g. `RaindropApiClientImpl`), not on the service — so business validation (not-configured/disabled/no-collection checks in `RaindropService`) runs outside the breaker and only the real HTTP call is wrapped. (Self-invocation bypasses the AOP proxy, so the annotated methods must live on a separate bean that the service calls.) Config in `application.yaml` under `resilience4j.circuitbreaker.instances` and `resilience4j.retry.instances`
- **Resilience4j fallback re-throw pattern**: a `@CircuitBreaker` fallback wraps everything as a 5xx (`IllegalStateException`→409) by default — the client's fallback `instanceof`-checks and rethrows `RaindropNotConfiguredException` (503) before wrapping everything else. Business-rule exceptions (400 "no collection", 409 "disabled") never reach the fallback because that validation happens in `RaindropService` before the client call. Also list `RaindropNotConfiguredException` under `ignore-exceptions` in both the circuit-breaker and retry instances so a missing token never opens the breaker or burns retries
- **GlobalExceptionHandler mappings**: `NotFoundException` → 404 (missing path entity), `IllegalArgumentException` → 400 (Bad Request), `OpmlParseException` → 400, `IllegalStateException` → 409 (Configuration error), `FeedParseException` → 422, `FeedFetchException` → 422 (remote returned an HTTP error), `RaindropNotConfiguredException` → 503. Throw the right type from services and the controller layer doesn't need try/catch
- Spring Data JDBC does not support derived query methods like JPA — use `@Query` annotation for custom queries

## Spring Boot 4 / Jackson 3.x Notes

- Jackson 3.x **databind** moved to `tools.jackson.databind.*` (`ObjectMapper`, `JsonNode`, `JsonMapper`). Spring Boot auto-configures `tools.jackson.databind.ObjectMapper` as a bean.
- Jackson 3.x **annotations** did NOT move — `@JsonProperty`, `@JsonIgnoreProperties`, `@JsonIgnore`, `@JsonCreator` etc. are still in `com.fasterxml.jackson.annotation.*` (jackson-annotations 2.x is a transitive dep of jackson-databind 3.x). There is no `tools.jackson.annotation` package.
- Test annotations `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest` are in `org.springframework.boot.*.test.autoconfigure` packages (e.g., `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`).
- Test starter dependencies follow the pattern `spring-boot-starter-<module>-test` (e.g., `spring-boot-starter-webmvc-test`).
- `RestClientCustomizer` is in `org.springframework.boot.restclient` (Boot 4 moved it out of `org.springframework.boot.web.client`). Spring Boot auto-applies any `RestClientCustomizer` bean to the auto-configured `RestClient.Builder` injected into services.
- Outbound HTTP timeouts/redirects for the auto-configured `RestClient` are set via `spring.http.client.connect-timeout` / `read-timeout` / `redirects` (metadata lives in `spring-boot-http-client-*.jar`, not `-autoconfigure`); they apply globally to every `RestClient` (feed fetches + Raindrop). Currently 5s/30s in `application.yaml`.
- `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7. Use `HttpStatus.valueOf(422)` (or `UNPROCESSABLE_CONTENT` once it's exposed).

## Gotchas

- **Docker required**: Must be running for both `./gradlew test` (Testcontainers) and `./gradlew bootRun` (Docker Compose)
- **Zustand persist + new preferences**: Adding a new field to `preferencesStore` with a default value only applies to fresh installs. Existing users with a `myfeeder-prefs` localStorage key get `undefined` for the new field (Zustand merges stored state over defaults). Use a `merge` function or version migration if the default must apply to everyone.
- **Spring Data JDBC ≠ JPA**: No lazy loading, no derived query methods, no `@Entity` — use `@Table`/`@Id` from `org.springframework.data.annotation` and `@Query` for custom queries
- **Jackson 3.x imports**: Must use `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`
- **FeedPollingScheduler is event-driven**: feed mutations publish `FeedSavedEvent`/`FeedDeletedEvent`; the scheduler (re-)registers or cancels via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. New feed-mutating paths publish the event — never call `registerFeed`/`cancelFeed` directly.
- **MaxDirectMemorySize (historical, Paketo-only)**: the old Paketo buildpack hardcoded `-XX:MaxDirectMemorySize=10M`, which starved Netty (Lettuce/Redis); the Helm chart overrides it via the `JDK_JAVA_OPTIONS` env var. The Dockerfile (`eclipse-temurin:25-jre`) has no such cap — direct memory defaults are container-aware — so the override is no longer required, but the chart still sets `JDK_JAVA_OPTIONS` and the JVM honors it. Tune JVM flags via `JDK_JAVA_OPTIONS` (auto-read by the `java -jar` entrypoint), not `_JAVA_OPTIONS`.
- **Frontend not in image**: the image is `docker build`-ed from `build/libs/*.jar`, so the frontend must already be embedded in that jar. Always build with `./gradlew clean bootJar` before `docker build` — `clean` forces `npmBuild`→`processResources` to repackage the current SPA bundle. A stale jar in `build/libs/` will ship an old frontend.
- **SNAPSHOT tags + pullPolicy**: `imagePullPolicy: IfNotPresent` causes k8s to reuse stale images when the same SNAPSHOT tag is pushed. Use `Always` during development; `IfNotPresent` is only safe with immutable release tags.
- **Gradle terminal escapes in scripts**: `./gradlew currentVersion -q` outputs terminal control sequences. In shell scripts, pipe through `grep 'Project version'` before parsing to avoid contaminating variables.
- **`./gradlew release` push uses git CLI**: axion-release's bundled jgit can't read OpenSSH-format keys. `release` and `pushRelease` in `build.gradle.kts` clear their built-in push action and finalize via a `gitPushRelease` Exec task. Don't replace this with raw axion auth config.
- **`./gradlew release` tag push can fail on a stray git-lfs pre-push hook**: a global `git lfs install` leaves a `pre-push` hook in `.git/hooks/` that runs `git lfs pre-push` even though this repo has **no** LFS content. It intermittently fails the push with "Remote origin does not support the Git LFS locking API" / "Unable to verify locks", blocking `gitPushRelease` (and plain `git push`). Fix (persistent, safe — no LFS data here): `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false`. Already set in this clone; re-apply after a fresh re-clone.
- **Helm probes have a startupProbe**: `startupProbe` (5s × 60 = up to 5 min) gates `livenessProbe` and `readinessProbe`. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to liveness — startupProbe is the gate.
- **Clipboard API requires HTTPS**: The app is served over HTTP (`192.168.44.204`), so `navigator.clipboard` is unavailable. Use `document.execCommand('copy')` fallback for clipboard operations.
- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`**: the `BuildProperties` bean is absent in controller-slice tests. Either inject with `@Autowired(required = false)` and handle `null`, or `@Import` a test config that exposes a `BuildProperties` bean from a `Properties` literal. Same caveat applies to other `info.*` / actuator auto-configured beans.
- **Outbound `RestClient` User-Agent**: the JDK HttpClient default is `Java-http-client/<version>`, which Vercel and other CDNs rate-limit by returning HTTP 200 with body `{"data":"too many requests"}`. `config/RestClientConfig.java` registers a `RestClientCustomizer` that sets `myfeeder/<version>` on every outbound `RestClient.Builder` — don't bypass it by constructing a raw `RestClient.builder()` without going through the auto-configured bean.
- **`FeedParser.parse` rejects empty results**: if a response yields no title and no articles, it throws `FeedParseException` (mapped to 422 by `GlobalExceptionHandler`). This catches "200 OK with garbage" responses that would otherwise save a NULL-title feed and violate `feed.title NOT NULL`.
- **`pg.bartram.org` is split-horizon DNS**: only the LAN resolver (`192.168.44.6`/pi1) has the `192.168.44.206` record; public resolvers (1.1.1.1, Cloudflare) return no answer. The k3s nodes must use `192.168.44.6` as a resolver (configured in `k3s-ansible` via the `lan_dns_servers` var on the `prereq` role) and a CoreDNS forward block in the `coredns-custom` ConfigMap routes `bartram.org` queries there. cert-manager bypasses cluster DNS entirely via `--dns01-recursive-nameservers-only` so ACME challenges still resolve public records on Cloudflare.
- **Docker 29's containerd image store corrupts buildpack image exports** (pods fail to start with containerd `failed to pull and unpack … wrong diff id "sha256:…" calculated on extraction "sha256:…"`): this is why the build moved off `bootBuildImage` to a `Dockerfile` (see Deployment). Root cause: Docker 29's `dockerd` defaults to the **containerd-backed image store** (`docker info` shows `Storage Driver: overlayfs` + `driver-type io.containerd.snapshotter.v1`), and that store **mis-derives diffIDs when *exporting* certain images** — a known moby bug class ([moby/moby#47150](https://github.com/moby/moby/issues/47150), `docker save` produces wrong diffIDs). For a Paketo `bootBuildImage` image the exported **manifest** listed the wrong blob (the 3 KB `os-release` layer) in the base-layer slots while the config's `rootfs.diff_ids` stayed correct; strict consumers (k3s containerd) re-verify each layer against its diffID and refuse the image. Key scoping facts: it is **not** universal — plain `docker build` images (e.g. the `budget` project on the same engine) export fine; it only bites the **export → strict-pull** path, so the image still **runs locally** (`docker run` uses the daemon's internal store and never re-derives diffIDs) and survives a Docker-only `push`/`pull` (moby's puller is lenient). It reproduces via both `docker push` and `bootBuildImage publish=true`. Fixes, in order of preference: (1) **build via `Dockerfile`** (adopted — immune regardless of image store); (2) set `{"features":{"containerd-snapshotter":false}}` in the docker daemon config to revert to the `overlay2` graphdriver store, if Docker still allows opting out; (3) build with Docker ≤ 28. Emergency stopgap (used once for `0.1.16`): `skopeo copy docker://<image> dir:/tmp/img`, recover the correct base-layer bytes from the run image (`docker save <run-image>`, gunzip layers whose uncompressed sha256 matches the config `diff_ids`), patch the bad manifest slots' `digest`+`size`, re-verify every layer, then `skopeo copy dir:/tmp/img docker://<image>`.

## Test Patterns

- **Unit tests**: Mockito with `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for services
- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean` for dependencies
- **Repository tests**: `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)` for real Postgres
- **Parser tests**: Plain unit tests with sample feed files in `src/test/resources/feeds/`
- **Integration test**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` verifying all beans wire correctly
- **Migration tests**: Flyway runs at `@DataJdbcTest` startup, so tests can't insert a legacy-shape row and "re-run" the migration. Pattern: insert a legacy-shape row via `JdbcTemplate.update(...)`, then re-execute the migration SQL manually against it, then assert the post-migration shape. Example: `V4StripRaindropApiTokenMigrationTest`
- Test `application.yaml` must include `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key`
- **Frontend `vi.mock` of `preferencesStore` is full-replacement** (e.g. `SettingsDialog.test.tsx`) — adding a new export to `preferencesStore` requires updating every mock that consumes it, otherwise tests fail with `No "X" export is defined on the mock`. Prefer `vi.mock(..., async (importOriginal) => ({ ...await importOriginal(), usePreferences: ... }))` for new tests.

## Homelab

Shared infra facts (registry, pg, k3s nodes, LB IPs, deploy conventions): @../HOMELAB.md

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
