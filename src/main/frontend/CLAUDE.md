# Frontend (src/main/frontend)

- **Layout**: Three-panel (feed tree / article list / reading pane) with resizable dividers
- **Build**: `npm run build` outputs to `src/main/resources/static/`; Gradle `npmBuild` task wires this into `./gradlew build`
- **Dev workflow**: `./gradlew bootTestRun` (backend) + `cd src/main/frontend && npm run dev` (Vite on :5173, proxies `/api` to :8080). `TestMyfeederApplication` activates the `dev` profile so live settings load (see the `bootTestRun` gotcha); export `MYFEEDER_TYPESAFE_API_KEY` first to enable Jev scoring
- **Tests**: Vitest + React Testing Library; run with `cd src/main/frontend && npm test`
- **Type-check**: use `npx tsc -b` from `src/main/frontend/` — plain `tsc --noEmit` returns success even with errors because the root `tsconfig.json` has `files: []` and uses project references
- **Key conventions**:
  - API client in `src/api/` — thin fetch wrappers per domain (feeds, articles, folders, boards, integrations, opml, interest)
  - TanStack Query hooks in `src/hooks/` — one file per domain (useArticles, useFeeds, useFolders, useBoards, useOpml, useInterest (interest status, tiers, rubric and topic suggestions: useTopicSuggestions, useDismissSuggestion), useFeedback, usePriorityArticles, useEngagement (`useOpenOriginal`, `useForgetEngagement`), engagementReaction (`afterEngagement`, `invalidateAfterLearnedChange`; plain functions, not hooks))
  - Zustand stores in `src/stores/` — `uiStore` (selection, panel state), `preferencesStore` (localStorage-persisted settings)
  - Keyboard shortcuts: vim-style (j/k/n/p/m/s/o/b/v/r), g-chords, managed by `useKeyboardShortcuts` hook
  - Theme system: 6 themes (3 dark, 3 light) defined in `src/themes.ts`, applied via `useTheme` hook, persisted in `preferencesStore`

## Gotchas

- **Zustand persist + new preferences**: Adding a new field to `preferencesStore` with a default value only applies to fresh installs. Existing users with a `myfeeder-prefs` localStorage key get `undefined` for the new field (Zustand merges stored state over defaults). Use a `merge` function or version migration if the default must apply to everyone.
- **Clipboard API requires HTTPS**: The app is served over HTTP (`192.168.44.204`), so `navigator.clipboard` is unavailable. Use `document.execCommand('copy')` fallback for clipboard operations.
- **Frontend `vi.mock` of `preferencesStore` is full-replacement** (e.g. `SettingsDialog.test.tsx`) — adding a new export to `preferencesStore` requires updating every mock that consumes it, otherwise tests fail with `No "X" export is defined on the mock`. Prefer `vi.mock(..., async (importOriginal) => ({ ...await importOriginal(), usePreferences: ... }))` for new tests.
