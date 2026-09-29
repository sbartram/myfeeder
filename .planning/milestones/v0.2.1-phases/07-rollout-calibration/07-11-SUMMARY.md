---
phase: 07-rollout-calibration
plan: 11
subsystem: testing
tags: [junit, assertj, drift-guard, calibration-replay, sql]

requires:
  - phase: 07-rollout-calibration
    provides: "07-10 InterestCalibrationReplaySqlTest (blend lines checked by equality, badge count of 4) and the shipped scripts/interest-calibration-replay.sql"
provides:
  - "Badge guard pinned per statement: raw INTEREST_SCORE count of 4 with comments included, and the code of each badge line holds INTEREST_SCORE, the interest_score select item and the interest_score name exactly once"
  - "Lenient blend-opening discovery: the code of the file opens a learned CTE exactly 5 times, in any case, whitespace or position, checked before the strict label check"
  - "In-memory mutation proofs: aDriftedBadgeFailsDespiteAVerbatimDecoy (7 cases) and anExtraBlendStatementInAnyFormFails (3 cases)"
  - "07-REVIEW-DISPOSITION.md: WR-04 and IN-07 fixed by 07-11, open 12 -> 10"
affects: [07-verification, gsd-code-review-07]

actuals:
  tokens: 5600    # chars/4 over the two changed files (18997 + 3516 chars)
  tasks: 2
  commits: 3
plan_head_before: ab59a96cbf1b6faed554bdba34cefa1dc4b80f73
plan_head_after: 2eb9c0eafef953dc794d9bc0235e8aefb9a42388

tech-stack:
  added: []
  patterns:
    - "Negative drift cases collected with SoftAssertions.assertSoftly so a RED run names every case that slips past the guard, not just the first"
    - "codeOf(line): per-line code view (block-comment spans removed, -- tail cut) used for both badge-line pins and blend-opening discovery"

key-files:
  created: []
  modified:
    - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
    - .planning/phases/07-rollout-calibration/07-REVIEW-DISPOSITION.md

key-decisions:
  - "The raw INTEREST_SCORE count runs over the whole file with comments included, so any comment copy is a fifth copy and fails; the 07-10 whole-line comment filter was removed"
  - "Each badge line's code must hold the select item ', <INTEREST_SCORE> AS interest_score' once and name interest_score once, beyond the orchestrator's one-copy property, because same-line decoys (another column, a wrapping expression, a second interest_score column) pass the one-copy property alone"
  - "BLEND_START is deliberately unanchored ((?i)\\bwith\\s+learned\\s+as\\b over per-line code), so it also catches a blend opened mid-line inside a subquery, which the review's (?im)^\\s* suggestion would miss"
  - "OPS-02 is not marked Complete in REQUIREMENTS.md: d7e0088 reverted a premature Complete after gaps were found, so the requirement status is left to phase re-verification"

patterns-established:
  - "Drift guard contract: Javadoc lists exactly the checks performed and what is not checked; no claim beyond the assertions"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "A one-token drift of the top badge fails the replay drift guard whatever verbatim copy survives in a trailing -- comment, a block comment, an appended section, another column, a wrapping expression or beside a second interest_score column; an extra verbatim comment copy with no drift also fails"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#aDriftedBadgeFailsDespiteAVerbatimDecoy"
        status: pass
    human_judgment: false
  - id: D2
    description: "An extra drifted blend statement fails whether it is indented, lower-case or opened mid-line (IN-07)"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#anExtraBlendStatementInAnyFormFails"
        status: pass
    human_judgment: false
  - id: D3
    description: "The shipped replay SQL passes the tightened guard, and the ten existing tests plus occurrences, driftOneByte and runDriver are byte-identical to ad1a2c0 and still pass"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#everyCopyInTheReplayIsVerbatim"
        status: pass
      - kind: other
        ref: "awk body diff of the 13 signatures against git show ad1a2c0 (bodies-ok)"
        status: pass
      - kind: other
        ref: "git diff --exit-code aa2069f -- scripts/interest-calibration-replay.sql scripts/interest-calibration-replay.sh src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java"
        status: pass
    human_judgment: false
  - id: D4
    description: "07-REVIEW-DISPOSITION.md records WR-04 and IN-07 as fixed by 07-11 (open 10, total 13); WR-02 stays fixed by 07-10 and the other ten findings stay open"
    verification:
      - kind: other
        ref: "Task 2 ledger verify command (grep rows, frontmatter dispositions and counts)"
        status: pass
    human_judgment: false

duration: 4min
completed: 2026-09-29
status: complete
---

# Phase 7 Plan 11: Pin Badge Copies to Their Statements Summary

**The replay drift guard now counts `INTEREST_SCORE` over the raw file, comments included, and requires the code of each of the four badge lines to hold the verbatim `interest_score` item exactly once. It also finds learned-CTE openings in any case, whitespace or position (exactly 5). A drifted badge or an extra drifted blend can no longer hide behind a verbatim decoy.**

## Performance

- **Duration:** about 4 min
- **Started:** 2026-09-29T03:11:50Z
- **Completed:** 2026-09-29T03:16:15Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Closed WR-04 (gap missing items 1-3). `assertEveryCopyIsVerbatim` now:
  - counts `INTEREST_SCORE` at exactly 4 over the raw file, comments included;
  - requires the code of the line after each unread or window blend line (SQL lines 31, 53, 59 and 66) to hold `INTEREST_SCORE`, `BADGE_ITEM` and the `interest_score` name once each;
  - checks that exactly 4 badge lines were examined.
