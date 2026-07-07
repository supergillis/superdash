package com.superdash.kiosk.bus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KioskEventBusContractTest {
    @Test
    fun `DROP_OLDEST under burst does not block producer and bus still accepts new collectors`() =
        runTest {
            val bus = KioskEventBus()
            repeat(200) {
                bus.emit(KioskEvent.UserTouched)
            }
            val received = async { bus.events.first() }
            advanceUntilIdle()
            bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            assertEquals(KioskEvent.WakeWordDetected("hey_jarvis"), received.await())
        }

    @Test
    fun `emit from IO dispatcher is delivered to collector on test scheduler`() =
        runTest {
            val bus = KioskEventBus()
            val received = async { bus.events.first() }
            advanceUntilIdle()
            withContext(Dispatchers.IO) {
                bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            }
            assertEquals(KioskEvent.WakeWordDetected("hey_jarvis"), received.await())
        }

    @Test
    fun `cancelling one collector does not affect another collector`() =
        runTest {
            val bus = KioskEventBus()
            val collectorA = async { bus.events.take(2).toList() }
            val collectorB = async { bus.events.take(2).toList() }
            advanceUntilIdle()

            bus.emit(KioskEvent.UserTouched)
            advanceUntilIdle()

            collectorA.cancel()

            bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            advanceUntilIdle()

            val received = collectorB.await()
            assertEquals(2, received.size)
            assertEquals(KioskEvent.UserTouched, received[0])
            assertEquals(KioskEvent.WakeWordDetected("hey_jarvis"), received[1])
        }

    @Test
    fun `every KioskEvent variant round-trips with equality preserved`() =
        runTest {
            val variants =
                listOf(
                    KioskEvent.UserTouched,
                    KioskEvent.WakeWordDetected("hey_jarvis"),
                    KioskEvent.DoorbellRingStarted("a", 0L),
                )

            for (variant in variants) {
                val bus = KioskEventBus()
                val received = async { bus.events.first() }
                advanceUntilIdle()
                bus.emit(variant)
                assertEquals("round-trip failed for ${variant::class.simpleName}", variant, received.await())
            }
        }

    @Test
    fun `filterIsInstance passes only the matching variant and ignores others`() =
        runTest {
            val bus = KioskEventBus()
            val wakeWords =
                async {
                    bus.events
                        .filterIsInstance<KioskEvent.WakeWordDetected>()
                        .take(1)
                        .toList()
                }
            advanceUntilIdle()

            bus.emit(KioskEvent.UserTouched)
            bus.emit(KioskEvent.WakeWordDetected("hey_jarvis"))
            advanceUntilIdle()

            val received = wakeWords.await()
            assertEquals(1, received.size)
            assertEquals(KioskEvent.WakeWordDetected("hey_jarvis"), received[0])
        }
}
