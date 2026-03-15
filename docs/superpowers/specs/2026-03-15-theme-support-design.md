# Theme Support Design

## Overview

Add a theme system to myfeeder's frontend so users can switch between 6 built-in themes (3 dark, 3 light). Theme preference is persisted in localStorage via the existing preferences store.

## Decisions

- **Theme storage:** CSS custom properties applied to `document.documentElement.style` at runtime. No separate CSS files per theme.
- **Persistence:** Existing `preferencesStore` (Zustand + localStorage). New `theme` field defaulting to `'midnight'`.
- **UI location:** Theme picker in the Settings dialog as a `<select>` dropdown. Instant application on change (no save button).
- **No backend changes.** Purely frontend.

## Theme Definitions

Six themes, each defining the same set of CSS variables:

| ID | Name | Type | bg-primary | accent | Character |
|----|------|------|-----------|--------|-----------|
| `midnight` | Midnight | Dark | `#0f0f1a` | `#6c63ff` | Current default — deep navy/purple with violet accent |
| `solarized-dark` | Solarized Dark | Dark | `#002b36` | `#b58900` | Classic Ethan Schoonover warm dark palette |
| `solarized-light` | Solarized Light | Light | `#fdf6e3` | `#268bd2` | Warm cream background with blue accent |
| `sky-blue` | Sky Blue | Light | `#eceff1` | `#1976d2` | Material Design blue-grey |
| `light` | Light | Light | `#ffffff` | `#6366f1` | Clean neutral white with indigo accent |
| `nord` | Nord | Dark | `#2e3440` | `#88c0d0` | Arctic cool blue palette |

### CSS Variables Per Theme

Each theme defines all of these variables:

```
--bg-primary        Main background (html/body)
--bg-secondary      Feed panel background
--bg-tertiary       Article list background
--bg-active         Reading pane background
--text-primary      Headings, active text
--text-secondary    Body text, descriptions
--text-muted        Labels, timestamps, inactive text
--accent            Links, highlights, active indicators
--border            Panel dividers, input borders
--hover-bg          Hover/active background for feed rows, article items
--toast-error-bg    Toast error background
--toast-error-text  Toast error text/border
--toast-success-bg  Toast success background
--toast-success-text Toast success text/border
--accent-text       Text color on accent-colored buttons (white or dark)
```

**Maintenance constraint:** All themes must define the complete set of variables. The `useTheme` hook applies variables via `element.style.setProperty`, which means stale values from a prior theme persist if a variable is missing. Any future theme must include all variables.

### Full Theme Values

**Midnight** (current default):
```
--bg-primary: #0f0f1a
--bg-secondary: #1a1a2e
--bg-tertiary: #16213e
--bg-active: #0f3460
--text-primary: #eee
--text-secondary: #aaa
--text-muted: #666
--accent: #6c63ff
--border: #333
--hover-bg: rgba(108, 99, 255, 0.15)
--toast-error-bg: #3d1515
--toast-error-text: #e74c3c
--toast-success-bg: #153d1a
--toast-success-text: #2ecc71
--accent-text: #ffffff
```

**Solarized Dark:**
```
--bg-primary: #002b36
--bg-secondary: #073642
--bg-tertiary: #073642
--bg-active: #003847
--text-primary: #fdf6e3
--text-secondary: #93a1a1
--text-muted: #657b83
--accent: #b58900
--border: #586e75
--hover-bg: rgba(181, 137, 0, 0.15)
--toast-error-bg: #3d1515
--toast-error-text: #dc322f
--toast-success-bg: #153d1a
--toast-success-text: #859900
--accent-text: #fdf6e3
```

**Solarized Light:**
```
--bg-primary: #fdf6e3
--bg-secondary: #eee8d5
--bg-tertiary: #eee8d5
--bg-active: #fdf6e3
--text-primary: #073642
--text-secondary: #586e75
--text-muted: #93a1a1
--accent: #268bd2
--border: #d3cbb7
--hover-bg: rgba(38, 139, 210, 0.12)
--toast-error-bg: #fde8e8
--toast-error-text: #dc322f
--toast-success-bg: #e8fde8
--toast-success-text: #859900
--accent-text: #fdf6e3
```

**Sky Blue:**
```
--bg-primary: #eceff1
--bg-secondary: #e3e8ec
--bg-tertiary: #e8ecf0
--bg-active: #f5f7fa
--text-primary: #263238
--text-secondary: #546e7a
--text-muted: #90a4ae
--accent: #1976d2
--border: #cfd8dc
--hover-bg: rgba(25, 118, 210, 0.1)
--toast-error-bg: #fde8e8
--toast-error-text: #d32f2f
--toast-success-bg: #e8fde8
--toast-success-text: #388e3c
--accent-text: #ffffff
```

