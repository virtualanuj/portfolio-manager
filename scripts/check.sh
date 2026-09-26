#!/usr/bin/env bash
# Milestone gate: API tests, then web tests, lint and build. Fails fast.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/env.sh
source scripts/env.sh

echo "== api: format, tests =="
(cd api && ./gradlew spotlessCheck test)

echo "== web: format, tests, lint, build =="
(cd web && npm run format:check && npm run test && npm run lint && npm run build)

echo "== privacy: no trackers or CDNs in the build =="
scripts/check-privacy.sh

echo "All checks passed."
