---
phase: 07-rollout-calibration
verified: 2026-09-29T02:35:00Z
status: gaps_found
score: 16/18 must-haves verified
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
covered_digest: "v2:sha256:8f38ebaf1978cf7270d14e868dc21de042b5f0a3cee63bc3364251612f1d8c02"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 10/11
  gaps_closed:
    - "Every 'WITH learned AS' line is checked by exact equality, in file order (unread x3, window, learned), exactly 5 lines (prior gap missing item 1)"
    - "A one-byte drift of any single existing copy (5 blend lines, 4 badge copies) now fails the guard, and a missing or extra copy fails (the scenario the prior gap described: hand-editing the top or bottom copy, or 3 of the 4 badge copies)"
  gaps_remaining:
    - "07-04 'fails on any drift' is still not fully met for the badge copies: a drifted badge copy passes when the verbatim text is also present in a trailing -- comment, a /* */ block comment, or a new section (07-REVIEW WR-04)"
  regressions: []
gaps:
  - truth: "07-04: InterestCalibrationReplaySqlTest fails on any drift of a blend or badge copy in scripts/interest-calibration-replay.sql; and 07-10: a copy pasted into a -- comment cannot hide a drifted statement copy"
    status: partial
    reason: "07-10 closed the prior gap for single-copy drift. The badge check is still occurrences(withoutComments(sql), INTEREST_SCORE) == 4. withoutComments drops only whole lines that start with '--', and the count never checks which statement holds each copy. An in-memory re-implementation of assertEveryCopyIsVerbatim, run against the real SQL file (baseline passes, and a lone drift of the top badge fails, which matches the Java test), shows that these still pass: (a) the top badge drifted to ROUND(b.raw_n, 1) with the old text kept in a trailing '-- was ...' comment on the same line; (b) the same drift with the old text in a /* */ block comment; (c) the same drift plus a new appended section that uses the verbatim badge. In each case the top-20 calibration rows would no longer match the app's badge while the guard stays green. The 07-10 must-have's own claim ('a copy pasted into a -- comment cannot hide a drifted statement copy') is false for trailing comments. The prior gap's missing item said 'exactly 4 times in the SQL', a raw count that would have caught (a) and (b); 07-10 narrowed it to the non-comment text. Blend lines are not affected: every existing copy is pinned by equality and order."
    artifacts:
      - path: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java"
        issue: "assertEveryCopyIsVerbatim (lines 180-182) counts INTEREST_SCORE in withoutComments(sql), which removes only whole-line -- comments, and does not pin each badge copy to its statement; the Javadoc at lines 163-165 overstates the guarantee"
    missing:
      - "Count INTEREST_SCORE over the raw file text (comments included) and require exactly 4, so a copy in any comment form becomes a fifth copy and fails"
      - "Pin each badge copy to its statement: the line after each unread or window blend line (SQL lines 31, 53, 59, 66) holds exactly one INTEREST_SCORE"
      - "Add mutation cases to aMissingOrExtraCopyFails: drift one badge copy and keep the verbatim text in a trailing -- comment, in a /* */ comment, and in an added section; each must fail. Correct the Javadoc claim"
      - "Recommended in the same edit (07-REVIEW IN-07, not blocking on its own): find blend lines leniently ((?im)^\\s*with\\s+learned\\s+as\\b outside comments, exactly 5) before the strict label check, so an extra indented or lower-case drifted blend line is not skipped"
behavior_unverified_items: []
human_verification:
  - test: "Open http://192.168.44.204/priority, then Settings > Interests"
    expected: "Priority lists scored articles with badges; the Interests dialog shows no 'not configured' notice; reader, feed tree and article list behave as before"
    why_human: "Visual confirmation of the prod UI; API checks cannot show rendering (carried forward from the initial verification; not re-checked because prod was out of scope for this re-verification)"
  - test: "In the prod Priority and article lists, look at badges scored 22-39 and 70+"
    expected: "22-39 render in the neutral colour, 70+ in the high colour, below 22 low (served tiers 70/22). A brief low-colour flash for 22-39 on first paint is the known 70/40 fallback (07-REVIEW IN-01)"
    why_human: "Tier colours are CSS rendering"
  - test: "Confirm the judgment-tier prohibitions of 07-06..07-10 (approve-before-release gates, no prod writes by hand, no force-push/no-verify/disableChecks, no rubric text or key in committed files, no SDK retry / concurrency > 1, 07-10 test-only with no push or merge)"
    expected: "Each holds; the verifier's non-authoritative LLM judgment found supporting evidence for all (see Prohibitions)"
    why_human: "Judgment-tier prohibitions require explicit human resolution (ADR-550 D4)"
