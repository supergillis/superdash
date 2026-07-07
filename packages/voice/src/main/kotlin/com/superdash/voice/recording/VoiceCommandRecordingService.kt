package com.superdash.voice.recording

import com.superdash.core.log.Log
import com.superdash.voice.pipeline.DEFAULT_ASSIST_RUN_TIMEOUT_MS
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunResult
import com.superdash.voice.pipeline.VoiceRunTerminalState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val log = Log("VoiceCommandRecordingService")
const val DEFAULT_COMMAND_RECORDING_RESULT_TIMEOUT_MS = DEFAULT_ASSIST_RUN_TIMEOUT_MS + 5_000L

class VoiceCommandRecordingService(
    private val enabled: suspend () -> Boolean,
    private val runResults: SharedFlow<VoiceRunResult>,
    private val currentClearGeneration: () -> VoiceCommandRecordingGeneration = { VoiceCommandRecordingGeneration(0L) },
    private val saveRecording: suspend (VoiceCommandRecording, VoiceCommandRecordingGeneration) -> Unit,
    private val scope: CoroutineScope,
    private val resultTimeoutMs: Long = DEFAULT_COMMAND_RECORDING_RESULT_TIMEOUT_MS,
) {
    fun record(
        context: VoiceRunContext,
        audio: Flow<ShortArray>,
    ): Flow<ShortArray> =
        flow {
            if (!enabled()) {
                audio.collect { frame -> emit(frame) }
                return@flow
            }

            val frames = mutableListOf<ShortArray>()
            val audioFinished = CompletableDeferred<Unit>()
            val generation = currentClearGeneration()
            val saveJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        val result =
                            withTimeoutOrNull(resultTimeoutMs) {
                                runResults.first { runResult -> runResult.context.id == context.id }
                            } ?: return@launch
                        audioFinished.await()
                        if (result.terminalState == VoiceRunTerminalState.Cancelled) {
                            return@launch
                        }
                        saveRecording(
                            VoiceCommandRecording(
                                metadata = VoiceCommandRecordingMetadata.from(context, result),
                                frames = frames.toList(),
                            ),
                            generation,
                        )
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                        log.w("command recording failed", throwable)
                    }
                }

            var completedNormally = false
            try {
                audio.collect { frame ->
                    frames += frame.copyOf()
                    emit(frame.copyOf())
                }
                completedNormally = true
            } finally {
                if (!audioFinished.isCompleted) {
                    audioFinished.complete(Unit)
                }
                if (!completedNormally) {
                    saveJob.cancelAndJoin()
                }
            }
        }
}
