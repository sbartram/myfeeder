import { useContext } from 'react'
import { TierContext, tierOf } from '../utils/interest'

/**
 * The server's 0-100 interest score as a tier-colored pill (D-18, D-20). Unscored
 * (null or undefined) renders nothing, never the text 0. A click bubbles to the row (D-21).
 * Tier thresholds come from TierContext (served by /api/interest/status, D-13).
 */
export function InterestBadge({ score }: { score: number | null | undefined }) {
  const tiers = useContext(TierContext)
  if (score == null) return null
  return (
    <span
      className={`interest-badge tier-${tierOf(score, tiers)}`}
      title={`Interest score ${score} of 100`}
      aria-label={`Interest score ${score}`}
    >
      {score}
    </span>
  )
}
