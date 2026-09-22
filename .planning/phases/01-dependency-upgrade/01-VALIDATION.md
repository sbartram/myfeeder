---
phase: "1"
slug: "dependency-upgrade"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| UPG-01 | Target versions declared | static | `grep -E 'springframework.boot"\) version "4.0.8"\|springAiVersion"\] = "2.0.1"\|springCloudVersion"\] = "2025.1.3"' build.gradle.kts \| wc -l` → 3 | ✅ | ⬜ pending |
| UPG-01 | Resolved classpath is on the target line | static | `./gradlew -q dependencies --configuration runtimeClasspath \| grep -E 'spring-boot:4.0.8\|spring-ai-anthropic.*2.0.1'` | ✅ | ⬜ pending |
| UPG-01 | All backend tests pass | unit+integration | full suite command | ✅ | ⬜ pending |
| UPG-01 / D-01 | Reactor Netty transport preserved | integration/probe | Assert that the auto-configured request factory is `ReactorClientHttpRequestFactory` | ❌ W0 (planner decides: permanent test or probe) | ⬜ pending |
| UPG-01 / D-02 | Timeouts actually bound | integration/probe | Assert connect 5s and read 30s on the bound `spring.http.clients.*` settings | ❌ W0 (planner decides) | ⬜ pending |
| UPG-02 | No in-major updates outstanding | static | `cd src/main/frontend && npm outdated --json` → no entry where current ≠ wanted | ✅ | ⬜ pending |
| UPG-02 | react-router still v6 | static | `node -p "require('./src/main/frontend/package-lock.json').packages['node_modules/react-router-dom'].version"` starts with `6.` | ✅ | ⬜ pending |
| UPG-02 | Type-check + tests | unit | `cd src/main/frontend && npx tsc -b && npm test` | ✅ | ⬜ pending |
| UPG-03 | Deployed version | smoke | `curl -s http://192.168.44.204/api/version` → 0.1.24 | ✅ | ⬜ pending |
| UPG-03 | Clean startup | smoke | `kubectl -n myfeeder rollout status deploy/myfeeder`; logs contain `Started MyfeederApplication` and 0 ` ERROR ` lines | ✅ | ⬜ pending |
| UPG-03 | Feeds poll as before | smoke (before/after) | `/api/feeds` compared before and after: no feed goes from errorCount 0 to >0, and `lastSuccessfulPollAt` advances | ✅ (baseline: 46 feeds, 6 erroring) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Start Docker Desktop, then record a baseline full-suite run on the tree before the upgrade.
- [ ] Capture baseline JSON from `/api/feeds` and `/api/version` before any deploy step.
- [ ] Add a transport/timeout assertion (D-01/D-02), either as a permanent test under `src/test/java/org/bartram/myfeeder/config/` or as a documented probe.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| New articles appear in the reader | UPG-03 | Success criterion requires visual confirmation | Open http://192.168.44.204. Confirm articles with a `fetchedAt` after the deploy time (also `curl '/api/articles?limit=5'`) |
| Approve versions published less than 48 hours ago | UPG-02 / D-04 | Supply-chain judgement | Review `npm update` output before the commit |
| Approve `git push origin main` | UPG-03 / D-05 | Push only when the user asks | Human approval checkpoint |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
