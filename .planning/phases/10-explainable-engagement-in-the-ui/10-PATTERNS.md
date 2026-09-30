# Phase 10: Explainable Engagement in the UI - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 20 (new + modified, incl. tests)
**Analogs found:** 20 / 20 (almost every file is modified in place; its own current code or `useFeedback.ts` is the analog)

Paths below are relative to repo root; frontend = `src/main/frontend/src/`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `frontend/hooks/engagementReaction.ts` (NEW) | utility (cache reaction) | event-driven / request-response | `hooks/useFeedback.ts` `onSuccess` (lines ~52-75) + `hooks/usePriorityArticles.ts` `patchPriorityArticle` | exact |
| `frontend/hooks/engagementReaction.test.ts` (NEW, or fold into engagementRefresh.test.ts) | test | — | `hooks/useFeedback.test.ts` `renderVote` (lines 100-112) | exact |
| `frontend/hooks/useEngagement.ts` (`useOpenOriginal`, `useForgetEngagement`) | hook | fire-and-forget / mutation | itself + `useVoteFeedback` (`useMatch`) | exact |
| `frontend/hooks/useArticles.ts` (`useUpdateArticleState`, `useSaveToRaindrop`) | hook | mutation | itself (lines 48-61, 75-85) | exact |
| `frontend/hooks/useBoards.ts` (`useAddArticleToBoard`, `useReadLater`) | hook | mutation | itself (lines 28-59) | exact |
| `frontend/hooks/useFeedback.ts` (optional: share invalidation set) | hook | mutation | itself | exact |
| `frontend/components/WhyBreakdown.tsx` `TopicLabel` | component | transform | itself (lines 69-93) | exact |
| `frontend/components/TopicRow.tsx` `LearnedLine` | component | transform | itself (lines 220-256) | exact |
| `frontend/utils/feedback.ts` `effectNote`/`formatVoteToast` | utility | transform | itself (`formatDelta` l.49, `effectNote` l.~76) | exact |
| `frontend/types/index.ts`, `frontend/api/interest.ts` | model (types) | — | existing optional `thumbsLearned?`/`engagementLearned?` fields (types:78-80,112-115; api/interest.ts:32-35) | exact |
| `service/LearnedLimit.java` | model/enum | transform | itself | exact |
| `service/FeedbackResult.java` (`TopicEffect` + `engagementReplaced`) | model (DTO record) | — | itself (append after `engagementLearned`) | exact |
| `service/TopicLearned.java` (+ optional `engagementAtCap`) | model (DTO record) | — | itself | exact |
| `service/ArticleFeedbackService.java` (`applyAndReport`, `learnedTopics`) | service | CRUD | itself (lines 73-78, 95-117) | exact |
| `repository/InterestScoreQueries.java` (Javadoc only) | repository | — | itself (lines 182-190) | exact |
| Tests: `engagementRefresh.test.ts`, `useEngagement.test.ts`, `ScoreRow.test.tsx`, `usePriorityArticles.test.ts` | test | — | `useFeedback.test.ts` MemoryRouter wrapper | exact |
| Tests: `WhyBreakdown.test.tsx`, `TopicRow.test.tsx`, `feedback.test.ts`, `PriorityList.test.tsx` | test | — | themselves | exact |
| Tests: `ArticleFeedbackServiceTest.java`, `FeedbackApiIntegrationTest.java`, `PriorityApiIntegrationTest.java`, `ArticleControllerTest`, `InterestControllerTest` | test | — | themselves | exact |
| `CLAUDE.md`, `09-REVIEW-DISPOSITION.md`, `STATE.md` | docs | — | — | n/a |

## Pattern Assignments

### `hooks/engagementReaction.ts` (NEW, utility)

