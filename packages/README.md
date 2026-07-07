# Packages

Quick map for agents.

## `packages/app`

Android app module.

Owns:

- Activities.
- Compose UI.
- Settings.
- Services.
- App graph.
- Feature wiring.

Start with:

- `src/main/kotlin/com/superdash/SuperdashApp.kt`
- `src/main/kotlin/com/superdash/AppGraph.kt`
- `src/main/kotlin/com/superdash/MainActivity.kt`
- `src/main/kotlin/com/superdash/settings/SettingsActivity.kt`

Tests:

```bash
./gradlew :packages:app:testDebugUnitTest
```

## `packages/core`

Shared support code.

Owns:

- `Log`.
- `KeyedEnum`.
- `UrlNormalizer`.

Tests:

```bash
./gradlew :packages:core:testDebugUnitTest
```

## `packages/ha-client`

Home Assistant client library.

Owns:

- OAuth helpers.
- Encrypted token storage.
- Token refresh.
- WebSocket client.
- Assist client.
- Media source client.
- WebView bridge.

Start with:

- `src/main/kotlin/com/superdash/ha/HaWebSocketClient.kt`
- `src/main/kotlin/com/superdash/ha/HaTokenProvider.kt`
- `src/main/kotlin/com/superdash/ha/HaOAuthInterceptor.kt`

Tests:

```bash
./gradlew :packages:ha-client:testDebugUnitTest
```

## `packages/esphome-server`

ESPHome native API server.

Owns:

- Frame codec.
- Message type mapping.
- Per-client connection state.
- Entity definitions.
- Switch, binary sensor, sensor, text sensor, number, select, and button handling.
- TCP server.
- mDNS announce.

Start with:

- `src/main/kotlin/com/superdash/esphome/EsphomeBindings.kt`
- `src/main/kotlin/com/superdash/esphome/EsphomeConnection.kt`
- `src/main/kotlin/com/superdash/esphome/EsphomeServer.kt`

Tests:

```bash
./gradlew :packages:esphome-server:testDebugUnitTest
```

## `packages/immich-client`

Immich client library.

Owns:

- Album listing.
- Random asset search.
- Thumbnail URLs.
- API key auth.
- Reachability probes.

Start with:

- `src/main/kotlin/com/superdash/immich/ImmichApiClient.kt`
- `src/main/kotlin/com/superdash/immich/ImmichModels.kt`

Tests:

```bash
./gradlew :packages:immich-client:testDebugUnitTest
```
