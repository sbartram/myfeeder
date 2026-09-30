# Phase 10: Explainable Engagement in the UI - Research

**Researched:** 2026-09-30
**Domain:** React 19 + TanStack Query v5 cache choreography (in-place Priority patching), presentation of server-computed learned splits, one small Spring service/enum change
**Confidence:** HIGH (everything this phase touches was read in this session. No new libraries. The one library behavior it relies on, `QueryClient.query`/`fetchQuery`, was checked against the installed 5.103.2 source.)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (settled earlier; do not reopen)
- Phase 6 D-06: Priority is patched in place and never invalidated or refetched while it's being triaged. A vote patches only the voted row and sets `rankingChanged`. The roadmap says to reuse this reaction for engagement.
- Phase 9 D-10/D-15/D-16: the split fields already exist on the wire:
  - `InterestBreakdown.Row` / `TopicContribution` carry `thumbsWeight` + `engagementWeight` = `learnedWeight`.
  - `TopicLearned` / `TopicEffect` carry `thumbsLearned` + `engagementLearned` = `learned`.
  - Phase 10 renders these fields. The client never recomputes a weight.
- Phase 9 D-12: the vote effect's "before" includes the engagement the vote then replaces.
- DTO/enum rule: append, never rename.
- Deferred by the roadmap: ENG-F1 (reading-pane "Opened · Starred" status line). ENG-F6 is narrowed by D-14 below.

#### "Why N?" topic rows (EXPL-01)
- **D-01:** The visible label names each part inline, extending the Phase 5 D-11 form. Example: `Rust  80% × +13.5 (+10 +2.0 votes +1.5 engaged)`. The part words are **"votes"** and **"engaged"**. This replaces the single "learned" wording.
- **D-02:** A part whose value rounds to zero tenths is omitted from the label:
  - `(+10 +1.5 engaged)` or `(+10 +2.0 votes)`
  - the plain Phase 5 row when both round to zero
- **D-03:** The row tooltip always lists every part, zeros included. Example: `Match 80% → counts 60% × +13.5 = +8.1 pts · base +10, votes +2.0, engaged +1.5`.
- **D-04:** "Why N?" stays free of limit notes. It shows no ENGAGEMENT_CAP marker. Limits are worded only in the toast and in Interests.
- The points and total logic are unchanged: rows still sum exactly to the badge (the server's integer points). Only the label and tooltip text change.

#### Post-engagement reaction (LRN-06, SC-2, SC-3)
- **D-05:** The actions that react are open (both Open Original buttons and `o`), star (an unstarred→starred change only), board add (including Read Later and `b`), Raindrop save and Forget engagement. Unstar and board removal need no reaction, because engagement is sticky.
- **D-06:** On Priority, after the action succeeds, refetch the article by id and compare its `interestScore` to the Priority row's.
  - If they differ: patch the row's `interestScore` (via `patchPriorityArticle`) and set `rankingChanged`.
  - If they are equal, nothing lights. This covers repeat opens, unscored articles and articles that already carry a vote.
  - Priority is never invalidated, refetched or reset by an engagement.
- **D-07:** Mirror the vote's refresh set after any reacting action:
  - the exact `['article', id]` query
  - `['interest', 'learned']`
  - `['articles']` (list badges)
  - outside Priority, the other open by-id article queries, the same `['article', n]` predicate the vote uses (it leaves `['article', n, 'extracted']` alone)
- **D-08:** Open stays fire-and-forget: `window.open` runs first and is never delayed, and errors are swallowed with no toast. The reaction runs only after the PUT succeeds.
- **D-09 (IN-04):** Accept that unloaded Priority rows whose score rises above the cursor can be skipped until refresh. The hint already says to refresh. Correct the `InterestScoreQueries.priorityPageAfter` Javadoc, which claims rows can't be skipped, so that it states this limit. No forced reset (that would re-sort mid-triage and violate SC-2).

#### Vote toast and limit notes (SC-4, WR-01, WR-03)
- **D-10 (WR-01):** Change the `LearnedLimit.of` precedence to **LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE**. This supersedes the order in Phase 9 D-11. The binding bounds are judged on base + thumbs + engagement. Update `ArticleFeedbackServiceTest.engagementCapOutranksClampAndRange` to match. — **Reversibility:** reversible — a single method plus its tests.
- **D-11 (WR-03):** When a vote removes this article's engagement share on a topic (before.engagementLearned ≠ after.engagementLearned, and the thumbs part changed), the effect carries an appended marker, either a new `LearnedLimit` value such as `REPLACED_ENGAGEMENT` or a flag on `TopicEffect` (planner's choice).
  - Toast for a vote: `Rust +0.9 (replaces engagement)`.
  - Toast for a removed vote: `Rust −0.9 (engagement restored)`.
- **D-12:** Each topic gets one note. A binding limit (LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE) wins over the replaced-engagement note, which shows only when the limit is otherwise NONE.
- **D-13:** The vote toast never mentions ENGAGEMENT_CAP, and ENGAGEMENT_CAP alone is not a reason to list a topic. The listing filter treats it like NONE, which removes the "+0.0" noise pinned by `feedback.test.ts` `engagementCapWithNoChangeIsStillListed`; update that test.

#### Interests learned line (IN-01)
- **D-14:** Replace "Learned from votes" with a split like the one in "Why N?":
  - `Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5`
  - parts that round to zero are omitted, as in D-02
  - the "No learned adjustment yet" and "updates when you save" variants keep their shape with the new wording
  - This pulls part of ENG-F6 forward. ENG-F6 keeps the contributing-article counts and any richer layout.
- **D-15:** When the reported limit is ENGAGEMENT_CAP, the engaged part reads "at max". Example: `Learned +8.0 (engaged +8.0 at max) · Effective weight +18.0`. The thumbs `(at max)` / `(at min)` and the clamp/range suffixes keep their current wording under the new D-10 precedence.
- Update the tests that pin the old mislabel (`TopicRow.test.tsx` around lines 647–659, `feedback.test.ts` around lines 166–188) rather than keeping them as regression anchors.

### Claude's Discretion
- How the reaction is shared across the five actions, for example one `afterEngagement(qc, id, onPriority)` helper that votes and, later, Phase 11's suggestions query can also use. Also how the "on Priority" context reaches `useOpenOriginal`, `useUpdateArticleState`, the board-add and Raindrop hooks, and Forget.
- Whether D-11 is an enum value or a boolean on `TopicEffect`, and its exact name.
- Whether `TopicLearned` gets an engagement at-cap boolean so D-15's "at max" still shows when a binding limit outranks ENGAGEMENT_CAP. Add it only if cheap; otherwise the binding limit's note is enough.
- Exact spacing and punctuation of labels, within the examples above.

### Deferred Ideas (OUT OF SCOPE)
- ENG-F1: reading-pane engagement status line ("Opened · Starred"). Still deferred.
- ENG-F6 (narrowed): contributing-article counts and a richer votes/engagement layout in Interests. The basic split moved into this phase (D-14).
- A forced Priority reset when paging while the hint is lit (IN-04 alternative). Rejected, because it re-sorts mid-triage.

#### Reviewed Todos (not folded)
- `2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`: matched only on the keyword "raindrop" (score 0.2). It's backend resilience tuning, unrelated to this phase.

### Planning-run decisions (from the orchestrator, this session)
- No UI-SPEC.md for this phase. CONTEXT.md D-01..D-15 plus Claude's Discretion are the UI contract.
- Nyquist validation is enabled: the Validation Architecture section below is the source for VALIDATION.md.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LRN-06 | The Priority order, badge and "Why N?" reflect engagement consistently. Engagement never re-sorts an open Priority list; it sets the "Ranking changed" hint instead. | Pattern 1 (shared `afterEngagement` reaction built on `qc.query({... staleTime: 0})`), Pattern 2 (detecting "on Priority"), Pitfalls 1–5. Backend: Priority rows, `displayScores` and the breakdown all use the same `INTEREST_SCORE` expression, so after a refresh they agree (SC-3). |
| EXPL-01 | "Why N?" shows each topic's effective weight as base + votes + engagement, and its rows still sum exactly to the badge. | Pattern 3 (`TopicLabel` rewrite from `thumbsWeight`/`engagementWeight`, which the server already sends on every TOPIC row). Pitfall 6 (rounding the parts) and Pitfall 7 (the zero-part gate). Points and totals stay untouched. |
| (SC-4, WR-01, WR-03, IN-01, IN-04) | The vote toast accounts for replaced engagement; limit precedence; Interests wording; the Javadoc | Pattern 4 (the `LearnedLimit.of` reorder plus an appended `TopicEffect` boolean), Pattern 5 (`effectNote`/listing filter), Pattern 6 (`LearnedLine`), and the review-disposition bookkeeping |
</phase_requirements>

## Summary

This phase has almost no new mechanics. Phase 9 already serves every number the UI needs:
- `InterestBreakdown.Row.thumbsWeight/engagementWeight` for "Why N?"
- `TopicEffect.thumbsLearned/engagementLearned` for the toast
- `TopicLearned.thumbsLearned/engagementLearned` for Interests

Phase 6 already built the in-place Priority reaction for votes: `patchPriorityArticle`, `usePriorityStore.setRankingChanged` and the invalidation set in `useVoteFeedback.onSuccess`. The work splits in three:
1. Rewrite three pure presentation functions: `TopicLabel`, `effectNote`/`formatVoteToast` and `LearnedLine`.
2. Give five engagement hooks one shared post-success reaction.
3. Make two small backend changes: reorder `LearnedLimit.of`, and append a "replaced engagement" boolean to `TopicEffect`. `TopicLearned` optionally gets an engagement-at-cap boolean too.

The one real engineering risk is the reaction. Unlike a vote, the engagement endpoints return no article: `PUT …/engagement/open` and Raindrop answer 204 or void, board add answers void, and Forget answers 204. So the reaction has to fetch the article by id before it can compare scores. The global QueryClient has `staleTime: 30_000`, so a naive `fetchQuery` returns the cached, pre-engagement article and the comparison never fires. The installed TanStack Query 5.103.2 has the non-deprecated `qc.query(...)`, which merges client defaults. Pass `staleTime: 0` explicitly. It also dedupes onto an in-flight fetch of the same key, which matters because `useUpdateArticleState` already invalidates `['article', id]`.

The second risk is test churn. Adding `useMatch('/priority')` to the engagement hooks, as the vote hook does, makes every test that renders them without a `<MemoryRouter>` throw. Four test files do that today. Phase 8 also pinned "by-id only, never `['articles']`" in `engagementRefresh.test.ts` and `ScoreRow.test.tsx`, and D-07 reverses that deliberately.

**Primary recommendation:** Add one plain function, `afterEngagement(qc, id, onPriority)`, in a new `hooks/engagementReaction.ts`. It never rejects. It runs the D-07 invalidations, then `await qc.query({ queryKey: ['article', id], queryFn: () => articlesApi.getById(id), staleTime: 0 })`, then the D-06 compare, patch and hint. Call it with `void` from the five hooks' success paths, and detect Priority with `useMatch('/priority')` inside each hook. On the backend, reorder `LearnedLimit.of` and append `boolean engagementReplaced` to `TopicEffect`. Word it on the client by `VoteKind`: "removed" gives "engagement restored", anything else gives "replaces engagement".

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Votes/engagement split values | API / Backend (already shipped, Phase 9 SQL) | — | "The client never recomputes a weight" (carried forward). The client only formats. |
| "Why N?" row label and tooltip | Browser / Client (`WhyBreakdown.tsx`) | — | Pure presentation of `InterestBreakdown.Row` fields |
| Limit precedence (`LearnedLimit.of`) | API / Backend (`LearnedLimit.java`) | — | One server-side judgment, consumed by both the toast and Interests |
| "Replaced engagement" detection | API / Backend (`ArticleFeedbackService.applyAndReport`) | Browser (wording only) | Needs before/after `TopicWeight`, which only the server holds inside the vote transaction |
| Post-engagement Priority reaction | Browser / Client (TanStack cache + zustand `priorityStore`) | API (GET `/api/articles/{id}`) | Priority is a client-frozen snapshot, and only the client knows what is loaded |
| Priority skip limitation (IN-04) | API / Backend (Javadoc only) | — | Documentation fix, no behavior change (D-09) |

## Standard Stack

No new dependencies. Everything used is already in the build.

### Core (in use, versions verified this session)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| @tanstack/react-query | 5.103.2 [VERIFIED: node_modules/@tanstack/react-query/package.json] | Cache reads, writes and invalidation; `qc.query` for the by-id refetch | Already the app's server-state layer |
| react-router-dom | ^6.30.6 [VERIFIED: src/main/frontend/package.json] | `useMatch('/priority')` for Priority detection | Same mechanism as `useVoteFeedback` (`hooks/useFeedback.ts:45`) |
| zustand | ^5.0.15 [VERIFIED: package.json] | `usePriorityStore.getState().setRankingChanged(true)` | Existing hint store |
| vitest + @testing-library/react | ^4.1.11 / ^16.3.3 [VERIFIED: package.json] | Hook and component tests | Existing |
| JUnit 5 + Mockito + Testcontainers | BOM-managed | `ArticleFeedbackServiceTest`, `FeedbackApiIntegrationTest` | Existing |

**Installation:** none.

## Package Legitimacy Audit

