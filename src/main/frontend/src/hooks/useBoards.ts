import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { boardsApi } from '../api/boards'
import { useToastStore } from '../components/Toast'

export function useBoards() {
  return useQuery({ queryKey: ['boards'], queryFn: boardsApi.getAll })
}

export function useBoardArticles(boardId: number) {
  return useInfiniteQuery({
    queryKey: ['boardArticles', boardId],
    queryFn: ({ pageParam }) => boardsApi.getArticles(boardId, 50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor !== null ? lastPage.nextCursor : undefined,
  })
}

export function useCreateBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ name, description }: { name: string; description?: string }) =>
      boardsApi.create(name, description),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['boards'] }),
  })
}

export function useAddArticleToBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ boardId, articleId }: { boardId: number; articleId: number }) =>
      boardsApi.addArticle(boardId, articleId),
    onSuccess: (_, { boardId }) =>
      qc.invalidateQueries({ queryKey: ['boardArticles', boardId] }),
  })
}

export function useReadLater() {
  const qc = useQueryClient()
  const addToast = useToastStore((s) => s.addToast)

  return useMutation({
    mutationFn: async (articleId: number) => {
      const board = await boardsApi.getOrCreateByName('Read Later')
      await boardsApi.addArticle(board.id, articleId)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['boards'] })
      qc.invalidateQueries({ queryKey: ['boardArticles'] })
      addToast('Added to Read Later', 'success')
    },
    onError: () => {
      addToast('Failed to add to Read Later')
    },
  })
}

export function useRemoveArticleFromBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ boardId, articleId }: { boardId: number; articleId: number }) =>
      boardsApi.removeArticle(boardId, articleId),
    onSuccess: (_, { boardId }) =>
      qc.invalidateQueries({ queryKey: ['boardArticles', boardId] }),
  })
}
