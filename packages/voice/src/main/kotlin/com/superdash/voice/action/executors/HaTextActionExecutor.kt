package com.superdash.voice.action.executors

import com.superdash.ha.AssistEvent
import com.superdash.ha.AssistPipelineStage
import com.superdash.voice.action.TranscriptActionExecutor
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.toVoiceActionEvent
import com.superdash.voice.pipeline.VoiceResponseMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class HaTextActionExecutor(
    private val runTextPipeline: (String, AssistPipelineStage) -> Flow<AssistEvent>,
    private val responseMode: suspend () -> VoiceResponseMode,
) : TranscriptActionExecutor {
    override fun execute(transcript: String): Flow<VoiceActionEvent> =
        flow {
            val mode = responseMode()
            emitAll(
                runTextPipeline(transcript, mode.assistEndStage)
                    .map { event -> event.toVoiceActionEvent() },
            )
        }
}