This phase installs no external packages, so the legitimacy gate has nothing to check.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 user action (on /priority or elsewhere)
   │
   ├─ o / Open Original ──► window.open (sync, first) ──► PUT /engagement/open ─┐ (errors swallowed)
   ├─ Star (starred:true) ─► PATCH /articles/{id} ──────────────────────────────┤
   ├─ b / Board / Read Later ► POST /boards/{b}/articles ───────────────────────┤
   ├─ Raindrop ────────────► POST /articles/{id}/raindrop ──────────────────────┤
   └─ Forget ──────────────► DELETE /articles/{id}/engagement ──────────────────┤
                                                                                 ▼ success only
                                                    afterEngagement(qc, id, onPriority)  [never rejects]
                                                                                 │
             ┌───────────────────────────────────────────────────────────────────┤
             ▼                                                                   ▼
  invalidate ['interest','learned'], ['articles']        qc.query(['article', id], getById, staleTime 0)
  (off Priority: other ['article', n] by-id, not n=id)             │  writes the by-id cache → reading pane
                                                                   │  badge + "Why N?" update in place
                                                                   ▼
                                                   onPriority && Priority row loaded?
                                                     │ no → done
                                                     ▼ yes
                                        fresh.interestScore !== row.interestScore ?
                                          │ no → done (repeat open, unscored, voted article)
                                          ▼ yes
                         patchPriorityArticle(qc, id, {interestScore}) + setRankingChanged(true)
                         (row keeps its index; ['priority'] never invalidated/refetched/reset)
                                                                   │
                           user presses "↻ Ranking changed — refresh" → refreshPriority(qc)
                           → resetQueries(['priority']) + invalidate all by-id → SC-3 agreement
```

### Recommended Project Structure (touched files only)
```
src/main/frontend/src/
├── hooks/engagementReaction.ts        # NEW: afterEngagement(qc, id, onPriority) + priorityRowScore helper
├── hooks/engagementReaction.test.ts   # NEW (or fold into engagementRefresh.test.ts)
├── hooks/useEngagement.ts             # useOpenOriginal, useForgetEngagement → reaction
├── hooks/useArticles.ts               # useUpdateArticleState (starred:true), useSaveToRaindrop → reaction
├── hooks/useBoards.ts                 # useAddArticleToBoard, useReadLater → reaction
├── hooks/useFeedback.ts               # optionally reuse the shared invalidation set (keep behavior identical)
├── components/WhyBreakdown.tsx        # TopicLabel split (D-01..D-03)
├── components/TopicRow.tsx            # LearnedLine split (D-14, D-15)
├── utils/feedback.ts                  # effectNote/formatVoteToast (D-11..D-13)
└── types/index.ts, api/interest.ts    # append TopicEffect.engagementReplaced?, TopicLearned.engagementAtCap?
src/main/java/org/bartram/myfeeder/
├── service/LearnedLimit.java          # D-10 reorder + Javadoc
├── service/FeedbackResult.java        # append boolean to TopicEffect
├── service/ArticleFeedbackService.java# compute the boolean from before/after TopicWeight
├── service/TopicLearned.java          # (optional) append boolean engagementAtCap
└── repository/InterestScoreQueries.java # priorityPageAfter Javadoc only (D-09)
```

### Pattern 1: One shared, never-rejecting post-engagement reaction
**What:** A plain function next to `patchPriorityArticle`/`refreshPriority`. It is not a hook, so it can run after awaits and after navigation.
**When to use:** On the success path of all five D-05 actions. Phase 11 can reuse it later.
**Example:**
```typescript
// Source: pattern derived from hooks/useFeedback.ts:52-75 and hooks/usePriorityArticles.ts:93-112 (read this session);
// qc.query semantics verified in node_modules/@tanstack/query-core/build/modern/queryClient.js:367-375
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import { usePriorityStore } from '../stores/priorityStore'
import { PRIORITY_KEY, patchPriorityArticle } from './usePriorityArticles'
import type { PriorityPage } from '../types'

/** The loaded Priority row's score, or undefined when the row isn't loaded (first occurrence, like dedupeById). */
function priorityRowScore(qc: QueryClient, id: number): number | null | undefined {
  const data = qc.getQueryData<InfiniteData<PriorityPage>>(PRIORITY_KEY)
  for (const page of data?.pages ?? []) {
    const row = page.items.find((a) => a.id === id)
    if (row) return row.interestScore ?? null
  }
  return undefined
}

/** D-06/D-07. Never rejects: the user's action already succeeded, so a failed refetch stays silent. */
export async function afterEngagement(qc: QueryClient, id: number, onPriority: boolean): Promise<void> {
  void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })
  void qc.invalidateQueries({ queryKey: ['articles'] })
  if (!onPriority) {
    void qc.invalidateQueries({
      predicate: (q) => q.queryKey[0] === 'article' && q.queryKey.length === 2 && q.queryKey[1] !== id,
    })
  }
  try {
    // staleTime 0 is required: the client default is 30s, which would return the pre-engagement article.
    const fresh = await qc.query({ queryKey: ['article', id], queryFn: () => articlesApi.getById(id), staleTime: 0 })
    if (!onPriority) return
    const rowScore = priorityRowScore(qc, id)
    const score = fresh.interestScore ?? null
    if (rowScore !== undefined && rowScore !== score) {
      patchPriorityArticle(qc, id, { interestScore: score })
      usePriorityStore.getState().setRankingChanged(true)
    }
  } catch {
    // swallowed (D-08 for opens; for mutations the save itself succeeded)
  }
}
```
The call sites use `void afterEngagement(...)` in `onSuccess`, or in the open's `.then(...)`. Never return its promise from `onSuccess`: returning it keeps the mutation pending (the Forget button stays disabled) until the GET completes. Harmless, but it's a behavior change nobody asked for.

### Pattern 2: Detecting "on Priority" consistently with the vote
**What:** `const onPriority = useMatch('/priority') !== null` inside each of `useOpenOriginal`, `useForgetEngagement`, `useUpdateArticleState`, `useSaveToRaindrop`, `useAddArticleToBoard` and `useReadLater`, exactly as `useVoteFeedback` does (`hooks/useFeedback.ts:45`). Pass it into `afterEngagement` from the mutation variables or the closure.
**Why this over the alternatives:**
- It keeps the one convention CONTEXT names ("The engagement hooks need the same context").
- Every production caller already sits under `BrowserRouter`: ReadingPane, BoardManager (mounted by ReadingPane), ScoreRow and useKeyboardShortcuts (MainLayout).
- The compare in Pattern 1 is self-guarding. After leaving `/priority`, MainLayout's cleanup `qc.removeQueries({ queryKey: PRIORITY_KEY })` (`App.tsx:99-104`) drops the loaded pages. A stale closure captured on Priority therefore finds no row and never lights the hint.

**Cost (must be in the plan):** these tests render the hooks without a router and will throw `useMatch() may be used only in the context of a <Router>` until each is wrapped in `MemoryRouter`:

| Test file | Renders | Router today |
|---|---|---|
| `hooks/engagementRefresh.test.ts` | useSaveToRaindrop, useAddArticleToBoard, useReadLater | none [VERIFIED: file read, lines 14-15] |
| `hooks/useEngagement.test.ts` | useOpenOriginal | none [VERIFIED: lines 13-14] |
| `components/ScoreRow.test.tsx` | ScoreRow → useForgetEngagement | none [VERIFIED: lines 32-37] |
| `hooks/usePriorityArticles.test.ts` | useUpdateArticleState in `renderPriority` | none [VERIFIED: lines 48-63] |

These already have routers: `PriorityList.test.tsx`, `useKeyboardShortcuts.test.ts`, `useFeedback.test.ts` and `FeedbackBar.test.tsx`. `ReadingPane.test.tsx` mocks the hooks.

**Router-free alternative (if the planner prefers less churn):** decide at reaction time with `onPriority = qc.getQueryData(PRIORITY_KEY) !== undefined`. Off Priority the entry is either removed or a disabled one without data (useFeedback.test.ts:109 notes "even a disabled query creates a ['priority'] entry"). This diverges from the vote hook's convention, so the recommendation stays `useMatch`.

### Pattern 3: "Why N?" label from the server's split (EXPL-01)
```typescript
// Source: components/WhyBreakdown.tsx:69-93 (current), rewritten per D-01..D-03
function TopicLabel({ row }: { row: TopicBreakdownRow }) {
  const matchPct = Math.round(row.noul * 100)
  const countsPct = Math.round(row.hinge * 100)
  const votes = row.thumbsWeight ?? 0
  const engaged = row.engagementWeight ?? 0
  const showVotes = Math.round(votes * 10) !== 0
  const showEngaged = Math.round(engaged * 10) !== 0
  const math = `Match ${matchPct}% → counts ${countsPct}% × `
  if (row.baseWeight !== undefined && (showVotes || showEngaged)) {
    const weight = formatSigned(row.weight, 1)
    const parts = [formatSigned(row.baseWeight)]
    if (showVotes) parts.push(`${formatSigned(votes, 1)} votes`)
    if (showEngaged) parts.push(`${formatSigned(engaged, 1)} engaged`)
    return (
      <span className="why-label"
        title={`${math}${weight} = ${formatSigned(row.exact, 1)} pts · base ${formatSigned(row.baseWeight)}, votes ${fmtPart(votes)}, engaged ${fmtPart(engaged)}`}>
        {`${row.name}  ${countsPct}% × ${weight} (${parts.join(' ')})`}
      </span>
    )
  }
  // plain Phase 5 row (see Pitfall 7 on whether the tooltip lists zero parts here)
}
```
`fmtPart` should print `+0.0` for a value that rounds to zero and never print `−0.0`. `formatDelta` in `utils/feedback.ts:49-52` already does exactly that, so reuse it or move it to `utils/interest.ts`. `formatSigned(0, 1)` prints unsigned `0.0`, while `formatSigned(0.04, 1)` prints `+0.0`, which is inconsistent. [VERIFIED: utils/interest.ts:7-12]

### Pattern 4: Backend precedence and the replaced-engagement flag
```java
// Source: 09-REVIEW.md WR-01 reviewer code; LearnedLimit.java:26-41 (current, read this session)
public static LearnedLimit of(TopicWeight w, double learnedCap, double engagementCap) {
    if (Math.abs(w.learnedRaw()) >= learnedCap) return LEARNED_CAP;
    double sum = w.base() + w.learned();
    if ((w.base() > 0 && sum < 0) || (w.base() < 0 && sum > 0)) return SIGN_CLAMP;
    if (Math.abs(sum) > InterestService.MAX_WEIGHT) return WEIGHT_RANGE;
    if (engagementCap > 0 && w.engagementRaw() >= engagementCap) return ENGAGEMENT_CAP;
    return NONE;
}
```
For D-11, use a **boolean appended to `TopicEffect`**, for example `engagementReplaced`, not a new `LearnedLimit` value:
- `LearnedLimit` means "which bound held the value back", and it is shared with `TopicLearned`, which has no before/after, so a replacement value could never apply there.
- D-12 and D-13 already make the client choose one note from limit plus marker. A boolean keeps that choice in one place (`effectNote`), and the ENGAGEMENT_CAP-with-replacement case is handled without a server-side precedence rule.
- The frontend `LearnedLimit` union stays unchanged.

```java
// ArticleFeedbackService.applyAndReport, inside the map (before/after already exist, lines 101-113)
TopicWeight b = before.get(id), a = after.get(id);
boolean engagementReplaced = differs(b.engagementLearned(), a.engagementLearned())
        && differs(b.learnedRaw(), a.learnedRaw());
return new TopicEffect(id, a.name(), b.effective(), a.effective(), a.base(), a.learned(),
        LearnedLimit.of(a, cap, engagementCap), a.thumbsLearned(), a.engagementLearned(), engagementReplaced);
// differs(x, y): Math.abs(x - y) > 5e-7  — values are 6-decimal rounded (Pitfall 8)
```
Use `learnedRaw` (the uncapped thumbs) for "the thumbs part changed" rather than `thumbsLearned`: a vote that lands on an already-capped thumbs sum still counts as a change. Nothing is lost, because LEARNED_CAP wins under D-12 anyway.

`TopicLearned` gets an optional `boolean engagementAtCap = engagementCap > 0 && w.engagementRaw() >= engagementCap`. It is one expression in `learnedTopics()` plus two test constructor call sites (`InterestControllerTest:155-156`), so it counts as "cheap" under the discretion rule, and it lets D-15's "at max" survive a binding limit.

### Pattern 5: Toast wording (D-11..D-13)
```typescript
// utils/feedback.ts — effectNote needs the VoteKind now; listing filter ignores ENGAGEMENT_CAP
function effectNote(e: TopicEffect, kind: VoteKind): string {
  switch (e.limit) {
    case 'LEARNED_CAP': /* unchanged */
    case 'SIGN_CLAMP':  /* unchanged */
    case 'WEIGHT_RANGE': /* unchanged */
    default: // NONE and ENGAGEMENT_CAP (D-13: never worded)
      if (e.engagementReplaced) return kind === 'removed' ? ' (engagement restored)' : ' (replaces engagement)'
      return e.baseWeight < 0 ? ` (now ${formatSigned(e.after, 1)})` : ''
  }
}
const listable = (e: TopicEffect, d: number) =>
  Math.round(d * 10) !== 0 || (e.limit !== 'NONE' && e.limit !== 'ENGAGEMENT_CAP') || e.engagementReplaced === true
