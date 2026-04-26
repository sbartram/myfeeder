#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-$(./gradlew currentVersion -q 2>&1 | grep 'Project version' | awk '{print $NF}')}"
REGISTRY="registry.bartram.org/bartram/myfeeder"
NAMESPACE="myfeeder"
RELEASE="myfeeder"
CHART="helm/myfeeder"

echo "Deploying myfeeder version: $VERSION"

# Raindrop is optional; default to empty so set -u doesn't trip.
RAINDROP_TOKEN="${MYFEEDER_RAINDROP_API_TOKEN:-}"
if [[ -z "$RAINDROP_TOKEN" ]]; then
  echo "Warning: MYFEEDER_RAINDROP_API_TOKEN is unset; Raindrop integration will be disabled in this deployment."
fi

helm upgrade --install "$RELEASE" "$CHART" \
  -n "$NAMESPACE" \
  --create-namespace \
  --set app.image.tag="$VERSION" \
  --set secrets.postgresPassword="$MYFEEDER_PG_PASSWORD" \
  --set secrets.anthropicApiKey="$MYFEEDER_ANTHROPIC_API_KEY" \
  --set secrets.raindropApiToken="$RAINDROP_TOKEN" \
  --history-max 3
