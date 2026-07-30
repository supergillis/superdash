package com.superdash.feed

import com.superdash.core.log.Log
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val log = Log("FeedOverlayController")

/** Owns the overlay's UI state ([FeedState]).
 *
 *  The shown feed is derived, never assigned: candidates are the sustained
 *  feeds reported by [FeedWatcher.activeFeeds] plus the momentary feeds this
 *  class opens on [KioskEvent.FeedActivated]. Deriving is what makes a
 *  higher-order feed closing fall back to a still-active lower-order one
 *  without any restore logic. */
class FeedOverlayController(
    private val scope: CoroutineScope,
    bus: KioskEventBus,
    feedsFlow: Flow<List<FeedConfig>>,
    activeFeedsFlow: Flow<Map<String, Long>>,
    isIdleFlow: Flow<Boolean>,
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

    private val momentary = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val suppressed = MutableStateFlow<Set<String>>(emptySet())
    private val forced = MutableStateFlow<FeedState.Showing?>(null)

    private var autoCloseKey: Pair<String, Int>? = null
    private var autoCloseJob: Job? = null

    val state: StateFlow<FeedState> =
        combine(
            configsById,
            activeFeedsFlow,
            momentary,
            suppressed,
            isIdleFlow,
        ) { configs, active, momentaryFeeds, suppressedIds, isIdle ->
            selectFeed(configs, active + momentaryFeeds, suppressedIds, isIdle)
        }.combine(forced) { derived, manual ->
            manual ?: derived ?: FeedState.Idle
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = FeedState.Idle,
        )

    init {
        scope.launch {
            bus.events.filterIsInstance<KioskEvent.FeedActivated>().collect { event ->
                val resolved = configsById.value[event.feedId]
                if (resolved == null) {
                    log.w("activation with no matching config", null, "feedId" to event.feedId)
                    return@collect
                }
                if (resolved.trigger !is FeedTrigger.Momentary) {
                    return@collect
                }
                momentary.update { it + (event.feedId to event.timestampMs) }
            }
        }
        // A suppressed id is re-armed by leaving the candidate set. For sustained
        // feeds that is the trigger going inactive; for momentary feeds it is the
        // close itself, so they re-arm on the next ring. suppressed is itself an
        // input here: closeFeed() can write a suppression with no accompanying
        // change to activeFeedsFlow/momentary (e.g. a forced show() with nothing
        // else active), and without suppressed in the combine that write would
        // never get revisited, leaving the id stuck suppressed forever.
        scope.launch {
            combine(activeFeedsFlow, momentary, suppressed) { active, momentaryFeeds, _ ->
                active.keys + momentaryFeeds.keys
            }.collect { present ->
                suppressed.update { current -> current.filterTo(mutableSetOf()) { it in present } }
            }
        }
        scope.launch {
            state.collect { current -> syncAutoClose(current) }
        }
    }

    fun close() {
        val shown = state.value as? FeedState.Showing ?: return
        closeFeed(shown.config.id)
    }

    /** Force the overlay open for [config], bypassing trigger state, suppression,
     *  and the master toggle. Used by Settings' Test button and the sidebar. */
    fun show(config: FeedConfig) {
        log.i("show", "feed" to config.id)
        suppressed.update { it - config.id }
        forced.value = FeedState.Showing(config, nowEpochMs())
    }

    fun showById(feedId: String) {
        val resolved = configsById.value[feedId]
        if (resolved == null) {
            log.w("show requested for unknown feed", null, "feedId" to feedId)
            return
        }
        show(resolved)
    }

    private fun closeFeed(feedId: String) {
        forced.value = null
        momentary.update { it - feedId }
        suppressed.update { it + feedId }
    }

    private fun syncAutoClose(current: FeedState) {
        val key =
            (current as? FeedState.Showing)
                ?.takeIf { it.config.autoCloseSec > 0 }
                ?.let { it.config.id to it.config.autoCloseSec }
        if (key == autoCloseKey) {
            return
        }
        autoCloseKey = key
        autoCloseJob?.cancel()
        autoCloseJob =
            if (key == null) {
                null
            } else {
                scope.launch {
                    delay(key.second * 1000L)
                    closeFeed(key.first)
                }
            }
    }
}

/** Highest [FeedConfig.order] wins, ties break on the most recent activation. */
internal fun selectFeed(
    configsById: Map<String, FeedConfig>,
    candidates: Map<String, Long>,
    suppressedIds: Set<String>,
    isIdle: Boolean,
): FeedState.Showing? =
    candidates
        .asSequence()
        .filter { (id, _) -> id !in suppressedIds }
        .mapNotNull { (id, activatedAt) ->
            configsById[id]?.let { config -> FeedState.Showing(config, activatedAt) }
        }.filter { showing -> showing.config.wakeScreen || !isIdle }
        .maxWithOrNull(
            compareBy({ showing -> showing.config.order }, { showing -> showing.openedAtEpochMs }),
        )
