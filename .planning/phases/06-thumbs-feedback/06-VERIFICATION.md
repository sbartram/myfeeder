---
phase: 06-thumbs-feedback
verified: 2026-09-27T04:25:00Z
status: passed
score: 113/119 must-haves verified (5/5 roadmap success criteria; 108/114 plan truths; 6 backstop truths routed to human)
covered_files:

  - .planning/REQUIREMENTS.md
  - .planning/phases/06-thumbs-feedback/06-01-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-01-SUMMARY.md
  - .planning/phases/06-thumbs-feedback/06-02-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-02-SUMMARY.md
  - .planning/phases/06-thumbs-feedback/06-03-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-03-SUMMARY.md
  - .planning/phases/06-thumbs-feedback/06-04-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-04-SUMMARY.md
  - .planning/phases/06-thumbs-feedback/06-05-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-05-SUMMARY.md
  - .planning/phases/06-thumbs-feedback/06-06-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-06-SUMMARY.md
  - .planning/phases/06-thumbs-feedback/06-07-PLAN.md
  - .planning/phases/06-thumbs-feedback/06-07-SUMMARY.md
  - src/main/frontend/src/App.css
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/api/articles.ts
  - src/main/frontend/src/api/client.test.ts
  - src/main/frontend/src/api/client.ts
  - src/main/frontend/src/api/interest.ts
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

covered_digest: "v1:sha256:62194354b4d30b99f0175eda40f83bf0582bbf2c8e1a035f6468273d5dcd089c"
behavior_unverified: 0
overrides_applied: 0
coincidental_reliance_items:

  - truth: "06-02: the breakdown rows' integer points still sum exactly to total, and total and display still come from SQL"
    reason: incidental-ordering
    harden: "breakdownInputs runs the header query and the topic-row query as two statements outside any transaction (READ COMMITTED). The invariant holds only if no vote commits between them; now that votes change learned weights for every article sharing a topic, a concurrent vote (d then j) can tear it. Run findByIdWithBreakdown in a REPEATABLE_READ read-only transaction, or produce header and rows in one statement (06-REVIEW WR-03)."
  - truth: "06-03: the pressed state updates synchronously from the client intent written to ['article', id] before the request resolves"
    reason: incidental-ordering
    harden: "writeIntent/onSuccess do not cancel in-flight ['article', id] fetches, so a star/read refetch that is already running can land after the intent and erase it; a second u press in that window then reads vote 0 and re-sends an up-vote instead of removing it. Cancel queries before setQueryData and apply the newer-pending guard in onError (06-REVIEW WR-01)."
