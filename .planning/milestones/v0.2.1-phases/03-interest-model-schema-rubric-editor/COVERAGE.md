# API Coverage — TypeSafe Jev (consumed through the Phase 2 `JevApiClient`)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.

This phase adds no new external client. It consumes the TypeSafe Jev API through the Phase 2 `JevApiClient.judge(...)` (see `../02-jev-client-foundation/COVERAGE.md` for the client-construction surface). The rows below re-decide, from the full baseline, each capability this phase's callers can use: the topic preview (plan 03-04), the status endpoint (03-04), the question/state builders (03-02) and the calibration spike (03-08).

| capability | decision | reason |
|---|---|---|
| judge → systemOne(Map state, one Noul): topic preview | INTEGRATE | |
| judge → systemOne(Map state, profile Score + topic Nouls): full rubric | INTEGRATE | |
| question: Noul with map instructions and whenTrue/whenFalse criteria | INTEGRATE | |
| question: Score with map instructions and five situation levels | INTEGRATE | |
| question: Choice | OPT-OUT | not needed — ranking asks one profile Score plus one Noul per topic |
| answer: NoulAnswer.value (preview result) | INTEGRATE | |
| answer: ScoreAnswer value/maxLevel/confidence (calibration statistics) | INTEGRATE | |
| answer: ScoreAnswer legend/probabilities | OPT-OUT | not needed yet — calibration and scoring use value, maxLevel and confidence only |
| response: model id (preview response, calibration report) | INTEGRATE | |
| response: request id | OPT-OUT | not needed — the preview persists nothing; JevApiClientImpl already logs it for 401/403 |
| response: token usage | OPT-OUT | not needed yet — about 20 calibration calls cost well under a cent; call volume is observed in Phase 7 |
| typed error taxonomy mapped to fixed-text ProblemDetails (503/422) | INTEGRATE | |
| circuit-breaker state read ("jev") for /api/interest/status | INTEGRATE | |
| configured check (JevApiClient.isConfigured) | INTEGRATE | |
| batch systemOneAll | OPT-OUT | explicitly out of scope — inherited locked decision from Phase 2 |
| per-request model, listModels, model aliases | OPT-OUT | explicitly out of scope — JEV-03 pins jev-1.13.0 client-wide |
| String/List/JsonContent state overloads | OPT-OUT | explicitly out of scope — D-04 (Phase 2) requires an object top-level state |
| SDK retry policy / extra caller retries | OPT-OUT | explicitly out of scope — Resilience4j jev is the single retry layer and the preview never retries on its own (D-14) |
| Spring AI module (advisors, RAG rerank, tool index) | OPT-OUT | explicitly out of scope — REQUIREMENTS Out of Scope |
