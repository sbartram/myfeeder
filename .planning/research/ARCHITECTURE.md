# Architecture Research

**Domain:** Implicit engagement feedback (opens and saves) layered onto myfeeder's existing query-time interest ranking (v0.3.0 Engagement Learning)
**Researched:** 2026-09-29
**Confidence:** HIGH for the integration points (read directly from the code at `8fd4145`). MEDIUM for the design recommendations: several of them are product decisions, flagged as **Decision** below.

Sources are the codebase itself: `InterestScoreQueries`, `ArticleFeedbackStore`/`ArticleFeedbackService`, `ScoreBreakdowns`, `ArticleService`, `BoardService`, `RaindropService`, `ArticleController`, `V6__interest_scoring.sql`, `scripts/interest-calibration-replay.{sh,sql}`, `InterestCalibrationReplaySqlTest`, and on the frontend `ReadingPane.tsx`, `useKeyboardShortcuts.ts`, `useArticles.ts`, `useBoards.ts`, `useFeedback.ts`, `usePriorityArticles.ts`, `priorityStore.ts`, `WhyBreakdown.tsx`, `TopicRow.tsx`, `FeedbackNotice.tsx` and `InterestsDialog.tsx`. There were no external library questions, because every feature reuses what is already there: Spring Data JDBC `JdbcClient`, Flyway, TanStack Query and Zustand.

## Standard Architecture

### System Overview

Existing components are plain. New components are marked `[NEW]`, modified ones `[MOD]`.

