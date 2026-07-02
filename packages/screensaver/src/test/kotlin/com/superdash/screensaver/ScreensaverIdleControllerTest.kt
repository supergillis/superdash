package com.superdash.screensaver

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverIdleControllerTest {
    @Test fun `starts not idle`() =
        runTest {
            val timeout = MutableStateFlow(60)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            assertFalse(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }

    @Test fun `flips to idle after timeout`() =
        runTest {
            val timeout = MutableStateFlow(60)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            now = 60_001L
            advanceTimeBy(61_000)
            runCurrent()
            assertTrue(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }

    @Test fun `touch resets timer`() =
        runTest {
            val timeout = MutableStateFlow(60)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            now = 60_001L
            advanceTimeBy(61_000)
            runCurrent()
            assertTrue(controller.isIdle.value)
            controller.touch()
            runCurrent()
            assertFalse(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }

    @Test fun `touch before timeout keeps idle false`() =
        runTest {
            val timeout = MutableStateFlow(60)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            now = 30_000L
            controller.touch()
            runCurrent()
            now = 60_000L
            runCurrent()
            assertFalse(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }

    @Test fun `timeout zero disables idle`() =
        runTest {
            val timeout = MutableStateFlow(0)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            now = 1_000_000L
            advanceTimeBy(1_000_000)
            runCurrent()
            assertFalse(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }

    @Test fun `forceIdle flips isIdle true and survives subsequent ticks`() =
        runTest {
            val timeout = MutableStateFlow(60)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            controller.forceIdle()
            assertTrue(controller.isIdle.value)
            // Advance enough to consume the polling loop's 1s tick.
            now = 5_000L
            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(controller.isIdle.value)
            // touch() then dismisses cleanly.
            now = 6_000L
            controller.touch()
            runCurrent()
            assertFalse(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }

    @Test fun `pause clears idle and resume re-arms`() =
        runTest {
            val timeout = MutableStateFlow(60)
            var now = 0L
            val controller = ScreensaverIdleController(timeout, this, { now })
            runCurrent()
            now = 60_001L
            advanceTimeBy(61_000)
            runCurrent()
            assertTrue(controller.isIdle.value)
            controller.pause()
            runCurrent()
            assertFalse(controller.isIdle.value)
            now = 200_000L
            advanceTimeBy(1_000)
            runCurrent()
            assertFalse(controller.isIdle.value)
            controller.resume()
            runCurrent()
            assertFalse(controller.isIdle.value)
            coroutineContext.cancelChildren()
        }
}
