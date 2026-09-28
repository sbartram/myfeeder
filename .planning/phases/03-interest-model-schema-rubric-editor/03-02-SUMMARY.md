---
phase: 03-interest-model-schema-rubric-editor
plan: 02
subsystem: api
tags: [typesafe-jev, jsoup, question-builder, state-builder, interest-scoring]

requires:
  - phase: 02
    provides: JevApiClient.judge / JevApiClientImpl with jev breaker and retry
  - phase: 03-01
    provides: InterestTopic entity (id, name, description, weight, version)
provides:
  - InterestQuestions (pure): profile Score (5 levels), topic Noul, forRubric, stable keys profile / topic_<id> / topic_draft
  - ArticleStateBuilder (pure): ordered {feed, title, summary}, jsoup strip, 50,000-char raw cap, 1,500-char safe truncation, content fallback
  - JevApiClient.isConfigured(), the single "configured" rule, outside the breaker
affects: [03-04 status + preview, 03-08 calibration spike, phase-04 scorer]

actuals:
  tokens: 6474
  tasks: 3
  commits: 5
plan_head_before: 34d6a83772d642c1c65f3fd9d545246f158395f0

tech-stack:
  added: []
  patterns:
    - "Pure final builder classes with static methods, shared unchanged by preview and scorer (D-12)"
    - "Facts in state, evaluative text in question instructions; feed text never interpolated into questions"
    - "Cap untrusted raw HTML before Jsoup.parse (jsoup 1.11.2, CVE-2021-37714)"

key-files:
  created:
    - src/main/java/org/bartram/myfeeder/service/InterestQuestions.java
    - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
    - src/test/java/org/bartram/myfeeder/service/InterestJudgeRequestTest.java
    - src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java
    - src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java
  modified:
    - src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
    - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java

key-decisions:
  - "03-02: JevApiClient.isConfigured() = StringUtils.hasText(apiKey), no resilience annotations; requireConfigured() delegates to it (D-04)"
  - "03-02: InterestQuestions/ArticleStateBuilder are pure static builders; Phase 4 must call isConfigured() and InterestService.isColdStart() before forRubric and never call judge with an empty question map"

patterns-established:
  - "Question keys: profile, topic_<id>, topic_draft (Pitfall 23)"
  - "Wording lives in package-private constants (PROFILE_LEVELS, PROFILE_QUESTION, TOPIC_*) so 03-08 can calibrate them in one place"

requirements-completed: [INT-04]

