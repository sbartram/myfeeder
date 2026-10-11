---
type: Runbook
title: myfeeder Operations Runbook
description: Practical build, run, release, and deployment guidance for myfeeder — local dev commands, the Docker/Helm/k3s deployment pipeline, required secrets/env vars (including Jev/TypeSafe), the interest-scoring throttle levers, the engagement calibration replay tool, and known infrastructure gotchas that have caused real incidents.
resource: build.gradle.kts
tags: [operations, deployment, docker, helm, kubernetes, runbook, calibration]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-11T14:50:52.613Z
sources:
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-828d24ee9ccc8738afe410fb
    resource: repo://deploy.sh
  - id: openwiki-source-445c26dc79d49d991b20176d
    resource: repo://scripts/interest-calibration-replay.sh
  - id: openwiki-source-e10d497b6eb67a010f44960a
    resource: repo://src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
generated: { by: "openwiki/0.7.2", at: "2026-10-11T14:50:52.613Z" }
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

Docker must be running for both `./gradlew test` and `./gradlew bootRun`. `compose.yaml` defines Postgres + Redis for local dev; `TestcontainersConfiguration` (in `src/test/java/...`) provides the same for tests and `bootTestRun`. `bootTestRun` loads the test-classpath `application.yaml` with the `dev` profile active (`TestMyfeederApplication`), so it talks to the real Jev base URL — export `MYFEEDER_TYPESAFE_API_KEY` first if you want interest scoring to run locally.

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

**Minor releases**: for a release that adds a schema migration, a new external dependency, or a new database view (as for 0.2.0 and 0.3.0), cut the tag with `./gradlew release -Prelease.versionIncrementer=incrementMinor` instead of plain `./gradlew release` — the default increments only the patch version. Never pass `-Prelease.disableChecks`, and never move or re-tag an existing release tag.

