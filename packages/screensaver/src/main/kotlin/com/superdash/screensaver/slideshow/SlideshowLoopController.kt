package com.superdash.screensaver.slideshow

import com.superdash.core.log.Log
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
private val log = Log("SlideshowLoop")

class SlideshowLoopController(
    private val source: SlideshowSource,
    private val intervalMs: Long,
    historyCapacity: Int,
    private val scope: CoroutineScope,
    initialViewport: SlideshowViewport = SlideshowViewport.Landscape,
    private val videoTimeoutMs: Long = VIDEO_TIMEOUT_MS,
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
        log.i("navigate", "direction" to "forward")
        requests.trySend(NavRequest.Forward)
    }

    fun requestBack() {
        log.i("navigate", "direction" to "back")
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
                    // A video normally ends with notifyVideoFinished(). The cap only
                    // rescues a stalled player that never reports completion.
                    val waitMs =
                        if (history.current is SlideshowVideo) {
                            videoTimeoutMs
                        } else {
                            intervalMs
                        }
                    val nextRequest =
                        select<NavRequest> {
                            requests.onReceive { request -> request }
                            onTimeout(waitMs) {
                                if (history.current is SlideshowVideo) {
                                    log.w("video did not finish; advancing", null, "timeoutMs" to waitMs)
                                }
                                NavRequest.Forward
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

    companion object {
        /** Videos carry no duration, so this is a fixed ceiling on one video. */
        const val VIDEO_TIMEOUT_MS = 5 * 60_000L
    }

    private enum class NavRequest {
        Forward,
        Back,
        VideoFinished,
    }
}
