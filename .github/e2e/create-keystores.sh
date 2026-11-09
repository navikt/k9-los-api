#!/usr/bin/env bash
set -euo pipefail
: "${E2E_CERT_DIR:?}" "${E2E_STORE_PASSWORD:?}"

mkdir -p "$E2E_CERT_DIR"
keytool -genkeypair -alias los-e2e -keyalg RSA -keysize 2048 -validity 2 \
  -dname 'CN=localhost' -ext 'SAN=dns:localhost,dns:kafka' \
  -storetype PKCS12 -keystore "$E2E_CERT_DIR/keystore.p12" -storepass:env E2E_STORE_PASSWORD
keytool -exportcert -alias los-e2e -keystore "$E2E_CERT_DIR/keystore.p12" \
  -storepass:env E2E_STORE_PASSWORD -file "$E2E_CERT_DIR/cert.der"
keytool -importcert -noprompt -alias los-e2e -storetype JKS \
  -keystore "$E2E_CERT_DIR/truststore.jks" -storepass:env E2E_STORE_PASSWORD -file "$E2E_CERT_DIR/cert.der"
printf '%s' "$E2E_STORE_PASSWORD" > "$E2E_CERT_DIR/password"
cat > "$E2E_CERT_DIR/client.properties" <<EOF
security.protocol=SSL
ssl.keystore.location=/etc/kafka/secrets/keystore.p12
ssl.keystore.type=PKCS12
ssl.keystore.password=$E2E_STORE_PASSWORD
ssl.key.password=$E2E_STORE_PASSWORD
ssl.truststore.location=/etc/kafka/secrets/truststore.jks
ssl.truststore.type=JKS
ssl.truststore.password=$E2E_STORE_PASSWORD
EOF
