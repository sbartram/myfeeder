---
phase: "7"
slug: "rollout-calibration"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-27"
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest 4 + RTL (frontend) |
| **Config file** | `build.gradle.kts` (`tasks.withType<Test>`), `src/main/frontend/vitest.config.ts` |
| **Quick run command** | `./gradlew test -x npmBuild -x npmInstall --tests "*InterestStatus*" --tests "*DevProfileConfigTest" --tests "*InterestCalibrationReplaySqlTest" --tests "*JevResilienceTest"`; `cd src/main/frontend && npx vitest run src/utils/interest.test.ts src/components/InterestBadge.test.tsx` |
| **Full suite command** | `./gradlew test && cd src/main/frontend && npm test && npx tsc -b` |
| **Estimated runtime** | ~300 seconds (full), ~60 seconds (quick) |

---

## Sampling Rate

- **After every task commit:** Run the quick command for the touched side
- **After every plan wave:** Run the full suite command
- **Before `/gsd-verify-work`:** Full suite must be green, plus ops evidence files (`07-BACKFILL.md`, `07-CALIBRATION.md`)
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

Backend commands use the proven prefix `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall` (shortened to `GT` below).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-T1 | 07-01 | 1 | OPS-02 | T-07-03 | tiers appended last on status; existing fields intact | integration | `GT --tests 'org.bartram.myfeeder.controller.InterestApiIntegrationTest' --tests 'org.bartram.myfeeder.service.InterestStatusServiceTest'` | ✅ extend | ⬜ pending |
| 07-01-T2 | 07-01 | 1 | OPS-02 | T-07-02 | yaml parity main ↔ test+dev; Binder key path | unit | `GT --tests 'org.bartram.myfeeder.service.InterestStatusServiceTest' --tests 'org.bartram.myfeeder.DevProfileConfigTest' --tests 'org.bartram.myfeeder.controller.InterestApiIntegrationTest'` | ✅ extend | ⬜ pending |
| 07-02-T1 | 07-02 | 1 | OPS-02 | T-07-05 | 70/40 fallback; served tiers color the badge | hook/component | `cd src/main/frontend && npx vitest run src/hooks/useInterest.test.tsx src/components/InterestBadge.test.tsx` | ❌ created in task | ⬜ pending |
| 07-02-T2 | 07-02 | 1 | OPS-02 | T-07-04 | provider in MainLayout; no numeric tier literal | unit + full | `cd src/main/frontend && npx tsc -b && npm test` | ✅ extend | ⬜ pending |
| 07-03-T1 | 07-03 | 1 | OPS-01 | T-07-07 | retry line never echoes key/body | integration | `GT --tests 'org.bartram.myfeeder.integration.JevResilienceTest'` | ✅ extend | ⬜ pending |
| 07-03-T2 | 07-03 | 1 | OPS-01 | T-07-09 | transitions and exhausted retries logged; context boots | integration | `GT --tests 'org.bartram.myfeeder.integration.JevResilienceTest' --tests 'org.bartram.myfeeder.MyfeederApplicationTests'` | ✅ extend | ⬜ pending |
| 07-04-T1 | 07-04 | 1 | OPS-02 | T-07-10 | replay is read-only and verbatim app SQL | unit + scratch-PG smoke | `GT --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest' --tests 'org.bartram.myfeeder.repository.InterestScoreQueriesTest'`; `bash "$HOME/.cache/myfeeder-phase07/replay-smoke.sh"` | ❌ created in task | ⬜ pending |
| 07-04-T2 | 07-04 | 1 | OPS-02 | T-07-11 | bad input rejected before psql | unit + smoke | `GT --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest'` | ✅ | ⬜ pending |
| 07-05-T1 | 07-05 | 2 | OPS-03 | T-07-15 | documented facts grounded in source | doc | grep checks in 07-05 Task 1 (`## Jev Scoring and Resilience` once; source greps) | ✅ | ⬜ pending |
| 07-05-T2 | 07-05 | 2 | OPS-03 | T-07-14 | no secret in docs; stale lines fixed | doc | grep checks in 07-05 Task 2 (`V6__interest_scoring`, `incrementMinor`, `MYFEEDER_TYPESAFE_API_KEY optional`) | ✅ | ⬜ pending |
| 07-06-T1 | 07-06 | 3 | OPS-01 | T-07-16 | .envrc masked scan; read-only preflight; full suites | ops preflight | full suite + git/infra/secret-presence checks (07-06 Task 1) | ops | ⬜ pending |
| 07-06-T3 | 07-06 | 3 | OPS-01 | T-07-17 | key only via secret (sha256 match); V6 applied; rollback revision | ops smoke | `curl -sf http://192.168.44.204/api/interest/status` configured/coldStart/CLOSED/tiers + preview + dump checks | ops | ⬜ pending |
| 07-07-T1 | 07-07 | 4 | OPS-01 | T-07-24 | read-only evidence channels recording | ops | watch pid alive, status/log/evidence lines (07-07 Task 1) | ops | ⬜ pending |
| 07-07-T3 | 07-07 | 4 | OPS-01 | T-07-23 | D-05 pass bar (drain, CLOSED, 429 absorbed) | ops evidence | legacy_backlog=0, zero breaker/exhausted-429 lines, all samples CLOSED, `07-BACKFILL.md` Overall PASS | ops | ⬜ pending |
| 07-08-T1 | 07-08 | 5 | OPS-02 | T-07-28 | replay equals the app in prod | ops | `crosscheck-baseline.json` matched == checked | ops | ⬜ pending |
| 07-08-T2 | 07-08 | 5 | OPS-02 | T-07-29 | no personal data in 07-CALIBRATION.md | doc | structure + `grep -F -f` privacy checks (07-08 Task 2) | ops | ⬜ pending |
| 07-09-T1 | 07-09 | 6 | OPS-02 | T-07-33 | yaml parity; local /status serves approved tiers | unit + local | `GT --tests 'org.bartram.myfeeder.DevProfileConfigTest' ...` + bootTestRun `local-status.json` | ✅ | ⬜ pending |
| 07-09-T3 | 07-09 | 6 | OPS-02 | T-07-32 | prod tiers equal approved; replay equals app | ops smoke | status tiers check + `crosscheck-0.2.1.json` | ops | ⬜ pending |
| 07-10-T1 | 07-10 | 1 (gap) | OPS-02 | T-07-38 | every `WITH learned AS` line verbatim; one-byte drift of any blend copy fails | unit (no Docker) | `GT --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest'` | ✅ extend | ⬜ pending |
| 07-10-T2 | 07-10 | 1 (gap) | OPS-02 | T-07-39 | `INTEREST_SCORE` exactly 4; missing/extra copy fails; tracked SQL untouched | unit (no Docker) | `GT --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest' --tests 'org.bartram.myfeeder.DevProfileConfigTest'` | ✅ extend | ⬜ pending |
| 07-11-T1 | 07-11 | 1 (gap) | OPS-02 | T-07-41 | a drifted badge fails whatever verbatim copy survives (trailing/block comment, appended section, same line); raw `INTEREST_SCORE` 4 with comments included; each badge line's code holds the `interest_score` item once | unit (no Docker) | `GT --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest'` | ✅ extend | ⬜ pending |
| 07-11-T2 | 07-11 | 1 (gap) | OPS-02 | T-07-42 | an extra blend statement fails in any case, indentation or position (5 openings); tracked SQL untouched; WR-04/IN-07 recorded fixed | unit (no Docker) | `GT --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest' --tests 'org.bartram.myfeeder.DevProfileConfigTest'` | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`: drift guard for the replay SQL vs `InterestScoreQueries.blendCte`, created in 07-04 Task 1 (the tracer creates the test with the SQL)
- [ ] `src/main/frontend/src/hooks/useInterest.test.tsx`: tiers fallback and served tiers, created in 07-02 Task 1
- [ ] `JevResilienceTest` OutputCapture assertions for the retry, exhausted and transition log lines: 07-03 Tasks 1 and 2 (the logger was accepted as D-15)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Backfill drains without 429 storms / open breaker | OPS-01 | Prod observation over time | Watch script output recorded in `07-BACKFILL.md` |
| Tuned constants approved | OPS-02 | Human judgment (D-11) | Review histogram + top/bottom 20 in `07-CALIBRATION.md`, record approval |
| 0.2.0 / 0.2.1 deployed | OPS-01/02 | Prod deploy | `/api/version`, rollout status, startup logs |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
