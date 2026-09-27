import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { WhyBreakdown } from './WhyBreakdown'
import type { BreakdownRow, InterestBreakdown, NonMatchingTopic } from '../types'

const profile = (levelIndex: number, points: number): BreakdownRow => ({
  kind: 'PROFILE',
  levelIndex,
  exact: points,
  points,
})

const topic = (
  name: string,
  opts: {
    noul?: number
    hinge: number
    weight: number
    exact?: number
    points: number
    baseWeight?: number
    learnedWeight?: number
  }
): BreakdownRow => ({
  kind: 'TOPIC',
  topicId: name.length,
  name,
  noul: opts.noul ?? 0.5 + opts.hinge / 2,
  hinge: opts.hinge,
  weight: opts.weight,
  exact: opts.exact ?? opts.points,
  points: opts.points,
  ...(opts.baseWeight !== undefined ? { baseWeight: opts.baseWeight } : {}),
  ...(opts.learnedWeight !== undefined ? { learnedWeight: opts.learnedWeight } : {}),
})

const nonMatch = (name: string, noul: number, topicId = 90): NonMatchingTopic => ({ topicId, name, noul })

const breakdown = (
  rows: BreakdownRow[],
  total: number,
  display: number,
  nonMatching: NonMatchingTopic[] = []
): InterestBreakdown => ({ raw: total, total, display, rows, nonMatching })

const mockRows = (): BreakdownRow[] => [
  profile(3, 64),
  topic('Rust', { noul: 0.93, hinge: 0.86, weight: 20, exact: 17.2, points: 17 }),
  topic('WebAssembly', { hinge: 0.5, weight: 14, points: 7 }),
  topic('Politics', { hinge: 0.2, weight: -30, points: -6 }),
]

const labels = (c: HTMLElement) => Array.from(c.querySelectorAll('.why-label')).map((e) => e.textContent)
const points = (c: HTMLElement) => Array.from(c.querySelectorAll('.why-points')).map((e) => e.textContent)

