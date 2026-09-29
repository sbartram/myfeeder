# Phase 6: Thumbs Feedback - Context

**Gathered:** 2026-09-26
**Status:** Ready for planning

<domain>
## Phase Boundary

The user gives any article a thumbs up or down (reading-pane buttons plus `u`/`d`). Each vote is stored as one row in `article_feedback` and shifts the **learned** adjustment of the topics that article matched. Badges and scores reflect it immediately, with no Jev call. The user sees what changed, can narrow a thumbs-down to chosen topics, and is offered a topic draft when nothing matched. The topic editor shows base and learned weight separately. Covers FDBK-01..07.

Not in this phase: "reset learned adjustment" (FDBK-V2-01), tuning η/cap against real data (Phase 7 OPS-02), and re-judging articles when topics change.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (locked by research and prior phases, not re-discussed)
- **The learned model is derived, not mutated (research R2 / ARCHITECTURE Pattern 5):** a vote writes or deletes one `article_feedback` row, and `interest_topic.weight` is never written. The `learned` CTE in `InterestScoreQueries` (a stub returning 0 today, near line 199) becomes `learned_t = clamp(η × Σ vote × m_t, −L, +L)` with `m_t = max(0, (noul − 0.5) × 2)`, `η = 2` points per vote and `L = 20`. The effective weight is `w_t = clamp(base_t + learned_t, −50, +50)` with the **sign clamp**: a positive base never goes below 0, a negative base never goes above 0, and a base of 0 can move either way. Undo is exact because it's a row delete or flip. Constants go under `myfeeder.interest.blend.*`, and Phase 7 tunes them.
- **The learned CTE must honor `topics_narrowed` (03 D-02):** when it's true, only the `article_feedback_topic` child rows count, and zero child rows means the vote moves nothing. The schema allows narrowing in both directions (03 D-03); the UI offers it only on thumbs-down.
- **One source of truth:** the blend CTE drives the sort, the badge and the breakdown (05 decisions). The effect message's numbers also come from the server. The client never recomputes weights.
- **The Priority query is never invalidated.** It's patched in place (`patchPriorityArticle`, 05-06), and re-ranking happens only through the explicit refresh.
- **No V7 migration is expected.** V6 already has `article_feedback(article_id PK, vote ±1, topics_narrowed, created_at)` and `article_feedback_topic(article_id, topic_id)`, both with cascades.

### Vote UX & badge ripple
- **D-01:** 👍/👎 buttons live **only in the reading-pane toolbar**, next to ★ Star, in every view. `u`/`d` work wherever an article is selected (both keys are free today). Pressing the active vote again removes it, and pressing the other one flips it (FDBK-01).
- **D-02:** **List rows show no vote indicator.** Vote state shows only as the pane buttons' toggled state.
- **D-03:** **You can vote on unscored articles.** The row is stored and starts counting once the article is scored (R2). The message says so, e.g. "Saved — counts once this article is scored".
- **D-04:** **A vote has no side effects.** It doesn't mark read or advance, and read state still follows `autoMarkReadDelay`.
- **D-05:** **Outside Priority**, a vote refreshes **all visible badges**: invalidate `['articles']` and `['article', id]` so every badge re-reads the CTE. These lists sort by date, so order can't shift.
- **D-06:** **Inside Priority**, only the **voted row** is patched in place (its badge and feedback, from the vote response). Every other row keeps its served badge so the frozen list never looks mis-sorted, and the vote sets `rankingChanged` (the "Ranking changed — refresh" hint). This also covers 05-REVIEW WR-05 for vote-driven score rises.

### Effect message
- **D-07:** The message shows the **actual** change in each affected topic's effective weight, not the nominal η × m nudge. The vote endpoint returns each affected topic's effective weight before and after, and the client prints `after − before`. A capped topic shows `+0.0` with a cap note (e.g. "Rust +0.0 (learned at max +20)"). The sign clamp is covered the same way. — **Reversibility:** costly — the vote endpoint's response shape (per-topic before/after) becomes the contract that the toast, the pane and tests consume.
- **D-08:** **One decimal place** (e.g. "Rust +2.0 · Go +0.2").
- **D-09:** **Surface:** the existing success toast (`useToastStore.addToast(msg, 'success')`, 5s). It shows the **top 3 topics by absolute change, then "+N more"**. Zero-change topics are listed only when capped or clamped, to explain the missing effect.
- **D-10:** **Remove and flip use the same format with the net change**, e.g. "Vote removed · Rust −2.0". Flipping up→down shows the full swing ("Rust −4.0").
- **D-11:** The reading pane's **"Why N?" rows show the effective weight with its learned part**, e.g. `Rust 90% × 24 (20 +4 learned) = 22`. 05 D-02's rule still holds: rows sum exactly to the badge.

