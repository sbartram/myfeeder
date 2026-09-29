---
phase: 05-blend-priority-view
plan: 08
subsystem: api
tags: [spring-data-jdbc, postgres, keyset-pagination, cursor, react, tanstack-query, interest-ranking]
status: complete
gap_closure: true
gap_ids: [G-05-7]

requires:
  - phase: 05-01
    provides: "InterestScoreQueries keyed CTE with the (sort_score, sort_date, id) keyset and GET /api/articles/priority"
  - phase: 05-02
    provides: "usePriorityArticles infinite query, dedupeById, PriorityList 404-restart path"
provides:
  - "InterestScoreQueries.SortKey(double score, Instant date, long id) and PriorityRow(Article article, SortKey key)"
  - "priorityFirstPage(limit) / priorityPageAfter(SortKey, limit) return List<PriorityRow>; the after page compares against the literal cursor tuple"
  - "PriorityService.page(SortKey, int) with the unchanged R4 gone-article 404"
  - "controller/PriorityPage {items, nextCursor} record with of(), encodeCursor(), decodeCursor() and the fixed UNREADABLE_CURSOR 404"
  - "GET /api/articles/priority?before=<opaque> returns {items, nextCursor: string | null}"
  - "Frontend PriorityPage type; articlesApi.priority(limit, before?: string)"
affects: [phase-06-feedback, 05-verification]

actuals:
  tokens: 13081
  tasks: 3
  commits: 4
plan_head_before: 19443be2f6cb8d635c6f24b74ecc881c31cc3afd

tech-stack:
  added: []
  patterns:
    - "Keyset cursor carries the served sort tuple, opaque base64url of <Double.toString(score)>|<epochMicros>|<id>; the next page compares against those literal values, never the cursor row's live score"
    - "Instant cursor dates are bound as OffsetDateTime at UTC with explicit CAST(... AS timestamptz), so JVM and session zones cannot shift the boundary"
    - "Unreadable cursor = 404 with fixed text (the client's restart-from-page-1 signal), never echoing the input"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
    - src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
    - src/main/java/org/bartram/myfeeder/service/PriorityService.java
    - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
    - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
    - src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java
    - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
    - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/api/articles.ts
    - src/main/frontend/src/hooks/usePriorityArticles.ts
    - src/main/frontend/src/hooks/usePriorityArticles.test.ts
    - src/main/frontend/src/components/PriorityList.test.tsx

key-decisions:
  - "05-08: R4 amended for Priority only. The cursor is now the served (sort_score, sort_date, id) tuple as an opaque base64url string. PaginatedResponse and the article/board Long cursors are unchanged"
  - "05-08: A gone cursor article (404 'Article not found: <id>') and an unreadable cursor, including a legacy numeric id from an old tab (404 'Priority cursor not recognized'), both restart the list from page 1 through PriorityList's existing 404 path"
  - "05-08: A row whose own score changed between pages can be listed twice and the client dedupes it by id; no row is skipped (G-05-7 / WR-02 closed)"

patterns-established:
  - "Served-tuple cursor: the page SQL returns each row's key, the controller encodes the last kept key, and the next page binds the decoded parts as typed parameters"

requirements-completed: [PRIO-06, PRIO-05]

