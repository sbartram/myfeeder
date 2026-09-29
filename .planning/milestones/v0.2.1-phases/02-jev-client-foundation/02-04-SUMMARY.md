---
phase: 02-jev-client-foundation
plan: 04
subsystem: infra
tags: [helm, k8s, deploy, secrets, typesafe, jev]

requires:
  - phase: 02-jev-client-foundation (plan 02-01)
    provides: "spring.ai.typesafe.api-key bound from ${MYFEEDER_TYPESAFE_API_KEY:}; app starts keyless"
provides:
  - "Helm value secrets.typesafeApiKey (default empty)"
  - "Secret key myfeeder-typesafe-api-key under stringData"
  - "App container env MYFEEDER_TYPESAFE_API_KEY via secretKeyRef"
  - "checksum/secret pod-template annotation on the app Deployment (any Secret change rolls the pod)"
  - "deploy.sh TYPESAFE_KEY with unset warning, forwarded as --set secrets.typesafeApiKey"
affects: [phase-04, phase-07, deploy, helm]

actuals:
  tokens: 1703
  tasks: 2
  commits: 2
plan_head_before: 3e8d4a3e6a172a9ab32491e5fd303e66601106e8

tech-stack:
  added: []
  patterns:
    - "checksum/secret pod-template annotation (include app-secret.yaml | sha256sum) so Secret-only changes roll the Deployment"
    - "Optional deploy secret: ${VAR:-} + fixed warning in deploy.sh, empty default in values.yaml, no required/fail"

key-files:
  created: []
  modified:
    - helm/myfeeder/values.yaml
    - helm/myfeeder/templates/app-secret.yaml
    - helm/myfeeder/templates/app-deployment.yaml
    - deploy.sh

key-decisions:
  - "TypeSafe key mirrors Raindrop's optional secret at all four touchpoints; empty key = scoring disabled, no separate enabled flag"
  - "checksum/secret hashes the whole rendered Secret: any secret change (PG password, Anthropic, Raindrop, TypeSafe) rolls the pod; first deploy with the annotation rolls once"
  - "Kept --set (not --set-string) to mirror Raindrop; keys with comma/backslash would be mangled (known limitation shared with Raindrop)"
  - "No Helm lookup to preserve an existing key across an upgrade that omits it (deferred to Phase 4 or 7); no k3s deploy this phase"

patterns-established:
  - "Pod roll on secret change: checksum/secret under spec.template.metadata.annotations on the app Deployment only"

requirements-completed: [JEV-04]

coverage:
  - id: D1
    description: "Chart carries the optional TypeSafe key: values default empty, Secret stringData key, env via secretKeyRef; keyless render and helm lint pass"
    requirement: JEV-04
    verification:
      - kind: other
        ref: "helm lint helm/myfeeder --set app.image.tag=t (1 chart(s) linted, 0 chart(s) failed)"
        status: pass
      - kind: other
        ref: "helm template keyless render: myfeeder-typesafe-api-key: \"\" under stringData, MYFEEDER_TYPESAFE_API_KEY env + secretKeyRef present"
        status: pass
    human_judgment: false
  - id: D2
    description: "checksum/secret on the app pod template renders exactly once, changes on a key-only change (key-A vs key-B), unchanged on an image-tag-only change"
    requirement: JEV-04
    verification:
      - kind: other
        ref: "helm template key-A/key-B/tag-only comparison (plan 02-04 Task 1 verify #2)"
        status: pass
    human_judgment: false
  - id: D3
    description: "deploy.sh succeeds keyless with a warning and forwards secrets.typesafeApiKey=, forwards a set key without the warning, never echoes the key"
    requirement: JEV-04
    verification:
      - kind: other
        ref: "bash -n deploy.sh"
        status: pass
      - kind: other
        ref: "fake-helm keyless run (plan 02-04 Task 2 verify #2)"
        status: pass
      - kind: other
        ref: "fake-helm keyed run, key-X appears exactly once (plan 02-04 Task 2 verify #3)"
        status: pass
    human_judgment: false

duration: 1min
completed: 2026-09-23
status: complete
---

# Phase 02 Plan 04: Optional TypeSafe Key Deploy Secret Summary

**Optional `MYFEEDER_TYPESAFE_API_KEY` wired through deploy.sh, the Helm values, the Secret (stringData) and the app env, mirroring Raindrop. A `checksum/secret` pod-template annotation rolls the pod on a key-only change.**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-09-23T03:10:34Z
- **Completed:** 2026-09-23T03:11:36Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- `values.yaml` declares `secrets.typesafeApiKey: ""`. `app-secret.yaml` renders `myfeeder-typesafe-api-key` under `stringData`, above the conditional `data:` block. The app container gets `MYFEEDER_TYPESAFE_API_KEY` via `secretKeyRef`.
- `checksum/secret: {{ include (print $.Template.BasePath "/app-secret.yaml") . | sha256sum }}` sits on the app Deployment's pod template only. It renders once in the chart: key-A `3944e207…`, key-B `09490b70…`, and the tag-only change with key-A stays `3944e207…`.
- `deploy.sh` reads `TYPESAFE_KEY="${MYFEEDER_TYPESAFE_API_KEY:-}"` and prints a fixed warning when it is empty. It always passes `--set secrets.typesafeApiKey="$TYPESAFE_KEY"`. `set -euo pipefail` is unchanged, and the script never exits because the key is missing.

## Task Commits

1. **Task 1 (tracer): Helm key → pod env + checksum/secret pod roll** - `cd52fdf` (feat)
2. **Task 2: deploy.sh optional key with warning, forwarded to helm** - `a35d196` (feat)

## Files Created/Modified
- `helm/myfeeder/values.yaml` - `secrets.typesafeApiKey: ""` default
- `helm/myfeeder/templates/app-secret.yaml` - `myfeeder-typesafe-api-key` stringData entry
- `helm/myfeeder/templates/app-deployment.yaml` - `MYFEEDER_TYPESAFE_API_KEY` env and the `checksum/secret` pod-template annotation
- `deploy.sh` - optional `TYPESAFE_KEY`, unset warning, `--set secrets.typesafeApiKey`

## Decisions Made
- **Mirror Raindrop exactly.** An empty key means scoring is disabled, so there is no separate `jev.enabled` flag (assumption_delta_decision: no-change).
- **Whole-Secret checksum (intended behavior).** Any Secret change now rolls the app pod: the Postgres password, Anthropic key, Raindrop token or TypeSafe key. **The first deploy that carries the annotation rolls the pod once**, even if no secret changed.
- **`--set` kept, not `--set-string`.** Known limitation shared with Raindrop: Helm parses a key containing `,` or `\`, which would mangle it. Real TypeSafe keys are expected to be URL-safe (RESEARCH A1). A mangled key would surface as a 401, then a WARN and an open breaker (D-06), not silent corruption.
- **Helm `lookup` preservation stays deferred** to Phase 4 or 7. Same as Raindrop today: a `helm upgrade` run without the env var overwrites the stored key with empty.
- **Rollout concurrency:** a key-only change rolls through the default RollingUpdate. The old pod keeps serving with the old key until the new pod (new key) is ready. The app reads the key only at startup.

## Verification
- `helm lint helm/myfeeder --set app.image.tag=t` → `1 chart(s) linted, 0 chart(s) failed`
- All three Task 1 verify commands and all Task 1 acceptance criteria pass (the secret line sits at line 13, below the `googleApplicationCredentials` conditional at line 14; `lookup` count is 0 in both templates).
- `bash -n deploy.sh` passes, and both fake-helm runs pass. The `--set secrets.typesafeApiKey` line (31) sits between the Raindrop line (30) and `--history-max` (32).
- **No real `helm` or `kubectl` command ran against the cluster.** Verification used only `helm template`/`helm lint` (local rendering) and a fake `helm` script first on PATH with `KUBECONFIG=/nonexistent`.

## Deviations from Plan

None. The plan ran exactly as written.

## Issues Encountered
- The fake-helm verify runs inherit the operator shell's environment. This shell had a real `MYFEEDER_RAINDROP_API_TOKEN` set, so that token showed up in the fake helm's argument echo (tool output and two `mktemp` output files). It was never committed. Both temp directories were deleted right after verification. If these verify commands are reused, add `-u MYFEEDER_RAINDROP_API_TOKEN` to the `env` invocation.

## User Setup Required

None. `MYFEEDER_TYPESAFE_API_KEY` is optional. Export it before `./deploy.sh <version>` once a key exists (first keyed deploy planned for Phase 7).

## Next Phase Readiness
- The deploy path for the key is ready. Plan 02-01 already binds `MYFEEDER_TYPESAFE_API_KEY` into `spring.ai.typesafe.api-key`.
- No k3s deploy happened in this phase (RESEARCH Open Question 3).

---
*Phase: 02-jev-client-foundation*
*Completed: 2026-09-23*

## Self-Check: PASSED

- FOUND: helm/myfeeder/values.yaml, helm/myfeeder/templates/app-secret.yaml, helm/myfeeder/templates/app-deployment.yaml, deploy.sh
- FOUND: cd52fdf, a35d196
