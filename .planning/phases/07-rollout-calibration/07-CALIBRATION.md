# Phase 7: Blend and Tier Calibration

Calibration of the Priority blend and the badge tiers against the real prod score distribution (OPS-02,
D-09 to D-12). Every number here comes from `scripts/interest-calibration-replay.sh`, which runs the
verbatim `InterestScoreQueries` blend in read-only sessions (`default_transaction_read_only=on`). This
file holds article titles, article ids, `topic_<id>` keys and numbers only. It never includes the
profile text, the topic descriptions, the topic names or any key. The raw replay output lives outside
the repository under `$HOME/.cache/myfeeder-phase07/replay/`. The article titles below become public
when this file merges to main, because the origin is a public GitHub repo.

## Run

| | Value |
|---|---|
| Date | 2026-09-28 (replays 23:56Z to 23:58Z) |
| App version | 0.2.0 (Helm rev 18) |
| Backfill verdict | `07-BACKFILL.md` Overall PASS (legacy backlog drained, 192 SCORED, 0 FAILED, breaker CLOSED) |
| Scored unread | 192 |
| Scored articles in the 14-day window (read or unread) | 192 |
| Votes | 0 (0 up, 0 down) |
| Live constants | profile-points 100, learn-rate 2, learned-cap 20, tiers 70 / 40 |
| Topics | 7 (`topic_1`..`topic_7`, base weights 30, 30, 20, 20, 15, -20, -20) |
| Cross-check (replay vs `GET /api/articles/{id}` `interestScore`) | 5 of 5 matched (top 5 article ids at 100 / 70 / 40, no mismatch) |
| Candidates replayed | 22 (pass A 3, pass B 9, extra 10) |

The cross-check proves the replay equals the app in prod: at the live constants the replayed badge for
articles 25988, 26032, 25873, 26016 and 26025 equals the badge the app serves for each of them.

## Baseline (100 / 70 / 40)

Tiers follow `tierOf`: high is a badge at or above 70, neutral is at or above 40 and below 70, low is below 40.

| Tier | Articles | Share |
|---|---|---|
| high (>= 70) | 27 | 14.1% |
| neutral (40..69) | 39 | 20.3% |
| low (< 40) | 126 | 65.6% |

| P10 | P25 | P50 | P60 | P75 | P80 | P85 | P90 | P95 |
|---|---|---|---|---|---|---|---|---|
| 0 | 0 | 20 | 32 | 53 | 63 | 67 | 79 | 90 |

| Statistic | Value |
|---|---|
| at_zero (badge 0) | 58 (30.2%) |
| at_100 (badge 100) | 1 |
| Ties at the minimum raw score | 44 |

The distribution is not the low-end compression the Phase 3 spike predicted (Pitfall 8, assumption A5).
The median badge is 20 and the top decile reaches 79 and above. The high share is already inside the
D-10 band. Neutral is too thin (20.3%) because the band 40..69 is wide in the middle of a spread-out
distribution while the 20..39 range, which holds the weaker profile matches, falls into low. The at_zero
share is 30.2%, well under the 60% at which neutral 30-40% would become unreachable.

## Candidates

Pass A: 100, 125 and 150 at 70 / 40. Pass B: for each profile-points value, the integer tier pairs
(P85, P55), (P90, P60) and (P80, P50) of that value's display score (P55 taken as the integer midpoint of
P50 and P60, since the summary reports no P55). Extra: pp-100 threshold pairs around the band edges that
the pass B numbers called for. D-10 target: high 10-20% and neutral 30-40% of scored unread.

