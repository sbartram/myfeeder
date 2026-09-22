# Architecture Research

**Domain:** Per-article interest scoring (TypeSafe Jev) inside an existing Spring Boot 4 + React feed reader (myfeeder, brownfield)
**Researched:** 2026-09-22
**Confidence:** HIGH for integration points (read from the actual myfeeder source and from the `spring-ai-typesafe` v0.1.0 source at its git tag). MEDIUM for the blend formula constants (a design choice that needs tuning against real data).

---

## Standard Architecture

### System Overview

```
                        ┌──────────────────────────────────────────────────────────────┐
                        │ React SPA                                                    │
                        │  /priority route ─ PriorityArticles ─ ArticleList(priority)  │
                        │  InterestBadge · ThumbsButtons · InterestSettings dialog     │
                        │  useArticles(priority) · useArticleFeedback · useInterest    │
                        └───────────────┬──────────────────────────────────────────────┘
                                        │ HTTP/JSON
┌───────────────────────────────────────▼──────────────────────────────────────────────────┐
│ Controllers                                                                              │
│  ArticleController (+ GET /api/articles/priority, PUT/DELETE /api/articles/{id}/feedback)│
│  InterestController (/api/interest/profile, /topics, /status)                            │
└──────────┬───────────────────────────────┬───────────────────────────────┬───────────────┘
           │ read path                      │ config path                   │
┌──────────▼───────────┐   ┌────────────────▼────────────┐                  │
│ ArticleService       │   │ InterestProfileService      │                  │
│  + PriorityService   │   │  profile/topic CRUD,        │                  │
│  enrich(interestScore│   │  version bump on text edit  │                  │
│  , feedback)         │   └─────────────────────────────┘                  │
└──────────┬───────────┘                                                    │
           │ SQL (blend at query time)                                      │
┌──────────▼──────────────────────────────────────────────┐                 │
│ InterestScoreQueries (NamedParameterJdbcTemplate)       │                 │
│  one shared CTE: learned deltas → effective weights →   │                 │
│  blended score; priority page + cursor + enrich         │                 │
└──────────┬──────────────────────────────────────────────┘                 │
           │                                                                │
═══════════╪════════════════════════ WRITE / INGEST PATH ═══════════════════╪═════════════
           │                                                                │
  FeedPollingScheduler (1 scheduler thread) ──► FeedPollingService.pollFeed()
                                                  │ inserts new articles (auto-commit)
                                                  │ publishEvent(ArticlesIngestedEvent(feedId, newIds))
                                                  ▼
                                  InterestScoringListener  (@TransactionalEventListener
                                   AFTER_COMMIT, fallbackExecution=true; never throws;
                                   returns in microseconds)
                                                  │ submit(ids)            ▲ submit(ids)
                                                  ▼                        │
                         interestScoringExecutor (2 platform threads,      │
                          bounded queue, discard-on-full)          InterestBackfillJob
                                                  │                 (@Scheduled fixedDelay;
                                                  ▼                  SELECT unscored ids only)
                                  ArticleScoringService.score(articleId)
                                   load article+feed+profile+topics → build state/questions
                                                  │
                                                  ▼
                                  JevApiClientImpl  (@CircuitBreaker "jev" outer,
                                   @Retry "jev" inner; ObjectProvider<TypeSafeClient>)
                                                  │ systemOne(stateMap, questions)
                                                  ▼
                                        TypeSafe Jev API (~300 ms, 1200 req/min)
                                                  │
                                  ArticleScoringService persists raw outputs
                                   (INSERT … ON CONFLICT DO NOTHING, one tx)
                                                  ▼
            PostgreSQL: article_score · article_topic_score · interest_profile ·
                        interest_topic · article_feedback   (Flyway V6)
```

### Component Responsibilities

