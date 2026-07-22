# Contributing

Read `GLOBAL_GOAL.md`, `REPOSITORY_ROLE.md`, and `AGENTS.md` before proposing changes.

## Change process

1. Inspect `git status --short` and preserve unrelated work.
2. Explain affected Android APIs, Core/localization compatibility, permissions, privacy, accessibility, and lifecycle behavior.
3. Keep changes native and focused; do not move provider or document behavior into Kotlin.
4. Synchronize only the pinned localization checkout with `./tools/sync-l10n.sh --check`.
5. Run the foundation check and every applicable debug command in `docs/testing.md`.
6. Record exact evidence and unresolved release limits in `IMPLEMENTATION_STATUS.md`.

Use short imperative commit subjects with an optional scope, for example `android: harden cancellation recovery`.

Pull requests must describe user-visible behavior, tests, Core and localization revisions, permission or data-flow changes, rollback impact, and exact validation results. Include screenshots for UI changes and accessibility evidence where applicable. Never include credentials or private user content.