human_verification:

  - test: "Pressed-state styling and toolbar wrap: with ./gradlew bootTestRun and npm run dev, open an article and press 👍 Up; then narrow the reading pane"
    expected: "👍 Up turns the theme accent color and weight 600 while 👎 Down stays plain; the toolbar wraps to a second row instead of clipping"
    why_human: "Visual CSS outcome (.vote-btn[aria-pressed='true'], .reading-toolbar flex-wrap); jsdom tests assert aria-pressed only"
  - test: "Narrow picker walkthrough: on a scored article that matched 3+ topics press d, then Shift+D, press 2, Enter; reopen and press Esc; narrow the pane"
    expected: "Picker opens under 👎 Down with the first checkbox focused; label reads '{name} only' and the toast starts '👎 Narrowed'; Esc closes without clearing the selection; long names truncate at 12em with an ellipsis and the toolbar wraps (06-04 UI E2/long-text backstop)"
    why_human: "Popover placement, focus feel and text truncation are visual; truncation truth is tagged verification: backstop"
  - test: "Picker with a 40-character topic name at the 240px minimum width"
    expected: "The name wraps inside its 1fr grid column and never overlaps the right-aligned '{n}% match' (06-04 UI E3/long-text backstop)"
    why_human: "Layout overlap cannot be observed in jsdom; tagged verification: backstop"
  - test: "Live vote-feedback loop with scored articles: (1) press u on a multi-topic article, then i, then u again; (2) on /priority vote on a row; (3) vote on a scored article that matched no topic and click Create topic from article; (4) open Interests"
    expected: "(1) Toast like '👍 Rust +1.8', badge updates, Why row reads 'Rust  90% × +21.8 (+20 +1.8 learned)', second u toasts 'Vote removed · Rust −1.8'; (2) only that row's badge changes, nothing moves, '↻ Ranking changed — refresh' lights; (3) toast says 'No topics matched', the pane line appears, Interests opens with an empty-name draft described by the title at ±20; (4) each topic shows 'Learned from votes … · Effective weight …'"
    why_human: "End-to-end UX against a running app with real scored data (06-05 deferred human-check)"
  - test: "Effect toast wrap: trigger a toast listing three 40-character topic names with limit notes, and view the no-match strip in the narrowest reading pane"
    expected: "Toast wraps inside its 360px max-width without clipping (06-07 UI E5/long-text backstop); the 'No topics matched — Create topic from article' strip wraps with no horizontal scroll (06-05 UI E4/overflow backstop)"
    why_human: "Visual wrap/overflow; tagged verification: backstop"
  - test: "Why panel with a long topic name and a learned part"
    expected: "The '(+20 +1.8 learned)' label wraps inside the 1fr label column and never overlaps the points column (06-05 UI E6/long-text backstop)"
    why_human: "Visual layout; tagged verification: backstop"
  - test: "Concurrent votes on one article (e.g. two terminals: curl PUT {vote:1} and PUT {vote:-1} against the same id in a tight loop, then GET)"
    expected: "Exactly one article_feedback row, holding the vote of whichever request committed last; no duplicate-key error (06-01 Edge FDBK-05/concurrency backstop)"
    why_human: "No automated concurrency test exists; the truth rests on Postgres INSERT ... ON CONFLICT DO UPDATE row locking and is tagged verification: backstop"
---

# Phase 6: Thumbs Feedback Verification Report

**Phase Goal:** The user can teach the ranking with thumbs up/down and immediately see what each vote changed
**Verified:** 2026-09-27T04:25:00Z
**Status:** human_needed
**Re-verification:** No (initial verification)

## Goal Achievement

