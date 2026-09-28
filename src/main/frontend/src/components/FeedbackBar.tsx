import { Fragment, useEffect, useRef } from 'react'
import { useVoteFeedback } from '../hooks/useFeedback'
import { useFeedbackStore } from '../stores/feedbackStore'
import { canNarrow, narrowedPicks, narrowLabel } from '../utils/feedback'
import { NarrowPicker } from './NarrowPicker'
import type { Article } from '../types'

/** The narrow control's content: "Narrow…", the picks as truncating names, or the count text (D-16). */
function NarrowContent({ article }: { article: Article }) {
  const picks = narrowedPicks(article)
  if (!article.feedback?.narrowed || picks.length === 0 || picks.length > 2) {
    return <>{narrowLabel(article)}</>
  }
  return (
    <>
      {picks.map((p, i) => (
        <Fragment key={p.topicId}>
          {i > 0 && ', '}
          <span className="narrow-toggle-name">{p.name}</span>
        </Fragment>
      ))}
      {' only'}
    </>
  )
}

/**
 * The reading pane's 👍 / 👎 group (D-01), rendered directly after ★ Star. The buttons are
 * aria-pressed toggles whose labels never change; pressing the active vote removes it and the
 * other one flips it. Only the pane shows the vote (D-02: list rows carry no indicator), and a
 * vote never marks read, advances or changes the selection (D-04). Never disabled: rapid
 * presses queue in order. A 👎 on several topics (or a narrowed one) adds the narrow control and
 * its topic picker (D-13..D-16).
 */
export function FeedbackBar({ article }: { article: Article }) {
  const { press, narrow } = useVoteFeedback()
  const narrowOpen = useFeedbackStore((s) => s.narrowOpen)
  const setNarrowOpen = useFeedbackStore((s) => s.setNarrowOpen)
  const vote = article.feedback?.vote ?? 0
  const narrowable = canNarrow(article)
  const narrowed = article.feedback?.narrowed ?? false
  const label = narrowLabel(article)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const wasOpen = useRef(false)

  // The picker closes when another article is shown or the vote stops being a narrowable 👎.
  useEffect(() => {
    setNarrowOpen(false)
  }, [article.id, setNarrowOpen])
  useEffect(() => {
    if (narrowOpen && !narrowable) setNarrowOpen(false)
  }, [narrowOpen, narrowable, setNarrowOpen])

  // On close, focus returns to the narrow control, or to the article text when the control is gone.
  useEffect(() => {
    if (wasOpen.current && !narrowOpen) {
      ;(toggleRef.current ?? document.querySelector<HTMLElement>('.reading-content'))?.focus()
    }
    wasOpen.current = narrowOpen
  }, [narrowOpen])

  return (
    <div className="feedback-group">
      <button
        className="toolbar-btn vote-btn"
        aria-pressed={vote === 1}
        aria-label="Thumbs up"
        title={vote === 1 ? 'Remove thumbs up (u)' : 'Thumbs up (u)'}
        onClick={() => press(article, 1)}
      >
        👍 Up
      </button>
      <button
        className="toolbar-btn vote-btn"
        aria-pressed={vote === -1}
        aria-label="Thumbs down"
        title={vote === -1 ? 'Remove thumbs down (d)' : 'Thumbs down (d)'}
        onClick={() => press(article, -1)}
      >
        👎 Down
      </button>
      {narrowable && (
        <button
          ref={toggleRef}
          className={`toolbar-btn narrow-toggle${narrowed ? ' narrowed' : ''}`}
          aria-haspopup="dialog"
          aria-expanded={narrowOpen}
          aria-controls="narrow-picker"
          aria-label={narrowed ? `Penalizing ${label}. Change topics` : 'Choose topics to penalize'}
          title={label}
          onClick={() => setNarrowOpen(true)}
        >
          <NarrowContent article={article} />
        </button>
      )}
      {narrowOpen && narrowable && (
        <NarrowPicker
          article={article}
          onApply={(ids) => {
            narrow(article, ids)
            setNarrowOpen(false)
          }}
          onClose={() => setNarrowOpen(false)}
        />
      )}
    </div>
  )
}
