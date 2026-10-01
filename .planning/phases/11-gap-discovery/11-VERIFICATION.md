---
phase: 11-gap-discovery
verified: 2026-10-01T23:15:00Z
status: human_needed
score: 10/10 must-haves verified
covered_files:
  - .planning/phases/11-gap-discovery/11-01-PLAN.md
  - .planning/phases/11-gap-discovery/11-01-SUMMARY.md
  - .planning/phases/11-gap-discovery/11-02-PLAN.md
  - .planning/phases/11-gap-discovery/11-02-SUMMARY.md
  - .planning/phases/11-gap-discovery/11-03-PLAN.md
  - .planning/phases/11-gap-discovery/11-03-SUMMARY.md
  - .planning/phases/11-gap-discovery/11-04-PLAN.md
  - .planning/phases/11-gap-discovery/11-04-SUMMARY.md
  - CLAUDE.md
  - src/main/frontend/src/App.css
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/FeedbackNotice.test.tsx
  - src/main/frontend/src/components/FeedbackNotice.tsx
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/components/ReadingPane.test.tsx
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/components/TopicRow.tsx
  - src/main/frontend/src/hooks/engagementReaction.test.ts
  - src/main/frontend/src/hooks/engagementReaction.ts
  - src/main/frontend/src/hooks/useInterest.test.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/controller/InterestController.java
  - src/main/java/org/bartram/myfeeder/controller/TopicRequest.java
  - src/main/java/org/bartram/myfeeder/controller/TopicSuggestionController.java
  - src/main/java/org/bartram/myfeeder/model/SuggestionDismissalReason.java
  - src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java
  - src/main/java/org/bartram/myfeeder/service/InterestService.java
  - src/main/java/org/bartram/myfeeder/service/TopicSuggestion.java
  - src/main/java/org/bartram/myfeeder/service/TopicSuggestionService.java
  - src/main/java/org/bartram/myfeeder/service/TopicSuggestions.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/TopicSuggestionApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/repository/TopicSuggestionStoreTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/TopicSuggestionServiceTest.java
  - src/test/resources/application.yaml
covered_digest: "v2:sha256:361da72f27a304dce5483f8ab0e97d5eb09e51cbdf4aece1e536f223fc712e68"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "End-to-end in the running app (harvested from 11-04-PLAN <human-check>, SUMMARY D8): ./gradlew bootTestRun + npm run dev. Open an article that matches no topic, click Open Original, open Interests; Create topic on the suggestion, name it, Save; reload the page. Dismiss another suggestion and reload."
    expected: "The engaged article appears under Suggested topics; after Save it disappears and stays gone after a full page reload; the dismissed suggestion stays gone after reload"
    why_human: "Automated tests prove each half (MockMvc against Postgres; Vitest with stubbed fetch) but no test drives the real browser round trip across a page reload"
  - test: "Look at the Suggested topics section, including a 'Draft added' row and an at-max (disabled Create topic) row, in all 6 themes"
    expected: "Rows are legible and consistent with the Topics section in every theme; the drafted row's dimming reads as inactive, not broken"
    why_human: "Visual appearance; CSS uses theme variables only (no hard-coded colors in the diff) but the look is not asserted by any test"
  - test: "Decide on review warning WR-01: an article whose only engagement of a kind was first recorded more than 30 days ago does not re-enter Suggested topics when the user engages with it again in the same way (e.g. re-opens it), because article_engagement keeps the first created_at per (article, kind)"
    expected: "Either accept this as the D-06 rule ('measured on article_engagement.created_at', with Phase 8's sticky first-recorded rows) and correct CLAUDE.md:197 and the engagementReaction.ts comment, which say 'latest engagement' / 'an engagement can add one'; or schedule a last_engaged_at change for a later phase"
    why_human: "Product-semantics decision. The code matches the locked decision D-06 as written and SC-1's freshness path is proven by tests, but the project docs overstate the behavior"
---

# Phase 11: Gap Discovery Verification Report

