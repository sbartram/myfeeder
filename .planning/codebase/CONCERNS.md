---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
# Codebase Concerns

**Analysis Date:** 2026-09-22

## Tech Debt

**FeedPanel.tsx Component Size:**

- Issue: Component has grown to 430 lines and mixes concerns: feed tree rendering, folder management, drag-and-drop, context menus, file imports, unsubscription dialogs, and folder creation
- Files: `src/main/frontend/src/components/FeedPanel.tsx`
- Impact: Component becomes harder to test, maintain, and reason about; tight coupling between drag-and-drop logic and folder state management; context menu state cascades through the component
- Fix approach: Extract nested `SortableFolder` component and context menu dialog into separate components; move state management into custom hooks (e.g., `useFeedContextMenu`, `useFolderDragDrop`); isolate import/export logic into separate modal

**Spring Data JDBC N+1 Queries:**

- Issue: Spring Data JDBC lacks lazy loading, so eager fetches without explicit joins can trigger N+1 queries (e.g., fetching articles without joining feeds, fetching boards with their articles)
- Files: `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java`, `src/main/java/org/bartram/myfeeder/repository/BoardRepository.java`, and dependent services
- Impact: Performance degradation when listing articles for multiple feeds or boards with articles; latency increases linearly with data volume
- Fix approach: Audit all repository queries for N+1 patterns; use explicit `@Query` joins; consider fetch strategies for paginated endpoints; add query logging in tests to catch regressions

**FeedPollingScheduler Race Condition:**

- Issue: `pollAndAdjust` checks feed error state and replaces the scheduled task, but `registerFeed`/`cancelFeed` can be called concurrently (e.g., from event listeners or external API). Between the read and the task replacement, a feed could be edited or deleted, causing redundant polling until the next scheduled run
- Files: `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java` lines 79-103
- Impact: Worst case: feed polled multiple times in rapid succession if it's edited while a poll is in flight; acceptable for single-user deployment, but a potential issue if multi-user support is added
- Fix approach: Add synchronized block around the critical section in `pollAndAdjust`; or use a striped lock per feed ID to allow concurrent adjustment of different feeds; add test that reproduces the race

## Known Bugs

**JSON Feed Date Parse Failure:**

- Symptoms: If a JSON Feed item has a malformed `date_published` (invalid ISO 8601), `Instant.parse()` throws an uncaught exception
- Files: `src/main/java/org/bartram/myfeeder/parser/FeedParser.java` lines 220-224 (`parseJsonItem` method)
- Trigger: Subscribe to a JSON Feed with a date-like field that doesn't parse (e.g., `"date_published": "Jan 2025"`)
- Workaround: Feed will fail to parse entirely (mapped to 422). User must fix the feed or the feed source must correct the date
- Fix approach: Wrap `Instant.parse()` in try-catch in `parseJsonItem`, log the error, set `publishedAt` to null and continue

**FeedParser Rejects Valid Low-Content Feeds:**

- Symptoms: A feed with a title but no articles, or articles but no title, will parse successfully and return the ParsedFeed. However, if BOTH are missing (title == null or blank AND no articles), the parse throws `FeedParseException` (mapped to 422)
- Files: `src/main/java/org/bartram/myfeeder/parser/FeedParser.java` lines 52-57
- Trigger: A feed that returns 200 OK with a feed element but no content — could be legitimate during feed setup or error pages that partially match feed structure
- Workaround: Feed subscription fails; user must subscribe to a different URL
- Fix approach: Consider relaxing the check to allow feeds with a title OR articles, not requiring both; or add a per-feed override flag

## Security Considerations

**SSRF: DNS Rebinding / TOCTOU in FeedUrlValidator:**

- Risk: `FeedUrlValidator.validate()` resolves the hostname once and checks the IP is public, but `RestClient.fetch()` resolves the hostname again when it connects. A malicious or misconfigured DNS server could flip the record between the two lookups, causing the validator to pass a public address but the fetch to connect to a private one (e.g., `127.0.0.1`, internal database, cloud metadata endpoint)
- Files: `src/main/java/org/bartram/myfeeder/service/FeedUrlValidator.java` (known limitation documented in lines 19-26)
- Current mitigation: Documentation acknowledges the issue; accepted risk for single-user LAN deployment
- Recommendations: For production multi-user deployment, implement DNS pinning by using a custom DNS resolver / socket factory in `RestClient` that reuses the validated IP; or use a DNS-over-HTTPS (DoH) provider with signed responses

**Redirect Re-validation Gap:**

- Risk: `FeedFetcher.fetch()` validates the initial URL via `FeedUrlValidator`, but HTTP redirects (30x) are not re-validated. A permitted feed URL could redirect to a blocked private address (e.g., `http://example.com → http://192.168.1.1/admin`)
- Files: `src/main/java/org/bartram/myfeeder/service/FeedFetcher.java` (fetch via `RestClient` which auto-follows redirects by default)
- Current mitigation: None
- Recommendations: Disable auto-redirect in `RestClient` and manually validate each hop, or add a hop limit with validation callback

**API Token Persistence Risks:**

- Risk: `MYFEEDER_RAINDROP_API_TOKEN` and `SPRING_AI_ANTHROPIC_API_KEY` are stored in Helm secrets and injected as env vars; if a pod is killed uncleanly or its memory dumped, the token could be exposed. The application does not log or echo these values, which is good, but they exist in the JVM memory and in the Kubernetes secret object
- Files: `helm/myfeeder/templates/app-secret.yaml`, `helm/myfeeder/templates/app-deployment.yaml` (env vars injected from secrets)
- Current mitigation: Helm secrets use base64 encoding (not encryption); tokens are marked `SENSITIVE` in the env vars (by Spring Boot convention)
- Recommendations: Use Kubernetes secret encryption at rest (`etcd` encryption); rotate tokens regularly; consider using a secrets injection sidecar (e.g., HashiCorp Vault) for production

## Performance Bottlenecks

**Unbounded Article Batch Insert:**

- Problem: `FeedPollingService` parses all articles into memory before bulk inserting; for very large feeds (100K+ articles), this could exhaust heap even though the feed body is capped at 10 MB
- Files: `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java` (not read in detail, but likely in bulk upsert logic)
- Cause: ParsedArticle list grows unbounded; no streaming or batching within the insert operation
- Improvement path: Stream articles from the parser; batch insert in chunks of 1000 articles; add a per-feed article cap (e.g., keep only the last 10K articles)

**Cursor Pagination Composite Key Lookup:**

- Problem: Paginated article list uses `(published_at, id)` composite comparison for cursor pagination, but the cursor is stored as a single article ID. Each paginated request must re-fetch the cursor article to look up its publication date
- Files: `src/main/frontend/src/hooks/useArticles.ts`, backend article fetch logic (not read)
- Cause: Cursor pagination with sorted results requires knowledge of the sort key value at the cursor position
- Improvement path: Store the cursor as `base64(published_at, id)` tuple instead of just ID; or embed the sort key in the cursor opaquely

**Cache Invalidation on Raindrop Collections:**

- Problem: `RaindropService.listCollections()` caches results indefinitely (only cache unless empty); if a user adds a collection in Raindrop.io, myfeeder won't see it until the cache is manually cleared or the pod restarts
- Files: `src/main/java/org/bartram/myfeeder/integration/RaindropService.java` lines 47-51
- Cause: `@Cacheable` with no TTL
- Improvement path: Add explicit cache TTL (e.g., 5 minutes); expose a refresh endpoint; or use event-driven invalidation when a collection is selected/added

## Fragile Areas

**ArticleExtractionService Content Fetch Chain:**

- Files: `src/main/java/org/bartram/myfeeder/service/ArticleExtractionService.java`
- Why fragile: Extraction is a multi-step chain (fetch page → decode HTML → parse with Readability4J → sanitize on frontend). Any step can fail, and the error handling propagates exceptions rather than gracefully degrading. For example, Readability4J might extract malformed or empty content even on a valid page
- Safe modification: When adding charset handling changes, test against real-world feeds and pages with edge-case encodings (UTF-8 BOM, Windows-1252, ISO-8859-1); add integration tests with sample pages from news sites
- Test coverage: `ArticleExtractionServiceTest` exists (103 lines), but may not cover all failure modes (e.g., pages with no readable content, encoding errors, invalid HTML)

**FeedParser Image URL Extraction:**

- Files: `src/main/java/org/bartram/myfeeder/parser/FeedParser.java` lines 146-187
- Why fragile: Image extraction tries multiple sources (Media RSS, enclosures, HTML regex) and returns the first match. The regex `IMG_SRC_PATTERN` is case-insensitive but doesn't handle escaped quotes or malformed HTML; Readability4J and jsoup parsing could silently ignore malformed pages. If an image URL is relative (no protocol), it's stored as-is and will fail to load on the frontend
- Safe modification: Test regex with malformed HTML samples; validate extracted image URLs are absolute before storing; add fallback to feed's site_url for relative image URLs
- Test coverage: `FeedParserTest` (251 lines) tests happy paths and edge cases, but may not cover all HTML malformations

**Zustand Preferences Store Migration:**

- Files: `src/main/frontend/src/stores/preferencesStore.ts`
- Why fragile: Although a `merge` function exists (lines 63-67) that applies defaults, it relies on the order of spread operations. If a new preference is added and an existing user's stored state is missing it, the merge logic depends on the defaults being spread after the persisted state. The current code is safe (defaults spread after persisted, so missing fields get defaults), but future refactors could break this inadvertently
- Safe modification: Document the merge function contract in comments; consider using a versioned schema with an explicit migration function rather than relying on spread order
- Test coverage: No explicit test of the Zustand store or its merge logic; recommend adding `preferencesStore.test.ts` to verify merge behavior

**React Query Stale-While-Revalidate Edge Case:**

- Files: `src/main/frontend/src/hooks/useArticles.ts`, `useFeeds.ts`, `useBoards.ts`
- Why fragile: TanStack Query's default `staleTime` is 0 (immediately stale), so rapid navigation between feeds can trigger cascading refetches. Combined with cursor pagination, this can cause the article list to jump or show inconsistent data if the server's sort order changes between requests
- Safe modification: Audit useArticles hook for staleTime and gcTime settings; consider increasing staleTime to 30s for paginated lists; add tests that mock rapid navigation and verify the UI doesn't flicker
- Test coverage: Individual query hooks have tests, but multi-hook integration scenarios (navigating between feeds) may not be covered

## Scaling Limits

**PostgreSQL Connection Pool:**

- Current capacity: `spring.datasource.hikari.maximum-pool-size` defaults to 10 in Spring Boot; under load (e.g., 10 concurrent requests each holding a connection), the pool exhausts
- Limit: At 100+ concurrent requests, connection pool blocks and timeouts occur
- Scaling path: Increase `maximum-pool-size` in `application.yaml` (e.g., to 30 for Kubernetes); use a connection proxy (PgBouncer) for multi-pod deployments; optimize queries to release connections faster (see N+1 above)

**Redis Memory for Cached Feeds:**

- Current capacity: Local Redis in Helm chart has no explicit memory limit; uses host available memory (typically 2-8 GB in Kubernetes)
- Limit: As the number of feeds and articles grows, cache entries (articles, extracted content, Raindrop collections) consume memory; eviction is LRU-based, but cache misses on reload could spike DB queries
- Scaling path: Set Redis `maxmemory` policy in Helm values; switch to an external Redis cluster (AWS ElastiCache, Redis Cloud) for multi-pod deployments; consider time-based eviction for rarely-accessed feeds

**Polling Task Concurrency:**

- Current capacity: `FeedPollingScheduler` registers one task per feed, each calling `pollFeed` sequentially. With 1000+ feeds, polling 15 threads polling all feeds takes ~1000 minutes (if each poll is 1ms). Tasks run in the Spring scheduler thread pool (default size ~10-20)
- Limit: At ~500+ feeds, some feeds fall behind their poll interval because the scheduler thread pool is saturated
- Scaling path: Increase `spring.task.scheduling.pool.size` in `application.yaml`; switch to a distributed job queue (e.g., Quartz, Spring Cloud Task) for multi-pod polling; implement adaptive polling (boost active feeds, defer dormant ones)

## Dependencies at Risk

**ROME 2.1.0 & rome-modules 2.1.0:**

- Risk: ROME is a mature library but not actively maintained (last release 2.1.0 in 2023). Media RSS and iTunes module namespaces are version-specific; a feed using a new namespace (GeoRSS 2.x, PodCast 1.1) may not parse correctly
- Impact: New feed formats fail silently or throw parse exceptions; users must wait for ROME maintainer updates (rare)
- Migration plan: Monitor ROME releases; if stuck on 2.1.0 for >2 years, consider forking or switching to a more active RSS parser (e.g., Xstream-based custom parser); add feed format version detection to catch unsupported namespaces early

**Readability4J 1.0.8:**

- Risk: Not actively maintained (last release in 2020); relies on jsoup which is maintained but may diverge from the Readability.js algorithm
- Impact: Extraction quality could degrade on modern websites (e.g., shadow DOM content, lazy-loaded images); false negatives (no readable content found) increase
- Migration plan: Test extraction periodically against popular news sites; if quality drops significantly, fork or reimplement using Mozilla's Readability.js directly (via GraalVM or a Node.js microservice)

**Spring AI 2.0.0-M2:**

- Risk: Milestone release (not GA); breaking changes are possible in minor updates; Anthropic API is stable but Spring AI wrapper may have bugs
- Impact: Unexpected behavior changes on dependency upgrades; incompatible model changes (e.g., Claude 3 → Claude 4 parameter changes)
- Migration plan: Track Spring AI release notes closely; test thoroughly on minor version bumps; consider pinning to a stable GA release once available; wrap Spring AI calls in an abstraction layer to ease future swaps

**Resilience4j 2.x Annotations:**

- Risk: Resilience4j configuration is in `application.yaml` but not version-managed in `build.gradle.kts` (pulled via Spring Cloud BOM); BOM updates could introduce incompatible configuration schema
- Impact: Deployment fails if BOM upgrades resilience4j and config schema changes (e.g., renamed fields, new required fields)
- Migration plan: Lock Spring Cloud BOM to a specific version; audit resilience4j release notes before BOM upgrades; consider externalizing circuit breaker config to a separate file with a schema validator

## Missing Critical Features

**No Circuit Breaker on Feed Fetches:**

- Problem: `FeedFetcher` is not wrapped in a circuit breaker, so if a feed provider's domain goes down, all polling requests to that domain will timeout and block scheduler threads
- Blocks: Aggressive backoff for offline domains; recovery of scheduler threads
- Recommendation: Add a per-domain circuit breaker to FeedFetcher (e.g., `@CircuitBreaker(name = "feed-fetch-#{#url}")` where name is derived from the domain); fallback to cached version with a warning badge in the UI

**No Duplicate Feed Detection:**

- Problem: Users can subscribe to the same feed URL twice (or the same feed via different URLs that resolve to the same articles)
- Blocks: Inability to merge duplicate feeds; UI clutter; potential duplicate articles if the URLs are slightly different
- Recommendation: Add a unique constraint on normalized feed URLs; implement URL normalization (trim, lowercase, remove trailing slash); or add a merge endpoint that combines two feeds

**No Feed Update Notifications:**

