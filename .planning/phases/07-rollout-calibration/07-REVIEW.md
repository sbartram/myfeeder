---
phase: 07-rollout-calibration
reviewed: 2026-09-28T00:00:00Z
re_reviewed: 2026-09-29T02:27:29Z
re_review_scope:
  diff_base: e0baa69ce321d1775d097b2f619d2c581a4531de
  head: 91ee2a9
  plan: 07-10
  files:
    - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
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
  info: 9
  total: 12
  resolved: 1
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-09-28 (full phase); re-reviewed 2026-09-29T02:27:29Z (incremental, plan 07-10)
**Depth:** standard
**Files Reviewed:** 24 (re-review: 1)
**Status:** issues_found

## Summary

Scope: changes since `52b96d9^`. That covers served tier thresholds (backend record, properties, status endpoint, `useInterestTiers`, `TierContext`, `InterestBadge`), the Jev retry and breaker event logger, the read-only calibration replay (shell driver, SQL and drift-guard test), the calibrated yaml constants and the CLAUDE.md operations docs.

I found no security problems or data-loss risks. The replay driver validates every value it interpolates into psql, and the session is forced read-only. The frontend tier plumbing is correct. The observer that never goes stale does not add polling, because `refetchInterval` applies per observer. The `select` result and `DEFAULT_TIERS` keep stable references, and every `InterestBadge` renders under the MainLayout provider.

Main concerns:
1. `JevEventLogging` logs `-1.0%` rates on two of its three transitions. I reproduced this against resilience4j 2.3.0. These lines are the rollout evidence channel.
2. `InterestCalibrationReplaySqlTest` uses `contains()` against an SQL file that holds three copies of the unread blend and four copies of the badge expression. Only one copy has to match, so drift in the other copies passes. **(Resolved by 07-10, see WR-02 under Resolved.)**
3. Nothing checks the tier threshold invariant (`0 <= neutral <= high <= 100`), on the server or in the replay driver.

### Incremental re-review (plan 07-10, `e0baa69..91ee2a9`)

Scope: `InterestCalibrationReplaySqlTest.java` only. `scripts/interest-calibration-replay.sql` and `InterestScoreQueries.java` are unchanged since `e0baa69` (`git diff --stat` is empty).

What I verified:
- **WR-02 is resolved.** `assertEveryCopyIsVerbatim` (lines 167-183) labels every column-0 `WITH learned AS` line by exact equality and requires `unread, unread, unread, window, learned`. I checked this against the SQL: lines 30, 52 and 58 are `blendCte(UNREAD_SCOPE)`, line 65 is `blendCte(WINDOW_SCOPE)`, and line 90 starts with `LEARNED_CTE + " SELECT 'learned' AS section"`. The line-4 header comment starts with `--`, so it is not counted. `INTEREST_SCORE` appears on lines 31, 53, 59 and 66 and nowhere in `blendCte`, so the exact count of 4 is right. I ran the class through Gradle: 10 tests, 0 failures, 0 errors.
- **No vacuous pass for the nine copies.** Each drift test first asserts that the unmodified file passes and that the needle occurs the expected number of times (3/1/1 blend, 4 badge). Only then does it mutate the middle character of each occurrence. `driftOneByte` always writes a character that differs from the one it replaces. Blend lines are compared by equality, and the badge is counted as an exact substring, so each of the nine mutations must throw. `assertThatCode(...).isInstanceOf(AssertionError.class)` fails when nothing is thrown.
- **The six pre-existing tests are unchanged.** The diff since `e0baa69` only adds lines (imports, `LEARNED_SECTION`, four tests, four helpers) plus one Javadoc sentence.
- **No disk, database or network access from the new tests.** Every variant is an in-memory string. The two driver tests are pre-existing, and the driver exits on validation before `mkdir "$OUT_DIR"` or `psql`.
- **Comment stripping.** `withoutComments` drops only lines whose stripped text starts with `--`. Over-stripping (a `--` line inside a multi-line string literal) cannot happen in this file, which has no multi-line literals. Under-stripping is real: trailing `--` comments and `/* */` block comments survive. So the Javadoc claim that "a copy pasted into a comment cannot hide a drifted statement copy" is false (WR-04).

I confirmed the new findings with an in-memory probe that reuses the guard's exact logic against the real SQL file:

```
baseline passes: true
drifted top badge + trailing -- comment copy passes: true
drifted top badge + block comment copy passes: true
extra indented drifted blend statement passes: true
extra lowercase drifted blend statement passes: true
indented \! passes replayIsReadOnly sql checks: true
mid-line \g file passes replayIsReadOnly sql checks: true
```

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

