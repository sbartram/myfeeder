# Phase 8: Engagement Capture - Research

**Researched:** 2026-09-29
**Domain:** Recording implicit engagement (open original, star, board, Raindrop) as sticky, forgettable rows in Postgres (Flyway V7, a `JdbcClient` store, service-layer capture, one frontend open helper, and a small reading-pane Forget control), with the ranking left unchanged
**Confidence:** HIGH. Every integration point was read from the code at `9917477` during this session. The browser and test-runner facts were checked against Context7. No new dependencies.

## Summary

Phase 8 uses only patterns the codebase already has, and it needs no new library. The backend adds a V7 migration with two tables, an `EngagementKind` enum, an `ArticleEngagementStore` built on `JdbcClient` with `INSERT ... ON CONFLICT DO NOTHING`, three best-effort capture calls (in `ArticleService.updateState`, `BoardService.addArticle` and `RaindropService.saveToRaindrop`), two new `ArticleController` routes (open PUT, Forget DELETE) delegated to `ArticleService`, and an appended `engagement` field on the by-id `Article`. The frontend adds one `useOpenOriginal` helper, wired to the three Open Original entry points, and a Forget control shown next to the score row. After an engagement it also invalidates the exact by-id query in the four save/open/forget reactions.

**D-07 is confirmed safe from the code.** No `@Transactional` exists on `ArticleService`, `BoardService` or `RaindropService`, at class or method level. Their only callers are `ArticleController` and `BoardController`, and neither of those is transactional either. No `TransactionTemplate` or transaction interceptor exists anywhere in `src/main/java`. So an engagement INSERT runs in its own autocommit statement after the user's save has committed, and a failing INSERT cannot abort the save. What remains is to catch and log the failure (D-07/D-08) and to **keep it that way**: the milestone research proposed making `updateState` `@Transactional` (ARCHITECTURE Pattern 1, PITFALLS 19), and that would put the engagement INSERT back inside the user's transaction. Don't do it in this phase.

Four facts the planner must honor were found in the current code, and none of them appears in CONTEXT.md:
1. `ScoreRow` returns `null` for an unscored article, so a Forget control placed purely inside it would never show on engaged-but-unscored articles. Those are common, because an article is auto-marked read after 1 s and scoring only picks up unread articles.
2. The line numbers in D-10 are swapped. The toolbar button is at `ReadingPane.tsx:183-185` and the extraction-error fallback is at `:215`.
3. `ReadingPane.test.tsx` renders without a `QueryClientProvider` and mocks every hook module, so the new hook module must be mocked there too.
4. `invalidateQueries({ queryKey: ['article', id] })` matches by prefix, which also invalidates the reader-view `['article', id, 'extracted']` query. Use `exact: true`.

**Primary recommendation:** mirror `ArticleFeedbackStore` and `V6InterestScoringMigrationTest`. Record saves after the user's write succeeds, through one catch-and-WARN store method. Put the open and Forget logic on `ArticleService`, so `ArticleController` gains no new dependency. Build `useOpenOriginal` as `window.open` followed by an exception-safe promise chain that swallows every error. Prove the ranking is unchanged with a static guard that the ranking SQL never mentions `article_engagement`, plus a badge-before/after integration assertion.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (settled before this discussion; do not reopen)
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

#### Forget-engagement control
- **D-01:** Placement: a small text-style control beside the badge / "Why N?" row (`ScoreRow`) in the reading pane, not a toolbar button.
- **D-02:** Wording: it lists the kinds and offers Forget, e.g. `Engaged: opened, starred · Forget`. It renders only when the article has at least one engagement kind.
- **D-03:** Clicking Forget deletes immediately, with no confirm dialog and no undo toast. The control then disappears, because the article is refetched by id.
- **D-04:** No keyboard shortcut for Forget.
- **D-05:** `GET /api/articles/{id}` gains an appended field `engagement: string[]` holding the kind names, `[]` when none. The DTO rule applies: append, never rename.
- **D-06:** After a successful open-PUT, a save (star / board / Read Later / Raindrop) or a Forget, the client invalidates only the by-id article query (`['article', id]`) so the label updates right away. Never invalidate `['priority']` or the article lists for engagement.

#### Save-path failure semantics
- **D-07:** Engagement writes on the star and board paths are best-effort. The user's star or board add always succeeds. An engagement insert failure is caught and logged at WARN with ids and exception class only. Because a failing statement aborts a Postgres transaction, the engagement write must not share a transaction with the user's save; the planner verifies there is no enclosing `@Transactional`, or isolates the write.
- **D-08:** Raindrop is best-effort too. Once `createBookmark` has returned, the save reports success even if the engagement insert fails, which is logged at WARN. Surfacing an error would invite a retry that creates a duplicate bookmark.
- **D-09:** `BoardService.addArticle` records BOARD even when the article is already on that board. The engagement insert happens before the existing early return and relies on `ON CONFLICT DO NOTHING`. As a result, boards filled before V7 earn credit when an article is re-added.

#### Open-capture edges
- **D-10:** Every Open Original entry point uses one helper (e.g. `useOpenOriginal`) and records `OPEN_ORIGINAL`: the toolbar button (`ReadingPane.tsx:215`), the reader-view extraction-error fallback button (`ReadingPane.tsx:184`) and the `o` shortcut (`useKeyboardShortcuts.ts:173`). In-body link clicks (`handleContentClick`) and Copy Link record nothing (CAPT-07).
- **D-11:** No client-side dedupe: the PUT is sent on every open, and server idempotency handles repeats.
- **D-12:** An open on a missing article returns 404 through `NotFoundException`, and the fire-and-forget client ignores every error without a toast. The tab has already opened by then.

#### V7 suggestion dismissal table (used by Phase 11)
- **D-13:** One table records handled suggestions: `topic_suggestion_dismissal(article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE, reason CHECK IN ('DISMISSED','TOPIC_CREATED'), created_at)`. It has no `topic_id` column. The exact names are the planner's call, provided they pass the replay keyword check. — **Reversibility:** one-way — changing the table shape after release needs a new Flyway migration on prod data
- **D-14:** A handled suggestion stays handled. Deleting a topic that was created from a suggestion does not bring the suggestion back.

### Claude's Discretion
- Exact table and column names, the index on `article_engagement` (e.g. by kind, for Phase 9 joins) and the `EngagementKind` enum layout.
- The route name for Forget. The roadmap's research uses `DELETE /api/articles/{id}/engagement` returning 204; follow it unless there is a reason not to.
- Visual styling of the Forget control, reusing `ScoreRow` / `toolbar-btn` link-style classes, and how the kind labels are worded (opened / starred / boarded / Raindrop).
- Release and production verification: the plan ends with the v0.3.0 release and deploy behind a human checkpoint, as in Phase 7, plus a check that `article_engagement` rows accumulate in prod and that the Priority order and badges are unchanged.

### Deferred Ideas (OUT OF SCOPE)
- ENG-F1 reading-pane engagement status line (a richer version of the D-02 label): future.
- Resurfacing a suggestion when its created topic is deleted: rejected (D-14).

