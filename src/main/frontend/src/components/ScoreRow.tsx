import { Fragment } from 'react'
import type { Article, EngagementKind, TopicBreakdownRow } from '../types'
import { usePriorityStore } from '../stores/priorityStore'
import { useForgetEngagement } from '../hooks/useEngagement'
import { InterestBadge } from './InterestBadge'

/** How each engagement kind reads in the Engaged line (D-02). */
const ENGAGEMENT_LABEL: Record<EngagementKind, string> = {
  OPEN_ORIGINAL: 'opened',
  STAR: 'starred',
  BOARD: 'on a board',
  RAINDROP: 'saved to Raindrop',
}

/**
 * The reading pane's score row under the title. It renders when the article is scored or engaged,
 * and nothing when it is neither.
 * - Scored: the badge, one plain-text chip per matched topic that moved the
 *   score (weight not 0, server order, colored by sign with a trailing − on negatives), and the
 *   "Why N?" toggle.
 * - Engaged (CAPT-06): "Engaged: <kinds> · Forget", listing the kinds in server order. Forget
 *   deletes the engagement at once, with no confirm, undo or shortcut (D-03, D-04). An unscored
 *   engaged article shows this line alone.
 */
export function ScoreRow({ article }: { article: Article }) {
  const whyOpen = usePriorityStore((s) => s.whyOpen)
  const toggleWhy = usePriorityStore((s) => s.toggleWhy)
  const forget = useForgetEngagement()
  const score = article.interestScore
  const scored = score != null
  const engagement = article.engagement ?? []
  if (!scored && engagement.length === 0) return null

  const chips = (article.interestBreakdown?.rows ?? []).filter(
    (row): row is TopicBreakdownRow => row.kind === 'TOPIC' && row.weight !== 0
  )

  return (
    <div className="score-row">
      {scored && (
        <>
          <InterestBadge score={score} />
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
            {`Why ${score}? ${whyOpen ? '▾' : '▸'}`}
          </button>
        </>
      )}
      {engagement.length > 0 && (
        <>
          {scored && <span className="chip-sep"> · </span>}
          <span className="engagement-label">
            {`Engaged: ${engagement.map((kind) => ENGAGEMENT_LABEL[kind]).join(', ')}`}
          </span>
          <span className="chip-sep"> · </span>
          <button
            className="toolbar-btn why-toggle"
            aria-label="Forget engagement"
            disabled={forget.isPending}
            onClick={() => forget.mutate(article.id)}
          >
            Forget
          </button>
        </>
      )}
    </div>
  )
}
