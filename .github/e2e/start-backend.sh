#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${E2E_CERT_DIR:?}" "${E2E_STORE_PASSWORD:?}" "${E2E_DB_PASSWORD:?}"

export AZURE_APP_CLIENT_ID=k9-los
export AZURE_APP_WELL_KNOWN_URL="http://localhost:${E2E_AZURE_PORT:-8100}/v2.0/.well-known/openid-configuration"

exec bash scripts/start-dev.sh --no-build \
  '-P:ktor.deployment.host=127.0.0.1' \
  "-P:nav.db.url=jdbc:postgresql://localhost:${E2E_DB_PORT:-5432}/k9los_unit" \
  '-P:nav.db.password='"$E2E_DB_PASSWORD" \
  "-P:nav.auth.clients.0.discovery_endpoint=$AZURE_APP_WELL_KNOWN_URL" \
  "-P:nav.kafka_aiven.bootstrap_servers=localhost:${E2E_KAFKA_PORT:-9093}" \
  '-P:nav.kafka_aiven.key_store_path='"$E2E_CERT_DIR/keystore.p12" \
  '-P:nav.kafka_aiven.trust_store_path='"$E2E_CERT_DIR/truststore.jks" \
  '-P:nav.kafka_aiven.credstore_password='"$E2E_STORE_PASSWORD"
