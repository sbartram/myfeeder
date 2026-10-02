---
phase: 11-gap-discovery
plan: 02
subsystem: api
tags: [spring-boot, jdbcclient, postgres, interest-ranking, gap-discovery, transactions]

requires:
  - phase: 11-gap-discovery
    provides: "11-01: TopicSuggestionStore.candidates, TopicSuggestionService.list, GET /api/interest/suggestions"
  - phase: 08-engagement-capture
    provides: the V7 topic_suggestion_dismissal table (reason CHECK DISMISSED / TOPIC_CREATED)
provides:
  - "PUT /api/interest/suggestions/{articleId}/dismissal: bodyless, 204, idempotent, 404 for an unknown article"
  - "TopicSuggestionStore.handle(articleId, reason): INSERT ... SELECT ... ON CONFLICT (article_id) DO NOTHING"
  - "POST /api/interest/topics accepts an optional sourceArticleId and writes TOPIC_CREATED in the createTopic transaction"
  - "SuggestionDismissalReason enum (DISMISSED, TOPIC_CREATED)"
affects: [11-04 suggestion UI wiring (sends sourceArticleId and the dismissal PUT), 12 calibration]

actuals:
  tokens: 13386
  tasks: 3
  commits: 4
plan_head_before: 78d5faff53e488c48753afc316e734cf731baab1
plan_head_after: 7bd8d3f274ffb2ff07df29a43c4297b60f8bea66

tech-stack:
  added: []
  patterns:
    - "Idempotent marker write as INSERT ... SELECT FROM the parent table ... ON CONFLICT DO NOTHING: keeps the first value and turns a stale parent id into 0 rows instead of an FK error"
    - "A JdbcClient write with no transaction of its own joins the caller's Spring Data JDBC @Transactional (proven by a spy-throw rollback test)"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/model/SuggestionDismissalReason.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java
    - src/main/java/org/bartram/myfeeder/service/TopicSuggestionService.java
    - src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java
    - src/main/java/org/bartram/myfeeder/controller/TopicRequest.java
    - src/main/java/org/bartram/myfeeder/controller/InterestController.java
    - src/main/java/org/bartram/myfeeder/service/InterestService.java
    - src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java
    - src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
    - src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java
    - CLAUDE.md

key-decisions:
  - "Dismiss is a bodyless PUT, so it is never a CORS simple request and needs no content-type guard (the recordOpen precedent)"
  - "ON CONFLICT DO NOTHING keeps the first reason: a DISMISSED-then-created article stays DISMISSED, and it is hidden either way"
  - "createTopic has one 4-arg signature with no 3-arg overload; every existing caller passes null"
  - "A stale sourceArticleId is ignored (201, no row), so a stale suggestion never blocks a topic save"

patterns-established:
  - "Suggestion writes go only through TopicSuggestionStore.handle, the single reader and writer of topic_suggestion_dismissal"

requirements-completed: [GAP-02, GAP-03, GAP-05]

