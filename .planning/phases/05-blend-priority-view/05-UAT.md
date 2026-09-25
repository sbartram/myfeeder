---
status: testing
phase: 05-blend-priority-view
source: [05-VERIFICATION.md]
started: 2026-09-25T20:42:41Z
updated: 2026-09-25T20:42:41Z
---

## Current Test

number: 1
name: Live triage walkthrough on /priority
expected: |
  Marking rows read and starring them keeps them in place (read rows dim, badge kept); switching windows and waiting does not refetch or reorder; saving a topic weight lights the "↻ Ranking changed — refresh" hint; pressing r reloads page 1 and clears the hint; reloading the browser on /priority serves the app.
awaiting: user response

## Tests

### 1. Live triage walkthrough on /priority
expected: Rows stay in place and dim on read/star; no refetch on window focus; topic weight save lights the Ranking changed hint; r reloads page 1; browser reload on /priority serves the SPA.
result: [pending]

### 2. Reading-pane score explanation walkthrough
expected: For a scored article, the score row shows the badge and matched-topic chips; i opens "Why N?" and its last line equals the badge (Score N / capped at 100 / floored at 0); it stays open across j; the non-matching footer collapses when the article changes.
result: [pending]

### 3. Visual check across all 6 themes
expected: Badge pill sizing and tier colors (low/neutral/high) read correctly; dimmed read rows keep a dimmed badge; the "Not yet scored" separator strip, the status banner, and the Ranking changed hint label look right in every theme.
result: [pending]

### 4. Narrow-width layout
expected: Long titles wrap under themselves without pushing the badge; the banner wraps; the hint label fits at the narrowest list-panel width; matched-topic chips wrap; long breakdown labels wrap without overlapping the points column.
result: [pending]

### 5. Sort-key precision argument (05-01 backstop truth)
expected: Accept or reject: casting the 6-decimal numeric raw score (magnitude well below 10^4, ~10 significant digits) to float8 (15+ digits) for the sort key never merges two distinct raws, so it cannot reorder or duplicate rows.
result: [pending]

### 6. Dev-only double fetch under React StrictMode
expected: On first entry to /priority in the dev server, page 1 may be fetched twice; the list still renders correctly with no duplicates.
result: [pending]

### 7. Decide on code-review warnings WR-01..WR-03
expected: Decide whether to fix now or defer — WR-01 (breakdown read in two statements, rows can disagree with total under a concurrent topic edit), WR-02 (id-only cursor can silently skip rows when the cursor article's score drops mid-walk; recommended before Phase 6), WR-03 (topic save/re-score does not invalidate reading-pane and list article caches). See 05-REVIEW.md.
result: [pending]

## Summary

total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps
