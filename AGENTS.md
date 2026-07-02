# Contributor & Agent Guide

Conventions for this codebase that the tooling cannot enforce. These apply to
human contributors and AI coding agents alike. For build and run instructions,
see [README.md](README.md); for design, see [docs/architecture/](docs/architecture/).

## Documentation Style

- Keep docs short.
- Prefer bullets over long paragraphs.
- Use small tables for maps and lists.
- Do not use em dash.
- Do not keep historical notes in active docs.
- Link only to existing files.
- Use current paths under `packages/`.
- Use `superdash` for the app name.

## Code Style

### Braces

Always brace `if`, `else`, and `when` bodies.

Good:

```kotlin
if (bytes.isEmpty()) {
    return null
}
```

Bad:

```kotlin
if (bytes.isEmpty()) return null
if (bytes.isEmpty()) { return null }
```

For expression values, split to multiline form.

```kotlin
val colors =
    if (darkTheme) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
```

### Names

Use descriptive names.

Avoid:

- `v`
- `q`
- `s`
- `k`

Allowed:

- Math coordinates like `x` and `y`.
- Loop indexes like `i` and `j`.
- Type parameters like `T`, `R`, `K`, `V`.
- `it` when the lambda is clear.
- `e` or `t` for caught throwables.

### Tests

Use backtick test names with spaces.

Good:

```kotlin
@Test fun `long pause after wake times out`() {}
```

Bad:

```kotlin
@Test fun long_pause_after_wake_times_out() {}
```

### Comments

Default to no comment.

Write a comment only for:

- Hidden constraints.
- Workarounds.
- Non-obvious why.
- Behavior that may surprise a reader.

Do not restate the code.

### JSON

Prefer typed `@Serializable` data classes.

Use `JsonElement` only for dynamic shapes.

Examples:

- HA entity attributes.
- Pass-through payloads.
- Unknown framed payloads.

## Logging

Use one `Log` per file that logs.

```kotlin
private val log = Log("MyClass")
```

Use structured fields when there is more than one data point.

```kotlin
log.i("seeded entities", "count" to count)
log.w("connection failed", null, "reason" to reason)
log.w("auth invalid", throwable)
```

MUST use structured fields instead of interpolating key/value details into the message.

Good:

```kotlin
log.i("onReceive", "action" to action)
```

Bad:

```kotlin
log.i("onReceive action=$action")
```

For `w` and `e`, the throwable is the second positional argument.

Pass `null` when only fields are present.

## Compose

Split multi-flow screens into smart and dumb halves.

Smart wrapper:

- Named `<Foo>Screen`.
- Collects flows.
- Owns effects.
- Builds UI state.
- Talks to repositories and clients.

Dumb body:

- Named `<Foo>Content`.
- Takes `state: <Foo>UiState`.
- Takes event lambdas.
- Does not read `AppGraph`.
- Does not collect `Flow`.
- Should be preview friendly.

Split once a screen has:

- More than one collected flow.
- A `LaunchedEffect` tied to domain state.
- Multiple states worth previewing.

## Naming

New classes MUST use one of the codebase's existing nouns. Do not introduce foreign vocabulary (`Presenter`, `Manager`, `Service`, `Helper`, `Util`, `Handler`) without a documented reason.

- `<Foo>ViewModel` — top-level UI state holder backed by `viewModelScope` (e.g., `MainViewModel`, `SettingsViewModel`).
- `<Foo>Controller` — long-lived state owner that subscribes to inputs and exposes a `StateFlow` (e.g., `SleepController`, `ScreensaverIdleController`, `KioskWindowController`, `DoorbellOverlayController`).
- `<Foo>Coordinator` — orchestrator across components for a multi-step domain flow (e.g., `VoicePipelineCoordinator`).
- `<Foo>Watcher` / `<Foo>Detector` — pure observer of domain inputs that emits typed facts (e.g., `DoorbellWatcher`, `VadSpeechDetector`). Owns no UI-shaped state. See `KioskEventBus` discipline in `docs/architecture/voice.md`.
- `<Foo>Repository` — single owner of persisted state for a domain (e.g., `VoiceModelRepository`, `VoiceCommandRecordingRepository`).
- `<Foo>Screen` / `<Foo>Content` — Compose smart/dumb split, see the section above.
- `<Foo>State` — sealed UI state type (e.g., `DoorbellState`, `VoiceState`).
- `<Foo>Overlay` — Compose overlay composable (e.g., `DoorbellOverlay`).

If none of these fit, propose the new noun in PR review before adding it.

## ktlint

- `./gradlew ktlintCheck` gates checks.
- `./gradlew ktlintFormat` can rewrite files.
- Do not add new rule disables without a one-line reason.
- Production line length is 120.
- Test source sets may use longer fixtures.

## Architecture Rules

### Wake Word

Preserve these invariants in `MicroWakeWordRunner.kt`:

- Output is `uint8`.
- Read bytes with `.toInt() and 0xFF`.
- Inference stride is `FRAMES_PER_INFERENCE`.

### Native Audio Frontend

superdash owns the wake-word audio feature frontend. It is built from vendored
TensorFlow Lite microfrontend and KISS FFT sources; do not add a prebuilt `.so`.

Relevant files:

- `packages/voice/src/main/kotlin/com/superdash/voice/features/AudioFeatureExtractor.kt`
- `packages/voice/src/main/cpp/CMakeLists.txt`
- `packages/voice/src/main/cpp/superdash_audio_features_jni.cpp`
- `packages/voice/src/main/cpp/third_party/` (vendored TensorFlow Lite microfrontend + KISS FFT)

### Settings

Settings setters use `value` as the parameter name.

Each feature owns a typed `XSettings` view backed by `KeyValueStore`. Defaults live in the feature's `XSettings`. `SettingsRepository` holds only cross-cutting glue (`haUrl`, `snapshot()`).

Keep defaults and consumers aligned. Do not add feature-type imports to `SettingsRepository`.

### Home Assistant WebSocket

Use one `HaWebSocketClient` per app.

It is owned by `AppGraph`.

Use:

- `state`
- `entities`
- `observeEntity()`
- `rawFrames`
- `callResult()`

Do not create extra WebSocket clients.

## Testing & Workflow

- Prefer focused module tests over a full `./gradlew assembleDebug` during refactors.
- Do not play audio through host speakers in tests; inject audio via file fixtures.
- Run instrumentation tests on an emulator target by default. Instrumentation
  installs can reset app data and wipe local auth/settings on a real device.
- Android instrumentation test method names must be dex-safe camelCase. Do not
  use backtick names with spaces in `src/androidTest`.
- Prefer upgrade install paths that preserve app data.
