# Contributor & Agent Guide

A predictable place for the conventions this codebase relies on that the tooling
cannot enforce. These apply to human contributors and AI coding agents alike.

- For install and run instructions, see [README.md](README.md).
- For design and module detail, see [docs/architecture/README.md](docs/architecture/README.md).
- For contribution basics, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Project Overview

superdash is a Home Assistant kiosk app for Android tablets. It runs a Home
Assistant dashboard full-screen, stays signed in across restarts, and adds
wall-panel extras: on-device wake word and voice, an ambient photo screensaver,
and doorbell camera overlays. It can expose itself back to Home Assistant over
the ESPHome native API.

Multi-module Gradle/Kotlin build. The app module is `:packages:app`.

| Package | Purpose |
|---|---|
| `packages/app` | Android app, UI, wiring, services, settings. |
| `packages/core` | Shared logging and small utilities. |
| `packages/ha-client` | Home Assistant OAuth, tokens, WebSocket, Assist, media source. |
| `packages/voice` | Wake word, on-device STT (Whisper/Moonshine), local intents. |
| `packages/screensaver` | Screensaver and Immich photo slideshow. |
| `packages/doorbell` | Doorbell camera overlay. |
| `packages/esphome-server` | ESPHome native API server and mDNS announce. |
| `packages/immich-client` | Immich API client for slideshow photos. |
| `packages/kiosk-bus` | Internal event bus. |

Per-package orientation lives in [packages/README.md](packages/README.md).

## Environment

- JDK 17 on `PATH`.
- Android SDK installed. The build resolves NDK and CMake components as needed.
- Native code is part of the build. No prebuilt `.so` is checked in.
- `whisper.cpp` is fetched on demand and is optional. CMake builds a stub when
  it is absent, so a plain checkout builds fine without it.
- Git LFS is not required.

## Build, Test, Lint

```bash
./gradlew :packages:app:assembleDebug          # build the app
./gradlew testDebugUnitTest                     # run all unit tests
./gradlew :packages:app:testDebugUnitTest       # app module tests
./gradlew :packages:ha-client:testDebugUnitTest # ha-client module tests
./gradlew :packages:voice:testDebugUnitTest     # voice module tests
./gradlew :packages:esphome-server:testDebugUnitTest
./gradlew :packages:immich-client:testDebugUnitTest
./gradlew :packages:core:testDebugUnitTest
./gradlew ktlintCheck                            # lint check (gates)
./gradlew ktlintFormat                           # auto-format
```

## How to Verify a Change

A change is done when:

- `./gradlew ktlintCheck` passes.
- The relevant module unit tests pass.
- New behavior has a test, and it is a unit test unless it needs a device.

Prefer focused module tests over a full `./gradlew assembleDebug` during
refactors. Run `./gradlew ktlintCheck testDebugUnitTest` before opening a PR.

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

Android instrumentation test method names must be dex-safe camelCase. Do not use
backtick names with spaces in `src/androidTest`.

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

### ktlint

- `./gradlew ktlintCheck` gates checks.
- `./gradlew ktlintFormat` can rewrite files.
- Do not add new rule disables without a one-line reason.
- Production line length is 120.
- Test source sets may use longer fixtures.

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

New classes MUST use one of the codebase's existing nouns. Do not introduce
foreign vocabulary (`Presenter`, `Manager`, `Service`, `Helper`, `Util`,
`Handler`) without a documented reason.

| Suffix | Role |
|---|---|
| `<Foo>ViewModel` | Top-level UI state holder backed by `viewModelScope` (e.g. `MainViewModel`, `SettingsViewModel`). |
| `<Foo>Controller` | Long-lived state owner that subscribes to inputs and exposes a `StateFlow` (e.g. `SleepController`, `ScreensaverIdleController`, `KioskWindowController`, `DoorbellOverlayController`). |
| `<Foo>Coordinator` | Orchestrator across components for a multi-step domain flow (e.g. `VoicePipelineCoordinator`). |
| `<Foo>Watcher` / `<Foo>Detector` | Pure observer of domain inputs that emits typed facts (e.g. `DoorbellWatcher`, `VadSpeechDetector`). Owns no UI-shaped state. See `KioskEventBus` discipline in [docs/architecture/voice.md](docs/architecture/voice.md). |
| `<Foo>Repository` | Single owner of persisted state for a domain (e.g. `VoiceModelRepository`, `VoiceCommandRecordingRepository`). |
| `<Foo>Screen` / `<Foo>Content` | Compose smart/dumb split, see the Compose section above. |
| `<Foo>State` | Sealed UI state type (e.g. `DoorbellState`, `VoiceState`). |
| `<Foo>Overlay` | Compose overlay composable (e.g. `DoorbellOverlay`). |

