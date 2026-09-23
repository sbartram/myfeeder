# Feature Research

**Domain:** Interest-based article ranking ("priority inbox") for a self-hosted, single-user feed reader (myfeeder)
**Researched:** 2026-09-22
**Confidence:** MEDIUM overall. Competitor behavior comes from vendor docs and blogs (Feedly docs, NewsBlur blog, TT-RSS docs, Gmail Help, Spring blog). The research seam tags these fetches LOW, but they are primary sources and several were checked against a second source. The table-stakes and anti-feature calls are product judgment built on those facts, and the doc says so where that applies.

---

## How the reference products actually work (condensed)

| Product | Interest signal | How ranking shows up | "Why" explainability | Feedback affordance |
|---------|-----------------|----------------------|----------------------|---------------------|
| **Feedly AI (Leo)** | User-defined "Priorities": Topic (keywords, companies, people), Industry, Business Events, Like-Board (learns from saved articles). There are also Mute Filters. | A separate **Priority tab** per feed plus a "Priorities" section in the left nav. | Prioritized articles carry a **green label saying why they were chosen**, and salient entities are highlighted in the text. | A downvote arrow on hover opens **"Less like this"**, a popup listing the topics the AI linked to the article so you can pick which one to mute. Saving to a board counts as a positive signal. Filters are managed on a "Train Feedly AI" page with remove/pause/resume. |
| **NewsBlur Intelligence Trainer** | Thumbs up/down on author, tag, title phrase, full-text phrase, URL, publisher. Newer additions are natural-language **prompt classifiers** and **super dislike**. | Three-state filter (All / Unread / **Focus**). Green means liked, red means disliked (hidden). | Matched title/author/tag/text is **color-highlighted inline**, prompt classifiers show as **colored pills in the story header**, and an explainer banner states the precedence: prompt > super-dislike > like > dislike > feed score. | Per-story trainer dialog. A **Manage Training** tab lists every classifier ever trained. Everything is reversible. |
| **NewsBlur prompt classifiers** (closest analog to our free-text profile) | Plain-English description, with the LLM judging match / no match / opposite. | Same Focus mechanics. | Pills. | **Saving a prompt immediately re-classifies recent stories.** You can preview a prompt on a single story before saving. There is a per-story cost (~0.1 cent) with a monthly spend cap. |
| **Inoreader "Sort by Magic"** | Global engagement weighted by which feeds *you* read most. | A **sort option**, not a separate feed, with popularity indicators. | Only the popularity indicator. | Implicit only. |
| **Gmail Priority Inbox / importance markers** | Implicit: senders, opens, replies, keywords, star/archive/delete. | A separate "Important" section. | **Hover the marker for a one-line reason.** | Click the marker to toggle important/not important, which trains the model. Learning can be switched off. |
| **Tiny Tiny RSS scoring** | Rule filters with signed integer "modify score" actions, summed. | Sort by score. Score < 0 is left out of "Fresh". < -500 is auto-marked read. > 1000 is auto-starred. | An up/down **score icon** in the headline list. | **Click the score icon to adjust it manually.** |
| **feeds.fun** (OSS) | LLM assigns tags, user rules map tag combinations to +/- points. | Sort by score. | The tags on each entry. | Rules are built from tags. |
| **PersonalRSS** (OSS) | Local explainable scorer plus a small classifier trained on feedback. | **High / Maybe / Filtered** views. | Each card shows its strongest positive and negative evidence, confidence, and how many rated stories contributed. | Interested / Not interested, plus "Always / Never this topic" (strong feedback counts double). Cold start is a neutral 0.5. |
| **Readwise Reader (Ghostreader)** | Per-document AI (summarize, auto-tag, custom prompts). | No score-ranked feed. | n/a | n/a. Not a real competitor for ranking. |

