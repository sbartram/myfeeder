import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/articles', () => ({
  articlesApi: {
    getById: vi.fn(),
    list: vi.fn(),
    priority: vi.fn(),
    setFeedback: vi.fn(),
    clearFeedback: vi.fn(),
    updateState: vi.fn(),
  },
}))

import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { createElement, type ReactNode } from 'react'
import { useVoteFeedback } from './useFeedback'
import { useArticle } from './useArticles'
import { usePriorityArticles, PRIORITY_KEY } from './usePriorityArticles'
import { articlesApi } from '../api/articles'
import { ApiError } from '../api/client'
import { createQueryClient } from '../queryClient'
import { useToastStore } from '../components/Toast'
import { usePriorityStore } from '../stores/priorityStore'
import type { Article, ArticleFeedback, FeedbackResult, TopicEffect } from '../types'

function article(id: number, overrides: Partial<Article> = {}): Article {
  return {
    id,
    feedId: 1,
    guid: `g-${id}`,
    title: `Article ${id}`,
    url: `https://example.com/${id}`,
    author: null,
    content: null,
    summary: 'Summary',
    imageUrl: null,
    publishedAt: '2026-09-23T00:00:00Z',
    fetchedAt: '2026-09-23T00:00:00Z',
    read: false,
    starred: false,
    interestScore: 70,
    interestBreakdown: {
      raw: 70,
      total: 70,
      display: 70,
      rows: [
        { kind: 'PROFILE', levelIndex: 3, exact: 30, points: 30 },
        { kind: 'TOPIC', topicId: 3, name: 'Go', noul: 0.8, hinge: 0.6, weight: 20, exact: 20, points: 20 },
        { kind: 'TOPIC', topicId: 7, name: 'Rust', noul: 0.9, hinge: 0.8, weight: 20, exact: 20, points: 20 },
      ],
      nonMatching: [],
    },
    ...overrides,
  }
}

const up: ArticleFeedback = { vote: 1, narrowed: false, topics: [] }
const down: ArticleFeedback = { vote: -1, narrowed: false, topics: [] }

const rust = (before: number, after: number): TopicEffect => ({
  topicId: 7,
  name: 'Rust',
  before,
  after,
  baseWeight: 20,
  learned: after - 20,
  limit: 'NONE',
})

function result(a: Article, effects: TopicEffect[] = [rust(20, 22)]): FeedbackResult {
  return { article: a, scored: true, effects }
}

function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** A createQueryClient() client (so meta.inlineError is honored) without query retries. */
function client(): QueryClient {
  const qc = createQueryClient()
  qc.setDefaultOptions({ queries: { ...qc.getDefaultOptions().queries, retry: false } })
  return qc
}

interface Rendered {
  vote: ReturnType<typeof useVoteFeedback>
  article: ReturnType<typeof useArticle>
  priority?: ReturnType<typeof usePriorityArticles>
}

/** The vote hook, the by-id article and (optionally) the Priority list in one render. */
function renderVote(opts: { path?: string; id?: number; priority?: boolean } = {}) {
  const { path = '/', id = 1, priority = false } = opts
  const qc = client()
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(
      QueryClientProvider,
      { client: qc },
      createElement(MemoryRouter, { initialEntries: [path] }, children),
    )
  // Only the Priority test mounts the list: even a disabled query creates a ['priority'] entry.
  const useBoth = (): Rendered => ({ vote: useVoteFeedback(), article: useArticle(id) })
  const useAll = (): Rendered => ({ ...useBoth(), priority: usePriorityArticles() })
  const hook = renderHook(priority ? useAll : useBoth, { wrapper })
  return { qc, ...hook }
}

const cachedVote = (qc: QueryClient, id = 1) => qc.getQueryData<Article>(['article', id])?.feedback?.vote
const toasts = () => useToastStore.getState().toasts.map((t) => `${t.type}:${t.message}`)
const invalidated = (qc: QueryClient, key: unknown[]) => qc.getQueryState(key)?.isInvalidated

describe('useVoteFeedback', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(articlesApi.getById).mockImplementation(async (id) => article(id))
    useToastStore.setState({ toasts: [] })
    usePriorityStore.setState({ rankingChanged: false, baselineUnscored: null })
  })

  it('pressWritesTheIntentBeforeTheRequestResolves', async () => {
    const pending = deferred<FeedbackResult>()
    vi.mocked(articlesApi.setFeedback).mockReturnValueOnce(pending.promise)
    const { qc, result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => hook.current.vote.press(hook.current.article.data!, 1))

    expect(cachedVote(qc)).toBe(1)
    await waitFor(() => expect(articlesApi.setFeedback).toHaveBeenCalledWith(1, 1, null))
    expect(cachedVote(qc)).toBe(1)
    await act(async () => pending.resolve(result(article(1, { feedback: up }))))
  })

  it('rapidUpDownUpEndsAsASingleUp', async () => {
    const first = deferred<FeedbackResult>()
    const second = deferred<FeedbackResult>()
    const third = deferred<FeedbackResult>()
    vi.mocked(articlesApi.setFeedback)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
      .mockReturnValueOnce(third.promise)
    const { qc, result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => {
      const a = hook.current.article.data!
      hook.current.vote.press(a, 1)
      hook.current.vote.press(a, -1)
      hook.current.vote.press(a, 1)
    })

    expect(cachedVote(qc)).toBe(1)
    await waitFor(() => expect(articlesApi.setFeedback).toHaveBeenCalledTimes(1))
    await new Promise((r) => setTimeout(r, 20))
    expect(articlesApi.setFeedback).toHaveBeenCalledTimes(1)

    await act(async () => first.resolve(result(article(1, { feedback: up }))))
    await waitFor(() => expect(articlesApi.setFeedback).toHaveBeenCalledTimes(2))
    await act(async () => second.resolve(result(article(1, { feedback: down }), [rust(22, 16)])))
    await waitFor(() => expect(articlesApi.setFeedback).toHaveBeenCalledTimes(3))
    await act(async () => third.resolve(result(article(1, { feedback: up }), [rust(16, 22)])))

    expect(vi.mocked(articlesApi.setFeedback).mock.calls).toEqual([
      [1, 1, null],
      [1, -1, null],
      [1, 1, null],
    ])
    await waitFor(() => expect(toasts()).toHaveLength(3))
    expect(cachedVote(qc)).toBe(1)
  })

  it('staleResponseDoesNotOverwriteANewerIntent', async () => {
    const first = deferred<FeedbackResult>()
    const second = deferred<FeedbackResult>()
    vi.mocked(articlesApi.setFeedback)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const { qc, result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => {
      const a = hook.current.article.data!
      hook.current.vote.press(a, 1)
      hook.current.vote.press(a, -1)
    })
    await act(async () => first.resolve(result(article(1, { feedback: up, interestScore: 80 }))))
    await waitFor(() => expect(toasts()).toHaveLength(1))

    expect(cachedVote(qc)).toBe(-1)
    expect(qc.getQueryData<Article>(['article', 1])?.interestScore).toBe(80)

    await act(async () => second.resolve(result(article(1, { feedback: down }), [rust(22, 16)])))
    await waitFor(() => expect(toasts()).toHaveLength(2))
    expect(cachedVote(qc)).toBe(-1)
  })

  it('toastsFollowPressOrder', async () => {
    vi.mocked(articlesApi.setFeedback)
      .mockResolvedValueOnce(result(article(1, { feedback: up })))
      .mockResolvedValueOnce(result(article(1, { feedback: down }), [rust(22, 18)]))
    const { result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => {
      const a = hook.current.article.data!
      hook.current.vote.press(a, 1)
      hook.current.vote.press(a, -1)
    })

    await waitFor(() => expect(toasts()).toHaveLength(2))
    expect(toasts()).toEqual(['success:👍 Rust +2.0', 'success:👎 Rust −4.0'])
  })

  it('engagedVoteToastSaysItReplacesEngagement', async () => {
    vi.mocked(articlesApi.setFeedback).mockResolvedValueOnce(
      result(article(1, { feedback: up }), [{ ...rust(20.9, 21.8), engagementReplaced: true }])
    )
    const { result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => hook.current.vote.press(hook.current.article.data!, 1))

    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(toasts()).toEqual(['success:👍 Rust +0.9 (replaces engagement)'])
  })

  it('removedVoteToastSaysEngagementIsRestored', async () => {
    vi.mocked(articlesApi.getById).mockImplementation(async (id) => article(id, { feedback: up }))
    vi.mocked(articlesApi.clearFeedback).mockResolvedValueOnce(
      result(article(1), [{ ...rust(21.8, 20.9), engagementReplaced: true }])
    )
    const { result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data?.feedback?.vote).toBe(1))

    act(() => hook.current.vote.press(hook.current.article.data!, 1))

    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(articlesApi.clearFeedback).toHaveBeenCalledWith(1)
    expect(toasts()).toEqual(['success:Vote removed · Rust −0.9 (engagement restored)'])
  })

  it('priorityVotePatchesOnlyTheVotedRow', async () => {
    vi.mocked(articlesApi.priority).mockResolvedValue({
      items: [
        article(1, { interestScore: 90 }),
        article(2, { interestScore: 80 }),
        article(3, { interestScore: 70 }),
      ],
      nextCursor: null,
    })
    vi.mocked(articlesApi.setFeedback).mockResolvedValue(
      result(article(2, { feedback: up, interestScore: 95 })),
    )
    const { qc, result: hook } = renderVote({ path: '/priority', id: 2, priority: true })
    await waitFor(() => expect(hook.current.priority!.rows).toHaveLength(3))
    await waitFor(() => expect(hook.current.article.data).toBeDefined())
    qc.setQueryData(['article', 3], article(3))
    qc.setQueryData(['articles', {}], { pages: [], pageParams: [] })

    act(() => hook.current.vote.press(hook.current.article.data!, 1))
    await waitFor(() => expect(toasts()).toHaveLength(1))

    await waitFor(() =>
      expect(hook.current.priority!.rows.map((a) => [a.id, a.interestScore])).toEqual([
        [1, 90],
        [2, 95],
        [3, 70],
      ]),
    )
    expect(articlesApi.priority).toHaveBeenCalledTimes(1)
    expect(invalidated(qc, [...PRIORITY_KEY])).toBe(false)
    expect(usePriorityStore.getState().rankingChanged).toBe(true)
    expect(invalidated(qc, ['article', 3])).toBe(false)
    expect(invalidated(qc, ['articles', {}])).toBe(true)
  })

  it('outsidePriorityVoteInvalidatesListsAndOtherArticles', async () => {
    vi.mocked(articlesApi.setFeedback).mockResolvedValue(
      result(article(1, { feedback: up, interestScore: 88 })),
    )
    const { qc, result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())
    qc.setQueryData(['article', 2], article(2))
    qc.setQueryData(['article', 1, 'extracted'], { title: 't', content: '<p/>' })
    qc.setQueryData(['articles', {}], { pages: [], pageParams: [] })

    act(() => hook.current.vote.press(hook.current.article.data!, 1))
    await waitFor(() => expect(toasts()).toHaveLength(1))

    expect(invalidated(qc, ['articles', {}])).toBe(true)
    expect(invalidated(qc, ['article', 2])).toBe(true)
    expect(invalidated(qc, ['article', 1, 'extracted'])).toBe(false)
    expect(invalidated(qc, ['article', 1])).toBe(false)
    expect(qc.getQueryData<Article>(['article', 1])?.interestScore).toBe(88)
    expect(articlesApi.getById).toHaveBeenCalledTimes(1)
    expect(qc.getQueryCache().findAll({ queryKey: PRIORITY_KEY })).toHaveLength(0)
    expect(usePriorityStore.getState().rankingChanged).toBe(false)
  })

  it.each(['/', '/priority'])('learnedIsInvalidatedOnEveryVote (%s)', async (path) => {
    vi.mocked(articlesApi.setFeedback).mockResolvedValue(result(article(1, { feedback: up })))
    const { qc, result: hook } = renderVote({ path })
    await waitFor(() => expect(hook.current.article.data).toBeDefined())
    qc.setQueryData(['interest', 'learned'], [])

    act(() => hook.current.vote.press(hook.current.article.data!, 1))
    await waitFor(() => expect(toasts()).toHaveLength(1))

    expect(invalidated(qc, ['interest', 'learned'])).toBe(true)
  })

  it('failedVoteRevertsAndShowsTheSaveCopy', async () => {
    vi.mocked(articlesApi.setFeedback).mockRejectedValue(new ApiError('Internal Server Error', 500))
    const { qc, result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => hook.current.vote.press(hook.current.article.data!, 1))
    expect(cachedVote(qc)).toBe(1)

    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(toasts()).toEqual([
      "error:Couldn't save your vote. Your previous vote is back; press u or d to try again.",
    ])
    await waitFor(() => expect(articlesApi.getById).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(cachedVote(qc)).toBeUndefined())
    expect(toasts()).toHaveLength(1)
  })

  it('missingArticleShowsTheGoneCopy', async () => {
    vi.mocked(articlesApi.setFeedback).mockRejectedValue(new ApiError('Not found', 404))
    const { result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => hook.current.vote.press(hook.current.article.data!, 1))

    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(toasts()).toEqual(["error:This article no longer exists, so the vote wasn't saved."])
  })

  it('rejectedNarrowingShowsTheNarrowCopy', async () => {
    vi.mocked(articlesApi.setFeedback).mockRejectedValue(new ApiError('Bad Request', 400))
    const { result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => hook.current.vote.narrow(hook.current.article.data!, [3]))

    await waitFor(() => expect(toasts()).toHaveLength(1))
    expect(toasts()).toEqual([
      "error:Couldn't narrow the vote because this article's topics changed. Open Narrow… and pick again.",
    ])
  })

  it('narrowSendsThePicksAndAllMatchedSendsNone', async () => {
    const first = deferred<FeedbackResult>()
    const second = deferred<FeedbackResult>()
    vi.mocked(articlesApi.setFeedback)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const { qc, result: hook } = renderVote()
    await waitFor(() => expect(hook.current.article.data).toBeDefined())

    act(() => hook.current.vote.narrow(hook.current.article.data!, [3]))

    expect(qc.getQueryData<Article>(['article', 1])?.feedback).toEqual({
      vote: -1,
      narrowed: true,
      topics: [{ topicId: 3, name: 'Go' }],
    })
    await waitFor(() => expect(articlesApi.setFeedback).toHaveBeenLastCalledWith(1, -1, [3]))
    const narrowed = { vote: -1 as const, narrowed: true, topics: [{ topicId: 3, name: 'Go' }] }
    await act(async () => first.resolve(result(article(1, { feedback: narrowed }))))
    await waitFor(() => expect(toasts()).toHaveLength(1))

    act(() => hook.current.vote.narrow(hook.current.article.data!, null))

    expect(qc.getQueryData<Article>(['article', 1])?.feedback).toEqual({
      vote: -1,
      narrowed: false,
      topics: [],
    })
    await waitFor(() => expect(articlesApi.setFeedback).toHaveBeenLastCalledWith(1, -1, null))
    await act(async () => second.resolve(result(article(1, { feedback: down }))))
    await waitFor(() => expect(toasts()).toHaveLength(2))
  })
})