#### Reviewed Todos (not folded)
- `2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` (Tune Raindrop resilience) matched on the "raindrop" keyword only. PROJECT.md keeps carried-forward cleanup, including Raindrop tuning, out of this milestone.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CAPT-01 | Opening an article's original link (Open Original button or `o`) records an `OPEN_ORIGINAL` engagement, once per article. The tab always opens, even if recording fails. | Open helper pattern (Pattern 3), open endpoint (Pattern 2), idempotent store (Pattern 1); entry points located (ReadingPane.tsx:183-185, :215; useKeyboardShortcuts.ts:172-173); Vitest ordering and unhandled-rejection tests (Validation) |
| CAPT-02 | Starring an article (unstarred → starred) records a `STAR` engagement | `updateState` loads the article before it mutates it (ArticleService.java:49-63), so capture the prior `starred` value first; best-effort record after `save` |
| CAPT-03 | Adding an article to any board (including Read Later and `b`) records one `BOARD` engagement per article, however many boards it is on | Every board add goes through `POST /api/boards/{id}/articles` → `BoardService.addArticle` (BoardController.java:48-52; useBoards.ts:28-56); PK `(article_id, kind)` collapses multiple boards to one row; D-09 ordering refinement (Pitfall 3) |
| CAPT-04 | A successful Raindrop save records `RAINDROP`. A failed save, or one blocked by an open breaker, records nothing. | `createBookmarkFallback` always throws (RaindropApiClientImpl.java:78-84), so a normal return from `createBookmark` (RaindropService.java:43) means success; record right after it |
| CAPT-05 | Sticky: unstarring, removing from a board or deleting a board keeps it. Deleting the article or its feed removes it. | V7 FK `REFERENCES article(id) ON DELETE CASCADE`; `article.feed_id ... ON DELETE CASCADE` (V1:20); no FK to `board`; nothing calls the store's delete except Forget |
| CAPT-06 | Forget control, visible only when the article has engagement | `Article.engagement` @Transient on by-id only (Article.java pattern for `feedback`); `DELETE /api/articles/{id}/engagement`; ScoreRow null-return trap (Pitfall 1); exact by-id invalidation |
| CAPT-07 | Nothing else records engagement | `handleContentClick` (ReadingPane.tsx:133-139) and Copy Link stay untouched; auto-mark-read sends `{read:true}` only (ReadingPane.tsx:76), so `starred == null` records nothing; no capture in markRead, OPML, polling or extraction |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Root `CLAUDE.md`, `.claude/CLAUDE.md` and the user's global `~/.claude/CLAUDE.md`, which carry the same authority as locked decisions:

- **Spring Data JDBC, not JPA.** `@Id` comes from `org.springframework.data.annotation` and `@Table` from `org.springframework.data.relational.core.mapping`, with no derived query methods. A non-persisted entity field needs `org.springframework.data.annotation.Transient`, as `Article.feedback` has.
- **Jackson 3.x.** Databind lives in `tools.jackson.databind.*`, while annotations stay in `com.fasterxml.jackson.annotation.*` (`@JsonInclude` on `Article`).
- **GlobalExceptionHandler mappings.** `NotFoundException` → 404, `IllegalArgumentException` → 400, `IllegalStateException` → 409. Throw the right type from services; controllers have no try/catch.
- **DTO rule.** New fields are appended and never renamed (D-05).
- **Resilience4j annotations stay on the API-client bean.** The Raindrop capture belongs in `RaindropService`, not in `RaindropApiClientImpl`.
- **Migration test pattern.** Flyway runs at `@DataJdbcTest` startup. There is no backfill here, so follow `V6InterestScoringMigrationTest` (schema shape, CHECKs and cascades, with at most one expected violation per method, as the last statement).
- **Test patterns.** Mockito unit tests use `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks`. Controllers use `@WebMvcTest` + `@MockitoBean`. Repositories use `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)`. Integration tests use `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
- **Tests stay offline.** Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`, and add no new `spring.ai.typesafe.*` keys to the test yaml. Mock Jev with `@MockitoBean JevApiClient` and `verify(..., never())`.
- **Frontend type-check** with `npx tsc -b` from `src/main/frontend/`; plain `tsc --noEmit` returns false success.
- **Frontend `vi.mock` of a module is full replacement.** Every test that mocks `../api/articles` or a hooks module must list any new export it uses.
- **Release pipeline order.** `./gradlew release` first, then `./gradlew clean bootJar`, `docker build --provenance=false`, push, `./deploy.sh $VERSION` with the version passed explicitly, `rollout status`, then logs. Minor releases use `-Prelease.versionIncrementer=incrementMinor`, and existing tags are never moved.
- **Don't hand-edit generated OpenWiki pages.**
- **GSD enforcement.** Edits go through a GSD workflow. When a plan fixes a tracked item, mark it resolved in the same commit.
- **Git (user global).** Non-trivial work goes on a feature branch or worktree, and merges to main use `--no-ff`. Push only on request. Subagents never check out a detached SHA.
- **Simplicity and surgical changes (user global).** Build nothing speculative and don't refactor adjacent code. Remove only the orphans your own change creates.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| STAR / BOARD / RAINDROP capture | API / Backend (owning service) | — | Each save has exactly one server path. Capturing it there covers every client entry point (button, `s`, `b`, Read Later, BoardManager, `v`) and can't count a failed Raindrop save |
| OPEN_ORIGINAL capture | Browser / Client (intent) → API (record) | — | The open happens only in the browser (`window.open`). The client reports it through the one open-only endpoint |
| Idempotency and "one row per article and kind" | Database / Storage | API (store SQL) | Enforced by PK `(article_id, kind)` + `ON CONFLICT DO NOTHING`; race-safe with no application locking |
| Stickiness and cascade on article/feed delete | Database / Storage | — | FK `ON DELETE CASCADE` to `article`; no FK to `board`, so board removal and delete can't touch engagement |
| Forget | API / Backend | Browser (control) | One DELETE of the article's rows; the client only shows the control and refetches by id |
| Engagement label and Forget visibility | Browser / Client | API (by-id payload) | The client renders `Article.engagement`, which only `GET /api/articles/{id}` fills |
| Ranking unchanged | API / Backend (`InterestScoreQueries` untouched) | — | Nothing in the blend SQL reads the new table in Phase 8 |

## Standard Stack

### Core (all already in the project; nothing to install)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `JdbcClient` (spring-jdbc, via Boot 4.0.8) | BOM-managed | `ArticleEngagementStore` SQL | The established store pattern (`ArticleFeedbackStore`, `ArticleScoreStore`) [VERIFIED: repository/ArticleFeedbackStore.java:1-82] |
| Flyway (Boot-managed) | BOM-managed | `V7__engagement.sql` | Every schema change so far is a numbered migration (V1–V6) [VERIFIED: ls src/main/resources/db/migration] |
| PostgreSQL | prod `pg.bartram.org`; tests via Testcontainers | Storage, `ON CONFLICT DO NOTHING` | Already used for idempotent inserts (`ArticleScoreStore.java:139,175`) [VERIFIED: grep ON CONFLICT] |
| TanStack Query | ^5.103.2 | By-id invalidation, Forget mutation | Existing data layer [VERIFIED: package.json] |
| Vitest + RTL + jsdom | vitest ^4.1.11, jsdom ^29.1.1 | Frontend tests | Existing test stack [VERIFIED: package.json, vitest.config.ts] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `fetch` promise chain in the open helper | `useMutation` with `meta: { inlineError: true }` | Both suppress the global toast (queryClient.ts:16). A plain promise is simpler, needs no mutation bookkeeping and can't be retried by accident. Either works, and the recommendation is the plain promise |
| Direct store calls from the services | `ArticleEngagedEvent` + `@TransactionalEventListener` | Extra indirection for no gain; the milestone ARCHITECTURE research rejected it too |
| `navigator.sendBeacon` | — | Rejected by research: it throws on JSON Blobs in Chromium and jsdom doesn't implement it. The open PUT has no body anyway |