| Component | Package | Responsibility | Talks to |
|-----------|---------|----------------|----------|
| `ArticlesIngestedEvent(Long feedId, List<Long> articleIds)` | `event/` | Carries the IDs of genuinely new articles out of a poll. IDs only, never entities, so the worker always re-reads current state. | Published by `FeedPollingService`; consumed by `InterestScoringListener` |
| `FeedPollingService` (modified) | `service/` | Collects `newIds` in the existing dedup loop and publishes one event per poll when the list is non-empty. No other change. | `ApplicationEventPublisher` |
| `InterestScoringListener` | `service/` (or `scheduler/`) | `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. Returns immediately if Jev isn't configured, otherwise hands the IDs to `ArticleScoringService.submit()`. Wraps everything in try/catch so it **never throws**. | `ArticleScoringService` |
| `interestScoringExecutor` bean | `config/InterestScoringConfig` | Dedicated `ThreadPoolTaskExecutor`: core = max = `myfeeder.interest.concurrency` (default 2), queue capacity around 1000, `DiscardPolicy` with a warning log. Thread prefix `jev-`. | Used only by `ArticleScoringService` |
| `ArticleScoringService` | `service/` | `submit(ids)` does an in-flight dedup (`ConcurrentHashMap.newKeySet()`) and queues one task per ID. `score(id)` loads the article, feed title, profile and topics, builds the state map and question map, calls the client, classifies failures and persists raw outputs. Idempotent. | `JevApiClient`, `ArticleRepository`, `FeedRepository`, `InterestProfileService`, `ArticleScoreWriter` |
| `JevApiClient` / `JevApiClientImpl` | `integration/` | The only class that touches `TypeSafeClient`. Carries `@CircuitBreaker(name="jev")` (outer) and `@Retry(name="jev")` (inner). Holds `ObjectProvider<TypeSafeClient>`, exposes `isConfigured()`, and throws `JevNotConfiguredException` when the bean is absent. No fallback method: typed exceptions propagate so the service can classify them. | TypeSafe SDK |
| `InterestBackfillJob` | `service/` or `scheduler/` | `@Scheduled(fixedDelay)`. Skips when not configured or when the `jev` breaker is OPEN. Otherwise it SELECTs up to N unscored unread IDs (cheap) and calls `submit()`. It **never calls Jev on the scheduler thread**. The same job does the one-time launch backfill. | `ArticleScoringService`, `CircuitBreakerRegistry`, `InterestScoreQueries` |
| `InterestProfileService` | `service/` | Profile text and topic CRUD with validation (weight range, topic cap). Bumps `version` when profile text or a topic description changes. Weight edits do **not** bump version, because weights apply at query time. | `InterestProfileRepository`, `InterestTopicRepository` |
| `InterestScoreQueries` | `repository/` | A `@Repository` using `NamedParameterJdbcTemplate`. Owns the single blend CTE and three queries: the priority page (first page and after-cursor), the blended score of one article (for the cursor), and enrichment for a list of IDs (score + feedback vote). | PostgreSQL |
| `ArticleScoreWriter` | `repository/` | Write-once inserts into `article_score` and `article_topic_score` via `INSERT … ON CONFLICT (…) DO NOTHING` in one `@Transactional` method. | PostgreSQL |
| `ArticleFeedbackRepository` | `repository/` | `@Modifying @Query` upsert and delete of the thumbs vote. | PostgreSQL |
| `PriorityService` | `service/` | Resolves the cursor article's (blended score, date), then runs the keyset query. | `InterestScoreQueries` |
| `ArticleService` (modified) | `service/` | After any list or single-article fetch, calls `enrich(List<Article>)` to fill the `@Transient interestScore` and `@Transient feedback` fields. | `InterestScoreQueries` |
| `InterestController` | `controller/` | `/api/interest/status` (configured, breaker state, unscored count), `/api/interest/profile` (GET/PUT), `/api/interest/topics` (GET/POST/PUT/DELETE). | `InterestProfileService`, `JevApiClient` |
| `ArticleController` (modified) | `controller/` | `GET /api/articles/priority?limit&before` → `PaginatedResponse<Article>`; `PUT /api/articles/{id}/feedback {vote: 1\|-1}`; `DELETE /api/articles/{id}/feedback`. | `PriorityService`, `ArticleService` |

---

## Recommended Project Structure

This follows the existing layer-first packages (see `.planning/codebase/STRUCTURE.md`). Do **not** add a feature package; every existing integration (Raindrop) is spread across the layer packages.

```
src/main/java/org/bartram/myfeeder/
├── config/
│   ├── MyfeederProperties.java          # + Interest nested class (concurrency, queue, backfill, blend constants)
│   └── InterestScoringConfig.java       # interestScoringExecutor bean
├── event/
│   └── ArticlesIngestedEvent.java       # record(Long feedId, List<Long> articleIds)
├── integration/
│   ├── JevApiClient.java                # interface: isConfigured(), judge(state, questions)
│   ├── JevApiClientImpl.java            # @CircuitBreaker + @Retry, ObjectProvider<TypeSafeClient>
│   └── JevNotConfiguredException.java   # listed in ignore-exceptions (CB + retry)
├── model/
│   ├── Article.java                     # + @Transient Double interestScore, @Transient Integer feedback
│   ├── InterestProfile.java             # singleton row (id = 1)
│   └── InterestTopic.java
├── repository/
│   ├── InterestProfileRepository.java
│   ├── InterestTopicRepository.java
│   ├── ArticleFeedbackRepository.java   # @Modifying upsert/delete
│   ├── ArticleScoreWriter.java          # JdbcTemplate, ON CONFLICT DO NOTHING
│   └── InterestScoreQueries.java        # the blend CTE + priority/cursor/enrich queries
├── service/
│   ├── InterestProfileService.java
│   ├── ArticleScoringService.java       # submit(), score(), prompt building, failure classification
│   ├── InterestScoringListener.java
│   ├── InterestBackfillJob.java
│   └── PriorityService.java
└── controller/
    ├── InterestController.java
    └── (ArticleController additions) + FeedbackRequest, TopicRequest, ProfileRequest records

src/main/resources/db/migration/
└── V6__interest_scoring.sql             # all five tables in one migration

src/main/frontend/src/
├── api/interest.ts                      # profile/topics/status + feedback calls
├── api/articles.ts                      # list(): priority branch → /articles/priority
├── hooks/useInterest.ts                 # profile/topic queries + mutations
├── hooks/useArticles.ts                 # + useArticleFeedback mutation
├── components/InterestBadge.tsx (+ .test.tsx)
├── components/ThumbsButtons.tsx (+ .test.tsx)
├── components/InterestSettings.tsx      # profile textarea + topic table (own dialog/section, not inside the 233-line SettingsDialog)
└── types/index.ts                       # Article.interestScore, Article.feedback, ArticleFilters.priority
```

### Structure Rationale

- **Put the score tables' SQL in `repository/` and use JdbcTemplate.** The blend is a multi-CTE query that takes configuration parameters (η, clamp, profile weight) and returns a computed column. `@Query` would force four near-duplicate string variants of the CTE. One class holding one CTE constant is the single source of truth for "what is the score".
- **Keep `JevApiClientImpl` in `integration/`, separate from `ArticleScoringService`.** This mirrors `RaindropApiClientImpl`: only the HTTP call sits inside the breaker, and business logic (already scored? configured? how to classify a failure?) runs outside it. Keeping them in separate beans also avoids the self-invocation AOP bypass that CLAUDE.md warns about.
- **Put the executor in `config/`.** It is infrastructure, and a named bean is easy to swap in tests for a `SyncTaskExecutor`.

---

## Architectural Patterns

### Pattern 1: Publish after ingest, hand off immediately (the hook point)

**What:** `FeedPollingService.pollFeed` collects the new IDs and publishes `ArticlesIngestedEvent`. The listener uses the same annotation the codebase already uses (`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`). Its only job is to submit to a dedicated bounded executor.

**Why this and not a direct call:** A direct call from `FeedPollingService` to a scoring service couples ingest to an optional integration. It also invites a future "just call Jev inline" regression.

**Why this and not `@Async` on the listener:** The app has no `@EnableAsync`. Enabling it globally is a wider change than needed. Explicit `executor.execute(...)` makes rejection handling visible (discard + log) and is trivially testable.

**Two codebase facts drive the design (both HIGH confidence, from source):**

1. `pollFeed` is **not** `@Transactional`. Each `articleRepository.save` auto-commits, so with `fallbackExecution = true` the listener runs **synchronously on the polling thread** as soon as the event is published. The listener must return in microseconds.
2. Spring Boot's auto-configured `ThreadPoolTaskScheduler` has **one thread** unless `spring.task.scheduling.pool.size` is set, and myfeeder sets neither that nor virtual threads. Every feed's polling task and every `@Scheduled` job (Retention, and the new backfill) share one thread. Anything slow on it delays *all* feed polling.

`publishEvent` sits inside `pollFeed`'s `try`. A listener that throws would land in the `catch`, increment `feed.errorCount`, and eventually trigger polling backoff for a healthy feed. So the listener catches everything.

**Example:**
```java
// FeedPollingService (inside the existing loop)
List<Long> newIds = new ArrayList<>();
...
Article saved = articleRepository.save(toArticle(parsedArticle, feed.getId()));
newIds.add(saved.getId());
...
feedRepository.save(feed);                       // existing success bookkeeping
if (!newIds.isEmpty()) {
    eventPublisher.publishEvent(new ArticlesIngestedEvent(feed.getId(), List.copyOf(newIds)));
}

// InterestScoringListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onArticlesIngested(ArticlesIngestedEvent event) {
    try {
        if (!jevApiClient.isConfigured()) return;          // optional integration: no key → no-op
        scoringService.submit(event.articleIds());          // non-blocking enqueue
    } catch (Exception e) {                                 // never let scoring fail a poll
        log.warn("Could not enqueue {} articles for scoring", event.articleIds().size(), e);
    }
}
```

**Trade-offs:** With a platform pool of 2 plus a bounded queue, a burst (an OPML import of 100 feeds all polling at once) can overflow the queue. That is fine, because discarded IDs have no `article_score` row and the backfill job picks them up later. The backfill is the safety net, so the fast path can afford to drop work. **Do not enable `spring.threads.virtual.enabled` in this milestone.** It silently swaps the `TaskScheduler` implementation under `FeedPollingScheduler` to `SimpleAsyncTaskScheduler`, which is an unrelated behavior change with its own risk. Two platform threads are plenty: around 300 ms per call gives about 6 calls/s, far under the published 1,200 req/min limit.

### Pattern 2: Optional integration via `ObjectProvider<TypeSafeClient>` + Resilience4j on the client bean

**What:** `JevApiClientImpl` mirrors `RaindropApiClientImpl`. The auto-configured `TypeSafeClient` bean exists only when an API key is set, so the impl injects `ObjectProvider<TypeSafeClient>` and resolves it once.

**Starter facts that shape this (read from `spring-ai-typesafe` v0.1.0 source):**

- The auto-config is `@ConditionalOnProperty("spring.ai.typesafe.api-key")` **plus** `Assert.state(hasText(apiKey))`. A **present-but-blank key fails context startup.** It does not skip. The repo's own test is named `declinesToStartOnABlankApiKeyRatherThanBuildingAClientThatCannotCall`.
  - So **never** write `spring.ai.typesafe.api-key: ${MYFEEDER_TYPESAFE_API_KEY:}` in `application.yaml`.
  - And **do not copy the Raindrop Helm pattern**, which always renders the env var, possibly as `""`.
  - Render `SPRING_AI_TYPESAFE_API_KEY` in `app-deployment.yaml` only inside `{{- if .Values.secrets.typesafeApiKey }}`, and have `deploy.sh` default `MYFEEDER_TYPESAFE_API_KEY` to empty with a warning, exactly like Raindrop.
- The starter clones the context's `RestClient.Builder`, so the existing `RestClientCustomizer` (User-Agent) still applies. It installs its own `JdkClientHttpRequestFactory` with `spring.ai.typesafe.timeout`, so the global `spring.http.client.read-timeout: 30s` does **not** apply. Set `spring.ai.typesafe.timeout: 5s` explicitly.
- The SDK has **its own retry policy**: 2 retries on 408/429/5xx/connection errors within a 30 s budget. Stacked under `@Retry(max-attempts: 3)` that becomes up to 9 HTTP attempts per article. **Set `spring.ai.typesafe.retry.max-retries: 0`** and let Resilience4j own retries, which keeps the project convention and gives one place to tune. The trade-off is losing the SDK's `retry-after-ms` handling for 429s, which is acceptable at 2-way concurrency.
- The starter depends only on `typesafe-java-sdk` and `spring-boot-starter`, **not** on Spring AI. The "Spring AI 2.0.1+" requirement applies only to `typesafe-spring-ai` (advisors/RAG), which this milestone doesn't need. The project's `spring-ai 2.0.0-M2` is therefore not a blocker. The starter was built against Boot 4.0.7 and the project runs 4.0.3, so verify in Phase 1.

**Resilience4j configuration:**
```yaml
spring:
  ai:
    typesafe:
      timeout: 5s
      retry:
        max-retries: 0          # Resilience4j @Retry is the single retry layer
      # api-key: NOT set here. Supplied only via SPRING_AI_TYPESAFE_API_KEY when present.

resilience4j:
  circuitbreaker:
    instances:
      jev:
        failure-rate-threshold: 50
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        ignore-exceptions:                         # per-article content problems must not open the breaker
          - org.bartram.myfeeder.integration.JevNotConfiguredException
          - org.springaicommunity.typesafe.exception.TypeSafeBadRequestException
          - org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException
  retry:
    instances:
      jev:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:                          # allow-list: only transient failures
          - org.springaicommunity.typesafe.exception.TypeSafeInternalServerException
          - org.springaicommunity.typesafe.exception.TypeSafeRateLimitException
          - org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException
```

401/403 (bad or missing key at the API) are deliberately **recorded** by the breaker: a broken key opens it, and the backfill then stops hammering the API.

**No fallback method.** Raindrop's fallback exists to translate failures into HTTP status codes for a user-facing request. Scoring has no HTTP caller. `ArticleScoringService` catches and classifies:

| Exception | Classification | Persisted state |
|-----------|----------------|-----------------|
| `TypeSafeBadRequestException`, `TypeSafeUnprocessableEntityException` | Permanent for this article | `article_score` row with `status='FAILED'`, `attempts+1`. Retried at most `max-attempts` times by backfill, then left. |
| `CallNotPermittedException` (breaker open), 5xx, 429, connection/timeout, `JevNotConfiguredException` | Transient, not the article's fault | **No row written.** The backfill retries later. |
| Success | — | `status='SCORED'` plus topic rows, in one transaction |

### Pattern 3: Write-once raw outputs, blend at query time

**What:** Jev outputs are stored verbatim and never updated. The blended score is a SQL expression evaluated per request. Weight edits and thumbs therefore re-rank instantly with zero Jev calls, and raw data is never lost to a formula change.

**Why separate tables instead of columns on `article` (important):** `ArticleService.updateState` does `findById` → mutate → `articleRepository.save(article)`. Spring Data JDBC's `save` writes **every column**. If score columns lived on `article`, an async scorer writing `article.interest_*` between a user's load and save would have its values overwritten with `NULL`. That is a lost update that is hard to reproduce. Separate tables make the race impossible. They also keep the `Article` aggregate and its `SELECT *` queries unchanged.

**Why not Spring Data JDBC aggregates for the score/feedback tables:** Their primary keys are **assigned** (`article_id`), not generated. Spring Data JDBC's `save()` treats a non-null ID as an UPDATE, which fails on a missing row unless the entity implements `Persistable#isNew`. Write-once and upsert semantics are clearer as explicit SQL (`INSERT … ON CONFLICT`), and they give idempotency for free.

#### Data model: `V6__interest_scoring.sql`

