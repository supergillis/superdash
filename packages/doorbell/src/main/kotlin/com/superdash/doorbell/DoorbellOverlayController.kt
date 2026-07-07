package com.superdash.doorbell

import com.superdash.core.log.Log
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val log = Log("DoorbellOverlayController")

/** Owns the overlay's UI state ([DoorbellState]).
 *
 *  Subscribes to [KioskEvent.DoorbellRingStarted] and flips to
 *  [DoorbellState.Showing], resolving the typed [DoorbellConfig] from
 *  the doorbell id carried on the bus. Dismiss + Settings-test bypass
 *  are direct methods (Principle 1: commands stay direct). */
class DoorbellOverlayController(
    scope: CoroutineScope,
    bus: KioskEventBus,
    doorbellsFlow: Flow<List<DoorbellConfig>>,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val configsById: StateFlow<Map<String, DoorbellConfig>> =
        doorbellsFlow
            .map { list -> list.associateBy { it.id } }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    private val mutableState = MutableStateFlow<DoorbellState>(DoorbellState.Idle)
    val state: StateFlow<DoorbellState> = mutableState.asStateFlow()

    init {
        scope.launch {
            bus.events.filterIsInstance<KioskEvent.DoorbellRingStarted>().collect { event ->
                val resolved = configsById.value[event.doorbellId]
                if (resolved == null) {
                    log.w(
                        "ring with no matching config",
                        null,
                        "doorbellId" to event.doorbellId,
                    )
                    return@collect
                }
                log.i("ring → Showing", "doorbell" to resolved.id)
                mutableState.value = DoorbellState.Showing(resolved, event.timestampMs)
            }
        }
    }

    fun close() {
        mutableState.value = DoorbellState.Idle
    }

    /** Force the overlay open for [config], bypassing entity observation
     *  and the master toggle. Used by Settings' Test button. */
    fun show(config: DoorbellConfig) {
        log.i("show (manual test)", "doorbell" to config.id)
        mutableState.value = DoorbellState.Showing(config, nowEpochMs())
    }
}
