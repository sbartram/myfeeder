# Phase 8: Engagement Capture - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-29
**Phase:** 08-engagement-capture
**Areas discussed:** Forget control UX, Save-path failures, Open-capture edges, V7 dismissal table

---

## Forget control UX

| Question | Options | Selected |
|----------|---------|----------|
| Placement | Near score row / Toolbar button / Under the FeedbackBar | Near score row ✓ |
| Wording | Kinds + forget / Plain "Forget engagement" | Kinds + forget ✓ |
| On click | Immediate, no confirm / Undo toast / Confirm dialog | Immediate ✓ |
| GET payload | Kind list / Boolean only | Kind list ✓ |
| Refresh after engage | Refetch by id / No refresh | Refetch by id ✓ |
| Shortcut | None / Add one | None ✓ |

**User's choice:** all recommended options.

---

## Save-path failures

| Question | Options | Selected |
|----------|---------|----------|
| Star/board insert failure | Best-effort, log WARN / Same transaction | Best-effort ✓ |
| Raindrop insert failure after the bookmark exists | Best-effort, log WARN / Surface an error | Best-effort ✓ |
| Re-add to the same board | Record anyway / Only on a real add | Record anyway ✓ |

**User's choice:** all recommended options.

---

## Open-capture edges

| Question | Options | Selected |
|----------|---------|----------|
| Reader-view fallback Open Original | Yes, same helper / No | Yes ✓ |
| Client dedupe of repeat opens | No dedupe / Skip if already engaged | No dedupe ✓ |
| Open on a missing article | 404, silently ignored / Always 204 | 404 ignored ✓ |

**User's choice:** all recommended options.

---

## V7 dismissal table

| Question | Options | Selected |
|----------|---------|----------|
| Shape | One table + reason / Dismissal only / Record topic id too | One table + reason ✓ |
| Topic deleted later | Stays handled / Resurface | Stays handled ✓ |

**User's choice:** all recommended options.

---

## Claude's Discretion

- Table, column and index names; `EngagementKind` layout; the Forget route (default `DELETE /api/articles/{id}/engagement`); control styling and kind labels; the release checkpoint and prod verification steps.

## Deferred Ideas

- ENG-F1 status line remains future work.
- Raindrop resilience todo reviewed and not folded, because it is out of milestone scope.
