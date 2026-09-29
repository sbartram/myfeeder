# Pitfalls Research

**Domain:** Implicit engagement feedback (open original, star, board, Raindrop) added to myfeeder's derived, query-time learned-topic model (v0.3.0 Engagement Learning)
**Researched:** 2026-09-29
**Confidence:** HIGH for the codebase-specific pitfalls (verified against `InterestScoreQueries`, `ArticleScoreStore`, `ArticleService`, `BoardService`, `RaindropService`, `ReadingPane.tsx`, `useKeyboardShortcuts.ts`, V6 schema, replay script and drift test at `8fd4145`). MEDIUM for the browser-capture and ranking-bias pitfalls (MDN, Chromium and learning-to-rank sources, cross-checked).

The v0.2.1 file (Jev integration pitfalls 1-23) is archived with that milestone; the pitfalls it covered that still apply (derived-model shifts on Re-score, lost updates on `article`, stale-top items) are restated here only where engagement changes them.

## Code facts the pitfalls below depend on

| Fact | Where | Why it matters |
|------|-------|----------------|
| `LEARNED_CTE` aggregates **all** `article_feedback` rows on every read; it has no scope, window or decay | `InterestScoreQueries.LEARNED_CTE` | Engagement rows join the same always-on aggregate, and there will be 10-100x more of them than votes |
| The blend only credits articles with a `SCORED` `article_score` row | `LEARNED_CTE` `JOIN article_score fs ... status = 'SCORED'` | Engagement on an unscored article teaches nothing |
| Scoring eligibility is `read = false AND` inside the 14-day window; the dispatch recheck re-applies it | `ArticleScoreStore.ELIGIBLE` | An article read before scoring completes is **never** scored, and an engaged article is almost always read (auto-mark-read after 1 s) |
| Re-score deletes `SCORED`/`FAILED` rows inside the eligible (unread) scope | `ArticleScoreStore.RESCORE_SCOPE` | Starred-but-unread articles lose their nouls until re-judged |
| The sign clamp moves a zero base either way, a positive base never below 0, a negative base never above 0, all inside -50..+50 | `eff2` | A positive-only signal still erodes negative topics toward 0 |
| "Open original" is **client-only**: `window.open(url, '_blank', 'noopener')` from the button, `o` and content-link clicks; nothing reaches the server | `ReadingPane.tsx:115,137`, `useKeyboardShortcuts.ts:173` | The only engagement kind with no server-side hook |
| Star goes through the generic `PATCH /api/articles/{id}` (`updateState(id, read, starred)`), load-modify-save, not `@Transactional` | `ArticleService.updateState` | Star capture must detect the false-to-true transition inside a path that also toggles read |
| Board add is check-then-insert and silently no-ops on a repeat | `BoardService.addArticle` | Its natural idempotency must survive; one article can sit on several boards |
| Raindrop save throws on not-configured, disabled, no collection, breaker open | `RaindropService.saveToRaindrop`, `RaindropApiClientImpl` | Engagement must be recorded only after the bookmark succeeds |
| Retention only nulls `content`/`extracted_content`; articles are deleted only by feed delete (`article.feed_id ... ON DELETE CASCADE`) | `ArticleRepository.clearContentOlderThan`, V1 | Engagement rows live forever unless the feed goes; the V7 FK must cascade |
| Priority keyset cursor is the served `(sort_score, sort_date, id)` tuple; an **unserved** row whose score rises past the boundary is skipped (05-REVIEW WR-05) | `priorityPageAfter` | Engagement raises other articles' scores on every open, so the residual becomes routine |
| "Why N?" integer rows are apportioned by largest remainder from SQL `total`; `learned_applied = w - base` comes from SQL | `ScoreBreakdowns`, `breakdownInputs` | Splitting learned into two parts must preserve exact sums |
| Replay SQL must contain `blendCte(...)`, `LEARNED_CTE` and `INTEREST_SCORE` byte for byte; psql variables and JdbcClient params share the `:name` syntax | `InterestCalibrationReplaySqlTest`, `scripts/interest-calibration-replay.sql` | Every new bind constant must also be a psql `-v` variable with the identical name |
| Test `application.yaml` + `application-dev.yaml` must resolve every main key to main's value | `DevProfileConfigTest` | New `myfeeder.interest.blend.*` engagement keys break the suite unless mirrored |

## Critical Pitfalls

### Pitfall 1: Double counting (kinds, boards, and thumbs) by joining raw engagement rows into the topic sum

**What goes wrong:**
One article that is opened, starred, added to two boards and sent to Raindrop contributes five times to every matched topic. A thumbs-up on the same article adds a sixth. Topics hit the cap after a handful of saved articles, and "each article counts once, at its strongest engagement" is silently false.

**Why it happens:**
The natural extension of `learned` is `JOIN article_engagement e ON e.article_id = ts.article_id` followed by `SUM(weight(e.kind) * hinge)`. Every extra engagement row multiplies the topic rows before the `SUM`. A board-level row design (`article_id, board_id`) makes it worse, and forgetting the thumbs anti-join double counts voted articles.

