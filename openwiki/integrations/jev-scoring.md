---
type: Integration
title: Jev (TypeSafe) Interest-Scoring Integration
description: Documents how myfeeder calls the TypeSafe Jev judgment API to score articles against a user's interest profile — the app-owned TypeSafeClient bean, the Resilience4j circuit breaker/retry configuration, the dedicated scoring executor, the Jev event-logging lines, and the operational throttle levers.
resource: src/main/java/org/bartram/myfeeder/integration
tags: [integration, jev, typesafe, resilience4j, external-api, interest-scoring]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-11T14:50:52.613Z
sources:
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-e5dcb45322e31f835560b357
    resource: repo://helm/myfeeder/templates/app-deployment.yaml
  - id: openwiki-source-488238bd6a4828fc54072530
    resource: repo://src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - id: openwiki-source-0a50732f461f501fbe68e757
    resource: repo://src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - id: openwiki-source-068b4c54848ec0b7c44aa265
    resource: repo://src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java
  - id: openwiki-source-1ccecc8d0cb9715b566fdff9
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - id: openwiki-source-bfd9e6b64553ce72d0555fce
    resource: repo://src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - id: openwiki-source-e10d497b6eb67a010f44960a
    resource: repo://src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
  - id: openwiki-source-4805f4a78501939aafe493fa
    resource: repo://src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
generated: { by: "openwiki/0.7.2", at: "2026-10-11T14:50:52.613Z" }
---

# Jev (TypeSafe) Interest-Scoring Integration

Jev is TypeSafe's judgment API (`org.springaicommunity:spring-ai-starter-typesafe`, pinned to an explicit `typesafeVersion` outside the Spring AI BOM). myfeeder calls it to score each article against the user's interest profile and topics, producing the noul/score judgments the [Interest Scoring Workflow](../workflows/interest-scoring.md) blends into a priority rank. This page covers the client/transport, resilience configuration, the dedicated scoring executor, event logging, and the throttle levers an operator has at runtime. It follows the same `@CircuitBreaker`/`@Retry`-on-the-API-client-bean pattern as [Raindrop.io Integration](raindrop.md) — read that page for the general convention this one specializes.

## Client: `JevApiClient` / `JevApiClientImpl`

`JevApiClient` is a thin, generic pass-through interface with two methods:

- `isConfigured()` — true when the TypeSafe key has text. It never calls Jev and is **not** wrapped by the circuit breaker or retry (checking configuration must never count as a breaker call). Both `/api/interest/status` and the scorer's eligibility gate read this as the single "configured" rule.
- `judge(Map<String, ?> state, Map<String, ? extends Question> questions)` — sends one Jev call. The implementation knows nothing about articles or topics; callers build the SDK `Noul`/`Score` questions themselves. State is sent as a JSON object in the caller's iteration order with null values dropped (an empty state is sent as `{}`), and the caller's map is never mutated.

`JevApiClientImpl.judge` carries the resilience annotations:

```java
@CircuitBreaker(name = "jev")
@Retry(name = "jev")
@Override
public JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions) { ... }
```

There is no `fallbackMethod`: typed SDK exceptions (`org.springaicommunity.typesafe.exception.*`) and `CallNotPermittedException` (breaker open) propagate unchanged, so `GlobalExceptionHandler` maps them directly — `JevNotConfiguredException` → 503 ("Jev not configured"), `CallNotPermittedException` → 503 ("Jev unavailable"), `TypeSafeBadRequestException`/`TypeSafeUnprocessableEntityException` → 422 ("Jev rejected the request"), any other `TypeSafeException` → 503 ("Jev request failed"). These messages are fixed text and never echo a TypeSafe response body. `TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException` (401/403) are logged with status and request id only before rethrowing — never the message or body, which can echo request details.

Only `Noul` and `Score` questions are accepted; a `Choice` question throws `IllegalArgumentException` before the HTTP call (would otherwise be billed and then silently dropped building `JevJudgment`). That `IllegalArgumentException` is deliberately listed in the breaker's `ignore-exceptions` (see below) so a caller bug never opens the breaker.

