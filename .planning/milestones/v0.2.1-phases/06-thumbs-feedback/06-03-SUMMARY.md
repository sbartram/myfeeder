---
phase: 06-thumbs-feedback
plan: 03
subsystem: ui
tags: [react, typescript, tanstack-query, zustand, vitest, interest-ranking, thumbs-feedback]

requires:
  - phase: 06-thumbs-feedback
    provides: "06-01 PUT/DELETE /api/articles/{id}/feedback returning FeedbackResult {article, scored, effects[]} with TopicEffect.limit"
  - phase: 05-priority-view
    provides: "PRIORITY_KEY, patchPriorityArticle, priorityStore.setRankingChanged, ReadingPane toolbar and Why rows"
provides:
  - "FeedbackBar: 👍 Up / 👎 Down aria-pressed toggles in the reading pane after ★ Star"
  - "useVoteFeedback() { press, narrow } on the 'article-feedback' mutation scope, with intent-first writes, the Pitfall 3 merge, per-view cache policy and status-based error toasts"
  - "utils/feedback.ts: Vote, VoteKind, nextVote, matchedTopics, formatDelta, formatVoteToast"
  - "articlesApi.setFeedback / clearFeedback, apiDeleteJson<T>"
  - "Types: LearnedLimit, ArticleFeedback, TopicEffect, FeedbackResult; Article.feedback?; TopicBreakdownRow.baseWeight? / learnedWeight?"
  - "patchPriorityArticle accepts interestScore (null is a real value)"
  - "CSS: .feedback-group, .vote-btn[aria-pressed='true'], wrapping .reading-toolbar"
affects: [06-04, 06-05, 06-07]

actuals:
  tokens: 11200
  tasks: 3
  commits: 4
plan_head_before: 53ceab61ec11f70d806bac1de00c86ca740ca5cf

tech-stack:
  added: []
  patterns:
    - "Intent-first vote: write the client intent to ['article', id] synchronously in the handler, then mutate on a static TanStack scope so votes run one at a time in press order"
    - "Pitfall 3 merge: on success, keep the cached feedback while another feedback mutation for the same article is pending (isMutating > 1), otherwise take the response's"
    - "Per-view cache policy: /priority patches only the voted row and lights Ranking changed; elsewhere invalidate ['articles'] and other length-2 ['article', n] keys"

key-files:
  created:
    - src/main/frontend/src/hooks/useFeedback.ts
    - src/main/frontend/src/hooks/useFeedback.test.ts
    - src/main/frontend/src/utils/feedback.ts
    - src/main/frontend/src/components/FeedbackBar.tsx
    - src/main/frontend/src/components/FeedbackBar.test.tsx
  modified:
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/api/client.ts
    - src/main/frontend/src/api/articles.ts
    - src/main/frontend/src/components/ReadingPane.tsx
    - src/main/frontend/src/components/ReadingPane.test.tsx
    - src/main/frontend/src/hooks/usePriorityArticles.ts
    - src/main/frontend/src/hooks/usePriorityArticles.test.ts
    - src/main/frontend/src/App.css

key-decisions:
  - "Vote errors map to fixed UI-SPEC copy by ApiError.status (404 gone, 400 with picks = narrowing, anything else = save failed); the server's message is never shown, and meta.inlineError suppresses the global toast"
  - "['articles'] is invalidated in both branches (it never touches the Priority key), so date-sorted lists are fresh when the user leaves /priority"
  - "useFeedback.test mounts usePriorityArticles only in the Priority test, because even a disabled useInfiniteQuery creates a ['priority'] cache entry"

patterns-established:
  - "Vote hook tests render useVoteFeedback + useArticle in one renderHook on a createQueryClient() client (so meta.inlineError is honored) with query retries off, and hold responses on deferred promises to prove ordering"
  - "Assert query invalidation with qc.getQueryState(key)?.isInvalidated on seeded entries instead of spying on invalidateQueries"

requirements-completed: [FDBK-01, FDBK-02, FDBK-04]

