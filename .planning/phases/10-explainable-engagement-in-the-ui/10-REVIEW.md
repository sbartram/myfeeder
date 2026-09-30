---
phase: 10-explainable-engagement-in-the-ui
reviewed: 2026-09-30T17:05:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - CLAUDE.md
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/PriorityList.test.tsx
  - src/main/frontend/src/components/ScoreRow.test.tsx
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/components/TopicRow.tsx
  - src/main/frontend/src/components/WhyBreakdown.test.tsx
  - src/main/frontend/src/components/WhyBreakdown.tsx
  - src/main/frontend/src/hooks/engagementReaction.test.ts
  - src/main/frontend/src/hooks/engagementReaction.ts
  - src/main/frontend/src/hooks/engagementRefresh.test.ts
  - src/main/frontend/src/hooks/useArticles.ts
  - src/main/frontend/src/hooks/useBoards.ts
  - src/main/frontend/src/hooks/useEngagement.test.ts
  - src/main/frontend/src/hooks/useEngagement.ts
  - src/main/frontend/src/hooks/useFeedback.test.ts
  - src/main/frontend/src/hooks/useFeedback.ts
  - src/main/frontend/src/hooks/usePriorityArticles.test.ts
  - src/main/frontend/src/types/index.ts
  - src/main/frontend/src/utils/feedback.test.ts
  - src/main/frontend/src/utils/feedback.ts
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java
  - src/main/java/org/bartram/myfeeder/service/FeedbackResult.java
  - src/main/java/org/bartram/myfeeder/service/LearnedLimit.java
  - src/main/java/org/bartram/myfeeder/service/TopicLearned.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/FeedbackApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-09-30T17:05:00Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

I reviewed the Phase 10 diff (`53d369e^..HEAD`) across all 32 files. It covers the shared `afterEngagement` reaction and its five callers, the votes/engaged split in "Why N?", the toast and the Interests learned line, the `LearnedLimit` precedence change (WR-01), the `engagementReplaced` flag (WR-03), `engagementAtCap`, and the IN-04 Javadoc. `npx tsc -b` is clean, all 383 frontend tests pass, and the targeted Java unit and controller tests pass.

The D-10 precedence, the D-13 toast filter, and the D-14/D-15 learned line all match CONTEXT.md. The "never invalidate `['priority']`" rule holds on every path I traced. I found no security issues and no blockers.

The four warnings:
- **WR-01:** the reaction's forced refetch can race a thumbs vote on the same article.
- **WR-02:** a narrowed 👎 lists the topics the user did not pick, with an unexplained drop.
- **WR-03:** the 6-decimal "noise tolerance" cannot absorb the noise it names, and its test pins an input that cannot occur.
- **WR-04:** the Interests line hides a non-zero split whenever the votes and engaged parts cancel out.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: The engagement refetch can overwrite a newer vote in the by-id cache and on the Priority row

**File:** `src/main/frontend/src/hooks/engagementReaction.ts:58-76` (interacts with `src/main/frontend/src/hooks/useFeedback.ts:81-108`)

**Issue:** `afterEngagement` forces `qc.query({ queryKey: ['article', id], staleTime: 0 })` and only then checks for a pending vote (`isMutating(...) > 0`). The vote hook never cancels in-flight `['article', id]` fetches: `press`/`narrow` call `writeIntent` and `mutate` with no `cancelQueries`. Keyboard triage makes two interleavings likely, for example `s` (star) then `u` within one GET round trip:

1. **The GET resolves while the vote is pending.** `qc.query` writes the pre-vote article into `['article', id]`, which wipes the `writeIntent` pressed state. The thumbs button flickers back to the old vote. A second `u`/`d` in that window has `press()` compute `nextVote` from the stale cache (line 83), so a toggle intended as a removal re-sends the old vote instead.
2. **The GET was served before the vote committed but resolves after the vote's `onSuccess`.** `votePending` is now 0, so the stale GET response:
   - overwrites the vote's `setQueryData` (the reading pane shows the vote unpressed and the old badge);
   - makes `priorityRowScore` differ from the pre-vote score, so the code calls `patchPriorityArticle` with the stale score, undoing the vote's own row patch.

The "research Pitfall 4" guard covers only the case where the vote is still in flight at compare time. Phase 8's `invalidateQueries` carried part of this hazard, but Phase 10 always forces the fetch (`staleTime: 0`, also for inactive queries) and adds the Priority row patch. The stale row patch is new.

