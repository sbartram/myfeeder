# Cleanup & Polish Design

## Overview

Four independent improvements: resilience4j for Raindrop, "Read Later" board action, hide-read-feeds setting, and reading pane icons.

## 1. Resilience4j for Raindrop Integration

### Configuration

Add to `application.yaml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      raindrop:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
  retry:
    instances:
      raindrop:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
```

Also add to test `application.yaml` so integration tests have the config available.

### Code Changes

**`RaindropService.java`**: Add `@Retry(name = "raindrop")` and `@CircuitBreaker(name = "raindrop", fallbackMethod = "saveToRaindropFallback")` to the `saveToRaindrop` method. Add fallback method that throws `IllegalStateException` with a descriptive message including the cause.

```java
@CircuitBreaker(name = "raindrop", fallbackMethod = "saveToRaindropFallback")
@Retry(name = "raindrop")
public void saveToRaindrop(Article article) { ... }

private void saveToRaindropFallback(Article article, Throwable throwable) {
    throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
}
```

`@CircuitBreaker` is declared first (outer) so the circuit breaker wraps the retry logic. Each retry attempt counts against the circuit breaker's sliding window. If the circuit opens, retries stop immediately.

### Dependencies

Already present: `spring-cloud-starter-circuitbreaker-resilience4j` is in `build.gradle.kts` and the Spring Cloud BOM manages the version.

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/org/bartram/myfeeder/integration/RaindropService.java` | Add annotations + fallback |
| `src/main/resources/application.yaml` | Add resilience4j config |
| `src/test/resources/application.yaml` | Add resilience4j config |

## 2. "Read Later" Board Action

### Backend

**New endpoint**: `POST /api/boards/by-name` — accepts `{name: string}`, returns existing board matching the name (case-insensitive) or creates a new one. This keeps the get-or-create logic server-side.

**`BoardService.java`**: Add `getOrCreateByName(String name)`:

```java
public Board getOrCreateByName(String name) {
    return boardRepository.findByNameIgnoreCase(name)
        .orElseGet(() -> create(name, null));
}
```

**`BoardRepository`**: Add `Optional<Board> findByNameIgnoreCase(String name)` query method.

**`BoardController.java`**: Add endpoint:

```java
@PostMapping("/by-name")
public Board getOrCreateByName(@RequestBody Map<String, String> body) {
    return boardService.getOrCreateByName(body.get("name"));
}
```

### Frontend

**API client** (`boards.ts`): Add `getOrCreateByName(name: string)` — POST to `/api/boards/by-name`.

**New hook** (`useBoards.ts`): Add `useReadLater()` mutation. The `mutationFn` performs both API calls sequentially with `await`:

```typescript
mutationFn: async (articleId: number) => {
  const board = await boardsApi.getOrCreateByName('Read Later')
  await boardsApi.addArticle(board.id, articleId)
}
```

On success: invalidate `['boards']` and `['boardArticles']` queries, show success toast. On error (whether from board creation or article addition): show error toast. Partial failure (board created but article add fails) is acceptable — the board exists for next time and the user can retry.

**ReadingPane**: Add "Read Later" button to toolbar, using the `useReadLater()` hook.

### Files to Create

None — all changes go in existing files.

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/org/bartram/myfeeder/repository/BoardRepository.java` | Add `findByNameIgnoreCase` query |
| `src/main/java/org/bartram/myfeeder/service/BoardService.java` | Add `getOrCreateByName` |
| `src/main/java/org/bartram/myfeeder/controller/BoardController.java` | Add `POST /by-name` endpoint |
| `src/main/frontend/src/api/boards.ts` | Add `getOrCreateByName` |
| `src/main/frontend/src/hooks/useBoards.ts` | Add `useReadLater` hook |
| `src/main/frontend/src/components/ReadingPane.tsx` | Add "Read Later" button |

## 3. "Hide Read Feeds" Setting

### Frontend Only

**`preferencesStore.ts`**: Add `hideReadFeeds: boolean` (default `false`) and `setHideReadFeeds` setter.

**`SettingsDialog.tsx`**: Add a checkbox under the Appearance section:

```tsx
<label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--text-muted)' }}>
  <input
    type="checkbox"
    checked={prefs.hideReadFeeds}
    onChange={(e) => prefs.setHideReadFeeds(e.target.checked)}
  />
  Hide feeds with no unread articles
</label>
```

**`FeedPanel.tsx`**: Read the preference and filter feeds when enabled:

```typescript
const hideReadFeeds = usePreferences((s) => s.hideReadFeeds)

// In folder feed list and uncategorized list:
// Filter out feeds where feedUnread(feed.id) === 0 when hideReadFeeds is true
const visibleFeeds = (feedList: Feed[]) =>
  hideReadFeeds ? feedList.filter((f) => feedUnread(f.id) > 0 || f.id === selectedFeedId) : feedList
```

The currently-selected feed is always shown even if it has no unread articles, to prevent it from vanishing while the user is looking at it. Folders are always shown (even if empty after filtering) to avoid jarring layout shifts.

### Files to Modify

| File | Change |
|------|--------|
| `src/main/frontend/src/stores/preferencesStore.ts` | Add `hideReadFeeds` + setter |
| `src/main/frontend/src/components/SettingsDialog.tsx` | Add checkbox |
| `src/main/frontend/src/components/FeedPanel.tsx` | Filter feeds by unread count |

## 4. Reading Pane Icons

Add unicode icons before each toolbar button label in `ReadingPane.tsx`:

| Action | Current Label | New Label |
|--------|--------------|-----------|
| Star (unstarred) | `Star` | `★ Star` |
| Star (starred) | `Unstar` | `★ Unstar` |
| Board | `Board` | `📋 Board` |
| Read Later | *(new)* | `🔖 Read Later` |
| Raindrop | `Raindrop` | `💧 Raindrop` |
| Copy Link | `Copy Link` | `🔗 Copy Link` |
| Open Original | `Open Original` | `↗ Open Original` |

### Files to Modify

| File | Change |
|------|--------|
| `src/main/frontend/src/components/ReadingPane.tsx` | Add icons to button labels |

## Testing

### Backend

**`BoardServiceTest`**: Add test for `getOrCreateByName` — returns existing board by name (case-insensitive), creates new board when not found.

**`BoardControllerTest`**: Add test for `POST /api/boards/by-name` — stub `boardService.getOrCreateByName("Read Later")` to return a board, verify 200 with board JSON.

**`RaindropService`**: Existing tests cover the save method. Resilience4j annotations are config-only and tested via integration test (app context loads with the config).

### Frontend

**`useReadLater` hook test** (`useBoards.test.ts`): Mock the boards API, verify both `getOrCreateByName` and `addArticle` are called in sequence on mutation. Follow the `useOpml.test.ts` pattern.

**Manual verification**: Switch themes and verify icons render correctly on all themes. Test hide-read-feeds toggle. Test Read Later button creates board and adds article.

## No Schema Changes

All backend changes use existing database tables. No Flyway migration needed.
