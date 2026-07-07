package com.superdash.screensaver.slideshow

import java.time.Instant

data class SlideshowMedia(
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val title: String? = null,
    val date: Instant? = null,
    val locationLabel: String? = null,
)

sealed interface SlideshowItem {
    val media: List<SlideshowMedia>
}

data class SlideshowImage(
    override val media: List<SlideshowMedia>,
    val isStacked: Boolean = false,
    val fillViewport: Boolean = true,
) : SlideshowItem {
    init {
        require(media.isNotEmpty()) { "SlideshowImage requires at least one image" }
    }
}

data class SlideshowVideo(
    val video: SlideshowMedia,
    val fillViewport: Boolean = true,
) : SlideshowItem {
    override val media: List<SlideshowMedia> = listOf(video)
}

enum class SlideshowViewport {
    Landscape,
    Portrait,
}

/** Pluggable source of slideshow items. Implementations manage their own
 *  list/refresh internally. */
interface SlideshowSource {
    /** Stable identifier used for [com.superdash.screensaver.slideshow.SlideshowScreensaver.id].
     *  e.g. "picsum", "media_library". */
    val id: String

    suspend fun next(): SlideshowItem? = next(SlideshowViewport.Landscape)

    /** Returns the next item, or null if currently unavailable
     *  (network down, source removed, empty source). The caller advances
     *  on the next tick regardless. */
    suspend fun next(viewport: SlideshowViewport): SlideshowItem?
}
