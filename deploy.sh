#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="myfeeder"
RELEASE="myfeeder"
CHART="helm/myfeeder"

helm upgrade --install "$RELEASE" "$CHART" \
  -n "$NAMESPACE" \
  --create-namespace \
  --set secrets.postgresPassword="$MYFEEDER_PG_PASSWORD" \
  --set secrets.anthropicApiKey="$MYFEEDER_ANTHROPIC_API_KEY"

