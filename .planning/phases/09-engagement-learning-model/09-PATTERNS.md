# Phase 9: Engagement Learning Model - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 30 (new + modified)
**Analogs found:** 30 / 30. Most changes edit the analog file itself: this phase extends the v0.2.1 learned model, so the closest analog is usually the thumbs code path that the new file sits next to.

All analog paths below are git-tracked source (none are gitignored mirrors).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java` (M) | repository/query | CRUD read, transform (SQL CTE) | itself: `LEARNED_CTE` L102-119, `blendCte` L314-328, `TOPIC_WEIGHTS_SELECT` L261-264, breakdown select L212-227, `learnedSql` L295-300 | exact |
| `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java` (M) | config | binding + startup validation | itself: `Interest.Blend` L57-71 (nested `@Data` class, like `Tiers`) | exact (for the nested class); no in-repo self-`Validator` exists, so use RESEARCH Pattern 2 |
| `src/main/java/org/bartram/myfeeder/service/LearnedLimit.java` (M) | utility enum | transform | itself L19-34 | exact |
| `src/main/java/org/bartram/myfeeder/service/TopicLearned.java` (M) | DTO record | response | itself L9-10 | exact |
| `src/main/java/org/bartram/myfeeder/service/FeedbackResult.java` (M, `TopicEffect`) | DTO record | response | itself L22-23 | exact |
| `src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java` (M, `Row`) | model/DTO | response | itself L27-43 | exact |
| `src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java` (M) | service | transform | itself L77 (`Row.topic(...)` call) | exact |
| `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java` (M) | service | request-response | itself L71-73, L100-106 | exact |
| `src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java` (M, javadoc only) | repository | - | itself | exact |
| `src/main/resources/application.yaml` (M) | config | - | `blend:` block L43-50 | exact |
| `src/test/resources/application.yaml` (M) | config | - | `blend:` block L42-49 | exact |
| `scripts/interest-calibration-replay.sql` (M, regenerated) | script | batch read-only | itself (L30 = verbatim blend line) | exact |
| `scripts/interest-calibration-replay.sh` (M) | script | batch | itself L21-38, L64-66 | exact |
| `src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java` (NEW) | test (repo, Testcontainers) | CRUD read | `InterestScoreQueriesTest.java` | exact |
| engagement methods in `InterestScoreQueriesTest.java` or sibling `InterestScoreQueriesEngagementTest.java` (NEW/M) | test | CRUD read | `InterestScoreQueriesTest.java` L316-470 | exact |
| `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java` (NEW) | test | CRUD read | `InterestScoreQueriesTest.java` + `InterestCalibrationReplaySqlTest` (reads SQL file text) | exact |
| `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java` (NEW) | test | batch seed + timing | `InterestScoreQueriesTest.java` (`insertMatchingArticles` L585-594) | role-match (no timing test exists) |
| `src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java` (NEW) | test (no Docker) | config binding | `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` | role-match |
| `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java` (M) | test | script driver | itself: `driverRejectsANonNumericCandidate` L89-94, `runDriver` L365-381 | exact |
| `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java` (M, invert guard) | test | file-text guard | itself L209-217 | exact |
| `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java` (M, invert) | test (SpringBootTest) | request-response | itself L320-342 | exact |
| `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java` (M) | test (Mockito) | - | itself L157-195 | exact |
| `src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java` (M) | test | - | itself L30 | exact |
| `src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java` (M) | test (WebMvc) | - | itself L155-156 | exact |
| `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java` (M) | test (WebMvc) | - | itself L435 | exact |
| `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java` (verify only, likely unchanged per D-04) | test | - | itself | exact |
| `src/main/frontend/src/types/index.ts` (M) | types | - | L55-56 (`LearnedLimit`), L65-73 (`TopicEffect`), L101-104 (optional breakdown fields) | exact |
| `src/main/frontend/src/api/interest.ts` (M) | api types | - | L26-31 `TopicLearned` | exact |
| `src/main/frontend/src/utils/feedback.test.ts` (M) | test | - | L141-144 (`effect(...)` with limit) | exact |
| `CLAUDE.md` (M) | docs | - | § Interest Ranking, § Engagement capture ("The ranking SQL does not read engagement until Phase 9"), replay usage | exact |

## Pattern Assignments

### `InterestScoreQueries.java` (repository, SQL transform)

**LEARNED_CTE today** (L102-119) — `WITH learned AS` must remain the first token (drift guard `BLEND_START` in `InterestCalibrationReplaySqlTest` L54). Add an engagement CTE after `learned` (collapse `article_engagement` per article with `GROUP BY article_id` + `MAX(...)` first — RESEARCH Pitfall 1), extend `eff` with `eng_raw`/`eng` (`LEAST(cap, GREATEST(0, ...))`), and compute `w_thumbs` (byte-identical to today's `w`) and `w` (same + `e.eng`) in `eff2`:
```java
static final String LEARNED_CTE = "WITH learned AS (SELECT ts.topic_id, "
        + "SUM(f.vote * GREATEST(0, (ts.noul - 0.5) * 2)) AS vote_sum "
        + "FROM article_feedback f "
        + "JOIN article_score fs ON fs.article_id = f.article_id AND fs.status = 'SCORED' "
        + "JOIN article_topic_score ts ON ts.article_id = f.article_id "
        + "WHERE NOT f.topics_narrowed OR EXISTS (...) GROUP BY ts.topic_id), "
        + "eff AS (SELECT t.id, t.name, t.weight AS base, "
        + "CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0) AS learned_raw, "
        + "LEAST(CAST(:learnedCap AS float8), GREATEST(-CAST(:learnedCap AS float8), "
        + "CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0))) AS learned "
        + "FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id), "
        + "eff2 AS (SELECT e.*, CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned)) "
        + "WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned)) "
        + "ELSE GREATEST(-50, LEAST(50, e.learned)) END AS w "
        + "FROM eff e)";
