# Phase 1: Dependency Upgrade - Research

**Researched:** 2026-09-22
**Domain:** Spring Boot 4.0.x patch upgrade (Boot/Spring AI/Spring Cloud BOMs), npm minor/patch refresh, release + k3s deploy
**Confidence:** HIGH (most of the upgrade was run in a scratch copy of the repo; the Docker-backed tests and the deploy were not run)

## Summary

All three targets exist on Maven Central, were published 2026-08-20, and are the newest releases on their lines. There is no Boot 4.0.9, no Spring AI 2.0.2 and no Spring Cloud 2025.1.4 [VERIFIED: repo1.maven.org maven-metadata.xml + HTTP 404 probes]. Spring Cloud 2025.1.3 is built on Spring Boot 4.0.8 [CITED: spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released]. Spring AI 2.0.x says it supports Boot 4.0.x and 4.1.x, but its 2.0.0 and 2.0.1 artifacts are compiled against Boot **4.1.x** (the 2.0.1 anthropic autoconfigure POM declares `spring-boot-autoconfigure` `4.1.1`) [VERIFIED: Maven Central POM]. The project uses the Gradle `io.spring.dependency-management` plugin, which pins every Spring Boot artifact back to 4.0.8. I ran a context-startup probe on Boot 4.0.8 + Spring AI 2.0.1 + Spring Cloud 2025.1.3, and the Anthropic auto-configuration loaded cleanly [VERIFIED: executed probe]. In a scratch copy with only the three version strings changed, `compileJava`, `compileTestJava` and all 145 non-Docker backend tests (23 classes) pass. The deprecation warnings are identical to baseline [VERIFIED: executed]. The 7 Docker/Testcontainers test classes could not run because Docker Desktop is not running on this machine.

**The upgrade silently changes the outbound HTTP transport.** Spring AI 2.0.0-M2's Anthropic starter brought in `spring-boot-starter-webclient`, which brings `reactor-netty-http`. Spring Boot's RestClient auto-detection prefers Reactor Netty over the JDK client, so today every feed fetch, Raindrop call and reader-view extraction goes through `ReactorClientHttpRequestFactory`. Production logs confirm this (`r.netty.http.client.HttpClientConnect`). Spring AI 2.0.1 moved Anthropic onto the official `anthropic-java` SDK (OkHttp) and no longer brings reactor-netty. After the upgrade the auto-configured `RestClient.Builder` uses **`JdkClientHttpRequestFactory`** (HTTP/2 default, `Redirect.NORMAL`, compression on) [VERIFIED: executed probe, base vs upgraded]. No unit test can catch this, because MockRestServiceServer replaces the factory. The phase goal is "behaves exactly as before", so the planner must decide this explicitly. One BOM-managed line, `implementation("io.projectreactor.netty:reactor-netty-http")`, restores Reactor Netty (1.3.7) [VERIFIED: executed probe].

**A pre-existing latent bug surfaced (not caused by the upgrade).** `application.yaml` sets `spring.http.client.connect-timeout` / `read-timeout`. Those keys have been deprecated since Boot 4.0.0 in favour of `spring.http.clients.*`, and they are **not bound**. With the old keys the probe showed `connectTimeout=null/Optional.empty` and `readTimeout=null` on both 4.0.3 and 4.0.8. With the new keys it showed `3s/7s` [VERIFIED: executed probe + spring-boot-http-client metadata]. So production currently runs with **no** configured outbound timeouts, and root CLAUDE.md's statement "Currently 5s/30s" is wrong. Fixing it is a behavior change (adding timeouts), so it is flagged as a decision rather than bundled in silently.

On the frontend, `npm update --save` inside the current majors works. It bumps 20 packages and keeps react-router on 6.30.6. `npx tsc -b`, `npm test` (13 files / 47 tests) and `npm run build` all pass. `npm audit` drops from 15 findings (7 high) to 2 moderate. The 2 left are react-router v6 advisories whose fix needs v7, which is out of scope [VERIFIED: executed in scratch copy].

**Primary recommendation:**
- Change exactly the three version strings in `build.gradle.kts`.
- Add `reactor-netty-http` explicitly to preserve the current transport (pending user confirmation).
- Run `npm update --save` in `src/main/frontend`.
- Prove it with `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew clean build` (Docker Desktop running), `npx tsc -b` and `npm test`.
- Then merge `--no-ff` to `main`, push (user-approved), and run the documented release pipeline from the `main` worktree.
- Verify the deploy by `/api/version` = new version, clean startup logs, and a before/after `/api/feeds` `errorCount` comparison.

## User Constraints

No CONTEXT.md exists for this phase. The user chose to skip discuss-phase. The binding constraints are:

### Locked (from ROADMAP.md Phase 1 + REQUIREMENTS.md)
- Targets are fixed: **Spring Boot 4.0.8, Spring AI BOM 2.0.1, Spring Cloud 2025.1.3** (UPG-01).
- Frontend: **latest minor/patch only, no major bumps; react-router stays on v6** (UPG-02).
- Released and deployed to k3s, clean startup, feeds keep polling (UPG-03).
- Goal wording: "behaves **exactly** as before".

### Claude's Discretion (researcher recommendations; see "Decisions for the Planner")
- Whether to pin the HTTP transport (Reactor Netty) or accept the JDK client.
- Whether to fix the unbound `spring.http.client.*` timeout keys in this phase.
- Whether non-BOM pinned deps (axion-release, Gradle wrapper) move.
- Whether to apply a supply-chain cooldown to very fresh npm releases.

### Deferred / Out of scope
- react-router v7, eslint 10, vitest 5, jsdom 30, TypeScript 6/7, @testing-library/jest-dom 7, @types/node 26 (all major bumps).
- Anything Jev/TypeSafe-related (Phase 2+). Do **not** add `spring-ai-starter-typesafe` here.
- Pre-existing lint errors (`npm run lint` shows 6 errors on baseline, not a phase gate).

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UPG-01 | Backend runs on Spring Boot 4.0.8, Spring AI BOM 2.0.1 and Spring Cloud 2025.1.3 with all backend tests passing | All versions exist and resolve together. Compile plus the 145 non-Docker tests pass in scratch. The context probe loads Spring AI 2.0.1 on Boot 4.0.8. Transport change identified (D-1). The full suite needs Docker Desktop plus a `DOCKER_HOST` override (Pitfall 1). |
| UPG-02 | Frontend deps on latest minor/patch (no majors), `npm test` and `npx tsc -b` pass | `npm update --save` is verified: package.json diff captured, tsc/test/build green, audit 15→2. Two packages are under 48h old (supply-chain flag, D-4). |
| UPG-03 | Released and deployed to k3s, starts cleanly, feeds poll as before | Pipeline documented with exact commands. Release must run from the `main` worktree after a pushed `--no-ff` merge (axion ahead-of-remote check). Baseline captured: 46 feeds, 6 with `errorCount>0`, `/api/version`=0.1.23. Rollback is `./deploy.sh 0.1.23` (no schema change). |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