coverage:
  - id: D1
    description: "PUT /api/interest/suggestions/{articleId}/dismissal returns 204 with an empty body, writes one DISMISSED row, and removes the article from the list; a repeat keeps one row with the first reason; an unknown id is 404 'Article not found: {id}' and a non-numeric id is 400"
    requirement: GAP-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#dismissReturns204AndHidesTheSuggestion"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#dismissIsIdempotentAndKeepsTheFirstReason"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#dismissingAnUnknownArticleIs404"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#dismissingWithANonNumericIdIs400"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java#handleInsertsOnceAndKeepsTheFirstReason, handleOnAMissingArticleInsertsNothing, aHandledArticleIsNoLongerACandidate"
        status: pass
    human_judgment: false
  - id: D2
    description: "A dismissed suggestion stays gone after a later engagement of a new kind and on every later GET, and dismissing leaves the article's badge unchanged (no ranking signal)"
    requirement: GAP-03
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#aDismissedSuggestionStaysGoneAfterLaterEngagement"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#dismissingLeavesTheBadgeUnchanged"
        status: pass
    human_judgment: false
  - id: D3
    description: "POST /api/interest/topics with sourceArticleId writes TOPIC_CREATED with the topic and hides the suggestion for good, also after the topic is deleted; without a source, with a stale id, or on PUT no row is written; the 25th topic writes the row and the 26th is a 400 that writes nothing; a prior DISMISSED row is kept"
    requirement: GAP-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#creatingATopicFromASuggestionHidesItForGood"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#aStaleSourceArticleIdStillCreatesTheTopic"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#withoutASourceNoRowIsWritten"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#theTwentyFifthTopicWritesTheRowAndTheTwentySixthWritesNothing"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#aDismissedArticleKeepsItsFirstReasonWhenATopicIsCreatedFromIt"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#deletingTheTopicKeepsTheArticleHandled"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#updatingATopicWithASourceWritesNoRow"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#createTopicWithASourceHandlesTheSuggestionAfterTheSave, createTopicWithoutASourceNeverTouchesTheStore, aRejectedCreateNeverHandlesTheSuggestion"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java#createTopicPassesTheSourceArticleId, updateTopicIgnoresTheSourceArticleId"
        status: pass
    human_judgment: false
  - id: D4
    description: "The topic insert and its TOPIC_CREATED row commit or roll back together, and a text/plain topic POST is refused with 415 and writes nothing"
    requirement: GAP-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#aFailedDismissalInsertRollsBackTheTopic"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#aTextPlainTopicCreateIs415"
        status: pass
    human_judgment: false
  - id: D5
    description: "Dismissing and creating a topic from a suggestion never call Jev, and the ranking SQL is unchanged"
    requirement: GAP-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#neverCallsJev (@AfterEach, all 18 tests)"
        status: pass
      - kind: other
        ref: "git hash-object InterestScoreQueries.java = 807baf80e93c36b519a44cfc92eb5d9c052cb305; V7EngagementMigrationTest and InterestCalibrationReplaySqlTest unmodified and green"
        status: pass
    human_judgment: false
  - id: D6
    description: "CLAUDE.md documents the backend: new classes in Package Structure, the two routes and sourceArticleId under Routes, and a Gap discovery (v0.3.0, Phase 11) bullet group"
    verification:
      - kind: other
        ref: "Task 3 grep verify (GET /suggestions, PUT /suggestions/{articleId}/dismissal, Gap discovery (v0.3.0, Phase 11), myfeeder.interest.suggestions.near-miss, TopicSuggestionStore, SuggestionDismissalReason, sourceArticleId)"
        status: pass
    human_judgment: true
    rationale: "A grep proves the facts are present; whether the prose reads clearly is a human judgment"

duration: 9min
completed: 2026-10-01
status: complete
---

# Phase 11 Plan 02: Suggestion Dismiss and Create-from-Suggestion Backend Summary

**A bodyless `PUT /api/interest/suggestions/{articleId}/dismissal` and an optional `sourceArticleId` on `POST /api/interest/topics` both mark a suggestion handled through one `INSERT ... SELECT ... ON CONFLICT DO NOTHING`. The write keeps the first reason, ignores a stale id, and commits atomically with the topic insert. A handled article never returns, no ranking signal changes, and Jev is never called.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-10-01T22:42:16Z
- **Completed:** 2026-10-01T22:50:50Z
- **Tasks:** 3
- **Files modified:** 13 (1 created, 12 modified)

## Accomplishments

- `SuggestionDismissalReason { DISMISSED, TOPIC_CREATED }` mirrors the V7 CHECK values.
- `TopicSuggestionStore.handle(articleId, reason)` is the only writer of `topic_suggestion_dismissal`.
  - It is idempotent and keeps the first reason.
  - A missing article inserts 0 rows and never throws.
  - It opens no transaction of its own, so inside `createTopic` it joins that transaction.
- `TopicSuggestionService.dismiss` returns 404 `Article not found: {id}` for an unknown article, else writes DISMISSED. `TopicSuggestionController` serves it as a bodyless PUT that returns 204.
- `TopicRequest` gains `Long sourceArticleId` (appended). `InterestService.createTopic(name, description, weight, sourceArticleId)` calls `handle(sourceArticleId, TOPIC_CREATED)` after `topicRepository.save`, inside the existing `@Transactional`. A cap or validation 400 writes nothing, and `PUT /topics/{id}` ignores the field.
- The HTTP tests prove each case: permanence after later engagement and after a topic delete, an unchanged badge, the 25th/26th cap edge, a kept first reason, rollback on a forced insert failure, 415 for text/plain, and no Jev call.
- CLAUDE.md documents the backend (Package Structure, Schema, Routes, and a new Gap discovery group).

## Task Commits

1. **Task 1 (tracer): dismissing a suggestion over HTTP removes it for good** - `e5fa294` (feat)
2. **Task 2 RED: failing tests for createTopic sourceArticleId** - `65c3d4e` (test)
3. **Task 2 GREEN: createTopic writes TOPIC_CREATED in one transaction** - `4d23a30` (feat)
4. **Task 3: atomic and permanent over HTTP, plus CLAUDE.md** - `7bd8d3f` (test)

