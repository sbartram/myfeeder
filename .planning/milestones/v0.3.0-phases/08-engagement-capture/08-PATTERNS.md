# Phase 8: Engagement Capture - Pattern Map

**Mapped:** 2026-09-29
**Files analyzed:** 27 (new + modified)
**Analogs found:** 27 / 27 (all analogs are git-tracked source)

Line numbers were read at `298cb26`. RESEARCH.md already quotes most service and hook bodies verbatim; the numbers below point back to those quotes where that saves space.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `src/main/resources/db/migration/V7__engagement.sql` | migration | schema | `V6__interest_scoring.sql` (lines 22-63) | exact |
| `model/EngagementKind.java` | model (enum) | n/a | `model/FeedType.java` / `IntegrationType.java` | exact |
| `model/Article.java` (MOD) | model | DTO field | `Article.java:35-38` (`feedback` @Transient) | exact |
| `repository/ArticleEngagementStore.java` | store | CRUD (idempotent insert) | `repository/ArticleFeedbackStore.java` | exact |
| `service/ArticleService.java` (MOD) | service | request-response | itself, `findByIdWithBreakdown` :35-47, `updateState` :49-63 | exact |
| `service/BoardService.java` (MOD) | service | CRUD | itself :62-69 | exact |
| `integration/RaindropService.java` (MOD) | service | request-response | itself :43-44 | exact |
| `controller/ArticleController.java` (MOD) | controller | request-response | itself `/{id}/feedback` routes :91-101 | exact |
| `test/.../repository/V7EngagementMigrationTest.java` | test | DB | `V6InterestScoringMigrationTest.java` | exact |
| `test/.../repository/ArticleEngagementStoreTest.java` | test | DB | `ArticleFeedbackStoreTest` / V6 migration test | role-match |
| `test/.../controller/EngagementApiIntegrationTest.java` | test | integration | `FeedbackApiIntegrationTest.java` | exact |
| `ArticleServiceTest`, `BoardServiceTest`, `RaindropServiceTest`, `ArticleControllerTest` (MOD) | test | unit | themselves | exact |
| `frontend/src/api/client.ts` (MOD) | utility | request-response | `apiPost` :44-52 (optional body) | exact |
| `frontend/src/api/articles.ts` (MOD) | api client | request-response | `setFeedback`/`clearFeedback` :37-39 | exact |
| `frontend/src/types/index.ts` (MOD) | type | n/a | `Article.feedback?` :40-44 | exact |
| `frontend/src/hooks/useEngagement.ts` (+ test) | hook | fire-and-forget / mutation | `hooks/useFeedback.ts` | role-match |
| `frontend/src/hooks/useArticles.ts`, `useBoards.ts` (MOD) | hook | invalidation | `useFeedback.ts` onError exact invalidation | exact |
| `frontend/src/hooks/useKeyboardShortcuts.ts` (+ test) (MOD) | hook | event | itself `case 'o'` :172-173 | exact |
| `frontend/src/components/ReadingPane.tsx` (+ test) (MOD) | component | event | itself `handleOpenOriginal` :114-116 | exact |
| `frontend/src/components/ScoreRow.tsx` (MOD) | component | render | itself (why-toggle button) | exact |
| `frontend/src/App.css` (MOD) | style | n/a | `.why-toggle` :524-526, `.chip-sep` :522 | exact |
| `CLAUDE.md` (MOD) | docs | n/a | Interest Ranking section | exact |

## Pattern Assignments

### `V7__engagement.sql` (migration)
**Analog:** `src/main/resources/db/migration/V6__interest_scoring.sql`
- `article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,` (V6:24,51)
- `status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED')),` (V6:25)
- `PRIMARY KEY (article_id, topic_id)` (V6:43); `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` (V6:54)
- Full recommended text: RESEARCH.md "V7 migration". Never edit V6 (Flyway checksum).

### `repository/ArticleEngagementStore.java` (store, idempotent CRUD)
**Analog:** `repository/ArticleFeedbackStore.java`

Imports / class shape (lines 1-22):
```java
package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ArticleFeedbackStore {
    private final JdbcClient jdbc;
```
Insert pattern (lines 31-37):
```java
jdbc.sql("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (:articleId, :vote, :narrowed) "
                + "ON CONFLICT (article_id) DO UPDATE SET vote = EXCLUDED.vote, "
                + "topics_narrowed = EXCLUDED.topics_narrowed")
        .param("articleId", articleId)
        .param("vote", vote)
        .param("narrowed", topicIds != null)
        .update();
```
Delete returns count (lines 77-81): `public int delete(long articleId) { return jdbc.sql("DELETE FROM article_feedback WHERE article_id = :articleId")...update(); }`
**Deviations:** use `ON CONFLICT (article_id, kind) DO NOTHING`; add `@Slf4j` and a `recordQuietly` that catches `DataAccessException` and WARNs `kind, articleId, e.getClass().getSimpleName()` only. Do NOT copy the `@Transactional` from `upsert` (D-07). Full shape in RESEARCH.md Pattern 1.

