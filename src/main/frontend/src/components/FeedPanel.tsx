import { useRef, useState, useEffect, useCallback } from 'react'
import { useFeeds, useDeleteFeed, usePollFeed } from '../hooks/useFeeds'
import { EmptyState } from './EmptyState'
import { useFolders, useReorderFolders } from '../hooks/useFolders'
import { useUnreadCounts } from '../hooks/useArticles'
import { useBoards } from '../hooks/useBoards'
import { useUIStore } from '../stores/uiStore'
import { useNavigate, useLocation } from 'react-router-dom'
import { useImportOpml, exportOpml } from '../hooks/useOpml'
import { usePreferences } from '../stores/preferencesStore'
import type { Feed, Folder } from '../types'
import {
  DndContext,
  closestCenter,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

interface FeedPanelProps {
  onAddFeed?: () => void
  onSettings?: () => void
  onHelp?: () => void
}

interface SortableFolderProps {
  folder: Folder
  expanded: boolean
  active: boolean
  unreadSum: number
  onToggle: () => void
  onSelect: () => void
  children: React.ReactNode
}

function SortableFolder({ folder, expanded, active, unreadSum, onToggle, onSelect, children }: SortableFolderProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: folder.id })
  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }
  return (
    <div ref={setNodeRef} style={style}>
      <div
        className={`folder-row ${active ? 'active' : ''}`}
        onClick={onSelect}
        {...attributes}
        {...listeners}
      >
        <span className="folder-toggle"
              onClick={(e) => { e.stopPropagation(); onToggle() }}
              onPointerDown={(e) => e.stopPropagation()}>
          {expanded ? '▼' : '▶'}
        </span>
        <span>{folder.name}</span>
        <span className="count">{unreadSum || ''}</span>
      </div>
      {expanded && children}
    </div>
  )
}

