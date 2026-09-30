---
phase: 09-engagement-learning-model
reviewed: 2026-09-30T00:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - CLAUDE.md
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/types/index.ts
  - src/main/frontend/src/utils/feedback.test.ts
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
  - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
  - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
  - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
  - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestLearnedGridTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesEngagementTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesLatencyTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesZeroEngagementTest.java
  - src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
  - src/test/resources/application.yaml
  - src/test/resources/interest/v021-unread-blend.sql
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-09-30T00:00:00Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

I reviewed the Phase 9 diff (`81a73e2..HEAD`) against decisions D-01..D-17 in `09-CONTEXT.md`. The core SQL is correct:

- `engaged` collapses engagement to one MAX strength per article before the topic join.
- `eng_learned` counts SCORED articles only.
- A topic with a negative base is zeroed in both `eng_raw` and `eng`.
- `eff2` applies the single clamp to `base + thumbs + eng`.

The split by subtraction is exact. `interest_topic.weight` is an INTEGER, so `ROUND(w) - ROUND(base)` equals the old `ROUND(w - base)` byte for byte. The grid test covers every clamp branch. The zero-engagement test compares against a frozen copy of the v0.2.1 SQL rather than the new code's own text. The replay's drift guard and `driverPassesEveryBlendParameter` keep the psql copy and the JdbcClient bindings in step. Self-validation through `MyfeederProperties implements Validator` works because Boot's `ConfigurationPropertiesBinder` adds a bound target that implements `Validator` to its validators. It does not collide with MVC or method validation, which look up `jakarta.validation.Validator`.

I found no blockers. The defects are in how the new limit is reported to users and in the calibration tooling:

- **ENGAGEMENT_CAP hides the real limit (WR-01).** `ENGAGEMENT_CAP` outranks and hides the SIGN_CLAMP and WEIGHT_RANGE notes, which are the ones that actually hold a vote back.
- **Unexplained smaller vote effect (WR-03).** When a vote replaces an article's engagement, the effect is smaller than the usual nudge but still reports `NONE`.
- **Replay accepts bad constants (WR-02).** The replay driver accepts engagement constants that the app refuses to start with.

## Warnings

### WR-01: ENGAGEMENT_CAP outranks, and hides, the limit that actually holds a vote back

**File:** `src/main/java/org/bartram/myfeeder/service/LearnedLimit.java:73-75` (consumed at `ArticleFeedbackService.java:111`, `src/main/frontend/src/utils/feedback.ts:77-90`, `TopicRow.tsx:222-229`)
**Issue:** `LearnedLimit` is documented as "why a topic's weight change is smaller than the nominal nudge". The engagement cap never limits a vote's thumbs change, because the two caps are independent and additive. Yet `ENGAGEMENT_CAP` is returned before the checks for the bound that does hold the vote back:
- Base 45, engagement at its cap of 8, and a 👍: the sum is 55, so WEIGHT_RANGE holds the weight at +50. The check returns `ENGAGEMENT_CAP`, so the toast prints `Go +0.0` without its old "(weight at max +50)" note, and the topic editor drops "(at the +50 limit)".
- Base 10, thumbs −19, engagement 8 (the worked example in CONTEXT): the check returns `ENGAGEMENT_CAP` rather than `SIGN_CLAMP`, so "(can't cross 0)" disappears.

`engagementCapOutranksClampAndRange` in `ArticleFeedbackServiceTest` locks this in.

Engagement never decays (D-08), and about 16 strong opens reach the cap. Every favored topic will therefore sit at `ENGAGEMENT_CAP` permanently, and its clamp and range notes will be lost for good. Because `formatVoteToast` lists any limit other than `NONE`, a topic the vote did not move also shows up as "+0.0" on every vote. `feedback.test.ts` `engagementCapWithNoChangeIsStillListed` records this. The precedence follows D-11, but this consequence was not weighed.
**Fix:** Judge the binding bounds first, and report the engagement cap only when nothing else applies:
```java
if (Math.abs(w.learnedRaw()) >= learnedCap) return LEARNED_CAP;
double sum = w.base() + w.learned();
if ((w.base() > 0 && sum < 0) || (w.base() < 0 && sum > 0)) return SIGN_CLAMP;
if (Math.abs(sum) > InterestService.MAX_WEIGHT) return WEIGHT_RANGE;
if (engagementCap > 0 && w.engagementRaw() >= engagementCap) return ENGAGEMENT_CAP;
return NONE;
```
Alternatively, keep D-11 but record this as an explicit Phase 10 prerequisite, so the note does not regress on main in the meantime. Either way, update `engagementCapOutranksClampAndRange` to match.

### WR-02: The replay driver accepts engagement constants the app refuses to start with

**File:** `scripts/interest-calibration-replay.sh:36-41`
**Issue:** The driver checks only that each value is a non-negative decimal. Phase 12 calibrates with this script, and it will replay without complaint:
- `ENGAGEMENT_SAVE_WEIGHT=1` or higher (D-03 forbids it)
- `ENGAGEMENT_OPEN_WEIGHT` greater than or equal to `ENGAGEMENT_SAVE_WEIGHT`
- `ENGAGEMENT_CAP=20` or higher, which is at or above `LEARNED_CAP`