**How to avoid:**
- Store one row per `(article_id, kind)` with that as the primary key. Kinds are `OPEN`, `STAR`, `BOARD`, `RAINDROP` (one `BOARD` row no matter how many boards). Store the **kind only, never a weight**, so calibration stays a query-time constant.
- Collapse to one strength per article **before** touching `article_topic_score`:
  ```sql
  eng AS (SELECT e.article_id,
            MAX(CASE WHEN e.kind = 'OPEN' THEN CAST(:engageOpenWeight AS float8)
                     ELSE CAST(:engageSaveWeight AS float8) END) AS strength
          FROM article_engagement e
          WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = e.article_id)
          GROUP BY e.article_id),
  eng_learned AS (SELECT ts.topic_id, SUM(g.strength * GREATEST(0, (ts.noul - 0.5) * 2)) AS eng_sum
          FROM eng g
          JOIN article_score s ON s.article_id = g.article_id AND s.status = 'SCORED'
          JOIN article_topic_score ts ON ts.article_id = g.article_id
          GROUP BY ts.topic_id)
  ```
- The thumbs override is **article-wide**: any `article_feedback` row (up or down, narrowed or not) removes that article from `eng`. A narrowed up-vote on topic A must not leave engagement crediting topic B of the same article.
- Tests (in `InterestScoreQueriesTest` style, real Postgres): open + star + 2 boards + Raindrop on one article yields exactly `saveWeight x hinge` per topic; adding a thumbs vote on it removes the engagement part entirely; a narrowed vote also removes it for the non-picked topics.

**Warning signs:**
A topic's engagement learned value is a multiple of the save weight larger than the number of engaged articles; the learned value changes when an already-saved article is added to a second board.

**Phase to address:** Learned-model phase (the `LEARNED_CTE` change). The Capture phase fixes the PK shape that makes it possible.

---

### Pitfall 2: Two caps plus the sign clamp break "Why N?" exactness and the learned split

**What goes wrong:**
"Why N?" shows base 20, thumbs +8, engagement +5 for a topic whose effective weight is 30 (not 33), or the sub-parts differ from the effective weight by 0.000001 after rounding, or the integer rows no longer sum to the badge. The Interests learned line and the thumbs effect toast disagree with the badge.

**Why it happens:**
- The learned part is clamped three times (thumbs cap, engagement cap, sign clamp and -50..+50 range). Attribution of a clamped amount between two sources is not linear. Clamping sequentially differs from clamping the sum: base 10, thumbs -20, engagement +5 gives `clamp(clamp(10-20)+5) = 5` but `clamp(10-20+5) = 0`.
- Rounding `thumbs_applied` and `eng_applied` to 6 decimals separately does not guarantee they sum to the rounded `w`.
- Rendering the split as extra integer point rows outside the largest-remainder apportionment breaks D-02 (rows sum to `total`).

**How to avoid:**
- Decide and write down the semantics in the Learned-model phase. Recommended, because it is monotone and always attributable:
  1. `t = clamp(learnRate x vote_sum, -learnedCap, +learnedCap)` (unchanged).
  2. `e = LEAST(engageCap, engageRate x eng_sum)`, always >= 0.
  3. `w = signClamp(base + t + e)`: one combined clamp, the value the ranking uses.
  4. `w1 = signClamp(base + t)`; `thumbs_applied = w1 - base`; `eng_applied = w - w1`.
  Because the clamp is monotone and `e >= 0`, `eng_applied >= 0`, `thumbs_applied` has the sign of `t`, and `base + thumbs_applied + eng_applied = w` exactly.
- Compute all of this in SQL in `eff2`/`contrib`, never in Java. Round `w`, `base` and `thumbs_applied`; derive the rounded `eng_applied` **by subtraction** in SQL so the decimal identity holds.
- State explicitly whether the maximum total learned is `learnedCap + engageCap` (additive, recommended, with `engageCap` well below `learnedCap`) or whether the combined learned is clamped to `learnedCap`. The milestone text ("its own cap below the thumbs cap") reads as additive; the UI and CLAUDE.md must say so.
- Show the split as a **weight annotation** on each topic row (e.g. "weight 33 = 20 base + 8 thumbs + 5 engagement"), keeping one integer point row per topic in the largest-remainder apportionment. Do not add point sub-rows.
- Extend `LearnedLimit` so each part reports its own limit (thumbs at cap, engagement at cap, range-clamped).
- Property-style test: for a grid of base in {-50,-10,0,10,50}, t in {-20,-5,0,5,20}, e in {0,3,engageCap}, assert `base + thumbs_applied + eng_applied = w`, `eng_applied >= 0`, and the breakdown rows sum to the badge.

**Warning signs:**
`ScoreBreakdowns` tests pass only with engagement at zero; a Why row where the annotation's parts do not add up; `eng_applied < 0` anywhere.

**Phase to address:** Learned-model phase (semantics and SQL), Explainability phase (annotation, `LearnedLimit`, toast).

---

### Pitfall 3: Engaged articles that are never scored, so the signal is silently lost and gap discovery reports false gaps

**What goes wrong:**
Engagement is recorded, but it never moves a topic, and the article shows up as a "gap" (matched no topic) when it was simply never judged.

**Why it happens:**
`ELIGIBLE` requires `read = false`. Selecting an article auto-marks it read after 1 s (`autoMarkReadDelay`), and opening or starring an article almost always happens in the reading pane. So any article engaged while Jev was unconfigured, the `jev` breaker was open, the queue was backed up, the profile was in cold start, or the article was older than 14 days (a board item, a search hit) becomes permanently ineligible. The dispatch recheck also drops articles that turn read while queued.

