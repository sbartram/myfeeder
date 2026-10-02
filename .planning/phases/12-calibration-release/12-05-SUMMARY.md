---
phase: 12-calibration-release
plan: 05
subsystem: release
tags: [release, deploy, v0.3.1, CAL-03, SC-3, D-12, D-13]
status: complete
requires:
  - 12-03 (12-CALIBRATION.md approved-constants line)
  - 12-04 (constants in yaml, CLAUDE.md SC-4 docs)
provides:
  - v0.3.1 tagged (c3a25fe), image pushed, deployed to k3s as Helm revision 21
  - "## Shipped in 0.3.1" section in 12-CALIBRATION.md
affects:
  - prod ranking (engagement learning and Suggested topics live at 0.25 / 0.5 / 8)
tech-stack:
  added: []
  patterns: []
key-files:
  created:
    - .planning/phases/12-calibration-release/12-05-SUMMARY.md
  modified:
    - .planning/phases/12-calibration-release/12-CALIBRATION.md
decisions:
  - "User approved the release with approve-exclude-untracked (D-12)"
  - "D-13 cross-check recorded as 'not provable yet' by the user: replay = api on 5/5 engaged ids, but 0 topics with engagement under the D-03 fallback means no engagement part can show"
metrics:
  duration: ~75 min (dry run ~4 min, release and soak ~25 min, plus decisions)
  completed: 2026-10-02
actuals:
  tasks: 3
  commits: 2
plan_head_before: 8c8a3c868434c7dddfa1f74aed061c513f48486e
plan_head_after: 4131e2c
---

# Phase 12 Plan 05: Release v0.3.1 Summary

v0.3.1 ships Phases 9 to 11 and the Phase 12 constants (0.25 / 0.5 / 8, marked revisit) to prod. The startup and the soak are clean, and the replay at the shipped constants equals what prod serves on 5 of 5 engaged articles.

## Tasks

| Task | Name | Commit | Result |
|------|------|--------|--------|
| 1 | Dry run of the v0.3.1 path (read-only, executor) | none (evidence only) | version 0.3.1, image tag 0.3.1, Helm diff = image tag only, suites green (723 backend, 413 frontend), rollback revision 20 |
| 2 | Approve publishing v0.3.1 (blocking-human) | — | user answer: `approve-exclude-untracked` |
| 3 | Merge, push, release, build, dump, deploy, D-13, soak, note (orchestrator, main checkout) | c3a25fe (merge), 4131e2c (note) | shipped; D-13 not provable yet |

## Release facts

- Merge `--no-ff` c3a25fe on main; pushed 9230982..c3a25fe (76 commits, 49 of them unpushed before this plan).
- Tag v0.3.1 → c3a25fe; jar VERSION 0.3.1; image sha256:8e7b648d1636df2499bc4bbbc71e5d3b9188aae222f9979f3db202373d71b059.
- Pre-deploy pg_dump 11.6 MB (114 entries) in `$HOME/.cache/myfeeder-phase12/`, mode 600.
- Helm revision 21 deployed 22:42:53Z, rolled out 22:43:23Z; rollback revision 20 (0.3.0).
- Startup clean (0 ERROR, Jev configured, no retries or breaker transitions); `/api/version` 0.3.1; status configured, CLOSED, tiers 70 / 22.
- Soak 10 min: polling registered for 46 feeds (pre-deploy 46), 0 ERROR, 4 per-feed poll WARNs.
- v0.3.0 and v0.2.1 tags unchanged.

## Deviations

- **Task 3 ran in the orchestrator, not an executor.** The project's isolation guard refuses non-worktree executor dispatch, and the merge, release tag and deploy must run from the main checkout. The orchestrator ran the steps one at a time in the plan's order.
- **D-13 (`sc3`) did not reach PASS.** Replay equals api on all 5 ids (65, 62, 21, 20, 16), but no article has a nonzero engagement part, because 0 topics have engagement (the D-03 fallback reason). The plan's `sc3: PASS` condition was structurally unreachable; the user chose to record it as "not provable yet" rather than fail the release. Re-run `$HOME/.cache/myfeeder-phase12/sc3.sh` once engaged articles match topics. Task 3's third automated verify block therefore does not pass as written.
- **Untracked files:** excluded locally for the release, then (user request, d995411) the MetalLB plan files were committed and `.claude/HANDOFF.md` was added to `.gitignore`; the local excludes were removed.

## Observations (not fixed)

- One soak WARN is a feed refused at the 10 MiB body cap (`Feed body exceeds 10485760 bytes`), as designed.
- `lfs.locksverify` is not set in this clone although CLAUDE.md says it is; the push succeeded without it (no pre-push hook installed).

## Self-Check: PASSED (with the D-13 disposition above)

- Tag v0.3.1 on origin, reachable from origin/main; image 0.3.1 in the registry; v0.3.0/v0.2.1 unchanged.
- Prod runs 0.3.1; startup and soak clean; status configured, CLOSED, 70 / 22.
- 12-CALIBRATION.md has `## Shipped in 0.3.1`, no key value or topic name, committed on main.
