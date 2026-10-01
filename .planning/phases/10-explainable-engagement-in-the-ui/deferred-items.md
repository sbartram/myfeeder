# Phase 10 Deferred Items

## Deferred Items

- ESLint `react-hooks/set-state-in-effect` error in `WhyBreakdown`'s footer-collapse effect
  status: open
  **What:** `src/main/frontend/src/components/WhyBreakdown.tsx:19` calls `setMoreOpen(false)` inside `useEffect(() => …, [articleId])`, which `npx eslint` reports as an error.
  **Found during:** plan 10-03 Task 3 (linting the changed files). Pre-existing on the phase base `618a640`; plan 10-03 did not touch this effect, so it is out of scope.
  **Possible fix:** reset the footer state by keying the component on `articleId` at its call site, or store the article id the footer was opened for and derive `moreOpen` from it.
