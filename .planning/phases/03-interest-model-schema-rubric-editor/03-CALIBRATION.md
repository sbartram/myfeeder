# Phase 3: Interest Question Calibration

Findings from the gated live spike `InterestCalibrationSpikeTest` (plan 03-08). It sends exactly the
Phase 4 request (`InterestQuestions.forRubric` + `ArticleStateBuilder.build`, D-12), one `judge()` call
per article plus three repeats. This file holds article titles, topic keys and numbers only. It never
includes the profile text, the topic descriptions or the key. The raw input and the per-article reports
live outside the repository under `$HOME/.cache/myfeeder-phase03/`.

## Run

| | v1 (shipped in 03-02) | v2 (chosen) |
|---|---|---|
| Date | 2026-09-23 | 2026-09-23 |
| Model | jev-1.13.0 | jev-1.13.0 |
| Articles | 20 (+3 repeats = 23 calls) | 20 (+3 repeats = 23 calls) |
| Labelled | 20 (4 high, 16 low; 64 high/low pairs) | 20 (4 high, 16 low; 64 high/low pairs) |
| Topics | 7 (`topic_1`..`topic_7`, weights 30, 30, 20, 20, 15, −20, −20) | same |
| Wording fingerprint | `6816f6b7` | `5ba35e63` |
| Jev timeout | 60s via `SPRING_AI_TYPESAFE_TIMEOUT` env override (see Notes) | same |

The input (`calibration-input.json`, outside the repo) holds 20 real articles. Task 1 prepared the file and
the user completed it. The user supplied the profile, the topics and every label, and authorized the
billed runs ("run it").

## Numbers

| Statistic | v1 | v2 | A6 threshold |
|---|---|---|---|
| Stddev of normalized profile score | 0.181 | 0.178 | ≥ 0.15 |
| Median profile confidence | 0.895 | 0.910 | ≥ 0.5 |
| Share of nouls in [0.35, 0.65] | 0.007 (1 of 140) | 0.014 (2 of 140) | < 0.5 |
| High-over-low label agreement (ties ½) | 0.859 (55 of 64) | 0.859 (55 of 64) | ≥ 0.75 |
| Max repeat delta (points / norm. profile / noul) | 1.3 / 0.005 / 0.020 | 0.6 / 0.002 / 0.010 | (none) |

Topic nouls on the articles that obviously match a topic (the reason v2 was tried):

| Article | Label | Topic | v1 noul | v2 noul |
|---|---|---|---|---|
| Anthropic chief scientist Jared Kaplan warned AI training could trigger an intelligence explosion | high | `topic_2` | 0.15 | 0.34 |
| Opus 5.5 vs GPT-6 Sol: 3D Pelican riding bike test in Blender | low | `topic_2` | 0.27 | 0.48 |
| Opus 5.5 built me a website that turns any photo into ASCII art | low | `topic_2` | 0.10 | 0.17 |
| The ‘QwenBook’ Laptop Can Run Every OS at Once, and It May Be PC Fans’ Worst Nightmare | low | `topic_7` | 0.14 | 0.26 |
| Elon Musk Eyed as Investor in Paramount’s Disastrous Takeover of Warner Bros: Report | low | `topic_6` | 0.57 | 0.57 |

Every other noul, on every other article, is 0.00–0.05 in both versions. In both versions the only topic
that crosses 0.5 is `topic_6` on the Musk/Paramount article (hinge 0.14 × −20 = −2.8 points). In practice
the ranking over this set comes from the profile score alone.

Ranking (points) is the same in both versions apart from two adjacent "low" rows swapping places:
Kaplan 57.5/56.0, Moon rock 43.8/41.8, Opus pelican 37.5/36.5, QwenBook 35.3/34.5, Opus ASCII and
Siri ~31–34, "Americans worried about AI" 26.3/26.5, Putin G20 15.3/15.5, then everything else below 5.1.
The 9 lost pairs are the same in both versions: the "high" FBI breach article (5.0 points) loses to 5 lows, and
"Americans worried about AI" (26.3) loses to 4 lows.

## Verdict

| Threshold | v1 | v2 |
|---|---|---|
| Stddev of normalized profile ≥ 0.15 | PASS (0.181) | PASS (0.178) |
| Median profile confidence ≥ 0.5 | PASS (0.895) | PASS (0.910) |
| Mid-band noul share < 0.5 | PASS (0.007) | PASS (0.014) |
| Label agreement ≥ 0.75 | PASS (0.859) | PASS (0.859) |

**Overall: PASS for both versions. v2 ships.** Both versions pass every A6 heuristic, and the
differences between them are within the repeat noise (max repeat delta 0.6–1.3 points). Label agreement
is identical. The profile question and levels are unchanged and produce a meaningful spread. v1 failed
none of the A6 thresholds; it was revised by user decision because its topic nouls under-fired on obvious
matches. v2 moves every obvious-match noul up (roughly doubling them) and leaves unrelated
articles at 0.00–0.05, so it is the better topic question. It still does not make obvious matches
cross 0.5 (see Notes).

## Wording versions

Only the topic constants changed. `PROFILE_QUESTION` and `PROFILE_LEVELS` are the same in v1 and v2.

