---
status: testing
phase: 12-calibration-release
source: [12-VERIFICATION.md]
started: 2026-10-02T23:34:04Z
updated: 2026-10-02T23:34:04Z
---

## Current Test

number: 1
name: SC-3 second clause — engaged articles in prod show engagement in badges and "Why N?"
expected: |
  Once at least one engaged, SCORED, unvoted article matches a non-negative topic above noul 0.5, re-running
  $HOME/.cache/myfeeder-phase12/sc3.sh shows the replay badge equal to the API interestScore on every id, and at
  least one id's interestBreakdown has a TOPIC row with engagementWeight > 0 ("+x.x engaged" in "Why N?").
  Not observable today (engagementLearned 0.0 on all 10 topics); alternatively accept via the override in 12-VERIFICATION.md.
awaiting: user response

## Tests

### 1. SC-3 second clause — engaged articles in prod show engagement in badges and "Why N?"
expected: Replay badge equals API interestScore on every id, and at least one id has a TOPIC breakdown row with engagementWeight > 0 (or accept the deviation via the 12-VERIFICATION.md override)
result: [pending]

### 2. Backstop (12-05): replay ~1 day after the 2026-10-02T22:43Z deploy at 100:70:22:0.25:0.5:8 and 100:70:22:0.25:0.5:0
expected: |high_pct(cap 8) - high_pct(cap 0)| <= 5.0
result: [pending]

### 3. Judgment-tier prohibitions (12-02 to 12-05) hold
expected: No prod Postgres write, no backfill row, no tier or near-miss tuning, no Helm/env blend override, no force-push or tag move, no secret printed or committed
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
