---
phase: "2"
slug: "jev-client-foundation"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-23"
---

# Phase 2 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Environment / K8s Secret → Spring properties | The TypeSafe key enters via `MYFEEDER_TYPESAFE_API_KEY` | API key (secret) |
| Maven Central → Gradle build | `spring-ai-starter-typesafe` 0.1.0 + `typesafe-java-sdk` + `spring-boot-starter-aspectj` join the classpath | Third-party code |
| App → api.typesafe.ai (HTTPS) | Bearer-authenticated calls to a paid, rate-limited API | Key (header), article-derived state, responses/error bodies |
| Caller (Phase 3/4) → JevApiClient | Untrusted feed text becomes request state | Untrusted text |
| Server Retry-After → app thread | An untrusted header decides how long a caller sleeps | Timing hint |
| Test runner → live API | Opt-in smoke test spends real quota | API key |
| Operator shell → deploy.sh → helm --set → K8s Secret → pod env | The key crosses into Helm values, cluster storage, and the container | API key (secret) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-02-01 | Information disclosure | TypeSafeConfig startup log | high | mitigate | Fixed-text INFO; key only via Supplier — `TypeSafeConfigTest.configuredKeyNeverAppearsInOutput` | closed |
| T-02-02 | Denial of service | Pod startup with blank key | high | mitigate | App-owned TypeSafeClient bean — `blankKeyStartsDespiteStarterAutoConfiguration` + negative control `starterAloneFailsOnBlankKey` | closed |
| T-02-03 | DoS / cost | SDK retries stacked under R4j | medium | mitigate | `spring.ai.typesafe.retry.max-retries: 0` (application.yaml:19) — `mainYamlPinsModelTimeoutAndDisablesSdkRetries` | closed |
| T-02-04 | Spoofing | Outbound TLS | medium | mitigate | base-url not overridden in main YAML; test asserts effective `https://api.typesafe.ai` | closed |
| T-02-05 | Information disclosure | Shared RestClient.Builder | low | mitigate | `restClientBuilder.clone()` (TypeSafeConfig.java:59); no logging interceptor | closed |
| T-02-SC | Tampering | New Gradle dependencies | high | mitigate | Exact pin 0.1.0; resolved classpath re-checked 2026-09-23 (only two `org.springaicommunity` artifacts; spring-web 7.0.9, no downgrade) | closed |
| T-02-06 | Information disclosure | JevApiClientImpl 401/403 WARN | high | mitigate | WARN logs status + requestId only (JevApiClientImpl.java:58) — `rejectedKeyLogsWarnWithRequestIdOnly` | closed |
| T-02-07 | Tampering | Outbound state from untrusted text | low | mitigate | SDK Jackson serialization, object top-level, nulls dropped — pinned by raw-body wire tests in `JevApiClientImplTest` | closed |
| T-02-08 | DoS / cost | Keyless/misused client | medium | mitigate | `judgeThrowsNotConfiguredWithoutHttpCall`, `judgeRejectsNullStateAndEmptyQuestionsBeforeHttp` | closed |
| T-02-09 | Information disclosure | JevLiveSmokeTest | medium | mitigate | `@EnabledIfEnvironmentVariable(JEV_LIVE_SMOKE=true)`; skipped in default run (re-verified); live run printed no key (UAT test 1) | closed |
| T-02-10 | Repudiation | Tracing bad scores | low | accept | JevJudgment carries requestId + model — see AR-01 | closed |
| T-02-11 | Denial of service | Retry-After honoring | medium | mitigate | Clamp to [0, `MAX_RETRY_AFTER_MS`=10000] (TypeSafeConfig.java:79) — `retryIntervalHonorsRetryAfterUpToCap` | closed |
| T-02-12 | DoS / cost | Retry storms / bad key | high | mitigate | Single retry layer, 429/5xx/connection only, 401/403 recorded by breaker — `rejectedKeyIsNotRetriedButRecorded`, `breakerOpensAtMinimumCallsAndShortCircuits`, `mainYamlJevInstancesBindAsSpecified` | closed |
| T-02-13 | Denial of service | Slow responses holding threads | medium | mitigate | Jev-only 5s read timeout + 3s slow-call threshold — `readTimeoutSurfacesAsConnectionExceptionAndIsRetried` | closed |
| T-02-14 | Information disclosure | 401/403 through proxy | high | mitigate | `rejectedKeyIsNotRetriedButRecorded` asserts no LEAKCHECK in output | closed |
| T-02-15 | Tampering | Raindrop YAML edits | low | mitigate | Both `RaindropNotConfiguredException` ignore entries retained (application.yaml:44, :68) | closed |
| T-02-16 | Information disclosure | deploy.sh output | medium | mitigate | Fixed warning text only; fake-helm keyed run 2026-09-23 printed no key | closed |
| T-02-17 | Tampering / availability | Stale key after rotation | medium | mitigate | `checksum/secret` annotation; key-A ≠ key-B, tag-only change stable (re-verified) | closed |
| T-02-18 | Denial of service | Deploy with key absent | high | mitigate | No required/fail in chart; keyless render + keyless fake-helm run exit 0 | closed |
| T-02-19 | Information disclosure | Key in Helm history / Secret | low | accept | See AR-02 | closed |
| T-02-20 | Tampering | Comma/backslash mangled by `--set` | low | accept | See AR-03 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-02-10 | JevJudgment carries requestId + model so Phase 4 can persist them per score; no audit log needed at ASVS L1 | plan 02-02 threat model | 2026-09-22 |
| AR-02 | T-02-19 | Same exposure as existing Raindrop/Anthropic secrets (K8s Secret, cluster RBAC, `--history-max 3`); checksum sha256 does not reveal the key | plan 02-04 threat model | 2026-09-22 |
| AR-03 | T-02-20 | TypeSafe keys expected URL-safe; a mangled key yields 401 → visible WARN + open breaker, not silent corruption (also noted as review WR) | plan 02-04 threat model | 2026-09-22 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-23 | 20 | 20 | 0 | orchestrator (L1 grep + re-run tests/helm/deploy checks) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-23
