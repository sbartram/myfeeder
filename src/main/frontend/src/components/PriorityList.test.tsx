import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { PriorityList } from './PriorityList'
import { useUpdateArticleState } from '../hooks/useArticles'
import { useSaveInterestProfile } from '../hooks/useInterest'
import { useOpenOriginal } from '../hooks/useEngagement'
import { useUIStore } from '../stores/uiStore'
import { usePriorityStore } from '../stores/priorityStore'
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
// A real served-tuple cursor, the literal PriorityPageTest pins on the server
const CURSOR = 'LUluZmluaXR5fDE3NTg4MDAwMDAxMjM0NTZ8NDI'
const pageAfter = (cursor: string) => `/api/articles/priority?limit=50&before=${cursor}`

function page(items: Article[], nextCursor: string | null = null): Reply {
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
const HINT_LABEL = 'Ranking changed. Refresh ranking'
const HINT_TEXT = '↻ Ranking changed — refresh'
const REFRESHING = '↻ Refreshing…'
const FIRST_PAGE_ERROR = "Couldn't load the Priority list. Press ↻ Refresh ranking to try again."

/** A button that marks one article read through the app's shared state mutation. */
function MarkReadHarness({ id }: { id: number }) {
  const update = useUpdateArticleState()
  return <button onClick={() => update.mutate({ id, state: { read: true } })}>harness mark read</button>
}

/** A button that saves the interest profile through the app's mutation. */
function SaveProfileHarness() {
  const save = useSaveInterestProfile()
  return <button onClick={() => save.mutate('I like compilers')}>harness save profile</button>
}

/** A button that opens one article through the app's Open Original path. */
function OpenOriginalHarness({ id, url }: { id: number; url: string }) {
  const open = useOpenOriginal()
  return <button onClick={() => open({ id, url })}>harness open</button>
}

const waiting = (eligibleUnscored: number) => () => ({
  status: 200,
  body: { ...STATUS_ALL_SCORED, eligibleUnscored },
})

function renderPriority(props: { onSetUpInterests?: () => void } = {}, extra?: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const utils = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/priority']}>
        <Routes>
          <Route path="/priority" element={<PriorityList {...props} />} />
        </Routes>
        {extra}
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
    usePriorityStore.setState({ rankingChanged: false, baselineUnscored: null })
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
        CURSOR,
      ),
    )
    route('GET', pageAfter(CURSOR), () => page([article(2), article(5), article(4)], null))

    const { container } = renderPriority()
    await screen.findByText('Article 3')
    expect(titles(container)).toEqual(['Article 3', 'Article 1', 'Article 2'])

    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Article 5')
    expect(titles(container)).toEqual(['Article 3', 'Article 1', 'Article 2', 'Article 5', 'Article 4'])
    expect(screen.getAllByText('Article 2')).toHaveLength(1)
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument())
    expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(CURSOR)])
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
    route('GET', PAGE_1, () => page(scored(90, 82), CURSOR))
    route('GET', pageAfter(CURSOR), () => page([article(3, { interestScore: null })]))

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
    route('GET', PAGE_1, () => page(scored(90, 82), CURSOR))
    route('GET', pageAfter(CURSOR), () => page([article(3, { interestScore: 50 })]))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Article 3')
    expect(priorityGets()).toHaveLength(2)

    fireEvent.click(await screen.findByRole('button', { name: REFRESH }))
    await waitFor(() => expect(titles(container)).toEqual(['Article 1', 'Article 2']))
    expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(CURSOR), PAGE_1])
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
    route('GET', PAGE_1, () => page(scored(90, 82), CURSOR))
    route(
      'GET',
      pageAfter(CURSOR),
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
    expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(CURSOR), pageAfter(CURSOR)])
  })

  it('missingCursorRestartsFromPageOne', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82), CURSOR))
    route('GET', pageAfter(CURSOR), () => ({ status: 404, body: { title: 'Not Found' } }))

    const { container } = renderPriority()
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))

    await waitFor(() => expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, pageAfter(CURSOR), PAGE_1]))
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

  it('markReadKeepsTheRowInPlaceDimmed', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82, 70)))
    route('PATCH', '/api/articles/2', () => ({
      status: 200,
      body: article(2, { read: true, interestScore: 5 }),
    }))

    const { container } = renderPriority({}, <MarkReadHarness id={2} />)
    await screen.findByText('Article 3')
    fireEvent.click(screen.getByRole('button', { name: 'harness mark read' }))

    await waitFor(() => expect(container.querySelectorAll('.article-item')[1]).toHaveClass('read'))
    expect(titles(container)).toEqual(['Article 1', 'Article 2', 'Article 3'])
    const rows = container.querySelectorAll('.article-item')
    expect(rows[0]).not.toHaveClass('read')
    expect(rows[2]).not.toHaveClass('read')
    expect(rows[1].querySelector('.interest-badge')).toHaveTextContent('82')
    expect(priorityGets()).toHaveLength(1)
  })

  it('hintLightsWhenFewerArticlesAreWaiting', async () => {
    route('GET', '/api/interest/status', sequenceOf(waiting(10), waiting(8)))
    route('GET', PAGE_1, () => page(scored(90, 82)))

    const { container, qc } = renderPriority()
    await screen.findByText('Article 2')
    await waitFor(() => expect(usePriorityStore.getState().baselineUnscored).toBe(10))
    expect(screen.getByRole('button', { name: REFRESH })).toBeInTheDocument()

    await act(async () => {
      await qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    })

    const hint = await screen.findByRole('button', { name: HINT_LABEL })
    expect(hint).toHaveTextContent(HINT_TEXT)
    expect(hint).toHaveClass('toolbar-btn', 'priority-refresh', 'hint')
    expect(titles(container)).toEqual(['Article 1', 'Article 2'])
    expect(priorityGets()).toHaveLength(1)
  })

  it('noHintWhenMoreArticlesAreWaiting', async () => {
    route('GET', '/api/interest/status', sequenceOf(waiting(10), waiting(12)))
    route('GET', PAGE_1, () => page(scored(90, 82)))

    const { qc } = renderPriority()
    await screen.findByText('Article 2')
    await waitFor(() => expect(usePriorityStore.getState().baselineUnscored).toBe(10))

    await act(async () => {
      await qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    })
    await waitFor(() => expect(calls.filter((c) => c.url === '/api/interest/status')).toHaveLength(2))

    expect(screen.getByRole('button', { name: REFRESH })).not.toHaveClass('hint')
    expect(usePriorityStore.getState().rankingChanged).toBe(false)
  })

  it('interestSaveLightsTheHint', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82)))
    route('PUT', '/api/interest/profile', () => ({
      status: 200,
      body: { id: 1, profileText: 'I like compilers', version: 2, updatedAt: '2026-09-25T00:00:00Z' },
    }))

    const { container } = renderPriority({}, <SaveProfileHarness />)
    await screen.findByText('Article 2')
    fireEvent.click(screen.getByRole('button', { name: 'harness save profile' }))

    await waitFor(() => expect(usePriorityStore.getState().rankingChanged).toBe(true))
    const hint = await screen.findByRole('button', { name: HINT_LABEL })
    expect(hint).toHaveTextContent(HINT_TEXT)
    expect(titles(container)).toEqual(['Article 1', 'Article 2'])
    expect(priorityGets()).toHaveLength(1)
  })

  it('refreshClearsTheHint', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82)))

    renderPriority()
    await screen.findByText('Article 2')
    act(() => {
      usePriorityStore.getState().setRankingChanged(true)
    })

    fireEvent.click(await screen.findByRole('button', { name: HINT_LABEL }))

    expect(await screen.findByRole('button', { name: REFRESH })).toBeInTheDocument()
    await waitFor(() => expect(priorityGets().map((c) => c.url)).toEqual([PAGE_1, PAGE_1]))
    expect(usePriorityStore.getState().rankingChanged).toBe(false)
  })

  it('reenteringClearsTheHint', async () => {
    route('GET', PAGE_1, () => page(scored(90, 82)))

    const first = renderPriority()
    await screen.findByText('Article 2')
    act(() => {
      usePriorityStore.getState().setRankingChanged(true)
    })
    expect(await screen.findByRole('button', { name: HINT_LABEL })).toBeInTheDocument()
    first.unmount()

    renderPriority()
    expect(await screen.findByRole('button', { name: REFRESH })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: HINT_LABEL })).not.toBeInTheDocument()
  })

  it('openingLightsTheHintAndPatchesTheBadgeWithoutReordering', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    route('GET', PAGE_1, () => page(scored(90, 82, 70)))
    route('PUT', '/api/articles/2/engagement/open', () => ({ status: 204 }))
    route('GET', '/api/articles/2', () => ({ status: 200, body: article(2, { interestScore: 95 }) }))

    const { container } = renderPriority({}, <OpenOriginalHarness id={2} url="https://example.com/2" />)
    await screen.findByText('Article 3')
    fireEvent.click(screen.getByRole('button', { name: 'harness open' }))

    const hint = await screen.findByRole('button', { name: HINT_LABEL })
    expect(hint).toHaveTextContent(HINT_TEXT)
    expect(titles(container)).toEqual(['Article 1', 'Article 2', 'Article 3'])
    await waitFor(() =>
      expect(container.querySelectorAll('.article-item')[1].querySelector('.interest-badge')).toHaveTextContent('95'),
    )
    expect(priorityGets()).toHaveLength(1)
    expect(openSpy).toHaveBeenCalledWith('https://example.com/2', '_blank', 'noopener')
  })

  it('aRepeatOpenWithTheSameScoreLightsNothing', async () => {
    vi.spyOn(window, 'open').mockImplementation(() => null)
    route('GET', PAGE_1, () => page(scored(90, 82, 70)))
    route('PUT', '/api/articles/2/engagement/open', () => ({ status: 204 }))
    route('GET', '/api/articles/2', () => ({ status: 200, body: article(2, { interestScore: 82 }) }))

    const { container } = renderPriority({}, <OpenOriginalHarness id={2} url="https://example.com/2" />)
    await screen.findByText('Article 3')
    fireEvent.click(screen.getByRole('button', { name: 'harness open' }))
    fireEvent.click(screen.getByRole('button', { name: 'harness open' }))

    await waitFor(() => expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(2))
    await waitFor(() =>
      expect(calls.filter((c) => c.method === 'GET' && c.url === '/api/articles/2').length).toBeGreaterThan(0),
    )
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    expect(usePriorityStore.getState().rankingChanged).toBe(false)
    expect(screen.getByRole('button', { name: REFRESH })).toBeInTheDocument()
    expect(container.querySelectorAll('.article-item')[1].querySelector('.interest-badge')).toHaveTextContent('82')
    expect(priorityGets()).toHaveLength(1)
  })
})
