# MetalLB Annotation Migration (External Services) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Portable plan.** Copy this file and its `.tasks.json` into ONE service's repo, at the same relative path `docs/superpowers/plans/`, because `planPath` in the tasks file is relative. Run it there. It covers exactly one row of the Service table below per run. The operator runs it six times, once per repo.

**Goal:** Replace the deprecated `metallb.universe.tf/loadBalancerIPs` annotation with `metallb.io/loadBalancerIPs` in one service's deployment source, and on its live Service, without changing its LoadBalancer IP.

**Architecture:** Each service is a Helm release whose Service carries a MetalLB IP-pinning annotation. You change the key in the repo (values file or chart template) and verify it with `helm template`. Then you switch the live Service in place with one `kubectl annotate` that adds the new key and removes the old one in the same patch. This avoids a redeploy. The next `helm upgrade` from the updated source then changes nothing, because Helm's three-way merge already sees the new key and no old key.

**Tech Stack:** K3s v1.36.4, MetalLB v0.14.9 (L2 mode, pool `192.168.44.200-220`), Helm 3, kubectl context `k3s-ansible`.

**Spec:** Origin is the `gitops-deploy` repo: `docs/backlog.md`, entry "MetalLB `metallb.universe.tf/*` annotations still used outside this repo". The same migration was done for the gitops-deploy components on 2026-09-29 (merge `cd4d25b`). It used this exact method with no IP changes.

## Service table (pick the row for this repo)

Captured live on 2026-09-29. The executor must re-check the row in Task 1, Step 1 before changing anything.

| Namespace | Service | Helm release | Chart (deployed) | LB IP | Port | Reachability check |
|---|---|---|---|---|---|---|
| `blink` | `blink-jobs` | `blink-jobs` | `blink-jobs-0.1.0` (own chart) | `192.168.44.209` | 80 | `curl -s -o /dev/null -w '%{http_code}' http://192.168.44.209/` |
| `budget` | `budget` | `budget` | `budget-0.1.0` (own chart) | `192.168.44.213` | 80 | `curl -s -o /dev/null -w '%{http_code}' http://192.168.44.213/` |
| `docker-registry` | `docker-registry` | `docker-registry` | `docker-registry-3.0.0` (upstream twuni chart, `service.annotations` in values) | `192.168.44.205` | 443 | `curl -sk -o /dev/null -w '%{http_code}' https://192.168.44.205/v2/` (200 or 401 = reachable) |
| `myfeeder` | `myfeeder` | `myfeeder` | `myfeeder-0.1.1` (own chart) | `192.168.44.204` | 80 | `curl -s -o /dev/null -w '%{http_code}' http://192.168.44.204/` |
| `vidq` | `vidq-ui` | `vidq-ui` | `vidq-ui-0.2.0` (own chart) | `192.168.44.210` | 80 | `curl -s -o /dev/null -w '%{http_code}' http://192.168.44.210/` |
| `vidtag` | `vidtag` | `vidtag` | `vidtag-0.1.0` (own chart) | `192.168.44.211` | 80 | `curl -s -o /dev/null -w '%{http_code}' http://192.168.44.211/` |

In the steps below, `$NS`, `$SVC`, `$RELEASE` and `$IP` are that row's Namespace, Service, Helm release and LB IP. "The row's reachability check" is its last column. Baseline codes on 2026-09-29: 200 everywhere except **vidtag, whose root returns 500** (a pre-existing app issue, out of scope). What counts is that the code doesn't change. Export them once at the start:

```bash
export NS=<namespace> SVC=<service> RELEASE=<release> IP=<lb-ip>
```

## Global Constraints

- **The IP must not change.** The new annotation value is exactly the row's LB IP. After every step, `kubectl get svc -n $NS $SVC -o jsonpath='{.status.loadBalancer.ingress[0].ip}'` must still print `$IP`.
- New key: `metallb.io/loadBalancerIPs`. The old key `metallb.universe.tf/loadBalancerIPs` must not remain in the repo's deployment source or on the live Service. None of these six services uses `metallb.universe.tf/allow-shared-ip`. If you find it, migrate it to `metallb.io/allow-shared-ip` with the same value in the same patch.
- **Live change:** use only `kubectl annotate` (add the new key and remove the old one in ONE command). Do NOT run `helm upgrade` or the repo's deploy pipeline to apply this change. A redeploy can pull a newer image or chart and is out of scope.
- The kubectl context must be `k3s-ansible` (`kubectl config current-context`).
- Leave historical documents (old plans, specs, changelogs) as written. Only change files that feed the deployment: values files, chart templates, raw manifests.
- Git: work on branch `chore/metallb-annotation`, merge to the default branch with `--no-ff`, and push only when the operator asks.

