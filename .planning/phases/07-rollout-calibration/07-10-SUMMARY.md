---
phase: 07-rollout-calibration
plan: 10
subsystem: testing
tags: [junit, assertj, drift-guard, calibration-replay, gap-closure]

requires:
  - phase: 07-rollout-calibration
    provides: "07-04 replay SQL (scripts/interest-calibration-replay.sql) and its contains()-based drift guard"
provides:
  - "Every-copy drift guard: each 'WITH learned AS' line must equal its Java text, in the order unread x3, window, learned (exactly 5)"
  - "Exact badge count: InterestScoreQueries.INTEREST_SCORE occurs exactly 4 times outside -- comments"
  - "In-memory proofs: a one-byte drift of any of the 9 copies fails, and 4/6 blend lines or 3/5 badge copies fail"
  - "07-REVIEW WR-02 recorded as fixed by 07-10"
affects: [07-VERIFICATION re-verification (truth 8), future calibration tuning via the replay]

actuals:
  tokens: 2487
  tasks: 2
  commits: 3
plan_head_before: 7d5ce3a449b2bf8b95f4b892b9149fd48db1fde3
plan_head_after: 1531facc6cb294d050d067d6fc4bba91ba91050e

tech-stack:
  added: []
  patterns:
    - "Drift guards assert every copy (label per line in file order + exact occurrence count), never contains()"
    - "Mutation proofs build drifted variants as in-memory strings after first asserting the unmodified text passes"

key-files:
  created: []
  modified:
    - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
    - .planning/phases/07-rollout-calibration/07-REVIEW-DISPOSITION.md

key-decisions:
  - "Negative cases use assertThatCode(...).as(label).isInstanceOf(AssertionError.class) instead of assertThatThrownBy(...).as(label): assertThatThrownBy fails before .as() applies, so its failure would not name the copy that slipped through"

patterns-established:
  - "Every-copy verbatim guard: label each guarded line by equality with the Java text, assert the exact label sequence, count inline copies outside comments"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "Every 'WITH learned AS' line of the replay SQL must be one of the allowed verbatim Java texts, in order, exactly 5 lines (gap missing item 1)"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#everyCopyInTheReplayIsVerbatim"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driftInAnySingleBlendCopyFails"
        status: pass
    human_judgment: false
  - id: D2
    description: "INTEREST_SCORE must occur exactly 4 times outside comments; a one-byte drift of any badge copy fails (gap missing item 2)"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#driftInAnySingleBadgeCopyFails"
        status: pass
    human_judgment: false
  - id: D3
    description: "One step either side of the shipped counts fails: 4/6 blend lines, 3/5 badge copies"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java#aMissingOrExtraCopyFails"
        status: pass
    human_judgment: false
  - id: D4
    description: "Six existing replay tests unchanged and passing; DevProfileConfigTest finds no activation token; replay SQL and InterestScoreQueries.java byte-identical to aa2069f"
    verification:
      - kind: unit
        ref: "./gradlew test --tests InterestCalibrationReplaySqlTest --tests DevProfileConfigTest (10 + 4 tests, 0 failures)"
        status: pass
      - kind: other
        ref: "git diff --exit-code aa2069f -- scripts/interest-calibration-replay.sql src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java"
        status: pass
    human_judgment: false
  - id: D5
    description: "07-REVIEW-DISPOSITION.md: WR-02 fixed (07-10), open: 8, the other eight findings still open"
    verification:
      - kind: other
        ref: "grep -qF '| WR-02 | warning | fixed | 07-10 |' && grep -qx 'open: 8' (plus WR-01/WR-03 open) on 07-REVIEW-DISPOSITION.md"
        status: pass
    human_judgment: false

duration: 4min
completed: 2026-09-29
status: complete
---

# Phase 7 Plan 10: Every-copy replay drift guard Summary

**InterestCalibrationReplaySqlTest now fails when any one of the 9 guarded copies in the replay SQL drifts: it labels all 5 `WITH learned AS` lines by exact equality (unread x3, window, learned) and requires exactly 4 badge copies outside comments. In-memory one-byte and missing/extra-copy tests prove each check fails when it should.**

## Performance

- **Duration:** about 4 min
- **Started:** 2026-09-29T02:18:38Z
- **Completed:** 2026-09-29T02:22:01Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Closed 07-VERIFICATION gap missing item 1. `assertEveryCopyIsVerbatim` maps every raw line that starts with `WITH learned AS` to `unread`, `window`, `learned` or `drifted`, and asserts `containsExactly("unread", "unread", "unread", "window", "learned")`. The line-4 `--` header comment is never counted.
- Closed gap missing item 2. `occurrences(withoutComments(sql), InterestScoreQueries.INTEREST_SCORE)` must be exactly 4.
- Proved the guard fails on drift in memory. A one-byte drift of each of the 9 copies fails (3 unread, 1 window, 1 learned, 4 badge). Removing the bottom blend line or adding a sixth fails, and so do 3 or 5 badge copies. Each proof first shows that the unmodified text passes, so no negative case can pass vacuously.
- Recorded WR-02 as fixed by 07-10 (open 9 -> 8).
- Changed tests only. The replay SQL and `InterestScoreQueries.java` are byte-identical to aa2069f, and no test writes a file.

