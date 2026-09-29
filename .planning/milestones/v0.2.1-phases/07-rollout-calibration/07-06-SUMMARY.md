---
phase: 07-rollout-calibration
plan: 06
subsystem: infra
tags: [release, deploy, helm, k3s, axion, docker, pg_dump, flyway, typesafe, jev]

requires:
  - phase: 07-rollout-calibration
    provides: "07-01..07-05: tier config on /status, Jev event logging, calibration replay tooling, OPS-03 CLAUDE.md docs"
  - phase: 01-dependency-upgrade
    provides: "01-04 release precedent (preflight, blocking-human approval, publish, verify, soak)"
provides:
  - "Release v0.2.0 (tag on origin, main pushed as the --no-ff merge of sbartram/main)"
  - "Image registry.bartram.org/bartram/myfeeder:0.2.0 (sha256:2252196edbc69dc3e2bbf1d29334256ab58942ae2503fc5c3bb378d49edf7fd7)"
  - "Prod on 0.2.0 (Helm revision 18) with the live TypeSafe key, Flyway V6 applied, cold start holding scoring idle"
  - "Pre-deploy pg_dump and a recorded rollback revision (17 = 0.1.24)"
  - "07-BACKFILL.md opening with the Release 0.2.0 facts table (07-07 appends the backfill evidence)"
affects: [07-07, 07-08, 07-09, launch-backfill, calibration]

actuals:
  tokens: 3100
  tasks: 3
  commits: 1
plan_head_before: 88d2218934afc9c9b232b398a55f2c499b59eed2
plan_head_after: 3eb4637

tech-stack:
  added: []
  patterns:
    - "Release ops run cross-checkout against MAIN via git -C / (cd MAIN && ...); evidence and dump live only under $HOME/.cache/myfeeder-phase07 (dir 700, files 600)"
    - "Secret checks by presence (test -n) and sha256 equality only; never printed, never written"

key-files:
  created:
    - .planning/phases/07-rollout-calibration/07-BACKFILL.md
  modified:
    - .envrc (committed in MAIN on main as abeac7f, not in this worktree)

key-decisions:
  - "User approved publishing release 0.2.0 at the Task 2 blocking-human checkpoint (verbatim: approve)"
  - "0.2.0 released from main 687217f (tag v0.2.0), image sha256:2252196e, Helm rev 18, rollback target rev 17 (0.1.24); failure path not run"
  - "Live TypeSafe key deployed (key-hash match); preview smoke 200 jev-1.13.0 noul 0.02; /status configured, coldStart, CLOSED, tiers 70/40, eligibleUnscored 182"
  - "Soak passed 10.5 min: 46/46 feeds registered, 0 ERROR, 0 Jev log lines; human UI check (Priority CTA, Interests dialog) queued for end-of-phase UAT"

patterns-established:
  - "A secret change on this cluster rolls the app twice (chart checksum/secret plus the cluster Reloader); expect two new ReplicaSets per secret-bearing deploy"

requirements-completed: [OPS-01]

coverage:
  - id: D1
    description: "Release v0.2.0 published: .envrc committed after a SAFE scan, --no-ff merge of sbartram/main pushed, tag v0.2.0 on origin, image 0.2.0 in the registry"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "Task 3 verify block 1 (ls-remote tag, merge-base main origin/main, docker manifest inspect, main^2 ancestor of sbartram/main, MAIN clean)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Prod runs 0.2.0 with Flyway V6 applied successfully, clean startup and a new SPA bundle"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "Task 3 verify blocks 2 and 3 (rollout status, image :0.2.0, /api/version 0.2.0, flyway_schema_history V6 success t, startup.log checks, bundle changed)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Live TypeSafe key in the cluster matches the local key, /status is configured/coldStart/CLOSED with tiers 70/40, and one preview call authenticates"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "Task 3 verify block 4 (key-check.txt key-hash: match; status-after-deploy.json and preview.json node assertions)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Pre-deploy dump (mode 600, pg_restore-readable) and a 10-minute soak with every feed registered and no ERROR line"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "Task 3 verify block 5 (dump mode 600, dump-list TABLE DATA public article, Registered polling tasks for 46 feeds = pre-deploy 46, no ERROR, soak.log mtime >= rollout + 600s)"
        status: pass
    human_judgment: false
  - id: D5
    description: "07-BACKFILL.md holds the Release 0.2.0 table and no secret"
    verification:
      - kind: other
        ref: "Task 3 verify block 6 (grep section and tag; grep -F -f process substitution against all four secrets)"
        status: pass
    human_judgment: false
  - id: D6
    description: "0.2.0 UI loads in prod with the key configured: Priority shows the cold-start call to action, the Interests dialog shows no not-configured notice, reader/feed tree/article list behave as before"
    requirement: "OPS-01"
    verification: []
    human_judgment: true
    rationale: "Visual check of the deployed UI (plan's <human-check>); queued for end-of-phase UAT per human_verify_mode end-of-phase"

