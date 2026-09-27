# Phase 7: Rollout & Calibration - Context

**Gathered:** 2026-09-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship interest ranking (Phases 2–6, 302 commits unreleased since `v0.1.24`) to production with a live TypeSafe key. Watch the launch backfill drain the eligible backlog without 429 storms or an open `jev` circuit. Make the badge tiers configurable next to the existing blend constants. Tune profile-points and the tiers against the real score distribution. Document the Jev behaviors in CLAUDE.md. Requirements: OPS-01, OPS-02, OPS-03.

This phase adds no new user-facing capability. It is release, observation, config and docs.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (locked by prior phases, not re-discussed)
- **Blend (05/06):** `raw = ROUND(100·p·(profile-points/100) + Σ m_t·w_t, 6)`, `m_t = max(0,(noul−0.5)·2)`, `w_t = clamp(base + learned, −50, +50)` with sign clamp, `learned = clamp(learn-rate·Σ vote·m_t, ±learned-cap)`. The existing constants are `myfeeder.interest.blend.{profile-points: 100, learn-rate: 2, learned-cap: 20}` in `application.yaml`. All of it is **query-time**: changing constants needs no Jev re-score.
- **One source of truth:** `InterestScoreQueries` drives the sort, the badge and the breakdown. The client never recomputes scores.
- **Tiers today:** ≥70 high, 40–69 neutral, <40 muted, hardcoded in `src/main/frontend/src/utils/interest.ts` `tierOf`.
- **Pipeline (04):** a single-thread `jev-score` executor (`myfeeder.interest.concurrency: 1`), a sweep every `PT2M` enqueueing ≤ `sweep-batch-size: 50`, eligibility = unread and inside `window-days: 14`. The sweep pauses when unconfigured, in cold start, or with the breaker OPEN. The `jev` breaker is the outer aspect (one outcome per logical call) with auto OPEN→HALF_OPEN. Exactly one retry layer: Resilience4j `jev` retry (3 attempts, 429 `retry-after-ms` honored ≤10s), SDK `retry.max-retries: 0`.
- **`/api/interest/status`** is append-only: `{configured, breakerState, coldStart, eligibleUnscored, failed}`. New fields are appended and existing ones are never renamed.
- **Deploy pipeline (root CLAUDE.md):** release before bootJar, `clean bootJar`, `docker build --provenance=false`, push, `./deploy.sh $VERSION`. `deploy.sh` already takes the optional `MYFEEDER_TYPESAFE_API_KEY` (02-04), and checksum/secret rolls the pod.
- **Git:** merge `sbartram/main` → `main` with `--no-ff` before releasing.

### Release & key rollout (OPS-01)
- **D-01:** Release as **0.2.0** (minor bump for a new schema, a new external dependency and a new view): `./gradlew release` with axion's minor incrementer (e.g. `-Prelease.versionIncrementer=incrementMinor`), not the default patch.
- **D-02:** **The key goes live on the first deploy.** Set `MYFEEDER_TYPESAFE_API_KEY` for the 0.2.0 deploy. Scoring stays idle until the user saves a profile/topic in prod (cold start), so the user controls when the backfill starts. No dark deploy.
- **D-03:** Take a **one-off `pg_dump` of the prod myfeeder DB before the 0.2.0 deploy.** Rollback = `helm rollback` to 0.1.24, and restore only if needed. V6 is additive. The planner should verify that Flyway on 0.1.24 tolerates an applied-but-unknown V6 (default `ignoreMigrationPatterns: *:future`).
- **D-04:** **Ship with the open review warnings.** 06-REVIEW WR-01..04, the 06-UI-REVIEW a11y items and 03-REVIEW WR-01..05 are non-blocking and are fixed after launch, not in this phase.

### Launch backfill observation (OPS-01)
- **D-05:** **Pass bar:** `eligibleUnscored` drains to ~0 (only new arrivals remain), `breakerState` stays CLOSED for the whole backfill, and any 429s in the logs were absorbed by the `jev` retry, with no FAILED rows caused by them. A few isolated 429s are acceptable.
- **D-06:** **Observation = a Claude-run watch script.** It's a throwaway loop, not app code: `kubectl port-forward`/curl `/api/interest/status` plus `kubectl logs` grepped for 429 / breaker transitions. It records counts over time as evidence in the phase dir (e.g. `07-BACKFILL.md`). No Actuator metrics are added.
- **D-07:** **Concurrency stays at 1** during and after launch. Raise it only if drain time actually becomes a complaint, which is not planned here.
- **D-08:** **429/breaker fallback = throttle the sweep via config:** lower `sweep-batch-size` and/or lengthen `sweep-delay`, keep the single Resilience4j `jev` retry, and let auto half-open resume. Do **not** swap to the SDK retry layer. (Note: at ~2.6s/call, 50 per 2 min already roughly matches single-thread throughput, so a lower batch size is what actually caps the rate.)

