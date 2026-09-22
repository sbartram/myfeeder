# Phase 1: Dependency Upgrade - Context

**Gathered:** 2026-09-22
**Status:** Ready for planning
**Source:** /gsd-plan-phase 1. discuss-phase was skipped. These decisions are the user's answers to the questions raised in 01-RESEARCH.md ("Decisions for the Planner").

<domain>
## Phase Boundary

Move the backend to Spring Boot 4.0.8, Spring AI BOM 2.0.1 and Spring Cloud 2025.1.3, and move the frontend to the latest minor/patch release within each current major version (react-router stays on v6). Then release and deploy to k3s. The app must behave as before; the only intentional exception is D-02.
</domain>

<decisions>
## Implementation Decisions

- **D-01 (HTTP client):** Keep Reactor Netty as the outbound HTTP client. Add `implementation("io.projectreactor.netty:reactor-netty-http")` to `build.gradle.kts` without a version (the BOM manages it), so the auto-configured `RestClient` still uses `ReactorClientHttpRequestFactory` after the upgrade. Update the User-Agent gotcha in root CLAUDE.md: it says the JDK HttpClient is the client, but production actually uses Reactor Netty.
- **D-02 (Outbound timeouts):** Fix the timeout keys in this phase, in a commit separate from the version bump. Rename `spring.http.client.*` to `spring.http.clients.*` in `application.yaml` so connect-timeout 5s and read-timeout 30s actually take effect. Update root CLAUDE.md, which currently says "Currently 5s/30s" and cites the old key names.
- **D-03 (Non-BOM pinned deps):** Change nothing outside the stated targets. Do not touch axion-release (1.21.1) or the Gradle wrapper (9.4.1). ROME and Readability4J are already on their latest versions.
- **D-04 (npm policy):** Run plain `npm update --save`, which takes the latest release within each current major version with no cooldown. Add a human-verify checkpoint before committing, so the user can confirm the versions published less than 48 hours ago (`@tanstack/react-query` 5.103.2 and `typescript-eslint` 8.70.1 at research time).
- **D-05 (Release path):** Finish the work on `sbartram/main`. Then `git -C /Volumes/data2/scottb/dev/bartram/myfeeder merge --no-ff sbartram/main`, which requires that worktree's uncommitted `.serena/project.yml` to be dealt with first. Pause for the user to approve `git push origin main`. Then run the documented "Cut a release" pipeline from that worktree: `./gradlew release` → `clean bootJar` → `docker build --provenance=false` → `docker push` → `./deploy.sh $VERSION` → `rollout status` → logs. The expected version is 0.1.24.

### Claude's Discretion
- How to split the work into plans and waves. Research suggested backend (01) and frontend (02) in parallel in wave 1, then verify + release in wave 2.
- Whether to add permanent tests that assert the HTTP client and timeout configuration (D-01/D-02), or verify them with a one-off probe.
</decisions>

<canonical_refs>
## Canonical References

- `.planning/phases/01-dependency-upgrade/01-RESEARCH.md`: versions, probes, pitfalls, and the Validation Architecture section
- `CLAUDE.md` (root): "Cut a release" pipeline, gotchas, and Git workflow
- `build.gradle.kts`, `src/main/resources/application.yaml`, `src/main/frontend/package.json`
</canonical_refs>

<deferred>
## Deferred Ideas

- Bump axion-release to 1.21.4 and the Gradle wrapper to 9.7.1 (D-03). Not in scope.
- Move react-router to v7, which would clear the remaining 2 moderate npm audit advisories. That is a major bump and out of scope.
</deferred>
