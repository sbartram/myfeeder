# Phase 3: Interest Model, Schema & Rubric Editor - Research

**Researched:** 2026-09-23
**Domain:** Spring Data JDBC schema + CRUD, TypeSafe Jev question/state construction, React/TanStack Query editor dialog
**Confidence:** HIGH for the in-repo integration facts and the SDK API (read from source this session). MEDIUM for question wording (vendor guidance is cited, but the calibration spike still has to confirm it).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (locked by research and prior phases, not re-discussed)
- **Scoring units (research R1):** points on a 0–100 scale. `score = 100 × profile_match + Σ hinge(noul_t) × effective_weight_t`. Base topic weight is −50..+50, default +20, enforced by a V6 `CHECK (weight BETWEEN -50 AND 50)`. The ARCHITECTURE.md schema sketch's `-3..3 DOUBLE` weight is **superseded** by R1.
- **Hinge (R6):** `m_t = max(0, (noul_t − 0.5) × 2)`. Used by the preview's display (D-13) exactly as the Phase 5 blend will use it.
- **Learned adjustment (R2):** calculated at query time from feedback rows, never written into `interest_topic.weight`. Capped at ±20 and can't flip the base weight's sign. Phase 3 only ships the tables.
- **Statuses (R3):** `article_score.status` covers `SCORED`, `FAILED`, `SKIPPED`. The ARCHITECTURE.md sketch lists only the first two; R3 adds `SKIPPED`.
- **Cold start (C3):** "profile empty AND no topics" means not configured for scoring. No calls, no rows.
- **Versions:** `interest_profile.version` and `interest_topic.version` bump on **text** edits only (profile text / topic description), not on weight changes. `article_score.profile_version` and `article_topic_score.topic_version` record provenance.
- **Jev client (Phase 2 D-01..D-04):** `JevApiClient.judge(Map<String,?> state, Map<String, ? extends Question> questions)` returns `JevJudgment`. Callers build SDK `Noul`/`Score` questions and pass state as an ordered `LinkedHashMap` object (never bare numbers or booleans). Typed exceptions propagate (D-08): `JevNotConfiguredException`, `TypeSafe*Exception`, `CallNotPermittedException`.
- **Ordering and keys (research Pitfall 23):** question keys are `profile` and `topic_<id>`, and answers are read by key.

#### Feedback schema (FDBK-06 support, ships in V6)
- **D-01:** Keep one vote row per article, `article_feedback(article_id PK → article ON DELETE CASCADE, vote SMALLINT CHECK (vote IN (-1,1)), created_at)`. Add a child table `article_feedback_topic(article_id → article_feedback ON DELETE CASCADE, topic_id → interest_topic ON DELETE CASCADE, PRIMARY KEY (article_id, topic_id))`. Child rows are written **only when the user narrows** a vote to specific topics. With no narrowing, a vote applies to all topics the article matched, as R2 already defines. This also works for votes cast before the article is scored. — **Reversibility:** one-way — changing the vote/pick storage later needs a V7 migration plus data conversion, and it contradicts the roadmap's "later phases add no migrations".
- **D-02:** `article_feedback` gets `topics_narrowed BOOLEAN NOT NULL DEFAULT false`. When it's true, **only** the child rows count, and zero remaining child rows means the vote penalizes nothing. This stops a narrowed vote from silently widening to all matched topics after its picked topic is deleted, since the child row cascades away. The Phase 6 learned CTE must honor the flag. — **Reversibility:** one-way — a column whose meaning the Phase 6 learned-adjustment query depends on; removing or re-purposing it needs a migration.
- **D-03:** The schema does **not** tie picks to `vote = -1`. Narrowing is allowed in either vote direction at the database/service level. The Phase 6 UI exposes narrowing on thumbs-down only, per FDBK-06. — **Reversibility:** reversible — adding a restriction later is a service-level check; no migration required.

#### Status endpoint (INT-06)
- **D-04:** Phase 3 creates `GET /api/interest/status` returning at least `{configured, breakerState, coldStart}`. `configured` is true when the TypeSafe key has text (same rule as `JevApiClientImpl.requireConfigured()`). `breakerState` is read from `CircuitBreakerRegistry` instance `"jev"`. Phase 4 adds the eligible-unscored and failed counts to the **same** response to finish JEV-05. This corrects the roadmap note ("Phase 2 lays down /api/interest/status"): Phase 2 deferred it (`02-CONTEXT.md`), and no route exists today. The planner should fix that roadmap note. — **Reversibility:** costly — the Phase 5 Priority view, the Phase 4 counts and this phase's editor all consume the response shape; renaming fields touches every consumer.
- **D-05:** `coldStart` is computed **server-side** on `/status` from one predicate (profile text blank after trim AND zero topics). Phase 4's SCOR-06 scorer gate must reuse the same predicate, not reimplement it.
- **D-06:** With no key set, the Interests dialog stays **fully editable**. The profile and topics save normally, so the rubric can be prepared before rollout. A "not configured" notice sits above the editor. Only Preview is disabled, and it shows the reason.

#### Editor placement and saving
- **D-07:** The editor is its **own "Interests" dialog**, wider than the 500px `SettingsDialog`. It opens from a new "Interests" row or section in `SettingsDialog`, and later from the Phase 5 Priority cold-start call to action. It follows the existing `dialog-overlay` / `dialog` pattern. The preview area shows the title of the article it's judging.
- **D-08:** **Saving is per item.** The profile has its own Save. Each topic row is added, updated and deleted individually through REST (topic ids stay stable, and version bumps only on description edits). Unsaved rows are visibly marked, and closing the dialog with unsaved changes warns first.
- **D-09:** The **negation warning (INT-03)** is inline and non-blocking. A debounced client-side phrase check (e.g. "not about", "not ", "no ", "except", "without", "anything but", "isn't", "nothing about") shows a warning under the description suggesting a positive description with a negative weight, e.g. *Looks negated. Try "about crypto" with a negative weight.* Saving is still allowed. There's no auto-rewrite.
- **D-10:** The **weight control** is a range slider (−50..+50, step 1) synced with a numeric input. New topics default to +20. The sign is color-coded with theme CSS variables (6 themes in `src/themes.ts`). Out-of-range values are rejected client-side and server-side (the CHECK constraint is the backstop).
- **D-11:** The **profile writing guidance (INT-01)** has three parts:
  - 3–4 short, always-visible tips, e.g. say what you want to read, be concrete, name technologies/people/projects, avoid negations (use negative-weight topics for dislikes)
  - a greyed example profile as the textarea placeholder
  - a live `N / 2,000` counter

  A note says profile and topic edits apply to newly arriving articles. The Re-score option arrives in Phase 4.

#### Topic preview (INT-04)
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

### Deferred Ideas (OUT OF SCOPE)
- **Roadmap correction:** update ROADMAP.md §Phase 3 notes so they no longer claim Phase 2 built `/api/interest/status` (D-04). This is a planning-doc fix, not new scope.
- **Phase 6 note:** the learned-adjustment CTE must honor `article_feedback.topics_narrowed` and `article_feedback_topic` (D-01/D-02).
- **Phase 4 note:** the SCOR-06 gate reuses the cold-start predicate from D-05; `/status` gains the JEV-05 counts.

#### Reviewed Todos (not folded)
- "Tune Raindrop resilience and fix CLAUDE.md AspectJ note" (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): unrelated to the interest model. The todo matcher found no match for this phase, so it stays pending.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INT-01 | User can write and edit a free-text interest profile (≤2,000 chars) with writing guidance shown in the editor | V6 singleton `interest_profile` row (§Schema); `InterestService.updateProfile` length check using UTF-16 `String.length()` to match the browser `maxLength` (Pitfall 5); UI copy is fixed in UI-SPEC |
| INT-02 | User can add, edit and delete topics (≤25), each with a positively-phrased description and a signed weight in −50..+50 (default +20) | V6 `interest_topic` with `weight INTEGER … CHECK (weight BETWEEN -50 AND 50)`; the service enforces the 25-topic cap and the weight range and throws `IllegalArgumentException` → 400; explicit timestamps/version on insert (Pitfall 2); do not use `@Version` (Pitfall 1) |
| INT-03 | Editor warns when a topic description is negated and suggests a negative weight | Client-side debounced regex from the UI-SPEC word list, with the `’` → `'` normalization (Pitfall 9). The backend does not reject negated text |
| INT-04 | User can preview a topic against the currently open article (one Jev call) before saving it | `InterestQuestions.topic(description)` + `ArticleStateBuilder.build(feedTitle, article)` → `JevApiClient.judge` with one question; new Jev/TypeSafe exception handlers in `GlobalExceptionHandler` (fixed-text details); frontend `ApiError` with `status`/`title` so the UI can pick the right copy (Pitfall 6) |
| INT-06 | Settings show a "not configured" notice when no API key is set, and a "cold start" prompt when the profile is empty and there are no topics | `GET /api/interest/status` → `{configured, breakerState, coldStart}`: `JevApiClient.isConfigured()` (new), `CircuitBreakerRegistry.circuitBreaker("jev").getState().name()`, and one `InterestService.isColdStart()` predicate |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Directives from `./CLAUDE.md`, `./.claude/CLAUDE.md` and the user's global CLAUDE.md that bind this phase:

- **Gradle only** (never Maven). Gradle Kotlin DSL.
- **Spring Data JDBC, not JPA.** No `@Entity`, no lazy loading. Use `@Query` for custom queries. (Note: `FolderRepository` does use a derived method, `findAllByOrderByDisplayOrderAsc`, so derived queries do work in this Spring Data version. The CLAUDE.md directive still says `@Query`, so follow it for new code.) `[VERIFIED: src/main/java/org/bartram/myfeeder/repository/FolderRepository.java:8]`
- **Annotation imports:** CLAUDE.md says `@Table`/`@Id` come from `org.springframework.data.annotation`. The code actually imports `import org.springframework.data.annotation.Id;` and `import org.springframework.data.relational.core.mapping.Table;`. Copy the real imports. `[VERIFIED: src/main/java/org/bartram/myfeeder/model/Folder.java:4-5]`
- **Jackson 3:** databind is `tools.jackson.databind.*`, and annotations stay in `com.fasterxml.jackson.annotation.*`.
- **Exceptions → status via `GlobalExceptionHandler`:** throw the right type from services, with no try/catch in controllers. `IllegalArgumentException` → 400, `NotFoundException` → 404, `IllegalStateException` → 409 (so never use ISE for validation).
- **Resilience4j annotations stay on the API-client bean** (`JevApiClientImpl`). Business validation runs outside the breaker, in the calling service.
- **Test patterns:** Mockito unit tests (`@ExtendWith(MockitoExtension.class)`); `@WebMvcTest` + `@MockitoBean` for controllers; `@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)` for repositories and migrations; the test `application.yaml` must keep a dummy `spring.ai.anthropic.api-key`.
- **Frontend:** thin fetch wrappers in `src/api/`, one TanStack Query hook file per domain in `src/hooks/`, components in `src/components/`, Vitest + RTL. Type-check with `npx tsc -b` (plain `tsc --noEmit` passes falsely).
- **`vi.mock` of a store is full replacement.** Adding exports to a mocked module breaks every consumer's mock. Prefer `vi.mock(path, async (importOriginal) => ({...await importOriginal(), …}))` for new tests.
- **Zustand persist gotcha:** a new `preferencesStore` field's default only applies to fresh installs. This phase should not add persisted preferences; keep dialog state local.
- **Git:** work on a feature branch (currently `sbartram/main`); merges to main use `--no-ff`; subagents never check out detached HEAD. Push only when asked.
- **GSD workflow:** edits go through `/gsd-execute-phase`.
- **Surgical changes:** touch only what the phase needs. Mention unrelated dead code, don't delete it.
- **OpenWiki pages are generated.** Do not hand-edit `openwiki/`.

## Summary

This phase is mostly plain Spring Data JDBC CRUD plus one React dialog. The risk sits in three places that are easy to get subtly wrong: (1) the V6 schema is **one-way**, so every column Phases 4–6 need must be right now; (2) the pure question and state builders are shared verbatim with the Phase 4 scorer, so their wording and text normalization define ranking quality; (3) the frontend has two existing global behaviors (the `MutationCache` toast-on-error, and `raiseIfBad` throwing a status-less `Error`) that collide with the UI-SPEC's inline, status-specific error copy.

The TypeSafe SDK surface was read from the `typesafe-java-sdk-0.1.0-sources.jar` on Maven Central this session. `Noul.builder().instructions(..).whenTrue(..).whenFalse(..).build()` and `Score.builder().instructions(..).level(..)…build()` both accept a `String` or a `Map` for the instructions. `Score.maxLevel()` is `criteria.size() - 1`. Both are records wrapping `JsonContent` records, so value equality works in unit tests. The vendor docs recommend object-valued instructions that reference state fields in backticks, level descriptions that "describe situations, not degrees", and positive yes-means-high Noul phrasing. The builders below follow all three.

Phase 2 shipped `JevApiClient.judge(...)` but **no `isConfigured()`**. No route exists yet under `/api/interest`, and no exception handler exists yet for Jev/TypeSafe exceptions: unhandled, they would surface as a generic 500. jsoup is already on the compile classpath (1.11.2, transitive via Readability4J, and used directly by `ArticleExtractionService`), so the STATE.md blocker "verify jsoup" is resolved. That version predates the fix for CVE-2021-37714 (a parser DoS), so the state builder must cap its raw input before parsing.

**Primary recommendation:** Build it in this order: V6 migration plus its test → entities, repositories and `InterestService` (limits, versions, cold-start predicate) → pure `InterestQuestions` + `ArticleStateBuilder` (TDD) → `JevApiClient.isConfigured()` + status and preview services + controller + exception handlers → frontend `ApiError` + api/hooks + `InterestsDialog` → a gated calibration spike whose findings are recorded in `03-CALIBRATION.md`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Profile/topic persistence, limits (≤2,000 chars, ≤25 topics, weight −50..50), version bumps | API / Backend (`InterestService`) | Database (weight `CHECK` backstop) | Limits are product rules and must hold for any client; the DB enforces only the locked weight range |
| Cold-start predicate | API / Backend (`InterestService.isColdStart()`) | — | D-05: one server-side predicate, reused by the Phase 4 SCOR-06 gate; the client never computes it |
| Jev configured / breaker state | API / Backend (`InterestStatusService`) | — | The key and the breaker live in the JVM only |
| Question + state building | API / Backend (pure functions) | — | Shared with the Phase 4 scorer (D-12); must be identical to what scoring sends |
| Topic preview Jev call | API / Backend (`InterestPreviewService` → `JevApiClient`) | — | The key never leaves the server; one call, nothing persisted |
| Preview math display (hinge × weight, live recompute on weight change) | Browser / Client | — | D-13 plus UI-SPEC "live recompute": the server returns the raw `noul`, and the client applies `max(0,(noul−0.5)×2) × weight` |
| Negation warning | Browser / Client | — | D-09: debounced, non-blocking, advice only |
| Draft/dirty row state, unsaved-changes guard | Browser / Client (component-local state) | — | Per-item saves (D-08); must not be clobbered by query refetches |
| Preview target (open article) | Browser / Client (`uiStore.selectedArticleId` + `useArticle`) | API (`GET /api/articles/{id}`, already exists) | Reuses the reading pane's cached article query |

## Standard Stack

### Core (all already on the classpath; nothing new is installed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JDBC (Boot starter) | Boot 4.0.8-managed | `InterestProfile`/`InterestTopic` entities and repositories | Project convention `[VERIFIED: build.gradle.kts:35]` |
| Flyway | Boot-managed | `V6__interest_scoring.sql` | V1–V5 exist; V6 is next `[VERIFIED: ls src/main/resources/db/migration/]` |
| `org.springaicommunity:typesafe-java-sdk` | 0.1.0 (via `spring-ai-starter-typesafe:0.1.0`) | `Noul`, `Score`, `Question` types built by `InterestQuestions` | Phase 2 client contract `[VERIFIED: build.gradle.kts:30,46; source jar read]` |
| jsoup | 1.11.2 (transitive via `net.dankito.readability4j:readability4j:1.0.8`) | HTML → text in `ArticleStateBuilder` (`Jsoup.parse(html).text()` decodes entities and collapses whitespace) | Already on the compile classpath and imported directly by `ArticleExtractionService` `[VERIFIED: ./gradlew dependencies --configuration compileClasspath → "org.jsoup:jsoup:1.11.2"; ArticleExtractionService.java:8]` |
| Resilience4j `CircuitBreakerRegistry` | 2.3.0 | `breakerState` for `/status` | `State` enum values are `DISABLED, METRICS_ONLY, CLOSED, OPEN, FORCED_OPEN, HALF_OPEN` `[VERIFIED: javap resilience4j-circuitbreaker-2.3.0.jar]` |
| TanStack Query | ^5.103.2 | Profile/topics/status queries, save/preview mutations | Project convention `[VERIFIED: src/main/frontend/package.json]` |
| Zustand `useUIStore` | ^5.0.15 | Reads `selectedArticleId: number \| null` | `[VERIFIED: src/main/frontend/src/stores/uiStore.ts:9]` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| jsoup `text()` | Regex tag stripping | Regex misses entities, comments, CDATA and `<script>` bodies. jsoup is already present. **Don't hand-roll.** |
| Spring Data JDBC entity for the singleton profile | `JdbcTemplate` upsert | The row is seeded, so `save()` with `id = 1` is a plain UPDATE. The entity is simpler and matches house style. |
| Upgrading jsoup explicitly | Leave 1.11.2 and cap input | An upgrade changes Readability4J's jsoup, and compatibility with 1.23.x is untested `[ASSUMED]`. Out of scope; cap the input instead (Pitfall 4) and raise it as an open question. |

**Installation:** none. `npm install` / Gradle dependency changes: **none** in this phase.

## Package Legitimacy Audit

No external packages are installed in this phase. The backend uses only libraries already resolved on the classpath (verified via `./gradlew dependencies`). The frontend uses only the existing `package.json` (UI-SPEC §Registry Safety: "No new npm dependencies").

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| *(none new)* | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
Browser (InterestsDialog)                         Spring Boot (MVC)                                     Postgres / Jev
─────────────────────────                         ──────────────────                                    ──────────────
open dialog ──GET /api/interest/status──────────▶ InterestController ─▶ InterestStatusService
                                                     configured  ◀─ JevApiClient.isConfigured()
                                                     breakerState◀─ CircuitBreakerRegistry("jev").getState()
                                                     coldStart   ◀─ InterestService.isColdStart() ──SELECT──▶ interest_profile / interest_topic
            ◀──{configured,breakerState,coldStart}─┘
            ──GET /api/interest/profile, /topics──▶ InterestService ─────────────────SELECT──────────▶ interest_profile, interest_topic
Save profile ─PUT /api/interest/profile──────────▶ validate ≤2000 → bump version iff text changed ─UPDATE─▶ interest_profile (id=1)
Save topic ──POST|PUT /api/interest/topics[/id]──▶ validate (≤25, −50..50, name/desc) → bump iff desc ─▶ interest_topic (CHECK weight)
Delete topic ─DELETE /api/interest/topics/{id}───▶ deleteById ──────────────────────────────────────▶ CASCADE → article_topic_score,
                                                                                                                 article_feedback_topic
Preview ─POST /api/interest/preview {articleId,──▶ InterestPreviewService
          description, topicId?}                    1. validate description (outside breaker)
   (selectedArticleId from uiStore)                 2. ArticleRepository/FeedRepository ─SELECT──────▶ article, feed
                                                    3. ArticleStateBuilder.build(feedTitle, article)  (pure: strip, truncate, fallback)
                                                    4. InterestQuestions.topic(description)           (pure: one Noul)
                                                    5. JevApiClient.judge(state, {topic_x: noul}) ──HTTPS (CB+Retry)──▶ TypeSafe Jev
                                                    6. return {noul, model}   (persists nothing)
            ◀──{noul} or ProblemDetail(503/422/400/404)──┘  (GlobalExceptionHandler maps Jev/TypeSafe exceptions)