### Calibration method (OPS-02)
- **D-09:** **A read-only SQL replay against prod pg.** A committed analysis SQL/script replays the blend over stored nouls/profile scores for several candidate constant sets and prints, for each set, the tier histogram and percentiles over scored unread articles. Results go into `07-CALIBRATION.md` in the phase dir. No app endpoint is added.
- **D-10:** **Target:** among scored unread articles, **high ≈ 10–20%**, neutral ≈ 30–40%, the rest muted. High must stay a real "read this" signal.
- **D-11:** **Quality check:** for the proposed constants, the replay also lists the **top 20 and bottom 20 article titles**. Claude proposes the constants and the **user approves** them after reviewing those lists (a human checkpoint) before they ship.
- **D-12:** **Timing:** calibrate **once, after the launch backfill drains**. Tune `profile-points` and the tiers. Leave `learn-rate`/`learned-cap` at 2/20 unless the available vote data already shows a clear problem (a few days of votes is too little to tune them).

### Tier configurability (OPS-02)
- **D-13:** Tier thresholds move to **server config**: `myfeeder.interest.blend.tiers.high` (default 70) and `.neutral` (default 40) in `application.yaml` (and the `MyfeederProperties` binding). They are **served to the client** by an existing interest GET. The preferred option is appending fields to `/api/interest/status` under the append-only rule; the exact shape is Claude's discretion. `tierOf` takes the thresholds from that query, with 70/40 as the fallback until it loads. Tuning = a config change, no frontend constant edit. — **Reversibility:** costly — the new status/config field becomes a contract that `InterestBadge`/`tierOf` and their tests consume, and status fields are append-only and never renamed.
- **D-14:** **Tuned constants ship via source control.** Commit them to main `application.yaml` with a comment citing `07-CALIBRATION.md`, keep the test yaml + `application-dev.yaml` overlay consistent so `DevProfileConfigTest` passes, and release as **0.2.1** through the normal pipeline. No Helm env overrides for blend/tier values.

