---
phase: 03-interest-model-schema-rubric-editor
plan: 08
subsystem: testing
tags: [typesafe, jev, calibration, prompt-wording, junit, spring-boot]

requires:
  - phase: 03-02
    provides: "InterestQuestions.forRubric and ArticleStateBuilder.build (shared question/state builders, D-12)"
  - phase: 03-04
    provides: "InterestPreviewService consuming the shared builders"
provides:
  - "Gated live calibration spike InterestCalibrationSpikeTest (JEV_CALIBRATION=true) sending the exact Phase 4 request"
  - "Unit-tested CalibrationStats (A6 thresholds, hinge, blended points, stddev, median, mid-band share, label agreement, repeat delta)"
  - "v2 topic question wording in InterestQuestions ('substantially about `topic`')"
  - "03-CALIBRATION.md with v1/v2 numbers, verdict and Phase 4/5 notes"
affects: [phase-04-scoring, phase-05-priority-ui, phase-07-ops-tuning, interests-dialog]

actuals:
  tokens: 8618
  tasks: 3
  commits: 4
plan_head_before: ff8d7c0be24b083a30f9b9f50e72bf15dc11eee6

tech-stack:
  added: []
  patterns:
    - "Billed live spikes are gated per method with @EnabledIfEnvironmentVariable and run with cleanTest; inputs/reports with personal data live under $HOME/.cache, never in the repo"
    - "Prompt-wording changes go through TDD: a RED test pinning the new phrasing, then the GREEN constant change in the shared builder"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/integration/InterestCalibrationSpikeTest.java
    - src/test/java/org/bartram/myfeeder/integration/CalibrationStats.java
    - src/test/java/org/bartram/myfeeder/integration/CalibrationStatsTest.java
    - .planning/phases/03-interest-model-schema-rubric-editor/03-CALIBRATION.md
  modified:
    - src/main/java/org/bartram/myfeeder/service/InterestQuestions.java
    - src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java
    - .planning/phases/03-interest-model-schema-rubric-editor/deferred-items.md

key-decisions:
  - "Ship v2 topic wording ('substantially about `topic`'): same A6 results and label agreement as v1 (0.859), obvious-match topic nouls roughly doubled, no false firing"
  - "Profile question and levels unchanged: they already give stddev 0.18 and 0.859 high-over-low agreement"
  - "Topic under-firing (no obvious match crosses noul 0.5) is deferred to Phase 4/5 OPS-02 tuning; no v3 run (user authorized one v2 retry)"
  - "Phase 4 needs a scoring Jev timeout well above the 5s production value (profile + 7 topics averages ~2.6s, cold call >5s)"

patterns-established:
  - "Calibration report schema: titles, topic_<id> keys and numbers only, never the profile text, topic descriptions or key"

requirements-completed: [INT-04]

coverage:
  - id: D1
    description: "Gated calibration spike that is skipped in the default suite and sends the exact Phase 4 request when enabled"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "./gradlew test (full suite): InterestCalibrationSpikeTest#calibrateQuestionWording reported skipped"
        status: pass
      - kind: integration
        ref: "direnv exec . env JEV_CALIBRATION=true SPRING_AI_TYPESAFE_TIMEOUT=60s ./gradlew cleanTest test --tests InterestCalibrationSpikeTest (v2 live run, 23 calls, BUILD SUCCESSFUL)"
        status: pass
    human_judgment: false
  - id: D2
    description: "CalibrationStats blend math and statistics"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/integration/CalibrationStatsTest.java#pointsFollowTheScoringModel, stddevAndMedianOfKnownValues, midBandShareUsesInclusiveBounds, labelAgreementCountsPairsAndTies, hingeClampsAtHalf"
        status: pass
    human_judgment: false
  - id: D3
    description: "v2 topic wording in the shared InterestQuestions builder, inherited by the preview and the Phase 4 scorer"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java#topicAsksWhetherTheArticleIsSubstantiallyAboutTheTopic"
        status: pass
      - kind: unit
        ref: "InterestJudgeRequestTest (2) and InterestPreviewServiceTest (10)"
        status: pass
    human_judgment: false
  - id: D4
    description: "03-CALIBRATION.md verdict and whether the ranking under v2 matches the user's sense of high versus low"
    requirement: INT-04
    verification:
      - kind: other
        ref: "03-CALIBRATION.md section check + personal-text leak check (no personal text in findings)"
        status: pass
    human_judgment: true
    rationale: "Research A6: whether the ranking 'means something' is the user's judgment; the thresholds are heuristics"

duration: "~2h 30m wall clock across three executor sessions (including vendor-outage wait)"
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 08: Interest Question Calibration Summary

