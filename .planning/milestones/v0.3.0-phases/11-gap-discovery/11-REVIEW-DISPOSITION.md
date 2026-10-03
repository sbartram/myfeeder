---
phase: 11
review: 11-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Engaging again does not move an article back into the 30-day window, but the docs and comments say it does"
  - id: WR-02
    severity: warning
    disposition: open
    title: "The dismiss cache update and pending state are not tied to the row that was clicked"
  - id: IN-01
    severity: info
    disposition: open
    title: "The heading count uses the unfiltered item count"
  - id: IN-02
    severity: info
    disposition: open
    title: "The draft builder is duplicated across the two \"one draft path\" entry points"
  - id: IN-03
    severity: info
    disposition: open
    title: "`addDraft`'s parameter shadows the `draft` prop"
  - id: IN-04
    severity: info
    disposition: open
    title: "Two \"section hidden\" tests can pass before the response lands"
  - id: IN-05
    severity: info
    disposition: open
    title: "A failed suggestions load is silent"
open: 6
total: 7
recorded: 2026-10-01T23:10:04.648Z
---

# Phase 11: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | UAT test 3: accepted first-recording-per-kind window; CLAUDE.md and engagementReaction.ts corrected |
| WR-02 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
