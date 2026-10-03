# Phase 8: Engagement Capture Release

This file holds numbers, ids, class names and timestamps only. It never holds a key, token, password, article title or raw log body. The raw evidence (preflight, snapshots, logs, scripts and the dump) lives under `$HOME/.cache/myfeeder-phase08/`, outside the repo.

## Release 0.3.0

| Fact | Value |
|------|-------|
| Approval | D-13 blocking-human checkpoint answered `approve b`, relayed by the execute-phase orchestrator (b = ignore `.planning/milestone.lock` in `.gitignore`) |
| Phase branch | `gsd/phase-08-engagement-capture` @ `5b1f30dc4c492dc18f01377067c65933823d4fdb` |
| Merge into main | `4b3841b69b547173bfa48dc688bf68df93400814` (`--no-ff`) |
| Lock-ignore commit | `db5acd76ef52fc7bd82ec4b1a36bdc7b749907d2` (`.planning/milestone.lock` in `.gitignore`; axion verifyRelease counted the untracked lock) |
| Pushed | `origin/main` 52c830c → db5acd7 (fast-forward, hooks ran, no force) |
| Tag | `v0.3.0` (annotated, object `53def2e5f8066a27d0a2885558fe55d3cb83e24a`) on commit `db5acd76ef52fc7bd82ec4b1a36bdc7b749907d2`, on origin; cut with `-Prelease.versionIncrementer=incrementMinor` |
| VERSION | `0.3.0` (build-info `build.version=0.3.0`; one jar `myfeeder-0.3.0.jar` with the SPA bundle and `V7__engagement.sql`) |
| Image | `registry.bartram.org/bartram/myfeeder:0.3.0`, digest `sha256:7a785af7ef5f91c7fb96f6ee47d2123d1eb1f292722fdd13d87fcc8a5d1ed464` (OCI single-platform manifest, `--provenance=false`) |
| Helm | revision 20 deployed 2026-09-30T03:57:45Z; rollback revision 19 (0.2.1); rollout complete 03:58:07Z |
| Flyway V7 | `flyway_schema_history` installed_rank 7, version 7 `engagement`, success `t`, installed 2026-09-30 03:57:58, 981 ms (schema 6 → 7) |
| Startup | PASS: `Started MyfeederApplication` in 14.518 s, 0 ERROR lines, no `TypeSafe Jev not configured`; `/api/version` 0.3.0; `/api/interest/status` configured, CLOSED, not cold start |
| Ranking (SC-5) | `ranking: UNCHANGED` over 47 ids scored in both Priority top-50 captures (same relative order, identical badges) |
| Why (SC-5) | `why: UNCHANGED` for 5 of 5 pre-deploy top-5 ids (26221, 26224, 26246, 26163, 26096), byte-identical `{id, interestScore, interestBreakdown}` |
| Smoke | `smoke: PASS` on article 26221 (no prior engagement): PUT open 204 → `["OPEN_ORIGINAL"]`; second PUT 204 → 1 row; DELETE 204 → `[]`; PUT on 999999999 → 404 |
| Soak | PASS: 10 m 28 s after rollout (log captured 04:08:35Z), `Registered polling tasks for 46 feeds` (= pre-deploy 46), 0 ERROR lines, 0 pod restarts; 8 WARN lines are `FeedFetchException` poll failures on feeds that already had error counts before the deploy |
| Dump | taken before the deploy, 11331027 bytes, `pg_restore --list` reads 91 entries; `$HOME/.cache/myfeeder-phase08/myfeeder-pre-0.3.0.dump` (mode 600) |
| v0.2.1 unchanged | `5461d0a555e9daa8f0907e07660e5e51958d154c` (matches the preflight record) |
| Failure path | not run (no rollback, no restore) |

## Follow-up

The rows-accumulate check (SC-5) is pending the user's real use. After at least a day, a read-only `SELECT kind, count(*) FROM article_engagement GROUP BY kind` should show rows for the kinds used. At release time the table held 0 rows (the smoke's own row was forgotten).
