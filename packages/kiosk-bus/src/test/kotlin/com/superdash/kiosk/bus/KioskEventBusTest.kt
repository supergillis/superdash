package com.superdash.kiosk.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KioskEventBusTest {
    @Test
    fun `single collector receives emitted event`() =
        runTest {
            val bus = KioskEventBus()
            val received = async { bus.events.first() }
            advanceUntilIdle()
            bus.emit(KioskEvent.UserTouched)
            assertEquals(KioskEvent.UserTouched, received.await())
        }

    @Test
    fun `multiple concurrent collectors all receive every event`() =
        runTest {
            val bus = KioskEventBus()
            val collectorA = async { bus.events.take(2).toList() }
            val collectorB = async { bus.events.take(2).toList() }
            advanceUntilIdle()
            bus.emit(KioskEvent.UserTouched)
            bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            advanceUntilIdle()
            val expected = listOf(KioskEvent.UserTouched, KioskEvent.WakeWordDetected("hey_jarvis"))
            assertEquals(expected, collectorA.await())
            assertEquals(expected, collectorB.await())
        }

    @Test
    fun `replay zero late collector misses prior events`() =
        runTest {
            val bus = KioskEventBus()
            bus.emit(KioskEvent.UserTouched)
            advanceUntilIdle()
            val late = async { bus.events.first() }
            advanceUntilIdle()
            bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            assertEquals(KioskEvent.WakeWordDetected("hey_jarvis"), late.await())
        }

    @Test
    fun `emit does not throw while filling the 64-event buffer`() {
        val bus = KioskEventBus()
        repeat(64) {
            bus.emit(KioskEvent.UserTouched)
        }
    }
}
