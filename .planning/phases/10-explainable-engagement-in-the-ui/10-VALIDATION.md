---
phase: "10"
slug: "explainable-engagement-in-the-ui"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: true) (#2117)
status: validated
nyquist_compliant: false
wave_0_complete: true
created: "2026-09-30"
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4 + React Testing Library (frontend); JUnit 5 + Mockito + Testcontainers via Gradle (backend) |
| **Config file** | `src/main/frontend/vitest.config.ts`, `build.gradle.kts` |
| **Quick run command** | `cd src/main/frontend && npx tsc -b && npx vitest run <touched test files>` (frontend) · `./gradlew test -x npmBuild -x npmInstall --tests "<touched class>"` (backend) |
| **Full suite command** | `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npx vitest run` |
| **Estimated runtime** | ~20 seconds quick (frontend), ~60 seconds quick (backend class), ~4 minutes full |

---

## Sampling Rate

- **After every task commit:** Run the touched Vitest files + `npx tsc -b` (frontend tasks) or the touched JUnit class (backend tasks)
- **After every plan wave:** Run the full frontend suite and `./gradlew test -x npmBuild -x npmInstall`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

Filled by the planner/executor from the PLAN.md tasks. Requirement → test map from 10-RESEARCH.md § Validation Architecture:

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| EXPL-01 / SC-1 | "Why N?" label names votes/engaged parts, zero parts omitted, tooltip lists all parts, rows sum to badge | unit (component) | `npx vitest run src/components/WhyBreakdown.test.tsx` | ✅ | ✅ green |
| LRN-06 / SC-2 | Open, star, board add, Raindrop, Forget on Priority: by-id refetch, patch only when score differs, set hint, never touch `['priority']` | unit (hook) | `npx vitest run src/hooks/engagementRefresh.test.ts src/hooks/useEngagement.test.ts` | ✅ | ✅ green |
| LRN-06 / D-07 | Off Priority: learned, articles, other by-id (not extracted) invalidated | unit (hook) | `npx vitest run src/hooks/engagementRefresh.test.ts` | ✅ | ✅ green |
| LRN-06 / SC-2 | Forget from ScoreRow reacts; unstar and board removal do not | unit | `npx vitest run src/components/ScoreRow.test.tsx src/hooks/usePriorityArticles.test.ts` | ✅ | ✅ green |
| LRN-06 / SC-2 | PriorityList keeps row order and shows "↻ Ranking changed — refresh" after engagement | component | `npx vitest run src/components/PriorityList.test.tsx` | ✅ | ✅ green |
| LRN-06 / SC-3 | Priority page score = by-id `interestScore` = breakdown display for an engaged article | integration | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.PriorityApiIntegrationTest"` | ✅ | ✅ green |
| SC-4 / WR-03 | Vote/removal on engaged article marks replaced engagement; no marker without engagement | unit + integration | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.service.ArticleFeedbackServiceTest" --tests "org.bartram.myfeeder.controller.FeedbackApiIntegrationTest"` | ✅ | ✅ green |
| SC-4 / D-11..D-13 | Toast wording, one note per topic, ENGAGEMENT_CAP never worded or listed alone | unit | `npx vitest run src/utils/feedback.test.ts` | ✅ | ✅ green |
| WR-01 / D-10 | LearnedLimit precedence LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE | unit | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.service.ArticleFeedbackServiceTest"` | ✅ | ✅ green |
| IN-01 / D-14, D-15 | Interests learned line split, omitted zeros, "at max", other variants | unit (component) | `npx vitest run src/components/TopicRow.test.tsx` | ✅ | ✅ green |
| Serialization | New `TopicEffect` / `TopicLearned` fields on the JSON | controller slice | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.ArticleControllerTest" --tests "org.bartram.myfeeder.controller.InterestControllerTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `MemoryRouter` wrappers in `engagementRefresh.test.ts`, `useEngagement.test.ts`, `ScoreRow.test.tsx`, `usePriorityArticles.test.ts` (same task that adds `useMatch` to the hooks)
- [x] New Priority-reaction cases for all five actions (differ → patch + hint; equal → nothing; failure → silent; never `['priority']`)
- [x] `PriorityApiIntegrationTest`: one SC-3 agreement test with an `article_engagement` row
- [x] `ArticleFeedbackServiceTest`: replaced-engagement true/false cases, including the narrowed-unpicked case

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Engagement in Priority lights the hint without reordering, refresh agrees everywhere | LRN-06 | End-to-end feel in the real app | `./gradlew bootTestRun` + `npm run dev`; on `/priority` open, star, board, Raindrop (if configured) and Forget; confirm hint, no reorder, then refresh and compare badge/"Why N?"/position |
| IN-04 Javadoc corrected on `InterestScoreQueries.priorityPageAfter` | D-09 | Documentation only | Read the Javadoc |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-09-30

---

## Validation Audit 2026-09-30

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Evidence: `npx tsc -b` clean; 9 Vitest files / 146 tests green (WhyBreakdown, engagementRefresh, engagementReaction, useEngagement, ScoreRow, usePriorityArticles, PriorityList, feedback, TopicRow); 5 JUnit classes / 98 tests green (PriorityApiIntegrationTest 7, ArticleFeedbackServiceTest 25, FeedbackApiIntegrationTest 24, ArticleControllerTest 30, InterestControllerTest 12). Former W0 rows now pinned by `engagementReaction.test.ts` (offPriority…, neverInvalidatesResetsOrRefetchesPriority, extracted key untouched), `PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh`, `engagementRefresh.test.ts` boardRemovalRunsNoReaction, `usePriorityArticles.test.ts` unstarAndReadDoNotReact. Manual-only rows passed in 10-UAT.md (6/6).
