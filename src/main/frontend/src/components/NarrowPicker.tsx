import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { matchedTopics, narrowedPicks } from '../utils/feedback'
import type { Article } from '../types'

interface NarrowPickerProps {
  article: Article
  /** The picked topic ids in breakdown order, or null when every matched topic is picked (un-narrows). */
  onApply: (topicIds: number[] | null) => void
  onClose: () => void
}

function initialChecks(article: Article): Set<number> {
  const picks = article.feedback?.narrowed ? narrowedPicks(article) : matchedTopics(article)
  return new Set(picks.map((p) => p.topicId))
}

function sameSet(a: Set<number>, b: Set<number>): boolean {
  return a.size === b.size && [...a].every((id) => b.has(id))
}

/**
 * The thumbs-down topic picker (D-14, D-15): a non-modal popover inside `.feedback-group` listing
 * the article's matched topics in breakdown order. It opens with the current picks checked (every
 * matched topic when not narrowed). Apply never sends an empty set: every matched topic checked
 * un-narrows (null), a strict subset narrows, and an unchanged set just closes.
 *
 * Keys (Pitfall 5): 1-9 toggle, Enter applies, Esc cancels. Each stops propagation, so the
 * document-level shortcut handler never sees them (React delegates at the root container).
 * A mousedown outside `.feedback-group` closes it.
 */
export function NarrowPicker({ article, onApply, onClose }: NarrowPickerProps) {
  const topics = matchedTopics(article)
  const [initial] = useState(() => initialChecks(article))
  const [checked, setChecked] = useState<Set<number>>(() => new Set(initial))
  const rootRef = useRef<HTMLDivElement>(null)

  // Focus the first checkbox on open (the dialog itself when the list is empty).
  useEffect(() => {
    const root = rootRef.current
    ;(root?.querySelector<HTMLInputElement>('input[type="checkbox"]') ?? root)?.focus()
  }, [])

  useEffect(() => {
    const handleMouseDown = (e: MouseEvent) => {
      const scope = rootRef.current?.closest('.feedback-group') ?? rootRef.current
      if (scope && !scope.contains(e.target as Node)) onClose()
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [onClose])

  const toggle = (topicId: number) =>
    setChecked((prev) => {
      const next = new Set(prev)
      if (next.has(topicId)) next.delete(topicId)
      else next.add(topicId)
      return next
    })

  const apply = () => {
    if (checked.size === 0) return
    if (sameSet(checked, initial)) {
      onClose()
      return
    }
    if (topics.every((t) => checked.has(t.topicId))) {
      onApply(null)
      return
    }
    onApply(topics.filter((t) => checked.has(t.topicId)).map((t) => t.topicId))
  }

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (/^[1-9]$/.test(e.key)) {
      const t = topics[Number(e.key) - 1]
      if (t) toggle(t.topicId)
    } else if (e.key === 'Enter') {
      // On a focused Cancel/Apply button, Enter keeps its native click.
      if ((e.target as HTMLElement).tagName !== 'BUTTON') {
        e.preventDefault()
        apply()
      }
      e.stopPropagation()
      return
    } else if (e.key === 'Escape') {
      onClose()
    } else {
      return
    }
    e.preventDefault()
    e.stopPropagation()
  }

  const k = checked.size

  return (
    <div
      id="narrow-picker"
      ref={rootRef}
      className="narrow-picker"
      role="dialog"
      aria-label="Choose topics to penalize"
      tabIndex={-1}
      onKeyDown={onKeyDown}
    >
      <p className="narrow-picker-title">Penalize which topics?</p>
      {topics.length === 0 ? (
        <p className="narrow-empty">
          No topics match this article any more. Press 👎 Down again to remove the vote.
        </p>
      ) : (
        <div className="narrow-picker-list">
          {topics.map((t, i) => {
            const sign =
              t.weight > 0 ? 'weight-positive' : t.weight < 0 ? 'weight-negative' : 'narrow-name-zero'
            return (
              <label key={t.topicId} className="narrow-option">
                <input
                  type="checkbox"
                  checked={checked.has(t.topicId)}
                  onChange={() => toggle(t.topicId)}
                />
                <kbd className="narrow-key">{i < 9 ? i + 1 : ''}</kbd>
                <span className={`narrow-name ${sign}`}>
                  {t.name}
                  {t.weight < 0 ? '−' : ''}
                </span>
                <span className="narrow-match">{Math.round(t.noul * 100)}% match</span>
              </label>
            )
          })}
        </div>
      )}
      {topics.length > 0 && k === 0 && (
        <p className="narrow-hint">Pick at least one topic, or press 👎 Down again to remove the vote.</p>
      )}
      <div className="narrow-picker-footer">
        <button type="button" className="btn-secondary" onClick={onClose}>
          Cancel
        </button>
        {topics.length > 0 && (
          <button type="button" className="btn-primary" disabled={k === 0} onClick={apply}>
            {k === 1 ? 'Apply to 1 topic' : `Apply to ${k} topics`}
          </button>
        )}
      </div>
    </div>
  )
}
