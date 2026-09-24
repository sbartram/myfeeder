---
phase: 04-scoring-pipeline-backfill-sweep
verified: 2026-09-24T22:06:00Z
status: human_needed
score: 95/96 must-haves verified (5/5 roadmap success criteria; 83/83 plan 04-01..04-08 truths, 1 of them by human acceptance; 7/7 plan 04-09 truths; G-04-1 live-drain truth present, behavior unverified)
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-01-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-01-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-02-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-02-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-03-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-03-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-04-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-04-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-05-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-05-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-06-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-06-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-07-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-07-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-08-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-08-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-09-PLAN.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-09-SUMMARY.md
  - .planning/phases/04-scoring-pipeline-backfill-sweep/04-UAT.md
  - CLAUDE.md
  - src/main/frontend/src/App.css
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java
  - src/main/java/org/bartram/myfeeder/controller/RescoreRequest.java
  - src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java
  - src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
  - src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java
  - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
  - src/main/java/org/bartram/myfeeder/service/FeedPollingService.java
  - src/main/java/org/bartram/myfeeder/service/InterestRescoreService.java
  - src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/RescoreCount.java
  - src/main/java/org/bartram/myfeeder/service/ScoringFailure.java
  - src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java
  - src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java
  - src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java
  - src/test/resources/application-dev.yaml
  - src/test/resources/application.yaml
covered_digest: "v1:sha256:9caf8c68f02f041427467cb835df18f9ffd7c4d3dba145d0c0b06575ae41e806"
behavior_unverified: 1
overrides_applied: 1
overrides:
  - must_have: "Two concurrent writes for the same article cannot create two rows or an FK error, because ON CONFLICT on the article_score primary key serializes them"
    reason: "Backstop truth accepted without a concurrent-writer test: Postgres ON CONFLICT on the PK plus the single jev-score thread and in-flight set (UAT test 4, result pass)"
    accepted_by: "Scott Bartram"
    accepted_at: "2026-09-24T21:16:14Z"
re_verification:
  previous_status: human_needed
  previous_score: 87/88
  gaps_closed:
    - "G-04-1 (automated side): under ./gradlew bootTestRun the dev profile binds MYFEEDER_TYPESAFE_API_KEY, the real Jev base-url, the PT1M first sweep, the 5s/30s HTTP timeouts and the app name, and Jev reports configured"
    - "Prior human item 2 (footer layout and theme): UAT test 2 passed"
    - "Prior human item 3 (flagged prohibition, edits typed after the confirmation opens): UAT test 3 passed, so the partial enforcement was accepted"
    - "Prior human item 4 (backstop concurrency truth): UAT test 4 passed, now carried as an override"
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "The suite stays offline only by shell hygiene. The Gradle Test task passes the whole developer environment into the test JVM. The activation scan does not catch a direct load of the overlay (spring.config.import, @TestPropertySource(locations=...application-dev.yaml), spring.config.additional-location). suiteContextStaysOffline only runs after other contexts may already have started (04-REVIEW WR-01)"
    category: security
    reason: "Raised by the incremental code review. No test currently loads the overlay directly: a grep of src/test/java for application-dev, spring.config., SPRING_CONFIG_ and TestPropertySource finds nothing outside DevProfileConfigTest. .envrc exports only MYFEEDER_* names. Would be resolved by stripping SPRING_AI_TYPESAFE_*/SPRING_PROFILES_ACTIVE/SPRING_CONFIG_* in tasks.withType<Test> and widening ACTIVATION_TOKENS"
    evidence_status: "none provided (no reproducing test; current tree is clean)"
  - finding: "devOverlayResolvesEveryMainKeyToMainsValue checks only one direction. Keys that exist only in the test yaml (spring.ai.anthropic.api-key, spring.datasource.hikari.*, spring.flyway.connect-retries*) reach bootTestRun unreviewed (04-REVIEW WR-02)"
    category: other
    reason: "All current test-only keys are harmless for a dev run. A future offline-only test knob would slip into bootTestRun unnoticed. Would be resolved by an allow-list of suite-only keys"
    evidence_status: "none provided"
coincidental_reliance_items:
  - truth: "Without the dev profile (what every @SpringBootTest context sees) nothing changes: no TypeSafe key is bound even when MYFEEDER_TYPESAFE_API_KEY is set"
    reason: undeclared-precondition
    harden: "The no-profile probe removes the systemEnvironment source, so it proves the yaml side only. The real suite also depends on the shell not exporting SPRING_AI_TYPESAFE_API_KEY or SPRING_PROFILES_ACTIVE. CLAUDE.md states this but nothing enforces it before contexts start. Strip those variables in the Gradle Test task (see 04-REVIEW WR-01)"
behavior_unverified_items:
  - truth: "G-04-1: with MYFEEDER_TYPESAFE_API_KEY exported and ./gradlew bootTestRun (no SPRING_AI_TYPESAFE_* or MYFEEDER_INTEREST_* overrides), Jev is configured and the backlog sweep drains within about 3 minutes of startup"
    test: "Export the real MYFEEDER_TYPESAFE_API_KEY only, run ./gradlew bootTestRun, save a profile or a topic, then watch /api/interest/status, article_score, a feed refresh, Re-score unread and the poll logs"
    expected: "The startup log shows 'The following 1 profile is active: \"dev\"' and no 'TypeSafe Jev not configured' line. eligibleUnscored drains to 0 with SCORED rows (model jev-1.13.0, request id). New arrivals are scored without waiting for the sweep. Re-score resets and re-drains. No new feed errors"
    why_human: "The configured half is proven by the smoke log and DevProfileConfigTest. The drain half needs the billed TypeSafe API. The automated smoke deliberately used a fake key in cold start, so no Jev call was made under the dev profile"
human_verification:
  - test: "Live-key re-run of UAT test 1 under the documented procedure (04-05 human-check, as updated by 04-09). Export the real MYFEEDER_TYPESAFE_API_KEY with no SPRING_AI_TYPESAFE_* or MYFEEDER_INTEREST_* overrides, run ./gradlew bootTestRun, confirm the dev-profile startup line and the absence of the keyless TypeSafe INFO line, then save a profile or at least one topic. (1) Within about 3 minutes GET /api/interest/status shows eligibleUnscored falling to 0, and article_score rows appear with status SCORED, model jev-1.13.0 and a request id. (2) A feed refresh that brings new articles scores them without waiting for the sweep. (3) Interests -> Re-score unread shows a count; confirming resets and re-drains (not separately exercised in the first UAT run). (4) Poll logs show no new feed errors."
    expected: "Backlog drains through the sweep; new arrivals are scored via the ArticlesIngestedEvent hand-off; Re-score resets and re-drains; feeds keep errorCount 0"
    why_human: "Needs the live, billed TypeSafe API and a running app. The verifier never calls the billed API and does not start servers"
---

# Phase 4: Scoring Pipeline & Backfill Sweep Verification Report

**Phase Goal:** Every eligible article, including the existing unread backlog, is judged once by Jev in the background, and feed polling is never slowed or failed by it.
**Verified:** 2026-09-24T22:06:00Z
**Status:** human_needed
**Re-verification:** Yes. This follows UAT gap G-04-1 and gap-closure plan 04-09 (commits 28d64fb, 79492fe, 0ecce98, 15a36d2, 394237e). It also covers the review fixes committed after the first verification (00ca174 WR-01 rubric discard, e67b696 WR-02 JSON confirm body, 81dd8c7 WR-03 poll gate).

## Goal Achievement

