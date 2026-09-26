# Phase 6: Thumbs Feedback - Research

**Researched:** 2026-09-26
**Domain:** Derived per-topic learning in a Postgres CTE (Spring Data JDBC / JdbcClient), vote endpoints, and a React 19 + TanStack Query v5 reading-pane UX with in-place cache patching
**Confidence:** HIGH (the codebase is fully read and the SQL was run against Postgres 18; the only external facts are TanStack `scope` and React 17+ event delegation)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (locked by research and prior phases, not re-discussed)
- **The learned model is derived, not mutated (research R2 / ARCHITECTURE Pattern 5):** a vote writes or deletes one `article_feedback` row, and `interest_topic.weight` is never written. The `learned` CTE in `InterestScoreQueries` (a stub returning 0 today, near line 199) becomes `learned_t = clamp(η × Σ vote × m_t, −L, +L)` with `m_t = max(0, (noul − 0.5) × 2)`, `η = 2` points per vote and `L = 20`. The effective weight is `w_t = clamp(base_t + learned_t, −50, +50)` with the **sign clamp**: a positive base never goes below 0, a negative base never goes above 0, and a base of 0 can move either way. Undo is exact because it's a row delete or flip. Constants go under `myfeeder.interest.blend.*`, and Phase 7 tunes them.
- **The learned CTE must honor `topics_narrowed` (03 D-02):** when it's true, only the `article_feedback_topic` child rows count, and zero child rows means the vote moves nothing. The schema allows narrowing in both directions (03 D-03); the UI offers it only on thumbs-down.
- **One source of truth:** the blend CTE drives the sort, the badge and the breakdown (05 decisions). The effect message's numbers also come from the server. The client never recomputes weights.
- **The Priority query is never invalidated.** It's patched in place (`patchPriorityArticle`, 05-06), and re-ranking happens only through the explicit refresh.
- **No V7 migration is expected.** V6 already has `article_feedback(article_id PK, vote ±1, topics_narrowed, created_at)` and `article_feedback_topic(article_id, topic_id)`, both with cascades.

#### Vote UX & badge ripple
- **D-01:** 👍/👎 buttons live **only in the reading-pane toolbar**, next to ★ Star, in every view. `u`/`d` work wherever an article is selected (both keys are free today). Pressing the active vote again removes it, and pressing the other one flips it (FDBK-01).
- **D-02:** **List rows show no vote indicator.** Vote state shows only as the pane buttons' toggled state.
- **D-03:** **You can vote on unscored articles.** The row is stored and starts counting once the article is scored (R2). The message says so, e.g. "Saved — counts once this article is scored".
- **D-04:** **A vote has no side effects.** It doesn't mark read or advance, and read state still follows `autoMarkReadDelay`.
- **D-05:** **Outside Priority**, a vote refreshes **all visible badges**: invalidate `['articles']` and `['article', id]` so every badge re-reads the CTE. These lists sort by date, so order can't shift.
- **D-06:** **Inside Priority**, only the **voted row** is patched in place (its badge and feedback, from the vote response). Every other row keeps its served badge so the frozen list never looks mis-sorted, and the vote sets `rankingChanged` (the "Ranking changed — refresh" hint). This also covers 05-REVIEW WR-05 for vote-driven score rises.

#### Effect message
- **D-07:** The message shows the **actual** change in each affected topic's effective weight, not the nominal η × m nudge. The vote endpoint returns each affected topic's effective weight before and after, and the client prints `after − before`. A capped topic shows `+0.0` with a cap note (e.g. "Rust +0.0 (learned at max +20)"). The sign clamp is covered the same way. — **Reversibility:** costly — the vote endpoint's response shape (per-topic before/after) becomes the contract that the toast, the pane and tests consume.
- **D-08:** **One decimal place** (e.g. "Rust +2.0 · Go +0.2").
- **D-09:** **Surface:** the existing success toast (`useToastStore.addToast(msg, 'success')`, 5s). It shows the **top 3 topics by absolute change, then "+N more"**. Zero-change topics are listed only when capped or clamped, to explain the missing effect.
- **D-10:** **Remove and flip use the same format with the net change**, e.g. "Vote removed · Rust −2.0". Flipping up→down shows the full swing ("Rust −4.0").
- **D-11:** The reading pane's **"Why N?" rows show the effective weight with its learned part**, e.g. `Rust 90% × 24 (20 +4 learned) = 22`. 05 D-02's rule still holds: rows sum exactly to the badge.

#### Thumbs-down topic picker (FDBK-06)
- **D-12:** **Vote first, narrow optionally.** `d`/👎 saves immediately against all matched topics, so `d` stays a one-key triage action. Narrowing is a later, optional step that re-saves with `topics_narrowed = true` plus child rows.
- **D-13:** **Entry point:** a small **"Narrow…" control in the pane** next to the active 👎, shown whenever the down-vote covers 2+ matched topics, plus **Shift+D** to open the picker. The toast stays text-only.
- **D-14:** **The picker is a popover under 👎:** a checkbox list of the matched topics with match % in the existing chip sign colors, plus Apply/Cancel. Keyboard: `1`–`9` toggle, Enter applies, Esc cancels. It starts with the current subset checked, or all matched topics.
- **D-15:** **Apply is disabled when no topic is checked.** To penalize nothing, remove the vote. The UI never creates a narrowed vote with zero child rows, even though D-02 would allow one.
- **D-16:** **A narrowed down-vote is visible and re-editable.** The pane shows e.g. "👎 Politics only", and clicking it re-opens the picker. Pressing 👎 again still removes the vote (FDBK-01).
- **D-17:** **Flipping a narrowed 👎 to 👍 clears the narrowing** (`topics_narrowed = false`, child rows deleted). The up-vote applies to all matched topics.

