---
type: Integration
title: Jev (TypeSafe) Scoring Integration
description: Documents the outbound integration to the Jev/TypeSafe scoring API that powers interest ranking — the app-owned TypeSafeClient bean, the dedicated Reactor Netty transport, the single-retry-layer design (SDK retries disabled, Resilience4j owns retry+breaker), the scoring executor in front of the client, event logging, and the not-configured/blank-key degrade path.
tags: [integration, jev, typesafe, resilience4j, interest-scoring, external-api]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-25T13:07:33.493Z
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
generated: { by: "openwiki/0.7.2", at: "2026-10-25T13:07:33.493Z" }
---

# Jev (TypeSafe) Scoring Integration

Jev is the external scoring API (accessed via the `org.springaicommunity:spring-ai-starter-typesafe`
SDK) that judges how well an article matches the user's interest profile and topics. This page
documents the integration layer that calls it: the app-owned client bean, its dedicated transport,
the resilience configuration, and the logging/error-mapping around it. For how the scoring sweep
and queue decide *which* articles get sent here, see
[Interest Ranking Workflow](../workflows/interest-ranking.md); for the schema the results are
written into, see [Interest Scoring Domain Model](../domain/interest-scoring.md).

## Entry points

- **`JevApiClient`** (`integration/JevApiClient.java`) is the interface the rest of the app depends
  on. It is deliberately a generic pass-through: it knows nothing about articles or topics, and
  callers build the SDK's `Noul`/`Score` `Question` objects themselves.
  - `isConfigured()` returns whether the TypeSafe key has text. It never throws, never calls Jev,
    and carries no resilience annotations, so a configuration check can never touch the circuit
    breaker (the interest status endpoint and the scorer's eligibility gate both rely on this).
  - `judge(state, questions)` sends one Jev call and returns a `JevJudgment`. Typed SDK exceptions
    (`org.springaicommunity.typesafe.exception.*`) propagate unchanged; once the breaker is wired
    up, `CallNotPermittedException` means the breaker is open.
- **`JevApiClientImpl`** (`integration/JevApiClientImpl.java`) is the only implementation and the
  sole bean carrying `@CircuitBreaker(name = "jev")` + `@Retry(name = "jev")`. Before the HTTP call
  it validates that the key is configured (throwing `JevNotConfiguredException` otherwise), asserts
  every question is a `Noul` or `Score` (a `Choice` question would be billed and then silently
  dropped by `JevJudgment`), and builds the request state as a `LinkedHashMap` copy of the caller's
  map with `null` values removed — the caller's map is never mutated, and an empty state is sent as
  `{}`. `TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException` (401/403) are logged
  with status and request id only — never the exception's message or body, since those can echo
  request content — then rethrown.

No `fallbackMethod` is configured: because typed SDK exceptions and `CallNotPermittedException`
must propagate unchanged to `GlobalExceptionHandler`, there is nothing for a fallback to usefully
rewrite.

## Concurrency control in front of the client: the scoring executor

Calls into `JevApiClient` are never made directly from the sweep thread or HTTP request threads.
`InterestScoringConfig` defines the `interestScoringExecutor` bean (qualifier
`InterestScoringConfig.EXECUTOR`, threads named `jev-score-`): a `ThreadPoolTaskExecutor` whose core
and max pool size both equal `myfeeder.interest.concurrency` (default `1`), with `queue-capacity`
1000. It is registered with `defaultCandidate = false` so Boot's `applicationTaskExecutor` stays the
default `Executor` bean; consumers inject this one explicitly with
`@Qualifier(InterestScoringConfig.EXECUTOR)`. There is no `@Async` anywhere in the scoring path —
work is handed to the executor explicitly.

The default abort rejection policy is kept intentionally. `ScoringQueue` tracks in-flight article
ids in a `ConcurrentHashMap`-backed set and submits `Runnable`s to this executor; when the executor
throws `TaskRejectedException` (queue full), `ScoringQueue` removes the id from the in-flight set
instead of leaving it stuck, so the next sweep run can pick it up and retry — a discarding
rejection policy would have left the id marked in-flight forever with no retry path.

```mermaid
flowchart LR
    Sweep["InterestScoringSweep / ingest listener"] --> Queue["ScoringQueue (in-flight dedup)"]
    Queue -->|execute| Exec["interestScoringExecutor\n(core=max=concurrency, queue 1000)"]
    Exec -->|rejected: queue full| Queue
    Exec --> Scorer["ArticleScoringService.score"]
    Scorer --> Client["JevApiClientImpl.judge\n(@CircuitBreaker + @Retry \"jev\")"]
    Client --> SDK["TypeSafeClient (app-owned bean)"]
    SDK -->|Reactor Netty, Jev-only transport| API[("Jev / TypeSafe API")]
```
*Everything calling Jev funnels through one bounded-queue, single-concurrency executor before reaching the client bean.*

Keeping `concurrency: 1` is a deliberate design decision (see the related runbook throttle levers
below) rather than an accidental default — raising it increases the request rate the breaker and
retry layer must absorb.

## Why the app owns the `TypeSafeClient` bean

`config/TypeSafeConfig` defines the `TypeSafeClient` bean itself instead of letting the TypeSafe
starter auto-configure it:

- The starter's own auto-configured bean asserts that `spring.ai.typesafe.api-key` has text, so a
  blank `MYFEEDER_TYPESAFE_API_KEY` would crash the pod on startup. Defining the bean in
  `TypeSafeConfig` makes the starter's `@ConditionalOnMissingBean` back off; a missing or blank key
  only disables Jev calls (`isConfigured()` returns `false`, and a fixed-text
  `TypeSafe Jev not configured; interest scoring disabled` line is logged at INFO), never the app.
- The API key is read **only** through a `Supplier` passed to `TypeSafeClient.builder().apiKey(...)`
  (`() -> Objects.requireNonNullElse(properties.getApiKey(), "")`), never through the `String`
  overload (which asserts non-blank text at build time) and never logged — not even its length or
  prefix.
- `baseUrl` is set explicitly from `TypeSafeProperties.getBaseUrl()` so the SDK never falls back to
  reading its own `TYPESAFE_*` environment variables, which would otherwise let an unrelated
  environment variable silently redirect Jev traffic.

```java
@Bean
TypeSafeClient typeSafeClient(TypeSafeProperties properties, RestClient.Builder restClientBuilder) {
    if (!StringUtils.hasText(properties.getApiKey())) {
        log.info("TypeSafe Jev not configured; interest scoring disabled");
    }
    ReactorClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.reactor()
            .build(HttpClientSettings.defaults().withTimeouts(JEV_CONNECT_TIMEOUT, properties.getTimeout()));
    return TypeSafeClient.builder()
            .apiKey(() -> Objects.requireNonNullElse(properties.getApiKey(), ""))
            .baseUrl(properties.getBaseUrl())
            .defaultModel(properties.getModel())
            .timeout(properties.getTimeout())
            .retryPolicy(properties.toRetryPolicy())
            .restClientBuilder(restClientBuilder.clone().requestFactory(requestFactory))
            .build();
}
```

## A dedicated transport, never shared or rebuilt

`typeSafeClient(...)` is given a Jev-only Reactor Netty `ClientHttpRequestFactory`, built from
`ClientHttpRequestFactoryBuilder.reactor()` with its own timeouts: **connect 5s**
(`TypeSafeConfig.JEV_CONNECT_TIMEOUT`) and **read = `spring.ai.typesafe.timeout`** (default 30s,
chosen from calibration showing ~2.6s average and >5s cold-start latency for a profile plus 7
topics, with the rubric able to grow to 25 topics). This factory is installed on a `clone()` of the
auto-configured `RestClient.Builder`, not the builder itself — cloning preserves the app's
`RestClientCustomizer` (the `myfeeder/<version>` User-Agent) and any observation instrumentation
without altering the transport of any *other* bean that also consumes the shared, auto-configured
`RestClient.Builder` (feed fetches and the Raindrop client). This is distinct from the timeouts
documented for the regular outbound `RestClient` (`spring.http.clients.connect-timeout`/
`read-timeout`, 5s/30s, used by feed fetches and Raindrop) — Jev's timeouts are configured
independently through `spring.ai.typesafe.timeout`, not those keys.

**Invariant: never build a second `TypeSafeClient` bean, and never reuse this Reactor Netty
transport (or its builder clone) for feed fetches or any other outbound call.** Doing so would
either bypass the not-configured degrade path (a second starter-created bean could assert a blank
key and crash startup) or leak Jev's shorter/longer timeouts onto unrelated traffic.

## Single-retry-layer design

Jev calls pass through exactly one retry layer, by design:

- `spring.ai.typesafe.retry.max-retries: 0` disables the TypeSafe SDK's own built-in retry
  entirely.
- The Resilience4j **`jev` retry** instance is the only retry layer: 3 attempts total, waiting 1s
  then 2s between attempts. The wait function is replaced by `TypeSafeConfig.jevRetryInterval`, a
  `RetryConfigCustomizer` bean that reads the base wait (`resilience4j.retry.instances.jev.wait-duration`,
  1s) via `Binder` (not `@Value Duration`, since `ApplicationContextRunner` has no conversion
  service for `Duration` in tests) and computes the actual wait per attempt:
  - If the failure was a `TypeSafeRateLimitException` (HTTP 429) carrying a server-supplied
    `retryAfterMs`, the wait is that value clamped to `[0, MAX_RETRY_AFTER_MS]` (10 seconds).
  - Otherwise the wait doubles per attempt from the base (`base * 2^(attempt-1)`: 1s, then 2s).
  - It retries only on `TypeSafeRateLimitException` (429), `TypeSafeInternalServerException` (5xx /
    overloaded), and `TypeSafeApiConnectionException` (connection or timeout failures) — the
    `retry-exceptions` list in `application.yaml`. It **never** retries 400
    (`TypeSafeBadRequestException`), 422 (`TypeSafeUnprocessableEntityException`), or 401/403
    (`TypeSafeAuthenticationException`/`TypeSafePermissionDeniedException`) — those are either
    non-retriable client errors or would otherwise burn attempts against a request that can never
    succeed. `JevNotConfiguredException` is also listed under `ignore-exceptions` so a simply
    unconfigured key never burns a retry attempt.

**Warning: never re-enable the SDK's own retry layer.** With both layers active, a single logical
429 would be retried by the SDK *and* by Resilience4j, multiplying the number of requests sent
while the upstream is already rate-limiting — worsening exactly the condition the retry exists to
recover from.

```yaml
spring:
  ai:
    typesafe:
      retry:
        max-retries: 0   # Resilience4j "jev" retry is the only retry layer

resilience4j:
  retry:
    instances:
      jev:
        max-attempts: 3
        wait-duration: 1s   # doubled per attempt by TypeSafeConfig.jevRetryInterval; 429 honors retry-after-ms (clamped to 10s)
        retry-exceptions:
          - org.springaicommunity.typesafe.exception.TypeSafeRateLimitException
          - org.springaicommunity.typesafe.exception.TypeSafeInternalServerException
          - org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
```

## The `jev` circuit breaker

`@CircuitBreaker(name = "jev")` sits **outside** `@Retry(name = "jev")` on `JevApiClientImpl.judge`:
`resilience4j.circuitbreaker.circuit-breaker-aspect-order: 1` and
`resilience4j.retry.retry-aspect-order: 2` (lower = outer), so the breaker wraps the retry and
records exactly **one** outcome per logical call (success or failure after all retries), not one
per HTTP attempt. Without these explicit orders, Resilience4j's defaults would put the retry
outermost instead.

Breaker configuration (`resilience4j.circuitbreaker.instances.jev`):

- `sliding-window-type: COUNT_BASED`, `sliding-window-size: 20`, `minimum-number-of-calls: 10`
- `failure-rate-threshold: 50` (%)
- `slow-call-duration-threshold: 15s`, `slow-call-rate-threshold: 50` (%)
- `wait-duration-in-open-state: 60s`, with `automatic-transition-from-open-to-half-open-enabled:
  true` — OPEN moves to HALF_OPEN after 60s without needing an incoming call, so a sweep that was
  paused because the breaker was open resumes scoring on its own once HALF_OPEN permits calls
  through.
- `permitted-number-of-calls-in-half-open-state: 3`
- `ignore-exceptions`: `JevNotConfiguredException`, `TypeSafeBadRequestException`,
  `TypeSafeUnprocessableEntityException`, `TypeSafeMissingAnswerException`,
  `TypeSafeAnswerTypeException`, `IllegalArgumentException` — none of these count as a breaker
  failure, since they represent either "not configured" (no call was really attempted) or a
  client-side validation/response-shape problem rather than upstream instability.

Critically, **401 (`TypeSafeAuthenticationException`) and 403 (`TypeSafePermissionDeniedException`)
are deliberately absent from `ignore-exceptions`**, so they are recorded as breaker failures. This
means a bad or revoked API key — which returns 401/403 on every call — will open the `jev` breaker
once the failure-rate threshold is crossed, exactly like any other upstream failure, rather than
failing forever with no circuit protection.

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: a semicolon inside a label breaks rendering; rephrase the label. -->
```text
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: failure rate >= 50% or slow-call rate >= 50%\n(window 20, min 10 calls; 401/403 count)
    OPEN --> HALF_OPEN: automatic after 60s
    HALF_OPEN --> CLOSED: enough successes (of 3 permitted calls)
    HALF_OPEN --> OPEN: a call still fails
```
*The `jev` breaker (order 1, outer) wraps the `jev` retry (order 2, inner); `InterestStatusService` surfaces the current state via `CircuitBreakerRegistry.circuitBreaker("jev")` on `GET /api/interest/status`.*

## Error mapping (`GlobalExceptionHandler`)

| Exception | HTTP status | Notes |
|---|---|---|
| `JevNotConfiguredException` | 503 | Fixed app text ("Jev not configured"); key is blank |
| `CallNotPermittedException` | 503 | "Jev unavailable" — the `jev` breaker is OPEN |
| `TypeSafeBadRequestException` / `TypeSafeUnprocessableEntityException` | 422 | "Jev rejected the request (HTTP `<status>`)" |
| any other `TypeSafeException` | 503 | Catch-all: 429, 5xx, 401/403, connection timeouts, missing-answer errors — "The Jev request failed. Try again later." |

Every one of these `ProblemDetail` bodies is fixed app text — the handler never forwards a
TypeSafe exception's own message or response body, since those can echo request content back to a
client.

## Event logging

`config/JevEventLogging` subscribes directly to the `jev` `Retry` and `CircuitBreaker` event
publishers (obtained get-or-create by name from `RetryRegistry`/`CircuitBreakerRegistry` — the same
instances the `@CircuitBreaker`/`@Retry` annotations on `JevApiClientImpl.judge` use) and logs
exactly three fixed-format lines. **No message, response body, or API key ever appears in these
lines — only exception simple class names and numbers:**

- INFO, on each retry attempt:
  `Jev retry attempt {N} after {ExceptionSimpleName} (waiting {M} ms)`
- WARN, once a retryable call has used all its attempts:
  `Jev retries exhausted after {N} attempts: {ExceptionSimpleName}`
- WARN, on every breaker state transition:
  `Jev circuit breaker {FROM}_TO_{TO} (failure rate {x}%, slow-call rate {y}%)` — built from
  `StateTransition.name()` (e.g. `CLOSED_TO_OPEN`), not its prose `toString()`.

Find these in production with:

```bash
kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '
```

These lines are the launch/incident evidence channel for proving the breaker stayed CLOSED during a
backfill and that 429s were being absorbed by the retry, without adding a dedicated metric.

## Configuration reference

```yaml
spring:
  ai:
    typesafe:
      api-key: ${MYFEEDER_TYPESAFE_API_KEY:}   # blank = disabled, app still starts
      model: jev-1.13.0                        # pinned
      timeout: 30s                             # Jev-only read timeout (connect is hardcoded 5s)
      retry:
        max-retries: 0                         # Resilience4j is the only retry layer

myfeeder:
  interest:
    concurrency: 1          # interestScoringExecutor core=max pool size
    queue-capacity: 1000
    sweep-batch-size: 50
    sweep-delay: PT2M
    sweep-initial-delay: PT1M

resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1   # outer
    instances:
      jev: { sliding-window-size: 20, minimum-number-of-calls: 10, failure-rate-threshold: 50,
             slow-call-duration-threshold: 15s, slow-call-rate-threshold: 50,
             wait-duration-in-open-state: 60s, automatic-transition-from-open-to-half-open-enabled: true }
  retry:
    retry-aspect-order: 2             # inner
    instances:
      jev: { max-attempts: 3, wait-duration: 1s }
```

The only required secret is **`MYFEEDER_TYPESAFE_API_KEY`**, which is optional at deploy time: a
blank value disables interest scoring (`isConfigured()` is `false`, the sweep skips every run) but
never prevents the app from starting or serving other features.

## Operational throttle levers

If 429s or breaker trips become a recurring problem, see
[Operations Runbook](../operations/runbook.md) for the full deployment/secret pipeline. The
levers specific to this integration:

- Lower `myfeeder.interest.sweep-batch-size` and/or lengthen `myfeeder.interest.sweep-delay` in
  `application.yaml` for a durable fix.
- For an immediate mitigation without a release, use the emergency `kubectl` env override:
  `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20` (this restarts
  the pod). Once the tuned value lands in yaml, remove the override with
  `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-`, since a later
  Helm upgrade's three-way merge can otherwise keep an out-of-band env var indefinitely.
- For a full emergency stop, redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset — the not-configured
  degrade path takes over cleanly rather than leaving the breaker thrashing against a bad key.
- Keep `myfeeder.interest.concurrency: 1` and never reintroduce the SDK retry layer — both choices
  exist specifically to keep the request rate the `jev` breaker/retry must absorb low.

## Testing this integration

- `JevApiClientImplTest` covers the client's validation (question-type assertions, state
  cleaning), the 401/403 logging-and-rethrow path, and the not-configured check running outside any
  resilience annotation.
- `JevResilienceTest` exercises the `@CircuitBreaker`/`@Retry` wiring on the real AOP proxy — see
  [Testing Guide](../testing/guide.md) for why resilience-annotated methods must be tested through
  the proxy rather than by calling the bean's method directly.
- `TypeSafeConfigTest` covers the app-owned bean: that a blank key does not throw, that the key is
  never exposed through the client's configuration, and the `jevRetryInterval` wait-time
  computation (exponential backoff vs. a clamped `retry-after-ms`).
- `JevLiveSmokeTest` is a live-API smoke test (billed), excluded from the default `./gradlew test`
  run.
