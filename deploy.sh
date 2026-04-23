#!/usr/bin/env bash
set -euo pipefail

VERSION=$(./gradlew currentVersion -q | awk '{print $NF}')
REGISTRY="registry.bartram.org/bartram/myfeeder"
NAMESPACE="myfeeder"
RELEASE="myfeeder"
CHART="helm/myfeeder"

echo "Deploying myfeeder version: $VERSION"

helm upgrade --install "$RELEASE" "$CHART" \
  -n "$NAMESPACE" \
  --create-namespace \
  --set app.image.tag="$VERSION" \
  --set secrets.postgresPassword="$MYFEEDER_PG_PASSWORD" \
  --set secrets.anthropicApiKey="$MYFEEDER_ANTHROPIC_API_KEY"

