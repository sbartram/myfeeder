# Design Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the design-review findings from the Ousterhout/Bloch review: the backoff re-registration bug, scheduler coupling, duplicated feed fetching (with charset bug), untyped request bodies, wrong HTTP status semantics, Raindrop resilience structure, OPML error handling, pagination duplication, dead code, and mutable parser value types.

**Architecture:** All changes stay within the existing layering (controller → service → repository). New pieces: a `FeedFetcher` component owning HTTP feed retrieval, an `event` package with after-commit feed events replacing direct scheduler coupling, a `NotFoundException` for 404 semantics, and typed request records replacing `Map<String,String>` bodies. Resilience4j annotations move from `RaindropService` down to `RaindropApiClientImpl` so business validation never passes through the circuit breaker.

**Tech Stack:** Spring Boot 4.0.3, Java 25, Spring Data JDBC, Resilience4j, Lombok, JUnit 5 + Mockito, MockRestServiceServer; frontend React 19 + TypeScript + Vitest.

**User decisions (already made):**
- "Fix all these issues" — implement every finding the review recommended fixing.
- Explicitly excluded (review recommended non-action, honored here): no entity→response-DTO layer (deliberate single-user simplicity tradeoff); no `markRead` endpoint split (leave alone unless it grows another mode).
- Git workflow (user global CLAUDE.md): work on a feature branch `design-review-fixes`; merges to main use `--no-ff`; no push unless asked.

**Behavior changes to be aware of (all intentional):**
- Missing path-variable entities return 404 (was 400/409).
- `PUT /api/feeds/{id}` with `pollIntervalMinutes < 1` returns 400 (was silently ignored).
- Subscribing to a URL that returns an HTTP error status returns 422 (was 500).
- Paginated responses use field name `items` (was `articles`) — frontend updated in the same task.
- Exponential backoff now actually engages while the app runs (previously only on restart/update).

---

## Conventions for every task

- Run from repo root: `/Users/scottb/dev/bartram/myfeeder`
- Backend verify: `./gradlew test` (needs Docker running for Testcontainers)
- Targeted test: `./gradlew test --tests "org.bartram.myfeeder.<Class>"`
- Frontend verify (only Task 10): `cd src/main/frontend && npx tsc -b && npm test`
- Jackson 3.x: databind is `tools.jackson.databind.*`; annotations stay `com.fasterxml.jackson.annotation.*`
- Spring Data JDBC (not JPA): `@Table`/`@Id` from `org.springframework.data.annotation`
- Commit after each task with a conventional-commit message ending in the Claude trailer.

---

### Task 1: Remove dead code

**Goal:** Delete service and repository methods with no production callers, shrinking the interfaces before refactoring around them.

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/service/ArticleService.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedService.java`
- Modify: `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java`
- Test: `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`, `src/test/java/org/bartram/myfeeder/repository/ArticleRepositoryTest.java`, `src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java` (delete tests of removed methods only)

**Acceptance Criteria:**
- [ ] `ArticleService` no longer declares `findByFeedId`, `findAll`, `findUnread`, `findStarred`
- [ ] `FeedService` no longer declares the single-arg `subscribe(String feedUrl)` overload
- [ ] `ArticleRepository` no longer declares `findByFeedId`, `findByFeedIdAndGuid`, `findByStarredTrue`, `findByReadFalse`
- [ ] `grep -rn "findByReadFalse\|findByStarredTrue\|findByFeedIdAndGuid" src/main src/test` returns nothing (`existsByFeedIdAndGuid` stays — it is used by `FeedPollingService`)
- [ ] Full test suite passes

**Verify:** `./gradlew test` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Create the feature branch**

```bash
git checkout main && git checkout -b design-review-fixes
```

- [ ] **Step 2: Re-verify the methods are unused** (grep must return only the declarations themselves)

```bash
grep -rn "findUnread\|findStarred\|findByReadFalse\|findByStarredTrue\|findByFeedIdAndGuid\|\.findByFeedId(" src/main/java src/test/java
grep -rn "subscribe(" src/main/java src/test/java | grep -v "SubscribeRequest"
```

If any *production* caller appears (controller/service other than the declaring class), keep that method and note it in the commit message. Test-only callers: delete the test alongside the method.

- [ ] **Step 3: Delete from `ArticleService`** the methods `findByFeedId` (lines 25–27), `findAll` (29–31), `findUnread` (33–35), `findStarred` (37–39). Keep `findById`, `updateState`, `markRead` (both overloads — the two-arg one IS used? check: `ArticleController.markRead` calls the 3-arg version; if the 2-arg overload at lines 55–57 has no callers, delete it too).

- [ ] **Step 4: Delete from `FeedService`** the single-arg overload:

```java
    public Feed subscribe(String feedUrl) {
        return subscribe(feedUrl, null);
    }
```

- [ ] **Step 5: Delete from `ArticleRepository`** the declarations `findByFeedId(Long)`, `findByFeedIdAndGuid(Long, String)`, `findByStarredTrue()`, `findByReadFalse()`. Remove the now-unused `import java.util.Optional;` only if nothing else in the file uses it (`findById` comes from `ListCrudRepository`, so the import may become orphaned).

- [ ] **Step 6: Delete tests that exercised removed methods** in `ArticleServiceTest`, `ArticleRepositoryTest`, `FeedServiceTest`. Do not touch tests of surviving methods.

- [ ] **Step 7: Run the suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "refactor: remove unused service and repository methods

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: NotFoundException → HTTP 404

**Goal:** Missing path-variable entities produce 404 instead of 400/409, via a dedicated exception type mapped once in `GlobalExceptionHandler`.

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/NotFoundException.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/ArticleService.java` (updateState)
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedService.java` (update)
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java` (pollFeed)
- Modify: `src/main/java/org/bartram/myfeeder/service/FolderService.java` (rename; moveFeedToFolder feed lookup only)
- Modify: `src/main/java/org/bartram/myfeeder/service/BoardService.java` (update)
- Modify: `src/main/java/org/bartram/myfeeder/controller/ArticleController.java` (saveToRaindrop lookup)
- Modify: `src/main/java/org/bartram/myfeeder/controller/BoardController.java` (simplify updateBoard double-lookup)
- Test: `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java` and affected service tests

**Acceptance Criteria:**
- [ ] `NotFoundException` extends `RuntimeException`; `GlobalExceptionHandler` maps it to 404 with a ProblemDetail
- [ ] `PATCH /api/articles/{missing}` returns 404 (was 400)
- [ ] Payload-referenced entities keep `IllegalArgumentException` → 400: `FolderService.moveFeedToFolder` target-folder check, `ArticleService.findFiltered` cursor check
- [ ] `BoardController.updateBoard` no longer does its own `findById` before calling the service
- [ ] Full test suite passes

**Verify:** `./gradlew test` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Write failing controller test** in `ArticleControllerTest` (follow the file's existing `@WebMvcTest` + `@MockitoBean` style):

```java
@Test
void updateStateReturns404WhenArticleMissing() throws Exception {
    when(articleService.updateState(eq(99L), any(), any()))
            .thenThrow(new NotFoundException("Article not found: 99"));

    mockMvc.perform(patch("/api/articles/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"read\":true}"))
            .andExpect(status().isNotFound());
}
```

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.ArticleControllerTest"`
Expected: FAIL (NotFoundException does not exist / status is 400)

- [ ] **Step 2: Create the exception**

```java
package org.bartram.myfeeder.service;

/** Thrown when an entity addressed by a path variable does not exist. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Add the handler** to `GlobalExceptionHandler` (above the `IllegalArgumentException` handler):

```java
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
```

Add import: `org.bartram.myfeeder.service.NotFoundException`.

- [ ] **Step 4: Replace `IllegalArgumentException` with `NotFoundException`** at these exact lookups (change only the exception class in the `orElseThrow`; messages stay):
  - `ArticleService.updateState` — `"Article not found: " + id`
  - `FeedService.update` — `"Feed not found: " + id`
  - `FeedPollingService.pollFeed` — `"Feed not found: " + feedId`
  - `FolderService.rename` — `"Folder not found: " + id`
  - `FolderService.moveFeedToFolder` — the **feed** lookup only (`"Feed not found: " + feedId`); the folder-target check stays `IllegalArgumentException`
  - `BoardService.update` — `"Board not found: " + id`
  - `ArticleController.saveToRaindrop` — `"Article not found: " + id`

  Leave `ArticleService.findFiltered`'s cursor check as `IllegalArgumentException` (query-parameter validity → 400).

- [ ] **Step 5: Simplify `BoardController.updateBoard`** — the service now handles missing boards:

```java
    @PutMapping("/{id}")
    public Board updateBoard(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return boardService.update(id, request.get("name"), request.get("description"));
    }
```

(Task 8 replaces the `Map` body; keep it as-is here.)

- [ ] **Step 6: Update service tests** that assert `IllegalArgumentException` for the lookups changed in Step 4 → assert `NotFoundException`. Search: `grep -rn "IllegalArgumentException" src/test/java` and update only the not-found cases.

- [ ] **Step 7: Run the suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, including the new 404 test

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "fix: return 404 for missing path entities via NotFoundException

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Centralize OPML error handling

**Goal:** `OpmlParseException` handled in `GlobalExceptionHandler` like `FeedParseException`; the swallow-everything try/catch in `OpmlController` is deleted so failures keep their messages and server bugs return 500.

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/OpmlController.java`
- Test: `src/test/java/org/bartram/myfeeder/controller/OpmlControllerTest.java`

**Acceptance Criteria:**
- [ ] OPML parse failure returns 400 with a ProblemDetail carrying the parse message (no longer a bodiless 400)
- [ ] `OpmlController.importOpml` contains no try/catch
- [ ] Full test suite passes

**Verify:** `./gradlew test --tests "org.bartram.myfeeder.controller.OpmlControllerTest"` → PASS

**Steps:**

- [ ] **Step 1: Write/adjust the failing test** in `OpmlControllerTest` (match its existing multipart style):

```java
@Test
void importReturns400WithDetailOnParseError() throws Exception {
    when(opmlImportService.importOpml(any()))
            .thenThrow(new OpmlParseException("Invalid OPML: missing <body> element"));

    mockMvc.perform(multipart("/api/opml/import")
                    .file(new MockMultipartFile("file", "bad.opml",
                            MediaType.APPLICATION_XML_VALUE, "<opml/>".getBytes())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid OPML: missing <body> element"));
}
```

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.OpmlControllerTest"`
Expected: FAIL (`$.detail` absent — current controller returns an empty body)

- [ ] **Step 2: Add the handler** to `GlobalExceptionHandler`:

```java
    @ExceptionHandler(OpmlParseException.class)
    public ProblemDetail handleOpmlParseException(OpmlParseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Could not parse OPML");
        return problem;
    }
```

Add import: `org.bartram.myfeeder.parser.OpmlParseException`.

- [ ] **Step 3: Strip the try/catch from `OpmlController.importOpml`:**

```java
    @PostMapping("/import")
    public OpmlImportResult importOpml(@RequestParam("file") MultipartFile file) throws IOException {
        return opmlImportService.importOpml(file.getInputStream());
    }
```

Add `import java.io.IOException;`. Remove the now-unused `@Slf4j`, `OpmlParseException` import, and `ResponseEntity` import if nothing else in the file uses them (the export endpoint still uses `ResponseEntity`).

- [ ] **Step 4: Fix any other `OpmlControllerTest` expectations** that relied on the old bodiless-400-for-everything behavior (an unexpected `RuntimeException` now yields 500 — if such a test exists, change its expectation to `isInternalServerError()`).

- [ ] **Step 5: Run and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

```bash
git add -A && git commit -m "refactor: handle OpmlParseException in GlobalExceptionHandler

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Convert parser value types to records

**Goal:** `ParsedFeed` and `ParsedArticle` become immutable records (matching `OpmlFeed`/`OpmlImportResult`), removing mutable `@Data` from write-once carriers.

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/parser/ParsedFeed.java`
- Modify: `src/main/java/org/bartram/myfeeder/parser/ParsedArticle.java`
- Modify: `src/main/java/org/bartram/myfeeder/parser/FeedParser.java` (accessor renames in `parse()` empty-check)
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedService.java` (accessors in `subscribe`)
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java` (accessors in `pollFeed`/`toArticle`)
- Test: `src/test/java/org/bartram/myfeeder/parser/FeedParserTest.java` and any service test using getters

**Acceptance Criteria:**
- [ ] Both types are `record`s with `@Builder` (Lombok supports records); no setters exist
- [ ] All call sites use record accessors (`parsed.title()`, not `parsed.getTitle()`)
- [ ] Full test suite passes with zero production-logic changes

**Verify:** `./gradlew test` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Rewrite `ParsedFeed`:**

```java
package org.bartram.myfeeder.parser;

import lombok.Builder;
import org.bartram.myfeeder.model.FeedType;

import java.util.List;

@Builder
public record ParsedFeed(
        String title,
        String description,
        String siteUrl,
        FeedType feedType,
        List<ParsedArticle> articles) {}
```

- [ ] **Step 2: Rewrite `ParsedArticle`:**

```java
package org.bartram.myfeeder.parser;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ParsedArticle(
        String guid,
        String title,
        String url,
        String author,
        String content,
        String summary,
        String imageUrl,
        Instant publishedAt) {}
```

- [ ] **Step 3: Update accessors** — builders are source-compatible; only getters change:
  - `FeedParser.parse()`: `parsed.getTitle()` → `parsed.title()`, `parsed.getArticles()` → `parsed.articles()`
  - `FeedService.subscribe()`: `parsed.getTitle()/getDescription()/getSiteUrl()/getFeedType()` → `parsed.title()/description()/siteUrl()/feedType()`
  - `FeedPollingService`: `parsed.getArticles()` → `parsed.articles()`; in `toArticle`: `parsed.getGuid()/getTitle()/getUrl()/getAuthor()/getContent()/getSummary()/getImageUrl()/getPublishedAt()` → `parsed.guid()/title()/url()/author()/content()/summary()/imageUrl()/publishedAt()`
  - Compile will catch stragglers: `./gradlew compileJava compileTestJava` and fix every reported getter, including in `FeedParserTest` and service tests.

- [ ] **Step 4: Run and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

```bash
git add -A && git commit -m "refactor: make ParsedFeed and ParsedArticle immutable records

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Extract FeedFetcher (single fetch path, charset fix)

**Goal:** One deep module owns HTTP feed retrieval — conditional requests, charset-correct decoding, error statuses — used by both subscribe and polling; kills the platform-default-charset bug and the `Feed`-mutation side channel.

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/FetchResult.java`
- Create: `src/main/java/org/bartram/myfeeder/service/FeedFetcher.java`
- Create: `src/main/java/org/bartram/myfeeder/service/FeedFetchException.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedService.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java`
- Create: `src/test/java/org/bartram/myfeeder/service/FeedFetcherTest.java`
- Test: update `FeedServiceTest.java`, `FeedPollingServiceTest.java` (mock `FeedFetcher` instead of `RestClient.Builder`)

**Acceptance Criteria:**
- [ ] `FeedService` and `FeedPollingService` no longer depend on `RestClient.Builder`; both call `FeedFetcher`
- [ ] Response bodies decode using the response `Content-Type` charset, defaulting to UTF-8 (test proves ISO-8859-1 decodes correctly)
- [ ] HTTP 304 → `FetchResult.notModified()`; HTTP error status → `FeedFetchException` → 422
- [ ] `Feed.lastError` stores `e.toString()` (not `getMessage()`)
- [ ] Full test suite passes

**Verify:** `./gradlew test --tests "org.bartram.myfeeder.service.FeedFetcherTest"` → PASS, then full `./gradlew test`

**Steps:**

- [ ] **Step 1: Create `FetchResult`:**

```java
package org.bartram.myfeeder.service;

/** Outcome of one HTTP feed fetch. On 304, notModified is true and all other fields are null. */
public record FetchResult(String body, String etag, String lastModified, boolean notModified) {
    public static FetchResult notModified() {
        return new FetchResult(null, null, null, true);
    }
}
```

- [ ] **Step 2: Create `FeedFetchException`:**

```java
package org.bartram.myfeeder.service;

/** The remote server answered with an HTTP error status while fetching a feed. Mapped to 422. */
public class FeedFetchException extends RuntimeException {
    public FeedFetchException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Write the failing tests** in `FeedFetcherTest`:

```java
package org.bartram.myfeeder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FeedFetcherTest {

    private MockRestServiceServer server;
    private FeedFetcher fetcher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new FeedFetcher(builder);
    }

    @Test
    void decodesBodyUsingResponseCharset() {
        byte[] latin1 = "<rss><title>café</title></rss>".getBytes(StandardCharsets.ISO_8859_1);
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withSuccess(latin1,
                        new MediaType("application", "rss+xml", StandardCharsets.ISO_8859_1)));

        FetchResult result = fetcher.fetch("https://example.com/feed");

        assertThat(result.body()).contains("café");
        assertThat(result.notModified()).isFalse();
    }

    @Test
    void sendsConditionalHeadersAndMaps304() {
        server.expect(requestTo("https://example.com/feed"))
                .andExpect(header(HttpHeaders.IF_NONE_MATCH, "\"abc\""))
                .andExpect(header(HttpHeaders.IF_MODIFIED_SINCE, "Tue, 01 Jul 2026 00:00:00 GMT"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        FetchResult result = fetcher.fetch("https://example.com/feed", "\"abc\"",
                "Tue, 01 Jul 2026 00:00:00 GMT");

        assertThat(result.notModified()).isTrue();
    }

    @Test
    void capturesCachingHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"v2\"");
        headers.set(HttpHeaders.LAST_MODIFIED, "Wed, 02 Jul 2026 00:00:00 GMT");
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withSuccess("<rss/>", MediaType.APPLICATION_XML).headers(headers));

        FetchResult result = fetcher.fetch("https://example.com/feed");

        assertThat(result.etag()).isEqualTo("\"v2\"");
        assertThat(result.lastModified()).isEqualTo("Wed, 02 Jul 2026 00:00:00 GMT");
    }

    @Test
    void throwsOnHttpErrorStatus() {
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> fetcher.fetch("https://example.com/feed"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("404");
    }
}
```

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedFetcherTest"`
Expected: FAIL (FeedFetcher does not exist)

- [ ] **Step 4: Create `FeedFetcher`:**

```java
package org.bartram.myfeeder.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Owns HTTP retrieval of feed documents: conditional requests (ETag / Last-Modified),
 * charset-correct body decoding, and error-status handling. The RestClient.Builder is the
 * auto-configured bean, so the myfeeder User-Agent customizer applies (see RestClientConfig).
 */
@Component
public class FeedFetcher {

    private final RestClient restClient;

    public FeedFetcher(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /** Unconditional fetch, used at subscribe time. */
    public FetchResult fetch(String url) {
        return fetch(url, null, null);
    }

    /**
     * Conditional fetch: sends If-None-Match / If-Modified-Since when etag / lastModified are
     * non-null. Returns FetchResult.notModified() on 304; throws FeedFetchException on 4xx/5xx.
     */
    public FetchResult fetch(String url, String etag, String lastModified) {
        return restClient.get()
                .uri(url)
                .headers(headers -> {
                    if (etag != null) {
                        headers.setIfNoneMatch(etag);
                    }
                    if (lastModified != null) {
                        headers.set(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
                    }
                })
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 304) {
                        return FetchResult.notModified();
                    }
                    if (response.getStatusCode().isError()) {
                        throw new FeedFetchException(
                                "HTTP " + response.getStatusCode().value() + " fetching " + url);
                    }
                    String body = new String(response.getBody().readAllBytes(), charsetOf(response.getHeaders()));
                    return new FetchResult(
                            body,
                            response.getHeaders().getETag(),
                            response.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED),
                            false);
                });
    }

    private Charset charsetOf(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType != null && contentType.getCharset() != null
                ? contentType.getCharset() : StandardCharsets.UTF_8;
    }
}
```

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedFetcherTest"`
Expected: PASS

