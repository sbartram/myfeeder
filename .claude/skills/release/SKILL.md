---
name: release
description: Cut, build, push and deploy a myfeeder release (axion tag, bootJar, docker build/push, deploy.sh, rollout check), plus the deploy-time gotchas. Use when asked to cut/create/deploy a release or redeploy the chart.
---

# Releasing myfeeder

## Cut a release (full pipeline)

"Cut a release / create and deploy a new release" means this sequence, in this order (details on each step in the bullets below):

```bash
./gradlew release                       # 1. cut + push the release tag (axion) — BEFORE building, so the jar gets the release version
./gradlew clean bootJar                 # 2. build the jar (clean forces fresh frontend embed)
VERSION=$(./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}')
docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:$VERSION .
docker push registry.bartram.org/bartram/myfeeder:$VERSION
./deploy.sh $VERSION                    # needs MYFEEDER_PG_PASSWORD + MYFEEDER_ANTHROPIC_API_KEY (MYFEEDER_RAINDROP_API_TOKEN and MYFEEDER_TYPESAFE_API_KEY optional)
kubectl -n myfeeder rollout status deploy/myfeeder
kubectl -n myfeeder logs deploy/myfeeder --tail=20   # verify clean startup
```

For a minor release (new schema, dependency or view, as for 0.2.0) cut the tag with `./gradlew release -Prelease.versionIncrementer=incrementMinor`; the default increments the patch.

Ordering matters: `release` before `bootJar` (else the jar is stamped `-SNAPSHOT`); always pass `$VERSION` to `deploy.sh` explicitly (no arg → axion computes the *next* snapshot, which won't match any pushed image).

## Reference

- **Registry**: `registry.bartram.org/bartram/myfeeder`
- **Cluster**: k3s (`k3s-ansible` context), namespace `myfeeder`
- **Helm chart**: `helm/myfeeder/` — deploys app + Redis; Postgres is external at `pg.bartram.org`
- **Build image** (Dockerfile, not buildpacks — see Gotchas): first build the jar on the host with `./gradlew clean bootJar` (compiles + embeds the frontend via `npmBuild`→`processResources`, stamps the axion-release version into build-info), then `docker build --provenance=false -t registry.bartram.org/bartram/myfeeder:<version> .` (the `Dockerfile` only packages `build/libs/*.jar` into a JRE runtime; `--provenance=false` keeps the pushed artifact a plain single-platform image instead of a buildkit attestation index). Use `clean` so the latest frontend bundle is included; `<version>` comes from `./gradlew currentVersion -q`.
- **Push image**: `docker push registry.bartram.org/bartram/myfeeder:<version>` (build does NOT push)
- **Cut release tag**: `./gradlew release` (creates tag locally via axion-release, pushes via git CLI — see Gotchas)
- **GSD milestone tags vs release tags**: `./gradlew release` already creates `vX.Y.Z`, so a milestone named after the release it shipped in (e.g. v0.2.1) must not get a second tag from `/gsd-complete-milestone` (its tag step skips an existing tag; never move or overwrite a release tag)
- **Deploy**: `./deploy.sh [version]` (requires `MYFEEDER_PG_PASSWORD` and `MYFEEDER_ANTHROPIC_API_KEY` env vars; `MYFEEDER_RAINDROP_API_TOKEN` and `MYFEEDER_TYPESAFE_API_KEY` are optional — a blank TypeSafe key disables interest scoring and the app still starts; every key or token is passed through `helm --set`, so it must not contain `,` or `\`; no arg → axion computes the *next* snapshot version, which won't match a built image, so for chart-only redeploys against a release tag pass it explicitly: `./deploy.sh 0.1.2`)


## Deploy gotchas

- **MaxDirectMemorySize (historical, Paketo-only)**: the old Paketo buildpack hardcoded `-XX:MaxDirectMemorySize=10M`, which starved Netty (Lettuce/Redis); the Helm chart overrides it via the `JDK_JAVA_OPTIONS` env var. The Dockerfile (`eclipse-temurin:25-jre`) has no such cap — direct memory defaults are container-aware — so the override is no longer required, but the chart still sets `JDK_JAVA_OPTIONS` and the JVM honors it. Tune JVM flags via `JDK_JAVA_OPTIONS` (auto-read by the `java -jar` entrypoint), not `_JAVA_OPTIONS`.
- **Frontend not in image**: the image is `docker build`-ed from `build/libs/*.jar`, so the frontend must already be embedded in that jar. Always build with `./gradlew clean bootJar` before `docker build` — `clean` forces `npmBuild`→`processResources` to repackage the current SPA bundle. A stale jar in `build/libs/` will ship an old frontend.
- **SNAPSHOT tags + pullPolicy**: `imagePullPolicy: IfNotPresent` causes k8s to reuse stale images when the same SNAPSHOT tag is pushed. Use `Always` during development; `IfNotPresent` is only safe with immutable release tags.
- **Gradle terminal escapes in scripts**: `./gradlew currentVersion -q` outputs terminal control sequences. In shell scripts, pipe through `grep 'Project version'` before parsing to avoid contaminating variables.
- **`./gradlew release` push uses git CLI**: axion-release's bundled jgit can't read OpenSSH-format keys. `release` and `pushRelease` in `build.gradle.kts` clear their built-in push action and finalize via a `gitPushRelease` Exec task. Don't replace this with raw axion auth config.
- **`./gradlew release` tag push can fail on a stray git-lfs pre-push hook**: a global `git lfs install` leaves a `pre-push` hook in `.git/hooks/` that runs `git lfs pre-push` even though this repo has **no** LFS content. It intermittently fails the push with "Remote origin does not support the Git LFS locking API" / "Unable to verify locks", blocking `gitPushRelease` (and plain `git push`). Fix (persistent, safe — no LFS data here): `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false`. Already set in this clone; re-apply after a fresh re-clone.
- **Helm probes have a startupProbe**: `startupProbe` (5s × 60 = up to 5 min) gates `livenessProbe` and `readinessProbe`. Configurable in `values.yaml` under `app.probes.{startup,readiness,liveness}`. Don't add `initialDelaySeconds` to liveness — startupProbe is the gate.
- **`pg.bartram.org` is split-horizon DNS**: only the LAN resolver (`192.168.44.6`/pi1) has the `192.168.44.206` record; public resolvers (1.1.1.1, Cloudflare) return no answer. The k3s nodes must use `192.168.44.6` as a resolver (configured in `k3s-ansible` via the `lan_dns_servers` var on the `prereq` role) and a CoreDNS forward block in the `coredns-custom` ConfigMap routes `bartram.org` queries there. cert-manager bypasses cluster DNS entirely via `--dns01-recursive-nameservers-only` so ACME challenges still resolve public records on Cloudflare.
- **Docker 29's containerd image store corrupts buildpack image exports** (pods fail to start with containerd `failed to pull and unpack … wrong diff id "sha256:…" calculated on extraction "sha256:…"`): this is why the build moved off `bootBuildImage` to a `Dockerfile` (see Deployment). Root cause: Docker 29's `dockerd` defaults to the **containerd-backed image store** (`docker info` shows `Storage Driver: overlayfs` + `driver-type io.containerd.snapshotter.v1`), and that store **mis-derives diffIDs when *exporting* certain images** — a known moby bug class ([moby/moby#47150](https://github.com/moby/moby/issues/47150), `docker save` produces wrong diffIDs). For a Paketo `bootBuildImage` image the exported **manifest** listed the wrong blob (the 3 KB `os-release` layer) in the base-layer slots while the config's `rootfs.diff_ids` stayed correct; strict consumers (k3s containerd) re-verify each layer against its diffID and refuse the image. Key scoping facts: it is **not** universal — plain `docker build` images (e.g. the `budget` project on the same engine) export fine; it only bites the **export → strict-pull** path, so the image still **runs locally** (`docker run` uses the daemon's internal store and never re-derives diffIDs) and survives a Docker-only `push`/`pull` (moby's puller is lenient). It reproduces via both `docker push` and `bootBuildImage publish=true`. Fixes, in order of preference: (1) **build via `Dockerfile`** (adopted — immune regardless of image store); (2) set `{"features":{"containerd-snapshotter":false}}` in the docker daemon config to revert to the `overlay2` graphdriver store, if Docker still allows opting out; (3) build with Docker ≤ 28. Emergency stopgap (used once for `0.1.16`): `skopeo copy docker://<image> dir:/tmp/img`, recover the correct base-layer bytes from the run image (`docker save <run-image>`, gunzip layers whose uncompressed sha256 matches the config `diff_ids`), patch the bad manifest slots' `digest`+`size`, re-verify every layer, then `skopeo copy dir:/tmp/img docker://<image>`.
