---
phase: 08-engagement-capture
verified: 2026-09-30T04:20:00Z
status: human_needed
score: 5/7 must-haves verified (2 insufficient_spec backstops routed to human; 0 failed)
covered_files:
  - ".gitignore"
  - ".planning/phases/08-engagement-capture/08-01-PLAN.md"
  - ".planning/phases/08-engagement-capture/08-01-SUMMARY.md"
  - ".planning/phases/08-engagement-capture/08-02-PLAN.md"
  - ".planning/phases/08-engagement-capture/08-02-SUMMARY.md"
  - ".planning/phases/08-engagement-capture/08-03-PLAN.md"
  - ".planning/phases/08-engagement-capture/08-03-SUMMARY.md"
  - ".planning/phases/08-engagement-capture/08-04-PLAN.md"
  - ".planning/phases/08-engagement-capture/08-04-SUMMARY.md"
  - ".planning/phases/08-engagement-capture/08-05-PLAN.md"
  - ".planning/phases/08-engagement-capture/08-05-SUMMARY.md"
  - "CLAUDE.md"
  - "src/main/frontend/src/App.css"
  - "src/main/frontend/src/api/articles.ts"
  - "src/main/frontend/src/api/client.ts"
  - "src/main/frontend/src/components/ReadingPane.tsx"
  - "src/main/frontend/src/components/ScoreRow.tsx"
  - "src/main/frontend/src/hooks/useArticles.ts"
  - "src/main/frontend/src/hooks/useBoards.ts"
  - "src/main/frontend/src/hooks/useEngagement.ts"
  - "src/main/frontend/src/hooks/useKeyboardShortcuts.ts"
  - "src/main/frontend/src/types/index.ts"
  - "src/main/java/org/bartram/myfeeder/controller/ArticleController.java"
  - "src/main/java/org/bartram/myfeeder/integration/RaindropService.java"
  - "src/main/java/org/bartram/myfeeder/model/Article.java"
  - "src/main/java/org/bartram/myfeeder/model/EngagementKind.java"
  - "src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java"
  - "src/main/java/org/bartram/myfeeder/service/ArticleService.java"
  - "src/main/java/org/bartram/myfeeder/service/BoardService.java"
  - "src/main/resources/db/migration/V7__engagement.sql"
covered_digest: "v2:sha256:d46e87b6241d27ab89c461bd876f6b182424db5d2ac23ba706710774d8eee153"
behavior_unverified: 0
overrides_applied: 0
coincidental_reliance_items:
  - truth: "An engagement insert failure never fails the user's star, board add or Raindrop save (recordQuietly never throws, D-07/D-08)"
    reason: undeclared-precondition
    harden: "recordQuietly catches only DataAccessException. It holds today because every realistic failure of the JdbcClient insert is translated to DataAccessException and no caller passes a null boxed Long (article.getId() of a loaded article; a null board articleId fails the board_article NOT NULL save first). Catch RuntimeException in recordQuietly (review WR-01) so the D-08 no-duplicate-bookmark guarantee no longer depends on those preconditions."
human_verification:
  - test: "Live Open Original with the backend stopped: in a real browser, open an article, stop the backend (or block /api), then press `o` and click the toolbar ↗ Open Original"
    expected: "Each action opens the original page in a new tab immediately (no popup-blocker prompt), no error toast appears, and nothing is logged as an unhandled rejection in the console. With the backend back up, one press of `o` shows 'Engaged: opened · Forget' under the title; Forget hides the line; another `o` brings it back"
    why_human: "jsdom tests prove window.open runs before the PUT and that rejections are swallowed, but real-browser user-activation/popup-blocker behavior and the rendered look of the Engaged line cannot be observed programmatically (deferred to end-of-phase UAT by the user)"
  - test: "SC-5 backstop: after at least one day of real prod use, run read-only `SELECT kind, count(*) FROM article_engagement GROUP BY kind` against pg.bartram.org"
    expected: "Rows exist for each kind actually exercised (OPEN_ORIGINAL, and STAR/BOARD/RAINDROP if used)"
    why_human: "Rows accumulating from real use is a backstop truth that needs elapsed time and real usage; at release the table held 0 rows (the smoke's row was forgotten)"
  - test: "Confirm the 08-05 judgment-tier release prohibitions (no publish before approve; no force-push/--no-verify/disableChecks/tag rewrite; no hand edits to prod data or schema; no secret printed)"
    expected: "Human agrees with the non-authoritative verifier verdict below (all four consistent with evidence)"
    why_human: "Judgment-tier prohibitions require explicit human resolution; the verifier's check is evidence-based but non-authoritative"
  - test: "Accept the CAPT-04 interruption backstop (08-03): a process stop between createBookmark returning and the RAINDROP insert leaves a bookmark with no RAINDROP row, and nothing retries"
    expected: "Human confirms the D-08 best-effort loss is acceptable"
    why_human: "Backstop truth describing an accepted limitation; code confirms no compensating path exists, but acceptance is a product decision"