```
┌──────────────────────────────── Frontend (React) ─────────────────────────────────┐
│ ReadingPane [MOD]          useKeyboardShortcuts [MOD]     InterestsDialog [MOD]   │
│  ↗ Open Original ─┐         'o' ─┐                        + Suggested topics      │
│  ★ Star, 📋 Board, 🔖, 💧│         │                        TopicRow LearnedLine [MOD]│
│                   ▼              ▼                        WhyBreakdown [MOD]      │
│            useOpenOriginal() [NEW] ── fire-and-forget POST ───────────┐           │
│            onEngaged(qc) [NEW]: learned/by-id/suggestions invalidate, │           │
│              Priority row patch + "Ranking changed" hint              │           │
│ useUpdateArticleState / useAddArticleToBoard / useReadLater /         │           │
│   useSaveToRaindrop [MOD]: call onEngaged on success                  │           │
└───────────────────────────────────────────────────────────────────────┼───────────┘
                                                                        │ HTTP
┌──────────────────────────────── Backend (Spring MVC) ─────────────────┼───────────┐
│ ArticleController [MOD]                                               ▼           │
│   PATCH /{id} (star) ──► ArticleService.updateState [MOD] ──┐  POST /{id}/engagement [NEW]
│   POST /{id}/raindrop ─► RaindropService.saveToRaindrop [MOD]┤    └► ArticleEngagementService [NEW]
│ BoardController                                             │         (OPEN_ORIGINAL only, 404,
│   POST /boards/{id}/articles ─► BoardService.addArticle [MOD]┤          returns article + breakdown)
│                                                             ▼                     │
│                                   ArticleEngagementStore [NEW] (JdbcClient,       │
│                                   INSERT … ON CONFLICT DO NOTHING)                │
│ InterestController [MOD]: GET /api/interest/suggestions,                          │
│   POST /api/interest/suggestions/{articleId}/dismiss ─► TopicSuggestionService [NEW]
│                                                                                   │
│ InterestScoreQueries [MOD]: LEARNED_CTE gains engaged + eng_learned CTEs and the  │
│   thumbs/engagement split in eff/eff2/contrib (the single source of sort, badge,  │
│   Why rows and learned weights, so they stay consistent)                          │
│ ScoreBreakdowns [MOD] (passes the split through, apportionment unchanged)         │
│ ArticleFeedbackService.learnedTopics / LearnedLimit / TopicLearned [MOD]          │
└───────────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────── PostgreSQL ───────────────────────────────────────┐
│ V7 [NEW]: article_engagement (article_id, kind) PK, topic_suggestion_dismissal    │
│ V6 (unchanged): article_score, article_topic_score, article_feedback(+_topic),    │
│   interest_topic                                                                  │
└───────────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────── Ops tooling ──────────────────────────────────────┐
│ scripts/interest-calibration-replay.sql/.sh [MOD] + InterestCalibrationReplaySqlTest [MOD] │
│ (the replay must copy the new blendCte text byte for byte; the build fails otherwise) │
└───────────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Status | Responsibility |
|-----------|--------|----------------|
| `V7__article_engagement.sql` | NEW | `article_engagement` table (one row per article and kind, idempotent) and `topic_suggestion_dismissal`, plus an optional backfill from `article.starred` and `board_article` (Decision D-A). All v0.3.0 schema goes in this one migration, following the V6 precedent. |
| `ArticleEngagementStore` (repository) | NEW | `record(articleId, kind) → boolean inserted` (`INSERT … ON CONFLICT (article_id, kind) DO NOTHING`) and `find(articleId)` (kinds plus timestamps). It has no dependencies other than `JdbcClient`, so it can be injected into `ArticleService`, `BoardService` and `RaindropService` without cycles. It never writes `interest_topic`. |
| `ArticleEngagementService` | NEW | Backs the client-reported open (`OPEN_ORIGINAL` only). It validates the kind with a fixed-text 400, returns 404 for a missing article, records, and returns `EngagementResult{recorded, article}` with the re-blended badge and breakdown, like `FeedbackResult`. |
| `ArticleService.updateState` | MOD | When the request's `starred == TRUE`, records `STAR` in the same transaction (add `@Transactional`). `withScores` runs after the insert, so the returned badge already includes the nudge. |
| `BoardService.addArticle` | MOD | Records `BOARD`. This covers BoardManager, 🔖 Read Later and `b`, because all three go through `POST /api/boards/{id}/articles`. |
| `RaindropService.saveToRaindrop` | MOD | Records `RAINDROP` only after `raindropApiClient.createBookmark` returns. The breaker, the fallback and the business-rule exceptions all throw before that line, so a failed save never counts. |
| `InterestScoreQueries.LEARNED_CTE` / `blendCte` | MOD | Adds the `engaged` (strongest kind per article, excluding thumbs-voted articles) and `eng_learned` (per topic) CTEs; `eff` gains `eng_raw`/`eng`; `eff2` gains `w_thumbs`; `contrib` gains `thumbs_applied`/`engagement_applied`. Every consumer (Priority, badges, Why, `topicWeights`, `allTopicWeights`) inherits the change, because they are all built from this one constant. |
| `TopicContribution` / `TopicWeight` / `TopicLearned` / `InterestBreakdown.Row` / `Article` | MOD | Fields are appended, never renamed (the project convention). Details are in Pattern 3. |
| `TopicSuggestionService` + endpoints | NEW | Lists gap-discovery suggestions (engaged, SCORED, matched no topic, not voted 👎, not dismissed) and dismisses one. It is read-only apart from the dismissal row and never calls Jev. |
| `useOpenOriginal` (frontend hook) | NEW | The only way to open an original link: `window.open` runs synchronously, then a fire-and-forget POST follows. Used by ReadingPane's two "Open Original" buttons and the `o` shortcut. |
| `onEngaged(qc, res?)` (frontend helper) | NEW | One shared reaction to any engagement: invalidate `['interest','learned']`, `['interest','suggestions']`, `['articles']` and the by-id articles; patch the Priority row; set the "Ranking changed" hint. |
| Replay script, SQL and drift guard | MOD | The regenerated blend lines, new psql variables (`openWeight`, `saveWeight`, `engagementCap`) validated in the driver, and new `engagement` and extended `learned` sections. The test keeps asserting that the file holds the verbatim text. |

## Recommended Project Structure

Only new or touched files are shown. They follow the existing package layout (see root CLAUDE.md).

```
src/main/resources/db/migration/
└── V7__article_engagement.sql               # NEW: engagement + suggestion dismissal (+ optional backfill)

src/main/java/org/bartram/myfeeder/
├── model/
│   ├── EngagementKind.java                 # NEW enum: OPEN_ORIGINAL, STAR, BOARD, RAINDROP
│   ├── ArticleEngagement.java              # NEW record: kinds, strongest class, counted/overridden (by-id payload)
│   ├── InterestBreakdown.java              # MOD: Row appends thumbsLearnedWeight, engagementLearnedWeight
│   └── Article.java                        # MOD: @Transient engagement (by-id only, like feedback)
├── repository/
│   ├── ArticleEngagementStore.java         # NEW
│   ├── TopicSuggestionQueries.java         # NEW (or a method on InterestScoreQueries; keep it read-only)
│   └── InterestScoreQueries.java           # MOD: LEARNED_CTE, blendCte, records, binds engagement params
├── service/
│   ├── ArticleEngagementService.java       # NEW
│   ├── EngagementResult.java               # NEW record {recorded, article}
│   ├── TopicSuggestionService.java         # NEW
│   ├── ArticleService.java                 # MOD: STAR capture, engagement on findByIdWithBreakdown
│   ├── BoardService.java                   # MOD: BOARD capture
│   ├── ScoreBreakdowns.java                # MOD: pass split fields through (no apportionment change)
│   ├── ArticleFeedbackService.java         # MOD: learnedTopics carries engagement
│   ├── TopicLearned.java / LearnedLimit.java # MOD
├── integration/RaindropService.java        # MOD: RAINDROP capture after success
├── controller/
│   ├── ArticleController.java              # MOD: POST /{id}/engagement (JSON-only)
│   ├── EngagementRequest.java              # NEW record {kind}
│   └── InterestController.java             # MOD (or new TopicSuggestionController): suggestions routes
└── config/MyfeederProperties.java          # MOD: interest.blend.engagement.{open-weight,save-weight,cap}

