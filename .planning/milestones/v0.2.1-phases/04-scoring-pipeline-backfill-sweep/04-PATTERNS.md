# Phase 4: Scoring Pipeline & Backfill Sweep - Pattern Map

**Mapped:** 2026-09-23
**Files analyzed:** 38 (13 new main, 6 new test, 19 modified)
**Analogs found:** 36 / 38 (2 have only partial analogs: the `JdbcClient` store and the executor-owning `ScoringQueue`)

All analog paths below are git-tracked source (checked against `git ls-files`). Class names follow the RESEARCH.md recommendations (Claude's discretion). The planner may rename them but should keep the split.

## File Classification

### Backend: new files

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java` | event (record) | event-driven | `event/FeedSavedEvent.java` | exact |
| `src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java` | listener | event-driven | `scheduler/FeedPollingScheduler.java` L43-51 | exact (annotation), role-match (body) |
| `src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java` | config (`@Bean` executor) | n/a | `config/TypeSafeConfig.java` | role-match |
| `src/main/java/org/bartram/myfeeder/service/ScoringQueue.java` | service (in-memory queue) | batch / hand-off | `scheduler/FeedPollingScheduler.java` (concurrent map state + catch-and-WARN) + `integration/JevApiClientImpl.java` L26-32 (explicit constructor) | partial |
| `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java` | service | request-response (outbound Jev) then write | `service/InterestPreviewService.java` | exact |
| `src/main/java/org/bartram/myfeeder/service/ScoringFailure.java` (optional) | utility (classifier) | transform | `controller/GlobalExceptionHandler.java` L71-105 (Jev exception type mapping) | role-match |
| `src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java` | repository (`JdbcClient`) | CRUD (upserts, counts, delete) | `repository/ArticleRepository.java` (SQL style) + `V6InterestScoringMigrationTest` (raw JDBC) | partial (no `JdbcClient` in main yet) |
| `src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java` | scheduled job | batch | `service/RetentionService.java` (`@Scheduled` + properties) + `service/InterestStatusService.java` L24 (breaker read) | exact |
| `src/main/java/org/bartram/myfeeder/service/InterestRescoreService.java` | service | CRUD (count + delete) | `service/InterestStatusService.java` / `service/InterestService.java` | role-match |
| `src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java` | controller | request-response | `controller/InterestStatusController.java` + `controller/InterestPreviewController.java` | exact |
| `src/main/java/org/bartram/myfeeder/service/RescoreCount.java` (or similar `{count}` record) | DTO record | n/a | `service/TopicPreviewResponse.java` / `service/InterestStatus.java` | exact |

### Backend: modified files

| Modified File | Role | Change | Analog |
|---------------|------|--------|--------|
| `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java` | service | collect new IDs, publish event after success bookkeeping | self L45-60; publisher injection from `service/FeedService.java` L27, L52 |
| `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java` | config | add `Interest` nested class | self L10-31 (`Polling`/`Retention`) |
| `src/main/java/org/bartram/myfeeder/service/InterestStatus.java` | DTO record | append `long eligibleUnscored, long failed` | self L17 |
| `src/main/java/org/bartram/myfeeder/service/InterestStatusService.java` | service | add store + counts | self L15-26 |
| `src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java` | utility | D-13 truncate fix | self L56-74 |
| `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java` | API client | D-15 question-type check | self L50-53 |
| `src/main/resources/application.yaml` | config | D-05/06/07/14/17 + `myfeeder.interest.*` | self L14-17, L33-79 |
| `src/test/resources/application.yaml` | config | mirror everything above | self L7-12, L32-78 |
| `CLAUDE.md` (root) | docs | D-07 Resilience4j convention text | line 137 |
| `.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md` | docs | remove the aspect-order part now covered by D-07 | n/a |

### Frontend: modified files

| Modified File | Role | Change | Analog |
|---------------|------|--------|--------|
| `src/main/frontend/src/api/interest.ts` | API wrapper | `InterestStatus` fields + `getRescoreCount`/`rescore` | self L20-24, L43-55 |
| `src/main/frontend/src/hooks/useInterest.ts` | TanStack hooks | rescore count query, rescore mutation, conditional status polling | self L11-17, L24-34, L85-95 |
| `src/main/frontend/src/components/InterestsDialog.tsx` | component | Re-score footer row, inline confirm, "N waiting" line, optional `TOPICS_HELP` copy fix | self L93, L112-125; `TopicRow.tsx` L343-361 |
| `src/main/frontend/src/App.css` | styles | styles for the Re-score row, if needed | self L495-507 |

### Tests

| Test File | New/Mod | Analog |
|-----------|---------|--------|
| `src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java` | new | `repository/V6InterestScoringMigrationTest.java` |
| `src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java` | new | `service/InterestPreviewServiceTest.java` |
| `src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java` | new | none (plain JUnit with a real `ThreadPoolTaskExecutor`) |
| `src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java` | new | `service/FeedPollingServiceTest.java` |
| `src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java` | new | `scheduler/FeedPollingSchedulerTest.java` + `service/InterestStatusServiceTest.java` (real registry) |
| `src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java` | new | `controller/InterestPreviewControllerTest.java` |
| `src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java` | mod | self |
| `src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java` | mod | self |
| `src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java` | mod | self L91-100 |
| `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` | mod | self L215-255, L301-351 |
| `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java` | mod | self L134-170 |
| `src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java` | mod | self L114-130 |
| `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java` | mod | self |
| `src/main/frontend/src/components/InterestsDialog.test.tsx` | mod | self L69-124, L263 |

---

## Pattern Assignments

### `event/ArticlesIngestedEvent.java` (event record, event-driven)

**Analog:** `src/main/java/org/bartram/myfeeder/event/FeedSavedEvent.java` (whole file, L1-6)
```java
package org.bartram.myfeeder.event;

import org.bartram.myfeeder.model.Feed;

/** Published after a feed is created or updated; the polling scheduler (re-)registers it. */
public record FeedSavedEvent(Feed feed) {}
```
Copy the shape: a one-line Javadoc and a record, e.g. `public record ArticlesIngestedEvent(Long feedId, List<Long> articleIds) {}`. The publisher passes `List.copyOf(newIds)`.

---

### `service/FeedPollingService.java` (modified: publish new IDs)

**Current dedup loop and success bookkeeping** (L44-60):
```java
ParsedFeed parsed = feedParser.parse(result.body(), result.contentType());
int newCount = 0;

for (ParsedArticle parsedArticle : parsed.articles()) {
    if (!articleRepository.existsByFeedIdAndGuid(feed.getId(), parsedArticle.guid())) {
        Article article = toArticle(parsedArticle, feed.getId());
        articleRepository.save(article);
        newCount++;
    }
}

feed.setLastSuccessfulPollAt(Instant.now());
feed.setErrorCount(0);
feed.setLastError(null);
feedRepository.save(feed);

log.info("Polled feed '{}': {} new articles", feed.getTitle(), newCount);
```
**The outer catch that must never see a scoring error** (L61-66): `feed.setErrorCount(feed.getErrorCount() + 1);`

**Publisher injection pattern**, from `src/main/java/org/bartram/myfeeder/service/FeedService.java`:
- L12: `import org.springframework.context.ApplicationEventPublisher;`
- L27: `private final ApplicationEventPublisher eventPublisher;` (last field; the class uses `@RequiredArgsConstructor`)
- L52: `eventPublisher.publishEvent(new FeedSavedEvent(saved));`

**What to change:** replace `int newCount` with `List<Long> newIds`, add `newIds.add(articleRepository.save(...).getId())`, keep the log using `newIds.size()`, then call `publishIngested(feed.getId(), newIds)` **after** `feedRepository.save(feed)`. Wrap the publish in its own `try { ... } catch (RuntimeException e) { log.warn(...) }` (RESEARCH Pattern 1, L243-266). No `@Transactional` is added: `pollFeed` has none, which is why the listener runs inline.

---

### `service/InterestScoringListener.java` (listener, event-driven)

**Analog:** `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java`

**Imports and class header** (L1-27):
```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedPollingScheduler {
```
**Listener annotation** (L43-46):
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onFeedSaved(FeedSavedEvent event) {
    registerFeed(event.feed());
}
```
**Catch-everything-and-WARN pattern** (L100-102):
```java
} catch (Exception e) {
    log.warn("Interval re-evaluation failed for feed {}; keeping current schedule", feedId, e);
}
```
**Body:** gate on `jevApiClient.isConfigured()`, call `scoringQueue.submitIngested(event.articleIds())`, and wrap it all in `try/catch (RuntimeException e)` that logs counts only (RESEARCH L268-277). The listener must never throw, because it runs on the polling thread.

---

### `config/InterestScoringConfig.java` (config, executor bean)

**Analog:** `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java`

**Class shape** (L35-38, L43-44):
```java
@Slf4j
@Configuration(proxyBeanMethods = false)
public class TypeSafeConfig {

    static final Duration JEV_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    TypeSafeClient typeSafeClient(TypeSafeProperties properties, RestClient.Builder restClientBuilder) {
```
Copy: `@Configuration(proxyBeanMethods = false)`, package-private `@Bean` methods, constants on the config class, and a class Javadoc explaining why the bean is app-owned (TypeSafeConfig L23-34 is the model for that Javadoc).

**Differences (no analog in the codebase, use RESEARCH Pattern 2 L288-304):**
- `@Bean(name = EXECUTOR, defaultCandidate = false)` with `public static final String EXECUTOR = "interestScoringExecutor";`, so Boot's `applicationTaskExecutor` survives (Pitfall 1).
- `setCorePoolSize(concurrency)` and `setMaxPoolSize(concurrency)` (must be equal, Pitfall 2), `setQueueCapacity(queueCapacity)`, `setThreadNamePrefix("jev-score-")`.
- Keep the default AbortPolicy. Never use DiscardPolicy (Pitfall 3).
- Values come from `MyfeederProperties.getInterest()`.

---

### `service/ScoringQueue.java` (in-memory queue, batch hand-off)

**Analog (partial):** `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java` for concurrent in-memory state:
```java
// L34-35
private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
private final Map<Long, Duration> currentIntervals = new ConcurrentHashMap<>();
```
**Explicit constructor (required, because `@Qualifier` would be dropped by Lombok; the repo has no `lombok.config`):** copy the style of `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java` L22-32:
```java
@Slf4j
@Component
public class JevApiClientImpl implements JevApiClient {

    private final TypeSafeClient client;
    private final TypeSafeProperties properties;

    public JevApiClientImpl(TypeSafeClient client, TypeSafeProperties properties) {
        this.client = client;
        this.properties = properties;
    }
```
Use `public ScoringQueue(@Qualifier(InterestScoringConfig.EXECUTOR) TaskExecutor executor, ArticleScoringService scorer, ArticleScoreStore store, ...)`. Typing the field as `TaskExecutor` lets tests pass a `SyncTaskExecutor`.

**Core logic:** RESEARCH Pattern 2 L306-340: `ConcurrentHashMap.newKeySet()` in-flight set; `catch (TaskRejectedException)` removes the ID; `finally { inFlight.remove(id); }` in `run`; `remainingCapacity()` via `instanceof ThreadPoolTaskExecutor tp`. `submitIngested(ids)` first filters the IDs through the store's eligibility SQL (`AND a.id IN (:ids)`).

---

### `service/ArticleScoringService.java` (service, outbound request then write)

**Analog:** `src/main/java/org/bartram/myfeeder/service/InterestPreviewService.java` (exact: same builders, same client, no transaction)

**Imports** (L1-13):
```java
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.springaicommunity.typesafe.question.Noul;
import org.springframework.stereotype.Service;

import java.util.Map;
```
**Class Javadoc stating "no transaction"** (L15-24): copy the wording "No transaction: a DB connection must never be held across the HTTP call and its retries." and "The service never retries; only the client's jev retry applies."

**Core build-then-judge pattern** (L53-65):
```java
Article article = articleRepository.findById(articleId)
        .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
String feedTitle = feedRepository.findById(article.getFeedId()).map(Feed::getTitle).orElse(null);

Map<String, Object> state = ArticleStateBuilder.build(feedTitle, article);
if (!ArticleStateBuilder.hasJudgeableText(state)) {
    throw new IllegalArgumentException("This article has no text to judge");
}
...
JevJudgment judgment = jevApiClient.judge(state, Map.of(key, question));
return new TopicPreviewResponse(judgment.nouls().get(key), judgment.model());
```
**Differences for the scorer (RESEARCH Pattern 3 L346-371):**
- Gates first: `jevApiClient.isConfigured()` and `interestService.isColdStart()` (`InterestService.java` L120-125). Never reimplement cold start.
- Load the candidate through `store.loadCandidate(id, cutoff, MAX_ATTEMPTS)` (predicate recheck), not `articleRepository.findById`.
- No text → `store.writeSkipped(...)` instead of throwing. Blank GUID → `writeSkipped` (`StringUtils.hasText`).
- Questions: `InterestQuestions.forRubric(profile.getProfileText(), topics)` (`InterestQuestions.java` L102-119); empty map → return.
- Answers by key: `j.scores().get(InterestQuestions.PROFILE_KEY)`, `j.nouls().get(InterestQuestions.topicKey(t.getId()))` (`InterestQuestions.java` L24-25, L50-52). `maxLevel` of -1 → `InterestQuestions.PROFILE_MAX_LEVEL` (`JevJudgment.java` L21, L27).
- `catch (RuntimeException e)` around `judge()` → permanent: `writeFailed(id, fixedText)`; transient: `log.debug` and return.
- `MAX_ATTEMPTS = 3` as a service constant, the same style as `InterestService.java` L21-27 (`public static final int ...`).
- Pitfall 11: clamp or validate `noul`/`profile_score` before writing, and fall back to `writeFailed(id, "write failed")` if `writeScored` throws.

**Profile and topic snapshot sources:** `InterestService.getProfile()` (L34-37) and `InterestService.listTopics()` (L60-62). Versions come from `InterestProfile.getVersion()` and `InterestTopic.getVersion()` (both `int`).

---

### `service/ScoringFailure.java` (classifier utility, transform)

**Analog:** `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java` L71-105. This is the existing typed mapping of Jev exceptions, with fixed text:
```java
// Jev handlers: every detail is fixed text. TypeSafe exception messages and bodies can echo
// request content (Phase 2 D-06), so they never reach a ProblemDetail.
...
@ExceptionHandler({TypeSafeBadRequestException.class, TypeSafeUnprocessableEntityException.class})
public ProblemDetail handleJevRejected(TypeSafeApiException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422),
            "Jev rejected the request (HTTP " + ex.status() + ")");
```
**Fixed-text logging precedent** (`JevApiClientImpl.java` L62-65):
```java
} catch (TypeSafeAuthenticationException | TypeSafePermissionDeniedException e) {
    // D-06: status and requestId only. The exception message and body can echo request details.
    log.warn("TypeSafe rejected the API key (status {}, requestId {})", e.status(), e.requestId());
```
**Classification (D-19 + RESEARCH table L380-385):** TRANSIENT = `TypeSafeRateLimitException`, `TypeSafeInternalServerException` (includes Overloaded), `TypeSafeApiConnectionException` (includes Timeout), `CallNotPermittedException`, `JevNotConfiguredException`, `TypeSafeAuthenticationException`, `TypeSafePermissionDeniedException`. Everything else is PERMANENT. `describe(e)` returns `simpleClassName + " (HTTP " + status + ", requestId " + requestId + ")"` for `TypeSafeApiException`, and the class name only otherwise. Never `e.getMessage()`.

---

### `repository/ArticleScoreStore.java` (repository, CRUD via `JdbcClient`)

**Analog (partial):** `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java`. There is no `JdbcClient`/`JdbcTemplate` class in `src/main` yet. Copy the SQL conventions:
```java
// L41 — quoted "read" column, COALESCE ordering with id tiebreak, named params
@Query("SELECT * FROM article WHERE (:feedId IS NULL OR feed_id = :feedId) AND (:read IS NULL OR \"read\" = :read) ... ORDER BY COALESCE(published_at, fetched_at) DESC, id DESC LIMIT :limit")

// L53 — aggregate query style
@Query("SELECT feed_id, COUNT(*) AS count FROM article WHERE \"read\" = false GROUP BY feed_id")
```
**Class shape (new to the codebase):** `@Repository` class with a constructor-injected `JdbcClient` (`@RequiredArgsConstructor` is fine here: no qualifier). One `static final String ELIGIBLE` predicate constant, reused by every query (RESEARCH Pattern 4 L401-446 has the verified SQL). Put `@Transactional` (`org.springframework.transaction.annotation.Transactional`, as used in `InterestService.java` L9, L68) on `writeScored` only, so the `article_score` insert and the topic-row inserts share one transaction.

**Critical binding rule (Pitfall 7):** bind the cutoff as `java.sql.Timestamp.from(cutoff)`, never `Instant`. `JdbcClient`/PgJDBC 42.7.13 can't infer `Instant`. The `@Query` repositories above do convert `Instant`, but that doesn't carry over to `JdbcClient`.

**Upsert semantics (verified in RESEARCH):** SCORED uses `ON CONFLICT (article_id) DO UPDATE ... WHERE article_score.status = 'FAILED'`, not `DO NOTHING`. FAILED increments `attempts` only while the row is FAILED. SKIPPED uses `DO NOTHING`. Topic rows use `INSERT ... SELECT FROM interest_topic t WHERE t.id = :topicId ON CONFLICT DO NOTHING`, and only when the score insert returned 1.

---

### `scheduler/InterestScoringSweep.java` (scheduled job, batch)

**Analog 1:** `src/main/java/org/bartram/myfeeder/service/RetentionService.java` (L13-27):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final ArticleRepository articleRepository;
    private final MyfeederProperties properties;

    @Scheduled(cron = "${myfeeder.retention.cleanup-cron}")
    public void cleanupOldContent() {
        int days = properties.getRetention().getFullContentDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        articleRepository.clearContentOlderThan(cutoff);
        log.info("Retention cleanup: cleared content older than {} days", days);
    }
}
```
Copy the property-placeholder form: `@Scheduled(fixedDelayString = "${myfeeder.interest.sweep-delay}", initialDelayString = "${myfeeder.interest.sweep-initial-delay}")`. Copy the cutoff computation too (`Instant.now().minus(days, ChronoUnit.DAYS)`). Put the class in `scheduler/` with `@Component`, like `FeedPollingScheduler`.

**Analog 2 (breaker read without tripping it):** `src/main/java/org/bartram/myfeeder/service/InterestStatusService.java` L18, L24:
```java
private final CircuitBreakerRegistry circuitBreakerRegistry;
...
circuitBreakerRegistry.circuitBreaker("jev").getState().name(),
```
Gate on `State.OPEN` / `State.FORCED_OPEN` only (HALF_OPEN runs). Wrap the whole body in `try/catch (RuntimeException e) { log.warn("Scoring sweep failed", e); }`, following `FeedPollingScheduler` L100-102. Batch = `min(scoringQueue.remainingCapacity(), props.getSweepBatchSize())` (D-16).

---

### `service/InterestRescoreService.java` (service, count + delete)

**Analog:** `src/main/java/org/bartram/myfeeder/service/InterestStatusService.java` (L8-27): a small `@Service @RequiredArgsConstructor` class with a Javadoc and delegated predicates. Guard (D-18) in the `InterestService` style, which throws `IllegalStateException` for a configuration conflict (`InterestService.java` L36):
```java
.orElseThrow(() -> new IllegalStateException("interest_profile row 1 is missing"));
```
→ `if (!jevApiClient.isConfigured() || interestService.isColdStart()) throw new IllegalStateException("<fixed text>");` This is mapped to 409 by `GlobalExceptionHandler` L57-62 (title "Configuration error"). `count()` and `rescore()` both call the store with the same `RESCORE_SCOPE` predicate (D-02). Decide whether the GET count also applies the guard; the D-18 wording guards only the POST.

---

### `controller/InterestRescoreController.java` (controller, request-response)

**Analog:** `src/main/java/org/bartram/myfeeder/controller/InterestStatusController.java` (whole file, L1-21):
```java
@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestStatusController {

    private final InterestStatusService statusService;

    @GetMapping("/status")
    public InterestStatus status() {
        return statusService.status();
    }
}
```
Add `@GetMapping("/rescore")` returning the `{count}` record and `@PostMapping("/rescore")` (no `@RequestBody`, per V5 in RESEARCH) returning `{count}` of deleted rows. There is no try/catch in the controller: exceptions go to `GlobalExceptionHandler`. The one-controller-per-concern precedent is `InterestPreviewController.java` L11-22.

---

### `config/MyfeederProperties.java` (modified)

**Self-analog** (L10-25):
```java
private Polling polling = new Polling();
private Retention retention = new Retention();
private Raindrop raindrop = new Raindrop();

@Data
public static class Polling {
    private int defaultIntervalMinutes = 15;
    private int maxIntervalMinutes = 1440;
    private int backoffThreshold = 5;
}
```
Add `private Interest interest = new Interest();` and `@Data public static class Interest { private int windowDays = 14; private int concurrency = 1; private int queueCapacity = 1000; private Duration sweepDelay = Duration.ofMinutes(2); private Duration sweepInitialDelay = Duration.ofMinutes(1); private int sweepBatchSize = 50; }`. `RetentionServiceTest` L23-32 shows how tests stub nested properties (`new MyfeederProperties.Retention()` + `when(properties.getRetention())`).

---

### `service/InterestStatus.java` + `service/InterestStatusService.java` (modified)

**Current record** (`InterestStatus.java` L17): `public record InterestStatus(boolean configured, String breakerState, boolean coldStart) {}`. Its Javadoc L3-16 lists each component. Append `long eligibleUnscored, long failed` and document them with D-11/D-12 semantics. Do not rename the first three.

**Current assembly** (`InterestStatusService.java` L21-26):
```java
public InterestStatus status() {
    return new InterestStatus(
            jevApiClient.isConfigured(),
            circuitBreakerRegistry.circuitBreaker("jev").getState().name(),
            interestService.isColdStart());
}
```
This is the only `new InterestStatus(` call site. Add the `ArticleScoreStore` collaborator (one `COUNT(*) FILTER` query returning both counts). Because `InterestStatusServiceTest` L26-30 constructs the service by hand (`new InterestStatusService(jevApiClient, registry, interestService)`), update that constructor call.

---

### `service/ArticleStateBuilder.java` (modified, D-13)

**Current loop** (L56-74):
```java
static String truncate(String text, int max) {
    if (text.length() <= max) {
        return text;
    }
    int cut = -1;
    for (int i = max - 2; i > 0; i--) {
        if (Character.isWhitespace(text.charAt(i))) {
            cut = i;
            break;
        }
    }
    if (cut <= 0) {
        cut = max - 1;
        if (Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
    }
    return text.substring(0, cut).stripTrailing() + '…';
}
```
Change only the loop bound to `i >= max / 2` (RESEARCH L565-586) and update the Javadoc L51-55 to match. Keep `'…'` (the file's existing style, not the literal `'…'`).

---

### `integration/JevApiClientImpl.java` (modified, D-15)

**Current validation block** (L50-53), inside the proxied method:
```java
public JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions) {
    requireConfigured();
    Assert.notNull(state, "state must not be null");
    Assert.notEmpty(questions, "questions must not be empty");
```
Append `questions.forEach((name, q) -> Assert.isTrue(q instanceof Noul || q instanceof Score, () -> "Unsupported question type for '" + name + "'"));`. `Noul`/`Score` are already imported (L10, L12). The `else { response.answer(name); }` branch at L75-77 then becomes unreachable. Leave or simplify it, and note which in the SUMMARY. Keeping these checks out of the breaker's failure count relies on the YAML `ignore-exceptions` addition (D-14) below.

---

### `src/main/resources/application.yaml` + `src/test/resources/application.yaml` (modified)

**Current keys to change (main):**
- L17 `timeout: 5s` → `30s` (test L10 must match; `TypeSafeConfigTest` L156-165 enforces the mirror)
- L51 `slow-call-duration-threshold: 3s` → `15s` (test L50)
- L55-60 `ignore-exceptions` list for `jev` → append `- java.lang.IllegalArgumentException` (test L54-59)
- New under `instances.jev`: `automatic-transition-from-open-to-half-open-enabled: true` (D-17)
- New **outside** `instances`: `resilience4j.circuitbreaker.circuit-breaker-aspect-order: 1` and `resilience4j.retry.retry-aspect-order: 2` (D-07)
- New `myfeeder.interest.*` block after `myfeeder.raindrop` (main L29-31 / test L29-30). In the test YAML, use `sweep-initial-delay: PT1H` (Pitfall 12).

Copy the existing comment style: a one-line `#` reason above non-obvious keys (e.g. main L54 and L71-72).

---

### `CLAUDE.md` (modified, D-07)

Line 137 currently says: "Use `@CircuitBreaker(name = "...")` (outer) + `@Retry(name = "...")` (inner) annotations". After D-07 this is actually true, because it is enforced by the `*-aspect-order` properties. Update the sentence to name those properties. **Stage only this hunk** (`git add -p`): the file has uncommitted user edits.

---

### Frontend: `src/main/frontend/src/api/interest.ts` (modified)

**Self-analog** (L20-24, L43-55):
```ts
export interface InterestStatus {
  configured: boolean
  breakerState: string
  coldStart: boolean
}
...
export const interestApi = {
  getStatus: () => apiGet<InterestStatus>('/interest/status'),
  ...
  preview: (request: TopicPreviewRequest) =>
    apiPost<TopicPreview>('/interest/preview', request),
}
```
Add `eligibleUnscored: number` and `failed: number`, add an `export interface RescoreCount { count: number }`, then add `getRescoreCount: () => apiGet<RescoreCount>('/interest/rescore')` and `rescore: () => apiPost<RescoreCount>('/interest/rescore')`. Check the `apiPost` signature in `src/api/client.ts` for a body-less call.

### Frontend: `src/main/frontend/src/hooks/useInterest.ts` (modified)

**Status query** (L10-17): add the conditional `refetchInterval: (query) => (query.state.data?.eligibleUnscored ?? 0) > 0 ? 15_000 : false`. The existing test (`InterestsDialog.test.tsx` L263) asserts exactly 2 status calls, and its mocks omit the new fields, so the interval stays off there.

**Mutation template with inline error + status invalidation** (L85-95):
```ts
export function useDeleteInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => interestApi.deleteTopic(id),
    meta: { inlineError: true },
    onSuccess: (_result: void, id: number) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) => old?.filter((t) => t.id !== id))
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    },
  })
}
```
→ `useRescoreUnread()` with `meta: { inlineError: true }`, whose `onSuccess` invalidates `['interest', 'status']`. Add `useRescoreCount(enabled: boolean)`: `useQuery({ queryKey: ['interest', 'rescore-count'], queryFn: interestApi.getRescoreCount, enabled, staleTime: 0 })`. Add a JSDoc line on each, as the existing hooks have.

### Frontend: `src/main/frontend/src/components/InterestsDialog.tsx` (modified)

**Replace the footer note** (L93): `<p className="interests-note">Profile and topic changes apply to newly arriving articles.</p>`

**Dirty state is already in scope** (L55-56): `profileDirty`, `dirtyTopics`. For D-04, disable with `title="Save your changes first"` when `profileDirty || dirtyTopics > 0`.

**Inline confirm template** (L112-125, dialog-level):
```tsx
<div className="dialog-actions interests-confirm">
  <span>
    Discard unsaved changes? You have unsaved edits to {describeUnsaved(profileDirty, dirtyTopics)}.
  </span>
  <span className="interests-confirm-actions">
    <button className="btn-secondary" onClick={() => setConfirmingClose(false)}>
      Keep editing
    </button>
    <button className="btn-primary interests-danger" onClick={onClose}>
      Discard changes
    </button>
  </span>
</div>
```
**Row-level confirm with pending label** (`src/main/frontend/src/components/TopicRow.tsx` L343-361):
```tsx
<button
  className="btn-primary interests-danger"
  aria-label={`Confirm delete topic: ${label}`}
  disabled={remove.isPending}
  onClick={handleDelete}
>
  {remove.isPending ? 'Deleting…' : 'Delete topic'}
</button>
```
State toggle: `const [confirmingDelete, setConfirmingDelete] = useState(false)` (TopicRow L234). The mutate-with-callback style is `remove.mutate(row.id, { onSuccess: onDeleted })` (TopicRow L285-287).

**Status-driven UI** (`InterestNotices` L141-171): read `status.configured`, `OPEN_BREAKER_STATES.includes(status.breakerState)` and `status.coldStart` the same way. Show "N waiting to be scored" only when `configured && !coldStart && eligibleUnscored > 0`. Put this in a small sub-component (e.g. `RescoreFooter`) next to the existing ones, rather than inlining it in `InterestsDialogBody`.

**Optional fold-in:** `TOPICS_HELP` (L30-31): change "primarily about" to "substantially about".

### Frontend: `src/main/frontend/src/App.css` (modified, only if needed)

Reuse these existing classes (L495-507): `.interests-note` (muted 13px footer text), `.interests-confirm` (space-between flex row), `.interests-confirm-actions` (8px gap), `.btn-primary.interests-danger`. Add new rules next to them, using the same `var(--text-muted)` / `var(--text-secondary)` tokens.

---

## Test Pattern Assignments

### `repository/ArticleScoreStoreTest.java` (new)
**Analog:** `src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java`
- Header (L16-35): `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)`, `@Autowired private JdbcTemplate jdbc;`, and a `@BeforeEach` that inserts a feed with `RETURNING id`. Change the import to `@Import({TestcontainersConfiguration.class, ArticleScoreStore.class})`, because the slice does not scan custom `@Repository` classes.
- Javadoc note (L17-19): "Postgres aborts the test transaction after a constraint error, so each method triggers at most one expected violation, as its last statement."
- Helpers to copy (L200-227): `insertArticle(guid)`, `insertTopic(name)`, `insertScore(articleId, status)`, `insertTopicScore(...)`. Extend `insertArticle` with `read`, `published_at` and `fetched_at` parameters for the window/newest-first cases.
- Assertion idiom: `jdbc.queryForObject("SELECT count(*) FROM article_score", Integer.class)`.

### `service/ArticleScoringServiceTest.java` (new)
**Analog:** `src/test/java/org/bartram/myfeeder/service/InterestPreviewServiceTest.java`
- L37-46: `@ExtendWith(MockitoExtension.class)`, `@Mock JevApiClient`, `@InjectMocks`, and `@Captor ArgumentCaptor<Map<String, Question>> questionsCaptor`.
- L56-61: verify `judge(stateCaptor.capture(), questionsCaptor.capture())`, and assert equality with `ArticleStateBuilder.build(...)` / `InterestQuestions.*` outputs, which proves the builders are shared.
- L86-90: `verifyNoInteractions(jevApiClient)` for the gate, skip and cold-start cases.
- L144-148: `CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("jev"))`, used to build a transient failure.
- TypeSafe exception constructors (`InterestPreviewControllerTest.java` L77, L99, L104): `new TypeSafeBadRequestException("bad", 400, "{}", new HttpHeaders(), ENDPOINT)`, `new TypeSafeApiConnectionException("timed out", new RuntimeException("io"))`.

### `service/ScoringIsolationTest.java` (new, success criterion 2)
**Analog:** `src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java`
- L27-36 mock set plus `pollingService.pollFeed(1L)` and the L95-101 `ArgumentCaptor<Feed>` assertion on `getErrorCount()`.
- Compose the real `FeedPollingService` (explicit constructor) with a real `InterestScoringListener`, a real `ScoringQueue` and a real `ThreadPoolTaskExecutor`. Use a `CountDownLatch`-blocked mocked `JevApiClient` and a lambda `ApplicationEventPublisher` that calls the listener inline.

### `FeedPollingServiceTest.java` (modified, Pitfall 4)
Add `@Mock private ApplicationEventPublisher eventPublisher;` to the L30-33 mock list. Stub `articleRepository.save` with an ID-assigning `thenAnswer`, or `shouldSaveOnlyNewArticles` (L73-102) will NPE into the error branch. Add tests for "publishes one event with only new IDs" and "no event when 0 new".

### `scheduler/InterestScoringSweepTest.java` (new)
**Analogs:** `scheduler/FeedPollingSchedulerTest.java` L30-49 (`@Mock MyfeederProperties` + `lenient().when(properties.getPolling()).thenReturn(polling)` + hand-constructed subject in `@BeforeEach`), and `service/InterestStatusServiceTest.java` L23-30, L45-58 (a real `CircuitBreakerRegistry.ofDefaults()`, then `transitionToOpenState()` / `transitionToForcedOpenState()` / `transitionToHalfOpenState()`).

### `controller/InterestRescoreControllerTest.java` (new)
**Analog:** `src/test/java/org/bartram/myfeeder/controller/InterestPreviewControllerTest.java` L33-42:
```java
@WebMvcTest(InterestPreviewController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
class InterestPreviewControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private InterestPreviewService previewService;
```
409 case: `when(service.rescore()).thenThrow(new IllegalStateException("..."))` → `status().isConflict()` and `jsonPath("$.title").value("Configuration error")`.

### `InterestStatusServiceTest.java` (modified)
Update the L29 constructor call, and add a `@Mock ArticleScoreStore` that returns counts. Add assertions for `eligibleUnscored()` / `failed()`.

### `ArticleStateBuilderTest.java` (modified, D-13)
Copy `neverSplitsSurrogatePair` (L91-100), which uses `summaryOf(text)` plus the `ELLIPSIS` assertions. Add CJK (`"标题 " + "字".repeat(2000)`) and long-URL cases that assert `summary.length() == ArticleStateBuilder.MAX_SUMMARY_CHARS`.

### `JevResilienceTest.java` (modified, D-07/D-14/D-15/D-17)
- Rewrite `breakerOpensAtMinimumCallsAndShortCircuits` (L226-255): 10 logical calls → 30 hits and 10 failures → OPEN; the 11th call → `CallNotPermittedException` with hits still 30. Remove the "Retry is the outer aspect" comment at L235.
- `mainYamlJevInstancesBindAsSpecified` (L301-342): `Duration.ofSeconds(3)` at L311 → 15. Add `ignored.test(new IllegalArgumentException("x"))` true (next to L316-323). Assert `cb.isAutomaticTransitionFromOpenToHalfOpenEnabled()`.
- New cases modeled on `notConfiguredIsNeitherRetriedNorRecorded` (L215-224): `judge(null, questions())` and a `Choice` question → `IllegalArgumentException`, `stub.hits()` zero, `getNumberOfFailedCalls()` zero. Helpers: `questions()` L353-369, `state()` L371-376, `jevBreaker(ctx)` L378-380.
- Aspect-order assertion: `ctx.getBean(CircuitBreakerAspect.class).getOrder() < ctx.getBean(RetryAspect.class).getOrder()` inside `runner.run(...)`. Extend `testYamlMirrorsMainJevInstances` / `jevProperties` (L344-351, L397-406) so the two `*-aspect-order` keys are also compared.

### `TypeSafeConfigTest.java` (modified)
L146: `Duration.ofSeconds(5)` → `Duration.ofSeconds(30)`. The mirror test (L156-165) passes once both YAMLs are changed.

### `InterestApiIntegrationTest.java` (modified)
Extend `statusReportsKeylessClosedAndColdStart` (L114-130) with `jsonPath("$.eligibleUnscored")` / `jsonPath("$.failed")`. Add Re-score GET/POST cases, seeding rows with `jdbcTemplate` the way `keylessPreviewIs503ThroughTheFullStack` does (L133-140). Clean up in `@BeforeEach` (L37-43).

### `MyfeederApplicationTests.java` (modified, Pitfall 1)
Add `@Autowired ApplicationContext ctx` and `assertThat(ctx.containsBean("applicationTaskExecutor")).isTrue()`, next to the existing bean-not-null assertions (L25-32).

### `InterestsDialog.test.tsx` (modified)
Harness (L69-124): `route(method, url, handler)`, the `calls` recorder, `status(overrides)` (L77-82; widen its `Partial<...>` type with the new fields), and `renderDialog`. Add routes `GET /api/interest/rescore` and `POST /api/interest/rescore`. Filter calls the way `statusFetches()` does (L69-71). Keep the L263 "exactly 2 status calls" assertion green.

---

## Shared Patterns

### Constructor injection
**Source:** every `@Service` / `@Component` (e.g. `FeedPollingService.java` L16-24).
**Apply to:** all new beans **except** `ScoringQueue` (and anything else injecting the qualified executor), which needs an explicit constructor with `@Qualifier(InterestScoringConfig.EXECUTOR)` (style: `JevApiClientImpl.java` L29-32).
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedPollingService {
    private final FeedRepository feedRepository;
```

### Fixed-text errors (never echo TypeSafe messages)
**Source:** `GlobalExceptionHandler.java` L71-72 comment, `JevApiClientImpl.java` L63-64 (status + requestId only), `InterestService.java` L127 ("Fixed-text messages only").
**Apply to:** `ScoringFailure.describe`, `article_score.last_error`, every WARN/DEBUG log in the listener, queue, scorer and sweep, and the D-18 409 message.

### Exception → HTTP mapping (no controller try/catch)
**Source:** `GlobalExceptionHandler.java` L50-62: `IllegalArgumentException` → 400 "Bad Request", `IllegalStateException` → 409 "Configuration error".
**Apply to:** `InterestRescoreController` / `InterestRescoreService` (D-18 guard throws `IllegalStateException`).

### Single "configured" and "cold start" predicates
**Source:** `JevApiClient.isConfigured()` (`JevApiClientImpl.java` L34-38, not breaker-wrapped) and `InterestService.isColdStart()` (`InterestService.java` L115-125).
**Apply to:** listener, sweep, scorer (both gates) and rescore service. Never reimplement either.

### Never-throw background boundaries
**Source:** `FeedPollingScheduler.pollAndAdjust` L86-102 (a separate try/catch so a secondary failure keeps the primary schedule).
**Apply to:** the `FeedPollingService.publishIngested` wrapper, the `InterestScoringListener` body, `ScoringQueue.run`, and `InterestScoringSweep.sweep`.

### Resilience annotations only on the API-client bean
**Source:** `JevApiClientImpl.java` L46-49 (`@CircuitBreaker(name = "jev")` + `@Retry(name = "jev")`, no fallback).
**Apply to:** no new annotations anywhere. The scorer calls `judge()` directly and never pre-checks the breaker (RESEARCH anti-pattern, Pitfall 5).

### YAML mirror discipline
**Source:** `TypeSafeConfigTest.testYamlMirrorsMainTypeSafePinsWithoutKey` (L155-170) and `JevResilienceTest.testYamlMirrorsMainJevInstances` (L344-351).
**Apply to:** every YAML key changed or added in this phase. Edit both files in the same task.

### Frontend mutation conventions
**Source:** `useInterest.ts` L23-34, L85-95: `meta: { inlineError: true }`, invalidate `['interest', 'status']`, no retry of billed or destructive calls.
**Apply to:** `useRescoreUnread`.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `config/InterestScoringConfig.java` (executor part) | config | n/a | No `TaskExecutor`/`ThreadPoolTaskExecutor` bean exists yet. Use RESEARCH Pattern 2 (`@Bean(defaultCandidate = false)`, core == max) |
| `repository/ArticleScoreStore.java` (`JdbcClient` part) | repository | CRUD | No `JdbcClient`/`JdbcTemplate` class in `src/main`. SQL style comes from `ArticleRepository`; mechanics from RESEARCH Pattern 4 (and bind the cutoff as `Timestamp`) |
| `service/ScoringQueueTest.java` | test | batch | No executor tests exist. Use a real 1-thread `ThreadPoolTaskExecutor` with capacity 1 and a latch-blocked task (RESEARCH Pitfall 3) |

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/**`, `src/test/java/org/bartram/myfeeder/**`, `src/main/resources`, `src/test/resources`, `src/main/frontend/src/{api,hooks,components}`, `App.css`, root `CLAUDE.md`
**Files scanned:** ~45 read or grepped (all git-tracked)
**Pattern extraction date:** 2026-09-23
