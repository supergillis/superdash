package com.superdash.voice.recording

import com.superdash.voice.pipeline.VoiceRunContext
import kotlinx.coroutines.flow.Flow

class VoiceRecordingComponent(
    private val service: VoiceCommandRecordingService,
    private val clearRecordings: suspend () -> Unit,
) {
    fun transformCommandAudio(
        context: VoiceRunContext,
        audio: Flow<ShortArray>,
    ): Flow<ShortArray> =
        service.record(context, audio)

    suspend fun clear() {
        clearRecordings()
    }
}