#### No match → create topic (FDBK-05)
- **D-18:** When a vote on a **scored** article matches no topics (every `m_t = 0`), **the vote is still stored.** It moves nothing now, and it counts only if a later re-score gives it matches.
- **D-19:** **Entry point:** the toast says "No topics matched", and an **inline line in the pane** under the thumbs reads "No topics matched — [Create topic from article]". The line stays while the article is open. This is the same reasoning as D-13: the toast has no actions.
- **D-20:** **"Create topic from article" opens `InterestsDialog`** (reusing MainLayout's `interestsOpen`, as 03-05 and 05 did) with a **new unsaved draft row**. The name is empty and focused, the description is prefilled from the article title, and the weight is **+20 for 👍 / −20 for 👎**. The user edits and saves as normal (negation warning and Preview against this article still work). Nothing is saved automatically.
- **D-21:** **Creating the topic doesn't re-judge the article.** Rubric changes apply to new articles, and manual "Re-score unread" covers the backlog. A vote never causes a Jev call (FDBK-02).

### Claude's Discretion
- **Topic editor display (FDBK-07), not discussed:** how `TopicRow` shows base vs learned. The slider and number stay the base weight, and the learned adjustment (and effective weight) shows alongside as read-only. This needs learned values per topic from the server, from the same CTE, e.g. added to the topics GET.
- The endpoint shape: `PUT /api/articles/{id}/feedback` (`{vote, topicIds?}`) and `DELETE /api/articles/{id}/feedback` (research suggests these), what they return (the re-enriched article plus per-topic before/after for D-07), and how the vote and picks reach the pane (e.g. a `feedback` field on article responses).
- How "matched topics" is defined for the picker and the no-match check: `m_t > 0` from stored nouls, over topics that still exist.
- Where Shift+D and `u`/`d` go in `ShortcutOverlay`, and the exact toast and inline copy.
- Whether a vote also invalidates the topics query (the learned values in the editor), and the interplay with `usePriorityStore`.
- How the draft-row prefill passes into `InterestsDialog` / `TopicRowState` (e.g. a store field or a dialog prop).

### Deferred Ideas (OUT OF SCOPE)
- Per-topic "reset learned adjustment": already v2 (FDBK-V2-01).
- A vote indicator in list rows and thumbs on list-row hover: considered and rejected for v1 (D-01/D-02). They could come back with PRIO-V2 triage work.
- Re-judging a single article after creating a topic from it: rejected (D-21). Covered by manual Re-score.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FDBK-01 | Thumbs up/down (buttons plus `u`/`d`); pressing again removes, switching flips | Absolute `PUT`/`DELETE` endpoints (Pattern 2); client toggle rule with an intent cache plus a TanStack `scope` so rapid presses stay ordered (Pattern 5, Pitfall 3); key guard for modifiers (Pitfall 6) |
| FDBK-02 | A vote adjusts the effective weight of matched topics; ranking and badges reflect it immediately, no Jev call | `learned` CTE body (Pattern 1, run against Postgres below); the vote response carries the re-enriched article; cache policy per D-05/D-06 (Pattern 6) |
| FDBK-03 | Learned capped at ±20, never flips base sign, undo exact | `LEAST/GREATEST` cap plus a CASE sign clamp, probed on Postgres 18 (cap, sign clamp, ±50, and up/down/up equal to a single up); derived model gives exact undo |
| FDBK-04 | User sees the vote's effect ("Rust +2") | Service reads effective weights before and after the write in one transaction and returns `effects[]` with a `limit` flag (Pattern 3); client formatter (Code Examples) |
| FDBK-05 | No match → told, offered to create topic | `scored` + empty matched set in the response; pane line; `InterestsDialog` `draft` prop seeded into `TopicsSection` (Pattern 8) |
| FDBK-06 | Thumbs-down on multi-topic article → choose topics | `topicIds` on `PUT`, validated as a non-empty subset of matched topics; popover with keys that stop propagation (Pattern 7, Pitfall 5) |
| FDBK-07 | Topic editor shows base and learned separately | New read-only `GET /api/interest/topics/learned` from the same CTE, on its own query key (Pattern 4) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Spring Data JDBC, not JPA. No derived query methods; use `@Query` or JdbcClient text blocks. Entities use `org.springframework.data.annotation.Id` and `org.springframework.data.relational.core.mapping.Table`.
- Jackson 3: databind lives in `tools.jackson.databind.*`, but annotations stay in `com.fasterxml.jackson.annotation.*`.
- GlobalExceptionHandler mappings: `NotFoundException` → 404, `IllegalArgumentException` → 400, `IllegalStateException` → 409. Throw the right type from services. Messages are fixed text and never echo the submitted value (the T-03-10 convention in `InterestService`).
- `@WebMvcTest`, `@DataJdbcTest` and `@SpringBootTest` live in `org.springframework.boot.*.test.autoconfigure` packages. Controller tests use `@MockitoBean`.
- `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated; use `HttpStatus.valueOf(422)`.
- Frontend type-check: run `npx tsc -b` from `src/main/frontend/` (plain `tsc --noEmit` passes even with errors).
- Frontend conventions: API wrappers in `src/api/`, TanStack hooks in `src/hooks/`, Zustand stores in `src/stores/`, and vim-style shortcuts in `useKeyboardShortcuts`.
- `vi.mock` of a module is a full replacement. Adding an export means updating every mock that consumes it; prefer the `importOriginal` spread for new tests.
- `bootTestRun` and the test yaml gotcha: `DevProfileConfigTest` requires every key in main `application.yaml` to resolve to main's value in the test yaml plus the dev overlay. New `myfeeder.interest.blend.*` keys must be added to **both** yaml files.
- Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`.
- Interest Ranking section: the scoring write path's rubric-changed check must be kept (this phase doesn't touch it). `/api/interest` routes are listed in CLAUDE.md; new routes should be added there during the docs pass.
- User global: Gradle, never Maven. Non-trivial work goes on a feature branch. Keep changes surgical. Prefer the serena or LSP tools over grep for code navigation.
- GSD workflow: repo edits happen inside `/gsd-execute-phase`.

## Summary

This phase is almost entirely in-repo work on patterns that already exist. No new libraries, no migration and no external services are involved. The backend has three moving parts. (1) The `learned` CTE body replaces the stub in `InterestScoreQueries.blendCte`. I ran the exact SQL against Postgres 18. It gives the ±20 cap, the sign clamp, the ±50 range, narrowing, "zero picks move nothing", "unscored votes count nothing", and up→down→up equal to a single up. (2) A small `ArticleFeedbackStore` (JdbcClient, `ON CONFLICT` upsert) plus a `@Transactional` service method. It reads each matched topic's effective weight before the write, writes, reads again, and returns `{article, scored, effects[]}`. (3) A read-only per-topic learned endpoint for the editor.

The frontend is the bigger surface, and it's where the real risks are. Rapid `u`/`d` presses need serialized mutations and a client-side intent state, otherwise up, down, up can land out of order or compute the wrong toggle from a stale cache. The vote cache policy must follow D-05 outside Priority (invalidate) and D-06 inside Priority (patch only the voted row and set `rankingChanged`). The picker popover must stop its `1`–`9`, Enter and Esc keys from reaching the document-level shortcut handler (Esc there clears the selection). `u`, `d` and Shift+D need a modifier guard (Cmd+D is the browser bookmark key).

**Primary recommendation:** Build it in this order. First the backend CTE and constants, proven with `@DataJdbcTest` on the probe fixture. Then feedback endpoints that return server-computed before/after effects. Then one `useVoteFeedback` hook: `scope: { id: 'article-feedback' }`, a synchronous intent update, and the D-05/D-06 cache split. Then the pane buttons, picker, no-match line and display changes.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Learned adjustment and effective weight (cap, sign clamp, narrowing) | Database (SQL CTE in `InterestScoreQueries`) | — | Locked: single source of truth for sort, badge, breakdown and effects |
| Vote persistence (upsert, delete, picks) | API / Backend (`ArticleFeedbackStore` + service) | Database (FK cascades) | Transactional write plus a before/after read |
| Effect numbers (before/after per topic, limit flags) | API / Backend | Database | D-07: the client prints, never recomputes |
| Matched-topic set, validation of picks | API / Backend | — | `m_t > 0` from stored nouls; 400 on a bad pick |
| Toggle rule (same vote removes, other flips) | Browser / Client | API (absolute PUT/DELETE) | The key/button decides the target; the server stores absolute state |
| Cache policy (patch Priority vs invalidate lists) | Browser / Client (TanStack) | — | D-05/D-06 |
| Effect toast formatting (top 3, "+N more", one decimal) | Browser / Client | — | Presentation of server numbers |
| Picker popover, Shift+D, no-match line, draft prefill | Browser / Client | — | UI only |
| Topic editor learned display | Browser / Client | API (`GET /api/interest/topics/learned`) | Read-only server values |

## Standard Stack

### Core (all already in the project; nothing to install)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `JdbcClient` | via Spring Boot 4.0.8 | Feedback upsert/delete and CTE reads | Used by `InterestScoreQueries` and `ArticleScoreStore` today [VERIFIED: src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:6 `import org.springframework.jdbc.core.simple.JdbcClient;`] |
| PostgreSQL | `postgres:latest` (18.6 when probed) | CTE, `ON CONFLICT` | Testcontainers image [VERIFIED: src/test/java/org/bartram/myfeeder/TestcontainersConfiguration.java:16 `new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))`] |
| @tanstack/react-query | ^5.103.2 | Vote mutation, cache patch/invalidate, `scope` serialization | [VERIFIED: src/main/frontend/package.json `"@tanstack/react-query": "^5.103.2"`] |
| zustand | ^5.0.15 | Session UI state (picker open, interests draft) | [VERIFIED: package.json `"zustand": "^5.0.15"`] |
| React | ^19.3.0 | UI | [VERIFIED: package.json `"react": "^19.3.0"`] |
| Vitest + RTL | vitest 4.1.11, @testing-library/react ^16.3.3 | Frontend tests | [VERIFIED: `npx vitest --version` → `vitest/4.1.11`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Absolute `PUT`/`DELETE` feedback | Server-side "toggle" `POST` | Toggle needs no client state, but it isn't idempotent (a replayed request flips twice), and narrowing needs an absolute PUT anyway. Use absolute state. |
| `ArticleFeedbackStore` with JdbcClient | Spring Data `CrudRepository<ArticleFeedback, Long>` | The PK `article_id` is assigned, not generated, so `save()` treats a set id as not-new and issues an UPDATE, which affects 0 rows on insert unless the entity implements `Persistable`. The child table also has a composite key. JdbcClient with `ON CONFLICT` is simpler and matches `ArticleScoreStore`. |
| Separate `GET /api/interest/topics/learned` | `@Transient learned` fields on `InterestTopic` in `GET /topics` | Transient fields would also have to be filled on create/update responses, which `setQueryData` writes into the topics cache. Refreshing learned values would mean refetching the topics query, which 03 deliberately never does (research Pitfall 7, unsaved row edits). A sibling key is cleaner. |

**Installation:** none. No packages are added.

## Package Legitimacy Audit

This phase installs no external packages (backend or frontend), so the gate does not apply.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 key u/d/Shift+D ─┐        👍/👎 click ─┐       "Narrow…" / picker Apply ─┐
                  ▼                     ▼                                ▼
          useKeyboardShortcuts ──► useVoteFeedback(articleId) ◄── ReadingPane (FeedbackBar, NarrowPicker)
                                    │ 1. target = toggle(intent, key)       (intent read from ['article', id].feedback)
                                    │ 2. setQueryData(['article',id]) intent (sync, before mutate)
                                    │ 3. mutate, scope 'article-feedback'  (serial)
                                    ▼
             PUT /api/articles/{id}/feedback {vote, topicIds?}   |   DELETE /api/articles/{id}/feedback
                                    │
                                    ▼
             ArticleFeedbackService.vote/clear  (@Transactional)
                ├─ 404 if article missing; 400 on bad vote / bad picks (non-empty ⊆ matched)
                ├─ before = InterestScoreQueries.topicWeights(matchedIds)   ◄── blend CTE: learned → eff
                ├─ ArticleFeedbackStore.upsert/delete (+ child rows)
                ├─ after  = InterestScoreQueries.topicWeights(matchedIds)
                └─ article = ArticleService.findByIdWithBreakdown(id) (+ feedback)
                                    │
                                    ▼
             200 {article, scored, effects:[{topicId,name,before,after,baseWeight,learned,limit}]}
                                    │
             ┌──────────────────────┼──────────────────────────────┐
             ▼                      ▼                              ▼
   setQueryData(['article',id])   toast (formatEffects)      isPriority?
   (merge, keep newer intent)                                 ├─ yes: patchPriorityArticle(id,{interestScore})
                                                              │       + setRankingChanged(true)
                                                              └─ no:  invalidate ['articles'] + other ['article',n]
                                    + invalidate ['interest','learned'] (editor)
```

### Recommended Project Structure (new and changed files)
```
src/main/java/org/bartram/myfeeder/
├── repository/InterestScoreQueries.java     # learned CTE body, blend params, topicWeights(), base/learned on contributions
├── repository/ArticleFeedbackStore.java     # NEW: JdbcClient upsert/delete/read of article_feedback(+_topic)
├── service/ArticleFeedbackService.java      # NEW: vote/clear, validation, before/after effects
├── service/FeedbackResult.java              # NEW record: {article, scored, effects}
├── model/ArticleFeedback.java               # NEW record: {vote, narrowed, topics:[{id,name}]}
├── model/Article.java                       # + @Transient feedback (by-id + vote responses only)
├── model/InterestBreakdown.java             # Row gains baseWeight + learnedWeight (appended components)
├── controller/ArticleController.java        # PUT/DELETE /{id}/feedback
├── controller/FeedbackRequest.java          # NEW record: (Integer vote, List<Long> topicIds)
├── controller/InterestController.java       # GET /topics/learned
└── config/MyfeederProperties.java           # Blend.learnRate, Blend.learnedCap
src/main/frontend/src/
├── api/articles.ts                          # setFeedback / clearFeedback
├── types/index.ts                           # ArticleFeedback, FeedbackResult, TopicEffect; row baseWeight/learnedWeight
├── hooks/useFeedback.ts                     # NEW: useVoteFeedback (keeps useArticles mocks untouched)
├── utils/feedback.ts                        # NEW: nextVote(), formatEffects(), formatDelta()
├── components/FeedbackBar.tsx               # NEW: 👍/👎, "👎 X only", Narrow…, no-match line
├── components/NarrowPicker.tsx              # NEW: popover
├── components/WhyBreakdown.tsx, TopicRow.tsx, InterestsDialog.tsx, ReadingPane.tsx, ShortcutOverlay.tsx
├── hooks/useKeyboardShortcuts.ts            # u, d, Shift+D
└── stores/priorityStore.ts (or new feedbackStore.ts)  # narrowOpen, interestsDraft
```

### Pattern 1: The learned CTE (replaces the stub)

The current stub [VERIFIED: src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:198-212]:
```java
return "WITH learned AS (SELECT t.id AS topic_id, 0::double precision AS delta FROM interest_topic t), "
        + "eff AS (SELECT t.id, t.weight + COALESCE(l.delta, 0) AS w "
        + "FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id), "
        + "contrib AS (SELECT ts.article_id, ts.topic_id, ts.noul, "
        + "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, "
        + "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "
        + "FROM article_topic_score ts JOIN eff e ON e.id = ts.topic_id), "
```
It binds only `:profilePoints` today [VERIFIED: InterestScoreQueries.java:98 `.param("profilePoints", properties.getInterest().getBlend().getProfilePoints())`].

Replacement (the body I probed; column names `base`, `learned`, `w` are recommendations):
```sql
WITH learned AS (
  SELECT ts.topic_id, SUM(f.vote * GREATEST(0, (ts.noul - 0.5) * 2)) AS vote_sum
  FROM article_feedback f
  JOIN article_score fs ON fs.article_id = f.article_id AND fs.status = 'SCORED'
  JOIN article_topic_score ts ON ts.article_id = f.article_id
  WHERE NOT f.topics_narrowed
     OR EXISTS (SELECT 1 FROM article_feedback_topic ft
                WHERE ft.article_id = f.article_id AND ft.topic_id = ts.topic_id)
  GROUP BY ts.topic_id),
eff AS (
  SELECT t.id, t.weight AS base,
         CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0) AS learned_raw,
         LEAST(CAST(:learnedCap AS float8), GREATEST(-CAST(:learnedCap AS float8),
               CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0))) AS learned
  FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id),
eff2 AS (
  SELECT e.*, CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned))
                   WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned))
                   ELSE GREATEST(-50, LEAST(50, e.learned)) END AS w
  FROM eff e),
