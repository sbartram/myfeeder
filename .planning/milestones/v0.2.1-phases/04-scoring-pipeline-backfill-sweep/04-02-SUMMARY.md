---
phase: 04-scoring-pipeline-backfill-sweep
plan: 02
subsystem: database
tags: [postgres, jdbcclient, spring-data-jdbc, testcontainers, scoring-queue, upsert]

requires:
  - phase: 03-interest-model-schema-rubric-editor
    provides: V6 interest schema (article_score, article_topic_score, interest_topic)
provides:
  - "MyfeederProperties.Interest (myfeeder.interest.*) with eligibilityCutoff()"
  - "ArticleScoreStore: the single ELIGIBLE predicate, newest-first selection, dispatch recheck, write-once SCORED / counted FAILED / terminal SKIPPED upserts, status counts and Re-score count/delete"
  - "ArticleScoreStoreTest: 18 real-Postgres tests"
affects: [04-03, 04-04, 04-05, 04-06, 04-08]

actuals:
  tokens: 8000
  tasks: 3
  commits: 5
plan_head_before: 896a090319b804323483dca07cb04d348af8d27e

tech-stack:
  added: []
  patterns:
    - "First JdbcClient repository in src/main: @Repository + @RequiredArgsConstructor, SQL built from package-visible static final predicate constants"
    - "Bind every Instant as java.sql.Timestamp.from(instant) through JdbcClient (PgJDBC cannot infer Instant)"
    - "INSERT ... SELECT FROM article ... ON CONFLICT upserts: missing parent rows are silent no-ops instead of FK errors"
    - "RED phase with signature-only stubs so Java tests fail on assertions, not compilation"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java
    - src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java

key-decisions:
  - "NEEDS_SCORING and counts() inline MAX_ATTEMPTS as a compile-time constant (per plan), not the :maxAttempts parameter shown in research Pattern 4"
  - "counts() and filter tests were strengthened with in-window baseline rows so the RED stubs (returning 0 / empty) could not pass them vacuously"

patterns-established:
  - "One predicate constant (ELIGIBLE) reused by selection, recheck, counts and Re-score, so what's counted is what's acted on"
  - "Re-score count and delete share RESCORE_SCOPE by construction"

requirements-completed: [SCOR-03, SCOR-04, SCOR-07, SCOR-08, INT-05, JEV-05]

coverage:
  - id: D1
    description: "Single eligibility predicate (unread AND COALESCE(published_at, fetched_at) > cutoff, strict) with newest-first selection and id DESC tiebreak"
    requirement: SCOR-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#selectsEligibleUnscoredNewestFirst"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#excludesReadAndOutOfWindowArticles"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#limitCapsTheSelection"
        status: pass
    human_judgment: false
  - id: D2
    description: "Write-once SCORED upsert with topic nouls in one transaction; dispatch recheck via loadCandidate"
    requirement: SCOR-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#scoredArticleIsStoredAndNoLongerSelected"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#scoredIsWriteOnce"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#scoredReplacesFailedOnceAndKeepsAttempts"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#loadCandidateCarriesArticleTextAndFeedTitle"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#topicDeletedMidCallIsLeftOut"
        status: pass
    human_judgment: false
  - id: D3
    description: "FAILED attempts bounded at 3; SKIPPED terminal first-writer-wins; missing-article writes are no-ops"
    requirement: SCOR-07
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#failedAttemptsCountUpToExhaustion"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#failedNeverOverwritesScoredOrSkipped"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#skippedIsTerminalAndFirstReasonWins"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#writesForMissingArticleAreNoOps"
        status: pass
    human_judgment: false
  - id: D4
    description: "Ingest-time filter filterNeedingScoring on the same predicate, empty ids short-circuit"
    requirement: SCOR-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#filterKeepsOnlyIdsThatNeedScoringNewestFirst"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#filterOfNoIdsRunsNoQuery"
        status: pass
    human_judgment: false
  - id: D5
    description: "Status counts (eligibleUnscored, failed) from one statement over eligible articles"
    requirement: JEV-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#countsSplitRetryingFromExhausted"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#countsIgnoreReadAndOutOfWindow"
        status: pass
    human_judgment: false
  - id: D6
    description: "Re-score count and delete share RESCORE_SCOPE; count equals rows deleted; SKIPPED/read/old rows untouched; topic rows cascade"
    requirement: INT-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#rescoreCountEqualsRowsDeleted"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java#rescoreLeavesSkippedReadAndOutOfWindowRows"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 02: ArticleScoreStore Summary

