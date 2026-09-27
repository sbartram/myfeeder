# Phase 7: Rollout & Calibration - Pattern Map

**Mapped:** 2026-09-27
**Files analyzed:** 28 (22 code/config/test/doc files in the repo, 4 phase evidence docs, 2 throwaway ops scripts)
**Analogs found:** 25 / 28

Most of Phase 7 is ops (release, deploy, watch, replay). The code is small and every piece extends an existing file, so most "analogs" are the file itself, and the excerpt shows the exact spot to extend.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java` (mod) | config | config binding | itself, nested `Interest.Blend` (lines 52-60) | exact |
| `src/main/java/org/bartram/myfeeder/service/TierThresholds.java` (new) | model (record DTO) | request-response | `service/RescoreCount.java` | exact |
| `src/main/java/org/bartram/myfeeder/service/InterestStatus.java` (mod) | model (record DTO) | request-response | itself (line 20) | exact |
| `src/main/java/org/bartram/myfeeder/service/InterestStatusService.java` (mod) | service | request-response | itself (lines 26-34) | exact |
| `src/main/java/org/bartram/myfeeder/config/JevEventLogging.java` (new, D-15) | config | event-driven (in-process Resilience4j events → log) | `config/TypeSafeConfig.java` + registry use in `InterestStatusService.java` | role-match |
| `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java` (mod: `blendCte` visibility only) | repository | CRUD (read) | itself (line 314) | exact |
| `src/main/resources/application.yaml` (mod) | config | — | itself (lines 41-45) | exact |
| `src/test/resources/application.yaml` (mod) | config | — | itself (lines 40-44) | exact |
| `src/test/resources/application-dev.yaml` (mod, Wave 4 / 0.2.1 only) | config | — | itself (lines 19-24) | exact |
| `scripts/interest-calibration-replay.sql` (new) | utility (analysis SQL) | batch (read-only) | `InterestScoreQueries.blendCte` + `INTEREST_SCORE` + `KEYED_ORDER` text | partial (no analysis SQL exists) |
| `scripts/interest-calibration-replay.sh` (new) | utility (ops script) | batch | `deploy.sh` | role-match |
| `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java` (new) | test | file-I/O | `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java` | role-match |
| `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` (mod) | test | event-driven | itself + `config/TypeSafeConfigTest.java` (CapturedOutput) | exact |
| `src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java` (mod) | test | request-response | itself | exact |
| `src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java` (mod) | test | request-response | itself (lines 121-126) | exact |
| `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java` | test (guard) | — | unchanged; must stay green | n/a |
| `src/main/frontend/src/api/interest.ts` (mod) | model (TS type) | request-response | itself (lines 33-39) | exact |
| `src/main/frontend/src/utils/interest.ts` (mod) | utility | transform | itself (lines 26-33) | exact |
| `src/main/frontend/src/utils/tierContext.ts` (new) | provider (React context) | — | none (no `createContext` anywhere in `src/`) | no analog |
| `src/main/frontend/src/hooks/useInterest.ts` (mod: `useInterestTiers`) | hook | request-response | `useInterestStatus` (same file, lines 18-28) + `hooks/useVersion.ts` | exact |
| `src/main/frontend/src/hooks/useInterest.test.ts` (new) | test | request-response | `src/main/frontend/src/hooks/useVersion.test.ts` | exact |
| `src/main/frontend/src/App.tsx` (mod: `MainLayout`) | component | — | itself (lines 75-156) | exact |
| `src/main/frontend/src/components/InterestBadge.tsx` (mod) | component | transform | itself | exact |
| `src/main/frontend/src/components/InterestBadge.test.tsx` (mod) | test | — | itself | exact |
| `src/main/frontend/src/utils/interest.test.ts` (mod) | test | — | itself | exact |
| `CLAUDE.md` (mod, OPS-03) | doc | — | itself (lines 40, 51-59, 99, 113, 128, 147) | exact |
| `.planning/phases/07-rollout-calibration/07-CALIBRATION.md`, `07-BACKFILL.md` (new) | doc (evidence) | — | `.planning/phases/03-interest-model-schema-rubric-editor/03-CALIBRATION.md`; release evidence style from `.planning/phases/01-dependency-upgrade/01-04-SUMMARY.md` | role-match |
| Watch script (throwaway, `$HOME/.cache/myfeeder-phase07/`, not committed) | utility | streaming/polling | none; use RESEARCH Pattern 5 | no analog |

All analog paths above were checked with `git ls-files` (all tracked; no mirror paths).

---

## Pattern Assignments

### `config/MyfeederProperties.java` (config, binding)

**Analog:** itself, `Interest.Blend` (lines 52-60). Add a nested `Tiers` class inside `Blend`, same Lombok `@Data` + field-initializer defaults + one-line Javadoc per field:

```java
        @Data
        public static class Blend {
            /** Points for a full profile match (R1): the profile contributes profile_score / profile_max_level x profilePoints. */
            private int profilePoints = 100;
            /** Points one vote moves a topic at a full match (R2): learned = learnRate x SUM(vote x hinge). Phase 7 tunes it. */
            private double learnRate = 2;
            /** Bound on a topic's learned adjustment in points, either direction (FDBK-03). */
            private int learnedCap = 20;
        }
