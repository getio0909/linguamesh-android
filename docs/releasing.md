# Releasing

No Android package can be produced from the foundation checkpoint.

A future release must pin a compatible core AAR, ABI, protocol, provider-catalog, and localization version. CI must run formatting, static analysis, unit and instrumentation tests, localization validation, core compatibility checks, release assembly, dependency/license review, and packaging smoke tests.

Produce checksummed APK and AAB artifacts. Keep signing credentials outside source and public-fork CI, and do not claim an artifact is signed unless verified. Record the application version and checksums in the central `linguamesh-project/release-manifest.toml`. Mark prereleases clearly and never publish an unverified stable version.