These are the directives that apply to this phase. Root `CLAUDE.md`, `.claude/CLAUDE.md` and the user's global `~/.claude/CLAUDE.md` all apply.

- **Gradle only, never Maven** (global). Gradle Kotlin DSL.
- **BOM-managed versions for Spring AI and Spring Cloud. Do not put versions on individual dependencies.** This also applies to `reactor-netty-http` if D-1 adopts it: no version string.
- **Git:** non-trivial work goes on a feature branch/worktree (this worktree's `sbartram/main` qualifies). Merges to main use `--no-ff`. **Push only when the user asks.** Subagents must never `git checkout <sha>` and must verify the parent commit before branching.
- **GSD workflow:** edits happen through `/gsd-execute-phase`.
- **Release order is mandatory:** `./gradlew release` **before** `./gradlew clean bootJar`. Always pass `$VERSION` explicitly to `deploy.sh`. Pipe `currentVersion -q` through `grep 'Project version'`.
- **Image build:** `docker build --provenance=false` from the Dockerfile. Never `bootBuildImage` (Docker 29 containerd diffID bug).
- **`clean` before `bootJar`** so the new frontend bundle is embedded.
- **Type-check with `npx tsc -b`**, never `tsc --noEmit` (root tsconfig has `files: []`).
- **Jackson 3 imports:** `tools.jackson.databind.*`, with annotations still in `com.fasterxml.jackson.annotation.*`. Unchanged by this upgrade; compile verified.
- **`RestClientCustomizer`** lives in `org.springframework.boot.restclient`. Unchanged; compile verified.
- **Never construct a raw `RestClient.builder()`.** Use the auto-configured builder so the UA customizer applies. This is relevant to D-1: pin the transport via dependency/property, not by hand-building a request factory.
- **Docker is required** for `./gradlew test` (Testcontainers) and for `docker build`.
- Test `application.yaml` must keep `spring.ai.anthropic.api-key` (dummy). The property name is unchanged in Spring AI 2.0.1 [VERIFIED: autoconfigure metadata lists `spring.ai.anthropic.api-key`].

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| BOM/version management | Build (Gradle `dependencyManagement`) | — | Boot plugin plus imported BOMs pin every managed artifact. Only 3 strings change. |
| Outbound HTTP transport selection | API / Backend (Boot `HttpClientAutoConfiguration`) | Build classpath | Chosen by classpath detection. It changes when a transitive dep disappears (D-1). |
| Outbound timeouts | API / Backend (`application.yaml` → `spring.http.clients.*`) | — | Currently unbound (D-2). |
| Frontend deps | Browser bundle (Vite build, embedded into the jar) | Build (Gradle `npmInstall`/`npmBuild`) | `npm update --save` changes package.json plus the lockfile. Gradle embeds the bundle. |
| Release tagging | Build (axion-release plus `gitPushRelease` Exec) | Git remote | The tag is created on HEAD of the worktree it runs in, then pushed. |
| Image + deploy | CDN/Static-free: registry + k3s (Helm) | — | Image built from `build/libs/*.jar`. Helm sets the tag and secrets. |
| Post-deploy proof | API (`/api/version`, `/api/feeds`) + pod logs | — | Objective before/after evidence. |

## Standard Stack

### Backend: current → target (resolved by Gradle in scratch)

| Item | Current | Target | Evidence |
|------|---------|--------|----------|
| `org.springframework.boot` plugin | `4.0.3` | **4.0.8** | [VERIFIED: build.gradle.kts:3 `id("org.springframework.boot") version "4.0.3"`; Maven Central metadata lists 4.0.8, 4.0.9 → 404] |
| `springAiVersion` | `"2.0.0-M2"` (a **milestone**) | **2.0.1** | [VERIFIED: build.gradle.kts:28 `extra["springAiVersion"] = "2.0.0-M2"`] |
| `springCloudVersion` | `"2025.1.0"` | **2025.1.3** | [VERIFIED: build.gradle.kts:29 `extra["springCloudVersion"] = "2025.1.0"`] |
| `io.spring.dependency-management` | 1.1.7 | 1.1.7 (latest; no change) | [VERIFIED: Maven Central `<release>1.1.7`] |

Transitive changes resolved in scratch with `./gradlew dependencies` (runtimeClasspath, base vs upgraded) [VERIFIED: executed]:

| Library | 4.0.3 stack | 4.0.8 stack | Note |
|---------|-------------|-------------|------|
| Spring Framework | 7.0.5 | 7.0.9 | |
| Spring Data (JDBC/Redis/Commons) | 4.0.3 | 4.0.7 | |
| Jackson 3 (`tools.jackson`) | 3.0.4 | **3.1.5** | Minor bump inside the patch line. Boot 4.0.4 adopted Jackson 3.1 because 3.0.x reached end of support; it also fixes a jackson-core CVE (GHSA-72hv-8253-57qq) [CITED: github.com/spring-projects/spring-boot/releases/tag/v4.0.4, issue #49383]. FeedParser tests pass. |
| Jackson 2 (`com.fasterxml`) | 2.20.2 | 2.21.5 | |
| Tomcat | 11.0.18 | 11.0.24 | |
| Netty | 4.2.10 | 4.2.17 | Lettuce still uses it |
| Micrometer | 1.16.3 | 1.16.7 | Spring AI declares 1.17.1; Boot pins it down to 1.16.7, and the context loads |
| Spring Cloud commons/circuitbreaker | 5.0.0 | 5.0.3 | Resilience4j stays **2.3.0** |
| Spring AI anthropic | 2.0.0-M2 (own `AnthropicApi` over RestClient/WebClient) | 2.0.1 (`com.anthropic:anthropic-java-core` 2.52.0 + OkHttp 4.12.0) | Not used by any app code |
| **reactor-netty-http / spring-webflux / spring-boot-starter-webclient** | **present (1.3.3)** | **absent** | → transport switch (D-1) |
| Testcontainers | 2.0.3 | 2.0.5 | test classpath |
| PostgreSQL JDBC | 42.7.10 | 42.7.13 | |
| Lombok (annotationProcessor) | 1.18.42 | 1.18.46 | |
| Flyway / Lettuce / JUnit / Mockito / AssertJ / HikariCP | 11.14.1 / 6.8.2 / 6.0.3 / 5.20.0 / 3.27.7 / 7.0.2 | unchanged | |

### Non-BOM pinned backend deps (decision D-3)

| Dependency | Pinned | Latest | Recommendation |
|------------|--------|--------|----------------|
| `com.rometools:rome` / `rome-modules` | 2.1.0 | 2.1.0 | Already latest. No change. [VERIFIED: Maven Central] |
| `net.dankito.readability4j:readability4j` | 1.0.8 | 1.0.8 | Already latest. No change. [VERIFIED: Maven Central] |
| `pl.allegro.tech.build.axion-release` | 1.21.1 | 1.21.4 | Patch bump available. **Leave it** unless the user opts in. The release mechanics (the `gitPushRelease` override) are fragile, and this phase must release. [VERIFIED: Gradle plugin portal `<release>1.21.4`] |
| Gradle wrapper | 9.4.1 | 9.7.1 | **Leave it.** A minor bump outside the stated targets, and 9.4.1 builds Boot 4.0.8 fine (verified in scratch). |

### Frontend: target versions (`npm update --save`, verified in scratch)

| Package | Lockfile now | Target | Latest overall (excluded major) |
|---------|--------------|--------|--------------------------------|
| @tanstack/react-query | 5.99.2 | 5.103.2 | — |
| dompurify | 3.4.1 | 3.4.15 | — |
| react / react-dom | 19.2.5 | 19.3.0 | — |
| react-router-dom | 6.30.3 | **6.30.6** | 7.18.4 (excluded, locked v6) |
| zustand | 5.0.12 | 5.0.15 | — |
| vite | 8.0.9 | 8.3.0 | — |
| @vitejs/plugin-react | 6.0.1 | 6.1.1 | — |
| vitest | 4.1.5 | 4.1.11 | 5.0.1 (excluded) |
| jsdom | 29.0.2 | 29.1.1 | 30.1.1 (excluded) |
| typescript | 5.9.3 | 5.9.3 (last 5.x; range `~5.9.3`) | 6.0.3 / 7.0.2 (excluded) |
| typescript-eslint | 8.59.0 | 8.70.1 | — |
| eslint / @eslint/js | 9.39.4 | 9.39.5 | 10.x (excluded) |
| eslint-plugin-react-hooks | 7.0.x | 7.1.1 | — |
| eslint-plugin-react-refresh | 0.5.2 | 0.5.7 | — |
| globals | 17.5.0 | 17.12.0 | — |
| @types/react / @types/react-dom | 19.2.14 / 19.2.3 | 19.3.0 / 19.3.0 | — |
| @types/node | 24.12.2 | 24.13.6 | 26.x (excluded) |
| @testing-library/react | 16.3.2 | 16.3.3 | — |
| @testing-library/user-event | 14.6.1 | 14.6.7 | — |
| @testing-library/jest-dom | 6.9.1 | 6.9.1 | 7.0.1 (excluded) |
| @dnd-kit/* | current | current | — |

Quirk: `npm update --save` installed `@eslint/js` 9.39.5 but left its package.json range at `^9.39.4` [VERIFIED: executed diff]. Bump that range by hand if the verifier compares package.json floors. The lockfile is what matters for `npm outdated`.

**Installation (the exact commands):**
```bash
# backend: edit the 3 strings in build.gradle.kts (see Code Examples); no install step
# frontend:
cd src/main/frontend && npm update --save && npx tsc -b && npm test
```

## Package Legitimacy Audit

This phase installs **no new packages**. Every npm package is an existing dependency moving within its major. The only possible new direct backend declaration (D-1, `io.projectreactor.netty:reactor-netty-http`) is already on the production classpath today as a transitive dependency, and its version comes from the Spring Boot BOM. The seam `gsd-tools query package-legitimacy check --ecosystem npm` flags most of them `SUS` for **`too-new`** only. The flag refers to the latest publish date, not the package's age or provenance.

| Package | Registry | Target published | Postinstall | Seam verdict | Disposition |
|---------|----------|------------------|-------------|--------------|-------------|
| @tanstack/react-query@5.103.2 | npm | **2026-09-21 (1 day)** | none | SUS (too-new) | Flagged. Human-verify, or use cooldown D-4 |
| typescript-eslint@8.70.1 | npm | **2026-09-21 (1 day)** | none | SUS (too-new) | Flagged. Human-verify, or use cooldown D-4 |
| @types/node@24.13.6 | npm | 2026-09-19 | none | (not run) | Approved |
| vite@8.3.0 | npm | 2026-09-10 | none | SUS (too-new) | Approved (12 days, established project) |
| react / react-dom@19.3.0 | npm | 2026-09-09 | none | SUS (too-new) | Approved |
| dompurify@3.4.15 | npm | 2026-09-06 | none | SUS (too-new) | Approved (security fixes; see Security Domain) |
| @vitejs/plugin-react@6.1.1 | npm | 2026-08-28 | none | SUS (too-new) | Approved |
| react-router-dom@6.30.6 | npm | 2026-08-18 | none | SUS (too-new) | Approved |
| vitest@4.1.11 | npm | 2026-08-18 | none | SUS (too-new) | Approved |
| zustand@5.0.15 | npm | 2026-08-13 | none | OK | Approved |
| jsdom@29.1.1 | npm | 2026-04-30 | none | SUS (too-new) | Approved |
| eslint@9.39.5 | npm | 2026-07-10 | none | SUS (too-new) | Approved |
| io.projectreactor.netty:reactor-netty-http (D-1) | Maven Central | Boot-managed 1.3.7 | n/a | n/a (seam has no maven) | Approved. Already transitively in prod at 1.3.3 |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** `@tanstack/react-query@5.103.2`, `typescript-eslint@8.70.1`. Both were published under 48h before research. The planner adds a `checkpoint:human-verify` before the `npm update`, or adopts cooldown D-4. The other `too-new` flags are ≥10 days old on long-established packages with no postinstall scripts. They are treated as approved.

## Decisions for the Planner (surface to user; do not decide silently)

**D-1: Outbound HTTP transport (recommended: preserve Reactor Netty).**
Today: `ReactorClientHttpRequestFactory` (reactor-netty 1.3.3, from Spring AI M2). After the bare upgrade: `JdkClientHttpRequestFactory`, `HttpClient.version()=HTTP_2`, `followRedirects()=NORMAL`, `compression=true` [VERIFIED: probe output].
- *Option A (recommended for "exactly as before"):* add `implementation("io.projectreactor.netty:reactor-netty-http")` with no version. Verified: the factory goes back to `ReactorClientHttpRequestFactory`, reactor-netty resolves to 1.3.7, and spring-webflux is not pulled in.
- *Option B:* accept the JDK client. Risks, all [ASSUMED], not observed:
  - The JDK client in HTTP_2 mode sends an `Upgrade: h2c` handshake on plain `http://` feeds, which some servers mishandle.
  - `Redirect.NORMAL` refuses https→http redirects that Reactor Netty's follow-all accepted.
  - The HTTP/2 path differs on some CDNs.

  Only production polling would reveal these. Test mocks replace the factory.
- Note: root CLAUDE.md's User-Agent gotcha already *assumes* a JDK client ("JDK HttpClient default is `Java-http-client`"). The truth is that prod has used Reactor Netty. Either option should update that line.

**D-2: Fix the unbound timeout keys (recommended: yes, as a separate commit in this phase; user confirms).**
`application.yaml` lines 6-9 (verbatim):
```yaml
  http:
    client:
      connect-timeout: 5s
      read-timeout: 30s
```
Neither 4.0.3 nor 4.0.8 binds these. The working keys are `spring.http.clients.connect-timeout` / `read-timeout` [VERIFIED: probe + `spring-boot-http-client-4.0.8.jar` metadata: `spring.http.client.connect-timeout {'replacement': 'spring.http.clients.connect-timeout', 'since': '4.0.0'}`]. Renaming `client:` to `clients:` makes the documented 5s/30s real.
- It is technically a behavior change: a feed slower than 30s would now fail instead of hanging.
- It is low-risk: baseline polls finish in under 2s each.
- It supports the milestone's core value ("never slow feed polling").

If the user wants strict "exactly as before", defer it to a quick task. Either way, correct the root CLAUDE.md claim "Currently 5s/30s" (CLAUDE.md:149).

**D-3: Non-BOM pinned deps.** ROME, rome-modules and Readability4J are already latest. Recommend **no change** to axion-release (1.21.1→1.21.4 available) and the Gradle wrapper (9.4.1→9.7.1). Both are outside the stated targets, and the release tooling must stay stable for UPG-03.

**D-4: npm supply-chain cooldown.** `npm update --save --before=<date>` works on npm 11.19.1 [VERIFIED: executed with `--before=2026-09-15` → react-query 5.102.8, typescript-eslint 8.70.0]. It conflicts with the literal "latest" criterion, because `npm outdated` would then show Wanted > Current. Recommendation: plain `npm update --save` plus a human-verify checkpoint for the two <48h packages. Use the cooldown only if the user prefers it, and then relax the verification to "no in-major updates older than the cooldown date".

**D-5: Where the release runs.** `main` is checked out in another worktree (`/Volumes/data2/scottb/dev/bartram/myfeeder`), and this worktree is on `sbartram/main`, 8 commits ahead of `main` [VERIFIED: `git worktree list`, `git rev-list`]. Recommendation:
1. Finish on `sbartram/main`.
2. Merge `--no-ff` into `main` via `git -C /Volumes/data2/scottb/dev/bartram/myfeeder merge --no-ff sbartram/main`.
3. The user approves `git push origin main`.
4. Run the whole release pipeline **from that worktree**, so the `v0.1.24` tag lands on `main` like `v0.1.23` did.

## Architecture Patterns

### System Architecture Diagram (phase data flow)

```
 build.gradle.kts (3 version strings [+ reactor-netty-http line])      src/main/frontend/package.json + lockfile
            │                                                                   │  npm update --save
            ▼                                                                   ▼
 Gradle resolve: Boot plugin 4.0.8 pins spring-boot-* ◄── imported BOMs     npx tsc -b ─► npm test ─► (vite build)
   (Spring AI 2.0.1 asks for Boot 4.1.1 → pinned down to 4.0.8)                 │
            │                                                                   │
            ▼                                                                   │
 ./gradlew clean build  ── compileJava ─ processResources ◄── npmBuild ◄────────┘
   (Docker Desktop up,      │
    DOCKER_HOST override)   ├─ unit + @WebMvcTest (no Docker)
                            └─ @DataJdbcTest / @SpringBootTest (Testcontainers Postgres+Redis)
            │ all green
            ▼
 merge --no-ff sbartram/main → main (other worktree) ─► [HUMAN] git push origin main
            ▼
 ./gradlew release (axion verifyRelease: clean tree + not ahead of remote) ─► tag v0.1.24 + push
            ▼
 ./gradlew clean bootJar ─► docker build --provenance=false ─► docker push registry.bartram.org/...:0.1.24
            ▼
 ./deploy.sh 0.1.24 (helm upgrade; secrets from env) ─► kubectl rollout status
            ▼
 Evidence: logs "Started MyfeederApplication" + no ERROR │ /api/version == 0.1.24 │ /api/feeds errorCount before≈after
            │ failure
            └──► rollback: ./deploy.sh 0.1.23 (image still in registry; no schema change)
```

### Recommended plan structure
- **Plan 01 (wave 1): Backend BOM bump.** Edit the 3 strings, apply D-1/D-2 as decided, run `./gradlew clean build` with Docker, update the version mentions in root CLAUDE.md (lines 7, 36, the HTTP-client/UA notes) and `.planning/codebase/STACK.md`.
- **Plan 02 (wave 1, parallel; disjoint files): Frontend refresh.** `npm update --save`, then `npx tsc -b`, `npm test`, `npm run build`, then `npm outdated` shows only major-bump rows.
- **Plan 03 (wave 2): Integration gate + release + deploy.**
  1. Final `./gradlew clean build` on the combined tree.
  2. Merge.
  3. Push (human).
  4. Release, image, deploy.
  5. Post-deploy verification (human confirms the reader shows new articles).

  Most of this runs autonomously once secrets are present, but it needs a `checkpoint:human-action` for the push/release approval and a `checkpoint:human-verify` for "new articles appear in the reader".

### Pattern: BOM-only upgrade
Change only the plugin version and the two `extra[...]` properties. Never add explicit versions to Boot/Spring AI/Spring Cloud managed artifacts. The dependency-management plugin already forces Spring AI's Boot 4.1.1 requests down to 4.0.8. Adding versions would defeat that.

### Anti-Patterns to Avoid
- **Overriding Jackson/Micrometer/Spring versions to match what Spring AI 2.0.1 was built against (4.1.x line).** It breaks the Boot 4.0.x alignment. The probe shows the pinned-down versions work.
- **Hand-building a `ClientHttpRequestFactory` to "fix" the transport.** It bypasses the auto-configured builder and the UA customizer. Use the dependency (D-1 A) or `spring.http.clients.imperative.factory`.
- **Running `./gradlew release` in this worktree on `sbartram/main`.** It would tag a commit that is not on `main` and push `sbartram/main`.
- **`bootBuildImage`.** Forbidden by CLAUDE.md (containerd diffID corruption).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Finding in-major npm updates | Manual edits / `ncu` (new tool dependency) | `npm update --save` (+ optional `--before`) | Respects existing `^`/`~` ranges, so it can never cross a major. Verified. |
| Version alignment across BOMs | Explicit per-artifact versions | `io.spring.dependency-management` + BOM imports | Already in place and verified to pin Spring AI's 4.1.x asks down to 4.0.8 |
| HTTP transport choice | Custom `ClientHttpRequestFactory` bean | Classpath dep or `spring.http.clients.imperative.factory` | Keeps the Boot auto-config, UA customizer and global timeout properties |
| Release versioning | Manual `git tag` | `./gradlew release` (axion + `gitPushRelease`) | CLAUDE.md-mandated. Stamps build-info, which the jar and `/api/version` rely on. |
| Rollback | Manual kubectl edits | `./deploy.sh 0.1.23` or `helm -n myfeeder rollback myfeeder` | The 0.1.23 image is in the registry, and there is no Flyway migration in this phase |

## Runtime State Inventory

Not a rename/refactor phase. For the deploy, the state that matters is:

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | Postgres schema at V5. **No new migration** in this phase. | None. Rollback-safe. |
| Live service config | Helm release `myfeeder` (rev 16, image 0.1.23); Redis cache (Spring Cache) | Helm upgrade sets only `app.image.tag`. Redis entries are serialized by Jackson (3.0.4→3.1.5). Previously cached values could fail to deserialize after the Jackson minor bump [ASSUMED low risk]. If startup/API logs show cache deserialization errors, flush via `kubectl -n myfeeder exec deploy/myfeeder-redis -- redis-cli FLUSHALL`. |
| OS-registered state | None | — |
| Secrets/env vars | `MYFEEDER_PG_PASSWORD`, `MYFEEDER_ANTHROPIC_API_KEY`, `MYFEEDER_RAINDROP_API_TOKEN`. All three are **set** in the current shell (presence checked, values not read). Only the raindrop token is in `.envrc`. | Deploy step must run in a shell where they are set. `SPRING_AI_ANTHROPIC_API_KEY` env name unchanged in Spring AI 2.0.1. |
| Build artifacts | `build/libs/*.jar`, `src/main/resources/static/` | `./gradlew clean bootJar` (mandated) |

## Common Pitfalls

### Pitfall 1: `./gradlew test` points at a nonexistent Docker socket
**What goes wrong:** Testcontainers can't find Docker, and all 7 repository/context test classes fail.
**Why:** build.gradle.kts:92 (verbatim): `environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///Users/scottb/.rd/docker.sock")`. That is a Rancher Desktop path, and `~/.rd/docker.sock` does not exist. This machine runs **Docker Desktop**: `~/.docker/run/docker.sock`, with `/var/run/docker.sock` symlinked to it. The daemon is currently **not running**.
**How to avoid:** `open -a Docker`, poll `docker info` until it is up, then run `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew clean build`. Do not edit build.gradle.kts for this (out of scope).
**Warning signs:** `Could not find a valid Docker environment` or `IllegalStateException` in `TestcontainersConfiguration`.

### Pitfall 2: Silent transport switch (D-1)
**What goes wrong:** Everything passes, but prod feeds start failing or redirecting differently under the JDK HTTP/2 client.
**How to avoid:** Decide D-1 explicitly. If you keep the JDK client, compare `errorCount` per feed before and after the deploy (Validation Architecture).
**Warning signs:** New `FeedFetchException`/`ResourceAccessException` WARNs for feeds that were healthy on 0.1.23.

### Pitfall 3: axion `release` refuses to run
**What goes wrong:** `verifyRelease` fails on uncommitted changes or "ahead of remote".
**Why:** axion checks by default for (a) staged/unstaged changes to tracked files and (b) local commits that are not pushed [CITED: axion-release-plugin.readthedocs.io/en/latest/configuration/checks]. The `main` worktree currently has ` M .serena/project.yml`. This worktree has modified `CLAUDE.md` and `.claude/CLAUDE.md` (user's own GSD-install edits, **not** phase work).
**How to avoid:**
- Push `main` before `release`. The user must approve the push.
- Resolve or stash the `.serena/project.yml` change with the user's consent. Use a named stash, per the environment rules.
- Never commit the unrelated CLAUDE.md edits as part of phase commits. Stage files explicitly.

Do not use `-Prelease.disableChecks` without asking.

### Pitfall 4: Jar stamped `-SNAPSHOT` / wrong image tag
**How to avoid:** Follow the order exactly: `release` → `clean bootJar` → `VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')` → build/push → `./deploy.sh $VERSION`. Expected `VERSION=0.1.24` (current computed: `0.1.24-sbartram-main-SNAPSHOT` on this branch) [VERIFIED: `./gradlew currentVersion`].

### Pitfall 5: Deprecated/unbound config keys hide misconfiguration
**What goes wrong:** Boot does not warn about deprecated keys unless `spring-boot-properties-migrator` is present, so `spring.http.client.*` silently does nothing (D-2).
**How to avoid:** If D-2 is adopted, verify with a unit/probe test that reads the factory's timeouts. Otherwise at least fix the CLAUDE.md claim.

### Pitfall 6: "Clean logs" misjudged against a noisy baseline
**What goes wrong:** Someone reads the pre-existing per-feed WARNs as an upgrade regression.
**Why:** The production baseline already has `WARN ... FeedPollingService : Failed to poll feed 'X'`: about 200 of 4,604 poll log lines over 2 days, **0 ERROR lines**, plus occasional reactor-netty `PrematureCloseException` traces [VERIFIED: `kubectl logs --tail=5000`].
**How to avoid:** Define "clean startup" as:
- `Started MyfeederApplication` present
- no `ERROR` lines and no `APPLICATION FAILED TO START` in the first 2 minutes
- no new *class* of exception
- per-feed failure WARNs only for feeds that already had `errorCount>0` before

### Pitfall 7: Stale frontend in the image
**How to avoid:** Always use `clean bootJar` (mandated). Confirm the bundle hash changed. Scratch build: `index-BQIrFRxz.js` 362.55 kB → `index-DmtnXdgs.js` 395.67 kB after the npm update (+33 kB, mostly React 19.3/TanStack).

## Code Examples

### build.gradle.kts: the entire backend change (Option A of D-1 shown)
```kotlin
// Current (verbatim, build.gradle.kts:3, :28-29):
//   id("org.springframework.boot") version "4.0.3"
//   extra["springAiVersion"] = "2.0.0-M2"
//   extra["springCloudVersion"] = "2025.1.0"
plugins {
	java
	id("org.springframework.boot") version "4.0.8"
	id("io.spring.dependency-management") version "1.1.7"
	id("pl.allegro.tech.build.axion-release") version "1.21.1"
}
extra["springAiVersion"] = "2.0.1"
extra["springCloudVersion"] = "2025.1.3"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	// D-1 Option A: keep the pre-upgrade outbound transport. Spring AI 2.0.0-M2 brought this in
	// transitively; 2.0.1 does not. Version comes from the Spring Boot BOM (1.3.7 on 4.0.8).
	implementation("io.projectreactor.netty:reactor-netty-http")
	// ...rest unchanged
}
```

### application.yaml: D-2 (only if adopted)
```yaml
spring:
  http:
    clients:            # was "client:" — deprecated since Boot 4.0.0 and NOT bound
      connect-timeout: 5s
      read-timeout: 30s
```

### Transport/timeout probe (throwaway verification, adapted from the research probe)
```java
// @SpringBootTest(classes = Cfg.class, webEnvironment = NONE,
//   properties = "spring.autoconfigure.exclude=<DataSource,Flyway,DataJdbcRepositories,DataRedis,DataRedisRepositories autoconfigs>")
RestClient rc = builder.build();                          // auto-configured RestClient.Builder
Field f = rc.getClass().getDeclaredField("clientRequestFactory");
f.setAccessible(true);
Object factory = f.get(rc);   // expect ReactorClientHttpRequestFactory (D-1 A) or JdkClientHttpRequestFactory (D-1 B)
```
Autoconfig class names for the exclude list, read from the 4.0.8 jars:
- `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
- `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`
- `org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration`
- `org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration`
- `org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration`

Don't commit this probe unless the planner wants a permanent regression test for D-1/D-2. If it does, a leaner permanent test asserts `ClientHttpRequestFactoryBuilder.detect()` is `ReactorClientHttpRequestFactoryBuilder`.

### Release + deploy (verbatim from root CLAUDE.md, run in the `main` worktree)
```bash
cd /Volumes/data2/scottb/dev/bartram/myfeeder        # main worktree (D-5)
./gradlew release
./gradlew clean bootJar
VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')
docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:$VERSION .
docker push registry.bartram.org/bartram/myfeeder:$VERSION
./deploy.sh $VERSION       # deploy.sh:22-23 dereference $MYFEEDER_PG_PASSWORD and $MYFEEDER_ANTHROPIC_API_KEY under set -u
kubectl -n myfeeder rollout status deploy/myfeeder
kubectl -n myfeeder logs deploy/myfeeder --tail=20
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring.http.client.*` | `spring.http.clients.*` (`imperative.factory`, `connect-timeout`, `read-timeout`, `redirects`) | Boot 4.0.0 | The old keys are deprecated and unbound. The app's timeouts are inert (D-2). |
| Spring AI Anthropic via its own `AnthropicApi` (RestClient/WebClient) | Official `anthropic-java` SDK (OkHttp) | Spring AI 2.0 GA line | Drops reactor-netty/webflux from the classpath (D-1) |
| Jackson 3.0.x in Boot 4.0.x | Jackson 3.1.x | Boot 4.0.4 | Minor bump in a patch line; CVE fix |
| Spring Cloud CB factory default TimeLimiter | Default TimeLimiterConfig no longer applied | Spring Cloud 2025.1.3 | **No impact.** myfeeder uses `io.github.resilience4j...annotation.CircuitBreaker`/`Retry` on `RaindropApiClientImpl`, not `Resilience4JCircuitBreakerFactory`. |

**Deprecated/outdated:**
- `@types/dompurify` (in devDependencies) is a deprecated stub, because dompurify ships its own types. Leaving it is harmless. Removing it is out-of-scope cleanup; mention only.
- Compile deprecation warnings are identical before and after: `JsonNode.asText()` (FeedParser.java:249) and `isUnprocessableEntity()` (ArticleControllerTest.java:60) [VERIFIED: `-Xlint:deprecation` both builds]. No new deprecations are introduced.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The JDK HttpClient in HTTP_2 mode sends an h2c upgrade on `http://` URLs, and some feed servers mishandle it | D-1 | If D-1 B is chosen, some feeds could start failing. Detected by the errorCount comparison; rollback available. |
| A2 | Reactor Netty's FOLLOW redirect mode follows https→http, while the JDK `NORMAL` does not | D-1 | Same as A1 (edge feeds) |
| A3 | Redis-cached values survive the Jackson 3.0.4→3.1.5 minor bump | Runtime State | Cache read errors after deploy. Mitigate with FLUSHALL (cache only). |
| A4 | The user wants the release tagged on `main` (as with v0.1.23) rather than on `sbartram/main` | D-5 | Tag placement/branch push differs from habit |
| A5 | Adding real 5s/30s timeouts (D-2) will not break any currently-working feed (baseline polls are ~0.1–2s) | D-2 | A very slow feed would start erroring. Visible in errorCount. |

## Open Questions

1. **D-1 transport:** preserve Reactor Netty (recommended) or accept the JDK client?
   - Recommendation: preserve it (one line), and revisit consciously later.
2. **D-2 timeout fix in this phase?**
   - Recommendation: yes, as its own commit. Otherwise log it as a quick task.
3. **The Docker-backed test suite has not been observed on 4.0.8.** The Flyway/Testcontainers/Postgres driver deltas are small (Testcontainers 2.0.3→2.0.5, pgjdbc 42.7.10→42.7.13, Flyway unchanged), but not run.
   - Recommendation: the first executor task starts Docker Desktop and runs the full suite on an **unchanged** tree to get a baseline, then on the upgraded tree. That separates pre-existing failures from regressions (tests use `postgres:latest`/`redis:latest`, which float).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | build | ✓ | Corretto 25.0.4 (`.sdkmanrc` java=25.0.4-amzn) | — |
| Gradle wrapper | build | ✓ | 9.4.1 | — |
| Node / npm | frontend | ✓ | v26.9.0 / 11.19.1 (`/opt/homebrew/bin/npm`, which Gradle's `npmExec` prefers) | — |
| Docker daemon | Testcontainers tests, `docker build` | ✗ **not running** (Docker Desktop installed; socket `~/.docker/run/docker.sock`) | — | `open -a Docker`. No fallback for UPG-01 full suite or UPG-03 image. |
| kubectl (context `k3s-ansible`) | deploy/verify | ✓ | reachable. `myfeeder` ns: image 0.1.23, pod Running | — |
| helm | deploy | ✓ | v4.3.0 | — |
| Registry `registry.bartram.org` | push | ✓ | `/v2/` → 200 | — |
| Secrets env vars | deploy.sh | ✓ (set in the current shell) | — | Human supplies them if the executor's shell lacks them |
| git remote push (SSH) | release | ✓ presumably | origin `git@github.com:sbartram/myfeeder.git` | — |
| pre-commit (trufflehog) | commits/pushes | ✓ | `.pre-commit-config.yaml` runs trufflehog on pre-commit **and** pre-push | — |

The git-lfs pre-push hook gotcha in CLAUDE.md is **not currently present**. The main clone's `.git/hooks/` has only `pre-commit`, and `lfs.locksverify` is unset. Note it but take no action.

**Missing dependencies with no fallback:** Docker daemon (must be started; `open -a Docker` is autonomous, otherwise a human-action checkpoint).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework (backend) | JUnit Jupiter 6.0.3 + Mockito 5.20 + Spring Boot test slices + Testcontainers 2.0.5 |
| Framework (frontend) | Vitest 4.1.x + React Testing Library + jsdom |
| Config file | `build.gradle.kts` (`tasks.withType<Test>`); `src/main/frontend/vite.config.ts` |
| Quick run (backend, no Docker) | `./gradlew test -x npmBuild -x npmInstall --tests 'org.bartram.myfeeder.controller.*' --tests 'org.bartram.myfeeder.service.*' --tests 'org.bartram.myfeeder.parser.*' --tests 'org.bartram.myfeeder.integration.*' --tests 'org.bartram.myfeeder.scheduler.*'` (~6s, 145 tests) |
| Quick run (frontend) | `cd src/main/frontend && npx tsc -b && npm test` (~3s, 47 tests) |
| Full suite | `DOCKER_HOST=unix:///Users/scottb/.docker/run/docker.sock ./gradlew clean build` (includes npmBuild + all 30 test classes) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UPG-01 | Versions are the targets | static | `grep -E 'springframework.boot"\) version "4.0.8"\|springAiVersion"\] = "2.0.1"\|springCloudVersion"\] = "2025.1.3"' build.gradle.kts \| wc -l` → 3 | ✅ |
| UPG-01 | Resolved classpath is on the target line | static | `./gradlew -q dependencies --configuration runtimeClasspath \| grep -E 'spring-boot:4.0.8\|spring-ai-anthropic.*2.0.1\|spring-cloud-commons.*5.0.3'` | ✅ |
| UPG-01 | All backend tests pass | unit+integration | full suite command above; then assert no failures in `build/test-results/test/*.xml` | ✅ (30 classes exist) |
| UPG-01 / D-1 | Transport preserved (if Option A) | integration | a throwaway or permanent probe test asserting `ReactorClientHttpRequestFactory` | ❌ Wave 0, only if the planner wants it permanent |
| UPG-01 / D-2 | Timeouts actually bound (if adopted) | integration | probe asserting `readTimeout=PT30S`, `connectTimeout=5000` | ❌ Wave 0 (optional) |
| UPG-02 | No in-major updates outstanding | static | `cd src/main/frontend && npm outdated --json \| node -e 'const o=JSON.parse(require("fs").readFileSync(0));const bad=Object.entries(o).filter(([k,v])=>v.current!==v.wanted);console.log(bad);process.exit(bad.length?1:0)'` | ✅ |
| UPG-02 | react-router still v6 | static | `node -p "require('./src/main/frontend/package-lock.json').packages['node_modules/react-router-dom'].version"` starts with `6.` | ✅ |
| UPG-02 | Type-check + tests | unit | `cd src/main/frontend && npx tsc -b && npm test` | ✅ |
| UPG-03 | Deployed version | smoke | `curl -s http://192.168.44.204/api/version` → `"version":"0.1.24"` | ✅ endpoint exists (returns 0.1.23 now) |
| UPG-03 | Clean startup | smoke | `kubectl -n myfeeder logs deploy/myfeeder --since=10m \| grep -c ' ERROR '` = 0 **and** `grep -q 'Started MyfeederApplication'`; `kubectl -n myfeeder rollout status deploy/myfeeder` succeeds | ✅ |
| UPG-03 | Feeds poll as before | smoke (before/after) | Before the deploy: `curl -s http://192.168.44.204/api/feeds > /tmp/feeds-before.json`. After ≥20 min: re-fetch and compare. Every feed with `errorCount==0` before still has `errorCount==0`, and `lastSuccessfulPollAt` has advanced for most feeds. `kubectl logs \| grep -c 'Polled feed'` > 0. | ✅ (baseline: 46 feeds, 6 with errorCount>0) |
| UPG-03 | New articles appear in the reader | manual | Human opens http://192.168.44.204 and sees articles with `fetchedAt` after the deploy time (also `curl '/api/articles?limit=5'`) | manual-only: visual confirmation per the success criterion |

### Sampling Rate
- **Per task commit:** backend quick run (no Docker) or frontend quick run, whichever the task touched.
- **Per wave merge:** full suite (`./gradlew clean build` with Docker).
- **Phase gate:** full suite green, `npm outdated` gate passes, post-deploy smoke checks pass, then `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] Start Docker Desktop and record a **baseline full-suite run on the un-upgraded tree** (separates floating-`postgres:latest` flakiness from regressions).
- [ ] Capture `/api/feeds` and `/api/version` baseline JSON before any deploy step.
- [ ] (Optional, per D-1/D-2) a transport/timeout assertion test under `src/test/java/org/bartram/myfeeder/config/`.

## Security Domain

`security_enforcement: true`, ASVS level 1. This phase changes no auth, session or input-handling code. Its security value is dependency hygiene (ASVS V14.2, dependency management).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation / output encoding | indirectly | DOMPurify (sanitizes feed/extracted HTML) goes 3.4.1 → 3.4.15. Unchanged `FORBID_TAGS/FORBID_ATTR: ['style']` config. |
| V6 Cryptography | no | (bouncycastle 1.81→1.85.2 transitive via Spring Cloud; not used directly) |
| V14 Configuration / Dependencies | **yes** | BOM upgrade + `npm update`; `npm audit` gate |

### Known vulnerabilities addressed / remaining

| Item | Before | After | Note |
|------|--------|-------|------|
| jackson-core 3.0.4 (GHSA-72hv-8253-57qq, CVSS 8.7) | vulnerable | fixed (3.1.5) | [CITED: Boot issue #49383 / 4.0.4 notes] |
| Spring Cloud Commons CVE-2026-59284 (writable env actuator endpoint) | present | fixed (5.0.3) | The app exposes only the health endpoint (default), so low exposure |
| Spring AI 2.0.1: 7 CVEs (PDF reader, ONNX, session, file ops, semantic cache, Redis repos, tool dispatch) | n/a | fixed | None of these modules are on the classpath [CITED: javarubberduck.com 2026-08-22] |
| DOMPurify ≤3.4.12 advisories (IN_PLACE bypasses, hook pollution, etc.; 6 moderate + 4 low) | vulnerable (3.4.1) | fixed (3.4.15) | myfeeder does not use IN_PLACE mode, but the upgrade is still the right hygiene |
| vite ≤8.0.15 (`server.fs.deny` bypass on Windows, high; launch-editor NTLM) | vulnerable (dev only) | fixed (8.3.0) | dev-server only |
| npm audit total | 15 (1 low, 7 moderate, 7 high) | **2 moderate** | [VERIFIED: `npm audit --json`] |
| react-router ≤6.x: GHSA-wrjc-x8rr-h8h6 (open redirect via backslash in `<Link>`/`useNavigate`), GHSA-337j-9hxr-rhxg (SSR hydration) | present | **remains** (fix is v7.18.0, locked out) | Accepted risk. The SPA has no SSR, and navigation targets are app-internal. Record in the phase SECURITY/verification notes. |

### Known Threat Patterns for this phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Compromised fresh npm release (supply chain) | Tampering | Lockfile committed. Check postinstall (none found). Human-verify or cooldown for <48h releases (D-4). |
| Secret leakage during deploy (`--set secrets.*` on the helm CLI) | Information disclosure | Unchanged existing practice. Never echo the env vars. trufflehog pre-commit/pre-push hook guards commits. |
| Outbound SSRF guard regression via transport switch | Tampering | `FeedUrlValidator` runs before the request regardless of the factory. `FeedFetcherTest`/`FeedUrlValidatorTest` pass on 4.0.8. |

## Sources

### Primary (HIGH confidence, tool-verified this session)
- Maven Central `maven-metadata.xml` + POMs: spring-boot-dependencies, spring-ai-bom, spring-ai-autoconfigure-model-anthropic 2.0.0-M2…2.0.1, spring-ai-anthropic 2.0.1, spring-cloud-dependencies, dependency-management plugin, rome, rome-modules, readability4j; Gradle plugin portal (axion); services.gradle.org (Gradle versions)
- Scratch-copy execution (`git archive HEAD`): `./gradlew dependencies` diffs, compile with `-Xlint:deprecation`, 145 non-Docker tests, Spring context probes (transport, timeouts, Anthropic autoconfig with dummy/blank key, reactor-netty restore)
- `spring-boot-http-client-4.0.3.jar` / `4.0.8.jar` `spring-configuration-metadata.json`
- Scratch npm runs: `npm outdated`, `npm update --save`, `--before`, `npx tsc -b`, `npm test`, `npm run build`, `npm audit --json`, `npm view <pkg> time/scripts.postinstall`
- Context7 `/spring-projects/spring-boot/v4.0.3`: RestClient HTTP client detection order and `spring.http.clients.imperative.factory`
- GitHub release notes v4.0.4–v4.0.8 (`gh api repos/spring-projects/spring-boot/releases/tags/...`)
- Production (read-only): `kubectl get/logs`, `helm history`, `curl /api/version|/api/feeds|/actuator/health`

### Secondary (MEDIUM confidence)
- https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/: Boot 4.0.8 baseline, TimeLimiter change, CVE-2026-59284
- https://spring.io/blog/2026/08/20/spring-boot-4-0-8-available-now/: 77 fixes, no features
- https://github.com/spring-projects/spring-ai/issues/6465: Spring AI 2.0.x artifacts aligned to Boot 4.1 despite documented 4.0.x support (open)
- https://axion-release-plugin.readthedocs.io/en/latest/configuration/checks/: default release checks
- https://github.com/spring-projects/spring-boot/issues/49383: Jackson 3.1 in Boot 4.0.x

### Tertiary (LOW confidence)
- https://javarubberduck.com/java/news-2026-08-22-spring/: Spring AI 2.0.1 CVE list (cross-checked only against the module list)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Versions resolved and compiled; tests and context probes executed.
- Architecture / release flow: HIGH for mechanics (read from repo + CLAUDE.md + axion docs). MEDIUM for the worktree/push choreography (depends on user preference, A4).
- Pitfalls: HIGH for the transport switch, unbound timeouts and Docker socket (all executed or observed). MEDIUM for the JDK-client behavioral risks (assumed).
- Full Docker-backed suite on 4.0.8: **not observed** (Docker not running).

**Research date:** 2026-09-22
**Valid until:** 2026-10-06 for the npm side (fast-moving; re-run `npm outdated` at execution time). 2026-10-22 for the backend (next Boot 4.0.x patch is expected around then; the targets are locked regardless).
