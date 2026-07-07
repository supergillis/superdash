package com.superdash.screensaver.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class ImageMetadataOverlayTest {
    @Test
    fun `slide caption media returns the first captioned media in a grouped slide`() {
        val first =
            SlideshowMedia(
                url = "https://example.test/first.jpg",
                date = Instant.ofEpochMilli(0),
                locationLabel = "Springfield, USA",
            )
        val second =
            SlideshowMedia(
                url = "https://example.test/second.jpg",
                date = Instant.ofEpochMilli(1),
                locationLabel = "Riverside, USA",
            )

        val media =
            slideCaptionMedia(
                SlideshowImage(
                    media = listOf(first, second),
                    isStacked = true,
                ),
            )

        assertEquals(first, media)
    }

    @Test
    fun `slide caption media skips media that have no caption text`() {
        val withoutCaption =
            SlideshowMedia(
                url = "https://example.test/filename-only.jpg",
                title = "IMG_1234.jpg",
            )
        val withCaption =
            SlideshowMedia(
                url = "https://example.test/dated.jpg",
                date = Instant.ofEpochMilli(0),
            )

        val media =
            slideCaptionMedia(
                SlideshowImage(
                    media = listOf(withoutCaption, withCaption),
                ),
            )

        assertEquals(withCaption, media)
    }

    @Test
    fun `slide caption media is null when no media has a caption`() {
        val a = SlideshowMedia(url = "https://example.test/a.jpg", title = "IMG_1.jpg")
        val b = SlideshowMedia(url = "https://example.test/b.jpg", title = "IMG_2.jpg")

        val media = slideCaptionMedia(SlideshowImage(media = listOf(a, b)))

        assertNull(media)
    }

    @Test
    fun `caption ignores filename and shows location before date time`() {
        val caption =
            metadataCaption(
                SlideshowMedia(
                    url = "https://example.test/photo.jpg",
                    title = "IMG_1234.jpg",
                    date = Instant.ofEpochMilli(0),
                    locationLabel = "Springfield, USA",
                ),
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC"),
            )

        assertEquals("Springfield, USA  Jan 1, 1970 00:00", caption)
    }

    @Test
    fun `caption falls back to date time without filename`() {
        val caption =
            metadataCaption(
                SlideshowMedia(
                    url = "https://example.test/photo.jpg",
                    title = "IMG_1234.jpg",
                    date = Instant.ofEpochMilli(0),
                ),
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC"),
            )

        assertEquals("Jan 1, 1970 00:00", caption)
    }

    @Test
    fun `caption is absent when only filename exists`() {
        val caption =
            metadataCaption(
                SlideshowMedia(
                    url = "https://example.test/photo.jpg",
                    title = "IMG_1234.jpg",
                ),
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC"),
            )

        assertNull(caption)
    }
}
