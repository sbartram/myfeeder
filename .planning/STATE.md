---
gsd_state_version: "1.0"
milestone: v0.1.24
current_phase: 07
current_phase_name: Rollout & Calibration
status: executing
stopped_at: Completed 07-09-PLAN.md
last_updated: "2026-09-29T02:04:51.056Z"
last_activity: 2026-09-28
last_activity_desc: Phase 07 execution started
state_head: 51e76c7fa3c6d0a266b3bba169b9bc7eddc0e169
progress:
  total_phases: 7
  completed_phases: 6
  total_plans: 50
  completed_plans: 49
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-27)

**Core value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.
**Current focus:** Phase 07 — Rollout & Calibration

## Current Position

Phase: 07 (Rollout & Calibration) — READY TO EXECUTE
Plan: 9 of 9
Status: Ready to execute
Last activity: 2026-09-28 — Phase 07 execution started

Progress: [█████████░] 86%

## Performance Metrics

**Velocity:**

- Total plans completed: 40
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | - | - |
| 2 | 4 | - | - |
| 03 | 8 | - | - |
| 04 | 9 | - | - |
| 05 | 8 | - | - |
| 06 | 7 | - | - |

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
| Phase 03 P08 | 2h 30m | 3 tasks | 7 files |
| Phase 04 P01 | 6 min | 3 tasks | 7 files |
| Phase 04 P02 | 6 min | 3 tasks | 3 files |
| Phase 04 P03 | 7 min | 3 tasks | 6 files |
| Phase 04 P06 | 2 min | 1 tasks | 4 files |
| Phase 04 P08 | 3 min | 2 tasks | 6 files |
| Phase 04 P04 | 7 min | 3 tasks | 9 files |
| Phase 04 P07 | 7 min | 3 tasks | 6 files |
| Phase 04 P05 | 5 min | 2 tasks | 6 files |
| Phase 04 P09 | 4 min | 3 tasks | 6 files |
| Phase 05 P01 | 6 min | 3 tasks | 13 files |
| Phase 05 P02 | 6 min | 3 tasks | 11 files |
| Phase 05 P03 | 8 min | 3 tasks | 13 files |
| Phase 05 P04 | 5 min | 3 tasks | 13 files |
| Phase 05 P05 | 3 min | 2 tasks | 4 files |
| Phase 05 P06 | 6 min | 3 tasks | 12 files |
| Phase 05 P07 | 5 min | 3 tasks | 13 files |
| Phase 05 P08 | 24 min | 3 tasks | 14 files |
| Phase 06 P01 | 10min | 3 tasks | 14 files |
| Phase 06 P03 | 5 min | 3 tasks | 13 files |
| Phase 06 P02 | 5 min | 2 tasks | 12 files |
| Phase 06 P04 | 7 min | 3 tasks | 10 files |
| Phase 06 P06 | 3 min | 2 tasks | 7 files |
| Phase 06 P05 | 7 min | 3 tasks | 14 files |
| Phase 06 P07 | 4 min | 2 tasks | 5 files |
| Phase 07 P01 | 2 min | 2 tasks | 8 files |
| Phase 07 P02 | 2 min | 2 tasks | 8 files |
| Phase 07 P03 | 3 min | 2 tasks | 2 files |
| Phase 07 P04 | 4 min | 2 tasks | 4 files |
| Phase 07 P05 | 3min | 2 tasks | 1 files |
| Phase 07 P06 | 14 min | 3 tasks | 2 files |
| Phase 07 P07 | 1h 13m | 3 tasks | 1 files |
| Phase 07 P08 | 10 min | 3 tasks | 1 files |
| Phase 07 P09 | 37 min | 3 tasks | 4 files |

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
- [Phase 03]: 03-08: Shipped v2 topic wording ('substantially about `topic`'); v1/v2 both pass A6, label agreement 0.859, v2 doubles obvious-match topic nouls
- [Phase 03]: 03-08: Topic under-firing (no obvious match crosses noul 0.5) deferred to Phase 4/5 OPS-02 tuning
- [Phase 03]: 03-08: Phase 4 scoring needs a Jev timeout well above 5s (profile+7 topics ~2.6s avg, cold >5s)
- [Phase 04]: Scoring runs on a dedicated jev-score executor (queue 1000, non-default candidate); sweep enqueues ≤50 per 2 min on the scheduler thread and pauses while the breaker is OPEN
- [Phase 04]: UAT accepted: Re-score confirm doesn't block on edits typed after it opens; concurrent-write truth rests on ON CONFLICT + single thread (override)
- [Phase 04]: 04-09: bootTestRun activates a dev profile overlay (src/test/resources/application-dev.yaml) only from TestMyfeederApplication; both application.yaml files unchanged, DevProfileConfigTest enforces the main-mirror rule and that no test activates dev
- [Phase 05]: 05-01: InterestScoreQueries is the single source of truth for Priority sort and badge: numeric raw_n ROUND(...,6), CASE-guarded 0..100 badge (null when unscored), float8 sort key with '-Infinity' for unscored rows
- [Phase 05]: 05-01: GET /api/articles/priority returns {items,nextCursor}; a cursor naming no article is 404 (restart from page 1), not 400; no index and no V7 migration
- [Phase 05]: 05-02: Priority frontend: ['priority'] infinite query with infinite staleTime and no focus/reconnect refetch; re-rank only via refreshPriority(qc) (resetQueries, page 1 only); nothing invalidates PRIORITY_KEY
- [Phase 05]: 05-02: Priority state copy renders in a slot under the filter so the toolbar always shows; a 404 on Load more restarts from page 1; EmptyState detail uses a column layout via .empty-state:has(.empty-state-detail)
- [Phase 05]: 05-03: interestScore is on every article response (lists, boards, PATCH, GET by id, Priority), id-scoped via InterestScoreQueries.displayScores so read articles keep their badge; enrichment never reorders
- [Phase 05]: 05-03: only GET /api/articles/{id} carries interestBreakdown {raw,total,display,rows,nonMatching}; total = SQL ROUND(raw) unclamped, rows apportioned by largest remainder to sum exactly to it (ScoreBreakdowns); Raindrop keeps findById
- [Phase 05]: 05-04: Priority smart view is route-driven (useMatch('/priority')); All Articles is inactive on /priority; g then p = setSelectedFeed(null) + navigate('/priority')
- [Phase 05]: 05-04: PriorityBanner shows one status by D-14 precedence (not configured > cold start > paused OPEN/FORCED_OPEN > N waiting), between toolbar and filter, reusing useInterestStatus polling; OPEN_BREAKER_STATES and articles() (en-US thousands separators) live only in utils/interest.ts
- [Phase 05]: 05-05: Outside Priority the empty badge slot is reserved only when at least one loaded row is scored (reserveSlot over allArticles); lists render the badge only (no chips, no Why) and never reorder by score
- [Phase 05]: 05-06: Read/star patch the ['priority'] cache in place (patchPriorityArticle, request read/starred only); nothing invalidates or refetches the Priority query
- [Phase 05]: 05-06: usePriorityStore (non-persisted) holds rankingChanged + baselineUnscored; 05-07 adds whyOpen/toggleWhy there
- [Phase 05]: 05-06: Ranking changed hint = 5 interest mutations or status.eligibleUnscored below the page-1 baseline (soft signal); refresh and re-entry clear it
- [Phase 05]: 05-06: On /priority j past the last row pages via fetchNextNewId, r re-ranks, Shift+A guarded by !isPriority; leaving /priority removes the cache
- [Phase 05]: 05-07: The reading pane explains a score only from GET /api/articles/{id}: ScoreRow chips are TOPIC rows with weight != 0 in server order; WhyBreakdown prints server points and a total line always equal to the badge (capped/floored wording); the client never recomputes points
- [Phase 05]: 05-07: whyOpen/toggleWhy live in usePriorityStore (session-only), shared by the Why button and i (every view, scored articles only); the non-matching footer is local state that collapses on article change
- [Phase 05]: 05-08: Priority cursor is an opaque base64url encoding of the served (sort_score, sort_date, id) tuple via the sibling PriorityPage record (same {items,nextCursor} JSON); the next page compares literal cursor values (date bound as UTC), undecodable cursors are a fixed-text 404; PaginatedResponse and other endpoints keep Long cursors (closes G-05-7/WR-02)
- [Phase 06]: 06-01: ArticleFeedbackService.applyAndReport takes Function<List<Long>, Runnable> so pick validation runs on the matched set before any weight read or write, keeping before/write/after in one method
- [Phase 06]: 06-01: vote effects skip a matched topic missing from either weight read (topic deleted mid-request) rather than failing the vote
- [Phase 06]: 06-03: vote errors use fixed UI-SPEC copy by ApiError.status (meta.inlineError); a failed vote refetches the by-id article so the pressed state reverts
- [Phase 06]: 06-03: a vote on /priority patches only the voted row's interestScore and sets rankingChanged; elsewhere it invalidates ['articles'] and other length-2 ['article', n] keys; ['interest','learned'] always
- [Phase 06]: 06-02: breakdownInputs rounds the effective weight to 6 decimals like base_w and learned_w, so base + learned equals weight exactly; exact, sort and badge unchanged
- [Phase 06]: 06-02: article.feedback rides only on findByIdWithBreakdown (GET /api/articles/{id} and the PUT/DELETE feedback responses), never on lists
- [Phase 06]: 06-04: Enter on a focused Cancel/Apply button in the narrow picker keeps its native click; only Enter elsewhere applies, and every picker Enter stops propagation
- [Phase 06]: 06-04: Shift+D, like u and d, ignores Cmd/Ctrl/Alt; it opens the picker only when canNarrow and never votes
- [Phase 06]: 06-06: GET /api/interest/topics/learned serves [{topicId, baseWeight, learned, effectiveWeight, limit}] from the eff2 CTE via ArticleFeedbackService.learnedTopics; topicWeights and allTopicWeights share one select fragment
- [Phase 06]: 06-05: The Create topic from article draft is one-shot, held in MainLayout state and seeded once as row d-1 in the TopicsSection useState initializer; at 25 topics the at-max notice replaces it
- [Phase 06]: 06-05: useLearnedTopics lives on its own ['interest','learned'] key, invalidated by votes and topic create/update/delete, never the topics list, so unsaved row edits survive
- [Phase 06]: 06-07: vote toast follows the UI-SPEC list rule (sort by absolute change), so the capped example prints Go before Rust; limit notes take precedence over (now …)
- [Phase 07]: 07-01: badge tier thresholds are server config myfeeder.interest.blend.tiers.high=70/.neutral=40, served last on /api/interest/status as tiers:{high,neutral} (D-13)
- [Phase 07]: InterestBadge reads TierContext (default DEFAULT_TIERS 70/40); MainLayout is the single provider via a non-polling useInterestTiers observer (staleTime Infinity, select tiers)
- [Phase 07]: 07-03: JevEventLogging logs jev retries (INFO), exhausted retries and breaker transitions (WARN, StateTransition.name()) as fixed text, class names and numbers only; no metric added (D-15)
- [Phase 07]: 07-04: the calibration replay SQL is reflected verbatim from InterestScoreQueries (blendCte now package-private) and pinned by InterestCalibrationReplaySqlTest; the driver forces default_transaction_read_only=on and validates inputs before connecting
- [Phase 07]: 07-05: CLAUDE.md OPS-03 doc written source-first: every Jev fact grep-checked, package lists from ls; throttle override removal is explicit (kubectl set env NAME-), never assumed reverted by Helm
- [Phase 07]: 07-06: user approved release 0.2.0 (verbatim: approve); v0.2.0 = main 687217f, image sha256:2252196e, Helm rev 18, rollback rev 17 (0.1.24); failure path not run
- [Phase 07]: 07-06: live TypeSafe key deployed (key-hash match), preview 200 jev-1.13.0, /status configured+coldStart+CLOSED tiers 70/40, 182 eligible unscored; 10.5-min soak 46/46 feeds, 0 ERROR
- [Phase 07]: 07-06: a secret change rolls the app twice (chart checksum/secret + cluster Reloader); harmless, V6 applied once
- [Phase 07]: 07-07: user chose load-phase3-rubric; Phase 3 rubric (profile + 7 topics) saved in prod at 2026-09-28T23:39:57Z
- [Phase 07]: 07-07: launch backfill D-05 Overall PASS (183 legacy drained in 6m54s, 192 SCORED, 0 FAILED, breaker CLOSED, 0 retries); no D-08 throttle, so 07-09 has no override to remove
- [Phase 07]: 07-08: user approved calibrated constants 100/70/22 (verbatim: approve), learn-rate 2, learned-cap 20; only tiers.neutral moves 40->22 so the Priority order is unchanged; no title redaction
- [Phase 07]: 07-08: replay matched the app 5/5 at 100/70/40; D-10 met at 100/70/22 (high 14.1%, neutral 34.9% of 192 scored unread); pp above 100 rejected (18/30 badges saturate at 100)
- [Phase 07]: 07-09: user approved publishing 0.2.1 (verbatim: approve); v0.2.1 = main 5461d0a, image sha256:42fcdc5b, Helm rev 19, rollback rev 18 (0.2.0); failure path not run
- [Phase 07]: 07-09: prod serves tiers 70/22 (configured, CLOSED, not cold start); replay at 100/70/22 matched the app 5/5; no MYFEEDER_INTEREST_* override existed, so none was removed

### Pending Todos

- [2026-09-23] [backend] Tune Raindrop resilience and fix CLAUDE.md AspectJ note — [todo file](.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md)

### Blockers/Concerns

- [Phase 2] 02-REVIEW WR-04 (Raindrop createBookmark POST retried) and WR-05 (helm --set secret mangling) still deferred; WR-01..03 closed in Phase 4
- [Phase 5] 05-REVIEW.md WR-04 closed by quick 260926-hhz (decoded cursor date bounded to years 1..9999 → 404); WR-05 (an unserved row whose score rises past the fixed cursor boundary is skipped — mitigated in Phase 6: votes set the Ranking-changed hint); WR-01/WR-03 user-deferred
- [Phase 3] 03-REVIEW.md WR-01..WR-05 (dialog save race, keyboard shortcuts behind modal, error copy, failed article load) still open
- [Phase 4] 04-VERIFICATION advisories: suite offline-ness relies on shell hygiene (strip SPRING_AI_TYPESAFE_*/SPRING_PROFILES_ACTIVE in the Gradle Test task); dev-overlay drift check is one-directional
- [Phase 4] Preview spinner can run ~93s worst case (3×30s + backoff); consider a UI ceiling
- [Phase 6] 06-REVIEW.md WR-01..WR-04 open (optimistic vote-state race, narrowed-vote toast credits untouched topics, breakdown rows torn by a concurrent vote under READ COMMITTED, Re-score leaves learned cache stale); non-blocking
- [Phase 6] 06-UI-REVIEW.md 21/24: picker can overflow a narrow pane (positioned against viewport), vote-error toasts announced politely not as alert, aria-controls points at an absent element when picker is closed
- Phase 7: 429 behavior during launch backfill is unobserved; fallback is concurrency 1 or the SDK retry layer (keep exactly one)

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260926-hhz | fix WR-04 bound the decoded cursor date | 2026-09-26 | 5c6c642 | [260926-hhz-fix-wr-04-bound-the-decoded-cursor-date](./quick/260926-hhz-fix-wr-04-bound-the-decoded-cursor-date/) |

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-09-29T00:44:44.759Z
Stopped at: Completed 07-09-PLAN.md
Resume file: None
