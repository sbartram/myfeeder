---
status: testing
phase: 11-gap-discovery
source: [11-VERIFICATION.md]
started: 2026-10-01T23:14:54Z
updated: 2026-10-01T23:14:54Z
---

## Current Test

number: 1
name: End-to-end create-from-suggestion and dismiss, with reloads
expected: |
  In the running app, engage an article no topic covers, open Interests: it appears under Suggested topics.
  Create topic → prefilled +20 draft, row reads "Draft added"; save → suggestion gone, and still gone after a page reload.
  Dismiss a different suggestion → gone at once, and still gone after a reload.
awaiting: user response

## Tests

### 1. End-to-end create-from-suggestion and dismiss, with reloads
expected: Saved-from-suggestion and dismissed articles disappear and stay gone across reloads (SC-3, SC-4)
result: [pending]

### 2. Visual check across all 6 themes
expected: A normal suggestion row, a "Draft added" row, and the disabled Create topic button at 25 topics read correctly in every theme
result: [pending]

### 3. Decide WR-01 (30-day window semantics)
expected: Either accept "first recording per engagement kind" as the D-06 window and correct CLAUDE.md:197 and engagementReaction.ts:12-15, or schedule a last_engaged_at change for a later phase (never overwrite created_at)
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
