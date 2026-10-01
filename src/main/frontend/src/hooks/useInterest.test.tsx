import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/interest', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/interest')>()),
  interestApi: {
    getStatus: vi.fn(),
    createTopic: vi.fn(),
    updateTopic: vi.fn(),
    deleteTopic: vi.fn(),
    rescore: vi.fn(),
    saveProfile: vi.fn(),
  },
}))

import { act, render, renderHook, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import {
  SUGGESTIONS_KEY,
  useCreateInterestTopic,
  useDeleteInterestTopic,
  useInterestTiers,
  useRescoreUnread,
  useSaveInterestProfile,
  useUpdateInterestTopic,
} from './useInterest'
import { interestApi, type InterestStatus, type InterestTopic } from '../api/interest'
import { TierContext } from '../utils/interest'
import { InterestBadge } from '../components/InterestBadge'

function createClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function createWrapper(qc: QueryClient = createClient()) {
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}

function status(overrides: Partial<InterestStatus> = {}): InterestStatus {
  return {
    configured: true,
    breakerState: 'CLOSED',
    coldStart: false,
    eligibleUnscored: 0,
    failed: 0,
    ...overrides,
  }
}

/** Does exactly what MainLayout does: read the served tiers and provide them to the badges. */
function Harness({ scores }: { scores: number[] }) {
  const tiers = useInterestTiers()
  return (
    <TierContext.Provider value={tiers}>
      {scores.map((score) => (
        <div key={score} data-testid={`badge-${score}`}>
          <InterestBadge score={score} />
        </div>
      ))}
    </TierContext.Provider>
  )
}

function badgeFor(score: number) {
  return screen.getByTestId(`badge-${score}`).querySelector('.interest-badge')
}

describe('useInterestTiers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('servedTiersColorTheBadge', async () => {
    vi.mocked(interestApi.getStatus).mockResolvedValue(
      status({ tiers: { high: 50, neutral: 20 } }),
    )

    render(<Harness scores={[55, 20, 19]} />, { wrapper: createWrapper() })

    // Before status resolves the 70/40 fallback applies: 55 is neutral.
    expect(badgeFor(55)).toHaveClass('tier-neutral')

    await waitFor(() => expect(badgeFor(55)).toHaveClass('tier-high'))
    expect(badgeFor(20)).toHaveClass('tier-neutral')
    expect(badgeFor(19)).toHaveClass('tier-low')
  })

  it('fallsBackTo70And40WithoutTiers', async () => {
    vi.mocked(interestApi.getStatus).mockResolvedValue(status())
    const qc = createClient()

    const { result } = renderHook(() => useInterestTiers(), { wrapper: createWrapper(qc) })

    await waitFor(() => expect(interestApi.getStatus).toHaveBeenCalled())
    await waitFor(() =>
      expect(qc.getQueryState(['interest', 'status'])?.status).toBe('success'),
    )
    expect(result.current).toEqual({ high: 70, neutral: 40 })
  })

  it('fallsBackWhenStatusFails', async () => {
    vi.mocked(interestApi.getStatus).mockRejectedValue(new Error('boom'))
    const qc = createClient()

    const { result } = renderHook(() => useInterestTiers(), { wrapper: createWrapper(qc) })

    await waitFor(() => expect(interestApi.getStatus).toHaveBeenCalled())
    await waitFor(() =>
      expect(qc.getQueryState(['interest', 'status'])?.status).toBe('error'),
    )
    expect(result.current).toEqual({ high: 70, neutral: 40 })
  })

  it('fetchesStatusOnceAndNeverRefetches', async () => {
    vi.mocked(interestApi.getStatus).mockResolvedValue(
      status({ tiers: { high: 60, neutral: 30 } }),
    )
    const wrapper = createWrapper()

    const first = renderHook(() => useInterestTiers(), { wrapper })
    await waitFor(() => expect(first.result.current).toEqual({ high: 60, neutral: 30 }))

    const second = renderHook(() => useInterestTiers(), { wrapper })
    expect(second.result.current).toEqual({ high: 60, neutral: 30 })

    expect(interestApi.getStatus).toHaveBeenCalledTimes(1)
  })
})

describe('suggestions freshness', () => {
  const input = { name: 'Rust', description: 'The Rust language', weight: 20 }

  function savedTopic(id: number): InterestTopic {
    return {
      ...input,
      id,
      version: 1,
      createdAt: '2026-09-23T00:00:00Z',
      updatedAt: '2026-09-23T00:00:00Z',
    }
  }

  function seeded() {
    const qc = createClient()
    qc.setQueryData(SUGGESTIONS_KEY, { items: [], total: 0 })
    return qc
  }

  const suggestionsStale = (qc: QueryClient) => qc.getQueryState(SUGGESTIONS_KEY)?.isInvalidated

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('topicCreateMarksSuggestionsStale', async () => {
    vi.mocked(interestApi.createTopic).mockResolvedValue(savedTopic(3))
    const qc = seeded()
    const { result } = renderHook(() => useCreateInterestTopic(), { wrapper: createWrapper(qc) })

    await act(() => result.current.mutateAsync(input))

    expect(suggestionsStale(qc)).toBe(true)
  })

  it('topicDeleteMarksSuggestionsStale', async () => {
    vi.mocked(interestApi.deleteTopic).mockResolvedValue(undefined)
    const qc = seeded()
    const { result } = renderHook(() => useDeleteInterestTopic(), { wrapper: createWrapper(qc) })

    await act(() => result.current.mutateAsync(3))

    expect(suggestionsStale(qc)).toBe(true)
  })

  it('rescoreMarksSuggestionsStale', async () => {
    vi.mocked(interestApi.rescore).mockResolvedValue({ count: 12, windowDays: 14 })
    const qc = seeded()
    const { result } = renderHook(() => useRescoreUnread(), { wrapper: createWrapper(qc) })

    await act(() => result.current.mutateAsync(undefined))

    expect(suggestionsStale(qc)).toBe(true)
  })

  it('topicUpdateAndProfileSaveLeaveSuggestionsAlone', async () => {
    vi.mocked(interestApi.updateTopic).mockResolvedValue(savedTopic(3))
    vi.mocked(interestApi.saveProfile).mockResolvedValue({
      id: 1,
      profileText: 'Rust and Go',
      version: 2,
      updatedAt: '2026-09-23T00:00:00Z',
    })
    const qc = seeded()
    const update = renderHook(() => useUpdateInterestTopic(), { wrapper: createWrapper(qc) })
    const save = renderHook(() => useSaveInterestProfile(), { wrapper: createWrapper(qc) })

    await act(() => update.result.current.mutateAsync({ id: 3, input }))
    await act(() => save.result.current.mutateAsync('Rust and Go'))

    expect(interestApi.updateTopic).toHaveBeenCalledTimes(1)
    expect(interestApi.saveProfile).toHaveBeenCalledTimes(1)
    expect(suggestionsStale(qc)).toBe(false)
  })
})
