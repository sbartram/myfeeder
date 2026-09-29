---
phase: 07-rollout-calibration
plan: 09
subsystem: infra
tags: [release, helm, k3s, axion, calibration, interest-scoring, yaml]

requires:
  - phase: 07-rollout-calibration
    provides: "07-08 approved constants (approved-constants: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20)"
  - phase: 07-rollout-calibration
    provides: "07-06 release sequence (0.2.0, Helm rev 18) and 07-07 backfill verdict (no D-08 throttle)"
provides:
  - "Calibrated tier constant tiers.neutral=22 in main application.yaml and the dev overlay (D-14)"
  - "Release v0.2.1 (merge 5461d0a), image myfeeder:0.2.1 sha256:42fcdc5b, Helm revision 19"
  - "Prod /api/interest/status serving tiers 70/22 with configured true, coldStart false, CLOSED"
  - "07-CALIBRATION.md ## Shipped in 0.2.1 release record"
affects: [phase-07-verification, milestone-completion, future-calibration-recheck]

actuals:
  tokens: 1050
  tasks: 3
  commits: 2
plan_head_before: a36736b48cfb02804d4630003939f7ee9c0c68a0
plan_head_after: 93d19fdea74ebb52d1bf949c07b7cdcf01dfb770

tech-stack:
  added: []
  patterns:
    - "Tuned constants ship through source control: main yaml plus dev overlay, test yaml pins suite constants (D-14)"

key-files:
  created:
    - .planning/phases/07-rollout-calibration/07-09-SUMMARY.md
  modified:
    - src/main/resources/application.yaml
    - src/test/resources/application-dev.yaml
    - src/test/resources/application.yaml
    - .planning/phases/07-rollout-calibration/07-CALIBRATION.md

key-decisions:
  - "07-09: user approved publishing 0.2.1 (verbatim: approve); v0.2.1 = main 5461d0a, image sha256:42fcdc5b, Helm rev 19, rollback rev 18 (0.2.0); failure path not run"
  - "07-09: prod serves tiers 70/22 (configured, CLOSED, not cold start); replay at 100/70/22 matched the app 5/5; no MYFEEDER_INTEREST_* override existed, so none was removed"

patterns-established:
  - "A config-only release with unchanged secrets rolls the pod once (no Reloader double roll)"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "Approved constants in main application.yaml and the dev overlay; test yaml keeps 100/70/40"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "./gradlew test (532 pass, 2 skipped) incl. DevProfileConfigTest, InterestScoreQueriesTest (Task 1)"
        status: pass
      - kind: other
        ref: "bootTestRun /api/interest/status tiers {high:70, neutral:22}, configured false (local-status.json)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Release v0.2.1 published: --no-ff merge pushed, tag on origin, image in registry"
    requirement: OPS-02
    verification:
      - kind: other
        ref: "Task 3 verify block 1 (ls-remote v0.2.1, main==origin/main, docker manifest inspect, main^2 ancestor of sbartram/main)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Prod runs 0.2.1 with the key, serves the approved tiers and computes with the tuned constants"
    requirement: OPS-02
    verification:
      - kind: other
        ref: "Task 3 verify block 2 (rollout, image :0.2.1, /api/version 0.2.1, no MYFEEDER_INTEREST_* env)"
        status: pass
      - kind: other
        ref: "Task 3 verify block 3 (/status configured, !coldStart, CLOSED, tiers 70/22; crosscheck-0.2.1.json 5/5; Shipped section)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Badges in the prod UI re-tier by the approved thresholds (neutral band now starts at 22)"
    verification: []
    human_judgment: true
    rationale: "Visual tier colors in the live UI are not asserted by any test; queued for end-of-phase UAT"

duration: 37min
completed: 2026-09-29
status: complete
---

# Phase 7 Plan 09: Ship Calibrated Constants as 0.2.1 Summary

**Release 0.2.1 shipped tiers.neutral 40 to 22 (profile-points 100, high 70, learn-rate 2, cap 20) through source control. Prod at Helm revision 19 now serves tiers 70/22 with Jev configured and the breaker CLOSED, and a replay at the approved constants matched the app's badges 5 of 5.**

## Performance

- **Duration:** 37 min
- **Started:** 2026-09-29T00:07Z
- **Completed:** 2026-09-29T00:44Z
- **Tasks:** 3 (1 tracer, 1 blocking-human decision, 1 release)
- **Files modified:** 4

## Accomplishments

