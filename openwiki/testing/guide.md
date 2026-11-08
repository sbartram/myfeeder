---
type: Testing Guide
title: myfeeder Testing Guide
description: Summarizes the test patterns used across myfeeder's backend (JUnit/Mockito/Testcontainers/WebMvcTest) and frontend (Vitest/React Testing Library), including the interest-scoring/engagement subsystem's proofs (grid tests, latency tests, migration tests, Jev-mocking patterns), and where to find representative tests for each layer.
resource: src/test/java/org/bartram/myfeeder
tags: [testing, junit, vitest, testcontainers, interest-scoring, engagement]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-08T14:57:22.664Z
sources:
  - id: openwiki-source-85e307461f160ad90bda8ad6
    resource: repo://src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
  - id: openwiki-source-a57d7e53cd49fac081e44335
    resource: repo://src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java
  - id: openwiki-source-cd953d153d1f926895a54be9
    resource: repo://src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java
  - id: openwiki-source-206e2786f678e567177c6e71
    resource: repo://src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java
  - id: openwiki-source-7d7e0e36e54df1da05559741
    resource: repo://src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java
  - id: openwiki-source-7c575fe7d35b5a47d1440e92
    resource: repo://src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java
  - id: openwiki-source-d2b8a99388f4e9c226c75976
    resource: repo://src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java
  - id: openwiki-source-0a38e12a0c35c617bfb46793
    resource: repo://src/test/java/org/bartram/myfeeder/repository/V4StripRaindropApiTokenMigrationTest.java
  - id: openwiki-source-68271ef0e1a2be5bfdfef669
    resource: repo://src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
  - id: openwiki-source-9cd60d7e630b8c9834f0d6f3
    resource: repo://src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java
  - id: openwiki-source-dd2d645abee0bc0755ca1501
    resource: repo://src/test/resources/application-dev.yaml
  - id: openwiki-source-f390f7f52c9adafdf38e7480
    resource: repo://src/test/resources/application.yaml
  - id: openwiki-source-09fe1fc21716ff76df70098f
    resource: repo://src/test/resources/interest/v021-unread-blend.sql
generated: { by: "openwiki/0.9.0", at: "2026-11-08T14:57:22.664Z" }
---

# Testing Guide

## Backend test patterns