**Phase Goal:** Articles the user engaged with that no topic covers show up as topic suggestions in Interests, which the user can turn into topics or dismiss, with no Jev calls
**Verified:** 2026-10-01T23:15:00Z
**Status:** human_needed
**Re-verification:** No (initial verification)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1: Interests has a "Suggested topics" section listing engaged, scored articles that matched no topic, including ones engaged since the dialog was last opened; unscored articles are not listed | ✓ VERIFIED | `TopicSuggestionStore.CANDIDATES` joins `article_engagement` + `article_score ... status = 'SCORED'`, excludes votes and dismissals, `HAVING MAX(g.created_at) > :cutoff` (30 days). `SuggestedTopics` is rendered by `TopicsSection` after the Topics section and before `RescoreFooter`. `useTopicSuggestions` has `staleTime: 0` and the dialog body unmounts on close, so every open refetches; `invalidateAfterLearnedChange` invalidates `['interest','suggestions']`. Tests passing now: `anArticleEngagedAfterAListingAppearsOnTheNextOne`, `leavesOutUnscoredUnengagedAndMatchedArticles`, `unscoredFailedAndSkippedArticlesAreNot`, `everyOpenRefetchesTheList`, `sectionSitsBelowTopicsAndAboveTheRescoreFooter`. Caveat WR-01 (same-kind re-engagement after 30 days does not refresh the window); see Human Verification item 3 |
| 2 | SC-2: articles whose best noul is close to the match threshold are left out | ✓ VERIFIED | `COALESCE((SELECT MAX(ts.noul) ...), 0) < :nearMiss` (max over every topic row, either weight sign). `near-miss: 0.35` in main and test yaml, validated `0 < x <= 0.5` with `SUGGESTIONS_INVALID`. Tests: `nearMissIsExclusiveAtTheThreshold`, `theBestNoulAcrossTopicsCounts`, `aNegativeWeightTopicCoversTheArticle`, `aMatchedArticleIsNot`, `MyfeederPropertiesValidationTest` (16 pass) |
| 3 | SC-3: choosing a suggestion opens the prefilled draft; saving removes the suggestion and it stays gone after reload | ✓ VERIFIED | `onCreate` calls `addDraft({ description: s.title.trim().slice(0, 500), weight: 20, sourceArticleId: s.articleId })`; `TopicRow.handleSave` adds `sourceArticleId` on create only; `InterestController` passes `request.sourceArticleId()`; `InterestService.createTopic` (`@Transactional`) calls `suggestionStore.handle(sourceArticleId, TOPIC_CREATED)` after the save. Tests: `createTopicAddsAPlus20DraftToTheOpenDialog` (no writes before Save), `savingTheDraftSendsTheSourceAndRemovesTheSuggestion` (exact POST body, gone after refetch), `creatingATopicFromASuggestionHidesItForGood` (TOPIC_CREATED row, absent on two later GETs), `aFailedDismissalInsertRollsBackTheTopic`, `deletingTheTopicKeepsTheArticleHandled` |
| 4 | SC-4: dismissing removes a suggestion permanently, across reloads and after later engagement | ✓ VERIFIED | `PUT /api/interest/suggestions/{articleId}/dismissal` → `service.dismiss` → `store.handle(DISMISSED)` (`ON CONFLICT (article_id) DO NOTHING`); `CANDIDATES` excludes any dismissal row. No un-dismiss route exists. Tests: `dismissReturns204AndHidesTheSuggestion`, `dismissIsIdempotentAndKeepsTheFirstReason`, `aDismissedSuggestionStaysGoneAfterLaterEngagement` (open via API + STAR), `dismissRemovesTheRowWithoutConfirming`, `aFailedDismissKeepsTheRowAndToasts` |
| 5 | SC-5: listing, creating from and dismissing suggestions never call Jev | ✓ VERIFIED | None of `TopicSuggestionStore`, `TopicSuggestionService`, `TopicSuggestionController`, `InterestService` imports or reaches `JevApiClient`/`TypeSafeClient`/preview/scoring classes. `TopicSuggestionApiIntegrationTest` `@AfterEach neverCallsJev` verifies `judge` is never invoked after all 18 tests; frontend `listingNeverPostsPreview`, `dismissNeverPostsPreview`, `creatingNeverPostsPreview` pass |
| 6 | Order badge ascending (from `displayScores`), then latest engagement, then id desc; cap 10 with total; vanished badge dropped | ✓ VERIFIED | `TopicSuggestionService.list()`; `TopicSuggestionServiceTest` (6 pass); heading `Suggested topics (N of M)` only when capped (`headerShowsTheTotalOnlyWhenCapped`) |
| 7 | Ranking SQL and calibration replay unchanged; the ranking never reads the dismissal table; dismissing leaves the badge unchanged | ✓ VERIFIED | `git diff 7cc260d HEAD` on `InterestScoreQueries.java`, `scripts/`, `application-dev.yaml` is empty; `V7EngagementMigrationTest.rankingSqlReadsEngagementButNotDismissals` and `InterestCalibrationReplaySqlTest` pass; `dismissingLeavesTheBadgeUnchanged` |
| 8 | Topic-create edge cases: no source writes nothing; stale source still 201; 25th writes TOPIC_CREATED, 26th 400 writes nothing; existing DISMISSED kept; PUT ignores source; text/plain 415 | ✓ VERIFIED | `withoutASourceNoRowIsWritten`, `aStaleSourceArticleIdStillCreatesTheTopic`, `theTwentyFifthTopicWritesTheRowAndTheTwentySixthWritesNothing`, `aDismissedArticleKeepsItsFirstReasonWhenATopicIsCreatedFromIt`, `updatingATopicWithASourceWritesNoRow`, `aTextPlainTopicCreateIs415` |
| 9 | UI states: "Draft added" while the draft is unsaved (no actions), Discard reactivates, at 25 rows Create topic disabled with tooltip and Dismiss works; section hidden when empty/loading/failed; titles render as text | ✓ VERIFIED | `SuggestedTopics` code; tests `aDraftedSuggestionReadsDraftAddedAndCannotBeAddedTwice`, `discardingTheDraftReactivatesTheSuggestion`, `at25CreateTopicIsDisabledWithTheTooltipAndDismissStillWorks`, `hiddenWhenThereAreNoSuggestions`, `hiddenWhenTheListFails`, `titlesRenderAsText`, `shownInColdStartAndWithoutAKey` |
| 10 | Reading-pane "Create topic from article" draft carries `sourceArticleId` (one draft path) | ✓ VERIFIED | `FeedbackNotice.tsx:27 sourceArticleId: article.id`; seeded draft row keeps it (`InterestsDialog.tsx:465`); `seededDraftSendsItsSourceOnSave` passes |