**What this means for myfeeder:** the proposed design (written profile, a weighted topic rubric, thumbs that nudge topic weights, a Priority smart view, a badge) sits between Feedly's Priorities and NewsBlur's prompt classifiers plus TT-RSS/feeds.fun point-scoring. That puts it firmly in the mainstream. The design choices that decide whether users trust it are explainability and feedback semantics, not the model.

**Jev constraint that shapes the UX (Spring blog, 2026-09-21):** Jev **never returns prose**. It returns a continuous `Score` over an ordered rubric with per-level probabilities and a `confidence`, and a `Noul` truth value in [0,1] (0.5 means undecided). So any "why is this ranked high?" text has to be **computed deterministically from stored numbers** (profile score plus per-topic match × weight), never generated. That is a strength: the explanation is exact, reproducible, and free.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Without these, the feature feels broken or untrustworthy. Every reference product has some version of each.

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| T1 | **Priority smart view** in the feed tree (next to "All Articles" / "Starred") | Feedly Priority tab, Gmail Important, NewsBlur Focus. A dedicated place to triage is the core of the pattern. | MEDIUM | Add it to the existing `smart-view` block in `FeedPanel.tsx` as route `/priority`. Unread only, ordered by blended score desc, ties by `COALESCE(published_at, fetched_at)` desc then id. Needs a composite score cursor that fits `PaginatedResponse` (see ARCHITECTURE/PITFALLS). |
| T2 | **Unscored articles still visible, after the scored ones, with a visible separator** ("Not yet scored"). | Silently missing articles destroy trust. TT-RSS and betternews hide low scores, and users complain. | LOW | Already a PROJECT decision. The separator is a small UX addition: a divider row where scored ends. It makes backfill progress obvious. |
| T3 | **Score badge** on article-list rows and in the reading-pane header | TT-RSS score icon, Feedly green label, NewsBlur green/red. Users need to see the ranking signal at a glance. | LOW–MEDIUM | See "Badge format" below. Unscored shows **no badge** (or a faint "·" in Priority only). Don't render "0", because it reads as "scored as irrelevant". |
| T4 | **"Why this score" breakdown**, shown on badge hover/click and as a collapsible section in the reading pane | Gmail's hover reason, Feedly's "why chosen" label, NewsBlur pills plus precedence banner, PersonalRSS evidence. An unexplained number gets ignored or distrusted. | MEDIUM | Jev gives no prose, so render the math: *Profile match 64 ("Strong interest", conf 0.81)* + *Rust +20 × 0.93 = +19* + *Politics −30 × 0.71 = −21* → **62**. List only topics with match ≥ 0.5 by default and add "show all" for the rest. Computed client-side or server-side from the same stored numbers the query uses. **One formula implementation only** (see PITFALLS), so the badge, breakdown, and sort can never disagree. |
| T5 | **Matched-topic chips** in the reading pane (and optionally a compact form in the list) | NewsBlur classifier pills, Feedly highlighted entities. They are the fastest way to see what an article matched. | LOW | Chip color comes from the sign of the weight (positive = accent, negative = muted/red). Clicking a chip opens that topic in the rubric editor. This comes nearly free with T4. |
| T6 | **Thumbs up / down on an article, toggleable and reversible** | Gmail click-to-toggle, Feedly "less like this", NewsBlur reversible training. A one-shot, non-undoable vote feels risky. | MEDIUM | Store one feedback row per article (`UP` / `DOWN` / none). Toggling off or flipping **reverses the exact nudge that was applied**, so store the per-topic deltas, not just the direction. Idempotent: pressing up twice does not nudge twice. |
| T7 | **Feedback shows its effect immediately** | The whole point of "nudge weights" is that the user sees it work. Silent training feels like it did nothing. | LOW | A toast like "Nudged Rust +2, WebAssembly +1" and an immediate badge update on that article. **Don't re-sort the list under the cursor** (see T8). |
| T8 | **List stability while triaging** | Every triage UI (Gmail, Reader) keeps your place. Re-sorting after each vote or mark-read breaks `j`/`k` flow. | MEDIUM | This is a **real risk in this codebase**: `useUpdateArticleState` calls `invalidateQueries(['articles'])`, so a thumbs vote or mark-read refetches and reorders or removes rows in Priority. The Priority list should update the badge in place (`setQueryData`) and re-rank only on explicit refresh, view re-entry, or page refetch. Keep the selected article selected. |
| T9 | **Interest profile editor** (free text) | Feedly Priorities and NewsBlur prompt classifiers both put a written description at the center. | LOW | A new "Interests" section in `SettingsDialog`, or a dedicated dialog opened from the Priority view header. Include **writing guidance** inline, because Jev's option text quality measurably changes confidence (0.82 → 0.60 with bare labels, per the Spring blog). Show "applies to newly arriving articles" (see Conflict C1). |
| T10 | **Topic rubric editor**: add, remove, edit description, signed weight | Feedly topic priorities and mute filters, feeds.fun rules, TT-RSS score filters. A rubric without CRUD isn't a feature. | MEDIUM | Each row: name, description (the Jev question text, phrased as a statement about the article), weight as a signed number or slider (e.g. −50…+50 points). Show the **current effective weight and how much of it came from feedback** ("+20 set, +6 from feedback"), with a **"reset to my value"** action. Cap the topic count (≈20–25) to keep one `systemOne` call bounded. |
| T11 | **Manage-all-training view** | NewsBlur "Manage Training", Feedly "Train Feedly AI" page. Users need one place to see what drives ranking. | LOW | For a single user this is the topic rubric editor from T10 plus a feedback history count per topic. No separate screen needed. |
| T12 | **Clear "not configured" / "unavailable" state** | The existing Raindrop precedent ("not configured by the administrator"). Optional integrations must say why they're inert. | LOW | A status endpoint (configured? circuit open? unscored backlog count?). When the key is missing: hide the Priority smart view and badges, and show a notice in Settings. When the circuit is open: the Priority view still works (scored first, rest by date) with a subtle banner saying "Scoring paused — N articles waiting". |
| T13 | **Cold-start empty state** | Every ranker needs one. PersonalRSS starts neutral, Feedly walks you through creating a Priority. | LOW | If the profile is empty and there are no topics, the Priority view shows a CTA ("Describe what you care about to start ranking") that opens the editor. **Don't call Jev with an empty profile.** Leave articles unscored so backfill picks them up once a profile exists (see Conflict C1). |
| T14 | **Backfill progress visibility** | NewsBlur re-sorts "within seconds" after a new prompt. Users expect to see the ranking fill in. | LOW | A Priority header line such as "Scoring 312 of 1,204 unread…" backed by an unscored-unread count query. Hide it when the count is 0. |
| T15 | **Keyboard support for voting and the view** | This app is keyboard-first (vim-style shortcuts, `ShortcutOverlay`). A mouse-only thumbs button would be a regression in style. | LOW | See "Keyboard shortcuts" below. Update `ShortcutOverlay.tsx`. |

