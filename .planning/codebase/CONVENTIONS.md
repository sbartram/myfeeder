---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
# Coding Conventions

**Analysis Date:** 2026-09-22

## Naming Patterns

**Java Classes & Types:**

- Class names: PascalCase (e.g., `Feed`, `FeedService`, `FeedRepository`, `FeedController`)
- Package structure: `org.bartram.myfeeder.<domain>` (e.g., `org.bartram.myfeeder.service`, `org.bartram.myfeeder.controller`, `org.bartram.myfeeder.repository`)
- Test classes: append `Test` suffix to the class under test (e.g., `FeedServiceTest` tests `FeedService`)

**Java Methods & Fields:**

- Method names: camelCase (e.g., `subscribe`, `findAll`, `delete`, `updateFeed`, `listCollections`)
- Field names: camelCase (e.g., `feedId`, `pollIntervalMinutes`, `lastPolledAt`, `apiToken`)
- Test method names: "should" prefix with descriptive name (e.g., `shouldReturnAllFeeds`, `shouldDeleteFeed`, `shouldRejectNonPositivePollInterval`)
- Private helper methods: camelCase with clear intent (e.g., `requireConfigured`, `raiseIfBad`)

**TypeScript/React:**

- Component names: PascalCase (e.g., `FeedPanel`, `ArticleList`, `ReadingPane`, `SortableFolder`)
- Hook names: camelCase with `use` prefix (e.g., `useFeeds`, `useArticles`, `useFolders`, `useTheme`, `useKeyboardShortcuts`)
- Store names: camelCase with `Store` suffix (e.g., `uiStore`, `preferencesStore`)
- Function names: camelCase (e.g., `formatPublishedDate`, `renderWithRouter`, `formatBylineDate`)
- Test names: descriptive statement style (e.g., "should X when Y", "shows date and time when the article is less than 24 hours old", "opens dropdown menu and shows 'Mark older than…' option")
- File names: camelCase for functions/utilities (e.g., `dates.ts`), PascalCase for components (e.g., `FeedPanel.tsx`)
- Type files: lowercase plural (e.g., `types/index.ts`)
- API modules: lowercase plural (e.g., `api/feeds.ts`, `api/articles.ts`)

## Code Style

**Java Formatting:**

- No explicit formatter configured (relies on IDE defaults)
- Constructor injection preferred via Lombok's `@RequiredArgsConstructor`
- Imports organized: standard library, then third-party packages, then project imports

**TypeScript/React Formatting:**

- ESLint for linting: `.eslintrc.js` enables `@eslint/js`, TypeScript ESLint, React Hooks, and React Refresh plugins
- No Prettier configuration found; ESLint rules control formatting
- Strict TypeScript: `strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`
- Target: ES2023 with React 19 JSX

**Linting:**

- Java: Gradle checks run via `./gradlew check`; no explicit linter rules in build config
- TypeScript/React: `npm run lint` runs ESLint; see `src/main/frontend/eslint.config.js` for rules

## Import Organization

**Java:**

```java
// 1. Standard library (java.*, javax.*)
import java.time.Instant;
import java.util.List;

// 2. Third-party packages (org.*, com.*)
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 3. Project packages (org.bartram.myfeeder.*)
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
```

**TypeScript:**

```typescript
// 1. React and core packages
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, useEffect } from 'react'

// 2. Project paths (relative imports)
import { feedsApi } from '../api/feeds'
import { useUIStore } from '../stores/uiStore'
import type { Feed } from '../types'

// 3. Type-only imports use `type` keyword
import type { DragEndEvent } from '@dnd-kit/core'
```

## Error Handling

**Java Approach:**

- Custom exception types map to specific HTTP status codes via centralized `GlobalExceptionHandler`
- Use `@RestControllerAdvice` to handle exceptions uniformly across controllers
- Return RFC 7807 `ProblemDetail` responses with title and detail message

**Custom Exception Hierarchy:**

- `NotFoundException` → HTTP 404 (resource not found)
- `IllegalArgumentException` → HTTP 400 (Bad Request; validation errors)
- `FeedParseException` → HTTP 422 (Unprocessable Entity; feed parsing failed)
- `FeedFetchException` → HTTP 422 (remote HTTP error or network issue)
- `OpmlParseException` → HTTP 400 (OPML parsing failed)
- `IllegalStateException` → HTTP 409 (Configuration error, e.g., integration disabled)
- `RaindropNotConfiguredException` → HTTP 503 (Service Unavailable; integration not configured)

**Throwing Exceptions:**

```java
// Validation errors
if (updates.pollIntervalMinutes() < 1) {
    throw new IllegalArgumentException("pollIntervalMinutes must be >= 1");
}

// Not found errors
Feed feed = feedRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Feed not found: " + id));

// Configuration checks happen BEFORE calling external APIs
if (apiToken == null || apiToken.isBlank()) {
    throw new RaindropNotConfiguredException();
}
```

**TypeScript/Frontend:**

- Fetch errors are caught and rethrown with descriptive messages via `raiseIfBad()` helper in `src/api/client.ts`
- ProblemDetail responses are parsed for the `detail`, `title`, or `message` field; falls back to raw response text
- Errors propagate to UI via React Query's `isError` state

## Logging

**Java:**

- Use Lombok's `@Slf4j` annotation to inject logger
- Log at INFO level for key events (successful operations, config changes)
- Log at WARN level for recoverable errors or degraded behavior
- Log at ERROR level for exceptions and failed operations
- Resilience4j handles circuit breaker logging via its own mechanisms

**Example:**

```java
@Slf4j
@Component
public class RaindropApiClientImpl {
    // Logger is injected by @Slf4j
    // Usage: log.info("..."), log.warn("..."), log.error("...")
}
```

**TypeScript:**

- No centralized logging framework; `console.log` / `console.error` used for debugging in development
- Errors are typically handled via React Query's `onError` callbacks

## Comments

**Java:**

- Javadoc for public methods and classes
- Single-line comments for non-obvious logic
- No verbose inline comments; code should be self-documenting

**Example:**

```java
/**

 * Subscribes to a feed at the given URL.
 *
 * @param feedUrl the feed URL to subscribe to
 * @param folderId optional folder ID to organize the feed
 * @return the created Feed entity
 * @throws FeedFetchException if the URL cannot be fetched
 * @throws FeedParseException if the response cannot be parsed as a feed
 */
public Feed subscribe(String feedUrl, Long folderId) {
    // Implementation
}
```

**TypeScript:**

- JSDoc for exported functions and public APIs
- Inline comments sparingly; prefer clear function names
- Comments for business logic that isn't obvious from code

**Example:**

```typescript
/**

 * Reader byline date: full date + time while an article is under 24 hours old,
 * date only once it's older. Future-dated articles count as recent.
 */
export function formatPublishedDate(dateStr: string, now: Date = new Date()): string {
  const date = new Date(dateStr)
  const isRecent = now.getTime() - date.getTime() < 24 * 60 * 60 * 1000
  return isRecent ? date.toLocaleString() : date.toLocaleDateString()
}
```

## Function Design

**Java:**

- Constructor injection via `@RequiredArgsConstructor` from Lombok; no setter injection
- Keep methods focused on a single responsibility
- Use Optional for nullable returns (e.g., `Optional<Feed> findById(Long id)`)
- Avoid null returns; throw exceptions or use Optional

**Example:**

```java
@Service
@RequiredArgsConstructor
public class FeedService {
    private final FeedRepository feedRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Optional<Feed> findById(Long id) {
        return feedRepository.findById(id);
    }

    public void delete(Long id) {
        feedRepository.deleteById(id);
        eventPublisher.publishEvent(new FeedDeletedEvent(id));
    }
}
```

**TypeScript:**

- Hooks return object structures with explicit property names
- Use destructuring in components to extract only needed values
- Helper functions take explicit parameters, not options objects (unless many optional params)

**Example:**

```typescript
export function useFeeds() {
  return useQuery({ queryKey: ['feeds'], queryFn: feedsApi.getAll })
}

export function useSubscribeFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ url, folderId = null }: { url: string; folderId?: number | null }) =>
      feedsApi.subscribe(url, folderId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}
```

## Module Design

**Java Package Structure:**