**Score:** 10/10 truths verified (0 present, behavior-unverified)

Behavior-dependent truths (atomic TOPIC_CREATED rollback, dismissal permanence, refetch-on-open) are each backed by a named test that passed in this verifier's own run.

### Prohibitions (all test-tier)

| Prohibition | Enforcement evidence | Disposition |
|-------------|---------------------|-------------|
| MUST NOT call Jev to build the list / dismiss / create from a suggestion | `@AfterEach neverCallsJev` in `TopicSuggestionApiIntegrationTest` (18/18 pass) | verified |
| MUST NOT change the Priority blend, badge SQL or replay; ranking never reads the dismissal table | Empty diff + `rankingSqlReadsEngagementButNotDismissals` | verified |
| MUST NOT bring back a dismissed or topic-created suggestion | `aDismissedSuggestionStaysGoneAfterLaterEngagement`, `deletingTheTopicKeepsTheArticleHandled`, `creatingATopicFromASuggestionHidesItForGood` | verified |
| MUST NOT treat a dismissal as a ranking signal | `dismissingLeavesTheBadgeUnchanged` + ranking-SQL guard | verified |
| MUST NOT call Preview when listing/dismissing/creating; Preview only on explicit click | `listingNeverPostsPreview`, `dismissNeverPostsPreview`, `creatingNeverPostsPreview` | verified |
| MUST NOT show a dismissed suggestion again in the open dialog or on reopen | `dismissRemovesTheRowWithoutConfirming` (+ backend permanence) | verified |
| MUST NOT create or save a topic on the user's behalf | `createTopicAddsAPlus20DraftToTheOpenDialog` asserts `writes()` is empty | verified |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `repository/TopicSuggestionStore.java` | D-08 candidate query + idempotent `handle` | ✓ VERIFIED | Contains `NOT EXISTS (SELECT 1 FROM topic_suggestion_dismissal` and `ON CONFLICT (article_id) DO NOTHING`; used by service and `InterestService` |
| `service/TopicSuggestionService.java` | list + dismiss | ✓ VERIFIED | Calls `scoreQueries.displayScores(`, `getSuggestions().getNearMiss()`; 404 on unknown article |
| `controller/TopicSuggestionController.java` | GET list, PUT dismissal | ✓ VERIFIED | `service.list()`, `service.dismiss(articleId)`, 204 |
| `service/TopicSuggestion.java`, `TopicSuggestions.java` | DTOs | ✓ VERIFIED | `{articleId, title, feedTitle, interestScore}`, `{items, total}` |
| `model/SuggestionDismissalReason.java` | enum | ✓ VERIFIED | `DISMISSED, TOPIC_CREATED` |
| `config/MyfeederProperties.java` | near-miss + validation | ✓ VERIFIED | `Suggestions.nearMiss = 0.35`, `SUGGESTIONS_INVALID` |
| `controller/TopicRequest.java`, `InterestController.java`, `InterestService.java` | sourceArticleId through create | ✓ VERIFIED | Wired, `@Transactional` |
| `frontend api/interest.ts` | types + `getSuggestions`, `dismissSuggestion`, `TopicInput.sourceArticleId?` | ✓ VERIFIED | |
| `frontend hooks/useInterest.ts` | `SUGGESTIONS_KEY`, `useTopicSuggestions`, `useDismissSuggestion`, invalidations | ✓ VERIFIED | Invalidated on create, delete, Re-score, dismiss |
| `frontend hooks/engagementReaction.ts` | suggestions invalidation | ✓ VERIFIED | `['interest', 'suggestions']` |
| `frontend components/InterestsDialog.tsx` | `SuggestedTopics`, `addDraft(draft?)`, source on drafts | ✓ VERIFIED | |
| `frontend components/TopicRow.tsx`, `FeedbackNotice.tsx` | source on create / reading-pane draft | ✓ VERIFIED | |
| `App.css` | `.interests-suggestion*` | ✓ VERIFIED | Theme variables only (no hex/rgb added) |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| TopicSuggestionController | TopicSuggestionService | `service.list()`, `service.dismiss(articleId)` | WIRED |
| TopicSuggestionService | InterestScoreQueries | `scoreQueries.displayScores(` | WIRED |
| TopicSuggestionService | MyfeederProperties | `getSuggestions().getNearMiss()` | WIRED |
| TopicSuggestionStore | topic_suggestion_dismissal | NOT EXISTS in CANDIDATES; INSERT in `handle` | WIRED |
| InterestController | InterestService | `request.sourceArticleId()` | WIRED |
| InterestService | TopicSuggestionStore | `suggestionStore.handle(sourceArticleId, TOPIC_CREATED)` inside `@Transactional` | WIRED |
| InterestsDialog | useInterest | `useTopicSuggestions()`, `useDismissSuggestion()` | WIRED |
| useInterest | api/interest | `interestApi.getSuggestions`, `interestApi.dismissSuggestion` | WIRED |
| InterestsDialog SuggestedTopics | TopicsSection.addDraft | `sourceArticleId: s.articleId` | WIRED |
| TopicRow | api/interest | `row.sourceArticleId` added on create only | WIRED |
| FeedbackNotice | InterestsDialog | `sourceArticleId: article.id` via App's `interestsDraft` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| SuggestedTopics | `suggestions.data.items` | `GET /api/interest/suggestions` → `TopicSuggestionStore.candidates` (SQL over engagement/score/feedback/dismissal/topic-score) + `displayScores` | Yes | ✓ FLOWING |
| InterestBadge in row | `s.interestScore` | `InterestScoreQueries.displayScores` (same blend as Priority) | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase 11 backend tests + ranking guards | `env -u SPRING_PROFILES_ACTIVE ./gradlew test -x npmBuild -x npmInstall --tests '*TopicSuggestion*' --tests '*MyfeederPropertiesValidationTest' --tests '*InterestServiceTest' --tests '*InterestControllerTest' --tests '*V7EngagementMigrationTest' --tests '*InterestCalibrationReplaySqlTest' --tests '*ArticleScoringFlowTest'` | 124 tests, 0 failures, 0 skipped (results stamped 2026-10-01T23:11Z) | ✓ PASS |
| Phase 11 frontend tests | `npx vitest run` on InterestsDialog, useInterest, engagementReaction, TopicRow, FeedbackNotice, ReadingPane tests | 6 files, 146 tests pass | ✓ PASS |
| Type-check | `npx tsc -b` | clean | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED (no probes declared by the phase; not a migration/tooling phase).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| GAP-01 | 11-01, 11-03 | "Suggested topics" section listing engaged, SCORED articles that matched no topic | ✓ SATISFIED | Truths 1, 6, 9 |
| GAP-02 | 11-02, 11-04 | Create a topic from a suggestion (prefilled draft), which removes it | ✓ SATISFIED | Truths 3, 8, 10 |
| GAP-03 | 11-02, 11-03 | Dismiss a suggestion permanently | ✓ SATISFIED | Truth 4 |
| GAP-04 | 11-01 | Near-miss articles not suggested | ✓ SATISFIED | Truth 2 |
| GAP-05 | 11-01..11-04 | Suggestions never call Jev | ✓ SATISFIED | Truth 5 |

