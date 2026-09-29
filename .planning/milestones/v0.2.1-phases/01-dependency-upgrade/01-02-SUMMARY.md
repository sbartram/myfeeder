---
phase: 01-dependency-upgrade
plan: 02
subsystem: frontend
tags: [npm, react, vite, vitest, typescript, dompurify, react-router, supply-chain]

requires: []
provides:
  - "Frontend dependencies on the latest minor/patch inside every current major (20 direct deps bumped)"
  - "Approved, sha256-pinned package-lock.json installed with npm ci"
  - "npm audit reduced to 2 moderate react-router v6 advisories (0 high / 0 critical)"
affects: [01-03 (STACK.md records these resolved versions), 01-04 (release bundle embeds this frontend)]

plan_head_before: db2094922b1561eb27f9ec7f56b08bd8689569fb
actuals:
  tokens: 27800
  tasks: 3
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Resolve with --package-lock-only, review at a blocking-human checkpoint, sha256-check, then npm ci"

key-files:
  created: []
  modified:
    - src/main/frontend/package.json
    - src/main/frontend/package-lock.json

key-decisions:
  - "01-02: User approved the full resolved npm set (resume signal \"approved\"), including the <48h releases @tanstack/react-query 5.103.2 and typescript-eslint 8.70.1; no holds"
  - "01-02: The 2 moderate react-router v6 advisories (GHSA-wrjc-x8rr-h8h6, GHSA-337j-9hxr-rhxg) are accepted; their fix needs v7, which is deferred"

patterns-established:
  - "npm refresh gate: resolve lockfile-only, then human approval, then sha256 drift guard, then npm ci"

requirements-completed: [UPG-02]

coverage:
  - id: D1
    description: "Every frontend dependency is on the latest release in its current major; no major crossed (react-router-dom 6.30.6, typescript 5.9.3); dependency name set unchanged"
    requirement: UPG-02
    verification:
      - kind: other
        ref: "npm --prefix src/main/frontend outdated --json + in-major gate node script (in-major gate clean; holds: 0)"
        status: pass
      - kind: other
        ref: "lockfile check: react-router-dom 6.30.6, typescript 5.9.3; dependency-name diff against main baseline is empty"
        status: pass
    human_judgment: false
  - id: D2
    description: "Updated frontend type-checks, tests pass and bundle builds"
    requirement: UPG-02
    verification:
      - kind: other
        ref: "cd src/main/frontend && npx tsc -b (exit 0)"
        status: pass
      - kind: unit
        ref: "npm --prefix src/main/frontend test (13 files / 47 tests passed)"
        status: pass
      - kind: other
        ref: "npm --prefix src/main/frontend run build (vite 8.3.0, assets/index-DmtnXdgs.js)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Supply-chain gate: the user approved the version set before install; the installed lockfile is byte-identical to the approved one; zero high/critical advisories"
    requirement: UPG-02
    verification:
      - kind: other
        ref: "shasum -a 256 -c ~/.cache/myfeeder-phase01/npm-approved.sha256 (OK before and after npm ci)"
        status: pass
      - kind: other
        ref: "npm --prefix src/main/frontend audit --json (high 0, critical 0, moderate 2)"
        status: pass
      - kind: manual_procedural
        ref: "Task 2 blocking-human checkpoint, user resume signal: approved"
        status: pass
    human_judgment: false

duration: 16min
completed: 2026-09-23
status: complete
---

# Phase 01 Plan 02: Frontend Dependency Refresh Summary

**`npm update --save` moved 20 frontend dependencies to their latest in-major releases: React 19.3, vite 8.3, vitest 4.1.11, DOMPurify 3.4.15, TanStack Query 5.103.2, react-router-dom 6.30.6. The user approved the version set, the lockfile was sha256-pinned and installed with `npm ci`, and npm audit dropped from 15 findings to 2 accepted react-router v6 moderates.**

## Performance

- **Duration:** about 16 min, including the wait at the approval checkpoint
- **Started:** 2026-09-23T00:09:53Z
- **Completed:** 2026-09-23T00:26:17Z
- **Tasks:** 3 (Task 1 tracer, Task 2 blocking-human checkpoint, Task 3 install/prove/commit)
- **Files modified:** 2

## Accomplishments

- 20 direct dependencies moved to their latest minor/patch inside their current major. No major was crossed, and the dependency name set is unchanged.
- react-router-dom stays on v6 (6.30.6), and typescript stays at `~5.9.3` (5.9.3).
- DOMPurify 3.4.1 → 3.4.15 and vite 8.0.9 → 8.3.0 clear the published sanitizer and dev-server advisories. npm audit now shows 0 high, 0 critical and 2 moderate.
- `npx tsc -b`, vitest (13 files, 47 tests) and `vite build` all pass on the new lockfile.

## Candidate Table (Task 1)

Full table: `$HOME/.cache/myfeeder-phase01/npm-candidates.md`. It has 20 rows for changed direct dependencies, and 114 transitive lockfile entries changed or were added while 7 were removed.

