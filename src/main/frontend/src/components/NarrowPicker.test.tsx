import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { NarrowPicker } from './NarrowPicker'
import type { Article, ArticleFeedback, TopicBreakdownRow } from '../types'

const topic = (topicId: number, name: string, weight: number, noul = 0.8): TopicBreakdownRow => ({
  kind: 'TOPIC',
  topicId,
  name,
  noul,
  hinge: noul * 2 - 1,
  weight,
  exact: weight / 2,
  points: Math.round(weight / 2),
})

const politics = topic(3, 'Politics', -30, 0.8)
const crypto = topic(4, 'Crypto', -15, 0.74)
const rust = topic(7, 'Rust', 20, 0.92)
const zero = topic(9, 'Zero', 0, 0.6)

function article(topics: TopicBreakdownRow[], feedback: ArticleFeedback | null = null): Article {
  return {
    id: 1,
    feedId: 1,
    guid: 'g-1',
    title: 'News',
    url: 'https://example.com/1',
    author: null,
    content: null,
    summary: 'Summary',
    imageUrl: null,
    publishedAt: '2026-09-23T00:00:00Z',
    fetchedAt: '2026-09-23T00:00:00Z',
    read: false,
    starred: false,
    interestScore: 50,
    interestBreakdown: {
      raw: 50,
      total: 50,
      display: 50,
      rows: [{ kind: 'PROFILE', levelIndex: 3, exact: 50, points: 50 }, ...topics],
      nonMatching: [],
    },
    feedback,
  }
}

const down: ArticleFeedback = { vote: -1, narrowed: false, topics: [] }
const narrowedTo = (...picks: TopicBreakdownRow[]): ArticleFeedback => ({
  vote: -1,
  narrowed: true,
  topics: picks.map((p) => ({ topicId: p.topicId, name: p.name })),
})

function renderPicker(a: Article) {
  const onApply = vi.fn()
  const onClose = vi.fn()
  render(<NarrowPicker article={a} onApply={onApply} onClose={onClose} />)
  return { onApply, onClose }
}

const box = (name: RegExp) => screen.getByRole('checkbox', { name })

describe('NarrowPicker', () => {
  it('listsMatchedTopicsInBreakdownOrder', () => {
    renderPicker(article([politics, crypto, rust, zero], down))

    expect(screen.getByRole('dialog', { name: 'Choose topics to penalize' })).toHaveAttribute(
      'id',
      'narrow-picker'
    )
    expect(screen.getByText('Penalize which topics?')).toBeInTheDocument()
    const options = document.querySelectorAll('.narrow-option')
    expect(options).toHaveLength(4)
    expect([...options].map((o) => o.querySelector('.narrow-key')?.textContent)).toEqual(['1', '2', '3', '4'])
    const names = [...options].map((o) => o.querySelector('.narrow-name')!)
    expect(names.map((n) => n.textContent)).toEqual(['Politics−', 'Crypto−', 'Rust', 'Zero'])
    expect(names[0]).toHaveClass('weight-negative')
    expect(names[1]).toHaveClass('weight-negative')
    expect(names[2]).toHaveClass('weight-positive')
    expect(names[3]).not.toHaveClass('weight-positive')
    expect(names[3]).not.toHaveClass('weight-negative')
    expect(options[0].querySelector('.narrow-match')?.textContent).toBe('80% match')
  })

  it('startsWithEveryMatchedTopicCheckedWhenNotNarrowed', () => {
    renderPicker(article([politics, crypto, rust], down))

    expect(box(/Politics/)).toBeChecked()
    expect(box(/Crypto/)).toBeChecked()
    expect(box(/Rust/)).toBeChecked()
    expect(screen.getByRole('button', { name: 'Apply to 3 topics' })).toBeEnabled()
  })

  it('startsWithThePicksWhenNarrowed', () => {
    renderPicker(article([politics, crypto, rust], narrowedTo(politics)))

    expect(box(/Politics/)).toBeChecked()
    expect(box(/Crypto/)).not.toBeChecked()
    expect(box(/Rust/)).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Apply to 1 topic' })).toBeEnabled()
  })

  it('applyingASubsetCallsOnApplyWithIds', () => {
    const { onApply, onClose } = renderPicker(article([politics, crypto, rust], down))

    fireEvent.click(box(/Crypto/))
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 2 topics' }))

    expect(onApply).toHaveBeenCalledWith([politics.topicId, rust.topicId])
    expect(onClose).not.toHaveBeenCalled()
  })

  it('applyingAllMatchedUnNarrows', () => {
    const { onApply } = renderPicker(article([politics, crypto, rust], narrowedTo(politics)))

    fireEvent.click(box(/Crypto/))
    fireEvent.click(box(/Rust/))
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 3 topics' }))

    expect(onApply).toHaveBeenCalledWith(null)
  })

  it('unchangedSetClosesWithoutApplying', () => {
    const { onApply, onClose } = renderPicker(article([politics, crypto, rust], narrowedTo(politics, crypto)))

    fireEvent.click(screen.getByRole('button', { name: 'Apply to 2 topics' }))

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(onApply).not.toHaveBeenCalled()
  })
})
