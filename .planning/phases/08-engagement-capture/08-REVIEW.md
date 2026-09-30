---
phase: 08-engagement-capture
reviewed: 2026-09-30T04:14:36Z
depth: standard
files_reviewed: 31
files_reviewed_list:
  - .gitignore
  - CLAUDE.md
  - src/main/frontend/src/App.css
  - src/main/frontend/src/api/articles.ts
  - src/main/frontend/src/api/client.ts
  - src/main/frontend/src/components/ReadingPane.test.tsx
  - src/main/frontend/src/components/ReadingPane.tsx
  - src/main/frontend/src/components/ScoreRow.test.tsx
  - src/main/frontend/src/components/ScoreRow.tsx
  - src/main/frontend/src/hooks/engagementRefresh.test.ts
  - src/main/frontend/src/hooks/useArticles.ts
  - src/main/frontend/src/hooks/useBoards.ts
  - src/main/frontend/src/hooks/useEngagement.test.ts
  - src/main/frontend/src/hooks/useEngagement.ts
  - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
  - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
  - src/main/frontend/src/types/index.ts
  - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - src/main/java/org/bartram/myfeeder/integration/RaindropService.java
  - src/main/java/org/bartram/myfeeder/model/Article.java
  - src/main/java/org/bartram/myfeeder/model/EngagementKind.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java
  - src/main/java/org/bartram/myfeeder/service/ArticleService.java
  - src/main/java/org/bartram/myfeeder/service/BoardService.java
  - src/main/resources/db/migration/V7__engagement.sql
  - src/test/java/org/bartram/myfeeder/controller/EngagementApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java
  - src/test/java/org/bartram/myfeeder/repository/ArticleEngagementStoreTest.java
  - src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java
findings:
  critical: 0
  warning: 1
  info: 6
  total: 7
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-09-30T04:14:36Z
**Depth:** standard
**Files Reviewed:** 31
**Status:** issues_found

## Narrative Findings (AI reviewer)

## Summary

I reviewed the diff `52c830c..HEAD` for the Phase 8 engagement capture: the V7 migration, `ArticleEngagementStore`, capture in the star, board and Raindrop paths, the open and forget endpoints, the `useOpenOriginal`/`useForgetEngagement` hooks, the `ScoreRow` Forget line, the by-id cache refreshes and the tests.

The implementation follows the decisions in 08-CONTEXT.md:
- **Transactions:** none of the three capture services (`ArticleService.updateState`, `BoardService.addArticle`, `RaindropService.saveToRaindrop`) or their callers (`ArticleController`, `BoardController`) runs inside a transaction. `CrudRepository.save` commits in its own transaction before `recordQuietly` runs, so D-07 holds.
- **Board capture:** the D-09 amendment is implemented as written. A failing `board_article` insert (FK or NOT NULL) throws before `recordQuietly` is reached.
- **Star capture:** STAR is recorded only when `starred` changes from false to true.
- **Open capture:** `window.open` runs synchronously before the PUT, and every error is swallowed.
- **Cache refresh:** each capture path invalidates only the exact `['article', id]` key.
- **Ranking:** `InterestScoreQueries` does not read the new tables.

I found no blocker. One warning: the best-effort store is narrower than its documented "never throws" guarantee, and the Raindrop duplicate-bookmark protection (D-08) depends on that guarantee. The info items are edge cases in how the frontend handles state, a TOCTOU that turns a 404 into a 500, drift risk in one test, and a pre-existing URL-scheme gap that now sits in the new shared helper.

## Warnings

### WR-01: `recordQuietly` swallows only `DataAccessException`, but the D-08 no-duplicate-bookmark guarantee relies on it never throwing

**File:** `src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java:39-45` (relied on at `src/main/java/org/bartram/myfeeder/integration/RaindropService.java:51-52`)

**Issue:** The Javadoc on both `recordQuietly` and `RaindropService.saveToRaindrop` promises that "the capture never throws", so a created bookmark is never reported as an error that could invite a retry and a duplicate bookmark (D-08). The implementation catches only `DataAccessException`. Any other `RuntimeException` on this path escapes, and in `saveToRaindrop` that happens after `createBookmark` has already created the remote bookmark. Examples:
- a `NullPointerException` from auto-unboxing, since the signature is `recordQuietly(long articleId, …)` and callers pass a boxed `Long` (`article.getId()`, `articleId`)
- a non-translated driver or pool exception
- a future change inside `record`

The request then returns a 5xx. The client's global MutationCache shows an error toast, and the user retries, which creates the duplicate bookmark that D-08 was meant to prevent. The tests do not cover this because `RaindropServiceTest` mocks the store, and `ArticleEngagementStoreTest` exercises only a `DataIntegrityViolationException`. For the star and board paths the same gap would fail a save that has already committed (D-07).

