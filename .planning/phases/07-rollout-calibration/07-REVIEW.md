---
phase: 07-rollout-calibration
reviewed: 2026-09-28T00:00:00Z
re_reviewed: 2026-09-29T03:21:25Z
re_review_scope:
  diff_base: d806f7d51172bc599064e4809571f8933348fbd9
  head: 3069fba
  plan: 07-11
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
  resolved: 3
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-09-28 (full phase). Re-reviewed 2026-09-29T02:27:29Z (incremental, plan 07-10) and 2026-09-29T03:21:25Z (incremental, plan 07-11)
**Depth:** standard
**Files Reviewed:** 24 (each re-review: 1)
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
- **WR-02 is resolved.** `assertEveryCopyIsVerbatim` labels every column-0 `WITH learned AS` line by exact equality and requires `unread, unread, unread, window, learned`. I checked this against the SQL: lines 30, 52 and 58 are `blendCte(UNREAD_SCOPE)`, line 65 is `blendCte(WINDOW_SCOPE)`, and line 90 starts with `LEARNED_CTE + " SELECT 'learned' AS section"`. The line-4 header comment starts with `--`, so it is not counted. `INTEREST_SCORE` appears on lines 31, 53, 59 and 66 and nowhere in `blendCte`, so the exact count of 4 is right. I ran the class through Gradle: 10 tests, 0 failures, 0 errors.
- **No vacuous pass for the nine copies.** Each drift test first asserts that the unmodified file passes and that the needle occurs the expected number of times (3/1/1 blend, 4 badge). Only then does it mutate the middle character of each occurrence. `driftOneByte` always writes a character that differs from the one it replaces. Blend lines are compared by equality, and the badge is counted as an exact substring, so each of the nine mutations must throw. `assertThatCode(...).isInstanceOf(AssertionError.class)` fails when nothing is thrown.
- **The six pre-existing tests are unchanged.**
- **No disk, database or network access from the new tests.** Every variant is an in-memory string.
- **Comment stripping.** Under-stripping was real: trailing `--` comments and `/* */` block comments survived, so the Javadoc claim that "a copy pasted into a comment cannot hide a drifted statement copy" was false (WR-04, now resolved by 07-11).

### Incremental re-review (plan 07-11, `d806f7d..3069fba`)

Scope: `InterestCalibrationReplaySqlTest.java` only. `git diff --stat d806f7d..HEAD -- scripts src/main` is empty, so the replay SQL, the driver and `InterestScoreQueries.java` are byte-identical. The shipped SQL has no `/*` anywhere, and its only literals are `' '`, `'\n'`, `'\t'`, `'1 day'`, `'SCORED'` and the section labels.

What I verified:
- **The suite is green.** `./gradlew test --tests "...InterestCalibrationReplaySqlTest"` ran 12 tests with 0 failures and 0 errors.
- **WR-04 is resolved.** `assertEveryCopyIsVerbatim` (lines 261-303) now does three things. It counts `INTEREST_SCORE` over the raw file, comments included (exactly 4). It requires that the per-line code (`codeOf`: `/* */` spans removed, `--` tail cut) of the line after each unread or window blend line holds `INTEREST_SCORE` once, `BADGE_ITEM` once and the name `interest_score` once. It also requires exactly 4 such badge lines. `aDriftedBadgeFailsDespiteAVerbatimDecoy` covers the three WR-04 decoys: the trailing `--` comment, the `/* */` comment and the appended section. It also covers three same-line decoys and the extra-copy-on-a-comment-line case, and it first proves each variant keeps 4 (or 5) raw copies, so no count alone can reject it. I reran the same logic outside the test with an in-memory probe. The trailing `--` decoy fails at "badge V line 53", which matches the intended check.
- **IN-07 is resolved as scoped.** `BLEND_START` (`(?i)\bwith\s+learned\s+as\b`, unanchored) runs over the code of the whole file and must match exactly 5 times. The indented, lower-case and mid-line forms named in IN-07 each make it 6, and `anExtraBlendStatementInAnyFormFails` proves all three. `blendCte` contains neither `INTEREST_SCORE` nor the name `interest_score`, so these three variants can only be caught by the new count, and the test is not vacuous. Other spellings of a learned CTE still slip past (WR-05).
- **The Javadoc overclaims.** Item 1 says the lenient count plus the label check "means no statement anywhere in the code opens a learned CTE other than the five verbatim lines". The probe below shows that this is false.

Probe output. It is a verbatim mirror of `assertEveryCopyIsVerbatim`, run in memory against the real SQL and the compiled `InterestScoreQueries`. The blend is drifted by changing the `contrib` hinge from `0.5` to `0.4`, and the badge by changing `ROUND(b.raw_n)` to `ROUND(b.raw_n, 1)`:

```
PASSES : shipped
PASSES : extra WITH RECURSIVE learned AS stmt
PASSES : extra WITH "learned" AS stmt
PASSES : extra WITH learned (topic_id, vote_sum) AS stmt
PASSES : extra WITH x AS (SELECT 1), learned AS stmt
PASSES : top stmt: verbatim blend in multi-line /* */, real blend WITH RECURSIVE + drifted
PASSES : top stmt: verbatim blend in multi-line /* */, real blend WITH "learned" + drifted
PASSES : top badge drifted, verbatim item in a string literal
fails (blend-start count) : CONTROL indented drifted blend
fails (badge V line 53) : CONTROL trailing -- decoy
fails (blend-start count) : CONTROL multi-line /* */ with normal WITH learned AS
```

The three controls fail at the expected checks, which confirms that the probe mirrors the guard.

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

As a result, prod logs `Jev circuit breaker HALF_OPEN_TO_CLOSED (failure rate -1.0%, slow-call rate -1.0%)`. These lines are the D-05/D-06 launch evidence channel, which the CLAUDE.md "Jev event log lines" bullet and the 07-07 rollout watch grep for. A negative percentage there is misleading. `breakerTransitionsAreLogged` checks only the transition name, so the test never caught this.
**Fix:** Only print rates when they mean something. Print them on transitions into OPEN, where resilience4j carries the previous window's metrics over, and never print a negative value:

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

