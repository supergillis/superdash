# Third-Party Notices

superdash bundles or redistributes the third-party components below. This is a
summary for convenience: the authoritative, full license texts ship with each
dependency (Maven artifact) or live alongside the vendored code and model
files in this repository (see the paths noted below). superdash itself is
licensed under GPL-3.0 (see [LICENSE](LICENSE)).

Gradle-resolved dependencies are listed by family; transitive dependencies of
these libraries (e.g. OkHttp, Kotlin stdlib, kotlinx-coroutines) carry the
same Apache-2.0 licensing unless noted.

## Apache License 2.0

- AndroidX libraries (core-ktx, appcompat, activity-compose, Compose UI /
  Material 3, DataStore, WebKit, Lifecycle, Media3) — Google —
  <https://github.com/androidx/androidx>
- Kotlin standard library and kotlinx libraries (kotlinx-serialization,
  kotlinx-datetime, kotlinx-collections-immutable, kotlinx-coroutines) —
  JetBrains — <https://github.com/JetBrains/kotlin>,
  <https://github.com/Kotlin>
- Ktor (client-okhttp, websockets, content-negotiation, auth,
  serialization-kotlinx-json, network) — JetBrains —
  <https://github.com/ktorio/ktor>
- TensorFlow Lite (org.tensorflow:tensorflow-lite) — Google —
  <https://github.com/tensorflow/tensorflow>
- Google Tink (tink-android; used for the ESPHome Noise NNpsk0 handshake
  primitives) — Google — <https://github.com/tink-crypto/tink-java>
- Coil 3 (coil-compose, coil-network-okhttp) — Coil Contributors —
  <https://github.com/coil-kt/coil>
- OkHttp (transitive, via Ktor and Coil) — Square —
  <https://github.com/square/okhttp>
- TensorFlow Lite Microfrontend (vendored audio feature-generation code) —
  Google — <https://github.com/tensorflow/tflite-micro> — license text at
  `packages/voice/src/main/cpp/third_party/tflite_microfrontend/LICENSE`
- "hey_jarvis" micro-wake-word model (bundled) — ESPHome
  micro-wake-word-models —
  <https://github.com/esphome/micro-wake-word-models> — license text at
  `packages/app/src/main/assets/models/wakeword/LICENSE.esphome-micro-wake-word-models`

## MIT License

- Moonshine tiny-en speech-to-text model (bundled) — Useful Sensors, Inc.
  (dba Moonshine AI) — <https://github.com/usefulsensors/moonshine> —
  license text at
  `packages/app/src/main/assets/models/moonshine/LICENSE.moonshine`
  (note: only English Moonshine models are MIT upstream)
- Moonshine Voice SDK (ai.moonshine:moonshine-voice) — Useful Sensors, Inc. —
  <https://github.com/moonshine-ai/moonshine> — MIT (verify against the
  published artifact)
- android-vad WebRTC (com.github.gkonovalov.android-vad:webrtc) — Georgiy
  Konovalov — <https://github.com/gkonovalov/android-vad> — wraps the WebRTC
  VAD, which is BSD-3-Clause (WebRTC project)
- whisper.cpp (fetched into `packages/voice/src/main/cpp/whisper.cpp` at
  build time and compiled into the app) — The ggml authors —
  <https://github.com/ggml-org/whisper.cpp>
- OpenAI Whisper tiny.en model weights (bundled as
  `packages/app/src/main/assets/models/whisper/ggml-tiny.en-q5_1.bin`,
  converted to GGML format) — OpenAI —
  <https://github.com/openai/whisper>

## BSD 3-Clause License

- Protocol Buffers (protobuf-kotlin-lite runtime and the vendored
  `descriptor.proto` at
  `packages/esphome-server/src/main/proto-include/google/protobuf/descriptor.proto`)
  — Google — <https://github.com/protocolbuffers/protobuf>
- KISS FFT (vendored) — Mark Borgerding —
  <https://github.com/mborgerding/kissfft> — license text at
  `packages/voice/src/main/cpp/third_party/kissfft/COPYING`

## Other

- The ESPHome native API protocol definitions implemented by the
  `esphome-server` package are based on the ESPHome project
  (<https://github.com/esphome/esphome>), which is licensed GPL-3.0 for
  Python sources and ESPHome License (MIT-based) for C++/runtime components;
  superdash's implementation is original GPL-3.0 code.

If you find an error or omission in this list, please open an issue.
