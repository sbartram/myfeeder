---
phase: "6"
slug: "thumbs-feedback"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-27"
---

# Phase 6 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser -> `PUT/DELETE /api/articles/{id}/feedback` | Untrusted path id, vote and topicIds cross into the service and SQL | vote (±1), topic id list — low sensitivity |
| browser -> `GET /api/interest/topics/learned` | Read-only route, no request parameters | topic weights — low sensitivity |
| service -> Postgres | Named-parameter SQL built from compile-time fragments | article/topic ids, vote |
| server response -> React UI | Topic names, article titles and numbers rendered in toast, picker, notice, topic row and Why panel | user-authored topic names, feed-supplied titles (untrusted text) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-06-01 | Tampering | `ArticleFeedbackStore`, `InterestScoreQueries` | high | mitigate | Named params only (6 in store, 11 in queries); the only concatenated `scope` values are class constants `UNREAD_SCOPE`/`IDS_SCOPE`/`ARTICLE_SCOPE` (InterestScoreQueries.java:138-212) | closed |
| T-06-02 | Tampering (CSRF) | `PUT /api/articles/{id}/feedback` | medium | mitigate | `consumes = MediaType.APPLICATION_JSON_VALUE` (ArticleController.java:92); `putFeedbackRequiresJson` (ArticleControllerTest.java:393) | closed |
| T-06-03 | Denial of service | `topicIds` list | low | mitigate | `topicIds.size() > InterestService.MAX_TOPICS` rejected before SQL (ArticleFeedbackService.java:51); `rejectsMoreTopicIdsThanTopicsCanExist` | closed |
| T-06-04 | Information disclosure | 400 messages | low | mitigate | Four fixed-text `IllegalArgumentException` messages, none echo input (ArticleFeedbackService.java:46-81) | closed |
| T-06-05 | Tampering | vote value | low | mitigate | `vote != 1 && vote != -1` rejected (ArticleFeedbackService.java:45); V6 `CHECK (vote IN (-1, 1))` backstop | closed |
| T-06-06 | Tampering | user base weights | medium | mitigate | No `UPDATE`/`INSERT` on `interest_topic` in store or service (only a read JOIN); `learnedEndpointMatchesTheVoteEffects` asserts base unchanged | closed |
| T-06-07 | Spoofing / Repudiation | vote endpoints | low | accept | Single-user app, no auth layer — see Accepted Risks | closed |
| T-06-08 | Tampering | `ArticleFeedbackStore.find`, breakdown SQL | high | mitigate | `:articleId` named param; `ARTICLE_SCOPE` constant | closed |
| T-06-09 | Information disclosure | `feedback` on list responses | low | mitigate | `listItemsOmitFeedback` (FeedbackApiIntegrationTest.java:314) | closed |
| T-06-10 | Tampering | learned read path | medium | mitigate | `learnedTopics()` has no `@Transactional` and only calls `queries.allTopicWeights()` (ArticleFeedbackService.java:70-75) | closed |
| T-06-11 | Spoofing | `GET /topics/learned` | low | accept | Single-user app, no auth layer — see Accepted Risks | closed |
| T-06-12 | Tampering (XSS) | toast text, FeedbackBar | medium | mitigate | No `dangerouslySetInnerHTML` in FeedbackBar.tsx, Toast.tsx, utils/feedback.ts | closed |
| T-06-13 | Denial of service | rapid presses | low | mitigate | Single mutation scope `FEEDBACK_SCOPE = 'article-feedback'` (useFeedback.ts:13) | closed |
| T-06-14 | Information disclosure | error toasts | low | mitigate | `meta: { inlineError: true }` (useFeedback.ts:49); fixed copy by status | closed |
| T-06-15 | Tampering | ranking integrity | low | mitigate | Priority row patched only with server `interestScore` (useFeedback.ts:67) | closed |
| T-06-16 | Tampering (XSS) | NarrowPicker, narrow label | medium | mitigate | No `dangerouslySetInnerHTML` in NarrowPicker.tsx or FeedbackBar.tsx | closed |
| T-06-17 | Tampering | browser-shortcut votes | low | mitigate | Meta/Ctrl/Alt guard (useKeyboardShortcuts.ts:163,168); `modifiedVotingKeysAreIgnored` | closed |
| T-06-18 | Tampering | narrowing to arbitrary topic ids | low | mitigate | Server rejects unmatched ids: "topicIds must be topics this article matched" (ArticleFeedbackService.java:81) | closed |
| T-06-19 | Tampering (XSS) | draft description, learned line, Why label | medium | mitigate | No `dangerouslySetInnerHTML` in FeedbackNotice.tsx, TopicRow.tsx, WhyBreakdown.tsx | closed |
| T-06-20 | Tampering | oversized draft description | low | mitigate | `article.title.trim().slice(0, 500)` (FeedbackNotice.tsx:23); server validates on save | closed |
| T-06-21 | Elevation / cost | unintended billed Jev calls | low | mitigate | FeedbackNotice has no Preview/save call; draft only seeds the dialog | closed |
| T-06-22 | Tampering | `allTopicWeights` SQL | high | mitigate | Only `:learnRate`/`:learnedCap` bound; fragments are class constants | closed |
| T-06-23 | Tampering (XSS) | `formatVoteToast`, `Toast` | medium | mitigate | Plain string rendered as React text child; no `dangerouslySetInnerHTML` | closed |
| T-06-24 | Tampering | displayed effect numbers | low | mitigate | Server before/after/learned only; `upVoteListsTopThreeThenMore`, `cappedTopicShowsZeroWithItsNote` | closed |
| T-06-SC | Tampering | npm/Gradle installs | low | accept | No dependency changes in `package.json` or `build.gradle.kts` across the phase — see Accepted Risks | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-06-01 | T-06-07 | Single-user app with no auth layer (unchanged from prior phases); votes are not audited by design | plan 06-01 threat model | 2026-09-26 |
| AR-06-02 | T-06-11 | Same no-auth posture for the new read-only learned route | plan 06-06 threat model | 2026-09-26 |
| AR-06-03 | T-06-SC | No package added in any phase 06 plan (06-RESEARCH Package Legitimacy Audit: none) | plans 06-01, 03, 04, 05, 07 threat models | 2026-09-26 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-27 | 25 | 25 | 0 | gsd-secure-phase (orchestrator, ASVS L1 grep-depth; plan-time register, short-circuit) |

## Security Audit 2026-09-27
| Metric | Count |
|--------|-------|
| Threats found | 25 |
| Closed | 25 |
| Open | 0 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-27
