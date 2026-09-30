---
phase: "8"
slug: "engagement-capture"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-30"
---

# Phase 8 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → `PUT /api/articles/{id}/engagement/open`, `DELETE /api/articles/{id}/engagement` | Path id from an untrusted client; no request body | numeric article id |
| browser → existing save routes (`PATCH /api/articles/{id}`, `POST /api/boards/{id}/articles`, `POST /api/articles/{id}/raindrop`) | Save routes that now also record engagement server-side | article/board ids, star flag |
| ArticleEngagementStore → Postgres | Parameterised JdbcClient statements; kind comes from the server-side enum only | article id, engagement kind |
| article.url (feed-supplied, untrusted) → window.open | URL from a third-party feed opened in a new tab | untrusted URL |
| server by-id payload → ScoreRow rendering | `engagement` kinds rendered as text | enum strings |
| RaindropService → Raindrop.io | External call whose success gates the RAINDROP capture | bookmark payload |
| app → logs | WARN on a failed engagement insert | kind, numeric id, exception class name |
| workstation → GitHub, registry, k3s, pg.bartram.org | Release, deploy and read-only prod access | deploy keys (never printed), tags, image |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-08-01 | Tampering | engagement routes (cross-site trigger) | medium | mitigate | PUT/DELETE only (`ArticleController.java:107,114`), no CORS config anywhere in `src/main/java`, no body accepted | closed |
| T-08-02 | Tampering / Repudiation | `recordOpen` (client-chosen kind) | medium | mitigate | `ArticleService.java:63` always writes `EngagementKind.OPEN_ORIGINAL`; V7 `CHECK (kind IN (...))` | closed |
| T-08-03 | Denial of Service | `article_engagement` row flooding | low | mitigate | `ON CONFLICT (article_id, kind) DO NOTHING` (`ArticleEngagementStore.java:28`); `repeatedOpenKeepsOneRowAndItsFirstCreatedAt` | closed |
| T-08-04 | Information Disclosure / Tampering | `recordQuietly` WARN | low | mitigate | Logs kind, id and `getSimpleName()` only (`ArticleEngagementStore.java:42`); `recordQuietlySwallowsAFailureAndLogsTheClassNameOnly` | closed |
| T-08-05 | Denial of Service (self) | V7 FKs blocking feed/article delete | medium | mitigate | `REFERENCES article(id) ON DELETE CASCADE` on both V7 tables; `V7EngagementMigrationTest` | closed |
| T-08-06 | Tampering | path id validation | low | mitigate | `@PathVariable Long id`; `openWithANonNumericIdIs400`; unknown id → 404 before any write | closed |
| T-08-07 | Tampering / Elevation | `useOpenOriginal` reverse tabnabbing | medium | mitigate | `window.open(article.url, '_blank', 'noopener')` (`useEngagement.ts:23`); `opensFirstThenSendsABodylessPut` | closed |
| T-08-08 | Denial of Service (UX) | open-report failures as toasts/unhandled rejections | low | mitigate | `Promise.resolve()…catch(() => {})` (`useEngagement.ts:25-28`), no `useMutation` | closed |
| T-08-09 | Information Disclosure | over-reporting browsing | low | mitigate | `inBodyLinkOpensButIsNotReported`, `copyLinkIsNotReported` (`ReadingPane.test.tsx`) | closed |
| T-08-10 | Tampering | capture sharing the user's transaction (D-07) | medium | mitigate | No `@Transactional` in ArticleService, BoardService or RaindropService; capture via `recordQuietly` | closed |
| T-08-11 | Repudiation / Integrity | RAINDROP recorded for a failed/blocked save | medium | mitigate | Capture after `createBookmark` returns; `aFailedOrBlockedBookmarkRecordsNothing`, `aRaindropNotConfiguredFailureRecordsNothing` (`RaindropServiceTest`) | closed |
| T-08-12 | Denial of Service (external duplicate) | Raindrop save erroring after the bookmark exists (D-08) | medium | mitigate | `recordQuietly` catches `DataAccessException` (`ArticleEngagementStore.java:41`); save still returns 200. Code review WR-01 notes non-`DataAccessException` failures are not swallowed — tracked in 08-REVIEW.md | closed |
| T-08-13 | Tampering | BOARD row left by a failed board add | low | mitigate | Record after save-or-exists branch; `aFailedSaveRecordsNothing` (`BoardServiceTest`) | closed |
| T-08-14 | Denial of Service (self) | feed delete blocked by engagement FKs | medium | mitigate | `ON DELETE CASCADE`; `deletingTheFeedRemovesEngagementAndSucceeds`, `deletingTheFeedCascadesEngagement` | closed |
| T-08-15 | Tampering (XSS) | ScoreRow engagement label | low | mitigate | Fixed `ENGAGEMENT_LABEL` map rendered as React text (`ScoreRow.tsx:8,69`); no `dangerouslySetInnerHTML` | closed |
| T-08-16 | Tampering | Forget triggered unintentionally or cross-site | low | accept | See Accepted Risks Log | closed |
| T-08-17 | Denial of Service (UX) | save-path refresh reshuffling Priority | low | mitigate | Only `['article', id]` invalidated; `noSaveInvalidatesPriority`, `forgetDeletesAndRefreshesOnlyThisArticle` | closed |
| T-08-18 | Information Disclosure | deploy keys in files, logs or echo | high | mitigate | Keys checked with `test -n` only; 08-RELEASE.md grep-verified key-free; user confirmed no secret printed (08-UAT test 3) | closed |
| T-08-19 | Tampering | release tags/history | high | mitigate | v0.2.1 SHA `5461d0a` unchanged (08-RELEASE.md); no force-push/`--no-verify`/`disableChecks` (08-UAT test 3) | closed |
| T-08-20 | Denial of Service | bad release takes prod down | high | mitigate | Preflight green, rollback revision 19 recorded, pg_dump before deploy, rollout complete (08-RELEASE.md) | closed |
| T-08-21 | Tampering (integrity) | V7 applied to prod irreversibly (D-13) | medium | mitigate | Blocking-human checkpoint approved before publish (08-RELEASE.md); V7 additive only (`v7OnlyCreatesTables`) | closed |
| T-08-22 | Tampering | prod smoke deleting real engagement | low | mitigate | Smoke used article 26221 with `[]` engagement, API only, no SQL writes (08-RELEASE.md) | closed |
| T-08-SC | Tampering | npm/pip/cargo installs | low | accept | See Accepted Risks Log | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-08-01 | T-08-16 | Forget is a same-origin click; DELETE is never a CORS simple request (T-08-01); a wrong Forget only loses engagement, which the next open or save re-records. D-03 deliberately has no confirm | plan 08-04 (user-approved design D-03) | 2026-09-29 |
| AR-08-02 | T-08-SC | No package installed in any 08 plan; build.gradle.kts and package.json unchanged | plans 08-01…08-05 | 2026-09-29 |
| AR-08-03 | T-08-12 (D-08 residual) | A process stop between `createBookmark` returning and the RAINDROP row write loses that one row, with no retry | user (08-UAT test 4) | 2026-09-30 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 23 | 23 | 0 | /gsd-secure-phase 08 (orchestrator, ASVS L1 grep-depth; register authored at plan time) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-30
