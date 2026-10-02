---
phase: 12-calibration-release
plan: 01
subsystem: testing
tags: [calibration, replay, psql, bash, awk, testcontainers, postgres, interest-ranking]

requires:
  - phase: 09-engagement-learning-model
    provides: "LEARNED_CTE with the engaged / eng_learned terms, Engagement.isValid, the replay driver and drift guard"
  - phase: 11-gap-discovery
    provides: "merged into main (D-11); the replay must never read the dismissal table"
provides:
  - "Driver candidate syntax PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP], per-candidate output names, tmp-then-rename output"
  - "engagement_ok range check mirroring Engagement.isValid before any connection (WR-02 closed)"
  - "Replay `engaged` section: badge of every engaged SCORED article, read or unread (D-13 cross-check source)"
  - "InterestCalibrationReplayRunTest: real replay through host psql on Testcontainers, badges equal displayScores"
affects: [12-02, 12-03, 12-calibration-release]

actuals:
  tokens: 12295
  tasks: 2
  commits: 3
plan_head_before: 38c3a8ef60742b4b0a094d0df891e8ab7e06612e
plan_head_after: 77559525a0f725f586472a0be4c4dd2f8130dcb3

tech-stack:
  added: []
  patterns:
    - "Replay blend lines are generated mechanically from an existing verbatim line, never hand-typed"
    - "Driver tests run against a dead port (127.0.0.1:1) with OUT_DIR in a JUnit @TempDir"
    - "End-to-end replay test: @DataJdbcTest with class-level @Transactional(NOT_SUPPORTED) so psql sees committed fixtures"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java
  modified:
    - scripts/interest-calibration-replay.sh
    - scripts/interest-calibration-replay.sql
    - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
    - .planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md
    - .planning/STATE.md

key-decisions:
  - "Candidate syntax extended to six fields rather than looping over env values, so validate-all-before-connect holds across the whole D-06 grid"
  - "Default OUT_DIR moved to $HOME/.cache/myfeeder-phase12/replay; output written to <file>.tmp and renamed only on psql success"
  - "WR-02 range check is an awk mirror of Engagement.isValid, run after the numeric regexes and before the password check"

patterns-established:
  - "Named drift-guard counts: BLEND_STATEMENTS (6) and BADGE_COPIES (5) replace literal 5/4"

requirements-completed: [CAL-02]

coverage:
  - id: D1
    description: "Driver accepts PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] and writes one distinctly named TSV per candidate"
    requirement: CAL-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverAcceptsTheSixFieldForm"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverRejectsAMalformedSixFieldCandidate"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java#engagedBadgesEqualTheAppAtTheDefaultsAndWithEngagementOff"
        status: pass
    human_judgment: false
  - id: D2
    description: "Replay `engaged` section lists every engaged SCORED article with badges equal to InterestScoreQueries.displayScores at cap 8 and cap 0, pinned verbatim by the drift guard"
    requirement: CAL-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java#engagedBadgesEqualTheAppAtTheDefaultsAndWithEngagementOff"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#replaysTheEngagedSectionVerbatim"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driftInAnySingleBlendCopyFails"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java#rankingSqlReadsEngagementButNotDismissals"
        status: pass
    human_judgment: false
  - id: D3
    description: "WR-02 closed: the driver refuses, before any connection, every engagement constant set Engagement.isValid refuses (64-cell grid and every boundary)"
    requirement: CAL-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverRefusesWhatTheAppRefuses"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverRejectsOutOfRangeEngagementBeforeConnecting"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driverValidatesEveryCandidateBeforeAnyConnection"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#aFailedRunLeavesNoFile"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-10-02
status: complete
---

# Phase 12 Plan 01: Replay tooling for the engagement grid Summary

**The calibration replay now takes six-field `PP:HIGH:NEUTRAL:OPEN:SAVE:CAP` candidates with per-candidate output names, adds an `engaged` badge section proven equal to `displayScores` on a real Testcontainers database, and refuses out-of-range engagement constants before connecting (WR-02 closed).**

## Performance

- **Duration:** about 6 min
- **Started:** 2026-10-02T17:52:46Z
- **Completed:** 2026-10-02T17:58:37Z
- **Tasks:** 2
- **Files modified:** 6 (1 created)

## Accomplishments

