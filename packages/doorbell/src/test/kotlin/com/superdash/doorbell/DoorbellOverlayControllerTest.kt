package com.superdash.doorbell

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
class DoorbellOverlayControllerTest {
    private val config =
        DoorbellConfig(
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
                DoorbellOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    doorbellsFlow = flowOf(listOf(config)),
                    nowEpochMs = { 999L },
                )
            advanceUntilIdle()
            assertEquals(DoorbellState.Idle, presenter.state.value)

            bus.emit(KioskEvent.DoorbellRingStarted(config.id, 4242L))
            advanceUntilIdle()

            val state = presenter.state.value
            assertTrue(state is DoorbellState.Showing)
            assertEquals(config, (state as DoorbellState.Showing).config)
            assertEquals(4242L, state.openedAtEpochMs)
        }

    @Test
    fun `bus event with unknown doorbell id is dropped`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                DoorbellOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    doorbellsFlow = flowOf(listOf(config)),
                    nowEpochMs = { 1L },
                )
            advanceUntilIdle()

            bus.emit(KioskEvent.DoorbellRingStarted("does_not_exist", 1L))
            advanceUntilIdle()

            assertEquals(DoorbellState.Idle, presenter.state.value)
        }

    @Test
    fun `close flips Showing back to Idle`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                DoorbellOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    doorbellsFlow = flowOf(listOf(config)),
                    nowEpochMs = { 1L },
                )
            advanceUntilIdle()
            bus.emit(KioskEvent.DoorbellRingStarted(config.id, 1L))
            advanceUntilIdle()
            assertTrue(presenter.state.value is DoorbellState.Showing)

            presenter.close()
            assertEquals(DoorbellState.Idle, presenter.state.value)
        }

    @Test
    fun `show bypasses the bus and sets Showing directly`() =
        runTest {
            val bus = KioskEventBus()
            val presenter =
                DoorbellOverlayController(
                    scope = TestScope(testScheduler),
                    bus = bus,
                    doorbellsFlow = flowOf(emptyList()),
                    nowEpochMs = { 7L },
                )
            presenter.show(config)
            val state = presenter.state.value
            assertTrue(state is DoorbellState.Showing)
            assertEquals(config, (state as DoorbellState.Showing).config)
            assertEquals(7L, state.openedAtEpochMs)
        }
}
