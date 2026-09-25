---
phase: 05-blend-priority-view
verified: 2026-09-25T20:45:00Z
status: human_needed
score: 5/5 roadmap success criteria verified (8/8 requirements satisfied; plan must-have truths verified except 7 backstop/visual truths routed to human)
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/05-blend-priority-view/05-01-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-01-SUMMARY.md"
  - ".planning/phases/05-blend-priority-view/05-02-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-02-SUMMARY.md"
  - ".planning/phases/05-blend-priority-view/05-03-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-03-SUMMARY.md"
  - ".planning/phases/05-blend-priority-view/05-04-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-04-SUMMARY.md"
  - ".planning/phases/05-blend-priority-view/05-05-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-05-SUMMARY.md"
  - ".planning/phases/05-blend-priority-view/05-06-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-06-SUMMARY.md"
  - ".planning/phases/05-blend-priority-view/05-07-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-07-SUMMARY.md"
  - "src/main/frontend/src/App.css"
  - "src/main/frontend/src/App.tsx"
  - "src/main/frontend/src/api/articles.ts"
  - "src/main/frontend/src/components/ArticleList.test.tsx"
  - "src/main/frontend/src/components/ArticleList.tsx"
  - "src/main/frontend/src/components/BoardArticleList.test.tsx"
  - "src/main/frontend/src/components/BoardArticleList.tsx"
  - "src/main/frontend/src/components/EmptyState.tsx"
  - "src/main/frontend/src/components/FeedPanel.test.tsx"
  - "src/main/frontend/src/components/FeedPanel.tsx"
  - "src/main/frontend/src/components/InterestBadge.test.tsx"
  - "src/main/frontend/src/components/InterestBadge.tsx"
  - "src/main/frontend/src/components/InterestsDialog.tsx"
  - "src/main/frontend/src/components/PriorityBanner.test.tsx"
  - "src/main/frontend/src/components/PriorityBanner.tsx"
  - "src/main/frontend/src/components/PriorityList.test.tsx"
  - "src/main/frontend/src/components/PriorityList.tsx"
  - "src/main/frontend/src/components/ReadingPane.test.tsx"
  - "src/main/frontend/src/components/ReadingPane.tsx"
  - "src/main/frontend/src/components/ScoreRow.tsx"
  - "src/main/frontend/src/components/ShortcutOverlay.tsx"
  - "src/main/frontend/src/components/WhyBreakdown.test.tsx"
  - "src/main/frontend/src/components/WhyBreakdown.tsx"
  - "src/main/frontend/src/hooks/useArticles.ts"
  - "src/main/frontend/src/hooks/useInterest.ts"
  - "src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts"
  - "src/main/frontend/src/hooks/useKeyboardShortcuts.ts"
  - "src/main/frontend/src/hooks/usePriorityArticles.test.ts"
  - "src/main/frontend/src/hooks/usePriorityArticles.ts"
  - "src/main/frontend/src/stores/priorityStore.ts"
  - "src/main/frontend/src/types/index.ts"
  - "src/main/frontend/src/utils/interest.test.ts"
  - "src/main/frontend/src/utils/interest.ts"
  - "src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java"
  - "src/main/java/org/bartram/myfeeder/config/SpaForwardController.java"
  - "src/main/java/org/bartram/myfeeder/controller/ArticleController.java"
  - "src/main/java/org/bartram/myfeeder/model/Article.java"
  - "src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java"
  - "src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java"
  - "src/main/java/org/bartram/myfeeder/service/ArticleService.java"
  - "src/main/java/org/bartram/myfeeder/service/BoardService.java"
  - "src/main/java/org/bartram/myfeeder/service/PriorityService.java"
  - "src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java"
  - "src/main/resources/application.yaml"
  - "src/test/java/org/bartram/myfeeder/config/SpaForwardControllerTest.java"
  - "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java"
  - "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java"
  - "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java"
  - "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java"
  - "src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java"
  - "src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java"
  - "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java"
  - "src/test/resources/application.yaml"
