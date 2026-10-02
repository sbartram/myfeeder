---
phase: 12-calibration-release
reviewed: 2026-10-02T23:20:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - .gitignore
  - CLAUDE.md
  - docs/superpowers/plans/2026-09-29-metallb-annotation-external-services.md
  - docs/superpowers/plans/2026-09-29-metallb-annotation-external-services.md.tasks.json
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/resources/application.yaml
findings:
  critical: 0
  warning: 3
  info: 8
  total: 11
status: issues_found
---

# Phase 12: Code Review Report

**Reviewed:** 2026-10-02T23:20:00Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

Phase 12 extended the read-only calibration replay. The driver now accepts an optional `:OPEN:SAVE:CAP` per candidate, checks it against the app's `Engagement.isValid`, and writes through a `.tmp` file. The SQL gained the engaged, backfill, dormant, floor and `eng_articles` sections. A Testcontainers end-to-end run and more drift and driver tests were added. Elsewhere the changes are comments, Javadoc and CLAUDE.md text.

What holds up:
- **Password and injection safety.** The password reaches psql only through `PGPASSWORD`, never on argv, and `set -x` is absent. Every value interpolated with `psql -v` is checked by a numeric regex before any connection.
- **Read-only mode.** It is enforced through `PGOPTIONS`.
- **Drift guard.** The blend and badge copies (including the backfill exception, which may differ only in the `engaged` CTE) are guarded byte for byte.
- **Validation parity.** I checked the awk `engagement_ok` predicate by hand against `Engagement.isValid`, including strnum handling of `0.0` and `00` on macOS awk. It agrees.
- **New SQL sections.** The dormant, floor, backfill-pool and `eng_articles` sections match their definitions in CLAUDE.md, and the table keys rule out double counting (`article_score` has PK article_id; `article_topic_score` has PK (article_id, topic_id)).

No blockers. The main problems:
- **WR-01:** the driver tests in `InterestCalibrationReplaySqlTest` inherit the operator's replay env vars. I reproduced this: with `ENGAGEMENT_CAP=0` or `LEARNED_CAP=25` in the shell, cases the test expects to be refused get through.
- **WR-02:** the tier fields are not validated.
- **WR-03:** the "a failed run leaves no file" guarantee does not hold when the run is interrupted.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: The SqlTest driver tests inherit the operator's replay env vars, so the boundary tests become env-dependent

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:685-700`

**Issue:** `runDriver` starts from the full inherited environment. It overrides only `MYFEEDER_PG_PASSWORD`, `PGHOST`, `PGPORT` and `OUT_DIR`. It does not remove `LEARN_RATE`, `LEARNED_CAP`, `WINDOW_DAYS`, `ENGAGEMENT_OPEN_WEIGHT`, `ENGAGEMENT_SAVE_WEIGHT` or `ENGAGEMENT_CAP`. Those are the replay's documented tuning interface (CLAUDE.md, "Engagement tuning"), so an operator is likely to have them exported during a calibration session.

`InterestCalibrationReplayRunTest.runReplay` (lines 411-414) does strip them. The two driver harnesses are therefore inconsistent. I reproduced the failure:
- With `ENGAGEMENT_CAP=0`, the refused case `("100:70:40", ENGAGEMENT_SAVE_WEIGHT=1)` in `driverRejectsOutOfRangeEngagementBeforeConnecting` reaches psql. The refusal assertion fails.
- With `LEARNED_CAP=25`, the grid cell `100:70:40:0.25:0.5:20` is accepted by the driver. `driverRefusesWhatTheAppRefuses` compares against a hard-coded `isValid(20)`, so it fails.

The same inheritance also weakens the Javadoc promise that the driver runs "against a dead local port, so a validation bug could never reach a real database". An inherited `PGHOSTADDR` takes precedence over `PGHOST` in libpq, so the connection would not go to `127.0.0.1:1`. The same applies to `PGSERVICE`/`PGSERVICEFILE`.

**Fix:** Strip the same names as `runReplay`, plus the libpq overrides:
```java
for (String name : List.of("LEARN_RATE", "LEARNED_CAP", "WINDOW_DAYS",
        "ENGAGEMENT_OPEN_WEIGHT", "ENGAGEMENT_SAVE_WEIGHT", "ENGAGEMENT_CAP",
        "PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE", "PGPASSFILE")) {
    environment.remove(name);
}
environment.remove("MYFEEDER_PG_PASSWORD");
```
Consider moving this into one shared helper that both test classes use.

### WR-02: Tier fields are not range- or order-checked, so a swapped candidate silently produces inconsistent histograms

**File:** `scripts/interest-calibration-replay.sh:36-41`, `scripts/interest-calibration-replay.sql:47-49` (and 82-84, 116-118)

**Issue:** `HIGH` and `NEUTRAL` only have to match `[0-9]+`. Neither the order nor the 0-100 badge range is checked. A transposed candidate such as `100:40:70` passes validation, connects to prod and writes a normal-looking `.tsv`:
- `high_pct` counts badges >= 40.
- `neutral_pct` (>= 70 AND < 40) is always 0.
- `low_pct` counts badges < 70.

So high + low exceed 100%, with no error. The D-04 nudge rule compares `high_pct` across candidates, so a typo in one candidate silently corrupts the comparison. The script already promises that every value is validated before connecting (line 35), and this is the one field pair where a wrong value gives believable but wrong output.

**Fix:** After the candidate regex loop:
```bash
IFS=: read -r pp high neutral _ <<<"$candidate"
if (( neutral > high || high > 100 )); then
  echo "invalid tiers: $candidate (0 <= NEUTRAL <= HIGH <= 100)" >&2
  exit 2
