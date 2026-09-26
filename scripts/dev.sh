#!/usr/bin/env bash
# Starts Postgres, the API (profile "local") and the web dev server. Ctrl-C stops the API and web;
# the Postgres container keeps running (docker compose down to stop it).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/env.sh
source scripts/env.sh

set -m # each background job gets its own process group so we can stop the whole tree

if [ ! -d web/node_modules ]; then
  echo "Installing web dependencies (first run)..."
  (cd web && npm ci)
fi

echo "Starting Postgres..."
docker compose up -d --wait db

pids=()
cleanup() {
  trap - EXIT INT TERM
  echo
  echo "Stopping API and web..."
  for pid in "${pids[@]:-}"; do
    [ -n "$pid" ] && kill -TERM -- "-$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

(cd api && exec ./gradlew bootRun --args='--spring.profiles.active=local') &
pids+=($!)
(cd web && exec npm run dev -- --hostname 127.0.0.1) &
pids+=($!)

echo "API on http://127.0.0.1:8080, web on http://127.0.0.1:3000 (Ctrl-C to stop)"
wait
