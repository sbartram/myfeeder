# Phase 12: Calibration & Release - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-02
**Phase:** 12-calibration-release
**Areas discussed:** Data readiness gate, Tuning targets, Backfill sim & ENG-F4/F5, Release & prod proof

---

## Todo cross-reference

| Option | Description | Selected |
|--------|-------------|----------|
| Don't fold | Raindrop resilience is unrelated to calibration (keyword-only match) | ✓ |
| Fold into Phase 12 | Ship it in v0.3.1 alongside the calibration | |

## Data readiness gate

| Option | Description | Selected |
|--------|-------------|----------|
| Split: tooling now, tune later | Build the CAL-02 sections now; a blocking checkpoint holds tune/release/docs until the data is ready | ✓ |
| Wait, then do it all | Plan now, execute everything in 2–4 weeks | |
| Tune now on thin data | Replay on ~2 days of data, likely keep defaults, ship now | |

| Option | Description | Selected |
|--------|-------------|----------|
| Count-based floor | N engaged SCORED articles touching K topics | ✓ |
| Calendar-based | A fixed date (~2026-10-21) | |
| Both, whichever later | At least 2 weeks AND the floor | |

| Option | Description | Selected |
|--------|-------------|----------|
| 30 engaged scored, ≥3 topics | Enough to see whether the cap binds | ✓ |
| 50 engaged scored, ≥4 topics | More confidence, slower | |
| You decide | Planner picks from prod counts | |

| Option | Description | Selected |
|--------|-------------|----------|
| Ship with defaults | After ~6 weeks: record thin data, keep 0.25/0.5/8, release, mark revisit | ✓ |
| Keep waiting | No release until the floor is met | |

## Tuning targets

| Option | Description | Selected |
|--------|-------------|----------|
| Nudge, not reshuffle | High-tier share moves ≤ ~5 pts on vs off; no topic pinned at cap unless heavily engaged | ✓ |
| Noticeable lift | Visible neutral→high moves; cap toward ~12 | |
| Keep defaults unless broken | Change only on a clear problem | |

| Option | Description | Selected |
|--------|-------------|----------|
| Engagement only | Tune open/save/cap; report tiers on/off but don't move 70/22; near-miss stays 0.35 | ✓ |
| Engagement + tiers | Re-check the D-10 bands and adjust the tiers | |
| Engagement + tiers + near-miss | Also tune 0.35 | |

| Option | Description | Selected |
|--------|-------------|----------|
| Small grid + off | cap 0, defaults, cap {4,8,12} × save {0.5,0.75}, open = save/2 | ✓ |
| Wider grid | 3×3×4 | |
| You decide | Phase 7-style passes | |

| Option | Description | Selected |
|--------|-------------|----------|
| You decide | Driver CLI shape is the planner's call (keep validate-before-connect and the WR-02 range check) | ✓ |
| Extend candidate syntax | PP:HIGH:NEUTRAL:OPEN:SAVE:CAP | |

## Backfill sim & ENG-F4/F5

| Option | Description | Selected |
|--------|-------------|----------|
| Starred + any board, as saves | Unioned with real rows; vote exclusion and SCORED-only still apply | ✓ |
| Starred only | Boards may be a queue | |
| Starred + boards except Read Later | Exclude Read Later | |

| Option | Description | Selected |
|--------|-------------|----------|
| Separate labeled section | `backfill-sim` section; drift guard proves only the engaged CTE differs | ✓ |
| You decide | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Dormant share rule (ENG-F4) | Revisit if dormant ≥ 25% of engaged articles | ✓ |
| Always just record | Judgment call | |

| Option | Description | Selected |
|--------|-------------|----------|
| Impact rule (ENG-F5) | Recommend backfill only if the sim stays within the nudge target | ✓ |
| Always just record | Judgment call | |

## Release & prod proof

| Option | Description | Selected |
|--------|-------------|----------|
| Merge Phase 11 now, branch 12 from main | --no-ff merge before Phase 12 execution | ✓ |
| Stack Phase 12 on the Phase 11 branch | Merge everything at release | |

| Option | Description | Selected |
|--------|-------------|----------|
| Blocking approval checkpoint | Dry run, then user approval before tag/push/deploy (08-05 pattern) | ✓ |
| Autonomous after the gate | | |

| Option | Description | Selected |
|--------|-------------|----------|
| API + replay cross-check | 3–5 engaged ids: API badge = replay badge; nonzero engagementWeight in Why N?; clean logs | ✓ |
| API check + manual UI look | Adds a human UAT item | |

## Claude's Discretion

- Driver CLI shape for engagement candidates (and closing Phase 9 WR-02)
- SQL/labels/columns of the new replay sections
- Calibration note layout (mirrors 07-CALIBRATION.md)
- CLAUDE.md SC-4 audit

## Deferred Ideas

- Building ENG-F4 / ENG-F5 (decide only in this phase)
- Re-tuning tiers or near-miss with engagement on
- Raindrop resilience todo (separate /gsd-quick)
