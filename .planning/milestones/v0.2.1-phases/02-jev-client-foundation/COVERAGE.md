# API Coverage — TypeSafe Jev (typesafe-java-sdk 0.1.0 via spring-ai-starter-typesafe 0.1.0)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.

The capability surface was enumerated from the 0.1.0 SDK jar (`TypeSafeClient`, `TypeSafeClient.Builder`, `question.*`, `response.*`, `exception.*`) and the starter (`TypeSafeAutoConfiguration`, `TypeSafeProperties`). Phase 2 builds the client; Phases 3-4 call it through `JevApiClient.judge(...)`.

| capability | decision | reason |
|---|---|---|
| systemOne(Map state, questions) | INTEGRATE | |
| systemOne(String state, questions) | OPT-OUT | explicitly out of scope — D-04 requires an object top-level state |
| systemOne(List state, questions) | OPT-OUT | explicitly out of scope — D-04 requires an object top-level state |
| systemOne(JsonContent state, questions) | OPT-OUT | not needed — the Map overload covers the only state shape myfeeder sends |
| systemOne(SystemOneRequest) per-request model | OPT-OUT | not needed — the model is pinned client-wide (JEV-03) |
| systemOneAll batch (JevBatchOptions/JevBatchResult) | OPT-OUT | explicitly out of scope — locked out-of-bounds decision |
| listModels / ModelMetadata | OPT-OUT | not needed — model pinned to jev-1.13.0; the live smoke test proves it is served |
| question: Noul | INTEGRATE | |
| question: Score | INTEGRATE | |
| question: Choice | OPT-OUT | not needed — ranking asks one profile Score plus one Noul per topic |
| answer: NoulAnswer.value | INTEGRATE | |
| answer: ScoreAnswer value/maxLevel/confidence | INTEGRATE | |
| answer: ScoreAnswer legend/probabilities | OPT-OUT | not needed yet — D-02 requires value, maxLevel and confidence only |
| answer: ChoiceAnswer | OPT-OUT | not needed — no Choice questions are asked |
| answer: UnknownAnswer (future types) | OPT-OUT | not needed — ignored; requested names are still presence-checked |
| response: model id | INTEGRATE | |
| response: request id (x-typesafe-request-id) | INTEGRATE | |
| response: token usage | INTEGRATE | |
| answer presence/kind checks (noul/score/answer by name) | INTEGRATE | |
| typed error taxonomy (4xx/429 retryAfterMs/5xx/529/connection) | INTEGRATE | |
| builder: apiKey(Supplier) | INTEGRATE | |
| builder: apiKey(String) | OPT-OUT | explicitly out of scope — asserts hasText and would crash keyless startup (Pattern A) |
| builder: baseUrl/defaultModel/timeout | INTEGRATE | |
| builder: restClientBuilder (caller-owned transport) | INTEGRATE | |
| builder: headers(HttpHeaders) | OPT-OUT | not needed — User-Agent comes from the app's RestClientCustomizer |
| builder: typeSafeApi(TypeSafeApi) | OPT-OUT | not needed — test seam; tests use MockRestServiceServer and a JDK HttpServer stub |
| SDK RetryPolicy (spring.ai.typesafe.retry.*) | OPT-OUT | explicitly out of scope — max-retries 0; Resilience4j jev is the single retry layer |
| SDK env-var fallbacks (TYPESAFE_*) | OPT-OUT | explicitly out of scope — key, base URL and model are always set from spring.ai.typesafe.* |
| starter: TypeSafeProperties binding | INTEGRATE | |
| starter: auto-configured TypeSafeClient bean | OPT-OUT | explicitly out of scope — replaced by the app-owned bean (Pattern A) |
| starter: typeSafeEndpoints bean | OPT-OUT | not needed — internal path record with no consumer in myfeeder |
| Spring AI module (advisors, RAG rerank, tool index) | OPT-OUT | explicitly out of scope — REQUIREMENTS Out of Scope and locked out-of-bounds decision |
| model aliases (jev-latest, jev-preview) | OPT-OUT | explicitly out of scope — JEV-03 pins jev-1.13.0 |