contrib AS (... JOIN eff2 e ON e.id = ts.topic_id  -- also select e.base, e.w - e.base AS learned_applied
```
- The `article_score` status join is defensive: topic rows are only written on the SCORED path [VERIFIED: src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java:112-145 — `writeScored` inserts `article_topic_score` only after the `'SCORED'` upsert returns 1]. It keeps the learned CTE's definition of "scored" identical to `blended`'s `WHERE s.status = 'SCORED'`.
- `50`/`-50` are the V6 weight bounds [VERIFIED: src/main/resources/db/migration/V6__interest_scoring.sql:16 `weight INTEGER NOT NULL DEFAULT 20 CHECK (weight BETWEEN -50 AND 50)`] and match `InterestService.MIN_WEIGHT = -50` / `MAX_WEIGHT = 50` [VERIFIED: src/main/java/org/bartram/myfeeder/service/InterestService.java:23-24].
- Every query built from `blendCte` must now bind `learnRate` and `learnedCap`: `priorityFirstPage`, `priorityPageAfter`, `displayScores`, and both statements in `breakdownInputs`. Use one private helper so none is missed (see Pitfall 1).

**Probe results (Postgres 18.6, 5 topics, all 25 articles matching every topic; Rust 20 m=.9, Politics −30 m=.6, Small 5 m=.8, Zero 0 m=.5, Big 45 m=1):** [VERIFIED: ran the CTE above in a throwaway `postgres:latest` container]
```
--- one up on a1                         --- up, down, up (after the final up)
 Rust     |  20 |  1.8 | w  21.8          Rust     |  20 |  1.8 | w  21.8   (identical to a single up)
 Politics | -30 |  1.2 | w -28.8          Politics | -30 |  1.2 | w -28.8
 Small    |   5 |  1.6 | w   6.6          ...
 Zero     |   0 |  1.0 | w   1.0
 Big      |  45 |  2.0 | w  47.0
--- 20 downs: learned capped, sign clamp, range
 Rust     |  20 | -20 (raw -36) | w   0     <- sign clamp (base>0 never below 0)
 Politics | -30 | -20 (raw -24) | w -50     <- range floor
 Small    |   5 | -20 (raw -32) | w   0     <- sign clamp
 Zero     |   0 | -20           | w -20     <- base 0 moves either way
 Big      |  45 | -20 (raw -40) | w  25
--- narrowed down to Politics only: Politics -1.2, every other topic 0
--- narrowed with zero child rows: all 0
--- vote on an unscored article: all 0
```
Note the semantics this confirms: a thumbs-**up** on an article that matched a **negative** topic moves that topic toward 0 ("less disliked"), bounded by the sign clamp. That is the R2 formula as written. Tell the UI-SPEC author so the copy doesn't surprise the user (Open Question 2).

### Pattern 2: Endpoints (absolute state)
```
PUT    /api/articles/{id}/feedback   consumes=application/json   body {"vote": 1 | -1, "topicIds": [..] | null}
DELETE /api/articles/{id}/feedback
→ 200 FeedbackResult {article, scored, effects[]}
```
- `vote` must be exactly 1 or -1, else `IllegalArgumentException` → 400 with fixed text. The DB CHECK is `vote IN (-1, 1)` [VERIFIED: V6__interest_scoring.sql:52 `vote SMALLINT NOT NULL CHECK (vote IN (-1, 1))`].
- `topicIds == null` means not narrowed: upsert with `topics_narrowed=false` and delete all child rows. This gives D-17 (flipping to 👍 clears narrowing) with no special case.
- `topicIds != null` must be non-empty (D-15) and a subset of the article's matched topics (`hinge > 0`, topic still exists). Otherwise 400. De-duplicate the ids. The server accepts narrowing in both directions (03 D-03); the UI only sends it on 👎.
- Missing article → `NotFoundException` → 404, the same as `PATCH /{id}` [VERIFIED: src/main/java/org/bartram/myfeeder/service/ArticleService.java:46-47 `.orElseThrow(() -> new NotFoundException("Article not found: " + id));`].
- `DELETE` with no vote is a no-op 200 (idempotent), with empty or zero-change effects.
- Require `consumes = MediaType.APPLICATION_JSON_VALUE` on PUT, the same CSRF reasoning as rescore [VERIFIED: src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java:25-26 `/** JSON-only, so a cross-site "simple" POST ... */ @PostMapping(value = "/rescore", consumes = MediaType.APPLICATION_JSON_VALUE)`]. DELETE is never a CORS "simple" method.

Upsert SQL (one statement plus child replace, in the service transaction):
```sql
INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (:id, :vote, :narrowed)
ON CONFLICT (article_id) DO UPDATE SET vote = EXCLUDED.vote, topics_narrowed = EXCLUDED.topics_narrowed;
DELETE FROM article_feedback_topic WHERE article_id = :id;
INSERT INTO article_feedback_topic (article_id, topic_id) VALUES (:id, :topicId);   -- per pick
```
Column names [VERIFIED: V6__interest_scoring.sql:50-61 `article_feedback (article_id BIGINT PRIMARY KEY ..., vote SMALLINT ..., topics_narrowed BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW())`; `article_feedback_topic (article_id BIGINT NOT NULL REFERENCES article_feedback(article_id) ON DELETE CASCADE, topic_id BIGINT NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE, PRIMARY KEY (article_id, topic_id))`]. Deleting the parent row cascades the picks, so `DELETE FROM article_feedback WHERE article_id = :id` is the whole "remove".

### Pattern 3: Before/after effects in one transaction
```java
@Transactional
public FeedbackResult vote(long articleId, int vote, List<Long> topicIds) {
    requireArticle(articleId);                                   // 404
    List<Matched> matched = queries.matchedTopics(articleId);    // hinge > 0, SCORED only
    boolean scored = queries.isScored(articleId);
    validate(vote, topicIds, matched);                           // 400s, fixed text
    Set<Long> ids = matched ids;
    Map<Long, TopicWeight> before = queries.topicWeights(ids);   // same learned/eff CTE
    store.upsert(articleId, vote, topicIds);
    Map<Long, TopicWeight> after = queries.topicWeights(ids);
    return new FeedbackResult(articleWithBreakdownAndFeedback(articleId), scored, effects(before, after));
}
```
- In Postgres (READ COMMITTED), statements inside one transaction see that transaction's own writes, so the `after` read reflects the upsert. `JdbcClient` joins the Spring-managed transaction. [ASSUMED — standard Spring `DataSourceTransactionManager` behavior; the integration test in Validation proves it]
- Only matched topics (`hinge > 0`) can change because of this article's vote, so they are the affected set. A narrowing change (all → Politics only) makes the un-picked topics revert, and they show as non-zero deltas. D-09 filters zero-delta topics unless `limit != NONE`.
- `TopicWeight {topicId, name, base, learned (capped), learnedRaw, w}`. Compute `limit` from the **after** state: `LEARNED_CAP` when `|learnedRaw| > cap`; `SIGN_CLAMP` when `base != 0` and `w == 0` and `base + learned` crossed zero; `WEIGHT_RANGE` when `|base + learned| > 50`; otherwise `NONE`. Return `before`/`after` rounded to 6 decimals (numeric `ROUND(...,6)`), the same convention as `exact`, so float noise such as `(0.95 − 0.5) × 2 = 0.8999999999999999` doesn't reach the client.
- Response `effects[]` item: `{topicId, name, before, after, baseWeight, learned, limit}`. `scored` is `false` when there is no SCORED `article_score` row (D-03 message). `scored && matched.isEmpty()` gives the D-18/D-19 "No topics matched".

### Pattern 4: Per-topic learned for the editor (FDBK-07)
`GET /api/interest/topics/learned` → `[{topicId, baseWeight, learned, effectiveWeight, limit}]`, built from the same `eff` CTE (a `topicWeights(null)` variant over all topics). There is no collision with `/topics/{id}`, because only PUT and DELETE are mapped there [VERIFIED: src/main/java/org/bartram/myfeeder/controller/InterestController.java:26-42 — `@GetMapping("/topics")`, `@PostMapping("/topics")`, `@PutMapping("/topics/{id}")`, `@DeleteMapping("/topics/{id}")`]. Frontend key `['interest', 'learned']`. Invalidate it on every vote and on topic create/update/delete (a base edit changes the effective weight). `TopicRow` looks up its own `id` in that list. Row state is never reseeded, which preserves the 03-06 "rows seed once per open" rule.

### Pattern 5: The vote hook (serialized, intent-first)
```ts
// hooks/useFeedback.ts (new file, so the full-replacement vi.mock('../hooks/useArticles') mocks stay valid)
export function useVoteFeedback() {
  const qc = useQueryClient()
  const isPriority = useMatch('/priority') !== null
  const mutation = useMutation({
    mutationKey: ['feedback'],
    scope: { id: 'article-feedback' },          // serial: later votes wait (isPaused) until earlier ones finish
    mutationFn: ({ id, vote, topicIds }: VoteVars) =>
      vote === 0 ? articlesApi.clearFeedback(id) : articlesApi.setFeedback(id, vote, topicIds),
    onSuccess: (res, vars) => { /* Pattern 6 cache policy + toast */ },
    onError: (_e, vars) => { /* roll back the intent: invalidate ['article', vars.id] */ },
  })
  /** Applies the toggle rule to the freshest intent, writes it synchronously, then mutates. */
  const press = (id: number, key: 1 | -1) => {
    const current = qc.getQueryData<Article>(['article', id])?.feedback?.vote ?? 0
    const vote = current === key ? 0 : key                     // same → remove, other → flip (FDBK-01)
    setIntent(qc, id, vote)                                    // sync setQueryData on ['article', id].feedback
    mutation.mutate({ id, vote })
  }
  return { press, narrow: (id: number, topicIds: number[]) => { setIntent(...); mutation.mutate({ id, vote: -1, topicIds }) } }
}
```
- TanStack v5 `scope: { id }` runs mutations with the same id in serial; queued ones sit in `isPaused: true` and resume automatically [CITED: github.com/tanstack/query docs/framework/react/guides/mutations.md, "Mutation Scopes"; source `mutationCache.ts` `canRun`/`runNext`]. A static scope id serializes all votes, which is fine for one user.
- Write the intent **synchronously in the handler**, not in `onMutate`. Whether `onMutate` of a scoped, paused mutation runs before the wait is not verified [ASSUMED], and the handler write is correct either way.
- In `onSuccess`, merge `res.article` into `['article', id]`, but keep the cached `feedback` while another feedback mutation is still pending (`qc.isMutating({ mutationKey: ['feedback'] }) > 1`). Otherwise an earlier response can overwrite a newer intent, and the next keypress computes the wrong toggle (Pitfall 3).
- The toggle source is `['article', id]`, the pane's by-id query. The keyboard hook already reads it via `useArticle(selectedArticleId)` [VERIFIED: src/main/frontend/src/hooks/useKeyboardShortcuts.ts:53-54 `const { data: fetchedArticle } = useArticle(selectedArticleId)` / `const currentArticle = (currentIndex >= 0 ? articles[currentIndex] : null) ?? fetchedArticle ?? null`]. For `u`/`d`, use `fetchedArticle` (it carries `feedback`), **not** `currentArticle`, which prefers the list row. If it hasn't loaded yet, do nothing.

### Pattern 6: Cache policy after a vote
- Always: `setQueryData(['article', id], merged)` from the response (the pane's badge, Why rows and feedback update instantly with no extra GET), `invalidateQueries(['interest','learned'])`, and the toast.
- **Priority (`useMatch('/priority')`)** — D-06: `patchPriorityArticle(qc, id, { interestScore: res.article.interestScore })` plus `usePriorityStore.getState().setRankingChanged(true)`. Don't invalidate other by-id article queries: other rows keep their served badge, and `refreshPriority` already invalidates every by-id query when the user re-ranks [VERIFIED: src/main/frontend/src/hooks/usePriorityArticles.ts:79-82 `await qc.resetQueries({ queryKey: PRIORITY_KEY })` / `predicate: (q) => q.queryKey[0] === 'article' && q.queryKey.length === 2`].
- `patchPriorityArticle` accepts only `{ read?: boolean; starred?: boolean }` today [VERIFIED: usePriorityArticles.ts:92-99 `patch: { read?: boolean; starred?: boolean }` ... `if (patch.starred !== undefined) fields.starred = patch.starred`]. Widen it to accept `interestScore?: number | null`, copying a key only when it is `!== undefined` (null is a real value).
- **Outside Priority** — D-05: `invalidateQueries(['articles'])`. Also invalidate the **other** by-id article queries (predicate `q.queryKey[0]==='article' && q.queryKey.length===2 && q.queryKey[1]!==id`). Otherwise an article opened within the 30s `staleTime` shows a pre-vote Why breakdown [VERIFIED: src/main/frontend/src/queryClient.ts:12 `queries: { staleTime: 30_000, retry: 1 }`]. The length-2 predicate leaves `['article', id, 'extracted']` alone.
- The `['priority']` key is never invalidated (locked).

### Pattern 7: Picker popover and keys
- Get matched topics from `article.interestBreakdown.rows` where `kind === 'TOPIC'`. Those are exactly the `hinge > 0` topics [VERIFIED: src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java:61-66 `if (t.hinge() > 0) { candidates.add(...) } else { nonMatchingTopics.add(t); }`], in server order, with `noul` for the match %. The "Narrow…" control shows when that count is ≥ 2 and the current vote is −1 (D-13).
- The initial checked set is `feedback.topics` ids ∩ matched ids when narrowed, else all matched (D-14). Apply is disabled when none are checked (D-15). Apply sends `PUT {vote:-1, topicIds}`.
- Keys: handle `1`–`9`, Enter and Escape in the popover's React `onKeyDown`, and call `e.preventDefault(); e.stopPropagation()`. Since React 17, handlers attach to the root container, so a React `stopPropagation()` stops the native event before `document` listeners [CITED: legacy.reactjs.org/blog/2020/08/10/react-v17-rc.html, "Changes to Event Delegation"]. The global shortcut handler is a `document` listener [VERIFIED: useKeyboardShortcuts.ts:208-210 `document.addEventListener('keydown', handleKeyDown)`], so it never sees them. Focus the popover container (tabIndex -1) on open, or the keys never reach it.
- Shift+D opens the picker through a session store flag (e.g. `narrowOpen` in `priorityStore`, or a new small `feedbackStore`), because the keyboard hook lives in `MainLayout` and the picker in `ReadingPane`.

### Pattern 8: "Create topic from article" draft (D-20)
- Add a session store field `interestsDraft: { description: string; weight: 20 | -20 } | null` and a way to open the dialog. `MainLayout` owns `interestsOpen` as local state [VERIFIED: src/main/frontend/src/App.tsx:78 `const [interestsOpen, setInterestsOpen] = useState(false)`; App.tsx:135 `<InterestsDialog open={interestsOpen} onClose={() => setInterestsOpen(false)} />`]. The pane is a sibling, so pass an `onCreateTopic` callback down `ReadingPane` props (like `onBoardClose`), or move the open flag into the store. Then `InterestsDialog` → `TopicsSection` takes an optional `draft` prop.
- Seed it in the `useState` initializer: `seedRows(topics)` plus a draft row `{ key: 'd-1', id: null, name: '', description, weightText: String(weight), weight, saved: null }`, and start `draftCounter` at 1. The current draft shape [VERIFIED: src/main/frontend/src/components/InterestsDialog.tsx:445-458 `key: \`d-${draftCounter.current}\`, id: null, name: '', description: '', weightText: '20', weight: 20, saved: null`]. Clear the store draft on close.
- Truncate the prefilled title to 500 characters [VERIFIED: InterestService.java:27 `public static final int MAX_DESCRIPTION_CHARS = 500;`].
- Focus the name input: add an `autoFocus` / `focusName` prop to `TopicRow` for that row only.
- If there are already 25 topics [VERIFIED: InterestService.java:22 `public static final int MAX_TOPICS = 25;`], don't add the draft; show the at-max state instead of a draft that can only fail with a 400.
- The prefilled draft counts as dirty (it has description text), so closing without saving shows the discard confirm. That's expected; mention it in the UI-SPEC.

