---
phase: 07-rollout-calibration
verified: 2026-09-29T03:30:31Z
status: human_needed
score: 29/29 must-haves verified
covered_files:
  - .planning/phases/07-rollout-calibration/07-01-PLAN.md
  - .planning/phases/07-rollout-calibration/07-01-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-02-PLAN.md
  - .planning/phases/07-rollout-calibration/07-02-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-03-PLAN.md
  - .planning/phases/07-rollout-calibration/07-03-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-04-PLAN.md
  - .planning/phases/07-rollout-calibration/07-04-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-05-PLAN.md
  - .planning/phases/07-rollout-calibration/07-05-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-06-PLAN.md
  - .planning/phases/07-rollout-calibration/07-06-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-07-PLAN.md
  - .planning/phases/07-rollout-calibration/07-07-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-08-PLAN.md
  - .planning/phases/07-rollout-calibration/07-08-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-09-PLAN.md
  - .planning/phases/07-rollout-calibration/07-09-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-10-PLAN.md
  - .planning/phases/07-rollout-calibration/07-10-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-11-PLAN.md
  - .planning/phases/07-rollout-calibration/07-11-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-BACKFILL.md
  - .planning/phases/07-rollout-calibration/07-CALIBRATION.md
  - CLAUDE.md
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestBadge.test.tsx
  - src/main/frontend/src/components/InterestBadge.tsx
  - src/main/frontend/src/hooks/useInterest.test.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/utils/interest.test.ts
  - src/main/frontend/src/utils/interest.ts
  - src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/TierThresholds.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
  - src/test/resources/application-dev.yaml
  - src/test/resources/application.yaml
covered_digest: "v2:sha256:040789800182cf54bc389cad68ef1adf4d6fedbd4d2e6345aefb0b8349e1af4f"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 16/18
  gaps_closed:
    - "07-04: InterestCalibrationReplaySqlTest fails on any drift of a blend or badge copy; 07-10: a copy pasted into a -- comment cannot hide a drifted statement copy (truths 8 and 13, 07-REVIEW WR-04). A drifted badge now fails whether the verbatim text is kept in a trailing -- comment, a /* */ comment, an appended section, another column, a wrapping expression or beside a second interest_score column"
    - "Recommended item 4 (07-REVIEW IN-07): an extra drifted blend statement that is indented, lower-case or opened mid-line now fails"
  gaps_remaining: []
  regressions: []
coincidental_reliance_items:
  - truth: "07-04: InterestCalibrationReplaySqlTest fails on any drift of a blend or badge copy in scripts/interest-calibration-replay.sql (truth 8)"
    reason: undeclared-precondition
    harden: "The guard finds statements by the canonical text 'WITH learned AS' and treats a block comment that spans lines as code. It holds because the replay uses that spelling and has no /* anywhere, which nothing enforces. Enforce it: fail on any /* in the replay and widen BLEND_START to every spelling of 'learned AS (' (RECURSIVE, quoted name, column list, non-first CTE), per 07-REVIEW WR-05; blank string literals in codeOf (IN-10)"
human_verification:
  - test: "Open http://192.168.44.204/priority, then Settings > Interests"
    expected: "Priority lists scored articles with badges; the Interests dialog shows no 'not configured' notice; reader, feed tree and article list behave as before"
    why_human: "Visual confirmation of the prod UI; API checks cannot show rendering (07-06 deferred human-check, carried forward; prod was out of scope for this re-verification)"
  - test: "In the prod Priority and article lists, look at badges scored 22-39 and 70+"
    expected: "22-39 render in the neutral colour, 70+ in the high colour, below 22 low (served tiers 70/22). A brief low-colour flash for 22-39 on first paint is the known 70/40 fallback (07-REVIEW IN-01)"
    why_human: "Tier colours are CSS rendering"
  - test: "Confirm the judgment-tier prohibitions of 07-06..07-11 (approve-before-release gates, no prod writes by hand, no force-push/no-verify/disableChecks, no rubric text or key in committed files, no SDK retry / concurrency > 1; 07-10 and 07-11 test-only, no other findings fixed, no push or merge to main)"
    expected: "Each holds; the verifier's non-authoritative LLM judgment found supporting evidence for all (see Prohibitions)"
    why_human: "Judgment-tier prohibitions require explicit human resolution (ADR-550 D4)"
