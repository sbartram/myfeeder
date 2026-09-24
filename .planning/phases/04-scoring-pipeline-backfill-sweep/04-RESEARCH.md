# Phase 4: Scoring Pipeline & Backfill Sweep - Research

**Researched:** 2026-09-23
**Domain:** Background LLM-judgment pipeline on Spring Boot 4.0.8 (event → bounded executor → Jev → write-once SQL), Resilience4j 2.3.0 aspect ordering, PostgreSQL upserts, small React dialog addition
**Confidence:** HIGH (all load-bearing library behaviors verified from the resolved jars' bytecode or against a live Postgres 18 container this session)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (locked by research and prior phases, not re-discussed)
- **Pipeline shape (research R3):** `FeedPollingService` publishes an event with the IDs of genuinely new articles only. A listener that never throws hands them to a dedicated, named `ThreadPoolTaskExecutor`: bounded queue of about 1,000, discard-on-overflow plus a WARN log, no `@EnableAsync`, and a swappable `SyncTaskExecutor` in tests. The DB is the source of truth. "Needs scoring" means no `article_score` row, or a FAILED row with `attempts < 3`, AND the eligibility predicate holds. Ingest writes no PENDING rows. An in-flight `Set` plus `ON CONFLICT DO NOTHING` is enough, since `replicaCount: 1`.
- **Sweep = the only drain loop:** a `@Scheduled` job SELECTs eligible unscored IDs and enqueues them. It never calls Jev on the scheduler thread. This one job is the launch backfill, outage recovery, the late-added-key path, first scoring after the profile is written, and the Re-score drain. Don't change `spring.task.scheduling.pool.size` and don't enable virtual threads.
- **Eligibility (SCOR-04):** `read = false AND COALESCE(published_at, fetched_at) > now − 14d` (window configurable), newest first. The same predicate applies at enqueue, in the sweep, and again at dispatch (`read` is rechecked).
- **Gates:** `JevApiClient.isConfigured()`, `InterestService.isColdStart()` (the single predicate, never reimplemented, SCOR-06), and breaker not OPEN. Never judge an empty question map. Skip when `ArticleStateBuilder.hasJudgeableText` is false.
- **Statuses (R3):** SCORED is write-once. FAILED covers 400/422/missing-answer/answer-type, increments `attempts`, and the sweep retries it while `attempts < 3`. SKIPPED is terminal: GUID-less articles (SCOR-08) and articles with no judgeable text. Transient failures write **no row and use no attempt**: 429, 5xx, timeout/connection, `CallNotPermittedException`, `JevNotConfiguredException`. 401/403 are recorded by the breaker, so it opens and the sweep pauses.
- **Write order:** `article_score` is inserted before `article_topic_score` in one transaction. The row stores profile score, max level, confidence, profile version, model, request id, and per-topic noul + topic version.
- **Builders:** reuse `InterestQuestions` / `ArticleStateBuilder`, the pure static builders shared with the preview. Question keys are `profile` / `topic_<id>`, and answers are read by key. Topic wording stays v2 ("substantially about").
- **`InterestStatus`:** the three existing components `{configured, breakerState, coldStart}` are never renamed. JEV-05 counts are appended as new components.

#### Re-score unread (INT-05)
- **D-01:** The action sits in the **Interests dialog footer**, replacing or extending the note "Profile and topic changes apply to newly arriving articles." (`InterestsDialog.tsx:93`). No Settings-dialog entry. The Phase 5 Priority view may add another entry point later.
- **D-02:** The confirmation is **inline and themed** (not `window.confirm`). It shows a server-computed count with plain wording, e.g. *"Re-judge 312 unread articles from the last 14 days? Existing scores are replaced as they're re-scored."* The count comes from a server GET that uses **the same predicate as the delete**, so what's shown is exactly what gets reset. No time estimate.
- **D-03:** **Scope:** in-window unread articles with **SCORED or FAILED** rows (including exhausted FAILED). SKIPPED rows are not touched. Re-score therefore doubles as the recovery path for a rubric-caused 400 that burned every article's attempts. Articles with no row are already queued for the sweep. — **Reversibility:** reversible — a predicate in one service method.
- **D-04:** The Re-score button is **disabled with a reason** ("Save your changes first") while the dialog has unsaved profile/topic edits, using the existing `describeUnsaved(...)` dirty state.
- After confirmation the rows are deleted and the sweep drains them. Progress shows as the eligible-unscored count from `/status` (a simple "N waiting to be scored" line is enough in this phase).

#### Jev timeout and breaker
- **D-05:** Raise the **shared** `spring.ai.typesafe.timeout` from 5s to **about 30s**. It stays one `TypeSafeClient` and one setting for both the preview and the scorer, with no separate scorer client. Calibration measured about 2.6s average and more than 5s cold for profile + 7 topics, and the rubric can grow to 25 topics. Mirror the change in test config where relevant.
- **D-06:** Raise the `jev` breaker `slow-call-duration-threshold` from 3s to **about 15s** (it must stay below the timeout). Slow-call detection remains a "Jev is degraded" signal but no longer trips on normal multi-topic calls.
- **D-07:** **Breaker outer (fold in 02-REVIEW WR-03):** set the Resilience4j aspect orders so the circuit breaker wraps the retry and records **one outcome per logical `judge()` call**, not one per attempt. The D-09 thresholds from Phase 2 then count articles, as originally meant. Update `JevResilienceTest` (it currently asserts 3 calls → 9 recorded failures) and make the CLAUDE.md Resilience4j convention describe what actually runs. Check the Raindrop instance for side effects, because the aspect order is global. — **Reversibility:** costly — the aspect order is global to every `@CircuitBreaker`/`@Retry` bean (Jev and Raindrop), and both the tests and the CLAUDE.md convention encode it.
- **D-08:** An auth failure (401/403 → OPEN breaker) gets **no special surfacing** in Phase 4. `breakerState` on `/status` is enough, and the Phase 5 "scoring paused" state covers the UI. No `lastError` field.

#### Sweep pacing and failure counts
- **D-09:** Executor concurrency is **1 thread**, with a configurable property (e.g. under `myfeeder.interest.*`). 429 behavior during backfill hasn't been observed yet, and one thread at about 2.6s per call is roughly 1,400 articles/hour, plenty for one user. Phase 7 may raise it after watching the launch backfill. This supersedes the research default of 2 threads.
- **D-10:** The sweep runs on a **2-minute fixed delay** and enqueues **up to the executor's free queue capacity**, newest first. It skips when unconfigured, in cold start, or when the breaker is OPEN. There is no immediate kick on profile save or Re-score: pickup within 2 minutes is acceptable.
- **D-11:** The **`failed` count** on `/status` covers **exhausted articles only** (FAILED with `attempts ≥ 3`). FAILED rows still being retried count as **eligible-unscored**. — **Reversibility:** costly — the Phase 5 Priority states read these field semantics.
- **D-12:** **Both counts use the eligibility predicate** (unread, inside the window), so they always match what the sweep and Re-score will act on. Read and aged-out articles drop out of both counts.

#### Scorer-path review fixes (folded in)
- **D-13:** **CR-01:** fix `ArticleStateBuilder.truncate` (`ArticleStateBuilder.java:56-72`). Accept the whitespace cut only when it keeps most of the text (e.g. `cut >= max / 2`), otherwise hard-cut at `max − 1`. Add CJK and long-URL tests. The background scorer sends exactly this state.
- **D-14:** **02 WR-01:** caller input errors (`IllegalArgumentException` from `Assert` inside the proxied `judge()`) must not count as breaker failures. Either add the type to the `jev` `ignore-exceptions` in **both** main and test `application.yaml`, or validate outside the proxied method. Add a `JevResilienceTest` case asserting `judge(null, …)` leaves the failed-call count at 0.
- **D-15:** **02 WR-02:** reject unsupported question types (anything other than `Noul`/`Score`, i.e. `Choice`) before the HTTP call, so no billed answer is silently dropped. Keep the check outside the breaker's failure count, consistent with D-14.

### Claude's Discretion
- Event name and shape (e.g. `ArticlesIngestedEvent(feedId, newIds)`), listener placement, and the in-flight dedup mechanics.
- Class split (e.g. `ArticleScoringService`, `ArticleScoreWriter`, `InterestBackfillJob`) and config property names and defaults under `myfeeder.interest.*` (window days, concurrency, queue capacity, sweep delay).
- Endpoint paths and payloads for the Re-score count and the trigger (e.g. `GET /api/interest/rescore` → `{count}`, `POST /api/interest/rescore`), and the new `InterestStatus` component names (e.g. `eligibleUnscored`, `failed`).
- How the "N waiting to be scored" progress line is shown in the Interests dialog, including whether `/status` is polled while the dialog is open.
- Whether to reduce the 30s timeout to about 20s if testing shows 30s is excessive, as long as the slow-call threshold stays below it.
- Test strategy for success criterion 2: poll duration and feed error counts unchanged with a Jev stub sleeping 30s, plus a listener exception that never increments `feed.errorCount`.

### Deferred Ideas (OUT OF SCOPE)
- Immediate sweep kick on first profile save / Re-score confirm: not needed; the 2-minute sweep is acceptable.
- `lastError` / auth-failure category on `/status` ("check your API key"): Phase 5/7 if the paused state proves confusing.
- Separate scorer vs preview timeouts: rejected for now in favor of one shared 30s setting.
- 03-REVIEW WR-01..WR-05 (Interests dialog save race, discard-while-pending, shortcuts behind modal, endless "loading article…", wrong error copy): still open. Suggest `/gsd-code-review 03 --fix` or a `/gsd-quick` pass.
- 02-REVIEW WR-04 (Raindrop `createBookmark` POST retried on read timeout/5xx) and WR-05 (`helm --set` mangles secrets with `,` or `\`): separate `/gsd-quick` tasks.
- Interests dialog help text still says "primarily about" (03 deferred-items): trivial copy fix. The planner may fold it in, since the Re-score work edits the same dialog.

#### Reviewed Todos (not folded)
- **Tune Raindrop resilience and fix CLAUDE.md AspectJ note** (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): Raindrop tuning is outside scoring scope. The CLAUDE.md aspect-order wording overlaps D-07. If D-07 corrects that text, update the todo so it covers only the Raindrop tuning.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCOR-01 | Each newly ingested article judged once in a single call (5-level profile Score + one Noul per topic), feed/title/summary input, HTML-stripped, truncated, content fallback | Pattern 3 (scorer reuses `ArticleStateBuilder.build` + `InterestQuestions.forRubric` unchanged), Pattern 1 (event with new IDs only), D-13 truncate fix code in Code Examples |
| SCOR-02 | Scoring never blocks or fails polling; dedicated bounded executor; poll time and error counts unaffected when Jev slow/failing/unconfigured | Pattern 1 + Pattern 2 (listener catches all, `TaskRejectedException` handling, `maxPoolSize == corePoolSize`), Pitfalls 1-4, SC2 isolation test in Validation Architecture |
| SCOR-03 | Raw outputs stored write-once in separate tables | Pattern 4 (verified `ON CONFLICT ... DO UPDATE ... WHERE status = 'FAILED'` upsert), `INSERT ... SELECT FROM interest_topic` for topic rows |
| SCOR-04 | Unread + within 14 days (configurable), newest first | One shared SQL predicate constant (Pattern 4), used at enqueue, sweep, dispatch, counts, Re-score |
| SCOR-05 | Sweep covers launch backfill, outage recovery, late key, first scoring after profile | Pattern 5 (sweep job) + Pitfall 5 (breaker never leaves OPEN without a call: enable automatic OPEN→HALF_OPEN) |
| SCOR-06 | Nothing scored while cold start | Gate calls `InterestService.isColdStart()` in sweep AND at dispatch; `forRubric` empty-map guard |
| SCOR-07 | Permanent failures max 3 attempts; transient don't consume attempts | Failure classification table (verified SDK exception hierarchy), FAILED upsert increments `attempts` |
| SCOR-08 | GUID-less articles skipped | Scorer writes SKIPPED for blank/null GUID. **Note Pitfall 6:** `article.guid` is `NOT NULL` in the schema, so the "re-insert" bug does not occur as described |
| INT-05 | Re-score unread with a count shown first | Pattern 6 (count and delete share one predicate), frontend inline confirm in the dialog footer |
| JEV-05 | `/status` reports configured, breaker state, eligible-unscored, failed | `InterestStatus` gains `long eligibleUnscored, long failed`; one `COUNT(*) FILTER` query |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Directives the planner must honor (root `CLAUDE.md`, `.claude/CLAUDE.md`, user global `~/.claude/CLAUDE.md`):

- **Build:** Gradle only (`./gradlew`), never Maven. Frontend tests `cd src/main/frontend && npm test`; type-check with `npx tsc -b` (plain `tsc --noEmit` gives false success).
- **Docker must be running** for backend tests (Testcontainers).
- **Spring Data JDBC, not JPA:** `@Id` from `org.springframework.data.annotation`, `@Table` from `org.springframework.data.relational.core.mapping`; no derived query methods; use `@Query`.
- **Jackson 3:** `tools.jackson.databind.*`; annotations stay `com.fasterxml.jackson.annotation.*`.
- **Test slices:** `@WebMvcTest` (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`) + `@MockitoBean`; `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)`; Mockito `@ExtendWith(MockitoExtension.class)`.
- **Test `application.yaml` shadows main:** it must include `myfeeder.*` properties; new `resilience4j` / `spring.ai.typesafe` keys must be mirrored (existing mirror tests enforce the `jev` instance keys and the typesafe pins).
- **Resilience4j convention:** annotations on the API-client bean, not the service (self-invocation bypasses the proxy). D-07 must update the convention text to describe the real execution order.
- **Feed scheduling is event-driven:** new feed-mutating paths publish events; never call the scheduler directly. `FeedFetcher` is the single fetch path (not relevant to scoring, which makes no feed fetches).
- **GlobalExceptionHandler mappings:** `IllegalArgumentException` → 400, `IllegalStateException` → 409, `NotFoundException` → 404, Jev exceptions → fixed-text ProblemDetails. Throw the right type; no controller try/catch.
- **Interest ranking rules:** `InterestService.isColdStart()` is the single cold-start predicate; `InterestQuestions`/`ArticleStateBuilder` are shared pure builders; `InterestStatus` components `{configured, breakerState, coldStart}` are never renamed; later milestone phases add **no migrations** (V6 is complete).
- **`HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring 7** — use `HttpStatus.valueOf(422)` if needed.
- **Frontend conventions:** API wrappers in `src/api/`, TanStack Query hooks in `src/hooks/`, interest types live in `src/api/interest.ts`, interest mutations set `meta.inlineError` so the global toast is skipped.
- **Git (user global):** non-trivial work on a feature branch/worktree; merges `--no-ff`; subagents never detach HEAD. **GSD enforcement:** file changes go through a GSD command.
- **Simplicity/surgical changes (user global):** minimum code, no speculative configurability, touch only what the requirement needs.
- **Uncommitted user edits:** the working tree currently has uncommitted changes to `CLAUDE.md` and `.claude/CLAUDE.md` (plus `.envrc`, `.planning/config.json`) [VERIFIED: `git status --short`]. The D-07 CLAUDE.md edit must stage only its own hunk (`git add -p`) or wait until the user commits theirs. The pending todo says the same.

## Summary

Phase 4 adds a background write path that is almost entirely plain Spring: an after-ingest event, a listener that only enqueues, a one-thread bounded `ThreadPoolTaskExecutor`, a scorer that reuses the Phase 3 builders and the Phase 2 `JevApiClient`, a write-once store, and a 2-minute `@Scheduled` sweep that is the only drain loop. No new libraries or migrations are needed. The design is settled (research R3, CONTEXT decisions), so this research focuses on the exact mechanics that fail at execution time with these library versions. Each was checked against the resolved jars or a live Postgres container.

Six findings change what the planner should write:
1. **A custom `ThreadPoolTaskExecutor` bean removes Boot's `applicationTaskExecutor`.** Boot 4.0.8 creates it only when no `Executor` bean exists. Use `@Bean(defaultCandidate = false)` and inject by qualifier, with an explicit constructor, because the repo has no `lombok.config` to copy `@Qualifier`.
2. **The Resilience4j breaker never leaves OPEN on its own.** `automaticTransitionFromOpenToHalfOpenEnabled` defaults to `false`, and a sweep that skips while OPEN never makes the call that would move it to HALF_OPEN. Enable automatic transition on the `jev` instance, and have the scorer call `judge()` instead of pre-checking the breaker.
3. **"SCORED is write-once" plus "retry FAILED" needs `ON CONFLICT ... DO UPDATE ... WHERE article_score.status = 'FAILED'`, not `DO NOTHING`.** With `DO NOTHING`, a success after a failed attempt is silently dropped. Verified live.
4. **Plain `JdbcClient`/`JdbcTemplate` cannot bind `java.time.Instant` on PgJDBC 42.7.13.** Pass `Timestamp.from(cutoff)`. Verified live.
5. **`article.guid` is `NOT NULL`.** The "NULL-GUID re-insert" never happens: such an item fails the insert and fails the poll. The SCOR-08 guard stays (cheap, required), but no test should expect re-inserts.
6. **With `DiscardPolicy`, a dropped task leaves its ID in the in-flight set forever,** so the sweep can never rescore it. Keep the default abort policy, catch `TaskRejectedException` at submit, and remove the ID.

D-07's aspect swap is a two-property change (`circuit-breaker-aspect-order: 1`, `retry-aspect-order: 2`). Defaults verified in bytecode: retry `2147483642`, breaker `2147483643`, and the lower value wraps. The Raindrop side effects are benign: the same number of HTTP attempts, fewer breaker trips, and an immediate rejection when open instead of three retried rejections.

**Primary recommendation:** Build four small backend units (event + never-throwing listener, `ScoringQueue` owning the qualified executor and in-flight set, `ArticleScoringService` with a classifier, `ArticleScoreStore` holding one shared eligibility-predicate SQL constant), plus a `@Scheduled` sweep, two re-score endpoints, and the `InterestStatus` counts. Land the config and resilience fixes (D-05, D-06, D-07, D-13, D-14, D-15, auto-transition) first, as their own wave, because every scorer test depends on them.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Detect new articles and publish IDs | API / Backend (`FeedPollingService`) | — | Only ingest knows which rows are new; it runs on the single scheduler thread |
| Enqueue for scoring (never block, never throw) | API / Backend (listener + `ScoringQueue`) | — | Hand-off boundary between the polling thread and the scoring thread |
| Judge an article (Jev call, classification) | API / Backend (`ArticleScoringService` on `jev-score-*` thread) | External (TypeSafe Jev via `JevApiClient`) | Outbound call behind the breaker/retry proxy; no DB transaction held across it |
| Persist raw outputs write-once | Database / Storage (`article_score`, `article_topic_score`) | API / Backend (`ArticleScoreStore`) | The DB is the queue's source of truth (R3); the upsert makes "write-once" a DB guarantee |
| Eligibility / needs-scoring predicate | Database / Storage (one SQL predicate) | API / Backend (cutoff computation) | One SQL string reused by sweep, dispatch recheck, counts and Re-score (D-02, D-12) |
| Backfill / recovery drain | API / Backend (`@Scheduled` sweep on the scheduler thread) | Database | Cheap SELECT + enqueue only; never calls Jev on the scheduler thread |
| Status counts (JEV-05) | API / Backend (`InterestStatusService`) | Database | Server computes counts with the same predicate |
| Re-score count + reset (INT-05) | API / Backend (endpoints) | Database (DELETE cascades topic rows) | Destructive action with a server-computed preview count |
| Re-score confirm UI, "N waiting" line | Browser / Client (`InterestsDialog`) | — | Presentation only; dirty-state gating uses the existing client state |

## Standard Stack

No new dependencies. Everything is already on the classpath [VERIFIED: `./gradlew dependencies --configuration runtimeClasspath` this session]:

### Core (already present)
| Library | Resolved version | Purpose | Why Standard |
|---------|------------------|---------|--------------|
| Spring Boot | 4.0.8 | App framework, `@Scheduled`, task execution | Project baseline |
| Spring Framework (context/jdbc) | 7.0.9 | `ThreadPoolTaskExecutor`, `SyncTaskExecutor`, `JdbcClient`, `@TransactionalEventListener` | Built in; no queue library needed |
| resilience4j-spring-boot3 / -spring6 / -circuitbreaker / -retry | 2.3.0 (via `spring-cloud-starter-circuitbreaker-resilience4j` 5.0.3) | Jev breaker + retry aspects | Existing single retry layer |
| aspectjweaver | 1.9.25.1 (via `spring-boot-starter-aspectj`) | Activates the Resilience4j aspects | Already added in Phase 2 |
| spring-ai-starter-typesafe / typesafe-java-sdk | 0.1.0 | `TypeSafeClient`, `Noul`/`Score`/`Choice`, typed exceptions | Existing Jev client |
| org.postgresql:postgresql | 42.7.13 | JDBC driver | See Pitfall 7 (`Instant` binding) |
| jsoup | 1.11.2 (transitive via readability4j 1.0.8) | HTML strip in `ArticleStateBuilder` | Already used; STATE.md "verify jsoup on classpath" is **resolved**: present |

### Frontend (already present)
| Library | Version | Purpose |
|---------|---------|---------|
| @tanstack/react-query | ^5.103.2 | Re-score count query, mutation, conditional status polling |
| vitest + @testing-library/react | ^4.1.11 | Dialog tests |

**Installation:** none.

## Package Legitimacy Audit

This phase installs no external packages (backend or frontend). No `package-legitimacy check` was needed.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| *(none)* | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                        (single scheduler thread "scheduling-1")
 FeedPollingScheduler ──► FeedPollingService.pollFeed(feedId)
                              │ dedup loop: existsByFeedIdAndGuid → save → collect new IDs
                              │ success bookkeeping: feedRepository.save(feed)
                              ▼
                     publishEvent(ArticlesIngestedEvent(feedId, newIds))   [only if newIds non-empty;
                              │                                             wrapped in its own try/catch]
                              ▼  (no tx in pollFeed → @TransactionalEventListener fallbackExecution runs inline)
                     InterestScoringListener  ── catches everything ──► (never reaches pollFeed's catch)
                              │ isConfigured()? no → return
                              ▼
                     ScoringQueue.submitIngested(ids)
                              │ 1 SQL: filter ids by eligibility+needs-scoring, newest first
                              │ per id: inFlight.add(id) → executor.execute(task)
                              │         TaskRejectedException → inFlight.remove(id), count, WARN once
                              ▼
   ┌──────────── interestScoringExecutor (core=max=1, LinkedBlockingQueue 1000, "jev-score-") ───────────┐
   │ ArticleScoringService.score(id)                                                                   │
   │   gates: isConfigured, !isColdStart  (no breaker pre-check: let judge() throw CallNotPermitted)   │
   │   recheck: store.loadCandidate(id, cutoff)  → empty? return (read / aged out / already scored)    │
   │   guid blank?            → store.writeSkipped(id)                                                 │
   │   state = ArticleStateBuilder.build(feedTitle, article); !hasJudgeableText → writeSkipped         │
   │   snapshot profile(text, version) + topics(id, description, version)                             │
   │   questions = InterestQuestions.forRubric(...); empty → return                                    │
   │   judge(state, questions) ──► [CB "jev" (outer) → Retry "jev" (inner) → TypeSafeClient] ──► Jev  │
   │   success   → store.writeScored(...)   one tx: upsert article_score, INSERT..SELECT topic rows    │
   │   permanent → store.writeFailed(id, fixedErrorText)   attempts + 1                                │
   │   transient → nothing (no row, no attempt)                                                        │
   │ finally: inFlight.remove(id)                                                                      │
   └───────────────────────────────────────────────────────────────────────────────────────────────────┘
                              ▲
   InterestScoringSweep  @Scheduled(fixedDelay 2m) on the scheduler thread
     gates: isConfigured, !isColdStart, breaker state not OPEN/FORCED_OPEN
     room = queue.remainingCapacity(); ids = store.findNeedingScoring(cutoff, 3, room) → queue.submit(ids)

   /api/interest/status   → InterestStatusService: configured, breakerState, coldStart, eligibleUnscored, failed
   GET  /api/interest/rescore → {count}  (same predicate as the DELETE)
   POST /api/interest/rescore → DELETE in-window unread SCORED/FAILED rows (topic rows cascade) → sweep drains
```

### Recommended Project Structure (new/changed files)

```
src/main/java/org/bartram/myfeeder/
├── event/ArticlesIngestedEvent.java          # record(Long feedId, List<Long> articleIds)  [new]
├── config/MyfeederProperties.java            # + Interest nested class                      [changed]
├── config/InterestScoringConfig.java         # @Bean(defaultCandidate=false) executor       [new]
├── service/InterestScoringListener.java      # @TransactionalEventListener, never throws    [new]
├── service/ScoringQueue.java                 # in-flight set, submit, remainingCapacity      [new]
├── service/ArticleScoringService.java        # gates, recheck, build, judge, classify       [new]
├── service/ScoringFailure.java (optional)    # classifier enum/static fn: PERMANENT/TRANSIENT [new]
├── repository/ArticleScoreStore.java         # JdbcClient SQL: predicate const, upserts, counts, rescore [new]
├── scheduler/InterestScoringSweep.java       # @Scheduled drain                              [new]
├── service/InterestRescoreService.java       # count() / rescore()                           [new]
├── controller/InterestRescoreController.java # GET/POST /api/interest/rescore                [new]
├── service/InterestStatus(.java|Service.java)# + eligibleUnscored, failed                    [changed]
├── service/FeedPollingService.java           # collect new IDs, publish event                [changed]
├── service/ArticleStateBuilder.java          # D-13 truncate fix                             [changed]
└── integration/JevApiClientImpl.java         # D-15 type check before HTTP                   [changed]
src/main/resources/application.yaml + src/test/resources/application.yaml   # D-05/06/07/14 + myfeeder.interest.*
src/main/frontend/src/{api/interest.ts, hooks/useInterest.ts, components/InterestsDialog.tsx, App.css}
CLAUDE.md                                      # D-07 convention text (hunk-only commit)
```

Class names are recommendations (Claude's discretion). The planner may rename them, but keep the split: the store is separate from the scorer, so `@Transactional` on store methods goes through a proxy.

### Pattern 1: Publish new IDs after the success bookkeeping; the listener only enqueues

**What:** Collect the saved IDs in the existing dedup loop. Publish after `feedRepository.save(feed)`, wrapped in its own try/catch, so no listener failure can reach the outer `catch` that increments `errorCount`.
**Why here:** `pollFeed` has no transaction, so an `AFTER_COMMIT` listener with `fallbackExecution = true` runs **inline** on the polling thread [VERIFIED: `FeedPollingService.java:26-68`, no `@Transactional`; `FeedPollingScheduler.java:43` uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)`]. The outer catch is `feed.setErrorCount(feed.getErrorCount() + 1);` [VERIFIED: `FeedPollingService.java:61-66`].

```java
// FeedPollingService (changed region; existing lines 45-60)
List<Long> newIds = new ArrayList<>();
for (ParsedArticle parsedArticle : parsed.articles()) {
    if (!articleRepository.existsByFeedIdAndGuid(feed.getId(), parsedArticle.guid())) {
        Article saved = articleRepository.save(toArticle(parsedArticle, feed.getId()));
        newIds.add(saved.getId());
    }
}
feed.setLastSuccessfulPollAt(Instant.now());
feed.setErrorCount(0);
feed.setLastError(null);
feedRepository.save(feed);
log.info("Polled feed '{}': {} new articles", feed.getTitle(), newIds.size());
publishIngested(feed.getId(), newIds);

private void publishIngested(Long feedId, List<Long> newIds) {
    if (newIds.isEmpty()) return;
    try {
        eventPublisher.publishEvent(new ArticlesIngestedEvent(feedId, List.copyOf(newIds)));
    } catch (RuntimeException e) {           // belt and braces: scoring must never fail a poll (SCOR-02)
        log.warn("Could not hand {} new articles to scoring", newIds.size(), e);
    }
}

// InterestScoringListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onArticlesIngested(ArticlesIngestedEvent event) {
    try {
        if (!jevApiClient.isConfigured()) return;
        scoringQueue.submitIngested(event.articleIds());
    } catch (RuntimeException e) {
        log.warn("Could not enqueue {} articles for scoring", event.articleIds().size(), e);
    }
}
```

The enqueue-time eligibility filter (one PK-indexed SQL in `submitIngested`) runs on the polling thread. It costs about the same as one of the poll's own `existsByFeedIdAndGuid` queries, and it keeps a subscribe/OPML back-catalogue flood (hundreds of IDs older than 14 days) out of the 1,000-slot queue. CONTEXT requires the predicate at enqueue.

### Pattern 2: Dedicated executor that doesn't displace Boot's, plus in-flight dedup that can't leak

**Boot fact:** `applicationTaskExecutor` is created only when there is no `Executor` bean, or when `spring.task.execution.mode=force` [VERIFIED: `spring-boot-autoconfigure-4.0.8` bytecode, `TaskExecutorConfigurations$OnExecutorCondition` = `@ConditionalOnMissingBean(Executor.class)` OR `@ConditionalOnProperty("spring.task.execution.mode", havingValue="force")`]. The documented way to keep it is `@Bean(defaultCandidate = false)` [CITED: spring-boot v4.0.3 docs, task-execution-and-scheduling.adoc, "Register Custom Executor with defaultCandidate=false"]. The app currently has an `applicationTaskExecutor`, because `TaskSchedulingAutoConfiguration` runs after `TaskExecutionAutoConfiguration` [VERIFIED: bytecode `@AutoConfiguration(after = TaskExecutionAutoConfiguration.class)`]. Nothing in `src/main` uses it today [VERIFIED: grep found no `@Async`/`Callable<`/`DeferredResult`/`SseEmitter`/`StreamingResponseBody`], so displacing it would be silent. That makes it more dangerous, not less.

**ThreadPoolExecutor fact:** with a bounded queue, the pool grows toward `maxPoolSize` once the queue is full. Set `maxPoolSize == corePoolSize == concurrency`, or a full queue spawns up to `Integer.MAX_VALUE` threads instead of rejecting.

```java
@Configuration(proxyBeanMethods = false)
public class InterestScoringConfig {
    public static final String EXECUTOR = "interestScoringExecutor";

    @Bean(name = EXECUTOR, defaultCandidate = false)   // keeps Boot's applicationTaskExecutor
    ThreadPoolTaskExecutor interestScoringExecutor(MyfeederProperties props) {
        var p = props.getInterest();
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(p.getConcurrency());          // D-09: default 1
        ex.setMaxPoolSize(p.getConcurrency());           // must equal core, see above
        ex.setQueueCapacity(p.getQueueCapacity());       // ~1000 → LinkedBlockingQueue (FIFO)
        ex.setThreadNamePrefix("jev-score-");
        // default AbortPolicy: execute() throws TaskRejectedException, which ScoringQueue catches
        return ex;                                        // Spring calls initialize() (InitializingBean)
    }
}

@Component
public class ScoringQueue {
    private final TaskExecutor executor;                 // ThreadPoolTaskExecutor in prod, SyncTaskExecutor in tests
    private final ArticleScoringService scorer;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    // Explicit constructor: no lombok.config exists, so @RequiredArgsConstructor would drop @Qualifier
    public ScoringQueue(@Qualifier(InterestScoringConfig.EXECUTOR) TaskExecutor executor, ...) { ... }

    public void submit(List<Long> ids) {
        int dropped = 0;
        for (Long id : ids) {
            if (!inFlight.add(id)) continue;             // already queued or running
            try {
                executor.execute(() -> run(id));
            } catch (TaskRejectedException e) {          // queue full or context closing
                inFlight.remove(id);                      // else the sweep can never re-enqueue it
                dropped++;
            }
        }
        if (dropped > 0) log.warn("Scoring queue full; dropped {} articles (the sweep will retry them)", dropped);
    }

    private void run(Long id) {
        try { scorer.score(id); }
        catch (RuntimeException e) { log.warn("Scoring article {} failed unexpectedly", id, e); }
        finally { inFlight.remove(id); }
    }

    public int remainingCapacity() {
        return executor instanceof ThreadPoolTaskExecutor tp
                ? tp.getQueueCapacity() - tp.getQueueSize()
                : Integer.MAX_VALUE;                      // SyncTaskExecutor: runs inline
    }
}
```
[VERIFIED: `spring-context-7.0.9` bytecode: `ThreadPoolTaskExecutor.execute` wraps `RejectedExecutionException` in `TaskRejectedException`; `createQueue` uses `LinkedBlockingQueue` for capacity > 0; `getQueueCapacity()`, `getQueueSize()`, `setAcceptTasksAfterContextClose` exist.]

### Pattern 3: The scorer (one article, no transaction across the call)

```java
public void score(long articleId) {
    if (!jevApiClient.isConfigured() || interestService.isColdStart()) return;      // SCOR-06
    Instant cutoff = eligibilityCutoff();
    Optional<Candidate> c = store.loadCandidate(articleId, cutoff, MAX_ATTEMPTS);   // same predicate + needs-scoring
    if (c.isEmpty()) return;                                                         // read, aged out, or done
    Article article = c.get().article();
    if (!StringUtils.hasText(article.getGuid())) { store.writeSkipped(articleId, "no guid"); return; }  // SCOR-08
    Map<String, Object> state = ArticleStateBuilder.build(c.get().feedTitle(), article);
    if (!ArticleStateBuilder.hasJudgeableText(state)) { store.writeSkipped(articleId, "no text"); return; }

    InterestProfile profile = interestService.getProfile();          // snapshot BEFORE the call:
    List<InterestTopic> topics = interestService.listTopics();       // stored versions = what was sent
    Map<String, Question> questions = InterestQuestions.forRubric(profile.getProfileText(), topics);
    if (questions.isEmpty()) return;                                 // cold-start race; never judge {}

    JevJudgment j;
    try {
        j = jevApiClient.judge(state, questions);                   // no @Transactional on this method
    } catch (RuntimeException e) {
        if (ScoringFailure.isPermanent(e)) store.writeFailed(articleId, ScoringFailure.describe(e));
        else log.debug("Transient scoring failure for {}: {}", articleId, e.getClass().getSimpleName());
        return;
    }
    store.writeScored(articleId, profile, topics, j);                // @Transactional on the store
}
```

Read nouls with `j.nouls().get(InterestQuestions.topicKey(t.getId()))` for each topic in the snapshot, and the profile with `j.scores().get(InterestQuestions.PROFILE_KEY)`. `judge()` has already checked that every requested answer is present [VERIFIED: `JevApiClientImpl.java:70-78`]. Keys: `PROFILE_KEY = "profile"`, `TOPIC_KEY_PREFIX = "topic_"` [VERIFIED: `InterestQuestions.java:24-25`]. `PROFILE_MAX_LEVEL = PROFILE_LEVELS.size() - 1` [VERIFIED: `InterestQuestions.java:35`]. Store `JevScore.maxLevel()` when it is `>= 0`, otherwise `InterestQuestions.PROFILE_MAX_LEVEL`. `JevJudgment` documents `-1` as the "no legend" sentinel: `public record JevScore(double value, int maxLevel, double confidence) {}` [VERIFIED: `JevJudgment.java:27`, doc line 21].

### Failure classification (SCOR-07)

SDK hierarchy [VERIFIED: `typesafe-java-sdk-0.1.0.jar` `javap`]: `TypeSafeException extends RuntimeException`; `TypeSafeApiException extends TypeSafeException`; under it `TypeSafeBadRequestException`, `TypeSafeAuthenticationException`, `TypeSafePermissionDeniedException`, `TypeSafeNotFoundException`, `TypeSafeUnprocessableEntityException`, `TypeSafeRateLimitException`, `TypeSafeInternalServerException` (subclass `TypeSafeOverloadedException`), `TypeSafeApiResponseValidationException`; `TypeSafeApiConnectionException extends TypeSafeException` (subclass `TypeSafeApiTimeoutException`); `TypeSafeMissingAnswerException` and `TypeSafeAnswerTypeException extends TypeSafeException`.

| Outcome | Exception types | Action |
|---------|-----------------|--------|
| Permanent (locked) | `TypeSafeBadRequestException`, `TypeSafeUnprocessableEntityException`, `TypeSafeMissingAnswerException`, `TypeSafeAnswerTypeException` | `writeFailed` (attempts + 1) |
| Transient (locked) | `TypeSafeRateLimitException`, `TypeSafeInternalServerException` (incl. `TypeSafeOverloadedException`), `TypeSafeApiConnectionException` (incl. timeout), `CallNotPermittedException`, `JevNotConfiguredException` | no row, no attempt |
| Global auth (locked: breaker records it) | `TypeSafeAuthenticationException`, `TypeSafePermissionDeniedException` | no row, no attempt |
| **Not decided** | `TypeSafeNotFoundException` (404, e.g. unknown model), `TypeSafeApiResponseValidationException`, other `TypeSafeApiException` statuses, plain `TypeSafeException` (body read/serialize), `IllegalArgumentException` (caller bug) | **Recommendation: permanent** (bounded to 3 billed tries; Re-score recovers them per D-03). See Open Question 1 |

A timeout arrives as `TypeSafeApiConnectionException`, never the timeout subtype [VERIFIED: `JevApiClient.java` Javadoc lines 24-26; `JevResilienceTest.readTimeoutSurfacesAsConnectionExceptionAndIsRetried`].

### Pattern 4: `ArticleScoreStore` — one predicate string, verified upserts

Use a `@Repository` class with `JdbcClient` (auto-configured in both the app and the `@DataJdbcTest` slice [VERIFIED: `spring-boot-jdbc-test-4.0.8` `AutoConfigureJdbc.imports` lists `JdbcClientAutoConfiguration`; `AutoConfigureDataJdbc` meta-imports `AutoConfigureJdbc`]). The `@DataJdbcTest` slice does **not** component-scan custom `@Repository` classes, so tests need `@Import({TestcontainersConfiguration.class, ArticleScoreStore.class})`.

Schema facts used below [VERIFIED: `V6__interest_scoring.sql:23-44`]:
- `article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,` (line 24)
- `status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED')),` (line 25)
- `profile_score DOUBLE PRECISION,` `profile_max_level INTEGER,` `profile_confidence DOUBLE PRECISION,` `profile_version INTEGER,` `model TEXT,` `request_id TEXT,` (lines 26-31)
- `attempts INTEGER NOT NULL DEFAULT 1,` `last_error TEXT,` `scored_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` (lines 32-34)
- `article_topic_score`: `article_id BIGINT NOT NULL REFERENCES article_score(article_id) ON DELETE CASCADE,` `topic_id BIGINT NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,` `noul DOUBLE PRECISION NOT NULL CHECK (noul BETWEEN 0 AND 1),` `topic_version INTEGER NOT NULL,` `PRIMARY KEY (article_id, topic_id)` (lines 39-43)
- `article`: `guid TEXT NOT NULL,` `published_at TIMESTAMPTZ,` `fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),` `read BOOLEAN NOT NULL DEFAULT FALSE,` [VERIFIED: `V1__initial_schema.sql:21,27-29`]

```java
// The ONE eligibility predicate (SCOR-04, D-02, D-12). Alias a = article.
static final String ELIGIBLE = "a.\"read\" = false AND COALESCE(a.published_at, a.fetched_at) > :cutoff";
static final String NEEDS_SCORING = "(s.article_id IS NULL OR (s.status = 'FAILED' AND s.attempts < :maxAttempts))";

// Sweep / enqueue filter — newest first
"SELECT a.id FROM article a LEFT JOIN article_score s ON s.article_id = a.id " +
"WHERE " + ELIGIBLE + " AND " + NEEDS_SCORING +
" ORDER BY COALESCE(a.published_at, a.fetched_at) DESC, a.id DESC LIMIT :limit"
// submitIngested adds: AND a.id IN (:ids)

// Status counts (JEV-05, D-11, D-12)
"SELECT COUNT(*) FILTER (WHERE " + NEEDS_SCORING + ") AS eligible_unscored, " +
"       COUNT(*) FILTER (WHERE s.status = 'FAILED' AND s.attempts >= :maxAttempts) AS failed " +
"FROM article a LEFT JOIN article_score s ON s.article_id = a.id WHERE " + ELIGIBLE

// Re-score (INT-05, D-02, D-03): count and delete share the predicate
static final String RESCORE_SCOPE = ELIGIBLE + " AND s.status IN ('SCORED', 'FAILED')";
"SELECT COUNT(*) FROM article_score s JOIN article a ON a.id = s.article_id WHERE " + RESCORE_SCOPE
"DELETE FROM article_score s USING article a WHERE a.id = s.article_id AND " + RESCORE_SCOPE

// SCORED write: overwrites FAILED once, never SCORED/SKIPPED (write-once). Returns 1 if written.
"INSERT INTO article_score (article_id, status, profile_score, profile_max_level, profile_confidence, " +
"  profile_version, model, request_id) " +
"SELECT a.id, 'SCORED', :profileScore, :profileMaxLevel, :profileConfidence, :profileVersion, :model, :requestId " +
"FROM article a WHERE a.id = :articleId " +
"ON CONFLICT (article_id) DO UPDATE SET status = 'SCORED', profile_score = EXCLUDED.profile_score, " +
"  profile_max_level = EXCLUDED.profile_max_level, profile_confidence = EXCLUDED.profile_confidence, " +
"  profile_version = EXCLUDED.profile_version, model = EXCLUDED.model, request_id = EXCLUDED.request_id, " +
"  last_error = NULL, scored_at = NOW() " +
"WHERE article_score.status = 'FAILED'"
// only if that returned 1, per topic in the snapshot (skips topics deleted mid-call instead of FK-failing):
"INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) " +
"SELECT :articleId, t.id, :noul, :topicVersion FROM interest_topic t WHERE t.id = :topicId " +
"ON CONFLICT (article_id, topic_id) DO NOTHING"

// FAILED write: attempts + 1 only while still FAILED
"INSERT INTO article_score (article_id, status, attempts, last_error) " +
"SELECT a.id, 'FAILED', 1, :lastError FROM article a WHERE a.id = :articleId " +
"ON CONFLICT (article_id) DO UPDATE SET attempts = article_score.attempts + 1, " +
"  last_error = EXCLUDED.last_error, scored_at = NOW() WHERE article_score.status = 'FAILED'"

// SKIPPED write: terminal, first writer wins
"INSERT INTO article_score (article_id, status, last_error) SELECT a.id, 'SKIPPED', :reason " +
"FROM article a WHERE a.id = :articleId ON CONFLICT (article_id) DO NOTHING"
```

**Verified live against `postgres:latest` (PostgreSQL 18.6) this session:** FAILED upsert run twice → `attempts` 2, `last_error` from the second write. SCORED over FAILED → row becomes SCORED with `attempts` kept. A second SCORED → `INSERT 0 0` (no-op). FAILED over SCORED → `INSERT 0 0`. `INSERT ... SELECT` for a missing article id → `INSERT 0 0` (no FK error). `JdbcClient` NULL params in `INSERT ... SELECT` → accepted (Spring resolves the parameter type from metadata). The existing indexes cover the predicate: `idx_article_read ON article(read) WHERE read = FALSE` and `idx_article_published_at` [VERIFIED: `V1__initial_schema.sql:35-36`].

**Bind the cutoff as `Timestamp.from(cutoff)`, not `Instant`** (Pitfall 7).

### Pattern 5: The sweep

```java
@Scheduled(fixedDelayString = "${myfeeder.interest.sweep-delay:PT2M}",
           initialDelayString = "${myfeeder.interest.sweep-initial-delay:PT1M}")
public void sweep() {
    try {
        if (!jevApiClient.isConfigured() || interestService.isColdStart()) return;
        CircuitBreaker.State s = breakerRegistry.circuitBreaker("jev").getState();
        if (s == CircuitBreaker.State.OPEN || s == CircuitBreaker.State.FORCED_OPEN) return;
        int room = scoringQueue.remainingCapacity();
        if (room <= 0) return;
        List<Long> ids = store.findNeedingScoring(cutoff(), MAX_ATTEMPTS, Math.min(room, batchCap));
        scoringQueue.submit(ids);
    } catch (RuntimeException e) {
        log.warn("Scoring sweep failed", e);
    }
}
```
`@Scheduled` string attributes accept ISO-8601 (`PT2M`) or simple (`2m`) durations [VERIFIED: `spring-context-7.0.9` `ScheduledAnnotationBeanPostProcessor.toDuration` → `DurationFormatterUtils.detectAndParse`]. Bind the same keys in `MyfeederProperties.Interest` as `Duration` so values stay documented and typed. Existing precedent: `@Scheduled(cron = "${myfeeder.retention.cleanup-cron}")` [VERIFIED: `RetentionService.java:21`].

**`batchCap`:** see Open Question 2. D-10 says "up to free capacity". A per-sweep cap of about 50 (≈ 2 min ÷ 2.6 s/call) keeps new arrivals from waiting behind up to 1,000 queued backlog items (≈ 43 min at 2.6 s/call on one FIFO thread) and costs no throughput.

### Pattern 6: Re-score (INT-05)

- `GET /api/interest/rescore` → `{"count": n}` using `RESCORE_SCOPE`.
- `POST /api/interest/rescore` → one `DELETE ... USING` (atomic, and topic rows cascade [VERIFIED: `V6__interest_scoring.sql:39` `ON DELETE CASCADE`]) → `{"count": deleted}`. No transaction or loop needed.
- Recommended guard: return 409 (`IllegalStateException`) when `!isConfigured() || isColdStart()`, because the delete would remove ranks and nothing would re-score them. The UI disables the button with a reason in the same states. See Open Question 3.
- Known benign race: with one thread, at most one article is mid-call when the DELETE runs. It then writes a SCORED row with the pre-edit versions. D-04 already forces edits to be saved before Re-score, so that call used the saved rubric unless it started before the save. The stored `profile_version`/`topic_version` expose it. Accept it.

### Frontend (small UI addition; `ui_phase` is enabled, so the UI-SPEC step may formalize copy)

- `InterestStatus` TS type gains `eligibleUnscored: number; failed: number` [current shape VERIFIED: `src/api/interest.ts:20-24` `configured: boolean`, `breakerState: string`, `coldStart: boolean`].
- `interestApi.getRescoreCount = () => apiGet<{count: number}>('/interest/rescore')`, `interestApi.rescore = () => apiPost<{count: number}>('/interest/rescore')`.
- Footer at `InterestsDialog.tsx:93` (`<p className="interests-note">Profile and topic changes apply to newly arriving articles.</p>` [VERIFIED]): replace it with a Re-score row. Clicking fetches the count (query `enabled` only while confirming, `staleTime: 0`), then shows an inline confirm styled like the existing `.interests-confirm` block (`App.css:497`). Disable with `title="Save your changes first"` when `profileDirty || dirtyTopics > 0`. `describeUnsaved` is at `InterestsDialog.tsx:34` [VERIFIED].
- "N waiting to be scored": show when `configured && !coldStart && eligibleUnscored > 0`. Poll with a conditional interval so existing tests stay valid: `refetchInterval: (query) => (query.state.data?.eligibleUnscored ?? 0) > 0 ? 15_000 : false` [ASSUMED: TanStack v5 function form of `refetchInterval` receives `query`]. `InterestsDialog.test.tsx:263` asserts exactly 2 status calls [VERIFIED], and its mocks omit the new fields, so the conditional form keeps that test green.
- Rescore mutation: `meta: { inlineError: true }`; `onSuccess` invalidates `['interest', 'status']`.
- Fold-in (optional, same file): `TOPICS_HELP` still says "primarily about" [VERIFIED: `InterestsDialog.tsx:31`]; change it to "substantially about".

### Anti-Patterns to Avoid
- **`@Transactional` on `ArticleScoringService.score`:** holds a Hikari connection across a call of up to about 93 s. Put transactions only on store write methods.
- **Pre-checking the breaker in the scorer** (`if OPEN return`): together with the sweep gate, nothing ever calls `judge()`, so the breaker stays OPEN forever (Pitfall 5).
- **`DiscardPolicy` / `DiscardOldestPolicy` on the executor:** a silent drop leaves the ID in `inFlight` permanently.
- **Defining the executor as a plain `@Bean`** (a default candidate): Boot's `applicationTaskExecutor` disappears.
- **Calling `scoringQueue` methods from the sweep with Jev calls inline, or using `@Async`:** `@EnableAsync` is absent, so `@Async` would run inline on the scheduler thread.
- **Persisting `e.getMessage()` / `e.toString()` into `last_error`:** TypeSafe messages can echo request details (Phase 2 D-06 logs status and requestId only). Store fixed text: `simpleClassName + " (HTTP " + status + ", requestId " + requestId + ")"`.
- **Re-implementing cold start** (`profile blank && topics empty`) anywhere: call `InterestService.isColdStart()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Work queue / worker threads | Custom `Thread` + `BlockingQueue` loop | `ThreadPoolTaskExecutor` (core=max=1, capacity 1000) | Lifecycle (context-close rejection, shutdown), naming, testable swap with `SyncTaskExecutor` |
| Retry / breaker in the scorer | Try-count loops, sleep/backoff | Existing `@CircuitBreaker` + `@Retry("jev")` on `JevApiClientImpl` | The single retry layer is a project decision; a service-level retry would stack attempts |
| Idempotent writes / write-once | SELECT-then-INSERT in Java | `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE` | Atomic and race-free between the ingest path and the sweep; verified semantics |
| Cascading delete of topic scores on Re-score | Explicit two-step deletes | V6 `ON DELETE CASCADE` | Already in schema |
| Periodic job | `ScheduledExecutorService` | `@Scheduled(fixedDelayString=...)` | `@EnableScheduling` already on; fixedDelay never overlaps itself |
| Breaker-state recovery probe | Custom "probe every N min" timer | `automatic-transition-from-open-to-half-open-enabled: true` | Built-in; HALF_OPEN then permits 3 probe calls |
| HTML strip / truncation | New helper | `ArticleStateBuilder` (after the D-13 fix) | Shared with the preview; must stay identical |

**Key insight:** the pipeline's correctness lives in SQL (the predicate plus the upserts). Everything in memory (queue, in-flight set, event) is a best-effort fast path that is allowed to drop work.

## Common Pitfalls

### Pitfall 1: The new executor silently removes `applicationTaskExecutor`
**What goes wrong:** Registering `interestScoringExecutor` as a normal bean makes Boot's `@ConditionalOnMissingBean(Executor.class)` back off.
**How to avoid:** `@Bean(defaultCandidate = false)` + `@Qualifier` injection through an explicit constructor. There is **no `lombok.config`** [VERIFIED: `ls lombok.config` → not found], so `@RequiredArgsConstructor` does not copy field `@Qualifier`s.
**Warning sign:** `applicationTaskExecutor` missing from the context. Add a context assertion in `MyfeederApplicationTests`: `assertThat(ctx.containsBean("applicationTaskExecutor")).isTrue()`.

### Pitfall 2: Pool grows past 1 thread when the queue fills
`ThreadPoolTaskExecutor` default `maxPoolSize` is `Integer.MAX_VALUE`. Set max = core [ASSUMED: standard `ThreadPoolExecutor` semantics; set both explicitly regardless].

### Pitfall 3: Dropped tasks leak in-flight IDs
Covered in Pattern 2. Test: fill a 1-slot queue (with a blocked worker), submit 2 more, and assert that the rejected ID is not in-flight and a later `submit` accepts it.

### Pitfall 4: `FeedPollingServiceTest` breaks when the constructor changes
`@InjectMocks` with constructor injection passes `null` for an unmocked `ApplicationEventPublisher`. The test's `articleRepository.save` mock also returns `null`, so `saved.getId()` would NPE inside `pollFeed`'s try, and the catch **increments `errorCount`**. `shouldSaveOnlyNewArticles` asserts on the feed [VERIFIED: `FeedPollingServiceTest.java:30-36,74-80`]. Add `@Mock ApplicationEventPublisher` and stub `save` to assign IDs (`thenAnswer(inv -> { Article a = inv.getArgument(0); a.setId(seq++); return a; })`).

### Pitfall 5: The breaker never leaves OPEN (scoring paused indefinitely)
**What goes wrong:** Resilience4j moves OPEN → HALF_OPEN only when a call asks for permission after `wait-duration-in-open-state` [VERIFIED: `resilience4j-circuitbreaker-2.3.0` `CircuitBreakerStateMachine$OpenState.tryAcquirePermission` calls `toHalfOpenState()` after the clock check]. `automaticTransitionFromOpenToHalfOpenEnabled` defaults to `false` [VERIFIED: `CircuitBreakerConfig$Builder` bytecode `iconst_0` → field]. With D-10's sweep skipping on OPEN, recovery depends on an ingest event or a preview happening to call `judge()`. `/status` shows `OPEN` until then.
**How to avoid:** Add `automatic-transition-from-open-to-half-open-enabled: true` to `resilience4j.circuitbreaker.instances.jev` in **both** YAMLs (the setter exists on `CommonCircuitBreakerConfigurationProperties$InstanceProperties` [VERIFIED]), assert it in `mainYamlJevInstancesBindAsSpecified`, and never pre-check the breaker in the scorer. D-10's OPEN gate is kept: the state flips to HALF_OPEN after 60 s, and the next sweep resumes. See Open Question 4.

### Pitfall 6: The NULL-GUID "re-insert bug" does not exist in this schema
**Facts:** `guid TEXT NOT NULL,` [VERIFIED: `V1__initial_schema.sql:21`]; no later migration mentions `guid` except `UNIQUE (feed_id, guid)` (line 31) [VERIFIED: grep of all migrations]. Falsification run against real Postgres this session: `INSERT INTO article (feed_id, guid, title, url) VALUES (1, NULL, 't', 'u');` → `ERROR:  null value in column "guid" of relation "article" violates not-null constraint`. So a GUID-less parsed item makes `articleRepository.save` throw inside `pollFeed`'s try. Articles before it in the loop are saved, the rest are not, and the feed's `errorCount` increments on every poll. That is a pre-existing polling bug (QUAL-V2-01 territory), not a scoring one. (`url TEXT NOT NULL` fails the same way for link-less items.)
**Implications:** keep the SCOR-08 guard (`!StringUtils.hasText(guid)` → SKIPPED). Blank `""` GUIDs can exist, so the guard isn't dead code. Test it with a Mockito unit test (null and `""`) or a `@DataJdbcTest` row with `guid = ''`. **Do not** plan research's "GUID-less fixture polled 3 times" integration test: the poll fails instead. Tell the user this correction (PROJECT.md Key Decision row 88 rests on the wrong premise).

### Pitfall 7: `Instant` parameters fail in `JdbcClient` / `JdbcTemplate`
Run this session: `ps.setObject(1, Instant...)` on PgJDBC 42.7.13 → `PSQLException: Can't infer the SQL type to use for an instance of java.time.Instant`. Through `JdbcClient` → `BadSqlGrammarException`. `Timestamp.from(instant)` and `OffsetDateTime` work. Spring's `StatementCreatorUtils` 7.0.9 special-cases `LocalDate/LocalTime/LocalDateTime/OffsetTime/OffsetDateTime/Timestamp` but not `Instant` [VERIFIED: bytecode]. Spring Data JDBC `@Query` repository methods *do* convert `Instant`, which is why `ArticleRepository.markReadByFeedIdOlderThan(Long, Instant)` works.

### Pitfall 8: D-07 changes three existing test expectations and needs a YAML mirror outside `instances.jev`
- `JevResilienceTest.breakerOpensAtMinimumCallsAndShortCircuits` asserts `stub.hits()` `isEqualTo(9)` and failed calls `isEqualTo(9)` with the comment "Retry is the outer aspect" [VERIFIED: `JevResilienceTest.java:235-248`]. Rewrite it: 10 logical calls × 3 attempts = 30 hits and 10 recorded failures → OPEN, then the 11th call → `CallNotPermittedException` with hits still 30.
- `mainYamlJevInstancesBindAsSpecified` asserts `Duration.ofSeconds(3)` [VERIFIED: line 311] → 15 s.
- `TypeSafeConfigTest.mainYamlPinsModelTimeoutAndDisablesSdkRetries` asserts `Duration.ofSeconds(5)` [VERIFIED: `TypeSafeConfigTest.java:146`] → 30 s. `testYamlMirrorsMainTypeSafePinsWithoutKey` requires the test YAML `spring.ai.typesafe.timeout` to equal main's, so change both files.
- The aspect-order keys live at `resilience4j.circuitbreaker.circuit-breaker-aspect-order` / `resilience4j.retry.retry-aspect-order` [VERIFIED: `resilience4j-spring-boot3-2.3.0` `spring-configuration-metadata.json`], **outside** `instances.jev`, so `testYamlMirrorsMainJevInstances` (which filters on `...instances.jev.` prefixes [VERIFIED: lines 397-406]) won't catch a missing mirror. Put them in both YAMLs and extend the mirror test (or assert the order in a `@SpringBootTest`).
- Defaults being overridden: retry `2147483642` (`Integer.MAX_VALUE - 5`), breaker `2147483643` (`Integer.MAX_VALUE - 4`) [VERIFIED: `resilience4j-spring6-2.3.0` `RetryConfigurationProperties`/`CircuitBreakerConfigurationProperties` constructors]. Lower value = outer (Spring `Ordered`).

### Pitfall 9: D-07 side effects on Raindrop (global aspect order)
Raindrop has `@CircuitBreaker(name = "raindrop", fallbackMethod = ...)` + `@Retry(name = "raindrop")`, and its retry instance has no `retry-exceptions` [VERIFIED: `RaindropApiClientImpl.java:32-33,52-53`; `application.yaml:62-68`].
- **Before (Retry outer):** each attempt goes through breaker → fallback → `IllegalStateException`, and Retry retries that ISE. HTTP attempts: 3. Breaker records 3 per call, so it opens after about 2 failed saves (min 5 calls). When OPEN: `CallNotPermitted` → fallback ISE → retried 3× with 1 s waits.
- **After (breaker outer):** Retry retries the raw `RestClient` exception up to 3 times (same attempt count). The breaker records 1 per call, so it opens after 5 failed saves. When OPEN: immediate fallback ISE (no wasted waits). `RaindropNotConfiguredException` behavior is unchanged (ignored by both, rethrown by the fallback).
- Existing `RaindropApiClientImplTest` builds the client without a proxy (`MockRestServiceServer.bindTo(builder)`) [VERIFIED: lines 42-48], so it is unaffected. No Raindrop test asserts attempt counts. Net effect: benign. Record it in the SUMMARY and update the pending todo's "Retry is the outer aspect" sentence.

### Pitfall 10: Worst-case call time with a 30 s timeout
One logical `judge()` = up to 3 attempts × 30 s read timeout + 1 s + 2 s backoff ≈ 93 s (plus a 5 s connect timeout per attempt if connects hang). With CB outer, a timeout-exhausted call is **one failure** (not slow), and 10 such articles open the breaker (about 15 min at worst on one thread). The preview shares the timeout (D-05), so a preview can take that long too. If the frontend preview spinner has no ceiling, note it for UAT. The slow-call threshold (15 s) is measured over the whole logical call, including retry waits, so a 429 with a 10 s `retry-after-ms` plus a retry can register as slow. That is the intended "degraded" signal.

### Pitfall 11: A DB write failing after a billed success loops forever
If `writeScored` throws (for example a `noul` outside `[0,1]` violating the CHECK, or NaN), no row is written, so the sweep re-judges and re-bills the article every 2 minutes. Mitigate: validate and clamp `noul`/`profile_score` (finite, within bounds) before writing, and if the SCORED write throws, attempt `writeFailed(id, "write failed")` so the 3-attempt bound applies.

### Pitfall 12: `@SpringBootTest` contexts now run the sweep
Every `@SpringBootTest` (for example `InterestApiIntegrationTest`, `MyfeederApplicationTests`) starts the scheduler. The test YAML has no TypeSafe key, so the sweep exits at `isConfigured()`, which is harmless. A test that sets a fake key **and** a profile will start scoring against `http://127.0.0.1:9` (connection refused → transient). Use a long `sweep-initial-delay` in the test YAML (for example `PT1H`), or `@MockitoBean` the store/queue in such tests.

## Code Examples

### D-13 truncate fix (CR-01)
```java
// ArticleStateBuilder.truncate — current loop: for (int i = max - 2; i > 0; i--) {   [VERIFIED: line 61]
static String truncate(String text, int max) {
    if (text.length() <= max) {
        return text;
    }
    int cut = -1;
    for (int i = max - 2; i >= max / 2; i--) {           // D-13: only accept a cut that keeps most of the text
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
Tests: `"标题 " + "字".repeat(2000)` → length `== MAX_SUMMARY_CHARS` (hard cut + ellipsis). `"Link: https://example.com/" + "a".repeat(2000)` → same. Existing whitespace-dense tests (`ArticleStateBuilderTest` lines 64-112) must still pass. `MAX_SUMMARY_CHARS = 1500` [VERIFIED: `ArticleStateBuilder.java:24`].

### D-14 / D-15 in `JevApiClientImpl.judge` (before the HTTP call)
```java
requireConfigured();
Assert.notNull(state, "state must not be null");
Assert.notEmpty(questions, "questions must not be empty");
questions.forEach((name, q) -> Assert.isTrue(q instanceof Noul || q instanceof Score,
        () -> "Unsupported question type for '" + name + "'"));      // D-15: Choice rejected before billing
```
```yaml
# both application.yaml files, resilience4j.circuitbreaker.instances.jev.ignore-exceptions (append)
          - java.lang.IllegalArgumentException
```
Retry does not retry IAE: its `retry-exceptions` allow-list is RateLimit/InternalServer/ApiConnection only [VERIFIED: `application.yaml:74-77`]. New `JevResilienceTest` cases: `judge(null, questions())` → IAE, `stub.hits() == 0`, `getNumberOfFailedCalls() == 0`. A `Choice.builder().instructions("q").option("a").option("b").build()` question → IAE, 0 hits, 0 failures [VERIFIED: `Choice$Builder` has `instructions(String)`, `option(String)`, `build()`]. Also assert `ignored.test(new IllegalArgumentException("x"))` is true.

### D-05 / D-06 / D-07 / auto-transition config (both YAMLs)
```yaml
spring:
  ai:
    typesafe:
      timeout: 30s          # was 5s (application.yaml:17, test application.yaml:10)

resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1        # D-07: breaker outer (lower = outer)
    instances:
      jev:
        slow-call-duration-threshold: 15s  # was 3s (application.yaml:51)
        automatic-transition-from-open-to-half-open-enabled: true   # Pitfall 5 (see Open Question 4)
  retry:
    retry-aspect-order: 2                  # D-07: retry inner
```
An aspect-order test through the real context: `ctx.getBean(CircuitBreakerAspect.class).getOrder() < ctx.getBean(RetryAspect.class).getOrder()` (both implement `Ordered` [VERIFIED: `CircuitBreakerAspect implements org.springframework.core.Ordered`, `getOrder()` returns `circuitBreakerAspectOrder`]).

### `myfeeder.interest.*` properties (recommended names/defaults — discretion)
```yaml
myfeeder:
  interest:
    window-days: 14
    concurrency: 1
    queue-capacity: 1000
    sweep-delay: PT2M
    sweep-initial-delay: PT1M
    sweep-batch-size: 50      # only if Open Question 2 is accepted
```
Mirror the block in `src/test/resources/application.yaml`. CLAUDE.md requires `myfeeder.*` in the test YAML. Use `PT1H` for `sweep-initial-delay` there (Pitfall 12). Keep `MAX_ATTEMPTS = 3` as a service constant (SCOR-07 fixes it; CONTEXT does not list it as configurable).

### `InterestStatus` extension (JEV-05)
```java
// current: public record InterestStatus(boolean configured, String breakerState, boolean coldStart) {}  [VERIFIED: InterestStatus.java:17]
public record InterestStatus(boolean configured, String breakerState, boolean coldStart,
                             long eligibleUnscored, long failed) {}
```
Only one constructor call site in `src/main` [VERIFIED: grep `new InterestStatus(` → `InterestStatusService.java:22`]. Update `InterestStatusServiceTest`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Any `Executor` bean silently replaces Boot's task executor | `@Bean(defaultCandidate = false)` keeps both | Spring Framework 6.2 / Boot 3.4+ (documented in Boot 4 reference) | Use it for the scoring executor |
| `DiscardPolicy` for "drop on overflow" | Abort + catch `TaskRejectedException` (wrapper since Spring 3.0) | — | Lets the caller clean up in-flight state |
| Resilience4j default Retry-outer ordering assumed "CB outer" | Explicit `*-aspect-order` properties | Documented in the Resilience4j Spring Boot 3 notes | D-07 |

**Deprecated/outdated:**
- `spring.http.client.*` singular keys (Boot 4) — not touched here.
- Research's "NULL GUID re-inserts every poll" — incorrect for this schema (Pitfall 6).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | TanStack Query v5 `refetchInterval` accepts `(query) => number \| false` | Frontend | Low: fall back to a `useEffect`-driven `refetch` or a fixed interval gated on dialog open |
| A2 | `ThreadPoolTaskExecutor` default `maxPoolSize` is `Integer.MAX_VALUE` (pool grows when the bounded queue is full) | Pitfall 2 | None if max = core is set explicitly, as recommended |
| A3 | Relaxed binding maps `automatic-transition-from-open-to-half-open-enabled` to `InstanceProperties.setAutomaticTransitionFromOpenToHalfOpenEnabled` (setter verified; kebab key not in metadata because instances are map-typed) | Pitfall 5 | Low: the recommended binding assertion in `mainYamlJevInstancesBindAsSpecified` catches it |
| A4 | Per-sweep batch cap (~50) is compatible with D-10's "up to the executor's free queue capacity" | Pattern 5 | Medium: user may prefer the literal full-capacity fill; then fresh arrivals can wait up to ~43 min behind backlog during the launch backfill |
| A5 | Unlisted exception types (404, response-validation, generic `TypeSafeException`, IAE) should be PERMANENT | Failure classification | Medium: if treated transient instead, a deterministic post-billing failure re-bills every 2 min forever |
| A6 | A server-side 409 guard on `POST /rescore` when unconfigured/cold start is wanted | Pattern 6 | Low: UI already disables; server guard is extra safety |
| A7 | Storing fixed-text `last_error` (class + status + requestId) satisfies intent; nothing in Phase 4 reads it | Anti-patterns / Security | Low |

## Open Questions (RESOLVED)

All five were answered by the user after research and recorded in 04-CONTEXT.md ("Post-research decisions", 2026-09-23).

1. **Classification of exception types CONTEXT doesn't list** (`TypeSafeNotFoundException`, `TypeSafeApiResponseValidationException`, other `TypeSafeApiException` statuses, plain `TypeSafeException`, `IllegalArgumentException`).
   - Known: the locked lists cover 400/422/answer errors (permanent) and 429/5xx/connection/CNP/not-configured/401/403 (transient).
   - Unclear: the rest.
   - Recommendation: **permanent** (bounded 3 attempts, Re-score recovers). The planner can adopt it as Claude's discretion ("classification mechanics") or confirm with the user.
   - RESOLVED by **D-19**: unlisted exceptions are permanent (FAILED, attempt used). Implemented in plan 04-03 (`ScoringFailure`).
2. **Per-sweep batch cap vs "fill free capacity" (D-10).**
   - Recommendation: `min(remainingCapacity, sweep-batch-size=50)`. It keeps fresh ingest near the head of the FIFO and costs no throughput at 1 thread. If rejected, implement D-10 literally.
   - RESOLVED by **D-16**: `min(free queue capacity, batch cap ≈ 50)`, configurable under `myfeeder.interest.*`. Implemented in plan 04-05.
3. **Server-side guard on `POST /api/interest/rescore`** when not configured or cold start. Recommendation: 409 with fixed text, plus a UI disable with a reason.
   - RESOLVED by **D-18**: 409 via `IllegalStateException` → `GlobalExceptionHandler` when unconfigured or in cold start. Implemented in plan 04-08; the UI disable is in plan 04-07.
4. **Enable `automatic-transition-from-open-to-half-open-enabled` on `jev`.** This is not in CONTEXT, but without it D-10's OPEN gate can stall recovery until an unrelated ingest or preview call. Recommendation: enable it (config-only; mirrored and asserted). Alternative: the sweep enqueues one probe ID while OPEN.
   - RESOLVED by **D-17**: enabled on the `jev` instance in both YAML files. Implemented in plan 04-01.
5. **SCOR-08 premise correction.** Tell the user that the "re-insert" bug is really a "poll fails on a GUID-less item" bug (Pitfall 6). The scoring guard is still built; the parser fix remains out of scope (QUAL-V2-01).
   - RESOLVED (informational): recorded in 04-CONTEXT.md as the "SCOR-08 premise correction". The scorer keeps the null/blank-GUID → SKIPPED guard (plan 04-03) and no "GUID-less fixture polled 3 times" test is planned.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers (all repo/integration tests) | ✓ | 29.7.2 | — |
| JDK | Gradle toolchain 25 | ✓ | openjdk 25.0.4 | — |
| Node / npm | Frontend tests, `tsc -b` | ✓ | v26.9.0 / 11.19.1 (node_modules present) | — |
| Postgres (Testcontainers `postgres:latest`) | SQL tests | ✓ | 18.6 (pulled this session) | — |
| TypeSafe API key | Optional manual UAT only (live scoring) | ✗ in this shell (`.envrc` references it; direnv not loaded here) | — | All automated tests use stubs/mocks; live smoke is end-of-phase human check |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** live key (manual UAT only).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ via Spring Boot 4.0.8 test starters; Testcontainers Postgres; Vitest 4 + React Testing Library (frontend) |
| Config file | `build.gradle.kts` (JUnit platform), `src/test/resources/application.yaml` (shadows main), `src/main/frontend/vite.config.*` / vitest |
| Quick run command | `./gradlew test --tests "org.bartram.myfeeder.service.ArticleScoringServiceTest"` (per touched class) |
| Full suite command | `./gradlew test && (cd src/main/frontend && npm test && npx tsc -b)` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCOR-01 | Scorer builds state via `ArticleStateBuilder`, questions via `forRubric`, one `judge()` call; truncate keeps ≥ max/2 on CJK/URL text | unit | `./gradlew test --tests "*.ArticleScoringServiceTest" --tests "*.ArticleStateBuilderTest"` | ArticleStateBuilderTest ✅ (extend); ArticleScoringServiceTest ❌ Wave 0 |
| SCOR-01 | `pollFeed` publishes one event with only new IDs; none when 0 new | unit | `./gradlew test --tests "*.FeedPollingServiceTest"` | ✅ (extend; add `@Mock ApplicationEventPublisher`, id-assigning `save` stub) |
| SCOR-02 | Poll returns promptly and `errorCount` unchanged while Jev blocks (latch simulating 30 s) with the real 1-thread executor; same when Jev throws, when unconfigured, and when the listener's queue throws | unit (composed real listener + `ScoringQueue` + real `ThreadPoolTaskExecutor`, mocked Jev) | `./gradlew test --tests "*.ScoringIsolationTest"` | ❌ Wave 0 |
| SCOR-02 | Rejected task removes ID from in-flight; dedup of concurrent submits; `remainingCapacity` | unit | `./gradlew test --tests "*.ScoringQueueTest"` | ❌ Wave 0 |
| SCOR-02 | `applicationTaskExecutor` still present; scoring executor is qualified | integration | `./gradlew test --tests "*.MyfeederApplicationTests"` | ✅ (extend) |
| SCOR-03 | Upserts: FAILED increments, SCORED overwrites FAILED once, SCORED/SKIPPED write-once, topic rows written in the same tx, deleted topic skipped, missing article no-op | repository (`@DataJdbcTest` + `@Import(ArticleScoreStore)`) | `./gradlew test --tests "*.ArticleScoreStoreTest"` | ❌ Wave 0 |
| SCOR-04 | Predicate: unread + window on `COALESCE(published_at, fetched_at)`; newest-first order; read/aged-out excluded | repository | `./gradlew test --tests "*.ArticleScoreStoreTest"` | ❌ Wave 0 |
| SCOR-05 | Sweep: skips when unconfigured / cold start / OPEN / FORCED_OPEN; enqueues `min(room, cap)` newest-first; runs in HALF_OPEN | unit | `./gradlew test --tests "*.InterestScoringSweepTest"` | ❌ Wave 0 |
| SCOR-06 | Cold start → no judge, no rows (scorer and sweep) | unit | `./gradlew test --tests "*.ArticleScoringServiceTest" --tests "*.InterestScoringSweepTest"` | ❌ Wave 0 |
| SCOR-07 | Classification table: permanent → `writeFailed`; transient/auth/CNP/not-configured → no write; exhausted not re-selected | unit + repository | `./gradlew test --tests "*.ArticleScoringServiceTest" --tests "*.ArticleScoreStoreTest"` | ❌ Wave 0 |
| SCOR-08 | Null/blank GUID → SKIPPED, no judge | unit | `./gradlew test --tests "*.ArticleScoringServiceTest"` | ❌ Wave 0 |
| JEV-05 | `/status` has `eligibleUnscored` and `failed` with D-11/D-12 semantics | unit + integration | `./gradlew test --tests "*.InterestStatusServiceTest" --tests "*.InterestApiIntegrationTest"` | ✅ (extend both) |
| INT-05 | GET count == rows the POST deletes; SKIPPED untouched; topic rows cascade; 409 guard (if adopted) | controller (`@WebMvcTest`) + integration | `./gradlew test --tests "*.InterestRescoreControllerTest" --tests "*.InterestApiIntegrationTest"` | ❌ Wave 0 / ✅ extend |
| INT-05 | Dialog: Re-score disabled with "Save your changes first" when dirty; confirm shows server count; confirm POSTs; "N waiting" line | frontend | `cd src/main/frontend && npx vitest run src/components/InterestsDialog.test.tsx` | ✅ (extend) |
| D-07 | 10 failing calls → 30 hits, 10 recorded failures, OPEN; CB aspect order < Retry order | unit (real AOP proxy, `ApplicationContextRunner`) | `./gradlew test --tests "*.JevResilienceTest"` | ✅ (rewrite one test, add cases) |
| D-05/06 | timeout 30 s; slow-call 15 s; YAML mirrors (incl. aspect-order keys) | unit | `./gradlew test --tests "*.TypeSafeConfigTest" --tests "*.JevResilienceTest"` | ✅ (update) |
| D-14/15 | `judge(null, …)` and Choice question → IAE, 0 hits, 0 recorded failures | unit | `./gradlew test --tests "*.JevResilienceTest"` | ✅ (add cases) |

### Sampling Rate
- **Per task commit:** the quick `--tests` command(s) for the classes the task touched, plus `npx vitest run <file>` for frontend tasks.
- **Per wave merge:** `./gradlew test` (full backend; needs Docker) and `cd src/main/frontend && npm test && npx tsc -b`.
- **Phase gate:** full suite green before `/gsd-verify-work`. End-of-phase human check: with a live key, confirm new articles get `article_score` rows after a poll, `/status` counts drain, and Re-score works.

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java` — SCOR-03/04/07, INT-05 SQL (needs `@Import(TestcontainersConfiguration.class, ArticleScoreStore.class)`)
- [ ] `src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java` — SCOR-01/06/07/08
- [ ] `src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java` — SCOR-02 in-flight/rejection
- [ ] `src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java` — SCOR-02 / success criterion 2
- [ ] `src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java` — SCOR-05/06
- [ ] `src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java` — INT-05
- [ ] Test YAML: `myfeeder.interest.*` block (with long `sweep-initial-delay`), aspect orders, timeout, slow-call, IAE ignore, auto-transition
- Framework install: none needed.

## Security Domain

`security_enforcement: true`, ASVS level 1 [VERIFIED: `.planning/config.json`].

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user LAN app with no auth layer (existing posture; unchanged) |
| V3 Session Management | no | — |
| V4 Access Control | no (same posture) | New endpoints match existing `/api/interest/*` exposure |
| V5 Input Validation | yes | `POST /rescore` takes no body. Feed text reaches Jev only as data in the state object (never interpolated into question text, per the Pitfall 14 design in `ArticleStateBuilder`). HTML is capped at 50,000 chars before jsoup and the summary at 1,500 |
| V6 Cryptography | no | API key handling unchanged (Supplier-based, never logged) |
| V7 Error Handling & Logging | yes | Fixed-text `last_error` (class + status + requestId), never exception messages or bodies. WARN logs carry counts/IDs, never article text or the key |
| V8 Data Protection | yes (minor) | Scores stay in DB; `/status` exposes counts only |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via feed title/summary | Tampering | Content only in `state`; questions are fixed templates plus the user's own profile/topic text (existing builders) |
| Cost/rate amplification (flood, retry loops, repeated Re-score clicks) | Denial of Service | Eligibility window; 3-attempt bound; permanent-by-default for unknown errors; single thread; UI disables the button while the mutation is pending; transient failures write no row but depend on the breaker to pause |
| Leaking request details/key into DB or logs | Information Disclosure | Fixed-text `last_error`; reuse the Phase 2 D-06 pattern (status + requestId only) |
| SQL injection in new queries | Tampering | Named parameters only; the predicate is a compile-time constant, never user input |
| Scoring failure degrading polling | Denial of Service | Listener catches everything, plus a try/catch around `publishEvent`; bounded queue with abort-and-catch |

## Sources

### Primary (HIGH confidence — verified this session)
- Resolved classpath: `./gradlew dependencies --configuration runtimeClasspath` (resilience4j 2.3.0, spring-context/jdbc 7.0.9, postgresql 42.7.13, jsoup 1.11.2, typesafe 0.1.0, aspectjweaver 1.9.25.1)
- Bytecode (`javap`): `spring-boot-autoconfigure-4.0.8` (`TaskExecutorConfigurations$OnExecutorCondition`, `TaskSchedulingAutoConfiguration` ordering); `resilience4j-spring6-2.3.0` (aspect-order defaults, `CircuitBreakerAspect implements Ordered`); `resilience4j-spring-boot3-2.3.0` (prefixes + metadata keys); `resilience4j-circuitbreaker-2.3.0` (OpenState transition, auto-transition default false); `resilience4j-framework-common-2.3.0` (InstanceProperties setter); `spring-context-7.0.9` (`ThreadPoolTaskExecutor` API, `TaskRejectedException` wrapping, `@Scheduled` duration parsing); `spring-jdbc-7.0.9` (`StatementCreatorUtils` types); `typesafe-java-sdk-0.1.0` (exception hierarchy, `Choice$Builder`); `spring-boot-jdbc-test`/`data-jdbc-test-4.0.8` (slice imports)
- Live Postgres 18.6 container: NOT NULL guid falsification; upsert semantics; PgJDBC `Instant` failure; `JdbcClient` null params in `INSERT ... SELECT`
- Repository source read this session: `FeedPollingService`, `FeedPollingScheduler`, `JevApiClient(Impl)`, `JevJudgment`, `ArticleStateBuilder`, `InterestQuestions`, `InterestService`, `InterestStatus(Service)`, `InterestPreviewService`, `TypeSafeConfig`, `RaindropApiClientImpl`, `MyfeederProperties`, `ArticleRepository`, models, V1/V6 migrations, main+test `application.yaml`, `JevResilienceTest`, `TypeSafeConfigTest`, `FeedPollingServiceTest`, `InterestsDialog.tsx`(+test), `api/interest.ts`, `hooks/useInterest.ts`, `api/client.ts`
- Context7 `/spring-projects/spring-boot/v4.0.3`: task-execution-and-scheduling (`defaultCandidate = false`, `spring.task.execution.mode: force`)

### Secondary (MEDIUM confidence)
- Context7 `/websites/deepwiki_resilience4j_resilience4j`: Spring Boot 3 aspect-order guidance (CB 1 / Retry 2). Its default-order numbers differ by one from the 2.3.0 bytecode; the bytecode was used.
- Milestone research `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md` (settled design, now corrected on the NULL-GUID premise)

### Tertiary (LOW confidence)
- None relied upon.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; versions resolved from Gradle.
- Architecture: HIGH — settled in CONTEXT; the mechanics were verified in bytecode and on a live DB.
- Pitfalls: HIGH — Pitfalls 1, 5, 6, 7, 8 and 9 were verified against jars, source or a live DB; Pitfall 2 rests on standard JDK semantics.
- Open questions 1–4 are policy choices, not technical unknowns.

**Research date:** 2026-09-23
**Valid until:** 2026-10-23 (stable stack; revisit if Resilience4j, Boot or the TypeSafe SDK versions change)
