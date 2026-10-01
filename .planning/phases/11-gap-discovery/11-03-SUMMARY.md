---
phase: 11-gap-discovery
plan: 03
subsystem: ui
tags: [react, tanstack-query, typescript, vitest, interests]

requires:
  - phase: 11-gap-discovery
    provides: "Fixed backend contract from plans 11-01/11-02: GET /api/interest/suggestions -> {items,total}; PUT /api/interest/suggestions/{articleId}/dismissal (no body) -> 204"
provides:
  - "Suggested topics section in the Interests dialog (after Topics, before the Re-score footer)"
  - "interestApi.getSuggestions / dismissSuggestion and the TopicSuggestion / TopicSuggestions types"
  - "SUGGESTIONS_KEY, useTopicSuggestions (staleTime 0), useDismissSuggestion"
  - "Suggestions marked stale after engagement reactions, votes, topic create, topic delete and Re-score"
affects: [11-04-create-topic-from-suggestion, interests-dialog, engagement-reaction]

actuals:
  tokens: 8877
  tasks: 3
  commits: 5
plan_head_before: e02611503418fa059137a17c0724ef1b8300519b
plan_head_after: 7816b555a7a94e579967d3ac1a12ae69f0857f30

tech-stack:
  added: []
  patterns:
    - "SuggestedTopics renders inside TopicsSection's returned fragment, after its section, so plan 11-04 can reach rows/atMax/addDraft directly"
    - "Refetch-on-open via staleTime 0 plus invalidation of a usually-unmounted query (SC-1)"

key-files:
  created: []
  modified:
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/hooks/engagementReaction.ts
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/App.css
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/hooks/useInterest.test.tsx
    - src/main/frontend/src/hooks/engagementReaction.test.ts

key-decisions:
  - "SUGGESTIONS_KEY is declared next to TOPICS_KEY in useInterest.ts so it is defined before the mutation hooks that invalidate it"
  - "Dismiss has no inlineError meta: failures surface through the global toast, and the row stays"
  - "Topic update and profile save do not invalidate suggestions (stored nouls and suggestion badges are unaffected)"

patterns-established:
  - "Suggestion rows: InterestBadge + plain-text title (title attribute tooltip) + feed name, theme variables only"

requirements-completed: [GAP-01, GAP-03, GAP-05]

coverage:
  - id: D1
    description: "Suggested topics section below Topics with badge, plain-text title and feed; hidden when empty/loading/failed; shown in cold start and without a key; heading counts the total only when capped"
    requirement: GAP-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#Suggested topics (sectionSitsBelowTopicsAndAboveTheRescoreFooter, rowShowsTheBadgeTitleAndFeed, hiddenWhenThereAreNoSuggestions, hiddenWhenTheListFails, headerShowsTheTotalOnlyWhenCapped, shownInColdStartAndWithoutAKey)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every dialog open refetches the suggestions list (SC-1)"
    requirement: GAP-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#everyOpenRefetchesTheList"
        status: pass
    human_judgment: false
  - id: D3
    description: "Dismiss: one bodyless PUT, no confirm or undo, row removed at once, list refetched; a failure keeps the row and toasts"
    requirement: GAP-03
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#Dismiss (dismissRemovesTheRowWithoutConfirming, dismissingTheLastSuggestionHidesTheSection, aFailedDismissKeepsTheRowAndToasts)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Listing and dismissing never post Preview; titles and feed names render as literal text (T-11-14, T-11-15)"
    requirement: GAP-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#listingNeverPostsPreview, dismissNeverPostsPreview, titlesRenderAsText"
        status: pass
    human_judgment: false
  - id: D5
    description: "Suggestions marked stale after engagement reactions and votes, topic create, topic delete and Re-score; not after topic update or profile save"
    requirement: GAP-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/hooks/engagementReaction.test.ts#suggestionsAreMarkedStaleOnAndOffPriority"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/hooks/useInterest.test.tsx#suggestions freshness"
        status: pass
    human_judgment: false
  - id: D6
    description: "Visual fit of the suggestion rows across the 6 themes (ellipsis, spacing, badge alignment)"
    verification: []
    human_judgment: true
    rationale: "Styling uses theme variables only, but how the rows look in each theme is not asserted by any test"

duration: 4min
completed: 2026-10-01
status: complete
---

# Phase 11 Plan 03: Suggested Topics UI Summary

