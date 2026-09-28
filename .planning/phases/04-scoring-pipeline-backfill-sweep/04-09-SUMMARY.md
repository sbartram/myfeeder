---
phase: 04-scoring-pipeline-backfill-sweep
plan: 09
subsystem: testing
tags: [spring-boot, profiles, config-data, boottestrun, typesafe, jev, gap-closure]

requires:
  - phase: 04-scoring-pipeline-backfill-sweep
    provides: "Scoring sweep (PT1M initial delay, D-10), JevApiClient.isConfigured(), /api/interest/status counts"
provides:
  - "src/test/resources/application-dev.yaml overlay that restores main's live settings under bootTestRun"
  - "TestMyfeederApplication.DEV_PROFILE, activated only from the bootTestRun entry point"
  - "DevProfileConfigTest: Docker-free probe of the dev/no-profile environments, the main-mirror rule and a no-activation scan"
  - "MyfeederApplicationTests.suiteContextStaysOffline runtime guard"
affects: [phase-04-uat, phase-07-ops, local-dev-workflow]

actuals:
  tokens: 4800
  tasks: 3
  commits: 4
plan_head_before: 4a2d5fae00b6e3be6a4cb8d850cfb538beba4118

tech-stack:
  added: []
  patterns:
    - "Dev-only profile overlay in src/test/resources, activated by the bootTestRun entry point, never by a test"
    - "Config probe via SpringApplication with systemEnvironment removed and a fake key injected (boolean key assertions only)"

key-files:
  created:
    - src/test/resources/application-dev.yaml
    - src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java
  modified:
    - src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java
    - src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java
    - CLAUDE.md
    - .planning/phases/04-scoring-pipeline-backfill-sweep/04-05-PLAN.md

key-decisions:
  - "04-09: bootTestRun uses a dev profile overlay (src/test/resources/application-dev.yaml) activated only by TestMyfeederApplication; the test application.yaml and main application.yaml stay unchanged, so no @SpringBootTest context can bind a real TypeSafe key"
  - "04-09: The overlay mirrors main exactly (DevProfileConfigTest enforces every main key resolves to main's raw value); its only extra key is the explicit starter-default base-url https://api.typesafe.ai"

patterns-established:
  - "Profile-activation token scan: only TestMyfeederApplication may activate a profile; reading getActiveProfiles() is allowed"

requirements-completed: [SCOR-05, SCOR-01]

coverage:
  - id: D1
    description: "bootTestRun activates the dev profile and binds the TypeSafe key, real Jev base-url, PT1M first sweep, 5s/30s HTTP timeouts and app name"
    requirement: SCOR-05
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java#devProfileRestoresLiveMainSettings"
        status: pass
      - kind: integration
        ref: "bootTestRun smoke on port 18089 with a fake key: status JSON configured true, coldStart true, log line 'The following 1 profile is active: \"dev\"', no 'Jev not configured'"
        status: pass
    human_judgment: false
  - id: D2
    description: "Without the dev profile (every suite context) no key binds, base-url is loopback and first sweep is PT1H; no test activates the profile; overlay cannot drift from main"
    requirement: SCOR-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java#withoutTheProfileTheSuiteConfigStaysOffline"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java#devOverlayResolvesEveryMainKeyToMainsValue"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java#noTestActivatesTheDevProfile"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java#suiteContextStaysOffline"
        status: pass
      - kind: integration
        ref: "DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall (394 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Live-key end-to-end check with the real billed key (re-run of UAT test 1): sweep drains eligibleUnscored with SCORED rows, new arrivals scored, Re-score unread resets and re-drains, no new feed errors"
    requirement: SCOR-05
    verification: []
    human_judgment: true
    rationale: "Requires the real billed TypeSafe key and observation of live Jev scoring over several minutes; the automated smoke deliberately uses a fake key in cold start so no Jev call is made"
  - id: D4
    description: "CLAUDE.md Dev workflow bullet and bootTestRun gotcha plus the 04-05 human-check describe the working procedure; user's uncommitted CLAUDE.md hunks unchanged"
    verification:
      - kind: other
        ref: "Task 3 verify commands (3-line CLAUDE.md commit, before/after hunk diff identical, 04-05 human-check text, no protected file committed since base)"
        status: pass
    human_judgment: false

duration: 4min
completed: 2026-09-24
status: complete
---

# Phase 04 Plan 09: bootTestRun dev profile for live Jev Summary

