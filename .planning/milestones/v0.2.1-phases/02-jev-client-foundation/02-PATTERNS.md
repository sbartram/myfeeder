# Phase 2: Jev Client Foundation - Pattern Map

**Mapped:** 2026-09-22
**Files analyzed:** 17 (8 new Java, 9 modified config/deploy/docs)
**Analogs found:** 15 / 17 (2 have only partial or no tracked analog)

All analog paths below are git-tracked. They were verified with `git ls-files`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `build.gradle.kts` (M) | config | n/a | itself: lines 28-29 (`extra[...]`), 36-42 (implementation block) | exact |
| `src/main/resources/application.yaml` (M) | config | n/a | itself: lines 19-42 (raindrop props + resilience4j instances) | exact |
| `src/test/resources/application.yaml` (M) | config (test) | n/a | itself: lines 21-43 (mirrored raindrop blocks) | exact |
| `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java` (N) | config (bean factory) | request-response (outbound client build) | `config/RestClientConfig.java` + `integration/RaindropApiClientImpl.java` constructor | role-match |
| `src/main/java/org/bartram/myfeeder/integration/JevApiClient.java` (N) | service interface | request-response | `integration/RaindropApiClient.java` | exact |
| `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java` (N) | API-client bean | request-response (outbound HTTP) | `integration/RaindropApiClientImpl.java` | exact (fallback deliberately differs, D-08) |
| `src/main/java/org/bartram/myfeeder/integration/JevJudgment.java` (N) | model (record) | transform | `integration/RaindropCollection.java`, `service/FetchResult.java` (static factory) | role-match |
| `src/main/java/org/bartram/myfeeder/integration/JevNotConfiguredException.java` (N) | exception | n/a | `integration/RaindropNotConfiguredException.java` | exact |
| `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java` (N) | test (context runner) | n/a | `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` | exact |
| `src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java` (N) | test (wire contract) | request-response | `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java` (+ `service/FeedFetcherTest.java` for error statuses) | exact |
| `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` (N) | test (AOP + stub server) | request-response | `HttpClientConfigurationTest.java` (runner shape only) | partial: no tracked JDK `HttpServer` stub exists |
| `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java` (N) | test (gated live) | request-response | none | no analog |
| `deploy.sh` (M) | deploy script | n/a | itself: lines 12-16, 24 (Raindrop) | exact |
| `helm/myfeeder/values.yaml` (M) | helm config | n/a | itself: `secrets:` block (`raindropApiToken: ""`) | exact |
| `helm/myfeeder/templates/app-secret.yaml` (M) | helm template | n/a | itself: line 12 | exact |
| `helm/myfeeder/templates/app-deployment.yaml` (M) | helm template | n/a | itself: lines 48-52 (env), lines 13-17 (pod template metadata) | exact for env; standard Helm idiom for the annotation |
| `CLAUDE.md` (M, optional docs) | docs | n/a | itself: Package Structure `integration/` line + Resilience4j convention bullet | exact |

## Pattern Assignments

### `build.gradle.kts` (config)

**Analog:** itself.

**Version-property pattern** (lines 28-29). Add a sibling line:
```kotlin
extra["springAiVersion"] = "2.0.1"
extra["springCloudVersion"] = "2025.1.3"
// add:
extra["typesafeVersion"] = "0.1.0"
```

**Dependency placement** (lines 36-42). Precedent for a one-line "why" comment on an otherwise surprising dep is line 37-38:
```kotlin
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	// Pins the auto-configured RestClient transport to Reactor Netty (Spring AI 2.0.0-M2 used to bring it in transitively)
	implementation("io.projectreactor.netty:reactor-netty-http")
	...
	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
```
Add next to the resilience4j line (line 42), with a comment in the same style. Note that the file uses **tab** indentation:
```kotlin
	// Activates the @CircuitBreaker/@Retry aspects (resilience4j registers them only when AspectJ is present)
	implementation("org.springframework.boot:spring-boot-starter-aspectj")
	// Not in any BOM: explicit version. Do NOT add typesafe-spring-ai.
	implementation("org.springaicommunity:spring-ai-starter-typesafe:${property("typesafeVersion")}")
```
The version interpolation matches the BOM lines at 72-73: `"${property("springAiVersion")}"`.

---

### `src/main/resources/application.yaml` (config)

**Analog:** itself, lines 19-42.

**Secret-from-env pattern** (lines 19-21):
```yaml
  raindrop:
    api-base-url: https://api.raindrop.io/rest/v1
    api-token: ${MYFEEDER_RAINDROP_API_TOKEN:}
```
For Jev: `spring.ai.typesafe.api-key: ${MYFEEDER_TYPESAFE_API_KEY:}`. It goes under the **existing top-level `spring:` key** (lines 1-9), not in a second `spring:` document. The existing file has comment precedent at lines 4-5 for explaining a block.

**Resilience instance pattern** (lines 23-42). Add `jev:` as a sibling of `raindrop:` under both `circuitbreaker.instances` and `retry.instances`:
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
Use the exact `jev` values from RESEARCH.md Code Examples §3 (D-09 breaker values, the 5 ignore-exception FQNs, the 3 retry-exception FQNs). **Do not** copy `exponential-backoff-multiplier`; it has no effect without `enable-exponential-backoff: true`. The backoff comes from the `RetryConfigCustomizer` bean instead. Don't touch the Raindrop block either (surgical).

---

### `src/test/resources/application.yaml` (config, test)

**Analog:** itself, lines 21-43. It already duplicates the main `raindrop` resilience blocks verbatim and **omits** `api-token` (line 21-22 has only `api-base-url`).

Copy that approach for Jev:
- Add `spring.ai.typesafe` under the existing `spring:` (lines 1-11): `model: jev-1.13.0`, `timeout: 5s`, `retry.max-retries: 0`, and a pinned `base-url` (non-routable/local). **No `api-key`** and no `${MYFEEDER_TYPESAFE_API_KEY...}` reference.
- Copy the `resilience4j.*.instances.jev` blocks from main verbatim, as sibling `jev:` keys.

The test YAML shadows the main YAML on the classpath, which is why Raindrop is duplicated here. That is also why `HttpClientConfigurationTest` loads main from disk (see below).

---

### `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java` (config, outbound client build)

**Analog A:** `src/main/java/org/bartram/myfeeder/config/RestClientConfig.java`. This is the only `@Configuration` bean factory in `config/`.

**Imports + class shape** (lines 1-18, whole file):
```java
package org.bartram.myfeeder.config;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer userAgentCustomizer(BuildProperties buildProperties) {
        String version = buildProperties != null ? buildProperties.getVersion() : "dev";
        String userAgent = "myfeeder/" + version + " (+https://github.com/bartram/myfeeder)";
        return builder -> builder.defaultHeader(HttpHeaders.USER_AGENT, userAgent);
    }
}
```
This customizer is auto-applied to the injected `RestClient.Builder`. `TypeSafeConfig` must take that injected builder and call `builder.clone()` so the User-Agent carries over (D-05). Never use `RestClient.builder()` in main code.

**Analog B (injected-builder usage):** `integration/RaindropApiClientImpl.java` lines 19-24:
```java
    public RaindropApiClientImpl(MyfeederProperties properties, RestClient.Builder builder) {
        this.apiToken = properties.getRaindrop().getApiToken();
        this.restClient = builder
                .baseUrl(properties.getRaindrop().getApiBaseUrl())
                .build();
    }
```
Note that Raindrop mutates the shared prototype builder without cloning. Jev must `clone()` before `.requestFactory(...)`, because otherwise it would replace the transport on a builder instance other beans might share.

**Divergences from the analogs (from RESEARCH.md Code Examples §1, probe-verified):**
- Add `@Slf4j` (Lombok, same as `RaindropApiClientImpl` line 12) for the D-10 keyless INFO line: `log.info("TypeSafe Jev not configured; interest scoring disabled");`. Log fixed text only, never the key, its length or its prefix.
- `@Configuration(proxyBeanMethods = false)` + `@EnableConfigurationProperties(TypeSafeProperties.class)`. The latter is required because `@ConfigurationPropertiesScan` only covers `org.bartram.myfeeder`, and the starter's auto-config is skipped when `api-key` is absent.
- Build the transport with `ClientHttpRequestFactoryBuilder.reactor().build(HttpClientSettings.defaults().withTimeouts(5s, 5s))`. This is the same `HttpClientSettings` type that `HttpClientConfigurationTest` imports (`org.springframework.boot.http.client.HttpClientSettings`).
- Use the supplier `.apiKey(() -> Objects.requireNonNullElse(props.getApiKey(), ""))`, never `.apiKey(String)`.
- A second `@Bean RetryConfigCustomizer jevRetryInterval(Environment env)` using `Binder.get(env).bind(..., Duration.class)`. Don't use `@Value Duration`, which fails in `ApplicationContextRunner`.

---

### `src/main/java/org/bartram/myfeeder/integration/JevApiClient.java` (interface)

**Analog:** `src/main/java/org/bartram/myfeeder/integration/RaindropApiClient.java` (whole file, lines 1-20):
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
    ...
}
```
Copy the Javadoc style with an `@throws JevNotConfiguredException when the API key is not set` line. Also document the typed SDK exceptions that propagate (D-08) and that timeouts arrive as the base `TypeSafeApiConnectionException`. The single method is `JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions)` (D-01, D-03).

---

### `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java` (API-client bean, outbound HTTP)

**Analog:** `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`

**Imports pattern** (lines 1-10):
```java
package org.bartram.myfeeder.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
```
For Jev, replace `MyfeederProperties`/`RestClient` with `org.springaicommunity.typesafe.TypeSafeClient` and `org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties`. Add the SDK response and exception imports.

**Class declaration + guard** (lines 12-30):
```java
@Slf4j
@Component
public class RaindropApiClientImpl implements RaindropApiClient {

    private final String apiToken;
    ...
    private void requireConfigured() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RaindropNotConfiguredException();
        }
    }
```
For Jev, `requireConfigured()` uses `StringUtils.hasText(properties.getApiKey())` (locked decision) and throws `JevNotConfiguredException`. Keep the class **non-final**, because CGLIB proxies it. Use constructor injection (no Lombok `@RequiredArgsConstructor` in the analog; an explicit constructor is the local style).

**Annotation pattern** (lines 32-36):
```java
    @CircuitBreaker(name = "raindrop", fallbackMethod = "listCollectionsFallback")
    @Retry(name = "raindrop")
    @Override
    public List<RaindropCollection> listCollections() {
        requireConfigured();
```
For Jev, use `@CircuitBreaker(name = "jev")` + `@Retry(name = "jev")` in the same order with **no `fallbackMethod`**. RESEARCH shows typed exceptions and `CallNotPermittedException` propagate unchanged, which satisfies D-08.

**Anti-pattern: do NOT copy the fallback** (lines 68-84):
```java
    private List<RaindropCollection> listCollectionsFallback(Throwable throwable) {
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }
```
Wrapping in `IllegalStateException` is forbidden for Jev by D-08.

**Response mapping pattern** (lines 44-49): map the transport DTO to an app-owned record at the client boundary, the way Raindrop maps `CollectionItem` to `RaindropCollection`. For Jev, map `SystemOneResponse` to `JevJudgment` (via `JevJudgment.from(response)` or a private mapper), with `model = response.model()`.

**Core body:** follow RESEARCH.md Code Examples §2 exactly:
1. Guard.
2. `new LinkedHashMap<>(state)` then `values().removeIf(Objects::isNull)`.
3. `client.systemOne(...)`.
4. Throw a missing-answer check as `TypeSafeMissingAnswerException(name, keys)`.
5. Catch `TypeSafeAuthenticationException | TypeSafePermissionDeniedException` and `log.warn` with status and requestId only (never `e.getMessage()`), then rethrow.

**Log style precedent:** `service/FeedPollingService.java:66`: `log.warn("Failed to poll feed '{}': {}", feed.getTitle(), e.toString());`. Use parameterized SLF4J placeholders, no string concatenation.

---

### `src/main/java/org/bartram/myfeeder/integration/JevJudgment.java` (model record)

**Analog 1:** `src/main/java/org/bartram/myfeeder/integration/RaindropCollection.java` (line 3). This is the app-owned result record in the same package:
```java
public record RaindropCollection(Long id, String title) {}
```
**Analog 2 (Javadoc + static factory on a record):** `src/main/java/org/bartram/myfeeder/service/FetchResult.java` lines 3-13:
```java
/**
 * Outcome of one HTTP feed fetch. On 304, notModified is true and all other fields are null.
 * ...
 */
public record FetchResult(byte[] body, String contentType, String etag, String lastModified,
                          boolean notModified) {
    public static FetchResult notModified304() {
        return new FetchResult(null, null, null, null, true);
    }
}
```
Apply: a top-level `record JevJudgment(String model, String requestId, Map<String, Double> nouls, Map<String, JevScore> scores, Integer inputTokens, Integer outputTokens)` (the exact field set is the planner's call; D-02 requires model, requestId, nouls by name, scores with value/maxLevel/confidence, and token usage). Add a nested `record JevScore(double value, int maxLevel, double confidence)` and a `static JevJudgment from(SystemOneResponse r)` factory. The class-level Javadoc should state that `model` is the **response** model id (SC2/JEV-03). No Lombok is needed on records, matching both analogs.

---

### `src/main/java/org/bartram/myfeeder/integration/JevNotConfiguredException.java` (exception)

**Analog:** `src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java` (whole file, lines 1-7):
```java
package org.bartram.myfeeder.integration;

public class RaindropNotConfiguredException extends RuntimeException {
    public RaindropNotConfiguredException() {
        super("Raindrop integration is not configured. Set the MYFEEDER_RAINDROP_API_TOKEN environment variable.");
    }
}
```
Copy it verbatim with the Jev name and a message naming `MYFEEDER_TYPESAFE_API_KEY`. The FQN `org.bartram.myfeeder.integration.JevNotConfiguredException` must match the YAML `ignore-exceptions` entries in both YAML files. No `GlobalExceptionHandler` mapping is needed this phase (there is no controller).

---

### `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java` (context-runner test)

**Analog:** `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` (whole file, lines 1-51).

**Imports + runner field** (lines 3-29):
```java
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
...
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class));
```
Extend the auto-config list with `TypeSafeAutoConfiguration.class` and `RestClientAutoConfiguration.class` (`org.springframework.boot.restclient.autoconfigure`). Add `.withConfiguration(UserConfigurations.of(TypeSafeConfig.class))` for the app-owned bean. Including `TypeSafeAutoConfiguration` is what makes the blank-key test fail if someone deletes the app-owned bean.

**Main-YAML-from-disk pattern** (lines 37-50):
```java
    @Test
    void outboundTimeoutsFromMainApplicationYamlBind() throws Exception {
        // Loaded from disk: src/test/resources/application.yaml shadows the main one on the classpath.
        PropertySource<?> mainYaml = new YamlPropertySourceLoader()
                .load("main-application-yaml", new FileSystemResource("src/main/resources/application.yaml"))
                .getFirst();

        runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(mainYaml))
                .run(ctx -> { ... });
    }
```
Reuse this for the main-YAML pins (`model: jev-1.13.0`, `retry.max-retries: 0`, the `jev` breaker and retry instances). **One change:** use `addLast(mainYaml)` instead of `addFirst` whenever the test also passes `withPropertyValues(...)`. Otherwise the main YAML's `${MYFEEDER_TYPESAFE_API_KEY:}` blank value wins (RESEARCH Pitfall 4).

**Class Javadoc precedent** (lines 19-24): explain what the test guards and why it runs without Docker. Copy that tone.

**No tracked analog for:** `OutputCaptureExtension` (keyless INFO line + `sk-test-LEAKCHECK` leak check) and `ctx.getStartupFailure()` assertions. Use the standard Boot test API (`org.springframework.boot.test.system.OutputCaptureExtension` / `CapturedOutput`). The untracked probe `KeyContextProbeTest.java` (session scratch, see Metadata) shows the absent/blank/set runner cases.

---

### `src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java` (wire-contract unit test)

**Analog:** `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java`

**Imports** (lines 3-20): static `MockRestRequestMatchers.{content,header,method,requestTo}`, `MockRestResponseCreators.{withSuccess,...}`, AssertJ `assertThat`/`assertThatThrownBy`. For Jev, add `MockRestRequestMatchers.jsonPath` and `MockRestResponseCreators.withStatus`.

**Not-configured test** (lines 33-40):
```java
    @Test
    void listCollectionsThrowsWhenTokenMissing() {
        properties.getRaindrop().setApiToken("");
        var client = new RaindropApiClientImpl(properties, RestClient.builder());

        assertThatThrownBy(client::listCollections)
                .isInstanceOf(RaindropNotConfiguredException.class);
    }
```

**Mock-bound builder + wire expectations** (lines 42-69):
```java
        var builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new RaindropApiClientImpl(properties, builder);

        server.expect(requestTo("https://api.raindrop.io/rest/v1/collections"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        { ... }
                        """, MediaType.APPLICATION_JSON));
        ...
        server.verify();
```
**Body assertion** (lines 81-88): `.andExpect(content().json("""..."""))`. Use it for the D-04 wire checks (nulls dropped, object top-level).

**Adaptation (required):** do NOT bind the mock to a builder that goes through `TypeSafeConfig`, because `TypeSafeConfig` calls `.requestFactory(reactor)` and replaces the mock. Build the SDK client directly, as in RESEARCH Code Examples §4: `TypeSafeClient.builder().apiKey(() -> "test-key").baseUrl("http://jev.test").defaultModel("jev-1.13.0").retryPolicy(RetryPolicy.noRetry()).restClientBuilder(builder).build()`. Then pass it plus a `TypeSafeProperties` into `new JevApiClientImpl(...)`. This is a plain unit test with no Spring context, matching the analog's `@BeforeEach` setup at lines 24-31.

**Error-status analog:** `src/test/java/org/bartram/myfeeder/service/FeedFetcherTest.java` lines 81-83:
```java
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> fetcher.fetch("https://example.com/feed"))
```
For Jev: `withStatus(HttpStatus.valueOf(429)).header("retry-after-ms","700")` etc., then `assertThatThrownBy(...).isInstanceOf(TypeSafeRateLimitException.class)`. Use `HttpStatus.valueOf(422)`, not the deprecated constant (see CLAUDE.md).

Also required: a canned response whose `model` is `"jev-1.13.0-canary"`, to prove `JevJudgment.model` comes from the response and not the default.

---

### `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` (AOP + breaker test)

**Partial analog:** `HttpClientConfigurationTest.java` for the `ApplicationContextRunner` + `AutoConfigurations.of(...)` + `withInitializer(... addLast(yaml))` shape (excerpted above).

**No tracked analog** for the JDK `com.sun.net.httpserver.HttpServer` stub or for `AopUtils.isAopProxy` assertions. Use RESEARCH.md Code Examples §5 for the runner's auto-config list (`AopAutoConfiguration`, resilience4j `springboot3` `CircuitBreakerAutoConfiguration`/`RetryAutoConfiguration`, `TypeSafeAutoConfiguration`, `RestClientAutoConfiguration`, `HttpClientAutoConfiguration`, `ImperativeHttpClientAutoConfiguration`). Set `resilience4j.retry.instances.jev.wait-duration=10ms`. The first assertion must be `assertThat(AopUtils.isAopProxy(ctx.getBean(JevApiClient.class))).isTrue()`.

The stub shape was executed in the untracked probe (`.../scratchpad/probe/src/test/java/probe/Stub.java`, 45 lines). Inline an equivalent private static nested class in the test rather than depending on scratch. The essentials:
- `HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)` with a cached-thread-pool executor.
- A `ConcurrentLinkedQueue<Resp(status, headers, body, delayMs)>` of queued responses, defaulting to 200 with a canned OK body.
- An `AtomicInteger hits`.
- Adds the `x-typesafe-request-id` header.
- `url()` returns `"http://127.0.0.1:" + port`.
- Stop it in `@AfterEach`.

---

### `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java` (gated live test)

**No analog.** There is no `@EnabledIfEnvironmentVariable` usage in the repo. Gate on a dedicated opt-in variable, `@EnabledIfEnvironmentVariable(named = "JEV_LIVE_SMOKE", matches = "true")`, not on the key variable (RESEARCH Pitfall 4). Build the client the same way as the wire test, but with the real base URL and `System.getenv("MYFEEDER_TYPESAFE_API_KEY")`. Assert `judgment.model()` equals `"jev-1.13.0"`. The planner adds a `checkpoint:human-verify` for running it.

---

### `deploy.sh` (deploy script)

**Analog:** itself, lines 12-16 and 24:
```bash
# Raindrop is optional; default to empty so set -u doesn't trip.
RAINDROP_TOKEN="${MYFEEDER_RAINDROP_API_TOKEN:-}"
if [[ -z "$RAINDROP_TOKEN" ]]; then
  echo "Warning: MYFEEDER_RAINDROP_API_TOKEN is unset; Raindrop integration will be disabled in this deployment."
fi
...
  --set secrets.raindropApiToken="$RAINDROP_TOKEN" \
```
Copy this for `TYPESAFE_KEY="${MYFEEDER_TYPESAFE_API_KEY:-}"` with the warning "…interest scoring will be disabled…". Add `--set secrets.typesafeApiKey="$TYPESAFE_KEY" \` directly after line 24, before `--history-max 3`. `--set-string` is optional, and it's the planner's call (RESEARCH Pitfall 6 / A1).

---

### `helm/myfeeder/values.yaml` (helm config)

**Analog:** itself, `secrets:` block (lines 47-51 of the file):
```yaml
secrets:
  postgresPassword: ""
  anthropicApiKey: ""
  raindropApiToken: ""
  googleApplicationCredentials: ""
```
Add `typesafeApiKey: ""` after `raindropApiToken`.

---

### `helm/myfeeder/templates/app-secret.yaml` (helm template)

**Analog:** itself, line 12:
```yaml
  myfeeder-raindrop-api-token: {{ .Values.secrets.raindropApiToken | quote }}
```
Add `  myfeeder-typesafe-api-key: {{ .Values.secrets.typesafeApiKey | quote }}` as line 13, **before** the `{{- if .Values.secrets.googleApplicationCredentials }}` / `data:` block (lines 13-16). Otherwise it lands under `data:` instead of `stringData:`.

---

### `helm/myfeeder/templates/app-deployment.yaml` (helm template)

**Env analog:** itself, lines 48-52:
```yaml
            - name: MYFEEDER_RAINDROP_API_TOKEN
              valueFrom:
                secretKeyRef:
                  name: {{ include "myfeeder.fullname" . }}-secret
                  key: myfeeder-raindrop-api-token
```
Add the `MYFEEDER_TYPESAFE_API_KEY` / `myfeeder-typesafe-api-key` block right after it, before `JDK_JAVA_OPTIONS` at line 53.

**Pod-template metadata** (lines 13-17). There are no annotations today:
```yaml
  template:
    metadata:
      labels:
        {{- include "myfeeder.labels" . | nindent 8 }}
        {{- include "myfeeder.appSelectorLabels" . | nindent 8 }}
```
Insert an `annotations:` sibling of `labels:` under `template.metadata`, using the standard Helm idiom (verified in RESEARCH Pattern 5):
```yaml
      annotations:
        checksum/secret: {{ include (print $.Template.BasePath "/app-secret.yaml") . | sha256sum }}
```

---

### `CLAUDE.md` (docs, optional)

**Analog:** itself. The Package Structure `integration/` line lists the Raindrop classes. Append `JevApiClient/JevApiClientImpl, JevJudgment, JevNotConfiguredException` there and `TypeSafeConfig` to the `config/` line. RESEARCH Open Question 1 also recommends correcting the Resilience4j convention bullet to note that the annotations need `spring-boot-starter-aspectj`. The planner decides whether that goes in this phase or a follow-up.

## Shared Patterns

### Optional-secret gating (not-configured)
**Source:** `integration/RaindropApiClientImpl.java` lines 26-30, `integration/RaindropNotConfiguredException.java`, and both YAML `ignore-exceptions` lists.
**Apply to:** `JevApiClientImpl`, `JevNotConfiguredException`, both `application.yaml` files.
Throw a dedicated `RuntimeException` before any HTTP call. List its FQN under **both** `circuitbreaker.instances.<name>.ignore-exceptions` and `retry.instances.<name>.ignore-exceptions`.

### Resilience annotations on the API-client bean
**Source:** `integration/RaindropApiClientImpl.java` lines 32-36 / 52-56.
**Apply to:** `JevApiClientImpl.judge`.
`@CircuitBreaker(name=…)` above `@Retry(name=…)` above `@Override`, on a separate `@Component` so the AOP proxy is used. For Jev there is no `fallbackMethod` (D-08). This requires `spring-boot-starter-aspectj`. Without it the annotations do nothing, so the tests must assert `AopUtils.isAopProxy`.

### Outbound RestClient via the auto-configured builder
**Source:** `config/RestClientConfig.java` lines 12-17 and `integration/RaindropApiClientImpl.java` lines 19-24.
**Apply to:** `TypeSafeConfig`.
Inject `RestClient.Builder` so the User-Agent customizer applies, then `clone()` before setting the Jev-specific Reactor factory.

### Test YAML mirrors main
**Source:** `src/test/resources/application.yaml` lines 24-43, which mirror main lines 23-42.
**Apply to:** every new `resilience4j.*.instances.jev` block and the `spring.ai.typesafe` pins, minus any key env reference.

### Main-YAML-from-disk assertions
**Source:** `config/HttpClientConfigurationTest.java` lines 37-50.
**Apply to:** `TypeSafeConfigTest` (main pins) and optionally `JevResilienceTest`. Use `addLast` when combined with `withPropertyValues`.

### Logging
**Source:** Lombok `@Slf4j` (`RaindropApiClientImpl.java:12`) and parameterized messages (`FeedPollingService.java:66`, `FeedPollingScheduler.java:101`).
**Apply to:** `TypeSafeConfig` (INFO, keyless) and `JevApiClientImpl` (WARN on 401/403 with status and requestId only). Never log the key or `e.getMessage()` for auth failures.

### Optional-secret Helm/deploy wiring
**Source:** `deploy.sh:12-16,24`, `values.yaml` `secrets.raindropApiToken`, `app-secret.yaml:12`, `app-deployment.yaml:48-52`.
**Apply to:** the TypeSafe key, following the same four touchpoints in the same order.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java` | test (gated live) | request-response | No env-gated tests exist in the repo; use RESEARCH Pitfall 4 guidance (`JEV_LIVE_SMOKE=true` gate) |
| `JevResilienceTest.java` (stub-server portion) | test | request-response | No tracked JDK `HttpServer` stub or AOP-proxy test exists; use RESEARCH Code Examples §5 plus an inline stub modelled on the untracked probe `Stub.java` |
| `app-deployment.yaml` `checksum/secret` annotation | helm template | n/a | No pod-template annotations exist; use the standard Helm `include … | sha256sum` idiom (RESEARCH Pattern 5, rendered and verified) |

## Metadata

**Analog search scope:** `src/main/java/org/bartram/myfeeder/{config,integration,service}`, `src/test/java/org/bartram/myfeeder/{config,integration,service}`, `src/main/resources`, `src/test/resources`, `helm/myfeeder`, `deploy.sh`, `build.gradle.kts`
**Files scanned:** 24
**Untracked supplementary reference (not an analog; session scratch, may be deleted):** `/private/tmp/claude-501/-Users-scottb-orca-workspaces-myfeeder-main/4e33f96c-750e-446d-a36c-92962690ba53/scratchpad/probe/` (`Stub.java`, `KeyContextProbeTest.java`, `RunnerAopProbeTest.java`, `TypeSafeConfig.java`, `JevClient.java`). Copy ideas inline; never reference these paths from plans as files to edit.
**Pattern extraction date:** 2026-09-22
