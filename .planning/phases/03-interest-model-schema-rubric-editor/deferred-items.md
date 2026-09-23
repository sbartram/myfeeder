## Deferred Items

- Pre-existing frontend ESLint errors (6), found during 03-05 and out of scope for it
  status: open
  **What:** `npx eslint .` in `src/main/frontend` reports 6 errors, all in files that predate plan 03-05: `ArticleList.tsx` (52, 102), `BoardArticleList.tsx` (38), `ReadingPane.tsx` (36), `SettingsDialog.tsx` (Raindrop load effect, `react-hooks/set-state-in-effect`) and `Toast.tsx`. The SettingsDialog error is at line 63 of the pre-plan file too. None of the files 03-05 created has a lint error. `npm run build` runs `tsc -b` and `vite build`, not eslint, so the build is not affected.
- TruffleHog pre-commit hook fails now and then with an updater error
  status: open
  **What:** during 03-05 the hook failed twice with `error occurred with trufflehog updater ... fork/exec /opt/homebrew/bin/trufflehog: no such file or directory`, even though the binary exists and the scan found 0 secrets. The same commit passed on retry each time. Adding `--no-update` to the hook entry in `.pre-commit-config.yaml` would likely stop it (not changed here).
- Interests dialog help text still describes topics as "primarily about" (found during 03-08)
  status: open
  **What:** plan 03-08 shipped v2 topic wording ("substantially about `topic`", see `03-CALIBRATION.md`). `src/main/frontend/src/components/InterestsDialog.tsx:31` still reads "Describe each topic as what an article is primarily about." Update the copy to match (for example "what an article is substantially about") and adjust any test asserting the old string. Outside 03-08's file list, so not changed there.
- Scoring timeout: production `spring.ai.typesafe.timeout: 5s` is too short for a profile + 7-topic judge call (found during 03-08)
  status: open
  **What:** the calibration spike averaged about 2.6s per call, and the first call took more than 5s. It needed a `SPRING_AI_TYPESAFE_TIMEOUT=60s` override. Phase 4's background scorer needs a longer timeout (15–30s) or its own setting; see `03-CALIBRATION.md` Notes 1.