**How to avoid:**
- Keep the milestone rule "no Jev calls for engagement": do **not** widen `ELIGIBLE` to include read, engaged articles as a side effect. If scoring engaged-but-unscored articles is wanted, it is a separate, explicit, costed decision (bounded count, same sweep, one call per article), not a hidden change to the shared predicate that also drives status counts and Re-score.
- Gap discovery must require `article_score.status = 'SCORED'`. "Matched no topic" means scored with every hinge = 0, never "no noul rows".
- Surface the loss: add an `engagedUnscored` count to `/api/interest/status` (append-only field, per the status contract) or show "not scored, engagement not counted" in the reading pane for an engaged unscored article, mirroring the thumbs `scored: false` flag.
- Test: an engaged article with no score row, a FAILED row and a SKIPPED row contributes nothing and is never a gap suggestion.

**Warning signs:**
Prod replay shows many engagement rows but a near-zero engagement learned value; gap suggestions include articles with no badge.

**Phase to address:** Learned-model phase (SCORED join), Gap-discovery phase (SCORED requirement), Explainability phase (visibility).

---

### Pitfall 4: Rich-get-richer and saturation. Exposure bias pins every positive topic at the engagement cap and inflates the tiers

**What goes wrong:**
The Priority view puts high-scoring articles first and colors them green, so they are opened far more often. Their topics gain engagement weight, which raises every article sharing those topics, which gets them opened more. With no decay, every topic you routinely read reaches `engageCap` within weeks and stays there. The net effect is a uniform upward shift: more articles cross `tiers.high`, the 70/22 calibration drifts, and engagement stops discriminating between topics.

**Why it happens:**
Implicit clicks are conditioned on exposure and position; learning directly from them produces rich-get-richer feedback loops (Joachims et al., unbiased learning-to-rank). `LEARNED_CTE` sums history forever, so the only brake is the cap. Opens are frequent, so the cap is reached quickly.

**How to avoid:**
- Keep engagement strictly weaker than thumbs: `engageOpenWeight` well below `engageSaveWeight`, both well below the thumbs unit of 1, and `engageCap` roughly half of `learnedCap` or less. The cap is the loop's brake; it has to bind before the tier shift becomes visible.
- The existing hinge already limits damage (only topics with noul > 0.5 learn). Do not relax it for engagement.
- Record `created_at` on every engagement row now, so a later decay or window is possible without a migration. Do not add decay in this milestone unless calibration shows saturation: a time window makes the ranking change with no user action and breaks replay reproducibility unless the replay pins `now()`.
- Calibration must report, per topic id, the engagement learned value and whether it is at the cap, plus the tier histogram with engagement on vs. off (see Pitfall 11).

**Warning signs:**
Most topics show "engagement at limit" in Interests; the replay's `high_pct` rises release over release with unchanged thresholds; Priority's top 20 converges on one or two topics.

**Phase to address:** Learned-model phase (weights, cap, `created_at`), Calibration phase (saturation check).

---

### Pitfall 5: Unreliable "open original" capture (popup blockers, awaited requests, middle-click, sendBeacon)

**What goes wrong:**
Opens are missed or the open itself breaks: the new tab is blocked, middle-clicks and Cmd-clicks are never counted, the recording request is dropped, or a failed request surfaces an error toast when the user only wanted to read.

**Why it happens:**
- Open is purely client-side today. The obvious implementation, `await recordOpen(id); window.open(...)`, spends the transient user activation on the await, and the popup blocker kills the tab (MDN: popups must be opened in direct response to user input).
- `window.open(..., 'noopener')` **always returns `null`**, the same value a blocked popup returns (MDN), so the return value cannot confirm the open.
- `onClick` does not fire for middle-click (`auxclick`), and "Open in new tab" from the context menu fires no handler at all.
- `navigator.sendBeacon` with a `application/json` Blob throws a `SecurityError` in Chromium (since Chrome 59, non-CORS-safelisted types), and the project's JSON-only POST pattern (rescore, feedback) rules out `text/plain` beacons.
- Content-body link clicks (`handleContentClick`) also call `window.open`, but most point to other sites; counting them all adds noise.

**How to avoid:**
- Call `window.open` **synchronously first**, then fire the record request without awaiting: `fetch(url, { method: 'POST', keepalive: true, headers: { 'Content-Type': 'application/json' }, body })` with errors swallowed (no toast, no retry loop). The SPA does not navigate, so `keepalive` is only a safety margin.
- One helper (`openOriginal(article)`) used by the button, `o`, and any title link, so capture cannot drift between entry points. Skip the record when `article.url` is blank.
- If the title or button becomes an `<a href target="_blank" rel="noopener">`, handle `onAuxClick` (button 1) as well as `onClick`; accept that context-menu opens are not counted and say so.
- Count a content-link click only when its `href` equals `article.url`. Other links are not engagement for this article.
- The server endpoint accepts only `OPEN`. Saves are recorded server-side (Pitfall 6).
- Frontend tests (Vitest): `window.open` is called before `fetch`; a rejected `fetch` does not throw or toast; `o`, the button and a middle-click each record once.

**Warning signs:**
Open counts in prod far below the number of tabs you remember opening; "popup blocked" icon in the address bar after pressing `o`; console `SecurityError` from `sendBeacon`.

**Phase to address:** Capture phase.

---

### Pitfall 6: Saves recorded in the wrong layer (client-reported kinds, pre-success Raindrop, bulk paths)

**What goes wrong:**
A Raindrop save that failed (breaker open, no collection) still teaches the ranking. A frontend that records `STAR` separately from the PATCH records twice or not at all when a new entry point (list star button, keyboard `s`) forgets. A future bulk action stars 200 articles and floods the model.

**Why it happens:**
It is tempting to add one `POST /engagement` and call it from every button. The save actions already have authoritative server paths.

