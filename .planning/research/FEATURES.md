# Feature Research

**Domain:** Implicit-feedback (engagement) learning added to an explainable, query-time interest ranker in a self-hosted, single-user feed reader (myfeeder v0.3.0 Engagement Learning)
**Researched:** 2026-09-29
**Confidence:** MEDIUM. The competitor behavior comes from vendor docs and blogs (Feedly, NewsBlur, Inoreader, Google/YouTube/Gmail Help, Instagram, Artifact coverage) and the academic results are well established (Joachims 2005 on clicks, Hu/Koren/Volinsky 2008 on implicit feedback, the feedback-loop and position-bias literature). The research seam tags plain web search LOW and cross-checked findings MEDIUM. The table-stakes, anti-feature and weighting calls below are product judgment built on those facts plus a read of the current code (`InterestScoreQueries.LEARNED_CTE`, `FeedbackNotice`, `ReadingPane`, `useKeyboardShortcuts`). The doc says so wherever that applies.

**Scope:** only the new v0.3.0 features. The v0.2.1 ranking (Jev judging, blend CTE, Priority view, badge, "Why N?", thumbs, learned CTE, Interests learned line, "Create topic from article") is treated as existing infrastructure.

---

## How the reference products handle implicit feedback (condensed)

| Product | Implicit signals used | Weighting vs explicit | Explainability of implicit learning | Undo / control |
|---------|----------------------|-----------------------|-------------------------------------|----------------|
| **Feedly Leo** | A **Like-Board** priority learns by example from articles **saved to boards**. Secondary reviews claim Leo also learns from opens and skips; Feedly's own docs only document board saves. | Saves are the documented implicit signal. Explicit "Less like this" (mute a topic Leo linked to the article) is the strongest control. | The priority label on the article says which priority (for example the Like-Board) selected it. It gives no per-signal points. | Remove articles from the board, or delete or pause the priority. Mute filters are managed on one page. |
| **NewsBlur** | None. Its 2011 "Explaining Intelligence" post says passive training from implicit preferences was the original goal, but it shipped **explicit-only** classifiers so that "nothing mysterious is happening". Implicit learning has not shipped in 15 years. | n/a | Inline highlights, pills and a precedence banner. | "Manage Training" lists every classifier, and all of them are reversible. |
| **Inoreader Sort by Magic** | Implicit **feed-level** affinity: articles from feeds you read more often rank higher, on top of global popularity. | Implicit only. | Only a popularity indicator. No personal "why". | Only a sort toggle. |
| **Google News** | Click history builds an interest profile. Skips and author/outlet clicks are also read. | Explicit "More/Fewer stories like this", "Fewer from this source" and follow/unfollow topics sit on top. | Little per-article explanation. | You can choose which Google activity feeds personalization. |
| **YouTube** | Watch history. In 2012 it **moved from clicks to watch time because clicks rewarded clickbait**. | Explicit "Not interested" and "Don't recommend channel" override. | Minimal. | **Remove an item from history**, after which it no longer affects recommendations. **Pause history**. Turning history off disables personalized recommendations. |
| **Gmail importance** | Opens, replies, stars, archive/delete, sender relationship, keywords you read. | Clicking the marker (explicit) trains it and overrides the prediction. | **Hovering the marker gives a one-line reason** (for example "you often read messages with these words"). | Toggle the marker per message. Learning can be switched off. |
| **Artifact (2023–24)** | Clicks, dwell/read time and shares. | Explicit dislike and a "clickbait" flag were added later **because implicit clicks alone misfired**. | A **Stats view** showing the categories you read, recent articles per category and your top publishers. | Explicit dislike and clickbait flag. |
| **Instagram "Your Algorithm"** (Dec 2025, extended through 2026) | All engagement. | Explicit add/remove of topics overrides. | An **AI summary of the topics it thinks you like**, plus a "From Running"-style label on each post. | Add or remove topics, and **Reset suggested content** (a cold restart). |

