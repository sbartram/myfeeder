import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { FeedbackBar } from './FeedbackBar'
import { createQueryClient } from '../queryClient'
import { useToastStore } from './Toast'
import { useUIStore } from '../stores/uiStore'
import { useFeedbackStore } from '../stores/feedbackStore'
import { useArticle } from '../hooks/useArticles'
import type { Article, ArticleFeedback, FeedbackResult, TopicBreakdownRow, TopicEffect } from '../types'

type Reply = { status: number; body?: unknown }
type Handler = (init?: RequestInit) => Reply | Promise<Reply>

interface RecordedCall {
  method: string
  url: string
  body?: string
}

let routes: Record<string, Handler>
let calls: RecordedCall[]

function jsonResponse({ status, body }: Reply): Response {
  if (body === undefined) return new Response(null, { status })
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function route(method: string, path: string, handler: Handler) {
  routes[`${method} ${path}`] = handler
}

function deferred<T>() {
  let resolve!: (v: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

const topicRow = (topicId: number, name: string, weight: number, noul = 0.8): TopicBreakdownRow => ({
  kind: 'TOPIC',
  topicId,
  name,
  noul,
  hinge: noul * 2 - 1,
  weight,
  exact: weight / 2,
  points: Math.round(weight / 2),
})

const rustRow = topicRow(7, 'Rust', 20, 0.9)
const politicsRow = topicRow(3, 'Politics', -30)
const cryptoRow = topicRow(4, 'Crypto', -15)
const goRow = topicRow(8, 'Go', 10)

function article(feedback: ArticleFeedback | null = null, topics: TopicBreakdownRow[] = [rustRow]): Article {
  return {
    id: 1,
    feedId: 1,
    guid: 'g-1',
    title: 'Rust news',
    url: 'https://example.com/1',
    author: null,
    content: null,
    summary: 'Summary',
    imageUrl: null,
    publishedAt: '2026-09-23T00:00:00Z',
    fetchedAt: '2026-09-23T00:00:00Z',
    read: false,
    starred: false,
    interestScore: 70,
    interestBreakdown: {
      raw: 70,
      total: 70,
      display: 70,
      rows: [{ kind: 'PROFILE', levelIndex: 3, exact: 50, points: 50 }, ...topics],
      nonMatching: [],
    },
    feedback,
  }
}

const rustEffect = (before: number, after: number): TopicEffect => ({
  topicId: 7,
  name: 'Rust',
  before,
  after,
  baseWeight: 20,
  learned: after - 20,
  limit: 'NONE',
})

const up: ArticleFeedback = { vote: 1, narrowed: false, topics: [] }
const down: ArticleFeedback = { vote: -1, narrowed: false, topics: [] }
const narrowedTo = (...picks: TopicBreakdownRow[]): ArticleFeedback => ({
  vote: -1,
  narrowed: true,
  topics: picks.map((p) => ({ topicId: p.topicId, name: p.name })),
})

function result(
  feedback: ArticleFeedback | null,
  effects: TopicEffect[],
  topics: TopicBreakdownRow[] = [rustRow]
): FeedbackResult {
  return { article: article(feedback, topics), scored: true, effects }
}

const threeTopics = [politicsRow, rustRow, goRow]
const narrowControl = () => document.querySelector<HTMLButtonElement>('.narrow-toggle')

/** The bar for the selected article, like ReadingPane; `withContent` adds a focusable .reading-content. */
function Harness({ withContent }: { withContent: boolean }) {
  const id = useUIStore((s) => s.selectedArticleId)
  const { data } = useArticle(id)
  return (
    <>
      {data ? <FeedbackBar article={data} /> : null}
      {withContent && <div className="reading-content" tabIndex={-1} />}
    </>
  )
}

function renderBar(client?: QueryClient, withContent = false) {
  const qc = client ?? createQueryClient()
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/']}>
        <Harness withContent={withContent} />
      </MemoryRouter>
    </QueryClientProvider>
  )
  return qc
}

const toasts = () => useToastStore.getState().toasts.map((t) => `${t.type}:${t.message}`)

describe('FeedbackBar', () => {
  beforeEach(() => {
    calls = []
    routes = {}
    useToastStore.setState({ toasts: [] })
    useFeedbackStore.setState({ narrowOpen: false })
    useUIStore.setState({ selectedArticleId: 1 })
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      calls.push({ method, url, body: init?.body as string | undefined })
      const handler = routes[`${method} ${url}`]
      if (!handler) return new Response(null, { status: 404 })
      return jsonResponse(await handler(init))
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    useUIStore.setState({ selectedArticleId: null })
  })

  it('upVoteSavesAndShowsTheEffect', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(null) }))
    const put = deferred<Reply>()
    route('PUT', '/api/articles/1/feedback', () => put.promise)
    const qc = renderBar()

    const upBtn = await screen.findByRole('button', { name: 'Thumbs up' })
    expect(upBtn).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(upBtn)

    // The intent is in the cache at once and shows before the PUT resolves.
    expect(qc.getQueryData<Article>(['article', 1])?.feedback?.vote).toBe(1)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Thumbs up' })).toHaveAttribute('aria-pressed', 'true')
    )
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    const putCall = calls.find((c) => c.method === 'PUT')!
    expect(putCall.url).toBe('/api/articles/1/feedback')
    expect(JSON.parse(putCall.body!)).toEqual({ vote: 1, topicIds: null })

    put.resolve({ status: 200, body: result(up, [rustEffect(20, 22)]) })
    await waitFor(() => expect(toasts()).toEqual(['success:👍 Rust +2.0']))
    expect(qc.getQueryData<Article>(['article', 1])?.feedback).toEqual(up)
  })

  it('pressingTheActiveVoteRemovesIt', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(up) }))
    const del = deferred<Reply>()
    route('DELETE', '/api/articles/1/feedback', () => del.promise)
    const qc = renderBar()

    const upBtn = await screen.findByRole('button', { name: 'Thumbs up' })
    expect(upBtn).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(upBtn)
    expect(qc.getQueryData<Article>(['article', 1])?.feedback).toBeNull()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Thumbs up' })).toHaveAttribute('aria-pressed', 'false')
    )

    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true))
    expect(calls.find((c) => c.method === 'DELETE')!.url).toBe('/api/articles/1/feedback')
    del.resolve({ status: 200, body: result(null, [rustEffect(22, 20)]) })
    await waitFor(() => expect(toasts()).toEqual(['success:Vote removed · Rust −2.0']))
  })

  it('pressingTheOtherVoteFlipsIt', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(up) }))
    const put = deferred<Reply>()
    route('PUT', '/api/articles/1/feedback', () => put.promise)
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Thumbs down' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Thumbs down' })).toHaveAttribute('aria-pressed', 'true')
    )
    expect(screen.getByRole('button', { name: 'Thumbs up' })).toHaveAttribute('aria-pressed', 'false')

    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    put.resolve({ status: 200, body: result(down, [rustEffect(22, 18)]) })
    await waitFor(() => expect(toasts()).toEqual(['success:👎 Rust −4.0']))
    const putCall = calls.find((c) => c.method === 'PUT')!
    expect(JSON.parse(putCall.body!)).toEqual({ vote: -1, topicIds: null })
    expect(screen.getByRole('button', { name: 'Thumbs down' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Thumbs up' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('voteHasNoSideEffects', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(null) }))
    route('PUT', '/api/articles/1/feedback', () => ({
      status: 200,
      body: result(up, [rustEffect(20, 22)]),
    }))
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Thumbs up' }))
    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(calls.filter((c) => c.method === 'PATCH')).toEqual([])
    expect(useUIStore.getState().selectedArticleId).toBe(1)
  })

  it('unscoredVoteToastSaysItCountsLater', async () => {
    route('GET', '/api/articles/1', () => ({
      status: 200,
      body: { ...article(null), interestScore: null, interestBreakdown: undefined },
    }))
    route('PUT', '/api/articles/1/feedback', () => ({
      status: 200,
      body: { ...result(up, []), scored: false },
    }))
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Thumbs up' }))
    await waitFor(() =>
      expect(toasts()).toEqual(['success:👍 Saved — counts once this article is scored'])
    )
  })

  it('noMatchVoteToastSaysNoTopicsMatched', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(null, []) }))
    route('PUT', '/api/articles/1/feedback', () => ({
      status: 200,
      body: result(down, [], []),
    }))
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Thumbs down' }))
    await waitFor(() => expect(toasts()).toEqual(['success:👎 Saved · No topics matched']))
  })

  it('buttonsCarryLabelsAndTitles', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(null) }))
    const qc = renderBar()

    const upBtn = await screen.findByRole('button', { name: 'Thumbs up' })
    const downBtn = screen.getByRole('button', { name: 'Thumbs down' })
    expect(upBtn).toHaveTextContent('👍 Up')
    expect(downBtn).toHaveTextContent('👎 Down')
    expect(upBtn).toHaveAttribute('title', 'Thumbs up (u)')
    expect(downBtn).toHaveAttribute('title', 'Thumbs down (d)')

    qc.setQueryData<Article>(['article', 1], (old) => (old ? { ...old, feedback: up } : old))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Thumbs up' })).toHaveAttribute(
        'title',
        'Remove thumbs up (u)'
      )
    )
    expect(screen.getByRole('button', { name: 'Thumbs up' })).toHaveTextContent('👍 Up')
    expect(screen.getByRole('button', { name: 'Thumbs down' })).toHaveAttribute('title', 'Thumbs down (d)')

    qc.setQueryData<Article>(['article', 1], (old) => (old ? { ...old, feedback: down } : old))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Thumbs down' })).toHaveAttribute(
        'title',
        'Remove thumbs down (d)'
      )
    )
    expect(screen.getByRole('button', { name: 'Thumbs down' })).toHaveTextContent('👎 Down')
  })
  it('narrowControlShowsForADownVoteOnSeveralTopics', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(down, threeTopics) }))
    const qc = renderBar()

    const control = await screen.findByRole('button', { name: 'Choose topics to penalize' })
    expect(control).toHaveTextContent('Narrow…')
    expect(control).toHaveAttribute('aria-haspopup', 'dialog')
    expect(control).toHaveAttribute('aria-controls', 'narrow-picker')
    expect(control).toHaveAttribute('aria-expanded', 'false')
    expect(control).toHaveClass('toolbar-btn', 'narrow-toggle')
    expect(control.previousElementSibling).toHaveAccessibleName('Thumbs down')

    qc.setQueryData<Article>(['article', 1], article(up, threeTopics))
    await waitFor(() => expect(narrowControl()).toBeNull())

    qc.setQueryData<Article>(['article', 1], article(down, [rustRow]))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Thumbs down' })).toHaveAttribute('aria-pressed', 'true'))
    expect(narrowControl()).toBeNull()

    qc.setQueryData<Article>(['article', 1], article(narrowedTo(rustRow), [rustRow]))
    await waitFor(() => expect(narrowControl()).not.toBeNull())
  })

  it('applyingASubsetNarrowsTheVote', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(down, threeTopics) }))
    const put = deferred<Reply>()
    route('PUT', '/api/articles/1/feedback', () => put.promise)
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Choose topics to penalize' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Rust/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Go/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1 topic' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(JSON.parse(calls.find((c) => c.method === 'PUT')!.body!)).toEqual({
      vote: -1,
      topicIds: [politicsRow.topicId],
    })
    // The intent shows before the response.
    expect(narrowControl()).toHaveTextContent('Politics only')

    put.resolve({
      status: 200,
      body: result(narrowedTo(politicsRow), [rustEffect(16, 20)], threeTopics),
    })
    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(toasts()[0].startsWith('success:👎 Narrowed · ')).toBe(true)
    expect(narrowControl()).toHaveTextContent('Politics only')
  })

  it('narrowedLabels', async () => {
    const fourTopics = [politicsRow, cryptoRow, rustRow, goRow]
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(narrowedTo(politicsRow), fourTopics) }))
    const qc = renderBar()

    const one = await screen.findByRole('button', { name: 'Penalizing Politics only. Change topics' })
    expect(one).toHaveTextContent('Politics only')
    expect(one).toHaveAttribute('title', 'Politics only')
    expect(one).toHaveClass('narrowed')
    expect(one.querySelector('.narrow-toggle-name')).toHaveTextContent('Politics')

    qc.setQueryData<Article>(['article', 1], article(narrowedTo(politicsRow, cryptoRow), fourTopics))
    await waitFor(() => expect(narrowControl()).toHaveTextContent('Politics, Crypto only'))
    expect(narrowControl()).toHaveAttribute('title', 'Politics, Crypto only')
    expect(narrowControl()!.querySelectorAll('.narrow-toggle-name')).toHaveLength(2)

    qc.setQueryData<Article>(['article', 1], article(narrowedTo(politicsRow, cryptoRow, rustRow), fourTopics))
    await waitFor(() => expect(narrowControl()).toHaveTextContent('3 of 4 topics'))
    expect(narrowControl()).toHaveAttribute('title', '3 of 4 topics')

    const gone = topicRow(99, 'Deleted', -10)
    qc.setQueryData<Article>(['article', 1], article(narrowedTo(gone), fourTopics))
    await waitFor(() => expect(narrowControl()).toHaveTextContent('No topics'))
    expect(narrowControl()).toHaveAccessibleName('Penalizing No topics. Change topics')
  })

  it('clickingTheNarrowedLabelReopensThePicker', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(narrowedTo(politicsRow), threeTopics) }))
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Penalizing Politics only. Change topics' }))
    expect(screen.getByRole('dialog', { name: 'Choose topics to penalize' })).toBeInTheDocument()
    expect(narrowControl()).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('checkbox', { name: /Politics/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /Rust/ })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: /Go/ })).not.toBeChecked()
  })

  it('pressingDownAgainRemovesTheWholeVote', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(narrowedTo(politicsRow), threeTopics) }))
    route('DELETE', '/api/articles/1/feedback', () => ({
      status: 200,
      body: result(null, [], threeTopics),
    }))
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Thumbs down' }))
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true))
    expect(calls.find((c) => c.method === 'DELETE')!.url).toBe('/api/articles/1/feedback')
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
    expect(narrowControl()).toBeNull()
  })

  it('flippingToUpClearsNarrowing', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(narrowedTo(politicsRow), threeTopics) }))
    const put = deferred<Reply>()
    route('PUT', '/api/articles/1/feedback', () => put.promise)
    renderBar()

    await screen.findByRole('button', { name: 'Penalizing Politics only. Change topics' })
    fireEvent.click(screen.getByRole('button', { name: 'Thumbs up' }))
    await waitFor(() => expect(narrowControl()).toBeNull())
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(JSON.parse(calls.find((c) => c.method === 'PUT')!.body!)).toEqual({ vote: 1, topicIds: null })
    put.resolve({ status: 200, body: result(up, [], threeTopics) })
    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(narrowControl()).toBeNull()
  })
  it('pickerClosesWhenTheArticleChanges', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(down, threeTopics) }))
    route('GET', '/api/articles/2', () => ({ status: 200, body: { ...article(down, threeTopics), id: 2 } }))
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Choose topics to penalize' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    act(() => useUIStore.setState({ selectedArticleId: 2 }))
    await waitFor(() => expect(calls.some((c) => c.url === '/api/articles/2')).toBe(true))
    await screen.findByRole('button', { name: 'Choose topics to penalize' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(useFeedbackStore.getState().narrowOpen).toBe(false)
    expect(calls.filter((c) => c.method !== 'GET')).toEqual([])
  })

  it('pickerClosesWhenTheVoteIsNoLongerDown', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(down, threeTopics) }))
    const put = deferred<Reply>()
    route('PUT', '/api/articles/1/feedback', () => put.promise)
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Choose topics to penalize' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thumbs up' }))

    await waitFor(() => expect(useFeedbackStore.getState().narrowOpen).toBe(false))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('focusReturnsToTheNarrowControl', async () => {
    route('GET', '/api/articles/1', () => ({ status: 200, body: article(down, threeTopics) }))
    route('DELETE', '/api/articles/1/feedback', () => new Promise<Reply>(() => {}))
    renderBar(undefined, true)

    fireEvent.click(await screen.findByRole('button', { name: 'Choose topics to penalize' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(narrowControl()).toHaveFocus())
    expect(screen.queryByRole('dialog')).toBeNull()

    // Removing the vote hides the control, so focus lands on the reading content.
    fireEvent.click(narrowControl()!)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thumbs down' }))
    await waitFor(() => expect(narrowControl()).toBeNull())
    await waitFor(() => expect(document.querySelector('.reading-content')).toHaveFocus())
  })

  it('rejectedNarrowingRevertsTheLabel', async () => {
    let served = 0
    route('GET', '/api/articles/1', () => {
      served += 1
      return { status: 200, body: article(down, threeTopics) }
    })
    const put = deferred<Reply>()
    route('PUT', '/api/articles/1/feedback', () => put.promise)
    renderBar()

    fireEvent.click(await screen.findByRole('button', { name: 'Choose topics to penalize' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Rust/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Go/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1 topic' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(narrowControl()).toHaveTextContent('Politics only'))
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    put.resolve({ status: 400, body: { message: 'topic 8 not matched' } })
    await waitFor(() =>
      expect(toasts()).toEqual([
        "error:Couldn't narrow the vote because this article's topics changed. Open Narrow… and pick again.",
      ])
    )
    await waitFor(() => expect(served).toBe(2))
    await waitFor(() => expect(narrowControl()).toHaveTextContent('Narrow…'))
    expect(narrowControl()).toHaveAccessibleName('Choose topics to penalize')
  })
})
