---
type: Testing Guide
title: myfeeder Testing Guide
description: Summarizes backend (JUnit/Mockito/Testcontainers/WebMvcTest) and frontend (Vitest/React Testing Library) test patterns, where to find representative tests for each layer including interest-scoring/engagement/suggestion/calibration tests, and gotchas that have caused false-positive or brittle tests.
tags: [testing, junit, vitest, testcontainers, interest-scoring, resilience4j]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-25T13:07:33.493Z
sources:
  - id: openwiki-source-2c322ae808d4737e641de2c4
    resource: repo://src/main/frontend/src/components/FeedPanel.test.tsx
  - id: openwiki-source-c0a4ce08cb43a0cdabdd89d1
    resource: repo://src/main/frontend/src/components/InterestsDialog.test.tsx
  - id: openwiki-source-83ccd47a37846705bb2f1fd0
    resource: repo://src/main/frontend/src/components/ReadingPane.test.tsx
  - id: openwiki-source-3efe87f2261caaef11357f39
    resource: repo://src/main/frontend/src/components/SettingsDialog.test.tsx
  - id: openwiki-source-52aa8eb0a719bbaa1caec17a
    resource: repo://src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
  - id: openwiki-source-7d384330292529ca69355b30
    resource: repo://src/main/frontend/tsconfig.json
  - id: openwiki-source-defad8a304525fdcd6f0dec2
    resource: repo://src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
  - id: openwiki-source-85e307461f160ad90bda8ad6
    resource: repo://src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
  - id: openwiki-source-a57d7e53cd49fac081e44335
    resource: repo://src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java
  - id: openwiki-source-977230c48ae93277ebd24afd
    resource: repo://src/test/java/org/bartram/myfeeder/controller/VersionControllerTest.java
  - id: openwiki-source-cd953d153d1f926895a54be9
    resource: repo://src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java
  - id: openwiki-source-08c14869052137507158792f
    resource: repo://src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - id: openwiki-source-2a903ae22f572e1571d4137a
    resource: repo://src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java
  - id: openwiki-source-0d2b45d6d13f3d35d664203b
    resource: repo://src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java
  - id: openwiki-source-957e9e3eef69246c5223db66
    resource: repo://src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java
  - id: openwiki-source-207b1b8c0f5ecc0938cacf43
    resource: repo://src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - id: openwiki-source-d2b8a99388f4e9c226c75976
    resource: repo://src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java
  - id: openwiki-source-0a38e12a0c35c617bfb46793
    resource: repo://src/test/java/org/bartram/myfeeder/repository/V4StripRaindropApiTokenMigrationTest.java
  - id: openwiki-source-1bde4f29eee73f959e295c33
    resource: repo://src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java
  - id: openwiki-source-68271ef0e1a2be5bfdfef669
    resource: repo://src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
  - id: openwiki-source-d877f90b5abc3c8fd9d14b9b
    resource: repo://src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java
  - id: openwiki-source-dd2d645abee0bc0755ca1501
    resource: repo://src/test/resources/application-dev.yaml
  - id: openwiki-source-f390f7f52c9adafdf38e7480
    resource: repo://src/test/resources/application.yaml
generated: { by: "openwiki/0.7.2", at: "2026-10-25T13:07:33.493Z" }
---

# Testing Guide

## Backend test patterns

