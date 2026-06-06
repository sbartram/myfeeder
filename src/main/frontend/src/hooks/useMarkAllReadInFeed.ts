import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMarkRead } from './useArticles'
import { useUnreadFeedNavigation } from './useUnreadFeedNavigation'
import { useUIStore } from '../stores/uiStore'

/**
 * Marks every article in a feed as read, then advances to the next feed that
 * still has unread articles (matching the visible folder ordering).
 *
 * Shared by the "Mark all read" toolbar button and the Shift+A keyboard
 * shortcut so the two cannot drift apart — previously only the button
 * performed the next-feed navigation.
 */
export function useMarkAllReadInFeed() {
  const markRead = useMarkRead()
  const navigate = useNavigate()
  const { findUnreadFeedId } = useUnreadFeedNavigation()
  const setSelectedFeed = useUIStore((s) => s.setSelectedFeed)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)

  return useCallback(
    (feedId: number) => {
      // Capture the next unread feed before marking, while counts still reflect
      // the pre-mark state (the current feed drops to zero afterwards).
      const nextId = findUnreadFeedId(feedId, 1)
      markRead.mutate(
        { feedId },
        {
          onSuccess: () => {
            if (nextId != null) {
              setSelectedFeed(nextId)
              setSelectedArticle(null)
              navigate(`/feed/${nextId}`)
            }
          },
        },
      )
    },
    [findUnreadFeedId, markRead, navigate, setSelectedFeed, setSelectedArticle],
  )
}