| pp | high | neutral | high% | neutral% | low% | P50 | P80 | P90 | at_zero | at_100 | meets D-10? | pass |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 100 | 70 | 40 | 14.1 | 20.3 | 65.6 | 20 | 63 | 79 | 58 | 1 | no (neutral) | A (baseline) |
| 100 | 67 | 26 | 16.1 | 29.2 | 54.7 | 20 | 63 | 79 | 58 | 1 | no (neutral) | B (P85, P55) |
| 100 | 79 | 32 | 10.9 | 29.2 | 59.9 | 20 | 63 | 79 | 58 | 1 | no (neutral) | B (P90, P60) |
| 100 | 63 | 20 | 20.3 | 30.2 | 49.5 | 20 | 63 | 79 | 58 | 1 | no (high) | B (P80, P50) |
| 100 | 67 | 25 | 16.1 | 30.2 | 53.6 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 100 | 67 | 24 | 16.1 | 31.8 | 52.1 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 100 | 67 | 22 | 16.1 | 32.8 | 51.0 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 100 | 65 | 24 | 19.3 | 28.6 | 52.1 | 20 | 63 | 79 | 58 | 1 | no (neutral) | extra |
| 100 | 69 | 24 | 14.6 | 33.3 | 52.1 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 100 | 70 | 25 | 14.1 | 32.3 | 53.6 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 100 | 70 | 24 | 14.1 | 33.9 | 52.1 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 100 | 70 | 23 | 14.1 | 34.4 | 51.6 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| **100** | **70** | **22** | **14.1** | **34.9** | **51.0** | 20 | 63 | 79 | 58 | 1 | **yes (proposal)** | extra |
| 100 | 70 | 21 | 14.1 | 35.4 | 50.5 | 20 | 63 | 79 | 58 | 1 | yes | extra |
| 125 | 70 | 40 | 23.4 | 16.7 | 59.9 | 24 | 78 | 98 | 58 | 18 | no (both) | A |
| 125 | 84 | 32 | 15.1 | 30.2 | 54.7 | 24 | 78 | 98 | 58 | 18 | yes | B (P85, P55) |
| 125 | 98 | 40 | 10.4 | 29.7 | 59.9 | 24 | 78 | 98 | 58 | 18 | no (neutral) | B (P90, P60) |
| 125 | 78 | 24 | 20.3 | 30.2 | 49.5 | 24 | 78 | 98 | 58 | 18 | no (high) | B (P80, P50) |
| 150 | 70 | 40 | 27.6 | 16.7 | 55.7 | 29 | 94 | 100 | 58 | 30 | no (both) | A |
| 150 | 100 | 38 | 15.6 | 29.7 | 54.7 | 29 | 94 | 100 | 58 | 30 | no (neutral) | B (P85, P55) |
| 150 | 100 | 48 | 15.6 | 24.5 | 59.9 | 29 | 94 | 100 | 58 | 30 | no (neutral) | B (P90, P60) |
| 150 | 94 | 29 | 20.3 | 30.2 | 49.5 | 29 | 94 | 100 | 58 | 30 | no (high) | B (P80, P50) |

Raising profile-points saturates badges at 100 (18 at pp 125, 30 at pp 150) without improving the
neutral share, so no pp above 100 is worth the reweighing of profile versus topics.

## Stability cross-check

The same statistics over all SCORED articles inside the 14-day window, read or unread (Pitfall 9).

| Constants | Scored in window | high% | neutral% | low% | P50 | P80 | P90 | at_zero | at_100 |
|---|---|---|---|---|---|---|---|---|---|
| Baseline 100 / 70 / 40 | 192 | 14.1 | 20.3 | 65.6 | 20 | 63 | 79 | 58 | 1 |
| Proposal 100 / 70 / 22 | 192 | 14.1 | 34.9 | 51.0 | 20 | 63 | 79 | 58 | 1 |

The window population equals the unread population today, because no scored article has been read yet
(calibration ran minutes after the drain, per D-12). As the user reads, high articles leave "scored
unread" first, so the unread high share will drift below 14.1% over time. The window figures are the
stable reference for a later re-check.

## Learned model

Votes: 0 (0 up, 0 down).

