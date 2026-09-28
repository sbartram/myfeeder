---
created: 2026-09-23T03:08:06Z
title: Tune Raindrop resilience
area: backend
severity: minor
files:
  - src/main/resources/application.yaml
  - src/test/resources/application.yaml
---

## Problem

Plan 02-01 added `spring-boot-starter-aspectj` (commit 0e85811) so Resilience4j's `@CircuitBreaker`/`@Retry` aspects activate. Before it, every resilience annotation in the app was inert. This also switched on Raindrop's resilience config, which had never actually run. The user accepted the new behavior on 2026-09-22 (Phase 02 RESEARCH Open Question 1, RESOLVED) and asked for this follow-up to be tracked as a quick task:

- Raindrop calls now retry up to 3 attempts, 1s apart, on **any** exception except `RaindropNotConfiguredException`. `retry.instances.raindrop` has no `retry-exceptions`, and `exponential-backoff-multiplier: 2` does nothing without `enable-exponential-backoff: true`.
- Raindrop's breaker (COUNT_BASED window 10, min 5 calls, 50% failure rate, 30s open) is live. Since plan 04-01 (D-07) the breaker is the outer aspect, so each save records one breaker outcome.
- Failures now surface as `IllegalStateException` → 409 "Raindrop.io is currently unavailable" instead of the raw error.

The remaining CLAUDE.md doc items (Package Structure listing `config/TypeSafeConfig` and the Jev integration classes; `MYFEEDER_TYPESAFE_API_KEY` as an optional deploy.sh variable) belong to Phase 7 OPS-03.

## Solution

1. Raindrop: decide on `retry-exceptions` (e.g. only connection errors and 5xx, not 4xx) and either add `enable-exponential-backoff: true` or remove the inert multiplier. Mirror the change in `src/test/resources/application.yaml`.
