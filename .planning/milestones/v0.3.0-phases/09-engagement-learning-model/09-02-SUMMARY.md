---
phase: 09-engagement-learning-model
plan: 02
subsystem: ui
tags: [react, typescript, vitest, interest-ranking, engagement]

requires:
  - phase: 06-thumbs-feedback (v0.2.1)
    provides: LearnedLimit union, TopicEffect, TopicBreakdownRow, TopicLearned, effectNote, LearnedLine
provides:
  - "LearnedLimit union carries 'ENGAGEMENT_CAP' (D-11)"
  - "Optional TopicEffect.thumbsLearned / engagementLearned and TopicLearned.thumbsLearned / engagementLearned (D-15)"
  - "Optional TopicBreakdownRow.thumbsWeight / engagementWeight (D-16)"
  - "Vitest pins for the ENGAGEMENT_CAP fall-through in the vote toast (incl. research Pitfall 6) and the Interests learned line"
affects: [09-03, 09-05, phase-10-engagement-ui]

actuals:
  tokens: 1569
  tasks: 2
  commits: 3
plan_head_before: e0cd84ba7f008bb7bb76b05d20870552e23a1f7a
plan_head_after: 9aae83a15e2062c0a81822a175f66d530afdd2a9

tech-stack:
  added: []
  patterns:
    - "Server-appended JSON fields are typed as optional client properties so existing fixtures keep type-checking"
    - "Fall-through rendering of a new enum value is pinned by a test so the next phase changes it deliberately"

key-files:
  created: []
  modified:
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/utils/feedback.test.ts
    - src/main/frontend/src/components/TopicRow.test.tsx

key-decisions:
  - "effectNote, formatVoteToast and LearnedLine stay unchanged in Phase 9; ENGAGEMENT_CAP falls through to their defaults (D-11), and the client still computes no weight"
  - "Task 2 RED evidence is a vitest typecheck-mode TAP failure of limitSuffixes (TS2353). The gsd tdd-red-evidence checker parses only node:test TAP, so it cannot classify vitest output"

patterns-established:
  - "Pinned fall-through: new server enum values without UI wording get a test asserting today's default rendering"

requirements-completed: []

coverage:
  - id: D1
    description: "Frontend LearnedLimit union accepts ENGAGEMENT_CAP, and TopicEffect/TopicBreakdownRow/TopicLearned accept the appended split fields as optional properties; the whole frontend type-checks"
    requirement: LRN-04
    verification:
      - kind: other
        ref: "cd src/main/frontend && npx tsc -b"
        status: pass
    human_judgment: false
  - id: D2
    description: "Vote toast: an ENGAGEMENT_CAP effect takes effectNote's default branch ('Rust +1.8'), and an unchanged ENGAGEMENT_CAP topic is still listed as '+0.0' (Pitfall 6 pinned for Phase 10)"
    requirement: LRN-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#engagementCapFallsThroughToNoNote"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#engagementCapWithNoChangeIsStillListed"
        status: pass
    human_judgment: false
  - id: D3
    description: "Interests learned line: an ENGAGEMENT_CAP entry reads 'Learned from votes +8.0 · Effective weight +28.0' with no suffix"
    requirement: LRN-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#limitSuffixes"
        status: pass
      - kind: unit
        ref: "cd src/main/frontend && npx vitest run (32 files, 348 tests)"
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-09-30
status: complete
---

# Phase 9 Plan 02: Frontend Engagement Types Summary

**The frontend now types `ENGAGEMENT_CAP` and the thumbs/engagement split fields as optional server-sent properties, and tests pin the Phase 9 fall-through in the vote toast (including the Pitfall 6 "+0.0" listing) and in the Interests learned line.**

## Performance

- **Duration:** about 3 min (after context loading and `npm ci`)
- **Started:** 2026-09-30T16:15:28Z
- **Completed:** 2026-09-30T16:18:08Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- `LearnedLimit` = `'NONE' | 'LEARNED_CAP' | 'SIGN_CLAMP' | 'WEIGHT_RANGE' | 'ENGAGEMENT_CAP'` (D-11).
- Optional split fields, with existing names kept:
  - `TopicEffect.thumbsLearned` and `engagementLearned` (D-15)
  - `TopicBreakdownRow.thumbsWeight` and `engagementWeight` (D-16)
  - `TopicLearned.thumbsLearned` and `engagementLearned` (D-15)
