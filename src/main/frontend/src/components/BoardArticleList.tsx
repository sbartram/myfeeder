import { useMemo } from 'react'
import { useBoardArticles } from '../hooks/useBoards'
import { useUIStore } from '../stores/uiStore'
import type { Article } from '../types'

interface BoardArticleListProps {
  boardId: number
}

export function BoardArticleList({ boardId }: BoardArticleListProps) {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useBoardArticles(boardId)
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const searchQuery = useUIStore((s) => s.searchQuery)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)

  const allArticles = useMemo(
    () => data?.pages.flatMap((p) => p.articles) ?? [],
    [data]
  )

  const filtered = useMemo(() => {
    if (!searchQuery) return allArticles
    const q = searchQuery.toLowerCase()
    return allArticles.filter(
      (a) =>
        a.title.toLowerCase().includes(q) ||
        (a.summary && a.summary.toLowerCase().includes(q))
    )
  }, [allArticles, searchQuery])

  const handleArticleClick = (article: Article) => {
    setSelectedArticle(article.id)
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
    return (
      <div className="article-list">
        <div className="article-list-toolbar">
          <span className="toolbar-title">Board</span>
        </div>
        <div className="empty-state">
          {searchQuery ? `No matches for "${searchQuery}"` : 'This board is empty — save articles to curate'}
        </div>
      </div>
    )
  }

  return (
    <div className="article-list">
      <div className="article-list-toolbar">
        <span className="toolbar-title">Board</span>
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
    </div>
  )
}
