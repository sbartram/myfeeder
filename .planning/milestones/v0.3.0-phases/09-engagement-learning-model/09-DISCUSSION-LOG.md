# Phase 9: Engagement Learning Model - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-30
**Phase:** 09-engagement-learning-model
**Areas discussed:** Starting values & zero rule, Engagement arithmetic, How far the split reaches, Latency budget & ship

---

## Starting values & zero rule

| Option | Description | Selected |
|--------|-------------|----------|
| Conservative on | open 0.25, save 0.5, cap 8 | ✓ |
| Zero (disabled) | prod identical to v0.2.1 until Phase 12 | |
| Conservative, cap 10 | upper end of research range | |

| Option | Description | Selected |
|--------|-------------|----------|
| cap = 0 disables | cap 0 off regardless; else 0 ≤ open < save and 0 < cap < learned-cap | ✓ |
| All-zero or strict | all three zero, or strict rule | |
| Loose: save ≥ open | allow equality | |

| Option | Description | Selected |
|--------|-------------|----------|
| Strictly below 1 | an explicit 👍 always outweighs a save | ✓ |
| Up to 1 | a save may equal a thumbs-up | |

| Option | Description | Selected |
|--------|-------------|----------|
| Same as main | test yaml 0.25/0.5/8 | ✓ |
| Zero in test yaml | engagement off by default in tests | |

**User's choice:** all recommended options.

---

## Engagement arithmetic

| Option | Description | Selected |
|--------|-------------|----------|
| Like a vote, via learnRate | eng_raw = learnRate × Σ strength × hinge | ✓ |
| Own scale, no learnRate | eng_raw = Σ strength × hinge | |

| Option | Description | Selected |
|--------|-------------|----------|
| All, like votes | no age window | ✓ |
| Only inside 14-day window | learning fades with article age | |

| Option | Description | Selected |
|--------|-------------|----------|
| Same hinge as votes | max(0,(noul−0.5)×2) | ✓ |
| Matched topics, flat | full strength to every matched topic | |

| Option | Description | Selected |
|--------|-------------|----------|
| A: One clamp on the full sum | w = clamp(base + thumbs + eng); thumbs dominate | ✓ |
| B: Sequential clamps | w = clamp(clamp(base + thumbs) + eng) | |

**User's choice:** all recommended options.

---

## How far the split reaches

| Option | Description | Selected |
|--------|-------------|----------|
| Append split fields now | thumbs/engagement fields on DTOs in Phase 9 | ✓ |
| Combined only until Phase 10 | math only in Phase 9 | |

| Option | Description | Selected |
|--------|-------------|----------|
| Append ENGAGEMENT_CAP | new LearnedLimit value with precedence | ✓ |
| No new value | engagement cap never named | |
| You decide | planner's call | |

**User's choice:** all recommended options.

---

## Latency budget & ship

| Option | Description | Selected |
|--------|-------------|----------|
| Record + generous guard | record vs baseline + EXPLAIN; assert < ~3× only | ✓ |
| Tight automated budget | ≤ 1.25× baseline assertion | |
| One-off measurement only | no assertion | |

| Option | Description | Selected |
|--------|-------------|----------|
| No release, lands on main | prod stays on v0.3.0 until Phase 12's v0.3.1 | ✓ |
| Patch release after Phase 9 | deploy the learning now | |

**User's choice:** all recommended options.

---

## Claude's Discretion

- yaml key names, validation wiring, CTE and DTO field names, grid test ranges, replay `-v` variable plumbing.

## Deferred Ideas

- ENG-F6 learned-line split UI, ENG-F8 decay, ENGAGEMENT_CAP wording (Phase 10).
- Todo "Tune Raindrop resilience" reviewed, not folded.