duration: 14min
completed: 2026-09-28
status: complete
---

# Phase 7 Plan 06: Release 0.2.0 and Deploy with the Live Key Summary

**Interest ranking released as v0.2.0 (main 687217f, image sha256:2252196e) and deployed to k3s as Helm revision 18 with the live TypeSafe key: V6 applied, key hash matches, one preview call returned jev-1.13.0, cold start holds scoring idle, and a 10.5-minute soak registered all 46 feeds with no ERROR**

## Performance

- **Duration:** 14 min for this continuation (Task 3). Task 1 ran in the previous executor.
- **Started:** 2026-09-28T22:24:47Z (continuation)
- **Completed:** 2026-09-28T22:39:17Z
- **Tasks:** 3 of 3 (Task 1 tracer, Task 2 decision checkpoint, Task 3 release)
- **Files modified:** 2 (07-BACKFILL.md in this worktree; .envrc committed in MAIN)

## User Decision (Task 2)

The user's answer at the blocking-human checkpoint, verbatim: `approve`

(Typed by the user in the orchestrator session and passed to this continuation executor.)

## Accomplishments

- D-16: a fresh re-run of `envrc-scan.sh` reported `envrc: SAFE` (both added lines are `$`-indirected assignments). The staged `.envrc` was committed on main as `abeac7f` and the trufflehog pre-commit hook passed.
- D-01: `git merge --no-ff sbartram/main` produced `687217f` (parents `abeac7f`, `88d2218`), and axion then read `0.1.25-SNAPSHOT`. `git push origin main` went through on the first try (29da9aa..687217f). `./gradlew release -Prelease.versionIncrementer=incrementMinor` created and pushed the annotated tag `v0.2.0`, which peels to `687217f`.
- `./gradlew clean bootJar` produced exactly one jar, `myfeeder-0.2.0.jar` (build-info `build.version=0.2.0`, embedded bundle `index-C8-FoLkO.js`), and VERSION read exactly `0.2.0`. The image was built from the Dockerfile with `--provenance=false` and pushed with digest `sha256:2252196edbc69dc3e2bbf1d29334256ab58942ae2503fc5c3bb378d49edf7fd7`.
- D-03: a `pg_dump -Fc` of prod was taken right before the deploy. It is 11,305,302 bytes, mode 600, and readable by `pg_restore --list` (it includes `TABLE DATA public article`). The rollback revision is 17 (0.1.24), and it is still in Helm history after the upgrade.
- D-02: `./deploy.sh 0.2.0` ran with the key exported and produced Helm revision 18. The rollout finished at 22:27:29Z. The pod logged `Started MyfeederApplication` in 13.25 s with no ERROR, no failed-to-start banner and no `TypeSafe Jev not configured` line. Flyway V6 succeeded (2586 ms). `/api/version` reports 0.2.0, and the served bundle changed from `index-DmtnXdgs.js` to `index-C8-FoLkO.js`.
- `key-hash: match`. `/api/interest/status` = `{configured: true, breakerState: CLOSED, coldStart: true, eligibleUnscored: 182, failed: 0, tiers: {high: 70, neutral: 40}}`. `article_score` has 0 rows.
- The preview smoke (one billed call, article 26041) returned HTTP 200, `{"noul": 0.02, "model": "jev-1.13.0"}`.
- Soak: `soak.log` was captured at 22:38:00Z, 10.5 min after the rollout. It shows `Registered polling tasks for 46 feeds`, matching the pre-deploy count of 46, with 0 ERROR lines and 0 `Jev ` lines. `/status` still reads configured, coldStart and CLOSED.
- The failure path did not run: no rollback and no restore.
- D-04: 0.2.0 ships with the open review warnings (06-REVIEW WR-01..04, the 06-UI-REVIEW a11y items, 03-REVIEW WR-01..05). This plan includes no review fix.

## Task Commits

1. **Task 1: Tracer, dry run of the release path**: no commit (read-only; evidence in `$HOME/.cache/myfeeder-phase07/`)
2. **Task 2: Checkpoint, approve publishing 0.2.0**: n/a (answer `approve`)
3. **Task 3: Release, deploy and verify 0.2.0**:
   - `abeac7f` (MAIN, main): chore: commit staged .envrc update (D-16)
   - `687217f` (MAIN, main): Merge branch 'sbartram/main': interest ranking (Phases 2-7), tagged `v0.2.0`
   - `3eb4637` (sbartram/main): docs(07-06): record release 0.2.0 facts in 07-BACKFILL.md (OPS-01)

