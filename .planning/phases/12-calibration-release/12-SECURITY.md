---
phase: "12"
slug: "calibration-release"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-02"
---

# Phase 12 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| command line and env → driver → psql -v | Candidate strings become SQL text through psql variable interpolation | numeric constants (validated) |
| workstation → prod Postgres (pg.bartram.org) | Read-only replay sessions with the user's password | prod rows, password (secret) |
| workstation → prod app API | GET-only cross-check and topic-name lookup | scores, topic names (private) |
| replay output / cache evidence → public repo note | TSV values flow into a committed, public file | ids and counts only |
| committed yaml → running app | Constants applied at query time, validated at startup | engagement constants |
| docs → future operators | CLAUDE.md drives future tuning | tuning procedure |
| workstation → public GitHub origin | Pushed history and tags become public and permanent | commits, release tags |
| workstation → registry, k3s | Image push, Helm deploy with the user's keys | image, API keys (secret) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-12-01 | Tampering | driver candidate fields via psql -v | high | mitigate | Anchored numeric regex on all six fields and env values before connecting; `driverRejectsAMalformedSixFieldCandidate`, `driverRejectsANonNumericEngagementValue` (`InterestCalibrationReplaySqlTest`) | closed |
| T-12-02 | Tampering | replay statements against the database | high | mitigate | `PGOPTIONS='-c default_transaction_read_only=on'` (`scripts/interest-calibration-replay.sh:75`); `replayIsReadOnly` | closed |
| T-12-03 | Denial of Service | calibrating to constants prod refuses (WR-02) | medium | mitigate | `engagement_ok` (script:58) mirrors `isValid`; `driverRefusesWhatTheAppRefuses` 64-cell grid | closed |
| T-12-04 | Information Disclosure | test replay output under $HOME/.cache | low | mitigate | OUT_DIR is a JUnit `@TempDir` in both replay tests; driver keeps `umask 077` (script:83) | closed |
| T-12-05 | Tampering | backfill sections drifting from the app blend | medium | mitigate | `backfillDiffersFromTheAppBlendOnlyInTheEngagedCte`, backfill drift entries, `aBackfillBypassFails` variants | closed |
| T-12-06 | Information Disclosure | new sections printing names/titles into TSVs | medium | mitigate | Sections select ids and counts; `newSectionsPrintNoNamesOrTitles` | closed |
| T-12-07 | Tampering | count-only statements writing to prod | high | mitigate | Plain aggregate SELECTs; `replayIsReadOnly`; read-only session | closed |
| T-12-08 | Tampering | replay sessions against prod | high | mitigate | Read-only session probed before the replay; GET-only cross-check (12-03-SUMMARY, 12-CALIBRATION.md); UAT test 3 confirmed no prod write | closed |
| T-12-09 | Information Disclosure | titles, topic names or password in the committed note | high | mitigate | Privacy grep of 37 titles and 10 topic names plus password `grep -qF`: no hit (12-CALIBRATION.md); inputs mode 600 in mode-700 dir | closed |
| T-12-10 | Tampering (integrity) | tuning on thin or unfaithful data | medium | mitigate | Cap-0 cross-check passed; D-02 floor gated by human checkpoint; D-03 fallback taken and marked revisit | closed |
| T-12-11 | Information Disclosure | MYFEEDER_PG_PASSWORD in logs or files | high | mitigate | `test -n` only, no `set -x`; checks ran as scratchpad scripts that never print the value; evidence grep: no hit | closed |
| T-12-12 | Tampering | tuning via Helm --set or env | medium | mitigate | Values only in committed yaml; `DevProfileConfigTest`; 12-05 preflight found no `MYFEEDER_INTEREST_*` env | closed |
| T-12-13 | Denial of Service | shipping constants the app refuses at startup | high | mitigate | `shippedMainYamlStarts` (`MyfeederPropertiesValidationTest`); prod startup clean on 0.3.1 | closed |
| T-12-14 | Repudiation | docs diverging from shipped values | low | mitigate | CLAUDE.md states 0.25 / 0.5 / 8, matching the approved-constants line and main yaml | closed |
| T-12-15 | Information Disclosure | deploy keys and Helm secrets in files, logs or echo | high | mitigate | `test -n` checks, `helm get values` piped through `jq -c '{app}'`, note grepped per key without printing; dump chmod 600 (12-05-SUMMARY); UAT test 3 | closed |
| T-12-16 | Tampering | release tags and history | high | mitigate | v0.3.0 (db5acd7) and v0.2.1 (5461d0a) unchanged locally and on origin; v0.3.1 new; no force-push (re-checked 2026-10-02) | closed |
| T-12-17 | Denial of Service | bad release takes prod down | high | mitigate | Suites green in preflight (723 backend, 413 frontend); rollback revision 20 recorded; pre-deploy pg_dump; 10-min soak 0 ERROR | closed |
| T-12-18 | Information Disclosure | topic names from "Why N?" rows in evidence | medium | mitigate | Raw JSON kept in `$C/sc3-raw/`; committed rows projected to id/replay/api/count; note has no topic name | closed |
| T-12-19 | Tampering | user's untracked files deleted or published without consent | medium | mitigate | User chose `approve-exclude-untracked`; files later committed on the user's own request (d995411); nothing deleted | closed |
| T-12-20 | Tampering | constants tuned by env/Helm override | medium | mitigate | Preflight asserted no `MYFEEDER_INTEREST_*` env and user-supplied values exactly `{app: {image: {tag}}}` | closed |
| T-12-SC | Tampering | npm/pip/cargo installs | low | accept | No package installed in any plan; image built from the tested jar with the existing Dockerfile | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-12-01 | T-12-SC | No new package entered the build in Phase 12; psql was already on the workstation | plan threat model (12-01 to 12-05) | 2026-10-02 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-02 | 21 | 21 | 0 | /gsd-secure-phase orchestrator (ASVS L1 short-circuit; plan-time register, no auditor spawn) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-02