fi
```
(Leading-zero inputs such as `08` would need `10#$high` inside `(( ))`.)

### WR-03: The "a failed run leaves no file" guarantee does not cover interruption, and a partial .tmp holds prod article titles

**File:** `scripts/interest-calibration-replay.sh:20, 94-105`

**Issue:** The header says output is "written to <file>.tmp and renamed only after psql succeeds, so a failed run leaves no file". The cleanup runs only when psql returns non-zero and bash gets back control.
- A SIGTERM, SIGHUP (terminal closed) or a Ctrl-C that bash acts on kills the script before line 102.
- `$OUT_DIR/replay-...tsv.tmp` is left behind, holding a partial dump that includes prod article titles from the top and bottom sections.
- The file is mode 600 thanks to `umask 077`, so this is a robustness and data-hygiene gap, not an exposure.
- `aFailedRunLeavesNoFile` only covers the psql-exit path, so the documented guarantee is broader than what the tests prove.

**Fix:**
```bash
tmp=""
trap '[[ -n "$tmp" ]] && rm -f "$tmp"' EXIT
trap 'exit 130' INT TERM HUP
...
  tmp="$out.tmp"
  psql ... -f "$SQL_FILE" > "$tmp"
  mv "$tmp" "$out"; tmp=""
```
Alternatively, narrow the header comment to "a psql failure leaves no file".

## Info

### IN-01: `eng_at_cap` / `at_cap` compare unrounded float8, but the app judges on 6-decimal rounded values

**File:** `scripts/interest-calibration-replay.sql:103` (also 134)

**Issue:** The `learned` and `backfill-learned` sections compute `e.eng_raw >= :engagementCap` and `abs(e.learned_raw) >= :learnedCap` on raw float8. `LearnedLimit.engagementAtCap` applies the same rule to `ROUND(e.eng_raw::numeric, 6)` from `InterestScoreQueries.topicWeights`. An `eng_raw` within 5e-7 below the cap (for example `7.9999999999998` from float hinge arithmetic) is "at cap" in the app but not in the replay. D-04's "no topic pinned at the cap" check reads this column. The expression predates Phase 12, but this phase re-states the line and builds a new rule on top of it.

**Fix:** Compare `round(e.eng_raw::numeric, 6) >= :engagementCap` (and the same for `learned_raw`). These columns sit after the verbatim `LEARNED_CTE` prefix, so the drift guard is unaffected.

### IN-02: The "reached psql" assertions also pass when psql is not installed

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:196, 236, 284, 307`

**Issue:** These assertions check `stderr.contains("psql:")` as proof that validation passed and a connection was attempted. On a host without psql, bash prints `...: line 95: psql: command not found`, which also contains `psql:`. The validation verdict is still proven, but `aFailedRunLeavesNoFile` then exercises "command not found" rather than a refused connection.

**Fix:** Assert on `"psql: error:"`, or call `assumeTrue` on `psql --version` as `InterestCalibrationReplayRunTest.requirePsql` does.

### IN-03: The process timeouts are ineffective because stdout/stderr are drained before `waitFor`

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:697-698`, `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java:423-424`

