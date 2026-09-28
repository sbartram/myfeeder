---
phase: 04-scoring-pipeline-backfill-sweep
plan: 03
subsystem: scoring
tags: [jev, typesafe, resilience4j, spring-data-jdbc, testcontainers, mockito, tdd]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-02 ArticleScoreStore (loadCandidate, writeScored, writeFailed, writeSkipped) and MyfeederProperties.Interest.eligibilityCutoff()"
  - phase: 03-interest-model-schema-rubric-editor
    provides: "InterestService.isColdStart/getProfile/listTopics, InterestQuestions.forRubric, ArticleStateBuilder"
provides:
  - "ArticleScoringService.score(long): SCOR-06 gates, dispatch recheck, GUID/no-text skips, one judge() call, answer validation, SCORED/FAILED write"
  - "ScoringFailure: D-19 transient vs permanent classification and fixed-text last_error"
  - "ArticleStateBuilder.truncate D-13 lower bound (CR-01 closed)"
affects: [04-04, 04-05, 04-06, 04-08]

actuals:
  tokens: 9500
  tasks: 3
  commits: 5
plan_head_before: d36af732a0b81f2afede6d3286292e5d5404695f

tech-stack:
  added: []
  patterns:
    - "Scorer holds no transaction across the Jev call; only the store's writeScored is @Transactional"
    - "Classify by exception type only; unknown errors are permanent (bounded at 3 billed attempts)"
    - "Validate answers before the write so a CHECK violation never becomes a re-billing loop"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java
    - src/main/java/org/bartram/myfeeder/service/ScoringFailure.java
    - src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java
    - src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
    - src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java

key-decisions:
  - "Profile columns are keyed on whether the profile question was SENT, not on whether the response happens to carry a profile score, so a blank profile always stores NULL profile columns (V6 comment)"
  - "Out-of-range or non-finite answers write FAILED 'invalid answer' (not clamped), and a missing requested answer counts as invalid too"
  - "Task 3's GREEN commit is typed fix(04-03), not feat(04-03), because it corrects CR-01 rather than adding a feature"

patterns-established:
  - "ScoringFailure.describe: simpleName + ' (HTTP <status>, requestId <id>)' for API errors, simpleName alone otherwise; never getMessage()/body"
  - "Scorer log lines carry article ids and class names only"

requirements-completed: [SCOR-01, SCOR-03, SCOR-06, SCOR-07, SCOR-08]

coverage:
  - id: D1
    description: "An eligible article is judged once with the shared builders and its raw outputs (profile score, max level, confidence, snapshot versions, model, request id, topic nouls) land in Postgres; a second score() call does not re-judge"
    requirement: SCOR-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java#scoresAnEligibleArticleOnceAndStoresRawOutputs"
        status: pass
    human_judgment: false
  - id: D2
    description: "SCOR-06 gates and dispatch recheck: not configured, cold start, read article or no candidate means no call and no write; an empty rubric is never judged"
    requirement: SCOR-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java#readArticleIsNeverJudged"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java#coldStartJudgesNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#notConfiguredTouchesNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#coldStartTouchesNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#noCandidateMeansNoCall"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#emptyRubricIsNeverJudged"
        status: pass
    human_judgment: false
  - id: D3
    description: "SCOR-08: blank/null GUID writes SKIPPED 'no guid' and a textless article writes SKIPPED 'no text', both without a call"
    requirement: SCOR-08
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#blankGuidIsSkippedWithoutACall"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#articleWithoutTextIsSkippedWithoutACall"
        status: pass
    human_judgment: false
  - id: D4
    description: "SCOR-07/D-19 classification: permanent failures write fixed-text FAILED (no message/body leak), transient failures write nothing; invalid answers and a failed write after a billed success record a FAILED attempt"
    requirement: SCOR-07
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#permanentFailuresRecordAFixedTextAttempt"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#transientFailuresWriteNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#invalidAnswerRecordsAFailedAttempt"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#writeErrorAfterBilledSuccessRecordsAFailedAttempt"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#scorerHoldsNoTransactionAndNoBreakerHandle"
        status: pass
    human_judgment: false
  - id: D5
    description: "SCOR-03 raw-output mapping: legend -1 falls back to PROFILE_MAX_LEVEL (4), blank profile stores NULL profile columns, boundary nouls 0.0/1.0 and full-precision values stored exactly"
    requirement: SCOR-03
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#missingLegendFallsBackToProfileMaxLevel"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#blankProfileStoresNullProfileColumns"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java#boundaryNoulsAreStoredExactly"
        status: pass
    human_judgment: false
  - id: D6
    description: "D-13 truncate fix (CR-01): CJK and long-URL summaries hard-cut to exactly 1,500 chars; whitespace cut accepted only at index >= max/2"
    requirement: SCOR-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java#cjkTextIsHardCutAtTheLimit"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java#longUrlIsHardCutAtTheLimit"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java#whitespaceAtHalfIsUsed"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java#whitespaceBelowHalfIsIgnored"
        status: pass
    human_judgment: false

