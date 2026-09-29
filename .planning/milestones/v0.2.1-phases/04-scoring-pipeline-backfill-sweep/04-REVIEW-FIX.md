---
phase: 04-scoring-pipeline-backfill-sweep
fixed_at: 2026-09-24T03:35:00Z
review_path: /Users/scottb/orca/workspaces/myfeeder/main/.planning/phases/04-scoring-pipeline-backfill-sweep/04-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 4: Code Review Fix Report

**Fixed at:** 2026-09-24T03:35:00Z
**Source review:** .planning/phases/04-scoring-pipeline-backfill-sweep/04-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (the critical_warning scope covers WR-01..WR-03; IN-01..IN-09 were not attempted)
- Fixed: 3
- Skipped: 0

**Where verification ran:** the main checkout (`/Users/scottb/orca/workspaces/myfeeder/main`, branch `sbartram/main`), as the orchestrator directed. No hand-rolled worktree was created. For each fix, a failing test was written first and then the relevant suites were run. Final gates: `./gradlew test` passed (389 tests, 2 skipped, 0 failures); `npx vitest run` passed (17 files, 119 tests); `npx tsc -b` was clean.

## Fixed Issues

### WR-01: Re-score does not re-judge an article that is mid-call; it is stored write-once with the replaced rubric

**Files modified:** `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java`, `src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java`
**Commit:** 00ca174
**Status:** fixed: requires human verification (logic change)
**Applied fix:** Once `judge()` returns, the scorer re-reads the profile and topics and compares two things against the pre-call snapshot: the profile `version` and the topic `id -> version` map. If either changed, it writes nothing (no SCORED row, no FAILED attempt), and the sweep picks the article up again under the current rubric. The check runs after the judge try/catch and before `isValid`, which is where the review placed it.

Versions go up only when judged text changes, so an edit to just a topic's name or weight keeps the result. The new tests `profileSavedMidCallDiscardsTheResultForTheSweep`, `topicEditedAddedOrDeletedMidCallDiscardsTheResultForTheSweep` and `weightOnlyTopicEditMidCallStillStoresTheScore` cover these cases.

Residual: a race window of a few milliseconds remains between the re-read and `writeScored`, compared with roughly 90s before. The review's alternative, a SQL guard inside `writeScored`, would close it completely, but it would also have to check topic versions. I kept the simpler service-level check.

### WR-02: `POST /api/interest/rescore` is a bodyless destructive POST, so any website can trigger it cross-site and cause re-billing

**Files modified:** `src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java`, `src/main/java/org/bartram/myfeeder/controller/RescoreRequest.java` (new), `src/main/frontend/src/api/interest.ts`, `src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java`, `src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java`, `src/main/frontend/src/components/InterestsDialog.test.tsx`
**Commit:** e67b696
**Applied fix:** The POST now has `consumes = application/json` and a `@RequestBody RescoreRequest(boolean confirm)`:
- A body-less, form-encoded or `text/plain` POST gets 415 from Spring's default resolver.
- `{}` or `{"confirm": false}` gets 400 through `IllegalArgumentException`.
- None of these reach `service.rescore()`.

The SPA now sends `{ confirm: true }`. I created `RescoreRequest.java` because the fix requires a request DTO; it follows the existing `*Request` record convention in `controller/`. The existing tests were updated to send the JSON body. The dialog test now asserts the body is `{"confirm":true}` instead of undefined.

Follow-up for the orchestrator: the root `CLAUDE.md` Interest Ranking "Routes" line could note that `POST /rescore` needs `{"confirm": true}`. I did not edit `CLAUDE.md` because it already had unrelated uncommitted changes.

### WR-03: The status poll never stops when nothing can drain (unconfigured, cold start, breaker open)

**Files modified:** `src/main/frontend/src/hooks/useInterest.ts`, `src/main/frontend/src/components/InterestsDialog.test.tsx`
**Commit:** 81dd8c7
**Applied fix:** `refetchInterval` now polls every 15s only when `configured && !coldStart && eligibleUnscored > 0`. This is the same gate as the dialog's "N waiting" line. I updated the hook's docstring to match. The new test `statusDoesNotPollWhenNothingCanDrain` covers `{configured: false, eligibleUnscored: 5}` and `{coldStart: true, eligibleUnscored: 5}` and asserts that only one fetch happens after advancing 15s.

Breaker OPEN is deliberately still polled, as in the review's suggested fix. An open breaker recovers by itself (HALF_OPEN, then CLOSED), and polling lets the waiting line drain live once it does.

---

_Fixed: 2026-09-24T03:35:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
