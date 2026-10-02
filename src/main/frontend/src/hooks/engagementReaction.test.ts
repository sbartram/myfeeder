import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('../api/articles', () => ({
  articlesApi: {
    getById: vi.fn(),
    priority: vi.fn(),
  },
}))

import { renderHook, act } from '@testing-library/react'
import { QueryClientProvider, useMutation, type InfiniteData, type QueryClient } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { afterEngagement } from './engagementReaction'
import { PRIORITY_KEY } from './usePriorityArticles'
import { articlesApi } from '../api/articles'
import { ApiError } from '../api/client'
import { createQueryClient } from '../queryClient'
import { useToastStore } from '../components/Toast'
import { usePriorityStore } from '../stores/priorityStore'
import type { Article, PriorityPage } from '../types'

function article(id: number, interestScore: number | null = null): Article {
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

let qc: QueryClient

/** Seeds the Priority cache with one page per argument, each a list of [id, score] rows. */
function seedPriority(...pages: [number, number | null][][]) {
  qc.setQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY, {
    pages: pages.map((rows, i) => ({
      items: rows.map(([id, score]) => article(id, score)),
      nextCursor: i < pages.length - 1 ? `c${i}` : null,
    })),
    pageParams: pages.map((_, i) => (i === 0 ? undefined : `c${i - 1}`)),
  })
}

function priorityRows(): [number, number | null | undefined][][] {
  const data = qc.getQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY)
  return (data?.pages ?? []).map((p) => p.items.map((a) => [a.id, a.interestScore]))
}

function refetchReturns(id: number, score: number | null) {
  vi.mocked(articlesApi.getById).mockImplementation(async (n) => article(n, n === id ? score : null))
}

const hint = () => usePriorityStore.getState().rankingChanged
const invalidated = (key: readonly unknown[]) => qc.getQueryState(key)?.isInvalidated

beforeEach(() => {
  vi.resetAllMocks()
  qc = createQueryClient()
  useToastStore.setState({ toasts: [] })
  usePriorityStore.setState({ rankingChanged: false, baselineUnscored: null })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('afterEngagement', () => {
  it('aDifferentScorePatchesTheRowInPlaceAndLightsTheHint', async () => {
    seedPriority([[1, 90], [2, 80], [3, 70]])
    refetchReturns(2, 95)

    await afterEngagement(qc, 2, true)

    expect(priorityRows()).toEqual([[[1, 90], [2, 95], [3, 70]]])
    expect(hint()).toBe(true)
    expect(qc.getQueryData<Article>(['article', 2])?.interestScore).toBe(95)
  })

  it('anEqualScoreChangesNothing', async () => {
    seedPriority([[1, 90], [2, 80], [3, null]])
    refetchReturns(2, 80)

    await afterEngagement(qc, 2, true)

    expect(priorityRows()).toEqual([[[1, 90], [2, 80], [3, null]]])
    expect(hint()).toBe(false)

    // An unscored row whose refetch is still unscored changes nothing either.
    refetchReturns(3, null)
    await afterEngagement(qc, 3, true)

    expect(priorityRows()).toEqual([[[1, 90], [2, 80], [3, null]]])
    expect(hint()).toBe(false)
  })

  it('aNullScoreAgainstAScoredRowPatchesToNull', async () => {
    seedPriority([[1, 90], [2, 80]])
    refetchReturns(2, null)

    await afterEngagement(qc, 2, true)

    expect(priorityRows()).toEqual([[[1, 90], [2, null]]])
    expect(hint()).toBe(true)
  })

  it('anUnloadedRowChangesNothing', async () => {
    seedPriority([[1, 90], [2, 80]])
    refetchReturns(9, 95)

    await afterEngagement(qc, 9, true)

    expect(articlesApi.getById).toHaveBeenCalledWith(9)
    expect(priorityRows()).toEqual([[[1, 90], [2, 80]]])
    expect(hint()).toBe(false)
  })

  it('duplicateRowsArePatchedEverywhere', async () => {
    seedPriority([[1, 90], [2, 80]], [[2, 80], [3, 70]])
    refetchReturns(2, 95)

    await afterEngagement(qc, 2, true)

    expect(priorityRows()).toEqual([
      [[1, 90], [2, 95]],
      [[2, 95], [3, 70]],
    ])
    expect(hint()).toBe(true)
  })

  it('offPriorityPatchesNothingAndRefreshesOtherOpenArticles', async () => {
    qc.setQueryData(['article', 8], article(8, 50))
    qc.setQueryData(['article', 8, 'extracted'], { title: 't', content: '<p/>' })
    qc.setQueryData(['articles', {}], { pages: [], pageParams: [] })
    qc.setQueryData(['interest', 'learned'], [])
    seedPriority([[2, 80]])
    refetchReturns(2, 95)

    await afterEngagement(qc, 2, false)

    expect(invalidated(['article', 8])).toBe(true)
    expect(invalidated(['article', 8, 'extracted'])).toBe(false)
    expect(invalidated(['articles', {}])).toBe(true)
    expect(invalidated(['interest', 'learned'])).toBe(true)
    expect(priorityRows()).toEqual([[[2, 80]]])
    expect(hint()).toBe(false)
    expect(qc.getQueryData<Article>(['article', 2])?.interestScore).toBe(95)
  })

  it('suggestionsAreMarkedStaleOnAndOffPriority', async () => {
    seedPriority([[2, 80]])
    refetchReturns(2, 95)
    qc.setQueryData(['interest', 'suggestions'], { items: [], total: 0 })

    await afterEngagement(qc, 2, false)
    expect(invalidated(['interest', 'suggestions'])).toBe(true)

    qc.setQueryData(['interest', 'suggestions'], { items: [], total: 0 })
    expect(invalidated(['interest', 'suggestions'])).toBe(false)
    await afterEngagement(qc, 2, true)
    expect(invalidated(['interest', 'suggestions'])).toBe(true)
  })

  it('onPriorityLeavesOtherOpenArticlesAlone', async () => {
    qc.setQueryData(['article', 8], article(8, 50))
    qc.setQueryData(['articles', {}], { pages: [], pageParams: [] })
    qc.setQueryData(['interest', 'learned'], [])
    seedPriority([[2, 80]])
    refetchReturns(2, 95)

    await afterEngagement(qc, 2, true)

    expect(invalidated(['article', 8])).toBe(false)
    expect(invalidated(['articles', {}])).toBe(true)
    expect(invalidated(['interest', 'learned'])).toBe(true)
  })

  it('aFreshCachedArticleIsStillRefetched', async () => {
    seedPriority([[2, 80]])
    refetchReturns(2, 95)
    qc.setQueryData(['article', 2], article(2, 80))

    await afterEngagement(qc, 2, true)

    expect(articlesApi.getById).toHaveBeenCalledTimes(1)
    expect(qc.getQueryData<Article>(['article', 2])?.interestScore).toBe(95)
    expect(priorityRows()).toEqual([[[2, 95]]])
  })

  it('neverInvalidatesResetsOrRefetchesPriority', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const resetSpy = vi.spyOn(qc, 'resetQueries')
    const refetchSpy = vi.spyOn(qc, 'refetchQueries')
    seedPriority([[1, 90], [2, 80]])
    refetchReturns(2, 95)

    await afterEngagement(qc, 2, true)
    await afterEngagement(qc, 2, false)

    expect(invalidateSpy).toHaveBeenCalled()
    for (const spy of [invalidateSpy, resetSpy, refetchSpy]) {
      for (const [filters] of spy.mock.calls) {
        const key = (filters as { queryKey?: unknown[] } | undefined)?.queryKey
        expect(key?.[0]).not.toBe('priority')
      }
    }
    // invalidateQueries refetches through refetchQueries internally; those calls carry the same filters.
    expect(resetSpy).not.toHaveBeenCalled()
    expect(qc.getQueryState(PRIORITY_KEY)?.isInvalidated).toBe(false)
    expect(articlesApi.priority).not.toHaveBeenCalled()
  })

  it('aFailedRefetchIsSilentAndNeverRejects', async () => {
    seedPriority([[2, 80]])
    vi.mocked(articlesApi.getById).mockRejectedValue(new ApiError('boom', 500))

    await expect(afterEngagement(qc, 2, true)).resolves.toBeUndefined()

    expect(articlesApi.getById).toHaveBeenCalledTimes(1)
    expect(useToastStore.getState().toasts).toHaveLength(0)
    expect(hint()).toBe(false)
    expect(priorityRows()).toEqual([[[2, 80]]])
  })

  it('aPendingVoteOnTheSameArticleSkipsThePatch', async () => {
    seedPriority([[1, 90], [2, 80]])
    refetchReturns(2, 95)
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children)
    const vote = renderHook(
      () =>
        useMutation({
          mutationKey: ['feedback'],
          mutationFn: (_v: { id: number }) => new Promise<void>(() => {}),
        }),
      { wrapper },
    )
    act(() => vote.result.current.mutate({ id: 2 }))
    expect(qc.isMutating({ mutationKey: ['feedback'] })).toBe(1)

    await afterEngagement(qc, 2, true)

    expect(articlesApi.getById).toHaveBeenCalledWith(2)
    expect(priorityRows()).toEqual([[[1, 90], [2, 80]]])
    expect(hint()).toBe(false)
  })
})
