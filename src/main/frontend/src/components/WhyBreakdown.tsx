import { Fragment, useEffect, useState } from 'react'
import type { InterestBreakdown } from '../types'
import { PROFILE_LEVEL_LABELS, formatSigned } from '../utils/interest'

/**
 * The "Why N?" panel (D-01..D-05): the server's rows in the server's order with the server's
 * integer points, then a total line whose value always equals the badge (D-02). Only the
 * presentation percentages are rounded here; no score or point value is ever recomputed.
 * Non-matching topics sit behind a footer that collapses on every article change (D-03).
 */
export function WhyBreakdown({ breakdown, articleId }: { breakdown: InterestBreakdown; articleId: number }) {
  const [moreOpen, setMoreOpen] = useState(false)
  useEffect(() => {
    setMoreOpen(false)
  }, [articleId])

  const { rows, nonMatching, total, display } = breakdown
  const [totalLabel, totalValue] =
    total > 100
      ? [`Total ${total} → capped at 100`, '100']
      : total < 0
        ? [`Total ${formatSigned(total)} → floored at 0`, '0']
        : ['Score', `${display}`]
  const n = nonMatching.length

  return (
    <div id="why-breakdown" className="why-breakdown">
      {rows.length === 0 && <span className="why-empty">No profile or topic contributed to this score.</span>}
      {rows.map((row) =>
        row.kind === 'PROFILE' ? (
          <Fragment key="profile">
            <span className="why-label">
              {`Profile match (${PROFILE_LEVEL_LABELS[row.levelIndex] ?? PROFILE_LEVEL_LABELS[0]})`}
            </span>
            <span className="why-points">{formatSigned(row.points)}</span>
          </Fragment>
        ) : (
          <Fragment key={row.topicId}>
            <span
              className="why-label"
              title={`Match ${Math.round(row.noul * 100)}% → counts ${Math.round(row.hinge * 100)}% × ${formatSigned(row.weight)} = ${formatSigned(row.exact, 1)} pts`}
            >
              {`${row.name}  ${Math.round(row.hinge * 100)}% × ${formatSigned(row.weight)}`}
            </span>
            <span className="why-points">{formatSigned(row.points)}</span>
          </Fragment>
        )
      )}
      <span className="why-label why-total">{totalLabel}</span>
      <span className="why-points why-total">{totalValue}</span>
      {n > 0 && (
        <button className="toolbar-btn why-more" aria-expanded={moreOpen} onClick={() => setMoreOpen(!moreOpen)}>
          {moreOpen
            ? 'Hide non-matching topics ▾'
            : n === 1
              ? 'Show 1 non-matching topic ▸'
              : `Show ${n} non-matching topics ▸`}
        </button>
      )}
      {moreOpen &&
        nonMatching.map((t) => (
          <Fragment key={t.topicId}>
            <span className="why-label why-muted">{t.name}</span>
            <span className="why-points why-muted">{`${Math.round(t.noul * 100)}% match · 0`}</span>
          </Fragment>
        ))}
    </div>
  )
}