src/main/frontend/src/
├── api/articles.ts, api/interest.ts        # MOD: recordOpen, suggestions, dismiss
├── hooks/useEngagement.ts                  # NEW: useOpenOriginal, onEngaged
├── hooks/useArticles.ts, useBoards.ts      # MOD: onEngaged in star/board/read-later/raindrop success
├── hooks/useKeyboardShortcuts.ts           # MOD: 'o' → useOpenOriginal
├── hooks/useInterest.ts                    # MOD: useTopicSuggestions, useDismissSuggestion
├── components/ReadingPane.tsx              # MOD: Open Original via hook; engagement line
├── components/WhyBreakdown.tsx             # MOD: TopicLabel shows votes vs engagement split
├── components/TopicRow.tsx                 # MOD: LearnedLine shows both parts
└── components/InterestsDialog.tsx          # MOD: Suggested topics section → draft row
```

### Structure Rationale

- **The store is separate from the service** because three existing services need to write engagement, and none of them should depend on an interest-domain service; each would otherwise risk a dependency cycle through `ArticleService`. `ArticleFeedbackStore` already follows this split.
- **The SQL model stays in `InterestScoreQueries`.** The class Javadoc says it is "the single source of truth for the Priority sort and the interest badge". Engagement must live inside `LEARNED_CTE` and nowhere else, or the sort, the badge, the Why rows and the Interests line can drift apart.

## Architectural Patterns

### Pattern 1: Capture at the owning server-side action; only "open original" is client-reported

**What:** Each save is recorded by the backend service that performs the save. Only the open, which happens entirely in the browser, is reported by the client, through a dedicated endpoint that accepts no other kind.

**Reliability comparison (the core question):**

| Capture point | Frontend event | Backend side effect | Recommendation |
|---------------|----------------|---------------------|----------------|
| STAR | Several call sites (toolbar button, `s`, future ones); lost if the tab closes before the extra POST; double-counts if both layers report | One place (`updateState`), atomic with the star write, covers every client path | **Backend** |
| BOARD | Three paths (BoardManager, Read Later with its two-call `getOrCreateByName` + `addArticle`, `b`) | One place (`BoardService.addArticle`) | **Backend** |
| RAINDROP | The client can't reliably tell a real save from a fallback | Recorded after `createBookmark` returns, so breaker-open, 503 not-configured, 409 disabled and 400 no-collection all throw first | **Backend** |
| OPEN_ORIGINAL | The only place it happens (`window.open`) | Only possible with a redirect endpoint (`GET /api/articles/{id}/open` → 302): a side-effecting GET, an open redirect to feed-supplied URLs, and it breaks the codebase's JSON-only-writes rule | **Frontend**, via a JSON-only POST |

**Trade-offs:** The open signal is best-effort: a failed POST loses one open, and it fails silently with no toast. That is acceptable for a fractional, capped, positive-only signal. The endpoint accepts `OPEN_ORIGINAL` only (400 otherwise), so a save kind can never be counted twice.

**Example (frontend):**
```typescript
// hooks/useEngagement.ts
export function useOpenOriginal() {
  const qc = useQueryClient()
  const { mutate } = useMutation({
    mutationKey: ['engagement'],
    mutationFn: (id: number) => articlesApi.recordOpen(id), // POST {kind:'OPEN_ORIGINAL'}, JSON
    meta: { inlineError: true },                            // silent: no toast on failure
    onSuccess: (res) => { if (res.recorded) onEngaged(qc, res.article) },
  })
  return useCallback((article: Article) => {
    if (!article.url) return
    window.open(article.url, '_blank', 'noopener') // FIRST and synchronous: keeps user activation
    mutate(article.id)                             // never awaited before window.open
  }, [mutate])
}
```

**Example (backend):**
```java
// ArticleService.updateState, now @Transactional
Article saved = articleRepository.save(article);
if (Boolean.TRUE.equals(starred)) {
    engagementStore.record(saved.getId(), EngagementKind.STAR); // idempotent; unstar never deletes (D-C)
}
withScores(List.of(saved));
```

### Pattern 2: Engagement as a derived, capped, overridable second learned term in `LEARNED_CTE`

**What:** This extends the existing derived model rather than adding a parallel one. Engagement is never stored as a weight, so deleting an engagement row, casting a vote or deleting a topic changes the result on the next read, the same way votes do today.

**Rules as SQL:**
- *One per article, at the strongest kind:* `MAX(CASE kind WHEN 'OPEN_ORIGINAL' THEN :openWeight ELSE :saveWeight END)` grouped by `article_id`.
- *Thumbs override:* `NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = e.article_id)`. Any vote (up, down, narrowed or not) removes the article's whole engagement contribution.
- *Counts only once scored:* join `article_score … status = 'SCORED'`, as votes do (D-03).
- *Hinge-weighted per matched topic:* `strength × GREATEST(0, (noul − 0.5) × 2)`, the same shape as a vote.
- *Own cap, positive only:* `eng = LEAST(:engagementCap, :learnRate × eng_sum)`, which is never negative because strengths and hinges are both non-negative.
- *Thumbs-first attribution:* `w_thumbs = signclamp(base + learned)`, `w = signclamp(base + learned + eng)`, `thumbs_applied = w_thumbs − base`, `engagement_applied = w − w_thumbs`. The two parts sum exactly to the existing `learned_applied = w − base`. Explicit votes claim room under the sign clamp and the ±50 range first, so engagement never "steals" a vote's effect.

**Sketch (keep `WITH learned AS` as the opening token, because the drift guard's `BLEND_START` regex looks for it):**
```sql
WITH learned AS (… unchanged thumbs CTE …),
engaged AS (SELECT e.article_id,
    MAX(CASE WHEN e.kind = 'OPEN_ORIGINAL' THEN CAST(:openWeight AS float8)
             ELSE CAST(:saveWeight AS float8) END) AS strength
  FROM article_engagement e
  WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = e.article_id)
  GROUP BY e.article_id),