| Topic | base | learned_raw | learned | effective | at_cap |
|---|---|---|---|---|---|
| `topic_1` | 30 | 0.000 | 0.000 | 30.000 | f |
| `topic_2` | 30 | 0.000 | 0.000 | 30.000 | f |
| `topic_3` | 20 | 0.000 | 0.000 | 20.000 | f |
| `topic_4` | 20 | 0.000 | 0.000 | 20.000 | f |
| `topic_5` | 15 | 0.000 | 0.000 | 15.000 | f |
| `topic_6` | -20 | 0.000 | 0.000 | -20.000 | f |
| `topic_7` | -20 | 0.000 | 0.000 | -20.000 | f |

D-12 assessment: there are no votes yet, so the learned model is inert and no topic is pinned at the
cap. There is no evidence of a problem, and learn-rate 2 and learned-cap 20 stay unchanged.

## Proposal

Keep profile-points 100 and tiers.high 70, and lower tiers.neutral from 40 to 22. Keep learn-rate 2 and
learned-cap 20.

- D-10: high 14.1% (inside 10-20) and neutral 34.9% (inside 30-40), low 51.0%.
- Threshold-only at pp 100, so the Priority order is unchanged. The top 20 and bottom 20 below are
  identical, row for row, to the baseline's.
- Ten candidates meet both bands (nine at pp 100, one at pp 125). Of the pp-100 ones, 100 / 70 / 22 is the
  closest to high 15% and neutral 35% (distance 0.9 points; the runner-up 100 / 70 / 21 is 1.0).
- High stays at 70, which already selects the top 14% and keeps "high" a real "read this" signal: the
  weakest article in the top 20 scores 79. Whether those titles deserve "read this" is the D-11 check
  at the checkpoint.
- Neutral 22 moves the weaker profile matches (badges 22..39, 28 articles) from low into neutral.
  Neutral 21..25 all fall inside the band, so the choice is not sensitive to one article.

Yaml lines 07-09 will write:

`src/main/resources/application.yaml` (under `myfeeder.interest`):

```yaml
    blend:
      profile-points: 100
      learn-rate: 2
      learned-cap: 20
      tiers:
        high: 70
        neutral: 22
```

`src/test/resources/application-dev.yaml` (under `myfeeder.interest`, so the dev overlay mirrors main;
the test `application.yaml` stays at 100 / 70 / 40 for `InterestScoreQueriesTest`):

```yaml
    blend:
      profile-points: 100
      tiers:
        high: 70
        neutral: 22
```

proposed-constants: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20

## Top 20 (proposal)

At 100 / 70 / 22, ordered like the Priority sort (raw desc, date desc, id desc). Badge tier: 20 high.

| Article id | Badge | Raw | Title |
|---|---|---|---|
| 25988 | 100 | 101.9 | Introducing Claude Sonnet 5.5, the second model in the Claude 5.5 family |
| 26032 | 98 | 98.5 | Anthropic Releases Its Second New AI Model in Less Than a Week |
| 25873 | 97 | 97.0 | Performance Improvements in JDK 27 |
| 26016 | 97 | 96.8 | Sonnet 5.5 vs Opus 5.5 on a from-scratch Rust decompressor: same correctness, a quarter of the price |
| 26025 | 97 | 96.7 | Sonnet 5.5 has been out for an hour. Has anyone gotten a chance to stress test it? |
| 26023 | 96 | 96.0 | Insane Claude Safeguards since Sonnet 5.5 release |
| 26051 | 92 | 92.1 | Opus 5.5 is weirdly human to interact with |
| 25989 | 92 | 92.0 | Sonnet 5.5 is out!! |
| 25896 | 92 | 91.9 | I stayed with Anthropic Max subscription even when it seemed like Anthropic was at their worst, thinking it will be better and I do not regret it. |
| 25998 | 90 | 90.0 | Sonnet 5.5 on Vals AI benchmark, if these hold true the $20 is insane value right now |
| 25991 | 90 | 89.8 | Anyone notice that Opus 5.5 seems to be very trigger happy |
| 25990 | 89 | 89.2 | Sonnet 5.5 available in Claude App |
| 26048 | 89 | 89.0 | September 2026 New Java Performance Tips |
| 25905 | 88 | 87.9 | Switched from Astra to Opus and I am blown away |
| 25863 | 84 | 84.0 | Opus 5.5 Ultracode |
| 25962 | 84 | 83.8 | Measured it: headless Claude Code (Agent SDK / claude -p) eats ~3x more of the 5-hour limit per token than interactive use (vscode) |
| 26008 | 83 | 82.6 | opus 5.5 usage is honestly great. ~7 agents nonstop since sunday reset, 76% left (20x) |
| 25877 | 82 | 82.3 | The code review gap I see in the AI-agent era |
| 26047 | 80 | 80.1 | Java Performance News September 2026 |
| 25923 | 79 | 79.3 | Opus 5.5 is sick. It can create a full video clip using nothing but HTML and JS. |

