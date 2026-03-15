import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface Preferences {
  autoMarkReadDelay: number
  articleSortOrder: 'newest-first' | 'oldest-first'
  theme: string
  setAutoMarkReadDelay: (delay: number) => void
  setArticleSortOrder: (order: 'newest-first' | 'oldest-first') => void
  setTheme: (theme: string) => void
}

export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      autoMarkReadDelay: 1000,
      articleSortOrder: 'newest-first',
      theme: 'midnight',
      setAutoMarkReadDelay: (delay) => set({ autoMarkReadDelay: delay }),
      setArticleSortOrder: (order) => set({ articleSortOrder: order }),
      setTheme: (theme) => set({ theme }),
    }),
    { name: 'myfeeder-prefs' }
  )
)
