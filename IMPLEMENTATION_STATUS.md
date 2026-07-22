# Implementation Status

Status date: 2026-07-22

## Implemented

- Single-module Gradle Android application targeting API 36 with minimum API 26, Java 17 bytecode, Kotlin, Compose, coroutines, Flow, and DataStore.
- Responsive provider onboarding, profile selection, streaming translation state, cancellation, settings, theme, runtime locale, RTL direction, and baseline accessibility semantics.
- Application-scoped Core gateway contract with an unavailable debug implementation and generated-wrapper release adapter source.
- Native event identity and sequence validation plus bounded cancellation draining; an unrecoverable or mismatched cancelled operation isolates the gateway.
- Android Keystore AES-256-GCM credential broker with secret-reference AAD, mutable-buffer clearing, failure rollback, and backup/device-transfer exclusion.
- Generated Android resources synchronized from clean `linguamesh-l10n` revision `3724cc9d436ebdbac3b8ebf0df9bce9af1b41b15`; no local `strings.xml` override remains.
- Sixteen JVM regression tests and one compiling Compose instrumentation test.
- Core ABI 1 integration pinned to exact source revision
  `8837e59395742b5385af5037aa36a2596af3b025`; the release adapter maps
  `CoreResult.RESOURCE_EXHAUSTED` to a safe protocol failure.
- GitHub Actions debug and release preparation with immutable Node 24-compatible action revisions,
  `persist-credentials: false`, pinned localization and Core checkouts, NDK 28.2.13676358, Rust
  1.93.0, Gradle 9.5.0, AAR metadata/checksum verification and staging, plus debug and release
  builds, debug and release unit tests and lint, and debug instrumentation compilation.

## Not implemented or not verified

- The staged Core AAR is checksum-verified locally, but it was built before this checkpoint's Core
  pin and is not accepted as provenance evidence. CI must rebuild the AAR from the pinned `8837e59`
  checkout and verify its metadata, checksum, generated wrapper, and JNI dependencies.
- Debug builds intentionally report Core unavailable and cannot perform real translation.
- Credential host responses and Core-owned provider-profile persistence/loading are not integrated; the release adapter rejects credential-bearing translation profiles.
- Instrumentation, accessibility, restoration, RTL screenshot, macrobenchmark, device Keystore, and real Core integration tests were not executed.
- Document workflows, Storage Access Framework leases, history, routing, background work, packaging, signing, and distribution remain unimplemented.

## Evidence

Validated locally on 2026-07-22 with JDK 21.0.2 and Android SDK 36:

- `./tools/check-foundation.sh` passed.
- `./tools/sync-l10n.sh --check` passed against the exact clean pinned revision `3724cc9`.
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew assembleDebug` passed.
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew testDebugUnitTest --rerun-tasks` passed: 16 tests, 0 failures, 0 errors, 0 skipped.
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew compileDebugAndroidTestKotlin` passed.
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew lintDebug` passed with 0 errors. Remaining warnings are pinned platform/dependency updates and canonical generated-catalog plural/unused-resource findings.
- The staged AAR checksum `e659adbde0de708ea0d7c762545418a9e1d90afc88e135c5bc3a511d96f58e8d` verified, and local `assembleRelease`, `testReleaseUnitTest --rerun-tasks` (16 tests), and `lintRelease` passed against that staged artifact; these are not provenance evidence for the new Core pin.
- `git diff --check` passed for tracked changes; `tools/check-foundation.sh` separately checked whitespace and line endings across tracked and untracked source files.

Workflow YAML parsing, immutable 40-character action-reference checks, checkout credential settings,
the exact Core/l10n revision pins, and `git diff --check` passed static validation. The pinned NDK is
unavailable locally, so rebuilding the AAR from Core `8837e59` was not run. `connectedDebugAndroidTest`
and the updated GitHub Actions workflow were not run locally; their results remain required CI evidence.