---

# Phase 8: Engagement Capture Verification Report

**Phase Goal:** Opening an article's original link and saving an article (star, board, Raindrop) are recorded in production as sticky, forgettable engagement, while the ranking stays exactly as it was
**Verified:** 2026-09-30T04:20:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1: `o` or Open Original opens the page in a new tab every time, even when the request fails, records one OPEN_ORIGINAL however often; in-body links record nothing | ✓ VERIFIED | `useEngagement.ts:21-28`: `window.open` synchronously, then `Promise.resolve().then(recordOpen).then(invalidate exact).catch(()=>{})`. All three entry points route through it (`ReadingPane.tsx:117,186,217`; `useKeyboardShortcuts.ts:175`). Only `window.open` of `article.url` in the app is the helper; the in-body handler (`ReadingPane.tsx:139`) opens `link.href` without the helper. Backend `ArticleService.recordOpen` → `record` with `ON CONFLICT (article_id, kind) DO NOTHING`. Tests passed: `opensFirstThenSendsABodylessPut`, `aNetworkFailureIsSilent`, `aServerErrorIsSilent`, keyboard rejection test, `inBodyLinkOpensButIsNotReported`, `copyLinkIsNotReported`, `repeatedOpenKeepsOneRowAndItsFirstCreatedAt`, `openOnAnUnknownArticleIs404AndStoresNothing`, `openWithANonNumericIdIs400` |
| 2 | SC-2: star (unstarred→starred) records STAR; any board add incl. Read Later and `b` records one BOARD; successful Raindrop records RAINDROP, failed/blocked records nothing | ✓ VERIFIED | `ArticleService.updateState:84-93` (`newlyStarred` computed before mutation, `recordQuietly` after save). `BoardService.addArticle:70-78` is the only board_article writer; `BoardController` → it; Read Later (`useBoards.ts:41-48`) and BoardManager both call `boardsApi.addArticle`. `RaindropService:51-52` records after `createBookmark`; `createBookmarkFallback` always throws (`RaindropApiClientImpl:78-83`). Tests passed: `starringRecordsStarAndShowsOnTheArticle`, `starringAStarredArticleRecordsNothing`, `boardAddRecordsOneBoardRowAcrossTwoBoards`, `reAddingAnArticleAlreadyOnABoardEarnsBoard`, `aFailedSaveRecordsNothing`, `aFailedOrBlockedBookmarkRecordsNothing`, `aRaindropNotConfiguredFailureRecordsNothing`, `aRepeatedSaveCapturesEachTimeQuietly` |
| 3 | SC-3: sticky across unstar / board removal / board delete; article or feed delete removes it without breaking the delete; nothing else records | ✓ VERIFIED | V7 FK `ON DELETE CASCADE` on article only (no FK to board). Exhaustive writer enumeration: the only `record`/`recordQuietly` call sites are ArticleService (OPEN, STAR), BoardService (BOARD), RaindropService (RAINDROP); the only client report is `articlesApi.recordOpen` via the helper. Tests passed: `engagementIsStickyAcrossUnstarBoardRemovalAndBoardDelete`, `deletingTheFeedRemovesEngagementAndSucceeds` (204), `deletingTheArticleCascadesEngagementAndDismissal`, `readOnlyPatchRecordsNothing`, `bulkMarkReadRecordsNothing`, `removingAndDeletingTouchNoEngagement` |
| 4 | SC-4: reading pane shows a Forget control only when the article has engagement; it deletes and hides; a later open/save records again | ✓ VERIFIED | `ScoreRow.tsx`: returns null when unscored and no engagement; line + `aria-label="Forget engagement"` button only when `engagement.length > 0`; `forget.mutate(article.id)` → `useForgetEngagement` → DELETE, exact `['article', id]` invalidation. By-id `GET` carries `engagement` (`ArticleService.findByIdWithBreakdown:49`), ReadingPane fetches by id (`useArticle`). Backend `forgetEngagement` deletes rows only, no tombstone. Tests passed: `noEngagementShowsNoLine`, `unscoredButEngagedShowsOnlyTheEngagementLine`, `forgetDeletesAndRefreshesOnlyThisArticle`, `aFailedForgetShowsTheErrorToast`, `forgetLeavesStarBoardAndVoteAlone`, `openAfterForgetRecordsAgain`, `forgetWithoutEngagementIs204`, `forgetOnAnUnknownArticleIs404` |
| 5 | SC-5a: capture release runs in production with Priority order, badges and "Why N?" unchanged from v0.2.1 | ✓ VERIFIED | Read-only prod check now: `/api/version` = 0.3.0; `GET /api/articles/26221` has `engagement: []` plus score and breakdown; list items carry no `engagement` key. Tag `v0.3.0` on origin → db5acd7; `v0.2.1` still 5461d0a; phase branch 5b1f30d is an ancestor of main. Ranking isolation: `InterestScoreQueries.java` and `scripts/interest-calibration-replay.sql` do not mention either V7 table (grep); `rankingSqlDoesNotReadTheV7TablesYet` and `engagementLeavesTheRankingUnchanged` (badge + breakdown equal, topic weight unchanged, `jevApiClient.judge` never called) pass. 08-RELEASE.md records ranking UNCHANGED over 47 ids and 5/5 byte-identical Why payloads (release evidence, not re-run) |
| 6 | SC-5b: engagement rows accumulate from real use in prod | ? insufficient_spec (backstop) | Needs ≥1 day of real use; at release the table held 0 rows. Human item 2 |
| 7 | 08-03 backstop: process stop between createBookmark and the RAINDROP insert loses the row, nothing retries (accepted by D-08) | ? insufficient_spec (backstop) | Code confirms no compensating path (`RaindropService:51-52`, no retry/outbox). Acceptance is a human decision. Human item 4 |

