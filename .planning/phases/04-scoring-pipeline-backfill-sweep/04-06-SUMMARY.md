---
phase: 04-scoring-pipeline-backfill-sweep
plan: 06
subsystem: api
tags: [interest-status, jev, spring-boot, testcontainers, postgres]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "04-02 ArticleScoreStore.counts(Instant) / ScoreCounts and MyfeederProperties.Interest.eligibilityCutoff()"
provides:
  - "GET /api/interest/status returns {configured, breakerState, coldStart, eligibleUnscored, failed}"
  - "InterestStatus record components eligibleUnscored and failed (D-11/D-12 semantics)"
affects: [04-07, 05-priority-view]

actuals:
  tokens: 1492
  tasks: 1
  commits: 1
plan_head_before: 64b01ae8bc736f90e3e4a49bbe6675ebe64e3a0c

tech-stack:
  added: []
  patterns:
    - "Status counts are read once per status() call from the single ArticleScoreStore.counts statement, so the two numbers are one consistent snapshot"
    - "Integration count assertions against the shared Spring context and Postgres are deltas over a baseline read in the same test"

key-files:
  created: []
  modified:
    - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
    - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
    - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java

key-decisions:
  - "The status JSON fields are named eligibleUnscored and failed. They are appended after configured, breakerState and coldStart, which keep their names and order"
  - "There is no lastError or auth-failure field. breakerState remains the only failure signal (D-08)"

patterns-established:
  - "InterestStatus evolution: append components only, never rename or reorder existing ones"

requirements-completed: [JEV-05]

coverage:
  - id: D1
    description: "GET /api/interest/status appends eligibleUnscored and failed. The first three fields are unchanged, and there is no failure-detail field"
    requirement: JEV-05
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#reportsEligibleUnscoredAndFailedCounts"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#reportsConfiguredAndColdStartFromTheirSources"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#statusReportsKeylessClosedAndColdStart"
        status: pass
    human_judgment: false
  - id: D2
    description: "Counts follow D-11/D-12 through the full keyless stack. A FAILED row still being retried counts as eligibleUnscored, an exhausted one counts as failed, and read or aged-out articles are excluded. The cutoff is now minus 14 days"
    requirement: JEV-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#statusCountsEligibleUnscoredAndExhaustedFailures"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#countsUseTheEligibilityWindow"
        status: pass
    human_judgment: false

duration: 2min
completed: 2026-09-24
status: complete
---

# Phase 4 Plan 06: Interest Status Counts Summary

**`GET /api/interest/status` now returns `eligibleUnscored` and `failed`. Both come from the one `ArticleScoreStore.counts` statement over the 14-day eligibility window. A keyless full-stack test checks them as deltas over a baseline for six seeded articles: no score row, retrying FAILED, exhausted FAILED, SCORED, read, and aged-out.**

## Performance

- **Duration:** 2 min
- **Started:** 2026-09-24T01:46:07Z
- **Completed:** 2026-09-24T01:48:06Z
- **Tasks:** 1
- **Files modified:** 4

## Final `/api/interest/status` contract (consumed by 04-07 and Phase 5)

`GET /api/interest/status` returns 200 with:

```json
{ "configured": false, "breakerState": "CLOSED", "coldStart": true, "eligibleUnscored": 0, "failed": 0 }
```

- `configured` (boolean): a TypeSafe key is set. Unchanged.
- `breakerState` (string): the "jev" circuit breaker state name, passed through verbatim. Unchanged.
- `coldStart` (boolean): `InterestService.isColdStart()`. Unchanged.
- `eligibleUnscored` (number): eligible articles (unread, and `COALESCE(published_at, fetched_at)` inside the window) that have no `article_score` row, or a FAILED row with fewer than 3 attempts (D-11, D-12).
- `failed` (number): eligible articles whose FAILED row has used all 3 attempts (D-11).
- Both counts come from one SQL statement, so they are consistent with each other. They can change between two reads. There is no `lastError` or auth-failure field (D-08).

Java: `record InterestStatus(boolean configured, String breakerState, boolean coldStart, long eligibleUnscored, long failed)`.

## Accomplishments
- I appended two components to `InterestStatus` and kept the first three exactly as they were.
- `InterestStatusService.status()` calls `store.counts(properties.getInterest().eligibilityCutoff())` once. It is still the only place that constructs `InterestStatus`.
- Two new unit tests cover count pass-through (312 and 4) and check that the cutoff is within 5 seconds of now minus 14 days.
- The keyless full-stack test `statusCountsEligibleUnscoredAndExhaustedFailures` asserts eligibleUnscored = baseline + 2 and failed = baseline + 1.

## Task Commits

1. **Task 1 (tracer): status reports eligible-unscored and failed counts.** `69762b9` (feat)

Tracer feedback gate: the tracer's verify steps are automated only and the mode is end-of-phase. I re-ran them after the commit and they passed. This plan has no expansion tasks.

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/service/InterestStatus.java`: two new record components; the Javadoc now defines them
- `src/main/java/org/bartram/myfeeder/service/InterestStatusService.java`: new `ArticleScoreStore` and `MyfeederProperties` dependencies; reads the counts once
- `src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java`: new constructor, lenient `counts` stub, 2 new tests
- `src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java`: `COUNTS_FEED_URL` cleanup in `@BeforeEach`, the delta count test and seeding helpers

## Decisions Made
- I kept the field names `eligibleUnscored` and `failed` exactly as the planner recorded them.

## Deviations from Plan

None. The plan was executed exactly as written.

## Verification

- `./gradlew test -x npmBuild -x npmInstall --tests '...InterestStatusServiceTest' --tests '...InterestApiIntegrationTest'`: 5/5 and 8/8 pass
- The JUnit XML check for all 7 named tests passed, with no failure or error elements
- Full backend suite `./gradlew test -x npmBuild -x npmInstall`: 353 tests, 0 failures, 0 errors, 2 skipped (both skips predate this plan)
- Acceptance greps: the record signature appears 1 time, `store.counts(` 1 time, `new InterestStatus(` 1 time in src/main, and `lastError` 0 times

## Issues Encountered
None

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- 04-07 can read `eligibleUnscored` and `failed` from `/api/interest/status`.
- JEV-05 is not marked complete in REQUIREMENTS.md yet. `requirements.ready-ids` reported 0/1 ready, because 04-07 and 04-08 also declare it and have no SUMMARY yet.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED
