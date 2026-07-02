package com.superdash.voice.audio

import com.superdash.core.log.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

private val log = Log("CommandAudioGate")

private const val FRAME_MS = 10

internal data class CommandAudioGateConfig(
    val dropInitialFrames: Int,
    val speechStartPreBufferFrames: Int,
    val initialCommandTimeoutMs: Int,
    val trailingSilenceMs: Int,
)

internal fun commandAudioGated(
    audio: Flow<ShortArray>,
    config: CommandAudioGateConfig,
    detectorFactory: () -> SpeechDetector,
): Flow<ShortArray> =
    flow {
        log.i(
            "commandAudioGated subscribed",
            "dropInitialFrames" to config.dropInitialFrames,
            "speechStartPreBufferFrames" to config.speechStartPreBufferFrames,
            "initialCommandTimeoutMs" to config.initialCommandTimeoutMs,
            "trailingSilenceMs" to config.trailingSilenceMs,
        )
        val detector = detectorFactory()
        val tracker = UtteranceTracker()
        val speechStartBuffer = ArrayDeque<ShortArray>(config.speechStartPreBufferFrames)
        var pending: ShortArray? = null
        var consumed = 0
        var eligibleFrames = 0
        var emitted = 0
        var commandStarted = false
        try {
            emitAll(
                audio.transformWhile { frame ->
                    consumed += 1
                    if (consumed <= config.dropInitialFrames) {
                        true
                    } else {
                        eligibleFrames += 1
                        if (!commandStarted) {
                            if (config.speechStartPreBufferFrames > 0) {
                                if (speechStartBuffer.size == config.speechStartPreBufferFrames) {
                                    speechStartBuffer.removeFirst()
                                }
                                speechStartBuffer.addLast(frame.copyOf())
                            }
                        } else {
                            emit(frame)
                            emitted += 1
                        }

                        val previousFrame = pending
                        val combined = previousFrame?.let { it + frame }
                        if (combined == null) {
                            pending = frame
                            if (!commandStarted && eligibleFrames * FRAME_MS >= config.initialCommandTimeoutMs) {
                                log.i(
                                    "commandAudioGated timeout",
                                    "consumed" to consumed,
                                    "eligibleFrames" to eligibleFrames,
                                )
                                false
                            } else {
                                true
                            }
                        } else {
                            pending = null
                            val isSpeech = detector.isSpeech(combined)
                            if (!commandStarted) {
                                if (isSpeech) {
                                    commandStarted = true
                                    tracker.next(isSpeech)
                                    if (speechStartBuffer.isEmpty()) {
                                        emit(previousFrame)
                                        emitted += 1
                                        emit(frame)
                                        emitted += 1
                                    } else {
                                        for (buffered in speechStartBuffer) {
                                            emit(buffered)
                                            emitted += 1
                                        }
                                    }
                                    speechStartBuffer.clear()
                                    true
                                } else if (eligibleFrames * FRAME_MS >= config.initialCommandTimeoutMs) {
                                    log.i(
                                        "commandAudioGated timeout",
                                        "consumed" to consumed,
                                        "eligibleFrames" to eligibleFrames,
                                    )
                                    false
                                } else {
                                    true
                                }
                            } else if (tracker.next(isSpeech)) {
                                log.i("commandAudioGated complete", "consumed" to consumed, "emitted" to emitted)
                                false
                            } else {
                                true
                            }
                        }
                    }
                },
            )
        } finally {
            log.i(
                "commandAudioGated ended",
                "consumed" to consumed,
                "eligibleFrames" to eligibleFrames,
                "emitted" to emitted,
                "commandStarted" to commandStarted,
            )
            detector.close()
        }
    }