**Score:** 5/7 truths verified (0 present-but-behavior-unverified; 2 backstops routed to human; 0 failed)

Plan-level must-haves folded into the rows above were each checked against code: V7 creates exactly the two tables with the specified PK/CHECK/FK and no `topic_id` (`V7__engagement.sql`, `v7OnlyCreatesTables`, `dismissalTableHasNoTopicColumn`, `deletingATopicKeepsItsDismissal`); kind order OPEN_ORIGINAL, STAR, BOARD, RAINDROP (`EngagementKind`, `kinds()` sorts by enum, `kindsFollowTheEnumOrder`); class-name-only WARN (`recordQuietlySwallowsAFailureAndLogsTheClassNameOnly`); bodyless PUT with no Content-Type while `setFeedback`/boards keep JSON (`client.ts:54-62`); exact by-id invalidation after Raindrop, board add, Read Later and no `['priority']` invalidation (`engagementRefresh.test.ts`, `noSaveInvalidatesPriority`); no Forget shortcut (no `forget` in `useKeyboardShortcuts.ts`); CLAUDE.md lists V7, the routes, the classes, and no longer says later phases add no migrations (CLAUDE.md:112,160).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V7__engagement.sql` | two tables, PK (article_id, kind), CHECKs, cascades | ✓ VERIFIED | 18 lines, only CREATE TABLE; applied in prod (Flyway v7 success per release record) |
| `model/EngagementKind.java` | enum of four kinds | ✓ VERIFIED | Used by store, services, Article |
| `repository/ArticleEngagementStore.java` | record / recordQuietly / kinds / deleteAll | ✓ VERIFIED | `ON CONFLICT (article_id, kind) DO NOTHING`; no transaction boundary; injected into ArticleService, BoardService, RaindropService |
| `controller/ArticleController.java` | PUT `/{id}/engagement/open`, DELETE `/{id}/engagement`, both 204 | ✓ VERIFIED | Lines 107-118, delegate to `articleService.recordOpen(id)` / `forgetEngagement(id)` |
| `service/BoardService.java` | BOARD capture | ✓ VERIFIED | `recordQuietly(articleId, EngagementKind.BOARD)` after save-or-exists |
| `integration/RaindropService.java` | RAINDROP after createBookmark | ✓ VERIFIED | Line 52, outside the breaker bean |
| `service/ArticleService.java` | STAR on unstarred→starred; by-id engagement | ✓ VERIFIED | Lines 49, 84-93 |
| `frontend/src/hooks/useEngagement.ts` | useOpenOriginal, useForgetEngagement | ✓ VERIFIED | Both exported and consumed (ReadingPane, useKeyboardShortcuts, ScoreRow) |
| `frontend/src/api/articles.ts` | recordOpen, forgetEngagement | ✓ VERIFIED | Lines 40-43 |
| `frontend/src/api/client.ts` | apiPut optional body | ✓ VERIFIED | Content-Type only when body present |
| `frontend/src/types/index.ts` | EngagementKind union, Article.engagement | ✓ VERIFIED | Lines 46-53 |
| `frontend/src/components/ScoreRow.tsx` | Engaged line + Forget | ✓ VERIFIED | Rendered from ReadingPane:198 |
| Test files (EngagementApiIntegrationTest, ArticleEngagementStoreTest, V7EngagementMigrationTest, useEngagement.test, ScoreRow.test, engagementRefresh.test) | proofs | ✓ VERIFIED | All present and passing (below) |
| `08-RELEASE.md` | Release 0.3.0 evidence | ✓ VERIFIED | Contains `## Release 0.3.0`; no secret values (checked against all four env values without printing them) |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| ArticleController | ArticleService | `articleService.recordOpen(id)` / `forgetEngagement(id)` | ✓ WIRED |
| ArticleService | ArticleEngagementStore | `setEngagement(engagementStore.kinds(id))` | ✓ WIRED |
| ArticleEngagementStore | V7 table | `ON CONFLICT (article_id, kind) DO NOTHING` | ✓ WIRED |
| ArticleService.updateState | store | `recordQuietly(id, EngagementKind.STAR)` | ✓ WIRED |
| BoardService.addArticle | store | `recordQuietly(articleId, EngagementKind.BOARD)` | ✓ WIRED |
| RaindropService.saveToRaindrop | store | `recordQuietly(article.getId(), EngagementKind.RAINDROP)` | ✓ WIRED |
| ReadingPane | useEngagement | `openOriginal(article)` (both buttons) | ✓ WIRED |
| useKeyboardShortcuts | useEngagement | `openOriginal(currentArticle)` in `case 'o'` | ✓ WIRED |
| useEngagement | api/articles | `articlesApi.recordOpen`, `articlesApi.forgetEngagement` | ✓ WIRED |
| ScoreRow | useEngagement | `forget.mutate(article.id)` | ✓ WIRED |
| useBoards | useArticle key | `queryKey: ['article', articleId], exact: true` (board add, Read Later) | ✓ WIRED |
| main | tag v0.3.0 on origin | `./gradlew release -Prelease.versionIncrementer=incrementMinor` | ✓ WIRED (ls-remote shows v0.3.0 → db5acd7) |
| image 0.3.0 | deploy/myfeeder | `./deploy.sh 0.3.0` | ✓ WIRED (prod `/api/version` = 0.3.0) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| ScoreRow Engaged line | `article.engagement` | `useArticle(id)` → `GET /api/articles/{id}` → `engagementStore.kinds(id)` → `SELECT kind FROM article_engagement` | Yes (prod returns `engagement: []` field; integration test `byIdCarriesEngagement`) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend capture, stickiness, cascade, ranking isolation, V7 shape | `./gradlew test -x npmBuild -x npmInstall --tests` (7 phase classes, no TypeSafe/dev env) | 100 tests, 0 failures, 0 skipped (EngagementApiIntegrationTest 21, V7EngagementMigrationTest 16, ArticleServiceTest 28, BoardServiceTest 10, RaindropServiceTest 8, ArticleEngagementStoreTest 5, InterestCalibrationReplaySqlTest 12) | ✓ PASS |
| Frontend type-check | `npx tsc -b` | exit 0 | ✓ PASS |
| Frontend open/forget/refresh/keyboard/reading pane | `npx vitest run` on 5 engagement-related files | 68/68 passed | ✓ PASS |
| Prod version | `curl /api/version` | `0.3.0` | ✓ PASS |
| Prod by-id engagement / list omission | `curl /api/articles/26221`, `curl /api/articles?limit=2` | `engagement: []` on by-id; no key on list items | ✓ PASS |

