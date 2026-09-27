import { useVoteFeedback } from '../hooks/useFeedback'
import type { Article } from '../types'

/**
 * The reading pane's 👍 / 👎 group (D-01), rendered directly after ★ Star. The buttons are
 * aria-pressed toggles whose labels never change; pressing the active vote removes it and the
 * other one flips it. Only the pane shows the vote (D-02: list rows carry no indicator), and a
 * vote never marks read, advances or changes the selection (D-04). Never disabled: rapid
 * presses queue in order.
 */
export function FeedbackBar({ article }: { article: Article }) {
  const { press } = useVoteFeedback()
  const vote = article.feedback?.vote ?? 0

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
    </div>
  )
}
