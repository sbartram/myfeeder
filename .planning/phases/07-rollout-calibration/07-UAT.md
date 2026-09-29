---
status: testing
phase: 07-rollout-calibration
source: [07-VERIFICATION.md]
started: 2026-09-29T03:32:43Z
updated: 2026-09-29T03:32:43Z
---

## Current Test

number: 1
name: Prod Priority view and Interests dialog load with a configured rubric
expected: |
  Open http://192.168.44.204/priority, then Settings > Interests. Priority lists scored articles with badges; the Interests dialog shows no 'not configured' notice; reader, feed tree and article list behave as before.
awaiting: user response

## Tests

### 1. Prod Priority view and Interests dialog load with a configured rubric
expected: Open http://192.168.44.204/priority, then Settings > Interests. Priority lists scored articles with badges; the Interests dialog shows no 'not configured' notice; reader, feed tree and article list behave as before.
result: [pending]

### 2. Badge tier colours at 70/22
expected: In the prod Priority and article lists, badges scored 22-39 render neutral, 70+ high, below 22 low. A brief low-colour flash for 22-39 on first paint is the known 70/40 fallback (07-REVIEW IN-01).
result: [pending]

### 3. Judgment-tier prohibitions for 07-06..07-11 hold
expected: Approve-before-release gates, no prod writes by hand, no force-push/--no-verify/disableChecks, no rubric text or key in committed files, no SDK retry / concurrency > 1; 07-10 and 07-11 test-only, no other findings fixed, no push or merge to main. See the Prohibitions table in 07-VERIFICATION.md.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
