package com.superdash.screensaver.slideshow

import androidx.collection.LruCache
import com.superdash.core.log.Log
import com.superdash.immich.ImmichApiClient
import com.superdash.immich.ImmichAsset
import com.superdash.immich.ImmichAssetOrientation
import com.superdash.immich.ImmichCatalogEntry
import com.superdash.immich.formattedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.random.Random

private val log = Log("ImmichSource")

private const val DEFAULT_CATALOG_TTL_MS = 24L * 60 * 60 * 1000
private const val IMMICH_IMAGE = "IMAGE"
private const val IMMICH_VIDEO = "VIDEO"
private const val ENRICHMENT_CACHE_SIZE = 16
private const val LOOKAHEAD_COUNT = 3

class ImmichSlideshowSource(
    private val client: ImmichApiClient,
    private val albumName: String = "",
    private val catalogStore: ImmichCatalogStore,
    private val catalogTtlMs: () -> Long = { DEFAULT_CATALOG_TTL_MS },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
    private val groupSize: () -> Int = { random.nextInt(2, 4) },
    private val prefetchScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SlideshowSource,
    AutoCloseable {
    override val id = "immich:$albumName"

    private val mutex = Mutex()
    private val enrichmentCache = LruCache<String, ImmichAsset>(ENRICHMENT_CACHE_SIZE)

    override fun close() {
        prefetchScope.cancel()
    }

    fun catalogSize(): Int = catalog.size

    private var catalog: List<ImmichCatalogEntry> = emptyList()
    private var shuffledIndices: IntArray = IntArray(0)
    private var cursor: Int = 0
    private var catalogFetchedAtMs: Long = 0L
    private var resolvedAlbumId: String? = null
    private var loadedFromDisk: Boolean = false

    override suspend fun next(viewport: SlideshowViewport): SlideshowItem? =
        mutex.withLock {
            ensureCatalog()
            if (catalog.isEmpty()) {
                return@withLock null
            }

            var attempts = 0
            while (attempts < catalog.size) {
                attempts++
                val firstCatalogIndex = shuffledIndices[cursor]
                val first = catalog[firstCatalogIndex]
                val firstOrientation = first.orientation
                if (first.type == IMMICH_VIDEO) {
                    val fillViewport =
                        firstOrientation == ImmichAssetOrientation.Unknown ||
                            firstOrientation == viewport.orientation
                    advanceCursor(1)
                    val media = fetchMedia(first)
                    return returnSlide(SlideshowVideo(video = media, fillViewport = fillViewport))
                }
                if (firstOrientation == ImmichAssetOrientation.Unknown ||
                    firstOrientation == viewport.orientation
                ) {
                    advanceCursor(1)
                    val media = fetchMedia(first)
                    return returnSlide(SlideshowImage(media = listOf(media)))
                }

                val groupLimit =
                    if (viewport == SlideshowViewport.Portrait) {
                        2
                    } else {
                        groupSize().coerceIn(2, 3)
                    }
                val groupCursorOffsets = findMismatchedGroupOffsets(firstOrientation, groupLimit)
                if (groupCursorOffsets.size > 1) {
                    val selectedEntries = groupCursorOffsets.map { catalog[shuffledIndices[cursor + it]] }
                    hoistAndConsumeGroup(groupCursorOffsets)
                    val media = selectedEntries.map { fetchMedia(it) }
                    return returnSlide(
                        SlideshowImage(
                            media = media,
                            isStacked = viewport == SlideshowViewport.Portrait,
                        ),
                    )
                }
                val wasLast = cursor == shuffledIndices.lastIndex
                advanceCursor(1)
                if (wasLast) {
                    return null
                }
            }
            return null
        }

    private fun returnSlide(item: SlideshowItem): SlideshowItem {
        scheduleLookahead()
        return item
    }

    private fun findMismatchedGroupOffsets(
        targetOrientation: ImmichAssetOrientation,
        limit: Int,
    ): List<Int> {
        val offsets = mutableListOf<Int>()
        var offset = 0
        while (offsets.size < limit && cursor + offset < shuffledIndices.size) {
            val entry = catalog[shuffledIndices[cursor + offset]]
            if (entry.type == IMMICH_VIDEO) {
                break
            }
            if (entry.orientation == ImmichAssetOrientation.Unknown) {
                break
            }
            if (entry.orientation != targetOrientation) {
                offset++
                continue
            }
            offsets += offset
            offset++
        }
        return offsets
    }

    /** Rearranges `shuffledIndices` so that the selected offsets are
     *  contiguous starting at `cursor`, then advances cursor past them.
     *  Preserves "every index touched exactly once per pass". */
    private fun hoistAndConsumeGroup(offsets: List<Int>) {
        val selectedAbsolute = offsets.map { cursor + it }.toSet()
        val before = shuffledIndices.copyOfRange(0, cursor)
        val groupIndices = offsets.map { shuffledIndices[cursor + it] }
        val tail =
            shuffledIndices
                .copyOfRange(cursor, shuffledIndices.size)
                .filterIndexed { i, _ -> (cursor + i) !in selectedAbsolute }
        shuffledIndices = (before.toList() + groupIndices + tail).toIntArray()
        advanceCursor(offsets.size)
    }

    private fun advanceCursor(count: Int) {
        cursor += count
        if (cursor >= shuffledIndices.size) {
            reshuffle()
        }
    }

    private fun reshuffle() {
        shuffledIndices = catalog.indices.shuffled(random).toIntArray()
        cursor = 0
    }

    private suspend fun ensureCatalog() {
        if (!loadedFromDisk) {
            loadedFromDisk = true
            val snapshot = catalogStore.load()
            if (snapshot != null && snapshot.album == albumName) {
                catalog = snapshot.entries
                catalogFetchedAtMs = snapshot.fetchedAtMs
                if (catalog.isNotEmpty()) {
                    reshuffle()
                }
            } else if (snapshot != null) {
                // album setting changed since last save → drop stale cache
                catalogStore.clear()
            }
        }
        if (catalog.isEmpty() || isStale()) {
            runCatching { refreshCatalogLocked() }
                .onFailure { log.e("background catalog refresh failed", it) }
        }
    }

    private fun isStale(): Boolean = now() - catalogFetchedAtMs > catalogTtlMs()

    /** Public entry point — acquires the mutex so it is safe to call from external scopes.
     *  Returns the new catalog size on success; throws on failure. */
    suspend fun refreshCatalog(): Int = mutex.withLock { refreshCatalogLocked() }

    /** Performs the actual catalog refresh. Must only be called while `mutex` is held.
     *  Throws on any failure (network error, missing album, empty result). */
    private suspend fun refreshCatalogLocked(): Int {
        val fetched =
            if (albumName.isBlank()) {
                client.listCatalog()
            } else {
                val albumId =
                    resolvedAlbumId
                        ?: resolveAlbumId()
                        ?: throw IllegalStateException("immich album not found: $albumName")
                client.listCatalog(albumId = albumId)
            }
        if (fetched.isEmpty()) {
            throw IllegalStateException("immich returned an empty catalog")
        }
        catalog = fetched
        catalogFetchedAtMs = now()
        catalogStore.save(albumName, catalog, catalogFetchedAtMs)
        enrichmentCache.evictAll()
        reshuffle()
        return fetched.size
    }

    private suspend fun resolveAlbumId(): String? {
        val album = client.getAlbumByName(albumName)
        if (album == null) {
            log.w("immich album not found", null, "name" to albumName)
            return null
        }
        resolvedAlbumId = album.id
        return album.id
    }

    /** Fetch overlay metadata for a slide, consulting the enrichment cache first.
     *  Always returns a media object; overlay fields are null if the network call fails. */
    private suspend fun fetchMedia(entry: ImmichCatalogEntry): SlideshowMedia {
        val asset =
            enrichmentCache.get(entry.id) ?: runCatching { client.getAsset(entry.id) }
                .onSuccess { enrichmentCache.put(entry.id, it) }
                .getOrElse {
                    log.w("getAsset failed; rendering without overlay", it, "id" to entry.id)
                    return SlideshowMedia(url = mediaUrl(entry), requestHeaders = mediaHeaders(entry))
                }
        return SlideshowMedia(
            url = mediaUrl(entry),
            requestHeaders = mediaHeaders(entry),
            title = asset.originalFileName,
            date = Instant.ofEpochMilli(asset.fileCreatedAt.toEpochMilliseconds()),
            locationLabel = asset.exifInfo?.formattedLocation,
        )
    }

    private fun scheduleLookahead() {
        val end = (cursor + LOOKAHEAD_COUNT).coerceAtMost(shuffledIndices.size)
        for (i in cursor until end) {
            val entry = catalog[shuffledIndices[i]]
            if (enrichmentCache.get(entry.id) != null) {
                continue
            }
            prefetchScope.launch {
                runCatching { client.getAsset(entry.id) }
                    .onSuccess { enrichmentCache.put(entry.id, it) }
            }
        }
    }

    private fun mediaUrl(entry: ImmichCatalogEntry): String =
        if (entry.type == IMMICH_VIDEO) {
            client.getVideoPlaybackUrl(entry.id)
        } else {
            client.getThumbnailUrl(entry.id)
        }

    private fun mediaHeaders(entry: ImmichCatalogEntry): Map<String, String> =
        if (entry.type == IMMICH_VIDEO) {
            client.authHeaders()
        } else {
            emptyMap()
        }

    private val SlideshowViewport.orientation: ImmichAssetOrientation
        get() =
            when (this) {
                SlideshowViewport.Landscape -> ImmichAssetOrientation.Landscape
                SlideshowViewport.Portrait -> ImmichAssetOrientation.Portrait
            }
}
