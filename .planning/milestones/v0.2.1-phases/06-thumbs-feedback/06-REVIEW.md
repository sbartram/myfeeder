---
phase: 06-thumbs-feedback
reviewed: 2026-09-27T04:17:33Z
depth: standard
files_reviewed: 58
files_reviewed_list:
  - .pre-commit-config.yaml
  - src/main/frontend/src/api/articles.ts
  - src/main/frontend/src/api/client.test.ts
  - src/main/frontend/src/api/client.ts
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/App.css
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/components/FeedbackBar.test.tsx
  - src/main/frontend/src/components/FeedbackBar.tsx
  - src/main/frontend/src/components/FeedbackNotice.test.tsx
  - src/main/frontend/src/components/FeedbackNotice.tsx
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/components/NarrowPicker.test.tsx
  - src/main/frontend/src/components/NarrowPicker.tsx
  - src/main/frontend/src/components/ReadingPane.test.tsx
  - src/main/frontend/src/components/ReadingPane.tsx
  - src/main/frontend/src/components/ShortcutOverlay.tsx
  - src/main/frontend/src/components/Toast.tsx
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/components/TopicRow.tsx
  - src/main/frontend/src/components/WhyBreakdown.test.tsx
  - src/main/frontend/src/components/WhyBreakdown.tsx
  - src/main/frontend/src/hooks/useFeedback.test.ts
  - src/main/frontend/src/hooks/useFeedback.ts
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
  - src/main/frontend/src/hooks/useKeyboardShortcuts.ts
  - src/main/frontend/src/hooks/usePriorityArticles.test.ts
  - src/main/frontend/src/hooks/usePriorityArticles.ts
  - src/main/frontend/src/stores/feedbackStore.ts
  - src/main/frontend/src/types/index.ts
  - src/main/frontend/src/utils/feedback.test.ts
  - src/main/frontend/src/utils/feedback.ts
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/controller/ArticleController.java
  - src/main/java/org/bartram/myfeeder/controller/FeedbackRequest.java
  - src/main/java/org/bartram/myfeeder/controller/InterestController.java
  - src/main/java/org/bartram/myfeeder/model/Article.java
  - src/main/java/org/bartram/myfeeder/model/ArticleFeedback.java
  - src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleFeedbackStore.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
  - src/main/java/org/bartram/myfeeder/service/ArticleService.java
  - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
  - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
  - src/main/java/org/bartram/myfeeder/service/ScoreBreakdowns.java
  - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestScoreQueriesTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ScoreBreakdownsTest.java
  - src/test/resources/application.yaml
findings:
  critical: 0
  warning: 4
  info: 5
  total: 9
status: issues_found
---

# Phase 6: Code Review Report

**Reviewed:** 2026-09-27T04:17:33Z
**Depth:** standard
**Files Reviewed:** 58
**Status:** issues_found

## Narrative Findings (AI reviewer)

## Summary

I reviewed the thumbs-feedback slice end to end: the learned CTE and its bindings in `InterestScoreQueries`, the vote store, service, controller and DTOs, and the frontend vote hook, toast formatter, pane controls, narrow picker, Interests draft and learned line.

The core model is sound:
- The learned CTE matches R2 (hinge-gated sum, cap, sign clamp, ±50 range, `topics_narrowed` honored).
- Every blend caller now binds `learnRate` and `learnedCap` through `blendSql`.
- Validation runs before any write, and the 400s use fixed text.
- The PUT is JSON-only.
- No Priority invalidation was introduced; the `['articles']` prefix does not match `['priority']`.

I found no blockers. The warnings are about consistency under concurrency and about how accurate the effect message is:
1. The optimistic vote state can be overwritten by in-flight by-id refetches.
2. The effect list includes topics that a narrowed vote never targeted.
3. Breakdown and before/after reads are not taken from one snapshot, now that votes change the learned weights between statements.
4. Re-score leaves the learned values in the Interests dialog stale.

## Warnings

### WR-01: In-flight `['article', id]` refetches can overwrite the vote intent and the vote result

**File:** `src/main/frontend/src/hooks/useFeedback.ts:24-26, 62, 77-81, 84-94` (interacts with `src/main/frontend/src/hooks/useArticles.ts:55`)

**Issue:** `writeIntent` and the `onSuccess` `setQueryData` follow the optimistic-update pattern without cancelling outgoing queries first. `['article', id]` is refetched often:
- `useUpdateArticleState` invalidates it on every star toggle, read toggle and auto-mark-read (`ReadingPane.tsx:74-77`, `useArticles.ts:55`).
- The vote hook's own `onError` invalidates it (line 80).

Race 1, shown through a star toggle:
1. The user presses `s`, then `u` while the star's GET refetch is still in flight.
2. The GET resolves with the pre-vote server state, which erases the 👍 intent.
3. If the user presses `u` again in that window, `press` reads `current = 0` from the overwritten cache and sends another up-vote instead of removing it. The result is the opposite of what the user asked for.

Race 2: a GET issued before the PUT committed can resolve after `onSuccess`. The pane then keeps showing "no vote" while the server holds one, until the next refetch.

Race 3: `onError` for vote A invalidates the article while a newer vote B on the same article is still queued in the scope. This drops B's cached intent, and the refetch can land after B's `onSuccess`. The newer-pending check that `onSuccess` already does is missing here.

**Fix:**
```ts
function writeIntent(qc: QueryClient, id: number, feedback: ArticleFeedback | null): void {
  void qc.cancelQueries({ queryKey: ['article', id], exact: true })
  qc.setQueryData<Article>(['article', id], (old) => (old ? { ...old, feedback } : old))
}
// onSuccess: cancel again before writing the server result
void qc.cancelQueries({ queryKey: ['article', v.id], exact: true })
qc.setQueryData<Article>(['article', v.id], { ...res.article, feedback })
// onError: only revert when no newer vote on this article is queued
const newerPending = qc.isMutating({ mutationKey: ['feedback'],
  predicate: (m) => (m.state.variables as VoteVars | undefined)?.id === v.id }) > 1
if (!newerPending) void qc.invalidateQueries({ queryKey: ['article', v.id], exact: true })
```
Add a test in `useFeedback.test.ts`. It should resolve a pending `getById` after `press` and assert that the intent survives.

### WR-02: The effect list includes matched topics that a narrowed vote never targeted, with misleading limit notes

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java:94-108`; `src/main/frontend/src/utils/feedback.ts:107-110`

**Issue:** `applyAndReport` returns a `TopicEffect` for every matched topic, and it computes `LearnedLimit.of(after)` from the topic's absolute state, whether or not this write could move that topic. For a narrowed 👎 (or removing one), the unpicked matched topics have `before == after`. If such a topic is already capped or clamped by other votes, it still has a non-`NONE` limit, and `formatVoteToast` keeps any row whose `limit !== 'NONE'` (line 109).

Example:
- The user narrows to "Politics".
- The toast reads "👎 Narrowed · Politics −2.0 · Rust +0.0 (learned at max +20)".

This suggests the vote tried to move Rust and was blocked, which contradicts D-07/D-09: zero-change topics are listed only when a cap or clamp explains why the vote had no effect.

**Fix:** On the server, report effects only for topics the write could affect: the union of the effective target sets before and after the write. For a stored vote, the target set is its picks if narrowed, otherwise all matched topics; for no vote it is empty. Capture the stored vote before the write, for example:
```java
Set<Long> targets = new LinkedHashSet<>(targetsOf(store.find(articleId), matched));
// ...write...
targets.addAll(targetsOf(store.find(articleId), matched));
List<TopicEffect> effects = matched.stream().filter(targets::contains)...
```
Alternatively, add a `targeted` flag to `TopicEffect` and have the client apply the limit exception only to targeted topics.

### WR-03: Breakdown and before/after reads span several statements under READ COMMITTED, so a concurrent vote can tear them

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleService.java:35-47`; `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:193-230`; `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java:91-110`

**Issue:** Before this phase the learned weights were always 0, so the header query and the topic-row query in `breakdownInputs` saw the same weights. Now every vote commit changes `w` for every article that shares a topic.

`findByIdWithBreakdown` runs outside any transaction, and `vote`/`clear` run at the default READ COMMITTED, so each statement takes a fresh snapshot. The fast-triage race:
1. The user presses `d`, then `j`.
2. The GET for the next article runs while the PUT commits.
3. It reads `total` from before the vote and the topic `exact` values from after it.
4. `ScoreBreakdowns.apportion` clamps `k` to `[0, n]`, so the "Why N?" rows no longer sum to the badge. This breaks the D-02 invariant that this phase restates in D-11.

On Priority, the neighbouring by-id article is never invalidated after a vote, so the torn breakdown stays until the next refetch. The same gap lets a concurrent write land between the `before` and `after` reads of `applyAndReport`.

**Fix:** Read each result from one snapshot:
```java
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public Optional<Article> findByIdWithBreakdown(Long id) { ... }

@Transactional(isolation = Isolation.REPEATABLE_READ)
public FeedbackResult vote(...)  // and clear(...)
```
Alternatively, compute the header and topic rows in a single SQL statement.

