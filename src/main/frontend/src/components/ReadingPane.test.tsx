import { render, screen, fireEvent, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ReadingPane } from './ReadingPane'
import type { Article, BreakdownRow, InterestBreakdown } from '../types'
import { usePriorityStore } from '../stores/priorityStore'

let mockArticle: Article
const mockUseExtractedArticle = vi.fn()
const mockPress = vi.fn()
const mockOpenOriginal = vi.fn()

// The pane renders without a QueryClientProvider, so the real hook would throw (Pitfall 2).
vi.mock('../hooks/useEngagement', () => ({
  useOpenOriginal: () => mockOpenOriginal,
}))

vi.mock('../hooks/useFeedback', () => ({
  useVoteFeedback: () => ({ press: mockPress, narrow: vi.fn() }),
}))

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

const profileRow = (points: number, levelIndex = 3): BreakdownRow => ({
  kind: 'PROFILE',
  levelIndex,
  exact: points,
  points,
})

const topicRow = (topicId: number, name: string, weight: number, points: number): BreakdownRow => ({
  kind: 'TOPIC',
  topicId,
  name,
  noul: 0.9,
  hinge: 0.8,
  weight,
  exact: points,
  points,
})

const breakdown = (rows: BreakdownRow[], total: number): InterestBreakdown => ({
  raw: total,
  total,
  display: Math.max(0, Math.min(100, total)),
  rows,
  nonMatching: [],
})

const mockRows = (): BreakdownRow[] => [
  profileRow(64),
  topicRow(1, 'Rust', 20, 17),
  topicRow(2, 'WebAssembly', 14, 7),
  topicRow(3, 'Politics', -30, -6),
  topicRow(4, 'Zero', 0, 0),
]

