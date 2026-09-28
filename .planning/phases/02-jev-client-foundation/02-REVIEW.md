---
phase: 02-jev-client-foundation
reviewed: 2026-09-22T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - build.gradle.kts
  - deploy.sh
  - helm/myfeeder/templates/app-deployment.yaml
  - helm/myfeeder/templates/app-secret.yaml
  - helm/myfeeder/values.yaml
  - src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/integration/JevJudgment.java
  - src/main/java/org/bartram/myfeeder/integration/JevNotConfiguredException.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - src/test/resources/application.yaml
findings:
  critical: 0
  warning: 5
  info: 7
  total: 12
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-09-22T00:00:00Z
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

I reviewed the Jev client (the app-owned `TypeSafeClient` bean, `JevApiClient`/`JevApiClientImpl`, `JevJudgment` and the retry interval customizer), the resilience4j and `spring.ai.typesafe` YAML, the Helm and `deploy.sh` secret wiring, and the four test classes. I checked the behavior against the TypeSafe SDK 0.1.0 bytecode: the status-to-exception mapping in `TypeSafeResponseErrorHandler`, the `Retry-After`/`retry-after-ms` parsing, the `TypeSafeClient.Builder` env-var fallbacks, and the `SystemOneResponse` accessors. I also ran the phase tests. `JevApiClientImplTest` (18), `JevResilienceTest` (12) and `TypeSafeConfigTest` (11) all pass, and `JevLiveSmokeTest` was skipped as designed.

The core design holds up. The keyless startup is safe, the key never reaches the logs, typed exceptions propagate unchanged, and the Reactor Netty transport is Jev-only. The remaining problems are at the edges:

- Caller input errors are recorded as circuit-breaker failures.
- Choice questions are accepted, but their answers are silently dropped.
- The breaker thresholds count retry attempts, not logical calls, which contradicts the documented aspect-order convention.
- Adding AspectJ turns on retries for Raindrop's non-idempotent POST.
- `helm --set` silently corrupts or rejects secret values that contain certain characters.

I found no critical issues.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Caller input errors inside `judge()` are recorded as circuit-breaker failures

**File:** `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java:41-47`
**Issue:** `Assert.notNull(state, …)` and `Assert.notEmpty(questions, …)` run inside the `@CircuitBreaker`/`@Retry` proxy. The resilience4j default record predicate treats every exception as a failure unless it is listed under `ignore-exceptions`, and `IllegalArgumentException` is not in the `jev` list (`application.yaml:55-60`). Caller-side serialization failures behave the same way. For example, a `null` key in `state` makes Jackson fail while writing the body. The SDK catches the resulting `RestClientException` and rethrows it as a plain `TypeSafeException("Jev … response could not be read")`, which the breaker also records.

The phase deliberately ignores per-article errors (400, 422, missing answer, wrong answer type) so that they can never open the breaker. A repeated Phase 4 caller bug, such as the same malformed state built for every article, would open the breaker after 10 records and pause all scoring for 60s at a time, with no Jev fault involved.
**Fix:** Add the caller-error type to the breaker's ignore list in both YAML files:
```yaml
ignore-exceptions:
  - org.bartram.myfeeder.integration.JevNotConfiguredException
  - java.lang.IllegalArgumentException
  - ...
```
Alternatively, validate `state`/`questions` (including null keys) in a non-proxied layer before the annotated method runs. Add a `JevResilienceTest` case that asserts `judge(null, …)` leaves `getNumberOfFailedCalls()` at 0.

### WR-02: Choice questions are accepted and billed, but their answers are silently dropped

**File:** `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java:64-72`, `src/main/java/org/bartram/myfeeder/integration/JevJudgment.java:29-44`
**Issue:** `judge` accepts any `Map<String, ? extends Question>`, and SDK 0.1.0 has three question types: `Noul`, `Score` and `Choice`. For a `Choice` question, the validation falls through to `response.answer(name)`, which only checks that an answer is present. `JevJudgment.from` then copies only `response.nouls()` and `response.scores()`. The caller pays for the Choice question and passes validation, but the answer is not in the result. Nothing in the API surface (types or exception) rejects it. Phase 2 marks `JevJudgment` as costly to reshape (D-02), so this should be decided now instead of being discovered in Phase 3 or 4.
**Fix:** Reject unsupported question types before the HTTP call. That keeps the check outside the breaker's failure count if WR-01's ignore is applied:
```java
questions.forEach((name, q) -> Assert.isTrue(q instanceof Noul || q instanceof Score,
        () -> "Unsupported question type for '" + name + "': " + q.getClass().getSimpleName()));
```
Alternatively, add a `choices` map to `JevJudgment`.

