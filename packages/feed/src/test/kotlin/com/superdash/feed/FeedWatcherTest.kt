package com.superdash.feed

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
class FeedWatcherTest {
    private fun entity(state: String): EntityState =
        EntityState(
            entityId = "binary_sensor.front_door",
            state = state,
            attributes = JsonObject(emptyMap()),
        )

    private val configA = FeedConfig("a", "Front", "binary_sensor.front_door", "camera.front")

    private fun ringEvents(received: List<KioskEvent>): List<KioskEvent.FeedActivated> =
        received.filterIsInstance<KioskEvent.FeedActivated>()

    @Test
    fun `binary_sensor rising edge emits FeedActivated`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(entity("off"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
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
            assertEquals(configA.id, events.first().feedId)
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
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
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
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
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
    fun `same feed within 5s coalesces`() =
        runTest {
            val triggerFlow = MutableStateFlow<EntityState?>(entity("off"))
            var now = 0L
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
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
    fun `same feed after 5s refreshes`() =
        runTest {
            val triggerFlow = MutableStateFlow<EntityState?>(entity("off"))
            var now = 0L
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
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
    fun `cross feed ring emits a separate event`() =
        runTest {
            val configB = FeedConfig("b", "Back", "binary_sensor.back_door", "camera.back")
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
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA, configB)),
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
            assertEquals("a", ringEvents(received).last().feedId)

            triggerB.value =
                EntityState(
                    entityId = "binary_sensor.back_door",
                    state = "on",
                    attributes = JsonObject(emptyMap()),
                )
            advanceUntilIdle()
            assertEquals(2, ringEvents(received).size)
            assertEquals("b", ringEvents(received).last().feedId)
            collectJob.cancel()
        }

    @Test
    fun `removing config cancels its subscription`() =
        runTest {
            val configB = FeedConfig("b", "Back", "binary_sensor.back_door", "camera.back")
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
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
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
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
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
            // attach a second collector on (enabled, feeds), racing reconcile()
            // runs against the shared maps. Catch that by counting collector
            // attaches on feedsFlow.
            var collectorAttaches = 0
            val configsFlow = MutableStateFlow(listOf(configA))
            val instrumentedConfigsFlow =
                kotlinx.coroutines.flow.flow {
                    collectorAttaches++
                    configsFlow.collect { emit(it) }
                }
            val bus = KioskEventBus()
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = instrumentedConfigsFlow,
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
                    listOf(FeedConfig("a", "Front", "binary_sensor.old", "camera.front")),
                )
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
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
                listOf(FeedConfig("a", "Front", "binary_sensor.new", "camera.front"))
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
            assertEquals("a", events.first().feedId)
            collectJob.cancel()
        }

    private val sustainedConfig =
        FeedConfig(
            id = "s",
            name = "Nursery",
            triggerEntity = "input_boolean.baby_monitor",
            cameraEntity = "camera.nursery",
            trigger = FeedTrigger.Sustained(activeStates = listOf("on")),
        )

    private fun booleanEntity(state: String): EntityState =
        EntityState(
            entityId = "input_boolean.baby_monitor",
            state = state,
            attributes = JsonObject(emptyMap()),
        )

    @Test
    fun `sustained trigger already active at first snapshot enters activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(sustainedConfig)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()

            assertEquals(mapOf("s" to 11L), watcher.activeFeeds.value)
        }

    @Test
    fun `sustained trigger going inactive leaves activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(sustainedConfig)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = booleanEntity("off")
            advanceUntilIdle()

            assertEquals(emptyMap<String, Long>(), watcher.activeFeeds.value)
        }

