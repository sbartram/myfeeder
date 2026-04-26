# Raindrop Collection Picker

**Date:** 2026-04-25
**Status:** Approved for planning

## Problem

The Raindrop integration settings dialog requires the user to enter a numeric `collectionId`. Raindrop's web UI does not surface this ID, so users have no practical way to discover it. The integration is effectively unusable as a result, even with a valid API token configured.

Additionally, the API token is currently stored per-user in the database as plaintext JSON, which is a weaker security posture than necessary for a single-tenant deployment.

## Goal

Make the Raindrop integration usable end-to-end:

1. Move the Raindrop API token out of user-facing settings and into deployment-time configuration (helm secret), parallel to `secrets.anthropicApiKey`.
2. Replace the numeric collection ID input with a dropdown of the user's Raindrop collections, sorted alphabetically by title.
3. Auto-disable the integration when no token is configured at deployment, with a clear "not configured" notice in the settings dialog.

## Non-goals

- Per-user Raindrop tokens (explicitly rejected — single deployment token).
- Tag picker at save time, collection creation from the app, or "already bookmarked?" indicators in the reading pane. These are sensible follow-ups but each warrants its own design.
- Encryption-at-rest for the token. It is no longer persisted by the application.
- Nested / child collections in Raindrop. Only root-level collections from `/collections` are listed.

## Architecture

### Component layout

Follows vidtag's split between HTTP client and service:

- **`MyfeederProperties.Raindrop`** — gains a new `apiToken` field (default `""`).
- **`RaindropApiClient`** (interface, new) + **`RaindropApiClientImpl`** (impl, new) — pure HTTP layer, talks to `https://api.raindrop.io/rest/v1`. Reads the token at construction from `MyfeederProperties` and applies it as a default `Authorization` header on its `RestClient`. Two methods to start: `listCollections()` and `createBookmark(Long collectionId, String url, String title)`.
- **`RaindropService`** (existing, refactored) — delegates HTTP calls to `RaindropApiClient`. Loads `collectionId` from the per-user `IntegrationConfig` when saving an article. No longer reads `apiToken` from stored config.
- **`IntegrationConfigController`** (existing, modified) — gains two GET endpoints (`/raindrop/status`, `/raindrop/collections`); existing PUT body shrinks to `{ collectionId }`.

### Token-not-configured behavior

When `myfeeder.raindrop.api-token` is empty:

- The `RaindropApiClient` bean is still instantiated (we do **not** use `@ConditionalOnProperty`).
- All calls into the client throw a new `RaindropNotConfiguredException` (a dedicated exception type so `GlobalExceptionHandler` can map it to HTTP 503 cleanly).
- `GET /api/integrations/raindrop/status` returns `{ configured: false }`.
- `GET /api/integrations/raindrop/collections` returns HTTP 503.
- `POST /api/articles/{id}/raindrop` returns HTTP 503.

Rationale: keeping the bean unconditional avoids `@Autowired` gymnastics elsewhere and keeps `RaindropService`'s code path simple.

## API surface

| Method | Path | Body | Returns | Notes |
|---|---|---|---|---|
| GET | `/api/integrations/raindrop/status` | — | `{ configured: boolean }` | Cheap, no Raindrop call. Tells the dialog whether to render the form. |
| GET | `/api/integrations/raindrop/collections` | — | `[{ id: number, title: string }]` sorted ASC by title (case-insensitive) | 503 when token not configured; 502 on Raindrop error; 401 if Raindrop rejects the token. |
| PUT | `/api/integrations/raindrop` | `{ collectionId: number }` | `IntegrationConfig` | `apiToken` field removed from request shape. |
| DELETE | `/api/integrations/raindrop` | — | 204 | Unchanged. |
| POST | `/api/articles/{id}/raindrop` | — | 200 | Unchanged from a contract perspective. Returns 503 when token not configured or 400 when no collection selected for the user. |

Sort is performed server-side so the frontend stays trivial.

## Data model

`RaindropConfig` (the JSON DTO stored in `integration_config.config`):

```java
@Data
public class RaindropConfig {
    private Long collectionId;
}
```

The `apiToken` field is removed.

### Migration

Flyway migration **`V3__strip_raindrop_api_token.sql`** scrubs the legacy field from existing rows:

```sql
UPDATE integration_config
SET config = jsonb_build_object(
    'collectionId', (config::jsonb->>'collectionId')::bigint
)
WHERE type = 'RAINDROP'
  AND config IS NOT NULL
  AND config::jsonb ? 'apiToken';
```

This both removes the leaked token from the DB and ensures Jackson can deserialize the row regardless of `FAIL_ON_UNKNOWN_PROPERTIES` configuration. A `@DataJdbcTest` covers the migration path.

## Frontend

### `SettingsDialog.tsx`

On open:
1. Call `GET /api/integrations/raindrop/status`.
2. If `configured === false`: render a static notice — *"Raindrop is not configured by the administrator. Set `myfeeder.raindrop.api-token` in deployment values to enable."* No form, no further calls.
3. If `configured === true`: call `GET /api/integrations/raindrop/collections` and render:
   - A `<select>` of titles (sorted by the server).
   - The currently saved `collectionId` pre-selected if present in the list.
   - A "(saved collection no longer exists — pick again)" placeholder option if the saved id is missing from the response.
   - A Save button that PUTs `{ collectionId }`.

The API token text input is removed.

### `api/integrations.ts`

```ts
export interface RaindropConfig { collectionId: number }
export interface RaindropCollection { id: number; title: string }
export interface RaindropStatus { configured: boolean }

export const integrationsApi = {
  // ...existing
  getRaindropStatus: () => apiGet<RaindropStatus>('/integrations/raindrop/status'),
  listRaindropCollections: () => apiGet<RaindropCollection[]>('/integrations/raindrop/collections'),
  upsertRaindrop: (config: RaindropConfig) => apiPut<IntegrationConfig>('/integrations/raindrop', config),
}
```

No new TanStack Query hook is needed — the dialog can call these directly via local state, since they only matter while the dialog is open.

## Helm + deployment

Mirror the existing `secrets.anthropicApiKey` pattern exactly.

### `helm/myfeeder/values.yaml`

```yaml
secrets:
  postgresPassword: ""
  anthropicApiKey: ""
  raindropApiToken: ""        # new
```

### `helm/myfeeder/templates/app-secret.yaml`

Add one entry:

```yaml
spring-myfeeder-raindrop-api-token: {{ .Values.secrets.raindropApiToken | quote }}
```

### `helm/myfeeder/templates/app-deployment.yaml`

Add an env entry under `env:`:

```yaml
- name: MYFEEDER_RAINDROP_API_TOKEN
  valueFrom:
    secretKeyRef:
      name: {{ include "myfeeder.fullname" . }}-secret
      key: spring-myfeeder-raindrop-api-token
```

### `src/main/resources/application.yaml`

```yaml
myfeeder:
  raindrop:
    api-base-url: https://api.raindrop.io/rest/v1
    api-token: ${MYFEEDER_RAINDROP_API_TOKEN:}
```

Empty default keeps `bootRun` and `bootTestRun` working without the secret; integration becomes inert in that case.

### `deploy.sh`

Read `MYFEEDER_RAINDROP_API_TOKEN` from the local environment alongside `MYFEEDER_PG_PASSWORD` and `MYFEEDER_ANTHROPIC_API_KEY`, and pass via `--set secrets.raindropApiToken=...`. The script should warn (not fail) when the variable is unset, since the integration is optional.

## Resilience

- `RaindropApiClient` methods are annotated with `@CircuitBreaker(name = "raindrop")` + `@Retry(name = "raindrop")`, sharing the existing breaker with `saveToRaindrop`. Existing `resilience4j` config in `application.yaml` is unchanged.
- The `listCollections` call benefits from caching via `@Cacheable(value = "raindrop-collections", ...)` on `RaindropService` (vidtag pattern). TTL is governed by the existing Redis cache config; we do **not** add a new cache region for this. A follow-up could add explicit invalidation when the user reports stale data, but for now the cache TTL is sufficient.

## Errors → user-facing messages

| Backend status | Frontend toast |
|---|---|
| 503 from `/collections` | "Raindrop is not configured by the administrator." |
| 401 from `/collections` | "Raindrop rejected the configured API token. Check the deployment secret." |
| 502 from `/collections` | "Could not reach Raindrop. Try again in a moment." |
| 400 from `POST /articles/{id}/raindrop` (no collection chosen) | "Pick a Raindrop collection in Settings first." |

## Tests

- **`RaindropApiClientImplTest`** — `MockRestServiceServer`, covers `listCollections` response parsing and `createBookmark` happy-path. Sorting is not the client's responsibility.
- **`RaindropServiceTest`** — adapt existing tests to the new client seam. Sorting (case-insensitive, ASC by title) lives in `RaindropService.listCollections()` and is covered here. Add cases for "token not configured" → `RaindropNotConfiguredException` and "no collection selected" on `saveToRaindrop`.
- **`IntegrationConfigControllerTest`** — `@WebMvcTest` + `@MockitoBean RaindropApiClient`/`RaindropService`. Cover:
  - `GET /status` → `{configured:true|false}` based on `MyfeederProperties`.
  - `GET /collections` → 200 with sorted list, 503 when not configured, 401/502 propagation.
  - `PUT` accepting `{collectionId}` only; rejects unknown fields gracefully (or accepts and ignores — Jackson default).
- **`V3__strip_raindrop_api_token` migration test** — `@DataJdbcTest` with `@Import(TestcontainersConfiguration.class)`. Seed a legacy row, run migrations, assert `apiToken` is gone and `collectionId` is preserved.
- **Frontend `SettingsDialog.test.tsx`** — covers the not-configured notice, auto-load + render of sorted collections, the saved-but-missing-collection placeholder, and that the PUT body is `{collectionId}` (no `apiToken`).

## Build sequence

1. Backend: add `apiToken` to `MyfeederProperties.Raindrop`; add `RaindropApiClient` interface + impl; refactor `RaindropService` to delegate.
2. Backend: write Flyway `V3` migration + test.
3. Backend: simplify `RaindropConfig` DTO; update `IntegrationConfigController` (PUT shape, new GETs); add error mapping in `GlobalExceptionHandler` if needed.
4. Helm + `application.yaml` + `deploy.sh` wiring.
5. Frontend: API client additions; `SettingsDialog` refactor; tests.
6. Manual end-to-end verification in browser against a real Raindrop account.