### WR-03: Breaker thresholds count retry attempts, and this contradicts the documented convention

**File:** `src/main/resources/application.yaml:45-60`, `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java:235-248`
**Issue:** CLAUDE.md says "`@CircuitBreaker(name = "...")` (outer) + `@Retry(name = "...")` (inner)". At runtime the order is the reverse: resilience4j's default aspect order puts Retry outermost. `JevResilienceTest` asserts this directly (3 calls produce 9 recorded failures, and the breaker opens during the 4th call). As a result, the D-09 values (`minimum-number-of-calls: 10`, window 20, 50% rate) count attempts, not articles:

- 4 consecutive failing articles open the breaker.
- Under persistent 5xx, each failed article adds 3 failure records and each success adds 1. At 25% failing articles the recorded failure rate is 0.75/(0.75+0.75) = 50%, so the breaker trips at about half the intended rate.

D-09's values were written as call-level thresholds. The pending todo notes the CLAUDE.md wording, but it does not note the effect on the D-09 numbers.
**Fix:** Choose one option on purpose:
- Make the breaker outer (`resilience4j.circuitbreaker.circuit-breaker-aspect-order` and `resilience4j.retry.retry-aspect-order` so the breaker has the lower precedence value). The breaker then records one outcome per logical call.
- Keep Retry outer, and restate the D-09 thresholds in attempt units, for example `minimum-number-of-calls: 30`.

In either case, update the CLAUDE.md convention to match what runs.

### WR-04: Adding AspectJ enables retries on Raindrop's non-idempotent `createBookmark` POST

**File:** `build.gradle.kts:44` (activates `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java:52-65`, configured by `src/main/resources/application.yaml:63-68`)
**Issue:** `spring-boot-starter-aspectj` turns on Raindrop's previously inert `@Retry(name = "raindrop")`. That instance has no `retry-exceptions`, so any exception is retried up to 3 times, including a `ResourceAccessException` read timeout (30s global read timeout) and a 5xx. `createBookmark` is a `POST /raindrop`. If Raindrop commits the bookmark and the response is then lost or slow, the retry creates a duplicate bookmark in the user's account. A request can also block for about 92s (3 × 30s plus waits). The accepted behavior change and the follow-up todo (`2026-09-23-tune-raindrop-resilience-…md`) only cover 4xx retries and the inert multiplier. They do not mention non-idempotent duplicates.
**Fix:** Either remove `@Retry` from `createBookmark` (keep the breaker), or give it a separate retry instance that only retries connect-phase failures (`java.net.ConnectException`) and never read timeouts or 5xx. Add the idempotency point to the pending todo.

### WR-05: `helm --set` silently corrupts or rejects secret values with certain characters

**File:** `deploy.sh:27-32`
**Issue:** The new `--set secrets.typesafeApiKey="$TYPESAFE_KEY"` line copies the existing `--set` pattern for secrets. Values passed with `--set` go through Helm's strvals parser. I tested `helm template` against this chart:
- `ab\cd` renders as `"abcd"`. The backslash is silently stripped, so the key is wrong, Jev returns 401, and the breaker opens.
- `a,b` fails the deploy with `key "b" has no value`.

