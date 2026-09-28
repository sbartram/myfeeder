# Phase 6: Thumbs Feedback - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-26
**Phase:** 06-thumbs-feedback
**Areas discussed:** Badge ripple after a vote, Effect message & capped votes, Thumbs-down topic picker, No-match → create topic

---

## Badge ripple after a vote

| Question | Options | Selected |
|----------|---------|----------|
| Outside Priority, which badges update? | All visible badges (invalidate) / Only the voted article | All visible badges |
| Inside Priority, other rows? | Only voted row + hint / All loaded rows re-badge / Nothing changes | Only voted row + hint |
| Button placement | Reading pane only / Pane + list-row hover | Reading pane only |
| List vote indicator | None / Small glyph | None |
| Vote on unscored | Stored, counts later / Disabled until scored | Stored, counts later |
| Side effects | None / Thumbs-down marks read | None |

## Effect message & capped votes

| Question | Options | Selected |
|----------|---------|----------|
| Number shown | Actual before/after change / Nominal η×m | Actual change |
| Precision | One decimal / Whole points, hide zeros | One decimal |
| Surface | Success toast / Inline under toolbar | Success toast |
| Remove/flip message | Net change same format / Short confirmation | Net change |
| Why N? weight | Effective with learned part / Effective only | Effective with learned part |
| Toast size | Top 3 + "+N more" / All topics | Top 3 + "+N more" |

## Thumbs-down topic picker

| Question | Options | Selected |
|----------|---------|----------|
| Trigger | Vote first, narrow optionally / Picker before saving | Vote first, narrow optionally |
| Picker UI | Popover under 👎 / Small dialog | Popover |
| Revisit | Pane shows it, re-openable / Only at vote time | Pane shows it, re-openable |
| Empty pick | Apply disabled / Allowed | Apply disabled |
| Narrow entry | Pane control + Shift+D / Toast action | Pane control + Shift+D |
| Flip narrowed ↓ to ↑ | Cleared / Kept | Cleared |

## No-match → create topic

| Question | Options | Selected |
|----------|---------|----------|
| Store vote? | Yes / No | Yes |
| Entry | Inline pane prompt / Toast action | Inline pane prompt |
| Create action | InterestsDialog with draft / Inline form | InterestsDialog with draft |
| Re-judge article | No / Re-score this article | No |

## Claude's Discretion

- The topic editor's base vs learned display (FDBK-07): the user chose to go to context without discussing it
- Endpoint shape, the feedback field on responses, picker "matched" definition, shortcut overlay copy, draft prefill channel

## Deferred Ideas

- Reset learned adjustment (FDBK-V2-01), list-row vote indicator/hover thumbs, single-article re-judge
