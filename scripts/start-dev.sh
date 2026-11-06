#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export AZURE_APP_CLIENT_ID="${AZURE_APP_CLIENT_ID:-k9-los}"
export AZURE_APP_WELL_KNOWN_URL="${AZURE_APP_WELL_KNOWN_URL:-http://localhost:8100/v2.0/.well-known/openid-configuration}"

if [[ "${1:-}" == '--no-build' ]]; then
  shift
else
  mvn --batch-mode test-compile dependency:build-classpath \
    -DincludeScope=test -Dmdep.outputFile=target/dev-classpath.txt
fi
exec java -cp "target/test-classes:target/classes:$(cat target/dev-classpath.txt)" \
  no.nav.k9.los.K9LosDev "$@"