covered_digest: "v1:sha256:4a9545f30a7136beefadccf14bee2355ec4f181e3482a2181d9addd4d02d19ec"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Live triage walkthrough (05-06 deferred human-check + 05-VALIDATION manual row): ./gradlew bootTestRun + npm run dev; open /priority, mark several rows read (m and auto-mark-read), star one, switch windows and back, wait 60s; save a topic weight in the Interests dialog; press r; reload the browser on /priority"
    expected: "Rows stay in place and dimmed through focus changes and waiting; the weight save lights '↻ Ranking changed — refresh' and nothing moves; r re-ranks from page 1 and read rows are gone; reload on /priority serves the app (not a 404)"
    why_human: "Real refetch timing, focus events and the SPA reload path across a live backend; unit tests stub fetch and do not exercise the browser lifecycle"
  - test: "Reading-pane walkthrough (05-07 deferred human-check): select a scored article, check badge and chips under the title, press i, move with j, open the non-matching footer, move again"
    expected: "Why N? opens; its last line equals the badge; the breakdown stays open across j; the non-matching footer collapses on article change"
    why_human: "End-to-end keyboard + live data flow in a real browser"
  - test: "Visual check across all 6 themes (3 dark, 3 light): badge pill sizing (2.75em, tabular numerals, 100 fits), tier colors high/neutral/low, read-row dimming, 'Not yet scored' separator strip, status banner, hint-lit refresh label"
    expected: "Tiers are distinguishable and legible in every theme; 100 fits the pill; dimmed read badges still readable"
    why_human: "jsdom does not lay out CSS or render colors"
  - test: "Narrow-width layout (backstop truths): very long article title in Priority/list rows; banner + cold-start button at minimum list-panel width; '↻ Ranking changed — refresh' beside the toolbar title; many matched chips in a narrow reading pane; long topic name in a Why breakdown row"
    expected: "Titles wrap under themselves without pushing the badge; banner and button wrap with no horizontal scroll; hint label fits or wraps without clipping; chips wrap inside .score-row; long breakdown labels wrap in the 1fr column and never overlap the points column"
    why_human: "Layout/overflow behavior (verification: backstop) is not observable in jsdom"
  - test: "Backstop numeric truth (05-01): casting the 6-decimal numeric raw to float8 for the sort key never merges two distinct raws"
    expected: "Accept the argument: |raw| is bounded (profile 100 + topics x |weight| <= 50, far below 10^4), so a 6-decimal raw has at most ~10 significant digits and float8 represents 15+; the cast is monotone and injective on that domain. Tied-raw paging (82.2 x3) and paged walks of 3 and 5 pass against real Postgres"
    why_human: "Truth is tagged verification: backstop; the passing tests are example-based, not a property/held-out test, so an explicit human acceptance is required"
  - test: "StrictMode dev double-fetch (05-06 backstop A7): in npm run dev, open /priority for the first time"
    expected: "Page 1 may be requested twice in development only; the list is correct either way; production build mounts once"
    why_human: "Dev-only React StrictMode behavior (verification: backstop)"
  - test: "Decide on code-review warnings WR-01..WR-03 (see Anti-Patterns): accept for Phase 5 or schedule fixes (recommended before Phase 6, which makes WR-02 routine)"
    expected: "A recorded decision: fix now, fix in Phase 6, or accept"
    why_human: "Consistency-under-concurrent-change issues that do not break any must-have on a stable dataset but weaken 'adds up exactly' and 'no gaps' guarantees under live rubric changes; a scope decision, not a code check"
---

# Phase 5: Blend & Priority View Verification Report