## Task Commits

1. **Task 1 (tracer): guard every WITH learned AS line** - `08185bf` (test)
2. **Task 2: exact badge count + missing/extra copy proofs** - `7ede686` (test)
3. **Task 2: WR-02 disposition** - `1531fac` (docs)

## RED / GREEN Evidence

**Task 1 RED** (helper temporarily kept the old `contains()` semantics of the three existing verbatim tests; not committed):
```
InterestCalibrationReplaySqlTest > driftInAnySingleBlendCopyFails() FAILED
8 tests completed, 1 failed
java.lang.AssertionError: [unread copy 0]
Expecting actual not to be null
```
The drifted summary copy went undetected: `contains()` still found the top and bottom copies. This is the gap the verifier reported.

**Task 1 GREEN** (label check in place): `BUILD SUCCESSFUL`, 8 tests, 0 failures. All eight names were in the JUnit XML, and `git diff --exit-code aa2069f` on the SQL and the Java was clean.

**Task 2 RED** (both new tests added; helper still checked only the blend lines; not committed):
```
InterestCalibrationReplaySqlTest > driftInAnySingleBadgeCopyFails() FAILED
InterestCalibrationReplaySqlTest > aMissingOrExtraCopyFails() FAILED
10 tests completed, 2 failed
java.lang.AssertionError: [badge copy 0] Expecting actual not to be null
java.lang.AssertionError: [last badge copy replaced] Expecting actual not to be null
```
Variants (a) "bottom blend line removed" and (b) "extra unread blend line" threw before the loop reached (c), which confirms the blend-line boundaries were already guarded. `gsd-tools check tdd-red-evidence` on the JUnit XML with target `driftInAnySingleBadgeCopyFails()` returned `RED_EVIDENCE_OK` (`target_test_failed`).

**Task 2 GREEN** (count added): `./gradlew test -x npmBuild -x npmInstall --tests InterestCalibrationReplaySqlTest --tests DevProfileConfigTest` gave `BUILD SUCCESSFUL`. InterestCalibrationReplaySqlTest ran 10 tests with 0 failures and 0 errors, and DevProfileConfigTest ran 4 tests with 0 failures. All ten names were in the XML.

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`: adds `LEARNED_SECTION`, the helpers `assertEveryCopyIsVerbatim`, `occurrences`, `driftOneByte` and `withoutComments`, and 4 new tests. The six existing tests are unchanged, and the Javadoc gains one sentence.
- `.planning/phases/07-rollout-calibration/07-REVIEW-DISPOSITION.md`: WR-02 changed to `fixed` / `07-10`, and `open: 8`.

## Decisions Made

- The negative cases use `assertThatCode(() -> ...).as(label).isInstanceOf(AssertionError.class)` rather than `assertThatThrownBy(() -> ...).as(label)`. `assertThatThrownBy` fails with "Expecting code to raise a throwable" before `.as()` applies, so the failure could not say which copy slipped through. The first RED run showed exactly that. With `assertThatCode`, the failure names the case (`[unread copy 0]`), and the assertion strength is the same.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Negative-case assertions could not name the failing copy**
- **Found during:** Task 1 (first RED run)
- **Issue:** The planned `assertThatThrownBy(...).as(label + " copy " + i)` produced the unlabelled message "Expecting code to raise a throwable". The description is attached only after the throwable check, so the RED run could not show which case failed.
- **Fix:** Used `assertThatCode(...).as(label).isInstanceOf(AssertionError.class)` for every negative case and removed the unused `assertThatThrownBy` import. It fails when nothing is thrown or when a non-AssertionError is thrown, as before.
- **Files modified:** InterestCalibrationReplaySqlTest.java
- **Verification:** The re-run RED showed `[unread copy 0]`, and GREEN passes.
- **Committed in:** 08185bf

**2. [Worktree] Commits are on the per-agent worktree branch, not sbartram/main**
- The plan says to commit on `sbartram/main`. This executor ran in an isolated worktree (`worktree-agent-ac33e3c901443073a`, based on 7d5ce3a), and the orchestrator merges it. Nothing was merged, pushed, tagged, built or deployed.

---

**Total deviations:** 1 auto-fixed (Rule 1), plus the worktree branch note.
**Impact on plan:** Neither weakens the guard. The fix only makes failure messages name the copy.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 07-VERIFICATION truth 8 (the 07-04 drift-guard must-have) is ready for re-verification (partial -> verified).
- WR-01, WR-03 and IN-01..IN-06 stay open for `/gsd-code-review 07 --fix`.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-29*
