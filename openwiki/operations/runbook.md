---
type: Runbook
title: myfeeder Operations Runbook
description: Practical build, run, release, and deployment guidance for myfeeder — local dev commands, the Docker/Helm/k3s deployment pipeline, required and optional secrets/env vars (including the optional TypeSafe Jev API key), interest-scoring throttle/emergency-stop levers, the calibration workflow, and known infrastructure gotchas that have caused real incidents.
resource: build.gradle.kts
tags: [operations, deployment, docker, helm, kubernetes, runbook]
verified:
  - by: openwiki/0.7.0
    at: 2026-10-04T13:52:44.431Z
sources:
  - id: openwiki-source-6d4b4e707b8d60b6ccfa3425
    resource: repo://.github/workflows/openwiki-update.yml
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
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
generated: { by: "openwiki/0.7.0", at: "2026-10-04T13:52:44.431Z" }
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

`./gradlew bootTestRun` loads `src/test/resources/application.yaml` (shadowing main) plus the `dev` profile overlay `application-dev.yaml`, which restores the real Jev base URL and key placeholder — export `MYFEEDER_TYPESAFE_API_KEY` first if you want interest scoring active locally. `./gradlew bootRun` loads main `application.yaml` directly.

## Release and deploy pipeline

"Cut a release" means, in this exact order:

```bash
./gradlew release                       # 1. cut + push release tag (axion) — BEFORE building
./gradlew clean bootJar                 # 2. build the jar (clean forces fresh frontend embed)
VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')
docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:$VERSION .
docker push registry.bartram.org/bartram/myfeeder:$VERSION
./deploy.sh $VERSION                    # needs MYFEEDER_PG_PASSWORD + MYFEEDER_ANTHROPIC_API_KEY (MYFEEDER_RAINDROP_API_TOKEN and MYFEEDER_TYPESAFE_API_KEY optional)
kubectl -n myfeeder rollout status deploy/myfeeder
kubectl -n myfeeder logs deploy/myfeeder --tail=20
```

Ordering matters: `release` **before** `bootJar`, or the jar gets stamped `-SNAPSHOT`. Always pass `$VERSION` explicitly to `deploy.sh` — omitting it makes axion compute the *next* snapshot version, which won't match any pushed image.

