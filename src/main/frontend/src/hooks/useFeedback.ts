import { useCallback } from 'react'
import { useMatch } from 'react-router-dom'
import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { ApiError } from '../api/client'
import { useToastStore } from '../components/Toast'
import { usePriorityStore } from '../stores/priorityStore'
import { patchPriorityArticle } from './usePriorityArticles'
import { invalidateAfterLearnedChange } from './engagementReaction'
import { formatVoteToast, matchedTopics, nextVote, type Vote, type VoteKind } from '../utils/feedback'
import type { Article, ArticleFeedback, FeedbackResult } from '../types'

/** The TanStack mutation scope that sends votes one at a time, in press order. */
export const FEEDBACK_SCOPE = 'article-feedback'

export interface VoteVars {
  id: number
  vote: Vote
  topicIds: number[] | null
  kind: VoteKind
  onPriority: boolean
}

/** Writes the client intent onto the by-id article synchronously, so the pressed state follows at once. */
function writeIntent(qc: QueryClient, id: number, feedback: ArticleFeedback | null): void {
  qc.setQueryData<Article>(['article', id], (old) => (old ? { ...old, feedback } : old))
}

/** The vote error toast (UI-SPEC Error states): fixed copy by status, never the server's message. */
function voteErrorCopy(error: unknown, v: VoteVars): string {
  const status = error instanceof ApiError ? error.status : undefined
  if (status === 404) return "This article no longer exists, so the vote wasn't saved."
  if (status === 400 && v.topicIds !== null) {
    return "Couldn't narrow the vote because this article's topics changed. Open Narrow… and pick again."
  }
  return "Couldn't save your vote. Your previous vote is back; press u or d to try again."
}

/**
 * The thumbs vote (FDBK-01): `press` stores, flips or removes the vote from the freshest intent,
 * `narrow` stores a 👎 limited to picked topics (null picks = every matched topic). Votes are
 * serialized through one mutation scope. A vote never marks read, stars or changes the selection (D-04).
 */
export function useVoteFeedback() {
  const qc = useQueryClient()
  const onPriority = useMatch('/priority') !== null
  const { mutate } = useMutation({
    mutationKey: ['feedback'],
    scope: { id: FEEDBACK_SCOPE },
    meta: { inlineError: true },
    mutationFn: (v: VoteVars): Promise<FeedbackResult> =>
      v.vote === 0 ? articlesApi.clearFeedback(v.id) : articlesApi.setFeedback(v.id, v.vote, v.topicIds),
    onSuccess: (res, v) => {
      // Pitfall 3: while a newer vote on this article is still queued, keep its cached intent.
      const newerPending =
        qc.isMutating({
          mutationKey: ['feedback'],
          predicate: (m) => (m.state.variables as VoteVars | undefined)?.id === v.id,
        }) > 1
      const feedback = newerPending
        ? (qc.getQueryData<Article>(['article', v.id])?.feedback ?? null)
        : (res.article.feedback ?? null)
      qc.setQueryData<Article>(['article', v.id], { ...res.article, feedback })
      // Learned weights and lists; off Priority also every other by-id article (D-05), leaving
      // ['article', n, 'extracted'] alone. The engagement reactions share this set.
      invalidateAfterLearnedChange(qc, v.id, v.onPriority)
      if (v.onPriority) {
        // D-06: patch only the voted row; the Priority key is never invalidated or refetched.
        patchPriorityArticle(qc, v.id, { interestScore: res.article.interestScore ?? null })
        usePriorityStore.getState().setRankingChanged(true)
      }
      useToastStore.getState().addToast(formatVoteToast(v.kind, res), 'success')
    },
    onError: (error, v) => {
      useToastStore.getState().addToast(voteErrorCopy(error, v), 'error')
      // Revert the pressed state to the server's.
      void qc.invalidateQueries({ queryKey: ['article', v.id], exact: true })
    },
  })

  const press = useCallback(
    (article: Article, key: 1 | -1) => {
      const current: Vote =
        qc.getQueryData<Article>(['article', article.id])?.feedback?.vote ?? article.feedback?.vote ?? 0
      const vote = nextVote(current, key)
      writeIntent(qc, article.id, vote === 0 ? null : { vote, narrowed: false, topics: [] })
      const kind: VoteKind = vote === 0 ? 'removed' : vote === 1 ? 'up' : 'down'
      mutate({ id: article.id, vote, topicIds: null, kind, onPriority })
    },
    [qc, mutate, onPriority]
  )

  const narrow = useCallback(
    (article: Article, topicIds: number[] | null) => {
      const picked = topicIds ?? []
      const topics = matchedTopics(article)
        .filter((row) => picked.includes(row.topicId))
        .map((row) => ({ topicId: row.topicId, name: row.name }))
      writeIntent(qc, article.id, { vote: -1, narrowed: topicIds !== null, topics })
      mutate({
        id: article.id,
        vote: -1,
        topicIds,
        kind: topicIds !== null ? 'narrowed' : 'all-matched',
        onPriority,
      })
    },
    [qc, mutate, onPriority]
  )

  return { press, narrow }
}
