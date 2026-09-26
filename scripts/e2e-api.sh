#!/usr/bin/env bash
# Starts the API for end-to-end tests on its own database (portfolio_e2e), recreated on every run so
# tests never touch development data. Used by web/playwright.config.ts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/env.sh
source scripts/env.sh

PORT="${E2E_API_PORT:-8081}"

docker compose up -d --wait db
docker compose exec -T db psql -U portfolio -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS portfolio_e2e WITH (FORCE)" \
  -c "CREATE DATABASE portfolio_e2e"

cd api
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/portfolio_e2e" \
SERVER_PORT="$PORT" \
  exec ./gradlew bootRun --args='--spring.profiles.active=local,e2e'