**Issue:** `readAllBytes()` blocks until the child closes the stream, which normally means it has exited. The following `waitFor(30|60, SECONDS)` therefore never bounds a hang, for example a psql stuck on a connect with no `PGCONNECT_TIMEOUT`. A hang stalls the whole Gradle test task instead of failing the test.

**Fix:** Redirect output to a temp file (`builder.redirectError(file)` / `redirectOutput(file)`), call `waitFor` with the timeout, `destroyForcibly()` on timeout, and then read the file. Also set `PGCONNECT_TIMEOUT=5` in the child env.

### IN-04: The D-13 end-to-end proof is silently skipped on hosts without psql

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java:77-90`

**Issue:** `assumeTrue(available, ...)` turns the whole class into skipped tests when host psql is missing. `./gradlew test` stays green, and the only proof that the replay's badges equal `displayScores` never runs. CLAUDE.md lists this class as a Phase 12 proof without that caveat.

**Fix:** Say in CLAUDE.md ("Proofs" bullet) that the run test needs host psql and skips without it. Alternatively, run psql inside a container (`postgres` image, `psql` against the Testcontainers network alias) so it never skips.

### IN-05: A tautological assertion in the run test

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java:157`

**Issue:** `assertThat(expectedIds).doesNotContain(d1, n1)` checks a set the test built itself (lines 149-151) without `d1` or `n1`, so it can never fail. The real exclusion check is the `badges.keySet().isEqualTo(expectedIds)` inside `assertEngagedSectionMatchesTheApp`.

**Fix:** Delete the line, or assert on the replay output: `assertThat(badgesAtCap8).doesNotContainKeys(d1, n1)`.

### IN-06: A test yaml comment claims the dev overlay carries the calibrated engagement values; it carries none

**File:** `src/test/resources/application.yaml:46`

**Issue:** The comment says "application-dev.yaml carries main's calibrated values (D-14)". `src/test/resources/application-dev.yaml` has no engagement keys (only `tiers.neutral: 22`), because the calibrated values equal the test values. CLAUDE.md states this correctly ("none differs today, so the dev overlay carries no engagement keys"). A reader of the yaml may look for keys that do not exist.

**Fix:** Change the comment to "...; a calibrated value that differs goes in application-dev.yaml (D-14); none differs today."

### IN-07: Output filenames come from the raw candidate text, so equal values can produce different files and duplicates overwrite silently

**File:** `scripts/interest-calibration-replay.sh:93`

**Issue:** `100:70:40:0.25:0.5:8` and `100:70:40:0.250:0.50:8.0` replay the same constants into two differently named files. The same candidate given twice overwrites its own file without notice. This only matters when comparing a grid by filename.

**Fix:** Normalise with `printf '%g'` before building `out`, or refuse duplicate candidates in the validation loop.

### IN-08: The MetalLB plan's backlog step uses `commit -am` in another repo, which sweeps up any unrelated tracked edits

**File:** `docs/superpowers/plans/2026-09-29-metallb-annotation-external-services.md:212`

**Issue:** `git -C .../gitops-deploy commit -am "docs(backlog): ..."` commits every modified tracked file in gitops-deploy. If that repo has unrelated uncommitted work, it lands in a docs-only commit on `main`, which bypasses the feature-branch rule for non-docs changes. (Reviewed proportionately: this file came from the unrelated docs commit d995411. The rest of the plan, the `.tasks.json` and the `.gitignore` entry for `.claude/HANDOFF.md` raised no issues. Note that `helm/myfeeder/values.yaml:14` still carries `metallb.universe.tf/loadBalancerIPs`, which is the pending work the plan describes, not a defect.)

**Fix:**
```bash
git -C /Volumes/data2/scottb/dev/bartram/gitops-deploy commit docs/backlog.md -m "docs(backlog): $SVC migrated to metallb.io annotation"
```

---

_Reviewed: 2026-10-02T23:20:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
