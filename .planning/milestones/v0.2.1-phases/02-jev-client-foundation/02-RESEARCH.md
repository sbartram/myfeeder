# Phase 2: Jev Client Foundation - Research

**Researched:** 2026-09-22
**Domain:** Optional third-party HTTP client (TypeSafe Jev SDK 0.1.0) + Resilience4j annotations + Helm/deploy secret on Spring Boot 4.0.8
**Confidence:** HIGH (every load-bearing claim was either read from the published source jars this session or executed in a scratch Gradle probe on this repo's exact Boot 4.0.8 / Spring Cloud 2025.1.3 / Reactor Netty stack)

## Summary

The locked design (Pattern A app-owned `TypeSafeClient`, Resilience4j as the only retry layer, Reactor Netty transport, typed-exception propagation, `checksum/secret`) works on Boot 4.0.8 as specified. It was executed end to end in a scratch probe. Boot 4.0.8 now manages Spring Framework **7.0.9** and Jackson **3.1.5**, which are at or above what the starter was built against (7.0.8 / 3.1.4). The "version skew" pitfall from the milestone research (written against Boot 4.0.3) no longer applies. The one-time `./gradlew dependencies` check is still worth keeping as a guard.

**One finding changes the plan: `@CircuitBreaker` and `@Retry` do nothing in this app today.** AspectJ is not on the runtime or test classpath. Resilience4j registers its aspects only when `org.aspectj.lang.ProceedingJoinPoint` is present. Without AspectJ, the annotated bean is not proxied, a 500 is attempted exactly once, and the breaker records nothing. Probe output with and without AspectJ is quoted below. This has been true of `RaindropApiClientImpl` since it was written: no commit ever added `spring-boot-starter-aop`/`-aspectj`. JEV-02 / SC3 **cannot be met** unless the phase adds `org.springframework.boot:spring-boot-starter-aspectj` (BOM-managed, no version). Adding it also turns on Raindrop's latent retry and breaker config, so the full backend suite must run afterwards.

Several details were verified by execution. Reactor Netty read and connect timeouts surface as the base `TypeSafeApiConnectionException`, **not** its `TypeSafeApiTimeoutException` subclass, so Phase 4 must classify on the base type. Resilience4j's default aspect order puts Retry *outside* CircuitBreaker, so every retry attempt counts toward the breaker. The 429 `retry-after-ms` interval function (D-07) works when registered as a `RetryConfigCustomizer` bean, and it overrides the YAML `wait-duration`. With no `fallbackMethod`, the SDK's typed exceptions and `CallNotPermittedException` propagate unchanged, which satisfies D-08 with less code.

**Primary recommendation:** Add `spring-ai-starter-typesafe:0.1.0` **and** `spring-boot-starter-aspectj`. Build the `TypeSafeClient` in `TypeSafeConfig` from a cloned `RestClient.Builder` with `ClientHttpRequestFactoryBuilder.reactor().build(HttpClientSettings.defaults().withTimeouts(5s, 5s))`. Put `@CircuitBreaker(name="jev")` + `@Retry(name="jev")` with **no fallbackMethod** on `JevApiClientImpl`. Register the Retry-After interval as a `RetryConfigCustomizer`. Prove all of it with `ApplicationContextRunner` tests (no Docker needed) that assert `AopUtils.isAopProxy(...)`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (locked by research and Phase 1, not re-discussed)
- **Pattern A:** keep the starter, but myfeeder defines its own `TypeSafeClient` bean. The starter's `@ConditionalOnMissingBean` then steps aside, and its blank-key `Assert` never runs. `spring.ai.typesafe.api-key: ${MYFEEDER_TYPESAFE_API_KEY:}`. Pass the key with the supplier form `.apiKey(() -> …)` so a blank key is legal at build time.
- **Single retry layer:** SDK `spring.ai.typesafe.retry.max-retries: 0`. Resilience4j `@Retry(name="jev")` owns retries and retries only transient failures: `TypeSafeRateLimitException` (429), `TypeSafeInternalServerException` (5xx incl. 529), and `TypeSafeApiConnectionException` (incl. timeout).
- **Breaker `ignore-exceptions`:** `JevNotConfiguredException`, `TypeSafeBadRequestException` (400), `TypeSafeUnprocessableEntityException` (422), `TypeSafeMissingAnswerException`, `TypeSafeAnswerTypeException`. Also list `JevNotConfiguredException` under retry `ignore-exceptions`.
- **Model:** pin `spring.ai.typesafe.model: jev-1.13.0`.
- **Raindrop pattern:** annotations go on the API-client bean. `requireConfigured()` checks `StringUtils.hasText(apiKey)` and throws `JevNotConfiguredException` before any HTTP call.
- **Test YAML:** mirror the `resilience4j.*.instances.jev` blocks into `src/test/resources/application.yaml`, which shadows main. The test YAML must not reference any real key env var. Pin `baseUrl` in tests.
- **Helm and deploy:** add `secrets.typesafeApiKey: ""`, secret key `myfeeder-typesafe-api-key`, and env `MYFEEDER_TYPESAFE_API_KEY`, mirroring Raindrop. `deploy.sh` uses `${MYFEEDER_TYPESAFE_API_KEY:-}` and prints a warning when it is empty. Add a `checksum/secret` pod-template annotation so a key-only change rolls the pod.
- **Out of bounds:** do not add `org.springaicommunity:typesafe-spring-ai`, do not use `systemOneAll`, do not add `@EnableAsync`, and do not set `spring.threads.virtual.enabled`.

#### Client API shape
- **D-01:** `JevApiClient` is a **thin, generic pass-through**: roughly `JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions)`. It knows nothing about articles, profiles or topics. Phase 4's scorer builds the article state and question map, and Phase 3's topic preview reuses the same method. — **Reversibility:** reversible
- **D-02:** The client returns an **app-owned result record** (e.g. `JevJudgment`) carrying `model`, `requestId`, the noul values by name, the score answers by name (value, maxLevel, confidence at minimum), and token usage. SDK response types stop at the client boundary. `model` must be the response's model id, not the configured default (SC2 / JEV-03). — **Reversibility:** costly — Phases 3–5 consume this record, so reshaping it later touches the scorer, preview and storage code.
- **D-03:** On the input side, callers pass **SDK `Question` types** (`Noul`, `Score`) built with the SDK builders. Don't create an app-owned question wrapper.
- **D-04:** State is passed as an ordered `Map` (a `LinkedHashMap`, since `Map.of` randomizes wire order and rejects nulls), and the implementation drops null values. The top-level state must be an object, never a bare number or boolean, which the API rejects with 422.
- **D-05:** **Transport = Reactor Netty.** Clone the auto-configured `RestClient.Builder` so the User-Agent customizer and observations still apply. Give it a Jev-specific Reactor Netty request factory with a **5s connect and 5s read timeout**. Do **not** introduce `JdkClientHttpRequestFactory` (research Pattern A's code); that would add a second HTTP stack and contradict Phase 1 D-01. The global `spring.http.clients.*` 5s/30s settings still apply to feeds and Raindrop. The Jev read timeout is deliberately tighter, so a slow API trips the breaker quickly.

#### Failure semantics
- **D-06:** **Rejected key (401 `TypeSafeAuthenticationException`, 403 `TypeSafePermissionDeniedException`) is permanent but counts toward the breaker.** It is not retried, and it is **not** in the breaker's `ignore-exceptions`. A bad key therefore opens the breaker and scoring pauses on its own. Log a WARN with the `requestId` and never the key. Phase 4 must treat these like an open circuit (transient, no attempt burned), so articles get scored once the key is fixed. Record that as a note for Phase 4.
- **D-07:** **Honor 429 `Retry-After`.** Configure the `jev` retry instance with a custom interval function. When the exception is `TypeSafeRateLimitException` with `retryAfterMs()` present, wait that long, capped at about 10s. Otherwise use exponential backoff: 1s, then 2s, with max-attempts 3. This keeps one retry layer without losing the server's hint.
- **D-08:** **The fallback rethrows typed exceptions.** Rethrow the original `TypeSafeException` subtype, `JevNotConfiguredException`, or `CallNotPermittedException` (breaker open) unchanged. Do **not** follow Raindrop's wrap-as-`IllegalStateException` pattern, because the Phase 4 worker classifies transient vs permanent from the real type. If an identity fallback turns out to add nothing over omitting `fallbackMethod`, the planner may omit it. The observable behavior (typed exceptions propagate) is what is locked.
- **D-09:** **Breaker config** (starting values; Phase 7 re-tunes): `COUNT_BASED`, sliding-window-size 20, minimum-number-of-calls 10, failure-rate-threshold 50, wait-duration-in-open-state 60s, slow-call-duration-threshold 3s, slow-call-rate-threshold 50, permitted-number-of-calls-in-half-open-state 3 (matching Raindrop).
- **D-10:** **Keyless startup log:** when the key is absent or blank, log one INFO line at startup, e.g. "TypeSafe Jev not configured; interest scoring disabled". Never log the key, its length, or its prefix.

### Claude's Discretion
- **Live smoke proof for SC2 (not discussed; use the research default):** a live test gated by `@EnabledIfEnvironmentVariable` and excluded from default `./gradlew test`. It asserts a real `judge(...)` returns `model` = `jev-1.13.0`. Wire-level contract tests use `MockRestServiceServer` bound to the builder, with no network access.
- **Deploy scope (not discussed; use the research default):** this phase ships the chart and `deploy.sh` changes and verifies them with `helm template` (key set and unset render correctly, and the checksum annotation changes when only the key changes). Whether to also deploy to k3s in this phase, and whether to preserve an existing key across a `helm upgrade` without the variable (e.g. Helm `lookup`), is left to the planner. The default is to match Raindrop's current behavior and not add `lookup`.
- **Class and package names:** `JevApiClient`, `JevApiClientImpl`, `JevJudgment`, `JevNotConfiguredException` and `TypeSafeConfig`, in `integration/` or `config/` following the Raindrop layout. Exact names are up to the planner.
- **Exception mapping for 401/403 vs `CallNotPermittedException`:** how Phase 4 reads them is Phase 4's call. This phase only guarantees the types propagate.
- **Resolved-classpath check:** a one-time `./gradlew dependencies` check for Jackson/Spring versions after adding the starter.

### Deferred Ideas (OUT OF SCOPE)
- **Note for Phase 4:** treat 401/403 and `CallNotPermittedException` as transient (don't consume the article's 3 attempts), per D-06.
- **Possible for Phase 4 or 7:** preserve an existing TypeSafe key across `helm upgrade` runs that omit the variable (Helm `lookup`). Raindrop has the same gap today.
- **Phase 7:** re-tune breaker, retry and 429 cap values against real launch-backfill traffic.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| JEV-01 | App starts and runs normally when the TypeSafe API key is absent, blank, or set (app-owned `TypeSafeClient` bean; context tests for each case) | Probe `KeyContextProbeTest`: app-owned bean starts for absent, blank and set; the starter alone with a blank key fails (negative control). Needs `@EnableConfigurationProperties(TypeSafeProperties.class)` because `@ConfigurationPropertiesScan` doesn't cover the starter's package. See Pattern 1, Code Examples §1. |
| JEV-02 | Dedicated client bean with `@CircuitBreaker` + `@Retry`; Resilience4j is the only retry layer; 400/422 don't open the breaker | **Needs `spring-boot-starter-aspectj`** (Pitfall 1, verified). Probes confirmed: 500 → 3 attempts, all recorded; 422 → 1 attempt, not recorded; 401 → 1 attempt, recorded; 429 with `retry-after-ms: 700` → waits ~700ms; breaker OPEN after 10 recorded attempts, then `CallNotPermittedException`. See Patterns 2–3. |
| JEV-03 | Model pinned (`jev-1.13.0`); model id stored with every score | `spring.ai.typesafe.model: jev-1.13.0` → `TypeSafeClient.defaultModel()` → request `"model":"jev-1.13.0"` on the wire (verified). `JevJudgment.model` comes from `SystemOneResponse.model()`, the response body. Storage is Phase 4; this phase exposes it. Live proof needs a key (human checkpoint). |
| JEV-04 | Key is an optional secret in `deploy.sh` and Helm; a key-only change rolls the pod | Rendered a modified chart copy with `helm template` (Helm v4.3.0): an empty key renders `""`, and `checksum/secret` changes between key-A and key-B but not on an image-tag-only change. `helm lint` passes. See Pattern 5. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Actionable directives from `./CLAUDE.md`, `./.claude/CLAUDE.md` and `~/.claude/CLAUDE.md` that constrain this phase:

- **Gradle Kotlin DSL only; never Maven.** Dependencies go in `build.gradle.kts`.
- **BOM-managed versions:** don't put a version on BOM-managed deps (`spring-boot-starter-aspectj` is Boot-BOM-managed). The TypeSafe starter is in no BOM and needs an explicit version (use `extra["typesafeVersion"]`).
- **Resilience4j convention:** `@CircuitBreaker(name=…)` + `@Retry(name=…)` go on the **API-client bean**, not the service. Business validation (not-configured) runs outside the HTTP call. Self-invocation bypasses the proxy, so annotated methods live on a separate bean. Config lives in `application.yaml` under `resilience4j.circuitbreaker.instances` / `resilience4j.retry.instances`, and the not-configured exception is listed in both `ignore-exceptions`.
- **Don't bypass the auto-configured `RestClient.Builder`** (User-Agent customizer). Don't construct a raw `RestClient.builder()` in production code.
- **Reactor Netty is the pinned transport** (Phase 1 D-01, guarded by `HttpClientConfigurationTest`).
- **Jackson 3:** `tools.jackson.databind.*`; annotations stay in `com.fasterxml.jackson.annotation.*`.
- **Test YAML shadows main:** `src/test/resources/application.yaml` must duplicate every config block the tests depend on (including `resilience4j.*.instances.jev`).
- **Test patterns:** Mockito `@ExtendWith(MockitoExtension.class)` for units, `MockRestServiceServer` for HTTP clients, `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` for full context (Docker required).
- **deploy.sh / Helm:** `set -euo pipefail`; optional secrets use `${VAR:-}` plus a warning (Raindrop precedent). Always pass `$VERSION` explicitly to `deploy.sh`.
- **Surgical changes / simplicity first:** no speculative abstraction. Every changed line traces to the request. Mention unrelated dead code rather than deleting it.
- **Git:** non-trivial work goes on a feature branch; merges to main use `--no-ff`; subagents never check out a detached SHA.
- **GSD workflow:** file edits happen through `/gsd-execute-phase`.
- **Never log secrets** (project security posture). Actuator stays at default exposure (health only).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| TypeSafe client construction (key, model, transport, timeouts) | API / Backend (Spring config) | — | Built once at startup. The key comes from env via Spring properties. |
| Resilience (retry, breaker, 429 backoff) | API / Backend (AOP proxy on `JevApiClientImpl`) | — | The project convention puts resilience on the API-client bean. |
| Not-configured gating | API / Backend (`JevApiClientImpl.requireConfigured()`) | — | Throws before any HTTP call; ignored by the breaker and retry. |
| Request state shaping (ordered map, drop nulls, object top-level) | API / Backend (`JevApiClientImpl`) | Callers (Phases 3/4) | The client enforces the invariants; callers supply content. |
| Secret storage and injection | Deploy (Helm Secret + env) | `deploy.sh` | K8s Secret → env var → Spring property. |
| Pod roll on secret change | Deploy (pod-template `checksum/secret`) | — | Only a pod-template change triggers a Deployment rollout. |

No browser, CDN or database tier is involved in this phase.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springaicommunity:spring-ai-starter-typesafe` | 0.1.0 (only release; Maven Central `lastUpdated 20260920142257`) | `TypeSafeProperties` binding (`spring.ai.typesafe.*`) + transitive `typesafe-java-sdk:0.1.0` | Locked by the user. The spring.io blog gives exactly these coordinates [CITED: spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment]. POM depends only on `typesafe-java-sdk` and `spring-boot-starter` [VERIFIED: Maven Central POM]. |
| `org.springframework.boot:spring-boot-starter-aspectj` | BOM-managed (Boot 4.0.8 → `aspectjweaver 1.9.25.1`) | Enables the `@Aspect`-based Resilience4j annotations | **Required for JEV-02.** Resilience4j docs: "The module expects that `org.springframework.boot:spring-boot-starter-actuator` and `org.springframework.boot:spring-boot-starter-aop` are already provided at runtime." [CITED: resilience4j.readme.io/docs/getting-started-3]. In Boot 4 the starter is named `-aspectj`; the Boot 4.0.8 BOM lists `spring-boot-starter-aspectj` and no `-aop` [VERIFIED: spring-boot-dependencies-4.0.8.pom lines 33, 2076]. |

### Supporting (already on the classpath; no change)
| Library | Version (resolved) | Purpose |
|---------|---------|---------|
| `spring-cloud-starter-circuitbreaker-resilience4j` | 5.0.3 → resilience4j 2.3.0 | Registries, `resilience4j.*` binding, aspects (once AspectJ is present) [VERIFIED: `./gradlew dependencies`] |
| `io.projectreactor.netty:reactor-netty-http` | 1.3.7 | Transport (Phase 1 D-01) |
| `spring-web` / `jackson-databind` | 7.0.9 / 3.1.5 | ≥ the SDK's build versions (7.0.8 / 3.1.4). No downgrade [VERIFIED: `./gradlew dependencies`] |
| `spring-boot-starter-restclient-test` | BOM | `MockRestServiceServer` (already a testImplementation) |
| JDK `com.sun.net.httpserver.HttpServer` | JDK 25 | Real-socket stub for AOP/breaker/timeout tests (no new dependency) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `spring-boot-starter-aspectj` + annotations | Programmatic `Retry.decorateSupplier(...)` / `CircuitBreaker.decorateSupplier(...)` using the registries | Needs no AspectJ and doesn't change Raindrop, but breaks the project's annotation convention. Rejected: the convention is documented in CLAUDE.md and the user locked "`@CircuitBreaker` + `@Retry`". |
| `ClientHttpRequestFactoryBuilder.reactor()` | The injected `ClientHttpRequestFactoryBuilder<?>` bean (follows whatever transport Boot detects) | Either works. `reactor()` makes D-05 explicit and can't silently drift. Recommend `reactor()`. |
| No `fallbackMethod` | Identity fallback that rethrows | Probe showed typed exceptions, including `CallNotPermittedException`, propagate unchanged with no fallback. An identity fallback is dead code. **Omit it** (D-08 permits this). |

**Installation (build.gradle.kts):**
```kotlin
extra["springAiVersion"] = "2.0.1"
extra["springCloudVersion"] = "2025.1.3"
extra["typesafeVersion"] = "0.1.0"
// dependencies { ... }
implementation("org.springframework.boot:spring-boot-starter-aspectj") // activates @CircuitBreaker/@Retry aspects
// Not in any BOM. Pulls in typesafe-java-sdk only (no Spring AI artifacts). Do NOT add typesafe-spring-ai.
implementation("org.springaicommunity:spring-ai-starter-typesafe:${property("typesafeVersion")}")
```
The existing lines (`build.gradle.kts:28-29`) are, verbatim: `extra["springAiVersion"] = "2.0.1"` and `extra["springCloudVersion"] = "2025.1.3"` [VERIFIED: build.gradle.kts:28-29].

## Package Legitimacy Audit

The `gsd-tools package-legitimacy` seam supports only npm, PyPI and crates (`Usage: … --ecosystem <npm|pypi|crates>`), so these Maven artifacts were checked by hand against Maven Central.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.springaicommunity:spring-ai-starter-typesafe:0.1.0` | Maven Central | 2 days (jar `last-modified: Sun, 20 Sep 2026 14:21:15 GMT`) | n/a (Central doesn't publish counts) | github.com/spring-ai-community/spring-ai-typesafe (POM `<scm>`), Apache-2.0 | Manual: new but authoritative (spring.io blog cites these exact coordinates) | Approved: user-locked in CONTEXT. Sources were read this session; there is no postinstall-equivalent in Maven. |
| `org.springaicommunity:typesafe-java-sdk:0.1.0` (transitive) | Maven Central | 2 days | n/a | same repo | Manual: OK | Approved (transitive) |
| `org.springframework.boot:spring-boot-starter-aspectj` | Maven Central (Boot BOM 4.0.8) | Boot 4 line | n/a | github.com/spring-projects/spring-boot | OK: first-party Spring Boot starter in the managed BOM | Approved. It is a **new dependency not in CONTEXT**, so surface it to the user (see Open Questions). |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none. The TypeSafe starter is only 2 days old; the user already chose it after the milestone research, so no extra checkpoint is needed beyond the existing locked decision.

## Architecture Patterns

### System Architecture Diagram

```
caller (Phase 3 preview / Phase 4 scorer)
   │ judge(LinkedHashMap state, Map<String,Question>)
   ▼
[JevApiClient interface] ──▶ CGLIB proxy (exists ONLY with spring-boot-starter-aspectj)
                                │
                                ▼
                        RetryAspect "jev"  (outer; order LOWEST-5)
                        │  retry-exceptions: 429 / 5xx(+529) / ApiConnection(incl. timeouts)
                        │  wait: RetryConfigCustomizer IntervalBiFunction
                        │        (429 retry-after-ms ≤10s, else 1s,2s)
                        ▼  (each attempt)
                        CircuitBreakerAspect "jev" (inner; order LOWEST-4)
                        │  records every attempt except ignore-exceptions
                        │  OPEN → CallNotPermittedException (not retried)
                        ▼
                JevApiClientImpl.judge()
                  ├─ requireConfigured(): blank key → JevNotConfiguredException (ignored by both)
                  ├─ drop null state values, keep order
                  ├─ TypeSafeClient.systemOne(state, questions)   (SDK max-retries 0)
                  │     └─ RestClient (cloned Boot builder: User-Agent + observations)
                  │          └─ ReactorClientHttpRequestFactory (connect 5s / read 5s)
                  │               └─ POST {base-url}/v1/systemone  Bearer <supplier key>
                  │     errors → TypeSafeResponseErrorHandler → typed TypeSafe*Exception
                  ├─ 401/403 → WARN log with requestId (never the key), rethrow
                  └─ map SystemOneResponse → JevJudgment(model from response, requestId, nouls, scores, tokens)
```

### Recommended Project Structure
```
src/main/java/org/bartram/myfeeder/
├── config/TypeSafeConfig.java              # @EnableConfigurationProperties(TypeSafeProperties), TypeSafeClient bean, RetryConfigCustomizer("jev"), keyless INFO log
└── integration/
    ├── JevApiClient.java                   # interface: JevJudgment judge(Map<String,?>, Map<String,? extends Question>)
    ├── JevApiClientImpl.java               # @Component, @CircuitBreaker(name="jev") @Retry(name="jev"), requireConfigured()
    ├── JevJudgment.java                    # app-owned record (+ nested JevScore record)
    └── JevNotConfiguredException.java      # mirrors RaindropNotConfiguredException
src/test/java/org/bartram/myfeeder/
├── config/TypeSafeConfigTest.java          # ApplicationContextRunner: absent/blank/set, keyless log, no key leak, main-yaml pins
└── integration/
    ├── JevApiClientImplTest.java           # MockRestServiceServer wire contract + typed exception mapping + JevJudgment mapping
    ├── JevResilienceTest.java              # ApplicationContextRunner + AOP + JDK HttpServer stub: proxy, retry, breaker, 429
    └── JevLiveSmokeTest.java               # gated by env var; asserts model == jev-1.13.0
```

### Pattern 1: App-owned `TypeSafeClient` bean (Pattern A with Reactor Netty)
**What:** Define the client yourself so the starter's `@ConditionalOnMissingBean` backs off and its blank-key `Assert.state` never runs.
**Why it works (source):** the starter's auto-config is `@ConditionalOnProperty(prefix = TypeSafeProperties.CONFIG_PREFIX, name = "api-key")` + `@EnableConfigurationProperties(TypeSafeProperties.class)`. Its bean method is `@ConditionalOnMissingBean` and calls `Assert.state(StringUtils.hasText(properties.getApiKey()), () -> "No API key configured. Set " + TypeSafeProperties.CONFIG_PREFIX + ".api-key.")` [VERIFIED: TypeSafeAutoConfiguration.java:46-68 in the 0.1.0 sources jar]. `CONFIG_PREFIX = "spring.ai.typesafe"` [VERIFIED: TypeSafeProperties.java:45].
**Key facts:**
- `@EnableConfigurationProperties(TypeSafeProperties.class)` is required on `TypeSafeConfig`. When the property is absent (the test YAML case), the auto-config class is skipped entirely, so nothing else registers `TypeSafeProperties`. `MyfeederApplication`'s `@ConfigurationPropertiesScan` only scans `org.bartram.myfeeder`.
- `TypeSafeClient.Builder.apiKey(String)` runs `Assert.hasText(apiKey, …)`. `apiKey(Supplier<String>)` only asserts non-null [VERIFIED: TypeSafeClient.java:470-485]. Use the supplier.
- When `restClientBuilder(...)` is supplied, the SDK leaves the request factory untouched ("its request factory is left untouched, so a caller keeps full control of timeouts") [VERIFIED: TypeSafeClient.java:534-546, 579-584]. The Reactor factory you set is therefore what's used.
- `.timeout(...)` on the SDK builder is **not** applied to a supplied transport; it only feeds the SDK retry budget [VERIFIED: TypeSafeClient.java:499-512]. With `max-retries: 0` it's cosmetic, but declare it anyway.
- The builder falls back to env vars `TYPESAFE_BASE_URL` / `TYPESAFE_DEFAULT_MODEL` / `TYPESAFE_API_KEY` only when the corresponding setter wasn't called [VERIFIED: TypeSafeClient.java:559-577; TypeSafeConstants `API_KEY_ENV = "TYPESAFE_API_KEY"`, `BASE_URL_ENV = "TYPESAFE_BASE_URL"`, `DEFAULT_MODEL_ENV = "TYPESAFE_DEFAULT_MODEL"`]. Always set all three from properties.
- Verified by probe (Boot 4.0.8): `appOwned-absent started=true clientBeans=1`, `appOwned-blank started=true clientBeans=1`, `appOwned-set started=true clientBeans=1 model=jev-1.13.0`. Negative control: `starterOnly-blank started=false failure=IllegalStateException: No API key configured. Set spring.ai.typesafe.api-key.`

### Pattern 2: Resilience via annotations, which requires AspectJ
**What:** `@CircuitBreaker(name = "jev")` + `@Retry(name = "jev")` on `JevApiClientImpl.judge`. **No `fallbackMethod`.**
**Aspect order (source):** `retryAspectOrder = Ordered.LOWEST_PRECEDENCE - 5`, `circuitBreakerAspectOrder = Ordered.LOWEST_PRECEDENCE - 4` [VERIFIED: resilience4j-spring6 2.3.0 sources, RetryConfigurationProperties.java:26 and CircuitBreakerConfigurationProperties.java:23]. Retry is **outer**, so the breaker records **every attempt**. (CLAUDE.md's "CircuitBreaker (outer) + Retry (inner)" describes annotation placement, not execution order.) Consequence, verified: with window 20 / min-calls 10, four articles that each hit 3 × 5xx put 10 attempts in the window and open the breaker.
**Probe evidence (with `spring-boot-starter-aspectj`):**
```
PROBE isAopProxy=true class=probe.JevClient$$SpringCGLIB$$0
PROBE 422 ex=...TypeSafeUnprocessableEntityException methodCalls=1 httpHits=1
PROBE cb after422 failed=0 success=0 notPermitted=0 state=CLOSED
PROBE 401 ex=...TypeSafeAuthenticationException methodCalls=1 httpHits=1 requestId=req-1
PROBE cb after401 failed=1 success=1 notPermitted=0 state=CLOSED
PROBE 429 then ok model=jev-1.13.0 methodCalls=2 httpHits=2 elapsedMs=723      (retry-after-ms: 700)
PROBE 500 ex=...TypeSafeInternalServerException methodCalls=3 httpHits=3 elapsedMs=316   (probe base 100ms)
PROBE runner state=OPEN last=CallNotPermittedException httpHits=10 cfgWindow=20 minCalls=10
```
**Same probe without AspectJ (today's classpath):**
```
PROBE isAopProxy=false class=probe.JevClient
PROBE aspectjOnClasspath=false
PROBE 500 ex=...TypeSafeInternalServerException methodCalls=1 httpHits=1 elapsedMs=3
PROBE cb after500 failed=0 success=0 notPermitted=0 state=CLOSED
AopAndResilienceProbeTest > e_429retryAfter() FAILED
```

### Pattern 3: Retry-After-aware interval via `RetryConfigCustomizer`
**What:** A `RetryConfigCustomizer.of("jev", b -> b.intervalBiFunction(...))` bean. The customizer runs **after** the YAML properties in `CommonRetryConfigurationProperties.buildConfig`, and `Builder.intervalBiFunction(f)` nulls any `intervalFunction`, so it cleanly overrides `wait-duration` with no "configured twice" `IllegalStateException` [VERIFIED: resilience4j-framework-common 2.3.0 CommonRetryConfigurationProperties.java:153-163; resilience4j-retry RetryConfig.java:312-326, 394-396]. Probe: YAML `wait-duration: 1s`, customizer base 100ms, observed 316ms for two waits (customizer won). A 429 with `retry-after-ms: 700` → 723ms.
**`attempt`** is 1 for the first wait. With `max-attempts: 3` the waits are f(1), f(2) → 1s, 2s.
**`retryAfterMs()`** returns `@Nullable Long`. The handler reads `retry-after-ms` first, then `Retry-After` seconds × 1000, and ignores the HTTP-date form [VERIFIED: TypeSafeRateLimitException.java:37-53; TypeSafeResponseErrorHandler.java:100, 112-119].
**Don't** use `exponential-backoff-multiplier` in YAML for this. It only takes effect with `enable-exponential-backoff: true` [VERIFIED: CommonRetryConfigurationProperties.java:176-186]. Side note for the user: Raindrop's `exponential-backoff-multiplier: 2` (application.yaml:40) is therefore inert; its backoff is a fixed 1s.

### Pattern 4: Thin pass-through + app-owned `JevJudgment`
Response surface to map (all [VERIFIED: 0.1.0 sources]):
- `SystemOneResponse(String model, Map<String, Answer> answers, Usage usage, @Nullable String requestId)`. `requestId` comes from the `x-typesafe-request-id` response header. `nouls()` / `scores()` return ordered, type-filtered maps and never throw.
- `NoulAnswer(double value)`, JSON `"noul"`.
- `ScoreAnswer(double value /*"score"*/, Map<Integer,JsonContent> legend, Map<Integer,Double> probabilities, double confidence)` + `maxLevel()`, the highest legend key, or -1 when the legend is missing.
- `Usage(@Nullable Integer inputTokens, @Nullable Integer outputTokens)`, with `Usage.EMPTY` when absent.
- The SDK already throws `TypeSafeApiResponseValidationException` for an empty body or empty `answers` [VERIFIED: TypeSafeClient.java:189-196].
- Recommend that `judge` verifies **every requested question name is present** and otherwise throws `new TypeSafeMissingAnswerException(name, response.answers().keySet())` (constructor `(String name, Collection<String> availableNames)` [VERIFIED: TypeSafeMissingAnswerException.java:35]). That gives the locked breaker-ignore entry a real trigger. `UnknownAnswer` (future answer types) is skipped.
- `model` **must** be `response.model()`. Test it with a canned response whose model differs from the configured default (e.g. `"jev-1.13.0-canary"`), so the test fails if someone uses `client.defaultModel()`.
- D-04: null state values are serialized as JSON `null`. Probe wire: `{"state":{"title":"T","summary":null},...}`. The impl must drop them: `new LinkedHashMap<>(state)` then `values().removeIf(Objects::isNull)`.
- Request wire (verified): `{"state":{"feed":"F","title":"T"},"model":"jev-1.13.0","questions":{"profile":{"type":"score","instructions":"…","criteria":["no","low",…]},"t1":{"type":"noul","instructions":"…","criteria":{"true":"…","false":"…"}}}}`.

### Pattern 5: Helm optional secret + `checksum/secret`
Mirror Raindrop exactly. The current anchors, verbatim:
- `values.yaml:47-51`: `secrets:` / `postgresPassword: ""` / `anthropicApiKey: ""` / `raindropApiToken: ""` / `googleApplicationCredentials: ""` [VERIFIED].
- `app-secret.yaml:12`: `  myfeeder-raindrop-api-token: {{ .Values.secrets.raindropApiToken | quote }}` [VERIFIED].
- `app-deployment.yaml:48-52`: `- name: MYFEEDER_RAINDROP_API_TOKEN` … `key: myfeeder-raindrop-api-token` [VERIFIED].
- `app-deployment.yaml:13-17`: `  template:` / `    metadata:` / `      labels:` with **no** `annotations:` today [VERIFIED].
- `deploy.sh:12-16, 24`: `RAINDROP_TOKEN="${MYFEEDER_RAINDROP_API_TOKEN:-}"` + `if [[ -z "$RAINDROP_TOKEN" ]]; then echo "Warning: …"` + `--set secrets.raindropApiToken="$RAINDROP_TOKEN" \` [VERIFIED].

Rendered on a copy of the chart with Helm v4.3.0 (same env/secret additions + annotation):
```
key=''       myfeeder-typesafe-api-key: ""       checksum/secret: 2c7e4149…
key=key-A    myfeeder-typesafe-api-key: "key-A"  checksum/secret: 64097869…
key=key-B    myfeeder-typesafe-api-key: "key-B"  checksum/secret: 58411413…
image-tag-only change (key-A)                     checksum/secret: 64097869…   (unchanged)
helm lint: 1 chart(s) linted, 0 chart(s) failed
```
Adding the annotation rolls the pod once on the first deploy; after that it rolls on any Secret change (postgres password, Anthropic key, Raindrop token and TypeSafe key alike). That is the intended behavior.

### Anti-Patterns to Avoid
- **Adding annotations without AspectJ.** They compile and do nothing, and existing tests can't tell. Every resilience test must assert `AopUtils.isAopProxy(bean)`.
- **Binding `MockRestServiceServer` to the context builder that `TypeSafeConfig` clones.** `TypeSafeConfig` then calls `.requestFactory(reactor)`, which replaces the mock factory. For wire tests, build the `TypeSafeClient` directly with `TypeSafeClient.builder()...restClientBuilder(mockBoundBuilder)` (verified working through the SDK's internal `clone()`: `PROBE mockServer model=jev-1.13.0 requestId=r-1`). For AOP tests, use a JDK `HttpServer` stub and point `spring.ai.typesafe.base-url` at it.
- **Raindrop-style fallback wrapping** (`IllegalStateException`). Forbidden by D-08.
- **Classifying timeouts by `TypeSafeApiTimeoutException`.** Reactor Netty timeouts never produce it (Pitfall 3).
- **`@Value Duration` in beans exercised by `ApplicationContextRunner`.** The runner has no `ApplicationConversionService`. Probe failure: `Failed to convert value of type 'java.lang.String' to required type 'java.time.Duration'`. Use `Binder.get(env).bind(..., Duration.class)` or `DurationStyle.detectAndParse(...)`, or pass values through `TypeSafeProperties`.
- **`systemOneAll`**, **`typesafe-spring-ai`**, **`@EnableAsync`**, **`spring.threads.virtual.enabled`**: out of bounds (locked).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP status → error type | Status-code switch on `RestClientResponseException` | SDK `TypeSafeResponseErrorHandler` (already wired) | Maps 400/401/403/404/422/429/5xx/529 and parses `retry-after-ms` / `Retry-After` |
| Transport failure typing | Catching `ResourceAccessException` | SDK `execute()` translation | Already returns `TypeSafeApiConnectionException` with the cause chain |
| Answer JSON parsing | Custom DTOs | `SystemOneResponse` + `AnswerDeserializer` | Handles unknown future answer types (`UnknownAnswer`) |
| Reactor factory with timeouts | `HttpClient.create().option(CONNECT_TIMEOUT_MILLIS…)` by hand | `ClientHttpRequestFactoryBuilder.reactor().build(HttpClientSettings.defaults().withTimeouts(c, r))` | Boot API, verified present in spring-boot-http-client 4.0.8 |
| Retry + breaker | Loops / counters | Resilience4j annotations + `RetryConfigCustomizer` | Locked convention; also produces metrics and breaker state for Phase 4's status endpoint |
| Pod roll on secret change | Manual `kubectl rollout restart` | `checksum/secret` annotation | Standard Helm idiom; verified above |

**Key insight:** the SDK already does the hard HTTP/JSON/error-typing work. This phase is almost entirely wiring, and wiring fails silently (inert annotations, a replaced mock factory, shadowed YAML), so every piece needs a test that would fail if the wiring broke.

## Common Pitfalls

### Pitfall 1: Resilience4j annotations are inert (no AspectJ on the classpath)
**What goes wrong:** `@CircuitBreaker`/`@Retry` silently do nothing: no retries, no breaker, no ignore-exceptions. JEV-02 appears done and isn't.
**Why:** `resilience4j-spring6`'s aspect beans carry `@Conditional(AspectJOnClasspathCondition.class)`, which checks `org.aspectj.lang.ProceedingJoinPoint` and logs at debug "Aspects are not activated because AspectJ is not on the classpath." [VERIFIED: javap of resilience4j-spring6-2.3.0 `AspectJOnClasspathCondition`]. `./gradlew dependencies --configuration runtimeClasspath | grep -ic aspectj` → `0`, and the test classpath is also empty [VERIFIED this session]. No commit ever added it (`git log -S aop/-S aspectj -- build.gradle.kts` returns nothing).
**How to avoid:** add `implementation("org.springframework.boot:spring-boot-starter-aspectj")`. Guard it with a test asserting `AopUtils.isAopProxy(jevApiClient)` in an AOP-enabled context.
**Side effect to plan for:** Raindrop's `@CircuitBreaker`/`@Retry` start working. Its `retry.instances.raindrop` has no `retry-exceptions`, so it retries **everything** except `RaindropNotConfiguredException`, 3 attempts at a fixed 1s. The fallback converts failures to `IllegalStateException` → 409. Previously a Raindrop HTTP error propagated raw. Run the full `./gradlew test` and note the change for the user. Don't retune Raindrop in this phase (surgical changes).
**Warning signs:** the bean class name has no `$$SpringCGLIB$$`; a 5xx stub gets exactly one hit.

### Pitfall 2: The blank key crashes startup (starter's own bean)
**What goes wrong:** with `api-key: ${MYFEEDER_TYPESAFE_API_KEY:}` and no app-owned bean, startup fails with `IllegalStateException: No API key configured.` (re-verified on Boot 4.0.8).
**How to avoid:** Pattern A, plus a context test that includes `TypeSafeAutoConfiguration` and a blank key. The test fails if someone deletes the app-owned bean.

### Pitfall 3: Reactor Netty timeouts are `TypeSafeApiConnectionException`, not `TypeSafeApiTimeoutException`
**What goes wrong:** Phase 4 (or a test) branches on `TypeSafeApiTimeoutException` and never matches.
**Why:** the SDK maps to the timeout subclass only when the cause is `SocketTimeoutException` / `HttpConnectTimeoutException` / `HttpTimeoutException` [VERIFIED: TypeSafeClient.java:425-432]. Reactor Netty's are wrapped differently. Probe chains:
- read timeout (500ms) → `TypeSafeApiConnectionException > ResourceAccessException > IOException > io.netty.handler.timeout.ReadTimeoutException` (elapsed 509–749ms)
- connect timeout (black-hole, 800ms) → `TypeSafeApiConnectionException > ResourceAccessException > io.netty.channel.ConnectTimeoutException`
- refused → `TypeSafeApiConnectionException > ResourceAccessException > AnnotatedConnectException > ConnectException`

**How to avoid:** the retry list already uses the base `TypeSafeApiConnectionException` (matches subclasses too). Tell Phase 4 to classify on the base type.

### Pitfall 4: Tests that replace the transport, or read the wrong YAML
- A `MockRestServiceServer` bound before `TypeSafeConfig` gets overridden (see Anti-Patterns).
- `src/test/resources/application.yaml` shadows main, so a test that only loads the classpath YAML never sees the main pins. Follow `HttpClientConfigurationTest`: load `src/main/resources/application.yaml` from disk to assert `spring.ai.typesafe.model: jev-1.13.0`, `retry.max-retries: 0` and the `jev` resilience4j instances. Use `addLast`, not `addFirst`, if the test also passes `withPropertyValues`. Probe: with `addFirst` the YAML's `${…:}` blank key beat the test property and every call threw `JevNotConfiguredException`.
- `@EnabledIfEnvironmentVariable(named="MYFEEDER_TYPESAFE_API_KEY")` alone would run the live test under plain `./gradlew test` for any developer with the key exported. Gate on a dedicated opt-in var (e.g. `JEV_LIVE_SMOKE=true`) so the default run stays offline.

### Pitfall 5: Key leakage
**What goes wrong:** the key appears in logs (D-10 INFO line, a D-06 WARN, exception messages) or via actuator.
**How to avoid:** log only fixed text plus `requestId`. The SDK builds the bearer header per request from the supplier, via `setBearerAuth(apiKey.get())` [VERIFIED: TypeSafeApi.java:101], so there is no key in any toString. Assert with `OutputCaptureExtension` that a distinctive fake key (e.g. `sk-test-LEAKCHECK`) never appears in output during context start and a 401 call. Keep actuator at default exposure (application.yaml has no `management.*`).

### Pitfall 6: `deploy.sh` under `set -u`
**What goes wrong:** `$MYFEEDER_TYPESAFE_API_KEY` referenced bare aborts the deploy when unset.
**How to avoid:** `TYPESAFE_KEY="${MYFEEDER_TYPESAFE_API_KEY:-}"` + warning, mirroring lines 12-16. Note that `--set` parses commas and backslashes in values. Raindrop has the same exposure and real TypeSafe keys are presumably URL-safe [ASSUMED]; `--set-string` would be the safer flag, but it would diverge from the Raindrop line. Planner's call.

## Code Examples

### 1. `TypeSafeConfig` (Pattern A + Reactor + Retry-After customizer)
```java
// Sources: TypeSafeAutoConfiguration/TypeSafeClient 0.1.0 (read this session); probe TypeSafeConfig (executed)
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TypeSafeProperties.class) // autoconfig is skipped when api-key is absent
public class TypeSafeConfig {

    static final Duration JEV_CONNECT_TIMEOUT = Duration.ofSeconds(5); // D-05
    static final long MAX_RETRY_AFTER_MS = 10_000L;                    // D-07 cap

    @Bean
    TypeSafeClient typeSafeClient(TypeSafeProperties props, RestClient.Builder builder) {
        if (!StringUtils.hasText(props.getApiKey())) {
            log.info("TypeSafe Jev not configured; interest scoring disabled"); // D-10: never the key/length/prefix
        }
        ReactorClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.reactor()
                .build(HttpClientSettings.defaults().withTimeouts(JEV_CONNECT_TIMEOUT, props.getTimeout())); // timeout: 5s in yaml
        return TypeSafeClient.builder()
                .apiKey(() -> Objects.requireNonNullElse(props.getApiKey(), "")) // supplier form: blank is legal
                .baseUrl(props.getBaseUrl())
                .defaultModel(props.getModel())
                .timeout(props.getTimeout())
                .retryPolicy(props.toRetryPolicy())                            // max-retries: 0
                .restClientBuilder(builder.clone().requestFactory(requestFactory)) // keeps User-Agent customizer
                .build();
    }

    @Bean
    RetryConfigCustomizer jevRetryInterval(Environment env) {
        Duration base = Binder.get(env)
                .bind("resilience4j.retry.instances.jev.wait-duration", Duration.class)
                .orElse(Duration.ofSeconds(1)); // Binder works in ApplicationContextRunner, @Value Duration does not
        return RetryConfigCustomizer.of("jev", b -> b.intervalBiFunction((IntervalBiFunction<Object>) (attempt, either) -> {
            if (either.isLeft() && either.getLeft() instanceof TypeSafeRateLimitException rl && rl.retryAfterMs() != null) {
                return Math.min(rl.retryAfterMs(), MAX_RETRY_AFTER_MS);
            }
            return base.toMillis() * (1L << (attempt - 1)); // 1s, 2s
        }));
    }
}
```
Imports: `org.springframework.boot.http.client.{ClientHttpRequestFactoryBuilder,HttpClientSettings}`, `org.springframework.http.client.ReactorClientHttpRequestFactory`, `io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer`, `io.github.resilience4j.core.IntervalBiFunction`, `org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties`, `org.springframework.boot.context.properties.bind.Binder` (the first five compiled in the probe; `Binder` and `org.springframework.boot.convert.DurationStyle` confirmed present in `spring-boot-4.0.8.jar` [VERIFIED: jar listing]. The `Binder` form ran in the probe's `ApplicationContextRunner`: `PROBE binderBaseMs=10` with `wait-duration=10ms`).

### 2. `JevApiClientImpl`
```java
@Slf4j
@Component
public class JevApiClientImpl implements JevApiClient { // not final: CGLIB proxies it
    private final TypeSafeClient client;
    private final TypeSafeProperties properties;
    // constructor injection ...

    @CircuitBreaker(name = "jev")   // no fallbackMethod: typed exceptions + CallNotPermittedException propagate (D-08, verified)
    @Retry(name = "jev")
    @Override
    public JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions) {
        if (!StringUtils.hasText(properties.getApiKey())) throw new JevNotConfiguredException();
        Map<String, Object> cleaned = new LinkedHashMap<>(state);
        cleaned.values().removeIf(Objects::isNull);                         // D-04
        try {
            SystemOneResponse r = client.systemOne(cleaned, questions);
            for (String name : questions.keySet()) {
                if (!r.answers().containsKey(name)) throw new TypeSafeMissingAnswerException(name, r.answers().keySet());
            }
            return JevJudgment.from(r);                                      // model = r.model()
        } catch (TypeSafeAuthenticationException | TypeSafePermissionDeniedException e) {
            log.warn("TypeSafe rejected the API key (status {}, requestId {})", e.status(), e.requestId()); // D-06, never the key
            throw e;
        }
    }
}
```

### 3. Resilience YAML (main **and** test)
```yaml
spring:
  ai:
    typesafe:
      api-key: ${MYFEEDER_TYPESAFE_API_KEY:}   # main only; test yaml omits api-key and pins base-url
      model: jev-1.13.0
      timeout: 5s
      retry:
        max-retries: 0
resilience4j:
  circuitbreaker:
    instances:
      jev:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        slow-call-duration-threshold: 3s
        slow-call-rate-threshold: 50
        permitted-number-of-calls-in-half-open-state: 3
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
          - org.springaicommunity.typesafe.exception.TypeSafeBadRequestException
          - org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException
          - org.springaicommunity.typesafe.exception.TypeSafeMissingAnswerException
          - org.springaicommunity.typesafe.exception.TypeSafeAnswerTypeException
  retry:
    instances:
      jev:
        max-attempts: 3
        wait-duration: 1s        # base for the RetryConfigCustomizer interval (1s, 2s); tests may lower it
        retry-exceptions:
          - org.springaicommunity.typesafe.exception.TypeSafeRateLimitException
          - org.springaicommunity.typesafe.exception.TypeSafeInternalServerException
          - org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException
        ignore-exceptions:
          - org.bartram.myfeeder.integration.JevNotConfiguredException
```
Every `org.springaicommunity.typesafe.exception.*` FQN above corresponds to a file read in the 0.1.0 sources jar (`sdk/org/springaicommunity/typesafe/exception/…java`) [VERIFIED]. This YAML (with `probe.JevNotConfiguredException`) was executed in the probe. `TypeSafeOverloadedException extends TypeSafeInternalServerException` (529), so it's covered by the 5xx entry [VERIFIED: TypeSafeOverloadedException.java].

### 4. Offline wire test (verified approach)
```java
var builder = RestClient.builder();
var server = MockRestServiceServer.bindTo(builder).build();
var sdk = TypeSafeClient.builder().apiKey(() -> "test-key").baseUrl("http://jev.test")
        .defaultModel("jev-1.13.0").retryPolicy(RetryPolicy.noRetry()).restClientBuilder(builder).build();
server.expect(requestTo("http://jev.test/v1/systemone"))
      .andExpect(header("Authorization", "Bearer test-key"))
      .andExpect(jsonPath("$.model").value("jev-1.13.0"))
      .andRespond(withSuccess(CANNED_JSON, MediaType.APPLICATION_JSON).header("x-typesafe-request-id", "r-1"));
```
Canned success body that parses (verified): `{"model":"jev-1.13.0","answers":{"profile":{"type":"score","score":2.6,"legend":{"0":"a",…,"4":"e"},"probabilities":{…},"confidence":0.7},"t1":{"type":"noul","noul":0.91}},"usage":{"input_tokens":123,"output_tokens":4}}` → `score=2.6 maxLevel=4 conf=0.7 noul=0.91`. Error responses: `withStatus(HttpStatus.valueOf(429)).header("retry-after-ms","700")` etc.

### 5. AOP/breaker context test skeleton (no Docker)
```java
new ApplicationContextRunner()
  .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(mainOrTestYaml)) // addLast!
  .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class,
      io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
      io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration.class,
      TypeSafeAutoConfiguration.class, RestClientAutoConfiguration.class,
      HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class))
  .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class))
  .withPropertyValues("spring.ai.typesafe.api-key=test-key", "spring.ai.typesafe.base-url=" + stub.url(),
                      "resilience4j.retry.instances.jev.wait-duration=10ms")
  .run(ctx -> { assertThat(AopUtils.isAopProxy(ctx.getBean(JevApiClient.class))).isTrue(); /* … */ });
```
All class names here compiled and ran in the probe (`PROBE runner isAopProxy=true … state=OPEN last=CallNotPermittedException`).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | Spring Boot 4.0 | Resilience4j's docs still say "-aop". In Boot 4 use `-aspectj` [VERIFIED: Boot 4.0.8 BOM] |
| Starter on Boot 4.0.3 (downgraded Spring 7.0.5 / Jackson 3.0.4) | Boot 4.0.8 (Spring 7.0.9 / Jackson 3.1.5) | Phase 1 | Version-skew pitfall resolved. Keep the dependency check as a guard only |
| Research Pattern A `JdkClientHttpRequestFactory` | Reactor via `ClientHttpRequestFactoryBuilder.reactor()` | D-05 | One HTTP stack. Timeouts map to `TypeSafeApiConnectionException` |

**Deprecated/outdated:** the `spring.http.client.*` singular keys (already renamed in Phase 1). STACK.md's claim that "tests stub `JevApiClient`, add nothing for TypeSafe to test YAML" is superseded by the locked decision to mirror the `jev` blocks and pin `base-url`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Real TypeSafe API keys contain no `,` or `\` (safe with `helm --set`) | Pitfall 6 | Deploy would store a mangled key → 401 → breaker opens (a visible failure). Mitigate with `--set-string` if in doubt |
| A2 | `jev-1.13.0` is still served by the live API (alias pin per docs.typesafe.ai/models, MEDIUM in STACK.md; SDK constant `TypeSafeModels.JEV_1_13_0 = "jev-1.13.0"` verified) | Phase Requirements JEV-03 | A retired model → 400 `TypeSafeBadRequestException`. Only the live smoke test can confirm |
| A3 | A 401/403 response body from TypeSafe does not echo the key (the SDK puts the body in the exception message) | Pitfall 5 | Key in logs if the exception message is logged. Mitigation: log `status` + `requestId` only, never `e.getMessage()` |
| A4 | Activating Raindrop's latent retry/breaker (Pitfall 1 side effect) is acceptable to the user. **CONFIRMED by the user on 2026-09-22** (Open Question 1) | Standard Stack / Open Questions | Raindrop errors change from raw 5xx to 409 after up to 3×1s retries |

## Open Questions (RESOLVED)

1. **Add `spring-boot-starter-aspectj` (not in CONTEXT)?**
   - What we know: without it, JEV-02/SC3 are unachievable with the locked annotation approach (verified). It also activates Raindrop's currently inert resilience config.
   - What's unclear: whether the user wants Raindrop's newly active behavior left as is, or tuned (`retry-exceptions`, `enable-exponential-backoff`) in a separate quick task.
   - Recommendation: add it in the first plan. Run the full backend suite. Record "Raindrop resilience now active" in the phase summary. Propose a follow-up quick task for Raindrop tuning and a CLAUDE.md correction ("the annotations require `spring-boot-starter-aspectj`"). Don't tune Raindrop here.
   - **RESOLVED (user decision, 2026-09-22): accepted as planned.** 02-01 Task 2 adds `spring-boot-starter-aspectj` in its own commit. Raindrop's resilience config and `RaindropApiClientImpl` stay unchanged in this phase, and the user accepts the app-wide side effect: Raindrop failures now surface as HTTP 409 after up to 3 attempts 1s apart. A follow-up quick task is tracked for Raindrop tuning (`retry-exceptions`, `enable-exponential-backoff`) and for correcting the Resilience4j/AspectJ note in CLAUDE.md. CLAUDE.md is not edited in this phase. Assumption A4 is confirmed.
2. **Live smoke (SC2) needs a key that isn't present.** `MYFEEDER_TYPESAFE_API_KEY` and `TYPESAFE_API_KEY` are both unset in this environment and in `.envrc`. The planner should add an end-of-phase `checkpoint:human-verify` where the user runs `JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=… ./gradlew test --tests '*JevLiveSmokeTest'`.
   - **RESOLVED (2026-09-22): as planned.** 02-02 Task 3 adds `JevLiveSmokeTest`, an opt-in test that runs only when `JEV_LIVE_SMOKE=true` and is skipped in the default `./gradlew test`. The user runs it with their key as an end-of-phase human check (`<human-check>` in 02-02, per `human_verify_mode=end-of-phase`).
3. **Deploy to k3s this phase?** Recommendation: no. There's no user-visible feature, and the first deploy with the key is Phase 7. SC4 is verified with `helm template` / `helm lint` plus `bash -n deploy.sh`. If the user wants the keyless deploy exercised, ride it on the next release.
   - **RESOLVED (2026-09-22): no k3s deploy this phase.** 02-04 proves SC4 with `helm template` / `helm lint`, `bash -n deploy.sh` and a fake-helm dry run, with no cluster access. The first deploy with the key is Phase 7.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build/tests | ✓ | 25.0.4 | — |
| Gradle wrapper | build | ✓ | repo `./gradlew` | — |
| Docker | full `./gradlew test` (Testcontainers) | ✓ | Server 29.7.2 | New Jev tests use `ApplicationContextRunner` and don't need Docker |
| Helm | SC4 verification | ✓ | v4.3.0 | — |
| kubectl | optional deploy | ✓ | present | — |
| shellcheck | deploy.sh lint | ✗ | — | `bash -n deploy.sh` |
| TypeSafe API key | SC2 live smoke | ✗ | — | Human checkpoint (user supplies the key) |
| Maven Central | new artifacts | ✓ | jars already in `~/.gradle` cache | — |

**Missing dependencies with no fallback:** a live TypeSafe key (SC2 only), handled by the human checkpoint.
**Missing dependencies with fallback:** shellcheck → `bash -n`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Spring Boot 4.0.8 test starters), AssertJ, Mockito, `MockRestServiceServer`, `ApplicationContextRunner`, `OutputCaptureExtension` |
| Config file | `build.gradle.kts` `tasks.withType<Test> { useJUnitPlatform() … }`; `src/test/resources/application.yaml` |
| Quick run command | `./gradlew test --tests 'org.bartram.myfeeder.integration.Jev*' --tests 'org.bartram.myfeeder.config.*'` (no Docker needed) |
| Full suite command | `./gradlew test` (Docker required) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| JEV-01 | Context starts with key absent / blank / set; app-owned bean present; starter's assert not hit | context (runner) | `./gradlew test --tests '*TypeSafeConfigTest'` | ❌ Wave 0 |
| JEV-01 | One INFO "not configured" line when keyless; fake key never in output | context + OutputCapture | same | ❌ Wave 0 |
| JEV-01 | Full app context still loads (keyless, test YAML) | integration | `./gradlew test --tests '*MyfeederApplicationTests'` | ✅ |
| JEV-02 | Bean is an AOP proxy (guards AspectJ) | context (runner) | `./gradlew test --tests '*JevResilienceTest'` | ❌ Wave 0 |
| JEV-02 | 5xx → 3 attempts; 429 honors `retry-after-ms`; 400/422 → 1 attempt, breaker not recording; 401 → 1 attempt, recorded; breaker OPEN → `CallNotPermittedException` | context + JDK HttpServer stub | same | ❌ Wave 0 |
| JEV-02 | Interval function: retry-after capped at 10s; else base·2^(n−1) | unit | `./gradlew test --tests '*TypeSafeConfigTest'` | ❌ Wave 0 |
| JEV-02/03 | Main YAML pins: `model: jev-1.13.0`, `retry.max-retries: 0`, jev instances bind as specified | unit (main YAML from disk) | `./gradlew test --tests '*TypeSafeConfigTest'` | ❌ Wave 0 |
| JEV-03 | Wire: object state, nulls dropped, order kept, `"model":"jev-1.13.0"`, bearer header; `JevJudgment.model` = response model (not the default); requestId/nouls/scores/usage mapped; missing answer → `TypeSafeMissingAnswerException`; status → typed exception | unit (MockRestServiceServer) | `./gradlew test --tests '*JevApiClientImplTest'` | ❌ Wave 0 |
| JEV-03 | Live call returns `model == jev-1.13.0` | live (gated) | `JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=… ./gradlew test --tests '*JevLiveSmokeTest'` | ❌ Wave 0; manual checkpoint |
| JEV-04 | Chart renders key set/unset; checksum changes on a key-only change and not on a tag-only change; lint passes | shell | `helm template myfeeder helm/myfeeder --set app.image.tag=t --set secrets.typesafeApiKey=A \| grep -E 'checksum/secret\|myfeeder-typesafe-api-key\|MYFEEDER_TYPESAFE_API_KEY'` (repeat with B / empty) + `helm lint helm/myfeeder --set app.image.tag=t` | n/a (commands) |
| JEV-04 | deploy.sh parses; keyless run warns | shell | `bash -n deploy.sh` + `grep -n 'MYFEEDER_TYPESAFE_API_KEY:-' deploy.sh` | n/a |

### Sampling Rate
- **Per task commit:** the quick run command (seconds, no Docker)
- **Per wave merge:** `./gradlew test` (full suite; catches Raindrop side effects of AspectJ)
- **Phase gate:** full suite green + helm checks + resolved-classpath check (`./gradlew dependencies --configuration runtimeClasspath | grep -E 'typesafe|aspectj|jackson-databind|spring-web:'`) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java`: JEV-01 cases, keyless log, leak check, main-YAML pins, interval function
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java`: wire contract + mapping + typed errors
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java`: AOP proxy, retry, breaker, 429 (JDK `HttpServer` stub, `wait-duration=10ms`)
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java`: gated by `JEV_LIVE_SMOKE`
- [ ] `src/test/resources/application.yaml`: add `spring.ai.typesafe` (model, `base-url` pinned to a non-routable/local value, `retry.max-retries: 0`, **no api-key**) and the `resilience4j.*.instances.jev` blocks
- Framework install: none (all libraries present). The only new main dependencies are the two in Standard Stack.

A working, runnable reference for all of the above (stub server, runner config, probes) exists in the scratch probe at `/private/tmp/claude-501/-Users-scottb-orca-workspaces-myfeeder-main/4e33f96c-750e-446d-a36c-92962690ba53/scratchpad/probe/` (session scratch; may be cleaned up).

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (outbound bearer only; no user auth added) | — |
| V3 Session Management | no | — |
| V4 Access Control | no (no new endpoint this phase) | — |
| V5 Input Validation | yes (outbound) | Object-only top-level state, null-dropping, typed SDK questions; the SDK serializes via Jackson (no string concatenation) |
| V6 Cryptography | no (TLS by Reactor Netty to `https://api.typesafe.ai`) | Default base URL `DEFAULT_BASE_URL = "https://api.typesafe.ai"` [VERIFIED: TypeSafeConstants] |
| V7 Error Handling & Logging | yes | No key, length or prefix in logs. WARN with `status` + `requestId` only |
| V14 Configuration | yes | Key only via K8s Secret → env. `${…:}` default. Actuator stays health-only. Test YAML never references a real key var |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key in logs or exception messages | Information disclosure | Fixed-text logs; OutputCapture leak test; never log `e.getMessage()` for 401/403 |
| Key exposed via actuator `env`/`configprops` | Information disclosure | Keep default exposure (no `management.endpoints.web.exposure` change) |
| Bad or rotated key hammering the API | DoS (self) / cost | D-06: 401/403 count toward the breaker → OPEN pauses calls |
| Crash-loop from blank secret | DoS (availability) | Pattern A + blank-key context test |
| Shared-builder interceptor seeing `Authorization` | Information disclosure | Only the header-only User-Agent customizer exists. Don't add logging interceptors to the shared builder |
| Supply chain (2-day-old artifact) | Tampering | Official spring-ai-community repo, Apache-2.0, sources read; pinned exact version `0.1.0` |
| Stale key after rotation (pod not restarted) | Tampering/availability | `checksum/secret` rolls the pod on Secret change |

## Sources

### Primary (HIGH confidence)
- Maven Central `spring-ai-starter-typesafe-0.1.0-sources.jar` and `typesafe-java-sdk-0.1.0-sources.jar` (read this session): `TypeSafeAutoConfiguration`, `TypeSafeProperties`, `TypeSafeClient`, `TypeSafeApi`, `TypeSafeResponseErrorHandler`, exception classes, response records, `TypeSafeConstants`, `TypeSafeModels`
- Maven Central `resilience4j-{retry,framework-common,spring6,core}-2.3.0-sources.jar`: aspect order, `RetryConfigCustomizer`, interval-function precedence, `AspectJOnClasspathCondition`
- `spring-boot-dependencies-4.0.8.pom`, `spring-boot-http-client-4.0.8.jar` (javap): `spring-boot-starter-aspectj`, `ClientHttpRequestFactoryBuilder.reactor()`, `HttpClientSettings.withTimeouts`
- Project `./gradlew dependencies` (resolved versions; AspectJ absence)
- **Executed scratch probe** (Boot 4.0.8, Spring Cloud 2025.1.3, starter 0.1.0, Reactor Netty, with and without `spring-boot-starter-aspectj`): all `PROBE` lines quoted above
- `helm template` / `helm lint` (v4.3.0) on a modified copy of `helm/myfeeder`

### Secondary (MEDIUM confidence)
- https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment: coordinates, `api-key` property
- https://resilience4j.readme.io/docs/getting-started-3: AOP starter requirement, aspect order string

### Tertiary (LOW confidence)
- None used for load-bearing claims

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Resolved classpath inspected; starter and AspectJ behavior executed.
- Architecture: HIGH. Every pattern ran in the probe on the exact stack.
- Pitfalls: HIGH. The AspectJ finding was falsified both ways, with output pasted; timeout typing was observed.
- Live API behavior (model availability, error bodies): MEDIUM/ASSUMED. No key available.

**Research date:** 2026-09-22
**Valid until:** 2026-10-22 (stable libraries; re-check if the TypeSafe starter ships 0.1.x or Boot moves to 4.0.9+)
