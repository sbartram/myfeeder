---
phase: 03-interest-model-schema-rubric-editor
plan: 03
subsystem: api
tags: [spring-mvc, spring-data-jdbc, interest-profile, interest-topics, validation, testcontainers]

requires:
  - phase: 03-01
    provides: V6 interest_profile singleton + interest_topic table, InterestProfile/InterestTopic entities, InterestProfileRepository, InterestTopicRepository.findAllOrdered()
provides:
  - InterestService (profile + topic CRUD, INT-01/INT-02 limits, version rules, isColdStart())
  - InterestController GET|PUT /api/interest/profile, GET|POST /api/interest/topics, PUT|DELETE /api/interest/topics/{id}
  - ProfileUpdateRequest and TopicRequest request records
affects: [03-04 status + preview, 03-05/03-06 frontend editor, phase-04 scorer SCOR-06 gate]

actuals:
  tokens: 7841
  tasks: 3
  commits: 4
plan_head_before: ebacdb02609873aaa0786020dc3bfbb67fb52f0c

tech-stack:
  added: []
  patterns:
    - "Service-enforced product limits with fixed-text IllegalArgumentException messages (400 via GlobalExceptionHandler), never echoing input"
    - "Plain version column bumped by the service only on a semantic change (profile text / trimmed topic description)"
    - "@SpringBootTest + MockMvcBuilders.webAppContextSetup integration test that reuses the MyfeederApplicationTests context"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/InterestService.java
    - src/main/java/org/bartram/myfeeder/controller/InterestController.java
    - src/main/java/org/bartram/myfeeder/controller/ProfileUpdateRequest.java
    - src/main/java/org/bartram/myfeeder/controller/TopicRequest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  modified: []

key-decisions:
  - "03-03: InterestService.isColdStart() is the single cold-start predicate (blank-after-trim profile AND zero topics); 03-04 status and the Phase 4 SCOR-06 gate call it and must not reimplement it"
  - "03-03: Topic version bumps only when the trimmed description changes; name and weight edits never bump it. Profile version bumps only on an exact text change"
  - "03-03: Limits are service constants (MAX_PROFILE_CHARS 2000 UTF-16 units, MAX_TOPICS 25, weight -50..+50 default +20, name 40, description 500); the V6 weight CHECK is only the DB backstop"

patterns-established:
  - "Interest REST contract: field names id/profileText/version/updatedAt and id/name/description/weight/version/createdAt/updatedAt are fixed for the frontend plans"
  - "Missing topic id on PUT/DELETE is a NotFoundException (404), never a silent no-op"

requirements-completed: [INT-01, INT-02, INT-06]

coverage:
  - id: D1
    description: "Profile saved with PUT /api/interest/profile persists in Postgres and reloads via GET; version moves only when the text changes"
    requirement: INT-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#profilePutThenGetPersists"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#savingTheSameProfileTextKeepsTheVersion"
        status: pass
    human_judgment: false
  - id: D2
    description: "Profile limits: 2,000 UTF-16 units accepted, 2,001 rejected with the fixed detail, null rejected, empty clears and bumps the version"
    requirement: INT-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#updateProfileAcceptsExactly2000Characters"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#updateProfileRejects2001Characters"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#updateProfileCountsUtf16Units"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#updateProfileBumpsVersionOnlyWhenTextChanges"
        status: pass
    human_judgment: false
  - id: D3
    description: "Topic limits: 25-topic cap, weight -50..+50 with +20 default, name/description required and length-capped, messages never echo input"
    requirement: INT-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#createTopicRejectsTwentySixth"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#createTopicRejectsWeightOutsideBounds"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#createTopicRejectsBlankOrLongNameAndDescription"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#validationMessagesNeverEchoInput"
        status: pass
    human_judgment: false
  - id: D4
    description: "Topic CRUD over REST with stable ids: 201 create, in-place update with description-only version bump, 204 delete then 404, [] after"
    requirement: INT-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#topicCrudRoundTripsThroughTheDatabase"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#weightOutOfRangeIs400BeforeTheDatabase"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java (nine status-code tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "InterestService.isColdStart(): true only for a blank-after-trim profile and zero topics"
    requirement: INT-06
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java#isColdStartTrueOnlyForBlankProfileAndNoTopics"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 03: Interest Profile & Topic REST API Summary

