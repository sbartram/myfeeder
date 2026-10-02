import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  interestApi,
  type InterestProfile,
  type InterestTopic,
  type TopicInput,
  type TopicPreviewRequest,
  type TierThresholds,
  type TopicSuggestions,
} from '../api/interest'
import { usePriorityStore } from '../stores/priorityStore'
import { DEFAULT_TIERS } from '../utils/interest'

/**
 * Status is refetched on every dialog open (staleTime 0); the server owns coldStart (D-05).
 * It polls every 15s only while articles are waiting and can drain (configured, not cold start:
 * the same gate as the "N waiting" line), so that line drains live. It is observed by the
 * Interests dialog and, while /priority is open, by the Priority banner and the refresh
 * button's "Ranking changed" hint, so nothing polls otherwise.
 */
export function useInterestStatus() {
  return useQuery({
    queryKey: ['interest', 'status'],
    queryFn: interestApi.getStatus,
    staleTime: 0,
    refetchInterval: (query) => {
      const s = query.state.data
      return s && s.configured && !s.coldStart && s.eligibleUnscored > 0 ? 15_000 : false
    },
  })
}

/**
 * The badge tier thresholds (D-13). Shares the status cache key with useInterestStatus but never
 * polls: staleTime Infinity and no refetchInterval mean this observer fetches once when nothing is
 * cached and never refetches on mount or focus. select re-renders only when tiers change. Falls
 * back to DEFAULT_TIERS while loading, on error, or when the server sends no tiers.
 */
export function useInterestTiers(): TierThresholds {
  const { data } = useQuery({
    queryKey: ['interest', 'status'],
    queryFn: interestApi.getStatus,
    staleTime: Infinity,
    select: (s) => s.tiers,
  })
  return data ?? DEFAULT_TIERS
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
      usePriorityStore.getState().setRankingChanged(true)
    },
  })
}

const TOPICS_KEY = ['interest', 'topics']

export const SUGGESTIONS_KEY = ['interest', 'suggestions'] as const

export function useInterestTopics() {
  return useQuery({ queryKey: ['interest', 'topics'], queryFn: interestApi.listTopics })
}

/**
 * Each topic's learned adjustment and effective weight (FDBK-07). It has its own key so refreshing
 * learned values after a vote or a topic save never refetches the topics list, which would
 * disturb unsaved row edits (research Pattern 4).
 */
export function useLearnedTopics() {
  return useQuery({ queryKey: ['interest', 'learned'], queryFn: interestApi.getLearned })
}

/**
 * Appends the created topic to the cached list; a new topic can end cold start (D-05). It can
 * cover a suggested article, so the suggestions are marked stale.
 */
export function useCreateInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: TopicInput) => interestApi.createTopic(input),
    meta: { inlineError: true },
    onSuccess: (topic: InterestTopic) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) => (old ? [...old, topic] : [topic]))
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
      void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
      void qc.invalidateQueries({ queryKey: SUGGESTIONS_KEY })
      usePriorityStore.getState().setRankingChanged(true)
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
      void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
      usePriorityStore.getState().setRankingChanged(true)
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

/**
 * Removes the topic from the cached list; deleting the last topic can restore cold start. An
 * article only that topic covered can become a suggestion, so the suggestions are marked stale.
 */
export function useDeleteInterestTopic() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => interestApi.deleteTopic(id),
    meta: { inlineError: true },
    onSuccess: (_result: void, id: number) => {
      qc.setQueryData<InterestTopic[]>(TOPICS_KEY, (old) => old?.filter((t) => t.id !== id))
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
      void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
      void qc.invalidateQueries({ queryKey: SUGGESTIONS_KEY })
      usePriorityStore.getState().setRankingChanged(true)
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
 * Reset articles drop out of the suggestions until re-scored, so they are marked stale.
 */
export function useRescoreUnread() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => interestApi.rescore(),
    meta: { inlineError: true },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
      void qc.invalidateQueries({ queryKey: SUGGESTIONS_KEY })
      usePriorityStore.getState().setRankingChanged(true)
    },
  })
}

/**
 * The Interests dialog's suggested topics (GAP-01). staleTime 0 refetches it on every Interests
 * open, so articles engaged since the last open appear (SC-1). It never polls. The caller hides
 * the section when the list is empty, still loading or failed (D-02).
 */
export function useTopicSuggestions() {
  return useQuery<TopicSuggestions>({
    queryKey: SUGGESTIONS_KEY,
    queryFn: interestApi.getSuggestions,
    staleTime: 0,
  })
}

/**
 * Dismisses one suggestion: immediate and permanent, with no confirm and no undo (D-17). The row
 * is removed from the cached list at once and the list is refetched; the server keeps the first
 * reason. There is no inlineError meta, so a failure is reported through the global toast.
 */
export function useDismissSuggestion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (articleId: number) => interestApi.dismissSuggestion(articleId),
    onSuccess: (_result: void, articleId: number) => {
      qc.setQueryData<TopicSuggestions>(
        SUGGESTIONS_KEY,
        (old) =>
          old && {
            items: old.items.filter((s) => s.articleId !== articleId),
            total: Math.max(0, old.total - 1),
          },
      )
      void qc.invalidateQueries({ queryKey: SUGGESTIONS_KEY })
    },
  })
}
