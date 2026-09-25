import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { PriorityList } from './PriorityList'
import { useUIStore } from '../stores/uiStore'
import type { Article } from '../types'

type Reply = { status: number; body?: unknown }
type Handler = (init?: RequestInit) => Reply | Promise<Reply>

interface RecordedCall {
  method: string
  url: string
}

let routes: Record<string, Handler>
let calls: RecordedCall[]
let unknownRoutes: string[]

function jsonResponse({ status, body }: Reply): Response {
  if (body === undefined) return new Response(null, { status })
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function route(method: string, url: string, handler: Handler) {
  routes[`${method} ${url}`] = handler
}

const PAGE_1 = '/api/articles/priority?limit=50'
const pageAfter = (id: number) => `/api/articles/priority?limit=50&before=${id}`

function page(items: Article[], nextCursor: number | null = null): Reply {
  return { status: 200, body: { items, nextCursor } }
}

function article(id: number, overrides: Partial<Article> = {}): Article {
  return {
    id,
    feedId: 1,
    guid: `g-${id}`,
    title: `Article ${id}`,
    url: `https://example.com/${id}`,
    author: null,
    content: null,
    summary: `Summary ${id}`,
    imageUrl: null,
    publishedAt: '2026-09-23T00:00:00Z',
    fetchedAt: '2026-09-23T00:00:00Z',
    read: false,
    starred: false,
    interestScore: null,
    ...overrides,
  }
}

function priorityGets() {
  return calls.filter((c) => c.method === 'GET' && c.url.startsWith('/api/articles/priority'))
}

function titles(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('.article-item-title')).map((e) => e.textContent ?? '')
}

/** The list's direct children, as 'row' / 'separator' / 'load-more'. */
function sequence(container: HTMLElement): string[] {
  const items = container.querySelector('.article-items')
  if (!items) return []
  return Array.from(items.children).map((e) => {
    if (e.classList.contains('article-item')) return 'row'
    if (e.classList.contains('priority-separator')) return 'separator'
    if (e.classList.contains('load-more')) return 'load-more'
    return e.className
  })
}

