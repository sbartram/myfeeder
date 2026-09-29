# Phase 6: Thumbs Feedback - Pattern Map

**Mapped:** 2026-09-26
**Files analyzed:** 40 (new + modified, backend + frontend + tests)
**Analogs found:** 38 / 40 (2 new frontend components have only a partial analog, see "No Analog Found")

All analog paths below are git-tracked source (verified with `git ls-files`). Paths are relative to the repo root `/Users/scottb/orca/workspaces/myfeeder/main`. Backend package prefix `src/main/java/org/bartram/myfeeder/` is shortened to `…/myfeeder/`; frontend `src/main/frontend/src/` to `fe/`.

## File Classification

### Backend (main)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/myfeeder/repository/InterestScoreQueries.java` (M) | repository (read-only SQL) | transform (CTE) | itself: `blendCte` L198-212, `breakdownInputs` L154-191, `displayScores` L133-145 | self |
| `…/myfeeder/repository/ArticleFeedbackStore.java` (NEW) | repository (JdbcClient store) | CRUD (upsert/delete) | `…/myfeeder/repository/ArticleScoreStore.java` | exact |
| `…/myfeeder/service/ArticleFeedbackService.java` (NEW) | service | request-response, transactional write + re-read | `…/myfeeder/service/InterestRescoreService.java` + `ArticleService.findByIdWithBreakdown` + `ArticleScoreStore.writeScored` (`@Transactional`) | role-match |
| `…/myfeeder/service/FeedbackResult.java` (NEW record) | DTO | response | `…/myfeeder/service/RescoreCount.java`, `TopicPreviewResponse.java` | exact |
| `…/myfeeder/model/ArticleFeedback.java` (NEW record) | model/DTO | response | `…/myfeeder/model/InterestBreakdown.java` (record with nested records + `@JsonInclude`) | exact |
| `…/myfeeder/model/Article.java` (M) | model | — | itself L31-34 (`@Transient interestBreakdown`) | self |
| `…/myfeeder/model/InterestBreakdown.java` (M) | model | — | itself `Row` L23-36 | self |
| `…/myfeeder/service/ScoreBreakdowns.java` (M) | utility (pure) | transform | itself L75-77 | self |
| `…/myfeeder/controller/ArticleController.java` (M) | controller | request-response | itself + `…/myfeeder/controller/InterestRescoreController.java` L25-32 (JSON-only guard) | exact |
| `…/myfeeder/controller/FeedbackRequest.java` (NEW record) | DTO (request) | request | `…/myfeeder/controller/TopicRequest.java`, `RescoreRequest.java` | exact |
| `…/myfeeder/controller/InterestController.java` (M) | controller | request-response | itself L26-27 (`GET /topics`) | self |
| `…/myfeeder/config/MyfeederProperties.java` (M) | config | — | itself `Interest.Blend` L52-56 | self |
| `src/main/resources/application.yaml` (M) | config | — | itself L42-43 | self |
| `src/test/resources/application.yaml` (M) | config | — | itself L41-42 | self |

### Backend (tests)