**Academic grounding (MEDIUM–HIGH):**
- **Joachims et al. (SIGIR 2005):** clicks are informative but biased. They are unreliable as *absolute* relevance judgments, but *relative* preferences derived from them are reasonably accurate. **Position and trust bias** mean people click what is ranked first.
- **Hu, Koren & Volinsky (ICDM 2008):** implicit feedback is a *preference* with *varying confidence*. An observed interaction is a positive preference whose confidence grows with interaction strength. A missing interaction is weak, low-confidence evidence, not a negative. Some later work finds it better to model each feedback type separately and then combine.
- **Feedback-loop / bias-amplification surveys (Chen et al., TOIS 2023, and others):** training a ranker on clicks from its own ranking produces a "rich get richer" Matthew effect. Top-ranked items get more exposure, then more clicks, then rank higher.

**What this means for myfeeder:**
1. **The user's intuition is well supported.** Deliberate, post-preview actions are among the most reliable implicit signals: opening the original after reading the summary in the reading pane, starring, adding to a board, sending to Raindrop. The decided scope already drops the noisy signals (dwell, selection, auto-mark-read, reader view).
2. **Everyone ranks explicit above implicit, and saves above clicks.** No product lets implicit signals outvote an explicit statement.
3. **The distinctive risk is self-reinforcement.** Opens that happen inside the Priority view are partly caused by the ranking. A small per-article weight, a separate lower cap and positive-only learning are the standard mitigations, and all three are already decided.
4. **Explainability of implicit learning is weak across the industry.** Gmail's one-line reason and Instagram's topic summary are the best examples. myfeeder's exact point arithmetic makes "votes vs engagement" an unusually strong differentiator for little extra cost.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Without these, engagement learning feels creepy, wrong or irreversible. The complexity estimates assume the existing v0.2.1 infrastructure.

