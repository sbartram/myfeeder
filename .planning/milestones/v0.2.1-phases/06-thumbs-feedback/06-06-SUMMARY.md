---
phase: 06-thumbs-feedback
plan: 06
subsystem: api
tags: [spring-boot, jdbcclient, postgres, cte, interest-ranking, thumbs-feedback]

requires:
  - phase: 06-thumbs-feedback
    provides: "06-01 LEARNED_CTE (learned -> eff -> eff2), TopicWeight, learnedSql, LearnedLimit, ArticleFeedbackService; 06-02 contrib columns and FeedbackApiIntegrationTest vote-state tests"
provides:
  - "GET /api/interest/topics/learned -> [{topicId, baseWeight, learned, effectiveWeight, limit}] ordered by topicId"
  - "TopicLearned record (service package)"
  - "InterestScoreQueries.allTopicWeights() : List<TopicWeight> over the eff2 CTE"
  - "ArticleFeedbackService.learnedTopics() : List<TopicLearned> (read-only)"
  - "CLAUDE.md Thumbs feedback bullet and GET /topics/learned route (working tree only, unstaged)"
affects: [06-05, 06-07, 07-tuning]

actuals:
  tokens: 3600
  tasks: 2
  commits: 1
plan_head_before: 0dd1573f2ad390844411844373df03f83a67ebfa

tech-stack:
  added: []
  patterns:
    - "One TOPIC_WEIGHTS_SELECT fragment and one row mapper shared by topicWeights (id filter) and allTopicWeights (all topics), so both read eff2 identically"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
    - src/main/java/org/bartram/myfeeder/controller/InterestController.java
    - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
    - CLAUDE.md (working tree only, not committed)

key-decisions:
  - "topicWeights and allTopicWeights share one private TOPIC_WEIGHTS_SELECT constant and one topicWeight(ResultSet) mapper instead of duplicating the 6-decimal select"
  - "CLAUDE.md had uncommitted user edits before Task 2, so the documentation edit was left unstaged beside them and Task 2 made no commit"

patterns-established:
  - "Learned values for the editor are projections of the eff2 CTE served from ArticleFeedbackService, never stored"

requirements-completed: [FDBK-07]

coverage:
  - id: D1
    description: "GET /api/interest/topics/learned serves {topicId, baseWeight, learned, effectiveWeight, limit} per topic; empty list when there are no topics; GET /topics still serves the topic list"
    requirement: FDBK-07
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java#learnedTopicsAreServed, learnedTopicsEmptyWhenNoTopics, topicsListStillServed"
        status: pass
    human_judgment: false
  - id: D2
    description: "Learned values match the vote effects from the same eff2 CTE (up-vote on rust: base 20.0, learned 1.8, effective 21.8, NONE; unvoted topic learned 0.0 and effective = base), entries in ascending id order, base weights unchanged, and a deleted topic has no entry"
    requirement: FDBK-07
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java#learnedEndpointMatchesTheVoteEffects, learnedEntryDisappearsWithItsTopic"
        status: pass
    human_judgment: false
  - id: D3
    description: "CLAUDE.md documents the feedback endpoints, the feedback field, GET /api/interest/topics/learned and the derived learned model, left unstaged beside the user's own edits"
    verification:
      - kind: other
        ref: "grep -q 'topics/learned' CLAUDE.md && grep -q 'Thumbs feedback' CLAUDE.md; test -z \"$(git diff --cached --name-only)\""
        status: pass
    human_judgment: true
    rationale: "The doc edit is uncommitted by design; the user decides when to commit it alongside their own CLAUDE.md changes"

duration: 3min
completed: 2026-09-27
status: complete
---

# Phase 6 Plan 06: Learned Weights per Topic for the Editor Summary

**`GET /api/interest/topics/learned` returns `[{topicId, baseWeight, learned, effectiveWeight, limit}]` for every topic, ordered by id. The values come from the same eff2 CTE as the Priority sort and the badge, through the new `InterestScoreQueries.allTopicWeights` and `ArticleFeedbackService.learnedTopics`. Nothing on this path writes. CLAUDE.md now documents the thumbs-feedback model; the edit is in the working tree only.**

## Performance

- **Duration:** about 3 min
- **Started:** 2026-09-27T03:50:02Z
- **Completed:** 2026-09-27T03:53Z
- **Tasks:** 2
- **Files modified:** 7 (1 created, 6 modified; CLAUDE.md is not committed)

## Accomplishments

