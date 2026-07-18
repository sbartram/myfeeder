---
type: Testing Guide
title: myfeeder Testing Guide
description: Summarizes the test patterns used across myfeeder's backend (JUnit/Mockito/Testcontainers/WebMvcTest) and frontend (Vitest/React Testing Library), where to find representative tests for each layer, and gotchas that have caused false-positive or brittle tests in the past.
resource: src/test/java/org/bartram/myfeeder
tags: [testing, junit, vitest, testcontainers]
---

# Testing Guide

## Backend test patterns

| Layer | Pattern | Example |
|---|---|---|
| Service unit tests | Mockito, `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` | `FeedServiceTest`, `FeedPollingServiceTest`, `ArticleServiceTest`, `RaindropServiceTest` |
| Controller tests | `@WebMvcTest` + `MockMvc` + `@MockitoBean` for service deps | `ArticleControllerTest`, `FeedControllerTest`, `BoardControllerTest` |
| Repository tests | `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` against real Postgres | under `src/test/java/.../repository/` |
| Parser tests | Plain unit tests against sample feed files | `FeedParserTest`, samples in `src/test/resources/feeds/` |
| Full integration | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, verifies all beans wire | `MyfeederApplicationTests` |
| Migration tests | Insert a legacy-shape row via `JdbcTemplate`, re-run the migration SQL manually, assert post-migration shape (Flyway already ran at `@DataJdbcTest` startup, so a fresh DB can't "re-run" an old migration naturally) | `V4StripRaindropApiTokenMigrationTest` |

Run all backend tests: `./gradlew test` (requires Docker for Testcontainers). Run one class: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"`.

`src/test/resources/application.yaml` must define `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key` for the context to load.

### Gotchas specific to backend tests

- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`** — the `BuildProperties` bean (used by `VersionController`) is absent in controller-slice tests. Either `@Autowired(required = false)` and handle `null`, or `@Import` a test config exposing a `BuildProperties` bean from a `Properties` literal. The same caveat applies to other actuator/`info.*` auto-configured beans.
- Resilience4j-annotated methods (`RaindropApiClientImpl`) are AOP-proxy-based — testing fallback behavior means going through the proxy, not calling the private fallback method directly; see `RaindropApiClientImplTest` for the pattern.

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
- **Frontend keyboard/state changes** ([Architecture Overview](../architecture/overview.md)): `useKeyboardShortcuts.test.ts` is the most complex frontend test file (7.9KB) — covers chord handling and the current-article resolution fallback.