**The Interests dialog now has a "Suggested topics" section below Topics. It lists engaged articles that no topic covers, each with its badge, title and feed. Every open refetches it, and a one-click Dismiss removes a row for good. Engagement, votes, topic create/delete and Re-score mark the list stale.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-10-01T22:32:40Z
- **Completed:** 2026-10-01T22:36:40Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- `SuggestedTopics` is rendered in `TopicsSection`'s fragment, so the order is notices, Profile, Topics, Suggested topics, Re-score footer (D-01). It shows nothing while the list is empty, loading or failed (D-02). No status gate applies (D-03). Rows show the badge, title and feed and nothing else (D-04). The heading reads `Suggested topics (10 of 23)` only when the list is capped (D-07).
- `useTopicSuggestions` uses `staleTime: 0`, so each dialog open refetches the list even under the app's 30s default (SC-1).
- Dismiss sends a bodyless PUT, removes the row from the cache at once, then refetches (D-17). A failure keeps the row and goes to the global toast.
- `invalidateAfterLearnedChange` (votes and every `afterEngagement` caller, including Forget) plus topic create, topic delete and Re-score mark `['interest','suggestions']` stale. Topic update and profile save do not.
- 9 section tests, 4 dismiss tests, 1 engagement test and 4 freshness tests. Full frontend suite: 401/401, `npx tsc -b` clean.

## Task Commits

1. **Task 1 (tracer): Suggested topics section, fetched fresh on every open:** `607cb91` (feat)
2. **Task 2: Dismiss.** RED `7b12ec6` (test), then GREEN `ec52f47` (feat)
3. **Task 3: Freshness invalidations.** RED `e8c3b28` (test), then GREEN `7816b55` (feat)

## Files Created/Modified
- `src/main/frontend/src/api/interest.ts`: `TopicSuggestion`, `TopicSuggestions`, `getSuggestions`, `dismissSuggestion` (bodyless PUT)
- `src/main/frontend/src/hooks/useInterest.ts`: `SUGGESTIONS_KEY`, `useTopicSuggestions`, `useDismissSuggestion`; create, delete and Re-score now invalidate the suggestions
- `src/main/frontend/src/hooks/engagementReaction.ts`: the shared refresh set now invalidates `['interest', 'suggestions']`
- `src/main/frontend/src/components/InterestsDialog.tsx`: module-private `SuggestedTopics`, rendered after the Topics section
- `src/main/frontend/src/App.css`: `.interests-suggestion*` rules, theme variables only
- `src/main/frontend/src/components/InterestsDialog.test.tsx`: default suggestions route in `beforeEach`, helpers, and the `Suggested topics` / `Dismiss` describes
- `src/main/frontend/src/hooks/useInterest.test.tsx`: extended `interestApi` mock and the `suggestions freshness` describe
- `src/main/frontend/src/hooks/engagementReaction.test.ts`: `suggestionsAreMarkedStaleOnAndOffPriority`

## Decisions Made
- I moved `SUGGESTIONS_KEY` next to `TOPICS_KEY`. Task 1 had placed it at the bottom of the file, and placing it before the mutation hooks that use it reads better. Its name and export are unchanged.
- The fragment wrapper re-indents `TopicsSection`'s existing `<section>` by two spaces. The section's markup is otherwise unchanged.

## Deviations from Plan

None. The plan was executed as written.

## TDD Gate Compliance

- Task 2: RED `7b12ec6`. The 4 target tests failed because the `Dismiss suggestion: …` button was missing, and the 61 existing tests passed. GREEN `ec52f47` made all 65 pass.
- Task 3: RED `e8c3b28`. The 4 target tests failed on the staleness assertion (`expected false to be true`). The `topicUpdateAndProfileSaveLeaveSuggestionsAlone` guard passed on purpose, because it asserts that nothing is invalidated. GREEN `7816b55` made them pass.
- No REFACTOR commits were needed.

## Issues Encountered
None.

## User Setup Required
None. No external service configuration is required.

## Next Phase Readiness
- Plan 11-04 can add Create topic and the draft behavior. `SuggestedTopics` already sits inside `TopicsSection`'s fragment, so `rows`, `atMax` and `addDraft` can be passed to it as props.
- The real endpoint comes from plans 11-01/11-02, which are running in parallel. The frontend tests stub fetch against the fixed contract.

---
*Phase: 11-gap-discovery*
*Completed: 2026-10-01*

## Self-Check: PASSED
