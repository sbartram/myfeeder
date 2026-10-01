import { describe, it, expect, vi, beforeEach, afterEach, type MockInstance } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClientProvider, type InfiniteData, type QueryClient } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ScoreRow } from './ScoreRow'
import { createQueryClient } from '../queryClient'
import { useToastStore } from './Toast'
import { usePriorityStore } from '../stores/priorityStore'
import { PRIORITY_KEY } from '../hooks/usePriorityArticles'
import type { Article, PriorityPage } from '../types'

let qc: QueryClient
let fetchSpy: ReturnType<typeof vi.spyOn>
let invalidateSpy: MockInstance<QueryClient['invalidateQueries']>

const article = (overrides: Partial<Article>): Article =>
  ({
    id: 1,
    feedId: 1,
    guid: 'g-1',
    title: 'Test Article',
    url: 'https://example.com/post',
    author: null,
    content: null,
    summary: null,
    imageUrl: null,
    publishedAt: '2026-08-01T10:00:00Z',
    fetchedAt: '2026-08-01T10:05:00Z',
    read: false,
    starred: false,
    ...overrides,
  }) as Article

function renderRow(a: Article, path = '/') {
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <ScoreRow article={a} />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

/** The by-id article the Forget reaction refetches; its score is what GET /api/articles/1 answers. */
let refetchedScore: number | null

const articleGets = () =>
  fetchSpy.mock.calls.filter(
    ([url, init]: [string, RequestInit | undefined]) =>
      url === '/api/articles/1' && (init?.method ?? 'GET') === 'GET'
  )

const firstKeys = () =>
  invalidateSpy.mock.calls.map(([filters]) => filters?.queryKey?.[0])

beforeEach(() => {
  qc = createQueryClient()
  invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
  refetchedScore = null
  fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    if (String(input) === '/api/articles/1' && (init?.method ?? 'GET') === 'GET') {
      return new Response(JSON.stringify(article({ interestScore: refetchedScore, engagement: [] })), {
        headers: { 'Content-Type': 'application/json' },
      })
    }
    return new Response(null, { status: 204 })
  })
  useToastStore.setState({ toasts: [] })
  usePriorityStore.setState({ whyOpen: false, rankingChanged: false, baselineUnscored: null })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ScoreRow engagement line', () => {
  it('scoredAndEngagedShowsTheLabelAfterWhy', () => {
    const { container } = renderRow(article({ interestScore: 82, engagement: ['OPEN_ORIGINAL', 'STAR'] }))

    const row = container.querySelector('.score-row')!
    expect(row.textContent).toBe('82Why 82? ▸ · Engaged: opened, starred · Forget')
    expect(screen.getByRole('button', { name: 'Forget engagement' })).toBeInTheDocument()
  })

  it('allFourKindsReadInOrder', () => {
    const { container } = renderRow(
      article({ interestScore: 82, engagement: ['OPEN_ORIGINAL', 'STAR', 'BOARD', 'RAINDROP'] })
    )

    expect(container.querySelector('.engagement-label')!.textContent).toBe(
      'Engaged: opened, starred, on a board, saved to Raindrop'
    )
  })

  it('unscoredButEngagedShowsOnlyTheEngagementLine', () => {
    const { container } = renderRow(article({ interestScore: null, engagement: ['BOARD'] }))

    const row = container.querySelector('.score-row')!
    expect(row).not.toBeNull()
    expect(row.querySelector('.interest-badge')).toBeNull()
    expect(screen.queryByRole('button', { name: /Why/ })).toBeNull()
    expect(row.textContent).toBe('Engaged: on a board · Forget')
  })

  it('noEngagementShowsNoLine', () => {
    const empty = renderRow(article({ interestScore: 82, engagement: [] }))
    expect(screen.queryByText(/Engaged/)).toBeNull()
    expect(screen.queryByRole('button', { name: 'Forget engagement' })).toBeNull()
    empty.unmount()

    const absent = renderRow(article({ interestScore: 82 }))
    expect(screen.queryByText(/Engaged/)).toBeNull()
    expect(screen.queryByRole('button', { name: 'Forget engagement' })).toBeNull()
    absent.unmount()

    const unscored = renderRow(article({ interestScore: null, engagement: [] }))
    expect(unscored.container).toBeEmptyDOMElement()
  })

  it('forgetRefetchesTheArticleAndRefreshesLearnedAndLists', async () => {
    renderRow(article({ interestScore: null, engagement: ['OPEN_ORIGINAL', 'STAR'] }))

    fireEvent.click(screen.getByRole('button', { name: 'Forget engagement' }))

    await waitFor(() =>
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/articles/1/engagement',
        expect.objectContaining({ method: 'DELETE' })
      )
    )
    await waitFor(() => expect(articleGets()).toHaveLength(1))
    const deleteOrder = fetchSpy.mock.invocationCallOrder[0]
    const getOrder = fetchSpy.mock.invocationCallOrder[fetchSpy.mock.calls.indexOf(articleGets()[0])]
    expect(deleteOrder).toBeLessThan(getOrder)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['interest', 'learned'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['articles'] })
    expect(firstKeys()).not.toContain('priority')
    expect(useToastStore.getState().toasts).toEqual([])
  })

  it('forgetOnPriorityPatchesTheRowAndLightsTheHint', async () => {
    qc.setQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY, {
      pages: [{ items: [article({ interestScore: 82 })], nextCursor: null }],
      pageParams: [undefined],
    })
    refetchedScore = 75
    renderRow(article({ interestScore: 82, engagement: ['STAR'] }), '/priority')

    fireEvent.click(screen.getByRole('button', { name: 'Forget engagement' }))

    await waitFor(() => expect(usePriorityStore.getState().rankingChanged).toBe(true))
    const cached = qc.getQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY)
    expect(cached?.pages[0].items[0].interestScore).toBe(75)
    expect(firstKeys()).not.toContain('priority')
  })

  it('aFailedForgetShowsTheErrorToast', async () => {
    fetchSpy.mockImplementation(async () => new Response(null, { status: 500 }))
    renderRow(article({ interestScore: 82, engagement: ['RAINDROP'] }))

    fireEvent.click(screen.getByRole('button', { name: 'Forget engagement' }))

    await waitFor(() => expect(useToastStore.getState().toasts).toHaveLength(1))
    expect(useToastStore.getState().toasts[0].type).toBe('error')
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['article', 1], exact: true })
    expect(articleGets()).toHaveLength(0)
    expect(screen.getByText('Engaged: saved to Raindrop')).toBeInTheDocument()
  })
})
