---
gsd_state_version: "1.0"
milestone: v0.1.24
current_phase: 03
current_phase_name: Interest Model, Schema & Rubric Editor
status: executing
stopped_at: Completed 03-07-PLAN.md
last_updated: "2026-09-23T18:41:44.538Z"
last_activity: 2026-09-23
last_activity_desc: Phase 03 execution started
state_head: e1f6267d7cf5e74558bd367bdad1966ef128a71b
progress:
  total_phases: 7
  completed_phases: 2
  total_plans: 16
  completed_plans: 15
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-23)

**Core value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.
**Current focus:** Phase 03 — Interest Model, Schema & Rubric Editor

## Current Position

Phase: 03 (Interest Model, Schema & Rubric Editor) — EXECUTING
Plan: 8 of 8
Status: Ready to execute
Last activity: 2026-09-23 — Phase 03 execution started

Progress: [███░░░░░░░] 29%

## Performance Metrics

**Velocity:**

- Total plans completed: 8
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | - | - |
| 2 | 4 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01 P01 | 5 min | 3 tasks | 4 files |
| Phase 01 P02 | 16 min | 3 tasks | 2 files |
| Phase 01 P03 | 1 min | 3 tasks | 1 files |
| Phase 01 P04 | 26 min | 3 tasks | 1 files |
| Phase 02 P01 | 4 min | 2 tasks | 6 files |
| Phase 02 P04 | 1 min | 2 tasks | 4 files |
| Phase 02 P02 | 5 min | 3 tasks | 6 files |
| Phase 02 P03 | 6 min | 3 tasks | 6 files |
| Phase 03 P01 | 3 min | 3 tasks | 7 files |
| Phase 03 P02 | 4 min | 3 tasks | 7 files |
| Phase 03 P03 | 5 min | 3 tasks | 7 files |
| Phase 03 P05 | 6 min | 3 tasks | 12 files |
| Phase 03 P04 | 7 min | 3 tasks | 13 files |
| Phase 03 P06 | 8 min | 3 tasks | 8 files |
| Phase 03 P07 | 6 min | 2 tasks | 6 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: Dependency upgrade (Phase 1) precedes all Jev work; TypeSafe starter 0.1.0 targets Boot 4.0.7
- Roadmap: Whole V6 schema ships in Phase 3; the feedback tables must support per-topic penalization (FDBK-06)
- Roadmap: Question/state builders land in Phase 3 (needed by INT-04 preview and the calibration spike); Phase 4 reuses them
- Roadmap: `/api/interest/status` starts in Phase 2/3 (configured + breaker state); JEV-05 is completed in Phase 4 with counts
- Roadmap: Manual "Re-score unread" (INT-05) is implemented on the Phase 4 sweep (delete in-scope score rows, let the sweep drain)
- [Phase 01]: 01-01: RestClient transport pinned to Reactor Netty via version-less reactor-netty-http (D-01); guarded by HttpClientConfigurationTest
- [Phase 01]: 01-01: Outbound timeouts bound under spring.http.clients.* (D-02) - feeds slower than 30s now time out instead of hanging
- [Phase 01]: 01-02: User approved the full resolved npm set incl. <48h releases @tanstack/react-query 5.103.2 and typescript-eslint 8.70.1; no holds
- [Phase 01]: 01-02: 2 moderate react-router v6 advisories (GHSA-wrjc-x8rr-h8h6, GHSA-337j-9hxr-rhxg) accepted; fix needs v7, deferred
- [Phase 01]: 01-03: User chose restore for main worktree .serena/project.yml; identity re-check exited 0 before restore, nothing lost, no stash
- [Phase 01]: 01-03: sbartram/main merged --no-ff into local main (29da9aa); unpushed until 01-04 approval (D-05); axion computes 0.1.24-SNAPSHOT
- [Phase 01]: 01-04: User approved (verbatim: approve); v0.1.24 released from main 29da9aa, image myfeeder:0.1.24 sha256:f1517e2d, Helm rev 17; no rollback or Redis flush needed
- [Phase 01]: 01-04: 20-min soak passed (40/40 healthy feeds clean, 18 via 304); reader UI human check queued for end-of-phase UAT
- [Phase 02]: 02-01: App-owned TypeSafeClient (Pattern A) with Supplier-only key; Jev-only Reactor Netty transport (5s/5s) on a cloned RestClient.Builder
- [Phase 02]: 02-01: spring-boot-starter-aspectj added in its own commit (0e85811); Raindrop resilience now live (retry 3x/1s, breaker, 409 fallback), user-accepted 2026-09-22; tuning + CLAUDE.md fix tracked as pending todo
- [Phase 02]: 02-04: TypeSafe key is an optional deploy secret mirroring Raindrop (values/Secret stringData/secretKeyRef env/deploy.sh warning); checksum/secret on app pod template rolls the pod on any Secret change (first deploy rolls once); --set comma limitation shared with Raindrop; Helm lookup preservation deferred to Phase 4/7
- [Phase 02]: 02-02: JevApiClient.judge(state, questions) returns app-owned JevJudgment; model = response.model() (canary-tested), 401/403 WARN logs status+requestId only, answers checked via SDK noul/score/answer accessors; live SC2 smoke gated on JEV_LIVE_SMOKE=true (end-of-phase human check)
- [Phase 02]: 02-03: jev breaker/retry via @CircuitBreaker+@Retry on JevApiClientImpl.judge, no fallback (typed exceptions + CallNotPermittedException propagate); Retry outer so breaker records every attempt; 429 retry-after-ms honored, capped 10s, else 1s/2s; Phase 4 reads breaker from CircuitBreakerRegistry 'jev', timeouts are TypeSafeApiConnectionException
- [Phase 03]: 03-01: V6 topic name is required (name TEXT NOT NULL), user-confirmed in plan-phase 2026-09-23; user accepted both fixed choices (article_topic_score -> article_score ON DELETE CASCADE; INTEGER columns, no DB length CHECKs)
- [Phase 03]: 03-01: Phase 4 inserts article_score before article_topic_score in one transaction; Re-score unread deletes article_score rows and lets the cascade drop nouls; Phase 6 learned CTE must honor topics_narrowed (D-02)
- [Phase 03]: 03-02: JevApiClient.isConfigured() = hasText(apiKey), no breaker/retry, requireConfigured delegates (D-04); InterestQuestions/ArticleStateBuilder are pure static builders shared by preview and Phase 4 scorer (D-12)
- [Phase 03]: 03-02: Phase 4 must check isConfigured() and InterestService.isColdStart() before forRubric, never judge an empty question map, skip when hasJudgeableText is false; jsoup >1.14.2 upgrade is a separate quick task (check Readability4J 1.0.8 first)
- [Phase 03]: 03-03: InterestService.isColdStart() is the single cold-start predicate (blank-after-trim profile AND zero topics); 03-04 status and Phase 4 SCOR-06 call it, never reimplement
- [Phase 03]: 03-03: Topic version bumps only on a trimmed-description change (name/weight edits keep it); profile version only on exact text change; limits are InterestService constants with fixed-text 400s
- [Phase 03]: 03-05: Interest types live in src/api/interest.ts (following api/integrations.ts); Phase 5 reuses MainLayout interestsOpen for the Priority cold-start CTA
- [Phase 03]: 03-05: Interest mutations set meta.inlineError and createQueryClient() skips their toast; ApiError exposes status + ProblemDetail title for 03-07 preview copy
- [Phase 03]: 03-05: 03-06 feeds its dirty-topic count into describeUnsaved(profileDirty, dirtyTopics) in InterestsDialog.tsx; topic hooks go in useInterest.ts
- [Phase 03]: 03-04: /api/interest/status is exactly {configured, breakerState, coldStart}; Phase 4 appends JEV-05 counts to InterestStatus as NEW components, never renames these three
- [Phase 03]: 03-04: Jev failures map to fixed-text ProblemDetails (503 'Jev not configured', 503 'Jev unavailable', 422 'Jev rejected the request (HTTP <status>)', 503 'Jev request failed'); preview is one judge() call, no transaction, nothing persisted; WR-01 stays open for Phase 4
- [Phase 03]: 03-06: TopicRowState {key,id,name,description,weightText,weight,saved} with a stable t-<id>/d-<n> key; each TopicRow owns its create/update/delete mutations (03-07 adds preview the same way)
- [Phase 03]: 03-06: Rows seed once per open; isTopicDirty/parseWeight live in utils/interest.ts; the close guard counts edited saved rows plus drafts with any name/description text
- [Phase 03]: 03-07: One PreviewBlock per dialog (not configured, breaker, no article, status pending, status unknown) passed to every TopicRow; the row adds only the blank-description reason; status-unknown only when the status query has no data and failed
- [Phase 03]: 03-07: Preview stores {noul, description, articleId}; math renders from noul and the current weight, staleness compares stored inputs; mutate only from the click handler, no retries, no invalidation

### Pending Todos

- [2026-09-23] [backend] Tune Raindrop resilience and fix CLAUDE.md AspectJ note — [todo file](.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md)

### Blockers/Concerns

- [Phase 2] Code review 02-REVIEW.md: 5 warnings open (Retry-outer breaker counting, Raindrop POST retries, caller bugs recorded as breaker failures, choice answers dropped, untrimmed key) — address before/within Phase 4
- Phase 3: calibration spike needs a live `MYFEEDER_TYPESAFE_API_KEY` (key confirmed working 2026-09-23 via JevLiveSmokeTest)
- Phase 4: verify jsoup is on the classpath via Readability4J (or add it explicitly)
- Phase 7: 429 behavior during launch backfill is unobserved; fallback is concurrency 1 or the SDK retry layer (keep exactly one)

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-09-23T18:41:35.326Z
Stopped at: Completed 03-07-PLAN.md
Resume file: None
