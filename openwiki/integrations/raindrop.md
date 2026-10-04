---
type: Integration
title: Raindrop.io Integration
description: Documents how myfeeder exports saved articles to Raindrop.io — the RaindropService/RaindropApiClient split, the Resilience4j circuit breaker and retry configuration, the RaindropNotConfiguredException fallback-rethrow pattern, the token-not-in-DB configuration, and the post-save engagement capture.
tags: [integration, raindrop, resilience4j, external-api]
verified:
  - by: openwiki/0.7.0
    at: 2026-10-04T13:52:44.431Z
sources:
  - id: openwiki-source-1f9e2cb53a6eac922be73dec
    resource: repo://src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - id: openwiki-source-cef6cf34b0ee266bb62a613f
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java
  - id: openwiki-source-51d12dd31a877d9b5909945d
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java
  - id: openwiki-source-8d62a855cfcce2a529d94a39
    resource: repo://src/main/java/org/bartram/myfeeder/integration/RaindropService.java
  - id: openwiki-source-8c225e8495f0b2dcc9d139b4
    resource: repo://src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
  - id: openwiki-source-0d2b45d6d13f3d35d664203b
    resource: repo://src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java
generated: { by: "openwiki/0.7.0", at: "2026-10-04T13:52:44.431Z" }
---

# Raindrop.io Integration

Raindrop.io is a bookmarking service. myfeeder lets a user push a saved `Article` into a chosen Raindrop collection via `POST /api/articles/{id}/raindrop` (`ArticleController.saveToRaindrop`). This is the only outbound third-party integration besides [Jev](jev.md) (Dropbox/Google Drive export is backlog-only — see `docs/backlog.md` and the Quickstart backlog).

## Why the code is split into `RaindropService` + `RaindropApiClientImpl`

This split exists specifically so that **business validation runs outside the circuit breaker** and only the real HTTP call is wrapped:

- **`RaindropService`** (in `integration/`, but the business-rule layer) looks up the `IntegrationConfig` row for `IntegrationType.RAINDROP`, checks `enabled`, deserializes `RaindropConfig` (which holds the selected `collectionId`), and throws `IllegalStateException` (not configured/disabled, →409) or `IllegalArgumentException` (no collection picked, →400) *before* ever calling the API client. It also exposes `listCollections()`, cached via `@Cacheable("raindrop-collections")` (backed by Redis), so the Settings UI's collection picker doesn't hit Raindrop's API on every render.
- **`RaindropApiClientImpl`** (implements `RaindropApiClient`) owns the actual `RestClient` calls (`listCollections`, `createBookmark`) and carries the `@CircuitBreaker(name = "raindrop")` + `@Retry(name = "raindrop")` annotations. This is a **separate Spring bean** deliberately: Resilience4j's annotations are AOP-proxy-based, so if `RaindropService` called an annotated method on *itself* (self-invocation), the proxy — and the resilience behavior — would be bypassed. Putting the annotated methods on a distinct bean that `RaindropService` calls through avoids that trap.

**Convention for future external integrations:** follow this same pattern — `@CircuitBreaker`/`@Retry` on the API-client bean, not the service, with business-rule checks happening in the service before the client call. [Jev Integration](jev.md) applies the same pattern to a second external integration (`JevApiClient`/`JevApiClientImpl`); see that page for the general shape rather than repeating it here.

## The fallback rethrow trap (and why it matters)

Resilience4j's `@CircuitBreaker` fallback method wraps *any* throwable reaching it into a generic 5xx by default. `RaindropApiClientImpl`'s fallback methods (`listCollectionsFallback`, `createBookmarkFallback`) explicitly `instanceof`-check for `RaindropNotConfiguredException` and rethrow it as-is — because that exception must reach `GlobalExceptionHandler` and become a 503 ("Raindrop not configured"), not get papered over as a 409 "circuit breaker opened" error. Everything else gets wrapped as `IllegalStateException("Raindrop.io is currently unavailable", throwable)` → 409.

For this rethrow to actually take effect, `RaindropNotConfiguredException` is also listed under `ignore-exceptions` for **both** the circuit breaker and retry instances in `application.yaml` — otherwise a simply-unconfigured token would count as a "failure," open the breaker, and burn retry attempts for no reason:

```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1
    instances:
      raindrop:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        ignore-exceptions:
          - org.bartram.myfeeder.integration.RaindropNotConfiguredException
  retry:
    retry-aspect-order: 2
    instances:
      raindrop:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        ignore-exceptions:
          - org.bartram.myfeeder.integration.RaindropNotConfiguredException
```

The breaker is the outer aspect (`circuit-breaker-aspect-order: 1` vs. `retry-aspect-order: 2`), so it records one outcome per logical call, including its retries, not per attempt.

## Configuration and secret handling

The Raindrop **API token is a config property, not a DB value**: `myfeeder.raindrop.api-token` binds to env var `MYFEEDER_RAINDROP_API_TOKEN` (see `MyfeederProperties.Raindrop`; defaults to empty string when unset). `V4__strip_raindrop_api_token.sql` (see [Domain Concepts](../domain/concepts.md)) removed an earlier design where the token lived inside `integration_config.config`'s JSON blob — don't reintroduce that. `RaindropApiClientImpl.requireConfigured()` throws `RaindropNotConfiguredException` whenever the token is blank, which is the trigger for the whole ignore-exceptions/rethrow chain above.

In deployment, the token flows: local env var → `deploy.sh` (`MYFEEDER_RAINDROP_API_TOKEN`, optional — a warning is printed if unset, integration is simply disabled) → Helm `--set secrets.raindropApiToken` → chart secret. See [Operations Runbook](../operations/runbook.md).

## A successful save also records engagement

`RaindropService.saveToRaindrop` calls `engagementStore.recordQuietly(article.getId(), EngagementKind.RAINDROP)` immediately after `raindropApiClient.createBookmark(...)` returns — i.e. only once the bookmark actually exists in Raindrop, and only *after* the breaker/retry-wrapped call has succeeded, never before it and never from inside the `RaindropApiClientImpl` bean itself. Because the client's fallback methods always either rethrow or throw, a failed or breaker-blocked save never reaches this line, so engagement is never recorded for a save that didn't happen.

This call is deliberately outside any transaction boundary: `ArticleEngagementStore.recordQuietly` wraps a single autocommitting `INSERT ... ON CONFLICT DO NOTHING` in a try/catch over `DataAccessException`, logging a WARN (kind, article id, exception class name only — never the message) and swallowing the failure rather than propagating it. The save the user asked for (the Raindrop bookmark itself) must never be rolled back or reported as failed just because the local engagement insert had a problem — see [ArticleEngagementStore](../../src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java) for the invariant and [Interest Scoring Workflow](../workflows/interest-scoring.md) for how a recorded `RAINDROP` engagement later feeds the engagement-learning blend that boosts an article's display score.

## Testing this integration

`RaindropServiceTest` covers the business-rule layer (not-configured/disabled/no-collection paths — all verified via `verifyNoInteractions(engagementStore)` — plus an in-order check that `createBookmark` runs before `engagementStore.recordQuietly` on the happy path) and cache behavior. `RaindropApiClientImplTest` covers the HTTP client (`MockRestServiceServer`) and the not-configured-token throw. See [Testing Guide](../testing/guide.md).
