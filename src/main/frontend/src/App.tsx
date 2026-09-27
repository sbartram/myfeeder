import { useEffect, useMemo, useState } from 'react'
import { QueryClientProvider, useQueryClient } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, useMatch, useParams } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { FeedPanel } from './components/FeedPanel'
import { ArticleList } from './components/ArticleList'
import { BoardArticleList } from './components/BoardArticleList'
import { PriorityList } from './components/PriorityList'
import { ReadingPane } from './components/ReadingPane'
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts'
import { useArticles } from './hooks/useArticles'
import { usePriorityArticles, PRIORITY_KEY, refreshPriority } from './hooks/usePriorityArticles'
import { useFeeds } from './hooks/useFeeds'
import { useUIStore } from './stores/uiStore'
import { usePreferences } from './stores/preferencesStore'
import { AddFeedDialog } from './components/AddFeedDialog'
import { SettingsDialog } from './components/SettingsDialog'
import { InterestsDialog, type TopicDraft } from './components/InterestsDialog'
import { ShortcutOverlay } from './components/ShortcutOverlay'
import { ToastContainer } from './components/Toast'
import { createQueryClient } from './queryClient'
import './App.css'

const queryClient = createQueryClient()

function FeedArticles() {
  const { feedId } = useParams()
  const { data: feeds = [] } = useFeeds()
  const hideReadArticles = usePreferences((s) => s.hideReadArticles)
  const sortOrder = usePreferences((s) => s.articleSortOrder)
  const sort = sortOrder === 'oldest-first' ? 'asc' as const : 'desc' as const
  const feed = feeds.find((f) => f.id === Number(feedId))
  const filters = { feedId: Number(feedId), sort, ...(hideReadArticles ? { read: false } : {}) }
  return <ArticleList filters={filters} title={feed?.title || 'Feed'} feedName={feed?.title} />
}

function FolderArticles() {
  const { folderId } = useParams()
  const { data: feeds = [] } = useFeeds()
  const hideReadArticles = usePreferences((s) => s.hideReadArticles)
  const sortOrder = usePreferences((s) => s.articleSortOrder)
  const sort = sortOrder === 'oldest-first' ? 'asc' as const : 'desc' as const
  const folderFeeds = feeds.filter((f) => f.folderId === Number(folderId))
  const folderName = `Folder`
  const readFilter = hideReadArticles ? { read: false } : {}

  // Show articles from the first feed in the folder, or all if none selected
  // A proper implementation would need a backend endpoint for folder-level queries
  // For now, if there's only one feed in the folder, show that feed's articles
  if (folderFeeds.length === 1) {
    return <ArticleList filters={{ feedId: folderFeeds[0].id, sort, ...readFilter }} title={folderName} />
  }
  // For multiple feeds, show all (backend doesn't support multi-feed filter yet)
  return <ArticleList filters={{ sort, ...readFilter }} title={folderName} />
}

function StarredArticles() {
  const sortOrder = usePreferences((s) => s.articleSortOrder)
  const sort = sortOrder === 'oldest-first' ? 'asc' as const : 'desc' as const
  return <ArticleList filters={{ starred: true, sort }} title="Starred" />
}

function AllArticles() {
  const hideReadArticles = usePreferences((s) => s.hideReadArticles)
  const sortOrder = usePreferences((s) => s.articleSortOrder)
  const sort = sortOrder === 'oldest-first' ? 'asc' as const : 'desc' as const
  return <ArticleList filters={hideReadArticles ? { read: false, sort } : { sort }} title="All Articles" />
}

function BoardArticles() {
  const { boardId } = useParams()
  return <BoardArticleList boardId={Number(boardId)} />
}

function MainLayout() {
  const [addFeedOpen, setAddFeedOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [interestsOpen, setInterestsOpen] = useState(false)
  // One-shot "Create topic from article" draft (D-20): cleared when Interests closes.
  const [interestsDraft, setInterestsDraft] = useState<TopicDraft | null>(null)
  const [boardOpen, setBoardOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const hideReadArticles = usePreferences((s) => s.hideReadArticles)
  const sortOrder = usePreferences((s) => s.articleSortOrder)
  const sort = sortOrder === 'oldest-first' ? 'asc' as const : 'desc' as const
  const readFilter = hideReadArticles ? { read: false as const } : {}
  const { data } = useArticles(selectedFeedId ? { feedId: selectedFeedId, sort, ...readFilter } : { sort, ...readFilter })
  const articles = useMemo(() => data?.pages.flatMap((p) => p.items) ?? [], [data])
  const qc = useQueryClient()
  const isPriority = useMatch('/priority') !== null
  const priority = usePriorityArticles(isPriority)

  // Leaving /priority drops the frozen ranking so re-entry fetches page 1 fresh (D-08).
  // Under StrictMode, development may fetch page 1 twice on first mount (research A7).
  useEffect(() => {
    if (!isPriority) return
    return () => {
      qc.removeQueries({ queryKey: PRIORITY_KEY })
    }
  }, [isPriority, qc])

  useKeyboardShortcuts(isPriority ? priority.rows : articles, {
    onOpenBoard: () => setBoardOpen(true),
    onShowShortcuts: () => setShortcutsOpen(true),
    isPriority,
    onPriorityNextPage: priority.fetchNextNewId,
    onPriorityRefresh: () => void refreshPriority(qc),
  })

  return (
    <>
      <AppShell
        feedPanel={<FeedPanel onAddFeed={() => setAddFeedOpen(true)} onSettings={() => setSettingsOpen(true)} onHelp={() => setShortcutsOpen(true)} />}
        articleList={
          <Routes>
            <Route path="/feed/:feedId" element={<FeedArticles />} />
            <Route path="/folder/:folderId" element={<FolderArticles />} />
            <Route path="/starred" element={<StarredArticles />} />
            <Route path="/boards" element={<AllArticles />} />
            <Route path="/board/:boardId" element={<BoardArticles />} />
            <Route path="/priority" element={<PriorityList onSetUpInterests={() => setInterestsOpen(true)} />} />
            <Route path="*" element={<AllArticles />} />
          </Routes>
        }
        readingPane={
          <ReadingPane
            boardOpen={boardOpen}
            onBoardClose={() => setBoardOpen(false)}
            onCreateTopic={(draft) => {
              setInterestsDraft(draft)
              setInterestsOpen(true)
            }}
          />
        }
      />
      <AddFeedDialog open={addFeedOpen} onClose={() => setAddFeedOpen(false)} />
      <SettingsDialog
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        onOpenInterests={() => {
          setSettingsOpen(false)
          setInterestsOpen(true)
        }}
      />
      <InterestsDialog
        open={interestsOpen}
        draft={interestsDraft}
        onClose={() => {
          setInterestsOpen(false)
          setInterestsDraft(null)
        }}
      />
      <ShortcutOverlay open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} />
      <ToastContainer />
    </>
  )
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/*" element={<MainLayout />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