### Differentiators (Competitive Advantage)

These aren't expected, but they fit the Core Value: rank what I care about, explain it, never break polling.

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| D1 | **Exact, arithmetic "why"** (profile + Σ match×weight shown as a sum) | Better than Feedly's label or Gmail's one-liner: fully reproducible and free (no LLM prose call). This is what makes feedback nudges legible. | LOW (given T4) | Mostly presentation on top of T4. Show confidence as a hint ("low confidence" when Jev `confidence` < ~0.5). |
| D2 | **Feedback picks the topic** (Feedly "Less like this" popup model) | Thumbs down on a mixed article (e.g. Rust + Politics) otherwise punishes *both* topics. Letting the user click "less Politics" is more precise and keeps the "deterministic and explainable" decision intact. | MEDIUM | v1 default: thumbs nudges all matched topics in proportion to match strength. Optional: shift-click / a long-press popover lists the matched topics with per-topic up/down. This could be v1.x if time is short. |
| D3 | **"Create topic from this article"** when feedback has nothing to act on | Solves the dead-end in Conflict C2: thumbs on an article that matched no topics has no effect under the PROJECT design. | LOW–MEDIUM | The toast becomes "No topics matched — add a topic?" and opens the rubric editor pre-filled with the article title as a starting description. |
| D4 | **Manual "Re-score unread" action** | Lets profile/topic edits reach the existing backlog on demand, so the automatic new-only policy stays. Reuses the backfill job with no new machinery. At Jev prices (TypeSafe benchmark ≈ $0.00004 for a 14-question call) 1,000 unread costs about 4 cents. | LOW (given backfill job) | A button in the Interests editor showing an estimated count ("Re-score 1,204 unread articles"). Reset the scores for unread articles to "unscored" and let the backfill job drain them. See Conflict C1. |
| D5 | **Topic test/preview** against the currently open article | NewsBlur lets you test a prompt on a story before saving. Jev quality depends on description wording, so fast iteration matters. | MEDIUM | One on-demand `systemOne` call with the draft profile/topic against the selected article, showing the resulting match. Not persisted. Needs the same circuit breaker. v1.x. |
| D6 | **Priority unread count on the smart view** (count above a "high" threshold, not total unread) | Feedly shows priority counts. A count that equals "All unread" is noise. | LOW–MEDIUM | Needs a single "high" threshold on the display scale (e.g. ≥ 70). Can reuse the badge tier boundary. |
| D7 | **Tiered badge coloring** (high / neutral / low) with the number on hover | Fast visual triage (TT-RSS arrows, NewsBlur green/red) without making the user read numbers. | LOW | See "Badge format". |
| D8 | **"Mark everything below here as read"** in Priority | Priority-inbox triage finisher. Analogous to the existing `MarkOlderReadDialog`. | MEDIUM | v1.x. Scoped to the Priority view's order. Requires a confirm dialog. |
| D9 | **Per-topic stats**: matches in the last 30 days, feedback count, drift | Shows which topics actually do work. Helps prune useless ones. | LOW–MEDIUM | Aggregate query over the stored match values. v1.x. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Auto-hide / auto-mark-read low scores** (TT-RSS < −500, betternews < 0.35) | "Filter the noise for me" | Silent data loss. A miscalibrated topic can bury articles for weeks. Already Out of Scope in PROJECT. | Priority view ordering plus the badge. Low-scored articles are still in All/feed views. |
| **LLM-generated prose explanations** ("This article is relevant because…") | Feels more "AI" | Jev can't produce text. A separate chat call per article breaks the cost constraint and can hallucinate a reason unrelated to the actual score. | The deterministic breakdown (T4/D1). |
| **Implicit signals** (dwell time, opens, scroll depth, star/board as training) | Gmail and Inoreader Magic learn implicitly | Non-deterministic, hard to explain, contradicts the "explicit control" decision, and feeds a rich-get-richer loop. | Explicit thumbs only. *Maybe* later: treat star as an optional explicit thumbs-up (setting, off by default). |
| **Re-scoring every article on each profile/weight change** | "Keep everything consistent" | Unbounded Jev calls. Weight changes don't need it because the blend happens at query time. | Query-time blend for weights. The manual re-score button (D4) for profile/topic text edits. |
| **Popularity / engagement ranking** (Inoreader Magic) | "Show what's hot" | Single-user and self-hosted means there's no cross-user signal. It would need external APIs. | Nothing. Out of domain. |
| **Per-feed / per-folder topic scopes** (NewsBlur site/folder/global) | Fine-grained control | Multiplies the rubric UI and the query complexity for one user. Feed name is already in the Jev state, so "I don't care about X from feed Y" can go in the profile text. | Global rubric only. Revisit if feed-specific noise shows up. |
| **Super-dislike / precedence rules** (NewsBlur) | Hard veto on a topic | Adds non-additive semantics that break the "sum of contributions" explanation. | A large negative weight (e.g. −50) already sinks an article in an additive model. Document it in the editor ("use −50 to bury a topic"). |
| **Relative / percentile badges** ("top 10% today") | Always shows a spread | The same article's badge changes as other articles arrive, so users can't learn what a number means. | Absolute scale (0–100) that only changes when weights change. |
| **Using liked articles as in-context examples** | "Learn from examples like Feedly Like-Board" | Already Out of Scope. It bloats the Jev state and makes scores depend on the feedback history order. | Weight nudges. |
| **Sort-by-interest on every list, dimming low scores** | Consistency | Out of Scope for this milestone. It doubles the pagination/cursor work in every view. | Priority view plus the badge everywhere. |
| **Priority notifications / push alerts** | NewsBlur per-classifier notifications, Feedly alerts | Needs a notification channel that doesn't exist. Scope creep. | None this milestone. |
| **Auto-generated topics from reading history** | "Zero setup" | A black box. Contradicts explicit control. | The cold-start CTA (T13) plus "create topic from this article" (D3). |

