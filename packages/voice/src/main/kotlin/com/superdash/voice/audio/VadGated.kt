package com.superdash.voice.audio

import com.superdash.core.log.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

private val log = Log("VadGated")

private const val FRAME_MS = 10
internal const val DEFAULT_INITIAL_SILENCE_TIMEOUT_MS = 2_000

/** Pair adjacent 10ms frames into 20ms VAD chunks; complete the flow when
 *  konovalov-vad reports trailing silence past the configured threshold.
 *  Downstream HaAssistClient watches for completion to send the STT terminator.
 *
 *  Owns the [Vad] internally. Created on first collection, closed in `finally`.
 *  Callers must NOT pass a pre-built Vad: the konovalov-vad native backend throws
 *  "You can't use Vad after closing session!" if anything calls into a closed
 *  instance, and a caller-owned Vad is naturally racy when the flow is collected
 *  later than the caller's stack frame returns.
 *
 *  Termination uses `transformWhile` rather than `return@collect`: the latter
 *  returns from the collector lambda but does NOT stop source collection, so
 *  the flow would otherwise pull from `audio` forever and never signal end-of-
 *  utterance to HA. */
fun vadGated(audio: Flow<ShortArray>, silenceMs: Int): Flow<ShortArray> =
    vadGated(
        audio = audio,
        config =
            VadGateConfig(
                trailingSilenceMs = silenceMs,
                initialSilenceTimeoutMs = DEFAULT_INITIAL_SILENCE_TIMEOUT_MS,
                ignoredInitialFrames = 0,
            ),
        detectorFactory = { VadSpeechDetector(Vad(silenceMs)) },
    )

internal data class VadGateConfig(
    val trailingSilenceMs: Int,
    val initialSilenceTimeoutMs: Int,
    val ignoredInitialFrames: Int = 0,
)

internal interface SpeechDetector : AutoCloseable {
    fun isSpeech(frame: ShortArray): Boolean
}

internal class VadSpeechDetector(
    private val vad: Vad,
) : SpeechDetector {
    override fun isSpeech(frame: ShortArray): Boolean = vad.isSpeech(frame)

    override fun close() = vad.close()
}

internal fun vadGated(
    audio: Flow<ShortArray>,
    config: VadGateConfig,
    detectorFactory: () -> SpeechDetector,
): Flow<ShortArray> =
    flow {
        log.i(
            "vadGated subscribed",
            "silenceMs" to config.trailingSilenceMs,
            "initialSilenceTimeoutMs" to config.initialSilenceTimeoutMs,
        )
        val detector = detectorFactory()
        val tracker = UtteranceTracker()
        var pending: ShortArray? = null
        var emitted = 0
        var vadEligibleFrames = 0
        var vadCalls = 0
        var speechStarted = false
        try {
            emitAll(
                audio.transformWhile { frame ->
                    emit(frame)
                    emitted += 1
                    if (emitted <= config.ignoredInitialFrames) {
                        true
                    } else {
                        vadEligibleFrames += 1
                        val combined = pending?.let { it + frame }
                        if (combined == null) {
                            pending = frame
                            true
                        } else {
                            pending = null
                            val isSpeech = detector.isSpeech(combined)
                            if (isSpeech) {
                                speechStarted = true
                            }
                            val complete =
                                tracker.next(isSpeech) ||
                                    (!speechStarted && vadEligibleFrames * FRAME_MS >= config.initialSilenceTimeoutMs)
                            vadCalls += 1
                            if (complete) {
                                log.i(
                                    "vadGated complete",
                                    "emitted" to emitted,
                                    "vadCalls" to vadCalls,
                                    "speechStarted" to speechStarted,
                                )
                                false
                            } else {
                                true
                            }
                        }
                    }
                },
            )
        } finally {
            log.i("vadGated ended", "emitted" to emitted, "vadCalls" to vadCalls)
            detector.close()
        }
    }
