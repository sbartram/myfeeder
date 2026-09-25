import { tierOf } from '../utils/interest'

/**
 * The server's 0-100 interest score as a tier-colored pill (D-18, D-20). Unscored
 * (null or undefined) renders nothing, never the text 0. A click bubbles to the row (D-21).
 */
export function InterestBadge({ score }: { score: number | null | undefined }) {
  if (score == null) return null
  return (
    <span
      className={`interest-badge tier-${tierOf(score)}`}
      title={`Interest score ${score} of 100`}
      aria-label={`Interest score ${score}`}
    >
      {score}
    </span>
  )
}
