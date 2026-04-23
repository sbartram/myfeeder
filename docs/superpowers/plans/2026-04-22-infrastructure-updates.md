# Infrastructure Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the in-cluster Postgres deployment from the Helm chart (use external `pg.bartram.org` instead) and update frontend libraries to latest minor/patch versions.

**Architecture:** Two independent infrastructure changes. The Helm change deletes 3 template files, modifies 4 others, and updates values.yaml. The frontend change is an `npm update` within existing semver ranges, verified by tests and build.

**Tech Stack:** Helm 3 templates, npm/Node.js, Vite, Vitest

---

## File Structure

### Helm chart changes
- **Delete:** `helm/myfeeder/templates/postgres-deployment.yaml`
- **Delete:** `helm/myfeeder/templates/postgres-service.yaml`
- **Delete:** `helm/myfeeder/templates/postgres-pvc.yaml`
- **Modify:** `helm/myfeeder/values.yaml` — replace `postgres` section with `externalPostgres`
- **Modify:** `helm/myfeeder/templates/_helpers.tpl` — remove `postgresSelectorLabels`
- **Modify:** `helm/myfeeder/templates/app-configmap.yaml` — update JDBC URL
- **Modify:** `helm/myfeeder/templates/app-secret.yaml` — update username reference
- **Modify:** `helm/myfeeder/templates/app-deployment.yaml` — remove init container

### Frontend changes
- **Modify:** `src/main/frontend/package.json` — updated version ranges (via npm update)
- **Modify:** `src/main/frontend/package-lock.json` — updated lockfile (via npm update)

---

### Task 1: Delete Postgres template files

**Files:**
- Delete: `helm/myfeeder/templates/postgres-deployment.yaml`
- Delete: `helm/myfeeder/templates/postgres-service.yaml`
- Delete: `helm/myfeeder/templates/postgres-pvc.yaml`

- [ ] **Step 1: Delete the three Postgres template files**

```bash
cd /Users/scottb/dev/bartram/myfeeder
git rm helm/myfeeder/templates/postgres-deployment.yaml
git rm helm/myfeeder/templates/postgres-service.yaml
git rm helm/myfeeder/templates/postgres-pvc.yaml
```

- [ ] **Step 2: Commit**

```bash
git add -A helm/myfeeder/templates/postgres-*.yaml
git commit -m "chore(helm): remove in-cluster postgres templates

Postgres will be provided by external server pg.bartram.org instead
of an in-cluster deployment."
```

---

### Task 2: Remove postgresSelectorLabels from helpers

**Files:**
- Modify: `helm/myfeeder/templates/_helpers.tpl`

- [ ] **Step 1: Remove the postgresSelectorLabels template**

In `helm/myfeeder/templates/_helpers.tpl`, delete the entire `myfeeder.postgresSelectorLabels` block (lines 44-48):

```yaml
{{/*
Postgres selector labels
*/}}
{{- define "myfeeder.postgresSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: postgres
{{- end }}
```

The file should retain `myfeeder.name`, `myfeeder.fullname`, `myfeeder.labels`, `myfeeder.appSelectorLabels`, and `myfeeder.redisSelectorLabels`.

- [ ] **Step 2: Commit**

```bash
git add helm/myfeeder/templates/_helpers.tpl
git commit -m "chore(helm): remove postgresSelectorLabels helper"
```

---

### Task 3: Update values.yaml for external Postgres

**Files:**
- Modify: `helm/myfeeder/values.yaml`

- [ ] **Step 1: Replace the postgres section with externalPostgres**

In `helm/myfeeder/values.yaml`, replace the entire `postgres:` block:

```yaml
postgres:
  image:
    repository: postgres
    tag: "16"
  database: myfeeder
  username: myfeeder
  storage:
    storageClass: nfs-client
    size: 5Gi
```

With:

```yaml
externalPostgres:
  host: pg.bartram.org
  port: 5432
  database: myfeeder
  username: myfeeder
```

Leave all other sections (`app`, `redis`, `spring`, `secrets`) unchanged.

- [ ] **Step 2: Commit**

```bash
git add helm/myfeeder/values.yaml
git commit -m "chore(helm): replace postgres values with externalPostgres config

Point at pg.bartram.org instead of deploying an in-cluster Postgres."
```

---

### Task 4: Update app-configmap to use external Postgres host

**Files:**
- Modify: `helm/myfeeder/templates/app-configmap.yaml`

- [ ] **Step 1: Update the JDBC URL**

In `helm/myfeeder/templates/app-configmap.yaml`, change line 8 from:

```yaml
  SPRING_DATASOURCE_URL: "jdbc:postgresql://{{ include "myfeeder.fullname" . }}-postgres:5432/{{ .Values.postgres.database }}"
```

To:

```yaml
  SPRING_DATASOURCE_URL: "jdbc:postgresql://{{ .Values.externalPostgres.host }}:{{ .Values.externalPostgres.port }}/{{ .Values.externalPostgres.database }}"
```

- [ ] **Step 2: Commit**

```bash
git add helm/myfeeder/templates/app-configmap.yaml
git commit -m "chore(helm): point JDBC URL at external Postgres host"
```

---

### Task 5: Update app-secret to use externalPostgres username

**Files:**
- Modify: `helm/myfeeder/templates/app-secret.yaml`

- [ ] **Step 1: Update the username reference**

In `helm/myfeeder/templates/app-secret.yaml`, change line 11 from:

```yaml
  spring-datasource-username: {{ .Values.postgres.username | quote }}
```

To:

```yaml
  spring-datasource-username: {{ .Values.externalPostgres.username | quote }}
```

- [ ] **Step 2: Commit**

```bash
git add helm/myfeeder/templates/app-secret.yaml
git commit -m "chore(helm): update secret to reference externalPostgres username"
```

---

### Task 6: Remove wait-for-postgres init container from app deployment

**Files:**
- Modify: `helm/myfeeder/templates/app-deployment.yaml`

- [ ] **Step 1: Remove the initContainers block**

In `helm/myfeeder/templates/app-deployment.yaml`, delete the entire `initContainers` block (lines 23-33):

```yaml
      initContainers:
        - name: wait-for-postgres
          image: "{{ .Values.postgres.image.repository }}:{{ .Values.postgres.image.tag }}"
          command:
            - sh
            - -c
            - |
              until pg_isready -h {{ include "myfeeder.fullname" . }}-postgres -p 5432 -U {{ .Values.postgres.username }}; do
                echo "Waiting for postgres..."
                sleep 2
              done
```

The `containers` section (starting with `containers:`) should follow directly after the `spec:` / `imagePullSecrets` block.

- [ ] **Step 2: Commit**

```bash
git add helm/myfeeder/templates/app-deployment.yaml
git commit -m "chore(helm): remove wait-for-postgres init container

No longer needed since Postgres is external. The app's readiness
probe handles startup health checking."
```

---

### Task 7: Verify Helm chart renders correctly

- [ ] **Step 1: Run helm template to validate rendered output**

```bash
cd /Users/scottb/dev/bartram/myfeeder
helm template test-release helm/myfeeder \
  --set secrets.postgresPassword=testpass \
  --set secrets.anthropicApiKey=testkey
```

Expected: Valid YAML output containing:
- `app-configmap` with `SPRING_DATASOURCE_URL: "jdbc:postgresql://pg.bartram.org:5432/myfeeder"`
- `app-secret` with `spring-datasource-username: "myfeeder"`
- `app-deployment` with NO `initContainers` section
- NO postgres-deployment, postgres-service, or postgres-pvc resources
- Redis deployment and service still present

- [ ] **Step 2: Verify no references to old postgres values remain**

```bash
grep -r "\.Values\.postgres\." helm/myfeeder/templates/
```

Expected: No output (no remaining references to the old `.Values.postgres` path).

---

### Task 8: Update frontend libraries

**Files:**
- Modify: `src/main/frontend/package.json`
- Modify: `src/main/frontend/package-lock.json`

- [ ] **Step 1: Run npm update**

```bash
cd /Users/scottb/dev/bartram/myfeeder/src/main/frontend
npm update
```

This updates all dependencies to the latest version within their existing `^` semver ranges.

- [ ] **Step 2: Check what changed**

```bash
cd /Users/scottb/dev/bartram/myfeeder
git diff src/main/frontend/package.json
git diff --stat src/main/frontend/package-lock.json
```

Review the output to confirm only minor/patch version bumps — no unexpected major version changes.

- [ ] **Step 3: Run frontend tests**

```bash
cd /Users/scottb/dev/bartram/myfeeder/src/main/frontend
npm test
```

Expected: All tests pass.

- [ ] **Step 4: Run frontend build**

```bash
cd /Users/scottb/dev/bartram/myfeeder/src/main/frontend
npm run build
```

Expected: Build succeeds with no errors.

- [ ] **Step 5: Commit**

```bash
cd /Users/scottb/dev/bartram/myfeeder
git add src/main/frontend/package.json src/main/frontend/package-lock.json
git commit -m "chore(frontend): update libraries to latest minor/patch versions"
```

---

### Task 9: Update TODO.md

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Mark completed tasks**

In `TODO.md`, change:
```
- [ ] do not deploy postgres in the helm chart - use pg.bartram.org
- [ ] update the frontend libraries
```

To:
```
- [X] do not deploy postgres in the helm chart - use pg.bartram.org
- [X] update the frontend libraries
```

- [ ] **Step 2: Commit**

```bash
git add TODO.md
git commit -m "docs: mark infrastructure tasks as complete in TODO"
```
