package com.superdash.feed

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

private val log = Log("FeedOverlayController")

/** Owns the overlay's UI state ([FeedState]).
 *
 *  Subscribes to [KioskEvent.FeedActivated] and flips to
 *  [FeedState.Showing], resolving the typed [FeedConfig] from
 *  the feed id carried on the bus. Dismiss + Settings-test bypass
 *  are direct methods (Principle 1: commands stay direct). */
class FeedOverlayController(
    scope: CoroutineScope,
    bus: KioskEventBus,
    feedsFlow: Flow<List<FeedConfig>>,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val configsById: StateFlow<Map<String, FeedConfig>> =
        feedsFlow
            .map { list -> list.associateBy { it.id } }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    private val mutableState = MutableStateFlow<FeedState>(FeedState.Idle)
    val state: StateFlow<FeedState> = mutableState.asStateFlow()

    init {
        scope.launch {
            bus.events.filterIsInstance<KioskEvent.FeedActivated>().collect { event ->
                val resolved = configsById.value[event.feedId]
                if (resolved == null) {
                    log.w(
                        "ring with no matching config",
                        null,
                        "feedId" to event.feedId,
                    )
                    return@collect
                }
                log.i("ring → Showing", "feed" to resolved.id)
                mutableState.value = FeedState.Showing(resolved, event.timestampMs)
            }
        }
    }

    fun close() {
        mutableState.value = FeedState.Idle
    }

    /** Force the overlay open for [config], bypassing entity observation
     *  and the master toggle. Used by Settings' Test button. */
    fun show(config: FeedConfig) {
        log.i("show (manual test)", "feed" to config.id)
        mutableState.value = FeedState.Showing(config, nowEpochMs())
    }
}
