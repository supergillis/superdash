package com.superdash.screensaver.slideshow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/** Owns the slideshow advance/back/video-wait state machine.
 *
 *  Replaces the self-cancelling `LaunchedEffect` in `SlideshowScreensaver`.
 *  Constructed once per `(source, imageLoader)` pair; [start] launches the
 *  loop into the caller-owned [scope] and [stop] cancels it.
 *
 *  Not thread-safe: navigation requests serialize through a conflated
 *  channel and the loop is the sole writer to [currentItem]. */
class SlideshowLoopController(
    private val source: SlideshowSource,
    private val intervalMs: Long,
    historyCapacity: Int,
    private val scope: CoroutineScope,
    initialViewport: SlideshowViewport = SlideshowViewport.Landscape,
) {
    private val history = SlideshowHistory(capacity = historyCapacity)
    private val mutableCurrentItem = MutableStateFlow<SlideshowItem?>(null)
    val currentItem: StateFlow<SlideshowItem?> = mutableCurrentItem.asStateFlow()

    @Volatile private var viewport: SlideshowViewport = initialViewport

    private val requests: Channel<NavRequest> = Channel(capacity = Channel.CONFLATED)
    private var loopJob: Job? = null

    /** Idempotent. Triggers nothing on its own; the new viewport is read at
     *  the next fetch. */
    fun setViewport(value: SlideshowViewport) {
        viewport = value
    }

    fun requestForward() {
        requests.trySend(NavRequest.Forward)
    }

    fun requestBack() {
        requests.trySend(NavRequest.Back)
    }

    /** Called from `VideoPane.onFinished` to wake the loop without
     *  conflating with a user-driven forward. Behaves the same as a forward
     *  for now, but kept distinct so callers can tell intent at the
     *  channel-receive point. */
    fun notifyVideoFinished() {
        requests.trySend(NavRequest.VideoFinished)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (loopJob?.isActive == true) {
            return
        }
        loopJob =
            scope.launch {
                // Initial fetch: history is empty, so seed it before entering the wait loop.
                if (history.current == null) {
                    val first = source.next(viewport)
                    if (first != null) {
                        history.pushAndAdvance(first)
                        mutableCurrentItem.value = history.current
                    }
                }
                while (isActive) {
                    val nextRequest =
                        if (history.current is SlideshowVideo) {
                            requests.receive()
                        } else {
                            select<NavRequest> {
                                requests.onReceive { request -> request }
                                onTimeout(intervalMs) { NavRequest.Forward }
                            }
                        }
                    handle(nextRequest)
                }
            }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun handle(request: NavRequest) {
        when (request) {
            NavRequest.Back -> {
                if (history.goBack()) {
                    mutableCurrentItem.value = history.current
                }
            }
            NavRequest.Forward, NavRequest.VideoFinished -> {
                if (!history.goForward()) {
                    val next = source.next(viewport) ?: return
                    history.pushAndAdvance(next)
                }
                mutableCurrentItem.value = history.current
            }
        }
    }

    private enum class NavRequest {
        Forward,
        Back,
        VideoFinished,
    }
}