### Plan 04-09 Must-Have Truths (full verification)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | With MYFEEDER_TYPESAFE_API_KEY exported, bootTestRun starts with the dev profile active and Jev configured. The startup log shows the dev profile line, the keyless TypeSafe INFO line is absent, and status reports configured true | ✓ VERIFIED | `TestMyfeederApplication.main` chains `.with(TestcontainersConfiguration.class).withAdditionalProfiles(DEV_PROFILE).run(args)` with `DEV_PROFILE = "dev"`. I read the smoke log on disk (`~/.cache/myfeeder-phase04/boottestrun-dev-smoke.09.log`, 17:50 local). Line 22 is `The following 1 profile is active: "dev"`. The thread prefix is `[myfeeder]`, which proves the overlay's `spring.application.name` loaded, since the test yaml has no app name. Tomcat started on 18089. There is no `TypeSafe Jev not configured` line. `TypeSafeConfig.typeSafeClient` always logs that line when `properties.getApiKey()` is blank, and `JevApiClientImpl.isConfigured()` is `hasText(properties.getApiKey())` on the same properties, so the absent line means configured. The status JSON itself is only in the SUMMARY. I did not re-run the smoke because the verifier does not start servers |
| 2 | Under the dev profile the environment resolves the key from MYFEEDER_TYPESAFE_API_KEY, base-url to https://api.typesafe.ai, sweep-initial-delay PT1M, http 5s/30s and app name myfeeder | ✓ VERIFIED | `DevProfileConfigTest.devProfileRestoresLiveMainSettings` runs Spring Boot's real ConfigData pipeline with `setAdditionalProfiles("dev")` on the test classpath. It asserts `PROBE_KEY.equals(api-key)` (boolean form), base-url equals `new TypeSafeProperties().getBaseUrl()`, PT1M/PT2M, 5s/30s and myfeeder. My own run passed 4/4 (22:02:35Z) |
| 3 | The test yaml plus the dev overlay resolve every main key to main's value; the overlay adds nothing beyond main except base-url | ✓ VERIFIED | `devOverlayResolvesEveryMainKeyToMainsValue` passes. I checked it by hand against the three files: main's keys that the test yaml lacks or differs on are exactly app name, http.clients x2, typesafe.api-key, raindrop.api-token and sweep-initial-delay, and the overlay supplies all six with main's raw values. Its only extra key is `spring.ai.typesafe.base-url`. The code logic makes the recorded mutation (dropping raindrop.api-token gives `"null"`, which is not equal to main's placeholder) fail as the SUMMARY states |
| 4 | Without the dev profile nothing changes: no key bound even with MYFEEDER_TYPESAFE_API_KEY set, base-url 127.0.0.1:9, sweep-initial-delay PT1H | ✓ VERIFIED (coincidental-reliance) | `withoutTheProfileTheSuiteConfigStaysOffline` injects `MYFEEDER_TYPESAFE_API_KEY` and asserts `hasText(api-key)` is false, loopback base-url and PT1H. It passes. The test yaml has no api-key placeholder. Advisory: the probe strips systemEnvironment, so the real suite also relies on the shell not exporting `SPRING_AI_TYPESAFE_*`/`SPRING_PROFILES_ACTIVE`. `.envrc` exports only `MYFEEDER_RAINDROP_API_TOKEN` and `MYFEEDER_TYPESAFE_API_KEY` (names checked, values not read) |
| 5 | No test source or resource activates the dev profile; the MyfeederApplicationTests context runs without it, isConfigured() false, loopback base-url | ✓ VERIFIED | `noTestActivatesTheDevProfile` passes, with positive controls. My grep of src/test and src/main for `spring.profiles`, `SPRING_PROFILES` and `ActiveProfiles` found only the read call in `suiteContextStaysOffline`. `build.gradle.kts` sets no profile. `application-dev.yaml` exists only in src/test/resources. `MyfeederApplicationTests.suiteContextStaysOffline` passes 3/3 in my full-suite run, and its XML output contains the keyless `TypeSafe Jev not configured` line |
| 6 | Both application.yaml files are unchanged; TypeSafeConfigTest, JevResilienceTest and HttpClientConfigurationTest mirror guards keep passing | ✓ VERIFIED | `git diff 4a2d5fa HEAD -- src/main/resources/application.yaml src/test/resources/application.yaml` gives 0 lines. In my full run TypeSafeConfigTest was 11/11, JevResilienceTest 16/16 and HttpClientConfigurationTest 2/2 |
| 7 | CLAUDE.md (Dev workflow line plus a bootTestRun gotcha) and the 04-05 human-check describe the working procedure; the user's uncommitted CLAUDE.md hunks are unchanged | ✓ VERIFIED | `15a36d2` touches only CLAUDE.md with 3 changed lines (the Dev workflow line replaced, the gotcha added). The working tree has both lines. `git diff -U0 -- CLAUDE.md` minus index lines equals `claude-md-user-hunks.09.before.diff`. The 04-05 PLAN has exactly one `<human-check>`, and it names the dev profile line and 04-09. `git log 4a2d5fa..HEAD` touches no `.envrc`, `.claude/CLAUDE.md` or `.planning/config.json`, and all three are still modified and uncommitted |