### WR-04: The badge guard counts copies without checking where they are, and its comment filter only removes whole-line `--` comments, so a drifted badge copy can still pass

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:163-165, 180-182, 186-190`
**Issue:** The badge check is `occurrences(withoutComments(sql), INTEREST_SCORE) == 4`. It has two gaps:
1. `withoutComments` drops only lines whose stripped text starts with `--`. A trailing `--` comment on a statement line (`... AS title -- was CASE WHEN ...`) and a `/* ... */` block comment are both kept, so a verbatim copy inside them is counted. The Javadoc says that "a copy pasted into a comment cannot hide a drifted statement copy", and that is false for both comment forms. I confirmed both: drift the `top` badge to `LEAST(100, GREATEST(0, ROUND(b.raw_n, 1)))::int`, keep the old expression in a trailing `--` or a `/* */` comment, and the guard still passes.
2. The count ignores position. It never checks that each of the four statements (`summary`, `top`, `bottom`, `window-summary`) carries its own copy. If a new section that uses the verbatim badge is added in the same change that drifts one of the four existing copies, the count stays at 4 and the guard stays green. The top and bottom rows are the calibration evidence for the shipped 100/70/22, so a drifted copy there matters.

The nine mutation tests only prove that removing a copy is caught. They never test drift that is balanced by a copy added elsewhere. The guarantee that 07-10 recorded ("a one-byte change to any of the nine copies ... fails") therefore depends on no copy being added at the same time.
**Fix:** Count the raw text, so a copy in any comment form becomes a fifth copy and fails. Also pin each copy to its statement: every badge sits on the line right after its blend line (SQL lines 31, 53, 59 and 66).

```java
List<String> lines = sql.lines().toList();
for (int i = 0; i < lines.size(); i++) {
    String line = lines.get(i);
    if (line.startsWith("WITH learned AS") && !line.startsWith(LEARNED_SECTION)) {
        assertThat(i + 1 < lines.size() ? occurrences(lines.get(i + 1), InterestScoreQueries.INTEREST_SCORE) : 0)
                .as("badge copies on the line after blend line " + (i + 1))
                .isEqualTo(1);
    }
}
assertThat(occurrences(sql, InterestScoreQueries.INTEREST_SCORE))
        .as("verbatim INTEREST_SCORE copies anywhere in the file, comments included")
        .isEqualTo(4);
```

Add a mutation case to `aMissingOrExtraCopyFails` that drifts one copy and puts the verbatim text in a trailing `--` comment, then correct the Javadoc claim.

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

### IN-07: Blend lines are found by a case-sensitive, column-0 prefix, so an extra blend statement that is indented or lower-cased and drifted is never checked

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:170-178`
**Issue:** `assertEveryCopyIsVerbatim` only inspects lines where `line.startsWith("WITH learned AS")`. A sixth statement whose blend line starts with whitespace, or with `with learned AS`, is invisible to the label check. It can hold a hand-edited blend and the guard stays green. I confirmed both forms with a changed `contrib` term. An extra *correct* column-0 copy is rejected (6 labels), but a new section formatted differently slips past. Postgres runs either form the same way, and the SQL header's "never hand-edit those lines" contract is only as strong as this discovery rule. The five existing copies are not affected, because moving any of them off column 0 drops the label count to 4 and fails.
**Fix:** Discover blend lines leniently and check them strictly. Count every non-comment line that matches `(?i)^\s*with\s+learned\s+as\b` and require exactly 5 before the labels are compared:

```java
private static final Pattern BLEND_START = Pattern.compile("(?im)^\\s*with\\s+learned\\s+as\\b");
...
assertThat(BLEND_START.matcher(withoutComments(sql)).results().count())
        .as("statement lines that open a learned/blend CTE, in any case or indentation")
        .isEqualTo(5);
```

### IN-08: `replayIsReadOnly` only catches psql meta-commands at column 0 (pre-existing, unchanged by 07-10)

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:56`
**Issue:** `noneMatch(line -> line.startsWith("\\"))` misses a backslash command after leading whitespace (`  \! cmd`) and one mid-line (`FROM scored \g /tmp/out.tsv`, `\o file`, `\w file`). psql runs all of these from a `-f` file, and they run on the client, so `default_transaction_read_only=on` does not stop them: they can write local files or run a shell. I confirmed that both forms pass the test's SQL checks. The risk is low because the file is hand-maintained by the owner, but the test's stated contract is "It must also stay read-only".
**Fix:** Reject any backslash outside a quoted literal. The file legitimately uses `E'\t'` and `E'\n'` (line 53), so strip literals first:

```java
String outsideLiterals = statements.replaceAll("'(?:[^']|'')*'", "''");
assertThat(outsideLiterals).doesNotContain("\\");
```

### IN-09: `runDriver` inherits the developer's driver and libpq env, and its 30s timeout cannot fire (pre-existing, unchanged by 07-10)

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:224-238`
**Issue:** Only `MYFEEDER_PG_PASSWORD` is removed from the environment. A developer shell that exports `LEARN_RATE`, `LEARNED_CAP` or `WINDOW_DAYS` with a non-integer value makes `driverRequiresThePassword` fail with `invalid candidate: ...`. The failure depends on the environment, not the code. `PGHOSTADDR` and `PGSERVICE` override the forced `PGHOST=127.0.0.1`/`PGPORT=1`, so a future validation regression could let psql connect to a real host rather than the dead local port the Javadoc promises. `readAllBytes()` on stderr (line 235) blocks until the process exits, so `waitFor(30, SECONDS)` never bounds a hung driver, and no path calls `destroyForcibly()`.
**Fix:** Scrub before putting: `environment.keySet().removeIf(k -> k.startsWith("PG") || Set.of("LEARN_RATE", "LEARNED_CAP", "WINDOW_DAYS", "OUT_DIR").contains(k));`. Read stderr on a `CompletableFuture.supplyAsync(...)`, call `waitFor` with the timeout first, and `destroyForcibly()` it when the wait times out.

## Resolved

### WR-02: Replay drift guard passes when only one of several blend and badge copies matches the app

**Status:** Resolved by 07-10 (commits `08185bf`, `7ede686`), confirmed in the 2026-09-29 re-review. All five `WITH learned AS` lines are now checked by exact equality, in order, and the badge count is exact (4). Two weaknesses remain in the replacement guard and are tracked separately: WR-04 (the badge count ignores position, and trailing or block comments are not stripped) and IN-07 (blend-line discovery is case-sensitive and column-0 only).

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

---

_Reviewed: 2026-09-28; re-reviewed 2026-09-29T02:27:29Z (07-10 gap closure)_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
