import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('../api/articles', () => ({
  articlesApi: {
    priority: vi.fn(),
    updateState: vi.fn(),
    getById: vi.fn(),
    list: vi.fn(),
    markRead: vi.fn(),
    counts: vi.fn(),
    saveToRaindrop: vi.fn(),
    getExtractedContent: vi.fn(),
  },
}))

import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, focusManager, onlineManager } from '@tanstack/react-query'
import { createElement } from 'react'
import { usePriorityArticles, patchPriorityArticle, PRIORITY_KEY } from './usePriorityArticles'
import { useUpdateArticleState } from './useArticles'
import { articlesApi } from '../api/articles'
import type { Article, PriorityPage } from '../types'

function article(id: number, overrides: Partial<Article> = {}): Article {
  return {
    id,
    feedId: 1,
    guid: `g-${id}`,
    title: `Article ${id}`,
    url: `https://example.com/${id}`,
    author: null,
    content: null,
    summary: null,
    imageUrl: null,
    publishedAt: '2026-09-23T00:00:00Z',
    fetchedAt: '2026-09-23T00:00:00Z',
    read: false,
    starred: false,
    interestScore: 82,
    ...overrides,
  }
}

function page(items: Article[], nextCursor: string | null = null): PriorityPage {
  return { items, nextCursor }
}

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children),
  }
}

/** Both hooks in one render so they share the QueryClient. */
function renderPriority(wrapper: ReturnType<typeof createWrapper>['wrapper']) {
  return renderHook(
    () => ({ priority: usePriorityArticles(), update: useUpdateArticleState() }),
    { wrapper },
  )
}

function rowState(rows: Article[]) {
  return rows.map((a) => ({ id: a.id, read: a.read, starred: a.starred }))
}

