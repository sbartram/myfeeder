---
phase: 05-blend-priority-view
verified: 2026-09-26T15:58:00Z
status: passed
score: 5/5 roadmap success criteria verified; 9/9 05-08 must-have truths verified; 8/8 requirements satisfied; no regressions against 05-01..05-07 must-haves
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
  - ".planning/phases/05-blend-priority-view/05-08-PLAN.md"
  - ".planning/phases/05-blend-priority-view/05-08-SUMMARY.md"
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
  - "src/main/java/org/bartram/myfeeder/controller/PriorityPage.java"
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
  - "src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java"
  - "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java"
  - "src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java"
  - "src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java"
  - "src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java"
  - "src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java"
  - "src/test/resources/application.yaml"
covered_digest: "v1:sha256:ac4bacab0e6948a367c6c71c17def2c388b6f90d84629ac14a0fb09e02cc8f72"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: "5/5 roadmap success criteria verified (7 human items, walked in 05-UAT.md: 6 passed, test 7 raised G-05-7)"
  gaps_closed:
    - "G-05-7 / WR-02: Priority keyset pagination never silently skips rows when the cursor article's live score changes between pages"
  gaps_remaining: []
  regressions: []
---

# Phase 5: Blend & Priority View Verification Report

**Phase Goal:** The user can open a Priority view where the unread articles they care about most come first, each with a score badge and an exact explanation of that score
**Verified:** 2026-09-26T15:58:00Z
**Status:** passed
**Re-verification:** Yes, after gap closure (05-08 closes UAT gap G-05-7 / review WR-02)

## Scope of this re-verification

The previous report (2026-09-25, `human_needed`) had no `gaps:`. Its 7 human items were walked in `05-UAT.md`: tests 1-6 passed; test 7 (decision on WR-01..03) produced gap G-05-7 ("fix WR-02 now, defer WR-01 and WR-03"). Plan 05-08 (`gap_closure: true`, `gap_ids: [G-05-7]`) then ran: commits `7b3f64a` (interface), `0c38095` (RED), `7fdd313` (GREEN), `bfc636b` (SPA), `2fe827e` (docs). This report does a full 3-level check of 05-08's must-haves and a regression check of the 05-01..05-07 must-haves that 05-08's changes touch (cursor, page queries, controller, Priority page type). 05-08 changed only the cursor contract, so UAT tests 1-6 (triage, reading pane, themes, narrow layout, float8 precision, StrictMode) are not re-raised.

## Goal Achievement

