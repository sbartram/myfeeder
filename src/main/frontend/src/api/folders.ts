import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Folder } from '../types'

export const foldersApi = {
  getAll: () => apiGet<Folder[]>('/folders'),
  create: (name: string) => apiPost<Folder>('/folders', { name }),
  rename: (id: number, name: string) => apiPut<Folder>(`/folders/${id}`, { name }),
  delete: (id: number) => apiDelete(`/folders/${id}`),
}
