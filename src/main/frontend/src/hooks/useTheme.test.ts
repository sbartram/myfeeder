import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook } from '@testing-library/react'

// Track the current theme value for our mock
let mockTheme = 'midnight'

vi.mock('../stores/preferencesStore', () => ({
  usePreferences: (selector: (s: { theme: string }) => unknown) =>
    selector({ theme: mockTheme }),
}))

import { useTheme } from './useTheme'

describe('useTheme', () => {
  beforeEach(() => {
    mockTheme = 'midnight'
    document.documentElement.style.cssText = ''
  })

  it('should apply midnight theme variables by default', () => {
    renderHook(() => useTheme())

    expect(document.documentElement.style.getPropertyValue('--bg-primary')).toBe('#0f0f1a')
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#6c63ff')
  })

  it('should apply nord theme variables', () => {
    mockTheme = 'nord'
    renderHook(() => useTheme())

    expect(document.documentElement.style.getPropertyValue('--bg-primary')).toBe('#2e3440')
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#88c0d0')
  })

  it('should fall back to midnight for unknown theme ID', () => {
    mockTheme = 'nonexistent'
    renderHook(() => useTheme())

    expect(document.documentElement.style.getPropertyValue('--bg-primary')).toBe('#0f0f1a')
  })
})
