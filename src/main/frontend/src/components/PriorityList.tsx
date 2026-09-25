import { Fragment, useMemo } from 'react'
import { usePriorityArticles } from '../hooks/usePriorityArticles'
import { InterestBadge } from './InterestBadge'
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
  const { rows, fetchNextPage, hasNextPage, isFetchingNextPage } = usePriorityArticles()
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const searchQuery = useUIStore((s) => s.searchQuery)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)
  const articleListFontSize = usePreferences((s) => s.articleListFontSize)
  const articleItemsStyle = { fontSize: `${ARTICLE_LIST_FONT_PX[articleListFontSize]}px` }

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

  return (
    <div className="article-list">
      <div className="article-list-toolbar">
        <span className="toolbar-title">Priority</span>
        <div className="toolbar-actions" />
      </div>

      <input
        className="search-input"
        type="text"
        placeholder="Filter articles..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
      />

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
            {isFetchingNextPage ? 'Loading...' : 'Load more'}
          </button>
        )}
      </div>
    </div>
  )
}
