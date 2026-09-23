## Deferred Items

- Pre-existing frontend ESLint errors (6), found during 03-05 and out of scope for it
  status: open
  **What:** `npx eslint .` in `src/main/frontend` reports 6 errors, all in files that predate plan 03-05: `ArticleList.tsx` (52, 102), `BoardArticleList.tsx` (38), `ReadingPane.tsx` (36), `SettingsDialog.tsx` (Raindrop load effect, `react-hooks/set-state-in-effect`) and `Toast.tsx`. The SettingsDialog error is at line 63 of the pre-plan file too. None of the files 03-05 created has a lint error. `npm run build` runs `tsc -b` and `vite build`, not eslint, so the build is not affected.
- TruffleHog pre-commit hook fails now and then with an updater error
  status: open
  **What:** during 03-05 the hook failed twice with `error occurred with trufflehog updater ... fork/exec /opt/homebrew/bin/trufflehog: no such file or directory`, even though the binary exists and the scan found 0 secrets. The same commit passed on retry each time. Adding `--no-update` to the hook entry in `.pre-commit-config.yaml` would likely stop it (not changed here).
