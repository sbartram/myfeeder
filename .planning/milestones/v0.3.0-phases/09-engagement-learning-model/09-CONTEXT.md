# Phase 9: Engagement Learning Model - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Engagement recorded by Phase 8 (`article_engagement`) becomes a second derived term in `InterestScoreQueries.LEARNED_CTE`: a small, capped, thumbs-overridable implicit up-vote on the topics an engaged SCORED article matched. It is computed at query time, with no Jev calls and no writes to `interest_topic.weight`. The three constants come from committed yaml and are validated at startup. The calibration replay and its drift guard are regenerated in the same change (CAL-01). Everything that reads learned values moves with the CTE: the vote effect before/after, `LearnedLimit` and `GET /api/interest/topics/learned`.

Requirements: LRN-01, LRN-02, LRN-03, LRN-04, LRN-05, CAL-01.

Out of this phase: the "Why N?" / Interests UI split and the Priority "Ranking changed" reaction (Phase 10), gap discovery (Phase 11), and calibration from prod data plus the release (Phase 12).

</domain>

<decisions>
## Implementation Decisions

### Carried forward (settled before this discussion; do not reopen)
- Engagement skips topics whose **base** weight is negative. Topics with base ≥ 0, including base 0, qualify (LRN-03).
- Engagement has its own additive cap below the thumbs cap (20). Thumbs take their share first.
- Each engaged article counts once, at its strongest kind: save (STAR / BOARD / RAINDROP, all one save weight, with no per-kind weights) > open (OPEN_ORIGINAL). Collapse to one `MAX` strength per article **before** joining topics.
- Any thumbs vote on an article, narrowed or not, removes that article's engagement contribution on every topic (`NOT EXISTS` on `article_feedback`). Removing the vote restores it.
- Only `article_score.status = 'SCORED'` articles count. Engaged-but-unscored articles stay dormant (they are never made eligible for scoring).
- `WITH learned AS` stays the first token of `LEARNED_CTE` (the drift guard looks for it). `thumbs_applied` and `engagement_applied` are computed in SQL, with engagement by subtraction. Plan test-first, including a real-Postgres grid property test for the exact split.
- New yaml keys also go in the test yaml and the dev overlay (`DevProfileConfigTest`). New psql `-v` variables use the same names as the JdbcClient parameters. Kind names and SQL aliases must avoid words the replay's write-keyword check rejects.
- DTO rule: append fields, never rename.

