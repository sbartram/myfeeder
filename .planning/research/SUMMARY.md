# Project Research Summary

**Project:** myfeeder: interest ranking milestone (TypeSafe Jev)
**Domain:** Adding judgment-model ("priority inbox") article ranking to an existing self-hosted, single-user Spring Boot 4 + React feed reader
**Researched:** 2026-09-22
**Confidence:** HIGH for the build approach (library compatibility was checked by running code; integration points were read from source). MEDIUM for ranking quality (blend constants and question wording need tuning on real data).

> **Orchestrator note (added after synthesis):** The user added a **dependency-upgrade phase before all Jev work** (Spring Boot 4.0.3→4.0.8, Spring AI BOM 2.0.0-M2→2.0.1, Spring Cloud 2025.1.0→2025.1.3, frontend minor/patch bumps). The stack verification below was done on Boot 4.0.3 / Spring AI M2. After the upgrade, the TypeSafe starter (built against Boot 4.0.7) runs closer to its native baseline; re-run the key-state context tests in the Jev foundation phase. The phase numbers below shift by +1 in the roadmap.

## Executive Summary

This milestone adds a priority inbox to a feed reader, the same pattern as Feedly Priorities, NewsBlur prompt classifiers and TT-RSS point scoring. Every new article is judged once by TypeSafe Jev against a written interest profile and a weighted topic rubric. The raw judgments are stored, and a blended score is computed at query time so weight edits and thumbs feedback re-rank instantly with no new API calls. The planned design is mainstream. What decides whether it succeeds is not the model but four things: explainability (an exact arithmetic "why this score"), feedback semantics (reversible, bounded, never flipping a declared dislike), list stability while triaging, and a hard guarantee that scoring never touches feed polling.

**Recommended approach.** Use `spring-ai-starter-typesafe` 0.1.0, which was verified to work on Boot 4.0.3, Java 25 and the Spring AI 2.0.0-M2 BOM; it has no Spring AI dependency at all. myfeeder should **own the `TypeSafeClient` bean** so that a blank key cannot crash startup. Wrap calls in a `JevApiClientImpl` with `@CircuitBreaker` + `@Retry`, with the SDK's own retries turned off. Scoring runs off the polling thread: an after-ingest event feeds a small bounded executor, and the database is the source of truth. A periodic sweep of eligible unscored articles is both the safety net and the launch backfill. Store raw Jev outputs in separate tables (`article_score`, `article_topic_score`), never as columns on `article`. Blend in one SQL CTE, which is the single source of truth for the sort, the badge and the breakdown. Feedback is derived from an `article_feedback` table, not accumulated into weights.

**Key risks.**
1. Present-but-blank API key crashes context startup (verified). Mitigation: the app-owned bean pattern plus context tests.
2. Scoring stalls polling, because the app has one scheduler thread, no `@EnableAsync`, and `pollFeed` is non-transactional so `AFTER_COMMIT` listeners run inline. Mitigation: the listener only enqueues, and never throws.
3. Floods from subscribe, OPML import and startup polls hit the rate limit and open the breaker. Mitigation: a recency eligibility window, newest-first ordering, and bounded concurrency.
4. The Priority list reshuffles under the cursor after votes. Mitigation: do not invalidate the Priority query on mutation; update badges in place.
5. Flat, uninformative scores from poorly worded questions. Mitigation: positive phrasing, a descriptive 5-level rubric, and a calibration spike against the live API.

## Key Findings

### Recommended Stack

No new libraries apart from the starter; everything else (Resilience4j, Flyway, `RestClient`, jsoup via Readability4J, TanStack Query) is already present. See STACK.md for the full API surface and exception tree.

**Core technologies:**
- **`org.springaicommunity:spring-ai-starter-typesafe:0.1.0`**, explicit version in `build.gradle.kts` (not in any BOM). Brings `TypeSafeClient`, typed `Noul`/`Score` questions and answers, and the typed exception hierarchy. The dependency-management plugin downgrades it to Spring 7.0.5 and Jackson 3.0.4, which works (verified). **Do not add `typesafe-spring-ai`**: it needs Spring AI 2.0.1 and is out of scope.
- **App-owned `TypeSafeClient` bean ("Pattern A")**. Built with `apiKey(Supplier)`, an explicit 5s connect timeout, `spring.ai.typesafe.timeout: 5s`, and a clone of the context `RestClient.Builder` so the User-Agent customizer still applies. The auto-config's `@ConditionalOnMissingBean` backs off, so absent, blank and real keys all start cleanly (verified). This keeps the Raindrop convention (`${MYFEEDER_TYPESAFE_API_KEY:}` + `requireConfigured()` → `JevNotConfiguredException`) intact.
- **Pinned model `jev-1.13.0`**, with `response.model()` stored per score. `jev-latest` can move and make stored scores incomparable.
- **Resilience4j as the only retry layer**: `spring.ai.typesafe.retry.max-retries: 0` and Resilience4j `@Retry` with an allow-list of transient exceptions only (429, 5xx including 529, connection/timeout).
- **Cost and limits.** About $0.042 per 1M input tokens (profile and topic text are billed on every call). Roughly $0.0001 per article. 1,200 req/min, a limit the vendor says "adjusts dynamically". About 300ms per call. There is no server-side batching: send one `systemOne` call per article carrying all its questions.

