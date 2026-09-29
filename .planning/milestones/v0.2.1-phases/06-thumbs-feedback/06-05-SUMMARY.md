---
phase: 06-thumbs-feedback
plan: 05
subsystem: ui
tags: [react, typescript, tanstack-query, vitest, interest-ranking, feedback]

requires:
  - phase: 06-thumbs-feedback (06-02)
    provides: vote endpoint, Article.feedback, TopicBreakdownRow.baseWeight/learnedWeight on the server side
  - phase: 06-thumbs-feedback (06-03)
    provides: LearnedLimit/ArticleFeedback types, matchedTopics(), FeedbackBar in ReadingPane, useFeedback invalidating ['interest', 'learned'] after every vote
  - phase: 06-thumbs-feedback (06-04)
    provides: narrowing and keyboard voting (ReadingPane/FeedbackBar state this plan builds beside)
  - phase: 06-thumbs-feedback (06-06)
    provides: GET /api/interest/topics/learned -> [{topicId, baseWeight, learned, effectiveWeight, limit}]
provides:
  - FeedbackNotice no-match line ("No topics matched — Create topic from article") between the reading toolbar and content
  - TopicDraft type and the one-shot draft channel ReadingPane.onCreateTopic -> MainLayout -> InterestsDialog.draft
  - TopicsSection seeds the draft last once per open; at 25 topics shows the at-max notice instead
  - TopicLearned + interestApi.getLearned + useLearnedTopics on ['interest', 'learned'] (invalidated by topic create/update/delete)
  - TopicRow read-only learned line with limit suffixes; appended drafts scroll into view
  - WhyBreakdown learned label and title for topic rows whose learned part rounds to a non-zero tenth
affects: [06-07, interest-ranking-docs, verify-work]

actuals:
  tokens: 8154
  tasks: 3
  commits: 5
plan_head_before: 4208c851ed0a408ea24871fc85d4f6e5dbb8418d

tech-stack:
  added: []
  patterns:
    - "One-shot dialog seed: parent holds a nullable draft, the dialog consumes it in a useState initializer, the close handler clears it"
    - "Server values only: the learned line and Why label print server numbers; rounding is the only client arithmetic"
    - "Separate query key for derived per-topic data so refreshing it never refetches (or reseeds) the editable topics list"

key-files:
  created:
    - src/main/frontend/src/components/FeedbackNotice.tsx
    - src/main/frontend/src/components/FeedbackNotice.test.tsx
  modified:
    - src/main/frontend/src/App.tsx
    - src/main/frontend/src/App.css
    - src/main/frontend/src/components/ReadingPane.tsx
    - src/main/frontend/src/components/ReadingPane.test.tsx
    - src/main/frontend/src/components/InterestsDialog.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/hooks/useInterest.ts
    - src/main/frontend/src/components/TopicRow.tsx
    - src/main/frontend/src/components/TopicRow.test.tsx
    - src/main/frontend/src/components/WhyBreakdown.tsx
    - src/main/frontend/src/components/WhyBreakdown.test.tsx

key-decisions:
  - "The draft reuses key d-1 and the normal draft row flow (focus, Unsaved tag, discard confirm, Preview, Save); + Add topic continues at d-2"
  - "The at-max decision is frozen at seed time (useState initializer), so deleting a topic afterwards doesn't retroactively add the draft; the notice tells the user to use Create topic from article again"
  - "The learned line sits after the line-2 weight row and before row errors; it renders only for saved rows with a learned entry"
  - "Vitest TAP has no node:test summary footer, so RED evidence records append # tests/# pass/# fail counted from the same run's result lines before tdd-red-evidence validation"

patterns-established:
  - "FeedbackNotice is pure props (article, onCreateTopic), so ReadingPane tests render it unmocked"
  - "InterestsDialog tests route GET /api/interest/topics/learned to [] by default so unrelated tests never hit the 404 fallback"

requirements-completed: [FDBK-05, FDBK-07]

coverage:
  - id: D1
    description: "No-match line shows for a voted, scored article with no matched topic and hides otherwise; Create topic from article passes the trimmed, 500-char title and ±20 by vote"
    requirement: FDBK-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/FeedbackNotice.test.tsx#showsForAVotedScoredArticleWithNoMatches, hiddenWithoutVoteUnscoredOrMatched, createTopicPassesTheDraft"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx#noticeSitsBetweenToolbarAndContent"
        status: pass
    human_judgment: false
  - id: D2
    description: "InterestsDialog seeds one unsaved prefilled draft last (focused empty name, title description, ±20 weight), counts it as unsaved, sends nothing, seeds once, and shows the at-max notice at 25 topics"
    requirement: FDBK-05
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#draftRowIsSeededLastWithTheTitle, draftCountsAsUnsavedAndCloseAsksToDiscard, draftIsSeededOnce, atMaxShowsTheNoticeInsteadOfADraft, noDraftWithoutTheProp"
        status: pass
    human_judgment: false
  - id: D3
    description: "Topic editor learned line prints the server's learned and effective values with limit suffixes, matched by topic id, one note on load failure, refetched after topic saves without disturbing unsaved edits"
    requirement: FDBK-07
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#learnedLineShowsLearnedAndEffective, noLearnedAdjustmentYet, limitSuffixes, unsavedBaseEditSaysItUpdatesOnSave, draftsAndMissingEntriesShowNoLine, draftScrollsIntoView"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#learnedValuesAreMatchedById, learnedLoadFailureShowsOneNote, learnedIsRefetchedAfterATopicSave, rowEditsSurviveALearnedRefetch"
        status: pass
    human_judgment: false
  - id: D4
    description: "Why N? topic rows show '(base learned learned)' when the learned part rounds to a non-zero tenth, keep the Phase 5 label otherwise, and still sum to the badge"
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/WhyBreakdown.test.tsx#learnedPartShownWhenItRoundsAboveZero, negativeLearnedPart, phase5LabelUnchangedWithoutLearned, titleCarriesBaseAndLearned, rowsStillSumToTheBadge"
        status: pass
    human_judgment: false
  - id: D5
    description: "End-to-end flow in the running app: vote toast and Why learned part, Priority badge-only update, no-match line opening a prefilled draft, learned lines in Interests; plus narrow-pane wrapping of the no-match strip and long Why labels"
    verification: []
    human_judgment: true
    rationale: "Task 3 <human-check> needs bootTestRun + npm run dev with scored articles and a live Jev key; visual wrapping (UI E4/E6 overflow backstops) is not asserted by jsdom tests"

duration: 7min
completed: 2026-09-27
status: complete
---

# Phase 6 Plan 05: Vote Feedback Loop in the UI Summary

**A vote that matched no topic now says so under the reading toolbar and opens Interests with an unsaved, title-prefilled ±20 topic draft; each saved topic shows the server's learned adjustment and effective weight, and "Why N?" rows print base + learned parts that still sum to the badge.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-09-27T03:55:00Z
- **Completed:** 2026-09-27T04:02:21Z
- **Tasks:** 3
- **Files modified:** 14 (2 created, 12 modified)

## Accomplishments

- `FeedbackNotice` renders `No topics matched — Create topic from article` (role status) between `.reading-toolbar` and `.reading-content` only for a voted, scored article with zero matched topics (D-18, D-19).
- "Create topic from article" sets a one-shot `TopicDraft` in `MainLayout` and opens `InterestsDialog`; `TopicsSection` seeds it once as the last row (empty focused name, title description cut to 500, weight ±20), it counts as unsaved, and nothing is saved or re-judged until Save topic (D-20, D-21). At 25 topics the at-max notice shows instead. The draft is cleared on close.
- `useLearnedTopics()` on `['interest', 'learned']` feeds each saved `TopicRow` a read-only line (`Learned from votes +4.0 · Effective weight +24.0`, `No learned adjustment yet · …`, `… updates when you save`) with the LEARNED_CAP / SIGN_CLAMP / WEIGHT_RANGE suffixes; topic create/update/delete invalidate it and never the topics list (FDBK-07).
- `WhyBreakdown` topic rows with a learned part print `Rust  90% × +21.8 (+20 +1.8 learned)` and the ` · base +20, learned +1.8` title suffix; all other rows are byte-identical to Phase 5 (D-11).

## Task Commits

1. **Task 1 (tracer): no-match line and prefilled draft** - `eb1709b` (feat)
2. **Task 2: topic editor learned line** - `d06b4ad` (test, RED), `fe91f42` (feat, GREEN)
3. **Task 3: Why rows with the learned part** - `67716ed` (test, RED), `f64ea05` (feat, GREEN)

**Plan metadata:** recorded in the docs commit that adds this SUMMARY.

## Files Created/Modified

- `src/main/frontend/src/components/FeedbackNotice.tsx` - the no-match line and draft hand-off
- `src/main/frontend/src/components/FeedbackNotice.test.tsx` - visibility and draft-shape tests
- `src/main/frontend/src/components/ReadingPane.tsx` - `onCreateTopic` prop, renders FeedbackNotice under the toolbar
- `src/main/frontend/src/App.tsx` - `interestsDraft` state, one-shot draft wiring to InterestsDialog
- `src/main/frontend/src/components/InterestsDialog.tsx` - `TopicDraft`, `draft` prop, seeding, at-max notice, learned map per row, learned-failure note
- `src/main/frontend/src/api/interest.ts` - `TopicLearned`, `interestApi.getLearned`
- `src/main/frontend/src/hooks/useInterest.ts` - `useLearnedTopics`, learned invalidation on topic mutations
- `src/main/frontend/src/components/TopicRow.tsx` - `learned` prop, `LearnedLine`, draft scrollIntoView
- `src/main/frontend/src/components/WhyBreakdown.tsx` - `TopicLabel` with the learned form
- `src/main/frontend/src/App.css` - `.feedback-notice`, `.feedback-create`, `.interests-learned`, `.interests-learned-value`
- Tests: `ReadingPane.test.tsx`, `InterestsDialog.test.tsx`, `TopicRow.test.tsx`, `WhyBreakdown.test.tsx`

## Decisions Made

- The seeded draft uses key `d-1` and the existing draft flow; `draftCounter` starts at 1 so "+ Add topic" continues at `d-2`.
- The draft/at-max choice is made once in `useState` initializers, matching the rows' seed-once rule.
- The learned line is placed after the line-2 weight row, before row errors.
- RED evidence for the two `tdd="true"` tasks was validated with `check tdd-red-evidence` (both `RED_EVIDENCE_OK`). Vitest's TAP output has no node:test summary footer, so `# tests/# pass/# fail` counts from the same run's result lines were appended to the record.

## Deviations from Plan

None - plan executed exactly as written. Two notes:
- `FeedbackNotice` keeps the literal `slice(0, 500)` (with a comment naming `InterestService.MAX_DESCRIPTION_CHARS`) instead of a named constant, so the plan's acceptance grep matches.
- Task 1 is a `type="tracer"` task. Auto mode is off, `human_verify_mode` is end-of-phase and its `<verify>` is automated-only, so the tracer gate re-ran verify, which passed, and execution moved on to Tasks 2 and 3 without a checkpoint.

## TDD Gate Compliance

- Task 2: RED `d06b4ad` (test) precedes GREEN `fe91f42` (feat); RED verdict `RED_EVIDENCE_OK` on `TopicRow > learned line > learnedLineShowsLearnedAndEffective`.
- Task 3: RED `67716ed` (test) precedes GREEN `f64ea05` (feat); RED verdict `RED_EVIDENCE_OK` on `WhyBreakdown > learned part (D-11) > learnedPartShownWhenItRoundsAboveZero`.
- No REFACTOR commits were needed.

## Verification

- `npx vitest run` on each task's files: pass. `npx tsc -b`: exit 0.
- Full frontend suite `npm test`: 27 files, 296 tests passed.
- Backend regression `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew test -x npmBuild -x npmInstall`: exit 0 (run with `SPRING_AI_TYPESAFE_*` and `SPRING_PROFILES_ACTIVE` unset).
- `git status --short`: `.claude/CLAUDE.md`, `.envrc`, `.planning/config.json` and `CLAUDE.md` are still modified and unstaged, and none of them was committed.
- Threat model: no `dangerouslySetInnerHTML` in FeedbackNotice, TopicRow or WhyBreakdown (T-06-19); the title is cut to 500 (T-06-20); the draft flow sends no Preview and no save (T-06-21; `draftCountsAsUnsavedAndCloseAsksToDiscard` asserts no non-GET call).

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 06-07 (last in the phase) can proceed. FDBK-05 is also declared by 06-07, so it is marked complete only after 06-07 finishes (shared-ID gate); FDBK-07 is complete.
- The Task 3 `<human-check>` (live vote/toast/Why/no-match/Interests walkthrough) is deferred to end-of-phase UAT (coverage D5).

## Executor Context

The six executor workflow files were read in full before execution: execute-plan.md, templates/summary.md, references/checkpoints.md, references/tdd.md, references/worktree-path-safety.md, references/executor-examples.md.

---
*Phase: 06-thumbs-feedback*
*Completed: 2026-09-27*

## Self-Check: PASSED

- All 14 files in `files_modified` exist on disk.
- Commits eb1709b, d06b4ad, fe91f42, 67716ed and f64ea05 exist in `git log --all`.