- Approved constants (from `approved-constants: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20`) committed to main yaml with a `# D-14:` comment citing 07-CALIBRATION.md. Only `tiers.neutral` differs from the test yaml, so the dev overlay gained a `blend.tiers.neutral: 22` block. The test yaml keeps 100/2/20/70/40 and gained its explanatory comment.
- Local tracer: full suites green (backend 532 pass, 2 skipped; frontend `tsc -b` clean, 322 tests pass). `bootTestRun` served tiers `{high:70, neutral:22}` with `configured:false`.
- Task 2 answer from the user, verbatim: `approve`
- Released and deployed 0.2.1:
  - `--no-ff` merge `5461d0a` pushed to origin/main.
  - `./gradlew release` pushed tag `v0.2.1` (patch increment).
  - `clean bootJar` gave VERSION exactly `0.2.1` and one jar. The jar holds `neutral: 22`, `build.version=0.2.1` and the frontend bundle.
  - Image `registry.bartram.org/bartram/myfeeder:0.2.1` has digest `sha256:42fcdc5bd7542cdda55aee68e7080555d21c261ab3c55dbaab80b13620c041e2`.
  - `./deploy.sh 0.2.1` ran with all four secrets and produced Helm revision 19. The rollback target is revision 18 (0.2.0).
- Prod verification:
  - `/api/version` reports 0.2.1.
  - Startup log: `Started MyfeederApplication` in 13.6s, 0 ERROR lines, no `TypeSafe Jev not configured` line, 46 feeds registered.
  - `/api/interest/status` = `{"configured":true,"breakerState":"CLOSED","coldStart":false,"eligibleUnscored":0,"failed":0,"tiers":{"high":70,"neutral":22}}`.
- Replay cross-check at 100/70/22 (learn-rate 2, cap 20): 5 of 5 top articles matched the app's `interestScore` (25988, 26032, 25873, 26016, 26025). At deploy time, 199 scored unread split high 14.6%, neutral 35.2% and low 50.3%, still inside the D-10 bands.
- D-08 carry-over: the deployment's env names contain no `MYFEEDER_INTEREST_*`, so there was no override to remove (07-07 applied no throttle). `helm/` has no profile-points or tiers key.
- The failure path did not run.

## Task Commits

1. **Task 1 (tracer): approved constants flow yaml → dev overlay → local /status** - `49b9e54` (feat)
2. **Task 2: approve publishing 0.2.1** - no commit (checkpoint; user answered `approve`)
3. **Task 3: merge, release, build, deploy and verify 0.2.1** - `93d19fd` (docs: `## Shipped in 0.2.1` in 07-CALIBRATION.md). Commits in the main checkout: merge `5461d0a` on main and tag `v0.2.1`.

## Files Created/Modified

- `src/main/resources/application.yaml`: `tiers.neutral` 40 to 22 and a `# D-14:` comment citing 07-CALIBRATION.md
- `src/test/resources/application-dev.yaml`: `blend.tiers.neutral: 22` block, so the dev overlay mirrors main (DevProfileConfigTest parity)
- `src/test/resources/application.yaml`: comment only; the suite keeps the pre-calibration constants
- `.planning/phases/07-rollout-calibration/07-CALIBRATION.md`: `## Shipped in 0.2.1` release record

## Decisions Made

- The 0.2.1 replay went to `$HOME/.cache/myfeeder-phase07/replay/0.2.1/` (via `OUT_DIR`), so it did not overwrite 07-08's `replay-pp100-hi70-ne22.tsv` evidence. The cross-check JSON is at the path the plan specifies (`replay/crosscheck-0.2.1.json`).
- Rollback stays `./deploy.sh 0.2.0` (Helm revision 18). The two releases differ only in config, with no schema change.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] bootTestRun served on port 18080 instead of 8080**
- **Found during:** Task 1 (local tracer, previous executor)
- **Issue:** The user's qbittorrent Docker container holds port 8080, so `bootTestRun` could not bind the plan's `http://localhost:8080`.
- **Fix:** Ran bootTestRun with `SERVER_PORT=18080` and polled `http://localhost:18080`. The qbittorrent container was left running. The bootTestRun process tree was stopped afterwards.
- **Files modified:** none
- **Verification:** `local-status.json` shows tiers `{high:70, neutral:22}` with `configured:false`, and the Task 1 verify passed.
- **Committed in:** n/a (runtime only)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** A port change for the local check only. There is no scope change and no code change.

## Issues Encountered

- One WARN in the startup log: `Failed to poll feed 'Istio Blog and News'` (FeedFetchException). This is a routine failure to fetch that remote feed, unrelated to 0.2.1. There were 0 ERROR lines.
- `clean bootJar` finished in 2s, probably because the Gradle build cache supplied outputs. The jar was inspected before the image build and holds the tuned yaml, `build.version=0.2.1` and the frontend bundle (`index-C8-FoLkO.js`).

## User Setup Required

None. All four deploy secrets were already exported (checked with `test -n` only).

## Next Phase Readiness

- Every plan in Phase 7 is complete. OPS-02 is shipped, and ROADMAP SC2 (badges spread across tiers) holds in prod.
- End-of-phase UAT still has to check that the badge tier colors in the prod UI reflect the 70/22 thresholds.
- As the user reads high articles, the unread high share will drift below 14%. The 14-day window figures in 07-CALIBRATION.md are the reference for a later re-check.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-29*