### Expected Features

**Must have (table stakes, v1):**
- Priority smart view (a route, next to All/Starred): unread sorted by score, then a "Not yet scored" separator, then unscored articles by date (T1, T2).
- **List stability while triaging.** Votes and mark-read must not reorder the list under the cursor (T8). This is a real risk: `useArticles` currently invalidates `['articles']` on every mutation.
- Score badge on a 0–100 display scale with tier colors from theme variables. Unscored shows no badge; never render "0" (T3, D7).
- An exact arithmetic "Why 82?" breakdown plus matched-topic chips (T4, T5, D1). Jev returns no prose, so the explanation is computed from stored numbers, which makes it exact and free.
- Thumbs up/down: toggleable, idempotent, exactly reversible, with a visible effect ("Nudged Rust +2") and a visible no-op ("No topics matched — add a topic?") (T6, T7, C2).
- Profile editor with writing guidance, and a topic rubric editor with signed weights, "base + learned" display and "reset learning" (T9, T10, T11).
- Not-configured / scoring-paused / cold-start / backfill-progress states, backed by a status endpoint (T12, T13, T14).
- Keyboard: `u` / `d` for thumbs (toggles), `i` for the "why" breakdown, `g p` to open Priority. `Shift+A` is disabled in Priority. `+ = -` are already taken by font size (T15).

**Should have (differentiators, v1.x):**
- Manual "Re-score unread" (D4). **Needs a user decision because it conflicts with PROJECT.md** (see Reconciliations).
- Create a topic from an article when feedback has nothing to act on (D3).
- A topic picker on thumbs-down for mixed-topic articles, like Feedly's "less like this" (D2).
- Topic test/preview against the open article (D5). Priority count above a threshold (D6). Per-topic stats (D9). "Mark below here as read" (D8).

**Defer / anti-features:** auto-hiding or auto-marking low scores read; LLM prose explanations; implicit signals (dwell time, stars); percentile badges; per-feed topic scopes; super-dislike and precedence rules; using liked articles as examples; sort-by-interest everywhere; notifications.

### Architecture Approach

Layer-first packages as today; no feature package. Ingest publishes `ArticlesIngestedEvent(feedId, newIds)` after the existing dedup loop. A listener that never throws hands the IDs to a dedicated 2-thread bounded executor. `ArticleScoringService` builds a null-safe `LinkedHashMap` state `{feed, title, summary}` (HTML stripped, about 1,500 characters) and a question map (`profile` → a 5-level `Score`, `topic_<id>` → a `Noul` with `whenTrue`/`whenFalse`). It calls `JevApiClient` (the only bean touching the SDK), classifies failures, and writes results write-once with `ON CONFLICT DO NOTHING`. A scheduled sweep only SELECTs eligible unscored IDs and enqueues them; it never calls Jev on the scheduler thread. The read path is SQL only: `InterestScoreQueries` owns one blend CTE, used for the Priority keyset page, cursor resolution and `@Transient` enrichment (`interestScore`, `feedback`) of every article response.

**Major components:**
1. **`JevApiClient` / `JevApiClientImpl`** (`integration/`). CB (outer) + Retry (inner). `isConfigured()` = key has text. No fallback method, so typed exceptions propagate for classification.
2. **`ArticleScoringService` + `interestScoringExecutor` + `InterestScoringListener`** (`service/`, `config/`). A non-blocking `submit(ids)` with an in-flight dedup set, the state/question builders, failure classification and persistence.
3. **`InterestBackfillJob`** (`scheduler/`). `@Scheduled(fixedDelay ≈ 2m)`. Skips when unconfigured, when the rubric is empty or when the breaker is OPEN. Enqueues up to the free queue capacity. This one job is the launch backfill, the outage recovery and the cold-start trigger.
4. **`InterestProfileService` / `InterestController`**. Profile (singleton row) and topic CRUD, validation (≤25 topics, profile ≤2,000 chars, warn on negated topic text), version bumps on text edits. Serves `/api/interest/status`.
5. **`InterestScoreQueries` + `PriorityService`**. The single blend CTE, `GET /api/articles/priority`, and enrichment.
6. **Schema (Flyway V6, all five tables at once):** `interest_profile`, `interest_topic`, `article_score`, `article_topic_score`, `article_feedback`, all with `ON DELETE CASCADE`.
7. **Frontend.** `/priority` route plus an `ArticleFilters.priority` flag (**not** a uiStore sentinel; this follows how `/starred` works). `MainLayout` derives the same filter object from the route so `j`/`k` walk the ranked order. Also `InterestBadge`, `ThumbsButtons`, and an `InterestSettings` dialog kept separate from the 233-line SettingsDialog.

