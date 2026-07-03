package com.superdash.voice.pipeline

import com.superdash.core.log.Log
import com.superdash.voice.audio.AudioFrameHub
import com.superdash.voice.audio.CommandAudioGateConfig
import com.superdash.voice.audio.Vad
import com.superdash.voice.audio.VadSpeechDetector
import com.superdash.voice.audio.commandAudioGated
import com.superdash.voice.wake.WakeEvent
import com.superdash.voice.wake.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val log = Log("VoiceCaptureLoop")

private const val AUDIO_PROBE_FRAMES = 500
private const val COMMAND_PRE_ROLL_FRAMES = 80
private const val COMMAND_WAKE_TAIL_DROP_FRAMES = 25
private const val COMMAND_SPEECH_START_PRE_BUFFER_FRAMES = 60
private const val COMMAND_INITIAL_SILENCE_TIMEOUT_MS = 5_000

/** Audio capture + wake-word detection + VAD-gated handoff to the coordinator.
 *
 *  Owns no Android objects. `source` and `runnerFactory` inject the only sides
 *  that touch hardware (mic + LiteRT) so this loop is unit-testable with synthetic
 *  audio and a no-op runner factory.
 *
 *  collectLatest on [activeWakeWord] tears down the previous runner whenever the
 *  user changes the active wake word in Settings, so the next iteration constructs
 *  a fresh runner without restarting the surrounding service. */
class VoiceCaptureLoop(
    private val source: () -> Flow<ShortArray>,
    private val activeWakeWord: Flow<String>,
    private val vadSilenceMs: Flow<Int>,
    private val coordinator: VoicePipelineCoordinator,
    private val runnerFactory: (String) -> WakeWordDetector?,
    private val createRunContext: suspend (WakeEvent) -> VoiceRunContext,
    private val commandAudioTransform: (VoiceRunContext, Flow<ShortArray>) -> Flow<ShortArray> = { _, audio -> audio },
    private val audioFrameHubFactory: (CoroutineScope, Int) -> AudioFrameHub = { scope, capacity ->
        AudioFrameHub(capacity = capacity, streamScope = scope)
    },
) {
    suspend fun run() {
        activeWakeWord.collectLatest { wakeWord ->
            log.i("listening for wake word", "word" to wakeWord)
            val runner = runnerFactory(wakeWord) ?: return@collectLatest
            try {
                // SUSPEND (not DROP_OLDEST): the zero-length RESTART_SENTINEL is the
                // only signal that resets MicroWakeWordRunner streaming state across a
                // mic restart (resetVariableTensors, featureBuffer, probHistory,
                // audioFeatureExtractor). Dropping it leaks stale streaming state across the
                // restart. Back-pressure on the audio path is acceptable here: the wake
                // detector keeps pace under normal load, and a sustained stall is a
                // runner-level problem we want to surface rather than mask.
                val sharedAudio =
                    MutableSharedFlow<ShortArray>(
                        extraBufferCapacity = 64,
                        onBufferOverflow = BufferOverflow.SUSPEND,
                    )
                coroutineScope {
                    val commandAudioHub = audioFrameHubFactory(this, COMMAND_PRE_ROLL_FRAMES)
                    launch {
                        var frames = 0
                        var peak = 0
                        try {
                            source().collect { samples ->
                                if (samples.isEmpty()) {
                                    // AudioRecord restart sentinel: forward to wake detector for
                                    // state reset, but do not buffer as pre-roll content.
                                    sharedAudio.emit(samples)
                                    return@collect
                                }
                                commandAudioHub.publish(samples)
                                sharedAudio.emit(samples)
                                frames++
                                peak = maxOf(peak, samples.peakAmplitude())
                                if (frames % AUDIO_PROBE_FRAMES == 0) {
                                    log.i("mic audio observed", "frames" to frames, "peak" to peak)
                                    peak = 0
                                }
                            }
                        } finally {
                            commandAudioHub.close()
                        }
                    }
                    launch {
                        runner.detect(sharedAudio).collect { event ->
                            val commandAudio = commandAudioHub.openStream()
                            var coordinatorOwnsStream = false
                            try {
                                // Snapshot silence-timeout per fire so user adjustments take effect
                                // on the next wake without restarting the pipeline.
                                val silenceMs = vadSilenceMs.first()
                                log.i("wake fired", "word" to event.word, "vadSilenceMs" to silenceMs)
                                val context = createRunContext(event)
                                val gatedAudio =
                                    commandAudioGated(
                                        audio = commandAudio.audio,
                                        config =
                                            CommandAudioGateConfig(
                                                dropInitialFrames =
                                                    commandAudio.preRollFrameCount + COMMAND_WAKE_TAIL_DROP_FRAMES,
                                                speechStartPreBufferFrames = COMMAND_SPEECH_START_PRE_BUFFER_FRAMES,
                                                initialCommandTimeoutMs = COMMAND_INITIAL_SILENCE_TIMEOUT_MS,
                                                trailingSilenceMs = silenceMs,
                                            ),
                                        detectorFactory = { VadSpeechDetector(Vad(silenceMs)) },
                                    )
                                val runJob =
                                    coordinator.onWake(
                                        context,
                                        commandAudioTransform(context, gatedAudio),
                                    )
                                coordinatorOwnsStream = true
                                // The coordinator now owns the stream. Cancel the
                                // wall-clock uncollected-stream timeout so a slow
                                // first provider load (~50 MB model warm-up) cannot
                                // self-close the stream mid-load and drop the first
                                // command. Cleanup is re-homed to the run's lifecycle:
                                // closing on completion reclaims the stream whether or
                                // not the provider ever collected it.
                                commandAudio.markHandedOff()
                                runJob.invokeOnCompletion { commandAudio.close() }
                            } finally {
                                if (!coordinatorOwnsStream) {
                                    commandAudio.close()
                                }
                            }
                        }
                    }
                }
            } finally {
                runner.close()
            }
        }
    }
}

private fun ShortArray.peakAmplitude(): Int {
    var peak = 0
    for (sample in this) {
        val value = sample.toInt()
        val absValue =
            if (value == Short.MIN_VALUE.toInt()) {
                Short.MAX_VALUE.toInt()
            } else if (value < 0) {
                -value
            } else {
                value
            }
        if (absValue > peak) {
            peak = absValue
        }
    }
    return peak
}
