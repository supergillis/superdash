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
| Force doorbell | Settings, Doorbell, Test. |
| Wake from idle | Tap the screen. |

`SettingsActivity` is not exported.

Open settings through the edge swipe.

## Form Fields

- Tap a field before typing.
- Wait at least 1 second after editing debounced fields.
- Password fields show hidden characters.
- Clear hidden fields fully before retyping.

## App State

DataStore files live under:

```text
/data/data/com.superdash/files/datastore/
```

| File | Contents |
|---|---|
| `app_settings.preferences_pb` | App settings. |
| `ha_secrets.bin` | Encrypted HA tokens. |

Do not edit DataStore files by hand.

Drive settings through the UI.

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
