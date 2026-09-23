---
phase: 03-interest-model-schema-rubric-editor
plan: 04
subsystem: api
tags: [spring-mvc, resilience4j, typesafe-jev, problem-detail, interest-status, topic-preview, testcontainers]

requires:
  - phase: 03-02
    provides: JevApiClient.isConfigured(), InterestQuestions.topic/topicKey/PREVIEW_DRAFT_KEY, ArticleStateBuilder.build/hasJudgeableText
  - phase: 03-03
    provides: InterestService.isColdStart() and MAX_DESCRIPTION_CHARS, InterestApiIntegrationTest
provides:
  - GET /api/interest/status -> {configured, breakerState, coldStart} (D-04, D-05)
  - POST /api/interest/preview -> {noul, model}, one judge() call, nothing persisted (D-12..D-14)
  - GlobalExceptionHandler Jev mappings with fixed-text details (503 not configured / 503 breaker open / 422 rejected / 503 other)
  - CLAUDE.md Interest Ranking section, /api/interest route list, Jev handler mappings, corrected @Table package
affects: [03-05 status notices, 03-07 preview UI, phase-04 scorer and JEV-05 counts, phase-05 Priority view]

actuals:
  tokens: 8224
  tasks: 3
  commits: 5
plan_head_before: 5513ccbcc21f0d9682306ef5a52c5e2c9049572d

tech-stack:
  added: []
  patterns:
    - "Status service reads live beans only: isConfigured(), CircuitBreakerRegistry.circuitBreaker(\"jev\").getState().name(), isColdStart()"
    - "Validate and build the question before the proxied judge() call; no transaction and no service retry around outbound Jev calls"
    - "Jev ProblemDetails carry fixed text only (HTTP status at most), never a TypeSafe message or body"
    - "Doc edits to a file with the user's uncommitted hunks: stage a blob built from HEAD plus only the plan's edits (hash-object + update-index)"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
    - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
    - src/main/java/org/bartram/myfeeder/controller/InterestStatusController.java
    - src/main/java/org/bartram/myfeeder/service/InterestPreviewService.java
    - src/main/java/org/bartram/myfeeder/service/TopicPreviewResponse.java
    - src/main/java/org/bartram/myfeeder/controller/TopicPreviewRequest.java
    - src/main/java/org/bartram/myfeeder/controller/InterestPreviewController.java
    - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
    - src/test/java/org/bartram/myfeeder/service/InterestPreviewServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestPreviewControllerTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
    - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
    - CLAUDE.md

key-decisions:
  - "03-04: /api/interest/status is exactly {configured, breakerState, coldStart}; Phase 4 appends the JEV-05 counts to InterestStatus as NEW components and never renames these three"
  - "03-04: Jev exceptions map to fixed-text ProblemDetails: JevNotConfigured 503 'Jev not configured', CallNotPermitted 503 'Jev unavailable', TypeSafeBadRequest/UnprocessableEntity 422 'Jev rejected the request (HTTP <status>)', other TypeSafeException 503 'Jev request failed'"
  - "03-04: Spring picks the 422 subtype handler over the TypeSafeException catch-all (research A2 confirmed by the WebMvc tests); WR-01 (IllegalArgumentException on the jev ignore list) stays open for Phase 4"

patterns-established:
  - "Status and preview live in their own controllers under /api/interest; Phase 4 extends only InterestStatusService/InterestStatus"
  - "Preview key: topic_draft for an unsaved row, topic_<id> for a saved topic; the response carries the raw noul and model, the client does the D-13 math"

requirements-completed: [INT-04, INT-06]

coverage:
  - id: D1
    description: "GET /api/interest/status returns configured/breakerState/coldStart from the live client, the jev breaker and InterestService.isColdStart(); every breaker state name passes through verbatim"
    requirement: INT-06
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#reportsConfiguredAndColdStartFromTheirSources"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#passesBreakerStateNamesThrough"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#delegatesColdStartToInterestService"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#statusReportsKeylessClosedAndColdStart"
        status: pass
    human_judgment: false
  - id: D2
    description: "POST /api/interest/preview validates before judge(), sends exactly one Noul (topic_draft or topic_<id>) built with the scorer's builders, returns {noul, model}, never retries and has no write collaborators"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestPreviewServiceTest.java (10 tests)"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#previewOfMissingArticleIs404"
        status: pass
    human_judgment: false
  - id: D3
    description: "Jev failures reach the client as distinct fixed-text ProblemDetails (503/503/422/503), and no TypeSafe message or body is echoed"
    requirement: INT-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestPreviewControllerTest.java (9 tests, incl. jevErrorDetailsNeverEchoExceptionText)"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#keylessPreviewIs503ThroughTheFullStack"
        status: pass
    human_judgment: false
  - id: D4
    description: "Committed CLAUDE.md names V6__interest_scoring.sql, /api/interest, the Jev handler mappings and the real @Table package; the user's uncommitted CLAUDE.md hunks are byte-identical and still unstaged"
    verification:
      - kind: other
        ref: "git show HEAD:CLAUDE.md grep gate + diff of claude-md-user-hunks.before.diff vs .after.diff (ignoring index lines): empty"
        status: pass
    human_judgment: false