All five IDs mapped to Phase 11 in REQUIREMENTS.md are claimed by at least one plan; no orphaned requirements. REQUIREMENTS.md still marks GAP-01, 02, 04, 05 as Pending; that is the orchestrator's phase.complete step.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all modified files) | - | TBD/FIXME/XXX/TODO/HACK | none | Only hit is the pre-existing `PROFILE_PLACEHOLDER` constant (an input placeholder, not a stub) |
| `CLAUDE.md` | 197 | Doc says the window is "measured on the latest `article_engagement.created_at`"; the store keeps the first `created_at` per kind (review WR-01) | ⚠️ Warning | Misleads maintainers/Phase 12 tuning; behavior matches D-06 as written. Routed to human decision |
| `src/main/frontend/src/hooks/engagementReaction.ts` | 12-15 | Comment "an engagement can add one" overstates (WR-01) | ⚠️ Warning | Same as above |
| `src/main/frontend/src/hooks/useInterest.ts` | 206-215 | Dismiss `total - 1` not idempotent; pending guard per latest call only (review WR-02) | ⚠️ Warning | Heading can under-count transiently after a double dismiss of the same row; a 404 dismiss keeps the row until reopen. Server state is correct and the refetch fixes the display; no success criterion is broken |
| `InterestsDialog.tsx` | 637-642 | Heading count uses unfiltered items (IN-01) | ℹ️ Info | Transient |