---

## UX Detail Recommendations (opinionated)

### Score scale and badge format
- **Choose one display scale, 0–100 integer, and express topic weights in the same points.** Recommended blend: `display = clamp(0, 100, profileScoreNormalized × 100 + Σ(match_i × weight_i))`, with weights in points (default ±20, allowed range −50…+50). Users then read the breakdown as plain addition. (TT-RSS and feeds.fun both use integer points, and users understand them.)
  - `profileScoreNormalized` = Jev `Score` continuous value / (levels − 1). Use a 5-level rubric, e.g. "Not relevant / Slightly / Moderately / Strongly / Must-read", with each level phrased as a statement about the article.
  - Whether to include only matches ≥ 0.5 or all matches weighted continuously is a requirements decision. **Recommend continuous** (`match × weight`, no threshold) for the sort, because it's smoother and has no cliff at 0.5, and **show only ≥ 0.5 in the breakdown by default**.
  - Keep the raw unclamped value for sorting ties above 100 and below 0. Clamp only for display.
- **Badge:** a small pill with the integer (e.g. `82`). Color by tier: ≥ 70 accent/high, 40–69 neutral, < 40 muted. Low Jev confidence gets a dashed outline. Unscored gets no badge. Hovering (or clicking in the reading pane) opens the T4 breakdown. Tier colors must come from `themes.ts` variables so all 6 themes work.
- **Don't use stars, flames, or emoji.** The app already uses a star for "starred", and those glyphs would clash with it.