### Observable Truths: 05-08 must-haves (gap closure)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | G-05-7 / WR-02: lowering the cursor article's score between pages still returns every unread article that ranked below the cursor when the page was served | ✓ VERIFIED | `InterestScoreQueries.priorityPageAfter` (lines 114-126) now compares `(k.sort_score, k.sort_date, k.id) < (CAST(:cursorScore AS float8), CAST(:cursorDate AS timestamptz), CAST(:cursorId AS bigint))`, bound from the decoded `SortKey`. The correlated `SELECT … FROM keyed c WHERE c.id = :cursorId` and `UNREAD_OR_CURSOR_SCOPE` are gone from `src/main` and `src/test` (grep empty). RED `0c38095` came right before GREEN `7fdd313`. HTTP test `cursorScoreDropMidWalkSkipsNoRow` passed in my run: it lowers the topic weight to -50 right after top's page is served and asserts mid is present. |
| 2 | Edge drop, exact sequence a4,a3,a1,a2,a8,a6,a1,a7,a5,u1,u3,u5,u2,u4 | ✓ VERIFIED | `cursorScoreDropBetweenPagesSkipsNothing` asserts exactly this sequence after `profile_score = 0` for a1. It passed against real Postgres. |
| 3 | Edge rise: after page 1 (a4,a3,a1), raising a1 to 118.2 makes page 2 exactly a2,a8,a6 | ✓ VERIFIED | `cursorScoreRiseBetweenPagesRepeatsNothing` passed. |
| 4 | `{items, nextCursor}`, with nextCursor null on the last page and otherwise an opaque URL-safe encoding of the served (sort_score, sort_date, id). The next page never reads the cursor's live score | ✓ VERIFIED | `PriorityPage.of` encodes `kept.getLast().key()` only when there is a look-ahead row. The key comes from `mapPriorityRow` (the served `sort_score`/`sort_date`/`id`). Tests `priorityTrimsLookAheadRowAndSetsNextCursor`, `ofTrimsTheLookAheadRowAndEncodesTheLastKeptKey` and `cursorIsUrlSafe` pass. The SQL binds only literal cursor parts (truth 1). |
| 5 | Precision: lossless round trip (including -Infinity and microseconds). Walks in pages of 2/3/5 match the unpaged order across the boundary. Zone-independent | ✓ VERIFIED | `cursorRoundTripsExactly` covers -Infinity, a pre-epoch micro date and Long.MAX id. `cursorWireFormatIsPinned` passes. `servedKeysCarryTheSortTuple` checks exact 82.2/133.32, -Infinity for unscored rows and fetched_at for undated rows. `pagedWalksMatchTheUnpagedOrder` covers pages of 3 and 5. Pages of 2 are covered over HTTP by `rankedWalkCrossesIntoUnscoredWithNoDuplicates` (limit 2 across -Infinity). `cursorWalkIsZoneIndependent` covers Asia/Kolkata. The date is bound as `atOffset(ZoneOffset.UTC)`. |
| 6 | R4 kept: a cursor marked read continues exactly; a well-formed cursor for a gone article is 404 "Article not found: <id>" | ✓ VERIFIED | `cursorReadBetweenPagesStillContinues` covers both scored and unscored cursors. `PriorityService.page` does `existsById(after.id())`, which throws `NotFoundException("Article not found: " + id)`. Tests `missingCursorIs404` (HTTP, encoded gone id) and `priorityMissingCursorIs404` pass. |
| 7 | An undecodable cursor (legacy numeric id included) is 404 with fixed detail "Priority cursor not recognized"; it never echoes the input and the page query does not run | ✓ VERIFIED | `decodeCursor` maps every shape, parse, NaN, base64 and arithmetic failure to `NotFoundException(UNREADABLE_CURSOR)`. `unreadableCursorIsNotFound` covers 9 inputs including "12345" and asserts no echo. `priorityUnreadableCursorIs404WithoutCallingTheService` asserts `$.detail` and `never().page(...)`. For WR-04 (a crafted, decodable cursor with an out-of-range date gives a 500), see the Anti-Patterns table: it does not violate this truth. |
| 8 | Frontend passes the opaque cursor back verbatim; `nextCursor: string \| null`; frozen-page policy and dedupe unchanged; tsc and tests pass | ✓ VERIFIED | `types/index.ts` adds `PriorityPage { nextCursor: string \| null }`. `articlesApi.priority(limit, before?: string)` sets `before` verbatim. The hook uses `initialPageParam: undefined as string \| undefined`, and `getNextPageParam` is unchanged. `PriorityList.tsx` is untouched: its 404 path at line 67 calls `refreshPriority`. `PriorityList.test.tsx` uses the pinned real cursor in every `pageAfter`, including the 404-restart test. My runs: `npx tsc -b` exit 0; plan-touched files 39/39 pass. |
| 9 | PaginatedResponse, article-list and board cursors unchanged; no Flyway migration | ✓ VERIFIED | `git diff --name-only 19443be HEAD` over `PaginatedResponse.java`, `BoardController.java`, `db/migration`, `ArticleService.java`, `ScoreBreakdowns.java`, `useInterest.ts`, `PriorityList.tsx`, `useArticles.ts`, `useBoards.ts` and `api/boards.ts` is empty. `PaginatedResponseTest` passes. V6 is still the last migration. |

**05-08 score:** 9/9 verified (0 present but behavior-unverified).

### Observable Truths: Roadmap Success Criteria (regression check)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Priority from the feed tree or `g p` (`/priority`); scored by score then date, a "Not yet scored" separator, then unscored by date; infinite scroll crosses the boundary with no duplicates or gaps | ✓ VERIFIED | Unchanged routing and ordering code (see the previous report). Paging is now also gap-free when the cursor article's score changes (truths 1-5 above). `KEYED_ORDER` and `priorityFirstPage` SQL are unchanged. `fullOrderIsScoreThenDateThenId`, `pagedWalksMatchTheUnpagedOrder` and `rankedWalkCrossesIntoUnscoredWithNoDuplicates` pass in my run. |
| 2 | Tier-colored badge in the list and reading pane; "Why N?"/`i` adds up exactly | ✓ VERIFIED | 05-08 touched no badge or breakdown code (ArticleService, ScoreBreakdowns and the components are outside its diff). `ScoreBreakdownsTest` (14), `ArticleServiceTest` (14), `BoardServiceTest` (6) and `InterestScoreQueriesTest` breakdown cases pass. UAT tests 2-3 passed. |
| 3 | Triage in Priority doesn't reorder or drop rows; `j`/`k` walk; `Shift+A` disabled | ✓ VERIFIED | `patchPriorityArticle` and `dedupeById` only changed type (`PriorityPage`). `usePriorityArticles.test.ts` (frozen cache, patch in place, no refetch on focus) passes. UAT test 1 passed. |
| 4 | Four status states | ✓ VERIFIED | Untouched by 05-08. UAT passed. Orchestrator full frontend run: 218/218. |
| 5 | Weight change reorders on refresh with no Jev calls | ✓ VERIFIED | `weightChangeReordersWithoutRescoring` passes. `InterestScoreQueries` still depends only on `JdbcClient` and `MyfeederProperties`. |

