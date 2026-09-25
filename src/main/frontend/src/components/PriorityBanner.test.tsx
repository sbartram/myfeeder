import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { InterestStatus } from '../api/interest'
import { PriorityBanner } from './PriorityBanner'

let mockStatus: InterestStatus | undefined

vi.mock('../hooks/useInterest', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useInterest')>()),
  useInterestStatus: () => ({ data: mockStatus }),
}))

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

function banner(container: HTMLElement) {
  return container.querySelector('.priority-banner')
}

describe('PriorityBanner', () => {
  beforeEach(() => {
    mockStatus = undefined
  })

  it('rendersNothingWithoutStatus', () => {
    const { container } = render(<PriorityBanner />)
    expect(container).toBeEmptyDOMElement()
  })

  it('notConfiguredWinsOverEverything', () => {
    mockStatus = status({ configured: false, coldStart: true, breakerState: 'OPEN', eligibleUnscored: 50 })
    const { container } = render(<PriorityBanner onSetUpInterests={() => {}} />)
    expect(banner(container)).toHaveTextContent(
      "Scoring isn't set up. Interest scoring is not configured by the administrator, so articles are listed by date.",
    )
    expect(container.querySelectorAll('.priority-banner')).toHaveLength(1)
    expect(screen.queryByText(/Nothing to rank yet/)).toBeNull()
    expect(screen.queryByText(/Scoring paused/)).toBeNull()
    expect(screen.queryByText(/waiting/)).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('coldStartWinsOverPaused', () => {
    mockStatus = status({ coldStart: true, breakerState: 'OPEN', eligibleUnscored: 5 })
    const { container, unmount } = render(<PriorityBanner onSetUpInterests={() => {}} />)
    expect(banner(container)).toHaveTextContent(
      'Nothing to rank yet. Write a profile or add a topic so articles can be scored.',
    )
    expect(screen.queryByText(/Scoring paused/)).toBeNull()
    expect(screen.getByRole('button', { name: 'Set up interests' })).toBeInTheDocument()
    unmount()

    render(<PriorityBanner />)
    expect(screen.getByText('Nothing to rank yet.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('coldStartButtonCallsTheCallback', () => {
    mockStatus = status({ coldStart: true })
    const onSetUpInterests = vi.fn()
    render(<PriorityBanner onSetUpInterests={onSetUpInterests} />)
    fireEvent.click(screen.getByRole('button', { name: 'Set up interests' }))
    expect(onSetUpInterests).toHaveBeenCalledTimes(1)
  })

  it('pausedShowsTheWaitingCount', () => {
    mockStatus = status({ breakerState: 'OPEN', eligibleUnscored: 312 })
    const { container } = render(<PriorityBanner />)
    expect(banner(container)).toHaveTextContent(
      '⏸ Scoring paused — 312 articles waiting. It resumes automatically.',
    )
    expect(screen.getByText('Scoring paused').tagName).toBe('STRONG')
  })

  it('pausedWithNothingWaiting', () => {
    mockStatus = status({ breakerState: 'FORCED_OPEN', eligibleUnscored: 0 })
    const { container } = render(<PriorityBanner />)
    expect(banner(container)).toHaveTextContent('⏸ Scoring paused. It resumes automatically.')
    expect(screen.getByText('Scoring paused.').tagName).toBe('STRONG')
  })

  it('halfOpenIsNotPaused', () => {
    mockStatus = status({ breakerState: 'HALF_OPEN', eligibleUnscored: 1 })
    const { container } = render(<PriorityBanner />)
    expect(banner(container)).toHaveTextContent('1 article waiting to be scored.')
    expect(screen.queryByText(/Scoring paused/)).toBeNull()
    expect(screen.getByText('1 article waiting to be scored.').tagName).toBe('STRONG')
  })

  it('waitingUsesThousandsSeparators', () => {
    mockStatus = status({ eligibleUnscored: 1204 })
    const { container } = render(<PriorityBanner />)
    expect(banner(container)).toHaveTextContent('1,204 articles waiting to be scored.')
  })

  it('noBannerWhenEverythingIsScored', () => {
    mockStatus = status()
    const { container } = render(<PriorityBanner />)
    expect(container).toBeEmptyDOMElement()
  })

  it('failedCountIsNeverShown', () => {
    mockStatus = status({ eligibleUnscored: 3, failed: 7 })
    const { container } = render(<PriorityBanner />)
    expect(banner(container)).toHaveTextContent('3 articles waiting to be scored.')
    expect(container.textContent).not.toContain('7')
  })

  it('bannerIsAStatusRegion', () => {
    mockStatus = status({ eligibleUnscored: 2 })
    const { unmount } = render(<PriorityBanner />)
    const waiting = screen.getByRole('status')
    expect(waiting).toHaveClass('priority-banner')
    expect(waiting).not.toHaveClass('cold-start')
    unmount()

    mockStatus = status({ coldStart: true })
    render(<PriorityBanner />)
    const cold = screen.getByRole('status')
    expect(cold).toHaveClass('priority-banner')
    expect(cold).toHaveClass('cold-start')
  })
})
