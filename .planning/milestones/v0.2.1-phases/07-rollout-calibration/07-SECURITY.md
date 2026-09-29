---
phase: "7"
slug: "rollout-calibration"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-29"
---

# Phase 7 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| server -> browser (`GET /api/interest/status`) | Two tier integers are appended to the status payload and pick a CSS class | display config — non-sensitive |
| repo yaml -> running app | Tier and blend constants come only from committed yaml (D-14) | config values — non-sensitive |
| Jev API responses -> app logs | Resilience4j retry/breaker events are logged by `JevEventLogging` | exception class names, counts, rates |
| workstation -> prod Postgres | Replay driver, watch and check queries; `pg_dump` | article titles, scores, nouls, votes; the PG password (env only) |
| command-line candidates -> psql `-v` | Operator input becomes SQL text in the replay session | numbers only (validated) |
| main worktree -> public GitHub origin | `.envrc`, CLAUDE.md, calibration/backfill docs, tags | docs and titles — public by decision |
| workstation -> registry / k3s API | Image push, `helm --set` of secrets, `kubectl set env` throttle | TypeSafe, PG, Anthropic, Raindrop secrets |
| prod app -> TypeSafe Jev | Billed scoring calls after the rubric save | rubric and article state |
| hand-edited replay SQL -> calibration evidence | Drift guard keeps the replay SQL verbatim to `InterestScoreQueries` | SQL text — no secrets |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-07-01 | Information disclosure | `InterestStatus.tiers` on `/status` | low | accept | Two display integers; see Accepted Risks | closed |
| T-07-02 | Tampering | `myfeeder.interest.blend.tiers.*` binding | low | accept | Committed yaml only, typed `int` binding; see Accepted Risks | closed |
| T-07-03 | Tampering (contract) | `/status` field order and names | medium | mitigate | `tiers` appended last; `statusServesTheConfiguredTierThresholds` asserts existing names | closed |
| T-07-04 | Tampering | `tiers` values into `className` | low | mitigate | Class comes from the fixed `Tier` union; numbers compared, never interpolated | closed |
| T-07-05 | Denial of service (UI) | missing/failed `tiers` | low | mitigate | `DEFAULT_TIERS` fallback; `fallsBackTo70And40WithoutTiers`, `fallsBackWhenStatusFails` | closed |
| T-07-06 | Denial of service (self) | extra `/status` requests | low | mitigate | `staleTime: Infinity`, no interval; `fetchesStatusOnceAndNeverRefetches` | closed |
| T-07-07 | Information disclosure | `JevEventLogging` log lines | high | mitigate | `getSimpleName()` only, no `getMessage()`; tests assert no `LEAKCHECK` | closed |
| T-07-08 | Denial of service (log volume) | retry lines during a 429 burst | low | accept | Bounded per call, single thread; `perArticleErrorsLogNoRetryLine`; see Accepted Risks | closed |
| T-07-09 | Tampering (behavior) | retry/breaker semantics | medium | mitigate | Logger only subscribes via `getEventPublisher()`; JevResilienceTest + MyfeederApplicationTests | closed |
| T-07-10 | Tampering | replay session against prod | high | mitigate | Driver exports `default_transaction_read_only=on`; `replayIsReadOnly` guard | closed |
| T-07-11 | Tampering (SQL injection) | `-v` candidate interpolation | medium | mitigate | Numeric validation → `invalid candidate:` exit 2; `driverRejectsANonNumericCandidate` | closed |
| T-07-12 | Information disclosure | `PGPASSWORD` | high | mitigate | Env only; no `set -x`; never echoed (grep-verified in driver) | closed |
| T-07-13 | Information disclosure | replay output (titles) | low | mitigate | `umask 077` under `$HOME/.cache/myfeeder-phase07/`; learned section prints ids only | closed |
| T-07-14 | Information disclosure | CLAUDE.md content | medium | mitigate | Variable names only; no `NAME=value` secret assignments in CLAUDE.md (git grep) | closed |
| T-07-15 | Repudiation / doc integrity | stale or invented facts | low | mitigate | Plan 07-05 grep-checked each fact against source; OpenWiki/CLAUDE.md match code | closed |
| T-07-16 | Information disclosure | `.envrc` committed to a public repo | high | mitigate | D-16 scan `envrc: SAFE`; commit `abeac7f` adds only `${…}`-indirected assignments; trufflehog pre-commit hook installed and passed | closed |
| T-07-17 | Information disclosure | TypeSafe key in shell/argv/files/evidence | high | mitigate | `test -n` presence only; `grep -F -f` evidence check; residual helm-argv exposure accepted (WR-05, D-04) | closed |
| T-07-18 | Tampering | published tag, jar and image | high | mitigate | Human approval before publish; release→`clean bootJar`; Dockerfile `--provenance=false`; tag `v0.2.0` exists | closed |
| T-07-19 | Denial of service | prod rollout + V6 migration | high | mitigate | RollingUpdate + startupProbe; rollback revision 17 recorded; pre-deploy dump; failure path not needed (07-BACKFILL.md) | closed |
| T-07-20 | Tampering | prod Postgres | high | mitigate | All checks under `default_transaction_read_only=on`; no manual data/schema change | closed |
| T-07-21 | Denial of service (billing) | Jev calls at launch | medium | mitigate | Cold start idled scoring until 07-07; one preview call (`preview-code.txt`) | closed |
| T-07-22 | Information disclosure | dump file on workstation | medium | mitigate | `$HOME/.cache/myfeeder-phase07/` (700/600), never committed | closed |
| T-07-23 | Denial of service (billing, rate limits) | launch backfill | high | mitigate | `concurrency: 1`, `max-retries: 0`, sweep cap, breaker; 07-BACKFILL verdict PASS, 0 breaker transitions, 0 exhausted 429s | closed |
| T-07-24 | Tampering | prod Postgres from the watch | high | mitigate | `watch.sh` exports `default_transaction_read_only=on`; rubric written via app API only | closed |
| T-07-25 | Information disclosure | rubric and key in evidence | medium | mitigate | Loader prints codes/ids only; 07-BACKFILL.md `grep -F -f` checked against live rubric | closed |
| T-07-26 | Repudiation | backfill verdict | low | mitigate | Verdict rows recomputed from raw status/evidence/log files (07-07 Task 3) | closed |
| T-07-27 | Denial of service (self) | unbounded watch | low | mitigate | `watch.stop` marker used; watch exited 23:52:02Z | closed |
| T-07-28 | Tampering | prod DB during calibration | high | mitigate | Only the read-only replay driver touches the DB; drift guard forbids write keywords | closed |
| T-07-29 | Information disclosure | profile/topic text in calibration doc | medium | mitigate | Topics keyed `topic_<id>`; doc `grep -F -f` checked against live rubric | closed |
| T-07-30 | Information disclosure | article titles in a public repo | low | mitigate | Disclosed at checkpoint; user declined redaction ("Title redaction: Not requested") | closed |
| T-07-31 | Repudiation | approved constants | low | mitigate | `approved-constants:` line in 07-CALIBRATION.md | closed |
| T-07-32 | Tampering | shipping unapproved constants | medium | mitigate | yaml derived from `approved-constants:`; prod tiers verified equal (70/22) | closed |
| T-07-33 | Tampering | main vs dev/test yaml drift | medium | mitigate | `DevProfileConfigTest`; test yaml keeps 100/70/40 | closed |
| T-07-34 | Information disclosure | TypeSafe key during deploy | high | mitigate | `test -n` / bad-char checks only; bootTestRun without the key | closed |
| T-07-35 | Denial of service | 0.2.1 rollout | medium | mitigate | RollingUpdate + startupProbe; `./deploy.sh 0.2.0` fallback (no schema change) | closed |
| T-07-36 | Tampering | lingering env override | low | mitigate | `MYFEEDER_INTEREST_*` overrides removed; verify fails if any remain | closed |
| T-07-37 | Tampering | published tag and image (0.2.1) | high | mitigate | Human approval; VERSION exactly 0.2.1; tag `v0.2.1` exists; Dockerfile build | closed |
| T-07-38 | Tampering | non-first blend/badge copies in replay SQL | medium | mitigate | `assertEveryCopyIsVerbatim`; `driftInAnySingleBlendCopyFails`, `aMissingOrExtraCopyFails` | closed |
| T-07-39 | Tampering | working tree during test run | low | mitigate | In-memory variants only; `git diff --exit-code aa2069f` gate | closed |
| T-07-40 | Information disclosure | assertion failure output (07-10) | low | accept | Labels and counts only; see Accepted Risks | closed |
| T-07-41 | Tampering | drifted badge masked by a verbatim decoy | medium | mitigate | Per-line `codeOf` badge check; `aDriftedBadgeFailsDespiteAVerbatimDecoy` | closed |
| T-07-42 | Tampering | extra drifted blend statement in any form | low | mitigate | `BLEND_START` count = 5; `anExtraBlendStatementInAnyFormFails` | closed |
| T-07-43 | Tampering | working tree during test run (07-11) | low | mitigate | In-memory variants only; `git diff --exit-code aa2069f` gate | closed |
| T-07-44 | Information disclosure | assertion failure output (07-11) | low | accept | Case names, labels, counts, line numbers only; see Accepted Risks | closed |
| T-07-SC | Tampering | package installs | low | accept | No package or dependency added in any plan; see Accepted Risks | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-07-01 | T-07-01 | Two display integers with no personal data, key or rubric text, served to the same single-user browser that already sees counts and breaker state | plan 07-01 | 2026-09-29 |
| AR-07-02 | T-07-02 | Values come only from committed yaml; a malformed value fails startup | plan 07-01 | 2026-09-29 |
| AR-07-03 | T-07-08 | At most 2 retry lines + 1 exhausted line per logical call at ~23 calls/min | plan 07-03 | 2026-09-29 |
| AR-07-04 | T-07-17 (residual) | `deploy.sh` passes the key on the helm argv (WR-05 of the deploy script, deferred per D-04) | plan 07-06 | 2026-09-29 |
| AR-07-05 | T-07-40, T-07-44 | Test failure output prints labels/counts only; SQL holds no secret | plans 07-10, 07-11 | 2026-09-29 |
| AR-07-06 | T-07-SC | No package, image or dependency added (throwaway `postgres:17-alpine` for local smoke only) | all plans | 2026-09-29 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-29 | 45 | 45 | 0 | /gsd-secure-phase orchestrator (ASVS L1 grep-depth; register authored at plan time, short-circuit per workflow) |

Notes:
- T-07-16: the trufflehog config declares both `pre-commit` and `pre-push` stages, but only the `pre-commit` hook is installed in this clone (`pre-commit install --hook-type pre-push` would add the push-time scan). Non-blocking: the commit-time scan ran and passed on `abeac7f`, and the committed values are `${…}`-indirected.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-29
