# Testing

## Available now

Setup requires only Bash, Git, and standard POSIX utilities. From the repository root run:

```sh
./tools/check-foundation.sh
```

This verifies required foundation files, the global-goal revision pin, repository identity, line endings, and trailing whitespace. It is the only implemented format/lint/test check. There is no Android build.

## Planned commands, unavailable now

The following command contract is planned but cannot run until the checked-in Gradle wrapper, version catalog, and Android modules exist:

```sh
./gradlew --version
./gradlew spotlessCheck
./gradlew lint
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

Do not report any of these commands as executed at the foundation checkpoint. Instrumentation requires a documented emulator/device configuration; core-wrapper, restoration, accessibility, and macrobenchmark commands must be added when their modules exist.
