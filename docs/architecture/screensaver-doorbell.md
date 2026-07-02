# Screensaver And Doorbell

Screensaver, sleep, and doorbell share overlay state.

## Core Files

- `packages/screensaver/src/main/kotlin/com/superdash/screensaver/ScreensaverIdleController.kt`
- `packages/app/src/main/kotlin/com/superdash/sleep/SleepController.kt`
- `packages/screensaver/src/main/kotlin/com/superdash/screensaver/ScreensaverHost.kt`
- `packages/doorbell/src/main/kotlin/com/superdash/doorbell/DoorbellWatcher.kt`
- `packages/app/src/main/kotlin/com/superdash/kiosk/ui/KioskOverlays.kt`

## Idle

`ScreensaverIdleController` owns idle state.

- Timeout comes from settings.
- User touches reset the timer.
- `forceIdle()` starts the screensaver immediately.
- `resume()` exits idle.

## Sleep

`SleepController` owns night mode.

- Tracks configured night mode state.
- Wakes on touch, wake word, and doorbell.
- Uses the event bus for transient wake signals.

## Screensaver Modes

`ScreensaverMode` values:

- `off`
- `black`
- `clock`
- `picsum`
- `media_library`
- `immich`

## Slideshow Sources

Slideshow code lives in:

- `packages/screensaver/src/main/kotlin/com/superdash/screensaver/slideshow`

Sources:

- Picsum.
- Home Assistant media library.
- Immich albums.

Immich behavior:

- Images use the normal interval.
- Mismatched image orientations can group.
- Videos play as single full-screen slides.
- Videos are muted.
- Videos advance when playback ends.
- Failed videos are skipped.

## Doorbell

`DoorbellWatcher` observes configured HA entities.

- Watches enabled doorbells.
- Resolves camera streams through Home Assistant.
- Emits overlay state.
- Emits bus events for cross-feature reactions.
