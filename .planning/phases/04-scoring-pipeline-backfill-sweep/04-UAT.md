---
status: testing
phase: 04-scoring-pipeline-backfill-sweep
source: [04-VERIFICATION.md]
started: 2026-09-24T02:36:01Z
updated: 2026-09-24T02:36:01Z
---

## Current Test

number: 1
name: Live-key end-to-end scoring (04-05)
expected: |
  With MYFEEDER_TYPESAFE_API_KEY set, ./gradlew bootTestRun, and a profile or at least one topic saved:
  (1) within ~3 min of startup GET /api/interest/status shows eligibleUnscored falling toward 0 and article_score rows appear with status SCORED, model jev-1.13.0 and a request id;
  (2) a feed refresh that brings new articles produces rows for them without waiting for the sweep;
  (3) Interests -> Re-score unread shows a count, confirming resets it, and the "N waiting to be scored" line drains back to 0;
  (4) poll logs show no new feed errors while scoring runs.
awaiting: user response

## Tests

### 1. Live-key end-to-end scoring (04-05)
expected: Backlog drains to 0 with SCORED rows (model + request id); new arrivals scored without waiting for the sweep; Re-score resets and re-drains; no new feed errors in poll logs.
result: [pending]

### 2. Re-score footer layout across the 6 themes (04-07)
expected: Re-score row, inline confirmation ("Counting articles…" then count), "N articles waiting to be scored" line, disabled-reason tooltips and 409 error copy all read correctly and match each theme.
result: [pending]

### 3. Re-score with edits typed after the confirmation opens (04-07)
expected: Decide whether this is acceptable — the button is disabled while edits are unsaved (D-04, tested), but once the confirmation is open, new unsaved edits do not block the POST; the reset re-judges against the saved rubric. Accept, or file a fix.
result: [pending]

### 4. Concurrent score writes for one article (04-02)
expected: Decide whether to accept without a test — the "no duplicate row / no FK error under concurrent writes" claim rests on Postgres ON CONFLICT plus the single jev-score thread; only sequential repeated writes are tested. Accept, or request a concurrent-writer test.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