---

# Phase 7: Rollout & Calibration Verification Report

**Phase Goal:** Interest ranking is live in production with a real key, the backlog is scored, and the scores are tuned so they mean something
**Verified:** 2026-09-29T03:30:31Z
**Status:** human_needed (all 29 must-haves verified; the only gap from the previous run is closed; the remaining items are the carried-forward visual checks and the judgment-tier prohibitions)
**Re-verification:** Yes, after gap-closure plan 07-11 (commits 04aef6c, 615d7a5, 2eb9c0e; merged in 0e85874)

## Re-verification Scope

- **Carried-forward gap (truths 8 and 13, WR-04):** fully re-checked. I re-ran the test class, read the guard line by line, and ran a probe that calls the **real compiled** `assertEveryCopyIsVerbatim` by reflection (not a hand-written copy of it) against the real SQL.
- **07-11 must-haves (truths 19-29):** new in this run. I checked the RED claims by compiling the guard as it was at `ad1a2c0` (07-10) and at `04aef6c` (07-11 Task 1) into scratch directories and running the same probe against each.
- **Truths 1-7, 9-12 and 14-18:** regression check only. `git diff --stat ad1a2c0 HEAD -- . ':!.planning'` lists only `InterestCalibrationReplaySqlTest.java`. The replay SQL, the driver and `InterestScoreQueries.java` are byte-identical to `aa2069f`. No production, frontend, yaml, Helm or CLAUDE.md file changed. Nothing was built, pushed or deployed: `main` = `origin/main` = `5461d0a`, and no remote branch contains `04aef6c`. So the live facts behind truths 1, 2, 9 and 11 are carried forward from the initial run. I did not contact prod and did not use `MYFEEDER_PG_PASSWORD`.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | SC1: released and deployed with a live key; backfill drained with no 429 storms and without opening the circuit | ✓ VERIFIED (regression: no change) | Initial-run evidence stands: 0.2.1 live, 199 SCORED / 0 FAILED, 53/53 CLOSED samples, 0 `Jev ` retry lines. No release, image or deploy since then |
| 2 | SC2: blend constants configurable and tuned against the real distribution; badges spread across tiers | ✓ VERIFIED (regression: no change) | yaml, properties, queries and status service unchanged; the 07-CALIBRATION.md approval is unchanged |
| 3 | SC3: CLAUDE.md documents the Jev behaviors and gotchas | ✓ VERIFIED (regression: no change) | Committed CLAUDE.md unchanged |
| 4 | 07-01: tiers bind from yaml, `tiers` appended last, retune by config only | ✓ VERIFIED (regression) | Source unchanged |
| 5 | 07-02: InterestBadge uses the served tiers through TierContext, with a 70/40 fallback | ✓ VERIFIED (regression) | Frontend unchanged |
| 6 | 07-03: JevEventLogging lines carry class names and numbers only | ✓ VERIFIED (regression) | Source unchanged |
| 7 | 07-04: read-only replay driver reproduces the blend verbatim, validates its inputs and requires the password | ✓ VERIFIED | SQL and driver byte-identical; `replayIsReadOnly`, `driverRejectsANonNumericCandidate` and `driverRequiresThePassword` pass in my re-run |
| 8 | 07-04: `InterestCalibrationReplaySqlTest` fails on any drift (a single-byte difference) of a blend or badge copy | ✓ VERIFIED (coincidental-reliance) | **Previously FAILED, now closed.** A single-byte drift of any of the 9 copies fails. Every ordinary edit I tried fails: editing in place, keeping the old text on a `--` line, keeping it in a multi-line `/* */` block before a normal rewrite, all three WR-04 decoys, and an extra drifted statement. The residual bypasses (WR-05, IN-10) need a deliberately non-canonical spelling. See "Judgment on WR-05" |
| 9 | 07-06: 0.2.0 released (tag, image, dump, Flyway V6, key, soak) | ✓ VERIFIED (regression) | Tags and 07-BACKFILL.md unchanged |
| 10 | 07-08: calibration after the drain, with top/bottom 20 and explicit approval | ✓ VERIFIED (regression) | 07-CALIBRATION.md unchanged |
| 11 | 07-09: approved constants shipped as 0.2.1 through source control; test yaml at 100/70/40; no Helm or env override | ✓ VERIFIED (regression) | yaml and Helm unchanged |
| 12 | 07-10: every `WITH learned AS` line equals its Java text, in the order unread x3, window, learned (exactly 5) | ✓ VERIFIED | Label check unchanged at `InterestCalibrationReplaySqlTest.java:269-278`; the probe control "top blend edited in place" fails at the label check |
| 13 | 07-10: the test fails unless INTEREST_SCORE occurs exactly 4 times in the non-comment text, and a copy in a `--` comment cannot hide a drifted statement copy | ✓ VERIFIED | **Previously FAILED, now closed.** The raw count is 4 (`:279-281`) and the code of each of the 4 badge lines must hold exactly one copy (`:283-302`), so the non-comment count is exactly 4. Probe: a badge drift with a trailing `--` decoy fails ("INTEREST_SCORE copies in the code of the line after blend line 52"), and a blend kept on a `--` line with a drifted rewrite fails at the label check |
| 14 | 07-10: a one-byte drift at the middle of any of the nine copies fails, after a passing baseline | ✓ VERIFIED | `driftInAnySingleBlendCopyFails` and `driftInAnySingleBadgeCopyFails` pass (unchanged since `ad1a2c0`) |
| 15 | 07-10: 4 or 6 blend lines and 3 or 5 badge copies fail; 5 and 4 pass | ✓ VERIFIED | `aMissingOrExtraCopyFails` passes (unchanged) |
| 16 | 07-10: the six original tests are unchanged and pass | ✓ VERIFIED | The diff against `ad1a2c0` touches none of them; 12/12 pass |
| 17 | 07-10: test-only; mutations in memory only | ✓ VERIFIED | 0-line diff for SQL, driver and `InterestScoreQueries`; no `Files.write` in the test |
| 18 | 07-10: disposition records WR-02 fixed by 07-10 | ✓ VERIFIED | `\| WR-02 \| warning \| fixed \| 07-10 \|` |
| 19 | 07-11: the top badge drifted to `ROUND(b.raw_n, 1)` fails with the verbatim text kept in (a) a trailing `--`, (b) a `/* */`, (c) an appended section, and each variant is asserted to keep 4 raw copies | ✓ VERIFIED | `aDriftedBadgeFailsDespiteAVerbatimDecoy:188-218` asserts 4 raw copies and then an AssertionError for each case. My probe with the real guard shows the same: all three fail at the badge-line check for line 52 |
| 20 | 07-11 binding property: the code of each badge line holds exactly one INTEREST_SCORE, and the whole file holds exactly 4 | ✓ VERIFIED | `:279-302`; `codeOf` at `:311-315` removes `/* */` spans (including an unclosed `/*`) and cuts the `--` tail |
| 21 | 07-11: each badge line's code holds `BADGE_ITEM` once and names `interest_score` once, so the same-line decoys fail | ✓ VERIFIED | `:294-299`; test cases 4-6 pass; the probe case "verbatim as another column" fails on the `BADGE_ITEM` count |
| 22 | 07-11 IN-07: the code opens a learned CTE (`with learned as`, any case or whitespace, anywhere in a line) exactly 5 times, checked before the label check | ✓ VERIFIED | `BLEND_START` at `:54`, asserted at `:264-267` before the labels at `:269`. Probe: the indented, lower-case and mid-line forms each fail on the blend-start count |
| 23 | 07-11: RED before GREEN for both tasks | ✓ VERIFIED | The SUMMARY records both RED reports (7 and 3 named cases). I reproduced the RED behavior myself: at `ad1a2c0` the 3 WR-04 decoys and the same-line decoy **pass** the guard; at `04aef6c` they fail, but the 3 IN-07 forms still **pass**; at HEAD all of them fail |
| 24 | 07-11: the Javadoc lists the checks, defines "code", states what is not checked, drops the 07-10 "cannot hide" sentence and claims nothing beyond the checks | ✓ VERIFIED | `:241-260` and `:305-310`. Item 1 defines "opens a learned CTE" by the pattern in its parenthesis. Under that definition its inference ("no statement ... other than the five verbatim lines") follows from checks 1 and 2: each of the 5 labelled lines matches exactly once, so a count of 5 leaves no other match. The codeOf Javadoc discloses that a block comment spanning lines counts as code. Wording hazard: read with Postgres semantics, item 1 overstates. See WR-05 below |
| 25 | 07-11 boundary: the raw count of 4 is exact both ways; an extra verbatim `--` copy with no drift fails; the 07-10 count cases still fail | ✓ VERIFIED | Case 7 (`:204-205`, asserted at 5 raw copies); `aMissingOrExtraCopyFails` passes |
| 26 | 07-11 precision: a one-token badge drift fails whatever verbatim copy survives elsewhere, and a one-byte drift of any of the nine copies still fails | ✓ VERIFIED | The 7 decoy cases pass. Probe: the one-token drift with the alias kept and the verbatim item or bare expression inside a **string literal** on the same line also fails (on the name count and the item count). IN-10 escapes only when the real column is also renamed, which is not a one-token drift |
| 27 | 07-11: the ten existing tests and `occurrences`, `driftOneByte` and `runDriver` are byte-identical to `ad1a2c0` | ✓ VERIFIED | `git diff ad1a2c0 HEAD` on the test adds imports, constants, two tests and two helpers, rewrites `assertEveryCopyIsVerbatim` and its Javadoc, and removes `withoutComments`. No existing test or helper body changed |
| 28 | 07-11: test-only; SQL, driver and `InterestScoreQueries` byte-identical to `aa2069f`; variants in memory; no DB or network | ✓ VERIFIED | `git diff --exit-code aa2069f -- <3 files>` is empty; every variant is a `String`; `runDriver` is unchanged (dead port 127.0.0.1:1) |
| 29 | 07-11: the disposition records WR-04 and IN-07 fixed by 07-11 (open 12 to 10); the rest stay open; WR-02 stays fixed | ✓ VERIFIED | `git show 2eb9c0e:` gives `open: 10` and `total: 13` with both rows `fixed \| 07-11`. The later review commit `adb8088` added WR-05 and IN-10 as open, so the file now reads `open: 12` and `total: 15`, which is consistent |