**Score:** 5/5 roadmap truths verified; 9/9 05-08 truths verified; 0 behavior-unverified.

### Earlier plan must-haves touched by 05-08 (regression)

| Plan truth | Status | Evidence |
|------------|--------|----------|
| 05-01: walks of 3 and 5 match the unpaged order; nextCursor null when rows ≤ limit; empty first page with no unread articles | ✓ no regression | Tests pass. `PriorityPage.of` mirrors the limit+1 rule. |
| 05-01 R4: marking the cursor read (scored and unscored) still continues exactly | ✓ no regression | Now holds by construction: the statement never reads the cursor row. The test passes. |
| 05-01: a cursor id matching no article is 404, never a silent empty page | ✓ no regression (amended per 05-08) | A gone encoded cursor gives 404 "Article not found"; a legacy numeric id gives 404 "Priority cursor not recognized". |
| 05-02: Load more sends `before=<nextCursor>` and dedupes by id; a 404 restarts from page 1 | ✓ no regression | Pinned cursor in the `pageAfter(CURSOR)` tests, including the 404-restart test (`PriorityList.test.tsx:415-422`). |
| 05-06: marking the last loaded row read before Load more still continues | ✓ no regression | Covered at the SQL layer by `cursorReadBetweenPagesStillContinues` and in the UI by the hook tests. |

### Required Artifacts (05-08)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `repository/InterestScoreQueries.java` | Rows with served SortKey; literal-tuple predicate | ✓ VERIFIED | Has `record SortKey`, `record PriorityRow`, `mapPriorityRow`, `CAST(:cursorScore AS float8)`, `CAST(:cursorDate AS timestamptz)`, `ZoneOffset.UTC` |
| `controller/PriorityPage.java` | Record, limit+1 trim, opaque codec | ✓ VERIFIED | 73 lines. `withoutPadding`; `UNREADABLE_CURSOR`; `of`/`encodeCursor`/`decodeCursor` |
| `service/PriorityService.java` | R4 existence check on the decoded id, then delegation | ✓ VERIFIED | Contains `priorityPageAfter(after` |
| `controller/ArticleController.java` | Opaque `String before`; returns `PriorityPage` | ✓ VERIFIED | `PriorityPage.decodeCursor(before)`; `PriorityPage.of(priorityService.page(after, safeLimit + 1), safeLimit)` |
| `PriorityApiIntegrationTest.java` | HTTP drop proof | ✓ VERIFIED | `cursorScoreDropMidWalkSkipsNoRow` |
| `InterestScoreQueriesTest.java` | SQL drop and rise proof | ✓ VERIFIED | `cursorScoreDropBetweenPagesSkipsNothing`, `cursorScoreRiseBetweenPagesRepeatsNothing` |
| `PriorityPageTest.java` | Round trip, pinned format, closed decoder | ✓ VERIFIED | 5 tests incl. `unreadableCursorIsNotFound` |
| `hooks/usePriorityArticles.ts` | String page param | ✓ VERIFIED | `undefined as string \| undefined` |

### Key Link Verification (05-08)

| From | To | Via | Status |
|------|----|-----|--------|
| ArticleController | PriorityPage | `decodeCursor(before)` → `PriorityPage.of(priorityService.page(after, safeLimit + 1), safeLimit)` | ✓ WIRED |
| PriorityPage | InterestScoreQueries | `encodeCursor(kept.getLast().key())`; the key is built by `mapPriorityRow` from the served columns | ✓ WIRED |
| InterestScoreQueries | PostgreSQL keyed CTE | row-value `<` against bound `cursorScore`/`cursorDate`/`cursorId` | ✓ WIRED |
| usePriorityArticles | api/articles | `getNextPageParam` → `last.nextCursor` (string) → `articlesApi.priority(50, pageParam)` → `before` | ✓ WIRED |
| PriorityList | refreshPriority | 404 from `fetchNextPage` (gone or unreadable cursor) → restart from page 1 | ✓ WIRED (unchanged) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PriorityList rows | `pages[].items` | `GET /api/articles/priority` → `PriorityService.page` → `priorityFirstPage`/`priorityPageAfter` SQL | Yes | ✓ FLOWING |
| Next-page boundary | `nextCursor` | `sort_score`/`sort_date`/`id` columns of the last served row → `encodeCursor` → client → `decodeCursor` → bound SQL params | Yes (served values, not a live re-read) | ✓ FLOWING |