**Fix:** Cancel the by-id fetch when a vote starts (the standard optimistic-update pattern), and make the Priority compare ignore a GET that a vote overtook:
```ts
// useFeedback.ts, in press() and narrow(), before writeIntent:
void qc.cancelQueries({ queryKey: ['article', article.id], exact: true })

// engagementReaction.ts
const startedAt = Date.now()
const fresh = await qc.query({ ... })
if (!onPriority) return
const voteSince = qc.getMutationCache().findAll({ mutationKey: ['feedback'] }).some(
  (m) => (m.state.variables as { id?: number } | undefined)?.id === id &&
         (m.state.status === 'pending' || m.state.submittedAt >= startedAt),
)
if (voteSince) return
```
`afterEngagement`'s `catch` already swallows the resulting `CancelledError`. Add a test where the vote resolves before the reaction's GET does.

### WR-02: A narrowed 👎 lists unpicked topics with an unexplained drop

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java:119-120`, `src/main/frontend/src/utils/feedback.ts:91-95, 118-123`

**Issue:** Once an article has any vote row, the `engaged` CTE drops that article from engagement on every topic. A 👎 narrowed to Rust therefore also removes the article's engagement share from Go. Go's `learnedRaw` does not change, so `engagementReplaced` is false. Go's `after − before` still moves by −0.2, so the toast lists it with no note.

`FeedbackApiIntegrationTest.narrowedDownVoteOnAnEngagedArticleMarksOnlyThePickedTopic` pins exactly this (Go 10.2 → 10.0, `engagementReplaced` false, commented "D-11 as written"). The toast reads `👎 Narrowed · Rust −2.7 (replaces engagement) · Go −0.2`. The user explicitly said "not Go", and the toast appears to say the 👎 lowered Go.

Removing that narrowed vote has the mirror effect: Go reappears as `+0.2` with no "(engagement restored)" note.

**Fix:** Set the marker from the engagement change alone and drop the thumbs-changed condition. The thumbs condition was meant to separate the vote's own effect, but on an unpicked topic the engagement change is the whole effect:
```java
boolean engagementReplaced = differs(b.engagementLearned(), a.engagementLearned());
```
Then update the integration test to expect `true` for Go, and update the FeedbackResult Javadoc paragraph. Alternatively, amend D-11 explicitly and give unpicked topics their own wording (for example "(engagement no longer counts)"). Either way, the current silent drop should not ship as the intended UX.

### WR-03: `SIX_DECIMAL_TOLERANCE` cannot absorb the noise it names, and the test pins an impossible input

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleFeedbackService.java:31-35, 130-132`; `src/test/java/org/bartram/myfeeder/service/ArticleFeedbackServiceTest.java` (`sixDecimalNoiseIsNotAReplacement`)

**Issue:** `topicWeights` returns values from `ROUND(x::numeric, 6)` (`InterestScoreQueries.java:307-312`), so every value is a multiple of 1e-6.
- **What the noise actually does:** float8 SUM noise can only surface by flipping the 6th decimal at a .5 boundary, which gives a difference of exactly 1e-6. That exceeds the 5e-7 tolerance, so the noise the comment describes ("may differ in its last digit") is reported as a change.
- **What the tolerance actually absorbs:** only binary-representation error (~1e-16), which a plain `!=` on these values would already survive.
- **Why the test gives false confidence:** it feeds `2.7000004` and `0.9000004`, values the SQL can never return.

The real impact is small: a rare spurious "(replaces engagement)" on a vote for an article with no engagement. But the stated protection does not exist.

**Fix:** Use a tolerance above one unit in the last place, and pin a reachable input:
```java
/** Values are rounded to 6 decimals; a float8 SUM can flip the last one, so allow one unit plus slack. */
private static final double SIX_DECIMAL_TOLERANCE = 1.5e-6;
```
```java
// test: a one-unit flip in the 6th decimal is noise
new TopicWeight(10, "Rust", 20, 1.8, 2.700001, 22.700001, 1.8, 0.9, 0.900001, 21.8)
```
A real engagement share is `learnRate × strength × hinge`. That falls below 1.5e-6 only for a hinge under ~3e-6, which rounds away everywhere anyway.