### Anti-Patterns to Avoid
- **Writing `interest_topic.weight` on a vote**: forbidden (locked). Runaway, sign flips, no exact undo.
- **Client-side weight math** for the toast, the Why row or the editor: print server numbers only. `after − before` and one-decimal formatting are the only client arithmetic.
- **Invalidating or refetching `['priority']`**: it reloads every loaded page and re-ranks under the cursor.
- **Deciding the toggle from the list row** (`currentArticle`), which lags the vote. Use the by-id intent.
- **A CrudRepository for `article_feedback`**: the assigned PK makes `save()` do an UPDATE on insert (see Alternatives).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Serializing rapid votes | A hand-written promise queue / in-flight flag | TanStack `useMutation({ scope: { id } })` | Built-in, pauses and resumes, cooperates with `isMutating` |
| Keeping popover keys from global shortcuts | A global "picker open" check in every `case` | React `onKeyDown` + `stopPropagation()` on the focused popover | React 17+ root delegation stops the document listener |
| Learned accumulator | `weight += delta` bookkeeping, a per-vote delta table | Derived SQL aggregate over `article_feedback` | Exact undo, idempotent, retunable (R2) |
| Upsert | Select-then-insert/update in Java | `INSERT ... ON CONFLICT (article_id) DO UPDATE` | Atomic, one round trip; the repo already uses it (`ArticleScoreStore`) |
| Breakdown sum rule | New rounding for learned rows | Existing `ScoreBreakdowns.apportion` | The rows' `exact` already uses effective `w`; the sum rule is unchanged |

