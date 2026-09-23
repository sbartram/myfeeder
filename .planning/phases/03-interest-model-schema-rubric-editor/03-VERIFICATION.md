---
phase: 03-interest-model-schema-rubric-editor
verified: 2026-09-23T21:23:47Z
status: human_needed
score: 5/5 roadmap success criteria verified (all test-backed plan truths verified; 5 backstop truths routed to human)
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-01-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-01-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-02-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-02-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-03-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-03-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-04-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-04-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-05-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-05-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-06-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-06-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-07-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-07-SUMMARY.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-08-PLAN.md
  - .planning/phases/03-interest-model-schema-rubric-editor/03-08-SUMMARY.md
  - CLAUDE.md
  - src/main/frontend/src/App.css
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/api/client.test.ts
  - src/main/frontend/src/api/client.ts
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/components/SettingsDialog.test.tsx
  - src/main/frontend/src/components/SettingsDialog.tsx
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/components/TopicRow.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/queryClient.test.ts
  - src/main/frontend/src/queryClient.ts
  - src/main/frontend/src/utils/interest.test.ts
  - src/main/frontend/src/utils/interest.ts
  - src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - src/main/java/org/bartram/myfeeder/controller/InterestController.java
  - src/main/java/org/bartram/myfeeder/controller/InterestPreviewController.java
  - src/main/java/org/bartram/myfeeder/controller/InterestStatusController.java
  - src/main/java/org/bartram/myfeeder/controller/ProfileUpdateRequest.java
  - src/main/java/org/bartram/myfeeder/controller/TopicPreviewRequest.java
  - src/main/java/org/bartram/myfeeder/controller/TopicRequest.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/model/InterestProfile.java
  - src/main/java/org/bartram/myfeeder/model/InterestTopic.java
  - src/main/java/org/bartram/myfeeder/repository/InterestProfileRepository.java
  - src/main/java/org/bartram/myfeeder/repository/InterestTopicRepository.java
  - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
  - src/main/java/org/bartram/myfeeder/service/InterestPreviewService.java
  - src/main/java/org/bartram/myfeeder/service/InterestQuestions.java
  - src/main/java/org/bartram/myfeeder/service/InterestService.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/TopicPreviewResponse.java
  - src/main/resources/db/migration/V6__interest_scoring.sql
  - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestPreviewControllerTest.java
  - src/test/java/org/bartram/myfeeder/integration/CalibrationStats.java
  - src/test/java/org/bartram/myfeeder/integration/CalibrationStatsTest.java
  - src/test/java/org/bartram/myfeeder/integration/InterestCalibrationSpikeTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestTopicRepositoryTest.java
  - src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestJudgeRequestTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestPreviewServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
