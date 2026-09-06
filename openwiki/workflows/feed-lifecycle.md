---
type: Workflow
title: Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention)
description: Walks through how a feed is subscribed, scheduled, polled on a recurring basis with error backoff, kept in sync via application events on save/delete, and eventually has old article content cleared by the retention job. Key file for anyone changing polling, scheduling, or article ingestion behavior.
resource: src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java
tags: [workflow, scheduling, polling, events]
verified:
  - by: openwiki/0.5.0
    at: 2026-09-06T12:04:59.431Z
sources:
  - id: openwiki-source-3fc6a75fee36d9e5cebafb69
    resource: repo://src/main/java/org/bartram/myfeeder/parser/FeedParser.java
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
  - id: openwiki-source-0ca4313738bd1faeedf7586c
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java
  - id: openwiki-source-dc52751c9213b210ffa4f78c
    resource: repo://src/main/java/org/bartram/myfeeder/service/OpmlImportService.java
  - id: openwiki-source-e1ca7236dc9fb7545718d969
    resource: repo://src/main/java/org/bartram/myfeeder/service/RetentionService.java
generated: { by: "openwiki/0.5.0", at: "2026-09-06T12:04:59.431Z" }
---

# Feed Lifecycle Workflow

This is the core business process of myfeeder: get new articles from subscribed feeds into the database reliably, without hammering slow or broken feeds.

## 1. Subscribe

`FeedService.subscribe(feedUrl, folderId)`:
1. Calls `FeedFetcher.fetch(url)` — an **unconditional** GET. `FeedFetcher` is the single component in the codebase that performs outbound HTTP retrieval of a caller-supplied URL (see [Architecture Overview](../architecture/overview.md)), and every fetch through it — subscribe-time and every subsequent poll alike — is protected by the same two guards baked into `FeedFetcher` itself:
   - **SSRF guard (`FeedUrlValidator`)** — `FeedFetcher.fetch` calls `urlValidator.validate(url)` before issuing the request. It requires `http`/`https` and rejects any resolved address that is loopback, link-local, RFC1918 site-local, any-local, multicast, IPv6 unique-local, or carrier-grade NAT, so a subscribed feed URL can't steer the server at internal targets (database, in-cluster services, cloud metadata endpoints). A rejection is an `IllegalArgumentException` (→400).
   - **Size cap (`DEFAULT_MAX_FEED_BYTES` = 10 MiB)** — after a non-error, non-304 response, `FeedFetcher` reads at most `maxFeedBytes + 1` bytes; exceeding the cap throws `FeedFetchException` (→422) instead of continuing to buffer, bounding heap use against a huge or streaming body.
   Because both guards live inside `FeedFetcher` itself rather than in each caller, they apply uniformly no matter who calls it — the reader-view extraction workflow (`ArticleExtractionService.extract`, fetching an article's original page) reuses this exact same `FeedFetcher` for the same reasons: it too dereferences a URL that ultimately came from a subscribed feed and must not be trusted with an unguarded, unbounded fetch.
2. Parses the response with `FeedParser` (ROME for RSS/Atom, Jackson for JSON Feed) into a `ParsedFeed`. `FeedParser.parse` throws `FeedParseException` (→422) if the result has no title and no articles — this guards against "200 OK with garbage" responses that would otherwise violate the `feed.title NOT NULL` constraint.
3. Saves a new `Feed` row with `pollIntervalMinutes` from `myfeeder.polling.default-interval-minutes`.
4. Publishes `FeedSavedEvent(saved)` via `ApplicationEventPublisher` — this is what actually schedules polling (see step 2).

`FeedService.update(id, updates)` also re-publishes `FeedSavedEvent(saved)` after changing `title`/`pollIntervalMinutes` (rejecting `pollIntervalMinutes < 1`), which is how an interval edit reaches the scheduler (step 2 re-registers the feed with the new interval).

`OpmlImportService.importOpml` follows the same "create feed → publish `FeedSavedEvent`" pattern per new feed when bulk-importing an OPML file (existing feeds by URL are updated in place, not re-published).

## 2. Event-driven scheduling

**Never call `FeedPollingScheduler.registerFeed`/`cancelFeed` directly from new feed-mutating code.** Publish `FeedSavedEvent` or `FeedDeletedEvent` instead — this was a deliberate refactor (commit `9583ac2`) to decouple the scheduler from services.

`FeedPollingScheduler` listens with:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
```
on both `onFeedSaved`/`onFeedDeleted`. `AFTER_COMMIT` ensures the scheduler only reacts once the feed row is actually durable; `fallbackExecution = true` makes it still fire in non-transactional contexts (e.g. tests without a transaction). On app startup, `onStartup()` (`@EventListener(ApplicationReadyEvent.class)`) registers every existing feed.

`registerFeed` computes an **effective interval** (see backoff below) and schedules `pollAndAdjust` via `TaskScheduler.scheduleAtFixedRate`.

## 3. Poll + dedup

`FeedPollingService.pollFeed(feedId)`:
1. Conditional fetch via `FeedFetcher.fetch(url, etag, lastModifiedHeader)` — sends `If-None-Match`/`If-Modified-Since`. A 304 short-circuits (just updates `lastPolledAt`). This is the same `FeedFetcher` instance/path as subscribe, so the SSRF guard and size cap described in step 1 apply to every recurring poll too, not just the initial fetch — an already-subscribed feed whose DNS is later repointed at an internal address, or whose response balloons past `DEFAULT_MAX_FEED_BYTES`, is still caught on each poll.
2. On 200, re-parses and **dedups articles by `(feed_id, guid)`** using `articleRepository.existsByFeedIdAndGuid` before inserting — this is the mechanism that prevents duplicate articles across polls.
3. On success: `errorCount` resets to 0, `lastError` cleared, `lastSuccessfulPollAt` updated.
4. On any exception: `errorCount` increments and `lastError` is recorded, but the exception is swallowed (logged, not rethrown) — a single bad poll never crashes the scheduler thread.

## 4. Backoff — the interval self-adjusts after every poll

This is the trickiest part of the codebase and had two bugfix commits (`933950e`, `c95abec`) before landing correctly. After `pollFeed` returns (success or failure), `FeedPollingScheduler.pollAndAdjust` **re-reads the feed's current error state from the DB** and recomputes the effective interval via `computeEffectiveInterval`:

- If `errorCount >= backoffThreshold` (default 5), the interval is `pollIntervalMinutes * 2^(errorCount / backoffThreshold)`, capped at `maxIntervalMinutes` (default 1440 = 24h).
- Otherwise, the interval is just the feed's configured `pollIntervalMinutes`.

If the desired interval differs from the currently scheduled one, the task is cancelled and replaced with a new `scheduleAtFixedRate` starting one full interval from now. This means backoff **engages progressively** as a feed keeps failing, and **clears immediately** once it succeeds again — without ever needing an external cron sweep. The interval re-evaluation itself is wrapped in a try/catch that logs a warning and keeps the current schedule on failure, so a transient DB blip during re-evaluation can't silently unschedule a feed (fixed by `c95abec`).

There's an inherent, accepted race: a concurrent external `registerFeed`/`cancelFeed` (e.g. the user edits the feed at the same moment a poll completes) could in theory conflict with this self-replacement. The code comment in `FeedPollingScheduler` explicitly accepts this as fine for a single-user deployment.

## 5. Deletion

`FeedService.delete(id)` deletes the row (cascades to articles via FK `ON DELETE CASCADE` — see [Domain Concepts](../domain/concepts.md)) and publishes `FeedDeletedEvent(id)`, which the scheduler uses to cancel the scheduled task. `FeedPollingScheduler.pollAndAdjust` also self-cancels if `pollFeed` throws `NotFoundException` (feed deleted mid-flight).

## 6. Retention (separate, unrelated cron)

`RetentionService.cleanupOldContent()` is a plain `@Scheduled(cron = myfeeder.retention.cleanup-cron)` job (default daily at 03:00) that clears `content`/`summary` text on articles older than `full-content-days` (default 30) via `articleRepository.clearContentOlderThan`. This is independent of the polling/backoff mechanism above — it only trims stored content, it does not delete or unschedule anything.

## What to watch out for when changing this

- Any new code path that creates/updates/deletes a `Feed` must publish the corresponding event — don't add a second way to register/cancel scheduled polling.
- `computeEffectiveInterval` is duplicated conceptually only in `FeedPollingScheduler`; if you change backoff math, there's exactly one place to change it.
- Tests: `FeedPollingSchedulerTest`, `FeedPollingServiceTest`, `FeedServiceTest`, `OpmlImportServiceTest` — see [Testing Guide](../testing/guide.md).
