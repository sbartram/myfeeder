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

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD (planner fills) | — | 1 | OPS-02 | — | tiers appended last on status (append-only) | unit/integration | `./gradlew test --tests "*InterestStatusServiceTest" --tests "*InterestApiIntegrationTest"` | ✅ extend | ⬜ pending |
| TBD | — | 1 | OPS-02 | — | yaml parity main ↔ test+dev | unit | `./gradlew test --tests "*DevProfileConfigTest"` | ✅ | ⬜ pending |
| TBD | — | 1 | OPS-02 | — | N/A | unit/component | `npx vitest run src/utils/interest.test.ts src/components/InterestBadge.test.tsx` | ✅ extend | ⬜ pending |
| TBD | — | 1 | OPS-02 | — | N/A | hook | `npx vitest run src/hooks/useInterest*.test.ts` | ❌ W0 | ⬜ pending |
| TBD | — | 1 | OPS-02 | — | replay is read-only and verbatim app SQL | unit | `./gradlew test --tests "*InterestCalibrationReplaySqlTest"` | ❌ W0 | ⬜ pending |
| TBD | — | 1 | OPS-01 | — | logs never echo key/body | integration | `./gradlew test --tests "*JevResilienceTest"` | ✅ extend | ⬜ pending |
| TBD | — | 1 | OPS-03 | — | N/A | doc | `grep -q 'TypeSafeConfig' CLAUDE.md && grep -q 'jev-score' CLAUDE.md && grep -q 'window-days' CLAUDE.md` | ✅ | ⬜ pending |
| TBD | — | 2+ | OPS-01 | — | key only via secret | ops smoke | `curl -sf http://192.168.44.204/api/interest/status \| jq -e '.configured and .breakerState=="CLOSED"'` | ops | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java` — drift guard for replay SQL vs `InterestScoreQueries.blendCte`
- [ ] `src/main/frontend/src/hooks/useInterest*.test.ts(x)` — tiers fallback + served tiers
- [ ] `JevResilienceTest` OutputCapture assertion for retry/transition log lines (if the event logger is accepted)

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
