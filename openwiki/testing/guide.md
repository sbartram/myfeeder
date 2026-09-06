---
type: Testing Guide
title: myfeeder Testing Guide
description: Summarizes the test patterns used across myfeeder's backend (JUnit/Mockito/Testcontainers/WebMvcTest) and frontend (Vitest/React Testing Library), where to find representative tests for each layer, and gotchas that have caused false-positive or brittle tests in the past, including SSRF-guard (FeedUrlValidator), size-capped fetch (FeedFetcher), and reader-view extraction (ArticleExtractionService/ReadingPane) coverage.
resource: src/test/java/org/bartram/myfeeder
tags: [testing, junit, vitest, testcontainers]
verified:
  - by: openwiki/0.5.0
    at: 2026-09-06T12:04:59.431Z
sources:
  - id: openwiki-source-83ccd47a37846705bb2f1fd0
    resource: repo://src/main/frontend/src/components/ReadingPane.test.tsx
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-92b6e647240c8425fe97dcaa
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedFetcher.java
  - id: openwiki-source-0ca4313738bd1faeedf7586c
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java
  - id: openwiki-source-1ff5bdeec629441f7ddc8559
    resource: repo://src/test/java/org/bartram/myfeeder/service/ArticleExtractionServiceTest.java
  - id: openwiki-source-02211be22591a8c2606677e5
    resource: repo://src/test/java/org/bartram/myfeeder/service/FeedFetcherTest.java
  - id: openwiki-source-13380f46e0f48b93b264295a
    resource: repo://src/test/java/org/bartram/myfeeder/service/FeedUrlValidatorTest.java
generated: { by: "openwiki/0.5.0", at: "2026-09-06T12:04:59.431Z" }
---

# Testing Guide

## Backend test patterns

