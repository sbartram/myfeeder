---
phase: 01-dependency-upgrade
plan: 03
subsystem: infra
tags: [spring-boot, spring-ai, spring-cloud, reactor-netty, vite, react, git-merge, axion-release, integration-gate]

requires:
  - phase: 01-dependency-upgrade (01-01)
    provides: "Backend on Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3, Reactor Netty transport, spring.http.clients.* timeouts"
  - phase: 01-dependency-upgrade (01-02)
    provides: "Frontend deps refreshed within current majors, approved lockfile"
provides:
  - "Proof that the combined 01-01 + 01-02 tree builds clean, passes all 162 backend tests (31 classes) and runs end to end locally"
  - "STACK.md updated to the resolved backend and frontend versions"
  - "Local, unpushed --no-ff merge of sbartram/main into main (29da9aa) in /Volumes/data2/scottb/dev/bartram/myfeeder, with axion computing 0.1.24-SNAPSHOT"
affects: [01-04 (approved push of main, release tag v0.1.24, image and deploy)]

plan_head_before: 135aef6e04564d4f9c3d9856f9ee76f9c2f31dae
actuals:
  tokens: 700
  tasks: 3
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Integration gate: verify the exact SHA (verified-sha.txt), then guard that code paths are unchanged before merging"
    - "Before restoring a user's uncommitted file, re-check that it is byte-identical to the content the merge brings in"

key-files:
  created: []
  modified:
    - .planning/codebase/STACK.md

key-decisions:
  - "01-03: User chose `restore` for the main worktree's uncommitted .serena/project.yml. The identity check ran again right before the restore and exited 0 (byte-identical to sbartram/main), so nothing was lost and no stash entry was created"
  - "01-03: sbartram/main merged into the local main with --no-ff (merge 29da9aa, second parent 388d48d). Nothing pushed; the push waits for the 01-04 approval (D-05)"

patterns-established:
  - "The pre-merge code-path guard compares src, build.gradle.kts, gradle, Dockerfile, helm and deploy.sh against verified-sha.txt"

requirements-completed: [UPG-01, UPG-02, UPG-03]

coverage:
  - id: D1
    description: "The combined tree passes `./gradlew clean build` with Docker: 31 result files, 162 tests, 0 skipped / 0 failures / 0 errors. The lockfile was not changed by the build"
    requirement: UPG-01
    verification:
      - kind: integration
        ref: "DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew clean build (evidence: $HOME/.cache/myfeeder-phase01/combined-build.txt)"
        status: pass
      - kind: other
        ref: "test -z \"$(git status --porcelain -- src/main/frontend/package.json src/main/frontend/package-lock.json)\""
        status: pass
    human_judgment: false
  - id: D2
    description: "The upgraded app boots locally (bootTestRun) and serves the SPA and /api/version. It subscribes to and polls a live Atom feed through the Reactor Netty transport and returns its articles, with no ERROR-level log line"
    requirement: UPG-02
    verification:
      - kind: e2e
        ref: "bootTestRun smoke: $HOME/.cache/myfeeder-phase01/{boottestrun.log,smoke-version.json,smoke-index.html,smoke-subscribe.json,smoke-articles.json}"
        status: pass
    human_judgment: false
  - id: D3
    description: "STACK.md records Boot 4.0.8, Spring AI 2.0.1, Resilience4j 2025.1.3, spring.http.clients.*, reactor-netty-http and the installed frontend versions"
    verification:
      - kind: other
        ref: "Task 3 verify grep chain on .planning/codebase/STACK.md"
        status: pass
    human_judgment: false
  - id: D4
    description: "main (local, /Volumes/data2/scottb/dev/bartram/myfeeder) is a --no-ff merge whose tree equals main^2 and whose code paths equal the verified SHA. It is clean and unpushed, and axion computes 0.1.24-SNAPSHOT"
    requirement: UPG-03
    verification:
      - kind: other
        ref: "git -C MAIN diff --quiet main^2 main && git -C MAIN diff --quiet <verified-sha> main -- src build.gradle.kts gradle Dockerfile helm deploy.sh && status --porcelain empty"
        status: pass
      - kind: other
        ref: "git -C MAIN merge-base --is-ancestor origin/main main && ! merge-base --is-ancestor main origin/main"
        status: pass
      - kind: other
        ref: "(cd MAIN && ./gradlew currentVersion -q) -> Project version: 0.1.24-SNAPSHOT ($HOME/.cache/myfeeder-phase01/main-version.txt)"
        status: pass
    human_judgment: false

duration: 1 min (continuation run, Task 3 only; Task 1 ran in the previous executor)
completed: 2026-09-22
status: complete
---

# Phase 1 Plan 3: Combined Integration Gate and Local Merge Summary

**The combined Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3 and frontend-refresh tree passed a clean Docker build (162/162 tests) and a live bootTestRun smoke (spring.io Atom feed over Reactor Netty). It is now merged `--no-ff` into the local, unpushed `main` (29da9aa), and axion reads 0.1.24-SNAPSHOT.**

## Performance

- **Duration:** about 1 min for this continuation (Task 3). Task 1 (build and smoke) ran in the previous executor run before the Task 2 checkpoint.
- **Started (continuation):** 2026-09-23T00:36:50Z
- **Completed:** 2026-09-23T00:37:54Z
- **Tasks:** 3 of 3 (Task 1 tracer verification-only, Task 2 decision, Task 3 auto)
- **Files modified:** 1 (`.planning/codebase/STACK.md`)

## Accomplishments

