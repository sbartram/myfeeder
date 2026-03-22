import { apiGet, apiPost, apiPatch } from './client'
import type { Article, ArticleFilters, PaginatedArticles } from '../types'

export const articlesApi = {
  list: (filters: ArticleFilters = {}, limit = 50, before?: number) => {
    const params = new URLSearchParams()
    if (filters.feedId != null) params.set('feedId', String(filters.feedId))
    if (filters.read != null) params.set('read', String(filters.read))
    if (filters.starred != null) params.set('starred', String(filters.starred))
    params.set('limit', String(limit))
    if (before != null) params.set('before', String(before))
    return apiGet<PaginatedArticles>(`/articles?${params}`)
  },
  getById: (id: number) => apiGet<Article>(`/articles/${id}`),
  updateState: (id: number, state: { read?: boolean; starred?: boolean }) =>
    apiPatch<Article>(`/articles/${id}`, state),
  markRead: (articleIds?: number[], feedId?: number, olderThanDays?: number) =>
    apiPost<void>('/articles/mark-read', { articleIds, feedId, olderThanDays }),
  counts: () => apiGet<Record<string, number>>('/articles/counts'),
  saveToRaindrop: (id: number) => apiPost<void>(`/articles/${id}/raindrop`),
}
