#!/usr/bin/env bash
set -euo pipefail

expected_goal_sha="11f9a65927aac7e57e2af119e9d21cc98e8d5a08b8a112a19ee1c47903e36198"
expected_l10n_revision="7fd210692bb269ef52f7453bfeb2b0f0759b1d4c"
expected_core_revision="8837e59395742b5385af5037aa36a2596af3b025"
required_files=(
  README.md
  LICENSE
  AGENTS.md
  REPOSITORY_ROLE.md
  GLOBAL_GOAL.md
  SECURITY.md
  CONTRIBUTING.md
  CODE_OF_CONDUCT.md
  THIRD_PARTY_NOTICES.md
  IMPLEMENTATION_STATUS.md
  docs/architecture.md
  docs/testing.md
  docs/releasing.md
  docs/adr/0001-minimum-android-api.md
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
  gradlew
  gradlew.bat
  core-sdk/README.md
  core-sdk/REVISION
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/org/linguamesh/android/core/CancellationRecoveryDrainer.kt
  app/src/main/java/org/linguamesh/android/core/CoreGateway.kt
  app/src/main/java/org/linguamesh/android/security/AndroidKeystoreCredentialStore.kt
  app/src/main/java/org/linguamesh/android/translation/TranslationViewModel.kt
  app/src/release/java/org/linguamesh/android/core/NativeCoreGateway.kt
  app/src/test/java/org/linguamesh/android/core/CancellationRecoveryDrainerTest.kt
  app/src/test/java/org/linguamesh/android/translation/TranslationViewModelTest.kt
  app/src/androidTest/java/org/linguamesh/android/ui/LinguaMeshAppTest.kt
  l10n/REVISION
  l10n/manifest.json
  l10n/compatibility.json
  tools/check-foundation.sh
  tools/stage-core-sdk.sh
  tools/sync-l10n.sh
  .gitignore
  .github/workflows/foundation.yml
)

for path in "${required_files[@]}"; do
  if [[ ! -f "$path" ]]; then
    echo "Missing required file: $path" >&2
    exit 1
  fi
done

for path in gradlew tools/check-foundation.sh tools/stage-core-sdk.sh tools/sync-l10n.sh; do
  if [[ ! -x "$path" ]]; then
    echo "Required executable is not executable: $path" >&2
    exit 1
  fi
done

grep -Fqx 'Central repository: `linguamesh-project`' GLOBAL_GOAL.md
grep -Fqx "Authoritative SHA-256: \`$expected_goal_sha\`" GLOBAL_GOAL.md
grep -Fq '`linguamesh-android`' REPOSITORY_ROLE.md
grep -Fqx "$expected_l10n_revision" l10n/REVISION
grep -Fqx "$expected_core_revision" core-sdk/REVISION
grep -Fq 'CoreResult.RESOURCE_EXHAUSTED' \
  app/src/release/java/org/linguamesh/android/core/NativeCoreGateway.kt

if [[ -e app/src/main/res/values/strings.xml ]]; then
  echo "Local strings.xml must not override generated localization resources." >&2
  exit 1
fi

bash -n tools/check-foundation.sh tools/stage-core-sdk.sh tools/sync-l10n.sh

mapfile -t text_files < <(
  find . \
    -type d \( \
      -name .git -o \
      -name .gradle -o \
      -name .kotlin -o \
      -name build -o \
      -name l10n-source -o \
      -name linguamesh-l10n \
    \) -prune -o \
    -type f \( \
      -name '*.bat' -o \
      -name '*.json' -o \
      -name '*.kt' -o \
      -name '*.kts' -o \
      -name '*.md' -o \
      -name '*.pro' -o \
      -name '*.properties' -o \
      -name '*.sh' -o \
      -name '*.toml' -o \
      -name '*.xml' -o \
      -name '*.yaml' -o \
      -name '*.yml' -o \
      -name '.gitignore' -o \
      -name 'gradlew' -o \
      -name 'LICENSE' \
    \) -print | sort
)
if grep -nE '[[:blank:]]+$' "${text_files[@]}"; then
  echo "Trailing whitespace detected." >&2
  exit 1
fi

mapfile -t unix_text_files < <(printf '%s\n' "${text_files[@]}" | grep -v '\.bat$')
if grep -Il $'\r' "${unix_text_files[@]}" | grep -q .; then
  echo "Carriage-return line endings detected." >&2
  exit 1
fi

echo "Foundation validation passed."