**JdbcClient-backed `ArticleScoreStore` that builds every scoring query from one `ELIGIBLE` predicate. It covers newest-first selection, the dispatch recheck, SCORED upserts that are written once and never overwritten, FAILED rows that count attempts up to 3, SKIPPED rows that are terminal, status counts from one statement, and the Re-score count and delete, which share one scope. All of it is proven by 18 tests against real Postgres.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-24T01:27:47Z
- **Completed:** 2026-09-24T01:33:52Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- `MyfeederProperties.Interest` binds `myfeeder.interest.*` with the D-09/D-10/D-16 defaults and exposes `eligibilityCutoff()`.
- `ArticleScoreStore` defines the one eligibility predicate. The sweep selection, ingest filter, dispatch recheck, status counts and Re-score scope are all built from it (D-02, D-12).
- The upserts follow the R3 lifecycle. SCORED only overwrites FAILED, keeping attempts and clearing `last_error`. FAILED increments only while the row is FAILED. SKIPPED is `DO NOTHING`. Writes for a missing article or a deleted topic are silent no-ops.
- The Re-score count and delete share `RESCORE_SCOPE`, and the tests prove the count equals the rows deleted.

## Contract for downstream plans (exact signatures)

`org.bartram.myfeeder.config.MyfeederProperties`
- `MyfeederProperties.Interest getInterest()`
- `Interest` (`@Data`): `int windowDays = 14`, `int concurrency = 1`, `int queueCapacity = 1000`, `int sweepBatchSize = 50`, `Duration sweepDelay = PT2M`, `Duration sweepInitialDelay = PT1M`, `public Instant eligibilityCutoff()` (= `Instant.now().minus(Duration.ofDays(windowDays))`)
- Property keys: `myfeeder.interest.window-days`, `.concurrency`, `.queue-capacity`, `.sweep-batch-size`, `.sweep-delay`, `.sweep-initial-delay` (the YAML block itself is added by 04-05)

`org.bartram.myfeeder.repository.ArticleScoreStore` (`@Repository`, constructor-injected `JdbcClient`)
- `public static final int MAX_ATTEMPTS = 3`
- package-visible constants: `ELIGIBLE`, `NEEDS_SCORING`, `NEWEST_FIRST`, `RESCORE_SCOPE`
- `public record Candidate(Article article, String feedTitle)`: the article carries id, feedId, guid, title, summary, content, publishedAt, fetchedAt, and read=false
- `public record TopicNoul(long topicId, double noul, int topicVersion)`
- `public record ScoredRow(Double profileScore, Integer profileMaxLevel, Double profileConfidence, Integer profileVersion, String model, String requestId, List<TopicNoul> topics)` (profile fields may be null)
- `public record ScoreCounts(long eligibleUnscored, long failed)`
- `public List<Long> findNeedingScoring(Instant cutoff, int limit)`
- `public List<Long> filterNeedingScoring(Collection<Long> ids, Instant cutoff)`
- `public Optional<Candidate> loadCandidate(long articleId, Instant cutoff)`
- `@Transactional public boolean writeScored(long articleId, ScoredRow row)`
- `public void writeFailed(long articleId, String lastError)`: `lastError` must be fixed text only
- `public void writeSkipped(long articleId, String reason)`
- `public ScoreCounts counts(Instant cutoff)`
- `public long countRescoreScope(Instant cutoff)`
- `public int deleteRescoreScope(Instant cutoff)`

Callers pass `properties.getInterest().eligibilityCutoff()` as `cutoff`.

## Task Commits