| Layer | Pattern | Example |
|---|---|---|
| Service unit tests | Mockito, `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` | `FeedServiceTest`, `FeedPollingServiceTest`, `ArticleServiceTest`, `RaindropServiceTest`, `TopicSuggestionServiceTest` |
| Controller tests | `@WebMvcTest` + `MockMvc` + `@MockitoBean` for service deps | `ArticleControllerTest`, `FeedControllerTest`, `BoardControllerTest` |
| Repository tests | `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` against real Postgres | under `src/test/java/.../repository/` |
| Parser tests | Plain unit tests against sample feed files | `FeedParserTest`, samples in `src/test/resources/feeds/` |
| Full integration | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, verifies all beans wire, or exercises a full controller→service→repository→Postgres slice with Jev mocked | `MyfeederApplicationTests`, `TopicSuggestionApiIntegrationTest`, `PriorityApiIntegrationTest`, `EngagementApiIntegrationTest` |
| Migration tests | Insert a legacy-shape row via `JdbcTemplate`, re-run the migration SQL manually, assert post-migration shape (Flyway already ran at `@DataJdbcTest` startup, so a fresh DB can't "re-run" an old migration naturally) | `V4StripRaindropApiTokenMigrationTest`, `V7EngagementMigrationTest` |

Run all backend tests: `./gradlew test` (requires Docker for Testcontainers). Run one class: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"`.

`src/test/resources/application.yaml` must define `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key` for the context to load.

### Gotchas specific to backend tests

- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`** — the `BuildProperties` bean (used by `VersionController`) is absent in controller-slice tests. Either `@Autowired(required = false)` and handle `null`, or `@Import` a test config exposing a `BuildProperties` bean from a `Properties` literal. The same caveat applies to other actuator/`info.*` auto-configured beans.
- Resilience4j-annotated methods (`RaindropApiClientImpl`) are AOP-proxy-based — testing fallback behavior means going through the proxy, not calling the private fallback method directly; see `RaindropApiClientImplTest` for the pattern.

### The migration-test pattern: re-running old migration SQL against a live schema

Because Flyway has already applied every migration by the time a `@DataJdbcTest` context starts, a test can't get a fresh database back into a pre-migration shape to prove the migration itself. Both migration-test classes work around this the same way:

1. Insert a row shaped like it would have looked **before** the migration ran, directly via `JdbcTemplate` (bypassing the current schema's defaults/constraints where needed).
2. Manually re-issue the migration's own SQL statement(s) against that row.
3. Assert the row now has the **post**-migration shape.

`V4StripRaindropApiTokenMigrationTest` does this for the V4 Raindrop-token-stripping migration: it inserts a legacy `integration_config` row containing `apiToken`, re-runs V4's `UPDATE ... jsonb_build_object('collectionId', ...)` statement, and asserts the resulting `config` column no longer contains `apiToken` but still carries `collectionId`.

`V7EngagementMigrationTest` applies the same idea to the V7 engagement migration, plus direct schema/constraint proofs against the live, already-migrated database: it asserts `article_engagement` has exactly the columns `article_id`, `kind`, `created_at`; that only the four `EngagementKind` values are accepted and a second row of the same kind for the same article is rejected (`DataIntegrityViolationException`); that deleting an `article` or `feed` cascades engagement (and dismissal) rows, while deleting a `board` or `board_article` row does not; and that `topic_suggestion_dismissal` has no `topic` column. It also statically greps `V7__engagement.sql` and the ranking SQL text to guard that the ranking query **never reads `topic_suggestion_dismissal`** — dismissals only gate the suggestions feed, not scoring or priority ranking.

## Interest-scoring and engagement test suite

The interest-scoring/engagement subsystem (see [Interest Scoring Workflow](../workflows/interest-scoring.md) and [Engagement Capture Workflow](../workflows/engagement-capture.md)) carries its own layer of proofs beyond the ordinary service/controller/repository split, because it blends several numeric sources (profile score, learned thumbs weight, learned engagement weight) under hard caps and clamps, and because a regression there is expensive to detect by inspection.

### `InterestLearnedGridTest` — full grid of split combinations

`InterestLearnedGridTest` (`src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java`) proves the thumbs/engagement split of the learned model exactly, against real Postgres, across a grid of 14 bases × 13 thumbs values × 8 engagement values. It binds its own constants (learn-rate 32, learned-cap 20, open 0.25, save 0.5, engagement-cap 8) chosen so that every clamp boundary is reachable without floating-point rounding noise, letting a `BigDecimal` oracle be compared with `compareTo == 0` cell by cell. The oracle applies one sign-plus-range clamp (±50, sign-locked to the topic's base weight) to the whole sum, matching the production SQL's single final clamp.

### `InterestScoreQueriesEngagementTest` / `InterestScoreQueriesZeroEngagementTest` — frozen v0.2.1 blend equivalence

`InterestScoreQueriesEngagementTest` proves the engagement part of the learned model in isolation (an open adds `openWeight × hinge`, a save counts more than an open, every engagement kind on one article counts once as a save, each distinct engaged article counts once, only `SCORED` articles count).

`InterestScoreQueriesZeroEngagementTest` proves the complementary invariant: with the engagement cap set to zero (or no engagement rows at all), every raw score, badge, effective weight and Priority order must equal the **v0.2.1** SQL exactly — frozen in `src/test/resources/interest/v021-unread-blend.sql`, a verbatim copy of the pre-engagement ranking CTE tagged at that release. Comparing against a frozen fixture file (not the current code's own SQL) means the test can't pass merely because both sides changed together; it independently pins backward compatibility at the "no engagement" boundary.

### `InterestScoreQueriesLatencyTest` — 10× baseline + 250ms budget at ~20k engagement rows

`InterestScoreQueriesLatencyTest` seeds 10 topics, 30,000 articles (70% read), 5,000 `SCORED` articles with 10 nouls each, 500 `FAILED` articles, 130 votes, and roughly 20,000 `article_engagement` rows over about 13,000 articles — the recipe recorded in the project's research notes. It times `priorityFirstPage`, `breakdownInputs` and `allTopicWeights` as the median of 5 warm runs (after 3 warm-ups) both **without** and **with** the engagement rows, and asserts the extended-query time stays within `10 × baseline + 250 ms`. This guard only catches catastrophic query-plan regressions (e.g. the planner dropping an index or doing a sequential scan it shouldn't); the printed `LATENCY` and `EXPLAIN ANALYZE` lines are the actual record used for manual verification, not assertions in themselves. The fixture adds no index, migration, or query hint of its own, so a latency regression here reliably points at the production SQL or schema, not test-only tuning.

### `TopicSuggestionStoreTest` / `TopicSuggestionServiceTest` / `TopicSuggestionApiIntegrationTest` — gap discovery, ordering, atomicity, never-calls-Jev

These three classes test the "topic suggestions" (gap-discovery) feature across its three layers:

- `TopicSuggestionStoreTest` (`@DataJdbcTest`) proves the candidate-selection SQL predicate against real Postgres: a candidate must be `SCORED`, carry no vote and no dismissal, have its best noul (across every topic, either weight sign) strictly below the configured near-miss threshold, and fall inside the engagement-recency window.
- `TopicSuggestionServiceTest` (pure Mockito, store and `InterestScoreQueries` mocked) proves the ordering (badge ascending, then latest engagement, then higher id first), the 10-item cap with an accurate `total`, and that a candidate with no computed badge is silently dropped.
- `TopicSuggestionApiIntegrationTest` (`@SpringBootTest` + `TestcontainersConfiguration`, `JevApiClient` as a `@MockitoBean`) is the end-to-end proof: MockMvc → `TopicSuggestionController` → `TopicSuggestionService` → `TopicSuggestionStore`/`InterestScoreQueries` → Postgres. Every test method's `@AfterEach` asserts `verify(jevApiClient, never()).judge(any(), any())` — suggestions must be servable without ever calling Jev, including when Jev is entirely unconfigured. The class also proves the two writes are atomic and idempotent: creating a topic from a suggested article's `sourceArticleId` inserts the topic and the `TOPIC_CREATED` dismissal row together — when the dismissal insert is forced to throw (via a `@MockitoSpyBean` on `TopicSuggestionStore`), the whole `createTopic` call rolls back and neither the topic nor the dismissal row is left behind.

### `V7EngagementMigrationTest` — ranking SQL never reads `topic_suggestion_dismissal`

Covered above under the migration-test pattern; see that section for the schema/constraint/cascade proofs and the static guard that the ranking SQL text never references `topic_suggestion_dismissal`.

### `PriorityApiIntegrationTest` — post-refresh consistency across position/badges/Why-N

`PriorityApiIntegrationTest` is the end-to-end proof for the Priority ranked feed. Besides cursor-walk correctness (no duplicate ids across a paginated walk, and a mid-walk drop in a cursor article's live score skips no row — `cursorScoreDropMidWalkSkipsNoRow`), its `engagedArticleAgreesEverywhereAfterRefresh` test is the key interest-scoring regression guard: after starring an article (engagement), and again after undoing it ("Forget"), the engaged article's `interestScore` must agree **everywhere** — by-id GET, its Why-N breakdown's `display` value and the sum of its breakdown rows' `points`, the feed list's badge, and a fresh Priority walk — and the article's position in the Priority order must follow its score. This is the test that would catch a bug where one read path (e.g. the feed list) uses a stale or differently-computed score than another (e.g. the Priority walk) after an engagement event invalidates cached state.

## Test-vs-main `application.yaml` isolation rule

`./gradlew bootTestRun` and `./gradlew test` both run on the **test classpath**, where `src/test/resources/application.yaml` shadows `src/main/resources/application.yaml`. The test yaml pins Jev (`spring.ai.typesafe`) to an **offline base URL** (`http://127.0.0.1:9`, which refuses connections at once) and defines **no API key**, so that any stray call during the suite fails fast instead of silently hitting — and being billed by — the real Jev/TypeSafe API. It also pins the pre-calibration interest-blend constants that the fixtures in this guide (`InterestScoreQueriesTest`, `FeedbackApiIntegrationTest`, `PriorityApiIntegrationTest`) assume.

`src/test/resources/application-dev.yaml` is a **dev-profile overlay**, loaded only when the `dev` Spring profile is active. It restores `main`'s real, live settings (the real Jev base URL `https://api.typesafe.ai`, the API key read from `MYFEEDER_TYPESAFE_API_KEY`, and `main`'s calibrated blend constants) for local interactive runs via `./gradlew bootTestRun` or `TestMyfeederApplication` in an IDE, which is the only code path that ever activates the `dev` profile (`TestMyfeederApplication.withAdditionalProfiles(...)`).

