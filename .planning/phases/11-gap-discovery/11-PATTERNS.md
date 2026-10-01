# Phase 11: Gap Discovery - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 21 (9 new, 12 modified)
**Analogs found:** 21 / 21

RESEARCH.md §1-§6 already contains near-final code skeletons; this map ties each file to the tracked analog it must mirror. All analog paths verified with `git ls-files`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `model/SuggestionDismissalReason.java` (NEW) | model enum | — | `model/EngagementKind.java` | exact |
| `repository/TopicSuggestionStore.java` (NEW) | store | CRUD (read query + idempotent insert) | `repository/ArticleEngagementStore.java` | exact |
| `service/TopicSuggestionService.java` (NEW) | service | request-response | `service/InterestRescoreService.java` (+ `ArticleService.recordOpen` 404) | role-match |
| `service/TopicSuggestion.java`, `service/TopicSuggestions.java` (NEW) | DTO record | — | `service/RescoreCount.java` | exact |
| `controller/TopicSuggestionController.java` (NEW) | controller | request-response | `controller/InterestRescoreController.java` + `ArticleController.recordOpen` (lines 103-111) | exact |
| `controller/TopicRequest.java` (MOD) | DTO | — | itself (append `Long sourceArticleId`) | — |
| `controller/InterestController.java` (MOD) | controller | CRUD | itself | — |
| `service/InterestService.java` (MOD) | service | CRUD, transactional | itself, `createTopic` lines ~68-85 | — |
| `config/MyfeederProperties.java` (MOD) | config | — | itself, `ENGAGEMENT_INVALID` + `Blend.Engagement.isValid` | exact |
| `src/main/resources/application.yaml`, `src/test/resources/application.yaml` (MOD) | config | — | existing `myfeeder.interest.blend.engagement` keys | exact |
| `frontend/src/api/interest.ts` (MOD) | api client | request-response | `getRescoreCount` / `createTopic` lines 82-95 | exact |
| `frontend/src/hooks/useInterest.ts` (MOD) | hook | request-response | `useCreateInterestTopic` lines 82-94 | exact |
| `frontend/src/hooks/engagementReaction.ts` (MOD) | utility | event-driven | `invalidateAfterLearnedChange` lines 13-21 | — |
| `frontend/src/components/InterestsDialog.tsx` (MOD) | component | — | `TopicsSection` (seed ~452-460, `markSaved` ~495-504, `atMax` 515, "+ Add topic" ~573-575) | — |
| `frontend/src/components/TopicRow.tsx` (MOD) | component | — | its `handleSave` create path | — |
| `frontend/src/components/FeedbackNotice.tsx` (MOD) | component | — | its existing draft builder | — |
| `frontend/src/App.css` (MOD) | style | — | `.interests-topic-row` (~line 790) | role-match |
| Tests: `TopicSuggestionStoreTest` (NEW) | test | — | `repository/ArticleEngagementStoreTest.java` | exact |
| `TopicSuggestionControllerTest` (NEW) | test | — | `controller/InterestRescoreControllerTest.java` | exact |
| `SuggestionsApiIntegrationTest` (NEW) | test | — | `controller/EngagementApiIntegrationTest.java` | exact |
| `MyfeederPropertiesValidationTest` (MOD) | test | — | itself (`refusalTextIsFixed`) | — |

(Java paths under `src/main/java/org/bartram/myfeeder/`, frontend under `src/main/frontend/src/`.)

## Pattern Assignments

### `repository/TopicSuggestionStore.java` (store, CRUD)
**Analog:** `repository/ArticleEngagementStore.java`

Imports / class shape (lines 1-24):
```java
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ArticleEngagementStore {
    private final JdbcClient jdbc;
```
Idempotent insert (lines 27-33) — copy for `handle(articleId, reason)`, but use `INSERT … SELECT a.id, CAST(:reason AS text) FROM article a WHERE a.id = :articleId ON CONFLICT (article_id) DO NOTHING` (RESEARCH §1) so a stale id inserts 0 rows:
```java
return jdbc.sql("INSERT INTO article_engagement (article_id, kind) VALUES (:articleId, :kind) "
                + "ON CONFLICT (article_id, kind) DO NOTHING")
        .param("articleId", articleId)
        .param("kind", kind.name())
        .update() == 1;
```
Row-mapped query (lines 48-52) — pattern for `candidates(cutoff, nearMiss)`; full SQL in RESEARCH §1 (`CANDIDATES` constant, `Candidate` record).
Javadoc must document: only class reading `topic_suggestion_dismissal`; `created_at` is first-insert-per-kind (RESEARCH Pitfall 7).
**Hard rule:** no reference to `topic_suggestion_dismissal` may appear in `InterestScoreQueries.java` (`V7EngagementMigrationTest` raw-text check).