- **Resolution path:** `npm --prefix src/main/frontend update --save --package-lock-only`. The primary path worked, so the read-only fallback was not used. Task 1 ran no install, tsc, vitest or vite command.
- **Releases under 48 hours old:** `@tanstack/react-query` 5.99.2 → 5.103.2 (38.6 h) and `typescript-eslint` 8.59.0 → 8.70.1 (31.0 h).
- **New install scripts:** none. fsevents 2.3.3 already had an install script in the baseline. `npm ci` listed it as not covered by `allowScripts` and did not run it.
- **Other notable bumps:** react/react-dom 19.2.5 → 19.3.0, @types/react(-dom) → 19.3.0, vite 8.0.9 → 8.3.0, vitest 4.1.5 → 4.1.11, dompurify 3.4.1 → 3.4.15, zustand 5.0.12 → 5.0.15, jsdom 29.0.2 → 29.1.1, @vitejs/plugin-react 6.0.1 → 6.1.1, eslint and @eslint/js 9.39.4 → 9.39.5.

## Checkpoint (Task 2): User Approval

- **Gate:** `blocking-human` (package-legitimacy and D-04 approval)
- **User's resume signal, verbatim:** `approved`
- **Meaning:** install and commit exactly the resolved set. This covers both releases under 48 hours old: `@tanstack/react-query` 5.103.2 and `typescript-eslint` 8.70.1.
- **Holds:** none. No `npm-hold.txt` was written.

## Task 3 Evidence

- **Drift guard:** `shasum -a 256 -c ~/.cache/myfeeder-phase01/npm-approved.sha256` returned OK for both files before `npm ci`, and again after install.
- **Install:** `npm --prefix src/main/frontend ci` added 271 packages. All 20 approved versions were confirmed installed, with 0 mismatches.
- **Type-check:** `npx tsc -b` exits 0.
- **Tests:** vitest reports `Test Files 13 passed (13)` and `Tests 47 passed (47)`.
- **Build:** vite 8.3.0 transformed 107 modules and produced `assets/index-DmtnXdgs.js` (395.67 kB, 124.17 kB gzip) and `assets/index-BBM6mtSZ.css`. The output goes to the gitignored `src/main/resources/static/`.
- **In-major gate:** `in-major gate clean; holds: 0`. Newer versions exist only across a major and were not taken: @eslint/js 10, @testing-library/jest-dom 7, @types/node 26, eslint 10, jsdom 30, react-router-dom 7, typescript 7, vitest 5.
- **Audit:** `{"info":0,"low":0,"moderate":2,"high":0,"critical":0,"total":2}`. The two moderates are accepted (T-01-06):
  - GHSA-wrjc-x8rr-h8h6: React Router open redirect via a backslash in `<Link>` or `useNavigate`
  - GHSA-337j-9hxr-rhxg: React Router constructor injection via `deserializeErrors()` during SSR hydration. The SPA has no SSR.
- **Commit content gate:** the commit contains exactly `src/main/frontend/package.json` and `src/main/frontend/package-lock.json`.

## Task Commits

1. **Task 1 (tracer): resolve the in-major update set.** No commit on purpose; the changes stayed uncommitted until approval and landed in the Task 3 commit.
2. **Task 2: approval checkpoint.** No commit (approved).
3. **Task 3: install, prove, commit.** `0a82b49` (build)

## Files Created/Modified

- `src/main/frontend/package.json`: version ranges refreshed within their current majors. Names are unchanged.
- `src/main/frontend/package-lock.json`: the approved, sha256-pinned set of in-major versions.

## Decisions Made

- The user approved the full resolved set, including the two releases under 48 hours old, with no holds.
- The two moderate react-router v6 advisories are accepted until the deferred v7 migration.

## Deviations from Plan

None. The plan ran as written. Following the research quirk, the `@eslint/js` range was left as npm wrote it and not hand-edited.

## Issues Encountered

- npm 11 printed an `allowScripts` warning for fsevents@2.3.3. That install script was already in the baseline, and npm did not run it. It is macOS-optional, and tests and the build pass without it. No action taken.
- Node 26 prints `ExperimentalWarning: localStorage is not available` during vitest runs. This is runtime noise, not a failure.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- The frontend half of the phase is done. Plan 01-03 can record these lockfile versions in `.planning/codebase/STACK.md`.
- `./gradlew clean bootJar` will embed the refreshed bundle, because the lockfile is an input to `npmInstall`/`npmBuild`.

---
*Phase: 01-dependency-upgrade*
*Completed: 2026-09-23*

## Self-Check: PASSED

- FOUND: src/main/frontend/package.json, src/main/frontend/package-lock.json (committed in 0a82b49)
- FOUND: commit 0a82b49 on sbartram/main
- FOUND: ~/.cache/myfeeder-phase01/npm-candidates.md, npm-approved.sha256, npm-outdated.json, npm-audit.json, npm-commit-files.txt
- The user's unrelated working-tree edits (CLAUDE.md, .claude/CLAUDE.md, .planning/config.json) are intact after the commit (sha256 match)
