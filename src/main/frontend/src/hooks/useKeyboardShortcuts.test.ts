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

import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { createElement } from 'react'
import { useKeyboardShortcuts } from './useKeyboardShortcuts'
import { useUIStore } from '../stores/uiStore'
import { articlesApi } from '../api/articles'
import { feedsApi } from '../api/feeds'
import type { Article } from '../types'

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

describe('useKeyboardShortcuts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(feedsApi.getAll).mockResolvedValue([])
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
})
