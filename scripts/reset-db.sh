#!/usr/bin/env bash
# Deletes the local Postgres volume and recreates an empty database. Destroys all local data.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

read -r -p "This deletes ALL local portfolio data. Type 'y' to continue: " answer
if [ "$answer" != "y" ]; then
  echo "Aborted."
  exit 1
fi

docker compose down -v
docker compose up -d --wait db
echo "Database recreated. Flyway applies the schema on the next API start."
