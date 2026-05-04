import { useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUIStore } from '../stores/uiStore'
import { usePreferences, FONT_SIZE_STEPS } from '../stores/preferencesStore'
import { useUpdateArticleState, useMarkRead, useSaveToRaindrop } from './useArticles'
import { usePollFeed, useFeeds } from './useFeeds'
import type { Article, Feed } from '../types'

interface KeyboardShortcutCallbacks {
  onOpenBoard?: () => void
  onShowShortcuts?: () => void
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
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)
  const keyboardFocus = useUIStore((s) => s.keyboardFocus)

  const articleListFontSize = usePreferences((s) => s.articleListFontSize)
  const readingFontSize = usePreferences((s) => s.readingFontSize)
  const setArticleListFontSize = usePreferences((s) => s.setArticleListFontSize)
  const setReadingFontSize = usePreferences((s) => s.setReadingFontSize)

  const updateState = useUpdateArticleState()
  const markRead = useMarkRead()
  const pollFeed = usePollFeed()
  const saveToRaindrop = useSaveToRaindrop()
  const { data: feeds = [] } = useFeeds()

  const currentIndex = articles.findIndex((a) => a.id === selectedArticleId)
  const currentArticle = currentIndex >= 0 ? articles[currentIndex] : null

  // Build ordered feed list for n/p navigation
  const feedIds = feeds.map((f: Feed) => f.id)

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
          }
          break
        case 'k':
          if (currentIndex > 0) {
            setSelectedArticle(articles[currentIndex - 1].id)
          }
          break
        case 'Enter':
          // Confirm selection (article is already shown in reading pane)
          break
        case 'n': {
          // Next feed
          if (feedIds.length === 0) break
          const currentFeedIdx = selectedFeedId ? feedIds.indexOf(selectedFeedId) : -1
          const nextFeedIdx = currentFeedIdx < feedIds.length - 1 ? currentFeedIdx + 1 : 0
          setSelectedFeed(feedIds[nextFeedIdx])
          navigate(`/feed/${feedIds[nextFeedIdx]}`)
          break
        }
        case 'p': {
          // Previous feed
          if (feedIds.length === 0) break
          const curFeedIdx = selectedFeedId ? feedIds.indexOf(selectedFeedId) : 0
          const prevFeedIdx = curFeedIdx > 0 ? curFeedIdx - 1 : feedIds.length - 1
          setSelectedFeed(feedIds[prevFeedIdx])
          navigate(`/feed/${feedIds[prevFeedIdx]}`)
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
        case 'o':
          if (currentArticle) window.open(currentArticle.url, '_blank', 'noopener')
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
        case 'r':
          if (selectedFeedId) pollFeed.mutate(selectedFeedId)
          break
        case 'A':
          if (e.shiftKey && selectedFeedId) {
            markRead.mutate({ feedId: selectedFeedId })
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
    [articles, currentIndex, currentArticle, selectedFeedId, feedIds, navigate, setSelectedArticle, setSelectedFeed, cycleFocus, setSearchQuery, updateState, markRead, pollFeed, saveToRaindrop, callbacks, keyboardFocus, articleListFontSize, readingFontSize, setArticleListFontSize, setReadingFontSize]
  )

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [handleKeyDown])
}
