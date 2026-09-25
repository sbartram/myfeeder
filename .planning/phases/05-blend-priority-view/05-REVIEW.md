---
phase: 05-blend-priority-view
reviewed: 2026-09-25T00:00:00Z
depth: standard
files_reviewed: 53
files_reviewed_list:
  - src/main/frontend/src/App.css
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/api/articles.ts
  - src/main/frontend/src/components/ArticleList.test.tsx
  - src/main/frontend/src/components/ArticleList.tsx
  - src/main/frontend/src/components/BoardArticleList.test.tsx
  - src/main/frontend/src/components/BoardArticleList.tsx
  - src/main/frontend/src/components/EmptyState.tsx
  - src/main/frontend/src/components/FeedPanel.test.tsx
  - src/main/frontend/src/components/FeedPanel.tsx
  - src/main/frontend/src/components/InterestBadge.test.tsx
  - src/main/frontend/src/components/InterestBadge.tsx
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/components/PriorityBanner.test.tsx
  - src/main/frontend/src/components/PriorityBanner.tsx
  - src/main/frontend/src/components/PriorityList.test.tsx
  - src/main/frontend/src/components/PriorityList.tsx
  - src/main/frontend/src/components/ReadingPane.test.tsx
  - src/main/frontend/src/components/ReadingPane.tsx
  - src/main/frontend/src/components/ScoreRow.tsx
  - src/main/frontend/src/components/ShortcutOverlay.tsx
  - src/main/frontend/src/components/WhyBreakdown.test.tsx
  - src/main/frontend/src/components/WhyBreakdown.tsx
  - src/main/frontend/src/hooks/useArticles.ts
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
  - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
  - src/main/frontend/src/hooks/usePriorityArticles.test.ts
  - src/main/frontend/src/hooks/usePriorityArticles.ts
  - src/main/frontend/src/stores/priorityStore.ts
  - src/main/frontend/src/types/index.ts
  - src/main/frontend/src/utils/interest.test.ts
  - src/main/frontend/src/utils/interest.ts
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/config/SpaForwardController.java
  - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - src/main/java/org/bartram/myfeeder/model/Article.java
  - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/ArticleService.java
  - src/main/java/org/bartram/myfeeder/service/BoardService.java
  - src/main/java/org/bartram/myfeeder/service/PriorityService.java
  - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/SpaForwardControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/PriorityServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
  - src/test/resources/application.yaml
findings:
  critical: 0
  warning: 3
  info: 7
  total: 10
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-09-25T00:00:00Z
**Depth:** standard
**Files Reviewed:** 53
**Status:** issues_found

## Summary

I reviewed the phase diff (`9903ead..HEAD`) across the blend CTE (`InterestScoreQueries`), the Priority endpoint and service, the score enrichment in `ArticleService` and `BoardService`, the breakdown apportionment (`ScoreBreakdowns`), and the frontend Priority view (list, banner, infinite query, keyboard wiring, reading-pane score row and Why panel).

The SQL is safe. Every scope fragment is a compile-time constant, and every request value is a bound parameter. The unscored guard (`CASE WHEN b.raw_n IS NULL`) is correct. The `'-Infinity'` sort key and the row-value keyset comparison are internally consistent. I traced the largest-remainder apportionment through positive, negative and half-boundary values. It sums to `total` whenever its inputs come from a single consistent snapshot. On the client, the Priority query key sits outside the `['articles']` prefix, and I found no broad `invalidateQueries()` anywhere that could re-rank it by accident. `Shift+A` is guarded on the route.

