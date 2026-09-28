# Phase 5: Blend & Priority View - Research

**Researched:** 2026-09-25
**Domain:** Query-time score blending in PostgreSQL (Spring `JdbcClient`), composite keyset pagination over a computed sort key, TanStack Query v5 infinite-query cache control, React keyboard/route wiring
**Confidence:** HIGH (the SQL was prototyped against real PostgreSQL 18.6 with the project's own migrations; the TanStack behavior was confirmed from library source/docs; the frontend and backend wiring was read from the current code)

## Summary

Most of this phase's design is already locked in 05-CONTEXT.md and 05-UI-SPEC.md. This research checks those decisions against the real code and a real database, and turns them into concrete SQL, cache rules and wiring. I applied the project's V1/V2/V3/V5/V6 migrations to a throwaway `postgres:latest` (18.6), seeded scored, unscored, FAILED, SKIPPED and read articles, and ran the proposed blend CTE and keyset page query. Walking the list in pages of 3 gave exactly the same sequence as the unpaged query: ties were broken by date and then id, and paging crossed from the scored segment into the unscored one with no duplicates and no gaps. It kept working when the cursor article was marked read between pages, both for a scored cursor and an unscored one. The R4 tuple `(COALESCE(score,'-Infinity'), COALESCE(published_at, fetched_at), id)` works as-is, but the score must be `float8`, not `numeric`, for `'-Infinity'`. `numeric` infinity needs a newer server than the unknown production version is guaranteed to be.

The prototype found two bugs that the planner has to guard against. **(1)** `LEAST(100, GREATEST(0, ROUND(raw)))` returns **0, not NULL**, for unscored articles, because PostgreSQL's `GREATEST`/`LEAST` ignore NULL arguments. Without a `CASE WHEN raw IS NULL THEN NULL` guard, every unscored article would get a "0" badge, which PRIO-03 and the "never render 0" rule forbid. **(2)** `round()` on `double precision` rounds half to even (`round(2.5::float8) = 2`), while `round()` on `numeric` rounds half away from zero (`round(82.5::numeric) = 83`). The badge (`display`) and the breakdown total must both come from the **numeric** `raw` computed in SQL, and Java must never re-round the total.

On the frontend, TanStack Query v5 refetches **every loaded page** on any plain refetch: invalidation, window focus, or a stale remount. The Priority endpoint returns unread articles only, so any such refetch would drop read rows and re-rank the list, which is exactly what D-07 and D-09 forbid. The Priority infinite query therefore needs its own key outside `['articles']` (`['priority']`), `staleTime: Infinity`, and focus/reconnect refetch turned off. Read and star changes are patched in place with `setQueriesData`. "Refresh ranking" uses `resetQueries`, which empties the pages and so fetches **only page 1**. Leaving the route drops the cache with `removeQueries`. `MainLayout` must feed `useKeyboardShortcuts` from the same deduped Priority rows when on `/priority`. `SpaForwardController` must also forward `/priority`; without that, a browser reload on `/priority` returns 404.

**Primary recommendation:** Build one `InterestScoreQueries` repository (a `JdbcClient` text-block CTE: `learned` → `eff` → `contrib` → `blended`) and use it for all three reads: the Priority keyset page (with the cursor resolved inside the same SQL statement), the id-scoped `interestScore` enrichment, and the per-article breakdown. Split the breakdown rows into integers server-side with a pure static largest-remainder helper. On the frontend, a single `usePriorityArticles()` hook (key `['priority']`, never invalidated) is shared by `PriorityList` and `MainLayout`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Blend formula (p, hinge, Σ m×w, ROUND 6, display clamp) | Database (SQL CTE) | — | Single source of truth for sort, badge and breakdown (CTX carry). The DB owns the stored raw outputs |
| Priority ordering + keyset cursor | Database (SQL) | API (`PriorityService` cursor existence → 404) | The sort key is computed, so only SQL can compare it. The service keeps `PaginatedResponse` unchanged |
| `interestScore` enrichment on every article response | API / Backend (service layer after the repository fetch) | Database (id-scoped CTE) | Spring Data JDBC `@Query` methods can't populate `@Transient` fields, so a second id-scoped query sets them |
| Breakdown integer apportionment + level index | API / Backend (pure static Java) | — | Unit-testable, and it apportions exactly to the SQL total. The client only renders |
| Tier coloring, separator, banner precedence, chips | Browser / Client | — | Presentation only, from server values (`interestScore`, breakdown, `/status`) |
| Frozen triage order, dedupe, hint, refresh | Browser / Client (TanStack cache) | — | The list's stability is a cache-policy concern (R4), not a server one |
| Status states (PRIO-08) | API (`/api/interest/status`, exists) | Browser (banner) | Reuse the Phase 4 contract unchanged |
| `/priority` deep link | Frontend Server (Spring `SpaForwardController`) | — | A reload on `/priority` must forward to `index.html` |

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (locked by research and prior phases, not re-discussed)
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

#### "Why N?" breakdown
- **D-01:** The breakdown lives **only in the reading pane**, as a collapsible "Why N?" row under the article title. `i` toggles it, and the open/closed state persists across articles for the session. There's no hover tooltip or popover in the list.
- **D-02:** **Integer rows that add up exactly.** Row contributions are rounded with largest-remainder so they sum exactly to `round(raw)`. When `raw` falls outside 0–100, a final line says so explicitly, e.g. "Total 112 → capped at 100". The last line always equals the badge. Rows are ordered by absolute contribution. Each topic row shows match % (hinge) × weight = points, and the profile row shows its points.
- **D-03:** **Non-matching topics are hidden** (hinge = 0 contributes nothing). A collapsed footer, "Show N non-matching topics", reveals them with their match %.
- **D-04:** The profile row carries a **level label** (e.g. "Strongly"). `article_score` stores only the continuous `profile_score`, not per-level probabilities, so derive the label from the nearest level index (`round(profile_score)` → one of 5 short labels mapped from `InterestQuestions.PROFILE_LEVELS`). No migration.
- **D-05:** **Not included:** a low-confidence hint, "scored before topic X existed" / stale-profile notes, and clickable chips. Keep the breakdown to math rows + the level label.
- **D-06:** **Matched-topic chips are always visible** in the reading pane next to the badge, even when the breakdown is collapsed: `[82] Rust · WebAssembly · Politics− · Why 82? ▸`. Chips are colored by the sign of their contribution, reusing the Phase 3 `.weight-positive` / `.weight-negative` colors (`--toast-success-text` / `--toast-error-text`), and are not clickable. Chips aren't shown in list rows.

#### Triage stability & refresh
- **D-07:** **Read rows stay in place, dimmed** with the existing `.read` style, until the user refreshes or re-enters the view. This covers every row, not only the selected one. Today's `ArticleList` `preserved` logic protects only the selected article, so Priority needs a broader mechanism: the Priority query is excluded from `['articles']` invalidation, and read/star state is updated in place with `setQueryData`. `j`/`k` walk every row, including read ones.
- **D-08:** **Re-ranking is explicit.** A ↻ refresh button is always in the Priority toolbar. After a profile/topic save in the Interests dialog, or when `/status` shows more articles scored, the button gets a subtle "Ranking changed — refresh" hint. Nothing re-sorts until the user clicks it or re-enters the view (re-entry refetches from page 1).
- **D-09:** **No re-rank on window refocus.** Turn off `refetchOnWindowFocus` for the Priority query. At most, refocusing lights the hint.
- **D-10:** **Paging** uses the same "Load more" button as the other lists (no auto-load on scroll). Criterion 1's "infinite scroll" wording is satisfied by cursor paging that crosses the scored/unscored boundary with no duplicates or gaps.
- **D-11:** **`j` on the last loaded row** in Priority fetches the next page, then selects that page's first row. This is Priority only; other lists keep today's behavior, where `j` stops at the end.

#### Priority entry & states
- **D-12:** **Feed-tree placement:** Priority is the first smart view, above All Articles, and shows **no count**. Its icon must not be a star glyph (Starred already uses one). It gets `useMatch('/priority')` active state and is excluded from All's active state.
- **D-13:** **The entry is always shown**, including when Jev isn't configured. The view then explains why (Raindrop precedent: "not configured by the administrator").
- **D-14:** **Status states** show as **one slim themed banner** under the toolbar, and the list always renders below it: scored articles, then the "Not yet scored" separator, then unscored articles by date. Precedence is not configured > cold start > scoring paused (breaker OPEN) > "N articles waiting to be scored" (`eligibleUnscored > 0`), and only one banner shows at a time. Cold start adds a "Set up interests" button that opens `InterestsDialog`.
- **D-15:** **Unscored segment:** one "Not yet scored" segment, by date. Unread articles older than the 14-day window, which will never be scored, stay in it with no extra separator. They aren't excluded either, so PRIO-02 holds as written.
- **D-16:** **The "Mark all read" toolbar button is hidden** in Priority, consistent with `Shift+A` being disabled (PRIO-07). The Priority toolbar is the title plus refresh.
- **D-17:** **No failed count** in the banner. Permanently failed articles just stay unscored, and Re-score in the Interests dialog is the recovery path.

#### Badge reach & look
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

### Deferred Ideas (OUT OF SCOPE)
- The low-confidence hint and "scored before topic X existed / older profile" notes in the breakdown were offered and not selected. Revisit if scores prove confusing.
- Clickable chips that open the Interests dialog on that topic: not selected. Maybe a later polish item.
- A separate "Too old to score" segment for unread articles outside the 14-day window: not selected.
- A failed-count mention in the Priority banner: not selected.
- Already in v2 requirements: the ≥70 count on the Priority item (PRIO-V2-02), sort-by-interest on all lists (PRIO-V2-03), and "mark all below as read" (PRIO-V2-01).

**Also locked:** `05-UI-SPEC.md` (status `approved`) is the visual/interaction contract: component names, copy, banner precedence, badge tiers/sizes, level labels `None / In passing / Partly / Mainly / Core interest`, refresh/hint behavior, keyboard contract (`g p`, `i`, `r` on `/priority` = refresh, `Shift+A` no-op on `/priority`, Priority-only `j` paging).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PRIO-01 | Priority view from the feed tree (route `/priority`), unread by score, ties by date | Keyset page SQL (Pattern 2, verified); FeedPanel entry + `useMatch`; App route; **SpaForwardController `/priority`** (Pitfall 6) |
| PRIO-02 | Unscored after scored, by date, below "Not yet scored" | `COALESCE(raw::float8,'-Infinity')` puts unscored last, by date (verified); the client inserts the separator before the first `interestScore === null` row |
| PRIO-03 | 0–100 tier badge in lists + reading pane; unscored → none | `display` from numeric raw with the **NULL-guarded CASE** (Pitfall 1); id-scoped enrichment (Pattern 3); `@Transient Integer interestScore` |
| PRIO-04 | Exact "Why N?" breakdown + chips | `contrib` CTE rows + SQL `total`; server-side largest remainder (Pattern 4); `interestBreakdown` on `GET /api/articles/{id}` |
| PRIO-05 | Order stable while triaging | `['priority']` key outside `['articles']`; `staleTime: Infinity`; `setQueriesData` patch; refresh = `resetQueries` (Pattern 5, verified TanStack source) |
| PRIO-06 | Cursor pagination across the boundary | `PaginatedResponse.of` unchanged; cursor resolved inside SQL from the same `keyed` CTE, not unread-scoped; missing cursor → 404 → client reset (verified walk: no dup/gap) |
| PRIO-07 | `g p`, `j`/`k` ranked, `i`, `Shift+A` disabled | `useKeyboardShortcuts` changes (Pattern 6); MainLayout feeds the deduped Priority rows |
| PRIO-08 | not configured / cold start / paused / N waiting | `useInterestStatus()` unchanged; precedence in `PriorityBanner`; move `OPEN_BREAKER_STATES` + `articles()` to `utils/interest.ts` |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Spring Data JDBC, **not JPA**: entity annotations come from `org.springframework.data.annotation` / `org.springframework.data.relational.core.mapping`. No derived queries; use `@Query` or `JdbcClient` for custom SQL.
- Jackson 3: databind lives in `tools.jackson.databind.*`, and annotations (`@JsonInclude`, `@JsonIgnore`, …) stay in `com.fasterxml.jackson.annotation.*`.
- `GlobalExceptionHandler` mappings: throw `NotFoundException` → 404 and `IllegalArgumentException` → 400. Controllers don't catch.
- Paginated endpoints return `PaginatedResponse` (`{items, nextCursor}`). Controllers fetch `limit + 1` and delegate to `PaginatedResponse.of(...)`.
- Article sort uses `COALESCE(published_at, fetched_at)`, never `id` alone.
- The ReadingPane fetches the selected article by id (`useArticle(id)` → `GET /api/articles/{id}`).
- **V6 is the complete interest schema; "later milestone phases add no migrations"** (root CLAUDE.md §Interest Ranking). Any index would need a V7, which contradicts this, so none is recommended (see Pattern 2 performance note).
- `InterestService.isColdStart()` is the single cold-start predicate. Callers never reimplement it.
- `InterestStatus` fields are appended, never renamed.
- `src/test/resources/application.yaml` shadows main. `DevProfileConfigTest` requires that **every key in main `application.yaml` resolves to main's value** from the test yaml plus the dev overlay. Any `myfeeder.interest.blend.*` key added to main yaml must be mirrored in the test yaml.
- Test patterns: Mockito `@ExtendWith(MockitoExtension.class)` for services; `@WebMvcTest` + `@MockitoBean` for controllers; `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` for repositories; Vitest + RTL for the frontend.
- Frontend type-check with `npx tsc -b` (not `tsc --noEmit`). Tests live under `src` and are type-checked.
- `vi.mock` of stores/hooks is **full replacement**: a new export consumed by a mocked module breaks every test that mocks it. Prefer `importOriginal` spreads in new tests.
- Zustand persist gotcha: new persisted fields don't reach existing users. `uiStore`'s `partialize` whitelists `panelWidths`/`expandedFolders`, so new uiStore fields are **not** persisted (good for the session-only `whyOpen`).
- Theme colors come only from `src/themes.ts` CSS variables. No new variables (D-20).
- Git: work on a feature branch. Gradle, never Maven. Don't touch the scoring write path's rubric-change check.

## Standard Stack

No new dependencies. Everything is already on the classpath or in `package.json`.

### Core (existing, verified in repo)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `JdbcClient` (spring-jdbc, Boot 4.0.8 BOM) | BOM-managed | Blend CTE queries | Already used by `ArticleScoreStore` (`private final JdbcClient jdbc;` [VERIFIED: ArticleScoreStore.java:57]) |
| `BeanPropertyRowMapper` | spring-jdbc | Map the Priority page rows to `Article`, including `interest_score` → `setInterestScore` | Maps underscore columns to camelCase setters [CITED: docs.spring.io BeanPropertyRowMapper javadoc] |
| PostgreSQL | tests: `postgres:latest` (18.6 when probed); prod: unknown | Row-value keyset, `float8 '-Infinity'`, numeric `ROUND` | `PostgreSQLContainer(DockerImageName.parse("postgres:latest"))` [VERIFIED: TestcontainersConfiguration.java:16] |
| @tanstack/react-query | 5.103.2 (installed) | Priority infinite query, `setQueriesData`, `resetQueries`, `removeQueries` | Existing cache layer [VERIFIED: npm ls] |
| react-router-dom | 6.30.6 (installed) | `/priority` route, `useMatch` | Existing router [VERIFIED: npm ls] |
| zustand | ^5.0.15 | Session-only `whyOpen` + `rankingChanged` state | Existing store library |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `float8` sort score with `'-Infinity'` | `numeric` `'-Infinity'` | Needs a newer PostgreSQL than prod is known to run (numeric infinities are documented today; the version that added them is `[ASSUMED]` 14). `float8` infinity works on all versions |
| `float8` + `-Infinity` sentinel | Extra `is_scored` column first in the tuple | Cleaner without a sentinel, but R4 locked the `-Infinity` tuple. Keep R4 |
| Breakdown embedded on `GET /api/articles/{id}` | Separate `GET /api/articles/{id}/score-breakdown` | A separate endpoint needs its own hook plus loading/error UI states (UI-SPEC lists them "only if fetched separately"). Embedding is one request, and Phase 6's re-enriched feedback response updates the chips too |
| Largest remainder in Java (server) | In TypeScript (client) | UI-SPEC allows either. The server keeps one rounding authority next to the SQL `total`, and pure static Java is trivially unit-tested (matches the `InterestQuestions`/`ArticleStateBuilder` pure-builder pattern) |
| `removeQueries` on leave | Epoch-in-key (`['priority', epoch]`) | Both work. Epoch avoids reasoning about removed-query observers, but needs a shared counter. `removeQueries` is less code |

**Installation:** none.

## Package Legitimacy Audit

No external packages are installed by this phase (UI-SPEC: "No new npm dependencies"; backend uses only existing Spring JDBC).

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 Feed tree "Priority" / g p ──► navigate('/priority') ──► <Route /priority> PriorityList
                                                         │
 MainLayout (always mounted) ── useMatch('/priority') ───┤ both call usePriorityArticles()
   └─ useKeyboardShortcuts(rows = deduped priority rows) │ key ['priority'], staleTime ∞,
                                                         │ no focus/reconnect refetch
                                                         ▼
                              GET /api/articles/priority?limit=50[&before=<id>]
                                                         │
                          ArticleController ─► PriorityService
                               before != null && !existsById ─► NotFoundException (404)
                                                         │                 │
                                                         ▼                 └─► client: resetQueries(['priority'])
                          InterestScoreQueries.priorityPage(cursor, limit+1)
             ┌─────────── one SQL statement ───────────────────────────────────────┐
             │ learned(0) → eff(w = base+learned) → contrib(hinge×w per topic row)  │
             │ → blended(raw_n = ROUND(100p + Σpoints, 6), unread ∪ {cursor})      │
             │ → keyed(sort_score = COALESCE(raw_n::float8,'-Inf'), sort_date, id,  │
             │          interest_score = CASE raw_n NULL → NULL ELSE clamp(round)) │
             │ WHERE unread AND (tuple) < (cursor's tuple from keyed)               │
             │ ORDER BY sort_score DESC, sort_date DESC, id DESC LIMIT n+1          │
             └──────────────────────────────────────────────────────────────────────┘
                                                         │
                          PaginatedResponse.of(rows, limit, Article::getId)  ─► {items, nextCursor}
                                                         │
 PriorityList: dedupe by id → banner(useInterestStatus) → filter → rows (+ separator before
               first interestScore === null) → Load more / j-on-last-row → fetchNextPage

 Read/star (m, s, toolbar, auto-mark-read) ─► PATCH /api/articles/{id}
      onSuccess: invalidate ['articles'], ['article', id], ['unreadCounts']   (existing)
                 + setQueriesData(['priority'], patch read/starred in place) (new; never invalidate)

 Refresh (button, r on /priority) ─► resetQueries(['priority']) ─► page 1 only; clear hint
 Leave /priority ─► removeQueries(['priority'])  ⇒ re-entry fetches page 1
 Interests save / topic CUD / rescore success ─► rankingChanged = true (hint)
 /status eligibleUnscored < baseline(at page-1 load) ─► rankingChanged = true

 Every other article response (list, board, by-id, PATCH):
   Spring Data JDBC fetch ─► InterestScoreQueries.displayScores(ids) (id-scoped CTE)
                          ─► article.setInterestScore(...)
   GET /api/articles/{id} additionally ─► InterestScoreQueries.breakdown(id)
                          ─► ScoreBreakdowns.apportion(...) ─► article.interestBreakdown
```

### Recommended Project Structure
```
src/main/java/org/bartram/myfeeder/
├── repository/InterestScoreQueries.java   # the blend CTE text block + priorityPage / displayScores / breakdown
├── service/PriorityService.java            # cursor existence check, delegates to InterestScoreQueries
├── service/ScoreBreakdowns.java            # pure static: largest remainder, level index, row ordering
├── model/Article.java                      # + @Transient Integer interestScore, @Transient InterestBreakdown interestBreakdown
├── model/InterestBreakdown.java            # record(s) for the by-id breakdown payload
├── config/MyfeederProperties.java          # + Interest.Blend { int profilePoints = 100 }
└── config/SpaForwardController.java        # + "/priority"
src/main/frontend/src/
├── hooks/usePriorityArticles.ts (or in useArticles.ts)  # ['priority'] infinite query + dedupe + patch helper
├── components/PriorityList.tsx, PriorityBanner.tsx, InterestBadge.tsx, ScoreRow.tsx, WhyBreakdown.tsx
├── stores/priorityStore.ts                 # non-persisted: whyOpen, rankingChanged (keeps uiStore mocks untouched)
└── utils/interest.ts                       # + OPEN_BREAKER_STATES, articles(), tierOf(score)
```

### Pattern 1: The single blend CTE (text-block constant, scope-parameterized)

**What:** One `static final String` composed from fixed fragments (the `ArticleScoreStore` style, `static final String ELIGIBLE = "a.\"read\" = false AND COALESCE(a.published_at, a.fetched_at) > :cutoff";` [VERIFIED: ArticleScoreStore.java:37-38]). The scope predicate is always one of a few **constants**, never user input.

**Schema facts it depends on** [VERIFIED: V6__interest_scoring.sql, read this session]:
- line 16: `weight INTEGER NOT NULL DEFAULT 20 CHECK (weight BETWEEN -50 AND 50), -- points (R1, D-10)`
- line 25: `status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED')),`
- line 26: `profile_score DOUBLE PRECISION, -- raw Score value in [0, profile_max_level]; NULL when no profile question`
- line 27: `profile_max_level INTEGER, -- levels - 1 at scoring time`
- lines 39-41: `article_id BIGINT NOT NULL REFERENCES article_score(article_id) ON DELETE CASCADE,` / `topic_id BIGINT NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,` / `noul DOUBLE PRECISION NOT NULL CHECK (noul BETWEEN 0 AND 1),`
- line 43: `PRIMARY KEY (article_id, topic_id)`

**Example (prototyped and run on PostgreSQL 18.6):**
```sql
-- Source: prototype run this session against V1/V2/V3/V5/V6 on postgres:latest (18.6)
WITH learned AS (
    -- Phase 6 replaces this body (must honor topics_narrowed, 03 D-02). Until then: no delta.
    SELECT t.id AS topic_id, 0::double precision AS delta FROM interest_topic t
),
eff AS (
    SELECT t.id, t.weight + COALESCE(l.delta, 0) AS w
    FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id
),
contrib AS (   -- one row per stored (article, topic) judgment; deleted topics cascade away
    SELECT ts.article_id, ts.topic_id, ts.noul,
           GREATEST(0, (ts.noul - 0.5) * 2)        AS hinge,
           e.w,
           GREATEST(0, (ts.noul - 0.5) * 2) * e.w  AS points
    FROM article_topic_score ts JOIN eff e ON e.id = ts.topic_id
),
blended AS (
    SELECT s.article_id,
           ROUND((:profilePoints * COALESCE(s.profile_score / NULLIF(s.profile_max_level, 0), 0)
                  + COALESCE(SUM(c.points), 0))::numeric, 6) AS raw_n
    FROM article_score s
    JOIN article a ON a.id = s.article_id AND ( %SCOPE% )        -- constant fragment
    LEFT JOIN contrib c ON c.article_id = s.article_id
    WHERE s.status = 'SCORED'
    GROUP BY s.article_id, s.profile_score, s.profile_max_level
)
```
Scopes (constants):
- Priority: `a."read" = false OR a.id = :cursorId`. With no cursor use the first-page statement variant; see Pitfall 5.
- Enrichment: `a.id IN (:ids)` (id-scoped, **not** unread-scoped, D-18). Guard: an empty `ids` list returns an empty map without running the query (the same guard `ArticleScoreStore.filterNeedingScoring` uses [VERIFIED: ArticleScoreStore.java:186-189]).
- Breakdown: `a.id = :articleId`.

Derived columns (always from numeric `raw_n`):
```sql
CASE WHEN b.raw_n IS NULL THEN NULL ELSE ROUND(b.raw_n)::int END                      AS total          -- breakdown target
CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END AS interest_score -- badge
COALESCE(b.raw_n::float8, '-Infinity'::float8)                                          AS sort_score
```

**Level index (D-04):** `PROFILE_LEVELS` has 5 entries and `PROFILE_MAX_LEVEL = PROFILE_LEVELS.size() - 1` [VERIFIED: InterestQuestions.java:28-35; `static final List<String> PROFILE_LEVELS` is **package-private**, `public static final int PROFILE_MAX_LEVEL` is public]. Stored rows use the response's legend max, falling back to the rubric's (`score.maxLevel() >= 0 ? score.maxLevel() : InterestQuestions.PROFILE_MAX_LEVEL` [VERIFIED: ArticleScoringService.java:159-161]). So compute the index as `round(profile_score / profile_max_level × PROFILE_MAX_LEVEL)`, not a bare `round(profile_score)`. The two agree whenever max = 4, and the scaled form stays in range if a legend ever differs. The server returns the index. The frontend maps it to the UI-SPEC labels.

### Pattern 2: Priority keyset page with the cursor resolved in the same statement

```sql
-- Source: prototype (verified: paged walk == unpaged order; boundary crossing; cursor read between pages)
<CTE above, scope = a."read" = false OR a.id = :cursorId>,
keyed AS (
    SELECT a.id, a.feed_id, a.guid, a.title, a.url, a.author, a.content, a.summary, a.image_url,
           a.published_at, a.fetched_at, a."read", a.starred,
           COALESCE(b.raw_n::float8, '-Infinity'::float8) AS sort_score,
           COALESCE(a.published_at, a.fetched_at)          AS sort_date,
           CASE WHEN b.raw_n IS NULL THEN NULL
                ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END AS interest_score
    FROM article a LEFT JOIN blended b ON b.article_id = a.id
    WHERE a."read" = false OR a.id = :cursorId
)
SELECT k.* FROM keyed k
WHERE k."read" = false
  AND (k.sort_score, k.sort_date, k.id)
      < (SELECT c.sort_score, c.sort_date, c.id FROM keyed c WHERE c.id = :cursorId)
ORDER BY k.sort_score DESC, k.sort_date DESC, k.id DESC
LIMIT :limit
```
- **First page** is the same statement without the cursor predicate. Scope `a."read" = false` only. Use two statements (like `findFiltered`/`findFilteredBefore` in `ArticleRepository`) rather than a nullable `:cursorId` (Pitfall 5).
- **The cursor is not unread-scoped** (R4). Verified: marking cursor 1 (scored) or cursor 19 (unscored) read before the next page still returns the correct continuation.
- **A missing cursor article** gives an empty result that looks the same as "end of list" (verified: 0 rows). So `PriorityService` checks `articleRepository.existsById(cursor)` first and throws `NotFoundException` (404, [VERIFIED: GlobalExceptionHandler.java:43-45]). Don't copy `findFiltered`'s `IllegalArgumentException` (400, [VERIFIED: ArticleService.java:57-58]); a 404 lets the client distinguish "restart from page 1".
- Explicit column list: `extracted_content` (V5) is never shipped in the list, and every column maps onto an `Article` setter.
- **Controller:** `@GetMapping("/priority")` in `ArticleController`. A literal segment outranks `/{id}` in Spring's pattern matching `[ASSUMED]`; a `@WebMvcTest` asserting `GET /api/articles/priority` hits the Priority handler proves it. Clamp `limit` with the existing `MAX_LIMIT = 100` [VERIFIED: ArticleController.java:23] and return `PaginatedResponse.of(fetched, safeLimit, Article::getId)` [VERIFIED: PaginatedResponse.java:12-17]. Adding `PriorityService` to `ArticleController`'s constructor requires `@MockitoBean PriorityService` in `ArticleControllerTest` (today: `@MockitoBean private ArticleService articleService;` etc. [VERIFIED: ArticleControllerTest.java:30-32]).

**Performance / index (discretion item):** at 100k articles / 20k unread / 26.6k scored / 213k topic rows, `EXPLAIN ANALYZE` of the page query took about 145 ms (first page and cursor page alike) on the dev laptop. The plan uses the existing `idx_article_read` partial index (`CREATE INDEX idx_article_read ON article(read) WHERE read = FALSE;` [VERIFIED: V1__initial_schema.sql:36]), `article_topic_score_pkey`, and `interest_topic_pkey`. The cost is the per-topic aggregation over scored unread rows, which no index removes. A 50-id enrichment query took 0.5 ms. **Recommendation: no new index and no V7 migration** (it would also contradict CLAUDE.md's "later phases add no migrations"). Realistic scored-unread counts are bounded by the 14-day window, far below this synthetic load `[ASSUMED]`.

### Pattern 3: `interestScore` enrichment (D-18)

```java
// model/Article.java — Spring Data's @Transient, NOT java.beans.Transient (Pitfall 3)
@org.springframework.data.annotation.Transient
private Integer interestScore;                         // null = unscored

@org.springframework.data.annotation.Transient
@com.fasterxml.jackson.annotation.JsonInclude(JsonInclude.Include.NON_NULL)
private InterestBreakdown interestBreakdown;           // only on GET /api/articles/{id}
```
- Enrich where the lists are produced: `ArticleService.findFiltered` / `findById` / `updateState`, and `BoardService.findArticles`. Board SQL is `SELECT a.* … ORDER BY a.id DESC` [VERIFIED: BoardArticleRepository.java:13]. `InterestScoreQueries.displayScores(ids) → Map<Long,Integer>`, then `a.setInterestScore(map.get(a.getId()))`.
- Existing unit tests use `@InjectMocks` with only the current repos (`@Mock private ArticleRepository articleRepository;` [VERIFIED: ArticleServiceTest.java:23-24]; `BoardServiceTest.java:18-20`). Add `@Mock InterestScoreQueries` to both, or the new constructor argument arrives as `null` `[ASSUMED Mockito behavior]` and enrichment NPEs.
- `RaindropService` also calls `articleService.findById`. Enrichment is harmless there. Keep the breakdown to a dedicated method (`findByIdWithBreakdown`) used only by `getArticle`.

### Pattern 4: Exact breakdown (D-02) — largest remainder against the SQL total

```java
// service/ScoreBreakdowns.java — pure static, no Spring (like InterestQuestions / ArticleStateBuilder)
/** Integer points per row that sum exactly to target (the SQL ROUND(raw_n)). Floor-based, sign-agnostic. */
static long[] apportion(BigDecimal[] exact, long target) {
    int n = exact.length;
    long[] out = new long[n];
    BigDecimal[] rem = new BigDecimal[n];
    long floorSum = 0;
    for (int i = 0; i < n; i++) {
        BigDecimal f = exact[i].setScale(0, RoundingMode.FLOOR);
        out[i] = f.longValueExact();
        rem[i] = exact[i].subtract(f);          // in [0, 1)
        floorSum += out[i];
    }
    long k = Math.max(0, Math.min(n, target - floorSum)); // mathematically round(Σrem) ∈ [0, n]
    Integer[] order = /* indices sorted by rem DESC, then by display order (|exact| DESC, name A–Z) */;
    for (int j = 0; j < k; j++) out[order[j]]++;
    return out;
}
```
- Rows: the profile row (only when `profile_score IS NOT NULL`, shown even at 0) plus matched topics (`hinge > 0`). Non-matching topics (`hinge = 0`, exact 0) are left out of the apportionment and returned separately for the "Show N non-matching topics" footer. Topics added after scoring have no `article_topic_score` row, so they are not listed (R5).
- SQL returns each row's exact points as `ROUND(points::numeric, 6)` → `BigDecimal`, so no binary-float floor surprises. The **target is the SQL `total`**, never a Java rounding.
- Worked check against the UI mock (seeded as article 1): profile 2.56/4×100 = 64, Rust 0.86×20 = 17.2, WebAssembly 0.5×14 = 7, Politics 0.2×(−30) = −6 → raw 82.2 → total 82; floors 64+17+7−6 = 82 → no bumps, rows `+64 +17 +7 −6`, Score 82 (exactly the approved mock). Capped case (seeded article 3): raw 133.32 → total 133 → rows 100/19/14 (19.6 and 13.72 floor to 19/13, and the one bump goes to the larger remainder 0.72) → "Total 133 → capped at 100".
- Payload suggestion: `{ raw, total, display, profile: {exact, points, levelIndex} | null, topics: [{topicId, name, noul, hinge, weight, exact, points}] (display order), nonMatching: [{topicId, name, noul}] }`. The client renders only; `matchPct = round(noul×100)` and `countsPct = round(hinge×100)` are presentation rounding, which UI-SPEC allows.

### Pattern 5: Frozen-order Priority query (TanStack v5)

**Verified behavior** [VERIFIED: tanstack/query `infiniteQueryBehavior.ts` via Context7]: `fetchNextPage` fetches one page (`direction` set). A plain refetch (invalidate, focus, stale remount) runs `// Fetch all pages … while (currentPage < remainingPages)` with `remainingPages = pages ?? oldPages.length`. With no old pages (after reset) the do-while runs once, so only the first page. `resetQueries` "resets matching queries to their initial state … Active queries among the matched set are then refetched." `setQueriesData` "only queries that already exist and match … are updated; no new cache entries are created." [CITED: tanstack QueryClient reference]

```ts
// hooks/usePriorityArticles.ts
export const PRIORITY_KEY = ['priority'] as const          // outside the ['articles'] prefix

export function usePriorityArticles(enabled = true) {
  const query = useInfiniteQuery({
    queryKey: PRIORITY_KEY,
    queryFn: ({ pageParam }) => articlesApi.priority(50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last.nextCursor !== null ? last.nextCursor : undefined),
    enabled,
    staleTime: Infinity,          // never "stale" ⇒ no remount refetch of all pages
    refetchOnWindowFocus: false,  // D-09
    refetchOnReconnect: false,
  })
  const rows = useMemo(() => dedupeById(query.data?.pages), [query.data]) // first occurrence wins
  return { ...query, rows }
}

/** In-place patch; used by useUpdateArticleState.onSuccess (and Phase 6 thumbs). Never invalidate. */
export function patchPriorityArticle(qc: QueryClient, id: number, patch: Partial<Article>) {
  qc.setQueriesData<InfiniteData<PaginatedArticles>>({ queryKey: PRIORITY_KEY }, (old) =>
    old && { ...old, pages: old.pages.map((p) => ({ ...p,
      items: p.items.map((a) => (a.id === id ? { ...a, ...patch } : a)) })) })
}
```
- Existing invalidations are prefix `['articles']` [VERIFIED: useArticles.ts:53-55 `qc.invalidateQueries({ queryKey: ['articles'] })` …, :66-67]. They never match `['priority']`, and `['article', id]` doesn't match `['articles']` either. In `useUpdateArticleState.onSuccess`, add `patchPriorityArticle(qc, variables.id, { read, starred } from the response/variables)`. Don't copy `interestScore` from the PATCH response, so a row's badge never flickers.
- Global default `queries: { staleTime: 30_000, retry: 1 }` [VERIFIED: queryClient.ts:12]. The Priority hook must override `staleTime`, or a return after 30 s would refetch every page and drop read rows.
- **Refresh** (button, `r` on `/priority`): `await qc.resetQueries({ queryKey: PRIORITY_KEY })`, clear `rankingChanged`, scroll `.article-items` to top. "↻ Refreshing…" = `isFetching && !isFetchingNextPage`.
- **Re-entry:** in `MainLayout`, `useEffect(() => { if (!isPriority) return; return () => { qc.removeQueries({ queryKey: PRIORITY_KEY }) } }, [isPriority, qc])`. Leaving drops the cache without a fetch, so re-entry starts empty and fetches page 1 with no stale flash. (`React.StrictMode` is on [VERIFIED: main.tsx:6], so dev may double-fetch page 1 on first mount. That is dev-only and self-healing `[ASSUMED observer rebuild on setOptions]`.)
- **Cursor gone:** `fetchNextPage()` rejects with `ApiError` whose `status === 404` (`readonly status: number` [VERIFIED: api/client.ts:9-18]), then `resetQueries(PRIORITY_KEY)`.
- **Hint:** `rankingChanged` in a small non-persisted store. Set it from `onSuccess` of `useSaveInterestProfile`, `useCreateInterestTopic`, `useUpdateInterestTopic`, `useDeleteInterestTopic` (and `useRescoreUnread`), and when `status.eligibleUnscored < baseline`, where `baseline` is captured when page 1 resolves. `useInterestStatus` already polls only when `s && s.configured && !s.coldStart && s.eligibleUnscored > 0 ? 15_000 : false` [VERIFIED: useInterest.ts:21-24]. Update its doc comment, which says it is "observed only while the dialog is open".

### Pattern 6: Route, feed tree and keyboard wiring

- `App.tsx` routes today: `/feed/:feedId`, `/folder/:folderId`, `/starred`, `/boards`, `/board/:boardId`, `*` [VERIFIED: App.tsx:97-104]. Add `<Route path="/priority" element={<PriorityList onSetUpInterests={() => setInterestsOpen(true)} />} />` (`const [interestsOpen, setInterestsOpen] = useState(false)` [VERIFIED: App.tsx:76]).
- `MainLayout` keyboard source today: `const { data } = useArticles(selectedFeedId ? { feedId: selectedFeedId, sort, ...readFilter } : { sort, ...readFilter })` [VERIFIED: App.tsx:84]. Add `const isPriority = useMatch('/priority') !== null; const priority = usePriorityArticles(isPriority)` and pass `isPriority ? priority.rows : articles` to `useKeyboardShortcuts`, plus callbacks `{ isPriority, onPriorityNextPage, onPriorityRefresh }`.
- `SpaForwardController`: `@GetMapping(value = {"/", "/feed/**", "/folder/**", "/starred", "/boards", "/board/**", "/settings"})` [VERIFIED: SpaForwardController.java:9]. **Add `"/priority"`.**
- FeedPanel All-active today: ``className={`smart-view ${!selectedFeedId && !selectedFolderId ? 'active' : ''}`}`` [VERIFIED: FeedPanel.tsx:230]. Add `&& !priorityMatch`, and insert the Priority entry first inside `.smart-views` (line 229).
- `useKeyboardShortcuts` today: g-chord `case 'a': navigate('/'); return` / `case 's': navigate('/starred'); return` / `case 'b': navigate('/boards'); return` [VERIFIED: useKeyboardShortcuts.ts:63-65]. Add `case 'p'` that clears feed/folder and navigates to `/priority`. The chord branch runs before the plain `p` (previous unread feed) case, so there's no conflict. `case 'A': if (e.shiftKey && selectedFeedId) { markAllReadInFeed(selectedFeedId) }` [VERIFIED: :152-156] needs an explicit `!isPriority` guard. `case 'r': if (selectedFeedId) pollFeed.mutate(selectedFeedId)` [VERIFIED: :149-151] should branch to the Priority refresh on `/priority`. `case 'j'` [VERIFIED: :86-92] needs a Priority branch: at the last index with `hasNextPage` and not already fetching, `await fetchNextPage()` and then select the first id not previously present. Add `case 'i'`, which toggles `whyOpen` when `currentArticle?.interestScore != null`.
- Reading pane: auto-mark-read `updateState.mutate({ id: article.id, state: { read: true } })` [VERIFIED: ReadingPane.tsx:63-71] goes through `useUpdateArticleState`, so the in-place patch covers it (D-07 discretion item). Insert `ScoreRow`/`WhyBreakdown` between `<h1 className="article-title">` [VERIFIED: ReadingPane.tsx:184] and `.article-meta`.
- Shared UI helpers to move: `const OPEN_BREAKER_STATES = ['OPEN', 'FORCED_OPEN']` [VERIFIED: InterestsDialog.tsx:28] and `function articles(count: number): string { return count === 1 ? '1 article' : \`${count} articles\` }` [VERIFIED: InterestsDialog.tsx:176-178] move to `utils/interest.ts`.

### Anti-Patterns to Avoid
- **Invalidating or refetching the Priority query on mark-read/star:** it re-fetches every loaded page, drops read rows and re-ranks (verified TanStack behavior). Patch in place only.
- **Priority as a virtual `selectedFeedId`** (e.g. −1): it leaks into `markAllReadInFeed`, `n`/`p` and counts (research Anti-Pattern 7). Use the route plus its own query.
- **Recomputing the score or the hinge on the client for the badge/breakdown:** violates the single source of truth. `utils/interest.ts hinge()` exists for the Phase 3 preview only.
- **Rounding the total in Java** (`Math.round`): half-up toward +∞ differs from numeric `ROUND` for negative .5 ties. Take `total` from SQL.
- **`SELECT a.*` into the Priority rows:** it ships `extracted_content` and couples the mapper to every future column.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Page-1-only refetch of an infinite list | Manually slicing `data.pages` / custom refetch loops | `queryClient.resetQueries({ queryKey })` | Verified to fetch only the first page after reset |
| In-place cache edits | Local component state mirroring the list | `setQueriesData` on `['priority']` | Keyboard (`MainLayout`) and list share one cache, so both see the dimmed row |
| NULL-safe multi-segment ordering | Two queries + client merge | One row-value comparison with `COALESCE(raw::float8,'-Infinity')` | Verified boundary crossing in one keyset |
| Row → `Article` mapping | Hand-written `ResultSet` code for 13 columns | `BeanPropertyRowMapper<>(Article.class)` (or reuse the `mapArticle` style) | Maps `interest_score` → `setInterestScore` automatically |
| Status polling | A new interval for the banner | `useInterestStatus()` | Already conditional 15 s polling with the correct gate |

**Key insight:** the dangerous parts here are one-line SQL/cache semantics (`GREATEST` ignoring NULL, float8 half-even rounding, refetch-all-pages). None of them fail loudly, so each needs a dedicated test.

## Common Pitfalls

### Pitfall 1: Unscored articles get a "0" badge
**What goes wrong:** Every unscored article shows `0`.
**Why it happens:** PostgreSQL `GREATEST`/`LEAST` ignore NULL arguments. Verified: `select GREATEST(0, NULL::numeric) is null` → `f`; `LEAST(100, GREATEST(0, ROUND(NULL::numeric)))` → `0`.
**How to avoid:** `CASE WHEN raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(raw_n)))::int END`.
**Warning signs:** a repository test asserting `interestScore IS NULL` for an article with no/FAILED/SKIPPED score row fails, and the separator never appears.

### Pitfall 2: Badge ≠ breakdown total by 1 on .5 ties
**What goes wrong:** The "Why 82?" total disagrees with the badge, or the capped/floored line is off by one.
**Why it happens:** `round(float8)` is half-even (`round(2.5::float8) = 2`, verified), `round(numeric)` is half-away-from-zero (`round(82.5::numeric) = 83`, `round(-2.5::numeric) = -3`, verified; PG docs: "For numeric, ties are broken by rounding away from zero. For double precision, the tie-breaking behavior is platform dependent" [CITED: postgresql.org/docs/current/functions-math.html]). Java `Math.round` is half-up.
**How to avoid:** Compute `raw_n` as numeric and derive `total` and `interest_score` from it in SQL. Cast to `float8` only for the sort key.
**Warning signs:** a test with a seeded x.5 raw fails.

### Pitfall 3: `interestScore` missing from JSON, or `save()` failing
**What goes wrong:** Either Spring Data JDBC tries to persist `interest_score` (no such column) on `save()`, or Jackson drops the field.
**Why it happens:** Without `@Transient` Spring Data maps the field as a column. With `java.beans.Transient` Jackson may treat it as ignorable `[ASSUMED]`.
**How to avoid:** `org.springframework.data.annotation.Transient` on the field. Test that `ArticleService.updateState` still saves and that the JSON contains `"interestScore"`.

### Pitfall 4: Plain refetch silently re-ranks and drops read rows
**What goes wrong:** Coming back to the tab or waiting 30 s makes read rows vanish and the order shuffle.
**Why it happens:** Global `staleTime: 30_000` plus v5 refetching all pages, and the endpoint is unread-only.
**How to avoid:** `staleTime: Infinity`, `refetchOnWindowFocus: false`, `refetchOnReconnect: false`, key outside `['articles']`, reset only on explicit refresh, remove on leave.
**Warning signs:** a hook test that marks a row read and advances timers / fires `focus` sees the row disappear.

### Pitfall 5: Nullable cursor parameter with `JdbcClient`
**What goes wrong:** `could not determine data type of parameter $n` on `(:cursorId IS NULL OR …)`.
**Why it happens:** `JdbcClient.param(name, null)` carries no SQL type, and `$n IS NULL` gives PostgreSQL nothing to infer from `[ASSUMED]`. Spring Data's `@Query` methods don't hit this because they bind declared types.
**How to avoid:** Two statements (first page / after cursor), mirroring `findFiltered` / `findFilteredBefore` [VERIFIED: ArticleRepository.java:41-45], or `.param("cursorId", id, Types.BIGINT)`.

### Pitfall 6: Reload on `/priority` returns 404
**What goes wrong:** The SPA works when navigated to, but a browser refresh or bookmark 404s.
**Why it happens:** `SpaForwardController` whitelists routes and `/priority` isn't listed [VERIFIED: SpaForwardController.java:9].
**How to avoid:** Add `"/priority"`, plus a MockMvc test that `GET /priority` forwards to `/index.html`.

### Pitfall 7: `DevProfileConfigTest` fails after adding `myfeeder.interest.blend.profile-points`
**Why it happens:** It asserts every main-yaml key resolves to main's value from the test yaml plus the dev overlay (`for (String name : main.getPropertyNames()) { … isEqualTo(String.valueOf(main.getProperty(name))) }` [VERIFIED: DevProfileConfigTest.java:88-91]).
**How to avoid:** Add the identical key to `src/test/resources/application.yaml`, or keep the default only in `MyfeederProperties` and don't list it in main yaml.

### Pitfall 8: Full-replacement `vi.mock`s break
**What goes wrong:** Existing tests fail with `No "X" export is defined on the mock`.
**Why it happens:** `ArticleList.test.tsx`, `ReadingPane.test.tsx`, `FeedPanel.test.tsx` and `AppShell.test.tsx` fully mock `../stores/uiStore` and/or `../hooks/useArticles`; `useKeyboardShortcuts.test.ts` fully mocks `../api/articles` (listing `getById, list, updateState, markRead, counts, saveToRaindrop`) [VERIFIED: grep of `vi.mock(` + useKeyboardShortcuts.test.ts:3-12].
**How to avoid:** Keep new state in a new `priorityStore` (not uiStore). Add `priority` to the `articlesApi` mock. Add new hook exports to the mocks that consume them. The `Article` fixtures in `useKeyboardShortcuts.test.ts`, `ReadingPane.test.tsx` and `TopicRow.test.tsx` need `interestScore` if the TS field is required, because tests are type-checked (`"include": ["src"]` [VERIFIED: tsconfig.app.json]).

### Pitfall 9: The hint lights from a count drop that isn't "more scored"
**What goes wrong:** Reading an unscored in-window article in Priority, or an article aging out of the window, lowers `eligibleUnscored` and lights "Ranking changed".
**Why it happens:** `eligibleUnscored` counts unread in-window articles needing scoring (`record InterestStatus(boolean configured, String breakerState, boolean coldStart, long eligibleUnscored, long failed)` [VERIFIED: InterestStatus.java:20]). It isn't a "scored" counter.
**How to avoid:** Accept it as a soft hint (UI-SPEC default). A precise signal would be an appended status field (e.g. latest `scored_at`), which is allowed by "later fields are appended" but not requested. See Open Questions.

### Pitfall 10: Mid-scroll drift (accepted by R4)
Between pages, an article can go from unscored to scored, or a weight edit can shift every score. The next page then uses the cursor's *new* tuple, which can repeat rows (client dedupe removes them) or skip rows until refresh. The hint lights on both triggers. Tests must not depend on weights changing between pages.

## Code Examples

### Repository test seeding pattern to copy
```java
// Source: ArticleScoreStoreTest.java (this repo) — @DataJdbcTest + real Postgres, rolled back per test
@DataJdbcTest
@Import({TestcontainersConfiguration.class, InterestScoreQueries.class})
@EnableConfigurationProperties(MyfeederProperties.class)   // precedent: IntegrationConfigControllerTest.java:33
class InterestScoreQueriesTest {
    @Autowired InterestScoreQueries queries;
    @Autowired JdbcTemplate jdbc;
    // seed: feed → articles (varied/equal dates, some read) → article_score (SCORED/FAILED/SKIPPED)
    //       → interest_topic (+20, +14, −30, 0) → article_topic_score nouls
    // assert: full order; walk pages of 3 == full order; boundary crossing; cursor read between pages;
    //         unscored interestScore IS NULL; capped/floored display; topic added after scoring = 0;
    //         weight change reorders with no new Jev call.
}
```
The seed set that produced the verified results in this session (feed 1; topics Rust +20, WebAssembly +14, Politics −30, Zero 0; 20 unread articles with repeating dates; SCORED 1–10 including three tied at raw 82.2; FAILED 11; SKIPPED 12; articles 7 and 13 read) is a good fixture template. Expected full order: `3 10 2 1 8 6 9 5 4 | 19 11 20 18 15 14 17 16 12`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| v4 `refetchPage` to refetch selected infinite pages | v5 removed it; refetch = all pages; `maxPages` limits stored pages | TanStack v5 | Frozen triage must avoid refetch entirely; use reset for page 1 [CITED: tanstack migrating-to-v5] |
| ARCHITECTURE Pattern 4 weight clamp −3..3 | R1 points, −50..+50 (V6 CHECK) | Phase 3 | Don't copy the `LEAST(3, GREATEST(-3, …))` from ARCHITECTURE.md |
| ARCHITECTURE: "frontend invalidates `['articles']` and refetches from page 1" on score changes | R4/D-07: never invalidate Priority; hint + explicit refresh | Research R4 | The Pattern 4 bullet in ARCHITECTURE.md is superseded |

**Deprecated/outdated:** `HttpStatus.UNPROCESSABLE_ENTITY` (use `valueOf(422)`). Not needed in this phase.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `numeric` `'-Infinity'` needs PostgreSQL ≥ 14; prod version at pg.bartram.org is unknown | Standard Stack | None if float8 is used as recommended |
| A2 | A literal `/priority` mapping outranks `/{id}` in Spring MVC | Pattern 2 | A 400 from `Long` conversion of "priority"; caught by the recommended `@WebMvcTest` |
| A3 | Mockito `@InjectMocks` passes `null` for an unmocked constructor argument | Pattern 3 | Existing service tests NPE; adding `@Mock` avoids it either way |
| A4 | `JdbcClient` null param + `:x IS NULL` fails type inference on PostgreSQL | Pitfall 5 | None if the two-statement form is used |
| A5 | Jackson treats `java.beans.Transient` as ignorable | Pitfall 3 | None if Spring Data's `@Transient` is used |
| A6 | Real scored-unread volume is far below the 20k synthetic probe (so about 145 ms/page is a ceiling) | Pattern 2 | Slow Priority pages; would need a V7 index/materialization, a user decision against CLAUDE.md |
| A7 | After `removeQueries`, a still-subscribed disabled observer rebuilds a fresh query on its next `setOptions` | Pattern 5 | Dev-only double fetch; if wrong in prod, switch to the epoch-in-key alternative |

## Open Questions

1. **Precise "more articles scored" signal for the hint**
   - What we know: UI-SPEC defaults to "`eligibleUnscored` dropped below the page-1 baseline". That count also drops on reads and window aging (Pitfall 9).
   - What's unclear: whether false-positive hints bother the user.
   - Recommendation: ship the UI-SPEC default. If it's noisy, append a `lastScoredAt` field to `InterestStatus` later (allowed by its contract).
2. **Should `j`/`k` walk the search-filtered rows?**
   - What we know: UI-SPEC says "walk every rendered Priority row". Existing lists' keyboard walk ignores the search filter (MainLayout uses the raw query).
   - Recommendation: match existing behavior (deduped rows, unfiltered) unless the planner wants to apply `searchQuery` in the keyboard list too. It's cheap either way.
3. **Matched topic with weight 0**
   - What we know: its hinge is > 0 but it contributes 0. UI-SPEC chips require contribution ≠ 0, and rows list "matched" topics.
   - Recommendation: show it as a row with `0` points (it did match) but no chip. Or treat it as non-matching. Either is harmless; pick one and test it.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers repository/integration tests | ✓ | 29.8.0 | — |
| Java | Gradle build/tests | ✓ | 25.0.4 | — |
| Node | Vitest, `tsc -b`, `npmBuild` | ✓ | v26.10.0 | — |
| PostgreSQL (Testcontainers `postgres:latest`) | Blend SQL | ✓ | 18.6 (pulled 6 days ago) | — |

**Missing dependencies with no fallback:** none. **With fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot test slices + Testcontainers (backend); Vitest 4 + React Testing Library (frontend) |
| Config file | `build.gradle.kts` (Gradle test task); `src/main/frontend/vitest.config.ts` (jsdom, `src/test/setup.ts`) |
| Quick run command | `./gradlew test --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` / `cd src/main/frontend && npx vitest run src/hooks/usePriorityArticles.test.ts` |
| Full suite command | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PRIO-01 | Order: raw DESC, date DESC, id DESC; unread only | repository (PG) | `./gradlew test --tests "*InterestScoreQueriesTest"` | ❌ Wave 0 |
| PRIO-01 | `GET /api/articles/priority` routed (not `/{id}`), limit clamp, 404 on missing cursor | @WebMvcTest | `./gradlew test --tests "*ArticleControllerTest"` | ✅ extend |
| PRIO-01 | `/priority` forwarded to index.html | @WebMvcTest | `./gradlew test --tests "*SpaForwardControllerTest"` | ❌ Wave 0 |
| PRIO-02 | Unscored (none/FAILED/SKIPPED/out-of-window) after scored, by date; separator before first null | repository + RTL | `*InterestScoreQueriesTest`; `npx vitest run src/components/PriorityList.test.tsx` | ❌ Wave 0 |
| PRIO-03 | `interestScore` null for unscored (Pitfall 1), clamp 0/100, enrichment on list/board/by-id/PATCH | repository + service unit | `*InterestScoreQueriesTest`, `*ArticleServiceTest`, `*BoardServiceTest` | ✅ extend / ❌ |
| PRIO-03 | Badge tiers 70/40 boundaries, no badge for null | RTL | `npx vitest run src/components/InterestBadge.test.tsx` | ❌ Wave 0 |
| PRIO-04 | Largest remainder sums exactly to SQL total; cap/floor lines; x.5 tie; level index | unit (pure) + repository | `./gradlew test --tests "*ScoreBreakdownsTest"` | ❌ Wave 0 |
| PRIO-04 | Chips by sign with trailing −, Why toggle, non-matching footer | RTL | `npx vitest run src/components/WhyBreakdown.test.tsx` | ❌ Wave 0 |
| PRIO-05 | Mark read/star patches `['priority']` in place; no refetch on focus/timer; refresh fetches page 1 only; leave+re-enter refetches | hook (renderHook + QueryClient) | `npx vitest run src/hooks/usePriorityArticles.test.ts` | ❌ Wave 0 |
| PRIO-06 | Walk pages of N == unpaged order; boundary crossing; cursor read between pages; client dedupe | repository + hook | `*InterestScoreQueriesTest`; `usePriorityArticles.test.ts` | ❌ Wave 0 |
| PRIO-07 | `g p`, `i`, `Shift+A` no-op on /priority, `r` refresh, `j` on last row fetches+selects | hook | `npx vitest run src/hooks/useKeyboardShortcuts.test.ts` | ✅ extend |
| PRIO-08 | Banner precedence (not configured > cold start > paused > waiting), no banner on status error, CTA calls callback | RTL | `npx vitest run src/components/PriorityBanner.test.tsx` | ❌ Wave 0 |
| Criterion 5 | Weight edit changes badge/order with no Jev call | repository | `*InterestScoreQueriesTest` (update `interest_topic.weight`, re-query) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the task's targeted test class/file (commands above).
- **Per wave merge:** `./gradlew test` + `cd src/main/frontend && npm test && npx tsc -b`.
- **Phase gate:** full suite green before `/gsd-verify-work`, plus a manual UAT pass on `./gradlew bootTestRun` + `npm run dev` (triage stability, reload on `/priority`).

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java`: seed fixture from Code Examples
- [ ] `src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java`: pure apportionment cases
- [ ] `src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java`: missing cursor → `NotFoundException`
- [ ] `src/test/java/org/bartram/myfeeder/config/SpaForwardControllerTest.java`
- [ ] `src/main/frontend/src/hooks/usePriorityArticles.test.ts`, `components/PriorityList.test.tsx`, `PriorityBanner.test.tsx`, `InterestBadge.test.tsx`, `WhyBreakdown.test.tsx`
- [ ] Update existing mocks/fixtures per Pitfall 8

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user app with no auth layer (unchanged) |
| V3 Session Management | no | — |
| V4 Access Control | no | No per-user data. The breakdown exposes only the owner's own topics |
| V5 Input Validation | yes | `limit` clamped to [1, 100] (`MAX_LIMIT`); `before` typed `Long` (Spring rejects non-numeric → 400); SQL scope fragments are compile-time constants; all values are named parameters |
| V6 Cryptography | no | — |
| V7 Error Handling | yes | `NotFoundException` → fixed-text ProblemDetail 404. Don't echo SQL errors |

### Known Threat Patterns for Spring JDBC + React

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via dynamic CTE scope | Tampering | Scope fragments chosen from Java constants, never request data. Parameters bound via `JdbcClient.param` |
| Oversized page / expensive query DoS | Denial of service | `limit` clamp; no new unbounded endpoints; the enrichment `IN (:ids)` is bounded by page size |
| XSS via topic names in chips/breakdown | Tampering | Render as React text nodes (no `dangerouslySetInnerHTML`). Topic names are user-authored but rendered escaped |
| State-changing GET | Tampering | All new endpoints are GET, read-only. Mutations reuse the existing PATCH |

## Sources

### Primary (HIGH confidence)
- This repo, read this session: `V1__initial_schema.sql`, `V6__interest_scoring.sql`, `ArticleService.java`, `ArticleRepository.java`, `ArticleController.java`, `BoardController.java`, `BoardService.java`, `PaginatedResponse.java`, `Article.java`, `ArticleScoreStore.java`, `InterestQuestions.java`, `ArticleScoringService.java`, `InterestStatus(.java|Service.java)`, `MyfeederProperties.java`, `SpaForwardController.java`, `GlobalExceptionHandler.java`, `DevProfileConfigTest.java`, both `application.yaml`s; frontend `useArticles.ts`, `api/articles.ts`, `types/index.ts`, `queryClient.ts`, `App.tsx`, `ArticleList.tsx`, `useKeyboardShortcuts.ts`, `uiStore.ts`, `FeedPanel.tsx`, `ReadingPane.tsx`, `BoardArticleList.tsx`, `useInterest.ts`, `api/interest.ts`, `utils/interest.ts`, `InterestsDialog.tsx`, `ShortcutOverlay.tsx`, `main.tsx`, test files
- Live probe: project migrations + blend/keyset SQL on `postgres:latest` (PostgreSQL 18.6), including rounding/`GREATEST` checks and `EXPLAIN ANALYZE` at 100k articles
- Context7 `/tanstack/query`: `infiniteQueryBehavior.ts` onFetch, `resetQueries`, `setQueriesData`, disabling queries, v5 migration (`refetchPage` removed)

### Secondary (MEDIUM confidence)
- Context7 `/websites/spring_io_spring-framework_current_javadoc-api`: `BeanPropertyRowMapper`, `SimplePropertyRowMapper`
- postgresql.org/docs/current: functions-math (round tie-breaking), functions-comparisons (row constructor comparison), datatype-numeric (Infinity special values)

### Tertiary (LOW confidence)
- None relied on without a probe or code read.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, because there are no new dependencies and every library is already installed and in use.
- Architecture (SQL): HIGH, because it was executed against real PostgreSQL with the project schema.
- Architecture (frontend cache): HIGH for the TanStack semantics (library source) and MEDIUM for the removeQueries-on-leave observer detail (A7).
- Pitfalls: HIGH, because 1, 2, 6, 7 and 8 were reproduced or read directly; 3 and 5 are guarded by recommended tests.

**Research date:** 2026-09-25
**Valid until:** 2026-10-25 (stable stack; revisit if TanStack Query or the V6 schema changes)
