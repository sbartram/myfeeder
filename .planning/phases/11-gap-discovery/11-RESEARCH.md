# Phase 11: Gap Discovery - Research

**Researched:** 2026-10-01
**Domain:** Spring Boot 4 / Spring Data JDBC read query + one dismissal write; React 19 / TanStack Query 5 dialog section
**Confidence:** HIGH (everything is in-repo; no new packages, no new migration, no Jev)

## Summary

The phase is a read-only derived list plus one write table that already exists (V7 `topic_suggestion_dismissal`). The code allows every locked decision (D-01..D-18). The two constraints that shape the design are guard tests, not product rules:

1. `V7EngagementMigrationTest.rankingSqlReadsEngagementButNotDismissals` does a **raw file-text** check: `InterestScoreQueries.java` must not contain the string `topic_suggestion_dismissal` anywhere, comments included. So the suggestions SQL **cannot live in `InterestScoreQueries`**. It goes in a new repository class.
2. `InterestCalibrationReplaySqlTest` pins `blendCte(...)`, `LEARNED_CTE`, `INTEREST_SCORE` and the set of blend bind names (`driverPassesEveryBlendParameter`). Changing `InterestScoreQueries` invites drift-guard churn.

**Primary recommendation:** Leave `InterestScoreQueries` byte-for-byte unchanged. Use two steps:

- **Step 1:** a new `TopicSuggestionStore` (JdbcClient) returns every candidate that passes the D-08 predicate (window, SCORED, no vote, no dismissal, best noul < near-miss).
- **Step 2:** a new `TopicSuggestionService` calls the existing `InterestScoreQueries.displayScores(ids)`. That is the exact badge `InterestBadge` shows, from the same blend. The service then sorts by badge ascending, then latest engagement descending, then id descending, keeps 10 and reports `total`.

This reuses the blend with zero duplication, never touches the ranking SQL and never calls Jev.

`InterestService.createTopic` is already `@Transactional`, so D-13 means:
- appending `Long sourceArticleId` to `TopicRequest`
- one `INSERT … SELECT … ON CONFLICT DO NOTHING` inside that transaction

On the frontend, render the new section **inside `TopicsSection`'s returned fragment, after its `<section>`**. It then sits visually below Topics and above the Re-score footer (D-01), and it has direct access to `rows`, `atMax` and a widened `addDraft(draft?)`. No state lifting and no imperative ref are needed.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Gap predicate (window, SCORED, no vote, no dismissal, near-miss) | Database / Storage | API (`TopicSuggestionStore`) | Pure set logic over 6 tables; one SQL statement |
| Badge score for ordering | Database (existing blend) | API (`TopicSuggestionService` sort) | Must be the same number `InterestBadge` shows; reuse `displayScores` |
| Order, cap 10, total | API / Backend | — | Small in-memory sort after the badge lookup; avoids touching blend SQL |
| Near-miss constant + startup validation | API (config) | — | `MyfeederProperties implements Validator` precedent |
| TOPIC_CREATED atomic with topic insert | API (`InterestService` @Transactional) | Database | Same JDBC transaction for Spring Data JDBC `save` and `JdbcClient` |
| Dismiss (DISMISSED) | API | Database | Bodyless idempotent PUT, 404 for unknown article |
| Section render, "Draft added", at-max | Browser / Client | — | Derived from `TopicsSection` local `rows` |
| Freshness (refetch on open, invalidations) | Browser / Client (TanStack Query) | — | `staleTime: 0` + `invalidateAfterLearnedChange` |

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (settled earlier; do not reopen)
- Phase 8 D-13: `topic_suggestion_dismissal(article_id BIGINT PK → article ON DELETE CASCADE, reason CHECK IN ('DISMISSED','TOPIC_CREATED'), created_at)` already exists in V7. **No new migration in this phase.**
- Phase 8 D-14: a handled suggestion stays handled. Deleting a topic created from a suggestion does not bring the suggestion back.
- Phase 3 D-20 draft shape: `TopicDraft = { description: title.trim().slice(0, 500), weight: 20 | -20 }`.
- Phase 3 D-21: articles are never re-judged. That is why the dismissal row, not a derived rule, clears a suggestion.
- "Matched" has one definition: a stored `article_topic_score.noul > 0.5` (hinge > 0), the same predicate as `InterestScoreQueries` matched topics and the frontend `matchedTopics`.
- Phase 10 / roadmap: add the suggestions query key to the shared post-engagement reaction (`afterEngagement` in `hooks/engagementReaction.ts`), so suggestions engaged since the dialog last opened appear (SC-1).
- DTO/enum rule: append, never rename. Kind names and SQL aliases must pass the replay write-keyword check.

#### List placement & rows
- **D-01:** The "Suggested topics" section sits **below Topics** in the Interests dialog, before the Re-score footer.
- **D-02:** The section is **hidden when there are no suggestions**, with no empty-state note.
- **D-03:** There is no cold-start or Jev-unconfigured gating. Rows require SCORED articles, so those states yield an empty list anyway.
- **D-04:** Each row shows the **title (plain text, not clickable)**, the **feed name** and the article's **score badge** (the existing `InterestBadge` with tiers), plus **Create topic** and **Dismiss** actions. Engagement kind and date are not shown.

#### Ordering, window & cap
- **D-05:** Order by the **badge score ascending** (lowest first, "surprise" order), the same blended score the badge shows. Ties go to the most recent engagement.
- **D-06:** Only articles whose **latest engagement is within the last 30 days** are eligible. The window is measured on `article_engagement.created_at`.
- **D-07:** Show at most **10** rows. The header shows a total when more qualify, e.g. `Suggested topics (10 of 23)`, so the response carries a total count alongside the items.

#### What counts as a gap
- **D-08:** An article is a suggestion when all of these are true:
  - it has at least one `article_engagement` row of **any kind** (opens included) inside the D-06 window
  - its `article_score.status = 'SCORED'` (unscored, FAILED and SKIPPED are never suggested)
  - it has **no `article_feedback` row** (any vote, 👍 or 👎, excludes it; a 👍 with no match already gets the reading-pane notice)
  - it has **no `topic_suggestion_dismissal` row**
  - its **best noul across all its topic rows is below the near-miss threshold**
- **D-09:** The near-miss threshold is **0.35**: an article whose best noul is ≥ 0.35 is left out. It's a yaml constant (e.g. `myfeeder.interest.suggestions.near-miss`), applied at query time so Phase 12 can tune it. Follow the existing constant rules:
  - identical literals in main and test yaml (dev overlay untouched)
  - startup validation in `MyfeederProperties` (planner picks the exact bounds, e.g. 0 < x ≤ 0.5)
- **D-10:** A match to **any** topic counts as covered, including a negative-weight topic. The near-miss check spans all topics regardless of weight sign.
- **D-11:** A SCORED article with **no topic rows at all** is a gap (best noul treated as 0).

#### Create & dismiss flow
- **D-12:** Create topic adds a **+20** draft (`description = title.trim().slice(0, 500)`). The `TopicDraft` weight type stays `20 | -20`. The draft gains an optional `sourceArticleId`.
- **D-13:** TOPIC_CREATED is written **atomically with the topic save**. `POST /api/interest/topics` accepts an appended optional `sourceArticleId` and inserts the dismissal row (reason `TOPIC_CREATED`) in the same transaction as the topic insert. — **Reversibility:** costly — `sourceArticleId` becomes part of the topic-create request contract used by two UI paths.
- **D-14:** The reading-pane `FeedbackNotice` "Create topic from article" also passes `sourceArticleId`, so a topic saved from it marks the article handled. There is one draft path.
- **D-15:** Create topic adds the draft to the **already-open** Interests dialog. `TopicsSection.addDraft` must accept a `TopicDraft`; today a draft is seeded only once, at open.
- **D-16:** While a suggestion's draft is unsaved, its row stays in the list, **disabled and marked "Draft added"**, so it can't be added twice. It disappears when the topic saves. If the unsaved draft row is removed, the suggestion becomes active again.
- **D-17:** Dismiss is **immediate**: no confirm, no undo toast, and no un-dismiss endpoint. It writes reason `DISMISSED`.
- **D-18:** At 25 topics (`TOPICS_MAX`), a suggestion's Create topic is **disabled with the tooltip** "You have 25 topics, the maximum." Dismiss still works.

### Claude's Discretion
- Route shapes, e.g. `GET /api/interest/suggestions` → `{items, total}` and `POST` or `PUT /api/interest/suggestions/{articleId}/dismiss` (204, idempotent; 404 for an unknown article), plus any JSON-only or CSRF-style guard consistent with the other interest routes.
- Service and query placement (`TopicSuggestionService` + a read-only query, or a method next to `InterestScoreQueries`). The query must not touch Jev.
- Conflict behavior when a dismissal row already exists at topic save (e.g. `ON CONFLICT DO NOTHING`, keeping the first reason), and what happens when `sourceArticleId` points at a missing article (ignore vs 404), as long as the topic save itself isn't blocked by a stale suggestion.
- The query key name (e.g. `['interest', 'suggestions']`) and which mutations invalidate it: engagement (via `afterEngagement`), votes, topic create and delete, dismiss, and Re-score.
- Refetch-on-open behavior so the list is fresh each time the dialog opens.
- Styling of rows and the "Draft added" state, and how the feed name and badge are laid out.

### Deferred Ideas (OUT OF SCOPE)
- ENG-F3: the "matched no topic" notice after engaging (roadmap-deferred)
- An undo or un-dismiss for dismissed suggestions (rejected for now; dismissal is permanent)
- Showing the engagement kind and date on suggestion rows (not chosen)

**Specifics (UI contract, no UI-SPEC this phase):** header copy `Suggested topics (10 of 23)` when capped; disabled row state reads "Draft added"; at-max tooltip "You have 25 topics, the maximum."; "surprise" ordering is the point.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GAP-01 | Interests has a "Suggested topics" section listing engaged, SCORED articles that matched no topic | Candidate SQL (Code Examples §1). `GET /api/interest/suggestions` → `{items,total}`. `SuggestedTopics` rendered inside `TopicsSection`'s fragment. `staleTime: 0` refetch on open plus invalidation in `invalidateAfterLearnedChange` (SC-1). |
| GAP-02 | Create a topic from a suggestion (prefilled draft), which removes the suggestion | `addDraft(draft?)`; `TopicRowState.sourceArticleId`; `TopicInput.sourceArticleId`; `TopicRequest` appended `Long sourceArticleId`; `InterestService.createTopic` (already `@Transactional`) calls `TopicSuggestionStore.handle(id, TOPIC_CREATED)`. Persists across reloads (SC-3). |
| GAP-03 | Dismiss a suggestion permanently | `PUT /api/interest/suggestions/{articleId}/dismissal` → 204, idempotent, 404 unknown. Row survives later engagement because engagement never deletes dismissal rows (SC-4). |
| GAP-04 | Near-miss articles not suggested | `COALESCE(MAX(ts.noul), 0) < CAST(:nearMiss AS float8)` over **all** topic rows (D-10/D-11). `myfeeder.interest.suggestions.near-miss: 0.35` validated `0 < x <= 0.5`. |
| GAP-05 | Suggestions never call Jev | No Jev dependency in `TopicSuggestionStore`, `TopicSuggestionService` or `InterestService`. The integration test reuses the `@MockitoBean JevApiClient` + `@AfterEach verify(jevApiClient, never()).judge(any(), any())` pattern from `EngagementApiIntegrationTest`. The frontend test asserts there is no `POST /api/interest/preview` (SC-5). |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Spring Data JDBC, not JPA. Write custom SQL with `JdbcClient` or `@Query`; derived query methods are not available. `@Id` comes from `org.springframework.data.annotation`.
- Jackson 3: databind is `tools.jackson.databind.*`. Annotations stay in `com.fasterxml.jackson.annotation.*`.
- `GlobalExceptionHandler` mappings:
  - `NotFoundException` → 404
  - `IllegalArgumentException` → 400 (fixed text, never echo input)
  - `IllegalStateException` → 409
  - Throw the right type from services; controllers have no try/catch.
- Interest constants live in yaml and apply at query time. `MyfeederProperties implements Validator` refuses startup with fixed text. Main and test yaml carry identical literals; the dev overlay is untouched. `DevProfileConfigTest` enforces this.
- DTO and status fields are appended and never renamed. Kind names and SQL aliases must pass the replay write-keyword regex `(?i)\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\b`. Checked: `TOPIC_CREATED`, `DISMISSED`, `best_noul`, `engaged_at`, `near_miss` and `suggestion` all pass (run with python `re` this session).
- The engagement-capturing services (`ArticleService.updateState`, `BoardService.addArticle`, `RaindropService`) must stay transaction-free. **This phase does not touch them.** `InterestService.createTopic` is a different path and is already `@Transactional`.
- Feature branch for non-trivial work (previous phases used `gsd/phase-NN-<slug>`). Merges use `--no-ff`. Push only on request.
- Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`. No test may activate `dev`.
- Frontend type-check: `npx tsc -b` (not `tsc --noEmit`).
- Frontend `vi.mock` of a module is full replacement. New exports from `hooks/useInterest` need every full mock updated. Only `PriorityBanner.test.tsx` mocks `../hooks/useInterest`, and it already uses `importOriginal`, so it is safe.
- When a plan fixes an item tracked elsewhere, mark it resolved in the same commit.
- Docs: update root `CLAUDE.md` in this phase:
  - Package Structure: new classes
  - API endpoints / Interest Ranking → Routes: `GET /suggestions`, `PUT /suggestions/{articleId}/dismissal`
  - Constants: `suggestions.near-miss`

  Do not hand-edit `openwiki/`.
- Read-only research constraints (this session): no prod, no Jev, no branch changes.

## Standard Stack

No new libraries. Everything uses what is on the classpath or in `package.json`.

### Core (existing, verified in repo)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `JdbcClient` | Boot 4.0.8 BOM | Candidate query + dismissal insert | Same pattern as `ArticleEngagementStore`, `ArticleFeedbackStore` [VERIFIED: src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java] |
| Spring `@Transactional` | Boot 4.0.8 | Topic insert + TOPIC_CREATED in one transaction | `InterestService.createTopic` already annotated [VERIFIED: InterestService.java:68-69 `@Transactional` / `public InterestTopic createTopic(String name, String description, Integer weight)`] |
| TanStack Query | ^5.103.2 | `useTopicSuggestions`, `useDismissSuggestion` | Existing hooks pattern [VERIFIED: src/main/frontend/package.json:18] |
| React | ^19.3.0 | Section component | [VERIFIED: package.json:20] |
| Vitest | 4.1.11 (installed) | Frontend tests | [VERIFIED: `npx vitest --version` → `vitest/4.1.11`] |

**Installation:** none.

## Package Legitimacy Audit

No external packages are installed in this phase. The audit is not applicable.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
Interests dialog opens (InterestsDialogBody mounts)
   └─ TopicsSection → <SuggestedTopics rows atMax onCreate>
         └─ useTopicSuggestions()  ['interest','suggestions'] staleTime 0 → refetch on every mount
               │  GET /api/interest/suggestions
               ▼
   TopicSuggestionController ──► TopicSuggestionService.list()
                                   ├─ TopicSuggestionStore.candidates(cutoff = now-30d, nearMiss)
                                   │     SQL: article_engagement ⨝ article ⨝ feed ⨝ article_score(SCORED)
                                   │          NOT EXISTS article_feedback, NOT EXISTS topic_suggestion_dismissal
                                   │          COALESCE(MAX(noul) over ALL topic rows, 0) < nearMiss
                                   │          GROUP BY article HAVING MAX(created_at) > cutoff
                                   ├─ InterestScoreQueries.displayScores(ids)   (unchanged blend → badge)
                                   └─ sort badge ASC, engagedAt DESC, id DESC → items = first 10, total = n
               ◄─ {items:[{articleId,title,feedTitle,interestScore}], total}

Row "Create topic" ─► TopicsSection.addDraft({description: title.trim().slice(0,500), weight: 20, sourceArticleId})
   └─ draft TopicRow (unsaved) → suggestion row shows "Draft added" (disabled)
        └─ Save → POST /api/interest/topics {name, description, weight, sourceArticleId}
              └─ InterestService.createTopic  @Transactional
                    count<25 → validate → topicRepository.save → store.handle(sourceArticleId, TOPIC_CREATED)
                    (INSERT … SELECT FROM article … ON CONFLICT DO NOTHING; missing article → 0 rows, no error)
              onSuccess → invalidate ['interest','suggestions'] (+ existing status/learned)

Row "Dismiss" ─► PUT /api/interest/suggestions/{articleId}/dismissal (bodyless)
   └─ TopicSuggestionService.dismiss: existsById or 404 → store.handle(id, DISMISSED) → 204

Engagement / vote / Forget ─► invalidateAfterLearnedChange(qc,…) ─► also invalidates ['interest','suggestions']
Topic delete / Re-score ─► invalidate ['interest','suggestions']
```

### Recommended file layout

```
src/main/java/org/bartram/myfeeder/
├── model/SuggestionDismissalReason.java        # NEW enum { DISMISSED, TOPIC_CREATED }
├── repository/TopicSuggestionStore.java        # NEW JdbcClient: candidates(cutoff, nearMiss), handle(id, reason)
├── service/TopicSuggestionService.java         # NEW list() (sort/cap/total), dismiss(id)
├── service/TopicSuggestion.java                # NEW record (articleId, title, feedTitle, interestScore)
├── service/TopicSuggestions.java               # NEW record (items, total)
├── service/InterestService.java                # MOD createTopic(+ Long sourceArticleId)
├── controller/TopicSuggestionController.java   # NEW @RequestMapping("/api/interest")
├── controller/TopicRequest.java                # MOD append Long sourceArticleId
├── controller/InterestController.java          # MOD pass request.sourceArticleId()
└── config/MyfeederProperties.java              # MOD Interest.Suggestions.nearMiss + SUGGESTIONS_INVALID
src/main/resources/application.yaml + src/test/resources/application.yaml   # MOD identical near-miss literal

src/main/frontend/src/
├── api/interest.ts                 # MOD TopicSuggestion(s) types, getSuggestions, dismissSuggestion, TopicInput.sourceArticleId?
├── hooks/useInterest.ts            # MOD useTopicSuggestions, useDismissSuggestion, invalidations
├── hooks/engagementReaction.ts     # MOD invalidateAfterLearnedChange adds ['interest','suggestions']
├── components/InterestsDialog.tsx  # MOD TopicDraft.sourceArticleId?, addDraft(draft?), SuggestedTopics
├── components/TopicRow.tsx         # MOD TopicRowState.sourceArticleId?, send it on create only
├── components/FeedbackNotice.tsx   # MOD draft carries sourceArticleId: article.id
└── App.css                         # MOD .interests-suggestion* styles
```

A separate `TopicSuggestionController` is recommended over adding routes to `InterestController`. Every controller slice test is scoped (`@WebMvcTest(InterestController.class)` at `InterestControllerTest.java:30`), so a new controller with a new service dependency does not force a new `@MockitoBean` into `InterestControllerTest`. `InterestRescoreController` already shares `/api/interest` this way. [VERIFIED: grep of `@WebMvcTest` in src/test]

### Pattern 1: Reuse the badge without touching the blend (two-step)
**What:** The store returns the candidate rows. The service calls `displayScores(ids)`, which builds `blendCte(IDS_SCOPE)` + `INTEREST_SCORE` and binds every blend constant through the private `blendSql`.

**Why:** `displayScores` is "The badge for each of `ids`, from the same blend as the Priority sort. Id-scoped, not unread-scoped (D-18): a read article keeps its badge. Only SCORED ids appear in the map" [VERIFIED: InterestScoreQueries.java:208-224]. That is exactly the D-05 sort key. Suggestions are often read articles, so the id scope (not the unread scope) is required.

The alternative is a single SQL statement inside a new class built on `InterestScoreQueries.blendCte(...)` (package-private static). It is rejected for two reasons:
- The new class would have to re-bind all six blend parameters, because `learnedSql`/`blendSql` are private. That creates a second binding site that drifts when a blend constant is added.
- `blendCte`'s scope contract says "Only this class's scope constants are ever passed as `scope`" [VERIFIED: InterestScoreQueries.java:368].

**Observation for the planner (not a decision change):** every suggestion has best noul < 0.35 < 0.5, so all its hinges are 0. Its blended raw is therefore just the profile part, `profilePoints × profile_score / profile_max_level` (0 when no profile question was asked). "Badge ascending" in practice means "weakest profile match first". With an empty profile, every suggestion is 0 and the D-05 tie-break (most recent engagement) decides the order. Learned weights never move a suggestion's badge.

### Pattern 2: Atomic TOPIC_CREATED inside the existing transaction
`createTopic` already runs count → validate → `topicRepository.save` in one `@Transactional`. Append the dismissal insert **after** the save, so a 400 (26th topic, bad name) writes nothing. Spring Data JDBC and `JdbcClient` both use the same `DataSource`, so the insert joins the transaction. `ArticleFeedbackStore.upsert` already mixes multiple JdbcClient statements in one `@Transactional`. [ASSUMED — proven by the planned spy-throw test below]

### Pattern 3: Section lives inside TopicsSection's fragment
`TopicsSection` owns `rows` in local state, seeded once, and never overwritten by refetches (Pitfall 7, comment at InterestsDialog.tsx:440-445). Rendering `<SuggestedTopics>` as a sibling *after* `TopicsSection`'s `</section>` (return `<>…</>`) gives it `rows`, `atMax` and `addDraft` directly. The visual order stays notices → Profile → Topics → **Suggested topics** → Re-score footer (D-01), because `RescoreFooter` follows `TopicsSection` in `InterestsDialogBody` (InterestsDialog.tsx:96-103).

### Anti-Patterns to Avoid
- **Putting the suggestions SQL, or even a comment naming the dismissal table, in `InterestScoreQueries.java`.** It breaks `V7EngagementMigrationTest.rankingSqlReadsEngagementButNotDismissals`: `assertThat(queries).doesNotContain("topic_suggestion_dismissal")` is a raw file-text check [VERIFIED: V7EngagementMigrationTest.java:210-218].
- **Adding a blend bind parameter or a new blend copy.** `driverPassesEveryBlendParameter` pins the exact set `"profilePoints", "learnRate", "learnedCap", "engagementOpenWeight", "engagementSaveWeight", "engagementCap"` [VERIFIED: InterestCalibrationReplaySqlTest.java:101-111]. The near-miss constant must **not** go under `blend`.
- **Deriving "handled" from topics** (e.g. "articles judged against every current topic"). Rejected in research (ARCHITECTURE Pattern 4). The dismissal row is the only clearing mechanism.
- **`onClick={addDraft}` after widening `addDraft(draft?)`.** React passes the MouseEvent as `draft`. Change to `onClick={() => addDraft()}` (InterestsDialog.tsx:573).
- **Wiring Preview to suggestions.** The draft row's Preview stays an explicit click (TopicRow comment: "mutate runs only from this handler, never from an effect or a timer").

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Badge score for ordering | A second copy of the blend or a profile-only formula | `InterestScoreQueries.displayScores(ids)` | Single source of truth. The badge and the order can never disagree |
| "Matched" check | A new noul rule | Best noul < near-miss, with validation `near-miss <= 0.5` (so any `noul > 0.5` match is always excluded) | One definition of matched |
| Idempotent insert | Read-then-insert | `INSERT … ON CONFLICT (article_id) DO NOTHING` | Precedent in `ArticleEngagementStore.record` |
| Article existence for TOPIC_CREATED | `existsById` then insert (two statements, and it throws on a stale id) | `INSERT … SELECT a.id … FROM article a WHERE a.id = :articleId` | A missing article inserts 0 rows with no FK error, so the topic save is never blocked |
| Freshness on open | Manual `refetch()` in an effect | `staleTime: 0` on the query (refetch on mount) | TanStack refetches stale queries "when new instances of the query mount" [CITED: tanstack.com/query/v5/docs/framework/react/guides/important-defaults] |

