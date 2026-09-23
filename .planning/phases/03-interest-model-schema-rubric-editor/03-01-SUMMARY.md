---
phase: 03-interest-model-schema-rubric-editor
plan: 01
subsystem: database
tags: [postgres, flyway, spring-data-jdbc, testcontainers, interest-scoring]

requires:
  - phase: 02
    provides: JevApiClient and Jev resilience wiring (consumers of this schema arrive in 03-02..03-04 and Phase 4)
provides:
  - V6__interest_scoring.sql, the milestone's last migration, with all six interest tables
  - InterestProfile entity + InterestProfileRepository (seeded singleton, id = 1)
  - InterestTopic entity + InterestTopicRepository.findAllOrdered()
affects: [03-03 InterestService, 03-04 status/preview, phase-04 score writer and re-score, phase-05 priority view, phase-06 feedback and learned adjustment]

actuals:
  tokens: 5200
  tasks: 3
  commits: 2
plan_head_before: 71522089355f883e616705332de22eec785bf522

tech-stack:
  added: []
  patterns:
    - "Singleton table: INTEGER PK DEFAULT 1 CHECK (id = 1), seeded in the migration, read via findById(1)"
    - "Plain version column (no @Version); the service bumps it only on meaningful text changes"
    - "article_topic_score hangs off article_score, not article, so deleting a score row removes its nouls"

key-files:
  created:
    - src/main/resources/db/migration/V6__interest_scoring.sql
    - src/main/java/org/bartram/myfeeder/model/InterestProfile.java
    - src/main/java/org/bartram/myfeeder/repository/InterestProfileRepository.java
    - src/main/java/org/bartram/myfeeder/model/InterestTopic.java
    - src/main/java/org/bartram/myfeeder/repository/InterestTopicRepository.java
    - src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java
    - src/test/java/org/bartram/myfeeder/repository/InterestTopicRepositoryTest.java
  modified: []

key-decisions:
  - "03-01: V6 topic name is required (name TEXT NOT NULL), user-confirmed during plan-phase on 2026-09-23"
  - "03-01: User accepted both fixed V6 choices: article_topic_score.article_id REFERENCES article_score(article_id) ON DELETE CASCADE, and INTEGER columns with no DB length CHECKs (text limits enforced in the service)"

patterns-established:
  - "One expected constraint violation per @DataJdbcTest method, as its last statement (Postgres aborts the transaction)"

requirements-completed: [INT-01, INT-02]

