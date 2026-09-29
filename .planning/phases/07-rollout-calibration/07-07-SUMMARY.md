---
phase: 07-rollout-calibration
plan: 07
subsystem: infra
tags: [launch-backfill, typesafe, jev, resilience4j, circuit-breaker, k3s, psql, ops]

requires:
  - phase: 07-rollout-calibration
    provides: "07-06: prod on 0.2.0 (Helm rev 18) with the live TypeSafe key, cold start holding scoring idle; 07-03 Jev event log lines"
  - phase: 03-jev-scoring
    provides: "03-08 calibration rubric (profile + 7 topics) in $HOME/.cache/myfeeder-phase03/calibration-input.json"
provides:
  - "Prod rubric saved (profile version 2, topic ids 1-7) via load-phase3-rubric; cold start ended 2026-09-28T23:39:57Z"
  - "Launch backfill drained: 183 legacy-backlog articles, 192 SCORED rows, 0 FAILED, in 6 min 54 s"
  - "07-BACKFILL.md sections Backfill watch, Time series, Log evidence, Score rows, Throttle and Verdict (D-05 Overall PASS)"
affects: [07-08, 07-09, calibration, OPS-01]

actuals:
  tokens: 1300
  tasks: 3
  commits: 1
plan_head_before: f35dbf25d1b398d4152ed4aaec8d6c739c5e136d
plan_head_after: c921332ebe18c58bb543a64a200a05e924208ddd

tech-stack:
  added: []
  patterns:
    - "Throwaway watch (status every 60s, pod log refresh, read-only psql evidence every ~10 min) outside the repo, stopped by a marker file"
    - "Rubric loaded back to back through the app API so no article is scored against a partial rubric"

key-files:
  created: []
  modified:
    - .planning/phases/07-rollout-calibration/07-BACKFILL.md

key-decisions:
  - "User chose load-phase3-rubric at the Task 2 blocking-human checkpoint; the Phase 3 rubric was saved as-is (8 saves in 0.8 s)"
  - "No D-08 throttle: no breaker transition and no rate-limit retry occurred, so 07-09 has no env override to remove"
  - "D-05 launch-backfill verdict Overall PASS (drain, breaker CLOSED, 429 absorbed); concurrency stayed 1 and the SDK retry layer stayed off"

patterns-established:
  - "Backfill evidence doc holds numbers, class names, timestamps and ids only; checked with grep -F against live profile text, topic descriptions and topic names"

requirements-completed: [OPS-01]

coverage:
  - id: D1
    description: "Full rubric (profile + 7 topics) saved in prod back to back; cold start ended"
    requirement: OPS-01
    verification:
      - kind: other
        ref: "curl /api/interest/status -> configured true, coldStart false, breakerState CLOSED (Task 3 verify 1)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Launch backfill drained the legacy backlog with the breaker CLOSED throughout and no 429 storm (D-05)"
    requirement: OPS-01
    verification:
      - kind: other
        ref: "Task 3 verify 2: evidence.log last legacy_backlog=0; 0 'Jev circuit breaker' and 0 exhausted rate-limit lines in backfill*.log; all status samples CLOSED"
        status: pass
      - kind: other
        ref: "Task 3 verify 4: watch stopped; deployment env has no SDK-retry or concurrency override"
        status: pass
    human_judgment: false
  - id: D3
    description: "07-BACKFILL.md records the six evidence sections and an Overall PASS verdict without any rubric text"
    requirement: OPS-01
    verification:
      - kind: other
        ref: "Task 3 verify 3: six headings, '| Overall | PASS |', grep -F against live profile text and topic descriptions finds nothing"
        status: pass
    human_judgment: false

duration: 1h 13m
completed: 2026-09-28
status: complete
---

# Phase 7 Plan 07: Launch Backfill Summary

**The Phase 3 rubric was saved to prod in 0.8 s. The 183-article legacy backlog drained to 192 SCORED rows in 6 min 54 s with the Jev breaker CLOSED in all 53 samples, zero retries and zero FAILED rows. D-05 verdict: Overall PASS, with no throttle.**

## Performance

- **Duration:** 1h 13m wall clock, including about 54 min at the Task 2 blocking-human checkpoint. The watch started at 22:43:59Z and the evidence commit landed at 23:53Z.
- **Started:** 2026-09-28T22:41:10Z (07-07 dispatch after 07-06)
- **Completed:** 2026-09-28T23:54:10Z
- **Tasks:** 3 of 3 (Task 1 tracer by the previous executor, Task 2 checkpoint answered by the user, Task 3 by this continuation)
- **Files modified:** 1 (`07-BACKFILL.md`)

## Task 2 answer (verbatim)

`load-phase3-rubric`

## Accomplishments

- Rubric saved at **2026-09-28T23:39:57Z** with `PUT /profile` HTTP 200 (profile version 2), then 7 × `POST /topics` HTTP 201 (ids 1 to 7). All 8 saves fell between 23:39:57.037Z and 23:39:57.807Z, and none needed a retry. `/status` showed `coldStart:false` at once.
- **Baseline:** eligibleUnscored was 183 at the watch start and 190 at the save (7 new arrivals), and the legacy backlog was 183. **Peak** eligibleUnscored was 190.
- **Drain:** the first score row landed at 23:40:26Z and the last at 23:46:51Z, which is 6 min 54 s after the save. Each 2-minute sweep scored one batch of about 50 rows (50/52/50/40 per minute bucket). eligibleUnscored hit 0 at the 23:47:00Z sample, and legacy_backlog=0 at 23:50:01Z, confirmed by a fresh read-only query at 23:51:08Z.
- **Final:** eligibleUnscored 0 and failed 0. Score rows: SCORED 192 (183 legacy plus 9 new), FAILED 0, SKIPPED 0, retried 0. Every row has model `jev-1.13.0`.
- **Log evidence:** the pod `myfeeder-5d6c6b4b9f-q66j7` had no restart and no pod change, and its log since the watch start is 85 lines. It holds 0 `Jev retry attempt` lines, 0 rate-limit retries (the most in any 10-minute window is 0), 0 `Jev retries exhausted` lines and 0 `Jev circuit breaker` lines. There are 0 ERROR lines and 1 WARN line, a `ResourceAccessException` from `FeedPollingService` at 23:27:41Z: a feed host, before the save, not Jev.
- **Status samples:** 53, all CLOSED, 0 unreachable.
- **Throttle:** none.
- **D-05 verdict:** drain PASS, breaker CLOSED PASS, 429 absorbed PASS, **Overall PASS**. D-07: concurrency stayed 1.