There was no Task 2 REFACTOR commit, because nothing needed cleaning up.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/model/SuggestionDismissalReason.java`: the two reasons
- `src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java`: `handle`, the idempotent dismissal insert
- `src/main/java/org/bartram/myfeeder/service/TopicSuggestionService.java`: `dismiss` (404 check, then DISMISSED)
- `src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java`: `PUT /suggestions/{articleId}/dismissal`
- `src/main/java/org/bartram/myfeeder/controller/TopicRequest.java`: appended `Long sourceArticleId`
- `src/main/java/org/bartram/myfeeder/controller/InterestController.java`: POST passes `request.sourceArticleId()`
- `src/main/java/org/bartram/myfeeder/service/InterestService.java`: 4-arg `createTopic` and the `suggestionStore` dependency
- `src/test/java/.../repository/TopicSuggestionStoreTest.java`: 3 `handle` tests
- `src/test/java/.../controller/TopicSuggestionApiIntegrationTest.java`: 15 new HTTP tests (6 dismissal, 9 topic-create); `@BeforeEach` now clears every topic; `@MockitoSpyBean TopicSuggestionStore`
- `src/test/java/.../service/InterestServiceTest.java`: 13 call sites take `null`, plus 3 new tests
- `src/test/java/.../controller/InterestControllerTest.java`: 2 stubs moved to 4 args, plus 2 new tests
- `src/test/java/.../service/ArticleScoringFlowTest.java`: 3 call sites take `null`; `@Import` adds `TopicSuggestionStore`
- `CLAUDE.md`: backend facts for gap discovery

## Decisions Made

- The plan was followed as specified, including its recorded interpretations: a bodyless PUT route, first reason wins, a stale id is ignored, and there is no 3-arg overload.
- The `handle` SQL literal puts `INSERT INTO topic_suggestion_dismissal (article_id, reason) SELECT a.id` on one source line, so the Task 1 acceptance grep matches it as one string. The statement is the same as RESEARCH §1.

## TDD Notes (Task 2)

- **RED (valid, `RED_EVIDENCE_OK` from `check tdd-red-evidence` for both target classes):** the RED commit carried a signature-only skeleton so the tests compile: the appended field, the 4-arg `createTopic` with no `handle` call, and a controller that passed `null`. Against it, two tests failed on their assertions:
  - `createTopicWithASourceHandlesTheSuggestionAfterTheSave`: `VerificationInOrderFailure`, because `handle` was never called.
  - `createTopicPassesTheSourceArticleId`: an `AssertionError` on `$.id`, because the controller passed `null`, so the stub returned no body.
  - The other 35 tests in the three classes passed.
- **GREEN:** the `handle` call after the save, plus `request.sourceArticleId()` in the controller. All 37 tests pass.
- **Atomicity mutation check (Task 3):** with `@Transactional` temporarily removed from `createTopic`, `aFailedDismissalInsertRollsBackTheTopic` failed. The file was restored from git, and the test passes again. This settles research assumption A1: the JdbcClient insert joins the Spring Data JDBC transaction.

## Deviations from Plan

None in what was built: the plan was executed as written.

## Verification

- Task 1: TopicSuggestionApiIntegrationTest 9, TopicSuggestionStoreTest 17, TopicSuggestionServiceTest 6, all green. The tracer gate re-ran verify end to end before expansion.
- Task 2: InterestServiceTest 20, InterestControllerTest 14, ArticleScoringFlowTest 3, all green.
- Task 3: TopicSuggestionApiIntegrationTest 18, all green.
- Full backend suite (`./gradlew test -x npmBuild -x npmInstall`): 78 classes, 707 tests (684 before + 23 new), 0 failures, 0 errors, and 2 pre-existing skips.
- Guards:
  - `InterestScoreQueries.java` blob is still `807baf80e93c36b519a44cfc92eb5d9c052cb305`.
  - V7EngagementMigrationTest, InterestCalibrationReplaySqlTest, DevProfileConfigTest and every migration are unmodified.
  - `git status --porcelain openwiki` is empty.
- Requirements: GAP-03 is marked complete in REQUIREMENTS.md. GAP-02 and GAP-05 are also declared by plan 11-04, so `requirements.ready-ids` holds them until that plan's SUMMARY exists.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- Plan 11-04 can wire the UI to these two endpoints:
  - Dismiss sends `PUT /api/interest/suggestions/{id}/dismissal`, which returns 204 with an empty body.
  - The suggestion and reading-pane drafts send `sourceArticleId` on `POST /api/interest/topics`.
  - The CLAUDE.md "Gap discovery" group is where 11-04 adds the UI facts.

---
*Phase: 11-gap-discovery*
*Completed: 2026-10-01*

## Self-Check: PASSED

- Files: SuggestionDismissalReason.java, TopicSuggestionStore.java, TopicSuggestionController.java, InterestService.java and this SUMMARY all exist.
- Commits: e5fa294, 65c3d4e, 4d23a30, 7bd8d3f and ac18662 are all on the branch.