### Explainability ("why is this ranked high?")
- Put the breakdown **in the reading pane** (collapsible "Why 82?" row under the title) and as a **badge tooltip** in the list.
- Order rows by absolute contribution. Show the profile level label ("Strongly interested", from the rubric level with the highest probability) and not just the number.
- Show the feedback effect on each topic row ("weight 26 = 20 set + 6 from feedback").

### Feedback affordances
- Put buttons in the reading-pane toolbar (next to star / board / Raindrop), showing filled state when active. Optionally add hover buttons on list rows.
- Nudge rule (for requirements): `Δw_i = η × match_i × (+1 | −1)`, applied only to topics with `match_i ≥ 0.5`, with η ≈ 2 points, clamped to the weight range. Record the deltas per article so undo is exact.
- Dead-end handling: if nothing matched ≥ 0.5, **show it** ("No topics matched — nothing to adjust. Add a topic?") and don't fail silently (D3).
- Thumbs doesn't mark read and doesn't star. Keep the signals orthogonal.

### Keyboard shortcuts (grounded in `useKeyboardShortcuts.ts`)
- Taken: `j k n p m s o b v r A / g ? Tab Enter Escape` and **`+ = -` (font size)**, so **don't use `+`/`-` for thumbs**, even though it's the obvious choice.
- Recommend: **`u` = thumbs up, `d` = thumbs down** (mnemonic "up/down", both free, pressing again toggles off), **`i` = toggle the "why" breakdown**, **`g` then `p` = go to Priority** (fits the existing `g a` / `g s` / `g b` chords).
- In the Priority view, **disable `Shift+A`** (mark all read in feed), because there is no single feed and it could mark the whole backlog read. Use D8 later.

