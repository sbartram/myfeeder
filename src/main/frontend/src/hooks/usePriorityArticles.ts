import { useCallback, useMemo } from 'react'
import { useInfiniteQuery, type InfiniteData, type QueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import type { Article, PaginatedArticles } from '../types'

/**
 * The Priority list's query key. It sits outside the ['articles'] prefix so the
 * mark-read / star invalidations never touch it (D-07).
 */
export const PRIORITY_KEY = ['priority'] as const

/**
 * Flattens the loaded pages and keeps the first occurrence of each id: the cursor
 * tuple can shift between pages, so a row may come back on the next page.
 */
export function dedupeById(pages: PaginatedArticles[] | undefined): Article[] {
  if (!pages) return []
  const seen = new Set<number>()
  const rows: Article[] = []
  for (const page of pages) {
    for (const article of page.items) {
      if (seen.has(article.id)) continue
      seen.add(article.id)
      rows.push(article)
    }
  }
  return rows
}

/**
 * The ranked unread list for /priority, in server order.
 *
 * An infinite staleTime overrides the 30s global default: any refetch of an infinite
 * query reloads every loaded page, which would drop read rows and re-rank the list
 * under the user (research Pattern 5). Focus and reconnect refetch are off (D-09);
 * only the explicit refresh re-ranks.
 */
export function usePriorityArticles(enabled = true) {
  const query = useInfiniteQuery({
    queryKey: PRIORITY_KEY,
    queryFn: ({ pageParam }) => articlesApi.priority(50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last.nextCursor !== null ? last.nextCursor : undefined),
    enabled,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  const rows = useMemo(() => dedupeById(query.data?.pages), [query.data])
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query

  /**
   * Loads the next page and returns the first row that was not loaded before (D-11),
   * or undefined when there is no next page (or one is already loading).
   */
  const fetchNextNewId = useCallback(async (): Promise<number | undefined> => {
    if (!hasNextPage || isFetchingNextPage) return undefined
    const seen = new Set(rows.map((a) => a.id))
    const result = await fetchNextPage()
    return dedupeById(result.data?.pages).find((a) => !seen.has(a.id))?.id
  }, [hasNextPage, isFetchingNextPage, fetchNextPage, rows])

  return { ...query, rows, fetchNextNewId }
}

/**
 * Re-ranks the Priority list (D-08): scrolls it to the top, resets the query to its
 * initial state so only page 1 is fetched (a plain refetch would reload every loaded
 * page, research Pattern 5), then refreshes the reading pane's by-id article so its
 * badge matches the new ranking.
 */
export async function refreshPriority(qc: QueryClient): Promise<void> {
  const list = document.querySelector<HTMLElement>('.article-list .article-items')
  if (list) list.scrollTop = 0
  await qc.resetQueries({ queryKey: PRIORITY_KEY })
  await qc.invalidateQueries({
    predicate: (q) => q.queryKey[0] === 'article' && q.queryKey.length === 2,
  })
}

/**
 * Patches one row of the loaded Priority pages in place (D-07): the row keeps its index
 * and its listed interestScore; only the request's read / starred values are copied.
 * Used by the state mutation (and by the Phase 6 thumbs). The Priority query is
 * patched, never invalidated: any refetch would reload every page and re-rank.
 * Only an existing Priority query is updated, so this never creates a cache entry.
 */
export function patchPriorityArticle(
  qc: QueryClient,
  id: number,
  patch: { read?: boolean; starred?: boolean },
): void {
  const fields: { read?: boolean; starred?: boolean } = {}
  if (patch.read !== undefined) fields.read = patch.read
  if (patch.starred !== undefined) fields.starred = patch.starred
  qc.setQueriesData<InfiniteData<PaginatedArticles>>({ queryKey: PRIORITY_KEY }, (old) => {
    if (!old) return old
    return {
      ...old,
      pages: old.pages.map((p) => ({
        ...p,
        items: p.items.map((a) => (a.id === id ? { ...a, ...fields } : a)),
      })),
    }
  })
}
