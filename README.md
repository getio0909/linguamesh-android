# LinguaMesh for Android

Native Android client for LinguaMesh, built with Kotlin, Jetpack Compose, coroutines, Flow, Android Keystore, and the generated LinguaMesh Core Android wrapper.

## Current status

The repository contains a buildable debug application and one translation-workspace vertical slice: provider onboarding, profile selection, streaming state, cancellation, theme and locale preferences, responsive Compose UI, and accessibility semantics. Debug builds deliberately use `UnavailableCoreGateway`; they validate the host UI but cannot perform real translation. Release source targets the exact Core ABI 1 revision in `core-sdk/REVISION`. CI is prepared to build and stage that AAR, but release assembly remains unverified until the updated job passes.

## Source layout

- `app/src/main/`: shared application, UI, preferences, credential broker, and core contracts.
- `app/src/debug/`: debug gateway that reports Core as unavailable.
- `app/src/release/`: generated-wrapper adapter for a staged Core AAR.
- `app/src/test/` and `app/src/androidTest/`: JVM and Compose instrumentation tests.
- `l10n/`: pinned localization revision and copied compatibility metadata.
- `core-sdk/`: pinned Core revision and the ignored local AAR staging location.
- `tools/`: foundation, localization-sync, and Core-AAR staging scripts.

## Local validation

Use JDK 21 and Android SDK 36. Keep sibling `linguamesh-l10n` at the revision in `l10n/REVISION`, or set `LINGUAMESH_L10N_DIR` to that checkout.

```sh
export ANDROID_HOME=/path/to/Android/Sdk
./tools/check-foundation.sh
./tools/sync-l10n.sh --check
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew lintDebug
```

For the credential-free emulator path, run the Core fake provider on port `40123`, execute
`adb reverse tcp:40123 tcp:40123`, and keep the default endpoint
`http://127.0.0.1:40123/v1`. Remote HTTP and emulator host aliases are intentionally rejected;
remote providers require HTTPS.

Read [GLOBAL_GOAL.md](GLOBAL_GOAL.md), [REPOSITORY_ROLE.md](REPOSITORY_ROLE.md), and [docs/architecture.md](docs/architecture.md) before contributing.

## License

MIT. See [LICENSE](LICENSE).