duration: 7min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 03: Article Scorer Summary

**`ArticleScoringService.score(id)` judges one eligible article with a single `JevApiClient.judge` call. The state and questions come from the same builders the preview uses, and the rubric is snapshotted before the call. The raw outputs are stored write-once through `ArticleScoreStore`. `ScoringFailure` sorts every error: permanent errors write a fixed-text FAILED row and use an attempt, transient ones write nothing. The CR-01 truncate fix means CJK and URL summaries keep 1,500 characters.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-09-24T01:37:04Z
- **Completed:** 2026-09-24T01:43:37Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- `score(long)` runs these steps in order:
  1. The SCOR-06 gates (`isConfigured()`, `isColdStart()`, both delegated).
  2. The dispatch recheck (`loadCandidate`).
  3. The GUID and no-text skips.
  4. The state build and the rubric snapshot. An empty question map is never sent.
  5. One `judge()` call, then answer validation.
  6. The SCORED write. It has no transaction and no breaker pre-check.
- `ScoringFailure.isTransient` follows the D-19/D-08 table: 429, 5xx including overloaded, connection errors and timeouts, an open breaker, not-configured, and 401/403 are transient. Everything else is permanent, including 404, response-validation errors, a plain `TypeSafeException` and `IllegalArgumentException`.
- `ScoringFailure.describe` writes only the class name, HTTP status and request id. The test puts a "LEAKCHECK" marker in exception messages and bodies and asserts that it never reaches `writeFailed`.
- These cases now record a FAILED attempt, which keeps the 3-attempt bound (Pitfall 11) instead of letting the article re-bill forever:
  - an answer that is non-finite or out of range
  - a missing requested answer
  - a `writeScored` exception after a billed success
- D-13: `truncate` accepts a whitespace cut only at index `>= max / 2`. Otherwise it hard-cuts at `max - 1`, and the surrogate guard is kept.

## Task Commits