- Driver: candidate regex extended to the optional `:OPEN:SAVE:CAP` part (empty fields fall back to the `ENGAGEMENT_*` env). Each candidate writes `replay-pp<PP>-hi<HIGH>-ne<NEUTRAL>-op<OPEN>-sv<SAVE>-cap<CAP>.tsv` through `<file>.tmp`, which is renamed only after psql exits 0 and removed otherwise. The default OUT_DIR is now `$HOME/.cache/myfeeder-phase12/replay`.
- Replay SQL: new `engaged` section. Its blend line was generated mechanically from the unread blend line (one `(a."read" = false)` replaced by the engaged scope) and equals `blendCte(ENGAGED_SCOPE)`. The next line is the badge line with `section, article_id, interest_score, raw, is_read, voted`.
- Drift guard: `ENGAGED_SCOPE`, `BLEND_STATEMENTS = 6` and `BADGE_COPIES = 5` replace the literal counts. The labels are now `unread, unread, unread, window, learned, engaged`, the engaged line counts as a badge-checked blend line, and an `engaged` entry is added to the one-byte drift test. `runDriver` is now an instance method, with OUT_DIR in a `@TempDir`.
- New `InterestCalibrationReplayRunTest`: runs the real driver through host psql against the migrated Testcontainers database with two candidates (cap 8 and cap 0). It asserts exactly two TSVs and no `.tmp`, engaged ids {S1..S9, O1, VT} (D1 dormant and N1 unengaged are absent), badges equal to `displayScores` under the same constants, the is_read and voted flags, and S5 higher at cap 8 than at cap 0.
- WR-02: `engagement_ok` (awk) mirrors `MyfeederProperties.Interest.Blend.Engagement.isValid`. It runs for every candidate before the password check and prints `invalid engagement constants: <candidate> (...)`, then exits 2. On the 64-cell grid its verdict matches `isValid(20)` in every cell.

## Task Commits

1. **Task 1 (tracer): 6-field candidates and the engaged section, proven on a real database** - `1e7876d` (feat)
2. **Task 2 RED: failing range-check tests** - `cc824e9` (test)
3. **Task 2 GREEN: engagement_ok range check plus the WR-02 closure in 09-REVIEW-DISPOSITION.md and STATE.md** - `7755952` (feat)

No REFACTOR commit was needed.

## TDD Gate Compliance

- RED `cc824e9`: `driverRejectsOutOfRangeEngagementBeforeConnecting`, `driverRefusesWhatTheAppRefuses` and `driverValidatesEveryCandidateBeforeAnyConnection` failed on their `invalid engagement constants` assertions, because the driver reached psql (`Connection refused` on 127.0.0.1:1). That is the WR-02 reproduction. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK`. `aFailedRunLeavesNoFile` already passed at RED, because Task 1 added the tmp-then-rename output.
- GREEN `7755952`: all 21 InterestCalibrationReplaySqlTest tests pass.

## Files Created/Modified

- `scripts/interest-calibration-replay.sh`: six-field candidates, `engagement_ok`, per-candidate names, tmp-then-rename, phase12 OUT_DIR
- `scripts/interest-calibration-replay.sql`: header entry and the `engaged` section
- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`: named counts, the engaged copy, and the six-field and WR-02 driver tests
- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java`: the end-to-end replay run on Testcontainers through host psql
- `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md`: WR-02 `fixed`, `open: 2`
- `.planning/STATE.md`: the Phase 9 blocker line now reads "WR-02 fixed in Phase 12 (12-01)" (the only STATE.md edit)

## Decisions Made

All three follow the plan's recorded interpretations:
- Six-field candidate syntax instead of an env loop.
- Phase-12 default OUT_DIR, with tmp-then-rename output.
- The awk mirror of `isValid` runs after the numeric regexes.

## Deviations from Plan

**1. [TDD commit split] The Task 2 test changes are in the RED commit, not the GREEN commit**
- **Found during:** Task 2
- **Issue:** The plan's `<verification>` says the Task 2 commit's `--stat` lists the driver, the test, 09-REVIEW-DISPOSITION.md and STATE.md together. The TDD contract needs a separate `test(12-01)` RED commit before the `feat(12-01)` GREEN commit.
- **Resolution:** The tests are in `cc824e9` (RED). The driver change, 09-REVIEW-DISPOSITION.md and STATE.md are in `7755952` (GREEN). This still meets the acceptance criterion that the WR-02 closure ships in the same commit as the driver change.

**Total deviations:** 1 (commit layout only, no behavior change). **Impact:** none on the delivered behavior.

## Issues Encountered

- The first RED evidence record used the wrong field names. The validator expects `exitCode`, `targetTest` and `output` (the surefire XML). After the rebuild it returned `RED_EVIDENCE_OK`.

## Verification

- InterestCalibrationReplaySqlTest: 21 tests, 0 skipped, 0 failures
- InterestCalibrationReplayRunTest: 1 test, 0 skipped, 0 failures (host psql 18.6 was visible to the test JVM)
- V7EngagementMigrationTest: 16 tests, 0 failures
- `git hash-object` of InterestScoreQueries.java: `807baf80e93c36b519a44cfc92eb5d9c052cb305` (unchanged)
- WR-02 reproduction (`ENGAGEMENT_SAVE_WEIGHT=1`, candidate `100:70:40`, dead port): stderr shows `invalid engagement constants`, rc=2, no `psql:`
- No step touched pg.bartram.org (D-01). Driver tests used 127.0.0.1:1; the run test used Testcontainers.
- REQUIREMENTS.md was not marked: CAL-02 is shared with 12-02 and 12-03 (shared-ID gate), so it completes when the last of them finishes.

## User Setup Required

None.

## Next Phase Readiness

- 12-02 and 12-03 can run the D-06 grid in one call:
  `OUT_DIR=... scripts/interest-calibration-replay.sh 100:70:22:0.25:0.5:0 100:70:22:0.25:0.5:8 ...`
- The `engaged` section is the badge source for the D-13 cross-check.

---
*Phase: 12-calibration-release*
*Completed: 2026-10-02*
