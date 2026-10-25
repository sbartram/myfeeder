---
type: Runbook
title: myfeeder Operations Runbook
description: Practical build, run, release, and deployment guidance for myfeeder — local dev commands, the Docker/Helm/k3s deployment pipeline, required secrets/env vars (including Jev/TypeSafe), interest-scoring throttle/emergency levers, the calibration/tuning workflow, and known infrastructure gotchas that have caused real incidents.
resource: build.gradle.kts
tags: [operations, deployment, docker, helm, kubernetes, runbook, interest-scoring, calibration]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-25T13:07:33.493Z
sources:
  - id: openwiki-source-6d4b4e707b8d60b6ccfa3425
    resource: repo://.github/workflows/openwiki-update.yml
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-828d24ee9ccc8738afe410fb
    resource: repo://deploy.sh
  - id: openwiki-source-e5dcb45322e31f835560b357
    resource: repo://helm/myfeeder/templates/app-deployment.yaml
  - id: openwiki-source-61633941f0ae598bce52a4cb
    resource: repo://helm/myfeeder/templates/app-secret.yaml
  - id: openwiki-source-445c26dc79d49d991b20176d
    resource: repo://scripts/interest-calibration-replay.sh
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
  - id: openwiki-source-dd2d645abee0bc0755ca1501
    resource: repo://src/test/resources/application-dev.yaml
  - id: openwiki-source-f390f7f52c9adafdf38e7480
    resource: repo://src/test/resources/application.yaml
generated: { by: "openwiki/0.7.2", at: "2026-10-25T13:07:33.493Z" }
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

`bootTestRun` loads the **test** `application.yaml` (it runs on the test classpath, which shadows `src/main/resources/application.yaml`), so `TestMyfeederApplication` activates the `dev` profile to restore main's live settings via `src/test/resources/application-dev.yaml` (real Jev base-url, `PT1M` first sweep, 5s/30s HTTP timeouts, etc.). Export `MYFEEDER_TYPESAFE_API_KEY` before `bootTestRun` to exercise Jev scoring locally; never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in the shell that runs `./gradlew test`, or the suite will call the billed Jev API. See [Testing Guide](../testing/guide.md) for the full test-profile layering rules.

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

Ordering matters: `release` **before** `bootJar`, or the jar gets stamped `-SNAPSHOT`. Always pass `$VERSION` explicitly to `deploy.sh` — omitting it makes axion compute the *next* snapshot version, which won't match any pushed image. This also matters for a **chart-only redeploy against an already-released tag** (no new image to build): pass the version explicitly (`./deploy.sh 0.1.2`), since the no-arg default always resolves to the next snapshot.

For a minor release (new schema, dependency, or view) cut the tag with `./gradlew release -Prelease.versionIncrementer=incrementMinor`; the default increments the patch (`build.gradle.kts`).

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys the app + Redis; Postgres is external at `pg.bartram.org`
- **Image build**: Dockerfile-based (`eclipse-temurin:25-jre` runtime), not Paketo buildpacks — see the Docker 29 gotcha below for why. `docker build` only packages `build/libs/*.jar`; the jar must already contain the embedded frontend (built via `./gradlew clean bootJar`, which chains `npmBuild` → `processResources`).
- **`./gradlew release` pushes via the git CLI**, not jgit — see the Gotchas section.

### Secrets for `deploy.sh`

`deploy.sh` runs `helm upgrade --install` and passes every secret through `helm --set`:

| Variable | Required? | Effect if unset |
|---|---|---|
| `MYFEEDER_PG_PASSWORD` | **Required** | `deploy.sh` has no default; the script relies on `set -euo pipefail`, so an unset value fails the Helm call. |
| `MYFEEDER_ANTHROPIC_API_KEY` | **Required** | Same as above. |
| `MYFEEDER_RAINDROP_API_TOKEN` | Optional | `deploy.sh` defaults it to empty, prints a warning, and Raindrop integration stays disabled in that deployment. See [Raindrop Integration](../integrations/raindrop.md). |
| `MYFEEDER_TYPESAFE_API_KEY` | Optional | `deploy.sh` defaults it to empty, prints a warning, and interest scoring (Jev) stays disabled — the app-owned `TypeSafeClient` bean backs off and the app still starts cleanly. See [Jev (TypeSafe) Scoring Integration](../integrations/jev-typesafe.md). |