| # | Feature | Why Expected | Complexity | Notes / dependencies on existing code |
|---|---------|--------------|------------|---------------------------------------|
| E1 | **Capture "opened the original"** from `o`, the "↗ Open Original" toolbar button(s) and the header link, as one idempotent OPEN per article | This is the core signal the user named. Every implicit system logs the action once and treats repeats as no extra evidence (Hu/Koren: repeats raise confidence, but here one count per article is decided). | LOW–MEDIUM | Opening is **client-only today**: `ReadingPane.handleOpenOriginal` and `useKeyboardShortcuts` case `'o'` both call `window.open` and never touch the server. A new endpoint (for example `PUT /api/articles/{id}/engagement/open`, JSON-only like feedback) is needed. **Call `window.open` synchronously first, then fire the request without awaiting it**, or popup blockers will eat the tab. A retry-free fire-and-forget is fine: a lost open is harmless. **Decision to flag:** `handleContentClick` also opens *in-body* links. Recommendation: do **not** count those in v1. On aggregator feeds (HN, Lobsters) the body links are "Comments" and third-party URLs, not the article. Count only `o`, the Open Original buttons and the title link. |
| E2 | **Capture saves server-side** in the existing mutation paths: star (`PUT` article state with `starred:true`), add to board, Raindrop save | Saves are the strongest non-explicit signal in every reference product (Feedly's Like-Board is built on them). Capturing them on the server means no new client calls and no chance of the UI and DB disagreeing. | LOW | Hook `ArticleService` (starred transition false→true), `BoardService` add-article and `RaindropService` (after a *successful* save only; a breaker-open or failed save must not count). **Raindrop saves are persisted nowhere today** (only `integration_config`), so they *must* be recorded at save time. Star and board state already exist (`article.starred`, `board_article`). |
| E3 | **Undoing the save undoes its signal** | YouTube's "remove from history, it no longer affects recommendations" and Gmail's marker toggle set the expectation: reversing the action reverses the learning. An accidental `s` press must not leave a permanent nudge. | LOW–MEDIUM | Recommended model: one row per `(article_id, kind)` with kinds `OPEN`, `STAR`, `BOARD`, `RAINDROP` (primary key = idempotent). "Strongest engagement" = the max kind weight over the article's rows, computed in the CTE. **Unstar deletes the STAR row, and removing the article from its last board deletes the BOARD row.** RAINDROP and OPEN are one-way facts (there is no un-send or un-open in the app). **Decision to flag:** deleting a *whole board* is housekeeping, not a statement about its articles. Recommendation: keep the BOARD rows, or at least don't cascade silently. |
| E4 | **Explicit thumbs overrides engagement on the same article, and removing the vote restores it** | Every product ranks explicit above implicit, and none lets a click outvote "not interested". The decided scope says "thumbs overrides". | LOW | In the learned CTE, exclude engagement for articles that have an `article_feedback` row. Because the model is derived (v0.2.1 decision), deleting the vote brings the engagement back with no extra code. That is "override, don't erase", which users expect and which falls out of the existing design for free. A narrowed thumbs vote (Shift+D picks) still overrides engagement for the **whole article**. Don't mix picks with engagement per topic. |
| E5 | **Fractional weight: save > open, both < a thumbs vote** | Matches Hu/Koren confidence levels and industry practice (Feedly weights saves; YouTube and Artifact learned that bare clicks mislead). | LOW | Starting point for the replay (product judgment, calibrate in the Calibration phase): **open = 0.25, any save = 0.5** of a thumbs up (vote = 1), reusing `hinge(noul)` and `learn-rate` exactly like thumbs. Put them in yaml (`myfeeder.interest.blend.engagement.open-weight`, `save-weight`, `cap`) as query-time constants, so tuning needs no re-score (the same pattern as `learned-cap`). Star, board and Raindrop share one "save" weight. Splitting them is an anti-feature (see A6). |
| E6 | **Separate, lower engagement cap** (for example +8 to +10 against the thumbs ±20) | Implicit data is abundant. With no cap it saturates every frequently read topic within weeks and drowns explicit signal. The decided scope says "own cap below thumbs cap". | LOW–MEDIUM | Add an engagement sum next to `vote_sum` in the `learned` CTE, and scale and clamp it separately in `eff`. **Two decisions to flag (both needed before planning):** (a) **Is the positive total also capped at the thumbs cap?** Recommendation: yes, `min(thumbs + engagement, learned-cap)` for positive totals, so implicit learning can never push a topic further than explicit learning could. When clipping happens, **thumbs keeps priority and engagement fills the remaining headroom**. That rule is what makes the E8 split exact. (b) **Negative-weight topics.** The existing sign clamp would let engagement pull a negative topic (Politics −30) toward 0 whenever an opened article also matched it. Thumbs has the narrow picker to avoid this, but engagement has none. Recommendation: **engagement only nudges topics whose base weight is ≥ 0.** Softening a negative topic takes an explicit 👍. Zero-weight "tracking" topics may rise, which is the useful discovery case. |
| E7 | **Engagement on unscored articles is dormant, not lost** | Users expect "I opened it" to count eventually. | LOW (to state), MEDIUM (to fix, see D4) | The existing learned CTE joins `article_score ... status = 'SCORED'`, so an engaged but unscored article contributes nothing. **The trap:** selecting an article auto-marks it read (`autoMarkReadDelay`), and the scoring `ELIGIBLE` predicate requires `read = false`. So an article opened *before* Jev scored it (backlog, breaker open, Jev unconfigured, older than 14 days) is **never** scored and its engagement stays dormant forever. Ingest-time scoring makes this rare in steady state. v1 table stake: document it and show nothing misleading (no "counts toward interests" indicator on unscored articles). See D4 for the fix. |
| E8 | **"Why N?" splits learned points into votes vs engagement** | The v0.2.1 promise is an exact breakdown that sums to the badge. A number that moved without a visible cause breaks trust, which is Gmail's reason for hover explanations. It is a PROJECT target feature. | MEDIUM | `TopicContribution` already carries `baseWeight + learnedWeight = weight`. Split `learnedWeight` into `learnedVotes + learnedEngagement` (both after caps and clamps, per the E6(a) priority rule), so **base + votes + engagement = effective weight**, exactly. `WhyBreakdown`/`ScoreRow` render for example "Rust +20 base, +4 votes, +3 reading → +27 × 0.93 = +25". Keep one formula implementation (the CTE). The frontend must never recompute the split. |
| E9 | **Interests learned line splits votes vs engagement per topic** | This is "Manage Training" (NewsBlur) and Instagram's topic summary: one place to see what the system learned. It is a PROJECT target feature. | LOW–MEDIUM | Extend `TopicWeight`/`TopicLearned` and `LearnedLine` in `TopicRow.tsx`: "Learned from votes +4.0 · from reading +3.5 (at max) · Effective +27.5". The existing `limit` enum (`LEARNED_CAP`, `SIGN_CLAMP`, `WEIGHT_RANGE`) needs an engagement-cap case. **Also show how many engaged articles contributed** (for example "from reading +3.5 · 14 articles"). Counts make implicit learning legible (PersonalRSS shows "N rated stories contributed", and Artifact's Stats shows recent reads). |
| E10 | **Per-article "Don't count this" for engagement** | YouTube's "remove from watch history" is the canonical expectation. A mis-click on `o`, or opening something to debunk or reference it, must be neutralizable **without** casting a 👎 (which is a *negative* signal, not a neutral one). This is the PROJECT's "every learned point stays reversible". | LOW–MEDIUM | A small line in the reading pane when the article has counted engagement and is scored, for example "Opened · counts toward your interests · Don't count". Store it as a tombstone or `excluded` flag on the article's engagement, so a later `o` doesn't silently re-add it (idempotent insert, then do nothing). Re-counting is available from the same line. There is **no toast**: see A3. |
| E11 | **Engagement changes re-rank on refresh, not under the cursor** | This is v0.2.1's T8 (frozen order while triaging). An open or star happens *during* triage, so a live re-sort would jump the list. | LOW | The existing Priority invalidation rules already handle star and state patches in place (`usePriorityArticles` patch helper). The new open call must **not** invalidate `['articles']` or `['priority']`. At most, patch the open article's badge. Per WR-05 (Phase 5), an engagement that raises unserved rows past the boundary should set the existing "Ranking changed" hint, the same as votes. |
| E12 | **Gap discovery in context: the no-match line also appears for engaged articles** | Engaged articles that matched no topic are a PROJECT target, and the in-context surface already exists. | LOW | `FeedbackNotice` renders only when `article.feedback` exists. Widen the condition to "has feedback **or** counted engagement", with the draft weight **+20** for engagement (positive-only, and it keeps the `TopicDraft` type `20 \| -20` unchanged). "Matched no topic" must mean what it means today (`matchedTopics(article).length === 0`, stored nouls with a positive hinge) so both notices agree. |

