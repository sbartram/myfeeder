---
type: Integration
title: Jev (TypeSafe) Scoring Integration
description: Documents the Jev/TypeSafe AI judging integration that powers interest scoring — the app-owned TypeSafeClient bean, the JevApiClient/JevApiClientImpl split with its own Resilience4j circuit breaker and retry, the single-retry-layer invariant, the bounded scoring executor, and the operational throttle levers used when Jev rate-limits the app.
tags: [integration, jev, typesafe, resilience4j, circuit-breaker, interest-scoring, external-api]
verified:
  - by: openwiki/0.7.0
    at: 2026-10-04T13:52:44.431Z
sources:
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-488238bd6a4828fc54072530
    resource: repo://src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - id: openwiki-source-0a50732f461f501fbe68e757
    resource: repo://src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - id: openwiki-source-068b4c54848ec0b7c44aa265
    resource: repo://src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-1ccecc8d0cb9715b566fdff9
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - id: openwiki-source-bfd9e6b64553ce72d0555fce
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - id: openwiki-source-4805f4a78501939aafe493fa
    resource: repo://src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
generated: { by: "openwiki/0.7.0", at: "2026-10-04T13:52:44.431Z" }
---

# Jev (TypeSafe) Scoring Integration

Jev is TypeSafe's structured-judgment API (`org.springaicommunity:spring-ai-starter-typesafe`, pinned to an explicit version `0.1.0` in `build.gradle.kts` via `extra["typesafeVersion"]` because it ships in no Spring AI BOM). myfeeder calls it to score every article against a user's interest profile and topics, producing the data behind [interest ranking](../workflows/interest-scoring.md). This page documents the client wiring, the resilience configuration, and the operational levers; see that workflow page for how scores are blended into a display score.

## App-owned `TypeSafeClient` bean

The TypeSafe starter's own auto-configured `TypeSafeClient` bean asserts that `spring.ai.typesafe.api-key` has text and throws `IllegalStateException` if it doesn't — which would crash the pod on startup whenever the key is unset. `TypeSafeConfig` avoids this by defining the `TypeSafeClient` bean itself, which makes the starter's `@ConditionalOnMissingBean` back off entirely:

- A blank or missing key only disables Jev calls (`TypeSafeConfig` logs the fixed line `TypeSafe Jev not configured; interest scoring disabled`, never the key, its length, or its prefix) — the app still starts.
- The key is passed via the `Supplier<String>` overload of `.apiKey(...)`, not the `String` overload (which asserts `hasText` at build time and would make a blank key illegal). The key is read only through that supplier and is never logged.
- `baseUrl` is set explicitly from `TypeSafeProperties.getBaseUrl()`, so the SDK never falls back to its own `TYPESAFE_*` environment variables.
- The bean gets a Jev-only Reactor Netty request factory (`ClientHttpRequestFactoryBuilder.reactor()`, connect timeout 5s via `TypeSafeConfig.JEV_CONNECT_TIMEOUT`, read timeout = `spring.ai.typesafe.timeout`, 30s in production) built on a `clone()` of the auto-configured `RestClient.Builder`. Cloning keeps the shared builder's User-Agent customizer and observations intact and leaves the transport of every other bean (feed fetches, Raindrop) untouched. Never build a second `TypeSafeClient` or reuse its transport for other outbound calls.
- The model is pinned (`spring.ai.typesafe.model: jev-1.13.0`) so score semantics don't shift under an alias like `jev-latest`.

## `JevApiClient` / `JevApiClientImpl` split

This follows the same pattern documented in [Raindrop Integration](raindrop.md) for `RaindropService`/`RaindropApiClientImpl`: business-rule checks happen outside the circuit breaker, and only the real HTTP call is wrapped.

- **`JevApiClient`** is the interface: `isConfigured()` (true when the TypeSafe key has text; never throws, never calls Jev, and is **not** wrapped by the breaker or retry — both the `/api/interest/status` endpoint and the scoring sweep gate on it) and `judge(Map<String, ?> state, Map<String, ? extends Question> questions)`, a generic pass-through that knows nothing about articles or topics.
- **`JevApiClientImpl`** carries `@CircuitBreaker(name = "jev")` and `@Retry(name = "jev")` on `judge(...)` only, with no `fallbackMethod` — typed SDK exceptions (`org.springaicommunity.typesafe.exception.*`) and `CallNotPermittedException` (breaker open) propagate unchanged to callers and ultimately to `GlobalExceptionHandler`. `judge` calls a private `requireConfigured()` that throws `JevNotConfiguredException` *before* the HTTP call — a configuration check must never touch the breaker. These annotations work only because they sit on a separate bean: Resilience4j's `@CircuitBreaker`/`@Retry` are AOP-proxy-based, and `JevApiClientImpl` calling its own annotated method (self-invocation) would bypass the proxy and silently drop the resilience behavior — exactly the trap the Raindrop split avoids.
- `judge` builds the state as a JSON object in the caller's iteration order with null values dropped (the caller's map is never mutated), validates that every question is a `Noul` or `Score` (a `Choice` would be billed and then silently dropped by `JevJudgment`), and checks that every requested question has a present, correctly-typed answer before returning.
- `TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException` (401/403) are logged at WARN with status and request id only — never the exception message or response body, since those can echo request details — then rethrown.

`GlobalExceptionHandler` maps the resulting exceptions to HTTP responses, with every detail fixed text (TypeSafe messages/bodies can echo request content and must never reach a `ProblemDetail`):

| Exception | Status | Detail |
|---|---|---|
| `JevNotConfiguredException` | 503 | the app's own fixed message |
| `CallNotPermittedException` (breaker open) | 503 | "Jev unavailable" |
| `TypeSafeBadRequestException`, `TypeSafeUnprocessableEntityException` | 422 | "Jev rejected the request (HTTP `status`)" |
| any other `TypeSafeException` (429, 5xx, 401/403, connection/timeout, missing answer) | 503 | "The Jev request failed. Try again later." |

## Single-retry-layer invariant

`spring.ai.typesafe.retry.max-retries` is `0` — the TypeSafe SDK's own retry layer is permanently disabled. The Resilience4j `jev` retry instance is the **only** retry layer, and this is a standing invariant, not historical trivia: re-enabling SDK retries would stack a second retry layer under Resilience4j's and multiply outbound calls on every 429, defeating the rate-limit protection below.

- 3 attempts total, waiting 1s then 2s by default (`resilience4j.retry.instances.jev.wait-duration: 1s`, doubled per attempt).
- `TypeSafeConfig.jevRetryInterval` (a `RetryConfigCustomizer` bean) overrides the wait: when the last failure was a `TypeSafeRateLimitException` (429) carrying a `retryAfterMs` hint, it waits that long instead, clamped to `[0, MAX_RETRY_AFTER_MS]` (10s) — honoring the server's `Retry-After` without ever blocking the scoring thread for an unbounded time.
- `retry-exceptions` (the only retried types): `TypeSafeRateLimitException` (429), `TypeSafeInternalServerException` (5xx, including the `TypeSafeOverloadedException` 529 subclass), `TypeSafeApiConnectionException` (connection failures and timeouts — a Jev-transport read timeout surfaces as this type, not `TypeSafeApiTimeoutException`).
- It never retries 400/422 (`TypeSafeBadRequestException`/`TypeSafeUnprocessableEntityException`), 401/403 (`TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException`), or `JevNotConfiguredException` (listed under `ignore-exceptions` for both the retry and breaker instances, so an unconfigured key never counts as a failure or burns a retry attempt).

## Circuit breaker configuration

The `jev` circuit breaker instance (`resilience4j.circuitbreaker.instances.jev`) is the outer aspect relative to retry (`circuit-breaker-aspect-order: 1` vs. `retry-aspect-order: 2`), so it records one outcome per logical call (including its retries), not per attempt:

- `COUNT_BASED` sliding window, size 20, minimum 10 calls before the failure rate is evaluated.
- 50% failure-rate threshold to trip OPEN.
- Slow-call detection: calls over 15s count as slow, and a 50% slow-call rate also trips OPEN.
- `wait-duration-in-open-state: 60s`, with `automatic-transition-from-open-to-half-open-enabled: true` — the breaker moves from OPEN to HALF_OPEN on its own after 60s with no call needed, so a paused sweep resumes automatically (`InterestScoringSweep` skips while OPEN/FORCED_OPEN but runs during HALF_OPEN).

**401/403 are deliberately recorded, not ignored.** `TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException` are absent from the `jev` breaker's `ignore-exceptions` list, so a bad or revoked API key counts as a failure and opens the breaker after enough calls. This is the opposite of the Raindrop breaker's `ignore-exceptions` pattern, where `RaindropNotConfiguredException` is explicitly ignored (together with its retry instance) so a simply-unconfigured token never opens that breaker or burns a retry attempt. The `jev` breaker's `ignore-exceptions` instead covers only caller-input/configuration problems that should never count as a Jev *failure*: `JevNotConfiguredException`, `TypeSafeBadRequestException`, `TypeSafeUnprocessableEntityException`, `TypeSafeMissingAnswerException`, `TypeSafeAnswerTypeException`, and `IllegalArgumentException` (the question-type assertion in `JevApiClientImpl.judge`).

