# Phase 3: Interest Model, Schema & Rubric Editor - Pattern Map

**Mapped:** 2026-09-23
**Files analyzed:** 44 (32 new, 12 modified)
**Analogs found:** 41 / 44

All analog paths below are git-tracked source (verified with `git ls-files`). Paths are relative to the repo root; Java paths abbreviate `src/main/java/org/bartram/myfeeder/` as `…/` and tests `src/test/java/org/bartram/myfeeder/` as `test/…/`. Frontend paths are relative to `src/main/frontend/src/`.

## File Classification

### Backend: new

| New File | Role | Data Flow | Closest Analog | Match |
|----------|------|-----------|----------------|-------|
| `src/main/resources/db/migration/V6__interest_scoring.sql` | migration | DDL | `src/main/resources/db/migration/V2__folders_boards_and_feed_folder.sql` | exact |
| `…/model/InterestProfile.java` | model | CRUD | `…/model/Folder.java` | exact |
| `…/model/InterestTopic.java` | model | CRUD | `…/model/Board.java` (name + description) | exact |
| `…/repository/InterestProfileRepository.java` | repository | CRUD | `…/repository/FolderRepository.java` | exact |
| `…/repository/InterestTopicRepository.java` | repository | CRUD | `…/repository/BoardRepository.java` (`@Query`) | exact |
| `…/service/InterestService.java` | service | CRUD + validation | `…/service/FolderService.java` | exact |
| `…/service/InterestQuestions.java` | utility (pure) | transform | `test/…/integration/JevLiveSmokeTest.java` lines 70-84 (SDK builder calls) | partial |
| `…/service/ArticleStateBuilder.java` | utility (pure) | transform | `…/service/ArticleExtractionService.java` (jsoup import + parse) | partial |
| `…/service/InterestStatusService.java` | service | request-response | `…/controller/IntegrationConfigController.java` lines 33-38 (`configured` status) | role-match |
| `…/service/InterestPreviewService.java` | service | request-response (outbound) | `…/service/ArticleExtractionService.java` (load article → call outbound bean) + `…/integration/RaindropService.java` (validate before client call) | exact |
| `…/controller/InterestController.java` | controller | request-response CRUD | `…/controller/FolderController.java` | exact |
| `…/controller/ProfileUpdateRequest.java`, `TopicRequest.java`, `TopicPreviewRequest.java` | DTO (record) | request | `…/controller/CreateFolderRequest.java` | exact |
| `…/controller/InterestStatus.java`, `TopicPreviewResponse.java` | DTO (record) | response | `…/service/ExtractedContent.java` / `…/controller/CreateFolderRequest.java` | exact |

### Backend: modified

| Modified File | Role | Change | Analog |
|---------------|------|--------|--------|
| `…/integration/JevApiClient.java` | interface | add `boolean isConfigured()` | self (lines 7-26) |
| `…/integration/JevApiClientImpl.java` | client bean | add `isConfigured()`, route `requireConfigured()` through it | self (lines 34-38) |
| `…/controller/GlobalExceptionHandler.java` | controller advice | add Jev/TypeSafe handlers | self (lines 58-63, Raindrop 503) |
| `src/main/resources/application.yaml` + `src/test/resources/application.yaml` (optional, WR-01) | config | add `java.lang.IllegalArgumentException` to `jev` breaker `ignore-exceptions` | self (main lines 55-60, test lines 54-59) |
| `CLAUDE.md`, `.planning/ROADMAP.md` | docs | V6 + `/api/interest` in lists; fix roadmap note | n/a |

### Backend: tests (new)

| New Test | Type | Closest Analog | Match |
|----------|------|----------------|-------|
| `test/…/repository/V6InterestScoringMigrationTest.java` | DataJdbc (JdbcTemplate) | `test/…/repository/V4StripRaindropApiTokenMigrationTest.java` | exact |
| `test/…/repository/InterestTopicRepositoryTest.java` | DataJdbc | `test/…/repository/FolderRepositoryTest.java` | exact |
| `test/…/service/InterestServiceTest.java` | Mockito unit | `test/…/service/FolderServiceTest.java` | exact |
| `test/…/service/InterestPreviewServiceTest.java` | Mockito unit | `test/…/service/ArticleExtractionServiceTest.java` | exact |
| `test/…/service/InterestStatusServiceTest.java` | Mockito unit | `test/…/service/FolderServiceTest.java` | role-match |
| `test/…/service/InterestQuestionsTest.java`, `ArticleStateBuilderTest.java` | plain unit | `test/…/service/FeedUrlValidatorTest.java` | exact |
| `test/…/controller/InterestControllerTest.java` | WebMvc | `test/…/controller/IntegrationConfigControllerTest.java` (imports GlobalExceptionHandler, 503) + `FolderControllerTest.java` (CRUD) | exact |
| `test/…/integration/InterestCalibrationSpikeTest.java` | gated live | `test/…/integration/JevLiveSmokeTest.java` | exact |
| `test/…/integration/JevApiClientImplTest.java` (modify) | unit | self, `judgeThrowsNotConfiguredWithoutHttpCall` lines 138-145 | exact |

