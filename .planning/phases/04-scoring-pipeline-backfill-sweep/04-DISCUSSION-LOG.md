# Phase 4: Scoring Pipeline & Backfill Sweep - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-23
**Phase:** 04-scoring-pipeline-backfill-sweep
**Areas discussed:** Re-score unread UX, Scorer timeout & breaker, Sweep pacing & failures, Carried review debt

---

## Re-score unread UX

| Option | Description | Selected |
|--------|-------------|----------|
| Interests dialog footer | Next to the "apply to newly arriving articles" note | ✓ |
| Settings dialog row | Separate row beside the Interests entry | |
| Both | Dialog now, Priority view later | |

| Option | Description | Selected |
|--------|-------------|----------|
| Count + cost-free wording | Inline confirm, count from server GET | ✓ |
| Count + estimated time | Adds an ETA from concurrency × latency | |
| Count only, native confirm | `window.confirm` | |

| Option | Description | Selected |
|--------|-------------|----------|
| SCORED + FAILED, not SKIPPED | Doubles as the retry-failed recovery | ✓ |
| Everything in window | Includes SKIPPED | |
| SCORED only | Failed needs a separate action | |

| Option | Description | Selected |
|--------|-------------|----------|
| Disable with reason | "Save your changes first" | ✓ |
| Allow, with warning | Confirm notes unsaved changes are ignored | |
| You decide | | |

**User's choice:** Recommended options throughout.

---

## Scorer timeout & breaker

| Option | Description | Selected |
|--------|-------------|----------|
| Raise shared timeout to ~30s | One client, one setting | ✓ |
| Separate scorer timeout | Keep 5s for preview | |
| Raise to 15s | Tighter ceiling | |

| Option | Description | Selected |
|--------|-------------|----------|
| Raise slow-call threshold to ~15s | Stays a degradation signal | ✓ |
| Remove slow-call detection | Failures only | |
| Keep 3s | Accept breaker opening under normal load | |

| Option | Description | Selected |
|--------|-------------|----------|
| No special auth surfacing | breakerState is enough | ✓ |
| Add lastError to /status | Fixed-text category | |

**User's choice:** Recommended options throughout.

---

## Sweep pacing & failures

| Option | Description | Selected |
|--------|-------------|----------|
| 1 thread, configurable | Safer with unobserved 429 behavior | ✓ |
| 2 threads | Research default | |

| Option | Description | Selected |
|--------|-------------|----------|
| Every 2 min, up to free queue capacity | Research default | ✓ |
| Every 2 min, capped batch | e.g. 100 | |
| Also trigger immediately on save/re-score | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Exhausted only (attempts ≥ 3) | Retrying rows count as waiting | ✓ |
| Any FAILED row | | |
| Both as separate fields | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Window-scoped counts | Same eligibility predicate | ✓ |
| Failed unscoped | | |

**User's choice:** Recommended options throughout.

---

## Carried review debt

| Option | Description | Selected |
|--------|-------------|----------|
| Scorer-path fixes (CR-01, 02 WR-01, WR-02) | On the scoring call path | ✓ |
| 02 WR-03 breaker counting | Retry-outer counts attempts | ✓ (confirmed in follow-up) |
| 03 dialog warnings WR-01..05 | UI polish | |
| Raindrop/deploy items | 02 WR-04, WR-05, Raindrop todo | |

| Option | Description | Selected |
|--------|-------------|----------|
| Breaker outer | One record per logical call | ✓ |
| Keep Retry outer, restate thresholds | | |
| Not folding | | |

**Notes:** The first multi-select left WR-03 unchecked, but the user also answered "Breaker outer". A follow-up question asked them to confirm, and they chose "Yes, fold it in".

## Claude's Discretion

Event/class naming, config property names/defaults, endpoint paths, the progress-line presentation, final timeout value (20–30s), and the test strategy for the 30s-stub criterion.

## Deferred Ideas

Immediate sweep kick; `lastError` on `/status`; separate timeouts; 03 WR-01..05; 02 WR-04/WR-05; Raindrop tuning todo.
