---
status: testing
phase: 04-scoring-pipeline-backfill-sweep
source: [04-VERIFICATION.md]
started: 2026-09-24T02:36:01Z
updated: 2026-09-24T22:08:00Z
---

## Current Test

number: 1
name: Live-key end-to-end scoring (04-05) — re-run after gap-closure plan 04-09
expected: |
  Export only the real MYFEEDER_TYPESAFE_API_KEY (no SPRING_AI_TYPESAFE_* or MYFEEDER_INTEREST_* overrides) and run ./gradlew bootTestRun. The startup log shows `The following 1 profile is active: "dev"` and no keyless TypeSafe INFO line. Save a profile or at least one topic; within ~3 min /api/interest/status eligibleUnscored drains to 0 with SCORED rows (model jev-1.13.0, request id); new arrivals are scored without waiting for the sweep; Re-score unread resets and re-drains; no new feed errors.
awaiting: user response

## Tests

### 1. Live-key end-to-end scoring (04-05)
expected: Backlog drains to 0 with SCORED rows (model + request id); new arrivals scored without waiting for the sweep; Re-score resets and re-drains; no new feed errors in poll logs.
result: [pending]
previous_result: issue (before 04-09; see G-04-1)
previous_reported: "I see this in the local log: `TypeSafeConfig : TypeSafe Jev not configured; interest scoring disabled` ... the UI says \"30 articles waiting to be scored\" but nothing else is happening ... [after restarting with SPRING_AI_TYPESAFE_API_KEY, SPRING_AI_TYPESAFE_BASE_URL and MYFEEDER_INTEREST_SWEEP_INITIAL_DELAY overrides] I saw the unscored number go to 30 and then to 0 with no errors (or any logs at all)"
severity: major
note: "Live behavior verified once overridden: 30 SCORED rows, model jev-1.13.0, 30/30 with request id, breaker CLOSED, failed 0; new arrivals scored on ingest (30->28 before the sweep ran). The documented setup is what fails: under bootTestRun the test application.yaml shadows the main one, so MYFEEDER_TYPESAFE_API_KEY is never bound, base-url is 127.0.0.1:9 and sweep-initial-delay is PT1H. Re-score reset/drain (part 3) not separately exercised."

### 2. Re-score footer layout across the 6 themes (04-07)
expected: Re-score row, inline confirmation ("Counting articles…" then count), "N articles waiting to be scored" line, disabled-reason tooltips and 409 error copy all read correctly and match each theme.
result: pass

### 3. Re-score with edits typed after the confirmation opens (04-07)
expected: Decide whether this is acceptable — the button is disabled while edits are unsaved (D-04, tested), but once the confirmation is open, new unsaved edits do not block the POST; the reset re-judges against the saved rubric. Accept, or file a fix.
result: pass

### 4. Concurrent score writes for one article (04-02)
expected: Decide whether to accept without a test — the "no duplicate row / no FK error under concurrent writes" claim rests on Postgres ON CONFLICT plus the single jev-score thread; only sequential repeated writes are tested. Accept, or request a concurrent-writer test.
result: pass

## Summary

total: 4
passed: 3
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps

- gap_id: G-04-1
  truth: "With MYFEEDER_TYPESAFE_API_KEY set and ./gradlew bootTestRun, Jev is configured and the backlog sweep drains within ~3 min of startup"
  status: failed
  reason: "User reported: TypeSafe Jev not configured under bootTestRun; 30 articles waiting and nothing happening until restarted with SPRING_AI_TYPESAFE_API_KEY / SPRING_AI_TYPESAFE_BASE_URL / MYFEEDER_INTEREST_SWEEP_INITIAL_DELAY overrides, after which it drained 30 -> 0 with no errors"
  severity: major
  test: 1
  fix_plan: 04-09 (commits 28d64fb, 79492fe, 0ecce98, 15a36d2) — awaiting live re-test
  root_cause: "Under ./gradlew bootTestRun the classpath is test output first, so src/test/resources/application.yaml shadows the main application.yaml entirely (Spring loads only the first classpath:/application.yaml). The test yaml is deliberately offline: no spring.ai.typesafe.api-key placeholder (MYFEEDER_TYPESAFE_API_KEY binds to nothing), base-url http://127.0.0.1:9, sweep-initial-delay PT1H. Also silently lost: spring.http.clients connect/read timeouts (5s/30s) and spring.application.name. Compounded by the 04-05 human-check (and 03-07/03-VERIFICATION) prescribing bootTestRun for live-key checks."
  artifacts:
    - path: "src/test/resources/application.yaml"
      issue: "Only config loaded by bootTestRun; offline-by-design values (no key, 127.0.0.1:9, PT1H) are wrong for a live dev run"
    - path: "src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java"
      issue: "bootTestRun entry point activates no dev-specific config"
    - path: "src/main/resources/application.yaml"
      issue: "Live settings (key placeholder, PT1M, http timeouts, app name) never load under bootTestRun; relies on the starter default base-url"
    - path: ".planning/phases/04-scoring-pipeline-backfill-sweep/04-05-PLAN.md"
      issue: "Human-check prescribes bootTestRun + MYFEEDER_TYPESAFE_API_KEY, which cannot enable Jev"
  missing:
    - "A supported live-Jev local run: bootTestRun (or its documented replacement) binds the TypeSafe key, the real base-url, a PT1M initial sweep delay and the 5s/30s http timeouts"
    - "Tests stay offline: no @SpringBootTest context can bind a real key, reach the real Jev base-url, or sweep during the suite (TypeSafeConfigTest.testYamlMirrorsMainTypeSafePinsWithoutKey and mirror tests keep passing or are updated to guard the new setup)"
    - "Live-key check wording updated (04-05 human-check / CLAUDE.md dev workflow) to the working procedure"
  fix_hint: "NON-BINDING: dev-only profile — src/test/resources/application-dev.yaml activated by TestMyfeederApplication via .withAdditionalProfiles(\"dev\"), plus a guard test that its values match main and no test activates it. Alternatives in the debug session (rename to application-test.yaml; document bootRun)."
  debug_session: .planning/debug/boottestrun-live-jev-unconfigured.md
