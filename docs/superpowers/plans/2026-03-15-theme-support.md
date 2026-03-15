# Theme Support Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 6 switchable themes (3 dark, 3 light) to the frontend with persistence in localStorage.

**Architecture:** Theme definitions in a single `themes.ts` map. A `useTheme` hook applies CSS variables to `document.documentElement.style` reactively. Theme selector added to the existing Settings dialog. Persisted via the existing Zustand `preferencesStore`.

**Tech Stack:** React 19, TypeScript, Zustand, Vitest, CSS custom properties

**Spec:** `docs/superpowers/specs/2026-03-15-theme-support-design.md`

---

## Chunk 1: Theme Definitions and Hook

### Task 1: Theme Definitions

**Files:**
- Create: `src/main/frontend/src/themes.ts`

- [ ] **Step 1: Create themes.ts with all 6 theme definitions**

```typescript
export interface Theme {
  id: string
  name: string
  type: 'dark' | 'light'
  vars: Record<string, string>
}

export const themes: Record<string, Theme> = {
  midnight: {
    id: 'midnight',
    name: 'Midnight',
    type: 'dark',
    vars: {
      '--bg-primary': '#0f0f1a',
      '--bg-secondary': '#1a1a2e',
      '--bg-tertiary': '#16213e',
      '--bg-active': '#0f3460',
      '--text-primary': '#eee',
      '--text-secondary': '#aaa',
      '--text-muted': '#666',
      '--accent': '#6c63ff',
      '--border': '#333',
      '--hover-bg': 'rgba(108, 99, 255, 0.15)',
      '--toast-error-bg': '#3d1515',
      '--toast-error-text': '#e74c3c',
      '--toast-success-bg': '#153d1a',
      '--toast-success-text': '#2ecc71',
      '--accent-text': '#ffffff',
    },
  },
  'solarized-dark': {
    id: 'solarized-dark',
    name: 'Solarized Dark',
    type: 'dark',
    vars: {
      '--bg-primary': '#002b36',
      '--bg-secondary': '#073642',
      '--bg-tertiary': '#073642',
      '--bg-active': '#003847',
      '--text-primary': '#fdf6e3',
      '--text-secondary': '#93a1a1',
      '--text-muted': '#657b83',
      '--accent': '#b58900',
      '--border': '#586e75',
      '--hover-bg': 'rgba(181, 137, 0, 0.15)',
      '--toast-error-bg': '#3d1515',
      '--toast-error-text': '#dc322f',
      '--toast-success-bg': '#153d1a',
      '--toast-success-text': '#859900',
      '--accent-text': '#fdf6e3',
    },
  },
  'solarized-light': {
    id: 'solarized-light',
    name: 'Solarized Light',
    type: 'light',
    vars: {
      '--bg-primary': '#fdf6e3',
      '--bg-secondary': '#eee8d5',
      '--bg-tertiary': '#eee8d5',
      '--bg-active': '#fdf6e3',
      '--text-primary': '#073642',
      '--text-secondary': '#586e75',
      '--text-muted': '#93a1a1',
      '--accent': '#268bd2',
      '--border': '#d3cbb7',
      '--hover-bg': 'rgba(38, 139, 210, 0.12)',
      '--toast-error-bg': '#fde8e8',
      '--toast-error-text': '#dc322f',
      '--toast-success-bg': '#e8fde8',
      '--toast-success-text': '#859900',
      '--accent-text': '#fdf6e3',
    },
  },
  'sky-blue': {
    id: 'sky-blue',
    name: 'Sky Blue',
    type: 'light',
    vars: {
      '--bg-primary': '#eceff1',
      '--bg-secondary': '#e3e8ec',
      '--bg-tertiary': '#e8ecf0',
      '--bg-active': '#f5f7fa',
      '--text-primary': '#263238',
      '--text-secondary': '#546e7a',
      '--text-muted': '#90a4ae',
      '--accent': '#1976d2',
      '--border': '#cfd8dc',
      '--hover-bg': 'rgba(25, 118, 210, 0.1)',
      '--toast-error-bg': '#fde8e8',
      '--toast-error-text': '#d32f2f',
      '--toast-success-bg': '#e8fde8',
      '--toast-success-text': '#388e3c',
      '--accent-text': '#ffffff',
    },
  },
  light: {
    id: 'light',
    name: 'Light',
    type: 'light',
    vars: {
      '--bg-primary': '#ffffff',
      '--bg-secondary': '#f8f9fa',
      '--bg-tertiary': '#f1f3f5',
      '--bg-active': '#ffffff',
      '--text-primary': '#1a1a1a',
      '--text-secondary': '#495057',
      '--text-muted': '#adb5bd',
      '--accent': '#6366f1',
      '--border': '#dee2e6',
      '--hover-bg': 'rgba(99, 102, 241, 0.08)',
      '--toast-error-bg': '#fde8e8',
      '--toast-error-text': '#e53e3e',
      '--toast-success-bg': '#e8fde8',
      '--toast-success-text': '#38a169',
      '--accent-text': '#ffffff',
    },
  },
  nord: {
    id: 'nord',
    name: 'Nord',
    type: 'dark',
    vars: {
      '--bg-primary': '#2e3440',
      '--bg-secondary': '#3b4252',
      '--bg-tertiary': '#3b4252',
      '--bg-active': '#434c5e',
      '--text-primary': '#eceff4',
      '--text-secondary': '#d8dee9',
      '--text-muted': '#7b88a1',
      '--accent': '#88c0d0',
      '--border': '#4c566a',
      '--hover-bg': 'rgba(136, 192, 208, 0.15)',
      '--toast-error-bg': '#3d1515',
      '--toast-error-text': '#bf616a',
      '--toast-success-bg': '#153d1a',
      '--toast-success-text': '#a3be8c',
      '--accent-text': '#2e3440',
    },
  },
}

export const themeList = Object.values(themes)
```