### Starting values and the zero rule (LRN-05)
- **D-01:** Main `application.yaml` ships conservative values until Phase 12 calibrates them: open **0.25**, save **0.5**, engagement cap **8**. Keys live under `myfeeder.interest.blend` (exact names are Claude's discretion; research suggests `engagement.{open-weight, save-weight, cap}`).
- **D-02:** Startup validation: **`cap = 0` disables engagement learning**, whatever the weights are, and is always valid. When cap ≠ 0, require `0 ≤ open < save < 1` **and** `0 < cap < learned-cap`. Anything else refuses to start with a fixed-text message.
- **D-03:** Save is **strictly below 1**, so an explicit 👍 always outweighs any single implicit signal.
- **D-04:** The test `application.yaml` carries the **same values as main** (0.25 / 0.5 / 8), so the dev overlay needs nothing extra. Grid, latency and "zero equals v0.2.1" tests bind their own values where needed. Existing `InterestScoreQueriesTest` fixtures have no engagement rows, so they are unaffected.
- **D-05:** "With engagement set to zero, scores and Priority order match v0.2.1 exactly" is proven with cap = 0 (and, separately, with engagement rows absent).

### Engagement arithmetic
- **D-06:** Engagement works like a fractional vote through `learnRate`: `eng_raw = learnRate × Σ_articles strength × hinge`, then `eng = LEAST(cap, eng_raw)` (positive only, so there is no lower bound below 0). At learnRate 2, one full-hinge save adds 1.0 point and one open adds 0.5.
- **D-07:** The hinge is the same as for votes: `max(0, (noul − 0.5) × 2)`. Only matched topics move, in proportion to match strength.
- **D-08:** There is no age window. Any SCORED engaged article counts whatever its age or read state, the same as thumbs. Retention deletes remove it by cascade, and decay stays deferred (ENG-F8).
- **D-09:** **One clamp on the full sum.** `w_thumbs = clamp(base + thumbs)` (today's `w`), and `w = clamp(base + thumbs + eng)`, with the existing sign clamp and ±50 applied once to the whole sum. Down-votes clamped past zero must be "paid back" before engagement shows, so explicit thumbs dominate. Split: `thumbs_applied = w_thumbs − base` and `engagement_applied = w − w_thumbs`, so `base + thumbs_applied + engagement_applied = w` exactly. For a negative-base topic, eng is 0 before the sum.

### How far the split reaches in Phase 9
- **D-10:** **Append split fields now** to the backend DTOs that carry learned values. `TopicContribution` gets thumbs and engagement applied parts. `TopicWeight` gets the engagement raw and capped parts next to the existing thumbs `learnedRaw`/`learned`. `TopicLearned` / `GET /api/interest/topics/learned` and `FeedbackResult` effects get engagement where they expose learned values. Existing fields keep their current names and mean the **combined** learned part (`w − base`), so the current UI stays correct. Phase 10 only renders the split. Exact field names are Claude's discretion.
- **D-11:** `LearnedLimit` gains an appended **`ENGAGEMENT_CAP`** value. Precedence: `LEARNED_CAP` (thumbs `|learnedRaw| ≥ learned-cap`) → `ENGAGEMENT_CAP` (engagement raw ≥ engagement cap, only when cap > 0) → `SIGN_CLAMP` / `WEIGHT_RANGE`, judged on `base + thumbs + engagement` → `NONE`. The frontend `LearnedLimit` type gains the value. `effectNote` / `TopicRow` may fall through to their default for now, and Phase 10 words it.
- **D-12:** The vote effect before/after is read through the same topic-weights query, so "before" includes the engagement that the vote then replaces.

### Latency budget and shipping
- **D-13:** Latency uses **record + generous guard**. A Testcontainers test seeds about 20k `article_engagement` rows (a realistic mix of kinds, scored and unscored, with and without votes) and measures the Priority page query, the per-article breakdown and the topic-weights query against a no-engagement baseline. The numbers and an `EXPLAIN (ANALYZE)` go into the phase's VERIFICATION. The automated assertion catches only catastrophic plans (for example < 3× baseline) so that it does not flake. An index is added only if EXPLAIN shows it is needed; that would need a new migration and is a planner decision flagged to the user.
- **D-14:** **No release in Phase 9.** Work happens on a feature branch and merges to main with `--no-ff`, with no tag, image or deploy. Prod stays on v0.3.0 (capturing engagement) until Phase 12 ships calibrated v0.3.1. Phase 12 calibrates by read-only replay, which needs no deploy.

### Planning-time resolutions (research open questions, confirmed by the user 2026-09-30)
- **D-15:** The existing `learned` field on `TopicLearned` and `TopicEffect` keeps its "capped, before the clamp" meaning and becomes combined: `learned = thumbs capped + engagement capped`. This is byte-identical to v0.2.1 at zero engagement, and it is the same sum `LearnedLimit` judges (D-11). Append the capped parts (thumbs learned and engagement learned) next to it. D-10's "(w − base)" describes only the `learnedWeight` fields (`TopicContribution`, `InterestBreakdown.Row`), which already hold `w − base`. (Research Open Question 1, Pitfall 5.)
- **D-16:** `InterestBreakdown.Row` also gets the thumbs-applied and engagement-applied split fields in Phase 9, appended with `NON_NULL` so PROFILE rows omit them. Phase 10 only renders them. (Research Open Question 2.)
- **D-17:** The latency guard refines D-13's "for example < 3× baseline". For each query (Priority first page, per-article breakdown, topic weights), assert `extendedMedian ≤ 10 × baselineMedian + 250 ms`, using the median of 5 warm runs after 3 warm-ups. The measured numbers and an `EXPLAIN (ANALYZE)` still go into the phase VERIFICATION. (Research Open Question 3, Pitfall 2.)

### Claude's Discretion
- Exact yaml key names, `MyfeederProperties.Blend` nesting and how validation is wired (e.g. `@Validated` + a custom check, or an `InitializingBean`), provided startup fails with a clear fixed-text message.
- CTE names (`engaged`, `eng_learned`, …) and new DTO field names, within the replay keyword constraint.
- Grid test value ranges (base, vote sums and engagement sums that cover each clamp branch, both caps and ±50).
- How the replay script exposes the new `-v` variables and validates them before connecting (same pattern as the existing ones).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and requirements
- `.planning/ROADMAP.md` § Phase 9 — goal, 5 success criteria, notes (test-first, split by subtraction, zero-rule collision, research flag)
- `.planning/REQUIREMENTS.md` — LRN-01..LRN-05, CAL-01; ENG-F6/ENG-F8 are deferred and must not leak in
- `.planning/STATE.md` § Accumulated Context — the v0.3.0 settled decisions

### Research
- `.planning/research/SUMMARY.md` — Open Decisions 1–2 (now settled), the `LEARNED_CTE` pipeline (`engaged` → `eng_learned` → `eff` → `eff2` → `contrib`), and the pitfall list for Phase 9
- `.planning/research/PITFALLS.md` — pitfalls 1, 2, 3, 4, 9, 11, 12, 17 (broken sums, caps and clamps, drift, latency)
- `.planning/research/ARCHITECTURE.md` — learned-model extension design

### Prior phases
- `.planning/phases/08-engagement-capture/08-CONTEXT.md` — V7 schema, engagement kinds, sticky and Forget semantics
- `.planning/milestones/` — v0.2.1 Phase 6 (thumbs learned model) and Phase 7 (calibration replay, drift guard, D-14 "never tune via Helm/env")

### Project docs
- `CLAUDE.md` § Interest Ranking, Thumbs feedback, Tier thresholds and tuning — the learned model, the replay script and the test-yaml invariants (profile-points 100 / tiers 70/40 in the test yaml)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `repository/InterestScoreQueries.LEARNED_CTE` (lines ~94–118): the `learned → eff → eff2` pipeline to extend. `blendCte` / `contrib` (~line 309) already computes `learned_applied = w − base`. `learnedSql()` (~line 295) is the **single binding point** for learned parameters; bind the new ones there.
- `repository/ArticleEngagementStore` (Phase 8): the engagement table and kinds. `model/EngagementKind` holds the kind names.
- `service/LearnedLimit.of(TopicWeight, cap)`: extend its signature for the engagement cap and the full-sum checks.
- `config/MyfeederProperties.Interest.Blend`: `profilePoints`, `learnRate`, `learnedCap`, `tiers`. The new engagement constants go here.
- `scripts/interest-calibration-replay.sql` + `.sh`: the verbatim replay with `-v` variables validated before connecting. `InterestCalibrationReplaySqlTest` is the drift guard.

### Established Patterns
- Constants are cast to `float8` in SQL (an untyped unary minus is ambiguous in Postgres).
- Values are rounded to 6 decimals in `topicWeights` / breakdown, and `LearnedLimit` is judged on those 6-decimal values.
- Every blend constant applies at query time, so changing one needs no Jev re-score.
- `DevProfileConfigTest` requires test yaml + dev overlay to resolve every main key to main's value.

### Integration Points
- `ArticleFeedbackService` (vote effects before/after), `InterestController` `GET /topics/learned` → `TopicLearned`, `FeedbackResult`, `ScoreBreakdowns` / `TopicContribution` for "Why N?".
- Frontend types: `src/main/frontend/src/types/index.ts:56` (`LearnedLimit` union), `api/interest.ts`, `utils/feedback.ts` `effectNote` (default branch serves as the fallback), `components/TopicRow.tsx:222`.

</code_context>

<specifics>
## Specific Ideas

- Worked clamp example to encode in the grid test: base 10, thumbs learned −20, engagement +8 → `w_thumbs = 0`, `w = clamp(−2) = 0`, `engagement_applied = 0`.
- At the chosen values, a topic reaches the engagement cap after about 8 strong saves, or about 16 strong opens.

</specifics>

<deferred>
## Deferred Ideas

- The Interests learned line split with contributing-article counts and an at-cap flag (ENG-F6): future; Phase 9 only appends backend fields.
- Engagement decay / rate normalization (ENG-F8): future.
- User-facing wording for `ENGAGEMENT_CAP` in toasts and Interests: Phase 10.
- `contrib AS NOT MATERIALIZED` in the breakdown topics query (research Pitfall 9, Open Question 5): a pre-existing inefficiency that the latency budget does not need. It is deferred by user decision on 2026-09-30. Phase 9 records it as an observation in VERIFICATION and does not change the CTE or the replay for it.

### Reviewed Todos (not folded)
- `2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` (Tune Raindrop resilience): it matched only on keywords, and PROJECT.md keeps carried-forward cleanup out of this milestone.

</deferred>

---

*Phase: 09-engagement-learning-model*
*Context gathered: 2026-09-30*