### Frontend

| File | New/Mod | Role | Data Flow | Closest Analog | Match |
|------|---------|------|-----------|----------------|-------|
| `api/client.ts` | mod | utility | request-response | self (lines 8-21) | exact |
| `api/client.test.ts` | mod | test | — | self | exact |
| `api/interest.ts` | new | api wrapper | CRUD | `api/integrations.ts` + `api/folders.ts` | exact |
| `hooks/useInterest.ts` | new | hook | CRUD | `hooks/useFolders.ts` + `hooks/useArticles.ts` | exact |
| `hooks/useInterest.test.ts` (optional) | new | test | — | `hooks/useFolders.test.ts` | exact |
| `utils/interest.ts` | new | utility (pure) | transform | `utils/dates.ts` | exact |
| `utils/interest.test.ts` | new | test | — | `utils/dates.test.ts` | exact |
| `types/index.ts` | mod | types | — | self (`Folder`, lines 36-41) | exact |
| `components/InterestsDialog.tsx` (+ `TopicRow`, `WeightControl`, `TopicPreviewResult`, `InterestNotices`) | new | component | CRUD + event | `components/SettingsDialog.tsx` (dialog shell, not-configured notice) + `components/MarkOlderReadDialog.tsx` (`if (!open) return null`) | role-match |
| `components/InterestsDialog.test.tsx` | new | test | — | `components/SettingsDialog.test.tsx` + `components/ReadingPane.test.tsx` (hook/store mocks) | role-match |
| `components/SettingsDialog.tsx` | mod | component | — | self (lines 6-9 props, 222-225 section) | exact |
| `components/SettingsDialog.test.tsx` | mod | test | — | self | exact |
| `App.tsx` | mod | provider wiring | — | self (lines 21-30 MutationCache, 81-119 MainLayout) | exact |
| `App.css` | mod | styles | — | self (lines 363-399 dialog rules) | exact |

---

## Pattern Assignments

### `src/main/resources/db/migration/V6__interest_scoring.sql` (migration)

**Analog:** `src/main/resources/db/migration/V2__folders_boards_and_feed_folder.sql`

House DDL style (lines 1-29): leading `-- V2__….sql` comment, `BIGSERIAL PRIMARY KEY`, `TEXT` columns (never VARCHAR), `TIMESTAMPTZ NOT NULL DEFAULT NOW()`, FKs inline with `ON DELETE CASCADE`, named indexes `idx_<table>_<col>`:
```sql
CREATE TABLE board_article (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL REFERENCES board(id) ON DELETE CASCADE,
    article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (board_id, article_id)
);
CREATE INDEX idx_board_article_board_id ON board_article(board_id);
```
FK targets: `article(id)` is `BIGSERIAL` (`V1__initial_schema.sql`), so every `article_id` FK column is `BIGINT`. `feed → article` is already `ON DELETE CASCADE` (V1), so feed delete reaches every V6 table.

Use the full DDL from RESEARCH.md Pattern 1 (lines 222-283) verbatim as the body. Key points: `article_topic_score.article_id REFERENCES article_score(article_id)` (Pitfall 3), `interest_profile` seeded with `INSERT INTO interest_profile (id) VALUES (1);`, weight `INTEGER … CHECK (weight BETWEEN -50 AND 50)`, status CHECK includes `'SKIPPED'`, `article_feedback.topics_narrowed BOOLEAN NOT NULL DEFAULT false`. No length CHECKs.

---

### `…/model/InterestProfile.java`, `…/model/InterestTopic.java` (model)

**Analog:** `…/model/Folder.java` (lines 1-17), `…/model/Board.java` (lines 1-15)

Copy these imports exactly (not the CLAUDE.md claim; `@Table` is from `relational.core.mapping`):
```java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("folder")
public class Folder {
    @Id
    private Long id;
    private String name;
    private int displayOrder;
    private Instant createdAt;
}
```
- Primitives for NOT NULL ints (`private int displayOrder;` → `private int weight; private int version;`).
- `InterestProfile`: `@Id private Integer id;` (column is `INTEGER`), `String profileText`, `int version`, `Instant updatedAt`.
- **Do not** annotate `version` with `@Version` (RESEARCH Pitfall 1).

---

### `…/repository/InterestTopicRepository.java`, `InterestProfileRepository.java` (repository)

**Analog:** `…/repository/BoardRepository.java` (lines 1-10)
```java
package org.bartram.myfeeder.repository;
import org.bartram.myfeeder.model.Board;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;

public interface BoardRepository extends ListCrudRepository<Board, Long> {
    @Query("SELECT * FROM board WHERE LOWER(name) = LOWER(:name)")
    Optional<Board> findByNameIgnoreCase(String name);
}
```
- `InterestTopicRepository extends ListCrudRepository<InterestTopic, Long>` with `@Query("SELECT * FROM interest_topic ORDER BY id") List<InterestTopic> findAllOrdered();`
- `InterestProfileRepository extends ListCrudRepository<InterestProfile, Integer>` with no custom methods (service uses `findById(1)`). `FolderRepository.java` (lines 1-9) is the zero-`@Query` shape.