## Common Pitfalls

### Pitfall 1: The dismissal table name leaks into `InterestScoreQueries.java`
**What goes wrong:** `V7EngagementMigrationTest` fails.
**How to avoid:** Keep all suggestion SQL in `TopicSuggestionStore.java`, and don't mention the table in `InterestScoreQueries` Javadoc.
**Warning sign:** a `doesNotContain("topic_suggestion_dismissal")` failure.

### Pitfall 2: Near-miss above 0.5 would let matched articles through
The near-miss test is `best < nearMiss`. With `nearMiss = 0.7`, an article that matched at noul 0.6 would be suggested. The validation bound `0 < near-miss <= 0.5` removes that. At exactly 0.5, an article with noul 0.5 (hinge 0, not matched) is excluded as a near miss, which is correct. NaN fails both comparisons and is refused.

### Pitfall 3: Shared Testcontainers DB pollutes global list assertions
**What goes wrong:** `@SpringBootTest` classes share one container per cached context and never roll back. The suggestions list is global, so another class's engaged, scored, unmatched articles can appear in it and push a seeded row past the cap of 10.

**How to avoid:**
- Prove the predicate, the cap and the total in a `@DataJdbcTest` (rolled back). Start it with `DELETE FROM article_score; DELETE FROM article; DELETE FROM interest_topic` so the fixture is the whole table, as `InterestScoreQueriesEngagementTest.setUp` does.
- In the API integration test, assert only on seeded ids (`$.items[*].articleId` contains / doesNotContain). Seed with `profile_score NULL` (badge 0) and fresh engagement so seeded rows sort first.

Test classes run sequentially: no `maxParallelForks` in `build.gradle.kts:97-102`.

### Pitfall 4: "Draft added" flicker between save and refetch
**What goes wrong:** `markSaved` currently rebuilds the row object without carrying extra fields (InterestsDialog.tsx:495-504). After a save, the row loses `sourceArticleId`, so the suggestion becomes active again until the suggestions refetch lands. A fast double click could then add a second draft.

**How to avoid:**
- Preserve `sourceArticleId` in `markSaved`: `updateRow(key, (r) => ({ …, sourceArticleId: r.sourceArticleId }))`.
- Hide a suggestion whose `articleId` is on a **saved** row (`id !== null`).
- Show "Draft added" when it is on an **unsaved** row (`id === null`).
- Removing the unsaved row (Discard) re-enables the suggestion with no extra code (D-16).

### Pitfall 5: `addDraft` receives a click event
See Anti-Patterns. TypeScript will not catch it if the parameter type is `TopicDraft | undefined` and the handler is passed bare: React's `MouseEventHandler` parameter is not assignable, so `tsc -b` *should* error. Fix it at the call site anyway.

### Pitfall 6: Existing frontend tests that pin exact payloads or call sequences
- `ReadingPane.test.tsx:351`: `expect(onCreateTopic).toHaveBeenCalledWith({ description: 'Rust async runtimes', weight: -20 })` must gain `sourceArticleId: 1`. Verify the mock article id.
- `InterestsDialog.test.tsx`: unrouted URLs return 404 from the fetch stub. One test uses `createQueryClient()` (retry 1) and asserts the exact calls after a click (`expect(calls.slice(before)…).toEqual(['POST /api/interest/preview'])`, ~line 761). A retried `GET /api/interest/suggestions` could land in that window. Add a default `route('GET', '/api/interest/suggestions', () => ({ status: 200, body: { items: [], total: 0 } }))` to `beforeEach`.
- Keep `sourceArticleId` **out** of the JSON when undefined. `JSON.stringify` drops `undefined`, so existing POST body assertions stay equal.

### Pitfall 7: Engagement `created_at` is first-insert time per kind
`record` is `ON CONFLICT (article_id, kind) DO NOTHING`, and `repeatedOpenKeepsOneRowAndItsFirstCreatedAt` proves the first `created_at` is kept. "Latest engagement" (D-06) is therefore the most recent *first* engagement of any kind. Re-opening a 40-day-old article does not bring it back into the window, but starring it (a new kind) does. This is consistent with D-06 as written. Document it in the store Javadoc.

### Pitfall 8: Two-statement race (candidates then badges)
Under READ COMMITTED, a Re-score can delete a candidate's SCORED row between the two statements. `displayScores` then omits it. Drop candidates with no badge and compute `total` after that filter. Don't throw.

### Pitfall 9: `InterestService` gains a constructor dependency
`InterestServiceTest` uses `@InjectMocks` with constructor injection. With no `@Mock TopicSuggestionStore`, Mockito passes `null`. That is fine for the existing tests, because `sourceArticleId` is null and the store is never touched. New tests must add `@Mock private TopicSuggestionStore suggestionStore;`. `InterestControllerTest` stubs `createTopic("Rust", "Rust lang", null)` (3 args, lines 90 and 103). If the controller calls a 4-arg method, update those stubs to the 4-arg form with `isNull()`/`null`.

## Code Examples

### §1 Candidate SQL (TopicSuggestionStore)

V7 values quoted verbatim [VERIFIED: V7__engagement.sql:13-17]:

```
CREATE TABLE topic_suggestion_dismissal (
    article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (reason IN ('DISMISSED', 'TOPIC_CREATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Also quoted:
- `status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED'))` [VERIFIED: V6__interest_scoring.sql:25]
- `noul DOUBLE PRECISION NOT NULL CHECK (noul BETWEEN 0 AND 1)` [VERIFIED: V6:41]
- `feed.title TEXT NOT NULL`, `article.title TEXT NOT NULL` [VERIFIED: V1__initial_schema.sql]
- engagement kinds `('OPEN_ORIGINAL', 'STAR', 'BOARD', 'RAINDROP')` [VERIFIED: V7:6]. The suggestion query ignores kind (D-08: any kind).

```java
// repository/TopicSuggestionStore.java  (NEW; the only class that reads topic_suggestion_dismissal)
/** One candidate per article; engaged_at = the article's latest engagement (first insert per kind). */
static final String CANDIDATES = "SELECT a.id, a.title, f.title AS feed_title, MAX(g.created_at) AS engaged_at "
        + "FROM article_engagement g "
        + "JOIN article a ON a.id = g.article_id "
        + "JOIN feed f ON f.id = a.feed_id "
        + "JOIN article_score s ON s.article_id = a.id AND s.status = 'SCORED' "
        + "WHERE NOT EXISTS (SELECT 1 FROM article_feedback fb WHERE fb.article_id = a.id) "
        + "AND NOT EXISTS (SELECT 1 FROM topic_suggestion_dismissal d WHERE d.article_id = a.id) "
        // D-10/D-11: every topic row regardless of weight sign; no rows → 0
        + "AND COALESCE((SELECT MAX(ts.noul) FROM article_topic_score ts WHERE ts.article_id = a.id), 0) "
        + "< CAST(:nearMiss AS float8) "
        + "GROUP BY a.id, a.title, f.title "
        + "HAVING MAX(g.created_at) > :cutoff";

public record Candidate(long articleId, String title, String feedTitle, Instant engagedAt) {}

public List<Candidate> candidates(Instant cutoff, double nearMiss) {
    return jdbc.sql(CANDIDATES)
            .param("cutoff", Timestamp.from(cutoff))      // same binding style as ArticleScoreStore (:cutoff)
            .param("nearMiss", nearMiss)
            .query((rs, n) -> new Candidate(rs.getLong("id"), rs.getString("title"),
                    rs.getString("feed_title"), rs.getTimestamp("engaged_at").toInstant()))
            .list();
}

/** Idempotent; keeps the first reason. A missing article inserts nothing and never throws. True when inserted. */
public boolean handle(long articleId, SuggestionDismissalReason reason) {
    return jdbc.sql("INSERT INTO topic_suggestion_dismissal (article_id, reason) "
                    + "SELECT a.id, CAST(:reason AS text) FROM article a WHERE a.id = :articleId "
                    + "ON CONFLICT (article_id) DO NOTHING")
            .param("articleId", articleId)
            .param("reason", reason.name())
            .update() == 1;
}
```

