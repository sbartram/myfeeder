---
last_mapped_commit: 5aa00cc3238b19f637b4c4837cf26cddac012c62
last_mapped_at: 2026-09-22
---
# Testing Patterns

**Analysis Date:** 2026-09-22

## Test Framework

### Backend (Java)

**Test Runner:**

- JUnit Jupiter (JUnit 5)
- Gradle task: `./gradlew test` (requires Docker for Testcontainers)
- Config: `build.gradle.kts` sets `useJUnitPlatform()`

**Assertion Library:**

- AssertJ for fluent assertions (e.g., `assertThat(result).hasSize(1)`, `assertThat(result).isPresent()`)
- Static imports: `import static org.assertj.core.api.Assertions.*`

**Mocking:**

- Mockito for unit test mocks
- MockitoExtension: `@ExtendWith(MockitoExtension.class)`
- Annotations: `@Mock`, `@InjectMocks`
- Static imports: `import static org.mockito.Mockito.*`
- Spring Boot 6 uses `@MockitoBean` for slice tests (WebMvcTest, DataJdbcTest)

### Frontend (TypeScript)

**Test Runner:**

- Vitest 4.1.0 with jsdom environment
- Config: `src/main/frontend/vitest.config.ts`
- Commands: `npm test` (run once), `npm run test:watch` (watch mode)

**Component Testing:**

- React Testing Library for component tests
- Imports: `import { render, screen, fireEvent } from '@testing-library/react'`

**Assertion Library:**

- Vitest's built-in `expect` (compatible with Jest)

**Mocking:**

- Vitest's `vi.mock()` for module mocking
- `vi.fn()` for mock functions

**Test Setup:**

