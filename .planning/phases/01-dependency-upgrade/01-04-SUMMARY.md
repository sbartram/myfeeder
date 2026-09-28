---
phase: 01-dependency-upgrade
plan: 04
subsystem: infra
tags: [release, axion-release, docker, helm, k3s, deploy, soak]

requires:
  - phase: 01-dependency-upgrade (01-03)
    provides: "Local, unpushed --no-ff merge 29da9aa on main (release clone), axion at 0.1.24-SNAPSHOT"
provides:
  - "main pushed to origin (02f74a1..29da9aa)"
  - "Release tag v0.1.24 on origin (on merge commit 29da9aa)"
  - "Image registry.bartram.org/bartram/myfeeder:0.1.24 (sha256:f1517e2dd5f108ba2232015b38c29f4836be7ed42990e1d420329c5709ecaae9)"
  - "Helm release myfeeder revision 17 in namespace myfeeder running 0.1.24; startup clean; 20-minute soak passed"
affects: [phase-02 onward (production baseline is now 0.1.24 on Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3)]

plan_head_before: df7419b8c06fbe6c48d22c0607a4cd585e4d148b
actuals:
  tokens: 0        # operational plan: no source/doc diff besides this SUMMARY
  tasks: 3
  commits: 0       # measured: git rev-list --count df7419b..HEAD before the SUMMARY commit (no code changes; release commits/tag live in the release clone)

tech-stack:
  added: []
  patterns:
    - "Release soak judged on lastPolledAt vs deploy-time (304s count), errorCount compared per previously-healthy feed"

key-files:
  created:
    - .planning/phases/01-dependency-upgrade/01-04-SUMMARY.md
  modified: []

key-decisions:
  - "01-04: User answered `approve` at the blocking-human release gate (D-05); optional pre-push TruffleHog hook NOT installed (not requested)"
  - "01-04: Released v0.1.24 from main 29da9aa as merged (the docs-only df7419b stays on sbartram/main only); image digest sha256:f1517e2d..., Helm revision 17"
  - "01-04: No Redis FLUSHALL and no rollback were needed; startup had 0 ERROR lines and no cache deserialization errors"

patterns-established:
  - "Cut-a-release pipeline verified end to end with the Dockerfile image path (no bootBuildImage): push main -> gradlew release -> clean bootJar -> VERSION gate -> docker build/push -> deploy.sh $VERSION"

requirements-completed: [UPG-03]

coverage:
  - id: D1
    description: "main and tag v0.1.24 are on origin, and image registry.bartram.org/bartram/myfeeder:0.1.24 exists in the registry"
    requirement: UPG-03
    verification:
      - kind: other
        ref: "git ls-remote --exit-code --tags origin refs/tags/v0.1.24 && git merge-base --is-ancestor main origin/main && docker manifest inspect registry.bartram.org/bartram/myfeeder:0.1.24"
        status: pass
    human_judgment: false
  - id: D2
    description: "k3s deployment rolled out on :0.1.24 and /api/version reports 0.1.24"
    requirement: UPG-03
    verification:
      - kind: other
        ref: "kubectl -n myfeeder rollout status deploy/myfeeder; deployed-image.txt ends :0.1.24; version-after.json {\"version\":\"0.1.24\"}"
        status: pass
    human_judgment: false
  - id: D3
    description: "Clean startup (Started MyfeederApplication, no failed-to-start banner, no ERROR line) and new SPA bundle live (index-BQIrFRxz.js -> index-DmtnXdgs.js)"
    requirement: UPG-03
    verification:
      - kind: other
        ref: "Task 3 check 3 over $HOME/.cache/myfeeder-phase01/{startup.log,bundle-before.txt,bundle-after.txt}"
        status: pass
    human_judgment: false
  - id: D4
    description: "20-minute soak: every one of the 40 previously healthy feeds polled after deploy-time with errorCount 0; the new pod registered all 46 feeds"
    requirement: UPG-03
    verification:
      - kind: other
        ref: "Task 3 soak check (node, evidence files) exit 0; Registered polling tasks for 46 feeds == feeds-before.json length"
        status: pass
    human_judgment: false
  - id: D5
    description: "The reader UI works as before on 0.1.24: feed tree, article list, reading pane and Reader View, with post-deploy articles visible"
    requirement: UPG-03
    verification: []
    human_judgment: true
    rationale: "ROADMAP Phase 1 success criterion 3 needs visual confirmation. Whether publishers post cannot be forced, and UI regressions from the React 19.3 / TanStack Query refresh cannot be grepped (plan's <human-check>)"

