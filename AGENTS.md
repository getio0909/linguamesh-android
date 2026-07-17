# LinguaMesh Android Instructions

## Required reading

Before changing this repository, read `REPOSITORY_ROLE.md`, `GLOBAL_GOAL.md`, `IMPLEMENTATION_STATUS.md`, and the relevant files under `docs/`.

## Scope

This repository owns the native Android client. Use Kotlin, Jetpack Compose, Material 3, coroutines, Flow, Android platform APIs, and only the generated LinguaMesh Core Android wrapper for core interop. Do not add a shared UI framework, provider networking, document parsing, or raw JNI use outside the future bridge module.

## Workflow

1. Inspect `git status --short` and preserve user changes.
2. Confirm the pinned global-goal and core compatibility revisions.
3. Record uncertain decisions with `Assumption:`.
4. Implement the smallest complete native vertical slice with tests.
5. Run every available command in `docs/testing.md`.
6. Update `IMPLEMENTATION_STATUS.md` with exact evidence.

## Android rules

- Keep immutable screen state and unidirectional event flow.
- Keep network, database, document, and core polling work off the main thread.
- Store credentials with Android Keystore-protected encryption; never store plaintext secrets in DataStore or preferences.
- Use the Storage Access Framework for user documents and preserve source files.
- Use WorkManager and foreground services only according to Android lifecycle rules.
- Use generated resources from `linguamesh-l10n` and preserve runtime locale switching.
- All code comments must be Simplified Chinese on separate lines above the code they describe.
- All console, log, diagnostic, and command-line output strings must be English.

Do not claim build, packaging, background restoration, accessibility, or secure-storage behavior without tests and reproducible evidence.
