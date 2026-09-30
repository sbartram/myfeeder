---
phase: 10-explainable-engagement-in-the-ui
plan: 03
subsystem: ui
tags: [react, typescript, vitest, interest-ranking, engagement, explainability]

requires:
  - phase: 10-explainable-engagement-in-the-ui
    provides: "10-02: TopicEffect.engagementReplaced and TopicLearned.engagementAtCap on the wire; LearnedLimit precedence LEARNED_CAP -> SIGN_CLAMP -> WEIGHT_RANGE -> ENGAGEMENT_CAP"
  - phase: 09-engagement-learning-model
    provides: "thumbsWeight/engagementWeight on Why N? rows and thumbsLearned/engagementLearned on effects and learned topics"
provides:
  - "Vote toast notes '(replaces engagement)' / '(engagement restored)', binding limits win, ENGAGEMENT_CAP never worded or listed alone (D-11..D-13)"
  - "'Why N?' topic rows name base, votes and engaged parts; the tooltip lists every part (D-01..D-04)"
  - "Interests learned line 'Learned ±x.x (votes ±x.x, engaged ±x.x) · Effective weight ±x.x' with 'at max' for capped parts (D-14, D-15)"
  - "IN-01 (Phase 9 review) fixed"
affects: [10-04 docs and CLAUDE.md wording, phase 10 verification and UAT]

actuals:
  tokens: 10010    # chars/4 over the realized diff (40,038 chars, 12 files)
  tasks: 3
  commits: 5
plan_head_before: 618a640380ee240f1b3a12c676998ba2397b9c3e
plan_head_after: 940045c266fff803ac7b888259b59964edb349e4

tech-stack:
  added: []
  patterns:
    - "Displayed weight parts are the server's split fields rounded with formatDelta; the client never derives one part from another"
    - "Optional wire booleans default absent-as-false (engagementReplaced) or fall back to the limit (engagementAtCap ?? limit === 'ENGAGEMENT_CAP')"

key-files:
  created:
    - .planning/phases/10-explainable-engagement-in-the-ui/deferred-items.md
  modified:
    - src/main/frontend/src/types/index.ts
    - src/main/frontend/src/utils/feedback.ts
    - src/main/frontend/src/utils/feedback.test.ts
    - src/main/frontend/src/hooks/useFeedback.test.ts
    - src/main/frontend/src/components/WhyBreakdown.tsx
    - src/main/frontend/src/components/WhyBreakdown.test.tsx
    - src/main/frontend/src/api/interest.ts
    - src/main/frontend/src/components/TopicRow.tsx
    - src/main/frontend/src/components/TopicRow.test.tsx
    - src/main/frontend/src/components/InterestsDialog.test.tsx
    - .planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md

key-decisions:
  - "effectNote takes the VoteKind so a removal reads '(engagement restored)' and every other kind '(replaces engagement)'; the note sits in the default branch so LEARNED_CAP, SIGN_CLAMP and WEIGHT_RANGE notes win (D-12)"
  - "The toast listing filter treats ENGAGEMENT_CAP like NONE and always lists a topic whose engagement share was replaced, even when its change rounds to +0.0 (D-13, research Pattern 5)"
  - "Why N? parts are rounded on their own and may differ from the printed weight by 0.1 (Open Question 1 adopted); the split no longer gates on learnedWeight"
  - "The plain Phase 5 row's tooltip gains the base/votes/engaged suffix whenever baseWeight is present (assumption A3); without baseWeight it stays byte-identical"
  - "LEARNED_CAP's 'at max' / 'at min' moved inside the votes part of the Interests line (assumption A2)"

patterns-established:
  - "Part labels: base first, then votes, then engaged; zero-rounded parts omitted from the label, listed in the title"

requirements-completed: [EXPL-01]

