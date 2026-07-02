package com.superdash.screensaver.slideshow

import com.superdash.core.log.Log
import com.superdash.ha.BrowseMediaSource
import com.superdash.ha.HaMediaSourceClient
import com.superdash.screensaver.MediaLibraryOrder

private val log = Log("HaMediaLibrarySource")

private const val CACHE_TTL_MS = 50L * 60 * 1000 // < 60min Google CDN URL expiry

/** Slideshow source backed by HA's media_source. Browses the configured
 *  source on first call (and again when stale), then resolves each item's
 *  url on demand per [next] call. */
class HaMediaLibrarySource(
    private val client: HaMediaSourceClient,
    private val sourceId: String,
    private val orderKey: String,
    private val now: () -> Long = { System.currentTimeMillis() },
) : SlideshowSource {
    override val id = "media_library"

    private var cachedChildren: List<BrowseMediaSource> = emptyList()
    private var cachedAtMs: Long = 0L
    private var cursor: Int = 0

    override suspend fun next(viewport: SlideshowViewport): SlideshowItem? {
        if (shouldRefresh()) {
            val refreshed =
                runCatching { client.browseMedia(sourceId) }.getOrElse { t ->
                    log.w("browseMedia failed", t, "sourceId" to sourceId)
                    return null
                }
            cachedChildren =
                refreshed.children
                    .filter { it.mediaClass == "image" }
                    .let { applyOrder(it, orderKey) }
            cachedAtMs = now()
            cursor = 0
        }
        if (cachedChildren.isEmpty()) {
            return null
        }
        val pick = cachedChildren[cursor]
        cursor++
        if (cursor >= cachedChildren.size) {
            cachedChildren =
                if (orderKey == MediaLibraryOrder.Shuffle.key) {
                    cachedChildren.shuffled()
                } else {
                    cachedChildren
                }
            cursor = 0
        }
        val resolved =
            runCatching { client.resolveMedia(pick.mediaContentId) }.getOrElse { t ->
                log.w("resolveMedia failed", t, "mediaContentId" to pick.mediaContentId)
                return null
            }
        return SlideshowImage(media = listOf(SlideshowMedia(url = resolved.url, title = pick.title)))
    }

    private fun shouldRefresh(): Boolean = cachedChildren.isEmpty() || (now() - cachedAtMs) > CACHE_TTL_MS
}

private fun applyOrder(
    items: List<BrowseMediaSource>,
    orderKey: String,
): List<BrowseMediaSource> =
    when (orderKey) {
        MediaLibraryOrder.Shuffle.key -> items.shuffled()
        else -> items // provider order
    }
