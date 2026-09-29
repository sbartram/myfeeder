# Phase 5: Blend & Priority View - Pattern Map

**Mapped:** 2026-09-25
**Files analyzed:** 44 (19 new, 25 modified; backend + frontend + tests)
**Analogs found:** 43 / 44 (1 file has no close analog; `WhyBreakdown` uses a partial one)

All analog paths below are git-tracked source (checked with `git ls-files`). No gitignored mirrors.

## File Classification

### Backend (Java, `src/main/java/org/bartram/myfeeder/`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `repository/InterestScoreQueries.java` (NEW) | repository (JdbcClient store) | read / transform (CTE) | `repository/ArticleScoreStore.java` | exact |
| `service/PriorityService.java` (NEW) | service | request-response (cursor paging) | `service/ArticleService.java#findFiltered` (55-70) | role-match |
| `service/ScoreBreakdowns.java` (NEW) | utility (pure static) | transform | `service/ArticleStateBuilder.java`, `service/InterestQuestions.java` | exact |
| `model/InterestBreakdown.java` (NEW, records) | model (DTO record) | transform | `service/InterestStatus.java`, `ArticleScoreStore` nested records (59-77) | exact |
| `model/Article.java` (MOD) | model (Spring Data JDBC entity) | CRUD | self (add `@Transient` fields) | n/a |
| `service/ArticleService.java` (MOD) | service | CRUD + enrichment | self | n/a |
| `service/BoardService.java` (MOD) | service | CRUD + enrichment | `ArticleService` enrichment (same change) | exact |
| `controller/ArticleController.java` (MOD) | controller | request-response | self `listArticles` (29-43) | exact |
| `config/MyfeederProperties.java` (MOD) | config | n/a | self `Interest` nested class (37-50) | exact |
| `config/SpaForwardController.java` (MOD) | controller (SPA forward) | request-response | self (line 9) | exact |
| `src/main/resources/application.yaml` + `src/test/resources/application.yaml` (MOD, optional) | config | n/a | `myfeeder.interest.*` blocks | exact |

### Backend tests (`src/test/java/org/bartram/myfeeder/`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `repository/InterestScoreQueriesTest.java` (NEW) | test (`@DataJdbcTest` + PG) | read | `repository/ArticleScoreStoreTest.java` | exact |
| `service/ScoreBreakdownsTest.java` (NEW) | test (pure unit) | transform | `service/ArticleStateBuilderTest.java` | exact |
| `service/PriorityServiceTest.java` (NEW) | test (Mockito) | request-response | `service/ArticleServiceTest.java` | exact |
| `config/SpaForwardControllerTest.java` (NEW) | test (`@WebMvcTest`) | request-response | `controller/VersionControllerTest.java` | role-match |
| `controller/ArticleControllerTest.java` (MOD) | test (`@WebMvcTest`) | request-response | self | n/a |
| `service/ArticleServiceTest.java`, `service/BoardServiceTest.java` (MOD) | test (Mockito) | CRUD | self | n/a |

### Frontend (`src/main/frontend/src/`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `hooks/usePriorityArticles.ts` (NEW) | hook (TanStack infinite query) | request-response + cache patch | `hooks/useArticles.ts#useArticles` (6-14) + `useExtractedArticle` (29-37) | exact |
| `hooks/useArticles.ts` (MOD) | hook | mutation / cache | self `useUpdateArticleState` (47-58) | n/a |
| `api/articles.ts` (MOD) | api client | request-response | self `list` (5-14) | exact |
| `types/index.ts` (MOD) | types | n/a | self `Article` (19-33) | n/a |
| `stores/priorityStore.ts` (NEW) | store (non-persisted Zustand) | event-driven (UI state) | `components/Toast.tsx#useToastStore` (1-28) | exact |
| `utils/interest.ts` (MOD) | utility | transform | self `formatSigned` (4-9); moved helpers from `InterestsDialog.tsx` 28, 176-178 | exact |
| `components/PriorityList.tsx` (NEW) | component (list panel) | request-response | `components/ArticleList.tsx` | exact |
| `components/PriorityBanner.tsx` (NEW) | component (status notice) | request-response (polled) | `components/InterestsDialog.tsx#InterestNotices` (143-174) | exact |
| `components/InterestBadge.tsx` (NEW) | component (presentational) | transform | `components/EmptyState.tsx` (shape), `TopicRow.tsx#weightClass` (91-95) | role-match |
| `components/ScoreRow.tsx` (NEW) | component | transform | `components/TopicRow.tsx#TopicPreviewResult` (165-190) | role-match |
| `components/WhyBreakdown.tsx` (NEW) | component | transform | `TopicRow.tsx` preview math line (177-178) | partial (see No Analog) |
| `components/ArticleList.tsx` (MOD) | component | request-response | self row render (167-179) | n/a |
| `components/BoardArticleList.tsx` (MOD) | component | request-response | `ArticleList.tsx` row (same change) | exact |
| `components/ReadingPane.tsx` (MOD) | component | request-response | self header (184-191) | n/a |
| `components/FeedPanel.tsx` (MOD) | component (nav) | event-driven | self `handleStarredClick` (205-209), smart views (229-238); `ReadingPane.tsx` `useMatch` (44-47) | exact |
| `components/EmptyState.tsx` (MOD, optional `detail`) | component | n/a | self | n/a |
| `components/ShortcutOverlay.tsx` (MOD) | component (static) | n/a | self `shortcuts` array (6-25) | exact |
| `components/InterestsDialog.tsx` (MOD) | component | n/a | import the moved helpers | n/a |
| `hooks/useInterest.ts` (MOD) | hook | mutation | self (`onSuccess` blocks 38-41, 57-60, 74-78, 99-102, 128-130) | n/a |
| `hooks/useKeyboardShortcuts.ts` (MOD) | hook | event-driven | self g-chord (59-68), `j` (86-92), `r`/`A` (149-156) | n/a |
| `App.tsx` (MOD) | layout / routing | event-driven | self `MainLayout` (73-122) | n/a |
| `App.css` (MOD) | styles | n/a | `.interests-notice*` (480-493), `.article-item*` (244-261), `.weight-*` (589-590) | exact |

### Frontend tests