| File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java` (extend) | test (`@DataJdbcTest`) | — | itself (fixture helpers L385-412) | self |
| `src/test/java/org/bartram/myfeeder/repository/ArticleFeedbackStoreTest.java` (NEW, optional) | test (`@DataJdbcTest`) | CRUD | `src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java` / `InterestScoreQueriesTest.java` L38-64 | exact |
| `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java` (NEW) | test (Mockito unit) | — | `src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java` L1-58 | exact |
| `src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java` (NEW) | test (`@SpringBootTest` + MockMvc) | — | `src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java` L37-63, helpers L298-330 | exact |
| `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java` (extend) | test (`@WebMvcTest`) | — | itself L36-43, L145-173 | self |
| `src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java` (extend) | test (`@WebMvcTest`) | — | itself L26-32 | self |
| `src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java` (extend) | test (pure unit) | — | itself L22-25 | self |

### Frontend (main)

| File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `fe/api/articles.ts` (M) | API wrapper | request-response | itself L21-29; `fe/api/interest.ts` L50-64 | self |
| `fe/api/client.ts` (M, likely) | API client | request-response | itself `apiPut` L54-62 / `apiDelete` L74-77 | self (see gap note) |
| `fe/types/index.ts` (M) | types | — | itself `Article` L19-40, `TopicBreakdownRow` L50-59 | self |
| `fe/api/interest.ts` (M) | API wrapper + types | request-response | itself L10-18, L50-64 | self |
| `fe/hooks/useFeedback.ts` (NEW) | hook (TanStack mutation) | request-response + cache patch | `fe/hooks/useArticles.ts` `useUpdateArticleState` L48-61 + `fe/hooks/useInterest.ts` mutations L34-110 | role-match |
| `fe/hooks/useInterest.ts` (M) | hook | CRUD | itself L48-52 (`useInterestTopics`), L54-110 (invalidate on save/delete) | self |
| `fe/hooks/usePriorityArticles.ts` (M) | hook utility | cache patch | itself `patchPriorityArticle` L85-110 | self |
| `fe/utils/feedback.ts` (NEW) | utility (pure) | transform | `fe/utils/interest.ts` (`formatSigned` L1-9, `formatPreviewText` L51-61) | exact |
| `fe/components/FeedbackBar.tsx` (NEW) | component | event-driven | `fe/components/ScoreRow.tsx` (small pane sub-component reading `article`) + `fe/components/ReadingPane.tsx` toolbar L150-180 | role-match |
| `fe/components/NarrowPicker.tsx` (NEW) | component (popover) | event-driven | `fe/components/ArticleList.tsx` split-menu L28-41, L140-160 (outside-click + absolute menu) | partial |
| `fe/components/ReadingPane.tsx` (M) | component | — | itself L19-24 (props), L148-180 (toolbar) | self |
| `fe/components/WhyBreakdown.tsx` (M) | component | transform (display) | itself L37-46 | self |
| `fe/components/TopicRow.tsx` (M) | component | display | itself L237-242 (focus-on-mount), L337-342 (line-2) | self |
| `fe/components/InterestsDialog.tsx` (M) | component | — | itself L41-49 (props), L385-397 (`seedRows`), L433-459 (`TopicsSection`, `addDraft`) | self |
| `fe/components/ShortcutOverlay.tsx` (M) | component (static) | — | itself L6-27 | self |
| `fe/components/Toast.tsx` (M, one attr) | component | — | itself L37 | self |
| `fe/hooks/useKeyboardShortcuts.ts` (M) | hook | event-driven | itself L145-181 (`s`, `i`, Shift+A) | self |
| `fe/stores/priorityStore.ts` (M) or `fe/stores/feedbackStore.ts` (NEW) | store (zustand, session) | — | `fe/stores/priorityStore.ts` L1-30 | exact |
| `fe/App.tsx` (M) | layout | — | itself L75-137 (`MainLayout`, `interestsOpen`, `ReadingPane` props) | self |
| `fe/App.css` (M) | styles | — | itself `.reading-toolbar` L384-389, `.interests-notice` L621-634, `.split-btn-menu` L930-953, `.weight-positive/negative` L730-731 | self |

### Frontend (tests)

| File | Role | Closest Analog | Match Quality |
|---|---|---|---|
| `fe/utils/feedback.test.ts` (NEW) | unit | `fe/utils/interest.test.ts` | exact |
| `fe/hooks/useFeedback.test.ts` (NEW) | hook test | `fe/hooks/usePriorityArticles.test.ts` L1-60 | exact |
| `fe/components/FeedbackBar.test.tsx` (NEW) | RTL | `fe/components/ReadingPane.test.tsx` L1-70 | role-match |
| `fe/components/NarrowPicker.test.tsx` (NEW) | RTL | `fe/components/WhyBreakdown.test.tsx` (pure-props component test) | role-match |
| `fe/hooks/useKeyboardShortcuts.test.ts` (extend) | hook test | itself L1-80 | self |
| `fe/components/ReadingPane.test.tsx` (extend mocks) | RTL | itself L11-39 | self |
| `fe/components/WhyBreakdown.test.tsx`, `fe/components/TopicRow.test.tsx`, `fe/components/InterestsDialog.test.tsx` (extend) | RTL | themselves | self |

---

## Pattern Assignments — Backend

### `…/myfeeder/repository/InterestScoreQueries.java` (repository, transform) — MODIFY

**Analog:** itself.

**Current stub to replace** (L198-212):
```java
private static String blendCte(String scope) {
    return "WITH learned AS (SELECT t.id AS topic_id, 0::double precision AS delta FROM interest_topic t), "
            + "eff AS (SELECT t.id, t.weight + COALESCE(l.delta, 0) AS w "
            + "FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id), "
            + "contrib AS (SELECT ts.article_id, ts.topic_id, ts.noul, "
            + "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, "
            + "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "
            + "FROM article_topic_score ts JOIN eff e ON e.id = ts.topic_id), "
            + "blended AS (...)";
}
```
Replace `learned`/`eff` with the probed body in 06-RESEARCH.md Pattern 1 (`learned` → `eff` (base, learned_raw, learned) → `eff2` (sign clamp + ±50 → `w`)); `contrib` joins `eff2` and additionally selects `e.base` and `e.w - e.base AS learned_applied` for the Why row (Pitfall 4). Keep the string-concatenation style and the class javadoc rule (L26-28: scope fragments are compile-time constants, every value is a named param). Update the javadoc at L34-35 (the "returns 0 until Phase 6" note).

**Param-binding pattern to centralize** (every caller binds by hand today — L98, L119, L139, L161, L178):
```java
.param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
```
Add one private helper (e.g. `private JdbcClient.StatementSpec blend(String sql)` that calls `jdbc.sql(sql).param("profilePoints", …).param("learnRate", …).param("learnedCap", …)`) and route all five statements (`priorityFirstPage`, `priorityPageAfter`, `displayScores`, both `breakdownInputs` statements) plus the new reads through it (Pitfall 1). Casts follow the cursor style at L117:
```java
" < (CAST(:cursorScore AS float8), CAST(:cursorDate AS timestamptz), CAST(:cursorId AS bigint)) "
```
→ `CAST(:learnRate AS float8)`, `CAST(:learnedCap AS float8)` (untyped unary minus fails on Postgres).

**Record-extension pattern** (L77-83): nested public records next to the queries. Extend `TopicContribution` with `double baseWeight, double learnedWeight` (appended components) and map them in the `breakdownInputs` topic query (L175-187: `rs.getDouble("w")` style). Add new nested records here for the new reads, e.g. `TopicWeight(long topicId, String name, double base, double learned, double learnedRaw, double w)` and a matched-topic record.

**New read methods** — copy `displayScores` (L133-145) for an id-collection read with an early empty return, and `breakdownInputs` (L154-191) for `optional()`/`list()` row mapping:
```java
public Map<Long, Integer> displayScores(Collection<Long> ids) {
    if (ids.isEmpty()) {
        return Map.of();
    }
    Map<Long, Integer> scores = new HashMap<>();
    jdbc.sql(blendCte(IDS_SCOPE) + " SELECT b.article_id, " + INTEREST_SCORE + " AS interest_score FROM blended b")
            .param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
            .param("ids", ids)
            .query(rs -> {
                scores.put(rs.getLong("article_id"), rs.getObject("interest_score", Integer.class));
            });
    return scores;
}
```
- `topicWeights(Collection<Long> topicIds)` (null/all variant for `/topics/learned`): select from `eff2` joined to `interest_topic` for names; round before/after with `ROUND(x::numeric, 6)` like `exact` at L176.
- `matchedTopics(long articleId)`: `article_topic_score` rows with hinge > 0 for a SCORED article (same "scored" definition as `blended`'s `WHERE s.status = 'SCORED'`, L211).
- `isScored(long articleId)`: existence of a SCORED `article_score` row.
Note: `blendCte` is `static` today and scope-parameterized; the learned/eff part is scope-independent, so a `learnedCte()` fragment reused by `blendCte` and `topicWeights` keeps "one source of truth".

---

### `…/myfeeder/repository/ArticleFeedbackStore.java` (repository, CRUD) — NEW

**Analog:** `…/myfeeder/repository/ArticleScoreStore.java`

**Imports + class shape** (L1-31, L57):
```java
package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
...
@Repository
@RequiredArgsConstructor
public class ArticleScoreStore {
    ...
    private final JdbcClient jdbc;
```
Class javadoc states exactly which tables it writes (L26-27: "This store writes and deletes only `article_score` and `article_topic_score` rows…") — mirror it: "writes/deletes only `article_feedback` and `article_feedback_topic`; never writes `interest_topic.weight`".

**Upsert pattern** (L114-132, `ON CONFLICT … DO UPDATE` with named params and `.update()`):
```java
int written = jdbc.sql("INSERT INTO article_score (article_id, status, ...) "
                + "SELECT a.id, 'SCORED', ... FROM article a WHERE a.id = :articleId "
                + "ON CONFLICT (article_id) DO UPDATE SET status = 'SCORED', ... ")
        .param("articleId", articleId)
        ...
        .update();
```
**Child-row loop pattern** (L136-145): one `INSERT` per child with `.param(...)` in a `for` loop — use for `article_feedback_topic` after `DELETE FROM article_feedback_topic WHERE article_id = :id`.

**Delete pattern** (L231-235): single-statement `jdbc.sql("DELETE …").param(...).update()`; the V6 cascade removes child picks.

**Read pattern** for the vote state (vote + narrowed + pick ids/names) → `.query((rs, rowNum) -> …).optional()` as in `loadCandidate` (L92-101).

Use raw JdbcClient, NOT a `CrudRepository` (RESEARCH "Alternatives": assigned PK makes `save()` issue an UPDATE).

---

### `…/myfeeder/service/ArticleFeedbackService.java` (service, transactional request-response) — NEW

**Analogs:** `…/myfeeder/service/InterestRescoreService.java` (shape), `…/myfeeder/service/InterestPreviewService.java` (validation order + fixed text), `…/myfeeder/service/ArticleService.java` L32-43 (article + breakdown enrichment), `ArticleScoreStore.writeScored` L112 (`@Transactional` from `org.springframework.transaction.annotation`).

**Class shape** (InterestRescoreService L1-25):
```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.springframework.stereotype.Service;
...
/** javadoc naming the requirement ids and decisions */
@Service
@RequiredArgsConstructor
public class InterestRescoreService {
    private final JevApiClient jevApiClient;
    ...
```
Do NOT inject `JevApiClient` (FDBK-02: a vote never calls Jev); the integration test asserts no interaction.

**Validation with fixed-text messages** (InterestPreviewService L42-53):
```java
if (articleId == null) {
    throw new IllegalArgumentException("articleId is required");
}
...
Article article = articleRepository.findById(articleId)
        .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
```
Map: missing article → `NotFoundException` (404); `vote ∉ {-1, 1}`, empty `topicIds`, `topicIds` not a subset of matched, oversize list → `IllegalArgumentException` (400) with fixed text that never echoes input (InterestService class javadoc L14-17). Validate before any write.

**Enrichment to reuse** (ArticleService L32-43) — call `articleService.findByIdWithBreakdown(id)` for the response article rather than duplicating, then set the new `feedback` field:
```java
public Optional<Article> findByIdWithBreakdown(Long id) {
    return articleRepository.findById(id).map(article -> {
        interestScoreQueries.breakdownInputs(id).ifPresentOrElse(inputs -> {
            article.setInterestScore(inputs.display());
            article.setInterestBreakdown(ScoreBreakdowns.build(inputs));
        }, () -> { ... });
        return article;
    });
}
```
Also: `GET /api/articles/{id}` must carry `feedback` too (the pane reads toggle state from `['article', id]`). Either extend `findByIdWithBreakdown` to set it (then `ArticleService` gains a store dependency; update `ArticleServiceTest` constructor) or have the controller/feedback service decorate it — planner's call.

**Core flow** — RESEARCH Pattern 3 (before → write → after in one `@Transactional` method). Compute `limit` (`NONE | LEARNED_CAP | SIGN_CLAMP | WEIGHT_RANGE`) from the after state.

---

### `…/myfeeder/service/FeedbackResult.java` + effect record (DTO) — NEW

**Analog:** `…/myfeeder/service/RescoreCount.java`
```java
package org.bartram.myfeeder.service;

/**
 * A Re-score count (INT-05). For {@code GET /api/interest/rescore} it is ...
 */
public record RescoreCount(long count, int windowDays) {
}
```
→ `public record FeedbackResult(Article article, boolean scored, List<TopicEffect> effects) {}` with a javadoc that states D-07 ("the client prints after − before; never recomputes"). `TopicEffect(long topicId, String name, double before, double after, double baseWeight, double learned, String limit)` — nested or sibling record; the response shape is the costly-to-reverse contract (D-07), so document field meaning in the javadoc.

---

### `…/myfeeder/model/ArticleFeedback.java` (model record) — NEW

**Analog:** `…/myfeeder/model/InterestBreakdown.java` L1-41
```java
package org.bartram.myfeeder.model;

import com.fasterxml.jackson.annotation.JsonInclude;   // Jackson 3: annotations stay in com.fasterxml

public record InterestBreakdown(BigDecimal raw, int total, int display, List<Row> rows,
                                List<NonMatchingTopic> nonMatching) {
    ...
    /** A topic judged for this article that did not match (noul at or below 0.5, hinge 0). */
    public record NonMatchingTopic(long topicId, String name, double noul) {}
}
```
→ `public record ArticleFeedback(int vote, boolean narrowed, List<Topic> topics) { public record Topic(long topicId, String name) {} }`.

---

### `…/myfeeder/model/Article.java` — MODIFY

**Pattern** (L31-34): copy exactly for the new field:
```java
// The "Why N?" breakdown; only set on GET /api/articles/{id}, omitted from JSON elsewhere
@Transient
@JsonInclude(JsonInclude.Include.NON_NULL)
private InterestBreakdown interestBreakdown;
```
→ `@Transient @JsonInclude(NON_NULL) private ArticleFeedback feedback;` (null = no vote, omitted from list JSON; `org.springframework.data.annotation.Transient`, already imported L6). Client treats absent as "no vote".

---

### `…/myfeeder/model/InterestBreakdown.java` + `…/myfeeder/service/ScoreBreakdowns.java` — MODIFY

**Row record** (InterestBreakdown L23-36): append `Double baseWeight, Double learnedWeight` at the END of the components (existing JSON keys unchanged); `profile(...)` passes `null, null` (omitted by `@JsonInclude(NON_NULL)` L22); `topic(...)` gains two params.

**Call sites that must change** (compile breaks otherwise):
- `…/myfeeder/service/ScoreBreakdowns.java` L77: `Row.topic(t.topicId(), t.name(), t.noul(), t.hinge(), t.weight(), c.exact(), points[i])` → add `t.baseWeight(), t.learnedWeight()` (from the extended `TopicContribution`).
- `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java` L153-155 (3 × `Row.topic(...)`).
- `src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java` L22-25 helper `new TopicContribution(id, name, noul, hinge, weight, bd(exact))`.
- `InterestScoreQueries.java` L180 `new TopicContribution(...)`.
Apportionment (`apportion` L94-115) is unchanged — `exact` already uses effective `w`.

---

### `…/myfeeder/controller/ArticleController.java` (controller) — MODIFY

**Analog for the JSON-only write** — `…/myfeeder/controller/InterestRescoreController.java` L25-32:
```java
/** JSON-only, so a cross-site "simple" POST (no body, form or text) is refused with 415 before any reset. */
@PostMapping(value = "/rescore", consumes = MediaType.APPLICATION_JSON_VALUE)
public RescoreCount rescore(@RequestBody RescoreRequest request) {
    if (!request.confirm()) {
        throw new IllegalArgumentException("confirm must be true");
    }
    return service.rescore();
}
```
→ `@PutMapping(value = "/{id}/feedback", consumes = MediaType.APPLICATION_JSON_VALUE) public FeedbackResult setFeedback(@PathVariable Long id, @RequestBody FeedbackRequest request)` and `@DeleteMapping("/{id}/feedback") public FeedbackResult clearFeedback(@PathVariable Long id)`. Needs `import org.springframework.http.MediaType;`.

**Existing controller conventions** (L19-30, L70-74): `@RequiredArgsConstructor` with `private final` services; thin methods, no try/catch (GlobalExceptionHandler maps exceptions):
```java
@PatchMapping("/{id}")
public ResponseEntity<Article> updateState(@PathVariable Long id, @RequestBody ArticleStateRequest request) {
    Article updated = articleService.updateState(id, request.getRead(), request.getStarred());
    return ResponseEntity.ok(updated);
}
```
Add `private final ArticleFeedbackService articleFeedbackService;` → `ArticleControllerTest` needs a new `@MockitoBean` (L40-43 list).

---

### `…/myfeeder/controller/FeedbackRequest.java` (request DTO) — NEW

**Analog:** `…/myfeeder/controller/TopicRequest.java`
```java
package org.bartram.myfeeder.controller;

/** Boxed weight: a missing weight on POST means the +20 default; on PUT it is required. */
public record TopicRequest(String name, String description, Integer weight) {}
```
→ `/** topicIds null = not narrowed (D-17); non-null must be a non-empty subset of matched topics (D-15). */ public record FeedbackRequest(Integer vote, List<Long> topicIds) {}` (boxed `Integer` so a missing vote is a 400 from the service, not a 0 default).

---

### `…/myfeeder/controller/InterestController.java` — MODIFY (`GET /topics/learned`)

**Pattern** (L26-27):
```java
@GetMapping("/topics")
public List<InterestTopic> listTopics() { return interestService.listTopics(); }
```
→ `@GetMapping("/topics/learned") public List<TopicLearned> learned() { return <service>.learned(); }`. No route collision: `/topics/{id}` has only PUT/DELETE (L35-42). A new constructor dependency means `InterestControllerTest` (L26-32) needs a matching `@MockitoBean`. Add the route to root `CLAUDE.md` "Routes under /api/interest" in the docs pass.

---

### `…/myfeeder/config/MyfeederProperties.java` + both `application.yaml` — MODIFY

**Pattern** (MyfeederProperties L52-56):
```java
@Data
public static class Blend {
    /** Points for a full profile match (R1): the profile contributes profile_score / profile_max_level x profilePoints. */
    private int profilePoints = 100;
}
```
→ add `private double learnRate = 2;` and `private int learnedCap = 20;` with one-line javadoc each (R1/R2; Phase 7 tunes).

**YAML** — main `src/main/resources/application.yaml` L42-43 and test `src/test/resources/application.yaml` L41-42 are identical today:
```yaml
    blend:
      profile-points: 100
```
→ add `learn-rate: 2` and `learned-cap: 20` to BOTH files with identical raw values (DevProfileConfigTest compares `String.valueOf` of raw values; RESEARCH Pitfall 2).

---

## Pattern Assignments — Backend tests

### `InterestScoreQueriesTest.java` — EXTEND

**Setup** (L38-64): `@DataJdbcTest @Import({TestcontainersConfiguration.class, InterestScoreQueries.class}) @EnableConfigurationProperties(MyfeederProperties.class)`; `@BeforeEach` deletes and reseeds inside the rolled-back transaction. Add `jdbc.update("DELETE FROM article_feedback")` (cascades picks) to setUp, and helpers styled like L394-412:
```java
private void insertTopicScore(long articleId, long topicId, double noul) {
    jdbc.update("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
            articleId, topicId, noul);
}
```
→ `insertFeedback(long articleId, int vote, boolean narrowed)` and `insertPick(long articleId, long topicId)`. Test cases = RESEARCH probe table (cap, sign clamp, ±50, zero base both ways, narrowed only, zero picks, unscored vote = 0, up/down/up = single up). Existing assertions stay green because the fixture has no feedback rows.

### `ArticleFeedbackServiceTest.java` — NEW

**Analog:** `InterestRescoreServiceTest.java` L1-58:
```java
@ExtendWith(MockitoExtension.class)
class InterestRescoreServiceTest {
    private static final String REFUSAL = "...fixed text...";
    @Mock private JevApiClient jevApiClient;
    @Mock private InterestService interestService;
    @Mock private ArticleScoreStore store;
    private InterestRescoreService service;