```
Convention: every constant is a bound named param and `CAST(:x AS float8)` (untyped unary minus is ambiguous). Avoid alias words matching `(?i)\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\b`.

**Parameter binding** (L294-305) — add the three engagement params here so every learned-model statement (topicWeights, blend, breakdown) gets them:
```java
private JdbcClient.StatementSpec learnedSql(String sql) {
    MyfeederProperties.Interest.Blend blend = properties.getInterest().getBlend();
    return jdbc.sql(sql)
            .param("learnRate", blend.getLearnRate())
            .param("learnedCap", blend.getLearnedCap());
}
private JdbcClient.StatementSpec blendSql(String sql) {
    return learnedSql(sql).param("profilePoints", properties.getInterest().getBlend().getProfilePoints());
}
```
Param names must equal the new psql `-v` names in the replay driver.

**contrib** (L316-319) — add `e.w_thumbs`:
```java
+ "contrib AS (SELECT ts.article_id, ts.topic_id, ts.noul, "
+ "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, e.base, e.w - e.base AS learned_applied, "
+ "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "
```

**Breakdown topic select + mapper** (L212-227) — replace with RESEARCH Pattern 1 select (`learned_w = ROUND(w,6) - ROUND(base,6)`, `thumbs_w`, `eng_w` by subtraction of rounded values); append two `rs.getDouble(...)` args to `new TopicContribution(...)`.

**Records** (L90-91, L124-125) — append fields only (DTO rule):
```java
public record TopicContribution(long topicId, String name, double noul, double hinge, double weight,
                                BigDecimal exact, double baseWeight, double learnedWeight) {}
public record TopicWeight(long topicId, String name, double base, double learnedRaw, double learned,
                          double effective) {}
```
Per D-15, `TopicWeight.learned` becomes combined (thumbs capped + engagement capped); append thumbs-learned, engagement-raw, engagement-learned.

**TOPIC_WEIGHTS_SELECT + mapper** (L261-269):
```java
private static final String TOPIC_WEIGHTS_SELECT = LEARNED_CTE
        + " SELECT e.id, e.name, ROUND(e.base::numeric, 6) AS base, "
        + "ROUND(e.learned_raw::numeric, 6) AS learned_raw, ROUND(e.learned::numeric, 6) AS learned, "
        + "ROUND(e.w::numeric, 6) AS w FROM eff2 e";
private static TopicWeight topicWeight(ResultSet rs) throws SQLException {
    return new TopicWeight(rs.getLong("id"), rs.getString("name"), rs.getDouble("base"),
            rs.getDouble("learned_raw"), rs.getDouble("learned"), rs.getDouble("w"));
}
```
Javadoc style: update the `LEARNED_CTE` (L93-101) and `blendCte` (L307-313) javadocs in the same terse "{@code ...}" style.

---

### `MyfeederProperties.java` (config)

**Analog:** `Blend` nested class L57-71 — add `private Engagement engagement = new Engagement();` and a nested `@Data public static class Engagement { double openWeight = 0.25; double saveWeight = 0.5; double cap = 8; }` mirroring `Tiers`:
```java
@Data
public static class Blend {
    /** Points one vote moves a topic at a full match (R2): learned = learnRate x SUM(vote x hinge). Phase 7 tunes it. */
    private double learnRate = 2;
    /** Bound on a topic's learned adjustment in points, either direction (FDBK-03). */
    private int learnedCap = 20;
    private Tiers tiers = new Tiers();