### Cold start
- No profile and no topics: the Priority view shows the CTA, no Jev calls are made, and articles stay unscored.
- After the first profile save, the backfill job scores the unscored unread backlog. (This is the same mechanism as the launch backfill and the D4 re-score. Build it once.)
- Seed suggestion (optional, LOW): pre-fill topic names from existing folder names as *suggestions*, never created automatically.

---

## Conflicts / Flags Against PROJECT.md

- **C1: "Profile/topic edits apply to new articles only" vs the expectation that editing interests changes what I see.** NewsBlur re-classifies recent stories on prompt save, and users now expect that. Under the current decision:
  - a newly added topic has zero effect on any existing article, and thumbs on older articles can never touch it;
  - an edited topic description leaves stale match values that refer to the old wording.
  The cost rationale is weak at Jev prices (about $0.00004 per call), and the unread backlog is bounded. **Recommendation:** keep automatic new-only scoring, but (a) say "applies to newly arriving articles" clearly in the editor, and (b) add the **manual "Re-score unread" button (D4)**, which reuses the backfill job. This keeps cost user-controlled and removes the trust gap. Requirements should decide this explicitly.
- **C2: Thumbs feedback only adjusts matched topics.** An article that matched no topic (or when no topics exist, only a profile) produces a vote with no effect. This violates table stakes T6/T7 ("feedback does something"). **Recommendation:** make the no-op visible and offer D3.
- **C3: Cold start vs "each newly ingested article is judged once".** Articles ingested while the profile is empty should stay *unscored* and not be judged against an empty profile. Otherwise they are permanently "scored" with garbage under the new-only policy. Treat an "empty profile" the same as "not configured" for scoring.
- **C4: Priority view reordering vs the existing cache invalidation.** Mutations in `useArticles.ts` invalidate `['articles']`, so in Priority a vote or mark-read reorders or drops rows under the cursor. This isn't a PROJECT conflict, but it's a table-stakes UX requirement (T8) that the frontend phase must handle explicitly.
- **No conflict:** the badge-only / Priority-only scope (sort-by-interest elsewhere is Out of Scope) is consistent with the reference products. Feedly also isolates priority in its own tab. Per-feed Priority tabs are a plausible later ask, not table stakes.

---

## Feature Dependencies

```
Jev client + stored raw outputs (profile Score, per-topic Noul, confidence)
    └──requires──> Interest profile (T9) + Topic rubric (T10)   [defines the questions]
                        └──enables──> Cold-start empty state (T13)

Blend formula (single implementation, query-time)
    ├──requires──> stored raw outputs + current topic weights
    ├──enables──> Priority view sort + score cursor (T1, T2)
    ├──enables──> Badge (T3, D7) ──enhances──> Priority unread count (D6)
    └──enables──> "Why" breakdown (T4, D1) ──includes──> Matched-topic chips (T5)

Thumbs feedback (T6) + per-article feedback record with deltas
    ├──requires──> stored per-topic matches + editable weights (T10)
    ├──requires──> blend formula (to show the instant effect, T7)
    ├──enhanced by──> Topic picker popover (D2), Create-topic-from-article (D3)
    └──conflicts──> auto-refetch reorder (T8 must be solved in the same frontend phase)

Background backfill job (unscored → scored)
    ├──enables──> Graceful degradation recovery (circuit open / key added later)
    ├──enables──> One-time launch backfill
    ├──enables──> Cold-start first scoring (T13)
    ├──enables──> Manual re-score unread (D4)
    └──enables──> Backfill progress line (T14)

Status endpoint (configured / circuit / unscored count) ──enables──> T12, T14
Keyboard shortcuts (T15) ──requires──> T1 route, T6 mutation, T4 panel
```

