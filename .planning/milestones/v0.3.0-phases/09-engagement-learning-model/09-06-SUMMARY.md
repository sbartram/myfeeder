---
phase: 09-engagement-learning-model
plan: 06
subsystem: database
tags: [postgres, latency, explain-analyze, testcontainers, documentation]

# Dependency graph
requires:
  - phase: 09-engagement-learning-model
    provides: "09-01: extended LEARNED_CTE, engagement constants and validation, regenerated replay; 09-03: split fields; 09-05: ENGAGEMENT_CAP and the TopicLearned/TopicEffect split"
provides:
  - "InterestScoreQueriesLatencyTest: the D-13/D-17 latency record and guard (median of 5 warm runs, extended <= 10 x baseline + 250 ms) with EXPLAIN (ANALYZE, BUFFERS) printed for VERIFICATION"
  - "CLAUDE.md 'Engagement learning (v0.3.0, Phase 9)' bullet and updated Engagement capture, Schema, Thumbs feedback, tuning and Package Structure lines"
  - "ArticleEngagementStore javadoc that says LEARNED_CTE reads the table"
affects: [10, 12]

# Actuals (#2632)
actuals:
  tokens: 13000
  tasks: 2
  commits: 2
plan_head_before: 81f8d4de7df009b35e0aab8fe095d17acf7d5414
plan_head_after: e909c2e38d58ac83236a97881cfd9b2e562b6db8

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Latency guard as record plus generous bound: median of 5 warm runs after 3 warm-ups, extended <= 10 x baseline + 250 ms, numbers and EXPLAIN printed with LATENCY/EXPLAIN prefixes into the JUnit XML system-out"
    - "Set-based seeding with generate_series and rn modulo rules over row_number() by id, so a 30k-article fixture seeds in seconds and is deterministic"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java
  modified:
    - CLAUDE.md
    - src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java

key-decisions:
  - "The blend EXPLAIN selects count(*), sum(b.raw_n) instead of count(*) alone: with count(*) only, Postgres removed the learned/eng_learned joins as unused, so the plan did not show the engagement cost"
  - "Engagement candidates are rn % 17 < 9 (17 is coprime with the kind moduli 3, 4, 5, 7), which gives 20,006 rows over 13,160 articles with the recipe's 2/3, 1/4, 1/5, 1/7 kind mix"
  - "No index, migration or query hint: the D-17 bound passed with a wide margin (D-13)"

patterns-established:
  - "LATENCY and EXPLAIN lines are read from build/test-results/test/TEST-org.bartram.myfeeder.repository.InterestScoreQueriesLatencyTest.xml"

requirements-completed: [LRN-01, LRN-05, CAL-01]

coverage:
  - id: D1
    description: "With 20,006 engagement rows over 13,160 articles (2,196 SCORED, 51 voted) on a 30k-article prod-like fixture, the Priority first page, one article's breakdown and the topic weights each stay within 10 x baseline + 250 ms, and the numbers plus EXPLAIN (ANALYZE, BUFFERS) are recorded"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java#extendedLearnedCteStaysWithinTheLatencyBudget"
        status: pass
    human_judgment: false
  - id: D2
    description: "CLAUDE.md documents the extended learned model, the constants and the replay variables; the stale Phase 8 ranking claim, the old guard name and the stale store javadoc are gone"
    requirement: LRN-05
    verification:
      - kind: other
        ref: "grep chain from 09-06-PLAN Task 2 verify #2 (rankingSqlReadsEngagementButNotDismissals, engagementLearned, ENGAGEMENT_CAP, ENGAGEMENT_OPEN_WEIGHT present; stale strings absent)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Phase gate in a clean shell: full backend suite, frontend type-check and vitest pass"
    requirement: CAL-01
    verification:
      - kind: integration
        ref: "./gradlew test -x npmBuild -x npmInstall (648 tests, 0 failures, 2 existing live-Jev skips)"
        status: pass
      - kind: unit
        ref: "cd src/main/frontend && npx tsc -b && npx vitest run (32 files, 348 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-14 and deferred-item checks: no tag contains HEAD; ArticleScoreStore, migrations, Helm and deploy.sh are unchanged against main; no NOT MATERIALIZED hint in the CTE or the replay"
    verification:
      - kind: other
        ref: "09-06-PLAN Task 2 verify #3 (git tag --contains HEAD empty; git diff --quiet main -- ...)"
        status: pass
    human_judgment: false

