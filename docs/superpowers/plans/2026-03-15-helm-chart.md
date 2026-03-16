# Helm Chart Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a self-contained Helm chart deploying myfeeder + PostgreSQL + Redis to a local k8s cluster with MetalLB.

**Architecture:** Single Helm chart at `helm/myfeeder/` with inline templates for all three workloads (no subcharts). Secrets via gitignored `values-secrets.yaml`. MetalLB LoadBalancer for external access, NFS-backed PVC for Postgres, emptyDir for Redis.

**Tech Stack:** Helm 3, Kubernetes, MetalLB, PostgreSQL 16, Redis 7, Spring Boot 4.0.3

**Spec:** `docs/superpowers/specs/2026-03-15-helm-chart-design.md`

---

## Chunk 1: Chart Scaffold, Helpers, and Values

### Task 1: Create Chart.yaml, values.yaml, and .helmignore

**Files:**
- Create: `helm/myfeeder/Chart.yaml`
- Create: `helm/myfeeder/values.yaml`
- Create: `helm/myfeeder/.helmignore`

- [ ] **Step 1: Create Chart.yaml**

```yaml
apiVersion: v2
name: myfeeder
description: A Helm chart for myfeeder — RSS/Atom/JSON Feed reader
type: application
version: 0.1.0
appVersion: "0.0.1-SNAPSHOT"
```

- [ ] **Step 2: Create values.yaml**

```yaml
nameOverride: ""
fullnameOverride: ""

app:
  image:
    repository: gitea.bartram.org:3000/bartram/myfeeder
    tag: latest
    pullPolicy: Always
  imagePullSecrets: []
  service:
    type: LoadBalancer
    port: 8080
    annotations:
      metallb.universe.tf/loadBalancerIPs: "192.168.44.203"
  resources: {}
  replicaCount: 1

postgres:
  image:
    repository: postgres
    tag: "16"
  database: myfeeder
  username: myfeeder
  storage:
    storageClass: nfs-client
    size: 5Gi

redis:
  image:
    repository: redis
    tag: "7"

spring:
  profiles: ""

secrets:
  postgresPassword: ""
  anthropicApiKey: ""
  googleApplicationCredentials: ""
```

- [ ] **Step 3: Create .helmignore**

```
values-secrets.yaml
.git
.gitignore
```

- [ ] **Step 4: Validate chart scaffolding**

Run: `helm lint helm/myfeeder`
Expected: chart lints with warnings (templates missing is OK at this stage)

- [ ] **Step 5: Commit**

```bash
git add helm/myfeeder/Chart.yaml helm/myfeeder/values.yaml helm/myfeeder/.helmignore
git commit -m "feat(helm): scaffold chart with Chart.yaml, values, and helmignore"
```

### Task 2: Create _helpers.tpl

**Files:**
- Create: `helm/myfeeder/templates/_helpers.tpl`

- [ ] **Step 1: Create _helpers.tpl with standard helpers**

```gotemplate
{{/*
Expand the name of the chart.
*/}}
{{- define "myfeeder.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "myfeeder.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "myfeeder.labels" -}}
helm.sh/chart: {{ include "myfeeder.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
App selector labels
*/}}
{{- define "myfeeder.appSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: app
{{- end }}

{{/*
Postgres selector labels
*/}}
{{- define "myfeeder.postgresSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: postgres
{{- end }}

{{/*
Redis selector labels
*/}}
{{- define "myfeeder.redisSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: redis
{{- end }}
```

- [ ] **Step 2: Commit**

```bash
git add helm/myfeeder/templates/_helpers.tpl
git commit -m "feat(helm): add _helpers.tpl with labels and naming helpers"
```

### Task 3: Add gitignore entry for values-secrets.yaml

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Append to .gitignore**

Add this line to the end of `.gitignore`:

```
helm/myfeeder/values-secrets.yaml
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: gitignore helm values-secrets.yaml"
```

---

## Chunk 2: PostgreSQL Templates

### Task 4: Create postgres-pvc.yaml

**Files:**
- Create: `helm/myfeeder/templates/postgres-pvc.yaml`

