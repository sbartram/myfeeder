import type { Article, FeedbackResult, TopicBreakdownRow } from '../types'

/** An article's vote: 1 up, -1 down, 0 none. */
export type Vote = -1 | 0 | 1

/** What a vote request does, which picks the toast's lead. */
export type VoteKind = 'up' | 'down' | 'removed' | 'narrowed' | 'all-matched'

/** FDBK-01: pressing the current vote removes it (0); pressing the other one flips to it. */
export function nextVote(current: Vote, key: 1 | -1): Vote {
  return current === key ? 0 : key
}

/** The article's matched topics: its breakdown TOPIC rows (the server's hinge above 0 set), in server order. */
export function matchedTopics(article: Article): TopicBreakdownRow[] {
  return (article.interestBreakdown?.rows ?? []).filter(
    (row): row is TopicBreakdownRow => row.kind === 'TOPIC'
  )
}

/** D-13: narrowing is offered on a 👎 with 2 or more matched topics, or on an already narrowed vote. */
export function canNarrow(article: Article): boolean {
  const fb = article.feedback
  if (!fb || fb.vote !== -1) return false
  return fb.narrowed || matchedTopics(article).length >= 2
}

/** The narrowed vote's picks that still match the article, in breakdown order (empty when not narrowed). */
export function narrowedPicks(article: Article): { topicId: number; name: string }[] {
  const fb = article.feedback
  if (!fb?.narrowed) return []
  const picked = new Set(fb.topics.map((t) => t.topicId))
  return matchedTopics(article)
    .filter((row) => picked.has(row.topicId))
    .map((row) => ({ topicId: row.topicId, name: row.name }))
}

/** The narrow control's label (D-16): "Narrow…", "No topics", "{name} only", "{a}, {b} only" or "{k} of {n} topics". */
export function narrowLabel(article: Article): string {
  if (!article.feedback?.narrowed) return 'Narrow…'
  const picks = narrowedPicks(article)
  if (picks.length === 0) return 'No topics'
  if (picks.length <= 2) return `${picks.map((p) => p.name).join(', ')} only`
  return `${picks.length} of ${matchedTopics(article).length} topics`
}

/** "+2.0", "−4.0" (U+2212) or "+0.0"; rounds to one decimal first, so a tiny negative prints "+0.0", never "−0.0". */
export function formatDelta(d: number): string {
  const r = Math.round(d * 10) / 10
  return r < 0 ? `−${Math.abs(r).toFixed(1)}` : `+${r.toFixed(1)}`
}

const LEADS: Record<VoteKind, string> = {
  up: '👍 ',
  down: '👎 ',
  removed: 'Vote removed · ',
  narrowed: '👎 Narrowed · ',
  'all-matched': '👎 All matched topics · ',
}

/** The lead for a saved vote with nothing to list; removals use "Vote removed" instead. */
const SAVED_LEADS: Record<Exclude<VoteKind, 'removed'>, string> = {
  up: '👍 Saved',
  down: '👎 Saved',
  narrowed: '👎 Narrowed',
  'all-matched': '👎 All matched topics',
}

/**
 * The effect toast (D-07, D-08, D-10, FDBK-04): an unscored article's vote says it counts once
 * the article is scored (D-03), and a scored article with no matched topic says so (D-18, D-19);
 * a removal of either is just "Vote removed". Otherwise each topic whose rounded server change
 * (after − before) is non-zero, largest change first (ties keep server order), joined by " · "
 * after the vote's lead. The client prints the server's numbers and never recomputes a weight.
 */
export function formatVoteToast(kind: VoteKind, result: FeedbackResult): string {
  if (!result.scored) {
    return kind === 'removed' ? 'Vote removed' : `${SAVED_LEADS[kind]} — counts once this article is scored`
  }
  if (result.effects.length === 0) {
    return kind === 'removed' ? 'Vote removed' : `${SAVED_LEADS[kind]} · No topics matched`
  }
  const entries = result.effects
    .map((e) => ({ name: e.name, d: e.after - e.before }))
    .filter(({ d }) => Math.round(d * 10) !== 0)
    .sort((a, b) => Math.abs(b.d) - Math.abs(a.d))
    .map(({ name, d }) => `${name} ${formatDelta(d)}`)
  return LEADS[kind] + entries.join(' · ')
}