```sql
-- Singleton profile (single-user app). Seeded so the entity is always UPDATE-able.
CREATE TABLE interest_profile (
    id           SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    profile_text TEXT        NOT NULL DEFAULT '',
    version      INTEGER     NOT NULL DEFAULT 1,     -- bumped when profile_text changes
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO interest_profile (id) VALUES (1);

CREATE TABLE interest_topic (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT             NOT NULL,
    description TEXT             NOT NULL,           -- goes into the Noul instruction
    weight      DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (weight BETWEEN -3 AND 3),
    version     INTEGER          NOT NULL DEFAULT 1, -- bumped when description changes
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- One row per judged article. Write-once on SCORED; FAILED rows count attempts.
CREATE TABLE article_score (
    article_id         BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    status             TEXT             NOT NULL CHECK (status IN ('SCORED', 'FAILED')),
    profile_score      DOUBLE PRECISION,            -- raw Jev Score value, zero-indexed: [0, profile_max_level]
    profile_max_level  SMALLINT,                    -- rubric levels - 1 at scoring time (normalization)
    profile_confidence DOUBLE PRECISION,
    profile_version    INTEGER,
    model              TEXT,                        -- SystemOneResponse.model
    request_id         TEXT,                        -- x-typesafe-request-id (support/debug)
    attempts           SMALLINT         NOT NULL DEFAULT 1,
    last_error         TEXT,
    scored_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE TABLE article_topic_score (
    article_id    BIGINT           NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    topic_id      BIGINT           NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,
    noul          DOUBLE PRECISION NOT NULL CHECK (noul BETWEEN 0 AND 1),
    topic_version INTEGER          NOT NULL,
    PRIMARY KEY (article_id, topic_id)
);
CREATE INDEX idx_article_topic_score_topic ON article_topic_score(topic_id);

CREATE TABLE article_feedback (
    article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    vote       SMALLINT    NOT NULL CHECK (vote IN (-1, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Notes:
- **Profile confidence is stored but not used in the blend** for this milestone. It is cheap to keep and useful for a later "low confidence" indicator.
- **Per-level probabilities and the legend are not stored.** Nothing in scope consumes them, so they are YAGNI.
- **`ON DELETE CASCADE` everywhere.** Feed delete → article delete → scores and feedback go too. Topic delete → its nouls vanish, and its contribution disappears from every article instantly, which is the desired behavior.
- **`profile_version` and `topic_version` are recorded for provenance only.** Re-scoring is out of scope. The versions make a future "rescore stale articles" a pure query.

#### Jev request shape (in `ArticleScoringService`)

- **State:** a `Map`, per the SDK's `systemOne(Map<String,?> state, …)`. Keys: `feed` (feed title), `title`, `summary`.
  - The summary is **plain text**: HTML stripped (jsoup is on the classpath via Readability4J; verify) and truncated to about 1,500 characters.
  - Omit null keys, since `Map.of` rejects nulls. Never pass a bare number or boolean, which gets a 422.
- **Questions:** one map per call.
  - `"profile"` → a `Score` whose instruction embeds the profile text, with a fixed 5-level rubric: "Not relevant", "Slightly", "Somewhat", "Relevant", "Highly relevant". `profile_max_level = 4`.
  - `"topic_<id>"` → a `Noul` per topic, with instruction = topic description and explicit `whenTrue`/`whenFalse` text.
  - Parse the IDs back from the keys.
- **Empty profile text:** omit the `profile` question. With zero topics too, skip the call entirely and write nothing (not configured in the product sense).
- **Topic cap:** about 25 topics, validated in `InterestProfileService`. Extra questions are cheap, but the rubric should stay legible.

### Pattern 4: The blend (single SQL source of truth)

**Formula** (MEDIUM confidence; tune on real data, constants in `myfeeder.interest.blend.*`):

```
p        = profile_score / profile_max_level                      ∈ [0, 1]   (0 when no profile question)
m_i      = GREATEST(0, (noul_i − 0.5) × 2)                         ∈ [0, 1]   (hinge: only "more yes than no" counts)
w_i      = topic.weight + learned_delta_i                          (clamped to [-3, 3])
interest = ROUND( profile_weight × p  +  Σ_i m_i × w_i , 6 )
```

Why these choices:
- **Normalize the Score by its max level.** Jev Score values are zero-indexed expected levels, e.g. `[0, 4]` for 5 levels, so dividing by the max level puts the profile on the same `[0,1]` scale as a fully matched topic. Storing `profile_max_level` per row means a future rubric change can't corrupt old normalizations.
- **Use a hinge on Noul, not raw Noul.** Noul 0.5 means "undecided" (per TypeSafe's docs). Summing raw nouls lets ten irrelevant topics at around 0.1 each add noise comparable to the entire profile signal. The hinge contributes exactly 0 until the model leans yes. Negative weights penalize only articles that actually match the unwanted topic.
- **Don't normalize by Σ|w|.** That would make one strongly matched topic weaker every time the user adds an unrelated topic, which is surprising. Additive evidence ("this topic is worth 2 profiles") is easier to explain in the settings UI.
- **Topics added after scoring** have no `article_topic_score` row. The `LEFT JOIN` + `COALESCE(…, 0)` gives zero contribution, which matches "new articles only". Deleted topics cascade away.
- **Round to 6 decimals** so the cursor comparison is exact. A float `SUM` over rows is not guaranteed to be bit-identical across two executions if the row order differs, and an off-by-one-ulp cursor score would skip or duplicate an item at a page boundary.

**The CTE** is defined once as a Java text-block constant in `InterestScoreQueries`:

```sql
WITH learned AS (           -- thumbs → per-topic weight delta (see Pattern 5)
  SELECT ts.topic_id,
         LEAST(:maxDelta, GREATEST(-:maxDelta,
               :eta * SUM(f.vote * GREATEST(0, (ts.noul - 0.5) * 2)))) AS delta
  FROM article_feedback f
  JOIN article_topic_score ts ON ts.article_id = f.article_id
  GROUP BY ts.topic_id
),
eff AS (
  SELECT t.id, LEAST(3, GREATEST(-3, t.weight + COALESCE(l.delta, 0))) AS w
  FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id
),
blended AS (
  SELECT s.article_id,
         ROUND(( :profileWeight * COALESCE(s.profile_score / NULLIF(s.profile_max_level, 0), 0)
               + COALESCE(SUM(GREATEST(0, (ts.noul - 0.5) * 2) * eff.w), 0) )::numeric, 6)::float8 AS score
  FROM article_score s
  JOIN article a              ON a.id = s.article_id AND a."read" = false   -- scope to unread
  LEFT JOIN article_topic_score ts ON ts.article_id = s.article_id
  LEFT JOIN eff               ON eff.id = ts.topic_id
  WHERE s.status = 'SCORED'
  GROUP BY s.article_id, s.profile_score, s.profile_max_level
)
```

For the enrichment variant, replace the `a."read" = false` join with `s.article_id IN (:ids)`, so badges also appear on read articles in normal lists.

**Priority page with a composite keyset cursor, compatible with `PaginatedResponse` (the cursor stays a `Long` article ID):**

```sql
-- sort key: (COALESCE(score, '-Infinity'), COALESCE(published_at, fetched_at), id) DESC
SELECT a.*, b.score AS interest_score
FROM article a
LEFT JOIN blended b ON b.article_id = a.id
WHERE a."read" = false
  AND (:cursorId IS NULL OR
       (COALESCE(b.score, '-Infinity'::float8), COALESCE(a.published_at, a.fetched_at), a.id)
         < (:cursorScore, :cursorDate, :cursorId))
ORDER BY COALESCE(b.score, '-Infinity'::float8) DESC,
         COALESCE(a.published_at, a.fetched_at) DESC,
         a.id DESC
LIMIT :limit
```

- **Unscored articles sort after all scored ones, by date.** Mapping `NULL` to `-Infinity` turns the two-segment ordering into one row-value comparison; all three sort keys are DESC, so the Postgres tuple `<` is correct.
- **Cursor resolution mirrors the existing `ArticleService.findFiltered` pattern.** `PriorityService` looks up the cursor article's current `(score, date)` with the same CTE (a `WHERE s.article_id = :id` variant, not unread-scoped: the cursor article may have just been auto-marked read), then runs the page query with `limit + 1`. `PaginatedResponse.of(fetched, limit, Article::getId)` needs no change.
- **Row mapping:** use `BeanPropertyRowMapper<Article>`. `Article` is a Lombok `@Data` bean, and `interest_score` maps onto the `@Transient interestScore` setter.
- **Score changes between pages** (a thumbs vote, a weight edit) can shift items across a page boundary. That is acceptable because the frontend invalidates `['articles']` on those mutations and refetches from page 1.

### Pattern 5: Thumbs nudge as a derived aggregate, not a mutation

**What:** A thumbs vote writes only `article_feedback(article_id, vote)`. The learned per-topic delta is **computed** in the `learned` CTE:

`delta_t = clamp(η × Σ_feedback vote × m_t(article), ±maxDelta)`

Defaults: `η = 0.1`, `maxDelta = 1.5`. It is applied on top of the user-authored `interest_topic.weight`.

**Why not `UPDATE interest_topic SET weight = weight + …` on each click:**
- The derived version is exactly **reversible**: un-voting or flipping a vote is a delete/upsert of one row. A mutation approach must remember and subtract the exact deltas it applied, including clamping effects.
- It is **idempotent** against double-clicks and retried requests.
- It **never clobbers the user's own weight edits**.
- It lets η and the clamp be retuned retroactively.
- It is explainable: the settings UI can show "base 1.0, learned +0.4" per topic from the same CTE.

**Where it lives:** in SQL (`InterestScoreQueries`), not in Java. The Java side (`ArticleService.setFeedback`) only upserts or deletes the vote and returns the re-enriched article.

**Cost:** the `learned` CTE scans feedback rows, at most a few thousand for one user, once per query. That is negligible, and it needs no Jev calls, as required.

**Only articles that were scored can nudge anything.** Thumbs on an unscored article is stored (harmless) and starts contributing once the article is scored.

### Pattern 6: Backfill = the ingest path's safety net + the launch backfill

```java
@Scheduled(fixedDelayString = "${myfeeder.interest.backfill.interval:PT2M}",
           initialDelayString = "${myfeeder.interest.backfill.initial-delay:PT1M}")