### Differentiators (Competitive Advantage)

These are not expected, but they fit the Core Value (the Priority view reflects what I care about, explainably, with no extra Jev calls). The industry is weakest here, so these carry the most value.

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| D1 | **Gap-discovery list in Interests: "You read these, but no topic covers them"** | This turns implicit behavior into *explicit, user-approved* rubric changes, which is the most trustworthy way to use implicit data. It mirrors Instagram's topic summary and Feedly's Like-Board without any opaque model. Nothing learns silently: the user decides. | MEDIUM | A new read-only query: engaged (not excluded, not thumbs-overridden), SCORED, zero positive-hinge topics, newest first, limited to around 20. Each row offers **Create topic** (the existing draft flow: title → description, +20) and **Dismiss**. **Order by surprise:** show the lowest blended or profile score first. The articles the whole model predicted you'd ignore but you opened or saved are the real gaps (the "learn from surprise" principle). An article with a high profile score is already covered by the profile. |
| D2 | **Suggestion lifecycle: handled and dismissed suggestions stay gone** | Without this, the list never empties. **Topics created after an article was scored have no stored noul for it (R5), and articles are never re-judged**, so the article still "matches no topic" after you create a topic *from it* and would be suggested forever. | LOW–MEDIUM | Record a `suggestion_handled_at` (or dismissed flag) on the article's engagement when Create or Dismiss is used. Create can mark the source article as handled on successful topic save (pass the article id with the draft). This is **required** if D1 ships. The lifecycle is a hidden dependency of D1, not an extra. |
| D3 | **Engagement badge in the article list and the reading pane** (a small "opened"/"saved" glyph) | This makes the signal visible where it happens, so the user can see what the system knows, like Gmail's marker. It is cheap, and it teaches the user that opens count. | LOW | The article DTO already carries `feedback`. Add `engagement: {kind, excluded}` the same way (`GET /api/articles/{id}` and list items). It is also the anchor for E10's "Don't count". |
| D4 | **Engaged-but-unscored articles become eligible for scoring** | This fixes the E7 trap: an explicit user action should not be wasted because it came before Jev. The cost is bounded (at most one Jev call per engaged article, and only once). | MEDIUM | Extend `ArticleScoreStore.ELIGIBLE` to `(read = false OR engaged) AND window`. The window stays, so it does not reopen the old backlog. **This touches the shared ELIGIBLE predicate that drives the sweep, `/status` counts and Re-score**, so it needs care: `eligibleUnscored` semantics and Re-score scope must stay consistent (PITFALLS). Could be P2 if the Calibration phase shows dormant engagement is rare. |
| D5 | **Seed engagement from existing saves at migration** (current starred and board articles become STAR/BOARD rows in V7) | Learning is useful on day one instead of after weeks of new saves. Past stars are real saves. | LOW | This is pure SQL in V7 (`INSERT ... SELECT` from `article` where starred, and from `board_article`). No opens and no Raindrop history exist, so those start empty. **It shifts production ranking at deploy**, so the Calibration replay must be run *with* the seeded rows before choosing weights. Flag it as a decision: some users would rather start clean. |
| D6 | **Engagement effect preview in the Why row, not a toast** | "+3 from reading (6 articles)" per topic in the breakdown shows cause and effect exactly where the user looks when asking "why is this high?" | LOW (given E8) | Mostly presentation on top of E8 and E9. |
| D7 | **Record the view the open came from** (`priority` / `feed` / `all` / `starred` / `board`) | This enables a later position-bias check. If most opens come from the top of Priority, the loop is self-reinforcing (Joachims). The Calibration replay can compare nudges with and without Priority-sourced opens. | LOW | One nullable column. **Don't use it in the blend in v1.** It exists for calibration and diagnostics only. It could equally be deferred. Include it only if it costs nothing in the capture endpoint. |