### Thumbs-down topic picker (FDBK-06)
- **D-12:** **Vote first, narrow optionally.** `d`/👎 saves immediately against all matched topics, so `d` stays a one-key triage action. Narrowing is a later, optional step that re-saves with `topics_narrowed = true` plus child rows.
- **D-13:** **Entry point:** a small **"Narrow…" control in the pane** next to the active 👎, shown whenever the down-vote covers 2+ matched topics, plus **Shift+D** to open the picker. The toast stays text-only.
- **D-14:** **The picker is a popover under 👎:** a checkbox list of the matched topics with match % in the existing chip sign colors, plus Apply/Cancel. Keyboard: `1`–`9` toggle, Enter applies, Esc cancels. It starts with the current subset checked, or all matched topics.
- **D-15:** **Apply is disabled when no topic is checked.** To penalize nothing, remove the vote. The UI never creates a narrowed vote with zero child rows, even though D-02 would allow one.
- **D-16:** **A narrowed down-vote is visible and re-editable.** The pane shows e.g. "👎 Politics only", and clicking it re-opens the picker. Pressing 👎 again still removes the vote (FDBK-01).
- **D-17:** **Flipping a narrowed 👎 to 👍 clears the narrowing** (`topics_narrowed = false`, child rows deleted). The up-vote applies to all matched topics.