- `src/main/frontend/src/test/setup.ts` runs before tests
- Provides localStorage shim for Zustand persist middleware (jsdom doesn't have localStorage by default)

## Test File Organization

### Backend

**Location:**

- Co-located in `src/test/java/org/bartram/myfeeder/<module>/` mirroring source structure
- Example: `src/main/java/org/bartram/myfeeder/service/FeedService.java` → `src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java`

**Naming:**

- Append `Test` suffix to the class under test: `FeedServiceTest`, `FeedControllerTest`, `FeedRepositoryTest`

**Directory Structure:**

```
src/test/java/org/bartram/myfeeder/
├── service/
│   ├── FeedServiceTest.java
│   ├── ArticleServiceTest.java
│   └── ...
├── controller/
│   ├── FeedControllerTest.java
│   ├── ArticleControllerTest.java
│   └── ...
├── repository/
│   ├── FeedRepositoryTest.java
│   └── ...
├── parser/
│   └── FeedParserTest.java
├── integration/
│   └── RaindropApiClientImplTest.java
├── MyfeederApplicationTests.java
└── TestcontainersConfiguration.java
```

### Frontend

**Location:**

- Co-located with source files: `ArticleList.tsx` → `ArticleList.test.tsx`
- Utility tests in same directory: `dates.ts` → `dates.test.ts`

**Naming:**

- Append `.test.tsx` (components) or `.test.ts` (utilities) to filename

**Directory Structure:**

```
src/main/frontend/src/
├── components/
│   ├── ArticleList.tsx
│   ├── ArticleList.test.tsx
│   ├── FeedPanel.tsx
│   ├── FeedPanel.test.tsx
│   └── ...
├── hooks/
│   ├── useFeeds.ts
│   ├── useArticles.ts
│   └── (no .test files in hooks, but tested via component tests)
├── utils/
│   ├── dates.ts
│   ├── dates.test.ts
│   └── ...
├── test/
│   └── setup.ts
└── stores/
    └── (stores tested via component tests)
```

## Test Structure

### Backend Service Tests

**Pattern:**

```java
@ExtendWith(MockitoExtension.class)
class FeedServiceTest {
    @Mock private FeedRepository feedRepository;
    @Mock private FeedParser feedParser;
    @Mock private FeedFetcher feedFetcher;
    @Mock private MyfeederProperties properties;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FeedService feedService;

    @Test
    void shouldReturnAllFeeds() {
        // Arrange
        var feed = new Feed();
        feed.setTitle("Test");
        when(feedRepository.findAll()).thenReturn(List.of(feed));

        // Act
        var result = feedService.findAll();

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldDeleteFeed() {
        // Act
        feedService.delete(1L);

        // Assert
        verify(eventPublisher).publishEvent(new FeedDeletedEvent(1L));
        verify(feedRepository).deleteById(1L);
    }
}
```

**Naming Convention:**

- Test method names: `shouldX` (e.g., `shouldReturnAllFeeds`, `shouldDeleteFeed`, `shouldRejectNonPositivePollInterval`)
- Arrange-Act-Assert pattern with inline comments

### Backend Controller Tests

**Pattern:**

```java
@WebMvcTest(FeedController.class)
class FeedControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private FeedService feedService;
    @MockitoBean private FeedPollingService feedPollingService;

    @Test
    void shouldListFeeds() throws Exception {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("Test Feed");
        when(feedService.findAll()).thenReturn(List.of(feed));

        mockMvc.perform(get("/api/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Feed"));
    }

    @Test
    void shouldSubscribeToFeed() throws Exception {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("New Feed");
        when(feedService.subscribe("https://example.com/feed.xml", null)).thenReturn(feed);

        mockMvc.perform(post("/api/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/feed.xml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Feed"));
    }

    @Test
    void shouldReturn404ForUnknownFeed() throws Exception {
        when(feedService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/feeds/999"))
                .andExpect(status().isNotFound());
    }
}
```

**Patterns:**

- `@WebMvcTest(ControllerClass.class)` for controller-slice tests
- `@MockitoBean` (Spring 6+) to mock dependencies
- `@Autowired MockMvc mockMvc` to perform HTTP requests
- Static imports for fluent API: `get()`, `post()`, `status()`, `jsonPath()`

### Backend Repository Tests

**Pattern:**

```java
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class FeedRepositoryTest {
    @Autowired
    private FeedRepository feedRepository;

    @Test
    void shouldSaveAndRetrieveFeed() {
        var feed = new Feed();
        feed.setUrl("https://example.com/feed.xml");
        feed.setTitle("Example Feed");
        feed.setFeedType(FeedType.RSS);
        feed.setCreatedAt(Instant.now());

        var saved = feedRepository.save(feed);

        assertThat(saved.getId()).isNotNull();
        var found = feedRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Example Feed");
    }
}
```

**Patterns:**

- `@DataJdbcTest` for repository-only tests
- `@Import(TestcontainersConfiguration.class)` to get real Postgres container
- Requires Docker to be running

### Backend Integration Tests

**Pattern:**

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MyfeederApplicationTests {
    @Autowired private FeedController feedController;
    @Autowired private ArticleController articleController;
    @Autowired private FeedPollingScheduler feedPollingScheduler;

    @Test
    void contextLoads() {
        assertThat(feedController).isNotNull();
        assertThat(articleController).isNotNull();
        assertThat(feedPollingScheduler).isNotNull();
    }
}
```

**Patterns:**

- `@SpringBootTest` for full application context
- `@Import(TestcontainersConfiguration.class)` for database and Redis containers
- Verifies that all beans wire correctly

### Frontend Component Tests

**Pattern:**

```typescript
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { FeedPanel } from './FeedPanel'

const createFolderMutate = vi.fn()

vi.mock('../hooks/useFeeds', () => ({
  useFeeds: () => ({ data: [{ id: 1, title: 'Feed A', folderId: null }] }),
  useDeleteFeed: () => ({ mutate: vi.fn() }),
  usePollFeed: () => ({ mutate: vi.fn() }),
}))

vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const state = {
      selectedFeedId: null,
      setSelectedFeed: vi.fn(),
      expandedFolders: new Set<number>(),
      toggleFolder: vi.fn(),
    }
    return selector(state)
  },
}))

const renderPanel = () =>
  render(
    <MemoryRouter>
      <FeedPanel />
    </MemoryRouter>
  )

describe('FeedPanel', () => {
  beforeEach(() => {
    createFolderMutate.mockClear()
  })

  it('shows the new folder button', () => {
    renderPanel()
    expect(screen.getByLabelText('New folder')).toBeInTheDocument()
  })

  it('opens the inline input when clicked', () => {
    renderPanel()
    fireEvent.click(screen.getByLabelText('New folder'))
    expect(screen.getByPlaceholderText('Folder name')).toBeInTheDocument()
  })
})
```

**Patterns:**

- `vi.mock()` at module level for all hook and store mocks
- Selector pattern for Zustand store mocks (receives state, returns selected value)
- `beforeEach` to reset mock state between tests
- Helper functions (e.g., `renderPanel`) for common setup
- `describe` / `it` for test organization
- Query functions: `getByLabelText`, `getByPlaceholderText`, `getByText`, `queryByLabelText` (null if not found)

### Frontend Utility Tests

**Pattern:**

```typescript
import { describe, expect, test } from 'vitest'
import { formatPublishedDate } from './dates'

