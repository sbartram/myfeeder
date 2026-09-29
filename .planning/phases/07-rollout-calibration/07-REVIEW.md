---
phase: 07-rollout-calibration
reviewed: 2026-09-28T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - CLAUDE.md
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestBadge.test.tsx
  - src/main/frontend/src/components/InterestBadge.tsx
  - src/main/frontend/src/hooks/useInterest.test.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/utils/interest.test.ts
  - src/main/frontend/src/utils/interest.ts
  - src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/TierThresholds.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
  - src/test/resources/application-dev.yaml
  - src/test/resources/application.yaml
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-09-28
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Scope: changes since `52b96d9^`. That covers served tier thresholds (backend record, properties, status endpoint, `useInterestTiers`, `TierContext`, `InterestBadge`), the Jev retry and breaker event logger, the read-only calibration replay (shell driver, SQL and drift-guard test), the calibrated yaml constants and the CLAUDE.md operations docs.

I found no security problems or data-loss risks. The replay driver validates every value it interpolates into psql, and the session is forced read-only. The frontend tier plumbing is correct. The observer that never goes stale does not add polling, because `refetchInterval` applies per observer. The `select` result and `DEFAULT_TIERS` keep stable references, and every `InterestBadge` renders under the MainLayout provider.

Main concerns:
1. `JevEventLogging` logs `-1.0%` rates on two of its three transitions. I reproduced this against resilience4j 2.3.0. These lines are the rollout evidence channel.
2. `InterestCalibrationReplaySqlTest` uses `contains()` against an SQL file that holds three copies of the unread blend and four copies of the badge expression. Only one copy has to match, so drift in the other copies passes.
3. Nothing checks the tier threshold invariant (`0 <= neutral <= high <= 100`), on the server or in the replay driver.

## Warnings

### WR-01: Breaker transition log prints "-1.0%" failure and slow-call rates for OPEN_TO_HALF_OPEN and HALF_OPEN_TO_CLOSED

**File:** `src/main/java/org/bartram/myfeeder/config/JevEventLogging.java:385-388`
**Issue:** The state-transition consumer reads `breaker.getMetrics()` after the transition has happened. In resilience4j, entering HALF_OPEN or CLOSED creates a new, empty `CircuitBreakerMetrics`, and an empty metrics object returns `-1.0` for both rates. I checked this directly against `resilience4j-circuitbreaker-2.3.0`:

```
CLOSED_TO_OPEN fr=100.0 sr=0.0
OPEN_TO_HALF_OPEN fr=-1.0 sr=-1.0
HALF_OPEN_TO_OPEN fr=50.0 sr=0.0
HALF_OPEN_TO_CLOSED fr=-1.0 sr=-1.0
```

As a result, prod logs `Jev circuit breaker HALF_OPEN_TO_CLOSED (failure rate -1.0%, slow-call rate -1.0%)`. These lines are documented as the D-05/D-06 launch evidence channel, which the CLAUDE.md "Jev event log lines" bullet and the 07-07 rollout watch grep. A negative percentage is misleading there. `breakerTransitionsAreLogged` checks only the transition name, so the test never saw this.
**Fix:** Only print rates when they are meaningful. Print them on transitions into OPEN, where resilience4j carries the previous window's metrics over, and never print a negative value:

```java
breaker.getEventPublisher().onStateTransition(e -> {
    CircuitBreaker.StateTransition t = e.getStateTransition();
    float fr = breaker.getMetrics().getFailureRate();
    float sr = breaker.getMetrics().getSlowCallRate();
    if (fr < 0 || sr < 0) {
        log.warn("Jev circuit breaker {}", t.name());
    } else {
        log.warn("Jev circuit breaker {} (failure rate {}%, slow-call rate {}%)", t.name(), fr, sr);
    }
});
```

Add an assertion to `breakerTransitionsAreLogged` that the output does not contain `-1.0%`, and update the CLAUDE.md log-line description to match.

### WR-02: Replay drift guard passes when only one of several blend and badge copies matches the app

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:35-40, 57-59`
**Issue:** The SQL file repeats the unread blend three times (the `summary`, `top` and `bottom` statements) and the `INTEREST_SCORE` expression four times. `grep` shows 3 occurrences of `a."read" = false` and 4 of the badge CASE. `assertThat(sql).contains(...)` only proves that at least one copy is identical. If someone hand-edits the `top` or `bottom` copy, the test still passes, which breaks the stated guarantee: "InterestCalibrationReplaySqlTest fails on any drift" (`interest-calibration-replay.sql:89`, and the "drift-guarded" claim in CLAUDE.md). Calibration decisions such as the shipped 100/70/22 depend on those rows matching what the app serves.
**Fix:** Assert the exact number of verbatim copies. Better still, assert that every `WITH learned AS` line is one of the allowed expected texts:

```java
private static int count(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
    return n;
}