---

# Phase 7: Rollout & Calibration Verification Report

**Phase Goal:** Interest ranking is live in production with a real key, the backlog is scored, and the scores are tuned so they mean something
**Verified:** 2026-09-29T02:35:00Z
**Status:** gaps_found (the three roadmap success criteria remain verified; the replay drift guard is tighter but still misses one class of badge drift)
**Re-verification:** Yes, after gap-closure plan 07-10 (commits 08185bf, 7ede686, 1531fac; merged in d0d06fa)

## Re-verification Scope

- **Carried-forward gap (truth 8):** full re-check against the test source, the SQL, a forced re-run of the test class and an in-memory mutation probe.
- **Previously verified truths 1-7 and 9-11:** regression check. Since the prior verification (`aa2069f`), the only change outside `.planning/` is `InterestCalibrationReplaySqlTest.java` (`git diff --stat aa2069f HEAD -- . ':!.planning'`). `scripts/interest-calibration-replay.sql` and `InterestScoreQueries.java` have a 0-line diff against `aa2069f`. No production, frontend, yaml, Helm or CLAUDE.md file changed, so no code path behind truths 1-7 and 9-11 could regress. Prod was not contacted (per instructions), so the live facts in truths 1, 2, 9 and 11 are carried forward from the initial run. They cannot have changed through this phase's commits because nothing was built, pushed or deployed: `1531fac` is only on `sbartram/main`, and `origin/main` = `main` = `5461d0a`.
- **07-10 must-haves (truths 12-18):** new in this run.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | SC1: released and deployed with a live key; backfill drained with no 429 storms and without opening the circuit | ✓ VERIFIED (regression: no change) | Initial-run evidence stands (0.2.1 live, 199 SCORED / 0 FAILED, 53/53 CLOSED samples, 0 `Jev ` retry lines). No release, image or deploy since |
| 2 | SC2: blend constants configurable and tuned against the real distribution; badges spread across tiers | ✓ VERIFIED (regression: no change) | `application.yaml`, `MyfeederProperties`, `InterestScoreQueries`, `InterestStatusService` unchanged since `aa2069f`; 07-CALIBRATION.md approval unchanged |
| 3 | SC3: CLAUDE.md documents the Jev behaviors and gotchas | ✓ VERIFIED (regression: no change) | Committed CLAUDE.md unchanged since `aa2069f` (the modified file in the working tree is `.claude/CLAUDE.md`, a separate GSD file) |
| 4 | 07-01: tiers bind from yaml, `tiers` appended last, config-only retune | ✓ VERIFIED (regression: no change) | Source unchanged |
| 5 | 07-02: InterestBadge uses served tiers via TierContext, 70/40 fallback | ✓ VERIFIED (regression: no change) | Frontend unchanged; caller reports vitest 322/322 after the merge |
| 6 | 07-03: JevEventLogging lines with class names and numbers only | ✓ VERIFIED (regression: no change) | Source unchanged |
| 7 | 07-04: read-only replay driver reproduces the blend verbatim, validates inputs, needs the password | ✓ VERIFIED | SQL and driver unchanged; `replayIsReadOnly`, `driverRejectsANonNumericCandidate`, `driverRequiresThePassword` pass in the forced re-run |
| 8 | 07-04: `InterestCalibrationReplaySqlTest` fails on any drift (single-byte difference) of a blend or badge copy | ✗ FAILED (partial, narrowed) | Single-byte drift of any one existing copy now fails (5 blend lines + 4 badge copies; test and probe agree). Drift of a badge copy still passes when the verbatim text survives in a trailing `--` comment, a `/* */` comment or a new section. See Gaps and "Judgment on WR-04" |
| 9 | 07-06: 0.2.0 released (tag, image, dump, Flyway V6, key, soak) | ✓ VERIFIED (regression: no change) | Tags and 07-BACKFILL.md unchanged |
| 10 | 07-08: calibration after the drain with top/bottom 20 and explicit approval | ✓ VERIFIED (regression: no change) | 07-CALIBRATION.md unchanged |
| 11 | 07-09: approved constants shipped as 0.2.1 via source control; test yaml at 100/70/40; no Helm/env override | ✓ VERIFIED (regression: no change) | yaml and helm unchanged since `aa2069f` |
| 12 | 07-10: every `WITH learned AS` line equals its Java text, in order unread x3, window, learned, exactly 5 lines | ✓ VERIFIED | `assertEveryCopyIsVerbatim` labels each column-0 `WITH learned AS` line by `equals` (unread, window) or `startsWith(LEARNED_SECTION)` and asserts `containsExactly("unread","unread","unread","window","learned")`. Probe: a one-byte drift at the middle of SQL lines 30, 52, 58, 65 and 90 each fails |
| 13 | 07-10: the test fails unless INTEREST_SCORE occurs exactly 4 times in the non-comment text, and a copy pasted into a `--` comment cannot hide a drifted statement copy | ✗ FAILED (partial) | Exact count 4 is enforced over `withoutComments(sql)`, which drops only whole-line `--` comments. Probe: top badge drifted + verbatim text in a trailing `-- was ...` on the same line passes; the same with `/* */` passes. The truth's second clause is falsified. Same root cause as truth 8 |
| 14 | 07-10: a one-byte drift at the middle of any of the nine copies throws AssertionError, after a passing baseline | ✓ VERIFIED | `driftInAnySingleBlendCopyFails` and `driftInAnySingleBadgeCopyFails` assert the baseline passes first, then loop every occurrence; both pass in the forced re-run |
| 15 | 07-10: 4/6 blend lines and 3/5 badge copies fail; 5 and 4 pass | ✓ VERIFIED | `aMissingOrExtraCopyFails` (4 variants) and `everyCopyInTheReplayIsVerbatim` pass |
| 16 | 07-10: the six existing tests are unchanged and pass | ✓ VERIFIED | `git diff aa2069f HEAD` on the test removes only two Javadoc lines; all six methods present and passing (10/10 total) |
| 17 | 07-10: test-only fix; SQL and InterestScoreQueries byte-identical to `aa2069f`; mutations in memory only | ✓ VERIFIED | 0-line diff for both files; every variant is a `String`; no `Files.write` in the test |
| 18 | 07-10: 07-REVIEW-DISPOSITION.md records WR-02 fixed by 07-10; others stay open | ✓ VERIFIED | `\| WR-02 \| warning \| fixed \| 07-10 \|`; WR-01, WR-03, WR-04, IN-01..IN-09 open |