**Installation:** none.

## Package Legitimacy Audit

This phase installs **no external packages**: `build.gradle.kts` and `package.json` stay unchanged, so no legitimacy check applies.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                         Browser (React)
  ┌──────────────────────────────────────────────────────────────────────┐
  │ ↗ Open Original (toolbar)   ↗ Open Original (reader-error)   'o' key │
  │            └───────────────┬──────────────┘                  │       │
  │                            ▼                                 ▼       │
  │                   useOpenOriginal(article) ◄────────────────┘        │
  │                     1. url blank? → return                           │
  │                     2. window.open(url,'_blank','noopener')  [sync]  │
  │                     3. Promise chain: PUT …/engagement/open          │
  │                          ok  → invalidate ['article',id] exact       │
  │                          err → swallowed (no toast)                  │
  │                                                                      │
  │ ★ / 's' ─PATCH /articles/{id}──┐   in-body <a> click ─► window.open  │
  │ 📋 / 🔖 / 'b' ─POST /boards/{b}/articles─┐   (records nothing)       │
  │ 💧 / 'v' ─POST /articles/{id}/raindrop─┐ │   Copy Link (nothing)     │
  │ ScoreRow: "Engaged: … · Forget" ─DELETE /articles/{id}/engagement─┐  │
  └────────────────────────────────────────┼─┼────────────────────────┼──┘
                                           │ │                        │
                   Spring MVC (no @Transactional anywhere on these paths)
  ┌────────────────────────────────────────┼─┼────────────────────────┼──┐
  │ ArticleController.updateState ─► ArticleService.updateState          │
  │     wasStarred = article.starred; save(article) ✓                    │
  │     if starred==TRUE && !wasStarred → store.recordQuietly(id, STAR)  │
  │ BoardController ─► BoardService.addArticle                           │
  │     if !exists → save(board_article) ✓ ; store.recordQuietly(BOARD)  │
  │ ArticleController.saveToRaindrop ─► RaindropService                  │
  │     validate (409/400) → RaindropApiClient.createBookmark            │
  │         [@CircuitBreaker+@Retry; fallback always throws]             │
  │     returned normally → store.recordQuietly(id, RAINDROP)            │
  │ ArticleController PUT …/engagement/open ─► ArticleService.recordOpen │
  │     !existsById → NotFoundException(404); store.record(OPEN_ORIGINAL)│
  │ ArticleController DELETE …/engagement ─► ArticleService.forget…      │
  │     !existsById → 404; store.deleteAll(id) → 204                     │
  │ GET /articles/{id} ─► findByIdWithBreakdown: + setEngagement(kinds)  │
  └──────────────────────────────┬───────────────────────────────────────┘
                                 ▼  (each statement autocommits)
  ┌──────────────────────────────────────────────────────────────────────┐
  │ Postgres: article_engagement PK(article_id, kind) FK→article CASCADE │
  │           topic_suggestion_dismissal PK(article_id) FK→article CASC. │
  │ InterestScoreQueries / blend CTE: UNCHANGED (never reads either)     │
  └──────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure (new and touched files only)

```
src/main/resources/db/migration/V7__engagement.sql            # NEW: both tables
src/main/java/org/bartram/myfeeder/
├── model/EngagementKind.java                                  # NEW enum OPEN_ORIGINAL, STAR, BOARD, RAINDROP
├── model/Article.java                                         # MOD: @Transient List<EngagementKind> engagement
├── repository/ArticleEngagementStore.java                     # NEW: JdbcClient store
├── service/ArticleService.java                                # MOD: STAR capture, recordOpen, forgetEngagement, by-id engagement
├── service/BoardService.java                                  # MOD: BOARD capture
├── integration/RaindropService.java                           # MOD: RAINDROP capture
└── controller/ArticleController.java                          # MOD: PUT /{id}/engagement/open, DELETE /{id}/engagement
src/test/java/org/bartram/myfeeder/
├── repository/V7EngagementMigrationTest.java                  # NEW (V6InterestScoringMigrationTest shape)
├── repository/ArticleEngagementStoreTest.java                 # NEW (+ "ranking SQL never reads engagement" guard)
├── controller/EngagementApiIntegrationTest.java               # NEW (FeedbackApiIntegrationTest shape)
└── service/ArticleServiceTest, BoardServiceTest, integration/RaindropServiceTest, controller/ArticleControllerTest  # MOD
src/main/frontend/src/
├── api/client.ts                                              # MOD: bodyless PUT (make apiPut body optional)
├── api/articles.ts                                            # MOD: recordOpen, forgetEngagement
├── types/index.ts                                             # MOD: EngagementKind type, Article.engagement?
├── hooks/useEngagement.ts (+ .test.ts)                        # NEW: useOpenOriginal, useForgetEngagement
├── hooks/useArticles.ts                                       # MOD: useSaveToRaindrop invalidates by-id exact
├── hooks/useBoards.ts                                         # MOD: useAddArticleToBoard, useReadLater invalidate by-id exact
├── hooks/useKeyboardShortcuts.ts (+ test)                     # MOD: 'o' → helper
├── components/ReadingPane.tsx (+ test)                        # MOD: both buttons → helper
├── components/ScoreRow.tsx                                    # MOD: engagement label + Forget
└── App.css                                                    # MOD (small): engagement label style
CLAUDE.md                                                      # MOD: V7 schema/Flyway lines, package list, capture rules, routes
```

### Pattern 1: The idempotent engagement store (mirror `ArticleFeedbackStore`)

**What:** A `@Repository` class that depends only on `JdbcClient`. Spring Data JDBC `save()` with a preset composite key issues an UPDATE, so a `CrudRepository` would fail on the first insert (research Pitfall 7).

The existing template, quoted verbatim from `ArticleFeedbackStore.java:31-33`:
```java
jdbc.sql("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (:articleId, :vote, :narrowed) "
                + "ON CONFLICT (article_id) DO UPDATE SET vote = EXCLUDED.vote, "
                + "topics_narrowed = EXCLUDED.topics_narrowed")
```
and its delete returns the row count (`ArticleFeedbackStore.java:77-81`: `public int delete(long articleId) { return jdbc.sql("DELETE FROM article_feedback WHERE article_id = :articleId")...update(); }`) [VERIFIED: repository/ArticleFeedbackStore.java:31-33,77-81].