### WR-04: The Interests line says "No learned adjustment yet" when votes and engagement cancel out

**File:** `src/main/frontend/src/components/TopicRow.tsx:256-262`

**Issue:** The branch keys on `Math.round(learned.learned * 10) === 0`, but `learned` is `thumbsLearned + engagementLearned`, and the two can have opposite signs. Down-votes on other articles can give thumbs −1.5 while stars give engagement +1.5. The line then reads `No learned adjustment yet · Effective weight +20`, even though the topic has learned two non-zero adjustments.

"Why N?" for an article matching that topic shows `(+20 −1.5 votes +1.5 engaged)`, so the two surfaces contradict each other. The split is computed (`split`) but dropped in this branch.

**Fix:** Take the "No learned adjustment yet" branch only when both parts round to zero. Otherwise use the normal line, which then reads `Learned +0.0 (votes −1.5, engaged +1.5) · Effective weight +20.0`:
```tsx
if (parts.length === 0 && Math.round(learned.learned * 10) === 0) {
  return <p className="interests-learned">No learned adjustment yet · ...</p>
}
```
Add a TopicRow test with `thumbsLearned: -1.5, engagementLearned: 1.5, learned: 0`.

## Info

### IN-01: Parts rounded on their own may not add up to the printed weight or learned value

**File:** `src/main/frontend/src/components/WhyBreakdown.tsx:83-99`; `src/main/frontend/src/components/TopicRow.tsx:230-237`

**Issue:** For votes +0.06 and engaged +0.06, the row reads `+10.1 (+10 +0.1 votes +0.1 engaged)`, where the parts add up to 10.2. The Interests line reads `Learned +0.1 (votes +0.1, engaged +0.1)`. D-01 accepts per-part rounding, but the mismatch will look like a bug to a reader who adds the numbers.

**Fix:** Accept it and note it in the component Javadoc. Or derive the displayed total from the displayed parts, which would break "never recompute", so documenting it is probably the better choice.

### IN-02: `engagementReplaced` brings back "+0.0" rows in the toast

**File:** `src/main/frontend/src/utils/feedback.ts:118-123`

**Issue:** The filter lists a topic when `engagementReplaced === true`, even if `after − before` rounds to zero. A matched topic with a tiny hinge produces `Rust +0.0 (replaces engagement)`: the thumbs gain (learnRate × h) minus the engagement share (≤ 0.5 × learnRate × h) can be well under 0.05. D-13 removed exactly this "+0.0" noise for ENGAGEMENT_CAP.

**Fix:** Require a non-zero rounded change for the replaced-engagement reason as well, or accept the noise and pin it with a test.

### IN-03: Starring runs the reaction's full refresh set even when the article was already starred, and invalidates `['articles']` twice

**File:** `src/main/frontend/src/hooks/useArticles.ts:57, 65`

**Issue:** `starred: true` always calls `afterEngagement`. On a re-star of an already-starred article, off Priority, that still invalidates `['interest','learned']`, `['articles']` and every other open by-id article. It also invalidates `['articles']` a second time right after line 57 does, which cancels and restarts the list refetch. The code is correct, but it wastes requests.

**Fix:** Skip `invalidateQueries({ queryKey: ['articles'] })` on line 57 when `afterEngagement` will run. Optionally pass the previous `starred` value in the variables and react only on a false → true change.

### IN-04: The "never touches priority" test assertions skip predicate-based invalidations

**File:** `src/main/frontend/src/hooks/engagementReaction.test.ts:195-201`; `src/main/frontend/src/hooks/engagementRefresh.test.ts` (`noSaveTouchesPriority`)

**Issue:** The loops check only `filters.queryKey?.[0]`. The off-Priority invalidation passes a `predicate` with no `queryKey`, so a predicate that matched `['priority']` would pass. The later `getQueryState(PRIORITY_KEY)?.isInvalidated === false` check does catch it, so coverage holds. Still, the loop over spy calls gives weaker evidence than it appears to. Separately, `TopicBreakdownRow.learnedWeight` is no longer read anywhere in the client. It is fine to keep for the wire contract, but it is now dead on the client.

**Fix:** In the loop, also run any `filters.predicate` against a fake `{ queryKey: ['priority'] }` query and assert it returns false. Otherwise rely only on the `isInvalidated` assertion.

---

_Reviewed: 2026-09-30T17:05:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