### Critical Pitfalls

1. **A blank API key crashes startup** (verified in the starter source and by test). Use Pattern A and add `@SpringBootTest` context tests with the key absent **and** empty. Add a `checksum/secret` pod annotation so a key-only redeploy rolls the pod, and have `deploy.sh` default the variable with a warning.
2. **Scoring on the single polling thread.** The scheduler has one thread. There is no `@EnableAsync`, so `@Async` would silently run inline. `pollFeed` has no transaction, so the `AFTER_COMMIT` + `fallbackExecution` listener runs synchronously, and a listener that throws increments `feed.errorCount` and backs off a healthy feed. The listener must only enqueue, catch everything, and the executor must discard on overflow. Test: polling completes normally while the Jev stub sleeps 30s.
3. **Floods and re-scoring loops.** Subscribe, OPML import, feed edits and startup all poll immediately and can insert hundreds of back-catalogue items. Apply one eligibility predicate at enqueue and in the sweep, process newest-first, and bound concurrency. Articles with a NULL GUID are **re-inserted as new on every poll** (verified: `existsByFeedIdAndGuid` uses `guid = :guid`, and `= NULL` never matches), so each copy would be scored again. Guard against it (see Reconciliations).
4. **Lost updates if scores live on `article`.** `ArticleService.updateState` does a find → mutate → full-row `save()`, which would wipe scores written concurrently by the scorer. Keep scores in separate tables and never change the `Article` persistence shape.
5. **Unstable Priority pagination and reshuffling.** NULL scores break row-value comparison, float sums are not bit-stable, and mutation-triggered invalidation re-sorts under the cursor. Use `COALESCE(score, '-Infinity')`, `ROUND(…, 6)`, `id` as the final tiebreaker, no invalidation of the Priority query on votes or mark-read, and client-side dedup by `id`.

Also important: stacked retries (SDK 2 × Resilience4j 3 = 9 attempts), per-article 400/422 opening the breaker, `Map.of` NPE on a null summary, raw HTML in the state, negated topic wording, and `jev-latest` drift. All are covered in the phases below.

## Reconciliations: Where the Researchers Disagreed

Each item gives the resolution. **[USER DECISION]** marks items that conflict with PROJECT.md or change its scope; requirements must settle them explicitly.

### R1. Score and weight units: points on a 0–100 scale (FEATURES' units, ARCHITECTURE's formula)
The two proposals are the same model at different scales (ARCHITECTURE's ±3 × about 16.7 ≈ FEATURES' ±50). Use **points everywhere**, so the breakdown reads as plain addition and the user edits weights in the units they see:

```
p        = profile_score / profile_max_level                      ∈ [0,1]  (0 if no profile question was asked)
m_t      = GREATEST(0, (noul_t − 0.5) × 2)                          ∈ [0,1]  (hinge, see R6)
learned_t= clamp(η × Σ_feedback vote × m_t, −L, +L)                 η = 2 pts/vote, L = 20 pts
w_t      = clamp(base_t + learned_t, −50, +50), then clamped at 0 on base_t's sign side (see R2)
raw      = ROUND(P × p + Σ_{topics scored on this article that still exist} m_t × w_t, 6)   P = 100
display  = clamp(0, 100, round(raw))        -- sort uses raw; badge/breakdown use display
```
New topics default to ±20 points; the V6 `CHECK` is `weight BETWEEN -50 AND 50`. Constants live under `myfeeder.interest.blend.*`. Badge tiers: ≥70 high, 40–69 neutral, <40 muted. Store Score `confidence` but keep it out of the blend in v1; show a "low confidence" hint below 0.5. **The weight model must be fixed before V6 is written**, because the CHECK constraint and any feedback semantics depend on it.