    @Data
    public static class Tiers { private int high = 70; private int neutral = 40; }
}
```
Validation: no existing in-repo analog. Follow RESEARCH Pattern 2 — the top-level class `implements org.springframework.validation.Validator`, `supports(c) -> MyfeederProperties.class.isAssignableFrom(c)`, `validate` → `errors.reject(code, FIXED_TEXT)` per D-02 (cap == 0 always valid; else `0 <= open < save < 1` and `0 < cap < learnedCap`). Beware Lombok `@Data` + `Validator` method-name collisions (none expected: `supports`/`validate`).

---

### `LearnedLimit.java` (enum)

**Analog:** itself L19-34. Append `ENGAGEMENT_CAP` at the END of the enum constants (append rule) and change signature to `of(TopicWeight w, double learnedCap, double engagementCap)`; precedence per D-11:
```java
public enum LearnedLimit {
    NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE;

    public static LearnedLimit of(TopicWeight w, double cap) {
        if (Math.abs(w.learnedRaw()) >= cap) { return LEARNED_CAP; }
        double sum = w.base() + w.learned();
        if ((w.base() > 0 && sum < 0) || (w.base() < 0 && sum > 0)) { return SIGN_CLAMP; }
        if (Math.abs(sum) > InterestService.MAX_WEIGHT) { return WEIGHT_RANGE; }
        return NONE;
    }
}
```
Insert `if (engagementCap > 0 && w.engagementRaw() >= engagementCap) return ENGAGEMENT_CAP;` after the LEARNED_CAP check; `learned` is already combined (D-15) so the sum check stays `base + learned`. Update the ordered-list javadoc.

### `TopicLearned.java`, `FeedbackResult.TopicEffect` (DTO records)

Append fields after existing ones (existing names keep meaning "combined"):
```java
public record TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight,
                           LearnedLimit limit) {}
public record TopicEffect(long topicId, String name, double before, double after, double baseWeight,
                          double learned, LearnedLimit limit) {}
```

### `ArticleFeedbackService.java` (service)

Call sites to update (L71-73, L100-106):
```java
int cap = properties.getInterest().getBlend().getLearnedCap();
.map(w -> new TopicLearned(w.topicId(), w.base(), w.learned(), w.effective(), LearnedLimit.of(w, cap)))
...
return new TopicEffect(id, a.name(), before.get(id).effective(), a.effective(), a.base(),
        a.learned(), LearnedLimit.of(a, cap));
```
Read engagement cap from `properties.getInterest().getBlend().getEngagement().getCap()`. D-12: "before" already comes from `topicWeights`, so no extra logic.

### `InterestBreakdown.Row` + `ScoreBreakdowns.java`

`Row` (L27-43) is already `@JsonInclude(NON_NULL)`; append `Double thumbsWeight, Double engagementWeight` (names discretionary), pass `null, null` in `profile(...)`, extend `topic(...)`:
```java
public static Row profile(int levelIndex, BigDecimal exact, long points) {
    return new Row(KIND_PROFILE, null, null, levelIndex, null, null, null, exact, points, null, null);
}
public static Row topic(long topicId, String name, double noul, double hinge, double weight,
                        BigDecimal exact, long points, double baseWeight, double learnedWeight) { ... }
```
`ScoreBreakdowns.java` L77: `Row.topic(t.topicId(), t.name(), t.noul(), t.hinge(), t.weight(), c.exact(), points[i], ...)` — pass the two new `TopicContribution` fields through.

### `application.yaml` (main + test)

Insert under `blend:` next to `learned-cap` (main L43-50, test L42-49), same literals in both (D-04), with a `# D-01:` comment in the house style:
```yaml
    blend:
      profile-points: 100
      learn-rate: 2
      learned-cap: 20
      # D-13: badge tier thresholds ...
      tiers:
```
`application-dev.yaml` unchanged; `DevProfileConfigTest` then passes because test yaml == main.

### `scripts/interest-calibration-replay.sh` / `.sql`