covered_digest: "v1:sha256:d8dbfe7b3d90f9438a4e8329a474b059a532dafbbf353c235857436bd0e7ba0f"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Visual/UAT pass from 03-07's end-of-phase human-check: ./gradlew bootTestRun + npm run dev, open an article, Settings -> 'Edit interests…'. (1) save a profile, reload, reopen, text is still there; (2) add a topic, move slider/type number, try 51; (3) type 'not about crypto'; (4) with no key, see 'Scoring isn't set up yet.' and Preview disabled with the no-key reason; (6) switch through all 6 themes and narrow the window below 600px"
    expected: "Everything matches 03-UI-SPEC.md. Colors follow each theme, weight signs show +/− characters as well as color, row line 2 wraps with actions right-aligned below 600px, nothing scrolls horizontally"
    why_human: "Layout, theme contrast and media-query wrapping cannot be seen in jsdom; App.tsx Settings->Interests hand-off is type-checked and read but not rendered in a test"
  - test: "Live topic preview with a real key: export MYFEEDER_TYPESAFE_API_KEY before bootTestRun, open an article, click 'Preview topic' on a draft and on a saved topic"
    expected: "One POST /api/interest/preview per click; the row shows 'Match N% → counts M% × +W = +P pts' (or the muted 'No match' line); changing only the weight recomputes without a new request. Note: the production 5s Jev timeout may be tight on a cold call (03-CALIBRATION Note 1); a timeout should show 'Jev is unavailable right now'"
    why_human: "The preview endpoint has only been exercised against mocks and keyless (503). The shared builders were exercised live by the calibration spike, but not through /api/interest/preview; every live call is billed"
  - test: "UI backstop E6 (03-05): at a 360px-wide viewport with not-configured + paused + cold-start notices all showing"
    expected: "Notices stack 8px apart in order not configured -> paused -> cold start, wrap within the dialog, no horizontal scroll"
    why_human: "verification: backstop truth; wrapping at a viewport width is not observable in jsdom"
  - test: "UI backstop E4 long-text (03-06): paste a 500-character description into a topic row"
    expected: "It stays on one line inside its input, scrolls horizontally within it, and does not widen the row or dialog"
    why_human: "verification: backstop truth; input overflow is layout-only"
  - test: "UI backstop E5 overflow (03-07): produce a preview result in a narrow row"
    expected: "The math line wraps to a second line inside the result strip; no truncation, no horizontal scroll"
    why_human: "verification: backstop truth; layout-only"
  - test: "Acknowledge backstop truth (03-03): the 25-topic cap is count-then-insert in one @Transactional method and two simultaneous creates at 24 topics are NOT serialized"
    expected: "Accepted as a single-user limitation (code comment in InterestService.createTopic documents it)"
    why_human: "verification: backstop truth that asserts a known limitation; presence of the @Transactional count-then-insert is not evidence per the non-inferable rule"
  - test: "Decide whether CR-01 (ArticleStateBuilder.truncate has no lower bound on the whitespace search) is fixed now or before Phase 4"
    expected: "Either a /gsd-quick fix plus a CJK/URL test, or an explicit decision to carry it into Phase 4"
    why_human: "Verified defect (probe below) in the builder the preview and the Phase 4 scorer share. It does not falsify any must-have as written, so it is a WARNING, but it silently degrades what Jev judges for whitespace-sparse summaries"
---

# Phase 3: Interest Model, Schema & Rubric Editor Verification Report