### G-04-1 Gap Truth (from 04-UAT.md)

| Truth | Status | Evidence |
|-------|--------|----------|
| With MYFEEDER_TYPESAFE_API_KEY set and `./gradlew bootTestRun`, Jev is configured and the backlog sweep drains within about 3 minutes of startup | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | The config half is closed (truths 1-2 above). The overlay supplies exactly the three overrides that UAT test 1 used to get a live drain (key, base-url, initial delay), plus the timeouts. The drain under the new procedure with a real key has not been run: the smoke used a fake key in cold start by design. Routed to human verification |

### Roadmap Success Criteria (regression check)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | New eligible articles get exactly one stored judgment, newest first | ✓ VERIFIED | No regression. ArticleScoringFlowTest 3/3, ArticleScoreStoreTest 18/18, ScoringIsolationTest 5/5 and FeedPollingServiceTest 6/6 all pass in my full run. The post-verification WR-01 fix (`sameRubric` re-read after `judge()`, writes nothing when the rubric changed) keeps the one-judgment rule. ArticleScoringServiceTest is 17/17 (up from 14, adding the rubric-change tests) |
| 2 | Jev slow, failing or unconfigured never changes poll duration or feed errors; cold start scores nothing | ✓ VERIFIED | ScoringIsolationTest 5/5 (30s-blocked scorer, poll under 5s, errorCount 0). Cold-start tests pass. Scoring code is unchanged by 04-09 |
| 3 | The sweep drains the backlog on launch, late key, outage and first profile; attempt rules; GUID skip | ✓ VERIFIED | InterestScoringSweepTest 8/8, JevResilienceTest 16/16, ArticleScoringServiceTest 17/17, `sweepIsScheduledWithConfiguredDelays` (PT2M/PT1H). The live drain was observed once in UAT test 1 (30 to 0 SCORED rows, jev-1.13.0, 30/30 request ids) |
| 4 | `/api/interest/status` reports configured, breaker, eligibleUnscored and failed | ✓ VERIFIED | InterestApiIntegrationTest 8/8, InterestStatusServiceTest 5/5 |
| 5 | Re-score unread shows the count before confirming, then the sweep re-scores | ✓ VERIFIED | The post-verification WR-02 fix makes POST `/rescore` require a JSON body (`consumes = application/json`, `RescoreRequest(boolean confirm)`, false or missing gives 400). The SPA sends `{ confirm: true }` (`api/interest.ts:63`). InterestRescoreControllerTest 5/5, InterestRescoreApiIntegrationTest 1/1, InterestRescoreServiceTest 5/5. WR-03 gates the poll on configured, not cold start and eligibleUnscored > 0. Frontend vitest 119/119 and `npx tsc -b` exit 0 (both my runs). Visual check passed in UAT test 2 |

### Plan 04-01..04-08 Truths (regression)

All 83 plan truths from the first verification still hold. There are no regressions in the full suite, and the only production code changed since then is the three review fixes above, each covered by tests. The one backstop truth (04-02 concurrent writes) was accepted in UAT test 4 and is carried as a PASSED (override). The flagged 04-07 prohibition (edits typed after the confirmation opens) was accepted in UAT test 3.

**Score:** 95/96 truths verified (1 via override). 1 is present but behavior-unverified: the G-04-1 live drain under the new procedure.

### Prohibitions (plan 04-09, all test-tier)

| Prohibition | Disposition |
|-------------|-------------|
| MUST NOT modify either application.yaml | Enforced. The git diff since 4a2d5fa is empty, and the mirror guards pass |
| MUST NOT let any test activate the dev profile or bind a real key | Enforced. `noTestActivatesTheDevProfile`, `withoutTheProfileTheSuiteConfigStaysOffline` and `suiteContextStaysOffline` all pass. Scope limits are noted as advisory (review WR-01) |
| MUST NOT stage or commit .envrc, .claude/CLAUDE.md, .planning/config.json or the user's CLAUDE.md hunks | Enforced. No commit since base touches them, and the user hunks are byte-identical |

