import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi } from 'vitest'
import { ArticleList } from './ArticleList'

vi.mock('../hooks/useArticles', () => ({
  useArticles: () => ({
    data: {
      pages: [{ articles: [{ id: 1, title: 'Test', read: false, publishedAt: '2026-01-01T00:00:00Z' }] }],
    },
    fetchNextPage: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
  }),
  useMarkRead: () => ({ mutate: vi.fn() }),
  useUnreadCounts: () => ({ data: {} }),
}))

vi.mock('../hooks/useFeeds', () => ({
  useFeeds: () => ({ data: [] }),
}))

vi.mock('../hooks/useFolders', () => ({
  useFolders: () => ({ data: [] }),
}))

vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const state = {
      selectedArticleId: null,
      setSelectedArticle: vi.fn(),
      setSelectedFeed: vi.fn(),
      searchQuery: '',
      setSearchQuery: vi.fn(),
    }
    return selector(state)
  },
}))

const renderWithRouter = (ui: React.ReactElement) =>
  render(<MemoryRouter>{ui}</MemoryRouter>)

describe('ArticleList split button', () => {
  it('shows dropdown toggle when feedId is set', () => {
    renderWithRouter(<ArticleList filters={{ feedId: 1 }} title="Test Feed" feedName="Test Feed" />)
    expect(screen.getByLabelText('More mark-read options')).toBeInTheDocument()
  })

  it('hides dropdown toggle when no feedId', () => {
    renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    expect(screen.queryByLabelText('More mark-read options')).not.toBeInTheDocument()
  })

  it('opens dropdown menu and shows "Mark older than…" option', () => {
    renderWithRouter(<ArticleList filters={{ feedId: 1 }} title="Test Feed" feedName="Test Feed" />)
    fireEvent.click(screen.getByLabelText('More mark-read options'))
    expect(screen.getByText('Mark older than…')).toBeInTheDocument()
  })
})