### `service/TopicSuggestionService.java` (service)
**Analog:** `service/InterestRescoreService.java` lines 1-50 (constructor-injected `@Service @RequiredArgsConstructor`, reads `MyfeederProperties`, no Jev call path). Do NOT inject `JevApiClient` (GAP-05).
404 pattern from `service/ArticleService.java` lines 59-64:
```java
if (!articleRepository.existsById(id)) {
    throw new NotFoundException("Article not found: " + id);
}
engagementStore.record(id, EngagementKind.OPEN_ORIGINAL);
```
Body of `list()` (displayScores two-step, sort, cap 10, total after badge filter): RESEARCH §2.

### `service/TopicSuggestion.java`, `TopicSuggestions.java`
**Analog:** `service/RescoreCount.java` — a Javadoc'd one-line `public record`. Shapes: `(long articleId, String title, String feedTitle, int interestScore)`, `(List<TopicSuggestion> items, long total)`.

### `controller/TopicSuggestionController.java`
**Analog:** `controller/InterestRescoreController.java` (lines 13-23):
```java
@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestRescoreController {
    private final InterestRescoreService service;
    @GetMapping("/rescore")
    public RescoreCount rescoreCount() { return service.count(); }
```
Bodyless idempotent PUT from `controller/ArticleController.java` lines 103-111:
```java
/** ... Bodyless and idempotent. PUT is never a CORS simple request, so no content-type guard is needed. */
@PutMapping("/{id}/engagement/open")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void recordOpen(@PathVariable Long id) { articleService.recordOpen(id); }
```
Route: `PUT /api/interest/suggestions/{articleId}/dismissal`. No try/catch; `GlobalExceptionHandler` maps `NotFoundException` → 404.

### `service/InterestService.java` (MOD) — `createTopic`
Current (already `@Transactional`):
```java
@Transactional
public InterestTopic createTopic(String name, String description, Integer weight) {
    if (topicRepository.count() >= MAX_TOPICS) { throw new IllegalArgumentException("A maximum of 25 topics is allowed"); }
    ... validate, build ...
    return topicRepository.save(topic);
}
```
Change: add `Long sourceArticleId`; save first, then `if (sourceArticleId != null) suggestionStore.handle(sourceArticleId, TOPIC_CREATED)`; return saved. Optional 3-arg `@Transactional` delegating overload keeps existing test call sites. Update `InterestControllerTest` stubs (lines ~90, 103) if the controller calls the 4-arg form. New `InterestServiceTest` cases need `@Mock TopicSuggestionStore`.

### `controller/TopicRequest.java` (MOD)
`public record TopicRequest(String name, String description, Integer weight, Long sourceArticleId) {}` — append only; `PUT /topics/{id}` ignores it.

### `config/MyfeederProperties.java` (MOD)
Analog in-file (lines 15-36):
```java
static final String ENGAGEMENT_INVALID = "myfeeder.interest.blend.engagement must be cap 0 (disabled), "
        + "or 0 <= open-weight < save-weight < 1 and 0 < cap < learned-cap";
...
@Override
public void validate(Object target, Errors errors) {
    Interest.Blend blend = ((MyfeederProperties) target).getInterest().getBlend();
    if (!blend.getEngagement().isValid(blend.getLearnedCap())) {
        errors.reject("engagement", ENGAGEMENT_INVALID);
    }
}
```
Add `SUGGESTIONS_INVALID` + `Interest.Suggestions { double nearMiss = 0.35; boolean isValid() { return 0 < nearMiss && nearMiss <= 0.5; } }` as a sibling of `Blend` (NOT inside `blend` — `InterestCalibrationReplaySqlTest.driverPassesEveryBlendParameter`). Nested `@Data public static class` like `Blend.Engagement`.

