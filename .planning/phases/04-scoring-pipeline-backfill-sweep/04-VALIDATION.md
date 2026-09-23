---
phase: "4"
slug: "scoring-pipeline-backfill-sweep"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-23"
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ (Spring Boot 4.0.8 test starters), Testcontainers Postgres; Vitest 4 + React Testing Library (frontend) |
| **Config file** | `build.gradle.kts`, `src/test/resources/application.yaml` (shadows main), `src/main/frontend/vite.config.*` |
| **Quick run command** | `./gradlew test -x npmBuild -x npmInstall --tests "<touched test class>"` (Docker needed for repository/integration tests) |
| **Full suite command** | `./gradlew test && (cd src/main/frontend && npm test && npx tsc -b)` |
| **Estimated runtime** | ~180 seconds |

---

## Sampling Rate

- **After every task commit:** Run the quick `--tests` command(s) for the classes the task touched, plus `npx vitest run <file>` for frontend tasks
- **After every plan wave:** Run the full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

Populated from the plans. The requirement → test map from RESEARCH.md §Validation Architecture:

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| SCOR-01 | Scorer uses shared builders, one `judge()`; truncate keeps ≥ max/2 on CJK/URL | unit | `./gradlew test --tests "*.ArticleScoringServiceTest" --tests "*.ArticleStateBuilderTest"` | ❌ W0 / ✅ extend | ⬜ pending |
| SCOR-01 | `pollFeed` publishes one event with only new IDs | unit | `./gradlew test --tests "*.FeedPollingServiceTest"` | ✅ extend | ⬜ pending |
| SCOR-02 | Poll unaffected by slow/failing/unconfigured Jev and listener errors | unit | `./gradlew test --tests "*.ScoringIsolationTest"` | ❌ W0 | ⬜ pending |
| SCOR-02 | Rejected task clears in-flight; dedup; capacity | unit | `./gradlew test --tests "*.ScoringQueueTest"` | ❌ W0 | ⬜ pending |
| SCOR-02 | `applicationTaskExecutor` still present | integration | `./gradlew test --tests "*.MyfeederApplicationTests"` | ✅ extend | ⬜ pending |
| SCOR-03/04/07 | Upsert semantics, eligibility predicate, exhausted not re-selected | repository | `./gradlew test --tests "*.ArticleScoreStoreTest"` | ❌ W0 | ⬜ pending |
| SCOR-05/06 | Sweep gates and newest-first enqueue; cold start | unit | `./gradlew test --tests "*.InterestScoringSweepTest"` | ❌ W0 | ⬜ pending |
| SCOR-07/08 | Failure classification; GUID-less SKIPPED | unit | `./gradlew test --tests "*.ArticleScoringServiceTest"` | ❌ W0 | ⬜ pending |
| JEV-05 | `/status` eligibleUnscored + failed counts | unit + integration | `./gradlew test --tests "*.InterestStatusServiceTest" --tests "*.InterestApiIntegrationTest"` | ✅ extend | ⬜ pending |
| INT-05 | Re-score count == deleted rows; SKIPPED untouched | controller + integration | `./gradlew test --tests "*.InterestRescoreControllerTest" --tests "*.InterestApiIntegrationTest"` | ❌ W0 / ✅ extend | ⬜ pending |
| INT-05 | Dialog Re-score disabled when dirty; confirm shows count | frontend | `cd src/main/frontend && npx vitest run src/components/InterestsDialog.test.tsx` | ✅ extend | ⬜ pending |
| D-07 | Breaker wraps retry: one recorded outcome per `judge()` | unit | `./gradlew test --tests "*.JevResilienceTest"` | ✅ rewrite | ⬜ pending |
| D-05/06/14/15 | Timeout/slow-call config; IAE and Choice not counted | unit | `./gradlew test --tests "*.TypeSafeConfigTest" --tests "*.JevResilienceTest"` | ✅ update | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java` — SCOR-03/04/07, INT-05 SQL
- [ ] `src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java` — SCOR-01/06/07/08
- [ ] `src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java` — SCOR-02 in-flight/rejection
- [ ] `src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java` — SCOR-02 / success criterion 2
- [ ] `src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java` — SCOR-05/06
- [ ] `src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java` — INT-05
- [ ] Test YAML: `myfeeder.interest.*` block, aspect orders, timeout, slow-call, IAE ignore, auto-transition

Framework install: none needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live scoring end-to-end | SCOR-01, JEV-05, INT-05 | Needs a real TypeSafe key and billed calls | With a live key: poll a feed, confirm `article_score` rows appear, `/status` eligible-unscored drains, Re-score resets and re-drains |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