### Behavioral Spot-Checks (run by me, 2026-09-26)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 05-08 backend + regression slices | `./gradlew test -x npmBuild -x npmInstall --tests PriorityApiIntegrationTest InterestScoreQueriesTest PriorityPageTest ArticleControllerTest PriorityServiceTest PaginatedResponseTest ArticleServiceTest BoardServiceTest ScoreBreakdownsTest` (no SPRING_AI_TYPESAFE_* / SPRING_PROFILES_ACTIVE in env) | 5+22+5+23+3+2+14+6+14 = 94 tests, 0 failures, 0 errors; all 18 plan-named tests present in fresh XML reports | ✓ PASS |
| Frontend type-check | `npx tsc -b` | exit 0 | ✓ PASS |
| Plan-touched frontend tests | `npx vitest run src/hooks/usePriorityArticles.test.ts src/components/PriorityList.test.tsx` | 2 files, 39/39 | ✓ PASS |
| Full suites (orchestrator evidence, not re-run by me) | `./gradlew test`; `npx vitest run --maxWorkers=2` | 456 tests / 0 failures / 2 skipped; 218/218 | ✓ PASS (reported) |
| TDD order | `git log 19443be..HEAD` | `test(05-08)` `0c38095` directly before `fix(05-08)` `7fdd313` | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED. No probe scripts are declared by the phase, and it is not a migration or tooling phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PRIO-01 | 05-01, 05-02, 05-04 | Priority view, `/priority`, unread by score, ties by date | ✓ SATISFIED | Roadmap SC1 |
| PRIO-02 | 05-01, 05-02 | Unscored after scored, below the separator | ✓ SATISFIED | -Infinity sort key; separator tests |
| PRIO-03 | 05-01, 05-02, 05-03, 05-05, 05-07 | Tier-colored badge; none for unscored | ✓ SATISFIED | SC2; UAT 3 |
| PRIO-04 | 05-03, 05-07 | Exact Why N? with chips | ✓ SATISFIED | SC2 (WR-01 deferred by the user) |
| PRIO-05 | 05-06, 05-08 | Stable order while triaging | ✓ SATISFIED | SC3. The 05-08 repeat case (a row whose score changed between pages) is deduped by id, and loaded pages stay frozen |
| PRIO-06 | 05-01, 05-02, 05-06, 05-08 | Cursor pagination across the scored/unscored boundary | ✓ SATISFIED | Gap-free now even when the cursor's score changes (G-05-7). Note: the requirement text names `PaginatedResponse`. Priority now uses the sibling record `PriorityPage` with the identical `{items, nextCursor}` JSON shape and a string cursor, as a documented R4 amendment chosen by the user in UAT 7. Consider updating the REQUIREMENTS.md wording |
| PRIO-07 | 05-04, 05-06, 05-07 | `g p`, `j`/`k`, `i`, Shift+A disabled | ✓ SATISFIED | Keyboard tests; untouched by 05-08 |
| PRIO-08 | 05-04 | Four status states | ✓ SATISFIED | Banner tests; untouched by 05-08 |

All 8 IDs are claimed by plan frontmatter (05-08 claims PRIO-05 and PRIO-06). REQUIREMENTS.md maps no other IDs to Phase 5. No orphaned requirements.

### Prohibitions (05-08, all `verification: test`)

