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
| 02-01-T1 | 02-01 | 1 | JEV-01, JEV-02/03 (pins) | T-02-01, T-02-02, T-02-03, T-02-04 | Key never logged (value, prefix); blank key cannot crash startup | context (runner) + OutputCapture + main YAML from disk | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.config.TypeSafeConfigTest'` | ❌ created in task | ⬜ pending |
| 02-01-T2 | 02-01 | 1 | JEV-01, JEV-02 (AspectJ) | T-02-SC | Only the two TypeSafe artifacts resolve; no Spring/Jackson downgrade | full suite (Docker) + resolved classpath | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew cleanTest test -x npmBuild -x npmInstall` + `./gradlew -q dependencies --configuration runtimeClasspath` check | ✅ (MyfeederApplicationTests) | ⬜ pending |
| 02-02-T1 | 02-02 | 2 | JEV-03, JEV-01 | T-02-08 | Keyless call makes no HTTP request | unit (MockRestServiceServer) | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevApiClientImplTest'` | ❌ created in task | ⬜ pending |
| 02-02-T2 | 02-02 | 2 | JEV-03 | T-02-06, T-02-07 | 401/403 WARN has status + requestId only; bearer header only on the wire | unit (MockRestServiceServer) + OutputCapture | same as 02-02-T1 | ✅ (after T1) | ⬜ pending |
| 02-02-T3 | 02-02 | 2 | JEV-03 (SC2) | T-02-09 | Live test only on JEV_LIVE_SMOKE=true | gated live test (skipped by default) + end-of-phase human-check | `env -u JEV_LIVE_SMOKE ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest'` (asserts skipped) | ❌ created in task | ⬜ pending |
| 02-03-T1 | 02-03 | 3 | JEV-02 | T-02-12 | N/A | context + AOP + JDK HttpServer stub | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevResilienceTest'` | ❌ created in task | ⬜ pending |
| 02-03-T2 | 02-03 | 3 | JEV-02 (D-07) | T-02-11 | Retry-After wait clamped to [0, 10s] | unit (interval function) + stub | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.config.TypeSafeConfigTest' --tests 'org.bartram.myfeeder.integration.JevResilienceTest'` | ✅ | ⬜ pending |
| 02-03-T3 | 02-03 | 3 | JEV-02 (SC3) | T-02-12, T-02-13, T-02-14 | Bad key opens breaker without retries; no key in output | context + stub + main/test YAML + full suite (Docker) | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.Jev*' --tests 'org.bartram.myfeeder.config.*'` then the full-suite command | ✅ | ⬜ pending |
| 02-04-T1 | 02-04 | 1 | JEV-04 | T-02-17, T-02-18, T-02-19 | Secret only via stringData + secretKeyRef; checksum rolls pod on key change | shell | `helm lint helm/myfeeder --set app.image.tag=t` + `helm template` key-A / key-B / tag-only / unset checks | n/a | ⬜ pending |
| 02-04-T2 | 02-04 | 1 | JEV-04 | T-02-16, T-02-18, T-02-20 | deploy.sh never echoes the key | shell (fake helm on PATH, KUBECONFIG=/nonexistent) | `bash -n deploy.sh` + keyless and keyed fake-helm runs | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Each test file is created by the tracer/first task that needs it; no separate Wave 0 plan exists.

- [ ] `src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java`: JEV-01 cases, keyless log, leak check, main-YAML pins (02-01-T1); interval function (02-03-T2)
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevApiClientImplTest.java`: wire contract, mapping, typed errors (02-02-T1, T2)
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java`: AOP proxy, retry, breaker, 429, timeout, headers, config binding (02-03-T1..T3)
- [ ] `src/test/java/org/bartram/myfeeder/integration/JevLiveSmokeTest.java`: gated by `JEV_LIVE_SMOKE` (02-02-T3)
- [ ] `src/test/resources/application.yaml`: `spring.ai.typesafe` (model, pinned base-url, `retry.max-retries: 0`, no api-key) (02-01-T1) + `resilience4j.*.instances.jev` (02-03-T1)

*Framework install: none — only new main deps are the TypeSafe starter and `spring-boot-starter-aspectj`.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Jev call returns `model == jev-1.13.0` | JEV-03 (SC2) | Needs a real TypeSafe key, not available to agents | `JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=… ./gradlew cleanTest test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info` (`cleanTest` is required because Gradle does not treat env vars as test inputs); harvested from 02-02-T3's human-check |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
