import { useEffect, useMemo, useState } from 'react'
import DOMPurify from 'dompurify'
import { useUIStore } from '../stores/uiStore'
import { useArticles, useUpdateArticleState, useSaveToRaindrop } from '../hooks/useArticles'
import { usePreferences } from '../stores/preferencesStore'
import { BoardManager } from './BoardManager'

export function ReadingPane() {
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const { data } = useArticles(
    selectedFeedId ? { feedId: selectedFeedId } : {}
  )

  const article = useMemo(() => {
    if (!selectedArticleId || !data) return null
    for (const page of data.pages) {
      const found = page.articles.find((a) => a.id === selectedArticleId)
      if (found) return found
    }
    return null
  }, [selectedArticleId, data])

  const updateState = useUpdateArticleState()
  const saveToRaindrop = useSaveToRaindrop()
  const autoMarkReadDelay = usePreferences((s) => s.autoMarkReadDelay)
  const [boardOpen, setBoardOpen] = useState(false)

  // Auto-mark as read when article is selected
  useEffect(() => {
    if (article && !article.read && autoMarkReadDelay > 0) {
      const timer = setTimeout(() => {
        updateState.mutate({ id: article.id, state: { read: true } })
      }, autoMarkReadDelay)
      return () => clearTimeout(timer)
    }
  }, [article?.id, autoMarkReadDelay]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!article) {
    return (
      <div className="reading-pane">
        <div className="reading-empty">
          <p>Select an article to read</p>
          <p className="hint">Use j/k to navigate, Enter to select</p>
        </div>
      </div>
    )
  }

  const sanitizedContent = DOMPurify.sanitize(article.content || article.summary || '')

  const handleStar = () => {
    updateState.mutate({ id: article.id, state: { starred: !article.starred } })
  }

  const handleOpenOriginal = () => {
    window.open(article.url, '_blank', 'noopener')
  }

  const handleCopyLink = () => {
    navigator.clipboard.writeText(article.url)
  }

  const handleRaindrop = () => {
    saveToRaindrop.mutate(article.id)
  }

  return (
    <div className="reading-pane">
      <div className="reading-toolbar">
        <button className="toolbar-btn" onClick={handleStar}>
          {article.starred ? 'Unstar' : 'Star'}
        </button>
        <button className="toolbar-btn" onClick={() => setBoardOpen(true)}>Board</button>
        <button className="toolbar-btn" onClick={handleRaindrop}>Raindrop</button>
        <button className="toolbar-btn" onClick={handleCopyLink}>Copy Link</button>
        <button className="toolbar-btn" onClick={handleOpenOriginal} style={{ marginLeft: 'auto' }}>
          Open Original
        </button>
      </div>

      <div className="reading-content">
        <h1 className="article-title">{article.title}</h1>
        <div className="article-meta">
          {article.author && <span>{article.author} &middot; </span>}
          {article.url && <span>{new URL(article.url).hostname}</span>}
          {article.publishedAt && (
            <span> &middot; {new Date(article.publishedAt).toLocaleDateString()}</span>
          )}
        </div>
        <div
          className="article-body"
          dangerouslySetInnerHTML={{ __html: sanitizedContent }}
        />
      </div>
      <BoardManager open={boardOpen} articleId={article?.id ?? null} onClose={() => setBoardOpen(false)} />
    </div>
  )
}
