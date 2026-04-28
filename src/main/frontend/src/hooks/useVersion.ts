import { useQuery } from '@tanstack/react-query'
import { versionApi } from '../api/version'

export function useVersion() {
  return useQuery({
    queryKey: ['version'],
    queryFn: versionApi.get,
    staleTime: Infinity,
    gcTime: Infinity,
  })
}
