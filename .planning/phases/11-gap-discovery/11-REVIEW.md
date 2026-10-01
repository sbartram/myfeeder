---
phase: 11-gap-discovery
reviewed: 2026-10-01T23:08:47Z
depth: standard
files_reviewed: 33
files_reviewed_list:
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
findings:
  critical: 0
  warning: 2
  info: 5
  total: 7
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-10-01T23:08:47Z
**Depth:** standard
**Files Reviewed:** 33
**Status:** issues_found

## Summary

I reviewed the Phase 11 gap-discovery changes against the locked decisions in `11-CONTEXT.md` (D-01..D-18). The changes are: the candidate SQL (`TopicSuggestionStore.CANDIDATES`), list and dismiss in `TopicSuggestionService`, the atomic TOPIC_CREATED write in `InterestService.createTopic`, the `near-miss` constant and its validation, the frontend `SuggestedTopics` section, and the draft/source plumbing through `TopicsSection`, `TopicRow` and `FeedbackNotice`.

The backend matches the decisions:
- The D-08 predicate is correct, including D-10 (either weight sign) and D-11 (no topic rows counts as 0).
- `IDS_SCOPE` in `displayScores` covers read articles too, so engaged (usually read) articles keep their badge.
- `handle()` is a single `INSERT ... SELECT ... ON CONFLICT DO NOTHING` that joins the `@Transactional` topic insert, and atomicity is proven by `aFailedDismissalInsertRollsBackTheTopic`.
- Near-miss validation, the main and test yaml literals, and the bodyless PUT with no CORS configuration are all sound.
- No code path calls Jev.

I found no blockers. The two warnings are:
- **WR-01:** the D-06 window does not move when the user engages again with the same kind, but the docs and code comments say it does.
- **WR-02:** the dismiss mutation's optimistic cache update and pending state are not tied to the row that was clicked.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Engaging again does not move an article back into the 30-day window, but the docs and comments say it does

**File:** `src/main/java/org/bartram/myfeeder/repository/TopicSuggestionStore.java:32-42` (also `CLAUDE.md:197`, `src/main/frontend/src/hooks/engagementReaction.ts:12-15`)

**Issue:** `article_engagement` keeps the first `created_at` for each (article, kind) pair, because `ArticleEngagementStore.record` uses `ON CONFLICT (article_id, kind) DO NOTHING`. As a result, `HAVING MAX(g.created_at) > :cutoff` measures the most recent *first* engagement of each kind, not the latest engagement.

Example: the user clicks ↗ Open on an article they first opened 35 days ago.
- `afterEngagement` → `invalidateAfterLearnedChange` invalidates `['interest', 'suggestions']`.
- The article still never appears.

This contradicts three places:
- `CLAUDE.md:197` says the window is "measured on the latest `article_engagement.created_at`".
- The comment in `engagementReaction.ts` says "an engagement can add one".
- SC-1 says suggestions "engaged since the dialog last opened appear".

The store's Javadoc (lines 18-21) admits this, but the project docs and the reaction comment do not, so a future maintainer or Phase 12 tuning would reason from the wrong rule.

Only a new kind (star, board, Raindrop) or a Forget followed by a new open puts the article back in the window. Without a schema change the behavior cannot match "latest engagement", and this phase has no migration (carried decision D-13 of Phase 8). So the fix is to make the documented contract match the code.

**Fix:** State the actual rule in both places, and treat any wider change as a Phase 12 or follow-up decision:
```text
CLAUDE.md:197 — "... an engagement of any kind whose first recording (per kind) falls in the last 30 days
(`TopicSuggestionService.WINDOW_DAYS`); repeating an engagement of the same kind does not refresh it ..."

engagementReaction.ts:12-15 — "... a vote takes an article out of the list, and a first engagement of a
new kind can add one (a repeated engagement of the same kind keeps its original timestamp)."
```
To get true "latest engagement" semantics, a later phase would need a `last_engaged_at` column, or `ON CONFLICT ... DO UPDATE` on a separate column. Do not overwrite `created_at`, because decay depends on it.

### WR-02: The dismiss cache update and pending state are not tied to the row that was clicked

**File:** `src/main/frontend/src/hooks/useInterest.ts:206-215`, `src/main/frontend/src/components/InterestsDialog.tsx:678-685`

**Issue:** The whole section shares one `useDismissSuggestion()` instance. The Dismiss button's guard is `disabled={dismiss.isPending && dismiss.variables === s.articleId}`, and `variables` only ever holds the latest call. So the following sequence sends a second PUT for A while the first is still in flight:
1. Click Dismiss on row A.
2. Click Dismiss on row B before A resolves (row A's button re-enables).
3. Click Dismiss on A again.

The server treats the repeat as a no-op. On the client, however, `onSuccess` runs twice for A. The `filter` is idempotent but `total: Math.max(0, old.total - 1)` is not, so the capped heading (`Suggested topics (N of M)`) under-counts until the refetch lands.

Separately, a failed dismiss neither invalidates nor refetches anything. If the article is gone (404, for example deleted by retention while the dialog is open), the row stays, and every later click toasts "Article not found" until a window-focus refetch or a reopen.

**Fix:**
- Decrement `total` only when the item was actually present.
- Refetch on error too.
- Track pending ids per row, for example with `useMutationState`, or by disabling every row's Dismiss while any dismiss is pending.

```ts
onSuccess: (_r, articleId) => {
  qc.setQueryData<TopicSuggestions>(SUGGESTIONS_KEY, (old) => {
    if (!old || !old.items.some((s) => s.articleId === articleId)) return old
    return { items: old.items.filter((s) => s.articleId !== articleId), total: Math.max(0, old.total - 1) }
  })
},
onSettled: () => void qc.invalidateQueries({ queryKey: SUGGESTIONS_KEY }),
```

## Info

### IN-01: The heading count uses the unfiltered item count

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:637-642`

**Issue:** `items` drops rows whose source is already a saved topic (`created`), but the heading compares `data.total` with `data.items.length`. Between a draft save and the suggestions refetch, the header reads e.g. `Suggested topics (10 of 23)` above 9 rows. The mismatch is transient but visible.

**Fix:** Derive both numbers from the same set, for example `const hidden = data.items.length - items.length` and the heading `(${items.length} of ${data.total - hidden})`. Or skip the count until the refetch lands.

### IN-02: The draft builder is duplicated across the two "one draft path" entry points

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:596`, `src/main/frontend/src/components/FeedbackNotice.tsx:25`

**Issue:** D-14 says "There is one draft path", but the `title.trim().slice(0, 500)` rule (D-20 / D-12) is written in two places. A future change to the description rule (for example a surrogate-safe cut, since `.slice` on UTF-16 can split an emoji pair at character 500) must be made twice.

**Fix:** Export one helper, e.g. `topicDraftFromArticle(id, title, weight): TopicDraft`, next to `TopicDraft`, and call it from both places.

### IN-03: `addDraft`'s parameter shadows the `draft` prop

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:486`

**Issue:** `const addDraft = (draft?: TopicDraft) => ...` shadows the `draft` prop that `TopicsSection` also uses (lines 452-478). It works today, but someone editing `addDraft` could easily read the seeded prop by mistake.

**Fix:** Rename the parameter, e.g. `(prefill?: TopicDraft)`.

### IN-04: Two "section hidden" tests can pass before the response lands

**File:** `src/main/frontend/src/components/InterestsDialog.test.tsx:1317-1339`

**Issue:** `hiddenWhenThereAreNoSuggestions` and `hiddenWhenTheListFails` wait for the GET to be *issued*, sleep 20 ms, and then assert absence. Absence is also the state before the query resolves. If the mocked fetch were slower, or the component started rendering the section from cached data, both tests would still pass without proving anything.

**Fix:** Wait on query settlement instead of a timer. For example, assert through the QueryClient that `getQueryState(['interest','suggestions'])?.status` is `'success'` or `'error'` inside `waitFor`, and only then assert that the section is absent.

### IN-05: A failed suggestions load is silent

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:636-638`, `src/main/frontend/src/hooks/useInterest.ts:184-196`

**Issue:** A 500 from `GET /api/interest/suggestions` hides the section, exactly like an empty list. D-02 only asks for hiding when there are no suggestions. Hiding on error is a deliberate extension (documented in the hook's comment), but it gives no signal when the endpoint breaks in production. The Topics section, by contrast, shows a note when learned adjustments fail (`learned.isError`, lines 544-548).

**Fix:** Optional: render a one-line note on `suggestions.isError`, matching the learned-adjustments pattern. Or leave it and record the choice in `11-CONTEXT.md`.

---

_Reviewed: 2026-10-01T23:08:47Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
