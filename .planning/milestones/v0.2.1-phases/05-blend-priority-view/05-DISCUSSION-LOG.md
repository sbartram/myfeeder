# Phase 5: Blend & Priority View - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-25
**Phase:** 05-blend-priority-view
**Areas discussed:** "Why N?" breakdown, Triage stability & refresh, Priority entry & states, Badge reach & look

---

## "Why N?" breakdown

| Option | Description | Selected |
|--------|-------------|----------|
| Reading pane, collapsible | "Why N?" row under the title; `i` toggles; list badge number only | ✓ |
| Reading pane + list tooltip | Also a compact hover version in the list | |
| Popover from the badge | Floating panel on click anywhere | |

| Option | Description | Selected |
|--------|-------------|----------|
| Integer rows + explicit cap line | Largest-remainder rows sum to round(raw); "Total 112 → capped at 100" | ✓ |
| One decimal per row | Rows sum to the raw value, not the badge | |
| You decide | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Hide, with "N topics didn't match" toggle | Contributing rows only by default | ✓ |
| Hide entirely | | |
| Show all topics | | |

Extras (multi-select): Profile level label ✓; Low-confidence hint ✗; Stale-score notes ✗; Chips open Interests ✗

| Option | Description | Selected |
|--------|-------------|----------|
| Always visible next to the badge | Sign-colored, non-clickable chips even when collapsed | ✓ |
| Only inside the expanded breakdown | | |
| In the list row too | | |

**User's choice:** Reading-pane-only breakdown, exact integer math with a visible cap, level label, chips always visible.

---

## Triage stability & refresh

| Option | Description | Selected |
|--------|-------------|----------|
| Stay in place, dimmed | All read rows keep position until refresh or re-entry | ✓ |
| Only the selected one stays | Today's behavior | |

| Option | Description | Selected |
|--------|-------------|----------|
| Refresh button + "ranking changed" hint | Never auto re-sorts | ✓ |
| Plain refresh button only | | |
| Auto re-rank when Interests closes | | |

| Option | Description | Selected |
|--------|-------------|----------|
| No re-rank on refocus, only show the hint | `refetchOnWindowFocus` off | ✓ |
| Yes, re-rank on refocus | Research R4's suggestion | |

| Option | Description | Selected |
|--------|-------------|----------|
| Same "Load more" button | Consistent with other lists | ✓ |
| Auto-load on scroll | | |

| Option | Description | Selected |
|--------|-------------|----------|
| `j` at end: fetch the next page, then advance (Priority only) | | ✓ |
| Stop, like today | | |

**Notes:** Claude first said that `j` already fetches the next page. The code showed it doesn't, so this was re-asked as an explicit choice.

---

## Priority entry & states

| Option | Description | Selected |
|--------|-------------|----------|
| First, above All Articles, no count | | ✓ |
| Between All and Starred, no count | | |
| First, with total unread count | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Show it; the view explains (unconfigured) | | ✓ |
| Hide the entry entirely | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Slim banner above the list | One banner at a time with precedence; the list always renders | ✓ |
| Cold start/unconfigured replace the list | | |

| Option | Description | Selected |
|--------|-------------|----------|
| One "Not yet scored" segment | Includes the >14-day never-scored articles | ✓ |
| Split "Not yet scored" vs "Too old to score" | | |
| Exclude them from Priority | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Hide "Mark all read" in Priority | | ✓ |
| Keep it with a confirm | | |

| Option | Description | Selected |
|--------|-------------|----------|
| No failed count in the banner | | ✓ |
| Append "· N failed" | | |

**Notes:** The ★ in the placement preview was a placeholder. Priority's icon must not be a star (it clashes with Starred).

---

## Badge reach & look

| Option | Description | Selected |
|--------|-------------|----------|
| Every list, read articles included | `interestScore` enrichment on every article response | ✓ |
| Priority list + reading pane only | | |
| Every list, unread only | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Leading, before the title | Fixed-width pill; empty slot in Priority | ✓ |
| In the meta line | | |
| Trailing, right-aligned | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse existing vars | accent filled / text-secondary outlined / text-muted | ✓ |
| New `--interest-*` vars | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Badge click selects the row, nothing more | | ✓ |
| Selects + expands "Why N?" | | |

---

## Claude's Discretion

- Endpoint path/shape, class split, and how breakdown data reaches the pane
- "Ranking changed" detection, and `/status` polling while Priority is open
- Aligning MainLayout's keyboard list with the Priority query
- The 5 short profile level labels and banner copy
- Empty badge slot in non-Priority lists
- Auto-mark-read behavior in Priority
- Index support for the Priority query

## Deferred Ideas

- Low-confidence hint and stale-score notes in the breakdown
- Clickable chips that open the Interests dialog
- A "Too old to score" segment
- A failed count in the banner