**`/api/interest` profile and topic CRUD backed by `InterestService`. The service enforces the INT-01/INT-02 limits (2,000 UTF-16 units, 25 topics, weight -50..+50 with a +20 default) with fixed-text 400s, bumps the topic version only when the description changes, and provides the single `isColdStart()` predicate that Phase 4 will call.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-09-23T17:57:13Z
- **Completed:** 2026-09-23T18:02:13Z
- **Tasks:** 3
- **Files modified:** 7 (all new)

## Accomplishments

- A profile saved with PUT is read back by GET through Postgres. The version moves only when the text changes, and an empty text clears the profile.
- Topics can be listed (by id), created (201, +20 default weight, version 1), updated in place (same id) and deleted (204, then 404). Every limit returns a 400 with fixed text before the database is touched.
- `InterestService.isColdStart()` is the single D-05 predicate for plan 03-04 and the Phase 4 SCOR-06 gate.

## Task Commits

1. **Task 1 (tracer): profile REST slice**: `3c819ee` (feat)
2. **Task 2: limits, version rules, cold start**: `50905fe` (test, RED), `7f6a851` (feat, GREEN)
3. **Task 3: topic CRUD routes**: `db4af06` (feat)

## TDD Gate Compliance

- RED `50905fe`: 12 of 17 tests failed. `createTopicRejectsTwentySixth`, `createTopicRejectsWeightOutsideBounds` and `updateAndDeleteMissingTopicThrowNotFound` failed on assertions: they expected IllegalArgumentException or NotFoundException and got the skeleton's UnsupportedOperationException. `check tdd-red-evidence` returned RED_EVIDENCE_OK for each; the JUnit XML report was converted to TAP for the checker. The 5 profile tests passed at RED because the Task 1 tracer had already implemented `updateProfile`, so this was expected and not an unexpected GREEN on new behavior.
- GREEN `7f6a851`: all 17 pass. No refactor commit was needed.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/service/InterestService.java`: limit constants, profile and topic CRUD, version rules, `isColdStart()`
- `src/main/java/org/bartram/myfeeder/controller/InterestController.java`: `/api/interest` profile and topic routes, no try/catch
- `src/main/java/org/bartram/myfeeder/controller/ProfileUpdateRequest.java`: `record ProfileUpdateRequest(String profileText)`
- `src/main/java/org/bartram/myfeeder/controller/TopicRequest.java`: `record TopicRequest(String name, String description, Integer weight)`
- `src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java`: 4 end-to-end tests (MockMvc to Testcontainers Postgres)
- `src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java`: 17 Mockito behavior tests
- `src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java`: 9 WebMvc status-code tests

## Decisions Made

- `isColdStart()` also treats a null stored text as blank. V6 makes `profile_text NOT NULL`, so this is only defensive.
- In `validate`, the name and description length limits are checked after trimming, which matches what is stored.

## Notes for Phase 4

- The SCOR-06 gate calls `InterestService.isColdStart()`. It must not reimplement the predicate.
- Topic versions bump only on description edits (trimmed comparison). Name and weight edits keep the version, so they never invalidate a topic's scores.

## Deviations from Plan

**1. [Rule 3 - Blocking] Signature-only skeletons in the RED commit**
- **Found during:** Task 2 (RED)
- **Issue:** `createTopic`, `updateTopic`, `deleteTopic`, `listTopics` and `isColdStart` did not exist yet, so a test-only RED commit would have failed on compilation (INVALID_RED), not on assertions.
- **Fix:** The RED commit added method signatures that throw `UnsupportedOperationException`. GREEN replaced them.
- **Files modified:** src/main/java/org/bartram/myfeeder/service/InterestService.java
- **Committed in:** 50905fe

**2. Small test additions beyond the plan's named cases**
- `createTopicRejectsBlankOrLongNameAndDescription` also covers a null name and a null description.
- `weightOutOfRangeIs400BeforeTheDatabase` also asserts that `interest_topic` has zero rows after the rejected POST.

---

**Total deviations:** 1 auto-fixed (blocking), plus minor test additions.
**Impact on plan:** None on the product behavior or the REST contract.

## Issues Encountered

None.

## Verification

- `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.controller.*' --tests 'org.bartram.myfeeder.service.*' --tests 'org.bartram.myfeeder.MyfeederApplicationTests'`: BUILD SUCCESSFUL, 168 tests, 0 failures, 0 errors. The full context starts with the new beans.
- Every task's acceptance-criteria grep passed (the controller mapping appears once, it has 4 topic mappings and 0 `catch`, and `new NotFoundException` appears twice).

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- The REST contract is ready for the frontend plans 03-05 and 03-06.
- `isColdStart()` is ready for the 03-04 status endpoint.

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*

## Self-Check: PASSED