```
Keep the replaced note ahead of the negative-base "(now …)" note, because D-12 allows one note per topic. A negative-base topic never has engagement (the SQL zeroes it), so the two cannot collide in practice.

### Pattern 6: Interests learned line (D-14, D-15)
Recommended strings. Punctuation is Claude's discretion within the examples:
- `Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5`
- `Learned +8.0 (engaged +8.0 at max) · Effective weight +28.0`
- thumbs cap: `Learned +20.0 (votes +20.0 at max) · Effective weight +40.0` and `(votes −20.0 at min)`. The "(at max)/(at min)" words stay but move inside the votes part so they read in parallel with D-15. See Assumption A2.
- clamp and range suffixes are unchanged: `… · Effective weight 0.0 (can't cross 0)` and `(at the +50 limit)`
- base edited: `Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight updates when you save`
- `No learned adjustment yet · Effective weight +20` is unchanged. Gate it on `Math.round(learned.learned * 10) === 0`, as now.
- The engaged "at max" condition is `learned.engagementAtCap ?? learned.limit === 'ENGAGEMENT_CAP'`.

### Anti-Patterns to Avoid
- **`invalidateQueries({ queryKey: ['priority'] })`, `resetQueries` or `refetchQueries` on Priority from any engagement path:** this re-sorts mid-triage and violates SC-2. Only `patchPriorityArticle` is allowed.
- **Patching the Priority row from the mutation response:** the engagement endpoints return no article (204 or void). Patch only from the by-id refetch.
- **`qc.fetchQuery`/`qc.query` without `staleTime: 0`:** it silently returns the 30s-fresh cached article, so the hint never lights.
- **Letting the reaction's rejection escape `onSuccess`:** the global `MutationCache.onError` would then toast "An error occurred" for a save that succeeded (`queryClient.ts:14-18`).
- **Computing the parts on the client (for example `learnedWeight − thumbsWeight`):** this violates the carried-forward "client never recomputes a weight". Render the server's `thumbsWeight`/`engagementWeight`.
- **Unconditional hint on engagement:** D-06 requires the compare. Repeat opens must not light it.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Fetching the fresh by-id article and deduping with an in-flight refetch | a manual `fetch` + `setQueryData` | `qc.query({ ..., staleTime: 0 })` | It dedupes onto a running fetch of the same key (`query.js:298-303`) and writes the cache, so the reading pane updates. Retry defaults to false. |
| Patching a Priority row | a manual `setQueryData` over pages | `patchPriorityArticle` (`usePriorityArticles.ts:93`) | It already guards against creating a cache entry, keeps the row index and patches duplicate occurrences |
| The ranking hint | a new store flag | `usePriorityStore.getState().setRankingChanged(true)` | `refreshPriority` already clears it |
| Signed tenths with no `−0.0` | a new formatter | `formatDelta` (`utils/feedback.ts:49`) / `formatSigned` (`utils/interest.ts:7`) | Existing, tested |
| Limit judgment on the client | a TS port of `LearnedLimit.of` | the server's `limit` + the new booleans | Single source of truth |

**Key insight:** every number is already on the wire, and every cache primitive is already written. The phase is wiring and wording. Anything that looks like new arithmetic or a new cache mechanism is a mistake.

## Common Pitfalls

### Pitfall 1: The by-id refetch returns stale cached data
**What goes wrong:** the compare sees the old score, so the hint never lights and the Priority badge doesn't move.
**Why it happens:** `createQueryClient()` sets `queries: { staleTime: 30_000 }` (`queryClient.ts:10-12`). `fetchQuery`/`query` merge client defaults and return `query.state.data` when it isn't stale by that time. [VERIFIED: queryClient.js:367-375, 379-384]
**How to avoid:** pass `staleTime: 0`.
**Warning signs:** a test that opens an article already in cache sees no GET `/api/articles/{id}` after the PUT.

### Pitfall 2: Tests without a Router throw after adding `useMatch`
**What goes wrong:** four test files fail at render.
**How to avoid:** wrap them in `MemoryRouter` in the same task that adds `useMatch` (see the table in Pattern 2). Use `initialEntries: ['/priority']` for the Priority cases.

### Pitfall 3: Phase 8 tests pin the opposite behavior
`engagementRefresh.test.ts` `noSaveInvalidatesPriority` asserts `firstKeys()` does not contain `'articles'` and that every `'article'` invalidation equals `{queryKey: ['article', 7], exact: true}`. `raindropSaveRefreshesOnlyTheByIdArticle` asserts `invalidateSpy` was called exactly once. `ScoreRow.test.tsx` `forgetDeletesAndRefreshesOnlyThisArticle` asserts the exact by-id invalidation. With `qc.query` there is no by-id *invalidation* call at all; the refresh is a GET. **Rewrite these tests** to assert:
- the GET `/api/articles/7` was made
- `['interest','learned']` and `['articles']` were invalidated
- `'priority'` never appears in any invalidate, reset or refetch call

Keep the "never priority" assertion; it is the SC-2 regression anchor.

### Pitfall 4: A stale by-id response overwrites a newer vote patch
**What goes wrong:** the user presses `o` and then `u` quickly. The vote's `onSuccess` patches the Priority row with the post-vote score. Then the open's GET, issued before the vote's write, resolves with the pre-vote score, and the compare "fixes" the row back to the older score. The same race can briefly overwrite the pressed vote state in `['article', id]` (a pre-existing race; Phase 8's by-id invalidation had it too).
**How to avoid (cheap):** in `afterEngagement`, skip the compare and patch when `qc.isMutating({ mutationKey: ['feedback'], predicate: (m) => (m.state.variables as { id?: number } | undefined)?.id === id }) > 0`, because the vote's own reaction will patch the row. The hint is already lit by the vote in that case.
**Warning signs:** a Priority badge that disagrees with the reading pane until refresh after rapid `o`, `u`.

### Pitfall 5: Returning the reaction promise from `onSuccess`
TanStack awaits a promise returned from `onSuccess` before the mutation settles. If the promise rejects, the mutation errors and the global toast fires. Always `void afterEngagement(...)`, and keep the helper's own `try/catch`.

### Pitfall 6: The displayed parts may not add up to the displayed weight
**What goes wrong:** `weight 21.5 = base 20 + votes 0.75 + engaged 0.75` renders as `+21.5 (+20 +0.8 votes +0.8 engaged)`, where the parts sum to 21.6.
**Why it happens:** each part is rounded to tenths independently. The base is an integer, so the old single `learned` part always added up exactly, but two rounded parts can be off by ±0.1. The server values are exact to 6 decimals; only display rounding causes this.
**How to avoid:** accept it, since the tooltip carries the same rounded numbers, or confirm with the user (Open Question 1). Don't "fix" it by deriving one part from the others without that confirmation, because that is the client recomputing a weight.

### Pitfall 7: The zero gates for parts and totals disagree
Four cases to get right:
- **Why N?:** today the row shows the split when `learnedWeight` rounds non-zero. Under D-02 the gate must be "votes OR engaged rounds non-zero". With 0.04 + 0.04, `learnedWeight` rounds to +0.1 while both parts round to zero, which gives the plain row, per D-02.
- **Interests:** `learned` 0.08 rounds to +0.1 but both parts round to 0.0. Render `Learned +0.1 · Effective weight …` with **no empty `()`**.
- **Plain-row tooltip:** D-03 says "always lists every part, zeros included". Whether the plain Phase 5 row's tooltip should also gain `· base +20, votes +0.0, engaged +0.0` is ambiguous (Assumption A3). The recommendation is yes, whenever `baseWeight` is present.
- **Optional fields:** `thumbsWeight`, `engagementWeight`, `thumbsLearned` and `engagementLearned` are optional in the TS types (`types/index.ts:78-80,112-115`; `api/interest.ts:32-35`). Default them with `?? 0` so older fixtures still type-check.

### Pitfall 8: Double equality on the before/after TopicWeight
The values are `ROUND(…, 6)` float8 read into Java `double`. Postgres float8 `SUM` is not order-deterministic under parallel aggregation, so an unchanged topic could in principle round differently between the two reads. Compare with `Math.abs(x − y) > 5e-7`, since real changes are ≥ 1e-6.

### Pitfall 9: A narrowed 👎 removes engagement on topics the vote didn't pick
`engaged` excludes any article with an `article_feedback` row (`InterestScoreQueries.java:128`), so a narrowed 👎 drops this article's engagement from **every** matched topic. On an unpicked topic the thumbs part is unchanged, so the D-11 condition ("and the thumbs part changed") leaves no marker there. That topic still moves by −(engagement share) and is listed (for example `Go −0.5`) with no note. See Open Question 2.

### Pitfall 10: Star goes through the shared state mutation
`useUpdateArticleState` also carries `read` toggles and auto-mark-read. React only when `variables.state.starred === true`. Unstarring records nothing (D-05), and read changes must not trigger a GET. Starring an already-starred article records nothing server-side (`EngagementApiIntegrationTest.starringAStarredArticleRecordsNothing`), and the D-06 compare then finds no change.

## Code Examples

### Wiring the open (D-08 preserved)
```typescript
// Source: hooks/useEngagement.ts:16-32 (current), extended
export function useOpenOriginal(): (article: Pick<Article, 'id' | 'url'>) => void {
  const qc = useQueryClient()
  const onPriority = useMatch('/priority') !== null
  return useCallback((article) => {
    if (!article.url?.trim()) return
    window.open(article.url, '_blank', 'noopener')          // first, synchronous, never delayed
    void Promise.resolve()
      .then(() => articlesApi.recordOpen(article.id))
      .then(() => afterEngagement(qc, article.id, onPriority)) // only after the PUT succeeds
      .catch(() => {})
  }, [qc, onPriority])
}
```

### Star
```typescript
// Source: hooks/useArticles.ts:48-61 (current), extended
onSuccess: (_data, variables) => {
  /* existing invalidations + patchPriorityArticle(qc, variables.id, variables.state) unchanged */
  if (variables.state.starred === true) void afterEngagement(qc, variables.id, onPriority)
},
```

### D-10 test update (replace `engagementCapOutranksClampAndRange`)
```java
@Test
void clampAndRangeOutrankEngagementCap() {
    // 45 + 5 + 8 = 58 → WEIGHT_RANGE even with engagement at its cap
    assertThat(LearnedLimit.of(weight(45, 5, 5, 10, 8), 20, 8)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
    // 10 − 19 + 8 = −1 → SIGN_CLAMP
    assertThat(LearnedLimit.of(weight(10, -19, -19, 8, 8), 20, 8)).isEqualTo(LearnedLimit.SIGN_CLAMP);
}
```
`learnedCapOutranksEngagementCap` and `engagementAtExactlyItsCapIsEngagementCap` (base 20, sum 28) remain valid unchanged. `FeedbackApiIntegrationTest.learnedEndpointReportsTheEngagementCap` (20 + 8 = 28) still expects `ENGAGEMENT_CAP`. [VERIFIED: ArticleFeedbackServiceTest.java:192-209; FeedbackApiIntegrationTest.java:350-362]

## Verified in-repo values (verbatim)

- `LearnedLimit.java:24`: `NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE, ENGAGEMENT_CAP;` [VERIFIED: src/main/java/org/bartram/myfeeder/service/LearnedLimit.java:24]
- `types/index.ts:59`: `export type LearnedLimit = 'NONE' | 'LEARNED_CAP' | 'SIGN_CLAMP' | 'WEIGHT_RANGE' | 'ENGAGEMENT_CAP'` [VERIFIED: src/main/frontend/src/types/index.ts:59]
- `FeedbackResult.java:25-26`: `public record TopicEffect(long topicId, String name, double before, double after, double baseWeight, double learned, LearnedLimit limit, double thumbsLearned, double engagementLearned) {}` [VERIFIED]
- `TopicLearned.java:14-15`: `public record TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight, LearnedLimit limit, double thumbsLearned, double engagementLearned) {}` [VERIFIED]
- `InterestScoreQueries.java:160-162`: `public record TopicWeight(long topicId, String name, double base, double learnedRaw, double learned, double effective, double thumbsLearned, double engagementRaw, double engagementLearned, double thumbsEffective) {}` [VERIFIED]
- `InterestBreakdown.java:29-31` Row: `… Double baseWeight, Double learnedWeight, Double thumbsWeight, Double engagementWeight` with `@JsonInclude(JsonInclude.Include.NON_NULL)` [VERIFIED]
- `usePriorityArticles.ts:11`: `export const PRIORITY_KEY = ['priority'] as const` [VERIFIED]
- `useFeedback.ts:63-64`: `void qc.invalidateQueries({ queryKey: ['interest', 'learned'] })` / `void qc.invalidateQueries({ queryKey: ['articles'] })` [VERIFIED]
- `useFeedback.ts:71-73` other-by-id predicate: `q.queryKey[0] === 'article' && q.queryKey.length === 2 && q.queryKey[1] !== v.id` [VERIFIED]
- `PriorityList.tsx:156` hint text: `'↻ Ranking changed — refresh'` [VERIFIED]
- `queryClient.ts:11`: `queries: { staleTime: 30_000, retry: 1 },` [VERIFIED]
- `InterestScoreQueries.java:182-188`: the Javadoc sentence to correct: "so a score change between pages cannot skip rows (WR-02)" [VERIFIED]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `queryClient.fetchQuery(options)` | `queryClient.query(options)` (fetchQuery is `@deprecated`) | Present in installed 5.103.2 [VERIFIED: queryClient.js:367-381] | Use `qc.query`. Behavior is otherwise identical: defaults merged, `retry: false` by default, dedupes in-flight. |
| Phase 8 D-06: engagement refreshes only `['article', id]` | Phase 10 D-07: the full vote refresh set + the Priority compare and patch | This phase | Rewrite the Phase 8 refresh tests (Pitfall 3) |
| Phase 9 D-11 precedence (ENGAGEMENT_CAP second) | D-10: ENGAGEMENT_CAP after the binding bounds | This phase | Toast/Interests regain the clamp/range notes; CLAUDE.md text must change |

**Deprecated/outdated:**
- The "Learned from votes" wording (`TopicRow.tsx:236,251`) is a mislabel since Phase 9 (IN-01). This phase replaces it.
- The CLAUDE.md sentence under "Engagement learning": "`LearnedLimit.of(w, learnedCap, engagementCap)` returns the first match of LEARNED_CAP → ENGAGEMENT_CAP (only when cap > 0) → SIGN_CLAMP/WEIGHT_RANGE … The UI falls through to its default wording for ENGAGEMENT_CAP until Phase 10 words it." Rewrite it to the D-10 order, and state that the toast never words ENGAGEMENT_CAP while Interests reads "at max".

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A boolean `engagementReplaced` on `TopicEffect` (not a `LearnedLimit` value), worded by `VoteKind` ('removed' → "engagement restored") | Pattern 4/5 | Low: CONTEXT leaves this to the planner. The enum route also works but needs server-side D-12 precedence and widens the union for TopicLearned, where it can never apply. |
| A2 | The thumbs "(at max)/(at min)" words move inside the votes part: `(votes +20.0 at max)` | Pattern 6 | Low: wording only. D-15 says those words "keep their current wording", and placement is discretion. The alternative is `Learned +20.0 (at max) (votes +20.0)`. |
| A3 | D-03's "always lists every part" also applies to the plain Phase 5 row's tooltip whenever `baseWeight` is present | Pitfall 7 | Low: tooltip text only. The existing test `topicRowTitleShowsTheMath` would change. |
| A4 | Adding `engagementAtCap` to `TopicLearned` counts as "cheap" | Pattern 4 | Low: one record component + one expression + two test constructors |
| A5 | Skipping the engagement compare while a vote on the same id is pending (Pitfall 4) is acceptable behavior | Pitfall 4 | Low: the vote patches the row and lights the hint itself |

## Open Questions (RESOLVED)

Resolved at plan-phase (2026-09-30) by adopting each recommendation below; the plans record them as assumptions.

1. **Should the label's parts be forced to add up visually (Pitfall 6)?**
   - What we know: independent tenths rounding can make `base + votes + engaged` differ from the printed weight by 0.1. The carried-forward rule forbids client weight recomputation.
   - Recommendation: accept the mismatch (the server values are exact, and the tooltip shows the same rounding). Raise it at plan-check. If the user objects, the fix is presentation-only: print engaged as `round1(weight) − base − round1(votes)`. That needs explicit user approval, because it touches the "never recomputes" rule.
   - **RESOLVED:** mismatch accepted; no client recomputation (10-03).
2. **Narrowed 👎 on an engaged article: should unpicked topics get the replaced-engagement note (Pitfall 9)?**
   - What we know: D-11's condition requires "the thumbs part changed", so unpicked topics move by the engagement share with no note.
   - Recommendation: keep D-11 exactly as written (locked), and add a test that pins the behavior so it is deliberate. If the user wants the note there, drop the "thumbs changed" conjunct. The wording "(replaces engagement)" is still accurate.
   - **RESOLVED:** D-11 kept as written; pinned by tests in 10-02.
3. **Should the vote hook be refactored onto the shared helper?**
   - Recommendation: share only the invalidation set (learned, articles, other by-ids off Priority) through a small exported function, and keep the vote's unconditional patch/hint and toast. The existing `useFeedback.test.ts` cases must stay green unchanged.
   - **RESOLVED:** only the invalidation set is shared (10-01 Task 3).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | backend tests | ✓ | 25.0.4 | — |
| Docker | Testcontainers (`FeedbackApiIntegrationTest`) | ✓ | 29.8.1 | — |
| Node / npm | Vitest, `tsc -b` | ✓ | 26.10.0 / 11.19.1 | — |

No missing dependencies. A sample Vitest run (`feedback.test.ts` + `engagementRefresh.test.ts`) finished in 1.2 s.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4 + RTL (frontend); JUnit 5 + Mockito + Testcontainers via Gradle (backend) |
| Config file | `src/main/frontend/vitest.config.ts`, `build.gradle.kts` |
| Quick run command | `cd src/main/frontend && npx vitest run <touched test files> && npx tsc -b`; backend: `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.service.ArticleFeedbackServiceTest"` |
| Full suite command | `./gradlew test -x npmBuild -x npmInstall` and `cd src/main/frontend && npx tsc -b && npx vitest run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EXPL-01 / SC-1 | Label `(+10 +2.0 votes +1.5 engaged)`, zero parts omitted, plain row when both zero, tooltip lists all parts, rows still sum to the badge | unit (component) | `npx vitest run src/components/WhyBreakdown.test.tsx` | ✅ (update the `learned part (D-11)` describe block, lines 204-285) |
| LRN-06 / SC-2 | Each of open, star (starred:true only), board add, Read Later, Raindrop and Forget on `/priority`: GET by id, patch the row's score only when it differs, set `rankingChanged`, never invalidate/reset/refetch `['priority']`; the row keeps its index | unit (hook) | `npx vitest run src/hooks/engagementRefresh.test.ts src/hooks/useEngagement.test.ts` | ✅ rewrite + ❌ new Priority cases |
| LRN-06 / SC-2 | Equal score: no patch, no hint (repeat open, unscored, voted article) | unit (hook) | same | ❌ Wave 0 |
| LRN-06 / SC-2 | Open: `window.open` before the PUT; a failed PUT gives no reaction and no toast; a failed GET gives no toast | unit (hook) | `npx vitest run src/hooks/useEngagement.test.ts` | ✅ extend |
| LRN-06 / D-07 | Off Priority: learned, articles and other by-id (not `['article', n, 'extracted']`) invalidated | unit (hook) | `npx vitest run src/hooks/engagementRefresh.test.ts` | ❌ Wave 0 |
| LRN-06 / SC-2 | Forget from ScoreRow reacts the same way; unstar and board removal do not | unit | `npx vitest run src/components/ScoreRow.test.tsx src/hooks/usePriorityArticles.test.ts` | ✅ update |
| LRN-06 / SC-2 | PriorityList integration: after an engagement the row order is unchanged and the button reads "↻ Ranking changed — refresh" | component | `npx vitest run src/components/PriorityList.test.tsx` | ✅ add a case |
| LRN-06 / SC-3 | After engagement, the Priority page score equals by-id `interestScore` equals `interestBreakdown.display` for the engaged article | integration (Testcontainers) | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.controller.PriorityApiIntegrationTest"` | ❌ Wave 0 (one new test method) |
| SC-4 / WR-03 | A vote on an engaged article gives an effect with `engagementReplaced=true` (20.9 → 21.8); removal gives true (21.8 → 20.9); a vote with no engagement gives false; flip up→down gives false | unit + integration | `--tests "…ArticleFeedbackServiceTest"` and `--tests "…FeedbackApiIntegrationTest"` | ✅ extend `voteEffectBeforeIncludesTheEngagementItReplaces` |
| SC-4 / D-11..D-13 | Toast: `Rust +0.9 (replaces engagement)`, `Vote removed · Rust −0.9 (engagement restored)`; a binding limit wins; ENGAGEMENT_CAP alone isn't listed and is never worded | unit | `npx vitest run src/utils/feedback.test.ts` | ✅ update `engagementCapFallsThroughToNoNote`, `engagementCapWithNoChangeIsStillListed` |
| WR-01 / D-10 | Precedence LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE | unit | `--tests "…ArticleFeedbackServiceTest"` | ✅ replace `engagementCapOutranksClampAndRange` |
| IN-01 / D-14, D-15 | Interests line variants (split, omitted zeros, at max, base edited, none yet, clamp/range) | unit (component) | `npx vitest run src/components/TopicRow.test.tsx` | ✅ update the `learned line` block (~lines 594-676) |
| Serialization | `TopicEffect.engagementReplaced` and `TopicLearned.engagementAtCap` are on the JSON | controller slice | `--tests "…ArticleControllerTest" --tests "…InterestControllerTest"` | ✅ update constructors (ArticleControllerTest:442, InterestControllerTest:155-156, ArticleFeedbackServiceTest:157-158) |
| IN-04 / D-09 | Javadoc only | manual review | — | n/a |

### Sampling Rate
- **Per task commit:** the touched Vitest files + `npx tsc -b` (frontend tasks), or the touched JUnit class (backend tasks)
- **Per wave merge:** full frontend suite + `./gradlew test -x npmBuild -x npmInstall`
- **Phase gate:** full suite green before `/gsd-verify-work`. Manual UAT on `bootTestRun` + Vite: on Priority, open, star, board, Raindrop (if configured) and Forget each light the hint without reordering, and a refresh then agrees everywhere.

### Wave 0 Gaps
- [ ] Add `MemoryRouter` wrappers to `engagementRefresh.test.ts`, `useEngagement.test.ts`, `ScoreRow.test.tsx` and `usePriorityArticles.test.ts` in the same task that adds `useMatch` to the hooks
- [ ] New Priority-reaction cases (differ → patch + hint; equal → nothing; failure → silent; never `['priority']`) for all five actions
- [ ] `PriorityApiIntegrationTest`: one SC-3 agreement test with an `article_engagement` row
- [ ] `ArticleFeedbackServiceTest`: `engagementReplaced` true/false cases (mocked before/after `TopicWeight`), including the narrowed-unpicked case that pins Open Question 2

## Security Domain

`security_enforcement` is enabled (ASVS L1). This phase adds no endpoints, no auth changes, no persistence and no new input. The backend change is an appended response field plus an enum-precedence reorder.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — (single-user app, unchanged) |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | no new input | Existing endpoints and validation unchanged |
| V6 Cryptography | no | — |
| V7 Error handling/logging | yes (minor) | The reaction swallows errors silently. Toasts use fixed text only and never echo server messages (existing vote-toast rule). |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XSS through topic names in new label/toast strings | Tampering | Render as React text children and `title` attributes (auto-escaped). Never `dangerouslySetInnerHTML`. Topic names are user-authored and already rendered the same way. |
| Request amplification (extra GET per engagement) | DoS (self) | One GET per successful action, deduped with an in-flight fetch. Opens are user-paced. No polling added. |
| Cross-site triggering of engagement | Spoofing | Unchanged: PUT/DELETE are never CORS-simple requests (Phase 8 research) |

## Project Constraints (from CLAUDE.md)

- Type-check with `npx tsc -b` from `src/main/frontend/` (plain `tsc --noEmit` passes falsely).
- Frontend tests: Vitest + RTL. A `vi.mock` of `preferencesStore` is full-replacement. Prefer `importOriginal` spreads in new mocks.
- Priority is never invalidated or refetched mid-triage. Engagement paths must never touch `['priority']` except through `patchPriorityArticle`.
- Opens: `window.open` first, then a fire-and-forget PUT whose errors are swallowed (CLAUDE.md § Engagement capture).
- STAR, BOARD and RAINDROP capture stays server-side; those services stay free of transaction boundaries. This phase changes no backend capture path.
- DTOs and enums: append fields, never rename (the `TopicEffect`/`TopicLearned` record components go at the end).
- Jackson 3: annotations stay `com.fasterxml.jackson.annotation.*`. Records serialize components by name.
- Test patterns: Mockito unit tests (`@ExtendWith(MockitoExtension.class)`), `@WebMvcTest` + `@MockitoBean`, `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the test shell.
- Update CLAUDE.md § Interest Ranking → Engagement learning (the `LearnedLimit.of` order sentence and the "until Phase 10" sentence). Also add one line under § Engagement capture describing the post-engagement reaction (by-id refetch + Priority compare/patch/hint, never `['priority']`), since that section names `useOpenOriginal`/`useForgetEngagement` but not their refresh behavior.
- `.claude/CLAUDE.md`: when a plan fixes a tracked item, mark it resolved in the same commit. Set 09-REVIEW-DISPOSITION.md WR-01, WR-03, IN-01 and IN-04 to `fixed` in the commits that fix each one, and update the STATE.md blocker line for Phase 9 WR-01/WR-03.
- Git: a feature branch (`gsd/phase-10-…`), `--no-ff` merge to main, no release (none is scoped).
- Do not hand-edit generated OpenWiki pages.

## Sources

### Primary (HIGH confidence)
- Codebase, read this session:
  - frontend: `hooks/useFeedback.ts`, `hooks/useEngagement.ts`, `hooks/usePriorityArticles.ts`, `hooks/useArticles.ts`, `hooks/useBoards.ts`, `stores/priorityStore.ts`, `components/WhyBreakdown.tsx`, `components/TopicRow.tsx` (LearnedLine), `components/ScoreRow.tsx`, `utils/feedback.ts`, `utils/interest.ts`, `types/index.ts`, `api/articles.ts`, `api/interest.ts`, `App.tsx`, `queryClient.ts`
  - backend: `LearnedLimit.java`, `TopicLearned.java`, `FeedbackResult.java`, `ArticleFeedbackService.java`, `InterestScoreQueries.java` (LEARNED_CTE, TopicWeight, priorityPageAfter), `InterestBreakdown.java`
  - tests: `engagementRefresh.test.ts`, `useFeedback.test.ts`, `TopicRow.test.tsx`, `feedback.test.ts`, `ArticleFeedbackServiceTest.java`, `FeedbackApiIntegrationTest.java`
- Installed TanStack Query core 5.103.2 source: `queryClient.js:367-384` (`query`/`fetchQuery`), `query.js:298-303` (in-flight dedupe)
- Planning: 10-CONTEXT.md, 09-CONTEXT.md, 09-REVIEW.md, 09-REVIEW-DISPOSITION.md, 08-CONTEXT.md, REQUIREMENTS.md, STATE.md, CLAUDE.md

### Secondary (MEDIUM confidence)
- Context7 `/tanstack/query`: `QueryClient.query()` replaces the deprecated `fetchQuery`, has retries off by default and returns cached data when not stale per `staleTime`. Consistent with the installed source.

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. No new deps; versions read from package.json and node_modules.
- Architecture: HIGH. Every seam was read. The reaction mirrors the shipped vote reaction.
- Pitfalls: HIGH for 1–5, 7, 10 (verified against code and library source). MEDIUM for 8 (float SUM determinism is a defensive measure). HIGH for 6 and 9 (arithmetic and SQL read).

**Research date:** 2026-09-30
**Valid until:** 2026-10-30 (stable, in-repo scope)
