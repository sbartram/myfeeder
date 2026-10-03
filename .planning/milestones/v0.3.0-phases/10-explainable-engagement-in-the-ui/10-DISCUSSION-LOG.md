# Phase 10: Explainable Engagement in the UI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-30
**Phase:** 10-explainable-engagement-in-the-ui
**Areas discussed:** "Why N?" row format, Post-engagement reaction, Vote toast & limit notes, Interests learned label

---

## "Why N?" row format

| Option | Description | Selected |
|--------|-------------|----------|
| Inline, named parts | `Rust 80% × +13.5 (+10 +2.0 votes +1.5 engaged)` | ✓ |
| Inline combined, split in tooltip | Keep `(+10 +3.5 learned)`, split on hover only | |
| Second sub-line under the row | Muted `base · votes · engaged` line | |

| Option | Description | Selected |
|--------|-------------|----------|
| Omit zero parts | Show only parts that round to a non-zero tenth | ✓ |
| Always show both once either is non-zero | `+0.0 votes +1.5 engaged` | |

| Option | Description | Selected |
|--------|-------------|----------|
| "engaged" | Matches the reading pane's Engaged line | ✓ |
| "from opens/saves" | More literal | |
| "engagement" | | |

| Option | Description | Selected |
|--------|-------------|----------|
| No cap marker in Why N? | Why N? explains points, not limits | ✓ |
| Tooltip only | | |
| Visible marker | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Tooltip lists all parts, zeros included | | ✓ |
| Tooltip mirrors the label | | |

**User's choice:** all recommended options.

---

## Post-engagement reaction

| Option | Description | Selected |
|--------|-------------|----------|
| Only when the badge moved | Refetch by id, compare score, patch + hint only if different | ✓ |
| Always, on every engagement | | |
| When the engagement was new | | |

| Option | Description | Selected |
|--------|-------------|----------|
| All five: open, star, board, Raindrop, forget | | ✓ |
| All five plus unstar | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror the vote reaction | by-id, learned, articles, other by-id off Priority | ✓ |
| Minimal: by-id + learned | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Accept IN-04, fix the Javadoc | | ✓ |
| Force reset when paging while hint lit | | |

**User's choice:** all recommended options.

---

## Vote toast & limit notes

| Option | Description | Selected |
|--------|-------------|----------|
| WR-01: binding limits first | LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE | ✓ |
| Keep D-11 order | | |

| Option | Description | Selected |
|--------|-------------|----------|
| WR-03: note "(replaces engagement)" | Appended limit/flag; removal reads "(engagement restored)" | ✓ |
| No note, document it | | |
| Show both parts | `+0.9 (+1.8 vote, −0.9 engagement)` | |

| Option | Description | Selected |
|--------|-------------|----------|
| ENGAGEMENT_CAP not in toast at all | | ✓ |
| Note it when listed for other reasons | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Binding limit wins on clash | One note per topic | ✓ |
| Both notes | | |

**User's choice:** all recommended options.

---

## Interests learned label

| Option | Description | Selected |
|--------|-------------|----------|
| Split inline, same as Why N? | `Learned +3.5 (votes +2.0, engaged +1.5)` | ✓ |
| Neutral relabel only | `Learned +3.5` | |

| Option | Description | Selected |
|--------|-------------|----------|
| "at max" on the engaged part | | ✓ |
| Tooltip only | | |

**User's choice:** all recommended options.

---

## Claude's Discretion

- The shared post-engagement helper and how the "on Priority" context reaches each hook
- Enum value vs boolean for the replaced-engagement marker
- An optional engagement at-cap boolean on TopicLearned
- Exact label spacing and punctuation

## Deferred Ideas

- ENG-F1 reading-pane status line (still deferred)
- ENG-F6 narrowed to article counts and a richer layout
- Forced Priority reset on paging (rejected)
