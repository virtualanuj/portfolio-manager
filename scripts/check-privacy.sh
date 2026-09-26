#!/usr/bin/env bash
# Fails if the built web app mentions an analytics or tracker service or a public CDN
# (intent.md NFR-PRIV-1). Run after `npm run build`; scripts/check.sh does this.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/web/.next"

[ -d "$BUILD" ] || { echo "No build found at web/.next; run npm run build first." >&2; exit 1; }

PATTERN='googletagmanager|google-analytics|analytics\.google|segment\.(io|com)|mixpanel|hotjar|plausible|posthog|amplitude\.com|sentry\.io|cdn\.jsdelivr|unpkg\.com|cdnjs\.cloudflare|fonts\.googleapis|fonts\.gstatic'

# The build's own output only; caches and source maps of dependencies are not shipped to the browser.
if grep -rIlE "$PATTERN" "$BUILD/static" "$BUILD/server/app" 2>/dev/null; then
  echo "Found a tracker or CDN reference in the built output (listed above)." >&2
  exit 1
fi
echo "Privacy check passed: no analytics, tracker or CDN references in the build."