### Anti-Features (Commonly Requested, Often Problematic)

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| A1 | **Dwell time, selection, reader view or scroll depth as signals** | "Time spent reading" is what Artifact and YouTube use. | This is already decided out of scope. With `j`/`k` skimming and auto-mark-read, selection and dwell measure navigation, not interest. Reader view is auto-enabled for content-less items. Measuring dwell accurately in a tab you left also requires visibility tracking. | Deliberate actions only (open, star, board, Raindrop). |
| A2 | **Negative signal from skipped / never-opened articles** | "Implicit negatives" are standard in large recommenders. | This is decided out of scope. Hu/Koren treat non-interaction as *low-confidence* evidence, skipping is usually lack of time, and Priority position bias would punish whatever the ranker put below the fold. | Positive-only. The user expresses dislike with 👎 or a negative topic weight. |
| A3 | **A toast on every open or save** ("Nudged Rust +0.5") | Thumbs has an effect toast, so parity seems natural. | Opens and stars happen dozens of times per session, and a toast each time is noise that trains the user to ignore toasts, including the useful thumbs ones. The per-open effect is also tiny and often zero (at cap, no match, unscored). | A quiet engagement glyph (D3) plus the split in "Why N?" (E8) and Interests (E9). |
| A4 | **Engagement writes topic weights** (or auto-creates topics) | Simpler storage. It "really learns". | It breaks the v0.2.1 derived model: no exact undo, no override-and-restore, and drift is invisible. Auto-created topics would also call Jev on text the user never approved, and the topic would have no nouls for past articles. | A derived CTE (as decided). Gap discovery *suggests*, and the user creates. |
| A5 | **LLM-generated topic suggestions** (cluster engaged articles and have Claude write a topic description) | Spring AI Anthropic is already on the classpath, and suggestions would read better than a raw title. | It adds a new billed LLM path with prose outputs to a milestone whose goal is "no extra Jev calls". It is non-deterministic, needs its own resilience and cost controls, and Jev itself returns no prose. | Use the title as the draft (existing flow), and the user edits the description in the Interests dialog. Revisit when there is evidence that title drafts are poor. |
| A6 | **Separate weights for star vs board vs Raindrop** | They "feel" different. | There are three more knobs to calibrate from a single user's sparse data, and the replay can't distinguish them meaningfully. The decided scope is "save > open". | One save weight. Kinds are still stored per row, so splitting later is a yaml change. |
| A7 | **Counting repeated opens or saves** (open count, re-open after days) | "I opened it three times, so I really care." | This is decided as one count per article, and repeated opens are often navigation (lost tab). They also inflate high-volume topics further. | Strongest engagement once per article. |
| A8 | **Time decay of engagement** | Interests drift, and recommenders decay history. | Thumbs don't decay, so decaying only engagement would make the two halves of "learned" behave inconsistently. It adds a time-dependent term that makes the replay and the drift guard harder, and the cap already bounds stale influence. | The cap now, and "Reset reading history" later (F2). Revisit decay for both signals together. |
| A9 | **Feed affinity from engagement** (Inoreader-style per-feed bonus) | It is the cheapest implicit ranking there is. | This is decided as deferred. It adds a new blend term that isn't a topic, which complicates "Why N?". | Topic nudge plus gap discovery first. The capture table (with feed reachable via the article) keeps this possible later. |
| A10 | **A global "pause learning from reading" UI toggle** | YouTube and Gmail offer one. | For a single user this is a new setting, its persistence and its tests. The same effect is available by setting `open-weight`/`save-weight` to 0 in yaml (query-time, no re-score). | A yaml lever documented in CLAUDE.md, next to the other blend constants. |

