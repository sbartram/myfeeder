# Phase 8: Engagement Capture - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Record, in production, when the user opens an article's original link (Open Original button, the reader-view fallback button, or `o`) or saves an article (star, board incl. Read Later / `b`, successful Raindrop save) as sticky, forgettable engagement rows in a new V7 schema, and show a small "Forget engagement" control in the reading pane. Ship it as release v0.3.0 with the ranking (Priority order, badges, "Why N?") exactly as in v0.2.1. Engagement does not feed the learned model in this phase (that is Phase 9).

Requirements: CAPT-01..CAPT-07.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (settled before this discussion; do not reopen)
- `PUT /api/articles/{id}/engagement/open` has no body, is idempotent and returns 204; its kind is `OPEN_ORIGINAL`. `window.open` runs synchronously before a fire-and-forget request, with no error toast. Open Original stays a `<button>`.
- Saves are captured server-side in the services that own them. STAR is recorded in `ArticleService.updateState`, only on an unstarred→starred change. BOARD is recorded in `BoardService.addArticle`. RAINDROP is recorded in `RaindropService`, only after `createBookmark` returns, and outside the circuit-breaker bean.
- The store is a `JdbcClient` store using `INSERT ... ON CONFLICT DO NOTHING`, following `ArticleFeedbackStore`. It is not a `CrudRepository`.
- Engagement is sticky: unstarring, removing an article from a board and deleting a board all keep it. Deleting the article or its feed removes it through `ON DELETE CASCADE`.
- Forget deletes the rows. There is no tombstone, so a later open or save records again.
- There is no backfill of existing stars or boards in V7.
- V7 carries the milestone's whole schema in one migration: `article_engagement` (one row per article and kind, kind CHECK, `created_at`, cascade) plus the Phase 11 dismissal table.
- Kind names and SQL aliases must not contain a word the replay's write-keyword check rejects (e.g. `COPY_LINK`).
- Release is v0.3.0 via `./gradlew release -Prelease.versionIncrementer=incrementMinor`. Never move or re-tag an existing tag.
- CLAUDE.md's "later milestone phases add no migrations" line becomes false with V7; fix it in this phase.

### Forget-engagement control
- **D-01:** Placement: a small text-style control beside the badge / "Why N?" row (`ScoreRow`) in the reading pane, not a toolbar button.
- **D-02:** Wording: it lists the kinds and offers Forget, e.g. `Engaged: opened, starred · Forget`. It renders only when the article has at least one engagement kind.
- **D-03:** Clicking Forget deletes immediately, with no confirm dialog and no undo toast. The control then disappears, because the article is refetched by id.
- **D-04:** No keyboard shortcut for Forget.
- **D-05:** `GET /api/articles/{id}` gains an appended field `engagement: string[]` holding the kind names, `[]` when none. The DTO rule applies: append, never rename.
- **D-06:** After a successful open-PUT, a save (star / board / Read Later / Raindrop) or a Forget, the client invalidates only the by-id article query (`['article', id]`) so the label updates right away. Never invalidate `['priority']` or the article lists for engagement.

### Save-path failure semantics
- **D-07:** Engagement writes on the star and board paths are best-effort. The user's star or board add always succeeds. An engagement insert failure is caught and logged at WARN with ids and exception class only. Because a failing statement aborts a Postgres transaction, the engagement write must not share a transaction with the user's save; the planner verifies there is no enclosing `@Transactional`, or isolates the write.
- **D-08:** Raindrop is best-effort too. Once `createBookmark` has returned, the save reports success even if the engagement insert fails, which is logged at WARN. Surfacing an error would invite a retry that creates a duplicate bookmark.
- **D-09:** `BoardService.addArticle` records BOARD even when the article is already on that board. The engagement insert happens before the existing early return and relies on `ON CONFLICT DO NOTHING`. As a result, boards filled before V7 earn credit when an article is re-added.
  - *Amended 2026-09-29 (user-approved at plan-phase):* BOARD is recorded after the save-or-already-present branch rather than before the early return. If the article is not on the board it is saved first, then BOARD is recorded either way. A board add that throws (for example an unknown board id failing the FK on board_article) records nothing, while re-adds, including articles on boards filled before V7, still earn credit. `ON CONFLICT DO NOTHING` still makes a repeat a no-op. Implemented by 08-03 Task 2.

### Open-capture edges
- **D-10:** Every Open Original entry point uses one helper (e.g. `useOpenOriginal`) and records `OPEN_ORIGINAL`: the toolbar button (`ReadingPane.tsx:215`), the reader-view extraction-error fallback button (`ReadingPane.tsx:184`) and the `o` shortcut (`useKeyboardShortcuts.ts:173`). In-body link clicks (`handleContentClick`) and Copy Link record nothing (CAPT-07).
- **D-11:** No client-side dedupe: the PUT is sent on every open, and server idempotency handles repeats.
- **D-12:** An open on a missing article returns 404 through `NotFoundException`, and the fire-and-forget client ignores every error without a toast. The tab has already opened by then.