eng_learned AS (SELECT ts.topic_id, SUM(g.strength * GREATEST(0, (ts.noul - 0.5) * 2)) AS eng_sum
  FROM engaged g
  JOIN article_score gs ON gs.article_id = g.article_id AND gs.status = 'SCORED'
  JOIN article_topic_score ts ON ts.article_id = g.article_id
  GROUP BY ts.topic_id),
eff AS (SELECT t.id, t.name, t.weight AS base,
    <learned_raw, learned as today>,
    CAST(:learnRate AS float8) * COALESCE(g.eng_sum, 0) AS eng_raw,
    LEAST(CAST(:engagementCap AS float8), CAST(:learnRate AS float8) * COALESCE(g.eng_sum, 0)) AS eng
  FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id LEFT JOIN eng_learned g ON g.topic_id = t.id),
eff2 AS (SELECT e.*, <signclamp(base + learned)> AS w_thumbs, <signclamp(base + learned + eng)> AS w FROM eff e)
-- contrib: … e.w - e.base AS learned_applied, e.w_thumbs - e.base AS thumbs_applied, e.w - e.w_thumbs AS engagement_applied …
```

**Trade-offs:** The CTE is recomputed on every list, badge and Priority read. That costs one more aggregate over a small table joined to `article_topic_score` by its primary key, which is negligible at single-user scale. The CTE is no longer fully symmetric with votes, and this is intended: engagement is positive-only and cannot be narrowed.

**Decision D-B (negative-base topics):** Taken literally, "an engagement is a fractional up-vote" means that opening an article which matched a −30 topic softens that topic, bounded by the engagement cap and never flipping sign. **Lean: keep the literal semantics**, consistent with an un-narrowed 👍. The alternative, one `CASE WHEN t.weight < 0 THEN 0` in `eff`, is trivial if the user wants engagement to affect only topics with base ≥ 0. Settle this in requirements, because it changes the SQL text and therefore the replay.

### Pattern 3: The explainability split lives inside a topic row's weight, never as extra rows

**What:** Why rows are profile + matched topics, and their integer points are apportioned by largest remainder so they sum exactly to `total = ROUND(raw)` (D-02). Engagement already flows through `w`, so `points = hinge × w` and the invariant is untouched. The split is a decomposition of the weight, `weight = baseWeight + thumbsLearnedWeight + engagementLearnedWeight`, printed in the row's label and tooltip, the way D-11 prints base + learned today.

**Field plan (append only):**
- `TopicContribution` / `InterestBreakdown.Row`: keep `learnedWeight` as the total applied learned part (old clients and the existing `base + learned = weight` tests keep passing), and append `thumbsLearnedWeight` and `engagementLearnedWeight`. **Rounding rule:** compute `engagementLearnedWeight = ROUND(w,6) − base − ROUND(thumbs_applied,6)` rather than rounding the difference independently, so the parts sum exactly at 6 decimals.
- `TopicWeight` (used by `topicWeights`/`allTopicWeights`, the vote toast and Interests): append `engagementRaw` and `engagement`. `effective` now includes engagement.
- `TopicLearned`: keep `learned` meaning the capped thumbs part, and append `engagementLearned` and `engagementAtCap`. `LearnedLimit.of` must use `base + learned + engagement` for the SIGN_CLAMP and WEIGHT_RANGE checks, or the Java limit will disagree with the SQL `w`.
- `Article` (by-id only, like `feedback`): append `engagement: {kinds[], strongest: OPEN|SAVE, counted: boolean, overriddenByVote: boolean}`, so the reading pane can say "Saved: counts as ½ vote" or "Overridden by your 👍".

**Frontend:** `WhyBreakdown.TopicLabel` changes from `(+20 +3.0 learned)` to something like `(+20 · votes +2.0 · engagement +1.0)`. `TopicRow.LearnedLine` changes from "Learned from votes +x · Effective weight +y" to add "· from opens/saves +z (at max)". Neither recomputes anything; both print server values, as today.

### Pattern 4: Gap discovery as a read-only derived list plus one dismissal row

**What:** `GET /api/interest/suggestions` returns engaged articles that are SCORED, have no topic with a hinge above 0 (the same predicate as `matchedTopicIds`), have no 👎 vote, have no dismissal row, and have `created_at` inside a window (for example 30 days). They are sorted by strongest kind, then most recent engagement, and capped at about 10. Each item carries `{articleId, title, feedTitle, strongest, engagedAt}`. Clicking "Create topic" seeds the existing `TopicDraft` (`description = title.trim().slice(0,500)`, `weight: 20`) as a new row in the open Interests dialog, adapting `addDraft` to accept a draft. A successful save, or a "Not a topic" click, posts the dismissal.

**Why a dismissal table is needed:** Articles are never re-judged (D-21). An article therefore never gets a noul for a topic created from it, and it would stay "unmatched" forever. Any derived rule that avoids state (for example, "only articles judged against every current topic") would clear *all* suggestions whenever any topic is added. One `topic_suggestion_dismissal(article_id PK → article ON DELETE CASCADE)` table is the smallest correct fix, and it belongs in V7.

**Anti-scope:** No clustering and no LLM-drafted topic descriptions. Both would need Jev or Anthropic calls, which breaks the milestone goal of no extra Jev calls. See the anti-patterns.

## Data Flow

### Request Flow: open original (client-reported)

```
'o' or ↗ button → useOpenOriginal(article)
   ├─ window.open(url, '_blank', 'noopener')          (synchronous, inside the user gesture)
   └─ POST /api/articles/{id}/engagement {"kind":"OPEN_ORIGINAL"}  (JSON-only → 415 for simple CSRF)
        → ArticleEngagementService.recordOpen(id)
            → 404 if no article; store.record(id, OPEN_ORIGINAL) → inserted?
            → findByIdWithBreakdown(id)  (badge + Why + engagement, same read path as GET)
        ← {recorded, article}
   onSuccess (recorded only) → onEngaged(qc, article)