| Prohibition | Enforcement | Status |
|-------------|-------------|--------|
| No live-blend boundary | `cursorScoreDropBetweenPagesSkipsNothing` and `cursorScoreDropMidWalkSkipsNoRow` (red before, green after); grep shows no `FROM keyed c WHERE` in code | ✓ enforced |
| PaginatedResponse, list and board cursors unchanged | Scope diff empty; `PaginatedResponseTest` passes; list and board tests pass | ✓ enforced |
| No Flyway migration | `db/migration` diff empty; V6 last | ✓ enforced |
| WR-01/WR-03 untouched | `ArticleService`, `ScoreBreakdowns`, `useInterest.ts` diffs empty | ✓ enforced |
| No staging of user files | `git diff --name-only 19443be HEAD` has no `.envrc`/`CLAUDE.md`/`.claude/`/`config.json`; `git status` still shows all four as modified and uncommitted | ✓ enforced |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all 05-08 src files) | — | TBD/FIXME/XXX/TODO/HACK | — | None found |
| `controller/PriorityPage.java` | 47-72 | WR-04: a crafted, well-formed cursor with epoch micros outside Postgres' timestamptz range (e.g. `Long.MIN_VALUE`) decodes fine, passes `existsById` for a real id, and fails in SQL, which returns a 500 rather than a 404 | ⚠️ Warning | **Does not violate truth 7.** That truth covers cursors the server *cannot decode*, and this one decodes. The plan's own threat register accepted exactly this case (T-05-08-04: "An out-of-range timestamp can make Postgres reject that one statement (a 500 for that request only) … accept"). The server never emits such a cursor, because served dates come from timestamptz columns, so no real client hits it. But the class Javadoc "Anything it cannot read is a 404" overstates the contract. Cheap hardening: bound the decoded instant (as in 05-REVIEW WR-04) and add the case to `unreadableCursorIsNotFound`. Evidence: 05-REVIEW reproduced `timestamp out of range` on Postgres; I did not re-run it. |
| `repository/InterestScoreQueries.java` | 104-110; `hooks/usePriorityArticles.ts` 13-17 | WR-05: the Javadoc says "a score change between pages cannot skip rows". An *unserved* row whose own score rises above the frozen boundary between pages is still not shown in that walk | ⚠️ Warning | **Does not violate truth 1.** That truth is scoped to the cursor article's score being *lowered*, and a lowering never moves an unserved row above the boundary. This is inherent to keyset paging, and 05-REVIEW's original WR-02 fix text anticipated it. It matters for Phase 6: a thumbs-up on a common topic is exactly this case. Planning input for Phase 6: correct the Javadoc, and make vote/learned-weight changes call `setRankingChanged(true)` so the user gets the refresh hint. |
| `service/PriorityService.java` | 27-34 | IN-08: the existence check is now vestigial, since the tuple continues exactly after a deleted article; it forces a page-1 restart and costs a query | ℹ️ Info | Required by truth 6 as written (R4 kept); a design choice |
| `service/PriorityService.java` | 32 | IN-09: the gone-article 404 detail echoes the decoded numeric id | ℹ️ Info | A re-rendered `long`, so no injection vector; inconsistent with the codec's no-echo rule |
| `repository/InterestScoreQueries.java`, `hooks/useInterest.ts` | — | WR-01, WR-03 | ⚠️ Warning (user-deferred) | Deferred by the user in UAT 7 (05-UAT "Deferred Follow-Ups"); not a gap |

### Advisory (New Scope, Unevidenced)

None. The new findings (WR-04, WR-05, IN-08, IN-09) are warnings or info, not blockers, and each is recorded above with its evidence.

### Human Verification Required

None outstanding. The previous report's 7 items were walked in `05-UAT.md`: 1-6 passed, and 7 was decided ("fix WR-02 now, defer WR-01 and WR-03") and closed by 05-08. 05-08 changed only the cursor wire format and the page boundary, which the tests above cover. It did not change the triage, reading-pane, theme, layout, precision or StrictMode behavior that UAT 1-6 walked.

### Gaps Summary

G-05-7 is closed. The Priority cursor now carries the served `(sort_score, sort_date, id)` tuple as an opaque base64url string, and the next page compares against those literal values. The fix was checked end to end:

- SQL: literal-tuple predicate, UTC `OffsetDateTime` binding.
- Service: R4 existence check.
- Controller: decoding and the fixed-text 404.
- SPA: string cursor passed back verbatim; 404 restart unchanged.

The skip is proven fixed test-first, over HTTP and at the SQL layer, with exact sequences. I re-ran 94 targeted backend tests, `tsc -b` and the plan-touched frontend tests; all are green. There are no regressions against 05-01..05-07: the chronological and board cursors, PaginatedResponse, migrations and the WR-01/WR-03 code are untouched. All five roadmap success criteria and all eight PRIO requirements hold.

Two new review warnings remain, neither of which breaks a must-have:
- **WR-04:** a crafted out-of-range cursor returns a 500. The plan's threat model explicitly accepted this; the Javadoc overstates the contract.
- **WR-05:** the Javadoc overclaims "cannot skip". An unserved row rising above the boundary is still missed. This is a Phase 6 planning input.

WR-01 and WR-03 stay deferred by the user.

---

_Verified: 2026-09-26T15:58:00Z_
_Verifier: Claude (gsd-verifier)_
