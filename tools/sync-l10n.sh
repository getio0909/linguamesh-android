#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
L10N_ROOT="${LINGUAMESH_L10N_DIR:-$(dirname "$PROJECT_ROOT")/linguamesh-l10n}"
MODE="${1:---check}"
SOURCE_ROOT="$L10N_ROOT/generated/android"
PINNED_REVISION_FILE="$PROJECT_ROOT/l10n/REVISION"

if [[ "$MODE" != "--check" && "$MODE" != "--write" ]]; then
  echo "Usage: tools/sync-l10n.sh [--check|--write]" >&2
  exit 2
fi
if [[ ! -d "$SOURCE_ROOT" || ! -f "$L10N_ROOT/generated/manifest.json" || ! -f "$L10N_ROOT/compatibility.json" ]]; then
    echo "Localization sync failed: generated localization bundle is unavailable." >&2
    exit 1
fi
if [[ ! -f "$PINNED_REVISION_FILE" ]]; then
  echo "Localization sync failed: pinned revision is unavailable." >&2
  exit 1
fi
pinned_revision="$(tr -d '[:space:]' < "$PINNED_REVISION_FILE")"
if [[ ! "$pinned_revision" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Localization sync failed: pinned revision is malformed." >&2
  exit 1
fi
if ! git -C "$L10N_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Localization sync failed: localization source is not a Git worktree." >&2
  exit 1
fi
source_revision="$(git -C "$L10N_ROOT" rev-parse HEAD)"
if [[ "$source_revision" != "$pinned_revision" ]]; then
  echo "Localization sync failed: expected revision $pinned_revision but found $source_revision." >&2
  exit 1
fi
if [[ -n "$(git -C "$L10N_ROOT" status --porcelain -- generated compatibility.json)" ]]; then
  echo "Localization sync failed: pinned localization artifacts have uncommitted changes." >&2
  exit 1
fi

copy_or_check() {
  local source_file="$1"
  local destination_file="$2"
  if [[ "$MODE" == "--write" ]]; then
    install -D -m 0644 "$source_file" "$destination_file"
  elif ! cmp -s "$source_file" "$destination_file"; then
    echo "Localization sync failed: stale or missing file: $destination_file" >&2
    exit 1
  fi
}

declare -A expected_files=()
while IFS= read -r source_file; do
  qualifier="$(basename "$(dirname "$source_file")")"
  destination_file="$PROJECT_ROOT/app/src/main/res/$qualifier/linguamesh_generated.xml"
  expected_files["$destination_file"]=1
  copy_or_check "$source_file" "$destination_file"
done < <(find "$SOURCE_ROOT" -mindepth 2 -maxdepth 2 -type f -name strings.xml | sort)

while IFS= read -r destination_file; do
  if [[ -z "${expected_files[$destination_file]+present}" ]]; then
    if [[ "$MODE" == "--write" ]]; then
      unlink "$destination_file"
    else
      echo "Localization sync failed: stale generated file: $destination_file" >&2
      exit 1
    fi
  fi
done < <(find "$PROJECT_ROOT/app/src/main/res" -mindepth 2 -maxdepth 2 -type f -name linguamesh_generated.xml | sort)

copy_or_check "$L10N_ROOT/generated/manifest.json" "$PROJECT_ROOT/l10n/manifest.json"
copy_or_check "$L10N_ROOT/compatibility.json" "$PROJECT_ROOT/l10n/compatibility.json"

echo "Localization resources are synchronized."
