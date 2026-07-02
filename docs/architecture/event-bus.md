# Event Bus

`KioskEventBus` carries multi-consumer notification facts.

## Files

- `packages/kiosk-bus/src/main/kotlin/com/superdash/kiosk/bus/KioskEvent.kt`
- `packages/kiosk-bus/src/main/kotlin/com/superdash/kiosk/bus/KioskEventBus.kt`

## Rules

- Use the bus for fan-out notification facts.
- Past-tense or noun-event naming only. No commands.
- Use `StateFlow` for current state.
- Use `ActivityCommandQueue` for activity-targeted commands.
- Use a direct interface call for one-consumer settings writes.
- UI collectors must use lifecycle-aware collection.
- Events are not replayed.

## Events

| Event | Producers | Consumers |
|---|---|---|
| `UserTouched` | WebView touch, ESPHome stop screensaver, settings screensaver tap | Sleep controller |
| `WakeWordDetected(phrase)` | Voice coordinator on wake | Sleep controller |
| `DoorbellRingStarted(doorbellId, timestampMs)` | Doorbell watcher | Sleep controller, voice coordinator, doorbell overlay controller |

## Buffer behavior

- Replay is `0`.
- Overflow drops the oldest event.
- Late subscribers miss past events.

Use a domain `StateFlow` when a consumer needs the current value on first collect.

## Sibling primitives

- `ActivityCommandQueue` (`packages/kiosk-bus/src/main/kotlin/com/superdash/kiosk/bus/ActivityCommandQueue.kt`):
  one-consumer commands buffered until an Activity attaches. Used for `RefreshWebView`
  and `RestartApp`.
- `SleepCommands` (`packages/app/src/main/kotlin/com/superdash/sleep/SleepCommands.kt`):
  direct interface for night-mode writes. Used by `EsphomeBindings`. No bus indirection
  because there is exactly one consumer.

## Payload discipline

Bus event payloads are primitives (entity ids, timestamps, phrases). Consumers that
need typed objects (e.g., `DoorbellConfig`) resolve them from their own settings
flow. This keeps the bus package free of doorbell/voice/etc feature types and
makes future module extraction mechanical.