# Metrics
duration: 7min
completed: 2026-09-30
status: complete
---

# Phase 9 Plan 06: Latency Record and Engagement Learning Docs Summary

**On a 30k-article prod-like fixture, 20,006 engagement rows raise the Priority first page from 22 ms to 35 ms, the breakdown from 43 ms to 68 ms and the topic weights from 5 ms to 17 ms. Each stays far inside the D-17 bound of 10 x baseline + 250 ms, and EXPLAIN (ANALYZE) shows hash joins on existing keys, so no index is needed. CLAUDE.md now documents the engagement learning model, and the stale Phase 8 statements are gone.**

## Performance

- **Duration:** about 7 min
- **Started:** 2026-09-30T16:45:29Z
- **Completed:** 2026-09-30T16:52Z
- **Tasks:** 2
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `InterestScoreQueriesLatencyTest.extendedLearnedCteStaysWithinTheLatencyBudget` seeds the research recipe with `generate_series`:
  - 10 topics with bases 40 to -30
  - 30,000 articles, 70% read
  - 5,000 SCORED with 50,000 nouls, and 500 FAILED
  - 130 votes, 13 of them narrowed
  - 20,006 engagement rows
  It times the three reads before and after engagement, asserts the D-17 bound for each, and prints the LATENCY and EXPLAIN record.
- Sanity checks in the test:
  - Every `engagementLearned` is 0 at the baseline, and at least one is above 0 once engagement is added, so the extended branch is really exercised.
  - The breakdown article is unread, SCORED, engaged and carries no vote.
- CLAUDE.md:
  - New "Engagement learning (v0.3.0, Phase 9)" bullet.
  - Engagement capture now names `rankingSqlReadsEngagementButNotDismissals`.
  - Schema, Thumbs feedback (the CTE chain plus `thumbsLearned`/`engagementLearned`), tuning (replay `ENGAGEMENT_*` variables) and Package Structure lines are updated.
- The `ArticleEngagementStore` javadoc now links `InterestScoreQueries#LEARNED_CTE` as the reader, and keeps the D-07 no-transaction sentence.

## Task Commits

1. **Task 1 (tracer): latency record and guard:** `eefd40d` (test)
   - Tracer gate: the run was interactive, end-of-phase, and the verify was automated only. The verify was re-run green on the committed code before expansion.
2. **Task 2: CLAUDE.md and store javadoc, full gate, D-14 checks:** `e909c2e` (docs)

**Plan metadata:** committed with this SUMMARY (docs)

## Latency record (for VERIFICATION)

Docker Desktop on Apple silicon (Testcontainers Postgres). Each value is the median of 5 warm runs after 3 warm-ups, wall time through `JdbcClient`, in ms. The bound is 10 x baseline + 250.

**Focused run** (`--tests InterestScoreQueriesLatencyTest`):

| Query | Baseline median (0 engagement rows) | Extended median (20,006 rows) | Bound | Ratio |
|-------|------:|------:|------:|------:|
| priority (`priorityFirstPage(50)`) | 22.0 | 35.4 | 470.0 | 1.6x |
| breakdown (`breakdownInputs(x)`) | 43.3 | 68.0 | 683.5 | 1.6x |
| topic-weights (`allTopicWeights()`) | 4.6 | 16.9 | 295.7 | 3.7x |

**Full-suite run** (the same test inside `./gradlew test`, under suite load): priority 31.0 → 43.4 (bound 560.0), breakdown 40.1 → 71.2 (bound 650.8), topic-weights 3.9 → 18.0 (bound 289.2).

Counts line: `LATENCY engagement-rows=20006 engaged-articles=13160 scored-engaged=2196 voted-engaged=51`

These agree with the research scratch measurements (35 → 65, 54 → 86, 7.5 → 37 ms). The engagement branch adds roughly 10 to 30 ms per learned-CTE evaluation. As research predicted, topic weights has the largest ratio (3.7x, which would fail a 3x guard) and the smallest absolute cost.

