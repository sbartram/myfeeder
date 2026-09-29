# Phase 5: Blend & Priority View - Context

**Gathered:** 2026-09-25
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers a Priority view (route `/priority`, feed-tree entry, `g p`). It lists unread articles by a blended interest score that is computed at query time from the raw Jev outputs Phase 4 stored. Unscored articles come after the scored ones, ordered by date, below a "Not yet scored" separator. The phase also adds a tier-colored 0–100 badge on articles, an exact "Why N?" breakdown with matched-topic chips in the reading pane, list stability while triaging, and the four status states (not configured / cold start / scoring paused / N waiting). Requirements: PRIO-01..PRIO-08.

Out of this phase:
- thumbs feedback (Phase 6; the blend's `learned` input returns 0 here)
- the ≥70 count on the Priority item (PRIO-V2-02)
- sort-by-interest elsewhere (PRIO-V2-03)
- "mark all below as read" (PRIO-V2-01)

</domain>

<decisions>
## Implementation Decisions

### Carried forward (locked by research and prior phases, not re-discussed)
- **Formula (research R1/R6, 03-CONTEXT):**
  - `p = profile_score / profile_max_level` (0 when there was no profile question)
  - `m_t = max(0, (noul_t − 0.5) × 2)`
  - `raw = ROUND(100 × p + Σ m_t × w_t, 6)`
  - `display = clamp(0, 100, round(raw))`

  Sorting uses `raw`; the badge and breakdown use `display`. `w_t` = base weight + learned, and learned is 0 until Phase 6. The CTE must keep a `learned` input so Phase 6 can fill it in, and Phase 6's learned CTE must honor `topics_narrowed` (03 D-02). Constants go under `myfeeder.interest.blend.*`.
- **One source of truth:** a single blend CTE (a JdbcTemplate text-block constant, per research ARCHITECTURE Pattern 4) drives the sort, the badge and the breakdown. The frontend never recomputes the score from its own formula for display. The breakdown's per-row numbers must come from the same stored inputs and constants.
- **Topics added after scoring** contribute 0 (`LEFT JOIN` + `COALESCE`). There's no Σ|w| normalization (R5). Deleted topics cascade away.
- **Cursor (R4):** `PaginatedResponse` stays unchanged, with a `Long` article-id cursor. The keyset tuple is `(COALESCE(score,'-Infinity'), COALESCE(published_at, fetched_at), id)`, all DESC. The cursor-article lookup is **not** unread-scoped, because the cursor article may have just been read. If the cursor article is gone, restart from page 1. The client dedupes by `id` across pages.
- **Tiers:** ≥70 high, 40–69 neutral, <40 muted. Unscored articles get no badge, and "0" is never rendered for them.
- **Status inputs:** `/api/interest/status` `{configured, breakerState, coldStart, eligibleUnscored, failed}`. The field semantics are fixed (04 D-11/D-12). `isColdStart()` is the single predicate. The cold-start CTA reuses `MainLayout`'s `interestsOpen` (03-05).

### "Why N?" breakdown
- **D-01:** The breakdown lives **only in the reading pane**, as a collapsible "Why N?" row under the article title. `i` toggles it, and the open/closed state persists across articles for the session. There's no hover tooltip or popover in the list.
- **D-02:** **Integer rows that add up exactly.** Row contributions are rounded with largest-remainder so they sum exactly to `round(raw)`. When `raw` falls outside 0–100, a final line says so explicitly, e.g. "Total 112 → capped at 100". The last line always equals the badge. Rows are ordered by absolute contribution. Each topic row shows match % (hinge) × weight = points, and the profile row shows its points.
- **D-03:** **Non-matching topics are hidden** (hinge = 0 contributes nothing). A collapsed footer, "Show N non-matching topics", reveals them with their match %.
- **D-04:** The profile row carries a **level label** (e.g. "Strongly"). `article_score` stores only the continuous `profile_score`, not per-level probabilities, so derive the label from the nearest level index (`round(profile_score)` → one of 5 short labels mapped from `InterestQuestions.PROFILE_LEVELS`). No migration.
- **D-05:** **Not included:** a low-confidence hint, "scored before topic X existed" / stale-profile notes, and clickable chips. Keep the breakdown to math rows + the level label.
- **D-06:** **Matched-topic chips are always visible** in the reading pane next to the badge, even when the breakdown is collapsed: `[82] Rust · WebAssembly · Politics− · Why 82? ▸`. Chips are colored by the sign of their contribution, reusing the Phase 3 `.weight-positive` / `.weight-negative` colors (`--toast-success-text` / `--toast-error-text`), and are not clickable. Chips aren't shown in list rows.

### Triage stability & refresh
- **D-07:** **Read rows stay in place, dimmed** with the existing `.read` style, until the user refreshes or re-enters the view. This covers every row, not only the selected one. Today's `ArticleList` `preserved` logic protects only the selected article, so Priority needs a broader mechanism: the Priority query is excluded from `['articles']` invalidation, and read/star state is updated in place with `setQueryData`. `j`/`k` walk every row, including read ones.
- **D-08:** **Re-ranking is explicit.** A ↻ refresh button is always in the Priority toolbar. After a profile/topic save in the Interests dialog, or when `/status` shows more articles scored, the button gets a subtle "Ranking changed — refresh" hint. Nothing re-sorts until the user clicks it or re-enters the view (re-entry refetches from page 1).
- **D-09:** **No re-rank on window refocus.** Turn off `refetchOnWindowFocus` for the Priority query. At most, refocusing lights the hint.
- **D-10:** **Paging** uses the same "Load more" button as the other lists (no auto-load on scroll). Criterion 1's "infinite scroll" wording is satisfied by cursor paging that crosses the scored/unscored boundary with no duplicates or gaps.
- **D-11:** **`j` on the last loaded row** in Priority fetches the next page, then selects that page's first row. This is Priority only; other lists keep today's behavior, where `j` stops at the end.

### Priority entry & states
- **D-12:** **Feed-tree placement:** Priority is the first smart view, above All Articles, and shows **no count**. Its icon must not be a star glyph (Starred already uses one). It gets `useMatch('/priority')` active state and is excluded from All's active state.
- **D-13:** **The entry is always shown**, including when Jev isn't configured. The view then explains why (Raindrop precedent: "not configured by the administrator").
- **D-14:** **Status states** show as **one slim themed banner** under the toolbar, and the list always renders below it: scored articles, then the "Not yet scored" separator, then unscored articles by date. Precedence is not configured > cold start > scoring paused (breaker OPEN) > "N articles waiting to be scored" (`eligibleUnscored > 0`), and only one banner shows at a time. Cold start adds a "Set up interests" button that opens `InterestsDialog`.
- **D-15:** **Unscored segment:** one "Not yet scored" segment, by date. Unread articles older than the 14-day window, which will never be scored, stay in it with no extra separator. They aren't excluded either, so PRIO-02 holds as written.
- **D-16:** **The "Mark all read" toolbar button is hidden** in Priority, consistent with `Shift+A` being disabled (PRIO-07). The Priority toolbar is the title plus refresh.
- **D-17:** **No failed count** in the banner. Permanently failed articles just stay unscored, and Re-score in the Interests dialog is the recovery path.

### Badge reach & look
- **D-18:** **Badges appear in every list** (All, Feed, Folder, Starred, Board, Priority) and in the reading pane, **read articles included**. Every article response (list pages, board pages, `GET /api/articles/{id}`, Priority) is enriched with `interestScore` (display value; null when unscored) from the same blend CTE. This uses the enrichment variant scoped by id, not by unread. Sorting by score still happens only in Priority. — **Reversibility:** costly — the `interestScore` field on every article response becomes a contract that ArticleList, ReadingPane, BoardArticleList and the Phase 6 thumbs/badge updates all consume.
- **D-19:** **Position:** a leading, fixed-width pill before the title in list rows. In Priority, unscored rows keep the empty slot so titles stay aligned. Whether other lists reserve the slot is left to Claude's discretion.
- **D-20:** **Tier colors reuse existing theme variables.** No new vars are added across the 6 themes:
  - high: filled `--accent` background with `--accent-text`
  - neutral: outlined, `--text-secondary`
  - low: `--text-muted`, no fill

  Green and red stay reserved for chip signs.
- **D-21:** **Clicking a badge in the list** does nothing special: it just selects the row. The breakdown is reached with `i` or the pane's "Why N?" row.

### Claude's Discretion
- The Priority endpoint path and shape, e.g. `GET /api/articles/priority?cursor=&limit=` → `PaginatedResponse<Article>`. Also the class split (`InterestScoreQueries` / `PriorityService`), and how the breakdown data reaches the pane: a per-article breakdown endpoint, or fields on the single-article response. Either way it comes from the same CTE inputs and constants.
- How the "Ranking changed" hint is detected (a flag set on Interests save or topic mutation success, plus a comparison of `/status` counts), and whether `/status` is polled while Priority is open. Reuse `useInterestStatus`'s existing 15s conditional polling.
- How `j`/`k` get the Priority list. Today `MainLayout` builds its own `useArticles(...)` from `selectedFeedId`, not from the route, so it must be aligned so the keyboard hook walks the Priority query's rows in ranked order.
- The 5 short profile level labels (e.g. None / Passing / Partly / Mainly / Core) and exact banner copy.
- Whether non-Priority lists reserve an empty badge slot for unscored rows.
- How auto-mark-read on select (`autoMarkReadDelay`) behaves in Priority. It keeps working, and D-07 keeps the row in place.
- Index support for the Priority query (e.g. a partial index on unread), following research PITFALLS "Priority sort without a supporting index" with `EXPLAIN`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` §Priority View — PRIO-01..PRIO-08; §v2 PRIO-V2-01..03 (explicitly deferred)
- `.planning/ROADMAP.md` §Phase 5 — success criteria 1–5, notes (single CTE, `learned` = 0 until Phase 6, keyset/TanStack research flag)
- `.planning/PROJECT.md` §Key Decisions — score in points on 0–100, blend at query time, Priority = unread by blended score then unscored by date

### Blend formula, cursor and stability (research)
- `.planning/research/SUMMARY.md` §R1 (units and formula), §R4 (Priority cursor and no-invalidate policy), §R5 (topics added after scoring), §R6 (hinge)
- `.planning/research/ARCHITECTURE.md` §Pattern 4 (the blend CTE, enrichment variant, keyset page query, cursor resolution; note that its −3..3 weight clamp is superseded by R1's −50..+50 points) and §Pattern 5 (the learned CTE shape Phase 6 fills in)
- `.planning/research/FEATURES.md` §T2/T3/T4/T8/T12 and §"UX Detail Recommendations" (badge format and tiers, breakdown layout)
- `.planning/research/PITFALLS.md` §Pitfall 10 (unstable Priority pagination, the NULL segment), §Pitfall 21 (badge semantics), §Performance traps (index for the Priority sort)

### Prior phase decisions
- `.planning/phases/03-interest-model-schema-rubric-editor/03-CONTEXT.md` — R1/R2/R6 carry-forward, feedback schema D-01/D-02 (`topics_narrowed`), status D-04/D-05, hinge display D-13
- `.planning/phases/04-scoring-pipeline-backfill-sweep/04-CONTEXT.md` — eligibility predicate, D-08 (paused state covers auth failures), D-11/D-12 (status count semantics)

### Schema
- `src/main/resources/db/migration/V6__interest_scoring.sql` — `article_score` (profile_score, profile_max_level, profile_confidence, status), `article_topic_score` (noul per topic), `interest_topic` (weight −50..50), `article_feedback` / `article_feedback_topic`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ArticleService.findFiltered` (`src/main/java/org/bartram/myfeeder/service/ArticleService.java:55`): the cursor-article lookup plus `limit + 1` → `PaginatedResponse.of(...)` pattern for the Priority service to follow.
- `ArticleScoreStore` (`repository/`): Phase 4's JdbcTemplate store holding the eligibility predicate. The blend CTE / `InterestScoreQueries` sits alongside it in the same style.
- `InterestQuestions.PROFILE_LEVELS` / `PROFILE_MAX_LEVEL`: the source for the level labels (D-04).
- `useInterestStatus` (`src/main/frontend/src/hooks/useInterest.ts:16`): already polls `/status` every 15s while `configured && !coldStart && eligibleUnscored > 0`. Reuse it for the banner.
- `ArticleList` (`src/main/frontend/src/components/ArticleList.tsx`): toolbar, "Load more", the `preserved` selected-row logic, and the `.read` dimming style.
- `.weight-positive` / `.weight-negative` (`App.css:589-590`): the sign colors for chips.
- `InterestsDialog` + `MainLayout` `interestsOpen` state (`App.tsx`): the cold-start CTA target.

### Established Patterns
- `useUpdateArticleState` / `useMarkRead` (`hooks/useArticles.ts`) invalidate `['articles']` on success. The Priority query must use a query key outside that prefix, or the invalidation must exclude it (D-07).
- Query key `['articles', filters]` via `useInfiniteQuery`, page size 50, `nextCursor` → `getNextPageParam`.
- Keyboard: g-chords live in `useKeyboardShortcuts.ts` (`g a` / `g s` / `g b` exist; `g p` and `i` are free). `Shift+A` is currently gated on `selectedFeedId`.
- Theme colors are only CSS variables from `src/themes.ts`: `--accent`, `--accent-text`, `--text-secondary`, `--text-muted`, `--toast-success-text`, `--toast-error-text`.
- Spring Data JDBC entities: `Article` is a Lombok bean, and the enrichment adds an `@Transient interestScore` (research: `BeanPropertyRowMapper`).

### Integration Points
- `App.tsx` `MainLayout` routes: add `/priority`. `MainLayout`'s keyboard article list must follow the Priority query on that route.
- `FeedPanel.tsx` smart views (`~line 229`): add Priority first, with active state from the route.
- `ReadingPane.tsx` header (`~line 184`, `<h1 className="article-title">`): the badge, chips and "Why N?" row go under the title.
- Every article-returning controller path (article list, board articles, by-id) gets `interestScore` enrichment (D-18).

</code_context>

<specifics>
## Specific Ideas

- Breakdown mock the user approved:
  ```
  [82] Rust · WebAssembly · Politics− · Why 82? ▾
    Profile match (Strongly) ...... +64
    Rust 86% × +20 ............... +17
    WebAssembly 50% × +14 ........ +7
    Politics 20% × −30 ........... −6
    ─────────────────────────────
    Score                          82
    Show 6 non-matching topics ▸
  ```
  With a cap: `Total 112 → capped at 100`.
- Banner mock: `⏸ Scoring paused — 312 articles waiting`, a single line under the toolbar with the list below.

</specifics>

<deferred>
## Deferred Ideas

- The low-confidence hint and "scored before topic X existed / older profile" notes in the breakdown were offered and not selected. Revisit if scores prove confusing.
- Clickable chips that open the Interests dialog on that topic: not selected. Maybe a later polish item.
- A separate "Too old to score" segment for unread articles outside the 14-day window: not selected.
- A failed-count mention in the Priority banner: not selected.
- Already in v2 requirements: the ≥70 count on the Priority item (PRIO-V2-02), sort-by-interest on all lists (PRIO-V2-03), and "mark all below as read" (PRIO-V2-01).

### Reviewed Todos (not folded)
- "Tune Raindrop resilience and fix CLAUDE.md AspectJ note" (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): unrelated to Priority. The todo matcher found no match for this phase.

</deferred>

---

*Phase: 05-blend-priority-view*
*Context gathered: 2026-09-25*
