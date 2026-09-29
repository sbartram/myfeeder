---
phase: 07-rollout-calibration
plan: 05
subsystem: docs
tags: [claude-md, jev, typesafe, resilience4j, interest-ranking, ops]

requires:
  - phase: 07-rollout-calibration (07-01)
    provides: tier config (myfeeder.interest.blend.tiers.*, TierThresholds on /status, useInterestTiers/TierContext)
  - phase: 07-rollout-calibration (07-02)
    provides: JevEventLogging retry/breaker log lines (D-15)
  - phase: 07-rollout-calibration (07-03/07-04)
    provides: scripts/interest-calibration-replay.sh and InterestCalibrationReplaySqlTest
provides:
  - "Root CLAUDE.md `## Jev Scoring and Resilience` section (8 bold-lead bullets) documenting the app-owned TypeSafeClient bean, single retry layer, event log lines, scoring executor, sweep, eligibility window, throttle levers and tier tuning path"
  - "Stale CLAUDE.md lines fixed: Jev architecture bullet, interest classes in Package Structure, V6 migration, TYPESAFE key optional + incrementMinor + helm --set limitation in Deployment, tiers on the status bullet, interest frontend api/hooks"
affects: [07-06 merge/push, 07-07 rollout watch, future sessions on interest scoring]

actuals:
  tokens: 5000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Each CLAUDE.md fact grep-checked against its source file before writing; package lists derived from ls of the package directories"

key-files:
  created: []
  modified:
    - CLAUDE.md

key-decisions:
  - "Split the OPS-03 CLAUDE.md work into two commits (Task 1 tracer section, Task 2 stale-line fixes) rather than one, per the per-task commit protocol; the pre-existing uncommitted CLAUDE.md edits rode along in the Task 1 commit"
  - "Extended the Package Structure fix to event/ (ArticlesIngestedEvent), controller/ (InterestRescoreController, PriorityPage, FeedbackRequest, RescoreRequest) and the frontend api/hooks lists (interest, useFeedback, usePriorityArticles) because they were equally stale interest-ranking lines"

patterns-established:
  - "Throttle emergency lever documented with explicit removal (`kubectl set env ... NAME-`), never relying on a Helm upgrade to revert it"

requirements-completed: [OPS-03]

coverage:
  - id: D1
    description: "CLAUDE.md has exactly one `## Jev Scoring and Resilience` section between Interest Ranking and Spring Boot 4 notes, with the eight bold-lead bullets each appearing once"
    requirement: OPS-03
    verification:
      - kind: other
        ref: "07-05-PLAN Task 1 <verify> automated check 1 (heading + 8 bullet counts) and order awk check"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every Jev fact stated in the section exists in source (jev-score prefix, MAX_RETRY_AFTER_MS, not-configured log text, max-retries 0, window-days 14, queue-capacity 1000, breaker log format, useInterestTiers)"
    requirement: OPS-03
    verification:
      - kind: other
        ref: "07-05-PLAN Task 1 <verify> automated checks 2 and 3 (doc facts present; source grounding)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Stale lines fixed (Jev architecture bullet, interest package classes that exist on disk, V6 migration, TYPESAFE key optional, incrementMinor, tiers on status) and pre-existing CLAUDE.md edits preserved and committed"
    requirement: OPS-03
    verification:
      - kind: other
        ref: "07-05-PLAN Task 2 <verify> automated checks 1-3 plus per-class file-existence loop"
        status: pass
    human_judgment: false
  - id: D4
    description: "The section reads clearly as working memory for a future session (wording, completeness of the throttle/tuning runbook)"
    requirement: OPS-03
    verification: []
    human_judgment: true
    rationale: "Doc clarity and usefulness is a reader judgment no grep asserts"

duration: 3min
completed: 2026-09-27
status: complete
plan_head_before: 122cb0628c0f5b0b2ac1a132c51bb997eed63375
---

# Phase 7 Plan 05: CLAUDE.md Jev Behaviors and Gotchas (OPS-03) Summary

