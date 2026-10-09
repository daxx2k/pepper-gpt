# Credential handling

No API key, robot password or signing key belongs in this repository or in a distributed APK. API keys and optional SSH credentials are entered at runtime in Settings. Android backup is disabled. They currently remain in app-private SharedPreferences, rather than hardware-backed encrypted storage; use only on a trusted robot and device.

SSH is disabled until connection credentials and a verified known_hosts entry are supplied. Host-key checks are required. Verify the key through a trusted connection before copying it into Settings.

Never distribute legacy APKs or private recovery backups: they may contain embedded credentials even when the source is clean. If a credential has been shared, committed or included in a distributed artifact, rotate it in the relevant provider account or on the robot. Deleting a file does not revoke a credential. This repository and the sanitized release APK exclude runtime settings, conversations, logs, private backups and signing material. Release checks inspect all decompressed APK entries, including DEX strings and binary resources, rather than checking source alone.

The main Android launcher remains exported as required for launching. The downloadable beta has debugging and Android backup disabled. The application still permits cleartext traffic for existing radio streams; use a trusted network. Old dependencies and Android 6 require additional maintenance before treating this as a hardened product.
