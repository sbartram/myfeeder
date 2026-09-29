---
phase: 07-rollout-calibration
plan: 08
subsystem: infra
tags: [calibration, interest-scoring, tiers, blend, psql, read-only-replay, ops]

requires:
  - phase: 07-rollout-calibration
    provides: "07-04 read-only replay driver scripts/interest-calibration-replay.sh; 07-07 launch backfill drained (07-BACKFILL.md Overall PASS, 192 SCORED)"
provides:
  - "07-CALIBRATION.md: baseline, 22-candidate grid, stability cross-check, learned summary, proposal, top/bottom 20 titles, Verdict and Approval"
  - "User-approved constants for 07-09: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20"
affects: [07-09, OPS-02, application.yaml, application-dev.yaml]

actuals:
  tokens: 3420
  tasks: 3
  commits: 2
plan_head_before: 007bf81ff074f72aa4ae49f516e8913030eea6bc
plan_head_after: 72dd4f7584b975993c24b1b804dc07f950c9e41c

tech-stack:
  added: []
  patterns:
    - "Replay-vs-app cross-check before calibrating: the replayed badge must equal GET /api/articles/{id} interestScore for the top ids"
    - "Threshold-only calibration at profile-points 100, so the Priority order never changes"

key-files:
  created:
    - .planning/phases/07-rollout-calibration/07-CALIBRATION.md
  modified: []

key-decisions:
  - "User approved 100/70/22 (verbatim: approve), no title redaction; only tiers.neutral moves 40 to 22, so the Priority order is unchanged"
  - "D-10 met by the approved constants: high 14.1%, neutral 34.9%, low 51.0% of 192 scored unread"
  - "learn-rate 2 and learned-cap 20 unchanged (D-12): 0 votes, learned 0 for all 7 topics, none at the cap"
  - "No profile-points above 100: pp 125/150 saturate 18/30 badges at 100 without improving neutral"

patterns-established:
  - "Calibration doc holds titles, article ids, topic_<id> keys and numbers only; checked with grep -F against the live profile text and topic descriptions and the PG password"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "Replay proven equal to the app in prod at the live constants 100/70/40"
    requirement: OPS-02
    verification:
      - kind: other
        ref: "$HOME/.cache/myfeeder-phase07/replay/crosscheck-baseline.json (checked 5, matched 5: ids 25988, 26032, 25873, 26016, 26025)"
        status: pass
    human_judgment: false
  - id: D2
    description: "07-CALIBRATION.md with baseline, 22 candidates, stability cross-check, learned summary, proposal, top/bottom 20 and Verdict; privacy clean"
    requirement: OPS-02
    verification:
      - kind: other
        ref: "Task 2 verify: section/proposed-constants/TSV check and grep -F privacy check (re-run after Task 3, exit 0)"
        status: pass
    human_judgment: false
  - id: D3
    description: "User approval of the constants recorded verbatim with the machine-readable approved-constants line"
    requirement: OPS-02
    verification:
      - kind: other
        ref: "grep '^approved-constants: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20$' 07-CALIBRATION.md; replay-pp100-hi70-ne22.tsv exists"
        status: pass
    human_judgment: true
    rationale: "D-11: whether the top 20 titles deserve 'read this' is the user's judgment; the user gave it at the blocking-human checkpoint (approve)"

duration: 10min
completed: 2026-09-29
status: complete
---

# Phase 7 Plan 08: Blend and Tier Calibration Summary

**Read-only prod replay (5/5 match with the app) over 22 candidates; the user approved 100/70/22, which lowers only tiers.neutral from 40 to 22 and meets D-10 (high 14.1%, neutral 34.9%) with the Priority order unchanged**

## Performance

- **Duration:** about 10 min wall clock, including the checkpoint wait
- **Started:** 2026-09-28T23:56:30Z
- **Completed:** 2026-09-29T00:06Z
- **Tasks:** 3 (tracer, grid + doc, approval checkpoint)
- **Files modified:** 1 (07-CALIBRATION.md)

## Accomplishments

