# Project Research Summary

**Project:** myfeeder, milestone v0.3.0 Engagement Learning
**Domain:** Implicit (engagement) feedback added to an explainable, query-time interest ranker in a self-hosted, single-user feed reader
**Researched:** 2026-09-29
**Confidence:** HIGH for the integration points and stack (read directly from the code at `8fd4145`). MEDIUM for the product and weighting calls and the browser-API facts.

## Executive Summary

v0.3.0 teaches the existing v0.2.1 learned model from four deliberate implicit signals: opening the original, starring, adding to a board and sending to Raindrop. Reference products (Feedly Leo, Gmail, YouTube, Artifact, Instagram) and the literature (Joachims 2005; Hu, Koren & Volinsky 2008) agree on three things:
- Explicit signals always outrank implicit ones.
- Saves count for more than clicks.
- Learning from clicks on your own ranking creates a rich-get-richer loop.

The scope already decided matches the standard safeguards: positive-only, one count per article at its strongest kind, thumbs override, and a separate lower cap. myfeeder's exact point arithmetic also makes the votes-vs-engagement explanation a real advantage.

The build needs **zero new dependencies**:
- **Storage:** one Flyway V7 migration (`article_engagement` keyed on `(article_id, kind)` with `ON DELETE CASCADE`, plus a dismissal table for gap suggestions) and a `JdbcClient` `ArticleEngagementStore`.
- **Save capture:** server-side, in the three services that own the saves: `ArticleService.updateState`, `BoardService.addArticle`, and `RaindropService.saveToRaindrop` after `createBookmark` succeeds.
- **Open capture:** client-side, through one `openOriginal` helper. It calls `window.open` synchronously, then fires a JSON request without awaiting it.
- **Learning:** a second derived term in `InterestScoreQueries.LEARNED_CTE`. It collapses each article to one strength, drops articles that have a thumbs vote, counts only SCORED articles, applies the same hinge, then caps. Thumbs take their share first, so `base + thumbs_applied + engagement_applied = w` exactly.
- The term never writes `interest_topic` and never calls Jev. Sort, badge, "Why N?" and Interests all read the same CTE, so they stay consistent.

The risks are in the SQL, not the technology:
- **Double counting** when engagement rows are joined before being collapsed per article.
- **Broken "Why N?" sums** when two caps and the sign clamp interact.
- **Engagement that never counts** on articles read before Jev scored them.
- **Saturation and tier inflation** from the feedback loop.
- **Drift-guard lag**, where the calibration replay no longer matches the CTE.

The mitigations are real-Postgres grid tests, splitting the parts in SQL by subtraction, a conservative cap well below `learned-cap`, and committing each CTE change together with the regenerated replay. Ship conservative values, let engagement build up in prod, then calibrate.

**Six design decisions are contested across the research files** (see Open Decisions). Settle them in requirements before the learned-model SQL is written.

## Key Findings

### Recommended Stack

Nothing to install: no `build.gradle.kts` or `package.json` change, and no BOM or version bump. The new files are `V7__article_engagement.sql`, `ArticleEngagementStore`, `EngagementKind`, the open endpoint and service, and one frontend hook.

**Core technologies (new usage only):**
- **PostgreSQL + Flyway V7:**
  - Table: `article_engagement(article_id FK ON DELETE CASCADE, kind CHECK, created_at, PK(article_id, kind))`.
  - `INSERT ... ON CONFLICT DO NOTHING` is idempotent and safe under races.
  - Store only the kind, never a weight.
- **`JdbcClient` store:** follows the `ArticleFeedbackStore` pattern. Don't use a `CrudRepository`: with a composite key and a preset id, `save()` issues an `UPDATE`.
- **`LEARNED_CTE`:**
  - New `engaged` and `eng_learned` CTEs.
  - New parameters `openWeight`, `saveWeight` and `engagementCap`, bound in `learnedSql()`, the single binding point.
- **`myfeeder.interest.blend.engagement.{open-weight, save-weight, cap}`:**
  - Query-time yaml constants, tuned only in committed yaml (D-14).
  - Rules: `0 <= open < save < 1` and `cap < learned-cap` (20).
- **Browser:** plain `fetch` (optionally `keepalive`) after a synchronous `window.open`. Don't use `sendBeacon`:
  - Chrome throws on JSON Blobs.
  - It breaks the JSON-only CSRF rule.
  - jsdom doesn't implement it.

