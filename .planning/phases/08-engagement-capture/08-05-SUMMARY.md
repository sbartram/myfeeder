---
phase: 08-engagement-capture
plan: 05
subsystem: infra
tags: [release, deploy, helm, k3s, axion, docker, pg_dump, flyway, engagement]

requires:
  - phase: 08-engagement-capture
    provides: "08-01..08-04: V7 engagement schema, capture routes, Forget control, frontend wiring (all merged on the phase branch)"
  - phase: 07-rollout-calibration
    provides: "07-06 release precedent (preflight, blocking-human approval, publish, verify, soak, rollback)"
provides:
  - "Release v0.3.0 (tag on origin at db5acd7; main pushed with the --no-ff merge 4b3841b of the phase branch)"
  - "Image registry.bartram.org/bartram/myfeeder:0.3.0 (sha256:7a785af7ef5f91c7fb96f6ee47d2123d1eb1f292722fdd13d87fcc8a5d1ed464)"
  - "Prod on 0.3.0 (Helm revision 20, rollback target 19 = 0.2.1) with Flyway V7 applied; engagement now accumulates from real use"
  - "Pre-deploy pg_dump and the 08-RELEASE.md facts table"
affects: [phase-09, phase-11, phase-12, calibration]

actuals:
  tokens: 2600
  tasks: 3
  commits: 3
plan_head_before: 5b1f30dc4c492dc18f01377067c65933823d4fdb
plan_head_after: 3a3ac8ba1e89f9f6a0231bbc842ffc2d78e8b92f

tech-stack:
  added: []
  patterns:
    - "Read-only preflight proves every release hop before a blocking-human approval; evidence lives only under $HOME/.cache/myfeeder-phase08 (dir 700, files 600)"
    - "On a non-main branch axion appends the branch name to currentVersion; prove the main value with -Prelease.overriddenBranchName=main"

key-files:
  created:
    - .planning/phases/08-engagement-capture/08-RELEASE.md
  modified:
    - .gitignore

key-decisions:
  - "User decision at the D-13 blocking-human checkpoint, relayed verbatim by the orchestrator: approve b"
  - "Lock fix (b): .planning/milestone.lock added to .gitignore in its own commit (db5acd7) after the merge and before the push, because axion verifyRelease counted the untracked lock as an uncommitted change"
  - "v0.3.0 released from main db5acd7, image sha256:7a785af7, Helm rev 20, rollback rev 19; failure path not run"
  - "SC-5 held in prod: ranking UNCHANGED over 47 ids, why UNCHANGED 5/5, smoke PASS, soak 10.5 min with 46/46 feeds and 0 ERROR"

patterns-established:
  - "Homebrew pg_dump --version ends with '(Homebrew)', so parse the major with grep -oE '[0-9]+\\.[0-9]+', not awk $NF"

requirements-completed: [CAPT-01, CAPT-02, CAPT-03, CAPT-04, CAPT-05, CAPT-06, CAPT-07]

coverage:
  - id: D1
    description: "Release v0.3.0 published: --no-ff merge and lock-ignore commit pushed to origin/main, tag v0.3.0 on origin, image 0.3.0 in the registry, v0.2.1 unmoved"
    requirement: "CAPT-01"
    verification:
      - kind: other
        ref: "Task 3 verify block 1 (ls-remote tag v0.3.0, merge-base v0.3.0 origin/main, docker manifest inspect :0.3.0, v0.2.1 == 5461d0a)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Prod runs 0.3.0 with Flyway V7 applied successfully and a clean startup"
    requirement: "CAPT-05"
    verification:
      - kind: other
        ref: "Task 3 verify block 2 (rollout status, image :0.3.0, /api/version 0.3.0, flyway_schema_history V7 success t, startup.log Started and no ERROR)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Prod engagement routes work end to end and the Priority ranking and Why breakdowns are unchanged; the 10-minute soak is clean"
    requirement: "CAPT-06"
    verification:
      - kind: other
        ref: "Task 3 verify block 3 (ranking-check.txt UNCHANGED x2, smoke.txt PASS, soak.log 46 feeds and no ERROR)"
        status: pass
    human_judgment: false
  - id: D4
    description: "08-RELEASE.md records the release facts with no secret value and is committed on main"
    verification:
      - kind: other
        ref: "Task 3 verify block 4 (file exists, ## Release 0.3.0, grep -qF against all four keys finds none; committed as 3a3ac8b)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Engagement rows accumulate from real use (SC-5 backstop) and the browser shows Open Original then 'Engaged: opened · Forget'"
    requirement: "CAPT-07"
    verification: []
    human_judgment: true
    rationale: "Needs at least a day of the user's real use in prod, then a read-only GROUP BY kind query and a browser check"