The `CAST(:reason AS text)` and `CAST(:nearMiss AS float8)` follow the LEARNED_CTE style. They remove any PgJDBC parameter-type inference question in an `INSERT … SELECT` select list. [ASSUMED that it's needed; harmless either way]

Indexes: `article_engagement` PK `(article_id, kind)`, `article_topic_score` PK `(article_id, topic_id)`, and the PKs on `article_feedback`/dismissal make the correlated lookups index probes. The window check scans `article_engagement` (no `created_at` index). That is acceptable at ~20k rows (the latency test scale). Add an optional assertion in a latency-style test if wanted. Do not add a migration (locked: no new migration).

### §2 Service: order, cap, total

```java
// service/TopicSuggestionService.java (NEW)
public static final int MAX_SUGGESTIONS = 10;   // D-07
public static final int WINDOW_DAYS = 30;        // D-06 (Java constant; only near-miss is yaml per D-09)

public TopicSuggestions list() {
    List<Candidate> candidates = store.candidates(Instant.now().minus(Duration.ofDays(WINDOW_DAYS)),
            properties.getInterest().getSuggestions().getNearMiss());
    Map<Long, Integer> badges = scoreQueries.displayScores(candidates.stream().map(Candidate::articleId).toList());
    List<TopicSuggestion> all = candidates.stream()
            .filter(c -> badges.containsKey(c.articleId()))            // Pitfall 8
            .sorted(Comparator.<Candidate>comparingInt(c -> badges.get(c.articleId()))
                    .thenComparing(Candidate::engagedAt, Comparator.reverseOrder())
                    .thenComparing(Candidate::articleId, Comparator.reverseOrder()))
            .map(c -> new TopicSuggestion(c.articleId(), c.title(), c.feedTitle(), badges.get(c.articleId())))
            .toList();
    return new TopicSuggestions(all.stream().limit(MAX_SUGGESTIONS).toList(), all.size());
}

public void dismiss(long articleId) {
    if (!articleRepository.existsById(articleId)) {
        throw new NotFoundException("Article not found: " + articleId);   // same text pattern as ArticleService.recordOpen
    }
    store.handle(articleId, SuggestionDismissalReason.DISMISSED);
}
```

`displayScores` returns `Map.of()` without SQL for an empty collection [VERIFIED: InterestScoreQueries.java:213-216]. Accept the injectable `Clock`/`Instant` style only if tests need it. Simpler: the store test passes `cutoff` directly, and the service test mocks the store.

### §3 Topic create with sourceArticleId (D-13)

```java
// controller/TopicRequest.java — append, never rename. Current: [VERIFIED: TopicRequest.java:4]
//   public record TopicRequest(String name, String description, Integer weight) {}
public record TopicRequest(String name, String description, Integer weight, Long sourceArticleId) {}
// PUT /topics/{id} ignores sourceArticleId.

// InterestService (MOD)
@Transactional
public InterestTopic createTopic(String name, String description, Integer weight, Long sourceArticleId) {
    if (topicRepository.count() >= MAX_TOPICS) { throw new IllegalArgumentException("A maximum of 25 topics is allowed"); }
    // … existing validate + build …
    InterestTopic saved = topicRepository.save(topic);
    if (sourceArticleId != null) {
        suggestionStore.handle(sourceArticleId, SuggestionDismissalReason.TOPIC_CREATED); // stale id → 0 rows, no error
    }
    return saved;
}
```

Optionally keep the 3-arg `createTopic(name, description, weight)` as a delegating overload, so the ~15 `InterestServiceTest`/`ArticleScoringFlowTest` call sites stay untouched. If you do, annotate it `@Transactional` too: self-invocation bypasses the proxy, so only the externally called method's annotation counts.

Conflict policy: `ON CONFLICT DO NOTHING` keeps the first reason. A DISMISSED-then-created article stays DISMISSED, and it is hidden either way. Missing article: **ignore** (0 rows, 201 for the topic). D-13 requires that a stale suggestion never blocks the save.

### §4 Dismiss route

```java
@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class TopicSuggestionController {
    private final TopicSuggestionService service;

    @GetMapping("/suggestions")
    public TopicSuggestions suggestions() { return service.list(); }

    /** Bodyless and idempotent. PUT is never a CORS simple request, so no content-type guard is needed. */
    @PutMapping("/suggestions/{articleId}/dismissal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable Long articleId) { service.dismiss(articleId); }
}
```

Why PUT and not POST: a bodyless POST is a CORS "simple request", which a cross-site form can send. The project's answer for POST is the JSON-only `consumes` + `{"confirm": true}` (rescore). The project's answer for bodyless idempotent writes is PUT: `PUT /api/articles/{id}/engagement/open` carries the comment "PUT is never a CORS simple request, so no content-type guard is needed" [VERIFIED: ArticleController.java:103-111]. A non-numeric id gives 400 (proved for the engagement route by `openWithANonNumericIdIs400`).

### §5 Near-miss constant (D-09)

```yaml
# src/main/resources/application.yaml AND src/test/resources/application.yaml (identical literal), under myfeeder.interest:
    # D-09: gap discovery near-miss; an engaged article whose best noul (any topic) is >= this is not suggested. Checked at startup (0 < near-miss <= 0.5). Phase 12 tunes.
    suggestions:
      near-miss: 0.35
```

```java
// MyfeederProperties (MOD). Existing pattern [VERIFIED: MyfeederProperties.java:16-17, 31-36]:
//   static final String ENGAGEMENT_INVALID = "myfeeder.interest.blend.engagement must be cap 0 (disabled), "
//           + "or 0 <= open-weight < save-weight < 1 and 0 < cap < learned-cap";
//   ... errors.reject("engagement", ENGAGEMENT_INVALID);
static final String SUGGESTIONS_INVALID = "myfeeder.interest.suggestions.near-miss must be above 0 and at most 0.5";

@Override
public void validate(Object target, Errors errors) {
    MyfeederProperties p = (MyfeederProperties) target;
    Interest.Blend blend = p.getInterest().getBlend();
    if (!blend.getEngagement().isValid(blend.getLearnedCap())) { errors.reject("engagement", ENGAGEMENT_INVALID); }
    if (!p.getInterest().getSuggestions().isValid()) { errors.reject("suggestions", SUGGESTIONS_INVALID); }
}

// inside Interest:
private Suggestions suggestions = new Suggestions();
@Data
public static class Suggestions {
    /** D-09: best noul at or above this leaves an article out of Suggested topics. Phase 12 tunes it. */
    private double nearMiss = 0.35;
    /** 0 < nearMiss <= 0.5 keeps every matched (noul > 0.5) article excluded; NaN fails. */
    public boolean isValid() { return 0 < nearMiss && nearMiss <= 0.5; }
}
```

Key: `myfeeder.interest.suggestions.near-miss`. It is a sibling of `blend`, not inside it, so `driverPassesEveryBlendParameter` and the replay are unaffected.

`DevProfileConfigTest.devOverlayResolvesEveryMainKeyToMainsValue` makes every main key resolve, through the test yaml, to main's raw value [VERIFIED: DevProfileConfigTest.java]. So adding the key to main without the identical test literal fails that test. Leave `application-dev.yaml` untouched.

`MyfeederPropertiesValidationTest` additions:
- `defaultsStartAndBindTheNearMiss` (0.35)
- `shippedMainYamlStarts` (already loads main yaml; add a near-miss assert)
- refused: `0`, `-0.1`, `0.51`, `NaN`
- starts: `0.5`, `0.01`
- fixed text never echoes the value (mirror `refusalTextIsFixed`)

### §6 Frontend skeletons

```ts
// api/interest.ts (MOD). Current TopicInput [VERIFIED: api/interest.ts:64-68]: { name: string; description: string; weight: number }
export interface TopicInput { name: string; description: string; weight: number; sourceArticleId?: number }
export interface TopicSuggestion { articleId: number; title: string; feedTitle: string; interestScore: number }
export interface TopicSuggestions { items: TopicSuggestion[]; total: number }
//   getSuggestions: () => apiGet<TopicSuggestions>('/interest/suggestions'),
//   dismissSuggestion: (articleId: number) => apiPut<void>(`/interest/suggestions/${articleId}/dismissal`),
// apiPut sends no body/content-type when body is undefined; parseBody('') → undefined for the 204.
```

```ts
// hooks/useInterest.ts (MOD)
export const SUGGESTIONS_KEY = ['interest', 'suggestions'] as const

/** Refetched on every Interests open (staleTime 0); hidden by the caller when empty or failed (D-02). */
export function useTopicSuggestions() {
  return useQuery({ queryKey: SUGGESTIONS_KEY, queryFn: interestApi.getSuggestions, staleTime: 0 })
}

/** Immediate, permanent (D-17). Errors use the global toast (z-index 200 sits above the dialog's 100). */
export function useDismissSuggestion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (articleId: number) => interestApi.dismissSuggestion(articleId),
    onSuccess: (_r, articleId) => {
      qc.setQueryData<TopicSuggestions>(SUGGESTIONS_KEY, (old) =>
        old && { items: old.items.filter((s) => s.articleId !== articleId), total: Math.max(0, old.total - 1) })
      void qc.invalidateQueries({ queryKey: SUGGESTIONS_KEY })
    },
  })
}
// Add `void qc.invalidateQueries({ queryKey: ['interest', 'suggestions'] })` to:
//   useCreateInterestTopic.onSuccess, useDeleteInterestTopic.onSuccess, useRescoreUnread.onSuccess,
//   and engagementReaction.ts invalidateAfterLearnedChange (covers votes via useFeedback:66 and every
//   afterEngagement caller incl. Forget). Not needed: useUpdateInterestTopic, useSaveInterestProfile
//   (stored nouls/profile scores don't change; suggestion badges have hinge 0).
```

The invalidation from engagement mostly marks an inactive query stale (the dialog is modal, so it is closed while the user engages). Invalidation "marks stale… queries currently being rendered… will be refetched in the background" [CITED: tanstack.com/query/v5/docs/framework/react/guides/query-invalidation]. The refetch-on-open in `staleTime: 0` is what actually delivers SC-1. Both are kept, per the locked roadmap note.

```tsx
// components/InterestsDialog.tsx (MOD)
export type TopicDraft = { description: string; weight: 20 | -20; sourceArticleId?: number }  // D-12
// TopicRow.tsx: TopicRowState gains `sourceArticleId?: number`; handleSave create path:
//   create.mutate({ ...input, ...(row.sourceArticleId !== undefined && { sourceArticleId: row.sourceArticleId }) }, …)
// Seeded draft row (InterestsDialog.tsx:452-460) copies draft.sourceArticleId.

const addDraft = (draft?: TopicDraft) => {
  draftCounter.current += 1
  setRows((current) => [...current, {
    key: `d-${draftCounter.current}`, id: null, name: '',
    description: draft?.description ?? '', weightText: String(draft?.weight ?? 20), weight: draft?.weight ?? 20,
    saved: null, sourceArticleId: draft?.sourceArticleId,
  }])
}
// "+ Add topic": onClick={() => addDraft()}
// TopicsSection return: <><section className="interests-section">…</section>
//   <SuggestedTopics rows={rows} atMax={atMax} onCreate={(s) => addDraft({
//     description: s.title.trim().slice(0, 500), weight: 20, sourceArticleId: s.articleId })} /></>

function SuggestedTopics({ rows, atMax, onCreate }: { rows: TopicRowState[]; atMax: boolean; onCreate: (s: TopicSuggestion) => void }) {
  const suggestions = useTopicSuggestions()
  const dismiss = useDismissSuggestion()
  const created = new Set(rows.filter((r) => r.id !== null && r.sourceArticleId !== undefined).map((r) => r.sourceArticleId))
  const drafted = new Set(rows.filter((r) => r.id === null && r.sourceArticleId !== undefined).map((r) => r.sourceArticleId))
  const data = suggestions.data
  const items = data?.items.filter((s) => !created.has(s.articleId)) ?? []
  if (!data || items.length === 0) return null                     // D-02 (also hides on load/error)
  const heading = data.total > data.items.length ? `Suggested topics (${data.items.length} of ${data.total})` : 'Suggested topics'
  return (
    <section className="interests-section interests-suggestions" aria-labelledby="interests-suggestions-title">
      <h3 id="interests-suggestions-title">{heading}</h3>
      <ul className="interests-suggestion-list">
        {items.map((s) => {
          const isDrafted = drafted.has(s.articleId)
          return (
            <li key={s.articleId} className={isDrafted ? 'interests-suggestion drafted' : 'interests-suggestion'}>
              <InterestBadge score={s.interestScore} />
              <span className="interests-suggestion-text">
                <span className="interests-suggestion-title" title={s.title}>{s.title}</span>
                <span className="interests-suggestion-feed">{s.feedTitle}</span>
              </span>
              {isDrafted ? (
                <span className="interests-suggestion-state">Draft added</span>
              ) : (
                <span className="interests-suggestion-actions">
                  <button className="btn-secondary" onClick={() => onCreate(s)} disabled={atMax}
                    title={atMax ? 'You have 25 topics, the maximum.' : undefined}
                    aria-label={`Create topic from suggestion: ${s.title}`}>Create topic</button>
                  <button className="btn-secondary" onClick={() => dismiss.mutate(s.articleId)}
                    disabled={dismiss.isPending && dismiss.variables === s.articleId}
                    aria-label={`Dismiss suggestion: ${s.title}`}>Dismiss</button>
                </span>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
```

Values quoted from source:
- `const TOPICS_MAX = 25` [VERIFIED: InterestsDialog.tsx:29]
- `const atMax = rows.length >= TOPICS_MAX` [VERIFIED: :515]. Using `rows.length` counts unsaved drafts too, which matches "+ Add topic" behavior.
- The existing "+ Add topic" at-max tooltip text is `'You have 25 topics, the maximum. Delete one to add another.'` [VERIFIED: :575]. The suggestion tooltip uses the D-18 text exactly.

`InterestBadge` reads `TierContext`, whose default is `DEFAULT_TIERS` [VERIFIED: utils/interest.ts:35], so it renders in dialog tests without a provider.

The title is rendered as React text (auto-escaped). Never use `dangerouslySetInnerHTML`.

CSS (App.css, next to `.interests-topic-row` ~line 790):
- `.interests-suggestion-list` (flex column, gap 8px, no list style)
- `.interests-suggestion` (flex row, `align-items: center`, gap 8px, `border: 1px solid var(--border)`, radius 6px, padding 8px 12px)
- `.interests-suggestion-text` (flex 1, `min-width: 0`, column)
- `.interests-suggestion-title` (ellipsis, `color: var(--text-primary)`)
- `.interests-suggestion-feed` (12px, `var(--text-muted)`)
- `.interests-suggestion.drafted` (`opacity: 0.6`)
- `.interests-suggestion-state` (12px, `var(--text-muted)`)

Use theme variables only (6 themes).

FeedbackNotice (D-14):

```ts
onCreateTopic?.({ description: article.title.trim().slice(0, 500), weight: feedback.vote === 1 ? 20 : -20, sourceArticleId: article.id })
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Draft seeded only at open (`useState` initializer) | `addDraft(draft?)` appends at any time | This phase | The FeedbackNotice seed path stays as is; suggestions use `addDraft` |
| Prior research: order by strongest kind then recency; cap ~10/20; exclude only 👎 | CONTEXT D-05/D-07/D-08: badge ascending, cap 10 + total, exclude **any** vote | 11-CONTEXT | Follow CONTEXT, not ARCHITECTURE.md Pattern 4 |
| Prior research route `POST …/dismiss` | `PUT …/dismissal` (bodyless, preflighted) | This research | Matches the engagement-open precedent |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `JdbcClient` statements join the `@Transactional` started on `InterestService.createTopic` together with the Spring Data JDBC `save` | Pattern 2 | TOPIC_CREATED not atomic. Mitigation: the spy-throw integration test proves rollback |
| A2 | `CAST(:reason AS text)` / `CAST(:nearMiss AS float8)` avoid any PgJDBC param-type inference error in `INSERT … SELECT` | §1 | None if kept; a runtime SQL error if dropped and inference fails |
| A3 | A text/plain cross-site POST to `/api/interest/topics` gets 415 (no converter for `TopicRequest`), so the appended field adds no CSRF surface | Security | Low: a forged topic create + one dismissal row. Optional test or explicit `consumes = APPLICATION_JSON_VALUE` |
| A4 | The `title` tooltip on a disabled button shows in the user's browser | §6 | Cosmetic. It follows the existing "+ Add topic" precedent |
| A5 | D-16 "row disabled" means the whole row (Create and Dismiss replaced by "Draft added") | §6 | If the user wants Dismiss usable while drafted, add it back. A later save then keeps DISMISSED (first reason), which is harmless |
| A6 | Near-miss bounds `0 < x <= 0.5` | §5 | CONTEXT offers this as the example. Tighter or looser bounds are a planner call |

## Open Questions (RESOLVED)

1. **Header count after a local hide.** RESOLVED: accept the lag; no `(shown of total - hidden)` arithmetic. Between a save and the refetch, `items` can shrink by one while `total` hasn't; it is sub-second and not worth extra code. Plans 11-03 and 11-04 record this as accepted: the heading reads `data.items.length` and `data.total` (D-07), a Dismiss patches the cached list (item filtered out, `total` decremented), and a created-topic hide filters only the rendered rows, so the count can lag by one until the refetch.
2. **Help line under the heading.** RESOLVED: no help line. CONTEXT specifies none, and the section only shows when it has rows (D-02 spirit), so the plans render the `Suggested topics` heading and its rows with no caption. The Pitfall 10 research note ("a topic created now learns from future articles only") is not a locked decision; it would be added only if the user asks.
3. **Phase 12 calibration of 0.35 (do NOT run now).** RESOLVED: deferred to Phase 12. Phase 11 ships 0.35 as the D-09 yaml constant `myfeeder.interest.suggestions.near-miss` (plan 11-01, commented "Phase 12 tunes it"), and no Phase 11 task runs the query below or touches prod. It is recorded here for Phase 12, read-only against prod (via the existing read-only replay conventions, `PGOPTIONS='-c default_transaction_read_only=on'`):

   ```sql
   SELECT width_bucket(COALESCE(b.best, 0), 0, 0.5, 10) AS bucket, count(*)
   FROM (SELECT g.article_id, (SELECT MAX(ts.noul) FROM article_topic_score ts WHERE ts.article_id = g.article_id) AS best
         FROM article_engagement g JOIN article_score s ON s.article_id = g.article_id AND s.status = 'SCORED'
         WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id)
         GROUP BY g.article_id) b
   WHERE COALESCE(b.best, 0) <= 0.5 GROUP BY 1 ORDER BY 1;
   ```

   (It contains no write keyword, so it passes the replay check.)

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker (Testcontainers) | backend tests | ✓ | Server 29.8.1 | — |
| Java | build/tests | ✓ | OpenJDK 25.0.4 | — |
| Node | frontend tests/build | ✓ | v26.10.0 | — |
| Vitest | frontend tests | ✓ | 4.1.11 | — |

No missing dependencies. The phase needs no prod, Jev or network access.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot test slices + Testcontainers Postgres (backend); Vitest 4.1.11 + React Testing Library (frontend) |
| Config file | `build.gradle.kts` `tasks.withType<Test>` (sequential); `src/main/frontend/vite.config.ts`/vitest config |
| Quick run (backend) | `./gradlew test -x npmBuild -x npmInstall --tests "<FQCN>"` |
| Quick run (frontend) | `cd src/main/frontend && npx tsc -b && npx vitest run <files>` |
| Full suite command | `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npm test` (plus `./gradlew build` at the phase gate) |

### Phase Requirements → Test Map
| Req / SC | Behavior | Test Type | Automated Command | File Exists? |
|----------|----------|-----------|-------------------|-------------|
| GAP-01 / SC-1 | Candidate predicate: engaged in window (29d in, 31d out; latest of several kinds), SCORED only (none/FAILED/SKIPPED excluded), any vote excludes, any dismissal reason excludes | integration (@DataJdbcTest, rolled back) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.TopicSuggestionStoreTest"` | ❌ Wave 0 |
| GAP-01 / D-05 / D-07 | Badge ascending, ties → latest engagement, then id; cap 10; total; candidate without badge dropped | unit (Mockito) | `… --tests "org.bartram.myfeeder.service.TopicSuggestionServiceTest"` | ❌ Wave 0 |
| GAP-01 / SC-1 | `GET /api/interest/suggestions` shape `{items[{articleId,title,feedTitle,interestScore}], total}`; engaged-since-open appears on refetch | integration (@SpringBootTest + MockMvc) | `… --tests "org.bartram.myfeeder.controller.TopicSuggestionApiIntegrationTest"` | ❌ Wave 0 |
| GAP-01 (UI) | Section below Topics, hidden when empty or failed, header `(10 of 23)`, badge + title + feed, refetch on reopen | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ✅ (extend) |
| GAP-01 (SC-1) | `invalidateAfterLearnedChange` / `afterEngagement` invalidate `['interest','suggestions']`; topic create/delete, Re-score, dismiss invalidate it | hook | `npx vitest run src/hooks/engagementReaction.test.ts src/hooks/useInterest.test.tsx` | ✅ (extend) |
| GAP-02 / SC-3 | POST topics with `sourceArticleId` → TOPIC_CREATED row; GET no longer lists it (after reload); stale/missing id → 201 and no row; 26th topic → 400 and no row; store throws → topic rolled back | integration | `… TopicSuggestionApiIntegrationTest` (`@MockitoSpyBean TopicSuggestionStore` for rollback) | ❌ Wave 0 |
| GAP-02 | `InterestService.createTopic` calls `handle(id, TOPIC_CREATED)` only after save, never on null or validation failure | unit | `… --tests "org.bartram.myfeeder.service.InterestServiceTest"` | ✅ (extend) |
| GAP-02 | Controller passes `sourceArticleId` through; PUT ignores it | slice (@WebMvcTest) | `… --tests "org.bartram.myfeeder.controller.InterestControllerTest"` | ✅ (update 3-arg stubs) |
| GAP-02 (UI) / D-15 / D-16 / D-18 | Create topic adds a +20 draft to the open dialog; row shows "Draft added" and is disabled; Discard re-enables; saved → row gone; at 25 Create disabled with D-18 tooltip and Dismiss enabled; POST body carries `sourceArticleId` | component | `npx vitest run src/components/InterestsDialog.test.tsx src/components/TopicRow.test.tsx` | ✅ (extend) |
| D-14 | FeedbackNotice draft carries `sourceArticleId` | component | `npx vitest run src/components/FeedbackNotice.test.tsx src/components/ReadingPane.test.tsx` | ✅ (update ReadingPane:351) |
| GAP-03 / SC-4 | `PUT …/{id}/dismissal` 204, repeat 204 (one row, first reason kept), unknown 404, `abc` 400; dismissed stays gone after a new engagement kind is added and after reload; deleting a topic created from a suggestion doesn't resurrect it (D-14) | integration | `… TopicSuggestionApiIntegrationTest` | ❌ Wave 0 |
| GAP-03 (UI) | Dismiss removes the row immediately, no confirm, sends `PUT /api/interest/suggestions/{id}/dismissal` | component | `npx vitest run src/components/InterestsDialog.test.tsx` | ✅ (extend) |
| GAP-04 / SC-2 | best noul 0.35 excluded, 0.3499 included; negative-weight topic noul 0.4 excludes (D-10); no topic rows included (D-11); matched 0.6 excluded | integration | `… TopicSuggestionStoreTest` | ❌ Wave 0 |
| GAP-04 / D-09 | near-miss binds 0.35; 0 / -0.1 / 0.51 / NaN refused with fixed text; 0.5 starts; main yaml starts | unit (ApplicationContextRunner) | `… --tests "org.bartram.myfeeder.config.MyfeederPropertiesValidationTest"` | ✅ (extend) |
| D-09 parity | main and test yaml identical, dev overlay untouched | unit | `… --tests "org.bartram.myfeeder.DevProfileConfigTest"` | ✅ (no change needed) |
| GAP-05 / SC-5 | List, create-with-source and dismiss never call `JevApiClient.judge` | integration | `TopicSuggestionApiIntegrationTest` with `@MockitoBean JevApiClient` + `@AfterEach verify(jevApiClient, never()).judge(any(), any())` (copy from `EngagementApiIntegrationTest.java:49,62-65`) | ❌ Wave 0 |
| GAP-05 (UI) | Opening, creating from and dismissing suggestions never sends `POST /api/interest/preview` | component | `npx vitest run src/components/InterestsDialog.test.tsx` (assert on recorded `calls`) | ✅ (extend) |
| Guard | Ranking SQL still never reads the dismissal table | unit | `… --tests "org.bartram.myfeeder.repository.V7EngagementMigrationTest"` and `InterestCalibrationReplaySqlTest` | ✅ (must stay green unchanged) |

