---
phase: 11-gap-discovery
plan: 01
subsystem: api
tags: [spring-boot, jdbcclient, postgres, interest-ranking, gap-discovery, configuration-properties]

requires:
  - phase: 08-engagement-capture
    provides: article_engagement rows (first created_at per kind) and the V7 topic_suggestion_dismissal table
  - phase: 05-priority-view
    provides: InterestScoreQueries.displayScores (the badge from the Priority blend)
provides:
  - "GET /api/interest/suggestions -> {items: [{articleId, title, feedTitle, interestScore}], total}"
  - TopicSuggestionStore.candidates(cutoff, nearMiss), the D-08 gap predicate and the only reader of topic_suggestion_dismissal
  - TopicSuggestionService.list() with the D-05 order, cap 10 (MAX_SUGGESTIONS) and total; WINDOW_DAYS = 30
  - myfeeder.interest.suggestions.near-miss (0.35), refused at startup unless 0 < near-miss <= 0.5 (SUGGESTIONS_INVALID)
affects: [11-02 dismiss and topic-create handling, 11-03 Suggested topics UI, 12 calibration]

actuals:
  tokens: 9851
  tasks: 3
  commits: 3
plan_head_before: e02611503418fa059137a17c0724ef1b8300519b
plan_head_after: 593d1b7017a2d1b96e51dd65eda72c42b2a512d3

tech-stack:
  added: []
  patterns:
    - "Two-step derived list: a new JdbcClient store runs the predicate, and the existing displayScores supplies the badge, so the ranking SQL stays byte-for-byte unchanged"
    - "Config constant that is a sibling of blend (interest.suggestions), validated through the existing MyfeederProperties Validator with a fixed-text refusal"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java
    - src/main/java/org/bartram/myfeeder/service/TopicSuggestion.java
    - src/main/java/org/bartram/myfeeder/service/TopicSuggestions.java
    - src/main/java/org/bartram/myfeeder/service/TopicSuggestionService.java
    - src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java
    - src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java
    - src/test/java/org/bartram/myfeeder/service/TopicSuggestionServiceTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java

key-decisions:
  - "The candidate SQL lives in a new TopicSuggestionStore, never in InterestScoreQueries (a file-text guard forbids the dismissal table name there)"
  - "The service drops a candidate whose badge is absent or null (Pitfall 8) and counts total after that filter"
  - "WINDOW_DAYS = 30 and MAX_SUGGESTIONS = 10 are Java constants; only near-miss is a yaml constant (D-09)"

patterns-established:
  - "Suggestion classes import nothing from the integration package, and their tests verify JevApiClient.judge is never called"

requirements-completed: [GAP-01, GAP-04, GAP-05]

