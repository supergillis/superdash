package com.superdash.voice.pipeline

import com.superdash.voice.action.VoiceActionEvent
import kotlinx.coroutines.flow.Flow

fun interface VoiceProviderRunner {
    fun run(
        selection: VoiceProviderSelection,
        audio: Flow<ShortArray>,
    ): Flow<VoiceProviderRunEvent>
}

data class VoiceProviderRun(
    val events: List<VoiceActionEvent>,
    val providerTrace: List<VoiceProviderAttempt>,
)

sealed interface VoiceProviderRunEvent {
    data class Action(
        val event: VoiceActionEvent,
    ) : VoiceProviderRunEvent

    data class AttemptFinished(
        val attempt: VoiceProviderAttempt,
    ) : VoiceProviderRunEvent
}
