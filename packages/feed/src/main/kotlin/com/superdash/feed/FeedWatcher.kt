package com.superdash.feed

import com.superdash.core.log.Log
import com.superdash.ha.EntityState
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

private val log = Log("FeedWatcher")

class FeedWatcher(
    private val scope: CoroutineScope,
    private val feedsFlow: Flow<List<FeedConfig>>,
    private val enabledFlow: Flow<Boolean>,
    private val observeEntity: (entityId: String) -> Flow<EntityState?>,
    private val bus: KioskEventBus,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private data class Subscription(
        val triggerEntity: String,
        val job: Job,
    )

    private val subscriptions = mutableMapOf<String, Subscription>()
    private val lastStateById = mutableMapOf<String, String?>()
    private val lastFireById = mutableMapOf<String, Long>()
    private val started = AtomicBoolean(false)

    private val mutableActiveFeeds = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Feed id to the epoch millis of its rising edge, for sustained feeds only.
     *  Level state stays out of [KioskEventBus]: with replay 0 and DROP_OLDEST a
     *  dropped deactivation would strand the overlay on screen. */
    val activeFeeds: StateFlow<Map<String, Long>> = mutableActiveFeeds.asStateFlow()

    // reconcile() runs on the combine collector while each feed's handleUpdate
    // runs on its own coroutine, all on a multi-threaded dispatcher. Serialize the
    // three shared maps so concurrent access can't corrupt them or drop a ring.
    private val mutex = Mutex()

    companion object {
        const val DEBOUNCE_MS = 5_000L
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            combine(enabledFlow, feedsFlow) { enabled, configs ->
                if (enabled) {
                    configs
                } else {
                    emptyList()
                }
            }.distinctUntilChanged().collect { configs ->
                reconcile(configs)
            }
        }
    }

    private suspend fun reconcile(configs: List<FeedConfig>) {
        mutex.withLock {
            val wantedById = configs.associateBy { it.id }
            // Cancel removed AND triggerEntity-changed subscriptions.
            val toCancel =
                subscriptions
                    .filter { (id, sub) ->
                        val wanted = wantedById[id]
                        wanted == null || wanted.triggerEntity != sub.triggerEntity
                    }.keys
                    .toList()
            for (id in toCancel) {
                subscriptions.remove(id)?.job?.cancel()
                lastStateById.remove(id)
                lastFireById.remove(id)
                mutableActiveFeeds.update { it - id }
            }
            for (config in configs) {
                if (subscriptions.containsKey(config.id)) {
                    continue
                }
                val job =
                    scope.launch {
                        observeEntity(config.triggerEntity).collect { entity ->
                            handleUpdate(config, entity)
                        }
                    }
                subscriptions[config.id] = Subscription(config.triggerEntity, job)
            }
        }
    }

    private suspend fun handleUpdate(config: FeedConfig, entity: EntityState?) {
        when (val trigger = config.trigger) {
            is FeedTrigger.Momentary -> handleMomentaryUpdate(config, entity)
            is FeedTrigger.Sustained -> handleSustainedUpdate(config, trigger, entity)
        }
    }

    private suspend fun handleMomentaryUpdate(config: FeedConfig, entity: EntityState?) {
        val newState = entity?.state
        val fireAt =
            mutex.withLock {
                val previousState = lastStateById[config.id]
                lastStateById[config.id] = newState
                if (!isRing(config.triggerEntity, previousState, newState)) {
                    return@withLock null
                }
                val now = nowEpochMs()
                val lastFire = lastFireById[config.id]
                if (lastFire != null && now - lastFire < DEBOUNCE_MS) {
                    log.i("ring debounced", "feed" to config.id, "msSinceLast" to (now - lastFire))
                    return@withLock null
                }
                lastFireById[config.id] = now
                now
            }
        if (fireAt != null) {
            log.i("ring", "feed" to config.id, "name" to config.name)
            bus.emit(KioskEvent.FeedActivated(config.id, fireAt))
        }
    }

    private suspend fun handleSustainedUpdate(
        config: FeedConfig,
        trigger: FeedTrigger.Sustained,
        entity: EntityState?,
    ) {
        val active = trigger.isActive(entity?.state)
        val activatedAt =
            mutex.withLock {
                val wasActive = mutableActiveFeeds.value.containsKey(config.id)
                if (active == wasActive) {
                    return@withLock null
                }
                if (!active) {
                    mutableActiveFeeds.update { it - config.id }
                    return@withLock null
                }
                val now = nowEpochMs()
                mutableActiveFeeds.update { it + (config.id to now) }
                now
            }
        if (activatedAt != null) {
            log.i("sustained active", "feed" to config.id, "name" to config.name)
            bus.emit(KioskEvent.FeedActivated(config.id, activatedAt))
        }
    }

    private fun isRing(triggerEntity: String, previous: String?, current: String?): Boolean {
        if (current == null) {
            return false
        }
        val prefix = triggerEntity.substringBefore('.')
        if (previous == null) {
            return false
        }
        return when (prefix) {
            "binary_sensor" -> current == "on" && previous != "on"
            "event" -> current != previous
            else ->
                current != previous &&
                    current !in setOf("off", "unavailable", "unknown", "")
        }
    }
}