**Analog:** `hooks/useFeedback.ts` — `useVoteFeedback.onSuccess`. Copy the invalidation set verbatim:
```typescript
void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
void qc.invalidateQueries({ queryKey: ['articles'] })
if (v.onPriority) {
  // D-06: patch only the voted row; the Priority key is never invalidated or refetched.
  patchPriorityArticle(qc, v.id, { interestScore: res.article.interestScore ?? null })
  usePriorityStore.getState().setRankingChanged(true)
} else {
  // D-05: every other by-id article re-reads its badge; ['article', n, 'extracted'] is left alone.
  void qc.invalidateQueries({
    predicate: (q) => q.queryKey[0] === 'article' && q.queryKey.length === 2 && q.queryKey[1] !== v.id,
  })
}
```
Imports convention (from useFeedback.ts lines 1-10): relative imports `../api/articles`, `../stores/priorityStore`, `./usePriorityArticles`, `import type {...} from '../types'`.

Differences from the vote: engagement endpoints return no article, so fetch with
`await qc.query({ queryKey: ['article', id], queryFn: () => articlesApi.getById(id), staleTime: 0 })`
(staleTime 0 mandatory — client default 30s in `queryClient.ts:11`), then compare with the loaded Priority row
(read via `qc.getQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY)`) and patch+hint only when different.
Wrap in try/catch; never reject. Optional Pitfall 4 guard mirrors the vote's own check:
```typescript
qc.isMutating({ mutationKey: ['feedback'], predicate: (m) => (m.state.variables as VoteVars | undefined)?.id === v.id })
```
Full sketch: RESEARCH.md Pattern 1.

**Patch primitive** (`hooks/usePriorityArticles.ts:93-112`): `patchPriorityArticle(qc, id, { interestScore })` — only copies present keys, never creates a cache entry. Use it; do not hand-roll `setQueryData` over pages.

---

### `hooks/useEngagement.ts` (hook)

**Analog:** itself. Current `useOpenOriginal` chain:
```typescript
window.open(article.url, '_blank', 'noopener')
void Promise.resolve()
  .then(() => articlesApi.recordOpen(article.id))
  .then(() => qc.invalidateQueries({ queryKey: ['article', article.id], exact: true }))
  .catch(() => {})
```
Replace the second `.then` with `afterEngagement(qc, article.id, onPriority)`; add `const onPriority = useMatch('/priority') !== null` and add `onPriority` to the `useCallback` deps. Update the header Javadoc ("On success only the exact by-id article query refreshes (D-06)").

`useForgetEngagement` current:
```typescript
onSuccess: (_data, id) => qc.invalidateQueries({ queryKey: ['article', id], exact: true }),
```
→ `onSuccess: (_data, id) => { void afterEngagement(qc, id, onPriority) }` (block body — do NOT return the promise, Pitfall 5).

**Priority detection** (copy from `useFeedback.ts`): `import { useMatch } from 'react-router-dom'` and `const onPriority = useMatch('/priority') !== null`.

---

### `hooks/useArticles.ts` (hook)

`useUpdateArticleState` (lines 48-61) — keep existing body, append:
```typescript
if (variables.state.starred === true) void afterEngagement(qc, variables.id, onPriority)
```
Note the existing `qc.invalidateQueries({ queryKey: ['article', variables.id] })` stays; `qc.query` dedupes onto it.

`useSaveToRaindrop` (lines 75-85) — replace
```typescript
void qc.invalidateQueries({ queryKey: ['article', id], exact: true })
```
with `void afterEngagement(qc, id, onPriority)`; keep the `'Saved to Raindrop'` toast.

---

### `hooks/useBoards.ts` (hook)

`useAddArticleToBoard` (28-39): replace line 35 by-id invalidation with `void afterEngagement(qc, articleId, onPriority)`; keep `return qc.invalidateQueries({ queryKey: ['boardArticles', boardId] })`.
`useReadLater` (41-59): replace line 54 by-id invalidation likewise; keep boards/boardArticles invalidations and toasts.

---

### `components/WhyBreakdown.tsx` `TopicLabel` (lines 69-93)