**Phase Goal:** The user can open a Priority view where the unread articles they care about most come first, each with a score badge and an exact explanation of that score
**Verified:** 2026-09-25T20:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can open Priority from the feed tree or with `g p` (route `/priority`); unread scored articles by score (ties by date), then "Not yet scored" separator, then unscored by date; paging crosses the boundary with no duplicates or gaps | ✓ VERIFIED | `FeedPanel.tsx:237-240` first smart view, `useMatch('/priority')` active state; `useKeyboardShortcuts.ts:70` `g`→`p` navigates before the plain `p` branch; `App.tsx:120` route; `SpaForwardController` forwards `/priority`. `InterestScoreQueries` keyed order `sort_score DESC, sort_date DESC, id DESC` with `'-Infinity'` for unscored; `PriorityList.tsx:83,107` separator once before first unscored row. Tests passing (re-run): `fullOrderIsScoreThenDateThenId`, `pagedWalksMatchTheUnpagedOrder` (pages of 3 and 5), `cursorReadBetweenPagesStillContinues`, `rankedWalkCrossesIntoUnscoredWithNoDuplicates` (HTTP), `gThenPOpensPriority`, `gThenPDoesNotJumpToThePreviousUnreadFeed`, `priorityIsActiveOnlyOnItsRoute`, `separatorPrecedesTheFirstUnscoredRow`. Note: "infinite scroll" is realized as the Load more button plus `j` past the last row auto-paging (CONTEXT D-10, user decision); see WR-02 for the live-rescoring skip caveat. |
| 2 | Tier-colored 0–100 badge in the list and reading pane (none for unscored); "Why N?" (or `i`) shows profile contribution plus each matched topic's match × weight, adding up exactly to the badge value, with matched-topic chips | ✓ VERIFIED | Badge: `InterestBadge.tsx` returns null for null/undefined; tiers via `--accent`/`--text-secondary`/`--text-muted` theme vars (`App.css:288-304`); used in `PriorityList`, `ArticleList`, `BoardArticleList`, `ScoreRow`. Server: `INTEREST_SCORE` CASE guard keeps unscored null; `withScores` enriches list/board/PATCH; `findByIdWithBreakdown` on GET by id. Breakdown: SQL `TOTAL`/`INTEREST_SCORE` from one CTE, `ScoreBreakdowns.apportion` largest-remainder sums to `total`; `WhyBreakdown.tsx` prints server points and capped/floored last line; `ScoreRow.tsx` chips + Why toggle; `i` → `toggleWhy`. Tests: `oneNumberEverywhere`, `breakdownInputsMatchTheBadge`, `rowsSumToTotalAcrossCases`, `uiMockRowsAddUpToTheBadge`, `articleByIdCarriesAnExactBreakdown`, `rendersServerPointsWithoutRecomputing`, `cappedTotalLine`, `flooredTotalLine`, `scoreRowShowsBadgeChipsAndWhy`, `unscoredArticleHasNoScoreRow`, `iTogglesTheBreakdownForAScoredArticle`, `tiersAreInclusiveAtTheLowEnd`, `rendersNothingForUnscored`, `scoredZeroIsShown`. Caveat WR-01 (two-statement read under concurrent writes). |
| 3 | Marking read or starring in Priority does not reorder or drop rows until refresh or re-entry; `j`/`k` walk the ranked order; `Shift+A` disabled | ✓ VERIFIED | `PRIORITY_KEY=['priority']` outside `['articles']`; `staleTime: Infinity`, no focus/reconnect refetch; `useUpdateArticleState.onSuccess` calls `patchPriorityArticle` (in-place, read/starred only); leaving `/priority` removes the cache (`App.tsx:94-99`). Keyboard list is `priority.rows` on `/priority`; `case 'A'` guarded by `!callbacks.isPriority`. Tests: `markReadPatchesTheRowInPlace`, `patchKeepsTheListedScore`, `articlesInvalidationDoesNotRefetchPriority`, `focusAndReconnectDoNotRefetch`, `remountDoesNotRefetch`, `leavingAndReenteringFetchesPageOneFresh`, `markReadKeepsTheRowInPlaceDimmed`, `jWalksPriorityRowsIncludingReadOnes`, `jOnTheLastPriorityRowLoadsAndSelectsTheNextPage`, `shiftAIsANoOpOnPriority`. |
| 4 | Priority view shows "not configured", "cold start", "scoring paused" or "N articles waiting to be scored" when each applies | ✓ VERIFIED | `PriorityBanner.tsx` precedence not-configured → cold start → OPEN/FORCED_OPEN → waiting>0 → none; reads existing `useInterestStatus`; never shows `failed`; rendered above the filter and list in `PriorityList.tsx:161`. Tests: `notConfiguredWinsOverEverything`, `coldStartWinsOverPaused`, `pausedShowsTheWaitingCount`, `pausedWithNothingWaiting`, `halfOpenIsNotPaused`, `waitingUsesThousandsSeparators`, `failedCountIsNeverShown`, `bannerSitsAboveTheListAndNeverReplacesIt`, `coldStartButtonCallsOnSetUpInterests`. |
| 5 | Changing a topic's weight changes badges and Priority order on the next refresh, with no new Jev calls | ✓ VERIFIED | Blend computed at query time from `article_score`/`article_topic_score`/`interest_topic.weight`; `InterestScoreQueries` depends only on `JdbcClient` + `MyfeederProperties` (no Jev client). Topic mutations light the hint (`useInterest.ts` 5x `setRankingChanged(true)`); `refreshPriority` resets page 1 and invalidates by-id articles. Tests: `weightChangeReordersWithoutRescoring` (real Postgres), `deletedTopicContributionCascadesAway`, `topicAddedAfterScoringContributesNothing`, `interestSaveLightsTheHint`, `refreshFetchesOnlyPageOne`. Caveat WR-03: non-Priority list badges and an open reading pane refresh only when their own queries refetch (30s staleTime), not on the topic save itself. |

