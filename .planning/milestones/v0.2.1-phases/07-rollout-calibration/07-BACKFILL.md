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

## Backfill watch

| Fact | Value |
|---|---|
| Watch start (UTC) | 2026-09-28T22:43:59Z |
| Rubric save (UTC) | 2026-09-28T23:39:57Z (8 saves between 23:39:57.037Z and 23:39:57.807Z) |
| Save method | `load-phase3-rubric`: `PUT /profile` HTTP 200 (profile version 2), then 7 × `POST /topics` HTTP 201 (topic ids 1 to 7); no retry needed |
| Topics saved | 7 |
| Cold start ended | first `coldStart:false` sample 2026-09-28T23:39:57Z |
| Baseline eligibleUnscored | 183 at the watch start; 190 at the rubric save (7 new arrivals after the watch start) |
| Peak eligibleUnscored | 190 (first seen 2026-09-28T23:29:51Z) |
| Legacy backlog (fetched before the watch start) | 183 at the watch start and at the rubric save |
| First score row | 2026-09-28T23:40:26Z |
| Last score row | 2026-09-28T23:46:51Z |
| Drain end | 2026-09-28T23:46:51Z (eligibleUnscored 0 at the 23:47:00Z sample; legacy backlog 0 at the 23:50:01Z evidence line, confirmed by a fresh read-only query at 23:51:08Z) |
| Drain duration | 6 min 54 s from the rubric save to the last score row |
| Throughput | 50, 52, 50 and 40 rows in the minutes starting 23:40, 23:42, 23:44 and 23:46 (one sweep batch of 50 every 2 minutes, plus ingest) |
| Final eligibleUnscored / failed | 0 / 0 (2026-09-28T23:51:27Z) |
| Watch stopped | 2026-09-28T23:52:02Z (`watch.stop` marker) |
| Status samples | 53, 0 unreachable; one gap of 997 s (23:13:14Z to 23:29:51Z) while the workstation slept, before the rubric save |

## Time series

Rows with evidence are the watch's read-only SQL samples (about every 10 minutes). The rows from
23:40 to 23:47 are the 60-second status samples during the drain, which had no SQL sample.

| UTC | eligibleUnscored | failed | breakerState | legacy backlog | scored rows |
|---|---|---|---|---|---|
| 2026-09-28T22:43:59Z | 183 | 0 | CLOSED | 183 | 0 |
| 2026-09-28T22:54:04Z | 183 | 0 | CLOSED | 183 | 0 |
| 2026-09-28T23:04:09Z | 186 | 0 | CLOSED | 183 | 0 |
| 2026-09-28T23:29:51Z | 190 | 0 | CLOSED | 183 | 0 |
| 2026-09-28T23:39:57Z | 190 | 0 | CLOSED | 183 | 0 |
| 2026-09-28T23:40:57Z | 140 | 0 | CLOSED | - | - |
| 2026-09-28T23:41:58Z | 140 | 0 | CLOSED | - | - |
| 2026-09-28T23:42:58Z | 90 | 0 | CLOSED | - | - |
| 2026-09-28T23:43:58Z | 90 | 0 | CLOSED | - | - |
| 2026-09-28T23:44:59Z | 40 | 0 | CLOSED | - | - |
| 2026-09-28T23:45:59Z | 40 | 0 | CLOSED | - | - |
| 2026-09-28T23:47:00Z | 0 | 0 | CLOSED | - | - |
| 2026-09-28T23:50:01Z | 0 | 0 | CLOSED | 0 | 192 |
| 2026-09-28T23:51:27Z | 0 | 0 | CLOSED | 0 | 192 |

## Log evidence

Pod `myfeeder-5d6c6b4b9f-q66j7` (0 restarts, no pod change during the watch), log since
2026-09-28T22:43:59Z, 85 lines.

| Count | Value |
|---|---|
| `Jev retry attempt` lines, total | 0 |
| `Jev retry attempt` lines by exception class | none |
| Rate-limit retry lines (`after TypeSafeRateLimitException`), total | 0 |
| Most rate-limit retry lines in any 10-minute window | 0 |
| `Jev retries exhausted` lines by class | none (0) |
| `Jev circuit breaker` transition lines | none (0) |
| Status samples with breakerState other than CLOSED | none (0 of 53) |
| Unreachable status samples | 0 |
| ERROR lines | 0 |
| WARN lines | 1, `ResourceAccessException` from `FeedPollingService` at 2026-09-28T23:27:41Z (a feed host, before the rubric save; not Jev) |

## Score rows

| Count | Value |
|---|---|
| SCORED | 192 |
| FAILED | 0 |
| SKIPPED | 0 |
| Rows retried (attempts > 1) | 0 |
| FAILED rows by class | none |
| Model | `jev-1.13.0` on all 192 rows |

192 rows covers the 183 legacy-backlog articles plus 9 articles that arrived after the watch start.

## Throttle

none. No breaker transition and no rate-limit retry occurred, so the D-08 sweep throttle was never
applied. The deployment env holds no sweep-throttle, SDK-retry or scoring-concurrency override, and
07-09 has no override to remove.

## Verdict

| Criterion | Result |
|---|---|
| D-05 drain | PASS |
| D-05 breaker CLOSED | PASS |
| D-05 429 absorbed | PASS |
| Overall | PASS |

- Drain: legacy backlog 0 (evidence line 23:50:01Z, fresh query 23:51:08Z, final line 23:51:27Z).
- Breaker: all 53 status samples read CLOSED, and the log holds 0 `Jev circuit breaker` lines.
- 429 absorbed: 0 exhausted rate-limit lines, and 0 FAILED rows from `TypeSafeRateLimitException`. No
  429 occurred at all (0 rate-limit retry lines).

D-07: concurrency stayed 1