`commits: 1` in the frontmatter is measured on sbartram/main from the plan ledger (`88d2218..HEAD`). The two MAIN-side commits are on main and are listed above.

## Files Created/Modified

- `.planning/phases/07-rollout-calibration/07-BACKFILL.md`: the content-rule paragraph plus the `## Release 0.2.0` facts table (07-07 appends to it)
- `.envrc` (MAIN worktree only): the staged update committed after the SAFE scan. This worktree's own unstaged `.envrc` was not touched.
- Evidence, not committed, under `$HOME/.cache/myfeeder-phase07/`: image-digest.txt, myfeeder-pre-0.2.0.dump (600), dump-list.txt, bundle-before/after.txt, feeds-before-count.txt, deploy-time.txt, rollout-time.txt, startup.log, version-after.json, key-check.txt, status-after-deploy.json, status-after-soak.json, preview-article-id.txt, preview-code.txt, preview.json, soak.log

## Decisions Made

- The steps followed the plan in order. The executor made no design decisions.
- The feed names in the two soak WARN lines were left out of 07-BACKFILL.md to honor its content rule (numbers, class names, timestamps and ids only).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Task 1 verify block 5 parsed the pg_dump major version wrongly (previous executor)**
- **Found during:** Task 1 (tracer preflight)
- **Issue:** `pg_dump --version | awk '{print $NF}'` returns `(Homebrew)` on this host (`pg_dump (PostgreSQL) 18.6 (Homebrew)`), so the literal check exits 1 even though the versions are compatible.
- **Fix:** Used the corrected parse `grep -oE '[0-9]+\.[0-9]+' | head -1 | cut -d. -f1`, which exits 0 (server 180003 = 18, client 18).
- **Files modified:** none (a verify command in the plan text; the plan file was not edited)
- **Verification:** the corrected check passed and is recorded in preflight.txt (`pg-major: pass`)
- **Committed in:** n/a (read-only task)

---

**Total deviations:** 1 auto-fixed (1 plan verify-command bug)
**Impact on plan:** None on the release. The version-compatibility gate still ran with a correct parse.

## Issues Encountered

- **The deploy rolled twice.** On the secret change, the chart's `checksum/secret` annotation rolled ReplicaSet revision 19 at 22:27:00Z, and the cluster's Reloader (`Changes detected in 'myfeeder-secret'`) updated the Deployment again one second later (ReplicaSet revision 20). The revision-19 pod was stopped seconds after its container started. The revision-20 pod validated six migrations and found the schema already at V6. `flyway_schema_history` shows V6 `success = t`, installed at 22:27:16 UTC, and all six interest tables exist. There was one Helm revision (18). This did no harm. Expect it on any future deploy that changes the secret.
- **Plan-text count:** sbartram/main was 330 commits ahead of main at preflight. The plan text says 306.
- **Two WARN lines in the soak log.** Both are pre-existing feed-level fetch failures, `FeedFetchException` with a body over the 10 MiB cap and an HTTP 429 from a feed host. Neither is an app error, and there were no ERROR lines.

## Known Stubs

None.

## User Setup Required

None. The four deploy secrets were already in the executor shell (presence checked with `test -n` only).

## Next Phase Readiness

- Ready for 07-07: prod is on 0.2.0 with the key live, cold start holds, and 182 eligible unscored articles are waiting. The backfill starts when the user saves a rubric. The 07-07 evidence goes into `07-BACKFILL.md` below the release table.
- Rollback remains available: `helm rollback myfeeder 17 -n myfeeder --wait` (0.1.24 tolerates V6 through `*:future`). The dump is at `$HOME/.cache/myfeeder-phase07/myfeeder-pre-0.2.0.dump`.
- Queued for end-of-phase UAT: open http://192.168.44.204/priority (expect the cold-start call to action) and Settings, then Interests (expect no "not configured" notice).

## Self-Check: PASSED

- FOUND: .planning/phases/07-rollout-calibration/07-BACKFILL.md
- FOUND: 3eb4637, abeac7f, 687217f
- origin: refs/heads/main = 687217f; refs/tags/v0.2.0 = e796c8e (annotated), peeled to 687217f
- All six Task 3 automated verify blocks exited 0. No secret was found in 07-BACKFILL.md or in the evidence text files.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-28*
