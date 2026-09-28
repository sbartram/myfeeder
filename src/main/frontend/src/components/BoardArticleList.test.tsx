import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BoardArticleList } from './BoardArticleList'
import { useUIStore } from '../stores/uiStore'
import type { Article } from '../types'

let mockItems: Partial<Article>[] = []

vi.mock('../hooks/useBoards', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useBoards')>()),
  useBoardArticles: () => ({
    data: { pages: [{ items: mockItems, nextCursor: null }] },
    fetchNextPage: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
  }),
}))

const item = (id: number, interestScore: number | null, read = false): Partial<Article> => ({
  id,
  title: `Board article ${id}`,
  read,
  publishedAt: '2026-01-01T00:00:00Z',
  interestScore,
})

const rowOf = (container: HTMLElement, id: number) =>
  Array.from(container.querySelectorAll<HTMLElement>('.article-item')).find(
    (el) => el.querySelector('.article-item-title')?.textContent === `Board article ${id}`
  )!

const renderBoard = () =>
  render(
    <MemoryRouter>
      <BoardArticleList boardId={1} />
    </MemoryRouter>
  )

beforeEach(() => {
  mockItems = []
  useUIStore.setState({ searchQuery: '', selectedArticleId: null })
})

describe('BoardArticleList interest badges', () => {
  it('boardRowsShowBadges', () => {
    mockItems = [item(1, 100), item(2, null)]
    const { container } = renderBoard()
    const badge = rowOf(container, 1).querySelector('.interest-badge')
    expect(badge).toHaveTextContent('100')
    expect(badge).toHaveClass('tier-high')
    const unscored = rowOf(container, 2)
    expect(unscored.querySelector('.interest-badge')).toBeNull()
    expect(unscored.querySelector('.interest-badge-slot')).toHaveAttribute('aria-hidden', 'true')
  })

  it('boardWithoutScoresHasNoSlot', () => {
    mockItems = [item(1, null), item(2, null)]
    const { container } = renderBoard()
    expect(container.querySelector('.interest-badge')).toBeNull()
    expect(container.querySelector('.interest-badge-slot')).toBeNull()
  })

  it('boardReadRowKeepsItsBadge', () => {
    mockItems = [item(1, 39, true)]
    const { container } = renderBoard()
    const row = rowOf(container, 1)
    expect(row).toHaveClass('read')
    const badge = row.querySelector('.interest-badge')
    expect(badge).toHaveTextContent('39')
    expect(badge).toHaveClass('tier-low')
  })
})
