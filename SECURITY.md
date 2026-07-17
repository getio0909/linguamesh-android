# Security Policy

## Reporting a vulnerability

Do not open a public issue for an unpatched vulnerability. Use the private security-reporting channel configured on the canonical GitHub repository. If that channel is unavailable, contact the maintainers privately before disclosing details.

Include affected revision, Android version, reproduction conditions, impact, and a minimal non-sensitive proof. Never include credentials, private source text, user documents, or personal data.

## Android security requirements

- Protect encryption keys with Android Keystore and keep ciphertext in app-private storage.
- Never store plaintext API keys in DataStore, SharedPreferences, logs, diagnostics, backups, or saved instance state.
- Use the Storage Access Framework and respect URI permission lifetimes.
- Treat provider output, imported settings, intents, locale data, and document content as untrusted.
- Require HTTPS for remote endpoints; permit cleartext only for explicitly configured loopback development endpoints.
- Keep production credentials out of CI and public-fork workflows.

No release is currently supported. Security support windows will be documented with the first published application version.
