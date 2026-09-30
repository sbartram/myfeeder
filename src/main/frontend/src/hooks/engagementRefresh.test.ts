import { describe, it, expect, vi, beforeEach, afterEach, type MockInstance } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { createQueryClient } from '../queryClient'
import { useToastStore } from '../components/Toast'
import { useSaveToRaindrop } from './useArticles'
import { useAddArticleToBoard, useReadLater } from './useBoards'

let qc: QueryClient
let fetchSpy: MockInstance<typeof fetch>
let invalidateSpy: MockInstance<QueryClient['invalidateQueries']>

const wrapper = ({ children }: { children: ReactNode }) =>
  createElement(QueryClientProvider, { client: qc }, children)

const BY_ID = { queryKey: ['article', 7], exact: true }

const READ_LATER_BOARD = {
  id: 3,
  name: 'Read Later',
  description: null,
  createdAt: '2026-09-29T00:00:00Z',
}

/** Answers each request by "METHOD path"; anything unrouted gets the fallback status. */
function respond(routes: Record<string, () => Response>, fallback = 204) {
  fetchSpy.mockImplementation(async (input, init) => {
    const handler = routes[`${init?.method ?? 'GET'} ${String(input)}`]
    return handler ? handler() : new Response(null, { status: fallback })
  })
}

const firstKeys = () => invalidateSpy.mock.calls.map(([filters]) => filters?.queryKey?.[0])

/** Lets the mutation's onSuccess or onError settle, so a late invalidation would show up. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

beforeEach(() => {
  qc = createQueryClient()
  invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
  fetchSpy = vi.spyOn(globalThis, 'fetch')
  respond({})
  useToastStore.setState({ toasts: [] })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('save paths refresh the by-id article (D-06)', () => {
  it('raindropSaveRefreshesOnlyTheByIdArticle', async () => {
    const { result } = renderHook(() => useSaveToRaindrop(), { wrapper })

    act(() => result.current.mutate(7))

    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith(BY_ID))
    expect(fetchSpy).toHaveBeenCalledWith('/api/articles/7/raindrop', expect.objectContaining({ method: 'POST' }))
    expect(useToastStore.getState().toasts.map((t) => t.message)).toEqual(['Saved to Raindrop'])
    expect(invalidateSpy).toHaveBeenCalledTimes(1)
  })

  it('boardAddRefreshesTheBoardAndTheByIdArticle', async () => {
    respond({ 'POST /api/boards/3/articles': () => new Response(null, { status: 201 }) })
    const { result } = renderHook(() => useAddArticleToBoard(), { wrapper })

    act(() => result.current.mutate({ boardId: 3, articleId: 7 }))

    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith(BY_ID))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boardArticles', 3] })
  })

  it('readLaterRefreshesTheByIdArticle', async () => {
    respond({
      'POST /api/boards/by-name': () =>
        new Response(JSON.stringify(READ_LATER_BOARD), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      'POST /api/boards/3/articles': () => new Response(null, { status: 201 }),
    })
    const { result } = renderHook(() => useReadLater(), { wrapper })

    act(() => result.current.mutate(7))

    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith(BY_ID))
    expect(fetchSpy).toHaveBeenCalledWith('/api/boards/3/articles', expect.objectContaining({ method: 'POST' }))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boards'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['boardArticles'] })
    expect(useToastStore.getState().toasts.map((t) => t.message)).toEqual(['Added to Read Later'])
  })

  it('aFailedSaveDoesNotRefresh', async () => {
    respond({}, 503)
    const { result } = renderHook(() => useSaveToRaindrop(), { wrapper })

    act(() => result.current.mutate(7))

    await waitFor(() => expect(result.current.isError).toBe(true))
    await settle()
    expect(invalidateSpy).not.toHaveBeenCalledWith(BY_ID)
    expect(firstKeys()).not.toContain('article')
  })

  it('noSaveInvalidatesPriority', async () => {
    respond({
      'POST /api/boards/by-name': () =>
        new Response(JSON.stringify(READ_LATER_BOARD), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      'POST /api/boards/3/articles': () => new Response(null, { status: 201 }),
    })
    const raindrop = renderHook(() => useSaveToRaindrop(), { wrapper }).result
    const board = renderHook(() => useAddArticleToBoard(), { wrapper }).result
    const readLater = renderHook(() => useReadLater(), { wrapper }).result

    act(() => raindrop.current.mutate(7))
    act(() => board.current.mutate({ boardId: 3, articleId: 7 }))
    act(() => readLater.current.mutate(7))

    await waitFor(() =>
      expect(invalidateSpy.mock.calls.filter(([f]) => f?.queryKey?.[0] === 'article')).toHaveLength(3)
    )
    await settle()
    expect(firstKeys()).not.toContain('priority')
    expect(firstKeys()).not.toContain('articles')
    for (const [filters] of invalidateSpy.mock.calls) {
      if (filters?.queryKey?.[0] === 'article') expect(filters).toEqual(BY_ID)
    }
  })
})
