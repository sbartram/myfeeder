import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Board, PaginatedArticles } from '../types'

export const boardsApi = {
  getAll: () => apiGet<Board[]>('/boards'),
  create: (name: string, description?: string) =>
    apiPost<Board>('/boards', { name, description }),
  update: (id: number, name: string, description?: string) =>
    apiPut<Board>(`/boards/${id}`, { name, description }),
  delete: (id: number) => apiDelete(`/boards/${id}`),
  getArticles: (id: number, limit = 50, before?: number) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (before != null) params.set('before', String(before))
    return apiGet<PaginatedArticles>(`/boards/${id}/articles?${params}`)
  },
  addArticle: (boardId: number, articleId: number) =>
    apiPost<void>(`/boards/${boardId}/articles`, { articleId }),
  removeArticle: (boardId: number, articleId: number) =>
    apiDelete(`/boards/${boardId}/articles/${articleId}`),
}