---

### `…/service/InterestService.java` (service, CRUD + validation)

**Analog:** `…/service/FolderService.java`

**Imports + class shape** (lines 1-22):
```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final FolderRepository folderRepository;
    private final FeedRepository feedRepository;
```

**Create with explicit timestamps + count-based logic** (lines 28-34). Mirror this for `createTopic` (count cap, `setVersion(1)`, `setCreatedAt/UpdatedAt(Instant.now())`, RESEARCH Pitfall 2):
```java
public Folder create(String name) {
    Folder folder = new Folder();
    folder.setName(name);
    folder.setDisplayOrder((int) folderRepository.count());
    folder.setCreatedAt(Instant.now());
    return folderRepository.save(folder);
}
```

**Update with NotFoundException** (lines 36-41), used for `updateTopic`/`deleteTopic`:
```java
Folder folder = folderRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Folder not found: " + id));
```

**Validation via IllegalArgumentException** (lines 50-61):
```java
if (orderedFolderIds == null) {
    throw new IllegalArgumentException("folderIds must be provided");
}
```
Difference from the analog: `FolderService.delete` (lines 43-46) calls `deleteById` blindly; `deleteTopic` must `findById(...).orElseThrow(NotFoundException)` first (RESEARCH Pattern 2). Business rules (limits, version bump only when trimmed text differs, `isColdStart()`) come from RESEARCH Pattern 2 lines 323-357. Constants `MAX_PROFILE_CHARS`, `MAX_TOPICS`, `MIN_WEIGHT`, `MAX_WEIGHT`, `DEFAULT_WEIGHT` are `public static final` so the preview service and tests reuse them.

---

### `…/service/InterestQuestions.java` (pure utility, transform)

**Analog:** no pure-builder class exists; the closest SDK usage is `test/…/integration/JevLiveSmokeTest.java` lines 70-84:
```java
Map<String, Question> questions = new LinkedHashMap<>();
questions.put("profile", Score.builder()
        .instructions("How interested is a reader ...")
        .level("not interested")
        ...
        .build());
questions.put("t1", Noul.builder()
        .instructions("Is this article about the Rust programming language?")
        .whenTrue("The article is about the Rust programming language")
        .whenFalse("The article is not about the Rust programming language")
        .build());
```
SDK imports (from `…/integration/JevApiClientImpl.java` lines 10-12):
```java
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
```
Structure: `public final class`, private constructor, static methods, no Spring. Use the wording, keys (`PROFILE_KEY = "profile"`, `TOPIC_KEY_PREFIX = "topic_"`, `PREVIEW_DRAFT_KEY = "topic_draft"`), and map-valued instructions from RESEARCH Pattern 3 (lines 377-416). `forRubric` returns a `LinkedHashMap` (profile first when non-blank, then topics in id order).

---

### `…/service/ArticleStateBuilder.java` (pure utility, transform)

**Analog:** `…/service/ArticleExtractionService.java` for the jsoup dependency only (line 8 `import org.jsoup.Jsoup;`, line 70 `Jsoup.parse(...)`). jsoup 1.11.2 is already on the compile classpath.

The core logic has no analog; copy RESEARCH Pattern 4 (lines 423-446): `LinkedHashMap` with keys `feed`, `title`, `summary` in that order; blank fields omitted; `MAX_RAW_HTML_CHARS = 50_000` cap **before** `Jsoup.parse(bounded).text()` (Pitfall 4, CVE-2021-37714); summary falls back to `content`; truncate at whitespace to `MAX_SUMMARY_CHARS = 1500` without splitting surrogate pairs; `hasJudgeableText(state)`. Input is `(String feedTitle, Article article)`, using `Article` getters from `…/model/Article.java` lines 16-20 (`getTitle`, `getContent`, `getSummary`).

---

### `…/service/InterestStatusService.java` (service, request-response)

**Analog:** `…/controller/IntegrationConfigController.java` lines 33-38 (the "configured" rule for Raindrop):
```java
@GetMapping("/raindrop/status")
public Map<String, Boolean> raindropStatus() {
    String token = properties.getRaindrop().getApiToken();
    boolean configured = token != null && !token.isBlank();
    return Map.of("configured", configured);
}
```
Differences: return a typed record `InterestStatus(boolean configured, String breakerState, boolean coldStart)` instead of a `Map` (Phase 4 appends fields). Put the logic in a service, not the controller. Delegate "configured" to `jevApiClient.isConfigured()`, not a re-read of properties.

Breaker lookup, copied from `test/…/integration/JevResilienceTest.java` line 379:
```java
ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("jev");
```
→ inject `io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry` via `@RequiredArgsConstructor` and call `circuitBreakerRegistry.circuitBreaker("jev").getState().name()`.

