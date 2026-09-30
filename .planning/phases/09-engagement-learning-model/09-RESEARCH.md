# Phase 9: Engagement Learning Model - Research

**Researched:** 2026-09-30
**Domain:** Query-time derived learning in PostgreSQL CTEs (Spring Data JDBC `JdbcClient`), Spring Boot 4 configuration validation, and drift-guarded calibration replay SQL
**Confidence:** HIGH. The SQL was prototyped and property-checked on a disposable Postgres 18.6. The Java string form was compiled and compared byte for byte with the tested SQL. The validation mechanism was run against the Spring Boot 4.0.8 jars from this project's classpath.

## Summary

Phase 9 adds a second derived term to `InterestScoreQueries.LEARNED_CTE`. Engagement is collapsed to one strength per article and articles that carry any thumbs vote are dropped. What remains is joined to the SCORED nouls and hinged, then scaled by `learnRate` and capped. The result is added under the existing single clamp. I built the full pipeline (`learned → engaged → eng_learned → eff → eff2(w_thumbs, w)`) and ran it on a 1,456-cell grid. The grid covered 14 base weights, 13 thumbs values from −32 to +32 (so the thumbs cap binds) and 8 engagement values from 0 to 16 (so the engagement cap binds). On every cell:

- `base + thumbs_applied + engagement_applied = effective` held exactly in `numeric`.
- `engagement_applied ≥ 0`.
- Negative-base topics never moved.
- The sign clamp and ±50 were never violated.
- `w` and `w_thumbs` equalled an exact numeric oracle.

With `cap = 0`, the effective weights (compared bit for bit with `float8send`), every `raw_n` and the Priority order were identical to the v0.2.1 CTE. The same held with no engagement rows at cap 8. [VERIFIED: scratch Postgres 18.6 run, this session]

Two findings change what the planner must write. Neither is visible from CONTEXT.md.

1. **Zero floor.** The engagement term must be clamped at zero: `GREATEST(0, LEAST(cap, eng_raw))`, not just `LEAST(cap, eng_raw)`. D-02 makes `cap = 0` valid *whatever the weights are*, so the weights may be negative. Without the floor, `cap = 0` with open −5 / save −1 produced an engagement term of **−5356** on 8 of 10 topics. With the floor it was 0. [VERIFIED: scratch run]
2. **Latency guard.** D-13's example guard ("< 3× baseline") is too tight for the topic-weights query, and would flake or fail. On a prod-like dataset, 20k engagement rows took that query from **7.5 ms to 37 ms (≈4.9×)**. The absolute cost is small: a roughly fixed ~30 ms per learned-CTE evaluation. EXPLAIN shows hash joins on existing primary keys and a full aggregate that no index can shortcut, so **no new index or migration is needed**. Use an absolute-plus-ratio guard instead (see Validation Architecture).

Startup validation needs no new dependency. Spring Boot 4.0.8's `ConfigurationPropertiesBinder` automatically uses a `@ConfigurationProperties` class that implements `org.springframework.validation.Validator` as a self-validator, with no `@Validated` or JSR-303. I ran this against the project's classpath:

- `cap=20`, `save=0.25`, `save=1`, `open=save=0` and `cap=-1` each fail startup with a `BindValidationException` that carries the fixed text.
- `cap=0` with any weights starts. [VERIFIED: throwaway probe on the project's test runtime classpath]

The replay regeneration is mechanical but must land in the same plan as the CTE change. Five `WITH learned AS` lines are regenerated from the Java, and three `-v` variables are added to the driver. Two Phase 8 guards must be inverted in this phase: `V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` and `EngagementApiIntegrationTest.engagementLeavesTheRankingUnchanged`. The second is not mentioned anywhere in CONTEXT.md, and it **will fail** once engagement affects scores.

**Primary recommendation:** Paste the tested `LEARNED_CTE` below verbatim, which includes the zero floor and zeroes `eng_raw` for negative-base topics. Compute the rounded split by subtracting rounded values in SQL. Validate the settings with a self-validating `MyfeederProperties implements Validator`. Regenerate the replay in the same plan. Replace the 3× latency guard with `extended ≤ 10 × baseline + 250 ms`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (settled before this discussion; do not reopen)
- Engagement skips topics whose **base** weight is negative. Topics with base ≥ 0, including base 0, qualify (LRN-03).
- Engagement has its own additive cap below the thumbs cap (20). Thumbs take their share first.
- Each engaged article counts once, at its strongest kind: save (STAR / BOARD / RAINDROP, all one save weight, with no per-kind weights) > open (OPEN_ORIGINAL). Collapse to one `MAX` strength per article **before** joining topics.
- Any thumbs vote on an article, narrowed or not, removes that article's engagement contribution on every topic (`NOT EXISTS` on `article_feedback`). Removing the vote restores it.
- Only `article_score.status = 'SCORED'` articles count. Engaged-but-unscored articles stay dormant (they are never made eligible for scoring).
- `WITH learned AS` stays the first token of `LEARNED_CTE` (the drift guard looks for it). `thumbs_applied` and `engagement_applied` are computed in SQL, with engagement by subtraction. Plan test-first, including a real-Postgres grid property test for the exact split.
- New yaml keys also go in the test yaml and the dev overlay (`DevProfileConfigTest`). New psql `-v` variables use the same names as the JdbcClient parameters. Kind names and SQL aliases must avoid words the replay's write-keyword check rejects.
- DTO rule: append fields, never rename.

#### Starting values and the zero rule (LRN-05)
- **D-01:** Main `application.yaml` ships conservative values until Phase 12 calibrates them: open **0.25**, save **0.5**, engagement cap **8**. Keys live under `myfeeder.interest.blend` (exact names are Claude's discretion; research suggests `engagement.{open-weight, save-weight, cap}`).
- **D-02:** Startup validation: **`cap = 0` disables engagement learning**, whatever the weights are, and is always valid. When cap ≠ 0, require `0 ≤ open < save < 1` **and** `0 < cap < learned-cap`. Anything else refuses to start with a fixed-text message.
- **D-03:** Save is **strictly below 1**, so an explicit 👍 always outweighs any single implicit signal.
- **D-04:** The test `application.yaml` carries the **same values as main** (0.25 / 0.5 / 8), so the dev overlay needs nothing extra. Grid, latency and "zero equals v0.2.1" tests bind their own values where needed. Existing `InterestScoreQueriesTest` fixtures have no engagement rows, so they are unaffected.
- **D-05:** "With engagement set to zero, scores and Priority order match v0.2.1 exactly" is proven with cap = 0 (and, separately, with engagement rows absent).

#### Engagement arithmetic
- **D-06:** Engagement works like a fractional vote through `learnRate`: `eng_raw = learnRate × Σ_articles strength × hinge`, then `eng = LEAST(cap, eng_raw)` (positive only, so there is no lower bound below 0). At learnRate 2, one full-hinge save adds 1.0 point and one open adds 0.5.
- **D-07:** The hinge is the same as for votes: `max(0, (noul − 0.5) × 2)`. Only matched topics move, in proportion to match strength.
- **D-08:** There is no age window. Any SCORED engaged article counts whatever its age or read state, the same as thumbs. Retention deletes remove it by cascade, and decay stays deferred (ENG-F8).
- **D-09:** **One clamp on the full sum.** `w_thumbs = clamp(base + thumbs)` (today's `w`), and `w = clamp(base + thumbs + eng)`, with the existing sign clamp and ±50 applied once to the whole sum. Down-votes clamped past zero must be "paid back" before engagement shows, so explicit thumbs dominate. Split: `thumbs_applied = w_thumbs − base` and `engagement_applied = w − w_thumbs`, so `base + thumbs_applied + engagement_applied = w` exactly. For a negative-base topic, eng is 0 before the sum.

#### How far the split reaches in Phase 9
- **D-10:** **Append split fields now** to the backend DTOs that carry learned values. `TopicContribution` gets thumbs and engagement applied parts. `TopicWeight` gets the engagement raw and capped parts next to the existing thumbs `learnedRaw`/`learned`. `TopicLearned` / `GET /api/interest/topics/learned` and `FeedbackResult` effects get engagement where they expose learned values. Existing fields keep their current names and mean the **combined** learned part (`w − base`), so the current UI stays correct. Phase 10 only renders the split. Exact field names are Claude's discretion.
- **D-11:** `LearnedLimit` gains an appended **`ENGAGEMENT_CAP`** value. Precedence: `LEARNED_CAP` (thumbs `|learnedRaw| ≥ learned-cap`) → `ENGAGEMENT_CAP` (engagement raw ≥ engagement cap, only when cap > 0) → `SIGN_CLAMP` / `WEIGHT_RANGE`, judged on `base + thumbs + engagement` → `NONE`. The frontend `LearnedLimit` type gains the value. `effectNote` / `TopicRow` may fall through to their default for now, and Phase 10 words it.
- **D-12:** The vote effect before/after is read through the same topic-weights query, so "before" includes the engagement that the vote then replaces.

#### Latency budget and shipping
- **D-13:** Latency uses **record + generous guard**. A Testcontainers test seeds about 20k `article_engagement` rows (a realistic mix of kinds, scored and unscored, with and without votes) and measures the Priority page query, the per-article breakdown and the topic-weights query against a no-engagement baseline. The numbers and an `EXPLAIN (ANALYZE)` go into the phase's VERIFICATION. The automated assertion catches only catastrophic plans (for example < 3× baseline) so that it does not flake. An index is added only if EXPLAIN shows it is needed; that would need a new migration and is a planner decision flagged to the user.
- **D-14:** **No release in Phase 9.** Work happens on a feature branch and merges to main with `--no-ff`, with no tag, image or deploy. Prod stays on v0.3.0 (capturing engagement) until Phase 12 ships calibrated v0.3.1. Phase 12 calibrates by read-only replay, which needs no deploy.

### Claude's Discretion
- Exact yaml key names, `MyfeederProperties.Blend` nesting and how validation is wired (e.g. `@Validated` + a custom check, or an `InitializingBean`), provided startup fails with a clear fixed-text message.
- CTE names (`engaged`, `eng_learned`, …) and new DTO field names, within the replay keyword constraint.
- Grid test value ranges (base, vote sums and engagement sums that cover each clamp branch, both caps and ±50).
- How the replay script exposes the new `-v` variables and validates them before connecting (same pattern as the existing ones).

### Deferred Ideas (OUT OF SCOPE)
- The Interests learned line split with contributing-article counts and an at-cap flag (ENG-F6): future; Phase 9 only appends backend fields.
- Engagement decay / rate normalization (ENG-F8): future.
- User-facing wording for `ENGAGEMENT_CAP` in toasts and Interests: Phase 10.
- Reviewed Todos (not folded): `2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` (Tune Raindrop resilience): it matched only on keywords, and PROJECT.md keeps carried-forward cleanup out of this milestone.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LRN-01 | Each engaged, SCORED article counts once, at its strongest kind (save > open), as a fractional up-vote on the topics it matched. It is derived at query time, with no Jev calls and no writes to topic weights. | `engaged` CTE (`MAX(CASE kind …)` grouped by article) → `eng_learned` (join `article_score` SCORED + `article_topic_score`, hinge). Tested SQL is in Code Examples. The inverted `EngagementApiIntegrationTest` proves the score rises, `interest_topic.weight` is unchanged and there are no Jev calls. |
| LRN-02 | A thumbs vote replaces the article's engagement contribution, and removing the vote restores it. | `WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id)` inside `engaged`. It is article-wide, so it also drops topics a narrowed vote did not pick. There is a restore-exactly test in the style of `deletingTheVoteRestoresExactly`. |
| LRN-03 | Engagement only nudges topics whose base weight is ≥ 0. | `CASE WHEN t.weight < 0 THEN 0 …` on both `eng_raw` and `eng` in `eff`. Grid: `negbase_moved = 0`. |
| LRN-04 | Engagement has its own cap, below the thumbs cap and added on top of the thumbs adjustment, and the effective weight stays inside the sign clamp and ±50. | `eng = GREATEST(0, LEAST(:engagementCap, learnRate × eng_sum))`; one clamp on `base + learned + eng` (D-09); rounded split by subtraction. Grid of 1,456 cells: zero violations and an exact numeric sum. |
| LRN-05 | Open weight, save weight and cap come from committed yaml; zero disables; startup rejects save ≤ open or cap ≥ thumbs cap. | `MyfeederProperties implements Validator` (verified mechanism). Keys under `myfeeder.interest.blend.engagement.*` in main and test yaml. Zero floor in SQL. `ApplicationContextRunner` tests. |
| CAL-01 | Replay SQL and drift guard regenerated for the extended CTE in the same change. | Regenerate the 5 `WITH learned AS` lines. Add 3 `-v` variables and env-var validation to the driver. Add a new "every blend parameter is passed by the driver" drift check. Prototype replay ran read-only with the new variables. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted directives the planner must honor (root `CLAUDE.md`, `.claude/CLAUDE.md`, `~/.claude/CLAUDE.md`):

- **Build and test:** Gradle only (never Maven). The backend test command is `./gradlew test -x npmBuild -x npmInstall --tests "<fqcn>"`. Docker is required for Testcontainers.
- **Offline tests:** never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`, and no test may activate the `dev` profile (`DevProfileConfigTest`).
- **Git:** non-trivial work goes on a feature branch and merges to main with `--no-ff` (D-14 as well). Subagents never check out a detached HEAD. Push only when asked.
- **GSD:** edits go through a GSD workflow. When a plan fixes an item tracked elsewhere, mark it resolved in the same commit.
- **Spring Data JDBC, not JPA:** custom SQL goes through `JdbcClient` / `@Query`. There are no derived queries.
- **Jackson 3:** databind is in `tools.jackson.databind.*`. Annotations stay in `com.fasterxml.jackson.annotation.*` (for example `@JsonInclude` on `InterestBreakdown.Row`).
- **BOM-managed versions:** no versions on individual Spring deps. This phase adds no dependency.
- **Interest ranking rules:**
  - Every blend constant applies at query time, so a change needs no Jev re-score.
  - Tuned values go in main `application.yaml`, with the dev-overlay rule. Never tune through Helm `--set` or env (07 D-14).
  - Keep the test yaml at profile-points 100 / tiers 70 / 40.
- **Replay:** the calibration replay must be regenerated byte for byte from the Java and must stay read-only (`default_transaction_read_only=on`, no write keywords, topic ids only).
- **Scoring write path:** keep the "scores discarded if the rubric changed mid-call" check (unaffected by this phase).
- **Surgical changes:** every changed line must trace to the request. Don't refactor adjacent code (relevant to the optional `contrib NOT MATERIALIZED` observation below).
- **Frontend type-check:** use `npx tsc -b` (not `tsc --noEmit`).
- **Docs:** update CLAUDE.md where behavior changes (the "ranking SQL does not read engagement until Phase 9" line, the Interest Ranking section and the replay usage). Do not hand-edit generated OpenWiki pages.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Collapse engagement per article, thumbs override, SCORED filter, hinge, cap, clamp, split | Database / Storage (SQL CTE in `InterestScoreQueries`) | — | The derived model must be computed in one place that the ranking, badge, "Why N?", topic weights and replay all read. SQL is that single source (existing pattern). |
| Binding the three constants | API / Backend (`InterestScoreQueries.learnedSql`) | — | `learnedSql()` is the single binding point for learned parameters. |
| Startup validation of the constants | API / Backend (config binding, `MyfeederProperties`) | — | It fails fast at context start, before any query runs. |
| `LearnedLimit` classification | API / Backend (`service/LearnedLimit`) | — | It is judged on the SQL's 6-decimal values (existing pattern). |
| DTO split fields (`TopicWeight`, `TopicContribution`, `InterestBreakdown.Row`, `TopicLearned`, `TopicEffect`) | API / Backend | Browser / Client (types only) | The server computes and the client never recomputes (existing contract). Phase 9 only appends fields; Phase 10 renders them. |
| `LearnedLimit` union and optional fields in TS types | Browser / Client | — | Type parity only. `effectNote` / `LearnedLine` fall through to their default branches. |
| Calibration replay | Database (read-only psql script) | — | Verbatim copy of the app SQL, guarded by `InterestCalibrationReplaySqlTest`. |

## Standard Stack

No new libraries. Everything uses what is already on the classpath.

### Core (already present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.8 | Config binding + self-validation (`ConfigurationPropertiesBinder`) | Project framework [VERIFIED: build.gradle.kts:3 `id("org.springframework.boot") version "4.0.8"`] |
| Spring `JdbcClient` | via Boot BOM | Named-parameter SQL (`:learnRate`, …) | Existing query path in `InterestScoreQueries` [VERIFIED: InterestScoreQueries.java:295-300] |
| PostgreSQL (Testcontainers `postgres:latest`) | 18.6 today | Real-Postgres tests | [VERIFIED: `docker exec … select version()` → `PostgreSQL 18.6`; TestcontainersConfiguration uses `postgres:latest`] |
| Testcontainers + `@DataJdbcTest` / `@SpringBootTest` | via Boot BOM | Grid, latency and integration tests | Existing test patterns |
| `ApplicationContextRunner` (spring-boot-test) | via Boot BOM | Startup-failure tests for validation | Already used in `TypeSafeConfigTest`, `HttpClientConfigurationTest` |
| Vitest + `tsc -b` | existing | Frontend type parity | Existing |

**Installation:** none.

**Version verification:** no package is installed, so no registry lookup was needed. The Postgres version was read from the running container.

## Package Legitimacy Audit

This phase installs no external packages (npm, Maven or otherwise).

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| — | — | — | — | — | — | No packages installed |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 capture (Phase 8, unchanged)              query time (every Priority page, badge, "Why N?", topic weights, vote effect)
 ─────────────────────────────              ──────────────────────────────────────────────────────────────────────────────
 PUT /engagement/open ─┐
 PATCH starred=true  ──┼──> article_engagement (article_id, kind) ──┐
 POST /boards/…/art. ──┤                                            │
 Raindrop save ok ─────┘                                            ▼
                                                     engaged: 1 row / article, strength = MAX(open→:engagementOpenWeight,
                                                              save→:engagementSaveWeight); drop article if ANY article_feedback row
                                                                            │
 article_feedback (+ _topic picks) ──> learned: vote_sum per topic          │ JOIN article_score SCORED + article_topic_score
                                              │                             ▼
                                              │                eng_learned: eng_sum per topic = Σ strength × hinge
                                              ▼                             │
                             eff (per interest_topic):  learned = clamp(±learnedCap, learnRate×vote_sum)
                                                        eng_raw = base<0 ? 0 : learnRate×eng_sum
                                                        eng     = base<0 ? 0 : GREATEST(0, LEAST(:engagementCap, eng_raw))
                                              │
                                              ▼
                             eff2: w_thumbs = signClamp±50(base + learned)       (== v0.2.1 w)
                                   w        = signClamp±50(base + learned + eng)
                                              │
               ┌──────────────────────────────┼───────────────────────────────────────┐
               ▼                              ▼                                       ▼
   contrib → blended (raw_n) → Priority     TOPIC_WEIGHTS_SELECT → TopicWeight →    breakdown topics select:
   sort / badge (uses w only)               LearnedLimit / TopicLearned / effects   thumbs_w = r(w_thumbs) − r(base)
                                                                                    eng_w    = r(w) − r(w_thumbs)   (r = ROUND(::numeric,6))
                                                     same text, psql -v values ──> scripts/interest-calibration-replay.sql
```

### Recommended file touch list
```
src/main/java/org/bartram/myfeeder/
├── config/MyfeederProperties.java          # Blend.Engagement {openWeight, saveWeight, cap} + implements Validator
├── repository/InterestScoreQueries.java    # LEARNED_CTE, contrib (+ e.w_thumbs), TOPIC_WEIGHTS_SELECT, learnedSql binds,
│                                           #   TopicWeight/TopicContribution appended fields, breakdown select
├── repository/ArticleEngagementStore.java  # javadoc only ("ranking SQL does not read it in this phase" is now false)
├── model/InterestBreakdown.java            # Row appended thumbsWeight/engagementWeight (recommended, see Open Q2)
├── service/ScoreBreakdowns.java            # pass the two new fields through Row.topic(...)
├── service/LearnedLimit.java               # ENGAGEMENT_CAP appended + of(w, learnedCap, engagementCap)
├── service/ArticleFeedbackService.java     # LearnedLimit calls, TopicLearned/TopicEffect new fields
├── service/FeedbackResult.java             # TopicEffect appended fields
└── service/TopicLearned.java               # appended fields
src/main/resources/application.yaml         # myfeeder.interest.blend.engagement.{open-weight,save-weight,cap}
src/test/resources/application.yaml         # same literals (D-04); application-dev.yaml unchanged
scripts/interest-calibration-replay.{sql,sh}
src/main/frontend/src/types/index.ts        # LearnedLimit += 'ENGAGEMENT_CAP'; optional fields
src/main/frontend/src/api/interest.ts       # TopicLearned optional fields
CLAUDE.md                                   # Interest Ranking + engagement capture bullets + replay usage
```

### Pattern 1: One clamp on the full sum, with the split taken by subtracting rounded values
**What:** Compute `w_thumbs` (byte-identical to today's `w` expression) and `w` (the same expression plus `+ e.eng`) in `eff2`. Round each to 6 decimals, then subtract in `numeric`.
**Why subtraction of rounded values, not rounding of float differences:** `ROUND((w − w_thumbs)::numeric, 6)` can disagree with `ROUND(w,6) − ROUND(w_thumbs,6)` by 1e-6. For example, w = 1.0000004 and w_thumbs = 0.0000006 give 1.000000 against 0.999999. Only numeric subtraction of the already-rounded values makes `base_w + thumbs_w + engagement_w = ROUND(w,6)` hold exactly. [VERIFIED: grid `sum_broken = 0` with this formulation]
**Example (breakdown topic select):**
```sql
-- Source: tested in scratch Postgres 18.6; proposed column aliases (discretion)
SELECT c.topic_id, t.name, c.noul, c.hinge,
       ROUND(c.w::numeric, 6) AS w, ROUND(c.points::numeric, 6) AS exact,
       ROUND(c.base::numeric, 6) AS base_w,
       ROUND(c.w::numeric, 6) - ROUND(c.base::numeric, 6) AS learned_w,       -- combined, = thumbs_w + eng_w exactly
       ROUND(c.w_thumbs::numeric, 6) - ROUND(c.base::numeric, 6) AS thumbs_w,
       ROUND(c.w::numeric, 6) - ROUND(c.w_thumbs::numeric, 6) AS eng_w
FROM contrib c JOIN interest_topic t ON t.id = c.topic_id WHERE c.article_id = :articleId
```
Today `learned_w` is `ROUND(c.learned_applied::numeric, 6)` [VERIFIED: InterestScoreQueries.java:212-214 quotes `ROUND(c.learned_applied::numeric, 6) AS learned_w`]. Redefining it as `ROUND(w,6) − ROUND(base,6)` makes `learned = thumbs + engagement` exact. Because `base` is an integer, this agreed with the old expression on 2,000,000 random values. It can differ only in contrived half-ulp cases, and it never touches scores. [VERIFIED: scratch run, `differ = 0` over 2M random rows]

### Pattern 2: Self-validating `@ConfigurationProperties`
**What:** `MyfeederProperties implements org.springframework.validation.Validator`, with `supports(type)` limited to `MyfeederProperties`. `validate()` calls `errors.reject(code, FIXED_TEXT)` when D-02 fails.
**Why:** `ConfigurationPropertiesBinder.getValidators()` adds the bound object itself as a validator when it implements `Validator`. No `@Validated` or JSR-303 is needed [VERIFIED: spring-boot v4.0.8 `ConfigurationPropertiesBinder.java` lines 152-176, `getSelfValidator` → `(value instanceof Validator validator) ? validator : null`]. The failure surfaces as `BindValidationException` → `BindValidationFailureAnalyzer` "Reason: <fixed text>". The check runs in every context that binds `MyfeederProperties`, including `@DataJdbcTest` slices that use `@EnableConfigurationProperties(MyfeederProperties.class)`.
**Documented alternative:** a `static @Bean` named `configurationPropertiesValidator` [CITED: docs.spring.io Spring Boot 4.0 reference, "@ConfigurationProperties Validation"]. It is not preferred, because slices that don't load that `@Configuration` would skip the check.

### Pattern 3: Grid property test with dyadic values (exact oracle)
**What:** Build a second `InterestScoreQueries` in a `@DataJdbcTest` with its own `MyfeederProperties`: learnRate **32**, learnedCap 20, open 0.25, save 0.5, cap 8. With `noul = 0.5 + k/64`, hinge = k/32, so one voted article gives `learned_raw = ±k` exactly and one saved article gives `eng_raw = k/2` exactly. Every clamp boundary (exactly 0, exactly ±50, exactly at each cap) is then reachable in float8 without rounding noise, and a `BigDecimal` oracle can be compared with `compareTo == 0`.
**Grid used in the scratch proof:**
- base ∈ {−50, −45, −30, −10, −5, −1, 0, 1, 5, 10, 30, 42, 45, 50}
- thumbs raw ∈ {−32, −24, −20, −10, −5, −1, 0, 1, 5, 10, 20, 24, 32}
- engagement raw ∈ {0, 0.5, 2, 4, 7.5, 8, 10, 16}

That is 1,456 topics and about 2,700 articles. It seeds in under a second [VERIFIED: scratch run].

### Anti-Patterns to Avoid
- **Joining raw `article_engagement` rows to topics before collapsing.** This multiplies by kinds. Collapse with `GROUP BY article_id` + `MAX(...)` first (Pitfall 1).
- **`LEAST(cap, eng_raw)` without `GREATEST(0, …)`.** It breaks D-02's "cap = 0 disables whatever the weights are" (a −5356 leak, verified).
- **Clamping sequentially**, as in `clamp(clamp(base+t)+e)`. Base 10, t −20, e +8 would give 8 instead of 0. D-09 requires one clamp on the full sum.
- **Rounding each split part independently.** Use subtraction of rounded values (Pattern 1).
- **Asserting the exact sum on Java `double`s.** Convert with `BigDecimal.valueOf(double)`, which recovers the 6-decimal value because each value has ≤ 9 significant digits, then compare with `compareTo`.
- **Adding a new `WITH learned AS` statement to the replay in Phase 9** (for example an engagement histogram). The drift guard pins exactly 5 such statements in a fixed order. Leave new learned-CTE sections to Phase 12 (CAL-02), which updates the guard.
- **Words the replay check rejects:** `(?i)\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\b`. Do not use them in any alias or kind literal. The proposed text contains none [VERIFIED: grep on the proposed CTE and the prototype replay → 0 matches].

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Startup refusal on bad config | `@PostConstruct`/`InitializingBean` check in a random bean, or a custom `ApplicationListener` | `MyfeederProperties implements Validator` (Boot's self-validation) | Binder-integrated, fails before any bean uses the values, and gives a standard failure analysis. Verified on 4.0.8. |
| Testing startup failure | Booting the full app with bad yaml | `ApplicationContextRunner().withUserConfiguration(...).withPropertyValues(...)` + `assertThat(ctx).hasFailed()` | Fast, no Docker; the pattern already exists in `TypeSafeConfigTest` |
| Exact decimal split | Java-side arithmetic on doubles | SQL `numeric` subtraction of `ROUND(…,6)` values | The existing "server computes, client prints" contract; float sums are not exact |
| Per-topic learned caching for speed | A "learning version" cache | Nothing: measured cost is ~30 ms at 20k rows | Pitfall 12 says to cache only if the budget fails; it doesn't |
| New index for engagement | `CREATE INDEX … article_engagement(...)` in V8 | Existing PKs `article_engagement_pkey (article_id, kind)` and `article_topic_score_pkey (article_id, topic_id)` | EXPLAIN shows full hash aggregates; no index can prune an unfiltered aggregate [VERIFIED: EXPLAIN ANALYZE, scratch] |

**Key insight:** the cost of this phase is correctness under clamps and exact decimal identities, not machinery. Every value comes from one CTE, and every split is a subtraction of rounded values, so no second implementation (Java, TS or replay) can drift.

## Runtime State Inventory

Not a rename or refactor phase. For completeness, since the ranking behavior changes:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Prod `article_engagement` rows captured since the v0.3.0 release (Phase 8). They will start counting when a Phase 9 build is deployed, which D-14 defers to Phase 12. | None in Phase 9. No backfill (settled). Phase 12 calibrates on them via the replay. |
| Live service config | None. Constants live only in committed yaml (07 D-14: never Helm/env). | None |
| OS-registered state | None: verified by reading the Helm chart/deploy scope in CLAUDE.md; no scheduler or task embeds these values. | None |
| Secrets/env vars | None new. The replay driver gains non-secret env vars (`ENGAGEMENT_*`). | None |
| Build artifacts | None. No release, image or tag in Phase 9 (D-14). | None |

## Common Pitfalls

### Pitfall 1: Negative weights leak through at `cap = 0`
**What goes wrong:** D-02 accepts `cap = 0` with any weights. With `eng = LEAST(0, learnRate × eng_sum)` and negative weights, `eng` is negative and engagement lowers topic weights.
**Why it happens:** D-06's "positive only" assumes the validation `0 ≤ open`, which D-02 skips at cap 0.
**How to avoid:** `GREATEST(0, LEAST(CAST(:engagementCap AS float8), …))`. Add a test that binds `cap=0, open=-5, save=-1` and asserts every `TopicWeight` equals the no-engagement weights.
**Warning signs:** `min(eng) < 0` in the replay learned section. [VERIFIED: scratch, −5356.44 without the floor, 0 with it]

### Pitfall 2: The 3× latency guard flakes on topic weights
**What goes wrong:** The topic-weights query is cheap at baseline (~7.5 ms), and the engagement branch adds a roughly fixed ~30 ms. The ratio is about 4.9× on prod-like data and 3.0× on a 6× larger dataset.
**How to avoid:** Use `extendedMedian ≤ 10 × baselineMedian + 250 ms` per query, with a median of 5 warm runs after 3 warm-ups. It still catches catastrophic plans, which would take seconds.
**Warning signs:** A red build on a busy laptop with no SQL change.

### Pitfall 3: Phase 8 guards that must be inverted in the same plan as the CTE change
- `V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` [VERIFIED: V7EngagementMigrationTest.java:208-217, which asserts `assertThat(queries).doesNotContain(table)` for `"article_engagement", "topic_suggestion_dismissal"`]. Invert it: `article_engagement` **must** appear in `InterestScoreQueries.java` and the replay, and `topic_suggestion_dismissal` must still **not** appear (Phase 11 is a separate service).
- `EngagementApiIntegrationTest.engagementLeavesTheRankingUnchanged` [VERIFIED: EngagementApiIntegrationTest.java:320-342, which asserts `JsonPath.read(after, "$.interestScore")).isEqualTo(scoreBefore)` and `topicWeight(topicId)).isEqualTo(20)`]. It fails as soon as engagement counts: topic base 20, noul 0.95, and after open + star + board the article is a save at learnRate 2, so eng_raw = 0.9, w = 20.9 and the raw score moves from 68.0 to 68.81. The badge goes from 68 to 69 and the breakdown changes. Rewrite it as the SC-1 end-to-end test: the score/breakdown **rises**, `interest_topic.weight` stays 20, and `verify(jevApiClient, never()).judge(any(), any())`.
- CLAUDE.md line "The ranking SQL does not read engagement until Phase 9 (`V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` guards this)" and the `ArticleEngagementStore` javadoc ("the ranking SQL does not read it in this phase") [VERIFIED: ArticleEngagementStore.java:13-14] must be updated.

### Pitfall 4: `DevProfileConfigTest` compares raw yaml strings
**What goes wrong:** `devOverlayResolvesEveryMainKeyToMainsValue` compares `String.valueOf(...)` of the raw yaml values [VERIFIED: DevProfileConfigTest.java:86-89]. `cap: 8` (Integer "8") in main against `cap: 8.0` (Double "8.0") in the test yaml fails.
**How to avoid:** Type identical literals in both files: `open-weight: 0.25`, `save-weight: 0.5`, `cap: 8`. The overlay gets nothing (D-04).

### Pitfall 5: Existing `learned` fields do NOT currently mean `w − base`
**What goes wrong:** D-10 says existing fields "mean the combined learned part (w − base)". That is true today only for `TopicContribution.learnedWeight` / `Row.learnedWeight`. `TopicLearned.learned` and `TopicEffect.learned` are the **capped thumbs value before the clamp**. For example, `InterestControllerTest` builds `new TopicLearned(11, 5, -20, 0, LearnedLimit.LEARNED_CAP)`: learned −20, effective 0 [VERIFIED: InterestControllerTest.java:156]. `TopicRow.LearnedLine` and `effectNote`'s LEARNED_CAP branch print that value.
**How to avoid:** See Open Question 1. The recommendation keeps "capped, before the clamp" semantics and makes it combined: `learned = thumbs capped + engagement capped`. That is byte-identical to v0.2.1 at zero engagement, and it is the same sum `LearnedLimit` judges (D-11).

### Pitfall 6: The vote toast lists `ENGAGEMENT_CAP` topics as "+0.0"
**What goes wrong:** `formatVoteToast` lists any effect with `e.limit !== 'NONE'` [VERIFIED: utils/feedback.ts:109 `.filter(({ e, d }) => Math.round(d * 10) !== 0 || e.limit !== 'NONE')`]. After D-11, a topic at the engagement cap reports `ENGAGEMENT_CAP` on every vote, including topics the vote barely moved. D-11's precedence also puts ENGAGEMENT_CAP ahead of SIGN_CLAMP / WEIGHT_RANGE, so it can hide those notes.
**How to avoid:** D-11 allows the fall-through in Phase 9, and there is no release (D-14). Pin the current behavior with a vitest case so Phase 10 changes it deliberately, and record it as a Phase 10 input.

### Pitfall 7: Record constructor ripple
Appending record components breaks every positional `new TopicWeight(…)`, `new TopicLearned(…)`, `new TopicEffect(…)`, `new TopicContribution(…)` and `Row.topic(…)` call:
- 18 sites in `ArticleFeedbackServiceTest`
- 2 in `InterestControllerTest`
- 1 in `ArticleControllerTest`
- 1 helper in `ScoreBreakdownsTest`
- plus `ScoreBreakdowns` and `ArticleFeedbackService`

[VERIFIED: grep this session]. Update the call sites explicitly. The JSON field order appends, per the DTO rule.

### Pitfall 8: The shared DB across `@SpringBootTest` classes
Phase 8's capture paths (star, board) now change scores. Integration test classes share one Testcontainers DB and clean up in `@BeforeEach` by name prefix. Engagement left by one class can only move topics that have nouls for that article, and topics are per-class. Keep that naming discipline in any new integration test so learned weights stay isolated.

### Pitfall 9: The `contrib` CTE is materialized in the breakdown topics query (pre-existing)
In `breakdownInputs`' second query, `contrib` is referenced by both `blended` (unused but still textually referenced) and the final select. Postgres therefore materializes `contrib` for **every** article (300k rows / 24 MB on disk in the heavy dataset) and then filters it to one article [CITED: postgresql.org/docs/18/sql-select.html, which says a side-effect-free WITH query "is folded into the primary query if it is used exactly once"]. This predates Phase 9, and engagement makes it modestly worse: 41 → 86 ms prod-like, 243 → 601 ms heavy. `contrib AS NOT MATERIALIZED (` cut it to 37 ms / 112 ms with identical rows [VERIFIED: scratch]. It is **not required**: the budget passes without it, and CLAUDE.md asks for surgical changes. Record it in VERIFICATION as an observation or deferred item, or propose it to the user as a one-token optional follow-up that also changes the replay text.

## Code Examples

In-repo values quoted verbatim (read this session):

- `EngagementKind`: `OPEN_ORIGINAL, STAR, BOARD, RAINDROP` [VERIFIED: model/EngagementKind.java:4-6]
- V7: `kind TEXT NOT NULL CHECK (kind IN ('OPEN_ORIGINAL', 'STAR', 'BOARD', 'RAINDROP'))`, `PRIMARY KEY (article_id, kind)`, `article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE` [VERIFIED: V7__engagement.sql:4-9]
- `learnedSql` binds `.param("learnRate", blend.getLearnRate())` and `.param("learnedCap", blend.getLearnedCap())` [VERIFIED: InterestScoreQueries.java:295-300]
- Current `eff2` clamp: `CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned)) WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned)) ELSE GREATEST(-50, LEAST(50, e.learned)) END AS w` [VERIFIED: InterestScoreQueries.java:115-118]
- Current `contrib` select list: `GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, e.base, e.w - e.base AS learned_applied, GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points` [VERIFIED: InterestScoreQueries.java:316-319]
- `Blend` fields: `private int profilePoints = 100;`, `private double learnRate = 2;`, `private int learnedCap = 20;`, `private Tiers tiers = new Tiers();` [VERIFIED: MyfeederProperties.java:53-61]
- Main yaml: `blend:` / `profile-points: 100` / `learn-rate: 2` / `learned-cap: 20` / `tiers:` `high: 70` `neutral: 22` [VERIFIED: src/main/resources/application.yaml:43-50]. Test yaml: the same, with `neutral: 40` [VERIFIED: src/test/resources/application.yaml:42-49]
- `LearnedLimit`: `NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE;` and `public static LearnedLimit of(TopicWeight w, double cap)` [VERIFIED: service/LearnedLimit.java:19-22]
- `TopicWeight(long topicId, String name, double base, double learnedRaw, double learned, double effective)` [VERIFIED: InterestScoreQueries.java:124-125]
- `TopicContribution(long topicId, String name, double noul, double hinge, double weight, BigDecimal exact, double baseWeight, double learnedWeight)` [VERIFIED: InterestScoreQueries.java:90-91]
- `TopicEffect(long topicId, String name, double before, double after, double baseWeight, double learned, LearnedLimit limit)` [VERIFIED: FeedbackResult.java:22-23]
- `TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight, LearnedLimit limit)` [VERIFIED: TopicLearned.java:9-10]
- `Row(String kind, Long topicId, String name, Integer levelIndex, Double noul, Double hinge, Double weight, BigDecimal exact, long points, Double baseWeight, Double learnedWeight)` with `@JsonInclude(JsonInclude.Include.NON_NULL)` [VERIFIED: model/InterestBreakdown.java:25-27]
- Frontend: `export type LearnedLimit = 'NONE' | 'LEARNED_CAP' | 'SIGN_CLAMP' | 'WEIGHT_RANGE'` [VERIFIED: src/main/frontend/src/types/index.ts:56]
- Drift guard: `WRITE_KEYWORD = Pattern.compile("(?i)\\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\\b")`, `BLEND_START = Pattern.compile("(?i)\\bwith\\s+learned\\s+as\\b")`, `LEARNED_SECTION = InterestScoreQueries.LEARNED_CTE + " SELECT 'learned' AS section"`, expected labels `containsExactly("unread", "unread", "unread", "window", "learned")`, 5 blend statements, 4 badge copies [VERIFIED: InterestCalibrationReplaySqlTest.java:40-54, 265-281]
- Driver: `LEARN_RATE="${LEARN_RATE:-2}"`, `LEARNED_CAP="${LEARNED_CAP:-20}"`, decimal regex `^[0-9]+(\.[0-9]+)?$`, integer regex `^[0-9]+$`, error text `invalid candidate: $value`, psql line `-v profilePoints="$pp" -v learnRate="$LEARN_RATE" -v learnedCap="$LEARNED_CAP"` [VERIFIED: scripts/interest-calibration-replay.sh:21-41, 64-67]

### The tested `LEARNED_CTE` (byte-identical to the SQL proven in scratch)
The proposed names `engaged`, `eng_learned`, `strength`, `eng_sum`, `eng_raw`, `eng`, `w_thumbs`, `:engagementOpenWeight`, `:engagementSaveWeight` and `:engagementCap` are Claude's discretion. They were checked against the write-keyword regex. [VERIFIED: compiled with `java`, output `cmp`-identical to the SQL run on Postgres 18.6]
```java
static final String LEARNED_CTE = "WITH learned AS (SELECT ts.topic_id, "
        + "SUM(f.vote * GREATEST(0, (ts.noul - 0.5) * 2)) AS vote_sum "
        + "FROM article_feedback f "
        + "JOIN article_score fs ON fs.article_id = f.article_id AND fs.status = 'SCORED' "
        + "JOIN article_topic_score ts ON ts.article_id = f.article_id "
        + "WHERE NOT f.topics_narrowed OR EXISTS (SELECT 1 FROM article_feedback_topic ft "
        + "WHERE ft.article_id = f.article_id AND ft.topic_id = ts.topic_id) "
        + "GROUP BY ts.topic_id), "
        + "engaged AS (SELECT g.article_id, "
        + "MAX(CASE WHEN g.kind = 'OPEN_ORIGINAL' THEN CAST(:engagementOpenWeight AS float8) "
        + "ELSE CAST(:engagementSaveWeight AS float8) END) AS strength "
        + "FROM article_engagement g "
        + "WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id) "
        + "GROUP BY g.article_id), "
        + "eng_learned AS (SELECT ts.topic_id, SUM(g.strength * GREATEST(0, (ts.noul - 0.5) * 2)) AS eng_sum "
        + "FROM engaged g "
        + "JOIN article_score gs ON gs.article_id = g.article_id AND gs.status = 'SCORED' "
        + "JOIN article_topic_score ts ON ts.article_id = g.article_id "
        + "GROUP BY ts.topic_id), "
        + "eff AS (SELECT t.id, t.name, t.weight AS base, "
        + "CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0) AS learned_raw, "
        + "LEAST(CAST(:learnedCap AS float8), GREATEST(-CAST(:learnedCap AS float8), "
        + "CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0))) AS learned, "
        + "CASE WHEN t.weight < 0 THEN 0 ELSE CAST(:learnRate AS float8) * COALESCE(n.eng_sum, 0) END AS eng_raw, "
        + "CASE WHEN t.weight < 0 THEN 0 ELSE GREATEST(0, LEAST(CAST(:engagementCap AS float8), "
        + "CAST(:learnRate AS float8) * COALESCE(n.eng_sum, 0))) END AS eng "
        + "FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id "
        + "LEFT JOIN eng_learned n ON n.topic_id = t.id), "
        + "eff2 AS (SELECT e.*, CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned)) "
        + "WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned)) "
        + "ELSE GREATEST(-50, LEAST(50, e.learned)) END AS w_thumbs, "
        + "CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned + e.eng)) "
        + "WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned + e.eng)) "
        + "ELSE GREATEST(-50, LEAST(50, e.learned + e.eng)) END AS w "
        + "FROM eff e)";
```
Notes:
- The `learned` CTE text is unchanged from v0.2.1.
- The `w_thumbs` CASE is byte-identical to v0.2.1's `w` CASE.
- `x + 0.0 == x` in IEEE, so cap 0 or no rows gives bit-identical `w` (verified with `float8send`).
- `'OPEN_ORIGINAL'` → open weight, and every other kind → save weight. That is safe because V7's CHECK allows exactly four kinds.

### `contrib` and `TOPIC_WEIGHTS_SELECT` additions
```java
// blendCte: append e.w_thumbs to the contrib select list (after learned_applied)
+ "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, e.base, e.w - e.base AS learned_applied, e.w_thumbs, "
+ "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "

// TOPIC_WEIGHTS_SELECT: append engagement raw/capped and the thumbs-only effective weight (6 decimals)
+ "ROUND(e.w::numeric, 6) AS w, ROUND(e.eng_raw::numeric, 6) AS eng_raw, ROUND(e.eng::numeric, 6) AS eng, "
+ "ROUND(e.w_thumbs::numeric, 6) AS w_thumbs FROM eff2 e";

// learnedSql: the single binding point (names == psql -v names)
return jdbc.sql(sql)
        .param("learnRate", blend.getLearnRate())
        .param("learnedCap", blend.getLearnedCap())
        .param("engagementOpenWeight", blend.getEngagement().getOpenWeight())
        .param("engagementSaveWeight", blend.getEngagement().getSaveWeight())
        .param("engagementCap", blend.getEngagement().getCap());
```

### Self-validating properties (the mechanism was verified in a probe; names are discretion)
```java
@Data
@ConfigurationProperties(prefix = "myfeeder")
public class MyfeederProperties implements Validator {
    // ... existing fields ...

    /** Fixed text: never echoes the bound values. */
    static final String ENGAGEMENT_INVALID = "myfeeder.interest.blend.engagement must be cap 0 (disabled), "
            + "or 0 <= open-weight < save-weight < 1 and 0 < cap < learned-cap";

    @Override
    public boolean supports(Class<?> type) {
        return MyfeederProperties.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Interest.Blend blend = ((MyfeederProperties) target).getInterest().getBlend();
        if (!blend.getEngagement().isValid(blend.getLearnedCap())) {
            errors.reject("engagement", ENGAGEMENT_INVALID);
        }
    }
    // Blend gains: private Engagement engagement = new Engagement();
    // Engagement { double openWeight = 0.25; double saveWeight = 0.5; double cap = 8;
    //   boolean isValid(int learnedCap) { return cap == 0 || (0 <= openWeight && openWeight < saveWeight
    //       && saveWeight < 1 && 0 < cap && cap < learnedCap); } }   // NaN and negative values fail naturally
}
```
Probe output against Boot 4.0.8 [VERIFIED: throwaway `ApplicationContextRunner` probe, this session]:
```
myfeeder.interest.blend.engagement.cap=8 => STARTED
...cap=0,...open-weight=0,...save-weight=0 => STARTED
...cap=0,...open-weight=-5 => STARTED
...cap=20 => FAILED BindValidationException: Binding validation errors on myfeeder - Error in object 'myfeeder': ... default message [myfeeder.interest.blend.engagement: need cap 0, or ...]
...save-weight=0.25 => FAILED BindValidationException ...
...save-weight=1 => FAILED BindValidationException ...
...open-weight=0,...save-weight=0 => FAILED BindValidationException ...
...cap=-1 => FAILED BindValidationException ...
```

### Validation test (no Docker)
```java
// Source: pattern of TypeSafeConfigTest (ApplicationContextRunner)
private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class);   // @EnableConfigurationProperties(MyfeederProperties.class)

@Test void rejectsCapAtOrAboveTheThumbsCap() {
    runner.withPropertyValues("myfeeder.interest.blend.engagement.cap=20")
          .run(ctx -> assertThat(ctx).hasFailed()
                  .getFailure().hasStackTraceContaining(MyfeederProperties.ENGAGEMENT_INVALID));
}
@Test void capZeroIsValidWhateverTheWeights() {
    runner.withPropertyValues("myfeeder.interest.blend.engagement.cap=0",
                    "myfeeder.interest.blend.engagement.open-weight=-5")
          .run(ctx -> assertThat(ctx).hasNotFailed());
}
```

### Yaml (main and test are identical literals; the overlay is unchanged)
```yaml
    blend:
      profile-points: 100
      learn-rate: 2
      learned-cap: 20
      # Engagement learning (LRN-05, D-01): cap 0 disables; else 0 <= open < save < 1 and 0 < cap < learned-cap. Phase 12 calibrates.
      engagement:
        open-weight: 0.25
        save-weight: 0.5
        cap: 8
```

### Replay driver additions (same validate-before-connect pattern)
```bash
ENGAGEMENT_OPEN_WEIGHT="${ENGAGEMENT_OPEN_WEIGHT:-0.25}"
ENGAGEMENT_SAVE_WEIGHT="${ENGAGEMENT_SAVE_WEIGHT:-0.5}"
ENGAGEMENT_CAP="${ENGAGEMENT_CAP:-8}"
for value in "$LEARN_RATE" "$ENGAGEMENT_OPEN_WEIGHT" "$ENGAGEMENT_SAVE_WEIGHT" "$ENGAGEMENT_CAP"; do
  if [[ ! "$value" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then echo "invalid candidate: $value" >&2; exit 2; fi
done
# ... psql ... -v learnedCap="$LEARNED_CAP" \
#   -v engagementOpenWeight="$ENGAGEMENT_OPEN_WEIGHT" -v engagementSaveWeight="$ENGAGEMENT_SAVE_WEIGHT" \
#   -v engagementCap="$ENGAGEMENT_CAP" \
```
The prototype replay (the 5 lines regenerated, the learned section extended with `round(e.eng_raw::numeric, 3) AS eng_raw, round(e.eng::numeric, 3) AS eng, :engagementCap > 0 AND e.eng_raw >= :engagementCap AS eng_at_cap`) ran read-only (`PGOPTIONS='-c default_transaction_read_only=on'`) with every section producing rows. The write-keyword count was 0. [VERIFIED: scratch]

A psql literal cast to float8 equals the Java double bound by JdbcClient. The bits for 0.1, 0.25 and 0.3 matched: `3fb999999999999a`, `3fd0000000000000`, `3fd3333333333333` on both sides [VERIFIED: `float8send` and `Double.doubleToRawLongBits`, this session]. So identical text plus identical values reproduces the app exactly.

### New drift check: the driver passes every blend parameter
```java
// Named parameters of the app blend; (?<![:\w]) skips ::casts. Found: engagementCap, engagementOpenWeight,
// engagementSaveWeight, learnedCap, learnRate, profilePoints  [VERIFIED: regex run on the proposed blend text]
private static final Pattern BIND = Pattern.compile("(?<![:\\w]):([A-Za-z]\\w*)");
@Test void driverPassesEveryBlendParameter() throws IOException {
    String driver = Files.readString(DRIVER);
    BIND.matcher(InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE)).results()
        .map(m -> m.group(1)).distinct()
        .forEach(name -> assertThat(driver).as(name).contains("-v " + name + "="));
}
```

### Grid test sketch (dyadic oracle)
```java
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestLearnedGridTest {
    @Autowired JdbcClient jdbcClient; @Autowired JdbcTemplate jdbc;
    InterestScoreQueries grid() {                       // learnRate 32 => noul 0.5 + k/64 gives exact integers
        MyfeederProperties p = new MyfeederProperties();
        p.getInterest().getBlend().setLearnRate(32);
        p.getInterest().getBlend().setLearnedCap(20);
        p.getInterest().getBlend().getEngagement().setCap(8);   // open 0.25, save 0.5 defaults
        return new InterestScoreQueries(jdbcClient, p);
    }
    // per cell (base, t, e): one topic; a voted article (noul 0.5+|t|/64, vote sign(t)) when t != 0;
    // an engaged article with all four kinds (noul 0.5+e/32) when e > 0. Seed set-based with generate_series/unnest.
    // Assert over allTopicWeights():
    //   BigDecimal base+thumbsApplied+engApplied compareTo effective == 0   (BigDecimal.valueOf(double))
    //   engApplied >= 0; base<0 => engApplied == 0 && engRaw == 0
    //   effective == oracle clamp(base + clamp(t,±20) + (base<0 ? 0 : min(e, 8)))
    //   w_thumbs  == oracle clamp(base + clamp(t,±20))
    // Spot-check breakdownInputs(article) topics carry the same split for a sample of cells.
}
```
Worked cells from the scratch run (base, thumbs raw, eng raw → w_thumbs, w, thumbs_w, eng_w):
- (10, −20, 8) → 0, 0, −10, 0 (the CONTEXT example)
- (0, −5, 8) → −5, 3, −5, 8
- (42, 5, 16) → 47, 50, 5, 3
- (−5, 10, 16) → 0, 0, 5, 0
- (45, 5, 4) → 50, 50, 5, 0

[VERIFIED: scratch]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| v0.2.1 `LEARNED_CTE`: thumbs only, `w = clamp(base + learned)` | `w_thumbs` (same expression) + `w = clamp(base + learned + eng)` | Phase 9 | At cap 0 or with no rows, scores are bit-identical |
| `learned_w = ROUND((w − base)::numeric, 6)` | `ROUND(w,6) − ROUND(base,6)` plus `thumbs_w` / `eng_w` by subtraction | Phase 9 | Exact numeric identities across all parts |
| Phase 8 guard: ranking must not read V7 | Ranking reads `article_engagement`; still never `topic_suggestion_dismissal` | Phase 9 | Invert 2 tests |

**Deprecated/outdated:**
- `LearnedLimit.of(TopicWeight, double cap)` is replaced by a 3-argument form. It is the only production caller pair, in `ArticleFeedbackService`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `TopicLearned.learned` / `TopicEffect.learned` should become "thumbs capped + engagement capped (before the clamp)" rather than the literal `w − base` | Pitfall 5, Open Q1 | Choosing `w − base` changes the displayed value for clamped topics even at zero engagement (for example −20 → −5) and the LEARNED_CAP note number. Needs user confirmation. |
| A2 | `InterestBreakdown.Row` gets the split fields in Phase 9 (D-10 names `TopicContribution` but not `Row`) | Architecture, Open Q2 | If deferred, Phase 10 must also touch the backend DTO |
| A3 | `eng_raw` is zeroed for negative-base topics (not only `eng`) | Code Examples | If not zeroed, `LearnedLimit` could report `ENGAGEMENT_CAP` on a topic engagement cannot move |
| A4 | Latency guard `extended ≤ 10 × baseline + 250 ms` replaces the "3×" example | Pitfall 2, Validation | Too loose misses moderate regressions (which are still recorded in VERIFICATION); 3× fails on topic weights |
| A5 | Java defaults for the engagement fields equal D-01 (0.25 / 0.5 / 8), like the other `Blend` defaults | Code Examples | Unit tests that build `new MyfeederProperties()` would see engagement enabled |
| A6 | The engagement cap is a `double` (learned-cap stays `int`) | Code Examples | An int cap would block Phase 12 from tuning to 7.5; low risk either way |
| A7 | Replay env var names `ENGAGEMENT_OPEN_WEIGHT` / `ENGAGEMENT_SAVE_WEIGHT` / `ENGAGEMENT_CAP` with D-01 defaults | Code Examples | Cosmetic; Phase 12 may move them into the candidate string |
| A8 | The prod data volume is far below the 20k-row seed (7 topics and about 200 SCORED at 07-CALIBRATION; weeks of engagement since v0.3.0) | Latency | If prod is much larger, re-measure in Phase 12 with the replay (read-only) |

## Open Questions (RESOLVED)

All resolved on 2026-09-30. The user confirmed questions 1–3 and 5 during `/gsd-plan-phase 9` (see 09-CONTEXT.md D-15..D-17 and Deferred Ideas), and question 4 belongs to Phase 12.

1. **RESOLVED → D-15.** **What do the existing `learned` fields of `TopicLearned` and `TopicEffect` mean after Phase 9?**
   - What we know: today they hold the capped thumbs value before the clamp, not `w − base` (Pitfall 5). `Row.learnedWeight` / `TopicContribution.learnedWeight` already hold `w − base`.
   - What's unclear: D-10's parenthetical "(w − base)" applies literally to the contribution fields, but it would change the other two fields' values even with no engagement.
   - Recommendation: keep "capped, before the clamp" semantics and make it combined: `learned = thumbsLearned + engagementLearned`. Append `thumbsLearned` and `engagementLearned` (both capped), plus `thumbsApplied` / `engagementApplied` if Phase 10 wants them. Confirm with the user during planning. The planner can treat D-10's "(w − base)" as describing `learnedWeight` only.
2. **RESOLVED → D-16.** **Should `InterestBreakdown.Row` get `thumbsWeight` / `engagementWeight` now?** Recommendation: yes. They are appended with `NON_NULL`, so PROFILE rows omit them. That keeps D-10's "Phase 10 only renders".
3. **RESOLVED → D-17.** **The latency guard formula.** D-13 gave 3× only "for example", and the measurements rule it out for topic weights. Recommendation: `≤ 10 × baseline + 250 ms`, with the numbers recorded in VERIFICATION.
4. **RESOLVED → deferred to Phase 12 (read-only replay).** **Prod volumes.** These were not measured (no prod access, by constraint). Phase 12's read-only replay can report `count(*)` of engagement rows and SCORED-engaged articles. The seeded budget test is deliberately larger than prod today.
5. **RESOLVED → deferred (09-CONTEXT.md Deferred Ideas).** **Optional `contrib AS NOT MATERIALIZED`.** Pre-existing, and not needed for the budget. Planner/user decision; default is to not do it in Phase 9 and to record it as a deferred item.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker (Desktop) | Testcontainers tests | ✓ | client 29.8.1; socket `~/.docker/run/docker.sock` | — |
| Testcontainers Postgres | Grid/latency/integration tests | ✓ | `postgres:latest` = 18.6 (image cached) | — |
| Gradle + JDK 25 toolchain | Backend build/tests | ✓ | Proven: 3 test classes ran green this session (64 tests) | — |
| Node / npm | Frontend tsc + vitest | ✓ | node v26.10.0, npm 11.19.1; `node_modules/.bin/{tsc,vitest}` present | — |
| psql (local) | Optional manual replay dry-run | ✓ | 18.6 (Homebrew) | Not needed by tests (the driver tests use a dead port) |
| bash for the driver tests | `InterestCalibrationReplaySqlTest.runDriver` | ✓ | GNU bash 5.3 on PATH (3.2 at /bin/bash also works for `=~`) | — |
| Prod Postgres | Not used in Phase 9 | n/a | — | Out of scope by constraint |

**Note:** `build.gradle.kts` defaults `DOCKER_HOST` to `unix:///Users/scottb/.rd/docker.sock`, which does not exist. Testcontainers still connects, because `~/.testcontainers.properties` pins `docker.client.strategy=UnixSocketClientProviderStrategy` (the `/var/run/docker.sock` symlink to Docker Desktop). The tests ran green without any `DOCKER_HOST` prefix, as the Phase 8 plans say.

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Testcontainers (backend); Vitest + React Testing Library (frontend) |
| Config file | `build.gradle.kts` (`tasks.withType<Test> { useJUnitPlatform() }`); `src/main/frontend/vite.config.ts` |
| Quick run command | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest" --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest"` |
| Full suite command | `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npx vitest run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LRN-01 | Save > open. Open + star + board + Raindrop on one article counts once as a save. Only SCORED counts (no row / FAILED / SKIPPED add nothing). Zero base qualifies. | integration (real PG) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` (new engagement methods) | ✅ file exists; ❌ new methods (Wave 0) |
| LRN-01 | End to end: open/star/board raises the score and breakdown, `interest_topic.weight` is unchanged, no Jev call | integration (`@SpringBootTest`) | `… --tests "org.bartram.myfeeder.controller.EngagementApiIntegrationTest"` (invert `engagementLeavesTheRankingUnchanged`) | ✅ (rewrite 1 method) |
| LRN-02 | Any vote (up, down, narrowed with picks, narrowed with none) removes the article's engagement on every topic; deleting the vote restores `topicWeights` exactly | integration (real PG) | `… --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` | ❌ Wave 0 |
| LRN-02 / D-12 | The vote effect's `before` includes the engagement the vote replaces | integration | `… --tests "org.bartram.myfeeder.controller.FeedbackApiIntegrationTest"` (new method) | ✅ file; ❌ method |
| LRN-03 | Negative base: `eng_raw = eng = 0`, effective unchanged | integration + grid | `… --tests "org.bartram.myfeeder.repository.InterestLearnedGridTest"` | ❌ Wave 0 |
| LRN-04 | Exact split `base + thumbs + eng = effective` (BigDecimal), `eng ≥ 0`, sign clamp, ±50, both caps, additive on top of thumbs; breakdown split equals the topic-weights split | property grid (real PG) | `… --tests "org.bartram.myfeeder.repository.InterestLearnedGridTest"` | ❌ Wave 0 |
| LRN-04 / D-11 | `LearnedLimit` precedence LEARNED_CAP → ENGAGEMENT_CAP (only cap > 0) → SIGN_CLAMP/WEIGHT_RANGE on base + thumbs + eng → NONE | unit | `… --tests "org.bartram.myfeeder.service.ArticleFeedbackServiceTest"` | ✅ file; ❌ methods |
| LRN-05 | Validation: cap 0 always starts (including negative weights); rejects save ≤ open, save ≥ 1, open < 0, cap < 0, cap ≥ learned-cap; fixed text | unit (`ApplicationContextRunner`, no Docker) | `… --tests "org.bartram.myfeeder.config.MyfeederPropertiesValidationTest"` | ❌ Wave 0 |
| LRN-05 / D-05 | cap 0 with engagement rows present: every `raw_n` and the Priority order equal a frozen v0.2.1 blend copy; no rows at cap 8 is also equal; cap 0 with negative weights is equal (zero floor) | integration (real PG) | `… --tests "org.bartram.myfeeder.repository.InterestScoreQueriesZeroEngagementTest"` | ❌ Wave 0 |
| LRN-05 | Main + test yaml mirrored; overlay unchanged | unit (no Docker) | `… --tests "org.bartram.myfeeder.DevProfileConfigTest"` | ✅ (passes once yaml is mirrored) |
| CAL-01 | Replay is verbatim (5 lines, 4 badges); read-only; the driver passes every blend parameter; the driver rejects non-numeric `ENGAGEMENT_*` | unit (no Docker) | `… --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest"` | ✅ file; ❌ 2 new methods |
| CAL-01 | The ranking SQL and replay read `article_engagement` but never `topic_suggestion_dismissal` | integration | `… --tests "org.bartram.myfeeder.repository.V7EngagementMigrationTest"` | ✅ (invert 1 method) |
| SC-5 (latency) | 20k engagement rows: Priority first page, breakdown and topic weights each ≤ 10 × baseline + 250 ms; numbers and EXPLAIN go to stdout (captured in `build/test-results/test/TEST-*.xml` `<system-out>`) | integration (real PG) | `… --tests "org.bartram.myfeeder.repository.InterestScoreQueriesLatencyTest"` | ❌ Wave 0 |
| D-11 (frontend) | `LearnedLimit` union includes `ENGAGEMENT_CAP`; `effectNote` / `LearnedLine` fall through to default | unit (vitest) | `cd src/main/frontend && npx tsc -b && npx vitest run src/utils/feedback.test.ts src/components/TopicRow.test.tsx` | ✅ files; ❌ cases |

### Latency test recipe (D-13)
- Seed set-based inside the `@DataJdbcTest` transaction with `generate_series`, following the prod-like shape measured here:
  - 10 topics with mixed bases
  - 30k articles, 70% read
  - 5k SCORED × 10 nouls (50k `article_topic_score`), 500 FAILED
  - 130 votes, 10% narrowed
  - about 20k engagement rows over about 13k articles: opens on 2/3, STAR on 1/4, BOARD on 1/5, RAINDROP on 1/7, scored and unscored mixed
- Then run `ANALYZE` on the seeded tables. ANALYZE works inside a transaction and sees the transaction's own rows [VERIFIED: scratch, `reltuples = 5000` after an in-transaction ANALYZE].
- Measure the baseline before inserting engagement, then insert engagement, ANALYZE again and re-measure.
- Measured on Docker Desktop, Postgres 18.6, Apple silicon (the EXPLAIN ANALYZE execution-time median, SQL only) [VERIFIED: scratch]:

| Query | v0.2.1 SQL | Phase 9 SQL, 0 eng rows | Phase 9 SQL, 20k eng rows |
|-------|-----------|--------------------------|---------------------------|
| Priority first page (50), prod-like | 33 ms | 35 ms | 65 ms |
| Breakdown topics (1 article), prod-like | 41 ms | 54 ms | 86 ms |
| Topic weights (all), prod-like | 6.8 ms | 7.5 ms | 37 ms |
| Priority first page, heavy (12k SCORED × 25 topics) | 128 ms | 140 ms | 216 ms |
| Breakdown topics, heavy | 243 ms | 489 ms | 601 ms |
| Topic weights, heavy | 37 ms | 38 ms | 114 ms |

- EXPLAIN: `engaged` is a Hash Anti Join on `article_feedback` (20k rows to ~13k), then HashAggregate, then Hash Join to SCORED `article_score` and a seq scan of `article_topic_score`. No index would help a full aggregate, so no V8 migration is needed.
- For the EXPLAIN text in the test, run `EXPLAIN (ANALYZE, BUFFERS) ` + `InterestScoreQueries.LEARNED_CTE + " SELECT * FROM eff2"` and `+ blendCte(UNREAD_SCOPE) + " SELECT count(*) FROM blended b"`. Both are package-visible to a test in `org.bartram.myfeeder.repository`. Bind the same parameters that `learnedSql` binds.

### Sampling Rate
- **Per task commit:** the quick run command plus the test class named in the task.
- **Per wave merge:** `./gradlew test -x npmBuild -x npmInstall` (the full backend suite, Docker on) and `cd src/main/frontend && npx tsc -b && npx vitest run`.
- **Phase gate:** the full suite green before `/gsd-verify-work`, with the latency numbers and EXPLAIN pasted into VERIFICATION.

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java`: LRN-03/LRN-04 exact split grid (dyadic oracle)
- [ ] New engagement methods in `InterestScoreQueriesTest` (or a sibling `InterestScoreQueriesEngagementTest`): LRN-01/LRN-02 behaviors
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java`: D-05 against a frozen v0.2.1 blend string (copy the current line 30 of `scripts/interest-calibration-replay.sql` **before** editing it)
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java`: D-13
- [ ] `src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java`: LRN-05
- [ ] `InterestCalibrationReplaySqlTest`: `driverPassesEveryBlendParameter`, `driverRejectsANonNumericEngagementValue`
- [ ] Invert `V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` and `EngagementApiIntegrationTest.engagementLeavesTheRankingUnchanged`
- [ ] `ArticleFeedbackServiceTest`: `LearnedLimit` ENGAGEMENT_CAP precedence cases; update the record constructors
- [ ] Frontend: `feedback.test.ts` ENGAGEMENT_CAP fall-through case (pins Pitfall 6 for Phase 10)
- Framework install: none needed.

**Ordering constraint (CAL-01):** the `LEARNED_CTE` / `blendCte` edit, the replay regeneration and the V7 guard inversion must be in the **same plan and commit**. Otherwise `InterestCalibrationReplaySqlTest` and `V7EngagementMigrationTest` are red between commits.

## Security Domain

`security_enforcement` is enabled (`.planning/config.json:48`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user homelab app; unchanged |
| V3 Session Management | no | Unchanged |
| V4 Access Control | no | No new endpoint |
| V5 Input Validation | yes | Config: self-validating `MyfeederProperties` (fixed text). Replay driver: numeric regex on every `-v` value before connecting. SQL: every constant is a bound parameter (`JdbcClient` named params) and scope fragments stay class constants. |
| V6 Cryptography | no | None |
| V7 Error Handling / Logging | yes | The validation message is fixed text; replay output uses topic ids only (no names or descriptions) |
| V8 Data Protection | yes | The replay stays read-only (`default_transaction_read_only=on`) and lives outside the repo (`umask 077`, `$HOME/.cache/...`) |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection through psql `-v` interpolation (`ENGAGEMENT_*` env) | Tampering | `^[0-9]+(\.[0-9]+)?$` validation before any connection; test with a non-numeric value, run against a dead port |
| A write statement slipped into the replay | Tampering | Read-only session and the `WRITE_KEYWORD` guard (no new aliases match) |
| Misconfiguration that inflates ranking (cap ≥ thumbs cap, save ≥ 1) | Tampering / integrity | Startup refusal (D-02), yaml-only tuning (07 D-14), zero floor in SQL |
| Slow learned CTE degrading every list/Priority read | Denial of service | Latency guard + EXPLAIN record; per-article collapse before the topic join |
| Engagement triggering billed Jev calls | Elevation / cost | Derived at query time only; the integration test asserts `never().judge(...)` |

## Sources

### Primary (HIGH confidence)
- In-repo source read this session: `InterestScoreQueries.java`, `MyfeederProperties.java`, `LearnedLimit.java`, `ArticleFeedbackService.java`, `FeedbackResult.java`, `TopicLearned.java`, `InterestBreakdown.java`, `ScoreBreakdowns.java`, `ArticleEngagementStore.java`, `EngagementKind.java`, `V6__interest_scoring.sql`, `V7__engagement.sql`, `scripts/interest-calibration-replay.{sh,sql}`, `InterestCalibrationReplaySqlTest.java`, `DevProfileConfigTest.java`, `V7EngagementMigrationTest.java`, `InterestScoreQueriesTest.java`, `EngagementApiIntegrationTest.java`, both `application.yaml`s and `application-dev.yaml`, frontend `types/index.ts`, `utils/feedback.ts`, `components/TopicRow.tsx`, `components/WhyBreakdown.tsx`
- Scratch Postgres 18.6 experiments: the grid proof, bit-identity at cap 0, the zero-floor leak, latency plus EXPLAIN ANALYZE, the replay prototype, ANALYZE in a transaction
- Spring Boot v4.0.8 source (GitHub raw): `ConfigurationPropertiesBinder.java` (self-validator), `ValidationBindHandler.java`, `BindValidationException.java`, `BindValidationFailureAnalyzer.java`
- Throwaway `ApplicationContextRunner` probe on the project's test runtime classpath (Boot 4.0.8 jars)

### Secondary (MEDIUM confidence)
- Context7 `/spring-projects/spring-boot/v4.0.3`: "@ConfigurationProperties Validation" (the `configurationPropertiesValidator` bean, `@Validated`)
- Context7 `/websites/postgresql_18`: `sql-select.html` WITH clause (folding when referenced once; `NOT MATERIALIZED`)
- `.planning/research/{SUMMARY,PITFALLS}.md` (milestone research; formulas re-verified here)

### Tertiary (LOW confidence)
- None relied upon.

## Metadata

**Confidence breakdown:**
- SQL design and exact split: HIGH. Grid-proven on real Postgres, with bit-identity at zero and a byte-identical Java string.
- Startup validation: HIGH. Source-verified and executed against 4.0.8.
- Replay regeneration: HIGH. The prototype ran read-only, and the parameter-extraction regex was tested.
- Latency budget: MEDIUM. Measured on synthetic data on a laptop; prod volume is unknown (A8).
- DTO semantics: MEDIUM. Open Question 1 needs user confirmation.

**Research date:** 2026-09-30
**Valid until:** 2026-10-30 (stable stack; revisit if Spring Boot moves past 4.0.x or the `postgres:latest` major version changes)