**Score:** 5/5 roadmap truths verified (0 present, behavior-unverified)

### Plan must-have truths

All non-backstop plan truths across 05-01..05-07 were checked against code and map to named passing tests (see test lists above plus `priorityClampsLimit`, `priorityRouteIsNotTheIdRoute`, `priorityMissingCursorIs404`, `missingCursorIs404`, `missingCursorRestartsFromPageOne`, `displayIsClampedAndRoundsHalfAwayFromZero` (28.5→29), `displayScoresAreIdScopedIncludingReadArticles`, `listItemsOmitTheBreakdown`, `noulAtExactlyHalfIsNonMatching`, `weightZeroMatchIsARowWithZeroPoints`, `levelIndexScalesToTheRubric`, `boardRowsShowBadges`, `noSlotWhenNothingIsScored`, `rowsKeepTheirQueryOrder`, `footerCollapsesOnArticleChange`, `iDoesNothingForAnUnscoredArticle`, `iIsIgnoredWhileTyping`, `rRefreshesTheRankingOnPriority`, `refreshKeepsTheSelection`, `loadMoreFailureRelabelsAndRetries`). The 7 `verification: backstop` truths (float8 cast injectivity, long-title wrap, banner wrap, hint label fit, StrictMode double-fetch, chip wrap, breakdown label wrap) are `insufficient_spec` and routed to Human Verification.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `repository/InterestScoreQueries.java` | Single blend CTE (learned→eff→contrib→blended), keyset pages, displayScores, breakdownInputs | ✓ VERIFIED | `learned AS` returns 0 per topic; all queries built from `blendCte`; bound params only |
| `service/PriorityService.java` | Cursor existence 404 + delegation | ✓ VERIFIED | `existsById` → `NotFoundException` |
| `controller/ArticleController.java` | `GET /priority` → `PaginatedResponse` | ✓ VERIFIED | Clamp [1,100], `limit + 1`, `PaginatedResponse.of` |
| `model/Article.java` | `@Transient interestScore`, `interestBreakdown` NON_NULL | ✓ VERIFIED | lines 28-34 |
| `model/InterestBreakdown.java` | Breakdown records | ✓ VERIFIED | raw/total/display/rows/nonMatching |
| `service/ScoreBreakdowns.java` | Apportion, ordering, level index | ✓ VERIFIED | Uses `InterestQuestions.PROFILE_MAX_LEVEL` |
| `hooks/usePriorityArticles.ts` | PRIORITY_KEY, dedupe, infinite query, refresh, patch | ✓ VERIFIED | `staleTime: Infinity`, `setQueriesData` |
| `components/PriorityList.tsx` | Priority panel | ✓ VERIFIED | Separator, slot, states, refresh/hint, banner |
| `components/InterestBadge.tsx` | Tier badge | ✓ VERIFIED | null → nothing |
| `components/PriorityBanner.tsx` | Status banner | ✓ VERIFIED | `role="status"` |
| `components/FeedPanel.tsx` | Priority smart view | ✓ VERIFIED | `useMatch('/priority')` |
| `components/ArticleList.tsx`, `BoardArticleList.tsx` | Badge or reserved slot | ✓ VERIFIED | `reserveSlot` only when any row scored |
| `stores/priorityStore.ts` | Hint, baseline, whyOpen | ✓ VERIFIED | non-persisted `create` |
| `components/ScoreRow.tsx` | Badge, chips, Why toggle | ✓ VERIFIED | `aria-controls="why-breakdown"` |
| `components/WhyBreakdown.tsx` | Rows, capped/floored line, footer | ✓ VERIFIED | contains `capped at 100` |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| ArticleController | PriorityService | `priorityService.page(before, safeLimit + 1)` | ✓ WIRED |
| InterestScoreQueries | V6 schema | `article_score`, `article_topic_score`, `interest_topic` | ✓ WIRED |
| InterestScoreQueries | MyfeederProperties | `getProfilePoints()` bound as `:profilePoints` | ✓ WIRED |
| ArticleService / BoardService | InterestScoreQueries | `displayScores`, `breakdownInputs` | ✓ WIRED |
| ArticleController | ArticleService | `getArticle` → `findByIdWithBreakdown` | ✓ WIRED |
| ScoreBreakdowns | InterestQuestions | `PROFILE_MAX_LEVEL` | ✓ WIRED |
| App.tsx | PriorityList | `<Route path="/priority">` before `*`, `onSetUpInterests` | ✓ WIRED |
| usePriorityArticles | api/articles | `articlesApi.priority(50, pageParam)` | ✓ WIRED |
| PriorityList / ArticleList / BoardArticleList / ScoreRow | InterestBadge | `<InterestBadge` | ✓ WIRED |
| PriorityBanner | useInterest | `useInterestStatus()` | ✓ WIRED |
| InterestsDialog | utils/interest | `OPEN_BREAKER_STATES`, `articles` imported | ✓ WIRED |
| useArticles | usePriorityArticles | `patchPriorityArticle` in `useUpdateArticleState.onSuccess` | ✓ WIRED |
| App.tsx | useKeyboardShortcuts | `priority.rows`, `isPriority`, `onPriorityNextPage`, `onPriorityRefresh` | ✓ WIRED |
| useInterest | priorityStore | `setRankingChanged(true)` x5 | ✓ WIRED |
| ReadingPane | ScoreRow / WhyBreakdown | rendered after `h1.article-title`, before `.article-meta` | ✓ WIRED |
| ScoreRow / useKeyboardShortcuts | priorityStore | `toggleWhy` | ✓ WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PriorityList | `rows` | `GET /api/articles/priority` → `InterestScoreQueries.priorityFirstPage/priorityPageAfter` (SQL over article + score tables) | Yes | ✓ FLOWING |
| ArticleList / BoardArticleList badge | `article.interestScore` | `findFiltered`/board pages → `withScores` → `displayScores` SQL | Yes | ✓ FLOWING |
| ScoreRow / WhyBreakdown | `article.interestScore`, `interestBreakdown` | `useArticle(id)` → `GET /api/articles/{id}` → `breakdownInputs` SQL + `ScoreBreakdowns.build` | Yes | ✓ FLOWING |
| PriorityBanner | `status` | `useInterestStatus` → `/api/interest/status` (Phase 4) | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Blend order, paging, null guard, rounding, weight change, breakdown | `./gradlew test --tests InterestScoreQueriesTest PriorityApiIntegrationTest ScoreBreakdownsTest PriorityServiceTest ArticleControllerTest ArticleServiceTest BoardServiceTest SpaForwardControllerTest` | 18+4+14+3+22+14+6+2 = 83 tests, 0 failures, 0 errors | ✓ PASS |
| Dev-profile / yaml parity with blend key | `./gradlew test --tests org.bartram.myfeeder.DevProfileConfigTest` | 4 tests, 0 failures | ✓ PASS |
| Frontend type-check | `npx tsc -b` | exit 0 | ✓ PASS |
| Frontend suite (single full run) | `npx vitest run` | 23 files, 218 tests passed | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED (no probe scripts declared by the phase; not a migration/tooling phase).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PRIO-01 | 05-01, 05-02, 05-04 | Priority view from feed tree, `/priority`, unread by score, ties by date | ✓ SATISFIED | SC1 evidence |
| PRIO-02 | 05-01, 05-02 | Unscored after scored, by date, below separator | ✓ SATISFIED | `-Infinity` sort key; separator tests |
| PRIO-03 | 05-01, 05-02, 05-03, 05-05, 05-07 | Tier-colored 0–100 badge in list and reading pane; none for unscored | ✓ SATISFIED | SC2 evidence (visual tiers → human) |
| PRIO-04 | 05-03, 05-07 | Exact Why N? breakdown with chips | ✓ SATISFIED | SC2 evidence; WR-01 caveat |
| PRIO-05 | 05-06 | Stable order while triaging (read/star; voting arrives in Phase 6 via the same `patchPriorityArticle`) | ✓ SATISFIED | SC3 evidence |
| PRIO-06 | 05-01, 05-02, 05-06 | Cursor pagination via `PaginatedResponse` across boundary | ✓ SATISFIED | paged walk tests; WR-02 caveat |
| PRIO-07 | 05-04, 05-06, 05-07 | `g p`, `j`/`k`, `i`, `Shift+A` disabled | ✓ SATISFIED | keyboard tests |
| PRIO-08 | 05-04 | Four status states | ✓ SATISFIED | banner tests |