## Bottom 20 (proposal)

At 100 / 70 / 22, ordered raw asc, date desc, id desc (Pitfall 10). All 20 show raw 0.0 and badge 0 (low).
44 articles tie at the minimum raw score, so these 20 are simply the newest of those 44; 58 articles in
all show badge 0.

| Article id | Badge | Raw | Title |
|---|---|---|---|
| 26043 | 0 | 0.0 | Tennessee to execute a woman for first time in 200 years after governor denies clemency |
| 26042 | 0 | 0.0 | Pochettino: Manchester City verdicts show fans ‘lived through an era of deception’ |
| 26035 | 0 | 0.0 | ‘Spider-Man: Brand New Day’ May Be Re-Released With New Footage |
| 26033 | 0 | 0.0 | Samuel Alito steps aside in a major climate case amid scrutiny over oil stock holdings |
| 26027 | 0 | 0.0 | Eric Clapton, Trey Anastasio, John Mayer, Tedeschi Trucks Band and More Share the Stage at Crossroads |
| 26018 | 0 | 0.0 | To keep drug prices high, pharma has been piling up the patents |
| 26014 | 0 | 0.0 | Trump unveils plan for $15bn Iowa steel plant he calls biggest in US history |
| 26012 | 0 | 0.0 | Farm Aid 2026: Willie Nelson and Neil Young Share the Stage, Dave Matthews Jams with I’m with Her, Margo Price, Jesse Welles and More |
| 26009 | 0 | 0.0 | Judge sets $5m bond for third suspect in killing of Black woman hung from tree in Mississippi |
| 26020 | 0 | 0.0 | Kalshi loses again as judges rule prediction markets must obey gambling laws |
| 26003 | 0 | 0.0 | Scientists Find Threat of Toxic Metal Exposure Lingers Long After a Wildfire Goes Out |
| 26005 | 0 | 0.0 | NFL asks DHS to take down post likening violent tackles to treatment of immigrants |
| 25997 | 0 | 0.0 | Dengue fever outbreak prompts Florida counties to declare state of emergency |
| 25983 | 0 | 0.0 | F1 in Azerbaijan: That was almost a close-run thing |
| 25982 | 0 | 0.0 | Arizona frat suspended after allegedly making new recruits swallow live goldfish |
| 25981 | 0 | 0.0 | A ‘Wildly Unusual’ Outbreak of Dengue is Hitting Florida Hard As Several Counties Declare State of Emergency |
| 25977 | 0 | 0.0 | FBI co-deputy director Andrew Bailey steps down amid agency shake-up |
| 25973 | 0 | 0.0 | Andrew Garfield’s Stealth Robin Hood Movie Is Already Coming to Digital |
| 25969 | 0 | 0.0 | Dead Mouse Found in Jar of Mezzetta Peppers Prompts Recall |
| 25970 | 0 | 0.0 | Trump’s deportations to third countries violate human rights, UN experts warn |

## Verdict

| D-10 criterion | Proposal 100 / 70 / 22 | Met? |
|---|---|---|
| High 10-20% of scored unread | 14.1% | yes |
| Neutral 30-40% of scored unread | 34.9% | yes |

## Approval
