package com.superdash.voice.audio

import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val log = Log("AudioFrameHub")

private const val STATE_PENDING = 0
private const val STATE_COLLECTING = 1
private const val STATE_HANDED_OFF = 2
private const val STATE_TIMED_OUT = 3
private const val FRAME_DURATION_MS = 10
private const val LIVE_BUFFER_MS = 15_000

// Sized by time so a cold first-use model load of several seconds cannot drop the
// start of the command.
private const val DEFAULT_STREAM_BUFFER_CAPACITY = LIVE_BUFFER_MS / FRAME_DURATION_MS
private const val UNCOLLECTED_STREAM_TIMEOUT_MS = 5_000L

/** Fan-out of recent audio frames + pre-roll snapshot for new subscribers.
 *
 *  publish() is called once per incoming mic frame; openStream() opens a
 *  new subscriber that first replays the in-memory pre-roll then drains a
 *  bounded live channel. capacity caps the pre-roll length.
 *
 *  Synchronization: a single lock guards the recent-frames deque, the
 *  subscriber set, and the closed flag. publish() runs on the producer
 *  coroutine, openStream() runs on the consumer coroutine. */
class AudioFrameHub(
    private val capacity: Int,
    private val streamScope: CoroutineScope,
    private val streamBufferCapacity: Int = DEFAULT_STREAM_BUFFER_CAPACITY,
    private val uncollectedStreamTimeoutMs: Long = UNCOLLECTED_STREAM_TIMEOUT_MS,
) {
    private val lock = Any()
    private val recentFrames = ArrayDeque<ShortArray>(capacity)
    private val subscribers = mutableSetOf<AudioFrameStream>()
    private var closed = false

    fun publish(samples: ShortArray) {
        // Empty arrays are the AudioRecord-restart sentinel; they should not be
        // buffered as pre-roll content nor forwarded to command subscribers (the
        // wake detector receives the sentinel via a separate path).
        if (samples.isEmpty()) {
            return
        }
        val copy = samples.copyOf()
        synchronized(lock) {
            if (closed) {
                return
            }
            if (recentFrames.size == capacity) {
                recentFrames.removeFirst()
            }
            recentFrames.addLast(copy)
            // Subscribers receive a reference to the same defensive copy; the
            // channel slot prevents in-place mutation across consumers, and
            // downstream code only reads from these frames.
            subscribers.forEach { stream ->
                stream.offerLiveFrame(copy)
            }
        }
    }

    fun openStream(): AudioFrameStream {
        val stream =
            synchronized(lock) {
                AudioFrameStream(
                    snapshot = recentFrames.map { it.copyOf() },
                    liveFrameCapacity = streamBufferCapacity,
                    streamScope = streamScope,
                    uncollectedStreamTimeoutMs = uncollectedStreamTimeoutMs,
                    unsubscribe = { finished ->
                        synchronized(lock) {
                            subscribers -= finished
                        }
                    },
                ).also { opened ->
                    if (closed) {
                        opened.close()
                    } else {
                        subscribers += opened
                    }
                }
            }
        return stream
    }

    internal fun subscriberCount(): Int =
        synchronized(lock) {
            subscribers.size
        }

    fun close() {
        val streams =
            synchronized(lock) {
                closed = true
                val current = subscribers.toList()
                subscribers.clear()
                current
            }
        streams.forEach { it.closeLiveFrames() }
    }
}

class AudioFrameStream internal constructor(
    private val snapshot: List<ShortArray>,
    liveFrameCapacity: Int,
    streamScope: CoroutineScope,
    uncollectedStreamTimeoutMs: Long,
    private val unsubscribe: (AudioFrameStream) -> Unit,
) : AutoCloseable {
    private val state = AtomicInteger(STATE_PENDING)
    private val closed = AtomicBoolean(false)
    private val droppedLiveFrameTotal = AtomicInteger(0)
    private val liveFrames =
        Channel<ShortArray>(
            capacity = liveFrameCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { droppedLiveFrameTotal.incrementAndGet() },
        )
    private val uncollectedTimeoutJob: Job? =
        if (uncollectedStreamTimeoutMs > 0L) {
            streamScope.launch {
                delay(uncollectedStreamTimeoutMs)
                // The compare-and-set makes the timeout lose against a concurrent hand-off.
                if (state.compareAndSet(STATE_PENDING, STATE_TIMED_OUT)) {
                    close()
                }
            }
        } else {
            null
        }

    val preRollFrameCount: Int = snapshot.size

    /** Live frames lost to a full buffer so far. Pre-roll is never counted. */
    val droppedLiveFrames: Int get() = droppedLiveFrameTotal.get()

    val audio: Flow<ShortArray> =
        flow {
            if (!state.compareAndSet(STATE_PENDING, STATE_COLLECTING) && state.get() == STATE_TIMED_OUT) {
                log.w("collection started after the uncollected timeout closed the stream")
            }
            uncollectedTimeoutJob?.cancel()
            var loggedDropped = 0
            try {
                for (frame in snapshot) {
                    emit(frame.copyOf())
                }
                for (frame in liveFrames) {
                    // One log per burst: logged when the consumer catches up after drops.
                    val dropped = droppedLiveFrameTotal.get()
                    if (dropped > loggedDropped) {
                        log.w(
                            "live command frames dropped, consumer too slow",
                            null,
                            "dropped" to dropped - loggedDropped,
                        )
                        loggedDropped = dropped
                    }
                    emit(frame)
                }
            } finally {
                close()
            }
        }

    internal fun offerLiveFrame(frame: ShortArray) {
        liveFrames.trySend(frame)
    }

    internal fun closeLiveFrames() {
        liveFrames.close()
    }

    /** Hands the stream off to an owner that closes it when its run completes.
     *  Disarms the uncollected-stream timeout, because a slow first provider load
     *  can delay collection past it and the stream would drop the first command.
     *  The owner's close on run completion still reclaims a stream the provider
     *  never collects. */
    fun markHandedOff() {
        if (!state.compareAndSet(STATE_PENDING, STATE_HANDED_OFF) && state.get() == STATE_TIMED_OUT) {
            log.w("hand-off after the uncollected timeout already closed the stream")
        }
        uncollectedTimeoutJob?.cancel()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            uncollectedTimeoutJob?.cancel()
            unsubscribe(this)
            liveFrames.close()
        }
    }
}

internal fun Flow<ShortArray>.withPreRoll(preRoll: List<ShortArray>): Flow<ShortArray> =
    channelFlow {
        val liveFrames =
            Channel<ShortArray>(
                capacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val liveJob =
            launch {
                try {
                    collect { samples ->
                        liveFrames.trySend(samples.copyOf())
                    }
                } finally {
                    liveFrames.close()
                }
            }
        try {
            for (frame in preRoll) {
                send(frame.copyOf())
            }
            for (frame in liveFrames) {
                send(frame)
            }
        } finally {
            liveJob.cancel()
            liveFrames.close()
        }
    }
