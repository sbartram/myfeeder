# Mark Articles Older Than X Days as Read — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to mark all unread articles in the current feed older than a user-specified number of days as read, via a split-button dropdown and dialog in the ArticleList toolbar.

**Architecture:** Extend the existing `POST /api/articles/mark-read` endpoint with an optional `olderThanDays` field. Add a new repository query that filters by `feed_id`, `published_at < cutoff`, and `read = false`. On the frontend, convert the "Mark all read" button into a split button with a dropdown that opens a dialog for day selection (presets + custom input).

**Tech Stack:** Spring Boot 4 / Spring Data JDBC (backend), React 19 / TypeScript / TanStack Query (frontend), Vitest + React Testing Library (frontend tests), JUnit 5 + Mockito + Testcontainers (backend tests)

**Spec:** `docs/superpowers/specs/2026-03-21-mark-older-articles-read-design.md`

---

### Task 1: Backend — Repository query

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java:28-29` (after `markAllReadByFeedId`)
- Test: `src/test/java/org/bartram/myfeeder/repository/ArticleRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test**

Add to `ArticleRepositoryTest.java` after the existing `shouldBulkMarkReadByIds` test:

```java
@Test
void shouldMarkReadByFeedIdOlderThan() {
    var old = createArticle("old-guid", "Old Article");
    old.setPublishedAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));
    old = articleRepository.save(old);

    var recent = createArticle("recent-guid", "Recent Article");
    recent.setPublishedAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
    recent = articleRepository.save(recent);

    Instant cutoff = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
    articleRepository.markReadByFeedIdOlderThan(savedFeed.getId(), cutoff);

    assertThat(articleRepository.findById(old.getId()).get().isRead()).isTrue();
    assertThat(articleRepository.findById(recent.getId()).get().isRead()).isFalse();
}
```

Note: The `createArticle` helper doesn't set `publishedAt`. Articles need it set explicitly for this test. If `publishedAt` defaults to null, the query's `published_at < :cutoff` condition will skip nulls (correct behavior — null means unknown date, don't touch it).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.bartram.myfeeder.repository.ArticleRepositoryTest.shouldMarkReadByFeedIdOlderThan"`
Expected: FAIL — `markReadByFeedIdOlderThan` method does not exist.

- [ ] **Step 3: Implement the repository method**

Add to `ArticleRepository.java` after line 29 (`markAllReadByFeedId`):

```java
@Modifying
@Query("UPDATE article SET read = true WHERE feed_id = :feedId AND published_at < :cutoff AND read = false")
void markReadByFeedIdOlderThan(Long feedId, Instant cutoff);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.bartram.myfeeder.repository.ArticleRepositoryTest.shouldMarkReadByFeedIdOlderThan"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java src/test/java/org/bartram/myfeeder/repository/ArticleRepositoryTest.java
git commit -m "feat: add markReadByFeedIdOlderThan repository query"
```

---

### Task 2: Backend — Service and DTO

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/controller/MarkReadRequest.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/ArticleService.java:53-61` (`markRead` method)
- Test: `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`

- [ ] **Step 1: Write the failing service test**

Add to `ArticleServiceTest.java`:

```java
@Test
void shouldMarkReadByFeedIdOlderThanDays() {
    articleService.markRead(null, 5L, 7);
    verify(articleRepository).markReadByFeedIdOlderThan(eq(5L), any(Instant.class));
}

@Test
void shouldRejectOlderThanDaysWithoutFeedId() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> articleService.markRead(null, null, 7));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.ArticleServiceTest.shouldMarkReadByFeedIdOlderThanDays" --tests "org.bartram.myfeeder.service.ArticleServiceTest.shouldRejectOlderThanDaysWithoutFeedId"`
Expected: FAIL — `markRead` does not accept 3 arguments.

- [ ] **Step 3: Add `olderThanDays` to MarkReadRequest**

Update `MarkReadRequest.java` to:

```java
package org.bartram.myfeeder.controller;

import lombok.Data;
import java.util.List;

@Data
public class MarkReadRequest {
    private List<Long> articleIds;
    private Long feedId;
    private Integer olderThanDays;
}
```

- [ ] **Step 4: Update ArticleService.markRead()**