```

Nesting precedent: `Interest` holds `private Blend blend = new Blend();` (line 45). Mirror it with `private Tiers tiers = new Tiers();` inside `Blend`, giving the property path `myfeeder.interest.blend.tiers.high|neutral` (D-13). Defaults 70 and 40.

---

### `service/TierThresholds.java` (new record)

**Analog:** `src/main/java/org/bartram/myfeeder/service/RescoreCount.java` (whole file, 9 lines): a top-level record in `service/` with a Javadoc that says what each component means and which route serves it.

```java
package org.bartram.myfeeder.service;

/**
 * A Re-score count (INT-05). For {@code GET /api/interest/rescore} it is the number of score rows
 * Re-score would reset; for {@code POST /api/interest/rescore} it is the number of rows it reset.
 * {@code windowDays} is the eligibility window the confirmation copy names ("from the last N days").
 */
public record RescoreCount(long count, int windowDays) {
}
```

Shape: `public record TierThresholds(int high, int neutral) {}`. JSON becomes `"tiers":{"high":70,"neutral":40}` (Jackson serializes record components by name, the same as `RescoreCount`).

---

### `service/InterestStatus.java` (record, append-only)

**Analog:** itself (line 20). The Javadoc (lines 3-19) lists every component in a `<ul>`. Add a `<li>` for `tiers`, then append the component **last**. Never reorder or rename.

```java
public record InterestStatus(boolean configured, String breakerState, boolean coldStart, long eligibleUnscored, long failed) {}
```
→ `..., long failed, TierThresholds tiers) {}`

There is only one production construction site (`InterestStatusService.java:28`), and no test calls `new InterestStatus(` (grep confirmed), so appending a component breaks nothing else.

---

### `service/InterestStatusService.java` (service)

**Analog:** itself (lines 26-34). `properties` is already injected (line 24). Add the last constructor argument from it:

```java
    public InterestStatus status() {
        ScoreCounts c = store.counts(properties.getInterest().eligibilityCutoff());
        return new InterestStatus(
                jevApiClient.isConfigured(),
                circuitBreakerRegistry.circuitBreaker("jev").getState().name(),
                interestService.isColdStart(),
                c.eligibleUnscored(),
                c.failed());
    }
```

---

### `config/JevEventLogging.java` (new, config, event-driven) — D-15

**Analog 1 (class shape and log-safety rules):** `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java` lines 1-47.

```java
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TypeSafeProperties.class) // (not needed here)
public class TypeSafeConfig {
    ...
        if (!StringUtils.hasText(properties.getApiKey())) {
            // Fixed text only: never the key, its length or its prefix.
            log.info("TypeSafe Jev not configured; interest scoring disabled");
        }
```

**Analog 2 (getting the `jev` instance by name from the registry):** `InterestStatusService.java` lines 3, 21, 30: `import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;` ... `circuitBreakerRegistry.circuitBreaker("jev")`. `RetryRegistry` is resolved the same way in `JevResilienceTest.java:395`: `ctx.getBean(RetryRegistry.class).retry("jev")`.

**Log-line style to match:** existing Jev lines log the class simple name and numbers only, never `getMessage()` or bodies:
- `JevApiClientImpl.java:68`: `log.warn("TypeSafe rejected the API key (status {}, requestId {})", e.status(), e.requestId());`
- `ArticleScoringService.java:80`: `log.debug("Transient scoring failure for article {}: {}", articleId, e.getClass().getSimpleName());`
- `InterestScoringSweep.java:67`: `log.warn("Scoring sweep failed: {}", e.getClass().getSimpleName());`

**Planner notes (verified this session):**
1. **Make the class `public`.** RESEARCH Pattern 3 sketches `class JevEventLogging` (package-private). `JevResilienceTest` lives in package `integration` and registers user configs with `UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class)` (JevResilienceTest.java:121). Adding `JevEventLogging.class` there only compiles if the class is public.
2. **The `StateTransition` placeholder text.** `CircuitBreaker.StateTransition.toString()` returns `"State transition from %s to %s"` (javap of resilience4j-circuitbreaker-2.3.0.jar). Logging `{}` with `e.getStateTransition()` prints `Jev circuit breaker State transition from CLOSED to OPEN ...`, **not** `CLOSED_TO_OPEN`. RESEARCH's proposed test assertion ("Jev circuit breaker CLOSED_TO_OPEN") would fail. Log `e.getStateTransition().name()` (the enum constant, e.g. `CLOSED_TO_OPEN`), or `getFromState()`/`getToState()`, and grep for the same text in the watch script.

---

### `repository/InterestScoreQueries.java` (visibility change only)

**Analog:** itself, line 314. Change `private static String blendCte(String scope)` to package-private `static String blendCte(String scope)`. That matches the sibling constants, which are already package-private for tests (`static final String UNREAD_SCOPE` line 45, `INTEREST_SCORE` lines 60-61, `KEYED_ORDER` line 74). Change no SQL text. Update the class Javadoc's `{@link #blendCte(String)}` references only if the javadoc build complains (it won't for package-private).

---

### `src/main/resources/application.yaml` / `src/test/resources/application.yaml` (config)

**Analog:** main lines 41-45 and test lines 40-44 (identical blocks):

```yaml
    # Blend constants (R1); the profile contributes profile_score / max level x profile-points. Phase 7 tunes these.
    blend:
      profile-points: 100
      learn-rate: 2
      learned-cap: 20
```

Wave 1: add, in **both** files, the same values:
```yaml
      tiers:
        high: 70
        neutral: 40
```
`DevProfileConfigTest.devOverlayResolvesEveryMainKeyToMainsValue` (lines 78-97) compares every main key against `dev.containsProperty(name) ? dev : test`. A key added to main only fails the build.

### `src/test/resources/application-dev.yaml` (Wave 4 / 0.2.1 only)

**Analog:** itself, lines 19-24. Existing overlay entries carry a decision-citing comment:

```yaml
myfeeder:
  raindrop:
    api-token: ${MYFEEDER_RAINDROP_API_TOKEN:}
  interest:
    # D-10: first sweep about a minute after startup (the test yaml's PT1H is for the suite).
    sweep-initial-delay: PT1M
```

Put the tuned `blend.profile-points` / `blend.tiers.*` here under `interest:` with a `# D-14: tuned values from 07-CALIBRATION.md` comment, and the same values in main yaml. Leave the test yaml at 100/70/40 (InterestScoreQueriesTest fixtures hard-code raws at profile-points 100, lines 33-37). The second loop of the guard (lines 90-96) accepts dev keys that also exist in main.

---

### `scripts/interest-calibration-replay.sql` (new, analysis SQL)

**No committed analysis-SQL analog** (`git ls-files '*.sql'` = Flyway migrations only). The body must be the **verbatim** output of `InterestScoreQueries.blendCte(UNREAD_SCOPE)` (lines 102-118 `LEARNED_CTE` + lines 314-327). The `:profilePoints`, `:learnRate` and `:learnedCap` tokens are already psql `-v` compatible. The source to reproduce:

```java
    private static String blendCte(String scope) {
        return LEARNED_CTE + ", "
                + "contrib AS (SELECT ts.article_id, ts.topic_id, ts.noul, "
                + "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, e.base, e.w - e.base AS learned_applied, "
                + "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "
                + "FROM article_topic_score ts JOIN eff2 e ON e.id = ts.topic_id), "
                + "blended AS (SELECT s.article_id, "
                + "ROUND((:profilePoints * COALESCE(s.profile_score / NULLIF(s.profile_max_level, 0), 0) "
                + "+ COALESCE(SUM(c.points), 0))::numeric, 6) AS raw_n "
                + "FROM article_score s JOIN article a ON a.id = s.article_id AND (" + scope + ") "
                + "LEFT JOIN contrib c ON c.article_id = s.article_id "
                + "WHERE s.status = 'SCORED' "
                + "GROUP BY s.article_id, s.profile_score, s.profile_max_level)";
    }
```
with `scope = UNREAD_SCOPE = "a.\"read\" = false"` (line 45). The badge expression is `INTEREST_SCORE` (lines 60-61): `CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END`. Order the top/bottom 20 like `KEYED_ORDER` (line 74): score, then `COALESCE(a.published_at, a.fetched_at)`, then id (Pitfall 10). Use RESEARCH Pattern 4 for the three-statement layout (summary, top 20, bottom 20). The CTE text is one long line, since the Java concatenates without newlines. Keep it on one line in the file so the drift guard's `contains` matches exactly.

Pitfall 9 cross-check (all SCORED in window, read or unread) needs a second scope. Run it as a separate statement with its own copy of the blend text and a different scope. Keep the drift-guarded UNREAD copy byte-identical.

---

### `scripts/interest-calibration-replay.sh` (new, ops script)

**Analog:** `deploy.sh` (whole file, 32 lines). Conventions to copy:

```bash
#!/usr/bin/env bash
set -euo pipefail
...
# TypeSafe Jev is optional; default to empty so set -u doesn't trip.
TYPESAFE_KEY="${MYFEEDER_TYPESAFE_API_KEY:-}"
if [[ -z "$TYPESAFE_KEY" ]]; then
  echo "Warning: ..."
fi
```
- `set -euo pipefail`, `${VAR:-}` defaults, and `echo` progress lines. Never `set -x` (it would echo `PGPASSWORD`).
- `MYFEEDER_PG_PASSWORD` is the same env var deploy.sh already requires (line 28).
- Add `export PGOPTIONS='-c default_transaction_read_only=on'` and the candidate loop from RESEARCH Pattern 4. Host/db/user `pg.bartram.org` / `myfeeder` / `myfeeder`.
- The file mode must be executable (`git update-index --chmod=+x` if needed); `deploy.sh` is the precedent.

---

### `repository/InterestCalibrationReplaySqlTest.java` (new drift guard, no Docker)

**Analog:** `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java`. It is a plain JUnit class with no Spring context and no Docker, and it reads repo files by project-root-relative path. Copy:

Imports and class shape (lines 1-3, 19-26, 35):
```java
import org.junit.jupiter.api.Test;
...
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
...
import static org.assertj.core.api.Assertions.assertThat;

class DevProfileConfigTest {
```
File read (lines 102, 109, 117):
```java
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
        ...
            String source = Files.readString(file);
        ...
        assertThat(Files.readString(Path.of("src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java")))
                .contains("withAdditionalProfiles");
```
Place it in package `org.bartram.myfeeder.repository` so it can call the package-private `InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE)` and read `INTEREST_SCORE`. Assert `Files.readString(Path.of("scripts/interest-calibration-replay.sql"))` `.contains(...)` for each. Gradle runs tests with the project dir as the working directory, which is what DevProfileConfigTest relies on.

---

### `integration/JevResilienceTest.java` (extend, D-15)

**Analog:** itself.

- Class already has `@ExtendWith(OutputCaptureExtension.class)` (line 87) and imports `CapturedOutput` (line 49).
- Runner registration to extend (lines 118-121):
```java
                .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class))
```
→ add `JevEventLogging.class` (requires the public class; see above). Adding it to the shared runner means every test also exercises the logger, which is fine because it only logs.
- 429 driver to copy (lines 160-174, `rateLimitWaitsForRetryAfterThenSucceeds`): `stub.enqueue(429, Map.of("retry-after-ms", "700")); stub.enqueue(200);`. A new test can take `CapturedOutput output`, and assert after `runner.run(...)` that the output contains the retry line with `TypeSafeRateLimitException` and `doesNotContain("LEAKCHECK")`. Pattern from lines 196-206:
```java
    @Test
    void rejectedKeyIsNotRetriedButRecorded(CapturedOutput output) {
        stub.enqueue(401);
        runner.run(ctx -> { ... });
        assertThat(output.toString()).contains("req-1").doesNotContain("LEAKCHECK");
```
- Breaker transition driver (line 257 + helper at line 449-451): `jevBreaker(ctx).transitionToOpenState();`, then assert the transition log text. Use the `.name()` form (`CLOSED_TO_OPEN`) per the JevEventLogging note.

Secondary analog for counting log lines: `config/TypeSafeConfigTest.java:117-125` (`occurrences(output.toString(), KEYLESS_LINE)`).

---

### `service/InterestStatusServiceTest.java` (extend)

**Analog:** itself. It is built with `new MyfeederProperties()` (lines 40-41), so the defaults flow through. Add one test in the style of lines 84-92:

```java
    @Test
    void reportsEligibleUnscoredAndFailedCounts() {
        when(store.counts(any())).thenReturn(new ScoreCounts(312, 4));

        InterestStatus status = statusService.status();

        assertThat(status.eligibleUnscored()).isEqualTo(312);
        assertThat(status.failed()).isEqualTo(4);
    }
```
→ assert `status.tiers()` equals `new TierThresholds(70, 40)`, plus a case that sets custom values on a `MyfeederProperties` instance (`props.getInterest().getBlend().getTiers().setHigh(55)`) and builds a second service.

### `controller/InterestApiIntegrationTest.java` (extend)

**Analog:** itself, lines 121-126:
```java
        mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.breakerState").value("CLOSED"))
                .andExpect(jsonPath("$.coldStart").value(true));
```
→ add `.andExpect(jsonPath("$.tiers.high").value(70)).andExpect(jsonPath("$.tiers.neutral").value(40))`. This test reads the test yaml, so it proves the binding path.

---

### Frontend `api/interest.ts` (TS type)

**Analog:** itself, lines 33-39:
```ts
export interface InterestStatus {
  configured: boolean
  breakerState: string
  coldStart: boolean
  eligibleUnscored: number
  failed: number
}
```
→ append `tiers?: TierThresholds` (optional). Existing fixtures typed `InterestStatus` without `tiers` keep compiling under `npx tsc -b`. Example: `PriorityBanner.test.tsx:13-22` `status(overrides)` builds a full object with the five fields. Import the `TierThresholds` type from `utils/interest` (or declare it here and re-export; pick one home).

### Frontend `utils/interest.ts`

**Analog:** itself, lines 26-33:
```ts
export type Tier = 'high' | 'neutral' | 'low'

/** Badge tier of a server display score, inclusive at the low end (carried-forward tiers): 70+ high, 40+ neutral, else low. */
export function tierOf(score: number): Tier {
  if (score >= 70) return 'high'
  if (score >= 40) return 'neutral'
  return 'low'
}
```
→ add `TierThresholds` + `DEFAULT_TIERS` and a defaulted second parameter (RESEARCH Pattern 2). The file's style is: exported consts in UPPER_SNAKE (`OPEN_BREAKER_STATES` line 19, `WEIGHT_MIN` line 63) and a one-line `/** */` doc per export. Update the doc comment so it no longer hardcodes 70/40 as the only rule.

### Frontend `utils/tierContext.ts` (new) — no analog

No `createContext` exists in `src/main/frontend/src` (grep: only `DndContext`/`SortableContext` from dnd-kit). Use RESEARCH Pattern 2: `export const TierContext = createContext<TierThresholds>(DEFAULT_TIERS)`. Keep it a `.ts` file (no JSX needed). The default value is what keeps the provider-less tests green: `ArticleList.test.tsx` and `BoardArticleList.test.tsx` render without a `QueryClientProvider`.

### Frontend `hooks/useInterest.ts` (`useInterestTiers`)

**Analog 1:** `useInterestStatus`, same file, lines 11-28 (same query key, same `queryFn`; do not change it):
```ts
export function useInterestStatus() {
  return useQuery({
    queryKey: ['interest', 'status'],
    queryFn: interestApi.getStatus,
    staleTime: 0,
    refetchInterval: (query) => { ... },
  })
}
```
**Analog 2:** `hooks/useVersion.ts` (lines 4-11) for the never-refetch observer:
```ts
export function useVersion() {
  return useQuery({
    queryKey: ['version'],
    queryFn: versionApi.get,
    staleTime: Infinity,
    gcTime: Infinity,
  })
}
```
The new hook uses key `['interest', 'status']`, `staleTime: Infinity`, `select: (s) => s.tiers`, no `refetchInterval`, and returns `data ?? DEFAULT_TIERS`. It needs a JSDoc paragraph in the style of lines 11-17, explaining why it does not poll. Leave `gcTime` at the default: the status entry is shared with `useInterestStatus`, and `Infinity` there would change that query's cache lifetime.

### Frontend `hooks/useInterest.test.ts` (new)

**Analog:** `src/main/frontend/src/hooks/useVersion.test.ts` (whole file, 37 lines). Copy the `vi.mock` of the api module before imports, `createWrapper()` with `retry: false`, `renderHook(..., { wrapper })`, `waitFor`:
```ts
vi.mock('../api/version', () => ({
  versionApi: {
    get: vi.fn(),
  },
}))
...
function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}
```
Mock `../api/interest` with the `importOriginal` spread, per the CLAUDE.md Test Patterns rule (`vi.mock` is full replacement), e.g. `vi.mock('../api/interest', async (importOriginal) => ({ ...(await importOriginal<typeof import('../api/interest')>()), interestApi: { getStatus: vi.fn() } }))`. The spread precedent is `PriorityBanner.test.tsx:8-11`. Cases: 70/40 before resolve, served tiers after, 70/40 when the response lacks `tiers`.

### Frontend `App.tsx` (`MainLayout`)

**Analog:** itself, lines 75-156. `MainLayout` already calls hooks at the top (`useQueryClient`, `usePriorityArticles`, lines 90-92) and returns a fragment `<>...</>` (line 112). Call `const tiers = useInterestTiers()` next to those hooks and replace the outer fragment with `<TierContext.Provider value={tiers}>`. `MainLayout` is inside `QueryClientProvider` (`App`, lines 158-167), so the query is legal there.

### Frontend `components/InterestBadge.tsx`

**Analog:** itself (whole file, 18 lines):
```tsx
import { tierOf } from '../utils/interest'
...
      className={`interest-badge tier-${tierOf(score)}`}
```
→ `const tiers = useContext(TierContext)` and `tierOf(score, tiers)`. Put the hook call **before** the `if (score == null) return null` early return (line 8) to keep hook order stable. Keep the title/aria-label text unchanged; `labelsTheScore` asserts it.

### Frontend tests `InterestBadge.test.tsx` / `utils/interest.test.ts`

**Analog:** `InterestBadge.test.tsx` lines 11-26 (table-driven `cases` array with `score N` messages). Keep the existing 70/40 table unchanged (no provider means the default). Add a table under `<TierContext.Provider value={{ high: 50, neutral: 20 }}>`. `utils/interest.test.ts` has no `tierOf` block yet (imports at line 2 omit it). Add a `describe('tierOf', ...)` in the file's `describe`/`it('camelCaseName')` style for the default and custom thresholds, inclusive at each boundary.

---

### `CLAUDE.md` (OPS-03)

**Analog:** itself. Stale lines to edit, verified this session:

| Line | Current text (prefix) | Edit |
|---|---|---|
| 40 | `- **AI**: Spring AI Anthropic starter on the classpath (not yet used by any application code)` | keep; add a sibling **Jev** bullet (TypeSafe starter, app-owned `TypeSafeClient`) |
| 51 | `├── config/           MyfeederProperties, RestClientConfig (User-Agent customizer), SpaForwardController` | + `TypeSafeConfig`, `InterestScoringConfig`, `JevEventLogging` |
| 53, 55, 56, 59 | repository / service / integration / scheduler rows | + interest classes per RESEARCH §OPS-03 table |
| 99 | Flyway list ends at `V5__article_extracted_content.sql` | + `V6__interest_scoring.sql` |
| 113, 128 | `needs MYFEEDER_PG_PASSWORD + MYFEEDER_ANTHROPIC_API_KEY (MYFEEDER_RAINDROP_API_TOKEN optional)` | + `MYFEEDER_TYPESAFE_API_KEY` optional; minor-release incrementer flag |
| 147 | `serve {configured, breakerState, coldStart, eligibleUnscored, failed}` | + `tiers {high, neutral}` from `myfeeder.interest.blend.tiers.*` |

Style: bullets open with a bold lead-in (`- **Name**: ...`) and name concrete files and keys; new behavior sections sit under `## Interest Ranking` (line 142) or `## Gotchas` (line 164). The source facts for each new bullet are in RESEARCH §OPS-03. Include a verbatim `max-retries: 0` and `window-days`, because the validation grep in RESEARCH §Phase Requirements → Test Map checks for them.

---

### `07-CALIBRATION.md` / `07-BACKFILL.md` (phase evidence docs)

**Analog:** `.planning/phases/03-interest-model-schema-rubric-editor/03-CALIBRATION.md`. Copy:
- the opening paragraph's content rule (lines 3-7): "This file holds article titles, topic keys and numbers only. It never includes the profile text, the topic descriptions or the key. The raw input ... live outside the repository under `$HOME/.cache/myfeeder-phase03/`." Use `myfeeder-phase07` for this phase.
- `## Run` metadata table (date, model, counts), then `## Numbers` tables with a threshold column, then `## Verdict` table with PASS/FAIL per threshold (lines 9-60).

For `07-BACKFILL.md`, add the release facts in the style of the `01-04-SUMMARY.md` `provides:` list (lines 9-13): tag, image digest, Helm revision, soak verdict.

---

## Shared Patterns

### Fixed-text logging (never echo key, message, or body)
**Source:** `config/TypeSafeConfig.java:45-48`; `integration/JevApiClientImpl.java:68`; `service/ArticleScoringService.java:80`
**Apply to:** `JevEventLogging`, the watch/replay scripts (no `set -x`), evidence docs
```java
            // Fixed text only: never the key, its length or its prefix.
            log.info("TypeSafe Jev not configured; interest scoring disabled");
```
Log `getClass().getSimpleName()` and numbers. Tests assert `doesNotContain("LEAKCHECK")` (JevResilienceTest.java:206).

### Config-parity guard
**Source:** `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java:78-97`
**Apply to:** every yaml edit (Wave 1 tier keys, Wave 4 tuned values). Main key → must resolve through dev overlay or test yaml to the same raw string.

### Append-only status contract
**Source:** `service/InterestStatus.java:17-20` Javadoc ("Later fields are appended; existing components are never renamed.")
**Apply to:** `InterestStatus` record, TS `InterestStatus` interface (new field optional), CLAUDE.md line 147.

### Resilience4j instance lookup by name
**Source:** `service/InterestStatusService.java:30` `circuitBreakerRegistry.circuitBreaker("jev")`; `integration/JevResilienceTest.java:395,450`
**Apply to:** `JevEventLogging` (breaker + retry), its test.

### `@Configuration(proxyBeanMethods = false)` + `@Slf4j`
**Source:** `config/TypeSafeConfig.java:35-38`, `config/InterestScoringConfig.java:25-26`
**Apply to:** `JevEventLogging`.

### TanStack hook test harness
**Source:** `src/main/frontend/src/hooks/useVersion.test.ts:15-19`; mock spread `components/PriorityBanner.test.tsx:8-11`
**Apply to:** `useInterest.test.ts`, any test that must render `MainLayout`-level providers.

### Bash script conventions
**Source:** `deploy.sh:1-2, 12-22`
**Apply to:** `scripts/interest-calibration-replay.sh`, the throwaway watch script.

## No Analog Found

| File | Role | Data Flow | Reason / What to use |
|---|---|---|---|
| `src/main/frontend/src/utils/tierContext.ts` | provider | — | No React context exists in the app; use RESEARCH Pattern 2 (`createContext(DEFAULT_TIERS)`) |
| `scripts/interest-calibration-replay.sql` | utility | batch (read-only) | No committed analysis SQL; body is the verbatim `blendCte(UNREAD_SCOPE)` text (excerpt above) + RESEARCH Pattern 4 statement skeleton |
| Watch script (throwaway, outside git) | utility | polling | Nothing similar in repo; RESEARCH Pattern 5 (LB IP `192.168.44.204`, `kubectl logs -f --since-time`, 60s curl loop, jq summary) |

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{config,service,repository,integration,scheduler}`, `src/test/java/org/bartram/myfeeder/**`, `src/main/frontend/src/{api,hooks,utils,components}`, `src/main/resources`, `src/test/resources`, repo-root scripts, `.planning/phases/01-*`, `.planning/phases/03-*`
**Files scanned:** ~40
**Verified facts beyond RESEARCH:** `StateTransition.toString()` format (javap, resilience4j-circuitbreaker 2.3.0); `JevEventLogging` must be public for `JevResilienceTest` registration; no test constructs `InterestStatus` directly; `tierOf` has no direct unit test today; no `createContext` in the frontend.
**Pattern extraction date:** 2026-09-27
