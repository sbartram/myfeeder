# Phase 4: Scoring Pipeline & Backfill Sweep - Context

**Gathered:** 2026-09-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Every eligible article (new arrivals and the existing unread backlog) is judged once by Jev in the background. Raw outputs are stored write-once in `article_score` / `article_topic_score`, and feed polling is never slowed or failed by scoring. The phase also delivers:

- the manual "Re-score unread" action (INT-05)
- the eligible-unscored and failed counts on `GET /api/interest/status` (JEV-05)
- the scorer-path review fixes listed below

Requirements: SCOR-01..08, INT-05, JEV-05.

Not in this phase: the blend CTE, the Priority view, badges, the "Why N?" breakdown and Priority empty/paused states (Phase 5), thumbs feedback (Phase 6), and tuning the blend constants against real data (Phase 7).

</domain>

<decisions>
## Implementation Decisions

### Carried forward (locked by research and prior phases, not re-discussed)
- **Pipeline shape (research R3):** `FeedPollingService` publishes an event with the IDs of genuinely new articles only. A listener that never throws hands them to a dedicated, named `ThreadPoolTaskExecutor`: bounded queue of about 1,000, discard-on-overflow plus a WARN log, no `@EnableAsync`, and a swappable `SyncTaskExecutor` in tests. The DB is the source of truth. "Needs scoring" means no `article_score` row, or a FAILED row with `attempts < 3`, AND the eligibility predicate holds. Ingest writes no PENDING rows. An in-flight `Set` plus `ON CONFLICT DO NOTHING` is enough, since `replicaCount: 1`.
- **Sweep = the only drain loop:** a `@Scheduled` job SELECTs eligible unscored IDs and enqueues them. It never calls Jev on the scheduler thread. This one job is the launch backfill, outage recovery, the late-added-key path, first scoring after the profile is written, and the Re-score drain. Don't change `spring.task.scheduling.pool.size` and don't enable virtual threads.
- **Eligibility (SCOR-04):** `read = false AND COALESCE(published_at, fetched_at) > now − 14d` (window configurable), newest first. The same predicate applies at enqueue, in the sweep, and again at dispatch (`read` is rechecked).
- **Gates:** `JevApiClient.isConfigured()`, `InterestService.isColdStart()` (the single predicate, never reimplemented, SCOR-06), and breaker not OPEN. Never judge an empty question map. Skip when `ArticleStateBuilder.hasJudgeableText` is false.
- **Statuses (R3):** SCORED is write-once. FAILED covers 400/422/missing-answer/answer-type, increments `attempts`, and the sweep retries it while `attempts < 3`. SKIPPED is terminal: GUID-less articles (SCOR-08) and articles with no judgeable text. Transient failures write **no row and use no attempt**: 429, 5xx, timeout/connection, `CallNotPermittedException`, `JevNotConfiguredException`. 401/403 are recorded by the breaker, so it opens and the sweep pauses.
- **Write order:** `article_score` is inserted before `article_topic_score` in one transaction. The row stores profile score, max level, confidence, profile version, model, request id, and per-topic noul + topic version.
- **Builders:** reuse `InterestQuestions` / `ArticleStateBuilder`, the pure static builders shared with the preview. Question keys are `profile` / `topic_<id>`, and answers are read by key. Topic wording stays v2 ("substantially about").
- **`InterestStatus`:** the three existing components `{configured, breakerState, coldStart}` are never renamed. JEV-05 counts are appended as new components.

### Re-score unread (INT-05)
- **D-01:** The action sits in the **Interests dialog footer**, replacing or extending the note "Profile and topic changes apply to newly arriving articles." (`InterestsDialog.tsx:93`). No Settings-dialog entry. The Phase 5 Priority view may add another entry point later.
- **D-02:** The confirmation is **inline and themed** (not `window.confirm`). It shows a server-computed count with plain wording, e.g. *"Re-judge 312 unread articles from the last 14 days? Existing scores are replaced as they're re-scored."* The count comes from a server GET that uses **the same predicate as the delete**, so what's shown is exactly what gets reset. No time estimate.
- **D-03:** **Scope:** in-window unread articles with **SCORED or FAILED** rows (including exhausted FAILED). SKIPPED rows are not touched. Re-score therefore doubles as the recovery path for a rubric-caused 400 that burned every article's attempts. Articles with no row are already queued for the sweep. — **Reversibility:** reversible — a predicate in one service method.
- **D-04:** The Re-score button is **disabled with a reason** ("Save your changes first") while the dialog has unsaved profile/topic edits, using the existing `describeUnsaved(...)` dirty state.
- After confirmation the rows are deleted and the sweep drains them. Progress shows as the eligible-unscored count from `/status` (a simple "N waiting to be scored" line is enough in this phase).