| New/Modified File | Role | Closest Analog | Match Quality |
|-------------------|------|----------------|---------------|
| `hooks/usePriorityArticles.test.ts` (NEW) | hook test (real QueryClient) | `hooks/useOpml.test.ts` (`createWrapper`, 24-31) + `hooks/useKeyboardShortcuts.test.ts` (API mock 3-12) | exact |
| `components/PriorityList.test.tsx` (NEW) | RTL component | `components/ArticleList.test.tsx` | exact |
| `components/PriorityBanner.test.tsx` (NEW) | RTL component | `components/ArticleList.test.tsx` (hook mock) | role-match |
| `components/InterestBadge.test.tsx`, `WhyBreakdown.test.tsx` (NEW) | RTL component (pure props) | `components/ArticleList.test.tsx` render helpers | role-match |
| `utils/interest.test.ts` (MOD) | pure unit | self | exact |
| `hooks/useKeyboardShortcuts.test.ts`, `components/ReadingPane.test.tsx`, `ArticleList.test.tsx`, `FeedPanel.test.tsx`, `AppShell.test.tsx`, `TopicRow.test.tsx` (MOD) | mocks/fixtures | self | n/a |

---

## Pattern Assignments

### `repository/InterestScoreQueries.java` (repository, read/transform)

**Analog:** `src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java`

**Imports + class shape** (lines 1-31, 57):
```java
package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ...javadoc naming the single source of truth (D-02, D-12 style)... */
@Repository
@RequiredArgsConstructor
public class ArticleScoreStore {
    ...
    private final JdbcClient jdbc;
```
Copy `@Repository @RequiredArgsConstructor` + `private final JdbcClient jdbc;`. Also inject `MyfeederProperties` for `:profilePoints` (or pass the value in from the service).

**SQL-as-constant-fragments pattern** (lines 36-55): package-private `static final String` fragments composed by `+`, each with a javadoc naming its table alias:
```java
/** The one eligibility predicate (SCOR-04): unread and inside the window. Alias {@code a} = article. */
static final String ELIGIBLE =
        "a.\"read\" = false AND COALESCE(a.published_at, a.fetched_at) > :cutoff";

/** Newest first, with the id as a stable tiebreak for equal timestamps. */
static final String NEWEST_FIRST = "ORDER BY COALESCE(a.published_at, a.fetched_at) DESC, a.id DESC";

private static final String NEEDING_SCORING_FROM =
        "FROM article a LEFT JOIN article_score s ON s.article_id = a.id WHERE "
                + ELIGIBLE + " AND " + NEEDS_SCORING;
```
Apply: one `BLEND_CTE` template (`learned` → `eff` → `contrib` → `blended`, RESEARCH Pattern 1) with the scope as a **constant** fragment (`PRIORITY_SCOPE`, `PRIORITY_CURSOR_SCOPE`, `IDS_SCOPE`, `ARTICLE_SCOPE`); never splice request data. A Java text block is fine; the "no user input in SQL" rule is what matters.

**Nested result records** (lines 59-77): put small result types as public nested records with a one-line javadoc, e.g. `public record ScoreCounts(long eligibleUnscored, long failed) {}`. Use that for the breakdown's raw rows (`BreakdownInputs`, `TopicContribution`).

**Query with list result** (lines 80-86):
```java
return jdbc.sql("SELECT a.id " + NEEDING_SCORING_FROM + " " + NEWEST_FIRST + " LIMIT :limit")
        .param("cutoff", Timestamp.from(cutoff))
        .param("limit", limit)
        .query(Long.class)
        .list();
```

**Optional single row with a custom mapper** (lines 92-102): `.query((rs, rowNum) -> new Candidate(mapArticle(rs), rs.getString("feed_title"))).optional();`. Use this for `breakdown(articleId)`.

**Empty-collection guard for `IN (:ids)`** (lines 186-195), which is the enrichment `displayScores(ids)` template:
```java
public List<Long> filterNeedingScoring(Collection<Long> ids, Instant cutoff) {
    if (ids.isEmpty()) {
        return List.of();
    }
    return jdbc.sql("SELECT a.id " + NEEDING_SCORING_FROM + " AND a.id IN (:ids) " + NEWEST_FIRST)
            .param("cutoff", Timestamp.from(cutoff))
            .param("ids", ids)
            .query(Long.class)
            .list();
}
```
`displayScores` returns `Map<Long,Integer>`: `if (ids.isEmpty()) return Map.of();`, then collect rows `(id, interest_score)`. Only put non-null scores in the map (use a `HashMap`, because `Map.of`/`toMap` reject null values).

**Single-row aggregate** (lines 201-209): `.query((rs, rowNum) -> new ScoreCounts(rs.getLong(...), ...)).single();`

**Article row mapper** (lines 238-254). Copy the hand-mapper style or use `BeanPropertyRowMapper<>(Article.class)` (RESEARCH "Don't Hand-Roll"). If you hand-map, add `url`, `author`, `image_url`, `read`, `starred` and `interest_score` (nullable: `rs.getObject("interest_score", Integer.class)`). Note: the existing `mapArticle` hardcodes `setRead(false)`. Do NOT copy that, because Priority rows can include a read cursor row and the Priority rows need the real value.
```java
private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
}
```

**Two statements rather than a nullable cursor** (RESEARCH Pitfall 5): mirror `ArticleRepository.java` 41-45 (`findFiltered` / `findFilteredBefore`), so `priorityFirstPage(limit)` and `priorityPageAfter(cursorId, limit)`.

**Mandatory SQL guards from RESEARCH:** `CASE WHEN raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(raw_n)))::int END` (Pitfall 1); `raw_n` stays `numeric`, and only `sort_score` is cast to `float8` with `'-Infinity'` (Pitfall 2); explicit column list with no `a.*` and no `extracted_content`.

---

### `service/PriorityService.java` (service, request-response)

**Analog:** `src/main/java/org/bartram/myfeeder/service/ArticleService.java` (1-19, 55-70) and `InterestStatusService.java` (16-34) for the constructor style.

**Class shape** (ArticleService 1-19):
```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleService {
    private final ArticleRepository articleRepository;
```

