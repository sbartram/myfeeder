---
phase: 10
review: 10-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "The engagement refetch can overwrite a newer vote in the by-id cache and on the Priority row"
  - id: WR-02
    severity: warning
    disposition: open
    title: "A narrowed 👎 lists unpicked topics with an unexplained drop"
  - id: WR-03
    severity: warning
    disposition: open
    title: "`SIX_DECIMAL_TOLERANCE` cannot absorb the noise it names, and the test pins an impossible input"
  - id: WR-04
    severity: warning
    disposition: open
    title: "The Interests line says \"No learned adjustment yet\" when votes and engagement cancel out"
  - id: IN-01
    severity: info
    disposition: open
    title: "Parts rounded on their own may not add up to the printed weight or learned value"
  - id: IN-02
    severity: info
    disposition: open
    title: "`engagementReplaced` brings back \"+0.0\" rows in the toast"
  - id: IN-03
    severity: info
    disposition: open
    title: "Starring runs the reaction's full refresh set even when the article was already starred, and invalidates `['articles']` twice"
  - id: IN-04
    severity: info
    disposition: open
    title: "The \"never touches priority\" test assertions skip predicate-based invalidations"
open: 8
total: 8
recorded: 2026-09-30T20:59:27.618Z
---

# Phase 10: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| WR-03 | warning | open | - |
| WR-04 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