- [ ] **Step 1: Create postgres-pvc.yaml**

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: {{ include "myfeeder.fullname" . }}-postgres
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.postgresSelectorLabels" . | nindent 4 }}
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: {{ .Values.postgres.storage.storageClass }}
  resources:
    requests:
      storage: {{ .Values.postgres.storage.size }}
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/postgres-pvc.yaml`
Expected: Valid PVC YAML with `nfs-client` storageClass and `5Gi` size

- [ ] **Step 3: Commit**

```bash
git add helm/myfeeder/templates/postgres-pvc.yaml
git commit -m "feat(helm): add postgres PVC template"
```

### Task 5: Create postgres-deployment.yaml

**Files:**
- Create: `helm/myfeeder/templates/postgres-deployment.yaml`

- [ ] **Step 1: Create postgres-deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "myfeeder.fullname" . }}-postgres
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.postgresSelectorLabels" . | nindent 4 }}
spec:
  replicas: 1
  selector:
    matchLabels:
      {{- include "myfeeder.postgresSelectorLabels" . | nindent 6 }}
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        {{- include "myfeeder.labels" . | nindent 8 }}
        {{- include "myfeeder.postgresSelectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: postgres
          image: "{{ .Values.postgres.image.repository }}:{{ .Values.postgres.image.tag }}"
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: {{ .Values.postgres.database }}
            - name: POSTGRES_USER
              value: {{ .Values.postgres.username }}
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "myfeeder.fullname" . }}-secret
                  key: postgres-password
          readinessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - {{ .Values.postgres.username }}
            periodSeconds: 5
          volumeMounts:
            - name: postgres-data
              mountPath: /var/lib/postgresql/data
              subPath: pgdata
      volumes:
        - name: postgres-data
          persistentVolumeClaim:
            claimName: {{ include "myfeeder.fullname" . }}-postgres
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/postgres-deployment.yaml`
Expected: Valid Deployment YAML with correct image, env vars referencing secret, subPath mount

- [ ] **Step 3: Commit**

```bash
git add helm/myfeeder/templates/postgres-deployment.yaml
git commit -m "feat(helm): add postgres Deployment template"
```

### Task 6: Create postgres-service.yaml

**Files:**
- Create: `helm/myfeeder/templates/postgres-service.yaml`

- [ ] **Step 1: Create postgres-service.yaml**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "myfeeder.fullname" . }}-postgres
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.postgresSelectorLabels" . | nindent 4 }}
spec:
  type: ClusterIP
  ports:
    - port: 5432
      targetPort: 5432
      protocol: TCP
  selector:
    {{- include "myfeeder.postgresSelectorLabels" . | nindent 4 }}
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/postgres-service.yaml`
Expected: Valid ClusterIP Service on port 5432

- [ ] **Step 3: Commit**

```bash
git add helm/myfeeder/templates/postgres-service.yaml
git commit -m "feat(helm): add postgres Service template"
```

---

## Chunk 3: Redis Templates

### Task 7: Create redis-deployment.yaml

**Files:**
- Create: `helm/myfeeder/templates/redis-deployment.yaml`

- [ ] **Step 1: Create redis-deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "myfeeder.fullname" . }}-redis
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.redisSelectorLabels" . | nindent 4 }}
spec:
  replicas: 1
  selector:
    matchLabels:
      {{- include "myfeeder.redisSelectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "myfeeder.labels" . | nindent 8 }}
        {{- include "myfeeder.redisSelectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: redis
          image: "{{ .Values.redis.image.repository }}:{{ .Values.redis.image.tag }}"
          ports:
            - containerPort: 6379
          readinessProbe:
            exec:
              command:
                - redis-cli
                - ping
            periodSeconds: 5
          volumeMounts:
            - name: redis-data
              mountPath: /data
      volumes:
        - name: redis-data
          emptyDir: {}
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/redis-deployment.yaml`
Expected: Valid Deployment YAML with emptyDir volume, no PVC

- [ ] **Step 3: Commit**

```bash
git add helm/myfeeder/templates/redis-deployment.yaml
git commit -m "feat(helm): add redis Deployment template"
```

### Task 8: Create redis-service.yaml

**Files:**
- Create: `helm/myfeeder/templates/redis-service.yaml`

- [ ] **Step 1: Create redis-service.yaml**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "myfeeder.fullname" . }}-redis
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.redisSelectorLabels" . | nindent 4 }}
spec:
  type: ClusterIP
  ports:
    - port: 6379
      targetPort: 6379
      protocol: TCP
  selector:
    {{- include "myfeeder.redisSelectorLabels" . | nindent 4 }}
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/redis-service.yaml`
Expected: Valid ClusterIP Service on port 6379

- [ ] **Step 3: Commit**

```bash
git add helm/myfeeder/templates/redis-service.yaml
git commit -m "feat(helm): add redis Service template"
```

---

## Chunk 4: App Secret and ConfigMap

### Task 9: Create app-secret.yaml

**Files:**
- Create: `helm/myfeeder/templates/app-secret.yaml`

- [ ] **Step 1: Create app-secret.yaml**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "myfeeder.fullname" . }}-secret
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
type: Opaque
stringData:
  postgres-password: {{ .Values.secrets.postgresPassword | quote }}
  spring-datasource-username: {{ .Values.postgres.username | quote }}
  spring-datasource-password: {{ .Values.secrets.postgresPassword | quote }}
  spring-ai-anthropic-api-key: {{ .Values.secrets.anthropicApiKey | quote }}
{{- if .Values.secrets.googleApplicationCredentials }}
data:
  gcp-credentials.json: {{ .Values.secrets.googleApplicationCredentials | quote }}
{{- end }}
```

- [ ] **Step 2: Validate template renders without GCP credentials**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-secret.yaml --set secrets.postgresPassword=testpw --set secrets.anthropicApiKey=sk-test`
Expected: Valid Secret with stringData keys, no `data` section

- [ ] **Step 3: Validate template renders with GCP credentials**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-secret.yaml --set secrets.postgresPassword=testpw --set secrets.anthropicApiKey=sk-test --set secrets.googleApplicationCredentials=eyJ0ZXN0IjoidmFsdWUifQ==`
Expected: Valid Secret with both `stringData` and `data` sections, `gcp-credentials.json` key present

- [ ] **Step 4: Commit**

```bash
git add helm/myfeeder/templates/app-secret.yaml
git commit -m "feat(helm): add app Secret template with optional GCP credentials"
```

### Task 10: Create app-configmap.yaml

**Files:**
- Create: `helm/myfeeder/templates/app-configmap.yaml`

- [ ] **Step 1: Create app-configmap.yaml**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "myfeeder.fullname" . }}-config
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
data:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://{{ include "myfeeder.fullname" . }}-postgres:5432/{{ .Values.postgres.database }}"
  SPRING_DATA_REDIS_HOST: "{{ include "myfeeder.fullname" . }}-redis"
  SPRING_DATA_REDIS_PORT: "6379"
  {{- if .Values.spring.profiles }}
  SPRING_PROFILES_ACTIVE: {{ .Values.spring.profiles | quote }}
  {{- end }}
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-configmap.yaml`
Expected: ConfigMap with datasource URL pointing to `myfeeder-postgres:5432/myfeeder`, redis host `myfeeder-redis`, no SPRING_PROFILES_ACTIVE (empty default)

- [ ] **Step 3: Validate with spring profiles set**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-configmap.yaml --set spring.profiles=prod`
Expected: ConfigMap includes `SPRING_PROFILES_ACTIVE: "prod"`

- [ ] **Step 4: Commit**

```bash
git add helm/myfeeder/templates/app-configmap.yaml
git commit -m "feat(helm): add app ConfigMap template"
```

---

## Chunk 5: App Deployment and Service

### Task 11: Create app-deployment.yaml

**Files:**
- Create: `helm/myfeeder/templates/app-deployment.yaml`

- [ ] **Step 1: Create app-deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "myfeeder.fullname" . }}
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.appSelectorLabels" . | nindent 4 }}
spec:
  replicas: {{ .Values.app.replicaCount }}
  selector:
    matchLabels:
      {{- include "myfeeder.appSelectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "myfeeder.labels" . | nindent 8 }}
        {{- include "myfeeder.appSelectorLabels" . | nindent 8 }}
    spec:
      {{- with .Values.app.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
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
      containers:
        - name: myfeeder
          image: "{{ .Values.app.image.repository }}:{{ .Values.app.image.tag }}"
          imagePullPolicy: {{ .Values.app.image.pullPolicy }}
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: {{ include "myfeeder.fullname" . }}-config
          env:
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: {{ include "myfeeder.fullname" . }}-secret
                  key: spring-datasource-username
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "myfeeder.fullname" . }}-secret
                  key: spring-datasource-password
            - name: SPRING_AI_ANTHROPIC_API_KEY
              valueFrom:
                secretKeyRef:
                  name: {{ include "myfeeder.fullname" . }}-secret
                  key: spring-ai-anthropic-api-key
            {{- if .Values.secrets.googleApplicationCredentials }}
            - name: GOOGLE_APPLICATION_CREDENTIALS
              value: /var/secrets/gcp/gcp-credentials.json
            {{- end }}
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
            failureThreshold: 5
          {{- with .Values.app.resources }}
          resources:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- if .Values.secrets.googleApplicationCredentials }}
          volumeMounts:
            - name: gcp-credentials
              mountPath: /var/secrets/gcp
              readOnly: true
          {{- end }}
      {{- if .Values.secrets.googleApplicationCredentials }}
      volumes:
        - name: gcp-credentials
          secret:
            secretName: {{ include "myfeeder.fullname" . }}-secret
            items:
              - key: gcp-credentials.json
                path: gcp-credentials.json
      {{- end }}
```

- [ ] **Step 2: Validate template renders without GCP**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-deployment.yaml --set secrets.postgresPassword=testpw`
Expected: Valid Deployment with init container, envFrom configMap, env from secret, probes, no volumes/volumeMounts

- [ ] **Step 3: Validate template renders with GCP**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-deployment.yaml --set secrets.postgresPassword=testpw --set secrets.googleApplicationCredentials=eyJ0ZXN0IjoidmFsdWUifQ==`
Expected: Same as above plus GOOGLE_APPLICATION_CREDENTIALS env var, volumeMount, and volume

- [ ] **Step 4: Commit**

```bash
git add helm/myfeeder/templates/app-deployment.yaml
git commit -m "feat(helm): add app Deployment with init container and health probes"
```

### Task 12: Create app-service.yaml

**Files:**
- Create: `helm/myfeeder/templates/app-service.yaml`

- [ ] **Step 1: Create app-service.yaml**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "myfeeder.fullname" . }}
  labels:
    {{- include "myfeeder.labels" . | nindent 4 }}
    {{- include "myfeeder.appSelectorLabels" . | nindent 4 }}
  {{- with .Values.app.service.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  type: {{ .Values.app.service.type }}
  ports:
    - port: {{ .Values.app.service.port }}
      targetPort: 8080
      protocol: TCP
  selector:
    {{- include "myfeeder.appSelectorLabels" . | nindent 4 }}
```

- [ ] **Step 2: Validate template renders**

Run: `helm template myfeeder helm/myfeeder --show-only templates/app-service.yaml`
Expected: Valid LoadBalancer Service with MetalLB annotation `192.168.44.203`, port 8080

- [ ] **Step 3: Commit**

```bash
git add helm/myfeeder/templates/app-service.yaml
git commit -m "feat(helm): add app Service template with MetalLB annotations"
```

---

## Chunk 6: Full Chart Validation

### Task 13: Lint and validate the complete chart

- [ ] **Step 1: Run helm lint**

Run: `helm lint helm/myfeeder`
Expected: No errors. Warnings about missing secrets values are acceptable.

- [ ] **Step 2: Run full template render with test values**

Run: `helm template myfeeder helm/myfeeder --set secrets.postgresPassword=testpw --set secrets.anthropicApiKey=sk-test`
Expected: All templates render without errors. Inspect output for:
- Postgres Deployment references PVC and secret
- Redis Deployment uses emptyDir
- App Deployment has init container, envFrom configmap, env from secret, health probes
- App Service has MetalLB annotation
- Secret contains all expected keys
- ConfigMap has correct datasource URL and redis host

- [ ] **Step 3: Verify label consistency**

Check that all resources share common labels from `myfeeder.labels`, and that selector labels are scoped per component (`app`, `postgres`, `redis`).

- [ ] **Step 4: Final commit**

```bash
git add -A helm/myfeeder/
git commit -m "feat(helm): complete myfeeder Helm chart for local k8s deployment"
```

- [ ] **Step 5: Push**

```bash
git push
```
