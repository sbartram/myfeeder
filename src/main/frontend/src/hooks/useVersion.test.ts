import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/version', () => ({
  versionApi: {
    get: vi.fn(),
  },
}))

import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useVersion } from './useVersion'
import { versionApi } from '../api/version'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}

describe('useVersion', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches the version from the API', async () => {
    vi.mocked(versionApi.get).mockResolvedValue({
      version: '0.1.9',
      buildTime: '2026-04-27T19:00:00Z',
    })

    const { result } = renderHook(() => useVersion(), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.version).toBe('0.1.9')
    expect(result.current.data?.buildTime).toBe('2026-04-27T19:00:00Z')
  })
})