**Score:** 29/29 truths verified (0 present-but-behavior-unverified; truth 8 carries a coincidental-reliance advisory)

### Judgment on WR-05 (and IN-10): does the drift-guard truth still fail?

**My judgment:** no. The truth "the calibration replay is drift-guarded against the app's blend and badge SQL" (truth 8, plus truth 13) is now met. WR-05 and IN-10 are evidenced but non-blocking warnings. They sit outside the verified truths, and I recorded them as a coincidental-reliance advisory on truth 8. I used the same test as last round, so the two verdicts can be compared.

**What I reproduced.** I called the real compiled guard by reflection against the real SQL. These all **pass** the guard:
- an extra drifted statement opened as `WITH RECURSIVE learned AS`, `WITH "learned" AS`, `WITH learned (c) AS` or `WITH x AS (SELECT 1), learned AS`;
- the `top` statement replaced as the review describes: a `/*` line, the verbatim blend line, then one line holding `*/ WITH RECURSIVE learned AS <drifted blend> SELECT 'top' ... <INTEREST_SCORE> AS interest_score ...`, and the same with `"learned"`;
- IN-10: the top badge drifted and its column renamed `AS score`, with the verbatim item kept in a string literal on the same line.

So WR-05 and IN-10 are real. Deterministic evidence exists, and the evidence gate does not demote them to advisory.