@Test
void everyBlendCopyIsVerbatim() throws IOException {
    String sql = Files.readString(SQL);
    Set<String> allowed = Set.of(
            InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE),
            InterestScoreQueries.blendCte(WINDOW_SCOPE));
    List<String> withLines = sql.lines().filter(l -> l.startsWith("WITH learned AS")).toList();
    assertThat(withLines).hasSize(5);
    for (String line : withLines) {
        assertThat(allowed.stream().anyMatch(line::startsWith)
                || line.startsWith(InterestScoreQueries.LEARNED_CTE + " SELECT 'learned'"))
                .as(line).isTrue();
    }
    assertThat(count(sql, InterestScoreQueries.INTEREST_SCORE)).isEqualTo(4);
}
```

### WR-03: Tier thresholds are not validated anywhere, on the server or in the replay driver

**File:** `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java:63-69`, `src/main/java/org/bartram/myfeeder/service/TierThresholds.java:8`, `scripts/interest-calibration-replay.sh:32-37`
**Issue:** `myfeeder.interest.blend.tiers.high|neutral` goes straight into `/api/interest/status` and then into `tierOf`. Nothing enforces `0 <= neutral <= high <= 100`. A typo such as `neutral: 220`, or a swapped pair such as `high: 22, neutral: 70`, starts cleanly and silently mis-colors every badge. With a swapped pair, the neutral tier becomes unreachable and every score from 22 up renders high. CLAUDE.md documents env-var levers such as `kubectl set env`, and Spring relaxed binding accepts `MYFEEDER_INTEREST_BLEND_TIERS_NEUTRAL`, so a bad value can arrive without a code review. The replay driver has the same gap: its regex accepts `100:22:70` and writes a TSV whose `high_pct` and `neutral_pct` are meaningless, and that TSV then feeds the calibration record.
**Fix:** Validate in the record's compact constructor, so both the status service and any future caller fail fast, and build it once at startup:

```java
public record TierThresholds(int high, int neutral) {
    public TierThresholds {
        if (neutral < 0 || high > 100 || neutral > high) {
            throw new IllegalStateException(
                    "myfeeder.interest.blend.tiers must satisfy 0 <= neutral <= high <= 100");
        }
    }
}
```

Alternatively, add `@Validated` and a `@AssertTrue` method on `Tiers`. In the driver, after `IFS=: read -r pp high neutral`, add `(( neutral <= high && high <= 100 )) || { echo "invalid candidate: $candidate" >&2; exit 2; }`, and move it into the pre-connection validation loop.

## Info

### IN-01: Client fallback tiers (70/40) disagree with the shipped tiers (70/22)

**File:** `src/main/frontend/src/utils/interest.ts:32`, `src/main/frontend/src/hooks/useInterest.ts:45`
**Issue:** Prod serves `neutral: 22` (`application.yaml:496`). Until `/api/interest/status` resolves, and permanently if it errors until a reload or a focus refetch, every badge scored 22-39 renders `tier-low` instead of `tier-neutral`. This is a visible color flash on first paint of every list.
**Fix:** Either accept this and document it as a known flash, or remove it: persist the last served tiers (for example as `initialData` from a `localStorage` copy written in `select`'s consumer), or render badges uncolored (`tier-pending`) until tiers load.

### IN-02: `window-summary` reuses the `scored_unread` column label for a read-and-unread population

**File:** `scripts/interest-calibration-replay.sql:149`
**Issue:** The window cross-check counts every SCORED article in the window, read or unread, but its column is still named `scored_unread`. Anyone comparing the two TSV rows could misread the population.
**Fix:** Alias it `scored_in_window` in the `window-summary` statement. This is outside the verbatim blend line, so the drift guard is unaffected.

### IN-03: Driver error text and defaults for non-candidate values

**File:** `scripts/interest-calibration-replay.sh:27-47`
**Issue:** An invalid `LEARN_RATE`, `LEARNED_CAP` or `WINDOW_DAYS` is reported as `invalid candidate: <value>`, which points the operator at the wrong input. The defaults (2 / 20 / 14) are copied from `application.yaml` and will silently diverge if the app values are tuned later.
**Fix:** Name the variable in the message (`echo "invalid LEARN_RATE: $LEARN_RATE"`). Add a comment, or a drift test assertion, tying the defaults to `myfeeder.interest.blend.learn-rate`, `learned-cap` and `window-days`.

### IN-04: Replay sections read different snapshots of a live database

**File:** `scripts/interest-calibration-replay.sql:110-171`
**Issue:** psql runs each statement in its own autocommit transaction while the app keeps scoring and ingesting. So `summary`, `top`, `bottom`, `window-summary`, `votes` and `learned` can each reflect a different population, and `votes` and `learned` may not match the blend that `summary` used.
**Fix:** Wrap the file in `BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;` … `COMMIT;`. Neither keyword trips `WRITE_KEYWORD`.

### IN-05: Breaker recovery transitions are logged at WARN, and the logger is a `@Configuration` with no bean methods

**File:** `src/main/java/org/bartram/myfeeder/config/JevEventLogging.java:370-389`
**Issue:** `OPEN_TO_HALF_OPEN` and `HALF_OPEN_TO_CLOSED` are normal recovery steps but are logged at WARN, the same level as a trip, which adds noise to the WARN grep. The class is a `@Configuration(proxyBeanMethods = false)` whose only purpose is a side effect in its constructor. `@Component` states that intent more directly.
**Fix:** Log transitions whose target is `CLOSED` or `HALF_OPEN` at INFO. Consider `@Component`.

### IN-06: CLAUDE.md misattributes the test-yaml tier pin

**File:** `CLAUDE.md:166` (the "Tier thresholds and tuning" bullet)
**Issue:** It says to keep the test yaml at "profile-points 100 / tiers 70 / 40, because `InterestScoreQueriesTest` fixtures assume them". Tiers are never read by `InterestScoreQueries`. The 70/40 pin is asserted by `InterestApiIntegrationTest.statusServesTheConfiguredTierThresholds`, and only profile-points is a fixture assumption of `InterestScoreQueriesTest`.
**Fix:** Change the sentence to "profile-points 100 (`InterestScoreQueriesTest` fixtures) and tiers 70/40 (`InterestApiIntegrationTest`)".

---

_Reviewed: 2026-09-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
