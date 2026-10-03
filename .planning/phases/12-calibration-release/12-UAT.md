---
status: complete
phase: 12-calibration-release
source: [12-VERIFICATION.md]
started: 2026-10-02T23:34:04Z
updated: 2026-10-03T00:24:00Z
---

## Current Test

[testing complete]

## Tests

### 1. SC-3 second clause — engaged articles in prod show engagement in badges and "Why N?"
expected: Replay badge equals API interestScore on every id, and at least one id has a TOPIC breakdown row with engagementWeight > 0 (or accept the deviation via the 12-VERIFICATION.md override)
result: pass

### 2. Backstop (12-05): replay ~1 day after the 2026-10-02T22:43Z deploy at 100:70:22:0.25:0.5:8 and 100:70:22:0.25:0.5:0
expected: |high_pct(cap 8) - high_pct(cap 0)| <= 5.0
result: pass

### 3. Judgment-tier prohibitions (12-02 to 12-05) hold
expected: No prod Postgres write, no backfill row, no tier or near-miss tuning, no Helm/env blend override, no force-push or tag move, no secret printed or committed
result: pass

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