### WR-05: A learned CTE spelled any other way escapes the blend-opening count, so a commented-out verbatim blend can stand in for a drifted real statement

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:53-54, 241-246, 264-267, 305-315`
**Issue:** The drift guard relies on `BLEND_START` = `(?i)\bwith\s+learned\s+as\b` to find every statement that opens a learned CTE. Postgres accepts other spellings of the same CTE, and none of them match:
- `WITH RECURSIVE learned AS` (RECURSIVE is legal on non-recursive CTEs)
- `WITH "learned" AS` (the quoted identifier is the same name)
- `WITH learned (topic_id, vote_sum) AS` (a column list)
- `WITH x AS (...), learned AS` (learned is not the first CTE)

`codeOf` is per line, so a verbatim blend line inside a multi-line `/* ... */` block still counts as code, both for `BLEND_START` and for the column-0 label check. Put the two together and one of the four real statements can be replaced:

```
/*
WITH learned AS <verbatim unread blend>
*/ WITH RECURSIVE learned AS <drifted blend> SELECT 'top' AS section, ..., <INTEREST_SCORE> AS interest_score, ...
```

This version still has 5 blend openings and the labels `unread, unread, unread, window, learned`. It holds 4 raw badge copies, and the line after the commented-out blend carries the badge item and name exactly once. So the guard passes, while psql runs a `top` statement with a drifted blend. The probe above confirms this with both `RECURSIVE` and `"learned"`, and it also confirms that an extra drifted statement passes in each of the four spellings. This is the WR-04 failure class again, now for blend lines: a verbatim decoy kept in a comment hides a drifted statement. It also makes Javadoc item 1 false ("no statement anywhere in the code opens a learned CTE other than the five verbatim lines"). The `codeOf` Javadoc presents "a block comment that spans lines counts as code" as a harmless limit, when it is the gap. The `top` and `bottom` rows are the calibration evidence for the shipped 100/70/22.
**Fix:** The shipped SQL has no `/*` at all, so fail closed on block comments, and widen the discovery to every spelling of the CTE name followed by `AS (`:

```java
private static final Pattern BLEND_START = Pattern.compile(
        "(?i)(?<![\\w\"])\"?learned\"?\\s*(?:\\([^)]*\\)\\s*)?as\\s*(?:(?:not\\s+)?materialized\\s*)?\\(");

// first line of assertEveryCopyIsVerbatim
assertThat(sql).as("block comments in the replay (only whole-line -- comments are allowed)")
        .doesNotContain("/*");
```

The widened pattern matches every `learned AS (` wherever it appears (after `WITH`, after `RECURSIVE`, or after a comma), so the count of 5 still holds for the shipped file. Once `/*` is banned, `COMMENT_SPAN` can go, and `codeOf` only needs to cut `--` tails. Add the four spellings and the commented-out-verbatim replacement to `anExtraBlendStatementInAnyFormFails`, and change Javadoc item 1 to name the forms the pattern actually covers.

## Info

### IN-01: Client fallback tiers (70/40) disagree with the shipped tiers (70/22)

**File:** `src/main/frontend/src/utils/interest.ts:32`, `src/main/frontend/src/hooks/useInterest.ts:45`
**Issue:** Prod serves `neutral: 22` (`application.yaml:496`). Until `/api/interest/status` resolves, every badge scored 22-39 renders `tier-low` instead of `tier-neutral`. If the request errors, this lasts until a reload or a focus refetch. The result is a visible color flash on first paint of every list.
**Fix:** Either accept this and document it as a known flash, or remove it: persist the last served tiers (for example as `initialData` from a `localStorage` copy written in `select`'s consumer), or render badges uncolored (`tier-pending`) until tiers load.

### IN-02: `window-summary` reuses the `scored_unread` column label for a read-and-unread population

**File:** `scripts/interest-calibration-replay.sql:68`
**Issue:** The window cross-check counts every SCORED article in the window, read or unread, but its column is still named `scored_unread`. Anyone comparing the two TSV rows could misread the population.
**Fix:** Alias it `scored_in_window` in the `window-summary` statement. This is outside the verbatim blend line, so the drift guard is unaffected.

### IN-03: Driver error text and defaults for non-candidate values

**File:** `scripts/interest-calibration-replay.sh:27-47`
**Issue:** An invalid `LEARN_RATE`, `LEARNED_CAP` or `WINDOW_DAYS` is reported as `invalid candidate: <value>`, which points the operator at the wrong input. The defaults (2 / 20 / 14) are copied from `application.yaml` and will silently diverge if the app values are tuned later.
**Fix:** Name the variable in the message (`echo "invalid LEARN_RATE: $LEARN_RATE"`). Add a comment, or a drift test assertion, tying the defaults to `myfeeder.interest.blend.learn-rate`, `learned-cap` and `window-days`.

### IN-04: Replay sections read different snapshots of a live database

**File:** `scripts/interest-calibration-replay.sql:29-90`
**Issue:** psql runs each statement in its own autocommit transaction while the app keeps scoring and ingesting. So `summary`, `top`, `bottom`, `window-summary`, `votes` and `learned` can each reflect a different population, and `votes` and `learned` may not match the blend that `summary` used.
**Fix:** Wrap the file in `BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;` … `COMMIT;`. Neither keyword trips `WRITE_KEYWORD`.

### IN-05: Breaker recovery transitions are logged at WARN, and the logger is a `@Configuration` with no bean methods

**File:** `src/main/java/org/bartram/myfeeder/config/JevEventLogging.java:370-389`
**Issue:** `OPEN_TO_HALF_OPEN` and `HALF_OPEN_TO_CLOSED` are normal recovery steps but are logged at WARN, the same level as a trip, which adds noise to the WARN grep. The class is a `@Configuration(proxyBeanMethods = false)` whose only purpose is a side effect in its constructor. `@Component` states that intent more directly.
**Fix:** Log transitions whose target is `CLOSED` or `HALF_OPEN` at INFO. Consider `@Component`.

### IN-06: CLAUDE.md misattributes the test-yaml tier pin

**File:** `CLAUDE.md` (the "Tier thresholds and tuning" bullet)
**Issue:** It says to keep the test yaml at "profile-points 100 / tiers 70 / 40, because `InterestScoreQueriesTest` fixtures assume them". `InterestScoreQueries` never reads tiers. The 70/40 pin is asserted by `InterestApiIntegrationTest.statusServesTheConfiguredTierThresholds`, and only profile-points is a fixture assumption of `InterestScoreQueriesTest`.
**Fix:** Change the sentence to "profile-points 100 (`InterestScoreQueriesTest` fixtures) and tiers 70/40 (`InterestApiIntegrationTest`)".

### IN-08: `replayIsReadOnly` only catches psql meta-commands at column 0 (pre-existing, unchanged by 07-10 and 07-11)

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:71`
**Issue:** `noneMatch(line -> line.startsWith("\\"))` misses a backslash command after leading whitespace (`  \! cmd`) and one mid-line (`FROM scored \g /tmp/out.tsv`, `\o file`, `\w file`). psql runs all of these from a `-f` file, and they run on the client, so `default_transaction_read_only=on` does not stop them: they can write local files or run a shell. I confirmed that both forms pass the test's SQL checks. The risk is low because the owner maintains the file by hand, but the test's stated contract is "It must also stay read-only".
**Fix:** Reject any backslash outside a quoted literal. The file legitimately uses `E'\t'` and `E'\n'` (lines 53 and 59), so strip literals first:

```java
String outsideLiterals = statements.replaceAll("'(?:[^']|'')*'", "''");
assertThat(outsideLiterals).doesNotContain("\\");
```

### IN-09: `runDriver` inherits the developer's driver and libpq env, and its 30s timeout cannot fire (pre-existing, unchanged by 07-10 and 07-11)

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:365-379`
**Issue:** `runDriver` removes only `MYFEEDER_PG_PASSWORD` from the environment. If a developer shell exports `LEARN_RATE`, `LEARNED_CAP` or `WINDOW_DAYS` with a non-integer value, `driverRequiresThePassword` fails with `invalid candidate: ...`. The failure then depends on the environment, not the code. `PGHOSTADDR` and `PGSERVICE` override the forced `PGHOST=127.0.0.1`/`PGPORT=1`. So a future validation regression could let psql connect to a real host, not the dead local port the Javadoc promises. `readAllBytes()` on stderr (line 376) blocks until the process exits, so `waitFor(30, SECONDS)` (line 377) never bounds a hung driver, and no path calls `destroyForcibly()`.
**Fix:** Scrub before putting: `environment.keySet().removeIf(k -> k.startsWith("PG") || Set.of("LEARN_RATE", "LEARNED_CAP", "WINDOW_DAYS", "OUT_DIR").contains(k));`. Read stderr on a `CompletableFuture.supplyAsync(...)`, call `waitFor` with the timeout first, and `destroyForcibly()` it when the wait times out.

### IN-10: `codeOf` does not strip string literals, so a verbatim badge item in a literal can stand in for a drifted, renamed badge column

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:291-299, 311-315`
**Issue:** The per-badge-line check counts `INTEREST_SCORE`, `BADGE_ITEM` and `interest_score` in the line's "code", and that code still includes single-quoted literals. Suppose the top line becomes `..., a.id AS article_id, <drifted> AS score, ', <INTEREST_SCORE> AS interest_score' AS note, ...`. It then holds each needle exactly once, and the file still has 4 raw copies, so the guard passes (probe case "top badge drifted, verbatim item in a string literal"). The decoy has to rename the real column, which makes this contrived, but it contradicts the 07-11 precision truth that a one-token badge drift "fails whatever verbatim copy survives elsewhere". The `codeOf` Javadoc mentions "no string-literal ... awareness" only as a risk of cutting real SQL. It never says a literal can hide a decoy.
**Fix:** Blank single-quoted literals before counting. The badge lines' only literals are the section labels, `E'\t'`, `E'\n'` and `' '`, none of which holds a needle:

```java
private static String codeOf(String line) {
    String code = line.replaceAll("'(?:[^']|'')*'", "''");
    code = COMMENT_SPAN.matcher(code).replaceAll(" ");
    int dashes = code.indexOf("--");
    return dashes < 0 ? code : code.substring(0, dashes);
}
```

Stripping literals first also stops a `'--'` literal from cutting real SQL. Add the literal decoy to `aDriftedBadgeFailsDespiteAVerbatimDecoy`.

## Resolved

### WR-02: Replay drift guard passes when only one of several blend and badge copies matches the app

**Status:** Resolved by 07-10 (commits `08185bf`, `7ede686`), confirmed in the 2026-09-29 re-review. All five `WITH learned AS` lines are checked by exact equality, in order, and the badge count is exact (4). The follow-up gaps WR-04 and IN-07 are resolved by 07-11 (below). WR-05 and IN-10 track what 07-11 still leaves open.

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`
**Issue (original):** The SQL file repeats the unread blend three times and the `INTEREST_SCORE` expression four times. `assertThat(sql).contains(...)` only proved that at least one copy was identical, so a hand-edit to the `top` or `bottom` copy passed.

### WR-04: The badge guard counts copies without checking where they are, and its comment filter only removes whole-line `--` comments, so a drifted badge copy can still pass

**Status:** Resolved by 07-11 (commits `04aef6c`, `615d7a5`), confirmed in the 2026-09-29T03:21:25Z re-review. The raw count of 4 now includes comments. Each of the four badge lines must hold `INTEREST_SCORE`, the `, <INTEREST_SCORE> AS interest_score` item and the `interest_score` name exactly once in its code. `aDriftedBadgeFailsDespiteAVerbatimDecoy` proves that the trailing `--`, `/* */` and appended-section decoys all fail, along with three same-line decoys and a fifth copy on a comment line. The suite is green (12 tests). The literal-decoy gap that remains is tracked as IN-10.

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:261-303`
**Issue (original):** The badge check counted `INTEREST_SCORE` over text with only whole-line `--` comments removed, and it never tied a copy to its statement. A drifted badge passed when its verbatim text survived in a trailing or block comment, or in an added section.

### IN-07: Blend lines are found by a case-sensitive, column-0 prefix, so an extra blend statement that is indented or lower-cased and drifted is never checked

**Status:** Resolved by 07-11 (commit `615d7a5`) for the forms it named. `BLEND_START` runs unanchored and case-insensitive over the code of the whole file, and the count must be exactly 5. `anExtraBlendStatementInAnyFormFails` proves that the indented, lower-case and mid-line forms fail. Other spellings of a learned CTE (`RECURSIVE`, a quoted name, a column list, a non-first CTE) still escape, and they are tracked as WR-05.

**File:** `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java:53-54, 264-267`
**Issue (original):** `assertEveryCopyIsVerbatim` only inspected lines that start with `WITH learned AS` at column 0, so an extra drifted blend statement that was indented or lower-cased was never checked.

---

_Reviewed: 2026-09-28; re-reviewed 2026-09-29T02:27:29Z (07-10 gap closure) and 2026-09-29T03:21:25Z (07-11 gap closure)_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