EXPLAIN excerpts, focused run, printed by the test with `EXPLAIN learned:` / `EXPLAIN blend:` prefixes (the full plans are in the test's JUnit XML):

```
EXPLAIN learned: Hash Left Join  (cost=4386.15..4388.07 rows=10 width=76) (actual time=31.825..31.837 rows=10.00 loops=1)
EXPLAIN learned:               ->  Subquery Scan on l  (...) (actual time=6.285..6.289 rows=10.00 loops=1)          [thumbs: learned]
EXPLAIN learned:         ->  Subquery Scan on n  (...) (actual time=25.518..25.522 rows=10.00 loops=1)                [engagement: eng_learned]
EXPLAIN learned:               ->  HashAggregate  (...) Group Key: ts_1.topic_id  Batches: 1  Memory Usage: 32kB
EXPLAIN learned:                           ->  Hash  (...) rows=13109.00                                              [engaged, one strength per article]
EXPLAIN learned:                                       ->  HashAggregate  Group Key: g_1.article_id  Memory Usage: 1049kB
EXPLAIN learned:                                             ->  Hash Anti Join  (...) rows=19935.00                  [vote override drops voted articles]
EXPLAIN learned:                                                   ->  Seq Scan on article_engagement g_1  (...) rows=20006.00
EXPLAIN learned: Planning Time: 0.533 ms
EXPLAIN learned: Execution Time: 31.896 ms
EXPLAIN blend: Aggregate  (cost=8015.34..8015.35 rows=1 width=40) (actual time=49.086..49.099 rows=1.00 loops=1)
EXPLAIN blend:   ->  GroupAggregate  (...) Group Key: s.article_id  rows=1500.00
EXPLAIN blend:         ->  Nested Loop Left Join  (...) (actual time=32.001..46.854 rows=15000.00 loops=1)
EXPLAIN blend:               ->  Merge Join  Merge Cond: (s.article_id = a.id)  rows=1500.00                        [unread SCORED]
EXPLAIN blend:               ->  Hash Left Join ... Hash Cond: (t.id = ts_2.topic_id)  loops=1500                    [eff2 joined per article; eng_learned hashed once]
EXPLAIN blend: Planning Time: 0.996 ms
EXPLAIN blend: Execution Time: 49.179 ms
```

The full-suite run's EXPLAIN execution times were: learned 34.9 ms and blend 60.2 ms.

Reading of the plans:
- Every join is a hash join on an existing primary key or a seq scan feeding a full aggregate. `engaged` is a Hash Anti Join against `article_feedback` (20,006 → 19,935 rows), then a HashAggregate to 13,109 articles, then a hash join to the 5,000 SCORED rows and the 50,000 nouls.
- The learned CTE's grouped outputs are 10-row hashes, and the blend reuses them for all 1,500 unread SCORED articles.
- No index would shortcut a full aggregate, so no V8 migration is needed. `src/main/resources/db/migration` is unchanged against main (D-13).

## Observations for VERIFICATION

- **Research Pitfall 9 (the `contrib` materialization), deferred.** In `breakdownInputs`' topics query, `contrib` is referenced twice, so Postgres materializes it for every article before filtering to one.
  - This run's breakdown cost: 43.3 → 68.0 ms focused and 40.1 → 71.2 ms in the full suite. That is +25 to +31 ms, and the bound was 683.5 / 650.8 ms.
  - Research measured `contrib AS NOT MATERIALIZED` cutting it to about 37 ms prod-like.
  - It was deferred by user decision on 2026-09-30 and nothing here changes it: `NOT MATERIALIZED` appears in neither `InterestScoreQueries.java` nor the replay SQL.
- **Research A8 (prod volume) was not measured.** The seed (20k rows, 2,196 SCORED engaged) is deliberately larger than prod is believed to be. Phase 12's read-only replay should report `count(*)` of engagement rows and of SCORED engaged articles, and re-measure if prod turns out larger.
- **Inputs for Phase 10:**
  - The ENGAGEMENT_CAP "+0.0" toast listing (research Pitfall 6). `formatVoteToast` lists any effect whose limit is not NONE. After D-11, a topic at the engagement cap reports ENGAGEMENT_CAP on every vote, even a vote that barely moved it. ENGAGEMENT_CAP also outranks SIGN_CLAMP/WEIGHT_RANGE.
  - `effectNote` and `TopicRow`'s `LearnedLine` fall through to their default wording for ENGAGEMENT_CAP.
  - The "Learned from votes" label now also covers engagement, because `learned` = `thumbsLearned` + `engagementLearned`.
- **Blend EXPLAIN shape.** With the plan's literal `SELECT count(*) FROM blended b`, Postgres removed the `learned` and `eng_learned` joins, because `count(*)` never reads `raw_n` (first run: 10.3 ms, with no engagement nodes). The test uses `count(*), sum(b.raw_n)` so that the recorded plan includes the engagement branch.
- **Dormant engagement.** Engaged-but-unscored articles stay ineligible for scoring. `ArticleScoreStore` is unchanged against main (ENG-F4 is a Phase 12 decision).

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java` (new, 224 lines): the fixture, baseline and extended medians, the D-17 assertions, and the LATENCY/EXPLAIN output.
- `CLAUDE.md`: the Engagement learning bullet; the Engagement capture, Schema, Thumbs feedback, Tier thresholds and tuning, and Package Structure (config) lines.
- `src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java`: the class javadoc.

## Decisions Made

- The blend EXPLAIN also aggregates `raw_n` so that its plan shows the engagement cost (see Observations).
- Engagement candidates are chosen with a modulus that is coprime with the kind moduli, so the kind mix stays independent and the counts land on the recipe: 20,006 rows over 13,160 articles.
- The test builds its own `MyfeederProperties` (learnRate 2, learned-cap 20, profile-points 100, 0.25 / 0.5 / 8) instead of changing the context bean. It keeps `@EnableConfigurationProperties(MyfeederProperties.class)` so that it shares the `@DataJdbcTest` context cache key with its sibling tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The blend EXPLAIN did not include the learned CTE**
- **Found during:** Task 1
- **Issue:** With the plan's literal `blendCte(UNREAD_SCOPE) + " SELECT count(*) FROM blended b"`, Postgres removed the unused LEFT JOINs to the unique-keyed `learned` and `eng_learned` aggregates. The recorded plan (10.3 ms) therefore showed none of the engagement cost it was meant to document.
- **Fix:** The test selects `count(*), sum(b.raw_n)`, which forces `raw_n` and so the whole learned model. The `EXPLAIN blend: ... Execution Time` verify still matches.
- **Files modified:** `InterestScoreQueriesLatencyTest.java`
- **Verification:** The blend plan now contains the `eng_learned` HashAggregate and the Hash Anti Join (49.2 ms).
- **Committed in:** `eefd40d`

### Other notes

- **Frontend dependencies.** The fresh worktree had no `src/main/frontend/node_modules`, so `./gradlew npmInstall` was run before `npx tsc -b`. It installs the committed lockfile: no new package.
- **Plan-commit ledger.** The ledger was not written into the git dir, as in earlier plans. The base `81f8d4d` is recorded as `plan_head_before`, and the 2 task commits were counted with `git rev-list --count 81f8d4d..HEAD`.

---

**Total deviations:** 1 auto-fixed (1 bug in the recorded evidence).
**Impact on plan:** the EXPLAIN record now shows what it is meant to show. No production code or SQL changed.

## Issues Encountered

None.

## Known Stubs

None.

## User Setup Required

None: no external service configuration required.

## Next Phase Readiness

- Phase 9 is complete: all six plans have SUMMARYs. The branch is ready for `/gsd-verify-work`, and after that the `--no-ff` merge to main.
- D-14: no tag contains HEAD, and nothing was released, built into an image, pushed or deployed. Prod stays on v0.3.0 until Phase 12.
- Phase 10 inputs are listed under Observations for VERIFICATION.

---
*Phase: 09-engagement-learning-model*
*Completed: 2026-09-30*

## Self-Check: PASSED

- Files exist: InterestScoreQueriesLatencyTest.java, CLAUDE.md, ArticleEngagementStore.java, 09-06-SUMMARY.md
- Commits exist: eefd40d, e909c2e
- Full backend suite green (648 tests, 0 failures, 2 existing live-Jev skips); frontend `tsc -b` clean and vitest 348/348; all Task 1 and Task 2 verify chains pass
