# Third-party components

Android dependencies remain declared in `Android/PepperGPT/app/build.gradle`, including SoftBank Robotics QiSDK, AndroidX, OkHttp, JSch and Picasso. Their upstream licenses and any robot animation redistribution rights must be respected when publishing or distributing binaries.

Offline Cori uses the unmodified sherpa-onnx Android TTS engine, version 1.10.1, ARMv7, downloaded separately:
- Engine source: https://github.com/k2-fsa/sherpa-onnx/tree/v1.10.1/android/SherpaOnnxTtsEngine
- Engine distribution: https://huggingface.co/csukuangfj/sherpa-onnx-apk
- Piper Cori model card: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_GB/cori/medium

The model is `en_GB-cori-medium`, UK English female, medium quality. The selected APK supports API 21 and ARMv7; Pepper runs API 23. The upstream engine and model are not included in this source tree. Existing illustration/editor assets are preserved; this document does not assert new ownership or grant a new license.