If none of these fit, propose the new noun in PR review before adding it.

## Architecture Invariants

Do not change these without a deliberate reason and review.

### Wake Word

Preserve these invariants in `MicroWakeWordRunner.kt`:

- Output is `uint8`.
- Read bytes with `.toInt() and 0xFF`.
- Inference stride is `FRAMES_PER_INFERENCE`.

### Native Audio Frontend

superdash owns the wake-word audio feature frontend. It is built from vendored
TensorFlow Lite microfrontend and KISS FFT sources. Do not add a prebuilt `.so`.

Relevant files:

- `packages/voice/src/main/kotlin/com/superdash/voice/features/AudioFeatureExtractor.kt`
- `packages/voice/src/main/cpp/CMakeLists.txt`
- `packages/voice/src/main/cpp/superdash_audio_features_jni.cpp`
- `packages/voice/src/main/cpp/third_party/` (vendored TensorFlow Lite microfrontend + KISS FFT)

### Settings

Settings setters use `value` as the parameter name.

Each feature owns a typed `XSettings` view backed by `KeyValueStore`. Defaults
live in the feature's `XSettings`. `SettingsRepository` holds only cross-cutting
glue (`haUrl`, `snapshot()`).

Keep defaults and consumers aligned. Do not add feature-type imports to
`SettingsRepository`.

### Home Assistant WebSocket

Use one `HaWebSocketClient` per app. It is owned by `AppGraph`.

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
- Prefer upgrade install paths that preserve app data.

## On-Device Debugging

For the full cookbook see [docs/agent-on-device-testing.md](docs/agent-on-device-testing.md).

### adb path

`adb` is not always on `PATH`. With the Homebrew Android tools it lives at:

```text
/opt/homebrew/share/android-commandlinetools/platform-tools/adb
```

Export it once per session:

```bash
export ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
```

### Wireless debugging

The tablet advertises its ports over mDNS. Discover them, do not guess:

```bash
$ADB mdns services
```

- `_adb-tls-pairing._tcp` is the pairing port. Pair with the code from the
  tablet's Wireless Debugging screen.
- `_adb-tls-connect._tcp` is the connect port.

```bash
$ADB pair <ip>:<pairing-port> <code>
$ADB connect <ip>:<connect-port>
```

Gotcha: never run `adb shell svc wifi disable` over adb-over-wifi. It severs
your own transport and the tablet cannot be re-enabled remotely. To renew a
DHCP lease, wait out the lease or reconnect at the network layer.

### Logs

The app log tag is `superdash`.

```bash
$ADB -s <device-id> logcat -d -s superdash
```

`HaWs: connection failed` on a 30 s repeat means the HA WebSocket cannot
connect. Check DNS first: confirm the tablet can resolve the configured HA
hostname. DHCP handing out a public DNS server that cannot resolve local
split-horizon names is a common cause.

### App state

Read settings on a debug build without editing DataStore by hand:

```bash
$ADB -s <device-id> shell run-as com.superdash cat files/datastore/app_settings.preferences_pb
```

## Pull Requests

- Branch from `main`. Do not commit directly to `main`.
- Keep each PR focused on a single topic.
- Open a PR and merge it with **squash merge**, so each PR lands as one commit on `main` and history stays linear.
- Make sure `./gradlew ktlintCheck testDebugUnitTest` passes before opening the PR.

By contributing you agree your work is licensed under [GPL-3.0](LICENSE).

## Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org). The squash-merge
subject is what lands on `main`, so it must follow this format.

- Format: `type(scope): summary`, imperative and concise. Scope is optional.
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`.
- Scope is a module or area (e.g. `voice`, `screensaver`, `settings`).

Examples:

```text
feat(voice): add French wake word model
fix(settings): reset locale after instrumented test
docs: describe the ESPHome control surface
```