All 8 phase requirement IDs appear in plan frontmatter; REQUIREMENTS.md maps no additional IDs to Phase 5. No orphaned requirements.

### Prohibitions

All plan prohibitions are `verification: test` and each has wired enforcement: null-never-0 (`unscoredRowsHaveNullInterestScore`, `rendersNothingForUnscored`, `noZeroForUnscored`, `unscoredArticleHasNoScoreRow`); no Jev calls (query class has no Jev dependency; no Jev import in the read path); read-only Priority path (SELECT-only SQL); no new Flyway migration (`git diff 9903ead..HEAD -- src/main/resources/db/migration` is empty; V6 remains last); no silent re-rank (`articlesInvalidationDoesNotRefetchPriority`, `focusAndReconnectDoNotRefetch`, `remountDoesNotRefetch`); no client recompute (`rendersServerPointsWithoutRecomputing`); no bulk mark-read in Priority (toolbar has only refresh; `shiftAIsANoOpOnPriority`); non-Priority lists not sorted by score (`rowsKeepTheirQueryOrder`). The commit-hygiene prohibition is process-level; the working tree shows the user's pre-existing modified files (CLAUDE.md, .claude/CLAUDE.md, .envrc, .planning/config.json) still unstaged, consistent with it.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all 53 changed src files) | — | TBD/FIXME/XXX/TODO/HACK | — | None found (the only "PLACEHOLDER" hits are the Interests dialog's input placeholder text) |
| `repository/InterestScoreQueries.java` | 144-181 | WR-01: breakdown header and topic rows read in two statements without a shared snapshot | ⚠️ Warning | A rubric write between the statements can make rows not sum to `total` (apportion clamps silently). Holds for any consistent snapshot; single-user race window is milliseconds. Fix: one `REPEATABLE_READ` read-only transaction or a single statement. |
| `repository/InterestScoreQueries.java` | 105-116 | WR-02: id-only cursor re-derives the cursor's live sort key | ⚠️ Warning | If the cursor article's score drops mid-walk (weight edit/topic delete now; thumbs in Phase 6), rows between old and new position are skipped silently. Also an article scored mid-walk with a score above the cursor is not shown in that walk. Duplicates are deduped client-side; the "Ranking changed" hint partially mitigates. Strongly recommended before Phase 6. |
| `hooks/useInterest.ts` | 78-83, 104-108, 134-137 | WR-03: topic/rescore mutations don't invalidate `['article', id]`, `['articles']`, board pages | ⚠️ Warning | Reading-pane badge/chips/Why and non-Priority list badges stay stale until their queries refetch (30s staleTime). Priority itself is correct via refresh. |
| review IN-01..IN-07 | — | duplication, separator wording, async j selection, dangling aria-controls, −0.0 tooltip, unvalidated profile-points | ℹ️ Info | No must-have impact |

