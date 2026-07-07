package com.superdash.screensaver.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowHistoryTest {
    private fun item(url: String): SlideshowItem =
        SlideshowImage(media = listOf(SlideshowMedia(url = url)))

    @Test
    fun `empty history has null current`() {
        val history = SlideshowHistory(capacity = 5)
        assertNull(history.current)
    }

    @Test
    fun `pushAndAdvance sets current to pushed item`() {
        val history = SlideshowHistory(capacity = 5)
        val a = item("a")
        history.pushAndAdvance(a)
        assertEquals(a, history.current)
    }

    @Test
    fun `goBack on empty history returns false`() {
        val history = SlideshowHistory(capacity = 5)
        assertFalse(history.goBack())
        assertNull(history.current)
    }

    @Test
    fun `goForward on empty history returns false`() {
        val history = SlideshowHistory(capacity = 5)
        assertFalse(history.goForward())
        assertNull(history.current)
    }

    @Test
    fun `goBack after one push returns false because cursor is at head`() {
        val history = SlideshowHistory(capacity = 5)
        history.pushAndAdvance(item("a"))
        assertFalse(history.goBack())
        assertEquals(item("a"), history.current)
    }

    @Test
    fun `goBack after two pushes moves cursor to first item`() {
        val history = SlideshowHistory(capacity = 5)
        history.pushAndAdvance(item("a"))
        history.pushAndAdvance(item("b"))
        assertTrue(history.goBack())
        assertEquals(item("a"), history.current)
    }

    @Test
    fun `goForward after goBack walks back to tail`() {
        val history = SlideshowHistory(capacity = 5)
        history.pushAndAdvance(item("a"))
        history.pushAndAdvance(item("b"))
        history.goBack()
        assertTrue(history.goForward())
        assertEquals(item("b"), history.current)
    }

    @Test
    fun `goForward at tail returns false`() {
        val history = SlideshowHistory(capacity = 5)
        history.pushAndAdvance(item("a"))
        history.pushAndAdvance(item("b"))
        assertFalse(history.goForward())
        assertEquals(item("b"), history.current)
    }

    @Test
    fun `capacity trims oldest entries`() {
        val history = SlideshowHistory(capacity = 3)
        history.pushAndAdvance(item("a"))
        history.pushAndAdvance(item("b"))
        history.pushAndAdvance(item("c"))
        history.pushAndAdvance(item("d"))
        assertEquals(item("d"), history.current)
        history.goBack()
        assertEquals(item("c"), history.current)
        history.goBack()
        assertEquals(item("b"), history.current)
        assertFalse(history.goBack())
        assertEquals(item("b"), history.current)
    }

    @Test
    fun `pushAndAdvance from mid-history truncates forward branch`() {
        val history = SlideshowHistory(capacity = 5)
        history.pushAndAdvance(item("a"))
        history.pushAndAdvance(item("b"))
        history.pushAndAdvance(item("c"))
        history.goBack()
        history.goBack()
        history.pushAndAdvance(item("d"))
        assertEquals(item("d"), history.current)
        assertFalse(history.goForward())
    }
}