duration: 26 min (continuation run, Task 3 only)
completed: 2026-09-22
status: complete
---

# Phase 1 Plan 4: Publish and Deploy Release 0.1.24 Summary

**Release v0.1.24, the Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3 upgrade plus the frontend refresh, is published and runs in production. main (29da9aa) and tag v0.1.24 are on origin. Image `myfeeder:0.1.24` (sha256:f1517e2d…) runs as Helm revision 17 on k3s. Startup was clean, and a 20-minute soak shows all 40 previously healthy feeds still polling cleanly.**

## Performance

- **Duration:** about 26 min for this continuation (Task 3, including the 20-minute soak wait). Task 1 (preflight) ran in the previous executor.
- **Started (continuation):** 2026-09-23T00:53:41Z
- **Completed:** 2026-09-23T01:19:17Z
- **Tasks:** 3 of 3 (Task 1 tracer preflight, Task 2 decision, Task 3 auto)
- **Files modified:** 0 in the repo (operational plan). Evidence is under `$HOME/.cache/myfeeder-phase01/`.

## Task 2 answer (verbatim)

The user answered: **approve**

The user did not ask for the optional pre-push TruffleHog hook, so it was not installed.

## Accomplishments

**Release facts**

| Item | Value |
|------|-------|
| Pushed main SHA | `29da9aa49b349140bcb17ff7a31e8c8815fe9818` (`git push origin main`: `02f74a1..29da9aa`, 24 commits) |
| Tag | `v0.1.24` (tag object `58824e7e`, points at 29da9aa), created by axion `createRelease`, pushed by `gitPushRelease` |
| VERSION | `0.1.24`, gated as exact before `docker build`; `build/libs/` held exactly one jar, `myfeeder-0.1.24.jar` |
| Image | `registry.bartram.org/bartram/myfeeder:0.1.24`; `docker push` digest `sha256:f1517e2dd5f108ba2232015b38c29f4836be7ed42990e1d420329c5709ecaae9` (size 2002), built with `docker build --provenance=false` |
| Helm | release `myfeeder`, revision **17** (was 16 on 0.1.23), STATUS deployed |
| Deploy time (baseline) | 2026-09-23T00:56:05Z |
| Rollout time | 2026-09-23T00:57:10Z (`rollout status` succeeded; new pod `myfeeder-59d488df89-2cjnf` on tp-2, 0 restarts) |

**Startup checks (all pass)**

- The log shows `Started MyfeederApplication in 9.856 seconds` and `Registered polling tasks for 46 feeds`.
- It has no `APPLICATION FAILED TO START` banner and 0 ` ERROR ` lines.
- The startup log's 2 WARN lines are both `Failed to poll feed` on feeds that were already failing (31 Istio, 13 HN java).
- There were no Redis cache deserialization errors, so the cache flush was **not** run.
- `/api/version`: `{"version":"0.1.24","buildTime":"2026-09-23T00:54:42.637Z"}`. The deployment image is `registry.bartram.org/bartram/myfeeder:0.1.24`.
- The bundle changed from `assets/index-BQIrFRxz.js` before the deploy to `assets/index-DmtnXdgs.js` after it, so the new frontend is live.

**Soak check (final run, `feeds-after.json` captured 01:17:58Z, 20 min after rollout). Exit 0:**

```json
{"soakMinutes":20,"healthy":40,"total":46,"clean":40,"only304":18,"recovered":[],"remoteSide":[],"unresolved":[],"notPolled":[],"regression":[],"pending":[]}
```

- 40 of 46 feeds were healthy before the deploy, and all 40 are `clean`. 18 of them answered only with 304 Not Modified.
- No recheck was needed, so `feeds-recheck.json` and `remote-probe.txt` were not created and no workstation probe ran.
- The registration check passes: `Registered polling tasks for 46 feeds` equals the 46 feeds in `feeds-before.json`.
- `soak.log` (since deploy-time) holds **37** `Polled feed` lines, **2** `Failed to poll feed` lines and 0 ERROR lines.
- Articles: 50 items in `articles-after.json`. The newest `fetchedAt` is 2026-09-23T01:12:15Z, and **2** items were fetched after the rollout.

**Feeds whose health changed:** none of the previously healthy feeds. The only errorCount changes were one more failure each on 2 feeds that were already failing:

