package com.superdash.screensaver.slideshow

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PicsumSlideshowSourceTest {
    @Test
    fun `id is picsum`() {
        val source = PicsumSlideshowSource()
        assertEquals("picsum", source.id)
    }

    @Test
    fun `next emits item with injected random seed`() =
        runTest {
            var calls = 0
            val source =
                PicsumSlideshowSource(
                    random = {
                        calls++
                        42
                    },
                )
            val item = source.next()
            assertNotNull(item)
            assertEquals("https://picsum.photos/seed/42/1920/1080", item!!.media.first().url)
            assertEquals(1, calls)
        }

    @Test
    fun `next emits a different seed each call`() =
        runTest {
            val sequence = listOf(1, 2, 3).iterator()
            val source = PicsumSlideshowSource(random = { sequence.next() })
            val a =
                source
                    .next()!!
                    .media
                    .first()
                    .url
            val b =
                source
                    .next()!!
                    .media
                    .first()
                    .url
            assertNotEquals(a, b)
        }

    @Test
    fun `custom width and height appear in url`() =
        runTest {
            val source = PicsumSlideshowSource(width = 800, height = 600, random = { 7 })
            assertEquals(
                "https://picsum.photos/seed/7/800/600",
                source
                    .next()!!
                    .media
                    .first()
                    .url,
            )
        }
}
