import { describe, it, expect } from 'vitest'
import {
  DEFAULT_TIERS,
  PROFILE_LEVEL_LABELS,
  formatPreviewText,
  formatSigned,
  hinge,
  isNegated,
  tierOf,
} from './interest'

describe('isNegated', () => {
  it('isNegatedMatchesTheWordList', () => {
    for (const text of [
      'not about crypto',
      'Nothing about sports',
      'anything but politics',
      'Rust without unsafe',
      'Everything except Java',
      'excluding ads',
      'no-code tools', // accepted false positive: the warning is advice only (D-09)
      "aren't about sports",
    ]) {
      expect(isNegated(text), text).toBe(true)
    }
  })

  it('isNegatedNormalizesSmartQuotes', () => {
    expect(isNegated('isn’t crypto')).toBe(true)
  })

  it('isNegatedIgnoresPositiveText', () => {
    for (const text of ['The Rust programming language', 'Notable releases', 'knot theory']) {
      expect(isNegated(text), text).toBe(false)
    }
  })
})

describe('formatSigned', () => {
  it('formatSignedFormatsSignsAndDigits', () => {
    expect(formatSigned(20)).toBe('+20')
    expect(formatSigned(-15)).toBe('−15')
    expect(formatSigned(0)).toBe('0')
    expect(formatSigned(12.8, 1)).toBe('+12.8')
    expect(formatSigned(-12.8, 1)).toBe('−12.8')
  })
})

describe('hinge', () => {
  it('hingeFollowsR6', () => {
    expect(hinge(0.5)).toBe(0)
    expect(hinge(0.31)).toBe(0)
    expect(hinge(0.82)).toBeCloseTo(0.64)
    expect(hinge(1)).toBe(1)
  })
})

describe('formatPreviewText', () => {
  it('formatPreviewTextMatchesTheUiSpec', () => {
    expect(formatPreviewText(0.82, 20)).toBe('Match 82% → counts 64% × +20 = +12.8 pts')
    expect(formatPreviewText(0.82, -20)).toBe('Match 82% → counts 64% × −20 = −12.8 pts')
    expect(formatPreviewText(0.31, 20)).toBe('Match 31% · No match (contributes 0)')
    expect(formatPreviewText(0.5, 20)).toBe('Match 50% · No match (contributes 0)')
    expect(formatPreviewText(1, 50)).toBe('Match 100% → counts 100% × +50 = +50.0 pts')
  })
})

describe('PROFILE_LEVEL_LABELS', () => {
  it('levelLabels', () => {
    expect([...PROFILE_LEVEL_LABELS]).toEqual(['None', 'In passing', 'Partly', 'Mainly', 'Core interest'])
  })
})

describe('tierOf', () => {
  it('defaultsAreInclusiveAtTheLowEnd', () => {
    const cases: [number, string][] = [
      [0, 'low'],
      [39, 'low'],
      [40, 'neutral'],
      [69, 'neutral'],
      [70, 'high'],
      [100, 'high'],
    ]
    for (const [score, tier] of cases) {
      expect(tierOf(score), `score ${score}`).toBe(tier)
    }
  })

  it('honorsServedThresholds', () => {
    const tiers = { high: 60, neutral: 30 }
    const cases: [number, string][] = [
      [29, 'low'],
      [30, 'neutral'],
      [59, 'neutral'],
      [60, 'high'],
    ]
    for (const [score, tier] of cases) {
      expect(tierOf(score, tiers), `score ${score}`).toBe(tier)
    }
  })

  it('defaultTiersAre70And40', () => {
    expect(DEFAULT_TIERS).toEqual({ high: 70, neutral: 40 })
  })
})