public void backfill() {
    if (!jevApiClient.isConfigured()) return;
    if (circuitBreakerRegistry.circuitBreaker("jev").getState() == CircuitBreaker.State.OPEN) return;
    int room = scoringService.remainingCapacity();                // don't overfill the queue
    if (room <= 0) return;
    List<Long> ids = interestScoreQueries.findUnscoredIds(
            Math.min(room, props.getBackfill().getBatchSize()),  // default 100
            props.getBackfill().getMaxAgeDays(),                 // default 30: cost guard for ancient unread
            props.getBackfill().getMaxAttempts());               // default 3
    scoringService.submit(ids);                                  // returns immediately
}
```

```sql
SELECT a.id FROM article a
LEFT JOIN article_score s ON s.article_id = a.id
WHERE a."read" = false
  AND a.fetched_at > NOW() - make_interval(days => :maxAgeDays)
  AND (s.article_id IS NULL OR (s.status = 'FAILED' AND s.attempts < :maxAttempts))
ORDER BY a.fetched_at DESC           -- newest first: fresh articles matter most
LIMIT :limit
```

- **It runs on the single shared scheduler thread**, so it only runs a cheap indexed SELECT and enqueues. Jev calls happen on `jev-*` threads.
- **The launch backfill needs no separate code.** On first deploy every unread article lacks a row, and the job drains the backlog at batch/interval (100 per 2 min, about 3,000/hour, around 50 req/min, which is about 4% of the rate limit). A manual `POST /api/interest/backfill` trigger is optional sugar.
- **Idempotent.** The in-flight set stops the ingest listener and the backfill from scoring the same ID concurrently. `ArticleScoringService.score` re-checks "already SCORED?" before calling. The writer's `ON CONFLICT DO NOTHING` makes a duplicate harmless even if both checks race.
- **Only unread articles are scored**, because the Priority view shows only unread. The max-age filter bounds the one-time cost.

---

## Data Flow

### Ingest → score (write path)

```
FeedPollingScheduler thread
  pollFeed(feedId) ─ insert new articles (auto-commit each) ─ publish ArticlesIngestedEvent(ids)
        │  (synchronous, microseconds; no tx so fallbackExecution fires immediately)
        ▼
  InterestScoringListener ─ configured? ─ submit(ids) ─► interestScoringExecutor queue
                                                             │
jev-1 / jev-2 threads                                        ▼
  ArticleScoringService.score(id)
     ├─ already SCORED? → return
     ├─ load article, feed title, profile(v), topics(v)
     ├─ build state {feed,title,summary} + questions {profile: Score, topic_<id>: Noul…}
     ├─ JevApiClient.judge(...)  ──[CB "jev" → Retry "jev" → TypeSafeClient.systemOne]──► Jev
     └─ ArticleScoreWriter.insert(article_score + article_topic_score)  [one tx, ON CONFLICT DO NOTHING]
```

### Priority view (read path)

```
/priority route → useArticles({priority: true}) → GET /api/articles/priority?limit=50&before=<id>
  → ArticleController → PriorityService
       ├─ cursor? → InterestScoreQueries.scoreAndDateOf(cursorId)
       └─ InterestScoreQueries.priorityPage(cursor…, limit+1)   [blend CTE, keyset]
  → PaginatedResponse.of(rows, limit, Article::getId)
  → {items:[{…article, interestScore, feedback}], nextCursor}
```

### Thumbs (feedback path)

```
ThumbsButtons / 'u' 'd' keys → PUT|DELETE /api/articles/{id}/feedback
  → ArticleService.setFeedback → ArticleFeedbackRepository upsert/delete
  → onSuccess: invalidate ['articles'] (Priority re-ranks via learned CTE), ['article', id]
```

### Enrichment (every article response)

```
ArticleService.findFiltered / findById / PriorityService
  → InterestScoreQueries.enrich(ids) → Map<id, (score, vote)> → set @Transient fields
```

### State management (frontend)

```
Route (/priority)  ──► PriorityArticles ──► ArticleList(filters={priority:true})
      │                                        ▲
      └─► MainLayout: same filters object ─────┘  (same TanStack query key → shared cache,
                                                   so j/k walk the PRIORITY order)
