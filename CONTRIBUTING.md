# Contributing

Read `GLOBAL_GOAL.md`, `REPOSITORY_ROLE.md`, and `AGENTS.md` before proposing changes.

## Change process

1. Inspect `git status --short` and preserve unrelated work.
2. Explain affected Android APIs, core/localization compatibility, permissions, privacy, accessibility, and lifecycle behavior.
3. Keep changes native and focused; do not move core behavior into Kotlin.
4. Run `./tools/check-foundation.sh` at the current checkpoint.
5. Once the Gradle project exists, run every available command in `docs/testing.md` on a supported toolchain.
6. Record actual build and test evidence in `IMPLEMENTATION_STATUS.md`.

Use short imperative commit subjects with an optional scope, for example `android: define provider screen state`.

Pull requests must describe user-visible behavior, tests, core and localization versions, permission or data-flow changes, rollback impact, and exact validation results. Include screenshots for UI changes and accessibility evidence where applicable. Never include credentials or private user content.
