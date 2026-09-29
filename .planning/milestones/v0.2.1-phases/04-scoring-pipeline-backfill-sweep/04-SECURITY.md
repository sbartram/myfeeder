---
phase: "4"
slug: "scoring-pipeline-backfill-sweep"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-24"
---

# Phase 4 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| App → TypeSafe Jev | Billed outbound calls whose failures drive the breaker that pauses all scoring | Feed title/summary state, rubric questions, API key |
| Caller code → proxied judge() | Programming errors inside the proxy can masquerade as Jev faults | Exceptions |
| Feed content → Jev state | Untrusted feed title/summary travels to an LLM judge | Untrusted text |
| TypeSafe Jev → app | Exception messages and bodies from an external service return into the app | Error text, request ids |
| App / scorer → PostgreSQL | New SQL with parameters derived from app state and Jev outputs | Ids, cutoffs, scores |
| Polling/scheduler thread → scoring subsystem | The single scheduler thread that polls feeds also runs the sweep and hands work to background scoring | Article ids |
| App → Browser (/status, /rescore) | Counts and fixed-text error details leave the server | Counts, fixed-text errors |
| Browser → POST /api/interest/rescore | An unauthenticated LAN request triggers a bulk delete of score rows | JSON `{confirm: true}` |
| Developer shell → Gradle test JVM | `.envrc` exports a real, billed key into every `./gradlew test` and `bootTestRun` | API key |
| Working tree → git history | Uncommitted `.envrc` (real key) and user CLAUDE.md edits sit next to committed files | Secrets, user edits |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-04-01 | Denial of service | jev breaker counting retry attempts | medium | mitigate | Aspect orders 1/2 in both YAMLs; `JevResilienceTest.breakerOpensAtMinimumCallsAndShortCircuits`, `retriedCallThatSucceedsRecordsOneSuccess` | closed |
| T-04-02 | Denial of service | Breaker poisoning by caller bugs | medium | mitigate | `IllegalArgumentException` in jev ignore-exceptions (both YAMLs); `callerInputErrorsAreNeitherSentNorRecorded` | closed |
| T-04-03 | Denial of service | Scoring paused while breaker stays OPEN | medium | mitigate | `automatic-transition-from-open-to-half-open-enabled: true` (both YAMLs); `openBreakerMovesToHalfOpenWithoutACall` | closed |
| T-04-04 | Denial of service / cost | ~93s worst-case logical call | low | accept | See Accepted Risks | closed |
| T-04-05 | Tampering | Raindrop behavior under global aspect order | low | accept | See Accepted Risks | closed |
| T-04-06 | Tampering (repo) | Committing user's uncommitted CLAUDE.md edits | low | mitigate | Blob-from-HEAD staging + hunk snapshot diff (04-01 execution) | closed |
| T-04-07 | Tampering | ArticleScoreStore SQL | medium | mitigate | Named parameters; `ELIGIBLE`/`RESCORE_SCOPE`/`MAX_ATTEMPTS` are compile-time constants in `ArticleScoreStore` | closed |
| T-04-08 | Tampering / integrity | Re-score delete scope | medium | mitigate | Shared `RESCORE_SCOPE` for count and delete; `rescoreCountEqualsRowsDeleted`, `rescoreLeavesSkippedReadAndOutOfWindowRows` | closed |
| T-04-09 | Denial of service / cost | FAILED rows retried forever | medium | mitigate | `attempts < MAX_ATTEMPTS (3)` in NEEDS_SCORING; `failedAttemptsCountUpToExhaustion` | closed |
| T-04-10 | Tampering / integrity | Ingest/sweep double-write races | low | mitigate | `ON CONFLICT` upserts in `ArticleScoreStore` (SCORED overwrites FAILED; SKIPPED first-writer-wins) | closed |
| T-04-11 | Information disclosure | last_error column | low | mitigate | Fixed-text contract enforced by scorer no-echo test (T-04-12) | closed |
| T-04-12 | Information disclosure | article_score.last_error and scorer logs | high | mitigate | `ScoringFailure.describe` (class name, status, request id only); `permanentFailuresRecordAFixedTextAttempt` | closed |
| T-04-13 | Denial of service / cost | Re-billing loops | high | mitigate | Unknown errors permanent, bounded at 3 attempts; `writeErrorAfterBilledSuccessRecordsAFailedAttempt` | closed |
| T-04-14 | Tampering | Prompt injection via feed title/summary | medium | mitigate | Feed text only in the state object (`ArticleStateBuilder.build`); questions from shared `InterestQuestions.forRubric` | closed |
| T-04-15 | Denial of service | DB connection held across a ~93s call | medium | mitigate | `ArticleScoringService.score()` carries no `@Transactional`; only `ArticleScoreStore.writeScored` is transactional | closed |
| T-04-16 | Tampering / integrity | Stored versions not matching what was sent | low | mitigate | Profile/topics snapshotted before `judge()`; post-call version re-check (WR-01) | closed |
| T-04-17 | Denial of service | Scoring work on the polling thread | high | mitigate | `InterestScoringListener` only enqueues and catches; dedicated jev-score executor; `ScoringIsolationTest` | closed |
| T-04-18 | Denial of service | Unbounded memory/threads from ingest flood | medium | mitigate | Queue capacity 1000 (`MyfeederProperties`); `TaskRejectedException` caught in `ScoringQueue` | closed |
| T-04-19 | Denial of service | Ids stuck in-flight after a drop | medium | mitigate | In-flight id released on rejection and in finally; `rejectedArticleIsReleasedForTheSweep` | closed |
| T-04-20 | Denial of service | Displacing Boot's applicationTaskExecutor | medium | mitigate | `@Bean(defaultCandidate = false)` in `InterestScoringConfig` | closed |
| T-04-21 | Information disclosure | Listener/queue logs | low | mitigate | Logs carry counts, ids and exception class names only | closed |
| T-04-22 | Denial of service | Sweep blocking/crashing scheduler thread | high | mitigate | `InterestScoringSweep` selects + enqueues only, body in try/catch; `failuresAreContained` | closed |
| T-04-23 | Denial of service / cost | Backfill 429 storms | medium | mitigate | One scoring thread; batch cap 50/2 min; sweep pauses while OPEN/FORCED_OPEN | closed |
| T-04-24 | Denial of service | Scoring stays paused after outage | medium | mitigate | Auto OPEN→HALF_OPEN + `runsWhenTheBreakerIsHalfOpen` | closed |
| T-04-25 | Tampering (test isolation) | Suite contexts sweeping | low | mitigate | Test YAML `sweep-initial-delay: PT1H`, no key | closed |
| T-04-26 | Tampering / integrity | POST /rescore deleting more than confirmed | high | mitigate | Shared `RESCORE_SCOPE`; `rescoreCountEqualsRowsDeleted` + full-stack test | closed |
| T-04-27 | Denial of service | Resetting scores nothing can rebuild | medium | mitigate | 409 guard; `rescoreRefusesWhenNotConfigured`, `rescoreRefusesInColdStart` | closed |
| T-04-28 | Denial of service / cost | Repeated Re-score clicks | low | accept | See Accepted Risks | closed |
| T-04-29 | Information disclosure | /status counts | low | accept | See Accepted Risks | closed |
| T-04-30 | Tampering (repo) | Committing user's uncommitted CLAUDE.md edits | low | mitigate | Blob-from-HEAD staging + hunk snapshot diff (04-07 Task 3) | closed |
| T-04-31 | Tampering / integrity | Confirming against a stale count | medium | mitigate | `gcTime: 0` rescore count query (`useInterest.ts`); `reopeningFetchesAFreshCount` | closed |
| T-04-32 | Denial of service / cost | Accidental/repeated resets | low | mitigate | Inline confirm; button disabled while pending/unsaved/unconfigured/cold start | closed |
| T-04-33 | Information disclosure | Inline error text | low | accept | See Accepted Risks | closed |
| T-04-34 | Denial of service | Status polling load | low | mitigate | `refetchInterval` gated on configured && !coldStart && eligibleUnscored > 0 | closed |
| T-04-35 | Tampering / integrity | Status counts disagreeing with sweep/Re-score | medium | mitigate | One `ArticleScoreStore.counts` statement on `ELIGIBLE`; `countsUseTheEligibilityWindow`, `statusCountsEligibleUnscoredAndExhaustedFailures` | closed |
| T-04-09-01 | Elevation of privilege | Dev overlay activated inside the suite | high | mitigate | `DevProfileConfigTest.noTestActivatesTheDevProfile`, `withoutTheProfileTheSuiteConfigStaysOffline`; `MyfeederApplicationTests.suiteContextStaysOffline` | closed |
| T-04-09-02 | Information disclosure | Key values in assertion messages | medium | mitigate | Probe removes systemEnvironment, injects fake key; boolean-only key checks | closed |
| T-04-09-03 | Information disclosure | Git commits during 04-09 | high | mitigate | Explicit-path commits; verified no `.envrc`, `.claude/CLAUDE.md` or `.planning/config.json` committed since the 04-09 plan | closed |
| T-04-09-04 | Tampering | Dev overlay drifting from main | low | mitigate | `devOverlayResolvesEveryMainKeyToMainsValue` (mutation-checked) | closed |
| T-04-09-05 | Information disclosure | Overlay shipping in production image | low | accept | See Accepted Risks | closed |
| T-04-09-06 | Denial of service | bootTestRun smoke leaking processes | low | mitigate | Dedicated port 18089 precheck + EXIT-trap pkill (04-09 Task 1) | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-04-01 | T-04-04 | Single scoring thread bounds concurrency; a timeout-exhausted call is one breaker failure; the preview shares the 30s timeout by D-05 | plan 04-01 | 2026-09-24 |
| AR-04-02 | T-04-05 | Raindrop keeps the same HTTP attempt count; fewer breaker trips and fail-fast when open | plan 04-01 | 2026-09-24 |
| AR-04-03 | T-04-28 | Single-user LAN app; UI disables Re-score while pending; sweep drains at one thread, 50 per 2 minutes | plan 04-08 | 2026-09-24 |
| AR-04-04 | T-04-29 | Counts only, no content or key; same posture as other unauthenticated LAN /api endpoints | plan 04-06 | 2026-09-24 |
| AR-04-05 | T-04-33 | Server details are fixed text (D-18); React escapes rendered text | plan 04-07 | 2026-09-24 |
| AR-04-06 | T-04-09-05 | `src/test/resources` is never packaged by bootJar; Helm sets no `dev` profile | plan 04-09 | 2026-09-24 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-24 | 41 | 41 | 0 | gsd-secure-phase (L1 grep-depth, orchestrator) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-24