| Layer | Pattern | Example |
|---|---|---|
| Service unit tests | Mockito, `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` | `FeedServiceTest`, `FeedPollingServiceTest`, `ArticleServiceTest`, `RaindropServiceTest` (Mockito-mocked `RaindropApiClient`, so business-rule throws are checked without going through the resilience proxy) |
| Controller tests | `@WebMvcTest` + `MockMvc` + `@MockitoBean` for service deps | `ArticleControllerTest`, `FeedControllerTest`, `BoardControllerTest`, `InterestControllerTest`, `TopicSuggestionApiIntegrationTest` (full `@SpringBootTest`, not a slice — see below) |
| Repository tests | `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` against real Postgres | under [`src/test/java/.../repository/`](repo://src/test/java/org/bartram/myfeeder/repository), e.g. `ArticleRepositoryTest`, `ArticleScoreStoreTest`, `ArticleEngagementStoreTest`, `InterestTopicRepositoryTest` |
| Parser tests | Plain unit tests against sample feed files | `FeedParserTest`, samples in `src/test/resources/feeds/` |
| Full integration | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, verifies all beans wire | `MyfeederApplicationTests`; also used by the `*ApiIntegrationTest` classes that need a real MockMvc dispatcher plus a real Postgres (e.g. `PriorityApiIntegrationTest`, `FeedbackApiIntegrationTest`, `TopicSuggestionApiIntegrationTest`, `EngagementApiIntegrationTest`, `InterestApiIntegrationTest`, `InterestRescoreApiIntegrationTest`) |
| Migration tests | Insert a legacy-shape row via `JdbcTemplate`, re-run the migration SQL manually, assert post-migration shape (Flyway already ran at `@DataJdbcTest` startup, so a fresh DB can't "re-run" an old migration naturally) | `V4StripRaindropApiTokenMigrationTest` |
| Migration shape tests (extended pattern) | Rather than replaying an `UPDATE`, assert the post-migration table shape, constraints and cascades directly against the already-migrated Testcontainers Postgres (columns, uniqueness, `ON DELETE CASCADE`), plus static guards over the migration SQL file and/or the ranking SQL text | `V6InterestScoringMigrationTest` (seeded `interest_profile` row 1, all six V6 tables), `V7EngagementMigrationTest` (`article_engagement`/`topic_suggestion_dismissal` shape, cascades, **and** `rankingSqlReadsEngagementButNotDismissals` — a static guard proving `InterestScoreQueries`' ranking SQL never references `topic_suggestion_dismissal`) |

Run all backend tests: `./gradlew test` (requires Docker for Testcontainers). Run one class: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"`.

`src/test/resources/application.yaml` must define `myfeeder.*` properties and a dummy `spring.ai.anthropic.api-key` for the context to load.

### Gotchas specific to backend tests

- **`@WebMvcTest` omits `BuildPropertiesAutoConfiguration`** — the `BuildProperties` bean (used by `VersionController`) is absent in controller-slice tests. `VersionControllerTest` works around this with `@Import(VersionControllerTest.TestConfig.class)`, a nested `@Configuration` that builds a `BuildProperties` bean from a `Properties` literal. Either do the same, or `@Autowired(required = false)` and handle `null`. The same caveat applies to other actuator/`info.*` auto-configured beans.
- **Resilience4j-annotated methods are AOP-proxy-based, not callable via self-invocation** — `@CircuitBreaker`/`@Retry` only take effect through the Spring-managed proxy (AspectJ weaving via `spring-boot-starter-aspectj`). `JevResilienceTest` is the canonical proof: it builds a real `ApplicationContextRunner` with `AopAutoConfiguration` + `CircuitBreakerAutoConfiguration` + `RetryAutoConfiguration` wired against a JDK `HttpServer` stub, asserts `AopUtils.isAopProxy(ctx.getBean(JevApiClient.class))` is true, then drives the real production `jev` breaker/retry instances (loaded straight from main `application.yaml`) through retry-exhaustion, `retry-after-ms` honoring, and breaker open/half-open transitions. `RaindropApiClientImplTest` tests `RaindropApiClientImpl` itself (via `MockRestServiceServer`, no breaker involved) and `RaindropServiceTest` Mockito-mocks `RaindropApiClient` entirely — neither exercises the proxy, so a change to the fallback-rethrow pattern (`RaindropNotConfiguredException` rethrown as-is, everything else wrapped) needs a resilience-proxy-level test like `JevResilienceTest`'s pattern, not just these two.
- **`bootTestRun` / test-`application.yaml` shadowing**: `src/test/resources/application.yaml` shadows `src/main/resources/application.yaml` on the test classpath (Spring Boot loads only the first `classpath:/application.yaml`), and it is **offline by design**: no TypeSafe API key placeholder, Jev `base-url: http://127.0.0.1:9` (refuses instantly), and a `PT1H` first sweep delay so `@SpringBootTest` contexts never sweep mid-suite. `TestMyfeederApplication` (the `bootTestRun` entry point) activates the `dev` Spring profile, so `src/test/resources/application-dev.yaml` overlays it back to main-equivalent live values (real key placeholder, real Jev base URL, `PT1M` first sweep, 5s/30s HTTP timeouts, app name, Raindrop token placeholder). `DevProfileConfigTest` enforces two invariants with a from-disk ConfigData resolve (no Docker needed): the test yaml plus the `dev` overlay must resolve **every** main `application.yaml` key to main's value, and no test may itself activate the `dev` profile. **Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev`** in the shell that runs `./gradlew test` (e.g. via `.envrc`) — either would make the suite call the billed Jev API. Plain `./gradlew bootRun` (Docker Compose) loads main `application.yaml` directly and is unaffected.

## Interest scoring / engagement test patterns

Interest ranking, engagement capture and gap discovery have their own dense cluster of tests, mostly under [`repository/`](repo://src/test/java/org/bartram/myfeeder/repository) and [`controller/`](repo://src/test/java/org/bartram/myfeeder/controller). See [Interest Scoring](../domain/interest-scoring.md), [Interest Ranking Workflow](../workflows/interest-ranking.md) and [Jev / TypeSafe Integration](../integrations/jev-typesafe.md) for the behaviors these tests pin down.

- **`InterestScoreQueriesTest` family** — proofs of the blend/learned SQL in `InterestScoreQueries`:
  - `InterestScoreQueriesTest` — the base blend/learned/priority-sort/badge/breakdown CTEs.
  - `InterestLearnedGridTest` — the exact thumbs/engagement split across a 1,456-cell grid.
  - `InterestScoreQueriesEngagementTest` — the engagement-learning CTEs (`engaged`, `eng_learned`, `eff`/`eff2`) added in Phase 9.
  - `InterestScoreQueriesZeroEngagementTest` — proves engagement cap 0 reproduces the pre-engagement v0.2.1 blend exactly, frozen against [`src/test/resources/interest/v021-unread-blend.sql`](repo://src/test/resources/interest/v021-unread-blend.sql) so the proof doesn't drift with later schema changes.
  - `InterestScoreQueriesLatencyTest` — each query stays within 10x baseline + 250ms with ~20k engagement rows.
- **`V7EngagementMigrationTest`** — besides the V7 schema-shape assertions (see the migration-tests table row above), `rankingSqlReadsEngagementButNotDismissals` statically greps the ranking SQL to guard that it never reads `topic_suggestion_dismissal` — gap-discovery state must never leak into ranking.
- **`TopicSuggestionStoreTest` / `TopicSuggestionServiceTest` / `TopicSuggestionApiIntegrationTest`** — the gap-discovery (Phase 11) stack:
  - `TopicSuggestionStoreTest` — the `CANDIDATES` query and the one `INSERT ... SELECT ... ON CONFLICT (article_id) DO NOTHING` dismissal write.
  - `TopicSuggestionServiceTest` — windowing/ordering/cap rules above the store.
  - `TopicSuggestionApiIntegrationTest` — full `@SpringBootTest` + MockMvc over real Postgres, including `aFailedDismissalInsertRollsBackTheTopic` (proves a topic-create-from-suggestion and its dismissal write commit or roll back together) and a `neverCallsJev` assertion (the suggestions list/dismiss/create-topic-from-suggestion path never calls `JevApiClient`, verified with `Mockito.never()`). `EngagementApiIntegrationTest` carries its own `neverCallsJev` for the engagement-capture endpoints.
- **`PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh`** — proves that after a post-engagement refresh, an article's position in `/priority`, its badges and its "Why N?" breakdown all agree (SC-3); it is the proof behind the documented D-09 staleness window where an unloaded Priority row's rising score can lag until refresh.
- **`InterestCalibrationReplaySqlTest`** — a plain-JUnit, no-Spring, no-Docker drift guard: the read-only `scripts/interest-calibration-replay.sh` SQL must reproduce `InterestScoreQueries`' blend and badge text byte-for-byte, and must stay provably read-only (checked against a write-keyword regex).
- **`InterestCalibrationReplayRunTest`** — an end-to-end run of the same replay script against a Testcontainers Postgres through a host `psql` subprocess, proving the driver actually executes and parameterizes correctly (not just that the SQL text matches).
- **`FeedbackApiIntegrationTest`** — full `@SpringBootTest` coverage of `PUT`/`DELETE /api/articles/{id}/feedback`, asserting the returned `{article, scored, effects[]}` shape, the per-topic before/after effective weight, and the `engagementReplaced` flag; it (like `PriorityApiIntegrationTest`) assumes the test yaml's pre-calibration D-01 engagement constants (0.25/0.5/8), so changing those in test yaml breaks it.

## Migration-test pattern

Two variants exist, both working around the fact that Flyway has already run by the time `@DataJdbcTest` starts, so a fresh database can't naturally "re-run" an old migration:

1. **Replay-the-UPDATE pattern** (`V4StripRaindropApiTokenMigrationTest`): insert a legacy-shape row via `JdbcTemplate.update(...)`, re-execute the migration's `UPDATE` statement manually against it, then assert the post-migration shape.
2. **Shape-assertion pattern** (`V6InterestScoringMigrationTest`, `V7EngagementMigrationTest`): assert column sets, constraints and `ON DELETE CASCADE` behavior directly against the schema Flyway already produced, plus static text guards over the migration file or dependent SQL (e.g. `V7EngagementMigrationTest`'s `rankingSqlReadsEngagementButNotDismissals`). This variant suits additive migrations where there's no legacy row shape to replay, only a target shape and invariant to prove.

## Frontend test patterns

Vitest + React Testing Library. Run: `cd src/main/frontend && npm test`.

Representative tests live next to their source: `ArticleList.test.tsx`, `AppShell.test.tsx`, `FeedPanel.test.tsx`, `ReadingPane.test.tsx`, `MarkOlderReadDialog.test.tsx`, `SettingsDialog.test.tsx`, `PriorityList.test.tsx`, `PriorityBanner.test.tsx`, `ScoreRow.test.tsx`, `WhyBreakdown.test.tsx`, `InterestBadge.test.tsx`, `InterestsDialog.test.tsx`, `TopicRow.test.tsx`, `FeedbackBar.test.tsx`, `FeedbackNotice.test.tsx`, `useKeyboardShortcuts.test.ts`, `useFolders.test.ts`, `useOpml.test.ts`, `useTheme.test.ts`, `useVersion.test.ts`, `useInterest.test.tsx`, `useFeedback.test.ts`, `useEngagement.test.ts`, `usePriorityArticles.test.ts`, `engagementReaction.test.ts`, `engagementRefresh.test.ts`, `api/client.test.ts`. `InterestsDialog.test.tsx` is by far the largest frontend test file; its `describe('Suggested topics', ...)` block covers the gap-discovery UI (list, Create topic draft, Dismiss, the `(N of M)` capped heading).

### Gotcha: `vi.mock` of `preferencesStore` is full-replacement

`SettingsDialog.test.tsx`, `FeedPanel.test.tsx` and `ReadingPane.test.tsx` `vi.mock` the entire `preferencesStore` module with a literal object. Adding a new export to `preferencesStore` requires updating **every** mock that consumes it, or tests fail with `No "X" export is defined on the mock`. Prefer:

```ts
vi.mock('../stores/preferencesStore', async (importOriginal) => ({
  ...await importOriginal(),
  usePreferences: /* override */,
}))
```

for new tests touching `preferencesStore`, rather than a full manual replacement object.

### Type-checking

Use `npx tsc -b` from `src/main/frontend/` — plain `tsc --noEmit` returns success even with real type errors, because the root `tsconfig.json` has `"files": []` and relies on project references (`tsconfig.app.json`, `tsconfig.node.json`).

## What to check when changing each major area

- **Feed lifecycle changes** ([Feed Lifecycle Workflow](../workflows/feed-lifecycle.md)): `FeedPollingSchedulerTest`, `FeedPollingServiceTest`, `FeedServiceTest`, `OpmlImportServiceTest` — the backoff/self-adjusting-interval logic in particular has a history of subtle bugs, so re-read `computeEffectiveInterval`/`pollAndAdjust` (and the scheduler's re-registration after every poll) carefully before touching them.
- **Domain/API changes** ([Domain Concepts](../domain/concepts.md)): the relevant `*ControllerTest` plus `PaginatedResponseTest` if pagination/cursor logic is touched; `ArticleServiceTest` for sort-order/cursor comparison logic.
- **Interest ranking / Jev changes** ([Interest Ranking Workflow](../workflows/interest-ranking.md), [Interest Scoring](../domain/interest-scoring.md), [Jev / TypeSafe Integration](../integrations/jev-typesafe.md)): run the `InterestScoreQueriesTest` family (`InterestScoreQueriesTest`, `InterestLearnedGridTest`, `InterestScoreQueriesEngagementTest`, `InterestScoreQueriesZeroEngagementTest`, `InterestScoreQueriesLatencyTest`) if the blend/learned SQL changed; `V7EngagementMigrationTest` if the ranking SQL or the engagement/dismissal schema changed; `TopicSuggestionStoreTest`/`TopicSuggestionServiceTest`/`TopicSuggestionApiIntegrationTest` if gap discovery changed; `PriorityApiIntegrationTest` (especially `engagedArticleAgreesEverywhereAfterRefresh`) and `FeedbackApiIntegrationTest` for end-to-end agreement across `/priority`, `/api/articles/{id}` and "Why N?"; `InterestCalibrationReplaySqlTest`/`InterestCalibrationReplayRunTest` if `InterestScoreQueries`' blend SQL or the calibration replay script changed; `JevResilienceTest` if the `jev` breaker/retry wiring or `TypeSafeConfig` changed.
- **Raindrop changes** ([Raindrop Integration](../integrations/raindrop.md)): `RaindropServiceTest` (business rules) and `RaindropApiClientImplTest` (HTTP request/response shape) — both must stay green if you touch the ignore-exceptions config in `application.yaml`; use `JevResilienceTest`'s pattern as the template if you need a real-proxy fallback/breaker test for Raindrop too.
- **Frontend keyboard/state changes** ([Architecture Overview](../architecture/overview.md)): `useKeyboardShortcuts.test.ts` covers chord handling, the current-article resolution fallback, Priority-page behavior, score-breakdown (`i`) and thumbs/narrow (`u`/`d`/`Shift+D`) shortcuts.
