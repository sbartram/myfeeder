---
status: complete
phase: 03-interest-model-schema-rubric-editor
source: [03-VERIFICATION.md]
started: 2026-09-23T21:25:50Z
updated: 2026-09-23T22:15:30Z
---

## Current Test

[testing complete]

## Tests

### 1. Visual walkthrough of the Interests dialog
expected: ./gradlew bootTestRun + npm run dev, open an article, Settings -> "Edit interests…". Profile saves and persists across reload; topic slider/number sync and 51 rejected; negation warning on "not about crypto"; no-key notice and disabled Preview; all 6 themes correct; below 600px row wraps, no horizontal scroll.
result: pass

### 2. Live topic preview with a real TypeSafe key
expected: With MYFEEDER_TYPESAFE_API_KEY exported, "Preview topic" on a draft and a saved topic sends one POST /api/interest/preview per click and shows "Match N% → counts M% × +W = +P pts" (or the muted "No match" line); weight-only changes recompute without a request. A timeout (5s production limit) shows "Jev is unavailable right now".
result: pass

### 3. Notices wrap at 360px
expected: With not-configured + paused + cold-start notices showing at a 360px viewport, notices stack 8px apart in that order, wrap within the dialog, no horizontal scroll.
result: pass

### 4. 500-character topic description
expected: Pasted into a topic row, it stays on one line inside its input, scrolls within it, and does not widen the row or dialog.
result: pass

### 5. Preview math line in a narrow row
expected: The math line wraps to a second line inside the result strip; no truncation, no horizontal scroll.
result: pass

### 6. Acknowledge the 25-topic cap limitation
expected: Accept that two simultaneous creates at 24 topics are not serialized (single-user limitation, documented in InterestService.createTopic).
result: pass

### 7. Decide on CR-01 (ArticleStateBuilder.truncate)
expected: Either fix now (/gsd-quick fix plus a CJK/URL test) or explicitly carry it into Phase 4. Whitespace-sparse summaries currently truncate to a few characters.
result: skipped
reason: "Deferred follow-up: defer — CR-01 carried into Phase 4 (logged in deferred-items.md)"

## Summary

total: 7
passed: 6
issues: 0
pending: 0
skipped: 1
blocked: 0

## Gaps

## Deferred Follow-Ups

- test: 7
  idea: "defer"
  deferred_at: 2026-09-23
