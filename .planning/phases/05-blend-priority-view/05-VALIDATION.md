---
phase: "5"
slug: "blend-priority-view"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-25"
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot test slices + Testcontainers (backend); Vitest 4 + React Testing Library (frontend) |
| **Config file** | `build.gradle.kts`; `src/main/frontend/vitest.config.ts` |
| **Quick run command** | `./gradlew test -x npmBuild -x npmInstall --tests "<TestClass>"` / `cd src/main/frontend && npx vitest run <file>` |
| **Full suite command** | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |
| **Estimated runtime** | ~180 seconds (backend full), ~30 seconds (frontend) |

---

## Sampling Rate

- **After every task commit:** Run the task's targeted test class/file
- **After every plan wave:** Run the full suite commands
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner / validate-phase) | | | PRIO-01..08 | | | | see RESEARCH.md § Validation Architecture | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java` — PRIO-01/02/03/06, criterion 5
- [ ] `src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java` — PRIO-04
- [ ] `src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java` — missing cursor → 404
- [ ] `src/test/java/org/bartram/myfeeder/config/SpaForwardControllerTest.java` — PRIO-01 reload
- [ ] `src/main/frontend/src/hooks/usePriorityArticles.test.ts`, `components/PriorityList.test.tsx`, `PriorityBanner.test.tsx`, `InterestBadge.test.tsx`, `WhyBreakdown.test.tsx`
- [ ] Update existing mocks/fixtures for `interestScore` (RESEARCH Pitfall 8)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Triage stability feel + reload on `/priority` in a real browser | PRIO-05, PRIO-01 | End-to-end UX across real refetch timing | `./gradlew bootTestRun` + `npm run dev`; open `/priority`, mark read/star, refocus window, reload page |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
