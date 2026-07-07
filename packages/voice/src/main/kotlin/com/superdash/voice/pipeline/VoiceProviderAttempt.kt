package com.superdash.voice.pipeline

sealed interface VoiceProviderAttemptResult {
    data class Completed(
        val actionComplete: Boolean,
    ) : VoiceProviderAttemptResult

    data class Failed(
        val reason: String,
    ) : VoiceProviderAttemptResult

    data class Skipped(
        val reason: String,
    ) : VoiceProviderAttemptResult
}

data class VoiceProviderAttempt(
    val identity: VoiceProviderIdentity,
    val elapsedMs: Long,
    val result: VoiceProviderAttemptResult,
    val provenance: List<VoiceProviderProvenance> = emptyList(),
)