### Sampling Rate
- **Per task commit:** the touched test classes via the quick commands above
- **Per wave merge:** `./gradlew test -x npmBuild -x npmInstall` + `cd src/main/frontend && npx tsc -b && npm test`
- **Phase gate:** `./gradlew build` green (includes frontend build) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java`: @DataJdbcTest, `@Import(TestcontainersConfiguration.class)`, whole-table fixture (`DELETE FROM article_score; DELETE FROM article; DELETE FROM interest_topic` inside the rolled-back transaction, as in `InterestScoreQueriesEngagementTest.setUp`). Insert engagement with explicit `created_at` (`now() - interval '31 days'`).
- [ ] `src/test/java/org/bartram/myfeeder/service/TopicSuggestionServiceTest.java`: Mockito
- [ ] `src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java`: @SpringBootTest. Clean its own feed URL in `@BeforeEach` (articles cascade to engagement, score and dismissal) and `DELETE FROM interest_topic` (the 25-topic cap is global, as `InterestApiIntegrationTest.setUp` does). Assert on seeded ids only.
- [ ] `src/test/java/org/bartram/myfeeder/controller/TopicSuggestionControllerTest.java` (optional, @WebMvcTest(TopicSuggestionController.class)): 404/400 mapping
- [ ] Frontend: add a default `GET /api/interest/suggestions` route (`{items: [], total: 0}`) to `InterestsDialog.test.tsx` `beforeEach`

## Security Domain

`security_enforcement: true`, ASVS level 1.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Single-user homelab app with no auth (pre-existing) |
| V3 Session Management | no | — |
| V4 Access Control | no (single user) | — |
| V5 Input Validation | yes | `@PathVariable Long` (non-numeric → 400). `sourceArticleId` typed `Long`. Existing fixed-text 400s in `InterestService.validate`. All SQL values are named parameters, with no string concatenation of request data |
| V6 Cryptography | no | — |
| V13 API / CSRF | yes | Dismiss is a bodyless **PUT** (preflighted, never a simple request). Topic create stays a JSON `@RequestBody` (text/plain → 415, A3) |
| V7 Error handling / logging | yes | `NotFoundException("Article not found: " + id)` echoes only a numeric id (existing pattern). Never echo titles |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-site request dismisses suggestions or creates topics | Tampering | PUT for dismiss; JSON body for create (A3); optional test asserting 415 for text/plain on `POST /api/interest/topics` |
| SQL injection via ids | Tampering | `JdbcClient` named params; scope strings are compile-time constants |
| Stored XSS via article/feed titles in the new rows | Tampering | React text rendering only; no `dangerouslySetInnerHTML` |
| Billing abuse (Jev calls from listing) | DoS / financial | No Jev dependency on any path. `verify(jevApiClient, never())` test; no preview auto-fire |
| Stale `sourceArticleId` blocks topic save | DoS (self) | `INSERT … SELECT FROM article` → 0 rows, no FK error |

## Sources

### Primary (HIGH confidence, read this session)
- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java` (displayScores, blendCte, LEARNED_CTE, matchedTopicIds, scope contract)
- `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java:210-218`, `InterestCalibrationReplaySqlTest.java:30-111`
- `src/main/resources/db/migration/V7__engagement.sql`, `V6__interest_scoring.sql`, `V1__initial_schema.sql`
- `config/MyfeederProperties.java`, `MyfeederPropertiesValidationTest.java`, `DevProfileConfigTest.java`, `application.yaml` (main, test, dev)
- `service/InterestService.java`, `controller/InterestController.java`, `TopicRequest.java`, `InterestRescoreController.java`, `ArticleController.java:97-118`, `ArticleService.java:59-76`, `ArticleEngagementStore.java`, `ArticleFeedbackStore.java`
- Frontend: `InterestsDialog.tsx`, `TopicRow.tsx`, `FeedbackNotice.tsx`, `App.tsx`, `api/interest.ts`, `api/client.ts`, `hooks/useInterest.ts`, `hooks/engagementReaction.ts`, `hooks/useFeedback.ts`, `queryClient.ts`, `InterestBadge.tsx`, `App.css`, existing tests
- `.planning/research/ARCHITECTURE.md` Pattern 4, `PITFALLS.md` Pitfalls 3 and 10

### Secondary (MEDIUM)
- TanStack Query v5 Important Defaults (stale queries refetch on mount; gcTime 5 min): https://tanstack.com/query/v5/docs/framework/react/guides/important-defaults
- TanStack Query v5 Query Invalidation (marks stale; active queries refetch): https://tanstack.com/query/v5/docs/framework/react/guides/query-invalidation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Nothing new; every class and hook was read.
- Architecture: HIGH. The guard tests were read; the two-step design avoids them by construction.
- Pitfalls: HIGH for the in-repo ones (guards, test fixtures, markSaved). MEDIUM for A1/A2, which are covered by planned tests.

**Research date:** 2026-10-01
**Valid until:** 2026-10-31 (stable in-repo domain)