coverage:
  - id: D1
    description: "GET /api/interest/suggestions lists engaged, SCORED, unvoted, undismissed, unmatched articles with title, feed title and badge, and picks up a new engagement on the next call; served with Jev unconfigured"
    requirement: GAP-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#listsAnEngagedScoredUnmatchedArticle"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#leavesOutUnscoredUnengagedAndMatchedArticles"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#anArticleEngagedAfterAListingAppearsOnTheNextOne"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java (unscored/FAILED/SKIPPED, any vote, any dismissal, window, one row per article, read state)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Near-miss is exclusive at the threshold, over the best noul of every topic row whatever the weight sign; an article with no topic rows counts as 0"
    requirement: GAP-04
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java#nearMissIsExclusiveAtTheThreshold"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java#aMatchedArticleIsNot"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java#theBestNoulAcrossTopicsCounts"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java#aNegativeWeightTopicCoversTheArticle"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java#anArticleWithNoTopicRowsIsACandidate"
        status: pass
    human_judgment: false
  - id: D3
    description: "The list path never calls Jev and leaves the ranking SQL and replay unchanged"
    requirement: GAP-05
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java#neverCallsJev (@AfterEach)"
        status: pass
      - kind: other
        ref: "git hash-object InterestScoreQueries.java = 807baf80e93c36b519a44cfc92eb5d9c052cb305; scripts/interest-calibration-replay.sql = 59333601e2cfd71ccaaa6f634a72670f3913efef"
        status: pass
      - kind: integration
        ref: "V7EngagementMigrationTest (16) and InterestCalibrationReplaySqlTest (14), unmodified"
        status: pass
    human_judgment: false
  - id: D4
    description: "Order is badge ascending, then latest engagement newest first, then id descending; at most 10 items with a total of every qualifying article; a candidate with no badge is dropped"
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/TopicSuggestionServiceTest.java (6 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "myfeeder.interest.suggestions.near-miss is 0.35 in main and test yaml, and startup is refused with fixed text unless 0 < near-miss <= 0.5"
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java (defaultsBindTheNearMiss, shippedMainYamlStarts, nearMissOutOfRangeIsRefused, nearMissInRangeStarts, nearMissRefusalTextIsFixed)"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java (unmodified; application-dev.yaml hash 83628ebad0058a84ff3ec88b8f9b9b836cb5947a)"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-10-01
status: complete
---

# Phase 11 Plan 01: Suggested Topics Backend Summary

**`GET /api/interest/suggestions` lists engaged, SCORED, unvoted, undismissed articles whose best noul is below a validated 0.35 near-miss. A new JdbcClient store finds the candidates, and the unchanged `displayScores` blend supplies each badge. The list is ordered badge ascending, capped at 10, carries a total, and calls Jev nowhere.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-10-01T22:32:11Z
- **Completed:** 2026-10-01T22:38:31Z
- **Tasks:** 3
- **Files modified:** 12 (8 created, 4 modified)

## Accomplishments

- `TopicSuggestionStore.CANDIDATES` implements the D-08 predicate in one statement:
  - any engagement kind, with the latest `created_at` strictly after the 30-day cutoff
  - a SCORED row
  - no vote and no dismissal
  - `COALESCE(MAX(noul), 0) < near-miss` over every topic row
- `TopicSuggestionService.list()` gets each badge from `InterestScoreQueries.displayScores`, the number `InterestBadge` shows. It drops candidates with no badge, sorts badge asc / engagedAt desc / id desc, and returns the first 10 plus the total.
- `TopicSuggestionController` serves `GET /api/interest/suggestions`. It is a separate controller, so the `InterestController` slice test needs no new bean.
- `myfeeder.interest.suggestions.near-miss: 0.35` is committed in main and test yaml. `MyfeederProperties` refuses startup with the fixed text `SUGGESTIONS_INVALID` unless `0 < near-miss <= 0.5`.
- `InterestScoreQueries.java`, the calibration replay and `application-dev.yaml` are byte-for-byte unchanged.

## Task Commits

1. **Task 1 (tracer): GET /api/interest/suggestions end to end** - `14b8fad` (feat)
2. **Task 2: predicate, window, near-miss and order at every edge** - `d41a86b` (test)
3. **Task 3: near-miss yaml constant with startup validation** - `593d1b7` (feat)

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java`: the D-08 candidate query, and the only reader of `topic_suggestion_dismissal`
- `src/main/java/org/bartram/myfeeder/service/TopicSuggestion.java`: one row `{articleId, title, feedTitle, interestScore}`
- `src/main/java/org/bartram/myfeeder/service/TopicSuggestions.java`: the response `{items, total}`
- `src/main/java/org/bartram/myfeeder/service/TopicSuggestionService.java`: badges, order, cap and total
- `src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java`: `GET /api/interest/suggestions`
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`: `Interest.Suggestions.nearMiss`, `isValid()`, `SUGGESTIONS_INVALID`
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml`: `suggestions.near-miss: 0.35`
- `src/test/java/.../controller/TopicSuggestionApiIntegrationTest.java`: 3 HTTP tests, with `neverCallsJev` after each
- `src/test/java/.../repository/TopicSuggestionStoreTest.java`: 14 real-Postgres predicate tests
- `src/test/java/.../service/TopicSuggestionServiceTest.java`: 6 Mockito order, cap and total tests
- `src/test/java/.../config/MyfeederPropertiesValidationTest.java`: 4 near-miss tests added, and `shippedMainYamlStarts` extended

## Decisions Made

- The service drops a candidate when `badges.get(id)` is null, so both an absent key and a null value are dropped (Pitfall 8). `total` is counted after that filter.
- Otherwise the plan was followed as specified: the RESEARCH §1 SQL verbatim, and the §2 service logic.

## TDD Notes

- **Task 2:** this is the plan's intended shape, since the behavior shipped in the Task 1 tracer, so a RED phase could not be observed. To show the tests discriminate anyway, I made a temporary mutation: `HAVING ... > :cutoff` became `>=`, and `< CAST(:nearMiss ...)` became `<=`. Three tests failed against it: `nearMissIsExclusiveAtTheThreshold`, `aMatchedArticleIsNot` and `theWindowIsStrictOnTheLatestEngagement`. The file was then restored from git and the tests passed again.
- **Task 3:** valid RED. With only the `SUGGESTIONS_INVALID` constant added, `nearMissOutOfRangeIsRefused` and `nearMissRefusalTextIsFixed` failed on their `hasFailed()` assertions. Adding `isValid()`, the `validate` branch and the yaml made them GREEN.

## Deviations from Plan

None in what was built: the plan was executed as written.

- **Process note:** the sandbox blocks writes under `.git/worktrees/...`, so the protocol 0c ledger file could not be persisted. `commits` was measured directly as `git rev-list --count e026115..HEAD` = 3, from the spawn base the orchestrator supplied.

## Verification

- Plan-level suite, all green (73 tests):
  - TopicSuggestionApiIntegrationTest: 3
  - TopicSuggestionStoreTest: 14
  - TopicSuggestionServiceTest: 6
  - MyfeederPropertiesValidationTest: 16
  - DevProfileConfigTest: 4
  - V7EngagementMigrationTest: 16
  - InterestCalibrationReplaySqlTest: 14
- Full backend suite: 78 classes, 684 tests, 0 failures, 0 errors. The 2 skips are pre-existing and none is in this plan's classes.
- Blob hashes are unchanged:
  - `InterestScoreQueries.java` 807baf80…
  - `interest-calibration-replay.sql` 59333601…
  - `application-dev.yaml` 83628eba…
- Import guard: no suggestion class imports `org.bartram.myfeeder.integration.*`.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- Plan 11-03 (frontend) can consume `GET /api/interest/suggestions` exactly as specified: `{items: [{articleId, title, feedTitle, interestScore}], total}`.
- Plan 11-02 can add `handle(articleId, reason)` to `TopicSuggestionStore` and a dismiss route on `TopicSuggestionController`, as research §1, §3 and §4 sketch.

---
*Phase: 11-gap-discovery*
*Completed: 2026-10-01*

## Self-Check: PASSED
