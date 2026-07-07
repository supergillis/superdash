package com.superdash.ha.media

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraStreamSourceTest {
    @Test
    fun `success returns the url field`() =
        runTest {
            val source =
                CameraStreamSource(
                    call = { _, _ ->
                        Json.parseToJsonElement(
                            """{"id": 1, "type": "result", "success": true, "result": {"url": "/api/hls/abc/playlist.m3u8"}}""",
                        ) as JsonObject
                    },
                )
            val url = source.fetchHlsUrl("camera.front_door")
            assertEquals("/api/hls/abc/playlist.m3u8", url)
        }

    @Test
    fun `success request payload contains type and entity_id`() =
        runTest {
            var capturedType: String? = null
            var capturedPayload: JsonObject? = null
            val source =
                CameraStreamSource(
                    call = { type, params ->
                        capturedType = type
                        capturedPayload = buildJsonObject(params)
                        Json.parseToJsonElement(
                            """{"id": 1, "type": "result", "success": true, "result": {"url": "/api/hls/abc/playlist.m3u8"}}""",
                        ) as JsonObject
                    },
                )
            source.fetchHlsUrl("camera.front_door")
            assertEquals("camera/stream", capturedType)
            assertEquals("\"camera.front_door\"", capturedPayload?.get("entity_id")?.toString())
        }

    @Test
    fun `failure response throws StreamError`() {
        val source =
            CameraStreamSource(
                call = { _, _ ->
                    Json.parseToJsonElement(
                        """{"id": 1, "type": "result", "success": false, "error": {"code": "stream_unavailable", "message": "nope"}}""",
                    ) as JsonObject
                },
            )
        assertThrows(CameraStreamSource.StreamError::class.java) {
            runBlocking { source.fetchHlsUrl("camera.front_door") }
        }
    }

    @Test
    fun `success with missing url throws StreamError`() {
        val source =
            CameraStreamSource(
                call = { _, _ ->
                    Json.parseToJsonElement(
                        """{"id": 1, "type": "result", "success": true, "result": {}}""",
                    ) as JsonObject
                },
            )
        assertThrows(CameraStreamSource.StreamError::class.java) {
            runBlocking { source.fetchHlsUrl("camera.front_door") }
        }
    }
}
