# Releasing

No stable Android release is currently verified or publishable. The Linux-first prerelease
`v0.1.0-alpha.2-linux.5` contains unsigned Android artifacts; debug builds use an unavailable-Core
implementation and are validation artifacts only.

## Hosted signing smoke

Workflow `30305452239` generated a runner-local PKCS#12 key, signed a copy of the release APK and
AAB, verified the APK with `apksigner` and the AAB with `jarsigner`, then deleted the temporary
directory. This is packaging-format evidence only and does not authorize production signing,
distribution, or stable promotion.

## Core staging gate

Release work requires `linguamesh-core-android-0.1.0-alpha.1.aar` built from Core revision
`06813081669e36b6feec8a231cd9a53eaf643671`, with ABI major 1 and protocol version 1. After
independently verifying provenance, metadata, checksum, generated wrapper, and Protobuf runtime,
stage it locally with:

```sh
./tools/stage-core-sdk.sh /absolute/path/to/linguamesh-core-android-0.1.0-alpha.1.aar
```

The script copies the ignored artifact and writes a portable local SHA-256 file; it does not prove
provenance or compatibility by itself. CI checks out the exact Core revision, installs NDK
28.2.13676358, Rust 1.93.0, and Gradle 9.5.0, builds the AAR, verifies `SHA256SUMS` and
`build-metadata.json`, stages the AAR, and then runs release assembly, unit tests, and lint. Do not
treat this definition as release evidence until the workflow passes. Workflow `30099769434` is the
current successful evidence for the pinned Core revision; no remote AAR is used as an input.

## Release requirements

Before publishing any prerelease:

1. Compile and test the release wrapper against the pinned Core artifact.
2. Run debug and release lint, unit, instrumentation, accessibility, restoration, and packaging smoke tests.
3. Review dependencies, licenses, permissions, backup exclusions, signing configuration, and credential data flow.
4. Produce checksummed APK and AAB artifacts with signing credentials kept outside source and public-fork CI.
5. Record versions, compatibility, checksums, rollback impact, and localization revision in `linguamesh-project/release-manifest.toml`.

Mark prereleases clearly and never publish an unverified stable version.