### R2. Feedback storage: derived from `article_feedback` (ARCHITECTURE), with PITFALLS' guardrails
Store only `article_feedback(article_id PK, vote ∈ {−1,+1})`. The learned delta is **computed in the blend CTE**, never written into `interest_topic.weight`. This gives FEATURES' "exact undo" without storing per-vote deltas: deleting or flipping one row exactly removes or reverses that vote. It is idempotent against double-clicks. It never clobbers the user's own edits (`weight` is the base; learned is separate, as PITFALLS asks). It can be retuned retroactively. Add PITFALLS' constraints: cap learned at ±L, and **no feedback-driven sign flip** (a positive base clamps at ≥0 and a negative base at ≤0; a base of 0 may move either way). The toast's "Nudged Rust +2" is `η × m_t × vote` from the same formula. Votes on unscored articles are stored and start counting once the article is scored.

### R3. Scoring queue: DB is the source of truth, in-memory executor as the fast path (a hybrid)
Adopt PITFALLS' principle that **correctness never depends on the event**, using ARCHITECTURE's mechanism. "Needs scoring" is defined in SQL: the article has no `article_score` row, or has a `FAILED` row with `attempts < 3`, and meets the eligibility predicate (R-window below). Ingest writes **no** PENDING rows, which keeps the ingest path unchanged apart from publishing one event. The event feeds a 2-thread `ThreadPoolTaskExecutor` (queue about 1,000, `DiscardPolicy` + WARN log); the sweep drains anything dropped or missed. There is no need for `FOR UPDATE SKIP LOCKED`: the deployment is `replicaCount: 1` (verified), so an in-flight `Set` plus `ON CONFLICT DO NOTHING` is enough. Revisit if replicas ever exceed 1.

Statuses: `SCORED` (write-once); `FAILED` (400/422/missing-answer/answer-type; `attempts`, `last_error`, retried by the sweep up to 3 times); `SKIPPED` (terminal, e.g. title and body both empty, so the sweep stops re-selecting it). Transient failures (429/5xx/timeout/connection), `CallNotPermittedException` and `JevNotConfiguredException` write **no row and do not count as an attempt**. 401/403 are recorded by the breaker, so it opens and the sweep pauses. The half-open probe every 60s serves as PITFALLS' "global pause with periodic probe".

### R4. Priority cursor: keep the `Long` id cursor (ARCHITECTURE), fix drift in the frontend
Keep `PaginatedResponse` unchanged (`nextCursor: Long`). `PriorityService` recomputes the cursor article's `(score, date)` with the same CTE; that lookup is **not** unread-scoped, because the cursor article may have just been read. An opaque cursor that carries the score (PITFALLS) would not actually fix weights changing mid-scroll: the *other* rows' scores move too. Only snapshotting the weights would, and that is over-engineering for one user. So drift is handled by policy:
- **The Priority infinite query is excluded from the `['articles']` invalidation** on thumbs, mark-read and star. Update badge and feedback in place with `setQueryData`, and show a "Ranking changed — refresh" affordance.
- Re-rank from page 1 only on explicit refresh, re-entering the view, or window refocus.
- Dedupe by `id` across pages on the client as a safety net. If the cursor article is gone (404), restart from page 1.
- The keyset is a single tuple `(COALESCE(score,'-Infinity'), COALESCE(published_at, fetched_at), id) < (...)`, all DESC, which also crosses cleanly into the unscored segment.

### R5. Topics added after scoring: contribute 0, no Σ|w| normalization (ARCHITECTURE)
Both researchers agree a later-added topic must not change old articles' scores; `LEFT JOIN` + `COALESCE(…,0)` already gives that. Reject PITFALLS' normalization by the Σ|w| of present topics: it weakens a strong match whenever an unrelated topic is added, and it breaks the "plain addition" explanation. **Accept the residual cohort bias** (after adding a positive topic, new articles can outrank older ones) as a known consequence of PROJECT.md's new-articles-only decision. It is bounded by the eligibility window (the old cohort ages out in about 14 days), explained in the breakdown ("scored before topic X existed", from `topic.created_at > score.scored_at`), and fully fixed by R-C1 if approved.

### R6. Topic contribution: hinge `max(0, (noul − 0.5) × 2)`, not raw noul
Noul 0.5 means "undecided". With raw nouls, ten irrelevant topics at about 0.2 each and a weight of 20 add about 40 points of noise, as much as a strong profile match. The hinge is still continuous (it is zero at 0.5 and rises linearly), which answers FEATURES' "no cliff" objection. The set of topics with `m_t > 0` is exactly the set FEATURES wanted in the breakdown (match ≥ 0.5), so the sort, the badge and the "why" all use one formula. Negative weights penalize only real matches. The same hinge gates the feedback nudge (R2), so uninformative mid-range nouls never move weights.

