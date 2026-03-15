# Cleanup & Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four independent improvements: resilience4j for Raindrop, "Read Later" board action, hide-read-feeds setting, and reading pane icons.

**Architecture:** Each item is independent and can be implemented/tested separately. Backend changes for resilience4j (YAML config + annotations) and "Read Later" (new endpoint + service method). Frontend changes for all four items.

**Tech Stack:** Spring Boot 4.0.3, Resilience4j via Spring Cloud, React 19, TanStack Query, Zustand, Vitest

**Spec:** `docs/superpowers/specs/2026-03-15-cleanup-polish-design.md`

---

## Chunk 1: Resilience4j for Raindrop

### Task 1: Add Resilience4j Configuration

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`

- [ ] **Step 1: Add resilience4j config to application.yaml**

Append after the existing `myfeeder:` block:

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

- [ ] **Step 2: Add same config to test application.yaml**

Append the same `resilience4j:` block to `src/test/resources/application.yaml`.

- [ ] **Step 3: Add annotations to RaindropService**

Modify `src/main/java/org/bartram/myfeeder/integration/RaindropService.java`.

Add imports:
```java
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
```

Add annotations to `saveToRaindrop` method (line 25):
```java
@CircuitBreaker(name = "raindrop", fallbackMethod = "saveToRaindropFallback")
@Retry(name = "raindrop")
public void saveToRaindrop(Article article) {
```

Add fallback method after `saveToRaindrop` (after line 55):
```java
private void saveToRaindropFallback(Article article, Throwable throwable) {
    throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
}
```

- [ ] **Step 4: Run all tests to verify nothing breaks**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (the integration test verifies the app context loads with resilience4j config).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.yaml src/test/resources/application.yaml src/main/java/org/bartram/myfeeder/integration/RaindropService.java
git commit -m "feat: add resilience4j circuit breaker and retry for Raindrop integration"
```

## Chunk 2: "Read Later" Board Action

### Task 2: BoardRepository — Add findByNameIgnoreCase

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/repository/BoardRepository.java`

- [ ] **Step 1: Add query method**

Spring Data JDBC does not support derived query methods like JPA. Use `@Query` annotation:

```java
package org.bartram.myfeeder.repository;
import org.bartram.myfeeder.model.Board;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;

public interface BoardRepository extends ListCrudRepository<Board, Long> {
    @Query("SELECT * FROM board WHERE LOWER(name) = LOWER(:name)")
    Optional<Board> findByNameIgnoreCase(String name);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/repository/BoardRepository.java
git commit -m "feat: add findByNameIgnoreCase to BoardRepository"
```

### Task 3: BoardService — Add getOrCreateByName (Test)

**Files:**
- Modify: `src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java`

- [ ] **Step 1: Add tests for getOrCreateByName**

Append to the existing `BoardServiceTest` class:

```java
@Test
void shouldReturnExistingBoardByName() {
    Board existing = new Board();
    existing.setId(1L);
    existing.setName("Read Later");
    when(boardRepository.findByNameIgnoreCase("Read Later")).thenReturn(Optional.of(existing));

    Board result = boardService.getOrCreateByName("Read Later");

    assertThat(result.getId()).isEqualTo(1L);
    verify(boardRepository, never()).save(any());
}

@Test
void shouldCreateBoardWhenNameNotFound() {
    when(boardRepository.findByNameIgnoreCase("Read Later")).thenReturn(Optional.empty());
    when(boardRepository.save(any())).thenAnswer(inv -> { Board b = inv.getArgument(0); b.setId(2L); return b; });

    Board result = boardService.getOrCreateByName("Read Later");

    assertThat(result.getName()).isEqualTo("Read Later");
    verify(boardRepository).save(any());
}
```

Add missing import at top:
```java
import java.util.Optional;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*BoardServiceTest*"`
Expected: Compilation failure — `getOrCreateByName` does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java
git commit -m "test: add BoardService getOrCreateByName tests (red)"
```

### Task 4: BoardService — Implement getOrCreateByName

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/service/BoardService.java`

- [ ] **Step 1: Add getOrCreateByName method**

Add after the `create` method (line 28):

```java
public Board getOrCreateByName(String name) {
    return boardRepository.findByNameIgnoreCase(name)
        .orElseGet(() -> create(name, null));
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "*BoardServiceTest*"`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/BoardService.java
git commit -m "feat: implement BoardService.getOrCreateByName"
```

### Task 5: BoardController — Add POST /by-name (Test + Implementation)

**Files:**
- Modify: `src/test/java/org/bartram/myfeeder/controller/BoardControllerTest.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/BoardController.java`

- [ ] **Step 1: Add controller test**

Append to the existing `BoardControllerTest` class:

```java
@Test
void shouldGetOrCreateBoardByName() throws Exception {
    Board board = new Board(); board.setId(1L); board.setName("Read Later"); board.setCreatedAt(Instant.now());
    when(boardService.getOrCreateByName("Read Later")).thenReturn(board);

    mockMvc.perform(post("/api/boards/by-name")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Read Later\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Read Later"));
}
```

- [ ] **Step 2: Add endpoint to BoardController**

Add after the `createBoard` method (line 25):

```java
@PostMapping("/by-name")
public Board getOrCreateByName(@RequestBody Map<String, String> body) {
    return boardService.getOrCreateByName(body.get("name"));
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*BoardControllerTest*"`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/controller/BoardControllerTest.java src/main/java/org/bartram/myfeeder/controller/BoardController.java
git commit -m "feat: add POST /api/boards/by-name endpoint"
```

### Task 6: Frontend — Read Later API, Hook, and Button

**Files:**
- Modify: `src/main/frontend/src/api/boards.ts`
- Modify: `src/main/frontend/src/hooks/useBoards.ts`
- Modify: `src/main/frontend/src/components/ReadingPane.tsx`

- [ ] **Step 1: Add getOrCreateByName to boards API client**

Add to `boardsApi` object in `src/main/frontend/src/api/boards.ts`:

```typescript
getOrCreateByName: (name: string) =>
  apiPost<Board>('/boards/by-name', { name }),
```

- [ ] **Step 2: Add useReadLater hook**

Add to `src/main/frontend/src/hooks/useBoards.ts`:

```typescript
import { useToastStore } from '../components/Toast'

export function useReadLater() {
  const qc = useQueryClient()
  const addToast = useToastStore((s) => s.addToast)

  return useMutation({
    mutationFn: async (articleId: number) => {
      const board = await boardsApi.getOrCreateByName('Read Later')
      await boardsApi.addArticle(board.id, articleId)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['boards'] })
      qc.invalidateQueries({ queryKey: ['boardArticles'] })
      addToast('Added to Read Later', 'success')
    },
    onError: () => {
      addToast('Failed to add to Read Later')
    },
  })
}
```

- [ ] **Step 3: Add Read Later button to ReadingPane**

In `src/main/frontend/src/components/ReadingPane.tsx`, add import:

```typescript
import { useReadLater } from '../hooks/useBoards'
```

Inside the component function (after `const [internalBoardOpen, setInternalBoardOpen] = useState(false)`, line 32), add:

```typescript
const readLater = useReadLater()
```

Add the Read Later button to the toolbar (after the Board button, line 84):

```tsx
<button className="toolbar-btn" onClick={() => readLater.mutate(article.id)}>Read Later</button>
```

- [ ] **Step 4: Run frontend tests**

Run: `cd src/main/frontend && npm test -- --run`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/api/boards.ts src/main/frontend/src/hooks/useBoards.ts src/main/frontend/src/components/ReadingPane.tsx
git commit -m "feat: add Read Later button with auto-create board"
```

## Chunk 3: Hide Read Feeds Setting + Icons

### Task 7: Hide Read Feeds — Preferences and Settings Dialog

**Files:**
- Modify: `src/main/frontend/src/stores/preferencesStore.ts`
- Modify: `src/main/frontend/src/components/SettingsDialog.tsx`

- [ ] **Step 1: Add hideReadFeeds to preferences store**

In `src/main/frontend/src/stores/preferencesStore.ts`, add to the interface:

```typescript
hideReadFeeds: boolean
setHideReadFeeds: (hide: boolean) => void
```

Add to the store implementation (after `theme: 'midnight',`):

```typescript
hideReadFeeds: false,
```

And after the `setTheme` setter:

```typescript
setHideReadFeeds: (hide) => set({ hideReadFeeds: hide }),
```

- [ ] **Step 2: Add checkbox to SettingsDialog**

In `src/main/frontend/src/components/SettingsDialog.tsx`, add a checkbox inside the Appearance section (after the Theme `</label>` closing tag, before the `</div>` that closes the Appearance section):

```tsx
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
            <input
              type="checkbox"
              checked={prefs.hideReadFeeds}
              onChange={(e) => prefs.setHideReadFeeds(e.target.checked)}
            />
            Hide feeds with no unread articles
          </label>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/stores/preferencesStore.ts src/main/frontend/src/components/SettingsDialog.tsx
git commit -m "feat: add hide-read-feeds setting to preferences and Settings dialog"
```

### Task 8: Hide Read Feeds — FeedPanel Filter

**Files:**
- Modify: `src/main/frontend/src/components/FeedPanel.tsx`

- [ ] **Step 1: Add filter logic to FeedPanel**

In `src/main/frontend/src/components/FeedPanel.tsx`, add import:

```typescript
import { usePreferences } from '../stores/preferencesStore'
```

Inside the component function (after `const feedUnread = ...`, line 33), add:

```typescript
const selectedFeedId = useUIStore((s) => s.selectedFeedId)
const hideReadFeeds = usePreferences((s) => s.hideReadFeeds)

const visibleFeeds = (feedList: Feed[]) =>
  hideReadFeeds ? feedList.filter((f) => feedUnread(f.id) > 0 || f.id === selectedFeedId) : feedList
```

Note: `selectedFeedId` is already declared in the component (line 20). Remove the duplicate and reuse the existing one. The `visibleFeeds` function should reference that existing variable.

Replace `feedsByFolder(folder.id).map(...)` (line 103) with `visibleFeeds(feedsByFolder(folder.id)).map(...)`.

Replace `uncategorized.map(...)` (line 119) with `visibleFeeds(uncategorized).map(...)`.

Also update the folder unread count (line 99) to use `visibleFeeds` so the count matches what's displayed:
```tsx
{visibleFeeds(feedsByFolder(folder.id)).reduce((sum, f) => sum + feedUnread(f.id), 0) || ''}
```

- [ ] **Step 2: Run frontend tests**

Run: `cd src/main/frontend && npm test -- --run`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/components/FeedPanel.tsx
git commit -m "feat: filter read feeds from FeedPanel when setting enabled"
```

### Task 9: Reading Pane Icons

**Files:**
- Modify: `src/main/frontend/src/components/ReadingPane.tsx`

- [ ] **Step 1: Update toolbar button labels with unicode icons**

In `src/main/frontend/src/components/ReadingPane.tsx`, replace the toolbar section (lines 80-90):

```tsx
      <div className="reading-toolbar">
        <button className="toolbar-btn" onClick={handleStar}>
          {article.starred ? '★ Unstar' : '★ Star'}
        </button>
        <button className="toolbar-btn" onClick={() => setInternalBoardOpen(true)}>📋 Board</button>
        <button className="toolbar-btn" onClick={() => readLater.mutate(article.id)}>🔖 Read Later</button>
        <button className="toolbar-btn" onClick={handleRaindrop}>💧 Raindrop</button>
        <button className="toolbar-btn" onClick={handleCopyLink}>🔗 Copy Link</button>
        <button className="toolbar-btn" onClick={handleOpenOriginal} style={{ marginLeft: 'auto' }}>
          ↗ Open Original
        </button>
      </div>
```

- [ ] **Step 2: Run frontend tests**

Run: `cd src/main/frontend && npm test -- --run`
Expected: All tests PASS.

- [ ] **Step 3: Run frontend build**

Run: `cd src/main/frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/components/ReadingPane.tsx
git commit -m "feat: add unicode icons to reading pane toolbar buttons"
```

### Task 10: Full Build Verification

- [ ] **Step 1: Run full Gradle build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke test (optional)**

Run: `./gradlew bootTestRun`

1. Open Settings — verify Theme dropdown and "Hide feeds" checkbox
2. Toggle "Hide feeds" — verify feeds with 0 unread disappear
3. Click an article — verify toolbar icons render (★, 📋, 🔖, 💧, 🔗, ↗)
4. Click "🔖 Read Later" — verify toast says "Added to Read Later", verify "Read Later" board appears in Boards view
5. Click "🔖 Read Later" again on same article — should succeed silently (idempotent)
6. Navigate to Boards → Read Later — verify article is listed

- [ ] **Step 3: Final commit if any fixups were needed**

```bash
git add -A
git commit -m "fix: address issues found during smoke testing"
```
