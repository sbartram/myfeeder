---
phase: 01-dependency-upgrade
plan: 01
subsystem: infra
tags: [spring-boot, spring-ai, spring-cloud, gradle, reactor-netty, restclient, http-client]

requires: []
provides:
  - Backend on Spring Boot 4.0.8 / Spring AI BOM 2.0.1 / Spring Cloud 2025.1.3
  - Explicit reactor-netty-http dependency that keeps the auto-configured RestClient on ReactorClientHttpRequestFactory
  - Outbound 5s connect / 30s read timeouts actually bound under spring.http.clients.*
  - HttpClientConfigurationTest (Docker-free regression guard for transport and timeout keys)
affects: [01-03, 01-04, typesafe-jev-integration, feed-polling]

actuals:
  tokens: 3209
  tasks: 3
  commits: 3
plan_head_before: 8777658a079e6ecf1436f6a1f54c4270118e7d9b

tech-stack:
  added: ["io.projectreactor.netty:reactor-netty-http (Boot-BOM managed, resolves 1.3.7; previously transitive via Spring AI 2.0.0-M2)"]
  patterns:
    - "ApplicationContextRunner over Boot's HTTP-client auto-configs to test outbound wiring without Docker"
    - "Load src/main/resources/application.yaml from disk in tests (the test-classpath application.yaml shadows it)"

key-files:
  created:
    - src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java
  modified:
    - build.gradle.kts
    - src/main/resources/application.yaml
    - CLAUDE.md

key-decisions:
  - "D-01 applied: pin the RestClient transport with a version-less reactor-netty-http dependency, not with a hand-built request factory"
  - "D-02 applied: rename spring.http.client.* to spring.http.clients.* in its own commit; the only intended behavior change is that feeds slower than 30s now time out instead of hanging a scheduler thread"
  - "Task 2 landed as one fix commit (test + yaml), as the plan specified, not as separate test/feat TDD commits; RED was observed and recorded before the rename"

patterns-established:
  - "Outbound HTTP wiring is asserted with ApplicationContextRunner(HttpClientAutoConfiguration, ImperativeHttpClientAutoConfiguration). MockRestServiceServer cannot catch transport changes because it replaces the request factory."

requirements-completed: [UPG-01]

coverage:
  - id: D1
    description: "Backend resolves on Spring Boot 4.0.8, spring-ai-anthropic 2.0.1 and spring-cloud-commons 5.0.3, with no spring-boot artifact on 4.1.x and no spring-webflux"
    requirement: UPG-01
    verification:
      - kind: other
        ref: "./gradlew -q dependencies --configuration runtimeClasspath (grep checks from Task 1 verify #2)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Auto-configured outbound transport stays Reactor Netty (D-01)"
    requirement: UPG-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java#outboundTransportIsReactorNetty"
        status: pass
    human_judgment: false
  - id: D3
    description: "5s/30s outbound timeouts from the main application.yaml bind to HttpClientSettings (D-02)"
    requirement: UPG-01
    verification:
      - kind: unit
        ref: "src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java#outboundTimeoutsFromMainApplicationYamlBind"
        status: pass
    human_judgment: false
  - id: D4
    description: "Full Docker-backed backend suite green on the upgraded tree (162 tests, 0 skipped, 0 failures, 0 errors)"
    requirement: UPG-01
    verification:
      - kind: integration
        ref: "DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew cleanTest test -x npmBuild -x npmInstall"
        status: pass
    human_judgment: false
  - id: D5
    description: "Root CLAUDE.md documents Boot 4.0.8, the Reactor Netty transport and the spring.http.clients.* keys, and the user's uncommitted hunks are untouched"
    verification:
      - kind: other
        ref: "Task 3 verify: committed-CLAUDE.md greps plus the before/after user-hunk diff comparison"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-09-23
status: complete
---

# Phase 1 Plan 01: Backend Dependency Upgrade Summary

**Backend moved to Spring Boot 4.0.8 / Spring AI 2.0.1 / Spring Cloud 2025.1.3. An explicit `reactor-netty-http` dependency (1.3.7) stops a silent fallback of the RestClient transport to the JDK client, the 5s/30s outbound timeouts now bind under `spring.http.clients.*`, and a Docker-free test guards both.**

## Performance

- **Duration:** about 5 min
- **Started:** 2026-09-23T00:02:46Z
- **Completed:** 2026-09-23T00:07:28Z
- **Tasks:** 3
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- `build.gradle.kts` changes: Boot plugin 4.0.3 -> 4.0.8, `springAiVersion` 2.0.0-M2 -> 2.0.1, `springCloudVersion` 2025.1.0 -> 2025.1.3. The version-less `reactor-netty-http` dependency is added (D-01). Nothing else in the file moved (D-03). axion-release is still 1.21.1, the Gradle wrapper is still 9.4.1, rome/rome-modules 2.1.0 and readability4j 1.0.8 are unchanged, and the BOM import block is untouched.
- Resolved runtimeClasspath: `spring-boot:4.0.8`, `spring-ai-anthropic:2.0.1`, `spring-cloud-commons:5.0.3`, `reactor-netty-http -> 1.3.7`, no `spring-webflux`, and no `org.springframework.boot` artifact on 4.1.x. Spring AI's 4.1.1 requests are pinned down to 4.0.8.
- `application.yaml` renames `spring.http.client:` to `spring.http.clients:`, so connect 5s and read 30s really apply. The stale "JDK client" comment is fixed.
- New `HttpClientConfigurationTest` with 2 tests. It uses no Docker and no MockRestServiceServer.
- Root `CLAUDE.md`: 4 lines corrected (lines 7, 36, 149, 168). The user's uncommitted hunks at lines 40, 55-56, 99 and 105 are unchanged and still uncommitted.

