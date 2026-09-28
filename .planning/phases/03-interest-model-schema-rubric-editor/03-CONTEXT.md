# Phase 3: Interest Model, Schema & Rubric Editor - Context

**Gathered:** 2026-09-23
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers the interest model the user edits, the complete interest-scoring schema, and a one-call topic preview (INT-01, INT-02, INT-03, INT-04, INT-06):

- `V6__interest_scoring.sql`: the **whole** schema now (`interest_profile`, `interest_topic`, `article_score`, `article_topic_score`, `article_feedback`, `article_feedback_topic`). Later phases add no migrations.
- Profile and topic backend: entities, repositories, service and REST CRUD, with the limits (profile ≤2,000 chars, ≤25 topics, weight −50..+50, default +20).
- Pure-function **question builder** (5-level profile `Score`; one positively phrased `Noul` per topic) and **state builder** (feed name, title, summary; HTML stripped, truncated, content fallback). Phase 4's scorer reuses both unchanged.
- A slim `GET /api/interest/status` (see D-04). Phase 4 completes JEV-05 by adding counts.
- Topic preview endpoint: one Jev call via the Phase 2 `JevApiClient.judge(...)`.
- Frontend: an Interests dialog (profile editor with guidance, topic rubric editor, negation warning, preview, "not configured" and "cold start" notices).
- A closing **calibration spike**: run the question builder against 10–20 real articles with the live key, check score spread and confidence, iterate the wording.

**Not in this phase:**
- Scoring worker, executor, sweep, eligibility window, attempts, and the "Re-score unread" button (INT-05). All Phase 4.
- The eligible-unscored/failed counts on `/status` (JEV-05 completion, Phase 4).
- Blend CTE, Priority view, badges, "Why N?" breakdown (Phase 5).
- Thumbs voting UI/endpoints and learned-adjustment display (Phase 6). Only the schema for them ships here.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (locked by research and prior phases, not re-discussed)
- **Scoring units (research R1):** points on a 0–100 scale. `score = 100 × profile_match + Σ hinge(noul_t) × effective_weight_t`. Base topic weight is −50..+50, default +20, enforced by a V6 `CHECK (weight BETWEEN -50 AND 50)`. The ARCHITECTURE.md schema sketch's `-3..3 DOUBLE` weight is **superseded** by R1.
- **Hinge (R6):** `m_t = max(0, (noul_t − 0.5) × 2)`. Used by the preview's display (D-13) exactly as the Phase 5 blend will use it.
- **Learned adjustment (R2):** calculated at query time from feedback rows, never written into `interest_topic.weight`. Capped at ±20 and can't flip the base weight's sign. Phase 3 only ships the tables.
- **Statuses (R3):** `article_score.status` covers `SCORED`, `FAILED`, `SKIPPED`. The ARCHITECTURE.md sketch lists only the first two; R3 adds `SKIPPED`.
- **Cold start (C3):** "profile empty AND no topics" means not configured for scoring. No calls, no rows.
- **Versions:** `interest_profile.version` and `interest_topic.version` bump on **text** edits only (profile text / topic description), not on weight changes. `article_score.profile_version` and `article_topic_score.topic_version` record provenance.
- **Jev client (Phase 2 D-01..D-04):** `JevApiClient.judge(Map<String,?> state, Map<String, ? extends Question> questions)` returns `JevJudgment`. Callers build SDK `Noul`/`Score` questions and pass state as an ordered `LinkedHashMap` object (never bare numbers or booleans). Typed exceptions propagate (D-08): `JevNotConfiguredException`, `TypeSafe*Exception`, `CallNotPermittedException`.
- **Ordering and keys (research Pitfall 23):** question keys are `profile` and `topic_<id>`, and answers are read by key.