**How to avoid:**
- Record saves **inside the service write paths**, not from the client:
  - `ArticleService.updateState`: record `STAR` only on a `false -> true` transition of `starred` (read-only toggles and repeated stars record nothing).
  - `BoardService.addArticle`: record `BOARD` only when the row is actually inserted.
  - Raindrop: record `RAINDROP` only after `raindropApiClient.createBookmark` returns, outside the breaker-wrapped client (the service already runs validation outside it).
- Use `INSERT ... ON CONFLICT (article_id, kind) DO NOTHING` so repeats (keyboard spam on `s`, `v` retries after a failure, adding to a second board) are no-ops.
- Record only from user-facing single-article actions. OPML import, feed subscribe, polling and any future bulk operation must not record engagement; if a bulk star or bulk board-add is ever added, it records nothing (document this in CLAUDE.md Key Behaviors).
- Keep business-validation failures (409 disabled, 400 no collection, 503 not configured) free of side effects; test that each leaves no engagement row.

**Warning signs:**
Engagement rows with `RAINDROP` for articles not in Raindrop; `STAR` rows for articles that were starred before V7; row counts jumping by hundreds in a minute.

**Phase to address:** Capture phase.

---

### Pitfall 7: V7 schema and store mistakes (missing cascade, Spring Data JDBC `save()` with a preset id)

**What goes wrong:**
Unsubscribing a feed fails with a foreign-key violation because engagement rows reference its articles. Or the idempotent insert throws `IncorrectUpdateSemanticsDataAccessException` on the first engagement of an article.

**Why it happens:**
- A plain `REFERENCES article(id)` without `ON DELETE CASCADE` blocks the feed delete cascade (`article.feed_id ... ON DELETE CASCADE`).
- Spring Data JDBC treats an entity with a non-null `@Id` as existing and issues `UPDATE` on `save()`; a composite natural key has no clean `CrudRepository` mapping. Zero updated rows throws.

**How to avoid:**
- `article_engagement (article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE, kind TEXT NOT NULL CHECK (kind IN ('OPEN','STAR','BOARD','RAINDROP')), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (article_id, kind))`.
- Follow the `ArticleFeedbackStore` precedent: a `JdbcClient`-based `ArticleEngagementStore`, no `CrudRepository`.
- Add a repository test: delete a feed whose articles have engagement, votes and boards; the delete succeeds and no orphans remain.
- Update CLAUDE.md: the "Schema: V6 creates all six interest tables; later milestone phases add no migrations" line and the Flyway list become stale with V7.

**Warning signs:**
`DataIntegrityViolationException` on feed delete in logs; engagement writes failing only on first insert.

**Phase to address:** Capture phase.

---

### Pitfall 8: Reversibility semantics are left implicit (unstar and unboard vs. read-later workflows)

**What goes wrong:**
Either unstarring or removing from a board deletes engagement, which wipes the signal for people who use stars and boards as a read-later queue (add, read, remove), or it never does, and a misclicked `s` permanently teaches the model with no visible way to undo it. The milestone goal says every learned point stays "explainable and reversible", so the second outcome fails the goal.

**Why it happens:**
Event rows and current state diverge. "Save" in a feed reader is often a temporary queue, not an endorsement of the topic.

**How to avoid (recommendation):**
- Treat engagement as a **sticky fact**: unstar and board removal do **not** delete rows. Removing an item from a read-later queue is not disinterest, and the milestone is positive-only.
- Provide two explicit reversal paths, both already natural in the model: (1) any thumbs vote overrides the article's engagement (Pitfall 1), and (2) a "Forget engagement" control on the article (reading pane or its Why row) that deletes that article's engagement rows via a JSON-only `DELETE /api/articles/{id}/engagement`.
- Because the model is derived, deleting rows undoes the effect exactly; test that engage-then-forget restores every topic's effective weight to the pre-engagement value.
- If the user prefers state-derived saves instead (unstar withdraws), decide it in the Capture phase discussion, because it changes the table shape (saves derived from `article.starred` and `board_article` instead of stored).

**Warning signs:**
UAT question "how do I undo that?" has no answer; learned values drop after clearing a board.

**Phase to address:** Capture phase (decision and endpoint), Explainability phase (control placement).

---

### Pitfall 9: Positive-only engagement erodes deliberate negative topics

**What goes wrong:**
You set "crypto" to -30 but keep opening crypto headlines out of curiosity or irritation. Each open moves the negative topic toward 0 (the sign clamp lets a negative base rise to 0), so buried articles creep back into Priority.

**Why it happens:**
`eff2` treats the learned delta the same for every base sign. Thumbs-up on a negative topic is a deliberate act; an open is not.

**How to avoid:**
Apply engagement only to topics with `base >= 0` (a zero-base topic may rise; a negative base is left to thumbs and manual edits). Implement it in SQL (`CASE WHEN e.base < 0 THEN 0 ELSE ... END` for the engagement part) and document it in the Interests UI. Test that engagement never changes the effective weight of a negative-base topic.

**Warning signs:**
A negative topic's effective weight trending toward 0 with no thumbs votes.

**Phase to address:** Learned-model phase.

---

### Pitfall 10: Gap discovery that suggests duplicates, never clears, or bills Jev

**What goes wrong:**
- Suggestions list articles an existing topic nearly matched (noul 0.45; the v2 wording is known to under-fire), so you create a near-duplicate topic.
- After you create a topic from a suggestion, the suggestion stays forever: the article is read, so Re-score never re-judges it, it has no noul for the new topic, and it still "matches no topic".
- The new topic does not learn from the article it was created from, which looks like a bug.
- Reusing "Create topic from article" wires the one-call topic preview to each suggestion, so listing suggestions makes billed Jev calls.