    @BeforeEach
    void setUp() {
        service = new InterestRescoreService(jevApiClient, interestService, store, new MyfeederProperties());
        lenient().when(...).thenReturn(...);
    }
```
Constructor-built service (not `@InjectMocks`), `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessage(FIXED_TEXT)`, `verify(store, never()).upsert(...)` for validation-before-write.

### `FeedbackApiIntegrationTest.java` — NEW

**Analog:** `PriorityApiIntegrationTest.java` L37-63:
```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PriorityApiIntegrationTest {
    private static final String PRIORITY_FEED_URL = "https://example.test/priority-it-feed.xml";
    private static final String PRIORITY_TOPIC_NAME = "priority-it-topic";
    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, score rows and topic-score rows cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", PRIORITY_FEED_URL);
        jdbcTemplate.update("DELETE FROM interest_topic WHERE name LIKE ?", PRIORITY_TOPIC_NAME + "%");
    }
```
Use its own prefix (e.g. `feedback-it-…`); feedback rows cascade from the feed's articles. Helpers `insertFeed/insertArticle/insertTopic/insertTopicScore/insertScored` at L298-330 — copy them. Response parsing via `com.jayway.jsonpath.JsonPath.read(...)` (L80-83). Only assert on topics this class seeded (learned CTE sums all feedback per topic; container shared). For "no Jev call", add `@MockitoBean JevApiClient` and `verifyNoInteractions`.

### `ArticleControllerTest.java` — EXTEND

Add `@MockitoBean private ArticleFeedbackService articleFeedbackService;` to the list at L40-43. New cases: PUT with JSON → 200; PUT with `text/plain` → 415 (copy from `InterestRescoreControllerTest`); service `IllegalArgumentException` → 400; `NotFoundException` → 404. Note: `ArticleControllerTest` has no `@Import(GlobalExceptionHandler.class)` line while `InterestControllerTest` does (L26-28) — `@WebMvcTest` picks up `@RestControllerAdvice` automatically; follow the existing file.

---

## Pattern Assignments — Frontend

### `fe/api/articles.ts` (+ `fe/api/client.ts`) — MODIFY

**Pattern** (articles.ts L21-29):
```ts
getById: (id: number) => apiGet<Article>(`/articles/${id}`),
updateState: (id: number, state: { read?: boolean; starred?: boolean }) =>
  apiPatch<Article>(`/articles/${id}`, state),
```
→ `setFeedback: (id: number, vote: 1 | -1, topicIds?: number[]) => apiPut<FeedbackResult>(\`/articles/${id}/feedback\`, { vote, topicIds: topicIds ?? null })`.

**GAP — DELETE with a body:** `apiDelete` (client.ts L74-77) returns `Promise<void>` and never parses the body:
```ts
export async function apiDelete(path: string): Promise<void> {
  const res = await fetch(`${BASE_URL}${path}`, { method: 'DELETE' })
  await raiseIfBad(res, 'DELETE', path)
}
```
The DELETE feedback response (`FeedbackResult`) drives the "Vote removed · Rust −2.0" toast (D-10), so either add a typed sibling (e.g. `apiDeleteJson<T>` copying `apiPut` L54-62 minus the body, ending `return parseBody<T>(res)`) with a case in `fe/api/client.test.ts`, or change `apiDelete` to be generic without breaking its 5 existing void callers (boards/folders/integrations/interest/feeds). Planner decides; do not reuse `apiDelete` as-is.

**Mock ripple:** full-replacement `vi.mock('../api/articles', …)` in `fe/hooks/useKeyboardShortcuts.test.ts` L3-13 and `fe/hooks/usePriorityArticles.test.ts` L3-14 must list `setFeedback`/`clearFeedback` if code under test touches them.

### `fe/types/index.ts` + `fe/api/interest.ts` — MODIFY

- `Article` (types L19-40): add optional `feedback?: ArticleFeedback | null` with a doc comment in the same style as `interestScore` L33-37 ("Optional so existing fixtures still type-check").
- `TopicBreakdownRow` (L50-59): append `baseWeight?: number; learnedWeight?: number` (optional → WhyBreakdown/ReadingPane test fixtures still type-check).
- New `ArticleFeedback`, `FeedbackResult`, `TopicEffect`, `Limit` types (either here or in `utils/feedback.ts` per RESEARCH Code Examples).
- `fe/api/interest.ts`: add `TopicLearned` interface next to `InterestTopic` (L10-18) and `getLearned: () => apiGet<TopicLearned[]>('/interest/topics/learned')` in `interestApi` (L50-64).

### `fe/hooks/useFeedback.ts` (hook, mutation + cache policy) — NEW

**Analogs:** `fe/hooks/useArticles.ts` L48-61 (article mutation + patch Priority), `fe/hooks/useInterest.ts` L34-46 (`meta.inlineError`, `setRankingChanged`).

Imports style (useArticles.ts L1-5):
```ts
import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { useToastStore } from '../components/Toast'
import { patchPriorityArticle } from './usePriorityArticles'
```
Mutation + cache pattern (useArticles.ts L48-61):
```ts
export function useUpdateArticleState() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, state }: { id: number; state: { read?: boolean; starred?: boolean } }) =>
      articlesApi.updateState(id, state),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['article', variables.id] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
      // Priority rows change in place and never refetch (D-07); request values only.
      patchPriorityArticle(qc, variables.id, variables.state)
    },
  })
}
```
Inline-error + ranking hint (useInterest.ts L35-46):
```ts
return useMutation({
  mutationFn: (profileText: string) => interestApi.saveProfile(profileText),
  meta: { inlineError: true },
  onSuccess: (profile: InterestProfile) => {
    qc.setQueryData(['interest', 'profile'], profile)
    void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    usePriorityStore.getState().setRankingChanged(true)
  },
})
```
Differences for the vote hook (RESEARCH Patterns 5/6): `mutationKey: ['feedback']`, `scope: { id: 'article-feedback' }`, synchronous intent write via `qc.setQueryData(['article', id], …)` in the `press` handler before `mutate`; `onSuccess` merges `res.article` into `['article', id]` (keep the cached `feedback` while `qc.isMutating({ mutationKey: ['feedback'] }) > 1`); Priority branch = `patchPriorityArticle(qc, id, { interestScore })` + `setRankingChanged(true)`, never touching `['priority']`; non-Priority branch = invalidate `['articles']` + other length-2 `['article', n]` keys (predicate style from `refreshPriority`, usePriorityArticles.ts L80-82); always `invalidateQueries(['interest','learned'])`; toast via `useToastStore.getState().addToast(msg, 'success')` (useArticles L79); `onError` → `addToast(errMsg, 'error')` + invalidate `['article', id]`. Route detection: `useMatch('/priority') !== null` (App.tsx L89, ReadingPane.tsx L49 uses `useMatch`). Keep it in a NEW file so the `vi.mock('../hooks/useArticles', …)` full replacements in ReadingPane/ArticleList/FeedPanel tests stay valid.

### `fe/hooks/usePriorityArticles.ts` — MODIFY `patchPriorityArticle`

**Current** (L92-110):
```ts
export function patchPriorityArticle(
  qc: QueryClient,
  id: number,
  patch: { read?: boolean; starred?: boolean },
): void {
  const fields: { read?: boolean; starred?: boolean } = {}
  if (patch.read !== undefined) fields.read = patch.read
  if (patch.starred !== undefined) fields.starred = patch.starred
  qc.setQueriesData<InfiniteData<PriorityPage>>({ queryKey: PRIORITY_KEY }, (old) => { ... })
}
```
Widen the patch type with `interestScore?: number | null` (copy only when `!== undefined`; `null` is a real value). Update the doc comment L85-91 ("keeps … its listed interestScore" is no longer true for votes).

### `fe/hooks/useInterest.ts` — MODIFY

Add `useLearnedTopics()` copying `useInterestTopics` (L50-52): `useQuery({ queryKey: ['interest', 'learned'], queryFn: interestApi.getLearned })`. Add `void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })` to the create (L60-64), update (L78-83) and delete (L104-108) `onSuccess` blocks — never invalidate `TOPICS_KEY` (L68-71 comment, Pitfall 7).

### `fe/utils/feedback.ts` (pure utility) — NEW

**Analog:** `fe/utils/interest.ts` — JSDoc one-liners over small exported pure functions, U+2212 minus:
```ts
/**
 * A number with an explicit sign: "+20", "−15" (U+2212 minus) or "0", with `digits` decimals.
 */
export function formatSigned(n: number, digits = 0): string {
  const s = Math.abs(n).toFixed(digits)
  if (n > 0) return `+${s}`
  if (n < 0) return `−${s}`
  return s
}
```
`formatSigned(0)` returns `"0"`, so the toast's `+0.0` needs the new `formatDelta` (RESEARCH Code Examples: `nextVote`, `formatDelta`, `formatEffects`, limit notes; exact copy in 06-UI-SPEC "Effect toast"). Import `formatSigned` from `./interest` for the `(learned at max +20)` / `(now −28.8)` notes.

### `fe/components/FeedbackBar.tsx` (component) — NEW

**Analogs:** `fe/components/ScoreRow.tsx` (small pane component taking `article`, reading matched topics from the breakdown) and the toolbar button style in ReadingPane L150-164.

Matched-topic extraction (ScoreRow L16-18) — reuse, but WITHOUT the `weight !== 0` filter (picker/no-match use every `hinge > 0` row, which is exactly `kind === 'TOPIC'`):
```ts
const chips = (article.interestBreakdown?.rows ?? []).filter(
  (row): row is TopicBreakdownRow => row.kind === 'TOPIC' && row.weight !== 0
)
```
Toolbar button + ARIA toggle style (ScoreRow L35-42):
```tsx
<button
  className="toolbar-btn why-toggle"
  aria-expanded={whyOpen}
  aria-controls="why-breakdown"
  onClick={toggleWhy}
>
```
→ `<button className="toolbar-btn vote-btn" aria-pressed={active} aria-label="Thumbs up" title=…>👍 Up</button>`; narrow control uses `aria-haspopup="dialog" aria-expanded aria-controls="narrow-picker"`. No-match strip follows `.interests-notice` (App.css L621-634) with `role="status"`; it renders between `.reading-toolbar` and `.reading-content`, so FeedbackBar may need to return two siblings (toolbar group + notice) or ReadingPane renders the notice separately — layout per 06-UI-SPEC.

### `fe/components/NarrowPicker.tsx` (popover) — NEW

**Partial analog:** `fe/components/ArticleList.tsx` split menu.

Outside-click close (ArticleList L28-41):
```tsx
const [showDropdown, setShowDropdown] = useState(false)
const dropdownRef = useRef<HTMLDivElement>(null)

useEffect(() => {
  if (!showDropdown) return
  const handleClickOutside = (e: MouseEvent) => {
    if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
      setShowDropdown(false)
    }
  }
  document.addEventListener('mousedown', handleClickOutside)
  return () => document.removeEventListener('mousedown', handleClickOutside)
}, [showDropdown])
```
Anchor + absolute menu (ArticleList L141, L152-157; CSS `.split-btn-group { position: relative }` / `.split-btn-menu { position: absolute; top: 100%; … z-index: 100 }` App.css L928-941) → `.feedback-group` (relative) + `.narrow-picker` (absolute). Note `.split-btn-menu-item:hover` uses `--bg-hover`, which is NOT a theme variable — new rules use `--hover-bg` (06-UI-SPEC Design System).

Key isolation (no existing analog): React `onKeyDown` on the focused container calling `e.preventDefault(); e.stopPropagation()` for `1`–`9`, Enter, Escape (RESEARCH Pattern 7 / Pitfall 5); global handler is a `document` listener (useKeyboardShortcuts L208-211). Sign colors from ScoreRow L26-30 (`weight-positive` / `weight-negative` + trailing `−`).

### `fe/components/ReadingPane.tsx` — MODIFY

- Props (L19-24): add an `onCreateTopic?: (draft) => void` prop like `onBoardClose`, OR read a store action (see stores below).
- Toolbar (L150-153): insert the feedback group directly after the Star button:
```tsx
<div className="reading-toolbar">
  <button className="toolbar-btn" onClick={handleStar}>
    {article.starred ? '★ Unstar' : '★ Star'}
  </button>
  {/* FeedbackBar / .feedback-group goes here */}
  <button className="toolbar-btn" onClick={handleToggleRead}>
```
- `.reading-toolbar` CSS (App.css L384-389) gains `flex-wrap: wrap; row-gap: 4px;`.
- Picker must close on article change: follow the per-article reset effect L38-41 (`useEffect(() => { setReaderViewChoice(null) }, [article?.id])`).

### `fe/components/WhyBreakdown.tsx` — MODIFY

**Current topic label** (L39-44):
```tsx
<span
  className="why-label"
  title={`Match ${Math.round(row.noul * 100)}% → counts ${Math.round(row.hinge * 100)}% × ${formatSigned(row.weight)} = ${formatSigned(row.exact, 1)} pts`}
>
  {`${row.name}  ${Math.round(row.hinge * 100)}% × ${formatSigned(row.weight)}`}
</span>
```
Branch on `Math.round((row.learnedWeight ?? 0) * 10) !== 0`: learned → `${name}  ${pct}% × ${formatSigned(weight, 1)} (${formatSigned(baseWeight)} ${formatSigned(learnedWeight, 1)} learned)`; else keep the current string byte-for-byte (Phase 5 tests). Points column L45 unchanged.

### `fe/components/TopicRow.tsx` — MODIFY

- Learned line under line-2 (L337-342) for saved rows only (`isDraft` L250): `<p className="interests-learned">…</p>`; values come in as a prop (e.g. `learned?: TopicLearned`) looked up by `row.id` in `TopicsSection` — do not fetch per row.
- Focus: drafts already focus on mount (L237-242, `focusOnMount = useRef(row.saved === null)`), so the prefilled draft gets focus with no new prop; add `scrollIntoView({ block: 'nearest' })` if the UI-SPEC requires it.
- "Unsaved base edit" state: compare `row.weight !== row.saved?.weight` (same data as `isTopicDirty`, utils/interest.ts L81-88).

### `fe/components/InterestsDialog.tsx` — MODIFY

**Props → body** (L41-51): add an optional `draft?: { description: string; weight: 20 | -20 } | null` to `InterestsDialogProps`, pass through `InterestsDialogBody` into `TopicsSection` (L87-92).

**Seed-once pattern** (L433-439, L385-397):
```tsx
function TopicsSection({ topics, status, statusFailed, onDirtyCountChange }: TopicsSectionProps) {
  const [rows, setRows] = useState<TopicRowState[]>(() => seedRows(topics))
  ...
  const draftCounter = useRef(0)
```
Seed the draft inside the `useState` initializer (and start `draftCounter` at 1) using the exact draft shape of `addDraft` (L445-459):
```tsx
{
  key: `d-${draftCounter.current}`,
  id: null,
  name: '',
  description: '',
  weightText: '20',
  weight: 20,
  saved: null,
}
```
→ `description: title.trim().slice(0, 500)`, `weightText: String(weight)`, `weight`. Skip the draft when `topics.length >= TOPICS_MAX` (L28, L482) and render an `.interests-notice` (pattern: `InterestNotices` L149-168) with the at-max copy. Learned-load error note uses the `.interests-help`/`.interests-note` paragraph style near L492.

### `fe/stores/priorityStore.ts` (or new `fe/stores/feedbackStore.ts`) — MODIFY/NEW

**Analog** (priorityStore.ts L1-30) — session-only zustand, no `persist`, documented as kept out of the widely mocked `uiStore`:
```ts
import { create } from 'zustand'

interface PriorityState {
  /** Whether the reading pane's "Why N?" breakdown is open; survives article changes (D-01). */
  whyOpen: boolean
  /** Flips whyOpen; shared by the Why toggle and the i shortcut. */
  toggleWhy: () => void
}

export const usePriorityStore = create<PriorityState>((set) => ({
  ...
  whyOpen: false,
  toggleWhy: () => set((s) => ({ whyOpen: !s.whyOpen })),
}))
```
Add `narrowOpen: boolean` + `setNarrowOpen` (Shift+D from the keyboard hook in `MainLayout` → picker in `ReadingPane`) and optionally `interestsDraft` + setter/consume-and-clear (D-20). Accessed outside React via `usePriorityStore.getState()` (useKeyboardShortcuts L170). A new small `feedbackStore.ts` avoids touching existing `priorityStore` mocks; either is fine.

### `fe/App.tsx` — MODIFY

**Current wiring** (L78, L119, L135):
```tsx
const [interestsOpen, setInterestsOpen] = useState(false)
...
readingPane={<ReadingPane boardOpen={boardOpen} onBoardClose={() => setBoardOpen(false)} />}
...
<InterestsDialog open={interestsOpen} onClose={() => setInterestsOpen(false)} />
```
Pattern for a child opening the dialog already exists: `PriorityList onSetUpInterests={() => setInterestsOpen(true)}` (L117). Add `onCreateTopic={(draft) => { setInterestsDraft(draft); setInterestsOpen(true) }}` to `ReadingPane`, pass `draft` to `InterestsDialog`, clear it in `onClose`.

### `fe/hooks/useKeyboardShortcuts.ts` — MODIFY

**Toggle source** (L53-54) — use `fetchedArticle` (by-id, carries `feedback`), not `currentArticle`:
```ts
const { data: fetchedArticle } = useArticle(selectedArticleId)
const currentArticle = (currentIndex >= 0 ? articles[currentIndex] : null) ?? fetchedArticle ?? null
```
**Case pattern** (L150-154, L168-171, L176-181):
```ts
case 's':
  if (currentArticle) {
    updateState.mutate({ id: currentArticle.id, state: { starred: !currentArticle.starred } })
  }
  break
case 'i':
  if (currentArticle?.interestScore != null) usePriorityStore.getState().toggleWhy()
  break
case 'A':
  // Explicit route guard: no bulk mark-read from Priority (PRIO-07).
  if (e.shiftKey && selectedFeedId && !callbacks.isPriority) {
```
→ `case 'u'` / `case 'd'` (return early on `e.metaKey || e.ctrlKey || e.altKey`, Pitfall 6; no-op until `fetchedArticle` loaded) call `useVoteFeedback().press(id, ±1)`; `case 'D'` with `e.shiftKey` opens the picker only when the narrow control would be visible. Add the new hook value to the `useCallback` deps array (L205). Don't touch other keys.

### `fe/components/ShortcutOverlay.tsx` / `fe/components/Toast.tsx` — MODIFY

- ShortcutOverlay L12: insert after `{ key: 's', action: 'Toggle star' }`: `{ key: 'u / d', action: 'Thumbs up / down (press again to remove)' }`, `{ key: 'Shift+D', action: 'Choose topics to penalize' }`.
- Toast L37: `<div className="toast-container">` → add `role="status"` only.

---

## Pattern Assignments — Frontend tests

### `fe/hooks/useFeedback.test.ts` — NEW
**Analog:** `fe/hooks/usePriorityArticles.test.ts` L1-60: `vi.mock('../api/articles', () => ({ articlesApi: { … all fns as vi.fn() … } }))` BEFORE imports, `article(id, overrides)` factory, `createWrapper()` returning `{ qc, wrapper }` with `retry: false`, `renderHook` both hooks in one render to share `qc`. Seed `qc.setQueryData(PRIORITY_KEY, { pages: [...], pageParams: [...] })` and assert it is patched, not invalidated. Wrap in `MemoryRouter initialEntries={['/priority']}` for `useMatch` (keyboard test wrapper L74-80 shows `MemoryRouter` inside `QueryClientProvider`). Out-of-order test: `articlesApi.setFeedback` returns deferred promises resolved in reverse.

### `fe/utils/feedback.test.ts` — NEW
**Analog:** `fe/utils/interest.test.ts` — `describe`/`it` with camelCase test names that state the rule (`'isNegatedMatchesTheWordList'`), loops over input tables with `expect(fn(x), x)`.

### `fe/components/FeedbackBar.test.tsx` / `NarrowPicker.test.tsx` — NEW
**Analog:** `fe/components/ReadingPane.test.tsx` L1-70 (module-level `vi.mock` of hooks returning `{ mutate: vi.fn(), isPending: false }`, stores mocked via selector functions, `article(overrides)` factory cast `as Article`, `MemoryRouter` render) and `fe/components/WhyBreakdown.test.tsx` L1-30 (`topic(name, opts)` row factory). For NarrowPicker, assert Esc closes without `setSelectedArticle(null)` (Pitfall 5).

### Existing tests to extend
- `fe/components/ReadingPane.test.tsx` L11-16: it fully mocks `../hooks/useArticles`; add a `vi.mock('../hooks/useFeedback', …)` (and a store mock if a new store is used).
- `fe/hooks/useKeyboardShortcuts.test.ts` L3-13: add `setFeedback`/`clearFeedback` to the `articlesApi` mock.
- `fe/components/TopicRow.test.tsx`: fetch-route harness (L10-31 `route(method, url, handler)`) — add a `GET /api/interest/topics/learned` route if TopicRow/TopicsSection fetch it.
- `fe/components/InterestsDialog.test.tsx`: draft prefill, at-max notice.

---

## Shared Patterns

### Error mapping (no try/catch in controllers)
**Source:** `…/myfeeder/controller/GlobalExceptionHandler.java` (L43-50: `NotFoundException` → 404, `IllegalArgumentException` → 400; `IllegalStateException` → 409)
**Apply to:** `ArticleFeedbackService`, `ArticleController` feedback endpoints, `InterestController` learned endpoint.
Throw the typed exception from the service with fixed text; never echo submitted values (`InterestService` class javadoc, `InterestPreviewService` L42-53).

### JSON-only mutating endpoint (CSRF)
**Source:** `…/myfeeder/controller/InterestRescoreController.java` L25-26
**Apply to:** `PUT /api/articles/{id}/feedback` (`consumes = MediaType.APPLICATION_JSON_VALUE`). DELETE needs nothing (never a simple request).

### JdbcClient SQL conventions
**Source:** `InterestScoreQueries.java` L26-28, L117; `ArticleScoreStore.java` L112-146
**Apply to:** `ArticleFeedbackStore`, new `InterestScoreQueries` reads.
Named params only; SQL fragments are class constants; explicit `CAST(:p AS float8)` for numeric binds used with unary minus; `ROUND(x::numeric, 6)` for values shipped to the client; `ON CONFLICT` upserts; `@Transactional` from `org.springframework.transaction.annotation`.

### Session UI state in zustand, not uiStore
**Source:** `fe/stores/priorityStore.ts` L18-22 (plain `create`, no persist; kept out of the widely mocked `uiStore`)
**Apply to:** picker-open flag, interests draft.

### Mutation error surfacing
**Source:** `fe/queryClient.ts` L14-19 (global toast unless `meta.inlineError`), `fe/hooks/useInterest.ts` L39
**Apply to:** `useVoteFeedback` — set `meta: { inlineError: true }` and emit its own `addToast(msg, 'error')` with the 06-UI-SPEC error copy (by `ApiError.status`: 400 narrowing, 404 gone, else generic). `ApiError` is exported from `fe/api/client.ts` L9 and already used in `TopicRow.tsx` L3.

### Priority is patched, never invalidated
**Source:** `fe/hooks/usePriorityArticles.ts` L7-11 (`PRIORITY_KEY` outside `['articles']`), L85-110; `refreshPriority` L75-83 (length-2 `['article', n]` predicate)
**Apply to:** `useVoteFeedback` cache policy.

### Server numbers only
**Source:** `WhyBreakdown.tsx` L5-10 doc ("no score or point value is ever recomputed"); `types/index.ts` `InterestBreakdown` doc
**Apply to:** toast formatter, Why row, TopicRow learned line — only `after − before` and rounding happen client-side.

### Theme-variable CSS
**Source:** `fe/App.css` `.interests-notice` L621-634, `.split-btn-menu` L930-941, `.weight-positive/negative` L730-731
**Apply to:** `.feedback-group`, `.vote-btn[aria-pressed='true']`, `.narrow-picker*`, `.feedback-notice`, `.feedback-create`, `.interests-learned`. Only `var(--…)` theme values; `--hover-bg` (not `--bg-hover`).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `fe/components/NarrowPicker.tsx` (key isolation part) | component | event-driven | No existing popover owns keys: nothing calls `stopPropagation` on keydown to shield the document-level shortcut handler. Use RESEARCH Pattern 7 / Pitfall 5 (React root delegation). Outside-click + absolute positioning DO have an analog (ArticleList split menu). |
| `fe/hooks/useFeedback.ts` (serialization part) | hook | request-response | No existing mutation uses TanStack `scope` or a synchronous intent write before `mutate`. Use RESEARCH Pattern 5; the cache half has the `useUpdateArticleState` analog. |

## Planner Notes (gaps found while mapping)

1. **`apiDelete` drops the response body** (`fe/api/client.ts` L74-77). `DELETE /feedback` returns `FeedbackResult`, so a typed DELETE helper (plus a `client.test.ts` case) is needed.
2. **`TopicContribution` constructor change** ripples to `ScoreBreakdownsTest.java` L24 and `InterestScoreQueries.java` L180; **`Row.topic` change** ripples to `ScoreBreakdowns.java` L77 and `ArticleControllerTest.java` L153-155.
3. **New constructor dependencies** need `@MockitoBean` in `ArticleControllerTest` (L40-43) and `InterestControllerTest` (L31); if `ArticleService` gains a feedback-store dependency, `ArticleServiceTest` changes too.
4. **`GET /api/articles/{id}` must include `feedback`**, not only the vote response, or the pane shows no pressed state after a reload.
5. **Blend yaml keys go in both yaml files** with identical raw values (`DevProfileConfigTest`).

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{repository,service,controller,model,config}`, `src/test/java/org/bartram/myfeeder/{repository,service,controller}`, `src/main/frontend/src/{api,hooks,stores,components,utils,types}`, `src/main/resources`, `src/test/resources`
**Files scanned:** ~45 read in full or in part
**Pattern extraction date:** 2026-09-26
