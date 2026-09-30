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
import { MemoryRouter } from 'react-router-dom'
import { createElement } from 'react'
import { usePriorityArticles, patchPriorityArticle, PRIORITY_KEY } from './usePriorityArticles'
import { useUpdateArticleState } from './useArticles'
import { articlesApi } from '../api/articles'
import { usePriorityStore } from '../stores/priorityStore'
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
      createElement(
        QueryClientProvider,
        { client: qc },
        createElement(MemoryRouter, { initialEntries: ['/priority'] }, children),
      ),
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
    // The star reaction's by-id refetch answers the listed score, so no pre-existing case lights the hint.
    vi.mocked(articlesApi.getById).mockImplementation(async (id) => article(id))
    usePriorityStore.setState({ rankingChanged: false, baselineUnscored: null })
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

  it('starOnPriorityPatchesTheScoreAndLightsTheHint', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2), article(3)]))
    vi.mocked(articlesApi.getById).mockImplementation(async (id) =>
      article(id, id === 3 ? { starred: true, interestScore: 90 } : {}),
    )
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    await act(async () => {
      await result.current.update.mutateAsync({ id: 3, state: { starred: true } })
    })

    await waitFor(() => expect(result.current.priority.rows[2].interestScore).toBe(90))
    expect(result.current.priority.rows[2]).toMatchObject({ id: 3, starred: true, interestScore: 90 })
    expect(result.current.priority.rows.map((a) => a.id)).toEqual([1, 2, 3])
    expect(usePriorityStore.getState().rankingChanged).toBe(true)
    expect(articlesApi.getById).toHaveBeenCalledWith(3)
    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
  })

  it('unstarAndReadDoNotReact', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue(page([article(1), article(2), article(3)]))
    vi.mocked(articlesApi.getById).mockImplementation(async (id) => article(id, { interestScore: 90 }))
    const { wrapper } = createWrapper()
    const { result } = renderPriority(wrapper)
    await waitFor(() => expect(result.current.priority.rows).toHaveLength(3))

    for (const state of [{ starred: false }, { read: true }, { read: false }]) {
      await act(async () => {
        await result.current.update.mutateAsync({ id: 3, state })
      })
    }
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0))
    })

    // Nothing renders the by-id article here, so any getById call would be the reaction's.
    expect(articlesApi.getById).not.toHaveBeenCalled()
    expect(usePriorityStore.getState().rankingChanged).toBe(false)
    expect(result.current.priority.rows.map((a) => a.interestScore)).toEqual([82, 82, 82])
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

  it('patchCopiesInterestScoreIncludingNull', () => {
    const { qc } = createWrapper()
    qc.setQueryData(PRIORITY_KEY, { pages: [page([article(1), article(2), article(3)])], pageParams: [undefined] })
    const rows = () =>
      qc.getQueryData<{ pages: PriorityPage[] }>(PRIORITY_KEY)!.pages[0].items.map((a) => ({
        id: a.id,
        score: a.interestScore,
        read: a.read,
        starred: a.starred,
      }))

    patchPriorityArticle(qc, 2, { interestScore: 91 })
    expect(rows()[1]).toEqual({ id: 2, score: 91, read: false, starred: false })

    patchPriorityArticle(qc, 2, { interestScore: null })
    expect(rows()[1].score).toBeNull()

    patchPriorityArticle(qc, 2, {})
    expect(rows()[1].score).toBeNull()

    patchPriorityArticle(qc, 2, { read: true, starred: true })
    expect(rows()).toEqual([
      { id: 1, score: 82, read: false, starred: false },
      { id: 2, score: null, read: true, starred: true },
      { id: 3, score: 82, read: false, starred: false },
    ])
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