| Feed | errorCount before → after | lastError |
|------|---------------------------|-----------|
| 31 Istio Blog and News | 56 → 57 | `FeedFetchException: Feed body exceeds 10485760 bytes fetching https://istio.io/feed.xml` (pre-existing 10 MiB cap) |
| 13 Hacker News - Newest: "java" | 16 → 17 | `ResourceAccessException: I/O error on GET request for "https://hnrss.org/newest": connection timed out after 5000 ms` |

The feed 13 error is the **D-02 5s connect timeout** at work, as the plan asked us to call out. The feed was already failing before the deploy (errorCount 16), so this is intended behavior and not a regression. The other 4 feeds that were already failing (22, 11, 8, 48) did not change.

**Failure path:** not triggered. The app was not rolled back and production stays on 0.1.24.

## Task Commits

1. **Task 1 (tracer): read-only preflight.** No commit. The evidence is `preflight.txt`, `version-before.json` and `feeds-preflight.json`.
2. **Task 2: approve publishing 0.1.24 (checkpoint:decision, blocking-human).** No commit. The user answered `approve`.
3. **Task 3: push, release, build, ship, verify and soak.** No commit in this checkout, because it is purely operational. What it published: main `29da9aa` pushed, tag `v0.1.24`, image `0.1.24`, Helm revision 17.

**Plan metadata:** the `docs(01-04)` commit on sbartram/main.

## Files Created/Modified

- `.planning/phases/01-dependency-upgrade/01-04-SUMMARY.md` is this summary.
- Evidence files, not committed because the repo is public: `$HOME/.cache/myfeeder-phase01/` holds `feeds-before.json`, `bundle-before.txt`, `deploy-time.txt`, `rollout-time.txt`, `startup.log`, `version-after.json`, `bundle-after.txt`, `deployed-image.txt`, `feeds-after.json`, `soak.log`, `articles-after.json`, `docker-push.txt` and `release-version.txt`.

## Decisions Made

- The release shipped main exactly as merged in 01-03 (29da9aa). The docs-only commit `df7419b` (01-03 planning metadata) exists only on sbartram/main and was not re-merged. The preflight noted this. It doesn't affect the release because it touches only `.planning/`.
- No Redis flush, because no deserialization errors were observed.

## Deviations from Plan

None. The plan was executed exactly as written.

- The plan's environment notes expected a TruffleHog pre-push hook in the release clone, but only a pre-commit hook is installed there. The Task 1 preflight recorded this, and it ran a read-only TruffleHog scan over `02f74a1..main` instead: 0 findings. The push therefore ran without a pre-push scan. No hook was bypassed.
- The git-lfs lock-verify fallback was not needed.

## Issues Encountered

- None blocking.
- The only WARN lines were the 2 `Failed to poll feed` lines on feeds that were already failing (see above).
- No secret value appears in any evidence file: for each of the three secrets, `grep -rlF` over the evidence directory matched 0 files. The values were never printed.

## User Setup Required

None. The three deploy secrets were already present in the shell.

## Next Phase Readiness

- Phase 1's automated criteria are all met. Production runs 0.1.24 on the patch/GA dependency line, and feeds poll as before.
- **Pending end-of-phase human check (UAT):**
  1. Open http://192.168.44.204.
  2. Load the feed tree, open a feed with recent items and open an article.
  3. Use Reader View once.
  4. Confirm that articles fetched after `deploy-time.txt` (00:56:05Z) appear and that nothing looks different from 0.1.23.
- The 0.1.23 rollback stays available (`./deploy.sh 0.1.23`), since the image is still in the registry and there was no schema change.
- Two pre-existing feed issues are unchanged and outside this milestone's scope: Istio's feed is over 10 MiB, and hnrss.org times out.

---
*Phase: 01-dependency-upgrade*
*Completed: 2026-09-22*

## Self-Check: PASSED

- FOUND: tag v0.1.24 on origin (ls-remote), main == origin/main == 29da9aa
- FOUND: registry manifest for myfeeder:0.1.24
- FOUND: deployment image :0.1.24, /api/version 0.1.24, pod Running with 0 restarts after 22 min
- FOUND: evidence files feeds-after.json, soak.log, articles-after.json, startup.log, bundle-after.txt, rollout-time.txt
- Soak check exit 0; registration check exit 0; checks 1-3 exit 0
