# Files

- [myfeeder Testing Guide](guide.md) - Summarizes the test patterns used across myfeeder's backend (JUnit/Mockito/Testcontainers/WebMvcTest) and frontend (Vitest/React Testing Library), where to find representative tests for each layer, and gotchas that have caused false-positive or brittle tests in the past, including SSRF-guard (FeedUrlValidator), size-capped fetch (FeedFetcher), and reader-view extraction (ArticleExtractionService/ReadingPane) coverage.