describe('formatPublishedDate', () => {
  const now = new Date('2026-08-01T18:00:00Z')

  test('shows date and time when the article is less than 24 hours old', () => {
    const result = formatPublishedDate('2026-08-01T15:30:00Z', now)
    expect(result).toBe(new Date('2026-08-01T15:30:00Z').toLocaleString())
  })

  test('shows date only when the article is more than 24 hours old', () => {
    const result = formatPublishedDate('2026-07-30T12:00:00Z', now)
    expect(result).toBe(new Date('2026-07-30T12:00:00Z').toLocaleDateString())
  })

  test('shows date only when the article is exactly 24 hours old', () => {
    const result = formatPublishedDate('2026-07-31T18:00:00Z', now)
    expect(result).toBe(new Date('2026-07-31T18:00:00Z').toLocaleDateString())
  })
})
```

**Patterns:**

- `describe` / `test` for test organization
- Test names: statement-style (e.g., "shows date and time when...", "shows date only when...")
- Test edge cases (exactly 24 hours old, boundary conditions)

## Mocking

### Backend Mocking Strategy

**What to Mock:**

- Repository calls (inject `@Mock` FeedRepository)
- External API clients (inject `@Mock` RaindropApiClient)
- Configuration beans (inject `@Mock` MyfeederProperties)
- Event publisher (inject `@Mock` ApplicationEventPublisher)
- Parser/Fetcher dependencies (inject `@Mock` FeedParser, FeedFetcher)

**What NOT to Mock:**

- Don't mock the service under test (use `@InjectMocks`)
- Don't mock Spring data structures in slice tests (MockMvc is not mocked; it's real)
- Don't mock business entities (Feed, Article) — construct them directly

**Setup Pattern:**

```java
when(feedRepository.findAll()).thenReturn(List.of(feed));
when(feedRepository.save(any())).thenAnswer(i -> i.getArgument(0)); // Return input
```

**Verification Pattern:**

```java
verify(feedRepository).deleteById(1L);
verify(eventPublisher).publishEvent(new FeedDeletedEvent(1L));
```

### Frontend Mocking Strategy

**Module Mocking (vi.mock):**

- Place all `vi.mock()` calls at the top of the test file, before `describe()`
- Mock entire modules: hooks, stores, API clients
- Full-replacement pattern: the mock completely replaces the real module

**Store Mock Pattern (Zustand):**

```typescript
vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const state = {
      selectedFeedId: null,
      setSelectedFeed: vi.fn(),
    }
    return selector(state) // Selector pattern: caller extracts needed values
  },
}))
```

**Hook Mock Pattern:**

```typescript
vi.mock('../hooks/useFeeds', () => ({
  useFeeds: () => ({ data: [{ id: 1, title: 'Feed A' }] }),
  useDeleteFeed: () => ({ mutate: vi.fn() }),
}))
```

**What to Mock:**

- All custom hooks used by the component
- Zustand stores
- TanStack Query mutations/queries
- Context providers (via MemoryRouter for React Router)

**What NOT to Mock:**

- React Testing Library utilities (render, screen, fireEvent)
- Built-in React APIs
- HTML elements

**Mock Reuse Warning:**

- When adding a new export to a mocked module (e.g., a hook), update ALL test files that mock it
- Example: if `useFeeds.ts` adds `useRefreshFeeds`, update `vi.mock('../hooks/useFeeds')` in every test that mocks it

## Fixtures and Factories

### Backend Test Data

**Inline Construction:**

```java
var feed = new Feed();
feed.setId(1L);
feed.setTitle("Test Feed");
feed.setUrl("https://example.com/feed.xml");
feed.setFeedType(FeedType.RSS);
feed.setCreatedAt(Instant.now());
```

**No Factory Pattern:**

- Simple entities are constructed inline with setters
- More complex scenarios can use the builder pattern if needed (not currently used)

### Frontend Test Data

**Inline Objects:**

```typescript
const feed = { id: 1, title: 'Test Feed', url: 'https://example.com/feed.xml' }
```

**Mock Return Values:**

```typescript
vi.mock('../hooks/useFeeds', () => ({
  useFeeds: () => ({
    data: [{ id: 1, title: 'Feed A', folderId: null, errorCount: 0 }],
  }),
}))
```

## Coverage

**Requirements:**

- Not explicitly enforced; no coverage threshold in build config
- Tests exist for services, controllers, repositories, parsers, and key components

**View Backend Coverage:**

```bash
./gradlew test