### Expected Features

**Must have (v0.3.0):**
- **E1** Capture "open original" from `o` and the Open Original button(s), once per article. Don't count links inside the article body, or count one only when its `href` equals `article.url`.
- **E2** Capture saves server-side: star (false to true only), board add, and Raindrop on success only.
- **E3/E10** A way to reverse engagement. The mechanism is contested (Open Decision 3).
- **E4** A thumbs vote overrides the article's engagement for every topic. Removing the vote brings the engagement back; the derived model does this for free.
- **E5** Weights: save > open > 0, both below one vote. Starting values: open 0.25, save 0.5.
- **E6** A separate, lower engagement cap: provisionally 8 to 10, against 20 for thumbs.
- **E7** Engagement on an unscored article is dormant, and the UI doesn't suggest otherwise.
- **E8** "Why N?" splits each topic's weight into base, votes and engagement, as a note on the topic's weight rather than as extra point rows.
- **E9** The Interests learned line shows votes and engagement separately, with an at-cap flag and a count of contributing articles.
- **E11** Engagement never re-sorts the list live. Patch the row, set the "Ranking changed" hint, and never invalidate `['priority']`.
- **E12** Show the "matched no topic" notice for engaged articles too, reusing `FeedbackNotice` and `TopicDraft` with a +20 draft.
- **D1 + D2** A gap-discovery list in Interests, with a stored dismissed/handled state. Articles are never re-judged against new topics (R5), so without that state suggestions never clear.
- Calibration, plus extending the replay drift guard.

**Should have:**
- **D3** An engagement glyph or line on the article, which also anchors the undo control and the explanation.
- **Suggestion order:** either "by surprise", lowest blended score first (FEATURES), or by strength then recency (ARCHITECTURE and PITFALLS).
- **Near-miss filter:** exclude articles whose best noul is above a threshold (about 0.35) and show the closest existing topic, so the user doesn't create duplicates.
- **Status count:** an `engagedUnscored` field on `/api/interest/status` (appended, per the status contract).

**Defer:**
- D4: engaged articles become eligible for scoring (contested).
- D5: backfill from existing stars and boards (contested).
- D7: record which view an open came from.
- Copy Link as a signal.
- Rate-normalized engagement.
- Feed affinity.
- "Reset reading history".
- Decaying votes and engagement together.

**Anti-features (all files agree):**
- Dwell time, selection, reader view or scroll as signals.
- Negative signals from skipped articles.
- A toast on every open.
- Writing learned points into `interest_topic.weight`.
- LLM-drafted suggestions.
- Separate weights per save kind.
- Counting repeat opens or saves.
- Decaying engagement but not votes.
- A "pause learning" toggle in the UI. Setting the weights to 0 in yaml does the same.

### Architecture Approach

Record each engagement where the action is owned: the backend for saves, the client only for opens. Store only facts. All learning stays a derived term in the single `LEARNED_CTE`, so every learned point can be undone by deleting rows. The explanation splits a topic row's weight (`base + thumbsLearned + engagementLearned`). The integer point rows and their largest-remainder rounding (D-02) are unchanged. New DTO fields are appended, never renamed.

**Major components:**
1. **V7 migration:** `article_engagement` plus `topic_suggestion_dismissal`, both in one migration like V6.
2. **`ArticleEngagementStore`:** depends only on `JdbcClient`, so Article, Board and Raindrop services can use it without a dependency cycle.
3. **Capture hooks:**
   - **STAR:** make `updateState` transactional, or use a targeted `UPDATE ... WHERE starred = false` and record only when it changes a row.
   - **BOARD:** in `addArticle`.
   - **RAINDROP:** after `createBookmark` returns, outside the circuit-breaker bean.
   - **Opens:** an endpoint that accepts only the open kind.
4. **`LEARNED_CTE` pipeline:**
   - Chain: `engaged` → `eng_learned` → `eff` → `eff2` (`w_thumbs`, `w`) → `contrib` (`thumbs_applied`, and `engagement_applied` by subtraction).
   - `WITH learned AS` must stay the first token, because the drift guard looks for it.