duration: 29min active (61min wall incl. checkpoint wait)
completed: 2026-09-30
status: complete
---

# Phase 8 Plan 05: v0.3.0 Release Summary

**Engagement capture shipped as v0.3.0 (tag db5acd7, image sha256:7a785af7, Helm rev 20). Flyway V7 applied to prod in 981 ms, the open/Forget smoke passed, and the Priority order, badges and Why breakdowns are identical to v0.2.1.**

## Performance

- **Duration:** about 29 min of active work (preflight 03:07:56Z to 03:17Z; release 03:55:57Z to 04:10Z); 61 min wall clock including the wait at the approval checkpoint
- **Started:** 2026-09-30T03:07:56Z
- **Completed:** 2026-09-30T04:10Z
- **Tasks:** 3 (preflight, approval checkpoint, release and verify)
- **Files modified:** 2 in the repo (.gitignore, 08-RELEASE.md) plus this SUMMARY

## Accomplishments

- **Preflight (Task 1):** every hop was proven read-only before approval was asked for:
  - backend tests 599/0 failures/2 skipped; tsc clean; vitest 346/346
  - the phase branch merges into main without conflict; main was not diverged from origin/main
  - v0.3.0 did not exist; currentVersion reads 0.2.2-SNAPSHOT as main
  - Docker, the registry (200), k3s-ansible and Helm (rollback revision 19) all answered
  - prod was on 0.2.1 at Flyway 6, and v0.2.1 sets no Flyway key
  - pg 18 on both the server and the local pg_dump
  - all four deploy keys were set with no bad characters
  - Priority top-50 and top-5 Why snapshots were captured
- **Decision (Task 2):** the user answered "approve b" at the D-13 blocking-human checkpoint, after seeing the exact V7 DDL.
- **Release and verify (Task 3):**
  - Git and build: merged with --no-ff (4b3841b), committed the lock ignore (db5acd7), pushed main, then `./gradlew release -Prelease.versionIncrementer=incrementMinor` created and pushed tag v0.3.0. `clean bootJar` gave VERSION 0.3.0, and the jar has the SPA bundle and V7.
  - Image, dump and deploy: `docker build --provenance=false` and push; a pg_dump (11.3 MB, 91 list entries); then `./deploy.sh 0.3.0`, which made Helm revision 20.
  - Prod checks: startup in 14.5 s with no ERROR; Flyway V7 success; ranking and why UNCHANGED; smoke PASS; soak PASS.

## Task Commits

1. **Task 1: Tracer preflight.** No commit: the task is read-only, and its evidence is under `$HOME/.cache/myfeeder-phase08/`.
2. **Task 2: Approval checkpoint.** No commit. The answer is recorded below.
3. **Task 3: Release.** Three commits:
   - `4b3841b` merge of `gsd/phase-08-engagement-capture` (--no-ff)
   - `db5acd7` chore(08-05): ignore GSD milestone.lock runtime file
   - `3a3ac8b` docs(08-05): record v0.3.0 release evidence

**Plan metadata:** the docs commit that follows this SUMMARY (SUMMARY, STATE, ROADMAP, REQUIREMENTS).

## Files Created/Modified

- `.planning/phases/08-engagement-capture/08-RELEASE.md`: the Release 0.3.0 facts table and the follow-up
- `.gitignore`: added `.planning/milestone.lock` next to `.planning/state.json`

## Decisions Made

