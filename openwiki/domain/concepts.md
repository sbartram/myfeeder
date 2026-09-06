---
type: Domain Model
title: myfeeder Domain Concepts (Feed, Article, Folder, Board, Integration)
description: Explains the core data model — Feed, Article (including the cached extracted_content reader-view column), Folder, Board/BoardArticle, IntegrationConfig — their schema, relationships, Flyway migration history, and the business rules around article sort order, cursor pagination, retention, and read/starred state that every API consumer must respect.
resource: src/main/resources/db/migration
tags: [domain-model, database, schema, api]
verified:
  - by: openwiki/0.5.0
    at: 2026-09-06T12:04:59.431Z
sources:
  - id: openwiki-source-d6d102291b1964678af254e9
    resource: repo://src/main/frontend/src/components/ReadingPane.tsx
  - id: openwiki-source-cdc24fc3ca0c47ee6972a535
    resource: repo://src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - id: openwiki-source-ddb3b890ea464a84e24378db
    resource: repo://src/main/java/org/bartram/myfeeder/model/Article.java
  - id: openwiki-source-467204ca8042feb6892811eb
    resource: repo://src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java
  - id: openwiki-source-5af9bbadd4381dae61b11d89
    resource: repo://src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java
  - id: openwiki-source-74146f2d58936b037f62e557
    resource: repo://src/main/java/org/bartram/myfeeder/service/FeedPollingService.java
  - id: openwiki-source-e1ca7236dc9fb7545718d969
    resource: repo://src/main/java/org/bartram/myfeeder/service/RetentionService.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
  - id: openwiki-source-51200a041d438c053e6983d3
    resource: repo://src/main/resources/db/migration/V5__article_extracted_content.sql
generated: { by: "openwiki/0.5.0", at: "2026-09-06T12:04:59.431Z" }
---

# Domain Concepts

## Entities and schema evolution

Flyway migrations tell the story of how the domain grew:

- **`V1__initial_schema.sql`**: `feed` and `article` tables (core RSS reader), plus `integration_config` (generic key/value integration settings, unique per `type`).
- **`V2__folders_boards_and_feed_folder.sql`**: adds `folder` (feed grouping, with `display_order` for drag-to-reorder) and `board`/`board_article` (curated collections a user saves articles into — see "read later" in `docs/backlog.md`), plus `feed.folder_id`.
- **`V3__article_image_url.sql`**: adds `article.image_url` (thumbnail extracted from feed content).
- **`V4__strip_raindrop_api_token.sql`**: removes a legacy `apiToken` field that used to live inside `integration_config.config` JSON — the token moved to `myfeeder.raindrop.api-token` (env-var-backed config property) instead of being stored in the DB. See [Raindrop Integration](../integrations/raindrop.md). This migration is unusual in that it can't be re-triggered by normal Flyway test startup (see `V4StripRaindropApiTokenMigrationTest` in [Testing Guide](../testing/guide.md) for the pattern used to test it).

### `Feed` (`model/Feed.java` / table `feed`)
Tracks a subscribed source: `url`, `title`, `description`, `siteUrl`, `feedType` (RSS/Atom/JSON — `FeedType` enum), `pollIntervalMinutes`, `folderId`, and **poll health state**: `lastPolledAt`, `lastSuccessfulPollAt`, `errorCount`, `lastError`, `etag`, `lastModifiedHeader`. The last four fields exist purely to support conditional GETs and backoff — see [Feed Lifecycle Workflow](../workflows/feed-lifecycle.md).

### `Article` (`model/Article.java` / table `article`)
One article from a feed: `feedId` (FK, cascade delete), `guid` (dedup key, unique with `feedId`), `title`, `url`, `author`, `content` (full HTML from the feed itself), `summary` (short feed-supplied description, used by the frontend as a fallback when `content` is empty), `imageUrl`, `publishedAt`, `fetchedAt`, `read`, `starred`.

**`extracted_content` is deliberately not a field on the `Article` entity.** The column (added in `V5__article_extracted_content.sql`) holds a cache of the *reader view* — readable HTML extracted from the article's original page via Readability4J — but it's read and written only through two dedicated `ArticleRepository` methods, `findExtractedContent`/`saveExtractedContent`, never through the `Article` object returned by the list/detail endpoints. Clients fetch it via the separate `GET /api/articles/{id}/extracted-content` endpoint (`ArticleController.extractedContent` → `ArticleExtractionService.extract`), which serves the cached column value if present and otherwise fetches the page, extracts, caches, and returns it. See [Reader View Extraction Workflow](../workflows/reader-view-extraction.md) for the full fetch/extract/cache flow and how the frontend decides when to call it.

