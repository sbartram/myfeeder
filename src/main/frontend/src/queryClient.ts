import { MutationCache, QueryClient } from '@tanstack/react-query'
import { useToastStore } from './components/Toast'

/**
 * The app's QueryClient. Mutation errors are toasted globally, except for mutations
 * that set `meta: { inlineError: true }`: the interest mutations do, because the
 * Interests dialog shows their errors inline and a toast would report them twice.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: 1 },
    },
    mutationCache: new MutationCache({
      onError: (error, _variables, _onMutateResult, mutation) => {
        if (mutation.meta?.inlineError) return
        useToastStore.getState().addToast(error.message || 'An error occurred')
      },
    }),
  })
}