coverage:
  - id: D1
    description: "ArticleStateBuilder + InterestQuestions output goes through JevApiClientImpl.judge onto the wire as {feed, title, summary} with profile before topic_7, and canned answers return as JevJudgment"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestJudgeRequestTest.java#builtRequestReachesTheWireInScorerOrder"
        status: pass
    human_judgment: false
  - id: D2
    description: "JevApiClient.isConfigured() is false for empty/whitespace keys, true for a real key, and makes no HTTP call"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestJudgeRequestTest.java#isConfiguredReflectsKey"
        status: pass
    human_judgment: false
  - id: D3
    description: "InterestQuestions rejects blank profile/description, orders topics by id without mutating the caller's list, omits a blank profile, returns an empty map on cold start, rejects null ids"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java (10 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "ArticleStateBuilder strips HTML, falls back to content, omits blanks, caps raw HTML at 50,000 chars (1 MB builds within 2 s), truncates at whitespace with an ellipsis and never splits a surrogate pair"
    requirement: INT-04
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java (9 tests)"
        status: pass
    human_judgment: false

duration: 4min
completed: 2026-09-23
status: complete
---

# Phase 3 Plan 02: Interest Question and State Builders Summary

**Pure `InterestQuestions` (five-level profile Score, positively phrased topic Noul, id-ordered `forRubric`) and `ArticleStateBuilder` (jsoup-stripped `{feed, title, summary}` with a 50,000-char raw-HTML cap and surrogate-safe 1,500-char truncation), plus `JevApiClient.isConfigured()` outside the breaker. A wire-level test proves their output reaches Jev in scorer order.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-09-23T17:50:41Z
- **Completed:** 2026-09-23T17:55:06Z
- **Tasks:** 3 (1 tracer, 2 TDD)
- **Files modified:** 7 (5 new, 2 modified)

## Accomplishments

- `InterestQuestions`: keys `profile` / `topic_<id>` / `topic_draft`. `profile(text)` is a Score with instructions `{reader_profile, question}` and five levels (`maxLevel()` 4). The question tells Jev to judge the subject, "not how important or relevant it claims to be". `topic(description)` is a Noul with `{topic, question}` and explicit whenTrue/whenFalse. Blank input throws `IllegalArgumentException` before anything reaches `judge()`.
- `forRubric(profileText, topics)` puts `profile` first (when not blank), then topics in ascending id order from a sorted copy. A blank profile with no topics gives an empty map, and a null id throws.
- `ArticleStateBuilder.build(feedTitle, article)` returns `{feed, title, summary}` in order with blank fields omitted. jsoup strips tags, decodes entities and drops script bodies. The summary falls back to the content (SCOR-01). Raw HTML is cut to 50,000 chars before parsing, and summaries are truncated to at most 1,500 chars at a word boundary with `…`.
- `JevApiClient.isConfigured()` is the single "configured" rule (D-04). It has no breaker or retry, and `requireConfigured()` now calls it.
- Plan verification: `Interest*`, `ArticleStateBuilderTest` and `integration.Jev*` all pass without Docker (52 tests; the gated `JevLiveSmokeTest` reports skipped).

## Task Commits

1. **Task 1 (tracer): builder output reaches the wire through judge()**: `3d6e384` (feat)
2. **Task 2: question builder guards, id ordering, cold start**: `564d173` (test, RED), `e6d45cf` (feat, GREEN)
3. **Task 3: state builder raw cap, truncation, content fallback**: `1c4d239` (test, RED), `3380f1b` (feat, GREEN)

## TDD Gate Compliance

- Task 2 RED: `topicRejectsBlankDescription`, `profileRejectsBlankText`, `forRubricPutsProfileFirstThenTopicsById` and `forRubricRejectsTopicWithoutId` failed on assertions against the Task 1 code. `check tdd-red-evidence` returned RED_EVIDENCE_OK for each; the JUnit XML report was converted to TAP for the checker. GREEN: all 10 pass.
- Task 3 RED: `truncatesAtWhitespaceWithEllipsis`, `neverSplitsSurrogatePair` and `boundsRawHtmlBeforeParsing` failed on the length assertion (4000 / 3001 / 416666 > 1500). RED_EVIDENCE_OK for each. GREEN: all 9 pass.
- No refactor commits were needed.

## Files Created/Modified

- `src/main/java/org/bartram/myfeeder/service/InterestQuestions.java`: the pure question builder and key scheme
- `src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java`: the pure state builder (strip, cap, truncate, fallback)
- `src/main/java/org/bartram/myfeeder/integration/JevApiClient.java`: adds `boolean isConfigured()`
- `src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java`: implements `isConfigured()`; `requireConfigured()` delegates to it
- `src/test/java/org/bartram/myfeeder/service/InterestJudgeRequestTest.java`: wire-level tracer plus the isConfigured cases
- `src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java`: 10 question-builder tests
- `src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java`: 9 state-builder tests, including the 1 MB bound

## Decisions Made

- The wording strings are package-private constants (`PROFILE_QUESTION`, `TOPIC_QUESTION`, `TOPIC_WHEN_TRUE`, `TOPIC_WHEN_FALSE`, `PROFILE_LEVELS`), copied verbatim from RESEARCH Pattern 3, so the 03-08 calibration spike can change them in one place.
- `forRubric` validates every id before sorting, so a null id throws `IllegalArgumentException` instead of a `NullPointerException` from the comparator.

## Notes for later phases

- **Phase 4 scorer:** call `jevApiClient.isConfigured()` and `InterestService.isColdStart()` before `InterestQuestions.forRubric`. Never call `judge` with an empty question map. Skip articles where `ArticleStateBuilder.hasJudgeableText(state)` is false.
- **Follow-up (not done here):** upgrading jsoup past 1.14.2 is a separate quick task. It must first check Readability4J 1.0.8 compatibility. Until then, the 50,000-char cap is the DoS mitigation (T-03-04).
- Without the cap, jsoup 1.11.2 parsed the 1 MB test input quickly: the RED run failed on length, not on time. The 2-second bound therefore guards against a regression but does not reproduce the CVE's pathological input.

## Deviations from Plan

None. The plan was executed as written.

## Issues Encountered

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 03-04 (status + preview) can use `isConfigured()`, `InterestQuestions.topic`/`PREVIEW_DRAFT_KEY` and `ArticleStateBuilder.build` directly.
- No blockers.

## Self-Check: PASSED

- All 7 key files exist on disk
- Commits 3d6e384, 564d173, e6d45cf, 1c4d239 and 3380f1b exist
- Plan verification: 52 tests, 0 failures (1 gated skip)

---
*Phase: 03-interest-model-schema-rubric-editor*
*Completed: 2026-09-23*
