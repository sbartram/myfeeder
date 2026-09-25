import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { FeedPanel } from './FeedPanel'

const createFolderMutate = vi.fn()
const { setSelectedFeed, setSelectedFolder } = vi.hoisted(() => ({
  setSelectedFeed: vi.fn(),
  setSelectedFolder: vi.fn(),
}))

vi.mock('../hooks/useFeeds', () => ({
  useFeeds: () => ({ data: [{ id: 1, title: 'Feed A', folderId: null, errorCount: 0 }] }),
  useDeleteFeed: () => ({ mutate: vi.fn() }),
  usePollFeed: () => ({ mutate: vi.fn() }),
  useMoveFeedToFolder: () => ({ mutate: vi.fn() }),
}))

vi.mock('../hooks/useFolders', () => ({
  useFolders: () => ({ data: [] }),
  useReorderFolders: () => ({ mutate: vi.fn() }),
  useCreateFolder: () => ({ mutate: createFolderMutate }),
}))

vi.mock('../hooks/useArticles', () => ({
  useUnreadCounts: () => ({ data: {} }),
}))

vi.mock('../hooks/useBoards', () => ({
  useBoards: () => ({ data: [] }),
}))

vi.mock('../hooks/useVersion', () => ({
  useVersion: () => ({ data: undefined }),
}))

vi.mock('../hooks/useOpml', () => ({
  useImportOpml: () => ({ mutate: vi.fn() }),
  exportOpml: vi.fn(),
}))

vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const state = {
      selectedFeedId: null,
      selectedFolderId: null,
      expandedFolders: new Set<number>(),
      toggleFolder: vi.fn(),
      setSelectedFeed,
      setSelectedFolder,
    }
    return selector(state)
  },
}))

vi.mock('../stores/preferencesStore', () => ({
  usePreferences: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ hideReadFeeds: false }),
}))

const renderPanel = () =>
  render(
    <MemoryRouter>
      <FeedPanel />
    </MemoryRouter>
  )

function LocationProbe() {
  return <div data-testid="location">{useLocation().pathname}</div>
}

const renderPanelAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <FeedPanel />
      <LocationProbe />
    </MemoryRouter>
  )

const smartView = (label: string) => screen.getByText(label, { selector: '.smart-view > span' }).parentElement!

describe('FeedPanel Priority smart view', () => {
  beforeEach(() => {
    setSelectedFeed.mockClear()
    setSelectedFolder.mockClear()
  })

  it('priorityIsTheFirstSmartViewWithoutACount', () => {
    const { container } = renderPanelAt('/')
    const first = container.querySelector('.smart-views .smart-view')!
    expect(first.textContent).toBe('Priority')
    expect(first.querySelector('.count')).toBeNull()
  })

  it('priorityIsActiveOnlyOnItsRoute', () => {
    const { unmount } = renderPanelAt('/priority')
    expect(smartView('Priority')).toHaveClass('active')
    expect(smartView('All Articles')).not.toHaveClass('active')
    unmount()

    renderPanelAt('/')
    expect(smartView('Priority')).not.toHaveClass('active')
    expect(smartView('All Articles')).toHaveClass('active')
  })

  it('clickingPriorityNavigates', () => {
    renderPanelAt('/')
    fireEvent.click(smartView('Priority'))
    expect(screen.getByTestId('location')).toHaveTextContent('/priority')
    expect(setSelectedFeed).toHaveBeenCalledWith(null)
    expect(setSelectedFolder).toHaveBeenCalledWith(null)
  })
})

describe('FeedPanel new folder button', () => {
  beforeEach(() => {
    createFolderMutate.mockClear()
  })

  it('shows the new folder button next to the section label', () => {
    renderPanel()
    expect(screen.getByLabelText('New folder')).toBeInTheDocument()
  })

  it('opens the inline input when clicked', () => {
    renderPanel()
    fireEvent.click(screen.getByLabelText('New folder'))
    expect(screen.getByPlaceholderText('Folder name')).toBeInTheDocument()
  })

  it('creates a folder with the trimmed name on Enter', () => {
    renderPanel()
    fireEvent.click(screen.getByLabelText('New folder'))
    const input = screen.getByPlaceholderText('Folder name')
    fireEvent.change(input, { target: { value: '  Tech  ' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(createFolderMutate).toHaveBeenCalledWith('Tech', expect.anything())
  })

  it('does not create a folder when the name is empty', () => {
    renderPanel()
    fireEvent.click(screen.getByLabelText('New folder'))
    const input = screen.getByPlaceholderText('Folder name')
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(createFolderMutate).not.toHaveBeenCalled()
  })

  it('closes the input on Escape without creating', () => {
    renderPanel()
    fireEvent.click(screen.getByLabelText('New folder'))
    const input = screen.getByPlaceholderText('Folder name')
    fireEvent.change(input, { target: { value: 'Tech' } })
    fireEvent.keyDown(input, { key: 'Escape' })
    expect(screen.queryByPlaceholderText('Folder name')).not.toBeInTheDocument()
    expect(createFolderMutate).not.toHaveBeenCalled()
  })
})