## `TypeSafeConfig`: the app-owned `TypeSafeClient` bean

`config/TypeSafeConfig` defines the `TypeSafeClient` bean itself, rather than letting the `spring-ai-starter-typesafe` auto-configuration create it:

- **Why**: the starter's own bean asserts `spring.ai.typesafe.api-key` has text and throws at startup otherwise. Defining the bean in `TypeSafeConfig` makes the starter's `@ConditionalOnMissingBean` back off, so a missing or blank `MYFEEDER_TYPESAFE_API_KEY` only disables Jev calls — it never crashes the pod. When the key is blank, `TypeSafeConfig` logs the fixed line `TypeSafe Jev not configured; interest scoring disabled` (never the key, its length, or its prefix).
- The key reaches the SDK only through the **Supplier** overload — `.apiKey(() -> Objects.requireNonNullElse(properties.getApiKey(), ""))` — because the String overload asserts non-blank text at build time and a blank key must be legal there.
- `baseUrl`, `defaultModel`, `timeout` and `retryPolicy` are all set explicitly from `TypeSafeProperties`, so the SDK never falls back to its own `TYPESAFE_*` environment variables.
- **Jev-only transport**: the bean gets a dedicated Reactor Netty request factory (`ClientHttpRequestFactoryBuilder.reactor()`), connect timeout 5s (`JEV_CONNECT_TIMEOUT`), read timeout = `spring.ai.typesafe.timeout` (30s in main `application.yaml`). This factory is set on a `clone()` of the auto-configured `RestClient.Builder`, which keeps the shared `RestClientCustomizer` (the `myfeeder/<version>` User-Agent, see `CLAUDE.md`) without changing the transport of any other bean that shares that builder — in particular, feed fetches and the Raindrop client keep the globally-configured `spring.http.clients.connect-timeout`/`read-timeout` (5s/30s) via the default Reactor Netty transport.
- **Invariant**: never build a second `TypeSafeClient` bean, and never reuse this bean's transport or `RestClient.Builder` for feed fetching or any other outbound call. The Jev-only timeouts exist specifically because a profile-plus-many-topics call can take several seconds (calibration measured ~2.6s average, >5s cold for a profile plus 7 topics, and the rubric can grow to 25 topics) — reusing them elsewhere would either starve Jev or over-extend an unrelated call.

## Single retry layer

Jev calls are retried exactly once, at the Resilience4j layer — the SDK's own retry is explicitly disabled:

```yaml
spring:
  ai:
    typesafe:
      retry:
        max-retries: 0
```

The Resilience4j `jev` retry instance is the only retry:

```yaml
resilience4j:
  retry:
    instances:
      jev:
        max-attempts: 3
        wait-duration: 1s
        retry-exceptions:
          - org.springaicommunity.typesafe.exception.TypeSafeRateLimitException
          - org.springaicommunity.typesafe.exception.TypeSafeInternalServerException
          - org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
```

- 3 attempts total, waiting 1s then 2s by default (doubling), via the `jevRetryInterval` `RetryConfigCustomizer` bean in `TypeSafeConfig`.
- A 429 (`TypeSafeRateLimitException`) with a server-supplied `retryAfterMs` is honored instead of the exponential backoff, **clamped to [0, `MAX_RETRY_AFTER_MS`]** (10 seconds, `TypeSafeConfig.MAX_RETRY_AFTER_MS`, D-07).
- Only `TypeSafeRateLimitException` (429), `TypeSafeInternalServerException` (5xx/overloaded) and `TypeSafeApiConnectionException` (connection/timeout failures) are retried. `TypeSafeBadRequestException`/`TypeSafeUnprocessableEntityException` (400/422) and the authentication exceptions (401/403) are never retried.
- `JevNotConfiguredException` is ignored by the retry (and the breaker, below), so a simply-unconfigured key never burns retry attempts or opens the breaker.

**Never re-enable the SDK retry layer** (`spring.ai.typesafe.retry.max-retries` must stay 0): two retry layers stacked would multiply the number of requests sent after a 429, making rate-limiting worse instead of better.

