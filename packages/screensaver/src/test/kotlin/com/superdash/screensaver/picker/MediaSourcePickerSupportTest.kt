package com.superdash.screensaver.picker

import com.superdash.ha.BrowseMediaSource
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourcePickerSupportTest {
    @Test
    fun `filterBrowseMediaSources matches title`() {
        val children =
            listOf(
                mediaSource("Vacation Album", "directory", "media-source://local/vacation"),
                mediaSource("Kitchen", "directory", "media-source://local/kitchen"),
            )

        val filtered = filterBrowseMediaSources(children, " vacation ")

        assertEquals(listOf("Vacation Album"), filtered.map { source -> source.title })
    }

    @Test
    fun `filterBrowseMediaSources matches media class and id`() {
        val children =
            listOf(
                mediaSource("Family", "directory", "media-source://media_source/local/family"),
                mediaSource("Camera", "image", "media-source://media_source/local/front-door.jpg"),
            )

        assertEquals(
            listOf("Camera"),
            filterBrowseMediaSources(children, "front-door").map { source -> source.title },
        )
        assertEquals(
            listOf("Camera"),
            filterBrowseMediaSources(children, "image").map { source -> source.title },
        )
    }

    @Test
    fun `filterBrowseMediaSources preserves order for blank query`() {
        val children =
            listOf(
                mediaSource("B", "directory", "media-source://local/b"),
                mediaSource("A", "directory", "media-source://local/a"),
            )

        val filtered = filterBrowseMediaSources(children, "")

        assertEquals(listOf("B", "A"), filtered.map { source -> source.title })
    }

    private fun mediaSource(
        title: String,
        mediaClass: String,
        mediaContentId: String,
    ): BrowseMediaSource =
        BrowseMediaSource(
            title = title,
            mediaClass = mediaClass,
            mediaContentId = mediaContentId,
            mediaContentType = "",
        )
}
