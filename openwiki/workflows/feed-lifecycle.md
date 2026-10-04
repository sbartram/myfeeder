---
type: Workflow
title: Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention)
description: Walks through how a feed is subscribed, scheduled, polled on a recurring basis with error backoff, kept in sync via application events on save/delete, and eventually has old article content cleared by the retention job. Key file for anyone changing polling, scheduling, or article ingestion behavior.
resource: src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java
tags: [workflow, scheduling, polling, events]
verified:
  - by: openwiki/0.7.0
    at: 2026-10-04T13:52:44.431Z
sources:
  - id: openwiki-source-a71602e15d81cc6dfe06d6ec
    resource: repo://src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - id: openwiki-source-cdc24fc3ca0c47ee6972a535
    resource: repo://src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - id: openwiki-source-c3ff2e449c60f14eac2534e2
    resource: repo://src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java
  - id: openwiki-source-467204ca8042feb6892811eb
    resource: repo://src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java
  - id: openwiki-source-f645bebddc8435267a067d4f
    resource: repo://src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-92b6e647240c8425fe97dcaa
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedFetcher.java
  - id: openwiki-source-74146f2d58936b037f62e557
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedPollingService.java
  - id: openwiki-source-dfb755641f905ce59b84925b
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedService.java
  - id: openwiki-source-de3e8d1e24d1b39e66e69043
    resource: repo://src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java
  - id: openwiki-source-dc52751c9213b210ffa4f78c
    resource: repo://src/main/java/org/bartram/myfeeder/service/OpmlImportService.java
  - id: openwiki-source-e1ca7236dc9fb7545718d969
    resource: repo://src/main/java/org/bartram/myfeeder/service/RetentionService.java
generated: { by: "openwiki/0.7.0", at: "2026-10-04T13:52:44.431Z" }
---

# Feed Lifecycle Workflow

This is the core business process of myfeeder: get new articles from subscribed feeds into the database reliably, without hammering slow or broken feeds.

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
flowchart TD
    Sub["FeedService.subscribe"] -->|"publishes"| Saved["FeedSavedEvent"]
    Upd["FeedService.update"] -->|"publishes"| Saved
    Opml["OpmlImportService.importOpml (new feed only)"] -->|"publishes"| Saved
    Saved -->|"AFTER_COMMIT"| Register["FeedPollingScheduler.registerFeed"]
    Register --> Task["TaskScheduler.scheduleAtFixedRate(pollAndAdjust)"]
    Task --> Poll["FeedPollingService.pollFeed"]
    Poll -->|"200, new articles"| Ingested["ArticlesIngestedEvent"]
    Poll -->|"exception"| Err["errorCount++, lastError set"]
    Poll -->|"304 / no new articles"| Task
    Err --> Adjust["pollAndAdjust recomputes effective interval"]
    Adjust -->|"interval changed"| Task
    Ingested --> Listener["InterestScoringListener -> ScoringQueue"]
    Del["FeedService.delete"] -->|"publishes"| Deleted["FeedDeletedEvent"]
    Deleted -->|"AFTER_COMMIT"| Cancel["FeedPollingScheduler.cancelFeed"]
    Poll -->|"NotFoundException (feed deleted mid-flight)"| Cancel
    Cron["RetentionService.cleanupOldContent (cron)"] -->|"clears content/summary/extracted_content"| DB[("article table")]