- `org.bartram.myfeeder.model`: Domain entities with `@Table` and `@Id` (Spring Data JDBC, not JPA)
- `org.bartram.myfeeder.repository`: Spring Data repositories with `@Query` annotations for custom queries
- `org.bartram.myfeeder.service`: Business logic, orchestration, event publishing
- `org.bartram.myfeeder.controller`: HTTP endpoints, DTOs, request/response mapping
- `org.bartram.myfeeder.parser`: Feed parsing logic (ROME + Jackson)
- `org.bartram.myfeeder.integration`: External service clients (Raindrop.io)
- `org.bartram.myfeeder.scheduler`: Scheduled operations (feed polling, retention)
- `org.bartram.myfeeder.config`: Spring configuration, properties beans, customizers
- `org.bartram.myfeeder.event`: Event classes for pub/sub

**TypeScript Module Structure:**

- `src/api/`: HTTP client functions organized by domain (e.g., `feedsApi`, `articlesApi`)
- `src/hooks/`: React hooks for queries and mutations, one file per domain
- `src/stores/`: Zustand stores for global state (UI state, preferences)
- `src/components/`: React components with co-located tests (e.g., `ArticleList.tsx` + `ArticleList.test.tsx`)
- `src/utils/`: Helper functions with co-located tests (e.g., `dates.ts` + `dates.test.ts`)
- `src/types/`: TypeScript type definitions

## DTOs & Data Classes

**Java:**

- **Records** for immutable DTOs: `public record FeedUpdateRequest(String title, Integer pollIntervalMinutes) {}`
- **Lombok @Data** for mutable DTOs with more fields: `@Data public class MarkReadRequest { ... }`
- **Private Records** for internal/nested data: used in API clients for Jackson mapping
- **Spring Data JDBC @Table** for persistent entities: `@Table("feed")`, `@Id` for primary key

**Example:**

```java
// Immutable DTO (request)
public record FeedUpdateRequest(String title, Integer pollIntervalMinutes) {}

// Mutable DTO (request with many optional fields)
@Data
public class MarkReadRequest {
    private List<Long> articleIds;
    private Long feedId;
    private Integer olderThanDays;
}

// Entity
@Data
@Table("feed")
public class Feed {
    @Id
    private Long id;
    private String url;
    private String title;
    // ...
}

// Internal records in API client (with Jackson mapping)
private record CreateRaindropRequest(String link, String title, CollectionRef collection) {}
private record CollectionRef(@com.fasterxml.jackson.annotation.JsonProperty("$id") Long id) {}
```

**TypeScript:**

- **Interfaces** for component props and state shapes
- **Type aliases** for unions and specific types
- **Type imports** using `type` keyword to avoid circular dependencies

**Example:**

```typescript
interface FeedPanelProps {
  onAddFeed?: () => void
  onSettings?: () => void
  onHelp?: () => void
}

interface UIState {
  selectedFeedId: number | null
  selectedArticleId: number | null
  panelWidths: [number, number]
  setSelectedFeed: (feedId: number | null) => void
  // ...
}

type PanelFocus = 'feeds' | 'articles' | 'reading'
```

## Resilience & External Calls

**Java Resilience4j Pattern:**

- Place `@CircuitBreaker` (outer) and `@Retry` (inner) on the **API client bean**, not the service
- Business validation (config checks) runs in the service **before** calling the client
- Fallback methods re-throw specific exceptions (e.g., `RaindropNotConfiguredException`) before wrapping others
- List exception types in `ignore-exceptions` in both circuit breaker and retry configs

**Example:**

```java
@Slf4j
@Component
public class RaindropApiClientImpl implements RaindropApiClient {
    @CircuitBreaker(name = "raindrop", fallbackMethod = "listCollectionsFallback")
    @Retry(name = "raindrop")
    @Override
    public List<RaindropCollection> listCollections() {
        requireConfigured(); // Config check first
        // API call here
    }

    @SuppressWarnings("unused")
    private List<RaindropCollection> listCollectionsFallback(Throwable throwable) {
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc; // Don't wrap specific exceptions
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }
}
```

Configuration in `application.yaml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      raindrop:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        ignore-exceptions:
          - org.bartram.myfeeder.integration.RaindropNotConfiguredException
  retry:
    instances:
      raindrop:
        max-attempts: 3
        wait-duration: 1s
        ignore-exceptions:
          - org.bartram.myfeeder.integration.RaindropNotConfiguredException

```

---

*Convention analysis: 2026-09-22*
