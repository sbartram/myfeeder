# Stack Research: TypeSafe Jev interest ranking

**Domain:** Adding LLM-free "judgment model" scoring (TypeSafe Jev) to an existing Spring Boot 4 feed reader
**Researched:** 2026-09-22
**Confidence:** HIGH for library compatibility and behavior. The published source jars were read and a scratch Gradle project using this repo's exact plugin/BOM setup was compiled and tested against a local stub server. MEDIUM for pricing and rate limits, which come from the vendor docs and change often.

> Scope: this covers only what is new for the Jev milestone. The existing stack is in `.planning/codebase/STACK.md` and is not repeated here.

## Verdict (read this first)

1. **Compatible: YES.** `org.springaicommunity:spring-ai-starter-typesafe:0.1.0` works on Spring Boot 4.0.3, Java 25, and the project's Spring AI 2.0.0-M2 BOM. **Verified by test:** context startup, request serialization, and parsing of all answer types (Noul, Choice, Score, plus an unknown future type) against a stub HTTP server, on Boot 4.0.3.
2. **The starter does not use Spring AI at all.** Its only dependencies are `typesafe-java-sdk` and `spring-boot-starter`. The Spring AI version in `build.gradle.kts` does not matter for it. The separate module `typesafe-spring-ai` (advisors, RAG, JevJudge) does need Spring AI **2.0.1**. That module is out of scope for this milestone, so do not add it.
3. **Version downgrades happen, and they work.** The starter was built against Boot 4.0.7, Spring Framework 7.0.8, and Jackson 3.1.4. The `io.spring.dependency-management` plugin pins them to what Boot 4.0.3 manages: Spring **7.0.5** and Jackson **3.0.4**. The SDK's Jackson code paths ran correctly on 3.0.4 (verified).
4. **CRITICAL: a blank API key crashes startup.** The auto-configuration uses `@ConditionalOnProperty(spring.ai.typesafe.api-key)`, then runs `Assert.state(hasText(apiKey))`. Results:
   - Property **absent**: no `TypeSafeClient` bean, and the app starts (verified).
   - Property **present but blank**: `IllegalStateException: No API key configured`, and **the application context fails** (verified). This covers `${MYFEEDER_TYPESAFE_API_KEY:}` with the variable unset, and a Helm `secretKeyRef` to an empty secret value. The source comment says it "declines", but in fact it throws.
   - Property `=false`: no bean, and the app starts (verified). This is an accidental escape hatch; do not rely on it.

   **Recommendation: keep the starter, but have myfeeder own the `TypeSafeClient` bean** (Pattern A below). The auto-config's bean method is `@ConditionalOnMissingBean`, so it steps aside and its `Assert` never runs. Verified: absent, blank, and real keys all start cleanly. This lets the Raindrop convention (`${MYFEEDER_..._TOKEN:}` + `requireConfigured()` → `*NotConfiguredException`) carry over unchanged.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| `org.springaicommunity:spring-ai-starter-typesafe` | **0.1.0** (only release, published to Maven Central 2026-09-20) | Brings in the Jev SDK, `TypeSafeProperties` (`spring.ai.typesafe.*` binding plus IDE metadata), and the auto-config | This is the milestone's chosen integration. Its property binding gives timeout, model, and retry knobs without custom `@ConfigurationProperties`. It adds no Spring AI dependency, so it has no conflict with 2.0.0-M2. | HIGH (POM and source read; tested) |
| `org.springaicommunity:typesafe-java-sdk` | 0.1.0 (transitive) | `TypeSafeClient`, question types, answer records, typed exceptions | Built on Spring `RestClient` and Jackson 3, which the project already uses. A `RestClient.Builder` from the application context picks up the existing User-Agent `RestClientCustomizer` and Micrometer observation. | HIGH |
| Jev model | Pin **`jev-1.13.0`** (`spring.ai.typesafe.model`) | The judgment model | Raw scores are stored once per article and blended at query time. If the model is left on `jev-latest`, that alias can move and change score distributions midstream, so old and new articles would stop being comparable. Store `response.model()` with each score. | MEDIUM (aliases currently both point to 1.13.0 per docs.typesafe.ai/models) |