- Closed IN-07 (gap missing item 4). `BLEND_START` counts learned-CTE openings over the per-line code at exactly 5, before the strict column-0 label check.
- Two new in-memory mutation tests prove 10 decoy or extra-blend forms fail. Six of the decoys keep exactly 4 raw copies, so only the per-statement check can catch them.
- Rewrote the Javadoc to list exactly the checks, what "code" means, and what is not checked. The false 07-10 claim ("cannot hide") is gone.
- Updated the disposition ledger: WR-04 and IN-07 are fixed by 07-11, and open drops from 12 to 10.

## RED / GREEN Evidence

### Task 1 (WR-04): RED against the 07-10 guard (not committed)

Command: `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest'`

The build failed: 11 tests ran and 1 failed, and only `aDriftedBadgeFailsDespiteAVerbatimDecoy` was red. `gsd-tools check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target_test_failed). Soft report from the JUnit XML `<failure>`:

```
AssertJMultipleFailuresError: Multiple Failures (7 failures)
-- failure 1 -- [verbatim kept in a trailing -- comment] Expecting actual not to be null
-- failure 2 -- [verbatim kept in a block comment] Expecting actual not to be null
-- failure 3 -- [verbatim badge used by an appended section] Expecting actual not to be null
-- failure 4 -- [verbatim kept as another column] Expecting actual not to be null
-- failure 5 -- [verbatim wrapped in an expression] Expecting actual not to be null
-- failure 6 -- [a second interest_score column] Expecting actual not to be null
-- failure 7 -- [an extra verbatim copy on a comment line] Expecting actual not to be null
```

All seven cases are named, including the three gap cases. "Expecting actual not to be null" means the old guard threw nothing where an `AssertionError` was expected. The hard preconditions held before the soft block: 4 raw copies for cases 1-6 and 5 for case 7.

### Task 1: GREEN

`BUILD SUCCESSFUL`, and the JUnit XML shows `tests="11" failures="0" errors="0"`. The tracer gate re-ran the verify with `--rerun` after the commit: still 11/11 passing.

### Task 2 (IN-07): RED against the Task 1 guard (not committed)

Command: `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest' --tests 'org.bartram.myfeeder.DevProfileConfigTest'`

The build failed: 16 tests ran and 1 failed, and only `anExtraBlendStatementInAnyFormFails` was red. `check tdd-red-evidence` returned `RED_EVIDENCE_OK`. Soft report:

```
AssertJMultipleFailuresError: Multiple Failures (3 failures)
-- failure 1 -- [indented] Expecting actual not to be null
-- failure 2 -- [lower-case] Expecting actual not to be null
-- failure 3 -- [opened mid-line] Expecting actual not to be null
```

### Task 2: GREEN

`BUILD SUCCESSFUL`. InterestCalibrationReplaySqlTest ran 12 tests and DevProfileConfigTest ran 4, with no failures or errors.

## Task Commits

1. **Task 1 (tracer): pin each badge copy to its statement.** `04aef6c` (test)
2. **Task 2: find extra blend statements in any case, indentation or position.** `615d7a5` (test)
3. **Task 2: record WR-04 and IN-07 as fixed by 07-11.** `2eb9c0e` (docs)

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`:
  - adds the constants `BADGE_ITEM`, `SCORE_NAME`, `COMMENT_SPAN` and `BLEND_START`;
  - adds the helpers `codeOf` and `editTopBadgeLine`, and the two new tests;
  - rewrites `assertEveryCopyIsVerbatim` and its Javadoc;
  - removes the 07-10 `withoutComments` helper.
- `.planning/phases/07-rollout-calibration/07-REVIEW-DISPOSITION.md`: WR-04 and IN-07 are marked fixed by 07-11, and `open: 10`.

## Decisions Made

- The badge-line pins go beyond the orchestrator's one-copy property, as the plan's planner probe requires. Same-line decoys pass a one-copy check alone.
- `BLEND_START` is unanchored so it also catches a blend opened mid-line.
- OPS-02 is not flipped to Complete in REQUIREMENTS.md, because d7e0088 reverted a premature Complete. The requirement status is left to phase re-verification.

## Deviations from Plan

None in the plan's scope. Two process notes:

- The #3968 commit ledger could not be written under `.git/worktrees/...`, because the sandbox rejects writes into the git dir. The plan base `ab59a96` was recorded from the verified spawn base instead, and `commits: 3` was measured with `git log` (`ab59a96..2eb9c0e`).
- The acceptance commands for the body diff and the ledger check were run as saved scripts, not inline compound commands, for the same sandbox reason. Their logic was unchanged.

## Issues Encountered

- The first attempt at the `tdd-red-evidence` record used the wrong field names (snake_case) and then a `class#method` target, so the checker returned INVALID_RED for a malformed record, not for the RED run itself. Rebuilding the record from the Surefire XML with `targetTest` set to the method name returned `RED_EVIDENCE_OK` for both RED runs.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- The 07-VERIFICATION truths 8 and 13 are ready for re-verification. The verifier's three WR-04 probe variants and its IN-07 probe now fail the guard.
- Still open for `/gsd-code-review 07 --fix`: WR-01, WR-03, IN-01..IN-06, IN-08 and IN-09.
- Nothing was merged, pushed, released or deployed.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-29*

## Self-Check: PASSED

- FOUND: `.planning/phases/07-rollout-calibration/07-11-SUMMARY.md`, `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`
- FOUND commits: `04aef6c`, `615d7a5`, `2eb9c0e` (and the SUMMARY commit `7a2c24c`); working tree clean