**`./gradlew bootTestRun` now activates a `dev` profile whose `src/test/resources/application-dev.yaml` puts back main's TypeSafe key placeholder, the real Jev base-url, the PT1M first sweep and the 5s/30s timeouts. The test suite stays offline, and guards enforce both sides.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-09-24T21:49:19Z
- **Completed:** 2026-09-24T21:53:30Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- G-04-1 item 1: with `MYFEEDER_TYPESAFE_API_KEY` exported, bootTestRun starts with the dev profile and Jev configured.
- G-04-1 item 2: every `@SpringBootTest` context stays offline. Neither `application.yaml` changed. A runtime guard fails if a key or profile leaks in from the shell, and a source scan stops any test from activating `dev`.
- G-04-1 item 3: CLAUDE.md and the 04-05 human-check describe the procedure that now works.

## Task Commits

1. **Task 1: bootTestRun activates a dev overlay** - `28d64fb` (feat)
2. **Task 2: Guard the overlay against drift and keep suite contexts offline** - `79492fe` (test)
3. **Task 3: Document the live-key procedure** - `0ecce98` (docs, 04-05 human-check), `15a36d2` (docs, CLAUDE.md hunk-only)

## Files Created/Modified

- `src/test/resources/application-dev.yaml` - dev-only overlay: app name, HTTP timeouts, key placeholder, explicit base-url, Raindrop token placeholder, PT1M initial sweep
- `src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java` - `DEV_PROFILE` constant and `.withAdditionalProfiles(DEV_PROFILE)`
- `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java` - 4 Docker-free tests (dev probe, no-profile probe, main-mirror rule, no-activation scan with positive controls)
- `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java` - `suiteContextStaysOffline`
- `CLAUDE.md` - Dev workflow bullet and a new `bootTestRun` Gotchas bullet (committed as 3 changed lines only)
- `.planning/phases/04-scoring-pipeline-backfill-sweep/04-05-PLAN.md` - opening clause of the live-key human-check

## Evidence

**bootTestRun smoke** (port 18089, fake key `smoke-not-a-real-key`, log at `~/.cache/myfeeder-phase04/boottestrun-dev-smoke.09.log`):

```
INFO ... o.bartram.myfeeder.MyfeederApplication : The following 1 profile is active: "dev"
INFO ... o.bartram.myfeeder.MyfeederApplication : Started MyfeederApplication in 7.623 seconds (process running for 7.905)
STATUS={"configured":true,"breakerState":"CLOSED","coldStart":true,"eligibleUnscored":0,"failed":0}
```

There was no `Jev not configured` line. Afterwards no process carried the `server.port=18089` marker and the port was free.

**Mutation check** (Task 2 step 4): with the `myfeeder.raindrop.api-token` line deleted from the overlay, `devOverlayResolvesEveryMainKeyToMainsValue` FAILED with `[myfeeder.raindrop.api-token] expected: "${MYFEEDER_RAINDROP_API_TOKEN:}" but was: "null"`. After `git checkout -- src/test/resources/application-dev.yaml` it passed again, along with the other three DevProfileConfigTest tests.

**Full backend suite:** 394 tests in 58 result files, 0 failures and 0 errors. This includes the unchanged guards `TypeSafeConfigTest.testYamlMirrorsMainTypeSafePinsWithoutKey`, `JevResilienceTest.testYamlMirrorsMainJevInstances`, `HttpClientConfigurationTest.outboundTimeoutsFromMainApplicationYamlBind` and `MyfeederApplicationTests.sweepIsScheduledWithConfiguredDelays`. Neither `application.yaml` has changed since base `4a2d5fa`.

**CLAUDE.md hunk comparison:** `claude-md-user-hunks.09.before.diff` and `.after.diff` are identical apart from `index` lines, so the user's uncommitted hunks at lines 40, 55-56, 99, 105 and 149-151 are unchanged and still uncommitted. The commit `15a36d2` has exactly 3 changed lines. No commit since base touches `.envrc`, `.claude/CLAUDE.md` or `.planning/config.json`.

## Decisions Made

- Followed the plan's overlay approach (the debug session's only option that leaves test isolation byte-for-byte unchanged).

## Deviations from Plan

None - plan executed exactly as written.

Task 2 is marked `tdd="true"`, but its tests are guards that pass against the correct Task 1 overlay, so there was no classic RED commit. The plan's mutation check served as the failing-first evidence: the mirror guard failed on a dropped key and passed once the file was restored.

## Issues Encountered

The first mutation-check attempt redirected output to a wrong scratchpad path, so Gradle never ran. The file was restored and the check was re-run correctly, with the outcome recorded above.

## User Setup Required

None. For the end-of-phase human check, export the real `MYFEEDER_TYPESAFE_API_KEY` and run `./gradlew bootTestRun`.

## Next Phase Readiness

- G-04-1 is closed on the automated side. Re-run UAT test 1 with the real key (D3, human judgment) to confirm live scoring end-to-end.

---
*Phase: 04-scoring-pipeline-backfill-sweep*
*Completed: 2026-09-24*

## Self-Check: PASSED
