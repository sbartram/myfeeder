---
phase: "9"
slug: "engagement-learning-model"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-30"
---

# Phase 9 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| operator shell → replay driver (`psql -v`) | `ENGAGEMENT_*` env values become SQL text through psql interpolation | numeric constants (validated) |
| committed yaml → app startup | Engagement constants that shape every ranking read | open/save weights, cap |
| browser → existing save and vote routes | Open/star/board/Raindrop and votes now change the ranking through the learned CTE | article/board ids, vote, topic picks |
| stored engagement and votes → ranking | User actions that shape every Priority read through the derived model | engagement kinds, votes |
| API → browser | Weights, limits and the thumbs/engagement split rendered as text | server-computed numbers, fixed labels |
| every ranking read → Postgres | The learned CTE runs an extra aggregate on each Priority page, badge and breakdown | none new |
| feature branch → main / prod | Release and deploy path that D-14 closes for this phase | none (no release) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-09-01 | Tampering | `interest-calibration-replay.sh` psql `-v` interpolation of `ENGAGEMENT_*` | high | mitigate | Decimal regex `^[0-9]+(\.[0-9]+)?$` on every value before any connection (`scripts/interest-calibration-replay.sh:36-37`); `PGOPTIONS default_transaction_read_only=on` (`:54`); `InterestCalibrationReplaySqlTest.driverRejectsANonNumericEngagementValue` | closed |
| T-09-02 | Tampering (ranking integrity) | engagement constants (cap ≥ thumbs cap, save ≥ 1, negative weights) | medium | mitigate | `MyfeederProperties implements Validator` rejects with `ENGAGEMENT_INVALID`; zero floor in SQL; helm/ and deploy.sh have no engagement keys and are unchanged against main | closed |
| T-09-03 | Tampering | replay SQL gaining a write statement | medium | mitigate | `InterestCalibrationReplaySqlTest.replayIsReadOnly` plus the read-only session | closed |
| T-09-04 | Information Disclosure | validation failure message | low | mitigate | Fixed text `ENGAGEMENT_INVALID`; `MyfeederPropertiesValidationTest.refusalTextIsFixed` | closed |
| T-09-05 | Elevation of Privilege (billed cost) | engagement triggering Jev calls | medium | mitigate | Derived in SQL at query time only; `EngagementApiIntegrationTest.neverCallsJev` and `engagementRaisesTheRankingWithoutWritingWeightsOrCallingJev` verify `judge` is never called | closed |
| T-09-06 | Repudiation (misleading explanation) | `effectNote` / `LearnedLine` fall-through for ENGAGEMENT_CAP; "Learned from votes" label | low | accept | See Accepted Risks Log AR-09-01; pinned by D-11 cases in `feedback.test.ts` and `TopicRow.test.tsx` | closed |
| T-09-07 | Repudiation (explanation integrity) | split columns whose parts do not sum to the effective weight | medium | mitigate | 6-decimal rounded SQL subtraction; `InterestLearnedGridTest.everyCellMatchesTheExactOracle` (1,456 cells) and `breakdownSplitEqualsTopicWeightSplit` | closed |
| T-09-08 | Tampering (ranking integrity) | engagement lifting a negative-base topic or lowering any weight | medium | mitigate | `CASE WHEN t.weight < 0 THEN 0` and `GREATEST(0, LEAST(:engagementCap, …))` in `InterestScoreQueries` LEARNED_CTE; grid asserts eng ≥ 0 and no movement on negative bases | closed |
| T-09-09 | Tampering (user intent) | engagement surviving or outweighing an explicit vote | medium | mitigate | `NOT EXISTS (… article_feedback …)` in the `engaged` CTE; `InterestScoreQueriesEngagementTest.anyVoteReplacesTheArticlesEngagementOnEveryTopic` / `removingTheVoteRestoresTheEngagementExactly` | closed |
| T-09-10 | Tampering (ranking integrity) | cap 0 failing to disable engagement, or negative weights leaking | medium | mitigate | `InterestScoreQueriesZeroEngagementTest.capZeroWithEngagementRowsEqualsV021` / `capZeroWithNegativeWeightsEqualsV021` against the frozen v0.2.1 SQL | closed |
| T-09-11 | Repudiation (explanation integrity) | vote effect before/after hiding the engagement the vote replaced | medium | mitigate | before/after both from `topicWeights` in the write transaction (D-12); `FeedbackApiIntegrationTest.voteEffectBeforeIncludesTheEngagementItReplaces` | closed |
| T-09-12 | Repudiation | `LearnedLimit` mislabelling a clamp as the engagement cap or missing it | low | mitigate | Six precedence tests in `ArticleFeedbackServiceTest`; `FeedbackApiIntegrationTest.learnedEndpointReportsTheEngagementCap` | closed |
| T-09-13 | Denial of Service | extended learned CTE slowing every ranking read | medium | mitigate | `InterestScoreQueriesLatencyTest` guards each query at 10 × baseline + 250 ms with ~20k engagement rows; EXPLAIN recorded in VERIFICATION | closed |
| T-09-14 | Tampering (release integrity) | uncalibrated engagement weights reaching prod | medium | mitigate | D-14: no tag contains HEAD, no image built or deployed, helm/ and deploy.sh unchanged against main; release waits for Phase 12 | closed |
| T-09-15 | Elevation of Privilege (billed cost) | engaged-but-unscored articles becoming eligible for Jev scoring | medium | mitigate | `ArticleScoreStore.java` unchanged against main; CLAUDE.md records the rule | closed |
| T-09-SC | Tampering | npm/pip/cargo installs | low | accept | See Accepted Risks Log AR-09-02; `build.gradle.kts` and `package.json` unchanged against main | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-09-01 | T-09-06 | D-11 allows the ENGAGEMENT_CAP fall-through and the "votes" label in Phase 9; D-14 ships no release; Phase 10 rewords it deliberately (tests pin the current behavior) | plan-time (09-02-PLAN) | 2026-09-30 |
| AR-09-02 | T-09-SC | No plan installed a package; build and package manifests are unchanged | plan-time (all plans) | 2026-09-30 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 16 | 16 | 0 | secure-phase orchestrator (ASVS L1 grep-depth short-circuit; register authored at plan time) |

## Security Audit 2026-09-30

| Metric | Count |
|--------|-------|
| Threats found | 16 |
| Closed | 16 |
| Open | 0 |

Evidence: every cited test passed in the 2026-09-30 validate-phase run (169 backend tests across 12 classes, 40 frontend tests).

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-30