### Other items to surface

- **C1: manual "Re-score unread" [USER DECISION, conflicts with PROJECT.md Out of Scope].** FEATURES and PITFALLS both recommend it. At about $0.0001 per article, re-scoring 1,000 unread costs about $0.10, and it is the only fix for stale matches after a profile or topic-description edit and for the R5 cohort bias. **Recommendation: approve for v1**, user-triggered only, unread and in-window only, with a count estimate shown. Implementation: delete the in-scope `article_score` rows (topic rows cascade, or delete them explicitly) and let the sweep drain them. No new machinery. If declined, the editor must say "applies to newly arriving articles".
- **C3: cold start.** Treat "profile empty **and** no topics" as not configured: no calls, no rows, and a CTA in the Priority view. (Consensus: profile text or at least one topic is enough to score.) Because the sweep is gated on this, the PROJECT.md "one-time backfill" effectively starts at first profile save. That satisfies both ARCHITECTURE (automatic) and PITFALLS (don't score the backlog against nothing). The backfill is **not** a one-time flag: it is the continuous sweep, which also covers a key added late and long outages.
- **Recency eligibility window [USER DECISION on size].** Apply one predicate at enqueue and in the sweep: `read = false AND COALESCE(published_at, fetched_at) > now − 14d` (configurable), newest first. It must key on `published_at`: in a subscribe or OPML flood every back-catalogue item has `fetched_at = now`, so ARCHITECTURE's `fetched_at`-based 30-day filter would not stop floods. Consequence: unread items older than the window are never scored and stay in the "unscored" segment by date. That partly narrows PROJECT.md's "scores the existing unread backlog", so confirm the window size (14 vs 30 days). Recheck `read = false` at dispatch time too.
- **NULL-GUID re-insert [USER DECISION on the root fix].** Verified in code. It is rare in practice (items with neither guid nor link, or JSON Feed items missing the required `id`), but when it happens every poll re-inserts, and would re-score, the same items. **In scope (recommended):** the scorer skips `guid IS NULL` articles (mark `SKIPPED`, log). **Root fix:** a fallback GUID in `FeedParser` (a hash of link and title). That is CONCERNS-adjacent code, which PROJECT.md scopes out; recommend it as a separate quick task.
- **List stability in Priority** is a v1 requirement, not polish, and must ship in the same phase as the Priority view and thumbs (see R4). `ArticleList`'s existing "preserve selected article" logic helps.
- **Blank-key crash and Helm.** With Pattern A (STACK, verified), the Raindrop-style `${MYFEEDER_TYPESAFE_API_KEY:}` and an always-rendered `secretKeyRef` are safe. That supersedes ARCHITECTURE and PITFALLS' "never declare the property; render the env var conditionally" (which is only needed with the starter's own bean). Keep a context test that fails if someone deletes the app-owned bean. Add `checksum/secret`. `deploy.sh` uses `${MYFEEDER_TYPESAFE_API_KEY:-}` with a warning.
- **Single retry layer.** Resilience4j owns retries (the convention, one place to tune, the breaker sees each attempt), and SDK `max-retries: 0`. PITFALLS' opposite choice (keep the SDK retry for `retry-after-ms`) is the documented fallback if 429s actually appear during backfill. Breaker `ignore-exceptions` is the union of all three proposals: `JevNotConfiguredException`, `TypeSafeBadRequestException`, `TypeSafeUnprocessableEntityException`, `TypeSafeMissingAnswerException`, `TypeSafeAnswerTypeException`. Suggested settings: COUNT_BASED window 20, minimum 10 calls, 60s open, slow-call threshold about 3s. Mirror the `jev` instances into `src/test/resources/application.yaml`, which shadows main.
- **`@EnableAsync` is absent: don't add it.** Use an explicit named `ThreadPoolTaskExecutor` bean and `executor.execute(...)`, so rejection is visible and a `SyncTaskExecutor` can be swapped in for tests. STACK's "needs `@EnableAsync`" is moot. **Do not** set `spring.threads.virtual.enabled`: it swaps the `TaskScheduler` under `FeedPollingScheduler`. Leave `spring.task.scheduling.pool.size` unchanged in this milestone: the sweep only runs a SELECT and enqueues, and raising the pool size would make feed polls concurrent, an unrelated behavior change.
- **Pin `jev-1.13.0`** (unanimous). Store `model`, `request_id`, `profile_version` and `topic_version` on each row.
- **Jev input when the summary is blank [minor USER DECISION].** Many Atom feeds have content but no summary. Recommend falling back to stripped and truncated `content` (≤1,500 characters). PROJECT.md excludes "full article content" as input on cost grounds; a truncated fallback costs about the same as a summary.

