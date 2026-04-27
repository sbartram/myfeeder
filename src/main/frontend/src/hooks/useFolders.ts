import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { foldersApi } from '../api/folders'
import type { Folder } from '../types'

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

export function useReorderFolders() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (folderIds: number[]) => foldersApi.reorder(folderIds),
    onMutate: async (folderIds) => {
      await qc.cancelQueries({ queryKey: ['folders'] })
      const previous = qc.getQueryData<Folder[]>(['folders'])
      if (previous) {
        const byId = new Map(previous.map((f) => [f.id, f]))
        const reordered = folderIds
          .map((id, i) => {
            const f = byId.get(id)
            return f ? { ...f, displayOrder: i } : null
          })
          .filter((f): f is Folder => f !== null)
        qc.setQueryData<Folder[]>(['folders'], reordered)
      }
      return { previous }
    },
    onError: (_err, _ids, context) => {
      if (context?.previous) qc.setQueryData(['folders'], context.previous)
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['folders'] }),
  })
}
