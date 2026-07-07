# Decisions

Current decisions that prevent repeated mistakes.

## Screensaver

- Use an in-Activity Compose overlay.
- Do not use `DreamService`.
- Keep the WebView mounted under overlays.
- Avoid screensaver APIs that pause or cover the Activity.
- Keep screensaver state in Compose effects where possible.

## Foreground Services

- Use `KioskService` for kiosk lifetime.
- Use `VoiceService` for microphone capture.
- Keep voice separate because it needs microphone service type.
- Do not add extra foreground services without a lifecycle reason.

## WebView

- Keep renderer priority high during transient invisibility.
- Grant WebView permissions only for the configured HA origin.
- Do not rewrite HA HTML.
- Do not strip security headers.
- Do not add fake host routing.

## Voice

- Use `VoiceService` as a real microphone foreground service.
- Use Media3 ExoPlayer for TTS.
- Use superdash-owned `com.superdash.voice.features.AudioFeatureExtractor` for wake-word features.
- Build `libsuperdash_audio_features.so` through Gradle/CMake.
- Keep wake-word and VAD inference on a single worker lane.
- Do not add a JVM unit test for JNI loading.

## Home Assistant Control

- ESPHome native API is the current control surface.
- Keep REST and broker-based control out of active docs and code.
- Add ESPHome entities through `EsphomeBindings`.
- Keep entity IDs stable once exposed.

## Settings

- Use typed `Setting<T>` wrappers.
- Keep defaults beside setting declarations.
- Use UI-driven updates for device tests.
- Do not edit DataStore files by hand.
