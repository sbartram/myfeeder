import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ArticleList } from './ArticleList'
import type { Article } from '../types'

const DEFAULT_ITEMS = [{ id: 1, title: 'Test', read: false, publishedAt: '2026-01-01T00:00:00Z' }]
let mockItems: Partial<Article>[] = DEFAULT_ITEMS

vi.mock('../hooks/useArticles', () => ({
  useArticles: () => ({
    data: {
      pages: [{ items: mockItems }],
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

const item = (id: number, interestScore: number | null | undefined, read = false): Partial<Article> => ({
  id,
  title: `Article ${id}`,
  read,
  publishedAt: '2026-01-01T00:00:00Z',
  ...(interestScore === undefined ? {} : { interestScore }),
})

const rowOf = (container: HTMLElement, id: number) =>
  Array.from(container.querySelectorAll<HTMLElement>('.article-item')).find(
    (el) => el.querySelector('.article-item-title')?.textContent === `Article ${id}`
  )!

beforeEach(() => {
  mockItems = DEFAULT_ITEMS
})

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

describe('ArticleList interest badges', () => {
  it('scoredRowsShowTheirBadge', () => {
    mockItems = [item(1, 82), item(2, 45)]
    const { container } = renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    const high = rowOf(container, 1).querySelector('.interest-badge')
    const neutral = rowOf(container, 2).querySelector('.interest-badge')
    expect(high).toHaveTextContent('82')
    expect(high).toHaveClass('tier-high')
    expect(neutral).toHaveTextContent('45')
    expect(neutral).toHaveClass('tier-neutral')
  })

  it('unscoredRowReservesTheSlotWhenAnyRowIsScored', () => {
    mockItems = [item(1, 82), item(2, null)]
    const { container } = renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    const unscored = rowOf(container, 2)
    const slot = unscored.querySelector('.interest-badge-slot')
    expect(slot).not.toBeNull()
    expect(slot).toHaveAttribute('aria-hidden', 'true')
    expect(unscored.querySelector('.interest-badge')).toBeNull()
  })

  it('noSlotWhenNothingIsScored', () => {
    mockItems = [item(1, null), item(2, undefined)]
    const { container } = renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    expect(container.querySelector('.interest-badge')).toBeNull()
    expect(container.querySelector('.interest-badge-slot')).toBeNull()
  })

  it('readRowKeepsItsBadge', () => {
    mockItems = [item(1, 70, true)]
    const { container } = renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    const row = rowOf(container, 1)
    expect(row).toHaveClass('read')
    expect(row.querySelector('.interest-badge')).toHaveTextContent('70')
  })

  it('rowsKeepTheirQueryOrder', () => {
    mockItems = [item(1, 10), item(2, 90), item(3, 50)]
    const { container } = renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    const badges = Array.from(container.querySelectorAll('.article-item .interest-badge')).map(
      (el) => el.textContent
    )
    expect(badges).toEqual(['10', '90', '50'])
    const titles = Array.from(container.querySelectorAll('.article-item-title')).map((el) => el.textContent)
    expect(titles).toEqual(['Article 1', 'Article 2', 'Article 3'])
  })

  it('noZeroForUnscored', () => {
    mockItems = [item(1, null), item(2, undefined)]
    const { container } = renderWithRouter(<ArticleList filters={{}} title="All Articles" />)
    const zeros = Array.from(container.querySelectorAll('.article-items *')).filter(
      (el) => el.children.length === 0 && el.textContent?.trim() === '0'
    )
    expect(zeros).toHaveLength(0)
  })
})
