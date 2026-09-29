# Requirements: myfeeder v0.3.0 Engagement Learning

**Defined:** 2026-09-29
**Core Value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

**Milestone goal:** Opening or saving an article teaches the ranking what I care about, with no extra Jev calls, and every learned point stays explainable and reversible.

## v0.3.0 Requirements

### Engagement Capture

- [ ] **CAPT-01**: Opening an article's original link (Open Original button or `o`) records an `OPEN_ORIGINAL` engagement, once per article. The tab always opens, even if recording fails.
- [ ] **CAPT-02**: Starring an article (unstarred → starred) records a `STAR` engagement
- [ ] **CAPT-03**: Adding an article to any board (including Read Later and `b`) records one `BOARD` engagement per article, however many boards it is on
- [ ] **CAPT-04**: A successful Raindrop save records a `RAINDROP` engagement. A failed save, or one blocked by an open breaker, records nothing.
- [ ] **CAPT-05**: Engagement is sticky: unstarring, removing from a board or deleting a board keeps it. Deleting the article or its feed removes it.
- [ ] **CAPT-06**: User can forget an article's engagement with a small reading-pane control that appears only when the article has engagement
- [ ] **CAPT-07**: Nothing else records engagement: not in-body link clicks, selection, reader view, dwell time or auto-mark-read

### Learning

- [ ] **LRN-01**: Each engaged, SCORED article counts once, at its strongest kind (save > open), as a fractional up-vote on the topics it matched. This is derived at query time, with no Jev calls and no writes to topic weights.
- [ ] **LRN-02**: A thumbs vote on an article replaces its engagement contribution, and removing the vote restores it
- [ ] **LRN-03**: Engagement only nudges topics whose base weight is ≥ 0
- [ ] **LRN-04**: Engagement learning has its own cap, below the thumbs cap and added on top of the thumbs-learned adjustment. The effective weight stays within the existing sign clamp and ±50.
- [ ] **LRN-05**: Open weight, save weight and engagement cap are committed yaml constants. Zero disables engagement learning, and startup rejects inconsistent values (save ≤ open, or cap ≥ thumbs cap).
- [ ] **LRN-06**: The Priority order, badge and "Why N?" reflect engagement consistently. Engagement never re-sorts an open Priority list; it sets the "Ranking changed" hint instead.

### Explainability

- [ ] **EXPL-01**: "Why N?" shows each topic's effective weight as base + votes + engagement, and its rows still sum exactly to the badge

### Gap Discovery

- [ ] **GAP-01**: Interests has a "Suggested topics" section listing engaged, SCORED articles that matched no topic
- [ ] **GAP-02**: User can create a topic from a suggestion (a prefilled draft), which removes the suggestion from the list
- [ ] **GAP-03**: User can dismiss a suggestion permanently
- [ ] **GAP-04**: Near-miss articles (best topic noul close to matching) are not suggested, so existing topics don't get duplicated
- [ ] **GAP-05**: Suggestions never call Jev

### Calibration

- [ ] **CAL-01**: The replay SQL and its drift guard are regenerated for the extended CTE in the same change
- [ ] **CAL-02**: The replay reports dormant (engaged-but-unscored) engagement, topics at the cap, the tier histogram with engagement on and off, and a simulated backfill
- [ ] **CAL-03**: The engagement weights and cap are tuned from prod data, committed in yaml and documented in CLAUDE.md

## Future Requirements

Deferred. Tracked but not in the current roadmap.

- **ENG-F1**: Reading-pane engagement status line ("Opened · Starred")
- **ENG-F2**: `engagedUnscored` count on `/api/interest/status`
- **ENG-F3**: "Matched no topic" notice shown after engaging with an unmatched article
- **ENG-F4**: Engaged-but-unscored articles become eligible for one Jev call (decide from CAL-02 data)
- **ENG-F5**: Backfill engagement from existing stars and board articles (decide from the CAL-02 simulation)
- **ENG-F6**: Interests learned line splits votes vs engagement, with contributing-article counts and an at-cap flag
- **ENG-F7**: Feed affinity (per-feed bonus from a smoothed open rate)
- **ENG-F8**: Rate-normalized engagement, and decay of votes and engagement together

## Out of Scope

| Feature | Reason |
|---------|--------|
| Dwell time, selection, reader view or scroll as signals | j/k skimming, auto-mark-read and auto-enabled reader view make them noise |
| Negative signal from skipped (not opened) articles | Skipping usually means lack of time, not disinterest; positive-only |
| Toast on every open or save | Noise; engagement is silent and explained on demand |
| Writing learned points into `interest_topic.weight` | The derived model keeps every learned point reversible |
| LLM-drafted topic suggestions | Contradicts "no extra Jev calls"; user writes the topic |
| Separate weights per save kind (star vs board vs Raindrop) | Needless tuning surface; one save weight |
| Counting repeat opens or saves | One count per article at its strongest kind prevents inflation |
| A "pause learning" UI toggle | Setting the yaml weights to 0 does the same |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|

**Coverage:**
- v0.3.0 requirements: 22 total
- Mapped to phases: 0
- Unmapped: 22 ⚠️

---
*Requirements defined: 2026-09-29*
*Last updated: 2026-09-29 after initial definition*