---

## Feature Dependencies

```
[V7 article_engagement table]
    ├──required by──> [E1 capture open]  (new endpoint; client fires after window.open)
    ├──required by──> [E2 capture saves]  (ArticleService / BoardService / RaindropService hooks)
    │                     └──required by──> [E3 unstar/unboard deletes row]
    └──required by──> [Learned CTE engagement term: E4 override + E5 weights + E6 cap/sign rule]
                              ├──required by──> [E8 "Why N?" split]  (TopicContribution split)
                              ├──required by──> [E9 Interests learned line split + counts]
                              └──required by──> [Calibration: replay + drift guard extension]

[E6(a) combined-cap priority rule] ──required by──> [E8/E9 exact split]
[E10 "Don't count" (excluded flag)] ──requires──> [D3 engagement on article DTO]
[E12 in-context no-match line] ──requires──> [engagement on article DTO] + existing FeedbackNotice/TopicDraft
[D1 gap list] ──requires──> [D2 suggestion lifecycle]  (else suggestions never clear, R5)
[D1 gap list] ──requires──> [SCORED row]  ──enhanced by──> [D4 engaged ⇒ eligible]
[D4 engaged ⇒ eligible] ──touches──> [shared ELIGIBLE predicate: sweep, /status, Re-score]
[D5 V7 seed from stars/boards] ──must precede──> [Calibration replay]
[E11 no live re-sort] ──conflicts──> [invalidating ['articles']/['priority'] on open]
```

### Dependency Notes

- **The schema and capture come first.** Nothing is learnable, explainable or calibratable without rows. Server-side save capture (E2) is independent of the frontend. Open capture (E1) needs one endpoint and two call sites (`ReadingPane.handleOpenOriginal`, the `useKeyboardShortcuts` `'o'` case).
- **The learned CTE change is the core, and it is shared.** `LEARNED_CTE` feeds the blend, `topicWeights`, `allTopicWeights` and the breakdown. The engagement term, the override (E4), the weights (E5), and the cap and sign rules (E6) all land in that one CTE. E8 and E9 read its outputs. **Decide E6(a) and E6(b) before writing the CTE**: they determine whether the votes/engagement split is well defined.
- **The explainability split depends on a precedence rule, not just two sums.** When the combined learned total hits a cap or clamp, the split must say which part was clipped. "Thumbs first, engagement fills the headroom" is the simplest rule that keeps base + votes + engagement = effective.
- **Gap discovery depends on SCORED rows and on a lifecycle.** Unscored engaged articles can't be judged "no match". Articles are never re-judged against new topics (R5), so without D2 a suggestion reappears forever. E12 (the in-context notice) avoids the lifecycle problem because it only shows on the open article, so it can ship before D1.
- **Calibration depends on everything that changes production numbers.** That includes D5 (seeding) and the final weights, caps and sign rule. The drift guard (`InterestCalibrationReplaySqlTest`) must cover the extended CTE verbatim.
- **Conflict:** E11 (stable triage) conflicts with a naive "invalidate on open". The open endpoint's hook must patch in place only.

---

## MVP Definition

### Launch With (v0.3.0)

