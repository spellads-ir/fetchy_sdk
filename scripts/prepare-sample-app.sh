#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${GOOGLE_SERVICES_JSON:-/home/mahan/Downloads/google-services (2).json}"
DEST="$ROOT/sample-app/google-services.json"

if [[ ! -f "$SRC" ]]; then
  echo "Missing google-services.json at: $SRC"
  echo "Set GOOGLE_SERVICES_JSON to your file path."
  exit 1
fi

cp "$SRC" "$DEST"
echo "Copied google-services.json -> $DEST"

if [[ ! -f "$ROOT/local.properties" ]]; then
  cat > "$ROOT/local.properties" <<EOF
sdk.dir=$HOME/Android/Sdk
EOF
  echo "Created local.properties"
fi

echo "Sample app prepared."
