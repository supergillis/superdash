package com.superdash.ha.media

import com.superdash.core.log.Log
import com.superdash.ha.CameraStreamResult
import com.superdash.ha.ResultFrame
import com.superdash.ha.haJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

private val log = Log("CameraStreamSource")

/** Resolves an HLS playlist URL for a camera entity by issuing a `camera/stream`
 *  command over the shared HA WebSocket. The url returned is HA-relative (e.g.
 *  `/api/hls/<id>/playlist.m3u8`) and must be combined with the configured HA
 *  base URL by the caller.
 *
 *  [call] is injectable for testing; production wiring binds it to
 *  `HaWebSocketClient.callResult`. */
class CameraStreamSource(
    private val call: suspend (type: String, JsonObjectBuilder.() -> Unit) -> JsonObject,
) {
    class StreamError(
        message: String,
    ) : RuntimeException(message)

    suspend fun fetchHlsUrl(cameraEntity: String): String {
        val frameJson =
            call("camera/stream") {
                put("entity_id", cameraEntity)
            }
        val frame = haJson.decodeFromJsonElement(ResultFrame.serializer(), frameJson)
        if (!frame.success) {
            val message = frame.error?.message ?: "Camera stream request failed"
            log.w("camera/stream failed", null, "entity" to cameraEntity, "message" to message)
            throw StreamError(message)
        }
        val resultJson = frame.result ?: throw StreamError("camera/stream: missing result")
        val result = haJson.decodeFromJsonElement(CameraStreamResult.serializer(), resultJson)
        val url = result.url ?: throw StreamError("camera/stream: missing url in result")
        log.i("camera/stream resolved", "entity" to cameraEntity, "url" to url)
        return url
    }
}
