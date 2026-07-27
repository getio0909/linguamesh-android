# Testing

## Prerequisites

Use JDK 21 and Android SDK platform 36 with build-tools 36.0.0. Set `ANDROID_HOME` when the SDK is not discoverable. Localization validation requires a clean `linguamesh-l10n` worktree at revision `43f5a6f069f6d0e6d075517b0c017784fe505b0d`; set `LINGUAMESH_L10N_DIR` when it is not the default sibling checkout.

Release validation additionally requires the AAR built from the exact Core revision recorded in
`core-sdk/REVISION`. The reproducible CI path uses JDK 21 for Core, NDK 28.2.13676358, Rust 1.93.0,
Gradle 9.5.0, and then JDK 21 for the Android client.

## Required debug checks

From the repository root run:

```sh
./tools/check-foundation.sh
./tools/sync-l10n.sh --check
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew lintDebug
```

JVM tests cover provider validation and credential rollback, streaming and terminal-state handling, cancellation recovery bounds and identity isolation, profile switching, and application-scoped gateway ownership. The instrumentation source compiles a responsive Compose workspace test.

Hosted CI runs `./gradlew connectedDebugAndroidTest` on an API 35 x86_64 Pixel 2 emulator with
animations disabled and software GPU rendering. Local runs still require a documented API
26-or-newer emulator or device; record its model, API level, locale, and result. This executes the
Compose instrumentation fixture but does not provide real Core integration, accessibility review,
restoration, RTL screenshot, macrobenchmark, or physical-device evidence.

## Release limit

Do not substitute a fabricated AAR to make release tasks pass. After a verified compatible AAR is
staged, run:

```sh
./gradlew assembleRelease
./gradlew testReleaseUnitTest
./gradlew lintRelease
```

The updated CI performs this sequence after building, checking, and staging the pinned Core AAR.
The hosted workflow must pass the complete sequence for Core `cb061d24a3e0c4059a65d099d30bc643e9e079ea`.
Local release commands still require a freshly staged artifact and are not evidence of device or
distribution readiness.
