# Phase 10: Explainable Engagement in the UI - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase renders the votes/engagement split that Phase 9 already computes and serves:
- "Why N?" topic rows
- the vote effect toast
- the Interests topic editor's learned line

It also gives every engagement action taken in Priority the same in-place reaction a vote has: patch the row, set the "Ranking changed" hint, and never re-sort. Two Phase 9 review findings get fixed here (WR-01 limit precedence, WR-03 replaced-engagement note), and IN-04 gets a corrected Javadoc.

There is no new ranking math, no schema change and no release. Requirements: LRN-06, EXPL-01.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (settled earlier; do not reopen)
- Phase 6 D-06: Priority is patched in place and never invalidated or refetched while it's being triaged. A vote patches only the voted row and sets `rankingChanged`. The roadmap says to reuse this reaction for engagement.
- Phase 9 D-10/D-15/D-16: the split fields already exist on the wire:
  - `InterestBreakdown.Row` / `TopicContribution` carry `thumbsWeight` + `engagementWeight` = `learnedWeight`.
  - `TopicLearned` / `TopicEffect` carry `thumbsLearned` + `engagementLearned` = `learned`.
  - Phase 10 renders these fields. The client never recomputes a weight.
- Phase 9 D-12: the vote effect's "before" includes the engagement the vote then replaces.
- DTO/enum rule: append, never rename.
- Deferred by the roadmap: ENG-F1 (reading-pane "Opened · Starred" status line). ENG-F6 is narrowed by D-14 below.

### "Why N?" topic rows (EXPL-01)
- **D-01:** The visible label names each part inline, extending the Phase 5 D-11 form. Example: `Rust  80% × +13.5 (+10 +2.0 votes +1.5 engaged)`. The part words are **"votes"** and **"engaged"**. This replaces the single "learned" wording.
- **D-02:** A part whose value rounds to zero tenths is omitted from the label:
  - `(+10 +1.5 engaged)` or `(+10 +2.0 votes)`
  - the plain Phase 5 row when both round to zero
- **D-03:** The row tooltip always lists every part, zeros included. Example: `Match 80% → counts 60% × +13.5 = +8.1 pts · base +10, votes +2.0, engaged +1.5`.
- **D-04:** "Why N?" stays free of limit notes. It shows no ENGAGEMENT_CAP marker. Limits are worded only in the toast and in Interests.
- The points and total logic are unchanged: rows still sum exactly to the badge (the server's integer points). Only the label and tooltip text change.

### Post-engagement reaction (LRN-06, SC-2, SC-3)
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

### Vote toast and limit notes (SC-4, WR-01, WR-03)
- **D-10 (WR-01):** Change the `LearnedLimit.of` precedence to **LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP → NONE**. This supersedes the order in Phase 9 D-11. The binding bounds are judged on base + thumbs + engagement. Update `ArticleFeedbackServiceTest.engagementCapOutranksClampAndRange` to match. — **Reversibility:** reversible — a single method plus its tests.
- **D-11 (WR-03):** When a vote removes this article's engagement share on a topic (before.engagementLearned ≠ after.engagementLearned, and the thumbs part changed), the effect carries an appended marker, either a new `LearnedLimit` value such as `REPLACED_ENGAGEMENT` or a flag on `TopicEffect` (planner's choice).
  - Toast for a vote: `Rust +0.9 (replaces engagement)`.
  - Toast for a removed vote: `Rust −0.9 (engagement restored)`.
- **D-12:** Each topic gets one note. A binding limit (LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE) wins over the replaced-engagement note, which shows only when the limit is otherwise NONE.
- **D-13:** The vote toast never mentions ENGAGEMENT_CAP, and ENGAGEMENT_CAP alone is not a reason to list a topic. The listing filter treats it like NONE, which removes the "+0.0" noise pinned by `feedback.test.ts` `engagementCapWithNoChangeIsStillListed`; update that test.

### Interests learned line (IN-01)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements and roadmap
- `.planning/REQUIREMENTS.md` — LRN-06, EXPL-01, and the ENG-F1/ENG-F6 deferrals
- `.planning/ROADMAP.md` § Phase 10 — success criteria 1–4 and notes (reuse the vote reaction, never invalidate `['priority']`)

### Prior phase decisions
- `.planning/phases/09-engagement-learning-model/09-CONTEXT.md` — D-09 split arithmetic, D-10/D-15/D-16 split fields, D-11 limit order (superseded by D-10 here), D-12 vote "before"
- `.planning/phases/09-engagement-learning-model/09-REVIEW.md` — WR-01 (with the reviewer's precedence code), WR-03, IN-01, IN-04
- `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md` — set WR-01, WR-03, IN-01 and IN-04 to `fixed` in the commit that fixes each one
- `.planning/phases/08-engagement-capture/08-CONTEXT.md` — capture paths, D-06 (by-id refresh only), D-10..D-12 (open is fire-and-forget)

### Project docs
- `CLAUDE.md` § Engagement capture, § Interest Ranking → Engagement learning (the LearnedLimit order text must be updated to match D-10)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `hooks/useFeedback.ts` `useVoteFeedback.onSuccess` is the reaction to mirror (patch the row, set the hint, invalidate learned and articles, invalidate other by-id articles when off Priority).
- `hooks/usePriorityArticles.ts` `patchPriorityArticle(qc, id, {interestScore})` patches a Priority row in place.
- `stores/priorityStore.ts` `setRankingChanged` sets the hint that `PriorityList.tsx` renders ("↻ Ranking changed — refresh").
- `utils/interest.ts` `formatSigned` handles the label numbers.

### Established Patterns
- `components/WhyBreakdown.tsx` `TopicLabel` is the single place for the row label and tooltip. It currently shows `(base +learned learned)` when learned rounds to non-zero.
- `utils/feedback.ts` `effectNote` / `formatVoteToast` handle toast wording, including the listing filter `round(d) != 0 || limit != NONE`.
- `components/TopicRow.tsx` `LearnedLine` renders the Interests learned line (capSuffix/effSuffix).
- `service/LearnedLimit.java` `of(...)` sets the precedence. `ArticleFeedbackService` builds `TopicEffect` from the before/after topic-weights queries.
- Engagement hooks today refresh only `['article', id]`:
  - `useOpenOriginal` and `useForgetEngagement` in `hooks/useEngagement.ts`
  - `useSaveToRaindrop` in `hooks/useArticles.ts`
  - the board add in `hooks/useBoards.ts`
  - `useUpdateArticleState` patches Priority `starred` but not the score

### Integration Points
- `useMatch('/priority')` is how the vote hook detects Priority. The engagement hooks need the same context.
- `hooks/engagementRefresh.test.ts` pins today's "by-id only" refresh behavior. Its expectations change with D-07.

</code_context>

<specifics>
## Specific Ideas

- Row label example: `Rust  80% × +13.5 (+10 +2.0 votes +1.5 engaged)`
- Toast examples: `Rust +0.9 (replaces engagement)` and `Rust −0.9 (engagement restored)`
- Interests examples: `Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5` and `Learned +8.0 (engaged +8.0 at max) · Effective weight +18.0`

</specifics>

<deferred>
## Deferred Ideas

- ENG-F1: reading-pane engagement status line ("Opened · Starred"). Still deferred.
- ENG-F6 (narrowed): contributing-article counts and a richer votes/engagement layout in Interests. The basic split moved into this phase (D-14).
- A forced Priority reset when paging while the hint is lit (IN-04 alternative). Rejected, because it re-sorts mid-triage.

### Reviewed Todos (not folded)
- `2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`: matched only on the keyword "raindrop" (score 0.2). It's backend resilience tuning, unrelated to this phase.

</deferred>

---

*Phase: 10-explainable-engagement-in-the-ui*
*Context gathered: 2026-09-30*
