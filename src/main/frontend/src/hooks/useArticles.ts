import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import type { ArticleFilters } from '../types'

export function useArticles(filters: ArticleFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['articles', filters],
    queryFn: ({ pageParam }) => articlesApi.list(filters, 50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor !== null ? lastPage.nextCursor : undefined,
  })
}

export function useArticle(id: number | null) {
  return useQuery({
    queryKey: ['article', id],
    queryFn: () => articlesApi.getById(id!),
    enabled: id !== null,
  })
}

export function useUnreadCounts() {
  return useQuery({
    queryKey: ['unreadCounts'],
    queryFn: articlesApi.counts,
    refetchInterval: 60_000,
  })
}

export function useUpdateArticleState() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, state }: { id: number; state: { read?: boolean; starred?: boolean } }) =>
      articlesApi.updateState(id, state),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['article', variables.id] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
    },
  })
}

export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: { articleIds?: number[]; feedId?: number; olderThanDays?: number }) =>
      articlesApi.markRead(params.articleIds, params.feedId, params.olderThanDays),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
    },
  })
}

export function useSaveToRaindrop() {
  return useMutation({
    mutationFn: (id: number) => articlesApi.saveToRaindrop(id),
  })
}