- [ ] **Step 5: Map `FeedFetchException`** in `GlobalExceptionHandler`:

```java
    @ExceptionHandler(FeedFetchException.class)
    public ProblemDetail handleFeedFetchException(FeedFetchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        problem.setTitle("Could not fetch feed");
        return problem;
    }
```

Add import: `org.bartram.myfeeder.service.FeedFetchException`.

- [ ] **Step 6: Rewire `FeedService.subscribe`** — replace the `RestClient.Builder` field with `FeedFetcher feedFetcher` and change the fetch:

```java
        String rawContent = feedFetcher.fetch(feedUrl).body();
        ParsedFeed parsed = feedParser.parse(rawContent);
```

Remove the `org.springframework.web.client.RestClient` import.

- [ ] **Step 7: Rewire `FeedPollingService`** — replace `RestClient.Builder` with `FeedFetcher`, delete `fetchFeedContent`, and rewrite `pollFeed`'s body:

```java
    public void pollFeed(Long feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new NotFoundException("Feed not found: " + feedId));

        try {
            FetchResult result = feedFetcher.fetch(feed.getUrl(), feed.getEtag(), feed.getLastModifiedHeader());
            feed.setLastPolledAt(Instant.now());
            if (result.notModified()) {
                feedRepository.save(feed);
                return;
            }
            if (result.etag() != null) {
                feed.setEtag(result.etag());
            }
            if (result.lastModified() != null) {
                feed.setLastModifiedHeader(result.lastModified());
            }

            ParsedFeed parsed = feedParser.parse(result.body());
            int newCount = 0;

            for (ParsedArticle parsedArticle : parsed.articles()) {
                if (!articleRepository.existsByFeedIdAndGuid(feed.getId(), parsedArticle.guid())) {
                    Article article = toArticle(parsedArticle, feed.getId());
                    articleRepository.save(article);
                    newCount++;
                }
            }

            feed.setLastSuccessfulPollAt(Instant.now());
            feed.setErrorCount(0);
            feed.setLastError(null);
            feedRepository.save(feed);

            log.info("Polled feed '{}': {} new articles", feed.getTitle(), newCount);
        } catch (Exception e) {
            feed.setLastPolledAt(Instant.now());
            feed.setErrorCount(feed.getErrorCount() + 1);
            feed.setLastError(e.toString());
            feedRepository.save(feed);
            log.warn("Failed to poll feed '{}': {}", feed.getTitle(), e.toString());
        }
    }
```

(Note `e.toString()` in both places, and `lastPolledAt` set before the early 304 return.)

- [ ] **Step 8: Update `FeedServiceTest` and `FeedPollingServiceTest`** — replace `RestClient.Builder` mocking with a simple `@Mock FeedFetcher` returning `new FetchResult(xml, null, null, false)` / `FetchResult.notModified()`. Assert `lastError` expectations against `e.toString()` format where applicable.

- [ ] **Step 9: Run and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

```bash
git add -A && git commit -m "refactor: extract FeedFetcher owning HTTP feed retrieval

Fixes platform-default-charset decoding and replaces the Feed-mutation
side channel with an explicit FetchResult.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Make exponential backoff actually engage

**Goal:** After each poll, the scheduler re-reads the feed's error state and replaces the polling task when the effective interval changed — backoff engages while the app runs, not just at restart.

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java`
- Test: `src/test/java/org/bartram/myfeeder/scheduler/FeedPollingSchedulerTest.java`

**Acceptance Criteria:**
- [ ] After a poll pushes `errorCount` past the backoff threshold, the next scheduled task uses the backed-off interval (test proves it without waiting)
- [ ] When the interval is unchanged, no rescheduling happens
- [ ] A feed deleted mid-flight (pollFeed throws `NotFoundException`) is cancelled, not rescheduled
- [ ] `registerFeed` / `cancelFeed` external behavior unchanged (immediate first poll on register)
- [ ] Full test suite passes

**Verify:** `./gradlew test --tests "org.bartram.myfeeder.scheduler.FeedPollingSchedulerTest"` → PASS

**Steps:**

