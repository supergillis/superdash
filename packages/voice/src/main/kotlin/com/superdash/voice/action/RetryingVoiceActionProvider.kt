package com.superdash.voice.action

import com.superdash.core.log.Log
import com.superdash.voice.audio.ReplayableAudioBuffer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

private val retryLog = Log("RetryingVoiceActionProvider")

private const val DEFAULT_MAX_ATTEMPTS = 2
private const val REPLAY_FRAME_DELAY_MS = 10L

// 60s @ 16kHz with 10ms (160-sample) frames.
private const val DEFAULT_RETRY_MAX_FRAMES = 16_000 * 60 / 160

fun retryingVoiceActionProvider(
    delegate: VoiceActionProvider,
    maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    maxBufferedFrames: Int = DEFAULT_RETRY_MAX_FRAMES,
): VoiceActionProvider =
    { audio: Flow<ShortArray> ->
        flow {
            coroutineScope {
                val replayBuffer =
                    ReplayableAudioBuffer(
                        source = audio,
                        scope = this,
                        maxFrames = maxBufferedFrames,
                    )
                try {
                    var attempt = 1
                    var attemptAudio: Flow<ShortArray> = replayBuffer.liveFlow()
                    while (true) {
                        var retryableError: VoiceActionEvent.Error? = null
                        delegate(attemptAudio).collect { event ->
                            if (
                                event is VoiceActionEvent.Error &&
                                event.isRetryableSttError() &&
                                attempt < maxAttempts
                            ) {
                                retryableError = event
                            } else {
                                emit(event)
                            }
                        }

                        val pendingError = retryableError
                        if (pendingError == null) {
                            break
                        }
                        replayBuffer.awaitComplete()
                        if (attempt >= maxAttempts) {
                            emit(pendingError)
                            break
                        }
                        retryLog.w(
                            "retrying voice action after HA STT failure",
                            null,
                            "attempt" to attempt,
                            "truncated" to replayBuffer.truncated,
                            "code" to pendingError.code,
                        )
                        attempt += 1
                        attemptAudio = replayBuffer.replayFlow().pacedAudio()
                    }
                } finally {
                    replayBuffer.cancel()
                }
            }
        }
    }

private fun VoiceActionEvent.Error.isRetryableSttError(): Boolean = code == "stt-stream-failed"

private fun Flow<ShortArray>.pacedAudio(): Flow<ShortArray> =
    flow {
        var firstFrame = true
        collect { frame ->
            if (firstFrame) {
                firstFrame = false
            } else {
                delay(REPLAY_FRAME_DELAY_MS)
            }
            emit(frame.copyOf())
        }
    }
