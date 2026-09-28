# Phase 7: Rollout & Calibration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-27
**Phase:** 7-Rollout & Calibration
**Areas discussed:** Release & key rollout, Backfill observation, Calibration method, Tier configurability

---

## Release & key rollout

| Option | Description | Selected |
|--------|-------------|----------|
| 0.2.0 | Minor bump for the new schema, dependency and view | ✓ |
| 0.1.25 | Axion default patch bump | |

| Option | Description | Selected |
|--------|-------------|----------|
| Key on first deploy | Scoring stays idle until a rubric is saved (cold start) | ✓ |
| Deploy dark, key later | Soak V6 + UI unconfigured, then redeploy with the key | |

| Option | Description | Selected |
|--------|-------------|----------|
| pg_dump before deploy | One-off dump; rollback = helm rollback + restore | ✓ |
| Helm rollback only | V6 is additive; no backup | |
| You decide | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Ship as-is, fix later | Open review warnings are non-blocking | ✓ |
| Fix 06 WR-01..04 first | Pre-release plan for Phase 6 correctness warnings | |
| Fix all open warns first | Fold 03/06/UI items into this phase | |

**User's choice:** 0.2.0; key live on first deploy; pg_dump safety net; ship with the open warnings.

---

## Backfill observation

| Option | Description | Selected |
|--------|-------------|----------|
| Drained, breaker never OPEN | Isolated absorbed 429s OK | ✓ |
| Zero 429s at all | Strict | |
| Drained + failed ≈ 0 | Also bounds exhausted failures | |

| Option | Description | Selected |
|--------|-------------|----------|
| Claude-run watch script | /status + logs loop, evidence in the phase dir | ✓ |
| Add metrics to the app | Actuator counters | |
| Manual eyeballing | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Stay at 1 | | ✓ |
| Try 2 after a clean first hour | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Throttle the sweep | Lower batch size / longer delay via config; keep the R4j retry | ✓ |
| Swap to SDK retry | Move the single retry layer to the SDK | |
| Decide if it happens | | |

**User's choice:** all recommended options.

---

## Calibration method

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only SQL replay | Candidate constant sets replayed over stored nouls → CALIBRATION.md | ✓ |
| Admin endpoint in app | GET distribution endpoint | |
| Eyeball the Priority view | | |

| Option | Description | Selected |
|--------|-------------|----------|
| High ≈ top 10–20% | Neutral 30–40%, rest muted | ✓ |
| Roughly even thirds | | |
| Judged by eye | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Once, after backfill drains | Tune profile-points + tiers; learn-rate/cap stay | ✓ |
| After a week of voting | Also tune learn-rate/cap | |

| Option | Description | Selected |
|--------|-------------|----------|
| Yes: top/bottom 20 review | Claude proposes, user approves | ✓ |
| Distribution only | | |

**User's choice:** all recommended options.

---

## Tier configurability

| Option | Description | Selected |
|--------|-------------|----------|
| Server config, served to client | blend.tiers.* in yaml, served via an interest GET, 70/40 fallback | ✓ |
| Server computes tier per article | interestTier on every article response | |
| Frontend constant | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Commit to application.yaml + patch release | Tuned values shipped as 0.2.1 | ✓ |
| Helm values override | Env overrides without a new image | |

**User's choice:** all recommended options.

---

## Claude's Discretion

- OPS-03 CLAUDE.md content (plus the Raindrop todo's doc items)
- Tier field shape on the status GET, watch-script mechanics, replay candidate sets and SQL, prod DB access path, soak length

## Deferred Ideas

- Open review warnings (03/06/UI, 02 WR-04/05): after launch
- learn-rate/cap tuning from a week+ of votes
- Concurrency > 1; Actuator metrics; `lastError` on /status