5. **Explanation fields and UI:**
   - DTOs: `TopicContribution`, `Row`, `TopicWeight`, `TopicLearned`, `Article.engagement` (by-id only).
   - `LearnedLimit` must check limits on `base + learned + engagement`.
   - UI: the WhyBreakdown label, `TopicRow.LearnedLine`, and the vote toast saying the vote "replaces engagement".
6. **`TopicSuggestionService` and its routes:** read-only apart from the dismissal row; tests assert `verifyNoInteractions(jevApiClient)`.
7. **Frontend:**
   - `useOpenOriginal` is the only way to open an original link.
   - One shared reaction after any engagement: patch the Priority row, set the hint, invalidate `['interest','learned']` and `['interest','suggestions']`, and refresh the article by id.
8. **Replay script and drift test:**
   - Regenerate the replay byte for byte in the same plan as the CTE change.
   - New psql `-v` variables must have the same names as the JdbcClient parameters.
   - No kind name or alias may contain a word the replay's write-keyword check rejects; `COPY_LINK` would trip it.

### Critical Pitfalls

1. **Double counting.**
   - Collapse to one `MAX` strength per article before joining topics.
   - Any thumbs vote on the article removes all its engagement (`NOT EXISTS`).
   - One BOARD row per article, however many boards it is on.
   - Test: open + star + 2 boards + Raindrop on one article counts once.
2. **Two caps plus the sign clamp break the exact split.**
   - Formulas: `w1 = signClamp(base + t)`, `w = signClamp(base + t + e)`, `thumbs_applied = w1 − base`, `eng_applied = w − w1`.
   - Compute the parts in SQL, and get the rounded engagement part by subtraction.
   - Add a grid property test over base, thumbs and engagement values.
3. **Engaged-but-unscored articles never count.** An article is auto-marked read after 1 s, and scoring only picks up unread articles. The learned model and gap discovery must require `status = 'SCORED'`. Make the loss visible with the status count or a reading-pane hint.
4. **Saturation from the feedback loop.**
   - The cap is the only brake: keep it at about half of `learned-cap` or less.
   - Record `created_at` on every row now, so decay stays possible later.
   - Calibration must report which topics are at the cap, and the tier histogram with engagement on and off.
5. **Opens that are lost or blocked.** Awaiting the request before `window.open` gets the tab blocked, and `noopener` always returns `null`, so you can't even tell. Open synchronously, fire and forget, show no error toast, route every open through one helper, and keep Open Original a `<button>`.
6. **Others:**
   - Saves recorded in the wrong layer.
   - A missing cascade that breaks feed delete.
   - The drift guard and `DevProfileConfigTest` needing the new yaml keys mirrored.
   - The CTE slowing as engagement rows grow. Measure it with 20k seeded rows, and keep the open response lightweight.

## Open Decisions (the research files disagree; settle in requirements)

**1. Should engagement move topics with a negative base weight?** Lean: skip.
- **Skip** (FEATURES E6b, PITFALLS 9): opening curiosity headlines on a −30 topic would pull it toward 0 and let buried articles come back. Softening a negative topic should take an explicit 👍. Implement with `CASE WHEN base < 0 THEN 0` on the engagement part.
- **Soften** (ARCHITECTURE D-B): an engagement is literally a fractional up-vote, the same as an unnarrowed 👍. The cap bounds it, and the sign clamp never flips the sign.

**2. One combined cap, or separate caps that add up?** Lean: additive.
- **Combined** (FEATURES E6a): positive learning is capped at `min(thumbs + engagement, learned-cap)`. Thumbs claim their share first and engagement fills what's left, so implicit learning can never push a topic further than votes could.
- **Additive** (ARCHITECTURE D-D, PITFALLS 2): engagement gets its own cap on top of the thumbs cap, so learning can reach `learnedCap + engageCap` (for example 30) before the sign clamp and the ±50 range apply. The milestone's wording, "its own cap below the thumbs cap", reads as additive.
- Both sides agree that thumbs claim their share first. Whichever is chosen, the UI and CLAUDE.md must say it.

