# App Graph

`AppGraph` owns application-scoped dependencies.

## Files

- `packages/app/src/main/kotlin/com/superdash/SuperdashApp.kt`
- `packages/app/src/main/kotlin/com/superdash/AppGraph.kt`

## AppGraph

- Built once by `SuperdashApp`.
- Exposed as `SuperdashApp.graph`.
- Uses one application `CoroutineScope`.
- Constructs dependencies top to bottom.
- Keeps object ownership explicit.

Key groups:

- `HaSubgraph`: HTTP client, tokens, HA WebSocket, Assist, media source.
- `ImmichSubgraph`: Immich API client state.
- `VoiceSubgraph`: TTS, coordinator, capture loop.
- Root graph: settings, bus, image loader, idle, sleep, doorbell, device info.
- `EsphomeBindings`: Home Assistant control surface.

## Composition Root Rules

- `AppGraph` constructs dependencies and subgraphs.
- `AppGraph` does not perform workflow decisions.
- Subgraphs wire feature services and expose stable entry points.
- ViewModels own UI write actions.
- Repositories own persistence and I/O boundaries.
- Runtime coordinators own active workflow state.
- Debug receivers call typed debug APIs instead of mutating internals directly.

## Inter-component communication

`KioskEventBus` (`packages/kiosk-bus/src/main/kotlin/com/superdash/kiosk/bus/KioskEventBus.kt`)
is the application-wide channel for typed notification facts. See
`docs/architecture/voice.md` "Inter-Component Communication" for the full
principles.

| Producer | Event | Consumer(s) |
| --- | --- | --- |
| `MainActivity.dispatchTouchEvent` / `dispatchKeyEvent` | `UserTouched` | `SleepController` → `idleController.touch()` |
| `EsphomeBindings.stopScreensaver` | `UserTouched` | `SleepController` → `idleController.touch()` |
| `VoicePipelineCoordinator.onWake` | `WakeWordDetected(phrase)` | `SleepController` → `idleController.touch()` |
| `DoorbellWatcher.handleUpdate` | `DoorbellRingStarted(doorbellId, timestampMs)` | `SleepController` → `idleController.touch()`; `VoicePipelineCoordinator` → `stopAll()`; `DoorbellOverlayController` → resolves config by id → `state = Showing(config, timestampMs)` |

Activity-targeted commands (`RefreshWebView`, `RestartApp`) go through
`ActivityCommandQueue`, not the bus, so they survive Activity pauses.
Night-mode writes use the `SleepCommands` direct interface
(`EsphomeBindings.setNightMode` → `sleepController.setNightModeActive`).

Direct calls (intentionally not on the bus):

- UI cancel actions (`onCancelVoice`, `onCloseDoorbell`): single-consumer
  user input with synchronous-state-flip semantics.
- Activity lifecycle (`idleController.pause`/`resume`): tied to Activity
  hooks, not a fact.
- `VoicePipelineCoordinator.runResults`: typed `SharedFlow` with replay;
  consumers (`VoiceCommandRecordingService`, benchmarks) need replay
  semantics that the bus deliberately does not provide.
- `VoiceCaptureLoop` → `coordinator.onWake(context, audio)`: the call hands
  off a live `Flow<ShortArray>`. The bus-side notification is the separate
  `WakeWordDetected` emission from inside `onWake`.

## Startup

`SuperdashApp.onCreate()` starts AppGraph-owned side effects.

- Starts Home Assistant connectivity handling.
- Starts ESPHome native API.
- Starts doorbell watching.
- Starts screen state broadcasts.

The voice capture loop is not started here. It runs inside `VoiceService`, a
microphone foreground service started and stopped per `VoiceServiceRunPolicy`.

## Activity Owned Work

`MainActivity` owns Activity-lifetime work.

- Window and kiosk mode.
- Foreground service start.
- Control events that need a live Activity.
- Compose content.
- Navigation to settings.

Keep Activity work out of `AppGraph`.