---

### `…/service/InterestPreviewService.java` (service, request-response + outbound call)

**Analog A:** `…/service/ArticleExtractionService.java` (load article, validate, then call an outbound bean)

Class header + javadoc listing thrown types (lines 21-39):
```java
@Service
@RequiredArgsConstructor
public class ArticleExtractionService {

    private final ArticleRepository articleRepository;
    private final FeedFetcher feedFetcher;

    /**
     * ...
     * @throws NotFoundException        if the article doesn't exist (404)
     * @throws IllegalArgumentException if the article has no URL (400)
     */
    public ExtractedContent extract(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
        if (article.getUrl() == null || article.getUrl().isBlank()) {
            throw new IllegalArgumentException("Article " + articleId + " has no URL to extract from");
        }
```

**Analog B:** `…/integration/RaindropService.java` lines 24-45: all business validation happens **before** the resilient client call (`raindropApiClient.createBookmark(...)` on line 43 is the last step). Apply the same rule: validate description/article and build the `Noul` before calling `jevApiClient.judge(...)` (Phase 2 WR-01, RESEARCH Pitfall 11).

Not `@Transactional`. Body per RESEARCH Pattern 5 lines 474-487. Feed title via `feedRepository.findById(article.getFeedId()).map(Feed::getTitle).orElse(null)`.

---

### `…/controller/InterestController.java` (controller, request-response CRUD)

**Analog:** `…/controller/FolderController.java` (lines 1-38)
```java
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService folderService;

    @GetMapping
    public List<Folder> listFolders() { return folderService.findAll(); }

    @PostMapping
    public ResponseEntity<Folder> createFolder(@RequestBody CreateFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(request.name()));
    }

    @PutMapping("/{id}")
    public Folder renameFolder(@PathVariable Long id, @RequestBody RenameFolderRequest request) {
        return folderService.rename(id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id) { folderService.delete(id); }
}
```
- `@RequestMapping("/api/interest")`. Routes: `GET /status`, `GET|PUT /profile`, `GET|POST /topics`, `PUT|DELETE /topics/{id}`, `POST /preview` (RESEARCH endpoint table lines 503-512).
- No try/catch; `GlobalExceptionHandler` maps everything. Entities are returned directly (the house style; `Folder` is returned as JSON).
- Inject `InterestService`, `InterestStatusService`, `InterestPreviewService`.

### Request/response records

**Analog:** `…/controller/CreateFolderRequest.java`
```java
package org.bartram.myfeeder.controller;

public record CreateFolderRequest(String name) {}
```
- `ProfileUpdateRequest(String profileText)`, `TopicRequest(String name, String description, Integer weight)` (boxed so a missing weight means default 20), `TopicPreviewRequest(Long articleId, String description, Long topicId)`.
- `InterestStatus(boolean configured, String breakerState, boolean coldStart)`, `TopicPreviewResponse(Double noul, String model)`. RESEARCH places these in `controller/`; `ExtractedContent` lives in `service/`. Either works. If `InterestStatusService` returns `InterestStatus`, putting the record in `service/` avoids a service → controller package dependency (the planner decides).

---

### `…/controller/GlobalExceptionHandler.java` (modify)

**Analog:** self, lines 58-63:
```java
@ExceptionHandler(RaindropNotConfiguredException.class)
public ProblemDetail handleRaindropNotConfigured(RaindropNotConfiguredException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    problem.setTitle("Raindrop not configured");
    return problem;
}
```
Add four handlers in this style (RESEARCH Pattern 5 lines 491-497):
- `JevNotConfiguredException` → 503, title `"Jev not configured"` (the message is fixed text, so `ex.getMessage()` is safe here).
- `io.github.resilience4j.circuitbreaker.CallNotPermittedException` → 503, title `"Jev unavailable"`, **fixed** detail.
- `TypeSafeBadRequestException`, `TypeSafeUnprocessableEntityException` → `HttpStatus.valueOf(422)` (the file already uses this on line 18, not the deprecated constant), title `"Jev rejected the request"`, fixed detail with the status code only.
- `TypeSafeException` catch-all → 503, fixed detail.
- **Never** pass TypeSafe `ex.getMessage()` into the detail (Phase 2 D-06). Import paths are in `org.springaicommunity.typesafe.exception.*` (see `JevApiClientImpl.java` lines 8-9 and `JevApiClientImplTest.java` lines 12-21 for the names).

---

### `…/integration/JevApiClient.java` / `JevApiClientImpl.java` (modify)

**Analog:** self. Current `JevApiClientImpl.java` lines 34-38:
```java
private void requireConfigured() {
    if (!StringUtils.hasText(properties.getApiKey())) {
        throw new JevNotConfiguredException();
    }
}
```
Add `@Override public boolean isConfigured() { return StringUtils.hasText(properties.getApiKey()); }` with **no** `@CircuitBreaker`/`@Retry`, and make `requireConfigured()` call it. Add a javadoc'd `boolean isConfigured();` to the interface next to `judge` (line 25). Nothing else in `src/` implements `JevApiClient`, so no other implementers need updating. Tests use `ctx.getBean(JevApiClient.class)`, and new tests use `@Mock`/`@MockitoBean`.

