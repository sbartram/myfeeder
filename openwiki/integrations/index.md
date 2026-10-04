# Files

- [Jev (TypeSafe) Scoring Integration](jev.md) - Documents the Jev/TypeSafe AI judging integration that powers interest scoring — the app-owned TypeSafeClient bean, the JevApiClient/JevApiClientImpl split with its own Resilience4j circuit breaker and retry, the single-retry-layer invariant, the bounded scoring executor, and the operational throttle levers used when Jev rate-limits the app.
- [Raindrop.io Integration](raindrop.md) - Documents how myfeeder exports saved articles to Raindrop.io — the RaindropService/RaindropApiClient split, the Resilience4j circuit breaker and retry configuration, the RaindropNotConfiguredException fallback-rethrow pattern, the token-not-in-DB configuration, and the post-save engagement capture.
