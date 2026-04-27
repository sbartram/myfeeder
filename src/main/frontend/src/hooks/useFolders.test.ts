import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/folders', () => ({
  foldersApi: {
    reorder: vi.fn(),
  },
}))

import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useReorderFolders } from './useFolders'
import { foldersApi } from '../api/folders'
import type { Folder } from '../types'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children),
  }
}

const folder = (id: number, name: string, displayOrder: number): Folder =>
  ({ id, name, displayOrder, createdAt: '2026-01-01T00:00:00Z' })

describe('useReorderFolders', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('optimistically reorders the folders cache before the request resolves', async () => {
    const { qc, wrapper } = createWrapper()
    const initial: Folder[] = [folder(1, 'A', 0), folder(2, 'B', 1), folder(3, 'C', 2)]
    qc.setQueryData(['folders'], initial)

    let resolveReorder: (value: Folder[]) => void = () => {}
    vi.mocked(foldersApi.reorder).mockImplementation(
      () => new Promise<Folder[]>((resolve) => { resolveReorder = resolve }),
    )

    const { result } = renderHook(() => useReorderFolders(), { wrapper })
    act(() => { result.current.mutate([3, 1, 2]) })

    await waitFor(() => {
      const cached = qc.getQueryData<Folder[]>(['folders'])
      expect(cached?.map((f) => f.id)).toEqual([3, 1, 2])
      expect(cached?.map((f) => f.displayOrder)).toEqual([0, 1, 2])
    })

    resolveReorder([folder(3, 'C', 0), folder(1, 'A', 1), folder(2, 'B', 2)])
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('rolls back the cache when the request fails', async () => {
    const { qc, wrapper } = createWrapper()
    const initial: Folder[] = [folder(1, 'A', 0), folder(2, 'B', 1)]
    qc.setQueryData(['folders'], initial)

    vi.mocked(foldersApi.reorder).mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useReorderFolders(), { wrapper })
    act(() => { result.current.mutate([2, 1]) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    const cached = qc.getQueryData<Folder[]>(['folders'])
    expect(cached?.map((f) => f.id)).toEqual([1, 2])
  })
})
