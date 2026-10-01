---
phase: 09
review: 09-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "ENGAGEMENT_CAP outranks, and hides, the limit that actually holds a vote back"
  - id: WR-02
    severity: warning
    disposition: open
    title: "The replay driver accepts engagement constants the app refuses to start with"
  - id: WR-03
    severity: warning
    disposition: fixed
    title: "A vote that replaces engagement gets a smaller effect but `limit` stays `NONE`"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Engagement is shown as \"Learned from votes\" on main until Phase 10"
  - id: IN-02
    severity: info
    disposition: open
    title: "`engaged` treats every kind that is not an open as a save"
  - id: IN-03
    severity: info
    disposition: open
    title: "The replay's `learned` column means something different from the API's `learned`"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "Engagement makes Priority scores change on every open or star, so rows can be skipped between pages"
open: 3
total: 7
recorded: 2026-09-30T16:58:00.000Z
---

# Phase 09: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | Phase 10 plan 10-02: LearnedLimit.of precedence LEARNED_CAP → SIGN_CLAMP → WEIGHT_RANGE → ENGAGEMENT_CAP (D-10) |
| WR-02 | warning | open | - |
| WR-03 | warning | fixed | Phase 10 plan 10-02: TopicEffect.engagementReplaced marks a replaced or restored engagement share (D-11) |
| IN-01 | info | fixed | Phase 10 plan 10-03: Interests line reads Learned … (votes …, engaged …) (D-14, D-15) |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | fixed | Phase 10 plan 10-04: priorityPageAfter Javadoc states the skip limit; hint covers it (D-09) |

Dispositions default to `open`. Set `fixed`, `skipped` or `deferred` by hand, with the reason in the Source column.
