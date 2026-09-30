import { useCallback } from 'react'
import { useMatch } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { afterEngagement } from './engagementReaction'
import type { Article } from '../types'

/**
 * Engagement capture (CAPT-01). Rules for the open report:
 * - One path: every Open Original entry point (both ReadingPane buttons and `o`) calls
 *   useOpenOriginal (D-10). In-body links and Copy Link never do (CAPT-07).
 * - The tab opens first, synchronously inside the user's click or keydown, so the popup keeps
 *   user activation. The request never delays, blocks or cancels it.
 * - Fire and forget: every error is swallowed, with no toast (D-12). No useMutation, because the
 *   global MutationCache toasts mutation errors. No client dedupe: every open sends a PUT (D-11).
 * - Only after the PUT succeeds, afterEngagement refetches the article by id and refreshes the
 *   learned weights and lists (D-07); on Priority it patches the row and lights "Ranking changed"
 *   only when the score changed, and never re-sorts or refetches the list (D-05, D-06). A failed
 *   PUT runs no reaction, and a failed refetch is silent too (D-08).
 */
export function useOpenOriginal(): (article: Pick<Article, 'id' | 'url'>) => void {
  const qc = useQueryClient()
  const onPriority = useMatch('/priority') !== null
  return useCallback(
    (article: Pick<Article, 'id' | 'url'>) => {
      // No original page to open, so nothing to record either.
      if (!article.url?.trim()) return
      // window.open returns null with noopener, so never branch on it.
      window.open(article.url, '_blank', 'noopener')
      // Promise.resolve() turns even a synchronous throw into a rejection the catch swallows.
      void Promise.resolve()
        .then(() => articlesApi.recordOpen(article.id))
        .then(() => afterEngagement(qc, article.id, onPriority))
        .catch(() => {})
    },
    [qc, onPriority]
  )
}

/**
 * Forget engagement (CAPT-06): deletes every engagement kind recorded for the article at once,
 * with no confirm dialog, no undo and no success toast (D-03). A failure falls through to the
 * global MutationCache error toast. On success only the exact by-id article query refreshes
 * (D-06), so the Engaged line disappears; the lists and Priority are never refetched.
 */
export function useForgetEngagement() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => articlesApi.forgetEngagement(id),
    onSuccess: (_data, id) => qc.invalidateQueries({ queryKey: ['article', id], exact: true }),
  })
}