**Why it happens:**
"No hinge > 0" is the easy predicate, and the derived model has no memory of what was already handled.

**How to avoid:**
- Gap = SCORED, engaged, not thumbs-voted-down, and **max noul across topics below a near-miss threshold** (e.g. 0.35, a query constant). Show the closest existing topic and its noul on each suggestion so a near miss can be answered by editing that topic instead.
- Store handled state: a small `engagement_gap_dismissed(article_id PK, created_at)` table (or a column on the engagement row) set by "dismiss" and by "create topic from this suggestion". Keep it in V7 so there is one migration.
- Say in the UI that a topic created now learns from future articles only (topics created after scoring have no nouls, R5).
- The suggestion list and the draft never call `/api/interest/preview`; preview remains an explicit button. Add a controller/service test with `verifyNoInteractions(jevApiClient)` for the suggestions endpoint.
- Rank suggestions by strength (save before open) then recency, and cap the list (e.g. 20).

**Warning signs:**
Two topics with near-identical descriptions; the same article suggested after its topic exists; `Jev ` log lines when opening the suggestions panel.

**Phase to address:** Gap-discovery phase (and V7 in the Capture phase if the dismissal table is chosen).

---

### Pitfall 11: Calibrating with no data and letting the drift guard fall behind the new CTE

**What goes wrong:**
- Engagement weights are "calibrated" at release with zero engagement rows in prod, so the replay proves nothing.
- The replay SQL keeps the v0.2.1 `LEARNED_CTE`, so it no longer reproduces the app's badges; or a new bind name like `engage_open_weight` works in JdbcClient but is not passed as a psql `-v` variable, so the replay errors or silently uses a different value.
- New `myfeeder.interest.blend.engagement.*` keys in main `application.yaml` without test-yaml/dev-overlay counterparts fail `DevProfileConfigTest`; tuned values placed in the test yaml break `InterestScoreQueriesTest` fixtures.

**Why it happens:**
The v0.2.1 pattern (release, then calibrate from real data, then a follow-up release) is easy to compress into one step. The drift test checks text, so it only protects what it is told to check.

**How to avoid:**
- Ship v0.3.0 with conservative engagement constants, let engagement accumulate for 2-4 weeks, then calibrate and release the tuned values (the same shape as v0.2.0 then v0.2.1). Alternatively, the replay can simulate a backfill (treat existing `starred` and `board_article` rows as `STAR`/`BOARD`) in a read-only CTE to preview day-one impact.
- Regenerate every blend line of the replay from the Java after the CTE changes; add `:engageOpenWeight`, `:engageSaveWeight`, `:engageCap` (same camelCase names as the JdbcClient params, each wrapped in `CAST(... AS float8)` as today) to the driver's `-v` list; extend `InterestCalibrationReplaySqlTest` to assert the new constants are passed and that an `engagement` section exists. Keep the replay's rules: read-only session, topic ids only, never names or descriptions.
- Add the replay outputs needed for Pitfall 4: per-topic `t`, `e`, at-cap flags, and the tier histogram with engagement zeroed vs. live.
- Put fixture values for the new keys in `src/test/resources/application.yaml`, the tuned values in main `application.yaml` and `application-dev.yaml`, and never tune through Helm `--set` or env (D-14).

**Warning signs:**
`InterestCalibrationReplaySqlTest` still green after a `LEARNED_CTE` change (it should fail until the replay is regenerated); replay badges disagree with the app on the same article.

**Phase to address:** Learned-model phase (drift guard and yaml parity in the same plan that changes the CTE), Calibration phase (data-driven tuning).

---

### Pitfall 12: The always-on learned CTE gets slower as engagement rows grow

**What goes wrong:**
Every list page (`displayScores`), every Priority page, every `GET /api/articles/{id}` (the blend runs twice in `breakdownInputs`), the Interests page and each feedback write recompute the whole learned model. Today that is tens of votes. With opens it becomes thousands of engagement rows times up to 25 topic rows, recomputed several times per click on a Turing Pi-class Postgres.

**Why it happens:**
The derived model was designed for a small vote table; engagement changes the row count by one or two orders of magnitude. The learned CTE is independent of the page scope, so it is repeated identically for every query.

