---
status: testing
phase: 02-jev-client-foundation
source: [02-VERIFICATION.md]
started: 2026-09-23T03:45:00Z
updated: 2026-09-23T03:45:00Z
---

## Current Test

number: 1
name: SC2 live smoke — a real Jev call returns a judgment from jev-1.13.0
expected: |
  In a shell with your TypeSafe key:
  JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=... ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info
  Reports 1 test PASSED (not skipped), prints model=jev-1.13.0 with a requestId and token counts, and the key appears nowhere in the output.
  (The smoke test bypasses retry/breaker wiring — a single transient 429/5xx fails it; just re-run.)
awaiting: user response

## Tests

### 1. SC2 live smoke — a real Jev call returns a judgment from jev-1.13.0
expected: JEV_LIVE_SMOKE=true run of JevLiveSmokeTest passes (not skipped), prints model=jev-1.13.0, requestId and token counts; key never printed
result: [pending]

### 2. Concurrent judge() safety (plan 02-02)
expected: Accept by inspection — JevApiClientImpl holds only final fields and copies state per call — or defer a concurrency test to Phase 4
result: [pending]

### 3. Rolling update on a key-only change (plan 02-04)
expected: On the first deploy that sets MYFEEDER_TYPESAFE_API_KEY (Phase 7), `kubectl -n myfeeder rollout status deploy/myfeeder` shows the pod rolling and the new pod starting cleanly
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