coverage:
  - id: D1
    description: "Lowering the cursor article's score between page fetches skips no row, over HTTP (red before, green after)"
    requirement: PRIO-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#cursorScoreDropMidWalkSkipsNoRow"
        status: pass
    human_judgment: false
  - id: D2
    description: "SQL layer: exact sequence after a cursor score drop (a4, a3, a1, a2, a8, a6, a1, a7, a5, u1, u3, u5, u2, u4) and no repeat after a rise (page 2 = a2, a8, a6)"
    requirement: PRIO-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#cursorScoreDropBetweenPagesSkipsNothing"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#cursorScoreRiseBetweenPagesRepeatsNothing"
        status: pass
    human_judgment: false
  - id: D3
    description: "Paged walks (2, 3, 5) still match the unpaged order across the scored/unscored boundary, independent of the JVM time zone; served keys carry the exact float8 score, -Infinity for unscored rows and fetched_at for undated rows; R4 read-cursor continuation kept"
    requirement: PRIO-06
    verification:
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#pagedWalksMatchTheUnpagedOrder"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#cursorWalkIsZoneIndependent"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#servedKeysCarryTheSortTuple"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java#cursorReadBetweenPagesStillContinues"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#rankedWalkCrossesIntoUnscoredWithNoDuplicates"
        status: pass
    human_judgment: false
  - id: D4
    description: "Opaque cursor codec: lossless round trip, pinned wire format, URL-safe, and every malformed cursor (legacy numeric id included) is a fixed-text 404 that never reaches the service; a gone cursor article is still 404"
    requirement: PRIO-06
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java#cursorRoundTripsExactly"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java#cursorWireFormatIsPinned"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java#unreadableCursorIsNotFound"
        status: pass
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java#priorityUnreadableCursorIs404WithoutCallingTheService"
        status: pass
      - kind: integration
        ref: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java#missingCursorIs404"
        status: pass
    human_judgment: false
  - id: D5
    description: "SPA passes the opaque cursor back verbatim as before; frozen-page policy, dedupe by id and the 404 restart unchanged"
    requirement: PRIO-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/PriorityList.test.tsx (real pinned cursor in every pageAfter)"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts"
        status: pass
      - kind: other
        ref: "cd src/main/frontend && npx tsc -b && npm test (218 tests)"
        status: pass
    human_judgment: false

duration: 24min
completed: 2026-09-26
---

# Phase 5 Plan 08: Served-Tuple Priority Cursor Summary

**The Priority cursor is now an opaque base64url string holding each page's last served (sort_score, sort_date, id). The next page compares against those literal values, so a score change between "Load more" requests no longer skips rows (G-05-7 / WR-02 closed).**

## Performance

- **Duration:** 24 min
- **Started:** 2026-09-26T15:02:58Z
- **Completed:** 2026-09-26T15:27:43Z
- **Tasks:** 3
- **Files modified:** 14 (2 created, 12 modified)

## Accomplishments

- `InterestScoreQueries` returns `PriorityRow(article, SortKey)` for every Priority row. `priorityPageAfter(SortKey, limit)` uses `(k.sort_score, k.sort_date, k.id) < (CAST(:cursorScore AS float8), CAST(:cursorDate AS timestamptz), CAST(:cursorId AS bigint))` over `UNREAD_SCOPE`. The correlated live re-read of the cursor row and `UNREAD_OR_CURSOR_SCOPE` are gone.
- New `PriorityPage` record (`{items, nextCursor}`) with limit+1 trimming and a lossless codec: `Double.toString(score)|epochMicros|id`, base64url without padding. Pinned example: `LUluZmluaXR5fDE3NTg4MDAwMDAxMjM0NTZ8NDI`.
- `GET /api/articles/priority` takes an opaque `String before`. An unreadable cursor, including a legacy numeric id from an old tab, is a 404 with the fixed text "Priority cursor not recognized", and the service is never called. A cursor whose article is gone is still the R4 404 "Article not found: <id>".
- The SPA types the cursor as `string | null`, sends it back verbatim, and keeps the frozen-page policy, dedupe by id and the 404 restart unchanged.
- The full backend suite is green (456 tests, 0 failures) and the frontend `tsc -b` + `npm test` pass (218 tests).

## Task Commits

1. **Task 1 (tracer): opaque served-tuple cursor end to end, ranking unchanged** - `7b3f64a` (refactor)
2. **Task 2: next page compares against the served tuple, test-first**
   - RED - `0c38095` (test)
   - GREEN - `7fdd313` (fix)
3. **Task 3: SPA types and tests use the opaque string cursor** - `bfc636b` (fix)

**Plan metadata:** recorded in the docs(05-08) commit that includes this SUMMARY

## TDD Gate Compliance

