---
phase: "6"
slug: "thumbs-feedback"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: true) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-09-26"
---

# Phase 6 — Validation Strategy

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
- **Before `/gsd-verify-work`:** Full suite must be green; manual UAT of the toast and picker in `bootTestRun`
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-T1 | 06-01 | 1 | FDBK-02, FDBK-04 | T-06-01, T-06-02, T-06-06 | Named params only; JSON-only PUT; no topic weight write; no Jev call | integration | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.controller.FeedbackApiIntegrationTest' --tests 'org.bartram.myfeeder.controller.ArticleControllerTest' --tests 'org.bartram.myfeeder.repository.InterestScoreQueriesTest'` | ✅ | ✅ green |
| 06-01-T2 | 06-01 | 1 | FDBK-01, FDBK-03, FDBK-05 | T-06-06 | Delete restores exactly; blend keys mirrored in both yaml files | repository + integration + config | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.repository.InterestScoreQueriesTest' --tests 'org.bartram.myfeeder.controller.FeedbackApiIntegrationTest' --tests 'org.bartram.myfeeder.DevProfileConfigTest'` | ✅ | ✅ green |
| 06-01-T3 | 06-01 | 1 | FDBK-03, FDBK-04, FDBK-05, FDBK-06 | T-06-02..05 | Validation before write; fixed-text 400; 415 non-JSON; size cap; LearnedLimit boundaries | unit + @WebMvcTest + integration | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.service.ArticleFeedbackServiceTest' --tests 'org.bartram.myfeeder.controller.ArticleControllerTest' --tests 'org.bartram.myfeeder.controller.FeedbackApiIntegrationTest'` | ✅ | ✅ green |
| 06-02-T1 | 06-02 | 2 | FDBK-01, FDBK-06 | T-06-08, T-06-09 | Feedback only on by-id responses | integration + unit | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.controller.FeedbackApiIntegrationTest' --tests 'org.bartram.myfeeder.service.ArticleServiceTest' --tests 'org.bartram.myfeeder.controller.PriorityApiIntegrationTest'` | ✅ | ✅ green |
| 06-02-T2 | 06-02 | 2 | FDBK-04 (D-11) | — | — | unit + repository | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.service.ScoreBreakdownsTest' --tests 'org.bartram.myfeeder.controller.ArticleControllerTest' --tests 'org.bartram.myfeeder.repository.InterestScoreQueriesTest'` | ✅ | ✅ green |
| 06-03-T1 | 06-03 | 1 | FDBK-01, FDBK-02, FDBK-04 | T-06-12 | Text-only rendering | RTL | `cd src/main/frontend && npx vitest run src/components/FeedbackBar.test.tsx src/components/ReadingPane.test.tsx` | ✅ | ✅ green |
| 06-03-T2 | 06-03 | 1 | FDBK-01 (D-01) | — | Theme variables only | grep + RTL | `cd src/main/frontend && grep -q "vote-btn\[aria-pressed='true'\]" src/App.css && npx vitest run src/components/FeedbackBar.test.tsx src/components/ReadingPane.test.tsx` | ✅ | ✅ green |
| 06-03-T3 | 06-03 | 1 | FDBK-01, FDBK-02 | T-06-13, T-06-15 | Serialized votes; Priority never refetched | hook | `cd src/main/frontend && npx vitest run src/hooks/useFeedback.test.ts src/hooks/usePriorityArticles.test.ts src/components/FeedbackBar.test.tsx` | ✅ | ✅ green |
| 06-04-T1 | 06-04 | 2 | FDBK-06 | T-06-16, T-06-18 | Only matched topics offered | RTL | `cd src/main/frontend && npx vitest run src/components/NarrowPicker.test.tsx src/components/FeedbackBar.test.tsx` | ✅ | ✅ green |
| 06-04-T2 | 06-04 | 2 | FDBK-06 | — | Picker keys isolated from global shortcuts | RTL | `cd src/main/frontend && npx vitest run src/components/NarrowPicker.test.tsx src/components/FeedbackBar.test.tsx src/components/ReadingPane.test.tsx` | ✅ | ✅ green |
| 06-04-T3 | 06-04 | 2 | FDBK-01 | T-06-17 | Modifier keys ignored | hook | `cd src/main/frontend && npx vitest run src/hooks/useKeyboardShortcuts.test.ts` | ✅ | ✅ green |
| 06-05-T1 | 06-05 | 3 | FDBK-05 | T-06-19, T-06-20 | Nothing saved automatically; title cut to 500 | RTL | `cd src/main/frontend && npx vitest run src/components/FeedbackNotice.test.tsx src/components/ReadingPane.test.tsx src/components/InterestsDialog.test.tsx` | ✅ | ✅ green |
| 06-05-T2 | 06-05 | 3 | FDBK-07 | T-06-19 | Server values only | RTL | `cd src/main/frontend && npx vitest run src/components/TopicRow.test.tsx src/components/InterestsDialog.test.tsx` | ✅ | ✅ green |
| 06-05-T3 | 06-05 | 3 | D-11 | T-06-19 | Server values only | RTL | `cd src/main/frontend && npx vitest run src/components/WhyBreakdown.test.tsx src/components/ReadingPane.test.tsx` | ✅ | ✅ green |
| 06-06-T1 | 06-06 | 3 | FDBK-07 | T-06-10, T-06-22 | Read-only learned endpoint | @WebMvcTest + integration | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.controller.InterestControllerTest' --tests 'org.bartram.myfeeder.controller.FeedbackApiIntegrationTest'` | ✅ | ✅ green |
| 06-06-T2 | 06-06 | 3 | FDBK-07 (docs) | — | User's uncommitted CLAUDE.md edits never staged | grep + git | `grep -q 'topics/learned' CLAUDE.md && grep -q 'Thumbs feedback' CLAUDE.md && test -z "$(git diff --cached --name-only)"` | ✅ | ✅ green |
| 06-07-T1 | 06-07 | 3 | FDBK-05 | T-06-23 | Text-only rendering; fixed copy | unit + RTL | `cd src/main/frontend && npx vitest run src/utils/feedback.test.ts src/components/FeedbackBar.test.tsx` | ✅ | ✅ green |
| 06-07-T2 | 06-07 | 3 | FDBK-04 | T-06-23, T-06-24 | Server numbers only; fixed copy | unit | `cd src/main/frontend && npx vitest run src/utils/feedback.test.ts src/api/client.test.ts src/components/FeedbackBar.test.tsx` | ✅ | ✅ green |

