# Core SDK staging directory

Release builds require `linguamesh-core-android-0.1.0-alpha.1.aar` built from the exact
`linguamesh-core` revision in `REVISION`. That revision is Core ABI major 1 and protocol version 1.
The AAR and its local checksum are ignored by Git.

Use `tools/stage-core-sdk.sh <path-to-verified-aar>` only after independently verifying provenance, ABI, protocol, generated wrapper, and dependency compatibility. The script copies the AAR and records its SHA-256; it does not authenticate the artifact.

GitHub Actions checks out the pinned Core source, builds its prerelease AAR with fixed tools,
verifies its source metadata and checksums, stages it, and then compiles, tests, and lints the release
client. This workflow is reproducible preparation, not evidence of success until the updated job
passes. No remote AAR is currently treated as an authenticated input.
