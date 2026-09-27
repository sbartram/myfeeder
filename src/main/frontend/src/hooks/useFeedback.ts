import { useCallback } from 'react'
import { useMatch } from 'react-router-dom'
import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { useToastStore } from '../components/Toast'
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
    mutationFn: (v: VoteVars): Promise<FeedbackResult> =>
      v.vote === 0 ? articlesApi.clearFeedback(v.id) : articlesApi.setFeedback(v.id, v.vote, v.topicIds),
    onSuccess: (res, v) => {
      qc.setQueryData(['article', v.id], res.article)
      useToastStore.getState().addToast(formatVoteToast(v.kind, res), 'success')
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
