import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface Preferences {
  autoMarkReadDelay: number
  articleSortOrder: 'newest-first' | 'oldest-first'
  setAutoMarkReadDelay: (delay: number) => void
  setArticleSortOrder: (order: 'newest-first' | 'oldest-first') => void
}

export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      autoMarkReadDelay: 1000,
      articleSortOrder: 'newest-first',
      setAutoMarkReadDelay: (delay) => set({ autoMarkReadDelay: delay }),
      setArticleSortOrder: (order) => set({ articleSortOrder: order }),
    }),
    { name: 'myfeeder-prefs' }
  )
)
