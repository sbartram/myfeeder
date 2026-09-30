import { useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUIStore } from '../stores/uiStore'
import { usePriorityStore } from '../stores/priorityStore'
import { useFeedbackStore } from '../stores/feedbackStore'
import { usePreferences, FONT_SIZE_STEPS } from '../stores/preferencesStore'
import { useArticle, useUpdateArticleState, useSaveToRaindrop } from './useArticles'
import { usePollFeed } from './useFeeds'
import { useMarkAllReadInFeed } from './useMarkAllReadInFeed'
import { useUnreadFeedNavigation } from './useUnreadFeedNavigation'
import { useVoteFeedback } from './useFeedback'
import { useOpenOriginal } from './useEngagement'
import { canNarrow } from '../utils/feedback'
import type { Article } from '../types'

interface KeyboardShortcutCallbacks {
  onOpenBoard?: () => void
  onShowShortcuts?: () => void
  /** True on /priority: j pages past the last row, r re-ranks, Shift+A is disabled. */
  isPriority?: boolean
  /** Loads the next Priority page; resolves to its first new row id, or undefined. */
  onPriorityNextPage?: () => Promise<number | undefined>
  /** Re-ranks the Priority list from page 1. */
  onPriorityRefresh?: () => void
}

export function useKeyboardShortcuts(articles: Article[], callbacks: KeyboardShortcutCallbacks = {}) {
  const navigate = useNavigate()
  const chordRef = useRef<string | null>(null)
  const chordTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const setSelectedFeed = useUIStore((s) => s.setSelectedFeed)
  const cycleFocus = useUIStore((s) => s.cycleFocus)
  const setKeyboardFocus = useUIStore((s) => s.setKeyboardFocus)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)
  const keyboardFocus = useUIStore((s) => s.keyboardFocus)

  const articleListFontSize = usePreferences((s) => s.articleListFontSize)
  const readingFontSize = usePreferences((s) => s.readingFontSize)
  const setArticleListFontSize = usePreferences((s) => s.setArticleListFontSize)
  const setReadingFontSize = usePreferences((s) => s.setReadingFontSize)

  const updateState = useUpdateArticleState()
  const pollFeed = usePollFeed()
  const saveToRaindrop = useSaveToRaindrop()
  const markAllReadInFeed = useMarkAllReadInFeed()
  const { findUnreadFeedId } = useUnreadFeedNavigation()
  const { press } = useVoteFeedback()
  const openOriginal = useOpenOriginal()

  const currentIndex = articles.findIndex((a) => a.id === selectedArticleId)
  // The action target (o/m/s/v/b) must work even when the selected article is
  // not in `articles` — e.g. Starred/Folder views, where the list driving this
  // hook is keyed differently from the visible list. Fetch it by id like
  // ReadingPane does, preferring the list entry when present.
  const { data: fetchedArticle } = useArticle(selectedArticleId)
  const currentArticle = (currentIndex >= 0 ? articles[currentIndex] : null) ?? fetchedArticle ?? null

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      // Ignore when typing in inputs
      const tag = (e.target as HTMLElement).tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
        if (e.key === 'Escape') (e.target as HTMLElement).blur()
        return
      }

      // Handle chord continuation
      if (chordRef.current === 'g') {
        chordRef.current = null
        if (chordTimerRef.current) clearTimeout(chordTimerRef.current)
        switch (e.key) {
          case 'p': setSelectedFeed(null); navigate('/priority'); return
          case 'a': navigate('/'); return
          case 's': navigate('/starred'); return
          case 'b': navigate('/boards'); return
        }
        return
      }

      // Adjust font size of focused panel: '+' (or '=') and '-'
      if (e.key === '+' || e.key === '=' || e.key === '-') {
        const delta = e.key === '-' ? -1 : 1
        const target = keyboardFocus === 'reading' ? 'reading' : 'articles'
        const current = target === 'reading' ? readingFontSize : articleListFontSize
        const idx = FONT_SIZE_STEPS.indexOf(current)
        const next = FONT_SIZE_STEPS[Math.max(0, Math.min(FONT_SIZE_STEPS.length - 1, idx + delta))]
        if (next !== current) {
          if (target === 'reading') setReadingFontSize(next)
          else setArticleListFontSize(next)
        }
        e.preventDefault()
        return
      }

      switch (e.key) {
        case 'j':
          if (currentIndex < articles.length - 1) {
            setSelectedArticle(articles[currentIndex + 1].id)
          } else if (articles.length > 0 && currentIndex === -1) {
            setSelectedArticle(articles[0].id)
          } else if (
            callbacks.isPriority &&
            articles.length > 0 &&
            currentIndex === articles.length - 1 &&
            callbacks.onPriorityNextPage
          ) {
            // Priority only (D-11): past the last loaded row, load the next page and
            // select its first new row. Other lists stay put.
            void callbacks.onPriorityNextPage().then((id) => {
              if (id !== undefined) setSelectedArticle(id)
            })
          }
          break
        case 'k':
          if (currentIndex > 0) {
            setSelectedArticle(articles[currentIndex - 1].id)
          }
          break
        case 'Enter':
          // Open the article in the reading pane: select the first one if
          // nothing is selected yet, then move keyboard focus to the pane.
          if (!selectedArticleId && articles.length > 0) {
            setSelectedArticle(articles[0].id)
          }
          if (selectedArticleId || articles.length > 0) {
            setKeyboardFocus('reading')
          }
          break
        case 'n': {
          // Next feed with unread articles (wraps; no-op if none qualify)
          const nextId = findUnreadFeedId(selectedFeedId, 1)
          if (nextId != null) {
            setSelectedFeed(nextId)
            navigate(`/feed/${nextId}`)
          }
          break
        }
        case 'p': {
          // Previous feed with unread articles (wraps; no-op if none qualify)
          const prevId = findUnreadFeedId(selectedFeedId, -1)
          if (prevId != null) {
            setSelectedFeed(prevId)
            navigate(`/feed/${prevId}`)
          }
          break
        }
        case 'm':
          if (currentArticle) {
            updateState.mutate({ id: currentArticle.id, state: { read: !currentArticle.read } })
          }
          break
        case 's':
          if (currentArticle) {
            updateState.mutate({ id: currentArticle.id, state: { starred: !currentArticle.starred } })
          }
          break
        case 'u':
        case 'd':
          // Thumbs vote (D-01). Cmd/Ctrl/Alt are left to the browser (Cmd+D bookmarks; Pitfall 6).
          // The by-id article carries the vote, so wait for it; never the list row (Pattern 5).
          if (e.metaKey || e.ctrlKey || e.altKey) break
          if (fetchedArticle) press(fetchedArticle, e.key === 'u' ? 1 : -1)
          break
        case 'D':
          // Shift+D opens the topic picker only when the narrow control shows; it never votes (D-13).
          if (e.shiftKey && !(e.metaKey || e.ctrlKey || e.altKey) && fetchedArticle && canNarrow(fetchedArticle)) {
            useFeedbackStore.getState().setNarrowOpen(true)
          }
          break
        case 'o':
          if (currentArticle) openOriginal(currentArticle)
          break
        case 'b':
          if (currentArticle && callbacks.onOpenBoard) {
            callbacks.onOpenBoard()
          }
          break
        case 'v':
          if (currentArticle) {
            saveToRaindrop.mutate(currentArticle.id)
          }
          break
        case 'i':
          // Toggle the reading pane's Why breakdown (D-01); the same state as the Why button.
          if (currentArticle?.interestScore != null) usePriorityStore.getState().toggleWhy()
          break
        case 'r':
          if (callbacks.isPriority) callbacks.onPriorityRefresh?.()
          else if (selectedFeedId) pollFeed.mutate(selectedFeedId)
          break
        case 'A':
          // Explicit route guard: no bulk mark-read from Priority (PRIO-07).
          if (e.shiftKey && selectedFeedId && !callbacks.isPriority) {
            markAllReadInFeed(selectedFeedId)
          }
          break
        case '/':
          e.preventDefault()
          // eslint-disable-next-line no-case-declarations
          const searchInput = document.querySelector('.search-input') as HTMLInputElement
          searchInput?.focus()
          break
        case 'g':
          chordRef.current = 'g'
          chordTimerRef.current = setTimeout(() => { chordRef.current = null }, 1000)
          break
        case '?':
          if (callbacks.onShowShortcuts) callbacks.onShowShortcuts()
          break
        case 'Tab':
          e.preventDefault()
          cycleFocus()
          break
        case 'Escape':
          setSelectedArticle(null)
          setSearchQuery('')
          break
      }
    },
    [articles, currentIndex, currentArticle, fetchedArticle, press, selectedArticleId, selectedFeedId, findUnreadFeedId, navigate, setSelectedArticle, setSelectedFeed, cycleFocus, setKeyboardFocus, setSearchQuery, updateState, markAllReadInFeed, pollFeed, saveToRaindrop, openOriginal, callbacks, keyboardFocus, articleListFontSize, readingFontSize, setArticleListFontSize, setReadingFontSize]
  )

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [handleKeyDown])
}
