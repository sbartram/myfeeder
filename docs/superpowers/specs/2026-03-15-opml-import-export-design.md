# OPML Import/Export Design

## Overview

Add OPML import and export support to myfeeder, allowing users to bulk-import feed subscriptions from other readers and export their current subscriptions for backup or migration.

## Decisions

- **Duplicate handling (import):** Update existing feeds' title and folder assignment when the URL matches an existing subscription.
- **Folder nesting:** Flat only. Top-level OPML outline groups map to folders; deeper nesting is flattened (feeds assigned to the nearest top-level group).
- **Import speed:** Fire-and-forget. Feed records are created immediately from OPML metadata (title, URL). The scheduler's first poll fills in feedType, description, and articles.
- **Export scope:** Always exports all feeds. No folder filtering.
- **XML approach:** Built-in Java DOM parser and Transformer. No new dependencies.

## Components

### OpmlFeed Record

```
package: org.bartram.myfeeder.parser
```

```java
public record OpmlFeed(String title, String xmlUrl, String htmlUrl, String folderName) {}
```

`folderName` is the `text` attribute of the parent outline element. Null for uncategorized feeds.

### OpmlService

```
package: org.bartram.myfeeder.service
```

Handles XML parsing and generation. Stateless, no database access.

**Parsing (`parseOpml(InputStream) -> List<OpmlFeed>`):**
- Uses `DocumentBuilderFactory` with secure defaults: DTDs disabled, external entities disabled (XXE prevention).
- Walks `<body>` child `<outline>` elements.
- Top-level outlines with `xmlUrl` attribute are uncategorized feeds.
- Top-level outlines without `xmlUrl` are folders. Their child outlines with `xmlUrl` are feeds in that folder.
- Nesting beyond one level is flattened: feeds are assigned to the top-level folder regardless of depth.
- Throws `OpmlParseException` (new, extends `RuntimeException`) on malformed XML or missing `<body>` element.

**Generation (`generateOpml(List<Feed>, List<Folder>) -> String`):**
- Builds OPML 2.0 XML document using `Document` and `Transformer`.
- `<head>` contains `<title>myfeeder subscriptions</title>` and `<dateCreated>` (RFC 822).
- Feeds with a `folderId` are nested under their folder's `<outline text="folderName">` element.
- Uncategorized feeds (null `folderId`) go at the top level of `<body>`.
- Each feed outline has attributes: `type="rss"`, `text`, `title`, `xmlUrl` (feed URL), `htmlUrl` (siteUrl).

### OpmlImportService

```
package: org.bartram.myfeeder.service
```

Orchestrates the import. Depends on `OpmlService`, `FeedRepository`, `FolderRepository`, `FolderService`, `FeedPollingScheduler`, `MyfeederProperties`.

**`importOpml(InputStream) -> OpmlImportResult`:**

1. Call `opmlService.parseOpml(inputStream)` to get `List<OpmlFeed>`.
2. Load all existing feeds, index by URL (`Map<String, Feed>`).
3. Load all existing folders, index by lowercase name (`Map<String, Folder>`).
4. For each `OpmlFeed`:
   - **Folder resolution:** If `folderName` is non-null, look up by lowercase name. If not found, create via `FolderService.create()`.
   - **Feed exists?** (URL match) Update `title` and `folderId`. Save.
   - **Feed is new?** Create `Feed` with: `url` from `xmlUrl`, `title` from OPML `title`/`text`, `siteUrl` from `htmlUrl`, `folderId` from resolved folder, `pollIntervalMinutes` from `MyfeederProperties` default. Save and register with `FeedPollingScheduler.registerFeed()`.
5. Return `OpmlImportResult(created, updated, total)`.

Runs in a single `@Transactional` block. Scheduler registration (`registerFeed`) must happen after the transaction commits to avoid registering polls for feeds that get rolled back. Use `TransactionSynchronizationManager.registerSynchronization` with an `afterCommit` callback to collect new feed IDs during the transaction and register them with the scheduler post-commit.

```java
public record OpmlImportResult(int created, int updated, int total) {}
```

### OpmlController

```
package: org.bartram.myfeeder.controller
```

**`POST /api/opml/import`**
- Accepts `multipart/form-data` with a `file` part.
- Passes `file.getInputStream()` to `OpmlImportService.importOpml()`.
- Returns 200 with `OpmlImportResult` JSON.
- Returns 400 if file is missing, empty, or contains invalid OPML (catches `OpmlParseException`).