**Test:** extend `test/…/integration/JevApiClientImplTest.java` next to lines 138-145, using its `client(String apiKey)` helper (lines 73-85):
```java
@Test
void judgeThrowsNotConfiguredWithoutHttpCall() {
    JevApiClientImpl client = client("");
    assertThatThrownBy(() -> client.judge(state(), questions()))
            .isInstanceOf(JevNotConfiguredException.class);
    server.verify();
}
```
→ add `isConfiguredReflectsKey()` asserting `client("").isConfigured()` is false, `client("  ")` is false and `client("k")` is true.

---

## Test Pattern Assignments

### `test/…/repository/V6InterestScoringMigrationTest.java`
**Analog:** `test/…/repository/V4StripRaindropApiTokenMigrationTest.java` (lines 1-42)
```java
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V4StripRaindropApiTokenMigrationTest {
    @Autowired private JdbcTemplate jdbc;
    ...
    String config = jdbc.queryForObject("SELECT config FROM integration_config WHERE type = 'RAINDROP'", String.class);
```
V6 runs at startup, so the test asserts the shape directly (no manual re-run). Add `assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class)` for CHECK/PK violations. Seed feed/article parents with `jdbc.update("INSERT INTO feed (url, title, feed_type) VALUES (?,?,?)", ...)` and use `RETURNING id` via `queryForObject`, or copy the entity-save setup from `test/…/repository/ArticleRepositoryTest.java` lines 30-38 (`feed.setUrl/Title/FeedType(FeedType.RSS)/CreatedAt`). Cases: RESEARCH lines 648-661.