### V7 suggestion dismissal table (used by Phase 11)
- **D-13:** One table records handled suggestions: `topic_suggestion_dismissal(article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE, reason CHECK IN ('DISMISSED','TOPIC_CREATED'), created_at)`. It has no `topic_id` column. The exact names are the planner's call, provided they pass the replay keyword check. — **Reversibility:** one-way — changing the table shape after release needs a new Flyway migration on prod data
- **D-14:** A handled suggestion stays handled. Deleting a topic that was created from a suggestion does not bring the suggestion back.

### Claude's Discretion
- Exact table and column names, the index on `article_engagement` (e.g. by kind, for Phase 9 joins) and the `EngagementKind` enum layout.
- The route name for Forget. The roadmap's research uses `DELETE /api/articles/{id}/engagement` returning 204; follow it unless there is a reason not to.
- Visual styling of the Forget control, reusing `ScoreRow` / `toolbar-btn` link-style classes, and how the kind labels are worded (opened / starred / boarded / Raindrop).
- Release and production verification: the plan ends with the v0.3.0 release and deploy behind a human checkpoint, as in Phase 7, plus a check that `article_engagement` rows accumulate in prod and that the Priority order and badges are unchanged.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone scope and decisions
- `.planning/ROADMAP.md` §"Phase 8: Engagement Capture": success criteria and settled notes
- `.planning/REQUIREMENTS.md`: CAPT-01..CAPT-07, plus the Out of Scope table (no dwell, selection or reader-view signals)
- `.planning/STATE.md` §"Settled for v0.3.0 before roadmapping": locked decisions
- `.planning/PROJECT.md`: milestone goal, constraints (Flyway V7 next) and key decisions

### Research
- `.planning/research/SUMMARY.md`: Open Decisions 3–5 (now settled) and the Phase 8 delivery list
- `.planning/research/ARCHITECTURE.md`: capture points, store design, V7 layout
- `.planning/research/PITFALLS.md`: capture pitfalls 5, 6, 7, 19, 20, 21 (window.open popup blocking, fire-and-forget, keyword check, etc.)
- `.planning/research/STACK.md`: open endpoint shape (PUT is never a CORS simple request)
- `.planning/research/FEATURES.md`: engagement kinds and table-stakes list

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/CONVENTIONS.md`, `.planning/codebase/TESTING.md`

### Project rules
- `CLAUDE.md`: Spring Data JDBC conventions, GlobalExceptionHandler mappings, migration test pattern, the Interest Ranking section (the V6-only migration line to fix) and the release pipeline

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `repository/ArticleFeedbackStore.java`: the `JdbcClient` store pattern with `ON CONFLICT` upserts, which `ArticleEngagementStore` should mirror.
- `ArticleService.getArticle` path, which already calls `article.setFeedback(articleFeedbackStore.find(id)...)`: the place to attach `engagement` kinds for the by-id payload.
- `components/ScoreRow.tsx`: the host for the Forget control (D-01).
- `api/articles.ts` (`apiPut`/`apiDeleteJson`, feedback endpoints) and `hooks/useFeedback.ts`: patterns for the open and forget calls and the by-id invalidation.
- `V6__interest_scoring.sql`: the cascade and CHECK style for V7. `V4StripRaindropApiTokenMigrationTest` shows the migration test pattern.

### Established Patterns
- Feedback tables cascade from `article(id)`, and engagement should too (CAPT-05 delete behaviour).
- `ArticleService.updateState` loads the article before mutating, so the previous `starred` value is available for the unstarred→starred check. It has no `@Transactional`.
- `BoardService.addArticle` returns early when the article is already on the board (`existsByBoardIdAndArticleId`); per D-09, engagement goes before that return. (Superseded by the D-09 amendment of 2026-09-29: BOARD is recorded after the save-or-already-present branch.)
- `useReadLater` calls `getOrCreateByName('Read Later')` then `addArticle`, so it is captured server-side through `BoardService.addArticle` with no separate client call.
- `RaindropService.saveToRaindrop` calls `raindropApiClient.createBookmark(...)` at line 43; RAINDROP is recorded after it returns. A thrown exception or an open breaker records nothing.
- Frontend: `npx tsc -b` for type-checking. Vitest + RTL, with fetch mocked via `globalThis.fetch`.

### Integration Points
- New `ArticleController` routes, alongside `/{id}/feedback`: `PUT /{id}/engagement/open` and the Forget DELETE.
- `ReadingPane.tsx` `handleOpenOriginal` (both buttons) and `useKeyboardShortcuts.ts` `case 'o'`, which move to the shared helper.
- The `Article` model/DTO gains the `engagement` field, and the frontend `Article` type gains it too.

</code_context>

<specifics>
## Specific Ideas

- Example label: `Engaged: opened, starred · Forget`, with small text beside the score row.
- The ranking must be provably unchanged after this phase: engagement rows exist, and no query in `InterestScoreQueries` reads them yet.

</specifics>

<deferred>
## Deferred Ideas

- ENG-F1 reading-pane engagement status line (a richer version of the D-02 label): future.
- Resurfacing a suggestion when its created topic is deleted: rejected (D-14).

### Reviewed Todos (not folded)
- `2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` (Tune Raindrop resilience) matched on the "raindrop" keyword only. PROJECT.md keeps carried-forward cleanup, including Raindrop tuning, out of this milestone.

</deferred>

---

*Phase: 08-engagement-capture*
*Context gathered: 2026-09-29*
