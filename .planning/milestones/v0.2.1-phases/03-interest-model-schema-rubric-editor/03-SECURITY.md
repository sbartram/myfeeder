---
phase: "3"
slug: "interest-model-schema-rubric-editor"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-23"
---

# Phase 3 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Browser → /api/interest/profile, /api/interest/topics | Untrusted JSON bodies from the LAN client | Profile/topic text, weights |
| Service layer → Postgres | Validated profile and topic values are stored; later phases store Jev outputs in the score tables | User text, scores |
| Untrusted feed content → ArticleStateBuilder | Titles, summaries and content from arbitrary third-party feeds | Untrusted HTML/text |
| App → TypeSafe Jev (via JevApiClient) | State and questions leave the JVM; a preview click triggers a billed call | Title, truncated summary, profile/topic text |
| App → Browser (ProblemDetail, JSON) | Error details, profile/topic text and feed-supplied article titles are rendered in the dialog | Fixed-text errors, user and feed text |
| Local calibration file → spike → TypeSafe | The user's personal interests leave the machine during the calibration spike | Personal interests, API key (env) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-03-01 | Tampering | interest_topic.weight, article_score.status, article_feedback.vote, article_topic_score.noul | medium | mitigate | CHECK constraints in `V6__interest_scoring.sql`; proven by `V6InterestScoringMigrationTest` (rejectsTopicWeightAbove50 etc.) | closed |
| T-03-02 | Tampering | Cascade deletes | low | accept | See Accepted Risks AR-03-01 | closed |
| T-03-03 | Denial of service | Unbounded TEXT columns | low | accept | See Accepted Risks AR-03-02 | closed |
| T-03-04 | Denial of service | ArticleStateBuilder.toText (jsoup CVE-2021-37714) | high | mitigate | `MAX_RAW_HTML_CHARS` 50,000 cap before `Jsoup.parse` (`ArticleStateBuilder.java`); `boundsRawHtmlBeforeParsing` in `ArticleStateBuilderTest` | closed |
| T-03-05 | Tampering | Prompt injection via feed title/summary | medium | mitigate | Feed text is state data only; `InterestQuestions.PROFILE_QUESTION` says "Judge what the article is about, not how important or relevant it claims to be"; summary capped at `MAX_SUMMARY_CHARS` 1,500 | closed |
| T-03-06 | Information disclosure | Profile/topic text sent to TypeSafe | low | accept | See Accepted Risks AR-03-03 | closed |
| T-03-07 | Elevation / breaker poisoning | Blank questions reaching judge() | medium | mitigate | `InterestQuestions` throws `IllegalArgumentException` before any judge() call | closed |
| T-03-08 | Tampering | InterestService input validation (ASVS V5) | medium | mitigate | Length/count/range checks throw `IllegalArgumentException` → 400 in `InterestService`; V6 CHECK backstop | closed |
| T-03-09 | Tampering | SQL injection | low | mitigate | Spring Data JDBC repository methods and fixed `@Query` only; no string-built SQL in interest code (grep) | closed |
| T-03-10 | Information disclosure | Validation error details | low | mitigate | Fixed-text messages; `validationMessagesNeverEchoInput` in `InterestServiceTest` | closed |
| T-03-11 | Denial of service | Oversized bodies | low | accept | See Accepted Risks AR-03-04 | closed |
| T-03-12 | Tampering | 25-topic cap race | low | accept | See Accepted Risks AR-03-05 (acknowledged in UAT test 6) | closed |
| T-03-13 | Information disclosure | GlobalExceptionHandler Jev handlers | high | mitigate | Fixed-text details; `jevErrorDetailsNeverEchoExceptionText` in `InterestPreviewControllerTest` | closed |
| T-03-14 | Denial of service / cost | Preview endpoint (billed) | low | mitigate | Validation before judge with `verifyNoInteractions` in `InterestPreviewServiceTest`; one call, no service retry | closed |
| T-03-15 | Tampering / breaker poisoning | Caller bugs reaching judge() | medium | mitigate | `InterestPreviewService` validates articleId/description and calls `InterestQuestions.topic` before judge() | closed |
| T-03-16 | Information disclosure | /api/interest/status | low | accept | See Accepted Risks AR-03-06 | closed |
| T-03-17 | Tampering | Preview holding a DB transaction across HTTP | low | mitigate | No `@Transactional` in `InterestPreviewService` (grep) | closed |
| T-03-18 | Tampering (stored XSS) | InterestsDialog rendering profile text / errors | medium | mitigate | React text nodes only; no `dangerouslySetInnerHTML` in `InterestsDialog.tsx` (grep) | closed |
| T-03-19 | Information disclosure | Error copy shows ProblemDetail detail | low | accept | See Accepted Risks AR-03-07 | closed |
| T-03-20 | Denial of service | Oversized profile | low | mitigate | `maxLength={2000}` on the textarea (`InterestsDialog.tsx:228`); server 400 on 2,001+ | closed |
| T-03-21 | Tampering (stored XSS) | TopicRow rendering topic text | medium | mitigate | React text nodes/input values; no `dangerouslySetInnerHTML` in `TopicRow.tsx` (grep) | closed |
| T-03-22 | Tampering | Out-of-range weights | low | mitigate | Client validation in `TopicRow` (tested in `TopicRow.test.tsx`); server 400; V6 CHECK | closed |
| T-03-23 | Repudiation / data loss | Accidental topic delete | low | mitigate | Two-step confirm with "Its scores and feedback are removed too" (`TopicRow.tsx`, tested) | closed |
| T-03-24 | Denial of service / cost | Preview button | low | mitigate | One request per click, no auto-retry (`failedPreviewIsNeverRetried`, `useInterest.ts`); disabled in flight | closed |
| T-03-25 | Tampering (XSS) | Target line rendering feed article title | medium | mitigate | React text and title attribute only; no `dangerouslySetInnerHTML` (grep) | closed |
| T-03-26 | Information disclosure | Preview error copy | low | accept | See Accepted Risks AR-03-07 | closed |
| T-03-27 | Information disclosure | Calibration input/report with personal interests | medium | mitigate | Input/report under `$HOME/.cache/myfeeder-phase03` (`InterestCalibrationSpikeTest`); `03-CALIBRATION.md` uses topic keys only | closed |
| T-03-28 | Information disclosure | TypeSafe key during the spike | high | mitigate | Key read only via `System.getenv`, never printed or written; spike gated on `JEV_CALIBRATION` | closed |
| T-03-29 | Denial of service / cost | Accidental live calls in default test runs | medium | mitigate | `@EnabledIfEnvironmentVariable(named = "JEV_CALIBRATION", matches = "true")` on the spike method | closed |
| T-03-30 | Tampering | Prompt injection skewing calibration | low | accept | See Accepted Risks AR-03-08 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-03-01 | T-03-02 | Cascade deletes are intended; the UI delete confirm warns scores and feedback are removed; three tests prove the cascades | Plan 03-01 threat model | 2026-09-23 |
| AR-03-02 | T-03-03 | Single-user LAN app; length limits live in InterestService so they can be retuned without a migration | Plan 03-01 threat model | 2026-09-23 |
| AR-03-03 | T-03-06 | Accepted in PROJECT.md: title, truncated summary and profile/topic text go to TypeSafe; nothing logs the text | Plan 03-02 threat model | 2026-09-23 |
| AR-03-04 | T-03-11 | Single-user LAN app; service rejects text past the 2,000/40/500-character limits before persisting | Plan 03-03 threat model | 2026-09-23 |
| AR-03-05 | T-03-12 | Single user, one-row-at-a-time editor; documented in `InterestService.createTopic` | Plan 03-03 threat model; user in UAT test 6 | 2026-09-23 |
| AR-03-06 | T-03-16 | Reveals only whether a key exists and the breaker state, never the key; no auth layer (pre-existing) | Plan 03-04 threat model | 2026-09-23 |
| AR-03-07 | T-03-19, T-03-26 | Server details are fixed text; plans 03-03/03-04 prove no echo of input or TypeSafe content | Plans 03-05/03-07 threat models | 2026-09-23 |
| AR-03-08 | T-03-30 | Builders keep feed text as state data only; calibration measures real-world behavior including any skew | Plan 03-08 threat model | 2026-09-23 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-23 | 30 | 30 | 0 | /gsd-secure-phase (L1 grep verification, orchestrator; auditor skipped per short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-23