**Root CLAUDE.md now carries a source-checked `## Jev Scoring and Resilience` section (app-owned TypeSafeClient, single jev retry layer with the 10s Retry-After cap, jev-score executor, PT2M/50 sweep, 14-day eligibility window, D-15 log lines, kubectl throttle lever with explicit removal, tier tuning via the calibration replay) plus fixed Architecture, Package Structure, Flyway, Deployment and status lines.**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-09-27T20:38:30Z
- **Completed:** 2026-09-27T20:41:08Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- New `## Jev Scoring and Resilience` section with 8 bullets: App-owned client bean, Single retry layer, Jev event log lines, Scoring executor, Sweep, Eligibility window, Throttle levers, Tier thresholds and tuning. Each fact was confirmed in `TypeSafeConfig`, `InterestScoringConfig`, `JevEventLogging`, `InterestScoringSweep`, `ScoringQueue`, `ScoringFailure`, `ArticleScoreStore`, `application.yaml`, `useInterest.ts`/`utils/interest.ts`/`App.tsx` and the replay script.
- Architecture gained a `**Jev (interest scoring)**` bullet (`org.springaicommunity:spring-ai-starter-typesafe` 0.1.0, outside the Spring AI BOM).
- Package Structure lists every interest class, taken from `ls` of each package: config, model, repository, service, integration, event, controller, scheduler.
- Flyway list ends with `V6__interest_scoring.sql` and its six tables.
- Deployment: `MYFEEDER_TYPESAFE_API_KEY` is documented as optional in the release code block and the Deploy bullet. Added the `-Prelease.versionIncrementer=incrementMinor` minor-release line and the `helm --set` `,`/`\` limitation.
- The Interest Ranking status bullet now lists `tiers`. The frontend conventions list the interest api and hooks.
- The Raindrop todo's CLAUDE.md doc items are done: TypeSafeConfig and the Jev classes are in Package Structure, and the TYPESAFE key is optional for deploy.sh. The Raindrop tuning itself stays in the todo.

## Task Commits

1. **Task 1 (tracer): Jev Scoring and Resilience section** - `80f508e` (docs). Also carries the pre-existing uncommitted CLAUDE.md edits.
2. **Task 2: stale and missing lines fixed** - `400ecbd` (docs)

**Plan metadata:** see the final docs(07-05) commit.

## Pre-existing CLAUDE.md edits committed with OPS-03 (in 80f508e)
- AI line → "Spring AI Anthropic starter on the classpath (not yet used by any application code)"
- service/ row gained FeedUrlValidator, ArticleExtractionService, ExtractedContent
- integration/ row gained RaindropApiClient/, RaindropCollection, RaindropNotConfiguredException
- Flyway list gained `V5__article_extracted_content.sql`
- Removed "(run 3× for 0.1.16–0.1.18; ...)" from the Cut a release intro
- Routes bullet gained `GET /topics/learned` and the `{"confirm": true}` JSON-body rule for POST /rescore
- New bullets: "Scores are discarded if the rubric changed mid-call", "Status polling", "Thumbs feedback"

## Files Created/Modified
- `CLAUDE.md` - OPS-03 Jev section and stale-line fixes

## Decisions Made
- Used two commits (one per task) instead of the single commit Task 2 describes. Each commit carries `07-05` in its subject, so the "latest CLAUDE.md commit is 07-05" check holds.
- Documented the ingest path (`InterestScoringListener` → `ScoringQueue.submitIngested`) and the `App.tsx` TierContext provider, both confirmed in source, so readers can follow the whole chain.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical doc accuracy] Package Structure and frontend lists: additional stale interest entries**
- **Found during:** Task 2
- **Issue:** `ls` showed interest classes the plan's list did not name: `event/ArticlesIngestedEvent`, `controller/InterestRescoreController`, `PriorityPage`, `FeedbackRequest`, `RescoreRequest`, `model/InterestBreakdown`, `ArticleFeedback`, `api/interest.ts`, and hooks `useFeedback` and `usePriorityArticles`. Leaving them out would contradict the "no stale package line remains" done criterion.
- **Fix:** Added them to the matching rows (the plan's model/ item already called for the model additions).
- **Files modified:** CLAUDE.md
- **Verification:** A file-existence loop over every newly named class passed. No names were invented.
- **Committed in:** 400ecbd

**2. [Process] Two commits instead of one**
- **Found during:** Task 1
- **Issue:** Task 1 is a tracer task that commits under the per-task protocol. Task 2 describes a single combined commit.
- **Fix:** Committed per task. Both subjects carry `07-05`/OPS-03, and only CLAUDE.md was staged each time.
- **Committed in:** 80f508e, 400ecbd

---

**Total deviations:** 2 (1 doc-accuracy extension, 1 process)
**Impact on plan:** No scope creep beyond the stale-line intent. Only CLAUDE.md changed, and `.claude/CLAUDE.md`, `.envrc` and `.planning/config.json` remain unstaged.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CLAUDE.md is current for the interest-ranking release, ready for the 07-06 merge/push (the TruffleHog pre-commit hook passed on both commits).
- The tier values documented as 70/40 must be updated here if 07-CALIBRATION tunes them.

## Self-Check: PASSED
- FOUND: CLAUDE.md (modified; `## Jev Scoring and Resilience` count 1, `**Jev (interest scoring)**` count 1)
- FOUND: 80f508e, 400ecbd in `git log`
- All six plan `<verify>` automated checks passed; `git status --porcelain -- CLAUDE.md` was empty after the Task 2 commit

---
*Phase: 07-rollout-calibration*
*Completed: 2026-09-27*