- RED `0c38095` `test(05-08)`: three tests failed on their target assertions against Task 1's code. `cursorScoreDropMidWalkSkipsNoRow` failed with "mid must not be skipped when the cursor article's score drops". `cursorScoreDropBetweenPagesSkipsNothing` failed because a2, a8 and a6 were skipped. `cursorScoreRiseBetweenPagesRepeatsNothing` failed because a3 came back. `gsd_run check tdd-red-evidence` returned `RED_EVIDENCE_OK / target_test_failed` for each of the three. The TAP input was rendered from the Gradle JUnit XML results.
- GREEN `7fdd313`: all three pass after the literal-tuple predicate. It directly follows the RED commit. The plan names this commit `fix(05-08)` instead of `feat(05-08)`, and it is the GREEN gate.
- REFACTOR: none needed.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/controller/PriorityPage.java`: the Priority response record and the opaque cursor codec
- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java`: `SortKey`/`PriorityRow` records, `mapPriorityRow`, and the literal-tuple after-cursor predicate with UTC `OffsetDateTime` binding
- `src/main/java/org/bartram/myfeeder/service/PriorityService.java`: `page(SortKey, int)`, which checks that the decoded id exists (R4)
- `src/main/java/org/bartram/myfeeder/controller/ArticleController.java`: the priority handler decodes `before` and returns `PriorityPage`
- `src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java`: round trip, pinned wire format, URL safety, closed decoder, `of()` trimming
- `src/test/java/.../InterestScoreQueriesTest.java`: migrated to `PriorityRow`; drop/rise exact sequences, served keys, zone-independent walks; `walkFrom`
- `src/test/java/.../PriorityApiIntegrationTest.java`: opaque-cursor walk, HTTP drop test, gone-article 404 through an encoded cursor
- `src/test/java/.../ArticleControllerTest.java`, `src/test/java/.../PriorityServiceTest.java`: migrated to `SortKey`/`PriorityRow`; unreadable-cursor 404 without a service call
- `src/main/frontend/src/types/index.ts`, `api/articles.ts`, `hooks/usePriorityArticles.ts`: the `PriorityPage` type and a string cursor
- `src/main/frontend/src/hooks/usePriorityArticles.test.ts`, `components/PriorityList.test.tsx`: string cursors; the real pinned cursor in every `pageAfter`

## Decisions Made

- **R4 amendment (Priority only):** the cursor is now the served sort tuple as an opaque string. `PaginatedResponse`, the chronological list and the board endpoints keep their Long id cursor. The gone-cursor 404 and the new unreadable-cursor 404 both restart the list from page 1 through `PriorityList`'s existing 404 effect, so a tab loaded before this deploy recovers on its first "Load more".
- A row whose own score changed between pages may appear twice, at its new rank, and the client dedupes it by id. Nothing is skipped.

## Deviations from Plan

None. The plan was executed as written.

Two minor execution notes:
- The worktree allow-list in the executor commit protocol (`agent-*` branches) was not applied. The orchestrator's run context set sequential execution on `sbartram/main` in this orca workspace checkout, whose `.git` is a file. The supplied root pin and the protected-branch check passed before every commit.
- Task 3's first two commit attempts were blocked by the TruffleHog pre-commit hook's self-updater (`fork/exec /opt/homebrew/bin/trufflehog: no such file or directory`), and both scans reported 0 secrets. Running the hook's command by hand exited 0. The third attempt passed the hook normally, and `--no-verify` was never used.

## Issues Encountered

None beyond the transient TruffleHog updater failure noted above.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- G-05-7 is closed, and Phase 5's gap-closure plans are complete, ready for phase verification.
- Phase 6 votes can change scores mid-walk without skipping rows. `patchPriorityArticle` and dedupe by id already handle the repeat case.
- WR-01 and WR-03 remain deferred by the user, and this plan did not touch them.

---
*Phase: 05-blend-priority-view*
*Completed: 2026-09-26*

## Self-Check: PASSED

- FOUND: PriorityPage.java, PriorityPageTest.java, InterestScoreQueries.java, usePriorityArticles.ts
- FOUND commits: 7b3f64a, 0c38095, 7fdd313, bfc636b
- Plan-level verification: full backend suite 456/0 failures, frontend tsc -b + 218 tests green, scope and protected-file checks pass