Neither review warning undermines a success criterion: WR-02 is a transient client-side count/pending-state issue (permanence and removal are server-side and tested). WR-01 is a semantics/documentation mismatch at an edge of SC-1 (re-engaging, in the same way, an article first engaged more than 30 days ago); the main SC-1 path, a new engagement appearing on the next open, is proven by `anArticleEngagedAfterAListingAppearsOnTheNextOne` and `everyOpenRefetchesTheList`.

### Human Verification Required

### 1. End-to-end round trip with page reload

**Test:** `./gradlew bootTestRun` and `cd src/main/frontend && npm run dev`. Open an article that matches no topic, click Open Original, open Interests. Click Create topic on the suggestion, name it, Save, then reload the page. Dismiss another suggestion and reload.
**Expected:** The article is listed under Suggested topics; after Save it disappears and stays gone after reload; the dismissed one stays gone after reload.
**Why human:** No automated test drives the real browser plus backend across a page reload (harvested from 11-04-PLAN `<human-check>`, SUMMARY D8).

### 2. Six-theme visual check

**Test:** View the section with a normal row, a "Draft added" row and the at-max disabled state in all 6 themes.
**Expected:** Legible and consistent with the Topics section; the drafted row reads as inactive.
**Why human:** Visual appearance.

### 3. Decide WR-01 semantics

**Test:** Review whether "first recording per engagement kind" is the intended D-06 window.
**Expected:** Accept and fix the wording in `CLAUDE.md:197` and `engagementReaction.ts:12-15`, or schedule a `last_engaged_at` change (do not overwrite `created_at`, which decay relies on).
**Why human:** Product decision; the code matches D-06 as written, and the docs do not.

### Gaps Summary

No blocking gaps. The phase goal is achieved in the codebase: the backend lists engaged, SCORED, unvoted, undismissed, non-near-miss articles from stored data only; the Interests dialog shows them, turns one into a prefilled draft whose save atomically marks the article handled, and dismisses permanently; nothing calls Jev. Status is `human_needed` because of the harvested end-to-end/visual check and the WR-01 documentation/semantics decision.

---

_Verified: 2026-10-01T23:15:00Z_
_Verifier: Claude (gsd-verifier)_