- New `TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight, LearnedLimit limit)` record. This is the JSON contract plan 06-05's topic editor reads.
- `InterestScoreQueries.allTopicWeights()` runs the same 6-decimal eff2 select as `topicWeights` but with no id filter, `ORDER BY e.id`. The two methods now share one `TOPIC_WEIGHTS_SELECT` constant and one row mapper. Only the `learnRate`/`learnedCap` named params are bound (T-06-22).
- `ArticleFeedbackService.learnedTopics()` maps each row through `LearnedLimit.of(w, cap)`. It has no transaction and no write (T-06-10).
- `InterestController` gains `@GetMapping("/topics/learned")`. `/topics/{id}` still maps only PUT and DELETE, so the routes don't collide.
- CLAUDE.md, working tree only:
  - `GET /topics/learned` is added to the Routes bullet.
  - A new "Thumbs feedback" bullet describes the vote endpoints, the `feedback` field, the learned endpoint and the derived learned model.

## Task Commits

1. **Task 1 (tracer): GET /api/interest/topics/learned serves each topic's base, learned and effective weight**: `644a4b2` (feat)
2. **Task 2: CLAUDE.md records the thumbs-feedback model**: no commit. CLAUDE.md already had the user's uncommitted edits (`git diff --quiet -- CLAUDE.md` exited 1), so as the plan requires, the edit is left unstaged beside them.

## Files Created/Modified

- `service/TopicLearned.java` (new): the per-topic learned record
- `repository/InterestScoreQueries.java`: `allTopicWeights()`, the shared `TOPIC_WEIGHTS_SELECT` and `topicWeight(ResultSet)` mapper, and `topicWeights` refactored onto them
- `service/ArticleFeedbackService.java`: `learnedTopics()`
- `controller/InterestController.java`: the `ArticleFeedbackService` dependency and `GET /topics/learned`
- `InterestControllerTest`: a `@MockitoBean ArticleFeedbackService`, plus `learnedTopicsAreServed`, `learnedTopicsEmptyWhenNoTopics` and `topicsListStillServed`
- `FeedbackApiIntegrationTest`: `learnedEndpointMatchesTheVoteEffects` and `learnedEntryDisappearsWithItsTopic`, plus the `learnedEntries`/`assertLearned` helpers (they filter to ids this class seeded, since the container is shared)
- `CLAUDE.md`: edited in the working tree, not committed

## Decisions Made

- `topicWeights` and `allTopicWeights` share one select fragment and one mapper, so the editor and the vote effects can never read different columns or rounding.
- The Task 2 guard found CLAUDE.md already modified, so the file was neither staged nor committed.

## Deviations from Plan

### Process notes

**1. The CLAUDE.md edit touches one line the user had also changed.** The user's uncommitted diff already modified the "**Routes** under `/api/interest`" line. The plan requires adding `GET /topics/learned` to that line, so its text now differs from the user's version by that one inserted route. All other user-edited lines are byte-identical; this was checked by comparing every `+` line of the saved user diff against the file. Nothing of the user's was removed.

---

**Total deviations:** 0 auto-fixed, 1 process note. **Impact on plan:** none.

## Issues Encountered

None. The Testcontainers SSL flake did not occur. The TruffleHog pre-commit hook passed on the Task 1 commit.

## Verification

- Task 1 `<verify>`:
  - The Gradle run for `InterestControllerTest` and `FeedbackApiIntegrationTest` passed: 12 and 18 tests, 0 failures.
  - Both JUnit XML checks exited 0.
- Task 1 acceptance greps: `@GetMapping("/topics/learned")` = 1, `record TopicLearned` = 1, `allTopicWeights` >= 1.
- Tracer gate: the run was interactive with `end-of-phase` and an automated-only `<verify>`. The verify was re-run and passed, then Task 2 continued.
- Task 2 `<verify>`: both the grep and the empty-cache check exit 0. `grep -c 'Thumbs feedback' CLAUDE.md` = 1. `git status --short` lists CLAUDE.md as ` M` (unstaged).
- Plan `<verification>`: the full backend suite `./gradlew test -x npmBuild -x npmInstall` ran 519 tests with 0 failures and 0 errors. The 2 skips are the existing live-API tests. `.claude/CLAUDE.md`, `.envrc`, `.planning/config.json` and `CLAUDE.md` are still modified and unstaged.
- Prohibition check: `InterestScoreQueries` and `ArticleFeedbackService` contain no `UPDATE interest_topic` or `INSERT INTO interest_topic`. The integration test asserts that base weights are unchanged.
- The five execution_context files were read in full at the start: execute-plan.md, summary.md, checkpoints.md, worktree-path-safety.md and executor-examples.md. The project-root pin guard ran before the first write and before the commit.

## User Setup Required

None. No external service configuration required. Commit the CLAUDE.md documentation edit together with your own CLAUDE.md changes whenever you choose.

## Next Phase Readiness

- Plan 06-05's topic editor can fetch `GET /api/interest/topics/learned` (query key `['interest', 'learned']`) and look up its own `topicId`.
- No blockers.

## Self-Check: PASSED

- `src/main/java/org/bartram/myfeeder/service/TopicLearned.java` exists.
- Commit `644a4b2` is in git log.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*
