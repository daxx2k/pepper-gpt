# pepper-gpt

Android software for SoftBank Pepper, preserving voice conversation, illustrated stories, recipes, photo transformations, generated images, weather, internet radio, personality settings and conversation history.

## Source baselines

`Android/PepperGPT` is the cleaned development application (2.8.x), with runtime configuration and native Pepper speech. `tools/stable-voice` contains the current source for the separate restored 2.7.x integration: streamed OpenAI Coral/Marin speech, touch cancellation, native speech gestures, and ENG/ITA conversation language support. These integration helpers are not wired into the development Gradle app. Building the development project does not reproduce the privately patched stable app. The integration uses `ModelSettings.java` from the development source as a shared helper. Legacy APKs are excluded because they may embed credentials.

The language switch changes recognition, conversation and voice; menus remain in English. Italian feature requests are translated only for routing, while generators receive the original request. Stories retain narrative-only output; recipes retain their required illustration marker. Optional OpenAI voices require Internet and use the configured OpenAI key; no PC is needed during normal use.

The green launcher badge is an RGBA PNG with a transparent exterior, without a black tile or glow. The Android 6 launcher asset is exported at 192 pixels for hdpi.

The stable toolbar provides voice-volume minus/plus controls and a percentage after the language flag. Recipe chat previews have half the original width and height and rounded corners; tapping still opens the full image.

## Build the development application

Pepper targets Android 6.0 (API 23), 32-bit ARMv7. Use Java 11, Gradle 7.2, Android Gradle Plugin 7.1.3, Kotlin 1.4.0, SDK 28 and Build Tools 30.0.3. Open `Android/PepperGPT` in Android Studio, or copy its `local.properties.example` to `local.properties` and set your own SDK path.

Run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` from `Android/PepperGPT`. The ARMv7 APK output targets Pepper; x86 is not the tablet ABI. Build outside a synchronized directory if generated files are locked.

## Configuration

In Settings > AI models, enter exact model IDs separately for chat, stories/recipes and image analysis. Defaults remain `gpt-5-nano`, `gpt-5-mini` and `gpt-5-mini`; adding the controls does not migrate existing installations. Save commits all three fields together. Cancel discards edits; Restore defaults fills the original IDs for review before Save. Blank fields use the original defaults.

Custom models must support Chat Completions (and image input for analysis), and be available to the configured API account. Custom IDs use the selected model's default reasoning setting instead of inheriting legacy model-specific values. A new API contract may still require a code update. Speech and generated-image models are separate.

Enter your own OpenAI and OpenWeather keys in Settings. Optional robot SSH access requires your own connection settings and a verified SSH known_hosts entry. Credentials are runtime-only in the development source and must never be committed. Android backup is disabled.

## Validation and privacy

The development build and ten unit tests passed, including independent model selection and preservation of existing default request bodies. Routing checks cover natural Italian requests for each preserved module, negative conversation cases and city/temperature/time extraction. Runtime evidence and remaining limitations are documented in `CODE_REVIEW.md`; a successful build is not proof of physical robot behavior.

This is a source-only repository: no personal configuration, conversations, logs, private backups, credentials, signing keys or application APKs. See `SECURITY.md`. No new license is assigned to inherited code or animation assets.

The slow Cori option and its separate TTS Engine application have been removed. The restored stable integration keeps native Pepper and OpenAI Coral/Marin voices, streaming, touch interruption and contextual speech gestures.

Radio requests have a semantic fallback using the configured chat model: unfamiliar radio/music wording is classified independently of personality and old conversation history, then forwarded to the original player handler. Plain conversation and other feature APIs retain their existing request/response format. A broader topic check limits classification to audio-related requests. Unambiguous short commands stay local. Semantic interpretation can add several seconds and requires the same configured OpenAI key.