**Why this does not fail the truth, while WR-04 did.** Last round I treated WR-04 as blocking for three reasons:
- its trigger was an ordinary editing habit, keeping the old expression in a trailing `-- was ...` comment;
- it falsified the gap-closure plan's own stated truth;
- the prior gap had prescribed a check that would have caught it.

None of these holds for WR-05:

1. **An ordinary edit does not reach it.** Every normal way to change or re-sync the replay fails the guard. I checked each case with the real guard:
   - editing the top blend in place fails at the label check;
   - keeping the old blend on a `--` line above a new canonical line fails at the label check;
   - keeping the old blend in a multi-line `/* */` block above a new canonical line fails at the blend-start count;
   - keeping the old blend in a `/* */` block above a reformatted column-list rewrite on its own line fails at the badge-line check;
   - re-pasting a changed Java constant fails by equality;
   - the three WR-04 decoys fail at the badge-line check.

   Every WR-05 bypass needs a CTE opener that the workflow never produces: the replay is a byte copy of Java text that starts with `WITH learned AS`, and RECURSIVE, a quoted name, a column list or a non-first position serve no purpose in this file. The replacement form also needs the comment terminator, the respelled blend and the badge select joined on one 1.6k-character line, directly after the commented verbatim line. That is deliberate construction to defeat the guard, not drift.