**Fix:** Make the catch match the contract. Keep the log line free of the message:
```java
public void recordQuietly(long articleId, EngagementKind kind) {
    try {
        record(articleId, kind);
    } catch (RuntimeException e) {
        log.warn("Engagement {} not recorded for article {}: {}", kind, articleId, e.getClass().getSimpleName());
    }
}
```
Optionally, change the parameter to `Long` and treat a null as a logged no-op, so unboxing can never happen at the call site.

## Info

### IN-01: A delete between the existence check and the insert turns the documented 404 into an unmapped 500

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleService.java:59-64`

**Issue:** `recordOpen` calls `existsById` and then `record` as two separate autocommit statements. If the article is deleted between them (a RetentionService run, or a feed delete in another tab), the insert fails the FK with `DataIntegrityViolationException`. `GlobalExceptionHandler` has no mapping for that exception, so the response is a generic 500 instead of the 404 promised by D-12. The fire-and-forget client ignores both, so the only effect is a misleading status and a server-side error log.

**Fix:** Catch `DataIntegrityViolationException` around `record` in `recordOpen` and rethrow it as `NotFoundException`. Alternatively, use a single statement: `INSERT … SELECT :id, :kind WHERE EXISTS (SELECT 1 FROM article WHERE id = :id) ON CONFLICT DO NOTHING`, then check `existsById` only when 0 rows were affected.

### IN-02: A single `useForgetEngagement` instance carries `isPending` across article switches

**File:** `src/main/frontend/src/components/ScoreRow.tsx:28,75-76`

**Issue:** `ScoreRow` stays mounted while the selected article changes, and only its `article` prop changes. The mutation state therefore belongs to the component, not to the article. If you click Forget and press `j` before the DELETE resolves, the next article's Forget button renders disabled until the previous request settles.

**Fix:** Disable only for the article being forgotten, `disabled={forget.isPending && forget.variables === article.id}`. Alternatively, key `ScoreRow` by `article.id` in `ReadingPane.tsx:198`.

### IN-03: An unknown engagement kind renders as the literal text "undefined"

**File:** `src/main/frontend/src/components/ScoreRow.tsx:67`

**Issue:** `ENGAGEMENT_LABEL[kind]` is typed as total over `EngagementKind`, but the value comes from the server at runtime. When a later phase adds a kind, a tab still running the v0.3.0 bundle would render `Engaged: opened, undefined · Forget`.

**Fix:** Use `ENGAGEMENT_LABEL[kind] ?? kind.toLowerCase()`, or filter out unknown kinds before joining.

### IN-04: A late fire-and-forget open can bring OPEN_ORIGINAL back right after Forget

**File:** `src/main/frontend/src/hooks/useEngagement.ts:26-28`

**Issue:** The open PUT and the Forget DELETE are independent requests with no ordering between them. If you open the original (`o`) and then click Forget before the PUT lands (for example on a slow connection), the server can apply the DELETE first and the PUT second. The OPEN_ORIGINAL row then returns. The by-id refetch that follows the PUT shows it again, so the effect is visible, not silent. This follows from D-11 (no client-side dedupe) and has low impact, but D-03 does not document it.

**Fix:** Accept and document the race in the hook's comment. Alternatively, track in-flight open promises per article id and have the forget mutation `await` them before sending the DELETE.

### IN-05: `V7EngagementMigrationTest` copies the replay keyword regex instead of sharing it

**File:** `src/test/java/org/bartram/myfeeder/repository/V7EngagementMigrationTest.java:37-38`

**Issue:** `WRITE_KEYWORD` is copied verbatim from `InterestCalibrationReplaySqlTest.java:42`. If that pattern is tightened (for example by adding `merge` or `upsert`), this guard does not follow, and a kind or column name that the replay would reject can still pass. That defeats the "kind names must pass the replay keyword check" rule it exists to enforce.

**Fix:** Make the original constant package-visible, or move it to a small shared test helper in `org.bartram.myfeeder.repository`, and reference it from both tests.

### IN-06: `useOpenOriginal` passes feed-supplied URLs to `window.open` without checking the scheme (pre-existing, now centralized)

**File:** `src/main/frontend/src/hooks/useEngagement.ts:21-23`

**Issue:** `article.url` comes directly from the feed (`FeedPollingService.java:96` stores `parsed.url()` with no scheme validation). In-body links go through DOMPurify, which strips `javascript:` hrefs, but the Open Original URL does not. A hostile feed could therefore supply `javascript:` or `data:` URLs. This behaviour predates Phase 8, since the old inline `window.open` calls did the same. Phase 8 routes all three entry points through one helper, so the helper is now the single place to guard it.

**Fix:** In the helper, skip both the open and the PUT unless `/^https?:\/\//i.test(article.url.trim())`. Server-side validation of the article URL scheme at ingest would be the stronger fix.

---

_Reviewed: 2026-09-30T04:14:36Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