coverage:
  - id: D1
    description: "👍 Up / 👎 Down in the reading pane after ★ Star: store, flip and remove the vote, with the pressed state shown before the request resolves and correct labels/titles"
    requirement: FDBK-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#rendersVoteButtonsAfterStar"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#pressWritesTheIntentBeforeTheRequestResolves"
        status: pass
    human_judgment: false
  - id: D2
    description: "Effect toast prints each topic's server-computed after - before with one decimal, sorted by absolute change, with the vote's lead"
    requirement: FDBK-04
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackBar.test.tsx#upVoteSavesAndShowsTheEffect"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#toastsFollowPressOrder"
        status: pass
    human_judgment: false
  - id: D3
    description: "Badges update from the vote response with no extra fetch: /priority patches only the voted row and lights Ranking changed; elsewhere lists and other by-id articles refresh; learned values always refresh"
    requirement: FDBK-02
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#priorityVotePatchesOnlyTheVotedRow"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#outsidePriorityVoteInvalidatesListsAndOtherArticles"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#learnedIsInvalidatedOnEveryVote"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/usePriorityArticles.test.ts#patchCopiesInterestScoreIncludingNull"
        status: pass
    human_judgment: false
  - id: D4
    description: "Rapid votes stay in press order and a stale response never overwrites a newer intent; failed votes show their error copy and revert to the server's vote"
    requirement: FDBK-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#rapidUpDownUpEndsAsASingleUp"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#staleResponseDoesNotOverwriteANewerIntent"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#failedVoteRevertsAndShowsTheSaveCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#missingArticleShowsTheGoneCopy"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#rejectedNarrowingShowsTheNarrowCopy"
        status: pass
    human_judgment: false
  - id: D5
    description: "The pressed vote reads at a glance (accent text, weight 600) and the reading toolbar wraps instead of clipping in a narrow pane"
    requirement: FDBK-01
    verification:
      - kind: other
        ref: "grep of App.css for .vote-btn[aria-pressed='true'], .feedback-group and .reading-toolbar flex-wrap"
        status: pass
    human_judgment: true
    rationale: "Visual styling and wrap behavior (Task 2 human-check): open an article under ./gradlew bootTestRun + npm run dev, press 👍 Up and confirm accent/bold, then narrow the pane and confirm the toolbar wraps"

duration: 5min (continuation; Task 1 was implemented by the previous executor)
completed: 2026-09-27
status: complete
---

# Phase 6 Plan 3: Thumbs Voting in the Reading Pane Summary

**👍/👎 aria-pressed toggles in the reading pane that store, flip or remove the vote on a serialized TanStack scope. The pressed state shows before the request resolves, and the toast prints each topic's server-computed change. /priority patches only the voted row's badge; other views refresh their badges. Failed votes show fixed error copy and revert to the server's vote.**

## Performance

- **Duration:** about 5 min in this continuation (Task 1 was implemented and verified by the previous executor, which stopped at the TruffleHog checkpoint)
- **Started:** 2026-09-27T03:22:16Z (continuation)
- **Completed:** 2026-09-27T03:27:35Z
- **Tasks:** 3
- **Files modified:** 13

## Accomplishments

- The reading-pane toolbar shows `👍 Up` / `👎 Down` in a `.feedback-group` right after `★ Star`. Pressing the active vote removes it (DELETE), and pressing the other one flips it (PUT with `topicIds: null`). The pressed state comes from the intent written to `['article', id]` before the request resolves. A vote never marks read, advances or changes the selection.
- `formatVoteToast` prints each topic's `after - before` from the server, rounded to one decimal, largest change first, after the vote's lead (`👍 `, `👎 `, `Vote removed · `, `👎 Narrowed · `, `👎 All matched topics · `).
- Votes run one at a time on the `article-feedback` scope. When a response arrives while a newer vote on the same article is queued, the newer intent is kept (Pitfall 3), so rapid u, d, u ends as a single 👍 with three toasts in order.
- On `/priority`, a vote patches only the voted row's `interestScore` and sets "Ranking changed". The Priority key is never invalidated or refetched. Elsewhere, a vote invalidates `['articles']` and the other by-id articles; `['article', id, 'extracted']` is left alone. Every vote invalidates `['interest', 'learned']`.
- Vote errors use `meta.inlineError` with fixed copy by status (404, 400 on a narrowing, anything else). The by-id article is then refetched, so the pressed state reverts to the server's.
- The pressed vote uses `var(--accent)` at weight 600. The toolbar wraps with a 4px row gap.

## Task Commits

1. **Task 1 (tracer): 👍 and 👎 in the reading pane store, flip or remove the vote and toast its effect**: `72aab7b` (feat)
2. **Task 2: The pressed vote stands out and the toolbar wraps instead of clipping**: `a84cd3e` (feat)
3. **Task 3 (TDD): Rapid presses stay in order, badges refresh per view without re-ranking Priority, and a failed vote reverts**: RED `120d878` (test), GREEN `402550a` (feat); no refactor was needed

`53ceab6` (`chore: disable trufflehog self-update in pre-commit hook`) was committed by the orchestrator to clear the blocker. It is not part of this plan and is excluded from the commit count: `plan_head_before` is 53ceab6, while the on-disk ledger records 00d12d7, the HEAD before that fix.

## Files Created/Modified

- `src/main/frontend/src/types/index.ts`: `LearnedLimit`, `ArticleFeedback`, `TopicEffect`, `FeedbackResult`; `Article.feedback?`; `TopicBreakdownRow.baseWeight?` / `learnedWeight?`
- `src/main/frontend/src/api/client.ts`: `apiDeleteJson<T>` (DELETE that keeps the JSON body; `apiDelete` unchanged)
- `src/main/frontend/src/api/articles.ts`: `setFeedback`, `clearFeedback`
- `src/main/frontend/src/utils/feedback.ts`: `nextVote`, `matchedTopics`, `formatDelta`, `formatVoteToast`
- `src/main/frontend/src/hooks/useFeedback.ts`: `useVoteFeedback`, `FEEDBACK_SCOPE`, `VoteVars`, cache policy and error copy
- `src/main/frontend/src/hooks/usePriorityArticles.ts`: `patchPriorityArticle` accepts `interestScore`
- `src/main/frontend/src/components/FeedbackBar.tsx`: the 👍/👎 group
- `src/main/frontend/src/components/ReadingPane.tsx`: renders `<FeedbackBar article={article} />` after Star
- `src/main/frontend/src/App.css`: `.feedback-group`, `.vote-btn[aria-pressed='true']`, wrapping `.reading-toolbar`
- Tests: `FeedbackBar.test.tsx` (new), `useFeedback.test.ts` (new), `ReadingPane.test.tsx`, `usePriorityArticles.test.ts`

## Decisions Made

- Error copy is chosen by `ApiError.status`, and the 400 narrowing copy applies only when the vote carried picks (`topicIds !== null`). A 400 on a plain vote falls back to the generic save-failed copy.
- `['articles']` is invalidated on both routes, as the plan specifies. It is outside the Priority key, so the Priority list is not refetched (existing `articlesInvalidationDoesNotRefetchPriority` guards this).
- Test harness choices (RED-phase fixes, not behavior changes): `vi.resetAllMocks()` instead of `clearAllMocks()`, so an unconsumed `mockReturnValueOnce` cannot leak between tests. `setFeedback` assertions use `waitFor`, because `mutate` invokes the `mutationFn` on a later microtask; only the intent write is synchronous. The Priority list is mounted only in the Priority test.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] TruffleHog pre-commit hook self-updater failure blocked the Task 1 commit**
- **Found during:** Task 1 (previous executor)
- **Issue:** The pre-commit TruffleHog hook failed in its self-update step, a tool failure rather than a finding.
- **Fix:** The user chose "edit hook". The orchestrator committed `53ceab6`, which adds `--no-update` to `.pre-commit-config.yaml`. All plan commits then ran with hooks on (TruffleHog passed each time). No `--no-verify` or `SKIP=` was used.
- **Files modified:** `.pre-commit-config.yaml` (orchestrator commit, outside this plan)
- **Committed in:** `53ceab6` (not counted in this plan)

**2. [TDD] Invalid first RED run corrected before commit**
- **Found during:** Task 3 RED
- **Issue:** The first RED run failed partly for harness reasons: once-mock leakage between tests, a synchronous `setFeedback` assertion, and a destructured `result` shadowing the `result()` fixture helper. That run would have been INVALID_RED.
- **Fix:** Fixed the harness. Re-ran RED: 9 tests failed, every one on an assertion for the planned behavior. The target `outsidePriorityVoteInvalidatesListsAndOtherArticles` failed with `invalidated(['articles', {}])` false, and `check tdd-red-evidence` returned RED_EVIDENCE_OK.
- **Files modified:** `src/main/frontend/src/hooks/useFeedback.test.ts`
- **Committed in:** `120d878` (RED). The Priority-mount fix to the same harness landed in GREEN `402550a`, because a disabled `useInfiniteQuery` still creates a `['priority']` entry.

---

**Total deviations:** 2 (1 blocking tool issue resolved by the user, 1 TDD harness correction). **Impact on plan:** none on scope or behavior.

## TDD Gate Compliance

- Task 3: RED `120d878` precedes GREEN `402550a`. RED evidence: vitest `--reporter=tap-flat` output, with a `# tests 25 / # pass 16 / # fail 9` footer computed from that run's own `ok`/`not ok` lines, since vitest's TAP has no node-style summary. Target `outsidePriorityVoteInvalidatesListsAndOtherArticles` failed on an assertion; verdict RED_EVIDENCE_OK.
- Four new tests already passed in RED because they cover behavior the Task 1 tracer shipped: `pressWritesTheIntentBeforeTheRequestResolves`, `rapidUpDownUpEndsAsASingleUp` (scope serialization), `toastsFollowPressOrder` and `narrowSendsThePicksAndAllMatchedSendsNone`. The nine that failed are exactly the Task 3 additions: the Pitfall 3 merge, per-view cache policy, learned invalidation, error copy and revert, and the `interestScore` patch.
- Task 1 is a tracer (not tdd="true"); its tests and code were committed together in `72aab7b`.

## Issues Encountered

- The TruffleHog updater failure is covered in Deviations #1.

## Verification

- `npx tsc -b`: exit 0. `npx eslint` on the changed hooks, utils and component: clean.
- `npm test` (full frontend suite): 25 files, 237 tests, all passing.
- `useFeedback.test.ts` passed 5 of 5 repeated runs (the research flagged the u/d/u test as flake-prone).
- All acceptance greps pass. Task 1: scope 1, apiDeleteJson 1, `<FeedbackBar article={article} />` 1, no `updateState|setSelectedArticle`. Task 2: aria-pressed rule 1, `.feedback-group` 1, toolbar wraps, no color literal added. Task 3: `interestScore?: number | null` present, `setRankingChanged(true)` 1, `inlineError: true` 1, zero Priority `invalidateQueries` / `refetchQueries` outside tests.
- There is no `dangerouslySetInnerHTML` in `FeedbackBar.tsx`, `utils/feedback.ts`, `useFeedback.ts` or `Toast.tsx` (T-06-12).
- `git status` shows the user's `.envrc`, `CLAUDE.md`, `.claude/CLAUDE.md` and `.planning/config.json` still modified and unstaged.

## User Setup Required

None. No external service configuration required.

## Next Phase Readiness

- Plan 06-04 can add the Narrow… control and the picker inside `.feedback-group` (already `position: relative`) using `useVoteFeedback().narrow` and `matchedTopics`.
- Plan 06-05 can use `TopicBreakdownRow.baseWeight` / `learnedWeight` and `Article.feedback`.
- Plan 06-07 can extend `formatVoteToast`.
- Task 2's visual check (accent/bold pressed state, toolbar wrap) is left for end-of-phase UAT.
- No blockers.

## Self-Check: PASSED

- All 5 created files are present on disk.
- Commits 72aab7b, a84cd3e, 120d878 and 402550a are present in git log.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*