2. **No 07-11 truth is falsified.**
   - Truth 22 defines exactly the forms it covers ("in any case, with any whitespace, anywhere in a line").
   - Truth 24's Javadoc defines its terms, and its inference holds under them.
   - Truth 26 (precision) holds: a one-token drift with a literal decoy still fails. IN-10 also needs the column renamed.
   - Truth 13's clause is about `--` comments, and a `--` copy cannot hide a drift.
3. **07-11 delivered more than the gap prescribed.** The prescribed IN-07 pattern (`(?im)^\s*with\s+learned\s+as\b`) would also miss every WR-05 spelling, and the prescription never asked for multi-line block-comment handling. WR-05 is therefore new scope, not an unmet part of the carried-forward gap.
4. **No text guard can meet the adversarial reading.** Read as "no maintainer can defeat it", the truth cannot be met by any check short of parsing the SQL. Even the WR-05 fix leaves the psql meta-command path of IN-08. The truth's stated purpose ("so identical constants give identical scores to the running app") is to keep the replay from drifting away from the app by accident. That purpose is met: any app-side change, and every accidental or ordinary replay edit, turns the test red.
5. **The status would not change.** The phase goal is live and the shipped replay text is byte-identical to the text that produced the 07-08 evidence. The remaining items would also route the phase to `human_needed`.

**What argues the other way, for your decision.** Read literally, 07-04 says "fails on any drift", and the WR-05 replacement is a drift of the `top` copy that stays green. The same class of hole (a verbatim decoy in a comment hiding a drifted statement) keeps reappearing one level deeper. CLAUDE.md calls the replay "drift-guarded". If you want that phrase to mean "cannot be defeated without the test noticing", reopen it. The fix is small and mechanical:
- fail on any `/*` in the replay (the shipped file has none);
- widen `BLEND_START` to `learned AS (` in every spelling;
- blank string literals in `codeOf`;
- add the probe cases.

I recommend doing it through `/gsd-code-review 07 --fix` (WR-05 and IN-10 are open in the disposition), not another gap cycle.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `InterestCalibrationReplaySqlTest.java` | badge copies pinned per statement (`BADGE_ITEM`), lenient blend discovery, decoy and extra-blend proofs | ✓ VERIFIED | Exists, substantive (380 lines, 12 tests), wired to `InterestScoreQueries` constants and the replay SQL; 12/12 pass |
| `07-REVIEW-DISPOSITION.md` | `\| WR-04 \| warning \| fixed \| 07-11 \|` | ✓ VERIFIED | Present, with IN-07 also fixed by 07-11 |
| `scripts/interest-calibration-replay.{sh,sql}` | unchanged, read-only | ✓ VERIFIED | 0-line diff against `aa2069f`; no `/*` in the SQL |
| All other phase artifacts | as in the initial report | ✓ VERIFIED | unchanged |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| drift test | InterestScoreQueries | `occurrences(sql, InterestScoreQueries.INTEREST_SCORE)` (`:279`), `BADGE_ITEM` built from `INTEREST_SCORE` (`:46`), `blendCte(UNREAD_SCOPE)`/`blendCte(WINDOW_SCOPE)` by equality | WIRED |
| drift test | replay SQL | `Files.readString(SQL)`; `codeOf(lines.get(i + 1))` for each badge line; `BLEND_START` over the per-line code | WIRED |
| All other links | | | unchanged from the initial report (WIRED) |