- `feedback.test.ts` has two new cases: `engagementCapFallsThroughToNoNote` and `engagementCapWithNoChangeIsStillListed` (Pitfall 6).
- `TopicRow.test.tsx` `limitSuffixes` has a new ENGAGEMENT_CAP row that pins "Learned from votes +8.0 · Effective weight +28.0".
- `feedback.ts` and `TopicRow.tsx` are byte-identical to main.

## Task Commits

1. **Task 1 (tracer): type ENGAGEMENT_CAP and split fields, pin toast fall-through** - `76f9695` (feat)
2. **Task 2: TopicLearned split fields + learned-line pin (TDD)**
   - RED - `570a6bd` (test)
   - GREEN - `9aae83a` (feat)

REFACTOR was not needed.

## Files Created/Modified
- `src/main/frontend/src/types/index.ts` - `ENGAGEMENT_CAP` union member; optional split fields on `TopicEffect` and `TopicBreakdownRow`.
- `src/main/frontend/src/api/interest.ts` - optional `thumbsLearned` / `engagementLearned` on `TopicLearned`. The doc now says `learned` combines votes and engagement until Phase 10 (ENG-F6 is still deferred).
- `src/main/frontend/src/utils/feedback.test.ts` - the `effect` helper passes the split fields through when given, plus the two new ENGAGEMENT_CAP cases.
- `src/main/frontend/src/components/TopicRow.test.tsx` - the ENGAGEMENT_CAP `limitSuffixes` row.

## Decisions Made
- Followed the plan. The client only types and prints the server's fields and computes no learned, split or effective weight, so the prohibition holds.

## TDD Gate Compliance

Task 2 carries `tdd="true"`, and the gate sequence is present: `test(09-02)` `570a6bd` comes before `feat(09-02)` `9aae83a`.

- **RED evidence (intentional, on the target test):**
  - Command: `npx vitest run --typecheck.only --typecheck.include=src/components/TopicRow.test.tsx --typecheck.tsconfig=tsconfig.app.json --reporter=tap src/components/TopicRow.test.tsx`
  - Result: exit 1, with `not ok 3 - limitSuffixes` failing on `TypeCheckError: Object literal may only specify known properties, and 'thumbsLearned' does not exist in type 'Partial<TopicLearned>'`
  - `npx tsc -b` failed the same way (TS2353).
  - Expected: the ENGAGEMENT_CAP `TopicLearned` fixture type-checks.
- **Checker limitation:** `gsd-tools check tdd-red-evidence` returned `INVALID_RED (zero_tests_discovered)` because its parser reads only node:test TAP summary lines (`# tests/# pass/# fail`), which vitest does not emit. The failure itself is a real, targeted RED on the planned behavior. This is recorded here and not hidden.
- **Runtime rendering was already green in RED:** by design, the plan pins the existing D-11 fall-through and forbids editing `TopicRow.tsx`, so only the type-level half of the behavior could be red.
- **GREEN:** `npx tsc -b` is clean, the typecheck-mode run reports no errors, and all 348 vitest tests in 32 files pass.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- The worktree had no `node_modules`, so `npm ci` ran in the worktree's `src/main/frontend`, as the orchestrator's environment notes said. `package.json` and `package-lock.json` are unchanged.

## Known Stubs
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The frontend accepts the Phase 9 payloads from plans 09-03 and 09-05, in either order.
- **Input for Phase 10:**
  - Reword ENGAGEMENT_CAP in `effectNote` and `LearnedLine`.
  - Split the "Learned from votes" line (ENG-F6).
  - Decide whether an engagement-capped topic the vote didn't move should still be listed as "+0.0" (Pitfall 6).
  - Each of these changes a pinned test on purpose.
- LRN-04 stays pending in REQUIREMENTS.md: 09-01, 09-03, 09-04 and 09-05 also declare it (shared-ID gate).

## Self-Check: PASSED

- All four modified source files and this SUMMARY exist.
- Commits `76f9695`, `570a6bd` and `9aae83a` are present on the worktree branch.
- The working tree is clean.

---
*Phase: 09-engagement-learning-model*
*Completed: 2026-09-30*
