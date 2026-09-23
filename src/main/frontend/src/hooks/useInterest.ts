import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { interestApi, type InterestProfile } from '../api/interest'

/** Status is refetched on every dialog open (staleTime 0); the server owns coldStart (D-05). */
export function useInterestStatus() {
  return useQuery({
    queryKey: ['interest', 'status'],
    queryFn: interestApi.getStatus,
    staleTime: 0,
  })
}

export function useInterestProfile() {
  return useQuery({ queryKey: ['interest', 'profile'], queryFn: interestApi.getProfile })
}

/** Errors are shown inline by the Interests dialog, so the global toast is skipped. */
export function useSaveInterestProfile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (profileText: string) => interestApi.saveProfile(profileText),
    meta: { inlineError: true },
    onSuccess: (profile: InterestProfile) => {
      qc.setQueryData(['interest', 'profile'], profile)
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    },
  })
}
