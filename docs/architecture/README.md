# Architecture

Current system map for agents.

## App Shape

- Android package: `com.superdash`.
- Minimum SDK: 26.
- Main activity: `MainActivity`.
- Settings activity: `SettingsActivity`.
- Foreground services:
  - `KioskService` for kiosk lifetime.
  - `VoiceService` for microphone capture.
- App graph: `AppGraph`.
- Startup side effects are started from `SuperdashApp.onCreate()`.

## Subsystems

| Subsystem | Doc |
|---|---|
| App graph and startup | [app-graph.md](app-graph.md) |
| Event bus | [event-bus.md](event-bus.md) |
| Home Assistant integration | [ha-integration.md](ha-integration.md) |
| Compose UI | [compose-ui.md](compose-ui.md) |
| Voice | [voice.md](voice.md) |
| Screensaver and doorbell | [screensaver-doorbell.md](screensaver-doorbell.md) |
| Decisions | [decisions.md](decisions.md) |

## Boot Flow

```text
SuperdashApp.onCreate
  -> AppGraph
  -> AppGraph startup calls
       -> voice capture loop
       -> ConnectivityManager
       -> EsphomeBindings
       -> DoorbellWatcher
       -> ScreenStateProvider

MainActivity.onCreate
  -> KioskWindowController
  -> KioskService.start()
  -> control event handler
  -> MainScreen
```

## Reading Guide

- Need object ownership: read [app-graph.md](app-graph.md).
- Need cross-feature signals: read [event-bus.md](event-bus.md).
- Need Home Assistant tokens or WebSocket: read [ha-integration.md](ha-integration.md).
- Need UI flow: read [compose-ui.md](compose-ui.md).
- Need wake word or Assist: read [voice.md](voice.md).
- Need idle, sleep, photos, or doorbell: read [screensaver-doorbell.md](screensaver-doorbell.md).
- Need to avoid old mistakes: read [decisions.md](decisions.md).

## Conventions

Use [AGENTS.md](../../AGENTS.md) as the source of truth.
