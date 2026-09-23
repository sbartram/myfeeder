---
created: 2026-09-23T03:08:06Z
title: Tune Raindrop resilience and fix CLAUDE.md AspectJ note
area: backend
severity: minor
files:
  - src/main/resources/application.yaml:33-52
  - CLAUDE.md:47,113,137-138
---

## Problem

Plan 02-01 added `spring-boot-starter-aspectj` (commit 0e85811) so Resilience4j's `@CircuitBreaker`/`@Retry` aspects activate. Before it, every resilience annotation in the app was inert. This also switched on Raindrop's resilience config, which had never actually run. The user accepted the new behavior on 2026-09-22 (Phase 02 RESEARCH Open Question 1, RESOLVED) and asked for this follow-up to be tracked as a quick task:

- Raindrop calls now retry up to 3 attempts, 1s apart, on **any** exception except `RaindropNotConfiguredException`. `retry.instances.raindrop` has no `retry-exceptions`, and `exponential-backoff-multiplier: 2` does nothing without `enable-exponential-backoff: true`.
- Raindrop's breaker (COUNT_BASED window 10, min 5 calls, 50% failure rate, 30s open) is live. Because Retry is the outer aspect, every attempt counts toward the breaker.
- Failures now surface as `IllegalStateException` → 409 "Raindrop.io is currently unavailable" instead of the raw error.

CLAUDE.md is also out of date:
- The Resilience4j convention (line ~137) doesn't say the annotations need `spring-boot-starter-aspectj` (without it they compile and silently do nothing).
- "CircuitBreaker (outer) + Retry (inner)" describes annotation placement, not execution order. Retry is the outer aspect (retry order LOWEST_PRECEDENCE-5, breaker order LOWEST_PRECEDENCE-4), so every attempt counts toward the breaker.
- Package Structure (line ~47) doesn't list the new classes (`config/TypeSafeConfig`, plus the Jev integration classes from plans 02-02/02-03).
- The deploy notes (lines ~113/128) don't mention `MYFEEDER_TYPESAFE_API_KEY` as an optional deploy.sh variable (wired in plan 02-04).

## Solution

1. Raindrop: decide on `retry-exceptions` (e.g. only connection errors and 5xx, not 4xx) and either add `enable-exponential-backoff: true` or remove the inert multiplier. Mirror the change in `src/test/resources/application.yaml`.
2. CLAUDE.md: make the four corrections above. Don't edit it while the user's uncommitted CLAUDE.md edits are pending unless they are committed first.