# Coverage is not generated by default; can be added via JaCoCo plugin if needed

```

**View Frontend Coverage:**

```bash
cd src/main/frontend
npm test -- --coverage
```

## Test Types

### Unit Tests (Backend)

**Scope:** Individual methods of a service, controller, or parser
**Example:** `FeedServiceTest.shouldReturnAllFeeds()`
**Approach:**

- Mock all dependencies
- Test a single method
- Assert return value or side effects (event publishing, repository calls)

**Location:** `src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java`

### Integration Tests (Backend)

**Scope:** A slice of the application (controller + service layers)
**Example:** `FeedControllerTest.shouldSubscribeToFeed()`
**Approach:**

- Use `@WebMvcTest` for controller integration
- Mock service dependencies
- Test HTTP request/response flow
- Assert response status and JSON content

**Location:** `src/test/java/org/bartram/myfeeder/controller/FeedControllerTest.java`

### Repository Tests (Backend)

**Scope:** Database operations
**Example:** `FeedRepositoryTest.shouldSaveAndRetrieveFeed()`
**Approach:**

- Use `@DataJdbcTest` + `TestcontainersConfiguration`
- Real Postgres container manages data
- Test CRUD operations, custom queries
- Requires Docker to be running

**Location:** `src/test/java/org/bartram/myfeeder/repository/FeedRepositoryTest.java`

### Full Integration Tests (Backend)

**Scope:** Entire application context
**Example:** `MyfeederApplicationTests.contextLoads()`
**Approach:**

- Use `@SpringBootTest` + `TestcontainersConfiguration`
- All beans are wired; no mocking unless specified
- Verify the application can start

**Location:** `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java`

### Component Tests (Frontend)

**Scope:** Individual React components
**Example:** `ArticleList.test.tsx`, `FeedPanel.test.tsx`
**Approach:**

- Render component with React Testing Library
- Mock hooks and stores
- Simulate user interactions (clicks, typing)
- Assert component renders expected UI

**Location:** `src/main/frontend/src/components/ArticleList.test.tsx`

### Utility Tests (Frontend)

**Scope:** Helper functions
**Example:** `dates.test.ts`
**Approach:**

- Test function with various inputs
- Test edge cases and boundary conditions
- Assert return values

**Location:** `src/main/frontend/src/utils/dates.test.ts`

### No E2E Tests

- End-to-end tests are not present in the current codebase
- Could be added with Playwright or Cypress for full application flow testing

## Common Patterns

### Async Testing (Backend)

**Service Methods Returning Optional:**

```java
@Test
void shouldFindFeedById() {
    var feed = new Feed();
    feed.setId(1L);
    when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

    var result = feedService.findById(1L);
    assertThat(result).isPresent();
}
```

**Event Publishing:**

```java
@Test
void shouldPublishEventOnDelete() {
    feedService.delete(1L);
    verify(eventPublisher).publishEvent(new FeedDeletedEvent(1L));
}
```

### Error Testing (Backend)

**Exception Assertions:**

```java
@Test
void shouldRejectNonPositivePollInterval() {
    var feed = new Feed();
    feed.setId(1L);
    when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

    var updates = new FeedUpdateRequest(null, 0);

    assertThatThrownBy(() -> feedService.update(1L, updates))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pollIntervalMinutes");
}
```

### Async Testing (Frontend)

**Waiting for Async Operations:**

```typescript
it('loads articles when component mounts', async () => {
  const { findByText } = render(<ArticleList />)
  
  // Vitest + React Testing Library automatically wait for async operations
  const article = await findByText('Article Title')
  expect(article).toBeInTheDocument()
})
```

### Error Testing (Frontend)

**API Errors:**

```typescript
vi.mock('../api/feeds', () => ({
  feedsApi: {
    getAll: () => Promise.reject(new Error('Network error')),
  },
}))

it('shows error message on fetch failure', async () => {
  render(<ArticleList />)
  const error = await screen.findByText(/error/i)
  expect(error).toBeInTheDocument()
})
```

## Test Run Commands

**Backend:**

```bash
./gradlew test                              # Run all tests
./gradlew test --tests "FeedServiceTest"    # Run single test class
```

**Frontend:**

```bash
cd src/main/frontend
npm test                                    # Run all tests once
npm run test:watch                          # Watch mode for continuous testing
```

---

*Testing analysis: 2026-09-22*