**3. Do unstarring and removing from a board undo the engagement?** Lean: sticky, with an explicit Forget control in v0.3.0.
- **Reversible** (FEATURES E3/E10): unstar deletes the STAR row, and removal from the article's last board deletes BOARD, so an accidental `s` doesn't stick. Deleting a whole board keeps the rows. A per-article "Don't count" tombstone stops a later `o` from quietly re-adding the engagement.
- **Sticky** (ARCHITECTURE D-C, PITFALLS 8): clearing a read-later queue after reading is normal, not a sign of disinterest. Undo comes from a thumbs vote plus an explicit `DELETE /api/articles/{id}/engagement` ("Forget engagement"). ARCHITECTURE says Forget can be deferred; PITFALLS says the "every learned point stays reversible" goal requires it.
- Still to choose: a tombstone (survives a later `o`) or deleting the rows.

**4. Backfill existing stars and boards in V7?** Lean: no backfill, plus a simulated-backfill section in the replay; revisit after that preview.
- **Yes** (ARCHITECTURE D-A): idempotent, only SCORED articles count, and calibration gets real data from day one.
- **No** (PITFALLS 16): it would push topics to the cap and reshuffle Priority on release day, and the kinds would be uneven, since opens and Raindrop saves have no history.
- FEATURES D5 treats it as P2, to be decided before calibration.

**5. Open endpoint shape.** Lean: either is CSRF-safe. Prefer a lightweight response and refetch the article by id. Also settle the kind name (`OPEN` or `OPEN_ORIGINAL`).
- **Option A** (STACK): `PUT /api/articles/{id}/engagement/open`, no body, 204. It is an idempotent verb for an idempotent insert, and a PUT is never a CORS simple request, so it needs no content-type guard.
- **Option B** (ARCHITECTURE, PITFALLS): `POST /api/articles/{id}/engagement`, JSON-only, body `{kind}`.
  - ARCHITECTURE returns the article with its re-blended breakdown.
  - PITFALLS 12 says return 204, because the breakdown costs two extra learned-CTE runs per open.

**6. Should engaged-but-unscored articles become eligible for scoring?** Lean: defer. Ship the `engagedUnscored` count and decide from prod data.
- **Later, yes** (FEATURES D4, P2): change the eligibility rule to `(read = false OR engaged)`. It costs at most one Jev call per engaged article.
- **Not as a side effect** (PITFALLS 3): the eligibility rule also drives the sweep, the `/status` counts and Re-score, and the milestone promises no Jev calls for engagement. If wanted, make it a separate, costed decision.
- STACK and ARCHITECTURE accept that such engagement stays dormant.

**Minor disagreements:**
- **Cache on engagement:** ARCHITECTURE invalidates `['articles']`; the other three refresh only the article by id. Lean: by id, plus the Priority patch.
- **Gap test:** "every topic's hinge is 0" (FEATURES, ARCHITECTURE) or "best noul below a near-miss threshold" (PITFALLS).

## Implications for Roadmap

### Phase 8: Engagement Capture
- **Why first:** everything later needs rows, and the ranking doesn't change. Releasing it early lets prod data build up.
- **Delivers:**
  - The V7 migration (both tables), `EngagementKind` and `ArticleEngagementStore`.
  - Server-side capture of STAR, BOARD and RAINDROP.
  - The open endpoint, and `useOpenOriginal` wired to the button(s) and `o`.
- **Addresses:** E1, E2, and the capture side of E3/E10.
- **Avoids:** Pitfalls 5, 6, 7, 19, 20, 21.
- **Must settle first:** Open Decisions 3, 4 and 5.

### Phase 9: Learned Model Extension
- **Why here:** it is the riskiest SQL, and everything after it reads its output.
- **Delivers:**
  - The new CTEs, the thumbs-first split, the config properties, the appended DTO fields and the `LearnedLimit` fix.
  - In the same plan: the regenerated replay, the psql variables, the drift-guard update, and the yaml keys in both the test yaml and the dev overlay.
  - Main yaml can keep `engagement.cap: 0`, so prod behavior doesn't change until calibration.
- **Addresses:** E4 to E7.
- **Avoids:** Pitfalls 1, 2, 3, 4, 9, 11, 12, 17. Includes the 20k-row latency budget.
- **Must settle first:** Open Decisions 1 and 2.

### Phase 10: Explainability and Cache Reactions
- **Why here:** it needs Phase 9's new fields.
- **Delivers:**
  - `Article.engagement`, the WhyBreakdown note, and the LearnedLine split with article counts and the at-cap flag.
  - The reading-pane engagement line with the Forget or Don't-count control.
  - The post-engagement reaction, the toast copy, and optionally the `engagedUnscored` status count.
