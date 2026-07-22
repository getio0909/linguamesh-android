#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_AAR="${1:-}"
DESTINATION_AAR="$PROJECT_ROOT/core-sdk/linguamesh-core-android-0.1.0-alpha.1.aar"

if [[ -z "$SOURCE_AAR" || ! -f "$SOURCE_AAR" ]]; then
  echo "Usage: tools/stage-core-sdk.sh <path-to-verified-aar>" >&2
  exit 2
fi

install -m 0644 "$SOURCE_AAR" "$DESTINATION_AAR"
(
  cd "$(dirname "$DESTINATION_AAR")"
  sha256sum "$(basename "$DESTINATION_AAR")" > "$(basename "$DESTINATION_AAR").sha256"
)
echo "Core Android SDK staged."
