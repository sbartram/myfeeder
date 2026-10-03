# Phase 11: Gap Discovery - Context

**Gathered:** 2026-10-01
**Status:** Ready for planning

<domain>
## Phase Boundary

A "Suggested topics" section in the Interests dialog lists articles the user engaged with (open or save) that are SCORED and matched no topic. From each suggestion the user can create a topic (prefilled draft) or dismiss it permanently. Listing, creating from and dismissing suggestions never call Jev (GAP-01..GAP-05).

Not in this phase: the "matched no topic" notice after engaging (ENG-F3), clustering, LLM-drafted topic descriptions, re-judging articles against new topics.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (settled earlier; do not reopen)
- Phase 8 D-13: `topic_suggestion_dismissal(article_id BIGINT PK → article ON DELETE CASCADE, reason CHECK IN ('DISMISSED','TOPIC_CREATED'), created_at)` already exists in V7. **No new migration in this phase.**
- Phase 8 D-14: a handled suggestion stays handled. Deleting a topic created from a suggestion does not bring the suggestion back.
- Phase 3 D-20 draft shape: `TopicDraft = { description: title.trim().slice(0, 500), weight: 20 | -20 }`.
- Phase 3 D-21: articles are never re-judged. That is why the dismissal row, not a derived rule, clears a suggestion.
- "Matched" has one definition: a stored `article_topic_score.noul > 0.5` (hinge > 0), the same predicate as `InterestScoreQueries` matched topics and the frontend `matchedTopics`.
- Phase 10 / roadmap: add the suggestions query key to the shared post-engagement reaction (`afterEngagement` in `hooks/engagementReaction.ts`), so suggestions engaged since the dialog last opened appear (SC-1).
- DTO/enum rule: append, never rename. Kind names and SQL aliases must pass the replay write-keyword check.

### List placement & rows
- **D-01:** The "Suggested topics" section sits **below Topics** in the Interests dialog, before the Re-score footer.
- **D-02:** The section is **hidden when there are no suggestions**, with no empty-state note.
- **D-03:** There is no cold-start or Jev-unconfigured gating. Rows require SCORED articles, so those states yield an empty list anyway.
- **D-04:** Each row shows the **title (plain text, not clickable)**, the **feed name** and the article's **score badge** (the existing `InterestBadge` with tiers), plus **Create topic** and **Dismiss** actions. Engagement kind and date are not shown.

### Ordering, window & cap
- **D-05:** Order by the **badge score ascending** (lowest first, "surprise" order), the same blended score the badge shows. Ties go to the most recent engagement.
- **D-06:** Only articles whose **latest engagement is within the last 30 days** are eligible. The window is measured on `article_engagement.created_at`.
- **D-07:** Show at most **10** rows. The header shows a total when more qualify, e.g. `Suggested topics (10 of 23)`, so the response carries a total count alongside the items.

### What counts as a gap
- **D-08:** An article is a suggestion when all of these are true:
  - it has at least one `article_engagement` row of **any kind** (opens included) inside the D-06 window
  - its `article_score.status = 'SCORED'` (unscored, FAILED and SKIPPED are never suggested)
  - it has **no `article_feedback` row** (any vote, 👍 or 👎, excludes it; a 👍 with no match already gets the reading-pane notice)
  - it has **no `topic_suggestion_dismissal` row**
  - its **best noul across all its topic rows is below the near-miss threshold**
- **D-09:** The near-miss threshold is **0.35**: an article whose best noul is ≥ 0.35 is left out. It's a yaml constant (e.g. `myfeeder.interest.suggestions.near-miss`), applied at query time so Phase 12 can tune it. Follow the existing constant rules:
  - identical literals in main and test yaml (dev overlay untouched)
  - startup validation in `MyfeederProperties` (planner picks the exact bounds, e.g. 0 < x ≤ 0.5)
- **D-10:** A match to **any** topic counts as covered, including a negative-weight topic. The near-miss check spans all topics regardless of weight sign.
- **D-11:** A SCORED article with **no topic rows at all** is a gap (best noul treated as 0).

### Create & dismiss flow
- **D-12:** Create topic adds a **+20** draft (`description = title.trim().slice(0, 500)`). The `TopicDraft` weight type stays `20 | -20`. The draft gains an optional `sourceArticleId`.
- **D-13:** TOPIC_CREATED is written **atomically with the topic save**. `POST /api/interest/topics` accepts an appended optional `sourceArticleId` and inserts the dismissal row (reason `TOPIC_CREATED`) in the same transaction as the topic insert. — **Reversibility:** costly — `sourceArticleId` becomes part of the topic-create request contract used by two UI paths.
- **D-14:** The reading-pane `FeedbackNotice` "Create topic from article" also passes `sourceArticleId`, so a topic saved from it marks the article handled. There is one draft path.
- **D-15:** Create topic adds the draft to the **already-open** Interests dialog. `TopicsSection.addDraft` must accept a `TopicDraft`; today a draft is seeded only once, at open.
- **D-16:** While a suggestion's draft is unsaved, its row stays in the list, **disabled and marked "Draft added"**, so it can't be added twice. It disappears when the topic saves. If the unsaved draft row is removed, the suggestion becomes active again.
- **D-17:** Dismiss is **immediate**: no confirm, no undo toast, and no un-dismiss endpoint. It writes reason `DISMISSED`.
- **D-18:** At 25 topics (`TOPICS_MAX`), a suggestion's Create topic is **disabled with the tooltip** "You have 25 topics, the maximum." Dismiss still works.

