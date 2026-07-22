# Releasing

No Android release is currently verified or publishable. Debug builds use an unavailable-Core implementation and are validation artifacts only.

## Core staging gate

Release work requires `linguamesh-core-android-0.1.0-alpha.1.aar` built from Core revision
`8837e59395742b5385af5037aa36a2596af3b025`, with ABI major 1 and protocol version 1. After
independently verifying provenance, metadata, checksum, generated wrapper, and Protobuf runtime,
stage it locally with:

```sh
./tools/stage-core-sdk.sh /absolute/path/to/linguamesh-core-android-0.1.0-alpha.1.aar
```

The script copies the ignored artifact and writes a portable local SHA-256 file; it does not prove
provenance or compatibility by itself. CI checks out the exact Core revision, installs NDK
28.2.13676358, Rust 1.93.0, and Gradle 9.5.0, builds the AAR, verifies `SHA256SUMS` and
`build-metadata.json`, stages the AAR, and then runs release assembly, unit tests, and lint. Do not
treat this definition as release evidence until the workflow passes. No remote AAR is currently an
authenticated or verified input.

## Release requirements

Before publishing any prerelease:

1. Compile and test the release wrapper against the pinned Core artifact.
2. Run debug and release lint, unit, instrumentation, accessibility, restoration, and packaging smoke tests.
3. Review dependencies, licenses, permissions, backup exclusions, signing configuration, and credential data flow.
4. Produce checksummed APK and AAB artifacts with signing credentials kept outside source and public-fork CI.
5. Record versions, compatibility, checksums, rollback impact, and localization revision in `linguamesh-project/release-manifest.toml`.

Mark prereleases clearly and never publish an unverified stable version.
