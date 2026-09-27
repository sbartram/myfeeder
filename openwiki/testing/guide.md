---
type: Testing Guide
title: myfeeder Testing Guide
description: Summarizes the test patterns used across myfeeder's backend (JUnit/Mockito/Testcontainers/WebMvcTest) and frontend (Vitest/React Testing Library), where to find representative tests for each layer, and gotchas that have caused false-positive or brittle tests in the past.
resource: src/test/java/org/bartram/myfeeder
tags: [testing, junit, vitest, testcontainers]
verified:
  - by: openwiki/0.6.0
    at: 2026-09-27T13:47:46.861Z
sources:
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-92b6e647240c8425fe97dcaa
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedFetcher.java
  - id: openwiki-source-0ca4313738bd1faeedf7586c
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
  - id: openwiki-source-c058c81a0fd47b404bea49ab
    resource: repo://src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java
  - id: openwiki-source-e047e7f3555fcca942b55b9d
    resource: repo://src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - id: openwiki-source-1ff5bdeec629441f7ddc8559
    resource: repo://src/test/java/org/bartram/myfeeder/service/ArticleExtractionServiceTest.java
  - id: openwiki-source-02211be22591a8c2606677e5
    resource: repo://src/test/java/org/bartram/myfeeder/service/FeedFetcherTest.java
  - id: openwiki-source-13380f46e0f48b93b264295a
    resource: repo://src/test/java/org/bartram/myfeeder/service/FeedUrlValidatorTest.java
generated: { by: "openwiki/0.6.0", at: "2026-09-27T13:47:46.861Z" }
---

# Testing Guide

## Backend test patterns