**Phase Goal:** The user can describe what they care about in a free-text profile and a weighted topic rubric, and can check how a topic judges a real article, on top of the complete interest-scoring schema
**Verified:** 2026-09-23T21:23:47Z
**Status:** human_needed
**Re-verification:** No (initial verification)

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can write and edit a free-text interest profile of up to 2,000 characters, with writing guidance shown in the editor, and it persists across reloads | ✓ VERIFIED | `InterestService.updateProfile` enforces null/2,000 (UTF-16) with fixed-text errors; `InterestsDialog.ProfileEditor` renders the 4 `PROFILE_TIPS`, `maxLength={2000}`, the example placeholder and the counter. Tests I ran: `InterestApiIntegrationTest.profilePutThenGetPersists` (real Postgres), `InterestServiceTest.updateProfileRejects2001Characters/AcceptsExactly2000/CountsUtf16Units`, `InterestControllerTest.putProfileTooLongIs400WithDetail`, vitest `loadsProfileAndSavesItThroughTheApi`, `showsTipsPlaceholderAndCounter`, `counterAtLimitShowsLimitReached` |
| 2 | User can add, edit and delete up to 25 topics, each with a description and a signed weight between −50 and +50 (default +20); out-of-range weights and a 26th topic are rejected | ✓ VERIFIED | Enforced at three layers: DB `CHECK (weight BETWEEN -50 AND 50)`, `DEFAULT 20` (V6); service `validate()` + `MAX_TOPICS` count check; UI `parseWeight` never clamps, Save disabled, "+ Add topic" disabled at 25. Tests: `V6InterestScoringMigrationTest.rejectsTopicWeightAbove50/BelowMinus50`, `InterestServiceTest.createTopicRejectsTwentySixth`, `createTopicDefaultsWeightTo20AndVersionTo1`, `InterestApiIntegrationTest.topicCrudRoundTripsThroughTheDatabase`, `weightOutOfRangeIs400BeforeTheDatabase`, vitest `addsADraftTopicAndSavesItInPlace`, `savedRowEditSendsPut`, `deleteConfirmsInlineThenDeletes`, `weightOutOfRangeShowsErrorAndDisablesSave`, `addTopicDisabledAt25WithTitle` |
| 3 | Entering a negated topic description shows a warning that suggests a positive description with a negative weight | ✓ VERIFIED | `utils/interest.ts isNegated` (word list incl. ’ normalization); `TopicRow` 400 ms debounce -> `role="status"` warning with the UI-SPEC copy; never touches Save or text. Tests: `isNegatedMatchesTheWordList`, `isNegatedNormalizesSmartQuotes`, `negationWarningAppearsAfterDebounceAndNeverBlocks`, `negationWarningClears` |
| 4 | User can preview a draft topic against the article open in the reading pane and see its match result (one Jev call) before saving it | ✓ VERIFIED (live call is a human item) | `InterestPreviewService.preview` validates, then one `jevApiClient.judge(state, Map.of(key, InterestQuestions.topic(trimmed)))` with `ArticleStateBuilder.build` (same builders as Phase 4, D-12); no write collaborators. UI `TopicRow.handlePreview` sends `{articleId: uiStore.selectedArticleId, description, topicId: row.id}` (null for drafts) and renders the hinge math. Tests: `previewSendsExactlyOneNoulUnderTheDraftKey`, `previewHasNoWriteCollaborators`, `previewPropagatesJevExceptionsWithoutRetrying`, `keylessPreviewIs503ThroughTheFullStack`, vitest `draftPreviewSendsNullTopicId`, `previewSendsTheRowAndShowsTheMath`, `failedPreviewIsNeverRetried`, `previewTriggersNoOtherRequests` |
| 5 | The interest settings UI shows a "not configured" notice when no API key is set, and a "cold start" prompt when the profile is empty and there are no topics | ✓ VERIFIED | `GET /api/interest/status` = `{isConfigured(), circuitBreaker("jev").getState().name(), interestService.isColdStart()}`; `InterestNotices` renders from server status only (D-05). Tests: `InterestApiIntegrationTest.statusReportsKeylessClosedAndColdStart`, `InterestServiceTest.isColdStartTrueOnlyForBlankProfileAndNoTopics`, `InterestStatusServiceTest.*`, vitest `notConfiguredNoticeShownAndProfileStillSaves`, `noticesStackInFixedOrder`, `coldStartFocusesTheTextareaAndClearsAfterSave`, `showsPausedNoticeForForcedOpenBreaker` |

**Score:** 5/5 roadmap truths verified (0 present-but-behavior-unverified)

### Plan must-haves (merged)

All test-backed truths from the eight PLAN frontmatters were checked against code and against the test names in the fresh run. Every one maps to code I read and a passing test, with these notes:

| Plan | Result | Notes |
|------|--------|-------|
| 03-01 schema/entities/repos | ✓ all 13 | V6 creates all six tables; singleton `CHECK (id = 1)` + seed; status CHECK; cascades; `topics_narrowed` default false; no vote-direction CHECK. 16 migration tests + 4 repo tests pass |
| 03-02 builders + isConfigured | ✓ all 12 | Truncation truth is satisfied **as written** ("cut at the last whitespace"), but see CR-01: the spec itself lacks a lower bound |
| 03-03 service + REST | ✓ 15; 1 backstop -> human | Concurrency backstop (25-cap not serialized) is a documented limitation |
| 03-04 status + preview + handlers + CLAUDE.md | ✓ all 13 | Committed CLAUDE.md names V6, `/api/interest`, the Jev handler mappings and `org.springframework.data.relational.core.mapping`; the user's CLAUDE.md hunks are still uncommitted in the working tree |
| 03-05 dialog/profile/notices | ✓ 25; 1 backstop -> human | E6 360px wrap |
| 03-06 topic rows | ✓ 30; 1 backstop -> human | E4 500-char input overflow |
| 03-07 preview UI | ✓ 18; 1 backstop -> human | E5 narrow math-line wrap |
| 03-08 calibration | ✓ all 9 | Spike gated by method-level `@EnabledIfEnvironmentVariable(named = "JEV_CALIBRATION", matches = "true")`, reported skipped in my run; v2 wording in `InterestQuestions`; 03-CALIBRATION.md has `## Verdict`; input/report not tracked by git |