- Baseline replay at the live constants 100/70/40 ran read-only against prod and matched the app's `interestScore` for 5 of 5 top article ids (25988, 26032, 25873, 26016, 26025).
- Baseline distribution over 192 scored unread: high 14.1%, neutral 20.3%, low 65.6%; P50 20, P80 63, P90 79; at_zero 58 (30.2%); at_100 1; 44 ties at the minimum raw score. The low-end compression the Phase 3 spike predicted (Pitfall 8) did not appear.
- 22 candidates replayed (pass A 3, pass B 9, extra pp-100 threshold pairs 10). Ten meet both D-10 bands (nine at pp 100, one at pp 125). 100/70/22 is the pp-100 candidate closest to high 15% and neutral 35%.
- The user approved the proposal at the blocking-human checkpoint. Answer, verbatim: `approve`. No title redaction was requested.
- `## Approval` in 07-CALIBRATION.md records the answer, the date and `approved-constants: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20`, which 07-09 reads as its precondition.

## Task Commits

1. **Task 1 (tracer): baseline replay + app cross-check.** No commit (read-only; output in `$HOME/.cache/myfeeder-phase07/replay/`).
2. **Task 2: candidate grid, D-10 proposal, 07-CALIBRATION.md.** `9d4db3c` (docs)
3. **Task 3: approval checkpoint (decision, blocking-human).** `72dd4f7` (docs), which records the verbatim `approve`.

**Plan metadata:** in the final docs commit (SUMMARY, STATE, ROADMAP, REQUIREMENTS)

## Files Created/Modified

- `.planning/phases/07-rollout-calibration/07-CALIBRATION.md`: sections Run, Baseline, Candidates, Stability cross-check, Learned model, Proposal (`proposed-constants:`), Top 20, Bottom 20, Verdict and Approval (`approved-constants:`).

## Decisions Made

- **Approved constants: 100/70/22, learn-rate 2, learned-cap 20.** Only the neutral threshold moves, so every badge value and the Priority order stay the same. Only the tier colour of the badges from 22 to 39 changes (28 articles move from low to neutral).
- **Profile-points stays 100.** At pp 125 and 150, 18 and 30 badges saturate at 100 and neutral does not improve.
- **Learned model unchanged (D-12).** With 0 votes the model is inert and no topic is at the cap, so nothing suggests a problem.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] No P55 column for the pass B (P85, P55) pairs**
- **Found during:** Task 2
- **Issue:** The replay summary reports P50 and P60 but no P55, which pass B needs for its (P85, P55) pair.
- **Fix:** Used the integer midpoint of P50 and P60 as P55, and stated this in the Candidates section. The previous executor also added 10 extra pp-100 threshold pairs around the band edges (the plan allows "any pair the numbers clearly call for"), for 22 candidates in total.
- **Files modified:** 07-CALIBRATION.md
- **Verification:** Every candidate row has its own replay TSV, and the proposal's TSV exists.
- **Committed in:** 9d4db3c

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** None on the result. The midpoint only picks grid points; every reported number comes from a real replay.

## Issues Encountered

None. All prod DB reads went through `scripts/interest-calibration-replay.sh`, which sets `default_transaction_read_only=on`. The only API calls were GETs: 5 articles for the cross-check, plus the profile and topics for the privacy check. The throwaway cross-check script lived only in the session scratchpad. The privacy check (no profile text, no topic description, no PG password) passed in Task 2 and again after the Approval edit.

## User Setup Required

None. `MYFEEDER_PG_PASSWORD` was already set in the user's shell (plan `user_setup`).

## Next Phase Readiness

- 07-09 can run. Its precondition is the `approved-constants:` line in 07-CALIBRATION.md. It writes `tiers.neutral: 22` (with profile-points 100 and tiers.high 70 unchanged) to main `application.yaml` and to `src/test/resources/application-dev.yaml`, and keeps the test `application.yaml` at 100/70/40.
- 07-07 found no D-08 throttle override, so 07-09 has none to remove.
- Caveat (Pitfall 9): calibration ran on one snapshot minutes after the drain, when no scored article had been read yet. As the user reads, the unread high share will fall below 14.1%. The 14-day window figures in 07-CALIBRATION.md are the reference for a later re-check.
- 07-CALIBRATION.md, titles included, becomes public when it merges to main. The user was told this at the checkpoint and did not ask for redaction.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-29*

## Self-Check: PASSED