**Light:**
```
--bg-primary: #ffffff
--bg-secondary: #f8f9fa
--bg-tertiary: #f1f3f5
--bg-active: #ffffff
--text-primary: #1a1a1a
--text-secondary: #495057
--text-muted: #adb5bd
--accent: #6366f1
--border: #dee2e6
--hover-bg: rgba(99, 102, 241, 0.08)
--toast-error-bg: #fde8e8
--toast-error-text: #e53e3e
--toast-success-bg: #e8fde8
--toast-success-text: #38a169
--accent-text: #ffffff
```

**Nord:**
```
--bg-primary: #2e3440
--bg-secondary: #3b4252
--bg-tertiary: #3b4252
--bg-active: #434c5e
--text-primary: #eceff4
--text-secondary: #d8dee9
--text-muted: #7b88a1
--accent: #88c0d0
--border: #4c566a
--hover-bg: rgba(136, 192, 208, 0.15)
--toast-error-bg: #3d1515
--toast-error-text: #bf616a
--toast-success-bg: #153d1a
--toast-success-text: #a3be8c
--accent-text: #2e3440
```

## Components

### `src/main/frontend/src/themes.ts`

Theme type definition and theme map:

```typescript
export interface Theme {
  id: string
  name: string
  type: 'dark' | 'light'
  vars: Record<string, string>
}

export const themes: Record<string, Theme> = { ... }
```

Exports `themes` map (keyed by theme ID) and `Theme` type.

### `src/main/frontend/src/hooks/useTheme.ts`

Hook that reads the theme preference and applies CSS variables to `document.documentElement.style`:

```typescript
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

Called once in `AppShell` component.

### `src/main/frontend/src/stores/preferencesStore.ts`

Add:
- `theme: string` field (default: `'midnight'`)
- `setTheme: (theme: string) => void` setter

### `src/main/frontend/src/components/SettingsDialog.tsx`

Add a "Theme" section to the existing Settings dialog with a `<select>` dropdown listing all 6 themes by name (with dark/light label). Selecting a theme calls `setTheme()` which triggers the `useTheme` hook reactively.

## CSS Changes

### `App.css`

Replace hardcoded colors with new CSS variables:

1. **`:root` block** — Add the new variables (`--hover-bg`, `--toast-error-bg`, `--toast-error-text`, `--toast-success-bg`, `--toast-success-text`, `--accent-text`) with Midnight defaults. This ensures the app renders correctly before JS loads.
2. **Hover backgrounds** — all `rgba(108, 99, 255, 0.15)` and `rgba(108, 99, 255, 0.08)` become `var(--hover-bg)`
3. **Toast styles** — `.toast-error` background/border/color use `var(--toast-error-bg)` and `var(--toast-error-text)`. Same for `.toast-success`.
4. **Article item border-bottom** — `rgba(51, 51, 51, 0.5)` becomes `1px solid var(--border)`
5. **Shortcut row border** — same treatment
6. **`.btn-primary` color** — `color: white` becomes `color: var(--accent-text)` for proper contrast on all themes
7. **`.dialog-error` color** — `#e74c3c` becomes `var(--toast-error-text)` for theme consistency

### `SettingsDialog.tsx`

Fix hardcoded `color: '#aaa'` on label elements — change to `color: var(--text-muted)` for light theme compatibility.

### Theme flash on page load

When a non-Midnight theme is selected, there will be a brief flash of Midnight colors before `useTheme` fires. This is an accepted trade-off for simplicity. The `:root` block provides Midnight defaults, and `useEffect` applies the saved theme within milliseconds of React hydration. For a Vite SPA this is imperceptible in practice. A `<script>` block in `index.html` could eliminate this entirely but adds complexity disproportionate to the benefit.

## Testing

### `useTheme.test.ts`

- Verify that switching theme applies CSS variables to `document.documentElement`
- Verify that unknown theme ID falls back to midnight

### Manual verification

- Switch each theme and confirm no text becomes invisible (contrast)
- Verify toast visibility on all themes
- Verify dialog overlay looks correct on light themes
- Verify scrollbar appearance is acceptable (browser-dependent)

## Files to Create

| File | Type |
|------|------|
| `src/main/frontend/src/themes.ts` | Theme definitions |
| `src/main/frontend/src/hooks/useTheme.ts` | Theme application hook |
| `src/main/frontend/src/hooks/useTheme.test.ts` | Hook test |

## Files to Modify

| File | Change |
|------|--------|
| `src/main/frontend/src/stores/preferencesStore.ts` | Add `theme` field + setter |
| `src/main/frontend/src/App.css` | Replace hardcoded colors with theme variables |
| `src/main/frontend/src/components/SettingsDialog.tsx` | Add theme picker dropdown |
| `src/main/frontend/src/components/AppShell.tsx` | Call `useTheme()` hook |

## No Backend Changes

This is entirely a frontend feature. No API endpoints, no database changes.