### Prohibitions

| Prohibition | Tier | Disposition | Evidence |
|-------------|------|-------------|----------|
| No profile text in article state (03-02) | test | ✓ enforced | `ArticleStateBuilder.build(feedTitle, article)` has no profile parameter; `InterestJudgeRequestTest.builtRequestReachesTheWireInScorerOrder` asserts wire state `{feed,title,summary}` |
| No echo of user text in validation errors/logs (03-03) | test | ✓ enforced | `InterestServiceTest.validationMessagesNeverEchoInput` |
| Preview persists nothing (03-04) | test | ✓ enforced | `previewHasNoWriteCollaborators`; constructor has only ArticleRepository, FeedRepository, JevApiClient |
| No TypeSafe message/body in ProblemDetails (03-04) | test | ✓ enforced | `jevErrorDetailsNeverEchoExceptionText`; handlers use fixed strings |
| User CLAUDE.md hunks not committed (03-04) | test | ✓ enforced | `git diff CLAUDE.md` still shows the user's hunks (AI line, service/integration lines, V5 line) as uncommitted |
| Profile editing not blocked without key (03-05) / topics not blocked (03-06) | test | ✓ enforced | `notConfiguredNoticeShownAndProfileStillSaves`, `topicsStayEditableWithoutAKey` |
| No persisted prefs for the dialog (03-05) | test | ✓ enforced | no file under `src/stores/` changed in 7152208..HEAD |
| Negation never blocks/rewrites (03-06) | test | ✓ enforced | `negationWarningAppearsAfterDebounceAndNeverBlocks` |
| No auto-retry / no unclicked preview; no uiStore write, no invalidation (03-07) | test | ✓ enforced | `failedPreviewIsNeverRetried`, `previewTriggersNoOtherRequests` (see IN-05: 50 ms sleep makes the negative weaker); `usePreviewTopic` has no `onSuccess`; the only `useUIStore` use is a `selectedArticleId` selector |
| Default test run never calls live TypeSafe (03-08) | test | ✓ enforced | spike reported `skipped=1` in my run |
| Calibration input/report not committed, no personal text in findings (03-08) | test | ✓ enforced | `git ls-files` shows no calibration input/report; 03-CALIBRATION.md holds titles, topic keys and numbers |
| Judgment-tier: V6 decision checkpoint (03-01), billed spike go-ahead (03-08), user's uncommitted edits untouched (all) | judgment | resolved, recorded | 03-01 SUMMARY records the Task 1 decision; 03-CALIBRATION.md records "run it"; `git status` shows .envrc, CLAUDE.md, .claude/CLAUDE.md, .planning/config.json still modified-uncommitted |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `db/migration/V6__interest_scoring.sql` | ✓ VERIFIED | 6 tables, CHECKs and cascades as specified |
| `model/InterestProfile.java`, `model/InterestTopic.java` | ✓ VERIFIED | `@Table` from relational mapping; `version` plain int |
| `repository/InterestProfileRepository.java`, `InterestTopicRepository.java` | ✓ VERIFIED | `findAllOrdered` = `ORDER BY id` |
| `service/InterestQuestions.java`, `ArticleStateBuilder.java` | ✓ VERIFIED (CR-01 warning) | Pure, used by preview and spike |
| `service/InterestService.java` | ✓ VERIFIED | limits, versions, `isColdStart()` |
| `service/InterestStatusService.java`, `InterestPreviewService.java` + controllers | ✓ VERIFIED | wired, no stubs |
| `controller/GlobalExceptionHandler.java` | ✓ VERIFIED | 4 Jev handlers, fixed text |
| `frontend api/interest.ts`, `hooks/useInterest.ts`, `utils/interest.ts` | ✓ VERIFIED | all 8 endpoints; `inlineError` meta on mutations |
| `components/InterestsDialog.tsx`, `TopicRow.tsx` | ✓ VERIFIED | full editor, notices, guard, preview |
| `queryClient.ts`, `api/client.ts` (ApiError) | ✓ VERIFIED | `App.tsx` uses `createQueryClient()` |
| Calibration spike, stats, 03-CALIBRATION.md | ✓ VERIFIED | gated; findings recorded |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| SettingsDialog "Edit interests…" | App MainLayout | `onOpenInterests` -> `setSettingsOpen(false); setInterestsOpen(true)` | ✓ WIRED |
| InterestsDialog / TopicRow | `/api/interest/*` | hooks -> `interestApi.*` -> `apiGet/Put/Post/Delete` | ✓ WIRED |
| InterestController | InterestService | constructor injection, no try/catch | ✓ WIRED |
| InterestStatusService | `InterestService.isColdStart()` | delegated, not reimplemented | ✓ WIRED |
| InterestPreviewService | InterestQuestions.topic + ArticleStateBuilder.build + `jevApiClient.judge` | single call | ✓ WIRED |
| JevApiClientImpl.requireConfigured | `isConfigured()` | single rule, no resilience annotations | ✓ WIRED |
| TopicsSection | `uiStore.selectedArticleId` | read-only selector + `useArticle` | ✓ WIRED |
| TopicRow | `hinge`/`formatSigned`/`formatPreviewText`, `ApiError` | preview math and failure copy | ✓ WIRED |
| Spike | `InterestQuestions.forRubric`, `ArticleStateBuilder.build` | production builders | ✓ WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| ProfileEditor | `profile.profileText` | `GET /api/interest/profile` -> `profileRepository.findById(1)` | yes (Postgres singleton) | ✓ FLOWING |
| TopicsSection rows | `topics.data` | `GET /api/interest/topics` -> `findAllOrdered()` | yes | ✓ FLOWING |
| InterestNotices | `status.data` | `GET /api/interest/status` -> key check, breaker registry, DB counts | yes | ✓ FLOWING |
| TopicPreviewResult | `noul` | `POST /api/interest/preview` -> `judge()` response | yes (mocked in tests, live call is a human item) | ✓ FLOWING |
| Preview target line | `article.data.title` | `useArticle(selectedArticleId)` | yes | ✓ FLOWING (WR-04: error state shows "loading…") |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase backend classes pass on real Postgres | `./gradlew test --tests '*Interest*' --tests '*V6InterestScoringMigrationTest' --tests '*ArticleStateBuilderTest' --tests '*CalibrationStatsTest' --tests '*JevApiClientImplTest' -x npmBuild` | BUILD SUCCESSFUL; fresh XML timestamps 21:21–21:22Z; 121 tests, 0 failures, spike 1 skipped | ✓ PASS |
| Phase frontend tests | `npx vitest run` on InterestsDialog, TopicRow, utils/interest, queryClient, api/client, SettingsDialog tests | 6 files, 70 tests passed | ✓ PASS |
| Type check | `npx tsc -b` | clean | ✓ PASS |
| CR-01 reproduction | reflective call of `ArticleStateBuilder.truncate` on `"标题 " + "字"×2000` and `"Link: https://example.com/" + "a"×2000`, max 1500 | `'标题…'` (3 chars) and `'Link:…'` (6 chars) | ✗ defect confirmed (WARNING, see below) |