The goal is achieved in code. The backend stores a vote as one `article_feedback` row, and the learned adjustment is derived in SQL on every read. That derived CTE feeds the sort, the badge, the breakdown and the topic editor. Votes do not write `interest_topic` and never call Jev. The frontend has the 👍/👎 buttons, the u/d/Shift+D keys, the narrow picker, the effect toast, the no-match notice with a topic draft, and the learned line in the topic editor. Everything is wired and exercised by passing tests. What remains is visual/UX confirmation and six truths the plans tagged `verification: backstop`.

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can vote with buttons or `u`/`d`; same vote again removes it, the other flips it; up, down, up = a single up | ✓ VERIFIED | `FeedbackBar.tsx` renders aria-pressed 👍/👎 after ★ Star (`ReadingPane.tsx:159`). `useFeedback.ts` `press` uses `nextVote` (same key → 0 → DELETE, other → flip). `useKeyboardShortcuts.ts:159-165` has `case 'u'`/`'d'` → `press(fetchedArticle, …)`, skipping Cmd/Ctrl/Alt. Passing tests: backend `upDownUpEqualsASingleUp`, `deleteRestoresExactly`; frontend `rapidUpDownUpEndsAsASingleUp`, `pressingTheActiveVoteRemovesIt`, `pressingTheOtherVoteFlipsIt`, `uVotesUpOnTheSelectedArticle`, `dVotesDownAndDAgainRemoves` |
| 2 | After a vote, badges/scores reflect new effective weights right away (no Jev call), Priority order stays put until refresh, and a message shows the effect | ✓ VERIFIED | `blendCte` joins `eff2` (the learned model), so every badge read reflects votes. `upVoteRaisesMatchedTopicsAndTheBadge` shows badges for the voted and sibling articles go 68→70 and asserts `verify(jevApiClient, never()).judge`. Outside Priority, `['articles']` and other by-id articles are invalidated. On Priority, only the voted row is patched (`patchPriorityArticle`) and `rankingChanged` is set; the Priority key is never invalidated (`priorityVotePatchesOnlyTheVotedRow`). The toast prints server `after − before` (`formatVoteToast`; `upVoteListsTopThreeThenMore`, `upVoteSavesAndShowsTheEffect`). Note: on /priority, other rows keep their served badges by locked decision D-06, which keeps the frozen list from looking mis-sorted |
| 3 | Learned adjustment never past ±20, never flips base sign; undo reverses exactly; topic editor shows base and learned separately | ✓ VERIFIED | `LEARNED_CTE` clamps `learned` to ±`learnedCap` (20) and `eff2` sign-clamps within ±50. Tests: `learnedIsCappedAtTwenty`, `positiveBaseNeverGoesBelowZero`, `negativeBaseNeverGoesAboveZero`, `effectiveWeightStaysWithinFifty`, `deletingTheVoteRestoresExactly`, `deleteRestoresExactly`. `GET /api/interest/topics/learned` → `TopicLearned`, and `TopicRow` `LearnedLine` keeps the slider on base and adds "Learned from votes … · Effective weight …" (`learnedLineShowsLearnedAndEffective`, `learnedValuesAreMatchedById`, `learnedEndpointMatchesTheVoteEffects`) |
| 4 | A vote on an article that matched no topics tells the user and offers to create a topic | ✓ VERIFIED | The server returns `scored:true, effects:[]` (`scoredNoMatchVoteIsStoredWithNoEffects`), and the toast reads "👍 Saved · No topics matched" (`noMatchVoteToastSaysNoTopicsMatched`). `FeedbackNotice.tsx` shows "No topics matched — Create topic from article", which calls `onCreateTopic` → `App.tsx:130` `setInterestsDraft` + `setInterestsOpen(true)`. `InterestsDialog` seeds one draft with the title and ±20 (`draftRowIsSeededLastWithTheTitle`, `draftIsSeededOnce`, `atMaxShowsTheNoticeInsteadOfADraft`) |
| 5 | A thumbs-down on a multi-topic article lets the user choose which topics to penalize | ✓ VERIFIED | `canNarrow` (👎 and ≥2 matched topics, or already narrowed) shows the `.narrow-toggle`, and Shift+D opens `NarrowPicker`. Apply sends `topicIds` (a subset) or null (all topics). The server validates the subset (`picksOf`) and the CTE honors `topics_narrowed` plus the picks. Tests: `narrowedDownVoteMovesOnlyPickedTopics` (API), `narrowedVoteMovesOnlyPickedTopics` (SQL), `applyingASubsetNarrowsTheVote`, `shiftDOpensThePickerWhenNarrowable`, `pickerKeysNeverReachTheDocument`, `flipToUpClearsNarrowing` |

**Roadmap score:** 5/5

### Plan must-have truths (merged)

