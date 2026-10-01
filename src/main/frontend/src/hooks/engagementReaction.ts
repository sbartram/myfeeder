import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { usePriorityStore } from '../stores/priorityStore'
import { PRIORITY_KEY, patchPriorityArticle } from './usePriorityArticles'
import type { PriorityPage } from '../types'

/**
 * The refresh set shared by a vote and every engagement reaction (D-07): the learned
 * weights and the list badges re-read; outside Priority every other open by-id article
 * re-reads its badge too, while ['article', n, 'extracted'] is left alone. The engaged
 * (or voted) article itself is refreshed by its caller. ['priority'] is never touched here.
 * The Interests dialog's suggestions are marked stale too, so articles engaged or voted on since
 * it last opened are refetched (Phase 11 SC-1): a vote takes an article out of the list and an
 * engagement can add one. This covers votes (useVoteFeedback) and every afterEngagement caller,
 * Forget included.
 */
export function invalidateAfterLearnedChange(qc: QueryClient, id: number, onPriority: boolean): void {
  void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
  void qc.invalidateQueries({ queryKey: ['interest', 'suggestions'] })
  void qc.invalidateQueries({ queryKey: ['articles'] })
  if (!onPriority) {
    void qc.invalidateQueries({
      predicate: (q) => q.queryKey[0] === 'article' && q.queryKey.length === 2 && q.queryKey[1] !== id,
    })
  }
}

/**
 * The loaded Priority row's score: the first occurrence in page order (as dedupeById shows
 * it), null for an unscored row, or undefined when no loaded page holds the id.
 */
function priorityRowScore(qc: QueryClient, id: number): number | null | undefined {
  const data = qc.getQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY)
  for (const page of data?.pages ?? []) {
    const row = page.items.find((a) => a.id === id)
    if (row) return row.interestScore ?? null
  }
  return undefined
}

/**
 * The reaction to a successful engagement action (LRN-06, D-05..D-08): open, star, board add,
 * Read Later, Raindrop save and Forget. Run it only after the action succeeded.
 * - Invalidates the vote's refresh set (invalidateAfterLearnedChange).
 * - Refetches the article by id with staleTime 0, because the client's 30s default would hand
 *   back the pre-engagement article (research Pitfall 1). The reading pane's badge and
 *   "Why N?" update from that refetch.
 * - On Priority, only when the refetched score differs from the loaded row's, patches that row
 *   in place and lights "Ranking changed" (D-06). ['priority'] is touched only through
 *   patchPriorityArticle and is never invalidated, reset or refetched (SC-2), so the list being
 *   triaged never re-sorts.
 * - While a vote on the same article is pending, the compare is skipped: the vote patches the
 *   row from its own newer response (research Pitfall 4).
 * - Never rejects: the user's action already succeeded, so a failed refetch stays silent (D-08).
 *
 * Callers `void` it inside a block body and never return it from onSuccess, which would keep
 * the mutation pending until the GET finishes (research Pitfall 5). Phase 11 may reuse it.
 */
export async function afterEngagement(qc: QueryClient, id: number, onPriority: boolean): Promise<void> {
  invalidateAfterLearnedChange(qc, id, onPriority)
  try {
    // retry false keeps this to one GET per action (T-10-01); the client default retries once.
    const fresh = await qc.query({
      queryKey: ['article', id],
      queryFn: () => articlesApi.getById(id),
      staleTime: 0,
      retry: false,
    })
    if (!onPriority) return
    const votePending =
      qc.isMutating({
        mutationKey: ['feedback'],
        predicate: (m) => (m.state.variables as { id?: number } | undefined)?.id === id,
      }) > 0
    if (votePending) return
    const score = fresh.interestScore ?? null
    const rowScore = priorityRowScore(qc, id)
    if (rowScore !== undefined && rowScore !== score) {
      patchPriorityArticle(qc, id, { interestScore: score })
      usePriorityStore.getState().setRankingChanged(true)
    }
  } catch {
    // Swallowed: the engagement itself succeeded, and a background refresh never toasts (D-08).
  }
}