### `model/EngagementKind.java` / `model/Article.java`
- Enum: plain `public enum EngagementKind { OPEN_ORIGINAL, STAR, BOARD, RAINDROP }` like `FeedType`.
- Article field, copy `Article.java:35-38`:
```java
// The stored thumbs vote; only set on GET /api/articles/{id} and the feedback responses, omitted from JSON elsewhere
@Transient
@JsonInclude(JsonInclude.Include.NON_NULL)
private ArticleFeedback feedback;
```
Append `private List<EngagementKind> engagement;` after it with the same two annotations (`org.springframework.data.annotation.Transient`).

### `service/ArticleService.java` (MOD)
**Analog:** itself. By-id attach point (lines 35-47):
```java
public Optional<Article> findByIdWithBreakdown(Long id) {
    return articleRepository.findById(id).map(article -> {
        ...
        article.setFeedback(articleFeedbackStore.find(id).orElse(null));
        return article;
    });
}
```
Add `article.setEngagement(engagementStore.kinds(id));` next to `setFeedback`. Add constructor field `private final ArticleEngagementStore engagementStore;` (Lombok `@RequiredArgsConstructor`, lines 17-23). STAR: compute `newlyStarred = Boolean.TRUE.equals(starred) && !article.isStarred()` before `setStarred`, record after `save` (`updateState` quoted in RESEARCH Pattern 2). New `recordOpen(id)` / `forgetEngagement(id)`: `if (!articleRepository.existsById(id)) throw new NotFoundException("Article not found: " + id);` (same message as `updateState`).

### `service/BoardService.java`, `integration/RaindropService.java` (MOD)
Bodies quoted in RESEARCH Pattern 2. BOARD: `if (!exists) { ...save(ba); } engagementStore.recordQuietly(articleId, BOARD);` (Pitfall 3 ordering). RAINDROP: one line between `createBookmark(...)` (line 43) and `log.info` (line 44). No `@Transactional` anywhere.

### `controller/ArticleController.java` (MOD)
**Analog:** lines 91-101:
```java
/** Removes a thumbs vote. DELETE is never a CORS simple request, so no content-type guard is needed. */
@DeleteMapping("/{id}/feedback")
public FeedbackResult clearFeedback(@PathVariable Long id) {
    return articleFeedbackService.clear(id);
}
```
New routes: `@PutMapping("/{id}/engagement/open") @ResponseStatus(HttpStatus.NO_CONTENT) public void recordOpen(@PathVariable Long id)` and `@DeleteMapping("/{id}/engagement") @ResponseStatus(HttpStatus.NO_CONTENT)`, both delegating to `articleService`. No try/catch (GlobalExceptionHandler maps `NotFoundException` → 404). Constructor unchanged.

### `V7EngagementMigrationTest.java`
**Analog:** `src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java` lines 1-35:
```java
/**
 * ... Postgres aborts the test transaction after a constraint error, so each method
 * triggers at most one expected violation, as its last statement.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V6InterestScoringMigrationTest {
    @Autowired private JdbcTemplate jdbc;
    private long feedId;

    @BeforeEach
    void setUp() {
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/feed.xml", "Test Feed", "RSS");
    }
```
Constraint violation asserted with `assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class)` (import line 10). `ArticleEngagementStoreTest` uses the same harness plus the static "InterestScoreQueries.java never contains article_engagement" guard (RESEARCH Code Examples).

### `EngagementApiIntegrationTest.java`
**Analog:** `controller/FeedbackApiIntegrationTest.java` lines 37-56:
```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FeedbackApiIntegrationTest {
    private static final String FEEDBACK_FEED_URL = "https://example.test/feedback-it-feed.xml";
    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private JevApiClient jevApiClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, score rows, topic-score rows and feedback rows cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", FEEDBACK_FEED_URL);
```
Use a unique feed URL (e.g. `engagement-it-feed.xml`); keep `verify(jevApiClient, never())`. Include the badge-before/after equality assertion.

### Service unit tests (MOD)
Add `@Mock private ArticleEngagementStore engagementStore;` to `ArticleServiceTest` (:26-29), `BoardServiceTest` (:22-25), `RaindropServiceTest` (:26-30) or `@InjectMocks` passes null (Pitfall 6).

