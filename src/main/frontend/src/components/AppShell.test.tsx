import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { AppShell } from './AppShell'

// Mock Zustand store
vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const state = {
      panelWidths: [200, 280] as [number, number],
      setPanelWidths: vi.fn(),
      keyboardFocus: 'articles' as const,
    }
    return selector(state)
  },
}))

describe('AppShell', () => {
  it('renders three panels', () => {
    render(
      <AppShell
        feedPanel={<div>Feeds</div>}
        articleList={<div>Articles</div>}
        readingPane={<div>Reading</div>}
      />
    )

    expect(screen.getByText('Feeds')).toBeInTheDocument()
    expect(screen.getByText('Articles')).toBeInTheDocument()
    expect(screen.getByText('Reading')).toBeInTheDocument()
  })
})
