---
phase: "3"
slug: "interest-model-schema-rubric-editor"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-23"
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito + Testcontainers (backend); Vitest 4 + RTL 16 + jsdom (frontend) |
| **Config file** | `build.gradle.kts`, `src/test/resources/application.yaml`; `src/main/frontend/vitest.config.ts`, `src/main/frontend/src/test/setup.ts` |
| **Quick run command** | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.*Interest*'` / `cd src/main/frontend && npx vitest run src/utils/interest.test.ts src/components/InterestsDialog.test.tsx` |
| **Full suite command** | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |
| **Estimated runtime** | ~120 seconds (backend full suite with Testcontainers) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command for the touched layer
- **After every plan wave:** Run the full suite command (Docker running)
- **Before `/gsd-verify-work`:** Full suite must be green; spike findings recorded in `03-CALIBRATION.md`
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

Filled in by the planner/executor per task. Requirement → test map from research:

| Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|--------|----------|-----------|-------------------|-------------|--------|
| (schema) | V6 tables, seed row, weight CHECK, SKIPPED, cascades incl. score→topic-score | integration (DataJdbc) | `./gradlew test -x npmBuild -x npmInstall --tests '*V6InterestScoringMigrationTest'` | ❌ W0 | ⬜ pending |
| INT-01 | Profile ≤2,000 accepted, 2,001 → 400; version bumps only on text change; persists | unit + WebMvc | `--tests '*InterestServiceTest' --tests '*InterestControllerTest'` | ❌ W0 | ⬜ pending |
| INT-01 | Tips, placeholder, `N / 2,000` counter, save/dirty states | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ❌ W0 | ⬜ pending |
| INT-02 | 26th topic → 400; weight ±51 → 400; default 20; description-only version bump; delete 204/404 | unit + WebMvc | `--tests '*InterestServiceTest' --tests '*InterestControllerTest'` | ❌ W0 | ⬜ pending |
| INT-02 | Slider/number sync, invalid weight disables Save, Add disabled at 25 | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ❌ W0 | ⬜ pending |
| INT-03 | `isNegated` list, smart-quote normalization, debounced warning, Save still enabled | unit + component | `npx vitest run src/utils/interest.test.ts src/components/InterestsDialog.test.tsx` | ❌ W0 | ⬜ pending |
| INT-04 | State builder: content fallback, HTML strip, 1,500 truncation, bounded raw input, key order | unit | `--tests '*ArticleStateBuilderTest'` | ❌ W0 | ⬜ pending |
| INT-04 | Question builder: keys `profile`/`topic_<id>`, 5 levels, Noul whenTrue/whenFalse, order | unit | `--tests '*InterestQuestionsTest'` | ❌ W0 | ⬜ pending |
| INT-04 | Preview: one `judge` with one Noul, nothing persisted, 400/404/503/422 mappings | unit + WebMvc | `--tests '*InterestPreviewServiceTest' --tests '*InterestControllerTest'` | ❌ W0 | ⬜ pending |
| INT-04 | Preview math copy, no-match copy, disabled reasons, inline error without toast | unit + component | `npx vitest run src/utils/interest.test.ts src/components/InterestsDialog.test.tsx` | ❌ W0 | ⬜ pending |
| INT-06 | `/status` configured, breakerState, coldStart predicate | unit + WebMvc | `--tests '*InterestStatusServiceTest' --tests '*InterestServiceTest'` | ❌ W0 | ⬜ pending |
| INT-06 | Not-configured / paused / cold-start notices; editing allowed without key | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ❌ W0 | ⬜ pending |
| (client) | `ApiError` carries status/title | unit | `npx vitest run src/api/client.test.ts` | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java`
- [ ] `src/test/java/org/bartram/myfeeder/service/{InterestServiceTest,InterestQuestionsTest,ArticleStateBuilderTest,InterestPreviewServiceTest,InterestStatusServiceTest}.java`
- [ ] `src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java`
- [ ] `src/main/frontend/src/utils/interest.test.ts`, `src/main/frontend/src/components/InterestsDialog.test.tsx`
- [ ] Update `SettingsDialog.test.tsx`; extend `src/api/client.test.ts` for `ApiError`

Framework install: none (all present).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Calibration of question wording against 10–20 real articles | INT-04 (roadmap research flag) | Billed live API, needs `MYFEEDER_TYPESAFE_API_KEY` and human judgment of spread/confidence | `JEV_CALIBRATION=true ./gradlew test -x npmBuild -x npmInstall --tests '*InterestCalibrationSpikeTest'`; record findings in `03-CALIBRATION.md` |
| Visual layout of Interests dialog across themes/viewports | INT-01..04, INT-06 | UI-SPEC backstop items (wrapping, narrow rows) only visible when rendered | Open Settings → Interests in each theme; check wrapping at narrow widths |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