`MyfeederProperties.validate` rejects all of these at startup. A calibration could pick and record a winning candidate that then fails `ApplicationContext` startup on deploy. The two rule sets can also drift apart.
**Fix:** Mirror D-02 after the numeric checks and before connecting, for example with awk, since bash has no float compare:
```bash
if ! awk -v o="$ENGAGEMENT_OPEN_WEIGHT" -v s="$ENGAGEMENT_SAVE_WEIGHT" -v c="$ENGAGEMENT_CAP" -v l="$LEARNED_CAP" \
     'BEGIN { exit !(c == 0 || (o >= 0 && o < s && s < 1 && c > 0 && c < l)) }'; then
  echo "invalid engagement constants: cap 0, or 0 <= open < save < 1 and 0 < cap < learned-cap" >&2
  exit 2
fi
```
Add a case to `InterestCalibrationReplaySqlTest` that runs the driver with save 1 and expects exit 2.

### WR-03: A vote that replaces engagement gets a smaller effect but `limit` stays `NONE`

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java:101-113`, `LearnedLimit.java:69-86`
**Issue:** D-12 makes "before" include the engagement the vote then removes. For an engaged article under the engagement cap, a 👍 therefore moves the topic by `vote share − engagement share`, not by the full nudge. `FeedbackApiIntegrationTest.voteEffectBeforeIncludesTheEngagementItReplaces` asserts this: 20.9 → 21.8 (+0.9 against a nominal +1.8) with limit `NONE`. Deleting the vote reports −0.9, also `NONE`. The toast shows "Rust +0.9" with no note, which breaks the contract that `limit` names why the change is smaller than the nudge (FDBK-03). This happens on every vote on an article the user opened or starred first, which is the normal path: open, read, then vote.
**Fix:** Add a limit value, appended to the enum (never renamed), such as `REPLACED_ENGAGEMENT`, or a boolean on `TopicEffect`. Set it when `before.engagementLearned() != after.engagementLearned()` and the thumbs part changed. Phase 10 can word it. At minimum, document this case on `TopicEffect` and in the Phase 10 plan, so the missing note is a deliberate decision.

## Info

### IN-01: Engagement is shown as "Learned from votes" on main until Phase 10

**File:** `src/main/frontend/src/components/TopicRow.tsx:236,251`; tests at `TopicRow.test.tsx:647-659`, `feedback.test.ts:166-188`
**Issue:** `learned.learned` now combines votes and engagement. A topic with no votes and 8 engagement points reads "Learned from votes +8.0". The new tests pin this mislabel and the "+0.0" noise as the expected output. This is intentional (deferred to Phase 10), but anything merged to main before Phase 10 shows it.
**Fix:** Keep the Phase 10 dependency explicit. When Phase 10 rewords the label, update these tests rather than keeping them as regression anchors.

### IN-02: `engaged` treats every kind that is not an open as a save

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:141-143` (and all 5 replay copies)
**Issue:** `CASE WHEN g.kind = 'OPEN_ORIGINAL' THEN open ELSE save END` gives any future engagement kind the save weight silently. The V7 CHECK constraint limits this today. A later migration that adds a kind, for example one for Copy Link, would change ranking without any test failing.
**Fix:** List the save kinds explicitly (`WHEN g.kind IN ('STAR','BOARD','RAINDROP') THEN save`), with a zero `ELSE` or no match. Alternatively, add a test that asserts `EngagementKind.values()` equals the four kinds the CTE knows. Any change to the CTE text needs the replay regenerated together with it.

### IN-03: The replay's `learned` column means something different from the API's `learned`

**File:** `scripts/interest-calibration-replay.sql:107`
**Issue:** In the replay's learned section, `learned` is the thumbs-only capped value (`e.learned`). In `TopicWeight`, `TopicLearned` and `TopicEffect`, `learned` is now thumbs plus engagement (D-15). A calibration record that compares the replay against `/api/interest/topics/learned` will disagree for every engaged topic.
**Fix:** Alias the replay column `thumbs_learned` (its header comment already calls it thumbs), or add a `learned_total` column. Keep aliases clear of the write-keyword list.

### IN-04: Engagement makes Priority scores change on every open or star, so rows can be skipped between pages

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:181-188`
**Issue:** The `priorityPageAfter` Javadoc says a score change between pages "cannot skip rows (WR-02)". That holds only for the cursor row. Any unloaded row whose score rises above the cursor tuple is skipped. Before Phase 9, only a vote could cause this. Now every `o`, star, board or Raindrop save raises the weights of the engaged article's topics, and with them the scores of every article sharing those topics. Phase 10 SC-2 handles the re-sort hint but does not mention skipped rows.
**Fix:** Narrow the Javadoc claim so it states that rows whose score rises above the cursor can be skipped until refresh. Consider adding this to Phase 10's "Ranking changed" scope, for example by forcing a Priority reset when the hint is applied.

---

_Reviewed: 2026-09-30T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