Env-default + validate-before-connect pattern (sh L21-38) and psql `-v` pass-through (L64-66):
```bash
LEARN_RATE="${LEARN_RATE:-2}"
LEARNED_CAP="${LEARNED_CAP:-20}"
...
if [[ ! "$LEARN_RATE" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
  echo "invalid candidate: $LEARN_RATE" >&2
  exit 2
fi
...
psql -X -A -F $'\t' -P footer=off -v ON_ERROR_STOP=1 \
  -v profilePoints="$pp" -v learnRate="$LEARN_RATE" -v learnedCap="$LEARNED_CAP" \
```
Add e.g. `OPEN_WEIGHT`/`SAVE_WEIGHT`/`ENGAGEMENT_CAP` env with decimal regex, and `-v openWeight=... -v saveWeight=... -v engagementCap=...` matching JdbcClient param names. Regenerate the `.sql` so every blend/learned copy is verbatim `LEARNED_CTE`/`blendCte`; do NOT add a new `WITH learned AS` statement (guard pins 5). Copy current `.sql` L30 into `InterestScoreQueriesZeroEngagementTest` BEFORE editing (Wave 0).

---

### New Testcontainers repository tests (`InterestLearnedGridTest`, `InterestScoreQueriesZeroEngagementTest`, `InterestScoreQueriesLatencyTest`, engagement tests)

**Analog:** `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java`

**Class setup** (L1-66):
```java
@DataJdbcTest
@Import({TestcontainersConfiguration.class, InterestScoreQueries.class})
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestScoreQueriesTest {
    @Autowired private InterestScoreQueries queries;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so the fixture is the whole table.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/priority-feed.xml", "Priority Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        seedFixture();
    }
```
For grid/zero tests that need their own constants (learnRate 32, cap 0, etc.), construct a second `InterestScoreQueries` manually with a fresh `MyfeederProperties` (RESEARCH Pattern 3) rather than the autowired one — check the constructor signature in `InterestScoreQueries` (JdbcClient + MyfeederProperties).

**Fixture helpers to copy** (L546-594): `insertArticle`, `insertTopic`, `insertScored`, `insertTopicScore`, `insertFeedback`, `insertPick`, `insertMatchingArticles`. Add `insertEngagement` copied from `V7EngagementMigrationTest` L231-233:
```java
private void insertEngagement(long articleId, String kind) {
    jdbc.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
}
```
**Assertion style** (L316-337):
```java
TopicWeight w = queries.topicWeights(List.of(rust)).get(rust);
assertThat(w.learnedRaw()).isCloseTo(24.0, within(1e-6));
assertWeight(w, 20.0, 40.0);
```
Grid exactness: use `BigDecimal.valueOf(double).compareTo(oracle) == 0` (RESEARCH anti-pattern: never assert sums on raw doubles). Latency: seed ~20k engagement rows in batch (`jdbc.batchUpdate`), 3 warm-ups + median of 5, assert `ext <= 10 * base + 250ms` (D-17).

### `MyfeederPropertiesValidationTest.java` (no Docker)

**Analog:** `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` (ApplicationContextRunner, loads main yaml from disk):
```java
private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(...));

PropertySource<?> mainYaml = new YamlPropertySourceLoader()
        .load("main-application-yaml", new FileSystemResource("src/main/resources/application.yaml"))
        .getFirst();
runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(mainYaml))
        .run(ctx -> { ... });
```
For this test use `new ApplicationContextRunner().withUserConfiguration(EnableProps.class)` (a nested `@EnableConfigurationProperties(MyfeederProperties.class)` config) plus `.withPropertyValues("myfeeder.interest.blend.engagement.cap=0", ...)`; assert `ctx.getStartupFailure()` has root cause `BindValidationException` with the fixed text, and `hasNotFailed()` for valid/cap=0 cases. Also run it once over main yaml (as above) to prove shipped values are valid.

### `InterestCalibrationReplaySqlTest.java` (add driver tests)

Copy `driverRejectsANonNumericCandidate` (L89-94) and reuse `runDriver` (L365-381, runs against dead port 127.0.0.1:1):
```java
DriverRun run = runDriver(Map.of("MYFEEDER_PG_PASSWORD", "x"), "100:70:4x");
assertThat(run.exitCode()).isEqualTo(2);
assertThat(run.stderr()).contains("invalid candidate: 100:70:4x");
```
New cases pass the env var (e.g. `Map.of("MYFEEDER_PG_PASSWORD","x","OPEN_WEIGHT","0.2x")`). `driverPassesEveryBlendParameter` can grep the `.sh` text for each `-v <param>=` against the JdbcClient param names.