### Jev timeout and breaker
- **D-05:** Raise the **shared** `spring.ai.typesafe.timeout` from 5s to **about 30s**. It stays one `TypeSafeClient` and one setting for both the preview and the scorer, with no separate scorer client. Calibration measured about 2.6s average and more than 5s cold for profile + 7 topics, and the rubric can grow to 25 topics. Mirror the change in test config where relevant.
- **D-06:** Raise the `jev` breaker `slow-call-duration-threshold` from 3s to **about 15s** (it must stay below the timeout). Slow-call detection remains a "Jev is degraded" signal but no longer trips on normal multi-topic calls.
- **D-07:** **Breaker outer (fold in 02-REVIEW WR-03):** set the Resilience4j aspect orders so the circuit breaker wraps the retry and records **one outcome per logical `judge()` call**, not one per attempt. The D-09 thresholds from Phase 2 then count articles, as originally meant. Update `JevResilienceTest` (it currently asserts 3 calls → 9 recorded failures) and make the CLAUDE.md Resilience4j convention describe what actually runs. Check the Raindrop instance for side effects, because the aspect order is global. — **Reversibility:** costly — the aspect order is global to every `@CircuitBreaker`/`@Retry` bean (Jev and Raindrop), and both the tests and the CLAUDE.md convention encode it.
- **D-08:** An auth failure (401/403 → OPEN breaker) gets **no special surfacing** in Phase 4. `breakerState` on `/status` is enough, and the Phase 5 "scoring paused" state covers the UI. No `lastError` field.

### Sweep pacing and failure counts
- **D-09:** Executor concurrency is **1 thread**, with a configurable property (e.g. under `myfeeder.interest.*`). 429 behavior during backfill hasn't been observed yet, and one thread at about 2.6s per call is roughly 1,400 articles/hour, plenty for one user. Phase 7 may raise it after watching the launch backfill. This supersedes the research default of 2 threads.
- **D-10:** The sweep runs on a **2-minute fixed delay** and enqueues **up to the executor's free queue capacity**, newest first. It skips when unconfigured, in cold start, or when the breaker is OPEN. There is no immediate kick on profile save or Re-score: pickup within 2 minutes is acceptable.
- **D-11:** The **`failed` count** on `/status` covers **exhausted articles only** (FAILED with `attempts ≥ 3`). FAILED rows still being retried count as **eligible-unscored**. — **Reversibility:** costly — the Phase 5 Priority states read these field semantics.
- **D-12:** **Both counts use the eligibility predicate** (unread, inside the window), so they always match what the sweep and Re-score will act on. Read and aged-out articles drop out of both counts.

### Scorer-path review fixes (folded in)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements and roadmap
- `.planning/ROADMAP.md` §"Phase 4: Scoring Pipeline & Backfill Sweep": goal and 5 success criteria
- `.planning/REQUIREMENTS.md`: SCOR-01..08, INT-05, JEV-05, plus the scoring-model header (R1/R2/R6)
- `.planning/PROJECT.md`: Key Decisions table (eligibility window, NULL-GUID skip, content fallback, single retry layer)

### Research (settled design)
- `.planning/research/SUMMARY.md` §R3 (scoring queue, statuses), §Reconciliations C1/C3/recency window/NULL-GUID, §"Phase 3: Scoring Pipeline and Backfill Sweep" (component list and tests; research numbering, not roadmap numbering)
- `.planning/research/ARCHITECTURE.md`: event → executor → scorer → writer flow, sweep job
- `.planning/research/PITFALLS.md`: single polling thread, listener exceptions → `feed.errorCount`, floods, NULL-GUID re-insert, stacked retries

### Prior phase artifacts
- `.planning/phases/03-interest-model-schema-rubric-editor/03-CONTEXT.md`: D-04/D-05 status contract, D-12 shared builders
- `.planning/phases/03-interest-model-schema-rubric-editor/03-CALIBRATION.md`: v2 wording, latency (about 2.6s avg, >5s cold), timeout note
- `.planning/phases/03-interest-model-schema-rubric-editor/03-REVIEW.md` §CR-01: the truncate bug (D-13)
- `.planning/phases/03-interest-model-schema-rubric-editor/deferred-items.md`: CR-01 and scoring-timeout entries
- `.planning/phases/02-jev-client-foundation/02-REVIEW.md` §WR-01, §WR-02, §WR-03: folded fixes (D-07, D-14, D-15)
- `.planning/phases/02-jev-client-foundation/02-CONTEXT.md`: Jev client D-01..D-09 (breaker thresholds, typed exceptions)