1. **Task 1 (tracer): Score an eligible article end to end.** `eda8f0a` (feat)
2. **Task 2: Skips, answer validation, failure classification.** RED `d9509c5` (test), GREEN `5a990e7` (feat)
3. **Task 3: D-13 truncate fix (CR-01).** RED `51c63ce` (test), GREEN `39a7b9e` (fix)

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java`: the scorer
- `src/main/java/org/bartram/myfeeder/service/ScoringFailure.java`: classification and fixed-text descriptions
- `src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java`: the `truncate` lower bound and its Javadoc (`build()` is unchanged)
- `src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java`: 3 `@DataJdbcTest` tests running scorer, store and Postgres with the real `InterestService`
- `src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java`: 14 Mockito tests
- `src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java`: 4 new truncate tests

## Contract for downstream plans

- `org.bartram.myfeeder.service.ArticleScoringService` (`@Service`). The constructor takes `(JevApiClient, InterestService, ArticleScoreStore, MyfeederProperties)`.
- `public void score(long articleId)` never throws for a Jev failure. It catches and classifies them. A failure in the store's own `loadCandidate`, `writeSkipped` or `writeFailed` still propagates, so the 04-04 executor task should log and continue.
- Stored reason texts: `"no guid"`, `"no text"` (SKIPPED), `"invalid answer"`, `"write failed"`, and `ScoringFailure.describe(e)` (FAILED).

## Decisions Made
- The profile columns depend on whether the profile question was **sent** (`questions.containsKey("profile")`), not on whether the response happens to carry a profile score. A blank profile therefore always stores NULL profile columns, which matches the V6 comment, even if Jev returns an answer nobody asked for.
- A requested answer that is missing from the judgment counts as `"invalid answer"`. `judge()` already rejects missing answers, so this is defense in depth, and it prevents an NPE when unboxing a noul.
- Task 3's GREEN commit is typed `fix`, not `feat`, because it corrects a review finding (CR-01).

## Plan notes recorded as requested
- **SCOR-08 premise correction:** `article.guid` is `NOT NULL` (V1). A GUID-less parsed item fails the poll insert before scoring can see it. That is a polling problem (QUAL-V2-01), not a scoring one. The scorer's blank-GUID guard still ships because `""` GUIDs can exist. No "polled three times" test was written.
- **STATE.md concern "verify jsoup is on the classpath" is resolved:** jsoup 1.11.2 comes in transitively through readability4j, and `ArticleStateBuilder` compiles and runs against it.
- **CR-01 is closed** by Task 3.

## Deviations from Plan

### Test additions (no production-scope change)

**1. The boundary-noul case became its own test, `boundaryNoulsAreStoredExactly`**
- **Found during:** Task 2 RED
- **Issue:** The behavior list folds "nouls of exactly 0.0 and 1.0 are stored" into `invalidAnswerRecordsAFailedAttempt`. That mixes a rejection case and an acceptance case in one test.
- **Fix:** I split the acceptance case into a 14th test. It also asserts full-precision profile values, the model and the request id (the precision edges of SCOR-01 and SCOR-03). All 13 named tests exist as specified.
- **Committed in:** d9509c5

**2. The flow test also clears `interest_topic` in `@BeforeEach`**
- **Issue:** The cold-start test depends on having zero topics, so leftover topic rows would make it order-dependent.
- **Fix:** A `DELETE FROM interest_topic` runs inside the rolled-back test transaction.
- **Committed in:** eda8f0a

---

**Total deviations:** 2. Both only add test rigor.
**Impact on plan:** None. Production code matches the plan's contract.

## TDD Gate Compliance

| Task | RED | GREEN | REFACTOR | Evidence |
|------|-----|-------|----------|----------|
| 2 | d9509c5 | 5a990e7 (feat) | none needed | RED_EVIDENCE_OK: 6/14 failed; target `invalidAnswerRecordsAFailedAttempt` failed with Mockito `WantedButNotInvoked` (an AssertionError) |
| 3 | 51c63ce | 39a7b9e (fix) | none needed | RED_EVIDENCE_OK: 3/13 failed; target `cjkTextIsHardCutAtTheLimit` failed with "Expected size: 1500 but was: 3" |

The Task 3 GREEN commit uses the `fix(04-03)` type instead of `feat(04-03)`. It is still the implementation commit that follows its RED commit. `whitespaceAtHalfIsUsed` already passed before the fix. It is a regression guard for the part of the behavior that exists today, and the planned RED was the CJK, URL and below-half cases.

Task 1 is the tracer (`type="tracer"`). The run was interactive in `end-of-phase` mode and the tracer's verify was automated only, so after the commit I re-ran the flow test end to end. It passed 3/3, and expansion went ahead.

## Verification

- `ArticleScoringFlowTest`: 3/3 pass. `ArticleScoringServiceTest`: 14/14. `ArticleStateBuilderTest`: 13/13. `InterestPreviewServiceTest`: 10/10. `InterestJudgeRequestTest`: 2/2.
- The full backend suite (`./gradlew test -x npmBuild -x npmInstall`) ran 350 tests with 0 failures, 0 errors and 2 skipped. The skips predate this plan.
- All acceptance-criteria greps pass:
  - 1 `jevApiClient.judge(`, 1 `InterestQuestions.forRubric(`, 1 `ArticleStateBuilder.build(`, 1 `interestService.isColdStart()`
  - 0 `isBlank`, 0 non-comment `Transactional`, 0 `CircuitBreakerRegistry`, 0 non-comment `getMessage` in ScoringFailure
  - 1 `ScoringFailure.isTransient`, 1 each of `"no guid"` and `"no text"`
  - 1 `i >= max / 2`, and the `build()` signature is unchanged

## Issues Encountered
None.

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- 04-04 (listener and queue) and 04-05 (sweep) can call `ArticleScoringService.score(id)` directly.
- 04-06 and 04-08 do not depend on this plan's files.
- Requirements: SCOR-03, SCOR-07 and SCOR-08 are marked complete. SCOR-01 and SCOR-06 are still blocked by sibling plans that also declare them (`requirements.ready-ids`).

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED
