# Phase 2: Jev Client Foundation - Context

**Gathered:** 2026-09-22
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers an optional, resilient TypeSafe Jev client (JEV-01..04):

- The `org.springaicommunity:spring-ai-starter-typesafe:0.1.0` dependency.
- An app-owned `TypeSafeClient` bean.
- A dedicated `JevApiClient`/`JevApiClientImpl` bean with `@CircuitBreaker` + `@Retry` (Resilience4j is the only retry layer).
- The pinned `jev-1.13.0` model, with the model id exposed per response.
- `MYFEEDER_TYPESAFE_API_KEY` as an optional secret in `deploy.sh` and the Helm chart. A key-only change rolls the pod.

The app must start and serve feeds normally with the key absent, blank or set.

**Not in this phase:**
- Interest profile, topics or V6 schema (Phase 3)
- Scoring worker, executor, sweep and attempt counting (Phase 4)
- `GET /api/interest/status` (JEV-05, Phase 4)
- Any UI
- Production rollout with a live key (Phase 7)

</domain>

<decisions>
## Implementation Decisions

### Carried forward (locked by research and Phase 1, not re-discussed)
- **Pattern A:** keep the starter, but myfeeder defines its own `TypeSafeClient` bean. The starter's `@ConditionalOnMissingBean` then steps aside, and its blank-key `Assert` never runs. `spring.ai.typesafe.api-key: ${MYFEEDER_TYPESAFE_API_KEY:}`. Pass the key with the supplier form `.apiKey(() -> …)` so a blank key is legal at build time.
- **Single retry layer:** SDK `spring.ai.typesafe.retry.max-retries: 0`. Resilience4j `@Retry(name="jev")` owns retries and retries only transient failures: `TypeSafeRateLimitException` (429), `TypeSafeInternalServerException` (5xx incl. 529), and `TypeSafeApiConnectionException` (incl. timeout).
- **Breaker `ignore-exceptions`:** `JevNotConfiguredException`, `TypeSafeBadRequestException` (400), `TypeSafeUnprocessableEntityException` (422), `TypeSafeMissingAnswerException`, `TypeSafeAnswerTypeException`. Also list `JevNotConfiguredException` under retry `ignore-exceptions`.
- **Model:** pin `spring.ai.typesafe.model: jev-1.13.0`.
- **Raindrop pattern:** annotations go on the API-client bean. `requireConfigured()` checks `StringUtils.hasText(apiKey)` and throws `JevNotConfiguredException` before any HTTP call.
- **Test YAML:** mirror the `resilience4j.*.instances.jev` blocks into `src/test/resources/application.yaml`, which shadows main. The test YAML must not reference any real key env var. Pin `baseUrl` in tests.
- **Helm and deploy:** add `secrets.typesafeApiKey: ""`, secret key `myfeeder-typesafe-api-key`, and env `MYFEEDER_TYPESAFE_API_KEY`, mirroring Raindrop. `deploy.sh` uses `${MYFEEDER_TYPESAFE_API_KEY:-}` and prints a warning when it is empty. Add a `checksum/secret` pod-template annotation so a key-only change rolls the pod.
- **Out of bounds:** do not add `org.springaicommunity:typesafe-spring-ai`, do not use `systemOneAll`, do not add `@EnableAsync`, and do not set `spring.threads.virtual.enabled`.

### Client API shape
- **D-01:** `JevApiClient` is a **thin, generic pass-through**: roughly `JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions)`. It knows nothing about articles, profiles or topics. Phase 4's scorer builds the article state and question map, and Phase 3's topic preview reuses the same method. — **Reversibility:** reversible
- **D-02:** The client returns an **app-owned result record** (e.g. `JevJudgment`) carrying `model`, `requestId`, the noul values by name, the score answers by name (value, maxLevel, confidence at minimum), and token usage. SDK response types stop at the client boundary. `model` must be the response's model id, not the configured default (SC2 / JEV-03). — **Reversibility:** costly — Phases 3–5 consume this record, so reshaping it later touches the scorer, preview and storage code.
- **D-03:** On the input side, callers pass **SDK `Question` types** (`Noul`, `Score`) built with the SDK builders. Don't create an app-owned question wrapper.
- **D-04:** State is passed as an ordered `Map` (a `LinkedHashMap`, since `Map.of` randomizes wire order and rejects nulls), and the implementation drops null values. The top-level state must be an object, never a bare number or boolean, which the API rejects with 422.
- **D-05:** **Transport = Reactor Netty.** Clone the auto-configured `RestClient.Builder` so the User-Agent customizer and observations still apply. Give it a Jev-specific Reactor Netty request factory with a **5s connect and 5s read timeout**. Do **not** introduce `JdkClientHttpRequestFactory` (research Pattern A's code); that would add a second HTTP stack and contradict Phase 1 D-01. The global `spring.http.clients.*` 5s/30s settings still apply to feeds and Raindrop. The Jev read timeout is deliberately tighter, so a slow API trips the breaker quickly.

### Failure semantics
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Jev / TypeSafe integration
- `.planning/research/STACK.md`: starter behavior (blank-key crash), Pattern A code, the `TypeSafeClient` API surface, the exception hierarchy, SDK retry and timeout defaults, and pricing and rate limits. **Note:** its `JdkClientHttpRequestFactory` code is superseded by D-05 (Reactor Netty).
- `.planning/research/PITFALLS.md` §Pitfall 1 (blank key), §Pitfall 5 (stacked retries and breaker config), §Pitfall 15 (Helm/deploy secret), §Pitfall 16 (version skew), §Pitfall 17 (testing strategy).
- `.planning/research/SUMMARY.md` §"Other items to surface" (single retry layer, the breaker ignore-exception union, Pattern A supersedes conditional Helm rendering) and §"Phase 1: Jev Client Foundation" (deliverables and test list, renumbered as Phase 2 here).

### Requirements and roadmap
- `.planning/REQUIREMENTS.md`: JEV-01..04 (this phase) and JEV-05 (Phase 4, not here).
- `.planning/ROADMAP.md` §Phase 2: success criteria 1–4.
- `.planning/PROJECT.md` §Constraints and §Key Decisions ("App-owned TypeSafeClient bean; Resilience4j as the single retry layer").

### Prior phase
- `.planning/phases/01-dependency-upgrade/01-CONTEXT.md`: D-01 (Reactor Netty pinned) and D-02 (`spring.http.clients.*` timeouts).
- `CLAUDE.md` (root): the Resilience4j convention and the fallback rethrow pattern, the User-Agent and `RestClient` transport gotcha, and the deploy pipeline.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/org/bartram/myfeeder/integration/RaindropApiClientImpl.java`: the template for the client bean (constructor-injected `RestClient.Builder`, `requireConfigured()`, `@CircuitBreaker` + `@Retry`, fallback that rethrows the not-configured exception). Jev diverges in its fallback (D-08).
- `src/main/java/org/bartram/myfeeder/integration/RaindropNotConfiguredException.java`: the model for `JevNotConfiguredException`.
- `src/main/java/org/bartram/myfeeder/config/RestClientConfig.java`: the User-Agent `RestClientCustomizer`, auto-applied to the builder that the Jev client clones.
- `src/test/java/org/bartram/myfeeder/integration/RaindropApiClientImplTest.java`: the model for the client unit test.
- `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java`: the Reactor Netty transport guard from Phase 1. The Jev factory choice (D-05) should be consistent with it.

### Established Patterns
- `application.yaml` `resilience4j.circuitbreaker.instances.raindrop` / `retry.instances.raindrop`: add sibling `jev` instances, and duplicate them into `src/test/resources/application.yaml`.
- `deploy.sh`: `RAINDROP_TOKEN="${MYFEEDER_RAINDROP_API_TOKEN:-}"` + warning + `--set secrets.raindropApiToken=`. Copy this for TypeSafe.
- `helm/myfeeder/templates/app-secret.yaml` (the `myfeeder-raindrop-api-token` key), `app-deployment.yaml` (the `secretKeyRef` env block; there are currently **no** pod-template annotations), and `values.yaml` (`secrets.raindropApiToken: ""`).
- `GlobalExceptionHandler` maps `RaindropNotConfiguredException` to 503. Jev has no controller in this phase, so no mapping is needed yet.

### Integration Points
- `build.gradle.kts`: add `extra["typesafeVersion"] = "0.1.0"` and an explicit-version `implementation(...)`. The starter is not in any BOM.
- `MyfeederApplication`: no change. Scheduling stays as is, and there is no `@EnableAsync`.

</code_context>

<specifics>
## Specific Ideas

- The client should be reusable as-is by Phase 3's "preview topic against open article" (one call) and Phase 4's scorer. Keep it generic.
- A bad key should pause scoring by itself (breaker opens) rather than quietly burning every article's attempts.

</specifics>

<deferred>
## Deferred Ideas

- **Note for Phase 4:** treat 401/403 and `CallNotPermittedException` as transient (don't consume the article's 3 attempts), per D-06.
- **Possible for Phase 4 or 7:** preserve an existing TypeSafe key across `helm upgrade` runs that omit the variable (Helm `lookup`). Raindrop has the same gap today.
- **Phase 7:** re-tune breaker, retry and 429 cap values against real launch-backfill traffic.

</deferred>

---

*Phase: 02-jev-client-foundation*
*Context gathered: 2026-09-22*
