import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Feed } from '../types'

export const feedsApi = {
  getAll: () => apiGet<Feed[]>('/feeds'),
  getById: (id: number) => apiGet<Feed>(`/feeds/${id}`),
  subscribe: (url: string) => apiPost<Feed>('/feeds', { url }),
  update: (id: number, feed: Partial<Feed>) => apiPut<Feed>(`/feeds/${id}`, feed),
  delete: (id: number) => apiDelete(`/feeds/${id}`),
  poll: (id: number) => apiPost<void>(`/feeds/${id}/poll`),
  moveToFolder: (id: number, folderId: number | null) =>
    apiPut<Feed>(`/feeds/${id}/folder`, { folderId }),
}