| Plan | Truths | Verified | Backstop → human | Notes |
|------|--------|----------|------------------|-------|
| 06-01 backend vote path | 17 | 16 | 1 (concurrency serialization) | Validation, 404/415, idempotency, D-03/D-18, D-04, LearnedLimit boundaries and no V7 migration are each covered by named passing tests (ArticleFeedbackServiceTest 14/14, FeedbackApiIntegrationTest 18/18, InterestScoreQueriesTest 36/36) |
| 06-02 vote state + Why weights | 5 | 5 | 0 | `feedback` is set only in `findByIdWithBreakdown` and omitted from lists via `@JsonInclude(NON_NULL)` (`listItemsOmitFeedback`, `removedVoteHasNoFeedbackKey`). `baseWeight`/`learnedWeight` on TOPIC rows (`breakdownLearnedIsTheAppliedPart`). The sum-to-total truth is flagged coincidental-reliance (see below) |
| 06-03 reading-pane votes | 15 | 15 | 0 | Scope serialization, per-view cache policy, error revert and copy (`failedVoteRevertsAndShowsTheSaveCopy`, `rejectedNarrowingShowsTheNarrowCopy`), no side effects (`voteHasNoSideEffects`). The pressed-state truth is flagged coincidental-reliance (see below) |
| 06-04 picker + keys | 23 | 21 | 2 (long-text truncation, 40-char wrap) | Labels, focus return, outside close, key isolation and overlay rows (`ShortcutOverlay.tsx:13-14`) |
| 06-05 notice, draft, learned line, Why | 37 | 35 | 2 (no-match strip overflow, Why long-text) | Draft one-shot, at-max notice, learned suffixes, error note, rows survive refetch |
| 06-06 learned endpoint + docs | 5 | 5 | 0 | `@GetMapping("/topics/learned")`; the CLAUDE.md "Thumbs feedback" paragraph and the route list edit exist in the working tree (intentionally uncommitted, per the orchestrator) |
| 06-07 toast rules | 12 | 11 | 1 (toast long-text wrap) | Top-3 + "+N more", limit notes, unscored/no-match/under-0.1 copy, `role="status"` on `.toast-container` (`Toast.tsx:37`), `apiDeleteJson` ApiError |

### Required Artifacts

`gsd-tools verify.artifacts` returned all artifacts passing for every plan: 06-01 6/6, 06-02 2/2, 06-03 4/4, 06-04 3/3, 06-05 4/4, 06-06 3/3, 06-07 3/3. I also read the key files and they are substantive: `InterestScoreQueries.LEARNED_CTE`, `ArticleFeedbackStore` (upsert ON CONFLICT plus picks), `ArticleFeedbackService` (@Transactional vote/clear with before/after), `LearnedLimit`, `FeedbackResult`, `TopicLearned`, `ArticleFeedback`, `useFeedback.ts`, `utils/feedback.ts`, `FeedbackBar.tsx`, `NarrowPicker.tsx`, `FeedbackNotice.tsx`, `TopicRow.LearnedLine`, `WhyBreakdown`, `feedbackStore.ts`. No V7 migration exists (the migrations directory ends at V6).

### Key Link Verification

`gsd-tools verify.key-links` returned all links verified: 06-01 3/3, 06-02 2/2, 06-03 3/3, 06-04 3/3, 06-05 3/3, 06-06 2/2, 06-07 2/2. I confirmed these by hand:

| From | To | Via | Status |
|------|----|-----|--------|
| ArticleController PUT/DELETE `/{id}/feedback` | ArticleFeedbackService.vote/clear | direct delegate (PUT consumes JSON) | WIRED |
| ArticleFeedbackService | InterestScoreQueries.topicWeights / allTopicWeights | before/write/after | WIRED |
| InterestScoreQueries.learnedSql | MyfeederProperties blend learnRate/learnedCap | named params (application.yaml 2 / 20 in main and test) | WIRED |
| ArticleService.findByIdWithBreakdown | ArticleFeedbackStore.find | sets `feedback` | WIRED |
| ReadingPane | FeedbackBar / FeedbackNotice | rendered after Star / after the toolbar | WIRED |
| useFeedback | articlesApi.setFeedback/clearFeedback, patchPriorityArticle, formatVoteToast | mutationFn / onSuccess | WIRED |
| useKeyboardShortcuts | useVoteFeedback.press, feedbackStore.setNarrowOpen | u/d, Shift+D | WIRED |
| FeedbackNotice → App | InterestsDialog `draft` | `setInterestsDraft` + `setInterestsOpen` | WIRED |
| InterestsDialog TopicsSection | useLearnedTopics → `GET /interest/topics/learned` | `learnedById` per row | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Badge / Why rows | interestScore, rows[].baseWeight/learnedWeight | blend CTE ⟶ eff2 ⟶ article_feedback + article_topic_score | Yes (SQL) | ✓ FLOWING |
| Effect toast | effects[].before/after/learned/limit | ArticleFeedbackService.topicWeights before/after the write | Yes | ✓ FLOWING |
| FeedbackBar pressed state / narrow label | article.feedback | ArticleFeedbackStore.find via GET /api/articles/{id}, plus client intent | Yes | ✓ FLOWING |
| TopicRow learned line | TopicLearned | allTopicWeights over eff2 | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend phase suites (learned model, vote API, validation, breakdown, learned endpoint, dev-profile parity) | `./gradlew test -x npmBuild -x npmInstall --tests '*FeedbackApiIntegrationTest' '*ArticleFeedbackServiceTest' '*InterestScoreQueriesTest' '*InterestControllerTest' '*ArticleControllerTest' '*ScoreBreakdownsTest' '*ArticleServiceTest' '*DevProfileConfigTest'` (SPRING_AI_TYPESAFE_* / SPRING_PROFILES_ACTIVE unset) | 146 tests: 30+18+12+4+36+14+16+16, 0 failures, 0 errors | ✓ PASS |
| Frontend suite | `npx vitest run` | 28 files, 314/314 passed | ✓ PASS |
| Type-check | `npx tsc -b` | exit 0 | ✓ PASS |
| Full backend suite | orchestrator evidence (not re-run in full) | 519 tests, 0 failures, 2 skipped (live-API) | ✓ PASS (relied on) |

### Probe Execution

Step 7c: SKIPPED. No `scripts/*/tests/probe-*.sh` exists, and no plan declares a probe.

### Prohibitions (all `verification: test`)

| Prohibition | Enforcement evidence | Disposition |
|-------------|----------------------|-------------|
| No interest_topic write from the vote path, the learned read or the breakdown read | `topicWeight(rust) == 20` asserted after vote/flip/delete in FeedbackApiIntegrationTest; grep finds no `UPDATE/INSERT interest_topic` in the vote/learned code | Enforced |
| No Jev call on a vote | `verify(jevApiClient, never()).judge(...)` in FeedbackApiIntegrationTest; no Jev import in ArticleFeedbackService/Store | Enforced |
| No Priority invalidate/refetch/reorder on a vote | `priorityVotePatchesOnlyTheVotedRow` | Enforced |
| No mark-read/advance/selection change | `voteHasNoSideEffects`, `voteLeavesReadAndStarredAlone`, keyboard tests | Enforced |
| The client prints only server numbers | `formatVoteToast`/`LearnedLine`/WhyBreakdown only subtract after − before or round server values; covered by the toast and learned-line rule tests | Enforced |
| Create topic from article saves nothing and calls no Jev | `draftCountsAsUnsavedAndCloseAsksToDiscard` (asserts no non-GET call) | Enforced |
| Protected user files (CLAUDE.md, .claude/CLAUDE.md, .envrc, .planning/config.json) never committed | `git log --name-only 9d2d91d..HEAD` lists none of them; `git status` shows all four ` M` | Enforced |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| FDBK-01 | 06-01, 02, 03, 04 | Thumbs up/down via buttons and u/d; toggle/flip | ✓ SATISFIED | SC1 evidence |
| FDBK-02 | 06-01, 03 | A vote adjusts matched topics' effective weight; ranking/badges immediate; no Jev | ✓ SATISFIED | SC2 evidence |
| FDBK-03 | 06-01 | Learned capped ±20, never flips sign; undo exact | ✓ SATISFIED | SC3 evidence |
| FDBK-04 | 06-01, 02, 03, 07 | The user sees the vote's effect | ✓ SATISFIED | effect toast rules and tests |
| FDBK-05 | 06-01, 05, 07 | No-match vote tells the user and offers topic creation | ✓ SATISFIED | SC4 evidence |
| FDBK-06 | 06-01, 02, 04 | Choose which topics to penalize on a multi-topic 👎 | ✓ SATISFIED | SC5 evidence |
| FDBK-07 | 06-05, 06 | Topic editor shows base and learned separately | ✓ SATISFIED | learned endpoint + LearnedLine (see WR-04 staleness warning) |