The key also appears on the `helm` process argv, where `ps` on the deploy host can read it.
**Fix:** Pass secrets through a file instead of `--set`. Either use `--set-file secrets.typesafeApiKey=<(printf %s "$TYPESAFE_KEY")`, or write a temporary `values` file with `umask 077` and pass `-f`. At minimum, use `--set-string` and reject keys that contain `,` or `\`. Apply the same change to the pg/anthropic/raindrop secrets.

## Info

### IN-01: Stale Javadoc on `JevApiClient`

**File:** `src/main/java/org/bartram/myfeeder/integration/JevApiClient.java:20-21`
**Issue:** "Once the circuit breaker is in place, `CallNotPermittedException` means the breaker is open." The breaker is already in place in this phase.
**Fix:** Change it to "`CallNotPermittedException` means the `jev` circuit breaker is open."

### IN-02: Live smoke test does not run the resilience wiring it claims to cover

**File:** `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java:53-58`
**Issue:** The Javadoc says the test uses "the production wiring", but the runner does not load `AopAutoConfiguration`, `CircuitBreakerAutoConfiguration` or `RetryAutoConfiguration`. `@Retry`/`@CircuitBreaker` are therefore inactive, and a single transient 429 or 5xx fails the billed run.
**Fix:** Add the three auto-configurations, as `JevResilienceTest` does, or change the Javadoc to say resilience is not exercised.

### IN-03: Retry base interval reads only the instance key, and the shift can overflow

**File:** `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java:73-81`
**Issue:** The `Binder` reads only `resilience4j.retry.instances.jev.wait-duration`. If a later phase moves the value into `configs.default` or a `base-config`, the customizer silently falls back to 1s. `1L << (attempt - 1)` also overflows for `max-attempts` ≥ 64, which is harmless at 3 but not guarded.
**Fix:** Document that the key must stay on the instance, or read the effective value from the built `RetryConfig`. Clamp the exponent, for example `Math.min(attempt - 1, 20)`.

### IN-04: `JevJudgment`'s unmodifiable guarantee holds only through `from()`, and `maxLevel` uses a -1 sentinel

**File:** `src/main/java/org/bartram/myfeeder/integration/JevJudgment.java:24-27`
**Issue:** The public canonical constructor accepts mutable or null maps, so the Javadoc's "unmodifiable" claim depends on callers using `from()`. `JevScore.maxLevel` is `-1` when there is no legend, and `0` for a one-level legend. A Phase 4 `value / maxLevel` normalization would then produce negative values or Infinity/NaN.
**Fix:** Add a compact constructor that defensively copies the maps (`Collections.unmodifiableMap(new LinkedHashMap<>(…))` keeps the order) and rejects nulls. Note in the Javadoc that normalization must use the requested level count when `maxLevel < 1`.

### IN-05: The API key is not trimmed

**File:** `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java:34-38`, `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java:53`
**Issue:** `hasText` treats a whitespace-padded key (for example `"sk-abc "`) as configured, and the supplier sends it verbatim. The result is a 401, which opens the breaker (D-06), and a key that looks correct in the Secret.
**Fix:** Trim in the supplier and in `requireConfigured()`: `StringUtils.trimWhitespace(properties.getApiKey())`.

### IN-06: A blank `model` or `base-url` silently falls back to SDK env vars or `jev-latest`

**File:** `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java:54-56`
**Issue:** The comment says setting these explicitly means "the SDK never falls back to its TYPESAFE_* environment variables". The SDK builder actually uses `firstNonBlank(explicit, System.getenv("TYPESAFE_DEFAULT_MODEL"), "jev-latest")`, and the same pattern for `TYPESAFE_BASE_URL`. A blank override such as `SPRING_AI_TYPESAFE_MODEL=` therefore unpins the model (JEV-03) without any error. Only the per-response `model` would reveal it.
**Fix:** `Assert.hasText(properties.getModel(), …)` and `Assert.hasText(properties.getBaseUrl(), …)` in `typeSafeClient(...)`, or correct the comment.

### IN-07: A redeploy without the env var wipes the stored key and now rolls the pod immediately

**File:** `deploy.sh:18-22`, `helm/myfeeder/templates/app-deployment.yaml:15-16`
**Issue:** `deploy.sh` always sends `--set secrets.typesafeApiKey=""` when the variable is unset. The new `checksum/secret` annotation then rolls the pod straight away, and production Jev is silently disabled apart from a console warning. CONTEXT defers this (Helm `lookup`), and Raindrop has the same gap. It is recorded here because the checksum annotation makes the wipe take effect immediately.
**Fix:** This is tracked as deferred. As a cheap guard, have `deploy.sh` refuse to run with an empty key when the live Secret already has one, unless `--allow-empty-typesafe` is passed.

---

_Reviewed: 2026-09-22T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
