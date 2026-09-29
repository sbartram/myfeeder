# Phase 1: Dependency Upgrade - Pattern Map

**Mapped:** 2026-09-22
**Files analyzed:** 8 (6 modified, 1 optional new, 1 optional modified)
**Analogs found:** 8 / 8 (most files are modified in place, so the file itself is the pattern)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `build.gradle.kts` | config (build) | batch (dependency resolution) | itself: lines 3, 28-29, 36 | exact (in-place edit) |
| `src/main/resources/application.yaml` | config | request-response (outbound HTTP settings) | itself: lines 4-9 | exact (in-place edit) |
| `src/main/frontend/package.json` | config (npm) | batch | itself (`npm update --save` rewrites ranges) | exact |
| `src/main/frontend/package-lock.json` | config (lockfile) | batch | itself (generated) | exact |
| `CLAUDE.md` (root) | docs | n/a | itself: lines 7, 36, 149, 168 | exact |
| `.planning/codebase/STACK.md` | docs | n/a | itself: lines 42, 122, 154 | exact |
| `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` (OPTIONAL, new; the `config` test package does not exist yet) | test (config/auto-config) | request-response (outbound HTTP client wiring) | `src/test/java/org/bartram/myfeeder/controller/VersionControllerTest.java` (inline test config) + `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java` (context test) | role-match |
| `src/test/resources/application.yaml` (only if the test goes the `@SpringBootTest` route) | config (test) | n/a | itself | exact |

All paths above are git-tracked (checked with `git ls-files`).

## Pattern Assignments

### `build.gradle.kts` (build config)

**Analog:** itself. The only lines that change are:

- Line 3: `id("org.springframework.boot") version "4.0.3"` → `"4.0.8"`
- Line 28: `extra["springAiVersion"] = "2.0.0-M2"` → `"2.0.1"`
- Line 29: `extra["springCloudVersion"] = "2025.1.0"` → `"2025.1.3"`
- D-01: add one line in the `dependencies {}` block, next to the restclient starter (line 36). Use no version string, because the BOM manages it:

```kotlin
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("io.projectreactor.netty:reactor-netty-http")
```

**Convention to follow** (lines 31-45): tab indentation. Boot, Spring AI and Spring Cloud artifacts have **no** version (for example `implementation("org.springframework.ai:spring-ai-starter-model-anthropic")`). Only non-BOM libraries carry versions (`rome:2.1.0`, `readability4j:1.0.8`), and D-03 says not to touch them. BOM imports stay as they are (lines 68-73):

```kotlin
dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}
```

**Do not touch:**
- line 5 (axion 1.21.1)
- line 92 (`DOCKER_HOST` default, which points at the Rancher path). Override it with an env var at run time instead (RESEARCH Pitfall 1).
- the `release`/`pushRelease`/`gitPushRelease` block (lines 75-88)

No new test dependency is needed for the optional test. `spring-boot-starter-restclient-test` (line 59) already brings `spring-boot-test`, which provides `ApplicationContextRunner`.

---

### `src/main/resources/application.yaml` (config, D-02)

**Analog:** itself. Current state (lines 4-9):

```yaml
  # Applies to the auto-configured RestClient (feed fetches + Raindrop): bound how long a slow
  # remote can tie up a request/scheduler thread. Without these the JDK client waits indefinitely.
  http:
    client:
      connect-timeout: 5s
      read-timeout: 30s
```

Change `client:` to `clients:` (line 7), and put this change in its own commit (D-02). The comment on line 5 names "the JDK client", which is wrong now that D-01 keeps Reactor Netty. Fix that comment in the same D-02 commit, for example "Without these the HTTP client waits indefinitely." Keep two-space YAML indentation and the comment style.

---

### `src/main/frontend/package.json` + `package-lock.json` (npm config)

**Analog:** itself. Produce it with `cd src/main/frontend && npm update --save`. Do not hand-edit, with one exception: `npm update --save` leaves `"@eslint/js": "^9.39.4"` (line 26) even though it installs 9.39.5 (RESEARCH quirk). Bump that range by hand only if the verifier compares package.json floors. `"react-router-dom": "^6.30.3"` (line 22) must stay `^6.x`. `"typescript": "~5.9.3"` (line 40) stays unchanged.

---

### `CLAUDE.md` (root docs)

**Analog:** itself. The lines to edit:
- **Line 7** and **line 36**: `Spring Boot 4.0.3` → `4.0.8`
- **Line 149** (D-02). Current text: "…are set via `spring.http.client.connect-timeout` / `read-timeout` / `redirects` … Currently 5s/30s in `application.yaml`." Change it to `spring.http.clients.*`. Optionally add that the old `spring.http.client.*` keys are deprecated since Boot 4.0.0 and are not bound.
- **Line 168** (D-01). Current text: "the JDK HttpClient default is `Java-http-client/<version>`…". Say that the auto-configured transport is Reactor Netty (kept by the explicit `reactor-netty-http` dependency) and that the UA customizer still applies regardless of the transport.

**Caution (commit hygiene):** root `CLAUDE.md` already has **uncommitted user edits** that are not phase work. The hunks are at lines 40, 55-56, 99 and 105 (`git diff -U0 CLAUDE.md`). The line-36 edit falls inside the same default `git add -p` hunk as the user's line-40 edit. The executor must stage only the phase lines, for example with `git add -p` and then `s` (split) or `e`, and must never run `git add CLAUDE.md` or `git commit -a`. The same applies to the dirty `.claude/CLAUDE.md`, which must not be staged at all.

---

### `.planning/codebase/STACK.md` (docs)

**Analog:** itself.
- Lines 42 and 122: `Spring Boot 4.0.3` → `4.0.8`
- Line 154: `spring.http.client.{connect-timeout: 5s, read-timeout: 30s}` → `spring.http.clients.{…}`

---

### `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` (OPTIONAL test, D-01/D-02)

No test in the repo exercises auto-configuration directly. No test uses `ApplicationContextRunner`, and the only `@SpringBootTest` is the Testcontainers-backed full context. The analogs below supply the conventions. The auto-config class names come from the 4.0.8 jars.

**Analog A (full-context style):** `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java` (lines 1-33):

```java
package org.bartram.myfeeder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MyfeederApplicationTests {
    @Autowired private FeedController feedController;
    ...
    @Test
    void contextLoads() {
        assertThat(feedController).isNotNull();
```

Conventions: package-private class, `@Autowired private` fields, AssertJ `assertThat` static import, 4-space indent. Any `@SpringBootTest` needs Docker, because `TestcontainersConfiguration` is `@Import`ed. Without it, DataSource, Flyway and Redis cannot start.

**Analog B (inline test config):** `src/test/java/org/bartram/myfeeder/controller/VersionControllerTest.java` (lines 18-31). This shows how the repo supplies a `BuildProperties` bean in a slice context. `RestClientConfig.userAgentCustomizer(BuildProperties)` needs this bean if `RestClientConfig` is included:

```java
    static class TestConfig {
        @Bean
        BuildProperties buildProperties() {
            Properties props = new Properties();
            props.setProperty("version", "1.2.3");
            props.setProperty("time", Instant.parse("2026-04-27T19:00:00Z").toString());
            return new BuildProperties(props);
        }
    }
```

**Recommended shape (no Docker, fast): `ApplicationContextRunner` over the Boot 4.0.8 HTTP-client auto-configs.** Verified from the jars:
- `spring-boot-http-client-4.0.8.jar` provides `org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration`. Its `httpClientSettings(ObjectProvider<SslBundles>, HttpClientsProperties)` returns an `HttpClientSettings` record with `connectTimeout()`, `readTimeout()` and `redirects()`.
- The same jar provides `org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration`. It exposes a `ClientHttpRequestFactoryBuilder<?>` bean and a `ClientHttpRequestFactory clientHttpRequestFactory(builder, settings)` bean.
- The static `org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder.detect()` exists and returns `ReactorClientHttpRequestFactoryBuilder` when reactor-netty is on the classpath.
- `spring-boot-restclient-4.0.8.jar` provides `org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration`. Include it only if the test asserts on the `RestClient.Builder`.

```java
package org.bartram.myfeeder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ReactorClientHttpRequestFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class));

    @Test
    void outboundTransportIsReactorNetty() {           // D-01
        runner.run(ctx -> assertThat(ctx.getBean(ClientHttpRequestFactory.class))
                .isInstanceOf(ReactorClientHttpRequestFactory.class));
    }

    @Test
    void clientsTimeoutKeysBind() {                    // D-02
        runner.withPropertyValues("spring.http.clients.connect-timeout=5s",
                                  "spring.http.clients.read-timeout=30s")
              .run(ctx -> {
                  HttpClientSettings s = ctx.getBean(HttpClientSettings.class);
                  assertThat(s.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                  assertThat(s.readTimeout()).isEqualTo(Duration.ofSeconds(30));
              });
    }
}
```

The executor should confirm two details at compile time:
- the package of `AutoConfigurations` in Boot 4 (expected `org.springframework.boot.autoconfigure.AutoConfigurations`)
- whether `ImperativeHttpClientAutoConfiguration` has an `@ConditionalOnClass` or other condition that the runner has to satisfy

**Critical pitfall: the test `application.yaml` shadows the main one.** `src/test/resources/application.yaml` has the same resource name as `src/main/resources/application.yaml`. On the Gradle test classpath, `build/resources/test` comes first, and Boot's `classpath:/application.yaml` lookup takes only the first match. This is why the test yaml repeats all the `myfeeder.*` and `resilience4j.*` blocks. The consequences:
- A `@SpringBootTest` does **not** see the main `spring.http.clients.*` keys. Asserting 5s/30s there would fail unless you also add the keys to `src/test/resources/application.yaml`, and adding them only proves the key *name* binds, not that the main file is right.
- `ApplicationContextRunner` loads no yaml at all. `withPropertyValues(...)` proves the key name binds, which is the actual D-02 bug.
- To also guard the main file's contents, load it explicitly in the test. Use `new YamlPropertySourceLoader().load("main", new FileSystemResource("src/main/resources/application.yaml"))`; Gradle's test working directory is the project root. Then add it to the runner environment through `.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(...))`. Alternatively, assert the raw key `spring.http.clients.read-timeout` is present in it.

**Testing conventions to copy** (from `FeedFetcherTest.java` lines 1-35 and `RaindropApiClientImplTest.java` lines 1-31):
- Package-private class, no `public`, and `@Test` methods named in camelCase describing the behavior.
- AssertJ only (`assertThat`, `assertThatThrownBy`). No Hamcrest.
- Existing HTTP tests use `MockRestServiceServer.bindTo(RestClient.builder())`. That **replaces** the request factory, so it cannot detect a transport change (RESEARCH D-1). Do not use it for this test.

---

## Shared Patterns

### BOM-managed versions
**Source:** `build.gradle.kts` lines 31-45, 68-73; root CLAUDE.md "Key Conventions"
**Apply to:** every Gradle dependency line added in this phase (only `reactor-netty-http`)
Leave the version off every Boot, Spring AI and Spring Cloud managed artifact. Never pin Jackson, Micrometer or Spring to the 4.1.x versions that Spring AI 2.0.1 was built against.

### Auto-configured RestClient.Builder only
**Source:** `src/main/java/org/bartram/myfeeder/config/RestClientConfig.java` lines 9-17; `FeedFetcher.java` lines 12-13 (javadoc: "The RestClient.Builder is the auto-configured bean, so the myfeeder User-Agent customizer applies")
**Apply to:** D-01 implementation and the optional test
Pin the transport through the classpath dependency. Do not hand-build a `ClientHttpRequestFactory` or `RestClient.builder()` in main code.

### Test application.yaml requirements
**Source:** `src/test/resources/application.yaml` lines 1-43
**Apply to:** any `@SpringBootTest` added or run in this phase
The file must keep `spring.ai.anthropic.api-key: test-dummy-key` (lines 2-4). The property name is unchanged in Spring AI 2.0.1. It must also keep the full `myfeeder.*` and `resilience4j.*` blocks, because it shadows the main file.

### Commit hygiene
**Source:** git status (dirty `CLAUDE.md` and `.claude/CLAUDE.md` from the user's GSD install)
**Apply to:** every commit in this phase
Stage files explicitly by path. For root `CLAUDE.md`, use `git add -p` and split hunks. Keep the D-02 yaml fix in its own commit, separate from the version bump.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| (partial) `config/HttpClientConfigurationTest.java` | test | request-response | No existing test uses `ApplicationContextRunner` or asserts on auto-configuration. The conventions come from the analogs above, and the auto-config API was verified from the Boot 4.0.8 jars. |

## Metadata

**Analog search scope:** `src/test/java/org/bartram/myfeeder/**` (32 tracked test files), `src/test/resources/`, `src/main/java/org/bartram/myfeeder/config/`, `build.gradle.kts`, `src/main/resources/application.yaml`, `src/main/frontend/package.json`, root `CLAUDE.md`, `.planning/codebase/STACK.md`, and the Boot 4.0.8 `spring-boot-http-client` / `spring-boot-restclient` jars in `~/.gradle/caches` (inspected with `javap`/`unzip`)
**Files scanned:** ~14
**Pattern extraction date:** 2026-09-22
