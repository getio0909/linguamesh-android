# Implementation Status

Status date: 2026-07-27

## 2026-07-27 — Hosted prerelease artifact checkpoint

Assumption: the unsigned APK/AAB and checksum-verified AAR are prerelease evidence only; device,
instrumentation execution, signing, distribution, rollback, and stable-release gates remain open.

- Artifact commit `e4dd84deaca0ef5bf34a3cfb31a4bbb6a290f73c` consumes Core
  `cb061d24a3e0c4059a65d099d30bc643e9e079ea` and l10n
  `43f5a6f069f6d0e6d075517b0c017784fe505b0d`.
- Hosted Android validation `30287899440` passed debug/release assembly, JVM tests,
  instrumentation compilation, lint, clean Core AAR rebuild/checksum validation, bundle assembly,
  and release artifact staging/upload.
- A clean download passed `sha256sum --check SHA256SUMS`: unsigned APK
  `0ea1aa42bcbea0189b86abc657fefd29843f38edc0b9f8efb5e2fc33b2c70530`, AAB
  `c29af19c90c4ca1488de561b94446669c0526ad7b38ed28289fa4ca1dcf5df49`, and AAR
  `ed9a7de3990e64ad67e3e9a2a1097911662f237f00edd802add6ebb82c6d81a6`.
- No device execution or signed distribution package is claimed.

## 2026-07-27 — Central Linux-first prerelease pin alignment

Assumption: the central prerelease compatibility train is the authoritative input for the next
hosted Android check; changing only immutable source pins does not claim device or distribution
readiness.

- CI, the staged SDK revision, foundation checks, and release documentation now consume Core
  `cb061d24a3e0c4059a65d099d30bc643e9e079ea` and l10n
  `43f5a6f069f6d0e6d075517b0c017784fe505b0d`, matching central `release-manifest.toml`.
- Hosted Android validation for this exact pair is required before adding Android artifacts to the
  central prerelease; device, instrumentation execution, signing, and distribution gates remain
  open.

## 2026-07-24 — Core VFS-descendant Android compatibility checkpoint

Assumption: Core `9e69d01cbae1ca0421923e059aa3252c4ecbe1be` preserves the Android ABI-1 typed
host-secret contract while adding Linux-only storage tests; Android consumes the exact revision
without depending on Linux VFS behavior.

- Android Native CI now checks out Core `9e69d01cbae1ca0421923e059aa3252c4ecbe1be` and l10n
  `7fd210692bb269ef52f7453bfeb2b0f0759b1d4c`. The release gateway uses the generated typed
  `SecretRequired`/`sendHostResponse` flow and clears resolved secret bytes immediately.
- Hosted workflow `30099769434` rebuilt and checksum-verified the AAR from the exact Core source,
  then passed debug/release assembly, 19 JVM tests per variant, instrumentation compilation, and
  debug/release lint. This validates the Android ABI-1 compatibility checkpoint; release remains
  `unreleased` because device, document, signing, and distribution evidence is still open.

## 2026-07-24 — typed host-secret transport checkpoint

Assumption: the existing Android Keystore-backed credential store is the host resolver for Core's
ABI 1 secret channel; Core-owned profile persistence and file-lease projections remain separate
follow-up contracts.

- The release gateway now passes the profile's opaque `secretRef` to Core, consumes the generated
  wrapper's typed `SecretRequired` event, validates the requested reference, resolves the secret
  once, and sends a typed bounded response. Missing or failed storage returns an unavailable
  resolution; oversized or invalid UTF-8 values are rejected, and resolved byte arrays are cleared
  immediately after the response call. No secret value enters UI state, DataStore, or diagnostics.
- Core is pinned to `9e69d01cbae1ca0421923e059aa3252c4ecbe1be`, which includes the typed Android wrapper
  event/response API while retaining ABI/protocol version 1. The clean Core AAR rebuild and
  release-client integration passed hosted validation in workflow `30099769434`.
- Release remains `unreleased`; device Keystore, Core-owned persistence, document workflows,
  background execution, signing, distribution, and cross-client conformance remain open.

## 2026-07-24 — Persisted provider-profile metadata checkpoint

Assumption: Android may persist bounded, non-secret provider-profile metadata in DataStore until
Core-owned profile persistence is exposed by the generated wrapper; credential bytes remain only in
the Keystore-backed credential store and are represented by an opaque `secretRef`.

- Added a bounded `ProviderProfileRepository` with a DataStore implementation that stores only
  encoded profile identifiers, display names, endpoints, model IDs, and secret references. Invalid
  records, duplicate IDs, oversized fields, and unknown active IDs are rejected or ignored safely;
  write-time field limits prevent oversized metadata from entering DataStore.
- The application container now owns the repository. The translation ViewModel restores saved
  profiles on a background dispatcher, re-registers them with Core, preserves the active profile,
  and persists profile creation and active-profile changes without placing credentials in UI state.
- Added JVM coverage for restart-style restoration/Core registration, the secret-reference-only
  persistence boundary, and write-time size rejection. The l10n pin and generated manifest now follow
  `linguamesh-l10n` `7fd210692bb269ef52f7453bfeb2b0f0759b1d4c`.
- Release remains `unreleased`; Core-owned persistence, device restoration, document workflows,
  background execution, real-provider credentials, signing, and distribution remain open.

Validation for this checkpoint on 2026-07-24 with JDK 21 and Android SDK 36:

- `./tools/check-foundation.sh` passed.
- `./tools/sync-l10n.sh --check` passed at the pinned l10n revision.
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew testDebugUnitTest --rerun-tasks` passed
  (19 tests, 0 failures).
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew assembleDebug
  compileDebugAndroidTestKotlin lintDebug` passed.
- `ANDROID_HOME=/home/wangtinghu/Android/Sdk ./gradlew assembleRelease testReleaseUnitTest
  lintRelease` passed (19 release tests); the staged AAR remains prior-pin provenance and is not treated as a clean
  Core rebuild.
- Hosted Android workflow `30091714901` passed clean Core AAR provenance checks, debug/release
  builds, 18 JVM tests per variant, instrumentation compilation, and debug/release lint. The final
  bounded-write head `535e4485ff555b04702c0390ca76428ff995d457` passed the same gates in workflow
  `30092613918`, including 19 JVM tests per variant.

## Implemented

- Single-module Gradle Android application targeting API 36 with minimum API 26, Java 17 bytecode, Kotlin, Compose, coroutines, Flow, and DataStore.
- Responsive provider onboarding, profile selection, streaming translation state, cancellation, settings, theme, runtime locale, RTL direction, and baseline accessibility semantics.
- Application-scoped Core gateway contract with an unavailable debug implementation and generated-wrapper release adapter source.
- Native event identity and sequence validation plus bounded cancellation draining; an unrecoverable or mismatched cancelled operation isolates the gateway.
- Android Keystore AES-256-GCM credential broker with secret-reference AAD, mutable-buffer clearing, failure rollback, and backup/device-transfer exclusion.
- Generated Android resources synchronized from clean `linguamesh-l10n` revision `7fd210692bb269ef52f7453bfeb2b0f0759b1d4c`; no local `strings.xml` override remains.
- Sixteen JVM regression tests and one compiling Compose instrumentation test.
- Core ABI 1 integration pinned to exact source revision
  `9e69d01cbae1ca0421923e059aa3252c4ecbe1be`; the release adapter maps
  `CoreResult.RESOURCE_EXHAUSTED` to a safe protocol failure.
- GitHub Actions debug and release preparation with immutable Node 24-compatible action revisions,
  `persist-credentials: false`, pinned localization and Core checkouts, NDK 28.2.13676358, Rust
  1.93.0, Gradle 9.5.0, AAR metadata/checksum verification and staging, plus debug and release
  builds, debug and release unit tests and lint, and debug instrumentation compilation.
- The authoritative Android workflow run `29932649692` passed after correcting the package metadata
  assertion to Core's Cargo version `0.1.0-alpha.2`; it rebuilt and verified the AAR from Core
  `8837e59395742b5385af5037aa36a2596af3b025` and passed all debug/release checks.

## Not implemented or not verified

- The locally staged Core AAR may still be an older developer artifact; provenance for the current
  pin is the clean CI rebuild and metadata/checksum verification in workflow `30099769434`.
- Debug builds intentionally report Core unavailable and cannot perform real translation.
- Core-owned provider-profile persistence/loading, device-backed integration, and document workflows
  are not integrated; the release adapter now handles credential-bearing profiles through the typed
  host-secret exchange.
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
- GitHub Actions run `29932649692` (job `88966082464`) passed on commit `51729fa`: clean Core/l10n
  checkouts, debug and release builds, 16 debug and 16 release JVM tests, instrumentation compile,
  debug and release lint, and Core AAR metadata/checksum/JNI verification.
- `git diff --check` passed for tracked changes; `tools/check-foundation.sh` separately checked whitespace and line endings across tracked and untracked source files.

Workflow YAML parsing, immutable 40-character action-reference checks, checkout credential settings,
the exact Core/l10n revision pins, and `git diff --check` passed static validation. The pinned NDK is
unavailable locally, so the clean AAR rebuild was verified only in CI. `connectedDebugAndroidTest`
and device-based acceptance checks remain unverified.