For a **minor** release (new schema, new dependency, or new/changed view — e.g. the 0.2.0 and 0.3.0 cuts), tag it with `./gradlew release -Prelease.versionIncrementer=incrementMinor`; the plugin's default increments the patch component. Never move or re-tag an existing release tag.

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys the app + Redis; Postgres is external at `pg.bartram.org`
- **Image build**: Dockerfile-based (`eclipse-temurin:25-jre` runtime), not Paketo buildpacks — see the Docker 29 gotcha below for why. `docker build` only packages `build/libs/*.jar`; the jar must already contain the embedded frontend (built via `./gradlew clean bootJar`, which chains `npmBuild` → `processResources`).
- **Stack**: Spring Boot 4.0.8 on a Java 25 toolchain, Spring AI 2.0.1 (BOM), Spring Cloud 2025.1.3 (BOM); the Jev client comes from `org.springaicommunity:spring-ai-starter-typesafe:0.1.0`, pinned explicitly because it sits outside any BOM.
- **Secrets required for deploy** (`deploy.sh` passes every one through `helm --set`, so **none may contain `,` or `\`** — a comma breaks Helm's value-list parsing and a backslash is an escape character):
  - `MYFEEDER_PG_PASSWORD` — **required**.
  - `MYFEEDER_ANTHROPIC_API_KEY` — **required**.
  - `MYFEEDER_RAINDROP_API_TOKEN` — **optional**; if unset, `deploy.sh` prints a warning and Raindrop stays disabled. See [Raindrop Integration](../integrations/raindrop.md).
  - `MYFEEDER_TYPESAFE_API_KEY` — **optional**; if unset, `deploy.sh` prints a warning and interest scoring (Jev) stays disabled — the app still starts normally, since `TypeSafeConfig` owns the client bean and only logs `TypeSafe Jev not configured; interest scoring disabled` on a blank key. See [Jev Integration](../integrations/jev.md).

## Interest scoring operations

See [Interest Scoring Workflow](../workflows/interest-scoring.md) for the ranking math (blend, tiers, engagement terms) that the levers and tuning below affect. The scoring sweep (`InterestScoringSweep`) enqueues up to `sweep-batch-size` eligible, unread articles every `sweep-delay`, skipping runs while Jev is unconfigured, in cold start, or while the `jev` circuit breaker is OPEN/FORCED_OPEN.

### Throttle levers

- **Committed change** (survives redeploys): lower `myfeeder.interest.sweep-batch-size` (default 50) and/or lengthen `myfeeder.interest.sweep-delay` (default `PT2M`) in `src/main/resources/application.yaml`, then release and deploy as usual.
- **Emergency override** (no release): patch the running Deployment directly —
  ```bash
  kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20
  ```
  This restarts the pod with a smaller batch size immediately. Once the tuned value has landed in `application.yaml` via a normal release, **remove the override**:
  ```bash
  kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-
  ```
  This step is required, not optional: a Helm `upgrade`'s three-way merge only reconciles values it manages, so an out-of-band `kubectl set env` variable can survive subsequent `helm upgrade` runs and silently keep overriding the committed yaml value.

### Emergency full stop

To stop interest scoring entirely without touching the sweep logic, redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset (e.g. `unset MYFEEDER_TYPESAFE_API_KEY` before re-running `./deploy.sh`). A blank key is the same code path as "never configured" — `TypeSafeConfig` backs off cleanly and the app keeps serving everything else.

### Observability

Jev retry attempts, retry exhaustion, and circuit-breaker state transitions are logged as single-line, PII-free messages by `JevEventLogging` (class names and numbers only — never a message, body, or key). Grep them in production:

```bash
kubectl -n myfeeder logs deploy/myfeeder | grep 'Jev '
```

### Tier and engagement constant tuning (calibration)

Approved tier (`myfeeder.interest.blend.tiers.high|neutral`) and engagement (`myfeeder.interest.blend.engagement.open-weight|save-weight|cap`) constants are **tuned by replay, not by live re-scoring**:

1. Run `scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] ...` for each candidate constant set. It opens a read-only `psql` session (`default_transaction_read_only=on`) against production and replays the verbatim blend/engagement SQL from `InterestScoreQueries` (drift-guarded by `InterestCalibrationReplaySqlTest`) over real data — no Jev calls, no writes, no re-score. It needs `MYFEEDER_PG_PASSWORD` and LAN access to `pg.bartram.org`, and validates every candidate (format and `Engagement.isValid` constraints) before connecting.
2. Each candidate writes one `replay-pp<PP>-hi<HIGH>-ne<NEUTRAL>-op<OPEN>-sv<SAVE>-cap<CAP>.tsv` (default under `$HOME/.cache/myfeeder-phase12/replay`), written to a `.tmp` file and renamed only on success, so a failed run leaves no stale output.
3. Compare candidates against the decision rules recorded for the relevant phase (e.g. a data floor of 30 engaged scored articles and 3+ non-negative topics before trusting a change, and a nudge limit on how far the high-tier share may move) and record the outcome in a dated calibration note.
4. Put the **approved values only in committed yaml**, never via Helm `--set` or a `kubectl set env` override:
   - Main `src/main/resources/application.yaml` always gets the new values.
   - `src/test/resources/application-dev.yaml` gets them too, but **only where they differ** from the frozen defaults the test yaml (`src/test/resources/application.yaml`) holds for test fixtures (profile-points 100, tiers 70/40, engagement 0.25/0.5/8) — those test-yaml values must never be changed, since several integration tests assume them.

This separation exists because the blend and engagement constants are read at query time (no re-score needed to take effect), but they must stay reproducible for tests and auditable in version control — unlike the sweep throttle, they are never acceptable as a live Helm/env override.

## OpenWiki maintenance

This repository's `openwiki/` docs are regenerated by a scheduled GitHub Actions workflow (`.github/workflows/openwiki-update.yml`, weekly at 08:00 UTC on Sundays via `openwiki code --update --print`, opening a PR on branch `openwiki/update`). Do not hand-edit generated pages under `openwiki/` outside of an explicit request — prefer changing source/docs and letting the next scheduled run regenerate these pages. `openwiki/INSTRUCTIONS.md` is the user-authored brief controlling scope/priorities for this wiki; it is not itself regenerated.

## Known infrastructure gotchas (source: `CLAUDE.md`, kept current there — summarized here)

- **Docker 29 + containerd image store corrupts buildpack image exports** — pods failed to start with a wrong-diffID error. Root cause: Docker 29's containerd-backed image store mis-derives layer diffIDs on export for certain images (a known moby bug class), which strict consumers like k3s containerd reject. This is why the build moved from `bootBuildImage` (Paketo) to a plain `Dockerfile`. If you ever consider going back to buildpacks, re-read the full incident writeup in `CLAUDE.md` first — there's a documented emergency recovery procedure using `skopeo` if it recurs.
- **`pg.bartram.org` is split-horizon DNS** — only the LAN resolver (`192.168.44.6`) has the real record; public resolvers return nothing. k3s nodes must use the LAN resolver (configured via `k3s-ansible`'s `lan_dns_servers`), with a CoreDNS forward block routing `bartram.org` there. cert-manager bypasses this via `--dns01-recursive-nameservers-only` so ACME still resolves public records.
- **Helm `startupProbe` gates readiness/liveness** — `startupProbe` (5s × 60 = up to 5 min) must succeed before liveness/readiness probes run. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to the liveness probe — the startup probe is already the gate.
- **`imagePullPolicy`**: use `Always` during development (SNAPSHOT tags get reused/stale under `IfNotPresent`); `IfNotPresent` is only safe with immutable release tags.
- **`MaxDirectMemorySize` (historical, Paketo-only)**: the old buildpack hardcoded a 10M direct-memory cap that starved Netty/Lettuce (Redis client). The current Dockerfile-based image (JRE, container-aware defaults) doesn't strictly need the override, but the Helm chart still sets it (`JDK_JAVA_OPTIONS=-XX:MaxDirectMemorySize=64M` in `app-deployment.yaml`) for tuning — use that env var, not `_JAVA_OPTIONS`.
- **Outbound `RestClient` User-Agent**: `config/RestClientConfig.java` registers a `RestClientCustomizer` setting `myfeeder/<version>` on all outbound calls — some CDNs (e.g. Vercel) rate-limit the default HTTP client user agent by returning HTTP 200 with a bogus body. Never construct a raw `RestClient.builder()` bypassing the auto-configured bean, or you'll lose this and get silently garbage responses instead of errors.
- **`./gradlew release` pushes via git CLI, not jgit** — axion-release's bundled jgit can't read OpenSSH-format keys, so `build.gradle.kts` clears the default push action and finalizes via a `gitPushRelease` Exec task calling `git push --follow-tags`. Don't replace this with raw axion auth config.
- **`./gradlew release` tag push can fail on a stray git-lfs pre-push hook** — a global `git lfs install` leaves a `pre-push` hook in `.git/hooks/` that runs `git lfs pre-push` even though this repo has **no** LFS content. It intermittently fails the push with "Remote origin does not support the Git LFS locking API" / "Unable to verify locks", blocking `gitPushRelease` (and plain `git push`). Fix (persistent, safe — no LFS data here): `git config lfs.<remote-url>.locksverify false` (e.g. `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false`); re-apply after a fresh clone. Never use `--no-verify` for other push-hook failures.
- **Clipboard API requires HTTPS**: the app is served over plain HTTP on the LAN, so `navigator.clipboard` is unavailable — clipboard actions fall back to `document.execCommand('copy')`.

See [Architecture Overview](../architecture/overview.md) for how the frontend build embeds into the backend jar, and [Raindrop Integration](../integrations/raindrop.md) for how the Raindrop token flows through `deploy.sh` into the Helm secret.
