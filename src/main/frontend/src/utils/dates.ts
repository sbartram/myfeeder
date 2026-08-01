/**
 * Reader byline date: full date + time while an article is under 24 hours old,
 * date only once it's older. Future-dated articles count as recent.
 */
export function formatPublishedDate(dateStr: string, now: Date = new Date()): string {
  const date = new Date(dateStr)
  const isRecent = now.getTime() - date.getTime() < 24 * 60 * 60 * 1000
  return isRecent ? date.toLocaleString() : date.toLocaleDateString()
}