**Key insight:** every number the user sees must come from the one `eff` CTE. Every new read (effects, Why rows, editor) is a projection of that CTE, never a parallel formula.

## Common Pitfalls

### Pitfall 1: An unbound or untyped blend parameter
**What goes wrong:** a blend query fails with "No value supplied for the SQL parameter 'learnRate'", or Postgres rejects the unary minus.
**Why it happens:** `blendCte` is shared by 5 statements, each binding params by hand (today only `profilePoints`). An untyped `-$n` is ambiguous in Postgres. Probe output from Postgres 18.6 [VERIFIED: ran against `postgres:latest`]:
```
psql:/probe.sql:64: ERROR:  operator is not unique: - unknown
LINE 1: PREPARE untyped AS SELECT LEAST($2, GREATEST(-$2, $1 * 1.5::...
HINT:  Could not choose a best candidate operator. You might need to add explicit type casts.
```
**How to avoid:** wrap the constants in `CAST(:learnRate AS float8)` / `CAST(:learnedCap AS float8)`, the same style as the cursor binds [VERIFIED: InterestScoreQueries.java:117 `CAST(:cursorScore AS float8), CAST(:cursorDate AS timestamptz), CAST(:cursorId AS bigint)`]. Bind all blend params through one helper (e.g. a `Map` passed to `JdbcClient.StatementSpec.params(Map)` [ASSUMED API name — check against Spring Framework 7 javadoc], or a small `bind(spec)` method).
**Warning signs:** `InterestScoreQueriesTest` goes red on every test at once.

### Pitfall 2: The new blend keys break `DevProfileConfigTest`
**What goes wrong:** `devOverlayResolvesEveryMainKeyToMainsValue` fails.
**Why it happens:** the test walks every key in main `application.yaml` and requires the test yaml or the dev overlay to give the same raw value [VERIFIED: src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java:86-89 `for (String name : main.getPropertyNames()) { Object effective = dev.containsProperty(name) ? dev.getProperty(name) : test.getProperty(name); assertThat(String.valueOf(effective)).as(name).isEqualTo(String.valueOf(main.getProperty(name)));`].
**How to avoid:** add `learn-rate: 2` and `learned-cap: 20` under `myfeeder.interest.blend` in **both** `src/main/resources/application.yaml` and `src/test/resources/application.yaml` (the test yaml mirrors `blend: profile-points: 100` today [VERIFIED: src/test/resources/application.yaml:41-42 `blend:` / `profile-points: 100`]). Add the defaults to `MyfeederProperties.Interest.Blend`, which today holds only `private int profilePoints = 100;` [VERIFIED: src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java:52-56]. Use `double learnRate = 2` so Phase 7 can tune fractionally, and `int learnedCap = 20`.

### Pitfall 3: Out-of-order or mis-toggled rapid votes
**What goes wrong:** pressing `u d u` fast ends as "down", "none", or "up" with the wrong toast. Success criterion 1 fails.
**Why it happens:** (a) concurrent fetches can be processed out of order by the server. (b) The toggle is computed from a cache that the earlier response overwrote.
**How to avoid:** `scope` serialization, a synchronous intent write, and an `onSuccess` merge that keeps the newer intent (Pattern 5). Test it with a deferred-promise mock of `articlesApi.setFeedback` that resolves out of order.
**Warning signs:** a flaky "up, down, up ends as single up" test.

### Pitfall 4: The Why row's "(base +learned)" doesn't add up
**What goes wrong:** `Big 100% × 50 (45 +20 learned)` — the parts don't equal the effective weight.
**Why it happens:** the capped `learned` (±20) can still be cut by the ±50 range or the sign clamp.
**How to avoid:** for the Why row (D-11), send `learnedWeight = w − base` (the **applied** part) computed in SQL, plus `baseWeight`. Show the capped `learned` and the `limit` note only in the editor and the toast. Both new `Row` fields are appended components. The current row is `Row(String kind, Long topicId, String name, Integer levelIndex, Double noul, Double hinge, Double weight, BigDecimal exact, long points)` [VERIFIED: src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java:23-24]. The factory call sites in `ScoreBreakdownsTest` (2) and `ArticleControllerTest` (3) must be updated.
**Warning signs:** a row reads `20 +4 learned` next to a weight of 24, but a sign-clamped topic reads `5 −20 learned` next to 0.

### Pitfall 5: Picker keys leak to the global handler
**What goes wrong:** Esc in the picker also clears the selected article (the global `Escape` case calls `setSelectedArticle(null)` [VERIFIED: useKeyboardShortcuts.ts:199-201 `case 'Escape': setSelectedArticle(null); setSearchQuery('')`]), and Enter moves focus to the pane.
**Why it happens:** the checkboxes are `INPUT`s, so the global handler ignores most keys, but on Escape it blurs the input and returns [VERIFIED: useKeyboardShortcuts.ts:59-63 `if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') { if (e.key === 'Escape') (e.target as HTMLElement).blur(); return }`]. Keys pressed with focus on the popover's buttons reach every case.
**How to avoid:** Pattern 7 (`stopPropagation` in the popover `onKeyDown`, focus on open). Test that Esc closes the picker and the selection is unchanged.

### Pitfall 6: Cmd+D votes down
**What goes wrong:** the browser's bookmark shortcut (Cmd/Ctrl+D) also records a 👎.
**Why it happens:** the handler switches on `e.key` with no modifier check (existing keys have the same flaw).
**How to avoid:** in `u`, `d` and `D`, return early when `e.metaKey || e.ctrlKey || e.altKey`. Shift+D is `case 'D'` with `e.shiftKey`, following the existing Shift+A pattern [VERIFIED: useKeyboardShortcuts.ts:176-181 `case 'A': // Explicit route guard ... if (e.shiftKey && selectedFeedId && !callbacks.isPriority)`]. Don't touch the other keys (surgical).

