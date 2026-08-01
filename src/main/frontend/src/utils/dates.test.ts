import { describe, expect, test } from 'vitest'
import { formatPublishedDate } from './dates'

describe('formatPublishedDate', () => {
  const now = new Date('2026-08-01T18:00:00Z')

  test('shows date and time when the article is less than 24 hours old', () => {
    const result = formatPublishedDate('2026-08-01T15:30:00Z', now)
    expect(result).toBe(new Date('2026-08-01T15:30:00Z').toLocaleString())
  })

  test('shows date only when the article is more than 24 hours old', () => {
    const result = formatPublishedDate('2026-07-30T12:00:00Z', now)
    expect(result).toBe(new Date('2026-07-30T12:00:00Z').toLocaleDateString())
  })

  test('shows date only when the article is exactly 24 hours old', () => {
    const result = formatPublishedDate('2026-07-31T18:00:00Z', now)
    expect(result).toBe(new Date('2026-07-31T18:00:00Z').toLocaleDateString())
  })
})
