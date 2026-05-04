import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type FontSize = 'small' | 'medium' | 'large' | 'xlarge'

export const FONT_SIZE_STEPS: FontSize[] = ['small', 'medium', 'large', 'xlarge']

export const ARTICLE_LIST_FONT_PX: Record<FontSize, number> = {
  small: 11,
  medium: 13,
  large: 15,
  xlarge: 17,
}

export const READING_FONT_PX: Record<FontSize, number> = {
  small: 13,
  medium: 15,
  large: 17,
  xlarge: 20,
}

interface Preferences {
  autoMarkReadDelay: number
  articleSortOrder: 'newest-first' | 'oldest-first'
  theme: string
  hideReadFeeds: boolean
  hideReadArticles: boolean
  articleListFontSize: FontSize
  readingFontSize: FontSize
  setAutoMarkReadDelay: (delay: number) => void
  setArticleSortOrder: (order: 'newest-first' | 'oldest-first') => void
  setTheme: (theme: string) => void
  setHideReadFeeds: (hide: boolean) => void
  setHideReadArticles: (hide: boolean) => void
  setArticleListFontSize: (size: FontSize) => void
  setReadingFontSize: (size: FontSize) => void
}

const defaults = {
  autoMarkReadDelay: 1000,
  articleSortOrder: 'newest-first' as const,
  theme: 'midnight',
  hideReadFeeds: false,
  hideReadArticles: true,
  articleListFontSize: 'medium' as FontSize,
  readingFontSize: 'medium' as FontSize,
}

export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      ...defaults,
      setAutoMarkReadDelay: (delay) => set({ autoMarkReadDelay: delay }),
      setArticleSortOrder: (order) => set({ articleSortOrder: order }),
      setTheme: (theme) => set({ theme }),
      setHideReadFeeds: (hide) => set({ hideReadFeeds: hide }),
      setHideReadArticles: (hide) => set({ hideReadArticles: hide }),
      setArticleListFontSize: (size) => set({ articleListFontSize: size }),
      setReadingFontSize: (size) => set({ readingFontSize: size }),
    }),
    {
      name: 'myfeeder-prefs',
      merge: (persisted, current) => ({
        ...current,
        ...defaults,
        ...((persisted as Partial<Preferences>) || {}),
      }),
    }
  )
)