## Operational observability: `JevEventLogging`

`config/JevEventLogging` subscribes to the `jev` retry and circuit-breaker event publishers obtained from the shared `CircuitBreakerRegistry`/`RetryRegistry` (get-or-create by name, so it observes the exact same instances the `@CircuitBreaker`/`@Retry` aspects on `JevApiClientImpl.judge` use) and logs three kinds of lines, with simple exception class names and numbers only — **never** an exception message, a response body, or the API key:

- `Jev retry attempt {N} after {Exception} (waiting {M} ms)` at **INFO**, once per retry attempt.
- `Jev retries exhausted after {N} attempts: {Exception}` at **WARN**, fired only when a retryable call has used every attempt.
- `Jev circuit breaker {STATE_TRANSITION} (failure rate {x}%, slow-call rate {y}%)` at **WARN** on every breaker state transition (e.g. `CLOSED_TO_OPEN`), using `StateTransition.name()` rather than its prose `toString()`.

These lines are the production evidence channel for Jev health — no metric is added. Find them in the cluster with:

```bash
kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '
```

## Bounded scoring executor

`InterestScoringConfig` defines the `interestScoringExecutor` bean (constant `InterestScoringConfig.EXECUTOR`, threads named `jev-score-`) that runs scoring off the polling/scheduler thread:

- Registered with `defaultCandidate = false` — a plain `Executor` bean here would make Boot's `@ConditionalOnMissingBean(Executor.class)` back off and silently remove the `applicationTaskExecutor`. Consumers (`ScoringQueue`) inject it explicitly by name with `@Qualifier(InterestScoringConfig.EXECUTOR)`.
- Core pool size = max pool size = `myfeeder.interest.concurrency` (default **1**). **Concurrency stays at 1 for rate-limit safety**: Jev enforces a per-account request rate, and running more than one scoring call concurrently would make the already-conservative Resilience4j retry/breaker budget race against itself. This is also why the SDK's own retry layer must never be re-added (see above) — concurrency 1 plus a single retry layer is the whole rate-limit safety story, and loosening either independently defeats it.
- `queue-capacity` 1000, and the **default abort rejection policy is kept intentionally**: `ScoringQueue.submit` catches the resulting `TaskRejectedException`, removes the article id from its in-flight set, and lets the periodic sweep re-enqueue it later. A discarding policy would instead silently drop the task while leaving the id marked in-flight forever, so eligible articles would never be retried.
- No `@Async` is used anywhere; work is handed to this executor explicitly by `ScoringQueue`.

## Operational throttle levers

If Jev rate-limits the app (repeated 429s or a tripped breaker), the levers are, in order of preference:

1. **A release change**: lower `myfeeder.interest.sweep-batch-size` (default 50, the max ids enqueued per sweep run) and/or lengthen `myfeeder.interest.sweep-delay` (default `PT2M`) in `application.yaml`, then deploy normally.
2. **An emergency lever without a release**: `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20` (relaxed binding maps `myfeeder.interest.sweep-batch-size` to that env var name). This restarts the pod immediately. Once the throttled value has been encoded into `application.yaml` and deployed, remove the out-of-band override explicitly with `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-` — a Helm upgrade's three-way merge can otherwise **keep** an env var set out of band, so the next deploy does not revert it on its own.

`myfeeder.interest.concurrency` (1) is never part of this lever set and should not be raised to relieve rate-limiting — see above. See [Operations Runbook](../operations/runbook.md) for the full deploy/rollback procedure these commands run inside of.

## Testing

`JevApiClientImplTest` covers the `isConfigured()`/`requireConfigured()` business-rule layer, state cleaning (order preserved, nulls dropped, caller map untouched), question-type validation, and the 401/403 key-free logging. `JevResilienceTest` covers the breaker/retry wiring itself — ignored vs. retried vs. recorded exception types, the Retry-After clamp, and the automatic OPEN→HALF_OPEN transition — through the real AOP proxy rather than by calling `JevApiClientImpl` methods directly (self-invocation would bypass the proxy and prove nothing). `TypeSafeConfigTest` asserts the app-owned bean starts with an absent/blank/set key, that the starter's own bean never activates, and that the main and test YAML pins for model/timeout/`retry.max-retries` match. `JevLiveSmokeTest` is a skipped-by-default live-key check. See [Testing Guide](../testing/guide.md).
