# Helm Chart Design Spec — myfeeder

## Overview

A self-contained Helm chart to deploy the myfeeder Spring Boot application along with PostgreSQL and Redis into a local Kubernetes cluster. All three workloads run in the same namespace. The chart uses MetalLB for external access and NFS-backed persistent storage, matching the existing gitops-deploy patterns.

## Decisions

- **Approach**: Single custom chart with inline Postgres/Redis templates (no Bitnami subcharts). Keeps things simple and fully visible for a local deployment.
- **Secrets**: Gitignored `values-secrets.yaml` file passed at install time. No external secret managers.
- **Networking**: MetalLB LoadBalancer service for the app; ClusterIP for Postgres and Redis (internal only).
- **Storage**: PVCs with `nfs-client` storageClass for both Postgres (5Gi) and Redis (1Gi).
- **Image build**: Spring Boot `bootBuildImage` (Cloud Native Buildpacks), pushed to `gitea.bartram.org:3000`.
- **No Dockerfile**: Not needed — `bootBuildImage` handles it.

## Chart Structure

```
helm/myfeeder/
├── Chart.yaml
├── values.yaml                # defaults (non-secret, committed)
├── values-secrets.yaml        # gitignored, user-created
├── templates/
│   ├── _helpers.tpl           # common labels, fullname, etc.
│   ├── app-deployment.yaml
│   ├── app-service.yaml
│   ├── app-configmap.yaml
│   ├── app-secret.yaml
│   ├── postgres-deployment.yaml
│   ├── postgres-service.yaml
│   ├── postgres-pvc.yaml
│   ├── redis-deployment.yaml
│   ├── redis-service.yaml
│   └── redis-pvc.yaml
└── .helmignore
```

## Values Schema

### `values.yaml` (committed defaults)

```yaml
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
  storage:
    storageClass: nfs-client
    size: 1Gi

spring:
  profiles: ""

secrets:
  postgresPassword: ""
  anthropicApiKey: ""
  vertexAiProjectId: ""
  vertexAiLocation: "us-central1"
  raindropApiToken: ""
```

### `values-secrets.yaml` (gitignored, user-created)

```yaml
secrets:
  postgresPassword: "actual-password"
  anthropicApiKey: "sk-ant-..."
  vertexAiProjectId: "my-gcp-project"
  raindropApiToken: "..."
```

### Frequently updated values (not hardcoded in templates)

- `app.image.tag` — updated on each build/deploy
- `app.service.annotations` — MetalLB IP
- `postgres.image.tag`, `redis.image.tag` — infrastructure version bumps
- `postgres.storage.size`, `redis.storage.size` — capacity changes
- All `secrets.*` values

## Workload Details

### App Deployment

- Single-replica Deployment
- **ConfigMap** provides non-secret environment variables:
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://<release>-postgres:5432/<db>`
  - `SPRING_DATA_REDIS_HOST=<release>-redis`
  - `SPRING_PROFILES_ACTIVE` (if set)
  - `MYFEEDER_RAINDROP_API_BASE_URL=https://api.raindrop.io/rest/v1`
- **Secret** provides credentials as env vars:
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_AI_ANTHROPIC_API_KEY`
  - `SPRING_AI_VERTEX_AI_EMBEDDING_PROJECT_ID`
  - `SPRING_AI_VERTEX_AI_EMBEDDING_LOCATION`
  - `MYFEEDER_RAINDROP_API_TOKEN` (if the app uses one)
- **Init container**: `postgres` image running `pg_isready` loop — blocks until Postgres is accepting connections, preventing Flyway migration failures on first deploy.
- **Readiness probe**: HTTP GET `/actuator/health`, `initialDelaySeconds: 30`, `periodSeconds: 10`
- **Liveness probe**: HTTP GET `/actuator/health`, `initialDelaySeconds: 60`, `periodSeconds: 30`, `failureThreshold: 5`
- **Service**: LoadBalancer (MetalLB), port 8080

### PostgreSQL Deployment

- Single-replica Deployment, official `postgres` image
- Environment from Secret: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- **PVC**: `nfs-client` storageClass, 5Gi, mounted at `/var/lib/postgresql/data`
- **Subpath mount**: `pgdata` subpath to avoid lost+found conflicts
- **Readiness probe**: `exec pg_isready -U <user>`, `periodSeconds: 5`
- **Service**: ClusterIP, port 5432

### Redis Deployment

- Single-replica Deployment, official `redis` image
- No authentication (internal-only, local cluster)
- **PVC**: `nfs-client`, 1Gi, mounted at `/data`
- **Readiness probe**: `exec redis-cli ping`, `periodSeconds: 5`
- **Service**: ClusterIP, port 6379

## Startup Order

1. Postgres and Redis Deployments start independently
2. App Deployment's init container runs `pg_isready` loop, waiting for Postgres
3. Once Postgres is ready, main app container starts, Flyway runs migrations, app boots
4. Redis reconnects automatically via Spring's cache abstraction if it's not ready yet

## Image Build & Deploy Workflow

```bash
# Build image (no Dockerfile needed)
./gradlew bootBuildImage --imageName=gitea.bartram.org:3000/bartram/myfeeder:0.1.0

# Push to registry
docker push gitea.bartram.org:3000/bartram/myfeeder:0.1.0

# First install
helm install myfeeder ./helm/myfeeder \
  -f ./helm/myfeeder/values-secrets.yaml \
  -n myfeeder --create-namespace

# Upgrade with new image tag
helm upgrade myfeeder ./helm/myfeeder \
  -f ./helm/myfeeder/values-secrets.yaml \
  -n myfeeder \
  --set app.image.tag=0.1.0
```

### Image pull secret (one-time setup)

```bash
kubectl create secret docker-registry gitea-registry \
  --docker-server=gitea.bartram.org:3000 \
  --docker-username=<user> --docker-password=<token> \
  -n myfeeder
```

Then set in values:

```yaml
app:
  imagePullSecrets:
    - name: gitea-registry
```

## .gitignore Addition

```
helm/myfeeder/values-secrets.yaml
```

## What This Design Does NOT Include

- Ingress (MetalLB LoadBalancer is sufficient)
- Horizontal Pod Autoscaling (single local instance)
- Network policies (local trusted cluster)
- TLS termination (local HTTP access)
- Backup/restore for PostgreSQL (out of scope for local dev)