describe('usePriorityArticles cache policy', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(articlesApi.updateState).mockImplementation(async (id, state) =>
      article(id, { ...state, interestScore: 5 }),
    )
  })

  afterEach(() => {
    focusManager.setFocused(undefined)
    onlineManager.setOnline(true)
  })

  it('markReadPatchesTheRowInPlace', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2), article(3)]))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    await act(async () => {
      await result.current.update.mutateAsync({ id: 2, state: { read: true } })
    })

    await waitFor(() =>
      expect(rowState(result.current.priority.rows)).toEqual([
        { id: 1, read: false, starred: false },
        { id: 2, read: true, starred: false },
        { id: 3, read: false, starred: false },
      ]),
    )
    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('starPatchesTheRowInPlace', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2), article(3)]))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    await act(async () => {
      await result.current.update.mutateAsync({ id: 3, state: { starred: true } })
    })

    await waitFor(() =>
      expect(rowState(result.current.priority.rows)).toEqual([
        { id: 1, read: false, starred: false },
        { id: 2, read: false, starred: false },
        { id: 3, read: false, starred: true },
      ]),
    )
    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('twoMutationsOnTheSameRowBothApply', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2), article(3)]))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    await act(async () => {
      await result.current.update.mutateAsync({ id: 2, state: { read: true } })
    })
    await act(async () => {
      await result.current.update.mutateAsync({ id: 2, state: { starred: true } })
    })

    await waitFor(() =>
      expect(result.current.priority.rows[1]).toMatchObject({ id: 2, read: true, starred: true }),
    )
    expect(result.current.priority.rows.map((a) => a.id)).toEqual([1, 2, 3])
    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('patchKeepsTheListedScore', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2, { interestScore: 82 })]))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(2))

    await act(async () => {
      await result.current.update.mutateAsync({ id: 2, state: { read: true } })
    })

    await waitFor(() => expect(result.current.priority.rows[1].read).toBe(true))
    expect(result.current.priority.rows[1].interestScore).toBe(82)
  })

  it('articlesInvalidationDoesNotRefetchPriority', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2)]))
    const { qc, wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(2))

    await act(async () => {
      await qc.invalidateQueries({ queryKey: ['articles'] })
    })

    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('focusAndReconnectDoNotRefetch', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2)]))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(2))

    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
      onlineManager.setOnline(false)
      onlineManager.setOnline(true)
    })
    await new Promise((r) => setTimeout(r, 20))

    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
    expect(result.current.priority.rows.map((a) => a.id)).toEqual([1, 2])
  })

  it('remountDoesNotRefetch', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2)]))
    const { wrapper } = createWrapper()
    const first = renderPriority(wrapper)
    await waitFor(() => expect(first.result.current.priority.rows).toHaveLength(2))
    first.unmount()

    const second = renderPriority(wrapper)
    expect(second.result.current.priority.rows.map((a) => a.id)).toEqual([1, 2])
    await new Promise((r) => setTimeout(r, 20))

    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('patchWithoutPriorityCacheCreatesNothing', () => {
    const { qc } = createWrapper()

    patchPriorityArticle(qc, 2, { read: true })

    expect(qc.getQueryCache().findAll({ queryKey: PRIORITY_KEY })).toHaveLength(0)
  })

  it('cursorRowReadBeforeLoadMoreContinues', async () => {
    vi.mocked(articlesApi.priority).mockImplementation(async (_limit, before) =>
      before === 'c3' ? page([article(3), article(4)]) : page([article(1), article(2), article(3)], 'c3'),
    )
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    await act(async () => {
      await result.current.update.mutateAsync({ id: 3, state: { read: true } })
    })
    await waitFor(() => expect(result.current.priority.rows[2].read).toBe(true))

    await act(async () => {
      await result.current.priority.fetchNextPage()
    })

    expect(articlesApi.priority).toHaveBeenLastCalledWith(50, 'c3')
    await waitFor(() => expect(result.current.priority.rows.map((a) => a.id)).toEqual([1, 2, 3, 4]))
    expect(result.current.priority.rows[2].read).toBe(true)
  })

  it('fetchNextNewIdSelectsTheFirstUnseenRow', async () => {
    vi.mocked(articlesApi.priority).mockImplementation(async (_limit, before) =>
      before === 'c3' ? page([article(3), article(4)]) : page([article(1), article(2), article(3)], 'c3'),
    )
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    let next: number | undefined
    await act(async () => {
      next = await result.current.priority.fetchNextNewId()
    })

    expect(next).toBe(4)
    expect(articlesApi.priority).toHaveBeenLastCalledWith(50, 'c3')
  })

  it('fetchNextNewIdWithoutANextPageReturnsUndefined', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2)], null))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(2))

    let next: number | undefined = -1
    await act(async () => {
      next = await result.current.priority.fetchNextNewId()
    })

    expect(next).toBeUndefined()
    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('leavingAndReenteringFetchesPageOneFresh', async () => {
    let firstLoad = true
    vi.mocked(articlesApi.priority).mockImplementation(async (_limit, before) => {
      if (before === 'c2') return page([article(3)])
      if (firstLoad) {
        firstLoad = false
        return page([article(1), article(2)], 'c2')
      }
      return page([article(9), article(8)])
    })
    const { qc, wrapper } = createWrapper()
    const { result, rerender } = renderHook(
      ({ enabled }: { enabled: boolean }) => usePriorityArticles(enabled),
      { wrapper, initialProps: { enabled: true } },
    )
    await waitFor(() => expect(result.current.rows).toHaveLength(2))
    await act(async () => {
      await result.current.fetchNextPage()
    })
    await waitFor(() => expect(result.current.rows.map((a) => a.id)).toEqual([1, 2, 3]))

    rerender({ enabled: false })
    act(() => {
      qc.removeQueries({ queryKey: PRIORITY_KEY })
    })
    rerender({ enabled: true })

    await waitFor(() => expect(result.current.rows.map((a) => a.id)).toEqual([9, 8]))
    expect(articlesApi.priority).toHaveBeenLastCalledWith(50, undefined)
    expect(articlesApi.priority).toHaveBeenCalledTimes(3)
  })
})