Requirement → test map (from 06-RESEARCH.md § Validation Architecture):

| Req ID | Behavior | Test Type | Automated Command | File Exists |
|--------|----------|-----------|-------------------|-------------|
| FDBK-03 | Cap ±20, sign clamp, ±50, zero-base both ways, narrowed-only, zero picks, unscored vote = 0 | repository (`@DataJdbcTest`) | `./gradlew test --tests "*InterestScoreQueriesTest"` | ✅ |
| FDBK-01/03 | up→down→up equals a single up; delete restores exactly | API integration | `./gradlew test --tests "*FeedbackApiIntegrationTest"` | ✅ |
| FDBK-02 | Vote changes badge/breakdown on by-id response; no Jev call | API integration | same | ✅ |
| FDBK-04 | `effects[]` before/after/limit (cap and sign-clamp) | service unit + integration | `./gradlew test --tests "*ArticleFeedbackServiceTest"` | ✅ |
| FDBK-05 | `scored=false` for unscored; no matches → "No topics matched" | service + RTL | `npx vitest run src/components/FeedbackBar.test.tsx` | ✅ |
| FDBK-06 | 400 on empty/non-matched `topicIds`; 415 on non-JSON; flip to up clears narrowing | service + `@WebMvcTest` | `./gradlew test --tests "*ArticleControllerTest"` | ✅ |
| FDBK-06 | Picker: 1–9 toggle, Enter applies, Esc cancels without clearing selection | RTL | `npx vitest run src/components/NarrowPicker.test.tsx` | ✅ |
| FDBK-01 | `u`/`d`/Shift+D; Cmd+D ignored; rapid u,d,u out-of-order ends up | hook test | `npx vitest run src/hooks/useKeyboardShortcuts.test.ts src/hooks/useFeedback.test.ts` | ✅ |
| FDBK-02 | Priority row patched in place, `['priority']` never invalidated | hook test | `npx vitest run src/hooks/useFeedback.test.ts` | ✅ |
| FDBK-04 | Effect formatter: top 3, "+N more", one decimal, cap note, no −0.0 | unit | `npx vitest run src/utils/feedback.test.ts` | ✅ |
| FDBK-07 | `/topics/learned`; TopicRow shows base and learned separately | controller + RTL | `./gradlew test --tests "*InterestControllerTest"`; `npx vitest run src/components/TopicRow.test.tsx` | ✅ |
| D-11 | Why row with learned part still sums to badge | unit + RTL | `./gradlew test --tests "*ScoreBreakdownsTest"` | ✅ |
| Config | New blend keys mirrored in test yaml | unit | `./gradlew test --tests "*DevProfileConfigTest"` | ✅ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java` — FDBK-01/02/03 end to end (own name-prefixed feed/topics; assert only on seeded topics)
- [x] `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java` — validation and effects
- [x] `src/main/frontend/src/utils/feedback.test.ts`, `src/hooks/useFeedback.test.ts`, `src/components/FeedbackBar.test.tsx`, `src/components/NarrowPicker.test.tsx`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Toast copy and picker feel | FDBK-04, FDBK-06 | Visual/interaction quality | `bootTestRun` + `npm run dev`; vote on a multi-topic article, check toast and picker — UAT tests 1, 2, 4 passed 2026-09-27 |
| Layout wrap/overlap (toolbar, picker at 240px, toast, no-match strip, Why panel) | FDBK-01, FDBK-05, FDBK-06, D-11 | jsdom cannot observe layout | UAT tests 1, 2, 3, 5, 6 passed 2026-09-27 |
| Concurrent votes leave exactly one row | FDBK-01 | No automated concurrency test; relies on Postgres `INSERT … ON CONFLICT DO UPDATE` row locking | Two curl loops (PUT vote 1 / −1) then GET — UAT test 7 passed 2026-09-27 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-09-27

---

## Validation Audit 2026-09-27

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 18 task rows and 13 requirement rows map to existing test files. Targeted backend run: 152 tests, 0 failures (FeedbackApiIntegrationTest, ArticleFeedbackServiceTest, InterestScoreQueriesTest, ScoreBreakdownsTest, ArticleControllerTest, InterestControllerTest, ArticleServiceTest, PriorityApiIntegrationTest, DevProfileConfigTest). Frontend: 28 files / 314 tests passed; `npx tsc -b` clean. Visual and concurrency truths are manual-only and passed UAT.
