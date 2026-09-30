---
phase: 10-explainable-engagement-in-the-ui
plan: 04
subsystem: testing
tags: [spring-boot, integration-test, priority, engagement, docs]

requires:
  - phase: 10-explainable-engagement-in-the-ui
    provides: "10-01 afterEngagement / invalidateAfterLearnedChange; 10-02 engagementReplaced, engagementAtCap, D-10 LearnedLimit order; 10-03 final toast, Why N? and Interests wording"
  - phase: 09-engagement-learning-model
    provides: "engagement term in the learned CTE; thumbsWeight/engagementWeight on breakdown rows"
provides:
  - "SC-3 proof over HTTP: after a star and after Forget, the engaged article's Priority position, by-id score, breakdown display and point sum, and list badge agree (68 -> 69 -> 68)"
  - "priorityPageAfter Javadoc states the skip limit the Ranking changed hint covers (D-09, IN-04 fixed)"
  - "CLAUDE.md documents the Phase 10 reaction, D-10 order, new fields and UI wording"
  - "STATE.md Phase 9 review line lists WR-02, IN-02 and IN-03 as the remaining open findings"
affects: [phase 10 verification and UAT, 11-topic-suggestions, 12-calibration]

actuals:
  tokens: 3354     # chars/4 over the realized diff (13,415 chars, 5 files)
  tasks: 2
  commits: 2
plan_head_before: cece5d2b71f88c4dbde9a2a4d5797a852602ec19
plan_head_after: 3d2583e0b9750b888eb7c393603e8c674577562f

tech-stack:
  added: []
  patterns:
    - "Cross-source agreement assertion: one helper checks by-id, breakdown, list and a full Priority walk for the same article in one pass"

key-files:
  created: []
  modified:
    - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - .planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md
    - CLAUDE.md
    - .planning/STATE.md

key-decisions:
  - "SC-3 is exercised through the real capture routes (star PATCH, Forget DELETE), not a direct article_engagement insert, so capture, blend and every read path are covered together"
  - "CLAUDE.md words the star trigger as 'a starred: true update only', matching useArticles.ts (the client reacts to any starred: true request; the server records STAR only on an unstarred-to-starred change)"

patterns-established:
  - "An unloaded Priority row whose score rises above the cursor is an accepted, documented limitation (D-09); the hint prompts the refresh"

requirements-completed: [LRN-06]

coverage:
  - id: D1
    description: "After a star, and again after Forget, the engaged article's score agrees across the Priority walk, GET by id, breakdown display, breakdown point sum and the feed list (68 -> 69 -> 68), its TOPIC row carries engagementWeight 0.9 then 0.0 and thumbsWeight 0.0, and the peer/engaged Priority order flips and flips back"
    requirement: LRN-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#engagedArticleAgreesEverywhereAfterRefresh"
        status: pass
      - kind: integration
        ref: "./gradlew test -x npmBuild -x npmInstall --tests org.bartram.myfeeder.controller.PriorityApiIntegrationTest (7 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D2
    description: "priorityPageAfter Javadoc says the literal-tuple compare only protects the cursor row, and an unloaded row whose score rises is skipped until refresh, which the Ranking changed hint prompts; IN-04 fixed"
    verification:
      - kind: other
        ref: "grep 'until the user refreshes' InterestScoreQueries.java; ! grep 'score change between pages cannot skip'; IN-04 disposition: fixed; diff touches only Javadoc lines"
        status: pass
    human_judgment: false
  - id: D3
    description: "CLAUDE.md documents the post-engagement reaction, the engagementReaction hooks, engagementReplaced/engagementAtCap, the D-10 LearnedLimit order and the final UI wording; STATE.md's Phase 9 line names WR-02, IN-02 and IN-03 as open"
    verification:
      - kind: other
        ref: "Task 2 verify grep (afterEngagement, LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE, engagementReplaced, engagementAtCap, invalidateAfterLearnedChange, (replaces engagement), no 'until Phase 10 words it', >= 4 fixed dispositions)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Full backend and frontend suites green"
    verification:
      - kind: other
        ref: "./gradlew test -x npmBuild -x npmInstall (657 tests, 0 failures, 2 known opt-in skips)"
        status: pass
      - kind: other
        ref: "cd src/main/frontend && npx tsc -b && npx vitest run (33 files, 383 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Manual end-to-end UAT on /priority: open, star, board add, Raindrop save and Forget patch the row in place and light the hint; pressing the hint re-ranks so position, badges and Why N? agree; the vote toast reads (replaces engagement) / (engagement restored); the Interests line reads Learned … (votes …, engaged …) · Effective weight …"
    requirement: LRN-06
    verification: []
    human_judgment: true
    rationale: "Task 2's <human-check>: real-browser feel (no list jump, hint visibility, wording next to the rest of the UI) is not asserted by automated tests; human_verify_mode is end-of-phase, so the verifier harvests it into UAT"

duration: 4min
completed: 2026-09-30
status: complete
---

# Phase 10 Plan 04: SC-3 agreement proof, skip-limit Javadoc and Phase 10 docs Summary

