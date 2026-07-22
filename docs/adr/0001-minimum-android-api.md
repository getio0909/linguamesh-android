# ADR 0001: Minimum Android API

Status: Accepted for the first prerelease

## Context

The client needs Android Keystore AES keys, modern Compose support, current WorkManager, CI emulator coverage, and a maintainable security baseline. Lower API levels add compatibility branches without improving the core protocol.

Assumption: API 26 provides sufficient initial device coverage for a developer prerelease. Actual user coverage must be measured before 1.0.

## Decision

Set `minSdk` to 26, `compileSdk` and `targetSdk` to 36, and compile with Java 17. Revisit the minimum before the first stable release using supported-device and security-update data.

## Consequences

Android 7.1 and older are unsupported. Keystore AES/GCM is available without legacy fallback. CI must build on API 36 and run instrumentation on an API 26-or-newer emulator.