Recommended shape (names are the planner's call):
```java
@Slf4j
@Repository
@RequiredArgsConstructor
public class ArticleEngagementStore {
    private final JdbcClient jdbc;

    /** Idempotent: true when a row was inserted, false when (article, kind) already existed. */
    public boolean record(long articleId, EngagementKind kind) {
        return jdbc.sql("INSERT INTO article_engagement (article_id, kind) VALUES (:articleId, :kind) "
                        + "ON CONFLICT (article_id, kind) DO NOTHING")
                .param("articleId", articleId).param("kind", kind.name())
                .update() == 1;
    }

    /** Best-effort capture for the save paths (D-07/D-08): never throws, WARNs ids + exception class only. */
    public void recordQuietly(long articleId, EngagementKind kind) {
        try {
            record(articleId, kind);
        } catch (DataAccessException e) {
            log.warn("Engagement {} not recorded for article {}: {}", kind, articleId, e.getClass().getSimpleName());
        }
    }

    /** Kinds in enum declaration order; empty when none. */
    public List<EngagementKind> kinds(long articleId) { /* SELECT kind ... ; map valueOf; sort natural */ }

    /** Forget: rows deleted (0 when none). */
    public int deleteAll(long articleId) { /* DELETE FROM article_engagement WHERE article_id = :articleId */ }
}
```
- Don't annotate any store method `@Transactional`. Each statement autocommits, which is what D-07 needs.
- The WARN message format follows `JevEventLogging`: simple class names only, never a message or body (CLAUDE.md, Jev event log lines).
- Put the best-effort catch in one place, the store, so the three services stay one-liners. Service unit tests then verify `recordQuietly` is called or not called. The swallow itself is proven in `ArticleEngagementStoreTest`, for example with a nonexistent `article_id`, which raises an FK violation that the method must swallow.

### Pattern 2: Capture in the owning services, after the user's write

The code as it stands now, quoted verbatim:

`ArticleService.java:49-63`:
```java
    public Article updateState(Long id, Boolean read, Boolean starred) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Article not found: " + id));

        if (read != null) {
            article.setRead(read);
        }
        if (starred != null) {
            article.setStarred(starred);
        }

        Article saved = articleRepository.save(article);
        withScores(List.of(saved));
        return saved;
    }
```
`BoardService.java:62-69`:
```java
    public void addArticle(Long boardId, Long articleId) {
        if (boardArticleRepository.existsByBoardIdAndArticleId(boardId, articleId)) return;
        BoardArticle ba = new BoardArticle();
        ba.setBoardId(boardId);
        ba.setArticleId(articleId);
        ba.setAddedAt(Instant.now());
        boardArticleRepository.save(ba);
    }
```
`RaindropService.java:43-44`:
```java
        raindropApiClient.createBookmark(config.getCollectionId(), article.getUrl(), article.getTitle());
        log.info("Saved article '{}' to Raindrop.io", article.getTitle());
```
[VERIFIED: service/ArticleService.java:49-63, service/BoardService.java:62-69, integration/RaindropService.java:24-45]

Recommended edits:
- **STAR:** compute `boolean newlyStarred = Boolean.TRUE.equals(starred) && !article.isStarred();` *before* `article.setStarred(...)`, then call `if (newlyStarred) engagementStore.recordQuietly(saved.getId(), EngagementKind.STAR);` after `articleRepository.save(article)`. `Article.starred` is a primitive `boolean` (`Article.java:27`: `private boolean starred;`), so `isStarred()` never returns null [VERIFIED: model/Article.java:26-27].
- **BOARD (D-09 intent, safer order):** `if (!exists) { ...save(ba); } engagementStore.recordQuietly(articleId, EngagementKind.BOARD);`. This records BOARD for an add that succeeded and for an article already on the board (D-09), but not for an add that threw. See Pitfall 3 for why this beats inserting before the early return.
- **RAINDROP:** `engagementStore.recordQuietly(article.getId(), EngagementKind.RAINDROP);` goes between the `createBookmark` line and the `log.info`. `createBookmarkFallback` always throws, so a failed call or an open breaker never reaches this line. Quoted verbatim from `RaindropApiClientImpl.java:78-84`: `if (throwable instanceof RaindropNotConfiguredException rnc) { throw rnc; } throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);` [VERIFIED: integration/RaindropApiClientImpl.java:52-84]. The business validation throws first at `RaindropService.java:25-41`: not configured → `IllegalStateException`, disabled → `IllegalStateException`, no collection → `IllegalArgumentException`.

**Transaction boundaries (D-07), verified in code:**
- `grep -rn Transactional src/main/java` lists only `ArticleFeedbackStore:29`, `ArticleScoreStore:112`, `FolderService:48`, `OpmlImportService:35`, `InterestService:68`, `ArticleFeedbackService:43,61` and two `@TransactionalEventListener`s. **None** is on `ArticleService`, `BoardService`, `RaindropService`, `ArticleController` or `BoardController`.
- A grep for `TransactionTemplate|PlatformTransactionManager|TransactionInterceptor` returns nothing.
- The only callers are controllers: `grep "updateState(\|\.addArticle(\|saveToRaindrop("` finds `ArticleController.java:76,107` and `BoardController.java:51` only.
- The one transactional caller of `ArticleService` is `ArticleFeedbackService`, and it calls only `findById` / `findByIdWithBreakdown`, which are reads (`ArticleFeedbackService.java:92,109`).

[VERIFIED: grep + read of those files this session]

### Pattern 3: The open helper (one path for all three entry points)

The code as it stands now [VERIFIED: components/ReadingPane.tsx:114-116,133-139,183-185,213-216; hooks/useKeyboardShortcuts.ts:172-173]:
```tsx
// ReadingPane.tsx:114-116
  const handleOpenOriginal = () => {
    window.open(article.url, '_blank', 'noopener')
  }
// ReadingPane.tsx:183-185  (TOOLBAR button — CONTEXT D-10 says :215, that is swapped)
        <button className="toolbar-btn" onClick={handleOpenOriginal} style={{ marginLeft: 'auto' }}>
          ↗ Open Original
        </button>
// ReadingPane.tsx:213-216  (reader-view extraction-error FALLBACK — CONTEXT D-10 says :184)
          <p className="reader-status">
            {"Couldn't load the full article. "}
            <button className="toolbar-btn" onClick={handleOpenOriginal}>↗ Open Original</button>
          </p>
// ReadingPane.tsx:133-139  (in-body links — must stay untouched, records nothing)
  const handleContentClick = (e: React.MouseEvent) => {
    const link = (e.target as HTMLElement).closest('a')
    if (link?.href) {
      e.preventDefault()
      window.open(link.href, '_blank', 'noopener')
    }
  }
// useKeyboardShortcuts.ts:172-173
        case 'o':
          if (currentArticle) window.open(currentArticle.url, '_blank', 'noopener')
```
Both ReadingPane buttons already share `handleOpenOriginal`, so swapping its body for the helper covers two of the three entry points. The `o` case needs the helper passed through the hook, and `openOriginal` must be added to the `handleKeyDown` `useCallback` deps array (`useKeyboardShortcuts.ts:221`).

Recommended helper:
```ts
// hooks/useEngagement.ts
export function useOpenOriginal() {
  const qc = useQueryClient()
  return useCallback((article: Pick<Article, 'id' | 'url'>) => {
    if (!article.url) return                                  // research Pitfall 21
    window.open(article.url, '_blank', 'noopener')            // FIRST, synchronous: keeps user activation
    void Promise.resolve()
      .then(() => articlesApi.recordOpen(article.id))         // never awaited before window.open (D-10/D-11)
      .then(() => qc.invalidateQueries({ queryKey: ['article', article.id], exact: true }))  // D-06
      .catch(() => {})                                        // D-12: every error ignored, no toast
  }, [qc])
}
```
- `Promise.resolve().then(...)` turns even a synchronous throw, such as a test mock that lacks `recordOpen`, into a swallowed rejection. `fetch` itself runs in a microtask after `window.open`.
- Don't use a `useMutation` without `meta: { inlineError: true }`. The global `MutationCache.onError` toasts every mutation error that lacks it (quoted verbatim from `queryClient.ts:15-18`: `onError: (error, _variables, _onMutateResult, mutation) => { if (mutation.meta?.inlineError) return; useToastStore.getState().addToast(error.message || 'An error occurred') }`) [VERIFIED: src/queryClient.ts:14-19].
- `window.open(..., 'noopener')` always returns `null` [CITED: MDN Window.open, via milestone PITFALLS 5], so never branch on its return value.
- Blank-URL behavior is a small product call: today a blank URL opens `about:blank`. Returning early skips both the open and the record. `article.url` is `TEXT NOT NULL` in V1 (`url TEXT NOT NULL`), so blank is an edge case, not a common one [VERIFIED: V1__initial_schema.sql:23].

**API client:** `apiPut` always serializes a body and sets JSON headers. Quoted verbatim from `client.ts:54-58`: `export async function apiPut<T>(path: string, body: unknown): Promise<T> { const res = await fetch(\`${BASE_URL}${path}\`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), })` [VERIFIED: src/api/client.ts:54-62]. For a bodyless PUT, make `body` optional and send the header and body only when one is given, as `apiPost` already does at `client.ts:44-49`. Forget can use the existing `apiDelete(path): Promise<void>` (`client.ts:74-77`).

### Pattern 4: The by-id payload and the Forget control

- **Backend:** `Article` gains `@Transient @JsonInclude(JsonInclude.Include.NON_NULL) private List<EngagementKind> engagement;` appended after `feedback`, following the existing pattern. Quoted verbatim from `Article.java:35-38`: `// The stored thumbs vote; only set on GET /api/articles/{id} and the feedback responses, omitted from JSON elsewhere` / `@Transient` / `@JsonInclude(JsonInclude.Include.NON_NULL)` / `private ArticleFeedback feedback;` [VERIFIED: model/Article.java:29-38]. Set it in `findByIdWithBreakdown` next to `article.setFeedback(...)` (`ArticleService.java:44`). An empty list serializes as `[]`, since `NON_NULL` doesn't drop empty lists, and list rows keep `null`, so the field is omitted. The vote responses reuse `findByIdWithBreakdown` (`ArticleFeedbackService.java:109`), so they carry `engagement` too, which is consistent. Jackson writes the enum as its name, which gives D-05's `string[]`.
- **Routes** on `ArticleController`, beside `/{id}/feedback` (`ArticleController.java:91-101`):
  - `@PutMapping("/{id}/engagement/open") @ResponseStatus(HttpStatus.NO_CONTENT)` calls `articleService.recordOpen(id)`.
  - `@DeleteMapping("/{id}/engagement") @ResponseStatus(HttpStatus.NO_CONTENT)` calls `articleService.forgetEngagement(id)`.
  - Putting both methods on `ArticleService`, which already gets the store, leaves `ArticleController`'s constructor unchanged, so `ArticleControllerTest` needs no new `@MockitoBean`.
  - Each method checks `articleRepository.existsById(id)`, which exists because `ArticleRepository extends ListCrudRepository<Article, Long>` [VERIFIED: ArticleRepository.java:12], and throws `NotFoundException("Article not found: " + id)` (404, D-12).
  - Neither route has a body, so no `consumes` guard is needed. A PUT or DELETE is never a CORS simple request (the existing comment at `ArticleController.java:97` says so for DELETE), and the app has no CORS configuration: a grep for `CrossOrigin|CorsRegistry|allowedOrigins` returns nothing [VERIFIED: grep].
- **Frontend type:** `export type EngagementKind = 'OPEN_ORIGINAL' | 'STAR' | 'BOARD' | 'RAINDROP'`; `Article.engagement?: EngagementKind[]`. Keep it optional so existing fixtures still type-check, following the `feedback?:` precedent at `types/index.ts:40-44`.
- **Control placement (D-01):** the host is `ScoreRow`, rendered at `ReadingPane.tsx:196` (`<ScoreRow article={article} />`) directly under the title. **But** `ScoreRow.tsx:14` returns early (`if (article.interestScore == null) return null`) [VERIFIED: components/ScoreRow.tsx:11-45]. See Pitfall 1: the guard must become "render the row if it is scored **or** engaged", and the badge, chips and "Why N?" render only when scored.
- **Styling:** reuse `className="toolbar-btn"` for the Forget button, as the Why toggle does (`ScoreRow.tsx:35-36`: `className="toolbar-btn why-toggle"`). `.toolbar-btn` is `background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 12px;` and `.why-toggle` overrides it with `font-size: inherit; color: var(--text-secondary);` [VERIFIED: App.css:219-227,524-526]. The label can be a `<span>` with the `chip-sep` separator (`.chip-sep { color: var(--text-muted); }`, App.css:522). Suggested wording (discretion): `Engaged: opened, starred, on a board, saved to Raindrop · Forget`, in enum order. Give the button an accessible name such as `aria-label="Forget engagement"` so tests and screen readers can target it.

### Anti-Patterns to Avoid
- **Adding `@Transactional` to `updateState` or `addArticle`** (suggested by ARCHITECTURE Pattern 1 and PITFALLS 19). It would put the engagement INSERT inside the user's save transaction and break D-07. The lost-update risk it addresses is pre-existing and out of scope (Pitfall 5).
- **Recording saves from the client.** The open endpoint accepts `OPEN_ORIGINAL` only, and saves are server-side.
- **Setting the Priority "Ranking changed" hint or patching Priority rows on engagement.** The ranking doesn't change in Phase 8; that reaction belongs to Phase 10 (LRN-06).
- **Editing `V6__interest_scoring.sql`**, even its header comment "Later phases add no migrations." Flyway checksums the file, and prod would fail validation at startup [ASSUMED]. Fix the wording in CLAUDE.md only.
- **Invalidating `['article', id]` without `exact: true`.** It also refetches `['article', id, 'extracted']` (Pitfall 4).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "Record once per article and kind" | A check-then-insert, or a Java-side dedupe set | PK `(article_id, kind)` + `ON CONFLICT (article_id, kind) DO NOTHING` | Race-safe and idempotent in one statement; the codebase already uses it (`ArticleScoreStore.java:139,175`) |
| Engagement cleanup on article or feed delete | Service-side deletes in `FeedService.delete` | `REFERENCES article(id) ON DELETE CASCADE` | Feed delete is `feedRepository.deleteById(id)` (`FeedService.java:75-76`), which relies on DB cascades throughout |
| Client-side "already opened" tracking | A Zustand set of opened ids | Server idempotency (D-11) | Fewer states; a Forget followed by a later open must record again |
| Toast suppression | A custom error channel | A plain promise with `.catch(() => {})`, or `meta: { inlineError: true }` | The global toast lives in `MutationCache.onError` only |

**Key insight:** every hard property here (idempotency, stickiness, cascade, race-safety) comes from the schema. The Java and TS code only decide *when* to call a single-statement insert.

## Runtime State Inventory

Not a rename or refactor phase, but V7 is new prod schema, so for completeness:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None existing. Two new prod tables are created by Flyway on first v0.3.0 startup; no backfill (locked) | None beyond the migration |
| Live service config | None. No Helm values or env vars change; no new yaml keys | None |
| OS-registered state | None | None |
| Secrets/env vars | None new. `deploy.sh` still needs `MYFEEDER_PG_PASSWORD` + `MYFEEDER_ANTHROPIC_API_KEY` | None |
| Build artifacts | The jar must be rebuilt with `clean` so the new SPA bundle is embedded (CLAUDE.md "Frontend not in image") | `./gradlew clean bootJar` in the release plan |

## Common Pitfalls

### Pitfall 1: The Forget control vanishes on unscored articles
**What goes wrong:** the control goes inside `ScoreRow`, which returns `null` when `interestScore == null`. An engaged but unscored article, the common case because auto-mark-read fires after 1 s and scoring picks up only unread articles, then shows no control, and CAPT-06 fails.
**How to avoid:** change the guard to `if (article.interestScore == null && !(article.engagement?.length)) return null`. Render the badge, chips and Why toggle only when scored, and the engagement label whenever `engagement` is non-empty. Alternatively render a sibling element right after `<ScoreRow>` in `ReadingPane`. Either way, add a test for "unscored + engaged shows the control".
**Existing tests to keep green:**
- `unscoredArticleHasNoScoreRow` (ReadingPane.test.tsx:205-214) expects `.score-row` to be null for an unscored fixture with no engagement. It still passes.
- The exact `textContent` assertion `'82Rust · WebAssembly · Politics− · Why 82? ▸'` (ReadingPane.test.tsx:191) still passes as long as the fixture has no `engagement`.
- The DOM-order tests (`:200-202`, `:278-280`) query `.article-title, .score-row, .article-meta`.

[VERIFIED: components/ScoreRow.tsx:14; ReadingPane.test.tsx:179-214,278-280]

### Pitfall 2: `ReadingPane.test.tsx` and the shortcut tests break on the new modules
**What goes wrong:**
- `ReadingPane.test.tsx` mocks every hook module (`../hooks/useArticles`, `../hooks/useBoards`, `../hooks/useFeedback`) and renders without a `QueryClientProvider` (`renderPane` wraps only `MemoryRouter`). A real `useOpenOriginal` or `useForgetEngagement` calls `useQueryClient()` and throws "No QueryClient set".
- `useKeyboardShortcuts.test.ts` mocks `../api/articles` with an explicit object (`getById, list, updateState, markRead, counts, saveToRaindrop, priority, setFeedback, clearFeedback`). A missing `recordOpen` then gives `undefined is not a function`.

**How to avoid:** add `vi.mock('../hooks/useEngagement', () => ({ useOpenOriginal: () => mockOpenOriginal, useForgetEngagement: () => ({ mutate: mockForget, isPending: false }) }))` to `ReadingPane.test.tsx`, and add `recordOpen: vi.fn()` and `forgetEngagement: vi.fn()` to the articles mock in `useKeyboardShortcuts.test.ts`. The other files that mock `../api/articles` (ArticleList, BoardArticleList, FeedPanel, useFeedback and usePriorityArticles tests) don't call the new functions, so they need no change unless a plan touches them.
[VERIFIED: ReadingPane.test.tsx:1-80; useKeyboardShortcuts.test.ts:1-26]

### Pitfall 3: D-09 taken literally records BOARD for a failed board add
**What goes wrong:** with the insert placed *before* the early return, `addArticle(nonexistentBoardId, articleId)` records BOARD, then `boardArticleRepository.save` fails its FK (`board_id BIGINT NOT NULL REFERENCES board(id)`, V2:22). The add fails, but the engagement row stays.
**How to avoid:** restructure as `if (!exists) save(ba); recordQuietly(articleId, BOARD);`. This still records on a re-add of an article already on a board (D-09's goal) and records nothing when the add throws. Test it with a mocked `boardArticleRepository.save` that throws, and verify `recordQuietly` is never called.
[VERIFIED: BoardService.java:62-69; V2__folders_boards_and_feed_folder.sql:20-26]

### Pitfall 4: Prefix invalidation refetches the reader-view extraction
**What goes wrong:** `qc.invalidateQueries({ queryKey: ['article', id] })` also matches `['article', id, 'extracted']` (`useArticles.ts:32`, which has `staleTime: Infinity`). Every open or save would then refetch the extracted content while reader view is on.
**How to avoid:** use `{ queryKey: ['article', id], exact: true }`, which the vote hook already does (quoted verbatim from `useFeedback.ts:80`: `void qc.invalidateQueries({ queryKey: ['article', v.id], exact: true })`). The by-id key is `['article', id]` (`useArticles.ts:19`: `queryKey: ['article', id],`). Note that `useUpdateArticleState` already invalidates `['article', variables.id]` by prefix (`useArticles.ts:55`). That existing star behavior already refreshes the label, so leave it alone (surgical).
[VERIFIED: hooks/useArticles.ts:17-38,48-61; hooks/useFeedback.ts:80] [CITED: TanStack Query docs, query-invalidation guide, "exact: true", via Context7 /tanstack/query]

### Pitfall 5: The lost-update on the star path is pre-existing, so don't "fix" it with a transaction
**What goes wrong:** `updateState` loads, mutates and saves the whole row, so a concurrent auto-mark-read `{read:true}` PATCH can overwrite a star. Research PITFALLS 19 suggests `@Transactional` or a targeted `UPDATE ... WHERE starred = false`.
**How to avoid:** leave it out of Phase 8 scope. The race predates this phase, the engagement row is sticky anyway, and a `@Transactional` would violate D-07. If the planner wants the targeted UPDATE, it is compatible with D-07: the row count decides STAR, and the insert still runs outside any transaction. It is not required by any CAPT requirement.

### Pitfall 6: Existing `@InjectMocks` tests silently get a null store
**What goes wrong:** `ArticleServiceTest`, `BoardServiceTest` and `RaindropServiceTest` use `@InjectMocks` with constructor injection. Without an `@Mock ArticleEngagementStore`, Mockito passes `null` for it, and the new call throws an NPE outside the `DataAccessException` catch.
**How to avoid:** add `@Mock private ArticleEngagementStore engagementStore;` to all three.
[VERIFIED: ArticleServiceTest.java:26-29; BoardServiceTest.java:22-25; RaindropServiceTest.java:26-30]

### Pitfall 7: Replay keyword check: the real rule is whole words only
The check, quoted verbatim from `InterestCalibrationReplaySqlTest.java:42-43`:
```java
    private static final Pattern WRITE_KEYWORD = Pattern.compile(
            "(?i)\\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\\b");
```
In Java regex, `_` is a word character, so `\b` doesn't split on it. A probe run this session:
```
'COPY_LINK' -> false
e.kind = 'COPY' -> true
copy link -> true
'TOPIC_CREATED' -> false
'DISMISSED' -> false
'OPEN_ORIGINAL' -> false
topic_suggestion_dismissal -> false
article_engagement -> false
COPY-LINK -> true
```
So the CONTEXT example (`COPY_LINK`) would actually pass. What trips the check is a whole word such as a kind or alias named `copy`, `into`, `delete` or `update`, or a hyphenated one. All names proposed here pass. The check only matters from Phase 9 on, when the replay reads these tables; Phase 8 doesn't touch the replay.
[VERIFIED: InterestCalibrationReplaySqlTest.java:42-43,64-76; java Probe.java run this session]

### Pitfall 8: An unhandled promise rejection fails the Vitest run
If the open helper ever lets a rejection escape, Vitest fails the run even when every assertion passes. This project doesn't set `dangerouslyIgnoreUnhandledErrors` (vitest.config.ts has only `environment`, `setupFiles` and `globals`). That is useful: a test with `recordOpen.mockRejectedValue(...)` automatically proves the helper swallows the error.
[CITED: vitest v4.1.6 docs, guide/learn/async.md "Unhandled Rejections", via Context7] [VERIFIED: vitest.config.ts]

## Code Examples

### V7 migration (recommended text; the names are the planner's call)
```sql
-- V7__engagement.sql: the v0.3.0 schema (engagement capture + Phase 11 suggestion dismissal). No backfill.

-- One sticky row per article and kind. Forget deletes the rows; article/feed delete cascades.
CREATE TABLE article_engagement (
    article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('OPEN_ORIGINAL', 'STAR', 'BOARD', 'RAINDROP')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- kept so decay stays possible later
    PRIMARY KEY (article_id, kind)
);

-- D-13: a handled gap suggestion stays handled (D-14); no topic_id.
CREATE TABLE topic_suggestion_dismissal (
    article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (reason IN ('DISMISSED', 'TOPIC_CREATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```
- The style mirrors V6. Quoted verbatim: `article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,` (V6:24,51), `status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED')),` (V6:25), `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` (V6:54) and `PRIMARY KEY (article_id, topic_id)` (V6:43) [VERIFIED: V6__interest_scoring.sql:22-63].
- **Index (discretion): none extra.** The PK `(article_id, kind)` serves by-article lookups, Forget, the cascade and Phase 9's `GROUP BY article_id`. An index on `kind`, with only 4 values, won't help the planner. Adding one later would take a new migration, but none is foreseeable.
- The file name is the planner's call. A `V7__...` prefix is what matters for Flyway ordering, and `V7__engagement.sql` keeps both tables under an honest name.

### Test: `window.open` runs before the request, and a failed request stays silent
```ts
// hooks/useEngagement.test.ts (vi.mock('../api/articles', () => ({ articlesApi: { recordOpen: vi.fn() } })))
it('opens synchronously, then records, and swallows a failure', async () => {
  const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)   // existing pattern: useKeyboardShortcuts.test.ts:125
  vi.mocked(articlesApi.recordOpen).mockRejectedValue(new TypeError('Failed to fetch'))
  const { result } = renderHook(() => useOpenOriginal(), { wrapper })
  act(() => result.current({ id: 7, url: 'https://example.com/7' }))
  expect(openSpy).toHaveBeenCalledWith('https://example.com/7', '_blank', 'noopener')
  expect(articlesApi.recordOpen).not.toHaveBeenCalled()                     // not yet: proves window.open went first
  await waitFor(() => expect(articlesApi.recordOpen).toHaveBeenCalledWith(7))
  expect(useToastStore.getState().toasts).toHaveLength(0)                   // D-12; an escaped rejection would fail the run (Pitfall 8)
  openSpy.mockRestore()
})
```
Add a success case (`mockResolvedValue(undefined)`) that asserts `qc.invalidateQueries` was called with `{ queryKey: ['article', 7], exact: true }` (spy on it) and that `['priority']` was not invalidated.

### Test: the ranking SQL never reads engagement (static guard, in the repository test package)
```java
// InterestScoreQueries' constants are package-private (LEARNED_CTE line 102, blendCte line 314), so read the source like the replay test reads files
assertThat(Files.readString(Path.of("src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java")))
        .doesNotContain("article_engagement").doesNotContain("topic_suggestion_dismissal");
```
[VERIFIED: InterestScoreQueries.java:45,102,314; InterestCalibrationReplaySqlTest.java:32-33 reads repo-relative paths]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Opens are client-only and never reach the server | One helper: synchronous `window.open` + fire-and-forget PUT | This phase | The first client-reported signal; best-effort by design |
| CLAUDE.md: "V6 creates all six interest tables; later milestone phases add no migrations" (line 148) | V7 adds `article_engagement` + `topic_suggestion_dismissal` | This phase | Update CLAUDE.md lines 100 and 148, the Package Structure list, Key Behaviors (capture rules, "bulk paths never record") and the API list [VERIFIED: grep -n CLAUDE.md] |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Editing a shipped migration's comment changes its Flyway checksum and fails prod validation | Anti-Patterns | Low: the recommendation (don't edit V6) is safe either way |
| A2 | Rolling back to the v0.2.1 image after V7 is applied works, because Flyway ignores "future" migrations by default | Security / rollback note | Medium, only if a rollback is ever needed; verify before relying on it |
| A3 | Returning early (no tab, no record) on a blank `url` is acceptable product behavior | Pattern 3 | Low: `url` is `NOT NULL`, so blank is rare; the planner may keep the old always-open behavior and skip only the record |
| A4 | Kind label wording ("opened, starred, on a board, saved to Raindrop") | Pattern 4 | Cosmetic; this is a discretion item |

## Open Questions (RESOLVED)

1. **The D-09 ordering refinement (Pitfall 3).** — RESOLVED: adopted by 08-03 Task 2 and approved by the user at plan-phase on 2026-09-29; 08-CONTEXT.md D-09 now carries the amendment.
   - What we know: literal D-09 (insert before the early return) records BOARD even when the board add then fails its FK.
   - Recommendation: use `if (!exists) save; recordQuietly(BOARD)`, which honors D-09's intent (credit on a re-add). Record it as an interpretation in the plan rather than asking the user again.
2. **Where the Forget control lives for unscored articles (Pitfall 1).** — RESOLVED: adopted by 08-04 Task 1, which widens the `ScoreRow` guard to "scored or engaged".
   - Recommendation: widen `ScoreRow`'s guard so `.score-row` renders when the article is scored or engaged. That keeps D-01's "beside the badge" when scored and a single small line when not.
3. **Prod "ranking unchanged" check.** Priority order drifts on its own as feeds poll. — RESOLVED: adopted by 08-05 in a stricter form. Task 1 takes the pre-deploy snapshot (Priority top 50 plus the top-5 scored breakdowns), Task 3 compares order, badges and Why breakdowns after the rollout, and the read-only `GROUP BY kind` count is the human check after at least a day.
   - Recommendation: before deploy, capture `GET /api/articles/priority?limit=20` (ids and badges) with `/usr/bin/curl http://192.168.44.204/...`. Capture it again right after the rollout, and check that the ids present in both captures have identical badges. Rely on the static guard and the unchanged replay drift test for exactness. Then, a day or more later, check `SELECT kind, count(*) FROM article_engagement GROUP BY kind` via `psql -h pg.bartram.org`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Gradle build/tests | ✓ | OpenJDK 25.0.4 | — |
| Node | Frontend build/tests | ✓ | v26.10.0 | — |
| Docker daemon | `./gradlew test` (Testcontainers), `docker build` | ✗ **not running now** (`failed to connect to the docker API at unix:///Users/scottb/.docker/run/docker.sock`) | — | None: start Docker Desktop before any backend test task or the release |
| kubectl | Deploy verification | ✓ | client v1.37.1 | — |
| helm | `deploy.sh` | ✓ | v4.3.0 | — |
| LAN access (pg.bartram.org, registry, k3s) | Release, deploy, prod checks | Not probed | — | User memory: in Orca sessions, kubectl/helm/psql fail with "no route to host" until Orca is granted Local Network; `/usr/bin/curl` works. The release checkpoint should remind the user |

**Missing dependencies with no fallback:** Docker must be started, or the backend tests and the release plan block.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot test slices + Testcontainers (backend); Vitest 4 + RTL + jsdom (frontend) |
| Config file | `build.gradle.kts` test task; `src/main/frontend/vitest.config.ts` |
| Quick run command | `./gradlew test --tests "org.bartram.myfeeder.repository.ArticleEngagementStoreTest"` (per class) / `cd src/main/frontend && npx vitest run src/hooks/useEngagement.test.ts` |
| Full suite command | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CAPT-01 | The helper opens before recording, records once per call and swallows errors; all three entry points use it; blank URL skipped | unit (Vitest) | `npx vitest run src/hooks/useEngagement.test.ts src/components/ReadingPane.test.tsx src/hooks/useKeyboardShortcuts.test.ts` | ❌ Wave 0 (`useEngagement.test.ts`); others exist, extend |
| CAPT-01 | PUT open → 204; repeat → 204 with one row; unknown id → 404 | integration | `./gradlew test --tests "*EngagementApiIntegrationTest"` | ❌ Wave 0 |
| CAPT-02 | STAR only on false→true; read-only PATCH and already-starred record nothing; store failure doesn't fail the star | unit | `./gradlew test --tests "*ArticleServiceTest"` | ✅ extend |
| CAPT-03 | BOARD on add and on re-add; not when save throws; two boards → one row | unit + integration | `./gradlew test --tests "*BoardServiceTest" --tests "*EngagementApiIntegrationTest"` | ✅ extend / ❌ Wave 0 |
| CAPT-04 | RAINDROP after `createBookmark` returns; none on not-configured, disabled, no-collection or client throw (breaker/fallback) | unit | `./gradlew test --tests "*RaindropServiceTest"` | ✅ extend |
| CAPT-05 | Unstar, board remove and board delete keep rows; feed delete cascades rows (and succeeds) | integration + migration | `./gradlew test --tests "*V7EngagementMigrationTest" --tests "*EngagementApiIntegrationTest"` | ❌ Wave 0 |
| CAPT-06 | By-id carries `engagement` (`[]` when none); DELETE → 204, rows gone, later open records again; control shows only when engaged (including unscored) and calls forget | integration + controller + Vitest | `./gradlew test --tests "*ArticleControllerTest" --tests "*EngagementApiIntegrationTest"`; `npx vitest run src/components/ReadingPane.test.tsx` | ✅ extend / ❌ |
| CAPT-07 | In-body link and Copy Link don't call the helper or the API; auto-mark-read records nothing | Vitest + unit | `npx vitest run src/components/ReadingPane.test.tsx`; `ArticleServiceTest` | ✅ extend |
| Ranking unchanged (SC-5) | `InterestScoreQueries` source never mentions the new tables; badge identical before and after open+star+board; replay drift test still green | static + integration | `./gradlew test --tests "*ArticleEngagementStoreTest" --tests "*EngagementApiIntegrationTest" --tests "*InterestCalibrationReplaySqlTest"` | ❌ / ✅ |
| Release (SC-5) | v0.3.0 runs in prod; rows accumulate | manual (human checkpoint) | `kubectl -n myfeeder rollout status deploy/myfeeder`; `psql -h pg.bartram.org -c "SELECT kind, count(*) FROM article_engagement GROUP BY kind"` | n/a |

### Sampling Rate
- **Per task commit:** the touched class's `--tests` filter, or the touched Vitest files, plus `npx tsc -b` for frontend tasks.
- **Per wave merge:** `./gradlew test` and `npm test`.
- **Phase gate:** full suite green (including `DevProfileConfigTest` and `InterestCalibrationReplaySqlTest`, which are untouched but must stay green) before `/gsd-verify-work`, and before the release checkpoint.

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java`: CHECKs, PKs, cascades (template: `V6InterestScoringMigrationTest`; one expected violation per method, last statement)
- [ ] `src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java`: record idempotency (true then false), kinds order, deleteAll count, `recordQuietly` swallows an FK failure, and the static "ranking SQL doesn't read engagement" guard
- [ ] `src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java`: `@SpringBootTest`, no test transaction; clean up by deleting the feed plus a board-name prefix (boards don't cascade from the feed); `@MockitoBean JevApiClient` with `verify(never())`
- [ ] `src/main/frontend/src/hooks/useEngagement.test.ts`
- [ ] Add `@Mock ArticleEngagementStore` to the three existing `@InjectMocks` service tests (Pitfall 6); add the new mocks to `ReadingPane.test.tsx` and `useKeyboardShortcuts.test.ts` (Pitfall 2)

## Security Domain

`security_enforcement: true`, ASVS level 1.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user LAN app with no auth (unchanged) |
| V3 Session Management | no | — |
| V4 Access Control | no | Single user; no per-user data |
| V5 Input Validation | yes | Path `{id}` bound as `Long` (non-numeric → 400 by Spring); no request body on either new route; the kind is fixed server-side (the open route can only write `OPEN_ORIGINAL`); a DB CHECK constraint backs the enum |
| V6 Cryptography | no | — |
| V7 Error handling and logging | yes | Fixed-text `NotFoundException("Article not found: " + id)` with a numeric id; WARN logs carry ids, kind and exception simple class name only |
| V13 API | yes | PUT/DELETE are never CORS-simple, and there is no CORS config, so a cross-site page can't trigger them; no form or text body is accepted |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-site request faking engagement | Tampering | Non-simple verbs (PUT/DELETE) plus no CORS allowance → the preflight fails; a form can't send PUT |
| Client fabricating save kinds | Tampering / Repudiation | Only `OPEN_ORIGINAL` is client-reportable; saves are captured server-side |
| Log injection via exception messages | Tampering | Log `e.getClass().getSimpleName()` only, never `getMessage()` |
| Row flooding by repeat opens | DoS | PK + `ON CONFLICT DO NOTHING` caps it at 4 rows per article |
| Feed delete blocked by FK | DoS (self) | `ON DELETE CASCADE`, tested |

## Sources

### Primary (HIGH confidence)
- Codebase at `9917477`, read this session:
  - Services and store: `ArticleService.java`, `BoardService.java`, `RaindropService.java`, `RaindropApiClientImpl.java`, `ArticleFeedbackStore.java`, `ArticleFeedbackService.java`.
  - Controllers and model: `ArticleController.java`, `BoardController.java`, `GlobalExceptionHandler.java`, `Article.java`.
  - Migrations: `V1`, `V2`, `V6`.
  - Tests: `InterestCalibrationReplaySqlTest.java`, `V6InterestScoringMigrationTest.java`, `FeedbackApiIntegrationTest.java`, and the setup of `ArticleServiceTest`, `BoardServiceTest` and `RaindropServiceTest`.
  - Frontend: `ReadingPane.tsx`, `ScoreRow.tsx`, `useKeyboardShortcuts.ts`, `useArticles.ts`, `useBoards.ts`, `useFeedback.ts`, `api/client.ts`, `api/articles.ts`, `api/boards.ts`, `queryClient.ts`, `types/index.ts`, `App.css`, `vitest.config.ts`, `ReadingPane.test.tsx`, `useKeyboardShortcuts.test.ts`, `package.json`.
- Java regex probe of `WRITE_KEYWORD` (output pasted in Pitfall 7).
- Milestone research `.planning/research/{SUMMARY,ARCHITECTURE,PITFALLS}.md`; its claims were re-checked against the code, and the corrections are noted above.

### Secondary (MEDIUM confidence, official docs via Context7)
- /tanstack/query: query-invalidation guide (`exact: true` vs prefix matching; `partialMatchKey`)
- /vitest-dev/vitest v4.1.6: unhandled rejections fail the run; `dangerouslyIgnoreUnhandledErrors`, `onUnhandledError`
- MDN Window.open (`noopener` returns null; popups need user activation), as cited in milestone PITFALLS 5

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. No new dependencies; every pattern exists in the repo.
- Architecture: HIGH. Transaction boundaries, entry points and cache keys were read from code.
- Pitfalls: HIGH. Each one is tied to a specific line; the regex claim was probed.

**Research date:** 2026-09-29
**Valid until:** 2026-10-29 (stable; re-check line numbers if ReadingPane or ScoreRow change before planning)
