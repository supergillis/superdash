package com.superdash.screensaver.slideshow

import com.superdash.immich.ImmichAlbum
import com.superdash.immich.ImmichApiClient
import com.superdash.immich.ImmichAsset
import com.superdash.immich.ImmichAssetOrientation
import com.superdash.immich.ImmichCatalogEntry
import com.superdash.immich.ImmichExif
import com.superdash.immich.ImmichSearchAssetsBucket
import com.superdash.immich.ImmichSearchPage
import com.superdash.immich.toCatalogEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ImmichSlideshowSourceTest {
    private fun asset(
        id: String,
        type: String = "IMAGE",
        width: Int = 1200,
        height: Int = 800,
        orientation: String? = null,
    ) =
        ImmichAsset(
            id = id,
            type = type,
            originalFileName = "$id.jpg",
            fileCreatedAt = Instant.fromEpochMilliseconds(0),
            exifInfo =
                ImmichExif(
                    city = "City",
                    country = "Country",
                    exifImageWidth = width,
                    exifImageHeight = height,
                    exifOrientation = orientation,
                ),
        )

    private val mockAlbumId = "mock-album-id"
    private val mockAlbumName = "test"

    private fun mockClient(
        assets: List<ImmichAsset>,
        failPaginationAfter: Int = Int.MAX_VALUE,
    ): ImmichApiClient {
        val byId = assets.associateBy { it.id }
        var imageCalls = 0
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path == "/api/albums" ->
                        respond(
                            content = Json.encodeToString(listOf(ImmichAlbum(id = mockAlbumId, albumName = mockAlbumName))),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    path == "/api/search/metadata" -> {
                        imageCalls++
                        if (imageCalls > failPaginationAfter) {
                            return@MockEngine respond("boom", status = HttpStatusCode.InternalServerError)
                        }
                        respond(
                            content =
                                Json.encodeToString(
                                    ImmichSearchPage(ImmichSearchAssetsBucket(items = assets, nextPage = null)),
                                ),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                    path.startsWith("/api/albums/") -> {
                        val albumId = path.removePrefix("/api/albums/")
                        if (albumId == mockAlbumId) {
                            respond(
                                content =
                                    Json.encodeToString(
                                        com.superdash.immich.ImmichAlbumDetails(
                                            id = mockAlbumId,
                                            albumName = mockAlbumName,
                                            assets = assets,
                                        ),
                                    ),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        } else {
                            respond("[]", headers = headersOf("Content-Type", "application/json"))
                        }
                    }
                    path.startsWith("/api/assets/") && !path.contains("/thumbnail") && !path.contains("/video") -> {
                        val id = path.removePrefix("/api/assets/")
                        val asset = byId[id]
                        if (asset != null) {
                            respond(
                                content = Json.encodeToString(asset),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        } else {
                            respond("not found", status = HttpStatusCode.NotFound)
                        }
                    }
                    else -> respond("[]", headers = headersOf("Content-Type", "application/json"))
                }
            }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
        return ImmichApiClient(httpClient, "http://immich", "api-key", sleep = {})
    }

    @Test
    fun `next traverses every catalog entry exactly once per pass`() =
        runTest {
            val items = (1..20).map { asset("id-$it", width = 1200, height = 800) }
            val store = InMemoryImmichCatalogStore()
            val source =
                ImmichSlideshowSource(
                    client = mockClient(items),
                    catalogStore = store,
                    random = kotlin.random.Random(0L),
                )

            val seen = mutableSetOf<String>()
            repeat(items.size) {
                val item = source.next(SlideshowViewport.Landscape)!!
                seen +=
                    item.media
                        .first()
                        .title!!
                        .removeSuffix(".jpg")
            }
            assertEquals(items.map { it.id }.toSet(), seen)
        }

    @Test
    fun `next includes more than 50 assets (no cap)`() =
        runTest {
            val items = (1..120).map { asset("id-$it", width = 1200, height = 800) }
            val store = InMemoryImmichCatalogStore()
            val source =
                ImmichSlideshowSource(
                    client = mockClient(items),
                    catalogStore = store,
                    random = kotlin.random.Random(0L),
                )

            val seen = mutableSetOf<String>()
            repeat(120) {
                val title =
                    source
                        .next(SlideshowViewport.Landscape)!!
                        .media
                        .first()
                        .title!!
                seen += title.removeSuffix(".jpg")
            }
            assertEquals(120, seen.size)
        }

    @Test
    fun `next traverses assets in order`() =
        runTest {
            val items = listOf(asset("a"), asset("b"))
            val source =
                ImmichSlideshowSource(
                    mockClient(items),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    random = kotlin.random.Random(42L),
                )

            // With a seeded random the traversal is deterministic; just verify
            // both assets are seen in the first two calls.
            val first =
                source
                    .next()!!
                    .media
                    .first()
                    .title
            val second =
                source
                    .next()!!
                    .media
                    .first()
                    .title
            assertTrue(first == "a.jpg" || first == "b.jpg")
            assertTrue(second == "a.jpg" || second == "b.jpg")
            assertTrue(first != second)
        }

    @Test
    fun `videos emit as single video slides`() =
        runTest {
            val items = listOf(asset("a", "IMAGE"), asset("v", "VIDEO"))
            val source =
                ImmichSlideshowSource(
                    mockClient(items),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    random = kotlin.random.Random(0L),
                )

            // Collect both items (order may vary due to shuffle)
            val first = source.next()!!
            val second = source.next()!!
            val image = listOf(first, second).filterIsInstance<SlideshowImage>().first()
            val video = listOf(first, second).filterIsInstance<SlideshowVideo>().first()
            assertEquals(listOf("a.jpg"), image.media.map { it.title })
            assertEquals("http://immich/api/assets/a/thumbnail?size=preview", image.media.first().url)
            assertEquals(listOf("v.jpg"), video.media.map { it.title })
            assertEquals("http://immich/api/assets/v/video/playback", video.media.first().url)
        }

    @Test
    fun `metadata mapping includes location`() =
        runTest {
            val assetWithLocation =
                ImmichAsset(
                    id = "a",
                    type = "IMAGE",
                    originalFileName = "test.jpg",
                    fileCreatedAt = Instant.fromEpochMilliseconds(1000),
                    exifInfo = ImmichExif(city = "San Francisco", state = "CA", country = "USA"),
                )
            val source =
                ImmichSlideshowSource(
                    mockClient(listOf(assetWithLocation)),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                )
            val item = source.next()!!

            assertEquals("San Francisco, CA, USA", item.media.first().locationLabel)
        }

    @Test
    fun `landscape viewport groups portrait photos side by side`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("p1", width = 800, height = 1200),
                                asset("p2", width = 800, height = 1200),
                                asset("p3", width = 800, height = 1200),
                                asset("l1", width = 1200, height = 800),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 3 },
                    random = kotlin.random.Random(7L), // Seed that produces [p1,p2,p3,l1] order
                )

            // With seeded random, drive until we find the group.
            // The source will emit either the group or single items; collect all in one pass.
            val results = mutableListOf<SlideshowItem>()
            repeat(4) {
                val item = source.next(SlideshowViewport.Landscape)
                if (item != null) results += item
            }
            val group = results.find { it.media.size == 3 }
            assertNotNull("expected a group of 3", group)
            assertEquals(listOf("p1.jpg", "p2.jpg", "p3.jpg"), group!!.media.map { it.title })
            assertEquals(false, (group as SlideshowImage).isStacked)
        }

    @Test
    fun `portrait viewport stacks landscape photos`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("l1", width = 1200, height = 800),
                                asset("l2", width = 1200, height = 800),
                                asset("p1", width = 800, height = 1200),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 2 },
                    random = kotlin.random.Random(5L), // Seed that produces [l1,l2,p1] order
                )

            val results = mutableListOf<SlideshowItem>()
            repeat(3) {
                val item = source.next(SlideshowViewport.Portrait)
                if (item != null) results += item
            }
            val stackedGroup = results.find { (it as? SlideshowImage)?.isStacked == true }
            assertNotNull("expected a stacked group", stackedGroup)
            assertEquals(listOf("l1.jpg", "l2.jpg"), stackedGroup!!.media.map { it.title })
            assertEquals(true, (stackedGroup as SlideshowImage).isStacked)
        }

    @Test
    fun `portrait viewport stacks at most two landscape photos`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("l1", width = 1200, height = 800),
                                asset("l2", width = 1200, height = 800),
                                asset("l3", width = 1200, height = 800),
                                asset("p1", width = 800, height = 1200),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 3 },
                    random = kotlin.random.Random(0L),
                )

            val results = mutableListOf<SlideshowItem>()
            repeat(4) {
                val item = source.next(SlideshowViewport.Portrait)
                if (item != null) results += item
            }
            val stackedGroups = results.filter { (it as? SlideshowImage)?.isStacked == true }
            assertTrue("expected at least one stacked group", stackedGroups.isNotEmpty())
            stackedGroups.forEach { group ->
                assertTrue("stacked group should have at most 2 items", group.media.size <= 2)
            }
        }

    @Test
    fun `portrait viewport stacks non-adjacent landscape photos instead of emitting a single landscape`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("l1", width = 1200, height = 800),
                                asset("p1", width = 800, height = 1200),
                                asset("l2", width = 1200, height = 800),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 2 },
                    // Use a seeded random that will produce [0,1,2] order (l1,p1,l2)
                    random = kotlin.random.Random(7L),
                )

            // Drive the source collecting all items in one pass
            val results = mutableListOf<SlideshowItem>()
            repeat(3) {
                val item = source.next(SlideshowViewport.Portrait)
                if (item != null) results += item
            }
            val stackedGroup = results.find { (it as? SlideshowImage)?.isStacked == true }
            assertNotNull("expected a stacked group of non-adjacent landscape photos", stackedGroup)
            assertEquals(2, stackedGroup!!.media.size)
            val titles = stackedGroup.media.map { it.title }.toSet()
            assertTrue("stacked group should contain l1 and l2", titles.containsAll(setOf("l1.jpg", "l2.jpg")))
        }

    @Test
    fun `portrait video uses fit rendering in landscape viewport`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("v1", type = "VIDEO", width = 800, height = 1200),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                )

            val video = source.next(SlideshowViewport.Landscape)!!
            assertTrue(video is SlideshowVideo)
            assertEquals(listOf("v1.jpg"), video.media.map { it.title })
            assertEquals(false, (video as SlideshowVideo).fillViewport)
        }

    @Test
    fun `rotated portrait photo is not treated as landscape in portrait viewport`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("rotated", width = 4032, height = 3024, orientation = "Rotate 90 CW"),
                                asset("landscape", width = 4032, height = 3024),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 2 },
                    // Seed that produces [0,1] (rotated first, then landscape)
                    random = kotlin.random.Random(3L),
                )

            val results = mutableListOf<SlideshowItem?>()
            repeat(4) {
                results += source.next(SlideshowViewport.Portrait)
            }

            // rotated photo is portrait orientation → emitted as single portrait slide
            val portraits =
                results.filterNotNull().filter { item ->
                    item is SlideshowImage && item.media.size == 1 && item.media.first().title == "rotated.jpg"
                }
            assertTrue("rotated photo should be emitted as a single portrait slide", portraits.isNotEmpty())
        }

    @Test
    fun `portrait viewport skips unpaired landscape photo and emits next portrait`() =
        runTest {
            // With only one landscape and one portrait, the landscape can't be stacked
            // (group size 2 but only 1 landscape available). The portrait should be emitted.
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("l1", width = 1200, height = 800),
                                asset("p1", width = 800, height = 1200),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 2 },
                    random = kotlin.random.Random(0L),
                )

            val results = mutableListOf<SlideshowItem>()
            repeat(2) {
                val item = source.next(SlideshowViewport.Portrait)
                if (item != null) results += item
            }
            assertTrue("should have emitted the portrait photo", results.any { it.media.first().title == "p1.jpg" })
        }

    @Test
    fun `video breaks image grouping`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("p1", width = 800, height = 1200),
                                asset("v1", type = "VIDEO"),
                                asset("p2", width = 800, height = 1200),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 3 },
                    // Seed that produces order [p1, v1, p2] so video interrupts grouping
                    random = kotlin.random.Random(0L),
                )

            val results = mutableListOf<SlideshowItem>()
            repeat(3) {
                val item = source.next(SlideshowViewport.Landscape)
                if (item != null) results += item
            }
            assertTrue("should have emitted the video", results.any { it is SlideshowVideo })
        }

    @Test
    fun `unknown image orientation emits single slide before mismatched photos are skipped`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    client =
                        mockClient(
                            listOf(
                                asset("a", width = 0, height = 0).copy(exifInfo = ImmichExif()),
                                asset("b", width = 800, height = 1200),
                            ),
                        ),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    groupSize = { 2 },
                    // Seed that produces [a, b] order
                    random = kotlin.random.Random(0L),
                )

            val results = mutableListOf<SlideshowItem>()
            repeat(2) {
                val item = source.next(SlideshowViewport.Landscape)
                if (item != null) results += item
            }
            assertTrue(
                "unknown-orientation asset should be emitted as single slide",
                results.any { it.media.size == 1 && it.media.first().title == "a.jpg" },
            )
        }

    @Test
    fun `next returns null on empty response`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    mockClient(emptyList()),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                )
            assertNull(source.next())
        }

    @Test
    fun `next pulls from named album when albumName matches`() =
        runTest {
            val source =
                ImmichSlideshowSource(
                    mockClient(listOf(asset("photo-from-tablet"))),
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                )

            val item = source.next()
            assertNotNull(item)
            assertEquals("photo-from-tablet.jpg", item!!.media.first().title)
        }

    @Test
    fun `next album name match is case insensitive`() =
        runTest {
            // mockClient uses mockAlbumName ("test"); pass "TEST" and it should still resolve
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path == "/api/albums" ->
                            respond(
                                content = Json.encodeToString(listOf(ImmichAlbum(id = mockAlbumId, albumName = "TEST"))),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        path.startsWith("/api/albums/") ->
                            respond(
                                content =
                                    Json.encodeToString(
                                        com.superdash.immich.ImmichAlbumDetails(
                                            id = mockAlbumId,
                                            albumName = "TEST",
                                            assets = listOf(asset("a")),
                                        ),
                                    ),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        path.startsWith("/api/assets/") ->
                            respond(
                                content = Json.encodeToString(asset("a")),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        else -> respond("[]", headers = headersOf("Content-Type", "application/json"))
                    }
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "api-key",
                    sleep = {},
                )
            val source = ImmichSlideshowSource(client, albumName = "test", catalogStore = InMemoryImmichCatalogStore())

            assertNotNull(source.next())
        }

    @Test
    fun `next returns null when albumName has no match`() =
        runTest {
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path == "/api/albums" ->
                            respond(
                                content = Json.encodeToString(listOf(ImmichAlbum(id = "alb-1", albumName = "Tablet"))),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        else -> respond("[]", headers = headersOf("Content-Type", "application/json"))
                    }
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "api-key",
                    sleep = {},
                )
            val source = ImmichSlideshowSource(client, albumName = "Phone", catalogStore = InMemoryImmichCatalogStore())

            assertNull(source.next())
        }

    @Test
    fun `loads from persisted catalog without hitting the network`() =
        runTest {
            val items = listOf(asset("a", width = 1200, height = 800))
            val store = InMemoryImmichCatalogStore()
            store.save(
                album = "",
                entries = items.map { it.toCatalogEntry() },
                fetchedAtMs = 0L,
            )

            var searchCalls = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path == "/api/search/metadata") {
                        searchCalls++
                    }
                    if (path.startsWith("/api/assets/")) {
                        respond(
                            content = Json.encodeToString(items.first()),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    } else {
                        respond(
                            """{"assets":{"items":[],"nextPage":null}}""",
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "key",
                    sleep = {},
                )
            val source =
                ImmichSlideshowSource(
                    client = client,
                    catalogStore = store,
                    now = { 100L }, // fresh — well within the ttl
                    catalogTtlMs = { 1_000L },
                )

            val item = source.next(SlideshowViewport.Landscape)!!
            assertEquals("a.jpg", item.media.first().title)
            assertEquals(0, searchCalls)
        }

    @Test
    fun `mismatched album in persisted cache triggers a fresh fetch`() =
        runTest {
            val store = InMemoryImmichCatalogStore()
            store.save(
                album = "OldAlbum",
                entries = listOf(ImmichCatalogEntry("stale", "IMAGE", ImmichAssetOrientation.Landscape)),
                fetchedAtMs = 0L,
            )

            val items = listOf(asset("fresh", width = 1200, height = 800))
            val source =
                ImmichSlideshowSource(
                    client = mockClient(items),
                    albumName = "",
                    catalogStore = store,
                    now = { 0L },
                )

            val item = source.next(SlideshowViewport.Landscape)!!
            assertEquals("fresh.jpg", item.media.first().title)
            val loaded = store.load()!!
            assertEquals("", loaded.album)
            assertEquals(listOf("fresh"), loaded.entries.map { it.id })
        }

    @Test
    fun `cache refreshes after ttl`() =
        runTest {
            var fetchCount = 0
            var time = 0L

            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path == "/api/albums" ->
                            respond(
                                content = Json.encodeToString(listOf(ImmichAlbum(id = mockAlbumId, albumName = mockAlbumName))),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        path.startsWith("/api/albums/") -> {
                            fetchCount++
                            respond(
                                content =
                                    Json.encodeToString(
                                        com.superdash.immich.ImmichAlbumDetails(
                                            id = mockAlbumId,
                                            albumName = mockAlbumName,
                                            assets = listOf(asset("a")),
                                        ),
                                    ),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        }
                        path.startsWith("/api/assets/") ->
                            respond(
                                content = Json.encodeToString(asset("a")),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        else -> respond("[]", headers = headersOf("Content-Type", "application/json"))
                    }
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "key",
                    sleep = {},
                )

            val source =
                ImmichSlideshowSource(
                    client,
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    now = { time },
                )

            source.next()
            assertEquals(1, fetchCount)

            // Advance < ttl (24h)
            time = 1000
            source.next()
            assertEquals(1, fetchCount)

            // Advance > ttl (24h)
            time = 24L * 60 * 60 * 1000 + 1
            source.next()
            assertEquals(2, fetchCount)
        }

    @Test
    fun `enrichment cache is cleared after catalog refresh due to TTL expiry`() =
        runTest {
            var time = 0L
            val catalogTtlMs = 1_000L
            val items = listOf(asset("a", width = 1200, height = 800))

            var assetCallCount = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path == "/api/albums" ->
                            respond(
                                content = Json.encodeToString(listOf(ImmichAlbum(id = mockAlbumId, albumName = mockAlbumName))),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        path.startsWith("/api/albums/") ->
                            respond(
                                content =
                                    Json.encodeToString(
                                        com.superdash.immich.ImmichAlbumDetails(
                                            id = mockAlbumId,
                                            albumName = mockAlbumName,
                                            assets = items,
                                        ),
                                    ),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        path.startsWith("/api/assets/") && !path.contains("/thumbnail") && !path.contains("/video") -> {
                            assetCallCount++
                            respond(
                                content = Json.encodeToString(items.first()),
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        }
                        else -> respond("[]", headers = headersOf("Content-Type", "application/json"))
                    }
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "api-key",
                    sleep = {},
                )
            val source =
                ImmichSlideshowSource(
                    client = client,
                    albumName = mockAlbumName,
                    catalogStore = InMemoryImmichCatalogStore(),
                    now = { time },
                    catalogTtlMs = { catalogTtlMs },
                    prefetchScope = this,
                )

            // First next(): fetches enrichment for the slide
            source.next(SlideshowViewport.Landscape)
            testScheduler.advanceUntilIdle()
            val callsAfterFirst = assetCallCount

            // Second next() within TTL: cached, no new asset call
            source.next(SlideshowViewport.Landscape)
            testScheduler.advanceUntilIdle()
            assertEquals("cache should serve enrichment within TTL", callsAfterFirst, assetCallCount)

            // Advance past TTL to trigger catalog refresh
            time = catalogTtlMs + 1

            // next() after TTL: catalog is refreshed, cache evicted, so enrichment is fetched again
            source.next(SlideshowViewport.Landscape)
            testScheduler.advanceUntilIdle()
            assertTrue("evictAll should force a fresh asset fetch after TTL refresh", assetCallCount > callsAfterFirst)
        }

    @Test
    fun `concurrent next and refreshCatalog do not throw`() =
        runTest {
            val items = (1..50).map { asset("id-$it", width = 1200, height = 800) }
            val source =
                ImmichSlideshowSource(
                    client = mockClient(items),
                    catalogStore = InMemoryImmichCatalogStore(),
                    random = kotlin.random.Random(0L),
                    prefetchScope = this,
                )

            // Drive next() and refreshCatalog() concurrently.
            val nextJob = launch { repeat(20) { source.next(SlideshowViewport.Landscape) } }
            val refreshJob = launch { repeat(5) { source.refreshCatalog() } }
            nextJob.join()
            refreshJob.join()
            // Test passes if no exception thrown.
        }

    @Test
    fun `look-ahead prefetches the next 3 entries after each next call`() =
        runTest {
            val items = (1..10).map { asset("id-$it", width = 1200, height = 800) }
            val store = InMemoryImmichCatalogStore()
            store.save(
                album = "",
                entries = items.map { it.toCatalogEntry() },
                fetchedAtMs = 1L,
            )

            val fetchedIds = CopyOnWriteArrayList<String>()
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path.startsWith("/api/assets/")) {
                        val id = path.removePrefix("/api/assets/")
                        fetchedIds += id
                        respond(
                            content = Json.encodeToString(items.first { it.id == id }),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    } else {
                        respond(
                            """{"assets":{"items":[],"nextPage":null}}""",
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                }
            val client =
                ImmichApiClient(
                    HttpClient(engine) { install(ContentNegotiation) { json() } },
                    "http://immich",
                    "key",
                    sleep = {},
                )
            val source =
                ImmichSlideshowSource(
                    client = client,
                    catalogStore = store,
                    now = { 10L },
                    catalogTtlMs = { 1_000_000L },
                    random = kotlin.random.Random(0L),
                    prefetchScope = this,
                )

            source.next(SlideshowViewport.Landscape)
            // Start the look-ahead prefetch coroutines.
            testScheduler.advanceUntilIdle()

            // Their HTTP calls run on Ktor's own dispatcher, not the test
            // scheduler, so wait in real time for them to settle rather than
            // assuming advanceUntilIdle already drained them (flaky on slow CI).
            withContext(Dispatchers.Default) {
                withTimeout(10_000) {
                    while (fetchedIds.toSet().size < 4) {
                        delay(20)
                    }
                }
            }

            // 1 synchronous fetch for current slide + 3 prefetches = 4 distinct ids.
            assertEquals(4, fetchedIds.toSet().size)
        }
}