function scored(...scores: (number | null)[]): Article[] {
  return scores.map((s, i) => article(i + 1, { interestScore: s }))
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

/** Answers each call to a route with the next reply in turn; the last reply repeats. */
function sequenceOf(...replies: (() => Reply | Promise<Reply>)[]): Handler {
  let n = 0
  return () => replies[Math.min(n++, replies.length - 1)]()
}

const STATUS_ALL_SCORED = {
  configured: true,
  breakerState: 'CLOSED',
  coldStart: false,
  eligibleUnscored: 0,
  failed: 0,
}

const REFRESH = '↻ Refresh ranking'
const REFRESHING = '↻ Refreshing…'
const FIRST_PAGE_ERROR = "Couldn't load the Priority list. Press ↻ Refresh ranking to try again."

function renderPriority(props: { onSetUpInterests?: () => void } = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const utils = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/priority']}>
        <Routes>
          <Route path="/priority" element={<PriorityList {...props} />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...utils, qc }
}

describe('PriorityList', () => {
  beforeEach(() => {
    calls = []
    routes = {}
    unknownRoutes = []
    useUIStore.setState({ searchQuery: '', selectedArticleId: null })
    route('GET', '/api/interest/status', () => ({ status: 200, body: STATUS_ALL_SCORED }))
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      calls.push({ method, url })
      const handler = routes[`${method} ${url}`]
      if (!handler) {
        unknownRoutes.push(`${method} ${url}`)
        return new Response(null, { status: 599 })
      }
      return jsonResponse(await handler(init))
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    expect(unknownRoutes).toEqual([])
  })

  it('rendersServerOrderAndPagesWithTheCursor', async () => {
    route('GET', PAGE_1, () =>
      page(
        [
          article(3, { interestScore: 90 }),
          article(1, { interestScore: 82 }),
          article(2, { interestScore: null }),
        ],
        2,
      ),
    )
    route('GET', pageAfter(2), () => page([article(2), article(5), article(4)], null))

    const { container } = renderPriority()
    await screen.findByText('Article 3')
    expect(titles(container)).toEqual(['Article 3', 'Article 1', 'Article 2'])

    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Article 5')
    expect(titles(container)).toEqual(['Article 3', 'Article 1', 'Article 2', 'Article 5', 'Article 4'])
    expect(screen.getAllByText('Article 2')).toHaveLength(1)
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument())
    expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(2)])
  })

  it('clickingARowSelectsIt', async () => {
    route('GET', PAGE_1, () => page([article(3, { interestScore: 90 }), article(1, { interestScore: 82 })]))

    const { container } = renderPriority()
    await screen.findByText('Article 1')
    fireEvent.click(screen.getByText('Article 1'))

    expect(useUIStore.getState().selectedArticleId).toBe(1)
    const rows = container.querySelectorAll('.article-item')
    expect(rows[1]).toHaveClass('selected')
    expect(rows[0]).not.toHaveClass('selected')
  })

  it('separatorPrecedesTheFirstUnscoredRow', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82, null, null)))

    const { container } = renderPriority()
    await screen.findByText('Article 4')
    expect(sequence(container)).toEqual(['row', 'row', 'separator', 'row', 'row'])
    expect(screen.getAllByRole('separator', { name: 'Not yet scored' })).toHaveLength(1)
  })

  it('separatorFirstWhenNothingIsScored', async () => {
    route('GET', PAGE_1, () => page(scored(null, null)))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    expect(sequence(container)).toEqual(['separator', 'row', 'row'])
  })

  it('noSeparatorWhenEverythingIsScored', async () => {
    route('GET', PAGE_1, () => page(scored(90, 45)))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    expect(sequence(container)).toEqual(['row', 'row'])
    expect(screen.queryByRole('separator')).not.toBeInTheDocument()
  })

  it('separatorAppearsOnceTheFirstUnscoredRowLoads', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82), 2))
    route('GET', pageAfter(2), () => page([article(3, { interestScore: null })]))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    expect(screen.queryByRole('separator')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Article 3')
    await waitFor(() => expect(sequence(container)).toEqual(['row', 'row', 'separator', 'row']))
    expect(screen.getAllByRole('separator', { name: 'Not yet scored' })).toHaveLength(1)
  })

  it('filterHidesTheSeparatorWithoutUnscoredMatches', async () => {
    route('GET', PAGE_1, () =>
      page([
        article(1, { interestScore: 90, title: 'Rust compiler news', summary: null }),
        article(2, { interestScore: null, title: 'Gardening tips', summary: null }),
      ]),
    )

    const { container } = renderPriority()
    await screen.findByText('Gardening tips')
    expect(screen.getByRole('separator', { name: 'Not yet scored' })).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('Filter articles...'), { target: { value: 'rust' } })
    expect(sequence(container)).toEqual(['row'])
    expect(screen.queryByRole('separator')).not.toBeInTheDocument()
  })

  it('unscoredRowsKeepAnEmptySlot', async () => {
    route('GET', PAGE_1, () => page(scored(90, null)))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    const [scoredRow, unscoredRow] = Array.from(container.querySelectorAll('.article-item'))
    expect(scoredRow.querySelector('.interest-badge')).toHaveTextContent('90')
    expect(scoredRow.querySelector('.interest-badge-slot')).toBeNull()
    const slot = unscoredRow.querySelector('.interest-badge-slot')
    expect(slot).not.toBeNull()
    expect(slot).toHaveAttribute('aria-hidden', 'true')
    expect(unscoredRow.querySelector('.interest-badge')).toBeNull()
  })

  it('readRowKeepsItsBadge', async () => {
    route('GET', PAGE_1, () => page([article(1, { interestScore: 55, read: true })]))

    const { container } = renderPriority()
    await screen.findByText('Article 1')
    const row = container.querySelector('.article-item')
    expect(row).toHaveClass('read')
    expect(row?.querySelector('.interest-badge')).toHaveTextContent('55')
  })

  it('showsLoadingCopyWhileTheFirstPageIsPending', async () => {
    route('GET', PAGE_1, () => new Promise<Reply>(() => {}))

    renderPriority()
    expect(await screen.findByText('Loading articles…')).toBeInTheDocument()
    expect(screen.queryByText('All caught up!')).not.toBeInTheDocument()
  })

  it('showsCaughtUpWithDetailWhenEmpty', async () => {
    route('GET', PAGE_1, () => page([]))

    renderPriority()
    expect(await screen.findByText('All caught up!')).toBeInTheDocument()
    expect(screen.getByText('New unread articles are ranked here as your feeds update.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: REFRESH })).toBeInTheDocument()
  })

  it('showsErrorCopyWhenTheFirstPageFails', async () => {
    route('GET', PAGE_1, () => ({ status: 500, body: { title: 'Internal Server Error', detail: 'boom' } }))

    renderPriority()
    expect(await screen.findByText(FIRST_PAGE_ERROR)).toBeInTheDocument()
    expect(screen.getByText('Priority')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: REFRESH })).toBeInTheDocument()
    expect(screen.queryByText('boom')).not.toBeInTheDocument()
  })

  it('showsNoMatchesForTheFilter', async () => {
    route('GET', PAGE_1, () => page(scored(90, null)))

    renderPriority()
    await screen.findByText('Article 1')
    fireEvent.change(screen.getByPlaceholderText('Filter articles...'), { target: { value: 'zzz' } })
    expect(screen.getByText('No matches for "zzz"')).toBeInTheDocument()
  })

  it('refreshFetchesOnlyPageOne', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82), 2))
    route('GET', pageAfter(2), () => page([article(3, { interestScore: 50 })]))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Article 3')
    expect(priorityGets()).toHaveLength(2)

    fireEvent.click(await screen.findByRole('button', { name: REFRESH }))
    await waitFor(() => expect(titles(container)).toEqual(['Article 1', 'Article 2']))
    expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(2), PAGE_1])
  })

  it('refreshKeepsTheSelection', async () => {
    route(
      'GET',
      PAGE_1,
      sequenceOf(
        () => page(scored(90, 82)),
        () => page([article(1, { interestScore: 90 }), article(3, { interestScore: 70 })]),
      ),
    )

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByText('Article 2'))
    expect(useUIStore.getState().selectedArticleId).toBe(2)

    fireEvent.click(screen.getByRole('button', { name: REFRESH }))
    await waitFor(() => expect(titles(container)).toEqual(['Article 1', 'Article 3']))
    expect(useUIStore.getState().selectedArticleId).toBe(2)
  })

  it('refreshButtonShowsRefreshingWhileInFlight', async () => {
    const refresh = deferred<Reply>()
    route('GET', PAGE_1, sequenceOf(() => page(scored(90)), () => refresh.promise))

    renderPriority()
    await screen.findByText('Article 1')
    fireEvent.click(await screen.findByRole('button', { name: REFRESH }))

    const busy = await screen.findByRole('button', { name: REFRESHING })
    expect(busy).toBeDisabled()

    refresh.resolve(page(scored(80)))
    const idle = await screen.findByRole('button', { name: REFRESH })
    expect(idle).toBeEnabled()
  })

  it('failedRefreshReturnsTheButtonToIdle', async () => {
    route('GET', PAGE_1, sequenceOf(() => page(scored(90)), () => ({ status: 500 })))

    renderPriority()
    await screen.findByText('Article 1')
    fireEvent.click(await screen.findByRole('button', { name: REFRESH }))

    expect(await screen.findByText(FIRST_PAGE_ERROR)).toBeInTheDocument()
    const idle = await screen.findByRole('button', { name: REFRESH })
    expect(idle).toBeEnabled()
  })

  it('loadMoreFailureRelabelsAndRetries', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82), 2))
    route(
      'GET',
      pageAfter(2),
      sequenceOf(() => ({ status: 500 }), () => page([article(3, { interestScore: 40 })])),
    )

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))

    const retry = await screen.findByRole('button', { name: "Couldn't load more. Try again" })
    expect(retry).toBeEnabled()
    expect(titles(container)).toEqual(['Article 1', 'Article 2'])

    fireEvent.click(retry)
    await screen.findByText('Article 3')
    expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(2), pageAfter(2)])
  })

  it('missingCursorRestartsFromPageOne', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82), 2))
    route('GET', pageAfter(2), () => ({ status: 404, body: { title: 'Not Found' } }))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))

    await waitFor(() => expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(2), PAGE_1]))
    await waitFor(() => expect(titles(container)).toEqual(['Article 1', 'Article 2']))
    await screen.findByRole('button', { name: 'Load more' })
  })

  it('bannerSitsAboveTheListAndNeverReplacesIt', async () => {
    route('GET', '/api/interest/status', () => ({
      status: 200,
      body: { ...STATUS_ALL_SCORED, breakerState: 'OPEN', eligibleUnscored: 4 },
    }))
    route('GET', PAGE_1, () => page(scored(80, 50)))
    const { container } = renderPriority()

    const bannerEl = await screen.findByRole('status')
    expect(bannerEl).toHaveTextContent('⏸ Scoring paused — 4 articles waiting. It resumes automatically.')
    await waitFor(() => expect(titles(container)).toEqual(['Article 1', 'Article 2']))

    const panel = container.querySelector('.article-list')!
    const children = Array.from(panel.children)
    const toolbarAt = children.findIndex((e) => e.classList.contains('article-list-toolbar'))
    const bannerAt = children.indexOf(bannerEl)
    const filterAt = children.findIndex((e) => e.classList.contains('search-input'))
    expect(toolbarAt).toBe(0)
    expect(bannerAt).toBe(toolbarAt + 1)
    expect(filterAt).toBe(bannerAt + 1)
    expect(bannerEl.contains(container.querySelector('.article-items'))).toBe(false)
  })

  it('coldStartButtonCallsOnSetUpInterests', async () => {
    route('GET', '/api/interest/status', () => ({
      status: 200,
      body: { ...STATUS_ALL_SCORED, coldStart: true },
    }))
    route('GET', PAGE_1, () => page(scored(null, null)))
    const onSetUpInterests = vi.fn()
    renderPriority({ onSetUpInterests })

    fireEvent.click(await screen.findByRole('button', { name: 'Set up interests' }))
    expect(onSetUpInterests).toHaveBeenCalledTimes(1)
  })
})
