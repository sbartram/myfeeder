# Infrastructure Updates Design

## Overview

Two infrastructure tasks: (1) remove the in-cluster Postgres deployment from the Helm chart and point at an external Postgres server at `pg.bartram.org`, and (2) update frontend libraries to latest minor/patch versions.

## Task 1: External Postgres

### Context

The Helm chart currently deploys its own Postgres pod (postgres:16) with a PVC on `nfs-client` storage. The app connects via an in-cluster service (`<release>-myfeeder-postgres:5432`). The goal is to use an existing external Postgres server at `pg.bartram.org` instead. The database and user will be created manually on the external server.

### Changes

**Delete files:**
- `helm/myfeeder/templates/postgres-deployment.yaml`
- `helm/myfeeder/templates/postgres-service.yaml`
- `helm/myfeeder/templates/postgres-pvc.yaml`

**Modify `helm/myfeeder/templates/_helpers.tpl`:**
- Remove the `myfeeder.postgresSelectorLabels` template definition

**Modify `helm/myfeeder/values.yaml`:**
- Remove the `postgres` section (image, database, username, storage)
- Add `externalPostgres` section:
  ```yaml
  externalPostgres:
    host: pg.bartram.org
    port: 5432
    database: myfeeder
    username: myfeeder
  ```

**Modify `helm/myfeeder/templates/app-configmap.yaml`:**
- Change JDBC URL from in-cluster service reference to external host:
  ```
  jdbc:postgresql://{{ .Values.externalPostgres.host }}:{{ .Values.externalPostgres.port }}/{{ .Values.externalPostgres.database }}
  ```

**Modify `helm/myfeeder/templates/app-secret.yaml`:**
- Update username reference from `postgres.username` to `externalPostgres.username`

**Modify `helm/myfeeder/templates/app-deployment.yaml`:**
- Remove the `wait-for-postgres` init container entirely (it relied on the postgres image and `pg_isready` against the in-cluster pod; the app's readiness probe handles startup health)

### What stays the same
- Redis deployment (unchanged)
- App deployment structure (minus init container)
- Secret structure for API keys, GCP credentials
- Password still provided via `secrets.postgresPassword` in values

## Task 2: Frontend Library Updates

### Context

Frontend dependencies use caret (`^`) ranges and are fairly current. The goal is conservative: pick up latest minor/patch versions within existing major version ranges.

### Changes

- Run `npm update` in `src/main/frontend/`
- Verify with `npm test` (Vitest + React Testing Library)
- Verify with `npm run build` (TypeScript + Vite)
- Commit updated `package.json` and `package-lock.json`

### Scope

Stay within current major versions only. No major version bumps.

## Verification

- `helm template` the chart to verify rendered output is valid
- Frontend tests pass after update
- Frontend build succeeds after update
