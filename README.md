# superdash

Home Assistant kiosk app for Android tablets.

## What It Does

- Shows a Home Assistant dashboard in kiosk mode.
- Handles Home Assistant OAuth and token refresh.
- Runs on-device wake word detection.
- Sends captured speech to Home Assistant Assist.
- Shows screensavers and doorbell camera overlays.
- Exposes controls to Home Assistant through ESPHome native API.

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

## Requirements

- JDK 17 on `PATH`.
- Android SDK installed.

## Common Commands

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
