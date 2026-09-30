---
phase: 09-engagement-learning-model
plan: 01
subsystem: database
tags: [postgres, cte, spring-boot, configuration-properties, validation, calibration-replay, psql]

# Dependency graph
requires:
  - phase: 08-engagement-capture
    provides: article_engagement table (V7) and the STAR/BOARD/RAINDROP/OPEN_ORIGINAL capture paths
provides:
  - "Extended LEARNED_CTE: engaged (one MAX strength per article, vote override), eng_learned (SCORED only, no window), eff.eng_raw/eng (zero floor, negative-base skip), eff2.w_thumbs and one clamp on base + learned + eng"
  - "contrib carries e.w_thumbs (for the 09-03 split)"
  - "JdbcClient parameters engagementOpenWeight, engagementSaveWeight, engagementCap bound in learnedSql"
  - "MyfeederProperties.Interest.Blend.Engagement {openWeight 0.25, saveWeight 0.5, cap 8} with isValid(learnedCap)"
  - "MyfeederProperties implements Validator; fixed-text ENGAGEMENT_INVALID refusal at startup"
  - "myfeeder.interest.blend.engagement.* in main and test application.yaml (identical literals)"
  - "Regenerated replay SQL (5 verbatim blend lines; learned section appends eng_raw, eng, eng_at_cap) and driver ENGAGEMENT_* env vars with validate-before-connect"
affects: [09-03, 09-04, 09-05, 09-06, 10, 12]

# Actuals (#2632)
actuals:
  tokens: 13474
  tasks: 2
  commits: 3
plan_head_before: e0cd84ba7f008bb7bb76b05d20870552e23a1f7a
plan_head_after: 4ec0607d3cf86a51166a861a35a96b58fafb9afe

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Self-validating @ConfigurationProperties: the bound class implements org.springframework.validation.Validator, so Boot's binder checks it in every context that binds it (no @Validated, no JSR-303)"
    - "Replay lines regenerated from the Java by a throwaway generator test plus a script, never hand-typed"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - scripts/interest-calibration-replay.sql
    - scripts/interest-calibration-replay.sh
    - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
    - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java

key-decisions:
  - "LEARNED_CTE copied character for character from 09-RESEARCH.md 'The tested LEARNED_CTE' (diffed identical), including the GREATEST(0, ...) zero floor so cap 0 disables engagement whatever the weights"
  - "The RED commit of Task 2 declared ENGAGEMENT_INVALID alone (no validator) so the tests compiled and failed on startup assertions, not on a compile error"

patterns-established:
  - "Startup refusal tests: ApplicationContextRunner + @EnableConfigurationProperties(MyfeederProperties.class), hasFailed() + hasStackTraceContaining(fixed text)"
  - "Replay drift guard: every named blend parameter (?<![:\\w]):name must appear as '-v name=' in the driver"

requirements-completed: [LRN-01, LRN-05, CAL-01]

