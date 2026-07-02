package com.superdash.screensaver.slideshow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowLoopControllerTest {
    private fun image(url: String): SlideshowImage = SlideshowImage(media = listOf(SlideshowMedia(url = url)))

    private fun video(url: String): SlideshowVideo = SlideshowVideo(video = SlideshowMedia(url = url))

    private class FakeSource(
        private val items: MutableList<SlideshowItem?> = mutableListOf(),
    ) : SlideshowSource {
        override val id: String = "fake"
        var nextCallCount: Int = 0
            private set
        val viewports: MutableList<SlideshowViewport> = mutableListOf()

        fun enqueue(item: SlideshowItem?) {
            items.add(item)
        }

        override suspend fun next(viewport: SlideshowViewport): SlideshowItem? {
            nextCallCount++
            viewports.add(viewport)
            return if (items.isEmpty()) {
                null
            } else {
                items.removeAt(0)
            }
        }
    }

    @Test fun `auto advances after intervalMs elapses`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            source.enqueue(a)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            advanceTimeBy(29_999L)
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(b, controller.currentItem.value)

            controller.stop()
            runCurrent()
        }

    @Test fun `initial fetch uses constructor viewport before setViewport runs`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            source.enqueue(a)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                    initialViewport = SlideshowViewport.Portrait,
                )

            controller.start()
            runCurrent()

            assertEquals(a, controller.currentItem.value)
            assertEquals(listOf(SlideshowViewport.Portrait), source.viewports)

            controller.stop()
            runCurrent()
        }

    @Test fun `requestForward mid-interval advances immediately and resets the timer`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            val c = image("c")
            source.enqueue(a)
            source.enqueue(b)
            source.enqueue(c)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            advanceTimeBy(5_000L)
            controller.requestForward()
            runCurrent()
            assertEquals(b, controller.currentItem.value)

            advanceTimeBy(29_999L)
            runCurrent()
            assertEquals(b, controller.currentItem.value)

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(c, controller.currentItem.value)

            controller.stop()
            runCurrent()
        }

    @Test fun `requestBack walks history backwards without consulting source`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            source.enqueue(a)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(a, controller.currentItem.value)
            controller.requestForward()
            runCurrent()
            assertEquals(b, controller.currentItem.value)

            val callsBeforeBack = source.nextCallCount
            controller.requestBack()
            runCurrent()
            assertEquals(a, controller.currentItem.value)
            assertEquals(callsBeforeBack, source.nextCallCount)

            controller.stop()
            runCurrent()
        }

    @Test fun `requestForward after requestBack walks history forward before fetching`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            source.enqueue(a)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            controller.requestForward()
            runCurrent()
            assertEquals(b, controller.currentItem.value)
            controller.requestBack()
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            val callsBeforeReplay = source.nextCallCount
            controller.requestForward()
            runCurrent()
            assertEquals(b, controller.currentItem.value)
            assertEquals(callsBeforeReplay, source.nextCallCount)

            controller.stop()
            runCurrent()
        }

    @Test fun `video item suspends interval until notifyVideoFinished`() =
        runTest {
            val source = FakeSource()
            val v = video("v")
            val b = image("b")
            source.enqueue(v)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(v, controller.currentItem.value)

            advanceTimeBy(120_000L)
            runCurrent()
            assertEquals(v, controller.currentItem.value)

            controller.notifyVideoFinished()
            runCurrent()
            assertEquals(b, controller.currentItem.value)

            controller.stop()
            runCurrent()
        }

    @Test fun `video item still advances on requestForward without waiting for finished`() =
        runTest {
            val source = FakeSource()
            val v = video("v")
            val b = image("b")
            source.enqueue(v)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(v, controller.currentItem.value)

            controller.requestForward()
            runCurrent()
            assertEquals(b, controller.currentItem.value)

            controller.stop()
            runCurrent()
        }

    @Test fun `setViewport preserves history cursor`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            source.enqueue(a)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            controller.requestForward()
            runCurrent()
            controller.requestBack()
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            controller.setViewport(SlideshowViewport.Portrait)
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            controller.stop()
            runCurrent()
        }

    @Test fun `setViewport routes subsequent fetches to the new viewport`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            source.enqueue(a)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(SlideshowViewport.Landscape, source.viewports.last())

            controller.setViewport(SlideshowViewport.Portrait)
            controller.requestForward()
            runCurrent()
            assertEquals(b, controller.currentItem.value)
            assertEquals(SlideshowViewport.Portrait, source.viewports.last())

            controller.stop()
            runCurrent()
        }

    @Test fun `stop cancels the loop without emitting further items`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            val b = image("b")
            source.enqueue(a)
            source.enqueue(b)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            controller.stop()
            runCurrent()
            advanceTimeBy(120_000L)
            runCurrent()
            assertEquals(a, controller.currentItem.value)
        }

    @Test fun `null from source leaves current item unchanged and retries next tick`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            source.enqueue(null)
            source.enqueue(a)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertNull(controller.currentItem.value)
            assertTrue(source.nextCallCount >= 1)

            advanceTimeBy(31_000L)
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            controller.stop()
            runCurrent()
        }

    @Test fun `requestBack at head is a no-op`() =
        runTest {
            val source = FakeSource()
            val a = image("a")
            source.enqueue(a)
            val controller =
                SlideshowLoopController(
                    source = source,
                    intervalMs = 30_000L,
                    historyCapacity = 20,
                    scope = this,
                )
            controller.setViewport(SlideshowViewport.Landscape)
            controller.start()
            runCurrent()
            assertEquals(a, controller.currentItem.value)

            controller.requestBack()
            runCurrent()
            assertEquals(a, controller.currentItem.value)
            assertNotNull(controller.currentItem.value)

            controller.stop()
            runCurrent()
        }
}
