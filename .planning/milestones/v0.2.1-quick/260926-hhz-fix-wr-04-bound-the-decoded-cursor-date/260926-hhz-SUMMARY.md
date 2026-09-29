---
phase: quick-260926-hhz
plan: 01
subsystem: priority-pagination
status: complete
tags: [priority, cursor, pagination, wr-04, tdd]
requires: [PriorityPage served-tuple cursor (05-08)]
provides: [decodeCursor rejects dates outside 0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z]
affects: [GET /api/articles/priority]
tech-stack:
  added: []
  patterns: [decode-time bound on untrusted cursor fields, same fixed-text 404 as every unreadable cursor]
key-files:
  created: []
  modified:
    - src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
    - src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
decisions:
  - "Out-of-range cursor dates are rejected at decode time with NotFoundException(UNREADABLE_CURSOR); no clamp and no DataAccessException mapping (per 05-REVIEW WR-04)"
  - "WR-04's 500 does not reproduce through JDBC: pgjdbc 42.7.13 binds an OffsetDateTime outside its range as -infinity/infinity, so the old behavior was a 200 served against a tuple the server never emitted; the fix is the same"
requirements: [PRIO-06]
metrics:
  duration: 3m
  completed: 2026-09-26
actuals:
  tokens: 2200
  tasks: 1
  commits: 2
plan_head_before: 31fc0f3a0a21570a6a991e5675939e20e71e910e
---

# Quick 260926-hhz Plan 01: Bound the decoded Priority cursor date (WR-04) Summary

`PriorityPage.decodeCursor` now rejects any decoded date outside `0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z` (both edges inclusive) with the existing fixed-text `NotFoundException("Priority cursor not recognized")`. A crafted cursor therefore gets the R4 restart 404 and never reaches `PriorityService` or SQL.

## Commits

| Step | Commit | Message |
|------|--------|---------|
| RED | 508cc90 | test(260926-hhz): out-of-range Priority cursor date is not the 404 (WR-04) |
| GREEN | 5c6c642 | fix(260926-hhz): bound the decoded Priority cursor date to years 1..9999 (WR-04) |

## TDD Gate Compliance

**RED evidence** (the plan's verify command at 508cc90, before the fix: 37 tests ran, 4 failed, and every failure was an assertion):
- `PriorityPageTest.unreadableCursorIsNotFound`: `[cursor 'MXwtOTIyMzM3MjAzNjg1NDc3NTgwOHwx'] Expecting actual not to be null`. That cursor is `1|Long.MIN_VALUE|1`; it decoded to a SortKey instead of throwing.
- `PriorityPageTest.cursorDateOneMicrosecondOutsideTheBoundsIsNotFound`: `[cursor dated 0000-12-31T23:59:59.999999Z] Expecting actual not to be null`.
- `PriorityPageTest.cursorDateBoundsAreInclusive`: passed, as intended. It guards against a bound that is too tight.
- `ArticleControllerTest.priorityOutOfRangeCursorDateIs404WithoutCallingTheService`: `Status expected:<404> but was:<200>`.
- `PriorityApiIntegrationTest.outOfRangeCursorDateIs404`: `Status expected:<404> but was:<200>`. See Deviations: this was a 200, not the "timestamp out of range" exception the plan predicted.

**GREEN** (5c6c642): the same verify command passes. PriorityPageTest has 7 tests, ArticleControllerTest 24 and PriorityApiIntegrationTest 6: 37 in total, with 0 failures, 0 errors and 0 skipped.

## Verification

- `./gradlew test --tests "org.bartram.myfeeder.controller.PriorityPageTest" --tests "org.bartram.myfeeder.controller.ArticleControllerTest" --tests "org.bartram.myfeeder.controller.PriorityApiIntegrationTest"` failed before the fix (37 tests, 4 failed) and passed after it (37 tests, 0 failed).
- `./gradlew test --tests "org.bartram.myfeeder.controller.*" --tests "org.bartram.myfeeder.service.PriorityServiceTest" --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` passed: 17 classes, 125 tests, 0 failures, 0 errors, 0 skipped. This includes cursorRoundTripsExactly, cursorWireFormatIsPinned, the ranked walk and the score-drop walk.
- Acceptance greps all pass. Both date literals are in PriorityPage.java, Long.MIN_VALUE and Long.MAX_VALUE are in PriorityPageTest, and each new test name appears once. The test commit sits directly before the fix commit.

## Deviations from Plan

**1. [Finding] The WR-04 reproduction is a 200, not a 500**
- **Found during:** RED run.
- **Issue:** The plan (and 05-REVIEW) expected `perform()` to throw a "timestamp out of range" DataAccessException. Against real Postgres, the request returned 200 instead. The review checked a psql literal, but the app binds through pgjdbc 42.7.13. `TimestampUtils.toString(OffsetDateTime)` sends dates after `MAX_OFFSET_DATETIME` as `infinity` and dates before `MIN_OFFSET_DATETIME` (4713 BC) as `-infinity`. I confirmed this with `javap` on the driver. So a `Long.MIN_VALUE` cursor was silently compared against `(1.0, -infinity, id)` and served a page for a tuple the server never emitted, with no R4 restart. `Long.MAX_VALUE` micros is about 294247 AD, which Postgres can hold.
- **Impact:** None on the fix. Decode-time rejection gives the documented fixed-text 404 either way. The RED still failed on the intended status assertion (404 expected, 200 actual).
- **Adjustment:** The RED commit message says "is not the 404" rather than "is a 500". The decodeCursor Javadoc describes the real mechanism (the driver binds `-infinity`/`infinity`) instead of saying it "would fail in SQL as a 500".

Otherwise the plan was executed as written.

## Known Stubs

None.

## Threat Flags

None. T-hhz-01 and T-hhz-02 are mitigated as planned. T-hhz-03 is accepted as planned.

## Self-Check: PASSED

- All 4 modified files exist.
- Commits 508cc90 and 5c6c642 are in `git log`, in RED-then-GREEN order.
