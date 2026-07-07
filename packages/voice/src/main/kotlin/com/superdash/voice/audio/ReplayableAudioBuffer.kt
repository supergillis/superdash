package com.superdash.voice.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** Replays a hot upstream audio flow on demand.
 *
 *  Maintains two views of the same source:
 *  - [liveFlow]: forwards frames as they arrive (single-consumer Channel).
 *  - [replayFlow]: re-emits every captured frame in order (multi-consumer).
 *
 *  The producer coroutine drains the source independently of the live
 *  consumer so that a short-circuiting consumer (local STT that emits a
 *  final transcript on the first frame) does not also short-circuit the
 *  replay buffer. The replay buffer is the source of truth for fallback
 *  stages.
 *
 *  [maxFrames] caps the captured frames. `null` keeps unbounded behavior
 *  (current default — bounded only by the 60s assist timeout, ~2 MB at
 *  16 kHz/16-bit mono). Bounded buffers drop the oldest frames once the
 *  cap is reached and flip [truncated] to true so the caller can log
 *  the partial-replay condition. */
class ReplayableAudioBuffer(
    source: Flow<ShortArray>,
    scope: CoroutineScope,
    private val maxFrames: Int? = null,
) {
    private val liveFrames = Channel<ShortArray>(Channel.UNLIMITED)
    private val replayableFrames = ArrayDeque<ShortArray>()

    @Volatile
    var truncated: Boolean = false
        private set

    private val producer =
        scope.launch {
            try {
                source.collect { frame ->
                    val copy = frame.copyOf()
                    synchronized(replayableFrames) {
                        val cap = maxFrames
                        if (cap != null && replayableFrames.size >= cap) {
                            replayableFrames.removeFirst()
                            truncated = true
                        }
                        replayableFrames.addLast(copy)
                    }
                    liveFrames.send(copy.copyOf())
                }
            } finally {
                liveFrames.close()
            }
        }

    fun liveFlow(): Flow<ShortArray> =
        flow {
            for (frame in liveFrames) {
                emit(frame.copyOf())
            }
        }

    suspend fun awaitComplete() {
        producer.join()
    }

    suspend fun cancel() {
        producer.cancelAndJoin()
    }

    fun replayFlow(): Flow<ShortArray> =
        flow {
            val snapshot =
                synchronized(replayableFrames) {
                    replayableFrames.map { it.copyOf() }
                }
            for (frame in snapshot) {
                emit(frame.copyOf())
            }
        }
}