```
*Subscribe/update/delete publish events that the scheduler reacts to; each poll either feeds the scoring pipeline or adjusts its own backoff; retention runs independently on a cron.*

## 1. Subscribe

`FeedService.subscribe(feedUrl, folderId)`:
1. Calls `FeedFetcher.fetch(url)` — an **unconditional** GET (see [Architecture Overview](../architecture/overview.md) on `FeedFetcher` being the single fetch path, including its SSRF guard via `FeedUrlValidator` and its response-size cap).
2. Parses the response with `FeedParser` (ROME for RSS/Atom, Jackson for JSON Feed) into a `ParsedFeed`. `FeedParser.parse` throws `FeedParseException` (→422) if the result has no title and no articles — this guards against "200 OK with garbage" responses that would otherwise violate the `feed.title NOT NULL` constraint.
3. Saves a new `Feed` row with `pollIntervalMinutes` from `myfeeder.polling.default-interval-minutes` (default 15).
4. Publishes `FeedSavedEvent(saved)` via `ApplicationEventPublisher` — this is what actually schedules polling (see step 2).

`FeedService.update(id, updates)` follows the same "save then publish `FeedSavedEvent`" pattern when a caller changes the title or `pollIntervalMinutes` (rejecting values `< 1`); this is how an interval edit actually takes effect — the scheduler re-registers the feed at the new interval rather than the running task picking it up on its own.

`OpmlImportService.importOpml` follows the same "create feed → publish `FeedSavedEvent`" pattern per new feed when bulk-importing an OPML file; existing feeds (matched by URL) only have their title/folder updated in place and are **not** re-published, since their polling schedule is already registered.

## 2. Event-driven scheduling

**Never call `FeedPollingScheduler.registerFeed`/`cancelFeed` directly from new feed-mutating code.** Publish `FeedSavedEvent` or `FeedDeletedEvent` instead — this was a deliberate refactor to decouple the scheduler from services.

`FeedPollingScheduler` listens with:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
```
on both `onFeedSaved`/`onFeedDeleted`. `AFTER_COMMIT` ensures the scheduler only reacts once the feed row is actually durable; `fallbackExecution = true` makes it still fire in non-transactional contexts (e.g. tests without a transaction). On app startup, `onStartup()` (`@EventListener(ApplicationReadyEvent.class)`) registers every existing feed.

`registerFeed` computes an **effective interval** (see backoff below) and schedules `pollAndAdjust` via `TaskScheduler.scheduleAtFixedRate`; it first cancels any existing task for that feed id, so re-publishing `FeedSavedEvent` for the same feed (e.g. an interval edit) safely replaces the schedule rather than stacking a second task.

## 3. Poll + dedup

`FeedPollingService.pollFeed(feedId)`:
1. Looks the feed up by id, throwing `NotFoundException` if it is gone — this call is outside the try/catch below, so a deleted feed propagates straight to the scheduler (see step 5).
2. Conditional fetch via `FeedFetcher.fetch(url, etag, lastModifiedHeader)` — sends `If-None-Match`/`If-Modified-Since`. A 304 short-circuits (just updates `lastPolledAt` and returns).
3. On 200, re-parses and **dedups articles by `(feed_id, guid)`** using `articleRepository.existsByFeedIdAndGuid` before inserting — this is the mechanism that prevents duplicate articles across polls.
4. On success: `errorCount` resets to 0, `lastError` cleared, `lastSuccessfulPollAt` and `lastPolledAt` updated, and the saved article ids are collected.
5. If any articles were inserted, `ArticlesIngestedEvent(feedId, newIds)` is published — this is the handoff into interest scoring (see the closing note below). Publishing is wrapped in its own try/catch so a scoring-side failure can never be mistaken for a poll failure.
6. On any exception during fetch/parse/save: `errorCount` increments and `lastError` is recorded (`lastPolledAt` is still updated), but the exception is swallowed (logged, not rethrown) — a single bad poll never crashes the scheduler thread.

## 4. Backoff — the interval self-adjusts after every poll

This is the trickiest part of the codebase. After `pollFeed` returns (success or failure), `FeedPollingScheduler.pollAndAdjust` **re-reads the feed's current error state from the DB** and recomputes the effective interval via `computeEffectiveInterval`:

- If `errorCount >= backoffThreshold` (default 5), the interval is `pollIntervalMinutes * 2^(errorCount / backoffThreshold)` (integer division for the exponent, capped at 30 to keep the `long` multiplier from overflowing), then clamped to `maxIntervalMinutes` (default 1440 = 24h).
- Otherwise, the interval is just the feed's configured `pollIntervalMinutes`.

If the desired interval differs from the currently scheduled one, the task is cancelled and replaced with a new `scheduleAtFixedRate` starting one full interval from now. This means backoff **engages progressively** as a feed keeps failing, and **clears immediately** once it succeeds again — without ever needing an external cron sweep. The interval re-evaluation itself is wrapped in a try/catch that logs a warning and keeps the current schedule on failure, so a transient DB blip during re-evaluation can't silently unschedule a feed.