- [ ] **Step 1: Write failing tests** (adapt to the existing test class's mock setup; it already mocks `TaskScheduler`, `FeedRepository`, `FeedPollingService`, `MyfeederProperties`):

```java
@Test
void backoffEngagesAfterErrorsCrossThreshold() {
    Feed feed = feedWith(1L, 15, 0);              // helper: id, intervalMinutes, errorCount
    when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
            .thenReturn(mock(ScheduledFuture.class));
    when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
            .thenReturn(mock(ScheduledFuture.class));
    when(taskScheduler.getClock()).thenReturn(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    scheduler.registerFeed(feed);

    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), eq(Duration.ofMinutes(15)));

    Feed failing = feedWith(1L, 15, 5);           // errorCount == backoffThreshold → multiplier 2
    when(feedRepository.findById(1L)).thenReturn(Optional.of(failing));

    taskCaptor.getValue().run();

    verify(feedPollingService).pollFeed(1L);
    verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class),
            eq(Instant.EPOCH.plus(Duration.ofMinutes(30))), eq(Duration.ofMinutes(30)));
}

@Test
void noRescheduleWhenIntervalUnchanged() {
    Feed feed = feedWith(1L, 15, 0);
    doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
            .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
    scheduler.registerFeed(feed);
    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), eq(Duration.ofMinutes(15)));
    when(feedRepository.findById(1L)).thenReturn(Optional.of(feedWith(1L, 15, 0)));

    taskCaptor.getValue().run();

    verify(feedPollingService).pollFeed(1L);
    verify(taskScheduler, never())
            .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
}

@Test
void cancelsWhenFeedDeletedDuringPoll() {
    Feed feed = feedWith(1L, 15, 0);
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future).when(taskScheduler)
            .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
    scheduler.registerFeed(feed);
    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), eq(Duration.ofMinutes(15)));
    doThrow(new NotFoundException("Feed not found: 1")).when(feedPollingService).pollFeed(1L);

    taskCaptor.getValue().run();

    verify(future).cancel(false);
    verify(feedRepository, never()).findById(anyLong());
}
```

Shared helper for the tests above (add to the test class if it doesn't already have an equivalent):

```java
private Feed feedWith(Long id, int intervalMinutes, int errorCount) {
    Feed feed = new Feed();
    feed.setId(id);
    feed.setTitle("Feed " + id);
    feed.setPollIntervalMinutes(intervalMinutes);
    feed.setErrorCount(errorCount);
    return feed;
}
```

The existing `FeedPollingSchedulerTest` already mocks these collaborators — reuse its `MyfeederProperties` stubbing (backoffThreshold 5, maxIntervalMinutes 1440) and adapt naming to the file's style. Use `doReturn(...)` (not `when(...).thenReturn`) for `scheduleAtFixedRate` to sidestep the `ScheduledFuture<?>` wildcard-generics friction.

Run: `./gradlew test --tests "org.bartram.myfeeder.scheduler.FeedPollingSchedulerTest"`
Expected: FAIL (no rescheduling logic exists)

- [ ] **Step 2: Rewrite the scheduler:**

```java
package org.bartram.myfeeder.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.NotFoundException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedPollingScheduler {

    private final FeedRepository feedRepository;
    private final FeedPollingService feedPollingService;
    private final TaskScheduler taskScheduler;
    private final MyfeederProperties properties;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Long, Duration> currentIntervals = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        feedRepository.findAll().forEach(this::registerFeed);
        log.info("Registered polling tasks for {} feeds", scheduledTasks.size());
    }

    public void registerFeed(Feed feed) {
        cancelFeed(feed.getId());
        Duration interval = computeEffectiveInterval(feed);
        currentIntervals.put(feed.getId(), interval);
        scheduledTasks.put(feed.getId(),
                taskScheduler.scheduleAtFixedRate(() -> pollAndAdjust(feed.getId()), interval));
        log.info("Scheduled polling for feed '{}' every {} minutes", feed.getTitle(), interval.toMinutes());
    }

    public void cancelFeed(Long feedId) {
        currentIntervals.remove(feedId);
        ScheduledFuture<?> existing = scheduledTasks.remove(feedId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    /**
     * Polls, then re-reads the feed's error state; when the effective interval changed
     * (backoff engaged or cleared) the fixed-rate task replaces itself with one at the new
     * interval whose first run is one full interval away. A concurrent external
     * registerFeed/cancelFeed can in theory race this replacement; worst case is one
     * extra poll cycle, acceptable for a single-user deployment.
     */
    private void pollAndAdjust(Long feedId) {
        try {
            feedPollingService.pollFeed(feedId);
        } catch (NotFoundException e) {
            cancelFeed(feedId);
            return;
        }
        feedRepository.findById(feedId).ifPresent(feed -> {
            Duration desired = computeEffectiveInterval(feed);
            if (!desired.equals(currentIntervals.get(feedId))) {
                cancelFeed(feedId);
                currentIntervals.put(feedId, desired);
                scheduledTasks.put(feedId, taskScheduler.scheduleAtFixedRate(
                        () -> pollAndAdjust(feedId),
                        taskScheduler.getClock().instant().plus(desired),
                        desired));
                log.info("Adjusted polling interval for feed '{}' to {} minutes",
                        feed.getTitle(), desired.toMinutes());
            }
        });
    }

    private Duration computeEffectiveInterval(Feed feed) {
        int threshold = properties.getPolling().getBackoffThreshold();
        int maxMinutes = properties.getPolling().getMaxIntervalMinutes();

        if (feed.getErrorCount() >= threshold) {
            int multiplier = (int) Math.pow(2, feed.getErrorCount() / threshold);
            int backoffMinutes = Math.min(feed.getPollIntervalMinutes() * multiplier, maxMinutes);
            return Duration.ofMinutes(backoffMinutes);
        }

        return Duration.ofMinutes(feed.getPollIntervalMinutes());
    }
}
```

- [ ] **Step 3: Run the scheduler tests, then the suite**

Run: `./gradlew test --tests "org.bartram.myfeeder.scheduler.FeedPollingSchedulerTest"` → PASS
Run: `./gradlew test` → BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "fix: re-evaluate polling interval after each poll so backoff engages

Previously computeEffectiveInterval only ran at registration, so a failing
feed kept its normal cadence until restart.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Replace scheduler coupling with after-commit events

**Goal:** `FeedService` and `OpmlImportService` publish feed events; the scheduler subscribes with `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. The "don't forget to register" invariant becomes structural; the hand-rolled `TransactionSynchronization` block dies.

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/event/FeedSavedEvent.java`
- Create: `src/main/java/org/bartram/myfeeder/event/FeedDeletedEvent.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedService.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/OpmlImportService.java` (also: `toMap` duplicate-key guard)
- Modify: `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java`
- Modify: `CLAUDE.md` (Key Behaviors + Gotchas bullets about the coupling)
- Test: `FeedServiceTest.java`, `OpmlImportServiceTest.java`

**Acceptance Criteria:**
- [ ] `FeedService` and `OpmlImportService` have no `FeedPollingScheduler` dependency; both publish events
- [ ] Scheduler has `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` handlers for both events
- [ ] `OpmlImportService` contains no `TransactionSynchronization` code and uses `toMap(..., (a, b) -> a)` for the URL index
- [ ] CLAUDE.md no longer instructs "don't forget this coupling"; it documents the event mechanism instead
- [ ] Full test suite passes

**Verify:** `./gradlew test` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Create the events:**

```java
package org.bartram.myfeeder.event;

import org.bartram.myfeeder.model.Feed;

/** Published after a feed is created or updated; the polling scheduler (re-)registers it. */
public record FeedSavedEvent(Feed feed) {}
```

```java
package org.bartram.myfeeder.event;

/** Published after a feed is deleted; the polling scheduler cancels its task. */
public record FeedDeletedEvent(Long feedId) {}
```

- [ ] **Step 2: Write failing test updates** — in `FeedServiceTest`, replace the `FeedPollingScheduler` mock with `@Mock ApplicationEventPublisher eventPublisher` and change assertions from `verify(feedPollingScheduler).registerFeed(...)` to:

```java
verify(eventPublisher).publishEvent(new FeedSavedEvent(saved));
// and for delete:
verify(eventPublisher).publishEvent(new FeedDeletedEvent(1L));
```

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedServiceTest"`
Expected: FAIL (compile error — FeedService still injects the scheduler)

- [ ] **Step 3: Rewire `FeedService`** — swap `FeedPollingScheduler feedPollingScheduler` for `ApplicationEventPublisher eventPublisher` (`org.springframework.context.ApplicationEventPublisher`); in `subscribe` and `update` replace `feedPollingScheduler.registerFeed(saved)` with `eventPublisher.publishEvent(new FeedSavedEvent(saved))`; rewrite `delete`:

```java
    public void delete(Long id) {
        feedRepository.deleteById(id);
        eventPublisher.publishEvent(new FeedDeletedEvent(id));
    }
```

- [ ] **Step 4: Rewire `OpmlImportService`** — remove `FeedPollingScheduler`, `TransactionSynchronization*` imports and the whole `newFeedsToRegister` block; inject `ApplicationEventPublisher`; where a new feed is saved:

```java
                Feed saved = feedRepository.save(feed);
                eventPublisher.publishEvent(new FeedSavedEvent(saved));
                created++;
```

(The method is `@Transactional`; AFTER_COMMIT defers delivery until commit — exactly what the old synchronization block did.) Also fix the URL index:

```java
        Map<String, Feed> existingByUrl = feedRepository.findAll().stream()
                .collect(Collectors.toMap(Feed::getUrl, Function.identity(), (a, b) -> a));
```

- [ ] **Step 5: Add listeners to `FeedPollingScheduler`:**

```java
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFeedSaved(FeedSavedEvent event) {
        registerFeed(event.feed());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFeedDeleted(FeedDeletedEvent event) {
        cancelFeed(event.feedId());
    }
```

Imports: `org.springframework.transaction.event.TransactionalEventListener`, `org.springframework.transaction.event.TransactionPhase`, plus the two event types. (`fallbackExecution = true` makes the listener fire immediately when no transaction is active — `FeedService` methods are not transactional.)

- [ ] **Step 6: Update `OpmlImportServiceTest`** — drop scheduler/synchronization mocking; verify `eventPublisher.publishEvent(any(FeedSavedEvent.class))` fires once per created feed and never for updated feeds.

- [ ] **Step 7: Update CLAUDE.md** — replace the Key Behaviors bullet "**FeedService** registers feeds with `FeedPollingScheduler` on create/update — don't forget this coupling" and the Gotchas bullet "**FeedPollingScheduler coupling**: ..." with:

```markdown
- **Feed scheduling is event-driven**: `FeedService`/`OpmlImportService` publish `FeedSavedEvent`/`FeedDeletedEvent`; `FeedPollingScheduler` listens with `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. New feed-mutating code paths just publish the event — never call the scheduler directly.
```

Also update the "OpmlImportService registers new feeds with scheduler post-commit (not inline)" Key Behaviors bullet to mention the event listener instead of manual synchronization.

- [ ] **Step 8: Run and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

```bash
git add -A && git commit -m "refactor: decouple feed scheduling via after-commit application events

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: Typed request DTOs

**Goal:** Every `Map<String, String>`/`Map<String, Long>` request body and the entity-typed `PUT /api/feeds/{id}` body become small records — compile-time contracts, same JSON wire format (frontend unchanged).

**Files:**
- Create in `src/main/java/org/bartram/myfeeder/controller/`: `CreateBoardRequest.java`, `UpdateBoardRequest.java`, `BoardByNameRequest.java`, `AddArticleToBoardRequest.java`, `CreateFolderRequest.java`, `RenameFolderRequest.java`, `ReorderFoldersRequest.java`, `MoveFeedToFolderRequest.java`, `FeedUpdateRequest.java`
- Modify: `BoardController.java`, `FolderController.java`, `FeedController.java`, `src/main/java/org/bartram/myfeeder/service/FeedService.java`
- Test: `BoardControllerTest.java`, `FolderControllerTest.java`, `FeedControllerTest.java`, `FeedServiceTest.java`

**Acceptance Criteria:**
- [ ] No controller method takes `@RequestBody Map<...>`; `FeedController.updateFeed` takes `FeedUpdateRequest`, not `Feed`
- [ ] JSON wire format unchanged (frontend payloads `{url, folderId}`, `{name, description}`, `{articleId}`, `{name}`, `{folderIds}`, `{folderId}`, partial-feed `{title?, pollIntervalMinutes?}` all still bind)
- [ ] `PUT /api/feeds/{id}` with `pollIntervalMinutes: 0` returns 400 (was silently ignored)
- [ ] Full test suite passes

**Verify:** `./gradlew test` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Create the records** (one file each, package `org.bartram.myfeeder.controller`, matching the existing `SubscribeRequest` convention):

```java
public record CreateBoardRequest(String name, String description) {}
public record UpdateBoardRequest(String name, String description) {}
public record BoardByNameRequest(String name) {}
public record AddArticleToBoardRequest(Long articleId) {}
public record CreateFolderRequest(String name) {}
public record RenameFolderRequest(String name) {}
public record ReorderFoldersRequest(java.util.List<Long> folderIds) {}
public record MoveFeedToFolderRequest(Long folderId) {}
public record FeedUpdateRequest(String title, Integer pollIntervalMinutes) {}
```

- [ ] **Step 2: Write one failing test first** — in `FeedControllerTest`:

```java
@Test
void updateFeedRejectsNonPositivePollInterval() throws Exception {
    when(feedService.update(eq(1L), any(FeedUpdateRequest.class)))
            .thenThrow(new IllegalArgumentException("pollIntervalMinutes must be >= 1"));

    mockMvc.perform(put("/api/feeds/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"pollIntervalMinutes\":0}"))
            .andExpect(status().isBadRequest());
}
```

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.FeedControllerTest"` → FAIL (no such signature)

- [ ] **Step 3: Update `BoardController`:**

```java
    @PostMapping("/by-name")
    public Board getOrCreateByName(@RequestBody BoardByNameRequest request) {
        return boardService.getOrCreateByName(request.name());
    }

    @PostMapping
    public ResponseEntity<Board> createBoard(@RequestBody CreateBoardRequest request) {
        Board board = boardService.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    @PutMapping("/{id}")
    public Board updateBoard(@PathVariable Long id, @RequestBody UpdateBoardRequest request) {
        return boardService.update(id, request.name(), request.description());
    }

    @PostMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public void addArticleToBoard(@PathVariable Long id, @RequestBody AddArticleToBoardRequest request) {
        boardService.addArticle(id, request.articleId());
    }
```

Remove the `java.util.Map` import if now unused.

- [ ] **Step 4: Update `FolderController`:**

```java
    @PostMapping
    public ResponseEntity<Folder> createFolder(@RequestBody CreateFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(request.name()));
    }

    @PutMapping("/{id}")
    public Folder renameFolder(@PathVariable Long id, @RequestBody RenameFolderRequest request) {
        return folderService.rename(id, request.name());
    }

    @PutMapping("/order")
    public List<Folder> reorderFolders(@RequestBody ReorderFoldersRequest request) {
        return folderService.reorder(request.folderIds());
    }
```

- [ ] **Step 5: Update `FeedController`:**

```java
    @PutMapping("/{id}")
    public ResponseEntity<Feed> updateFeed(@PathVariable Long id, @RequestBody FeedUpdateRequest updates) {
        return ResponseEntity.ok(feedService.update(id, updates));
    }

    @PutMapping("/{id}/folder")
    public Feed moveFeedToFolder(@PathVariable Long id, @RequestBody MoveFeedToFolderRequest request) {
        return folderService.moveFeedToFolder(id, request.folderId());
    }
```

- [ ] **Step 6: Update `FeedService.update`:**

```java
    public Feed update(Long id, FeedUpdateRequest updates) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Feed not found: " + id));

        if (updates.title() != null) {
            feed.setTitle(updates.title());
        }
        if (updates.pollIntervalMinutes() != null) {
            if (updates.pollIntervalMinutes() < 1) {
                throw new IllegalArgumentException("pollIntervalMinutes must be >= 1");
            }
            feed.setPollIntervalMinutes(updates.pollIntervalMinutes());
        }

        Feed saved = feedRepository.save(feed);
        eventPublisher.publishEvent(new FeedSavedEvent(saved));
        return saved;
    }
```

Add import `org.bartram.myfeeder.controller.FeedUpdateRequest`. (Yes, service imports a controller-package record — consistent with the existing single-module layout; do not create a new package for it.)

- [ ] **Step 7: Update tests** — controller tests keep the same JSON strings; only mock signatures change (`update(eq(1L), any(FeedUpdateRequest.class))` etc.). `FeedServiceTest.update` tests construct `new FeedUpdateRequest("title", 30)` instead of a `Feed`; add a test for the new `< 1` rejection.

- [ ] **Step 8: Run and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

```bash
git add -A && git commit -m "refactor: replace Map request bodies and entity binding with typed records

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: Move resilience to the Raindrop API client

**Goal:** `@CircuitBreaker`/`@Retry` move from `RaindropService` to `RaindropApiClientImpl` (a separate bean, so the AOP proxy applies). Business validation (not configured in DB, disabled, no collection) runs outside the breaker; the three-way fallback-rethrow chain shrinks to one.

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`
- Modify: `src/main/java/org/bartram/myfeeder/integration/RaindropService.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `CLAUDE.md` (Resilience4j convention + fallback re-throw gotcha)
- Test: `RaindropServiceTest.java`, `RaindropApiClientImplTest.java`

**Acceptance Criteria:**
- [ ] `RaindropService` has no resilience annotations and no fallback methods; `@Cacheable` on `listCollections` stays
- [ ] `RaindropApiClientImpl` methods carry `@CircuitBreaker(name = "raindrop", fallbackMethod = ...)` + `@Retry(name = "raindrop")`; each fallback rethrows only `RaindropNotConfiguredException` before wrapping
- [ ] `application.yaml` ignores `RaindropNotConfiguredException` in both the `raindrop` circuit breaker and retry instances (a missing token must not open the breaker or burn retries)
- [ ] `IllegalStateException`("disabled"/"not configured in DB") and `IllegalArgumentException`("no collection") propagate untouched to `GlobalExceptionHandler` (409/400) — test-verified
- [ ] CLAUDE.md convention bullets updated
- [ ] Full test suite passes

**Verify:** `./gradlew test --tests "org.bartram.myfeeder.integration.*"` → PASS, then full suite

**Steps:**

- [ ] **Step 1: Read both existing test classes first** (`RaindropServiceTest`, `RaindropApiClientImplTest`) — they encode current fallback behavior; identify which tests move to the client and which die.

- [ ] **Step 2: Update `RaindropApiClientImpl`** — add annotations and fallbacks:

```java
    @CircuitBreaker(name = "raindrop", fallbackMethod = "listCollectionsFallback")
    @Retry(name = "raindrop")
    @Override
    public List<RaindropCollection> listCollections() {
        // body unchanged
    }

    @CircuitBreaker(name = "raindrop", fallbackMethod = "createBookmarkFallback")
    @Retry(name = "raindrop")
    @Override
    public void createBookmark(Long collectionId, String url, String title) {
        // body unchanged
    }

    @SuppressWarnings("unused")
    private List<RaindropCollection> listCollectionsFallback(Throwable throwable) {
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }

    @SuppressWarnings("unused")
    private void createBookmarkFallback(Long collectionId, String url, String title, Throwable throwable) {
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }
```

Imports: `io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker`, `io.github.resilience4j.retry.annotation.Retry`.

- [ ] **Step 3: Strip `RaindropService`** — remove both annotations, both fallback methods, and the resilience imports. `saveToRaindrop` and `listCollections` bodies stay; `@Cacheable` stays on `listCollections`.

- [ ] **Step 4: Update `application.yaml`** — add under the existing instances:

```yaml
resilience4j:
  circuitbreaker:
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
    instances:
      raindrop:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        ignore-exceptions:
          - org.bartram.myfeeder.integration.RaindropNotConfiguredException
```

Mirror the same addition in the test `application.yaml` if it declares resilience4j instances (check `src/test/resources/application.yaml`).

- [ ] **Step 5: Update tests** — `RaindropServiceTest`: business-validation tests (not configured in DB → `IllegalStateException`, disabled → `IllegalStateException`, null collection → `IllegalArgumentException`) now assert the exception propagates directly (no fallback translation); delete fallback-specific tests. `RaindropApiClientImplTest`: keep existing HTTP tests; the fallback methods are exercised only under Spring AOP, so add a plain unit test of the rethrow logic only if the existing test style allows direct invocation — otherwise rely on the annotation config being identical to the previously-proven pattern.

- [ ] **Step 6: Update CLAUDE.md** — revise two bullets:
  - Key Conventions "Resilience4j: Use `@CircuitBreaker` ... on external service calls" → note annotations belong on the **API-client bean** (e.g. `RaindropApiClientImpl`), not on services, so business validation stays outside the breaker.
  - The "**Resilience4j fallback re-throw pattern**" bullet → shrink: fallback rethrows `RaindropNotConfiguredException` (503) before wrapping everything else as `IllegalStateException` (409); business-rule exceptions never reach the fallback because validation happens in `RaindropService` before the client call.

- [ ] **Step 7: Run and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

```bash
git add -A && git commit -m "refactor: move Raindrop resilience annotations to the API client

Business validation now runs outside the circuit breaker, eliminating
the three-way fallback re-throw chain.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: Pagination factory + rename `articles` → `items` (backend + frontend)

**Goal:** The limit+1/hasMore/subList/nextCursor dance lives once in `PaginatedResponse.of(...)`; the generic response field gets the honest name `items`, updated across the API and frontend in one commit.

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/controller/PaginatedResponse.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/ArticleController.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/BoardController.java`
- Modify: `src/main/frontend/src/types/index.ts` (line ~50)
- Modify: `src/main/frontend/src/App.tsx` (line ~92), `src/main/frontend/src/components/ArticleList.tsx` (line ~43), `src/main/frontend/src/components/BoardArticleList.tsx` (line ~18)
- Test: `ArticleControllerTest.java`, `BoardControllerTest.java` (JSON path `$.articles` → `$.items`), frontend tests referencing the field

**Acceptance Criteria:**
- [ ] `PaginatedResponse` exposes `items` and a static factory `of(List<T>, int limit, Function<T, Long>)`; both controllers use it
- [ ] `grep -rn "p.articles\|\.articles\b" src/main/frontend/src --include="*.ts" --include="*.tsx"` shows no paginated-field usage left (feed-level `articles` unrelated hits OK — inspect each)
- [ ] Backend suite passes AND frontend type-check + tests pass
- [ ] Manual sanity: `PaginatedResponse.of(listOf(a,b,c), 2, Article::getId)` → 2 items, nextCursor = b.id; `of(listOf(a), 2, ...)` → 1 item, null cursor

**Verify:** `./gradlew test` → BUILD SUCCESSFUL, then `cd src/main/frontend && npx tsc -b && npm test` → PASS

**Steps:**

- [ ] **Step 1: Write the failing factory test** (new `src/test/java/org/bartram/myfeeder/controller/PaginatedResponseTest.java`):

```java
package org.bartram.myfeeder.controller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatedResponseTest {

    @Test
    void trimsExtraRowAndSetsCursorWhenMorePagesExist() {
        PaginatedResponse<Long> page = PaginatedResponse.of(List.of(10L, 20L, 30L), 2, Function.identity());
        assertThat(page.items()).containsExactly(10L, 20L);
        assertThat(page.nextCursor()).isEqualTo(20L);
    }

    @Test
    void returnsAllItemsAndNullCursorOnLastPage() {
        PaginatedResponse<Long> page = PaginatedResponse.of(List.of(10L), 2, Function.identity());
        assertThat(page.items()).containsExactly(10L);
        assertThat(page.nextCursor()).isNull();
    }
}
```

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.PaginatedResponseTest"` → FAIL

- [ ] **Step 2: Rewrite `PaginatedResponse`:**

```java
package org.bartram.myfeeder.controller;

import java.util.List;
import java.util.function.Function;

public record PaginatedResponse<T>(List<T> items, Long nextCursor) {

    /**
     * Builds a page from a list fetched with limit + 1 rows: the extra row, if present,
     * signals another page and is trimmed; nextCursor is the last returned item's id.
     */
    public static <T> PaginatedResponse<T> of(List<T> fetched, int limit, Function<T, Long> id) {
        boolean hasMore = fetched.size() > limit;
        List<T> items = hasMore ? fetched.subList(0, limit) : fetched;
        Long nextCursor = hasMore ? id.apply(items.getLast()) : null;
        return new PaginatedResponse<>(items, nextCursor);
    }
}
```

- [ ] **Step 3: Use it in `ArticleController.listArticles`:**

```java
    @GetMapping
    public PaginatedResponse<Article> listArticles(
            @RequestParam(required = false) Long feedId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Boolean starred,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "desc") String sort) {
        boolean ascending = "asc".equalsIgnoreCase(sort);
        List<Article> fetched = articleService.findFiltered(feedId, read, starred, before, limit + 1, ascending);
        return PaginatedResponse.of(fetched, limit, Article::getId);
    }
```

- [ ] **Step 4: Use it in `BoardController.listBoardArticles`:**

```java
    @GetMapping("/{id}/articles")
    public PaginatedResponse<Article> listBoardArticles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        return PaginatedResponse.of(boardService.findArticles(id, before, limit + 1), limit, Article::getId);
    }
```

- [ ] **Step 5: Update backend controller tests** — JSON paths `$.articles[...]` → `$.items[...]` in `ArticleControllerTest` and `BoardControllerTest`.

- [ ] **Step 6: Update the frontend** —
  - `src/main/frontend/src/types/index.ts`: in `PaginatedArticles`, `articles: Article[]` → `items: Article[]`
  - `src/main/frontend/src/App.tsx:92`: `p.articles` → `p.items`
  - `src/main/frontend/src/components/ArticleList.tsx:43`: `p.articles` → `p.items`
  - `src/main/frontend/src/components/BoardArticleList.tsx:18`: `p.articles` → `p.items`
  - Fix any test fixtures: `grep -rn "articles:" src/main/frontend/src --include="*.test.*"` and update ones building `PaginatedArticles` objects.

- [ ] **Step 7: Verify both stacks**

Run: `./gradlew test` → BUILD SUCCESSFUL
Run: `cd src/main/frontend && npx tsc -b && npm test` → PASS (note: plain `tsc --noEmit` false-passes; must use `-b`)

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "refactor: add PaginatedResponse.of factory; rename articles field to items

API contract change coordinated with the frontend in the same commit.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Completion

After all tasks: run the full suite one final time (`./gradlew test` + frontend `npx tsc -b && npm test`), then use the superpowers-extended-cc:finishing-a-development-branch skill to decide merge/PR/cleanup. Per user convention: merge to main with `--no-ff` when asked; do not push unless asked. A release is NOT part of this plan.

## Task dependency graph

- Task 1 (dead code) → blocks Task 7 (FeedService overload gone before rewiring)
- Task 2 (NotFoundException) → blocks Task 3 (same handler file), Task 5 (pollFeed throws it), Task 8 (FeedService.update rewritten on top of it)
- Task 4 (parser records) → blocks Task 5 (accessor names in FeedPollingService)
- Task 6 (backoff) → blocks Task 7 (both rewrite FeedPollingScheduler)
- Task 5 (FeedFetcher) → blocks Task 6 (scheduler test asserts NotFoundException path through the rewritten pollFeed)
- Task 7 (events) → blocks Task 8 (Task 8's `FeedService.update` code publishes `FeedSavedEvent` via the `eventPublisher` introduced in Task 7)
- Tasks 9, 10 independent (any order)
