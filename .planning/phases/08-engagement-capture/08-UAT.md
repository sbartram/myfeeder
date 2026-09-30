---
status: testing
phase: 08-engagement-capture
source: [08-VERIFICATION.md]
started: 2026-09-30T04:20:36Z
updated: 2026-09-30T04:20:36Z
---

## Current Test

number: 1
name: Live Open Original with the backend stopped, plus the Engaged line and Forget
expected: |
  With an article selected and the backend stopped, clicking "↗ Open Original" and pressing o each open a new tab, with no error toast. With the backend running, the reading pane then shows "Engaged: opened · Forget", and Forget removes the line with no dialog and no toast.
awaiting: user response

## Tests

### 1. Live Open Original with the backend stopped, plus the Engaged line and Forget
expected: The toolbar button and the o key both open a tab every time, with no toast while the backend is down. The Engaged line appears after an open, and Forget clears it.
result: [pending]

### 2. Engagement rows accumulate in prod (SC-5 backstop)
expected: After at least a day of real use, the read-only query SELECT kind, count(*) FROM article_engagement GROUP BY kind on prod returns rows for the kinds exercised (OPEN_ORIGINAL, STAR, BOARD, RAINDROP).
result: [pending]

### 3. The 08-05 release rules held
expected: The user confirms: nothing was published before the approval; there was no force-push, --no-verify, disableChecks or tag rewrite; prod data was not hand-edited; no secret value was printed.
result: [pending]

### 4. Accept the Raindrop interruption limitation (D-08)
expected: The user accepts that a process stop between createBookmark returning and the RAINDROP row being written loses that one row, with no retry.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
