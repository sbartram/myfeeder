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

function renderPriority() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const utils = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/priority']}>
        <Routes>
          <Route path="/priority" element={<PriorityList />} />
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
})
