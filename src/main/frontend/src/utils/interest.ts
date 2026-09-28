import { createContext } from 'react'
import type { TierThresholds } from '../api/interest'

/**
 * A number with an explicit sign: "+20", "−15" (U+2212 minus) or "0", with `digits` decimals.
 */
export function formatSigned(n: number, digits = 0): string {
  const s = Math.abs(n).toFixed(digits)
  if (n > 0) return `+${s}`
  if (n < 0) return `−${s}`
  return s
}

/**
 * Breakdown labels for the profile match level (D-04): index i names the server's
 * InterestQuestions.PROFILE_LEVELS[i] (0 nothing to do with the profile ... 4 squarely about a
 * core interest); the server sends that index as levelIndex.
 */
export const PROFILE_LEVEL_LABELS = ['None', 'In passing', 'Partly', 'Mainly', 'Core interest'] as const

/** Circuit-breaker states in which scoring is paused. */
export const OPEN_BREAKER_STATES: readonly string[] = ['OPEN', 'FORCED_OPEN']

/** "1 article" or "N articles", with thousands separators ("1,204 articles"). */
export function articles(count: number): string {
  return count === 1 ? '1 article' : `${count.toLocaleString('en-US')} articles`
}

export type Tier = 'high' | 'neutral' | 'low'

/** Tier thresholds used until /api/interest/status loads (D-13 fallback). */
export const DEFAULT_TIERS: TierThresholds = { high: 70, neutral: 40 }

/** Tier thresholds for every InterestBadge; MainLayout provides the served values, and a tree without a provider gets DEFAULT_TIERS. */
export const TierContext = createContext<TierThresholds>(DEFAULT_TIERS)

/** Badge tier of a server display score, inclusive at the low end, using the served thresholds (DEFAULT_TIERS until status loads). */
export function tierOf(score: number, tiers: TierThresholds = DEFAULT_TIERS): Tier {
  if (score >= tiers.high) return 'high'
  if (score >= tiers.neutral) return 'neutral'
  return 'low'
}

const NEGATION =
  /\b(not about|nothing about|anything but|not|no|except|without|isn't|aren't|excluding)\b/i

/**
 * Whether a topic description looks negated (D-09). Smart apostrophes (’, as macOS types them)
 * are normalized first. Advice only: hyphenated words like "no-code" also match, by design.
 */
export function isNegated(text: string): boolean {
  return NEGATION.test(text.replace(/’/g, "'"))
}

/** How much of a match counts toward the score (R6, D-13): 0 up to a 0.5 match, then linear to 1. */
export function hinge(noul: number): number {
  return Math.max(0, (noul - 0.5) * 2)
}

/**
 * The preview math line, e.g. "Match 82% → counts 64% × +20 = +12.8 pts", or
 * "Match 31% · No match (contributes 0)" when the match is 0.5 or below.
 */
export function formatPreviewText(noul: number, weight: number): string {
  const m = hinge(noul)
  const matchPct = Math.round(noul * 100)
  if (m <= 0) return `Match ${matchPct}% · No match (contributes 0)`
  const countsPct = Math.round(m * 100)
  return `Match ${matchPct}% → counts ${countsPct}% × ${formatSigned(weight)} = ${formatSigned(m * weight, 1)} pts`
}

export const WEIGHT_MIN = -50
export const WEIGHT_MAX = 50

/** The typed weight as a whole number from −50 to +50, or null when it isn't one (D-10). */
export function parseWeight(text: string): number | null {
  if (!/^-?\d+$/.test(text.trim())) return null
  const n = Number(text)
  return n >= WEIGHT_MIN && n <= WEIGHT_MAX ? n : null
}

interface TopicEdit {
  name: string
  description: string
  weightText: string
  saved: { name: string; description: string; weight: number } | null
}

/** A draft is always unsaved; a saved row is unsaved once its text or weight differs from its baseline. */
export function isTopicDirty(row: TopicEdit): boolean {
  if (row.saved === null) return true
  return (
    row.name !== row.saved.name ||
    row.description !== row.saved.description ||
    parseWeight(row.weightText) !== row.saved.weight
  )
}
