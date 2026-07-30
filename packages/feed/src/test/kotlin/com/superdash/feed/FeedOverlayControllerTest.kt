package com.superdash.feed

import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedOverlayControllerTest {
    private val config =
        FeedConfig(
            id = "a",
            name = "Front",
            triggerEntity = "binary_sensor.front",
            cameraEntity = "camera.front",
        )

    @Test
    fun `bus event flips state to Showing with resolved config and timestamp`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                FeedOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    feedsFlow = flowOf(listOf(config)),
                    nowEpochMs = { 999L },
                )
            advanceUntilIdle()
            assertEquals(FeedState.Idle, presenter.state.value)

            bus.emit(KioskEvent.FeedActivated(config.id, 4242L))
            advanceUntilIdle()

            val state = presenter.state.value
            assertTrue(state is FeedState.Showing)
            assertEquals(config, (state as FeedState.Showing).config)
            assertEquals(4242L, state.openedAtEpochMs)
        }

    @Test
    fun `bus event with unknown feed id is dropped`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                FeedOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    feedsFlow = flowOf(listOf(config)),
                    nowEpochMs = { 1L },
                )
            advanceUntilIdle()

            bus.emit(KioskEvent.FeedActivated("does_not_exist", 1L))
            advanceUntilIdle()

            assertEquals(FeedState.Idle, presenter.state.value)
        }

    @Test
    fun `close flips Showing back to Idle`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                FeedOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    feedsFlow = flowOf(listOf(config)),
                    nowEpochMs = { 1L },
                )
            advanceUntilIdle()
            bus.emit(KioskEvent.FeedActivated(config.id, 1L))
            advanceUntilIdle()
            assertTrue(presenter.state.value is FeedState.Showing)

            presenter.close()
            assertEquals(FeedState.Idle, presenter.state.value)
        }

    @Test
    fun `show bypasses the bus and sets Showing directly`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                FeedOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    feedsFlow = flowOf(emptyList()),
                    nowEpochMs = { 7L },
                )
            presenter.show(config)
            val state = presenter.state.value
            assertTrue(state is FeedState.Showing)
            assertEquals(config, (state as FeedState.Showing).config)
            assertEquals(7L, state.openedAtEpochMs)
        }
}
