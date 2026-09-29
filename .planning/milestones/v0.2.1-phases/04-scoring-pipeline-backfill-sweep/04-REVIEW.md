---
phase: 04-scoring-pipeline-backfill-sweep
reviewed: 2026-09-24T22:00:17Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - CLAUDE.md
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java
  - src/main/java/org/bartram/myfeeder/controller/RescoreRequest.java
  - src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java
  - src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java
  - src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java
  - src/test/java/org/bartram/myfeeder/TestMyfeederApplication.java
  - src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java
  - src/test/resources/application-dev.yaml
findings:
  critical: 0
  warning: 2
  info: 4
  total: 6
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-09-24T22:00:17Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

This is an incremental review of the changes since `648c7a4`: the 04-REVIEW-FIX fixes (WR-01 rubric-change discard, WR-02 JSON confirm body on `POST /api/interest/rescore`, WR-03 status-poll gate) and gap-closure plan 04-09 (a `dev` profile overlay for `bootTestRun`, plus drift and offline guards). For `CLAUDE.md`, only the two committed hunks were reviewed. The working-tree edits are out of scope.

What I checked and found correct:
- **Rubric check (`ArticleScoringService`)**: the versions are primitive `int`, so `==` is correct. Topic-version maps are compared by id. Name- and weight-only edits do not bump versions, so they are correctly ignored. An exception thrown during the re-read goes to `ScoringQueue.run`, which logs it and releases the in-flight id.
- **Rescore endpoint**: with `consumes = application/json`, a body-less, form or text POST gets 415 before `service.rescore()` runs. A JSON `null` body or a missing `confirm` gets 400, either through `HttpMessageNotReadableException` or through the `IllegalArgumentException` handler. The app has no CORS configuration, so the preflight claim in `RescoreRequest` holds. `apiPost` sets `Content-Type: application/json` when a body is passed.
- **Poll gate**: `useInterestStatus` uses exactly the same condition as the "N waiting" line in `InterestsDialog.tsx:205-206`.
- **Dev overlay**: `application-dev.yaml` plus the test yaml resolve every main key to main's value (checked by hand against both files). The `CLAUDE.md` gotcha text matches the files.

No blockers. The two warnings are both about the billed-API safety net and the config-drift guard that 04-09 added. Each protects only against the exact failure it was written for, not the wider class it is meant to stop.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Keeping the suite offline relies on shell hygiene, and the activation scan misses ways to load the live overlay directly

**File:** `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java:40-42`, `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java:78-87`, `build.gradle.kts:97-102` (the unguarded test task)
**Issue:** The documented dev workflow (the new `CLAUDE.md` line 83) is to export `MYFEEDER_TYPESAFE_API_KEY` in the shell. The uncommitted `.envrc` now does this. The Gradle `Test` task passes the developer's whole environment to the test JVM. So the only things that keep `./gradlew test` from making billed Jev calls are:
1. A source-token scan (`ACTIVATION_TOKENS`) that only looks for ways to *activate a profile*. It does not catch a test that loads the overlay file directly, for example `@TestPropertySource(locations = "classpath:application-dev.yaml")`, `spring.config.import=classpath:application-dev.yaml` or `spring.config.additional-location`. Any of these binds `${MYFEEDER_TYPESAFE_API_KEY:}` and the real base-url without activating `dev`. (`"spring.config."` is not in the token list. `"spring.profiles."` does not match it.)
2. A rule in `CLAUDE.md` saying never to export `SPRING_PROFILES_ACTIVE=dev` or `SPRING_AI_TYPESAFE_*`.
3. `suiteContextStaysOffline`, which only fires after contexts have started. It only inspects its own context, and it only runs when JUnit reaches that test. Before that, other `@SpringBootTest` contexts may already have started with a live key. `FeedPollingScheduler` polls feeds at `ApplicationReadyEvent`, and `submitIngested` then scores the new articles right away, whatever the PT1H sweep delay is.

The test JVM does not need any of these variables, so the barrier can be enforced rather than advised.
**Fix:** Strip the variables at the test-task boundary and widen the scan:
```kotlin
// build.gradle.kts
tasks.withType<Test> {
    useJUnitPlatform()
    // The suite is offline by design: never inherit a live Jev key or an active profile from the shell
    environment.keys.removeAll { it == "MYFEEDER_TYPESAFE_API_KEY" || it == "SPRING_PROFILES_ACTIVE"
        || it.startsWith("SPRING_AI_TYPESAFE_") || it.startsWith("SPRING_CONFIG_") }
    ...
}
```
```java
// DevProfileConfigTest
private static final List<String> ACTIVATION_TOKENS = List.of("withAdditionalProfiles",
        "setAdditionalProfiles", "@ActiveProfiles", "setActiveProfiles", "addActiveProfile",
        "setDefaultProfiles", "spring.profiles.", "SPRING_PROFILES",
        "application-dev", "spring.config.", "SPRING_CONFIG_");
```

### WR-02: The drift guard only checks one direction, so settings that exist only in the test yaml still reach `bootTestRun`

