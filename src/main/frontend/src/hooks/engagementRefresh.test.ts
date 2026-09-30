import { describe, it, expect, vi, beforeEach, afterEach, type MockInstance } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider, type InfiniteData, type QueryClient } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { createElement, type ReactNode } from 'react'
import { createQueryClient } from '../queryClient'
import { useToastStore } from '../components/Toast'
import { usePriorityStore } from '../stores/priorityStore'
import { PRIORITY_KEY } from './usePriorityArticles'
import { useSaveToRaindrop } from './useArticles'
import { useAddArticleToBoard, useReadLater, useRemoveArticleFromBoard } from './useBoards'
import type { Article, PriorityPage } from '../types'

let qc: QueryClient
let fetchSpy: MockInstance<typeof fetch>
let invalidateSpy: MockInstance<QueryClient['invalidateQueries']>
let resetSpy: MockInstance<QueryClient['resetQueries']>
let refetchSpy: MockInstance<QueryClient['refetchQueries']>
/** The score GET /api/articles/7 answers. */
let refetchedScore: number | null

function article(id: number, interestScore: number | null): Article {
  return {
    id,
    feedId: 1,
    guid: `g-${id}`,
    title: `Article ${id}`,
    url: `https://example.com/${id}`,
    author: null,
    content: null,
    summary: 'Summary',
    imageUrl: null,
    publishedAt: '2026-09-23T00:00:00Z',
    fetchedAt: '2026-09-23T00:00:00Z',
    read: false,
    starred: false,
    interestScore,
  }
}

function renderAt<T>(hook: () => T, path = '/') {
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(
      QueryClientProvider,
      { client: qc },
      createElement(MemoryRouter, { initialEntries: [path] }, children),
    )
  return renderHook(hook, { wrapper }).result
}

const READ_LATER_BOARD = {
  id: 3,
  name: 'Read Later',
  description: null,
  createdAt: '2026-09-29T00:00:00Z',
}

const json = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

/**
 * Answers each request by "METHOD path": the board routes and GET /api/articles/7 are always
 * routed; anything else unrouted gets the fallback status.
 */
function respond(routes: Record<string, () => Response> = {}, fallback = 204) {
  const all: Record<string, () => Response> = {
    'POST /api/boards/by-name': () => json(READ_LATER_BOARD),
    'POST /api/boards/3/articles': () => new Response(null, { status: 201 }),
    'GET /api/articles/7': () => json(article(7, refetchedScore)),
    ...routes,
  }
  fetchSpy.mockImplementation(async (input, init) => {
    const handler = all[`${init?.method ?? 'GET'} ${String(input)}`]
    return handler ? handler() : new Response(null, { status: fallback })
  })
}

const articleGets = () =>
  fetchSpy.mock.calls.filter(([url, init]) => String(url) === '/api/articles/7' && (init?.method ?? 'GET') === 'GET')
const invalidatedWith = (key: unknown[]) =>
  invalidateSpy.mock.calls.some(([filters]) => JSON.stringify(filters?.queryKey) === JSON.stringify(key))
const toasts = () => useToastStore.getState().toasts.map((t) => t.message)

function seedPriorityRow7(score: number) {
  qc.setQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY, {
    pages: [{ items: [article(1, 90), article(7, score)], nextCursor: null }],
    pageParams: [undefined],
  })
}

const priorityRow7 = () =>
  qc.getQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY)?.pages[0].items.find((a) => a.id === 7)?.interestScore

/** Lets the mutation's onSuccess or onError and the reaction settle, so a late call would show up. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

type Save = 'raindrop' | 'board' | 'readLater'

/** Renders one save hook at `path` and returns a function that fires it for article 7. */
function saveAt(save: Save, path: string): () => void {
  if (save === 'raindrop') {
    const r = renderAt(() => useSaveToRaindrop(), path)
    return () => r.current.mutate(7)
  }
  if (save === 'board') {
    const r = renderAt(() => useAddArticleToBoard(), path)
    return () => r.current.mutate({ boardId: 3, articleId: 7 })
  }
  const r = renderAt(() => useReadLater(), path)
  return () => r.current.mutate(7)
}

