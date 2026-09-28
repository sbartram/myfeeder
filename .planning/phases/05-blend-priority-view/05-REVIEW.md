---
phase: 05-blend-priority-view
reviewed: 2026-09-26T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - src/main/frontend/src/api/articles.ts
  - src/main/frontend/src/components/PriorityList.test.tsx
  - src/main/frontend/src/hooks/usePriorityArticles.test.ts
  - src/main/frontend/src/hooks/usePriorityArticles.ts
  - src/main/frontend/src/types/index.ts
  - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/PriorityService.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
  - src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase 5: Code Review Report (incremental, plan 05-08)

**Reviewed:** 2026-09-26T00:00:00Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

This is an incremental review of gap-closure plan 05-08 (`59e4d3c..HEAD`). The plan replaces the Priority id-only cursor with an opaque base64url cursor that encodes the served `(sort_score, sort_date, id)` tuple.

**WR-02 (previous review) is resolved for the mechanism it reported.** `priorityPageAfter` no longer reads the cursor article's live score. It compares against the literal tuple that was served. So when the cursor article's score drops, the rows it drops past are no longer skipped. `cursorScoreDropBetweenPagesSkipsNothing` and `cursorScoreDropMidWalkSkipsNoRow` cover this. One skip class remains, as WR-02's own fix text predicted: an unserved row whose own score rises above the frozen boundary is skipped. The new Javadoc says this cannot happen (WR-05).

**What I checked and found correct:**

- **Codec round-trip:**
  - `Double.toString`/`parseDouble` is exact for float8, and `-Infinity` round-trips.
  - NaN is rejected.
  - The micros encoding is correct for pre-epoch instants, because `Instant` nanos are always non-negative.
  - Legacy numeric ids (`"7"`, `"42"`, `"12345"`) and malformed base64 map to the fixed-text 404.
- **Timezone:** `published_at` and `fetched_at` are `TIMESTAMPTZ` (V1). The read uses `getTimestamp().toInstant()` and the bind uses an `OffsetDateTime` at UTC with an explicit `timestamptz` cast. Neither depends on the zone.
- **SQL:**
  - The row-value `<` matches `ORDER BY ... DESC` on all three keys.
  - `sort_date` is never NULL, because `fetched_at` is NOT NULL.
  - Every cursor value is a bound parameter.
  - I confirmed in Postgres that `-Infinity` works in a row comparison.
- **Frontend:**
  - The string cursor passes through `URLSearchParams` unchanged, since base64url characters are URL-safe.
  - `getNextPageParam` and `dedupeById` are typed correctly.
  - The 404 restart path in `PriorityList` is unchanged and still fires for an unreadable cursor.

**New defects:**

- A crafted cursor can produce a 500 instead of the documented 404 (WR-04).
- The Javadoc overstates the no-skip guarantee (WR-05).

## Narrative Findings (AI reviewer)

## Warnings

### WR-04: A cursor date outside Postgres' timestamp range passes decoding and returns a 500, not the documented 404

**File:** `src/main/java/org/bartram/myfeeder/controller/PriorityPage.java:57-68`, `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:121`
**Issue:** `decodeCursor` accepts any `long` micros. `Instant.EPOCH.plus(micros, MICROS)` throws nothing across the whole `long` range, because `Instant` reaches ±1e9 years. So the value passes the decode step and reaches SQL. Postgres `timestamptz` only covers 4713 BC to 294276 AD. `Long.MIN_VALUE` micros is about 290308 BC.

I checked this against `postgres:latest`:

```
select '290308-12-22 19:59:05.224192+00 BC'::timestamptz;
ERROR:  timestamp out of range
```

A request whose cursor is `base64url("1.0|-9223372036854775808|<any existing article id>")` passes `existsById`. It then fails inside `priorityPageAfter` with a `DataAccessException`. `GlobalExceptionHandler` has no mapping for that, so the client gets a 500 and the server logs an ERROR stack trace.

This breaks the class contract: "Anything it cannot read is a 404 with fixed text". It also defeats the R4 restart signal for such input, since `PriorityList` only restarts on 404. The app has no auth, so any client can trigger this. `PriorityPageTest.unreadableCursorIsNotFound` has no out-of-range case.
**Fix:** Bound the decoded instant to a range the server can actually have served, then add the case to `unreadableCursorIsNotFound`:
```java
private static final Instant MIN_DATE = Instant.parse("0001-01-01T00:00:00Z");
private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");
...
Instant date = Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
    throw new NotFoundException(UNREADABLE_CURSOR);
}
return new SortKey(score, date, id);
```
```java
// PriorityPageTest.unreadableCursorIsNotFound inputs
b64("1|" + Long.MIN_VALUE + "|1"), b64("1|" + Long.MAX_VALUE + "|1")
```

