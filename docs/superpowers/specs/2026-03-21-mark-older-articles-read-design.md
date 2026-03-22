# Mark Articles Older Than X Days as Read

## Summary

Add the ability to mark all unread articles in the currently selected feed that are older than a user-specified number of days as read. Extends the existing `POST /api/articles/mark-read` endpoint and adds a split-button UI with a dialog for day selection.

## Backend

### MarkReadRequest

Add optional `Integer olderThanDays` field to the existing `MarkReadRequest` DTO. When present alongside `feedId`, articles in that feed with `published_at` before the computed cutoff are marked as read.

Validation: `olderThanDays` must be >= 1 if provided. Requires `feedId` when `olderThanDays` is set.

### ArticleRepository

New query method:

```java
@Modifying
@Query("UPDATE article SET read = true WHERE feed_id = :feedId AND published_at < :cutoff AND read = false")
void markReadByFeedIdOlderThan(Long feedId, Instant cutoff);
```

### ArticleService

In `markRead()`, add a branch: if `olderThanDays` is present, compute `Instant cutoff = Instant.now().minus(olderThanDays, ChronoUnit.DAYS)` and call `markReadByFeedIdOlderThan(feedId, cutoff)`.

### ArticleController

No new endpoint. The existing `POST /api/articles/mark-read` handles the new field transparently.

## Frontend

### API Layer

Update `articlesApi.markRead()` to accept an optional `olderThanDays` parameter, passed through in the request body.

### ArticleList Toolbar

Convert the existing "Mark all read" button into a **split button**:
- **Main area**: Clicking does the current behavior (mark all read in feed)
- **Dropdown chevron**: Opens a small menu with one option: "Mark older than..." which opens the dialog

### MarkOlderReadDialog

A small modal dialog containing:
- **Preset quick-pick buttons**: 7 days, 30 days, 90 days
- **Custom number input**: Free-form input for any positive integer, labeled "days"
- **Action buttons**: "Mark as read" (primary, disabled until a value is selected/entered) and "Cancel"
- Clicking a preset populates the input; user can then click "Mark as read" or just click the preset directly to apply
- Shows the feed name for context (e.g., "Mark articles older than X days as read in **Feed Name**")

### Hooks

`useMarkRead()` already invalidates `articles` and `unreadCounts` queries. No changes needed — just pass `olderThanDays` through.

## Testing

### Backend
- **Repository test**: Verify `markReadByFeedIdOlderThan` only marks articles older than the cutoff in the specified feed
- **Service test**: Verify cutoff computation and delegation to repository
- **Controller test**: Verify `olderThanDays` is accepted in the request and passed through

### Frontend
- **Dialog test**: Renders presets, accepts custom input, calls onConfirm with correct days value
- **ArticleList test**: Split button renders dropdown, triggers dialog

## No Migration Needed

Uses existing columns: `feed_id`, `published_at`, `read`. No schema changes.