coverage:
  - id: D1
    description: "Vote toast names a replaced ('(replaces engagement)') or restored ('(engagement restored)') engagement share; a binding limit's note wins; ENGAGEMENT_CAP is never worded and never lists a topic alone"
    requirement: EXPL-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/utils/feedback.test.ts#replacedEngagementIsNamed, restoredEngagementOnRemoval, aBindingLimitWinsOverTheReplacedNote, engagementCapWithReplacementReadsReplaced, aReplacedShareIsListedEvenWhenItRoundsAway, engagementCapIsNeverWorded, engagementCapAloneIsNotListed"
        status: pass
      - kind: integration
        ref: "src/main/frontend/src/hooks/useFeedback.test.ts#engagedVoteToastSaysItReplacesEngagement, removedVoteToastSaysEngagementIsRestored"
        status: pass
    human_judgment: false
  - id: D2
    description: "'Why N?' topic rows show base + votes + engaged from the server split, omit zero-rounded parts, list every part in the tooltip, show no limit notes and still sum to the badge"
    requirement: EXPL-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/WhyBreakdown.test.tsx#votes and engagement parts (D-01..D-04): bothPartsNamedInline, votesOnlyPart, engagedOnlyPart, negativeBaseVotesPart, bothPartsRoundToZeroGivePlainRow, absentSplitFieldsGivePhase5Row, tooltipListsEveryPartIncludingZeros, noLimitNoteInWhy, rowsStillSumToTheBadge"
        status: pass
      - kind: unit
        ref: "src/main/frontend/src/components/ReadingPane.test.tsx (score row and Why panel unchanged)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Interests learned line splits votes and engaged parts, says 'at max' for a capped part, keeps the clamp/range suffixes and the 'No learned adjustment yet' / 'updates when you save' variants"
    requirement: EXPL-01
    verification:
      - kind: unit
        ref: "src/main/frontend/src/components/TopicRow.test.tsx#learned line: learnedLineShowsLearnedAndEffective, learnedLineSplitsVotesAndEngagement, engagedOnlyLine, zeroPartsAreOmitted, noLearnedAdjustmentYet, limitSuffixes, unsavedBaseEditSaysItUpdatesOnSave"
        status: pass
      - kind: integration
        ref: "src/main/frontend/src/components/InterestsDialog.test.tsx#learnedValuesAreMatchedById"
        status: pass
    human_judgment: false
  - id: D4
    description: "The new wording reads naturally in the running app (toast, Why N? panel, Interests editor)"
    requirement: EXPL-01
    verification: []
    human_judgment: true
    rationale: "Tests pin the exact strings; whether they read clearly next to the rest of the UI is a visual/UX judgment for phase UAT"

duration: 5min
completed: 2026-09-30
status: complete
---

# Phase 10 Plan 03: Explainable engagement in the toast, "Why N?" and the Interests line Summary

**The vote toast says "(replaces engagement)" or "(engagement restored)". "Why N?" rows read "Rust  80% × +13.5 (+10 +2.0 votes +1.5 engaged)". The Interests line reads "Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5", with "at max" for a capped part. Every number is a server field, only rounded for display.**

## Performance

- **Duration:** 5 min, measured from the recorded start. The context reading before that point is not counted.
- **Started:** 2026-09-30T20:38:36Z
- **Completed:** 2026-09-30T20:43:50Z
- **Tasks:** 3
- **Files modified:** 12 (11 modified, 1 created)

## Accomplishments

- The toast explains a vote that replaced an engaged article's engagement share, and a removal that restored it. Each topic gets one note, and a binding limit's note wins. The engagement cap is never worded and never lists a topic on its own (D-11..D-13, SC-4).
- "Why N?" names the votes and engaged parts of each topic's weight. The tooltip lists every part, zeros included. Rows still sum exactly to the badge (D-01..D-04, SC-1).
- The Interests learned line no longer calls engagement "votes". It shows both parts, with "at max" on the engaged part when engagement is capped and "at max" / "at min" on the votes part at the learned cap (D-14, D-15). IN-01 is closed.

## Task Commits

1. **Task 1 (tracer): the vote toast names replaced or restored engagement** - `6b90222` (feat). Tracer gate: interactive run, `end-of-phase` mode, automated-only verify. The verify was re-run and passed, then the plan moved on to Tasks 2 and 3.
2. **Task 2: "Why N?" names the votes and engaged parts** - `bebfc81` (test, RED: 8 target tests failed on the old "learned" label or title), `354d323` (feat, GREEN)
3. **Task 3: the Interests learned line split** - `06940c7` (test, RED: 6 target tests failed on "Learned from votes"), `940045c` (feat, GREEN, together with the IN-01 disposition)

**Plan metadata:** committed with this SUMMARY (docs commit).

## Files Created/Modified

- `src/main/frontend/src/types/index.ts` - `TopicEffect.engagementReplaced?`; LearnedLimit doc names D-13/D-15
- `src/main/frontend/src/utils/feedback.ts` - `effectNote(e, kind)` with the replaced/restored note; the listing filter treats ENGAGEMENT_CAP like NONE and lists replaced topics
- `src/main/frontend/src/utils/feedback.test.ts` - 5 new tests plus 2 renamed ENGAGEMENT_CAP tests; the builder takes `engagementReplaced`
- `src/main/frontend/src/hooks/useFeedback.test.ts` - the two hook-level toast cases
- `src/main/frontend/src/components/WhyBreakdown.tsx` - `TopicLabel` renders `thumbsWeight`/`engagementWeight` through `formatDelta`
- `src/main/frontend/src/components/WhyBreakdown.test.tsx` - the "votes and engagement parts (D-01..D-04)" describe
- `src/main/frontend/src/api/interest.ts` - `TopicLearned.engagementAtCap?`; the interface doc no longer says "until Phase 10"
- `src/main/frontend/src/components/TopicRow.tsx` - `LearnedLine` split, `capSuffix` removed
- `src/main/frontend/src/components/TopicRow.test.tsx` - "learned line" describe updated, 3 new tests
- `src/main/frontend/src/components/InterestsDialog.test.tsx` - the learned fixture carries the split, and the two strings are updated
- `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md` - IN-01 `fixed` (frontmatter, `open: 4`, table row)
- `.planning/phases/10-explainable-engagement-in-the-ui/deferred-items.md` - the pre-existing lint error (created)

## Decisions Made

The plan's recorded interpretations were followed as written: Open Question 1 (parts rounded on their own), A2 (the cap words go inside the votes part), A3 (the plain row's tooltip carries the parts whenever `baseWeight` is present) and research Pattern 5 (the listing rule). The key-decisions frontmatter lists them.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The InterestsDialog test pinned the old "Learned from votes" wording**
- **Found during:** Task 3 (full frontend suite)
- **Issue:** `InterestsDialog.test.tsx#learnedValuesAreMatchedById` expected "Learned from votes +4.0 · …". Its fixture also lacked the split fields, which the server always sends. The plan's verify note named this file as a likely failure, but it was not in `files_modified`.
- **Fix:** `learnedEntry` now sends `thumbsLearned: learned, engagementLearned: 0`. The expected strings are now 'Learned +4.0 (votes +4.0) · Effective weight +24.0' and 'Learned −3.0 (votes −3.0) · Effective weight +17.0'.
- **Files modified:** src/main/frontend/src/components/InterestsDialog.test.tsx
- **Verification:** the full suite passes, 383/383
- **Committed in:** 940045c

---

**Total deviations:** 1 auto-fixed (1 test pin of the old mislabel)
**Impact on plan:** None beyond the expected wording change. No scope creep.

## Issues Encountered

- The worktree sandbox would not let me write the plan-ledger and cwd sentinel files into the git dir. The ledger base (`618a640`) was kept in the session scratchpad instead. `commits: 5` is measured with `git rev-list --count 618a640..HEAD` before the SUMMARY commit.
- `npx eslint` reports one error, `react-hooks/set-state-in-effect`, in `WhyBreakdown`'s footer-collapse `useEffect`. It is pre-existing on the base, and the plan did not touch that effect. It is logged in `deferred-items.md` and not fixed here.

## TDD Gate Compliance

Tasks 2 and 3 (`tdd="true"`) each have a `test(10-03)` RED commit before their `feat(10-03)` GREEN commit. The RED runs failed on the target assertions, not on load or type errors (vitest does not type-check). Task 1 is a tracer task and was committed as a single `feat` commit.

## Verification

- `cd src/main/frontend && npx tsc -b && npx vitest run`: exit 0, 33 files, 383 tests passed
- Plan grep: `(replaces engagement)` in feedback.ts, `row.engagementWeight` in WhyBreakdown.tsx, `learned.engagementAtCap` in TopicRow.tsx: PASS
- Task acceptance greps for Tasks 1-3: PASS. TopicRow keeps the `interests-learned` / `interests-learned-value` classNames and all three clamp/range suffixes, and IN-01 is `fixed` in both places.
- No `dangerouslySetInnerHTML` was added (T-10-08). Every new string is a React text child or a `title` attribute.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 10-04 (docs/CLAUDE.md wording) can describe the final UI strings above.
- Phase UAT should look at the three surfaces in the running app (coverage D4).

---
*Phase: 10-explainable-engagement-in-the-ui*
*Completed: 2026-09-30*
