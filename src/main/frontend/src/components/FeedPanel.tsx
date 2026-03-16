import { useRef } from 'react'
import { useFeeds } from '../hooks/useFeeds'
import { EmptyState } from './EmptyState'
import { useFolders } from '../hooks/useFolders'
import { useUnreadCounts } from '../hooks/useArticles'
import { useUIStore } from '../stores/uiStore'
import { useNavigate } from 'react-router-dom'
import { useImportOpml, exportOpml } from '../hooks/useOpml'
import { usePreferences } from '../stores/preferencesStore'
import type { Feed } from '../types'

interface FeedPanelProps {
  onAddFeed?: () => void
  onSettings?: () => void
}

export function FeedPanel({ onAddFeed, onSettings }: FeedPanelProps) {
  const { data: feeds = [] } = useFeeds()
  const { data: folders = [] } = useFolders()
  const { data: counts = {} } = useUnreadCounts()
  const navigate = useNavigate()
  const importMutation = useImportOpml()
  const fileInputRef = useRef<HTMLInputElement>(null)

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

  const handleBoardsClick = () => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate('/boards')
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
        <div className="smart-view" onClick={handleBoardsClick}>
          <span>Boards</span>
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

        {folders.map((folder) => (
          <div key={folder.id}>
            <div className={`folder-row ${selectedFolderId === folder.id ? 'active' : ''}`}
                 onClick={() => handleFolderClick(folder.id)}>
              <span className="folder-toggle"
                    onClick={(e) => { e.stopPropagation(); toggleFolder(folder.id) }}>
                {expandedFolders.has(folder.id) ? '\u25BC' : '\u25B6'}
              </span>
              <span>{folder.name}</span>
              <span className="count">
                {visibleFeeds(feedsByFolder(folder.id)).reduce((sum, f) => sum + feedUnread(f.id), 0) || ''}
              </span>
            </div>
            {expandedFolders.has(folder.id) &&
              visibleFeeds(feedsByFolder(folder.id)).map((feed) => (
                <div key={feed.id}
                     className={`feed-row ${selectedFeedId === feed.id ? 'active' : ''}`}
                     onClick={() => handleFeedClick(feed.id)}>
                  <span>
                    {feed.errorCount > 0 && <span className="feed-error-icon" title={feed.lastError || 'Feed error'}>!</span>}
                    {feed.title}
                  </span>
                  {feedUnread(feed.id) > 0 && (
                    <span className="count">{feedUnread(feed.id)}</span>
                  )}
                </div>
              ))}
          </div>
        ))}

        {visibleFeeds(uncategorized).map((feed) => (
          <div key={feed.id}
               className={`feed-row ${selectedFeedId === feed.id ? 'active' : ''}`}
               onClick={() => handleFeedClick(feed.id)}>
            <span>
              {feed.errorCount > 0 && <span className="feed-error-icon" title={feed.lastError || 'Feed error'}>!</span>}
              {feed.title}
            </span>
            {feedUnread(feed.id) > 0 && (
              <span className="count">{feedUnread(feed.id)}</span>
            )}
          </div>
        ))}
          </>
        )}
      </div>

      <div className="feed-panel-footer">
        <button className="footer-btn" onClick={onAddFeed}>+ Add Feed</button>
        <button className="footer-btn" onClick={handleImportClick}>Import</button>
        <button className="footer-btn" onClick={exportOpml}>Export</button>
        <button className="footer-btn" onClick={onSettings}>Settings</button>
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