uiStore: unchanged shape. Handlers call setSelectedFeed(null)/setSelectedFolder(null)
and navigate('/priority'), exactly like Starred. Do NOT add a "virtual feed id" to uiStore.
```

**Frontend facts from the source (HIGH):**
- **Smart views are route-driven** (`/starred` via `navigate`), not uiStore state. Priority should follow the same pattern: `FeedPanel.handlePriorityClick`, a `/priority` route in `App.tsx`, and a `g p` chord.
- **The "All Articles" `active` class** is `!selectedFeedId && !selectedFolderId`, so it would also light up on `/priority`, as it already does on `/starred`. Use `useMatch('/priority')` for Priority's active state and exclude it from All's.
- **`MainLayout` drives keyboard shortcuts from its own `useArticles(...)` call**, keyed by `selectedFeedId`, not by route. On `/priority`, j/k would walk the *All Articles* chronological list, not the ranked one. MainLayout must derive its filters from the route (`useMatch('/priority')` → `{ priority: true }`) with the identical object shape `PriorityArticles` passes, so both hit one cached query.
- **`ArticleList`'s "preserved selected article" logic** already re-inserts the selected item at its old index after it drops out of an unread-only list. It works unchanged for Priority when auto-mark-read or a thumbs re-rank refetches the list.
- **Free keys for thumbs:** `j k n p m s o b v r A / g ? Tab Enter Escape + = -` are taken. Recommend `u` (thumbs up) and `d` (thumbs down), both toggles. Add them and `g p` to `ShortcutOverlay`.
- **`preferencesStore` needs no change.** If a "show interest badge" toggle is added later, remember the CLAUDE.md Zustand-merge gotcha.

---

## Suggested Build Order (phase implications)

The dependencies are real: scoring needs the profile/topics schema, the Priority view needs score rows (seedable in tests), and the thumbs nudge needs the blend CTE.

| # | Phase | Delivers | Depends on | Why this position |
|---|-------|----------|------------|-------------------|
| 1 | **Jev client foundation** | Gradle dep (explicit version, not BOM), `JevApiClient(Impl)` with CB+Retry, `JevNotConfiguredException`, SDK retries off, `spring.ai.typesafe.timeout`, Helm conditional env + `deploy.sh`, `/api/interest/status`, tests: context starts with **no** key; a live smoke test gated on the key. | — | Retires the biggest unknowns first: Boot 4.0.3 vs 4.0.7 compatibility, Jackson 3 interplay, and the blank-key startup crash. Everything else is plain Spring. |
| 2 | **Interest model + settings** | V6 migration (all five tables), `InterestProfile`/`InterestTopic` entities + repos, `InterestProfileService`, `InterestController` CRUD, frontend `InterestSettings` + `useInterest`. | 1 (status endpoint drives the "not configured" UI) | Scoring can't build questions without a profile and topics. Ship the whole schema at once so later phases add no migrations. |
| 3 | **Scoring pipeline** | `ArticlesIngestedEvent`, `FeedPollingService` publish, `InterestScoringListener`, `interestScoringExecutor`, `ArticleScoringService` (state/question building, classification), `ArticleScoreWriter`. | 1, 2 | The core write path. Tests: listener never throws into `pollFeed`; the executor is swapped for `SyncTaskExecutor`; mocked `JevApiClient`. |
| 4 | **Backfill** | `InterestBackfillJob`, `findUnscoredIds`, breaker-open skip, capacity-aware enqueue, config. | 3 | Reuses the pipeline. Small, so it can merge into Phase 3 if preferred. It is also the launch backfill. |
| 5 | **Blend + Priority view** | `InterestScoreQueries` (CTE with `learned` returning 0 until Phase 6 data exists), `PriorityService`, `GET /api/articles/priority`, `@Transient` enrichment, frontend `/priority` route, FeedPanel entry, MainLayout filter alignment, `InterestBadge`. | 2 (schema). Testable with seeded `article_score` rows, independent of 3/4. | Can run **in parallel with 3–4** because it reads a table and does not depend on the writer. Keyset cursor tests: ties, unscored segment, cursor article read since. |
| 6 | **Thumbs feedback** | `article_feedback` repo, PUT/DELETE endpoints, `learned` CTE active, `ThumbsButtons`, `u`/`d` shortcuts, `useArticleFeedback`, learned-delta display in settings. | 5 | Needs the blend to observe its effect. Smallest phase. |

**Research flags:**
- **Phase 1:** needs a quick spike against the live API (real latency, the 403-on-missing-key behavior, whether 0.1.0 resolves cleanly on Boot 4.0.3 with Gradle).
- **Phase 5:** needs tuning of the blend constants against the real backlog after Phase 4 has scored some of it. Consider a hidden `/api/interest/debug/{articleId}` breakdown endpoint to support that tuning.
- **Phases 2, 3, 4, 6:** standard patterns already present in the codebase; no deeper research needed.

---

## Scaling Considerations

This is a single-user homelab app. Realistic scale is tens to hundreds of feeds, 500 to 3,000 new articles a day, and 1,000 to 20,000 unread.

| Scale | Architecture adjustments |
|-------|--------------------------|
| ≤ 20k unread (expected) | As designed: blend computed per request over unread scored rows (a hash join on PKs, milliseconds). |
| 20k–200k unread | Add a partial index `article(id) WHERE read = false` if the planner struggles. If still slow, materialize `article_score.cached_blend` refreshed on weight/feedback change (a single UPDATE … FROM CTE), keeping raw outputs as the source of truth. |
| Ingest bursts (OPML import of many feeds) | The bounded queue discards overflow and backfill catches up. No change needed. |

### Scaling priorities

1. **First bottleneck: the single scheduler thread**, not Jev. Anything added to `@Scheduled` or the polling path that blocks stalls all feeds. That is why the design keeps Jev off that thread entirely.
2. **Second: Jev rate limit and cost** during the launch backfill. It is bounded by batch size, interval, the 2-thread pool and the max-age filter.

---

## Anti-Patterns

### Anti-Pattern 1: Calling Jev inline in `pollFeed` or in a `@Scheduled` method
**What people do:** Score inside the insert loop, or have the backfill loop call Jev directly.
**Why it's wrong:** The scheduler has one thread. At 300 ms × N articles, all feed polling stalls, and a Jev outage turns into feed-polling latency.
**Do this instead:** Publish the event, submit to `interestScoringExecutor`, and keep the backfill to "SELECT IDs + enqueue".

### Anti-Pattern 2: Letting a scoring exception escape into `pollFeed`
**What people do:** A listener without try/catch, or `submit` throwing `RejectedExecutionException`.
**Why it's wrong:** `publishEvent` runs inside `pollFeed`'s `try`, so the exception increments `feed.errorCount` and eventually backs off polling for a healthy feed.
**Do this instead:** The listener catches everything; the executor uses `DiscardPolicy` + log.

### Anti-Pattern 3: Blank API key property (copying the Raindrop env pattern)
**What people do:** `spring.ai.typesafe.api-key: ${MYFEEDER_TYPESAFE_API_KEY:}`, or a Helm env var always rendered from a possibly-empty secret.
**Why it's wrong:** The starter's `Assert.state(hasText(apiKey))` **fails application startup** on a present-but-blank key, so the pod crash-loops.
**Do this instead:** Leave the key out of `application.yaml`, render `SPRING_AI_TYPESAFE_API_KEY` only when the value is non-empty, and add a context test with no key.

### Anti-Pattern 4: Score columns on the `article` table
**What people do:** `ALTER TABLE article ADD interest_score …`.
**Why it's wrong:** `ArticleService.updateState` saves the full row loaded earlier and silently wipes scores written by the async worker in between.
**Do this instead:** Use separate `article_score` and `article_topic_score` tables with explicit INSERTs.

### Anti-Pattern 5: Storing only the blended number
**What people do:** Compute the blend at ingest and store one `interest` value.
**Why it's wrong:** Weight edits and thumbs could not re-rank without re-calling Jev, which violates a core requirement and the cost constraint.
**Do this instead:** Store raw Score and Noul values; blend in SQL at query time.

### Anti-Pattern 6: Double retry layers
**What people do:** Keep the SDK's default 2 retries *and* add `@Retry(max-attempts: 3)`.
**Why it's wrong:** Up to 9 HTTP attempts per article, retry storms against a rate-limited API, and breaker statistics that lag reality.
**Do this instead:** `spring.ai.typesafe.retry.max-retries: 0`, with Resilience4j as the only retry layer and an allow-list of transient exceptions.

### Anti-Pattern 7: Priority as a "virtual feed id" in uiStore
**What people do:** Encode Priority as `selectedFeedId = -1` or add a `selectedView` field.
**Why it's wrong:** It breaks `feedId`-based API calls, `n`/`p` unread-feed navigation and mark-all-read-in-feed, and diverges from how Starred works.
**Do this instead:** Use a route (`/priority`) plus a filter flag (`ArticleFilters.priority`).

---

## Integration Points

### External services

| Service | Integration pattern | Notes |
|---------|---------------------|-------|
| TypeSafe Jev (`systemOne`) | `spring-ai-starter-typesafe` 0.1.0 auto-config → `TypeSafeClient` bean (only when the key is set) → `JevApiClientImpl` (CB+Retry) | ~300 ms/call, no streaming, 1,200 req/min published limit. State must be a string, object, array or null. 403 means a missing key at the API; 401 a rejected key. Score values are zero-indexed expected levels (max 10 levels). Noul is a single probability in `[0,1]`. Store `requestId` for support. |

### Internal boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `FeedPollingService` → scoring | Domain event (`ArticlesIngestedEvent`), AFTER_COMMIT with fallback | Same contract style as `FeedSavedEvent`. Ingest never references scoring classes. |
| Listener/backfill → `ArticleScoringService` | Direct call to `submit()` (non-blocking) | The service owns the executor and the in-flight set. |
| `ArticleScoringService` → `JevApiClient` | Direct call through the AOP proxy (separate bean) | Business checks outside the breaker; only the HTTP call inside it. |
| Read path → scores | SQL only (`InterestScoreQueries`) | Nothing in the read path calls Jev. The blend formula exists in exactly one place. |
| `ArticleService` ↔ `Article` API shape | `@Transient interestScore`, `@Transient feedback` filled by enrichment | Avoids a parallel DTO hierarchy. `@WebMvcTest` JSON assertions gain two nullable fields. |
| Backend ↔ Frontend | REST: `/api/articles/priority`, `/api/articles/{id}/feedback`, `/api/interest/*` | `PaginatedResponse` unchanged (`items`, `nextCursor: Long`). |

---

## Sources

- myfeeder source (HIGH, read directly): `service/FeedPollingService.java`, `scheduler/FeedPollingScheduler.java`, `repository/ArticleRepository.java`, `service/ArticleService.java`, `controller/ArticleController.java`, `controller/PaginatedResponse.java`, `integration/RaindropApiClientImpl.java`, `application.yaml`, `helm/myfeeder/templates/app-deployment.yaml`, `deploy.sh`, `db/migration/V1..V5`, frontend `App.tsx`, `stores/uiStore.ts`, `hooks/useArticles.ts`, `hooks/useKeyboardShortcuts.ts`, `components/FeedPanel.tsx`, `components/ArticleList.tsx`, `api/articles.ts`, `types/index.ts`
- spring-ai-typesafe v0.1.0 source at the git tag (HIGH): `TypeSafeAutoConfiguration.java` (blank-key `Assert.state`, cloned `RestClient.Builder`), `TypeSafeProperties.java` (timeout, retry props), `TypeSafeAutoConfigurationTests.java`, `TypeSafeClient.java` (`systemOne(Map,…)` overloads), `SystemOneResponse.java`, `ScoreAnswer.java`, `NoulAnswer.java`, `docs/client/ErrorsAndRetries.md`, `docs/client/Batches.md`, starter `pom.xml` (no Spring AI dependency) — https://github.com/spring-ai-community/spring-ai-typesafe
- Spring blog, "Spring AI and TypeSafe Jev: Fast, Cheap, Structured Decisions" (2026-09-21) — https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment/
- TypeSafe docs, Score primitive (zero-indexed levels, expected-value computation, ≤10 levels) — https://docs.typesafe.ai/primitives/score ; Noul primitive — https://docs.typesafe.ai/primitives/noul
- Spring Boot 4.0.3 reference, Task Execution and Scheduling (default `ThreadPoolTaskScheduler` with one thread; the virtual-threads switch to `SimpleAsyncTaskScheduler`), via Context7 `/spring-projects/spring-boot/v4.0.3`, cross-checked against the Boot source (`TaskExecutorConfigurations`, `Threading`)

---
*Architecture research for: Jev interest scoring integration into myfeeder*
*Researched: 2026-09-22*