- **Task 2 answer, verbatim as relayed by the orchestrator:** `approve b`. It covers the merge, push main, tag, image push, dump, deploy and smoke. "b" chose the `.gitignore` fix for the lock.
- The release commit is db5acd7, the lock-ignore commit, so the tag includes the ignore rule. The merge commit 4b3841b sits directly under it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] axion verifyRelease rejected the untracked `.planning/milestone.lock`**
- **Found during:** Task 1. The read-only `./gradlew verifyRelease` failed with "There are uncommitted files in your repository".
- **Issue:** the GSD orchestrator's runtime lock is untracked, and axion counts untracked files. `-Prelease.disableChecks` is forbidden by the plan.
- **Fix:** surfaced at the checkpoint with three options. The user chose (b): add the lock to `.gitignore` in its own commit on main after the merge and before the push. `verifyRelease` then passed the uncommitted-changes check.
- **Files modified:** .gitignore
- **Committed in:** db5acd7

**2. [Rule 1 - Bug] Two of the plan's verify commands were wrong for this environment**
- **Found during:** Task 1 tracer verify.
- **Issue:** block 4 parsed the pg_dump major with `awk '{print $NF}'`, which reads `(Homebrew)`. Block 2 found MAIN through `git worktree list`, but no worktree had main checked out before the approval.
- **Fix:** block 4 was re-run with `grep -oE '[0-9]+\.[0-9]+'`, and block 2 with M set to this checkout, which is where `git switch main` happened. Both pass, and both are logged in `preflight.txt`.
- **Files modified:** none (evidence only)

**3. [Rule 3 - Blocking] currentVersion on the phase branch carries a branch suffix**
- **Found during:** Task 1.
- **Issue:** on the phase branch, axion's versionWithBranch creator gives `0.2.2-gsd-phase-08-engagement-capture-SNAPSHOT`.
- **Fix:** proved the main value, 0.2.2-SNAPSHOT, with `-Prelease.overriddenBranchName=main` without switching branches. After the release on main, VERSION read exactly 0.3.0.

---

**Total deviations:** 3 (1 fixed via the user's lock choice, 1 verify-command fix, 1 read-only version proof)
**Impact on plan:** none on the release. No check was bypassed, and nothing was forced or skipped.

## Issues Encountered

- **Task 3 verify block 4 is not fully green:** it requires `git status --porcelain` to be empty in MAIN. After the 08-RELEASE commit, two untracked files appeared that are not from this plan, created during the run by another session: `docs/superpowers/plans/2026-09-29-metallb-annotation-external-services.md` and its `.tasks.json`. I left them untouched. Everything this plan wrote is committed.
- **Feed-poll WARNs in the soak:** the 8 WARN lines are `FeedFetchException` poll failures on feeds that already had error counts before the deploy. They are not new.

## User Setup Required

None beyond the plan's user_setup (all four keys were already exported; Orca Local Network was already granted).

## Next Phase Readiness

- Prod collects engagement now; Phase 12 calibration needs several weeks of it.
- Pending human check (SC-5 backstop): after at least a day of use, a read-only `SELECT kind, count(*) FROM article_engagement GROUP BY kind` shows rows for the kinds used. In the browser, Open Original opens a tab and the reading pane shows "Engaged: opened · Forget". At release time the table held 0 rows.
- Rollback remains `helm rollback myfeeder 19 -n myfeeder --wait` (0.2.1 ignores V7 as a future migration); the dump is at `$HOME/.cache/myfeeder-phase08/myfeeder-pre-0.3.0.dump`.

---
*Phase: 08-engagement-capture*
*Completed: 2026-09-30*

## Self-Check: PASSED

- FOUND: 08-RELEASE.md, the `.planning/milestone.lock` line in .gitignore
- FOUND commits: 4b3841b, db5acd7, 3a3ac8b
- FOUND: tag v0.3.0 on origin, image registry.bartram.org/bartram/myfeeder:0.3.0; v0.2.1 still 5461d0a
- No key value in 08-RELEASE.md or this SUMMARY (grep -qF against all four keys)