duration: 7min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 04: Interest Status, Topic Preview and Jev Error Mapping Summary

**`GET /api/interest/status` serves `{configured, breakerState, coldStart}` from the live Jev client, the `jev` Resilience4j breaker and `InterestService.isColdStart()`. `POST /api/interest/preview` judges one draft topic against one article with a single `judge()` call built from the scorer's own builders and persists nothing. Four new `GlobalExceptionHandler` mappings turn Jev failures into distinct fixed-text ProblemDetails (503 not configured, 503 breaker open, 422 rejected, 503 other).**

## Performance

- **Duration:** 7 min
- **Started:** 2026-09-23T18:13:52Z
- **Completed:** 2026-09-23T18:21:07Z
- **Tasks:** 3 (1 tracer, 1 TDD, 1 auto)
- **Files modified:** 13 (10 new, 3 modified)

## Accomplishments

- `InterestStatus(boolean configured, String breakerState, boolean coldStart)` and `InterestStatusService.status()`. The cold-start predicate is delegated, not reimplemented. Breaker state names (CLOSED, OPEN, FORCED_OPEN, HALF_OPEN) pass through verbatim. With no key, the full Spring context reports configured false, CLOSED and cold start true, and cold start becomes false once a profile is saved.
- `InterestPreviewService.preview(articleId, description, topicId)` validates in order (articleId, blank description "Write a description first", 500-char limit, 404 article, "This article has no text to judge"), builds `ArticleStateBuilder.build(feedTitle, article)` and `InterestQuestions.topic(description.trim())` under `topic_draft` or `topic_<id>`, then calls `jevApiClient.judge` once. There is no try/catch, no transaction and no retry of its own. It returns `TopicPreviewResponse(noul, model)`. Its only collaborators are `ArticleRepository`, `FeedRepository` and `JevApiClient`.
- `GlobalExceptionHandler`: `JevNotConfiguredException` gives 503 "Jev not configured" (detail is the app's own fixed message). `CallNotPermittedException` gives 503 "Jev unavailable". `TypeSafeBadRequestException` and `TypeSafeUnprocessableEntityException` give 422 "Jev rejected the request" with detail "Jev rejected the request (HTTP <status>)". Any other `TypeSafeException` gives 503 "Jev request failed". The WebMvc tests confirm that Spring picks the 422 subtype handler over the catch-all (research Assumption A2). A "LEAKCHECK" test confirms no TypeSafe message or body reaches the response.
- The keyless preview returns 503 "Jev not configured" through the full stack, and a missing article returns 404.
- CLAUDE.md: the package structure lists the interest classes, `/api/interest` is among the endpoints, the GlobalExceptionHandler bullet covers the Jev mappings, and both Spring Data JDBC bullets give `@Table` as `org.springframework.data.relational.core.mapping.Table`. A new `## Interest Ranking` section covers V6, the services and builders, the status and preview contracts, and the routes.
- Plan verification: `./gradlew cleanTest test -x npmBuild -x npmInstall` ran 300 tests with 0 failures and 0 errors. The one skip is the gated `JevLiveSmokeTest`.

## Task Commits

1. **Task 1 (tracer): GET /api/interest/status**: `f8e820a` (feat). The tracer gate (interactive, end-of-phase, automated-only verify) re-ran the verify, it passed, and expansion continued.
2. **Task 2: one-call topic preview**: `bf29fa7` (test, RED), `515bd6f` (feat, GREEN)
3. **Task 3: Jev ProblemDetails and docs**: `09432ff` (feat: handlers and tests), `c8a6845` (docs: CLAUDE.md only)

## TDD Gate Compliance (Task 2)

- RED: all ten behavior tests failed on assertions (Mockito `WantedButNotInvoked`, AssertJ `AssertionError`/`AssertionFailedError`) against a compile-only skeleton, a no-arg `InterestPreviewService` whose `preview` returns null. `check tdd-red-evidence` returned RED_EVIDENCE_OK for each of the ten target tests. The JUnit XML was converted to TAP, and the records are in `$HOME/.cache/myfeeder-phase03/red-03-04-*.json`.
- GREEN: 10/10 pass.
- REFACTOR: none needed.

## CLAUDE.md Hunk Comparison

- Before snapshot: the user's four line-neutral hunks at lines 40, 55-56, 99 and 105, all above the "## Spring Boot 4 / Jackson 3.x Notes" heading, so the edit was safe to proceed.
- The edits were applied identically to the working copy and to `git show HEAD:CLAUDE.md` (saved as `CLAUDE.staged.md`). Before staging, `diff CLAUDE.staged.md CLAUDE.md` showed exactly the user's four hunks. The staged blob came from `hash-object -w` plus `update-index --cacheinfo`.
- After the commit, `diff` of the before and after `git diff -U0 -- CLAUDE.md` snapshots (ignoring `index` lines) was empty. The user's hunks are byte-identical and still uncommitted, and no plan edit was left unstaged. The docs commit `c8a6845` contains CLAUDE.md only.

## Files Created/Modified

- `service/InterestStatus.java`: the D-04 status record
- `service/InterestStatusService.java`: status assembly from the three live sources
- `controller/InterestStatusController.java`: `GET /api/interest/status`
- `service/InterestPreviewService.java`: the validate-then-judge preview, with no transaction and nothing persisted
- `service/TopicPreviewResponse.java`: `{noul, model}`
- `controller/TopicPreviewRequest.java`: `{articleId, description, topicId}`
- `controller/InterestPreviewController.java`: `POST /api/interest/preview`
- `controller/GlobalExceptionHandler.java`: the four Jev handlers
- `test/.../service/InterestStatusServiceTest.java`: 3 tests with a real `CircuitBreakerRegistry.ofDefaults()`
- `test/.../service/InterestPreviewServiceTest.java`: 10 Mockito tests
- `test/.../controller/InterestPreviewControllerTest.java`: 9 WebMvc tests
- `test/.../controller/InterestApiIntegrationTest.java`: adds `statusReportsKeylessClosedAndColdStart`, `keylessPreviewIs503ThroughTheFullStack` and `previewOfMissingArticleIs404`. `@BeforeEach` also deletes the preview test feed, and its article cascades.
- `CLAUDE.md`: the documentation edits described above

## Decisions Made

- The RED skeleton had a no-arg constructor, so `previewHasNoWriteCollaborators` also failed on its assertion instead of passing early.
- The integration test's preview feed uses a distinctive URL (`https://example.test/interest-preview-feed.xml`) and is deleted by URL in `@BeforeEach`, so it cannot touch feeds from other tests sharing the cached context.

## Notes for later phases

- **Phase 4:** add the JEV-05 eligible-unscored and failed counts to `InterestStatus` as new record components. Never rename `configured`, `breakerState` or `coldStart`.
- **WR-01 remains open for Phase 4.** `IllegalArgumentException` is still absent from the jev breaker's ignore list. The preview is safe because it validates and builds its question before `judge()`, but the Phase 4 scorer is the heavy caller.
- `JevNotConfiguredException` is already on the jev breaker and retry ignore lists, so keyless previews never open the breaker or burn retries.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Mockito re-stubbing invoked the earlier throwing stub (tests only)**
- **Found during:** Task 2 GREEN (`previewPropagatesJevExceptionsWithoutRetrying`) and Task 3 (`otherTypeSafeFailuresAre503`, `jevErrorDetailsNeverEchoExceptionText`)
- **Issue:** A second `when(mock.method(...)).thenThrow(...)` calls the mock, which fires the previous throwing stub, so the test died before it asserted anything.
- **Fix:** The second and later stubs use `doThrow(...).when(mock).method(...)`.
- **Files modified:** InterestPreviewServiceTest.java, InterestPreviewControllerTest.java
- **Verification:** 10/10 and 9/9 pass.
- **Committed in:** `515bd6f` and `09432ff`

**2. [Process] RED commit includes a compile-only skeleton and the response record**
- **Found during:** Task 2 RED
- **Issue:** A test-only RED commit would fail compilation, which counts as INVALID_RED rather than an assertion failure.
- **Fix:** `bf29fa7` also adds `TopicPreviewResponse` and a no-arg `InterestPreviewService` skeleton whose `preview` returns null. GREEN replaces the skeleton.

**3. [Orchestrator instruction] STATE.md and ROADMAP.md are updated in the plan-metadata commit**
- The plan's prohibition names the orchestrator as the single writer of STATE.md and ROADMAP.md. This sequential run's orchestrator asked the executor to update both, as in 03-01 through 03-05. No task commit touches them, and they are updated only in the final `docs(03-04)` metadata commit. No commit touches `.envrc`, `.claude/CLAUDE.md` or `.planning/config.json`.

---

**Total deviations:** 1 auto-fixed (Rule 1, tests only) plus 2 process notes.
**Impact on plan:** None on behavior or scope.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 03-07 (preview UI) can call `POST /api/interest/preview` and branch on the ProblemDetail title ("Jev not configured", "Jev unavailable", "Jev rejected the request", "Jev request failed") via `ApiError`.
- 03-05's status notices now have a live `/api/interest/status` behind them.
- No blockers.

## Self-Check: PASSED

- All 10 created and 3 modified files exist on disk
- Commits f8e820a, bf29fa7, 515bd6f, 09432ff and c8a6845 exist (5 commits since plan_head_before 5513ccb)
- Full backend suite: 300 tests, 0 failures, 0 errors, 1 gated skip

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*