I found no blockers. The three warnings are about consistency under concurrent change:
- The breakdown is read in two separate statements, so a concurrent write can produce rows that don't sum to the total.
- The Priority cursor is an id whose sort key is recomputed live on every page, so a score change mid-walk can silently skip rows. Phase 6's learned weights will make this routine.
- Interest-rubric mutations don't invalidate the cached article or list queries, so badges and "Why N?" breakdowns go stale.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Breakdown header and topic rows are read in two unsynchronized statements, so rows may not sum to `total`

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:144-181` (called from `src/main/java/org/bartram/myfeeder/service/ArticleService.java:32-43`)
**Issue:** `breakdownInputs` runs the blend CTE twice. The first statement gets `raw`, `total`, `display` and the profile inputs. The second gets the per-topic contributions. There is no transaction, and Postgres READ COMMITTED gives each statement its own snapshot. Several writes can land between the two statements:
- a topic weight edit or topic delete (cascades `article_topic_score`)
- "Re-score unread" (deletes the `article_score` row and cascades the topic rows)
- a Phase 6 learned-weight change

When that happens, `ScoreBreakdowns.apportion` gets `exact` values that no longer add up to `total`. Its clamp `k = max(0, min(n, target - floorSum))` then quietly returns points that don't sum to the displayed total, which breaks the D-02 contract ("the rows' points sum exactly to total") that `WhyBreakdown` relies on. In the delete case, the header shows a score of N while the rows list no topics at all. `findByIdWithBreakdown` also reads the article itself in a third statement.
**Fix:** Read all three in one snapshot. Either annotate the service method:
```java
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public Optional<Article> findByIdWithBreakdown(Long id) { ... }
```
or fold the topic rows into the header query (for example with `json_agg` over `contrib` in the same statement). Also consider logging when `target - floorSum` falls outside `[0, n]` rather than clamping silently.

### WR-02: The id-only keyset cursor is re-scored live on every page, so a score change mid-walk skips rows

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:105-116` (and `src/main/frontend/src/hooks/usePriorityArticles.ts:13-29`)
**Issue:** `priorityPageAfter` recomputes the cursor article's `sort_score` from the current blend (`SELECT c.sort_score ... FROM keyed c WHERE c.id = :cursorId`). The client deliberately freezes the loaded pages (D-07/D-09), but the page boundary is not frozen. Several events change the cursor's live score between pages:
- a topic weight lowered or made negative, or a topic deleted (these take effect in the blend immediately)
- the cursor article being re-scored
- from Phase 6, every thumbs vote that moves a `learned` delta

If the cursor's score drops, the next page starts below the cursor's new position, and every article between its old and new position is never shown in that walk. `dedupeById` only handles the opposite case, where the cursor's score rises and rows come back as duplicates. Skips are silent: the list just looks complete. PRIO-05 lets the user vote and triage inside Priority, so once Phase 6 lands, a vote followed by `j` past the last row or "Load more" will routinely produce this.
**Fix:** Make the cursor carry the sort tuple as it was when the page was served, instead of re-deriving it. One option is to return an opaque cursor `(sort_score, sort_date, id)`, for example base64 of `raw_n|epochMicros|id`, and compare against those literal values:
```sql
... AND (k.sort_score, k.sort_date, k.id) < (:cursorScore, :cursorDate, :cursorId)
```
This keeps the walk monotone in the ranking the user is looking at. Rows whose score changes can still move across the boundary, but the rest of the list is no longer skipped. If the id-only cursor stays, document the skip behavior in R4 and make the "Ranking changed" hint fire on the Phase 6 vote path.

### WR-03: Rubric mutations don't invalidate the reading pane's by-id article or the list badges

**File:** `src/main/frontend/src/hooks/useInterest.ts:78-83, 104-108, 134-137`
**Issue:** Topic weight and name edits and topic deletes change the live blend right away. "Re-score unread" deletes score rows. `useUpdateInterestTopic`, `useDeleteInterestTopic` and `useRescoreUnread` only light the Priority hint. They don't invalidate `['article', id]` or `['articles']`. After the Interests dialog closes, the open reading pane keeps showing the old badge, old chips and old "Why N?" rows (including a deleted topic's name) until the 30s staleTime expires and something triggers a refetch. The same applies to badges in All/Feed/Starred/Board lists. Only the Priority refresh path (`refreshPriority`) invalidates by-id articles.
**Fix:** In those `onSuccess` handlers, add:
```ts
void qc.invalidateQueries({ queryKey: ['articles'] })
void qc.invalidateQueries({ predicate: (q) => q.queryKey[0] === 'article' && q.queryKey.length === 2 })
void qc.invalidateQueries({ queryKey: ['boards'] }) // board article pages carry badges too
```
Leave `PRIORITY_KEY` alone so D-07 still holds.