### Advisory (New Scope, Unevidenced)

| # | Finding | Category | Why Advisory |
|---|---------|----------|--------------|
| 1 | The offline barrier relies on shell hygiene, and the activation scan misses direct overlay loads (04-REVIEW WR-01) | security | New scope with no reproducing test. The current tree has no direct overlay load |
| 2 | The drift guard is one-directional, so test-only yaml keys reach bootTestRun unreviewed (04-REVIEW WR-02) | other | New scope. The current test-only keys are harmless |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/test/resources/application-dev.yaml` | Dev overlay restoring main's live settings | ✓ VERIFIED | 6 main keys plus base-url; `sweep-initial-delay: PT1M` present; loaded under dev (probe asserts the property source name) |
| `src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java` | bootTestRun entry that activates dev | ✓ VERIFIED | `withAdditionalProfiles(DEV_PROFILE)`; used by the Spring Boot plugin's bootTestRun and referenced by the tests |
| `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java` | Docker-free probes, mirror rule, no-activation scan | ✓ VERIFIED | 4 tests, all passing; no Spring test annotations |
| `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java` | Runtime offline guard | ✓ VERIFIED | `suiteContextStaysOffline` passes; the message avoids the scan tokens |
| Phase 04 production artifacts (01-08) | Scoring pipeline | ✓ VERIFIED | Unchanged except the review fixes; see regression tables |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| TestMyfeederApplication | application-dev.yaml | `withAdditionalProfiles(DEV_PROFILE)` loads profile-specific ConfigData | ✓ WIRED (smoke log profile line plus app-name prefix; the probe proves the same mechanism through `setAdditionalProfiles`) |
| application-dev.yaml | JevApiClientImpl | `${MYFEEDER_TYPESAFE_API_KEY:}` feeds TypeSafeProperties, which `isConfigured()` reads | ✓ WIRED (probe key check; the smoke log has no keyless line) |
| DevProfileConfigTest | src/main/resources/application.yaml | Loaded from disk; every main key is compared | ✓ WIRED |
| Earlier phase links (FeedPollingService to listener to queue to scorer to store; sweep; status; rescore; dialog) | - | - | ✓ WIRED (regression: suite green) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| bootTestRun Jev config | api-key, base-url, initial delay | Shell env var through the dev overlay placeholder | Yes (the probe resolves an injected env value; the smoke is configured with an exported key) | ✓ FLOWING |
| `/api/interest/status`, Re-score count, waiting line, article_score rows | - | Same as the first verification | Yes | ✓ FLOWING (no change) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Dev and no-profile config resolution, mirror rule, activation scan | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.DevProfileConfigTest'` | 4 tests, 0 failures, 0 errors (22:02:35Z) | ✓ PASS |
| Full backend suite (run once) | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall` | exit 0; 58 result files, 394 tests, 0 failures, 0 errors, 2 skipped (env-gated live tests). This matches the orchestrator's 17:55 run | ✓ PASS |
| Frontend suite | `npx vitest run` | 17 files, 119/119 passed | ✓ PASS |
| Frontend type-check | `npx tsc -b` | exit 0 | ✓ PASS |
| bootTestRun smoke (dev profile, fake key) | Not re-run (the verifier does not start servers); I inspected the executor's log on disk | Profile line present, `[myfeeder]` app name, port 18089, no keyless TypeSafe line | ✓ PASS (log evidence) |

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh` scripts, and none exist under `scripts/*/tests/`.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| SCOR-01 | 04-03, 04-04, 04-09 | One Jev call per new article; profile score plus a noul per topic | ✓ SATISFIED | SC1. 04-09 makes the local live path usable |
| SCOR-02 | 04-04 | Scoring never blocks or fails polling | ✓ SATISFIED | SC2 |
| SCOR-03 | 04-02, 04-03 | Raw outputs stored write-once | ✓ SATISFIED | `writeScored`, `scoredIsWriteOnce`; WR-01 discard keeps write-once |
| SCOR-04 | 04-02, 04-04, 04-05 | Unread, within 14 days, newest first | ✓ SATISFIED | ELIGIBLE/NEWEST_FIRST |
| SCOR-05 | 04-01, 04-05, 04-09 | Background sweep: backfill, outage, late key, first profile | ✓ SATISFIED (the live drain under bootTestRun is a human item) | SC3; dev overlay restores PT1M (D-10) under bootTestRun |
| SCOR-06 | 04-03, 04-05 | Nothing scored in cold start | ✓ SATISFIED | Cold-start gates; smoke started in cold start |
| SCOR-07 | 04-01, 04-02, 04-03 | Permanent failures at most 3 attempts; transient failures use none | ✓ SATISFIED | MAX_ATTEMPTS, ScoringFailure |
| SCOR-08 | 04-02, 04-03 | GUID-less articles skipped | ✓ SATISFIED | `blankGuidIsSkippedWithoutACall` |
| INT-05 | 04-02, 04-07, 04-08 | Re-score unread with count | ✓ SATISFIED | SC5, WR-02 JSON confirm |
| JEV-05 | 04-02, 04-06, 04-07 | Status reports configured, breaker and counts | ✓ SATISFIED | SC4 |

