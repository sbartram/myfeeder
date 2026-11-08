# Files

- [Jev (TypeSafe) Scoring Integration](jev.md) - Documents the Jev/TypeSafe AI client used for interest scoring — the app-owned TypeSafeClient bean, its single-retry-layer resilience configuration and circuit-breaker tuning, structured event logging, and the exception-to-HTTP-status mapping at the transport/resilience boundary.
- [Raindrop.io Integration](raindrop.md) - Documents how myfeeder exports saved articles to Raindrop.io — the RaindropService/RaindropApiClient split, the Resilience4j circuit breaker and retry configuration, the RaindropNotConfiguredException fallback-rethrow pattern, post-call engagement recording, and how the API token is configured without being stored in the database.