There's an inherent, accepted race: a concurrent external `registerFeed`/`cancelFeed` (e.g. the user edits the feed at the same moment a poll completes) could in theory conflict with this self-replacement. The code comment in `FeedPollingScheduler` explicitly accepts this as fine for a single-user deployment (worst case is redundant polling until the feed is next edited or deleted).

## 5. Deletion

`FeedService.delete(id)` deletes the row (cascades to articles via FK `ON DELETE CASCADE` — see [Domain Concepts](../domain/concepts.md)) and publishes `FeedDeletedEvent(id)`, which the scheduler uses to cancel the scheduled task. `FeedPollingScheduler.pollAndAdjust` also self-cancels if `pollFeed` throws `NotFoundException` (feed deleted mid-flight, e.g. the delete and a scheduled poll race each other).

## 6. Retention (separate, unrelated cron)

`RetentionService.cleanupOldContent()` is a plain `@Scheduled(cron = myfeeder.retention.cleanup-cron)` job (default `0 0 3 * * *`, i.e. daily at 03:00) that clears `content`, `summary`, and `extracted_content` on articles whose `fetched_at` is older than `full-content-days` (default 30) via `articleRepository.clearContentOlderThan`. This is independent of the polling/backoff mechanism above — it only trims stored content (title, metadata, and scores are untouched), it does not delete rows or unschedule anything.

## Related, on-demand flow: reader-view extraction

`GET /api/articles/{id}/extracted-content` (`ArticleController.extractedContent` → `ArticleExtractionService.extract`) is a distinct, on-demand path, not part of the recurring poll: a user opens an article whose feed content is sparse, and the app fetches the article's own page to produce a readable "reader view."

- It reuses `FeedFetcher.fetch(url)` for the page fetch, so it gets the same SSRF guard (`FeedUrlValidator`) and response-size cap as feed polling — there is still only one fetch path in the codebase.
- The fetched HTML is decoded (header charset, else Jsoup's BOM/meta-tag detection) and run through Readability4J; `FeedParseException` (→422) is thrown if extraction yields no usable content, and `NotFoundException`/`IllegalArgumentException` cover a missing article or a missing URL.
- The result is cached in `article.extracted_content` via `articleRepository.saveExtractedContent`, so the first request pays the extraction cost and later requests for the same article just read the cached column.
- Because it shares the `extracted_content` column, `RetentionService.cleanupOldContent` ages this cache out on the same schedule and cutoff as `content`/`summary` — there is no separate retention rule for reader-view content.

## What to watch out for when changing this

- Any new code path that creates/updates/deletes a `Feed` must publish the corresponding event — don't add a second way to register/cancel scheduled polling.
- `computeEffectiveInterval` lives in exactly one place, `FeedPollingScheduler`; if you change backoff math, there's exactly one place to change it.
- `pollFeed` must keep publishing `ArticlesIngestedEvent` only after the success bookkeeping (`errorCount`/`lastError`/`lastSuccessfulPollAt`) is saved, and must keep that publish in its own try/catch — a scoring-side exception must never be attributed to the poll itself.
- Tests: `FeedPollingSchedulerTest`, `FeedPollingServiceTest`, `FeedServiceTest`, `OpmlImportServiceTest`, `RetentionServiceTest`, `ArticleExtractionServiceTest`, `ArticleControllerTest` — see [Testing Guide](../testing/guide.md).

## Handoff to interest scoring

A successful poll that inserts new articles publishes `ArticlesIngestedEvent(feedId, newArticleIds)` from `FeedPollingService`. `InterestScoringListener` consumes it (`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`), and — if the external scoring API is configured — hands the new article ids to `ScoringQueue` for asynchronous scoring. This event is the exact handoff point out of this ingestion pipeline and into the scoring pipeline; scoring mechanics, the queue, and the blended score are documented in [Interest Scoring Workflow](interest-scoring.md) and are not duplicated here.
