import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { foldersApi } from '../api/folders'

export function useFolders() {
  return useQuery({ queryKey: ['folders'], queryFn: foldersApi.getAll })
}

export function useCreateFolder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => foldersApi.create(name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['folders'] }),
  })
}

export function useDeleteFolder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => foldersApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['folders'] })
      qc.invalidateQueries({ queryKey: ['feeds'] })
    },
  })
}
