import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, useParams } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { FeedPanel } from './components/FeedPanel'
import { ArticleList } from './components/ArticleList'
import { ReadingPane } from './components/ReadingPane'
import './App.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
})

function FeedArticles() {
  const { feedId } = useParams()
  return <ArticleList filters={{ feedId: Number(feedId) }} title="Feed" />
}

function StarredArticles() {
  return <ArticleList filters={{ starred: true }} title="Starred" />
}

function AllArticles() {
  return <ArticleList filters={{}} title="All Articles" />
}

function MainLayout() {
  return (
    <AppShell
      feedPanel={<FeedPanel />}
      articleList={
        <Routes>
          <Route path="/feed/:feedId" element={<FeedArticles />} />
          <Route path="/folder/:folderId" element={<AllArticles />} />
          <Route path="/starred" element={<StarredArticles />} />
          <Route path="/boards" element={<AllArticles />} />
          <Route path="/board/:boardId" element={<AllArticles />} />
          <Route path="*" element={<AllArticles />} />
        </Routes>
      }
      readingPane={<ReadingPane />}
    />
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