**Retention only clears `content` and `extracted_content`, not `summary`.** `RetentionService.cleanupOldContent()` runs on `myfeeder.retention.cleanup-cron` and nulls `article.content`/`article.extracted_content` for rows older than `full-content-days` (`ArticleRepository.clearContentOlderThan`). `summary` is left untouched indefinitely, which is why the reading pane can still show a short preview for old articles even after their full content and cached reader view have been purged — and why a reader-view request for an old article always re-extracts from the live page rather than erroring.

**Sort order rule (important, easy to get wrong):** articles are sorted by `COALESCE(published_at, fetched_at)`, **not** by `id`. Batch-fetched articles (e.g. from a bulk OPML import or a feed with a backlog of items) get sequential DB IDs but widely varied publication dates, so `ORDER BY id` does not produce chronological order. Cursor pagination therefore uses a composite `(published_at, id)` comparison — `ArticleController`/`ArticleService` still expose a single article ID as the cursor, but the service looks up that cursor article's date to build the SQL comparison.

**Pagination shape:** all list endpoints return `PaginatedResponse<T>` (`{items, nextCursor}` — the field is `items`, not `articles`). Controllers fetch `limit + 1` rows and delegate the "does another page exist / trim the extra row" logic to `PaginatedResponse.of(fetched, limit, idExtractor)` (see `controller/PaginatedResponse.java`) — don't reimplement that trimming logic per-controller.

**Reading pane fetch-by-ID:** the reading pane fetches the selected article directly via `GET /api/articles/{id}` (`useArticle(id)`), not by searching the paginated list query client-side. This avoids the reading pane and the article list disagreeing about filters/sort — a pattern also leaned on by `useKeyboardShortcuts` (see [Architecture Overview](../architecture/overview.md)) to resolve the "current article" on views where the visible list and the hook's list diverge.

### `Folder` (`model/Folder.java` / table `folder`)
A named grouping of feeds with a user-controlled `displayOrder` (drag-to-reorder in the sidebar, `c47a2a0`/`4cc1a1b`). A feed's `folderId` is nullable (`ON DELETE SET NULL`), so deleting a folder ungroups its feeds rather than deleting them.

### `Board` / `BoardArticle` (`model/Board.java`, `BoardArticle.java` / tables `board`, `board_article`)
A board is a named, user-created collection (e.g. "Read Later"); `board_article` is the join table (`UNIQUE(board_id, article_id)` — adding the same article twice is a no-op, see `BoardService.addArticle`). `BoardService.getOrCreateByName` backs the "Read Later" quick-action so it doesn't need a separate creation step in the UI.

### `IntegrationConfig` (`model/IntegrationConfig.java` / table `integration_config`)
Generic per-integration settings row (`type` unique, JSON `config` blob, `enabled` flag). Currently the only consumer is Raindrop.io — see [Raindrop Integration](../integrations/raindrop.md) for how `RaindropConfig` is deserialized from the `config` column and how the token itself is deliberately **not** stored here (V4 migration).

## API surface (by domain)

| Domain | Controller | Base path |
|---|---|---|
| Feed | `FeedController` | `/api/feeds` |
| Article | `ArticleController` | `/api/articles` |
| Folder | `FolderController` | `/api/folders` |
| Board | `BoardController` | `/api/boards` |
| Integration | `IntegrationConfigController` | `/api/integrations` |
| OPML | `OpmlController` | `/api/opml` |

Request bodies are typed records per action (e.g. `FeedUpdateRequest`, `CreateBoardRequest`, `MoveFeedToFolderRequest`, `ArticleStateRequest`, `MarkReadRequest`) rather than raw `Map` — see [Architecture Overview](../architecture/overview.md) for the HTTP-layer conventions and exception-to-status mapping shared by all these controllers.

## Business rules worth remembering

- **Article dedup key is `(feed_id, guid)`**, enforced at the DB level and checked in `FeedPollingService` before insert.
- **`read`/`starred` are independent booleans**, both patchable via `PATCH /api/articles/{id}` (`ArticleStateRequest`). "Mark read" also supports bulk operations by article ID list, by feed, or by "older than N days" (`MarkReadRequest` — see `docs/backlog.md` for the UI preset days: 1/3/7/14).
- **OPML import** (`OpmlService` + `OpmlImportService`) has explicit XXE protection in the XML parser — preserve this if you touch `OpmlService`'s parsing code. Import matches existing feeds by URL (update in place) and folders by case-insensitive name (create if missing); only newly created feeds publish `FeedSavedEvent`.
