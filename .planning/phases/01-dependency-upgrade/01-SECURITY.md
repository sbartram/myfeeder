---
phase: "1"
slug: "dependency-upgrade"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-22"
---

# Phase 1 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Maven Central → Gradle build | BOM bumps bring in new managed transitive jars | Third-party code (build and runtime classpath) |
| npm registry → workstation / bundle | Package code runs at install, test and build time, and ships in the SPA | Third-party code (production bundle) |
| App → remote feed servers | Untrusted hosts. The transport and timeouts bound what a hostile or slow server can do to scheduler threads | Untrusted feed content |
| Production bundle → browser | Feed and extracted HTML rendered after DOMPurify | Untrusted HTML |
| Local build ↔ Docker (Testcontainers) | Floating `postgres:latest` / `redis:latest` images | Test data only |
| Feature branch → main (local merge) | The verified tree becomes the release candidate | Source code |
| Workstation → GitHub origin (public) | Commits and tags become public | Source, tags (must hold no secrets) |
| Workstation → registry.bartram.org | The pushed image is what production runs | Container image |
| Workstation → k3s API (Helm) | Deploy secrets passed as `--set secrets.*` | Credentials (high) |
| Workstation → feed hosts (soak probe) | One GET per rechecked feed | Status code only |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-01-01 | Tampering | Gradle BOM bump | medium | mitigate | The build.gradle.kts diff is +5/−3 lines. runtimeClasspath has no spring-webflux. The only 4.1.1 Boot requests (from Spring AI) resolve `4.1.1 -> 4.0.8` | closed |
| T-01-02 | Denial of Service | Outbound RestClient timeouts | medium | mitigate | `application.yaml:7-9` binds `spring.http.clients` connect-timeout 5s / read-timeout 30s. Guarded by `HttpClientConfigurationTest.outboundTimeoutsFromMainApplicationYamlBind` (green) | closed |
| T-01-03 | Elevation of Privilege | SSRF guard (FeedFetcher / FeedUrlValidator) | high | mitigate | `FeedFetcher.java:47` calls `urlValidator.validate(url)` before any request. FeedFetcherTest and FeedUrlValidatorTest are green (162/162) | closed |
| T-01-04 | Tampering | Transitive bumps (Jackson 3.1.5, Spring Cloud Commons 5.0.3) | low | accept | See the Accepted Risks Log. The full suite is green | closed |
| T-01-SC | Tampering | npm installs | high | mitigate | Lockfile-only resolve, then human approval ("approved"), then a sha256 drift guard, then `npm ci` (01-02-SUMMARY). `package-lock.json` is committed | closed |
| T-01-05 | Tampering | DOMPurify → 3.4.15 | medium | mitigate | `ReadingPane.tsx:90` still has `FORBID_TAGS: ['style']`. DOMPurify resolved to 3.4.15 (past the ≤3.4.12 advisories). 47/47 frontend tests green | closed |
| T-01-06 | Tampering | react-router-dom 6.x advisories | medium | accept | See the Accepted Risks Log | closed |
| T-01-07 | Information Disclosure | vite dev-server advisories | low | accept | See the Accepted Risks Log. vite is 8.3.0 | closed |
| T-01-08 | Information Disclosure | Anthropic key in local smoke | low | mitigate | The smoke run used `SPRING_AI_ANTHROPIC_API_KEY=test-dummy-key` (01-03 plan). No code path calls Anthropic | closed |
| T-01-09 | Tampering | Merge sbartram/main → main | medium | mitigate | 01-03-SUMMARY: the merge tree equals main^2, the code paths equal verified-sha.txt, and the push was held until the 01-04 approval | closed |
| T-01-10 | Tampering | Uncommitted .serena/project.yml | low | mitigate | The user chose `restore`. The byte-identity check exited 0 immediately before it, and no stash was created (01-03-SUMMARY) | closed |
| T-01-11 | Elevation of Privilege | Smoke subscription via FeedFetcher | low | accept | See the Accepted Risks Log | closed |
| T-01-12 | Information Disclosure | Deploy secrets | high | mitigate | Presence-only checks (`set`/`UNSET`). No evidence file contains a secret value (`grep -rlF` found 0 files for all three secrets). No evidence files are committed. Compensating control: there was no pre-push hook in the release clone, so a manual TruffleHog scan over `02f74a1..main` ran before the push and found 0 findings (01-04-SUMMARY) | closed |
| T-01-13 | Tampering | Published artifact (tag, jar, image) | high | mitigate | The user answered `approve` at the D-05 gate before any publish. Tag v0.1.24 is on the `--no-ff` merge 29da9aa. Image digest sha256:f1517e2d… The deployment runs `:0.1.24` | closed |
| T-01-14 | Denial of Service | Production rollout | high | mitigate | RollingUpdate plus startupProbe. `rollout status` succeeded, 0 ERROR lines, 46 feeds registered, and the 20-min soak passed. The scripted rollback to 0.1.23 existed and was not needed | closed |
| T-01-15 | Tampering | Redis cache across the Jackson bump | low | mitigate | Conditional FLUSHALL path. No deserialization errors were observed, so no flush was needed (01-04-SUMMARY) | closed |
| T-01-16 | Repudiation | Release provenance | low | mitigate | Tag v0.1.24 is on the merge commit 29da9aa. `/api/version` reports 0.1.24 with buildTime 2026-09-23T00:54:42Z. The SUMMARY records the commit, tag and digest | closed |
| T-01-17 | Information Disclosure | Soak recheck probe | low | mitigate | The probe covered only feeds that went from clean to erroring. It sent a single GET with no credentials, discarded the body and recorded only the status. No `POST /poll` against production (01-04 plan/SUMMARY) | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low. Only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01-01 | T-01-04 | Patch and minor bumps that fix published CVEs (jackson-core GHSA-72hv-8253-57qq, Spring Cloud Commons CVE-2026-59284). The full test suite is the regression gate, and that is sufficient at ASVS L1 | Scott Bartram (plan 01-01 approval) | 2026-09-22 |
| AR-01-02 | T-01-06 | The react-router v6 advisories GHSA-wrjc-x8rr-h8h6 and GHSA-337j-9hxr-rhxg need v7 to fix, and v7 is deferred by CONTEXT. There is no SSR, and navigation targets are app-internal, so feed content cannot reach the open-redirect vector | Scott Bartram (01-02 approval) | 2026-09-22 |
| AR-01-03 | T-01-07 | The vite advisories affect only the dev server. Production serves a static bundle from Spring | Scott Bartram (plan 01-02 approval) | 2026-09-22 |
| AR-01-04 | T-01-11 | The smoke used a fixed public HTTPS feed, with FeedUrlValidator active, in a short-lived local run on Testcontainers-only data | Scott Bartram (plan 01-03 approval) | 2026-09-22 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-22 | 18 | 18 | 0 | /gsd-secure-phase (orchestrator, ASVS L1 grep-depth short-circuit) |

## Security Audit 2026-09-22

| Metric | Count |
|--------|-------|
| Threats found | 18 |
| Closed | 18 |
| Open | 0 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-22
