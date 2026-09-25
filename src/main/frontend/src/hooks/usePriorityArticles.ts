import { useMemo } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
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
