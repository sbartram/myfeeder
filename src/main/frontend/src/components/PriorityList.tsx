import { Fragment, useEffect, useMemo } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refreshPriority, usePriorityArticles } from '../hooks/usePriorityArticles'
import { ApiError } from '../api/client'
import { InterestBadge } from './InterestBadge'
import { EmptyState } from './EmptyState'
import { PriorityBanner } from './PriorityBanner'
import { useUIStore } from '../stores/uiStore'
import { usePreferences, ARTICLE_LIST_FONT_PX } from '../stores/preferencesStore'
import type { Article } from '../types'

function formatTime(dateStr: string | null) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const hours = Math.floor(diff / 3600000)
  if (hours < 1) return 'just now'
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

/** The /priority panel: unread articles in the server's ranked order, never re-sorted here. */
export function PriorityList() {
  const {
    rows,
    data,
    error,
    isPending,
    isError,
    isFetching,
    isFetchNextPageError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = usePriorityArticles()
  const qc = useQueryClient()
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const searchQuery = useUIStore((s) => s.searchQuery)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)
  const articleListFontSize = usePreferences((s) => s.articleListFontSize)
  const articleItemsStyle = { fontSize: `${ARTICLE_LIST_FONT_PX[articleListFontSize]}px` }

  // The cursor article is gone (R4): restart the list from page 1.
  useEffect(() => {
    if (isFetchNextPageError && error instanceof ApiError && error.status === 404) {
      void refreshPriority(qc)
    }
  }, [isFetchNextPageError, error, qc])

  const filtered = useMemo(() => {
    if (!searchQuery) return rows
    const q = searchQuery.toLowerCase()
    return rows.filter(
      (a: Article) =>
        a.title.toLowerCase().includes(q) ||
        (a.summary && a.summary.toLowerCase().includes(q))
    )
  }, [rows, searchQuery])

  // The "Not yet scored" separator goes once, before the first displayed unscored row.
  const firstUnscored = filtered.findIndex((a) => a.interestScore == null)

  const refreshing = isFetching && !isFetchingNextPage

  let slot
  if (isPending) {
    slot = <EmptyState message="Loading articles…" />
  } else if (data === undefined && isError) {
    slot = <EmptyState message="Couldn't load the Priority list. Press ↻ Refresh ranking to try again." />
  } else if (rows.length === 0) {
    slot = (
      <EmptyState
        message="All caught up!"
        detail="New unread articles are ranked here as your feeds update."
      />
    )
  } else if (filtered.length === 0) {
    slot = <EmptyState message={`No matches for "${searchQuery}"`} />
  } else {
    slot = (
      <div className="article-items" style={articleItemsStyle}>
        {filtered.map((article, index) => (
          <Fragment key={article.id}>
            {index === firstUnscored && (
              <div className="priority-separator" role="separator" aria-label="Not yet scored">
                Not yet scored
              </div>
            )}
            <div
              className={`article-item ${selectedArticleId === article.id ? 'selected' : ''} ${article.read ? 'read' : ''}`}
              onClick={() => setSelectedArticle(article.id)}
            >
              <div className="article-item-head">
                {article.interestScore != null ? (
                  <InterestBadge score={article.interestScore} />
                ) : (
                  <span className="interest-badge-slot" aria-hidden="true" />
                )}
                <div className="article-item-title">{article.title}</div>
              </div>
              <div className="article-item-meta">
                {formatTime(article.publishedAt)}
                {article.starred && ' starred'}
              </div>
            </div>
          </Fragment>
        ))}

        {hasNextPage && (
          <button className="load-more" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
            {isFetchingNextPage
              ? 'Loading...'
              : isFetchNextPageError
                ? "Couldn't load more. Try again"
                : 'Load more'}
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="article-list">
      <div className="article-list-toolbar">
        <span className="toolbar-title">Priority</span>
        <div className="toolbar-actions">
          <button
            className="toolbar-btn priority-refresh"
            onClick={() => void refreshPriority(qc)}
            disabled={refreshing}
          >
            {refreshing ? '↻ Refreshing…' : '↻ Refresh ranking'}
          </button>
        </div>
      </div>

      <PriorityBanner />

      <input
        className="search-input"
        type="text"
        placeholder="Filter articles..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
      />

      {slot}
    </div>
  )
}