### Dependency Notes
- **Everything user-visible depends on one blend implementation.** If the SQL sort and the UI breakdown compute the score separately, they will drift and the "why" will contradict the ordering. Compute the blend in SQL (or one Java function) and return the components with each article.
- **The backfill job is the keystone:** four features (launch backfill, degradation recovery, cold start, manual re-score) are the same "drain unscored articles" loop. Build it once, early, in the backend phase.
- **T8 (list stability) must ship with T1/T6**, not after. Otherwise the first vote in Priority visibly scrambles the list.
- **Feedback needs the weight model finalized** (units, range, η, clamping), because the deltas stored for undo depend on it.

---

## MVP Definition

### Launch With (v1)
- [ ] Interest profile editor with writing guidance (T9) — nothing to judge against without it
- [ ] Topic rubric editor with signed weights, feedback-drift display, reset (T10/T11)
- [ ] Priority smart view: scored-first, "Not yet scored" separator, stable list (T1, T2, T8)
- [ ] Score badge with tier colors, unscored shows nothing (T3, D7)
- [ ] "Why" breakdown plus matched-topic chips (T4, T5, D1) — without it, feedback-driven weights are opaque
- [ ] Thumbs up/down, toggleable, exact undo, visible effect, visible no-op (T6, T7, C2 message)
- [ ] Not-configured / paused / cold-start states and backfill progress (T12, T13, T14)
- [ ] Keyboard: `u` / `d` / `i` / `g p`, plus the overlay update (T15)

### Add After Validation (v1.x)
- [ ] Manual "Re-score unread" (D4) — trigger: the first time a new topic "doesn't work" on visible articles. **Strongly consider pulling it into v1** (cheap, given the backfill job).
- [ ] Create-topic-from-article (D3) — trigger: frequent no-op feedback
- [ ] Topic picker on feedback (D2) — trigger: mixed-topic articles getting punished
- [ ] Topic test/preview (D5) — trigger: iterating on descriptions
- [ ] Priority unread count above threshold (D6)
- [ ] Per-topic stats (D9)
- [ ] "Mark below here as read" (D8)

### Future Consideration (v2+)
- [ ] Priority tab per feed/folder — only if the global view proves too broad
- [ ] Star as an optional explicit positive signal (setting, off by default)
- [ ] Sort-by-interest in other lists (currently Out of Scope)

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| T1/T2 Priority view + unscored separator | HIGH | MEDIUM | P1 |
| T8 List stability in Priority | HIGH | MEDIUM | P1 |
| T3/D7 Badge with tiers | HIGH | LOW | P1 |
| T4/T5/D1 "Why" breakdown + chips | HIGH | MEDIUM | P1 |
| T6/T7 Thumbs with undo + visible effect | HIGH | MEDIUM | P1 |
| T9 Profile editor | HIGH | LOW | P1 |
| T10/T11 Topic rubric editor | HIGH | MEDIUM | P1 |
| T12/T13/T14 Status, cold start, progress | MEDIUM | LOW | P1 |
| T15 Keyboard shortcuts | MEDIUM | LOW | P1 |
| D4 Manual re-score unread | MEDIUM–HIGH | LOW | P1/P2 (see C1) |
| D3 Create topic from article | MEDIUM | LOW–MEDIUM | P2 |
| D2 Topic picker on feedback | MEDIUM | MEDIUM | P2 |
| D5 Topic test/preview | MEDIUM | MEDIUM | P2 |
| D6 Priority count | LOW–MEDIUM | LOW | P2 |
| D9 Per-topic stats | LOW | LOW–MEDIUM | P3 |
| D8 Mark-below-read | MEDIUM | MEDIUM | P3 |

**Priority key:** P1: must have for launch. P2: should have, add when possible. P3: nice to have, future consideration.

---

## Competitor Feature Analysis

