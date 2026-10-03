---
phase: "11"
slug: "gap-discovery"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-01"
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot test slices + Testcontainers Postgres (backend); Vitest + React Testing Library (frontend) |
| **Config file** | `build.gradle.kts` (`tasks.withType<Test>`); `src/main/frontend/vite.config.ts` |
| **Quick run command** | Backend: `./gradlew test -x npmBuild -x npmInstall --tests "<FQCN>"` · Frontend: `cd src/main/frontend && npx tsc -b && npx vitest run <files>` |
| **Full suite command** | `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npm test` (phase gate: `./gradlew build`) |
| **Estimated runtime** | ~240 seconds (backend full suite with Testcontainers); ~60 seconds frontend |

---

## Sampling Rate

- **After every task commit:** Run the touched test classes via the quick run commands
- **After every plan wave:** Run both full suite commands
- **Before `/gsd-verify-work`:** `./gradlew build` must be green
- **Max feedback latency:** 240 seconds

---

## Per-Task Verification Map

Filled by the planner/executor per task. Requirement → test map from RESEARCH.md:

| Req / SC | Behavior | Test Type | Automated Command | File Exists | Status |
|----------|----------|-----------|-------------------|-------------|--------|
| GAP-01 / SC-1 | Candidate predicate: window 30d on latest engagement, SCORED only, any vote excludes, any dismissal excludes | integration (@DataJdbcTest) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.TopicSuggestionStoreTest"` | ❌ W0 | ⬜ pending |
| GAP-01 / D-05 / D-07 | Badge ascending, ties → latest engagement then id; cap 10; total | unit (Mockito) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.service.TopicSuggestionServiceTest"` | ❌ W0 | ⬜ pending |
| GAP-01 / SC-1 | `GET /api/interest/suggestions` → `{items, total}` | integration | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.TopicSuggestionApiIntegrationTest"` | ❌ W0 | ⬜ pending |
| GAP-01 (UI) | Section below Topics, hidden when empty, `(10 of 23)` header, badge + title + feed, refetch on open | component | `cd src/main/frontend && npx tsc -b && npx vitest run src/components/InterestsDialog.test.tsx` | ✅ extend | ⬜ pending |
| GAP-01 (SC-1) | Engagement/vote/topic create+delete/Re-score/dismiss invalidate `['interest','suggestions']` | hook | `cd src/main/frontend && npx vitest run src/hooks/engagementReaction.test.ts` | ✅ extend | ⬜ pending |
| GAP-02 / SC-3 | Topic create with `sourceArticleId` writes TOPIC_CREATED atomically; stale id → 201 no row; rollback on store failure | integration | `… --tests "org.bartram.myfeeder.controller.TopicSuggestionApiIntegrationTest"` + `InterestServiceTest` + `InterestControllerTest` | ❌ W0 / ✅ | ⬜ pending |
| GAP-02 (UI) / D-14..D-16, D-18 | Draft added to open dialog, "Draft added" disabled row, discard re-enables, at-max tooltip, FeedbackNotice passes `sourceArticleId` | component | `cd src/main/frontend && npx vitest run src/components/InterestsDialog.test.tsx src/components/FeedbackNotice.test.tsx src/components/ReadingPane.test.tsx` | ✅ extend | ⬜ pending |
| GAP-03 / SC-4 | Dismiss PUT 204 idempotent, 404 unknown, 400 non-numeric; stays gone after new engagement and reload | integration | `… TopicSuggestionApiIntegrationTest` | ❌ W0 | ⬜ pending |
| GAP-04 / SC-2 | Best noul 0.35 excluded, 0.3499 included; negative-weight topic counts; no topic rows included | integration | `… TopicSuggestionStoreTest` | ❌ W0 | ⬜ pending |
| GAP-04 / D-09 | near-miss 0.35 binds; invalid values refused with fixed text; yaml parity | unit | `… --tests "org.bartram.myfeeder.config.MyfeederPropertiesValidationTest" --tests "org.bartram.myfeeder.DevProfileConfigTest"` | ✅ extend | ⬜ pending |
| GAP-05 / SC-5 | List, create-with-source, dismiss never call `JevApiClient.judge`; UI never posts preview | integration + component | `… TopicSuggestionApiIntegrationTest` (`verify(jevApiClient, never())`) + `InterestsDialog.test.tsx` | ❌ W0 / ✅ | ⬜ pending |
| Guard | Ranking SQL never reads the dismissal table; replay parameters unchanged | unit | `… --tests "org.bartram.myfeeder.repository.V7EngagementMigrationTest" --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest"` | ✅ unchanged | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java` — @DataJdbcTest + TestcontainersConfiguration; GAP-01, GAP-04
- [ ] `src/test/java/org/bartram/myfeeder/service/TopicSuggestionServiceTest.java` — Mockito; D-05/D-07 ordering and cap
- [ ] `src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java` — @SpringBootTest; GAP-01..GAP-05, `@MockitoBean JevApiClient` never called
- [ ] Frontend: default `GET /api/interest/suggestions` route (`{items: [], total: 0}`) in `InterestsDialog.test.tsx` `beforeEach`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Suggested topics section reads well in all 6 themes | GAP-01 | Visual styling | Open Interests with suggestions present; check each theme |
| End-to-end: engage an unmatched article, reopen Interests, create topic, reload | GAP-01..GAP-03 | Real-app flow | `./gradlew bootTestRun` + `npm run dev`; follow SC-1..SC-4 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