- **Combined build (Task 1, verified SHA `135aef6e04564d4f9c3d9856f9ee76f9c2f31dae`):**
  - `./gradlew clean build` with Docker exited 0.
  - 31 `TEST-*.xml` result files: 162 tests, 0 skipped, 0 failures, 0 errors.
  - The skipped count (0) matches 01-01's upgraded run.
  - `package.json` and `package-lock.json` were not changed by the build.
- **Local end-to-end smoke (Task 1, bootTestRun with Testcontainers Postgres and Redis, dummy Anthropic key):**
  - Health reported UP after about 15s. The log shows "Started MyfeederApplication in 5.863 seconds".
  - `/api/version` returned `0.1.24-sbartram-main-SNAPSHOT`.
  - The SPA served `assets/index-DmtnXdgs.js` and `assets/index-BBM6mtSZ.css` from the fresh npmBuild.
  - Feed used: `https://spring.io/blog.atom`. `POST /api/feeds` returned 201 with title "Spring".
  - The forced poll returned 202, with an ETag stored and errorCount 0.
  - `/api/articles?limit=5` returned 4 items.
  - The boot log has 0 ERROR-level and 0 WARN-level log lines.
  - Cleanup: port 8080 is free and no `org.testcontainers=true` containers remain.
- **STACK.md (Task 3, commit `388d48d`):**
  - Backend: Boot 4.0.8 (lines 42, 122), Resilience4j 2025.1.3, Spring AI 2.0.1.
  - RestClient bullet now notes the Reactor Netty transport (reactor-netty-http, D-01). The timeouts bullet uses `spring.http.clients.*`.
  - Frontend, from the committed lockfile:
    - React 19.3.0, React Router 6.30.6, TanStack Query 5.103.2 (5.103 on line 139)
    - Zustand 5.0.15, Vite 8.3.0, TypeScript 5.9.3 (unchanged), DOMPurify 3.4.15
    - Vitest 4.1.11, React Testing Library 16.3.3, ESLint 9.39.5, @vitejs/plugin-react 6.1.1
  - No other version text changed (D-03).
- **Local merge (Task 3, D-05):**
  - `git -C /Volumes/data2/scottb/dev/bartram/myfeeder merge --no-ff sbartram/main` produced merge commit `29da9aa49b349140bcb17ff7a31e8c8815fe9818` (parents `02f74a1` and `388d48d`).
  - The tree equals main^2, and the code paths equal the verified SHA.
  - The worktree is clean. `origin/main` (02f74a1) is a strict ancestor, so nothing was pushed.
  - axion computes `Project version: 0.1.24-SNAPSHOT`.

## Task Commits

1. **Task 1 (tracer): The combined tree builds, passes every test and serves the upgraded app locally.** No commit (verification only). Evidence is in `$HOME/.cache/myfeeder-phase01/`.
2. **Task 2: .serena/project.yml decision (checkpoint:decision, blocking-human).** No commit. The user chose `restore`.
3. **Task 3: STACK.md versions and the --no-ff merge.** `388d48d` (docs) on sbartram/main, plus merge commit `29da9aa` on the local `main` in the main worktree (unpushed).

**Plan metadata:** see the final `docs(01-03)` commit on sbartram/main. It lands after the merge, which is expected; 01-04 handles what gets pushed.

## Files Created/Modified

- `.planning/codebase/STACK.md`: records the resolved backend and frontend versions, the Reactor Netty transport and the `spring.http.clients.*` timeout keys.

## Decisions Made

- **Task 2 identity check and user choice:**
  - The previous executor presented `git -C /Volumes/data2/scottb/dev/bartram/myfeeder diff --quiet sbartram/main -- .serena/project.yml`, which reported identical (exit 0).
  - The user's choice, verbatim: **restore**.
  - Just before restoring, after the STACK.md commit had moved the sbartram/main tip to 388d48d, the check ran again and still exited 0.
  - `git -C MAIN restore .serena/project.yml` then ran, and `status --porcelain` came back empty.
  - The merge brought the byte-identical content back, so nothing was lost and no stash entry was created.
- The merge was local only. Per D-05, pushing main, the tag and the image is 01-04's approved step.

## Deviations from Plan

None. The plan was executed exactly as written.

## Issues Encountered

- The boot log contains 4 JVM `WARNING:` stderr lines (lines 91-94). These are not logger WARN lines: they are Java's restricted-method notice about `io.netty.util.internal.NativeLibraryUtil` calling `System::loadLibrary`, which Netty 4.2 triggers on JDK 25. They are harmless. Silencing them would need `--enable-native-access=ALL-UNNAMED` (for example via `JDK_JAVA_OPTIONS`); that is optional and not done here.
- `npmInstall` during the build reports the 2 known moderate react-router v6 advisories, already accepted in 01-02.

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness

- 01-04 is ready. The local `main` at `29da9aa` holds the verified release candidate: clean, unpushed, with axion at 0.1.24-SNAPSHOT. `./gradlew release` will cut `v0.1.24`.
- The push of main, the tag and the image, and the deploy, all wait for the user's approval in 01-04.
- The metadata commit for this plan lands on sbartram/main after the merge, so main will be one docs commit behind sbartram/main. 01-04 decides whether to re-merge or push as is.

---
*Phase: 01-dependency-upgrade*
*Completed: 2026-09-22*

## Self-Check: PASSED

- FOUND: .planning/codebase/STACK.md
- FOUND: commit 388d48d (sbartram/main), merge 29da9aa (local main)
- FOUND: evidence files verified-sha.txt, boottestrun.log, smoke-*.json/html, main-version.txt under $HOME/.cache/myfeeder-phase01/