**`GET /api/opml/export`**
- Loads all feeds and folders from their respective services.
- Calls `OpmlService.generateOpml()`.
- Returns 200 with `Content-Type: application/xml` and `Content-Disposition: attachment; filename="myfeeder-export.opml"`.

### OpmlParseException

```
package: org.bartram.myfeeder.parser
```

```java
public class OpmlParseException extends RuntimeException {
    public OpmlParseException(String message) { super(message); }
    public OpmlParseException(String message, Throwable cause) { super(message, cause); }
}
```

## Frontend

### API Client

New file `src/main/frontend/src/api/opml.ts`:

- `importOpml(file: File): Promise<OpmlImportResult>` — POST multipart to `/api/opml/import`.
- `exportOpml(): Promise<void>` — GET `/api/opml/export`, trigger browser download via blob URL.

### Hook

New file `src/main/frontend/src/hooks/useOpml.ts`:

- `useImportOpml()` — mutation wrapping `importOpml`. On success: invalidates `['feeds']` and `['folders']` query keys, shows toast with summary (e.g. "Imported 12 new feeds, updated 3 existing").
- `exportOpml()` — plain async function (not a mutation), triggers download, shows toast on completion.

### UI

Import and export buttons added to the feed panel toolbar area (near existing "Add Feed"). No new pages, dialogs, or routes.

- **Import:** File input button accepting `.opml` and `.xml`. On file select, calls import mutation.
- **Export:** Button that calls `exportOpml()`.

## Security

- XML parsing uses secure `DocumentBuilderFactory` configuration:
  - `FEATURE_SECURE_PROCESSING` enabled
  - DTD loading disabled (`http://apache.org/xml/features/disallow-doctype-decl` set to true)
  - External entities disabled
- This prevents XXE (XML External Entity) attacks from malicious OPML files.
- Spring's default multipart size limit applies. Optionally set `spring.servlet.multipart.max-file-size=1MB` as a safety net.

## Testing

### Backend

**`OpmlServiceTest`** (unit):
- Parse standard OPML with folders and feeds — verify correct `OpmlFeed` list.
- Parse feeds without folders (top-level outlines) — verify null `folderName`.
- Parse nested folders — verify flattening to one level.
- Parse empty OPML — verify `OpmlParseException`.
- Parse malformed XML — verify `OpmlParseException`.
- Generate OPML from feeds and folders — verify valid XML, correct outline structure.
- Sample OPML files in `src/test/resources/opml/`.

**`OpmlImportServiceTest`** (unit, Mockito):
- New feeds are created with correct fields and registered with scheduler.
- Existing feeds (by URL) are updated (title, folderId) but not re-registered with scheduler.
- Folders are reused by name (case-insensitive).
- New folders are created when needed.
- Import result counts are correct.

**`OpmlControllerTest`** (`@WebMvcTest`):
- Import with valid OPML multipart file — 200 with result JSON.
- Import with invalid OPML — 400.
- Import with missing file — 400.
- Export — 200 with XML content type and attachment disposition header.

### Frontend

- Hook test: verify query invalidation on import success.

## Files to Create

| File | Type |
|------|------|
| `src/main/java/org/bartram/myfeeder/parser/OpmlFeed.java` | Record |
| `src/main/java/org/bartram/myfeeder/parser/OpmlParseException.java` | Exception |
| `src/main/java/org/bartram/myfeeder/service/OpmlService.java` | Service |
| `src/main/java/org/bartram/myfeeder/service/OpmlImportService.java` | Service |
| `src/main/java/org/bartram/myfeeder/service/OpmlImportResult.java` | Record |
| `src/main/java/org/bartram/myfeeder/controller/OpmlController.java` | Controller |
| `src/main/frontend/src/api/opml.ts` | API client |
| `src/main/frontend/src/hooks/useOpml.ts` | Hook |
| `src/test/java/org/bartram/myfeeder/service/OpmlServiceTest.java` | Test |
| `src/test/java/org/bartram/myfeeder/service/OpmlImportServiceTest.java` | Test |
| `src/test/java/org/bartram/myfeeder/controller/OpmlControllerTest.java` | Test |
| `src/test/resources/opml/*.opml` | Test fixtures |

## Files to Modify

| File | Change |
|------|--------|
| Feed panel component(s) | Add import/export buttons |

## No Schema Changes

The existing `feed` and `folder` tables are sufficient. No Flyway migration needed.
