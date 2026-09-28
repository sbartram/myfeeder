---
phase: 04-scoring-pipeline-backfill-sweep
plan: 08
subsystem: api
tags: [spring-mvc, rest, interest-ranking, rescore, testcontainers, mockito, webmvctest]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-02 ArticleScoreStore.countRescoreScope / deleteRescoreScope (shared RESCORE_SCOPE), MyfeederProperties.Interest"
provides:
  - "record RescoreCount(long count, int windowDays)"
  - "InterestRescoreService.count() (unguarded) and rescore() (D-18 guard, 409 fixed text)"
  - "GET /api/interest/rescore and POST /api/interest/rescore"
affects: [04-07]

actuals:
  tokens: 4300
  tasks: 2
  commits: 2
plan_head_before: 39c79ef7d24dc415295599306d6cbbd30092d410

tech-stack:
  added: []
  patterns:
    - "One controller per /api/interest concern (status, preview, rescore)"
    - "Destructive endpoint guarded by IllegalStateException -> 409 'Configuration error'; the read-only count is not guarded"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/RescoreCount.java
    - src/main/java/org/bartram/myfeeder/service/InterestRescoreService.java
    - src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java
    - src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java
  modified: []

key-decisions:
  - "The GET count is unguarded; only the destructive POST enforces D-18 (unconfigured or cold start -> 409)"
  - "RescoreCount carries windowDays so the dialog copy follows myfeeder.interest.window-days"
  - "No @Transactional on rescore(): the delete is one atomic statement"

patterns-established:
  - "Re-score confirmation count and delete go through the same store scope (D-02)"

requirements-completed: [INT-05]

coverage:
  - id: D1
    description: "GET /api/interest/rescore returns the exact in-scope count (unread in-window SCORED + FAILED, SKIPPED and read excluded) with windowDays 14 through the full stack"
    requirement: INT-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java#rescoreCountIsServedAndKeylessResetIs409"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java#countUsesTheRescoreScope"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java#getReturnsCountAndWindow"
        status: pass
    human_judgment: false
  - id: D2
    description: "POST /api/interest/rescore deletes the scope and returns the rows deleted when Jev is configured and not in cold start"
    requirement: INT-05
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java#rescoreDeletesTheScope"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java#postReturnsTheResetCount"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-18 guard: POST is 409 'Configuration error' with fixed detail when unconfigured or in cold start, and deletes nothing or changes no read flag; the GET count is never guarded"
    requirement: INT-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java#rescoreCountIsServedAndKeylessResetIs409"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java#rescoreRefusesWhenNotConfigured"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java#rescoreRefusesInColdStart"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java#countIsNotGuarded"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java#postWhenItCannotRescoreIs409"
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 08: Re-score Endpoints Summary

**`GET|POST /api/interest/rescore` on top of the shared `RESCORE_SCOPE`: the GET returns the exact count of in-window unread SCORED/FAILED score rows with the window length, and the POST deletes exactly that scope. The POST refuses with a fixed-text 409 when Jev is unconfigured or in cold start, so no score is deleted that nothing could rebuild.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-09-24T01:50:05Z
- **Completed:** 2026-09-24T01:52:44Z
- **Tasks:** 2
- **Files modified:** 6 (all new)

## Accomplishments
- `InterestRescoreService.count()` calls `ArticleScoreStore.countRescoreScope(cutoff)`. `rescore()` calls `deleteRescoreScope(cutoff)` after the D-18 guard. Both use `properties.getInterest().eligibilityCutoff()`, so the count and the delete describe the same rows (D-02, D-03).
- `InterestRescoreController` has one GET and one POST with no request body and no try/catch. Errors flow to `GlobalExceptionHandler`.
- A keyless full-stack test seeds four articles (unread SCORED, unread exhausted FAILED, unread SKIPPED, read SCORED) and a topic-score row. It proves the GET count is baseline + 2 with `windowDays` 14, and that the POST is 409 and leaves every score row, the topic row and every read flag unchanged.
- Unit tests cover what the keyless stack can't reach: the configured reset, the cold-start refusal, the unguarded count and the 409 mapping.

## Final Re-score REST contract (consumed by 04-07)

- `GET /api/interest/rescore` → 200 `{"count": number, "windowDays": number}`. `count` = the rows Re-score would reset. Never guarded.
- `POST /api/interest/rescore` (no body) → 200 `{"count": number, "windowDays": number}`. `count` = the rows actually deleted, and it is authoritative if rows changed since the GET.
- `POST` when Jev is not configured or `InterestService.isColdStart()` → 409 ProblemDetail, `title` "Configuration error", `detail` "Re-score needs a TypeSafe API key and a profile or at least one topic". Nothing is deleted.
- Scope: unread articles inside `myfeeder.interest.window-days` (default 14) that have a SCORED or FAILED `article_score` row (exhausted FAILED included). SKIPPED rows, read articles and out-of-window articles are untouched, and topic-score rows cascade. After a reset the sweep re-scores; nothing kicks it immediately.

## Task Commits

1. **Task 1 (tracer): Re-score count and reset endpoints through the full stack.** `94417a5` (feat)
2. **Task 2: Service and controller unit tests for the guard, reset count and 409 mapping.** `eb94b62` (test)

Tracer gate: the run was interactive with `human_verify_mode` end-of-phase and only automated verify steps, so I re-ran the tracer verify on the committed state. It passed, and Task 2 went ahead.

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/service/RescoreCount.java`: the response record
- `src/main/java/org/bartram/myfeeder/service/InterestRescoreService.java`: count and guarded reset over the shared scope
- `src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java`: GET and POST `/api/interest/rescore`
- `src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java`: keyless full-stack test (1 method)
- `src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java`: Mockito tests (5 methods)
- `src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java`: `@WebMvcTest` tests (3 methods)

## Decisions Made
None beyond the planner's recorded decisions (unguarded GET, `windowDays` in the response, no transaction on a single-statement delete).

## Deviations from Plan

None. The plan executed exactly as written, and Task 2's tests did not force any production fix.

## Verification

- Plan verification: `InterestRescoreApiIntegrationTest`, `InterestRescoreServiceTest` and `InterestRescoreControllerTest` all pass (1 + 5 + 3 tests).
- Full backend suite `./gradlew test -x npmBuild -x npmInstall`: 362 tests, 0 failures, 0 errors, 2 skipped (the skips predate this plan).
- Acceptance greps: one `@GetMapping("/rescore")`, one `@PostMapping("/rescore")`, no `@RequestBody`. The service has one each of `isColdStart()`, `isConfigured()`, `deleteRescoreScope(` and `countRescoreScope(`. The service test has 2 `never()`.

## Issues Encountered
None

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- 04-07 (the frontend Re-score footer and the CLAUDE.md route line) can build against the contract above.
- INT-05 is not yet marked complete in REQUIREMENTS.md. `requirements.ready-ids` reported 0/1 ready, because sibling plan 04-07 also declares it and has no SUMMARY yet.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED
