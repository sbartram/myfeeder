# Phase 12: Engagement Calibration

Calibration of the engagement constants (`myfeeder.interest.blend.engagement.open-weight`, `save-weight`
and `cap`) against prod engagement (CAL-02, CAL-03, D-01 to D-10). Every number here comes from
`scripts/interest-calibration-replay.sh`, which runs the verbatim `InterestScoreQueries` blend in read-only
sessions (`default_transaction_read_only=on`). This file holds article ids, `topic_<id>` keys and numbers
only. It never includes article titles, topic names, topic descriptions, the profile text or any key,
because the origin is a public GitHub repo. The raw replay output lives outside the repository under
`$HOME/.cache/myfeeder-phase12/replay/20261002T213738Z/`.

## Run

| | Value |
|---|---|
| Date | 2026-10-02 (replay 21:37Z) |
| App version | 0.3.0 (Helm rev 20; ranks with the v0.2.1 blend, engagement not live) |
| Breaker | CLOSED |
| Live constants | profile-points 100, learn-rate 2, learned-cap 20, tiers 70 / 22, near-miss 0.35; engagement constants not live in 0.3.0 |
| Scored unread | 312 |
| Scored in the 14-day window (read or unread) | 1003 |
| Votes | 4 (4 up, 0 down) |
| Topics | 10 (`topic_1`..`topic_10`, base weights 30, 30, 20, 20, 15, -20, -20, 20, 25, 35; 8 non-negative) |
| Candidates replayed | 7 (D-06 grid, all at 100 / 70 / 22) |
| Cross-check (cap 0 replay vs `GET /api/articles/{id}` `interestScore` on 0.3.0) | 5 of 5 matched (26466, 26659, 26513, 26498, 26221) |

The top 5 replayed badges are all 100, so the cross-check also compared the next 5 unsaturated ids:
26452 (99), 26714 (98), 26224 (98), 26645 (97) and 26289 (95). All 10 match what 0.3.0 serves, so the
cap 0 replay equals the blend running in prod.

## Data floor

| | Value |
|---|---|
| counted (engaged, SCORED, unvoted articles) | 13 |
| Non-negative topics with nonzero `eng_raw` | 0 |
| Floor met | no |
| D-02 rule | counted >= 30 and topics >= 3 |
| Gate | `unmet-fallback` |

Earlier attempts, from `gate-history.txt` and the phase log, all on 2026-10-02:

| When | counted | topics | Outcome |
|---|---|---|---|
| Replay run 19:49Z | 8 | 0 | `unmet-hold`, answer `hold` |
| Read-only floor precheck 21:08Z | 13 | 0 | hold kept, no executor dispatch |
| Read-only floor precheck 21:21Z | 13 | 0 | led to the D-03 date amendment |
| Replay run 21:37Z (this note) | 13 | 0 | `unmet-fallback`, answer `fallback` |

D-03 applied. The fallback date was moved from 2026-11-11 to 2026-10-02 by the user (12-CONTEXT.md D-03
amendment, commit fe54be9). The prechecks showed that every counted article scores at most 0.10 on all
non-negative topics, so engagement adds 0 to every topic and the topic clause of the floor cannot be met
by normal reading. The data was thin: 13 of the 30 counted articles the floor asks for, and 0 of the 3
topics. The defaults 0.25 / 0.5 / 8 are kept and the engagement constants are marked revisit.

## Baseline (engagement off, cap 0)

Tiers follow `tierOf`: high is a badge at or above 70, neutral is at or above 22 and below 70, low is
below 22.

| Tier | Share of 312 scored unread |
|---|---|
| high (>= 70) | 24.4% |
| neutral (22..69) | 57.7% |
| low (< 22) | 17.9% |

| P10 | P25 | P50 | P60 | P75 | P80 | P85 | P90 | P95 |
|---|---|---|---|---|---|---|---|---|
| 7 | 30 | 51 | 58 | 69 | 73 | 75 | 80 | 92 |

| Statistic | Value |
|---|---|
| at_zero (badge 0) | 9 |
| at_100 (badge 100) | 6 |
| Ties at the minimum raw score | 5 |

Over all 1003 scored articles in the 14-day window (read or unread), the window summary gives high 11.6%,
neutral 33.9% and low 54.5% (P50 15, P90 72, at_zero 302, at_100 8).

## Candidates

D-06 grid: cap 0, the defaults, and cap {4, 8, 12} x save {0.5, 0.75} with open = save / 2. `delta` is the
run's high share minus the cap 0 high share, from the printed 1-decimal values. `bf` columns are the
simulated backfill (D-07) at the same constants. D-04 eligibility uses T = 10.