| Layer | Pattern | Example |
|---|---|---|
| Service unit tests | Mockito, `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` | `FeedServiceTest`, `FeedPollingServiceTest`, `ArticleServiceTest`, `RaindropServiceTest`, `ArticleExtractionServiceTest` |
| Outbound-fetch / SSRF-guard tests | Plain unit tests with an injectable DNS resolver stub (no real network); `MockRestServiceServer` for the HTTP layer | `FeedUrlValidatorTest`, `FeedFetcherTest` |
| Controller tests | `@WebMvcTest` + `MockMvc` + `@MockitoBean` for service deps | `ArticleControllerTest`, `FeedControllerTest`, `BoardControllerTest` |
| Repository tests | `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` against real Postgres | under `src/test/java/.../repository/` |
| Parser tests | Plain unit tests against sample feed files | `FeedParserTest`, samples in `src/test/resources/feeds/` |
| Full integration | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, verifies all beans wire | `MyfeederApplicationTests` |
| Migration tests | Insert a legacy-shape row via `JdbcTemplate`, re-run the migration SQL manually, assert post-migration shape (Flyway already ran at `@DataJdbcTest` startup, so a fresh DB can't "re-run" an old migration naturally) | `V4StripRaindropApiTokenMigrationTest` |
| Platform/auto-configuration tests | `ApplicationContextRunner` with only the relevant Boot auto-configuration classes loaded, no Docker required | `HttpClientConfigurationTest` |

`FeedUrlValidatorTest` covers the SSRF guard in `FeedUrlValidator`: it stubs `FeedUrlValidator`'s package-private `HostResolver` functional interface to a fixed literal IP (avoiding real DNS) and asserts that public hosts are allowed while loopback, RFC1918, link-local/metadata (`169.254.169.254`), IPv6 unique-local (`fc00::/7`), CGNAT (`100.64.0.0/10`), broadcast, and IPv4-mapped-loopback addresses are all rejected with a "non-public" message; non-http(s) schemes and malformed URLs are rejected separately. `FeedFetcherTest` exercises `FeedFetcher` against a `MockRestServiceServer`-backed `RestClient`, using a validator stub that resolves every test host to a public IP so the SSRF guard itself never blocks the fixture requests; it covers raw-byte-plus-charset retrieval, conditional `If-None-Match`/`If-Modified-Since` requests mapping a 304 to `FetchResult.notModified()`, ETag/Last-Modified capture, `FeedFetchException` on error statuses, and — via the package-private constructor overload that exposes the byte cap — rejection of a response body exceeding the configured size limit. `ArticleExtractionServiceTest` covers the reader-view extraction flow in `ArticleExtractionService`: caching (extract-and-save on first request, serve-cached without re-fetching on repeat requests, asserted via `verifyNoInteractions(feedFetcher)`), 404 for a missing article, 400 for an article with no URL, and mapping a page with no extractable content (via `Readability4J`) to `FeedParseException`. All three are new since the last review and pertain to the SSRF guard and reader-view feature — see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md) and [Architecture Overview](../architecture/overview.md) for the surrounding design.

Run all backend tests: `./gradlew test` (requires Docker for Testcontainers). Run one class: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"`.

`src/test/resources/application.yaml` must define `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key` for the context to load.

### Gotchas specific to backend tests

- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`** — the `BuildProperties` bean (used by `VersionController`) is absent in controller-slice tests. Either `@Autowired(required = false)` and handle `null`, or `@Import` a test config exposing a `BuildProperties` bean from a `Properties` literal. The same caveat applies to other actuator/`info.*` auto-configured beans.
- Resilience4j-annotated methods (`RaindropApiClientImpl`) are AOP-proxy-based — testing fallback behavior means going through the proxy, not calling the private fallback method directly; see `RaindropApiClientImplTest` for the pattern.
- **`HttpClientConfigurationTest` guards the `spring.http.clients.*` timeout configuration** introduced with the Boot 4.0.8 upgrade. It builds a bare `ApplicationContextRunner` with only `HttpClientAutoConfiguration`/`ImperativeHttpClientAutoConfiguration` loaded (no Docker, no full context), asserts the auto-configured outbound transport is `ReactorClientHttpRequestFactory`, and — by loading `src/main/resources/application.yaml` from disk directly, since `src/test/resources/application.yaml` shadows it on the test classpath — asserts the bound `HttpClientSettings` connect/read timeouts (`5s`/`30s`) actually take effect. This exists because `MockRestServiceServer`-based tests (`FeedFetcherTest`, Raindrop client tests) replace the request factory entirely and so cannot catch a regression in the real transport or timeout wiring; a change to `spring.http.clients.*` in `application.yaml` or to the HTTP client auto-configuration should be checked against this test.

## Frontend test patterns

Vitest + React Testing Library. Run: `cd src/main/frontend && npm test`.

Representative tests live next to their source: `ArticleList.test.tsx`, `AppShell.test.tsx`, `MarkOlderReadDialog.test.tsx`, `SettingsDialog.test.tsx`, `useKeyboardShortcuts.test.ts`, `useFolders.test.ts`, `useOpml.test.ts`, `useTheme.test.ts`, `useVersion.test.ts`, `api/client.test.ts`.

### Gotcha: `vi.mock` of `preferencesStore` is full-replacement

`SettingsDialog.test.tsx` (and similar tests) `vi.mock` the entire `preferencesStore` module. Adding a new export to `preferencesStore` requires updating **every** mock that consumes it, or tests fail with `No "X" export is defined on the mock`. Prefer:

```ts
vi.mock('../stores/preferencesStore', async (importOriginal) => ({
  ...await importOriginal(),
  usePreferences: /* override */,
}))
```

for new tests touching `preferencesStore`, rather than a full manual replacement object.

### Type-checking

Use `npx tsc -b` from `src/main/frontend/` — plain `tsc --noEmit` returns success even with real type errors, because the root `tsconfig.json` has `files: []` and relies on project references.

## What to check when changing each major area

- **Feed lifecycle changes** ([Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)): `FeedPollingSchedulerTest`, `FeedPollingServiceTest`, `FeedServiceTest`, `OpmlImportServiceTest` — the backoff/self-adjusting-interval logic in particular has a history of subtle bugs (`933950e`, `c95abec`) worth re-reading before touching `computeEffectiveInterval` or `pollAndAdjust`.
- **Domain/API changes** ([Domain Concepts](../domain/concepts.md)): the relevant `*ControllerTest` plus `PaginatedResponseTest` if pagination/cursor logic is touched; `ArticleServiceTest` for sort-order/cursor comparison logic.
- **Raindrop changes** ([Raindrop Integration](../integrations/raindrop.md)): `RaindropServiceTest` (business rules) and `RaindropApiClientImplTest` (resilience/fallback behavior) — both must stay green if you touch the ignore-exceptions config in `application.yaml`.
- **Outbound-fetch / SSRF changes** ([Feed Lifecycle Workflow](../workflows/feed-lifecycle.md), [Architecture Overview](../architecture/overview.md)): `FeedUrlValidatorTest` (scheme/host/IP-range rejection rules) and `FeedFetcherTest` (conditional-request mapping, error handling, and the response-size cap) — both must stay green if you touch `FeedUrlValidator`'s non-public-address ranges or `FeedFetcher`'s `maxFeedBytes`/conditional-header logic, since `ArticleExtractionService` and the feed-polling path both depend on `FeedFetcher` for the SSRF guard and size cap.
- **Reader-view / extraction changes** ([Domain Concepts](../domain/concepts.md), [Architecture Overview](../architecture/overview.md)): `ArticleExtractionServiceTest` (caching, 404/400 mapping, `Readability4J` no-content failure) plus the extracted-content slice tests in `ArticleControllerTest` (`shouldReturnExtractedContent`, `extractedContentReturns404ForMissingArticle`, `extractedContentReturns422WhenPageFetchFails`) for the `GET /api/articles/{id}/extracted-content` HTTP-status mapping.
- **Frontend keyboard/state changes** ([Architecture Overview](../architecture/overview.md)): `useKeyboardShortcuts.test.ts` is the most complex frontend test file (7.9KB) — covers chord handling and the current-article resolution fallback.
