package com.superdash.doorbell

import com.superdash.core.log.Log
import com.superdash.ha.EntityState
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val log = Log("DoorbellWatcher")

class DoorbellWatcher(
    private val scope: CoroutineScope,
    private val doorbellsFlow: Flow<List<DoorbellConfig>>,
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

    companion object {
        const val DEBOUNCE_MS = 5_000L
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            combine(enabledFlow, doorbellsFlow) { enabled, configs ->
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

    private fun reconcile(configs: List<DoorbellConfig>) {
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

    private fun handleUpdate(config: DoorbellConfig, entity: EntityState?) {
        val newState = entity?.state
        val previousState = lastStateById[config.id]
        lastStateById[config.id] = newState
        if (!isRing(config.triggerEntity, previousState, newState)) {
            return
        }
        val now = nowEpochMs()
        val lastFire = lastFireById[config.id]
        if (lastFire != null && now - lastFire < DEBOUNCE_MS) {
            log.i("ring debounced", "doorbell" to config.id, "msSinceLast" to (now - lastFire))
            return
        }
        lastFireById[config.id] = now
        log.i("ring", "doorbell" to config.id, "name" to config.name)
        bus.emit(KioskEvent.DoorbellRingStarted(config.id, now))
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
