import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/articles', () => ({
  articlesApi: {
    getById: vi.fn(),
    list: vi.fn(),
    updateState: vi.fn(),
    markRead: vi.fn(),
    counts: vi.fn(),
    saveToRaindrop: vi.fn(),
  },
}))
vi.mock('../api/feeds', () => ({
  feedsApi: {
    getAll: vi.fn(),
    poll: vi.fn(),
  },
}))
vi.mock('../api/folders', () => ({
  foldersApi: {
    getAll: vi.fn(),
  },
}))

import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { createElement } from 'react'
import { useKeyboardShortcuts } from './useKeyboardShortcuts'
import { useUIStore } from '../stores/uiStore'
import { articlesApi } from '../api/articles'
import { feedsApi } from '../api/feeds'
import { foldersApi } from '../api/folders'
import type { Article, Feed } from '../types'

const feed = (id: number, overrides: Partial<Feed> = {}): Feed => ({
  id,
  url: `https://example.com/feed/${id}`,
  title: `Feed ${id}`,
  description: null,
  siteUrl: null,
  feedType: 'RSS',
  pollIntervalMinutes: 60,
  lastPolledAt: null,
  lastSuccessfulPollAt: null,
  errorCount: 0,
  lastError: null,
  etag: null,
  lastModifiedHeader: null,
  createdAt: '2026-01-01T00:00:00Z',
  folderId: null,
  ...overrides,
})

const article = (id: number, overrides: Partial<Article> = {}): Article => ({
  id,
  feedId: 1,
  guid: `guid-${id}`,
  title: `Article ${id}`,
  url: `https://example.com/${id}`,
  author: null,
  content: null,
  summary: null,
  imageUrl: null,
  publishedAt: '2026-01-01T00:00:00Z',
  fetchedAt: '2026-01-01T00:00:00Z',
  read: false,
  starred: false,
  ...overrides,
})

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, createElement(MemoryRouter, null, children)),
  }
}

function press(key: string) {
  act(() => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
  })
}

// Seed the feed list + unread counts so the hook's unread-aware navigation has
// data to work with synchronously (no folders, plain order).
function seedFeeds(qc: QueryClient, feeds: Feed[], counts: Record<string, number>) {
  qc.setQueryData(['feeds'], feeds)
  qc.setQueryData(['folders'], [])
  qc.setQueryData(['unreadCounts'], counts)
  vi.mocked(feedsApi.getAll).mockResolvedValue(feeds)
  vi.mocked(articlesApi.counts).mockResolvedValue(counts)
}

describe('useKeyboardShortcuts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(feedsApi.getAll).mockResolvedValue([])
    vi.mocked(foldersApi.getAll).mockResolvedValue([])
    // Reset the selection/focus slice of the UI store between tests.
    useUIStore.setState({ selectedArticleId: null, selectedFeedId: null, keyboardFocus: 'articles' })
  })

  it("'o' opens the selected article's URL even when it is not in the passed list", async () => {
    // Reproduces the Starred/Folder divergence: the selected article exists
    // (and is loaded by id, like ReadingPane does) but is absent from the
    // `articles` array the hook receives for j/k navigation.
    const selected = article(7, { url: 'https://example.com/seven' })
    const { qc, wrapper } = createWrapper()
    qc.setQueryData(['article', 7], selected)
    vi.mocked(articlesApi.getById).mockResolvedValue(selected)
    useUIStore.setState({ selectedArticleId: 7 })

    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    renderHook(() => useKeyboardShortcuts([]), { wrapper })

    // Wait for useArticle(7) to resolve into the action target.
    await waitFor(() => expect(qc.getQueryData(['article', 7])).toBeTruthy())

    press('o')
    expect(openSpy).toHaveBeenCalledWith('https://example.com/seven', '_blank', 'noopener')
    openSpy.mockRestore()
  })

  it("Enter focuses the reading pane when an article is selected", () => {
    const { wrapper } = createWrapper()
    useUIStore.setState({ selectedArticleId: 7, keyboardFocus: 'articles' })

    renderHook(() => useKeyboardShortcuts([article(7)]), { wrapper })

    press('Enter')
    expect(useUIStore.getState().keyboardFocus).toBe('reading')
  })

  it("Enter selects the first article and focuses the reading pane when nothing is selected", () => {
    const { wrapper } = createWrapper()
    useUIStore.setState({ selectedArticleId: null, keyboardFocus: 'articles' })

    renderHook(() => useKeyboardShortcuts([article(3), article(4)]), { wrapper })

    press('Enter')
    expect(useUIStore.getState().selectedArticleId).toBe(3)
    expect(useUIStore.getState().keyboardFocus).toBe('reading')
  })

  it("Shift+A marks the current feed read and advances to the next unread feed", async () => {
    // Parity with the mouse "Mark all read" button: after clearing the current
    // feed it should jump to the next feed that still has unread articles.
    const { qc, wrapper } = createWrapper()
    const feeds = [feed(1), feed(2), feed(3)]
    qc.setQueryData(['feeds'], feeds)
    qc.setQueryData(['folders'], [])
    qc.setQueryData(['unreadCounts'], { '1': 5, '2': 0, '3': 4 })
    vi.mocked(feedsApi.getAll).mockResolvedValue(feeds)
    vi.mocked(articlesApi.counts).mockResolvedValue({ '1': 5, '2': 0, '3': 4 })
    vi.mocked(articlesApi.markRead).mockResolvedValue(undefined as never)
    useUIStore.setState({ selectedFeedId: 1, selectedArticleId: 99 })

    renderHook(() => useKeyboardShortcuts([]), { wrapper })

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'A', shiftKey: true, bubbles: true }))
    })

    await waitFor(() => expect(articlesApi.markRead).toHaveBeenCalledWith(undefined, 1, undefined))
    // Feed 2 has no unread, so it should skip to feed 3.
    await waitFor(() => expect(useUIStore.getState().selectedFeedId).toBe(3))
    expect(useUIStore.getState().selectedArticleId).toBeNull()
  })

  it("'n' skips fully-read feeds and lands on the next feed with unread articles", () => {
    const { qc, wrapper } = createWrapper()
    seedFeeds(qc, [feed(1), feed(2), feed(3), feed(4)], { '1': 3, '2': 0, '3': 0, '4': 7 })
    useUIStore.setState({ selectedFeedId: 1 })

    renderHook(() => useKeyboardShortcuts([]), { wrapper })

    press('n')
    // Feeds 2 and 3 have no unread, so it skips to feed 4.
    expect(useUIStore.getState().selectedFeedId).toBe(4)
  })

  it("'n' wraps around to the first unread feed when past the last one", () => {
    const { qc, wrapper } = createWrapper()
    seedFeeds(qc, [feed(1), feed(2), feed(3)], { '1': 2, '2': 0, '3': 1 })
    useUIStore.setState({ selectedFeedId: 3 })

    renderHook(() => useKeyboardShortcuts([]), { wrapper })

    press('n')
    // Past the last feed: wrap to the first unread one (feed 1).
    expect(useUIStore.getState().selectedFeedId).toBe(1)
  })

  it("'p' lands on the previous feed with unread articles", () => {
    const { qc, wrapper } = createWrapper()
    seedFeeds(qc, [feed(1), feed(2), feed(3), feed(4)], { '1': 0, '2': 5, '3': 0, '4': 0 })
    useUIStore.setState({ selectedFeedId: 4 })

    renderHook(() => useKeyboardShortcuts([]), { wrapper })

    press('p')
    // Feed 3 has no unread, so it skips back to feed 2.
    expect(useUIStore.getState().selectedFeedId).toBe(2)
  })

  it("'n' stays put when no other feed has unread articles", () => {
    const { qc, wrapper } = createWrapper()
    seedFeeds(qc, [feed(1), feed(2), feed(3)], { '1': 4, '2': 0, '3': 0 })
    useUIStore.setState({ selectedFeedId: 1 })

    renderHook(() => useKeyboardShortcuts([]), { wrapper })

    press('n')
    // Current feed is the only one with unread → no-op.
    expect(useUIStore.getState().selectedFeedId).toBe(1)
  })
})
