---
status: complete
phase: 08-engagement-capture
source: [08-VERIFICATION.md]
started: 2026-09-30T04:20:36Z
updated: 2026-09-30T13:26:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Live Open Original with the backend stopped, plus the Engaged line and Forget
expected: The toolbar button and the o key both open a tab every time, with no toast while the backend is down. The Engaged line appears after an open, and Forget clears it.
result: pass

### 2. Engagement rows accumulate in prod (SC-5 backstop)
expected: After at least a day of real use, the read-only query SELECT kind, count(*) FROM article_engagement GROUP BY kind on prod returns rows for the kinds exercised (OPEN_ORIGINAL, STAR, BOARD, RAINDROP).
result: pass

### 3. The 08-05 release rules held
expected: The user confirms: nothing was published before the approval; there was no force-push, --no-verify, disableChecks or tag rewrite; prod data was not hand-edited; no secret value was printed.
result: pass

### 4. Accept the Raindrop interruption limitation (D-08)
expected: The user accepts that a process stop between createBookmark returning and the RAINDROP row being written loses that one row, with no retry.
result: pass

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
