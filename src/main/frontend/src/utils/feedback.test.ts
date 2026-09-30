import { describe, it, expect } from 'vitest'
import { formatDelta, formatVoteToast, matchedTopics, nextVote } from './feedback'
import type { Article, FeedbackResult, LearnedLimit, TopicBreakdownRow, TopicEffect } from '../types'

const anArticle = { id: 1 } as Article

function result(effects: TopicEffect[], scored = true): FeedbackResult {
  return { article: anArticle, scored, effects }
}

let nextTopicId = 1

/** One server effect; baseWeight defaults to a positive 10 so no "(now …)" note applies. */
function effect(
  name: string,
  before: number,
  after: number,
  opts: {
    baseWeight?: number
    learned?: number
    limit?: LearnedLimit
    thumbsLearned?: number
    engagementLearned?: number
    engagementReplaced?: boolean
  } = {}
): TopicEffect {
  const baseWeight = opts.baseWeight ?? 10
  return {
    topicId: nextTopicId++,
    name,
    before,
    after,
    baseWeight,
    learned: opts.learned ?? after - baseWeight,
    limit: opts.limit ?? 'NONE',
    ...(opts.thumbsLearned !== undefined && { thumbsLearned: opts.thumbsLearned }),
    ...(opts.engagementLearned !== undefined && { engagementLearned: opts.engagementLearned }),
    ...(opts.engagementReplaced !== undefined && { engagementReplaced: opts.engagementReplaced }),
  }
}

const topicRow = (topicId: number, name: string, weight: number): TopicBreakdownRow => ({
  kind: 'TOPIC',
  topicId,
  name,
  noul: 0.8,
  hinge: 0.6,
  weight,
  exact: weight * 0.6,
  points: Math.round(weight * 0.6),
})

describe('formatDelta', () => {
  it('formatDeltaHasOneDecimalAndNeverMinusZero', () => {
    expect(formatDelta(2)).toBe('+2.0')
    expect(formatDelta(-4)).toBe('−4.0')
    expect(formatDelta(0)).toBe('+0.0')
    expect(formatDelta(-0.04)).toBe('+0.0')
    expect(formatDelta(0.04)).toBe('+0.0')
    expect(formatDelta(1.84)).toBe('+1.8')
  })
})

describe('nextVote', () => {
  it('nextVoteTogglesAndFlips', () => {
    expect(nextVote(0, 1)).toBe(1)
    expect(nextVote(1, 1)).toBe(0)
    expect(nextVote(1, -1)).toBe(-1)
    expect(nextVote(-1, -1)).toBe(0)
    expect(nextVote(-1, 1)).toBe(1)
    expect(nextVote(0, -1)).toBe(-1)
  })
})