    @Test
    fun `activation timestamp is stable while the feed stays active`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("off"))
            var clock = 100L
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(sustainedConfig.copy(trigger = FeedTrigger.Sustained(listOf("on", "playing"))))),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { clock },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = booleanEntity("on")
            advanceUntilIdle()
            clock = 200L
            triggerStateFlow.value = booleanEntity("playing")
            advanceUntilIdle()

            assertEquals(mapOf("s" to 100L), watcher.activeFeeds.value)
        }

    @Test
    fun `sustained rising edge emits FeedActivated`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("off"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(sustainedConfig)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 77L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = booleanEntity("on")
            advanceUntilIdle()

            val events = received.filterIsInstance<KioskEvent.FeedActivated>()
            assertEquals(1, events.size)
            assertEquals("s", events.first().feedId)
            collectJob.cancel()
        }

    @Test
    fun `activation carries the feed's wakeScreen flag`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("off"))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(sustainedConfig.copy(wakeScreen = false))),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 77L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = booleanEntity("on")
            advanceUntilIdle()

            val events = received.filterIsInstance<KioskEvent.FeedActivated>()
            assertEquals(1, events.size)
            assertEquals(false, events.first().wakeScreen)
            collectJob.cancel()
        }

    @Test
    fun `disabling the master toggle clears activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val enabledFlow = MutableStateFlow(true)
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(sustainedConfig)),
                    enabledFlow = enabledFlow,
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()
            assertEquals(mapOf("s" to 11L), watcher.activeFeeds.value)

            enabledFlow.value = false
            advanceUntilIdle()

            assertEquals(emptyMap<String, Long>(), watcher.activeFeeds.value)
        }

    @Test
    fun `editing activeStates while keeping the trigger entity applies the new predicate`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("off"))
            val configsFlow = MutableStateFlow(listOf(sustainedConfig))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()

            configsFlow.value =
                listOf(sustainedConfig.copy(trigger = FeedTrigger.Sustained(listOf("playing"))))
            advanceUntilIdle()

            triggerStateFlow.value = booleanEntity("playing")
            advanceUntilIdle()

            assertEquals(mapOf("s" to 11L), watcher.activeFeeds.value)
        }

    @Test
    fun `editing wakeScreen while keeping the trigger entity applies the new flag`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("off"))
            val configsFlow = MutableStateFlow(listOf(sustainedConfig))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()

            configsFlow.value = listOf(sustainedConfig.copy(wakeScreen = false))
            advanceUntilIdle()

            triggerStateFlow.value = booleanEntity("on")
            advanceUntilIdle()

            val events = ringEvents(received)
            assertEquals(1, events.size)
            assertEquals(false, events.first().wakeScreen)
            collectJob.cancel()
        }

    @Test
    fun `switching a momentary feed to sustained tracks it in activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(entity("off"))
            val configsFlow = MutableStateFlow(listOf(configA))
            val bus = KioskEventBus()
            val received = mutableListOf<KioskEvent>()
            val collectJob = launch { bus.events.toList(received) }
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = bus,
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()

            configsFlow.value =
                listOf(configA.copy(trigger = FeedTrigger.Sustained(listOf("on"))))
            advanceUntilIdle()

            triggerStateFlow.value = entity("on")
            advanceUntilIdle()

            assertEquals(mapOf("a" to 11L), watcher.activeFeeds.value)
            assertEquals(1, ringEvents(received).size)
            collectJob.cancel()
        }

    @Test
    fun `switching a sustained feed to momentary drops it from activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val configsFlow = MutableStateFlow(listOf(sustainedConfig))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()
            assertEquals(mapOf("s" to 11L), watcher.activeFeeds.value)

            configsFlow.value = listOf(sustainedConfig.copy(trigger = FeedTrigger.Momentary))
            advanceUntilIdle()

            assertEquals(emptyMap<String, Long>(), watcher.activeFeeds.value)
        }

    @Test
    fun `an unrelated edit keeps the activation timestamp`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val configsFlow = MutableStateFlow(listOf(sustainedConfig))
            var clock = 100L
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { clock },
                )
            watcher.start()
            advanceUntilIdle()
            assertEquals(mapOf("s" to 100L), watcher.activeFeeds.value)

            clock = 500L
            configsFlow.value = listOf(sustainedConfig.copy(name = "Nursery cam"))
            advanceUntilIdle()

            assertEquals(mapOf("s" to 100L), watcher.activeFeeds.value)
        }

    @Test
    fun `removing a sustained config drops it from activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val configsFlow = MutableStateFlow(listOf(sustainedConfig))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()
            assertEquals(mapOf("s" to 11L), watcher.activeFeeds.value)

            configsFlow.value = emptyList()
            advanceUntilIdle()

            assertEquals(emptyMap<String, Long>(), watcher.activeFeeds.value)
        }

    @Test
    fun `repointing a sustained config drops it from activeFeeds`() =
        runTest {
            val oldTrigger = MutableStateFlow<EntityState?>(booleanEntity("on"))
            val newTrigger =
                MutableStateFlow<EntityState?>(
                    EntityState(
                        entityId = "input_boolean.other",
                        state = "off",
                        attributes = JsonObject(emptyMap()),
                    ),
                )
            val configsFlow = MutableStateFlow(listOf(sustainedConfig))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = configsFlow,
                    enabledFlow = flowOf(true),
                    observeEntity = { id ->
                        when (id) {
                            "input_boolean.baby_monitor" -> oldTrigger
                            else -> newTrigger
                        }
                    },
                    bus = KioskEventBus(),
                    nowEpochMs = { 11L },
                )
            watcher.start()
            advanceUntilIdle()
            assertEquals(mapOf("s" to 11L), watcher.activeFeeds.value)

            configsFlow.value =
                listOf(sustainedConfig.copy(triggerEntity = "input_boolean.other"))
            advanceUntilIdle()

            assertEquals(emptyMap<String, Long>(), watcher.activeFeeds.value)
        }

    @Test
    fun `momentary feed never enters activeFeeds`() =
        runTest {
            val triggerStateFlow = MutableStateFlow<EntityState?>(entity("off"))
            val watcher =
                FeedWatcher(
                    scope = TestScope(testScheduler),
                    feedsFlow = flowOf(listOf(configA)),
                    enabledFlow = flowOf(true),
                    observeEntity = { _ -> triggerStateFlow },
                    bus = KioskEventBus(),
                    nowEpochMs = { 42L },
                )
            watcher.start()
            advanceUntilIdle()

            triggerStateFlow.value = entity("on")
            advanceUntilIdle()

            assertEquals(emptyMap<String, Long>(), watcher.activeFeeds.value)
        }
}
