# Restored integration packaging

The downloadable 2.7.1 beta is a sanitized packaging of the restored 2.7.x integration. It is separate from the Gradle development baseline in `Android/PepperGPT`; building that baseline does not reproduce this APK. A complete source-only reproduction of the restored integration is not yet available.

`ReleaseSettings.java` supplies optional SSH configuration from runtime preferences. SSH remains disabled until host, user, password and a verified `known_hosts` entry are supplied. Connections require `StrictHostKeyChecking=yes`. Restart the app after changing SSH settings. Native speech, gestures and awareness use QiSDK.

`sanitize-integration.py` takes the path to an already prepared decoded restored integration. It deliberately checks the expected class layout and stops if that baseline differs. It removes legacy OpenAI/OpenWeather defaults from application classes and BuildConfig, removes embedded robot login defaults, adds the runtime settings hook, and disables Android backup and debugging in the existing binary manifest. It changes the manifest version to 2.7.1 / code 90.

Packaging procedure for maintainers:

1. Work on an isolated copy of the restored integration. Never commit or redistribute its unsanitized input.
2. Compile `ReleaseSettings.java` with Java 8 compatibility against Android SDK 28 and the existing JSch API. Convert it with D8, minimum API 23, and include its generated smali in the application DEX.
3. Run `python sanitize-integration.py <decoded-directory>` once on that copy, then rebuild with Apktool 2.11.1.
4. Preserve original resources, ARMv7 native libraries and dependency DEX. Replace only the application DEX and patched manifest, remove obsolete signatures, align and sign using a privately held signing key. Never publish that key.
5. Audit every decompressed APK entry for credential patterns, known legacy/private credential values in UTF8/UTF16, personal identifiers/paths/addresses and private files. Verify the package, version, API level, ABI and signatures. Compare all other ZIP entries with the baseline.
6. Publish the sanitized signed APK and SHA256 checksum as a beta until fresh on-device validation is complete.

The release is signed with the existing integration certificate so compatible installations can be updated. A build signed with a different certificate cannot be updated in place. Back up any needed settings/history before changing installation baselines; uninstalling erases app data. Never force-stop a robot-focused app: first return to the tablet home screen and let QiSDK release robot focus.
