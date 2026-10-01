# Phase 11: Gap Discovery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-01
**Phase:** 11-gap-discovery
**Areas discussed:** List placement & rows, Ordering, window & cap, What counts as a gap, Create & dismiss flow

---

## List placement & rows

| Question | Options | Selected |
|----------|---------|----------|
| Placement | Below Topics / Above Topics / Collapsed below Topics | Below Topics |
| Row content | Feed name / Engagement kind / Engagement date / Score badge (multi) | Feed name, Score badge |
| Title link | Plain text / Opens original in new tab / Selects in reading pane | Plain text |
| Empty state | Hide the section / One-line note | Hide the section |
| Cold start | Show whenever rows exist / Hide in cold start | Show whenever rows exist |

## Ordering, window & cap

| Question | Options | Selected |
|----------|---------|----------|
| Order | Lowest score first / Strongest engagement then newest / Newest first | Lowest score first |
| Which score | Badge score / Raw profile noul | Badge score (ties → newest engagement) |
| Window | Last 30 days / All time / Last 14 days | Last 30 days |
| Cap | 10 / 20 / 5 | 10 |
| Overflow | Show "(10 of 23)" / No count | Show count |

## What counts as a gap

| Question | Options | Selected |
|----------|---------|----------|
| Near-miss | 0.35 yaml constant / 0.35 hard-coded / 0.25 | 0.35 yaml constant |
| Negative topics | Any match counts as covered / Gap if only negative matched | Any match counts as covered |
| Votes | Exclude any voted article / Exclude 👎 only | Exclude any voted article |
| Min. signal | Any engagement incl. opens / Saves only | Any engagement |
| No topic rows | Gap (best noul 0) / Require a judged topic | Gap |

## Create & dismiss flow

| Question | Options | Selected |
|----------|---------|----------|
| Mark created | Atomically on topic save / Separate call after save | Atomically on topic save |
| Row on click | Stays as "Draft added" until saved / Hidden immediately | Stays until saved |
| Dismiss | Immediate, no confirm or undo / Immediate + Undo toast | Immediate |
| Notice link | FeedbackNotice passes sourceArticleId too / Suggestions only | Yes |
| Draft weight | +20 / +10 | +20 |
| At max | Disable Create with tooltip / Hide Create | Disable with tooltip |

## Claude's Discretion

- Route shapes and response envelope, service/query placement, conflict handling for an existing dismissal at topic save, the query key and its invalidation set, refetch-on-open, and row styling.

## Deferred Ideas

- ENG-F3 notice after engaging (roadmap-deferred); un-dismiss/undo; engagement kind and date on rows.
