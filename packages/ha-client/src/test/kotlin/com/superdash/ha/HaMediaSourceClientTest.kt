package com.superdash.ha

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HaMediaSourceClientTest {
    @Test
    fun `browseMedia without id sends type only`() =
        runTest {
            var capturedType: String? = null
            var capturedPayload: JsonObject? = null
            val client =
                HaMediaSourceClient(
                    call = { type, params ->
                        capturedType = type
                        capturedPayload = buildJsonObject(params)
                        buildJsonObject {
                            put("id", 1)
                            put("type", "result")
                            put("success", true)
                            putJsonObject("result") {
                                put("title", "root")
                                put("media_class", "directory")
                                put("media_content_id", "media-source://media_source")
                                put("media_content_type", "")
                                put("can_expand", true)
                            }
                        }
                    },
                )
            val result = client.browseMedia(mediaContentId = null)
            assertEquals("media_source/browse_media", capturedType)
            assertNull(capturedPayload?.get("media_content_id"))
            assertEquals("root", result.title)
            assertEquals("media-source://media_source", result.mediaContentId)
            assertTrue(result.canExpand)
        }

    @Test
    fun `browseMedia with id includes media_content_id`() =
        runTest {
            var capturedPayload: JsonObject? = null
            val client =
                HaMediaSourceClient(
                    call = { _, params ->
                        capturedPayload = buildJsonObject(params)
                        buildJsonObject {
                            put("id", 1)
                            put("type", "result")
                            put("success", true)
                            putJsonObject("result") {
                                put("title", "vacation")
                                put("media_class", "directory")
                                put("media_content_id", "media-source://media_source/local/vacation")
                                put("media_content_type", "")
                                put("can_expand", true)
                                putJsonArray("children") {
                                    add(
                                        buildJsonObject {
                                            put("title", "img1.jpg")
                                            put("media_class", "image")
                                            put("media_content_id", "media-source://media_source/local/vacation/img1.jpg")
                                            put("media_content_type", "image/jpeg")
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            val result = client.browseMedia(mediaContentId = "media-source://media_source/local/vacation")
            assertEquals(
                "\"media-source://media_source/local/vacation\"",
                capturedPayload?.get("media_content_id")?.toString(),
            )
            assertEquals(1, result.children.size)
            assertEquals("image", result.children[0].mediaClass)
        }

    @Test
    fun `resolveMedia returns url and mime type`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("id", 1)
                            put("type", "result")
                            put("success", true)
                            putJsonObject("result") {
                                put("url", "/api/media_source_proxy/foo.jpg")
                                put("mime_type", "image/jpeg")
                            }
                        }
                    },
                )
            val resolved = client.resolveMedia("media-source://media_source/local/img.jpg")
            assertEquals("/api/media_source_proxy/foo.jpg", resolved.url)
            assertEquals("image/jpeg", resolved.mimeType)
        }

    @Test
    fun `error frame surfaces as exception`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("id", 1)
                            put("type", "result")
                            put("success", false)
                            putJsonObject("error") {
                                put("code", "not_found")
                                put("message", "media not found")
                            }
                        }
                    },
                )
            try {
                client.browseMedia(mediaContentId = "media-source://bogus")
                fail("expected exception")
            } catch (e: HaMediaSourceException) {
                assertEquals("not_found", e.code)
                assertEquals("media not found", e.message)
            }
        }

    @Test
    fun `browseMedia surfaces command timeout`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        throw HaCommandTimeoutException("media_source/browse_media", 42)
                    },
                )

            try {
                client.browseMedia(mediaContentId = null)
                fail("expected timeout")
            } catch (e: HaCommandTimeoutException) {
                assertEquals("media_source/browse_media", e.commandType)
                assertEquals(42, e.commandId)
            }
        }

    @Test
    fun `malformed browse response names browse command`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("type", "result")
                            put("success", true)
                        }
                    },
                )

            try {
                client.browseMedia(mediaContentId = null)
                fail("expected exception")
            } catch (e: HaMediaSourceException) {
                assertEquals("invalid_result", e.code)
                assertTrue(e.message.orEmpty().startsWith("media_source/browse_media: invalid result:"))
            }
        }

    @Test
    fun `malformed browse payload names browse command`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("type", "result")
                            put("success", true)
                            putJsonObject("result") {
                                put("title", "missing required fields")
                            }
                        }
                    },
                )

            try {
                client.browseMedia(mediaContentId = null)
                fail("expected exception")
            } catch (e: HaMediaSourceException) {
                assertEquals("invalid_result", e.code)
                assertTrue(e.message.orEmpty().startsWith("media_source/browse_media: invalid result:"))
            }
        }

    @Test
    fun `malformed resolve response names resolve command`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("type", "result")
                            put("success", true)
                        }
                    },
                )

            try {
                client.resolveMedia("media-source://media_source/local/img.jpg")
                fail("expected exception")
            } catch (e: HaMediaSourceException) {
                assertEquals("invalid_result", e.code)
                assertTrue(e.message.orEmpty().startsWith("media_source/resolve_media: invalid result:"))
            }
        }

    @Test
    fun `malformed resolve payload names resolve command`() =
        runTest {
            val client =
                HaMediaSourceClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("type", "result")
                            put("success", true)
                            putJsonObject("result") {
                                put("url", "/api/media_source_proxy/foo.jpg")
                            }
                        }
                    },
                )

            try {
                client.resolveMedia("media-source://media_source/local/img.jpg")
                fail("expected exception")
            } catch (e: HaMediaSourceException) {
                assertEquals("invalid_result", e.code)
                assertTrue(e.message.orEmpty().startsWith("media_source/resolve_media: invalid result:"))
            }
        }
}
