import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { feedsApi } from '../api/feeds'

export function useFeeds() {
  return useQuery({ queryKey: ['feeds'], queryFn: feedsApi.getAll })
}

export function useSubscribeFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (url: string) => feedsApi.subscribe(url),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}

export function usePollFeed() {
  return useMutation({
    mutationFn: (id: number) => feedsApi.poll(id),
  })
}

export function useDeleteFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => feedsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}
