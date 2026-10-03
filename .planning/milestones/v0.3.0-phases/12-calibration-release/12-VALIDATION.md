---
phase: "12"
slug: "calibration-release"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-02"
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ (Spring Boot BOM), Testcontainers PostgreSQL; host psql for the replay run test |
| **Config file** | `build.gradle.kts` (Gradle test task); `src/test/resources/application.yaml` |
| **Quick run command** | `test -z "${SPRING_PROFILES_ACTIVE:-}" && ! env \| grep -q '^SPRING_AI_TYPESAFE_' && ./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest"` (no Docker) |
| **Full suite command** | `test -z "${SPRING_PROFILES_ACTIVE:-}" && ! env \| grep -q '^SPRING_AI_TYPESAFE_' && ./gradlew test -x npmBuild -x npmInstall` (Docker), plus `cd src/main/frontend && npx tsc -b && npx vitest run` before the release |
| **Estimated runtime** | ~40 seconds for the quick run (Gradle startup plus about 80 driver invocations); the full suite takes minutes |

---

## Sampling Rate

- **After every task commit:** Run the quick run command. Tasks that touch the run test also run `--tests "org.bartram.myfeeder.repository.InterestCalibrationReplayRunTest"` and check `skipped="0"` in its XML report
- **After every plan wave:** Run the full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | CAL-02 | T-12-01, T-12-02 | Candidate fields numeric-only before connect; replay read-only | unit + integration | quick run; `--tests "*InterestCalibrationReplayRunTest"` + `skipped="0"` | ✅ SqlTest / ❌ RunTest (created by this task) | ⬜ pending |
| 12-01-02 | 01 | 1 | CAL-02 | T-12-03 | Out-of-range engagement refused before any connection (WR-02) | unit (ProcessBuilder) | quick run | ✅ | ⬜ pending |
| 12-02-01 | 02 | 2 | CAL-02 | T-12-05 | Backfill differs from the app blend only in the engaged CTE | unit + integration | quick run; run test | ✅ | ⬜ pending |
| 12-02-02 | 02 | 2 | CAL-02 | T-12-06, T-12-07 | New sections print ids and counts only; read-only | unit + integration | quick run; run test; `--tests "*V7EngagementMigrationTest"` | ✅ | ⬜ pending |
| 12-03-01 | 03 | 3 | CAL-02, CAL-03 | T-12-08, T-12-11 | Read-only prod session; no password in evidence | ops (prod, read-only) | evidence checks in the plan's verify | n/a | ⬜ pending |
| 12-03-02 | 03 | 3 | CAL-03 | T-12-10 | Blocking-human data-floor gate | checkpoint | — | n/a | ⬜ pending |
| 12-03-03 | 03 | 3 | CAL-03 | T-12-09 | No titles, topic names or keys in the note | doc + privacy grep | note checks in the plan's verify | n/a | ⬜ pending |
| 12-04-01 | 04 | 4 | CAL-03 | T-12-12, T-12-13 | Tuning only in committed yaml; shipped values start | unit | `--tests "*MyfeederPropertiesValidationTest" --tests "*DevProfileConfigTest"` | ✅ | ⬜ pending |
| 12-04-02 | 04 | 4 | CAL-03 | T-12-14 | Docs agree with yaml | doc + full suite | CLAUDE.md greps; full suite | ✅ | ⬜ pending |
| 12-05-01 | 05 | 5 | CAL-03 | T-12-15, T-12-16, T-12-20 | Read-only dry run; secrets never printed | ops | preflight checks in the plan's verify | n/a | ⬜ pending |
| 12-05-02 | 05 | 5 | CAL-03 | T-12-19 | One-way release approval; untracked files settled by the user | checkpoint | — | n/a | ⬜ pending |
| 12-05-03 | 05 | 5 | CAL-03 | T-12-15, T-12-16, T-12-17, T-12-18 | Release order, startup, D-13 cross-check, soak | ops | tag, image, startup, sc3 and soak checks in the plan's verify | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java`: the end-to-end replay run on Testcontainers through host psql (research's throwaway-DB rehearsal). Created by the 12-01 tracer task, then extended in 12-02
- [ ] Drift-guard literal counts (5/4/4) refactored to named constants (`BLEND_STATEMENTS`, `BADGE_COPIES`) before new sections land. Done in the 12-01 tracer task

*No framework install: JUnit, Testcontainers and psql 18.6 are already present.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| The replay against prod emits every section, and the cap-0 replay equals what 0.3.0 serves | CAL-02 | Needs the LAN, the prod password and Orca's Local Network permission | 12-03 Task 1 (read-only); evidence under `$HOME/.cache/myfeeder-phase12/` |
| Data-floor gate and constants approval | CAL-03 | A user decision (D-01 to D-04) | 12-03 Task 2 blocking checkpoint |
| Release v0.3.1 and the D-13 replay-vs-API cross-check | CAL-03 | Publishes to the public origin and prod (D-12, one-way) | 12-05 Tasks 1-3 |
| High share holds within about 5 points a day after the deploy (backstop) | CAL-03 | Needs a day of live use | Re-run the replay at the shipped constants and at cap 0, and compare the `summary` high_pct |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** {pending / approved YYYY-MM-DD}