client: m = max(0,(noul−0.5)×2); pts = m × draftWeight  → "Match 82% → counts 64% × +20 = +12.8 pts"
```

### Recommended Project Structure (new files; layer-first, as the codebase does today)

```
src/main/resources/db/migration/V6__interest_scoring.sql
src/main/java/org/bartram/myfeeder/
├── model/InterestProfile.java, InterestTopic.java
├── repository/InterestProfileRepository.java, InterestTopicRepository.java
├── service/InterestService.java           # profile + topic CRUD, limits, versions, isColdStart()
├── service/InterestQuestions.java         # PURE: profile Score, topic Noul, keys (reused by Phase 4)
├── service/ArticleStateBuilder.java       # PURE: {feed,title,summary} (reused by Phase 4)
├── service/InterestStatusService.java     # configured / breakerState / coldStart (Phase 4 adds counts)
├── service/InterestPreviewService.java    # one judge() call, nothing persisted
├── controller/InterestController.java     # /api/interest/{status,profile,topics,preview}
├── controller/ProfileUpdateRequest.java, TopicRequest.java, TopicPreviewRequest.java (records)
├── controller/InterestStatus.java, TopicPreviewResponse.java (records)
├── controller/GlobalExceptionHandler.java # + Jev/TypeSafe handlers
└── integration/JevApiClient.java / JevApiClientImpl.java  # + boolean isConfigured()
src/main/frontend/src/
├── api/client.ts            # raiseIfBad throws ApiError(status, title) — message unchanged
├── api/interest.ts          # interestApi.{getStatus,getProfile,saveProfile,listTopics,createTopic,updateTopic,deleteTopic,preview}
├── hooks/useInterest.ts     # useInterestStatus/useInterestProfile/useInterestTopics + mutations
├── components/InterestsDialog.tsx (+ .test.tsx)   # shell + unsaved guard; body mounts only when open
├── components/TopicRow.tsx, WeightControl.tsx, TopicPreviewResult.tsx, InterestNotices.tsx (split at planner's discretion)
├── utils/interest.ts (+ .test.ts)  # hinge(), formatSigned(), isNegated() — pure, unit-tested
└── types/index.ts           # InterestTopic, InterestProfile, InterestStatus types
src/test/java/org/bartram/myfeeder/
├── repository/V6InterestScoringMigrationTest.java, InterestTopicRepositoryTest.java
├── service/InterestServiceTest.java, InterestQuestionsTest.java, ArticleStateBuilderTest.java,
│   InterestPreviewServiceTest.java, InterestStatusServiceTest.java
├── controller/InterestControllerTest.java
└── integration/InterestCalibrationSpikeTest.java   # gated, never in default ./gradlew test
```

### Pattern 1: V6 schema (whole milestone, one migration)

Corrections to the ARCHITECTURE.md sketch: R1 weights, R3 `SKIPPED`, D-01/D-02 feedback tables, and **one new recommendation**: `article_topic_score` references `article_score`, not `article` (see Pitfall 3).

```sql
-- V6__interest_scoring.sql: the complete interest-scoring schema. Later phases add no migrations.

-- Singleton profile (single-user app), seeded so it is always UPDATE-able via save().
CREATE TABLE interest_profile (
    id           INTEGER     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    profile_text TEXT        NOT NULL DEFAULT '',
    version      INTEGER     NOT NULL DEFAULT 1,   -- bumped only when profile_text changes
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO interest_profile (id) VALUES (1);

CREATE TABLE interest_topic (
    id          BIGSERIAL   PRIMARY KEY,
    name        TEXT        NOT NULL,              -- short label for Phase 5/6 chips/toasts; not sent to Jev
    description TEXT        NOT NULL,              -- goes into the Noul instructions
    weight      INTEGER     NOT NULL DEFAULT 20 CHECK (weight BETWEEN -50 AND 50),  -- R1 points
    version     INTEGER     NOT NULL DEFAULT 1,    -- bumped only when description changes
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One row per judged article. SCORED is write-once; FAILED counts attempts; SKIPPED is terminal (R3).
CREATE TABLE article_score (
    article_id         BIGINT           PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    status             TEXT             NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED')),
    profile_score      DOUBLE PRECISION,           -- raw Score value in [0, profile_max_level]; NULL when no profile question
    profile_max_level  INTEGER,                    -- levels - 1 at scoring time
    profile_confidence DOUBLE PRECISION,
    profile_version    INTEGER,
    model              TEXT,                       -- JevJudgment.model() (response body, not config)
    request_id         TEXT,
    attempts           INTEGER          NOT NULL DEFAULT 1,
    last_error         TEXT,
    scored_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW()   -- time of the last write (score or failed attempt)
);

-- Child of article_score, so deleting a score row (Phase 4 "Re-score unread") also removes its nouls.
CREATE TABLE article_topic_score (
    article_id    BIGINT           NOT NULL REFERENCES article_score(article_id) ON DELETE CASCADE,
    topic_id      BIGINT           NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,
    noul          DOUBLE PRECISION NOT NULL CHECK (noul BETWEEN 0 AND 1),
    topic_version INTEGER          NOT NULL,
    PRIMARY KEY (article_id, topic_id)
);
CREATE INDEX idx_article_topic_score_topic ON article_topic_score(topic_id);

-- D-01/D-02: one vote per article, optional narrowing to picked topics.
CREATE TABLE article_feedback (
    article_id      BIGINT      PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    vote            SMALLINT    NOT NULL CHECK (vote IN (-1, 1)),
    topics_narrowed BOOLEAN     NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE article_feedback_topic (
    article_id BIGINT NOT NULL REFERENCES article_feedback(article_id) ON DELETE CASCADE,
    topic_id   BIGINT NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, topic_id)
);
CREATE INDEX idx_article_feedback_topic_topic ON article_feedback_topic(topic_id);
```

Design notes for the planner:
- **Length limits are not DB CHECKs.** The profile (2,000), name and description caps are product rules that may be retuned, and "no later migrations" makes a too-tight CHECK costly. Only the locked weight range goes in the DB.
- **`name` column:** recommended `NOT NULL` and required in the UI (1–40 chars). Phase 5/6 chips ("Rust +2") need a short label, and this is the last migration, so the column must exist now. If the planner prefers optional, make it `name TEXT` (nullable) and have consumers fall back to a truncated description. Do not omit the column.
- **`INTEGER` for weight/versions/attempts/max-level** avoids `Short` mapping friction. `vote` stays `SMALLINT` per D-01.
- **Cascade chains:** feed delete → article → `article_score` → `article_topic_score`; article → `article_feedback` → `article_feedback_topic`; topic delete → `article_topic_score` + `article_feedback_topic`. The UI-SPEC delete copy ("Its scores and feedback are removed too") matches. `FeedService.delete` calls `feedRepository.deleteById(id)`, so the cascade path is live. `[VERIFIED: src/main/java/org/bartram/myfeeder/service/FeedService.java:76]`

### Pattern 2: Entities and service (profile singleton, topics)

```java
// model/InterestTopic.java  (imports as in Folder.java:3-5)
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Data
@Table("interest_topic")
public class InterestTopic {
    @Id private Long id;
    private String name;
    private String description;
    private int weight;
    private int version;          // plain column. NOT @Version (see Pitfall 1)
    private Instant createdAt;
    private Instant updatedAt;
}
// model/InterestProfile.java: @Table("interest_profile"), @Id Integer id, String profileText, int version, Instant updatedAt
```

```java
// repository/InterestTopicRepository.java
public interface InterestTopicRepository extends ListCrudRepository<InterestTopic, Long> {
    @Query("SELECT * FROM interest_topic ORDER BY id")
    List<InterestTopic> findAllOrdered();
}
// repository/InterestProfileRepository.java: ListCrudRepository<InterestProfile, Integer>; the service calls findById(1)
```

```java
// service/InterestService.java: the essential rules
public static final int MAX_PROFILE_CHARS = 2000;
public static final int MAX_TOPICS = 25;
public static final int MIN_WEIGHT = -50, MAX_WEIGHT = 50, DEFAULT_WEIGHT = 20;

public InterestProfile updateProfile(String text) {
    if (text == null) throw new IllegalArgumentException("profileText is required");
    if (text.length() > MAX_PROFILE_CHARS)            // UTF-16 units == browser maxLength (Pitfall 5)
        throw new IllegalArgumentException("The profile can be at most 2,000 characters");
    InterestProfile p = profileRepository.findById(1).orElseThrow();  // seeded by V6
    if (!p.getProfileText().equals(text)) p.setVersion(p.getVersion() + 1);
    p.setProfileText(text);
    p.setUpdatedAt(Instant.now());
    return profileRepository.save(p);                  // id != null → UPDATE
}

public InterestTopic createTopic(String name, String description, Integer weight) {
    if (topicRepository.count() >= MAX_TOPICS)
        throw new IllegalArgumentException("A maximum of 25 topics is allowed"); // UI-SPEC quotes this detail
    InterestTopic t = new InterestTopic();
    apply(t, name, description, weight == null ? DEFAULT_WEIGHT : weight);
    Instant now = Instant.now();
    t.setVersion(1); t.setCreatedAt(now); t.setUpdatedAt(now);   // set explicitly (Pitfall 2)
    return topicRepository.save(t);
}
// updateTopic: NotFoundException if missing; bump version iff trimmed description differs; weight/name edits don't bump.
// deleteTopic: NotFoundException if missing (avoids a silent no-op), then deleteById.

/** D-05: the single cold-start predicate. The Phase 4 SCOR-06 gate calls this method. */
public boolean isColdStart() {
    String text = profileRepository.findById(1).map(InterestProfile::getProfileText).orElse("");
    return text.isBlank() && topicRepository.count() == 0;
}
```

### Pattern 3: Pure question builder (shared with Phase 4)

The SDK API was verified from `typesafe-java-sdk-0.1.0-sources.jar`:
- `Noul.builder()` has `instructions(String|Map|List|JsonContent)`, `whenTrue(String|Map|JsonContent)`, `whenFalse(String|Map|JsonContent)` and `build()`; `build()` asserts that instructions are set.
- `Score.builder()` has `instructions(String|Map|List|JsonContent)`, `level(String|Map|List|JsonContent)` and `build()`; the `Score` constructor asserts `criteria.size() >= 2`.
- `Score.maxLevel()` returns `this.criteria.size() - 1`.
- Every String overload runs `Assert.hasText`, so a blank description throws `IllegalArgumentException` in the builder.

Vendor wording guidance `[CITED: docs.typesafe.ai/primitives/score, /primitives/noul, /primitives/advanced.md, /model-jaggedness/jev-1.13.md]`:
- "Describe situations, not degrees."
- "Avoid numerical references."
- Keep each Score to a single dimension.
- "Phrase the question so that a high value means yes."
- Use `criteria` true/false descriptions when the boundary is subtle.
- Object instructions may reference fields by backtick name, e.g. `` "Does `extracted_value` match the `field` as it appears in `source_text`?" ``.
- Keep factual content in state and evaluative instructions in questions.
- Jev reads literally, and double negatives are answered less reliably.

```java
// service/InterestQuestions.java: pure, no Spring, no I/O. Starting wording; the calibration spike iterates it.
public final class InterestQuestions {
    public static final String PROFILE_KEY = "profile";
    public static final String TOPIC_KEY_PREFIX = "topic_";
    public static final String PREVIEW_DRAFT_KEY = "topic_draft";
    static final List<String> PROFILE_LEVELS = List.of(
        "The article's subject has nothing to do with anything in `reader_profile`",
        "The article touches a subject near the reader's interests, but only in passing",
        "The article is partly about a subject in `reader_profile`, mixed with unrelated material",
        "The article is mainly about a subject that `reader_profile` names as an interest",
        "The article is squarely about a core interest in `reader_profile`, in the depth or form the reader asks for");
    public static final int PROFILE_MAX_LEVEL = PROFILE_LEVELS.size() - 1; // 4

    public static String topicKey(long topicId) { return TOPIC_KEY_PREFIX + topicId; }

    public static Score profile(String profileText) {
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("reader_profile", profileText);
        instructions.put("question", "How well does the subject of the article in `title` and `summary` "
            + "match what the reader wants to read, as described in `reader_profile`? "
            + "Judge what the article is about, not how important or relevant it claims to be."); // Pitfall 14
        Score.Builder b = Score.builder().instructions(instructions);
        PROFILE_LEVELS.forEach(b::level);
        return b.build();
    }

    public static Noul topic(String description) {
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("topic", description);
        instructions.put("question", "Is the article in `title` and `summary` primarily about `topic`?");
        return Noul.builder().instructions(instructions)
            .whenTrue("The article's main subject is `topic`")
            .whenFalse("The article's main subject is something else, even if it mentions `topic` in passing")
            .build();
    }

    /** Scoring question map (Phase 4): profile first when non-blank, then topics in id order. LinkedHashMap keeps order. */
    public static Map<String, Question> forRubric(String profileText, List<InterestTopic> topicsById) { … }
}
```

A blank `description` passed to `Noul.builder().instructions(Map)` does **not** trip `hasText`: the map is non-empty, so the question is still built. The preview service must reject a blank description itself, before building.

### Pattern 4: Pure state builder (shared with Phase 4)

```java
// service/ArticleStateBuilder.java: pure. jsoup 1.11.2 is on the classpath.
public final class ArticleStateBuilder {
    public static final int MAX_SUMMARY_CHARS = 1500;  // research default; the spike may tune it
    static final int MAX_RAW_HTML_CHARS = 50_000;      // bound parse cost (Pitfall 4) [ASSUMED value]

    /** Returns {feed, title, summary} in this order, omitting blank fields. Empty map = nothing to judge. */
    public static Map<String, Object> build(String feedTitle, Article article) {
        Map<String, Object> state = new LinkedHashMap<>();
        putIfText(state, "feed", toText(feedTitle));
        putIfText(state, "title", toText(article.getTitle()));    // titles carry entities too (&amp;)
        String body = toText(article.getSummary());
        if (body.isEmpty()) body = toText(article.getContent());  // content fallback (SCOR-01)
        putIfText(state, "summary", truncate(body, MAX_SUMMARY_CHARS));
        return state;
    }
    static String toText(String html) {
        if (html == null || html.isBlank()) return "";
        String bounded = html.length() > MAX_RAW_HTML_CHARS ? html.substring(0, MAX_RAW_HTML_CHARS) : html;
        return Jsoup.parse(bounded).text().trim();    // strips tags, decodes entities, collapses whitespace
    }
    // truncate: cut at the last whitespace at or before max, append "…". Never split a surrogate pair.
    public static boolean hasJudgeableText(Map<String, Object> state) { return state.containsKey("title") || state.containsKey("summary"); }
}
```

`Article.getContent()` may be null after 30 days (RetentionService). That is fine, because the fallback only applies when the summary is blank. The feed title is always present: `feed.title` is `TEXT NOT NULL`. `[VERIFIED: V1__initial_schema.sql:4]`

### Pattern 5: Status + preview services, controller, error mapping

```java
// integration/JevApiClient.java: add (no AOP annotation, so it never touches the breaker)
boolean isConfigured();
// JevApiClientImpl: public boolean isConfigured() { return StringUtils.hasText(properties.getApiKey()); }
//                   private void requireConfigured() { if (!isConfigured()) throw new JevNotConfiguredException(); }
```

```java
// service/InterestStatusService.java
public InterestStatus status() {
    return new InterestStatus(
        jevApiClient.isConfigured(),
        circuitBreakerRegistry.circuitBreaker("jev").getState().name(),   // "CLOSED" | "OPEN" | "HALF_OPEN" | "FORCED_OPEN" | …
        interestService.isColdStart());
}
// controller/InterestStatus.java: public record InterestStatus(boolean configured, String breakerState, boolean coldStart) {}
// Phase 4 appends fields to this record (JEV-05); never rename these three (D-04 "costly").
```

```java
// service/InterestPreviewService.java: NOT @Transactional (never hold a DB transaction across the HTTP call)
public TopicPreviewResponse preview(Long articleId, String description, Long topicId) {
    if (articleId == null) throw new IllegalArgumentException("articleId is required");
    if (description == null || description.isBlank()) throw new IllegalArgumentException("Write a description first");
    // (validate description length with the same limit as InterestService)
    Article article = articleRepository.findById(articleId)
        .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
    String feedTitle = feedRepository.findById(article.getFeedId()).map(Feed::getTitle).orElse(null);
    Map<String, Object> state = ArticleStateBuilder.build(feedTitle, article);
    if (!ArticleStateBuilder.hasJudgeableText(state)) throw new IllegalArgumentException("This article has no text to judge");
    String key = topicId == null ? InterestQuestions.PREVIEW_DRAFT_KEY : InterestQuestions.topicKey(topicId);
    Noul question = InterestQuestions.topic(description.trim());   // built BEFORE judge(): outside the breaker (WR-01)
    JevJudgment j = jevApiClient.judge(state, Map.of(key, question));
    return new TopicPreviewResponse(j.nouls().get(key), j.model());
}
```

```java
// GlobalExceptionHandler additions. Fixed-text details only: TypeSafe messages and bodies can echo request content (Phase 2 D-06).
@ExceptionHandler(JevNotConfiguredException.class)      // 503, title "Jev not configured" (message is fixed text)
@ExceptionHandler(CallNotPermittedException.class)      // 503, title "Jev unavailable", detail "Jev is temporarily unavailable"
@ExceptionHandler({TypeSafeBadRequestException.class, TypeSafeUnprocessableEntityException.class})
                                                         // 422, title "Jev rejected the request", detail e.g. "Jev rejected the request (HTTP " + status() + ")"
@ExceptionHandler(TypeSafeException.class)              // catch-all 503 (429, 5xx, timeout/connection, 401/403, missing answer)
```

Spring picks the most specific handler by exception-hierarchy distance, so the subtype handlers win over the `TypeSafeException` catch-all `[ASSUMED: standard Spring ExceptionDepthComparator behavior; confirm with @WebMvcTest cases]`. Raindrop is unaffected: its `@CircuitBreaker` fallbacks catch and wrap everything before it reaches a controller `[VERIFIED: RaindropApiClientImpl.java:32,52 fallbackMethod]`.

Endpoints (discretion; recommended):

| Method | Path | Body → Response |
|--------|------|-----------------|
| GET | `/api/interest/status` | → `{configured, breakerState, coldStart}` |
| GET | `/api/interest/profile` | → `{id, profileText, version, updatedAt}` |
| PUT | `/api/interest/profile` | `{profileText}` → profile |
| GET | `/api/interest/topics` | → `[topic]` ordered by id |
| POST | `/api/interest/topics` | `{name, description, weight?}` → 201 topic |
| PUT | `/api/interest/topics/{id}` | `{name, description, weight}` → topic |
| DELETE | `/api/interest/topics/{id}` | → 204 |
| POST | `/api/interest/preview` | `{articleId, description, topicId?}` → `{noul, model}` (weight is not needed server-side; the client does the math) |

### Pattern 6: Frontend error status, inline errors, local draft state

```ts
// api/client.ts: keep the message identical so existing tests (toThrow('GET /test failed: 500')) still pass.
// erasableSyntaxOnly is ON: no constructor parameter properties, no enums.
export class ApiError extends Error {
  readonly status: number
  readonly title?: string
  constructor(message: string, status: number, title?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.title = title
  }
}
// in raiseIfBad: parse {detail,title,message}; throw new ApiError(detail || `${method} ${path} failed: ${res.status}`, res.status, body?.title)
```

```ts
// App.tsx MutationCache: global callbacks ALWAYS run (TanStack v5), so opt out per mutation via meta.
mutationCache: new MutationCache({
  onError: (error, _vars, _ctx, mutation) => {
    if (mutation.meta?.inlineError) return
    useToastStore.getState().addToast(error.message || 'An error occurred')
  },
}),
// useInterest.ts mutations: useMutation({ mutationFn, meta: { inlineError: true }, … })
```

The TanStack signature is `onError(error, variables, onMutateResult, mutation, context)`, and `mutation.meta` is readable inside the global callbacks `[VERIFIED: Context7 /tanstack/query v5.90.3 queryCache.ts types + useMutation meta test]`. For TypeScript, `meta` is `Record<string, unknown>`, so `mutation.meta?.inlineError` type-checks without a module augmentation `[ASSUMED]`.

```ts
// utils/interest.ts: pure helpers (unit-test these)
export const hinge = (noul: number) => Math.max(0, (noul - 0.5) * 2)
export function formatSigned(n: number, digits = 0): string {
  const s = Math.abs(n).toFixed(digits)
  return n > 0 ? `+${s}` : n < 0 ? `−${s}` : s   // U+2212 per UI-SPEC
}
const NEGATION = /\b(not about|nothing about|anything but|not|no|except|without|isn't|aren't|excluding)\b/i
export const isNegated = (text: string) => NEGATION.test(text.replace(/’/g, "'"))  // macOS smart quotes
```

Dialog data flow:
- `InterestsDialog` returns `null` when `!open` and renders an inner body component, so the queries mount only while the dialog is open.
- The status query uses `staleTime: 0`, so every open refetches (UI-SPEC "no stale cache across opens"). The global default is `staleTime: 30_000`. `[VERIFIED: src/main/frontend/src/App.tsx:21-24]`
- Rows live in component state keyed by id, with a separate "saved baseline" per row. A successful save updates **only that row's** baseline, from the mutation response, via `setQueryData`. Do **not** re-seed all rows from a refetch, or other rows' unsaved edits are wiped (Pitfall 7).
- After a profile save, topic add or topic delete, invalidate `['interest','status']` so `coldStart` refreshes.
- The preview target is `useUIStore((s) => s.selectedArticleId)` plus `useArticle(id)`. The query key `['article', id]` is already cached by the ReadingPane, so there is no extra fetch. `[VERIFIED: src/main/frontend/src/hooks/useArticles.ts:16-22]`
- `SettingsDialog` gains an `onOpenInterests` prop. Test files are type-checked (`tsconfig.app.json` `"include": ["src"]`), so either make the prop optional or update `SettingsDialog.test.tsx`'s renders. `[VERIFIED: src/main/frontend/tsconfig.app.json]`

### Anti-Patterns to Avoid
- **`@Version` on `version`:** Spring Data's optimistic-locking version increments on **every** save, including weight-only edits, which breaks "bump on text edits only".
- **Building questions inside `judge()`'s call path, or letting `IllegalArgumentException` reach the proxy:** Phase 2 WR-01. `IllegalArgumentException` inside `judge` is recorded as a breaker failure. Validate and build first.
- **`@Transactional` around the preview:** it holds a DB connection for up to about 3 × 5s of retries.
- **Echoing `ex.getMessage()` or the body of TypeSafe exceptions into ProblemDetail:** it can leak request content. Use fixed text.
- **Computing cold start on the client:** D-05.
- **Using the profile text as state:** it belongs in the question instructions (vendor: "Keep factual content … in state; place evaluative instructions in your questions").
- **Negation logic on the server that rejects saves:** D-09 says advice only, never blocking.
- **Binding Escape in the dialog:** the global Escape handler clears `selectedArticleId`, the preview target (UI-SPEC). Inputs already blur on Escape. `[VERIFIED: src/main/frontend/src/hooks/useKeyboardShortcuts.ts:53-54]`

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML → plain text, entity decoding | Regex tag stripping | `Jsoup.parse(html).text()` | Entities, comments, `<script>`/`<style>` bodies, malformed markup |
| Jev question JSON | Hand-written `Map` question payloads | SDK `Noul.builder()` / `Score.builder()` | Typed wire format (`criteria: {"true","false"}`), built-in asserts |
| Breaker state | Tracking failures yourself | `CircuitBreakerRegistry.circuitBreaker("jev").getState()` | The same instance the `@CircuitBreaker` aspect uses |
| Retry/timeout on preview | A retry loop in the preview service | The existing `@Retry`/`@CircuitBreaker` on `JevApiClientImpl` | One retry layer (Phase 2 D-07) |
| Error-detail extraction | Parsing `res.text()` per component | `raiseIfBad` → `ApiError` | One place that understands ProblemDetail |
| Range/number input sync | A custom slider | Native `<input type="range">` + `<input type="number">` | UI-SPEC mandates the native controls |

**Key insight:** every "clever" piece of this phase already exists: the SDK builders, jsoup, the Resilience4j registry, `raiseIfBad`. The work is wiring them so the Phase 4 scorer can reuse the builders unchanged.

## Common Pitfalls

### Pitfall 1: `@Version` semantics on the `version` column
**What goes wrong:** Annotating `version` with Spring Data `@Version` makes every save increment it (and adds optimistic-lock WHERE clauses), so weight edits bump the version.
**How to avoid:** Keep `version` a plain `int`. Increment it in the service only when the (trimmed) text changed. Test: a weight-only PUT leaves the version unchanged, and a description PUT increments it by exactly 1.

### Pitfall 2: DB DEFAULTs don't apply to Spring Data JDBC inserts of null/zero fields
**What goes wrong:** `save()` on a new entity writes every mapped property. A null `createdAt` violates `NOT NULL`, and a primitive `version` of 0 is stored as 0, not the DB default of 1.
**How to avoid:** Set `version = 1`, `createdAt`, `updatedAt` and the weight default explicitly in the service, matching `FolderService.create`, which sets `folder.setCreatedAt(Instant.now())` `[VERIFIED: FolderService.java:32]`. `[ASSUMED: exact Spring Data JDBC null-insert behavior; the explicit-set pattern avoids depending on it]`

### Pitfall 3: Re-score would keep stale nouls if `article_topic_score` hangs off `article`
**What goes wrong:** Phase 4 INT-05 deletes in-scope `article_score` rows and lets the sweep re-score them. If topic rows reference `article(id)` (as in the ARCHITECTURE sketch), they survive the delete, the re-insert's `ON CONFLICT DO NOTHING` keeps the **old** nouls, and the re-score silently does nothing for topics.
**How to avoid:** `article_topic_score.article_id REFERENCES article_score(article_id) ON DELETE CASCADE` (Pattern 1). The writer inserts the parent row first in the same transaction. The V6 test asserts that deleting a score row deletes its topic rows.

### Pitfall 4: jsoup 1.11.2 parsing unbounded untrusted HTML
**What goes wrong:** jsoup before 1.14.2 can loop or stall on crafted input (CVE-2021-37714, CVSS 7.5) `[CITED: cvedetails.com/cve/CVE-2021-37714]`. Some feeds put a 1 MB article in `<description>`. In Phase 4 the builder runs for every article on the scoring executor.
**How to avoid:** Cap the raw input (e.g. 50,000 chars) before `Jsoup.parse`, then truncate the text to 1,500 chars. Unit-test with a 1 MB field. The upgrade question is recorded under Open Questions.

### Pitfall 5: Character counting mismatch between browser and server
**What goes wrong:** The browser `maxLength` and JS `.length` count UTF-16 code units, and so does Java `String.length()`. Postgres `char_length` counts code points. Using different measures makes the counter and the server disagree on emoji-heavy text.
**How to avoid:** Validate with Java `String.length()` only. No DB length CHECK. The frontend counter uses `text.length`.

### Pitfall 6: The UI can't tell 503-not-configured from 503-breaker from 422
**What goes wrong:** `raiseIfBad` throws `new Error(detail || \`${method} ${path} failed: ${res.status}\`)`, which carries no status `[VERIFIED: src/main/frontend/src/api/client.ts:20]`. UI-SPEC preview errors branch on 503 / 429 / 5xx / 400 / 422, and on "not configured" vs "unavailable".
**How to avoid:** Use `ApiError` with `status` and ProblemDetail `title` (Pattern 6). The server gives the not-configured and breaker-open cases distinct titles ("Jev not configured" vs "Jev unavailable").

### Pitfall 7: Query refetch clobbers unsaved rows
**What goes wrong:** Row B is saved, `['interest','topics']` is invalidated, the component re-derives its rows from the fresh data, and row A's unsaved edits vanish. Tests that save one row while another is dirty catch this.
**How to avoid:** Seed local rows from the query once per open. Merge saved responses per row id. Drafts keep a client key until POST returns an id, then become saved rows in place (UI-SPEC: "no re-sort until the dialog reopens").

### Pitfall 8: Double error reporting through the global `MutationCache` toast
**What goes wrong:** `App.tsx`'s `MutationCache.onError` toasts every mutation error `[VERIFIED: src/main/frontend/src/App.tsx:25-29]`. The dialog also shows inline errors, so the user sees each failure twice.
**How to avoid:** Add `meta: { inlineError: true }` to the interest mutations and skip them in the global handler (Pattern 6). Alternatively, call `interestApi` directly without `useMutation`, but `meta` is cleaner and keeps TanStack state.

### Pitfall 9: Negation false negatives and positives
**What goes wrong:** macOS auto-substitutes `’` for `'`, so `isn’t` misses `/isn't/`. `\bno\b` fires on "no-code tools" because the hyphen is a word boundary.
**How to avoid:** Normalize `’` → `'` before matching. Accept the hyphen false positives (the warning is non-blocking advice), and cover both cases in `utils/interest.test.ts`.

### Pitfall 10: Flat, uninformative scores (research Pitfall 7)
**What goes wrong:** Degree-style levels ("somewhat relevant") and "mentions X" phrasing produce mid-range clustering.
**How to avoid:** Use situation-style levels and "primarily about" with explicit `whenTrue`/`whenFalse` (Pattern 3). Then run the calibration spike before Phase 5 builds on the scores. Warning signs: low stddev of normalized profile scores, median Score confidence below 0.5, and most nouls in [0.35, 0.65].

### Pitfall 11: Question-builder asserts reaching the breaker
**What goes wrong:** Phase 2 WR-01 says `IllegalArgumentException` inside `judge()` counts as a breaker failure.
**How to avoid:** Build the question and validate the state in `InterestPreviewService` before calling `judge`. `Noul.builder().instructions(Map)` does not assert non-blank values, so validate `description.isBlank()` yourself.

## Code Examples

### Preview math (client), per UI-SPEC
```ts
const m = hinge(noul)
const matchPct = Math.round(noul * 100)
const countsPct = Math.round(m * 100)
const pts = m * weight
const text = m > 0
  ? `Match ${matchPct}% → counts ${countsPct}% × ${formatSigned(weight)} = ${formatSigned(pts, 1)} pts`
  : `Match ${matchPct}% · No match (contributes 0)`
// noul 0.82, weight +20 → m = 0.6399999999999999 → "Match 82% → counts 64% × +20 = +12.8 pts"
```

### V6 migration test (house pattern: Flyway runs at `@DataJdbcTest` startup)
```java
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V6InterestScoringMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void seedsSingletonProfile() { assertThat(jdbc.queryForObject("SELECT count(*) FROM interest_profile", Integer.class)).isEqualTo(1); }
    @Test void rejectsSecondProfileRow() { assertThatThrownBy(() -> jdbc.update("INSERT INTO interest_profile (id) VALUES (2)")).isInstanceOf(DataIntegrityViolationException.class); }
    @Test void rejectsWeightOutOfRange() { /* insert topic weight 51 and -51 → DataIntegrityViolationException; 50 and -50 accepted */ }
    @Test void acceptsSkippedStatus() { /* insert article_score status 'SKIPPED' ok; 'PENDING' rejected */ }
    @Test void cascades() { /* delete topic → article_topic_score + article_feedback_topic rows gone;
                               delete article_score → its article_topic_score rows gone;
                               delete article → score, feedback, children gone; vote 0 rejected */ }
}
```

### Controller test for Jev error mapping
```java
when(previewService.preview(any(), any(), any()))
    .thenThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("jev")));
mockMvc.perform(post("/api/interest/preview").contentType(APPLICATION_JSON).content("{\"articleId\":1,\"description\":\"Rust\"}"))
    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.title").value("Jev unavailable"));
```
The `createCallNotPermittedException(CircuitBreaker)` and `CircuitBreaker.ofDefaults(String)` signatures were verified with javap against resilience4j-circuitbreaker 2.3.0.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Weight `DOUBLE -3..3` (ARCHITECTURE sketch) | `INTEGER -50..50` points, default 20 | R1 (research synthesis) | CHECK constraint and UI slider |
| Status `SCORED/FAILED` | `SCORED/FAILED/SKIPPED` | R3 | V6 CHECK |
| `article_feedback(article_id, vote)` only | + `topics_narrowed` + `article_feedback_topic` | D-01/D-02 | FDBK-06 picks without later migration |
| `article_topic_score → article` | `article_topic_score → article_score` | This research | Phase 4 re-score correctness |
| `JevConsistency.sample(...)` (Pitfall 7) | Manual repeat calls in the spike | — | `JevConsistency` is **not** in `typesafe-java-sdk` 0.1.0 `[VERIFIED: source jar file listing: no Consistency/CompositeScore classes]` |

## Calibration Spike (research flag, closing task)

Recommended mechanics (discretion):

- **Runner:** `src/test/java/org/bartram/myfeeder/integration/InterestCalibrationSpikeTest.java`, gated by `@EnabledIfEnvironmentVariable(named = "JEV_CALIBRATION", matches = "true")`, with the same `ApplicationContextRunner` wiring as `JevLiveSmokeTest`. That test is gated on `JEV_LIVE_SMOKE` `[VERIFIED: src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java:44]` and loads the main YAML from disk because the test YAML shadows it (lines 50–56). Run it with `cleanTest` (Gradle doesn't treat env vars as inputs), for example: `JEV_CALIBRATION=true ./gradlew cleanTest test -x npmBuild -x npmInstall --tests '*InterestCalibrationSpikeTest' --info`.
- **Inputs (kept out of git):** `JEV_CALIBRATION_INPUT=/path/input.json` holding `{profile, topics:[{id,name,description,weight}], articles:[{feedTitle,title,summary,content}]}`.
  - Articles can be exported from the running deployment's existing `GET /api/articles?limit=…`. The LAN address is `192.168.44.204`; reachability from the dev machine is `[ASSUMED]`.
  - The user writes the profile and topics, ideally the real ones entered in the new dialog.
  - Don't commit the input, since it contains personal interests.
- **Calls:** one `judge` per article with `InterestQuestions.forRubric(...)` and `ArticleStateBuilder.build(...)`, so exactly what Phase 4 sends. Optionally repeat 3 articles twice to eyeball consistency. Cost is negligible: about 20 calls at roughly 1–2k input tokens each.
- **Output:** a printed table (title, profile value/maxLevel, confidence, each noul, hinge, blended points) plus summary statistics: stddev of normalized profile score, median confidence, and the share of nouls in [0.35, 0.65].
- **Starting heuristics `[ASSUMED]`:** stddev of normalized profile score ≥ 0.15, median confidence ≥ 0.5, fewer than half of nouls in [0.35, 0.65], and the user's own high/low labels ranking in the right order for most articles.
- **Iteration:** tweak only the `InterestQuestions` wording constants (and `MAX_SUMMARY_CHARS`), re-run, and update the builder unit tests to match.
- **Record:** `.planning/phases/03-interest-model-schema-rubric-editor/03-CALIBRATION.md` with aggregate numbers, the wording versions tried and the chosen wording. Titles are fine; no profile text unless the user approves.
- **Human checkpoint:** needs a live `MYFEEDER_TYPESAFE_API_KEY`. STATE.md says the key was confirmed working on 2026-09-23. `.envrc` references it via direnv; it isn't exported in this agent shell.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Data JDBC writes null/zero properties on insert rather than using DB defaults | Pitfall 2 | Low: the recommended explicit-set pattern works either way |
| A2 | Spring's handler resolution prefers the most specific `@ExceptionHandler` (subtype over the `TypeSafeException` catch-all) | Pattern 5 | Medium: the wrong status for 422 vs 503; covered by `@WebMvcTest` cases |
| A3 | `mutation.meta?.inlineError` type-checks without a `Register` module augmentation | Pattern 6 | Low: add a `declare module '@tanstack/react-query'` Register if `tsc -b` complains |
| A4 | The raw-HTML pre-parse cap of 50,000 chars is a reasonable bound | Pattern 4 | Low: tunable constant; too small could drop text in markup-heavy feeds |
| A5 | Upgrading jsoup past 1.14.2 might break Readability4J 1.0.8 | Alternatives / Open Questions | Low for this phase (no upgrade proposed) |
| A6 | Spike thresholds (stddev ≥ 0.15, median confidence ≥ 0.5, < 50% nouls mid-band) | Calibration Spike | Medium: they are heuristics; the user's judgment on ranked output decides |
| A7 | The deployed app's `/api/articles` is reachable from the dev machine for spike input | Calibration Spike | Low: alternative is a psql export or a local `bootTestRun` |
| A8 | Jackson 3 keeps `ACCEPT_FLOAT_AS_INT` on by default, so `"weight": 20.5` binds to `Integer` 20 | Open Questions | Low: the client rejects non-integers; the server CHECK still bounds the range |
| A9 | Recommended topic limits: name 1–40 chars, description ≤ 500 chars | Pattern 1/2 | Low: service constants, no DB constraint |

## Open Questions

1. **Topic `name`: required or optional?**
   - What we know: Phase 5/6 need short labels, and V6 is the last migration. UI-SPEC supports both layouts.
   - Recommendation: required `name TEXT NOT NULL` (1–40 chars). Confirm with the user during planning, because it adds a field to every row.
2. **jsoup 1.11.2 (CVE-2021-37714)**
   - What we know: this is pre-existing (ArticleExtractionService already parses untrusted pages with it). The latest jsoup is 1.23.2 (Maven Central metadata, lastUpdated 2026-08-26).
   - Recommendation: cap the input in this phase and file a separate quick task to pin a newer jsoup after checking Readability4J compatibility. Not in scope here.
3. **Phase 2 WR-01 (`IllegalArgumentException` recorded by the breaker)**
   - Recommendation: the preview avoids it by validating first (Pattern 5). Adding `java.lang.IllegalArgumentException` to the `jev` breaker `ignore-exceptions` in **both** YAMLs is a two-line hardening. The planner may fold it in, but STATE.md schedules the WR items for "before/within Phase 4".
4. **Roadmap note fix (deferred item)**
   - Planner: include a doc-only task correcting `.planning/ROADMAP.md` line 100 ("Phase 2 lays down `/api/interest/status`…").
5. **CLAUDE.md accuracy**
   - `Flyway migrations` list (add V6), `API endpoints` list (add `/api/interest`), and the `@Table` import claim (see Project Constraints). A small doc task keeps CLAUDE.md truthful; OPS-03 (Phase 7) covers the fuller Jev docs.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Backend build/tests | ✓ | OpenJDK 25.0.4 | — |
| Docker (Testcontainers) | `@DataJdbcTest`, `@SpringBootTest` | ✓ | Server 29.7.2 | — |
| Node / npm | Frontend tests, `tsc -b` | ✓ | Node v26.9.0 / npm 11.19.1 | — |
| Frontend `node_modules` | Vitest | ✓ | installed | `npm install` |
| `MYFEEDER_TYPESAFE_API_KEY` | Calibration spike only | ✗ in agent shell (referenced in `.envrc` via direnv) | — | The spike is a human-run checkpoint; all other tests stub `JevApiClient` |

**Missing dependencies with no fallback:** none for automated work.
**Missing dependencies with fallback:** the live key, needed only for the gated spike (human-verify checkpoint).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito + Testcontainers (backend); Vitest 4 + RTL 16 + jsdom (frontend) |
| Config file | `build.gradle.kts` (`useJUnitPlatform`), `src/test/resources/application.yaml`; `src/main/frontend/vitest.config.ts`, `src/test/setup.ts` |
| Quick run command | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.*Interest*'` and `cd src/main/frontend && npx vitest run src/utils/interest.test.ts src/components/InterestsDialog.test.tsx` |
| Full suite command | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| (schema) | V6 tables, seed row, weight CHECK, SKIPPED, cascades incl. score→topic-score | integration (DataJdbc) | `./gradlew test -x npmBuild -x npmInstall --tests '*V6InterestScoringMigrationTest'` | ❌ Wave 0 |
| INT-01 | Profile ≤2,000 accepted, 2,001 → 400; version bumps only on a text change; persists | unit + WebMvc | `--tests '*InterestServiceTest' --tests '*InterestControllerTest'` | ❌ Wave 0 |
| INT-01 | Tips, placeholder, `N / 2,000` counter, save/dirty states | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ❌ Wave 0 |
| INT-02 | 26th topic → 400 "A maximum of 25 topics is allowed"; weight ±51 → 400; default 20; description-only version bump; delete → 204/404 | unit + WebMvc | `--tests '*InterestServiceTest' --tests '*InterestControllerTest'` | ❌ Wave 0 |
| INT-02 | Slider/number sync, invalid weight disables Save, Add disabled at 25 | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ❌ Wave 0 |
| INT-03 | `isNegated` word list, smart-quote normalization, debounce shows/clears warning, Save still enabled | unit + component | `npx vitest run src/utils/interest.test.ts src/components/InterestsDialog.test.tsx` | ❌ Wave 0 |
| INT-04 | State builder: null summary → content fallback, HTML/entity strip, 1,500 truncation, 1 MB input bounded, blank fields omitted, key order | unit | `--tests '*ArticleStateBuilderTest'` | ❌ Wave 0 |
| INT-04 | Question builder: keys `profile`/`topic_<id>`, profile first, 5 levels (`maxLevel()==4`), Noul has whenTrue/whenFalse, LinkedHashMap order | unit | `--tests '*InterestQuestionsTest'` | ❌ Wave 0 |
| INT-04 | Preview: one `judge` with exactly one Noul, nothing persisted, blank desc → 400, missing article → 404, not configured → 503, breaker open → 503, 422 mapping | unit + WebMvc | `--tests '*InterestPreviewServiceTest' --tests '*InterestControllerTest'` | ❌ Wave 0 |
| INT-04 | Preview math copy (82% → +12.8), no-match copy, weight-only recompute, stale dimming, disabled reasons, inline error without toast | unit + component | `npx vitest run src/utils/interest.test.ts src/components/InterestsDialog.test.tsx` | ❌ Wave 0 |
| INT-06 | `/status` configured true/false, breakerState name, coldStart predicate (blank-after-trim + 0 topics) | unit + WebMvc | `--tests '*InterestStatusServiceTest' --tests '*InterestServiceTest'` | ❌ Wave 0 |
| INT-06 | Not-configured / paused / cold-start notices in order; editing still allowed without a key | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ❌ Wave 0 |
| (client) | `ApiError` carries status/title; existing messages unchanged | unit | `npx vitest run src/api/client.test.ts` | ✅ (extend) |
| (spike) | Live calibration of wording | manual (gated) | `JEV_CALIBRATION=true … --tests '*InterestCalibrationSpikeTest'` | ❌ Wave 0 (manual-only: billed live API, needs a key and human judgment) |

### Sampling Rate
- **Per task commit:** the quick run commands for the touched layer.
- **Per wave merge:** `./gradlew test` (Docker running) + `cd src/main/frontend && npm test && npx tsc -b`.
- **Phase gate:** the full suite green before `/gsd-verify-work`; the spike findings are recorded in `03-CALIBRATION.md`.

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java`
- [ ] `src/test/java/org/bartram/myfeeder/service/{InterestServiceTest,InterestQuestionsTest,ArticleStateBuilderTest,InterestPreviewServiceTest,InterestStatusServiceTest}.java`
- [ ] `src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java`
- [ ] `src/main/frontend/src/utils/interest.test.ts`, `src/main/frontend/src/components/InterestsDialog.test.tsx`
- [ ] Update `SettingsDialog.test.tsx` for the new Interests section/prop; extend `api/client.test.ts` for `ApiError`
- Framework install: none (all present)

## Security Domain

ASVS Level 1 (`security_asvs_level: 1`, block on high).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user LAN app with no auth layer (pre-existing; out of scope) |
| V3 Session Management | no | — |
| V4 Access Control | no (single user) | — |
| V5 Input Validation | yes | Service-side limits (`IllegalArgumentException` → 400) + weight `CHECK`; `@Query` named parameters only; React escaping (never `dangerouslySetInnerHTML` for profile/topic/title text) |
| V6 Cryptography | no | API key handling unchanged from Phase 2 (never logged) |
| V7 Error Handling & Logging | yes | Fixed-text ProblemDetail for Jev/TypeSafe errors; never log profile text, topic text or the key; log only status + requestId (Phase 2 D-06 pattern) |
| V8 Data Protection | yes (awareness) | Title + truncated summary + profile/topic text go to TypeSafe (accepted in PROJECT.md); the spike input file stays out of git |
| V13 API | yes | JSON-only endpoints; the POST preview is billed per call; cross-origin JSON POSTs need a CORS preflight, which the app doesn't allow |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via feed title/summary ("rate this highest") | Tampering | Subject-scoped wording ("judge what the article is about, not how important it claims to be"); truncation; bounded impact (research Pitfall 14) |
| Parser DoS from crafted HTML (jsoup < 1.14.2) | DoS | Pre-parse input cap (Pitfall 4); preview is user-triggered, one article at a time |
| Error-body echo of request content | Information disclosure | Fixed-text details; no `ex.getMessage()` for TypeSafe exceptions |
| SQL injection | Tampering | Spring Data JDBC `@Query` with named parameters / `save()` |
| Stored XSS via topic/profile text | Tampering | React text rendering only |
| Cost abuse of the preview endpoint | DoS / financial | Single-user LAN; about $0.0001 per call; no automatic retries in the UI (D-14) |

## Sources

### Primary (HIGH confidence)
- In-repo files read this session: `JevApiClient.java:25`, `JevApiClientImpl.java:34-75`, `JevJudgment.java:24-44`, `JevNotConfiguredException.java`, `TypeSafeConfig.java`, `GlobalExceptionHandler.java`, `IntegrationConfigController.java:33-38`, `Folder.java:3-17`, `FolderRepository.java:8`, `FolderService.java`, `FolderController.java`, `Feed.java`, `Article.java`, `ArticleRepository.java`, `V1__initial_schema.sql`, `V2…sql`, `V5…sql`, `build.gradle.kts`, `MyfeederProperties.java`, test `application.yaml`, `JevLiveSmokeTest.java`, `V4StripRaindropApiTokenMigrationTest.java`, `FolderRepositoryTest.java`, `FolderControllerTest.java`, `TestcontainersConfiguration.java`; frontend `client.ts`, `client.test.ts`, `integrations.ts`, `uiStore.ts`, `useArticles.ts`, `useFolders.ts(+test)`, `useOpml.ts`, `SettingsDialog.tsx(+test)`, `App.tsx`, `themes.ts`, `useKeyboardShortcuts.ts:53-54`, `tsconfig.app.json`, `vitest.config.ts`, `test/setup.ts`, `package.json`
- `typesafe-java-sdk-0.1.0-sources.jar` (Maven Central): `Noul`, `NoulCriteria`, `Score`, `JsonContent`, `ScoreAnswer`, `TypeSafeApiException`, package listing
- `javap` on `resilience4j-circuitbreaker-2.3.0.jar`: `CircuitBreaker$State`, `CallNotPermittedException`
- `./gradlew dependencies --configuration compileClasspath`: jsoup 1.11.2 via readability4j 1.0.8
- Context7 `/tanstack/query/v5.90.3`: `MutationCacheConfig.onError` signature; meta in global callbacks
- Planning docs: 03-CONTEXT.md, 03-UI-SPEC.md, REQUIREMENTS.md, STATE.md, research SUMMARY/ARCHITECTURE/STACK/PITFALLS, 02-REVIEW.md

### Secondary (MEDIUM confidence)
- docs.typesafe.ai: `/primitives/score`, `/primitives/noul`, `/primitives/advanced.md`, `/concepts/state.md`, `/model-jaggedness/jev-1.13.md` (wording guidance)

### Tertiary (LOW confidence)
- cvedetails.com / strix.ai (CVE-2021-37714 affected range); Maven Central jsoup metadata (latest 1.23.2)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Nothing new; everything resolved on the classpath and read from source.
- Architecture: HIGH. Integration points were read from source; the endpoint shapes are discretionary recommendations.
- Schema: HIGH for the locked parts. The `article_topic_score → article_score` FK is a new recommendation (reasoned from INT-05's documented mechanism).
- Question wording: MEDIUM. It follows vendor guidance but is unproven until the spike.
- Pitfalls: HIGH for the in-repo ones (verified), MEDIUM for the Spring Data insert and handler-resolution assumptions.

**Research date:** 2026-09-23
**Valid until:** 2026-10-23 (stable stack; the TypeSafe SDK is 0.1.0 and could move, so re-check if it is bumped)
