import { useEffect, useRef, useState } from 'react'
import { useMatch } from 'react-router-dom'
import DOMPurify from 'dompurify'
import { useUIStore } from '../stores/uiStore'
import {
  useArticle,
  useExtractedArticle,
  useUpdateArticleState,
  useSaveToRaindrop,
} from '../hooks/useArticles'
import { usePreferences, READING_FONT_PX } from '../stores/preferencesStore'
import { useReadLater, useRemoveArticleFromBoard } from '../hooks/useBoards'
import { BoardManager } from './BoardManager'
import { formatPublishedDate } from '../utils/dates'

interface ReadingPaneProps {
  boardOpen?: boolean
  onBoardClose?: () => void
}

export function ReadingPane({ boardOpen: externalBoardOpen, onBoardClose }: ReadingPaneProps = {}) {
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const keyboardFocus = useUIStore((s) => s.keyboardFocus)
  const { data: article } = useArticle(selectedArticleId)
  const contentRef = useRef<HTMLDivElement>(null)

  // Reader view: null = auto (on only when the feed provided no content for this article).
  const [readerViewChoice, setReaderViewChoice] = useState<boolean | null>(null)
  const hasFeedContent = !!(article?.content || article?.summary)
  const readerView = readerViewChoice ?? (!!article && !hasFeedContent)
  const extracted = useExtractedArticle(article?.id ?? null, readerView)

  // The manual choice is per-article; a newly selected article returns to auto.
  useEffect(() => {
    setReaderViewChoice(null)
  }, [article?.id])

  // When focus moves to the reading pane (e.g. via Enter), focus the scroll
  // container so the keyboard can scroll the article.
  useEffect(() => {
    if (keyboardFocus === 'reading') contentRef.current?.focus()
  }, [keyboardFocus, selectedArticleId])

  const boardRouteMatch = useMatch('/board/:boardId')
  const currentBoardId = boardRouteMatch?.params.boardId
    ? Number(boardRouteMatch.params.boardId)
    : null

  const updateState = useUpdateArticleState()
  const saveToRaindrop = useSaveToRaindrop()
  const autoMarkReadDelay = usePreferences((s) => s.autoMarkReadDelay)
  const readingFontSize = usePreferences((s) => s.readingFontSize)
  const readLater = useReadLater()
  const removeFromBoard = useRemoveArticleFromBoard()
  const [internalBoardOpen, setInternalBoardOpen] = useState(false)
  const boardOpen = externalBoardOpen || internalBoardOpen
  const closeBoardDialog = () => {
    setInternalBoardOpen(false)
    onBoardClose?.()
  }

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

  const bodyHtml = article.content || article.summary || ''
  // Reader view forbids style tags/attributes so publisher styling (e.g. dark backgrounds)
  // is dropped and the app theme applies.
  const sanitizedContent = readerView
    ? extracted.data
      ? DOMPurify.sanitize(extracted.data.contentHtml, {
          FORBID_TAGS: ['style'],
          FORBID_ATTR: ['style'],
        })
      : ''
    : DOMPurify.sanitize(bodyHtml)
  const showLeadImage = !!article.imageUrl && !bodyHtml.includes(article.imageUrl)

  const handleStar = () => {
    updateState.mutate({ id: article.id, state: { starred: !article.starred } })
  }

  const handleToggleRead = () => {
    updateState.mutate({ id: article.id, state: { read: !article.read } })
  }

  const handleOpenOriginal = () => {
    window.open(article.url, '_blank', 'noopener')
  }

  const handleCopyLink = () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(article.url)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = article.url
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
  }

  const handleContentClick = (e: React.MouseEvent) => {
    const link = (e.target as HTMLElement).closest('a')
    if (link?.href) {
      e.preventDefault()
      window.open(link.href, '_blank', 'noopener')
    }
  }

  const handleRaindrop = () => {
    saveToRaindrop.mutate(article.id)
  }

  const handleRemoveFromBoard = () => {
    if (currentBoardId == null) return
    removeFromBoard.mutate(
      { boardId: currentBoardId, articleId: article.id },
      { onSuccess: () => setSelectedArticle(null) }
    )
  }

  return (
    <div className="reading-pane">
      <div className="reading-toolbar">
        <button className="toolbar-btn" onClick={handleStar}>
          {article.starred ? '★ Unstar' : '★ Star'}
        </button>
        <button className="toolbar-btn" onClick={handleToggleRead}>
          {article.read ? '○ Mark Unread' : '● Mark Read'}
        </button>
        <button className="toolbar-btn" onClick={() => setInternalBoardOpen(true)}>📋 Board</button>
        <button className="toolbar-btn" onClick={() => readLater.mutate(article.id)} disabled={readLater.isPending}>
          {readLater.isPending ? '🔖 Saving…' : '🔖 Read Later'}
        </button>
        <button className="toolbar-btn" onClick={handleRaindrop} disabled={saveToRaindrop.isPending}>
          {saveToRaindrop.isPending ? '💧 Saving…' : '💧 Raindrop'}
        </button>
        <button className="toolbar-btn" onClick={handleCopyLink}>🔗 Copy Link</button>
        <button className="toolbar-btn" onClick={() => setReaderViewChoice(!readerView)}>
          {readerView ? '📖 Feed View' : '📖 Reader View'}
        </button>
        {currentBoardId != null && (
          <button
            className="toolbar-btn"
            onClick={handleRemoveFromBoard}
            disabled={removeFromBoard.isPending}
          >
            {removeFromBoard.isPending ? '🗑 Removing…' : '🗑 Remove from board'}
          </button>
        )}
        <button className="toolbar-btn" onClick={handleOpenOriginal} style={{ marginLeft: 'auto' }}>
          ↗ Open Original
        </button>
      </div>

      <div
        ref={contentRef}
        className="reading-content"
        tabIndex={-1}
        style={{ fontSize: `${READING_FONT_PX[readingFontSize]}px`, outline: 'none' }}
      >
        <h1 className="article-title">{article.title}</h1>
        <div className="article-meta">
          {article.author && <span>{article.author} &middot; </span>}
          {article.url && <span>{new URL(article.url).hostname}</span>}
          {article.publishedAt && (
            <span> &middot; {formatPublishedDate(article.publishedAt)}</span>
          )}
        </div>
        {showLeadImage && (
          <img className="article-lead-image" src={article.imageUrl!} alt="" />
        )}
        {readerView && extracted.isPending ? (
          <p className="reader-status">Loading full article…</p>
        ) : readerView && extracted.isError ? (
          <p className="reader-status">
            {"Couldn't load the full article. "}
            <button className="toolbar-btn" onClick={handleOpenOriginal}>↗ Open Original</button>
          </p>
        ) : (
          <div
            className="article-body"
            onClick={handleContentClick}
            dangerouslySetInnerHTML={{ __html: sanitizedContent }}
          />
        )}
      </div>
      <BoardManager open={boardOpen} articleId={article?.id ?? null} onClose={() => closeBoardDialog()} />
    </div>
  )
}