**How to avoid:**
- Collapse engagement per article **first** (Pitfall 1's `eng` CTE) so the topic join sees one row per engaged, un-voted article, and join `article_topic_score` by its `(article_id, topic_id)` primary key.
- Postgres inlines a CTE referenced once and materializes one referenced more than once (PostgreSQL docs); keep `eng`/`eng_learned` referenced once, or mark them `MATERIALIZED` deliberately after checking `EXPLAIN ANALYZE`.
- Add a measured budget in the Learned-model phase: seed 20k engagement rows and 25 topics in a Testcontainers test or a one-off `EXPLAIN ANALYZE`, and record Priority first-page and by-id latency. Rough scale: a few hundred rows is negligible; 10k-50k rows is where it starts to show.
- Only if the budget fails: cache the per-topic learned result keyed by a "learning version" bumped on every vote, engagement, topic, Re-score and feed-delete write. Do not store learned values in `interest_topic` (that breaks exact reversibility).
- Do not return before/after weight effects from the open endpoint (that would run the learned CTE twice per open). Opens return 204.

**Warning signs:**
Priority page or reading-pane latency rising week over week; `pg_stat_statements` showing the blend as the top query.

**Phase to address:** Learned-model phase.

## Moderate Pitfalls

### Pitfall 13: Priority paging skips rows now that every open changes scores (WR-05 becomes routine)

**What goes wrong:** Opening or starring an article while walking Priority raises the score of unserved articles that share its topics; a row that rises above the served cursor tuple is skipped on the next page. With thumbs this was rare; with opens it happens on most triage sessions.
**Prevention:** Bound it (each engagement raises any article by at most `engageWeight x engageRate` points, and only near-boundary rows can cross), set `rankingChanged` on every successful engagement write the same way `useFeedback` does, and keep the frozen order. Add a test documenting the bound. Do not invalidate the Priority query on open, which would reshuffle the list under the cursor.
**Phase:** Capture phase (frontend hook), Learned-model phase (bound).

### Pitfall 14: Stale and self-referential badges after engagement

**What goes wrong:** After `o` or `s`, the reading pane's badge and Why for the current article change (its own topics just gained weight), while list badges stay stale until refetch. The user sees an article's score rise because they opened it.
**Prevention:** Invalidate only the by-id article query after an engagement write; leave list queries alone (badges catch up on the next refetch). Explain self-influence in the Why annotation ("includes +N from your engagement"). Do not exclude an article's own engagement from its own score; that would make the badge depend on which article is being viewed.
**Phase:** Explainability phase.

### Pitfall 15: The thumbs effect toast misreports after a vote on an engaged article

**What goes wrong:** Voting up on an opened article removes its engagement credit and adds thumbs credit; the toast's before/after effective weight shows a smaller change than the user expects, or even a drop at the engagement cap.
**Prevention:** `FeedbackResult` effects report thumbs and engagement parts before and after, and the toast wording says "replaces engagement". Test the up-vote-on-engaged case.
**Phase:** Explainability phase.

### Pitfall 16: Backfilling historical stars and boards without a decision

**What goes wrong:** A migration that inserts `STAR`/`BOARD` rows for every existing starred or boarded article moves several topics to the engagement cap on deploy, reshuffling Priority on release day. Not backfilling means a slow start.
**Prevention:** Decide explicitly in the Capture phase. Recommended: no backfill in V7 (opens cannot be backfilled anyway, so kinds would be unevenly represented), with the Calibration replay's simulated-backfill section available if the user wants it later as a one-off, reviewed SQL.
**Phase:** Capture phase (decision), Calibration phase (simulation).

### Pitfall 17: Derived values shift for reasons unrelated to engagement

**What goes wrong:** Engagement learned values change after a Re-score (unread starred articles lose nouls, then get new ones), a topic delete (its nouls cascade away), a topic description edit (old nouls keep teaching the redefined topic), or a feed delete (its articles and engagement cascade).
**Prevention:** Keep the v0.2.1 behavior (derived, no version filter) for consistency with thumbs, and document it next to the existing "learned values shift" note in CLAUDE.md Interest Ranking. Do not add a `topic_version` filter for engagement only; that would make the two learned parts behave differently under the same edit.
**Phase:** Learned-model phase (documentation).

### Pitfall 18: Tests that stop being offline, or engagement code that reaches Jev

**What goes wrong:** A new engagement or gap test pulls in the preview path, or a developer exports a TypeSafe key to try gap suggestions and the suite starts billing.
**Prevention:** Engagement and gap tests are DB-only (`@DataJdbcTest`, `@WebMvcTest` with `@MockitoBean JevApiClient` plus `verifyNoInteractions`). Add no new `spring.ai.typesafe.*` keys to test yaml. The existing rule stands: never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`.
**Phase:** Every phase; enforced in Capture and Gap-discovery plans.

### Pitfall 19: Lost updates on the star path

**What goes wrong:** `updateState` loads, mutates and saves the whole `article` row. Adding engagement recording there without a transaction lets a concurrent auto-mark-read PATCH clobber `starred`, or records `STAR` for a save that was then overwritten.
**Prevention:** Make the star path `@Transactional` with the engagement insert in the same transaction, or switch the starred write to a targeted `UPDATE article SET starred = true WHERE id = :id AND starred = false` whose row count decides whether to record `STAR`. The targeted update is the cleaner fix and removes the false-to-true detection problem.
**Phase:** Capture phase.

## Minor Pitfalls

### Pitfall 20: Endpoint shape drift
**What goes wrong:** An open endpoint that accepts form or text bodies can be triggered cross-site from a LAN page, faking engagement.
**Prevention:** `PUT`/`POST /api/articles/{id}/engagement` with `consumes = APPLICATION_JSON` (415 otherwise), body `{"kind":"OPEN"}`, 404 for an unknown article, fixed-text 400 for any other kind. Matches the feedback and rescore endpoints.

### Pitfall 21: Missing `url`
**What goes wrong:** `o` on an article with a blank `url` opens `about:blank` and records an open.
**Prevention:** The `openOriginal` helper returns early when the URL is blank, and the server rejects nothing extra (the client never sends it).

### Pitfall 22: Status and replay contracts
**What goes wrong:** Renaming or reshaping `/api/interest/status` fields, or printing topic names in the replay's new section.
**Prevention:** Append new status fields only; the replay prints topic ids only.

### Pitfall 23: Documentation drift
**What goes wrong:** CLAUDE.md, OpenWiki source docs and the Interest Ranking section still describe thumbs as the only learned signal and V6 as the last migration.
**Prevention:** The final phase updates CLAUDE.md (schema line, Interest Ranking learned model, Key Behaviors capture rules, throttle/tuning levers for the engagement constants). Do not hand-edit generated OpenWiki pages.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Store the engagement weight in the row | Simple SQL | Calibration needs a data migration; replay cannot try new weights | Never |
| Client posts every kind (`STAR`, `BOARD`, `RAINDROP`) | One endpoint | Missed or duplicate records per entry point; failed Raindrop saves counted | Never; only `OPEN` is client-reported |
| No `created_at` on engagement rows | One fewer column | Decay, windows, recency ordering of gaps need a migration | Never; it is free now |
| Clamp learned parts in Java for the Why view | Easier to read | Badge and Why can disagree (D-02 violated) | Never |
| Cache learned values in Redis without a version key | Faster reads | Stale ranking after votes, Re-score or deletes; reversibility breaks | Only with a learning-version key bumped on every relevant write, and only after a measured budget failure |
| Skip the dismissal table for gap suggestions | One fewer table | Suggestions never clear; user loses trust in the panel | Only if suggestions are rebuilt to exclude articles engaged before the newest topic's `created_at` (weaker, but migration-free) |
| Calibrate engagement at release without data | One release | Weights are guesses presented as calibrated | Acceptable if labelled conservative defaults and followed by a data-driven release |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Browser popup blocker | `await` the record request before `window.open` | Open synchronously in the handler, then fire-and-forget the record |
| `window.open` with `noopener` | Using the return value to confirm the open | It always returns `null` (MDN); record on intent, not on result |
| `navigator.sendBeacon` | Sending a JSON Blob | Chromium throws for non-CORS-safelisted types; use `fetch` with `keepalive: true` and a JSON body |
| Raindrop (Resilience4j) | Recording engagement before or around the breaker-wrapped call | Record after `createBookmark` returns, in `RaindropService`, outside the client bean |
| Jev / TypeSafe | Gap suggestions auto-running the topic preview | Preview only on an explicit click; `verifyNoInteractions(jevApiClient)` on suggestion endpoints |
| Replay (psql) | New bind names that are not psql variables, or unregenerated blend lines | Same camelCase names in JdbcClient and `-v`; regenerate from Java; drift test asserts it |
| Flyway | Editing V6 to add the table | New `V7__article_engagement.sql`; update the CLAUDE.md schema line |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Engagement rows joined to topics before collapsing per article | Learned CTE cost scales with kinds x boards | `eng` CTE: one row per article, then join topics | Immediately wrong; slow from ~10k rows |
| Learned CTE recomputed per query (list, Priority, by-id x2, Interests) | Reading-pane latency grows over months | Budget test with 20k seeded rows; version-keyed cache only if it fails | Noticeable around 10k-50k engagement rows on homelab Postgres |
| Invalidating every article list on each open | Refetch storms, list flicker, Priority reshuffles | Invalidate by-id only; set `rankingChanged` | Every open |
| Returning before/after weights on open | Two extra learned-CTE runs per open | Opens return 204 | Every open |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Open endpoint accepts non-JSON bodies | Any LAN page can inflate topics via cross-site form POST | JSON-only (`consumes`), 415 otherwise, same as feedback and rescore |
| Endpoint accepts arbitrary `kind` | Client can fabricate saves | Server accepts `OPEN` only; saves are server-side |
| Echoing the request body or article URL in error text | Log and response injection | Fixed-text 400/404, following GlobalExceptionHandler conventions |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No way to undo a misclicked star's learning | Distrust of the model | "Forget engagement" control plus thumbs override (Pitfall 8) |
| Why row shows one learned number | Can't tell deliberate from implicit learning | Weight annotation `base + thumbs + engagement` (Pitfall 2) |
| Engagement silently ignored on unscored articles | "Why didn't opening this change anything?" | "Not scored, engagement not counted" hint and a status count |
| Suggestions for near-miss topics | Duplicate topics | Show closest topic and its noul; near-miss threshold |
| Opening an article raises its own badge with no explanation | Looks like a bug | Annotation "includes +N from your engagement" |
| Negative topics creeping up | Buried articles return | Engagement ignores negative-base topics (Pitfall 9) |

## "Looks Done But Isn't" Checklist

- [ ] **Capture:** middle-click and `o` each record once; `window.open` runs before the request; a failed request does not toast.
- [ ] **Capture:** Raindrop failure (breaker open, no collection, disabled, not configured) leaves no row.
- [ ] **Capture:** starring an already-starred article, or adding to a second board, adds no row.
- [ ] **Capture:** deleting a feed with engaged articles succeeds (cascade).
- [ ] **Learned model:** open + star + 2 boards + Raindrop counts once at save weight; any thumbs vote removes it; a narrowed vote removes it for every topic.
- [ ] **Learned model:** `base + thumbs_applied + eng_applied = w` over the full grid; `eng_applied >= 0`; negative-base topics unchanged by engagement.
- [ ] **Learned model:** an engaged article with no SCORED row contributes nothing.
- [ ] **Explainability:** Why rows still sum to the badge with engagement present; the toast reports both parts.
- [ ] **Gap discovery:** unscored articles never appear; dismissed and "created" suggestions never reappear; no Jev interaction.
- [ ] **Calibration:** `InterestCalibrationReplaySqlTest` fails before the replay is regenerated and passes after; new constants are psql `-v` variables; `DevProfileConfigTest` green with the new keys.
- [ ] **Performance:** seeded 20k-row latency recorded for Priority first page and `GET /api/articles/{id}`.
- [ ] **Docs:** CLAUDE.md schema line, Interest Ranking section and Key Behaviors updated.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Double counting shipped | LOW | Fix the CTE; the model is derived, so the next read is correct with no data change |
| Saturation / tier bloat | LOW | Lower `engageCap` or weights in yaml and release (query-time constants, no Jev re-score); emergency: set the engagement weights to 0 |
| Bad engagement rows (e.g. failed Raindrop saves recorded) | LOW | Targeted `DELETE FROM article_engagement WHERE kind = ...`; derived model self-corrects |
| Missing cascade blocks feed delete | MEDIUM | New migration dropping and re-adding the FK with `ON DELETE CASCADE`; never edit a shipped migration |
| Learned CTE too slow | MEDIUM | Version-keyed cache of per-topic learned values; or an index/`MATERIALIZED` fix guided by `EXPLAIN ANALYZE` |
| Gap suggestions never clear | LOW-MEDIUM | Add the dismissal table in a follow-up migration |

## Pitfall-to-Phase Mapping

Suggested phase names; numbering continues after v0.2.1's Phase 7.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 Double counting | Capture (PK), Learned model (CTE) | Real-Postgres test: five engagement kinds on one article count once; vote overrides |
| 2 Caps, clamps, exact split | Learned model, Explainability | Grid property test; Why rows sum to badge |
| 3 Engaged but unscored | Learned model, Gap discovery, Explainability | Unscored/FAILED/SKIPPED fixtures contribute nothing and are never gaps |
| 4 Rich-get-richer / saturation | Learned model, Calibration | Replay per-topic at-cap report; histogram engagement on vs. off |
| 5 Open capture reliability | Capture | Vitest: open-before-fetch, auxclick, rejected fetch silent |
| 6 Saves in the wrong layer | Capture | Service tests per save path, including Raindrop failure cases |
| 7 V7 cascade and store | Capture | Feed-delete test with engaged articles; `ON CONFLICT` store test |
| 8 Reversibility decision | Capture (decide), Explainability (control) | Engage-then-forget restores exact prior weights |
| 9 Negative topics eroded | Learned model | Engagement never changes a negative-base topic |
| 10 Gap discovery quality | Gap discovery | Near-miss excluded; dismissal persists; `verifyNoInteractions(jevApiClient)` |
| 11 Calibration and drift guard | Learned model (guard, yaml), Calibration (tuning) | Drift test covers new CTE and variables; post-data replay recorded in a CALIBRATION doc |
| 12 CTE performance | Learned model | Seeded 20k-row latency budget recorded |
| 13 Priority paging skips | Capture (hint), Learned model (bound) | `rankingChanged` set on engagement; bound documented in a test |
| 14-15 Badge and toast staleness | Explainability | By-id refetch after engagement; toast shows both parts |
| 16 Backfill | Capture (decide), Calibration (simulate) | Decision recorded in PROJECT.md Key Decisions |
| 17 Derived shifts | Learned model | CLAUDE.md note updated |
| 18 Offline tests | All | No new TypeSafe keys in test yaml; Jev mocks verified |
| 19 Lost update on star | Capture | Targeted `UPDATE ... WHERE starred = false` row count drives `STAR` |

## Sources

- Codebase at `8fd4145`: `InterestScoreQueries.java` (LEARNED_CTE, blendCte, breakdownInputs), `ArticleScoreStore.java` (ELIGIBLE, RESCORE_SCOPE), `ArticleService.updateState`, `BoardService.addArticle`, `RaindropService.saveToRaindrop`, `ScoreBreakdowns.java`, `V1`/`V6` migrations, `ReadingPane.tsx`, `useKeyboardShortcuts.ts`, `priorityStore.ts`, `scripts/interest-calibration-replay.sql`, `InterestCalibrationReplaySqlTest.java` (HIGH)
- `.planning/PROJECT.md` v0.3.0 milestone scope and Key Decisions; root `CLAUDE.md` Interest Ranking, Gotchas (DevProfileConfigTest, test yaml fixtures), Key Conventions (HIGH)
- MDN, [Window.open()](https://developer.mozilla.org/en-US/docs/Web/API/Window/open): `noopener` returns `null`; blocked popups return `null`; popups require direct user input (MEDIUM, official docs)
- MDN, [Navigator.sendBeacon()](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/sendBeacon): recommends `fetch` with `keepalive` when request properties must be set (MEDIUM)
- Chromium, [Intent to Temporarily Remove: sendBeacon() with a non-CORS-safelisted Blob type](https://groups.google.com/a/chromium.org/g/blink-dev/c/dAfYF2gauw4), [chromestatus 5654267444592640](https://chromestatus.com/feature/5654267444592640), [cypress#7115](https://github.com/cypress-io/cypress/issues/7115): `application/json` Blob beacons throw `SecurityError` (MEDIUM, cross-checked)
- PostgreSQL docs, [WITH Queries](https://www.postgresql.org/docs/current/queries-with.html): CTE inlined when referenced once, materialized when referenced more than once; `MATERIALIZED`/`NOT MATERIALIZED` override (HIGH)
- Joachims, Swaminathan, Schnabel, [Unbiased Learning-to-Rank with Biased Feedback](https://arxiv.org/abs/1608.04468); [InfoRank](https://arxiv.org/abs/2401.12553); [Unbiased Recommender Systems with Implicit Feedback](https://arxiv.org/abs/2608.16704): position and exposure bias in click feedback, rich-get-richer loops (MEDIUM; the mitigations here are simplified for a single-user app, not the papers' counterfactual estimators)
- Spring Data JDBC entity-state behavior (a non-null `@Id` means `save()` issues `UPDATE`), consistent with the project's `JdbcClient`-based `ArticleFeedbackStore` precedent (HIGH from project precedent; not re-fetched this session)

---
*Pitfalls research for: implicit engagement feedback on a derived, query-time learned-topic model*
*Researched: 2026-09-29*