`DevProfileConfigTest` (`src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java`) enforces this split with Spring Boot's real `ConfigData` pipeline (no Docker needed):

- With the `dev` profile active, every key the overlay restores resolves to `main`'s own live value — proven key-by-key by diffing `src/main/resources/application.yaml` against the test yaml plus the dev overlay.
- Without the `dev` profile (what every ordinary `@SpringBootTest` context sees), the environment stays offline: no API key is set, and the Jev base URL still starts with `http://127.0.0.1`.
- No test file other than `TestMyfeederApplication` itself ever activates the `dev` profile — proven by grepping every test source file for activation tokens (`withAdditionalProfiles`, `@ActiveProfiles`, `SPRING_PROFILES`, etc.), with `MyfeederApplicationTests` (which only *reads* active profiles) used as a positive control that the scan isn't skipping files that should be caught.

**Operational rule**: never export `SPRING_AI_TYPESAFE_*` environment variables or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`. Doing so defeats the offline test yaml and makes the suite call the real, billed Jev API.

### Jev-mocking patterns across backend tests

Three distinct patterns mock or isolate Jev depending on the layer under test:

- **Pure unit tests** (`ArticleScoringServiceTest`, `InterestPreviewServiceTest`, `InterestRescoreServiceTest`, `InterestStatusServiceTest`, `ScoringIsolationTest`, `InterestScoringSweepTest`) use Mockito `@Mock private JevApiClient jevApiClient` with `@InjectMocks`/manual wiring, stubbing `judge(...)` to return a canned `JevJudgment` or throw a typed exception.
- **`@SpringBootTest` controller/integration slices that must never call Jev at all** (`TopicSuggestionApiIntegrationTest`, `EngagementApiIntegrationTest`, `FeedbackApiIntegrationTest`) replace the whole bean with `@MockitoBean private JevApiClient jevApiClient` and assert in `@AfterEach` (or inline) that `judge(...)` was never invoked — proving the feature under test (suggestions, engagement capture, feedback) never triggers a scoring call as a side effect.
- **The transport boundary itself** (`JevApiClientImplTest`, `JevResilienceTest`) does not mock `JevApiClient` at all; instead it stubs the HTTP layer underneath `TypeSafeClient` with Spring's `MockRestServiceServer`, asserting on the actual request shape and exercising the real Resilience4j retry/circuit-breaker chain. See [Jev Integration](../integrations/jev.md) for what that boundary is responsible for.

## Frontend test patterns

Vitest + React Testing Library. Run: `cd src/main/frontend && npm test`.

Representative tests live next to their source: `ArticleList.test.tsx`, `AppShell.test.tsx`, `MarkOlderReadDialog.test.tsx`, `SettingsDialog.test.tsx`, `useKeyboardShortcuts.test.ts`, `useFolders.test.ts`, `useOpml.test.ts`, `useTheme.test.ts`, `useVersion.test.ts`, `api/client.test.ts`.

For the interest-scoring/engagement UI, `InterestsDialog.test.tsx` (the largest frontend test file, covering the profile editor, topic CRUD, learned-adjustment display, and the Suggested-topics/Dismiss flow against a hand-rolled fetch-route stub) and `hooks/engagementReaction.test.ts` (unit tests for `afterEngagement`, the shared post-engagement cache-patching hook used after a star/open/board/Raindrop action) are the representative tests; see [Engagement Capture Workflow](../workflows/engagement-capture.md) for the client-side flow they cover.

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
- **Interest scoring/ranking changes** ([Interest Scoring Workflow](../workflows/interest-scoring.md)): `InterestLearnedGridTest` for the thumbs/engagement split's exact arithmetic; `InterestScoreQueriesEngagementTest`/`InterestScoreQueriesZeroEngagementTest` for the engagement contribution and the frozen v0.2.1 no-engagement equivalence; `InterestScoreQueriesLatencyTest` for the latency budget at scale; `InterestScoreQueriesTest`, `V6InterestScoringMigrationTest` and `TopicSuggestionStoreTest`/`TopicSuggestionServiceTest`/`TopicSuggestionApiIntegrationTest` for the ranking SQL and gap-discovery feature; `PriorityApiIntegrationTest` for end-to-end Priority-order and cross-endpoint score consistency.
- **Engagement capture changes** ([Engagement Capture Workflow](../workflows/engagement-capture.md)): `ArticleEngagementStoreTest` (idempotent writes, kind ordering, cascades) and `V7EngagementMigrationTest` (schema/constraints and the guard that ranking SQL never reads `topic_suggestion_dismissal`) on the backend; `EngagementApiIntegrationTest` for the end-to-end API path with Jev mocked out; `hooks/engagementReaction.test.ts` on the frontend for the shared post-engagement cache-patching hook.
- **Frontend keyboard/state changes** ([Architecture Overview](../architecture/overview.md)): `useKeyboardShortcuts.test.ts` is the most complex frontend test file (7.9KB) — covers chord handling and the current-article resolution fallback.
