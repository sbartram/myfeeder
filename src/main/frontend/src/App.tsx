import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import './App.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppShell
          feedPanel={<div style={{ padding: 12, color: '#888' }}>Feed Panel</div>}
          articleList={<div style={{ padding: 12, color: '#888' }}>Article List</div>}
          readingPane={<div style={{ padding: 12, color: '#888' }}>Reading Pane</div>}
        />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
