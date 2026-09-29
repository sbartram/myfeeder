---
phase: "8"
slug: "engagement-capture"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-29"
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot test slices + Testcontainers (backend); Vitest 4 + RTL + jsdom (frontend) |
| **Config file** | `build.gradle.kts` test task; `src/main/frontend/vitest.config.ts` |
| **Quick run command** | `./gradlew test --tests "<touched test class>"` / `cd src/main/frontend && npx vitest run <touched test files> && npx tsc -b` |
| **Full suite command** | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |
| **Estimated runtime** | ~180 seconds (backend full suite with Testcontainers; Docker must be running) |

---

## Sampling Rate

- **After every task commit:** Run the touched class's `--tests` filter, or the touched Vitest files plus `npx tsc -b` for frontend tasks
- **After every plan wave:** Run `./gradlew test` and `cd src/main/frontend && npm test`
- **Before `/gsd-verify-work`:** Full suite must be green (including `DevProfileConfigTest` and `InterestCalibrationReplaySqlTest`), and before the release checkpoint
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

Filled in from the PLAN.md task IDs during execution / validate-phase. Requirement → test mapping (from 08-RESEARCH.md § Validation Architecture):

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| CAPT-01 | Helper opens before recording, records once per call, swallows errors; all three entry points use it; blank URL skipped | unit (Vitest) | `cd src/main/frontend && npx vitest run src/hooks/useEngagement.test.ts src/components/ReadingPane.test.tsx src/hooks/useKeyboardShortcuts.test.ts` | ❌ W0 (`useEngagement.test.ts`); others extend | ⬜ pending |
| CAPT-01 | PUT open → 204; repeat → 204 with one row; unknown id → 404 | integration | `./gradlew test --tests "*EngagementApiIntegrationTest"` | ❌ W0 | ⬜ pending |
| CAPT-02 | STAR only on false→true; already-starred / read-only PATCH records nothing; store failure doesn't fail the star | unit | `./gradlew test --tests "*ArticleServiceTest"` | ✅ extend | ⬜ pending |
| CAPT-03 | BOARD on add and re-add; not when save throws; two boards → one row | unit + integration | `./gradlew test --tests "*BoardServiceTest" --tests "*EngagementApiIntegrationTest"` | ✅ extend / ❌ W0 | ⬜ pending |
| CAPT-04 | RAINDROP after `createBookmark` returns; none on not-configured, disabled, no-collection or client throw | unit | `./gradlew test --tests "*RaindropServiceTest"` | ✅ extend | ⬜ pending |
| CAPT-05 | Unstar, board remove, board delete keep rows; feed delete cascades and succeeds | integration + migration | `./gradlew test --tests "*V7EngagementMigrationTest" --tests "*EngagementApiIntegrationTest"` | ❌ W0 | ⬜ pending |
| CAPT-06 | By-id carries `engagement` (`[]` when none); DELETE → 204, rows gone, later open records again; control shows only when engaged (incl. unscored) | integration + controller + Vitest | `./gradlew test --tests "*ArticleControllerTest" --tests "*EngagementApiIntegrationTest"`; `cd src/main/frontend && npx vitest run src/components/ReadingPane.test.tsx` | ✅ extend / ❌ W0 | ⬜ pending |
| CAPT-07 | In-body link and Copy Link don't call the helper/API; auto-mark-read records nothing | Vitest + unit | `cd src/main/frontend && npx vitest run src/components/ReadingPane.test.tsx`; `./gradlew test --tests "*ArticleServiceTest"` | ✅ extend | ⬜ pending |
| SC-5 ranking unchanged | `InterestScoreQueries` never mentions the new tables; badge identical before/after open+star+board; replay drift test green | static + integration | `./gradlew test --tests "*ArticleEngagementStoreTest" --tests "*EngagementApiIntegrationTest" --tests "*InterestCalibrationReplaySqlTest"` | ❌ W0 / ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java` — CHECKs, PKs, cascades (template: `V6InterestScoringMigrationTest`)
- [ ] `src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java` — idempotency, kinds order, deleteAll count, quiet-record swallows FK failure, static "ranking SQL doesn't read engagement" guard
- [ ] `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java` — `@SpringBootTest`, no test transaction, cleanup by feed delete + board-name prefix, `@MockitoBean JevApiClient` with `verify(never())`
- [ ] `src/main/frontend/src/hooks/useEngagement.test.ts`
- [ ] `@Mock ArticleEngagementStore` added to `ArticleServiceTest`, `BoardServiceTest`, `RaindropServiceTest`; new mocks added to `ReadingPane.test.tsx` and `useKeyboardShortcuts.test.ts`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| v0.3.0 runs in prod and engagement rows accumulate | SC-5 | Requires release, image push and deploy to the homelab k3s cluster | `kubectl -n myfeeder rollout status deploy/myfeeder`; `psql -h pg.bartram.org -c "SELECT kind, count(*) FROM article_engagement GROUP BY kind"` after real use |
| Priority order and badges unchanged in prod | SC-5 | Live prod data comparison | Compare badges for the same article ids in the Priority list before and after deploy |
| Open Original opens a new tab even with the server down | CAPT-01 | Real browser popup behavior | Stop backend, press `o` / click Open Original; tab still opens, no toast |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