### Claude's Discretion
- Route shapes, e.g. `GET /api/interest/suggestions` → `{items, total}` and `POST` or `PUT /api/interest/suggestions/{articleId}/dismiss` (204, idempotent; 404 for an unknown article), plus any JSON-only or CSRF-style guard consistent with the other interest routes.
- Service and query placement (`TopicSuggestionService` + a read-only query, or a method next to `InterestScoreQueries`). The query must not touch Jev.
- Conflict behavior when a dismissal row already exists at topic save (e.g. `ON CONFLICT DO NOTHING`, keeping the first reason), and what happens when `sourceArticleId` points at a missing article (ignore vs 404), as long as the topic save itself isn't blocked by a stale suggestion.
- The query key name (e.g. `['interest', 'suggestions']`) and which mutations invalidate it: engagement (via `afterEngagement`), votes, topic create and delete, dismiss, and Re-score.
- Refetch-on-open behavior so the list is fresh each time the dialog opens.
- Styling of rows and the "Draft added" state, and how the feed name and badge are laid out.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/ROADMAP.md` § Phase 11: goal, success criteria and notes (research flag on placement, ordering, near-miss and `addDraft(draft)`)
- `.planning/REQUIREMENTS.md`: GAP-01..GAP-05, and ENG-F3 (deferred)

### Prior decisions
- `.planning/phases/08-engagement-capture/08-CONTEXT.md`: D-13/D-14 dismissal table and its lifecycle
- `.planning/phases/10-explainable-engagement-in-the-ui/10-CONTEXT.md`: D-05..D-08 post-engagement reaction (`afterEngagement`)

### Research
- `.planning/research/ARCHITECTURE.md` § Pattern 4 (gap discovery as a read-only derived list plus one dismissal row), Anti-Pattern 8 (no LLM), query-key table
- `.planning/research/FEATURES.md`: D1/D2 (gap list and lifecycle, order by surprise)
- `.planning/research/PITFALLS.md`: Pitfall 3 (SCORED requirement), Pitfall 10 (duplicates, never clears, bills Jev)
- `.planning/research/STACK.md` § Gap discovery

### Schema
- `src/main/resources/db/migration/V7__engagement.sql`: `article_engagement`, `topic_suggestion_dismissal`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `components/InterestsDialog.tsx`: `TopicDraft`, `TopicsSection` (seeds rows once; `addDraft()` currently takes no draft; `TOPICS_MAX` at-max state), `draftBlocked` notice
- `components/FeedbackNotice.tsx`: the existing "Create topic from article" draft builder, which gains `sourceArticleId`
- `App.tsx` (lines ~81–155): the `interestsDraft` one-shot state passed to `InterestsDialog`
- `components/InterestBadge.tsx` + `TierContext`: the score badge for each row
- `hooks/engagementReaction.ts`: `afterEngagement` / `invalidateAfterLearnedChange`, which add the suggestions key
- `hooks/useInterest.ts`: home for `useTopicSuggestions` / `useDismissSuggestion`
- `repository/ArticleFeedbackStore`, `ArticleEngagementStore`: `JdbcClient` store pattern for the dismissal writes
- `repository/InterestScoreQueries`: matched-topic predicate `GREATEST(0, (ts.noul - 0.5) * 2) > 0`, and blended score SQL for the badge score used in ordering

### Established Patterns
- Interest constants live in yaml and are applied at query time, validated by `MyfeederProperties implements Validator`. `DevProfileConfigTest` enforces main/test/dev-overlay parity.
- Interest routes stay under `/api/interest`, with fixed-text 400s and JSON-only bodies on mutating routes (see rescore's `{"confirm": true}`).
- List responses with a total: precedent in `PaginatedResponse` (`items`).

### Integration Points
- `InterestController` / `InterestService.createTopic`: the optional `sourceArticleId` and the transactional dismissal insert
- The `InterestsDialog` body order: notices → Profile → Topics → **Suggested topics** → Re-score footer

</code_context>

<specifics>
## Specific Ideas

- Header copy: `Suggested topics (10 of 23)` when capped.
- The disabled state on a suggestion row reads "Draft added".
- At-max tooltip: "You have 25 topics, the maximum."
- "Surprise" ordering is the point: the articles the model expected you to ignore but you engaged with are the real gaps.

</specifics>

<deferred>
## Deferred Ideas

- ENG-F3: the "matched no topic" notice after engaging (roadmap-deferred)
- An undo or un-dismiss for dismissed suggestions (rejected for now; dismissal is permanent)
- Showing the engagement kind and date on suggestion rows (not chosen)

</deferred>

---

*Phase: 11-gap-discovery*
*Context gathered: 2026-10-01*