Replace the `markRead` method in `ArticleService.java` (lines 53-61) with:

```java
public void markRead(List<Long> articleIds, Long feedId) {
    markRead(articleIds, feedId, null);
}

public void markRead(List<Long> articleIds, Long feedId, Integer olderThanDays) {
    if (articleIds != null && !articleIds.isEmpty()) {
        articleRepository.markReadByIds(articleIds);
    } else if (feedId != null && olderThanDays != null) {
        if (olderThanDays < 1) {
            throw new IllegalArgumentException("olderThanDays must be >= 1");
        }
        Instant cutoff = Instant.now().minus(olderThanDays, java.time.temporal.ChronoUnit.DAYS);
        articleRepository.markReadByFeedIdOlderThan(feedId, cutoff);
    } else if (feedId != null) {
        articleRepository.markAllReadByFeedId(feedId);
    } else {
        throw new IllegalArgumentException("Either articleIds or feedId must be provided");
    }
}
```

Add import at top of file: `import java.time.Instant;`

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.ArticleServiceTest"`
Expected: ALL PASS (existing tests use the 2-arg overload, new tests use 3-arg)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/MarkReadRequest.java src/main/java/org/bartram/myfeeder/service/ArticleService.java src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
git commit -m "feat: add olderThanDays support to markRead service and DTO"
```

---

### Task 3: Backend — Controller wiring

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/controller/ArticleController.java:56-60` (`markRead` endpoint)
- Test: `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Add to `ArticleControllerTest.java`:

```java
@Test
void shouldMarkReadOlderThanDays() throws Exception {
    mockMvc.perform(post("/api/articles/mark-read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"feedId\":5,\"olderThanDays\":7}"))
            .andExpect(status().isNoContent());

    verify(articleService).markRead(null, 5L, 7);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.ArticleControllerTest.shouldMarkReadOlderThanDays"`
Expected: FAIL — controller calls 2-arg `markRead`, not 3-arg.

- [ ] **Step 3: Update the controller endpoint**

Replace the `markRead` method in `ArticleController.java` (lines 56-60):

```java
@PostMapping("/mark-read")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void markRead(@RequestBody MarkReadRequest request) {
    articleService.markRead(request.getArticleIds(), request.getFeedId(), request.getOlderThanDays());
}
```

- [ ] **Step 4: Update existing `shouldBulkMarkRead` test**

The existing test at `ArticleControllerTest.java:86` verifies the 2-arg call. The controller now calls the 3-arg method, so update the verify:

```java
verify(articleService).markRead(List.of(1L, 2L, 3L), null, null);
```

- [ ] **Step 5: Run all controller tests**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.ArticleControllerTest"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/ArticleController.java src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
git commit -m "feat: wire olderThanDays through ArticleController mark-read endpoint"
```

---

### Task 4: Frontend — API and hook

**Files:**
- Modify: `src/main/frontend/src/api/articles.ts:17-18` (`markRead` function)
- Modify: `src/main/frontend/src/hooks/useArticles.ts:35-44` (`useMarkRead` hook)

- [ ] **Step 1: Update the API function**

Replace line 17-18 of `articles.ts`:

```typescript
markRead: (articleIds?: number[], feedId?: number, olderThanDays?: number) =>
  apiPost<void>('/articles/mark-read', { articleIds, feedId, olderThanDays }),
```

- [ ] **Step 2: Update the useMarkRead hook**

Replace the `mutationFn` in `useMarkRead` (line 38 of `useArticles.ts`):

```typescript
mutationFn: (params: { articleIds?: number[]; feedId?: number; olderThanDays?: number }) =>
  articlesApi.markRead(params.articleIds, params.feedId, params.olderThanDays),
```

- [ ] **Step 3: Verify no TypeScript errors**

Run: `cd src/main/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/api/articles.ts src/main/frontend/src/hooks/useArticles.ts
git commit -m "feat: add olderThanDays param to frontend markRead API and hook"
```

---

### Task 5: Frontend — MarkOlderReadDialog component

**Files:**
- Create: `src/main/frontend/src/components/MarkOlderReadDialog.tsx`
- Create: `src/main/frontend/src/components/MarkOlderReadDialog.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `src/main/frontend/src/components/MarkOlderReadDialog.test.tsx`:

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { MarkOlderReadDialog } from './MarkOlderReadDialog'

describe('MarkOlderReadDialog', () => {
  const defaultProps = {
    open: true,
    feedName: 'Test Feed',
    onConfirm: vi.fn(),
    onClose: vi.fn(),
  }

  it('renders preset buttons', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    expect(screen.getByText('7 days')).toBeInTheDocument()
    expect(screen.getByText('30 days')).toBeInTheDocument()
    expect(screen.getByText('90 days')).toBeInTheDocument()
  })

  it('calls onConfirm with preset value', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    fireEvent.click(screen.getByText('30 days'))
    fireEvent.click(screen.getByText('Mark as read'))
    expect(defaultProps.onConfirm).toHaveBeenCalledWith(30)
  })

  it('accepts custom input', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    const input = screen.getByPlaceholderText('Custom')
    fireEvent.change(input, { target: { value: '14' } })
    fireEvent.click(screen.getByText('Mark as read'))
    expect(defaultProps.onConfirm).toHaveBeenCalledWith(14)
  })

  it('disables submit when no value', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    expect(screen.getByText('Mark as read')).toBeDisabled()
  })

  it('does not render when closed', () => {
    render(<MarkOlderReadDialog {...defaultProps} open={false} />)
    expect(screen.queryByText('Mark as read')).not.toBeInTheDocument()
  })

  it('shows feed name', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    expect(screen.getByText(/Test Feed/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd src/main/frontend && npx vitest run src/components/MarkOlderReadDialog.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the dialog component**

Create `src/main/frontend/src/components/MarkOlderReadDialog.tsx`:

```tsx
import { useState } from 'react'

interface MarkOlderReadDialogProps {
  open: boolean
  feedName: string
  onConfirm: (days: number) => void
  onClose: () => void
}

const PRESETS = [7, 30, 90]

export function MarkOlderReadDialog({ open, feedName, onConfirm, onClose }: MarkOlderReadDialogProps) {
  const [days, setDays] = useState<number | ''>('')

  if (!open) return null

  const handlePreset = (value: number) => {
    setDays(value)
  }

  const handleCustomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setDays(val === '' ? '' : Math.max(1, parseInt(val, 10) || 1))
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (days !== '' && days >= 1) {
      onConfirm(days)
      setDays('')
    }
  }

  const handleClose = () => {
    setDays('')
    onClose()
  }

  return (
    <div className="dialog-overlay" onClick={handleClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Mark older articles as read</h2>
        <p className="dialog-subtitle">
          Mark unread articles older than X days in <strong>{feedName}</strong>
        </p>
        <form onSubmit={handleSubmit}>
          <div className="preset-buttons">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                className={`preset-btn ${days === p ? 'active' : ''}`}
                onClick={() => handlePreset(p)}
              >
                {p} days
              </button>
            ))}
          </div>
          <input
            className="dialog-input"
            type="number"
            min="1"
            placeholder="Custom"
            value={days}
            onChange={handleCustomChange}
          />
          <div className="dialog-actions">
            <button type="button" className="btn-secondary" onClick={handleClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={days === '' || days < 1}>
              Mark as read
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd src/main/frontend && npx vitest run src/components/MarkOlderReadDialog.test.tsx`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/components/MarkOlderReadDialog.tsx src/main/frontend/src/components/MarkOlderReadDialog.test.tsx
git commit -m "feat: add MarkOlderReadDialog component with presets and custom input"
```

---

### Task 6: Frontend — Split button in ArticleList + CSS

**Files:**
- Modify: `src/main/frontend/src/components/ArticleList.tsx:83-85` (toolbar button area)
- Modify: `src/main/frontend/src/App.css` (add split-button and preset-button styles)
- Modify: `src/main/frontend/src/hooks/useFeeds.ts` (check if feed name is accessible — may need to pass feed name as prop)

This task has a subtlety: `ArticleList` receives `filters` and `title` props but not the feed name. The `title` prop is currently just "Feed" (hardcoded in `App.tsx` line 33). The feed name is needed for the dialog's confirmation text. Two approaches:

**Option A (simpler):** Pass a `feedName` prop to `ArticleList`. Update `FeedArticles` in `App.tsx` to look up the feed name from `useFeeds()` and pass it.

**Option B:** Look up the feed name inside `ArticleList` using `useFeeds()`. This couples ArticleList to the feed data.

**Use Option A** — it keeps ArticleList a dumb display component.

- [ ] **Step 1: Update ArticleList to accept feedName and add split button + dialog**

Modify `ArticleList.tsx`. The changes:

1. Add `feedName?: string` to the props interface
2. Add state for the dropdown menu and dialog
3. Replace the "Mark all read" button with a split button
4. Import and render the `MarkOlderReadDialog`

Updated component (key sections — modify in place, don't rewrite the whole file):

**Props interface** (line 7-10):
```tsx
interface ArticleListProps {
  filters: ArticleFilters
  title: string
  feedName?: string
}
```

**Component signature** (line 12):
```tsx
export function ArticleList({ filters, title, feedName }: ArticleListProps) {
```

**Add state** (after line 18, after `setSearchQuery`):
```tsx
const [showOlderDialog, setShowOlderDialog] = useState(false)
const [showDropdown, setShowDropdown] = useState(false)
```

Update the import on line 1 to include `useState`:
```tsx
import { useMemo, useState } from 'react'
```

**Add handler** (after `handleMarkAllRead`, around line 46):
```tsx
const handleMarkOlderRead = (days: number) => {
  if (filters.feedId) {
    markRead.mutate({ feedId: filters.feedId, olderThanDays: days })
  }
  setShowOlderDialog(false)
}
```

**Replace the toolbar-actions div** (lines 83-85) with:
```tsx
<div className="toolbar-actions">
  <div className="split-btn-group">
    <button className="toolbar-btn" onClick={handleMarkAllRead}>Mark all read</button>
    {filters.feedId && (
      <button
        className="toolbar-btn split-btn-toggle"
        onClick={() => setShowDropdown((v) => !v)}
        aria-label="More mark-read options"
      >
        ▾
      </button>
    )}
    {showDropdown && (
      <div className="split-btn-menu" onClick={() => setShowDropdown(false)}>
        <button className="split-btn-menu-item" onClick={() => setShowOlderDialog(true)}>
          Mark older than…
        </button>
      </div>
    )}
  </div>
</div>
```

**Add dialog render** (just before the closing `</div>` of the component, before line 123):
```tsx
<MarkOlderReadDialog
  open={showOlderDialog}
  feedName={feedName || title}
  onConfirm={handleMarkOlderRead}
  onClose={() => setShowOlderDialog(false)}
/>
```

**Add import** at top of file:
```tsx
import { MarkOlderReadDialog } from './MarkOlderReadDialog'
```

- [ ] **Step 2: Update App.tsx to pass feedName**

In `App.tsx`, update `FeedArticles` (lines 31-34) to look up the feed name:

```tsx
function FeedArticles() {
  const { feedId } = useParams()
  const { data: feeds = [] } = useFeeds()
  const feed = feeds.find((f) => f.id === Number(feedId))
  return <ArticleList filters={{ feedId: Number(feedId) }} title={feed?.title || 'Feed'} feedName={feed?.title} />
}
```

This requires `useFeeds` to already be imported (it is — line 11 of `App.tsx`). The `FeedArticles` component no longer needs the separate `title` prop since the feed title serves both purposes. But keep `title` for the toolbar header display.

- [ ] **Step 3: Add CSS for split button and preset buttons**

Append to `src/main/frontend/src/App.css`:

```css
/* Split button */
.split-btn-group { position: relative; display: flex; align-items: center; }
.split-btn-toggle { padding: 4px 6px; font-size: 11px; border-left: 1px solid var(--border); }
.split-btn-menu {
  position: absolute;
  top: 100%;
  right: 0;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
  min-width: 160px;
  margin-top: 2px;
}
.split-btn-menu-item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  background: none;
  border: none;
  color: var(--text-primary);
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}
.split-btn-menu-item:hover { background: var(--bg-hover); }

/* Preset buttons */
.preset-buttons { display: flex; gap: 8px; margin-bottom: 12px; }
.preset-btn {
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
}
.preset-btn:hover { border-color: var(--accent); color: var(--text-primary); }
.preset-btn.active { border-color: var(--accent); background: var(--accent); color: white; }

.dialog-subtitle { color: var(--text-secondary); font-size: 14px; margin-bottom: 16px; }
```

- [ ] **Step 4: Close dropdown on outside click**

Add an effect in `ArticleList` to close the dropdown when clicking outside:

```tsx
import { useState, useMemo, useEffect, useRef } from 'react'
```

Add ref and effect after state declarations:
```tsx
const dropdownRef = useRef<HTMLDivElement>(null)

useEffect(() => {
  if (!showDropdown) return
  const handleClickOutside = (e: MouseEvent) => {
    if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
      setShowDropdown(false)
    }
  }
  document.addEventListener('mousedown', handleClickOutside)
  return () => document.removeEventListener('mousedown', handleClickOutside)
}, [showDropdown])
```

Apply `ref={dropdownRef}` to the `split-btn-group` div.

- [ ] **Step 5: Write ArticleList split-button test**

Create `src/main/frontend/src/components/ArticleList.test.tsx`:

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { ArticleList } from './ArticleList'

vi.mock('../hooks/useArticles', () => ({
  useArticles: () => ({
    data: {
      pages: [{ articles: [{ id: 1, title: 'Test', read: false, publishedAt: '2026-01-01T00:00:00Z' }] }],
    },
    fetchNextPage: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
  }),
  useMarkRead: () => ({ mutate: vi.fn() }),
}))

vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const state = {
      selectedArticleId: null,
      setSelectedArticle: vi.fn(),
      searchQuery: '',
      setSearchQuery: vi.fn(),
    }
    return selector(state)
  },
}))

describe('ArticleList split button', () => {
  it('shows dropdown toggle when feedId is set', () => {
    render(<ArticleList filters={{ feedId: 1 }} title="Test Feed" feedName="Test Feed" />)
    expect(screen.getByLabelText('More mark-read options')).toBeInTheDocument()
  })

  it('hides dropdown toggle when no feedId', () => {
    render(<ArticleList filters={{}} title="All Articles" />)
    expect(screen.queryByLabelText('More mark-read options')).not.toBeInTheDocument()
  })

  it('opens dropdown menu and shows "Mark older than…" option', () => {
    render(<ArticleList filters={{ feedId: 1 }} title="Test Feed" feedName="Test Feed" />)
    fireEvent.click(screen.getByLabelText('More mark-read options'))
    expect(screen.getByText('Mark older than…')).toBeInTheDocument()
  })
})
```

- [ ] **Step 6: Run ArticleList tests**

Run: `cd src/main/frontend && npx vitest run src/components/ArticleList.test.tsx`
Expected: ALL PASS

- [ ] **Step 7: Verify TypeScript compiles**

Run: `cd src/main/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 8: Commit**

```bash
git add src/main/frontend/src/components/ArticleList.tsx src/main/frontend/src/components/MarkOlderReadDialog.tsx src/main/frontend/src/App.tsx src/main/frontend/src/App.css
git commit -m "feat: add split button and mark-older-read dialog to ArticleList toolbar"
```

---

### Task 7: Full integration test

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: Run all frontend tests**

Run: `cd src/main/frontend && npm test`
Expected: ALL PASS

- [ ] **Step 3: Manual smoke test**

Run: `./gradlew bootTestRun` (backend) and `cd src/main/frontend && npm run dev` (frontend)
1. Navigate to a feed with articles
2. Click the ▾ dropdown next to "Mark all read"
3. Click "Mark older than…"
4. Verify dialog shows with presets (7, 30, 90 days) and custom input
5. Click a preset, verify it populates the input
6. Click "Mark as read", verify articles older than that many days are now read
7. Verify unread counts update in the sidebar

- [ ] **Step 4: Final commit if any fixes needed**

---

### Task 8: Update TODO.md

- [ ] **Step 1: Mark the item as done in TODO.md**

Change line 6 from:
```
- [ ] option to mark articles older than _X_ days as read
```
to:
```
- [X] option to mark articles older than _X_ days as read
```

- [ ] **Step 2: Commit**

```bash
git add TODO.md
git commit -m "docs: mark 'older than X days' TODO as done"
```