- [ ] **Step 2: Commit**

```bash
git add src/main/frontend/src/themes.ts
git commit -m "feat(themes): add 6 theme definitions"
```

### Task 2: Preferences Store — Add Theme Field

**Files:**
- Modify: `src/main/frontend/src/stores/preferencesStore.ts`

- [ ] **Step 1: Add theme field and setter to preferencesStore**

Add `theme: string` and `setTheme` to the interface and implementation:

```typescript
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/frontend/src/stores/preferencesStore.ts
git commit -m "feat(themes): add theme field to preferences store"
```

### Task 3: useTheme Hook — Test

**Files:**
- Create: `src/main/frontend/src/hooks/useTheme.test.ts`

- [ ] **Step 1: Write useTheme tests**

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTheme } from './useTheme'
import { usePreferences } from '../stores/preferencesStore'

describe('useTheme', () => {
  beforeEach(() => {
    // Reset to midnight before each test
    usePreferences.setState({ theme: 'midnight' })
    // Clear any inline styles from previous tests
    document.documentElement.style.cssText = ''
  })

  it('should apply midnight theme variables by default', () => {
    renderHook(() => useTheme())

    expect(document.documentElement.style.getPropertyValue('--bg-primary')).toBe('#0f0f1a')
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#6c63ff')
  })

  it('should apply theme variables when theme changes', () => {
    renderHook(() => useTheme())

    act(() => {
      usePreferences.setState({ theme: 'nord' })
    })

    expect(document.documentElement.style.getPropertyValue('--bg-primary')).toBe('#2e3440')
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#88c0d0')
  })

  it('should fall back to midnight for unknown theme ID', () => {
    usePreferences.setState({ theme: 'nonexistent' })
    renderHook(() => useTheme())

    expect(document.documentElement.style.getPropertyValue('--bg-primary')).toBe('#0f0f1a')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd src/main/frontend && npm test -- --run`
Expected: Compilation failure — `useTheme` does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/hooks/useTheme.test.ts
git commit -m "test(themes): add useTheme hook tests (red)"
```

### Task 4: useTheme Hook — Implementation

**Files:**
- Create: `src/main/frontend/src/hooks/useTheme.ts`

- [ ] **Step 1: Implement useTheme hook**

```typescript
import { useEffect } from 'react'
import { usePreferences } from '../stores/preferencesStore'
import { themes } from '../themes'

export function useTheme() {
  const themeId = usePreferences((s) => s.theme)

  useEffect(() => {
    const theme = themes[themeId] ?? themes.midnight
    for (const [key, value] of Object.entries(theme.vars)) {
      document.documentElement.style.setProperty(key, value)
    }
  }, [themeId])
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd src/main/frontend && npm test -- --run`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/hooks/useTheme.ts
git commit -m "feat(themes): implement useTheme hook"
```

## Chunk 2: CSS Changes and UI Integration

### Task 5: App.css — Replace Hardcoded Colors

**Files:**
- Modify: `src/main/frontend/src/App.css`

- [ ] **Step 1: Add new variables to `:root` block**

Add after the existing `--divider-width` line (line 13):

```css
  --hover-bg: rgba(108, 99, 255, 0.15);
  --toast-error-bg: #3d1515;
  --toast-error-text: #e74c3c;
  --toast-success-bg: #153d1a;
  --toast-success-text: #2ecc71;
  --accent-text: #ffffff;
```

- [ ] **Step 2: Replace hardcoded hover backgrounds**

Replace all `rgba(108, 99, 255, 0.15)` with `var(--hover-bg)` in these selectors:
- `.smart-view:hover, .smart-view.active` (line 71)
- `.folder-row:hover, .feed-row:hover, .folder-row.active, .feed-row.active` (line 95-98)
- `.article-item.selected` (line 175-178)
- `.board-picker-item:hover` (line 353-355)

Replace `rgba(108, 99, 255, 0.08)` with `var(--hover-bg)` in:
- `.article-item:hover` (line 173)

- [ ] **Step 3: Replace hardcoded toast colors**

Replace `.toast-error` (lines 404-408):
```css
.toast-error {
  background: var(--toast-error-bg);
  border: 1px solid var(--toast-error-text);
  color: var(--toast-error-text);
}
```

Replace `.toast-success` (lines 410-414):
```css
.toast-success {
  background: var(--toast-success-bg);
  border: 1px solid var(--toast-success-text);
  color: var(--toast-success-text);
}
```

- [ ] **Step 4: Replace hardcoded border colors**

Replace `.article-item` border-bottom (line 169):
```css
  border-bottom: 1px solid var(--border);
```

Replace `.shortcut-row` border-bottom (line 368):
```css
  border-bottom: 1px solid var(--border);
```

- [ ] **Step 5: Fix btn-primary text color**

Replace `.btn-primary` `color: white` (line 322):
```css
  color: var(--accent-text);
```

- [ ] **Step 6: Fix dialog-error color**

Replace `.dialog-error` (line 316):
```css
.dialog-error { color: var(--toast-error-text); font-size: 13px; margin-top: 8px; }
```

- [ ] **Step 7: Commit**

```bash
git add src/main/frontend/src/App.css
git commit -m "feat(themes): replace hardcoded colors with CSS variables"
```

### Task 6: AppShell — Wire Up useTheme

**Files:**
- Modify: `src/main/frontend/src/components/AppShell.tsx`

- [ ] **Step 1: Add useTheme import and call**

Add import at the top of `AppShell.tsx`:

```typescript
import { useTheme } from '../hooks/useTheme'
```

Add as the first line inside the `AppShell` component function (before `const panelWidths`):

```typescript
  useTheme()
```

- [ ] **Step 2: Commit**

```bash
git add src/main/frontend/src/components/AppShell.tsx
git commit -m "feat(themes): wire useTheme into AppShell"
```

### Task 7: SettingsDialog — Add Theme Picker

**Files:**
- Modify: `src/main/frontend/src/components/SettingsDialog.tsx`

- [ ] **Step 1: Add imports**

Add at the top of `SettingsDialog.tsx`:

```typescript
import { themeList } from '../themes'
```

- [ ] **Step 2: Fix hardcoded label colors**

Replace both `color: '#aaa'` occurrences (lines 31 and 41) with:

```typescript
color: 'var(--text-muted)'
```

- [ ] **Step 3: Add theme picker section**

Add a new section between the `<h2>Settings</h2>` line and the existing "Reading" section:

```tsx
        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Appearance</h3>
          <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
            Theme
            <select
              className="dialog-input"
              value={prefs.theme}
              onChange={(e) => prefs.setTheme(e.target.value)}
              style={{ marginTop: 4 }}
            >
              {themeList.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.type})
                </option>
              ))}
            </select>
          </label>
        </div>
```

- [ ] **Step 4: Run frontend tests to verify nothing is broken**

Run: `cd src/main/frontend && npm test -- --run`
Expected: All tests PASS.

- [ ] **Step 5: Run frontend build to verify compilation**

Run: `cd src/main/frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 6: Commit**

```bash
git add src/main/frontend/src/components/SettingsDialog.tsx
git commit -m "feat(themes): add theme picker to Settings dialog"
```

### Task 8: Full Build Verification

- [ ] **Step 1: Run full Gradle build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke test (optional)**

Run: `./gradlew bootTestRun`

1. Open app in browser
2. Open Settings dialog
3. Switch to each theme — verify colors change instantly
4. Verify light themes: text readable, toasts visible, buttons have contrast
5. Close and reopen the app — verify theme persists
6. Switch back to Midnight — verify original look is restored

- [ ] **Step 3: Final commit if any fixups were needed**

```bash
git add -A
git commit -m "fix(themes): address issues found during smoke testing"
```
