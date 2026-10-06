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

## Feeds

`FeedWatcher` observes configured HA trigger entities.

- Watches enabled feeds.
- Momentary triggers emit `FeedActivated` on a rising edge, debounced 5s.
- Sustained triggers report activity through `activeFeeds`, a `StateFlow`.
- Resolves camera streams through Home Assistant.

`FeedOverlayController` derives the visible feed.

- Candidates are active sustained feeds plus open momentary feeds.
- Highest `order` wins, ties break on the most recent activation.
- Closing suppresses a feed until its trigger goes inactive.
- Auto-close is per feed and acts as an automatic close.
- Feeds with `wakeScreen` off stay hidden while the tablet is idle.

| Trigger | Shows | Hides |
|---|---|---|
| Momentary | Rising edge on the trigger entity | Auto-close, tap, or back |
| Sustained | Trigger state is in `activeStates` | Trigger leaves `activeStates`, auto-close, tap, or back |