| Run (open / save / cap) | scored unread | high% | neutral% | low% | delta | topics at cap | max eng | bf high% | bf delta (vs off) | bf delta (vs real) | bf topics at cap | D-04 eligible |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 0.25 / 0.5 / 0 (off) | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | n/a (baseline) |
| **0.25 / 0.5 / 8 (defaults)** | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | **yes** |
| 0.25 / 0.5 / 4 | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | yes |
| 0.25 / 0.5 / 12 | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | yes |
| 0.375 / 0.75 / 4 | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | yes |
| 0.375 / 0.75 / 8 | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | yes |
| 0.375 / 0.75 / 12 | 312 | 24.4 | 57.7 | 17.9 | 0.0 | 0 | 0.000 | 24.4 | 0.0 | 0.0 | 0 | yes |

All 7 runs are identical to cap 0, section for section (summary, window summary, learned, backfill summary,
backfill learned). With every counted article off-topic, no constant in the grid moves any badge today.

## Learned model (proposed constants)

At 0.25 / 0.5 / 8. Votes: 4 (4 up, 0 down). The voted articles clear no topic's hinge, so `learned_raw`
is 0.000 on every topic too.

| Topic | base | eng_raw | eng | eng_at_cap | eng_articles | effective |
|---|---|---|---|---|---|---|
| `topic_1` | 30 | 0.000 | 0.000 | f | 0 | 30.000 |
| `topic_2` | 30 | 0.000 | 0.000 | f | 0 | 30.000 |
| `topic_3` | 20 | 0.000 | 0.000 | f | 0 | 20.000 |
| `topic_4` | 20 | 0.000 | 0.000 | f | 0 | 20.000 |
| `topic_5` | 15 | 0.000 | 0.000 | f | 0 | 15.000 |
| `topic_6` | -20 | 0.000 | 0.000 | f | 0 | -20.000 |
| `topic_7` | -20 | 0.000 | 0.000 | f | 0 | -20.000 |
| `topic_8` | 20 | 0.000 | 0.000 | f | 0 | 20.000 |
| `topic_9` | 25 | 0.000 | 0.000 | f | 0 | 25.000 |
| `topic_10` | 35 | 0.000 | 0.000 | f | 0 | 35.000 |

No topic is at the engagement cap and no topic gains any engagement weight.

## Dormant engagement (ENG-F4)

| engaged | scored | counted | voted | dormant | no row | failed | skipped | read | outside window | dormant% |
|---|---|---|---|---|---|---|---|---|---|---|
| 16 | 15 | 13 | 2 | 1 | 1 | 0 | 0 | 1 | 1 | 6.3 |

The one dormant article has no score row, is read and is outside the 14-day window. The `read` and
`outside window` columns overlap the status split; they are reasons the sweep cannot score it.

| Kind | engaged | dormant |
|---|---|---|
| OPEN_ORIGINAL | 16 | 1 |

D-09 rule: revisit when 4 x dormant >= engaged (a dormant share of at least 25%), keep otherwise. Here
4 x 1 = 4 < 16 (6.3%), so the call is **keep**: making dormant engaged articles eligible for scoring would
recover one article today. Nothing is built here.

## Backfill simulation (ENG-F5)

The simulation treats every starred article and every article on a board (Read Later included) as a save,
unioned with the real `article_engagement` rows, under the live CTE's rules (D-07). Nothing is executed
here; a real backfill would be its own migration or quick task.

| candidates (starred or on a board) | already engaged | added | added and counted |
|---|---|---|---|
| 5 | 0 | 5 | 0 |

At 0.25 / 0.5 / 8:

| | high% | delta |
|---|---|---|
| Engagement off (cap 0) | 24.4 | |
| Real engagement | 24.4 | 0.0 vs off |
| Backfill | 24.4 | 0.0 vs off, 0.0 vs real |

Topics at the cap under backfill: 0 (every `backfill-learned` row has `eng` 0.000 and `eng_at_cap` f).

Comparison base: off (cap 0), the default (research A3), confirmed at the checkpoint. D-10 rule: recommend
only when the backfill high share is within 5.0 points of the base and no non-negative topic is pinned at
the cap under backfill unless its `eng_articles` >= T (10). Both hold, so the call is **recommend**.

Caveat: none of the 5 added articles is both SCORED and unvoted, so the backfill adds 0 counted articles
and moves nothing. The recommendation means "no harm today", not a measured benefit. It should be
re-checked when the constants are revisited.

