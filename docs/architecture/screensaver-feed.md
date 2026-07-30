# Screensaver And Feed

Screensaver, sleep, and feed share overlay state.

## Core Files

- `packages/screensaver/src/main/kotlin/com/superdash/screensaver/ScreensaverIdleController.kt`
- `packages/app/src/main/kotlin/com/superdash/sleep/SleepController.kt`
- `packages/screensaver/src/main/kotlin/com/superdash/screensaver/ScreensaverHost.kt`
- `packages/feed/src/main/kotlin/com/superdash/feed/FeedWatcher.kt`
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
- Wakes on touch, wake word, and feed.
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

## Feed

`FeedWatcher` observes configured HA entities.

- Watches enabled feeds.
- Resolves camera streams through Home Assistant.
- Emits overlay state.
- Emits bus events for cross-feature reactions.