### `frontend/src/api/client.ts` + `api/articles.ts`
Make `apiPut` body optional, copying `apiPost` (client.ts:44-52):
```ts
headers: body ? { 'Content-Type': 'application/json' } : {},
body: body ? JSON.stringify(body) : undefined,
```
Add to `articlesApi` next to (articles.ts:37-39):
```ts
setFeedback: (id: number, vote: 1 | -1, topicIds?: number[] | null) =>
  apiPut<FeedbackResult>(`/articles/${id}/feedback`, { vote, topicIds: topicIds ?? null }),
clearFeedback: (id: number) => apiDeleteJson<FeedbackResult>(`/articles/${id}/feedback`),
```
-> `recordOpen: (id) => apiPut<void>(\`/articles/${id}/engagement/open\`)`, `forgetEngagement: (id) => apiDelete(\`/articles/${id}/engagement\`)`.

### `frontend/src/hooks/useEngagement.ts` (NEW)
**Analog:** `hooks/useFeedback.ts` imports (lines 1-4) and its exact by-id invalidation (onError):
```ts
import { useCallback } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
...
void qc.invalidateQueries({ queryKey: ['article', v.id], exact: true })
```
`useOpenOriginal` = window.open first, then `Promise.resolve().then(recordOpen).then(invalidate exact).catch(() => {})` (RESEARCH Pattern 3). `useForgetEngagement` = `useMutation` on `articlesApi.forgetEngagement` with onSuccess exact by-id invalidation. Never invalidate `['priority']` or `['articles']` (D-06). Same exact invalidation added to `useSaveToRaindrop` and `useAddArticleToBoard`/`useReadLater` onSuccess.

### `useKeyboardShortcuts.ts` / `ReadingPane.tsx` (MOD)
Replace bodies of `handleOpenOriginal` (ReadingPane.tsx:114-116) and `case 'o'` (useKeyboardShortcuts.ts:172-173) with the helper; add `openOriginal` to the `useCallback` deps (:221). Leave `handleContentClick` (:133-139) untouched (CAPT-07).

### `components/ScoreRow.tsx` (MOD)
**Analog:** itself, button at lines 35-42:
```tsx
<button
  className="toolbar-btn why-toggle"
  aria-expanded={whyOpen}
  aria-controls="why-breakdown"
  onClick={toggleWhy}
>
```
and separator `<span className="chip-sep"> · </span>` (line 32). Change guard at line 14 (`if (article.interestScore == null) return null`) to also render when `article.engagement?.length`; badge/chips/Why only when scored (Pitfall 1). Forget: `<button className="toolbar-btn why-toggle" aria-label="Forget engagement">`. Update the doc comment ("Renders nothing for an unscored article").

### `ReadingPane.test.tsx` (MOD)
**Analog:** its own hook mocks (lines 12-27):
```tsx
vi.mock('../hooks/useFeedback', () => ({
  useVoteFeedback: () => ({ press: mockPress, narrow: vi.fn() }),
}))
```
Add `vi.mock('../hooks/useEngagement', () => ({ useOpenOriginal: () => mockOpenOriginal, useForgetEngagement: () => ({ mutate: mockForget, isPending: false }) }))`. No QueryClientProvider exists (`renderPane`, lines 64-69). Fixture builder `article(overrides)` (lines 46-62) takes `engagement: [...]`. Keep `.score-row` null test (:205-214) and exact textContent assertion (:191) green. `useKeyboardShortcuts.test.ts` articles mock (:1-26) needs `recordOpen: vi.fn()`, `forgetEngagement: vi.fn()`; `window.open` spy pattern at :125.

## Shared Patterns

- **Errors:** throw `NotFoundException` from services; `GlobalExceptionHandler` maps to 404. Controllers have no try/catch.
- **Best-effort capture:** a single `recordQuietly` in the store; WARN with ids + simple class name only (JevEventLogging style). No `@Transactional` on ArticleService/BoardService/RaindropService or the store (D-07).
- **By-id invalidation:** always `{ queryKey: ['article', id], exact: true }` (useFeedback.ts); prefix invalidation hits `['article', id, 'extracted']`.
- **Toast suppression:** global `MutationCache.onError` in `queryClient.ts:14-19` toasts unless `meta.inlineError`; the open helper is a plain promise with `.catch(() => {})`.
- **DTO rule:** append fields only (`engagement` after `feedback`).

## No Analog Found

None. All files have an in-repo analog.

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{repository,service,integration,controller,model}`, `src/main/resources/db/migration`, `src/test/java/.../{repository,controller}`, `src/main/frontend/src/{api,hooks,components}`
**Files scanned:** ~15
**Pattern extraction date:** 2026-09-29
