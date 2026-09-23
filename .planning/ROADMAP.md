# Roadmap: myfeeder

## Overview

This milestone adds interest ranking to myfeeder. First the dependency stack moves to the patch/GA line the TypeSafe starter was built against. Then an optional, resilient Jev client goes in. After that the user gets a profile and topic rubric to describe their interests, and every eligible new article is judged once in the background without touching feed polling. Those judgments feed a Priority view with exact, explainable scores. Thumbs feedback tunes the ranking with no extra Jev calls. The milestone ends with a production rollout and a tuning pass against real score data.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Dependency Upgrade** - Move to Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3 and frontend minor/patch versions, then deploy
- [ ] **Phase 2: Jev Client Foundation** - Optional, pinned-model Jev client with a single Resilience4j retry layer and deploy-time secret
- [ ] **Phase 3: Interest Model, Schema & Rubric Editor** - Profile and weighted topic editor, topic preview, and the full V6 interest schema
- [ ] **Phase 4: Scoring Pipeline & Backfill Sweep** - Background, never-blocking scoring of new articles plus the sweep that backfills, recovers and re-scores
- [ ] **Phase 5: Blend & Priority View** - Query-time blend, Priority route with stable keyset pagination, score badges and "Why N?" breakdown
- [ ] **Phase 6: Thumbs Feedback** - Reversible, bounded thumbs up/down that re-weights matched topics instantly
- [ ] **Phase 7: Rollout & Calibration** - Production release with a live key, launch backfill observed, blend constants tuned, docs updated

## Phase Details

### Phase 1: Dependency Upgrade

**Goal**: myfeeder runs in production on the current patch/GA dependency line and behaves exactly as before
**Depends on**: Nothing (first phase)
**Requirements**: UPG-01, UPG-02, UPG-03
**Success Criteria** (what must be TRUE):

  1. `./gradlew build` on Spring Boot 4.0.8, Spring AI BOM 2.0.1 and Spring Cloud 2025.1.3 passes every backend test
  2. Frontend dependencies are on their latest minor/patch versions (no major bumps; react-router stays on v6), and `npm test` and `npx tsc -b` both pass
  3. The upgraded release is deployed to k3s, starts with clean logs, and feeds keep polling so new articles appear in the reader as before