### Schema
- `src/main/resources/db/migration/V6__interest_scoring.sql`: `article_score` / `article_topic_score` (no new migration in this phase)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `service/InterestQuestions`, `service/ArticleStateBuilder`: pure static question/state builders shared with the preview. The scorer must use them unchanged, apart from the CR-01 fix.
- `integration/JevApiClient` / `JevApiClientImpl` / `JevJudgment`: `judge(state, questions)` behind `@CircuitBreaker` + `@Retry("jev")`, plus `isConfigured()` (no breaker).
- `service/InterestService.isColdStart()`: the single cold-start gate.
- `service/InterestStatusService` / `InterestStatus`: extend with the counts, and read breaker state from `CircuitBreakerRegistry.circuitBreaker("jev")`.
- `repository/InterestProfileRepository`, `InterestTopicRepository`: profile/topic versions for provenance.
- Frontend `src/api/interest.ts`, `src/hooks/useInterest.ts`, `InterestsDialog.tsx` (`describeUnsaved`, footer note at line 93): where the Re-score UI goes.

### Established Patterns
- Event-driven after-commit listeners (`FeedSavedEvent` → `FeedPollingScheduler` via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`). `pollFeed` has **no transaction**, so such a listener runs synchronously on the single polling thread. The scoring listener must only enqueue and must catch everything.
- `FeedPollingService.pollFeed` dedups with `existsByFeedIdAndGuid` then `save` (lines 47-50). New IDs are collected there, and a NULL guid never matches `= NULL`, which is why the SCOR-08 skip exists.
- Spring Data JDBC with `@Query` for custom SQL. Use `ON CONFLICT DO NOTHING` inserts through `@Modifying @Query` or `JdbcTemplate`.
- `GlobalExceptionHandler` fixed-text Jev ProblemDetails (Phase 3 03-04) apply to any new interest endpoints.
- `@WebMvcTest` controller slices, `@DataJdbcTest` + Testcontainers for repository SQL, Mockito unit tests.

### Integration Points
- `FeedPollingService.pollFeed`: publish the new-IDs event after the dedup loop.
- `application.yaml` (main **and** `src/test/resources/application.yaml`, which shadows main): `spring.ai.typesafe.timeout`, the `resilience4j.circuitbreaker.instances.jev` slow-call threshold, aspect orders, `ignore-exceptions`, and the new `myfeeder.interest.*` properties.
- `MyfeederApplication` already has `@EnableScheduling`, so the sweep uses `@Scheduled(fixedDelay…)` on the single-thread scheduler.
- `CLAUDE.md`: the Resilience4j convention text (D-07). OPS-03 documentation of the scoring executor is Phase 7, but D-07's convention correction ships here.

</code_context>

<specifics>
## Specific Ideas

- Re-score confirm copy: *"Re-judge 312 unread articles from the last 14 days? Existing scores are replaced as they're re-scored."*
- Disabled Re-score tooltip: *"Save your changes first"*.
- Throughput reasoning for 1 thread: about 2.6s/call ≈ 1,400 articles/hour. The launch backfill of a 14-day unread window finishes well within an hour or two.

</specifics>

<deferred>
## Deferred Ideas

- Immediate sweep kick on first profile save / Re-score confirm: not needed; the 2-minute sweep is acceptable.
- `lastError` / auth-failure category on `/status` ("check your API key"): Phase 5/7 if the paused state proves confusing.
- Separate scorer vs preview timeouts: rejected for now in favor of one shared 30s setting.
- 03-REVIEW WR-01..WR-05 (Interests dialog save race, discard-while-pending, shortcuts behind modal, endless "loading article…", wrong error copy): still open. Suggest `/gsd-code-review 03 --fix` or a `/gsd-quick` pass.
- 02-REVIEW WR-04 (Raindrop `createBookmark` POST retried on read timeout/5xx) and WR-05 (`helm --set` mangles secrets with `,` or `\`): separate `/gsd-quick` tasks.
- Interests dialog help text still says "primarily about" (03 deferred-items): trivial copy fix. The planner may fold it in, since the Re-score work edits the same dialog.

### Reviewed Todos (not folded)
- **Tune Raindrop resilience and fix CLAUDE.md AspectJ note** (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): Raindrop tuning is outside scoring scope. The CLAUDE.md aspect-order wording overlaps D-07. If D-07 corrects that text, update the todo so it covers only the Raindrop tuning.

</deferred>

---

*Phase: 04-scoring-pipeline-backfill-sweep*
*Context gathered: 2026-09-23*