export function FeedPanel({ onAddFeed, onSettings, onHelp }: FeedPanelProps) {
  const { data: feeds = [] } = useFeeds()
  const { data: folders = [] } = useFolders()
  const { data: boards = [] } = useBoards()
  const reorderFolders = useReorderFolders()
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = folders.findIndex((f) => f.id === Number(active.id))
    const newIndex = folders.findIndex((f) => f.id === Number(over.id))
    if (oldIndex < 0 || newIndex < 0) return
    const next = arrayMove(folders, oldIndex, newIndex).map((f) => f.id)
    reorderFolders.mutate(next)
  }
  const { data: counts = {} } = useUnreadCounts()
  const navigate = useNavigate()
  const location = useLocation()
  const activeBoardMatch = location.pathname.match(/^\/board\/(\d+)$/)
  const activeBoardId = activeBoardMatch ? Number(activeBoardMatch[1]) : null
  const importMutation = useImportOpml()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const deleteFeed = useDeleteFeed()
  const pollFeed = usePollFeed()
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; feed: Feed } | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Feed | null>(null)

  const handleContextMenu = useCallback((e: React.MouseEvent, feed: Feed) => {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, feed })
  }, [])

  useEffect(() => {
    if (!contextMenu) return
    const close = () => setContextMenu(null)
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [contextMenu])

  const handleRefreshFeed = () => {
    if (contextMenu) {
      pollFeed.mutate(contextMenu.feed.id)
      setContextMenu(null)
    }
  }

  const handleUnsubscribeFeed = () => {
    if (contextMenu) {
      setConfirmDelete(contextMenu.feed)
      setContextMenu(null)
    }
  }

  const confirmUnsubscribe = () => {
    if (confirmDelete) {
      deleteFeed.mutate(confirmDelete.id)
      if (selectedFeedId === confirmDelete.id) {
        setSelectedFeed(null)
        navigate('/')
      }
      setConfirmDelete(null)
    }
  }

  const handleImportClick = () => fileInputRef.current?.click()
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      importMutation.mutate(file)
      e.target.value = ''
    }
  }

  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const selectedFolderId = useUIStore((s) => s.selectedFolderId)
  const expandedFolders = useUIStore((s) => s.expandedFolders)
  const toggleFolder = useUIStore((s) => s.toggleFolder)
  const setSelectedFeed = useUIStore((s) => s.setSelectedFeed)
  const setSelectedFolder = useUIStore((s) => s.setSelectedFolder)

  const totalUnread = Object.values(counts).reduce((a, b) => a + b, 0)
  const uncategorized = feeds.filter((f) => !f.folderId)

  const feedsByFolder = (folderId: number): Feed[] =>
    feeds.filter((f) => f.folderId === folderId)

  const feedUnread = (feedId: number) => counts[String(feedId)] || 0
  const hideReadFeeds = usePreferences((s) => s.hideReadFeeds)

  const visibleFeeds = (feedList: Feed[]) =>
    hideReadFeeds ? feedList.filter((f) => feedUnread(f.id) > 0 || f.id === selectedFeedId) : feedList

  const handleAllClick = () => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate('/')
  }

  const handleStarredClick = () => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate('/starred')
  }

  const handleBoardClick = (boardId: number) => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate(`/board/${boardId}`)
  }

  const handleFeedClick = (feedId: number) => {
    setSelectedFeed(feedId)
    navigate(`/feed/${feedId}`)
  }

  const handleFolderClick = (folderId: number) => {
    setSelectedFolder(folderId)
    navigate(`/folder/${folderId}`)
  }

  return (
    <div className="feed-panel">
      <div className="smart-views">
        <div className={`smart-view ${!selectedFeedId && !selectedFolderId ? 'active' : ''}`}
             onClick={handleAllClick}>
          <span>All Articles</span>
          {totalUnread > 0 && <span className="count">{totalUnread}</span>}
        </div>
        <div className="smart-view" onClick={handleStarredClick}>
          <span>Starred</span>
        </div>
      </div>

      <div className="feed-tree">
        {feeds.length === 0 ? (
          <EmptyState
            message="Add your first feed to get started"
            action={onAddFeed ? { label: '+ Add Feed', onClick: onAddFeed } : undefined}
          />
        ) : (
          <>
            <div className="section-label">FOLDERS &amp; FEEDS</div>

        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext items={folders.map((f) => f.id)} strategy={verticalListSortingStrategy}>
            {folders.map((folder) => (
              <SortableFolder
                key={folder.id}
                folder={folder}
                expanded={expandedFolders.has(folder.id)}
                active={selectedFolderId === folder.id}
                unreadSum={visibleFeeds(feedsByFolder(folder.id)).reduce((sum, f) => sum + feedUnread(f.id), 0)}
                onToggle={() => toggleFolder(folder.id)}
                onSelect={() => handleFolderClick(folder.id)}
              >
                {visibleFeeds(feedsByFolder(folder.id)).map((feed) => (
                  <div key={feed.id}
                       className={`feed-row ${selectedFeedId === feed.id ? 'active' : ''}`}
                       onClick={() => handleFeedClick(feed.id)}
                       onContextMenu={(e) => handleContextMenu(e, feed)}>
                    <span>
                      {feed.errorCount >= 3 && <span className="feed-error-icon" title={feed.lastError || 'Feed error'}>!</span>}
                      {feed.title}
                    </span>
                    {feedUnread(feed.id) > 0 && (
                      <span className="count">{feedUnread(feed.id)}</span>
                    )}
                  </div>
                ))}
              </SortableFolder>
            ))}
          </SortableContext>
        </DndContext>

        {visibleFeeds(uncategorized).map((feed) => (
          <div key={feed.id}
               className={`feed-row ${selectedFeedId === feed.id ? 'active' : ''}`}
               onClick={() => handleFeedClick(feed.id)}
               onContextMenu={(e) => handleContextMenu(e, feed)}>
            <span>
              {feed.errorCount >= 3 && <span className="feed-error-icon" title={feed.lastError || 'Feed error'}>!</span>}
              {feed.title}
            </span>
            {feedUnread(feed.id) > 0 && (
              <span className="count">{feedUnread(feed.id)}</span>
            )}
          </div>
        ))}
          </>
        )}

        {boards.length > 0 && (
          <>
            <div className="section-label">BOARDS</div>
            {boards.map((board) => (
              <div key={board.id}
                   className={`feed-row ${activeBoardId === board.id ? 'active' : ''}`}
                   onClick={() => handleBoardClick(board.id)}>
                <span>{board.name}</span>
              </div>
            ))}
          </>
        )}
      </div>

      {contextMenu && (
        <div className="feed-context-menu" style={{ top: contextMenu.y, left: contextMenu.x }}>
          <button className="feed-context-menu-item" onClick={handleRefreshFeed}>
            Refresh
          </button>
          <button className="feed-context-menu-item feed-context-menu-danger" onClick={handleUnsubscribeFeed}>
            Unsubscribe
          </button>
        </div>
      )}

      {confirmDelete && (
        <div className="dialog-overlay" onClick={() => setConfirmDelete(null)}>
          <div className="dialog" onClick={(e) => e.stopPropagation()}>
            <h2>Unsubscribe from feed?</h2>
            <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 16 }}>
              This will remove <strong>{confirmDelete.title}</strong> and all its articles. This cannot be undone.
            </p>
            <div className="dialog-actions">
              <button className="btn-secondary" onClick={() => setConfirmDelete(null)}>Cancel</button>
              <button className="btn-primary" style={{ background: 'var(--toast-error-text)' }} onClick={confirmUnsubscribe}>
                Unsubscribe
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="feed-panel-footer">
        <button className="footer-btn" onClick={onAddFeed}>+ Add Feed</button>
        <button className="footer-btn" onClick={handleImportClick}>Import</button>
        <button className="footer-btn" onClick={exportOpml}>Export</button>
        <button className="footer-btn" onClick={onSettings}>Settings</button>
        <button className="footer-btn" onClick={onHelp}>Help</button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".opml,.xml"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </div>
    </div>
  )
}
