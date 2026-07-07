package com.superdash.ha

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

/** Wraps HA's `media_source/browse_media` and `media_source/resolve_media`
 *  WebSocket commands.
 *
 *  [call] is injectable for testing. The default factory
 *  [fromHaWebSocketClient] binds it to a real [HaWebSocketClient] via
 *  `HaWebSocketClient.callResult`. */
class HaMediaSourceClient(
    private val call: suspend (type: String, JsonObjectBuilder.() -> Unit) -> JsonObject,
) {
    suspend fun browseMedia(mediaContentId: String?): BrowseMediaSource {
        val respJson =
            call("media_source/browse_media") {
                if (mediaContentId != null) {
                    put("media_content_id", mediaContentId)
                }
            }
        return decodeResult("media_source/browse_media", respJson) { result ->
            haJson.decodeFromJsonElement(BrowseMediaSource.serializer(), result)
        }
    }

    suspend fun resolveMedia(
        mediaContentId: String,
        entityId: String? = null,
    ): ResolvedMedia {
        val respJson =
            call("media_source/resolve_media") {
                put("media_content_id", mediaContentId)
                if (entityId != null) {
                    put("entity_id", entityId)
                }
            }
        return decodeResult("media_source/resolve_media", respJson) { result ->
            haJson.decodeFromJsonElement(ResolvedMedia.serializer(), result)
        }
    }

    private fun <T> decodeResult(
        commandName: String,
        resp: JsonObject,
        decode: (JsonElement) -> T,
    ): T =
        try {
            val frame = resp.requireResult(commandName)
            val result = frame.result ?: throw HaMediaSourceException("no_result", "$commandName: missing result")
            decode(result)
        } catch (e: HaMediaSourceException) {
            throw e
        } catch (e: HaResultException) {
            throw HaMediaSourceException(code = e.code, message = e.haMessage)
        } catch (e: SerializationException) {
            throw HaMediaSourceException(
                code = "invalid_result",
                message = "$commandName: invalid result: ${e.message}",
            )
        }

    companion object {
        fun fromHaWebSocketClient(ws: HaWebSocketClient): HaMediaSourceClient =
            HaMediaSourceClient(call = { type, params -> ws.callResult(type, params) })
    }
}

class HaMediaSourceException(
    val code: String,
    message: String,
) : Exception(message)