**A new HTTP integration test shows that a star and then a Forget move the engaged article from 68 to 69 and back to 68 in the Priority walk, the by-id score, the "Why N?" display and point sum, and the list badge, with its Priority position flipping past a newer peer and back. `priorityPageAfter`'s Javadoc now states the skip limit that the "Ranking changed" hint covers. CLAUDE.md documents the Phase 10 reaction, the D-10 limit order and the final UI wording.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-09-30T20:46:52Z
- **Completed:** 2026-09-30T20:50:11Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- `PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh` seeds two SCORED articles at 68. The peer is newer, so it ranks first. The test then stars the engaged article over HTTP and forgets the engagement over HTTP, and checks every read source after each step. The numbers match the plan's arithmetic exactly: 50 + 18 = 68, then 20.9 × 0.9 = 18.81, which rounds to 19, so 69, with engagementWeight 0.9. No fixture was tuned.
- `InterestScoreQueries.priorityPageAfter` Javadoc no longer claims that a score change between pages can never skip rows. It says the tuple compare protects only the cursor row, and that an unloaded row whose score rises is skipped until refresh, which the hint prompts (D-09). Only comment lines changed.
- `09-REVIEW-DISPOSITION.md` marks IN-04 `fixed` (`open: 3`). WR-01, WR-03, IN-01 and IN-04 are now all fixed.
- CLAUDE.md changes:
  - A "Post-engagement reaction (Phase 10)" block under Engagement capture, and the Opens bullet points to it.
  - The hooks list names `engagementReaction`.
  - Thumbs feedback covers `engagementReplaced` and `engagementAtCap`.
  - The `LearnedLimit.of` bullet gives the D-10 order.
  - A new "UI wording (Phase 10)" sub-list replaces the Phase 9 fall-through sentence.
- STATE.md: only the Phase 9 review Blockers/Concerns line changed. It now names WR-02 (open until Phase 12), IN-02 and IN-03 as the findings still open.

## Task Commits

1. **Task 1 (tracer): SC-3 proof, skip-limit Javadoc, IN-04 fixed** - `78e73d7` (test). Tracer gate: interactive run, `end-of-phase` mode, automated-only verify. I re-ran the verify, it passed with 7/7, and then I moved on to Task 2.
2. **Task 2: CLAUDE.md and STATE.md describe Phase 10; full suites green** - `3d2583e` (docs)

**Plan metadata:** committed with this SUMMARY (docs commit).

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java` - the new test, plus the helpers `assertEverySourceAgrees`, `walkPriority` and `scoreOf`, and the `delete` import
- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java` - the `priorityPageAfter` Javadoc (comment lines only)
- `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md` - IN-04 marked fixed
- `CLAUDE.md` - the Phase 10 behavior and wording
- `.planning/STATE.md` - the Phase 9 review line

## Decisions Made

- The test adds no `@MockitoBean JevApiClient`. It reuses the class's existing Spring context, and the star route never calls Jev.
- I left out the plan's "unstarred to starred only" wording for the star trigger. The client calls `afterEngagement` on any `starred: true` update (`useArticles.ts:65`), and the server records STAR only on an actual change. CLAUDE.md now says "a `starred: true` update only" so it matches the code.

## Deviations from Plan

None. The plan ran as written, apart from the wording choice above.

## Issues Encountered

None.

## Human Check (end-of-phase UAT)

Task 2's `<human-check>` is recorded here for the verifier to harvest. It is not a stop, because `human_verify_mode` is `end-of-phase`. Start `./gradlew bootTestRun` (export `MYFEEDER_TYPESAFE_API_KEY` first if you want live scoring) and `cd src/main/frontend && npm run dev`. Then, on /priority, pick a scored article and check:
1. Press `o`. The tab opens at once, the refresh button turns into "↻ Ranking changed — refresh", the row keeps its place, and its badge and "Why N?" update.
2. Star it, add it to a board with `b`, save it to Raindrop if configured, then use Forget. Each step behaves the same way. A second open that doesn't change the score lights nothing.
3. Press the hint. The list re-ranks, and these all agree: the row's position, its badge in the Priority and feed lists, the reading pane badge and "Why N?".
4. Vote 👍 on an engaged article. The toast reads "(replaces engagement)", and removing the vote reads "(engagement restored)".
5. Open Interests. A topic's line reads "Learned … (votes …, engaged …) · Effective weight …".

## Verification

- `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.PriorityApiIntegrationTest"`: 7 tests, 0 failures, including every existing test
- Task 1 grep chain: PASS. The diff of `InterestScoreQueries.java` touches only Javadoc lines.
- Task 2 grep chain: PASS. It also finds `invalidateAfterLearnedChange` and `(replaces engagement)`.
- Full backend: `./gradlew test -x npmBuild -x npmInstall`, BUILD SUCCESSFUL, 657 tests, 0 failures, 2 known skips. That is 656 before this plan plus the new test.
- Full frontend: `npx tsc -b` is clean, and `npx vitest run` passes 33 files and 383 tests.
- `SPRING_PROFILES_ACTIVE` and `SPRING_AI_TYPESAFE_*` were unset for every run.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All four Phase 10 plans are complete. The phase is ready for `/gsd-verify-work` and the UAT above.
- Phase 9 review findings still open, none blocking: WR-02 (Phase 12 replay), IN-02 and IN-03.

---
*Phase: 10-explainable-engagement-in-the-ui*
*Completed: 2026-09-30*
