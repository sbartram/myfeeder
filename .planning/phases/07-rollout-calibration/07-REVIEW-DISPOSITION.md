---
phase: 07
review: 07-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Breaker transition log prints \"-1.0%\" failure and slow-call rates for OPEN_TO_HALF_OPEN and HALF_OPEN_TO_CLOSED"
  - id: WR-03
    severity: warning
    disposition: open
    title: "Tier thresholds are not validated anywhere, on the server or in the replay driver"
  - id: WR-04
    severity: warning
    disposition: open
    title: "The badge guard counts copies without checking where they are, and its comment filter only removes whole-line `--` comments, so a drifted badge copy can still pass"
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
  - id: IN-07
    severity: info
    disposition: open
    title: "Blend lines are found by a case-sensitive, column-0 prefix, so an extra blend statement that is indented or lower-cased and drifted is never checked"
  - id: IN-08
    severity: info
    disposition: open
    title: "`replayIsReadOnly` only catches psql meta-commands at column 0 (pre-existing, unchanged by 07-10)"
  - id: IN-09
    severity: info
    disposition: open
    title: "`runDriver` inherits the developer's driver and libpq env, and its 30s timeout cannot fire (pre-existing, unchanged by 07-10)"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Replay drift guard passes when only one of several blend and badge copies matches the app"
open: 12
total: 13
recorded: 2026-09-29T02:29:34.688Z
---

# Phase 07: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-03 | warning | open | - |
| WR-04 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| IN-06 | info | open | - |
| IN-07 | info | open | - |
| IN-08 | info | open | - |
| IN-09 | info | open | - |
| WR-02 | warning | fixed | 07-10 |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
