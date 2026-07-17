# LinguaMesh for Android

Native Android client for LinguaMesh, using Kotlin, Jetpack Compose, Android platform APIs, coroutines, Flow, and the generated LinguaMesh Core Android wrapper.

## Current status

This checkpoint contains repository policy, architecture, and foundation validation only. It does not contain a Gradle project, Android application, JNI library, UI, package, or runnable build.

## Repository boundaries

- Own Android UI, accessibility, lifecycle, file selection, secure credential storage, notifications, and background-work integration here.
- Consume shared translation and document behavior through the generated core wrapper.
- Do not implement provider HTTP clients, document codecs, or shared persistence in Kotlin.
- Keep localization source messages in `linguamesh-l10n`.

Read [GLOBAL_GOAL.md](GLOBAL_GOAL.md), [REPOSITORY_ROLE.md](REPOSITORY_ROLE.md), and [docs/architecture.md](docs/architecture.md) before contributing.

## Current validation

No Android SDK or external dependency is required for the foundation checkpoint.

```sh
./tools/check-foundation.sh
```

Android setup, format, lint, test, and build commands are currently unavailable because no Gradle wrapper or project exists. Planned commands are labeled in [docs/testing.md](docs/testing.md).

## License

MIT. See [LICENSE](LICENSE).
