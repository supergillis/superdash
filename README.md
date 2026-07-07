# superdash

Home Assistant kiosk app for Android tablets.

![superdash showing the ambient photo screensaver with a clock overlay](docs/screenshots/screensaver.jpg)

superdash turns an Android tablet into an always-on Home Assistant wall panel. Your
dashboard runs full-screen in a kiosk, stays signed in across restarts, and gains the
extras a wall panel wants: hands-free voice control, an ambient photo screensaver, and
doorbell camera overlays. It can also expose itself back to Home Assistant over the
ESPHome native API, so HA can read and control the kiosk.

## Features

- Shows a Home Assistant dashboard in kiosk mode.
- Handles Home Assistant OAuth and token refresh.
- Runs on-device wake word detection and sends captured speech to Home Assistant Assist.
- Shows screensavers and doorbell camera overlays.
- Exposes controls to Home Assistant through the ESPHome native API.

## Getting Started

This is for installing a released build on a tablet. To build it yourself, see
[Building from source](#building-from-source).

### Requirements

- **Android 15 or newer (API level 35). This is a hard requirement. The app
  will not install on older Android versions.**
- An arm64 (64-bit) tablet. The APK ships arm64-v8a only.
- A running Home Assistant instance reachable from the tablet's network.

### Install

1. Open the [Releases](../../releases) page and download the latest
   `app-release.apk`.
2. Open the file. Android will ask you to allow installs from your browser or
   file manager the first time. Allow it.
3. Install, then open superdash.

### Connect to Home Assistant

On first launch you see a "Welcome to superdash" screen that says "Enter your
Home Assistant URL to get started".

1. Type your Home Assistant address in the `HA URL` field. The placeholder shows
   the expected form, for example `homeassistant.local:8123`. If you leave off
   the scheme, `http://` is assumed. Use `https://...` for a TLS host.
2. Tap `Continue`. The app opens your Home Assistant login page.
3. Sign in with your Home Assistant account and approve access.
4. Your dashboard loads. You are done for basic use.

### Grant microphone access (optional)

The microphone is the only runtime permission the app asks for, and only if you
turn on voice. It is not needed for the dashboard.

- Open Settings (swipe in from the screen edge), go to `Voice`, and turn on
  `Voice enabled`. Android then prompts for microphone access. Allow it to use
  wake word and Home Assistant Assist.

### Keep it running

For an always-on tablet:

- In Settings, open `Kiosk` and turn on `Keep screen on`.
- The first time you run the app it prompts once to ignore battery optimizations.
  Allow it so the app is not killed in the background. You can re-open this later
  from Settings under `Admin` with the `Battery saving help` button.

### Optional: boot straight into the kiosk

This is optional. superdash works fine launched by hand from the app drawer.
Set this up only if you want a wall-mounted tablet to boot straight into the
dashboard.

1. In Settings, open `Kiosk` and turn on `Launch on boot`.
2. Make superdash your home app: open Android's own Settings, find the default
   home app or launcher setting, and choose superdash. superdash registers as a
   home app, so it then launches when the tablet powers on and when you press
   Home.

### Explore Settings

Open Settings by swiping in from the screen edge. Top-level sections:

- `Voice`: pick a wake word and, under `Local models`, download the on-device
  speech-to-text and wake word models (tap the download icon next to a model).
- `Screensaver`: idle display, night mode, and the Immich photo slideshow under
  `Immich photos`.
- `Doorbell`: turn on `Doorbell overlay` to show a camera feed when a doorbell
  rings.
- `ESPHome`: turn on `Enabled` to expose superdash to Home Assistant over the
  ESPHome protocol so HA can read and control the kiosk.

## Status & Disclaimers

**This project is "vibe coded."** It was built largely with an AI coding
assistant (Claude Code) under human direction and review. Practically, that
means:

- Expect AI-generated patterns and the occasional rough edge. Read the code
  before you rely on it.
- It is early-stage software (v0.1.x). Settings, interfaces, and behavior may
  change or break without notice.

**No warranty.** superdash is provided "as is", without warranty of any kind,
to the extent permitted by the GPL-3.0 license. You run it at your own risk.

**Security.** superdash handles Home Assistant OAuth tokens, runs a foreground
service, embeds a WebView, and exposes an ESPHome native API server on your
local network. It has not been security audited. Run it only on networks and
devices you trust, and review the code, permissions, and settings before
pointing it at a home you care about.

**Not affiliated.** superdash is an independent, community-built project. It is
not affiliated with or endorsed by Home Assistant, Nabu Casa, or any commercial
kiosk product.

## Packages

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

See [packages/README.md](packages/README.md).

## Building from source

### Requirements

- JDK 17 on `PATH`.
- Android SDK installed.

### Common Commands

```bash
./gradlew :packages:app:assembleDebug
./gradlew :packages:app:testDebugUnitTest
./gradlew :packages:ha-client:testDebugUnitTest
./gradlew :packages:esphome-server:testDebugUnitTest
./gradlew :packages:immich-client:testDebugUnitTest
./gradlew :packages:core:testDebugUnitTest
./gradlew ktlintCheck
```

## Agent Entry Points

- Start with [AGENTS.md](AGENTS.md).
- Architecture map: [docs/architecture/README.md](docs/architecture/README.md).
- Device testing: [docs/agent-on-device-testing.md](docs/agent-on-device-testing.md).

## Current Control Surface

Home Assistant discovers the kiosk through ESPHome native API.

- Toggle in Settings: `Home Assistant ESPHome`.
- mDNS service: `_esphomelib._tcp`.
- TCP port: `6053`.
- Exposes switches, sensors, text sensors, numbers, selects, and buttons.
- Writable settings use app settings setters.
- One-shot actions use the event bus.

| Type | Entities |
|---|---|
| Switches | Keep screen on, start on boot, night mode, voice, doorbell, launch on wake. |
| Sensors | HA entity count, doorbell count. |
| Text sensors | HA state, voice state, selected modes, weather entity, media source, app version. |
| Numbers | VAD silence, idle timeout, picture spacing, doorbell auto-close. |
| Selects | Screensaver modes, overlay position, STT providers, Assist provider, wake word, media order. |
| Buttons | Refresh WebView, restart app, start screensaver, stop screensaver. |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md) for conventions.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).