- **Addresses:** E8 to E11 and D3.
- **Avoids:** Pitfalls 13, 14, 15.

### Phase 11: Gap Discovery
- **Why here:** it needs only Phase 8's tables, so it can run in parallel with Phases 9 and 10.
- **Delivers:**
  - The suggestions query and endpoints, plus the dismissed/handled state.
  - A "Suggested topics" section in Interests, with `addDraft(draft)`.
  - E12: the "matched no topic" notice for engaged articles.
- **Addresses:** D1, D2, E12.
- **Avoids:** Pitfalls 10 and 18.

### Phase 12: Calibration and Rollout
- **Why last:** it needs real engagement data. Follow the v0.2.0 to v0.2.1 pattern: ship conservative values, let data build up for 2 to 4 weeks, run the replay, then tune.
- **Delivers:**
  - Replay runs over candidate open weight, save weight and engagement cap, reporting at-cap topics and the tier histogram with engagement on and off.
  - A CALIBRATION note and the tuned yaml.
  - CLAUDE.md updates: the V7 schema line, the Interest Ranking learned model, the capture rules in Key Behaviors, and the tuning levers.
  - A minor release.
- **Avoids:** Pitfalls 4, 11, 16, 23.

### Research Flags

- **Needs `--research-phase`:**
  - Phase 9: correctness under the combined clamps, exact sums, regenerating the drift guard, and the latency budget. Plan it test-first.
  - Phase 11: small UX calls (placement, ordering, near-miss threshold, dismissal) and the `addDraft` change.
- **Standard patterns:** Phase 8 (copies the V6 and `ArticleFeedbackStore` patterns), Phase 10 (appends fields, reuses the vote reaction), Phase 12 (repeats the v0.2.1 calibration workflow).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Checked in the code and `node_modules`. The browser facts are MEDIUM, cross-checked against MDN and Chromium/Mozilla sources. |
| Features | MEDIUM | Competitor behavior comes from vendor docs and blogs. The academic findings are HIGH. The weights are product judgment. |
| Architecture | HIGH | Read at `8fd4145`. The design choices flagged as decisions are MEDIUM. |
| Pitfalls | HIGH | The codebase-specific pitfalls are verified. The bias and saturation advice is MEDIUM. |

**Overall confidence:** HIGH on how to build it; MEDIUM on the values and decisions.

### Gaps to Address

- The six Open Decisions. Decisions 1 to 3 must be settled before Phases 8 and 9.
- The starting values (open 0.25, save 0.5, cap 8 to 10) are untested until Phase 12.
- How often engagement goes dormant in prod is unknown. Measure it before deciding D4.
- CTE latency at scale is unmeasured.
- The claim that Feedly Leo learns from opens and skips is unverified. It doesn't affect the design.
- CLAUDE.md still says "later milestone phases add no migrations", which V7 makes false.

## Sources

### Primary (HIGH confidence)
- The codebase at `8fd4145`: `InterestScoreQueries`, `ArticleScoreStore`, `ArticleFeedbackStore`, `ScoreBreakdowns`, the Article/Board/Raindrop services, the V1/V2/V6 migrations, `ReadingPane.tsx`, `useKeyboardShortcuts.ts`, `priorityStore.ts`, `FeedbackNotice.tsx`, `TopicRow.tsx`, `InterestsDialog.tsx`, the replay script and its test, and the build files.
- The PostgreSQL docs on WITH queries.
- Joachims 2005; Hu, Koren & Volinsky 2008; Chen et al. (TOIS).

### Secondary (MEDIUM confidence)
- MDN: `window.open`, `sendBeacon`, `keepalive`, `auxclick`.
- Chromium blink-dev (beacon Blob types); Firefox 133 release notes and Bugzilla.
- NewsBlur, Inoreader, and YouTube/Gmail Help pages; Instagram's posts; coverage of Artifact; unbiased learning-to-rank papers.

### Tertiary (LOW confidence)
- Third-party reviews saying Feedly Leo learns from opens and skips.

---
*Research completed: 2026-09-29*
*Ready for roadmap: once Open Decisions 1 to 3 are settled*
