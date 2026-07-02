package com.superdash.voice.action

import com.superdash.core.log.Log
import com.superdash.voice.audio.ReplayableAudioBuffer
import com.superdash.voice.pipeline.LocalSttRoute
import com.superdash.voice.pipeline.VoiceProviderProvenance
import com.superdash.voice.stt.LocalSttEngine
import com.superdash.voice.stt.RecognitionUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

private val log = Log("LocalTranscriptActionFlow")

// 60s @ 16kHz with 10ms (160-sample) frames. Matches RetryingVoiceActionProvider
// so a wedged local STT cannot grow the replay buffer indefinitely.
private const val LOCAL_TRANSCRIPT_MAX_BUFFERED_FRAMES = 16_000 * 60 / 160

class LocalTranscriptActionFlow(
    private val localStt: LocalSttEngine,
    private val transcriptDecider: LocalTranscriptDecider = LocalTranscriptDecider(),
    private val transcriptActionExecutor: TranscriptActionExecutor,
    private val audioActionProvider: VoiceActionProvider,
) : VoiceActionProvider {
    override fun invoke(audio: Flow<ShortArray>): Flow<VoiceActionEvent> =
        flow {
            coroutineScope {
                val replayableAudio =
                    ReplayableAudioBuffer(
                        source = audio,
                        scope = this,
                        maxFrames = LOCAL_TRANSCRIPT_MAX_BUFFERED_FRAMES,
                    )
                try {
                    val recognitionResult = recognizeLocal(replayableAudio.liveFlow())
                    if (!recognitionResult.shouldContinue) {
                        return@coroutineScope
                    }
                    when (val decision = transcriptDecider.decide(recognitionResult.finalUpdate)) {
                        is LocalTranscriptDecision.Accepted -> {
                            emitLocalSttProvenance(LocalSttRoute.HaText, decision.transcript, null)
                            transcriptActionExecutor.execute(decision.transcript).collect { event ->
                                emit(event.withFallbackTranscript(decision.transcript))
                            }
                        }
                        is LocalTranscriptDecision.Rejected -> {
                            emitLocalSttProvenance(LocalSttRoute.HaAudio, decision.transcript, decision.reason)
                            replayableAudio.awaitComplete()
                            audioActionProvider(replayableAudio.replayFlow()).collect { event -> emit(event) }
                        }
                    }
                } finally {
                    replayableAudio.cancel()
                }
            }
        }

    private suspend fun FlowCollector<VoiceActionEvent>.recognizeLocal(
        audio: Flow<ShortArray>,
    ): LocalRecognitionResult {
        var finalUpdate: RecognitionUpdate.Final? = null
        var consumedFrames = 0
        try {
            val recognitionUpdates =
                localStt.recognize(
                    flow {
                        audio.collect { frame ->
                            consumedFrames += 1
                            emit(frame)
                        }
                    },
                )
            recognitionUpdates.collect { update ->
                log.i("local STT update", "type" to update.javaClass.simpleName)
                emit(VoiceActionEvent.Recognition(update))
                if (update is RecognitionUpdate.Final) {
                    finalUpdate = update
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
            log.w("local STT failed", throwable, "consumedFrames" to consumedFrames)
            if (consumedFrames > 0) {
                emit(VoiceActionEvent.Error("local-stt-failed", throwable.message ?: "local STT failed"))
                return LocalRecognitionResult(
                    finalUpdate = null,
                    shouldContinue = false,
                )
            }
        }
        return LocalRecognitionResult(
            finalUpdate = finalUpdate,
            shouldContinue = true,
        )
    }

    private suspend fun FlowCollector<VoiceActionEvent>.emitLocalSttProvenance(
        route: LocalSttRoute,
        transcript: String?,
        reason: String?,
    ) {
        emit(
            VoiceActionEvent.ProviderProvenance(
                VoiceProviderProvenance.LocalStt(
                    route = route,
                    transcript = transcript,
                    reason = reason,
                ),
            ),
        )
    }

    private fun VoiceActionEvent.withFallbackTranscript(transcript: String): VoiceActionEvent =
        if (this is VoiceActionEvent.ActionComplete && this.transcript == null) {
            copy(transcript = transcript)
        } else {
            this
        }
}

private data class LocalRecognitionResult(
    val finalUpdate: RecognitionUpdate.Final?,
    val shouldContinue: Boolean,
)