| Layer | Pattern | Example |
|---|---|---|
| Service unit tests | Mockito, `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` | `FeedServiceTest`, `FeedPollingServiceTest`, `ArticleServiceTest`, `RaindropServiceTest`, `ArticleExtractionServiceTest` |
| Controller tests | `@WebMvcTest` + `MockMvc` + `@MockitoBean` for service deps | `ArticleControllerTest`, `FeedControllerTest`, `BoardControllerTest` |
| Repository tests | `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` against real Postgres | under `src/test/java/.../repository/` |
| Parser tests | Plain unit tests against sample feed files | `FeedParserTest`, samples in `src/test/resources/feeds/` |
| HTTP fetch tests | Plain unit test against `MockRestServiceServer` bound to the same `RestClient.Builder` the class under test uses | `FeedFetcherTest` |
| URL/SSRF validation tests | Plain unit test constructing `FeedUrlValidator` with a stubbed `HostResolver` (package-private constructor) instead of live DNS | `FeedUrlValidatorTest` |
| Full integration | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, verifies all beans wire | `MyfeederApplicationTests` |
| Migration tests | Insert a legacy-shape row via `JdbcTemplate`, re-run the migration SQL manually, assert post-migration shape (Flyway already ran at `@DataJdbcTest` startup, so a fresh DB can't "re-run" an old migration naturally) | `V4StripRaindropApiTokenMigrationTest` |

Run all backend tests: `./gradlew test` (requires Docker for Testcontainers). Run one class: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"`.

`src/test/resources/application.yaml` must define `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key` for the context to load.

### Gotchas specific to backend tests

- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`** — the `BuildProperties` bean (used by `VersionController`) is absent in controller-slice tests. Either `@Autowired(required = false)` and handle `null`, or `@Import` a test config exposing a `BuildProperties` bean from a `Properties` literal. The same caveat applies to other actuator/`info.*` auto-configured beans.
- Resilience4j-annotated methods (`RaindropApiClientImpl`) are AOP-proxy-based — testing fallback behavior means going through the proxy, not calling the private fallback method directly; see `RaindropApiClientImplTest` for the pattern.
- **`FeedUrlValidator` has a package-private `HostResolver`-taking constructor** (`FeedUrlValidator.HostResolver`, a `@FunctionalInterface`) purely so tests can stub DNS resolution instead of depending on live network lookups for SSRF-guard cases like RFC1918/loopback/link-local/CGNAT addresses. `FeedUrlValidatorTest` builds one validator per resolved-address case via `validatorResolvingTo(ip)`; don't add a real-DNS-dependent test for a new blocked-range case, follow the same stubbed-resolver pattern.
- **`FeedFetcherTest` builds its own `MockRestServiceServer`** bound to the `RestClient.Builder` passed into `FeedFetcher`'s test-only three-arg constructor (`RestClient.Builder, FeedUrlValidator, int maxFeedBytes`), which also lets tests override `DEFAULT_MAX_FEED_BYTES` to exercise the size-cap without a 10MB fixture. A `FeedUrlValidator` stubbed to resolve to `8.8.8.8` (`ALLOW_ALL`) is reused across most cases so validation doesn't interfere with tests that are really about HTTP handling; a separate test (`rejectsNonPublicUrlBeforeFetching`) uses a validator stubbed to resolve to `127.0.0.1` and asserts no HTTP request is recorded, proving the SSRF check runs before the network call.

## Frontend test patterns

Vitest + React Testing Library. Run: `cd src/main/frontend && npm test`.

Representative tests live next to their source: `ArticleList.test.tsx`, `AppShell.test.tsx`, `MarkOlderReadDialog.test.tsx`, `SettingsDialog.test.tsx`, `ReadingPane.test.tsx`, `useKeyboardShortcuts.test.ts`, `useFolders.test.ts`, `useOpml.test.ts`, `useTheme.test.ts`, `useVersion.test.ts`, `api/client.test.ts`.

`ReadingPane.test.tsx` mocks `useArticle`/`useExtractedArticle` (from `useArticles`) and drives the reader-view toggle end to end at the component level: it asserts `useExtractedArticle(id, enabled)` is called with `enabled=false` while the feed already supplied `content`, auto-flips to `enabled=true` when an article has neither `content` nor `summary`, and re-renders extracted `contentHtml` — including asserting inline `style` attributes are stripped — once the user clicks the "📖 Reader View" toggle. Separate cases cover the pending/loading and error fallback UI states surfaced while extraction is in flight or fails.

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
- **Feed fetching changes** (`FeedFetcher`/`FeedUrlValidator`, used by both feed polling and reader-view extraction): `FeedUrlValidatorTest` — covers the injectable `HostResolver` seam and every non-public-address case that must stay blocked (loopback, RFC1918, link-local/metadata, CGNAT `100.64.0.0/10`, IPv6 unique-local `fc00::/7`, IPv4-mapped loopback, broadcast) alongside the public IPv4/IPv6 allow cases; and `FeedFetcherTest`'s `rejectsNonPublicUrlBeforeFetching` case, which stubs the resolver to a blocked address and asserts on the *absence* of any recorded HTTP request — proving the SSRF check runs before the network call, not just that it eventually throws. Also re-run `FeedFetcherTest`'s `rejectsBodyExceedingMaxSize`/`acceptsBodyWithinMaxSize` cases if you touch `maxFeedBytes`/`DEFAULT_MAX_FEED_BYTES`.
- **Domain/API changes** ([Domain Concepts](../domain/concepts.md)): the relevant `*ControllerTest` plus `PaginatedResponseTest` if pagination/cursor logic is touched; `ArticleServiceTest` for sort-order/cursor comparison logic.
- **Reader-view/extraction changes** ([Reader View Extraction Workflow](../workflows/reader-view-extraction.md)): `ArticleExtractionServiceTest` — covers Readability4J extraction and caching (`saveExtractedContent`/`findExtractedContent`), the not-found/no-URL error paths, and the "nothing extractable" `FeedParseException` path — plus `ReadingPane.test.tsx`'s reader-view toggle tests, which cover the frontend's auto-load-when-no-content behavior, the manual toggle, and the loading/error states surfaced while `useExtractedArticle` is pending or fails.
- **Raindrop changes** ([Raindrop Integration](../integrations/raindrop.md)): `RaindropServiceTest` (business rules) and `RaindropApiClientImplTest` (resilience/fallback behavior) — both must stay green if you touch the ignore-exceptions config in `application.yaml`.
- **Frontend keyboard/state changes** ([Architecture Overview](../architecture/overview.md)): `useKeyboardShortcuts.test.ts` is the most complex frontend test file (7.9KB) — covers chord handling and the current-article resolution fallback.
