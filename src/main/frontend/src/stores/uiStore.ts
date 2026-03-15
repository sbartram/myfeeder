import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type PanelFocus = 'feeds' | 'articles' | 'reading'

interface UIState {
  selectedFeedId: number | null
  selectedFolderId: number | null
  selectedArticleId: number | null
  panelWidths: [number, number]
  expandedFolders: Set<number>
  keyboardFocus: PanelFocus
  searchQuery: string

  setSelectedFeed: (feedId: number | null) => void
  setSelectedFolder: (folderId: number | null) => void
  setSelectedArticle: (articleId: number | null) => void
  setPanelWidths: (widths: [number, number]) => void
  toggleFolder: (folderId: number) => void
  setKeyboardFocus: (panel: PanelFocus) => void
  setSearchQuery: (query: string) => void
  cycleFocus: () => void
}

const FOCUS_ORDER: PanelFocus[] = ['feeds', 'articles', 'reading']

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      selectedFeedId: null,
      selectedFolderId: null,
      selectedArticleId: null,
      panelWidths: [200, 280] as [number, number],
      expandedFolders: new Set<number>(),
      keyboardFocus: 'articles' as PanelFocus,
      searchQuery: '',

      setSelectedFeed: (feedId) =>
        set({ selectedFeedId: feedId, selectedFolderId: null, selectedArticleId: null }),
      setSelectedFolder: (folderId) =>
        set({ selectedFolderId: folderId, selectedFeedId: null, selectedArticleId: null }),
      setSelectedArticle: (articleId) => set({ selectedArticleId: articleId }),
      setPanelWidths: (widths) => set({ panelWidths: widths }),
      toggleFolder: (folderId) =>
        set((state) => {
          const next = new Set(state.expandedFolders)
          if (next.has(folderId)) next.delete(folderId)
          else next.add(folderId)
          return { expandedFolders: next }
        }),
      setKeyboardFocus: (panel) => set({ keyboardFocus: panel }),
      setSearchQuery: (query) => set({ searchQuery: query }),
      cycleFocus: () =>
        set((state) => {
          const idx = FOCUS_ORDER.indexOf(state.keyboardFocus)
          return { keyboardFocus: FOCUS_ORDER[(idx + 1) % FOCUS_ORDER.length] }
        }),
    }),
    {
      name: 'myfeeder-ui',
      partialize: (state) => ({
        panelWidths: state.panelWidths,
        expandedFolders: Array.from(state.expandedFolders),
      }),
      merge: (persisted: unknown, current) => ({
        ...current,
        ...((persisted as Record<string, unknown>) || {}),
        expandedFolders: new Set(
          ((persisted as Record<string, unknown>)?.expandedFolders as number[]) || []
        ),
      }),
    }
  )
)