**Score:** 16/18 truths verified (0 present, behavior-unverified). Truths 8 and 13 share one root cause.

### Judgment on WR-04: gap or advisory?

The caller asked whether the WR-04 residual keeps the 07-04 must-have open or is an advisory outside its literal scope. My judgment is that **it keeps the must-have open (partial)**, for four reasons.

1. **The must-have's words cover it.** 07-04 says the test "fails on any drift" of a badge copy, "so identical constants give identical scores to the running app". In the probe cases the `top` statement's badge is drifted, so the replay's top-20 rows (the evidence behind the shipped 100/70/22) would differ from the app, and the test stays green. That is a drift of a badge copy that is not caught. The extra condition (verbatim text elsewhere) is not an exotic attack: keeping the old expression in a `-- was ...` comment while editing is an ordinary habit, and adding a section is the normal way the replay grows.
2. **The gap-closure plan's own truth is falsified.** 07-10 truth 2 states that "a copy pasted into a -- comment cannot hide a drifted statement copy". A trailing `--` comment does hide one. The claim is false as written, independent of how broadly 07-04 is read.
3. **The prior gap prescribed a stronger check than was built.** Missing item 2 said "INTEREST_SCORE occurs exactly 4 times in the SQL", which is a raw count that would have caught the trailing and block comment cases (they make 5). 07-10 narrowed it to non-comment text. So the carried-forward gap is not fully closed by its own prescription either.
4. **Evidence gate (#3304).** This is the carried-forward gap, and the flagged file was modified after the prior `verified:` timestamp. It also has deterministic evidence: a reproducible in-memory probe that mirrors `assertEveryCopyIsVerbatim`, agrees with the Java test on the baseline (pass) and on a lone drift (fail), and passes the three WR-04 variants. So it stays blocking rather than dropping to advisory.

What argues the other way, recorded for the human decision: every existing copy is byte-identical today; the blend part of the guarantee is fully met; the shipped calibration numbers are sound (prod cross-checks matched 5/5 twice); and the remaining hole needs two coincident edits. If you read "drift" as "a copy differs and nothing else changes", the must-have is met and WR-04 is hardening. The fix is small (a raw count plus a per-statement placement check), which is why I recommend closing it over accepting an override.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `InterestCalibrationReplaySqlTest.java` | every-copy drift guard with in-memory proofs | ⚠️ PARTIAL | blend guard complete; badge guard is count-only over whole-line-comment-stripped text (WR-04) |
| `07-REVIEW-DISPOSITION.md` | `\| WR-02 \| warning \| fixed \| 07-10 \|` | ✓ VERIFIED | |
| `scripts/interest-calibration-replay.{sh,sql}` | unchanged, read-only | ✓ VERIFIED | 0-line diff vs `aa2069f` |
| All other phase artifacts | as in the initial report | ✓ VERIFIED | unchanged since `aa2069f` |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| drift test | InterestScoreQueries | `blendCte(UNREAD_SCOPE)` / `blendCte(WINDOW_SCOPE)` by equality, `LEARNED_CTE` by prefix, `INTEREST_SCORE` counted | WIRED for blend lines; PARTIAL for badge placement |
| drift test | replay SQL | `Files.readString(SQL)`, `line.startsWith("WITH learned AS")` | WIRED (column-0, case-sensitive discovery; IN-07) |
| All other links | | | unchanged from the initial report (WIRED) |

### Data-Flow Trace (Level 4)

Unchanged from the initial report: tier thresholds and scores flow from yaml and the SQL blend to the badge (✓ FLOWING). No data-path file changed.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Drift guard class | `./gradlew test --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest" --rerun` | BUILD SUCCESSFUL; XML: tests=10 failures=0 errors=0, timestamp 2026-09-29T02:30:31Z | ✓ PASS |
| Single-copy drift caught | in-memory probe (Python mirror of `assertEveryCopyIsVerbatim`, SQL read-only) | one-byte drift of lines 30/52/58/65/90 fails; lone top-badge drift fails | ✓ PASS |
| Compound badge drift caught | same probe | drift + trailing `--` copy: passes; drift + `/* */` copy: passes; drift + new verbatim section: passes | ✗ FAIL (gap) |
| Extra non-column-0 blend line caught | same probe | an indented drifted extra blend line passes (IN-07) | ⚠️ (recommended fix) |
| Production files untouched | `git diff aa2069f HEAD -- scripts/interest-calibration-replay.sql .../InterestScoreQueries.java \| wc -l` | 0 | ✓ PASS |
| 07-10 not pushed or merged | `git branch -r --contains 1531fac`; `git rev-parse origin/main main` | no remote branch; both 5461d0a | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED (no `scripts/*/tests/probe-*.sh` exist and no plan declares one). The in-memory probe above is a verifier spot-check, not a project probe.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| OPS-01 | 07-03, 07-06, 07-07 | Released and deployed with a live key; backfill without 429 storms or open circuit | ✓ SATISFIED | Truths 1, 6, 9 |
| OPS-02 | 07-01, 07-02, 07-04, 07-08, 07-09, 07-10 | Blend constants configurable and tuned against the real distribution | ✓ SATISFIED | Truths 2, 4, 5, 7, 10, 11, 12, 14-18. Truths 8 and 13 are a durability gap in the tuning tool's guard, not a gap in the tuning itself |
| OPS-03 | 07-05 | CLAUDE.md documents Jev behaviors and gotchas | ✓ SATISFIED | Truth 3 |

No orphaned requirements: REQUIREMENTS.md maps exactly OPS-01..03 to Phase 7, and plans claim all three. (The REQUIREMENTS.md traceability rows currently read "Gaps Found"; the orchestrator owns those.)

### Prohibitions (judgment tier, non-authoritative LLM verdict, flagged for human confirmation)

| Plan | Prohibition | LLM verdict | Evidence |
|---|---|---|---|
| 07-06/07-09 | no release action before `approve` | holds | as in the initial report |
| 07-06/07-07/07-08 | no prod writes by hand | holds | as in the initial report |
| 07-06/07-09 | no force-push, --no-verify, disableChecks, moved tag | holds | as in the initial report |
| 07-06/07-07/07-08 | no key or rubric text in committed files | holds | as in the initial report; the 07-10 commits touch only the test and the disposition |
| 07-07 | no SDK retry, concurrency stays 1 | holds | yaml unchanged |
| 07-10 | must not edit the replay SQL, InterestScoreQueries or any production file | holds | 0-line diff |
| 07-10 | must not weaken, rename or delete the six existing tests; no DB or network | holds | only Javadoc lines removed; the driver tests use 127.0.0.1:1 as before |
| 07-10 | no merge to main, push, tag, image, deploy or prod access | holds | `1531fac` only on `sbartram/main`; `main` = `origin/main` = `5461d0a` |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `InterestCalibrationReplaySqlTest.java` | 180-190 | badge count over whole-line-comment-stripped text, no placement check (WR-04) | 🛑 Blocker (carried-forward gap) | compound badge drift is undetected |
| `InterestCalibrationReplaySqlTest.java` | 163-165 | Javadoc claims a comment copy cannot hide a drift | ⚠️ Warning | false claim; fix with the code |
| `InterestCalibrationReplaySqlTest.java` | 170-171 | column-0, case-sensitive blend discovery (IN-07) | ⚠️ Warning | an extra indented or lower-case drifted blend line is skipped |
| (07-10 files) | - | TBD/FIXME/XXX/TODO/HACK | none | 0 markers |
| `JevEventLogging.java` | breaker lambda | `-1.0%` rates on recovery transitions (WR-01) | ⚠️ Warning | carried forward, still open |
| `MyfeederProperties` / `TierThresholds` | - | tier pair not validated (WR-03) | ⚠️ Warning | carried forward, still open |
| `utils/interest.ts` | 32 | 70/40 fallback vs shipped 70/22 (IN-01) | ℹ️ Info | carried forward |

### Human Verification Required

1. **Prod UI loads with a configured rubric:** open `/priority`, then Settings > Interests. Expected: badges on scored articles, no "not configured" notice, the rest of the UI unchanged. Why human: visual.
2. **Badge tier colours at 70/22:** 22-39 neutral, 70+ high, under 22 low; a brief first-paint low flash for 22-39 is the known fallback (IN-01). Why human: CSS rendering.
3. **Judgment-tier prohibitions:** confirm the table above, including the three 07-10 items. Why human: ADR-550 D4.

### Gaps Summary

The phase goal is still achieved in production, and nothing behind the three roadmap success criteria changed in this round: the only non-planning change is the test file, and nothing was built, pushed or deployed.

Gap-closure plan 07-10 fixed the defect the prior verification found. The guard now checks every blend line by exact equality and in order, requires exactly four badge copies, and proves by in-memory mutation that a one-byte drift of any single existing copy fails. The forced re-run passes 10/10.

One residual remains, with the same root cause behind truths 8 and 13 (07-REVIEW WR-04). The badge check counts copies after stripping only whole-line `--` comments and does not check which statement holds each copy. A drifted `top` badge passes if the old verbatim text survives in a trailing `--` comment, a `/* */` comment or a new section. I reproduced all three in memory. I judge this a remaining partial gap rather than an advisory, because it contradicts both the 07-04 "fails on any drift" guarantee and 07-10's explicit claim about comment copies, and because the prior gap's prescribed raw count would have caught the comment cases. The fix is a raw-text count of exactly 4, a per-statement placement check, three mutation cases and a Javadoc correction; lenient blend-line discovery (IN-07) can go in the same edit.

**If you judge WR-04 to be hardening beyond the must-have**, accept it with an override instead of another gap-closure plan:

```yaml
overrides:
  - must_have: "07-04: InterestCalibrationReplaySqlTest fails on any drift of a blend or badge copy in scripts/interest-calibration-replay.sql; and 07-10: a copy pasted into a -- comment cannot hide a drifted statement copy"
    reason: "Single-copy drift of every existing blend and badge copy is caught (07-10). The residual needs a drift plus a verbatim copy kept in a trailing/block comment or a new section; tracked as 07-REVIEW WR-04 for /gsd-code-review 07 --fix"
    accepted_by: "<name>"
    accepted_at: "<ISO timestamp>"
```

---

_Verified: 2026-09-29T02:35:00Z_
_Verifier: Claude (gsd-verifier)_
