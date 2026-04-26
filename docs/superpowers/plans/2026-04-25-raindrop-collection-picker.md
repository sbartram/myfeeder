# Raindrop Collection Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unusable numeric "collection ID" input in Raindrop settings with a sorted dropdown of the user's Raindrop collections, and move the Raindrop API token out of database storage into a deployment-time helm secret.

**Architecture:** A new `RaindropApiClient` interface + `RaindropApiClientImpl` becomes the sole HTTP layer to Raindrop. The token is read once from `MyfeederProperties` (bound to env var `MYFEEDER_RAINDROP_API_TOKEN` via helm). When the token is empty, every Raindrop call throws `RaindropNotConfiguredException` which `GlobalExceptionHandler` maps to HTTP 503. The settings dialog calls a new `/api/integrations/raindrop/status` endpoint to decide whether to render the form, and `/api/integrations/raindrop/collections` to populate a name-sorted dropdown. Per-user `IntegrationConfig.config` JSON shrinks to just `{ collectionId }`; a Flyway V4 migration scrubs the legacy `apiToken` field from existing rows.

**Tech Stack:** Spring Boot 4.0.3, Java 21, Lombok, Spring Data JDBC, Flyway, Spring RestClient, Resilience4j, Spring Cache (Redis), Jackson 3.x (`tools.jackson.databind`), JUnit 5, Mockito, AssertJ, MockMvc, MockRestServiceServer, Testcontainers, React 19 + TypeScript + Vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-04-25-raindrop-collection-picker-design.md`

---

## File Structure

**Create:**
- `src/main/java/org/bartram/myfeeder/integration/RaindropApiClient.java` — client interface
- `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java` — `RestClient`-based impl
- `src/main/java/org/bartram/myfeeder/integration/RaindropCollection.java` — `(id, title)` record returned to the controller layer
- `src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java` — sentinel exception → 503
- `src/main/resources/db/migration/V4__strip_raindrop_api_token.sql` — scrub legacy `apiToken` field
- `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java`
- `src/test/java/org/bartram/myfeeder/repository/V4StripRaindropApiTokenMigrationTest.java`
- `src/main/frontend/src/components/SettingsDialog.test.tsx`

**Modify:**
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java` — add `apiToken` to `Raindrop`
- `src/main/java/org/bartram/myfeeder/integration/RaindropConfig.java` — drop `apiToken`
- `src/main/java/org/bartram/myfeeder/integration/RaindropService.java` — delegate to `RaindropApiClient`, add `listCollections()`
- `src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java` — new GET endpoints, PUT body shape
- `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java` — map `RaindropNotConfiguredException` → 503
- `src/main/resources/application.yaml` — bind `myfeeder.raindrop.api-token` to env var
- `src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java`
- `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java`
- `helm/myfeeder/values.yaml` — add `secrets.raindropApiToken`
- `helm/myfeeder/templates/app-secret.yaml` — emit secret entry
- `helm/myfeeder/templates/app-deployment.yaml` — wire env var
- `deploy.sh` — pass `MYFEEDER_RAINDROP_API_TOKEN`
- `src/main/frontend/src/api/integrations.ts` — types + new endpoints
- `src/main/frontend/src/components/SettingsDialog.tsx` — replace token+id form with dropdown

---

## Task 1: Add `apiToken` to MyfeederProperties + bind env var

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Update MyfeederProperties**

Replace the `Raindrop` inner class in `MyfeederProperties.java` with:

```java
    @Data
    public static class Raindrop {
        private String apiBaseUrl = "https://api.raindrop.io/rest/v1";
        private String apiToken = "";
    }
```

- [ ] **Step 2: Bind env var in application.yaml**

In `src/main/resources/application.yaml`, replace:

```yaml
  raindrop:
    api-base-url: https://api.raindrop.io/rest/v1
```

with:

```yaml
  raindrop:
    api-base-url: https://api.raindrop.io/rest/v1
    api-token: ${MYFEEDER_RAINDROP_API_TOKEN:}
```

The trailing `:` provides an empty default so `bootRun` and `bootTestRun` work without the secret.

- [ ] **Step 3: Verify build still compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java src/main/resources/application.yaml
git commit -m "add raindrop api-token property bound to env var"
```

---

## Task 2: Add `RaindropNotConfiguredException` + 503 handler

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java`

- [ ] **Step 1: Write the failing handler test**

Append to `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java` (you'll need an existing controller method that throws — for now we'll add the test under the controller test in a later task once we have an endpoint that throws it; for this task, just verify the handler maps the exception via a synthetic test).

Skip writing a test in this task — the handler will be exercised by the controller tests in Tasks 9–10. Move on.

- [ ] **Step 2: Create the exception type**

Create `src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java`:

```java
package org.bartram.myfeeder.integration;

public class RaindropNotConfiguredException extends RuntimeException {
    public RaindropNotConfiguredException() {
        super("Raindrop integration is not configured. Set the MYFEEDER_RAINDROP_API_TOKEN environment variable.");
    }
}
```

- [ ] **Step 3: Map it to 503 in GlobalExceptionHandler**

Replace the contents of `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java` with:

```java
package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.integration.RaindropNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Configuration error");
        return problem;
    }

    @ExceptionHandler(RaindropNotConfiguredException.class)
    public ProblemDetail handleRaindropNotConfigured(RaindropNotConfiguredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Raindrop not configured");
        return problem;
    }
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
git commit -m "map RaindropNotConfiguredException to HTTP 503"
```

---

## Task 3: Define `RaindropCollection` record + `RaindropApiClient` interface

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/integration/RaindropCollection.java`
- Create: `src/main/java/org/bartram/myfeeder/integration/RaindropApiClient.java`

- [ ] **Step 1: Create the RaindropCollection record**

Create `src/main/java/org/bartram/myfeeder/integration/RaindropCollection.java`:

```java
package org.bartram.myfeeder.integration;

public record RaindropCollection(Long id, String title) {}
```

- [ ] **Step 2: Create the RaindropApiClient interface**

Create `src/main/java/org/bartram/myfeeder/integration/RaindropApiClient.java`:

```java
package org.bartram.myfeeder.integration;

import java.util.List;

public interface RaindropApiClient {

    /**
     * Lists the user's root collections.
     *
     * @throws RaindropNotConfiguredException when the deployment token is not set
     */
    List<RaindropCollection> listCollections();

    /**
     * Creates a bookmark in the given collection.
     *
     * @throws RaindropNotConfiguredException when the deployment token is not set
     */
    void createBookmark(Long collectionId, String url, String title);
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/RaindropCollection.java src/main/java/org/bartram/myfeeder/integration/RaindropApiClient.java
git commit -m "add RaindropApiClient interface and RaindropCollection record"
```

---

## Task 4: Implement `RaindropApiClientImpl.listCollections()` (TDD)

**Files:**
- Create: `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java`
- Create: `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`

- [ ] **Step 1: Write the failing test for token-not-configured**

Create `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java`:

```java
package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RaindropApiClientImplTest {

    private MyfeederProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MyfeederProperties();
        properties.getRaindrop().setApiBaseUrl("https://api.raindrop.io/rest/v1");
        properties.getRaindrop().setApiToken("test-token");
    }

    @Test
    void listCollectionsThrowsWhenTokenMissing() {
        properties.getRaindrop().setApiToken("");
        var client = new RaindropApiClientImpl(properties, RestClient.builder());

        assertThatThrownBy(client::listCollections)
                .isInstanceOf(RaindropNotConfiguredException.class);
    }
}
```

- [ ] **Step 2: Run the test and watch it fail to compile**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropApiClientImplTest" 2>&1 | tail -20`
Expected: compilation failure — `RaindropApiClientImpl` does not exist.

- [ ] **Step 3: Create the minimal RaindropApiClientImpl**

Create `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`:

```java
package org.bartram.myfeeder.integration;

import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class RaindropApiClientImpl implements RaindropApiClient {

    private final String apiToken;
    private final RestClient restClient;

    public RaindropApiClientImpl(MyfeederProperties properties, RestClient.Builder builder) {
        this.apiToken = properties.getRaindrop().getApiToken();
        this.restClient = builder
                .baseUrl(properties.getRaindrop().getApiBaseUrl())
                .build();
    }

    private void requireConfigured() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RaindropNotConfiguredException();
        }
    }

    @Override
    public List<RaindropCollection> listCollections() {
        requireConfigured();
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void createBookmark(Long collectionId, String url, String title) {
        requireConfigured();
        throw new UnsupportedOperationException("not implemented yet");
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropApiClientImplTest"`
Expected: PASS (1 test, listCollectionsThrowsWhenTokenMissing).

- [ ] **Step 5: Add the failing test for HTTP listCollections**

Append to `RaindropApiClientImplTest.java` (above the closing brace):

```java
    @Test
    void listCollectionsCallsApiAndParsesResponse() {
        var builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new RaindropApiClientImpl(properties, builder);

        server.expect(requestTo("https://api.raindrop.io/rest/v1/collections"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "items": [
                            {"_id": 100, "title": "Reading"},
                            {"_id": 200, "title": "Recipes"}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        List<RaindropCollection> result = client.listCollections();

        assertThat(result)
                .extracting(RaindropCollection::id, RaindropCollection::title)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(100L, "Reading"),
                        org.assertj.core.groups.Tuple.tuple(200L, "Recipes"));
        server.verify();
    }
```

- [ ] **Step 6: Run the test and confirm it fails**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropApiClientImplTest.listCollectionsCallsApiAndParsesResponse"`
Expected: FAIL — `UnsupportedOperationException`.

- [ ] **Step 7: Implement listCollections() with the Authorization header**

In `RaindropApiClientImpl.java`, replace the body of `listCollections()` and add the supporting records:

```java
    @Override
    public List<RaindropCollection> listCollections() {
        requireConfigured();
        CollectionsResponse response = restClient
                .get()
                .uri("/collections")
                .header("Authorization", "Bearer " + apiToken)
                .retrieve()
                .body(CollectionsResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(item -> new RaindropCollection(item.id(), item.title()))
                .toList();
    }

    private record CollectionsResponse(List<CollectionItem> items) {}

    private record CollectionItem(
            @com.fasterxml.jackson.annotation.JsonProperty("_id") Long id,
            String title) {}
```

Note: Jackson 3.x in this codebase coexists with `com.fasterxml.jackson.annotation.JsonProperty` (the annotation package is unchanged across Jackson versions; only `databind`/`core` package roots moved). Use the fully qualified name to avoid an extra import line.

- [ ] **Step 8: Run the test and confirm it passes**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropApiClientImplTest"`
Expected: 2 tests pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java
git commit -m "implement RaindropApiClient.listCollections via RestClient"
```

---

## Task 5: Implement `RaindropApiClientImpl.createBookmark()` (TDD)

**Files:**
- Modify: `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java`
- Modify: `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`

- [ ] **Step 1: Write the failing test**

Add the following imports to `RaindropApiClientImplTest.java`:

```java
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
```

Append this test method:

```java
    @Test
    void createBookmarkPostsExpectedBody() {
        var builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new RaindropApiClientImpl(properties, builder);

        server.expect(requestTo("https://api.raindrop.io/rest/v1/raindrop"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json(
                        """
                        {
                          "link": "https://example.com/article",
                          "title": "Hello",
                          "collection": {"$id": 100}
                        }
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.createBookmark(100L, "https://example.com/article", "Hello");

        server.verify();
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropApiClientImplTest.createBookmarkPostsExpectedBody"`
Expected: FAIL — `UnsupportedOperationException`.

- [ ] **Step 3: Implement createBookmark()**

In `RaindropApiClientImpl.java`, replace the body of `createBookmark()` and add the request records below the existing private records:

```java
    @Override
    public void createBookmark(Long collectionId, String url, String title) {
        requireConfigured();
        var body = new CreateRaindropRequest(url, title, new CollectionRef(collectionId));
        restClient
                .post()
                .uri("/raindrop")
                .header("Authorization", "Bearer " + apiToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private record CreateRaindropRequest(String link, String title, CollectionRef collection) {}

    private record CollectionRef(@com.fasterxml.jackson.annotation.JsonProperty("$id") Long id) {}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropApiClientImplTest"`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java
git commit -m "implement RaindropApiClient.createBookmark"
```

---

## Task 6: Flyway V4 migration to scrub legacy `apiToken`

**Files:**
- Create: `src/main/resources/db/migration/V4__strip_raindrop_api_token.sql`
- Create: `src/test/java/org/bartram/myfeeder/repository/V4StripRaindropApiTokenMigrationTest.java`

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V4__strip_raindrop_api_token.sql`:

```sql
UPDATE integration_config
SET config = jsonb_build_object(
    'collectionId', (config::jsonb->>'collectionId')::bigint
)::text
WHERE type = 'RAINDROP'
  AND config IS NOT NULL
  AND config::jsonb ? 'apiToken';
```

Note: `integration_config.config` is stored as `text` (per `V1__initial_schema.sql`), so we cast to `jsonb` for manipulation and back to `text` for storage.

- [ ] **Step 2: Write the migration test**

Create `src/test/java/org/bartram/myfeeder/repository/V4StripRaindropApiTokenMigrationTest.java`:

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.test.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V4StripRaindropApiTokenMigrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void migrationRemovesApiTokenAndPreservesCollectionId() {
        // Insert a row that simulates the legacy shape
        jdbc.update(
                "INSERT INTO integration_config (type, config, enabled) VALUES (?, ?::text, ?) "
                        + "ON CONFLICT (type) DO UPDATE SET config = EXCLUDED.config",
                "RAINDROP",
                "{\"apiToken\":\"leaked-token\",\"collectionId\":12345}",
                true);

        // Re-run migrations is implicit via @DataJdbcTest + Flyway autoconfigure;
        // the V4 migration was already applied at context startup. We just verify
        // the row reflects the post-migration shape.
        String config = jdbc.queryForObject(
                "SELECT config FROM integration_config WHERE type = 'RAINDROP'",
                String.class);

        assertThat(config).doesNotContain("apiToken");
        assertThat(config).contains("\"collectionId\"");
        assertThat(config).contains("12345");
    }
}
```

Important: this test relies on the V4 migration running at context startup, *then* the legacy row being inserted, *then* re-checking. But Flyway only runs once per session. The test instead inserts a row that's already in the post-migration shape (as the migration has already run). To genuinely exercise the migration, we need the row to predate it. Use a different approach in the next step.

- [ ] **Step 3: Rewrite the test to actually exercise the migration**

Replace the contents of `V4StripRaindropApiTokenMigrationTest.java` with:

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.test.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V4StripRaindropApiTokenMigrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void migrationLogicScrubsApiTokenWhenAppliedToLegacyRow() {
        // Simulate a row that survived V3 (legacy shape with apiToken).
        jdbc.update(
                "INSERT INTO integration_config (type, config, enabled) VALUES (?, ?::text, ?) "
                        + "ON CONFLICT (type) DO UPDATE SET config = EXCLUDED.config",
                "RAINDROP",
                "{\"apiToken\":\"leaked-token\",\"collectionId\":12345}",
                true);

        // Re-run the V4 migration's UPDATE manually against the inserted row.
        jdbc.update(
                "UPDATE integration_config "
                        + "SET config = jsonb_build_object('collectionId', (config::jsonb->>'collectionId')::bigint)::text "
                        + "WHERE type = 'RAINDROP' AND config IS NOT NULL AND config::jsonb ? 'apiToken'");

        String config = jdbc.queryForObject(
                "SELECT config FROM integration_config WHERE type = 'RAINDROP'",
                String.class);

        assertThat(config).doesNotContain("apiToken");
        assertThat(config).contains("\"collectionId\"");
        assertThat(config).contains("12345");
    }
}
```

This intentionally re-runs the migration's `UPDATE` against a freshly inserted legacy row, which is the only reliable way to test the SQL inside a `@DataJdbcTest` context (Flyway has already run by the time the test executes).

- [ ] **Step 4: Run the migration test**

Run: `./gradlew test --tests "org.bartram.myfeeder.repository.V4StripRaindropApiTokenMigrationTest"`
Expected: PASS. (Requires Docker for Testcontainers.)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V4__strip_raindrop_api_token.sql src/test/java/org/bartram/myfeeder/repository/V4StripRaindropApiTokenMigrationTest.java
git commit -m "scrub legacy raindrop apiToken from integration_config"
```

---

## Task 7: Simplify `RaindropConfig` DTO

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/integration/RaindropConfig.java`

- [ ] **Step 1: Drop `apiToken` from the DTO**

Replace `src/main/java/org/bartram/myfeeder/integration/RaindropConfig.java` with:

```java
package org.bartram.myfeeder.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RaindropConfig {
    private Long collectionId;
}
```

The `@JsonIgnoreProperties(ignoreUnknown = true)` is defense-in-depth: if any row escaped the V4 migration, deserialization still succeeds.

- [ ] **Step 2: Verify build (existing tests will fail next; that's fine)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/RaindropConfig.java
git commit -m "drop apiToken from RaindropConfig DTO"
```

---

## Task 8: Refactor `RaindropService` to use `RaindropApiClient` + add `listCollections()`

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/integration/RaindropService.java`
- Modify: `src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java`

- [ ] **Step 1: Update the service**

Replace `src/main/java/org/bartram/myfeeder/integration/RaindropService.java` with:

```java
package org.bartram.myfeeder.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaindropService {

    private final IntegrationConfigRepository configRepository;
    private final RaindropApiClient raindropApiClient;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "raindrop", fallbackMethod = "saveToRaindropFallback")
    @Retry(name = "raindrop")
    public void saveToRaindrop(Article article) {
        var integrationConfig = configRepository.findByType(IntegrationType.RAINDROP)
                .orElseThrow(() -> new IllegalStateException("Raindrop.io is not configured"));

        if (!integrationConfig.isEnabled()) {
            throw new IllegalStateException("Raindrop.io integration is disabled");
        }

        RaindropConfig config;
        try {
            config = objectMapper.readValue(integrationConfig.getConfig(), RaindropConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Raindrop configuration", e);
        }

        if (config.getCollectionId() == null) {
            throw new IllegalArgumentException("No Raindrop collection selected. Pick one in Settings.");
        }

        raindropApiClient.createBookmark(config.getCollectionId(), article.getUrl(), article.getTitle());
        log.info("Saved article '{}' to Raindrop.io", article.getTitle());
    }

    @Cacheable(value = "raindrop-collections", unless = "#result == null || #result.isEmpty()")
    @CircuitBreaker(name = "raindrop", fallbackMethod = "listCollectionsFallback")
    @Retry(name = "raindrop")
    public List<RaindropCollection> listCollections() {
        return raindropApiClient.listCollections().stream()
                .sorted(Comparator.comparing(RaindropCollection::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @SuppressWarnings("unused")
    private void saveToRaindropFallback(Article article, Throwable throwable) {
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        if (throwable instanceof IllegalStateException ise) {
            throw ise;
        }
        if (throwable instanceof IllegalArgumentException iae) {
            throw iae;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }

    @SuppressWarnings("unused")
    private List<RaindropCollection> listCollectionsFallback(Throwable throwable) {
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }
}
```

The fallback methods rethrow `RaindropNotConfiguredException` (so the controller layer's 503 mapping wins) and `IllegalArgumentException` (so the 400 mapping wins for "no collection selected"); only genuine downstream failures get translated.

- [ ] **Step 2: Rewrite the service test**

Replace `src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java` with:

```java
package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock private IntegrationConfigRepository configRepository;
    @Mock private RaindropApiClient raindropApiClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private RaindropService raindropService;

    @Test
    void shouldThrowWhenConfigMissing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());

        var article = articleAt("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldThrowWhenConfigDisabled() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"collectionId\":123}");
        config.setEnabled(false);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> raindropService.saveToRaindrop(articleAt("https://example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldThrowWhenNoCollectionSelected() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{}");
        config.setEnabled(true);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> raindropService.saveToRaindrop(articleAt("https://example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collection");
    }

    @Test
    void shouldDelegateToClientWhenConfigured() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"collectionId\":456}");
        config.setEnabled(true);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        var article = articleAt("https://example.com/x");
        article.setTitle("X");

        raindropService.saveToRaindrop(article);

        verify(raindropApiClient).createBookmark(456L, "https://example.com/x", "X");
    }

    @Test
    void listCollectionsSortsByTitleCaseInsensitive() {
        when(raindropApiClient.listCollections()).thenReturn(List.of(
                new RaindropCollection(3L, "zebra"),
                new RaindropCollection(1L, "Apple"),
                new RaindropCollection(2L, "banana")));

        List<RaindropCollection> result = raindropService.listCollections();

        assertThat(result).extracting(RaindropCollection::title)
                .containsExactly("Apple", "banana", "zebra");
    }

    private static Article articleAt(String url) {
        var a = new Article();
        a.setUrl(url);
        a.setTitle("t");
        return a;
    }
}
```

- [ ] **Step 3: Run the service tests**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropServiceTest"`
Expected: 5 tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/RaindropService.java src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java
git commit -m "delegate RaindropService to RaindropApiClient and add listCollections"
```

---

## Task 9: Add `GET /api/integrations/raindrop/status` (TDD)

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java`
- Modify: `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java`

- [ ] **Step 1: Write the failing test**

Replace the contents of `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java` with:

```java
package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.RaindropCollection;
import org.bartram.myfeeder.integration.RaindropNotConfiguredException;
import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntegrationConfigController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
@EnableConfigurationProperties(MyfeederProperties.class)
class IntegrationConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IntegrationConfigRepository configRepository;
    @MockitoBean private RaindropService raindropService;
    @Autowired private MyfeederProperties properties;

    @Test
    void statusReturnsConfiguredFalseWhenTokenEmpty() throws Exception {
        properties.getRaindrop().setApiToken("");

        mockMvc.perform(get("/api/integrations/raindrop/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void statusReturnsConfiguredTrueWhenTokenPresent() throws Exception {
        properties.getRaindrop().setApiToken("a-token");

        mockMvc.perform(get("/api/integrations/raindrop/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }
}
```

This intentionally drops the older tests for now (we'll re-add them in Task 11 once the PUT shape changes). All other existing tests will be re-added in Tasks 10 and 11.

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest"`
Expected: FAIL — endpoints don't exist (`404`).

- [ ] **Step 3: Add the status endpoint**

Replace the contents of `src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java` with:

```java
package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.RaindropConfig;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationConfigController {

    private final IntegrationConfigRepository configRepository;
    private final JsonMapper objectMapper;
    private final MyfeederProperties properties;

    @GetMapping
    public List<IntegrationConfig> listIntegrations() {
        return configRepository.findAll();
    }

    @GetMapping("/raindrop/status")
    public Map<String, Boolean> raindropStatus() {
        String token = properties.getRaindrop().getApiToken();
        boolean configured = token != null && !token.isBlank();
        return Map.of("configured", configured);
    }

    @PutMapping("/raindrop")
    public IntegrationConfig upsertRaindrop(@RequestBody RaindropConfig raindropConfig) {
        try {
            String configJson = objectMapper.writeValueAsString(raindropConfig);

            IntegrationConfig config = configRepository.findByType(IntegrationType.RAINDROP)
                    .orElseGet(() -> {
                        var c = new IntegrationConfig();
                        c.setType(IntegrationType.RAINDROP);
                        c.setEnabled(true);
                        return c;
                    });

            config.setConfig(configJson);
            return configRepository.save(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Raindrop config", e);
        }
    }

    @DeleteMapping("/raindrop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRaindrop() {
        configRepository.deleteByType(IntegrationType.RAINDROP);
    }
}
```

- [ ] **Step 4: Run the tests and confirm pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest"`
Expected: 2 status tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java
git commit -m "add GET /api/integrations/raindrop/status"
```

---

## Task 10: Add `GET /api/integrations/raindrop/collections` (TDD)

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java`
- Modify: `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java`

- [ ] **Step 1: Write the failing tests**

Append these test methods inside `IntegrationConfigControllerTest`:

```java
    @Test
    void collectionsReturnsListFromService() throws Exception {
        properties.getRaindrop().setApiToken("a-token");
        when(raindropService.listCollections()).thenReturn(List.of(
                new RaindropCollection(1L, "Apple"),
                new RaindropCollection(2L, "Banana")));

        mockMvc.perform(get("/api/integrations/raindrop/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Apple"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].title").value("Banana"));
    }

    @Test
    void collectionsReturns503WhenTokenNotConfigured() throws Exception {
        properties.getRaindrop().setApiToken("a-token"); // service-side check, not properties
        when(raindropService.listCollections()).thenThrow(new RaindropNotConfiguredException());

        mockMvc.perform(get("/api/integrations/raindrop/collections"))
                .andExpect(status().isServiceUnavailable());
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest.collectionsReturnsListFromService" --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest.collectionsReturns503WhenTokenNotConfigured"`
Expected: FAIL — endpoint doesn't exist (`404`).

- [ ] **Step 3: Add the endpoint**

In `IntegrationConfigController.java`, add the import:

```java
import org.bartram.myfeeder.integration.RaindropCollection;
import org.bartram.myfeeder.integration.RaindropService;
```

Add a `private final RaindropService raindropService;` field (after the other fields), and add this method below `raindropStatus`:

```java
    @GetMapping("/raindrop/collections")
    public List<RaindropCollection> raindropCollections() {
        return raindropService.listCollections();
    }
```

- [ ] **Step 4: Run all controller tests**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest"`
Expected: 4 tests pass (2 status + 2 collections).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java
git commit -m "add GET /api/integrations/raindrop/collections"
```

---

## Task 11: Update `PUT /api/integrations/raindrop` test for new body shape, restore other coverage

**Files:**
- Modify: `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java`

- [ ] **Step 1: Add the missing existing-coverage tests with the new shape**

Append inside `IntegrationConfigControllerTest`:

```java
    @Test
    void shouldListIntegrations() throws Exception {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setEnabled(true);
        when(configRepository.findAll()).thenReturn(List.of(config));

        mockMvc.perform(get("/api/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RAINDROP"));
    }

    @Test
    void shouldUpsertRaindropConfigWithCollectionIdOnly() throws Exception {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/integrations/raindrop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionId\":12345}"))
                .andExpect(status().isOk());

        verify(configRepository).save(any(IntegrationConfig.class));
    }

    @Test
    void shouldDeleteRaindropConfig() throws Exception {
        mockMvc.perform(delete("/api/integrations/raindrop"))
                .andExpect(status().isNoContent());

        verify(configRepository).deleteByType(IntegrationType.RAINDROP);
    }
```

- [ ] **Step 2: Run the full controller test suite**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest"`
Expected: 7 tests pass.

- [ ] **Step 3: Run the full backend test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all backend tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java
git commit -m "update PUT /api/integrations/raindrop test for collectionId-only body"
```

---

## Task 12: Helm + deploy.sh wiring

**Files:**
- Modify: `helm/myfeeder/values.yaml`
- Modify: `helm/myfeeder/templates/app-secret.yaml`
- Modify: `helm/myfeeder/templates/app-deployment.yaml`
- Modify: `deploy.sh`

- [ ] **Step 1: Add value to values.yaml**

In `helm/myfeeder/values.yaml`, replace:

```yaml
secrets:
  postgresPassword: ""
  anthropicApiKey: ""
  googleApplicationCredentials: ""
```

with:

```yaml
secrets:
  postgresPassword: ""
  anthropicApiKey: ""
  raindropApiToken: ""
  googleApplicationCredentials: ""
```

- [ ] **Step 2: Emit the secret entry**

In `helm/myfeeder/templates/app-secret.yaml`, replace:

```yaml
stringData:
  spring-datasource-username: {{ .Values.externalPostgres.username | quote }}
  spring-datasource-password: {{ .Values.secrets.postgresPassword | quote }}
  spring-ai-anthropic-api-key: {{ .Values.secrets.anthropicApiKey | quote }}
```

with:

```yaml
stringData:
  spring-datasource-username: {{ .Values.externalPostgres.username | quote }}
  spring-datasource-password: {{ .Values.secrets.postgresPassword | quote }}
  spring-ai-anthropic-api-key: {{ .Values.secrets.anthropicApiKey | quote }}
  myfeeder-raindrop-api-token: {{ .Values.secrets.raindropApiToken | quote }}
```

- [ ] **Step 3: Wire env var in app-deployment.yaml**

In `helm/myfeeder/templates/app-deployment.yaml`, after the `SPRING_AI_ANTHROPIC_API_KEY` env block (currently lines 43–47), insert before `JDK_JAVA_OPTIONS`:

```yaml
            - name: MYFEEDER_RAINDROP_API_TOKEN
              valueFrom:
                secretKeyRef:
                  name: {{ include "myfeeder.fullname" . }}-secret
                  key: myfeeder-raindrop-api-token
```

The full env section should now read in this order: `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_AI_ANTHROPIC_API_KEY`, `MYFEEDER_RAINDROP_API_TOKEN`, `JDK_JAVA_OPTIONS`, optional `GOOGLE_APPLICATION_CREDENTIALS`.

- [ ] **Step 4: Pass the value from deploy.sh**

Replace `deploy.sh` with:

```bash
#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-$(./gradlew currentVersion -q 2>&1 | grep 'Project version' | awk '{print $NF}')}"
REGISTRY="registry.bartram.org/bartram/myfeeder"
NAMESPACE="myfeeder"
RELEASE="myfeeder"
CHART="helm/myfeeder"

echo "Deploying myfeeder version: $VERSION"

# Raindrop is optional; default to empty so set -u doesn't trip.
RAINDROP_TOKEN="${MYFEEDER_RAINDROP_API_TOKEN:-}"
if [[ -z "$RAINDROP_TOKEN" ]]; then
  echo "Warning: MYFEEDER_RAINDROP_API_TOKEN is unset; Raindrop integration will be disabled in this deployment."
fi

helm upgrade --install "$RELEASE" "$CHART" \
  -n "$NAMESPACE" \
  --create-namespace \
  --set app.image.tag="$VERSION" \
  --set secrets.postgresPassword="$MYFEEDER_PG_PASSWORD" \
  --set secrets.anthropicApiKey="$MYFEEDER_ANTHROPIC_API_KEY" \
  --set secrets.raindropApiToken="$RAINDROP_TOKEN"
```

- [ ] **Step 5: Lint the helm chart**

Run: `helm lint helm/myfeeder`
Expected: `1 chart(s) linted, 0 chart(s) failed`.

- [ ] **Step 6: Render the chart and check the new env var is present**

Run: `helm template test-render helm/myfeeder --set secrets.raindropApiToken=tok-xyz | grep -A 3 MYFEEDER_RAINDROP_API_TOKEN`
Expected: shows the env block with `secretKeyRef` pointing to `myfeeder-raindrop-api-token`.

- [ ] **Step 7: Commit**

```bash
git add helm/myfeeder/values.yaml helm/myfeeder/templates/app-secret.yaml helm/myfeeder/templates/app-deployment.yaml deploy.sh
git commit -m "wire MYFEEDER_RAINDROP_API_TOKEN through helm and deploy.sh"
```

---

## Task 13: Frontend API types and functions

**Files:**
- Modify: `src/main/frontend/src/api/integrations.ts`

- [ ] **Step 1: Update the API client**

Replace `src/main/frontend/src/api/integrations.ts` with:

```ts
import { apiGet, apiPut, apiDelete } from './client'

export interface IntegrationConfig {
  id: number
  type: 'RAINDROP'
  config: string
  enabled: boolean
}

export interface RaindropConfig {
  collectionId: number
}

export interface RaindropCollection {
  id: number
  title: string
}

export interface RaindropStatus {
  configured: boolean
}

export const integrationsApi = {
  getAll: () => apiGet<IntegrationConfig[]>('/integrations'),
  getRaindropStatus: () => apiGet<RaindropStatus>('/integrations/raindrop/status'),
  listRaindropCollections: () =>
    apiGet<RaindropCollection[]>('/integrations/raindrop/collections'),
  upsertRaindrop: (config: RaindropConfig) =>
    apiPut<IntegrationConfig>('/integrations/raindrop', config),
  deleteRaindrop: () => apiDelete('/integrations/raindrop'),
}
```

- [ ] **Step 2: Verify types compile**

Run: `cd src/main/frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/api/integrations.ts
git commit -m "add raindrop status and collections to frontend api client"
```

---

## Task 14: Refactor `SettingsDialog` to use the dropdown (TDD)

**Files:**
- Create: `src/main/frontend/src/components/SettingsDialog.test.tsx`
- Modify: `src/main/frontend/src/components/SettingsDialog.tsx`

- [ ] **Step 1: Write the failing tests**

Create `src/main/frontend/src/components/SettingsDialog.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SettingsDialog } from './SettingsDialog'
import { integrationsApi } from '../api/integrations'

vi.mock('../api/integrations', () => ({
  integrationsApi: {
    getRaindropStatus: vi.fn(),
    listRaindropCollections: vi.fn(),
    upsertRaindrop: vi.fn(),
    getAll: vi.fn(),
    deleteRaindrop: vi.fn(),
  },
}))

vi.mock('../stores/preferencesStore', () => ({
  usePreferences: () => ({
    theme: 'dark',
    setTheme: vi.fn(),
    hideReadFeeds: false,
    setHideReadFeeds: vi.fn(),
    hideReadArticles: false,
    setHideReadArticles: vi.fn(),
    autoMarkReadDelay: 0,
    setAutoMarkReadDelay: vi.fn(),
    articleSortOrder: 'newest-first',
    setArticleSortOrder: vi.fn(),
  }),
}))

vi.mock('../themes', () => ({ themeList: [{ id: 'dark', name: 'Dark', type: 'dark' }] }))

describe('SettingsDialog Raindrop section', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(integrationsApi.getAll).mockResolvedValue([])
  })

  it('shows not-configured notice when status.configured is false', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: false })

    render(<SettingsDialog open={true} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByText(/not configured by the administrator/i)).toBeInTheDocument()
    })
    expect(integrationsApi.listRaindropCollections).not.toHaveBeenCalled()
  })

  it('renders collections sorted alphabetically when configured', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: true })
    vi.mocked(integrationsApi.listRaindropCollections).mockResolvedValue([
      { id: 1, title: 'Apple' },
      { id: 2, title: 'Banana' },
    ])
    vi.mocked(integrationsApi.getAll).mockResolvedValue([])

    render(<SettingsDialog open={true} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /raindrop collection/i })).toBeInTheDocument()
    })
    const options = screen.getAllByRole('option')
    const titles = options.map((o) => o.textContent)
    expect(titles).toEqual(expect.arrayContaining(['Apple', 'Banana']))
  })

  it('saves the selected collection without sending an apiToken', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: true })
    vi.mocked(integrationsApi.listRaindropCollections).mockResolvedValue([
      { id: 1, title: 'Apple' },
      { id: 2, title: 'Banana' },
    ])
    vi.mocked(integrationsApi.getAll).mockResolvedValue([])
    vi.mocked(integrationsApi.upsertRaindrop).mockResolvedValue({
      id: 1, type: 'RAINDROP', config: '{"collectionId":2}', enabled: true,
    })

    render(<SettingsDialog open={true} onClose={() => {}} />)

    const select = await screen.findByRole('combobox', { name: /raindrop collection/i })
    await userEvent.selectOptions(select, '2')
    await userEvent.click(screen.getByRole('button', { name: /save raindrop config/i }))

    await waitFor(() => {
      expect(integrationsApi.upsertRaindrop).toHaveBeenCalledWith({ collectionId: 2 })
    })
  })

  it('shows placeholder option when saved collection is no longer in the list', async () => {
    vi.mocked(integrationsApi.getRaindropStatus).mockResolvedValue({ configured: true })
    vi.mocked(integrationsApi.listRaindropCollections).mockResolvedValue([
      { id: 1, title: 'Apple' },
    ])
    vi.mocked(integrationsApi.getAll).mockResolvedValue([
      { id: 1, type: 'RAINDROP', config: '{"collectionId":999}', enabled: true },
    ])

    render(<SettingsDialog open={true} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByText(/saved collection no longer exists/i)).toBeInTheDocument()
    })
  })
})
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `cd src/main/frontend && npm test -- SettingsDialog`
Expected: FAIL — current `SettingsDialog` does not call `getRaindropStatus`/`listRaindropCollections` and has no "not configured" copy.

- [ ] **Step 3: Rewrite SettingsDialog**

Replace `src/main/frontend/src/components/SettingsDialog.tsx` with:

```tsx
import { useEffect, useState } from 'react'
import { usePreferences } from '../stores/preferencesStore'
import { integrationsApi, type RaindropCollection } from '../api/integrations'
import { themeList } from '../themes'

interface SettingsDialogProps {
  open: boolean
  onClose: () => void
}

type RaindropState =
  | { phase: 'loading' }
  | { phase: 'not-configured' }
  | { phase: 'ready'; collections: RaindropCollection[]; savedId: number | null; selectedId: number | null }
  | { phase: 'error'; message: string }

function readSavedCollectionId(configJson: string): number | null {
  try {
    const parsed = JSON.parse(configJson) as { collectionId?: number }
    return typeof parsed.collectionId === 'number' ? parsed.collectionId : null
  } catch {
    return null
  }
}

export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
  const prefs = usePreferences()
  const [raindrop, setRaindrop] = useState<RaindropState>({ phase: 'loading' })

  useEffect(() => {
    if (!open) return
    let cancelled = false

    async function load() {
      try {
        const status = await integrationsApi.getRaindropStatus()
        if (cancelled) return
        if (!status.configured) {
          setRaindrop({ phase: 'not-configured' })
          return
        }
        const [collections, all] = await Promise.all([
          integrationsApi.listRaindropCollections(),
          integrationsApi.getAll(),
        ])
        if (cancelled) return
        const existing = all.find((c) => c.type === 'RAINDROP')
        const savedId = existing ? readSavedCollectionId(existing.config) : null
        const inList = savedId != null && collections.some((c) => c.id === savedId)
        setRaindrop({
          phase: 'ready',
          collections,
          savedId,
          selectedId: inList ? savedId : (collections[0]?.id ?? null),
        })
      } catch (e) {
        if (!cancelled) {
          setRaindrop({ phase: 'error', message: e instanceof Error ? e.message : String(e) })
        }
      }
    }

    setRaindrop({ phase: 'loading' })
    void load()
    return () => {
      cancelled = true
    }
  }, [open])

  if (!open) return null

  const handleSaveRaindrop = async () => {
    if (raindrop.phase !== 'ready' || raindrop.selectedId == null) return
    await integrationsApi.upsertRaindrop({ collectionId: raindrop.selectedId })
  }

  const renderRaindropSection = () => {
    if (raindrop.phase === 'loading') {
      return <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Loading…</div>
    }
    if (raindrop.phase === 'not-configured') {
      return (
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          Raindrop is not configured by the administrator. Set <code>myfeeder.raindrop.api-token</code> in deployment values to enable.
        </div>
      )
    }
    if (raindrop.phase === 'error') {
      return (
        <div style={{ fontSize: 13, color: 'var(--text-error, crimson)' }}>
          Failed to load Raindrop settings: {raindrop.message}
        </div>
      )
    }
    const inList = raindrop.savedId != null && raindrop.collections.some((c) => c.id === raindrop.savedId)
    return (
      <>
        <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
          Raindrop collection
          <select
            aria-label="Raindrop collection"
            className="dialog-input"
            value={raindrop.selectedId ?? ''}
            onChange={(e) =>
              setRaindrop({ ...raindrop, selectedId: Number(e.target.value) })
            }
            style={{ marginTop: 4 }}
          >
            {!inList && raindrop.savedId != null && (
              <option value="" disabled>
                (saved collection no longer exists — pick again)
              </option>
            )}
            {raindrop.collections.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
        <button className="btn-primary" onClick={handleSaveRaindrop} style={{ marginTop: 8 }}>
          Save Raindrop Config
        </button>
      </>
    )
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ width: 500 }}>
        <h2>Settings</h2>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Appearance</h3>
          <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
            Theme
            <select
              className="dialog-input"
              value={prefs.theme}
              onChange={(e) => prefs.setTheme(e.target.value)}
              style={{ marginTop: 4 }}
            >
              {themeList.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.type})
                </option>
              ))}
            </select>
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
            <input
              type="checkbox"
              checked={prefs.hideReadFeeds}
              onChange={(e) => prefs.setHideReadFeeds(e.target.checked)}
            />
            Hide feeds with no unread articles
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
            <input
              type="checkbox"
              checked={prefs.hideReadArticles}
              onChange={(e) => prefs.setHideReadArticles(e.target.checked)}
            />
            Hide read articles
          </label>
        </div>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Reading</h3>
          <label style={{ display: 'block', marginBottom: 8, fontSize: 13, color: 'var(--text-muted)' }}>
            Auto-mark as read delay (ms, 0 to disable)
            <input
              className="dialog-input"
              type="number"
              value={prefs.autoMarkReadDelay}
              onChange={(e) => prefs.setAutoMarkReadDelay(Number(e.target.value))}
              style={{ marginTop: 4 }}
            />
          </label>
          <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
            Sort order
            <select
              className="dialog-input"
              value={prefs.articleSortOrder}
              onChange={(e) => prefs.setArticleSortOrder(e.target.value as 'newest-first' | 'oldest-first')}
              style={{ marginTop: 4 }}
            >
              <option value="newest-first">Newest first</option>
              <option value="oldest-first">Oldest first</option>
            </select>
          </label>
        </div>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Raindrop.io</h3>
          {renderRaindropSection()}
        </div>

        <div className="dialog-actions">
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run the SettingsDialog tests**

Run: `cd src/main/frontend && npm test -- SettingsDialog`
Expected: 4 SettingsDialog tests pass.

- [ ] **Step 5: Run the full frontend test suite**

Run: `cd src/main/frontend && npm test`
Expected: all tests pass. (If any pre-existing test relied on the old `apiToken`/`Collection ID` text inputs, update it minimally to the new shape — but do not write new tests beyond what this plan specifies.)

- [ ] **Step 6: Verify TypeScript compiles**

Run: `cd src/main/frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add src/main/frontend/src/components/SettingsDialog.tsx src/main/frontend/src/components/SettingsDialog.test.tsx
git commit -m "replace raindrop token+id inputs with status check and sorted dropdown"
```

---

## Task 15: Manual end-to-end verification

**Files:** none

This task is not automatable; perform the steps in order and check each off as you confirm it.

- [ ] **Step 1: Start the backend without a token**

Run (in one terminal): `./gradlew bootTestRun`
Wait for the Spring banner and "Started MyfeederApplication" log line.

- [ ] **Step 2: Confirm `/status` reports not configured**

Run: `curl -s http://localhost:8080/api/integrations/raindrop/status`
Expected: `{"configured":false}`

- [ ] **Step 3: Confirm `/collections` returns 503**

Run: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/integrations/raindrop/collections`
Expected: `503`

- [ ] **Step 4: Confirm the "save to raindrop" article action returns 503**

Pick any existing article id (e.g. via `curl -s http://localhost:8080/api/articles | jq '.items[0].id'`).
Run: `curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/articles/<ID>/raindrop`
Expected: `503`

- [ ] **Step 5: Stop and restart with a real token**

Stop the previous run. Restart with: `MYFEEDER_RAINDROP_API_TOKEN=<real-token> ./gradlew bootTestRun`

- [ ] **Step 6: Confirm `/status` flips to configured**

Run: `curl -s http://localhost:8080/api/integrations/raindrop/status`
Expected: `{"configured":true}`

- [ ] **Step 7: Confirm `/collections` returns sorted titles**

Run: `curl -s http://localhost:8080/api/integrations/raindrop/collections | jq '[.[] | .title]'`
Expected: list of your real Raindrop collection titles, sorted alphabetically (case-insensitive).

- [ ] **Step 8: Verify the settings dialog in the browser**

In a second terminal: `cd src/main/frontend && npm run dev`
Open http://localhost:5173, open Settings → Raindrop.io, confirm:
- Dropdown is populated with your collection titles in alpha order.
- Selecting a collection and clicking Save persists without errors (re-opening the dialog re-selects the same collection).

- [ ] **Step 9: Save an article to Raindrop end-to-end**

Open an article in the reading pane and click 💧 Raindrop. Then check raindrop.io in your browser — the article should appear in the chosen collection within a few seconds.

- [ ] **Step 10: Confirm legacy data is migrated**

If you had a Raindrop integration row in your dev DB before this change, run:
`curl -s http://localhost:8080/api/integrations | jq '.[] | select(.type=="RAINDROP") | .config'`
Expected: a JSON string of the form `{"collectionId":<id>}` with no `apiToken` field.

---

## Self-Review Notes

- All spec requirements are covered: token relocation (Tasks 1, 12), client interface + impl (Tasks 3–5), service refactor + sort + cache (Task 8), `/status` + `/collections` + new PUT shape (Tasks 9–11), Flyway migration (Task 6), DTO simplification (Task 7), 503 mapping (Task 2), helm + deploy.sh (Task 12), frontend (Tasks 13–14), end-to-end verification (Task 15).
- Type names are stable across tasks: `RaindropCollection`, `RaindropApiClient`, `RaindropApiClientImpl`, `RaindropNotConfiguredException`, `RaindropConfig` (with single `collectionId` field), and the `{ configured: boolean }` status shape are referenced consistently.
- Migration is `V4` (not `V3` as the spec drafted) because `V3__article_image_url.sql` already exists.
- The `IllegalStateException` → 409 mapping is intentionally left in place for the existing "not configured / disabled" code paths in `RaindropService.saveToRaindrop`. The user-facing toast in the frontend already surfaces this; only the **deployment-token-missing** state uses the new 503 code path.
