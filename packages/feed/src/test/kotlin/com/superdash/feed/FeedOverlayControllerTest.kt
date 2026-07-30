package com.superdash.feed

import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedOverlayControllerTest {
    private val doorbell =
        FeedConfig(
            id = "door",
            name = "Front",
            triggerEntity = "binary_sensor.front",
            cameraEntity = "camera.front",
            autoCloseSec = 60,
            order = 10,
        )

    private val monitor =
        FeedConfig(
            id = "baby",
            name = "Nursery",
            triggerEntity = "input_boolean.baby_monitor",
            cameraEntity = "camera.nursery",
            trigger = FeedTrigger.Sustained(),
            autoCloseSec = 0,
            wakeScreen = false,
            order = 0,
        )

    private fun controller(
        bus: KioskEventBus = KioskEventBus(),
        configs: MutableStateFlow<List<FeedConfig>> = MutableStateFlow(listOf(doorbell, monitor)),
        activeFeeds: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
        isIdle: MutableStateFlow<Boolean> = MutableStateFlow(false),
        scope: TestScope,
    ): FeedOverlayController =
        FeedOverlayController(
            scope = scope,
            bus = bus,
            feedsFlow = configs,
            activeFeedsFlow = activeFeeds,
            isIdleFlow = isIdle,
            nowEpochMs = { 5L },
        )

    private fun showingConfig(state: FeedState): FeedConfig? = (state as? FeedState.Showing)?.config

    @Test
    fun `sustained activity shows the feed`() =
        runTest {
            val activeFeeds = MutableStateFlow<Map<String, Long>>(emptyMap())
            val controller = controller(activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()

            activeFeeds.value = mapOf("baby" to 100L)
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
            assertEquals(100L, (controller.state.value as FeedState.Showing).openedAtEpochMs)
        }

    @Test
    fun `higher order wins over a lower order active feed`() =
        runTest {
            val bus = KioskEventBus()
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller = controller(bus = bus, activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()

            bus.emit(KioskEvent.FeedActivated("door", 200L, wakeScreen = true))
            runCurrent()

            assertEquals(doorbell, showingConfig(controller.state.value))
        }

    @Test
    fun `equal order breaks the tie on most recent activation`() =
        runTest {
            val first = monitor.copy(id = "first", order = 0)
            val second = monitor.copy(id = "second", order = 0)
            val activeFeeds = MutableStateFlow(mapOf("first" to 100L))
            val controller =
                controller(
                    configs = MutableStateFlow(listOf(first, second)),
                    activeFeeds = activeFeeds,
                    scope = TestScope(testScheduler),
                )
            runCurrent()

            activeFeeds.value = mapOf("first" to 100L, "second" to 200L)
            runCurrent()

            assertEquals(second, showingConfig(controller.state.value))
        }

    @Test
    fun `closing a higher order feed falls back to a still active feed`() =
        runTest {
            val bus = KioskEventBus()
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller = controller(bus = bus, activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()
            bus.emit(KioskEvent.FeedActivated("door", 200L, wakeScreen = true))
            runCurrent()

            controller.close()
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
        }

    @Test
    fun `closing a sustained feed suppresses it while it stays active`() =
        runTest {
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller = controller(activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()

            controller.close()
            runCurrent()

            assertEquals(FeedState.Idle, controller.state.value)
        }

    @Test
    fun `suppression clears when the feed goes inactive and it shows again`() =
        runTest {
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller = controller(activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()
            controller.close()
            runCurrent()

            activeFeeds.value = emptyMap()
            runCurrent()
            activeFeeds.value = mapOf("baby" to 300L)
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
        }

    @Test
    fun `auto close hides a momentary feed after its own timeout`() =
        runTest {
            val bus = KioskEventBus()
            val controller = controller(bus = bus, scope = TestScope(testScheduler))
            runCurrent()
            bus.emit(KioskEvent.FeedActivated("door", 200L, wakeScreen = true))
            runCurrent()
            assertTrue(controller.state.value is FeedState.Showing)

            advanceTimeBy(59_000L)
            runCurrent()
            assertTrue(controller.state.value is FeedState.Showing)

            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(FeedState.Idle, controller.state.value)
        }

    @Test
    fun `changing autoCloseSec while open restarts the timer with the new duration`() =
        runTest {
            val bus = KioskEventBus()
            val configs = MutableStateFlow(listOf(doorbell, monitor))
            val controller = controller(bus = bus, configs = configs, scope = TestScope(testScheduler))
            runCurrent()
            bus.emit(KioskEvent.FeedActivated("door", 200L, wakeScreen = true))
            runCurrent()
            assertTrue(controller.state.value is FeedState.Showing)

            advanceTimeBy(40_000L)
            runCurrent()
            assertTrue(controller.state.value is FeedState.Showing)

            configs.value = listOf(doorbell.copy(autoCloseSec = 30), monitor)
            runCurrent()
            assertTrue(controller.state.value is FeedState.Showing)

            // The original 60s boundary (t=60_000) falls inside this advance. If the
            // timer hadn't restarted at the new duration, this would already be Idle.
            advanceTimeBy(29_000L)
            runCurrent()
            assertTrue(controller.state.value is FeedState.Showing)

            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(FeedState.Idle, controller.state.value)
        }

    @Test
    fun `zero auto close keeps the feed open`() =
        runTest {
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller = controller(activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()

            advanceTimeBy(600_000L)
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
        }

    @Test
    fun `a momentary feed re-arms after auto close`() =
        runTest {
            val bus = KioskEventBus()
            val controller = controller(bus = bus, scope = TestScope(testScheduler))
            runCurrent()
            bus.emit(KioskEvent.FeedActivated("door", 200L, wakeScreen = true))
            runCurrent()
            advanceTimeBy(61_000L)
            runCurrent()
            assertEquals(FeedState.Idle, controller.state.value)

            bus.emit(KioskEvent.FeedActivated("door", 400L, wakeScreen = true))
            runCurrent()

            assertEquals(doorbell, showingConfig(controller.state.value))
        }

    @Test
    fun `a non waking feed stays hidden while idle and appears on wake`() =
        runTest {
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val isIdle = MutableStateFlow(true)
            val controller =
                controller(activeFeeds = activeFeeds, isIdle = isIdle, scope = TestScope(testScheduler))
            runCurrent()
            assertEquals(FeedState.Idle, controller.state.value)

            isIdle.value = false
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
        }

    @Test
    fun `a waking feed shows while idle`() =
        runTest {
            val waking = monitor.copy(wakeScreen = true)
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller =
                controller(
                    configs = MutableStateFlow(listOf(waking)),
                    activeFeeds = activeFeeds,
                    isIdle = MutableStateFlow(true),
                    scope = TestScope(testScheduler),
                )
            runCurrent()

            assertEquals(waking, showingConfig(controller.state.value))
        }

    @Test
    fun `bus event with an unknown feed id is dropped`() =
        runTest {
            val bus = KioskEventBus()
            val controller = controller(bus = bus, scope = TestScope(testScheduler))
            runCurrent()

            bus.emit(KioskEvent.FeedActivated("does_not_exist", 1L, wakeScreen = true))
            runCurrent()

            assertEquals(FeedState.Idle, controller.state.value)
        }

    @Test
    fun `show bypasses suppression and trigger state`() =
        runTest {
            val activeFeeds = MutableStateFlow(mapOf("baby" to 100L))
            val controller = controller(activeFeeds = activeFeeds, scope = TestScope(testScheduler))
            runCurrent()
            controller.close()
            runCurrent()
            assertEquals(FeedState.Idle, controller.state.value)

            controller.show(monitor)
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
        }

    @Test
    fun `showById resolves a configured feed`() =
        runTest {
            val controller = controller(scope = TestScope(testScheduler))
            runCurrent()

            controller.showById("baby")
            runCurrent()

            assertEquals(monitor, showingConfig(controller.state.value))
        }

    @Test
    fun `a feed closed while it is not a candidate re-arms on its next activation`() =
        runTest {
            val bus = KioskEventBus()
            val controller = controller(bus = bus, scope = TestScope(testScheduler))
            runCurrent()

            // Settings' Test button: show() a feed with no candidates behind it at all.
            controller.show(doorbell)
            runCurrent()
            assertEquals(doorbell, showingConfig(controller.state.value))

            controller.close()
            runCurrent()
            assertEquals(FeedState.Idle, controller.state.value)

            // The doorbell actually rings later. Without suppressed feeding back into
            // the re-arm collector, this activation would still find "door" suppressed
            // from the close() above and never show again.
            bus.emit(KioskEvent.FeedActivated("door", 700L, wakeScreen = true))
            runCurrent()

            assertEquals(doorbell, showingConfig(controller.state.value))
        }

    @Test
    fun `showById with an unknown id does nothing`() =
        runTest {
            val controller = controller(scope = TestScope(testScheduler))
            runCurrent()

            controller.showById("gone")
            runCurrent()

            assertEquals(FeedState.Idle, controller.state.value)
        }
}