### Probe Execution

No `scripts/*/tests/probe-*.sh` exist and no plan declares a probe. The calibration spike is a gated billed test, not a probe. It was not re-run. Its skip in the default run was confirmed.

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| INT-01 | 03-01, 03-03, 03-05 | Free-text profile ≤2,000 with guidance | ✓ SATISFIED | SC1 |
| INT-02 | 03-01, 03-03, 03-06 | ≤25 topics, positive description, weight −50..+50 default +20 | ✓ SATISFIED | SC2 |
| INT-03 | 03-06 | Negation warning suggesting a negative weight | ✓ SATISFIED | SC3 |
| INT-04 | 03-02, 03-04, 03-07, 03-08 | Preview a topic against the open article (one call) | ✓ SATISFIED (live call is a human item) | SC4 |
| INT-06 | 03-03, 03-04, 03-05, 03-07 | Not-configured notice and cold-start prompt | ✓ SATISFIED | SC5 |

No orphaned requirements: REQUIREMENTS.md maps exactly INT-01/02/03/04/06 to Phase 3. INT-05 is mapped to Phase 4.

### Anti-Patterns Found

No TBD/FIXME/XXX markers in phase-modified files. The only `PLACEHOLDER` match is the `PROFILE_PLACEHOLDER` constant, which is legitimate.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `service/ArticleStateBuilder.java` | 56-74 | CR-01: the whitespace search in `truncate` has no lower bound | ⚠️ Warning | Reproduced: CJK or long-URL summaries shrink to a few characters. The preview and the Phase 4 scorer share this builder, so they would judge those articles almost from the title alone. No must-have is falsified (the spec says "last whitespace"). The calibration set (English) was not affected. Fix before Phase 4 |
| `components/TopicRow.tsx` / `InterestsDialog.tsx` | 260-267, 330-339 | WR-01: `markSaved` overwrites text typed during an in-flight save | ⚠️ Warning | Short window; data loss of typed text |
| `components/TopicRow.tsx` | 383-386 | WR-02: "Discard draft" is enabled while create is pending | ⚠️ Warning | The topic is created on the server but hidden until reopen |
| `components/InterestsDialog.tsx` + `hooks/useKeyboardShortcuts.ts` | 59; 49-56, 170-177 | WR-03: global shortcuts are active behind the aria-modal dialog (confirmed: only INPUT/TEXTAREA/SELECT targets are ignored; Tab is preventDefault-ed) | ⚠️ Warning | Keyboard users can't Tab through dialog buttons. j/k silently change the preview target. The same app-wide pattern affects the other dialogs |
| `components/InterestsDialog.tsx` | 361-369 | WR-04: a failed `useArticle` shows "loading article…" with no end | ⚠️ Warning | Misleading target line |
| `components/TopicRow.tsx` | 71-84 | WR-05: 400/404 from the app's own validation are shown as Jev failures | ⚠️ Warning | Wrong advice. The 400/422 copy was specified by the plan, so this is a spec-level issue |
| `components/InterestsDialog.tsx` | 31 | Topic help text still says "primarily about" after the v2 wording ("substantially about") | ℹ️ Info | Logged in deferred-items.md |
| `InterestService.java` | 44-58, 90-106 | IN-01: version bumps are read-modify-write | ℹ️ Info | Phase 4 staleness key under concurrent tabs |

### Human Verification Required

1. **Visual/UAT pass (03-07 human-check).** Profile persist/reload, topic add/slider/51, negation warning, keyless notice + disabled Preview, 6 themes, below 600px. Expected per 03-UI-SPEC.md. This needs a real browser.
2. **Live preview with a real key.** One POST per click; the math line renders; a weight-only change recomputes locally. The preview endpoint has only been exercised against mocks and keyless. Note the 5s production timeout (03-CALIBRATION Note 1).
3. **Backstop E6:** 360px viewport, three notices wrap in order, no horizontal scroll.
4. **Backstop E4 long-text:** a 500-char description stays inside its input.
5. **Backstop E5 overflow:** the narrow-row math line wraps inside the strip.
6. **Acknowledge the 03-03 backstop:** the 25-cap is not serialized under concurrent creates (single-user limitation).
7. **Decide on CR-01:** fix now (quick task + CJK/URL test) or carry it into Phase 4.

### Gaps Summary

No blocking gaps. All five roadmap success criteria and all five requirement IDs are implemented, wired and covered by passing tests I ran (121 backend, 70 frontend, tsc clean). What remains:
- visual and live-key UAT
- five backstop truths that only a human can confirm
- a set of review warnings

CR-01 is the most consequential warning. It is a confirmed defect in the builder that Phase 4 inherits unchanged. It does not break any must-have as written, and it does not affect the user's English-language calibration set. It should still be fixed before Phase 4 relies on the builder. WR-01..WR-05 are UX/robustness issues in the editor; none prevents the goal. No later phase in the roadmap explicitly covers any of them, so none were deferred.

---

_Verified: 2026-09-23T21:23:47Z_
_Verifier: Claude (gsd-verifier)_
