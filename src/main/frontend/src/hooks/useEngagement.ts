import { useCallback } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import type { Article } from '../types'

/**
 * Engagement capture (CAPT-01). Rules for the open report:
 * - One path: every Open Original entry point (both ReadingPane buttons and `o`) calls
 *   useOpenOriginal (D-10). In-body links and Copy Link never do (CAPT-07).
 * - The tab opens first, synchronously inside the user's click or keydown, so the popup keeps
 *   user activation. The request never delays, blocks or cancels it.
 * - Fire and forget: every error is swallowed, with no toast (D-12). No useMutation, because the
 *   global MutationCache toasts mutation errors. No client dedupe: every open sends a PUT (D-11).
 * - On success only the exact by-id article query refreshes (D-06).
 */
export function useOpenOriginal(): (article: Pick<Article, 'id' | 'url'>) => void {
  const qc = useQueryClient()
  return useCallback(
    (article: Pick<Article, 'id' | 'url'>) => {
      // No original page to open, so nothing to record either.
      if (!article.url?.trim()) return
      // window.open returns null with noopener, so never branch on it.
      window.open(article.url, '_blank', 'noopener')
      // Promise.resolve() turns even a synchronous throw into a rejection the catch swallows.
      void Promise.resolve()
        .then(() => articlesApi.recordOpen(article.id))
        .then(() => qc.invalidateQueries({ queryKey: ['article', article.id], exact: true }))
        .catch(() => {})
    },
    [qc]
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
