import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { createElement, type ReactNode } from 'react'
import { createQueryClient } from '../queryClient'
import { useToastStore } from '../components/Toast'
import { useOpenOriginal } from './useEngagement'

let qc: QueryClient
let openSpy: ReturnType<typeof vi.spyOn>
let fetchSpy: ReturnType<typeof vi.spyOn>

function renderOpen(path = '/') {
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(
      QueryClientProvider,
      { client: qc },
      createElement(MemoryRouter, { initialEntries: [path] }, children),
    )
  return renderHook(() => useOpenOriginal(), { wrapper }).result
}

const ARTICLE_7 = {
  id: 7,
  feedId: 1,
  guid: 'g-7',
  title: 'Article 7',
  url: 'https://example.com/7',
  author: null,
  content: null,
  summary: 'Summary',
  imageUrl: null,
  publishedAt: '2026-09-23T00:00:00Z',
  fetchedAt: '2026-09-23T00:00:00Z',
  read: false,
  starred: false,
  interestScore: 80,
  engagement: ['OPEN_ORIGINAL'],
}

/**
 * Routes by method and URL: each PUT answers the next of `putStatuses` (the last repeats),
 * GET /api/articles/7 answers `getStatus` (the article as JSON when 200).
 */
function respondWith(putStatuses: number[], getStatus = 200) {
  let i = 0
  fetchSpy.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    if (method === 'GET' && String(input) === '/api/articles/7') {
      if (getStatus !== 200) return new Response(null, { status: getStatus })
      return new Response(JSON.stringify(ARTICLE_7), { headers: { 'Content-Type': 'application/json' } })
    }
    const status = putStatuses[Math.min(i++, putStatuses.length - 1)]
    return new Response(null, { status })
  })
}

function callsOf(method: string, url: string): { call: [string, RequestInit | undefined]; order: number }[] {
  const calls = fetchSpy.mock.calls as [string, RequestInit | undefined][]
  const orders = fetchSpy.mock.invocationCallOrder as number[]
  return calls
    .map((call, n) => ({ call, order: orders[n] }))
    .filter(({ call: [u, init] }) => (init?.method ?? 'GET') === method && String(u) === url)
}

const puts = () => callsOf('PUT', '/api/articles/7/engagement/open')
const gets = () => callsOf('GET', '/api/articles/7')

/** Lets the fire-and-forget chain settle, so a late invalidation or toast would show up. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

beforeEach(() => {
  qc = createQueryClient()
  useToastStore.setState({ toasts: [] })
  openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
  fetchSpy = vi.spyOn(globalThis, 'fetch')
  respondWith([204])
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useOpenOriginal', () => {
  it('opensFirstThenSendsABodylessPut', async () => {
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    expect(openSpy).toHaveBeenCalledWith('https://example.com/7', '_blank', 'noopener')
    await waitFor(() => expect(puts()).toHaveLength(1))
    const [{ call, order }] = puts()
    expect(openSpy.mock.invocationCallOrder[0]).toBeLessThan(order)

    const [url, init] = call as [string, RequestInit]
    expect(url).toBe('/api/articles/7/engagement/open')
    expect(init.method).toBe('PUT')
    expect(init.body).toBeUndefined()
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)
  })

  it('successRefetchesTheArticleAndRefreshesLearnedAndLists', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    await waitFor(() => expect(gets()).toHaveLength(1))
    expect(puts()[0].order).toBeLessThan(gets()[0].order)
    await settle()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['interest', 'learned'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['articles'] })
    for (const [filters] of invalidateSpy.mock.calls) {
      const key = (filters as { queryKey?: unknown[] } | undefined)?.queryKey
      expect(key?.[0]).not.toBe('priority')
    }
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })

  it('aNetworkFailureIsSilent', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    fetchSpy.mockRejectedValue(new TypeError('Failed to fetch'))
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    expect(openSpy).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    await settle()
    expect(useToastStore.getState().toasts).toHaveLength(0)
    expect(invalidateSpy).not.toHaveBeenCalled()
    expect(gets()).toHaveLength(0)
  })

  it('aServerErrorIsSilent', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    respondWith([404, 500])
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    expect(openSpy).toHaveBeenCalledTimes(2)
    await waitFor(() => expect(puts()).toHaveLength(2))
    await settle()
    expect(useToastStore.getState().toasts).toHaveLength(0)
    expect(invalidateSpy).not.toHaveBeenCalled()
    expect(gets()).toHaveLength(0)
  })

  it('aFailedRefetchIsSilent', async () => {
    respondWith([204], 500)
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    await waitFor(() => expect(gets()).toHaveLength(1))
    await settle()
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })

  it('everyOpenSendsItsOwnPut', async () => {
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    await waitFor(() => expect(puts()).toHaveLength(2))
  })

  it('aBlankUrlOpensAndRecordsNothing', async () => {
    const open = renderOpen()
    act(() => open.current({ id: 7, url: '' }))
    act(() => open.current({ id: 7, url: '   ' }))

    await settle()
    expect(openSpy).not.toHaveBeenCalled()
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