beforeEach(() => {
  mockPress.mockReset()
  mockOpenOriginal.mockReset()
  usePriorityStore.setState({ whyOpen: false })
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

describe('ReadingPane score row', () => {
  it('scoreRowShowsBadgeChipsAndWhy', () => {
    mockArticle = article({ interestScore: 82, interestBreakdown: breakdown(mockRows(), 82) })

    const { container } = renderPane()

    const row = container.querySelector('.score-row')!
    expect(row).not.toBeNull()
    const badge = row.querySelector('.interest-badge')!
    expect(badge.textContent).toBe('82')
    expect(badge.className).toContain('tier-high')
    const chips = Array.from(row.querySelectorAll('.topic-chip'))
    expect(chips.map((c) => c.textContent)).toEqual(['Rust', 'WebAssembly', 'Politics−'])
    expect(chips.map((c) => c.classList.contains('weight-positive'))).toEqual([true, true, false])
    expect(chips[2].classList.contains('weight-negative')).toBe(true)
    const toggle = screen.getByRole('button', { name: 'Why 82? ▸' })
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(toggle.getAttribute('aria-controls')).toBe('why-breakdown')
    expect(row.textContent).toBe('82Rust · WebAssembly · Politics− · Why 82? ▸')
  })

  it('scoreRowSitsBetweenTitleAndMeta', () => {
    mockArticle = article({ interestScore: 82, interestBreakdown: breakdown(mockRows(), 82) })

    const { container } = renderPane()

    const order = Array.from(
      container.querySelectorAll('.article-title, .score-row, .article-meta')
    ).map((el) => el.className)
    expect(order).toEqual(['article-title', 'score-row', 'article-meta'])
  })

  it('unscoredArticleHasNoScoreRow', () => {
    mockArticle = article({ interestScore: null })
    const { container, unmount } = renderPane()
    expect(container.querySelector('.score-row')).toBeNull()
    expect(screen.queryByText(/Why/)).toBeNull()
    unmount()

    mockArticle = article({})
    const second = renderPane()
    expect(second.container.querySelector('.score-row')).toBeNull()
    expect(screen.queryByText(/Why/)).toBeNull()
  })

  it('scoredWithoutMatchedTopicsShowsBadgeAndWhyOnly', () => {
    mockArticle = article({ interestScore: 64, interestBreakdown: breakdown([profileRow(64)], 64) })

    const { container } = renderPane()

    const row = container.querySelector('.score-row')!
    expect(row.querySelector('.interest-badge')!.textContent).toBe('64')
    expect(row.querySelector('.topic-chip')).toBeNull()
    expect(row.textContent).not.toContain(' · ')
    expect(screen.getByRole('button', { name: 'Why 64? ▸' })).toBeInTheDocument()
  })

  it('whyToggleFlipsTheSessionState', () => {
    mockArticle = article({ interestScore: 82, interestBreakdown: breakdown(mockRows(), 82) })
    const { rerender } = renderPane()

    fireEvent.click(screen.getByRole('button', { name: 'Why 82? ▸' }))

    expect(usePriorityStore.getState().whyOpen).toBe(true)
    const open = screen.getByRole('button', { name: 'Why 82? ▾' })
    expect(open.getAttribute('aria-expanded')).toBe('true')

    mockArticle = article({
      id: 2,
      interestScore: 45,
      interestBreakdown: breakdown([profileRow(45, 2)], 45),
    })
    act(() => {
      rerender(
        <MemoryRouter>
          <ReadingPane />
        </MemoryRouter>
      )
    })

    expect(usePriorityStore.getState().whyOpen).toBe(true)
    expect(screen.getByRole('button', { name: 'Why 45? ▾' }).getAttribute('aria-expanded')).toBe('true')
  })

  it('chipCarriesItsFullName', () => {
    const long = 'A very long topic name that should be truncated with an ellipsis'
    mockArticle = article({
      interestScore: 83,
      interestBreakdown: breakdown([profileRow(64), topicRow(1, long, 20, 18), topicRow(2, 'Rust', 10, 1)], 83),
    })

    const { container } = renderPane()

    const chips = Array.from(container.querySelectorAll('.topic-chip'))
    expect(chips.map((c) => c.getAttribute('title'))).toEqual([long, 'Rust'])
  })
})

describe('ReadingPane Why breakdown', () => {
  it('whyOpenShowsTheBreakdown', () => {
    mockArticle = article({ interestScore: 82, interestBreakdown: breakdown(mockRows(), 82) })
    usePriorityStore.setState({ whyOpen: true })
    const open = renderPane()

    const order = Array.from(
      open.container.querySelectorAll('.score-row, #why-breakdown, .article-meta')
    ).map((el) => el.id || el.className)
    expect(order).toEqual(['score-row', 'why-breakdown', 'article-meta'])
    open.unmount()

    usePriorityStore.setState({ whyOpen: false })
    const closed = renderPane()
    expect(closed.container.querySelector('#why-breakdown')).toBeNull()
  })
})

describe('ReadingPane vote buttons', () => {
  it('rendersVoteButtonsAfterStar', () => {
    mockArticle = article({ content: '<p>Body</p>' })
    const { container } = renderPane()

    const labels = Array.from(container.querySelectorAll('.reading-toolbar button')).map(
      (b) => b.textContent
    )
    expect(labels.slice(0, 4)).toEqual(['★ Star', '👍 Up', '👎 Down', '● Mark Read'])

    fireEvent.click(screen.getByRole('button', { name: 'Thumbs up' }))
    expect(mockPress).toHaveBeenCalledTimes(1)
    expect(mockPress).toHaveBeenCalledWith(mockArticle, 1)
  })
})

describe('ReadingPane no-match line', () => {
  it('noticeSitsBetweenToolbarAndContent', () => {
    mockArticle = article({
      title: 'Rust async runtimes',
      interestScore: 64,
      interestBreakdown: breakdown([profileRow(64)], 64),
      feedback: { vote: -1, narrowed: false, topics: [] },
    })
    const onCreateTopic = vi.fn()
    const { container } = render(
      <MemoryRouter>
        <ReadingPane onCreateTopic={onCreateTopic} />
      </MemoryRouter>
    )

    const order = Array.from(
      container.querySelectorAll('.reading-toolbar, .feedback-notice, .reading-content')
    ).map((el) => el.className)
    expect(order).toEqual(['reading-toolbar', 'feedback-notice', 'reading-content'])

    fireEvent.click(screen.getByRole('button', { name: 'Create topic from article' }))
    expect(onCreateTopic).toHaveBeenCalledWith({ description: 'Rust async runtimes', weight: -20 })
  })
})

describe('ReadingPane Open Original', () => {
  it('toolbarOpenOriginalGoesThroughTheHelper', () => {
    mockArticle = article({ content: '<p>Body</p>' })
    renderPane()

    const buttons = screen.getAllByRole('button', { name: '↗ Open Original' })
    expect(buttons).toHaveLength(1)
    fireEvent.click(buttons[0])

    expect(mockOpenOriginal).toHaveBeenCalledTimes(1)
    expect(mockOpenOriginal).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1, url: 'https://example.com/post' })
    )
  })

  it('extractionErrorFallbackGoesThroughTheHelper', () => {
    mockArticle = article({ content: null, summary: null })
    mockUseExtractedArticle.mockReturnValue({ data: undefined, isPending: false, isError: true })
    const { container } = renderPane()

    const fallback = container.querySelector('.reader-status button') as HTMLButtonElement
    expect(fallback).toHaveTextContent('↗ Open Original')
    fireEvent.click(fallback)

    expect(mockOpenOriginal).toHaveBeenCalledTimes(1)
    expect(mockOpenOriginal).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }))
  })
})

describe('ReadingPane unreported opens (CAPT-07)', () => {
  it('inBodyLinkOpensButIsNotReported', () => {
    mockArticle = article({ content: '<p><a href="https://example.com/inner">inner link</a></p>' })
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    renderPane()

    fireEvent.click(screen.getByText('inner link'))

    expect(openSpy).toHaveBeenCalledWith('https://example.com/inner', '_blank', 'noopener')
    expect(mockOpenOriginal).not.toHaveBeenCalled()
    openSpy.mockRestore()
  })

  it('copyLinkIsNotReported', () => {
    mockArticle = article({ content: '<p>Body</p>' })
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    try {
      renderPane()
      fireEvent.click(screen.getByRole('button', { name: '🔗 Copy Link' }))

      expect(writeText).toHaveBeenCalledWith('https://example.com/post')
      expect(mockOpenOriginal).not.toHaveBeenCalled()
      expect(fetchSpy).not.toHaveBeenCalled()
    } finally {
      fetchSpy.mockRestore()
      delete (navigator as { clipboard?: unknown }).clipboard
    }
  })
})