- [ ] **V7 `article_engagement`** (one row per article and kind, idempotent, `excluded`/tombstone support). Everything depends on it.
- [ ] **E1 capture open** (`o`, Open Original buttons, header/title link; not in-body links). This is the user's headline signal.
- [ ] **E2 + E3 capture saves server-side, with unstar and unboard reversal.** This is the strongest signal, and accidental stars must not stick.
- [ ] **E4 + E5 + E6 learned-CTE engagement term.** Thumbs override, save > open > 0, a lower separate cap, a combined cap with thumbs priority, and non-negative-base topics only. This is the milestone's core.
- [ ] **E8 + E9 explainability split** ("Why N?" and the Interests line, with contributing-article counts). It is a PROJECT target and the trust anchor.
- [ ] **E10 "Don't count"** per article (plus D3's glyph as its anchor). The PROJECT goal "every learned point stays reversible" requires a *neutral* undo.
- [ ] **E11 no live re-sort on open.** It protects the triage flow.
- [ ] **E12 in-context no-match line for engaged articles.** It is the cheapest gap discovery and reuses the existing draft.
- [ ] **D1 + D2 gap list in Interests, with its lifecycle.** It is a PROJECT target ("surfaced as topic suggestions"). Order by surprise.
- [ ] **Calibration**: replay open-weight, save-weight and engagement cap against prod (after D5 if seeding is chosen), and extend the drift guard to the new CTE.

### Add After Validation (v0.3.x)

- [ ] **D4 engaged ⇒ eligible for scoring.** Trigger: calibration or prod shows a meaningful share of engaged articles stuck unscored (query: engaged rows with no SCORED `article_score`).
- [ ] **D5 seed from existing stars/boards.** Trigger: the user wants day-one learning. Decide *before* the calibration replay, since it changes the baseline. It could equally move into v0.3.0 at low cost.
- [ ] **D7 record the source view of opens.** Trigger: suspicion that Priority opens are self-reinforcing (topics pinned at the engagement cap that are also the top-ranked ones).
- [ ] **Copy link as a signal.** It is a "share" action, which Artifact weights highly. Trigger: the user asks for it. It's the same capture pattern as E1.

### Future Consideration (v0.4+)

- [ ] **Rate-normalized engagement** (engaged / matched-and-read per topic instead of raw counts). A count model has **volume bias**: a topic with 200 articles a week reaches the cap even if you open 5% of them, while a niche topic you open 100% of barely moves. The cap hides this in v1. Revisit if calibration shows high-volume topics saturating.
- [ ] **Feed affinity** (A9). It was deferred by decision, and the capture table keeps it possible.
- [ ] **"Reset reading history"** (Instagram's Reset suggested content, YouTube's clear history). This is a bulk delete of engagement rows. Defer until the history is long enough to matter.
- [ ] **Joint decay of votes and engagement** (A8). Only together, never engagement alone.

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| V7 engagement table | HIGH (enabler) | LOW | P1 |
| E1 capture open | HIGH | LOW–MEDIUM | P1 |
| E2 capture saves (+E3 reversal) | HIGH | LOW–MEDIUM | P1 |
| E4/E5/E6 learned-CTE engagement term | HIGH | MEDIUM | P1 |
| E8 "Why N?" split | HIGH | MEDIUM | P1 |
| E9 Interests learned split + counts | MEDIUM–HIGH | LOW–MEDIUM | P1 |
| E10 "Don't count" + D3 glyph | MEDIUM | LOW–MEDIUM | P1 |
| E11 no re-sort on open | MEDIUM | LOW | P1 |
| E12 in-context no-match for engaged | MEDIUM | LOW | P1 |
| D1 gap list + D2 lifecycle | MEDIUM–HIGH | MEDIUM | P1 (PROJECT target) |
| Calibration + drift guard | HIGH | MEDIUM | P1 |
| D5 seed from stars/boards | MEDIUM | LOW | P2 (decide before calibration) |
| D4 engaged ⇒ eligible | MEDIUM | MEDIUM | P2 |
| D7 open source view | LOW | LOW | P3 |
| Copy link signal | LOW | LOW | P3 |
| Rate-normalized engagement | MEDIUM | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

---

## Competitor Feature Analysis

| Feature | Feedly Leo | Gmail / YouTube | Instagram / Artifact | Our Approach |
|---------|-----------|-----------------|----------------------|--------------|
| Which implicit signals | Board saves (documented) | Opens, replies, stars / watch history | All engagement / clicks, read time, shares | Open original + star/board/Raindrop only. Positive-only, one count per article. |
| Implicit vs explicit weight | "Less like this" dominates | Explicit toggle / "Not interested" overrides | Explicit dislike and clickbait flag added later | Open 0.25, save 0.5 of a thumbs vote (to calibrate). Thumbs overrides per article. Separate lower cap, combined within the thumbs cap. |
| Why explanation | Priority label | Gmail hover one-liner | Topic summary + "From X" label / Stats view | Exact points: base + votes + reading per topic in "Why N?", learned split and article counts in Interests. |
| Undo | Remove from board | Remove from history, pause history | Remove topic, reset | Unstar/unboard reverses. Per-article "Don't count". Removing a thumbs vote restores engagement (derived). Yaml weight 0 as the global off switch. |
| Suggestions from behavior | The Like-Board learns implicitly (no suggestion step) | None explicit | AI topic summary you can edit | Engaged-but-unmatched articles listed by surprise, with one-click "Create topic" (user approves) and Dismiss. No silent topic creation. |

---

## Sources

- Feedly Leo Like-Board and "Less like this" / mute: [coywolf.com Feedly Pro+ Leo](https://coywolf.com/news/productivity/feedly-pro-plus-leo-ai/), [coywolf.com Leo preview](https://coywolf.com/news/content-marketing/feedly-leo-rss-news-feed-ai/), [Feedly docs: saving to Boards](https://docs.feedly.com/article/60-how-can-i-save-an-article-to-a-board), [Leo and Mute Filters (Feedly blog mirror)](https://sechub.in/view/2578731). LOW–MEDIUM: the claim that Leo learns from opens and skips is from third-party reviews only.
- NewsBlur: [Explaining Intelligence (2011)](https://blog.newsblur.com/2011/04/01/explaining-intelligence/), [Intelligence Trainer Overhaul (2026-01)](https://blog.newsblur.com/2026/01/22/intelligence-trainer-overhaul/). MEDIUM.
- Inoreader: [Sort by Magic and popularity indicators](https://www.inoreader.com/blog/2019/11/new-feature-sort-by-magic-and-article-popularity-indicators.html). MEDIUM.
- Google News / YouTube / Gmail: [Google research: Personalized News Recommendation Based on Click Behavior](https://research.google.com/pubs/archive/35599.pdf), [YouTube Help: watch history](https://support.google.com/youtube/answer/95725?hl=en), [YouTube Help: manage recommendations](https://support.google.com/youtube/answer/6342839?hl=en), [YouTube Blog: why we focus on watch time](https://blog.youtube/news-and-events/youtube-now-why-we-focus-on-watch-time/), [Gmail Help: importance markers](https://support.google.com/mail/answer/186543?hl=en). MEDIUM.
- Instagram / Artifact: [Instagram: Reels algorithm control](https://about.instagram.com/blog/announcements/reels-algorithm-control), [Instagram: reset content suggestions](https://about.instagram.com/blog/announcements/reset-instagram-content-suggestions), [TechCrunch: Artifact public launch](https://techcrunch.com/2023/02/22/instagrams-co-founders-personalized-news-app-artifact-launches-to-the-public-with-new-features/), [iTech Post: Artifact clickbait flag](http://www.itechpost.com/articles/117690/20230523/ai-driven-news-app-artifact-lets-mark-articles-clickbait.htm). MEDIUM.
- Academic: [Joachims et al., Accurately Interpreting Clickthrough Data as Implicit Feedback (SIGIR 2005)](https://www.cs.cornell.edu/people/tj/publications/joachims_etal_05a.pdf), [Hu, Koren & Volinsky, Collaborative Filtering for Implicit Feedback Datasets (ICDM 2008)](http://yifanhu.net/PUB/cf.pdf), [Chen et al., Bias and Debias in Recommender System (TOIS)](https://arxiv.org/pdf/2010.03240), [Feedback Loop and Bias Amplification](https://www.alphaxiv.org/abs/2007.13019). HIGH for the stated findings.
- Codebase read (HIGH): `repository/InterestScoreQueries.java` (`LEARNED_CTE`, `TopicContribution`, `TopicWeight`), `components/FeedbackNotice.tsx`, `components/InterestsDialog.tsx` (`TopicDraft`), `components/TopicRow.tsx` (`LearnedLine`), `components/ReadingPane.tsx` (`handleOpenOriginal`, `handleContentClick`), `hooks/useKeyboardShortcuts.ts` (`'o'`), `db/migration/V2` (`board_article`), and the absence of any persisted Raindrop save.

---
*Feature research for: implicit engagement learning on top of an explainable query-time interest ranker (myfeeder v0.3.0)*
*Researched: 2026-09-29*
