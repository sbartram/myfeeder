import { useMemo } from 'react'
import { useInfiniteQuery, type QueryClient } from '@tanstack/react-query'
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
  return { ...query, rows }
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