### No match → create topic (FDBK-05)
- **D-18:** When a vote on a **scored** article matches no topics (every `m_t = 0`), **the vote is still stored.** It moves nothing now, and it counts only if a later re-score gives it matches.
- **D-19:** **Entry point:** the toast says "No topics matched", and an **inline line in the pane** under the thumbs reads "No topics matched — [Create topic from article]". The line stays while the article is open. This is the same reasoning as D-13: the toast has no actions.
- **D-20:** **"Create topic from article" opens `InterestsDialog`** (reusing MainLayout's `interestsOpen`, as 03-05 and 05 did) with a **new unsaved draft row**. The name is empty and focused, the description is prefilled from the article title, and the weight is **+20 for 👍 / −20 for 👎**. The user edits and saves as normal (negation warning and Preview against this article still work). Nothing is saved automatically.
- **D-21:** **Creating the topic doesn't re-judge the article.** Rubric changes apply to new articles, and manual "Re-score unread" covers the backlog. A vote never causes a Jev call (FDBK-02).

### Claude's Discretion
- **Topic editor display (FDBK-07), not discussed:** how `TopicRow` shows base vs learned. The slider and number stay the base weight, and the learned adjustment (and effective weight) shows alongside as read-only. This needs learned values per topic from the server, from the same CTE, e.g. added to the topics GET.
- The endpoint shape: `PUT /api/articles/{id}/feedback` (`{vote, topicIds?}`) and `DELETE /api/articles/{id}/feedback` (research suggests these), what they return (the re-enriched article plus per-topic before/after for D-07), and how the vote and picks reach the pane (e.g. a `feedback` field on article responses).
- How "matched topics" is defined for the picker and the no-match check: `m_t > 0` from stored nouls, over topics that still exist.
- Where Shift+D and `u`/`d` go in `ShortcutOverlay`, and the exact toast and inline copy.
- Whether a vote also invalidates the topics query (the learned values in the editor), and the interplay with `usePriorityStore`.
- How the draft-row prefill passes into `InterestsDialog` / `TopicRowState` (e.g. a store field or a dialog prop).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scoring and feedback model
- `.planning/research/SUMMARY.md` §R1 (formula), §R2 (feedback derived from `article_feedback`, cap, sign clamp, toast from the same formula), §R6 (hinge gates the nudge)
- `.planning/research/ARCHITECTURE.md` §Pattern 5 "Thumbs nudge as a derived aggregate", plus the blend CTE sketch (its constants η=0.1 / maxDelta=1.5 are **superseded by R1**: η=2, L=20 points)
- `.planning/research/FEATURES.md` T6/T7 (toggle, visible effect), D2 (down-vote topic picker), D3 (create topic from article), T11 (base + learned display)
- `.planning/research/PITFALLS.md`: feedback sign flip / clobbering user weights; list reshuffle under the cursor
- `src/main/resources/db/migration/V6__interest_scoring.sql`: `article_feedback`, `article_feedback_topic` (D-02/D-03 comments)

### Requirements and roadmap
- `.planning/REQUIREMENTS.md`: FDBK-01..07 and the scoring-model header
- `.planning/ROADMAP.md` §Phase 6: success criteria 1–5

### Prior phases
- `.planning/phases/03-interest-model-schema-rubric-editor/03-CONTEXT.md`: D-01..D-03 (feedback schema, `topics_narrowed`), D-07/D-08 (InterestsDialog, per-row saving), D-09 (negation warning), D-10 (weight slider)
- `.planning/phases/05-blend-priority-view/05-CONTEXT.md`: blend CTE single source of truth, D-02 (exact-sum breakdown), D-06 (chips + sign colors), D-07/D-08 (no invalidation, "Ranking changed" hint), D-18 (`interestScore` on every article response)
- `.planning/phases/05-blend-priority-view/05-REVIEW.md`: WR-05 (an unserved row rising past the cursor; votes set the hint)
- `CLAUDE.md` (root): Interest Ranking section, Spring Data JDBC `@Query` rule, GlobalExceptionHandler mappings, frontend conventions, `npx tsc -b`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `repository/InterestScoreQueries.java`: the blend CTE with the `learned` stub (`0::double precision AS delta`), the one place the learned formula goes. Its enrichment variant feeds `interestScore` on every article response.
- `ScoreBreakdowns` / `interestBreakdown` on `GET /api/articles/{id}`: extend its rows with base/learned for D-11. The largest-remainder sum rule is unchanged.
- `hooks/usePriorityArticles.ts` → `patchPriorityArticle(qc, id, patch)`: its doc comment already anticipates Phase 6 thumbs. It needs to accept `interestScore` / feedback fields.
- `stores/priorityStore.ts` → `setRankingChanged(true)`: already called by the interest mutations in `hooks/useInterest.ts`.
- `components/Toast.tsx` → `useToastStore.addToast(msg, 'success')`: text-only, 5s.
- `components/ScoreRow.tsx` (chips, sign colors `.weight-positive` / `.weight-negative`), `WhyBreakdown.tsx`, `InterestsDialog.tsx` + `TopicRow.tsx` (TopicRowState draft rows `d-<n>`), `utils/interest.ts`.
- `components/ReadingPane.tsx` `.reading-toolbar` (★ Star, Board, Raindrop…): thumbs go here.
- `hooks/useKeyboardShortcuts.ts`: `u`, `d` and Shift+D are unused.

### Established Patterns
- Optimistic/in-place cache patches for Priority; `['articles']` invalidation elsewhere (05-06).
- Interest mutations use `meta.inlineError` and skip the global toast (03-05).
- Spring Data JDBC: `@Query` / JdbcTemplate text blocks, no derived queries; a write plus a re-read in one service method.
- GlobalExceptionHandler: `NotFoundException` → 404 for a missing article, `IllegalArgumentException` → 400 (e.g. a picked topic that isn't matched, or an empty narrowed list).

### Integration Points
- New `ArticleFeedbackRepository` + feedback service method; `ArticleController` feedback endpoints.
- `Article` response enrichment gains the vote state (vote + narrowed topic ids) next to `interestScore`.
- The topics GET (or a sibling) exposes per-topic learned/effective weight for FDBK-07.
- `MainLayout` `interestsOpen` plus a draft-prefill channel for D-20.

</code_context>

<specifics>
## Specific Ideas

- Toast examples: "👍 Rust +2.0 · WebAssembly +1.4 · Go +0.2 · +2 more", "Vote removed · Rust −2.0", "Rust +0.0 (learned at max +20)", "No topics matched", "Saved — counts once this article is scored".
- Pane examples: "👎 Politics only" (click to re-open the picker); "No topics matched — [Create topic from article]".
- Why row example: `Rust 90% × 24 (20 +4 learned) = 22`.
- Up, down, up ends as a single up (success criterion 1), and that test is required.

</specifics>

<deferred>
## Deferred Ideas

- Per-topic "reset learned adjustment": already v2 (FDBK-V2-01).
- A vote indicator in list rows and thumbs on list-row hover: considered and rejected for v1 (D-01/D-02). They could come back with PRIO-V2 triage work.
- Re-judging a single article after creating a topic from it: rejected (D-21). Covered by manual Re-score.

</deferred>

---

*Phase: 06-thumbs-feedback*
*Context gathered: 2026-09-26*
