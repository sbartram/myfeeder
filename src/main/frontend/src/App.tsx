import { useMemo, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, useParams } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { FeedPanel } from './components/FeedPanel'
import { ArticleList } from './components/ArticleList'
import { BoardArticleList } from './components/BoardArticleList'
import { ReadingPane } from './components/ReadingPane'
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts'
import { useArticles } from './hooks/useArticles'
import { useFeeds } from './hooks/useFeeds'
import { useUIStore } from './stores/uiStore'
import { usePreferences } from './stores/preferencesStore'
import { AddFeedDialog } from './components/AddFeedDialog'
import { SettingsDialog } from './components/SettingsDialog'
import { ShortcutOverlay } from './components/ShortcutOverlay'
import { ToastContainer, useToastStore } from './components/Toast'
import { MutationCache } from '@tanstack/react-query'
import './App.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
  mutationCache: new MutationCache({
    onError: (error) => {
      useToastStore.getState().addToast(error.message || 'An error occurred')
    },
  }),
})

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
  const [boardOpen, setBoardOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const hideReadArticles = usePreferences((s) => s.hideReadArticles)
  const sortOrder = usePreferences((s) => s.articleSortOrder)
  const sort = sortOrder === 'oldest-first' ? 'asc' as const : 'desc' as const
  const readFilter = hideReadArticles ? { read: false as const } : {}
  const { data } = useArticles(selectedFeedId ? { feedId: selectedFeedId, sort, ...readFilter } : { sort, ...readFilter })
  const articles = useMemo(() => data?.pages.flatMap((p) => p.articles) ?? [], [data])

  useKeyboardShortcuts(articles, {
    onOpenBoard: () => setBoardOpen(true),
    onShowShortcuts: () => setShortcutsOpen(true),
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
            <Route path="*" element={<AllArticles />} />
          </Routes>
        }
        readingPane={<ReadingPane boardOpen={boardOpen} onBoardClose={() => setBoardOpen(false)} />}
      />
      <AddFeedDialog open={addFeedOpen} onClose={() => setAddFeedOpen(false)} />
      <SettingsDialog open={settingsOpen} onClose={() => setSettingsOpen(false)} />
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
