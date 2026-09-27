import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/interest', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/interest')>()),
  interestApi: { getStatus: vi.fn() },
}))

import { render, renderHook, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useInterestTiers } from './useInterest'
import { interestApi, type InterestStatus } from '../api/interest'
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
