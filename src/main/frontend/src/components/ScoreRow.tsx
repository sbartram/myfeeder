import { Fragment } from 'react'
import type { Article, TopicBreakdownRow } from '../types'
import { usePriorityStore } from '../stores/priorityStore'
import { InterestBadge } from './InterestBadge'

/**
 * The reading pane's score row under the title (D-01, D-06): the badge, one plain-text chip per
 * matched topic that moved the score (weight not 0, server order, colored by sign with a trailing
 * − on negatives), and the "Why N?" toggle. Renders nothing for an unscored article.
 */
export function ScoreRow({ article }: { article: Article }) {
  const whyOpen = usePriorityStore((s) => s.whyOpen)
  const toggleWhy = usePriorityStore((s) => s.toggleWhy)
  if (article.interestScore == null) return null

  const chips = (article.interestBreakdown?.rows ?? []).filter(
    (row): row is TopicBreakdownRow => row.kind === 'TOPIC' && row.weight !== 0
  )

  return (
    <div className="score-row">
      <InterestBadge score={article.interestScore} />
      {chips.map((row) => (
        <Fragment key={row.topicId}>
          <span
            className={`topic-chip ${row.weight > 0 ? 'weight-positive' : 'weight-negative'}`}
            title={row.name}
          >
            {row.name}
            {row.weight < 0 ? '−' : ''}
          </span>
          <span className="chip-sep"> · </span>
        </Fragment>
      ))}
      <button
        className="toolbar-btn why-toggle"
        aria-expanded={whyOpen}
        aria-controls="why-breakdown"
        onClick={toggleWhy}
      >
        {`Why ${article.interestScore}? ${whyOpen ? '▾' : '▸'}`}
      </button>
    </div>
  )
}
