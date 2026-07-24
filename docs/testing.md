# Testing

## Prerequisites

Use JDK 21 and Android SDK platform 36 with build-tools 36.0.0. Set `ANDROID_HOME` when the SDK is not discoverable. Localization validation requires a clean `linguamesh-l10n` worktree at revision `7fd210692bb269ef52f7453bfeb2b0f0759b1d4c`; set `LINGUAMESH_L10N_DIR` when it is not the default sibling checkout.

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

`compileDebugAndroidTestKotlin` does not execute instrumentation. Run `./gradlew connectedDebugAndroidTest` only with a documented API 26-or-newer emulator or device, then record its model, API level, locale, and result. Accessibility, restoration, RTL screenshot, macrobenchmark, and real Core integration suites are not implemented yet.

## Release limit

Do not substitute a fabricated AAR to make release tasks pass. After a verified compatible AAR is
staged, run:

```sh
./gradlew assembleRelease
./gradlew testReleaseUnitTest
./gradlew lintRelease
```

The updated CI performs this sequence after building, checking, and staging the pinned Core AAR.
Workflow `30099769434` passed the complete hosted sequence for Core `9e69d01cbae1ca0421923e059aa3252c4ecbe1be`.
Local release commands still require a freshly staged artifact and are not evidence of device or
distribution readiness.