**Plans**: 2/4 plans executed

Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Backend BOM bump to Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3, keep Reactor Netty (D-01), bind timeouts (D-02), CLAUDE.md (wave 1)
- [x] 01-02-PLAN.md — Frontend in-major `npm update --save` with blocking-human version approval before install/commit (D-04) (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 01-03-PLAN.md — Combined build + local end-to-end smoke, STACK.md, `.serena` decision, `--no-ff` merge to local main (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 01-04-PLAN.md — Read-only release preflight, approval gate, push + release v0.1.24 + image + k3s deploy + soak verification (D-05) (wave 3)

### Phase 2: Jev Client Foundation

**Goal**: The app can make resilient Jev judgment calls on a pinned model when a key is configured, and runs exactly as before when no key is set
**Depends on**: Phase 1
**Requirements**: JEV-01, JEV-02, JEV-03, JEV-04
**Success Criteria** (what must be TRUE):

  1. The app starts and serves feeds normally whether the TypeSafe API key is absent, blank or set, and a context test covers each case (the app owns the `TypeSafeClient` bean)
  2. With a live key, a smoke call returns a judgment from `jev-1.13.0`, and the client exposes the response's model id so it can be stored with each score
  3. Sustained transient failures (429/5xx/timeout) are retried only by Resilience4j (SDK retries are off) and open the circuit breaker; per-article 400/422 errors are neither retried nor able to open the breaker
  4. `deploy.sh` and the Helm chart treat `MYFEEDER_TYPESAFE_API_KEY` as optional: a deploy without it succeeds with a warning, and changing only the key rolls the pod

**Plans**: TBD

### Phase 3: Interest Model, Schema & Rubric Editor

**Goal**: The user can describe what they care about in a free-text profile and a weighted topic rubric, and can check how a topic judges a real article, on top of the complete interest-scoring schema
**Depends on**: Phase 2
**Requirements**: INT-01, INT-02, INT-03, INT-04, INT-06
**Success Criteria** (what must be TRUE):

  1. User can write and edit a free-text interest profile of up to 2,000 characters, with writing guidance shown in the editor, and it persists across reloads
  2. User can add, edit and delete up to 25 topics, each with a description and a signed weight between −50 and +50 (default +20); out-of-range weights and a 26th topic are rejected
  3. Entering a negated topic description (e.g. "not about crypto") shows a warning that suggests a positive description with a negative weight instead
  4. User can preview a draft topic against the article open in the reading pane and see its match result (one Jev call) before saving it
  5. The interest settings UI shows a "not configured" notice when no API key is set, and a "cold start" prompt when the profile is empty and there are no topics

**Plans**: TBD
**UI hint**: yes
**Notes**:

  - `V6__interest_scoring.sql` ships the whole schema now (`interest_profile`, `interest_topic`, `article_score`, `article_topic_score`, `article_feedback`, with `ON DELETE CASCADE` and the weight CHECK −50..50), so later phases add no migrations. The feedback schema must support FDBK-06: a thumbs-down can penalize only the topics the user picks, so the feedback tables need per-topic selection (for example an `article_feedback_topic` child table, or per-topic feedback rows), not just `article_feedback(article_id, vote)`.
  - The question builder (5-level profile `Score`; one positively phrased `Noul` per topic) and the state builder (feed, title, summary; HTML stripped, truncated, content fallback) are pure functions delivered here, because INT-04 preview and the calibration spike both need them. Phase 4 reuses them.
  - Phase 2 lays down `/api/interest/status` with the `configured` flag and breaker state, which INT-06 needs. The full JEV-05 endpoint (counts) is completed in Phase 4.
  - **Research flag:** end with a calibration spike. Run the question builder against 10–20 real articles with the live key, check the spread and confidence of scores, and iterate the wording before Phase 5 builds the UI on it.

### Phase 4: Scoring Pipeline & Backfill Sweep

**Goal**: Every eligible article, including the existing unread backlog, is judged once by Jev in the background, and feed polling is never slowed or failed by it
**Depends on**: Phase 3
**Requirements**: SCOR-01, SCOR-02, SCOR-03, SCOR-04, SCOR-05, SCOR-06, SCOR-07, SCOR-08, INT-05, JEV-05
**Success Criteria** (what must be TRUE):

  1. Shortly after a poll brings in new articles, each eligible one (unread, published within the last 14 days, has a GUID) gets exactly one stored judgment: profile score and confidence, a noul per topic, the model id, and the profile/topic versions. The newest articles are scored first.
  2. When Jev is slow (a stub sleeping 30s), failing or unconfigured, poll duration and feed error counts do not change, and nothing is scored while the profile is empty and there are no topics
  3. The background sweep scores the eligible unscored backlog on launch, after a key is added late, after an outage, and after the profile is first written, without re-judging articles that are already scored. Permanent failures stop after 3 attempts, transient failures and an open circuit don't use up attempts, and GUID-less articles are skipped.
  4. `GET /api/interest/status` reports whether Jev is configured, the circuit-breaker state, and the eligible-unscored and failed article counts
  5. User can trigger "Re-score unread", sees how many articles will be re-judged before confirming, and the in-window unread articles are then re-scored by the sweep

**Plans**: TBD

### Phase 5: Blend & Priority View

**Goal**: The user can open a Priority view where the unread articles they care about most come first, each with a score badge and an exact explanation of that score
**Depends on**: Phase 3, Phase 4
**Requirements**: PRIO-01, PRIO-02, PRIO-03, PRIO-04, PRIO-05, PRIO-06, PRIO-07, PRIO-08
**Success Criteria** (what must be TRUE):

  1. User can open Priority from the feed tree or with `g p` (route `/priority`). It lists unread scored articles by score, highest first with ties broken by date, then a "Not yet scored" separator, then unscored articles by date. Infinite scroll crosses that boundary with no duplicates or gaps.
  2. Articles show a tier-colored 0–100 interest badge in the list and the reading pane (unscored articles show none). "Why N?" (or `i`) shows the profile contribution plus each matched topic's match × weight, adding up exactly to the badge value, with matched-topic chips.
  3. While triaging in Priority, marking read or starring does not reorder or drop rows until the user refreshes or re-enters the view; `j`/`k` walk the ranked order, and `Shift+A` is disabled
  4. The Priority view shows a "not configured", "cold start", "scoring paused" or "N articles waiting to be scored" state when each applies
  5. Changing a topic's weight in settings changes badges and the Priority order on the next refresh, with no new Jev calls (the blend is computed at query time from stored raw outputs)

**Plans**: TBD
**UI hint**: yes
**Notes**:

  - Everything except PRIO-08 depends only on the Phase 3 schema, so the blend CTE and Priority UI can be built against seeded `article_score` rows in parallel with Phase 4. PRIO-08 needs the Phase 4 status counts.
  - The blend CTE is the single source of truth for sort, badge and breakdown. Its `learned` input returns 0 until Phase 6.
  - **Research flag:** keyset pagination plus TanStack infinite-query invalidation (`useArticles.ts`, `MainLayout`) deserves a focused look before planning.

### Phase 6: Thumbs Feedback

**Goal**: The user can teach the ranking with thumbs up/down and immediately see what each vote changed
**Depends on**: Phase 5
**Requirements**: FDBK-01, FDBK-02, FDBK-03, FDBK-04, FDBK-05, FDBK-06, FDBK-07
**Success Criteria** (what must be TRUE):

  1. User can vote thumbs up or down with buttons or the `u`/`d` keys. Pressing the same vote again removes it and pressing the other one flips it, so up, down, up ends as a single up.
  2. After a vote, badges and scores reflect the new effective weights of the article's matched topics right away (no Jev call), the Priority list order stays put until refresh, and a message shows the effect (e.g. "Rust +2")
  3. A topic's learned adjustment never goes past ±20 points and never flips the sign of its base weight; undoing a vote reverses it exactly, and the topic editor shows base weight and learned adjustment separately
  4. A vote on an article that matched no topics tells the user so and offers to create a topic from the article
  5. A thumbs-down on an article that matched several topics lets the user choose which topic(s) to penalize

**Plans**: TBD
**UI hint**: yes

### Phase 7: Rollout & Calibration

**Goal**: Interest ranking is live in production with a real key, the backlog is scored, and the scores are tuned so they mean something
**Depends on**: Phase 6
**Requirements**: OPS-01, OPS-02, OPS-03
**Success Criteria** (what must be TRUE):

  1. The feature is released and deployed with a live key, and the launch backfill drains the eligible backlog with no 429 storms and without opening the circuit (per `/api/interest/status` and the logs)
  2. Blend constants (profile weight, learning rate, cap, badge tier thresholds) are configurable and have been tuned against the real score distribution, so badges spread across tiers instead of clustering
  3. CLAUDE.md documents the Jev behaviors and gotchas: the app-owned client bean, the single retry layer, the scoring executor and the eligibility window

**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 (most of Phase 5 can overlap Phase 4; see Phase 5 notes)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Dependency Upgrade | 2/4 | In Progress|  |
| 2. Jev Client Foundation | 0/TBD | Not started | - |
| 3. Interest Model, Schema & Rubric Editor | 0/TBD | Not started | - |
| 4. Scoring Pipeline & Backfill Sweep | 0/TBD | Not started | - |
| 5. Blend & Priority View | 0/TBD | Not started | - |
| 6. Thumbs Feedback | 0/TBD | Not started | - |
| 7. Rollout & Calibration | 0/TBD | Not started | - |
