---
type: Runbook
title: myfeeder Operations Runbook
description: Practical build, run, release, and deployment guidance for myfeeder — local dev commands, the Docker/Helm/k3s deployment pipeline, required secrets/env vars, the interest-scoring operational levers (throttling, calibration, emergency stop), and known infrastructure gotchas that have caused real incidents.
resource: build.gradle.kts
tags: [operations, deployment, docker, helm, kubernetes, runbook, interest-scoring]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-08T14:57:22.664Z
sources:
  - id: openwiki-source-ef7629065e06131773b2ed05
    resource: repo://.claude/skills/interest-calibration/SKILL.md
  - id: openwiki-source-cea976cdcc9e10d604735b48
    resource: repo://.planning/milestones/v0.3.0-phases/12-calibration-release/12-CALIBRATION.md
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-828d24ee9ccc8738afe410fb
    resource: repo://deploy.sh
  - id: openwiki-source-e5dcb45322e31f835560b357
    resource: repo://helm/myfeeder/templates/app-deployment.yaml
  - id: openwiki-source-61633941f0ae598bce52a4cb
    resource: repo://helm/myfeeder/templates/app-secret.yaml
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
generated: { by: "openwiki/0.9.0", at: "2026-11-08T14:57:22.664Z" }
---

# Operations Runbook

## Local development

```bash
./gradlew build                 # full build; runs npmBuild (frontend) via Gradle task wiring
./gradlew test                  # backend tests; requires Docker running (Testcontainers)
cd src/main/frontend && npm test              # frontend tests (Vitest)
./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"   # single test class
./gradlew bootTestRun           # run app with Testcontainers-managed Postgres/Redis (no Compose needed)
./gradlew bootRun               # run app with compose.yaml-managed Postgres/Redis
cd src/main/frontend && npm run dev           # Vite dev server on :5173, proxies /api to :8080
```

Docker must be running for both `./gradlew test` and `./gradlew bootRun`. `compose.yaml` defines Postgres + Redis for local dev; `TestcontainersConfiguration` (in `src/test/java/...`) provides the same for tests and `bootTestRun`.

## Release and deploy pipeline

"Cut a release" means, in this exact order:

```bash
./gradlew release                       # 1. cut + push release tag (axion) — BEFORE building
./gradlew clean bootJar                 # 2. build the jar (clean forces fresh frontend embed)
VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')
docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:$VERSION .
docker push registry.bartram.org/bartram/myfeeder:$VERSION
./deploy.sh $VERSION                    # needs MYFEEDER_PG_PASSWORD + MYFEEDER_ANTHROPIC_API_KEY (MYFEEDER_RAINDROP_API_TOKEN, MYFEEDER_TYPESAFE_API_KEY optional)
kubectl -n myfeeder rollout status deploy/myfeeder
kubectl -n myfeeder logs deploy/myfeeder --tail=20
```

Ordering matters: `release` **before** `bootJar`, or the jar gets stamped `-SNAPSHOT`. Always pass `$VERSION` explicitly to `deploy.sh` — omitting it makes axion compute the *next* snapshot version, which won't match any pushed image.

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys the app + Redis; Postgres is external at `pg.bartram.org`
- **Image build**: Dockerfile-based (`eclipse-temurin:25-jre` runtime), not Paketo buildpacks — see the Docker 29 gotcha below for why. `docker build` only packages `build/libs/*.jar`; the jar must already contain the embedded frontend (built via `./gradlew clean bootJar`, which chains `npmBuild` → `processResources`).
- **Secrets required for deploy** (`deploy.sh`, surfaced to the pod via the Helm `*-secret` Secret and consumed as env vars in `app-deployment.yaml`):
  - `MYFEEDER_PG_PASSWORD` and `MYFEEDER_ANTHROPIC_API_KEY` are **required** — `deploy.sh` runs under `set -euo pipefail` and passes them straight through to `--set secrets.postgresPassword=...`/`--set secrets.anthropicApiKey=...` with no fallback, so an unset value aborts the script.
  - `MYFEEDER_RAINDROP_API_TOKEN` is optional — see [Raindrop Integration](../integrations/raindrop.md); if unset, `deploy.sh` prints a warning, defaults it to an empty string, and Raindrop stays disabled in that deployment.
  - `MYFEEDER_TYPESAFE_API_KEY` (the Jev API key) is also optional — if unset, `deploy.sh` prints a warning and defaults it to an empty string. Unlike the two required secrets, a blank/unset key is never fatal anywhere in the stack: `config/TypeSafeConfig` only logs `TypeSafe Jev not configured; interest scoring disabled` and the app starts normally with interest scoring disabled (see [Interest Scoring Workflow](../workflows/interest-scoring.md)).

