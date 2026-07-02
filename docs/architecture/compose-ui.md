# Compose UI

The app uses Compose for kiosk and settings screens.

## Main Pieces

- `MainActivity`: kiosk Activity.
- `SettingsActivity`: settings Activity.
- `MainViewModel`: kiosk state.
- `SettingsViewModel`: settings state.
- `KioskWebView`: Home Assistant WebView host.
- `KioskOverlays`: voice, screensaver, and doorbell overlays.

## Smart And Dumb Split

Follow `AGENTS.md`.

- `*Screen` collects flows and owns effects.
- `*Content` is stateless.
- `*Content` takes state and event lambdas.
- `*Content` does not read `AppGraph`.

## Main Screen

`MainViewModel` combines app state.

- HA setup state.
- Token state.
- Voice state.
- Doorbell state.
- Screensaver state.
- Weather state.
- Immich client state.

## Settings Screen

`SettingsViewModel` combines settings and status.

Settings sections live under:

- `packages/app/src/main/kotlin/com/superdash/settings/ui`

Current sections:

- Connection.
- Screensaver.
- Immich.
- Voice.
- Doorbell.
- Device.
- Admin.

Settings controls:

| Control | Use |
| --- | --- |
| `SettingsValueRow` | Read-only value rows and picker launch rows. |
| `SettingsTextEditRow` | Text, URL, and secret fields with Save/Cancel dialogs. |
| `SettingsChoiceDialog` | Small fixed option sets. |
| `HaEntityPickerDialog` | HA entity IDs with search, domain filters, and manual fallback. |
| `MediaSourcePickerDialog` | HA media folders with search, loading, error, empty, and selected states. |

Settings input rules:

- Do not write text settings on every keystroke.
- Normalize URLs only on Save.
- Use HA entity pickers for HA entity IDs.
- Use the existing `AppGraph` HA client state. Do not create another HA WebSocket client.

## WebView

`KioskWebView` hosts the Home Assistant dashboard.

- Uses a local kiosk shell asset.
- Injects scripts at document start.
- Injects HA tokens.
- Handles OAuth callback interception.
- Allows camera and microphone only for the configured HA origin.

## Settings Storage

`SettingsRepository` wraps DataStore.

- Settings use typed `Setting<T>` objects.
- Defaults live beside setting declarations.
- Setters use `set(value)`.