## Implications for Roadmap

Suggested structure: 5 phases plus a short rollout phase. Phases 3 and 4 can run in parallel after Phase 2. (With the user-added dependency-upgrade phase first, these become Phases 2–7.)

### Phase 1: Jev Client Foundation (optional integration)
**Rationale:** It retires the only external unknowns first: the key/startup behavior, the resolved classpath and the exception mapping. Everything downstream is plain Spring.
**Delivers:** Gradle dependency (explicit `0.1.0`); `TypeSafeConfig` (Pattern A); `JevApiClient`/`Impl` with CB + Retry and no fallback; `JevNotConfiguredException`; `application.yaml` (`api-key: ${MYFEEDER_TYPESAFE_API_KEY:}`, `model: jev-1.13.0`, `timeout: 5s`, `retry.max-retries: 0`, Resilience4j `jev` instances in main **and** test YAML); Helm secret + env + `checksum/secret`; `deploy.sh` variable; `/api/interest/status` (configured, breaker state).
**Tests:** context starts with the key absent and with it blank; `MockRestServiceServer` contract tests (object state, question keys, pinned model; 400/401/403/422/429/500/529 → typed exceptions); breaker opens on 5xx and ignores 422 through the real AOP proxy; resolved-classpath check; live smoke test gated by `@EnabledIfEnvironmentVariable`.
**Avoids:** Pitfalls 1, 5, 15, 16, 17.

### Phase 2: Interest Model, Schema and Rubric Editor
**Rationale:** Questions can't be built without a profile and topics. Shipping the whole V6 schema now means later phases add no migrations. It needs the R1 weight model locked.
**Delivers:** `V6__interest_scoring.sql` (all five tables, weight CHECK −50..50, cascades); `InterestProfile`/`InterestTopic` entities, repos and service (≤25 topics, profile ≤2,000 characters, warn on negated topic text, version bumps on text edits only); `InterestController` CRUD; the **question builder** as a pure function (`profile` → 5-level descriptive `Score` with the profile in the instructions; `topic_<id>` → a positively phrased "primarily about" `Noul` with `whenTrue`/`whenFalse`; answers read by key); frontend `InterestSettings` dialog with writing guidance, "applies to newly arriving articles" (or the C1 button), base/learned display placeholders, and the not-configured notice driven by `/status`.
**Addresses:** T9, T10, T11, T12 (settings side), T13 (gate definition).
**Avoids:** Pitfalls 7, 8 (IDs and versions), 12, 14, 23.
**Research flag:** a **calibration spike** at the end: run the question builder against 10–20 real articles with the live key. Check the spread and confidence of profile scores and whether nouls cluster in 0.35–0.65. Iterate the wording before the Priority view depends on it.

