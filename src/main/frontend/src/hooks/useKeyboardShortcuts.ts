import { useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUIStore } from '../stores/uiStore'
import { useUpdateArticleState, useMarkRead } from './useArticles'
import { usePollFeed } from './useFeeds'
import type { Article } from '../types'

export function useKeyboardShortcuts(articles: Article[]) {
  const navigate = useNavigate()
  const chordRef = useRef<string | null>(null)
  const chordTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const cycleFocus = useUIStore((s) => s.cycleFocus)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)

  const updateState = useUpdateArticleState()
  const markRead = useMarkRead()
  const pollFeed = usePollFeed()

  const currentIndex = articles.findIndex((a) => a.id === selectedArticleId)
  const currentArticle = currentIndex >= 0 ? articles[currentIndex] : null

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
        case 'v':
          if (currentArticle) {
            // Dispatch custom event for ReadingPane to handle Raindrop save
            document.dispatchEvent(new CustomEvent('save-to-raindrop', { detail: currentArticle.id }))
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
          // TODO: show shortcut overlay in future
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
    [articles, currentIndex, currentArticle, selectedFeedId, navigate, setSelectedArticle, cycleFocus, setSearchQuery, updateState, markRead, pollFeed]
  )

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [handleKeyDown])
}
