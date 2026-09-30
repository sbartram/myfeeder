---
phase: "9"
slug: "engagement-learning-model"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-30"
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito + Spring Boot test slices + Testcontainers Postgres 18.6 (backend); Vitest + RTL + jsdom (frontend) |
| **Config file** | `build.gradle.kts` test task; `src/main/frontend/vitest.config.ts` |
| **Quick run command** | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest" --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest"` plus the touched test class |
| **Full suite command** | `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npx vitest run` |
| **Estimated runtime** | ~200 seconds (backend full suite with Testcontainers, including the 20k-row latency seed; Docker must be running) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command plus the test class named in the task (the touched Vitest files plus `npx tsc -b` for frontend tasks)
- **After every plan wave:** Run `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npx vitest run`
- **Before `/gsd-verify-work`:** The full suite must be green, including `DevProfileConfigTest`, `InterestCalibrationReplaySqlTest` and `V7EngagementMigrationTest`. The latency numbers and EXPLAIN (ANALYZE) go into VERIFICATION (D-13, D-17)
- **Never** export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs the tests (CLAUDE.md, `bootTestRun` gotcha)
- **Max feedback latency:** 200 seconds

---

## Per-Task Verification Map

This is filled in from the PLAN.md task IDs during execution and validate-phase. The requirement → test mapping comes from 09-RESEARCH.md § Validation Architecture:

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| LRN-01 | Save > open. Open + star + board + Raindrop on one article counts once, as a save. Only SCORED counts (no row / FAILED / SKIPPED add nothing). A base of 0 qualifies | integration (real PG) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` (new engagement methods, or a sibling `InterestScoreQueriesEngagementTest`) | ✅ file; ❌ W0 methods | ⬜ pending |
| LRN-01 (SC-1) | End to end: open/star/board raises the score and breakdown, `interest_topic.weight` is unchanged, no Jev call | integration (`@SpringBootTest`) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.EngagementApiIntegrationTest"` (invert `engagementLeavesTheRankingUnchanged`) | ✅ (rewrite 1 method) | ⬜ pending |
| LRN-02 | Any vote (up, down, narrowed with picks, narrowed with none) removes the article's engagement on every topic. Deleting the vote restores `topicWeights` exactly | integration (real PG) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` | ❌ W0 | ⬜ pending |
| LRN-02 / D-12 | The vote effect's `before` includes the engagement that the vote replaces | integration | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.FeedbackApiIntegrationTest"` (new method) | ✅ file; ❌ method | ⬜ pending |
| LRN-03 | Negative base: `eng_raw = eng = 0`, effective unchanged | integration + grid | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestLearnedGridTest"` | ❌ W0 | ⬜ pending |
| LRN-04 | Exact split `base + thumbs + eng = effective` (BigDecimal), `eng ≥ 0`, sign clamp, ±50, both caps, additive on top of thumbs. The breakdown split equals the topic-weights split | property grid (real PG) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestLearnedGridTest"` | ❌ W0 | ⬜ pending |
| LRN-04 / D-11 / D-15 | `LearnedLimit` precedence LEARNED_CAP → ENGAGEMENT_CAP (only when cap > 0) → SIGN_CLAMP/WEIGHT_RANGE on base + thumbs + eng → NONE. `learned` = thumbs capped + engagement capped | unit | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.service.ArticleFeedbackServiceTest"` | ✅ file; ❌ methods | ⬜ pending |
| LRN-05 | Validation: cap 0 always starts (including negative weights). It rejects save ≤ open, save ≥ 1, open < 0, cap < 0 and cap ≥ learned-cap with fixed text | unit (`ApplicationContextRunner`, no Docker) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.config.MyfeederPropertiesValidationTest"` | ❌ W0 | ⬜ pending |
| LRN-05 / D-05 | cap 0 with engagement rows present: every `raw_n` and the Priority order equal a frozen v0.2.1 blend copy. No rows at cap 8 is also equal. cap 0 with negative weights is equal (zero floor) | integration (real PG) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesZeroEngagementTest"` | ❌ W0 | ⬜ pending |
| LRN-05 | Main and test yaml mirrored with identical literals; the dev overlay is unchanged | unit (no Docker) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.DevProfileConfigTest"` | ✅ (passes once the yaml is mirrored) | ⬜ pending |
| CAL-01 | The replay is verbatim (5 lines, 4 badges) and read-only. The driver passes every blend parameter and rejects a non-numeric `ENGAGEMENT_*` value | unit (no Docker) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest"` | ✅ file; ❌ 2 new methods | ⬜ pending |
| CAL-01 | The ranking SQL and the replay read `article_engagement` but never `topic_suggestion_dismissal` | integration | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.V7EngagementMigrationTest"` | ✅ (invert 1 method) | ⬜ pending |
| SC-5 latency (D-13 / D-17) | With 20k engagement rows, the Priority first page, the breakdown and the topic weights each stay ≤ 10 × baseline + 250 ms (median of 5 warm runs after 3 warm-ups). The numbers and EXPLAIN go to stdout | integration (real PG) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestScoreQueriesLatencyTest"` | ❌ W0 | ⬜ pending |
| D-11 (frontend) | The `LearnedLimit` union includes `ENGAGEMENT_CAP`. `effectNote` / `LearnedLine` fall through to their default (pins research Pitfall 6 for Phase 10) | unit (Vitest) | `cd src/main/frontend && npx tsc -b && npx vitest run src/utils/feedback.test.ts src/components/TopicRow.test.tsx` | ✅ files; ❌ cases | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java`: LRN-03/LRN-04 exact-split grid (dyadic oracle)
- [ ] New engagement methods in `InterestScoreQueriesTest` (or a sibling `InterestScoreQueriesEngagementTest`): LRN-01/LRN-02 behaviors
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java`: D-05 against a frozen v0.2.1 blend string (copy the current blend line of `scripts/interest-calibration-replay.sql` **before** editing it)
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java`: D-13 / D-17
- [ ] `src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java`: LRN-05
- [ ] `InterestCalibrationReplaySqlTest`: `driverPassesEveryBlendParameter`, `driverRejectsANonNumericEngagementValue`
- [ ] Invert `V7EngagementMigrationTest.rankingSqlDoesNotReadTheV7TablesYet` and `EngagementApiIntegrationTest.engagementLeavesTheRankingUnchanged` in the **same plan and commit** as the `LEARNED_CTE` change and the replay regeneration (CAL-01 ordering constraint)
- [ ] `ArticleFeedbackServiceTest`: `LearnedLimit` ENGAGEMENT_CAP precedence cases; update the record constructors (research Pitfall 7)
- [ ] Frontend: an ENGAGEMENT_CAP fall-through case in `feedback.test.ts`
- Framework install: none needed

---

## Manual-Only Verifications

All phase behaviors have automated verification. D-14 says Phase 9 ships no release, so there is no prod check.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 200s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
