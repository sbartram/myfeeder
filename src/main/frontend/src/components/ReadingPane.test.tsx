import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ReadingPane } from './ReadingPane'
import type { Article } from '../types'

let mockArticle: Article
const mockUseExtractedArticle = vi.fn()

vi.mock('../hooks/useArticles', () => ({
  useArticle: () => ({ data: mockArticle }),
  useUpdateArticleState: () => ({ mutate: vi.fn() }),
  useSaveToRaindrop: () => ({ mutate: vi.fn(), isPending: false }),
  useExtractedArticle: (id: number | null, enabled: boolean) =>
    mockUseExtractedArticle(id, enabled),
}))

vi.mock('../hooks/useBoards', () => ({
  useReadLater: () => ({ mutate: vi.fn(), isPending: false }),
  useRemoveArticleFromBoard: () => ({ mutate: vi.fn(), isPending: false }),
}))

vi.mock('./BoardManager', () => ({ BoardManager: () => null }))

vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({
      selectedArticleId: 1,
      setSelectedArticle: vi.fn(),
      keyboardFocus: 'list',
    }),
}))

vi.mock('../stores/preferencesStore', () => ({
  usePreferences: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ autoMarkReadDelay: 0, readingFontSize: 'medium' }),
  READING_FONT_PX: { small: 14, medium: 16, large: 18 },
}))

const article = (overrides: Partial<Article>): Article =>
  ({
    id: 1,
    feedId: 1,
    guid: 'g-1',
    title: 'Test Article',
    url: 'https://example.com/post',
    author: null,
    content: null,
    summary: null,
    imageUrl: null,
    publishedAt: '2026-08-01T10:00:00Z',
    fetchedAt: '2026-08-01T10:05:00Z',
    read: false,
    starred: false,
    ...overrides,
  }) as Article

const renderPane = () =>
  render(
    <MemoryRouter>
      <ReadingPane />
    </MemoryRouter>
  )

beforeEach(() => {
  mockUseExtractedArticle
    .mockReset()
    .mockReturnValue({ data: undefined, isPending: false, isError: false })
})

describe('ReadingPane reader view', () => {
  it('auto-loads reader view when the article has no content or summary', () => {
    mockArticle = article({ content: null, summary: null })
    mockUseExtractedArticle.mockReturnValue({
      data: { title: 'T', contentHtml: '<p style="background:#000;color:#fff">Extracted body</p>' },
      isPending: false,
      isError: false,
    })

    renderPane()

    expect(mockUseExtractedArticle).toHaveBeenCalledWith(1, true)
    const p = screen.getByText('Extracted body')
    expect(p).toBeInTheDocument()
    expect(p.getAttribute('style')).toBeNull()
  })

  it('shows feed content without fetching extraction when content exists', () => {
    mockArticle = article({ content: '<p>Feed body</p>' })

    renderPane()

    expect(mockUseExtractedArticle).toHaveBeenCalledWith(1, false)
    expect(screen.getByText('Feed body')).toBeInTheDocument()
  })

  it('switches to extracted content when Reader View is toggled', () => {
    mockArticle = article({ content: '<p>Feed body</p>' })
    mockUseExtractedArticle.mockReturnValue({
      data: { title: 'T', contentHtml: '<p>Extracted body</p>' },
      isPending: false,
      isError: false,
    })

    renderPane()
    fireEvent.click(screen.getByText('📖 Reader View'))

    expect(mockUseExtractedArticle).toHaveBeenLastCalledWith(1, true)
    expect(screen.getByText('Extracted body')).toBeInTheDocument()
  })

  it('shows a loading state while extracting', () => {
    mockArticle = article({ content: null, summary: null })
    mockUseExtractedArticle.mockReturnValue({ data: undefined, isPending: true, isError: false })

    renderPane()

    expect(screen.getByText(/loading full article/i)).toBeInTheDocument()
  })

  it('shows a fallback message when extraction fails', () => {
    mockArticle = article({ content: null, summary: null })
    mockUseExtractedArticle.mockReturnValue({ data: undefined, isPending: false, isError: true })

    renderPane()

    expect(screen.getByText(/couldn't load the full article/i)).toBeInTheDocument()
  })
})
