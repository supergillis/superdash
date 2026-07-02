package com.superdash.voice.audio

import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import com.konovalov.vad.webrtc.Vad as VadWebRTCBuilder

private const val MIN_SPEECH_MS = 500

/** Wraps konovalov-vad's WebRTC backend, which applies hysteresis: flips to "speech" after
 *  ~500ms of speech and back to "silence" only after [silenceTimeoutMs] of trailing silence.
 *  [UtteranceTracker] layers a "had-speech AND now-silent" rule on top so we don't trip
 *  complete on the leading silence before the user starts talking. */
class Vad(
    silenceTimeoutMs: Int,
) : AutoCloseable {
    private val backend =
        VadWebRTCBuilder
            .builder()
            .setSampleRate(SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(FrameSize.FRAME_SIZE_320)
            .setMode(Mode.VERY_AGGRESSIVE)
            .setSilenceDurationMs(silenceTimeoutMs)
            .setSpeechDurationMs(MIN_SPEECH_MS)
            .build()
    private val tracker = UtteranceTracker()

    fun isSpeech(frame: ShortArray): Boolean = backend.isSpeech(frame)

    /** Returns true once the user has spoken AND the accumulated trailing silence
     *  has exceeded the configured silence timeout, signalling the utterance is
     *  complete. */
    fun isUtteranceComplete(frame: ShortArray): Boolean = tracker.next(isSpeech(frame))

    override fun close() = backend.close()
}
