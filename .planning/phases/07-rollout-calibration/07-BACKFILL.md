# Phase 7: Launch Backfill

Evidence for the 0.2.0 release (plan 07-06) and the launch backfill (plan 07-07). This file holds
numbers, class names, timestamps and ids only. It never includes the profile text, the topic
descriptions or names, the key, or raw log bodies. The raw evidence (logs, JSON snapshots, the
pre-deploy dump) lives outside the repository under `$HOME/.cache/myfeeder-phase07/`.

## Release 0.2.0

| Fact | Value |
|---|---|
| Date (UTC) | 2026-09-28 |
| `.envrc` scan verdict (D-16) | `envrc: SAFE`; committed on main as `abeac7f` |
| Merge commit on main (`--no-ff` of sbartram/main) | `687217f28fa5b2881257bb709fc674817ec9e50f` (parents `abeac7f`, `88d2218`) |
| Tag | `v0.2.0` (annotated, on origin, points at `687217f`) |
| VERSION | `0.2.0` (one jar: `myfeeder-0.2.0.jar`, build-info `build.version=0.2.0`) |
| Image | `registry.bartram.org/bartram/myfeeder:0.2.0` |
| Image digest | `sha256:2252196edbc69dc3e2bbf1d29334256ab58942ae2503fc5c3bb378d49edf7fd7` |
| Helm revision | 18 (deployed 2026-09-28T22:27:00Z) |
| Rollback revision | 17 (0.1.24, still in history) |
| Rollout finished | 2026-09-28T22:27:29Z |
| Flyway V6 | success `t`, installed 2026-09-28 22:27:16 UTC, 2586 ms |
| Startup | `Started MyfeederApplication` in 13.25 s; 0 ERROR lines; no failed-to-start banner; no `TypeSafe Jev not configured` |
| `/api/version` | before `0.1.24`, after `0.2.0` |
| SPA bundle | `index-DmtnXdgs.js` before, `index-C8-FoLkO.js` after |
| Key check | `key-hash: match` (sha256 of cluster secret equals the local key; neither printed) |
| Status after deploy | configured `true`, coldStart `true`, breakerState `CLOSED`, eligibleUnscored 182, failed 0, tiers `{high: 70, neutral: 40}` |
| Preview smoke (one billed call) | HTTP 200, model `jev-1.13.0`, noul 0.02 (article id 26041) |
| Pre-deploy dump (D-03) | yes, 11,305,302 bytes (11 MiB), mode 600, `pg_restore --list` readable (7 TABLE DATA entries incl. `public article`); `$HOME/.cache/myfeeder-phase07/myfeeder-pre-0.2.0.dump` |
| Soak | pass: log captured 2026-09-28T22:38:00Z (10.5 min after rollout); `Registered polling tasks for 46 feeds` (pre-deploy count 46); 0 ERROR lines; 2 WARN lines, both `FeedFetchException` from feed hosts (one body over 10 MiB, one HTTP 429); 0 `Jev ` log lines |
| Status after soak | configured `true`, coldStart `true`, breakerState `CLOSED`, eligibleUnscored 182, failed 0 |
| Scored rows | `article_score` count 0 (cold start holds scoring idle until the rubric is saved in 07-07) |
| Failure path | not run |

Note: the secret change triggered two rollouts. The chart's `checksum/secret` annotation rolled
ReplicaSet revision 19, and the cluster's Reloader (`Changes detected in 'myfeeder-secret'`) updated
the Deployment again one second later (ReplicaSet revision 20). The revision-19 pod was stopped
seconds after starting. The revision-20 pod validated six migrations and found the schema at V6.
There was one Helm revision (18).
