# Architecture

## Application boundary

`LinguaMeshApplication` owns an application-scoped `AppContainer`. The container provides one `CoreGateway`, an Android Keystore credential broker, and a DataStore-backed UI preferences repository. Activity and ViewModel recreation must not close the application-owned gateway.

`TranslationViewModel` exposes immutable `StateFlow` state and accepts unidirectional UI events. Provider registration, credential persistence, Core polling, and cancellation requests run away from the main thread. A stream that ends without `completed`, `cancelled`, or `failed` is treated as a protocol failure.

## Core boundary

`CoreGateway` is the only app-facing Core contract. Debug builds bind `UnavailableCoreGateway`, so the UI can be built and tested without claiming translation capability. Release builds bind `NativeCoreGateway` from `app/src/release/` and require the exact staged AAR named in `core-sdk/README.md`.

`core-sdk/REVISION` pins Core commit `81f04be8bff338a1df6409ba564ddca3ad0bddf3`, ABI major 1,
and protocol version 1. CI builds the AAR from that source instead of resolving a mutable or
unverified binary dependency. `CoreResult.RESOURCE_EXHAUSTED` is mapped to a safe protocol failure.

The release adapter validates operation identity, correlation identity, and increasing sequence numbers. Coroutine cancellation sends a Core cancellation request and drains the cancelled operation on `Dispatchers.IO` for a bounded interval. If a matching terminal event cannot be confirmed, the gateway is isolated and subsequent calls fail with a safe English protocol diagnostic.

## Security and localization

Provider secrets never enter `TranslationUiState` or provider profiles. `AndroidKeystoreCredentialStore` encrypts each value with AES-256-GCM, binds ciphertext to its secret reference with additional authenticated data, clears mutable buffers, and excludes app data from backup and device transfer. The release adapter resolves the typed Core `SecretRequired` event through this broker and sends one bounded response; credential bytes never enter UI state, DataStore, diagnostics, or logs.

Canonical UI strings are copied from the exact Git revision in `l10n/REVISION`. `tools/sync-l10n.sh` rejects a different or dirty source checkout and stale destination resources. Locale changes use configuration contexts and platform layout-direction resolution; theme and locale preferences persist through DataStore.

Shared provider, routing, translation, document, and persistence semantics remain in `linguamesh-core`.