## Circuit breaker: `jev`

```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1   # outer aspect
    instances:
      jev:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        slow-call-duration-threshold: 15s
        slow-call-rate-threshold: 50
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
          - org.springaicommunity.typesafe.exception.TypeSafeBadRequestException
          - org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException
          - org.springaicommunity.typesafe.exception.TypeSafeMissingAnswerException
          - org.springaicommunity.typesafe.exception.TypeSafeAnswerTypeException
          - java.lang.IllegalArgumentException
  retry:
    retry-aspect-order: 2             # inner aspect
```

- COUNT_BASED window of 20 calls, minimum 10 calls before the failure rate is evaluated, opens at a 50% failure rate or a 50% slow-call rate (calls over 15s).
- Stays OPEN for 60s, then automatically transitions to HALF_OPEN without needing a call (`automatic-transition-from-open-to-half-open-enabled: true`) — a paused sweep resumes on its own once the window elapses.
- `circuit-breaker-aspect-order: 1` is lower than `retry-aspect-order: 2`, so the breaker is the **outer** aspect: it records one outcome per logical call (including all retry attempts inside), not one per HTTP attempt.
- **401/403 (`TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException`) are deliberately absent from `ignore-exceptions`** — they are recorded as failures, so a bad or revoked API key opens the breaker rather than silently retrying forever.
- Business/validation exceptions that are never the API's fault (`JevNotConfiguredException`, 400/422 TypeSafe exceptions, missing-answer/wrong-answer-type exceptions, and the caller-bug `IllegalArgumentException` from an unsupported question type) are ignored so they never count toward the failure rate or open the breaker.

Both the `jev` retry and breaker instances come from the Resilience4j registries by name (`circuitBreaker("jev")` / `retry("jev")`), the same instances the `@CircuitBreaker`/`@Retry` annotations on `JevApiClientImpl.judge` use.

## Event logging: `JevEventLogging`

`config/JevEventLogging` subscribes to the `jev` retry and breaker event publishers at construction (get-or-create by name, so it shares the exact registry instances the annotated method uses) and logs three fixed-format lines. Each carries only the exception's **simple class name** and numbers — **never an exception message, a response body, or the API key**:

| Event | Level | Format |
|---|---|---|
| Retry attempt | INFO | `Jev retry attempt {N} after {ExceptionSimpleName} (waiting {M} ms)` |
| Retries exhausted | WARN | `Jev retries exhausted after {N} attempts: {ExceptionSimpleName}` |
| Breaker state transition | WARN | `Jev circuit breaker {FROM}_TO_{TO} (failure rate {x}%, slow-call rate {y}%)` |

The transition line uses `StateTransition.name()` (e.g. `CLOSED_TO_OPEN`), not its prose `toString()`.

To grep these lines in production:

```bash
kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '
```

These lines are the primary diagnosis channel for 429 storms and breaker trips without needing a dashboard or metric — they were also the rollout-watch evidence that the breaker stayed CLOSED and that 429s were absorbed by retry during the initial backfill.

## Scoring executor and backpressure

Interest scoring runs off a dedicated, non-default executor bean so it never competes with or blocks Boot's `applicationTaskExecutor`:

```java
@Bean(name = EXECUTOR, defaultCandidate = false)
public ThreadPoolTaskExecutor interestScoringExecutor(MyfeederProperties properties) {
    ...
    executor.setCorePoolSize(interest.getConcurrency());   // 1
    executor.setMaxPoolSize(interest.getConcurrency());    // 1
    executor.setQueueCapacity(interest.getQueueCapacity()); // 1000
    executor.setThreadNamePrefix("jev-score-");
    return executor;
}
```

