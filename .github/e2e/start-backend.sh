#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${E2E_DB_PASSWORD:?}"

export AZURE_APP_CLIENT_ID=k9-los
export AZURE_APP_WELL_KNOWN_URL="http://localhost:${E2E_AZURE_PORT:-8100}/v2.0/.well-known/openid-configuration"

exec bash scripts/start-dev.sh --no-build \
  '-P:ktor.deployment.host=127.0.0.1' \
  "-P:nav.db.url=jdbc:postgresql://localhost:${E2E_DB_PORT:-5432}/k9los_unit" \
  '-P:nav.db.password='"$E2E_DB_PASSWORD" \
  "-P:nav.auth.clients.0.discovery_endpoint=$AZURE_APP_WELL_KNOWN_URL"
