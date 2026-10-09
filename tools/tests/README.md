# Stable integration runtime checks

`FeatureProbe.java` is a temporary Android instrumentation runner for the privately restored stable app with the integration helpers installed. It is not part of the production application and is not a test of an unmodified development build.

It calls the original feature predicates using 24 Italian/English requests, checks 25 ordinary-conversation cases, and verifies 10 city/weather/temperature/time results. Passing `-e story true` additionally submits an Italian story request through the real dispatcher and validates story mode, content, original scene groups and a cached image. That option uses the configured API key and can start speech and native gestures. Remove the temporary test package after use; never publish an APK that contains private app data.

The original scene helper groups sentences into up to three scenes, so three API paragraphs can become two displayed groups. The validation preserves this behavior rather than imposing an unrelated change to scene grouping.

Compile against Android SDK 28 with Java 8 bytecode, package this manifest with aapt and D8 (`--min-api 23`), and sign with the same debug key as the local target. Use Android instrumentation for package `com.softbankrobotics.pepper.pepperGPT.validation/.FeatureProbe`. Signing keys, APKs, device settings and runtime evidence remain private.
