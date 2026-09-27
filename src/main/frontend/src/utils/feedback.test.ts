import { describe, it, expect } from 'vitest'
import { formatVoteToast } from './feedback'
import type { Article, FeedbackResult, TopicEffect } from '../types'

const anArticle = { id: 1 } as Article

function result(effects: TopicEffect[], scored = true): FeedbackResult {
  return { article: anArticle, scored, effects }
}

describe('formatVoteToast', () => {
  it('unscoredCopy', () => {
    const unscored = result([], false)
    expect(formatVoteToast('up', unscored)).toBe('👍 Saved — counts once this article is scored')
    expect(formatVoteToast('down', unscored)).toBe('👎 Saved — counts once this article is scored')
    expect(formatVoteToast('removed', unscored)).toBe('Vote removed')
  })

  it('noMatchCopy', () => {
    const noMatch = result([])
    expect(formatVoteToast('up', noMatch)).toBe('👍 Saved · No topics matched')
    expect(formatVoteToast('down', noMatch)).toBe('👎 Saved · No topics matched')
    expect(formatVoteToast('removed', noMatch)).toBe('Vote removed')
  })
})
