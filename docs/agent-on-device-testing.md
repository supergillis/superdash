# Device Testing

Cookbook for testing on a tablet or emulator.

## Build

```bash
./gradlew :packages:app:assembleDebug
```

APK path:

```text
packages/app/build/outputs/apk/debug/app-debug.apk
```

## Install

Prefer mobile MCP tools when available.

```text
mcp__mobile__mobile_list_available_devices
mcp__mobile__mobile_install_app
mcp__mobile__mobile_launch_app
```

Use upgrade install.

Do not clear app data unless the user asks.

## Logs

The app log tag is `superdash`.

```bash
adb -s <device-id> logcat -c
adb -s <device-id> logcat -d -s superdash
```

For stack traces, use full logcat and filter after capture.

```bash
$ADB -s <device-id> logcat -d
```

## Common UI States

| State | Trigger |
|---|---|
| Open Settings | Swipe from screen edge. |
| Force screensaver | Settings, Screensaver, Test. |
| Force feed | Settings, Cameras, Test. |
| Wake from idle | Tap the screen. |

`SettingsActivity` is not exported.

Open settings through the edge swipe.

## Form Fields

- Tap a field before typing.
- Wait at least 1 second after editing debounced fields.
- Password fields show hidden characters.
- Clear hidden fields fully before retyping.

## Device Tooling

`scripts/device/sd.ts` drives a debug build over adb. It is TypeScript run directly by Node 26, so it starts instantly; the Kotlin scripts under `scripts/voice-fixtures` take seconds per run.

The control surface is off by default. Build with the Gradle flag to turn it on:

```bash
./gradlew :packages:app:assembleDebug -Psuperdash.debugTools=true
```

`sd install` does that, upgrade-installs, and launches. Release builds never contain it.

```bash
node scripts/device/sd.ts install
node scripts/device/sd.ts dump
node scripts/device/sd.ts set idle_timeout_sec 30
node scripts/device/sd.ts feed state
node scripts/device/sd.ts feed show "front door"
node scripts/device/sd.ts ha input_boolean.superdash_test_ring on
```

| Command | Does |
|---|---|
| `dump`, `get <key>` | Read settings. Secrets are redacted. |
| `set <key> <value> [--type t]` | Write a raw setting. The type is inferred from the stored value. |
| `remove <key>` | Delete a setting so the default applies. |
| `feed state` | Enabled and idle flags, shown feed, active triggers, configured feeds. |
| `feed show`, `feed close` | Force a feed open or closed. |
| `feed upsert <json\|@file>`, `feed remove` | Edit feeds. |
| `ha <entity> [on\|off]` | Read an HA entity, or toggle an `input_boolean.superdash_test*` helper. |
| `ha --list [prefix]` | Entity ids and states, filtered by prefix. |
| `idle`, `wake` | Force the screensaver idle state, or touch to leave it. |
| `ui` | On-screen elements with their center, text, description and id. |
| `tap <x> <y>`, `tap <label>` | Tap a point, or the element with that text, description or id. |
| `swipe <x1> <y1> <x2> <y2> [ms]` | Swipe between two points. |
| `text <value>`, `key <name>` | Type into the focused field, or press a key such as `back`. |
| `screenshot`, `logs` | Capture visual state and the `superdash` log tag. |

- Writes apply live. The app observes the same DataStore.
- Replies come back through `am broadcast`, so there is no logcat scraping.
- `set` skips the feature setters: no range clamping, and `immich_api_key` is stored unencrypted. Prefer the UI for those.
- `ha` reads the app's own entity cache. A toggle returns once the app has seen the new state.
- Use an `input_boolean.superdash_test*` helper as a feed trigger to test without a real doorbell. Create it once in HA under Settings, Devices, Helpers, Toggle.
- Debug receivers require `android.permission.DUMP`, which the adb shell holds and other apps cannot.
- The device is `--device`, then `$ANDROID_SERIAL`, then the only connected device.
- Type-check with `npm install && npm run typecheck` in `scripts/device`.

### Feed Smoke Test

`scripts/device/feed-smoke.ts` checks feeds end to end, from an HA trigger to the overlay state, in under a minute:

```bash
node scripts/device/feed-smoke.ts --camera <camera entity or stream URL>
```

- Needs the HA toggle helpers `input_boolean.superdash_test_ring` and `input_boolean.superdash_test_monitor`.
- Creates or updates the `test ring` and `test monitor` feeds, and leaves both helpers off.
- Covers show and hide, close and re-arm, auto-close, priority, the idle gate, night mode, and the `feed_showing` ESPHome sensor.
- Run it after any change to `packages/feed` or the overlay wiring.

### Gesture Smoke Test

`scripts/device/gesture-smoke.ts` drives real touches through the app:

```bash
node scripts/device/gesture-smoke.ts
```

- Covers the sidebar edge swipe and its thresholds, swipes and taps on a feed, and swipe, edge swipe and tap on the screensaver.
- Reads labels from every `strings.xml`, so it works in any device language.
- Needs one configured feed and a slideshow screensaver mode for the screensaver checks.
- Run it after any change to touch handling, `EdgeSwipeDetector`, overlays or the screensaver.

## App State

DataStore files live under:

```text
/data/data/com.superdash/files/datastore/
```

| File | Contents |
|---|---|
| `app_settings.preferences_pb` | App settings. |
| `ha_secrets.bin` | Encrypted HA tokens. |

Do not edit DataStore files by hand. Use `sd` or the UI.

## ESPHome Check

Enable in Settings:

```text
Home Assistant ESPHome
```

Expected logs:

- `EsphomeServer: listening`
- `EsphomeMdns: registered`
- `EsphomeConnection: hello`

## End To End Pattern

1. Build.
2. Install.
3. Launch.
4. Clear logs.
5. Drive the UI.
6. Wait for async work.
7. Capture logs.
8. Take a screenshot when visual state matters.
