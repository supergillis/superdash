package com.superdash.screensaver.slideshow

import com.superdash.ha.BrowseMediaSource
import com.superdash.ha.HaMediaSourceClient
import com.superdash.ha.HaMediaSourceException
import com.superdash.ha.haJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaMediaLibrarySourceTest {
    /** Test helper: build a stub HaMediaSourceClient with canned responses. */
    private fun client(
        browse: suspend (String?) -> BrowseMediaSource,
        resolve: suspend (String) -> com.superdash.ha.ResolvedMedia = {
            com.superdash.ha.ResolvedMedia(url = "/proxy/$it", mimeType = "image/jpeg")
        },
    ) = HaMediaSourceClient(
        call = { type, params ->
            val payload = buildJsonObject(params)
            when (type) {
                "media_source/browse_media" -> {
                    val id = payload["media_content_id"]?.jsonPrimitive?.content
                    val result =
                        try {
                            browse(id)
                        } catch (e: HaMediaSourceException) {
                            return@HaMediaSourceClient buildJsonObject {
                                put("id", 1)
                                put("type", "result")
                                put("success", false)
                                putJsonObject("error") {
                                    put("code", e.code)
                                    put("message", e.message ?: "")
                                }
                            }
                        }
                    val resultJson =
                        haJson.encodeToJsonElement(BrowseMediaSource.serializer(), result) as JsonObject
                    buildJsonObject {
                        put("id", 1)
                        put("type", "result")
                        put("success", true)
                        put("result", resultJson)
                    }
                }
                "media_source/resolve_media" -> {
                    val id = payload["media_content_id"]!!.jsonPrimitive.content
                    val resolved = resolve(id)
                    buildJsonObject {
                        put("id", 1)
                        put("type", "result")
                        put("success", true)
                        putJsonObject("result") {
                            put("url", resolved.url)
                            put("mime_type", resolved.mimeType)
                        }
                    }
                }
                else -> error("unexpected type $type")
            }
        },
    )

    private fun img(name: String): BrowseMediaSource =
        BrowseMediaSource(
            title = name,
            mediaClass = "image",
            mediaContentId = "media-source://media_source/local/$name",
            mediaContentType = "image/jpeg",
        )

    private fun folder(children: List<BrowseMediaSource>): BrowseMediaSource =
        BrowseMediaSource(
            title = "vacation",
            mediaClass = "directory",
            mediaContentId = "media-source://media_source/local/vacation",
            mediaContentType = "",
            canExpand = true,
            children = children,
        )

    @Test
    fun `default order traverses children in returned order`() =
        runTest {
            val items = listOf(img("a.jpg"), img("b.jpg"), img("c.jpg"))
            val source =
                HaMediaLibrarySource(
                    client = client(browse = { folder(items) }),
                    sourceId = "media-source://media_source/local/vacation",
                    orderKey = "default",
                )
            assertEquals(
                "a.jpg",
                source
                    .next()!!
                    .media
                    .first()
                    .title,
            )
            assertEquals(
                "b.jpg",
                source
                    .next()!!
                    .media
                    .first()
                    .title,
            )
            assertEquals(
                "c.jpg",
                source
                    .next()!!
                    .media
                    .first()
                    .title,
            )
        }

    @Test
    fun `shuffle order returns all items before repeating`() =
        runTest {
            val items = listOf(img("a.jpg"), img("b.jpg"), img("c.jpg"))
            val source =
                HaMediaLibrarySource(
                    client = client(browse = { folder(items) }),
                    sourceId = "x",
                    orderKey = "shuffle",
                )
            val seen =
                setOf(
                    source
                        .next()!!
                        .media
                        .first()
                        .title,
                    source
                        .next()!!
                        .media
                        .first()
                        .title,
                    source
                        .next()!!
                        .media
                        .first()
                        .title,
                )
            assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), seen)
        }

    @Test
    fun `next returns null on browse failure`() =
        runTest {
            val source =
                HaMediaLibrarySource(
                    client = client(browse = { throw HaMediaSourceException("not_found", "missing") }),
                    sourceId = "x",
                    orderKey = "default",
                )
            assertNull(source.next())
        }

    @Test
    fun `next returns null on empty children list`() =
        runTest {
            val source =
                HaMediaLibrarySource(
                    client = client(browse = { folder(emptyList()) }),
                    sourceId = "x",
                    orderKey = "default",
                )
            assertNull(source.next())
        }

    @Test
    fun `cache refreshes after ttl`() =
        runTest {
            var browseCalls = 0
            var time = 0L
            val source =
                HaMediaLibrarySource(
                    client =
                        client(browse = {
                            browseCalls++
                            folder(listOf(img("a.jpg")))
                        }),
                    sourceId = "x",
                    orderKey = "default",
                    now = { time },
                )
            source.next()
            assertEquals(1, browseCalls)
            // Advance < ttl: no refresh
            time = 49 * 60 * 1000L
            source.next()
            assertEquals(1, browseCalls)
            // Advance > ttl: refresh
            time = 51 * 60 * 1000L
            source.next()
            assertEquals(2, browseCalls)
        }
}