### Feedback schema (FDBK-06 support, ships in V6)
- **D-01:** Keep one vote row per article, `article_feedback(article_id PK → article ON DELETE CASCADE, vote SMALLINT CHECK (vote IN (-1,1)), created_at)`. Add a child table `article_feedback_topic(article_id → article_feedback ON DELETE CASCADE, topic_id → interest_topic ON DELETE CASCADE, PRIMARY KEY (article_id, topic_id))`. Child rows are written **only when the user narrows** a vote to specific topics. With no narrowing, a vote applies to all topics the article matched, as R2 already defines. This also works for votes cast before the article is scored. — **Reversibility:** one-way — changing the vote/pick storage later needs a V7 migration plus data conversion, and it contradicts the roadmap's "later phases add no migrations".
- **D-02:** `article_feedback` gets `topics_narrowed BOOLEAN NOT NULL DEFAULT false`. When it's true, **only** the child rows count, and zero remaining child rows means the vote penalizes nothing. This stops a narrowed vote from silently widening to all matched topics after its picked topic is deleted, since the child row cascades away. The Phase 6 learned CTE must honor the flag. — **Reversibility:** one-way — a column whose meaning the Phase 6 learned-adjustment query depends on; removing or re-purposing it needs a migration.
- **D-03:** The schema does **not** tie picks to `vote = -1`. Narrowing is allowed in either vote direction at the database/service level. The Phase 6 UI exposes narrowing on thumbs-down only, per FDBK-06. — **Reversibility:** reversible — adding a restriction later is a service-level check; no migration required.

### Status endpoint (INT-06)
- **D-04:** Phase 3 creates `GET /api/interest/status` returning at least `{configured, breakerState, coldStart}`. `configured` is true when the TypeSafe key has text (same rule as `JevApiClientImpl.requireConfigured()`). `breakerState` is read from `CircuitBreakerRegistry` instance `"jev"`. Phase 4 adds the eligible-unscored and failed counts to the **same** response to finish JEV-05. This corrects the roadmap note ("Phase 2 lays down /api/interest/status"): Phase 2 deferred it (`02-CONTEXT.md`), and no route exists today. The planner should fix that roadmap note. — **Reversibility:** costly — the Phase 5 Priority view, the Phase 4 counts and this phase's editor all consume the response shape; renaming fields touches every consumer.
- **D-05:** `coldStart` is computed **server-side** on `/status` from one predicate (profile text blank after trim AND zero topics). Phase 4's SCOR-06 scorer gate must reuse the same predicate, not reimplement it.
- **D-06:** With no key set, the Interests dialog stays **fully editable**. The profile and topics save normally, so the rubric can be prepared before rollout. A "not configured" notice sits above the editor. Only Preview is disabled, and it shows the reason.

### Editor placement and saving
- **D-07:** The editor is its **own "Interests" dialog**, wider than the 500px `SettingsDialog`. It opens from a new "Interests" row or section in `SettingsDialog`, and later from the Phase 5 Priority cold-start call to action. It follows the existing `dialog-overlay` / `dialog` pattern. The preview area shows the title of the article it's judging.
- **D-08:** **Saving is per item.** The profile has its own Save. Each topic row is added, updated and deleted individually through REST (topic ids stay stable, and version bumps only on description edits). Unsaved rows are visibly marked, and closing the dialog with unsaved changes warns first.
- **D-09:** The **negation warning (INT-03)** is inline and non-blocking. A debounced client-side phrase check (e.g. "not about", "not ", "no ", "except", "without", "anything but", "isn't", "nothing about") shows a warning under the description suggesting a positive description with a negative weight, e.g. *Looks negated. Try "about crypto" with a negative weight.* Saving is still allowed. There's no auto-rewrite.
- **D-10:** The **weight control** is a range slider (−50..+50, step 1) synced with a numeric input. New topics default to +20. The sign is color-coded with theme CSS variables (6 themes in `src/themes.ts`). Out-of-range values are rejected client-side and server-side (the CHECK constraint is the backstop).
- **D-11:** The **profile writing guidance (INT-01)** has three parts:
  - 3–4 short, always-visible tips, e.g. say what you want to read, be concrete, name technologies/people/projects, avoid negations (use negative-weight topics for dislikes)
  - a greyed example profile as the textarea placeholder
  - a live `N / 2,000` counter
  
  A note says profile and topic edits apply to newly arriving articles. The Re-score option arrives in Phase 4.

