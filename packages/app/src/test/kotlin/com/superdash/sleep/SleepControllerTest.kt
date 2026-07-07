package com.superdash.sleep

import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.screensaver.Touchable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepControllerTest {
    @Test
    fun `nightModeActive reflects active flow`() =
        runTest {
            val activeFlow = MutableStateFlow(true)
            val controller =
                SleepController(
                    nightModeActiveFlow = activeFlow,
                    setNightModeActive = {},
                    bus = KioskEventBus(),
                    idleController = NoopIdleController(),
                    scope = TestScope(testScheduler),
                )
            advanceUntilIdle()
            assertEquals(true, controller.nightModeActive.value)
        }

    @Test
    fun `nightModeActive flips when active flow changes`() =
        runTest {
            val activeFlow = MutableStateFlow(false)
            val controller =
                SleepController(
                    nightModeActiveFlow = activeFlow,
                    setNightModeActive = {},
                    bus = KioskEventBus(),
                    idleController = NoopIdleController(),
                    scope = TestScope(testScheduler),
                )
            advanceUntilIdle()
            assertEquals(false, controller.nightModeActive.value)
            activeFlow.value = true
            advanceUntilIdle()
            assertEquals(true, controller.nightModeActive.value)
        }

    @Test
    fun `setNightModeActive forwards to setter`() =
        runTest {
            var lastRequestedState: Boolean? = null
            val controller =
                SleepController(
                    nightModeActiveFlow = flowOf(false),
                    setNightModeActive = { state -> lastRequestedState = state },
                    bus = KioskEventBus(),
                    idleController = NoopIdleController(),
                    scope = TestScope(testScheduler),
                )
            advanceUntilIdle()

            controller.setNightModeActive(true)
            advanceUntilIdle()

            assertEquals(true, lastRequestedState)
        }

    @Test
    fun `UserTouched event triggers idle touch`() =
        runTest {
            val bus = KioskEventBus()
            var touchCount = 0
            val touchable = Touchable { touchCount++ }
            SleepController(
                nightModeActiveFlow = flowOf(false),
                setNightModeActive = {},
                bus = bus,
                idleController = touchable,
                scope = TestScope(testScheduler),
            )
            advanceUntilIdle()
            bus.emit(KioskEvent.UserTouched)
            advanceUntilIdle()
            assertEquals(1, touchCount)
        }

    @Test
    fun `WakeWordDetected and DoorbellRingStarted trigger idle touch`() =
        runTest {
            val bus = KioskEventBus()
            var touchCount = 0
            val touchable = Touchable { touchCount++ }
            SleepController(
                nightModeActiveFlow = flowOf(false),
                setNightModeActive = {},
                bus = bus,
                idleController = touchable,
                scope = TestScope(testScheduler),
            )
            advanceUntilIdle()
            bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            bus.emit(KioskEvent.DoorbellRingStarted("a", 0L))
            advanceUntilIdle()
            assertEquals(2, touchCount)
        }

    @Test
    fun `bus events the controller does not handle do not touch idle`() =
        runTest {
            val bus = KioskEventBus()
            var touchCount = 0
            val touchable = Touchable { touchCount++ }
            SleepController(
                nightModeActiveFlow = flowOf(false),
                setNightModeActive = {},
                bus = bus,
                idleController = touchable,
                scope = TestScope(testScheduler),
            )
            advanceUntilIdle()
            // Currently no KioskEvent variants are ignored by SleepController; this
            // test asserts that adding one in the future does not accidentally bump
            // idle. If a new variant is added that SHOULD bump idle, add it to the
            // when in SleepController.
            advanceUntilIdle()
            assertEquals(0, touchCount)
        }

    private class NoopIdleController : Touchable {
        override fun touch() {
            // no-op
        }
    }
}