beforeEach(() => {
  qc = createQueryClient()
  invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
  resetSpy = vi.spyOn(qc, 'resetQueries')
  refetchSpy = vi.spyOn(qc, 'refetchQueries')
  fetchSpy = vi.spyOn(globalThis, 'fetch')
  refetchedScore = 60
  respond()
  useToastStore.setState({ toasts: [] })
  usePriorityStore.setState({ rankingChanged: false, baselineUnscored: null })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('save paths run the engagement reaction (D-05..D-07)', () => {
  it('raindropSaveRefetchesTheArticleAndRefreshesLearnedAndLists', async () => {
    const save = saveAt('raindrop', '/')

    act(() => save())

    await waitFor(() => expect(articleGets()).toHaveLength(1))
    expect(fetchSpy).toHaveBeenCalledWith('/api/articles/7/raindrop', expect.objectContaining({ method: 'POST' }))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['interest', 'learned'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['articles'] })
    expect(toasts()).toEqual(['Saved to Raindrop'])
  })

  it('boardAddRefetchesTheArticleAndKeepsTheBoardRefresh', async () => {
    const save = saveAt('board', '/')

    act(() => save())

    await waitFor(() => expect(articleGets()).toHaveLength(1))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boardArticles', 3] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['interest', 'learned'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['articles'] })
  })

  it('readLaterRefetchesTheArticleAndKeepsItsReactions', async () => {
    const save = saveAt('readLater', '/')

    act(() => save())

    await waitFor(() => expect(articleGets()).toHaveLength(1))
    expect(fetchSpy).toHaveBeenCalledWith('/api/boards/3/articles', expect.objectContaining({ method: 'POST' }))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boards'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boardArticles'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['interest', 'learned'] })
    expect(toasts()).toEqual(['Added to Read Later'])
  })

  it('aFailedSaveRunsNoReaction', async () => {
    respond({ 'POST /api/articles/7/raindrop': () => new Response(null, { status: 503 }) })
    const result = renderAt(() => useSaveToRaindrop())

    act(() => result.current.mutate(7))

    await waitFor(() => expect(result.current.isError).toBe(true))
    await settle()
    expect(articleGets()).toHaveLength(0)
    expect(invalidatedWith(['interest', 'learned'])).toBe(false)
  })

  it('boardRemovalRunsNoReaction', async () => {
    const result = renderAt(() => useRemoveArticleFromBoard())

    act(() => result.current.mutate({ boardId: 3, articleId: 7 }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    await settle()
    expect(fetchSpy).toHaveBeenCalledWith('/api/boards/3/articles/7', expect.objectContaining({ method: 'DELETE' }))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boardArticles', 3] })
    expect(articleGets()).toHaveLength(0)
    expect(invalidatedWith(['interest', 'learned'])).toBe(false)
  })

  it.each<Save>(['raindrop', 'board', 'readLater'])(
    'onPriorityEachSavePatchesTheRowWhenTheScoreChanges (%s)',
    async (which) => {
      seedPriorityRow7(60)
      refetchedScore = 72
      const save = saveAt(which, '/priority')

      act(() => save())

      await waitFor(() => expect(priorityRow7()).toBe(72))
      expect(usePriorityStore.getState().rankingChanged).toBe(true)
      expect(qc.getQueryState(PRIORITY_KEY)?.isInvalidated).toBe(false)
    },
  )

  it('noSaveTouchesPriority', async () => {
    seedPriorityRow7(60)
    refetchedScore = 72
    const saves = (['raindrop', 'board', 'readLater'] as Save[]).map((s) => saveAt(s, '/priority'))

    act(() => saves.forEach((save) => save()))

    await waitFor(() => expect(toasts()).toEqual(expect.arrayContaining(['Saved to Raindrop', 'Added to Read Later'])))
    await waitFor(() => expect(articleGets().length).toBeGreaterThan(0))
    await waitFor(() => expect(priorityRow7()).toBe(72))
    await settle()
    for (const spy of [invalidateSpy, resetSpy, refetchSpy]) {
      for (const [filters] of spy.mock.calls) {
        expect((filters as { queryKey?: unknown[] } | undefined)?.queryKey?.[0]).not.toBe('priority')
      }
    }
    expect(resetSpy).not.toHaveBeenCalled()
    expect(qc.getQueryState(PRIORITY_KEY)?.isInvalidated).toBe(false)
    expect(fetchSpy.mock.calls.filter(([url]) => String(url).startsWith('/api/articles/priority'))).toHaveLength(0)
  })
})