All 10 phase IDs appear in at least one plan's `requirements`. REQUIREMENTS.md maps exactly these 10 IDs to Phase 4, so none are orphaned.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| 04-09 files and review-fix files | - | TBD/FIXME/XXX/TODO/HACK | none found | - |
| DevProfileConfigTest.java | 40-42 | Activation scan omits `spring.config.`/`application-dev` load paths (review WR-01) | ⚠️ Warning (advisory) | A future test could load the live overlay without tripping the guard |
| DevProfileConfigTest.java | 78-96 | One-directional drift check (review WR-02) | ⚠️ Warning (advisory) | Suite-only keys can silently reach bootTestRun |
| DevProfileConfigTest.java | resolve() | Boots a full SpringApplication in the shared test JVM (review IN-03) | ℹ️ Info | Re-initializes logging; the suite is still green |
| DevProfileConfigTest.java | loadYaml | Reads only the first YAML document (review IN-04) | ℹ️ Info | A future multi-document main yaml would be partly unchecked |
| ArticleScoringService.java | 92-105 | Non-atomic re-read then write (review IN-01); topic add or delete discards a billed result (IN-02) | ℹ️ Info | Negligible window; intentional and tested |

### Human Verification Required

### 1. Live-key re-run of UAT test 1 (G-04-1 closure)

**Test:** Export only the real `MYFEEDER_TYPESAFE_API_KEY`, with no `SPRING_AI_TYPESAFE_*` or `MYFEEDER_INTEREST_*` overrides, and run `./gradlew bootTestRun`. Confirm the startup log shows `The following 1 profile is active: "dev"` and no `TypeSafe Jev not configured` line. Save a profile or a topic. Watch `/api/interest/status` and `article_score`, refresh a feed, run Re-score unread, and check the poll logs.
**Expected:** Within about 3 minutes eligibleUnscored drains to 0 with SCORED rows (jev-1.13.0, request id). New arrivals are scored without waiting for the sweep. Re-score resets and re-drains (not separately exercised in the first run). There are no new feed errors.
**Why human:** Needs the billed TypeSafe API and a running app. The automated smoke deliberately made no Jev call.

### Gaps Summary

There are no blocking gaps. G-04-1 is closed as far as automation can prove:
- `bootTestRun` now activates a `dev` overlay that binds the key, the real base-url, the PT1M first sweep, the HTTP timeouts and the app name. The executor's smoke log and a real ConfigData probe that I re-ran both confirm it.
- The suite stays offline. Neither application.yaml changed, `suiteContextStaysOffline` passes, and the no-profile probe binds no key.
- The documented procedure (CLAUDE.md, the 04-05 human-check) matches the code.
- The full backend suite (394, 0 failures) and the frontend suite (119) are green in my own runs.

Prior human items 2-4 were resolved in UAT.

The status is `human_needed` because of the one billed live-key re-run of UAT test 1. The two review warnings (WR-01 offline barrier not enforced at the Gradle test-task boundary, WR-02 one-directional drift guard) are advisory hardening. A `/gsd-quick` pass is worth doing, but neither blocks the phase goal.

---

_Verified: 2026-09-24T22:06:00Z_
_Verifier: Claude (gsd-verifier)_
