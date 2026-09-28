---
phase: 01-dependency-upgrade
reviewed: 2026-09-22T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - CLAUDE.md
  - build.gradle.kts
  - src/main/frontend/package.json
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java
findings:
  critical: 0
  warning: 2
  info: 5
  total: 7
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-09-22T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

I reviewed the phase diff `151706f^..HEAD` for the five files in scope. For CLAUDE.md I looked only at the committed lines. I did not review the uncommitted working-copy edits.

I checked the phase's main claims against the Spring Boot 4.0.8 jars and the build:

- **D-02 is correct.** In `spring-boot-http-client-4.0.8.jar`, `HttpClientsProperties` is bound to `@ConfigurationProperties("spring.http.clients")`. The `spring.http.client.*` keys exist only as deprecated metadata (`since: 4.0.0`, with a replacement listed) and no class binds them. So before this phase the 5s/30s timeouts never took effect, and the rename fixes that.
- **D-01 works today.** The resolved `runtimeClasspath` has `reactor-netty-http 1.3.7`. It has no `httpclient5` or `jetty-client`, which Boot would pick ahead of Reactor Netty. Spring AI 2.0.1's Anthropic client now uses OkHttp through `anthropic-java-core`, which explains why Reactor Netty stopped arriving transitively.
- **The new test passes** (`./gradlew test --tests ...HttpClientConfigurationTest`).
- **The frontend lockfile matches.** Every range in `package.json` matches the lock's root entry, and the resolved versions stay within their current majors.
- **Scope rule D-03 holds.** Nothing changed outside the stated targets.

I found no blockers. The two warnings:

1. The timeout fix gives weaker protection than the comments and CLAUDE.md say. Reactor Netty's read timeout limits idle time between reads. It does not cap the total time of a request.
2. The new "guard" test does not cover the most likely way the transport would change: a `spring.http.clients.imperative.factory` override in `application.yaml`.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: `read-timeout: 30s` limits idle time between reads, not total request time, so a slow remote can still hold a thread much longer

**File:** `src/main/resources/application.yaml:4-9` (also `CLAUDE.md:149`)
**Issue:** The phase now makes `spring.http.clients.read-timeout` actually bind. On the Reactor Netty transport that D-01 keeps, Boot's `ReactorHttpClientBuilder` applies this value as `HttpClient.responseTimeout(...)`. Reactor Netty defines that as "the maximum duration allowed between each network-level read operation while reading a given response". It is an idle timeout, not a limit on the whole request. Spring's `ReactorClientHttpRequestFactory` no longer sets an exchange-level deadline by default (the 5s `exchangeTimeout` default was dropped in Framework 6.2).

`FeedFetcher.fetch` reads up to `DEFAULT_MAX_FEED_BYTES` (10 MiB) through `readNBytes`. A server that sends one byte every 29 seconds never trips the timeout, so it can hold a polling-scheduler thread or a servlet request thread almost indefinitely. `ArticleExtractionService` shares this path. On the Raindrop path, the 3-attempt `@Retry` multiplies the worst case.

The comment added or edited in this phase says the setting will "bound how long a slow remote can tie up a request/scheduler thread". The CLAUDE.md line says "Currently 5s/30s ... guarded by HttpClientConfigurationTest". Both overstate the protection, and D-02 was scoped around exactly this risk.
**Fix:** At minimum, correct the comment so nobody relies on a limit that doesn't exist:
```yaml
  # Applies to the auto-configured RestClient (feed fetches + Raindrop). connect-timeout bounds
  # connection setup; read-timeout is an *idle* timeout between reads (Reactor Netty
  # responseTimeout), NOT a total deadline -- a slow-drip body can still hold the thread longer.
```
For a real total limit, add one in `FeedFetcher`. For example, track the elapsed time inside the bounded read loop and throw `FeedFetchException` past a total budget. Another option is a `ClientHttpRequestFactoryBuilderCustomizer<ReactorClientHttpRequestFactoryBuilder>` that adds a Netty `ReadTimeoutHandler` plus an overall deadline. Record whichever you pick in CLAUDE.md.

### WR-02: `HttpClientConfigurationTest` does not catch a transport change made in configuration, although CLAUDE.md says it guards the transport

**File:** `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java:31-35`
**Issue:** `outboundTransportIsReactorNetty` starts a context without loading the main `application.yaml`. Only `outboundTimeoutsFromMainApplicationYamlBind` injects that file. So a production override such as `spring.http.clients.imperative.factory: jdk` (or `http-components`, `jetty`) in `src/main/resources/application.yaml` would still let this test pass while production changes transport. That is the most direct way the transport could change without anyone noticing.

The test also asserts on the `ClientHttpRequestFactory` bean. The `RestClient.Builder` that `FeedFetcher` and Raindrop use comes from `RestClientAutoConfiguration` → `RestClientBuilderConfigurer`, which uses `ClientHttpRequestFactoryBuilder` plus `HttpClientSettings` and not that bean. Today both are built from the same builder, so they agree. But a `RestClientCustomizer` that calls `builder.requestFactory(...)` would change the real transport and the test would not notice.

CLAUDE.md (committed line 168) says the transport is "guarded by `HttpClientConfigurationTest`", which is broader than what the test checks.
**Fix:** Load the main YAML in the transport test as well. Put it in a shared initializer so both tests see production configuration:
```java
private static PropertySource<?> mainYaml() throws IOException {
    return new YamlPropertySourceLoader()
            .load("main-application-yaml", new FileSystemResource("src/main/resources/application.yaml"))
            .getFirst();
}

@Test
void outboundTransportIsReactorNetty() throws Exception {
    PropertySource<?> yaml = mainYaml();
    runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(yaml))
          .run(ctx -> assertThat(ctx.getBean(ClientHttpRequestFactory.class))
                  .isInstanceOf(ReactorClientHttpRequestFactory.class));
}
```
Optionally, also add `RestClientAutoConfiguration` and `RestClientConfig` (with a stub `BuildProperties`). Then build a `RestClient` from the auto-configured `RestClient.Builder` and check its request factory, so the test covers the path the app actually uses.

## Info

### IN-01: The build comment says "Pins", but the dependency only makes Reactor Netty detectable

**File:** `build.gradle.kts:37-38`
**Issue:** `ClientHttpRequestFactoryBuilder.detect(...)` checks in this order: Apache HttpComponents, then Jetty, then Reactor Netty, then JDK. Adding `reactor-netty-http` makes Reactor Netty available, but it does not pin it. If a later transitive dependency adds `httpclient5` or `jetty-client`, Boot switches transport without any warning. The CLAUDE.md gotcha (line 168) repeats the word "pinned". The test does catch a classpath-driven switch, so this is not a warning.
**Fix:** If a hard pin is wanted, also set `spring.http.clients.imperative.factory: reactor` in `application.yaml`, and cover it with the fixed test from WR-02. Otherwise, reword the comment to "Makes Reactor Netty the detected RestClient transport ...".

### IN-02: Timeouts that now fire surface as unmapped `ResourceAccessException` (HTTP 500) on subscribe

**File:** `src/main/resources/application.yaml:8-9` (the call path goes through `service/FeedService.java:38` → `FeedFetcher.fetch`)
**Issue:** Now that the connect and read timeouts bind, a slow feed URL on `POST /api/feeds` produces a `ResourceAccessException`. `GlobalExceptionHandler` has no mapping for it, so the client gets a generic 500 instead of the 422 that `FeedFetchException` would give. Before this phase, the same request just hung. Connection-refused failures already had this gap, so it is not new, but D-02 makes it more likely to happen. The polling and extraction paths catch `Exception` and are unaffected.
**Fix:** Wrap `RestClientException` / `ResourceAccessException` inside `FeedFetcher.fetch` as `FeedFetchException("Timed out / unreachable fetching " + url, e)`. That keeps the documented contract: remote problem → 422.

### IN-03: The test loads `application.yaml` by a path relative to the working directory

**File:** `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java:41`
**Issue:** `new FileSystemResource("src/main/resources/application.yaml")` depends on the JVM working directory being the project root. Gradle does this by default, but some IDE run configurations and a changed `Test.workingDir` do not. If the path is wrong the test fails loudly with a missing file, so it won't pass by mistake, but it is brittle.
**Fix:** Resolve the path from a Gradle-provided system property, e.g. `systemProperty("project.root", projectDir.absolutePath)` in `tasks.withType<Test>`, then `Path.of(System.getProperty("project.root"), "src/main/resources/application.yaml")`.

### IN-04: The CLAUDE.md timeout note gets the history wrong

**File:** `CLAUDE.md:149`
**Issue:** "...was false until the 4.0.8 upgrade renamed them" suggests Boot 4.0.8 renamed the keys. Boot moved to `spring.http.clients.*` in 4.0.0; this project renamed its own keys in commit `fce474b`. Also, a guidance file should describe the current rule, not a change log ("the old claim ... was false").
**Fix:** "Use `spring.http.clients.*` (plural). The singular `spring.http.client.*` keys have been deprecated since Boot 4.0.0 and are not bound; nothing warns you if you use them."

### IN-05: Minor inconsistencies in the frontend devDependencies

**File:** `src/main/frontend/package.json:26,30`
**Issue:** `@eslint/js` stays at `^9.39.4` while `eslint` moved to `^9.39.5`. The lock already resolves `@eslint/js` 9.39.5, so the ranges are simply out of step. Separately, `@types/dompurify@^3.0.5` does nothing: `dompurify` 3.x ships its own types, and TypeScript uses those before `@types`. This line predates the phase, but the phase refreshed the dependency list without catching it.
**Fix:** Bump `@eslint/js` to `^9.39.5` to match `eslint`, and drop `@types/dompurify` in a follow-up. Both are out of D-03 scope for this phase, so defer them.

---

_Reviewed: 2026-09-22T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
