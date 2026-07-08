package com.superdash.esphome

import kotlinx.coroutines.flow.Flow

/** ESPHome assigns each entity a numeric `key` that's used in state push +
 *  command messages instead of the object_id string. Keys are device-local;
 *  derive from a stable hash of the object_id so entity reordering doesn't
 *  shift identities. HA computes its own entity unique_id as
 *  `{formatted_mac}-{entity_type}-{object_id}` from DeviceInfo + ListEntities
 *  responses; we do NOT send a `unique_id` field (it's reserved in proto). */
internal sealed class EsphomeEntity {
    abstract val key: Int
    abstract val objectId: String
    abstract val name: String

    data class Switch(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<Boolean>,
        val onCommand: suspend (Boolean) -> Unit,
    ) : EsphomeEntity()

    data class BinarySensor(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<Boolean>,
        val deviceClass: String = "",
    ) : EsphomeEntity()

    data class Sensor(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<Float>,
        val unitOfMeasurement: String = "",
        val accuracyDecimals: Int = 0,
        val deviceClass: String = "",
    ) : EsphomeEntity()

    data class TextSensor(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<String>,
        val deviceClass: String = "",
    ) : EsphomeEntity()

    data class Number(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<Float>,
        val minValue: Float,
        val maxValue: Float,
        val step: Float,
        val unitOfMeasurement: String = "",
        val onCommand: suspend (Float) -> Unit,
    ) : EsphomeEntity()

    data class Select(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<String>,
        val options: List<String>,
        val onCommand: suspend (String) -> Unit,
    ) : EsphomeEntity()

    /** Buttons are stateless. HA fires `ButtonCommandRequest` and we run the
     *  action. There's no `ButtonStateResponse` in the protocol; HA shows the
     *  press timestamp from its own observation. */
    data class Button(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val onPress: suspend () -> Unit,
    ) : EsphomeEntity()

    /** ESPHome camera. HA pulls JPEGs via CameraImageRequest: `single` sends
     *  the latest cached frame once; `stream` opens a ~5s window (refreshed by
     *  repeated requests) during which every frame from [frames] is pushed.
     *  Images are chunked into ≤15KiB CameraImageResponse messages. */
    data class Camera(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val frames: Flow<ByteArray>,
        val latestJpeg: suspend () -> ByteArray?,
    ) : EsphomeEntity()
}

/** FNV-1a-32 over the object_id's UTF-8 bytes, with the sign bit cleared so the
 *  result fits a positive `Int` (the ESPHome `key` field is a fixed32 in the proto,
 *  but the Kotlin model uses signed Int). We deliberately avoid `String.hashCode()`:
 *  the JVM spec does not guarantee its result is stable across JDK versions or
 *  vendors. A silent reshuffle of keys would route HA-issued commands to the wrong
 *  entity for the duration of an active ESPHome session.
 *
 *  Constants per the FNV-1a-32 specification:
 *    offset basis = 0x811C9DC5
 *    prime         = 0x01000193
 */
internal fun keyFromObjectId(objectId: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (byte in objectId.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toInt() and 0xFF)
        hash = hash * 0x01000193
    }
    return hash and 0x7FFFFFFF
}
