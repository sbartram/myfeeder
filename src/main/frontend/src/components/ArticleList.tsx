import { useMemo, useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useArticles, useMarkRead, useUnreadCounts } from '../hooks/useArticles'
import { useFeeds } from '../hooks/useFeeds'
import { useFolders } from '../hooks/useFolders'
import { useUIStore } from '../stores/uiStore'
import { EmptyState } from './EmptyState'
import { MarkOlderReadDialog } from './MarkOlderReadDialog'
import type { Article, ArticleFilters, Feed } from '../types'

interface ArticleListProps {
  filters: ArticleFilters
  title: string
  feedName?: string
}

export function ArticleList({ filters, title, feedName }: ArticleListProps) {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useArticles(filters)
  const markRead = useMarkRead()
  const navigate = useNavigate()
  const { data: feeds = [] } = useFeeds()
  const { data: folders = [] } = useFolders()
  const { data: counts = {} } = useUnreadCounts()
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const setSelectedFeed = useUIStore((s) => s.setSelectedFeed)
  const searchQuery = useUIStore((s) => s.searchQuery)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)

  const orderedFeeds: Feed[] = useMemo(() => {
    const list: Feed[] = []
    folders.forEach((folder) => {
      list.push(...feeds.filter((f) => f.folderId === folder.id))
    })
    list.push(...feeds.filter((f) => !f.folderId))
    return list
  }, [feeds, folders])
  const [showOlderDialog, setShowOlderDialog] = useState(false)
  const [showDropdown, setShowDropdown] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!showDropdown) return
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showDropdown])

  const allArticles = useMemo(
    () => data?.pages.flatMap((p) => p.articles) ?? [],
    [data]
  )

  // Preserve selected article (and its position) so it stays visible after being marked read
  const [preserved, setPreserved] = useState<{ article: Article; index: number } | null>(null)
  useEffect(() => {
    if (selectedArticleId) {
      const idx = allArticles.findIndex((a) => a.id === selectedArticleId)
      if (idx >= 0) setPreserved({ article: { ...allArticles[idx] }, index: idx })
    } else {
      setPreserved(null)
    }
  }, [selectedArticleId]) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    let articles: Article[] = allArticles
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      articles = articles.filter(
        (a) =>
          a.title.toLowerCase().includes(q) ||
          (a.summary && a.summary.toLowerCase().includes(q))
      )
    }
    // If the selected article was filtered out (e.g. marked read), reinsert it at its original position
    if (preserved && !articles.some((a) => a.id === preserved.article.id)) {
      const next = articles.slice()
      const insertAt = Math.min(preserved.index, next.length)
      next.splice(insertAt, 0, { ...preserved.article, read: true })
      articles = next
    }
    return articles
  }, [allArticles, searchQuery, preserved])

  const handleArticleClick = (article: Article) => {
    setSelectedArticle(article.id)
  }

  const findNextUnreadFeedId = (currentFeedId: number): number | null => {
    const idx = orderedFeeds.findIndex((f) => f.id === currentFeedId)
    if (idx < 0) return null
    const candidates = [...orderedFeeds.slice(idx + 1), ...orderedFeeds.slice(0, idx)]
    return candidates.find((f) => (counts[String(f.id)] || 0) > 0)?.id ?? null
  }

  const handleMarkAllRead = () => {
    if (filters.feedId) {
      const nextId = findNextUnreadFeedId(filters.feedId)
      markRead.mutate(
        { feedId: filters.feedId },
        {
          onSuccess: () => {
            if (nextId != null) {
              setSelectedFeed(nextId)
              setSelectedArticle(null)
              navigate(`/feed/${nextId}`)
            }
          },
        },
      )
    } else {
      const ids = allArticles.filter((a) => !a.read).map((a) => a.id)
      if (ids.length > 0) markRead.mutate({ articleIds: ids })
    }
  }

  const handleMarkOlderRead = (days: number) => {
    if (filters.feedId != null) {
      markRead.mutate({ feedId: filters.feedId, olderThanDays: days })
    } else {
      console.error('handleMarkOlderRead called without feedId')
    }
    setShowOlderDialog(false)
  }

  const formatTime = (dateStr: string | null) => {
    if (!dateStr) return ''
    const diff = Date.now() - new Date(dateStr).getTime()
    const hours = Math.floor(diff / 3600000)
    if (hours < 1) return 'just now'
    if (hours < 24) return `${hours}h ago`
    return `${Math.floor(hours / 24)}d ago`
  }

  if (filtered.length === 0) {
    let emptyMessage = 'No articles yet'
    if (searchQuery) {
      emptyMessage = `No matches for "${searchQuery}"`
    } else if (filters.starred) {
      emptyMessage = 'No starred articles'
    } else if (filters.read === false) {
      emptyMessage = 'All caught up!'
    } else if (allArticles.length > 0) {
      emptyMessage = 'No matching articles'
    }

    return (
      <div className="article-list">
        <div className="article-list-toolbar">
          <span className="toolbar-title">{title}</span>
        </div>
        <EmptyState message={emptyMessage} />
      </div>
    )
  }

  return (
    <div className="article-list">
      <div className="article-list-toolbar">
        <span className="toolbar-title">{title}</span>
        <div className="toolbar-actions">
          <div className="split-btn-group" ref={dropdownRef}>
            <button className="toolbar-btn" onClick={handleMarkAllRead}>Mark all read</button>
            {filters.feedId && (
              <button
                className="toolbar-btn split-btn-toggle"
                onClick={() => setShowDropdown((v) => !v)}
                aria-label="More mark-read options"
              >
                ▾
              </button>
            )}
            {showDropdown && (
              <div className="split-btn-menu" onClick={() => setShowDropdown(false)}>
                <button className="split-btn-menu-item" onClick={() => setShowOlderDialog(true)}>
                  Mark older than…
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      <input
        className="search-input"
        type="text"
        placeholder="Filter articles..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
      />

      <div className="article-items">
        {filtered.map((article) => (
          <div
            key={article.id}
            className={`article-item ${selectedArticleId === article.id ? 'selected' : ''} ${article.read ? 'read' : ''}`}
            onClick={() => handleArticleClick(article)}
          >
            <div className="article-item-title">{article.title}</div>
            <div className="article-item-meta">
              {formatTime(article.publishedAt)}
              {article.starred && ' starred'}
            </div>
          </div>
        ))}

        {hasNextPage && (
          <button className="load-more" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
            {isFetchingNextPage ? 'Loading...' : 'Load more'}
          </button>
        )}
      </div>
      <MarkOlderReadDialog
        open={showOlderDialog}
        feedName={feedName || title}
        onConfirm={handleMarkOlderRead}
        onClose={() => setShowOlderDialog(false)}
      />
    </div>
  )
}
