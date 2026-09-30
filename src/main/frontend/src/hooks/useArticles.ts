import { useMatch } from 'react-router-dom'
import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { useToastStore } from '../components/Toast'
import { patchPriorityArticle } from './usePriorityArticles'
import { afterEngagement } from './engagementReaction'
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

/**
 * Reader view content for an article. The server caches extractions, so a loaded
 * result never goes stale; failures aren't retried because extraction errors
 * (fetch blocked, nothing readable) are deterministic.
 */
export function useExtractedArticle(id: number | null, enabled: boolean) {
  return useQuery({
    queryKey: ['article', id, 'extracted'],
    queryFn: () => articlesApi.getExtractedContent(id!),
    enabled: id !== null && enabled,
    staleTime: Infinity,
    retry: false,
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
  const onPriority = useMatch('/priority') !== null
  return useMutation({
    mutationFn: ({ id, state }: { id: number; state: { read?: boolean; starred?: boolean } }) =>
      articlesApi.updateState(id, state),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['article', variables.id] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
      // Priority rows change in place and never refetch (D-07); request values only.
      patchPriorityArticle(qc, variables.id, variables.state)
      // Only starring is engagement (D-05): unstar and read toggles, auto-mark-read included,
      // never react. Starring an already-starred article records nothing, so the compare in
      // afterEngagement finds no change (research Pitfall 10).
      if (variables.state.starred === true) void afterEngagement(qc, variables.id, onPriority)
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
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => articlesApi.saveToRaindrop(id),
    onSuccess: (_data, id) => {
      useToastStore.getState().addToast('Saved to Raindrop', 'success')
      // The save is recorded as engagement: refresh only the by-id article (D-06).
      void qc.invalidateQueries({ queryKey: ['article', id], exact: true })
    },
  })
}