## Interest-scoring operational levers

The interest-scoring subsystem (Jev-backed article scoring, blend/engagement weighting, badge tiers) has three distinct operational levers: calibration (how the scoring math is tuned), throttling (how load on the upstream Jev API is controlled), and emergency stop (how scoring is disabled outright). All three are deploy-time or config-time levers, not runtime API toggles.

### Calibration: application.yaml only, never Helm/env

The calibrated blend and tier constants — engagement `open-weight`/`save-weight`/`cap`, the gap-discovery `near-miss` threshold, and the badge `tiers.high`/`tiers.neutral` thresholds (plus `profile-points`/`learn-rate`/`learned-cap`) under `myfeeder.interest.blend.*` and `myfeeder.interest.suggestions.*` in `src/main/resources/application.yaml` — are changed **only** by editing `application.yaml` (and `src/test/resources/application-dev.yaml` when a calibrated value differs from the test yaml's fixed fixtures), using the `interest-calibration` skill (`.claude/skills/interest-calibration/SKILL.md`). This is decision D-14: never set these through Helm `--set` or ad hoc env var overrides, because that would silently diverge the running config from the yaml that `InterestScoreQueriesTest` and friends assume, and from what the next `helm upgrade` would otherwise converge to.

The skill drives `scripts/interest-calibration-replay.sh`, a read-only psql replay of the verbatim `InterestScoreQueries` blend (needs `MYFEEDER_PG_PASSWORD` and LAN access), and records the outcome in a phase calibration note. The Phase 12 engagement-constant calibration decision history (data floor D-02, nudge rule D-04, dormant-engagement rule D-09, backfill-simulation rule D-10, and the final outcome) lives in `.planning/milestones/v0.3.0-phases/12-calibration-release/12-CALIBRATION.md`: on 2026-10-02 the D-03 fallback kept the engagement defaults (open-weight 0.25, save-weight 0.5, cap 8) because prod data was too thin to clear the D-02 floor (13 of the 30 engaged-SCORED-unvoted articles required, 0 of the 3 non-negative topics with engagement needed), and the constants were marked revisit rather than changed.

### Throttling: sweep pacing for sustained 429s/breaker trips

If the sweep causes sustained 429s or repeated `jev` circuit-breaker trips, the sanctioned fix is to lower `myfeeder.interest.sweep-batch-size` (default 50) and/or lengthen `myfeeder.interest.sweep-delay` (default `PT2M`) in `application.yaml`, then redeploy normally. This reduces how many eligible articles `InterestScoringSweep` enqueues per run and how often it runs, without touching the single-threaded `concurrency: 1` scoring executor or re-introducing a second (SDK-level) retry layer.

For an incident that needs relief before a release can go out, there is an out-of-band emergency lever that bypasses Helm:

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20
```

This restarts the pod with a lower batch size immediately. **This is a Helm three-way-merge gotcha**: once the equivalent value has been committed to `application.yaml` and deployed, the out-of-band env var must be explicitly removed, or a later `helm upgrade` can leave it in place (Helm's three-way merge only reconciles values it manages, not env vars added directly via `kubectl set env`):

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-
```

### Emergency stop: disable scoring entirely

To stop interest scoring outright (e.g. a key compromise or a runaway cost/incident), unset `MYFEEDER_TYPESAFE_API_KEY` and redeploy:

```bash
unset MYFEEDER_TYPESAFE_API_KEY
./deploy.sh $VERSION
```

Because the key is read through a `Supplier` and a blank key only disables the `TypeSafeClient` bean (logged, never fatal — see above), this is a safe, instant way to fully disable Jev calls (sweep, ingest-time scoring, and the preview endpoint all become unconfigured/503) without touching any other secret or rolling back the app version.

## OpenWiki maintenance

This repository's `openwiki/` docs are regenerated by a scheduled GitHub Actions workflow (`.github/workflows/openwiki-update.yml`, daily at 08:00 UTC via `openwiki code --update --print`, opening a PR on branch `openwiki/update`). Do not hand-edit generated pages under `openwiki/` outside of an explicit request — prefer changing source/docs and letting the next scheduled run regenerate these pages. `openwiki/INSTRUCTIONS.md` is the user-authored brief controlling scope/priorities for this wiki; it is not itself regenerated.

## Known infrastructure gotchas (source: `CLAUDE.md`, kept current there — summarized here)

- **Docker 29 + containerd image store corrupts buildpack image exports** — pods failed to start with a wrong-diffID error. Root cause: Docker 29's containerd-backed image store mis-derives layer diffIDs on export for certain images (a known moby bug class), which strict consumers like k3s containerd reject. This is why the build moved from `bootBuildImage` (Paketo) to a plain `Dockerfile`. If you ever consider going back to buildpacks, re-read the full incident writeup in `CLAUDE.md` first — there's a documented emergency recovery procedure using `skopeo` if it recurs.
- **`pg.bartram.org` is split-horizon DNS** — only the LAN resolver (`192.168.44.6`) has the real record; public resolvers return nothing. k3s nodes must use the LAN resolver (configured via `k3s-ansible`'s `lan_dns_servers`), with a CoreDNS forward block routing `bartram.org` there. cert-manager bypasses this via `--dns01-recursive-nameservers-only` so ACME still resolves public records.
- **Helm `startupProbe` gates readiness/liveness** — `startupProbe` (5s × 60 = up to 5 min) must succeed before liveness/readiness probes run. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to the liveness probe — the startup probe is already the gate.
- **`imagePullPolicy`**: use `Always` during development (SNAPSHOT tags get reused/stale under `IfNotPresent`); `IfNotPresent` is only safe with immutable release tags.
- **`MaxDirectMemorySize` (historical, Paketo-only)**: the old buildpack hardcoded a 10M direct-memory cap that starved Netty/Lettuce (Redis client). The current Dockerfile-based image (JRE, container-aware defaults) doesn't need the override, but the Helm chart still sets `JDK_JAVA_OPTIONS` for tuning — use that env var, not `_JAVA_OPTIONS`.
- **Outbound `RestClient` User-Agent**: `config/RestClientConfig.java` registers a `RestClientCustomizer` setting `myfeeder/<version>` on all outbound calls — some CDNs (e.g. Vercel) rate-limit the default JDK HttpClient user agent by returning HTTP 200 with a bogus body. Never construct a raw `RestClient.builder()` bypassing the auto-configured bean, or you'll lose this and get silently garbage responses instead of errors.
- **`./gradlew release` pushes via git CLI, not jgit** — axion-release's bundled jgit can't read OpenSSH-format keys, so `build.gradle.kts` clears the default push action and finalizes via a `gitPushRelease` Exec task calling `git push --follow-tags`. Don't replace this with raw axion auth config.
- **Clipboard API requires HTTPS**: the app is served over plain HTTP on the LAN, so `navigator.clipboard` is unavailable — clipboard actions fall back to `document.execCommand('copy')`.

See [Architecture Overview](../architecture/overview.md) for how the frontend build embeds into the backend jar, [Raindrop Integration](../integrations/raindrop.md) for how the Raindrop token flows through `deploy.sh` into the Helm secret, [Jev Integration](../integrations/jev.md) for the Jev client/resilience design these levers operate on, and [Interest Scoring Workflow](../workflows/interest-scoring.md) for the sweep/eligibility/blend mechanics being tuned or throttled here.
