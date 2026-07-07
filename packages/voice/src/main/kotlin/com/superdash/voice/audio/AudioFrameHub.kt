package com.superdash.voice.audio

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

private const val DEFAULT_STREAM_BUFFER_CAPACITY = 256
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
    private val subscribers = mutableSetOf<Channel<ShortArray>>()
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
            subscribers.forEach { channel ->
                channel.trySend(copy)
            }
        }
    }

    fun openStream(): AudioFrameStream {
        val liveFrames =
            Channel<ShortArray>(
                capacity = streamBufferCapacity,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val snapshot =
            synchronized(lock) {
                if (closed) {
                    liveFrames.close()
                } else {
                    subscribers += liveFrames
                }
                recentFrames.map { it.copyOf() }
            }
        return AudioFrameStream(
            snapshot = snapshot,
            liveFrames = liveFrames,
            preRollFrameCount = snapshot.size,
            streamScope = streamScope,
            uncollectedStreamTimeoutMs = uncollectedStreamTimeoutMs,
            closeAction = {
                synchronized(lock) {
                    subscribers -= liveFrames
                }
                liveFrames.close()
            },
        )
    }

    internal fun subscriberCount(): Int =
        synchronized(lock) {
            subscribers.size
        }

    fun close() {
        val channels =
            synchronized(lock) {
                closed = true
                val current = subscribers.toList()
                subscribers.clear()
                current
            }
        channels.forEach { it.close() }
    }
}

class AudioFrameStream(
    private val snapshot: List<ShortArray>,
    private val liveFrames: Channel<ShortArray>,
    val preRollFrameCount: Int,
    streamScope: CoroutineScope,
    uncollectedStreamTimeoutMs: Long,
    private val closeAction: () -> Unit,
) : AutoCloseable {
    private val collectionStarted = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var uncollectedTimeoutJob: Job? = null

    val audio: Flow<ShortArray> =
        flow {
            collectionStarted.set(true)
            uncollectedTimeoutJob?.cancel()
            try {
                for (frame in snapshot) {
                    emit(frame.copyOf())
                }
                for (frame in liveFrames) {
                    emit(frame)
                }
            } finally {
                close()
            }
        }

    init {
        if (uncollectedStreamTimeoutMs > 0L) {
            uncollectedTimeoutJob =
                streamScope.launch {
                    delay(uncollectedStreamTimeoutMs)
                    if (!collectionStarted.get()) {
                        close()
                    }
                }
        }
    }

    /** Hands the stream off to an owner that will drive collection when it is
     *  ready and close the stream when its run completes. Cancels the wall-clock
     *  uncollected-stream timeout: a slow first-use provider load (e.g. a ~50 MB
     *  Whisper/Moonshine warm-up) legitimately delays the start of collection past
     *  the timeout, and without this the stream would self-close mid-load and drop
     *  the first command after boot (empty transcript). The leak the timeout guards
     *  against is still covered — the owner closes the stream on run completion even
     *  when the provider never collects (e.g. provider-missing). */
    fun markHandedOff() {
        uncollectedTimeoutJob?.cancel()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            uncollectedTimeoutJob?.cancel()
            closeAction()
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
