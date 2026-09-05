#!/usr/bin/env bash
# Start Vite web + loopback development gateway for frontend work.
# Open http://127.0.0.1:5173. Does not start Workbench Server or Docker.
set -euo pipefail

WORKBENCH="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GATEWAY_PORT="${COMPILEFLOW_DEV_GATEWAY_PORT:-3001}"
WEB_URL='http://127.0.0.1:5173'

cd "$WORKBENCH"

if ! command -v pnpm >/dev/null 2>&1; then
  echo "pnpm is required; install pnpm 11.11 and retry" >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  echo "node_modules is missing; run 'pnpm install' from compileflow-workbench/ first" >&2
  exit 1
fi

gateway_pid=""
web_pid=""

# Terminate a process and its descendants. Children first so they are not reparented.
kill_tree() {
  local pid=$1
  local child
  [[ -n "$pid" ]] || return 0
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    kill_tree "$child"
  done
  kill "$pid" 2>/dev/null || true
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  kill_tree "$gateway_pid"
  kill_tree "$web_pid"
  wait 2>/dev/null || true
  exit "$exit_code"
}

trap cleanup EXIT INT TERM

echo "Starting CompileFlow Workbench with development gateway"
echo "Web:     ${WEB_URL}"
echo "Gateway: http://127.0.0.1:${GATEWAY_PORT}"
echo "Press Ctrl+C to stop both processes"
echo

# Same override for both child processes when the port is customized.
export COMPILEFLOW_DEV_GATEWAY_PORT="$GATEWAY_PORT"

pnpm --filter @compileflow/workbench-dev-gateway dev &
gateway_pid=$!

pnpm --filter @compileflow/workbench-web dev &
web_pid=$!

# Bash 3.2 (macOS /bin/bash) has no wait -n; poll until either child exits.
while kill -0 "$gateway_pid" 2>/dev/null && kill -0 "$web_pid" 2>/dev/null; do
  sleep 1
done

exit_code=0
if ! kill -0 "$gateway_pid" 2>/dev/null; then
  wait "$gateway_pid" || exit_code=$?
elif ! kill -0 "$web_pid" 2>/dev/null; then
  wait "$web_pid" || exit_code=$?
fi
exit "$exit_code"
