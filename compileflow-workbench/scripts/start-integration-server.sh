#!/usr/bin/env bash
set -euo pipefail

WORKBENCH="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(cd "$WORKBENCH/.." && pwd)"

: "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
: "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
: "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"
: "${COMPILEFLOW_E2E_SERVER_API_KEY:?COMPILEFLOW_E2E_SERVER_API_KEY is required}"

bundled_jars=("$ROOT"/compileflow-workbench-server/target/compileflow-workbench-all-in-one-*.jar)
if [[ "${#bundled_jars[@]}" -ne 1 || ! -f "${bundled_jars[0]}" ]]; then
  echo "Expected exactly one bundled Workbench JAR; run pnpm verify:delivery --assembly-only first" >&2
  exit 1
fi

# Managed integration tests exercise the same fail-closed authentication mode as production.
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE=API_KEY
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY="$COMPILEFLOW_E2E_SERVER_API_KEY"
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL=workbench-integration-test

exec java -jar "${bundled_jars[0]}" \
  --spring.profiles.active=prod \
  --server.port=8080