The full backend suite was not re-run by the verifier (orchestrator reports 599/0 post-merge); the phase-relevant classes were run directly.

### Probe Execution

Step 7c: no `scripts/*/tests/probe-*.sh` exist and no plan declares one. SKIPPED.

### Prohibitions

| Plan | Prohibition | Tier | Disposition |
|------|-------------|------|-------------|
| 08-01 | No engagement sent outside own Postgres; no Jev call | test | ✓ enforced: `verify(jevApiClient, never()).judge` in `engagementLeavesTheRankingUnchanged`; open/forget paths contain no HTTP client |
| 08-01 | Forget leaves no trace and doesn't unstar/unboard/clear vote | test | ✓ enforced: `forgetLeavesStarBoardAndVoteAlone`, `openAfterForgetRecordsAgain` (no tombstone) |
| 08-02 | Never delay/block the open; no toast on failure | test | ✓ enforced: `opensFirstThenSendsABodylessPut`, `aNetworkFailureIsSilent`, `aServerErrorIsSilent` |
| 08-02 | In-body links, Copy Link, selection, reader view not reported; no third party | test | ✓ enforced: `inBodyLinkOpensButIsNotReported`, `copyLinkIsNotReported`; writer enumeration |
| 08-03 | Engagement failure never fails/rolls back the save or surfaces for a created bookmark | test | ✓ enforced for DataAccessException (`recordQuietlySwallowsAFailureAndLogsTheClassNameOnly`, no @Transactional on the three services); see WR-01 advisory |
| 08-03 | Engagement doesn't change Priority/badge/Why | test | ✓ enforced: `rankingSqlDoesNotReadTheV7TablesYet`, `engagementLeavesTheRankingUnchanged` |
| 08-03 | No extra outbound request on save capture | test | ✓ enforced: store is JdbcClient-only; Jev never called |
| 08-04 | Forget sends only DELETE, touches nothing else | test | ✓ enforced: `forgetDeletesAndRefreshesOnlyThisArticle` + backend `forgetLeavesStarBoardAndVoteAlone` |
| 08-04 | No Priority/list refetch on engagement | test | ✓ enforced: `noSaveInvalidatesPriority`, `successInvalidatesOnlyTheExactByIdQuery` |
| 08-05 | No publish before approve | judgment | Flagged (non-authoritative verdict: consistent — RELEASE records approval before the merge commit) |
| 08-05 | No force-push / --no-verify / disableChecks / tag rewrite | judgment | Flagged (non-authoritative verdict: consistent — v0.2.1 unchanged at 5461d0a, main pushed as fast-forward per record) |
| 08-05 | No hand edits to prod data/schema | judgment | Flagged (non-authoritative verdict: consistent — only Flyway V7 and the smoke's own PUT/DELETE recorded) |
| 08-05 | No secret printed or written | judgment | Flagged (non-authoritative verdict: consistent — none of the four values appear in 08-RELEASE.md or 08-05-SUMMARY.md) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CAPT-01 | 08-01, 08-02, 08-05 | Open Original / `o` records OPEN_ORIGINAL once; tab always opens | ✓ SATISFIED | Truth 1 |
| CAPT-02 | 08-03, 08-05 | Star records STAR | ✓ SATISFIED | Truth 2 |
| CAPT-03 | 08-03, 08-05 | Any board add records one BOARD | ✓ SATISFIED | Truth 2 |
| CAPT-04 | 08-03, 08-05 | Successful Raindrop records RAINDROP; failed/blocked nothing | ✓ SATISFIED | Truth 2 (interruption backstop: human item 4) |
| CAPT-05 | 08-03, 08-05 | Sticky; article/feed delete removes | ✓ SATISFIED | Truth 3 |
| CAPT-06 | 08-01, 08-04, 08-05 | Forget control only when engaged | ✓ SATISFIED | Truth 4 |
| CAPT-07 | 08-02, 08-03, 08-05 | Nothing else records | ✓ SATISFIED | Truths 1, 3 |

All seven IDs mapped to Phase 8 in REQUIREMENTS.md are claimed by at least one plan. No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all 30 covered files) | — | TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER | — | None found |
| `repository/ArticleEngagementStore.java` | 39-45 | `recordQuietly` catches only `DataAccessException` (review WR-01) | ⚠️ Warning | Documented "never throws" is narrower than the catch; a non-DataAccess RuntimeException after `createBookmark` would 5xx a created bookmark and invite a duplicate. No realistic trigger found at current call sites (see coincidental_reliance_items). Not a blocker |
| `service/ArticleService.java` | 59-64 | existsById then insert TOCTOU (review IN-01) | ℹ️ Info | Concurrent delete turns 404 into 500; fire-and-forget client ignores it |
| `components/ScoreRow.tsx` | 67, 75 | unknown kind renders "undefined"; `isPending` shared across article switches (IN-02, IN-03) | ℹ️ Info | Cosmetic, forward-compat |
| `hooks/useEngagement.ts` | 21-28 | no URL scheme check before `window.open` (IN-06, pre-existing) | ℹ️ Info | Pre-existing behavior, now centralized |

