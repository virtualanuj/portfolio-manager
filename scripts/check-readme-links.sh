#!/usr/bin/env bash
# Fails if README.md links to, or names in `code`, a file or folder that does not exist.
# Checks relative markdown links and code spans that start with a known top-level folder.
# Usage: scripts/check-readme-links.sh [README path]   (default: README.md at the repo root)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="${1:-$ROOT/README.md}"
BASE="$(dirname "$README")"
missing=0

check() {
  local target="$1" kind="$2"
  target="${target%%#*}"
  [ -n "$target" ] || return 0
  if [ ! -e "$BASE/$target" ]; then
    echo "MISSING ($kind): $target" >&2
    missing=$((missing + 1))
  fi
}

# [text](relative/path) links, skipping URLs, anchors and mailto
while IFS= read -r link; do
  case "$link" in http*|mailto:*|\#*) continue ;; esac
  check "$link" link
done < <(grep -oE '\]\([^)]+\)' "$README" | sed -E 's/^\]\(//; s/\)$//')

# `scripts/...`, `docs/...`, `web/...`, `api/...` code spans; trailing punctuation is trimmed
while IFS= read -r path; do
  case "$path" in *'*'*|*'<'*|*' '*) continue ;; esac
  check "${path%/}" path
done < <(grep -oE '`(scripts|docs|web|api)/[A-Za-z0-9_./-]+`' "$README" | tr -d '`' | sed -E 's/[.,:;]+$//')

if [ "$missing" -gt 0 ]; then
  echo "$missing broken reference(s) in $README" >&2
  exit 1
fi
echo "README links OK"