### WR-05: Javadoc claims "a score change between pages cannot skip rows"; unserved rows whose score rises past the frozen boundary are still skipped

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:104-110`; `src/main/frontend/src/hooks/usePriorityArticles.ts:13-17`
**Issue:** The boundary is now frozen at the served tuple. Suppose a row that has not been served yet has its own score raised above the cursor score between pages, for example:
- its topic's weight is raised
- it is re-scored higher
- from Phase 6, a thumbs-up raises a topic's `learned` delta

That row then satisfies `tuple >= cursor` and never appears in the walk. This is inherent to keyset pagination, and the previous WR-02 fix text accepted it. Two things make it worth fixing the wording now:

1. The Javadoc states the opposite ("cannot skip rows"), and the `dedupeById` comment only describes the drop and duplicate direction. Phase 6 will replace the `learned` CTE and will rely on this documented invariant.
2. The risk has moved rather than disappeared. With the old live cursor, a uniform upward shift that also applied to the cursor article moved the boundary with it. With the frozen cursor, every unserved row that crosses the boundary is skipped silently. A Phase 6 positive vote on a common topic is exactly this case.

No test covers the rise-of-an-unserved-row direction. `cursorScoreRiseBetweenPagesRepeatsNothing` only raises the cursor row itself.
**Fix:**
- Correct the Javadoc to say what is actually guaranteed: "the cursor article's own score change no longer skips rows; rows whose score rises across the served boundary between pages are not shown in this walk, and the 'Ranking changed' hint is the recovery path."
- Mirror that in the `dedupeById` comment.
- Add a repository test that raises an unserved row above the boundary and asserts the documented behavior.
- In Phase 6, make sure the vote and learned-weight path sets `usePriorityStore.setRankingChanged(true)`, as the rubric mutations already do.

## Info

### IN-08: The cursor-existence 404 is now vestigial and forces an unnecessary restart

**File:** `src/main/java/org/bartram/myfeeder/service/PriorityService.java:27-34`
**Issue:** With the tuple cursor, `priorityPageAfter` never reads the cursor article, so continuing after a deleted article is exact. The `existsById` check still returns 404 when the cursor article is gone, which only happens when its feed is unsubscribed (cascade). That makes `PriorityList` reset the whole frozen list to page 1 and throw away the user's walk for no correctness reason. It also costs an extra query on every page.
**Fix:** Drop the existence check and let the tuple continue. The unreadable-cursor 404 already covers legacy tabs. Alternatively, keep the check but update the Javadoc to give the actual reason for the restart.

### IN-09: The existence-check 404 echoes the decoded id, unlike the codec's fixed-text 404

**File:** `src/main/java/org/bartram/myfeeder/service/PriorityService.java:32`
**Issue:** `"Article not found: " + after.id()` ends up in the ProblemDetail `detail`. The value is a parsed `long` re-rendered by `Long.toString`, so it cannot carry an injection payload. But it comes from the opaque client token, and it is inconsistent with `PriorityPage`'s "never echoes the input" contract.
**Fix:** Use a fixed detail, for example `"Priority cursor article not found"`, or remove the check as described in IN-08.

## Resolved Since Previous Review

- **WR-02 (id-only cursor re-scored live, cursor-drift skips): RESOLVED** by 05-08. The next page compares against the served tuple (`InterestScoreQueries.java:114-126`). The residual skip in the opposite direction is tracked as WR-05.

## Previously reported, deferred

These findings come from the 2026-09-25 phase-05 review. They are still open and deferred by the user, and are outside 05-08's scope. They are not counted in the frontmatter totals above.

### WR-01 (deferred): Breakdown header and topic rows are read in two unsynchronized statements, so the rows may not sum to `total`

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:154-191` (called from `ArticleService.findByIdWithBreakdown`)
**Issue:** `breakdownInputs` runs the blend CTE twice, and `findByIdWithBreakdown` reads the article a third time, with no shared snapshot. A topic weight edit or delete, a "Re-score unread", or a Phase 6 learned-weight change between those statements hands `ScoreBreakdowns.apportion` exact values that don't add up to `total`. The clamp then quietly breaks the D-02 "rows sum exactly to total" contract.
**Fix:** Use `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)` on `findByIdWithBreakdown`, or fold the topic rows into the header statement (for example with `json_agg`). Log when `target - floorSum` falls outside `[0, n]`.

### WR-03 (deferred): Rubric mutations don't invalidate the reading pane's by-id article or the list badges

**File:** `src/main/frontend/src/hooks/useInterest.ts` (`useUpdateInterestTopic`, `useDeleteInterestTopic`, `useRescoreUnread` `onSuccess` handlers)
**Issue:** These handlers only light the Priority hint. After a topic is edited or deleted, or scores are reset, the open reading pane and the All/Feed/Starred/Board badges keep showing stale scores and "Why N?" rows until the 30s staleTime expires. That includes a deleted topic's name.
**Fix:** In those `onSuccess` handlers, invalidate `['articles']`, `['boards']`, and every by-id `['article', id]` query. Leave `PRIORITY_KEY` alone so D-07 still holds.

---

_Reviewed: 2026-09-26T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