## RED / GREEN evidence

**Task 1: `outboundTransportIsReactorNetty`**
- RED, after the bare version bump and before `reactor-netty-http` was added: failed with `Expecting actual: org.springframework.http.client.JdkClientHttpRequestFactory@753aca85 to be an instance of: org.springframework.http.client.ReactorClientHttpRequestFactory`. This confirms that the bump alone silently switches the transport.
- GREEN, after adding `implementation("io.projectreactor.netty:reactor-netty-http")`: passed.

**Task 2: `outboundTimeoutsFromMainApplicationYamlBind`**
- RED, on the yaml with the singular `client:` key: failed with `org.opentest4j.AssertionFailedError: expected: 5S but was: null`. In the same run `outboundTransportIsReactorNetty` passed.
- The RED record `$HOME/.cache/myfeeder-phase01/task2-red-evidence.json` was checked with `gsd-tools check tdd-red-evidence` and returned `RED_EVIDENCE_OK / target_test_failed`. The checker only parses Node TAP output, so the JUnit XML result was transcribed to TAP one line per testcase.
- GREEN, after renaming to `clients:`: both tests passed.

## Backend test totals

| Run | Classes | Tests | Skipped | Failures | Errors |
|-----|---------|-------|---------|----------|--------|
| Baseline (Boot 4.0.3, HEAD 8777658) | 30 | 160 | 0 | 0 | 0 |
| Upgraded, after Task 1 | 31 | 161 | 0 | 0 | 0 |
| Final, after Task 3 (HEAD 2225423) | 31 | 162 | 0 | 0 | 0 |

The skipped count equals the baseline. The class count is the baseline plus exactly one (`HttpClientConfigurationTest`). Every Testcontainers class (`MyfeederApplicationTests` and the six repository/migration tests) ran against Docker Desktop 29.7.2 through `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock`. Evidence files are `backend-baseline.txt`, `backend-upgraded.txt`, `backend-final.txt` and `runtime-deps.txt`, all under `$HOME/.cache/myfeeder-phase01/`.

## Task Commits

1. **Task 1 (tracer): Version bump plus Reactor Netty transport** - `2bdd267` (build)
2. **Task 2: Bind outbound timeouts via spring.http.clients.* (D-02)** - `fce474b` (fix)
3. **Task 3: Root CLAUDE.md corrections** - `2225423` (docs)

## Files Created/Modified
- `build.gradle.kts`: 3 version strings, plus the `reactor-netty-http` line and its one-line comment
- `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java`: `ApplicationContextRunner` tests for the transport (D-01) and timeout binding (D-02)
- `src/main/resources/application.yaml`: `client:` renamed to `clients:`, plus the line-5 comment fix
- `CLAUDE.md`: Boot version (lines 7 and 36), timeout keys (line 149) and transport/User-Agent gotcha (line 168)

## Decisions Made
- Confirmed from the 4.0.8 jars before writing the test: `AutoConfigurations` is in `org.springframework.boot.autoconfigure`, and `YamlPropertySourceLoader` is in `org.springframework.boot.env`. `ImperativeHttpClientAutoConfiguration` is conditioned on `@ConditionalOnClass(ClientHttpRequestFactory)` and `NotReactiveWebApplicationCondition`, and a plain `ApplicationContextRunner` satisfies both. It exposes a (lazy) `ClientHttpRequestFactory` bean, so the primary assertion was used rather than the builder fallback.
- Task 2 was a single `fix(01-01)` commit (test plus yaml together), because the plan's action and acceptance criteria require exactly that. The TDD discipline was kept by observing and recording RED before the rename.
- Intentional behavior change (D-02): a feed slower than 30s to respond, or 5s to connect, now fails with a timeout instead of hanging a scheduler thread indefinitely. Baseline polls take about 0.1-2s.

## Deviations from Plan

None. The plan was executed as written.

## TDD Gate Compliance

This plan is `type: execute`, not a `type: tdd` plan, and TDD_MODE was not active. Task 2 (`tdd="true"`) followed RED then GREEN with RED evidence recorded, but by the plan's instruction it landed as one `fix(01-01)` commit rather than separate `test(01-01)` and `feat(01-01)` commits. This is advisory only.

## Issues Encountered
- The repo's pre-commit hook (TruffleHog via pre-commit) temporarily stashes unstaged files as a patch and restores them on every commit. I checked after each commit, and the user's uncommitted `.claude/CLAUDE.md`, `.planning/STATE.md`, `.planning/config.json` and root `CLAUDE.md` hunks all survived intact. The Task 3 before/after hunk diff is identical.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness
- The backend half of UPG-01 is done. Plan 01-02 (frontend refresh) is next, then 01-03 (integration gate and STACK.md) and 01-04 (release/deploy).
- `.planning/codebase/STACK.md` still says Boot 4.0.3 and `spring.http.client.*`. Plan 01-03 owns that update.
- When 01-03/01-04 run the full `./gradlew build`, they still need the `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock` override, because build.gradle.kts line 92 still defaults to the Rancher path (out of scope per D-03).

---
*Phase: 01-dependency-upgrade*
*Completed: 2026-09-23*

## Self-Check: PASSED

All key files exist on disk. Task commits 2bdd267, fce474b, 2225423 and summary commit 36aca44 are present in git history. Every task acceptance criterion and plan-level verification command was re-run and passed.
