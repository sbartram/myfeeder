# Phase 3: Interest Model, Schema & Rubric Editor - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-23
**Phase:** 3-Interest Model, Schema & Rubric Editor
**Areas discussed:** Thumbs-down topic picks, Status endpoint gap, Editor placement & saving, Topic preview UX

---

## Thumbs-down topic picks

**How should V6 store a narrowed thumbs-down?**

| Option | Description | Selected |
|--------|-------------|----------|
| Child rows only when narrowed | `article_feedback(article_id PK, vote)` + `article_feedback_topic(article_id, topic_id)`, written only when narrowing; none = all matched topics. Works for votes before scoring. | ✓ |
| Always list topics per vote | Every vote writes child rows; can't be written for unscored articles; freezes matched set. | |
| Per-topic vote rows, no parent | `article_feedback(article_id, topic_id, vote)` only; per-article vote becomes an aggregate. | |

**Deleted sole pick silently widens the penalty — how to prevent?**

| Option | Description | Selected |
|--------|-------------|----------|
| Add a 'narrowed' flag | `topics_narrowed BOOLEAN NOT NULL DEFAULT false`; when true only child rows count, zero rows = penalize nothing. | ✓ |
| Accept the widening | Keep schema minimal; rare case. | |
| Delete the vote with it | Remove the whole vote when its last pick is deleted. | |

**Limit picks to thumbs-down?**

| Option | Description | Selected |
|--------|-------------|----------|
| Either direction in schema | No schema coupling; UI narrows on thumbs-down only. | ✓ |
| Thumbs-down only, enforced | Stricter; a later change would need V7. | |

**User's choice:** All recommended options.

---

## Status endpoint gap

Context: Phase 2 deferred `/api/interest/status` to Phase 4 (`02-CONTEXT.md`), and no route exists, but INT-06 needs `configured` in Phase 3.

| Question | Options | Selected |
|----------|---------|----------|
| How does the settings UI learn "not configured"? | Slim `/api/interest/status` now (configured + breaker), grown by Phase 4 / Flag on profile response / `/status` with configured only | Slim `/api/interest/status` now |
| Where is cold start computed? | Server flag on `/status` (shared with SCOR-06 gate) / Frontend derives it | Server flag on `/status` |
| Keyless editor capability? | Edit everything, preview off / Read-only until configured | Edit everything, preview off |

---

## Editor placement & saving

| Question | Options | Selected |
|----------|---------|----------|
| Where does the editor live? | Own "Interests" dialog / Routed `/interests` view / Section inside SettingsDialog | Own "Interests" dialog |
| Save model? | Per item / Edit all, save once | Per item |
| Negation warning (INT-03)? | Inline, non-blocking / Warn and confirm on save / Warn + one-click fix | Inline, non-blocking |
| Weight input? | Slider + number box / Number input only / Preset steps | Slider + number box |
| Profile writing guidance (INT-01)? | Short tips + example / Collapsible "How to write this" / Placeholder only | Short tips + example |

---

## Topic preview UX

| Question | Options | Selected |
|----------|---------|----------|
| What does preview ask Jev? | Just the draft topic (same builders as scorer) / Full rubric + draft | Just the draft topic |
| How does the result read? | Numbers, scoring math (hinge × weight = pts) / Simple verdict | Numbers, scoring math |
| Availability? | Every row, disabled with reason / Draft row only | Every row, disabled with reason |

---

## Claude's Discretion

- Topic short name vs description-only
- Base weight column type (INTEGER vs DOUBLE) within the −50..50 CHECK
- Endpoint paths and payloads, error mapping
- Question wording and state truncation limits (research defaults, then iterated by the calibration spike)
- Calibration spike mechanics (gated, never in default test run)
- Frontend file layering, query keys
- Validation messages for limit violations

## Deferred Ideas

- ROADMAP.md §Phase 3 note correction (it claims `/api/interest/status` was built in Phase 2)
- Phase 6: learned CTE must honor `topics_narrowed` + `article_feedback_topic`
- Phase 4: SCOR-06 gate reuses the cold-start predicate; `/status` gains JEV-05 counts
