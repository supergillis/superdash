package com.superdash.voice.pipeline

import kotlinx.serialization.json.JsonObject

sealed interface VoiceState {
    data object Idle : VoiceState

    data class WakeFired(
        val word: String,
    ) : VoiceState

    data class Recording(
        val partialTranscript: String? = null,
    ) : VoiceState

    data class Processing(
        val transcript: String,
    ) : VoiceState

    data class ActionComplete(
        val transcript: String,
        val response: JsonObject,
    ) : VoiceState

    data class Speaking(
        val ttsUrl: String,
        val transcript: String,
    ) : VoiceState

    data class Failed(
        val reason: String,
    ) : VoiceState
}