### Human Verification Required

1. **Live triage walkthrough** — bootTestRun + dev server; mark read/star, refocus, wait, save a topic weight, press r, reload on /priority. Expected: rows stay put and dim; hint lights; r re-ranks page 1; reload serves the app.
2. **Reading-pane walkthrough** — i opens Why N?, last line equals the badge, stays open across j, non-matching footer collapses on article change.
3. **Six-theme visual check** — badge pill size/tiers, read dimming, separator strip, banner, hint label.
4. **Narrow-width layout (backstop truths)** — long titles, banner wrap, hint label fit, chip wrap, breakdown label wrap.
5. **Backstop numeric truth** — accept the float8 cast injectivity argument (bounded raw, 6 decimals, ~10 significant digits vs float8's 15+).
6. **StrictMode dev double-fetch** — confirm dev-only, list correct.
7. **Decision on WR-01..WR-03** — fix now, fix in Phase 6, or accept.

### Gaps Summary

No blocking gaps. Every roadmap success criterion and all 8 PRIO requirements are implemented, wired end to end (SQL blend → service → controller → hooks → components), and exercised by passing tests that I re-ran (83 targeted backend tests + DevProfileConfigTest; tsc; 218 frontend tests). The phase goal is achieved on a stable dataset. What remains is (a) visual/layout and live-browser checks that jsdom cannot see, (b) human acceptance of backstop-tagged truths, and (c) a scope decision on three consistency-under-concurrent-change warnings from the code review, of which WR-02 (id-only cursor skipping rows when the cursor's live score drops) is the one most worth fixing before Phase 6's thumbs make it routine.

---

_Verified: 2026-09-25T20:45:00Z_
_Verifier: Claude (gsd-verifier)_