Because every one of these values is passed as a literal through `helm --set`, **none of them may contain a `,` or a `\`** — either character breaks Helm's `--set` value parsing and the deploy will fail or (worse) silently truncate the value.

## Interest scoring operations

The interest-scoring sweep (`InterestScoringSweep`) calls the external Jev (TypeSafe) API to score articles against the user's interest profile; see [Interest Ranking Workflow](../workflows/interest-ranking.md) for the scoring/queue design and [Jev (TypeSafe) Scoring Integration](../integrations/jev-typesafe.md) for the client, retry, and circuit-breaker mechanics. This section covers the operational levers for when that sweep is causing problems in production (429s, breaker trips) or needs to be stopped entirely.

### Throttle levers

If 429s or breaker trips become a problem, there are two ways to throttle the sweep, in increasing order of urgency:

1. **Committed change (goes through a release)**: lower `myfeeder.interest.sweep-batch-size` (default 50) and/or lengthen `myfeeder.interest.sweep-delay` (default `PT2M`) in `src/main/resources/application.yaml`, then cut a release as usual.
2. **Emergency lever (no release needed)**: patch the running Deployment directly —

   ```bash
   kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20
   ```

   This relies on Spring Boot's relaxed env-var binding (`MYFEEDER_INTEREST_SWEEPBATCHSIZE` → `myfeeder.interest.sweep-batch-size`) and restarts the pod with the new value immediately, without a build or Helm upgrade.

**Important**: once the throttled value has been committed to `application.yaml` and deployed, remove the out-of-band env var:

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-
```

This step is not optional cleanup — Helm upgrade performs a three-way merge against the live object, and since the chart's own manifests never declared `MYFEEDER_INTEREST_SWEEPBATCHSIZE`, a later `helm upgrade` will not remove it on its own. Left in place, the stale out-of-band override silently keeps overriding the committed `application.yaml` value indefinitely.

### Emergency stop

To stop interest scoring entirely without touching the throttle, redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset in the shell running `deploy.sh`. The script defaults the key to empty, Helm writes an empty `myfeeder-typesafe-api-key` secret value, and `TypeSafeConfig`'s app-owned bean logs `TypeSafe Jev not configured; interest scoring disabled` and the sweep skips every run — the app keeps serving everything else normally.

### Monitoring Jev health

`config/JevEventLogging` logs structured, PII-free lines for every retry attempt and every breaker state transition on the `jev` Resilience4j instance. Tail them in production with:

```bash
kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '
```

Expect lines like `Jev retry attempt N after <Exception> (waiting M ms)` (INFO), `Jev retries exhausted after N attempts: <Exception>` (WARN), and `Jev circuit breaker <FROM>_TO_<TO> (failure rate x%, slow-call rate y%)` (WARN). These never include a message, body, or API key — only exception class names and numbers — so they are safe to paste into an incident channel. `GET /api/interest/status` also exposes the live breaker state (`CircuitBreakerRegistry.circuitBreaker("jev")`) for a non-log check.

## Calibration and tuning workflow

The blend/engagement constants that drive interest scores (profile points, tier thresholds, engagement open/save weights and cap) are tunable, but **never tuned live** — they are measured offline against a read-only replay of production data, then committed to source.

### Running the replay

```bash
MYFEEDER_PG_PASSWORD=... scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] [...]
```

- `PP:HIGH:NEUTRAL` are required (profile-points, high-tier threshold, neutral-tier threshold); `OPEN:SAVE:CAP` (engagement open-weight, save-weight, cap) are optional but must be given together — when omitted they default to `ENGAGEMENT_OPEN_WEIGHT`/`ENGAGEMENT_SAVE_WEIGHT`/`ENGAGEMENT_CAP` (0.25/0.5/8).
- Multiple candidate sets can be passed in one invocation; the script loops and replays each.
- It needs `MYFEEDER_PG_PASSWORD` and LAN access to `pg.bartram.org` (defaults: `PGHOST=pg.bartram.org`, `PGPORT=5432`, `PGUSER=myfeeder`, `PGDATABASE=myfeeder`); every `psql` session runs with `default_transaction_read_only=on`, so the replay cannot write to production.
- **Before connecting to Postgres**, the script validates every numeric argument and rejects any engagement-constant combination that `MyfeederProperties.Interest.Blend.Engagement.isValid` would also reject at startup (cap 0, or `0 <= open < save < 1` and `0 < cap < learned-cap`), printing `invalid engagement constants: ...` and exiting before opening a connection.
- Output is one tab-separated file per candidate, `$OUT_DIR/replay-pp<PP>-hi<HIGH>-ne<NEUTRAL>-op<OPEN>-sv<SAVE>-cap<CAP>.tsv` (default `OUT_DIR=$HOME/.cache/myfeeder-phase12/replay`), written to a `.tmp` path and renamed only after `psql` succeeds — a failed run leaves no stale file behind.

### Recording outcomes

Every calibration run's rationale and outcome gets written to a phase calibration note under `.planning/` before any yaml change is committed — for example `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md` (tier thresholds) and `.planning/phases/12-calibration-release/12-CALIBRATION.md` (engagement constants). These notes record the decision rules applied (data floor, nudge tolerance, dormant-engagement checks) and whether the defaults were kept or changed, so a later calibration run has the prior reasoning to compare against.

### Where approved values go

Approved values are committed only to yaml, never applied out of band:

- Main `application.yaml` always gets the approved values.
- `application-dev.yaml` (the `src/test/resources` overlay used by `bootTestRun`'s `dev` profile) gets the approved value **only when it differs** from the test yaml's fixed constants — currently profile-points 100, tiers high 70 / neutral 40, and engagement open/save/cap 0.25/0.5/8. (As of the last calibration, only the neutral tier threshold differs — main runs 22, so `application-dev.yaml` carries `myfeeder.interest.blend.tiers.neutral: 22`; the engagement constants currently match the test yaml exactly, so `application-dev.yaml` carries no engagement keys.)
- The test yaml itself (`src/test/resources/application.yaml`) is never changed by calibration — `InterestScoreQueriesTest`, `FeedbackApiIntegrationTest`, and `PriorityApiIntegrationTest` fixtures assume its fixed pre-calibration constants.
- Tuned values are **never** set via Helm `--set` or a Kubernetes env var override — unlike the sweep throttle levers above, these constants affect scoring math and must stay in source control so `DevProfileConfigTest` can keep enforcing that the test yaml plus the dev overlay resolve every main key to main's value.

## OpenWiki maintenance

This repository's `openwiki/` docs are regenerated by a scheduled GitHub Actions workflow (`.github/workflows/openwiki-update.yml`, weekly on Sundays at 08:00 UTC via `openwiki code --update --print`, opening a PR on branch `openwiki/update`). Do not hand-edit generated pages under `openwiki/` outside of an explicit request — prefer changing source/docs and letting the next scheduled run regenerate these pages. `openwiki/INSTRUCTIONS.md` is the user-authored brief controlling scope/priorities for this wiki; it is not itself regenerated.

## Known infrastructure gotchas (source: `CLAUDE.md`, kept current there — summarized here)

- **Docker 29 + containerd image store corrupts buildpack image exports** — pods failed to start with a wrong-diffID error. Root cause: Docker 29's containerd-backed image store mis-derives layer diffIDs on export for certain images (a known moby bug class), which strict consumers like k3s containerd reject. This is why the build moved from `bootBuildImage` (Paketo) to a plain `Dockerfile`. If you ever consider going back to buildpacks, re-read the full incident writeup in `CLAUDE.md` first — there's a documented emergency recovery procedure using `skopeo` if it recurs.
- **`pg.bartram.org` is split-horizon DNS** — only the LAN resolver (`192.168.44.6`) has the real record; public resolvers return nothing. k3s nodes must use the LAN resolver (configured via `k3s-ansible`'s `lan_dns_servers`), with a CoreDNS forward block routing `bartram.org` there. cert-manager bypasses this via `--dns01-recursive-nameservers-only` so ACME still resolves public records.
- **Helm `startupProbe` gates readiness/liveness** — `startupProbe` (5s × 60 = up to 5 min) must succeed before liveness/readiness probes run. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to the liveness probe — the startup probe is already the gate.
- **`imagePullPolicy`**: use `Always` during development (SNAPSHOT tags get reused/stale under `IfNotPresent`); `IfNotPresent` is only safe with immutable release tags.
- **`MaxDirectMemorySize` (historical, Paketo-only)**: the old buildpack hardcoded a 10M direct-memory cap that starved Netty/Lettuce (Redis client). The current Dockerfile-based image (JRE, container-aware defaults) doesn't need the override, but the Helm chart still sets `JDK_JAVA_OPTIONS=-XX:MaxDirectMemorySize=64M` for tuning — use that env var, not `_JAVA_OPTIONS`.
- **Outbound `RestClient` User-Agent**: `config/RestClientConfig.java` registers a `RestClientCustomizer` setting `myfeeder/<version>` on all outbound calls — some CDNs (e.g. Vercel) rate-limit the default JDK/Reactor Netty HTTP client user agent by returning HTTP 200 with a bogus body. Never construct a raw `RestClient.builder()` bypassing the auto-configured bean, or you'll lose this and get silently garbage responses instead of errors.
- **`./gradlew release` pushes via git CLI, not jgit** — axion-release's bundled jgit can't read OpenSSH-format keys, so `build.gradle.kts` clears the default push action and finalizes via a `gitPushRelease` Exec task calling `git push --follow-tags`. Don't replace this with raw axion auth config.
- **A stray git-lfs `pre-push` hook can block the release push** — a global `git lfs install` leaves a `pre-push` hook in `.git/hooks/` that runs `git lfs pre-push` even though this repo has no LFS content, and it can intermittently fail the push with "Remote origin does not support the Git LFS locking API" / "Unable to verify locks", blocking `gitPushRelease` (and any plain `git push`). Fix: `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false` (persistent and safe since this repo has no LFS data; re-apply after a fresh re-clone).
- **Clipboard API requires HTTPS**: the app is served over plain HTTP on the LAN, so `navigator.clipboard` is unavailable — clipboard actions fall back to `document.execCommand('copy')`.

See [Architecture Overview](../architecture/overview.md) for how the frontend build embeds into the backend jar, and [Raindrop Integration](../integrations/raindrop.md) for how the Raindrop token flows through `deploy.sh` into the Helm secret.
