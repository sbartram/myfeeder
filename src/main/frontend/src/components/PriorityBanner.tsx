import type { ReactNode } from 'react'
import { useInterestStatus } from '../hooks/useInterest'
import { articles, OPEN_BREAKER_STATES } from '../utils/interest'

interface PriorityBannerProps {
  onSetUpInterests?: () => void
}

/**
 * At most one status line under the Priority toolbar (D-14), first match wins: not configured,
 * cold start, scoring paused, N waiting. Nothing while the status is loading or failed. Reads the
 * existing status query (no new polling) and never shows the exhausted-failure count (D-17).
 */
export function PriorityBanner({ onSetUpInterests }: PriorityBannerProps) {
  const { data: status } = useInterestStatus()
  if (!status) return null

  const waiting = status.eligibleUnscored
  let coldStart = false
  let content: ReactNode

  if (status.configured === false) {
    content = (
      <span>
        <strong>Scoring isn't set up.</strong> Interest scoring is not configured by the
        administrator, so articles are listed by date.
      </span>
    )
  } else if (status.coldStart === true) {
    coldStart = true
    content = (
      <>
        <span>
          <strong>Nothing to rank yet.</strong> Write a profile or add a topic so articles can be
          scored.
        </span>
        {onSetUpInterests && (
          <button className="btn-primary" onClick={onSetUpInterests}>
            Set up interests
          </button>
        )}
      </>
    )
  } else if (OPEN_BREAKER_STATES.includes(status.breakerState)) {
    content =
      waiting > 0 ? (
        <span>
          ⏸ <strong>Scoring paused</strong> — {articles(waiting)} waiting. It resumes automatically.
        </span>
      ) : (
        <span>
          ⏸ <strong>Scoring paused.</strong> It resumes automatically.
        </span>
      )
  } else if (waiting > 0) {
    content = <strong>{articles(waiting)} waiting to be scored.</strong>
  } else {
    return null
  }

  return (
    <div className={coldStart ? 'priority-banner cold-start' : 'priority-banner'} role="status">
      {content}
    </div>
  )
}