### Data-Flow Trace (Level 4)

Unchanged from the initial report. Tier thresholds and scores flow from yaml and the SQL blend to the badge (✓ FLOWING). No data-path file changed.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Drift guard class | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest" --rerun` | XML: tests=12 failures=0 errors=0, timestamp 2026-09-29T03:25:42Z | ✓ PASS |
| Full backend suite | caller-provided post-merge run | 538 tests, 0 failures, 2 skipped | ✓ PASS (not re-run) |
| WR-04 decoys and ordinary edits caught | reflection probe calling the compiled `assertEveryCopyIsVerbatim` at HEAD (scratchpad `Probe.java`; SQL read-only) | shipped file passes; 7 controls, 3 IN-07 forms, same-line decoy, 2 literal decoys with the alias kept, and the column-list rewrite all fail | ✓ PASS |
| RED against older guards | same probe with the test class compiled from `ad1a2c0` and from `04aef6c` | `ad1a2c0`: WR-04 decoys, IN-07 forms and the same-line decoy all pass. `04aef6c`: WR-04 decoys and same-line decoy fail; IN-07 forms pass | ✓ PASS (RED confirmed) |
| WR-05 / IN-10 residuals | same probe at HEAD | 4 extra-statement spellings, 2 commented-verbatim replacements of `top`, and the renamed literal decoy all pass the guard | ⚠️ reproduced (non-blocking; see judgment) |
| Production files untouched | `git diff --exit-code aa2069f -- scripts/interest-calibration-replay.sql scripts/interest-calibration-replay.sh .../InterestScoreQueries.java` | empty | ✓ PASS |
| Not pushed or merged to main | `git rev-parse main origin/main`; `git branch -r --contains 04aef6c` | both `5461d0a`; no remote branch | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED. No `scripts/*/tests/probe-*.sh` exist, and no plan declares one. The reflection probe above is a verifier spot-check, not a project probe.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| OPS-01 | 07-03, 07-06, 07-07 | Released and deployed with a live key; backfill runs with no 429 storms or open circuit | ✓ SATISFIED | Truths 1, 6, 9 |
| OPS-02 | 07-01, 07-02, 07-04, 07-08, 07-09, 07-10, 07-11 | Blend constants configurable and tuned against the real score distribution | ✓ SATISFIED | Truths 2, 4, 5, 7, 8, 10-29. The replay drift guard, the last open item, is closed |
| OPS-03 | 07-05 | CLAUDE.md documents the Jev behaviors and gotchas | ✓ SATISFIED | Truth 3 |

No orphaned requirements: REQUIREMENTS.md maps exactly OPS-01..03 to Phase 7, and plans claim all three. The REQUIREMENTS.md checkboxes (lines 67-69) and traceability rows (148-150) still read "Gaps Found" and unchecked. `d7e0088` held them back pending this re-verification, and the orchestrator owns flipping them. I did not edit them.

### Prohibitions (judgment tier, non-authoritative LLM verdict, flagged for human confirmation)

| Plan | Prohibition | LLM verdict | Evidence |
|---|---|---|---|
| 07-06/07-09 | no release action before `approve` | holds | as in the initial report |
| 07-06/07-07/07-08 | no prod writes by hand | holds | as in the initial report |
| 07-06/07-09 | no force-push, --no-verify, disableChecks or moved tag | holds | as in the initial report |
| 07-06/07-07/07-08 | no key or rubric text in committed files | holds | the 07-11 commits touch only the test and the disposition |
| 07-07 | no SDK retry; concurrency stays 1 | holds | yaml unchanged |
| 07-10/07-11 | must not edit the replay SQL, the driver, InterestScoreQueries or any production file | holds | 0-line diff against `aa2069f` |
| 07-10/07-11 | must not weaken, rename or delete existing tests or mutation cases; no DB or network | holds | no existing test or helper body changed; `runDriver` unchanged |
| 07-11 | must not fix other open findings (IN-08, IN-09, WR-01, WR-03, IN-01..IN-06) | holds | `replayIsReadOnly` and `runDriver` unchanged; no production file changed |
| 07-10/07-11 | no merge to main, push, tag, image, deploy or prod access | holds | `main` = `origin/main` = `5461d0a`; the 07-11 commits are on `sbartram/main` only |

