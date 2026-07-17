# Architecture

## Intended client structure

The Android client will use Kotlin, Jetpack Compose, Material 3, coroutines, Flow, and Android platform APIs. Screens will expose immutable state and unidirectional events. A single tested core-bridge module will consume the generated Android wrapper; raw JNI calls must not spread through the application.

The native layer owns UI, accessibility, lifecycle, secure credential resolution, file selection and leases, clipboard/share integration, notifications, WorkManager scheduling, and foreground-service behavior. The shared Rust core owns providers, routing, translation, documents, shared persistence, and typed command/event semantics.

Core event polling, file work, and network work must run outside the main thread, with normalized state dispatched to Compose. Secrets must cross the host-service boundary only for the intended operation and must never enter normal UI state or persistence.

## Current boundary

No Gradle project, application module, wrapper, or runtime code exists at this checkpoint. This document defines constraints and does not claim an Android implementation.
