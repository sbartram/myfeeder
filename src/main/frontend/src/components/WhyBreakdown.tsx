import { Fragment, useEffect, useState } from 'react'
import type { InterestBreakdown, TopicBreakdownRow } from '../types'
import { PROFILE_LEVEL_LABELS, formatSigned } from '../utils/interest'

/**
 * The "Why N?" panel (D-01..D-05): the server's rows in the server's order with the server's
 * integer points, then a total line whose value always equals the badge (D-02). Only the
 * presentation percentages are rounded here; no score or point value is ever recomputed.
 * Non-matching topics sit behind a footer that collapses on every article change (D-03).
 * A topic row whose learned part rounds to a non-zero tenth also shows its base and learned
 * parts (D-11); learnedWeight is the server's applied part, so base + learned is the printed
 * weight and nothing is recomputed here.
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
            <TopicLabel row={row} />
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

/** A topic row's label and title: the Phase 5 form, or with the learned part when it's shown (D-11). */
function TopicLabel({ row }: { row: TopicBreakdownRow }) {
  const matchPct = Math.round(row.noul * 100)
  const countsPct = Math.round(row.hinge * 100)
  const learned = row.learnedWeight ?? 0
  if (row.baseWeight !== undefined && Math.round(learned * 10) !== 0) {
    const weight = formatSigned(row.weight, 1)
    return (
      <span
        className="why-label"
        title={`Match ${matchPct}% → counts ${countsPct}% × ${weight} = ${formatSigned(row.exact, 1)} pts · base ${formatSigned(row.baseWeight)}, learned ${formatSigned(learned, 1)}`}
      >
        {`${row.name}  ${countsPct}% × ${weight} (${formatSigned(row.baseWeight)} ${formatSigned(learned, 1)} learned)`}
      </span>
    )
  }
  return (
    <span
      className="why-label"
      title={`Match ${matchPct}% → counts ${countsPct}% × ${formatSigned(row.weight)} = ${formatSigned(row.exact, 1)} pts`}
    >
      {`${row.name}  ${countsPct}% × ${formatSigned(row.weight)}`}
    </span>
  )
}
