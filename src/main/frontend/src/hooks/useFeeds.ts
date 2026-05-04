import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { feedsApi } from '../api/feeds'

export function useFeeds() {
  return useQuery({ queryKey: ['feeds'], queryFn: feedsApi.getAll })
}

export function useSubscribeFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ url, folderId = null }: { url: string; folderId?: number | null }) =>
      feedsApi.subscribe(url, folderId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}

export function useMoveFeedToFolder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, folderId }: { id: number; folderId: number | null }) =>
      feedsApi.moveToFolder(id, folderId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}

export function usePollFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => feedsApi.poll(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['feeds'] })
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
    },
  })
}

export function useDeleteFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => feedsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['feeds'] })
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
    },
  })
}