### Supporting Libraries

No new libraries. Everything else is already on the classpath:

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Resilience4j (via `spring-cloud-starter-circuitbreaker-resilience4j`) | BOM 2025.1.0 (existing) | `@CircuitBreaker` (outer) + `@Retry` (inner) on `JevApiClientImpl` | Every Jev call. Turn **off** the SDK's own retry so attempts don't multiply (see config). |
| Spring `@Async` / `@TransactionalEventListener` / `@Scheduled` | Boot 4.0.3 (existing) | Scoring after ingest without blocking the poll; a backfill job | Scoring must happen after commit and off the polling thread. Needs `@EnableAsync` (the project currently has only `@EnableScheduling`). |
| Flyway | existing | V6 migration for profile, topics, and per-article Jev results | Schema work only. |
| `MockRestServiceServer` (spring-test) / JDK `HttpServer` | existing | Wire-level tests of the Jev client | `TypeSafeClient.builder().restClientBuilder(...)` is the SDK's documented test hook. |

Frontend: no new npm dependencies. The score badge and Priority view use existing React, TanStack Query, and Zustand.

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `TYPESAFE_API_KEY` / `MYFEEDER_TYPESAFE_API_KEY` in `.envrc` | Local real-API testing | The starter reads **only** `spring.ai.typesafe.api-key`. It does **not** read `TYPESAFE_API_KEY` by itself, despite what the blog implies. Only `TypeSafeClient.builder()` with no `apiKey(..)` falls back to that environment variable. Map it explicitly in `application.yaml`. |

## Installation (Gradle Kotlin DSL)

```kotlin
// build.gradle.kts: next to the existing extra[...] entries
extra["typesafeVersion"] = "0.1.0"

dependencies {
    // Not in the Spring AI BOM; needs an explicit version.
    // Pulls in typesafe-java-sdk only (no Spring AI artifacts).
    implementation("org.springaicommunity:spring-ai-starter-typesafe:${property("typesafeVersion")}")
    // Do NOT add org.springaicommunity:typesafe-spring-ai. It needs Spring AI 2.0.1, and the
    // project's 2.0.0-M2 BOM would silently downgrade its spring-ai-client-chat dependency.
}
```

A BOM alternative exists (`mavenBom("org.springaicommunity:typesafe-bom:0.1.0")` in `dependencyManagement.imports`). It isn't worth adding for a single artifact.

### Configuration (`application.yaml`)

```yaml
spring:
  ai:
    typesafe:
      # Safe to default to blank ONLY because myfeeder defines its own TypeSafeClient bean
      # (Pattern A). With the starter's own bean, a blank value fails startup.
      api-key: ${MYFEEDER_TYPESAFE_API_KEY:}
      model: jev-1.13.0          # pin; see rationale above
      timeout: 5s                # per-attempt read timeout (SDK default 10s; median call ~275-310ms)
      retry:
        max-retries: 0           # Resilience4j @Retry owns retries (project convention)

resilience4j:
  circuitbreaker:
    instances:
      jev:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 5
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
  retry:
    instances:
      jev:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:        # retry only transient failures; 400/401/403/422 are permanent
          - org.springaicommunity.typesafe.exception.TypeSafeRateLimitException       # 429
          - org.springaicommunity.typesafe.exception.TypeSafeInternalServerException  # 5xx incl. 529 Overloaded
          - org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException   # incl. TypeSafeApiTimeoutException
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
```

Test `src/test/resources/application.yaml`: add nothing for TypeSafe. With Pattern A, a missing key means "not configured", and tests stub `JevApiClient`.

Helm/deploy: add `secrets.typesafeApiKey: ""` → secret key `myfeeder-typesafe-api-key` → container env `MYFEEDER_TYPESAFE_API_KEY`, following Raindrop exactly, including the empty-default warning in `deploy.sh`. Pattern A makes an empty secret value harmless.

## Pattern A (recommended): app-owned `TypeSafeClient` bean

This was verified in a scratch project: the app starts with the key absent, blank, or set.

```java
@Configuration
@EnableConfigurationProperties(TypeSafeProperties.class) // bind even when the auto-config's class condition is off
public class TypeSafeConfig {

    @Bean
    TypeSafeClient typeSafeClient(TypeSafeProperties props, RestClient.Builder builder) {
        // Explicit connect timeout: the starter's `new JdkClientHttpRequestFactory()` sets none,
        // and it replaces the factory, so spring.http.client.connect-timeout does not apply to Jev.
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var rf = new JdkClientHttpRequestFactory(http);
        rf.setReadTimeout(props.getTimeout());
        return TypeSafeClient.builder()
                // Supplier form does not assert hasText, so a blank key is legal at build time.
                // JevApiClientImpl.requireConfigured() throws JevNotConfiguredException before any call.
                .apiKey(() -> Objects.requireNonNullElse(props.getApiKey(), ""))
                .baseUrl(props.getBaseUrl())
                .defaultModel(props.getModel())
                .timeout(props.getTimeout())
                .retryPolicy(props.toRetryPolicy())          // max-retries: 0 from yaml
                .restClientBuilder(builder.clone().requestFactory(rf)) // keeps the User-Agent customizer + observations
                .build();
    }
}
```

`JevApiClientImpl` (a separate bean, so the AOP proxy applies) follows `RaindropApiClientImpl`. It checks `StringUtils.hasText(props.getApiKey())` and otherwise throws `JevNotConfiguredException`. It carries `@CircuitBreaker(name="jev", fallbackMethod=...)` + `@Retry(name="jev")`, and its fallback rethrows `JevNotConfiguredException` as-is. Callers in the scoring path catch everything and leave the article unscored; the backfill job retries it later.

## TypeSafeClient API surface (0.1.0, from source)

**Calls**

| Method | Notes |
|--------|-------|
| `SystemOneResponse systemOne(String state, Map<String, ? extends Question> questions)` | Plain-text state |
| `systemOne(Map<String, ?> state, Map<...> questions)` | **Use this.** Named fields (`title`, `summary`, `feed`) let questions refer to them. Use a `LinkedHashMap`: `Map.of` randomizes field order on the wire (observed), and it rejects null values, so drop null fields or use `""`. |
| `systemOne(List<?> state, ...)` / `systemOne(JsonContent state, ...)` / `systemOne(SystemOneRequest)` | `SystemOneRequest.builder().state(..).model(..).question(name, q).build()` |
| `List<JevBatchResult<SystemOneResponse>> systemOneAll(List<SystemOneRequest>[, JevBatchOptions])` | **Client-side** concurrency only; there is no server batch endpoint. The default width is 4. Each slot holds either a value or a `TypeSafeException` (`succeeded()`, `orThrow()`, `orElse()`). `JevBatchOptions.ofConcurrency(n).withExecutor(virtualThreadExecutor)`. |
| `List<ModelMetadata> listModels()` | `GET /v1/models`. Could serve as a "test connection" check for a settings UI. |
| `defaultModel()`, `timeout()`, `retryPolicy()` | Introspection |

**Questions** (`org.springaicommunity.typesafe.question`, sealed `Question`)

- `Noul.of("Is this about X?")` or `Noul.builder().instructions(..).whenTrue(..).whenFalse(..).build()`. Wire format: `criteria: {"true":..,"false":..}`. Use `whenTrue`/`whenFalse` for each topic's description; it sharpens the yes/no boundary.
- `Score.of(instructions, "lowest level", ..., "highest level")` or `Score.builder().instructions(..).level(..)...build()`. Requires at least 2 levels. The answer is **continuous in [0, levels-1]**, so normalize by `maxLevel()` before blending.
- `Choice.of(instructions, "a","b")` or `Choice.builder().option(label, description)`, with at most 255 options (API docs). Not needed for this milestone.
- Instructions and criteria take String, Map, or List (`JsonContent`). The top-level state must be string, object, array, or null. Bare numbers or booleans get a 422.

**Answers** (`SystemOneResponse(model, answers, usage, requestId)`)

| Accessor | Returns |
|----------|---------|
| `noulValue(name)` / `noul(name).value()`, `.isTrue()`, `.isTrue(threshold)` | double in [0,1]. Nouls have **no confidence field**; the value itself expresses certainty. |
| `scoreValue(name)` / `score(name)` → `ScoreAnswer(value, legend, probabilities, confidence)` + `nearestLevel()`, `nearestLabel()`, `maxLevel()` | `confidence` in [0,1] measures how concentrated the per-level distribution is |
| `choiceValue(name)` / `choice(name)` → `ChoiceAnswer(value, probabilities, confidence)` + `probabilityOf()`, `optionsAbove()` | |
| `nouls()`, `scores()`, `choices()`, `answer(name)` | Typed maps. `answer()` returns the sealed `Answer` (`UnknownAnswer` for future types, so the call doesn't fail). |
| `usage().inputTokens()/outputTokens()/totalTokens()`, `model()`, `requestId()` | Log `requestId` (from the `x-typesafe-request-id` header) at WARN on failures |

**Exceptions** (all unchecked, rooted at `TypeSafeException extends RuntimeException`)

```
TypeSafeException
├── TypeSafeApiException (status(), body(), requestId(), errorType(), errorMessage(), validationErrors())
│   ├── TypeSafeBadRequestException          400  unknown model / malformed question   (permanent)
│   ├── TypeSafeAuthenticationException      401  rejected key                         (permanent)
│   ├── TypeSafePermissionDeniedException    403  missing key                          (permanent)
│   ├── TypeSafeNotFoundException            404
│   ├── TypeSafeUnprocessableEntityException 422  bad state/body (e.g. bare number)    (permanent, per-article)
│   ├── TypeSafeRateLimitException           429  retryAfter()/retryAfterMs()          (transient)
│   ├── TypeSafeInternalServerException      5xx                                       (transient)
│   │   └── TypeSafeOverloadedException      529                                       (transient)
│   └── TypeSafeApiResponseValidationException  empty body / no answers / non-JSON
├── TypeSafeApiConnectionException           no HTTP response                          (transient)
│   └── TypeSafeApiTimeoutException
├── TypeSafeMissingAnswerException           response lacks a named answer
└── TypeSafeAnswerTypeException              e.g. score() on a noul answer
```

**Timeouts and retries** (SDK defaults, overridable under `spring.ai.typesafe.*`): `timeout` 10s per attempt. `retry.max-retries` 2, `initial-backoff` 500ms, `max-backoff` 5s, `jitter` 0.25. Retryable statuses are {408, 429} plus every 5xx. `respect-retry-after` is true (honors `retry-after-ms`), `retry-connection-errors` is true, and `total-timeout` is 30s. With `max-retries: 0` the SDK makes a single attempt and Resilience4j handles retries.

## Pricing, limits, batching

| Item | Value | Confidence |
|------|-------|------------|
| Price | $0.042 per 1M **input** tokens; output tokens free; no free tier documented | MEDIUM (docs.typesafe.ai/models, cross-checked with search results) |
| What counts as input | State **and** questions. The interest profile text and every topic description are billed on every call. | HIGH (SDK `Usage` javadoc) |
| Rough cost for myfeeder | ~0.5–2k tokens per article call → 1,000 articles ≈ $0.02–0.08. Backfilling a 5k-article backlog costs well under $1. | MEDIUM (estimate) |
| Rate limits | 1,200 requests/min, 250k tokens/s; 429 when exceeded. The vendor says limits "adjust dynamically" and may change without notice. | MEDIUM |
| Request size | 64k tokens per request; 32k for state plus the longest question. Strip HTML from summaries and truncate (e.g. ~2k chars). | MEDIUM |
| Latency | Median ~275ms (1 question), ~310ms (3 questions); no streaming | MEDIUM (Spring blog) |
| Batching | No server batch endpoint. Pack all questions about **one** article into **one** `systemOne` call; the server answers them in parallel. `systemOneAll` is only client-side fan-out across articles. | HIGH (source) |

For backfill, don't use `systemOneAll`. It runs N calls inside one method invocation, which bypasses the per-call `@CircuitBreaker`/`@Retry` on `JevApiClientImpl`, and it creates its own platform-thread pool. Instead, loop through the annotated client bean sequentially, or with small bounded concurrency (≤4 in flight ≈ ≤800 req/min at 300ms). Stop the batch when the breaker opens.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **A: Starter + app-owned `TypeSafeClient` bean** | **B: Starter's auto-configured bean + `ObjectProvider<TypeSafeClient>`**, with the key supplied only through env var `SPRING_AI_TYPESAFE_API_KEY` | Needs zero Java config, but it has three fragile requirements. (1) `application.yaml` must never declare `spring.ai.typesafe.api-key`. (2) The Helm template must add the env var only `{{- if .Values.secrets.typesafeApiKey }}`, which differs from the Raindrop/Anthropic pattern that always renders a `secretKeyRef`. (3) Anyone who copies the Raindrop `${...:}` idiom crashes startup. Choose B only if you accept those rules and add a context test for them. |
| A | **C: `typesafe-java-sdk` only (no starter)** | Same code as A, minus the `spring.ai.typesafe.*` property binding and IDE metadata. Choose C if you'd rather keep all config under `myfeeder.jev.*` in `MyfeederProperties`. Functionally equivalent. |
| A | **D: Raw `RestClient` call to `POST https://api.typesafe.ai/v1/systemone`** | Only if the SDK turns out to be broken. You would lose the typed questions, the polymorphic answer deserializer, and the exception mapping for about 300 lines of hand-written DTOs. Not justified: the SDK works on this stack (verified). |
| Resilience4j `@Retry` (SDK retries off) | SDK `RetryPolicy` (keeps `retry-after-ms` handling), with `@CircuitBreaker` only | If 429s actually show up during backfill. The SDK's policy honors the server-stated wait precisely, and Resilience4j's fixed backoff does not. Keep exactly one retry layer in either case. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `spring.ai.typesafe.api-key: ${MYFEEDER_TYPESAFE_API_KEY:}` **combined with the starter's own bean** | A blank key fails context startup (`IllegalStateException`, verified). This breaks "runs normally without a key." | Pattern A (app-owned bean) |
| `org.springaicommunity:typesafe-spring-ai` | Out of scope (advisors, RAG, tool index, JevJudge). It is compiled against Spring AI 2.0.1, and the project's 2.0.0-M2 BOM would downgrade it at runtime: an untested combination. | Call `TypeSafeClient` directly. If you ever want `JevJudge`, first upgrade `springAiVersion` to 2.0.1 GA (released) as its own change. |
| `TypeSafeClient.builder().build()` with no `apiKey(..)` | Reads the `TYPESAFE_API_KEY` env var and **asserts** it is set, so it throws at bean creation when unset | `apiKey(Supplier)` as in Pattern A |
| `apiKey(String)` with a possibly blank value | `Assert.hasText` throws | `apiKey(Supplier)` |
| SDK retries **and** Resilience4j `@Retry` both enabled | Attempts multiply (3 × 3 = 9 per article) and blow the latency budget | `spring.ai.typesafe.retry.max-retries: 0` |
| `jev-latest` for stored scores | The alias can move to a new model version, making stored raw scores incomparable across time | Pin `jev-1.13.0`; store `response.model()` |
| `Map.of(...)` for state | Random field order on the wire; rejects null summaries | `LinkedHashMap`, dropping null or blank fields |
| Overriding Boot's managed Jackson/Spring versions to match the SDK's 3.1.4/7.0.8 | Unnecessary (it works on 3.0.4/7.0.5) and risks breaking the rest of Boot 4.0.3 | Let `io.spring.dependency-management` pin them |

## Stack Patterns by Variant

**If the TypeSafe key is not configured** (local dev, tests, a deploy without the secret):
- `JevApiClientImpl` throws `JevNotConfiguredException`; the scoring hook catches it, and the article stays unscored.
- The backfill job should skip entirely when the key isn't configured, so it doesn't spin.

**If the circuit is open or Jev is slow:**
- Ingest never waits. Scoring runs `@Async` after commit, the per-attempt timeout is 5s, and there is at most 1 SDK attempt with ≤3 Resilience4j attempts.
- Unscored articles get picked up by the scheduled backfill.

**If 429s appear during the one-time backlog backfill:**
- Lower the concurrency to 1–2, or switch to the SDK retry layer for the honored `retry-after-ms` (see Alternatives).

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `spring-ai-starter-typesafe:0.1.0` | Spring Boot 4.0.3 (built on 4.0.7) | Verified: auto-config loads; the `spring.ai.typesafe.*` binding works |
| `typesafe-java-sdk:0.1.0` | Spring Framework 7.0.5 (built on 7.0.8), Jackson databind 3.0.4 (built on 3.1.4), jackson-annotations 2.x | Verified: request serialization, polymorphic answer deserialization, and 429 → `TypeSafeRateLimitException` with `retryAfter()`. Targets Java 17+, so it runs on 25. |
| `typesafe-java-sdk:0.1.0` | Spring AI 2.0.0-M2 | No interaction; the SDK has no Spring AI dependency |
| `typesafe-spring-ai:0.1.0` | Spring AI **≥ 2.0.1** | Not compatible in practice with the project's 2.0.0-M2 BOM. Do not add it. |
| Starter auto-config | App's `RestClientCustomizer` (User-Agent) | Applies: the starter and Pattern A both clone the context `RestClient.Builder`. The starter replaces the request factory, so `spring.http.client.*` timeouts do **not** apply to Jev; configure `spring.ai.typesafe.timeout` instead. |

## Sources

- Maven Central POMs, metadata, and `-sources.jar` for `spring-ai-starter-typesafe`, `typesafe-java-sdk`, `typesafe-spring-ai`, and `typesafe-bom` 0.1.0 (`repo1.maven.org/maven2/org/springaicommunity/`): dependency versions, `TypeSafeAutoConfiguration`, `TypeSafeProperties`, `TypeSafeClient`, `RetryPolicy`, `JevBatchOptions`, questions, answers, exceptions. **HIGH**
- Scratch Gradle project (Boot 4.0.3 plugin, dependency-management 1.1.7, Spring AI BOM 2.0.0-M2, starter 0.1.0, JDK 25). `ApplicationContextRunner` tests covered absent, blank, `false`, and real keys, plus the app-owned bean variant; a JDK `HttpServer` stub covered the wire round-trip and 429 mapping; `gradle dependencies` confirmed the resolved versions. **HIGH (executed)**
- Spring blog, "Spring AI TypeSafe: structured judgment" (2026-09-21): https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment. **MEDIUM**
- Reference docs: https://spring-ai-community.github.io/spring-ai-typesafe/latest/ (the starter needs Boot 4.x; `typesafe-spring-ai` needs Spring AI 2.0.1). **MEDIUM**
- TypeSafe models, pricing, and limits: https://docs.typesafe.ai/models. API errors: https://docs.typesafe.ai/api. **MEDIUM** (vendor says limits change dynamically)
- Search cross-check for pricing and limits: https://www.marktechpost.com/2026/09/19/typesafe-ai-releases-jev/, https://opentweet.io/jev/limits. **LOW alone; MEDIUM when combined with the vendor docs**

---
*Stack research for: TypeSafe Jev interest ranking in myfeeder*
*Researched: 2026-09-22*
