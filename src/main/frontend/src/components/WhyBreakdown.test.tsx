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
    thumbsWeight?: number
    engagementWeight?: number
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
  ...(opts.thumbsWeight !== undefined ? { thumbsWeight: opts.thumbsWeight } : {}),
  ...(opts.engagementWeight !== undefined ? { engagementWeight: opts.engagementWeight } : {}),
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

  describe('votes and engagement parts (D-01..D-04)', () => {
    const rust = () =>
      topic('Rust', {
        noul: 0.95,
        hinge: 0.9,
        weight: 21.8,
        exact: 19.62,
        points: 20,
        baseWeight: 20,
        learnedWeight: 1.8,
        thumbsWeight: 1.8,
        engagementWeight: 0,
      })
    const politics = () =>
      topic('Politics', {
        hinge: 0.6,
        weight: -28.8,
        exact: -17.28,
        points: -17,
        baseWeight: -30,
        learnedWeight: 1.2,
        thumbsWeight: 1.2,
        engagementWeight: 0,
      })
    const both = () =>
      topic('Rust', {
        noul: 0.9,
        hinge: 0.8,
        weight: 13.5,
        exact: 10.8,
        points: 11,
        baseWeight: 10,
        learnedWeight: 3.5,
        thumbsWeight: 2.0,
        engagementWeight: 1.5,
      })
    const engagedOnly = () =>
      topic('Rust', {
        noul: 0.9,
        hinge: 0.8,
        weight: 11.54,
        exact: 9.23,
        points: 9,
        baseWeight: 10,
        learnedWeight: 1.54,
        thumbsWeight: 0.04,
        engagementWeight: 1.5,
      })
    const label = (c: HTMLElement, name: string) =>
      Array.from(c.querySelectorAll('.why-label')).find((e) => e.textContent?.startsWith(name))!
    const one = (row: BreakdownRow) => render(<WhyBreakdown breakdown={breakdown([row], row.points, row.points)} articleId={1} />)

    it('bothPartsNamedInline', () => {
      const { container } = one(both())

      expect(labels(container)).toEqual(['Rust  80% × +13.5 (+10 +2.0 votes +1.5 engaged)', 'Score'])
      expect(points(container)).toEqual(['+11', '11'])
    })

    it('votesOnlyPart', () => {
      const { container } = one(rust())

      expect(labels(container)[0]).toBe('Rust  90% × +21.8 (+20 +1.8 votes)')
    })

    it('engagedOnlyPart', () => {
      const { container } = one(engagedOnly())

      expect(labels(container)[0]).toBe('Rust  80% × +11.5 (+10 +1.5 engaged)')
      expect(labels(container)[0]?.endsWith('(+10 +1.5 engaged)')).toBe(true)
    })

    it('negativeBaseVotesPart', () => {
      const { container } = render(
        <WhyBreakdown breakdown={breakdown([profile(3, 64), politics()], 47, 47)} articleId={1} />
      )

      expect(labels(container)).toContain('Politics  60% × −28.8 (−30 +1.2 votes)')
    })

    it('bothPartsRoundToZeroGivePlainRow', () => {
      const { container } = one(
        topic('Rust', {
          noul: 0.93,
          hinge: 0.86,
          weight: 20.08,
          exact: 17.2,
          points: 17,
          baseWeight: 20,
          learnedWeight: 0.08,
          thumbsWeight: 0.04,
          engagementWeight: 0.04,
        })
      )

      expect(labels(container)[0]).toBe('Rust  86% × +20')
      expect(label(container, 'Rust').getAttribute('title')).toBe(
        'Match 93% → counts 86% × +20 = +17.2 pts · base +20, votes +0.0, engaged +0.0'
      )
    })

    it('absentSplitFieldsGivePhase5Row', () => {
      const { container } = one(topic('Rust', { noul: 0.93, hinge: 0.86, weight: 20, exact: 17.2, points: 17 }))

      expect(labels(container)[0]).toBe('Rust  86% × +20')
      expect(label(container, 'Rust').getAttribute('title')).toBe('Match 93% → counts 86% × +20 = +17.2 pts')
    })

    it('tooltipListsEveryPartIncludingZeros', () => {
      const r1 = one(both())
      expect(label(r1.container, 'Rust').getAttribute('title')).toBe(
        'Match 90% → counts 80% × +13.5 = +10.8 pts · base +10, votes +2.0, engaged +1.5'
      )
      r1.unmount()

      const r2 = one(engagedOnly())
      expect(label(r2.container, 'Rust').getAttribute('title')?.endsWith(' · base +10, votes +0.0, engaged +1.5')).toBe(
        true
      )
      r2.unmount()

      const r3 = one(
        topic('Rust', {
          noul: 0.9,
          hinge: 0.8,
          weight: 18.8,
          exact: 15.04,
          points: 15,
          baseWeight: 20,
          learnedWeight: -1.2,
          thumbsWeight: -1.2,
          engagementWeight: 0,
        })
      )
      const title = label(r3.container, 'Rust').getAttribute('title')
      expect(title).toContain('votes −1.2')
      expect(title?.endsWith(' · base +20, votes −1.2, engaged +0.0')).toBe(true)
      expect(labels(r3.container)[0]).toBe('Rust  80% × +18.8 (+20 −1.2 votes)')
    })

    it('noLimitNoteInWhy', () => {
      const { container } = one(
        topic('Rust', {
          noul: 0.9,
          hinge: 0.8,
          weight: 28,
          exact: 22.4,
          points: 22,
          baseWeight: 20,
          learnedWeight: 8,
          thumbsWeight: 0,
          engagementWeight: 8,
        })
      )

      const text = labels(container)[0]!
      const title = label(container, 'Rust').getAttribute('title')!
      expect(text.endsWith('(+20 +8.0 engaged)')).toBe(true)
      for (const s of [text, title]) {
        expect(s).not.toMatch(/max|cap|limit/i)
      }
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
        'Rust  90% × +21.8 (+20 +1.8 votes)',
        'Politics  60% × −28.8 (−30 +1.2 votes)',
        'Go  70% × +10',
        'Score',
      ])
    })
  })
})