REQUIREMENTS.md maps no orphaned IDs to Phase 6. All 7 IDs are claimed by plans and all are satisfied.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (57 phase-modified src files) | — | TBD/FIXME/XXX/TODO/HACK | none found | — (`PROFILE_PLACEHOLDER` in InterestsDialog.tsx is a pre-existing constant name, not a marker) |
| `hooks/useFeedback.ts` | 24-26, 62, 77-81 | Optimistic write without `cancelQueries`; onError revert lacks the newer-pending guard (06-REVIEW WR-01) | ⚠️ Warning | A rare race (star refetch in flight, then a quick u) can erase the intent and make a second u re-send an up-vote. SC1 holds on the normal path |
| `service/ArticleFeedbackService.java` | 94-108 + `utils/feedback.ts:107-110` | Effects cover every matched topic, including unpicked ones of a narrowed vote (WR-02) | ⚠️ Warning | A narrowed 👎 toast can list an untargeted, already-capped topic as "+0.0 (learned at max +20)", which is misleading copy in an edge case. The effect is still shown |
| `service/ArticleService.java` | 35-47 | The breakdown is read in two statements outside a transaction (WR-03) | ⚠️ Warning | A concurrent vote can make the Why rows not sum to the badge until the next refetch. Recorded as coincidental-reliance |
| `hooks/useInterest.ts` | 141-150 | Re-score does not invalidate `['interest','learned']` (WR-04) | ⚠️ Warning | After Re-score inside the Interests dialog, the learned line can be stale until refetch/reopen. FDBK-07 holds otherwise |
| various | — | 06-REVIEW IN-01..IN-05 (created_at not bumped, ±50 duplicated in SQL/Java, possible "−0.0" in `(now …)`, picker digits ignore modifiers, LearnedLimit looks only at the after state) | ℹ️ Info | Cosmetic or edge cases; none defeats a must-have |

I weighed each of WR-01..WR-04 against the must-haves. None contradicts a must-have on its normal path: each is a concurrency, edge-case or staleness issue. They stay warnings and are suitable for a gap-closure or quick follow-up at the developer's discretion.

### Human Verification Required

1. **Pressed-state styling and toolbar wrap.** Press 👍 Up. It should show the accent color at weight 600 while 👎 stays plain. Narrow the pane: the toolbar should wrap.
2. **Narrow picker walkthrough.** Press d, Shift+D, 2, Enter on a 3+ topic article. Expect "{name} only" and "👎 Narrowed". Esc should keep the selection. Long names should truncate with an ellipsis (backstop).
3. **Picker at 240px with a 40-character name.** The name should wrap with no overlap of the match % (backstop).
4. **Live vote-feedback loop with scored articles.** Check the toast, the badge, the Why learned label, removal, the Priority patch plus the "Ranking changed" hint, the no-match notice, the draft, and the learned lines (06-05 deferred check).
5. **Toast and no-match strip wrap.** Three long names with notes should wrap inside the 360px toast. The no-match strip should have no horizontal scroll (backstop x2).
6. **Why panel long-text wrap** (backstop).
7. **Concurrent votes on one article.** Expect exactly one row holding the last committed vote and no PK error (backstop; no automated concurrency test).

### Gaps Summary

There are no blocking gaps. All 5 roadmap success criteria and all 7 FDBK requirements are backed by code that is present, wired and data-flowing, and by named tests that pass: 146 targeted backend tests re-run here, the full frontend suite (314) and tsc. The status is `human_needed` for two reasons: the executors flagged visual/interaction checks for end-of-phase UAT, and the plans tagged six truths `verification: backstop` (layout/wrap truths, plus a concurrency truth with no automated test). The four code-review warnings are real but non-blocking. WR-01 and WR-03 are recorded as coincidental-reliance advisories on two verified truths, and WR-02 and WR-04 are copy and staleness edge cases.

---

_Verified: 2026-09-27T04:25:00Z_
_Verifier: Claude (gsd-verifier)_
