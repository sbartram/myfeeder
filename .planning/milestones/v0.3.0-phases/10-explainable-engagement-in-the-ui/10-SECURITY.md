---
phase: "10"
slug: "explainable-engagement-in-the-ui"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-30"
---

# Phase 10 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → GET /api/articles/{id} | One extra same-origin read after each successful engagement action | Article JSON (user's own data) |
| client cache → Priority list being triaged | Engagement reactions may change what the user sees while triaging | Cached scores |
| browser → PUT/DELETE /api/articles/{id}/feedback | Existing vote routes; responses gain one boolean per effect | Vote, topic ids; derived weights |
| API → browser | Limits and flags printed by the toast and the Interests editor | Enum limits, booleans, numbers |
| server JSON → rendered text | User-authored topic names and server numbers in labels, titles and toasts | Topic names (user-authored text) |
| browser → PATCH /api/articles/{id}, DELETE /api/articles/{id}/engagement | Existing same-origin routes, exercised by the new SC-3 test only | Star state, engagement rows |
| Priority cursor → next page | Documented skip limitation (no behavior change) | Cursor article id |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-10-01 | Denial of Service (self) | afterEngagement by-id refetch | low | mitigate | `hooks/engagementReaction.ts:58` single `qc.query` per action (dedupes in-flight); no polling added | closed |
| T-10-02 | Tampering (UI integrity) | Priority list re-sorted mid-triage | medium | mitigate | `['priority']` only via `patchPriorityArticle`; `engagementReaction.test.ts` `neverInvalidatesResetsOrRefetchesPriority`, `engagementRefresh.test.ts` `noSaveTouchesPriority` | closed |
| T-10-03 | Information Disclosure / noise | reaction errors surfacing as toasts | low | mitigate | `engagementReaction.ts:77` catch-all, never rejects; `aFailedRefetchIsSilentAndNeverRejects` | closed |
| T-10-04 | Tampering (data freshness) | stale by-id response overwriting a newer vote patch | low | mitigate | `engagementReaction.ts:66` `qc.isMutating({ mutationKey: ['feedback'] })` skip; `aPendingVoteOnTheSameArticleSkipsThePatch` | closed |
| T-10-05 | Repudiation (explanation integrity) | vote effect understated when it replaced engagement (WR-03) | medium | mitigate | `engagementReplaced` in `ArticleFeedbackService`/`FeedbackResult`; asserted in `ArticleFeedbackServiceTest`, `FeedbackApiIntegrationTest`, `ArticleControllerTest` | closed |
| T-10-06 | Repudiation (explanation integrity) | LearnedLimit naming the engagement cap while a clamp/range binds (WR-01) | low | mitigate | D-10 order; `ArticleFeedbackServiceTest.clampAndRangeOutrankEngagementCap`, `FeedbackApiIntegrationTest.bindingRangeOutranksTheEngagementCap` | closed |
| T-10-07 | Information Disclosure | new JSON booleans on existing routes | low | accept | See Accepted Risks Log AR-10-01 | closed |
| T-10-08 | Tampering (XSS) | topic names in new label, title and toast strings | low | mitigate | React text children / `title` attributes only; no new `dangerouslySetInnerHTML` (the sole use is the pre-existing DOMPurify-sanitized body in `ReadingPane.tsx`) | closed |
| T-10-09 | Repudiation (explanation integrity) | engagement shown as votes, or a client-computed part | low | mitigate | Labels print server split fields only (`WhyBreakdown`, `TopicRow`); exact strings pinned in tests; UAT test 2 confirmed by user | closed |
| T-10-10 | Repudiation (documentation integrity) | CLAUDE.md and Javadoc drifting from code | low | mitigate | CLAUDE.md Phase 10 sections; `InterestScoreQueries.java:185-188` IN-04 Javadoc | closed |
| T-10-11 | Denial of Service (UX) | unloaded Priority rows skipped between pages after engagement (IN-04) | low | accept | See Accepted Risks Log AR-10-02 | closed |
| T-10-SC | Tampering | npm/pip/cargo installs | low | accept | See Accepted Risks Log AR-10-03 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-10-01 | T-10-07 | Booleans derive from the user's own weights on a single-user app; no new route, parameter or input; existing validation and fixed-text 400s unchanged | plan 10-02 threat model | 2026-09-30 |
| AR-10-02 | T-10-11 | D-09: the "Ranking changed" hint prompts the refresh that restores every row; a forced reset would re-sort mid-triage | plan 10-04 threat model (D-09) | 2026-09-30 |
| AR-10-03 | T-10-SC | No package installed in any Phase 10 plan; package.json, package-lock.json and build.gradle.kts unchanged in the phase | plans 10-01..10-04 threat models | 2026-09-30 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 12 | 12 | 0 | /gsd-secure-phase (L1 grep, auditor short-circuited: plan-time register, ASVS 1) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-30