### `test/…/repository/InterestTopicRepositoryTest.java`
**Analog:** `test/…/repository/FolderRepositoryTest.java` (lines 13-45): `@DataJdbcTest @Import(TestcontainersConfiguration.class)`, `@Autowired` repo, save + `findById` + ordered-list `extracting(...).containsExactly(...)`. Add: a weight-only save leaves `version` unchanged (proves there's no `@Version`), and `findById(1)` on the profile repo returns the seeded row.

### `test/…/service/InterestServiceTest.java`, `InterestStatusServiceTest.java`
**Analog:** `test/…/service/FolderServiceTest.java` (lines 19-36, 61-68)
```java
@ExtendWith(MockitoExtension.class)
class FolderServiceTest {
    @Mock private FolderRepository folderRepository;
    @Mock private FeedRepository feedRepository;
    @InjectMocks private FolderService folderService;

    @Test
    void shouldCreateFolder() {
        when(folderRepository.count()).thenReturn(2L);
        when(folderRepository.save(any())).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        ...
    @Test
    void shouldRejectReorderWithMissingIds() {
        ...
        assertThatThrownBy(() -> folderService.reorder(List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
```
For `InterestStatusServiceTest`, `@Mock CircuitBreakerRegistry` + `@Mock CircuitBreaker` (`when(registry.circuitBreaker("jev")).thenReturn(cb); when(cb.getState()).thenReturn(CircuitBreaker.State.OPEN)`), or use a real `CircuitBreakerRegistry.ofDefaults()` and `transitionToOpenState()`.

### `test/…/service/InterestPreviewServiceTest.java`
**Analog:** `test/…/service/ArticleExtractionServiceTest.java` (lines 1-60): `@Mock ArticleRepository`, `@Mock` outbound collaborator, `@InjectMocks` service, `when(articleRepository.findById(5L)).thenReturn(Optional.of(article(...)))`, and `verifyNoInteractions(feedFetcher)` for the short-circuit paths → use `verifyNoInteractions(jevApiClient)` for the blank-description/missing-article/no-text cases. Capture the questions map with `ArgumentCaptor` and assert exactly one entry of type `Noul` under key `topic_draft` or `topic_<id>`. Assert no score repository is touched (the service has none injected).

### `test/…/service/InterestQuestionsTest.java`, `ArticleStateBuilderTest.java`
**Analog:** `test/…/service/FeedUrlValidatorTest.java` (lines 1-30): plain JUnit 5 class, no extension, AssertJ `assertThat`/`assertThatThrownBy`/`assertThatCode`. Use value equality on SDK records (`Noul`/`Score` are records). Assert `new ArrayList<>(map.keySet())` order with `containsExactly`.

### `test/…/controller/InterestControllerTest.java`
**Analog:** `test/…/controller/IntegrationConfigControllerTest.java` lines 30-39, 74-81 (imports `GlobalExceptionHandler` and asserts 503):
```java
@WebMvcTest(IntegrationConfigController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
class IntegrationConfigControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RaindropService raindropService;
    ...
    when(raindropService.listCollections()).thenThrow(new RaindropNotConfiguredException());
    mockMvc.perform(get("/api/integrations/raindrop/collections"))
            .andExpect(status().isServiceUnavailable());
```
CRUD shape from `test/…/controller/FolderControllerTest.java` lines 35-53 (`post(...).contentType(MediaType.APPLICATION_JSON).content("{...}")` → `status().isCreated()`, `delete(...)` → `isNoContent()`) and lines 68-76 (IAE → `isBadRequest()`). Add `jsonPath("$.title").value("Jev unavailable")` etc. (RESEARCH lines 664-669), and assert the 422/503 detail does **not** contain the TypeSafe exception message.

### `test/…/integration/InterestCalibrationSpikeTest.java` (gated)
**Analog:** `test/…/integration/JevLiveSmokeTest.java` (whole file, lines 1-101). Copy verbatim:
- Class javadoc with the run command (lines 28-40). Swap in `JEV_CALIBRATION=true` and the `cleanTest` note.
- `@EnabledIfEnvironmentVariable(named = "JEV_CALIBRATION", matches = "true")` (line 44).
- Key precondition assert (lines 46-48).
- Main-YAML-from-disk loader (lines 50-53) and the `ApplicationContextRunner` wiring (lines 55-60):
```java
String path = "src/main/resources/application.yaml";
PropertySource<?> mainYaml = new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).getFirst();

new ApplicationContextRunner()
        .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(mainYaml))
        .withConfiguration(AutoConfigurations.of(TypeSafeAutoConfiguration.class,
                RestClientAutoConfiguration.class, HttpClientAutoConfiguration.class,
                ImperativeHttpClientAutoConfiguration.class))
        .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class))
        .run(ctx -> { ... ctx.getBean(JevApiClient.class).judge(state, questions) ... });
```
Replace the hand-built questions/state with `InterestQuestions.forRubric(...)` and `ArticleStateBuilder.build(...)`. Read input from `JEV_CALIBRATION_INPUT` (JSON via `tools.jackson.databind.json.JsonMapper`, which is Jackson 3). Print with `System.out.printf` as on line 88. Record the findings in `03-CALIBRATION.md` (RESEARCH lines 682-696).

---

## Frontend Pattern Assignments

### `api/client.ts` (modify) + `api/client.test.ts`
**Analog:** self, `raiseIfBad` lines 8-21:
```ts
async function raiseIfBad(res: Response, method: string, path: string): Promise<void> {
  if (res.ok) return
  const text = await res.text()
  let detail: string | undefined
  if (text) {
    try {
      const body = JSON.parse(text) as { detail?: string; title?: string; message?: string }
      detail = body.detail || body.title || body.message
    } catch {
      detail = text
    }
  }
  throw new Error(detail || `${method} ${path} failed: ${res.status}`)
}
```
Change only the throw: `throw new ApiError(detail || \`${method} ${path} failed: ${res.status}\`, res.status, title)` with the `title` captured from the parsed body. `ApiError` is exported from this file (RESEARCH Pattern 6 lines 519-528; no parameter properties because `erasableSyntaxOnly` is on). Existing tests (lines 9-26) must pass unchanged. Add a case in the same `vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(...))` style (lines 17-26) asserting `instanceof ApiError`, `.status === 503` and `.title === 'Jev unavailable'`.

### `api/interest.ts` (new)
**Analog:** `api/integrations.ts` (lines 1-31) for local interfaces + object export, and `api/folders.ts` (lines 1-10) for typed CRUD with path params:
```ts
import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Folder } from '../types'

export const foldersApi = {
  getAll: () => apiGet<Folder[]>('/folders'),
  create: (name: string) => apiPost<Folder>('/folders', { name }),
  rename: (id: number, name: string) => apiPut<Folder>(`/folders/${id}`, { name }),
  delete: (id: number) => apiDelete(`/folders/${id}`),
}
```
→ `export const interestApi = { getStatus, getProfile, saveProfile, listTopics, createTopic, updateTopic, deleteTopic, preview }`. Paths omit the `/api` prefix (`BASE_URL` adds it): `'/interest/status'`, `'/interest/topics'`, etc.

### `hooks/useInterest.ts` (new)
**Analog:** `hooks/useFolders.ts` (lines 1-26) and `hooks/useArticles.ts` lines 16-37 (per-query options such as `staleTime`/`retry: false`):
```ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { foldersApi } from '../api/folders'

export function useFolders() {
  return useQuery({ queryKey: ['folders'], queryFn: foldersApi.getAll })
}

export function useDeleteFolder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => foldersApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['folders'] })
      qc.invalidateQueries({ queryKey: ['feeds'] })
    },
  })
}
```
- Query keys: `['interest','status']` (`staleTime: 0`), `['interest','profile']`, `['interest','topics']`.
- Every interest mutation adds `meta: { inlineError: true }` (RESEARCH Pitfall 8).
- Profile save and topic add/delete invalidate `['interest','status']`. Topic save uses `qc.setQueryData` per row rather than blind invalidation (Pitfall 7). The `setQueryData` idiom is in `useFolders.ts` line 43.
- The preview mutation has `retry` off (the default for mutations) and does not invalidate anything.
- Reuse `useArticle(id)` from `hooks/useArticles.ts` lines 16-22 for the preview target title; do not add a new article query.

**Test (optional):** `hooks/useFolders.test.ts` (lines 1-31): `vi.mock('../api/folders', ...)` **before** imports, `createWrapper()` with `new QueryClient({ defaultOptions: { queries: { retry: false } } })`, `renderHook(..., { wrapper })`, `act(() => result.current.mutate(...))`, `waitFor`.

### `utils/interest.ts` + `utils/interest.test.ts` (new)
**Analog:** `utils/dates.ts` (lines 1-9: JSDoc'd named export, pure, injectable inputs) and `utils/dates.test.ts` (lines 1-20):
```ts
import { describe, expect, test } from 'vitest'
import { formatPublishedDate } from './dates'

describe('formatPublishedDate', () => {
  test('shows date and time when the article is less than 24 hours old', () => { ... })
})
```
Contents: `hinge`, `formatSigned` (U+2212 minus), `isNegated` with `’`→`'` normalization, plus preview-text formatting. Implementations are in RESEARCH Pattern 6 lines 546-554 and Code Examples lines 636-645 (the `0.82, +20 → "+12.8 pts"` case is the key test).

### `types/index.ts` (modify)
**Analog:** self, `Folder` (lines 36-41). Add `InterestProfile { id; profileText; version; updatedAt: string }`, `InterestTopic { id; name; description; weight; version; createdAt: string; updatedAt: string }`, `InterestStatus { configured: boolean; breakerState: string; coldStart: boolean }`, `TopicPreview { noul: number; model: string }`. Instants serialize as ISO strings (`createdAt: string` convention).

### `components/InterestsDialog.tsx` (+ sub-components) (new)
**Analog A (shell + notice):** `components/SettingsDialog.tsx`
- Props shape (lines 6-9): `interface …Props { open: boolean; onClose: () => void }`.
- Overlay/dialog markup (lines 128-131, 227-231):
```tsx
<div className="dialog-overlay" onClick={onClose}>
  <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ width: 500 }}>
    <h2>Settings</h2>
    ...
    <div className="dialog-actions">
      <button className="btn-secondary" onClick={onClose}>Close</button>
    </div>
  </div>
</div>
```
  Differences: the overlay click and Close both route through the unsaved-changes guard, the width is wider (UI-SPEC), and there is **no** Escape binding (RESEARCH Anti-Patterns).
- Section heading (lines 222-225): `<h3 style={{ fontSize: 14, marginBottom: 8 }}>`. UI-SPEC promotes this to the `.interests-section h3` class.
- Muted not-configured notice (lines 81-86): `<div style={{ fontSize: 13, color: 'var(--text-muted)' }}>…<code>…</code></div>`.
- Error color (line 90): `var(--text-error, crimson)`; `.dialog-error` in `App.css` line 397 uses `var(--toast-error-text)`.

**Analog B (early return):** `components/MarkOlderReadDialog.tsx` line 15, `if (!open) return null`. RESEARCH wants an outer shell that returns `null` when closed and an inner body component that owns the queries, so they mount only while the dialog is open.

**Store read:** `useUIStore((s) => s.selectedArticleId)` from `stores/uiStore.ts` (line 9 type, line 32 default). Do not write to it.

**Theme colors for the weight sign:** only existing vars exist (`themes.ts` lines 14-29: `--accent`, `--text-muted`, `--toast-error-text`, `--toast-success-text`). Use `--toast-success-text` for positive and `--toast-error-text` for negative, unless UI-SPEC names new vars. Adding new vars means editing all 6 theme objects.

**Danger button:** `components/FeedPanel.tsx` line 401: `<button className="btn-primary" style={{ background: 'var(--toast-error-text)' }} …>`.

### `components/InterestsDialog.test.tsx` (new)
**Analog:** `components/SettingsDialog.test.tsx` lines 1-54 (api module mock + `render(<… open={true} onClose={() => {}} />)` + `waitFor(screen.getByText(...))`), and `components/ReadingPane.test.tsx` lines 10-31 for mocking hooks and the selector-style `useUIStore`:
```tsx
vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ selectedArticleId: 1, setSelectedArticle: vi.fn(), keyboardFocus: 'list' }),
}))
```
Either mock `../api/interest` and wrap in a real `QueryClientProvider` (the `createWrapper` from `useFolders.test.ts` lines 16-23), or mock `../hooks/useInterest` and `../hooks/useArticles` wholesale as `ReadingPane.test.tsx` does. For partial module mocks, use `vi.mock(path, async (importOriginal) => ({ ...await importOriginal(), … }))` (CLAUDE.md). Use `userEvent` (imported in `SettingsDialog.test.tsx` line 3) for typing and `vi.useFakeTimers()` for the negation debounce.

### `components/SettingsDialog.tsx` + `.test.tsx` (modify)
Add an **optional** `onOpenInterests?: () => void` to `SettingsDialogProps` (lines 6-9), so existing test renders on lines 48/64 still type-check under `tsc -b`. Add a new `<div style={{ marginBottom: 20 }}><h3 …>Interests</h3>…</div>` section before Raindrop, in the same shape as lines 222-225. Add one test asserting the button calls `onOpenInterests`. The existing `preferencesStore` mock (lines 17-35) is full-replacement, so don't add preference reads.

### `App.tsx` (modify)
- MutationCache opt-out (lines 25-29). Change to `onError: (error, _vars, _ctx, mutation) => { if (mutation.meta?.inlineError) return; useToastStore.getState().addToast(...) }` (RESEARCH lines 533-539).
- `MainLayout` state + render (lines 82-85, 116): add `const [interestsOpen, setInterestsOpen] = useState(false)`, `<InterestsDialog open={interestsOpen} onClose={() => setInterestsOpen(false)} />`, and pass `onOpenInterests={() => { setSettingsOpen(false); setInterestsOpen(true) }}` to `SettingsDialog` (the UI-SPEC decides whether Settings closes).

### `App.css` (modify)
**Analog:** self, lines 363-399 (`.dialog-overlay`, `.dialog`, `.dialog-input`, `.dialog-error`, `.dialog-actions`) and line 603 (`.dialog-subtitle`). Add `.interests-*` rules after the dialog block using only theme vars (`var(--bg-secondary)`, `var(--border)`, `var(--text-muted)`, `var(--accent)`).

---

## Shared Patterns

### Exception → HTTP status (no controller try/catch)
**Source:** `…/controller/GlobalExceptionHandler.java` lines 37-56
**Apply to:** `InterestService`, `InterestPreviewService`, `InterestController`
```java
@ExceptionHandler(IllegalArgumentException.class)   // 400 "Bad Request": limits, blank description, 26th topic
@ExceptionHandler(NotFoundException.class)          // 404: missing topic / article
@ExceptionHandler(IllegalStateException.class)      // 409: never use for validation
```
`NotFoundException` lives in `…/service/NotFoundException.java` (`new NotFoundException("Topic not found: " + id)`).

### Validate before the resilient call
**Source:** `…/integration/RaindropService.java` lines 24-43; `…/integration/JevApiClientImpl.java` lines 40-47
**Apply to:** `InterestPreviewService`. All validation and SDK builder calls happen before `jevApiClient.judge(...)`. An `IllegalArgumentException` raised inside the proxied `judge` counts as a breaker failure.

### Fixed-text logging and error detail for TypeSafe
**Source:** `…/integration/JevApiClientImpl.java` lines 56-59 and `…/config/TypeSafeConfig.java` line 46
```java
// D-06: status and requestId only. The exception message and body can echo request details.
log.warn("TypeSafe rejected the API key (status {}, requestId {})", e.status(), e.requestId());
```
**Apply to:** the new `GlobalExceptionHandler` Jev handlers, `InterestPreviewService`, and the spike. Never log profile or topic text, or the key.

### Lombok constructor injection
**Source:** every service/controller (`@Service @RequiredArgsConstructor` + `private final` fields, e.g. `FolderService.java` lines 18-22)
**Apply to:** all new Spring beans. `JevApiClientImpl` uses an explicit constructor; leave it as is.

### Frontend: one api file + one hook file per domain, toast suppression
**Source:** `api/folders.ts`, `hooks/useFolders.ts`, `App.tsx` lines 25-29
**Apply to:** `api/interest.ts`, `hooks/useInterest.ts`. Every interest mutation sets `meta: { inlineError: true }`.

### Frontend type-check
Run `npx tsc -b` from `src/main/frontend/` (not `tsc --noEmit`). Test files are type-checked too.

---

## No Analog Found

| File | Role | Data Flow | Reason / Use Instead |
|------|------|-----------|----------------------|
| `…/service/InterestQuestions.java` (wording content) | utility | transform | No question builder exists; the SDK calls come from `JevLiveSmokeTest`, and the wording/structure come from RESEARCH Pattern 3 |
| `…/service/ArticleStateBuilder.java` (strip/truncate logic) | utility | transform | Only the jsoup import has an analog; the logic comes from RESEARCH Pattern 4 |
| Weight slider + number sync, debounced negation warning, unsaved-changes confirm bar (inside `InterestsDialog`/`WeightControl`/`TopicRow`) | component | event-driven | No range input, debounce or dirty-guard exists in the frontend; follow 03-UI-SPEC.md and RESEARCH Pattern 6 |

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{config,controller,integration,model,repository,service}`, `src/main/resources/db/migration`, `src/test/java/org/bartram/myfeeder/{controller,integration,repository,service}`, `src/main/frontend/src/{api,hooks,components,stores,utils,types}`, `App.tsx`, `App.css`, `themes.ts`
**Files scanned:** ~45 read, ~130 listed
**Pattern extraction date:** 2026-09-23
