# Repository Role

`linguamesh-android` is the native Android client repository for LinguaMesh.

It is responsible for:

- Kotlin and Jetpack Compose UI;
- Android navigation, accessibility, lifecycle, theme, and locale behavior;
- Android Keystore-backed credential handling;
- Storage Access Framework file leases;
- clipboard, sharing, notifications, WorkManager, and foreground-service integration;
- JNI wrapper consumption, Android packaging, and distribution.

Provider adapters, routing, translation rules, document codecs, shared SQLite data, and command/event semantics belong to `linguamesh-core`. Canonical UI messages belong to `linguamesh-l10n`.

The current foundation does not claim a buildable or runnable Android application.