coverage:
  - id: D1
    description: "V6 migration creates all six interest tables with their constraints and cascades"
    requirement: INT-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java (16 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Seeded singleton profile row reads and updates in place through InterestProfileRepository"
    requirement: INT-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java#seededProfileRoundTripsThroughRepository"
        status: pass
    human_judgment: false
  - id: D3
    description: "Topics persist, list in id order, keep their version on weight-only saves, and the weight CHECK backstops the repository"
    requirement: INT-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestTopicRepositoryTest.java (4 tests)"
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 01: Interest Model Schema Summary

**V6 Flyway migration with all six interest-scoring tables (singleton profile, topics weighted -50..+50, scores with SKIPPED, nouls that cascade from score rows, votes with topic narrowing), plus the InterestProfile/InterestTopic entities and repositories, proven by 20 Testcontainers tests**

## Performance

- **Duration:** 3 min (continuation run, after the Task 1 decision)
- **Started:** 2026-09-23T17:44:14Z
- **Completed:** 2026-09-23T17:47:16Z
- **Tasks:** 3 (1 decision checkpoint, 1 tracer, 1 auto)
- **Files modified:** 7 (all new)

## V6 decision

Topic name: required-name, confirmed by the user during plan-phase on 2026-09-23

User reply on fixed choices 1 and 2 (verbatim, chosen via prompt): "Accept both (Recommended)"

That accepts fixed choice 1 (`article_topic_score.article_id REFERENCES article_score(article_id) ON DELETE CASCADE`) and fixed choice 2 (INTEGER columns; no DB length CHECKs, text limits enforced in the service). Task 2 wrote V6 with both choices exactly as stated.

## Accomplishments

- `V6__interest_scoring.sql` creates `interest_profile` (seeded id=1, `CHECK (id = 1)`), `interest_topic` (`name TEXT NOT NULL`, `weight INTEGER NOT NULL DEFAULT 20 CHECK (weight BETWEEN -50 AND 50)`), `article_score` (status SCORED/FAILED/SKIPPED), `article_topic_score` (child of `article_score`, noul in [0, 1]), `article_feedback` (vote ±1, `topics_narrowed BOOLEAN NOT NULL DEFAULT false`) and `article_feedback_topic`, plus two topic_id indexes
- The seeded profile row round-trips through `InterestProfileRepository`: `save()` updates it in place, and there is still exactly one row afterwards
- `InterestTopicRepository.findAllOrdered()` returns topics by id ascending. A weight-only save leaves `version` at 1, and weight 51 is rejected by `interest_topic_weight_check`
- All 34 repository-package tests pass, so the V1-V5 repository tests are unaffected

## Task Commits

1. **Task 1: Confirm V6's two remaining one-way choices.** Decision checkpoint; no files or commit. The user answered "Accept both (Recommended)"
2. **Task 2 (tracer): V6 applies at startup and the seeded profile row round-trips.** `6d19527` (feat)
3. **Task 3: Topics persist through InterestTopicRepository in id order with a stable version.** `ca4757c` (feat)

## Files Created/Modified

- `src/main/resources/db/migration/V6__interest_scoring.sql`: the complete interest-scoring schema
- `src/main/java/org/bartram/myfeeder/model/InterestProfile.java`: singleton profile entity (plain `version`)
- `src/main/java/org/bartram/myfeeder/repository/InterestProfileRepository.java`: `ListCrudRepository<InterestProfile, Integer>`
- `src/main/java/org/bartram/myfeeder/model/InterestTopic.java`: topic entity (name, description, weight, plain `version`, timestamps)
- `src/main/java/org/bartram/myfeeder/repository/InterestTopicRepository.java`: `findAllOrdered()` via `@Query`
- `src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java`: 16 schema, constraint, cascade and round-trip tests
- `src/test/java/org/bartram/myfeeder/repository/InterestTopicRepositoryTest.java`: 4 repository tests

## Decisions Made

- The topic name is required (`name TEXT NOT NULL`), as the user confirmed during plan-phase
- The user accepted both fixed choices (see "V6 decision" above)
- V6 uses the V2 house style, one space between column tokens and no alignment padding. The plan's acceptance greps (`name TEXT NOT NULL`, `topics_narrowed BOOLEAN NOT NULL DEFAULT false`) require that exact spacing

## Notes for later phases

- **Phase 4 (score writer):** insert the `article_score` row before its `article_topic_score` rows, in one transaction, because `article_topic_score.article_id` is a foreign key to `article_score`. "Re-score unread" should delete `article_score` rows and let the cascade remove the nouls. Do not delete from `article_topic_score` separately.
- **Phase 6 (learned adjustment):** the learned-adjustment CTE must honor `article_feedback.topics_narrowed` and `article_feedback_topic` (D-02). When `topics_narrowed` is true, only the child rows count, and zero child rows penalize nothing.
- **03-03 (InterestService):** set `version`, `createdAt` and `updatedAt` explicitly on insert (Pitfall 2). Enforce the 2,000/40/500-character limits and the weight range in the service, because the database only backstops the weight range.

## Deviations from Plan

None. The plan was executed as written.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- The storage layer for profile and topics is ready for 03-03 (InterestService/controller) and for 03-04's cold-start predicate
- No blockers

## Self-Check: PASSED

- All 7 created files exist on disk
- Commits `6d19527` and `ca4757c` exist
- Plan verification passed: `./gradlew test --tests 'org.bartram.myfeeder.repository.*'` ran 34 tests with 0 failures

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*