### Claude's Discretion
- **OPS-03 CLAUDE.md content (not discussed):** document the app-owned `TypeSafeClient` bean (`config/TypeSafeConfig`, Supplier-only key, Jev-only Reactor Netty transport), the single retry layer (Resilience4j `jev` retry, SDK retries 0, 429 retry-after handling), the `jev-score` scoring executor (concurrency/queue/discard, sweep cadence and batch cap, gates), the eligibility window, and the new tier config. Also fold in the Raindrop todo's doc items: Package Structure listing `config/TypeSafeConfig` and the Jev integration classes, plus `MYFEEDER_TYPESAFE_API_KEY` as an optional `deploy.sh` variable in the Deployment section. Check for stale statements (e.g. "Spring AI Anthropic starter… not yet used", the Resilience4j aspect-order text already fixed in 04).
- The exact `/status` (or other GET) field shape for the tiers, and how `useInterestStatus`/`InterestBadge` get them in views where status isn't polled.
- The watch-script mechanics, polling interval, and how the evidence is summarized.
- The candidate constant sets for the replay (e.g. profile-points 60/80/100 × tier pairs) and the replay SQL structure. It should reuse the `InterestScoreQueries` CTE text or mirror it exactly so the replay matches what the app computes.
- The prod DB access path for the replay and `pg_dump` (e.g. psql to `pg.bartram.org` from the LAN or a `kubectl run` pod), always read-only for the replay.
- Soak length after the 0.2.0 deploy and the post-deploy smoke checks (startup logs, Priority view, a preview call).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` §Phase 7 — goal and the 3 success criteria
- `.planning/REQUIREMENTS.md` §Rollout — OPS-01, OPS-02, OPS-03
- `.planning/STATE.md` §Blockers/Concerns — the "429 behavior during launch backfill is unobserved; keep exactly one retry layer" concern and the list of open review warnings (deferred per D-04)

### Prior phase decisions
- `.planning/phases/04-scoring-pipeline-backfill-sweep/04-CONTEXT.md` — executor/sweep/eligibility/breaker decisions (D-05..D-10, D-16, D-17) that OPS-01 observes and OPS-03 documents
- `.planning/phases/05-blend-priority-view/05-CONTEXT.md` — blend formula, tiers, badge tier colors (D-18..D-20)
- `.planning/phases/06-thumbs-feedback/06-CONTEXT.md` — learned CTE and the learn-rate/cap semantics
- `.planning/phases/03-interest-model-schema-rubric-editor/` 03-08 summary — calibration spike (label agreement, topic under-firing deferred to OPS-02 tuning)

### Research
- `.planning/research/PITFALLS.md` — 429/rate-limit and calibration pitfalls
- `.planning/research/ARCHITECTURE.md` — blend CTE pattern (Pattern 4), derived learned model (Pattern 5)

### Deployment & conventions
- `CLAUDE.md` §Deployment "Cut a release" — the release pipeline order; §Gotchas (`bootTestRun` dev overlay, `DevProfileConfigTest`, the lfs pre-push hook fix)
- `deploy.sh` — env vars, including optional `MYFEEDER_TYPESAFE_API_KEY`
- `helm/myfeeder/values.yaml`, `helm/myfeeder/templates/app-secret.yaml`, `app-deployment.yaml` — how the TypeSafe secret reaches the pod
- `../HOMELAB.md` — shared infra (registry, pg, k3s)
- `.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` — its CLAUDE.md doc items belong to OPS-03 (the Raindrop tuning itself stays out of scope)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `repository/InterestScoreQueries.java`: the blend/learned CTE text blocks. The calibration replay must match them exactly (reuse or mirror them).
- `config/MyfeederProperties.java`: already binds `myfeeder.interest.blend.*`, and the tier thresholds extend it.
- `service/InterestStatusService.java` / `service/InterestStatus.java`: the natural place to serve tier thresholds (append-only record).
- `frontend/src/utils/interest.ts` `tierOf` + `components/InterestBadge.tsx`: the only tier consumers.
- `frontend/src/hooks/useInterest.ts` `useInterestStatus`: the status query (conditional 15s polling).

### Established Patterns
- Config changes must keep main `application.yaml` and the test yaml + `application-dev.yaml` overlay consistent. `DevProfileConfigTest` enforces this.
- Status fields are append-only. Fixed-text errors come via `GlobalExceptionHandler`.
- Releases: axion tag → `clean bootJar` → Dockerfile build → push → `deploy.sh $VERSION` → rollout status + logs.

### Integration Points
- Prod DB `pg.bartram.org` (split-horizon DNS, LAN only) for `pg_dump` and the read-only replay.
- k3s namespace `myfeeder`: `kubectl logs deploy/myfeeder` and a port-forward to reach `/api/interest/status` for the watch script.

</code_context>

<specifics>
## Specific Ideas

- The user saving their first prod profile/topic is the effective "start the backfill" moment. The watch script should be running before that save.
- The calibration checkpoint shows the user the top 20 and bottom 20 titles next to the histogram for the proposed constants, and nothing ships without their approval.
- Two releases are expected: 0.2.0 (the feature) and 0.2.1 (the tuned constants plus the tier config, if not already in 0.2.0).

</specifics>

<deferred>
## Deferred Ideas

- Fixing the open review warnings (06-REVIEW WR-01..04, 06-UI-REVIEW a11y items, 03-REVIEW WR-01..05, 02-REVIEW WR-04/05): after launch, via `/gsd-code-review --fix` or quick tasks (D-04).
- Tuning `learn-rate`/`learned-cap` from real vote data: after about a week or more of use. Not in this phase unless the data already shows a clear problem (D-12).
- Raising scoring concurrency above 1: only if drain time becomes a complaint (D-07).
- Actuator/Micrometer metrics for scoring and the breaker: rejected in favor of a throwaway watch script (D-06).
- A `lastError`/auth-failure field on `/status` (04 deferred): still deferred.

### Reviewed Todos (not folded)
- **Tune Raindrop resilience** (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): the Raindrop retry/backoff tuning is unrelated to interest rollout and stays a quick task. Only its CLAUDE.md doc items are taken into OPS-03 (Claude's Discretion).

</deferred>

---

*Phase: 07-rollout-calibration*
*Context gathered: 2026-09-27*
