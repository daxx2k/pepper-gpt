# PepperGPT working requirements

Preserve the user's full PepperGPT application when changing the icon or adding the optional Piper Cori voice. Keep illustrated stories, images and photo transformations, recipes, radio, weather, conversation history, personality settings, themes, and touch-to-interrupt speech. A text-only chat is not an adequate replacement.

Pepper's tablet runs Android 6.0 (API 23), ARMv7. The requested voice option must work on Pepper without a PC. Keep the native Pepper voice available. The green application icon has no glow.

Before installing an update, preserve a private recovery APK and the complete device settings. Keep API/SSH keys and private APKs outside the publishable source tree; compare private settings without printing their values. Avoid unrelated rewrites. Treat the original app's feature routing and working modules as the baseline.

Validate the requested change together with the affected existing functions. Conversation checks include two consecutive spoken turns and Stop during a recording. Speech checks include punctuation and paragraph pauses, illustrated story progression, the native/Cori selector, and interruption by an actual head or hand touch. Check radio and weather when their speech or focus handling changes. Verify that existing content/history remains available.

Distinguish source/build checks, tablet/runtime observations and the human user's physical confirmation. Do not describe the whole application as working from a successful build, APK installation or active microphone alone. Clearly state any checks still awaiting a physical test.

The development Kotlin project and the restored private 2.7.0 APK are different build baselines. Identify which is being edited and deployed. `tools/patch-*.py` and `tools/stable-voice/LegacyVoiceAdapter.java` reproduce the narrow private 2.7.0 repairs; the legacy APK embeds old credentials and must not be published.