**GSD milestone tags vs. release tags**: `./gradlew release` already creates an annotated `vX.Y.Z` tag. A GSD milestone that is named after the release it shipped in (e.g. a milestone called `v0.2.1`) must **not** get a second tag from `/gsd-complete-milestone` — that command's tag step skips creation when the tag already exists, and the release tag must never be moved or overwritten to "fix" a milestone boundary.

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys the app + Redis; Postgres is external at `pg.bartram.org`
- **Image build**: Dockerfile-based (`eclipse-temurin:25-jre` runtime), not Paketo buildpacks — see the Docker 29 gotcha below for why. `docker build` only packages `build/libs/*.jar`; the jar must already contain the embedded frontend (built via `./gradlew clean bootJar`, which chains `npmBuild` → `processResources`).
- **Secrets for `deploy.sh`**:
  - **Required**: `MYFEEDER_PG_PASSWORD`, `MYFEEDER_ANTHROPIC_API_KEY` — `deploy.sh` has no fallback for either; both are passed straight into `helm upgrade --set`.
  - **Optional**: `MYFEEDER_RAINDROP_API_TOKEN` — if unset, `deploy.sh` prints a warning and Raindrop stays disabled (see [Raindrop Integration](../integrations/raindrop.md)). `MYFEEDER_TYPESAFE_API_KEY` — if unset, `deploy.sh` prints a warning; `TypeSafeConfig`'s bean still builds, `isConfigured()` returns false, and the app starts normally with interest scoring (Jev) disabled rather than crashing (see [Jev Scoring Integration](../integrations/jev-scoring.md)).
  - **Constraint**: every secret/token value flows through `helm upgrade --set secrets.<key>="$VALUE"`, so none of them may contain a comma (`,`) or a backslash (`\`) — both have special meaning to Helm's `--set` parser and will corrupt the rendered values (or the whole `--set` argument list) silently.
  - Both optional secrets default to an empty string in `deploy.sh` (via `${VAR:-}`) so `set -u` doesn't abort the script when they're unset.

## Interest-scoring throttle and emergency levers

Two config properties under `myfeeder.interest` in `application.yaml` bound how fast myfeeder calls Jev: **`sweep-batch-size`** (default 50 — the max number of eligible articles `InterestScoringSweep` enqueues per run) and **`sweep-delay`** (default `PT2M` — how often the sweep runs). If 429s or circuit-breaker trips become a recurring problem, lower `sweep-batch-size` and/or lengthen `sweep-delay` in `application.yaml` and redeploy through the normal release pipeline above.

For an immediate fix with no release, use Spring's relaxed environment-variable binding to override the running Deployment directly:

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20
```

This restarts the pod right away (`kubectl set env` always rolls the Deployment). **Once the chosen value has been committed into `application.yaml` and deployed normally, remove the out-of-band override explicitly**:

```bash
kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE-
```

This matters because a later `helm upgrade`'s three-way merge does not necessarily clear an env var that was set out-of-band via `kubectl set env` but is absent from the chart's rendered manifest — a stale override can silently survive the upgrade if it isn't removed first.

**Emergency stop**: redeploy with `MYFEEDER_TYPESAFE_API_KEY` unset. A blank key makes the `TypeSafeClient` bean build successfully but `isConfigured()` return false, so both the sweep and on-ingest scoring paths no-op without crashing the app — the same optional-secret behavior `deploy.sh` documents above.

Keep `concurrency: 1` and never re-enable the SDK-level retry layer as a throttling measure — both are structural decisions, not tuning knobs. See [Jev Scoring Integration](../integrations/jev-scoring.md) for the full breaker/retry design and the executor internals behind these levers.

## Engagement/interest calibration replay

`scripts/interest-calibration-replay.sh` replays the Priority blend's interest/engagement scoring math against production data to evaluate candidate tuning constants, **without writing anything**: every `psql` session runs with `default_transaction_read_only=on`, and connection parameters come only from env vars and CLI arguments.

```bash
scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] [...]
```

- `PP` — profile points; `HIGH`/`NEUTRAL` — tier badge thresholds; `OPEN`/`SAVE`/`CAP` — engagement weights/cap (given together or omitted entirely, in which case they default from `ENGAGEMENT_OPEN_WEIGHT`/`ENGAGEMENT_SAVE_WEIGHT`/`ENGAGEMENT_CAP`).
- **Requires `MYFEEDER_PG_PASSWORD`** and LAN access to `pg.bartram.org` (the production database host, behind split-horizon DNS — see the gotcha below); the script refuses to run without the password.
- Before connecting, every candidate is validated against the same rule `MyfeederProperties.Interest.Blend.Engagement.isValid` enforces at app startup (cap `0`, or `0 <= open < save < 1` and `0 < cap < learned-cap`), so an invalid candidate set fails fast with `invalid engagement constants` rather than wasting a database round trip.
- Output: one tab-separated file per candidate under `$OUT_DIR` (default `$HOME/.cache/myfeeder-phase12/replay`), written to a `.tmp` path and renamed only after `psql` succeeds, so a failed run leaves no stale file behind.
- The replay reports several sections beyond the blend summary, including a **data floor** check (decision rule **D-02**: at least 30 engaged SCORED unvoted articles and at least 3 non-negative topics with measurable engagement; below the floor, the correct action is to wait rather than tune on thin data) and a **nudge rule** (decision rule **D-04**: keep the shipped defaults unless a candidate's high-tier share differs from the cap-0 baseline by more than 5 points, and never pin a topic at the engagement cap unless it has reached the confirmed article-count threshold).

Tuned values go into main `application.yaml` and `src/test/resources/application-dev.yaml` (never into Helm `--set` or a Kubernetes env override — calibration is a committed config change, not a runtime lever), while the test `application.yaml` keeps its fixed constants because several integration tests assert against them. See [Engagement and Suggestions Workflow](../workflows/engagement-and-suggestions.md) for why these constants exist and how the learned model uses them, and the project's phase calibration notes (e.g. `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md`) for a worked example of recording a replay outcome.

## OpenWiki maintenance

This repository's `openwiki/` docs are regenerated by a scheduled GitHub Actions workflow (`.github/workflows/openwiki-update.yml`, daily at 08:00 UTC via `openwiki code --update --print`, opening a PR on branch `openwiki/update`). Do not hand-edit generated pages under `openwiki/` outside of an explicit request — prefer changing source/docs and letting the next scheduled run regenerate these pages. `openwiki/INSTRUCTIONS.md` is the user-authored brief controlling scope/priorities for this wiki; it is not itself regenerated.

Shared infrastructure facts that span repositories (registry host, `pg.bartram.org`, k3s node layout, load-balancer IPs, and other deploy conventions) live in `HOMELAB.md`, referenced from this repo's `CLAUDE.md` as `@../HOMELAB.md` — a sibling file outside this repository, not duplicated here. Treat it as the source of truth for infra facts that are not myfeeder-specific.

## Known infrastructure gotchas (source: `CLAUDE.md`, kept current there — summarized here)

- **Docker 29 + containerd image store corrupts buildpack image exports** — pods failed to start with a wrong-diffID error. Root cause: Docker 29's containerd-backed image store mis-derives layer diffIDs on export for certain images (a known moby bug class), which strict consumers like k3s containerd reject. This is why the build moved from `bootBuildImage` (Paketo) to a plain `Dockerfile`. If you ever consider going back to buildpacks, re-read the full incident writeup in `CLAUDE.md` first — there's a documented emergency recovery procedure using `skopeo` if it recurs.
- **`pg.bartram.org` is split-horizon DNS** — only the LAN resolver (`192.168.44.6`) has the real record; public resolvers return nothing. k3s nodes must use the LAN resolver (configured via `k3s-ansible`'s `lan_dns_servers`), with a CoreDNS forward block routing `bartram.org` there. cert-manager bypasses this via `--dns01-recursive-nameservers-only` so ACME still resolves public records. This is also why the calibration replay above needs LAN access, not just a password.
- **Helm `startupProbe` gates readiness/liveness** — `startupProbe` (5s × 60 = up to 5 min) must succeed before liveness/readiness probes run. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to the liveness probe — the startup probe is already the gate.
- **`imagePullPolicy`**: use `Always` during development (SNAPSHOT tags get reused/stale under `IfNotPresent`); `IfNotPresent` is only safe with immutable release tags.
- **`MaxDirectMemorySize` (historical, Paketo-only)**: the old buildpack hardcoded a 10M direct-memory cap that starved Netty/Lettuce (Redis client). The current Dockerfile-based image (JRE, container-aware defaults) doesn't need the override, but the Helm chart still sets `JDK_JAVA_OPTIONS` for tuning — use that env var, not `_JAVA_OPTIONS`.
- **Outbound `RestClient` User-Agent**: `config/RestClientConfig.java` registers a `RestClientCustomizer` setting `myfeeder/<version>` on all outbound calls — some CDNs (e.g. Vercel) rate-limit the default JDK HttpClient user agent by returning HTTP 200 with a bogus body. Never construct a raw `RestClient.builder()` bypassing the auto-configured bean, or you'll lose this and get silently garbage responses instead of errors.
- **`./gradlew release` pushes via git CLI, not jgit** — axion-release's bundled jgit can't read OpenSSH-format keys, so `build.gradle.kts` clears the default push action and finalizes via a `gitPushRelease` Exec task calling `git push --follow-tags`. Don't replace this with raw axion auth config.
- **`./gradlew release` tag push can fail on a stray git-lfs pre-push hook** — a global `git lfs install` leaves a `pre-push` hook that runs `git lfs pre-push` even though this repo has no LFS content, intermittently failing the push with "Unable to verify locks" and blocking `gitPushRelease` (and plain `git push`). The persistent, safe fix (no LFS data here) is `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false`; re-apply after a fresh clone if it recurs.
- **Clipboard API requires HTTPS**: the app is served over plain HTTP on the LAN, so `navigator.clipboard` is unavailable — clipboard actions fall back to `document.execCommand('copy')`.

See [Architecture Overview](../architecture/overview.md) for how the frontend build embeds into the backend jar, and [Raindrop Integration](../integrations/raindrop.md) for how the Raindrop token flows through `deploy.sh` into the Helm secret.
