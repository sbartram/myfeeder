# Roadmap: myfeeder

## Milestones

- ✅ **v0.2.1 Interest Ranking** — Phases 1-7 (shipped 2026-09-29) — [archive](milestones/v0.2.1-ROADMAP.md)
- 🚧 **v0.3.0 Engagement Learning** — Phases 8-12 (in progress)

## Overview

v0.3.0 teaches the v0.2.1 interest ranking from what the user does, not only from thumbs votes. Capture ships first and changes no ranking, so production collects engagement (opening the original link, starring, adding to a board, saving to Raindrop) while the rest is built. Then engagement becomes a small, capped, thumbs-overridable implicit up-vote in the derived learned model, with the calibration replay regenerated in the same change. The UI then explains votes and engagement separately, and engaged articles that no topic covers become topic suggestions. The milestone ends by tuning the engagement weights and cap against real production engagement and releasing the calibrated build. Nothing in this milestone calls Jev or writes a topic's stored weight.

## Phases

**Phase Numbering:**

- Integer phases (8, 9, 10): Planned milestone work (numbering continues from v0.2.1, which ended at Phase 7)
- Decimal phases (8.1, 8.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

<details>
<summary>✅ v0.2.1 Interest Ranking (Phases 1-7) — SHIPPED 2026-09-29</summary>

- [x] Phase 1: Dependency Upgrade (4/4 plans) — completed 2026-09-22
- [x] Phase 2: Jev Client Foundation (4/4 plans) — completed 2026-09-23
- [x] Phase 3: Interest Model, Schema & Rubric Editor (8/8 plans) — completed 2026-09-23
- [x] Phase 4: Scoring Pipeline & Backfill Sweep (9/9 plans) — completed 2026-09-24
- [x] Phase 5: Blend & Priority View (8/8 plans) — completed 2026-09-26
- [x] Phase 6: Thumbs Feedback (7/7 plans) — completed 2026-09-27
- [x] Phase 7: Rollout & Calibration (11/11 plans) — completed 2026-09-29

Full phase details: [milestones/v0.2.1-ROADMAP.md](milestones/v0.2.1-ROADMAP.md)

</details>

### 🚧 v0.3.0 Engagement Learning (In Progress)

**Milestone Goal:** Opening or saving an article teaches the ranking what I care about, with no extra Jev calls, and every learned point stays explainable and reversible.

- [ ] **Phase 8: Engagement Capture** - V7 schema; record opens (`o`/Open Original) and saves (star, board, Raindrop) as sticky, forgettable engagement; release to prod with ranking unchanged
- [ ] **Phase 9: Engagement Learning Model** - Engagement becomes a fractional, capped, thumbs-overridable up-vote in the derived learned CTE, with the replay and drift guard regenerated in the same change
- [ ] **Phase 10: Explainable Engagement in the UI** - "Why N?" splits each topic's weight into base + votes + engagement; engaging never re-sorts an open Priority list
- [ ] **Phase 11: Gap Discovery** - "Suggested topics" in Interests from engaged, unmatched articles, with a near-miss filter, create-from-draft and permanent dismissal
- [ ] **Phase 12: Calibration & Release** - Tune open/save weights and the engagement cap from prod data by read-only replay, release, and document

## Phase Details

### Phase 8: Engagement Capture

**Goal**: Opening an article's original link and saving an article (star, board, Raindrop) are recorded in production as sticky, forgettable engagement, while the ranking stays exactly as it was
**Depends on**: Nothing (first v0.3.0 phase; builds on the shipped v0.2.1 interest ranking)
**Requirements**: CAPT-01, CAPT-02, CAPT-03, CAPT-04, CAPT-05, CAPT-06, CAPT-07
**Success Criteria** (what must be TRUE):

  1. Pressing `o` or clicking Open Original opens the original page in a new tab every time, even when the server is down or the request fails, and records one `OPEN_ORIGINAL` engagement for the article however often it is opened; clicking a link inside the article body records nothing
  2. Starring an unstarred article records a STAR engagement; adding an article to any board (including Read Later and `b`) records one BOARD engagement however many boards it is on; a successful Raindrop save records RAINDROP, while a failed save or one blocked by the open breaker records nothing
  3. Engagement is sticky: unstarring, removing the article from a board or deleting a board keeps it, and deleting the article or its feed removes it without breaking the delete. Nothing else records engagement: not j/k selection, reader view, dwell time or auto-mark-read
  4. The reading pane shows a small "Forget engagement" control only when the open article has engagement; using it deletes that article's engagement and hides the control, and a later open or save records again
  5. The capture release runs in production and engagement rows accumulate from real use, while the Priority order, badges and "Why N?" are unchanged from v0.2.1

**Plans**: 2/5 plans executed

Plans:
**Wave 1**
- [x] 08-01-PLAN.md — Backend: V7 schema, EngagementKind, ArticleEngagementStore, PUT open route, by-id `engagement`, Forget DELETE route (wave 1)
- [x] 08-02-PLAN.md — Frontend: one `useOpenOriginal` helper for both Open Original buttons and `o`; bodyless fire-and-forget PUT; in-body links and Copy Link unreported (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*
- [ ] 08-03-PLAN.md — Backend: STAR/BOARD/RAINDROP capture in the owning services, stickiness, cascade and unchanged-ranking proofs, V7 migration test, CLAUDE.md (wave 2)
- [ ] 08-04-PLAN.md — Frontend: "Engaged: … · Forget" line in ScoreRow (scored or engaged) and exact by-id refresh after saves (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*
- [ ] 08-05-PLAN.md — Release v0.3.0: read-only dry run, blocking approval of the V7 one-way door, release and deploy, prod smoke and ranking-unchanged check (wave 3)

**UI hint**: yes
**Notes**:

  - V7 carries the milestone's whole schema in one migration, like V6: `article_engagement` (one row per article and kind, `ON DELETE CASCADE`, kind CHECK, `created_at` kept so decay stays possible later) plus the gap-discovery dismissal table that Phase 11 uses. No backfill of existing stars or boards (settled; the Phase 12 replay simulates one instead).
  - Open endpoint (settled): bodyless, idempotent `PUT /api/articles/{id}/engagement/open` returning 204, kind `OPEN_ORIGINAL`. `window.open` runs synchronously before a fire-and-forget request with no error toast, every open goes through one helper, and Open Original stays a `<button>`.
  - Saves are captured server-side in the services that own them: STAR only on an unstarred→starred change (`ArticleService.updateState`), BOARD in `BoardService.addArticle`, RAINDROP in `RaindropService` only after `createBookmark` returns (outside the circuit-breaker bean). The store is a `JdbcClient` store with `INSERT ... ON CONFLICT DO NOTHING` (not a `CrudRepository`), following `ArticleFeedbackStore`.
  - Forget (settled) deletes the rows, with no tombstone. `GET /api/articles/{id}` carries the article's engagement so the reading pane knows when to show the control.
  - Kind names and SQL aliases must not contain a word the replay's write-keyword check rejects (`COPY_LINK` would trip it).
  - **Release version (decided at roadmap approval):** capture ships as v0.3.0 (`./gradlew release -Prelease.versionIncrementer=incrementMinor`, since V7 is new schema) and the calibrated build as v0.3.1 (the v0.2.0→v0.2.1 pattern). Never move or re-tag an existing release tag.
  - CLAUDE.md says later milestone phases add no migrations; V7 makes that false, so fix that line when V7 lands.

### Phase 9: Engagement Learning Model

**Goal**: Engagement on scored articles nudges the topics they matched as a small, capped, thumbs-overridable implicit up-vote, derived at query time with no Jev calls and no writes to topic weights, and the calibration replay still reproduces the app exactly
**Depends on**: Phase 8
**Requirements**: LRN-01, LRN-02, LRN-03, LRN-04, LRN-05, CAL-01
**Success Criteria** (what must be TRUE):

  1. Opening or saving a scored article raises the effective weight of each non-negative topic it matched, so on refresh the scores of articles matching those topics rise, with no Jev call and no change to any topic's stored weight. Each article counts once at its strongest kind: a save counts more than an open, and open + star + two boards + Raindrop on one article counts once, as a save.
  2. A thumbs vote on an engaged article replaces its engagement contribution on every topic, and removing the vote restores it; engagement on an unscored article, or on a topic whose base weight is negative, adds nothing
  3. Engagement-learned points stay within their own cap (below the thumbs cap of 20) and add on top of the thumbs-learned points; every effective weight stays inside the sign clamp and ±50, and base + thumbs part + engagement part equals the effective weight exactly across a grid of base, vote and engagement values
  4. Open weight, save weight and engagement cap come from committed yaml; with engagement set to zero, scores and Priority order match v0.2.1 exactly, and the app refuses to start when save ≤ open or the cap is at or above the thumbs cap
  5. The calibration replay, regenerated in the same change, reproduces the app's scores with engagement present, the drift-guard test fails on any divergence between the replay and the CTE, and the extended learned CTE stays within a measured latency budget with about 20k seeded engagement rows

**Plans**: TBD
**Notes**:

  - Settled: engagement skips topics with a negative base weight, and its cap is separate and additive (thumbs take their share first; engagement fills its own cap on top), below the thumbs cap of 20.
  - Highest-risk SQL in the milestone, so plan it test-first. Collapse engagement to one strength per article before joining topics, drop articles with any thumbs vote (`NOT EXISTS`), count only `status = 'SCORED'`, and compute `thumbs_applied` and `engagement_applied` in SQL (the engagement part by subtraction). Add a real-Postgres grid property test for the exact split. `WITH learned AS` stays the CTE's first token, because the drift guard looks for it.
  - LRN-05's rules collide at zero (0 ≤ 0 trips "save > open"), so startup validation must accept the disabled setting; settle the exact rule in discussion.
  - New yaml keys go in the test yaml and the dev overlay too (`DevProfileConfigTest`), and new psql `-v` variables use the same names as the JdbcClient parameters.
  - Everything that reads learned values moves with the CTE: the vote effect before/after, `LearnedLimit` (checks base + learned + engagement) and `GET /api/interest/topics/learned` (engagement counts toward the learned total until ENG-F6 splits it), so each stays consistent with the effective weight.
  - Main yaml values until Phase 12 are a discussion call: conservative starting values (research suggests open 0.25, save 0.5, cap 8–10) or zero.
  - **Research flag:** run `--research-phase` (combined clamps, exact sums, drift-guard regeneration, latency budget).

### Phase 10: Explainable Engagement in the UI

**Goal**: The user can see how much of each topic's weight came from votes and how much from engagement, and engaging with an article updates what they see without reshuffling the Priority list they are triaging
**Depends on**: Phase 9
**Requirements**: LRN-06, EXPL-01
**Success Criteria** (what must be TRUE):

  1. "Why N?" shows each matched topic's effective weight as base + votes + engagement, and its point rows still sum exactly to the badge
  2. Opening, starring, boarding, saving to Raindrop or forgetting engagement while in Priority never re-sorts the list: the "Ranking changed" hint appears, the article's badge and "Why N?" update in place, and a refresh applies the new order
  3. After a refresh, an engaged article's position in Priority, its badge in every article list and in the reading pane, and its "Why N?" all agree
  4. A thumbs vote on an engaged article shows an effect toast whose before/after weights account for the engagement the vote replaced, matching "Why N?"

**Plans**: TBD
**UI hint**: yes
**Notes**:

  - Reuse the vote reaction after any engagement: patch the Priority row, set the hint, refresh the article by id and invalidate the learned query. Never invalidate `['priority']`.
  - Deferred, not in this phase: the reading-pane engagement status line (ENG-F1) and the Interests learned line split into votes and engagement (ENG-F6).

### Phase 11: Gap Discovery

**Goal**: Articles the user engaged with that no topic covers show up as topic suggestions in Interests, which the user can turn into topics or dismiss, with no Jev calls
**Depends on**: Phase 8 (independent of Phases 9–10; can run in parallel with them)
**Requirements**: GAP-01, GAP-02, GAP-03, GAP-04, GAP-05
**Success Criteria** (what must be TRUE):

  1. Interests has a "Suggested topics" section listing engaged, scored articles that matched no topic, including ones engaged since the dialog was last opened; unscored articles are not listed
  2. Articles whose best topic noul is close to the match threshold are left out, so the list doesn't suggest near-duplicates of existing topics
  3. Choosing a suggestion opens the prefilled "Create topic from article" draft; saving that topic removes the suggestion, and it stays gone after a reload
  4. Dismissing a suggestion removes it permanently, across reloads and after later engagement with the same article
  5. Listing, creating from and dismissing suggestions never call Jev

**Plans**: TBD
**UI hint**: yes
**Notes**:

  - Articles are never re-judged against new topics, so an article whose suggestion became a topic would still look unmatched; the stored handled/dismissed state (the V7 dismissal table from Phase 8) is what clears it.
  - If this phase runs after Phase 10, add the suggestions query to the shared post-engagement reaction.
  - Deferred, not in this phase: the "matched no topic" notice after engaging (ENG-F3).
  - **Research flag:** run `--research-phase` for placement, ordering (lowest score first, or strength then recency), the near-miss threshold (research suggests a best noul of about 0.35) and the `addDraft(draft)` change.

### Phase 12: Calibration & Release

**Goal**: The engagement weights and cap are tuned from real production engagement by the read-only replay, and the calibrated milestone is released to production and documented
**Depends on**: Phase 9, Phase 10, Phase 11 (and several weeks of production engagement collected since the Phase 8 release)
**Requirements**: CAL-02, CAL-03
**Success Criteria** (what must be TRUE):

  1. A read-only replay against prod reports dormant engagement (engaged but never scored), the topics at the engagement cap, the tier histogram with engagement on and off, and a simulated backfill from existing stars and boards
  2. The tuned open weight, save weight and engagement cap are committed in yaml (main plus the dev overlay; never Helm `--set` or env overrides), and a calibration note records the replay results, the reasoning, and a keep-or-revisit call on scoring engaged-but-unscored articles (ENG-F4) and on backfill (ENG-F5)
  3. The calibrated release is deployed to production with a clean startup, and engaged articles in prod show engagement in their badges and "Why N?"
  4. CLAUDE.md documents the V7 schema, the capture rules, the extended learned model (thumbs first, separate additive cap, negative-base skip, scored articles only) and the engagement tuning levers

**Plans**: TBD
**Notes**:

  - Follows the v0.2.0→v0.2.1 pattern: let engagement build up in prod (2–4 weeks per research), run the replay, then tune. The replay runs from the workstation against prod, so it doesn't need Phases 9–11 deployed first.
  - The new report sections must keep the drift guard green; the replay stays read-only.

## Progress

**Execution Order:**
Phases execute in numeric order: 8 → 9 → 10 → 11 → 12 (Phase 11 depends only on Phase 8 and can run alongside 9–10; Phase 12 waits for prod engagement data from the Phase 8 release)

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Dependency Upgrade | v0.2.1 | 4/4 | Complete | 2026-09-22 |
| 2. Jev Client Foundation | v0.2.1 | 4/4 | Complete | 2026-09-23 |
| 3. Interest Model, Schema & Rubric Editor | v0.2.1 | 8/8 | Complete | 2026-09-23 |
| 4. Scoring Pipeline & Backfill Sweep | v0.2.1 | 9/9 | Complete | 2026-09-24 |
| 5. Blend & Priority View | v0.2.1 | 8/8 | Complete | 2026-09-26 |
| 6. Thumbs Feedback | v0.2.1 | 7/7 | Complete | 2026-09-27 |
| 7. Rollout & Calibration | v0.2.1 | 11/11 | Complete | 2026-09-29 |
| 8. Engagement Capture | v0.3.0 | 2/5 | In Progress|  |
| 9. Engagement Learning Model | v0.3.0 | 0/TBD | Not started | - |
| 10. Explainable Engagement in the UI | v0.3.0 | 0/TBD | Not started | - |
| 11. Gap Discovery | v0.3.0 | 0/TBD | Not started | - |
| 12. Calibration & Release | v0.3.0 | 0/TBD | Not started | - |