```

### Request Flow: save (server-side capture)

```
★ / 's'            → PATCH /api/articles/{id} {starred:true}  → updateState → record STAR → withScores
📋 / 🔖 / 'b'       → POST /api/boards/{b}/articles {articleId} → addArticle → record BOARD
💧 / 'r'?          → POST /api/articles/{id}/raindrop          → saveToRaindrop → createBookmark OK → record RAINDROP
onSuccess (each existing mutation) → onEngaged(qc)   (star can also patch interestScore from the PATCH response)
```

### Read flow: every badge, Priority page, Why and Interests line

```
InterestScoreQueries.blendSql(...) binds profilePoints, learnRate, learnedCap, openWeight, saveWeight, engagementCap
  LEARNED_CTE (learned → engaged → eng_learned → eff → eff2) → contrib → blended → keyed
```
All the constants are bound in `learnedSql()`, which is already the single binding point. Add the three new parameters there, so `topicWeights()` gets them automatically.

### State Management (TanStack Query / Zustand reactions)

| Cache | On engagement | Reason |
|-------|---------------|--------|
| `PRIORITY_KEY` (`['priority']`) | **Never invalidated.** `patchPriorityArticle(qc, id, {interestScore})` when the response carries the article; otherwise leave the row alone | Frozen order while triaging (D-06/D-07); any refetch reloads every page and re-ranks |
| `usePriorityStore.rankingChanged` | `setRankingChanged(true)` on every recorded engagement, set unconditionally rather than route-gated | The store resets the hint on Priority re-entry and refresh, so setting it off-route is harmless. An engagement moves other articles' scores, which is exactly WR-05's "an unserved row can rise past the boundary" case |
| `['interest','learned']` | invalidate | The Interests learned line now includes engagement |
| `['interest','suggestions']` [NEW] | invalidate on engagement, vote (👎 removes a suggestion), topic create/delete and dismiss | Derived list |
| `['articles']` | invalidate (star already does) | Badges in the chronological lists shift; the order is by date, so no reorder |
| `['article', n]` (length 2) | `setQueryData` for the engaged id from the response; invalidate the others (the same predicate as the vote's D-05, which skips `['article', n, 'extracted']`) | The Why rows and the engagement line are by-id only |
| `['boardArticles']`, `['boards']` | unchanged | — |

A `recorded: false` response (repeat open, or an open on an already-saved article) triggers no invalidation or hint, so rapid `o` presses cost nothing. For board and Raindrop saves, which return `void`, call `onEngaged(qc)` without the patch step. It is fine to accept a false "Ranking changed" hint on a repeat save, because saves are rare and deliberate.

### Key Data Flows

1. **Thumbs vote on an engaged article:** the vote row appears, the `engaged` CTE drops the article, and the article's engagement contribution disappears in the same read. The vote toast prints the server's before/after, which now includes that removal. For example, a 👍 on a saved article shows +1.5 rather than +2.0. The value is truthful; the copy may need a note (see Pitfalls).
2. **Removing the vote:** the engagement contribution comes back automatically, because the model is derived.
3. **Topic deleted:** `article_topic_score` cascades, so engagement on its articles stops counting for that topic. Suggestions are unaffected, since the dismissal table is keyed by article.
4. **"Re-score unread":** deletes the score rows of unread articles. Engaged articles are usually read (auto-mark-read), so their contribution survives. An engaged-but-unread article stops counting until it is re-scored, the same as votes today.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Single user, now | No change. `article_engagement` grows by roughly tens of rows per day (thousands per year). The engaged and eng_learned CTEs aggregate through the `(article_id, kind)` primary key and the `article_topic_score` primary key |
| Years of history | The learned model sums all history (as votes already do). If list latency grows, `EXPLAIN` the blend first. The lever is a time window or decay on `engaged` (for example, the last 180 days), which is a query-time change needing no migration. Don't add it speculatively |

### Scaling Priorities

1. **First bottleneck:** the whole blend CTE on every Priority and list page, not the engagement part. It is already measured in production as fine.
2. **Second bottleneck:** none plausible for a single user.

## Anti-Patterns

### Anti-Pattern 1: Reporting saves from the frontend

**What people do:** Add `POST /engagement {kind:'STAR'}` next to the star PATCH.
**Why it's wrong:** A save then has two writers (double capture or a missed path), a failed Raindrop save can be counted, and every new save path has to remember to report.
**Do this instead:** Record the save in the service that performs it. The engagement endpoint accepts `OPEN_ORIGINAL` only.

### Anti-Pattern 2: Awaiting the engagement POST before `window.open`

**What people do:** Use `await recordOpen(id); window.open(url)` so the record "surely happens".
**Why it's wrong:** Browsers only allow popups during transient user activation, and Safari especially rejects a `window.open` that follows an await. The open silently fails, which is worse than losing one engagement.
**Do this instead:** Call `window.open` first and synchronously, then fire and forget. `fetch(..., {keepalive: true})` is optional hardening; the page doesn't unload, because the link opens in a new tab.

### Anti-Pattern 3: Storing or writing learned engagement points

**What people do:** Add `engagement_points` to `interest_topic`, or bump `weight` on each open.
**Why it's wrong:** It breaks "every learned point stays explainable and reversible", "a vote never writes a topic's weight", and exact undo by vote or delete.
**Do this instead:** Keep engagement a derived term in `LEARNED_CTE`. The only stored facts are "article X was engaged with kind K at time T" and "suggestion X was dismissed".

### Anti-Pattern 4: Separate "Engagement" rows in Why N

**What people do:** Add a row per topic for engagement points, or one global "Learned from engagement" row.
**Why it's wrong:** Those points are already inside each topic's `hinge × w`. Extra rows double-count, or force a rework of the largest-remainder apportionment, and the rows would stop summing to the badge.
**Do this instead:** Split the topic row's *weight* into base, votes and engagement in its label and tooltip (Pattern 3).

### Anti-Pattern 5: Counting noisy signals

**What people do:** Record selection, auto-mark-read, reader view, dwell time, Copy Link, or clicks on links inside the article body (`handleContentClick`).
**Why it's wrong:** The PROJECT.md scope excludes them: j/k skimming and auto-enabled reader view are noise, and in-body links point elsewhere.
**Do this instead:** Capture only the two "Open Original" buttons, `o`, and the three save actions. Leave `handleContentClick` untouched.

### Anti-Pattern 6: Invalidating the Priority query on engagement

**What people do:** Call `invalidateQueries(['priority'])` so the new ranking shows up.
**Why it's wrong:** It re-ranks under the user and reloads every loaded page (research Pattern 5, D-07/D-09).
**Do this instead:** Patch the row and set the hint; the user refreshes when ready.

### Anti-Pattern 7: Changing `LEARNED_CTE` without regenerating the replay in the same commit

**What people do:** Edit the Java CTE and plan to "update the script later".
**Why it's wrong:** `InterestCalibrationReplaySqlTest.replaysTheAppsUnreadBlendVerbatim` asserts that the SQL file contains `blendCte(UNREAD_SCOPE)` byte for byte, so `./gradlew test` goes red. Also, `WRITE_KEYWORD` rejects the words insert, update, delete, create, drop, alter, truncate, grant, copy and into anywhere in replay statements, so no kind name or CTE alias may contain those words (`COPY_LINK` would trip it).
**Do this instead:** Put the CTE change, the regenerated replay lines, the new psql `-v` variables with regex validation in the driver, and the extended test in one plan.

### Anti-Pattern 8: Using an LLM for gap discovery

**What people do:** Cluster engaged titles, or draft topic descriptions, with Jev or the (unused) Anthropic starter.
**Why it's wrong:** It breaks the "no extra Jev calls" goal and the cost model, and adds a failure mode to a feature that should be a pure read.
**Do this instead:** List articles and reuse the title-as-description draft; the user edits it in the dialog.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Jev (TypeSafe) | **None new.** Engagement reuses the stored nouls | An engaged article that is not yet scored counts once it is scored, same as votes |
| Raindrop.io | Capture after `RaindropApiClient.createBookmark` returns | The breaker fallback rethrows, so no row is recorded on failure. Keep the capture in the service, outside the `@CircuitBreaker`-annotated client bean |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| ArticleService / BoardService / RaindropService → ArticleEngagementStore | Direct call (idempotent INSERT) | An `ArticleEngagedEvent` + `@TransactionalEventListener` would work but adds indirection for no gain here. Events in this codebase exist to decouple scheduling from transactions, which is not the problem being solved |
| ArticleController → ArticleEngagementService | Direct | JSON-only `consumes`, like `/feedback` and `/rescore` (a simple cross-site POST gets 415) |
| ArticleFeedbackService ↔ engagement | Implicit, through the SQL only | No Java coupling. The override lives in the CTE's `NOT EXISTS` |
| InterestScoreQueries ↔ replay script | Byte-for-byte text contract, test-enforced | Extend the driver with `OPEN_WEIGHT`, `SAVE_WEIGHT` and `ENGAGEMENT_CAP` env vars (decimal regex) and pass them via `-v` |
| MyfeederProperties ↔ yaml ↔ DevProfileConfigTest | Config | New keys `myfeeder.interest.blend.engagement.{open-weight,save-weight,cap}` must resolve in the test yaml plus the dev overlay to main's values (`DevProfileConfigTest`). Pin the test yaml to fixed values that `InterestScoreQueriesTest` fixtures assume. Tune only in committed yaml (D-14), never with Helm `--set` |
| TopicDraft (FeedbackNotice ↔ InterestsDialog) | Existing prop | The suggestions section needs a way to add a draft row after the dialog has seeded (today the draft is seeded once, at open). Widen `addDraft` to accept a `TopicDraft` |

### Decisions to settle in requirements (they change schema or SQL text)

- **D-A: backfill in V7?** Insert `STAR` from `article.starred` and `BOARD` from `board_article` (`MIN(added_at)`). **Lean: yes.** It is idempotent, only SCORED articles count, and it gives the calibration replay real save data from day one. Opens and Raindrop saves have no history to backfill.
- **D-B: do engagement nudges apply to negative-base topics?** Lean: yes, the literal fractional up-vote (Pattern 2).
- **D-C: do unstarring and removing from a board retract the engagement?** **Lean: no.** Emptying Read Later after reading is the normal workflow, and deleting the row would erase the signal exactly when interest was confirmed. Reversal is through a thumbs vote (override). Optionally add `DELETE /api/articles/{id}/engagement` as an explicit "forget", but it can be deferred.
- **D-D: separate caps.** Thumbs keep ±`learned-cap` (20); engagement gets its own cap in `[0, +cap)` (a provisional 10), so total learned can reach 30 before the sign clamp and ±50 bound it. The alternative, one shared cap, contradicts "its own cap below the thumbs cap". Add a startup check or test that `engagement.cap < learned-cap` and `save-weight > open-weight ≥ 0`.
- **D-E: provisional weights.** Open 0.25 and save 0.5 (as fractions of one vote, scaled by the existing `learn-rate` 2) mean +0.5 and +1.0 points per full-match engagement. Calibration sets the final values.

## Suggested Build Order

The dependencies set the order. Phase numbers are left to the roadmapper.

1. **Engagement capture (backend + frontend, ranking unchanged).** V7 (both tables, plus the D-A backfill if chosen), `EngagementKind`, `ArticleEngagementStore`, capture in `updateState`/`addArticle`/`saveToRaindrop`, `POST /{id}/engagement`, `useOpenOriginal` wired to both buttons and `o`. Tests: a V7 migration test (the `V4StripRaindropApiTokenMigrationTest` pattern if backfilling), store idempotency, "no row on Raindrop failure", the controller's 415/400/404. *Ship this as its own release if possible:* the ranking doesn't change, and every day it runs in production accumulates the real data that step 5 needs. The replay cannot calibrate against a table that doesn't exist in prod yet.
2. **Learned model extension.** `LEARNED_CTE`/`blendCte`/`contrib` split, the new config props bound in `learnedSql()`, the `TopicContribution`/`TopicWeight`/`TopicLearned`/`LearnedLimit`/`Row` fields, `ScoreBreakdowns` pass-through, and **in the same plan** the regenerated replay SQL, driver variables and drift guard. Tests: `InterestScoreQueriesTest` cases for strongest-kind, override, the cap, thumbs-first attribution under the sign clamp, parts summing exactly, and Why rows still summing to the badge. If step 1 shipped alone, main yaml can keep `engagement.cap: 0` until step 5, so this step is behavior-neutral in production. The dev overlay sets provisional values for UAT.
3. **Explainability + cache reactions (frontend + by-id payload).** `Article.engagement`, the WhyBreakdown label split, the TopicRow LearnedLine, the reading-pane engagement line, `onEngaged` wired into star, board, Read Later, Raindrop and open, the Priority patch plus hint, and vote-toast copy for the override case. This depends on step 2's fields.
4. **Gap discovery.** The suggestions query, endpoints, dismissal, the Interests "Suggested topics" section and `addDraft(draft)`. It depends only on step 1 (the tables) plus the existing matched-topic predicate, so it can be planned in parallel with steps 2–3. Order it after them only if a single stream of work is preferred.
5. **Calibration + rollout.** Replay candidate `OPEN_WEIGHT:SAVE_WEIGHT:ENGAGEMENT_CAP` sets against the engagement accumulated in production, then record the result in a calibration note like `07-CALIBRATION.md`. Put the tuned values in main `application.yaml` and `application-dev.yaml`, update CLAUDE.md (the Interest Ranking section, V7, and the throttle/tuning levers), and release a minor version (new schema): `./gradlew release -Prelease.versionIncrementer=incrementMinor`.

**Research flags:** Step 2 is the risky one: SQL correctness, the drift guard, and exact 6-decimal sums. It deserves a careful plan-phase with TDD fixtures. Step 4 needs a small UX decision (where suggestions appear, and the dismissal copy). Steps 1, 3 and 5 follow established patterns.

---
*Architecture research for: implicit engagement learning on myfeeder's query-time interest ranking*
*Researched: 2026-09-29*