**v1** (plan 03-02, fingerprint `6816f6b7`):
- `TOPIC_QUESTION`: "Is the article in `title` and `summary` primarily about `topic`?"
- `TOPIC_WHEN_TRUE`: "The article's main subject is `topic`"
- `TOPIC_WHEN_FALSE`: "The article's main subject is something else, even if it mentions `topic` in passing"

**v2** (this plan, fingerprint `5ba35e63`):
- `TOPIC_QUESTION`: "Is the article in `title` and `summary` substantially about `topic`?"
- `TOPIC_WHEN_TRUE`: "`topic` is the article's subject or a significant part of what the article discusses"
- `TOPIC_WHEN_FALSE`: "The article is about something else; `topic` appears only as a brief mention or not at all"

Why: "primarily / main subject" set a high bar. An article that discusses a topic at length without it
being the single headline subject was judged "no" (for example `topic_2` at 0.15 on the Kaplan article).
v2 still follows the vendor guidance (research Pattern 3): one dimension, yes means high, backtick state
references, and no numbers. It widens the "yes" case to "a significant part" and keeps the passing
mention in the "no" case.

## Chosen wording

**v2** is in `InterestQuestions` (commits `3bba9fd` RED test, `346e74c` GREEN wording). The topic preview
and the Phase 4 scorer inherit it through the shared builder (D-12). `InterestQuestionsTest` pins the new
phrasing (`topicAsksWhetherTheArticleIsSubstantiallyAboutTheTopic`).

**Follow-up (outside this plan's file list, not changed here):** the Interests dialog help text at
`src/main/frontend/src/components/InterestsDialog.tsx:31` still says "Describe each topic as what an
article is *primarily* about". Update it to match v2, for example "what an article is substantially
about". It is logged in `deferred-items.md`.

## Notes for Phase 4/5

1. **Production Jev timeout is too short for scoring.** `spring.ai.typesafe.timeout` is `5s` in
   `application.yaml`. A profile + 7-topic `judge()` call averaged about 2.6s, and the first (cold)
   call took more than 5s. The spike ran with a `SPRING_AI_TYPESAFE_TIMEOUT=60s` env override (a Rule 3
   deviation in 03-08). The v2 run's 23 calls finished in about a minute. Phase 4 needs a longer scoring
   timeout (15–30s is a reasonable start) or a separate timeout for the background scorer. The preview
   (one topic) can keep a shorter one. Check how a client timeout surfaces (exception type) against
   the Resilience4j `jev` retry list, so a timeout does not simply re-send the same slow call.
2. **Topics still under-fire.** v1 → v2 roughly doubled obvious-match nouls (`topic_2` 0.15 → 0.34 on
   Kaplan, 0.27 → 0.48 on the Opus pelican article, 0.10 → 0.17 on Opus ASCII; `topic_7` 0.14 → 0.26 on
   QwenBook), but none crosses 0.5. Topics add no points on this set, so the blend is effectively
   `100 × profile_match`. The plan allows at most two wording revisions and the user authorized one v2
   run, so there is no v3. Options for Phase 4/5 (OPS-02 tuning): (a) a v3 topic wording that asks
   whether the article's subject *falls within* `topic`; (b) richer topic descriptions from the user;
   (c) revisit the hinge midpoint (0.5) in the scoring model. (c) changes the settled REQUIREMENTS scoring model,
   so it is a user decision. Watch the label trade-off: in v2, `topic_2` rose most on a
   user-labelled *low* article (Opus pelican, 0.48) and less on the *high* Kaplan article (0.34).
   Stronger topic firing could therefore *lower* label agreement unless the `topic_2` description or
   weight says the user does not want lightweight model demos.
3. **The FBI breach "high" article sits at 5.0 points in both versions** (normalized profile 0.05 at
   confidence 0.83–0.84). The model confidently judges it outside the profile, and no topic fires. This
   is a profile-text gap (security incidents are not described as an interest), not a wording problem.
   The user should add the subject to the profile or add a topic. The "Americans worried about AI" high
   (26.3) is the same kind of borderline profile miss.
4. **TypeSafe outage during the first v2 attempts.** Earlier v2 runs failed on vendor errors: HTTP 503,
   then 529 (overloaded). Calls that succeeded before a run aborted may have been billed, so the v2
   spend may be more than the one successful 23-call run. Check the TypeSafe dashboard if it matters.
   Before the single authorized retry, an unauthenticated probe (no judge call) showed the API
   answering 404/403 in 0.4–7s. The retry then succeeded on the first attempt.
5. **Confidence is lowest where it matters.** The median confidence (0.91) is dominated by clearly
   off-profile articles (0.9–1.0). The top-ranked articles have confidence 0.29–0.69. Phase 5 should not
   use profile confidence as a display filter for high scores.
6. **`MAX_SUMMARY_CHARS = 1500` looked sufficient.** The repeat deltas are tiny (≤ 0.010 noul, ≤ 0.6
   points in v2), and no misjudgement traced to truncated text. The two label misses above are profile
   gaps, not missing article text.
7. **Tier-threshold hint for OPS-02** (20 articles, profile-only in practice): points cluster at ~56,
   ~31–42, ~26, ~15, then < 6. A starting guess is "high" ≥ 40, "medium" 25–40, "low" < 25. Re-derive it
   from the real Phase 4 distribution once topics contribute.
