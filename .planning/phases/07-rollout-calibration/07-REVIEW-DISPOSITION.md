---
phase: 07
review: 07-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Breaker transition log prints \"-1.0%\" failure and slow-call rates for OPEN_TO_HALF_OPEN and HALF_OPEN_TO_CLOSED"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Replay drift guard passes when only one of several blend and badge copies matches the app"
  - id: WR-03
    severity: warning
    disposition: open
    title: "Tier thresholds are not validated anywhere, on the server or in the replay driver"
  - id: IN-01
    severity: info
    disposition: open
    title: "Client fallback tiers (70/40) disagree with the shipped tiers (70/22)"
  - id: IN-02
    severity: info
    disposition: open
    title: "`window-summary` reuses the `scored_unread` column label for a read-and-unread population"
  - id: IN-03
    severity: info
    disposition: open
    title: "Driver error text and defaults for non-candidate values"
  - id: IN-04
    severity: info
    disposition: open
    title: "Replay sections read different snapshots of a live database"
  - id: IN-05
    severity: info
    disposition: open
    title: "Breaker recovery transitions are logged at WARN, and the logger is a `@Configuration` with no bean methods"
  - id: IN-06
    severity: info
    disposition: open
    title: "CLAUDE.md misattributes the test-yaml tier pin"
open: 8
total: 9
recorded: 2026-09-29T00:50:50.394Z
---

# Phase 07: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | fixed | 07-10 |
| WR-03 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| IN-06 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