**Cursor pattern to adapt** (ArticleService 55-65):
```java
public List<Article> findFiltered(Long feedId, Boolean read, Boolean starred, Long cursor, int limit, boolean ascending) {
    if (cursor != null) {
        Article cursorArticle = articleRepository.findById(cursor)
                .orElseThrow(() -> new IllegalArgumentException("Cursor article not found: " + cursor));
        ...
        return articleRepository.findFilteredBefore(feedId, read, starred, cursorDate, cursor, limit);
    }
    ...
    return articleRepository.findFiltered(feedId, read, starred, limit);
}
```
**Deviation (locked by RESEARCH Pattern 2):** check `articleRepository.existsById(cursor)` and throw `NotFoundException` (404), **not** `IllegalArgumentException` (400), so the client can tell "restart from page 1" apart. Exception style: `new NotFoundException("Article not found: " + id)` (ArticleService 27). The cursor tuple is resolved inside SQL, so the service does not load the cursor date itself.

---

### `service/ScoreBreakdowns.java` (utility, pure transform)

**Analog:** `src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java` (1-40) and `InterestQuestions.java` (14-48).

**Pure-builder shell** (ArticleStateBuilder 9-28):
```java
/**
 * Builds the Jev article state. Pure: no Spring, no I/O.
 * <p>... Shared verbatim by the topic preview and the Phase 4 scorer.
 */
public final class ArticleStateBuilder {

    public static final int MAX_SUMMARY_CHARS = 1500;
    static final int MAX_RAW_HTML_CHARS = 50_000;

    private ArticleStateBuilder() {
    }

    public static Map<String, Object> build(String feedTitle, Article article) { ... }

    static String truncate(String text, int max) { ... }   // package-private helper, unit-tested directly
```
Apply: `public final class ScoreBreakdowns`, private ctor, `static long[] apportion(BigDecimal[] exact, long target)` (package-private, tested directly), a public `build(...)` that returns `InterestBreakdown`, and `static int levelIndex(double profileScore, int profileMaxLevel)`.

**Level constants source** (InterestQuestions 28-35): `PROFILE_LEVELS` is **package-private** and `PROFILE_MAX_LEVEL` is `public static final`. `ScoreBreakdowns` lives in the same `service` package, so it can read both without any visibility change. Index formula (RESEARCH Pattern 1): `Math.round(profile_score / profile_max_level × InterestQuestions.PROFILE_MAX_LEVEL)`, clamped to `[0, PROFILE_MAX_LEVEL]`.

**Validation style:** throw `IllegalArgumentException` with a fixed message for impossible inputs (InterestQuestions 61-64). Keep this minimal; CLAUDE.md says no error handling for impossible scenarios.

---

### `model/InterestBreakdown.java` (model, records)

**Analog:** `service/InterestStatus.java` (javadoc'd record listing the field semantics) and `service/TopicPreviewResponse.java`:
```java
/**
 * The topic preview result (D-13): the raw noul in [0, 1] for the previewed topic and the Jev
 * model id. It carries no computed points; the client applies the R6 hinge and the draft weight.
 */
public record TopicPreviewResponse(Double noul, String model) {}
```
Apply: `public record InterestBreakdown(BigDecimal raw, int total, int display, ProfileRow profile, List<TopicRow> topics, List<NonMatchingTopic> nonMatching)` with nested records. The payload shape is in RESEARCH Pattern 4. Put it in `model/` per RESEARCH's structure (or in `service/` next to `InterestStatus`; either follows precedent). Jackson annotations come from `com.fasterxml.jackson.annotation.*`.

---

### `model/Article.java` (MODIFY)

**Current file** (1-26): Lombok `@Data`, `@Table("article")`, imports `org.springframework.data.annotation.Id`. Add:
```java
import org.springframework.data.annotation.Transient;          // NOT java.beans.Transient (Pitfall 3)
import com.fasterxml.jackson.annotation.JsonInclude;          // Jackson 3 annotations stay in com.fasterxml

@Transient
private Integer interestScore;                // null = unscored; always serialized (as null)

@Transient
@JsonInclude(JsonInclude.Include.NON_NULL)
private InterestBreakdown interestBreakdown;  // only on GET /api/articles/{id}
```

---

### `service/ArticleService.java` and `service/BoardService.java` (MODIFY: enrichment)

**Analog:** self. Add `private final InterestScoreQueries interestScoreQueries;` to the `@RequiredArgsConstructor` field list (ArticleService line 19; BoardService lines 16-17).

Enrichment points:
- `ArticleService.findById` (21-23), `updateState` (25-37, enrich the saved result **after** `articleRepository.save`), and `findFiltered` (55-70, wrap every return path).
- `BoardService.findArticles` (45-48).
- Add a new `findByIdWithBreakdown(Long id)` used only by `ArticleController.getArticle`. `RaindropService` also calls `findById`, so the breakdown stays off that path (RESEARCH Pattern 3).

Suggested private helper, identical in both services (or a small shared static in `InterestScoreQueries`):
```java
private List<Article> withScores(List<Article> articles) {
    Map<Long, Integer> scores = interestScoreQueries.displayScores(articles.stream().map(Article::getId).toList());
    articles.forEach(a -> a.setInterestScore(scores.get(a.getId())));
    return articles;
}
```

---

### `controller/ArticleController.java` (MODIFY)

**Analog:** self `listArticles` (29-43):
```java
@GetMapping
public PaginatedResponse<Article> listArticles(
        ...
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Long before,
        ...) {
    // Clamp to [1, MAX_LIMIT] so an out-of-range limit is well-defined rather than an error,
    // and so limit + 1 (the pagination look-ahead) can never overflow.
    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    List<Article> fetched = articleService.findFiltered(feedId, read, starred, before, safeLimit + 1, ascending);
    return PaginatedResponse.of(fetched, safeLimit, Article::getId);
}
```
New `@GetMapping("/priority")` with `limit` + `before` params, the same clamp (`MAX_LIMIT` line 23), `priorityService.page(before, safeLimit + 1)`, and `PaginatedResponse.of(...)` (unchanged, `PaginatedResponse.java` 12-17). Add `private final PriorityService priorityService;` to the fields (25-27). `getArticle` (50-55) switches to `articleService.findByIdWithBreakdown(id)` and keeps its `.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build())` shape. No try/catch: `NotFoundException` goes to 404 through `GlobalExceptionHandler` (43-48).

Note: `BoardController.listBoardArticles` (40-46) does not clamp `limit`. Leave it unchanged (surgical rule); enrichment happens in `BoardService`.

---

### `config/MyfeederProperties.java` (MODIFY)

**Analog:** self, nested `@Data public static class Interest` (37-50). Add a nested `Blend` inside `Interest`:
```java
@Data
public static class Interest {
    private int windowDays = 14;
    ...
    private Blend blend = new Blend();

    @Data
    public static class Blend {
        /** Points for a full profile match (R1): p × profilePoints. */
        private int profilePoints = 100;
    }
}
```
**Yaml rule (RESEARCH Pitfall 7, `DevProfileConfigTest` 86-91):** if `myfeeder.interest.blend.profile-points` is added to `src/main/resources/application.yaml` (the `interest:` block at lines 33-40), add the **identical** key to `src/test/resources/application.yaml` (lines 31-39). The other option is to keep the default only in the Java class and not list it in the yaml.

---

### `config/SpaForwardController.java` (MODIFY)

Line 9. Add `"/priority"` to the array:
```java
@GetMapping(value = {"/", "/feed/**", "/folder/**", "/starred", "/boards", "/board/**", "/settings"})
```

---

### `repository/InterestScoreQueriesTest.java` (test, NEW)

**Analog:** `src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java`

**Header + setup** (lines 26-53):
```java
@DataJdbcTest
@Import({TestcontainersConfiguration.class, ArticleScoreStore.class})
class ArticleScoreStoreTest {

    @Autowired private ArticleScoreStore store;
    @Autowired private JdbcTemplate jdbc;
    ...
    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so counts are exact.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/feed.xml", "Test Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
```
Imports: `org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest` (Boot 4 package). If the class injects `MyfeederProperties`, add `@EnableConfigurationProperties(MyfeederProperties.class)` (precedent: `IntegrationConfigControllerTest.java` line 33).

**Seed helpers to copy** (lines 343-370): `insertArticle(guid, read, publishedAt, fetchedAt)`, `insertTopic(name)` (extend with a `weight` argument: `INSERT INTO interest_topic (name, description, weight)`), `insertScore(articleId, status, attempts)`. Add `insertScored(articleId, profileScore, maxLevel)` plus `insertTopicScore(articleId, topicId, noul)` via raw `jdbc.update`. Use the RESEARCH fixture (Code Examples, lines 500): expected order `3 10 2 1 8 6 9 5 4 | 19 11 20 18 15 14 17 16 12`.

---

### `service/ScoreBreakdownsTest.java` (test, NEW)

**Analog:** `src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java` (1-40): plain JUnit 5, no Spring, no Mockito, `static` fixture helpers, AssertJ `assertThat`. Cases: the UI mock (64/17/7/−6 → 82), capped (133 → rows 100/19/14), an x.5 tie, negative-remainder rows, and the level index at max = 4 and at a different max.

---

### `service/PriorityServiceTest.java` (test, NEW)

**Analog:** `ArticleServiceTest.java` (1-24):
```java
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {
    @Mock private ArticleRepository articleRepository;
    @InjectMocks private ArticleService articleService;
```
Test: a missing cursor gives `assertThatThrownBy(...).isInstanceOf(NotFoundException.class)`, no cursor calls the first-page query, and a present cursor calls the after-cursor query.

**Existing tests to update (Pitfall / RESEARCH A3):** `ArticleServiceTest` line 23 and `BoardServiceTest` lines 18-20 need `@Mock private InterestScoreQueries interestScoreQueries;`. Otherwise the new constructor argument arrives as null and enrichment NPEs. With `MockitoExtension` strict stubs, stub `displayScores(any())` only in tests that reach it (an unused stub fails the test with `UnnecessaryStubbingException`). An unstubbed mock returns an empty map, which is already safe.

---

### `config/SpaForwardControllerTest.java` (test, NEW)

**Analog:** `controller/VersionControllerTest.java` (1-40):
```java
@WebMvcTest(VersionController.class)
class VersionControllerTest {
    @Autowired private MockMvc mockMvc;
    @Test
    void ... throws Exception {
        mockMvc.perform(get("/api/version")).andExpect(status().isOk())...
```
Apply: `@WebMvcTest(SpaForwardController.class)`, `mockMvc.perform(get("/priority")).andExpect(forwardedUrl("/index.html"))`.

### `controller/ArticleControllerTest.java` (MODIFY)

Lines 28-32: add `@MockitoBean private PriorityService priorityService;` next to the existing `@MockitoBean`s. Copy `shouldListArticles` / `capsLimitAtServerMaximum` (63-80+) for `GET /api/articles/priority`: routing hits the Priority handler, not `/{id}` (assumption A2); limit clamp; `NotFoundException` gives 404 (pattern at 45-52); JSON contains `interestScore`.

---

### `hooks/usePriorityArticles.ts` (hook, NEW)

**Analog:** `src/main/frontend/src/hooks/useArticles.ts`

**Infinite-query shape** (lines 1-14):
```ts
import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import type { ArticleFilters } from '../types'

export function useArticles(filters: ArticleFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['articles', filters],
    queryFn: ({ pageParam }) => articlesApi.list(filters, 50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor !== null ? lastPage.nextCursor : undefined,
  })
}
```
**Cache-policy override precedent** (lines 29-37, `useExtractedArticle`): `staleTime: Infinity, retry: false`, with a doc comment explaining why. Apply `staleTime: Infinity, refetchOnWindowFocus: false, refetchOnReconnect: false` and a doc comment citing D-07/D-09. The global default is `staleTime: 30_000` (`queryClient.ts` line 12), so this override is mandatory.

Key `['priority']`, outside the `['articles']` prefix. Export `PRIORITY_KEY`, the `rows` memo (dedupe by id, first occurrence wins) and `patchPriorityArticle(qc, id, patch)` using `qc.setQueriesData`. Full sketch in RESEARCH Pattern 5.

### `hooks/useArticles.ts` (MODIFY)

`useUpdateArticleState.onSuccess` (52-56):
```ts
onSuccess: (_data, variables) => {
  qc.invalidateQueries({ queryKey: ['articles'] })
  qc.invalidateQueries({ queryKey: ['article', variables.id] })
  qc.invalidateQueries({ queryKey: ['unreadCounts'] })
},
```
Add `patchPriorityArticle(qc, variables.id, { ...variables.state })` (read/starred only, never `interestScore`). Never invalidate `['priority']`. `useMarkRead` (60-70) has no Priority path (Mark all read is hidden), so leave it alone.

**Circular-import caution:** if `patchPriorityArticle` lives in `usePriorityArticles.ts` and that file imports `articlesApi`, no cycle arises. Don't make `usePriorityArticles.ts` import from `useArticles.ts`.

### `api/articles.ts` (MODIFY)

**Analog:** `list` (5-14):
```ts
priority: (limit = 50, before?: number) => {
  const params = new URLSearchParams()
  params.set('limit', String(limit))
  if (before != null) params.set('before', String(before))
  return apiGet<PaginatedArticles>(`/articles/priority?${params}`)
},
```
**Pitfall 8:** `useKeyboardShortcuts.test.ts` lines 3-12 fully mock `articlesApi` (`getById, list, updateState, markRead, counts, saveToRaindrop`). Add `priority: vi.fn()` there.

### `types/index.ts` (MODIFY)

`Article` (19-33): add `interestScore: number | null` and `interestBreakdown?: InterestBreakdown`. Add an `InterestBreakdown` interface that mirrors the Java record. Because tests are type-checked (`npx tsc -b`), a **required** `interestScore` forces fixture updates in `useKeyboardShortcuts.test.ts` (`article()` helper near line 55), `ReadingPane.test.tsx` (`article()` at ~40), `InterestsDialog.test.tsx` (`article()` 47-62) and `TopicRow.test.tsx`. Making it optional (`interestScore?: number | null`) avoids that churn. Planner's call.

---

### `stores/priorityStore.ts` (store, NEW)

**Analog:** `src/main/frontend/src/components/Toast.tsx` lines 1-28 (a non-persisted Zustand store, no `persist` middleware):
```ts
import { create } from 'zustand'

interface ToastState {
  toasts: ToastItem[]
  addToast: (message: string, type?: 'error' | 'success') => void
  removeToast: (id: number) => void
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  addToast: (message, type = 'error') => { ... set((state) => ({ ... })) ... },
  removeToast: (id) => set((state) => ({ ... })),
}))
```
Apply: `usePriorityStore` with `whyOpen`, `toggleWhy()`, `rankingChanged`, `setRankingChanged(b)`, and optionally `baselineUnscored`. Keep it **out of `uiStore`**: `uiStore` (`stores/uiStore.ts` 27-74) is `persist`ed and fully mocked in 4 test files (Pitfall 8). Non-React callers (mutation `onSuccess`) use `usePriorityStore.getState().setRankingChanged(true)`, the same way `useArticles.ts` line 76 calls `useToastStore.getState().addToast(...)`.

---

### `utils/interest.ts` (MODIFY)

**Analog:** self. Pure exported functions with a one-line JSDoc each (lines 1-9):
```ts
/**
 * A number with an explicit sign: "+20", "−15" (U+2212 minus) or "0", with `digits` decimals.
 */
export function formatSigned(n: number, digits = 0): string { ... }
```
Move here from `components/InterestsDialog.tsx`:
- line 28: `const OPEN_BREAKER_STATES = ['OPEN', 'FORCED_OPEN']` → `export const OPEN_BREAKER_STATES`
- lines 175-178: `/** "1 article" or "N articles". */ function articles(count: number)` → export. For the UI-SPEC thousands separator, use `count.toLocaleString('en-US')` (precedent: `InterestsDialog.tsx` line 343).

Add `tierOf(score: number): 'high' | 'neutral' | 'low'` (≥70 / ≥40 / else) and a `PROFILE_LEVEL_LABELS` array (`None / In passing / Partly / Mainly / Core interest`). Reuse `formatSigned` for breakdown points (it already emits U+2212). Do **not** use `hinge()` for display values (RESEARCH anti-pattern: the client never recomputes).

Then update `InterestsDialog.tsx` to import `OPEN_BREAKER_STATES` and `articles` from `../utils/interest` (import line 13 already pulls `isTopicDirty` from there).

---

### `components/PriorityList.tsx` (component, NEW)

**Analog:** `src/main/frontend/src/components/ArticleList.tsx`

**Imports + store/preference reads** (1-8, 17-25):
```tsx
import { useMemo, useState, useEffect, useRef } from 'react'
import { useUIStore } from '../stores/uiStore'
import { usePreferences, ARTICLE_LIST_FONT_PX } from '../stores/preferencesStore'
import { EmptyState } from './EmptyState'
import type { Article } from '../types'
...
const selectedArticleId = useUIStore((s) => s.selectedArticleId)
const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
const searchQuery = useUIStore((s) => s.searchQuery)
const setSearchQuery = useUIStore((s) => s.setSearchQuery)
const articleListFontSize = usePreferences((s) => s.articleListFontSize)
const articleItemsStyle = { fontSize: `${ARTICLE_LIST_FONT_PX[articleListFontSize]}px` }
```
**Client filter** (58-67): the same title/summary `toLowerCase().includes(q)` filter. Do **not** copy the `preserved` reinsert logic (47-56, 68-74). Priority keeps read rows through the cache patch instead (D-07).

**Toolbar + search + rows + Load more** (131-186):
```tsx
<div className="article-list">
  <div className="article-list-toolbar">
    <span className="toolbar-title">{title}</span>
    <div className="toolbar-actions"> ...buttons... </div>
  </div>
  <input className="search-input" type="text" placeholder="Filter articles..."
         value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} />
  <div className="article-items" style={articleItemsStyle}>
    {filtered.map((article) => (
      <div key={article.id}
           className={`article-item ${selectedArticleId === article.id ? 'selected' : ''} ${article.read ? 'read' : ''}`}
           onClick={() => handleArticleClick(article)}>
        <div className="article-item-title">{article.title}</div>
        <div className="article-item-meta">
          {formatTime(article.publishedAt)}
          {article.starred && ' starred'}
        </div>
      </div>
    ))}
    {hasNextPage && (
      <button className="load-more" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
        {isFetchingNextPage ? 'Loading...' : 'Load more'}
      </button>
    )}
  </div>
</div>
```
Deltas: the toolbar's `.toolbar-actions` holds only the `.toolbar-btn.priority-refresh` button (no Mark-all-read split group, D-16); `<PriorityBanner>` goes between the toolbar and the search input; the title line is wrapped in `.article-item-head` with `<InterestBadge>` or an empty `.interest-badge-slot`; a `.priority-separator` goes before the first `interestScore === null` row; and the Load-more error label comes from UI-SPEC. The `formatTime` helper (100-107) is duplicated verbatim in `BoardArticleList.tsx` (36-43), so copy it the same way or extract it. The empty state is **not** an early return: unlike `ArticleList` (109-129), the toolbar with Refresh and the banner must still render (UI-SPEC E1). Take rows from `usePriorityArticles()` (shared with `MainLayout`).

---

### `components/PriorityBanner.tsx` (component, NEW)

**Analog:** `components/InterestsDialog.tsx#InterestNotices` (lines 143-174):
```tsx
function InterestNotices({ status }: { status: InterestStatus | undefined }) {
  if (!status) return null
  const notConfigured = status.configured === false
  const paused = OPEN_BREAKER_STATES.includes(status.breakerState)
  const coldStart = status.coldStart === true
  if (!notConfigured && !paused && !coldStart) return null

  return (
    <div className="interests-notices">
      {notConfigured && (
        <div className="interests-notice">
          <strong>Scoring isn't set up yet.</strong> No TypeSafe API key is configured, ...
        </div>
      )}
      ...
      {coldStart && (
        <div className="interests-notice cold-start">
          <strong>Start here.</strong> Nothing is ranked until you write a profile or add at least one topic.
        </div>
      )}
    </div>
  )
}
```
Deltas: show **one** banner by precedence (not configured > cold start > paused > waiting; D-14) rather than stacking several. Class `.priority-banner` (+ `.cold-start`) with `role="status"`. Cold start adds `<button className="btn-primary" onClick={onSetUpInterests}>Set up interests</button>`. Data comes from `useInterestStatus()` (`hooks/useInterest.ts` 16-26); a loading or errored status gives `return null`, the same as `if (!status) return null`. Copy the exact strings from UI-SPEC § Status banner.

---

### `components/InterestBadge.tsx` (component, NEW)

**Analog (shape):** `components/EmptyState.tsx` (1-17): a tiny props interface plus a named export function component. **Analog (class-by-value helper):** `TopicRow.tsx` 91-95:
```ts
function weightClass(weight: number): string {
  if (weight > 0) return 'weight-positive'
  if (weight < 0) return 'weight-negative'
  return 'weight-zero'
}
```
Apply: `export function InterestBadge({ score }: { score: number | null })`, which returns `null` when `score == null` and otherwise `<span className={`interest-badge tier-${tierOf(score)}`} title={`Interest score ${score} of 100`} aria-label={`Interest score ${score}`}>{score}</span>`.

---

### `components/ScoreRow.tsx` (component, NEW)

**Analog:** `components/TopicRow.tsx#TopicPreviewResult` (165-190), which renders the sign-colored points with the shared helpers:
```tsx
{`Match ${Math.round(result.noul * 100)}% → counts ${Math.round(m * 100)}% × ${formatSigned(weight)} = `}
<span className={`interests-preview-points ${weightClass(points)}`}>{formatSigned(points, 1)}</span>
```
Apply: chips are `<span className={`topic-chip ${points > 0 ? 'weight-positive' : 'weight-negative'}`} title={name}>{name}{points < 0 ? '−' : ''}</span>`, taken from `article.interestBreakdown.topics` (already server-ordered) and joined by ` · `. The Why toggle is `<button className="toolbar-btn why-toggle" aria-expanded={whyOpen} aria-controls="why-breakdown">`. `whyOpen`/`toggleWhy` come from `usePriorityStore`. All values come from the server; the client only formats them.

### `components/WhyBreakdown.tsx` (component, NEW)

See No Analog. Use `formatSigned` (utils/interest.ts 4-9) for the points, `PROFILE_LEVEL_LABELS[levelIndex]` for the profile row, and a local `useState` for the non-matching footer that resets on `article.id` change. Reset precedent: `ReadingPane.tsx` 34-36:
```tsx
useEffect(() => {
  setReaderViewChoice(null)
}, [article?.id])
```

---

### `components/ArticleList.tsx` / `components/BoardArticleList.tsx` (MODIFY: badge slot)

Row render: ArticleList 167-179, BoardArticleList 73-85 (identical markup). Replace `<div className="article-item-title">{article.title}</div>` with:
```tsx
<div className="article-item-head">
  {article.interestScore != null
    ? <InterestBadge score={article.interestScore} />
    : reserveSlot && <span className="interest-badge-slot" aria-hidden="true" />}
  <div className="article-item-title">{article.title}</div>
</div>
```
with `const reserveSlot = useMemo(() => allArticles.some((a) => a.interestScore != null), [allArticles])` (UI-SPEC: reserve the slot only when some loaded row is scored). Note that `BoardArticleList` (72) has no `articleItemsStyle`. Don't add one (surgical); the `em` sizes still work from the 13px base.

---

### `components/ReadingPane.tsx` (MODIFY)

Insert between lines 184 and 185:
```tsx
<h1 className="article-title">{article.title}</h1>
{article.interestScore != null && (
  <>
    <ScoreRow article={article} />
    {whyOpen && article.interestBreakdown && <WhyBreakdown breakdown={article.interestBreakdown} />}
  </>
)}
<div className="article-meta">
```
Auto-mark-read (63-71) already goes through `useUpdateArticleState`, so the Priority cache patch covers it with no change here. **Pitfall 8:** `ReadingPane.test.tsx` mocks `../stores/uiStore` (25-32) and `../hooks/useArticles` (10-16) as full replacements. A new `usePriorityStore` import is a new module, so it doesn't break those mocks, but fixtures with no `interestScore` must still render (treat `undefined` like `null`).

---

### `components/FeedPanel.tsx` (MODIFY)

**Click handler analog** (205-209):
```tsx
const handleStarredClick = () => {
  setSelectedFeed(null)
  setSelectedFolder(null)
  navigate('/starred')
}
```
**Smart views** (229-238):
```tsx
<div className="smart-views">
  <div className={`smart-view ${!selectedFeedId && !selectedFolderId ? 'active' : ''}`}
       onClick={handleAllClick}>
    <span>All Articles</span>
    {totalUnread > 0 && <span className="count">{totalUnread}</span>}
  </div>
  <div className="smart-view" onClick={handleStarredClick}>
    <span>Starred</span>
  </div>
</div>
```
**`useMatch` precedent:** `ReadingPane.tsx` 2 and 44 (`import { useMatch } from 'react-router-dom'`; `const boardRouteMatch = useMatch('/board/:boardId')`). Add `const priorityMatch = useMatch('/priority')`, insert the Priority `smart-view` **first** (text only, no count), and add `&& !priorityMatch` to All's active condition. `FeedPanel.test.tsx` renders inside a router; check that its wrapper is a `MemoryRouter` so `useMatch` works.

---

### `App.tsx` (MODIFY)

**Route list** (97-104). Add `<Route path="/priority" element={<PriorityList onSetUpInterests={() => setInterestsOpen(true)} />} />` before `*`. `setInterestsOpen` is at line 76.

**Keyboard list source** (79-90):
```tsx
const { data } = useArticles(selectedFeedId ? { feedId: selectedFeedId, sort, ...readFilter } : { sort, ...readFilter })
const articles = useMemo(() => data?.pages.flatMap((p) => p.items) ?? [], [data])

useKeyboardShortcuts(articles, {
  onOpenBoard: () => setBoardOpen(true),
  onShowShortcuts: () => setShortcutsOpen(true),
})
```
Add `const isPriority = useMatch('/priority') !== null; const priority = usePriorityArticles(isPriority)`, pass `isPriority ? priority.rows : articles`, and extend the callbacks with `{ isPriority, onPriorityNextPage, onPriorityRefresh }`. Add the leave effect (RESEARCH Pattern 5): `useEffect(() => { if (!isPriority) return; return () => { qc.removeQueries({ queryKey: PRIORITY_KEY }) } }, [isPriority, qc])`. Import `useMatch` next to the existing `react-router-dom` import (line 3); `MainLayout` is already inside `BrowserRouter` (127-131). `AppShell.test.tsx` mocks `uiStore`, so check whether it renders `App`/`MainLayout`.

---

### `hooks/useKeyboardShortcuts.ts` (MODIFY)

**Callbacks interface** (11-14). Extend it with `isPriority?: boolean`, `onPriorityNextPage?: () => Promise<number | undefined>` (or pass `hasNextPage`/`fetchNextPage`), `onPriorityRefresh?: () => void`, and `onToggleWhy?` (or call `usePriorityStore` directly).

**g-chord** (59-68):
```ts
if (chordRef.current === 'g') {
  chordRef.current = null
  if (chordTimerRef.current) clearTimeout(chordTimerRef.current)
  switch (e.key) {
    case 'a': navigate('/'); return
    case 's': navigate('/starred'); return
    case 'b': navigate('/boards'); return
  }
  return
}
```
Add `case 'p': setSelectedFeed(null); navigate('/priority'); return`. `setSelectedFeed(null)` also clears the folder and article (uiStore 38-39). The chord branch runs before the plain `p` case (117-125), so there is no conflict.

**`j`** (86-92): add a Priority branch at `currentIndex === articles.length - 1`.
**`r`** (149-151): `if (callbacks.isPriority) callbacks.onPriorityRefresh?.(); else if (selectedFeedId) pollFeed.mutate(selectedFeedId)`.
**`A`** (152-156): add `&& !callbacks.isPriority`.
**`i`** (new case): toggle `whyOpen` when `currentArticle?.interestScore != null`. `currentArticle` is already resolved at 41-47.
Update the `useCallback` dependency array (180) for any new values.

**Test pattern:** `useKeyboardShortcuts.test.ts` (1-60): `vi.mock('../api/articles', ...)` before imports, `renderHook` wrapped in `QueryClientProvider` + `MemoryRouter` via `createElement`, and `article(id, overrides)` / `feed(id, overrides)` fixture factories. Extend with `g p`, `i`, `Shift+A` no-op on `/priority` (`MemoryRouter initialEntries={['/priority']}`), `r`, and `j` on the last row.

### `hooks/useInterest.ts` (MODIFY)

Add `usePriorityStore.getState().setRankingChanged(true)` inside the existing `onSuccess` of `useSaveInterestProfile` (38-41), `useCreateInterestTopic` (57-60), `useUpdateInterestTopic` (74-78), `useDeleteInterestTopic` (99-102) and `useRescoreUnread` (128-130). Update the `useInterestStatus` doc comment (10-15), which says the query is "observed only while the dialog is open"; the Priority banner now observes it too. `useInterestStatus` itself stays unchanged.

### `components/ShortcutOverlay.tsx` (MODIFY)

`shortcuts` array (6-25). Insert `{ key: 'g then p', action: 'Go to Priority' }` before `g then a` (19), `{ key: 'i', action: 'Toggle score breakdown' }` after `v` (14), and change the `r` label (15) to `'Refresh current feed / ranking'`.

### `components/EmptyState.tsx` (MODIFY, optional)

Props (1-4) are `{ message, action? }`. Add an optional `detail?: string` rendered as a second `<p>` (UI-SPEC empty-state body). The addition is backward compatible.

### `App.css` (MODIFY)

**Banner analog** (480-493):
```css
.interests-notice {
  background: var(--hover-bg);
  border-left: 3px solid var(--border);
  border-radius: 4px;
  padding: 8px 16px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}
.interests-notice.cold-start { border-left-color: var(--accent); }
.interests-notice strong { color: var(--text-primary); font-weight: 600; }
```
`.priority-banner` changes: no radius, `padding: 8px 12px`, `border-bottom: 1px solid var(--border)`, plus flex/space-between/wrap (UI-SPEC § Status banner).
**Row analogs** (244-261): `.article-item`, `.article-item.read .article-item-title { color: var(--text-muted); }`, `.article-item-meta { font-size: 0.85em; ... }`. Add `.article-item-head`, `.interest-badge`, `.interest-badge.tier-{high,neutral,low}`, `.interest-badge-slot`, `.article-item.read .interest-badge { opacity: 0.6; }`, `.priority-separator`, `.score-row`, `.topic-chips`, `.topic-chip`, `.why-toggle`, `.why-breakdown`, `.why-more`, `.priority-refresh.hint`.
**Chip colors:** reuse `.weight-positive` / `.weight-negative` (589-590) as-is.
**`.toolbar-btn`** (219-227) is the base for the refresh, Why and "Show N" buttons; `.why-toggle` overrides with `font-size: inherit`.
Only `var(--…)` from `themes.ts`, no new variables (D-20). All dimensions come from UI-SPEC.

---

### Frontend tests

**`hooks/usePriorityArticles.test.ts`**: analog `hooks/useOpml.test.ts` 24-31:
```ts
function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children),
  }
}
```
with `vi.spyOn(qc, 'invalidateQueries')` style assertions (useOpml 38-49). Mock `../api/articles` as in useKeyboardShortcuts.test.ts 3-12 (include `priority`). Cases: dedupe; `patchPriorityArticle` keeps the row with `read: true`; the focus event and timer advance cause no refetch; `resetQueries` fetches page 1 only; `invalidateQueries(['articles'])` does not touch `['priority']`.

**`components/PriorityList.test.tsx` / `PriorityBanner.test.tsx`**: analog `components/ArticleList.test.tsx` 1-40: `vi.mock('../hooks/useArticles', () => ({ useArticles: () => ({ data: { pages: [...] }, fetchNextPage: vi.fn(), hasNextPage: false, isFetchingNextPage: false }), ... }))`, a selector-based `useUIStore` mock, and `renderWithRouter` via `MemoryRouter`. For new tests, prefer `vi.mock(path, async (importOriginal) => ({ ...await importOriginal(), X: ... }))` (CLAUDE.md Test Patterns).

**`components/InterestBadge.test.tsx` / `WhyBreakdown.test.tsx`**: pure-props render with `@testing-library/react` `render`/`screen` (ArticleList.test.tsx line 1 imports). Cover the 69/70 and 39/40 boundaries, `null` giving no badge and no "0", the capped/floored total lines, and the singular/plural footer.

**`utils/interest.test.ts`** (1-30): `describe` + `it` with camelCase descriptions, looping over tables of inputs. Add `tierOf`, `articles` (thousands separator) and the level labels.

---

## Shared Patterns

### Error handling (backend)
**Source:** `controller/GlobalExceptionHandler.java` 43-55
**Apply to:** `PriorityService`, `ArticleController#priority`
```java
@ExceptionHandler(NotFoundException.class)
public ProblemDetail handleNotFound(NotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Not Found");
    return problem;
}
```
Services throw `NotFoundException` (404) / `IllegalArgumentException` (400); controllers never catch. `limit` is clamped rather than rejected (ArticleController 38-40).

### Constructor injection (backend)
**Source:** every service/repository (`@Service`/`@Repository` + Lombok `@RequiredArgsConstructor` + `private final` fields; e.g. `InterestStatusService.java` 16-24).
**Apply to:** `InterestScoreQueries`, `PriorityService`, and the modified `ArticleService`/`BoardService`/`ArticleController`. Every added constructor dependency requires a matching `@Mock` (Mockito tests) or `@MockitoBean` (`@WebMvcTest`) in the existing tests.

### Single SQL source of truth
**Source:** `ArticleScoreStore.java` 22-24 javadoc + fragment composition 36-55 ("Every query is built from the single ELIGIBLE predicate, so ... always agree").
**Apply to:** `InterestScoreQueries`. The Priority page, `displayScores` and `breakdown` all compose the same `BLEND_CTE` constant.

### Pure static helpers
**Source:** `ArticleStateBuilder.java`, `InterestQuestions.java` (`public final class`, private ctor, "Pure: no Spring, no I/O." javadoc).
**Apply to:** `ScoreBreakdowns`.

### Cache policy (frontend)
**Source:** `hooks/useArticles.ts` 24-37 (`staleTime: Infinity` + doc comment), `hooks/useInterest.ts` 64-80 (`setQueryData` in place, "deliberately not invalidated" doc comment).
**Apply to:** `usePriorityArticles`, `useUpdateArticleState` patch. Patch in place and never invalidate `['priority']`.

### Out-of-React store access
**Source:** `hooks/useArticles.ts` 76 (`useToastStore.getState().addToast(...)`), `queryClient.ts` 17.
**Apply to:** mutation `onSuccess` blocks setting `rankingChanged`.

### Theme-only colors
**Source:** `App.css` `.interests-notice*` (480-493), `.weight-positive/.weight-negative` (589-590).
**Apply to:** every new CSS rule. Use only `var(--…)`, no hex, no new variables.

### Full-replacement mock hygiene (Pitfall 8)
**Files with full `vi.mock` replacements that a new export or field can break:** `components/ArticleList.test.tsx` (useArticles, uiStore), `components/ReadingPane.test.tsx` (useArticles, useBoards, uiStore, preferencesStore), `components/FeedPanel.test.tsx` (useArticles, uiStore, preferencesStore, …), `components/AppShell.test.tsx` (uiStore), `hooks/useKeyboardShortcuts.test.ts` (api/articles). If `ArticleList`/`ReadingPane`/`FeedPanel`/`useKeyboardShortcuts` start importing a new export from a mocked module, add that export to the mock.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `components/WhyBreakdown.tsx` | component | transform | No existing grid-of-rows math breakdown or expandable footer. Nearest are `TopicRow.tsx` 165-190 (math line formatting with `formatSigned`) and the `aria-expanded` disclosure described in UI-SPEC. Build it from UI-SPEC § "Why N?" breakdown (CSS grid `1fr auto`, rule, total/cap/floor lines, footer copy) |

Partial gaps the planner should know about:
- **Keyset over a computed score:** there's no in-repo precedent for a row-value `(a, b, c) < (subquery)` comparison. Use the SQL verified in RESEARCH Pattern 2 verbatim; `ArticleRepository.java` 44-45 only shows the two-column OR form.
- **`resetQueries` / `removeQueries` / `setQueriesData`:** no current usage in the frontend (`grep` finds only `invalidateQueries`/`setQueryData`). Follow RESEARCH Pattern 5.
- **Route-dependent `MainLayout` behavior:** `useMatch` is used only in `ReadingPane.tsx` 44 today; `App.tsx` has no route-aware hooks yet.

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{repository,service,controller,config,model}`, `src/test/java/org/bartram/myfeeder/{repository,service,controller}`, `src/main/frontend/src/{hooks,components,stores,utils,api,types}`, `src/main/frontend/src/App.{tsx,css}`, both `application.yaml`s
**Files scanned:** ~45 read, 233 listed
**Pattern extraction date:** 2026-09-25
