import { create } from 'zustand'

interface PriorityState {
  /** The refresh button's "Ranking changed" hint (D-08). */
  rankingChanged: boolean
  /** status.eligibleUnscored when the current Priority page 1 loaded; null until captured. */
  baselineUnscored: number | null
  setRankingChanged: (v: boolean) => void
  setBaselineUnscored: (v: number | null) => void
  /** Clears the hint and the baseline (refresh and re-entry). */
  resetHint: () => void
  /** Whether the reading pane's "Why N?" breakdown is open; survives article changes (D-01). */
  whyOpen: boolean
  /** Flips whyOpen; shared by the Why toggle and the i shortcut. */
  toggleWhy: () => void
}

/**
 * Session-only Priority UI state. Plain `create` with no middleware, so nothing reaches
 * localStorage; kept out of the persisted and widely mocked `uiStore` (research Pitfall 8).
 */
export const usePriorityStore = create<PriorityState>((set) => ({
  rankingChanged: false,
  baselineUnscored: null,
  setRankingChanged: (v) => set({ rankingChanged: v }),
  setBaselineUnscored: (v) => set({ baselineUnscored: v }),
  resetHint: () => set({ rankingChanged: false, baselineUnscored: null }),
  whyOpen: false,
  toggleWhy: () => set((s) => ({ whyOpen: !s.whyOpen })),
}))
