import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SettingsDialog } from './SettingsDialog'
import { integrationsApi } from '../api/integrations'

vi.mock('../api/integrations', () => ({
  integrationsApi: {
    getRaindropStatus: vi.fn(),
    listRaindropCollections: vi.fn(),
    upsertRaindrop: vi.fn(),
    getAll: vi.fn(),
    deleteRaindrop: vi.fn(),
  },
}))

vi.mock('../stores/preferencesStore', () => ({
  usePreferences: () => ({
    theme: 'dark',
    setTheme: vi.fn(),
    hideReadFeeds: false,
    setHideReadFeeds: vi.fn(),
    hideReadArticles: false,
    setHideReadArticles: vi.fn(),
    autoMarkReadDelay: 0,
    setAutoMarkReadDelay: vi.fn(),
    articleSortOrder: 'newest-first',
    setArticleSortOrder: vi.fn(),
    articleListFontSize: 'medium',
    setArticleListFontSize: vi.fn(),
    readingFontSize: 'medium',
    setReadingFontSize: vi.fn(),
  }),
  FONT_SIZE_STEPS: ['small', 'medium', 'large', 'xlarge'],
}))

vi.mock('../themes', () => ({ themeList: [{ id: 'dark', name: 'Dark', type: 'dark' }] }))

describe('SettingsDialog Raindrop section', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(integrationsApi.getAll).mockResolvedValue([])
  })

  it('shows not-configured notice when status.configured is false', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: false })

    render(<SettingsDialog open={true} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByText(/not configured by the administrator/i)).toBeInTheDocument()
    })
    expect(integrationsApi.listRaindropCollections).not.toHaveBeenCalled()
  })

  it('renders collections sorted alphabetically when configured', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: true })
    vi.mocked(integrationsApi.listRaindropCollections).mockResolvedValue([
      { id: 1, title: 'Apple' },
      { id: 2, title: 'Banana' },
    ])
    vi.mocked(integrationsApi.getAll).mockResolvedValue([])

    render(<SettingsDialog open={true} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /raindrop collection/i })).toBeInTheDocument()
    })
    const options = screen.getAllByRole('option')
    const titles = options.map((o) => o.textContent)
    expect(titles).toEqual(expect.arrayContaining(['Apple', 'Banana']))
  })

  it('saves the selected collection without sending an apiToken', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: true })
    vi.mocked(integrationsApi.listRaindropCollections).mockResolvedValue([
      { id: 1, title: 'Apple' },
      { id: 2, title: 'Banana' },
    ])
    vi.mocked(integrationsApi.getAll).mockResolvedValue([])
    vi.mocked(integrationsApi.upsertRaindrop).mockResolvedValue({
      id: 1, type: 'RAINDROP', config: '{"collectionId":2}', enabled: true,
    })

    render(<SettingsDialog open={true} onClose={() => {}} />)

    const select = await screen.findByRole('combobox', { name: /raindrop collection/i })
    await userEvent.selectOptions(select, '2')
    await userEvent.click(screen.getByRole('button', { name: /save raindrop config/i }))

    await waitFor(() => {
      expect(integrationsApi.upsertRaindrop).toHaveBeenCalledWith({ collectionId: 2 })
    })
  })

  it('shows placeholder option when saved collection is no longer in the list', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: true })
    vi.mocked(integrationsApi.listRaindropCollections).mockResolvedValue([
      { id: 1, title: 'Apple' },
    ])
    vi.mocked(integrationsApi.getAll).mockResolvedValue([
      { id: 1, type: 'RAINDROP', config: '{"collectionId":999}', enabled: true },
    ])

    render(<SettingsDialog open={true} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByText(/saved collection no longer exists/i)).toBeInTheDocument()
    })
  })
})