### Pitfall 7: Stale "matched" and picks after Re-score, topic delete, or feed delete
**What goes wrong:** learned adjustments shift without any vote.
**Why it happens:** the model is derived. Re-score deletes unread `article_score` rows, which cascade to the nouls [VERIFIED: V6__interest_scoring.sql:37-39 comment `Child of article_score, so deleting a score row (Phase 4 "Re-score unread") also removes its nouls.` and `REFERENCES article_score(article_id) ON DELETE CASCADE`]. Votes don't mark read (D-04), so many voted articles are unread and lose their contribution until re-scored with new nouls. Deleting a feed cascades its articles [VERIFIED: src/main/resources/db/migration/V1__initial_schema.sql:20 `feed_id BIGINT NOT NULL REFERENCES feed(id) ON DELETE CASCADE`] and so their feedback. Deleting a picked topic can leave a narrowed vote with zero picks.
**How to avoid:** this is accepted behavior of the locked derived model. Document it. In the pane, a narrowed vote whose picks are all gone renders as "👎 no topics" with Narrow… still available, and the picker's initial set intersects picks with the current matched topics.

### Pitfall 8: Breaking existing tests and mocks
- `ArticleControllerTest` is `@WebMvcTest(ArticleController.class)` with `@MockitoBean` per dependency [VERIFIED: src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java:36-43 — `ArticleService`, `RaindropService`, `ArticleExtractionService`, `PriorityService`]. A new constructor dependency (the feedback service) needs another `@MockitoBean`.
- Frontend full-replacement mocks: `vi.mock('../hooks/useArticles', ...)` in `ReadingPane.test.tsx`, `ArticleList.test.tsx` and `FeedPanel.test.tsx`, and `vi.mock('../api/articles', ...)` in `useKeyboardShortcuts.test.ts`, which lists `getById, list, updateState, markRead, counts`. Put the vote hook in a new `hooks/useFeedback.ts`. `ReadingPane.test.tsx` then needs a mock for it, and the keyboard tests must add `setFeedback`/`clearFeedback` to the `articlesApi` mock.

## Code Examples

### Effect toast formatter (D-07..D-10)
```ts
// utils/feedback.ts — prints server numbers; only after − before and rounding happen here
export type Limit = 'NONE' | 'LEARNED_CAP' | 'SIGN_CLAMP' | 'WEIGHT_RANGE'
export interface TopicEffect { topicId: number; name: string; before: number; after: number
  baseWeight: number; learned: number; limit: Limit }

/** "+2.0", "−4.0", "+0.0" (U+2212); rounds first so −0.04 prints +0.0, never −0.0. */
export function formatDelta(d: number): string {
  const r = Math.round(d * 10) / 10
  return r < 0 ? `−${Math.abs(r).toFixed(1)}` : `+${r.toFixed(1)}`
}

export function formatEffects(prefix: string, effects: TopicEffect[]): string {
  const shown = effects
    .map((e) => ({ e, d: e.after - e.before }))
    .filter(({ e, d }) => Math.round(d * 10) !== 0 || e.limit !== 'NONE')
    .sort((a, b) => Math.abs(b.d) - Math.abs(a.d))
  const parts = shown.slice(0, 3).map(({ e, d }) => `${e.name} ${formatDelta(d)}${limitNote(e)}`)
  const more = shown.length > 3 ? ` · +${shown.length - 3} more` : ''
  return [prefix, ...parts].filter(Boolean).join(' · ') + more
}
// limitNote: LEARNED_CAP → " (learned at max +20)" / " (learned at min −20)"; SIGN_CLAMP → " (can't cross 0)"; exact copy → UI-SPEC
```
The existing `formatSigned(0)` returns `"0"` [VERIFIED: src/main/frontend/src/utils/interest.ts:4-9 `if (n > 0) return \`+${s}\`` / `if (n < 0) return \`−${s}\`` / `return s`], so the D-07 "+0.0" needs the new `formatDelta`, not `formatSigned`.

### Toggle rule (pure, unit-testable)
```ts
/** FDBK-01: same key removes (0), the other key flips. */
export const nextVote = (current: -1 | 0 | 1, key: -1 | 1): -1 | 0 | 1 => (current === key ? 0 : key)
```

### Shortcut overlay entries
Append to the `shortcuts` array, whose entries look like `{ key: 'i', action: 'Toggle score breakdown' }` [VERIFIED: src/main/frontend/src/components/ShortcutOverlay.tsx:15]: `{ key: 'u / d', action: 'Thumbs up / down (again to remove)' }` and `{ key: 'Shift+D', action: 'Choose topics to penalize' }`. Exact copy goes in the UI-SPEC.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `learned` stub `0::double precision AS delta` | Derived η·Σ vote·m_t, capped, sign-clamped | This phase | Every badge, sort and breakdown reflects votes |
| ARCHITECTURE Pattern 5 constants η=0.1, maxDelta=1.5, `LEAST(3, GREATEST(-3, …))` | η=2 points, L=20 points, ±50 range plus sign clamp | Superseded by R1 (research) | Use R1 values; ignore the ARCHITECTURE sketch's constants |
| FEATURES T6 "store per-topic deltas" | Store only the vote (plus picks); derive deltas | R2 | No delta table |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Inside one `@Transactional` method, a JdbcClient read after the upsert sees the upsert (Spring-managed connection, Postgres READ COMMITTED) | Pattern 3 | Before and after would be equal and every toast would read +0.0. The integration test catches it. |
| A2 | `onMutate` timing for scoped, paused mutations is unverified, so the intent is written in the handler instead | Pattern 5 | None if the recommendation is followed |
| A3 | `JdbcClient.StatementSpec.params(Map)` exists in Spring Framework 7 | Pitfall 1 | Minor: fall back to a `bind(spec)` helper with chained `.param()` |
| A4 | Up-votes softening negative topics is intended R2 behavior (the formula says so; the user never discussed the UX) | Pattern 1 | The user may expect up-votes to leave dislikes alone. Confirm in UI-SPEC or discussion (Open Question 2). |
| A5 | Endpoint paths `PUT/DELETE /api/articles/{id}/feedback` and `GET /api/interest/topics/learned` (discretion) | Patterns 2 and 4 | Naming only |

## Open Questions (RESOLVED)

1. **Copy for votes on SKIPPED or exhausted-FAILED articles**
   - What we know: D-03 says "Saved — counts once this article is scored". A SKIPPED article (no judgeable text) is never scored, and neither is an exhausted FAILED one.
   - What's unclear: whether the message should differ.
   - RESOLVED: keep `scored: boolean` in v1 and the D-03 copy for every unscored case. Planner may add `scoreStatus` only if the UI-SPEC asks for it.
2. **Up-vote on an article matching a negative topic**
   - What we know: R2 moves every matched topic by `vote × m_t`, so Politics −30 becomes −28.8 after a 👍 (probe above). The toast will print "Politics +1.2".
   - What's unclear: whether the user reads that as a bug.
   - RESOLVED: follow the locked formula. The UI-SPEC copy should make the direction readable, e.g. by listing the topic's new effective weight.
3. **Why-row number format once weights are fractional**
   - What we know: `WhyBreakdown` prints `formatSigned(row.weight)` with 0 decimals today [VERIFIED: src/main/frontend/src/components/WhyBreakdown.tsx:43 `` `${row.name}  ${Math.round(row.hinge * 100)}% × ${formatSigned(row.weight)}` ``]. D-11's example uses whole numbers.
   - RESOLVED: when `learnedWeight` is 0, keep today's exact output (the Phase 5 tests stay green). Otherwise print the weight and the learned part with 1 decimal (D-08). Final format belongs to the UI-SPEC.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers (`./gradlew test`) | ✓ | 29.8.0 | — |
| Java | Backend build | ✓ | OpenJDK 25.0.4 | — |
| Node | Frontend build/tests | ✓ | v26.10.0 | — |
| Vitest | Frontend tests | ✓ | 4.1.11 | — |
| Postgres image | Tests | ✓ (pulled `postgres:latest`, 18.6) | 18.6 | — |

