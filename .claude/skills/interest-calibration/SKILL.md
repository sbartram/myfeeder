---
name: interest-calibration
description: Tune myfeeder interest-ranking constants (tier thresholds, profile points, engagement open/save weight and cap) with the read-only calibration replay, then commit them to yaml. Use when calibrating or revisiting blend/engagement constants.
---

# Interest calibration

## Tier thresholds

To tune, run `scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] ...` (a read-only psql replay of the verbatim `InterestScoreQueries` blend, drift-guarded by `InterestCalibrationReplaySqlTest`; needs `MYFEEDER_PG_PASSWORD` and the LAN) and record the result in a phase calibration note (v0.2.1's is `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md`). Put tuned values in main `application.yaml` and `src/test/resources/application-dev.yaml`, and keep the test yaml at profile-points 100 / tiers 70 / 40, because `InterestScoreQueriesTest` fixtures assume them. The replay also reads `ENGAGEMENT_OPEN_WEIGHT`, `ENGAGEMENT_SAVE_WEIGHT` and `ENGAGEMENT_CAP` (defaults 0.25 / 0.5 / 8, validated as numbers and against `Engagement.isValid` before connecting). Its psql `-v` names equal the JdbcClient parameters (guarded by `driverPassesEveryBlendParameter`), and its learned section reports `eng_raw`, `eng` and `eng_at_cap`. Calibrated engagement values go in main yaml plus `application-dev.yaml`, and the test yaml keeps 0.25 / 0.5 / 8 (`DevProfileConfigTest` enforces the split; see Engagement tuning above). Never tune through Helm `--set` or env overrides (D-14).

- **Engagement tuning (v0.3.1, Phase 12)**: tune the three constants with the read-only replay, then commit them in yaml.
  - Driver:
    - `scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] ...`; OPEN, SAVE and CAP are given together or omitted, and omitted fields come from `ENGAGEMENT_OPEN_WEIGHT` / `ENGAGEMENT_SAVE_WEIGHT` / `ENGAGEMENT_CAP`.
    - It writes one `replay-pp<PP>-hi<HIGH>-ne<NEUTRAL>-op<OPEN>-sv<SAVE>-cap<CAP>.tsv` per candidate (default `OUT_DIR` `$HOME/.cache/myfeeder-phase12/replay`).
    - Before connecting, it refuses any set that `Engagement.isValid` refuses, with `invalid engagement constants` (closes Phase 9 WR-02).
    - Engagement off is the same replay at cap 0.
  - Sections (besides the summary, window summary and `learned`):
    - `engaged`: every engaged SCORED article's badge, read or unread; the prod cross-check against `GET /api/articles/{id}`.
    - `dormant` and `dormant-kind`: engaged but never SCORED, overall and per kind (ENG-F4).
    - `floor`: the D-02 data floor.
    - `backfill-pool`, `backfill-summary` and `backfill-learned`: stars and board rows (Read Later included) simulated as saves (ENG-F5). Only the `engaged` CTE differs from the app, and the drift guard proves it.
    - `eng_articles` on `learned` and `backfill-learned`: per topic, the engaged SCORED articles above the hinge.
  - Rules:
    - D-02 floor: at least 30 engaged SCORED unvoted articles and at least 3 non-negative topics with engagement. Below it, wait; the D-03 fallback keeps the defaults and marks them revisit.
    - D-04 nudge: |high share minus the cap-0 high share| <= 5 points, with no topic pinned at the cap unless its `eng_articles` reaches the confirmed threshold T (default 10). If no candidate beats the defaults, keep them.
    - D-09 (ENG-F4): revisit when dormant is at least 25% of engaged, keep otherwise.
    - D-10 (ENG-F5): recommend a real backfill only when the backfill stays within the nudge rule.
    - D-05: the tiers and near-miss are not tuned here.
  - Yaml: the approved values go in main `application.yaml`, any value that differs from the test yaml's 0.25 / 0.5 / 8 also goes in `application-dev.yaml`, and never in Helm `--set` or env (D-14).
  - Outcome (2026-10-02): the D-03 fallback on thin data kept open-weight 0.25, save-weight 0.5 and cap 8, marked revisit; every grid candidate equalled cap 0 because no counted article clears a non-negative topic's hinge. ENG-F4 keep (1 dormant of 16 engaged). ENG-F5 recommend (no harm today, no measured benefit). See `.planning/phases/12-calibration-release/12-CALIBRATION.md`.
  - Proofs: `InterestCalibrationReplaySqlTest` (drift, backfill bypass, privacy and driver tests) and `InterestCalibrationReplayRunTest` (an end-to-end run on Testcontainers through host psql).
