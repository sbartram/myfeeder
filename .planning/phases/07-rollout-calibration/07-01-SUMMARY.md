---
phase: 07-rollout-calibration
plan: 01
subsystem: api
tags: [spring-boot, configuration-properties, interest-status, badge-tiers]

requires:
  - phase: 04-interest-status
    provides: InterestStatus / InterestStatusService and the append-only /api/interest/status contract
provides:
  - "MyfeederProperties.Interest.Blend.Tiers (myfeeder.interest.blend.tiers.high=70 / .neutral=40)"
  - "service.TierThresholds(int high, int neutral) record"
  - "InterestStatus.tiers appended last; GET /api/interest/status serves tiers: {high, neutral}"
affects: [07-02 frontend badge tiers, 07-09 tuned tier values]

actuals:
  tokens: 2093
  tasks: 2
  commits: 2
plan_head_before: 3189d7496a11128ecf56e0f23f3331e9551ad0c1

tech-stack:
  added: []
  patterns:
    - "Display-only config served on /status: bind under myfeeder.interest.blend.*, map to a service record, append to InterestStatus"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/TierThresholds.java
  modified:
    - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
    - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
    - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
    - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java

key-decisions:
  - "Badge tier thresholds are server config (myfeeder.interest.blend.tiers.high 70 / neutral 40), served as the last /api/interest/status field tiers:{high,neutral}; the client 70/40 is only a pre-load fallback (D-13)"

patterns-established:
  - "InterestStatus grows by appending components only; the new test asserts the five existing field names still exist"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "GET /api/interest/status serves tiers {high: 70, neutral: 40} after the five existing fields"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java#statusServesTheConfiguredTierThresholds"
        status: pass
    human_judgment: false
  - id: D2
    description: "Tiers are tuned by yaml alone: myfeeder.interest.blend.tiers.* bind through Spring's Binder and reach status()"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#tiersBindFromTheBlendTiersKeys"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java#servesTheDefaultTierThresholds"
        status: pass
    human_judgment: false
  - id: D3
    description: "Both application.yaml files carry identical tier keys; the dev overlay parity check stays green"
    requirement: OPS-02
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java#devOverlayResolvesEveryMainKeyToMainsValue"
        status: pass
    human_judgment: false

duration: 2min
completed: 2026-09-27
status: complete
---

# Phase 7 Plan 01: Server-side badge tier thresholds Summary

**The badge tier thresholds are now server config (`myfeeder.interest.blend.tiers.high: 70`, `.neutral: 40`). A new `TierThresholds` record serves them as `tiers: {high, neutral}`, the last field on `GET /api/interest/status`.**

## Performance

- **Duration:** about 2 min
- **Started:** 2026-09-27T20:17:48Z
- **Completed:** 2026-09-27T20:19:55Z
- **Tasks:** 2
- **Files modified:** 8 (1 created, 7 modified)

## Accomplishments
- Added `MyfeederProperties.Interest.Blend.Tiers` with defaults 70/40, bound at `myfeeder.interest.blend.tiers.*`.
- Added the `TierThresholds(int high, int neutral)` record. It is the last `InterestStatus` component, and the five existing components keep their order and names.
- `InterestStatusService.status()` reads the tiers from properties on every call.
- Set the keys explicitly in the main and test `application.yaml`, so changing a threshold only needs a yaml edit. `DevProfileConfigTest` parity still passes.

## Task Commits

1. **Task 1 (tracer): /status serves tiers bound from MyfeederProperties** - `c25753a` (feat)
2. **Task 2: tier keys in both application.yaml files + Binder proof** - `3d779ab` (feat)

**Plan metadata:** see the final docs(07-01) commit

## Files Created/Modified
- `src/main/java/org/bartram/myfeeder/service/TierThresholds.java`: the served tier pair record
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`: `Blend.tiers` field and nested `Tiers` class (70/40)
- `src/main/java/org/bartram/myfeeder/service/InterestStatus.java`: appended `TierThresholds tiers` and its Javadoc entry
- `src/main/java/org/bartram/myfeeder/service/InterestStatusService.java`: passes `new TierThresholds(t.getHigh(), t.getNeutral())`
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml`: the `# D-13:` comment plus `tiers.high: 70` / `tiers.neutral: 40`
- `src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java`: `statusServesTheConfiguredTierThresholds`
- `src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java`: `servesTheDefaultTierThresholds`, `tiersBindFromTheBlendTiersKeys`

## Decisions Made
- `InterestStatus` is wrapped over two lines. The `long failed, TierThresholds tiers)` fragment stays contiguous, so the plan's grep gate still matches.
- None otherwise. The plan was followed as specified.

## Deviations from Plan

### TDD note (Task 2, tdd="true")
- **RED was not observable.** When the Task 2 tests were first written, both passed, because the Task 1 tracer had already built the `Blend.Tiers` binding they exercise (the plan orders it that way). Following fail-fast rule 1, I checked that the tests are not vacuous with a temporary mutation: hardcoding `new TierThresholds(70, 40)` in `InterestStatusService` made `tiersBindFromTheBlendTiersKeys` fail. I then reverted the mutation with `git checkout -- <file>`, and it was never committed.
- As a result, Task 2 is one `feat(07-01)` commit and there is no `test(07-01)` commit. The plan's acceptance criterion needs the task commit to list exactly the three `<files>`, and this satisfies it. The plan is `type: execute` and `workflow.tdd_mode` is false, so the plan-level RED/GREEN gate enforcement does not apply.

**Total deviations:** 0 auto-fixed (one TDD process note above).
**Impact on plan:** none. Every acceptance criterion and verification command passes.

## TDD Gate Compliance
- RED `test(07-01)` commit: none. The behavior already existed from the Task 1 tracer; see the note above, including the mutation evidence.
- GREEN `feat(07-01)` commits: `c25753a`, `3d779ab`.

## Issues Encountered
None

## Verification
- `./gradlew test -x npmBuild -x npmInstall --tests InterestApiIntegrationTest --tests InterestStatusServiceTest --tests DevProfileConfigTest` passed. InterestApiIntegrationTest ran 9 tests, InterestStatusServiceTest 7 and DevProfileConfigTest 4, with 0 failures and 0 errors.
- Tracer feedback gate: the run was interactive with `end-of-phase` mode and an automated-only `<verify>`. I re-ran `<verify>`, it passed, and expansion continued.
- `git status --short` still shows the pre-existing unstaged edits (`.claude/CLAUDE.md`, `.envrc`, `.planning/config.json`, `CLAUDE.md`), untouched and not committed.

## User Setup Required
None. No external service configuration is needed.

## Next Phase Readiness
- Plan 07-02 (frontend) can consume `status.tiers` in the exact shape `{high: number, neutral: number}`.
- Plan 07-09 can ship tuned thresholds by editing the yaml in both `application.yaml` files.

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-27*
