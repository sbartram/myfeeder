import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the API and toast modules before importing the hook
vi.mock('../api/opml', () => ({
  opmlApi: {
    importFeeds: vi.fn(),
  },
}))

vi.mock('../components/Toast', () => ({
  useToastStore: Object.assign(
    (selector: (s: { addToast: ReturnType<typeof vi.fn> }) => unknown) =>
      selector({ addToast: vi.fn() }),
    { getState: () => ({ addToast: vi.fn() }) },
  ),
}))

import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useImportOpml } from './useOpml'
import { opmlApi } from '../api/opml'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children),
  }
}

describe('useImportOpml', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should invalidate feeds and folders queries on success', async () => {
    vi.mocked(opmlApi.importFeeds).mockResolvedValue({ created: 2, updated: 1, total: 3 })
    const { qc, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useImportOpml(), { wrapper })
    result.current.mutate(new File(['<opml/>'], 'test.opml'))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['feeds'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['folders'] })
  })
})
