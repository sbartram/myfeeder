import { create } from 'zustand'

interface FeedbackState {
  /** Whether the thumbs-down topic picker is open (D-14). */
  narrowOpen: boolean
  setNarrowOpen: (v: boolean) => void
}

/**
 * Session-only thumbs-feedback UI state, shared by Shift+D (the keyboard hook in MainLayout) and
 * the reading pane. Plain `create` with no middleware, so nothing reaches localStorage; kept out
 * of the persisted and widely mocked `uiStore`.
 */
export const useFeedbackStore = create<FeedbackState>((set) => ({
  narrowOpen: false,
  setNarrowOpen: (v) => set({ narrowOpen: v }),
}))