describe('WhyBreakdown', () => {
  it('rendersRowsWithSignedServerPoints', () => {
    const { container } = render(<WhyBreakdown breakdown={breakdown(mockRows(), 82, 82)} articleId={1} />)

    expect(container.querySelector('#why-breakdown')).not.toBeNull()
    expect(labels(container)).toEqual([
      'Profile match (Mainly)',
      'Rust  86% × +20',
      'WebAssembly  50% × +14',
      'Politics  20% × −30',
      'Score',
    ])
    expect(points(container)).toEqual(['+64', '+17', '+7', '−6', '82'])
  })

  it('topicRowTitleShowsTheMath', () => {
    const { container } = render(<WhyBreakdown breakdown={breakdown(mockRows(), 82, 82)} articleId={1} />)

    const rust = Array.from(container.querySelectorAll('.why-label')).find((e) => e.textContent?.startsWith('Rust'))!
    expect(rust.getAttribute('title')).toBe('Match 93% → counts 86% × +20 = +17.2 pts')
  })

  it('weightZeroRowShowsUnsignedZero', () => {
    const { container } = render(
      <WhyBreakdown
        breakdown={breakdown([profile(3, 64), topic('Zero', { hinge: 0.8, weight: 0, points: 0 })], 64, 64)}
        articleId={1}
      />
    )

    expect(labels(container)).toContain('Zero  80% × 0')
    expect(points(container)[1]).toBe('0')
  })

  it('cappedTotalLine', () => {
    const r1 = render(<WhyBreakdown breakdown={breakdown([profile(4, 133)], 133, 100)} articleId={1} />)
    expect(labels(r1.container).at(-1)).toBe('Total 133 → capped at 100')
    expect(points(r1.container).at(-1)).toBe('100')
    r1.unmount()

    const r2 = render(<WhyBreakdown breakdown={breakdown([profile(4, 101)], 101, 100)} articleId={1} />)
    expect(labels(r2.container).at(-1)).toBe('Total 101 → capped at 100')
    expect(points(r2.container).at(-1)).toBe('100')
    r2.unmount()

    const r3 = render(<WhyBreakdown breakdown={breakdown([profile(4, 100)], 100, 100)} articleId={1} />)
    expect(labels(r3.container).at(-1)).toBe('Score')
    expect(points(r3.container).at(-1)).toBe('100')
  })

  it('flooredTotalLine', () => {
    const neg = (total: number) => [topic('Politics', { hinge: 1, weight: -50, points: total })]
    const r1 = render(<WhyBreakdown breakdown={breakdown(neg(-30), -30, 0)} articleId={1} />)
    expect(labels(r1.container).at(-1)).toBe('Total −30 → floored at 0')
    expect(points(r1.container).at(-1)).toBe('0')
    r1.unmount()

    const r2 = render(<WhyBreakdown breakdown={breakdown(neg(-1), -1, 0)} articleId={1} />)
    expect(labels(r2.container).at(-1)).toBe('Total −1 → floored at 0')
    expect(points(r2.container).at(-1)).toBe('0')
    r2.unmount()

    const r3 = render(<WhyBreakdown breakdown={breakdown([profile(0, 0)], 0, 0)} articleId={1} />)
    expect(labels(r3.container).at(-1)).toBe('Score')
    expect(points(r3.container).at(-1)).toBe('0')
  })

  it('nothingContributed', () => {
    const { container } = render(<WhyBreakdown breakdown={breakdown([], 0, 0)} articleId={1} />)

    expect(screen.getByText('No profile or topic contributed to this score.')).toBeInTheDocument()
    expect(labels(container)).toEqual(['Score'])
    expect(points(container)).toEqual(['0'])
  })

  it('profileRowAbsentWithoutProfile', () => {
    render(
      <WhyBreakdown
        breakdown={breakdown([topic('Rust', { hinge: 0.86, weight: 20, points: 17 })], 17, 17)}
        articleId={1}
      />
    )

    expect(screen.queryByText(/Profile match/)).toBeNull()
  })

  it('footerSingularPluralAndOmitted', () => {
    const one = render(
      <WhyBreakdown breakdown={breakdown(mockRows(), 82, 82, [nonMatch('Gardening', 0.2)])} articleId={1} />
    )
    expect(screen.getByRole('button', { name: 'Show 1 non-matching topic ▸' })).toBeInTheDocument()
    one.unmount()

    const three = render(
      <WhyBreakdown
        breakdown={breakdown(mockRows(), 82, 82, [
          nonMatch('Gardening', 0.2, 91),
          nonMatch('Cooking', 0.1, 92),
          nonMatch('Sailing', 0.05, 93),
        ])}
        articleId={1}
      />
    )
    expect(screen.getByRole('button', { name: 'Show 3 non-matching topics ▸' })).toBeInTheDocument()
    three.unmount()

    render(<WhyBreakdown breakdown={breakdown(mockRows(), 82, 82)} articleId={1} />)
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.queryByText(/non-matching/)).toBeNull()
  })

  it('footerTogglesAndShowsMatch', () => {
    render(<WhyBreakdown breakdown={breakdown(mockRows(), 82, 82, [nonMatch('Gardening', 0.2)])} articleId={1} />)

    expect(screen.queryByText('Gardening')).toBeNull()
    const button = screen.getByRole('button', { name: 'Show 1 non-matching topic ▸' })
    expect(button.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(button)

    const open = screen.getByRole('button', { name: 'Hide non-matching topics ▾' })
    expect(open.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByText('Gardening')).toBeInTheDocument()
    expect(screen.getByText('20% match · 0')).toBeInTheDocument()
  })

  it('footerCollapsesOnArticleChange', () => {
    const b = breakdown(mockRows(), 82, 82, [nonMatch('Gardening', 0.2)])
    const { rerender } = render(<WhyBreakdown breakdown={b} articleId={1} />)
    fireEvent.click(screen.getByRole('button', { name: 'Show 1 non-matching topic ▸' }))
    expect(screen.getByText('Gardening')).toBeInTheDocument()

    rerender(<WhyBreakdown breakdown={b} articleId={2} />)

    expect(screen.getByRole('button', { name: 'Show 1 non-matching topic ▸' })).toBeInTheDocument()
    expect(screen.queryByText('Gardening')).toBeNull()
  })

  it('rendersServerPointsWithoutRecomputing', () => {
    const { container } = render(
      <WhyBreakdown
        breakdown={breakdown([topic('Rust', { hinge: 0.86, weight: 20, exact: 17.2, points: 18 })], 18, 18)}
        articleId={1}
      />
    )

    expect(points(container)).toEqual(['+18', '18'])
  })

  describe('learned part (D-11)', () => {
    const rust = () =>
      topic('Rust', { noul: 0.95, hinge: 0.9, weight: 21.8, exact: 19.62, points: 20, baseWeight: 20, learnedWeight: 1.8 })
    const politics = () =>
      topic('Politics', { hinge: 0.6, weight: -28.8, exact: -17.28, points: -17, baseWeight: -30, learnedWeight: 1.2 })
    const label = (c: HTMLElement, name: string) =>
      Array.from(c.querySelectorAll('.why-label')).find((e) => e.textContent?.startsWith(name))!

    it('learnedPartShownWhenItRoundsAboveZero', () => {
      const { container } = render(<WhyBreakdown breakdown={breakdown([rust()], 20, 20)} articleId={1} />)

      expect(labels(container)).toEqual(['Rust  90% × +21.8 (+20 +1.8 learned)', 'Score'])
      expect(points(container)).toEqual(['+20', '20'])
    })

    it('negativeLearnedPart', () => {
      const { container } = render(
        <WhyBreakdown breakdown={breakdown([profile(3, 64), politics()], 47, 47)} articleId={1} />
      )

      expect(labels(container)).toContain('Politics  60% × −28.8 (−30 +1.2 learned)')
    })

    it('phase5LabelUnchangedWithoutLearned', () => {
      const tiny = render(
        <WhyBreakdown
          breakdown={breakdown(
            [topic('Rust', { noul: 0.93, hinge: 0.86, weight: 20.04, exact: 17.2, points: 17, baseWeight: 20, learnedWeight: 0.04 })],
            17,
            17
          )}
          articleId={1}
        />
      )
      expect(labels(tiny.container)[0]).toBe('Rust  86% × +20')
      expect(label(tiny.container, 'Rust').getAttribute('title')).toBe('Match 93% → counts 86% × +20 = +17.2 pts')
      tiny.unmount()

      const absent = render(
        <WhyBreakdown
          breakdown={breakdown(
            [topic('Rust', { noul: 0.93, hinge: 0.86, weight: 20, exact: 17.2, points: 17 })],
            17,
            17
          )}
          articleId={1}
        />
      )
      expect(labels(absent.container)[0]).toBe('Rust  86% × +20')
      expect(label(absent.container, 'Rust').getAttribute('title')).toBe('Match 93% → counts 86% × +20 = +17.2 pts')
    })

    it('titleCarriesBaseAndLearned', () => {
      const { container } = render(<WhyBreakdown breakdown={breakdown([rust()], 20, 20)} articleId={1} />)

      const title = label(container, 'Rust').getAttribute('title')
      expect(title).toBe('Match 95% → counts 90% × +21.8 = +19.6 pts · base +20, learned +1.8')
      expect(title?.endsWith(' · base +20, learned +1.8')).toBe(true)
    })

    it('rowsStillSumToTheBadge', () => {
      const { container } = render(
        <WhyBreakdown
          breakdown={breakdown(
            [profile(3, 64), rust(), politics(), topic('Go', { hinge: 0.7, weight: 10, points: 4 })],
            71,
            71
          )}
          articleId={1}
        />
      )

      const values = points(container)
      const rows = values.slice(0, -1).map((v) => Number(v!.replace('−', '-')))
      expect(values).toEqual(['+64', '+20', '−17', '+4', '71'])
      expect(rows.reduce((a, b) => a + b, 0)).toBe(71)
      expect(labels(container)).toEqual([
        'Profile match (Mainly)',
        'Rust  90% × +21.8 (+20 +1.8 learned)',
        'Politics  60% × −28.8 (−30 +1.2 learned)',
        'Go  70% × +10',
        'Score',
      ])
    })
  })
})