## Proposal

Keep the defaults: open-weight 0.25, save-weight 0.5, cap 8.

- Gate `unmet-fallback`: D-03 keeps 0.25 / 0.5 / 8 and marks the constants revisit.
- The D-04 rule on its own reaches the same result. The defaults are eligible (delta 0.0, no topic at the
  cap), so no candidate can beat them.
- Tiers 70 / 22 and near-miss 0.35 do not move (D-05).
- 12-04 writes the same literals into yaml, which already hold them.

## Verdict

| D-04 criterion | Proposal 0.25 / 0.5 / 8 | Met? |
|---|---|---|
| \|high% (proposal) - high% (cap 0)\| <= 5.0 | \|24.4 - 24.4\| = 0.0 | yes |
| Every non-negative topic at the cap has eng_articles >= T (10) | no topic at the cap | yes |

| Call | Rule | Result |
|---|---|---|
| ENG-F4 | 4 x dormant >= engaged | 4 < 16: keep |
| ENG-F5 | backfill delta vs off <= 5.0, no unearned topic at cap | 0.0, none: recommend (no measured benefit) |

## Approval

| | Value |
|---|---|
| Date | 2026-10-02 |
| Checkpoint | 12-03 Task 2 (D-01 to D-10, blocking-human decision) |
| User's answer (verbatim) | `fallback` |
| Option | fallback (D-03): keep 0.25 / 0.5 / 8, record that the data was thin, mark the constants revisit |
| T | 10 (default) |
| Base | off (default) |

Neither T nor the base changes any result: no topic has engagement, and the off and real histograms are
identical. 12-04 and 12-05 read the line below as their precondition.

approved-constants: open-weight=0.25 save-weight=0.5 cap=8
constants: revisit
eng-f4: keep
eng-f5: recommend

## Shipped in 0.3.1

| Item | Value |
|---|---|
| Approval (12-05 Task 2) | `approve-exclude-untracked`; the 3 untracked paths were excluded locally for the release, then the user committed the MetalLB plan and gitignored `.claude/HANDOFF.md` (d995411) |
| Phase branch / merge | gsd/phase-12-calibration-release, merged `--no-ff` as c3a25fe |
| Pushed range | 9230982..c3a25fe (76 commits) |
| Tag | v0.3.1 → c3a25fe34180ba5559bb2e0edd0ad8fdcfcd9f32, VERSION 0.3.1 |
| Image | registry.bartram.org/bartram/myfeeder:0.3.1, sha256:8e7b648d1636df2499bc4bbbc71e5d3b9188aae222f9979f3db202373d71b059 |
| Helm | revision 21 deployed 2026-10-02T22:42:53Z, rolled out 2026-10-02T22:43:23Z; rollback revision 20 (0.3.0) |
| Startup | clean: 1 `Started MyfeederApplication`, 0 ERROR, 0 `TypeSafe Jev not configured`; Jev retry 0, exhausted 0, breaker transitions 0 |
| /api/version | 0.3.1 |
| /api/interest/status | configured true, breaker CLOSED, coldStart false, tiers 70 / 22 |
| Engagement constants | open-weight 0.25, save-weight 0.5, cap 8 (equal to the approved line) |
| Removed env overrides | none (0 `MYFEEDER_INTEREST_*`) |
| D-13 cross-check (cap 8) | 26605: replay 65, api 65, engaged topics 0; 26771: replay 62, api 62, engaged topics 0; 26595: replay 21, api 21, engaged topics 0; 26427: replay 20, api 20, engaged topics 0; 26819: replay 16, api 16, engaged topics 0 |
| D-13 verdict | **not provable yet** (user, 2026-10-02): replay equals api on 5 of 5 ids, so the shipped blend matches the replay, but with 0 topics with engagement (D-03 fallback) no article can show a nonzero engagement part. Re-run `$HOME/.cache/myfeeder-phase12/sc3.sh` once engaged articles match topics |
| Soak | pass: 10 min, polling registered for 46 feeds (pre-deploy 46), 0 ERROR; 4 per-feed poll WARNs (HTTP 429, I/O, parse, body size) |
| Dump | 11.6 MB (114 entries), `$HOME/.cache/myfeeder-phase12/myfeeder-pre-0.3.1.dump`, mode 600 |
| Earlier tags | v0.3.0 db5acd76ef52fc7bd82ec4b1a36bdc7b749907d2 and v0.2.1 5461d0a555e9daa8f0907e07660e5e51958d154c, unchanged |
| Failure path | not run |
