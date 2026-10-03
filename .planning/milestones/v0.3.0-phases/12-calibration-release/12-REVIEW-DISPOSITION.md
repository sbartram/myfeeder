---
phase: 12
review: 12-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "The SqlTest driver tests inherit the operator's replay env vars, so the boundary tests become env-dependent"
  - id: WR-02
    severity: warning
    disposition: open
    title: "Tier fields are not range- or order-checked, so a swapped candidate silently produces inconsistent histograms"
  - id: WR-03
    severity: warning
    disposition: open
    title: "The \"a failed run leaves no file\" guarantee does not cover interruption, and a partial .tmp holds prod article titles"
  - id: IN-01
    severity: info
    disposition: open
    title: "`eng_at_cap` / `at_cap` compare unrounded float8, but the app judges on 6-decimal rounded values"
  - id: IN-02
    severity: info
    disposition: open
    title: "The \"reached psql\" assertions also pass when psql is not installed"
  - id: IN-03
    severity: info
    disposition: open
    title: "The process timeouts are ineffective because stdout/stderr are drained before `waitFor`"
  - id: IN-04
    severity: info
    disposition: open
    title: "The D-13 end-to-end proof is silently skipped on hosts without psql"
  - id: IN-05
    severity: info
    disposition: open
    title: "A tautological assertion in the run test"
  - id: IN-06
    severity: info
    disposition: open
    title: "A test yaml comment claims the dev overlay carries the calibrated engagement values; it carries none"
  - id: IN-07
    severity: info
    disposition: open
    title: "Output filenames come from the raw candidate text, so equal values can produce different files and duplicates overwrite silently"
  - id: IN-08
    severity: info
    disposition: open
    title: "The MetalLB plan's backlog step uses `commit -am` in another repo, which sweeps up any unrelated tracked edits"
open: 11
total: 11
recorded: 2026-10-02T23:13:16.574Z
---

# Phase 12: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| WR-03 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| IN-06 | info | open | - |
| IN-07 | info | open | - |
| IN-08 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
