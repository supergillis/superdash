# Home Assistant Integration

Home Assistant code lives in `packages/ha-client`.

## Files

| File | Purpose |
|---|---|
| `HaWebSocketClient.kt` | Shared WebSocket connection and entity state. |
| `HaWsMessage.kt` | Serializable WebSocket messages. |
| `HaTokenStore.kt` | Encrypted token storage. |
| `HaTokenProvider.kt` | Access token refresh. |
| `HaOAuthFlow.kt` | OAuth URL and callback helpers. |
| `HaOAuthInterceptor.kt` | WebView callback handling. |
| `HaAssistClient.kt` | Assist pipeline client. |
| `HaMediaSourceClient.kt` | Media source browsing. |
| `JsBridge.kt` | Native bridge for kiosk shell commands. |

## WebSocket

- One `HaWebSocketClient` per app.
- Owned by `AppGraph`.
- Shared by voice, screensaver, doorbell, and media source code.
- Exposes connection state, entity state, recent frames, and raw frames.
- `callResult()` sends a command and waits for the matching result frame.

## OAuth

- HA web UI redirects to an auth callback.
- `HaOAuthInterceptor` consumes the callback.
- `HaOAuthCodeExchange` stores exchanged tokens.
- `KioskWebView` injects tokens into HA frontend storage.

## Tokens

- Stored with Tink AEAD.
- Refresh happens before expiry.
- `tokensFlow` drives WebSocket connect and disconnect.

## Assist

- `HaAssistClient.runPipeline()` sends audio to HA Assist.
- `VoicePipelineCoordinator` consumes Assist events.
- TTS audio is played by `HaTtsPlayer`.

## Media Source

- `HaMediaSourceClient` browses Home Assistant media folders.
- Screensaver sources use it for media library photos.

## ESPHome API

- Home Assistant discovers superdash through ESPHome native API.
- `EsphomeBindings` builds the exposed entity catalog.
- `EsphomeConnection` handles frame routing, state pushes, and commands.
- Writable settings call `SettingsRepository` setters.
- Transient actions use `KioskEventBus`.

| Type | Examples |
|---|---|
| Switch | `keep_screen_on`, `voice_enabled`, `doorbell_enabled` |
| Binary sensor | `screen_on`, `in_screensaver`, `voice_active` |
| Sensor | `ha_entity_count`, `doorbell_count` |
| Text sensor | `ha_connection_state`, `voice_state`, `app_version` |
| Number | `vad_silence_ms`, `idle_timeout_sec`, `picture_spacing_dp` |
| Select | `day_screensaver_mode`, `overlay_position`, `media_library_order` |
| Button | `refresh_webview`, `restart_app`, `stop_screensaver` |

Deferred controls need Android-side owners first:

- Brightness.
- Battery.
- Wi-Fi.
- Volume.
- Current page.
- Screen power.

## Camera Entity and Motion Sensor

The camera module exposes:

- A camera picture entity with live JPEG stream.
- A motion binary sensor (device class `motion`) from frame-diff and ML Kit person detectors.
- `camera_enabled` and `wake_on_motion` switches to control capture and screensaver wake.
- `motion_detection_mode` select to choose between `off`, `motion`, and `person` (ML Kit).
- `motion_sensitivity` and `motion_clear_delay_sec` numbers to tune detection and event hold.

The JPEG stream is chunked to 15 KiB frames to fit under the 16 KiB Noise frame cap. A rolling ~5 second window is refreshed at several fps; Home Assistant polls the picture entity to fetch the latest frame.

## ESPHome Noise Encryption

- Server-side support for `Noise_NNpsk0_25519_ChaChaPoly_SHA256`.
- Modes are mutually exclusive on a single device: no PSK => plaintext only; PSK set => Noise only.
- PSK is a 32-byte secret entered in Settings (base64). Persisted via the same Tink AEAD keyset used by `HaTokenStore`.
- mDNS TXT key `api_encryption` reflects the current mode so HA's discovery flow prompts for the right credential.
- State machine in `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeNoiseCrypto.kt`; handshake driver in `EsphomeNoiseHandshake.kt`; envelope codec in `EsphomeNoiseFrameCodec.kt`.
- Transports live in `EsphomeTransport.kt` (`PlainTransport`, `NoiseTransport`); accept-time dispatcher in `EsphomeServer.kt` (`buildTransport`).
