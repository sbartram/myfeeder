import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { FeedbackNotice } from './FeedbackNotice'
import type { Article, BreakdownRow, InterestBreakdown } from '../types'

const breakdown = (rows: BreakdownRow[], total: number): InterestBreakdown => ({
  raw: total,
  total,
  display: total,
  rows,
  nonMatching: [],
})

const profileOnly = breakdown([{ kind: 'PROFILE', levelIndex: 2, exact: 40, points: 40 }], 40)

const article = (overrides: Partial<Article>): Article =>
  ({
    id: 1,
    feedId: 1,
    guid: 'g-1',
    title: 'Test Article',
    url: 'https://example.com/post',
    author: null,
    content: null,
    summary: null,
    imageUrl: null,
    publishedAt: '2026-08-01T10:00:00Z',
    fetchedAt: '2026-08-01T10:05:00Z',
    read: false,
    starred: false,
    interestScore: 40,
    interestBreakdown: profileOnly,
    feedback: { vote: -1, narrowed: false, topics: [] },
    ...overrides,
  }) as Article

describe('FeedbackNotice', () => {
  it('showsForAVotedScoredArticleWithNoMatches', () => {
    render(<FeedbackNotice article={article({})} />)

    const notice = screen.getByRole('status')
    expect(notice).toHaveClass('feedback-notice')
    expect(notice.textContent).toBe('No topics matched — Create topic from article')
    expect(screen.getByRole('button', { name: 'Create topic from article' })).toHaveClass('feedback-create')
  })

  it('hiddenWithoutVoteUnscoredOrMatched', () => {
    const noVote = render(<FeedbackNotice article={article({ feedback: null })} />)
    expect(noVote.container).toBeEmptyDOMElement()
    noVote.unmount()

    const absentVote = render(<FeedbackNotice article={article({ feedback: undefined })} />)
    expect(absentVote.container).toBeEmptyDOMElement()
    absentVote.unmount()

    const unscored = render(
      <FeedbackNotice article={article({ interestScore: null, interestBreakdown: undefined })} />,
    )
    expect(unscored.container).toBeEmptyDOMElement()
    unscored.unmount()

    const matched = render(
      <FeedbackNotice
        article={article({
          interestBreakdown: breakdown(
            [
              { kind: 'PROFILE', levelIndex: 2, exact: 40, points: 40 },
              { kind: 'TOPIC', topicId: 3, name: 'Rust', noul: 0.9, hinge: 0.8, weight: 20, exact: 16, points: 16 },
            ],
            56,
          ),
        })}
      />,
    )
    expect(matched.container).toBeEmptyDOMElement()
  })

  it('createTopicPassesTheDraft', () => {
    const onCreateTopic = vi.fn()
    const down = render(
      <FeedbackNotice article={article({ title: '  A long title  ' })} onCreateTopic={onCreateTopic} />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Create topic from article' }))
    expect(onCreateTopic).toHaveBeenLastCalledWith({ description: 'A long title', weight: -20 })
    down.unmount()

    const up = render(
      <FeedbackNotice
        article={article({ title: 'Up title', feedback: { vote: 1, narrowed: false, topics: [] } })}
        onCreateTopic={onCreateTopic}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Create topic from article' }))
    expect(onCreateTopic).toHaveBeenLastCalledWith({ description: 'Up title', weight: 20 })
    up.unmount()

    render(<FeedbackNotice article={article({ title: 'x'.repeat(600) })} onCreateTopic={onCreateTopic} />)
    fireEvent.click(screen.getByRole('button', { name: 'Create topic from article' }))
    expect(onCreateTopic).toHaveBeenLastCalledWith({ description: 'x'.repeat(500), weight: -20 })
    expect(onCreateTopic).toHaveBeenCalledTimes(3)
  })
})