## Task Commits

1. **Task 1 (tracer): the watch records status, pod logs and read-only DB evidence.** No commit. The artifacts are throwaway files under `$HOME/.cache/myfeeder-phase07/`.
2. **Task 2 (checkpoint:decision, blocking-human).** No commit. The user answered `load-phase3-rubric`.
3. **Task 3: save the rubric, watch the drain, record the D-05 verdict.** Commit `c921332` (docs).

**Plan metadata:** see the final `docs(07-07)` commits.

## Files Created/Modified

- `.planning/phases/07-rollout-calibration/07-BACKFILL.md`: appended the sections Backfill watch, Time series, Log evidence, Score rows, Throttle and Verdict.
- These throwaway files are not committed, and all live in `$HOME/.cache/myfeeder-phase07/`: `rubric-load.out` (HTTP codes and ids only), `rubric-saved-at.txt`, `final-evidence.txt`, `check.log`, `watch.stop`, plus the Task 1 watch files.

## Decisions Made

- I loaded the rubric through the app API (`load-phase3-rubric`), as the user chose, using a throwaway node loader that prints only HTTP codes and ids.
- No throttle was applied, because the D-08 triggers (a breaker transition, or more than 5 rate-limit retries in 10 minutes) never fired.

## Deviations from Plan

### Carried from the previous executor (Task 1, all minor)

**1. [Rule 2 - Robustness] The evidence psql adds `statement_timeout=30000` and `PGCONNECT_TIMEOUT=10`.** Both sit alongside `default_transaction_read_only=on`, so a hung query cannot stall the watch.
**2. [Rule 1 - Correctness] Logs come from the explicit current Running pod, with `deploy/myfeeder` as the fallback,** so the refresh never reads a terminating pod.
**3. [Rule 1 - Correctness] On a pod change, the watch fetches `kubectl logs <old pod>` one last time into `backfill-<old pod>.log`.** It copies the last `backfill.log` only as a fallback, and it persists the last seen pod in `watch-pod.txt` so the old log survives a watch restart. No pod change happened.

### This continuation (Task 3)

**4. [Rule 3 - Blocking] I ran `caffeinate -i -s -w <watch pid>` so the host could not sleep during the backfill.**
- **Found during:** Task 3 precondition check
- **Issue:** `status.log` had a 997 s gap (23:13:14Z to 23:29:51Z) where the workstation slept before the save. A sleep during the backfill would have left holes in the evidence.
- **Fix:** a caffeinate process tied to the watch pid. It exited with the watch.
- **Files modified:** none (a runtime process)

**5. [Rule 1 - Evidence] I appended the final status sample and evidence line (23:51:27Z) with the watch's exact query and format, then stopped the watch.**
- **Issue:** the watch's next scheduled SQL sample was about 10 minutes away. The plan asks for a final sample and a final evidence line before stopping.
- **Fix:** I appended one line to each of `status.log` and `evidence.log` using the same read-only SQL and the same line format, then ran `touch watch.stop`. The watch exited at 23:52:02Z.

**6. [Rule 2 - Privacy] I reworded one line in 07-BACKFILL.md because a topic name matched an ordinary token in it.**
- **Found during:** Task 3 verification, with an extra check beyond the plan: grep against the live topic names, since the doc header promises no topic names.
- **Issue:** a 6-letter, one-word topic name matched a token in the `## Throttle` line (an env-var name). No rubric text was disclosed.
- **Fix:** I reworded the line to "no sweep-throttle, SDK-retry or scoring-concurrency override". Re-check: 0 topic-name hits. The plan's own check against the profile text and topic descriptions passes.
- **Committed in:** `c921332`

---

**Total deviations:** 6 (3 carried from Task 1, 3 in Task 3), all minor.
**Impact on plan:** none on the outcome. The evidence is more complete, and the doc stays within its privacy promise.

## Issues Encountered

- The first per-minute rollup query used `GROUP BY 1` over an aggregate expression and errored. I reran it with a subquery. It was read-only and had no side effects.

## Authentication Gates

None.

## User Setup Required

None. `MYFEEDER_PG_PASSWORD` was already set in the shell (checked with `test -n` only).

## Next Phase Readiness

- The backfill drained and D-05 is PASS, so 07-08 calibration can proceed (D-12).
- 07-09 has no throttle env override to encode or remove.
- The scores reflect the Phase 3 rubric as saved. Editing a topic later changes its version, and existing scores keep the old judgment until a billed Re-score.

## Self-Check: PASSED

- FOUND: `.planning/phases/07-rollout-calibration/07-BACKFILL.md`
- FOUND: `$HOME/.cache/myfeeder-phase07/rubric-saved-at.txt`, `watch.stop`, `final-evidence.txt`, `rubric-load.out`
- FOUND: commit `c921332`
- All four Task 3 automated verifies returned rc=0. The PG password is absent from the watch files and the doc, and the topic-name check finds 0 hits.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-28*