## Info

### IN-01: `withScores` is duplicated verbatim in two services

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleService.java:98-106`, `src/main/java/org/bartram/myfeeder/service/BoardService.java:54-60`
**Issue:** The same enrichment appears twice. D-18 says every article response must be enriched, so a third caller could easily drift from these two.
**Fix:** Move it to one method, e.g. `InterestScoreQueries.enrich(List<Article>)`, and call that from both services.

### IN-02: `PriorityList` copies `formatTime` and the row markup from `ArticleList`

**File:** `src/main/frontend/src/components/PriorityList.tsx:14-21, 112-128`
**Issue:** A duplicated helper and row JSX. Changes to row rendering (meta line, starred marker, badge slot) now have to be made in three list components.
**Fix:** Extract a shared `ArticleRow` component and a shared `formatTime` utility.

### IN-03: `"Not yet scored"` also covers articles that will never be scored

**File:** `src/main/frontend/src/components/PriorityList.tsx:83, 107-111`; `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:63`
**Issue:** Anything without a SCORED row sorts to `-Infinity` under the "Not yet scored" separator. That includes SKIPPED (terminal) rows, FAILED rows with all attempts used, and unread articles outside the eligibility window. It also applies to every row when Jev is not configured, so the separator appears at the very top right under the "listed by date" banner. The label suggests the articles are waiting to be scored when many never will be.
**Fix:** Consider hiding the separator when `status.configured === false` and when every row is unscored, or use a neutral label such as "Unscored".

### IN-04: Asynchronous `j` selection can override a later user selection

**File:** `src/main/frontend/src/hooks/useKeyboardShortcuts.ts:107-109`
**Issue:** `onPriorityNextPage().then(id => setSelectedArticle(id))` runs after the network round trip. If the user presses `k`, clicks another row or leaves `/priority` in the meantime, their selection is replaced.
**Fix:** Capture `selectedArticleId` before the fetch and only apply the new id if the selection hasn't changed, e.g. `if (id !== undefined && useUIStore.getState().selectedArticleId === startId) setSelectedArticle(id)`.

### IN-05: `aria-controls="why-breakdown"` points at an element that isn't rendered

**File:** `src/main/frontend/src/components/ScoreRow.tsx:37-38`
**Issue:** When `whyOpen` is false, or the breakdown is missing, no element has `id="why-breakdown"`, so the ARIA reference dangles.
**Fix:** Render `aria-controls` only when the panel is mounted, or keep the panel mounted with `hidden`.

### IN-06: Tooltip can show `−0.0 pts`

**File:** `src/main/frontend/src/components/WhyBreakdown.tsx:41`
**Issue:** `formatSigned(row.exact, 1)` picks the sign from the unrounded value, so an exact of -0.04 renders as "−0.0".
**Fix:** Round first, then format: `formatSigned(Math.round(row.exact * 10) / 10, 1)`.

### IN-07: `myfeeder.interest.blend.profile-points` is not validated

**File:** `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java:52-56`
**Issue:** Zero or negative values are accepted silently. That would invert or remove the profile's contribution to every badge and to the Priority order. Phase 7 is expected to tune this value.
**Fix:** Add `@Validated` to the properties class and `@PositiveOrZero` (or `@Min(1)`) to `profilePoints`, or reject bad values at startup.

---

_Reviewed: 2026-09-25T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
