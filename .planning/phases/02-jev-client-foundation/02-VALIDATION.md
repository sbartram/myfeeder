---
phase: "2"
slug: "jev-client-foundation"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-22"
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot 4.0.8 test starters), AssertJ, Mockito, `MockRestServiceServer`, `ApplicationContextRunner`, `OutputCaptureExtension` |
| **Config file** | `build.gradle.kts` (`useJUnitPlatform()`); `src/test/resources/application.yaml` |
| **Quick run command** | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.Jev*' --tests 'org.bartram.myfeeder.config.*'` (no Docker needed) |
| **Full suite command** | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew cleanTest test -x npmBuild -x npmInstall` (Docker required) |
| **Estimated runtime** | ~30 seconds quick; ~180 seconds full |

---

## Sampling Rate

- **After every task commit:** Run the quick run command
- **After every plan wave:** Run the full suite command (catches Raindrop side effects of enabling AspectJ)
- **Before `/gsd-verify-work`:** Full suite green + helm checks (`helm template` key set/unset/changed, `helm lint`) + `bash -n deploy.sh` + resolved-classpath check (`./gradlew dependencies --configuration runtimeClasspath | grep -E 'typesafe|aspectj|jackson-databind|spring-web:'`)
- **Max feedback latency:** 60 seconds (quick run)

---

## Per-Task Verification Map

*Filled by the planner/executor from PLAN.md task IDs. Requirement → test map from RESEARCH.md:*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | JEV-01 | — | Key never logged (value, length, prefix) | context (runner) + OutputCapture | `./gradlew test --tests '*TypeSafeConfigTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | JEV-01 | — | N/A | integration | `./gradlew test --tests '*MyfeederApplicationTests'` | ✅ | ⬜ pending |
| TBD | TBD | TBD | JEV-02 | — | N/A | context + JDK HttpServer stub | `./gradlew test --tests '*JevResilienceTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | JEV-02/03 | — | N/A | unit (main YAML) | `./gradlew test --tests '*TypeSafeConfigTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | JEV-03 | — | Bearer header only on wire | unit (MockRestServiceServer) | `./gradlew test --tests '*JevApiClientImplTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | JEV-04 | — | Secret never in plain env/values output | shell | `helm template ... --set secrets.typesafeApiKey=A` / `B` / empty + `helm lint helm/myfeeder` | n/a | ⬜ pending |
| TBD | TBD | TBD | JEV-04 | — | N/A | shell | `bash -n deploy.sh` | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java` — JEV-01 cases, keyless log, leak check, main-YAML pins, interval function
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java` — wire contract, mapping, typed errors
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java` — AOP proxy, retry, breaker, 429
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java` — gated by `JEV_LIVE_SMOKE`
- [ ] `src/test/resources/application.yaml` — `spring.ai.typesafe` (model, pinned base-url, `retry.max-retries: 0`, no api-key) + `resilience4j.*.instances.jev`

*Framework install: none — only new main deps are the TypeSafe starter and `spring-boot-starter-aspectj`.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Jev call returns `model == jev-1.13.0` | JEV-03 (SC2) | Needs a real TypeSafe key, not available to agents | `JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=… ./gradlew test --tests '*JevLiveSmokeTest'` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
