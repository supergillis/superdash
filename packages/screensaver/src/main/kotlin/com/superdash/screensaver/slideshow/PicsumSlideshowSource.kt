package com.superdash.screensaver.slideshow

import kotlin.random.Random

/** Picsum-backed source. Picsum (Lorem Picsum, an Unsplash-curated subset)
 *  returns a fresh JPEG for any /seed/$x/$w/$h request. Random images, no
 *  metadata, no auth, no rate limits. */
class PicsumSlideshowSource(
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val random: () -> Int = { Random.nextInt() },
) : SlideshowSource {
    override val id = "picsum"

    override suspend fun next(viewport: SlideshowViewport): SlideshowItem {
        val seed = random()
        return SlideshowImage(media = listOf(SlideshowMedia(url = "https://picsum.photos/seed/$seed/$width/$height")))
    }
}
