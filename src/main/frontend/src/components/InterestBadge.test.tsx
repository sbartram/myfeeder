import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { InterestBadge } from './InterestBadge'
import { TierContext } from '../utils/interest'

function badge(score: number | null | undefined) {
  const { container } = render(<InterestBadge score={score} />)
  return { container, el: container.querySelector('.interest-badge') }
}

describe('InterestBadge', () => {
  it('tiersAreInclusiveAtTheLowEnd', () => {
    const cases: [number, string][] = [
      [0, 'tier-low'],
      [39, 'tier-low'],
      [40, 'tier-neutral'],
      [69, 'tier-neutral'],
      [70, 'tier-high'],
      [100, 'tier-high'],
    ]
    for (const [score, tier] of cases) {
      const { el } = badge(score)
      expect(el, `score ${score}`).not.toBeNull()
      expect(el, `score ${score}`).toHaveClass(tier)
      expect(el).toHaveTextContent(String(score))
    }
  })

  it('usesTheProvidedTiers', () => {
    const cases: [number, string][] = [
      [0, 'tier-low'],
      [19, 'tier-low'],
      [20, 'tier-neutral'],
      [49, 'tier-neutral'],
      [50, 'tier-high'],
      [100, 'tier-high'],
    ]
    for (const [score, tier] of cases) {
      const { container } = render(
        <TierContext.Provider value={{ high: 50, neutral: 20 }}>
          <InterestBadge score={score} />
        </TierContext.Provider>,
      )
      const el = container.querySelector('.interest-badge')
      expect(el, `score ${score}`).not.toBeNull()
      expect(el, `score ${score}`).toHaveClass(tier)
    }
  })

  it('rendersNothingForUnscored', () => {
    for (const score of [null, undefined]) {
      const { container } = badge(score)
      expect(container).toBeEmptyDOMElement()
      expect(container.textContent).not.toContain('0')
    }
  })

  it('scoredZeroIsShown', () => {
    const { el } = badge(0)
    expect(el).not.toBeNull()
    expect(el).toHaveTextContent('0')
    expect(el).toHaveClass('tier-low')
  })

  it('labelsTheScore', () => {
    const { el } = badge(82)
    expect(el).toHaveAttribute('title', 'Interest score 82 of 100')
    expect(el).toHaveAttribute('aria-label', 'Interest score 82')
  })
})
