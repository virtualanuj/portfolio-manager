#!/usr/bin/env bash
# Fills a running local app with realistic demo data, so the UI can be judged without typing it in.
# Off by default: nothing seeds itself. Run it yourself with the API up (scripts/dev.sh).
#
#   scripts/seed-demo.sh            refuses if the database already has transactions
#   scripts/seed-demo.sh --force    seeds anyway (rows already present are skipped as duplicates)
#   API_URL=http://127.0.0.1:8080/api scripts/seed-demo.sh
#
# Demo instruments are priced manually, so no network is needed and Refresh has nothing to fetch.
# scripts/reset-db.sh removes everything, demo data included.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="${API_URL:-http://127.0.0.1:8080/api}"
FORCE=false
[ "${1:-}" = "--force" ] && FORCE=true

command -v jq >/dev/null || { echo "jq is required (brew install jq)." >&2; exit 1; }

curl -sf "$API/actuator/health/liveness" >/dev/null || {
  echo "The API is not reachable at $API. Start it with scripts/dev.sh first." >&2
  exit 1
}

existing="$(curl -sf "$API/transactions?size=1" | jq '.totalItems')"
if [ "$existing" -gt 0 ] && [ "$FORCE" != true ]; then
  echo "The database already has $existing transactions. Refusing to add demo data." >&2
  echo "Use scripts/reset-db.sh for a clean start, or pass --force to seed anyway." >&2
  exit 1
fi

echo "Importing demo transactions..."
batch="$(curl -sf -F "file=@$ROOT/scripts/demo/transactions.csv" "$API/imports" | jq -r '.id')"
curl -sf -X POST "$API/imports/$batch/commit" | jq -c '.'

echo "Pricing instruments manually..."
today="$(date +%Y-%m-%d)"
instruments="$(curl -sf "$API/instruments")"
jq -c '.[]' "$ROOT/scripts/demo/instruments.json" | while read -r demo; do
  symbol="$(jq -r '.symbol' <<<"$demo")"
  type="$(jq -r '.assetType' <<<"$demo")"
  price="$(jq -r '.price' <<<"$demo")"
  id="$(jq -r --arg s "$symbol" --arg t "$type" '.[] | select(.symbol == $s and .assetType == $t) | .id' <<<"$instruments")"
  [ -n "$id" ] || { echo "  $symbol not found, skipped"; continue; }
  body="$(jq -c --arg s "$symbol" --arg t "$type" '.[] | select(.symbol == $s and .assetType == $t) | {symbol, name, assetType, sourceId, priceSource: "MANUAL"} | with_entries(select(.value != null))' <<<"$instruments")"
  curl -sf -X PUT "$API/instruments/$id" -H 'content-type: application/json' -d "$body" >/dev/null
  curl -sf -X PUT "$API/instruments/$id/manual-price" -H 'content-type: application/json' \
    -d "{\"price\":\"$price\",\"asOf\":\"$today\"}" >/dev/null
  echo "  $symbol at $price"
done

echo "Setting target allocation..."
curl -sf -X PUT "$API/allocation/targets" -H 'content-type: application/json' \
  -d '[{"assetType":"ETF","targetPct":"45"},{"assetType":"MUTUAL_FUND","targetPct":"25"},{"assetType":"STOCK","targetPct":"10"},{"assetType":"CRYPTO","targetPct":"20"}]' >/dev/null

echo "Done. Open http://127.0.0.1:3000"
