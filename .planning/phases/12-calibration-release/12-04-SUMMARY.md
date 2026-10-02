---
phase: 12-calibration-release
plan: 04
subsystem: interest-ranking
tags: [engagement, calibration, yaml, docs, CAL-03, SC-4]
status: complete
requires:
  - 12-03 (12-CALIBRATION.md approved-constants line)
provides:
  - Shipped engagement constants 0.25 / 0.5 / 8 in main application.yaml with Phase 12 outcome comments
  - shippedMainYamlStarts tied to the approved-constants line
  - CLAUDE.md SC-4 documentation (Engagement tuning (v0.3.1, Phase 12) bullet, rewritten Constants bullet, corrected Tier thresholds and tuning bullet)
affects:
  - 12-05 (release ships exactly these constants)
tech-stack:
  added: []
  patterns:
    - "D-14 yaml split: main yaml holds the calibrated values, test yaml keeps the D-01 fixture values, the dev overlay holds only differing values"
key-files:
  created: []
  modified:
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
    - CLAUDE.md
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
decisions:
  - "Engagement constants ship as 0.25 / 0.5 / 8, unchanged from D-01: the D-03 fallback on thin data (13 of 30 counted engaged articles, 0 of 3 topics with engagement), marked revisit"
  - "application-dev.yaml left untouched: no approved value differs from the test yaml's 0.25 / 0.5 / 8"
metrics:
  duration: ~15 min
  completed: 2026-10-02
actuals:
  tokens: 4900
  tasks: 2
  commits: 2
plan_head_before: 244c90b9e37c3f75b9daf6ca157919f81efefd56
plan_head_after: 7cedef264a8603c6ab2894c1f4af7af6aa1c7ccd
---

# Phase 12 Plan 04: Ship the Approved Engagement Constants Summary

The engagement constants stay at open-weight 0.25, save-weight 0.5 and cap 8 in main yaml. Phase 12 kept them through the D-03 fallback on thin data and marked them revisit. The startup test is tied to the approved-constants line. CLAUDE.md now documents the full engagement tuning workflow and the Phase 12 outcome (SC-4).

## What was done

### Task 1 (tracer): approved constants in yaml (commit 89ce2a5)
- Checked the precondition: 12-CALIBRATION.md has exactly one `approved-constants: open-weight=0.25 save-weight=0.5 cap=8` line, with `constants: revisit`.
- I checked main `application.yaml` rather than assume it: it already held 0.25 / 0.5 / 8, so the literals did not change.
- Main yaml comment changes:
  - The engagement comment now ends `Calibrated in Phase 12 (revisit: kept by the D-03 fallback on thin data); see .planning/phases/12-calibration-release/12-CALIBRATION.md.`
  - The D-14 Phase 7 path now points at `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md`.
  - The near-miss comment now ends `Phase 12 kept it at 0.35 (D-05).`
- Test yaml: the literals 0.25 / 0.5 / 8 are unchanged. The engagement comment now says the suite keeps the D-01 values for FeedbackApiIntegrationTest and PriorityApiIntegrationTest. The near-miss comment got the same change as main.
- `application-dev.yaml` is unchanged, because no approved value differs from the test yaml. `git diff main -- application-dev.yaml` is empty.
- `shippedMainYamlStarts` already asserted 0.25 / 0.5 / 8.0 and near-miss 0.35. I added a one-line comment naming the approved-constants line as its source. `defaultsStartAndBindTheD01Values` is untouched.
- Tracer gate: MyfeederPropertiesValidationTest passed 16/16 and DevProfileConfigTest passed 4/4. The yaml grep verify passed, and `Phase 12 calibrates` appears 0 times in both yaml files.

### Task 2: CLAUDE.md SC-4 docs and full suite (commit 7cedef2)
- Under Interest Ranking → Engagement learning:
  - Added the four-rule summary (thumbs first, a separate additive cap, negative-base topics skipped, SCORED articles only).
  - Rewrote the Constants bullet. It now says the values ship as 0.25 / 0.5 / 8, were kept by the D-03 fallback on thin data (13 counted engaged articles of 30, 0 topics with engagement), and are marked revisit, with a pointer to 12-CALIBRATION.md. It also covers the test yaml rationale and that the dev overlay carries no engagement keys.
- New bullet `**Engagement tuning (v0.3.1, Phase 12)**` with these parts:
  - Driver: the 6-field syntax, the TSV naming and OUT_DIR, the `invalid engagement constants` pre-connect refusal, and cap 0 for engagement off.
  - Sections: engaged, dormant / dormant-kind, floor, the three backfill sections, and eng_articles.
  - Rules: the D-02 floor, D-04 nudge, D-09, D-10 and D-05.
  - Yaml placement.
  - Outcome: ENG-F4 keep, ENG-F5 recommend with no measured benefit.
  - Proofs: InterestCalibrationReplaySqlTest and InterestCalibrationReplayRunTest.
- Tier thresholds and tuning bullet:
  - Now uses `PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP]` and the archived Phase 7 path.
  - Now reads "validated as numbers and against `Engagement.isValid`".
  - The "identical literals" sentence is replaced by the main + dev overlay / test yaml split.
- The V7 schema (Infrastructure Flyway list, Interest Ranking → Schema) and "Engagement capture (v0.3.0)" were already present, so I did not edit them.
- `MyfeederProperties.java`: only the two Javadoc lines changed, and the diff shows nothing else.
- Full backend suite: **723 tests, 0 failures, 0 errors, 2 skipped (79 classes)**, BUILD SUCCESSFUL. I confirmed that no `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE` variable was set.

## Deviations from Plan

None in substance. The plan's step 5 ("change the `assertEngagement` literals to (o, s, c)") was a no-op because the approved values equal the existing literals. A one-line comment ties the assertion to the approved-constants line, so the test change is still traceable. The plan ledger file under the git dir was not written: the worktree sandbox refused that compound git command. The base 244c90b came from the startup HEAD check, and the commit count was measured directly with `git rev-list --count 244c90b..HEAD`.

## Known Stubs

None.

## Self-Check: PASSED
- FOUND: src/main/resources/application.yaml (contains 12-CALIBRATION.md)
- FOUND: src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java (shippedMainYamlStarts)
- FOUND: CLAUDE.md (contains PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP])
- FOUND: commit 89ce2a5
- FOUND: commit 7cedef2