- Bean name `interestScoringExecutor` (`InterestScoringConfig.EXECUTOR`); consumers inject it with `@Qualifier(InterestScoringConfig.EXECUTOR)`. There is no `@Async` anywhere — work is handed to this executor explicitly.
- `defaultCandidate = false` keeps it from satisfying Spring Boot's `@ConditionalOnMissingBean(Executor.class)`, which would otherwise silently remove Boot's own `applicationTaskExecutor`.
- **Core = max pool size = `myfeeder.interest.concurrency`, fixed at 1 by design (D-07)**: Jev scoring is deliberately single-threaded so it never competes for rate-limit headroom with itself, and so the breaker's outcome accounting stays simple (one call in flight at a time). Never raise this without re-reviewing D-07.
- `queue-capacity` is 1000 (`myfeeder.interest.queue-capacity`).
- The thread pool's **default abort rejection policy is kept intentionally**: when the queue is full, `execute()` throws `TaskRejectedException` rather than silently discarding the task. `ScoringQueue.submit` catches that exception, releases the article id from its in-flight set, and counts it as dropped (logged as a WARN with the count only) — "the sweep will retry them" on the next pass. A discarding policy would instead leave the id marked in-flight forever with no scorer ever running it.
- `ScoringQueue` also deduplicates: an article id already queued or running is not resubmitted, and `remainingCapacity()` (free queue slots) is what `InterestScoringSweep` uses to cap how many ids it enqueues per run — this is the queue-side half of the sweep's own batch-size throttle (see below).

## Throttle levers

Two operational levers bound how fast myfeeder calls Jev, both under `myfeeder.interest` in `application.yaml`:

- **`sweep-batch-size`** (default 50) — `InterestScoringSweep` enqueues at most `min(free queue room, sweep-batch-size)` eligible articles per run, newest first.
- **`sweep-delay`** (default `PT2M`, with a `sweep-initial-delay` of `PT1M` after startup) — how often `InterestScoringSweep.sweep()` runs.

If 429s or breaker trips become a recurring problem (D-08), lower `sweep-batch-size` and/or lengthen `sweep-delay` in `application.yaml` and redeploy through the normal release pipeline (see [Operations Runbook](../operations/runbook.md) for the full cut-a-release/deploy sequence).

**Emergency lever without a release** — Spring's relaxed environment-variable binding lets you override either property directly on the running Deployment:

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20
```

This restarts the pod immediately (a `set env` always rolls the Deployment). Once the chosen value has been committed into `application.yaml` and deployed normally, **remove the out-of-band override explicitly**:

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-
```

This step matters because Helm's three-way merge does not necessarily clear an env var that was set out-of-band via `kubectl set env` but is absent from the chart's rendered manifest — a stale override can silently survive a later `helm upgrade` if it isn't removed first.

**Emergency stop**: redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset. A blank key makes `TypeSafeConfig`'s bean build successfully but `isConfigured()` return false, so both the sweep and the on-ingest scoring path no-op without crashing the app. See [Operations Runbook](../operations/runbook.md) for how `deploy.sh` and the Helm chart treat `MYFEEDER_TYPESAFE_API_KEY` as an optional secret (a deploy without it succeeds with a warning, and changing only the key rolls the pod via a `checksum/secret` pod-template annotation) — the same optional-secret pattern Raindrop's token follows (see [Raindrop Integration](raindrop.md#configuration-and-secret-handling)).

Keep `concurrency: 1` (D-07) and never re-enable the SDK retry layer as a throttling measure — both decisions are structural, not tuning knobs.

## Related

- [Interest Scoring Workflow](../workflows/interest-scoring.md) — the business pipeline that calls `JevApiClient.judge`, consumes `JevJudgment`, and blends scores into the priority rank; eligibility windowing, cold start, and the sweep's gating conditions (unconfigured / breaker OPEN / cold start) live there.
- [Raindrop.io Integration](raindrop.md) — the sibling external integration this client's `@CircuitBreaker`/`@Retry`-on-the-API-client-bean pattern and optional-secret deploy flow follow.
- [Operations Runbook](../operations/runbook.md) — the deploy-time secret flow (`deploy.sh`, Helm `--set`, the `myfeeder-typesafe-api-key` Secret key) and the general release/deploy pipeline used to ship a tuned throttle value.
- [Testing Guide](../testing/guide.md) — backend test patterns; `JevResilienceTest` exercises the retry/breaker configuration and `TypeSafeConfigTest` exercises the app-owned bean and timeout wiring.
