---
phase: 12-calibration-release
plan: 03
subsystem: calibration
tags: [calibration, replay, engagement, interest-ranking, prod, read-only]
status: complete

requires:
  - phase: 12-calibration-release
    provides: "12-02: dormant, dormant-kind, floor, backfill-pool, backfill-summary and backfill-learned replay sections, eng_articles"
provides:
  - "12-CALIBRATION.md: prod replay of the D-06 grid, data floor (D-03 fallback), D-04 verdict, ENG-F4 and ENG-F5 calls"
  - "Machine-readable approval: approved-constants: open-weight=0.25 save-weight=0.5 cap=8, constants: revisit, eng-f4: keep, eng-f5: recommend"
affects: [12-04, 12-05]

actuals:
  tokens: 2368
  tasks: 3
  commits: 1
plan_head_before: fe54be9ab4b01b89e3fc36a6ee02f4cb70bad8ee
plan_head_after: 7426d907bea0d94f4534982552937bf10ab4b8d2

tech-stack:
  added: []
  patterns:
    - "Privacy checks that need the password run as a scratchpad script file, printing line numbers and counts only"

key-files:
  created:
    - .planning/phases/12-calibration-release/12-CALIBRATION.md
  modified: []

key-decisions:
  - "D-03 fallback (user answer `fallback`, 2026-10-02): keep 0.25 / 0.5 / 8 and mark the engagement constants revisit; the floor was 13/30 counted and 0/3 topics"
  - "ENG-F4 keep: 4 x dormant (1) = 4 < engaged 16 (6.3%)"
  - "ENG-F5 recommend at T=10, base=off, recorded as 'no harm today' because the backfill adds 0 counted articles"

patterns-established: []

requirements-completed: [CAL-02, CAL-03]

duration: ~25min (continuation; Task 1 ran in the previous agent)
completed: 2026-10-02
---

# Phase 12 Plan 03: Engagement Calibration Summary

Prod replay of the 7-run engagement grid matched 0.3.0 at cap 0 on 10 of 10 ids, found every candidate identical to engagement-off (13 counted articles, all off-topic), and recorded the user's D-03 fallback: ship 0.25 / 0.5 / 8 marked revisit, ENG-F4 keep, ENG-F5 recommend.

## Performance

- **Completed:** 2026-10-02T21:58Z
- **Tasks:** 3 (Task 1 tracer and Task 2 checkpoint in the previous agent; Task 3 here)
- **Files created:** 1

## Accomplishments

- Task 1 (previous agent, no commit by design): read-only prod replay of the D-06 grid at 100 / 70 / 22 into `$HOME/.cache/myfeeder-phase12/replay/20261002T213738Z/`; cross-check PASS (5 required plus 5 unsaturated ids); gate `unmet-fallback` (counted 13, topics 0).
- Task 2: the user answered `fallback` at the blocking-human checkpoint; the answer was appended to `gate-history.txt` as `answer (2026-10-02): fallback`, valid for `gate: unmet-fallback`.
- Task 3: wrote `12-CALIBRATION.md` with all ten headings. Data floor states the data was thin and that D-03 applied with the 2026-10-02 date amendment, and lists the earlier hold (19:49Z run) and the 21:08Z / 21:21Z prechecks. It ends with the four machine-readable lines.

## Task Commits

1. **Task 1: prod replay, cross-check, floor gate, proposal** - none (evidence only, outside the repo)
2. **Task 2: blocking data-floor gate** - none (checkpoint; answer recorded in gate-history.txt and the note)
3. **Task 3: write 12-CALIBRATION.md** - `7426d90` (docs)

## Verification

- Privacy: 37 titles (>= 12 chars, top and bottom rows of all 7 TSVs) and 10 topic names (whole word, case-insensitive) checked against the note: no hit. Password: no hit. `titles.txt` and `topic-names.txt` are mode 600 in the mode-700 evidence dir.
- Task 3's three automated checks: all exit 0 (headings and status lines; one well-formed approved-constants line that passes the app's rule; privacy and committed).
- Nothing was written to prod (read-only sessions and GETs only). STATE.md and ROADMAP.md untouched.

## Deviations from Plan

None in substance. Two execution notes:
- The worktree command guard refuses compound git commands and commands that pass the password on the command line, so the privacy and verify checks ran as scratchpad script files (never printing the value), and git commands ran one per call.
- The ENG-F5 call is `recommend` by the D-10 rule, but the backfill pool adds 0 counted articles, so the note records it as "no harm today", not a measured benefit, and asks for a re-check when the constants are revisited.

## Known Stubs

None.

## Next Phase Readiness

- 12-04 can read `approved-constants: open-weight=0.25 save-weight=0.5 cap=8`. The yaml already holds these literals, so 12-04 should find nothing to change in the values.
- The constants are marked revisit: once topics created from suggestions (v0.3.1) collect engagement, re-run the replay and re-decide.

## Self-Check: PASSED

- FOUND: .planning/phases/12-calibration-release/12-CALIBRATION.md
- FOUND: commit 7426d90
- FOUND: `answer (2026-10-02): fallback` in gate-history.txt
