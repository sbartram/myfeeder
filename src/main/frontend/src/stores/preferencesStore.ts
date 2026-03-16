import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface Preferences {
  autoMarkReadDelay: number
  articleSortOrder: 'newest-first' | 'oldest-first'
  theme: string
  hideReadFeeds: boolean
  setAutoMarkReadDelay: (delay: number) => void
  setArticleSortOrder: (order: 'newest-first' | 'oldest-first') => void
  setTheme: (theme: string) => void
  setHideReadFeeds: (hide: boolean) => void
}

export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      autoMarkReadDelay: 1000,
      articleSortOrder: 'newest-first',
      theme: 'midnight',
      hideReadFeeds: false,
      setAutoMarkReadDelay: (delay) => set({ autoMarkReadDelay: delay }),
      setArticleSortOrder: (order) => set({ articleSortOrder: order }),
      setTheme: (theme) => set({ theme }),
      setHideReadFeeds: (hide) => set({ hideReadFeeds: hide }),
    }),
    { name: 'myfeeder-prefs' }
  )
)