### `V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` (invert, same commit as SQL edit)

Current L209-217:
```java
for (String table : List.of("article_engagement", "topic_suggestion_dismissal")) {
    assertThat(queries).doesNotContain(table);
    assertThat(replay).doesNotContain(table);
}
```
Invert: `article_engagement` must be contained in both; `topic_suggestion_dismissal` still absent (Phase 11). Rename the method accordingly.

### `EngagementApiIntegrationTest.engagementLeavesTheRankingUnchanged` (invert)

L320-342 seeds topic 20, scored article, then `putOpen`, `patchState(starred)`, `addToBoard`, and asserts score/breakdown unchanged. Invert to assert score rises / breakdown carries engagement weight, keep `assertThat(topicWeight(topicId)).isEqualTo(20)` (base never written) and `verify(jevApiClient, never()).judge(any(), any())`.

### Record-constructor ripple sites

- `ArticleFeedbackServiceTest` L157-158 (`new TopicEffect(...)`), L163-189 (`LearnedLimit.of(weight(...), 20)`), helper L192-195:
  ```java
  private static TopicWeight weight(double base, double learnedRaw, double learned) {
      return new TopicWeight(1, "t", base, learnedRaw, learned, 0);
  }
  ```
  Add ENGAGEMENT_CAP precedence cases in the same one-line `assertThat(LearnedLimit.of(...)).isEqualTo(...)` style.
- `ScoreBreakdownsTest` L30 (`new TopicContribution(...)`), `InterestControllerTest` L155-156 (`new TopicLearned(...)`), `ArticleControllerTest` L435 (`new TopicEffect(...)`).

### Frontend

`types/index.ts` L55-56:
```ts
export type LearnedLimit = 'NONE' | 'LEARNED_CAP' | 'SIGN_CLAMP' | 'WEIGHT_RANGE'
```
append `| 'ENGAGEMENT_CAP'`; add optional (`?:`) split fields to `TopicEffect` (L65-73) and `TopicBreakdownRow` (like `learnedWeight?: number` at L104), and to `TopicLearned` in `api/interest.ts` L26-31.

`utils/feedback.ts` `effectNote` L77-89 has a `default:` branch, so `ENGAGEMENT_CAP` falls through with no code change; add a `feedback.test.ts` case pinning that, modelled on L141-144:
```ts
effect('Rust', 40, 40, { baseWeight: 20, learned: 20, limit: 'LEARNED_CAP' }),
```
`TopicRow.tsx` L222 only special-cases `LEARNED_CAP`, so no change is needed. Run `npx tsc -b`.

## Shared Patterns

### DTO evolution
**Source:** `InterestBreakdown.Row` javadoc and CLAUDE.md "later fields are appended, existing ones are never renamed".
**Apply to:** `TopicContribution`, `TopicWeight`, `TopicLearned`, `TopicEffect`, `Row`, `LearnedLimit` constants, TS types. Append at the end; existing names keep the combined meaning (D-10/D-15).

### SQL parameterisation
**Source:** `InterestScoreQueries.learnedSql` L295-300.
**Apply to:** every new constant: a named bind with `CAST(:x AS float8)`, never string-concatenated. The psql `-v` names match.

### Rounding / split
**Source:** RESEARCH Pattern 1. Round each value to 6 decimals first, then subtract as `numeric`: `thumbs_w = ROUND(w_thumbs) - ROUND(base)`, `eng_w = ROUND(w) - ROUND(w_thumbs)`.

### Same-commit ordering (CAL-01)
The `LEARNED_CTE`/`blendCte` edit, the replay `.sql` regeneration and the V7 guard inversion go in the same plan and the same commit.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| Self-validating part of `MyfeederProperties` | config validation | startup | No class in the repo implements `Validator` or uses `@Validated`. Use RESEARCH Pattern 2. |
| Timing part of `InterestScoreQueriesLatencyTest` | test | timing | The repo has no latency or benchmark test. Use the D-17 formula. |

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{repository,service,model,config}`, `src/test/java/org/bartram/myfeeder/**`, `scripts/`, `src/main/resources`, `src/test/resources`, `src/main/frontend/src/{types,api,utils,components}`
**Files scanned:** about 25
**Pattern extraction date:** 2026-09-30
