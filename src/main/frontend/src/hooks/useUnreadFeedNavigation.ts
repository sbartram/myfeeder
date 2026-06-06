import { useCallback, useMemo } from 'react'
import { useUnreadCounts } from './useArticles'
import { useFeeds } from './useFeeds'
import { useFolders } from './useFolders'
import type { Feed } from '../types'

/**
 * Unread-aware feed navigation, shared by the n/p keyboard shortcuts and the
 * "mark all read → jump to next unread feed" behavior.
 *
 * Feeds are walked in the same order the sidebar renders them (folder-grouped
 * first, then un-foldered) so navigation matches what the user sees. The search
 * wraps around and only ever lands on a feed with unread articles; if none
 * qualifies it returns null (callers treat that as a no-op).
 */
export function useUnreadFeedNavigation() {
  const { data: feeds = [] } = useFeeds()
  const { data: folders = [] } = useFolders()
  const { data: counts = {} } = useUnreadCounts()

  const orderedFeeds: Feed[] = useMemo(() => {
    const list: Feed[] = []
    folders.forEach((folder) => {
      list.push(...feeds.filter((f) => f.folderId === folder.id))
    })
    list.push(...feeds.filter((f) => !f.folderId))
    return list
  }, [feeds, folders])

  /**
   * Find the next/previous feed (relative to `currentFeedId`) that has unread
   * articles. `direction` is +1 for next, -1 for previous. Returns null when no
   * other feed qualifies. When `currentFeedId` is not in the list, the search
   * scans the whole list from the matching end.
   */
  const findUnreadFeedId = useCallback(
    (currentFeedId: number | null, direction: 1 | -1): number | null => {
      const n = orderedFeeds.length
      if (n === 0) return null
      const hasUnread = (f: Feed) => (counts[String(f.id)] || 0) > 0

      const idx = currentFeedId != null ? orderedFeeds.findIndex((f) => f.id === currentFeedId) : -1

      const candidates: Feed[] =
        idx < 0
          ? // No current selection: scan the whole list from the matching end.
            Array.from({ length: n }, (_, k) => orderedFeeds[direction === 1 ? k : n - 1 - k])
          : // Walk outward from the current feed, wrapping, excluding it.
            Array.from({ length: n - 1 }, (_, k) => orderedFeeds[(((idx + direction * (k + 1)) % n) + n) % n])

      return candidates.find(hasUnread)?.id ?? null
    },
    [orderedFeeds, counts],
  )

  return { findUnreadFeedId }
}
