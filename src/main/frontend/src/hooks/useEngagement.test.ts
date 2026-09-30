import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { createQueryClient } from '../queryClient'
import { useToastStore } from '../components/Toast'
import { useOpenOriginal } from './useEngagement'

let qc: QueryClient
let openSpy: ReturnType<typeof vi.spyOn>
let fetchSpy: ReturnType<typeof vi.spyOn>

const wrapper = ({ children }: { children: ReactNode }) =>
  createElement(QueryClientProvider, { client: qc }, children)

function renderOpen() {
  return renderHook(() => useOpenOriginal(), { wrapper }).result
}

function respondWith(...statuses: number[]) {
  let i = 0
  fetchSpy.mockImplementation(async () => {
    const status = statuses[Math.min(i++, statuses.length - 1)]
    return new Response(null, { status })
  })
}

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
  respondWith(204)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useOpenOriginal', () => {
  it('opensFirstThenSendsABodylessPut', async () => {
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    expect(openSpy).toHaveBeenCalledWith('https://example.com/7', '_blank', 'noopener')
    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    expect(openSpy.mock.invocationCallOrder[0]).toBeLessThan(fetchSpy.mock.invocationCallOrder[0])

    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/articles/7/engagement/open')
    expect(init.method).toBe('PUT')
    expect(init.body).toBeUndefined()
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)
  })

  it('successInvalidatesOnlyTheExactByIdQuery', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['article', 7], exact: true })
    )
    await settle()
    expect(invalidateSpy).toHaveBeenCalledTimes(1)
    for (const [filters] of invalidateSpy.mock.calls) {
      const key = (filters as { queryKey?: unknown[] } | undefined)?.queryKey
      expect(key?.[0]).not.toBe('priority')
      expect(key?.[0]).not.toBe('articles')
    }
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
  })

  it('aServerErrorIsSilent', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    respondWith(404, 500)
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    expect(openSpy).toHaveBeenCalledTimes(2)
    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(2))
    await settle()
    expect(useToastStore.getState().toasts).toHaveLength(0)
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('everyOpenSendsItsOwnPut', async () => {
    const open = renderOpen()
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))
    act(() => open.current({ id: 7, url: 'https://example.com/7' }))

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(2))
    for (const [url] of fetchSpy.mock.calls) expect(url).toBe('/api/articles/7/engagement/open')
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
