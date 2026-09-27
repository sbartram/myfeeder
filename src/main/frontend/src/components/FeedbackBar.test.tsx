import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { FeedbackBar } from './FeedbackBar'
import { createQueryClient } from '../queryClient'
import { useToastStore } from './Toast'
import { useUIStore } from '../stores/uiStore'
import { useArticle } from '../hooks/useArticles'
import type { Article, ArticleFeedback, FeedbackResult, TopicEffect } from '../types'

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

function article(feedback: ArticleFeedback | null = null): Article {
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
      rows: [
        { kind: 'PROFILE', levelIndex: 3, exact: 50, points: 50 },
        { kind: 'TOPIC', topicId: 7, name: 'Rust', noul: 0.9, hinge: 0.8, weight: 20, exact: 20, points: 20 },
      ],
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

function result(feedback: ArticleFeedback | null, effects: TopicEffect[]): FeedbackResult {
  return { article: article(feedback), scored: true, effects }
}

function Harness() {
  const { data } = useArticle(1)
  return data ? <FeedbackBar article={data} /> : null
}

function renderBar(client?: QueryClient) {
  const qc = client ?? createQueryClient()
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/']}>
        <Harness />
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
})