**User decisions (already made):**
- Migrate to `metallb.io/*` keys (done in gitops-deploy on 2026-09-29, same method).
- Each of the six services is processed separately, in its own repo, one at a time.

---

### Task 1: Change the annotation key in the repo

**Goal:** The repo's deployment source renders `metallb.io/loadBalancerIPs: <IP>` for `$SVC` and contains no `metallb.universe.tf/` key.

**Files:**
- Modify: whichever file sets the annotation. Usually `values.yaml` (`service.annotations`) or `chart/templates/service.yaml`, and possibly an environment values file such as `values-prod.yaml`. Step 2 finds them.

**Acceptance Criteria:**
- [ ] The live row still matches the table (IP, release, and the old key is present) before any edit
- [ ] `grep -rn 'metallb.universe.tf/'` over the deployment source (excluding historical docs) returns nothing
- [ ] `helm template` of the release, rendered with the same values files the deploy uses, prints `metallb.io/loadBalancerIPs` with value `$IP` on the `$SVC` Service
- [ ] The change is committed on `chore/metallb-annotation`

**Verify:** `helm template $RELEASE <chart-path> -n $NS -f <values files the deploy uses> | grep -n 'metallb\.'` → only `metallb.io/loadBalancerIPs: ...$IP` lines

**Steps:**

- [ ] **Step 1: Confirm the live state matches the table**

```bash
kubectl config current-context        # expect: k3s-ansible
kubectl get svc -n $NS $SVC -o jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}{.metadata.annotations}{"\n"}'
helm list -n $NS
```

Expected: the IP equals `$IP`, the annotations include `metallb.universe.tf/loadBalancerIPs`, and the release `$RELEASE` is listed. If the IP differs, or the old key is already gone, STOP and report: someone changed the service since 2026-09-29.

- [ ] **Step 2: Find every place the old key is set**

```bash
git checkout -b chore/metallb-annotation
grep -rn 'metallb.universe.tf/' . --include='*.yaml' --include='*.yml' --include='*.tpl' --include='*.json' | grep -v '^./.git/'
```

Also work out how the release is deployed: look for a `deploy.sh`, `Makefile`, `Jenkinsfile`, `.gitea/workflows/` or `helmfile.yaml`. Note which chart path and which `-f` values files it passes. You need them for Step 4. If the annotation isn't in this repo at all, it may be set by a `--set` flag in the pipeline. Search the pipeline file for `loadBalancerIPs` and change it there.

- [ ] **Step 3: Replace the key**

Change only the key, and keep the value exactly as it is:

```bash
# BSD sed (macOS); on Linux drop the '' after -i
grep -rl 'metallb.universe.tf/' <files from step 2> | xargs sed -i '' \
  -e 's#metallb\.universe\.tf/loadBalancerIPs#metallb.io/loadBalancerIPs#g' \
  -e 's#metallb\.universe\.tf/allow-shared-ip#metallb.io/allow-shared-ip#g'
```

Leave historical docs (`docs/plans`, changelogs) alone. Only change the files that feed the deployment.

- [ ] **Step 4: Render and check**

```bash
grep -rn 'metallb.universe.tf/' <deployment files>           # expect: no output
helm template $RELEASE <chart-path> -n $NS -f <values files> | grep -n 'metallb\.'
```

Expected: one `metallb.io/loadBalancerIPs: <$IP>` line (quoted or unquoted) on the `$SVC` Service, and no `metallb.universe.tf` line. For docker-registry (upstream chart, no local chart dir), add the repo and render at the deployed version:
`helm repo add twuni https://helm.twun.io && helm template docker-registry twuni/docker-registry --version 3.0.0 -n docker-registry -f <values file>`.

- [ ] **Step 5: Commit**

```bash
git add <changed files>
git commit -m "chore: migrate to metallb.io/loadBalancerIPs annotation

metallb.universe.tf/loadBalancerIPs is deprecated in MetalLB v0.14.9.
Same IP ($IP), key rename only."
```

---

### Task 2: Switch the live Service in place

**Goal:** The live `$SVC` Service carries only `metallb.io/loadBalancerIPs=$IP`, keeps `$IP`, and still answers.

**Files:** none (cluster state only).

**Acceptance Criteria:**
- [ ] `kubectl get svc -n $NS $SVC -o jsonpath='{.status.loadBalancer.ingress[0].ip}'` prints `$IP`, both straight after the patch and 30 seconds later
- [ ] The Service annotations contain `metallb.io/loadBalancerIPs` and no key starting with `metallb.universe.tf/`
- [ ] The row's reachability check returns the same HTTP code as the baseline recorded in Step 1 (don't expect 200 everywhere: vidtag's root returned 500 on 2026-09-29)
- [ ] No `deprecatedAnnotation` event for `$NS` has a `lastTimestamp` later than the patch time

**Verify:** `kubectl get svc -n $NS $SVC -o jsonpath='{.status.loadBalancer.ingress[0].ip} {.metadata.annotations}'` → `$IP` and only `metallb.io/*` keys

**Steps:**

- [ ] **Step 1: Record the baseline**

```bash
date -u +%Y-%m-%dT%H:%M:%SZ        # patch time, used to check events in step 4
<row's reachability check>          # note the HTTP code
```

- [ ] **Step 2: Patch (add the new key and remove the old one in ONE command)**

```bash
kubectl annotate svc -n $NS $SVC \
  metallb.io/loadBalancerIPs=$IP \
  metallb.universe.tf/loadBalancerIPs-
```

Doing both in one command means MetalLB never sees the Service without an IP-pinning key, so it keeps the allocation.

- [ ] **Step 3: Check the IP now and after 30 seconds**

```bash
kubectl get svc -n $NS $SVC -o jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}{.metadata.annotations}{"\n"}'
sleep 30
kubectl get svc -n $NS $SVC -o jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}'
<row's reachability check>
```

Expected: `$IP` both times, no `metallb.universe.tf/` key, and the same HTTP code as the baseline.

**If the IP changed or disappeared, roll back immediately:**

```bash
kubectl annotate svc -n $NS $SVC metallb.universe.tf/loadBalancerIPs=$IP metallb.io/loadBalancerIPs-
```

Then STOP and report the MetalLB controller log: `kubectl logs -n metallb-system deploy/metallb-controller --tail=50`.

- [ ] **Step 4: Check that no new deprecation event appeared**

```bash
kubectl get events -n $NS --field-selector reason=deprecatedAnnotation \
  -o custom-columns=LAST:.lastTimestamp,MSG:.message --no-headers
```

Expected: nothing, or only events with `LAST` earlier than the patch time from Step 1. Kubernetes keeps old events for about an hour, so an old timestamp is not a failure.

---

### Task 3: Merge and record

**Goal:** The key change is on the repo's default branch, and the gitops-deploy backlog shows this service as done.

**Files:**
- Modify (in the **gitops-deploy** repo, not this one): `docs/backlog.md`, the entry "MetalLB `metallb.universe.tf/*` annotations still used outside this repo"

**Acceptance Criteria:**
- [ ] `chore/metallb-annotation` is merged to the default branch with `--no-ff`, and the branch is deleted
- [ ] In gitops-deploy `docs/backlog.md`, `$SVC` is removed from the entry's list of services. If it was the last one, the whole entry is deleted.
- [ ] Nothing is pushed unless the operator asked

**Verify:** `git log --oneline --merges -1` → the merge commit; `grep -n "$SVC" <gitops-deploy>/docs/backlog.md` → no match in the MetalLB entry

**Steps:**

- [ ] **Step 1: Merge**

```bash
git checkout <default-branch>
git merge --no-ff chore/metallb-annotation -m "Merge branch 'chore/metallb-annotation'"
git branch -d chore/metallb-annotation
```

- [ ] **Step 2: Update the gitops-deploy backlog**

In `/Volumes/data2/scottb/dev/bartram/gitops-deploy/docs/backlog.md`, delete `$SVC` from the list "blink-jobs, budget, docker-registry, myfeeder, vidq-ui, vidtag". When the list becomes empty, delete the whole entry, from its `##` heading up to the next `##`. Commit it on gitops-deploy `main`; the CLAUDE.md rules there allow docs-only commits directly on main:

```bash
git -C /Volumes/data2/scottb/dev/bartram/gitops-deploy commit -am "docs(backlog): $SVC migrated to metallb.io annotation"
```

- [ ] **Step 3: Push only if asked.** Report the merge SHA, the before and after IP (identical), and the reachability HTTP code.