- Problem: When a feed's title, description, or type changes, users aren't notified; the UI silently updates
- Blocks: Users may not realize a feed has changed ownership or moved
- Recommendation: Add an update log in the feed details; show a badge when a feed's metadata has changed since the last view

## Test Coverage Gaps

**Java DTOs and Model Classes Not Tested:**

- What's not tested: Request DTOs (e.g., `SubscribeRequest`, `FeedUpdateRequest`, `CreateFolderRequest`) are not instantiated or validated in tests; model classes (e.g., `Feed`, `Article`, `Board`) lack unit tests even though they have business logic (e.g., `Feed.getErrorCount()`, `Article.isRead()`)
- Files: `src/main/java/org/bartram/myfeeder/controller/*Request.java`, `src/main/java/org/bartram/myfeeder/model/*.java`
- Risk: Subtle bugs in constructors, setters, or comparators in models; JSON serialization issues in DTOs
- Priority: Medium (models are simple data classes, but edge cases can hide in constructors or custom logic)

**Frontend Hooks and API Clients Not Fully Tested:**

- What's not tested: `useArticles`, `useBoards`, `useFeeds`, `useUnreadFeedNavigation`, `useMarkAllReadInFeed` hooks lack tests; API client layer in `src/main/frontend/src/api/*.ts` (feeds, articles, boards, folders, opml, integrations) is only partially tested (client.test.ts exists but may not cover all methods)
- Files: `src/main/frontend/src/hooks/useArticles.ts`, `useBoards.ts`, `useFeeds.ts`, `useUnreadFeedNavigation.ts`, `useMarkAllReadInFeed.ts`, `src/main/frontend/src/api/*`
- Risk: Query mutations fail silently; stale data scenarios not caught; API contract changes break the frontend without warning
- Priority: High (hooks are critical to data flow; mutations like `useUpdateArticleState` have no test coverage)

**Frontend Components Without Tests:**

- What's not tested: `ShortcutOverlay`, `BoardManager`, `BoardArticleList`, `Toast`, `EmptyState`, `AddFeedDialog` components have no test files
- Files: `src/main/frontend/src/components/{ShortcutOverlay,BoardManager,BoardArticleList,Toast,EmptyState,AddFeedDialog}.tsx`
- Risk: Keyboard shortcut mishandling (ShortcutOverlay), board management UI bugs (BoardManager), empty state messages not shown correctly
- Priority: Medium-High (ShortcutOverlay is critical for usability; BoardManager and AddFeedDialog are common user flows)

**RetentionService Not Tested:**

- What's not tested: The `@Scheduled` cron job that deletes old articles and extracted content based on retention policy
- Files: `src/main/java/org/bartram/myfeeder/service/RetentionService.java`
- Risk: Retention job silently fails or deletes too much/too little data; no way to verify the cron schedule is correct
- Priority: Medium (data loss risk if retention is too aggressive)

**Resilience4j Fallback Paths Not Fully Exercised:**

- What's not tested: Circuit breaker fallback methods in `RaindropApiClientImpl` (lines 69-84) are tested via `RaindropServiceTest`, but not all exception paths and degradation scenarios
- Files: `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`, tests in `RaindropServiceTest.java`
- Risk: Fallback returns wrong error code (e.g., 500 instead of 503 for unavailable service); circuit breaker state transitions are wrong
- Priority: Medium (resilience patterns are critical but well-documented in the code)

**Article Pagination Edge Cases:**

- What's not tested: Cursor pagination boundary conditions (e.g., cursor is the last article, cursor is deleted, sort order changes mid-pagination)
- Files: `src/main/java/org/bartram/myfeeder/service/ArticleService.java` (pagination logic), `src/main/frontend/src/hooks/useArticles.ts` (pagination fetch)
- Risk: Duplicate articles returned, articles skipped in pagination, cursor validation fails silently
- Priority: High (pagination is core functionality; skipped articles would be noticed by users)

---

*Concerns audit: 2026-09-22*
