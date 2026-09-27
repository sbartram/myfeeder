import { describe, it, expect, vi, afterEach } from 'vitest'
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

/** Renders the picker inside a .feedback-group next to a narrow control, as FeedbackBar does. */
function renderInGroup(a: Article) {
  const onApply = vi.fn()
  const onClose = vi.fn()
  render(
    <div className="feedback-group">
      <button className="toolbar-btn narrow-toggle">Narrow…</button>
      <NarrowPicker article={a} onApply={onApply} onClose={onClose} />
    </div>
  )
  return { onApply, onClose }
}

const dialog = () => screen.getByRole('dialog', { name: 'Choose topics to penalize' })

describe('NarrowPicker', () => {
  let docListener: ((e: KeyboardEvent) => void) | null = null
  afterEach(() => {
    if (docListener) document.removeEventListener('keydown', docListener)
    docListener = null
  })

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

  it('focusMovesToTheFirstCheckboxOnOpen', () => {
    renderPicker(article([politics, crypto, rust], down))

    expect(box(/Politics/)).toHaveFocus()
  })

  it('numberKeysToggleOptions', () => {
    renderPicker(article([politics, crypto, rust, zero], down))

    fireEvent.keyDown(box(/Politics/), { key: '2' })
    expect(box(/Crypto/)).not.toBeChecked()
    fireEvent.keyDown(box(/Politics/), { key: '2' })
    expect(box(/Crypto/)).toBeChecked()

    fireEvent.keyDown(box(/Politics/), { key: '7' })
    expect(screen.getAllByRole('checkbox').every((c) => (c as HTMLInputElement).checked)).toBe(true)
  })

  it('enterAppliesWhenSomethingIsChecked', () => {
    const { onApply } = renderPicker(article([politics, crypto, rust], down))

    fireEvent.keyDown(dialog(), { key: '3' })
    fireEvent.keyDown(dialog(), { key: 'Enter' })

    expect(onApply).toHaveBeenCalledWith([politics.topicId, crypto.topicId])
  })

  it('enterDoesNothingWithNoneChecked', () => {
    const { onApply, onClose } = renderPicker(article([politics, crypto], down))

    fireEvent.keyDown(dialog(), { key: '1' })
    fireEvent.keyDown(dialog(), { key: '2' })
    fireEvent.keyDown(dialog(), { key: 'Enter' })

    expect(onApply).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('escapeCancels', () => {
    const { onApply, onClose } = renderPicker(article([politics, crypto], down))

    fireEvent.keyDown(box(/Politics/), { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(onApply).not.toHaveBeenCalled()
  })

  it('pickerKeysNeverReachTheDocument', () => {
    renderPicker(article([politics, crypto], down))
    const seen: string[] = []
    docListener = (e) => seen.push(e.key)
    document.addEventListener('keydown', docListener)

    fireEvent.keyDown(box(/Politics/), { key: '1' })
    fireEvent.keyDown(box(/Politics/), { key: 'Enter' })
    fireEvent.keyDown(box(/Politics/), { key: 'Escape' })

    expect(seen).toEqual([])
  })

  it('applyIsDisabledWithTheHintAtZero', () => {
    renderPicker(article([politics, crypto], down))

    fireEvent.click(box(/Politics/))
    fireEvent.click(box(/Crypto/))

    expect(screen.getByRole('button', { name: 'Apply to 0 topics' })).toBeDisabled()
    expect(
      screen.getByText('Pick at least one topic, or press 👎 Down again to remove the vote.')
    ).toBeInTheDocument()
  })

  it('emptyListShowsTheMessageAndHidesApply', () => {
    renderPicker(article([], narrowedTo(politics)))

    expect(
      screen.getByText('No topics match this article any more. Press 👎 Down again to remove the vote.')
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Apply/ })).toBeNull()
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0)
  })

  it('optionsBeyondNineHaveNoKeyHint', () => {
    const many = Array.from({ length: 11 }, (_, i) => topic(100 + i, `Topic ${String.fromCharCode(65 + i)}`, -10))
    renderPicker(article(many, down))

    const keys = [...document.querySelectorAll('.narrow-key')].map((k) => k.textContent)
    expect(keys).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9', '', ''])
    fireEvent.click(box(/Topic J/))
    expect(box(/Topic J/)).not.toBeChecked()
    fireEvent.click(box(/Topic K/))
    expect(box(/Topic K/)).not.toBeChecked()
  })

  it('outsideMouseDownCloses', () => {
    const { onClose, onApply } = renderInGroup(article([politics, crypto], down))

    fireEvent.mouseDown(document.body)

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(onApply).not.toHaveBeenCalled()
  })

  it('insideMouseDownKeepsItOpen', () => {
    const { onClose } = renderInGroup(article([politics, crypto], down))

    fireEvent.mouseDown(screen.getByRole('button', { name: 'Narrow…' }))
    fireEvent.mouseDown(box(/Crypto/))

    expect(onClose).not.toHaveBeenCalled()
  })
})
