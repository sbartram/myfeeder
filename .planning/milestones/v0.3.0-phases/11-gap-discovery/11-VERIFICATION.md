---
phase: 11-gap-discovery
verified: 2026-10-01T23:47:00Z
status: passed
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
covered_digest: "v2:sha256:033f26ccc7722df262562deabb578c8c8abb24cc9fd6f8a272cb5e52f8b5f034"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 10/10
  gaps_closed:
    - "Human item 1 (end-to-end create-from-suggestion and dismiss across reloads): passed in 11-UAT.md test 1"
    - "Human item 2 (six-theme visual check): passed in 11-UAT.md test 2"
    - "Human item 3 (WR-01 window semantics): decided in 11-UAT.md test 3; first recording per engagement kind accepted; CLAUDE.md and engagementReaction.ts corrected in 55c9817; 11-REVIEW-DISPOSITION.md marks WR-01 fixed"
  gaps_remaining: []
  regressions: []
---

# Phase 11: Gap Discovery Verification Report

**Phase Goal:** Articles the user engaged with that no topic covers show up as topic suggestions in Interests, which the user can turn into topics or dismiss, with no Jev calls
**Verified:** 2026-10-01T23:47:00Z
**Status:** passed
**Re-verification:** Yes. The prior report (human_needed, 10/10) went stale when commit 55c9817 changed `CLAUDE.md` and a comment in `engagementReaction.ts`.

## What changed since the prior report

`git diff fa11ff9 HEAD` (fa11ff9 wrote the prior report) touches four files:

- `.planning/phases/11-gap-discovery/11-UAT.md`: UAT complete, 3 of 3 passed, 0 issues.
- `.planning/phases/11-gap-discovery/11-REVIEW-DISPOSITION.md`: WR-01 changed from open to fixed (open count 7 to 6).
- `CLAUDE.md:197`: the predicate now reads "an engagement of any kind whose first recording (per kind) falls in the last 30 days ... repeating an engagement of the same kind does not refresh the window (WR-01, accepted)".
- `src/main/frontend/src/hooks/engagementReaction.ts:12-16`: doc comment only. It now says "a first engagement of a new kind can add one (a repeated engagement of the same kind keeps its original timestamp)". No executable line changed.

No Java, SQL, yaml or test file changed. The last backend commit (7bd8d3f, 22:50Z) predates the prior backend test run (23:11Z), so the backend evidence from that run still applies.

**The new wording matches the code.** `ArticleEngagementStore` inserts with `ON CONFLICT (article_id, kind) DO NOTHING` (line 29), so each (article, kind) row keeps its first `created_at`. `V7__engagement.sql` has `PRIMARY KEY (article_id, kind)`. `TopicSuggestionStore.CANDIDATES` uses `HAVING MAX(g.created_at) > :cutoff` (line 42). The window therefore passes when any kind's first recording is newer than 30 days, which is what the corrected CLAUDE.md and comment say. The store's own Javadoc (line 18) already said so.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1: Interests has a "Suggested topics" section listing engaged, scored articles that matched no topic, including ones engaged since the dialog was last opened; unscored articles are not listed | ✓ VERIFIED | Unchanged code. `CANDIDATES` joins `article_engagement` with `article_score ... status = 'SCORED'`, excludes votes and dismissals, and applies the 30-day `HAVING`. `useTopicSuggestions` uses `staleTime: 0` and `invalidateAfterLearnedChange` invalidates `['interest','suggestions']`. Tests: `anArticleEngagedAfterAListingAppearsOnTheNextOne`, `leavesOutUnscoredUnengagedAndMatchedArticles`, `everyOpenRefetchesTheList` (all pass). The WR-01 edge is now an accepted rule, documented correctly (UAT test 3). |
| 2 | SC-2: articles whose best noul is close to the match threshold are left out | ✓ VERIFIED | `COALESCE((SELECT MAX(ts.noul) ...), 0) < :nearMiss`, near-miss 0.35 in main and test yaml, `SUGGESTIONS_INVALID` validation. Tests: `nearMissIsExclusiveAtTheThreshold`, `theBestNoulAcrossTopicsCounts`, `aNegativeWeightTopicCoversTheArticle`, `MyfeederPropertiesValidationTest`. |
| 3 | SC-3: choosing a suggestion opens the prefilled draft; saving removes the suggestion and it stays gone after reload | ✓ VERIFIED | `addDraft({ description, weight: 20, sourceArticleId })`, then `TopicRow` create, `InterestController`, and `@Transactional InterestService.createTopic` calling `suggestionStore.handle(..., TOPIC_CREATED)`. Tests: `savingTheDraftSendsTheSourceAndRemovesTheSuggestion`, `creatingATopicFromASuggestionHidesItForGood`, `aFailedDismissalInsertRollsBackTheTopic`. UAT test 1 confirmed the round trip in the real app across a reload. |
| 4 | SC-4: dismissing removes a suggestion permanently, across reloads and after later engagement | ✓ VERIFIED | `PUT .../dismissal` calls `store.handle(DISMISSED)` (`ON CONFLICT DO NOTHING`), and there is no un-dismiss route. Tests: `dismissReturns204AndHidesTheSuggestion`, `aDismissedSuggestionStaysGoneAfterLaterEngagement`, `dismissRemovesTheRowWithoutConfirming`. UAT test 1 confirmed it across a reload. |
| 5 | SC-5: listing, creating from and dismissing suggestions never call Jev | ✓ VERIFIED | No suggestion-path class reaches `JevApiClient`/`TypeSafeClient`. `@AfterEach neverCallsJev` covers all 18 integration tests. The frontend tests `listingNeverPostsPreview`, `dismissNeverPostsPreview` and `creatingNeverPostsPreview` pass. |
| 6 | Order badge ascending (from `displayScores`), then latest engagement, then id desc; cap 10 with total; vanished badge dropped | ✓ VERIFIED | `TopicSuggestionService.list()`; `TopicSuggestionServiceTest` (6 pass); `headerShowsTheTotalOnlyWhenCapped` |
| 7 | Ranking SQL and calibration replay unchanged; the ranking never reads the dismissal table; dismissing leaves the badge unchanged | ✓ VERIFIED | No ranking/replay file has changed since the prior report. `rankingSqlReadsEngagementButNotDismissals` and `dismissingLeavesTheBadgeUnchanged` passed in the prior backend run, and the code is unchanged since. |
| 8 | Topic-create edge cases: no source writes nothing; stale source still 201; 25th writes TOPIC_CREATED, 26th 400 writes nothing; existing DISMISSED kept; PUT ignores source; text/plain 415 | ✓ VERIFIED | Named tests in `TopicSuggestionApiIntegrationTest`/`InterestServiceTest`/`InterestControllerTest` (unchanged; passed 23:11Z) |
| 9 | UI states: "Draft added" while unsaved, Discard reactivates, 25-row disable with tooltip while Dismiss still works; section hidden when empty/loading/failed; titles as text | ✓ VERIFIED | `InterestsDialog.test.tsx` Suggested topics describe, re-run now (pass). UAT test 2 checked the appearance in all 6 themes. |
| 10 | Reading-pane "Create topic from article" draft carries `sourceArticleId` (one draft path) | ✓ VERIFIED | `FeedbackNotice.tsx` `sourceArticleId: article.id`; `seededDraftSendsItsSourceOnSave` passes (re-run now) |

**Score:** 10/10 truths verified (0 present, behavior-unverified)

### Prohibitions (all test-tier)

All seven prohibitions from the prior report keep their enforcement tests unchanged and passing. These are: no Jev call; no change to the blend, badge SQL or replay; no return of a handled suggestion; no dismissal as a ranking signal; no Preview call except on an explicit click; no re-show on reopen; no topic saved on the user's behalf. Disposition: verified.

### Required Artifacts

Every artifact in the prior report is unchanged in code. The only edited source file, `hooks/engagementReaction.ts`, changed in its doc comment only. All are ✓ VERIFIED (exists, substantive, wired). The table in the prior report (fa11ff9) still applies line for line.

### Key Link Verification

All 11 links from the prior report are WIRED. No executable line in a linking file changed: Controller to Service, Service to `displayScores`/near-miss, Store to dismissal table, `InterestController` to `InterestService` to `TopicSuggestionStore.handle`, dialog to hooks to api, `SuggestedTopics` to `addDraft`, `TopicRow` to api, and `FeedbackNotice` to dialog.

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| SuggestedTopics | `suggestions.data.items` | `GET /api/interest/suggestions` → `TopicSuggestionStore.candidates` SQL + `displayScores` | Yes | ✓ FLOWING |
| InterestBadge in row | `s.interestScore` | `InterestScoreQueries.displayScores` | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Type-check | `cd src/main/frontend && npx tsc -b` | clean (run 2026-10-01T23:45Z) | ✓ PASS |
| Phase 11 frontend tests | `npx vitest run` on engagementReaction, useInterest, InterestsDialog, TopicRow, FeedbackNotice, ReadingPane tests | 6 files, 146 tests pass (run 2026-10-01T23:45Z) | ✓ PASS |
| Phase 11 backend tests | not re-run: no backend file changed since the prior run | Existing results: `TopicSuggestionApiIntegrationTest` 18/0 failures, `TopicSuggestionStoreTest` 17/0, `TopicSuggestionServiceTest` 6/0 (stamped 23:11Z, after the last backend commit at 22:50Z) | ✓ PASS (carried) |

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

No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all modified files) | - | TBD/FIXME/XXX/TODO/HACK | none | `engagementReaction.ts` re-scanned: no markers |
| `CLAUDE.md` / `engagementReaction.ts` | 197 / 12-16 | WR-01 doc overstatement | resolved | Corrected in 55c9817; the wording now matches `ON CONFLICT (article_id, kind) DO NOTHING` + `HAVING MAX(g.created_at) > :cutoff` |
| `src/main/frontend/src/hooks/useInterest.ts` | 206-215 | Dismiss `total - 1` not idempotent (review WR-02, still open) | ⚠️ Warning | The heading count can drop too far for a moment after a double dismiss. Server state is correct and the refetch corrects the display. No success criterion is affected |
| `InterestsDialog.tsx` | 637-642 | Heading count uses unfiltered items (IN-01, open) | ℹ️ Info | Transient |

### Human Verification Required

None. All three items from the prior report were resolved in `11-UAT.md` (status complete, 3 passed, 0 issues):

1. End-to-end create-from-suggestion and dismiss, with reloads: pass.
2. Visual check in all 6 themes: pass.
3. WR-01 decision: the first recording per engagement kind is accepted as the D-06 window, and the docs were corrected (verified above against the code).

### Gaps Summary

There are no gaps. The phase goal is achieved:

- The backend lists engaged, SCORED, unvoted and undismissed articles that are not near misses, using stored data only.
- The Interests dialog shows them and turns one into a prefilled draft. Saving that draft marks the article handled in the same transaction.
- Dismissing is permanent, and nothing calls Jev.

The only change since the prior report is documentation, and it now describes the code correctly. Human UAT is complete. The open review items WR-02 and IN-01 are transient client-side display issues and do not affect any success criterion.

---

_Verified: 2026-10-01T23:47:00Z_
_Verifier: Claude (gsd-verifier)_
