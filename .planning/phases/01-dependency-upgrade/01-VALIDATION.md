---
phase: "1"
slug: "dependency-upgrade"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: true) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-09-22"
---

# Phase 1: Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Backend: JUnit Jupiter 6 + Mockito + Spring Boot test slices + Testcontainers. Frontend: Vitest 4 + React Testing Library |
| **Config file** | `build.gradle.kts` (`tasks.withType<Test>`); `src/main/frontend/vite.config.ts` |
| **Quick run command** | Backend, no Docker: `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.controller.*' --tests 'org.bartram.myfeeder.service.*' --tests 'org.bartram.myfeeder.parser.*' --tests 'org.bartram.myfeeder.integration.*' --tests 'org.bartram.myfeeder.scheduler.*'`. Frontend: `cd src/main/frontend && npx tsc -b && npm test` |
| **Full suite command** | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew clean build` |
| **Estimated runtime** | Quick: ~6s backend and ~3s frontend. Full: a few minutes, because of Testcontainers |

---

## Sampling Rate

- **After every task commit:** Run the quick command for whichever side the task touched.
- **After every plan wave:** Run the full suite command, with Docker Desktop running.
- **Before `/gsd-verify-work`:** The full suite must be green, the `npm outdated` gate must pass, and the post-deploy smoke checks must pass.
- **Max feedback latency:** ~10s for quick runs.

---

## Per-Task Verification Map

| Req | Behavior | Test Type | Automated Command | File Exists | Status |
|-----|----------|-----------|-------------------|-------------|--------|
| UPG-01 | Target versions declared | static | `grep -E 'springframework.boot"\) version "4.0.8"\|springAiVersion"\] = "2.0.1"\|springCloudVersion"\] = "2025.1.3"' build.gradle.kts \| wc -l` → 3 | ✅ | ✅ green |
| UPG-01 | Resolved classpath is on the target line | static | `./gradlew -q dependencies --configuration runtimeClasspath \| grep -E 'spring-boot:4.0.8\|spring-ai-anthropic.*2.0.1'` | ✅ | ✅ green |
| UPG-01 | All backend tests pass | unit+integration | full suite command | ✅ | ✅ green |
| UPG-01 / D-01 | Reactor Netty transport preserved | integration/probe | Assert that the auto-configured request factory is `ReactorClientHttpRequestFactory` | ✅ `HttpClientConfigurationTest.outboundTransportIsReactorNetty` | ✅ green |
| UPG-01 / D-02 | Timeouts actually bound | integration/probe | Assert connect 5s and read 30s on the bound `spring.http.clients.*` settings | ✅ `HttpClientConfigurationTest.outboundTimeoutsFromMainApplicationYamlBind` | ✅ green |
| UPG-02 | No in-major updates outstanding | static | `cd src/main/frontend && npm outdated --json` → no entry where current ≠ wanted | ✅ | ✅ green |
| UPG-02 | react-router still v6 | static | `node -p "require('./src/main/frontend/package-lock.json').packages['node_modules/react-router-dom'].version"` starts with `6.` | ✅ | ✅ green |
| UPG-02 | Type-check + tests | unit | `cd src/main/frontend && npx tsc -b && npm test` | ✅ | ✅ green |
| UPG-03 | Deployed version | smoke | `curl -s http://192.168.44.204/api/version` → 0.1.24 | ✅ | ✅ green |
| UPG-03 | Clean startup | smoke | `kubectl -n myfeeder rollout status deploy/myfeeder`; logs contain `Started MyfeederApplication` and 0 ` ERROR ` lines | ✅ | ✅ green |
| UPG-03 | Feeds poll as before | smoke (before/after) | `/api/feeds` compared before and after. Every feed that was healthy before is polled after deploy time (`lastPolledAt` > deploy-time; 304s count) with errorCount 0, or is classified by the recheck and remote-probe step in 01-04 Task 3. `soak.log` contains `Registered polling tasks for N feeds` | ✅ (baseline: 46 feeds, 6 erroring) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Start Docker Desktop, then record a baseline full-suite run on the tree before the upgrade.
- [x] Capture baseline JSON from `/api/feeds` and `/api/version` before any deploy step.
- [x] Add a transport/timeout assertion (D-01/D-02), either as a permanent test under `src/test/java/org/bartram/myfeeder/config/` or as a documented probe.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| New articles appear in the reader | UPG-03 | Success criterion requires visual confirmation | Open http://192.168.44.204. Confirm articles with a `fetchedAt` after the deploy time (also `curl '/api/articles?limit=5'`) |
| Approve versions published less than 48 hours ago | UPG-02 / D-04 | Supply-chain judgement | Review `npm update` output before the commit |
| Approve `git push origin main` | UPG-03 / D-05 | Push only when the user asks | Human approval checkpoint |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 10s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-09-22

---

## Validation Audit 2026-09-22

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Evidence (re-run at audit time): the build.gradle.kts grep returns 3. runtimeClasspath resolves spring-boot:4.0.8 and spring-ai-anthropic:2.0.1. The backend suite has 31 classes and 162 tests, with 0 failures, errors or skips (Docker/Testcontainers). The frontend passes `tsc -b` and 47/47 Vitest tests. `npm outdated` shows 0 in-major updates outstanding. react-router-dom is 6.30.6. Live: `/api/version` reports 0.1.24, the rollout succeeded with image :0.1.24, the pod log has 0 ERROR lines, `Started MyfeederApplication` appears, and the scheduler registered 46 feeds. 40 of 46 feeds are healthy, the same as the pre-deploy baseline. Manual-only rows were confirmed in 01-UAT.md, tests 1 and 2 (both pass).