Current split branch gated on `learnedWeight`:
```tsx
const learned = row.learnedWeight ?? 0
if (row.baseWeight !== undefined && Math.round(learned * 10) !== 0) {
  const weight = formatSigned(row.weight, 1)
  return (
    <span className="why-label"
      title={`Match ${matchPct}% → counts ${countsPct}% × ${weight} = ${formatSigned(row.exact, 1)} pts · base ${formatSigned(row.baseWeight)}, learned ${formatSigned(learned, 1)}`}>
      {`${row.name}  ${countsPct}% × ${weight} (${formatSigned(row.baseWeight)} ${formatSigned(learned, 1)} learned)`}
    </span>
  )
}
```
Rewrite: gate on `showVotes || showEngaged` from `row.thumbsWeight ?? 0` / `row.engagementWeight ?? 0`; label parts `"+2.0 votes"`, `"+1.5 engaged"` omitted when rounding to zero; tooltip lists `base, votes, engaged` always, formatted with `formatDelta` (`utils/feedback.ts:49`, prints `+0.0` never `−0.0`). Plain Phase 5 branch unchanged except optionally the tooltip suffix (Assumption A3). See RESEARCH Pattern 3.

---

### `components/TopicRow.tsx` `LearnedLine` (lines 220-256)

Current structure (capSuffix / effSuffix / three branches):
```tsx
const capSuffix = learned.limit === 'LEARNED_CAP' ? (learned.learned > 0 ? ' (at max)' : ' (at min)') : ''
const effSuffix = learned.limit === 'SIGN_CLAMP' ? " (can't cross 0)"
  : learned.limit === 'WEIGHT_RANGE' ? (learned.effectiveWeight > 0 ? ' (at the +50 limit)' : ' (at the −50 limit)') : ''
const value = (text: string) => <span className="interests-learned-value">{text}</span>
// baseEdited: "Learned from votes {value} {capSuffix} · Effective weight updates when you save"
// zero:       "No learned adjustment yet · Effective weight {value}{effSuffix}"
// default:    "Learned from votes {value}{capSuffix} · Effective weight {value}{effSuffix}"
```
Keep the three branches and effSuffix; replace "Learned from votes X{capSuffix}" with `Learned X (votes ±a[ at max|at min], engaged +b[ at max])`, omitting zero-rounded parts and the `()` when both are zero (Pitfall 7). Engaged "at max" = `learned.engagementAtCap ?? learned.limit === 'ENGAGEMENT_CAP'`. Keep the `interests-learned` / `interests-learned-value` classNames.

---

### `utils/feedback.ts` (`effectNote`, `formatVoteToast`)

Current `effectNote(e)` switch (LEARNED_CAP / SIGN_CLAMP / WEIGHT_RANGE / default `(now …)` for negative base). Add a `kind: VoteKind` param; in `default` (covers NONE and ENGAGEMENT_CAP) return `' (engagement restored)'` when `kind === 'removed'` and `e.engagementReplaced`, else `' (replaces engagement)'`, before the `(now …)` fallback. Listing filter: change `round(d) != 0 || limit != NONE` to also treat ENGAGEMENT_CAP as NONE and list when `engagementReplaced` (RESEARCH Pattern 5). Use existing `LEADS`/`SAVED_LEADS`/`MAX_LISTED` unchanged.

---

### `service/LearnedLimit.java`

Move the ENGAGEMENT_CAP block after WEIGHT_RANGE:
```java
if (Math.abs(w.learnedRaw()) >= learnedCap) return LEARNED_CAP;
double sum = w.base() + w.learned();
if ((w.base() > 0 && sum < 0) || (w.base() < 0 && sum > 0)) return SIGN_CLAMP;
if (Math.abs(sum) > InterestService.MAX_WEIGHT) return WEIGHT_RANGE;
if (engagementCap > 0 && w.engagementRaw() >= engagementCap) return ENGAGEMENT_CAP;
return NONE;
```
Reorder the `<ol>` Javadoc items to match (keep existing brace-per-if style from the file). Enum constant order `NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE, ENGAGEMENT_CAP` stays (append-never-rename).

### `service/FeedbackResult.java` / `service/TopicLearned.java`

Append components at the end, and extend the record Javadoc in the same style:
```java
public record TopicEffect(long topicId, String name, double before, double after, double baseWeight,
                          double learned, LearnedLimit limit, double thumbsLearned, double engagementLearned,
                          boolean engagementReplaced) {}
public record TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight,
                           LearnedLimit limit, double thumbsLearned, double engagementLearned,
                           boolean engagementAtCap) {}
```

### `service/ArticleFeedbackService.java`

`applyAndReport` map (lines ~107-113) currently:
```java
TopicWeight a = after.get(id);
return new TopicEffect(id, a.name(), before.get(id).effective(), a.effective(), a.base(),
        a.learned(), LearnedLimit.of(a, cap, engagementCap), a.thumbsLearned(),
        a.engagementLearned());
```
Add `TopicWeight b = before.get(id);` and `engagementReplaced = differs(b.engagementLearned(), a.engagementLearned()) && differs(b.learnedRaw(), a.learnedRaw())` with a private static `differs(x,y)` = `Math.abs(x - y) > 5e-7` (Pitfall 8).
`learnedTopics()` (lines 76-77) — append `engagementCap > 0 && w.engagementRaw() >= engagementCap` to the `new TopicLearned(...)` call.

### `repository/InterestScoreQueries.java` (Javadoc, lines 182-187)
Replace "so a score change between pages cannot skip rows (WR-02)" with a statement that the literal-tuple compare prevents skips from the cursor row's own change, but an unloaded row whose score rises above the cursor can be skipped until the user refreshes (D-09, IN-04).

## Shared Patterns

### Priority detection
**Source:** `hooks/useFeedback.ts` (`useVoteFeedback`)
**Apply to:** all six engagement hooks
```typescript
import { useMatch } from 'react-router-dom'
const onPriority = useMatch('/priority') !== null
```

### Never touch `['priority']` except via `patchPriorityArticle`
**Source:** `hooks/usePriorityArticles.ts:85-112` doc comment ("The Priority query is patched, never invalidated").
**Apply to:** `engagementReaction.ts` and every hook. Hint via `usePriorityStore.getState().setRankingChanged(true)`.

### Fire-and-forget from onSuccess
Call `void afterEngagement(...)` inside a block body; never return it (the global `MutationCache.onError` in `queryClient.ts:14-18` would toast on rejection and the mutation would stay pending).

### Test wrapper with router
**Source:** `hooks/useFeedback.test.ts:100-112`
**Apply to:** `engagementRefresh.test.ts`, `useEngagement.test.ts`, `ScoreRow.test.tsx`, `usePriorityArticles.test.ts`
```typescript
const wrapper = ({ children }: { children: ReactNode }) =>
  createElement(QueryClientProvider, { client: qc },
    createElement(MemoryRouter, { initialEntries: [path] }, children))
// Only mount usePriorityArticles in Priority tests: even a disabled query creates a ['priority'] entry.
```
Also mock `articlesApi.getById` (useFeedback.test.ts lines 3-12 `vi.mock('../api/articles', ...)` pattern) and use `createQueryClient()` from `../queryClient` so the 30s staleTime is realistic.

### Phase 8 refresh tests to rewrite
`engagementRefresh.test.ts` uses `BY_ID = { queryKey: ['article', 7], exact: true }` and asserts no `'articles'` invalidation — rewrite to assert GET `/api/articles/7`, invalidations of `['interest','learned']` and `['articles']`, and keep the "never `priority`" assertion (SC-2 anchor).

### Backend test for precedence
`ArticleFeedbackServiceTest.java:204-209` `engagementCapOutranksClampAndRange` — rename to `clampAndRangeOutrankEngagementCap` and flip expectations to `WEIGHT_RANGE` / `SIGN_CLAMP` using the same `weight(base, learnedRaw, learned, engagementRaw, engagementLearned)` helper. Constructor call sites to update for appended components: `ArticleControllerTest:442`, `InterestControllerTest:155-156`, `ArticleFeedbackServiceTest:157-158`.

## No Analog Found

None. `engagementReaction.ts` is new but is a lift of the vote's `onSuccess` plus a `qc.query` refetch (no existing `qc.query`/`fetchQuery` usage in the app; follow RESEARCH Pattern 1).

## Metadata

**Analog search scope:** `src/main/frontend/src/{hooks,components,utils}`, `src/main/java/org/bartram/myfeeder/{service,repository}`, `src/test/java/.../service`
**Files scanned:** ~15
**Pattern extraction date:** 2026-09-30
