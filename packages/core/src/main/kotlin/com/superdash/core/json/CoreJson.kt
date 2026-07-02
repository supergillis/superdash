package com.superdash.core.json

import kotlinx.serialization.json.Json

/**
 * Shared lenient [Json] instance for superdash code that does not need HA-specific configuration.
 *
 * Use this for app and feature-module payloads (Doorbell config files, settings export/import,
 * cross-module DTOs). HA WebSocket frames use the separate `haJson` in `:packages/ha-client`,
 * which sets `classDiscriminator = "type"` to match the HA protocol.
 */
val coreJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }
