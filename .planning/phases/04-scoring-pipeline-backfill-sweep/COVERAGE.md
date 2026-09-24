# API Coverage — TypeSafe Jev (consumed through the Phase 2 `JevApiClient`)

> Full coverage by default. Opt-outs are explicit, reasoned decisions.

This phase adds no new external client. It consumes the TypeSafe Jev API through the existing `JevApiClient.judge(...)` (see `../02-jev-client-foundation/COVERAGE.md` for the client-construction surface and `../03-interest-model-schema-rubric-editor/COVERAGE.md` for the preview). The rows below re-decide, from the full baseline, each capability this phase's callers use: the background scorer (plan 04-03), the resilience hardening (04-01), the sweep (04-05) and the status counts (04-06).

| capability | decision | reason |
|---|---|---|
| judge → systemOne(Map state, profile Score + topic Nouls), one call per article | INTEGRATE | |
| question: Noul with map instructions and whenTrue/whenFalse criteria | INTEGRATE | |
| question: Score with map instructions and five situation levels | INTEGRATE | |
| question: Choice | OPT-OUT | not needed — ranking asks one profile Score plus one Noul per topic; D-15 now rejects Choice before the HTTP call so no billed answer is dropped |
| answer: NoulAnswer.value (stored per topic in article_topic_score) | INTEGRATE | |
| answer: ScoreAnswer value/maxLevel/confidence (stored in article_score) | INTEGRATE | |
| answer: ScoreAnswer legend/probabilities | OPT-OUT | not needed — the blend (Phase 5) uses value / maxLevel only; maxLevel falls back to the builder's level count when the legend is absent |
| response: model id (stored with every score, JEV-03) | INTEGRATE | |
| response: request id (stored with every score and in FAILED last_error text) | INTEGRATE | |
| response: token usage | OPT-OUT | not needed yet — call volume and cost are observed during the Phase 7 launch backfill |
| typed errors → permanent (FAILED, attempt used) vs transient (no row) | INTEGRATE | |
| circuit-breaker state read ("jev") for the sweep gate and /api/interest/status | INTEGRATE | |
| JevApiClient.isConfigured check (listener, scorer, sweep, Re-score guard) | INTEGRATE | |
| batch systemOneAll | OPT-OUT | explicitly out of scope — one call per article on a single scoring thread (D-09); per-article failure classification needs per-call outcomes |
| per-request model, listModels, model aliases | OPT-OUT | explicitly out of scope — JEV-03 pins jev-1.13.0 client-wide |
| String/List/JsonContent state overloads | OPT-OUT | explicitly out of scope — Phase 2 D-04 requires an object top-level state |
| SDK retry policy / extra caller retries | OPT-OUT | explicitly out of scope — Resilience4j jev is the single retry layer; the scorer never retries on its own |
| Spring AI module (advisors, RAG rerank, tool index) | OPT-OUT | explicitly out of scope — REQUIREMENTS Out of Scope |