describe('matchedTopics', () => {
  it('matchedTopicsAreTheTopicRowsInServerOrder', () => {
    const rust = topicRow(7, 'Rust', 20)
    const zero = topicRow(3, 'Zeroed', 0)
    const withRows = {
      ...anArticle,
      interestBreakdown: {
        raw: 50,
        total: 50,
        display: 50,
        rows: [{ kind: 'PROFILE', levelIndex: 2, exact: 25, points: 25 }, rust, zero],
        nonMatching: [],
      },
    } as Article
    expect(matchedTopics(withRows)).toEqual([rust, zero])
    expect(matchedTopics(anArticle)).toEqual([])
  })
})

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

  it('upVoteListsTopThreeThenMore', () => {
    // Server order (ascending topicId) differs from the change order.
    const effects = [
      effect('Go', 10, 10.2),
      effect('Politics', -30, -28.8, { baseWeight: -30 }),
      effect('Rust', 20, 22),
      effect('WebAssembly', 14, 15.4),
      effect('Zig', 5, 5.1),
    ]
    expect(formatVoteToast('up', result(effects))).toBe(
      '👍 Rust +2.0 · WebAssembly +1.4 · Politics +1.2 (now −28.8) · +2 more'
    )
    expect(formatVoteToast('up', result(effects.slice(0, 4)))).toBe(
      '👍 Rust +2.0 · WebAssembly +1.4 · Politics +1.2 (now −28.8) · +1 more'
    )
    expect(formatVoteToast('up', result(effects.slice(1, 4)))).toBe(
      '👍 Rust +2.0 · WebAssembly +1.4 · Politics +1.2 (now −28.8)'
    )
  })

  it('downFlipShowsTheFullSwing', () => {
    const effects = [effect('Go', 10.2, 9.8), effect('Rust', 22, 18)]
    expect(formatVoteToast('down', result(effects))).toBe('👎 Rust −4.0 · Go −0.4')
  })

  it('removedShowsTheNetChange', () => {
    expect(formatVoteToast('removed', result([effect('Rust', 22, 20)]))).toBe('Vote removed · Rust −2.0')
  })

  it('narrowingLeads', () => {
    expect(
      formatVoteToast('narrowed', result([effect('Go', 9.6, 10), effect('Rust', 16.2, 18)]))
    ).toBe('👎 Narrowed · Rust +1.8 · Go +0.4')
    expect(
      formatVoteToast('all-matched', result([effect('Go', 10, 9.6), effect('Rust', 18, 16.2)]))
    ).toBe('👎 All matched topics · Rust −1.8 · Go −0.4')
  })

  it('cappedTopicShowsZeroWithItsNote', () => {
    // The UI-SPEC example lists Rust first, but its list rule sorts by absolute change.
    const capped = [
      effect('Go', 10, 10.2),
      effect('Rust', 40, 40, { baseWeight: 20, learned: 20, limit: 'LEARNED_CAP' }),
    ]
    expect(formatVoteToast('up', result(capped))).toBe('👍 Go +0.2 · Rust +0.0 (learned at max +20)')
    const floored = [effect('Rust', 10, 10, { baseWeight: 30, learned: -20, limit: 'LEARNED_CAP' })]
    expect(formatVoteToast('down', result(floored))).toBe('👎 Rust +0.0 (learned at min −20)')
  })

  it('signClampAndRangeNotes', () => {
    // A negative base with a limit takes the limit note, not "(now …)".
    const clamped = [effect('Small', 0, 0, { baseWeight: -10, learned: 10, limit: 'SIGN_CLAMP' })]
    expect(formatVoteToast('up', result(clamped))).toBe("👍 Small +0.0 (can't cross 0)")
    const top = [effect('Big', 50, 50, { baseWeight: 45, learned: 5, limit: 'WEIGHT_RANGE' })]
    expect(formatVoteToast('up', result(top))).toBe('👍 Big +0.0 (weight at max +50)')
    const bottom = [effect('Deep', -50, -50, { baseWeight: -40, learned: -10, limit: 'WEIGHT_RANGE' })]
    expect(formatVoteToast('down', result(bottom))).toBe('👎 Deep +0.0 (weight at min −50)')
  })

  it('engagementCapIsNeverWorded', () => {
    // D-13: the toast never words ENGAGEMENT_CAP, so effectNote's default branch applies.
    const capped = [
      effect('Rust', 28, 29.8, {
        baseWeight: 20,
        learned: 9.8,
        limit: 'ENGAGEMENT_CAP',
        thumbsLearned: 1.8,
        engagementLearned: 8,
      }),
    ]
    expect(formatVoteToast('up', result(capped))).toBe('👍 Rust +1.8')
  })

  it('engagementCapAloneIsNotListed', () => {
    // D-13: an engagement-capped topic the vote did not move is not listed (Phase 9 read "+0.0").
    const effects = [
      effect('Rust', 20, 21.8),
      effect('Go', 28, 28, { baseWeight: 20, learned: 8, limit: 'ENGAGEMENT_CAP' }),
    ]
    expect(formatVoteToast('up', result(effects))).toBe('👍 Rust +1.8')
    const lone = [effect('Go', 28, 28, { baseWeight: 20, learned: 8, limit: 'ENGAGEMENT_CAP' })]
    expect(formatVoteToast('up', result(lone))).toBe('👍 Saved · Effect under 0.1 points')
  })

  it('replacedEngagementIsNamed', () => {
    // D-11: the vote replaced the article's engagement share on the topic.
    const upVote = [effect('Rust', 20.9, 21.8, { engagementReplaced: true })]
    expect(formatVoteToast('up', result(upVote))).toBe('👍 Rust +0.9 (replaces engagement)')
    const downVote = [effect('Rust', 20.9, 18.2, { engagementReplaced: true })]
    expect(formatVoteToast('down', result(downVote))).toBe('👎 Rust −2.7 (replaces engagement)')
    expect(formatVoteToast('narrowed', result(downVote))).toBe(
      '👎 Narrowed · Rust −2.7 (replaces engagement)'
    )
    expect(formatVoteToast('all-matched', result(downVote))).toBe(
      '👎 All matched topics · Rust −2.7 (replaces engagement)'
    )
  })

  it('restoredEngagementOnRemoval', () => {
    const removed = [effect('Rust', 21.8, 20.9, { engagementReplaced: true })]
    expect(formatVoteToast('removed', result(removed))).toBe(
      'Vote removed · Rust −0.9 (engagement restored)'
    )
  })

  it('aBindingLimitWinsOverTheReplacedNote', () => {
    // D-12: one note per topic, and a binding limit's note wins.
    const capped = [
      effect('Rust', 40, 40, { baseWeight: 20, learned: 20, limit: 'LEARNED_CAP', engagementReplaced: true }),
    ]
    expect(formatVoteToast('up', result(capped))).toBe('👍 Rust +0.0 (learned at max +20)')
    const clamped = [
      effect('Small', 0, 0, { baseWeight: -10, learned: 10, limit: 'SIGN_CLAMP', engagementReplaced: true }),
    ]
    expect(formatVoteToast('up', result(clamped))).toBe("👍 Small +0.0 (can't cross 0)")
    const top = [
      effect('Big', 50, 50, { baseWeight: 45, learned: 5, limit: 'WEIGHT_RANGE', engagementReplaced: true }),
    ]
    expect(formatVoteToast('up', result(top))).toBe('👍 Big +0.0 (weight at max +50)')
  })

  it('engagementCapWithReplacementReadsReplaced', () => {
    const effects = [
      effect('Rust', 28.9, 29.8, {
        baseWeight: 20,
        learned: 9.8,
        limit: 'ENGAGEMENT_CAP',
        engagementReplaced: true,
      }),
    ]
    expect(formatVoteToast('up', result(effects))).toBe('👍 Rust +0.9 (replaces engagement)')
  })

  it('aReplacedShareIsListedEvenWhenItRoundsAway', () => {
    const effects = [effect('Rust', 20.02, 20.04, { engagementReplaced: true })]
    expect(formatVoteToast('up', result(effects))).toBe('👍 Rust +0.0 (replaces engagement)')
  })

  it('zeroChangeWithoutLimitIsOmitted', () => {
    const effects = [effect('Go', 10, 10.04), effect('Rust', 20, 22), effect('Bleak', -30, -30.03, { baseWeight: -30 })]
    expect(formatVoteToast('up', result(effects))).toBe('👍 Rust +2.0')
  })

  it('tiesKeepServerOrder', () => {
    const a = effect('Alpha', 10, 11)
    const b = effect('Beta', 20, 19)
    expect(formatVoteToast('removed', result([a, b]))).toBe('Vote removed · Alpha +1.0 · Beta −1.0')
    expect(formatVoteToast('removed', result([b, a]))).toBe('Vote removed · Beta −1.0 · Alpha +1.0')
  })

  it('underTenthCopy', () => {
    const tiny = result([effect('Rust', 20, 20.04)])
    expect(formatVoteToast('up', tiny)).toBe('👍 Saved · Effect under 0.1 points')
    expect(formatVoteToast('down', tiny)).toBe('👎 Saved · Effect under 0.1 points')
    expect(formatVoteToast('removed', tiny)).toBe('Vote removed · Effect under 0.1 points')
    expect(formatVoteToast('narrowed', tiny)).toBe('👎 Narrowed · Effect under 0.1 points')
    expect(formatVoteToast('all-matched', tiny)).toBe('👎 All matched topics · Effect under 0.1 points')
  })
})
