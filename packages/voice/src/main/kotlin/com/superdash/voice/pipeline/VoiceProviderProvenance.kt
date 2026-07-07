package com.superdash.voice.pipeline

import com.superdash.voice.intent.LocalIntentStatus

enum class LocalSttRoute {
    HaText,
    HaAudio,
}

sealed interface VoiceProviderProvenance {
    data class LocalStt(
        val route: LocalSttRoute,
        val transcript: String?,
        val reason: String?,
    ) : VoiceProviderProvenance

    data class LocalIntent(
        val status: LocalIntentStatus,
        val transcript: String,
        val intentId: String?,
        val confidence: Float?,
        val threshold: Float,
        val directActionDomain: String?,
        val directActionService: String?,
        val directActionTarget: String?,
        val fallbackReason: String?,
    ) : VoiceProviderProvenance
}