| Feature | Feedly AI | NewsBlur | TT-RSS / feeds.fun | Gmail | Our Approach |
|---------|-----------|----------|--------------------|-------|--------------|
| Interest definition | Priorities (topic/industry/events/like-board) | Classifiers + NL prompts | Point rules on tags/filters | Implicit | Free-text profile + weighted topic rubric |
| Ranking surface | Priority tab + nav section | Focus view | Sort by score | Important section | Priority smart view (unread, score desc) |
| Indicator | Green "why" label | Green/red, pills | Up/down score icon | Yellow marker | Numeric tiered badge |
| Explainability | Label + highlighted entities | Inline highlights + precedence banner | Tags visible | Hover one-liner | Exact arithmetic breakdown + chips |
| Feedback | Less-like-this picks topic; boards = positive | Thumbs per attribute, reversible | Click to adjust score | Toggle marker | Thumbs nudges matched-topic weights, reversible |
| Effect on backlog after edit | Ongoing | NL prompt re-classifies recent stories | Filters on import only | n/a | New-only automatically (+ recommended manual re-score) |
| Low scores | Mute filters hide | Disliked hidden in Focus | < −500 auto-read | Stay in inbox | Never hidden; sorted last |

---

## Sources

- Feedly: [Feedly AI and Mute Filters](https://feedly.com/new-features/posts/feedly-ai-and-mute-filters), [Refining Feedly AI Feeds](https://docs.feedly.com/article/549-refining-feedly-ai-feeds), [Track topics with Feedly AI](https://feedly.com/new-features/posts/track-specific-topics-and-trends-with-feedly-ai), [Feedly Leo review (green "why" label, priority types)](https://ai-productreviews.com/feedly-leo-review/). Official docs/blog, seam tier LOW, cross-checked across two sources.
- NewsBlur: [Intelligence Training](https://www.newsblur.com/features/intelligence-training), [Super dislikes and classifier notifications](https://blog.newsblur.com/2026/03/27/super-dislikes-and-classifier-notifications/), [Natural language text and image classifiers](https://blog.newsblur.com/2026/04/02/natural-language-text-and-image-classifiers/), [Intelligence Trainer overhaul](https://blog.newsblur.com/2026/01/22/intelligence-trainer-overhaul/). Vendor blog, seam tier LOW.
- Inoreader: [Sort by Magic and popularity indicators](https://www.inoreader.com/blog/2019/11/new-feature-sort-by-magic-and-article-popularity-indicators.html). Vendor blog (2019), LOW.
- Gmail: [Importance markers in Gmail](https://support.google.com/mail/answer/186543?hl=en). Official help, LOW per seam.
- Tiny Tiny RSS: [Scoring](https://tt-rss.org/docs/Scoring.html). Official docs, LOW per seam.
- Readwise: [Ghostreader overview](https://docs.readwise.io/reader/guides/ghostreader/overview). LOW.
- OSS rankers: [feeds.fun (Show HN)](https://news.ycombinator.com/item?id=43279239), [feeds.fun repo](https://github.com/Tiendil/feeds.fun), [PersonalRSS](https://github.com/andresdelcampo/PersonalRSS), [betternews](https://github.com/sxntixgo/betternews), [rosso](https://github.com/eetu/rosso). READMEs, LOW.
- Jev/TypeSafe: [Spring AI and TypeSafe Jev](https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment/) (no prose output, Score/Noul shapes, confidence, option-text guidance, pricing benchmark). Official Spring blog, LOW per seam.
- Codebase grounding: `src/main/frontend/src/hooks/useKeyboardShortcuts.ts` (taken keys incl. `+ = -`), `components/ShortcutOverlay.tsx`, `components/FeedPanel.tsx` (`smart-view`), `hooks/useArticles.ts` (invalidate-on-mutate), `components/SettingsDialog.tsx` (Raindrop "not configured" pattern). Read directly, HIGH.

---
*Feature research for: interest ranking in a single-user feed reader (myfeeder)*
*Researched: 2026-09-22*