### Advisory (New Scope, Unevidenced)

None. WR-05 and IN-10 are new scope but have deterministic evidence (the reflection probe), so they are not unevidenced advisories. They are non-blocking because they fail no must-have truth and do not prevent the phase goal (see the judgment above). They are recorded as Warnings below and in `coincidental_reliance_items`.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `InterestCalibrationReplaySqlTest.java` | 54, 264-267, 311-315 | `BLEND_START` misses `RECURSIVE`, quoted, column-list and non-first spellings; a block comment spanning lines counts as code (WR-05) | ⚠️ Warning | a deliberately respelled drifted statement, or a commented-verbatim replacement of one, passes; ordinary edits are caught |
| `InterestCalibrationReplaySqlTest.java` | 311-315 | `codeOf` keeps string literals (IN-10) | ⚠️ Warning | a literal decoy passes only if the real badge column is also renamed |
| `InterestCalibrationReplaySqlTest.java` | 244-246 | Javadoc item 1 is exact only under its own pattern definition | ℹ️ Info | read with Postgres semantics it overstates; name the covered forms when WR-05 is fixed |
| `InterestCalibrationReplaySqlTest.java` | - | TBD/FIXME/XXX/TODO/HACK | none | 0 markers |
| `InterestCalibrationReplaySqlTest.java` | 71, 365-379 | IN-08, IN-09 (pre-existing, out of 07-11 scope) | ℹ️ Info | open in the disposition |
| `JevEventLogging.java` | breaker lambda | `-1.0%` rates on recovery transitions (WR-01) | ⚠️ Warning | carried forward, still open |
| `MyfeederProperties` / `TierThresholds` | - | tier pair not validated (WR-03) | ⚠️ Warning | carried forward, still open |
| `utils/interest.ts` | 32 | 70/40 fallback vs the shipped 70/22 (IN-01) | ℹ️ Info | carried forward |

### Human Verification Required

1. **Prod UI loads with a configured rubric.** Open `/priority`, then Settings > Interests. Expected: badges on scored articles, no "not configured" notice, and the rest of the UI unchanged. Why human: visual (07-06 deferred human-check).
2. **Badge tier colours at 70/22.** Expected: 22-39 neutral, 70+ high, under 22 low. A brief low-colour flash for 22-39 on first paint is the known fallback (IN-01). Why human: CSS rendering.
3. **Judgment-tier prohibitions.** Confirm the table above, including the 07-11 items. Why human: ADR-550 D4.

### Gaps Summary

No gaps remain. Plan 07-11 closes the one gap from the previous run (truths 8 and 13, WR-04) and the recommended IN-07 item, and I confirmed this with my own evidence:
- a forced re-run passes 12/12;
- a probe that calls the real compiled guard shows every WR-04 decoy and IN-07 form failing at HEAD;
- the same probe shows them passing the older guards, which confirms the RED claims.

The replay SQL and the app SQL are byte-identical to the text behind the shipped 100/70/22 calibration. Nothing was pushed, merged to main, released or deployed.

The fresh review's WR-05 and IN-10 are real, and I reproduced both. They need a deliberately non-canonical CTE spelling (or a column rename) plus a verbatim decoy. No ordinary edit or app-side change reaches them, so I judge them residual hardening beyond the verified truths, not a failure of "the replay is drift-guarded". They remain open in `07-REVIEW-DISPOSITION.md` for `/gsd-code-review 07 --fix`. If you read "fails on any drift" as adversary-proof, reopen truth 8 and apply the WR-05 fix. The fix is mechanical: ban `/*` in the replay, widen `BLEND_START` and blank string literals.

The status is `human_needed` only because of the carried-forward prod visual checks and the judgment-tier prohibitions. Once those are confirmed, OPS-01..03 can be marked Complete.

---

_Verified: 2026-09-29T03:30:31Z_
_Verifier: Claude (gsd-verifier)_