1. **Task 1 (tracer): Predicate, selection, recheck, SCORED upsert.** `8a7398e` (feat)
2. **Task 2: FAILED/SKIPPED lifecycle and ingest filter.** RED `ba7e689` (test), GREEN `d732a3d` (feat)
3. **Task 3: Status counts and Re-score scope.** RED `e8f56a1` (test), GREEN `6fe8bb3` (feat)

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`: adds the nested `Interest` class and the `interest` field
- `src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java`: all scoring-queue SQL
- `src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java`: 18 `@DataJdbcTest` tests against Testcontainers Postgres

## Decisions Made
- I followed the plan and inlined `MAX_ATTEMPTS` into `NEEDS_SCORING` and `counts()` as a compile-time constant. Research Pattern 4 had used a `:maxAttempts` named parameter.
- `scoreRowFor` in the test asserts that exactly one row exists instead of calling `queryForMap`. A missing row then shows up as an assertion failure, which keeps the RED evidence valid, rather than as `EmptyResultDataAccessException`.

## Deviations from Plan

### Test strengthening (no production-scope change)

**1. In-window baseline rows in `countsIgnoreReadAndOutOfWindow` and an in-scope row in `rescoreLeavesSkippedReadAndOutOfWindowRows`**
- **Found during:** Task 3 RED
- **Issue:** As literally specified, both tests expect zero counts and zero deletions, so the RED stubs returning 0 would pass them without testing anything.
- **Fix:** Added one eligible unscored article and one exhausted in-window article, so counts are expected to be (1, 1). Added one in-scope SCORED row, so the count and delete are expected to be 1. The excluded rows still must add nothing and must survive. The Re-score test also checks that the article count is unchanged and that the read flag stays true, which covers the plan's prohibition.
- **Files modified:** ArticleScoreStoreTest.java
- **Committed in:** e8f56a1

**2. RED commits include signature-only stubs in ArticleScoreStore**
- **Found during:** Task 2 and Task 3 RED
- **Issue:** In Java, tests that call methods which don't exist yet fail to compile. Under tdd.md (#3770) that counts as INVALID_RED.
- **Fix:** Added empty no-op stubs in the `test(04-02)` commits. The RED evidence was checked with `gsd-tools check tdd-red-evidence`, converting the Gradle JUnit XML report to TAP. Both returned `RED_EVIDENCE_OK`, for target tests `failedAttemptsCountUpToExhaustion` and `rescoreCountEqualsRowsDeleted`.
- **Committed in:** ba7e689, e8f56a1

---

**Total deviations:** 2 (test rigor and TDD mechanics; neither changes production scope)
**Impact on plan:** None on the contract. The tests are strictly stronger than specified.

## TDD Gate Compliance

| Task | RED | GREEN | REFACTOR | Evidence |
|------|-----|-------|----------|----------|
| 2 | ba7e689 | d732a3d | none needed | RED_EVIDENCE_OK (5/14 failed on assertions, target failedAttemptsCountUpToExhaustion) |
| 3 | e8f56a1 | 6fe8bb3 | none needed | RED_EVIDENCE_OK (4/18 failed on assertions, target rescoreCountEqualsRowsDeleted) |

Task 1 is the tracer (`type="tracer"`, not TDD). After committing it I re-ran its automated verify end to end; it passed, so expansion went ahead.

## Verification

- `./gradlew test --tests 'org.bartram.myfeeder.repository.ArticleScoreStoreTest' --tests '...V6InterestScoringMigrationTest'`: 18/18 and 16/16 pass
- Full backend suite `./gradlew test -x npmBuild -x npmInstall`: 329 tests, 0 failures, 0 errors, 2 skipped (the skips predate this plan)
- All acceptance-criteria greps pass. `ELIGIBLE` is defined once. The window comparison appears once outside comments. The code has two `COUNT(*) FILTER` clauses, 5 `RESCORE_SCOPE` references, and no `UPDATE article ` or `DELETE FROM article ` statements.

## Issues Encountered
None

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- The 04-03 scorer, 04-04 listener/queue, 04-05 sweep, 04-06 status counts and 04-08 Re-score can all be built against the contract above. Nobody needs to edit this file.
- Requirement IDs are not yet marked complete. `requirements.ready-ids` reported 0/6 ready, because sibling plans in this phase also declare them.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED
