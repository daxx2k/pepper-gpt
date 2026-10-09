# Code review and validation

Scope: Android application sources, feature routing, microphone/WAV handling, speech ownership and cancellation, settings, robot lifecycle, and publication content. This is not an exhaustive dependency vulnerability audit.

## Corrections

- Development credentials are runtime settings rather than built-in API or SSH secrets. Android backups are disabled and SSH requires a supplied verified host key.
- Start Listening and Stop Listening have distinct green and red states. A stopped recording cannot launch a fresh transcription or restore a stale listening callback.
- WAV length fields are finalized before upload. Short filler filtering matches whole utterances rather than discarding legitimate questions by substring.
- Speech cancellation respects focus loss and late asynchronous callbacks. External streamed audio cancels network operations off the UI thread and restores temporary tablet volume changes.
- External speech owns a muted native Say action for contextual gestures. Cancelling speech also cancels that action; it introduces no raw joint targets or global motor/stiffness overrides.
- Italian routing recognizes request verbs and feature nouns with intervening articles and descriptive words. It preserves original prompts and retains the original story/image/recipe/radio/weather generators.
- Language instructions preserve each feature's output contract. Stories must not contain illustration markers; recipe prompts retain their required marker. Female OpenAI voices use feminine grammatical agreement for Pepper without changing other characters or users.

## Baseline distinction

The development Gradle application and the restored stable APK integration are separate implementations. The current integration source is in `tools/stable-voice`; it is not yet connected to the Gradle application. No private stable APK or embedded credentials are published.

## Remaining limitations

- Android 6 and the old dependency set require maintenance; API 23 and ARMv7 compatibility must be checked on the actual tablet.
- Runtime credentials currently use app-private SharedPreferences rather than hardware-backed encrypted storage. HTTP remains enabled for existing radio streams.
- The on-device routing matrix is not a full physical test of all modules. Spoken recognition, repeated conversations, all voice choices, punctuation, touch interruption, gestures, radio/weather playback and full illustrated scene progression need physical regression checks on any new build.
- The source-only repository does not promise a hardened product or validated robot posture. Existing asset and dependency rights are not reassigned.

## Current checks

The published development project passed `assembleDebug` and its five narration unit tests and five model-selection unit tests. On the restored Android 6 tablet, 24 original feature predicates, 25 negative conversation checks and 10 weather/time/city checks passed. A real Italian request reached illustrated Story Mode; its complete API narrative was retained in display and scene text, with a valid 1024 by 1024 cached first-scene image. Three API paragraphs were grouped into two scenes by the original helper, whose grouping behavior was retained. This establishes routing and that story pipeline; it does not establish all physical module, microphone, voice, gesture or touch regressions.

## Follow-up conversation and recipe preview

Conversation instructions now answer ongoing exchanges directly, overriding repetitive greeting/activity-offer patterns without removing requested story, joke or other feature behavior. Provider checks with an intentionally conflicting personality and prior assistant greeting passed two factual follow-up turns in Italian and English, without greetings, unsolicited offers or routine closing questions.

Recipe chat previews use 250 by 250 dp instead of 500 by 500 dp, with 16 dp rounded corners via API 21+ outline clipping. RecyclerView binding restores the original size and clipping for other image types. Original image files, recipe activity and tap callbacks are preserved. The change is implemented in the development source and the stable integration helper/patch.

A stable app's active robot session must be released normally before an update. Abruptly ending an instrumented process left a stale remote focus on the test robot; restarting the app and tablet connection service did not recover it. A normal robot reboot recovered focus, and the user confirmed working narration afterward. Subsequent updates use Home, wait for non-robot focus, then install and restart. Native BasicAwareness was verified enabled; physical face following remains a human check. No raw joint control or global stiffness/posture changes were introduced by these updates.


## Model settings and toolbar volume

Settings exposes independent runtime model IDs for chat, creative text and image analysis. Original defaults and legacy request parameters remain unchanged without a custom selection. Tests check default request preservation, independent overrides, vision routing, unchanged messages/token budgets, empty-value defaults and isolation from audio/image-generation models. New-model compatibility is not established merely by accepting its ID; custom models use their API reasoning default.

The stable toolbar has persistent voice-volume controls. Streamed voices use tablet media volume; native Pepper speech applies the chosen volume to utterances while the gesture-only native action remains muted. Device settings and conversation history are preserved during deployment. Recipe previews and full-image opening were checked on the tablet. A human confirmed speech/gestures and face following on the preceding build; this is not a complete physical regression of the latest build.

The settings dialog was exercised on the Android 6 tablet: all three original model IDs were visible; Cancel and Save of unchanged defaults preserved complete preferences and conversation history. Deployment preserved all APK entries except the modified application DEX. Robot focus returned to the main application.


## Offline voice removal

The user requested removal of the slow local voice. Its engine initializer, provider branch, selector entry, development demo and installer are removed. The shared request chunker remains for streamed OpenAI speech under a generic name. OpenAI callbacks, first-audio gesture timing, native speech, touch ownership and cancellation are retained. Private recovery copies remain outside the public repository.


## Natural radio requests

Italian radio routing also recognizes requests to make music audible (for example, asking to hear the radio) and equivalent listening phrases. Tests include these commands and negative conversational mentions of radio/music. Original player, stations, radio screen and speech/gesture helpers are unchanged. The default and pop stream endpoints returned HTTP 200 with audio/mpeg data from the robot network during diagnosis; this is not proof of audible tablet playback.


## Semantic radio fallback

A neutral, separate intent request uses the configured chat model for unfamiliar audio-related wording; ordinary conversation does not receive new tool schemas or response-format changes. Only validated play/stop commands and existing default/pop/synthwave selections reach the existing handler. No model or preference migration is performed. Normal playback handlers, all other feature handlers, gestures, voice streaming and private history remain untouched. API tests with the current model covered 7 playback/stop requests and 5 non-action cases, including imperfect transcription, indirect wording, factual discussion, past events and negation. These passed with low reasoning effort in the classifier; the normal chat effort is unchanged. The user physically confirmed that an indirect Italian request opened the radio screen and produced audible music. Other playback controls and all other physical modules were not re-tested in this change.