### Topic preview (INT-04)
- **D-12:** Preview sends **only the draft topic's `Noul`** (one question, key `topic_<id>` or a draft key) for the article selected in the reading pane (`uiStore.selectedArticleId`). It is built by the **same question builder and state builder** Phase 4's scorer uses, so the preview judges exactly as scoring will. It's one `judge(...)` call and **persists nothing**: no `article_score` / `article_topic_score` rows.
- **D-13:** The **result shows the scoring math**, e.g. *Match 82% → counts 64% × +20 = +12.8 pts*, using the R6 hinge and the row's current (draft) weight. Below 50% it reads *No match (contributes 0)*.
- **D-14:** Preview is available on **every row** (the new-topic draft and saved topics). It's disabled, with a tooltip explaining why, when no article is open, Jev isn't configured, or the breaker is open. A failed call shows inline next to the row and is never retried automatically.

### Claude's Discretion
- **Topic short name:** whether `interest_topic` has a separate short `name` besides `description`. The research sketch has both, and Phase 5/6 chips and toasts ("Rust +2") want a short label. The planner decides the shape: a required name, an optional name derived from the description, or description only.
- **Base weight column type** (INTEGER points vs DOUBLE PRECISION) as long as the CHECK is −50..50 and the UI steps by 1.
- **Endpoint paths and payloads** for profile/topic CRUD and preview (e.g. `/api/interest/profile`, `/api/interest/topics`, `POST /api/interest/preview {description, weight, articleId}`), plus mapping preview failures into `GlobalExceptionHandler` (e.g. not configured → 503, following the Raindrop precedent).
- **Question wording** (the 5 profile Score levels, the Noul "primarily about" phrasing with `whenTrue`/`whenFalse`) and **state limits** (truncation length ≤1,500 chars per research, the HTML-stripping approach). Start from research defaults and let the calibration spike iterate them.
- **Calibration spike mechanics:** how it runs (e.g. a gated live test or dev-only runner, like `JevLiveSmokeTest`'s `JEV_LIVE_SMOKE` gating), how many articles (10–20), and where findings are recorded (e.g. a phase artifact). It must never run in default `./gradlew test`.
- **Frontend layering:** `src/api/interest.ts`, `src/hooks/useInterest.ts`, the component split, and the store/query keys, following the existing one-file-per-domain convention.
- Server-side validation messages and status codes for limit violations (26th topic, out-of-range weight, profile >2,000 chars) via `IllegalArgumentException` → 400.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scoring model and schema
- `.planning/research/SUMMARY.md` §Reconciliations R1 (points/weights), R2 (feedback derived from `article_feedback`), R3 (statuses incl. `SKIPPED`), R5 (topics added later contribute 0), R6 (hinge). Also §"Other items to surface" (C3 cold start, content fallback) and §"Phase 2: Interest Model, Schema and Rubric Editor" (deliverables and calibration flag, renumbered as Phase 3 here) and §"Gaps to Address" (malformed-rubric 400s, topic-only users).
- `.planning/research/ARCHITECTURE.md` §"Data model: V6__interest_scoring.sql" (table sketch; **weight range superseded by R1, status set extended by R3, feedback extended by D-01/D-02**) and the learned-delta CTE (Pattern 5), which Phase 6 must extend for `topics_narrowed`.
- `.planning/research/PITFALLS.md` §Pitfall 7 (flat scores from weak wording), §Pitfall 8 (mixed versions), §Pitfall 12 (cold start), §Pitfall 14 (prompt injection through feed content, relevant to the state builder), §Pitfall 23 (question-map ordering and key churn).
- `.planning/research/STACK.md`: the `TypeSafeClient` / `Noul` / `Score` API surface and state rules (object state; bare numbers or booleans get a 422).

### Requirements and roadmap
- `.planning/REQUIREMENTS.md`: INT-01..04, INT-06 (this phase); the scoring-model header; FDBK-03/06/07 (their schema ships here); JEV-05 and SCOR-06 (Phase 4 consumers of D-04/D-05).
- `.planning/ROADMAP.md` §Phase 3: success criteria 1–5 and notes. **The note claiming Phase 2 built `/api/interest/status` is wrong. See D-04.**
- `.planning/PROJECT.md` §Key Decisions and §Constraints (Flyway V6 is next, Spring Data JDBC, Jackson 3).

### Prior phase
- `.planning/phases/02-jev-client-foundation/02-CONTEXT.md`: D-01..D-10 (client shape, typed exceptions, breaker `jev`), plus the note that `/api/interest/status` was deferred.
- `CLAUDE.md` (root): Spring Data JDBC rules (`@Query`, no derived queries), `GlobalExceptionHandler` mappings, the migration-test pattern (`V4StripRaindropApiTokenMigrationTest`), Zustand `preferencesStore` gotchas, the frontend conventions, and `npx tsc -b` for type-checking.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/org/bartram/myfeeder/integration/JevApiClient.java` / `JevApiClientImpl.java` / `JevJudgment.java` / `JevNotConfiguredException.java`: preview calls `judge(...)` directly. `requireConfigured()` defines "configured" for D-04.
- `src/main/java/org/bartram/myfeeder/config/TypeSafeConfig.java`: where the key and the app-owned client live, so the status endpoint reads configuredness consistently with it.
- `src/main/frontend/src/components/SettingsDialog.tsx`: `dialog-overlay` / `dialog` pattern, `<h3>` sections (Appearance, Reading, Raindrop.io). It gets the new Interests entry point (D-07).
- `src/main/frontend/src/stores/uiStore.ts`: `selectedArticleId`, the preview target (D-12).
- `src/main/frontend/src/themes.ts`: theme variables for the weight sign color (D-10).
- `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java`: existing exception → status mappings to reuse.

### Established Patterns
- Flyway migrations in `src/main/resources/db/migration/` (V1–V5 exist; this phase adds V6). The single-row/seeded approach for the singleton profile comes from the research sketch.
- Spring Data JDBC entities with `@Table`/`@Id` (`org.springframework.data.annotation`), `@Query` for custom SQL. Write-once assigned-PK tables use explicit SQL (`INSERT … ON CONFLICT`) rather than `save()` (research ARCHITECTURE note).
- Controllers throw typed exceptions; `GlobalExceptionHandler` maps them (`IllegalArgumentException` → 400, not-configured → 503 precedent from Raindrop).
- Frontend: thin fetch wrappers in `src/api/`, one TanStack Query hook file per domain in `src/hooks/`, components in `src/components/`, Vitest + RTL tests.

### Integration Points
- `SettingsDialog.tsx` → opens the new Interests dialog.
- `CircuitBreakerRegistry` bean → `breakerState` for `/api/interest/status`.
- Phase 4 scorer → imports the question/state builders and the cold-start predicate from this phase.
- Phase 5 Priority view → consumes `/api/interest/status` (`configured`, `coldStart`, later counts) and links to the Interests dialog.

</code_context>

<specifics>
## Specific Ideas

- The preview should teach the scoring model. Showing "Match 82% → counts 64% × +20 = +12.8 pts" makes the hinge and weights understandable before the Priority view exists.
- The rubric should be preparable before a key is set, so rollout (Phase 7) needs no further editor work.
- A narrowed thumbs-down must never silently turn into a broader penalty later (D-02).

</specifics>

<deferred>
## Deferred Ideas

- **Roadmap correction:** update ROADMAP.md §Phase 3 notes so they no longer claim Phase 2 built `/api/interest/status` (D-04). This is a planning-doc fix, not new scope.
- **Phase 6 note:** the learned-adjustment CTE must honor `article_feedback.topics_narrowed` and `article_feedback_topic` (D-01/D-02).
- **Phase 4 note:** the SCOR-06 gate reuses the cold-start predicate from D-05; `/status` gains the JEV-05 counts.

### Reviewed Todos (not folded)
- "Tune Raindrop resilience and fix CLAUDE.md AspectJ note" (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): unrelated to the interest model. The todo matcher found no match for this phase, so it stays pending.

</deferred>

---

*Phase: 03-interest-model-schema-rubric-editor*
*Context gathered: 2026-09-23*