**File:** `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java:78-96`
**Issue:** `devOverlayResolvesEveryMainKeyToMainsValue` checks that every key in *main* resolves to main's value. For extra keys, it only checks the ones that exist only in the *dev overlay*. It never looks at keys that exist only in the *test* yaml. Those are loaded by `bootTestRun` too, and nothing overrides them. There are already four: `spring.ai.anthropic.api-key: test-dummy-key`, `spring.datasource.hikari.*`, `spring.flyway.connect-retries*`, and `spring.ai.typesafe.base-url`, which the overlay happens to override. They are harmless today. But G-04-1 was exactly this class of bug: `bootTestRun` silently running with suite-only offline settings. If someone later adds an offline-only knob to `src/test/resources/application.yaml` that is not in main, `bootTestRun` inherits it and this test still passes.
**Fix:** Make every test-only key an explicit, reviewed decision:
```java
// Test-only keys that are allowed to leak into bootTestRun; anything else must be overridden by the dev overlay.
private static final Set<String> SUITE_ONLY_KEYS_ALLOWED_IN_DEV = Set.of(
        "spring.ai.anthropic.api-key",
        "spring.datasource.hikari.connection-timeout", "spring.datasource.hikari.initialization-fail-timeout",
        "spring.flyway.connect-retries", "spring.flyway.connect-retries-interval");
...
EnumerablePropertySource<?> test = (EnumerablePropertySource<?>) loadYaml("src/test/resources/application.yaml");
for (String name : test.getPropertyNames()) {
    if (!main.containsProperty(name) && !dev.containsProperty(name)) {
        assertThat(SUITE_ONLY_KEYS_ALLOWED_IN_DEV).as(name).contains(name);
    }
}
```

## Info

### IN-01: A small check-then-write window remains between the rubric re-read and `writeScored`

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java:92-105`
**Issue:** The re-read (line 92) and the write (line 105) are separate, non-atomic steps. A rubric save that commits between them still writes a SCORED row stamped with the old versions. In practice this is negligible. The window is milliseconds, and the scenario the fix targets (save, then Re-score, while a ~90s call is in flight) is stopped by human latency: Re-score needs a confirmation dialog. It is noted only so that the `CLAUDE.md` rule ("if any changed, it stores nothing") is not read as an atomic guarantee.
**Fix:** If an atomic guarantee is ever needed, make the parent insert in `ArticleScoreStore.writeScored` conditional, for example `... FROM article a WHERE a.id = :articleId AND EXISTS (SELECT 1 FROM interest_profile p WHERE p.id = 1 AND p.version = :profileVersion)`, and check the topic versions inside the same transaction.

### IN-02: Adding or deleting a topic mid-call throws away a valid, billed judgment

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java:144-156`
**Issue:** `sameRubric` compares the full id→version *set*. If a topic is deleted mid-call, the result is discarded and the article is re-judged, which is one more billed call. Yet the profile score and the remaining topics' nouls are still valid, and `ArticleScoreStore.writeScored` (lines 136-144, Javadoc lines 107-108) already drops deleted topics on purpose. That tolerance is now effectively dead code. An added topic is similar: every article scored before the addition also lacks that topic's noul until Re-score, so discarding only the in-flight article buys no consistency. The behaviour is tested and intentional (`topicEditedAddedOrDeletedMidCallDiscardsTheResultForTheSweep`), so this is a cost note, not a defect.
**Fix:** Optional: compare only the topics that were sent. Discard when a sent topic's version changed, or when the profile version changed. Otherwise store the row, which already skips deleted topics. Alternatively, update the `writeScored` Javadoc to say the scorer no longer relies on the deleted-topic tolerance.

### IN-03: `DevProfileConfigTest` boots a full `SpringApplication` inside the shared test JVM

**File:** `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java:330-347`
**Issue:** `resolve()` runs the whole application lifecycle. That includes `LoggingApplicationListener`, which re-initializes the logging system and sets `PID`/`LOG_*` system properties. It does this in the same JVM that holds cached `@SpringBootTest` contexts. It also runs every `EnvironmentPostProcessor` and `ApplicationListener` on the test classpath. The test only needs the ConfigData result.
**Fix:** Resolve the environment directly, without starting an application:
```java
StandardEnvironment env = new StandardEnvironment();
// ...remove system env, add probe source as today...
ConfigDataEnvironmentPostProcessor.applyTo(env, new DefaultResourceLoader(), null, List.of(profiles));
return env;
```

### IN-04: The drift check reads only the first YAML document

**File:** `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java:326-328`
**Issue:** `loadYaml(...).getFirst()` ignores any later `---` documents. If main `application.yaml` later adds a profile-activated document (`spring.config.activate.on-profile`), the drift and "no `spring.profiles.*`" checks skip it without any warning.
**Fix:** Assert that each file has exactly one document (`assertThat(loader.load(path, resource)).hasSize(1)`), or iterate over all returned sources.

---

_Reviewed: 2026-09-24T22:00:17Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
