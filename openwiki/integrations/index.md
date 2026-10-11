# Files

- [Jev (TypeSafe) Interest-Scoring Integration](jev-scoring.md) - Documents how myfeeder calls the TypeSafe Jev judgment API to score articles against a user's interest profile — the app-owned TypeSafeClient bean, the Resilience4j circuit breaker/retry configuration, the dedicated scoring executor, the Jev event-logging lines, and the operational throttle levers.
- [Raindrop.io Integration](raindrop.md) - Documents how myfeeder exports saved articles to Raindrop.io — the RaindropService/RaindropApiClient split, the Resilience4j circuit breaker and retry configuration, the RaindropNotConfiguredException fallback-rethrow pattern, the API token configuration, and the RAINDROP engagement record that follows a successful save.