**Gated live Jev calibration spike (20 real articles, jev-1.13.0) passes all A6 heuristics with 0.859 high-over-low agreement; v2 "substantially about `topic`" wording ships because it roughly doubles obvious-match topic nouls with no regression**

## Performance

- **Duration:** about 2h 30m wall clock across three executor sessions (Task 1 committed 14:46 local; this continuation 17:05–17:15 local), including the TypeSafe outage wait
- **Started:** 2026-09-23 (Task 1, commit fff5931 at 18:46Z)
- **Completed:** 2026-09-23T21:13:22Z
- **Tasks:** 3 of 3 (Task 2 was the human checkpoint)
- **Files modified:** 7

## Accomplishments

- `InterestCalibrationSpikeTest` runs the production builders (`InterestQuestions.forRubric` + `ArticleStateBuilder.build`) against the user's real profile, 7 topics and 20 labelled articles. It is gated on `JEV_CALIBRATION=true` and skipped in every default run.
- `CalibrationStats` handles blend math (score 3/4 + a +20 topic at noul 0.82 = 87.8) and the A6 statistics, unit-tested in the default suite.
- Live results: v1 and v2 both pass every A6 threshold. Label agreement is 0.859 in both, and the repeat delta is ≤ 1.3 points.
- v2 topic wording shipped via TDD (RED `3bba9fd`, GREEN `346e74c`). The topic preview and the Phase 4 scorer inherit it through the shared builder (D-12).
- `03-CALIBRATION.md` records the run, v1/v2 numbers, the verdict, both wording versions, the chosen wording and seven notes for Phase 4/5.

## Final numbers (v1 → v2)

| Statistic | v1 | v2 | Threshold |
|---|---|---|---|
| Stddev normalized profile | 0.181 | 0.178 | ≥ 0.15 PASS |
| Median profile confidence | 0.895 | 0.910 | ≥ 0.5 PASS |
| Mid-band noul share | 0.007 | 0.014 | < 0.5 PASS |
| Label agreement | 0.859 | 0.859 | ≥ 0.75 PASS |
| Max repeat delta (points) | 1.3 | 0.6 | (none) |
| `topic_2` on Kaplan (high) | 0.15 | 0.34 | fires at > 0.5 |
| `topic_2` on Opus pelican (low) | 0.27 | 0.48 | fires at > 0.5 |

## Task Commits

1. **Task 1 (tracer): gated spike runner + CalibrationStats** - `fff5931` (test)
2. **Task 2: user supplied interests/labels and replied "run it"** - no commit (input and report live outside the repo)
3. **Task 3: calibration findings and wording revision**
   - `3bba9fd` (test, RED): `topicAsksWhetherTheArticleIsSubstantiallyAboutTheTopic` fails against v1 (11 tests, 1 failed; re-confirmed in this session)
   - `346e74c` (feat, GREEN): v2 `TOPIC_QUESTION` / `TOPIC_WHEN_TRUE` / `TOPIC_WHEN_FALSE`
   - `9f8987c` (docs): `03-CALIBRATION.md` + deferred items

## Files Created/Modified

- `src/test/java/org/bartram/myfeeder/integration/InterestCalibrationSpikeTest.java`: gated live runner; writes a titles-and-numbers report to `$HOME/.cache/myfeeder-phase03/calibration-report.md`
- `src/test/java/org/bartram/myfeeder/integration/CalibrationStats.java`: A6 constants, hinge, points, stddev, median, mid-band share, label agreement, max delta, `Row`
- `src/test/java/org/bartram/myfeeder/integration/CalibrationStatsTest.java`: five default-suite tests
- `src/main/java/org/bartram/myfeeder/service/InterestQuestions.java`: v2 topic wording constants (profile untouched)
- `src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java`: new test pinning the v2 phrasing
- `.planning/phases/03-interest-model-schema-rubric-editor/03-CALIBRATION.md`: findings, verdict, chosen wording, Phase 4/5 notes
- `.planning/phases/03-interest-model-schema-rubric-editor/deferred-items.md`: dialog help-text and scoring-timeout follow-ups

## Task 2 record

- **Reply:** "run it" (the executor ran the spike with the key loaded via `direnv exec .`; the key was never printed or logged, and the v2 log has 0 occurrences of the key variable assignment).
- **User decisions on the way:** (1) "Revise topic wording (Recommended)": try v2, measure it and keep the better version; (2) after the vendor outage: "Retry v2 once (Recommended)": one more billed v2 run.
- **Did the table match high vs low?** Mostly. 55 of 64 high/low pairs are ordered correctly. Kaplan (high) and the Moon rock (high) top the list. The misses are the FBI breach "high" (5.0 points, a profile-text gap) and "Americans worried about AI" (26.3, below four lightweight AI/gadget "lows").
- **Task 1 input preparation:** the previous session prepared `calibration-input.json`, which ended up with 20 real articles. This continuation has no record of whether the deployment export or the empty-skeleton path was used. The input file was never copied into the repo.

