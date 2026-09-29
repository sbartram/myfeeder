---
status: diagnosed
phase: 05-blend-priority-view
source: [05-VERIFICATION.md]
started: 2026-09-25T20:42:41Z
updated: 2026-09-26T01:37:35Z
audit_acknowledged:
  milestone: v0.2.1
  at: 2026-09-29
  gap_snapshot: "diagnosed::scenarios=0"
---

## Current Test

[testing complete]

## Tests

### 1. Live triage walkthrough on /priority

expected: Rows stay in place and dim on read/star; no refetch on window focus; topic weight save lights the Ranking changed hint; r reloads page 1; browser reload on /priority serves the SPA.
result: pass

### 2. Reading-pane score explanation walkthrough

expected: For a scored article, the score row shows the badge and matched-topic chips; i opens "Why N?" and its last line equals the badge (Score N / capped at 100 / floored at 0); it stays open across j; the non-matching footer collapses when the article changes.
result: pass

### 3. Visual check across all 6 themes

expected: Badge pill sizing and tier colors (low/neutral/high) read correctly; dimmed read rows keep a dimmed badge; the "Not yet scored" separator strip, the status banner, and the Ranking changed hint label look right in every theme.
result: pass

### 4. Narrow-width layout

expected: Long titles wrap under themselves without pushing the badge; the banner wraps; the hint label fits at the narrowest list-panel width; matched-topic chips wrap; long breakdown labels wrap without overlapping the points column.
result: pass

### 5. Sort-key precision argument (05-01 backstop truth)

expected: Accept or reject: casting the 6-decimal numeric raw score (magnitude well below 10^4, ~10 significant digits) to float8 (15+ digits) for the sort key never merges two distinct raws, so it cannot reorder or duplicate rows.
result: pass

### 6. Dev-only double fetch under React StrictMode

expected: On first entry to /priority in the dev server, page 1 may be fetched twice; the list still renders correctly with no duplicates.
result: pass

### 7. Decide on code-review warnings WR-01..WR-03

expected: Decide whether to fix now or defer — WR-01 (breakdown read in two statements, rows can disagree with total under a concurrent topic edit), WR-02 (id-only cursor can silently skip rows when the cursor article's score drops mid-walk; recommended before Phase 6), WR-03 (topic save/re-score does not invalidate reading-pane and list article caches). See 05-REVIEW.md.
result: issue
reported: "fix WR-02 now, defer WR-01 and WR-03"
severity: major

## Summary

total: 7
passed: 6
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-05-7
  truth: "Priority keyset pagination never silently skips rows when the cursor article's live score changes between pages (WR-02)"
  status: failed
  reason: "User reported: fix WR-02 now, defer WR-01 and WR-03"
  severity: major
  test: 7
  root_cause: "InterestScoreQueries.priorityPageAfter re-derives the cursor's sort_score from the live blend (SELECT c.sort_score FROM keyed c WHERE c.id = :cursorId); loaded pages are frozen client-side but the page boundary is not, so a score drop (topic weight lowered/deleted, re-score, Phase 6 votes) starts the next page below the cursor's old position and skips rows"
  artifacts:
    - path: "src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java"
      issue: "priorityPageAfter (lines ~105-116) recomputes cursor sort key from the live blend"
    - path: "src/main/frontend/src/hooks/usePriorityArticles.ts"
      issue: "passes an id-only cursor (lines ~13-29)"
  missing:
    - "Cursor carries the sort tuple as served (sort_score, sort_date, id), e.g. an opaque encoded cursor, and the next-page query compares against those literal values: (k.sort_score, k.sort_date, k.id) < (:cursorScore, :cursorDate, :cursorId)"
    - "Test: lowering the cursor article's score between page fetches does not skip rows"
  debug_session: ".planning/phases/05-blend-priority-view/05-REVIEW.md#wr-02"

## Deferred Follow-Ups

- test: 7
  idea: "WR-01 (breakdown read in two statements without a transaction) — deferred by user"
  deferred_at: 2026-09-25
- test: 7
  idea: "WR-03 (topic save/re-score does not invalidate reading-pane and list article caches) — deferred by user"
  deferred_at: 2026-09-25