coverage:
  - id: D1
    description: "Opening, starring and boarding a SCORED article raises its badge 68 -> 69 and its breakdown topic weight 20.0 -> 20.9 over HTTP, with no topic-weight write and no Jev call"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java#engagementRaisesTheRankingWithoutWritingWeightsOrCallingJev"
        status: pass
    human_judgment: false
  - id: D2
    description: "Ranking SQL and replay read article_engagement and never topic_suggestion_dismissal"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java#rankingSqlReadsEngagementButNotDismissals"
        status: pass
    human_judgment: false
  - id: D3
    description: "Existing learned-model, feedback and Priority behavior is unchanged with no engagement rows (InterestScoreQueriesTest, FeedbackApiIntegrationTest, PriorityApiIntegrationTest)"
    requirement: LRN-01
    verification:
      - kind: integration
        ref: "./gradlew test -x npmBuild -x npmInstall (613 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Replay SQL reproduces the extended blend byte for byte (5 learned-CTE statements, 4 badge copies), stays read-only, and the driver passes and validates every blend parameter"
    requirement: CAL-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#everyCopyInTheReplayIsVerbatim"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverPassesEveryBlendParameter"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverRejectsANonNumericEngagementValue"
        status: pass
    human_judgment: false
  - id: D5
    description: "D-01 constants committed in main and test yaml; startup refuses inconsistent engagement values with fixed text while cap 0 always starts"
    requirement: LRN-05
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java (12 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java"
        status: pass
    human_judgment: false

# Metrics
duration: 7min
completed: 2026-09-30
status: complete
---

# Phase 9 Plan 01: Engagement Learning Model Core Summary

**Engagement becomes a capped, zero-floored fractional up-vote in `LEARNED_CTE` (one MAX strength per article, thumbs override, SCORED only, one clamp on base + thumbs + engagement). The constants are bound from self-validating yaml (0.25 / 0.5 / 8), and the calibration replay is regenerated byte for byte in the same commit.**

## Performance

- **Duration:** about 7 min
- **Started:** 2026-09-30T16:15:01Z
- **Completed:** 2026-09-30T16:22:19Z
- **Tasks:** 2
- **Files modified:** 10 (1 created, 9 modified)

## Accomplishments

- SC-1 over HTTP: open + star + board on a scored article moves its badge from 68 to 69 and its topic weight from 20.0 to 20.9. The three kinds count once, as a save. `interest_topic.weight` stays 20 and Jev is never called.
- `LEARNED_CTE` extended with the tested research SQL (diffed identical): `engaged`, `eng_learned`, `eng_raw`/`eng` with the zero floor and the negative-base skip, `w_thumbs` (byte-identical to the v0.2.1 `w`) and one clamp for `w`. `contrib` carries `e.w_thumbs`, and `learnedSql` binds the three engagement constants.
- Replay regenerated from the Java by a temporary generator and a script, never hand-typed:
  - The five `WITH learned AS` lines are regenerated.
  - The learned section appends `eng_raw`, `eng` and `eng_at_cap`.
  - The driver validates `ENGAGEMENT_*` with the decimal regex before the password check, and passes each as `-v`.
- The Phase 8 guards are inverted in the same commit as the CTE (CAL-01).
- `MyfeederProperties implements Validator`:
  - cap 0 always starts.
  - Otherwise it requires `0 <= open < save < 1` and `0 < cap < learned-cap`.
  - NaN and negative values are refused.
  - The refusal text `ENGAGEMENT_INVALID` is fixed and never echoes the bound value (T-09-04).
- Main and test yaml carry identical D-01 literals. `application-dev.yaml`, Helm and deploy.sh are unchanged against main.

## Task Commits

1. **Task 1 (tracer): engagement raises the ranking; replay and Phase 8 guards move together:** `b9546a7` (feat). All six files are in one commit (CAL-01).
2. **Task 2 (TDD): D-01 constants in yaml, startup refusal, driver drift checks:**
   - RED `f168acc` (test)
   - GREEN `4ec0607` (feat)
   - No refactor was needed.

**Plan metadata:** committed with this SUMMARY (docs)

## TDD Gate Compliance

- **Task 1 (tracer).** RED was run and observed but not committed, per the plan: CAL-01 requires the six files in one green commit.
  - `engagementRaisesTheRankingWithoutWritingWeightsOrCallingJev` failed with `expected: 69 but was: 68`. Its pre-engagement assertions (68, 20.0) passed.
  - `rankingSqlReadsEngagementButNotDismissals` failed on the missing `article_engagement`.
- **Task 2.**
  - RED `f168acc`: 7 refusal tests failed with "to have failed but context started successfully". `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target `capAtOrAboveTheThumbsCapIsRefused()`).
  - GREEN `4ec0607`: all 12 validation tests pass.
  - `driverPassesEveryBlendParameter` and `driverRejectsANonNumericEngagementValue` already passed at RED. They are drift guards over the driver behavior Task 1 committed.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java`: extended `LEARNED_CTE`, `contrib` gains `e.w_thumbs`, `learnedSql` binds the engagement constants, and the javadocs are updated.
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`: `Blend.Engagement`, `implements Validator`, `ENGAGEMENT_INVALID`, `isValid(learnedCap)`.
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml`: the `blend.engagement` block (0.25 / 0.5 / 8).
- `scripts/interest-calibration-replay.sql`: five regenerated blend lines, the extended learned section, and header comments for the new psql variables.
- `scripts/interest-calibration-replay.sh`: `ENGAGEMENT_*` defaults, a validation loop before connecting, and `-v` pass-through.
- `src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java` (new): 12 startup acceptance and refusal tests, with no Docker.
- `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java`: the SC-1 test replaces the Phase 8 unchanged-ranking test.
- `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java`: `rankingSqlReadsEngagementButNotDismissals` replaces the Phase 8 guard.
- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`: `driverPassesEveryBlendParameter` and `driverRejectsANonNumericEngagementValue`.

## Decisions Made

- The research CTE was pasted verbatim and diffed against 09-RESEARCH.md: identical.
- Task 2's RED commit declared the `ENGAGEMENT_INVALID` constant alone, with no validator. That makes RED an assertion failure rather than a compile failure (INVALID_RED).

## Deviations from Plan

### Verify-command portability (no code change)

**1. [Verify note] The Task 1 grep for `-v engagementCap="$ENGAGEMENT_CAP"` returns 1 on macOS.**
- **Found during:** Task 1 verification.
- **Issue:** macOS BSD `grep` (basic regex) treats the mid-pattern `$` in `"$ENGAGEMENT_CAP"` as an anchor, so the pattern cannot match. The other five greps in the chain pass.
- **Fix:** none needed in the code. `grep -qF` (fixed string) confirms the driver contains `-v engagementCap="$ENGAGEMENT_CAP"`, and `driverPassesEveryBlendParameter` asserts it in the test suite. A future plan should use `grep -F` for literal `$` patterns.
- **Files modified:** none.

---

**Total deviations:** 0 code deviations (1 verify-command portability note).
**Impact on plan:** none. The plan was executed as written.

## Issues Encountered

None.

## User Setup Required

None: no external service configuration required.

## Next Phase Readiness

- 09-03 can add the thumbs/engagement split: `contrib` already carries `w_thumbs`, and `eff2` exposes `w_thumbs`, `eng_raw` and `eng`.
- Intermediate state, per the plan: until 09-03 and 09-05 land, the effective weight includes engagement while `TopicWeight.learned` is still thumbs only. There is no release in Phase 9 (D-14).
- Plan 09-06 owns two stale docs: the CLAUDE.md line naming `rankingSqlDoesNotReadTheV7TablesYet` (renamed here) and the `ArticleEngagementStore` javadoc.

---
*Phase: 09-engagement-learning-model*
*Completed: 2026-09-30*

## Self-Check: PASSED

- Files exist: MyfeederPropertiesValidationTest.java, 09-01-SUMMARY.md
- Commits exist: b9546a7, f168acc, 4ec0607
- Full backend suite green (613 tests, 0 failures, 2 pre-existing live-Jev skips)