### WR-04: Re-score does not refresh the learned values that the Interests dialog shows

**File:** `src/main/frontend/src/hooks/useInterest.ts:141-150`

**Issue:** `useRescoreUnread` deletes the SCORED rows in the Re-score scope (`ArticleScoreStore.deleteRescoreScope`). `LEARNED_CTE` counts votes only on SCORED articles, so every vote on an unread article in the window stops counting until the sweep re-judges it. The re-judged nouls can also change the result.

The Re-score button lives in the Interests dialog, the same place that shows each topic's `LearnedLine`. This phase added `['interest', 'learned']` invalidation to create, update and delete topic, but not to rescore. The dialog therefore keeps showing learned and effective weights that the ranking no longer uses, which breaks FDBK-07's "the editor prints the server's values".

**Fix:**
```ts
onSuccess: () => {
  void qc.invalidateQueries({ queryKey: ['interest', 'status'] })
  void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
  usePriorityStore.getState().setRankingChanged(true)
},
```
Also consider documenting, in the rescore confirm copy or in CLAUDE.md, that votes on re-scored articles stop counting until the sweep re-judges them.

## Info

### IN-01: A flipped vote keeps its original `created_at`

**File:** `src/main/java/org/bartram/myfeeder/repository/ArticleFeedbackStore.java:31-33`
**Issue:** `ON CONFLICT ... DO UPDATE SET vote, topics_narrowed` never updates `created_at`, so a flip or re-narrow keeps the first vote's timestamp. Nothing reads the column today, but Phase 7 tuning (OPS-02) or any recency weighting would misread it.
**Fix:** Add `created_at = NOW()` to the `DO UPDATE SET` list, or rename or document the column as "first voted at".

### IN-02: The ±50 weight range is hard-coded in SQL and duplicated in Java

**File:** `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:115-117`; `src/main/java/org/bartram/myfeeder/service/LearnedLimit.java:30`
**Issue:** `eff2` inlines `50`/`-50`, while `LearnedLimit` uses `InterestService.MAX_WEIGHT`. If the constant changes, the SQL clamp and the `WEIGHT_RANGE` label drift apart without any error.
**Fix:** Build the literal from the constant (`"LEAST(" + InterestService.MAX_WEIGHT + ", ...)"`), or bind it as a parameter the same way as `learnedCap`.

### IN-03: `formatSigned` can print "−0.0" in the new learned and effect copy

**File:** `src/main/frontend/src/utils/feedback.ts:88`; `src/main/frontend/src/components/TopicRow.tsx:236`
**Issue:** `formatDelta` rounds before choosing the sign, but `effectNote` (`(now ${formatSigned(e.after, 1)})`) and the `baseEdited` branch of `LearnedLine` call `formatSigned` directly. That function picks the sign from the unrounded value. For example, base −10 with learned +9.96 prints "(now −0.0)". A topic with no votes and an unsaved base edit shows "Learned from votes 0.0".
**Fix:** Round to one decimal before calling `formatSigned` (or reuse `formatDelta`-style rounding) in both places.

### IN-04: Picker digit keys ignore modifiers, which swallows browser tab switching

**File:** `src/main/frontend/src/components/NarrowPicker.tsx:74-76`
**Issue:** `/^[1-9]$/` matches Cmd/Ctrl+1..9, and the handler then calls `preventDefault()`. With the picker focused, Cmd+1 toggles a topic instead of switching tabs. The `u`/`d` handler already skips modified keys (Pitfall 6).
**Fix:** `if (/^[1-9]$/.test(e.key) && !(e.metaKey || e.ctrlKey || e.altKey)) { ... }`

### IN-05: `LearnedLimit` looks only at the state after the write

**File:** `src/main/java/org/bartram/myfeeder/service/LearnedLimit.java:22-34`
**Issue:** The limit is judged only on the post-write weights. If a clamp in the before-state absorbed the vote and the after-state lands exactly on a boundary, the result is `NONE`. The doc says "a sum of exactly 0 is not a clamp". Example: base +10, learned −12 → 👍 → learned −10, effective 0 → 0. The toast then says "Effect under 0.1 points" instead of "(can't cross 0)". This is an edge case, but it does not meet D-07's promise to explain a missing effect.
**Fix:** Pass the `before` `TopicWeight` into `LearnedLimit.of`, and report the clamp or cap if either side was limited.

---

_Reviewed: 2026-09-27T04:17:33Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
