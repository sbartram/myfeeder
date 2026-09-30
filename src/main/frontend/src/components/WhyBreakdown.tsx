import { Fragment, useEffect, useState } from 'react'
import type { InterestBreakdown, TopicBreakdownRow } from '../types'
import { PROFILE_LEVEL_LABELS, formatSigned } from '../utils/interest'
import { formatDelta } from '../utils/feedback'

/**
 * The "Why N?" panel (D-01..D-05): the server's rows in the server's order with the server's
 * integer points, then a total line whose value always equals the badge (D-02). Only the
 * presentation percentages are rounded here; no score or point value is ever recomputed.
 * Non-matching topics sit behind a footer that collapses on every article change (D-03).
 * A topic row names its base, votes and engaged parts (D-01, D-02): the parts are the server's
 * applied thumbsWeight and engagementWeight (Phase 9 D-16), each rounded on its own for display
 * and never derived here. A part that rounds to zero tenths is left out of the label but listed
 * in the row's title (D-03). "Why N?" shows no limit notes (D-04).
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

/**
 * A topic row's label and title (D-01..D-04). The label is the Phase 5 form, or, when the votes or
 * engaged part rounds to a non-zero tenth, "{name}  {counts}% × {weight} ({base} {±votes} votes
 * {±engaged} engaged)" with zero-rounded parts omitted. Whenever baseWeight is present the title
 * lists every part, zeros included; without it the title is the exact Phase 5 form. The parts are
 * the server's thumbsWeight and engagementWeight, printed as sent; no limit note is shown.
 */
function TopicLabel({ row }: { row: TopicBreakdownRow }) {
  const matchPct = Math.round(row.noul * 100)
  const countsPct = Math.round(row.hinge * 100)
  const votes = row.thumbsWeight ?? 0
  const engaged = row.engagementWeight ?? 0
  const showVotes = Math.round(votes * 10) !== 0
  const showEngaged = Math.round(engaged * 10) !== 0
  const partsTitle =
    row.baseWeight !== undefined
      ? ` · base ${formatSigned(row.baseWeight)}, votes ${formatDelta(votes)}, engaged ${formatDelta(engaged)}`
      : ''
  if (row.baseWeight !== undefined && (showVotes || showEngaged)) {
    const weight = formatSigned(row.weight, 1)
    const parts = [formatSigned(row.baseWeight)]
    if (showVotes) parts.push(`${formatDelta(votes)} votes`)
    if (showEngaged) parts.push(`${formatDelta(engaged)} engaged`)
    return (
      <span
        className="why-label"
        title={`Match ${matchPct}% → counts ${countsPct}% × ${weight} = ${formatSigned(row.exact, 1)} pts${partsTitle}`}
      >
        {`${row.name}  ${countsPct}% × ${weight} (${parts.join(' ')})`}
      </span>
    )
  }
  return (
    <span
      className="why-label"
      title={`Match ${matchPct}% → counts ${countsPct}% × ${formatSigned(row.weight)} = ${formatSigned(row.exact, 1)} pts${partsTitle}`}
    >
      {`${row.name}  ${countsPct}% × ${formatSigned(row.weight)}`}
    </span>
  )
}
