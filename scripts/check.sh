#!/usr/bin/env bash
# Milestone gate: API tests, then web tests, lint and build. Fails fast.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/env.sh
source scripts/env.sh

echo "== api: tests =="
(cd api && ./gradlew test)

echo "== web: tests, lint, build =="
(cd web && npm run test && npm run lint && npm run build)

echo "All checks passed."