### yaml (MOD)
Identical literal `myfeeder.interest.suggestions.near-miss: 0.35` in main and test `application.yaml`; leave `application-dev.yaml` untouched (`DevProfileConfigTest`).

### `frontend/src/api/interest.ts` (MOD)
Analog lines 82-95:
```ts
getRescoreCount: () => apiGet<RescoreCount>('/interest/rescore'),
createTopic: (input: TopicInput) => apiPost<InterestTopic>('/interest/topics', input),
```
Add `getSuggestions`, `dismissSuggestion` (`apiPut<void>` bodyless), `TopicSuggestion(s)` types, `TopicInput.sourceArticleId?`.

### `frontend/src/hooks/useInterest.ts` (MOD)
Analog `useCreateInterestTopic` lines 82-94:
```ts
export function useCreateInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: TopicInput) => interestApi.createTopic(input),
    meta: { inlineError: true },
    onSuccess: (topic: InterestTopic) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) => (old ? [...old, topic] : [topic]))
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
      void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
```
Add `SUGGESTIONS_KEY = ['interest','suggestions']`, `useTopicSuggestions` (`staleTime: 0`), `useDismissSuggestion` (RESEARCH §6); add suggestions invalidation to create, delete and rescore `onSuccess`.

### `frontend/src/hooks/engagementReaction.ts` (MOD)
Add one line to `invalidateAfterLearnedChange` (lines 13-21), next to:
```ts
void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
```

### `InterestsDialog.tsx` / `TopicRow.tsx` / `FeedbackNotice.tsx` / `App.css` (MOD)
In-file analogs; full skeleton in RESEARCH §6. Key edits: `TopicDraft.sourceArticleId?`; `addDraft(draft?)` and `onClick={() => addDraft()}`; preserve `sourceArticleId` in `markSaved`; render `<SuggestedTopics>` after `TopicsSection`'s `</section>` in a fragment; reuse `InterestBadge`; theme variables only in CSS.

### Tests
- **Store** — `repository/ArticleEngagementStoreTest.java` lines 26-41: `@DataJdbcTest`, `@Import({TestcontainersConfiguration.class, ArticleEngagementStore.class})`, `@Autowired JdbcTemplate`, feed seeded in `@BeforeEach`. Also `DELETE FROM article_score; DELETE FROM article; DELETE FROM interest_topic` first (RESEARCH Pitfall 3). `@Import` `InterestScoreQueries` too if the test covers the service.
- **Controller slice** — `controller/InterestRescoreControllerTest.java` lines 23-32: `@WebMvcTest(X.class)`, `@ImportAutoConfiguration(JacksonAutoConfiguration.class)`, `@Import({GlobalExceptionHandler.class})`, `@MockitoBean` service.
- **Integration** — `controller/EngagementApiIntegrationTest.java` lines 41-49: `@SpringBootTest @Import(TestcontainersConfiguration.class)`, `@MockitoBean JevApiClient`, `verify(jevApiClient, never()).judge(any(), any())`; assert only on seeded ids.
- **Frontend** — add a default `GET /api/interest/suggestions` → `{items:[],total:0}` route in `InterestsDialog.test.tsx` `beforeEach`; update `ReadingPane.test.tsx:351` payload with `sourceArticleId`.

## Shared Patterns

- **Errors:** throw `NotFoundException` (404) / `IllegalArgumentException` (400, fixed text); no controller try/catch (`GlobalExceptionHandler`).
- **Writes:** `JdbcClient` + `ON CONFLICT DO NOTHING`, `.update() == 1` for "inserted" boolean.
- **Mutating routes:** bodyless PUT (no CSRF guard needed) or JSON-only POST (`consumes = APPLICATION_JSON_VALUE`).
- **Constants:** yaml under `myfeeder.interest.*`, validated in `MyfeederProperties.validate` with fixed text, identical main/test literals.
- **No Jev:** none of the new classes depend on `JevApiClient`.

## No Analog Found

None. All files have an in-repo analog.

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{repository,service,controller,config}`, `src/test/java/...`, `src/main/frontend/src/{api,hooks,components}`
**Files scanned:** ~14
**Pattern extraction date:** 2026-10-01