**Missing dependencies with no fallback:** none
**Missing dependencies with fallback:** none

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot test slices + Testcontainers (backend); Vitest 4.1.11 + RTL (frontend) |
| Config file | `build.gradle.kts`; `src/main/frontend/vitest.config.ts` (jsdom, `src/test/setup.ts`) |
| Quick run command | `./gradlew test --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"` / `cd src/main/frontend && npx vitest run src/utils/feedback.test.ts` |
| Full suite command | `./gradlew test` and `cd src/main/frontend && npm test && npx tsc -b` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FDBK-03 | Cap ±20, sign clamp, ±50, zero-base both ways, narrowed-only, zero picks, unscored vote = 0 | repository (`@DataJdbcTest`) | `./gradlew test --tests "*InterestScoreQueriesTest"` | ✅ extend (new learned cases; current fixture has no feedback so existing raws are unchanged) |
| FDBK-01/03 | up→down→up equals a single up (weights and badge); delete restores exactly | repository + API integration | `./gradlew test --tests "*FeedbackApiIntegrationTest"` | ❌ Wave 0 |
| FDBK-02 | Vote changes badge/breakdown on the by-id response; no Jev call (no `JevApiClient` interaction) | API integration (`@SpringBootTest` + MockMvc, as `PriorityApiIntegrationTest`) | same | ❌ Wave 0 |
| FDBK-04 | `effects[]` before/after/limit (cap and sign-clamp cases) | service unit (Mockito) + integration | `./gradlew test --tests "*ArticleFeedbackServiceTest"` | ❌ Wave 0 |
| FDBK-05 | `scored=false` for unscored; scored with no matches gives empty effects and "No topics matched" line | service + RTL | `npx vitest run src/components/FeedbackBar.test.tsx` | ❌ Wave 0 |
| FDBK-06 | 400 on empty or non-matched `topicIds`; narrowed picks only; flip to up clears narrowing | service + controller (`@WebMvcTest`, 415 on non-JSON) | `./gradlew test --tests "*ArticleControllerTest"` | ✅ extend |
| FDBK-06 | Picker: 1–9 toggle, Enter applies, Esc cancels without clearing selection, Apply disabled on none | RTL | `npx vitest run src/components/NarrowPicker.test.tsx` | ❌ Wave 0 |
| FDBK-01 | `u`/`d`/Shift+D; Cmd+D ignored; rapid u,d,u with out-of-order resolution ends up | hook test | `npx vitest run src/hooks/useKeyboardShortcuts.test.ts src/hooks/useFeedback.test.ts` | ✅ extend / ❌ Wave 0 |
| FDBK-02 | Priority: only the voted row patched and `rankingChanged` set; `['priority']` never invalidated; outside Priority `['articles']` invalidated | hook test (as `usePriorityArticles.test.ts`) | `npx vitest run src/hooks/useFeedback.test.ts` | ❌ Wave 0 |
| FDBK-04 | Formatter: top 3, "+N more", one decimal, +0.0 cap note, no −0.0 | unit | `npx vitest run src/utils/feedback.test.ts` | ❌ Wave 0 |
| FDBK-07 | `/topics/learned` values; TopicRow shows base and learned separately | controller + RTL | `./gradlew test --tests "*InterestControllerTest"`; `npx vitest run src/components/TopicRow.test.tsx` | ✅ extend |
| D-11 | Why row with learned part; rows still sum to the badge | unit (`ScoreBreakdownsTest`) + RTL (`WhyBreakdown.test.tsx`) | `./gradlew test --tests "*ScoreBreakdownsTest"` | ✅ extend |
| Config | New blend keys mirrored | unit | `./gradlew test --tests "*DevProfileConfigTest"` | ✅ (must stay green) |

### Sampling Rate
- **Per task commit:** the targeted `--tests` class or `npx vitest run <file>`
- **Per wave merge:** `./gradlew test` + `npm test` + `npx tsc -b`
- **Phase gate:** full suite green before `/gsd-verify-work`; manual UAT of the toast and picker in `bootTestRun`

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java` — FDBK-01/02/03 end to end (seed with its own feed/topic name prefix; clean up in `@BeforeEach`, like `PriorityApiIntegrationTest`)
- [ ] `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java` — validation and effects
- [ ] `src/main/frontend/src/utils/feedback.test.ts`, `src/hooks/useFeedback.test.ts`, `src/components/FeedbackBar.test.tsx`, `src/components/NarrowPicker.test.tsx`
- Integration tests share one container across classes. The learned CTE sums **all** feedback rows per topic, so each test class must only assert on topics it seeded (topics are name-prefixed per class today).

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user app with no auth layer (unchanged) |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes | Service-level checks: `vote ∈ {−1, 1}`, `topicIds` a non-empty de-duplicated subset of matched topics, path id exists (404). Fixed-text messages that never echo input. DB CHECK `vote IN (-1, 1)` as a backstop. |
| V6 Cryptography | no | — |
| V13 API / CSRF | yes | `PUT` with `consumes = application/json` (a cross-site simple request is refused with 415, following rescore); `DELETE` always preflights; no CORS config added |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via topicIds or the path | Tampering | JdbcClient named params only; scope fragments stay compile-time constants (existing `InterestScoreQueries` rule) |
| Cross-site vote forgery | Tampering | JSON-only PUT; DELETE is non-simple |
| Oversized `topicIds` list | DoS | Reject when larger than the matched count (at most 25 topics), before any SQL |
| Error message echo | Information disclosure | Fixed-text `IllegalArgumentException` messages |

## Sources

### Primary (HIGH confidence)
- Codebase, read this session: `InterestScoreQueries.java`, `ArticleScoreStore.java`, `ArticleService.java`, `ScoreBreakdowns.java`, `InterestBreakdown.java`, `Article.java`, `ArticleController.java`, `InterestController.java`, `InterestService.java`, `MyfeederProperties.java`, `GlobalExceptionHandler.java`, `InterestRescoreController.java`, `V6__interest_scoring.sql`, `V1__initial_schema.sql`, `DevProfileConfigTest.java`, frontend `useArticles.ts`, `usePriorityArticles.ts`, `priorityStore.ts`, `useInterest.ts`, `queryClient.ts`, `Toast.tsx`, `ReadingPane.tsx`, `WhyBreakdown.tsx`, `ScoreRow.tsx`, `InterestsDialog.tsx`, `TopicRow.tsx`, `useKeyboardShortcuts.ts`, `ShortcutOverlay.tsx`, `App.tsx`, `utils/interest.ts`, `types/index.ts`, `api/*.ts`
- Postgres 18.6 probe of the learned/eff CTE and the untyped-param failure (throwaway container, output pasted above)
- `.planning/research/SUMMARY.md` §R1, §R2, §R6; `ARCHITECTURE.md` Pattern 5; `PITFALLS.md` 10, 11, 19; `FEATURES.md` T6, T7, D2, D3, T11

### Secondary (MEDIUM confidence)
- Context7 `/tanstack/query`: mutation `scope` serialization (guides/mutations.md; `mutationCache.ts` `canRun`/`runNext`)

### Tertiary (LOW confidence per the seam, but a primary source)
- React 17 RC blog (legacy.reactjs.org/blog/2020/08/10/react-v17-rc.html): root-container delegation, and `stopPropagation` stopping document listeners

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Nothing new; versions read from package.json and the probe.
- Architecture: HIGH. The CTE was executed; patterns mirror existing code (`ArticleScoreStore`, `patchPriorityArticle`, rescore JSON guard).
- Pitfalls: HIGH for the SQL, config and test-mock items (verified in code or the probe); MEDIUM for the rapid-vote ordering (reasoned from TanStack docs; needs the out-of-order test).

**Research date:** 2026-09-26
**Valid until:** 2026-10-26 (stable in-repo stack)