## Decisions Made

- **v2 ships.** A6 results and label agreement are the same as v1, within the repeat noise. v2 raises every obvious-match topic noul (for example `topic_2` 0.15 → 0.34 and 0.27 → 0.48, `topic_7` 0.14 → 0.26) and leaves unrelated articles at 0.00–0.05.
- **No further revision.** The plan allows at most two revisions, and the user authorized one v2 retry. Topic under-firing is handed to Phase 4/5 (see Notes).

## Notes for Phase 4/5

1. **Scoring timeout.** Production `spring.ai.typesafe.timeout: 5s` is too short for a profile + 7-topic call (about 2.6s average, first call > 5s). The spike used a `SPRING_AI_TYPESAFE_TIMEOUT=60s` env override. Phase 4 needs 15–30s or a scorer-specific timeout (logged in deferred-items).
2. **Topic under-firing.** v1 → v2 numbers are above. No obvious match crosses noul 0.5, so topics add about 0 points and ranking is profile-only today. Options: a v3 wording ("falls within `topic`"), richer descriptions, or a hinge-midpoint change (a scoring-model decision for the user). Caveat: `topic_2` rose most on a user-labelled *low* Opus demo, so stronger firing could lower label agreement.
3. **The FBI breach "high" at 5.0 points** (profile 0.05, confidence 0.83) is a profile-text gap, not wording. The user should add the subject to the profile or add a topic.
4. **TypeSafe outage.** The first v2 attempts failed with 503, then 529 (overloaded), and may have been partially billed. An unauthenticated probe showed the API answering 404/403. The single authorized retry succeeded.
5. **Frontend follow-up.** `src/main/frontend/src/components/InterestsDialog.tsx:31` still says topics describe "what an article is primarily about". Update it to match v2 ("substantially about"). It is outside this plan's file list and logged in deferred-items.
6. Tier hint for OPS-02 and the confidence/summary-length observations are in `03-CALIBRATION.md`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Jev timeout overridden to 60s for the spike run**
- **Found during:** Task 3 (first billed runs, previous session)
- **Issue:** the production `spring.ai.typesafe.timeout: 5s` timed out on a profile + 7-topic call (the first call took more than 5s), so the plan's run command could not finish.
- **Fix:** ran the spike with `SPRING_AI_TYPESAFE_TIMEOUT=60s` in the environment only; no config or code change.
- **Files modified:** none
- **Verification:** the v2 run completed all 23 calls (BUILD SUCCESSFUL)
- **Committed in:** n/a (env-only)

**2. [Process] RED/GREEN split for the wording revision**
- The plan asked for one Task 3 commit for the wording plus tests. Per the orchestrator's TDD instruction it was split into a RED test commit (`3bba9fd`) and a GREEN wording commit (`346e74c`), plus a docs commit (`9f8987c`).

**3. [Scope] deferred-items.md updated**
- Two follow-ups outside this plan's file list (dialog help text, scoring timeout) were logged there instead of being fixed.

---

**Total deviations:** 1 auto-fixed (Rule 3), 2 process/scope notes
**Impact on plan:** None on scope. The timeout override was needed to run the spike at all and is itself a Phase 4 finding.

## Issues Encountered

- TypeSafe vendor outage (HTTP 503, then 529) during the first v2 attempts in the previous session. Handled by a user decision checkpoint. After a no-cost health probe, the one authorized retry succeeded.

## User Setup Required

None new. The spike reuses `MYFEEDER_TYPESAFE_API_KEY` from the repo's `.envrc` (direnv).

## Next Phase Readiness

- Phase 3 is complete (8/8 plans). The question wording is calibrated and recorded before Phase 4 builds scoring on it.
- Phase 4 must set a longer scoring timeout and should expect topics to contribute little until topic firing is tuned.

## Self-Check: PASSED

- FOUND: InterestCalibrationSpikeTest.java, CalibrationStats.java, CalibrationStatsTest.java, 03-CALIBRATION.md, InterestQuestions.java, InterestQuestionsTest.java
- FOUND commits: fff5931, 3bba9fd, 346e74c, 9f8987c
- Full `./gradlew test`: 307 tests, 0 failures, 0 errors, 2 skipped (InterestCalibrationSpikeTest, JevLiveSmokeTest)
- 03-CALIBRATION.md: five required sections + `jev-1.13.0`; leak check prints "no personal text in findings"; no `calibration-(input|report)` in `git status`

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*