### Phase 3: Scoring Pipeline and Backfill Sweep
**Rationale:** This is the core write path. ARCHITECTURE's separate "Backfill" phase is merged in because the sweep *is* the correctness mechanism (R3), not an add-on.
**Delivers:** `ArticlesIngestedEvent` published from `FeedPollingService` (IDs of new articles only); `InterestScoringListener` (never throws); the `interestScoringExecutor` bean; `ArticleScoringService` (in-flight dedup, eligibility and rubric-empty gates, the **state builder** as a pure TDD'd function: `LinkedHashMap`, jsoup strip, truncation, content fallback, never a scalar; failure classification per R3; NULL-GUID skip); `ArticleScoreWriter` (one transaction, `ON CONFLICT DO NOTHING`); `InterestBackfillJob` (gated on configured, non-empty rubric and breaker not OPEN; capacity-aware; newest-first; windowed); `/status` gains eligible-unscored and failed counts. If C1 is approved, add `POST /api/interest/rescore` here.
**Addresses:** automatic scoring of new articles, graceful degradation, the launch backfill, cold-start first scoring, T14 data.
**Avoids:** Pitfalls 2, 3, 4, 6, 9, 18, 19.
**Tests:** polling duration unchanged with the Jev stub sleeping 30s; a listener exception never increments `feed.errorCount`; a GUID-less fixture polled 3 times produces 0 extra calls; an OPML flood with the stub drains at a bounded pace; `SyncTaskExecutor` swapped in; a mocked `JevApiClient`.

### Phase 4: Blend and Priority View
**Rationale:** It needs only the V6 schema, so it can be built against seeded `article_score` rows **in parallel with Phase 3**. It carries the highest UX risk (cursor and list stability), so give it a full phase.
**Delivers:** `InterestScoreQueries` (the single CTE per R1/R2/R6, with the `learned` CTE present and returning 0 until feedback exists); `PriorityService` plus `GET /api/articles/priority` (keyset per R4); `@Transient interestScore`/`feedback` enrichment on every article response; frontend `/priority` route, a FeedPanel entry (`useMatch` active state, excluded from All's active state), `MainLayout` filter alignment so `j`/`k` walk the ranked order, `InterestBadge` (tiers, no badge when unscored), the "Why N?" breakdown and topic chips, the "Not yet scored" separator, the cold-start CTA, "Scoring paused / N waiting" and progress lines, `g p` / `i` shortcuts, `Shift+A` disabled, the **no-invalidate policy** for the Priority query, and client-side dedup.
**Addresses:** T1–T5, T8, T12–T15, D1, D7.
**Avoids:** Pitfalls 10, 13, 20, 21.
**Tests:** keyset ties; crossing the scored→unscored boundary; the cursor article being read between pages; adding a topic leaves old articles' scores unchanged; the breakdown sum equals the sort value.

### Phase 5: Thumbs Feedback
**Rationale:** The smallest phase. It needs the blend to show its effect and the invalidation policy from Phase 4.
**Delivers:** `ArticleFeedbackRepository` (upsert/delete); `PUT`/`DELETE /api/articles/{id}/feedback` returning the re-enriched article; the learned CTE active with the cap and sign clamp; `ThumbsButtons` in the reading-pane toolbar (optionally on list-row hover); `u`/`d` toggles; the effect toast and the no-op message; base + learned display and "reset learning" per topic in settings.
**Addresses:** T6, T7, C2 message (D3 and D2 are v1.x).
**Avoids:** Pitfall 11.
**Tests:** up/down/up equals a single up; the effective weight never crosses the base's sign; the list order is unchanged after a vote until refresh.

### Phase 6: Rollout and Calibration (short; can fold into Phase 5's close-out)
**Rationale:** The launch backfill is the largest flood, and the blend constants are MEDIUM-confidence until they meet real data.
**Delivers:** A release deploying with the key; observation of backfill pacing, 429s and breaker state; tuning of `η`, `L`, `P` and the badge tiers against the real score distribution (optionally with a hidden `/api/interest/debug/{articleId}` breakdown); a check that a key-only redeploy rolls the pod; CLAUDE.md updates (new key behaviors and gotchas).

### Phase Ordering Rationale

- **The dependency chain is real.** The client (1) → the questions and schema (2) → scoring (3). The Priority view (4) depends only on the schema and can use seeded rows, so it runs alongside 3. Feedback (5) needs the blend in 4.
- **Put the riskiest unknowns first.** Phase 1 settles every external-library question. The Phase 2 calibration spike settles "are the scores any good" before the UI is built on them.
- **The weight model (R1) is settled before V6** because the CHECK constraint and feedback semantics depend on it. List stability (R4) ships with the Priority view, not after it.
- **One formula, one place.** The blend CTE is written once in Phase 4, and Phase 5 only switches on the `learned` input.
- **One drain loop.** Launch backfill, outage recovery, cold start and the optional re-score are all the Phase 3 sweep; none gets bespoke code.

### Research Flags

Phases likely to need `/gsd-plan-phase --research-phase <N>`:
- **Phase 2:** the question and rubric wording calibration against the live API (Jev reads literally, and wording moves confidence from about 0.82 to 0.60). Needs real articles and a real key.
- **Phase 4:** the blend constants and badge tiers can only be tuned on data, and the keyset plus TanStack infinite-query invalidation interplay deserves a focused look at `useArticles.ts` and `MainLayout`.

Phases with standard patterns (skip research):
- **Phase 1:** already verified by STACK's scratch project (Boot 4.0.3 + starter 0.1.0, all key states). It needs only a live-key smoke test, not research.
- **Phase 3:** event plus executor plus sweep are fully specified, and the codebase already has the event-listener pattern.
- **Phase 5:** small; formula and table design are settled in R2.
- **Phase 6:** operational.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Source jars and POMs read; a scratch Gradle project on the repo's exact plugin/BOM set was compiled and tested (all key states, wire round-trip, 429 mapping). Pricing and rate limits are MEDIUM (vendor says they change dynamically). |
| Features | MEDIUM | Competitor behavior comes from vendor docs and blogs (primary but seam-tagged LOW). Table-stakes calls are reasoned product judgment. Keyboard and cache-invalidation facts were read from source (HIGH). |
| Architecture | HIGH / MEDIUM | Integration points (single scheduler thread, non-transactional `pollFeed`, full-row `save()`, route-driven smart views, `MainLayout` query) were read from source: HIGH. Blend constants: MEDIUM, need tuning. |
| Pitfalls | MEDIUM-HIGH | The highest-impact items are verified in code or source (blank key, NULL GUID, lost update, inline listener, stacked retries). Ranking-dynamics items (runaway, filter bubble, drift) are reasoned. |

**Overall confidence:** HIGH that the design is buildable and safe for polling; MEDIUM that the rankings will be useful without a tuning pass.

### Gaps to Address

- **Score quality and question wording:** unknown until the Phase 2 calibration spike runs with a real key.
- **Blend constants** (`P=100`, default ±20, `η=2`, `L=20`, tiers 70/40): starting values only. Tune in Phase 6. **Topic-only users** (empty profile) get `p = 0`, so their scores cluster at 0–40 and the badge tiers may look uniformly "muted". Consider a neutral baseline for them during tuning.
- **A 400 caused by the rubric, not the article:** a malformed question (bad topic text) fails *every* article with a 400. Because 400 is ignored by the breaker, articles would burn through their 3 attempts. Mitigate by validating questions on rubric save and surfacing a spike in failures on `/status`. `attempts < 3` plus a manual "retry failed" (or the C1 re-score) is the recovery path.
- **User decisions pending:** C1 manual re-score (recommended yes); eligibility window size (recommended 14 days); NULL-GUID root fix in the parser (recommended as a separate quick task; the scorer guard is in scope); content fallback when the summary is blank (recommended yes).
- **jsoup on the classpath** via Readability4J is assumed. Verify, or add it explicitly, in Phase 3.
- **429 behavior during the launch backfill** is unobserved. If it happens, lower concurrency to 1, or switch to the SDK retry layer to honor `retry-after-ms` (keep exactly one retry layer).
- **Non-English feeds** have lower Jev accuracy. Surface low confidence; a per-feed scoring opt-out is out of scope unless the user asks for it.

## Sources

### Primary (HIGH confidence)
- myfeeder source at HEAD: `FeedPollingService`, `FeedPollingScheduler`, `FeedParser`, `ArticleRepository` (`guid = :guid`, re-verified during synthesis), `ArticleService`, `ArticleController`, `PaginatedResponse`, `RaindropApiClientImpl`, `MyfeederApplication`, main and test `application.yaml`, Helm templates (`replicaCount: 1`, re-verified), `deploy.sh`, V1–V5 migrations, frontend `App.tsx`, `uiStore`, `useArticles`, `useKeyboardShortcuts`, `FeedPanel`, `ArticleList`, `MainLayout`.
- Maven Central `spring-ai-starter-typesafe` / `typesafe-java-sdk` / `typesafe-spring-ai` / `typesafe-bom` 0.1.0 POMs and source jars (`TypeSafeAutoConfiguration` blank-key `Assert.state`, `TypeSafeProperties`, `TypeSafeClient`, `RetryPolicy`, answers, exceptions); the spring-ai-typesafe v0.1.0 git tag.
- STACK's scratch Gradle project (Boot 4.0.3, dependency-management 1.1.7, Spring AI BOM 2.0.0-M2, JDK 25): absent, blank, `false` and real key; app-owned bean; stub-server wire and 429 tests.
- Spring Boot 4.0.3 reference, task execution and scheduling (single-thread default scheduler; the virtual-threads scheduler swap).

### Secondary (MEDIUM confidence)
- Spring blog, "Spring AI TypeSafe: structured judgment" (2026-09-21): no prose output, Score/Noul shapes, confidence, wording guidance, latency.
- spring-ai-typesafe reference docs (ErrorsAndRetries, SpringBootStarter, Batches, JevCompositeScore, JevConsistency); docs.typesafe.ai (models/pricing/limits, Score and Noul primitives, jev-1.13 jaggedness: literal reading, negation, adversarial state).
- Feedly, NewsBlur, TT-RSS and Gmail official docs and blogs (competitor ranking, explainability and feedback patterns).

### Tertiary (LOW confidence)
- Pricing and limits cross-checks (marktechpost, opentweet.io); OSS rankers' READMEs (feeds.fun, PersonalRSS, betternews, rosso); Inoreader 2019 blog; keyset pagination stability article.

---
*Research completed: 2026-09-22*
*Ready for roadmap: yes (after the user decisions flagged under Reconciliations: C1 re-score, eligibility window size, NULL-GUID parser fix, content fallback)*
