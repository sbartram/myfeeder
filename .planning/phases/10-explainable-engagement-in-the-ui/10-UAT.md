---
status: testing
phase: 10-explainable-engagement-in-the-ui
source: [10-VERIFICATION.md]
started: 2026-09-30T21:05:35.357Z
updated: 2026-09-30T21:05:35.357Z
---

## Current Test

number: 1
name: Manual UAT (harvested from 10-04 Task 2 <human-check>). Run ./gradlew bootTestRun (export MYFEEDER_TYPESAFE_API_KEY for live scoring) and cd src/main/frontend && npm run dev. On /priority pick a scored article: (1) press o; (2) star it, add it to a board with b, save to Raindrop if configured, then Forget; also repeat an open with no score change; (3) press the hint; (4) vote thumbs-up on an engaged article, then remove the vote; (5) open Interests.
expected: |
  (1) The tab opens at once, the refresh button reads '↻ Ranking changed — refresh', the row keeps its place, its badge and 'Why N?' update. (2) Each action behaves the same; a repeat open with no score change lights nothing. (3) The list re-ranks; the row's position, its badge in Priority and the feed list, the reading-pane badge and 'Why N?' all agree. (4) The toast reads '(replaces engagement)', and removing the vote reads '(engagement restored)'. (5) A topic's line reads 'Learned … (votes …, engaged …) · Effective weight …'.
awaiting: user response

## Tests

### 1. Manual UAT (harvested from 10-04 Task 2 <human-check>). Run ./gradlew bootTestRun (export MYFEEDER_TYPESAFE_API_KEY for live scoring) and cd src/main/frontend && npm run dev. On /priority pick a scored article: (1) press o; (2) star it, add it to a board with b, save to Raindrop if configured, then Forget; also repeat an open with no score change; (3) press the hint; (4) vote thumbs-up on an engaged article, then remove the vote; (5) open Interests.
expected: (1) The tab opens at once, the refresh button reads '↻ Ranking changed — refresh', the row keeps its place, its badge and 'Why N?' update. (2) Each action behaves the same; a repeat open with no score change lights nothing. (3) The list re-ranks; the row's position, its badge in Priority and the feed list, the reading-pane badge and 'Why N?' all agree. (4) The toast reads '(replaces engagement)', and removing the vote reads '(engagement restored)'. (5) A topic's line reads 'Learned … (votes …, engaged …) · Effective weight …'.
result: [pending]

### 2. Resolve judgment-tier prohibition (10-03): 'MUST NOT print a weight or part the client computed; every number shown is a server field, only rounded for display'.
expected: Confirm or reject. Non-authoritative LLM verdict: SATISFIED. WhyBreakdown.TopicLabel prints row.thumbsWeight / row.engagementWeight / row.baseWeight / row.weight via formatSigned/formatDelta only; TopicRow.LearnedLine prints thumbsLearned / engagementLearned / learned / effectiveWeight; the toast prints after − before, a delta of two server values that predates Phase 10. No part is derived from another.
result: [pending]

### 3. Decide on code-review WR-01 (engagement refetch vs a thumbs vote on the same article). Reproduce: on /priority press s then u within one GET round trip (throttle the network in devtools to widen the window).
expected: Either accept the race as a known edge, or fix it before shipping (cancel ['article', id] in press()/narrow() and ignore a GET a vote overtook, per 10-REVIEW.md). If the GET resolves after the vote's onSuccess, the reading pane can show the vote unpressed and the Priority row can be patched back to the pre-vote score until the next refresh.
result: [pending]

### 4. Decide on code-review WR-02: narrowed thumbs-down on an engaged article, e.g. narrowed to Rust on an article also matching Go.
expected: Toast currently reads '👎 Narrowed · Rust −2.7 (replaces engagement) · Go −0.2' — Go's drop (its engagement share leaving) has no note, and removing the vote shows 'Go +0.2' with no '(engagement restored)'. Accept as D-11 written (pinned by narrowedDownVoteOnAnEngagedArticleMarksOnlyThePickedTopic) or change the marker rule/wording.
result: [pending]

### 5. Decide on code-review WR-04: a topic whose votes and engagement cancel (thumbsLearned −1.5, engagementLearned +1.5).
expected: Interests currently reads 'No learned adjustment yet · Effective weight +20' while 'Why N?' shows '(+20 −1.5 votes +1.5 engaged)'. Accept or fix (take the 'No learned adjustment yet' branch only when both parts round to zero).
result: [pending]

### 6. Decide on board-list badge freshness (verifier finding): star or open an article that sits on a board, refresh Priority, then open that board within 30 s of its last load.
expected: BoardArticleList renders InterestBadge from ['boardArticles', boardId], which invalidateAfterLearnedChange and refreshPriority never invalidate, so the board list can show the pre-engagement badge for up to the 30 s staleTime. Accept (the vote path has had the same gap since v0.2.1 and D-07 mirrored it) or add ['boardArticles'] to invalidateAfterLearnedChange.
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