### Human Verification Required

### 1. Live Open Original with the backend stopped

**Test:** In a real browser, stop the backend (or block `/api`), then press `o` and click ↗ Open Original. Restart, press `o` once, check the Engaged line, click Forget, press `o` again.
**Expected:** A tab opens immediately every time, no toast, no unhandled rejection. Afterward "Engaged: opened · Forget" appears; Forget hides it; the next `o` brings it back.
**Why human:** Real-browser popup/user-activation behavior and the visual line cannot be observed in jsdom (deferred by the user to end-of-phase UAT).

### 2. SC-5 rows accumulate (backstop)

**Test:** After ≥1 day of real use, read-only `SELECT kind, count(*) FROM article_engagement GROUP BY kind` on prod.
**Expected:** Rows for each kind exercised.
**Why human:** Needs elapsed time and real usage.

### 3. Confirm 08-05 judgment-tier release prohibitions

**Test:** Review the Prohibitions table verdicts for 08-05.
**Expected:** Agree they held.
**Why human:** Judgment-tier; verifier verdict is non-authoritative.

### 4. Accept the CAPT-04 interruption backstop

**Test:** Confirm the D-08 best-effort loss (bookmark created, process dies before the RAINDROP insert) is acceptable.
**Expected:** Accepted.
**Why human:** Product decision on an accepted limitation.

### Gaps Summary

No gaps. Every roadmap success criterion and every plan must-have that can be checked in code is implemented, wired, and covered by passing tests that the verifier ran (100 backend, 68 frontend, tsc clean). Prod serves 0.3.0 with the by-id `engagement` field, and the release tags are correct. The status is `human_needed` because two backstop truths (rows accumulating in prod, the accepted Raindrop interruption loss), the live `o`-key check and the 08-05 judgment-tier prohibitions need a human. Review WR-01 is a real but non-blocking hardening item: widen `recordQuietly`'s catch to `RuntimeException`.

---

_Verified: 2026-09-30T04:20:00Z_
_Verifier: Claude (gsd-verifier)_
