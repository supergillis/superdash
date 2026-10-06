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

    private data class AutoClose(
        val feedId: String,
        val seconds: Int,
        val openedAtEpochMs: Long,
    )

    private var autoCloseKey: AutoClose? = null
    private var autoCloseJob: Job? = null

    val state: StateFlow<FeedState> =
        combine(
            configsById,
            activeFeedsFlow,
            momentary,
            suppressed,
            isIdleFlow,
        ) { configs, active, momentaryFeeds, suppressedIds, isIdle ->
            selectFeed(configs, active + momentaryFeeds, suppressedIds, isIdle, nowEpochMs())
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
        // openedAtEpochMs is part of the key so a re-activation of an already
        // showing feed restarts the timer: ringing again at t=59 must not leave
        // one second of overlay. Sustained feeds keep a stable timestamp while
        // they stay active, so this never thrashes their timer.
        val key =
            (current as? FeedState.Showing)
                ?.takeIf { it.config.autoCloseSec > 0 }
                ?.let { AutoClose(it.config.id, it.config.autoCloseSec, it.openedAtEpochMs) }
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
                    delay(key.seconds * 1000L)
                    closeFeed(key.feedId)
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
    nowEpochMs: Long,
): FeedState.Showing? =
    candidates
        .asSequence()
        .filter { (id, _) -> id !in suppressedIds }
        .mapNotNull { (id, activatedAt) ->
            configsById[id]?.let { config -> FeedState.Showing(config, activatedAt) }
        }.filter { showing -> showing.config.wakeScreen || !isIdle }
        .filterNot { showing -> showing.isExpiredRing(nowEpochMs) }
        .maxWithOrNull(
            compareBy({ showing -> showing.config.order }, { showing -> showing.openedAtEpochMs }),
        )

/** The auto-close timer only runs while a feed is on screen, so a momentary feed
 *  held back by the idle gate would otherwise wait there forever and paint the
 *  next time anyone touches the tablet. A ring is an edge: once its own timeout
 *  has passed it is stale, whether or not it was ever visible. A sustained feed
 *  is a level and never expires this way, nor does a feed that never auto-closes. */
private fun FeedState.Showing.isExpiredRing(nowEpochMs: Long): Boolean {
    if (config.trigger !is FeedTrigger.Momentary || config.autoCloseSec <= 0) {
        return false
    }
    return nowEpochMs - openedAtEpochMs >= config.autoCloseSec * 1000L
}
