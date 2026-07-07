package com.superdash.doorbell

import com.superdash.ha.EntityState
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoorbellWatcherTest {
    private fun entity(state: String): EntityState =
        EntityState(
            entityId = "binary_sensor.front_door",
            state = state,
            attributes = JsonObject(emptyMap()),
        )

    private val configA = DoorbellConfig("a", "Front", "binary_sensor.front_door", "camera.front")

    private fun ringEvents(received: List<KioskEvent>): List<KioskEvent.DoorbellRingStarted> =
        received.filterIsInstance<KioskEvent.DoorbellRingStarted>()

    @Test
    fun `binary_sensor rising edge emits DoorbellRingStarted`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(entity("off"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 42L },
                )
            advanceUntilIdle()
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = entity("on")
            advanceUntilIdle()

            val events = ringEvents(received)
            assertEquals(1, events.size)
            assertEquals(configA.id, events.first().doorbellId)
            assertEquals(42L, events.first().timestampMs)
            collectJob.cancel()
        }

    @Test
    fun `binary_sensor rising edge off to on fires once`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(entity("off"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = entity("on")
            advanceUntilIdle()

            assertEquals(1, ringEvents(received).size)
            collectJob.cancel()
        }

    @Test
    fun `on to off does not fire`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(entity("on"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = entity("off")
            advanceUntilIdle()

            assertTrue(ringEvents(received).isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `same doorbell within 5s coalesces`() =
        runTest {
            val triggerFlow = MutableStateFlow<EntityState?>(entity("off"))
            var now = 0L
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerFlow },
                    bus = bus,
                    nowEpochMs = { now },
                )
            watcher.start()
            advanceUntilIdle()

            now = 1000L
            triggerFlow.value = entity("on")
            advanceUntilIdle()
            assertEquals(1, ringEvents(received).size)
            assertEquals(1000L, ringEvents(received).first().timestampMs)

            triggerFlow.value = entity("off")
            advanceUntilIdle()

            now = 4000L
            triggerFlow.value = entity("on")
            advanceUntilIdle()

            // Within 5s of last fire. Debounced, so no new emission.
            assertEquals(1, ringEvents(received).size)
            collectJob.cancel()
        }

    @Test
    fun `same doorbell after 5s refreshes`() =
        runTest {
            val triggerFlow = MutableStateFlow<EntityState?>(entity("off"))
            var now = 0L
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerFlow },
                    bus = bus,
                    nowEpochMs = { now },
                )
            watcher.start()
            advanceUntilIdle()

            now = 1000L
            triggerFlow.value = entity("on")
            advanceUntilIdle()
            triggerFlow.value = entity("off")
            advanceUntilIdle()

            now = 7000L // 6s after first ring
            triggerFlow.value = entity("on")
            advanceUntilIdle()

            val events = ringEvents(received)
            assertEquals(2, events.size)
            assertEquals(7000L, events.last().timestampMs)
            collectJob.cancel()
        }

    @Test
    fun `cross doorbell ring emits a separate event`() =
        runTest {
            val configB = DoorbellConfig("b", "Back", "binary_sensor.back_door", "camera.back")
            val triggerA = MutableStateFlow<EntityState?>(entity("off"))
            val triggerB =
                MutableStateFlow<EntityState?>(
                    EntityState(
                        entityId = "binary_sensor.back_door",
                        state = "off",
                        attributes = JsonObject(emptyMap()),
                    ),
                )
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA, configB)),
                    enabledFlow = flowOf(true),
                    observeEntity = { id ->
                        if (id == "binary_sensor.front_door") {
                            triggerA
                        } else {
                            triggerB
                        }
                    },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerA.value = entity("on")
            advanceUntilIdle()
            assertEquals(1, ringEvents(received).size)
            assertEquals("a", ringEvents(received).last().doorbellId)

            triggerB.value =
                EntityState(
                    entityId = "binary_sensor.back_door",
                    state = "on",
                    attributes = JsonObject(emptyMap()),
                )
            advanceUntilIdle()
            assertEquals(2, ringEvents(received).size)
            assertEquals("b", ringEvents(received).last().doorbellId)
            collectJob.cancel()
        }

    @Test
    fun `removing config cancels its subscription`() =
        runTest {
            val configB = DoorbellConfig("b", "Back", "binary_sensor.back_door", "camera.back")
            val triggerB =
                MutableStateFlow<EntityState?>(
                    EntityState(
                        entityId = "binary_sensor.back_door",
                        state = "off",
                        attributes = JsonObject(emptyMap()),
                    ),
                )
            val configsFlow = MutableStateFlow(listOf(configA, configB))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { id ->
                        when (id) {
                            "binary_sensor.front_door" -> MutableStateFlow(entity("off"))
                            else -> triggerB
                        }
                    },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()

            configsFlow.value = listOf(configA) // drop B
            advanceUntilIdle()

            triggerB.value =
                EntityState(
                    entityId = "binary_sensor.back_door",
                    state = "on",
                    attributes = JsonObject(emptyMap()),
                )
            advanceUntilIdle()

            assertTrue(ringEvents(received).isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `master toggle off creates no subscriptions`() =
        runTest {
            var observeCalls = 0
            val triggerFlow = MutableStateFlow<EntityState?>(entity("off"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(false),
                    observeEntity = { _ ->
                        observeCalls++
                        triggerFlow
                    },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerFlow.value = entity("on")
            advanceUntilIdle()

            assertEquals(0, observeCalls)
            assertTrue(ringEvents(received).isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `second start does not launch a second collector`() =
        runTest {
            // start() is called from Application.onCreate; a double-call would
            // attach a second collector on (enabled, doorbells), racing reconcile()
            // runs against the shared maps. Catch that by counting collector
            // attaches on doorbellsFlow.
            var collectorAttaches = 0
            val configsFlow = MutableStateFlow(listOf(configA))
            val instrumentedConfigsFlow =
                kotlinx.coroutines.flow.flow {
                    collectorAttaches++
                    configsFlow.collect { emit(it) }
                }
            val bus = KioskEventBus()
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = instrumentedConfigsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> MutableStateFlow<EntityState?>(entity("off")) },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()
            watcher.start() // second call — must be ignored
            advanceUntilIdle()

            assertEquals(1, collectorAttaches)
        }

    @Test
    fun `editing triggerEntity while keeping id starts new subscription`() =
        runTest {
            val oldTrigger =
                MutableStateFlow<EntityState?>(
                    EntityState(
                        entityId = "binary_sensor.old",
                        state = "off",
                        attributes = JsonObject(emptyMap()),
                    ),
                )
            val newTrigger =
                MutableStateFlow<EntityState?>(
                    EntityState(
                        entityId = "binary_sensor.new",
                        state = "off",
                        attributes = JsonObject(emptyMap()),
                    ),
                )
            val configsFlow =
                MutableStateFlow(
                    listOf(DoorbellConfig("a", "Front", "binary_sensor.old", "camera.front")),
                )
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                DoorbellWatcher(
                    scope = TestScope(testScheduler),
                    doorbellsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { id ->
                        when (id) {
                            "binary_sensor.old" -> oldTrigger
                            "binary_sensor.new" -> newTrigger
                            else -> MutableStateFlow(null)
                        }
                    },
                    bus = bus,
                    nowEpochMs = { 0L },
                )
            watcher.start()
            advanceUntilIdle()

            // Edit config: same id, different triggerEntity
            configsFlow.value =
                listOf(DoorbellConfig("a", "Front", "binary_sensor.new", "camera.front"))
            advanceUntilIdle()

            // Old trigger firing should now be ignored.
            oldTrigger.value =
                EntityState(
                    entityId = "binary_sensor.old",
                    state = "on",
                    attributes = JsonObject(emptyMap()),
                )
            advanceUntilIdle()
            assertTrue(ringEvents(received).isEmpty())

            // New trigger firing should fire.
            newTrigger.value =
                EntityState(
                    entityId = "binary_sensor.new",
                    state = "on",
                    attributes = JsonObject(emptyMap()),
                )
            advanceUntilIdle()
            val events = ringEvents(received)
            assertEquals(1, events.size)
            assertEquals("a", events.first().doorbellId)
            collectJob.cancel()
        }
}
