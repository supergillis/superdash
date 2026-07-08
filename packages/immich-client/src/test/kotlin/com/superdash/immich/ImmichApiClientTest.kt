package com.superdash.immich

import com.superdash.core.json.coreJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmichApiClientTest {
    private val json = coreJson

    @Test
    fun `getAlbumByName should return matching album`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("http://immich.local/api/albums", request.url.toString())
                    respond(
                        content =
                            """
                            [
                                { "id": "id-1", "albumName": "Vacation" },
                                { "id": "id-2", "albumName": "Screensaver" }
                            ]
                            """.trimIndent(),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val client = ImmichApiClient(httpClient, "http://immich.local/", "test-api-key")
            val album = client.getAlbumByName("screensaver")

            assertEquals("id-2", album?.id)
            assertEquals("Screensaver", album?.albumName)
        }

    @Test
    fun `formattedLocation should join city, state, and country`() {
        val exifFull = ImmichExif(city = "San Francisco", state = "CA", country = "USA")
        assertEquals("San Francisco, CA, USA", exifFull.formattedLocation)

        val exifPartial = ImmichExif(city = "Berlin", country = "Germany")
        assertEquals("Berlin, Germany", exifPartial.formattedLocation)

        val exifEmpty = ImmichExif()
        assertEquals(null, exifEmpty.formattedLocation)
    }

    @Test
    fun `orientation uses exif rotation when dimensions are stored unrotated`() {
        val rotatedPortrait =
            ImmichExif(
                exifImageWidth = 4032,
                exifImageHeight = 3024,
                exifOrientation = "Rotate 90 CW",
            )
        assertEquals(ImmichAssetOrientation.Portrait, rotatedPortrait.orientation)

        val normalLandscape =
            ImmichExif(
                exifImageWidth = 4032,
                exifImageHeight = 3024,
                exifOrientation = "Horizontal (normal)",
            )
        assertEquals(ImmichAssetOrientation.Landscape, normalLandscape.orientation)
    }

    @Test
    fun `getThumbnailUrl returns URL without API key in query string`() {
        // The Coil OkHttp interceptor injects `x-api-key` at request time so the
        // URL must NOT carry the key as `?key=`. The Immich `?key=` param only
        // authenticates shared-link tokens, not API keys.
        val client = ImmichApiClient(HttpClient(MockEngine { respond("") }), "http://immich.local/", "test-api-key")
        val url = client.getThumbnailUrl("asset-id")
        assertEquals("http://immich.local/api/assets/asset-id/thumbnail?size=preview", url)
    }

    @Test
    fun `getVideoPlaybackUrl returns URL without API key in query string`() {
        val client = ImmichApiClient(HttpClient(MockEngine { respond("") }), "http://immich.local/", "test-api-key")
        val url = client.getVideoPlaybackUrl("asset-id")
        assertEquals("http://immich.local/api/assets/asset-id/video/playback", url)
    }

    @Test
    fun `listCatalog paginates until nextPage is null`() =
        runTest {
            // Single untyped pass: two pages, mixed IMAGE/VIDEO/AUDIO items.
            val responses =
                listOf(
                    """{"assets":{"total":3,"count":3,"items":[{"id":"a","type":"IMAGE","originalFileName":"a.jpg","fileCreatedAt":"1970-01-01T00:00:00Z","exifInfo":{"exifImageWidth":1200,"exifImageHeight":800}},{"id":"b","type":"VIDEO","originalFileName":"b.mp4","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":"2"}}""",
                    """{"assets":{"total":3,"count":3,"items":[{"id":"c","type":"AUDIO","originalFileName":"c.mp3","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":null}}""",
                )
            var pageIndex = 0
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    val body = request.body.toByteArray().decodeToString()
                    capturedBodies += body
                    respond(
                        content = responses[pageIndex++],
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
            val client = ImmichApiClient(httpClient, "http://immich", "key", sleep = {})

            val catalog = client.listCatalog()

            // AUDIO item "c" must be filtered out client-side
            assertEquals(listOf("a", "b"), catalog.map { it.id })
            assertEquals(2, pageIndex)
            // No "type" field must be sent in the request body
            capturedBodies.forEach { body ->
                assertFalse("request body must not contain \"type\" field: $body", body.contains("\"type\""))
            }
            // page must be sent as a JSON number, not a string — Immich rejects "page":"1"
            assert(capturedBodies[0].contains(""""page":1""")) {
                "Expected page to be JSON number 1, but got: ${capturedBodies[0]}"
            }
            assert(capturedBodies[1].contains(""""page":2""")) {
                "Expected page to be JSON number 2, but got: ${capturedBodies[1]}"
            }
        }

    @Test
    fun `listCatalog retries a failing page up to 3 times`() =
        runTest {
            var calls = 0
            var sleepMs = 0L
            val engine =
                MockEngine { _ ->
                    calls++
                    if (calls in 1..2) {
                        respond("boom", status = HttpStatusCode.InternalServerError)
                    } else {
                        respond(
                            content = """{"assets":{"items":[{"id":"a","type":"IMAGE","originalFileName":"a.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":null}}""",
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
            val client = ImmichApiClient(httpClient, "http://immich", "key", sleep = { sleepMs += it })

            val catalog = client.listCatalog()

            assertEquals(listOf("a"), catalog.map { it.id })
            assertEquals(3, calls)
            assertEquals(2_000L, sleepMs) // two 1s sleeps between three attempts
        }

    @Test
    fun `listCatalog aborts after 3 failed attempts on a page and returns partial`() =
        runTest {
            var calls = 0
            val engine =
                MockEngine { _ ->
                    calls++
                    if (calls == 1) {
                        respond(
                            content = """{"assets":{"items":[{"id":"a","type":"IMAGE","originalFileName":"a.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":"2"}}""",
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("boom", status = HttpStatusCode.InternalServerError)
                    }
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
            val client = ImmichApiClient(httpClient, "http://immich", "key", sleep = {})

            val catalog = client.listCatalog()

            assertEquals(listOf("a"), catalog.map { it.id })
            assertEquals(1 + 3, calls) // page 1 once + page 2 attempted 3 times
        }

    @Test
    fun `listCatalog for album fetches via search-metadata with albumIds filter and paginates`() =
        runTest {
            // Immich v3 dropped the inline assets array from GET /api/albums/{id}, so the album
            // fetch goes through POST /api/search/metadata with an albumIds filter — the same
            // paginated path used for the whole library, and one that works on both v2 and v3.
            val responses =
                listOf(
                    """{"assets":{"items":[{"id":"a","type":"IMAGE","originalFileName":"a.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"},{"id":"b","type":"VIDEO","originalFileName":"b.mp4","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":"2"}}""",
                    """{"assets":{"items":[{"id":"c","type":"IMAGE","originalFileName":"c.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":null}}""",
                )
            var pageIndex = 0
            val capturedPaths = mutableListOf<String>()
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    capturedPaths += request.url.encodedPath
                    capturedBodies += request.body.toByteArray().decodeToString()
                    respond(
                        content = responses[pageIndex++],
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "key",
                    sleep = {},
                )

            val catalog = client.listCatalog(albumId = "alb-1")

            assertEquals(listOf("a", "b", "c"), catalog.map { it.id })
            assertEquals(2, pageIndex)
            capturedPaths.forEach { assertEquals("/api/search/metadata", it) }
            capturedBodies.forEach { body ->
                assertTrue("album request must carry exact albumIds filter: $body", body.contains("\"albumIds\":[\"alb-1\"]"))
            }
        }

    @Test
    fun `listCatalog for whole library uses single untyped pass and filters AUDIO and OTHER`() =
        runTest {
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    capturedBodies += request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"assets":{"items":[
                    {"id":"img","type":"IMAGE","originalFileName":"x.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"},
                    {"id":"vid","type":"VIDEO","originalFileName":"v.mp4","fileCreatedAt":"1970-01-01T00:00:00Z"},
                    {"id":"aud","type":"AUDIO","originalFileName":"a.mp3","fileCreatedAt":"1970-01-01T00:00:00Z"},
                    {"id":"oth","type":"OTHER","originalFileName":"o.bin","fileCreatedAt":"1970-01-01T00:00:00Z"}
                ],"nextPage":null}}""",
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "key",
                )

            val catalog = client.listCatalog()

            // Only IMAGE and VIDEO survive; AUDIO and OTHER are filtered client-side
            assertEquals(listOf("img", "vid"), catalog.map { it.id })
            // Exactly one request, no "type" field in the body
            assertEquals(1, capturedBodies.size)
            assertFalse(
                "request body must not contain \"type\" field: ${capturedBodies[0]}",
                capturedBodies[0].contains("\"type\""),
            )
            // Whole-library pass must not carry an album filter.
            assertFalse(
                "whole-library request must not contain albumIds: ${capturedBodies[0]}",
                capturedBodies[0].contains("albumIds"),
            )
        }

    @Test
    fun `getAsset returns full ImmichAsset for an id`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/assets/some-id", request.url.encodedPath)
                    respond(
                        content = """{"id":"some-id","type":"IMAGE","originalFileName":"name.jpg","fileCreatedAt":"1970-01-01T00:00:00Z","exifInfo":{"city":"Paris"}}""",
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "key",
                )

            val asset = client.getAsset("some-id")
            assertEquals("name.jpg", asset.originalFileName)
            assertEquals("Paris", asset.exifInfo?.city)
        }

    @Test
    fun `canViewAsset returns null when the album has no assets`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"assets":{"items":[],"nextPage":null}}""",
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val client =
                ImmichApiClient(HttpClient(engine) { install(ContentNegotiation) { json() } }, "http://immich", "key")

            assertNull(client.canViewAsset("alb-1"))
        }

    @Test
    fun `canViewAsset returns true on a 2xx thumbnail HEAD and probes one asset via albumIds`() =
        runTest {
            var searchBody = ""
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/api/search/metadata") {
                        searchBody = request.body.toByteArray().decodeToString()
                        respond(
                            content = """{"assets":{"items":[{"id":"a","type":"IMAGE","originalFileName":"a.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":null}}""",
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    } else {
                        // thumbnail HEAD
                        respond("", status = HttpStatusCode.OK)
                    }
                }
            val client =
                ImmichApiClient(HttpClient(engine) { install(ContentNegotiation) { json() } }, "http://immich", "key")

            assertEquals(true, client.canViewAsset("alb-1"))
            // The probe filters to the album and fetches a single asset without EXIF.
            assertTrue("probe must filter by album: $searchBody", searchBody.contains("\"albumIds\":[\"alb-1\"]"))
            assertTrue("probe must request a single asset: $searchBody", searchBody.contains("\"size\":1"))
            assertFalse("probe must not request EXIF: $searchBody", searchBody.contains("\"withExif\":true"))
        }

    @Test
    fun `canViewAsset returns false when the thumbnail HEAD is forbidden`() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/api/search/metadata") {
                        respond(
                            content = """{"assets":{"items":[{"id":"a","type":"IMAGE","originalFileName":"a.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":null}}""",
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("forbidden", status = HttpStatusCode.Forbidden)
                    }
                }
            val client =
                ImmichApiClient(HttpClient(engine) { install(ContentNegotiation) { json() } }, "http://immich", "key")

            assertEquals(false, client.canViewAsset("alb-1"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `listCatalog respects cancellation during retry sleep`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond("boom", status = HttpStatusCode.InternalServerError)
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
            // sleep delegates to real coroutine delay so cancellation propagates through it
            val client = ImmichApiClient(httpClient, "http://immich", "key", sleep = { delay(it) })

            var caughtCancellation = false
            val job =
                launch {
                    try {
                        client.listCatalog()
                    } catch (e: CancellationException) {
                        caughtCancellation = true
                        throw e // must rethrow to properly cancel the coroutine
                    }
                }
            // advance partway into the first 1 000 ms retry sleep
            advanceTimeBy(500L)
            job.cancel()
            job.join()

            assertTrue("expected CancellationException to be thrown", caughtCancellation)
            assertTrue("job should be cancelled", job.isCancelled)
        }

    @Test
    fun `listCatalog terminates at MAX_PAGES when server returns infinite nextPage`() =
        runTest {
            // A misbehaving Immich returning a non-null nextPage on every page would
            // otherwise loop forever. paginate() must stop at the documented cap.
            var calls = 0
            val engine =
                MockEngine { _ ->
                    calls++
                    respond(
                        content = """{"assets":{"items":[{"id":"p$calls","type":"IMAGE","originalFileName":"$calls.jpg","fileCreatedAt":"1970-01-01T00:00:00Z"}],"nextPage":"${calls + 1}"}}""",
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
            val client = ImmichApiClient(httpClient, "http://immich", "key", sleep = {})

            val catalog = client.listCatalog()

            assertEquals(200, calls)
            assertEquals(200, catalog.size)
        }
}
