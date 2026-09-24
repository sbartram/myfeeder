import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  interestApi,
  type InterestProfile,
  type InterestTopic,
  type TopicInput,
  type TopicPreviewRequest,
} from '../api/interest'

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

const TOPICS_KEY = ['interest', 'topics']

export function useInterestTopics() {
  return useQuery({ queryKey: ['interest', 'topics'], queryFn: interestApi.listTopics })
}

/** Appends the created topic to the cached list; a new topic can end cold start (D-05). */
export function useCreateInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: TopicInput) => interestApi.createTopic(input),
    meta: { inlineError: true },
    onSuccess: (topic: InterestTopic) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) => (old ? [...old, topic] : [topic]))
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    },
  })
}

/**
 * Replaces only that topic's cache entry. The topics query is deliberately not invalidated,
 * so a refetch can never touch another row's unsaved edits (research Pitfall 7).
 */
export function useUpdateInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: TopicInput }) =>
      interestApi.updateTopic(id, input),
    meta: { inlineError: true },
    onSuccess: (topic: InterestTopic) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) =>
        old?.map((t) => (t.id === topic.id ? topic : t)),
      )
    },
  })
}

/**
 * Judges one topic description against one article (D-12). Nothing changes server-side, so no
 * query is invalidated; each call is billed, so it is never retried (TanStack's mutation default).
 */
export function usePreviewTopic() {
  return useMutation({
    mutationFn: (request: TopicPreviewRequest) => interestApi.preview(request),
    meta: { inlineError: true },
  })
}

/** Removes the topic from the cached list; deleting the last topic can restore cold start. */
export function useDeleteInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => interestApi.deleteTopic(id),
    meta: { inlineError: true },
    onSuccess: (_result: void, id: number) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) => old?.filter((t) => t.id !== id))
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    },
  })
}

/**
 * The server's count of what a re-score would reset. Never cached (gcTime 0, staleTime 0), so
 * each confirmation fetches a fresh number and shows exactly what is reset (D-02).
 */
export function useRescoreCount() {
  return useQuery({
    queryKey: ['interest', 'rescore-count'],
    queryFn: interestApi.getRescoreCount,
    staleTime: 0,
    gcTime: 0,
  })
}

/**
 * Resets the in-window unread scores so the sweep re-judges them. Destructive and billed
 * downstream, so it is never retried (TanStack's mutation default); refreshes the status counts.
 */
export function useRescoreUnread() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => interestApi.rescore(),
    meta: { inlineError: true },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
    },
  })
}
