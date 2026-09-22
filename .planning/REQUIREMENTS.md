# Requirements: myfeeder — Interest Ranking (Jev)

**Defined:** 2026-09-22
**Core Value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

Scoring model (settled in research, SUMMARY.md R1/R2/R6): `score = 100 × profile_match + Σ(topic_match × effective_weight)` in points, where `profile_match` = Jev Score / max level ∈ [0,1], `topic_match` = max(0, (noul − 0.5) × 2), `effective_weight` = base weight (−50..+50, default ±20) + learned adjustment from thumbs (capped ±20, never flips the base weight's sign). Display clamps to 0–100; sort uses the raw value.

## v1 Requirements

### Dependency Upgrade

- [ ] **UPG-01**: Backend runs on Spring Boot 4.0.8, Spring AI BOM 2.0.1 and Spring Cloud 2025.1.3 with all backend tests passing
- [ ] **UPG-02**: Frontend dependencies are on latest minor/patch versions (no major bumps) with `npm test` and `npx tsc -b` passing
- [ ] **UPG-03**: Upgraded app is released and deployed to k3s and starts cleanly, with feeds polling as before

### Jev Integration

- [ ] **JEV-01**: App starts and runs normally when the TypeSafe API key is absent, blank, or set (app-owned `TypeSafeClient` bean; context tests for each case)
- [ ] **JEV-02**: Jev calls go through a dedicated client bean with `@CircuitBreaker` + `@Retry` (Resilience4j is the only retry layer; SDK retries disabled; per-article 400/422 errors don't open the breaker)
- [ ] **JEV-03**: Jev model is pinned (`jev-1.13.0`) and the model id is stored with every score
- [ ] **JEV-04**: TypeSafe API key is an optional secret in `deploy.sh` and the Helm chart; changing only the key rolls the pod
- [ ] **JEV-05**: `GET /api/interest/status` reports whether Jev is configured, circuit-breaker state, and counts of eligible-unscored and failed articles

### Interest Model

- [ ] **INT-01**: User can write and edit a free-text interest profile (≤2,000 chars) with writing guidance shown in the editor
- [ ] **INT-02**: User can add, edit and delete topics (≤25), each with a positively-phrased description and a signed weight in −50..+50 (default +20)
- [ ] **INT-03**: Editor warns when a topic description is negated (e.g. "not about crypto") and suggests a negative weight instead
- [ ] **INT-04**: User can preview a topic against the currently open article (one Jev call) before saving it
- [ ] **INT-05**: User can trigger "Re-score unread", which shows how many articles will be re-judged, then re-scores unread articles within the eligibility window
- [ ] **INT-06**: Settings show a "not configured" notice when no API key is set, and a "cold start" prompt when the profile is empty and there are no topics

### Scoring Pipeline

- [ ] **SCOR-01**: Each newly ingested article is judged once by Jev in a single call (a 5-level profile Score plus one Noul per topic), using feed name, title and summary as input (summary HTML-stripped and truncated; falls back to stripped content when the summary is empty)
- [ ] **SCOR-02**: Scoring never blocks or fails feed polling — it runs on a dedicated bounded executor, and polling time and feed error counts are unaffected when Jev is slow, failing or unconfigured
- [ ] **SCOR-03**: Raw Jev outputs (profile score and confidence, per-topic noul by topic id, model, profile/topic versions) are stored write-once in tables separate from `article`
- [ ] **SCOR-04**: Only unread articles published within the last 14 days (configurable) are eligible for scoring; newest are scored first
- [ ] **SCOR-05**: A background sweep scores eligible unscored articles, covering the launch backfill, recovery after outages or a late-added key, and first scoring after the profile is written
- [ ] **SCOR-06**: Nothing is scored while the profile is empty and there are no topics
- [ ] **SCOR-07**: Articles that fail permanently (e.g. 400/422) are retried at most 3 times; transient failures (429/5xx/timeout/open circuit) don't consume attempts
- [ ] **SCOR-08**: Articles with no GUID are skipped by the scorer (guards against the known re-insert bug)

### Priority View

- [ ] **PRIO-01**: User can open a "Priority" view from the feed tree (route `/priority`) showing unread articles ordered by score (highest first; ties by date)
- [ ] **PRIO-02**: Unscored articles appear after scored ones, by date, below a "Not yet scored" separator
- [ ] **PRIO-03**: Articles show a 0–100 interest badge (tier-colored via theme variables) in the article list and reading pane; unscored articles show no badge
- [ ] **PRIO-04**: User can see an exact "Why N?" breakdown for an article: profile contribution plus each matched topic's match × weight, with matched-topic chips
- [ ] **PRIO-05**: Priority list order stays stable while triaging — marking read, starring or voting doesn't reorder or drop rows until the user refreshes or re-enters the view
- [ ] **PRIO-06**: Priority view pages with cursor pagination (`PaginatedResponse`), including across the scored/unscored boundary
- [ ] **PRIO-07**: Keyboard: `g p` opens Priority, `j`/`k` walk the ranked order, `i` toggles the breakdown; `Shift+A` (mark all read) is disabled in Priority
- [ ] **PRIO-08**: Priority view shows "not configured", "cold start", "scoring paused" and "N articles waiting to be scored" states

### Feedback

- [ ] **FDBK-01**: User can give an article a thumbs up or down (buttons plus `u`/`d` keys); pressing again removes the vote, and switching flips it
- [ ] **FDBK-02**: A vote adjusts the effective weight of the topics that article matched; ranking and badges reflect it immediately, with no Jev call
- [ ] **FDBK-03**: The learned adjustment per topic is capped (±20 points) and never flips a topic's base weight sign; undoing a vote exactly reverses it
- [ ] **FDBK-04**: After a vote, the user sees its effect (e.g. "Rust +2")
- [ ] **FDBK-05**: When a vote matches no topics, the user is told and offered to create a topic from the article
- [ ] **FDBK-06**: On thumbs-down of an article that matched several topics, the user can choose which topic(s) to penalize
- [ ] **FDBK-07**: Topic editor shows each topic's base weight and learned adjustment separately

### Rollout

- [ ] **OPS-01**: The feature is released and deployed with a live key; the launch backfill runs without 429 storms or an open circuit
- [ ] **OPS-02**: Blend constants (profile weight, learning rate, cap, badge tiers) are configurable and tuned against the real score distribution
- [ ] **OPS-03**: CLAUDE.md documents the new Jev behaviors and gotchas (app-owned client bean, single retry layer, scoring executor, eligibility window)

## v2 Requirements

### Priority

- **PRIO-V2-01**: "Mark all below this article as read" in the Priority view
- **PRIO-V2-02**: Count of high-interest (≥70) unread articles on the Priority feed-tree item
- **PRIO-V2-03**: Sort-by-interest option on all article lists

### Feedback / Rubric

- **FDBK-V2-01**: Per-topic "reset learned adjustment"
- **INT-V2-01**: Per-topic stats (recent match counts, average score)
- **INT-V2-02**: Debug endpoint exposing raw Jev outputs for an article

### Quality

- **QUAL-V2-01**: Fallback GUID (hash of link + title) in FeedParser so GUID-less items stop re-inserting (separate quick task)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Automatic re-scoring when profile/topics change | Manual "Re-score unread" (INT-05) covers it at user-controlled cost |
| Full article content / Readability extraction as Jev input | Truncated summary-or-content fallback is enough; extraction adds an outbound fetch per article |
| Liked/disliked articles as in-context examples | Feedback adjusts topic weights instead — deterministic and explainable |
| Hiding, dimming or auto-marking-read low-score articles | Hiding things by score erodes trust; the user decides |
| AI-written prose explanations | Jev returns numbers only; the arithmetic breakdown is exact and free |
| Implicit signals (dwell time, stars, opens) | Explicit profile + topics + thumbs only |
| Percentile/relative badges | Absolute 0–100 scale is stable and comparable |
| Jev advisors, RAG reranking, tool index (`typesafe-spring-ai`) | Not relevant to feed ranking |
| Spring Boot 4.1, react-router 7, frontend major upgrades | No Spring Cloud GA line for Boot 4.1 yet; unrelated churn |
| CONCERNS.md fixes (JSON Feed dates, SSRF DNS rebinding), FeedPanel refactor | Milestone stays focused on ranking |
| Multi-user profiles; multiple replicas | Single-user app, `replicaCount: 1` |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|

**Coverage:**
- v1 requirements: 40 total
- Mapped to phases: 0
- Unmapped: 40 ⚠️

---
*Requirements defined: 2026-09-22*
*Last updated: 2026-09-22 after initial definition*
